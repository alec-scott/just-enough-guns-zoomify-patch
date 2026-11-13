package ttv.migami.jeg.vehicle.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

public final class VehicleFireSoundInstance extends AbstractTickableSoundInstance {
    private static final float MIN_VOLUME = 0.65F;
    private static final float MAX_VOLUME = 0.85F;
    private final VehicleEntity vehicle;
    private final SoundEvent sound;
    private final boolean helicopter;
    private final boolean jet;

    public VehicleFireSoundInstance(VehicleEntity vehicle, SoundEvent sound) {
        super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.vehicle = vehicle;
        this.sound = sound;
        // Jets' guns are heard like a helicopter's: from inside the cockpit when aboard, positionally from outside.
        this.helicopter = vehicle.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER || vehicle.isJet();
        this.jet = vehicle.isJet();
        this.looping = true;
        this.delay = 0;
        // SoundEngine discards zero-volume sounds before the first tick unless canStartSilent is enabled.
        this.volume = this.helicopter ? MIN_VOLUME : 3.5F;
        this.pitch = 1.0F;
        this.relative = this.listensFromInside();
        this.attenuation = this.relative ? Attenuation.NONE : Attenuation.LINEAR;
        this.updatePosition();
    }

    public boolean matches(SoundEvent sound) {
        return this.sound == sound;
    }

    public boolean listenerModeMatches() {
        return this.relative == this.listensFromInside();
    }

    /** A helicopter's gun is heard from inside by anyone aboard; a jet's only from its cockpit view (JetAudio). */
    private boolean listensFromInside() {
        return this.helicopter && (this.jet ? JetAudio.inCockpit(this.vehicle) : isAboard(this.vehicle));
    }

    private static boolean isAboard(VehicleEntity vehicle) {
        var player = Minecraft.getInstance().player;
        return player != null && player.getRootVehicle() == vehicle;
    }

    @Override
    public void tick() {
        if (this.vehicle.isRemoved() || !this.vehicle.isAlive() || !this.vehicle.isWeaponFiring()) {
            this.stop();
            return;
        }
        this.updatePosition();
        double speed = this.vehicle.getDeltaMovement().length();
        if (this.jet) {
            // The GAU-8 is heard a long way off: the volume sets its range (16 blocks per unit) outside the cockpit.
            this.volume = this.relative ? 1.0F : 12.0F;
            this.pitch = 1.0F;
            return;
        }
        this.volume = this.helicopter
                ? Mth.clamp(MIN_VOLUME + (float) speed * 0.1F, MIN_VOLUME, MAX_VOLUME)
                : Mth.clamp(3.5F + (float) speed * 4.0F, 3.5F, 6.5F);
        this.pitch = Mth.clamp(0.96F + (float) speed * 0.08F, 0.96F, 1.08F);
    }

    private void updatePosition() {
        this.x = this.relative ? 0.0D : this.vehicle.getX();
        this.y = this.relative ? 0.0D : this.vehicle.getY();
        this.z = this.relative ? 0.0D : this.vehicle.getZ();
    }
}
