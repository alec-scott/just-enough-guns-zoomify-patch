package ttv.migami.jeg.vehicle.client.audio;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.network.AircraftTrackPayload;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Aircraft heard from beyond the range the server sends the vehicles themselves (its view distance, 192 blocks on yftm),
 * out to {@link AircraftSoundRange#MAX_RANGE}. The server sends where each one is every few ticks (AircraftTrackPayload);
 * from those this plays its distant engine, and a jet's distant gun while it fires, where it was when the sound now
 * arriving left it (a tick per 17 blocks), with Doppler. Once the vehicle itself comes into range its own sounds
 * (JetAudio, HelicopterEngineAudio) take over, at the same volume.
 */
public final class FarAircraftAudio {
    /** Speed of sound, blocks per tick (343 m/s). */
    private static final double SOUND_SPEED = 343.0D / 20.0D;
    /** Without a fresh track for this long, an aircraft has gone out of earshot or out of the level. */
    private static final int STALE_TICKS = 30;
    /** Long enough to hear one from the edge of earshot as it was (1024 blocks is 60 ticks of sound). */
    private static final int HISTORY_TICKS = 80;
    private static final float FADE_PER_TICK = 0.08F;
    private static final Map<Integer, Far> AIRCRAFT = new HashMap<>();
    private static long tick;

    private FarAircraftAudio() {}

    /** One track from the server. */
    public static void track(AircraftTrackPayload payload) {
        Far far = AIRCRAFT.computeIfAbsent(payload.entityId(), id -> new Far(payload.entityId(), payload.vehicleId()));
        far.tracks.addFirst(new Track(tick, payload.position(), payload.motion(), payload.loudness(), payload.pitch(), payload.gunFiring()));
        while (far.tracks.size() > 2 && tick - far.tracks.peekLast().tick > HISTORY_TICKS) {
            far.tracks.removeLast();
        }
    }

    public static void tick(Minecraft minecraft) {
        tick++;
        if (minecraft.level == null || minecraft.player == null) {
            clear(minecraft);
            return;
        }
        Entity listener = minecraft.getCameraEntity() == null ? minecraft.player : minecraft.getCameraEntity();
        Iterator<Far> iterator = AIRCRAFT.values().iterator();
        while (iterator.hasNext()) {
            Far far = iterator.next();
            boolean own = minecraft.level.getEntity(far.id) instanceof VehicleEntity;
            Track latest = far.tracks.peekFirst();
            if (own || latest == null || tick - latest.tick > STALE_TICKS) {
                // In entity range now (its own sounds play), or gone: let the distant ones fade out.
                far.engine.fadeOut();
                far.gun.fadeOut();
                if (far.engine.silent() && far.gun.silent()) {
                    far.engine.end(minecraft);
                    far.gun.end(minecraft);
                    iterator.remove();
                }
                continue;
            }
            far.heard = heard(far, listener.position());
            far.engine.ensure(minecraft, AircraftSoundRange.distantSound(far.vehicleId, "engine"));
            if (far.heard.gunFiring()) {
                // The second far sound is a jet's gun, or for a vehicle without one its horn.
                SoundEvent signal = AircraftSoundRange.distantSound(far.vehicleId, "fire");
                far.gun.ensure(minecraft, signal != null ? signal : AircraftSoundRange.distantSound(far.vehicleId, "horn"));
            } else {
                far.gun.fadeOut();
            }
        }
    }

    /** Where the aircraft was when the sound reaching the listener now left it, from the tracks around that moment. */
    private static Heard heard(Far far, Vec3 listener) {
        Track chosen = far.tracks.peekFirst();
        double at = tick;
        for (Track track : far.tracks) {
            Vec3 position = track.position.add(track.motion.scale(tick - track.tick));
            double delay = position.distanceTo(listener) / SOUND_SPEED;
            at = tick - delay;
            chosen = track;
            if (track.tick <= at) {
                break;
            }
        }
        Vec3 position = chosen.position.add(chosen.motion.scale(at - chosen.tick));
        return new Heard(position, chosen.motion, chosen.loudness, chosen.pitch, chosen.gunFiring);
    }

    public static void clear(Minecraft minecraft) {
        for (Far far : AIRCRAFT.values()) {
            far.engine.end(minecraft);
            far.gun.end(minecraft);
        }
        AIRCRAFT.clear();
    }

    /** For game tests: each far aircraft heard now, with its distance and the volume of its engine and gun. */
    public static String describe() {
        Minecraft minecraft = Minecraft.getInstance();
        Entity listener = minecraft.getCameraEntity();
        StringBuilder out = new StringBuilder();
        for (Far far : AIRCRAFT.values()) {
            if (far.heard == null || listener == null) {
                continue;
            }
            out.append(String.format(java.util.Locale.ROOT, "far[%d %s dist=%.0f engine=%.3f gun=%.3f] ", far.id, far.vehicleId,
                    far.heard.position.distanceTo(listener.position()), far.engine.volume(), far.gun.volume()));
        }
        return out.toString().trim();
    }

    /** Volume of the far engine of this aircraft (0 when none is playing), for game tests. */
    public static float engineVolume(int entityId) {
        Far far = AIRCRAFT.get(entityId);
        return far == null ? 0.0F : far.engine.volume();
    }

    /** Volume of the far gun of this aircraft (0 when none is playing), for game tests. */
    public static float gunVolume(int entityId) {
        Far far = AIRCRAFT.get(entityId);
        return far == null ? 0.0F : far.gun.volume();
    }

    private record Track(long tick, Vec3 position, Vec3 motion, float loudness, float pitch, boolean gunFiring) {}

    private record Heard(Vec3 position, Vec3 motion, float loudness, float pitch, boolean gunFiring) {}

    private static final class Far {
        private final int id;
        private final String vehicleId;
        private final ArrayDeque<Track> tracks = new ArrayDeque<>();
        private final Channel engine = new Channel(this, false);
        private final Channel gun = new Channel(this, true);
        private Heard heard;

        Far(int id, String vehicleId) {
            this.id = id;
            this.vehicleId = vehicleId;
        }
    }

    /** One of a far aircraft's sounds: started when needed, faded out rather than cut. */
    private static final class Channel {
        private final Far far;
        private final boolean gun;
        private Sound sound;
        private boolean fading;

        Channel(Far far, boolean gun) {
            this.far = far;
            this.gun = gun;
        }

        void ensure(Minecraft minecraft, SoundEvent event) {
            this.fading = false;
            if (event == null || (this.sound != null && !this.sound.isStopped())) {
                return;
            }
            this.sound = new Sound(this.far, event, this.gun, this);
            minecraft.getSoundManager().play(this.sound);
        }

        void fadeOut() {
            this.fading = true;
        }

        boolean silent() {
            return this.sound == null || this.sound.isStopped() || this.sound.getVolume() <= 0.002F;
        }

        void end(Minecraft minecraft) {
            if (this.sound != null) {
                minecraft.getSoundManager().stop(this.sound);
                this.sound = null;
            }
        }

        float volume() {
            return this.sound == null || this.sound.isStopped() ? 0.0F : this.sound.getVolume();
        }
    }

    private static final class Sound extends AbstractTickableSoundInstance {
        private final Far far;
        private final boolean gun;
        private final Channel channel;
        private float fade;

        Sound(Far far, SoundEvent event, boolean gun, Channel channel) {
            super(event, gun ? SoundSource.PLAYERS : SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.far = far;
            this.gun = gun;
            this.channel = channel;
            this.looping = true;
            this.delay = 0;
            this.attenuation = Attenuation.NONE;
            // SoundEngine discards a sound that opens silent; start barely audible and fade in.
            this.volume = 0.001F;
            this.update();
        }

        @Override
        public void tick() {
            this.update();
            if (this.channel.fading && this.fade <= 0.0F) {
                this.stop();
            }
        }

        private void update() {
            Heard heard = this.far.heard;
            Entity listener = Minecraft.getInstance().getCameraEntity();
            if (heard == null || listener == null) {
                return;
            }
            this.fade = Mth.clamp(this.fade + (this.channel.fading ? -FADE_PER_TICK : FADE_PER_TICK), 0.0F, 1.0F);
            this.x = heard.position.x;
            this.y = heard.position.y + 2.0D;
            this.z = heard.position.z;
            double distance = heard.position.distanceTo(listener.position());
            float loudness = this.gun ? JetAudio.GUN_LOUDNESS : heard.loudness;
            float share = AircraftSoundRange.distantShare(distance);
            this.volume = Math.max(0.001F, AircraftSoundRange.volume(distance, loudness) * share * this.fade);
            float doppler = VehicleDoppler.multiplier(heard.position, heard.motion, listener.position(), listener.getRootVehicle().getDeltaMovement());
            this.pitch = Mth.clamp((this.gun ? 1.0F : heard.pitch) * doppler, 0.5F, 2.0F);
        }
    }
}
