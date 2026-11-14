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
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;
import ttv.migami.jeg.vehicle.terrain.BulldozerTerrainWork;

/**
 * The BD-40's sound, heard the way the aircraft are (see {@link AircraftSoundRange}). The diesel is one voice
 * crossfaded between real idle and working recordings as engine speed rises; tracks, the blade's load, hydraulics,
 * the reverse alarm and the horn have their own channels. Outside, every channel fades on the shared distance curve
 * out to 1024 blocks, the engine crossfades into its muffled distant recording, and what is heard is the machine as
 * it was when the sound left it (a tick per 17 blocks), with Doppler. Past entity range {@link FarAircraftAudio}
 * carries on from the server's tracks. In first person the operator hears it centred in listener coordinates.
 * DynamicRelativeSound lets the same channels become positional in third person without restarting the buffers.
 */
public final class BulldozerAudio {
    private static final Map<Integer, Group> GROUPS = new HashMap<>();
    private static final String[] LOOPS = {"engine_idle", "engine_load", "engine_distant", "tracks", "blade", "hydraulic", "reverse", "horn"};
    /** Speed of sound, blocks per tick (343 m/s). */
    private static final double SOUND_SPEED = 343.0D / 20.0D;
    private static final int HISTORY = 40;
    // Loudness before distance (AircraftSoundRange): the A-10 flat out is 2.5, a helicopter's gun 2.5.
    private static final float ENGINE_IDLE_LOUDNESS = 1.0F, ENGINE_WORK_LOUDNESS = 1.6F;
    private static final float TRACK_LOUDNESS = .8F, BLADE_LOUDNESS = 1.0F, HYDRAULIC_LOUDNESS = .45F;
    private static final float ALARM_LOUDNESS = .9F, HORN_LOUDNESS = 2.5F, IMPACT_LOUDNESS = 1.2F, ONE_SHOT_ENGINE_LOUDNESS = 1.4F;
    private BulldozerAudio() {}

    /** The machine's sound-making state one tick, kept so listeners further off hear it as it was. */
    private record State(Vec3 position, Vec3 motion, float rpm, float load, float track, boolean hydraulic, boolean reversing,
                         boolean horn, boolean working, float startup) {}

    private static final class Group {
        final BulldozerEntity entity;
        final Map<String, Layer> layers = new HashMap<>();
        final List<Layer> shots = new ArrayList<>();
        final ArrayDeque<State> history = new ArrayDeque<>();
        boolean wasMoving, wasWorking, wasPowered;
        float gain = 1, trackSpeed;
        int impactCooldown;
        Group(BulldozerEntity entity) { this.entity = entity; }

