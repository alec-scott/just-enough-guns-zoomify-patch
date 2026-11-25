package ttv.migami.jeg.vehicle.client.audio;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.cabin.CabinMath;
import ttv.migami.jeg.vehicle.entity.ChinookEntity;

/**
 * The CH-47's ramp hydraulics, heard while the ramp (and its upper door) move: a loop at the hinge that swells in as
 * the pump takes the load and dies away when the ramp stops (the lock's clunk is the server's, ChinookEntity). Each
 * client follows the ramp's synced angle, so everyone near hears it. With it, the ramp warning beeps in time with the red
 * jump light's flashes (ChinookEntity#rampBeepTick). And whoever stands in the cabin, or mans a door gun, hears the
 * wind: the rotors' wash, and the slipstream roaring in at the open ramp and crew door as it flies, loudest near them
 * ({@link WindSound}; the two-player play-test, 2026-11-25, heard nothing at the open ramp at 23 m/s).
 */
public final class Ch47CabinAudio {
    private static final double RANGE = 48.0D;
    private static final Map<Integer, Float> LAST_ANGLE = new HashMap<>();
    private static final Map<Integer, RampSound> PLAYING = new HashMap<>();
    /** Ramp warning beeps played, by Chinook (for game tests). */
    private static final Map<Integer, Integer> BEEPS = new HashMap<>();

