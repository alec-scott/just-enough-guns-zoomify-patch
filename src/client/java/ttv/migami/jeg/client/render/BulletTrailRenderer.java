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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import ttv.migami.jeg.client.render.gun.GunMuzzleTracker;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.item.GunItem;
import ttv.migami.jeg.network.BulletTrailPayload;

/**
 * 1.20.x-style bullet trail renderer.
 * Trails are maintained per-bullet and rendered as textured energy-swirl geometry.
 */
public final class BulletTrailRenderer {
    private static final Identifier TRAIL_TEXTURE = Identifier.fromNamespaceAndPath("jeg", "textures/effect/tracer.png");
    private static final double TRACER_MAX_LENGTH = 16.0D;
    private static final double TRACER_LENGTH_TICKS = 1.6D;
    private static final double TRACER_NEAR_FADE_START = 1.0D;
    private static final double TRACER_NEAR_FADE_LENGTH = 3.0D;
    private static final double TRACER_WIDTH_PER_BLOCK = 0.0024D;
    private static final double TRACER_MUZZLE_FORWARD = 1.5D;
    private static final double TRACER_MUZZLE_RIGHT = 0.2D;
    private static final double TRACER_MUZZLE_DOWN = 0.42D;
    private static final double TRACER_CONVERGE_DISTANCE = 40.0D;
    /** Bullets fired by hand start this close to the shooter's eye (GunItem#fireAt starts them 0.35 blocks out). */
    private static final double MAX_EYE_ORIGIN_DISTANCE = 1.0D;
    /** How many ticks of a vehicle's travel a rider's round may start away from their eye (interpolation and network lag). */
    private static final double RIDER_LAG_TICKS = 8.0D;
    private static final double TRACER_MIN_HALF_WIDTH = 0.016D;
    /** A hand-held gun's tracer is this thin where it leaves a barrel right in front of the eye. */
    private static final double TRACER_MUZZLE_MIN_HALF_WIDTH = 0.003D;
    /**
     * Where along the sprite's fade (0 clear, 1 solid) a hand-held tracer's tail is drawn while it is still joined to the
     * barrel: solid enough to be seen leaving the gun.
     */
    private static final float TRACER_ATTACHED_TAIL_U = 0.75F;
    private static final double VEHICLE_TRACER_WIDTH_SCALE = 2.4D;
    private static final double VEHICLE_TRACER_LENGTH_SCALE = 1.6D;
    private static final double TRACER_HEAD_SIZE_PER_BLOCK = 0.006D;
    private static final double TRACER_HEAD_MIN_SIZE = 0.05D;
    private static final double TRACER_HEAD_MAX_SIZE = 0.9D;
    private static final double TRACER_MAX_HALF_WIDTH = 0.14D;
    private static final int TRACER_ALPHA = 255;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final Map<Integer, TrailState> TRAILS = new HashMap<>();
    private static final AtomicInteger SYNTHETIC_IDS = new AtomicInteger(-1);
    private static long lastRenderFrame = -1L;
    private static volatile Vec3 lastTracerStart;
    private static final int RECENT_TRACER_START_COUNT = 16;
    private static final java.util.ArrayDeque<Vec3> RECENT_TRACER_STARTS = new java.util.ArrayDeque<>();
    private static volatile Vec3 lastDrawnStart;
    private static int newestTrailId;
    private static volatile int impactsShown;

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
                state.vehicleRound = payload.vehicleRound();
                // A vehicle's own gun already fires from its muzzle; a hand-held gun fires from the eye and is moved onto
                // the gun, also for passengers shooting out of a helicopter.
                state.muzzleOffset = state.vehicleRound ? Vec3.ZERO : muzzleOffset(mc, payload.shooterId(), state.origin, payload.offhand());
                lastTracerStart = state.origin.add(state.muzzleOffset);
                RECENT_TRACER_STARTS.addLast(lastTracerStart);
                while (RECENT_TRACER_STARTS.size() > RECENT_TRACER_START_COUNT) {
                    RECENT_TRACER_STARTS.removeFirst();
                }
                if (state.muzzleOffset != Vec3.ZERO) {
                    state.convergeDistance = impactDistance(mc, payload.shooterId(), state.origin, payload.motions()[i]);
                    // Fired from a moving vehicle the round carries its speed: the tracer moves on with it from the gun.
                    Entity shooter = mc.level.getEntity(payload.shooterId());
                    Entity carrier = shooter != null ? shooter.getRootVehicle() : null;
                    if (carrier != null && carrier != shooter) {
                        state.carrier = carrier;
                        state.carrierStart = carrier.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
                    }
                }
                newestTrailId = entityId;
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
            if (trail.ended) {
                // It was drawn reaching the impact for a tick; now it is gone.
                iterator.remove();
                continue;
            }
            Vec3 next = trail.position.add(trail.motion);
            // The server only tells us where the bullet started; the tracer runs on to whatever it meets (a wall or
            // someone in the way) and is drawn reaching that point for one more tick before it disappears.
            Vec3 impact = impactPoint(mc, trail, next);
            if (impact != null) {
                trail.position = impact;
                trail.ended = true;
                impactsShown++;
                if (trail.vehicleRound) {
                    spawnImpactPuff(mc, impact, trail.motion);
                }
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
        Vec3 headWorld = trail.ended ? trail.position : trail.position.add(trail.motion.scale(partialTick));
        Vec3 head = headWorld.subtract(view);
        Vec3 origin = trail.origin.subtract(view);
        double travelled = trail.origin.distanceTo(headWorld);
        double speed = trail.motion.length();
        if (speed < 1.0E-4D || travelled < 1.0E-3D) {
            return;
        }
        Vec3 back = trail.motion.scale(-1.0D / speed);
        // A mounted gun's rounds are heavier: a longer, thicker, hotter streak than a rifle's.
        double maxLength = trail.vehicleRound ? TRACER_MAX_LENGTH * VEHICLE_TRACER_LENGTH_SCALE : TRACER_MAX_LENGTH;
        double fullLength = Math.min(maxLength, speed * TRACER_LENGTH_TICKS);
        double length = Math.min(travelled, fullLength);
        Vec3 tail = travelled <= length ? origin : head.add(back.scale(length));
        // The sprite fades from clear at its tail to solid at its head. A hand-held gun's tracer is drawn solid back to the
        // barrel while it is still joined to the gun, and takes on its tapering tail only as it pulls away, so it is seen
        // leaving the gun rather than appearing as a dash ahead of it. A vehicle gun's rounds keep their full taper.
        float tailU = trail.vehicleRound ? 0.0F
                : (float) (TRACER_ATTACHED_TAIL_U * Mth.clamp(1.0D - (travelled - length) / Math.max(1.0E-3D, fullLength), 0.0D, 1.0D));
        // A bullet leaves from the eye along the line of sight, which from the shooter's seat would be a single dot on the
        // crosshair and to everyone else would come out of their head. Tracers start at the gun's muzzle instead and close
        // in on the true path by the point the bullet hits, so they still end where it lands.
        // A passenger's round also keeps the vehicle's own movement since it was fired, as a real one would, so the stream
        // stays on the gun of a flying helicopter instead of being left behind it.
        Vec3 offset = trail.muzzleOffset;
        if (trail.carrier != null && !trail.carrier.isRemoved()) {
            offset = offset.add(trail.carrier.getPosition(partialTick).subtract(trail.carrierStart));
        }
        head = head.add(offset.scale(tracerConvergence(travelled, trail.convergeDistance)));
        tail = tail.add(offset.scale(tracerConvergence(travelled - length, trail.convergeDistance)));
        if (trail.entityId == newestTrailId) {
            lastDrawnStart = travelled <= fullLength ? tail.add(view) : null;
        }

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
        double widthScale = trail.vehicleRound ? VEHICLE_TRACER_WIDTH_SCALE : 1.0D;
        Vec3 headSide = side.scale(tracerHalfWidth(headDistance) * widthScale);
        // Right at a hand-held barrel the usual minimum width would be a thick bar across the view; it narrows into the gun.
        Vec3 tailSide = side.scale((trail.vehicleRound ? tracerHalfWidth(tail.length()) : muzzleEndHalfWidth(tail.length())) * widthScale);

        // Mostly a hot yellow-orange so it reads against a blue sky and in the dark, with a hint of the bullet's own colour.
        int red = trail.vehicleRound ? 255 : warmTint((trail.color >> 16) & 0xFF, 255);
        int green = trail.vehicleRound ? 222 : warmTint((trail.color >> 8) & 0xFF, 205);
        int blue = trail.vehicleRound ? 130 : warmTint(trail.color & 0xFF, 90);
        int alpha = (int) (TRACER_ALPHA * nearFade);

        Matrix4f matrix = poseStack.last().pose();
        vertex(consumer, matrix, red, green, blue, alpha, tail.subtract(tailSide), tailU, 1.0F);
        vertex(consumer, matrix, red, green, blue, alpha, head.subtract(headSide), 1.0F, 1.0F);
        vertex(consumer, matrix, red, green, blue, alpha, head.add(headSide), 1.0F, 0.0F);
        vertex(consumer, matrix, red, green, blue, alpha, tail.add(tailSide), tailU, 0.0F);

        if (trail.vehicleRound) {
            // Seen down the barrel a round flies straight away from the eye and its streak shrinks to nothing; a bright
            // head keeps each one visible all the way to where it lands.
            Vec3 toCamera = head.scale(-1.0D / Math.max(1.0E-4D, headDistance));
            Vec3 right = toCamera.cross(new Vec3(0.0D, 1.0D, 0.0D));
            right = right.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
            Vec3 up = right.cross(toCamera).normalize();
            double size = Mth.clamp(headDistance * TRACER_HEAD_SIZE_PER_BLOCK, TRACER_HEAD_MIN_SIZE, TRACER_HEAD_MAX_SIZE);
            Vec3 r = right.scale(size);
            Vec3 u = up.scale(size);
            vertex(consumer, matrix, red, green, blue, alpha, head.subtract(r).subtract(u), 0.45F, 0.6F);
            vertex(consumer, matrix, red, green, blue, alpha, head.add(r).subtract(u), 0.55F, 0.6F);
            vertex(consumer, matrix, red, green, blue, alpha, head.add(r).add(u), 0.55F, 0.4F);
            vertex(consumer, matrix, red, green, blue, alpha, head.subtract(r).add(u), 0.45F, 0.4F);
        }
    }