        /** The state heard now from this distance: as it was {@code distance / SOUND_SPEED} ticks ago. */
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

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { clear(mc); return; }
        var seen = new HashSet<Integer>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof BulldozerEntity dozer) || distanceSq(dozer) > AircraftSoundRange.MAX_RANGE * AircraftSoundRange.MAX_RANGE) continue;
            Group group = GROUPS.get(dozer.getId());
            if (group != null && group.entity != dozer) { group.stop(mc); GROUPS.remove(dozer.getId()); group = null; }
            boolean powered = dozer.powered();
            if (group == null) {
                if (!powered) continue;
                group = new Group(dozer);
                GROUPS.put(dozer.getId(), group);
                // An observer arriving at a running machine hears it running, never a second start.
                if (dozer.powerAge() <= 3) group.shot(mc, "engine_start", ONE_SHOT_ENGINE_LOUDNESS, 1, 70);
            }
            seen.add(dozer.getId());
            float tracks = Math.max(Math.abs(dozer.leftTravel(1) - dozer.leftTravel(0)), Math.abs(dozer.rightTravel(1) - dozer.rightTravel(0)));
            group.trackSpeed = Mth.lerp(.3F, group.trackSpeed, tracks);
            group.history.addFirst(new State(dozer.position(), dozer.getDeltaMovement(), dozer.engineRpm(1), dozer.load(),
                    Mth.clamp(group.trackSpeed / .2F, 0, 1), dozer.hydraulicMoving(), dozer.engineRunning() && dozer.travelSpeed() < -.01,
                    dozer.hornOn(), dozer.engineRunning() && dozer.bladeLift() <= 22,
                    Mth.clamp((dozer.powerAge() - (BulldozerEntity.START_TICKS - 14)) / 16F, 0, 1)));
            while (group.history.size() > HISTORY) group.history.removeLast();
            if (powered && group.layers.isEmpty()) {
                for (String name : LOOPS) {
                    Layer sound = new Layer(group, name, true, 0, 0);
                    group.layers.put(name, sound);
                    mc.getSoundManager().play(sound);
                }
            }
            if (group.wasPowered && !powered) group.shot(mc, "engine_stop", ONE_SHOT_ENGINE_LOUDNESS, 1, 56);
            group.wasPowered = powered;
            if (!powered) {
                group.gain *= .7F;
                if (group.gain < .01F && !group.layers.isEmpty()) group.stopLoops(mc);
            } else {
                group.gain = Mth.lerp(.25F, group.gain, 1);
            }
            boolean moving = dozer.hydraulicMoving();
            if (group.wasMoving && !moving && powered) group.shot(mc, "hydraulic_stop", HYDRAULIC_LOUDNESS, 1, 8);
            group.wasMoving = moving;
            int status = dozer.workStatus();
            boolean working = status == BulldozerTerrainWork.WORKING || status == BulldozerTerrainWork.BUSY
                    || status == BulldozerTerrainWork.SPOIL || status == BulldozerTerrainWork.TREE;
            boolean struck = status == BulldozerTerrainWork.SOLID && group.trackSpeed > .02F;
            if (group.impactCooldown > 0) group.impactCooldown--;
            if ((working && !group.wasWorking || struck) && group.impactCooldown == 0) {
                group.shot(mc, "blade_impact", struck ? IMPACT_LOUDNESS * 1.3F : IMPACT_LOUDNESS, .9F + dozer.getRandom().nextFloat() * .15F, 14);
                group.impactCooldown = 30;
            }
            group.wasWorking = working;
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
    /** The playing loop instances of a dozer, by layer name (for audio checks). */
    public static Map<String, SoundInstance> loops(int entityId) {
        Group g = GROUPS.get(entityId);
        return g == null ? Map.of() : Map.copyOf(g.layers);
    }
    public static float loopPitch(int entityId, String name) {
        Group g = GROUPS.get(entityId);
        Layer layer = g == null ? null : g.layers.get(name);
        return layer == null ? 0 : layer.getPitch();
    }

    /** The local player is riding this machine (in any camera view). */
    public static boolean aboard(BulldozerEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() == entity;
    }

    /** The operator in first person hears the machine around them rather than from a point. */
    public static boolean cab(BulldozerEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() == entity && mc.options.getCameraType().isFirstPerson();
    }

    private static double distanceSq(BulldozerEntity entity) {
        return Minecraft.getInstance().gameRenderer.getMainCamera().position().distanceToSqr(entity.position());
    }

    private static final class Layer extends AbstractTickableSoundInstance implements DynamicRelativeSound {
        final Group group;
        final String kind;
        final float loudness;
        final int lifetime;
        float smoothed;
        int age;

        Layer(Group group, String kind, boolean loop, float loudness, int lifetime) {
            super(ModSounds.ALL.get(Reference.id("vehicle.bulldozer." + kind)).get(),
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
            return aboard(this.group.entity) ? Math.min(1, this.loudness * .7F) : AircraftSoundRange.volume(Math.sqrt(distanceSq(this.group.entity)), this.loudness);
        }

        @Override public void tick() {
            BulldozerEntity d = this.group.entity;
            if (d.isRemoved()) { this.stop(); return; }
            if (!this.looping) {
                // The engine retires finished buffers without marking the instance stopped; retire it here too.
                if (++this.age > this.lifetime) { this.stop(); return; }
                this.follow(d.position(), cab(d));
                this.volume = Math.max(.001F, this.oneShotVolume());
                return;
            }
            // Aboard, first or third person, the operator hears the same mix: only where it sits changes.
            boolean inside = aboard(d);
            double distance = inside ? 0 : Math.sqrt(distanceSq(d));
            State s = this.group.heard(distance);
            if (s == null) return;
            this.follow(s.position(), cab(d));
            float rpm = s.rpm(), throttle = rpm * Mth.HALF_PI;
            float near = inside ? 1 : AircraftSoundRange.nearShare(distance);
            float far = inside ? 0 : AircraftSoundRange.distantShare(distance);
            float engineLoudness = Mth.lerp(rpm, ENGINE_IDLE_LOUDNESS, ENGINE_WORK_LOUDNESS);
            float target = switch (this.kind) {
                case "engine_idle" -> level(inside, .62F, engineLoudness, distance) * Mth.cos(throttle) * near;
                case "engine_load" -> level(inside, .7F, engineLoudness, distance) * Mth.sin(throttle) * near;
                case "engine_distant" -> inside ? 0 : AircraftSoundRange.volume(distance, engineLoudness) * far;
                case "tracks" -> level(inside, .32F, TRACK_LOUDNESS, distance) * s.track() * near;
                case "blade" -> s.working() ? level(inside, .55F, BLADE_LOUDNESS, distance) * s.load() * near : 0;
                case "hydraulic" -> s.hydraulic() ? level(inside, .16F, HYDRAULIC_LOUDNESS, distance) * near : 0;
                case "reverse" -> s.reversing() ? level(inside, .1F, ALARM_LOUDNESS, distance) : 0;
                case "horn" -> s.horn() ? level(inside, .55F, HORN_LOUDNESS, distance) : 0;
                default -> 0;
            };
            if (!this.kind.equals("horn")) target *= s.startup();
            // A horn snaps on and off; the machinery eases.
            float ease = this.kind.equals("horn") ? .7F : .25F;
            this.smoothed = Mth.lerp(ease, this.smoothed, target * this.group.gain);
            this.volume = Math.max(.001F, this.smoothed);
            float doppler = 1;
            var listener = Minecraft.getInstance().getCameraEntity();
            if (!inside && listener != null && listener.getRootVehicle() != d) {
                doppler = Mth.clamp(VehicleDoppler.multiplier(s.position(), s.motion(), listener.position(), listener.getDeltaMovement()), .95F, 1.05F);
            }
            this.pitch = doppler * switch (this.kind) {
                case "engine_idle" -> .9F + rpm * .34F;
                case "engine_load" -> .8F + rpm * .26F;
                case "engine_distant" -> .88F + rpm * .2F;
                case "tracks" -> .78F + s.track() * .4F;
                case "blade" -> .88F + s.load() * .16F;
                default -> 1F;
            };
        }

        /** Centred level in first person; outside, the aircraft distance curve at this source's loudness. */
        private static float level(boolean inside, float cab, float loudness, double distance) {
            return inside ? cab : AircraftSoundRange.volume(distance, loudness);
        }

        private void follow(Vec3 position, boolean onCamera) {
            this.relative = onCamera;
            Vec3 at = onCamera ? Vec3.ZERO : position.add(0, 1.6, 0);
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }
    }
}
