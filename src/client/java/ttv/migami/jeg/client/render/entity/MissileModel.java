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

/** A missile or rocket in flight: a pointed body lined up with its motion and the motor's flame out of the back. */
public final class MissileModel {
    private static final Identifier TEXTURE = Reference.id("textures/entity/missile.png");
    private static final int FULL_BRIGHT = 0x00F000F0;

    private MissileModel() {}

    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 motion, float length, float radius, int light, float flameSeed) {
        poseStack.pushPose();
        // The render origin is the entity's feet; its centre is close enough at this size.
        if (motion.lengthSqr() > 1.0E-4D) {
            double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            poseStack.mulPose(Axis.YP.rotationDegrees((float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG)));
            poseStack.mulPose(Axis.XP.rotationDegrees((float) (Mth.atan2(motion.y, horizontal) * -Mth.RAD_TO_DEG)));
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> {
            float half = radius * 0.5F;
            float back = -length * 0.5F;
            float shoulder = length * 0.32F;
            box(pose, buffer, light, -half, half, -half, half, back, shoulder);
            // The nose narrows in one step.
            box(pose, buffer, light, -half * 0.55F, half * 0.55F, -half * 0.55F, half * 0.55F, shoulder, length * 0.5F);
            // Fins.
            box(pose, buffer, light, -half * 2.1F, half * 2.1F, -half * 0.12F, half * 0.12F, back, back + length * 0.16F);
            box(pose, buffer, light, -half * 0.12F, half * 0.12F, -half * 2.1F, half * 2.1F, back, back + length * 0.16F);
        });
        // The flame fires out of the tail, along local -Z.
        poseStack.translate(0.0F, 0.0F, -length * 0.5F);
        float flameScale = radius * 2.4F;
        collector.submitCustomGeometry(poseStack, GunFlare.RENDER_TYPE,
                (pose, buffer) -> GunFlare.render(pose.pose(), buffer, flameSeed, flameScale));
        poseStack.popPose();
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buffer, int light,
                            float x0, float x1, float y0, float y1, float z0, float z1) {
        quad(pose, buffer, light, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
        quad(pose, buffer, light, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1);
        quad(pose, buffer, light, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);
        quad(pose, buffer, light, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0);
        quad(pose, buffer, light, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);
        quad(pose, buffer, light, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int light,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float nx, float ny, float nz) {
        vertex(pose, buffer, light, x1, y1, z1, 0.0F, 1.0F, nx, ny, nz);
        vertex(pose, buffer, light, x2, y2, z2, 1.0F, 1.0F, nx, ny, nz);
        vertex(pose, buffer, light, x3, y3, z3, 1.0F, 0.0F, nx, ny, nz);
        vertex(pose, buffer, light, x4, y4, z4, 0.0F, 0.0F, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, float x, float y, float z, float u, float v,
                               float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(Math.max(light, FULL_BRIGHT / 2))
                .setNormal(pose, nx, ny, nz);
    }
}
