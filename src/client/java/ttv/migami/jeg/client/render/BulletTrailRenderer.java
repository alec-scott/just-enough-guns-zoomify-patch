package ttv.migami.jeg.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import ttv.migami.jeg.network.BulletTrailPayload;

/**
 * 1.20.x-style bullet trail renderer.
 * Trails are maintained per-bullet and rendered as textured energy-swirl geometry.
 */
public final class BulletTrailRenderer {
    private static final Identifier TRAIL_TEXTURE = Identifier.fromNamespaceAndPath("jeg", "textures/effect/tracer.png");
    private static final double TRACER_MAX_LENGTH = 16.0D;
    private static final double TRACER_LENGTH_TICKS = 1.6D;
    private static final double TRACER_NEAR_FADE_START = 2.0D;
    private static final double TRACER_NEAR_FADE_LENGTH = 6.0D;
    private static final double TRACER_WIDTH_PER_BLOCK = 0.0024D;
    private static final double TRACER_MUZZLE_FORWARD = 1.5D;
    private static final double TRACER_MUZZLE_RIGHT = 0.2D;
    private static final double TRACER_MUZZLE_DOWN = 0.42D;
    private static final double TRACER_CONVERGE_DISTANCE = 40.0D;
    private static final double TRACER_MIN_HALF_WIDTH = 0.016D;
    private static final double TRACER_MAX_HALF_WIDTH = 0.14D;
    private static final int TRACER_ALPHA = 255;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final Map<Integer, TrailState> TRAILS = new HashMap<>();
    private static final AtomicInteger SYNTHETIC_IDS = new AtomicInteger(-1);
    private static long lastRenderFrame = -1L;

    private BulletTrailRenderer() {}

    public static void upsertLegacyTrail(BulletTrailPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;
        int payloadColor = payload.color();
        int count = Math.min(
                payload.entityIds().length,
                Math.min(payload.positions().length, payload.motions().length)
        );
        for (int i = 0; i < count; i++) {
            int entityId = payload.entityIds()[i];
            TrailState state = TRAILS.get(entityId);
            if (state == null) {
                state = new TrailState(entityId);
                TRAILS.put(entityId, state);
                state.origin = payload.positions()[i];
                if (mc.player != null && payload.shooterId() == mc.player.getId() && !mc.player.isPassenger() && mc.options.getCameraType().isFirstPerson()) {
                    state.muzzleOffset = firstPersonMuzzleOffset(mc, state.origin);
                }
            }

            state.position = payload.positions()[i];
            state.motion = payload.motions()[i];
            state.color = payloadColor;
            state.size = payload.size();
            state.maxAge = Math.max(2, payload.life());
            state.gravity = payload.gravity();
            state.shooterId = payload.shooterId();
            state.trailVisible = payload.trailVisible();
            state.lastUpdateTick = gameTime;
            state.updateYawPitch();
            state.age = 0;
        }
    }

    /**
     * Compatibility fallback for local-only trail calls.
     */
    public static void addInstantTrail(Vec3 start, Vec3 end, int color, float size) {
        Vec3 motion = end.subtract(start);
        if (motion.lengthSqr() < 1.0E-6) {
            return;
        }
        int id = SYNTHETIC_IDS.getAndDecrement();
        TrailState state = new TrailState(id);
        state.origin = start;
        state.position = start;
        state.motion = motion;
        state.color = color;
        state.size = size;
        state.maxAge = 3;
        state.gravity = 0.0D;
        state.shooterId = -1;
        state.trailVisible = true;
        state.lastUpdateTick = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        state.updateYawPitch();
        TRAILS.put(id, state);
    }

    public static void updateDynamicTrail(int entityId, Vec3 currentPos, int color, float size) {
        TrailState state = TRAILS.get(entityId);
        if (state == null) {
            return;
        }
        state.position = currentPos;
        state.color = color;
        state.size = size;
        state.lastUpdateTick = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : state.lastUpdateTick;
    }