    /** Where this tick of flight is stopped by a wall or by someone other than the shooter and their vehicle, if anywhere. */
    private static Vec3 impactPoint(Minecraft mc, TrailState trail, Vec3 next) {
        BlockHitResult block = mc.level.clip(new ClipContext(trail.position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        Vec3 end = block.getType() == HitResult.Type.MISS ? null : block.getLocation();
        Vec3 limit = end != null ? end : next;
        Entity shooter = mc.level.getEntity(trail.shooterId);
        Entity shooterVehicle = shooter != null ? shooter.getVehicle() : null;
        double best = end != null ? trail.position.distanceToSqr(end) : Double.MAX_VALUE;
        for (Entity entity : mc.level.getEntities((Entity) null, new AABB(trail.position, limit).inflate(1.0D),
                e -> e != shooter && e != shooterVehicle && e.isPickable() && !(e instanceof BulletEntity) && !e.isSpectator())) {
            var hit = entity.getBoundingBox().inflate(0.1D).clip(trail.position, limit);
            if (hit.isPresent() && trail.position.distanceToSqr(hit.get()) < best) {
                best = trail.position.distanceToSqr(hit.get());
                end = hit.get();
            }
        }
        return end;
    }

    /** A burst of dust and smoke where a vehicle gun's round lands, drawn however far away it is. */
    private static void spawnImpactPuff(Minecraft mc, Vec3 at, Vec3 motion) {
        Vec3 forward = motion.lengthSqr() < 1.0E-6D ? Vec3.ZERO : motion.normalize();
        Vec3 back = forward.scale(-0.12D);
        BlockState ground = mc.level.getBlockState(BlockPos.containing(at.add(forward.scale(0.05D))));
        RandomSource random = mc.level.getRandom();
        for (int i = 0; i < 10; i++) {
            double dx = back.x + (random.nextDouble() - 0.5D) * 0.2D;
            double dy = Math.abs(back.y) + random.nextDouble() * 0.15D;
            double dz = back.z + (random.nextDouble() - 0.5D) * 0.2D;
            if (!ground.isAir()) {
                mc.level.addAlwaysVisibleParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground), true, at.x, at.y, at.z, dx, dy, dz);
            }
            if (i < 3) {
                mc.level.addAlwaysVisibleParticle(ParticleTypes.POOF, true, at.x, at.y, at.z, dx * 0.3D, dy * 0.3D, dz * 0.3D);
            }
        }
        for (int i = 0; i < 3; i++) {
            mc.level.addAlwaysVisibleParticle(ParticleTypes.CRIT, true, at.x, at.y, at.z,
                    back.x * 3.0D + (random.nextDouble() - 0.5D) * 0.4D, 0.15D + random.nextDouble() * 0.2D, back.z * 3.0D + (random.nextDouble() - 0.5D) * 0.4D);
        }
    }

