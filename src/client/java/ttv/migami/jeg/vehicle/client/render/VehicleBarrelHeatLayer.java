package ttv.migami.jeg.vehicle.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.geckolib.util.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import ttv.migami.jeg.client.render.GunBarrelHeatRenderer;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * A rotary gun's barrels glowing with heat: the barrel parts of the model itself are drawn a second time, unlit, in a
 * red that deepens to orange as the gun heats up (see GunBarrelHeatRenderer for the heat and the smoke). Being the model's
 * own geometry, the glow turns, pitches and spins with the barrels and is hidden by whatever hides them.
 */
final class VehicleBarrelHeatLayer<T extends VehicleEntity> extends GeoRenderLayer<T, Void, VehicleRenderState> {
    /** The spinning barrel cluster and everything on it (barrels, muzzle brake). */
    private static final String BARREL_BONE = "barrel2";
    /** The muzzle brake on the end of them. */
    private static final String MUZZLE_BONE = "breaker";
    /** Any plain pale texture does: only the tint shows. */
    private static final Identifier WHITE = Identifier.withDefaultNamespace("textures/block/white_concrete.png");
    /** Heat from which the barrels start to show it. */
    private static final float GLOW_FROM = 0.15F;
    private static final float SWELL = 1.03F;

    VehicleBarrelHeatLayer(GeoRenderer<T, Void, VehicleRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void submitRenderTask(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        VehicleEntity vehicle = passInfo.renderState().vehicle;
        if (vehicle == null) {
            return;
        }
        float hot = (GunBarrelHeatRenderer.heat(vehicle.getId()) - GLOW_FROM) / (1.0F - GLOW_FROM);
        if (hot <= 0.0F) {
            return;
        }
        GeoBone barrel = passInfo.model().getBone(BARREL_BONE).orElse(null);
        if (barrel == null) {
            return;
        }
        // The gunner looks straight down the barrels, which fill the bottom of the view: kept fainter there.
        Minecraft mc = Minecraft.getInstance();
        boolean ownSight = mc.player != null && mc.player.getVehicle() == vehicle && mc.options.getCameraType().isFirstPerson();
        float strength = ownSight ? 0.55F : 1.0F;
        // A dull red along the barrels that warms towards orange as they heat; the muzzle end runs hottest.
        int barrelColour = heatColour((0.06F + 0.3F * hot) * strength, hot);
        int muzzleColour = heatColour((0.14F + 0.5F * hot) * strength, hot);
        // Where the model itself was drawn: by now the pass's own pose stack has been unwound past that.
        PoseStack modelPose = new PoseStack();
        modelPose.last().set(passInfo.getModelRenderMatrixPose());
        // A later draw order than the model: drawn before it, the barrels painted over all but its edges.
        collector.order(1).submitCustomGeometry(
                modelPose,
                RenderTypes.entityTranslucentEmissive(WHITE),
                (pose, buffer) -> {
                    PoseStack heatPose = new PoseStack();
                    heatPose.last().set(pose);
                    passInfo.renderPosed(() -> {
                        // Down the model from its root to the barrels, as the model's own draw gets there.
                        Deque<GeoBone> chain = new ArrayDeque<>();
                        for (GeoBone bone = barrel; bone != null; bone = bone.parent()) {
                            chain.push(bone);
                        }
                        for (GeoBone bone : chain) {
                            RenderUtil.prepMatrixForBone(heatPose, bone);
                        }
                        // A hair fatter than the barrels, about their own axis: drawn at exactly their surface the
                        // glow lost out to it and only showed in patches.
                        barrel.translateToPivotPoint(heatPose);
                        heatPose.scale(SWELL, SWELL, 1.0F);
                        barrel.translateAwayFromPivotPoint(heatPose);
                        renderTree(passInfo, barrel, heatPose, buffer, barrelColour, muzzleColour);
                    });
                }
        );
    }

    private static int heatColour(float alpha, float hot) {
        int a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        int green = Math.round(30.0F + 80.0F * hot);
        return (a << 24) | (255 << 16) | (green << 8) | 14;
    }

    private static void renderTree(RenderPassInfo<VehicleRenderState> passInfo, GeoBone bone, PoseStack pose, VertexConsumer buffer,
                                   int colour, int muzzleColour) {
        int own = MUZZLE_BONE.equals(bone.name()) ? muzzleColour : colour;
        bone.render(passInfo, pose, buffer, Brightness.FULL_BRIGHT.pack(), OverlayTexture.NO_OVERLAY, own);
        for (GeoBone child : bone.children()) {
            pose.pushPose();
            RenderUtil.prepMatrixForBone(pose, child);
            renderTree(passInfo, child, pose, buffer, own, muzzleColour);
            pose.popPose();
        }
    }
}
