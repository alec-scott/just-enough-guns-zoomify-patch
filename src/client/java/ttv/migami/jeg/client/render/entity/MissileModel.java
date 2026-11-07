package ttv.migami.jeg.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.GunFlare;

/**
 * A missile or rocket in flight, lined up with its motion: a round body with a pointed nose and a dark seeker band,
 * four swept tail fins, a nozzle, and the motor's flame out of the back. The motor glow and smoke trail are drawn by
 * {@link ttv.migami.jeg.client.render.MissileTrailRenderer}.
 */
public final class MissileModel {
    private static final Identifier TEXTURE = Reference.id("textures/entity/missile.png");
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final int SIDES = 10;
    private static final int BODY = 0xFFE4E6E0;
    private static final int BAND = 0xFF3A3D38;
    private static final int NOSE = 0xFFC9CCC4;
    private static final int FIN = 0xFF8E928A;
    private static final int NOZZLE = 0xFF2A2A2A;

    private MissileModel() {}

    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 motion, float length, float radius, int light, float flameSeed) {
        poseStack.pushPose();
        // The render origin is the entity's feet; its centre is close enough at this size.
        if (motion.lengthSqr() > 1.0E-4D) {
            double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            poseStack.mulPose(Axis.YP.rotationDegrees((float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG)));
            poseStack.mulPose(Axis.XP.rotationDegrees((float) (Mth.atan2(motion.y, horizontal) * -Mth.RAD_TO_DEG)));
        }
        int lit = Math.max(light, FULL_BRIGHT / 2);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> {
            float r = radius * 0.5F;
            float back = -length * 0.5F;
            float bandStart = length * 0.22F;
            float bandEnd = length * 0.27F;
            float shoulder = length * 0.3F;
            float tip = length * 0.5F;
            // Body, seeker band, ogive nose in two steps and a pointed tip.
            tube(pose, buffer, lit, BODY, back, r, bandStart, r);
            tube(pose, buffer, lit, BAND, bandStart, r * 1.01F, bandEnd, r * 1.01F);
            tube(pose, buffer, lit, BODY, bandEnd, r, shoulder, r);
            tube(pose, buffer, lit, NOSE, shoulder, r, shoulder + (tip - shoulder) * 0.55F, r * 0.62F);
            tube(pose, buffer, lit, NOSE, shoulder + (tip - shoulder) * 0.55F, r * 0.62F, tip, 0.0F);
            // Nozzle: a short dark flare, closed at the back.
            tube(pose, buffer, lit, NOZZLE, back - length * 0.04F, r * 0.78F, back, r * 0.6F);
            cap(pose, buffer, lit, NOZZLE, back - length * 0.04F, r * 0.78F);
            // Four swept fins.
            for (int fin = 0; fin < 4; fin++) {
                float angle = Mth.HALF_PI * fin + Mth.PI / 4.0F;
                fin(pose, buffer, lit, angle, r, back, length);
            }
        });
        // The flame fires out of the tail, along local -Z.
        poseStack.translate(0.0F, 0.0F, -length * 0.54F);
        float flameScale = radius * 2.6F;
        collector.submitCustomGeometry(poseStack, GunFlare.RENDER_TYPE,
                (pose, buffer) -> GunFlare.render(pose.pose(), buffer, flameSeed, flameScale));
        poseStack.popPose();
    }

    /** A round section from z0 (radius r0) to z1 (radius r1); r1 of 0 makes a cone. */
    private static void tube(PoseStack.Pose pose, VertexConsumer buffer, int light, int colour, float z0, float r0, float z1, float r1) {
        float slope = (r0 - r1) / Math.max(1.0E-4F, z1 - z0);
        for (int side = 0; side < SIDES; side++) {
            float a0 = Mth.TWO_PI * side / SIDES;
            float a1 = Mth.TWO_PI * (side + 1) / SIDES;
            float c0 = Mth.cos(a0);
            float s0 = Mth.sin(a0);
            float c1 = Mth.cos(a1);
            float s1 = Mth.sin(a1);
            float cm = Mth.cos((a0 + a1) * 0.5F);
            float sm = Mth.sin((a0 + a1) * 0.5F);
            float nz = slope;
            float inv = Mth.invSqrt(1.0F + nz * nz);
            vertex(pose, buffer, light, colour, c0 * r0, s0 * r0, z0, 0.0F, 1.0F, cm * inv, sm * inv, nz * inv);
            vertex(pose, buffer, light, colour, c1 * r0, s1 * r0, z0, 1.0F, 1.0F, cm * inv, sm * inv, nz * inv);
            vertex(pose, buffer, light, colour, c1 * r1, s1 * r1, z1, 1.0F, 0.0F, cm * inv, sm * inv, nz * inv);
            vertex(pose, buffer, light, colour, c0 * r1, s0 * r1, z1, 0.0F, 0.0F, cm * inv, sm * inv, nz * inv);
        }
    }

    private static void cap(PoseStack.Pose pose, VertexConsumer buffer, int light, int colour, float z, float r) {
        for (int side = 0; side < SIDES; side++) {
            float a0 = Mth.TWO_PI * side / SIDES;
            float a1 = Mth.TWO_PI * (side + 1) / SIDES;
            vertex(pose, buffer, light, colour, 0.0F, 0.0F, z, 0.5F, 0.5F, 0.0F, 0.0F, -1.0F);
            vertex(pose, buffer, light, colour, 0.0F, 0.0F, z, 0.5F, 0.5F, 0.0F, 0.0F, -1.0F);
            vertex(pose, buffer, light, colour, Mth.cos(a1) * r, Mth.sin(a1) * r, z, 1.0F, 0.0F, 0.0F, 0.0F, -1.0F);
            vertex(pose, buffer, light, colour, Mth.cos(a0) * r, Mth.sin(a0) * r, z, 0.0F, 0.0F, 0.0F, 0.0F, -1.0F);
        }
    }

    /** A thin swept fin standing out from the body at that angle: long at the root, short at the tip. */
    private static void fin(PoseStack.Pose pose, VertexConsumer buffer, int light, float angle, float r, float back, float length) {
        float c = Mth.cos(angle);
        float s = Mth.sin(angle);
        float span = r * 1.9F;
        float rootFront = back + length * 0.2F;
        float tipFront = back + length * 0.09F;
        float tipBack = back + length * 0.01F;
        float thick = r * 0.09F;
        // Corners in the fin's own plane (outward, along), offset either side by its thickness.
        float[][] outline = {{r, back}, {r, rootFront}, {r + span, tipFront}, {r + span, tipBack}};
        float nx = -s;
        float ny = c;
        for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
            float ox = nx * thick * sideSign;
            float oy = ny * thick * sideSign;
            int[] order = sideSign > 0 ? new int[] {0, 1, 2, 3} : new int[] {3, 2, 1, 0};
            for (int i : order) {
                float out = outline[i][0];
                vertex(pose, buffer, light, FIN, c * out + ox, s * out + oy, outline[i][1], 0.5F, 0.5F, nx * sideSign, ny * sideSign, 0.0F);
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, int colour, float x, float y, float z,
                               float u, float v, float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z)
                .setColor(colour)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