    /**
     * How far the shooter's gun muzzle sits from where their bullet actually started: the muzzle as last drawn, or an
     * estimate when the gun has not been drawn. Zero for shots that already leave a muzzle, like a vehicle's guns.
     */
    private static Vec3 muzzleOffset(Minecraft mc, int shooterId, Vec3 origin, boolean offhand) {
        if (mc.level == null || !(mc.level.getEntity(shooterId) instanceof LivingEntity shooter)
                || !(shooter.getItemInHand(offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND).getItem() instanceof GunItem)) {
            return Vec3.ZERO;
        }
        // The off hand's gun (a second minigun) is on the other side from the main one.
        HumanoidArm arm = offhand ? shooter.getMainArm().getOpposite() : shooter.getMainArm();
        // Only shots fired from the eye line; anything else was already given its own muzzle. A passenger in a moving
        // vehicle is drawn a few ticks behind where the server fired from, so their round starts that much further away.
        double allowed = MAX_EYE_ORIGIN_DISTANCE + RIDER_LAG_TICKS * riderSpeed(shooter);
        if (shooter.getEyePosition().distanceToSqr(origin) > allowed * allowed) {
            return Vec3.ZERO;
        }
        boolean firstPersonView = shooter == mc.player && mc.options.getCameraType().isFirstPerson();
        Vec3 muzzle = GunMuzzleTracker.muzzle(shooterId, firstPersonView, arm);
        if (muzzle != null) {
            return muzzle.subtract(origin);
        }
        return firstPersonView ? firstPersonMuzzleOffset(mc, origin, arm) : thirdPersonMuzzleEstimate(shooter, arm).subtract(origin);
    }

