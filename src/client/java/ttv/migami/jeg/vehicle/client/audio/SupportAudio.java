package ttv.migami.jeg.vehicle.client.audio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModSounds;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * The FSV-6's sound, heard the way the aircraft and the BD-40 are (see {@link AircraftSoundRange}): the diesel as one
 * voice crossfaded between its idle and working loops as engine speed rises, the cab's own recording for the crew
 * in first person (a closed cab), the generator that runs the shelter when deployed, the jacks' hydraulics and the
 * slide-outs' motor while it unfolds, the reversing alarm and the air horn. Outside, everything fades on the shared
 * curve out to 1024 blocks, arrives late by the speed of sound and takes Doppler; past entity range
 * {@link FarAircraftAudio} carries on from the server's tracks. Loops are static buffers and survive view changes.
 */
public final class SupportAudio {
    private static final Map<Integer, Group> GROUPS = new HashMap<>();
    private static final String[] LOOPS = {"engine_idle", "engine_load", "engine_cab", "engine_distant", "generator", "generator_distant",
            "hydraulic", "slide_motor", "reverse", "horn", "tyres"};
    private static final double SOUND_SPEED = 343.0D / 20.0D;
    private static final int HISTORY = 40;
    // Loudness before distance: a medium truck diesel, well under the bulldozer working (1.6) and the A-10 (2.5).
    private static final float ENGINE_IDLE_LOUDNESS = .8F, ENGINE_WORK_LOUDNESS = 1.4F, GENERATOR_LOUDNESS = .25F;
    private static final float HYDRAULIC_LOUDNESS = .5F, MOTOR_LOUDNESS = .45F, ALARM_LOUDNESS = .9F, HORN_LOUDNESS = 2.2F;
    /** Six big tyres on gravel and dirt (the LAV-150's recording), by how fast the truck rolls on the ground. */
    private static final float TYRE_LOUDNESS = .8F;
    private static final float ONE_SHOT_LOUDNESS = 1.1F, CLANK_LOUDNESS = 1.0F;

    private SupportAudio() {}

    private record State(Vec3 position, Vec3 motion, float rpm, boolean engine, boolean generator, boolean hydraulic, boolean motor,
                         boolean reversing, boolean horn, float startup, float roll) {}

    private static final class Group {
        final SupportVehicleEntity entity;
        final Map<String, Layer> layers = new HashMap<>();
        final List<Layer> shots = new ArrayList<>();
        final ArrayDeque<State> history = new ArrayDeque<>();
        boolean wasPowered, wasDeploying, wasJacks;
        float gain = 1, rpm, lastDoors = -1, roll;
        Group(SupportVehicleEntity entity) { this.entity = entity; }

        State heard(double distance) {
            int delay = Mth.clamp((int) Math.round(distance / SOUND_SPEED), 0, Math.max(0, this.history.size() - 1));
            int i = 0;
            for (State state : this.history) if (i++ == delay) return state;
            return this.history.peekFirst();
        }

        void shot(Minecraft mc, String name, float loudness, float pitch, int lifetime) {
            Layer sound = new Layer(this, name, false, loudness, lifetime);
            sound.setPitch(pitch);
            this.shots.add(sound);
            int delay = aboard(this.entity) ? 0 : (int) (Math.sqrt(distanceSq(this.entity)) / SOUND_SPEED);
            mc.getSoundManager().playDelayed(sound, delay);
        }

        void stopLoops(Minecraft mc) {
            for (Layer layer : this.layers.values()) { layer.halt(); mc.getSoundManager().stop(layer); }
            this.layers.clear();
        }

        void stop(Minecraft mc) {
            this.stopLoops(mc);
            for (Layer layer : this.shots) { layer.halt(); mc.getSoundManager().stop(layer); }
        }
    }

    /** The vehicle is making any sound at all: engine on, generator running, parts moving, horn. */
    private static boolean active(SupportVehicleEntity truck) {
        return truck.powered() || truck.generatorRunning() || truck.isMovingParts();
    }

