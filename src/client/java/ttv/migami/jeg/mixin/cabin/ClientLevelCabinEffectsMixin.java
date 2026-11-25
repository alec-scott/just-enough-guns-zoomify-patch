package ttv.migami.jeg.mixin.cabin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ttv.migami.jeg.vehicle.cabin.CabinBlockEffects;

/**
 * While a block placed in a cabin runs its animateTick, the particles and sounds it adds at its spot are put at the block
 * drawn in the cabin instead (CabinBlockEffects): a torch's flame, a furnace's crackle.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelCabinEffectsMixin {
    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void jeg$cabinParticle(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz, CallbackInfo ci) {
        if (CabinBlockEffects.mapping()) {
            ci.cancel();
            CabinBlockEffects.particle((ClientLevel) (Object) this, particle, false, false, false, x, y, z, vx, vy, vz);
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZZDDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void jeg$cabinParticle(ParticleOptions particle, boolean force, boolean decreased, double x, double y, double z, double vx, double vy,
                                   double vz, CallbackInfo ci) {
        if (CabinBlockEffects.mapping()) {
            ci.cancel();
            CabinBlockEffects.particle((ClientLevel) (Object) this, particle, force, decreased, false, x, y, z, vx, vy, vz);
        }
    }

    @Inject(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void jeg$cabinAlwaysVisible(ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz, CallbackInfo ci) {
        if (CabinBlockEffects.mapping()) {
            ci.cancel();
            CabinBlockEffects.particle((ClientLevel) (Object) this, particle, false, false, true, x, y, z, vx, vy, vz);
        }
    }

    @Inject(method = "addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V", at = @At("HEAD"), cancellable = true)
    private void jeg$cabinAlwaysVisible(ParticleOptions particle, boolean force, double x, double y, double z, double vx, double vy, double vz,
                                        CallbackInfo ci) {
        if (CabinBlockEffects.mapping()) {
            ci.cancel();
            CabinBlockEffects.particle((ClientLevel) (Object) this, particle, force, false, true, x, y, z, vx, vy, vz);
        }
    }

    @Inject(method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", at = @At("HEAD"), cancellable = true)
    private void jeg$cabinSound(double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch, boolean distanceDelay,
                                CallbackInfo ci) {
        if (CabinBlockEffects.mapping()) {
            ci.cancel();
            CabinBlockEffects.sound((ClientLevel) (Object) this, x, y, z, sound, source, volume, pitch, distanceDelay);
        }
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"), cancellable = true)
    private void jeg$cabinSeededSound(Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source, float volume,
                                      float pitch, long seed, CallbackInfo ci) {
        if (CabinBlockEffects.mapping()) {
            ci.cancel();
            CabinBlockEffects.seededSound((ClientLevel) (Object) this, except, x, y, z, sound, source, volume, pitch, seed);
        }
    }
}
