package ttv.migami.jeg.vehicle.client.render;

import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * The FSV-6's tow bar, drawn while a vehicle is on the hitch (it was not drawn: the towed vehicle followed 1.6 blocks
 * behind with nothing between them). A vehicle towed by its nose hangs on an A-frame from the pintle to two eyes either
 * side of its nose; a gun towed by its own trail (a tow eye, {@link VehicleEntity#towEye()}) on a short drawbar from the
 * pintle to the eye. Dark steel, lit by the world.
 * <p>
 * Built in the camera-relative frame: the pintle through the truck's model matrix (it follows the hull's pitch and
 * roll), the towed end from the towed vehicle's interpolated position and heading.
 */
final class SupportTowBarLayer extends GeoRenderLayer<SupportVehicleEntity, Void, VehicleRenderState> {
    private static final Identifier STEEL = Identifier.withDefaultNamespace("textures/block/anvil.png");
    /** Half the bar's section (a 10 cm square tube) and the A-frame's half spread at the towed vehicle's nose, blocks. */
    private static final float HALF = 0.05F, SPREAD = 0.42F;
    private static final int TINT = 0xFF707066;

    SupportTowBarLayer(GeoRenderer<SupportVehicleEntity, Void, VehicleRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void submitRenderTask(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        if (!(passInfo.renderState().vehicle instanceof SupportVehicleEntity truck) || truck.towedEntityId() < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(truck.towedEntityId()) instanceof VehicleEntity towed)) return;
        float partial = Mth.clamp(passInfo.renderState().ageInTicks - truck.tickCount, 0.0F, 1.0F);
        Vec3 camera = passInfo.cameraState().pos;
        Matrix4f model = new Matrix4f(passInfo.getModelRenderMatrixState());
        Vec3 pin = SupportVehicleEntity.PINTLE;
        Vector3f pintle = model.transformPosition(new Vector3f((float) pin.x / 16.0F, (float) pin.y / 16.0F, (float) pin.z / 16.0F));
        // The towed end where the towed vehicle is drawn (SupportVehicleEntity.clientTowPose), so the bar never
        // stretches or snaps as packets arrive.
        var towPose = SupportVehicleEntity.clientTowPose(towed, partial);
        Vec3 lerped = towed.getPosition(partial);
        Vec3 origin = towPose != null ? new Vec3(towPose.position().x, lerped.y, towPose.position().z) : lerped;
        float yaw = towPose != null ? towPose.yaw() : Mth.rotLerp(partial, towed.yRotO, towed.getYRot());
        Vec3 eye = towed.towEye();
        Vector3f[][] bars;
        if (eye != null) {
            // Data coordinates (+Z forward) to model pixels (+Z rearward), as SupportRig.world takes them.
            bars = new Vector3f[][]{{pintle, relative(SupportRig.world(origin, yaw, eye.x * 16, eye.y * 16, -eye.z * 16), camera)}};
        } else {
            double nose = towed.getBbWidth() * 0.5D;
            double height = pin.y;
            Vector3f left = relative(SupportRig.world(origin, yaw, SPREAD * 16, height, -nose * 16), camera);
            Vector3f right = relative(SupportRig.world(origin, yaw, -SPREAD * 16, height, -nose * 16), camera);
            bars = new Vector3f[][]{{pintle, left}, {pintle, right}};
        }
        int light = passInfo.packedLight();
        collector.order(0).submitCustomGeometry(new PoseStack(), RenderTypes.entityCutout(STEEL), (pose, buffer) -> {
            for (Vector3f[] bar : bars) tube(buffer, bar[0], bar[1], light);
        });
    }

    private static Vector3f relative(Vec3 world, Vec3 camera) {
        return new Vector3f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z));
    }

    /** A square tube from a to b, its four long faces (the ends are hidden in the couplings). */
    private static void tube(VertexConsumer buffer, Vector3f a, Vector3f b, int light) {
        Vector3f axis = new Vector3f(b).sub(a);
        float length = axis.length();
        if (length < 1.0E-3F) return;
        axis.div(length);
        Vector3f side = new Vector3f(axis).cross(0.0F, 1.0F, 0.0F);
        if (side.lengthSquared() < 1.0E-4F) side.set(1.0F, 0.0F, 0.0F);
        side.normalize();
        Vector3f up = new Vector3f(side).cross(axis).normalize();
        Vector3f[] normals = {up, side, new Vector3f(up).negate(), new Vector3f(side).negate()};
        for (int i = 0; i < 4; i++) {
            Vector3f n = normals[i], m = normals[(i + 1) % 4];
            // The face whose outward normal is n, spanning from the edge (n - m') to (n + m'), m' the next normal round.
            Vector3f c0 = new Vector3f(n).add(new Vector3f(m).negate()).mul(HALF);
            Vector3f c1 = new Vector3f(n).add(m).mul(HALF);
            // Wound counter-clockwise seen from outside (cutout culls the back).
            vertex(buffer, new Vector3f(a).add(c0), 0.0F, 0.0F, n, light);
            vertex(buffer, new Vector3f(a).add(c1), 0.25F, 0.0F, n, light);
            vertex(buffer, new Vector3f(b).add(c1), 0.25F, 1.0F, n, light);
            vertex(buffer, new Vector3f(b).add(c0), 0.0F, 1.0F, n, light);
        }
    }

    private static void vertex(VertexConsumer buffer, Vector3f p, float u, float v, Vector3f normal, int light) {
        buffer.addVertex(p.x, p.y, p.z).setColor(TINT).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(normal.x, normal.y, normal.z);
    }
}
