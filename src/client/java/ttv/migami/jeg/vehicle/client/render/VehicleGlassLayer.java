package ttv.migami.jeg.vehicle.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import ttv.migami.jeg.vehicle.client.resource.DefaultVehicleResource;
import ttv.migami.jeg.vehicle.client.resource.VehicleGlassTextures;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The vehicle's glass, drawn translucent over the opaque main pass (see VehicleGlassTextures): only the texture's
 * partly transparent pixels, lit like the rest of the vehicle, so windows and canopies stay see-through.
 */
final class VehicleGlassLayer<T extends VehicleEntity> extends GeoRenderLayer<T, Void, VehicleRenderState> {
    VehicleGlassLayer(GeoRenderer<T, Void, VehicleRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void preRender(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        VehicleEntity vehicle = passInfo.renderState().vehicle;
        if (vehicle == null || passInfo.renderState().hideWhileZooming) {
            return;
        }
        Identifier glass = VehicleGlassTextures.split(DefaultVehicleResource.texture(vehicle)).glass();
        if (glass == null) {
            return;
        }
        int light = passInfo.packedLight();
        collector.submitCustomGeometry(passInfo.poseStack(), RenderTypes.entityTranslucent(glass), (pose, buffer) -> {
            PoseStack glassPose = new PoseStack();
            glassPose.last().set(pose);
            passInfo.renderPosed(() -> {
                for (GeoBone bone : passInfo.model().topLevelBones()) {
                    bone.render(passInfo, glassPose, buffer, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                }
            });
        });
    }
}
