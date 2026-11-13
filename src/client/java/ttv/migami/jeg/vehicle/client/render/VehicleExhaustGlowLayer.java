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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The heat inside a jet's exhausts. Soft discs of light fill each nozzle. They are added to what is behind them, so the
 * dark interior and the turbine cone glow rather than being painted over: a hot core just behind the cone, an orange
 * glow out to the nozzle's rim. From a dull red ember at idle to a yellow-orange glare flat out. Drawn in the engine's
 * own frame, they sit in the nozzles whatever the jet does; side on they narrow to nothing, and from the front the
 * engine hides them.
 */
final class VehicleExhaustGlowLayer<T extends VehicleEntity> extends GeoRenderLayer<T, Void, VehicleRenderState> {
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("jeg", "textures/effect/exhaust_glow.png");
    /** The A-10's nozzles: the bone at each one's mouth (its pivot is on the engine's axis). */
    private static final String[] NOZZLES = {"houwaiyuan", "houwaiyuan2"};
    /** Model units (1/16 block) along the engine, back from the model's origin: the cone's tip and the nozzle's mouth. */
    private static final float NOZZLE_PIVOT_Z = 67.5F;
    private static final float CORE_Z = 78.0F;
    private static final float MOUTH_Z = 79.4F;
    private static final float CORE_RADIUS = 0.40F;
    private static final float AIRBORNE_MIN_HEAT = 0.6F;
    private static final float MOUTH_RADIUS = 0.68F;

    VehicleExhaustGlowLayer(GeoRenderer<T, Void, VehicleRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void submitRenderTask(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        VehicleEntity jet = passInfo.renderState().vehicle;
        if (jet == null || !jet.isJet() || jet.isWrecked()) {
            return;
        }
        float spool = Mth.clamp(jet.jetSpool(), 0.0F, 1.0F);
        if (spool <= 0.01F) {
            return;
        }
        float airspeed = (float) Mth.clamp(jet.getDeltaMovement().length() / jet.jetTopSpeed(), 0.0D, 1.0D);
        float heat = Mth.clamp(0.75F * spool + 0.25F * airspeed, 0.0F, 1.0F);
        // In the air the engines run hot whatever the throttle: throttled back the glow eases off but stays a clear
        // orange (only on the ground at idle is it a dull ember).
        if (!jet.onGround()) {
            heat = Math.max(heat, AIRBORNE_MIN_HEAT);
        }
        // Lights up as the engines start, rather than snapping on.
        float lit = jet.onGround() ? Mth.clamp(spool / 0.15F, 0.0F, 1.0F) : 1.0F;
        int[] core = colour(heat, lit * (0.2F + 0.65F * heat), 210, 60, 15, 255, 215, 140);
        int[] mouth = colour(heat, lit * (0.35F + 0.65F * heat), 160, 35, 8, 255, 130, 40);
        PoseStack modelPose = new PoseStack();
        modelPose.last().set(passInfo.getModelRenderMatrixPose());
        collector.order(2).submitCustomGeometry(modelPose, RenderTypes.eyes(GLOW), (pose, buffer) -> {
            for (String name : NOZZLES) {
                GeoBone nozzle = passInfo.model().getBone(name).orElse(null);
                if (nozzle == null || nozzle.parent() == null) {
                    continue;
                }
                PoseStack glowPose = new PoseStack();
                glowPose.last().set(pose);
                passInfo.renderPosed(() -> {
                    // Into the engine's frame (the nozzle's parent): the fans spin inside it, the glow does not.
                    Deque<GeoBone> chain = new ArrayDeque<>();
                    for (GeoBone bone = nozzle.parent(); bone != null; bone = bone.parent()) {
                        chain.push(bone);
                    }
                    for (GeoBone bone : chain) {
                        RenderUtil.prepMatrixForBone(glowPose, bone);
                    }
                    float x = nozzle.pivotX() / 16.0F;
                    float y = nozzle.pivotY() / 16.0F;
                    // Back from the nozzle's pivot, whichever way the baked model runs.
                    float pivotZ = nozzle.pivotZ();
                    float back = Math.signum(pivotZ) / 16.0F;
                    Matrix4f matrix = glowPose.last().pose();
                    disc(buffer, matrix, x, y, pivotZ / 16.0F + (CORE_Z - NOZZLE_PIVOT_Z) * back, CORE_RADIUS, core);
                    disc(buffer, matrix, x, y, pivotZ / 16.0F + (MOUTH_Z - NOZZLE_PIVOT_Z) * back, MOUTH_RADIUS, mouth);
                });
            }
        });
    }

    /** From an ember's red to the hot colour, scaled by brightness (the light is added, so dimmer is darker). */
    private static int[] colour(float heat, float brightness, int r0, int g0, int b0, int r1, int g1, int b1) {
        float b = Mth.clamp(brightness, 0.0F, 1.0F);
        return new int[]{
                Math.round(Mth.lerp(heat, r0, r1) * b),
                Math.round(Mth.lerp(heat, g0, g1) * b),
                Math.round(Mth.lerp(heat, b0, b1) * b),
                Math.round(255.0F * b)
        };
    }

    /** A disc across the nozzle, facing along the engine, drawn from both sides. */
    private static void disc(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, float radius, int[] colour) {
        float x0 = x - radius;
        float x1 = x + radius;
        float y0 = y - radius;
        float y1 = y + radius;
        vertex(buffer, matrix, x0, y0, z, 0.0F, 1.0F, colour);
        vertex(buffer, matrix, x1, y0, z, 1.0F, 1.0F, colour);
        vertex(buffer, matrix, x1, y1, z, 1.0F, 0.0F, colour);
        vertex(buffer, matrix, x0, y1, z, 0.0F, 0.0F, colour);
        vertex(buffer, matrix, x0, y1, z, 0.0F, 0.0F, colour);
        vertex(buffer, matrix, x1, y1, z, 1.0F, 0.0F, colour);
        vertex(buffer, matrix, x1, y0, z, 1.0F, 1.0F, colour);
        vertex(buffer, matrix, x0, y0, z, 0.0F, 1.0F, colour);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, float u, float v, int[] colour) {
        buffer.addVertex(matrix, x, y, z)
                .setColor(colour[0], colour[1], colour[2], colour[3])
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(0.0F, 0.0F, 1.0F);
    }
}
