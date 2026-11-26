package ttv.migami.jeg.vehicle.client.render;

import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import ttv.migami.jeg.vehicle.client.resource.DefaultVehicleResource;
import ttv.migami.jeg.vehicle.client.resource.TextureCoverage;
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
        // Lit vertex by vertex as the hull is (VehicleVertexLight), so glass in a hangar is not daylight-bright.
        net.minecraft.world.phys.Vec3 camera = passInfo.cameraState().pos;
        // Only the quads with glass on them: the rest of the model drew fully transparent fragments (TextureCoverage).
        TextureCoverage coverage = TextureCoverage.of(glass);
        collector.submitCustomGeometry(passInfo.poseStack(), RenderTypes.entityTranslucent(glass), (pose, buffer) -> {
            PoseStack glassPose = new PoseStack();
            glassPose.last().set(pose);
            VehicleVertexLight.Draw draw = VehicleVertexLight.begin(buffer, camera, light, vehicle);
            // Masked outside the lighting, so the quads left out aren't lit either.
            com.mojang.blaze3d.vertex.VertexConsumer out = QuadMaskConsumer.wrap(draw.consumer(), coverage);
            passInfo.renderPosed(() -> VehicleBones.drawAll(passInfo, glassPose, out, draw.light(),
                    VehicleBones.plan(passInfo.model(), coverage)));
            draw.end();
        });
    }
}
