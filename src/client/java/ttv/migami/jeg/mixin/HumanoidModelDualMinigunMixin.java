package ttv.migami.jeg.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ttv.migami.jeg.client.render.DualMinigunPose;
import ttv.migami.jeg.gun.MinigunSpin;

/**
 * A minigun in each hand: each arm holds its own gun (see DualMinigunPose). Applied right after vanilla poses the arms and
 * before its idle bob and attack swing, so the arms end up exactly as a vanilla crossbow hold on each side would. With Not
 * Enough Animations installed, which re-poses the arms afterwards, the same pose is registered with it too.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelDualMinigunMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/HumanoidModel;setupAttackAnimation(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V"))
    private void jeg$dualMinigunPose(HumanoidRenderState state, CallbackInfo ci) {
        if (state.rightHandItemStack == null || state.leftHandItemStack == null
                || !MinigunSpin.isMinigun(state.rightHandItemStack) || !MinigunSpin.isMinigun(state.leftHandItemStack)) {
            return;
        }
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        DualMinigunPose.poseArm(model.head, model.rightArm, false);
        DualMinigunPose.poseArm(model.head, model.leftArm, true);
    }
}
