package ttv.migami.jeg.mixin.cabin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ttv.migami.jeg.vehicle.cabin.CabinClient;

/** Right and left clicks aboard a cabin go to its blocks first (vehicle/cabin/CabinClient). */
@Mixin(Minecraft.class)
public abstract class MinecraftCabinUseMixin {
    @org.spongepowered.asm.mixin.Shadow
    private int rightClickDelay;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void jeg$cabinUse(CallbackInfo ci) {
        if (CabinClient.handleUse()) {
            // Vanilla sets this first thing in startUseItem. Without it the held-key repeat calls startUseItem again in
            // the same tick, and every tick after: one click on the ramp panel cycled the ramp twice (play-test 2026-11-20).
            this.rightClickDelay = 4;
            ci.cancel();
        }
    }

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void jeg$cabinAttack(CallbackInfoReturnable<Boolean> cir) {
        if (CabinClient.handleAttack()) {
            cir.setReturnValue(true);
        }
    }
}
