package ttv.migami.jeg.vehicle.client.audio;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModSounds;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.GroundVehicleSounds;

/**
 * The armoured vehicles' engines (fleet plan Phase 0.1), heard the way the aircraft and the BD-40 are (see
 * {@link AircraftSoundRange}): the engine is one voice crossfaded between its idle and working loops as it speeds up, a
 * tracked hull adds the track clank by the tracks' speed, and an enclosed crew in first person hears the muffled cab loop
 * instead. Outside, every loop fades on the shared distance curve out to 1024 blocks, crossfades into the distant loop,
 * and is heard as the vehicle was when the sound left it (a tick per 17 blocks), with Doppler; past entity range
 * {@link FarAircraftAudio} carries on from the server's tracks. Loops are static buffers that survive view changes
 * (DynamicRelativeSound). The engine winds up over a second when a driver takes the controls and runs down when they
 * leave. Which vehicles, and how loud: {@link GroundVehicleSounds}.
 */
public final class GroundVehicleAudio {
    private static final Map<Integer, Group> GROUPS = new HashMap<>();
    private static final String[] LOOPS = {"engine_idle", "engine_load", "engine_cab", "engine_distant", "tracks"};
    private static final double SOUND_SPEED = 343.0D / 20.0D;
    private static final int HISTORY = 70;
    private static final float TRACK_LOUDNESS = 0.9F;
    private static final int WIND_UP_TICKS = 24;

    private GroundVehicleAudio() {}

    /** The vehicle's sound-making state one tick, kept so listeners further off hear it as it was. */
    private record State(Vec3 position, Vec3 motion, float rpm, float track, float power) {}

    private static final class Group {
        final VehicleEntity entity;
        final GroundVehicleSounds profile;
        final Map<String, Layer> layers = new HashMap<>();
        final ArrayDeque<State> history = new ArrayDeque<>();
        Vec3 lastPosition;
        float lastTrack, rpm, track, power;

        Group(VehicleEntity entity, GroundVehicleSounds profile) {
            this.entity = entity;
            this.profile = profile;
            this.lastPosition = entity.position();
            this.lastTrack = entity.trackTravel(true, 1.0F);
        }

        State heard(double distance) {
            int delay = Mth.clamp((int) Math.round(distance / SOUND_SPEED), 0, Math.max(0, this.history.size() - 1));
            int i = 0;
            for (State state : this.history) if (i++ == delay) return state;
            return this.history.peekFirst();
        }

