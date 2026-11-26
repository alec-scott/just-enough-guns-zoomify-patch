package ttv.migami.jeg.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ttv.migami.jeg.vehicle.client.VehiclePassengerRenderHandler;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererVehicleMixin {
    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void jeg$hideVehiclePassenger(LivingEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (renderState instanceof AvatarRenderState avatarRenderState
                && VehiclePassengerRenderHandler.shouldHideLocalPlayerPassenger(avatarRenderState.id)) {
            ci.cancel();
            return;
        }
        VehiclePassengerRenderHandler.standAtStation(renderState);
        this.jeg$tilted = VehiclePassengerRenderHandler.tiltWithAircraft(renderState, poseStack);
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void jeg$markCabSeat(net.minecraft.world.entity.LivingEntity entity, LivingEntityRenderState renderState, float partialTick, CallbackInfo ci) {
        ((ttv.migami.jeg.vehicle.client.CabSeatedState) renderState).jeg$setSeatedInCab(
                entity.getVehicle() instanceof ttv.migami.jeg.vehicle.entity.base.VehicleEntity vehicle && vehicle.seatsMobInCab(entity));
    }

    /** Whether this submit pushed an aircraft's bank and pitch onto the pose, to pop at its end. */
    private boolean jeg$tilted;

    @Inject(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN")
    )
    private void jeg$untilt(LivingEntityRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (this.jeg$tilted) {
            this.jeg$tilted = false;
            poseStack.popPose();
        }
    }
}
