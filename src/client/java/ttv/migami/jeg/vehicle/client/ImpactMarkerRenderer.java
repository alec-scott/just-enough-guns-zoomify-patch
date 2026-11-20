package ttv.migami.jeg.vehicle.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import ttv.migami.jeg.client.render.FarDraw;
import ttv.migami.jeg.vehicle.entity.MortarEntity;
import ttv.migami.jeg.vehicle.entity.ShellGunEntity;

/**
 * Where the gunner's next round will land (the user, 2026-11-19: "how do I see where the mortar is going to land"). For
 * the mortar's gunner and a shell gun's (Mk42, BL-132): a ring on the ground at the predicted impact the size of the
 * shell's danger area, slowly pulsing, a dot in its middle, and a tall thin beam of light standing on it so the spot can
 * be found from the gun at any range. Red-orange on loaded terrain; amber and dashed when the shell leaves the loaded
 * world and the ground is taken as level with the gun ({@link ShellAim}). Beyond the render distance it is drawn
 * pulled in as the blasts are ({@link FarDraw}), so the spot still shows.
 */
public final class ImpactMarkerRenderer {
    /** The ring's radius, blocks: about the mortar round's lethal radius. */
    private static final float RING = 3.0F;
    private static final float BEAM_HEIGHT = 28.0F;
    private static final int SEGMENTS = 48;
    private static final int SURE = 0xFF5A2A;
    private static final int ASSUMED = 0xFFB040;

    private ImpactMarkerRenderer() {}

    public static void render(PoseStack poseStack, MultiBufferSource buffers, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        ShellAim.Prediction prediction;
        if (mc.player.getVehicle() instanceof MortarEntity mortar && mortar.getControllingPassenger() == mc.player) {
            prediction = MortarAim.predict(mortar, partialTick);
        } else if (mc.player.getVehicle() instanceof ShellGunEntity gun && gun.laysGun(mc.player)) {
            prediction = ShellGunAim.predict(gun, partialTick);
        } else {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 relative = prediction.impact().subtract(camera.position());
        float scale = FarDraw.scale(relative);
        Matrix4f matrix = new Matrix4f(poseStack.last().pose()).scale(scale);
        // Where it is drawn: shrunk towards the camera by the same factor, so it covers the same pixels.
        float x = (float) relative.x;
        float y = (float) relative.y + 0.06F;
        float z = (float) relative.z;
        double distance = relative.length();
        int rgb = prediction.assumedLevel() ? ASSUMED : SURE;
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float seconds = (mc.level.getGameTime() + partialTick) / 20.0F;
        float pulse = 0.75F + 0.25F * Mth.sin(seconds * 4.0F);
        // A far ring keeps a readable width; a near one a thin line.
        float width = Mth.clamp((float) distance * 0.006F, 0.12F, 1.6F);
        VertexConsumer consumer = buffers.getBuffer(RenderTypes.lightning());
        ring(consumer, matrix, x, y, z, RING, width, r, g, b, 0.85F * pulse, prediction.assumedLevel());
        ring(consumer, matrix, x, y, z, Math.max(0.25F, width * 1.4F), width * 1.4F, r, g, b, 0.9F, false);
        beam(consumer, matrix, x, y, z, camera, Mth.clamp((float) distance * 0.004F, 0.08F, 1.2F), r, g, b, 0.55F * pulse);
    }

    /** A flat ring on the ground, soft at both edges; dashed for a guess. */
    private static void ring(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float radius, float width,
                             float r, float g, float b, float alpha, boolean dashed) {
        float inner = Math.max(0.0F, radius - width);
        float outer = radius + width * 0.5F;
        for (int i = 0; i < SEGMENTS; i++) {
            if (dashed && (i & 1) == 1) {
                continue;
            }
            float a0 = Mth.TWO_PI * i / SEGMENTS;
            float a1 = Mth.TWO_PI * (i + 1) / SEGMENTS;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            consumer.addVertex(matrix, x + c0 * inner, y, z + s0 * inner).setColor(r, g, b, 0.0F);
            consumer.addVertex(matrix, x + c1 * inner, y, z + s1 * inner).setColor(r, g, b, 0.0F);
            consumer.addVertex(matrix, x + c1 * radius, y, z + s1 * radius).setColor(r, g, b, alpha);
            consumer.addVertex(matrix, x + c0 * radius, y, z + s0 * radius).setColor(r, g, b, alpha);
            consumer.addVertex(matrix, x + c0 * radius, y, z + s0 * radius).setColor(r, g, b, alpha);
            consumer.addVertex(matrix, x + c1 * radius, y, z + s1 * radius).setColor(r, g, b, alpha);
            consumer.addVertex(matrix, x + c1 * outer, y, z + s1 * outer).setColor(r, g, b, 0.0F);
            consumer.addVertex(matrix, x + c0 * outer, y, z + s0 * outer).setColor(r, g, b, 0.0F);
        }
    }

    /** A thin upright beam on the spot, turned to face the camera, bright at the foot and fading upward. */
    private static void beam(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, Camera camera,
                             float halfWidth, float r, float g, float b, float alpha) {
        float dx = -x, dz = -z;
        float length = Mth.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-3F) {
            return;
        }
        // Across the line of sight, horizontally.
        float sx = -dz / length * halfWidth;
        float sz = dx / length * halfWidth;
        float top = y + BEAM_HEIGHT;
        consumer.addVertex(matrix, x - sx, y, z - sz).setColor(r, g, b, alpha);
        consumer.addVertex(matrix, x + sx, y, z + sz).setColor(r, g, b, alpha);
        consumer.addVertex(matrix, x + sx, top, z + sz).setColor(r, g, b, 0.0F);
        consumer.addVertex(matrix, x - sx, top, z - sz).setColor(r, g, b, 0.0F);
    }
}