    /** A burning furnace, smoker or blast furnace in a bay crackles as the block does (a one in ten chance a tick). */
    private static void furnaceSounds(Minecraft mc, SupportVehicleEntity truck) {
        for (int bay = 0; bay < ttv.migami.jeg.vehicle.support.SupportModules.WALL_BAYS; bay++) {
            if (!truck.furnaceLitForDisplay(bay) || mc.level.getRandom().nextDouble() >= 0.1) continue;
            var item = truck.fittedForDisplay(bay).getItem();
            var sound = item == net.minecraft.world.item.Items.SMOKER ? net.minecraft.sounds.SoundEvents.SMOKER_SMOKE
                    : item == net.minecraft.world.item.Items.BLAST_FURNACE ? net.minecraft.sounds.SoundEvents.BLASTFURNACE_FIRE_CRACKLE
                    : net.minecraft.sounds.SoundEvents.FURNACE_FIRE_CRACKLE;
            var at = truck.modules().soundAt(bay);
            mc.level.playLocalSound(at.x, at.y, at.z, sound, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { clear(mc); return; }
        var seen = new HashSet<Integer>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof SupportVehicleEntity lit && distanceSq(lit) < 24 * 24) furnaceSounds(mc, lit);
            if (!(entity instanceof SupportVehicleEntity truck) || distanceSq(truck) > AircraftSoundRange.MAX_RANGE * AircraftSoundRange.MAX_RANGE) continue;
            Group group = GROUPS.get(truck.getId());
            if (group != null && group.entity != truck) { group.stop(mc); GROUPS.remove(truck.getId()); group = null; }
            boolean active = active(truck);
            if (group == null) {
                if (!active) continue;
                group = new Group(truck);
                GROUPS.put(truck.getId(), group);
                if (truck.powered() && truck.powerAge() <= 3) group.shot(mc, "engine_start", ONE_SHOT_LOUDNESS, 1, 60);
                group.wasPowered = truck.powered();
                group.wasDeploying = truck.deployTarget();
            }
            seen.add(truck.getId());
            float speed = Math.abs(truck.wheelTravel(1) - truck.wheelTravel(0));
            float throttle = truck.getControllingPassenger() == mc.player ? Math.abs(truck.landInputForAudio()) : Math.min(1, speed * 3);
            float rpmTarget = !truck.engineRunning() ? 0 : Mth.clamp(.08F + speed / .9F * .55F + throttle * .3F, 0, 1);
            group.rpm += (rpmTarget - group.rpm) * (rpmTarget > group.rpm ? .14F : .06F);
            float progress = truck.deployProgress();
            boolean jacks = truck.isMovingParts() && (progress < SupportRig.JACKS_END || progress > SupportRig.DOORS_END);
            boolean motor = truck.isMovingParts() && progress >= SupportRig.JACKS_END && progress <= SupportRig.SLIDES_END;
            // The tyres by road speed (about 0.9 blocks a tick flat out), only while they touch the ground.
            group.roll = Mth.lerp(.3F, group.roll, truck.onGround() ? Mth.clamp(speed / .9F, 0, 1) : 0);
            boolean reversing = truck.engineRunning() && truck.wheelTravel(1) - truck.wheelTravel(0) < -.01F;
            group.history.addFirst(new State(truck.position(), truck.getDeltaMovement(), group.rpm, truck.engineRunning(),
                    truck.generatorRunning(), jacks, motor, reversing, truck.hornOn(),
                    Mth.clamp((truck.powerAge() - (SupportVehicleEntity.START_TICKS - 12)) / 14F, 0, 1), group.roll));
            while (group.history.size() > HISTORY) group.history.removeLast();
            if (active && group.layers.isEmpty()) {
                for (String name : LOOPS) {
                    Layer sound = new Layer(group, name, true, 0, 0);
                    group.layers.put(name, sound);
                    mc.getSoundManager().play(sound);
                }
            }
            boolean powered = truck.powered();
            if (!group.wasPowered && powered && truck.powerAge() <= 3) group.shot(mc, "engine_start", ONE_SHOT_LOUDNESS, 1, 60);
            if (group.wasPowered && !powered) group.shot(mc, "engine_stop", ONE_SHOT_LOUDNESS, 1, 50);
            group.wasPowered = powered;
            // The parking brake set as it starts to deploy; the ramp's clank as it lands or closes.
            if (truck.deployTarget() && !group.wasDeploying && truck.isPacked()) group.shot(mc, "air_brake", ONE_SHOT_LOUDNESS, 1, 30);
            group.wasDeploying = truck.deployTarget();
            float doors = SupportRig.doors(progress);
            if (group.lastDoors >= 0 && ((doors >= 1 && group.lastDoors < 1) || (doors <= 0 && group.lastDoors > 0))) {
                group.shot(mc, "ramp_clank", CLANK_LOUDNESS, doors >= 1 ? 1 : 1.1F, 26);
            }
            group.lastDoors = doors;
            if (group.wasJacks && !jacks) group.shot(mc, "hydraulic_stop", HYDRAULIC_LOUDNESS, 1, 8);
            group.wasJacks = jacks;
            if (!active) {
                group.gain *= .7F;
                if (group.gain < .01F && !group.layers.isEmpty()) group.stopLoops(mc);
            } else {
                group.gain = Mth.lerp(.25F, group.gain, 1);
            }
            group.shots.removeIf(Layer::isStopped);
        }
        var it = GROUPS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Group group = entry.getValue();
            if (!seen.contains(entry.getKey()) || group.entity.isRemoved() || group.entity.level() != mc.level) {
                group.stop(mc);
                it.remove();
            }
        }
    }

    public static void clear(Minecraft mc) { GROUPS.values().forEach(g -> g.stop(mc)); GROUPS.clear(); }
    public static int loopCount(int entityId) { Group g = GROUPS.get(entityId); return g == null ? 0 : g.layers.size(); }
    public static float loopVolume(int entityId, String name) {
        Group g = GROUPS.get(entityId);
        Layer layer = g == null ? null : g.layers.get(name);
        return layer == null ? 0 : layer.getVolume();
    }
    public static Map<String, SoundInstance> loops(int entityId) {
        Group g = GROUPS.get(entityId);
        return g == null ? Map.of() : Map.copyOf(g.layers);
    }

    public static boolean aboard(SupportVehicleEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() == entity;
    }

    /** The crew in first person sits in the closed cab and hears the interior recording, centred. */
    public static boolean cab(SupportVehicleEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        return aboard(entity) && mc.options.getCameraType().isFirstPerson();
    }

    private static double distanceSq(SupportVehicleEntity entity) {
        return Minecraft.getInstance().gameRenderer.getMainCamera().position().distanceToSqr(entity.position());
    }

    private static final class Layer extends AbstractTickableSoundInstance implements DynamicRelativeSound {
        final Group group;
        final String kind;
        final float loudness;
        final int lifetime;
        float smoothed;
        int age;
        /** Slow, irregular drift in pitch and level so a running engine never sounds like the same loop twice. */
        final float drift1 = (float) (Math.random() * 6.3), drift2 = (float) (Math.random() * 6.3), drift3 = (float) (Math.random() * 6.3);

        Layer(Group group, String kind, boolean loop, float loudness, int lifetime) {
            super(ModSounds.ALL.get(Reference.id(kind.equals("tyres") ? "vehicle.tyres.gravel" : "vehicle.truck." + kind)).get(),
                    kind.equals("horn") ? SoundSource.PLAYERS : SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.group = group;
            this.kind = kind;
            this.looping = loop;
            this.delay = 0;
            this.loudness = loudness;
            this.lifetime = lifetime;
            this.attenuation = Attenuation.NONE;
            this.volume = loop ? .001F : Math.max(.001F, this.oneShotVolume());
            this.pitch = 1;
            this.follow(group.entity.position(), cab(group.entity));
        }

        void setPitch(float pitch) { this.pitch = pitch; }
        @Override public boolean canStartSilent() { return true; }
        void halt() { this.stop(); }

        private float oneShotVolume() {
            return aboard(this.group.entity) ? Math.min(1, this.loudness * .6F) : AircraftSoundRange.volume(Math.sqrt(distanceSq(this.group.entity)), this.loudness);
        }

        @Override public void tick() {
            SupportVehicleEntity t = this.group.entity;
            if (t.isRemoved()) { this.stop(); return; }
            if (!this.looping) {
                if (++this.age > this.lifetime) { this.stop(); return; }
                this.follow(t.position(), cab(t));
                this.volume = Math.max(.001F, this.oneShotVolume());
                return;
            }
            boolean inside = aboard(t);
            boolean inCab = cab(t);
            double distance = inside ? 0 : Math.sqrt(distanceSq(t));
            State s = this.group.heard(distance);
            if (s == null) return;
            this.follow(s.position(), inCab);
            float rpm = s.rpm(), throttle = rpm * Mth.HALF_PI;
            float near = inside ? 1 : AircraftSoundRange.nearShare(distance);
            float far = inside ? 0 : AircraftSoundRange.distantShare(distance);
            float engineLoudness = Mth.lerp(rpm, ENGINE_IDLE_LOUDNESS, ENGINE_WORK_LOUDNESS);
            // In the closed cab the exterior engine is heard only faintly under the cab's own recording.
            float exterior = inCab ? .25F : 1;
            float target = switch (this.kind) {
                case "engine_idle" -> s.engine() ? level(inside, .5F, engineLoudness, distance) * Mth.cos(throttle) * near * exterior : 0;
                case "engine_load" -> s.engine() ? level(inside, .6F, engineLoudness, distance) * Mth.sin(throttle) * near * exterior : 0;
                case "engine_cab" -> s.engine() && inCab ? .32F + .3F * rpm : 0;
                case "engine_distant" -> s.engine() && !inside ? AircraftSoundRange.volume(distance, engineLoudness) * far : 0;
                case "generator" -> s.generator() ? level(inside, .11F, GENERATOR_LOUDNESS, distance) * near : 0;
                case "generator_distant" -> s.generator() && !inside ? AircraftSoundRange.volume(distance, GENERATOR_LOUDNESS) * far : 0;
                case "hydraulic" -> s.hydraulic() ? level(inside, .2F, HYDRAULIC_LOUDNESS, distance) * near : 0;
                case "slide_motor" -> s.motor() ? level(inside, .2F, MOTOR_LOUDNESS, distance) * near : 0;
                case "reverse" -> s.reversing() ? level(inside, .08F, ALARM_LOUDNESS, distance) : 0;
                case "horn" -> s.horn() ? level(inside, .5F, HORN_LOUDNESS, distance) : 0;
                case "tyres" -> level(inside, inCab ? .1F : .24F, TYRE_LOUDNESS, distance) * Math.min(1, s.roll() * 1.3F) * near;
                default -> 0;
            };
            if (this.kind.startsWith("engine")) target *= s.startup();
            float clock = t.tickCount / 20F;
            boolean engine = this.kind.equals("engine_idle") || this.kind.equals("engine_load") || this.kind.equals("engine_cab") || this.kind.equals("generator");
            float wanderPitch = engine ? 1 + .022F * Mth.sin(clock * .37F + this.drift1) + .012F * Mth.sin(clock * 1.13F + this.drift2) : 1;
            if (engine) target *= 1 + .07F * Mth.sin(clock * .23F + this.drift3);
            float ease = this.kind.equals("horn") ? .7F : .25F;
            this.smoothed = Mth.lerp(ease, this.smoothed, target * this.group.gain);
            this.volume = Math.max(.001F, this.smoothed);
            float doppler = 1;
            var listener = Minecraft.getInstance().getCameraEntity();
            if (!inside && listener != null && listener.getRootVehicle() != t) {
                doppler = Mth.clamp(VehicleDoppler.multiplier(s.position(), s.motion(), listener.position(), listener.getDeltaMovement()), .93F, 1.07F);
            }
            this.pitch = doppler * wanderPitch * switch (this.kind) {
                case "engine_idle" -> .92F + rpm * .36F;
                case "engine_load" -> .82F + rpm * .3F;
                case "engine_cab" -> .9F + rpm * .3F;
                case "engine_distant" -> .9F + rpm * .2F;
                case "tyres" -> .8F + s.roll() * .3F;
                default -> 1F;
            };
        }

        private static float level(boolean inside, float cab, float loudness, double distance) {
            return inside ? cab : AircraftSoundRange.volume(distance, loudness);
        }

        private void follow(Vec3 position, boolean onCamera) {
            this.relative = onCamera;
            Vec3 at = onCamera ? Vec3.ZERO : position.add(0, 1.4, 0);
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }
    }
}
