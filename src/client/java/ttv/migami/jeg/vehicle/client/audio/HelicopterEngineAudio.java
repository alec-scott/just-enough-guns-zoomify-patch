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

/** One startup recording followed by one continuous positional engine loop, for every helicopter in earshot. */
public final class HelicopterEngineAudio {
    private static final double AUDIBLE_RANGE_SQR = 384.0D * 384.0D;
    private static final float OPERATING_ROTOR_SPEED = 0.085F;
    private static final Map<Integer, EngineSound> SOUNDS = new HashMap<>();

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
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof VehicleEntity vehicle) || !audible(minecraft, vehicle)) {
                continue;
            }
            EngineSound current = SOUNDS.get(vehicle.getId());
            boolean starting = vehicle.helicopterStartupProgress() > 0.0F && vehicle.helicopterStartupProgress() < 1.0F;
            if (current != null && (current.startup == starting || !current.startup)) {
                continue;
            }
            if (current != null) {
                minecraft.getSoundManager().stop(current);
                current.halt();
            }
            // A listener arriving halfway through startup hears the rotor at its current RPM, rather than replaying ignition.
            boolean playIgnition = starting && vehicle.helicopterStartupProgress() <= 0.05F;
            SoundEvent event = playIgnition ? VehicleSoundHelper.engineStartSound(vehicle) : VehicleSoundHelper.engineSound(vehicle);
            if (event != null) {
                EngineSound sound = new EngineSound(vehicle, event, playIgnition);
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

    public static void clear(Minecraft minecraft) {
        for (EngineSound sound : SOUNDS.values()) {
            minecraft.getSoundManager().stop(sound);
            sound.halt();
        }
        SOUNDS.clear();
    }

    public static String phase(int vehicleId) {
        EngineSound sound = SOUNDS.get(vehicleId);
        return sound == null ? "SILENT" : sound.startup ? "STARTUP" : "FLIGHT";
    }

    private static final class EngineSound extends AbstractTickableSoundInstance {
        private final VehicleEntity vehicle;
        private final boolean startup;

        EngineSound(VehicleEntity vehicle, SoundEvent event, boolean startup) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.vehicle = vehicle;
            this.startup = startup;
            this.looping = !startup;
            this.delay = 0;
            this.pitch = 1.0F;
            this.attenuation = Attenuation.LINEAR;
            this.update();
        }

        void halt() {
            this.stop();
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
            this.x = this.vehicle.getX();
            this.y = this.vehicle.getY() + 1.5D;
            this.z = this.vehicle.getZ();
            Minecraft minecraft = Minecraft.getInstance();
            Entity listener = minecraft.getCameraEntity();
            float doppler = 1.0F;
            if (listener != null && listener.getRootVehicle() != this.vehicle) {
                doppler = VehicleDoppler.multiplier(this.vehicle.position(), this.vehicle.getDeltaMovement(),
                        listener.position(), listener.getRootVehicle().getDeltaMovement());
            }
            float rpm = Mth.clamp(this.vehicle.propellerSpeed() / OPERATING_ROTOR_SPEED, 0.0F, 1.25F);
            float basePitch = this.startup ? 1.0F : Mth.clamp(0.55F + rpm * 0.45F, 0.55F, 1.12F);
            this.pitch = Mth.lerp(0.25F, this.pitch, basePitch * doppler);
            this.volume = Math.max(0.1F, this.vehicle.vehicleData().defaults().engine().engineSoundVolume())
                    * (this.startup ? 1.0F : Mth.sqrt(Math.min(rpm, 1.0F)));
        }
    }
}