    /** How fast (blocks a tick) whatever the shooter rides is moving here; 0 on foot. */
    private static double riderSpeed(LivingEntity shooter) {
        Entity vehicle = shooter.getRootVehicle();
        if (vehicle == shooter) {
            return 0.0D;
        }
        double moved = vehicle.position().distanceTo(new Vec3(vehicle.xo, vehicle.yo, vehicle.zo));
        return Math.max(moved, vehicle.getDeltaMovement().length());
    }

    /** Roughly where a gun held at the shoulder ends, for a shooter whose gun has not been drawn this frame. */
    private static Vec3 thirdPersonMuzzleEstimate(LivingEntity shooter, HumanoidArm arm) {
        Vec3 look = shooter.getLookAngle();
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        right = right.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
        if (arm == HumanoidArm.LEFT) {
            right = right.scale(-1.0D);
        }
        return shooter.getEyePosition().add(look.scale(0.9D)).add(right.scale(0.32D)).add(0.0D, -0.3D, 0.0D);
    }

    /** How far the bullet flies before something stops it, so its tracer can meet the true path right there. */
    private static double impactDistance(Minecraft mc, int shooterId, Vec3 origin, Vec3 motion) {
        double speed = motion.length();
        if (speed < 1.0E-4D) {
            return TRACER_CONVERGE_DISTANCE;
        }
        Vec3 end = origin.add(motion.scale(TRACER_CONVERGE_DISTANCE / speed));
        BlockHitResult block = mc.level.clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        double distance = block.getType() == HitResult.Type.MISS ? TRACER_CONVERGE_DISTANCE : origin.distanceTo(block.getLocation());
        Vec3 limit = origin.add(motion.scale(distance / speed));
        AABB sweep = new AABB(origin, limit).inflate(1.0D);
        Entity shooter = mc.level.getEntity(shooterId);
        Entity shooterVehicle = shooter != null ? shooter.getVehicle() : null;
        for (Entity entity : mc.level.getEntities((Entity) null, sweep,
                e -> e.getId() != shooterId && e != shooterVehicle && e.isPickable() && !(e instanceof BulletEntity))) {
            var hit = entity.getBoundingBox().inflate(0.3D).clip(origin, limit);
            if (hit.isPresent()) {
                distance = Math.min(distance, origin.distanceTo(hit.get()));
            }
        }
        return Math.max(1.0D, distance);
    }