    public static void removeDynamicTrail(int entityId) {
        TRAILS.remove(entityId);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            TRAILS.clear();
            return;
        }

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
        Iterator<TrailState> iterator = TRAILS.values().iterator();
        while (iterator.hasNext()) {
            TrailState trail = iterator.next();
            trail.age++;
            Vec3 next = trail.position.add(trail.motion);
            // The server only tells us where the bullet started; stop the tracer where it meets a wall.
            BlockHitResult hit = mc.level.clip(new ClipContext(trail.position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.MISS) {
                iterator.remove();
                continue;
            }
            trail.position = next;
            if (trail.gravity != 0.0D) {
                trail.motion = trail.motion.add(0.0D, trail.gravity, 0.0D);
                trail.updateYawPitch();
            }

            if (cameraPos.distanceToSqr(trail.position) > 256.0D * 256.0D) {
                iterator.remove();
                continue;
            }

            if (trail.age >= trail.maxAge) {
                iterator.remove();
            }
        }
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        long currentFrame = Minecraft.getInstance().getFrameTimeNs();
        if (currentFrame == lastRenderFrame) {
            return;
        }
        lastRenderFrame = currentFrame;

        if (TRAILS.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Vec3 view = mc.gameRenderer.getMainCamera().position();
        VertexConsumer consumer = bufferSource.getBuffer(RenderTypes.entityTranslucentEmissive(TRAIL_TEXTURE));

        for (TrailState trail : TRAILS.values()) {
            renderTrail(trail, poseStack, consumer, view, partialTick);
        }
    }

    /**
     * A thin ribbon from the bullet back along its path, turned to face the camera and tapering away at the tail. It widens
     * with distance so it holds a steady thin line on screen. It never reaches further back than the point the bullet
     * started from, so it cannot pass through the camera; drawn from the shooter's own eye it used to fill the screen.
     */
    private static void renderTrail(TrailState trail, PoseStack poseStack, VertexConsumer consumer, Vec3 view, float partialTick) {
        Vec3 head = trail.position.add(trail.motion.scale(partialTick)).subtract(view);
        Vec3 origin = trail.origin.subtract(view);
        double travelled = trail.origin.distanceTo(trail.position.add(trail.motion.scale(partialTick)));
        double speed = trail.motion.length();
        if (speed < 1.0E-4D || travelled < 1.0E-3D) {
            return;
        }
        Vec3 back = trail.motion.scale(-1.0D / speed);
        double length = Math.min(travelled, Math.min(TRACER_MAX_LENGTH, speed * TRACER_LENGTH_TICKS));
        Vec3 tail = travelled <= length ? origin : head.add(back.scale(length));
        // A bullet leaves from the eye along the line of sight, which from the shooter's seat would be a single dot on the
        // crosshair. Their own tracers start at the gun's muzzle instead and close in on the true path further out.
        head = head.add(trail.muzzleOffset.scale(tracerConvergence(travelled)));
        tail = tail.add(trail.muzzleOffset.scale(tracerConvergence(travelled - length)));

        double headDistance = head.length();
        // The shooter's own end of the line fades out so it does not streak across the sights.
        float nearFade = (float) Mth.clamp((headDistance - TRACER_NEAR_FADE_START) / TRACER_NEAR_FADE_LENGTH, 0.0D, 1.0D);
        if (nearFade <= 0.0F) {
            return;
        }

        Vec3 along = head.subtract(tail);
        Vec3 side = along.cross(head.add(tail).scale(0.5D));
        if (side.lengthSqr() < 1.0E-8D) {
            return;
        }
        side = side.normalize();
        Vec3 headSide = side.scale(tracerHalfWidth(headDistance));
        Vec3 tailSide = side.scale(tracerHalfWidth(tail.length()));

        // Mostly a hot yellow-orange so it reads against a blue sky and in the dark, with a hint of the bullet's own colour.
        int red = warmTint((trail.color >> 16) & 0xFF, 255);
        int green = warmTint((trail.color >> 8) & 0xFF, 205);
        int blue = warmTint(trail.color & 0xFF, 90);
        int alpha = (int) (TRACER_ALPHA * nearFade);

        Matrix4f matrix = poseStack.last().pose();
        vertex(consumer, matrix, red, green, blue, alpha, tail.subtract(tailSide), 0.0F, 1.0F);
        vertex(consumer, matrix, red, green, blue, alpha, head.subtract(headSide), 1.0F, 1.0F);
        vertex(consumer, matrix, red, green, blue, alpha, head.add(headSide), 1.0F, 0.0F);
        vertex(consumer, matrix, red, green, blue, alpha, tail.add(tailSide), 0.0F, 0.0F);
    }

    /** Where a held gun's muzzle roughly sits in view, relative to where the bullet actually started. */
    private static Vec3 firstPersonMuzzleOffset(Minecraft mc, Vec3 origin) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 look = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        right = right.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
        Vec3 up = right.cross(look);
        Vec3 muzzle = camera.position()
                .add(look.scale(TRACER_MUZZLE_FORWARD))
                .add(right.scale(TRACER_MUZZLE_RIGHT))
                .add(up.scale(-TRACER_MUZZLE_DOWN));
        return muzzle.subtract(origin);
    }

    private static double tracerConvergence(double travelled) {
        return Mth.clamp(1.0D - travelled / TRACER_CONVERGE_DISTANCE, 0.0D, 1.0D);
    }

    private static double tracerHalfWidth(double distance) {
        return Mth.clamp(distance * TRACER_WIDTH_PER_BLOCK, TRACER_MIN_HALF_WIDTH, TRACER_MAX_HALF_WIDTH);
    }

    private static int warmTint(int colorChannel, int warmChannel) {
        return (colorChannel + warmChannel * 3) / 4;
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, int red, int green, int blue, int alpha,
                               Vec3 position, float u, float v) {
        consumer.addVertex(matrix, (float) position.x, (float) position.y, (float) position.z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    public static void clear() {
        TRAILS.clear();
    }

    private static final class TrailState {
        final int entityId;
        Vec3 origin = Vec3.ZERO;
        Vec3 muzzleOffset = Vec3.ZERO;
        Vec3 position = Vec3.ZERO;
        Vec3 motion = Vec3.ZERO;
        float yaw;
        float pitch;
        int age;
        int maxAge = 3;
        int color = 0xFFFFFF;
        float size = 0.05F;
        double gravity;
        int shooterId = -1;
        boolean trailVisible = true;
        long lastUpdateTick;

        TrailState(int entityId) {
            this.entityId = entityId;
        }

        void updateYawPitch() {
            float horizontalLength = Mth.sqrt((float) (this.motion.x * this.motion.x + this.motion.z * this.motion.z));
            this.yaw = (float) Math.toDegrees(Mth.atan2(this.motion.x, this.motion.z));
            this.pitch = (float) Math.toDegrees(Mth.atan2(this.motion.y, horizontalLength));
        }
    }
}
