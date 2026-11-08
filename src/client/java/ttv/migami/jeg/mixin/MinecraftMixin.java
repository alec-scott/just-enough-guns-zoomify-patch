package ttv.migami.jeg.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ttv.migami.jeg.item.GunItem;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

@Mixin(Minecraft.class)
public final class MinecraftMixin {
    @Shadow
    public LocalPlayer player;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void jeg$cancelVanillaGunAttack(CallbackInfoReturnable<Boolean> cir) {
        if (this.player == null) {
            return;
        }

        ItemStack mainHand = this.player.getMainHandItem();
        if (mainHand.getItem() instanceof GunItem || this.jeg$handsOnVehicleControls()) {
            cir.setReturnValue(false);
        }
    }

    /** Holding fire as a pilot or gunner must not mine the block or keep swinging whatever is in hand. */
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void jeg$cancelVehicleContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (this.jeg$handsOnVehicleControls()) {
            ci.cancel();
        }
    }

    /** Right-click aims the vehicle's weapon for pilots and gunners; it must not also eat, drink, block or place. */
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void jeg$cancelVehicleUseItem(CallbackInfo ci) {
        if (this.jeg$handsOnVehicleControls()) {
            ci.cancel();
        }
    }

    /** Drivers and gunners have their hands on the vehicle's controls; other passengers keep their own hands. */
    @Unique
    private boolean jeg$handsOnVehicleControls() {
        return this.player != null
                && this.player.getVehicle() instanceof VehicleEntity vehicle
                && vehicle.shouldBanPassengerHand(this.player);
    }
}
