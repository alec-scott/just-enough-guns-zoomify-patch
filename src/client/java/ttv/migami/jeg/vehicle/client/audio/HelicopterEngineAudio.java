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
import net.neoforged.neoforge.registries.DeferredHolder;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModSounds;
import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.VehicleSoundHelper;

/**
 * Startup blends into a persistent engine loop; movement changes its pitch and gain without replacing the channel. From
 * outside, the rotor carries a long way and fades with distance as AircraftSoundRange has it, giving way to a muffled
 * distant version further off; past entity range FarAircraftAudio carries on with that.
 *
 * <p>Two optional layers, where the helicopter has the sounds: {@code vehicle.<id>.slap}, the blades' slap, rising while it
 * comes down (blade-vortex interaction: the sound of a helicopter landing) or banks hard at speed; and
 * {@code vehicle.<id>.shutdown}, played once when the engines are cut (on landing), the turbines spooling down while the
 * rotor coasts.
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
    private static final Map<Integer, SlapSound> SLAPS = new HashMap<>();
    private static final Map<Integer, Float> LAST_STARTUP = new HashMap<>();
    private static SoundInstance lastShutdown;

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
        SLAPS.entrySet().removeIf(entry -> {
            SlapSound sound = entry.getValue();
            if (sound.isStopped() || sound.vehicle.level() != minecraft.level || sound.vehicle.isRemoved()) {
                minecraft.getSoundManager().stop(sound);
                return true;
            }
            return false;
        });
        LAST_STARTUP.keySet().removeIf(id -> !(minecraft.level.getEntity(id) instanceof VehicleEntity));
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof VehicleEntity vehicle) || !audible(minecraft, vehicle)) {
                continue;
            }
            layers(minecraft, vehicle);
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

    private static void layers(Minecraft minecraft, VehicleEntity vehicle) {
        String id = vehicle.vehicleDataId().getPath();
        float progress = vehicle.helicopterStartupProgress();
        Float last = LAST_STARTUP.put(vehicle.getId(), progress);
        if (last != null && last > 0.0F && progress == 0.0F && vehicle.propellerSpeed() > 0.02F) {
            SoundEvent shutdown = layerSound(id, "shutdown");
            if (shutdown != null) {
                ShutdownSound sound = new ShutdownSound(vehicle, shutdown);
                minecraft.getSoundManager().play(sound);
                lastShutdown = sound;
            }
        }
        SoundEvent slapEvent = layerSound(id, "slap");
        if (slapEvent == null) {
            return;
        }
        SlapSound slap = SLAPS.get(vehicle.getId());
        boolean aboard = isAboard(minecraft, vehicle);
        if (slap != null && !slap.isStopped() && slap.isRelative() != aboard) {
            minecraft.getSoundManager().stop(slap);
            slap = null;
        }
        if ((slap == null || slap.isStopped()) && slapTarget(vehicle) > 0.02F) {
            slap = new SlapSound(vehicle, slapEvent, aboard);
            SLAPS.put(vehicle.getId(), slap);
            minecraft.getSoundManager().play(slap);
        }
    }

    private static SoundEvent layerSound(String vehicleId, String name) {
        DeferredHolder<SoundEvent, SoundEvent> sound = ModSounds.ALL.get(Reference.id("vehicle." + vehicleId + "." + name));
        return sound == null ? null : sound.get();
    }

    /**
     * How hard the blades slap (0..1): coming down with the engines at work (more with way on), or banked hard at speed;
     * none on the ground or while the engines start or stop.
     */
    static float slapTarget(VehicleEntity vehicle) {
        if (vehicle.helicopterStartupProgress() < 1.0F || vehicle.onGround()) {
            return 0.0F;
        }
        float rpm = Mth.clamp(vehicle.propellerSpeed() / OPERATING_ROTOR_SPEED, 0.0F, 1.0F);
        double sink = vehicle.yo - vehicle.getY();
        double pace = Math.sqrt(Mth.square(vehicle.getX() - vehicle.xo) + Mth.square(vehicle.getZ() - vehicle.zo));
        float way = (float) Mth.clamp(pace / 0.25D, 0.0D, 1.0D);
        float descent = (float) Mth.clamp((sink - 0.02D) / 0.12D, 0.0D, 1.0D);
        float bank = Mth.clamp((Math.abs(vehicle.roll()) - 12.0F) / 25.0F, 0.0F, 1.0F);
        // A governed rotor pulling hard (the CH-47 climbing) beats the air harder: its blades' slap thickens with it.
        float pull = vehicle instanceof ttv.migami.jeg.vehicle.entity.ChinookEntity chinook ? Math.max(0.0F, chinook.rotorLoad()) * 0.55F : 0.0F;
        return Math.max(Math.max(descent * (0.55F + 0.45F * way), bank * way * 0.8F), pull) * rpm;
    }

    private static boolean audible(Minecraft minecraft, VehicleEntity vehicle) {
        return vehicle.isAlive() && !vehicle.isRemoved()
                && vehicle.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER
                && minecraft.player.distanceToSqr(vehicle) <= AUDIBLE_RANGE_SQR
                && (vehicle.helicopterStartupProgress() > 0.0F || vehicle.propellerSpeed() > 0.0005F);
    }

    /**
     * Seated in it, or standing in its walk-in cabin: aboard, they hear the interior mix, flat, with no Doppler (in the
     * Chinook's cabin they heard the outside mix, its pitch dropped by the flight as if it flew away from them;
     * two-player play-test, 2026-11-25).
     */
    private static boolean isAboard(Minecraft minecraft, VehicleEntity vehicle) {
        return minecraft.player != null && (minecraft.player.getRootVehicle() == vehicle
                || ttv.migami.jeg.vehicle.cabin.CabinClient.aboardVehicle() == vehicle.getId());
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
        for (SlapSound sound : SLAPS.values()) {
            minecraft.getSoundManager().stop(sound);
        }
        SLAPS.clear();
        LAST_STARTUP.clear();
    }

    /** For game tests: the slap layer playing for this helicopter, or null. */
    public static SoundInstance slap(int vehicleId) {
        SlapSound slap = SLAPS.get(vehicleId);
        return slap == null || slap.isStopped() ? null : slap;
    }

    /** For game tests: the last shutdown played, or null. */
    public static SoundInstance lastShutdown() {
        return lastShutdown;
    }

    /** For game tests: the engine sound (startup or loop) playing for this helicopter, or null. */
    public static SoundInstance engine(int vehicleId) {
        EngineSound sound = SOUNDS.get(vehicleId);
        return sound == null || sound.isStopped() ? null : sound;
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

    /**
     * The loudest a helicopter's engine is heard aboard. A heavy transport fills its cabin and cockpit: the CH-47's start
     * and flight were too quiet at the light helicopters' 0.65 (the user, 2026-11-25).
     */
    private static float aboardCap(VehicleEntity vehicle) {
        // 0.9, so a climb's extra effort still has headroom to be heard.
        return vehicle instanceof ttv.migami.jeg.vehicle.entity.ChinookEntity ? 0.9F : 0.65F;
    }

    /** The near volume of a helicopter sound at this gain: aboard flat, outside fading with distance. */
    private static float nearVolume(VehicleEntity vehicle, boolean relative, float gain) {
        float baseVolume = Math.max(0.1F, vehicle.vehicleData().defaults().engine().engineSoundVolume());
        if (relative) {
            return Math.min(aboardCap(vehicle), baseVolume * 0.45F) * gain;
        }
        return AircraftSoundRange.volume(listenerDistance(vehicle), baseVolume * gain);
    }

    private static float doppler(VehicleEntity vehicle) {
        Entity listener = Minecraft.getInstance().getCameraEntity();
        if (listener == null || listener.getRootVehicle() == vehicle || isAboard(Minecraft.getInstance(), vehicle)) {
            return 1.0F;
        }
        return VehicleDoppler.multiplier(vehicle.position(), vehicle.getDeltaMovement(), listener.position(),
                listener.getRootVehicle().getDeltaMovement());
    }

    /** The blades' slap: a loop whose gain follows {@link #slapTarget}; it stops itself once silent for a while. */
    private static final class SlapSound extends AbstractTickableSoundInstance {
        private final VehicleEntity vehicle;
        private float gain;
        private int quietTicks;

        SlapSound(VehicleEntity vehicle, SoundEvent event, boolean relative) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.vehicle = vehicle;
            this.looping = true;
            this.delay = 0;
            this.relative = relative;
            this.attenuation = Attenuation.NONE;
            this.update();
        }

        @Override
        public void tick() {
            if (this.vehicle.isRemoved()) {
                this.stop();
                return;
            }
            this.update();
            // Below 1.5 % it is inaudible under the rotor: give the channel back after a second there.
            this.quietTicks = this.gain <= 0.015F ? this.quietTicks + 1 : 0;
            if (this.quietTicks > 20 || !audible(Minecraft.getInstance(), this.vehicle)) {
                this.stop();
            }
        }

        private void update() {
            float target = slapTarget(this.vehicle);
            // Swells in over half a second and dies away over a second and a half, as the blades bite and settle.
            this.gain = Mth.lerp(target > this.gain ? 0.1F : 0.08F, this.gain, target);
            this.x = this.relative ? 0.0D : this.vehicle.getX();
            this.y = this.relative ? 0.0D : this.vehicle.getY() + 2.5D;
            this.z = this.relative ? 0.0D : this.vehicle.getZ();
            this.volume = Math.max(0.001F, nearVolume(this.vehicle, this.relative, this.gain));
            float shift = this.relative ? 1.0F : doppler(this.vehicle);
            this.pitch = Mth.clamp(VehicleSoundHelper.aircraftEnginePitch(this.vehicle) * shift, 0.5F, 2.0F);
        }
    }

    /** The turbines spooling down, once, when the engines are cut; follows the helicopter. */
    private static final class ShutdownSound extends AbstractTickableSoundInstance {
        private final VehicleEntity vehicle;

        ShutdownSound(VehicleEntity vehicle, SoundEvent event) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.vehicle = vehicle;
            this.looping = false;
            this.delay = 0;
            this.relative = isAboard(Minecraft.getInstance(), vehicle);
            this.attenuation = Attenuation.NONE;
            this.update();
        }

        @Override
        public void tick() {
            if (this.vehicle.isRemoved()) {
                this.stop();
                return;
            }
            this.update();
        }

        private void update() {
            this.x = this.relative ? 0.0D : this.vehicle.getX();
            this.y = this.relative ? 0.0D : this.vehicle.getY() + 2.0D;
            this.z = this.relative ? 0.0D : this.vehicle.getZ();
            this.volume = Math.max(0.001F, nearVolume(this.vehicle, this.relative, 1.0F));
        }
    }

    /** For game tests: the engine sound's pitch now (1 at operating rotor speed), or 0 with none playing. */
    public static float enginePitch(int vehicleId) {
        EngineSound sound = SOUNDS.get(vehicleId);
        return sound == null || sound.isStopped() ? 0.0F : sound.rawPitch();
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

        float rawPitch() {
            return this.pitch;
        }

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
            if (!this.startup && listener != null && listener.getRootVehicle() != this.vehicle && !isAboard(minecraft, this.vehicle)) {
                doppler = VehicleDoppler.multiplier(this.vehicle.position(), this.vehicle.getDeltaMovement(),
                        listener.position(), listener.getRootVehicle().getDeltaMovement());
            }
            float rpm = Mth.clamp(this.vehicle.propellerSpeed() / OPERATING_ROTOR_SPEED, 0.0F, 1.25F);
            float basePitch = this.startup ? 1.0F : Mth.clamp(0.35F + rpm * 0.65F, 0.35F, 1.12F);
            float targetGain = this.startup ? 1.0F : Math.min(rpm, 1.0F);
            if (!this.startup && this.vehicle instanceof ttv.migami.jeg.vehicle.entity.ChinookEntity chinook
                    && chinook.helicopterStartupProgress() >= 1.0F) {
                // Governed rotors: the note holds, leaning a little into a climb as the turbines take the load, and the
                // whole aircraft grows louder with the collective (2026-11-25).
                float load = chinook.rotorLoad();
                basePitch = 1.0F + 0.035F * load;
                targetGain = 1.0F + 0.12F * Math.max(0.0F, load);
            }
            this.pitch = Mth.lerp(0.25F, this.pitch, basePitch * doppler);
            this.rpmGain = this.rpmGain < 0.0F ? targetGain : Mth.lerp(0.12F, this.rpmGain, targetGain);
            float baseVolume = Math.max(0.1F, this.vehicle.vehicleData().defaults().engine().engineSoundVolume());
            if (this.relative) {
                this.volume = Math.min(aboardCap(this.vehicle), baseVolume * 0.45F) * this.rpmGain * this.mixGain;
            } else {
                double distance = listenerDistance(this.vehicle);
                this.volume = Math.max(0.001F, AircraftSoundRange.volume(distance, baseVolume * this.rpmGain)
                        * AircraftSoundRange.nearShare(distance) * this.mixGain);
            }
        }
    }
}
