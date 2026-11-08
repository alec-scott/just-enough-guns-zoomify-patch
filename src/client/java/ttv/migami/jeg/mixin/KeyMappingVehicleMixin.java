package ttv.migami.jeg.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.client.overlay.VehicleHudOverlay;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

@Mixin(KeyMapping.class)
public class KeyMappingVehicleMixin {
    @Shadow
    private int clickCount;

    // Zoomify binds zoom to C by default, the same key as vehicle free look, and its scroll zoom fights the vehicle
    // camera, so its zoom keys are ignored for whoever is at the vehicle's controls (pilot, gunner, weapon seat).
    // Passengers who keep their own hands and gun zoom as they do on foot.

    @Inject(method = "isDown()Z", at = @At("HEAD"), cancellable = true)
    private void jeg$suppressZoomifyInVehicle(CallbackInfoReturnable<Boolean> callback) {
        if (this.jeg$isZoomifyKeyInVehicle()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "consumeClick()Z", at = @At("HEAD"), cancellable = true)
    private void jeg$consumeVehicleHotbarClick(CallbackInfoReturnable<Boolean> callback) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !(player.getVehicle() instanceof VehicleEntity vehicle)) {
            return;
        }
        if (this.jeg$isZoomifyKeyInVehicle()) {
            this.clickCount = 0;
            callback.setReturnValue(false);
            return;
        }
        for (KeyMapping hotbarKey : Minecraft.getInstance().options.keyHotbarSlots) {
            if (hotbarKey == (Object) this && vehicle.shouldBanPassengerHand(player)) {
                if (this.clickCount > 0) {
                    this.clickCount--;
                }
                callback.setReturnValue(false);
                return;
            }
        }
    }

    private boolean jeg$isZoomifyKeyInVehicle() {
        return ((KeyMapping) (Object) this).getName().startsWith(KeyBindings.ZOOMIFY_ZOOM_KEY_PREFIX)
                && VehicleHudOverlay.shouldReplaceHotbar();
    }
}
