package ttv.migami.jeg.vehicle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import ttv.migami.jeg.vehicle.projectile.VehicleDecoyEntity;

/**
 * A burning countermeasure flare: a magnesium fire too bright to look at. Drawn as soft glows always facing the camera: a white-hot core, a wide warm halo around it and a thin horizontal glare streak across it, all
 * flickering as the flare burns, flaring up as it ignites and dying away as it burns out. (Its smoke trail and sparks
 * are the entity's own particles.) Smoke decoys are only their smoke.
 */
public final class VehicleDecoyRenderer extends EntityRenderer<VehicleDecoyEntity, VehicleDecoyRenderer.State> {
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("jeg", "textures/effect/flare_glow.png");
    private static final int FULL_BRIGHT = 0x00F000F0;

    public VehicleDecoyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.smokeDecoy || state.burn <= 0.01F) {
            return;
        }
        float age = state.ageInTicks;
        // A burning flare's light never holds still: a fast flutter over a slower swell.
        float flicker = 0.82F + 0.1F * Mth.sin(age * 2.7F + state.seed) + 0.08F * Mth.sin(age * 7.3F + state.seed * 1.7F);
        float ignition = age < 4.0F ? 1.0F + (4.0F - age) * 0.35F : 1.0F;
        float glow = Mth.clamp(state.burn * flicker, 0.0F, 1.0F);
        float size = (0.75F + 0.25F * flicker) * ignition * (0.5F + 0.5F * state.burn);
        poseStack.pushPose();
        poseStack.translate(0.0D, state.boundingBoxHeight * 0.5D, 0.0D);
        poseStack.mulPose(camera.orientation);
        collector.submitCustomGeometry(poseStack, RenderTypes.eyes(GLOW), (pose, buffer) -> {
            // The halo: a wide warm glow lighting the air around it.
            quad(pose, buffer, 4.2F * size, 4.2F * size, 255, 205, 140, glow * 0.6F);
            // The glare streak, as a camera sees a light this bright.
            quad(pose, buffer, 6.0F * size, 0.28F * size, 255, 230, 195, glow * 0.8F);
            // A hot yellow-white glow, then the white core itself (twice, to burn out to white).
            quad(pose, buffer, 2.0F * size, 2.0F * size, 255, 240, 200, glow);
            quad(pose, buffer, 1.0F * size, 1.0F * size, 255, 255, 252, glow);
            quad(pose, buffer, 0.7F * size, 0.7F * size, 255, 255, 255, glow);
        });
        poseStack.popPose();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(VehicleDecoyEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.smokeDecoy = entity.isSmokeDecoy();
        state.burn = entity.flareBurn(partialTick);
        state.seed = (entity.getId() * 0.618F) % 6.283F;
    }

    public static final class State extends EntityRenderState {
        boolean smokeDecoy;
        /** How brightly it still burns: 1 most of its life, dying to 0 as it burns out. */
        float burn;
        float seed;
    }

    /**
     * A camera-facing quad, half-width w and half-height h, in the colour given; brightness fades it (the glow's own
     * texture is white, fading out to its edge, so it lightens whatever is behind it, sky or night).
     */
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, float w, float h, int red, int green, int blue, float brightness) {
        int a = Math.round(255 * Mth.clamp(brightness, 0.0F, 1.0F));
        vertex(pose, buffer, -w, -h, 0.0F, 1.0F, red, green, blue, a);
        vertex(pose, buffer, w, -h, 1.0F, 1.0F, red, green, blue, a);
        vertex(pose, buffer, w, h, 1.0F, 0.0F, red, green, blue, a);
        vertex(pose, buffer, -w, h, 0.0F, 0.0F, red, green, blue, a);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float u, float v, int r, int g, int b, int a) {
        buffer.addVertex(pose, x, y, 0.0F)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
    }
}
