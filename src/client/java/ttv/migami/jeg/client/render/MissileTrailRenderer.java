package ttv.migami.jeg.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.network.MissileTrackPayload;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

/**
 * Missiles and rockets in flight: a white-hot motor glow and flame out of the nozzle, and a thick smoke trail laid down
 * behind them that billows out, drifts on the wind and lingers long after the missile has gone. A puff of backblast marks
 * the launch.
 */
public final class MissileTrailRenderer {
    private static final int FULL_BRIGHT = 0x00F000F0;
    /** Smoke laid per block flown; keeps the trail continuous however fast the missile goes. */
    private static final double PUFFS_PER_BLOCK = 2.6D;
    private static final int MAX_PUFFS = 4000;
    private static final float HEAVY_MOTOR = 1.6F;
    /** A far missile no longer heard of for this long has gone off or left range. */
    private static final int GHOST_TIMEOUT_TICKS = 3;
    /** Least size of the motor's halo and core, as a share of their distance from the camera (about 8 and 3 pixels). */
    private static final float MIN_HALO_SPREAD = 0.006F;
    private static final float MIN_CORE_SPREAD = 0.0025F;
    /** Least size of the solid flame spot, as a share of its distance (its bright middle about 8 pixels on 1080p). */
    private static final float MIN_SPOT_SPREAD = 0.008F;
    /** Share of its distance the flame spot is drawn nearer the camera, in front of the trail behind it. */
    private static final double SPOT_LIFT = 0.15D;
    private static final Map<Integer, Motor> MOTORS = new HashMap<>();
    private static final List<Puff> PUFFS = new ArrayList<>();
    private static final Map<Integer, Ghost> GHOSTS = new HashMap<>();

    private static int motorsDrawn;

    private MissileTrailRenderer() {}

    /** How many motors the last frame with any drew, for checks. */
    public static int motorsDrawn() {
        return motorsDrawn;
    }

    /** A far missile's latest position from the server (see MissileTrackPayload). */
    public static void track(MissileTrackPayload payload) {
        Ghost ghost = GHOSTS.get(payload.entityId());
        if (ghost == null) {
            ghost = new Ghost(payload.position(), payload.heavy() ? HEAVY_MOTOR : 1.0F, payload.age());
            GHOSTS.put(payload.entityId(), ghost);
        }
        ghost.latest = payload.position();
        ghost.motion = payload.motion();
        ghost.age = payload.age();
        ghost.silentTicks = 0;
    }

