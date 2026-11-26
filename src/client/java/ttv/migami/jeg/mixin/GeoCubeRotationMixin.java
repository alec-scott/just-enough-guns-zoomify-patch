package ttv.migami.jeg.mixin;

import com.geckolib.cache.model.cuboid.GeoCube;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ttv.migami.jeg.vehicle.client.render.VehicleDrawCulling;

/**
 * GeckoLib 5.5 turns every cube about Z, then Y, then X on every draw, each a 4x4 and a 3x3 matrix multiply, even when
 * that angle is zero: 69% of the vehicle models' per-cube turns are (96% of the FSV-6's, 98% of the bulldozer's), and
 * the multiplies were 18% of the render thread with the ArmourTest fleet in view (2026-11-26). A zero-angle turn is the
 * identity, so it is left out and the others are done as GeckoLib does them, in its order: the matrices come out the
 * same. Every GeckoLib model gains, not only vehicles. Fails soft (require 0) if GeckoLib changes the method.
 */
@Mixin(value = GeoCube.class, remap = false)
abstract class GeoCubeRotationMixin {
    @Shadow
    public abstract Vec3 rotation();

    @Inject(method = "rotate", at = @At("HEAD"), cancellable = true, require = 0)
    private void jeg$skipZeroTurns(PoseStack poseStack, CallbackInfo ci) {
        if (!VehicleDrawCulling.cubeTurns) {
            return;
        }
        Vec3 rotation = this.rotation();
        float x = (float) rotation.x();
        float y = (float) rotation.y();
        float z = (float) rotation.z();
        if (z != 0.0F) {
            poseStack.mulPose(new Quaternionf().rotationXYZ(0.0F, 0.0F, z));
        }
        if (y != 0.0F) {
            poseStack.mulPose(new Quaternionf().rotationXYZ(0.0F, y, 0.0F));
        }
        if (x != 0.0F) {
            poseStack.mulPose(new Quaternionf().rotationXYZ(x, 0.0F, 0.0F));
        }
        ci.cancel();
    }
}