    /** Where a held gun's muzzle roughly sits in view, relative to where the bullet actually started. */
    private static Vec3 firstPersonMuzzleOffset(Minecraft mc, Vec3 origin, HumanoidArm arm) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 look = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        right = right.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : right.normalize();
        if (arm == HumanoidArm.LEFT) {
            right = right.scale(-1.0D);
        }
        Vec3 up = right.cross(look);
        Vec3 muzzle = camera.position()
                .add(look.scale(TRACER_MUZZLE_FORWARD))
                .add(right.scale(TRACER_MUZZLE_RIGHT))
                .add(up.scale(-TRACER_MUZZLE_DOWN));
        return muzzle.subtract(origin);
    }

    private static double tracerConvergence(double travelled, double convergeDistance) {
        return Mth.clamp(1.0D - travelled / convergeDistance, 0.0D, 1.0D);
    }

    private static double tracerHalfWidth(double distance) {
        return Mth.clamp(distance * TRACER_WIDTH_PER_BLOCK, TRACER_MIN_HALF_WIDTH, TRACER_MAX_HALF_WIDTH);
    }

    /** Like {@link #tracerHalfWidth}, but narrowing into a barrel right in front of the eye instead of holding its minimum. */
    private static double muzzleEndHalfWidth(double distance) {
        double minimum = Mth.clamp(distance * TRACER_MIN_HALF_WIDTH / 4.0D, TRACER_MUZZLE_MIN_HALF_WIDTH, TRACER_MIN_HALF_WIDTH);
        return Mth.clamp(distance * TRACER_WIDTH_PER_BLOCK, minimum, TRACER_MAX_HALF_WIDTH);
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

    /** How many tracers have been drawn reaching what stopped them, for checks. */
    public static int impactsShown() {
        return impactsShown;
    }

    /** Where the newest network tracer starts drawing (the shooter's muzzle), for checks. */
    /** Where the newest tracer was last drawn starting, while it is still joined to the gun, for checks. */
    public static Vec3 lastDrawnStart() {
        return lastDrawnStart;
    }

    public static Vec3 lastTracerStart() {
        return lastTracerStart;
    }

    /** Where the last few network tracers started (their shooters' muzzles), oldest first, for checks. */
    public static java.util.List<Vec3> recentTracerStarts() {
        return java.util.List.copyOf(RECENT_TRACER_STARTS);
    }

    private static final class TrailState {
        final int entityId;
        Vec3 origin = Vec3.ZERO;
        Vec3 muzzleOffset = Vec3.ZERO;
        /** The vehicle a passenger fired from, and where it was then: the tracer moves on with it. */
        Entity carrier;
        Vec3 carrierStart = Vec3.ZERO;
        double convergeDistance = TRACER_CONVERGE_DISTANCE;
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
        /** Reached what stopped it: drawn up to that point for one tick, then removed. */
        boolean ended;
        /** Fired from a vehicle's gun: drawn with a bright head and leaves a puff where it lands. */
        boolean vehicleRound;

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