        void stop(Minecraft mc) {
            for (Layer layer : this.layers.values()) {
                layer.halt();
                mc.getSoundManager().stop(layer);
            }
            this.layers.clear();
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            clear(mc);
            return;
        }
        var seen = new HashSet<Integer>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof VehicleEntity vehicle)) continue;
            GroundVehicleSounds profile = vehicle.groundSounds();
            if (profile == null || distanceSq(vehicle) > AircraftSoundRange.MAX_RANGE * AircraftSoundRange.MAX_RANGE) continue;
            Group group = GROUPS.get(vehicle.getId());
            if (group != null && group.entity != vehicle) {
                group.stop(mc);
                GROUPS.remove(vehicle.getId());
                group = null;
            }
            boolean running = vehicle.groundEngineRunning();
            if (group == null) {
                if (!running) continue;
                group = new Group(vehicle, profile);
                GROUPS.put(vehicle.getId(), group);
            }
            seen.add(vehicle.getId());
            // Engine speed from how fast the hull moves (any client sees that, driver or not), eased as an engine's is.
            double maxSpeed = Math.max(0.05D, vehicle.vehicleData().defaults().engine().maxForwardSpeed());
            double moved = vehicle.position().subtract(group.lastPosition).horizontalDistance();
            group.lastPosition = vehicle.position();
            float target = running ? 0.15F + 0.85F * (float) Mth.clamp(moved / maxSpeed, 0.0D, 1.0D) : 0.0F;
            group.rpm = Mth.lerp(0.12F, group.rpm, target);
            float travel = vehicle.trackTravel(true, 1.0F);
            float trackRate = Math.abs(travel - group.lastTrack) / (float) (maxSpeed * 16.0D);
            group.lastTrack = travel;
            group.track = Mth.lerp(0.3F, group.track, Mth.clamp(trackRate, 0.0F, 1.0F));
            group.power = Mth.clamp(group.power + (running ? 1.0F : -1.5F) / WIND_UP_TICKS, 0.0F, 1.0F);
            group.history.addFirst(new State(vehicle.position(), vehicle.getDeltaMovement(), group.rpm, group.track, group.power));
            while (group.history.size() > HISTORY) group.history.removeLast();
            if (group.layers.isEmpty() && group.power > 0.0F) {
                for (String name : LOOPS) {
                    if (name.equals("tracks") && !profile.tracked() || name.equals("engine_cab") && !profile.cab()) continue;
                    Layer sound = new Layer(group, name);
                    group.layers.put(name, sound);
                    mc.getSoundManager().play(sound);
                }
            }
            if (group.power <= 0.0F && !running && !group.layers.isEmpty() && group.history.size() >= HISTORY) {
                // Wound down and heard everywhere it will be: stop the buffers until a driver comes back.
                group.stop(mc);
            }
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

    public static void clear(Minecraft mc) {
        GROUPS.values().forEach(g -> g.stop(mc));
        GROUPS.clear();
    }

    /** For game tests: a vehicle's playing loops by name, and one's volume. */
    public static Map<String, SoundInstance> loops(int entityId) {
        Group g = GROUPS.get(entityId);
        return g == null ? Map.of() : Map.copyOf(g.layers);
    }

    public static float loopVolume(int entityId, String name) {
        Group g = GROUPS.get(entityId);
        Layer layer = g == null ? null : g.layers.get(name);
        return layer == null ? 0.0F : layer.getVolume();
    }

    /** The local player is riding this vehicle (in any camera view). */
    public static boolean aboard(VehicleEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getRootVehicle() == entity;
    }

    /** Aboard in first person: the crew hears the vehicle around them rather than from a point. */
    public static boolean firstPersonAboard(VehicleEntity entity) {
        return aboard(entity) && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    private static double distanceSq(VehicleEntity entity) {
        return Minecraft.getInstance().gameRenderer.getMainCamera().position().distanceToSqr(entity.position());
    }

    private static SoundEvent sound(VehicleEntity vehicle, String kind) {
        String id = kind.equals("tracks") ? "vehicle.tracks.clank" : "vehicle." + vehicle.vehicleDataId().getPath() + "." + kind;
        return ModSounds.ALL.get(Reference.id(id)).get();
    }

    private static final class Layer extends AbstractTickableSoundInstance implements DynamicRelativeSound {
        final Group group;
        final String kind;
        float smoothed;

        Layer(Group group, String kind) {
            super(sound(group.entity, kind), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.group = group;
            this.kind = kind;
            this.looping = true;
            this.delay = 0;
            this.attenuation = Attenuation.NONE;
            this.volume = 0.001F;
            this.pitch = 1.0F;
            this.follow(group.entity.position(), firstPersonAboard(group.entity));
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        void halt() {
            this.stop();
        }

        @Override
        public void tick() {
            VehicleEntity v = this.group.entity;
            if (v.isRemoved()) {
                this.stop();
                return;
            }
            boolean inside = aboard(v);
            boolean cabView = this.group.profile.cab() && firstPersonAboard(v);
            double distance = inside ? 0.0D : Math.sqrt(distanceSq(v));
            State s = this.group.heard(distance);
            if (s == null) return;
            this.follow(s.position(), firstPersonAboard(v));
            float rpm = s.rpm();
            float throttle = Mth.clamp((rpm - 0.15F) / 0.85F, 0.0F, 1.0F);
            float loudness = this.group.profile.loudness(throttle);
            float near = inside ? 1.0F : AircraftSoundRange.nearShare(distance);
            float far = inside ? 0.0F : AircraftSoundRange.distantShare(distance);
            float swing = throttle * Mth.HALF_PI;
            float target = switch (this.kind) {
                case "engine_idle" -> cabView ? 0.0F : level(inside, 0.55F, loudness, distance) * Mth.cos(swing) * near;
                case "engine_load" -> cabView ? 0.0F : level(inside, 0.65F, loudness, distance) * Mth.sin(swing) * near;
                case "engine_cab" -> cabView ? 0.35F + 0.4F * throttle : 0.0F;
                case "engine_distant" -> inside ? 0.0F : AircraftSoundRange.volume(distance, loudness) * far;
                case "tracks" -> level(inside, cabView ? 0.18F : 0.3F, TRACK_LOUDNESS, distance) * s.track() * near;
                default -> 0.0F;
            };
            target *= s.power();
            this.smoothed = Mth.lerp(0.25F, this.smoothed, target);
            this.volume = Math.max(0.001F, this.smoothed);
            float doppler = 1.0F;
            var listener = Minecraft.getInstance().getCameraEntity();
            if (!inside && listener != null && listener.getRootVehicle() != v) {
                doppler = Mth.clamp(VehicleDoppler.multiplier(s.position(), s.motion(), listener.position(), listener.getDeltaMovement()), 0.95F, 1.05F);
            }
            // Winding up and down, the engine's pitch follows its power.
            float wind = 0.7F + 0.3F * s.power();
            this.pitch = doppler * switch (this.kind) {
                case "engine_idle" -> wind * (0.92F + rpm * 0.3F);
                case "engine_load" -> wind * (0.82F + rpm * 0.24F);
                case "engine_cab" -> wind * (0.86F + rpm * 0.26F);
                case "engine_distant" -> wind * (0.9F + rpm * 0.18F);
                case "tracks" -> 0.8F + s.track() * 0.4F;
                default -> 1.0F;
            };
        }

        /** Centred level aboard; outside, the aircraft distance curve at this source's loudness. */
        private static float level(boolean inside, float aboard, float loudness, double distance) {
            return inside ? aboard : AircraftSoundRange.volume(distance, loudness);
        }

        private void follow(Vec3 position, boolean onCamera) {
            this.relative = onCamera;
            Vec3 at = onCamera ? Vec3.ZERO : position.add(0.0D, 1.6D, 0.0D);
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }
    }
}
