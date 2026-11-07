package ttv.migami.jeg.vehicle.client.audio;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModSounds;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/** Mechanical spin audio; the firing loop starts only when the server says the gun is ready. */
public final class VehicleRotaryGunAudio {
    private static final Map<Integer, SpinSound> SOUNDS = new HashMap<>();

    private VehicleRotaryGunAudio() {}

    public static void clear(Minecraft minecraft) {
        for (SpinSound sound : SOUNDS.values()) {
            minecraft.getSoundManager().stop(sound);
        }
        SOUNDS.clear();
    }

    public static void tick(Minecraft minecraft) {
        SOUNDS.entrySet().removeIf(entry -> {
            SpinSound sound = entry.getValue();
            if (minecraft.level != sound.vehicle.level() || sound.vehicle.isRemoved()
                    || !sound.vehicle.isAlive() || sound.vehicle.isWeaponFiring() || sound.vehicle.rotaryGunSpin() <= 0.0F
                    || minecraft.player == null || sound.vehicle.distanceToSqr(minecraft.player) > 16384.0D
                    || sound.coasting == sound.vehicle.rotaryGunRequested()
                    || sound.isRelative() != aboard(sound.vehicle)) {
                minecraft.getSoundManager().stop(sound);
                return true;
            }
            return false;
        });
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof VehicleEntity vehicle && vehicle.rotaryGunSpin() > 0.0F
                    && !vehicle.isWeaponFiring() && !SOUNDS.containsKey(vehicle.getId())
                    && vehicle.distanceToSqr(minecraft.player) <= 16384.0D) {
                SpinSound sound = new SpinSound(vehicle, !vehicle.rotaryGunRequested(), false);
                SOUNDS.put(vehicle.getId(), sound);
                minecraft.getSoundManager().play(sound);
            }
        }
    }

    public static void playStop(Minecraft minecraft, VehicleEntity vehicle) {
        minecraft.getSoundManager().play(new SpinSound(vehicle, true, true));
    }

    private static boolean aboard(VehicleEntity vehicle) {
        var player = Minecraft.getInstance().player;
        return player != null && player.getRootVehicle() == vehicle;
    }

    private static final class SpinSound extends AbstractTickableSoundInstance {
        private final VehicleEntity vehicle;
        private final boolean coasting;
        private final boolean tail;

        SpinSound(VehicleEntity vehicle, boolean coasting, boolean tail) {
            super(ModSounds.ALL.get(Reference.id(tail ? "item.minigun.fire_stop" : "item.minigun.spin_loop")).get(),
                    SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.vehicle = vehicle;
            this.coasting = coasting;
            this.tail = tail;
            this.looping = !tail;
            this.relative = aboard(vehicle);
            this.attenuation = this.relative ? Attenuation.NONE : Attenuation.LINEAR;
            this.update();
        }

        @Override
        public void tick() {
            if (this.vehicle.isRemoved() || !this.vehicle.isAlive()) {
                this.stop();
            } else {
                this.update();
            }
        }

        private void update() {
            this.x = this.relative ? 0.0D : this.vehicle.getX();
            this.y = this.relative ? 0.0D : this.vehicle.getY() + 1.0D;
            this.z = this.relative ? 0.0D : this.vehicle.getZ();
            float spin = this.vehicle.rotaryGunSpin();
            this.pitch = this.tail ? 1.0F : Mth.lerp(spin * spin, 0.5F, 1.0F);
            this.volume = this.tail ? 0.55F : 0.3F * Mth.sqrt(spin);
        }
    }
}