    /** Every client tick: follow each missile and rocket in the world and lay smoke along the path it just flew. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        RandomSource random = mc.level.getRandom();
        for (Motor motor : MOTORS.values()) {
            motor.seen = false;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            float size = motorSize(entity);
            if (size <= 0.0F || !entity.isAlive()) {
                continue;
            }
            follow(entity.getId(), entity.position(), entity.getDeltaMovement(), size, true, random);
        }
        // Missiles beyond entity range, followed from the server's track updates. Once one is in range its entity
        // takes over the same motor, so the trail carries on unbroken either way.
        Iterator<Map.Entry<Integer, Ghost>> ghosts = GHOSTS.entrySet().iterator();
        while (ghosts.hasNext()) {
            Map.Entry<Integer, Ghost> entry = ghosts.next();
            Ghost ghost = entry.getValue();
            if (++ghost.silentTicks > GHOST_TIMEOUT_TICKS) {
                ghosts.remove();
                continue;
            }
            ghost.previous = ghost.current;
            ghost.current = ghost.latest;
            if (mc.level.getEntity(entry.getKey()) == null) {
                follow(entry.getKey(), ghost.current, ghost.motion, ghost.size, ghost.firstAge <= 2, random);
            }
        }
        MOTORS.values().removeIf(motor -> !motor.seen);
        Iterator<Puff> iterator = PUFFS.iterator();
        while (iterator.hasNext()) {
            Puff puff = iterator.next();
            puff.age++;
            if (puff.age > puff.life) {
                iterator.remove();
            }
        }
        while (PUFFS.size() > MAX_PUFFS) {
            PUFFS.remove(0);
        }
    }

    /** Lays smoke along the path a motor flew since last tick; {@code launch} puffs a backblast where it is first seen. */
    private static void follow(int id, Vec3 now, Vec3 motion, float size, boolean launch, RandomSource random) {
        Motor motor = MOTORS.get(id);
        if (motor == null) {
            motor = new Motor(now, size);
            MOTORS.put(id, motor);
            if (launch) {
                // Backblast: a burst of grey smoke at the rail as the motor lights.
                for (int i = 0; i < 10; i++) {
                    Vec3 drift = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5D, random.nextGaussian()).scale(0.05D * size);
                    PUFFS.add(new Puff(now, drift, 0.6F * size, 2.2F * size, 70 + random.nextInt(40), random, 0.62F));
                }
            }
        }
        motor.seen = true;
        motor.motion = motion;
        double distance = motor.last.distanceTo(now);
        int count = Math.min(60, (int) Math.ceil(distance * PUFFS_PER_BLOCK / Math.max(0.6F, size)));
        for (int i = 0; i < count; i++) {
            Vec3 at = motor.last.lerp(now, (i + random.nextDouble()) / count);
            Vec3 drift = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).scale(0.012D * size);
            int life = (int) ((90 + random.nextInt(70)) * Math.sqrt(size));
            PUFFS.add(new Puff(at, drift, 0.5F * size, 1.9F * size, life, random, 0.9F));
        }
        motor.last = now;
    }

    /** How many missiles are being followed from track updates alone, beyond entity range, for checks. */
    public static int farMissiles() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0;
        }
        int count = 0;
        for (Map.Entry<Integer, Ghost> entry : GHOSTS.entrySet()) {
            if (mc.level.getEntity(entry.getKey()) == null && MOTORS.containsKey(entry.getKey())) {
                count++;
            }
        }
        return count;
    }

    /** How big a motor this entity has, or 0 if it is not a missile or rocket. */
    private static float motorSize(Entity entity) {
        if (entity instanceof VehicleMissileEntity missile) {
            return VehicleMissileEntity.isHeavy(missile.missileWeaponId()) ? HEAVY_MOTOR : 1.0F;
        }
        if (entity instanceof BulletEntity bullet) {
            String gun = bullet.getGunId();
            return gun.contains("vehicle_") && gun.contains("rocket") ? 0.7F : 0.0F;
        }
        return 0.0F;
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (PUFFS.isEmpty() && MOTORS.isEmpty())) {
            return;
        }
        MissileBlastRenderer.ensureTextures(mc);
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 view = camera.position();
        Vector3f right = camera.rotation().transform(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f up = camera.rotation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Matrix4f matrix = poseStack.last().pose();

        if (!PUFFS.isEmpty()) {
            float daylight = Mth.clamp(mc.level.getMaxLocalRawBrightness(BlockPos.containing(view)) / 15.0F, 0.3F, 1.0F);
            List<Puff> sorted = new ArrayList<>(PUFFS);
            sorted.sort(Comparator.comparingDouble((Puff puff) -> puff.position(partialTick).distanceToSqr(view)).reversed());
            VertexConsumer smoke = bufferSource.getBuffer(RenderTypes.entityTranslucent(MissileBlastRenderer.PUFF_TEXTURE));
            for (Puff puff : sorted) {
                float t = (puff.age + partialTick) / puff.life;
                float size = Mth.lerp(1.0F - (1.0F - t) * (1.0F - t), puff.startSize, puff.endSize);
                // Fresh smoke is lit orange by the motor; it cools to a bright grey-white that thins away.
                float warm = Math.max(0.0F, 1.0F - (puff.age + partialTick) / 6.0F);
                float grey = puff.shade * daylight;
                float alpha = 0.7F * Math.min(1.0F, (puff.age + partialTick) / 2.0F) * (1.0F - t) * (1.0F - t * 0.4F);
                Vec3 at = puff.position(partialTick).subtract(view);
                float far = FarDraw.scale(at);
                MissileBlastRenderer.billboard(smoke, matrix, at.scale(far), right, up, size * far,
                        puff.rotation + puff.spin * (puff.age + partialTick),
                        Math.min(1.0F, grey + warm * 0.4F), grey + warm * 0.12F, grey * 0.97F, alpha, FULL_BRIGHT, puff.variant);
            }
        }

        if (!MOTORS.isEmpty()) {
            List<MotorView> views = new ArrayList<>();
            for (Map.Entry<Integer, Motor> entry : MOTORS.entrySet()) {
                Entity entity = mc.level.getEntity(entry.getKey());
                Ghost ghost = entity == null ? GHOSTS.get(entry.getKey()) : null;
                if (entity == null && ghost == null) {
                    continue;
                }
                Motor motor = entry.getValue();
                Vec3 motion = motor.motion;
                if (motion.lengthSqr() < 1.0E-6D) {
                    continue;
                }
                Vec3 forward = motion.normalize();
                Vec3 position = entity != null ? entity.getPosition(partialTick) : ghost.previous.lerp(ghost.current, partialTick);
                Vec3 nozzle = position.subtract(forward.scale(0.85D * motor.size));
                int age = entity != null ? entity.tickCount : ghost.age;
                float flicker = 0.85F + 0.15F * Mth.sin((age + partialTick) * 2.7F + entry.getKey());
                Vec3 relative = nozzle.subtract(view);
                float far = FarDraw.scale(relative);
                views.add(new MotorView(motor, nozzle, relative.scale(far), forward, (float) relative.length(), far, flicker, ghost != null));
            }

            // A solid spot of flame first: added light alone washes out against a pale daytime sky, so a far missile
            // vanished in daylight even where it was drawn.
            motorsDrawn = views.size();
            VertexConsumer spot = bufferSource.getBuffer(RenderTypes.entityTranslucent(MissileBlastRenderer.softTexture()));
            for (MotorView motor : views) {
                float size = Math.max(0.8F * motor.motor.size, motor.distance * MIN_SPOT_SPREAD);
                // Seen from behind, down the length of its own trail, the smoke just behind the missile lies over it on
                // screen and is nearer: it buried the spot. The spot is drawn part of the way towards the camera instead,
                // shrunk to match, so it covers the same pixels but sits in front of that smoke.
                double lift = Math.min(Math.max(Math.max(2.0D, size * 3.0D), motor.distance * SPOT_LIFT), motor.distance * 0.5D);
                float shrink = motor.distance > 1.0E-3F ? (float) (1.0D - lift / motor.distance) : 1.0F;
                Vec3 at = motor.at.scale(shrink);
                float drawn = size * motor.flicker * motor.far * shrink;
                // An orange glow round a hot yellow-white centre: the sprite is soft, so only its middle reads at range.
                MissileBlastRenderer.dot(spot, matrix, at, right, up, drawn, 1.0F, 0.62F, 0.25F, 0.95F);
                MissileBlastRenderer.dot(spot, matrix, at, right, up, drawn * 0.5F, 1.0F, 0.95F, 0.75F, 1.0F);
            }

            VertexConsumer glow = bufferSource.getBuffer(RenderTypes.lightning());
            for (MotorView motor : views) {
                // Halo, hot core, and a flame cone streaming back from the nozzle. Seen from far beyond entity range
                // there is no body to draw, so the halo is a little larger, and both keep a least size on screen: a
                // missile hundreds of blocks out stays a bright point of light all the way to its target.
                float halo = Math.max((motor.ghost ? 3.0F : 2.2F) * motor.motor.size, motor.distance * MIN_HALO_SPREAD);
                float core = Math.max(0.45F * motor.motor.size, motor.distance * MIN_CORE_SPREAD);
                float scale = motor.flicker * motor.far;
                MissileBlastRenderer.disc(glow, matrix, motor.at, right, up, halo * scale, 1.0F, 0.55F, 0.2F, 0.6F);
                MissileBlastRenderer.disc(glow, matrix, motor.at, right, up, core * scale, 1.0F, 0.95F, 0.8F, 1.0F);
                flame(glow, matrix, motor.at, motor.forward.scale(-1.0D), view, motor.nozzle,
                        2.2F * motor.motor.size * scale, 0.22F * motor.motor.size * motor.far);
            }
        }
    }

    /** One motor as it is drawn this frame: {@code at} is where, relative to the camera, after any pulling in. */
    private record MotorView(Motor motor, Vec3 nozzle, Vec3 at, Vec3 forward, float distance, float far, float flicker, boolean ghost) {}

    /** A tapering flame streaming back from the nozzle, turned to face the camera about its own axis. */
    private static void flame(VertexConsumer consumer, Matrix4f matrix, Vec3 at, Vec3 back, Vec3 view, Vec3 nozzleWorld, float length, float width) {
        Vec3 toCamera = view.subtract(nozzleWorld).normalize();
        Vec3 side = back.cross(toCamera);
        if (side.lengthSqr() < 1.0E-6D) {
            return;
        }
        side = side.normalize().scale(width);
        Vec3 end = at.add(back.scale(length));
        vertex(consumer, matrix, at.subtract(side), 1.0F, 0.85F, 0.55F, 0.95F);
        vertex(consumer, matrix, at.add(side), 1.0F, 0.85F, 0.55F, 0.95F);
        vertex(consumer, matrix, end.add(side.scale(0.3D)), 1.0F, 0.35F, 0.08F, 0.0F);
        vertex(consumer, matrix, end.subtract(side.scale(0.3D)), 1.0F, 0.35F, 0.08F, 0.0F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 at, float r, float g, float b, float a) {
        consumer.addVertex(matrix, (float) at.x, (float) at.y, (float) at.z).setColor(r, g, b, a);
    }

    public static void clear() {
        MOTORS.clear();
        PUFFS.clear();
        GHOSTS.clear();
    }

    /** A missile known only from track updates; positions step once a client tick so it can be drawn in between. */
    private static final class Ghost {
        final float size;
        final int firstAge;
        Vec3 latest;
        Vec3 current;
        Vec3 previous;
        Vec3 motion = Vec3.ZERO;
        int age;
        int silentTicks;

        Ghost(Vec3 position, float size, int firstAge) {
            this.latest = position;
            this.current = position;
            this.previous = position;
            this.size = size;
            this.firstAge = firstAge;
            this.age = firstAge;
        }
    }

    private static final class Motor {
        Vec3 last;
        Vec3 motion = Vec3.ZERO;
        final float size;
        boolean seen;

        Motor(Vec3 last, float size) {
            this.last = last;
            this.size = size;
        }
    }

    private static final class Puff {
        final Vec3 origin;
        final Vec3 drift;
        final float startSize;
        final float endSize;
        final int life;
        final float rotation;
        final float spin;
        final float shade;
        final int variant;
        int age;

        Puff(Vec3 origin, Vec3 drift, float startSize, float endSize, int life, RandomSource random, float shade) {
            this.origin = origin;
            this.drift = drift;
            this.startSize = startSize;
            this.endSize = endSize * (0.8F + random.nextFloat() * 0.4F);
            this.life = life;
            this.rotation = random.nextFloat() * Mth.TWO_PI;
            this.spin = (random.nextFloat() - 0.5F) * 0.015F;
            this.shade = shade * (0.88F + random.nextFloat() * 0.12F);
            this.variant = random.nextInt(4);
        }

        /** Spreads out from the path, slowing as it goes, then rises and leans with a light wind. */
        Vec3 position(float partialTick) {
            double t = this.age + partialTick;
            double spread = 18.0D * (1.0D - Math.exp(-t / 18.0D));
            return this.origin.add(this.drift.scale(spread)).add(0.006D * t, 0.004D * t, 0.003D * t);
        }
    }
}
