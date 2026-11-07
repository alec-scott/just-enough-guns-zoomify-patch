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

/** Startup blends into a persistent engine loop; movement changes its pitch and gain without replacing the channel. */
public final class HelicopterEngineAudio {
    private static final double AUDIBLE_RANGE_SQR = 384.0D * 384.0D;
    private static final float OPERATING_ROTOR_SPEED = 0.085F;
    private static final int CROSSFADE_TICKS = 30;
    private static final long CROSSFADE_NANOS = CROSSFADE_TICKS * 50_000_000L;
    private static final long LISTENER_FADE_NANOS = 250_000_000L;
    private static final Map<Integer, EngineSound> SOUNDS = new HashMap<>();
    private static final Map<Integer, EngineSound> FADING = new HashMap<>();

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
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof VehicleEntity vehicle) || !audible(minecraft, vehicle)) {
                continue;
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
            this.attenuation = this.relative ? Attenuation.NONE : Attenuation.LINEAR;
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
            float listenerVolume = this.relative ? Math.min(0.65F, baseVolume * 0.45F) : baseVolume;
            this.volume = listenerVolume * this.rpmGain * this.mixGain;
        }
    }
}
