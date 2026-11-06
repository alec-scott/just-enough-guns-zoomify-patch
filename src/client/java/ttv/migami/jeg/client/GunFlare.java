package ttv.migami.jeg.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import ttv.migami.jeg.Reference;

/**
 * The flame at a gun's muzzle: a burst facing out of the barrel and crossed tongues of fire reaching forward along the bore, so
 * it has depth from the shooter's angle and from the side. Changes shape every round; {@code scale} sets how big a gun's flame
 * is (1 = a minigun).
 */
public final class GunFlare {
    public static final RenderType RENDER_TYPE = RenderTypes.entityTranslucentEmissive(Reference.id("textures/effect/minigun_flare.png"));
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int VARIANTS = 4;
    // Blocks of the gun's own model space; the first-person gun is scaled up a little on top.
    private static final float MIN_LENGTH = 0.85F;
    private static final float MAX_LENGTH = 1.45F;
    private static final float MIN_WIDTH = 0.36F;
    private static final float MAX_WIDTH = 0.56F;
    private static final float MIN_BURST = 0.20F;
    private static final float MAX_BURST = 0.34F;
    // From behind the gun the barrel hides anything right at the muzzle, so the burst sits a little way out.
    private static final float BURST_AHEAD = 0.34F;

    private GunFlare() {}

    public static void render(PoseStack poseStack, VertexConsumer consumer, float seed, float scale) {
        render(poseStack.last().pose(), consumer, seed, scale);
    }

    /** Draws it from the current origin at the muzzle, firing along local -Z. */
    public static void render(Matrix4f matrix, VertexConsumer consumer, float seed, float scale) {
        // One random number per round is stretched into several unrelated ones.
        float a = fraction(seed * 7.13F);
        float b = fraction(seed * 13.57F);
        float c = fraction(seed * 29.31F);
        float length = lerp(a, MIN_LENGTH, MAX_LENGTH) * scale;
        float width = lerp(b, MIN_WIDTH, MAX_WIDTH) * scale;
        float burst = lerp(c, MIN_BURST, MAX_BURST) * scale;
        float ahead = BURST_AHEAD * scale;
        int flameVariant = (int) (a * VARIANTS) % VARIANTS;
        int burstVariant = (int) (b * VARIANTS) % VARIANTS;
        float u0 = flameVariant / (float) VARIANTS;
        float u1 = u0 + 1.0F / VARIANTS;

        // Crossed tongues, base at the muzzle, turned around the bore a little each round.
        float angle = c * (float) (Math.PI * 2.0D);
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        tongue(matrix, consumer, cos * width, sin * width, length, u0, u1);
        tongue(matrix, consumer, -sin * width, cos * width, length, u0, u1);
        tongue(matrix, consumer, (cos - sin) * 0.7F * width, (sin + cos) * 0.7F * width, length * 0.8F, u0, u1);

        // Burst facing out of the muzzle.
        float bu0 = burstVariant / (float) VARIANTS;
        float bu1 = bu0 + 1.0F / VARIANTS;
        float rx = cos * burst;
        float ry = sin * burst;
        vertex(consumer, matrix, -rx + ry, -ry - rx, -ahead, bu0, 1.0F);
        vertex(consumer, matrix, rx + ry, ry - rx, -ahead, bu1, 1.0F);
        vertex(consumer, matrix, rx - ry, ry + rx, -ahead, bu1, 0.5F);
        vertex(consumer, matrix, -rx - ry, -ry + rx, -ahead, bu0, 0.5F);
    }

    private static void tongue(Matrix4f matrix, VertexConsumer consumer, float sideX, float sideY, float length, float u0, float u1) {
        float halfX = sideX * 0.5F;
        float halfY = sideY * 0.5F;
        vertex(consumer, matrix, -halfX, -halfY, 0.0F, u0, 0.5F);
        vertex(consumer, matrix, -halfX, -halfY, -length, u1, 0.5F);
        vertex(consumer, matrix, halfX, halfY, -length, u1, 0.0F);
        vertex(consumer, matrix, halfX, halfY, 0.0F, u0, 0.0F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, 1.0F);
    }

    private static float fraction(float value) {
        return value - (float) Math.floor(value);
    }

    private static float lerp(float t, float from, float to) {
        return from + (to - from) * t;
    }
}
