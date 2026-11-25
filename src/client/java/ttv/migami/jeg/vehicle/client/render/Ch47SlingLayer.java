package ttv.migami.jeg.vehicle.client.render;

import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ttv.migami.jeg.vehicle.entity.ChinookEntity;
import ttv.migami.jeg.vehicle.entity.ChinookSling;
import ttv.migami.jeg.vehicle.entity.SlingRope;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The Chinook's sling, drawn while it is out: the pendant from the belly hook to the apex ring, and the four legs from
 * the ring to a slung load's lift points, or hanging free with their hooks while the hook is empty. Each rope is drawn
 * from its simulated nodes (ChinookSling's {@link SlingRope}s, with weight), so it sags, bows and swings. Everything is
 * placed from the Chinook's smooth pose and the rope's eased end ({@link ChinookSling#clientEnd}), the same place the
 * load is drawn ({@link ChinookSling#clientLoadPosition}), so the ropes always meet it.
 */
final class Ch47SlingLayer extends GeoRenderLayer<ChinookEntity, Void, VehicleRenderState> {
    private static final Identifier STEEL = Identifier.withDefaultNamespace("textures/block/anvil.png");
    /** Pendant (a 4 cm wire rope, drawn a little thicker to read at range), legs, the ring and the hooks. */
    private static final float PENDANT = 0.035F, LEG = 0.025F, RING = 0.09F, HOOK = 0.06F;
    private static final int ROPE = 0xFF34342E, RING_TINT = 0xFF5A5A52;

    Ch47SlingLayer(GeoRenderer<ChinookEntity, Void, VehicleRenderState> renderer) {
        super(renderer);
    }

    private record Segment(Vector3f a, Vector3f b, float half, int tint) {}

    @Override
    public void submitRenderTask(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        if (!(passInfo.renderState().vehicle instanceof ChinookEntity chinook)) {
            return;
        }
        ChinookSling sling = chinook.sling();
        int state = sling.state();
        if (state == ChinookSling.STOWED || sling.length() < 0.05F) {
            return;
        }
        float partial = Mth.clamp(passInfo.renderState().ageInTicks - chinook.tickCount, 0.0F, 1.0F);
        Vec3 camera = passInfo.cameraState().pos;
        Vec3 hook = sling.hookWorld(partial);
        Vec3 end = sling.clientEnd(partial);
        List<Segment> segments = new ArrayList<>();
        VehicleEntity load = state == ChinookSling.CARRYING ? sling.loadEntity() : null;
        SlingRope pendant = sling.pendantRope();
        if (!pendant.started()) {
            return;
        }
        if (load != null) {
            // The ring rides a pendant's length down the rope; the legs run from it to the load's lift points as drawn.
            Vec3 ring = ChinookSling.ring(hook, end);
            rope(segments, pendant, partial, hook, ring, PENDANT, camera);
            List<Vec3> points = ChinookSling.liftPointsWorld(load, partial);
            List<SlingRope> legs = sling.legRopes();
            for (int i = 0; i < points.size() && i < legs.size(); i++) {
                if (legs.get(i).started()) {
                    rope(segments, legs.get(i), partial, ring, points.get(i), LEG, camera);
                }
            }
            ring(segments, ring, camera);
        } else {
            // Empty: the ring at the rope's end, the four legs hanging from it with their hooks, swinging on their own.
            rope(segments, pendant, partial, hook, end, PENDANT, camera);
            for (SlingRope leg : sling.legRopes()) {
                if (!leg.started()) {
                    continue;
                }
                rope(segments, leg, partial, null, null, LEG, camera);
                Vec3 tip = leg.node(leg.size() - 1, partial);
                Vec3 along = tip.subtract(leg.node(leg.size() - 2, partial));
                Vec3 dir = along.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, -1.0D, 0.0D) : along.normalize();
                segments.add(new Segment(rel(tip, camera), rel(tip.add(dir.scale(0.18D)), camera), HOOK, RING_TINT));
            }
            ring(segments, end, camera);
        }
        int light = passInfo.packedLight();
        collector.order(0).submitCustomGeometry(new PoseStack(), RenderTypes.entityCutout(STEEL), (pose, buffer) -> {
            for (Segment segment : segments) {
                tube(buffer, segment.a(), segment.b(), segment.half(), segment.tint(), light);
            }
        });
    }

    /**
     * A rope from its simulated nodes, eased between ticks; its pinned ends drawn exactly where they are held this frame
     * (the hook, the ring, a lift point), so it always meets them.
     */
    private static void rope(List<Segment> segments, SlingRope rope, float partial, Vec3 top, Vec3 bottom, float half, Vec3 camera) {
        int last = rope.size() - 1;
        Vec3 previous = top != null ? top : rope.node(0, partial);
        for (int i = 1; i <= last; i++) {
            Vec3 next = i == last && bottom != null ? bottom : rope.node(i, partial);
            segments.add(new Segment(rel(previous, camera), rel(next, camera), half, ROPE));
            previous = next;
        }
    }

    /** The apex ring: a short thick link. */
    private static void ring(List<Segment> segments, Vec3 at, Vec3 camera) {
        segments.add(new Segment(rel(at.add(0.0D, 0.1D, 0.0D), camera), rel(at.add(0.0D, -0.1D, 0.0D), camera), RING, RING_TINT));
    }

    private static Vector3f rel(Vec3 world, Vec3 camera) {
        return new Vector3f((float) (world.x - camera.x), (float) (world.y - camera.y), (float) (world.z - camera.z));
    }

    /** A square tube from a to b, its four long faces. */
    private static void tube(VertexConsumer buffer, Vector3f a, Vector3f b, float half, int tint, int light) {
        Vector3f axis = new Vector3f(b).sub(a);
        float length = axis.length();
        if (length < 1.0E-3F) {
            return;
        }
        axis.div(length);
        Vector3f side = new Vector3f(axis).cross(0.0F, 1.0F, 0.0F);
        if (side.lengthSquared() < 1.0E-4F) {
            side.set(1.0F, 0.0F, 0.0F);
        }
        side.normalize();
        Vector3f up = new Vector3f(side).cross(axis).normalize();
        Vector3f[] normals = {up, side, new Vector3f(up).negate(), new Vector3f(side).negate()};
        for (int i = 0; i < 4; i++) {
            Vector3f n = normals[i], m = normals[(i + 1) % 4];
            Vector3f c0 = new Vector3f(n).add(new Vector3f(m).negate()).mul(half);
            Vector3f c1 = new Vector3f(n).add(m).mul(half);
            vertex(buffer, new Vector3f(a).add(c0), 0.0F, 0.0F, n, tint, light);
            vertex(buffer, new Vector3f(a).add(c1), 0.25F, 0.0F, n, tint, light);
            vertex(buffer, new Vector3f(b).add(c1), 0.25F, 1.0F, n, tint, light);
            vertex(buffer, new Vector3f(b).add(c0), 0.0F, 1.0F, n, tint, light);
        }
    }

    private static void vertex(VertexConsumer buffer, Vector3f p, float u, float v, Vector3f normal, int tint, int light) {
        buffer.addVertex(p.x, p.y, p.z).setColor(tint).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(normal.x, normal.y, normal.z);
    }
}
