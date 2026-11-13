package ttv.migami.jeg.vehicle.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.geckolib.util.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import ttv.migami.jeg.vehicle.client.resource.DefaultVehicleResource;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Motion blur for a jet's fans at speed. Past about 25 degrees a tick the blades start to strobe (they look alike every
 * 45 degrees, and are only drawn once a frame), so faint copies of them trail behind, spread over the sweep of a frame:
 * the spinning fan reads as a blurred disc, like a real one at full power, rather than a wheel turning backwards.
 */
final class VehicleFanBlurLayer<T extends VehicleEntity> extends GeoRenderLayer<T, Void, VehicleRenderState> {
    /** The A-10's spinning parts, with how far each one's pivot sits from its engine's axis (see AbstractVehicleGeoRenderer). */
    private static final String[] FAN_BONES = {"qianwaiyuan", "qianwaiyuan2", "houwaiyuan", "houneiyuan", "houwaiyuan2", "houneiyuan2"};
    private static final float[] FAN_AXIS_OFFSET = {0.338F, -0.338F, 0.0F, 0.0F, 0.0F, 0.0F};
    private static final float BLUR_FROM = 25.0F;
    private static final int GHOSTS = 3;

    VehicleFanBlurLayer(GeoRenderer<T, Void, VehicleRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void submitRenderTask(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        VehicleEntity jet = passInfo.renderState().vehicle;
        if (jet == null || !jet.isJet() || jet.isWrecked()) {
            return;
        }
        float rate = jet.jetFanRate();
        if (rate <= BLUR_FROM) {
            return;
        }
        float blur = Mth.clamp((rate - BLUR_FROM) / 40.0F, 0.0F, 1.0F);
        // A frame at 60 fps sweeps a third of a tick's turn; the ghosts spread back over that.
        float sweep = rate / 3.0F;
        int light = passInfo.packedLight();
        PoseStack modelPose = new PoseStack();
        modelPose.last().set(passInfo.getModelRenderMatrixPose());
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.entityTranslucent(DefaultVehicleResource.texture(jet)), (pose, buffer) -> {
            for (int b = 0; b < FAN_BONES.length; b++) {
                GeoBone fan = passInfo.model().getBone(FAN_BONES[b]).orElse(null);
                if (fan == null) {
                    continue;
                }
                float offset = FAN_AXIS_OFFSET[b];
                for (int ghost = 1; ghost <= GHOSTS; ghost++) {
                    float back = -sweep * ghost / (GHOSTS + 1);
                    int alpha = Math.round(255.0F * blur * (0.42F - 0.11F * ghost));
                    if (alpha <= 4) {
                        continue;
                    }
                    int colour = (alpha << 24) | 0xFFFFFF;
                    PoseStack ghostPose = new PoseStack();
                    ghostPose.last().set(pose);
                    passInfo.renderPosed(() -> {
                        Deque<GeoBone> chain = new ArrayDeque<>();
                        for (GeoBone bone = fan.parent(); bone != null; bone = bone.parent()) {
                            chain.push(bone);
                        }
                        for (GeoBone bone : chain) {
                            RenderUtil.prepMatrixForBone(ghostPose, bone);
                        }
                        // Turned back about the engine's axis, in the engine's own frame.
                        float cx = fan.pivotX() / 16.0F + offset;
                        float cy = fan.pivotY() / 16.0F;
                        ghostPose.translate(cx, cy, 0.0F);
                        ghostPose.mulPose(Axis.ZP.rotationDegrees(back));
                        ghostPose.translate(-cx, -cy, 0.0F);
                        RenderUtil.prepMatrixForBone(ghostPose, fan);
                        renderTree(passInfo, fan, ghostPose, buffer, light, colour);
                    });
                }
            }
        });
    }

    private static void renderTree(RenderPassInfo<VehicleRenderState> passInfo, GeoBone bone, PoseStack pose, VertexConsumer buffer, int light, int colour) {
        bone.render(passInfo, pose, buffer, light, OverlayTexture.NO_OVERLAY, colour);
        for (GeoBone child : bone.children()) {
            pose.pushPose();
            RenderUtil.prepMatrixForBone(pose, child);
            renderTree(passInfo, child, pose, buffer, light, colour);
            pose.popPose();
        }
    }
}
