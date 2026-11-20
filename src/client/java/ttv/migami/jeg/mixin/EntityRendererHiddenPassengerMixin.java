package ttv.migami.jeg.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Whoever sits in a vehicle seat marked {@code hide_passenger} (inside an armoured hull) is not drawn, for everyone:
 * other players and mobs carried as passengers (yftm.22) were drawn poking through the BMP-2's roof. The local player's
 * own case is handled the same way (and by {@link LivingEntityRendererVehicleMixin} for its zoom rules).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererHiddenPassengerMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void jeg$hideSeatedPassenger(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                         CallbackInfoReturnable<Boolean> cir) {
        if (entity.getVehicle() instanceof VehicleEntity vehicle && vehicle.shouldHidePassenger(entity)) {
            cir.setReturnValue(false);
        }
    }
}