    private Ch47CabinAudio() {}

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            clear(minecraft);
            return;
        }
        ChinookEntity riding = windChinook(minecraft);
        if (riding != null && (wind == null || wind.isStopped() || wind.chinook != riding)) {
            wind = new WindSound(riding);
            minecraft.getSoundManager().play(wind);
        }
        PLAYING.values().removeIf(sound -> sound.isStopped());
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof ChinookEntity chinook) || chinook.distanceToSqr(minecraft.player) > RANGE * RANGE) {
                continue;
            }
            if (chinook.rampBeepTick()) {
                // The ramp warning: a beep with each flash of the red jump light while the ramp travels, from the lights.
                Vec3 at = CabinMath.toWorld(chinook.localToWorld(1.0F), new Vec3(0.0D, 3.85D, ChinookEntity.HINGE_Z + 0.1D));
                minecraft.level.playLocalSound(at.x, at.y, at.z, ChinookEntity.cabinSound("ramp_beep"), SoundSource.NEUTRAL,
                        0.9F, 1.0F, false);
                BEEPS.merge(chinook.getId(), 1, Integer::sum);
            }
            float angle = chinook.rampAngle(1.0F);
            Float last = LAST_ANGLE.put(chinook.getId(), angle);
            boolean moving = last != null && Math.abs(angle - last) > 1.0E-3F;
            RampSound sound = PLAYING.get(chinook.getId());
            if (moving) {
                if (sound == null || sound.isStopped()) {
                    sound = new RampSound(chinook);
                    PLAYING.put(chinook.getId(), sound);
                    minecraft.getSoundManager().play(sound);
                }
                sound.keepAlive();
            }
        }
        LAST_ANGLE.keySet().removeIf(id -> !(minecraft.level.getEntity(id) instanceof ChinookEntity));
    }

    public static void clear(Minecraft minecraft) {
        for (RampSound sound : PLAYING.values()) {
            minecraft.getSoundManager().stop(sound);
        }
        if (wind != null) {
            minecraft.getSoundManager().stop(wind);
            wind = null;
        }
        PLAYING.clear();
        LAST_ANGLE.clear();
        BEEPS.clear();
    }

    /** For game tests: how many ramp warning beeps this Chinook has sounded. */
    public static int rampBeeps(int vehicleId) {
        return BEEPS.getOrDefault(vehicleId, 0);
    }

    /** For game tests: whether this Chinook's ramp hydraulics are heard now. */
    public static boolean hydraulicsPlaying(int vehicleId) {
        RampSound sound = PLAYING.get(vehicleId);
        return sound != null && !sound.isStopped();
    }

    private static WindSound wind;

    /** The Chinook whose wind the local player hears: standing in its cabin, or at one of its door guns. */
    private static ChinookEntity windChinook(Minecraft minecraft) {
        int aboard = ttv.migami.jeg.vehicle.cabin.CabinClient.aboardVehicle();
        if (aboard >= 0 && minecraft.level.getEntity(aboard) instanceof ChinookEntity chinook) {
            return chinook;
        }
        if (minecraft.player.getVehicle() instanceof ChinookEntity chinook) {
            int seat = chinook.seatOf(minecraft.player);
            if (seat == ChinookEntity.LEFT_GUNNER_SEAT || seat == ChinookEntity.RIGHT_GUNNER_SEAT) {
                return chinook;
            }
        }
        return null;
    }

    /** The wind in the cabin, heard flat (it is all round the listener): the A-10's canopy roar, deeper. */
    private static final class WindSound extends AbstractTickableSoundInstance {
        private final ChinookEntity chinook;
        private float gain;

        WindSound(ChinookEntity chinook) {
            super(ttv.migami.jeg.init.ModSounds.ALL.get(ttv.migami.jeg.Reference.id("vehicle.a10.cockpit_flight")).get(),
                    SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.chinook = chinook;
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.001F;
            this.pitch = 0.7F;
        }

        @Override
        public void tick() {
            Minecraft minecraft = Minecraft.getInstance();
            boolean here = !this.chinook.isRemoved() && minecraft.player != null && windChinook(minecraft) == this.chinook;
            float target = here ? this.target(minecraft) : 0.0F;
            this.gain = Mth.lerp(0.08F, this.gain, target);
            this.volume = Math.max(0.001F, this.gain);
            float airspeed = (float) Mth.clamp(this.airspeed() / 30.0D, 0.0D, 1.0D);
            this.pitch = 0.7F + 0.25F * airspeed;
            if (!here && this.gain < 0.005F) {
                this.stop();
            }
        }

        /** Metres a second over the ground. */
        private double airspeed() {
            return Math.hypot(this.chinook.getX() - this.chinook.xo, this.chinook.getZ() - this.chinook.zo) * 20.0D;
        }

        private float target(Minecraft minecraft) {
            Vec3 at = CabinMath.toLocal(this.chinook.localToWorld(1.0F), minecraft.player.position());
            float ramp = Mth.clamp((ChinookEntity.RAMP_CLOSED - this.chinook.rampAngle(1.0F)) / ChinookEntity.RAMP_CLOSED, 0.0F, 1.0F);
            // Loudest on the ramp, a quarter of it at the cockpit bulkhead.
            float nearRamp = (float) Mth.clamp(1.0D - (at.z - ChinookEntity.HINGE_Z) / 9.0D, 0.25D, 1.0D);
            float nearDoor = (float) Mth.clamp(1.0D - Math.hypot(at.x + ChinookEntity.HALF_IN, at.z - (ChinookEntity.DOOR_Z0 + ChinookEntity.DOOR_Z1) / 2.0D) / 5.0D, 0.0D, 1.0D);
            float open = Math.max(ramp * nearRamp, this.chinook.doorOpen(1.0F) * nearDoor * 0.7F);
            if (this.chinook.seatOf(minecraft.player) == ChinookEntity.LEFT_GUNNER_SEAT) {
                open = Math.max(open, this.chinook.gunWindow(1.0F) * 0.45F);
            }
            float rotor = Mth.clamp(this.chinook.propellerSpeed() / 0.085F, 0.0F, 1.0F);
            float speed = (float) Mth.clamp(this.airspeed() / 30.0D, 0.0D, 1.0D);
            float wash = 0.16F * rotor * rotor;
            return (wash + 0.75F * speed * speed) * (0.1F + 0.9F * open);
        }
    }

    private static final class RampSound extends AbstractTickableSoundInstance {
        private final ChinookEntity chinook;
        private int idleTicks;
        private float gain;

        RampSound(ChinookEntity chinook) {
            super(ChinookEntity.cabinSound("ramp_hydraulic"), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.chinook = chinook;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.update();
        }

        void keepAlive() {
            this.idleTicks = 0;
        }

        @Override
        public void tick() {
            if (this.chinook.isRemoved()) {
                this.stop();
                return;
            }
            this.idleTicks++;
            this.update();
            if (this.idleTicks > 2 && this.gain < 0.02F) {
                this.stop();
            }
        }

        private void update() {
            // In over a quarter second as the pump loads, out over half a second as it unloads.
            float target = this.idleTicks <= 1 ? 1.0F : 0.0F;
            this.gain = Mth.lerp(target > this.gain ? 0.25F : 0.15F, this.gain, target);
            Vec3 at = CabinMath.toWorld(this.chinook.localToWorld(1.0F), new Vec3(0.0D, 1.6D, ChinookEntity.HINGE_Z));
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.volume = Math.max(0.01F, this.gain);
            this.pitch = 1.0F;
        }
    }
}
