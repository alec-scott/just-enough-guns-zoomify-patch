package ttv.migami.jeg.vehicle.client.audio;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.VehicleSoundHelper;

/**
 * Startup blends into a persistent engine loop; movement changes its pitch and gain without replacing the channel. From
 * outside, the rotor carries a long way and fades with distance as AircraftSoundRange has it, giving way to a muffled
 * distant version further off; past entity range FarAircraftAudio carries on with that.
 */
public final class HelicopterEngineAudio {
    private static final double AUDIBLE_RANGE_SQR = AircraftSoundRange.MAX_RANGE * AircraftSoundRange.MAX_RANGE;
    private static final float OPERATING_ROTOR_SPEED = 0.085F;
    private static final int CROSSFADE_TICKS = 30;
    private static final long CROSSFADE_NANOS = CROSSFADE_TICKS * 50_000_000L;
    private static final long LISTENER_FADE_NANOS = 250_000_000L;
    private static final Map<Integer, EngineSound> SOUNDS = new HashMap<>();
    private static final Map<Integer, EngineSound> FADING = new HashMap<>();
    private static final Map<Integer, DistantSound> DISTANT = new HashMap<>();

    private HelicopterEngineAudio() {}

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            clear(minecraft);
            return;
        }
        SOUNDS.entrySet().removeIf(entry -> {
            EngineSound sound = entry.getValue();
            if (sound.vehicle.level() != minecraft.level || !audible(minecraft, sound.vehicle)) {
                minecraft.getSoundManager().stop(sound);
                sound.halt();
                return true;
            }
            return false;
        });
        FADING.entrySet().removeIf(entry -> {
            EngineSound sound = entry.getValue();
            if (sound.isStopped() || sound.vehicle.level() != minecraft.level || !audible(minecraft, sound.vehicle)) {
                minecraft.getSoundManager().stop(sound);
                sound.halt();
                return true;
            }
            return false;
        });
        DISTANT.entrySet().removeIf(entry -> {
            DistantSound sound = entry.getValue();
            if (sound.isStopped() || sound.vehicle.level() != minecraft.level || sound.vehicle.isRemoved()) {
                minecraft.getSoundManager().stop(sound);
                return true;
            }
            return false;
        });
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof VehicleEntity vehicle) || !audible(minecraft, vehicle)) {
                continue;
            }
            if (!isAboard(minecraft, vehicle) && !DISTANT.containsKey(vehicle.getId())) {
                SoundEvent distant = AircraftSoundRange.distantSound(vehicle.vehicleDataId().getPath(), "engine");
                if (distant != null) {
                    DistantSound sound = new DistantSound(vehicle, distant);
                    DISTANT.put(vehicle.getId(), sound);
                    minecraft.getSoundManager().play(sound);
                }
            }
            EngineSound current = SOUNDS.get(vehicle.getId());
            boolean starting = vehicle.helicopterStartupProgress() > 0.0F && vehicle.helicopterStartupProgress() < 1.0F;
            float remainingStartupTicks = (1.0F - vehicle.helicopterStartupProgress()) * vehicle.helicopterStartupTicks();
            boolean blendToFlight = current != null && current.startup
                    && (!starting || remainingStartupTicks <= CROSSFADE_TICKS || current.nearEndOfStartup());
            if (current != null && !current.isStopped() && current.isRelative() == isAboard(minecraft, vehicle) && !blendToFlight) {
                continue;
            }
            // A listener arriving halfway through startup hears the rotor at its current RPM, rather than replaying ignition.
            boolean playIgnition = current == null && starting && vehicle.helicopterStartupProgress() <= 0.05F;
            SoundEvent event = playIgnition ? VehicleSoundHelper.engineStartSound(vehicle) : VehicleSoundHelper.engineSound(vehicle);
            if (event != null) {
                EngineSound sound = new EngineSound(vehicle, event, playIgnition);
                if (!playIgnition) {
                    long duration = blendToFlight ? CROSSFADE_NANOS : LISTENER_FADE_NANOS;
                    sound.fadeIn(duration);
                    if (current != null && !current.isStopped()) {
                        EngineSound previousFade = FADING.put(vehicle.getId(), current);
                        if (previousFade != null) {
                            minecraft.getSoundManager().stop(previousFade);
                            previousFade.halt();
                        }
                        current.fadeOut(duration);
                    }
                }
                SOUNDS.put(vehicle.getId(), sound);
                minecraft.getSoundManager().play(sound);
            }
        }
    }

    private static boolean audible(Minecraft minecraft, VehicleEntity vehicle) {
        return vehicle.isAlive() && !vehicle.isRemoved()
                && vehicle.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER
                && minecraft.player.distanceToSqr(vehicle) <= AUDIBLE_RANGE_SQR
                && (vehicle.helicopterStartupProgress() > 0.0F || vehicle.propellerSpeed() > 0.0005F);
    }

    private static boolean isAboard(Minecraft minecraft, VehicleEntity vehicle) {
        return minecraft.player != null && minecraft.player.getRootVehicle() == vehicle;
    }

    public static void clear(Minecraft minecraft) {
        for (EngineSound sound : SOUNDS.values()) {
            minecraft.getSoundManager().stop(sound);
            sound.halt();
        }
        SOUNDS.clear();
        for (EngineSound sound : FADING.values()) {
            minecraft.getSoundManager().stop(sound);
            sound.halt();
        }
        FADING.clear();
        for (DistantSound sound : DISTANT.values()) {
            minecraft.getSoundManager().stop(sound);
        }
        DISTANT.clear();
    }

    /** For game tests: the volume of this helicopter's full and distant rotor sounds as heard now. */
    public static String describe(int vehicleId) {
        EngineSound engine = SOUNDS.get(vehicleId);
        DistantSound distant = DISTANT.get(vehicleId);
        return String.format(java.util.Locale.ROOT, "engineVol=%.3f distantVol=%.3f",
                engine == null || engine.isStopped() ? 0.0F : engine.getVolume(),
                distant == null || distant.isStopped() ? 0.0F : distant.getVolume());
    }

    private static double listenerDistance(VehicleEntity vehicle) {
        Entity listener = Minecraft.getInstance().getCameraEntity();
        return listener == null ? 0.0D : listener.position().distanceTo(vehicle.position().add(0.0D, 1.5D, 0.0D));
    }

    /** Outside: the muffled distant rotor, which takes over from the full one with distance. */
    private static final class DistantSound extends AbstractTickableSoundInstance {
        private final VehicleEntity vehicle;
        private float gain;

        DistantSound(VehicleEntity vehicle, SoundEvent event) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.vehicle = vehicle;
            this.looping = true;
            this.delay = 0;
            this.attenuation = Attenuation.NONE;
            this.volume = 0.001F;
            this.update();
        }

        @Override
        public void tick() {
            if (this.vehicle.isRemoved()) {
                this.stop();
                return;
            }
            this.update();
            Minecraft minecraft = Minecraft.getInstance();
            if (this.gain <= 0.002F && (minecraft.player == null || !audible(minecraft, this.vehicle) || isAboard(minecraft, this.vehicle))) {
                this.stop();
            }
        }

        private void update() {
            Minecraft minecraft = Minecraft.getInstance();
            this.x = this.vehicle.getX();
            this.y = this.vehicle.getY() + 1.5D;
            this.z = this.vehicle.getZ();
            boolean on = minecraft.player != null && audible(minecraft, this.vehicle) && !isAboard(minecraft, this.vehicle);
            double distance = listenerDistance(this.vehicle);
            float target = on ? AircraftSoundRange.volume(distance, VehicleSoundHelper.aircraftEngineLoudness(this.vehicle))
                    * AircraftSoundRange.distantShare(distance) : 0.0F;
            this.gain = Mth.lerp(0.12F, this.gain, target);
            this.volume = Math.max(0.001F, this.gain);
            Entity listener = minecraft.getCameraEntity();
            float doppler = 1.0F;
            if (listener != null) {
                doppler = VehicleDoppler.multiplier(this.vehicle.position(), this.vehicle.getDeltaMovement(),
                        listener.position(), listener.getRootVehicle().getDeltaMovement());
            }
            this.pitch = Mth.clamp(VehicleSoundHelper.aircraftEnginePitch(this.vehicle) * doppler, 0.5F, 2.0F);
        }
    }

    public static String phase(int vehicleId) {
        EngineSound sound = SOUNDS.get(vehicleId);
        return sound == null ? "SILENT" : sound.vehicle.helicopterStartupProgress() > 0.0F
                && sound.vehicle.helicopterStartupProgress() < 1.0F ? "STARTUP"
                : sound.vehicle.helicopterStartupProgress() == 0.0F ? "COASTING" : "FLIGHT";
    }

    private static final class EngineSound extends AbstractTickableSoundInstance {
        private final VehicleEntity vehicle;
        private final boolean startup;
        private final long startedAtNanos = System.nanoTime();
        private long fadeStartedAtNanos;
        private long fadeDurationNanos;
        private boolean fadingOut;
        private float fadeFrom = 1.0F;
        private float mixGain = 1.0F;
        private float rpmGain = -1.0F;

        EngineSound(VehicleEntity vehicle, SoundEvent event, boolean startup) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.vehicle = vehicle;
            this.startup = startup;
            this.looping = !startup;
            this.delay = 0;
            this.pitch = 1.0F;
            this.relative = isAboard(Minecraft.getInstance(), vehicle);
            // Outside, it sets its own fade with distance (AircraftSoundRange).
            this.attenuation = Attenuation.NONE;
            this.update();
        }

        void halt() {
            this.stop();
        }

        boolean nearEndOfStartup() {
            return System.nanoTime() - this.startedAtNanos
                    >= (this.vehicle.helicopterStartupTicks() - CROSSFADE_TICKS) * 50_000_000L;
        }

        void fadeIn(long duration) {
            this.fadeStartedAtNanos = System.nanoTime();
            this.fadeDurationNanos = duration;
            this.fadingOut = false;
            this.mixGain = 0.001F; // SoundEngine must receive nonzero gain when opening the channel.
            this.update();
        }

        void fadeOut(long duration) {
            this.fadeStartedAtNanos = System.nanoTime();
            this.fadeDurationNanos = duration;
            this.fadingOut = true;
            this.fadeFrom = this.mixGain;
        }

        @Override
        public void tick() {
            if (this.vehicle.isRemoved()) {
                this.stop();
                return;
            }
            if (this.fadeDurationNanos > 0L) {
                float progress = Mth.clamp((System.nanoTime() - this.fadeStartedAtNanos) / (float) this.fadeDurationNanos, 0.0F, 1.0F);
                float angle = progress * Mth.HALF_PI;
                this.mixGain = this.fadingOut ? this.fadeFrom * Mth.cos(angle) : Math.max(0.001F, Mth.sin(angle));
                if (this.fadingOut && progress >= 1.0F) {
                    this.stop();
                    return;
                }
            }
            this.update();
        }

        private void update() {
            this.x = this.relative ? 0.0D : this.vehicle.getX();
            this.y = this.relative ? 0.0D : this.vehicle.getY() + 1.5D;
            this.z = this.relative ? 0.0D : this.vehicle.getZ();
            Minecraft minecraft = Minecraft.getInstance();
            Entity listener = minecraft.getCameraEntity();
            float doppler = 1.0F;
            if (!this.startup && listener != null && listener.getRootVehicle() != this.vehicle) {
                doppler = VehicleDoppler.multiplier(this.vehicle.position(), this.vehicle.getDeltaMovement(),
                        listener.position(), listener.getRootVehicle().getDeltaMovement());
            }
            float rpm = Mth.clamp(this.vehicle.propellerSpeed() / OPERATING_ROTOR_SPEED, 0.0F, 1.25F);
            float basePitch = this.startup ? 1.0F : Mth.clamp(0.35F + rpm * 0.65F, 0.35F, 1.12F);
            this.pitch = Mth.lerp(0.25F, this.pitch, basePitch * doppler);
            float targetGain = this.startup ? 1.0F : Math.min(rpm, 1.0F);
            this.rpmGain = this.rpmGain < 0.0F ? targetGain : Mth.lerp(0.12F, this.rpmGain, targetGain);
            float baseVolume = Math.max(0.1F, this.vehicle.vehicleData().defaults().engine().engineSoundVolume());
            if (this.relative) {
                this.volume = Math.min(0.65F, baseVolume * 0.45F) * this.rpmGain * this.mixGain;
            } else {
                double distance = listenerDistance(this.vehicle);
                this.volume = Math.max(0.001F, AircraftSoundRange.volume(distance, baseVolume * this.rpmGain)
                        * AircraftSoundRange.nearShare(distance) * this.mixGain);
            }
        }
    }
}
