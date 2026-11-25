package ttv.migami.jeg.mixin.cabin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ttv.migami.jeg.vehicle.cabin.CabinClient;

/**
 * Someone else standing in a cabin swings their limbs to their walk in the cabin, not to their world movement, which is
 * the vehicle's flight: standing still on a Chinook's ramp at 23 m/s, their legs ran flat out (two-player play-test,
 * 2026-11-25). The local player's own world movement aboard is already their walk (CabinClient#move).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCabinWalkMixin {
    @Shadow
    protected abstract void updateWalkAnimation(float distance);

    @Inject(method = "calculateEntityAnimation", at = @At("HEAD"), cancellable = true)
    private void jeg$cabinWalk(boolean includeHeight, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.level().isClientSide()) {
            return;
        }
        float walked = CabinClient.remoteWalked(self);
        if (Float.isNaN(walked)) {
            return;
        }
        if (self.isAlive()) {
            this.updateWalkAnimation(walked);
        } else {
            self.walkAnimation.stop();
        }
        ci.cancel();
    }
}
