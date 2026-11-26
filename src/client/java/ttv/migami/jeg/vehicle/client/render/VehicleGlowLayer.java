package ttv.migami.jeg.vehicle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Brightness;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import ttv.migami.jeg.vehicle.client.resource.DefaultVehicleResource;
import ttv.migami.jeg.vehicle.client.resource.TextureCoverage;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

final class VehicleGlowLayer<T extends VehicleEntity> extends GeoRenderLayer<T, Void, VehicleRenderState> {
    VehicleGlowLayer(GeoRenderer<T, Void, VehicleRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void preRender(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        VehicleEntity animatable = passInfo.renderState().vehicle;
        if (animatable instanceof ttv.migami.jeg.vehicle.entity.BulldozerEntity dozer
                && (!dozer.powered() || AircraftLights.nightSwitch(1) <= 0)) return;
        if (!DefaultVehicleResource.hasGlowTexture(animatable)) {
            return;
        }

        PoseStack poseStack = passInfo.poseStack();
        net.minecraft.resources.Identifier glow = DefaultVehicleResource.glowTexture(animatable);
        // Only the quads with a glow on them (lamps, screens): the rest of the model drew empty fragments.
        TextureCoverage coverage = TextureCoverage.of(glow);
        collector.submitCustomGeometry(
                poseStack,
                RenderTypes.entityTranslucentEmissive(glow),
                (pose, buffer) -> {
                    PoseStack glowPose = new PoseStack();
                    glowPose.last().set(pose);
                    com.mojang.blaze3d.vertex.VertexConsumer out = QuadMaskConsumer.wrap(buffer, coverage);
                    passInfo.renderPosed(() -> VehicleBones.drawAll(passInfo, glowPose, out, Brightness.FULL_BRIGHT.pack(),
                            VehicleBones.plan(passInfo.model(), coverage)));
                }
        );
    }
}
