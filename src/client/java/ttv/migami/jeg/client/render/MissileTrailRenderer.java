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
    private static final Map<Integer, Motor> MOTORS = new HashMap<>();
    private static final List<Puff> PUFFS = new ArrayList<>();

    private MissileTrailRenderer() {}

    /** Every client tick: follow each missile and rocket in the world and lay smoke along the path it just flew. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            MOTORS.clear();
            PUFFS.clear();
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
            Motor motor = MOTORS.get(entity.getId());
            Vec3 now = entity.position();
            if (motor == null) {
                motor = new Motor(now, size);
                MOTORS.put(entity.getId(), motor);
                // Backblast: a burst of grey smoke at the rail as the motor lights.
                for (int i = 0; i < 10; i++) {
                    Vec3 drift = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.5D, random.nextGaussian()).scale(0.05D * size);
                    PUFFS.add(new Puff(now, drift, 0.6F * size, 2.2F * size, 70 + random.nextInt(40), random, 0.62F));
                }
            }
            motor.seen = true;
            motor.motion = entity.getDeltaMovement();
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

    /** How big a motor this entity has, or 0 if it is not a missile or rocket. */
    private static float motorSize(Entity entity) {
        if (entity instanceof VehicleMissileEntity missile) {
            return missile.missileWeaponId().getPath().contains("kh39") ? 1.6F : 1.0F;
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
                MissileBlastRenderer.billboard(smoke, matrix, puff.position(partialTick).subtract(view), right, up, size,
                        puff.rotation + puff.spin * (puff.age + partialTick),
                        Math.min(1.0F, grey + warm * 0.4F), grey + warm * 0.12F, grey * 0.97F, alpha, FULL_BRIGHT, puff.variant);
            }
        }

        if (!MOTORS.isEmpty()) {
            VertexConsumer glow = bufferSource.getBuffer(RenderTypes.lightning());
            for (Map.Entry<Integer, Motor> entry : MOTORS.entrySet()) {
                Entity entity = mc.level.getEntity(entry.getKey());
                if (entity == null) {
                    continue;
                }
                Motor motor = entry.getValue();
                Vec3 motion = motor.motion;
                if (motion.lengthSqr() < 1.0E-6D) {
                    continue;
                }
                Vec3 forward = motion.normalize();
                Vec3 nozzle = entity.getPosition(partialTick).subtract(forward.scale(0.85D * motor.size));
                float flicker = 0.85F + 0.15F * Mth.sin((entity.tickCount + partialTick) * 2.7F + entity.getId());
                Vec3 at = nozzle.subtract(view);
                // Halo, hot core, and a flame cone streaming back from the nozzle.
                MissileBlastRenderer.disc(glow, matrix, at, right, up, 2.2F * motor.size * flicker, 1.0F, 0.55F, 0.2F, 0.6F);
                MissileBlastRenderer.disc(glow, matrix, at, right, up, 0.45F * motor.size * flicker, 1.0F, 0.95F, 0.8F, 1.0F);
                flame(glow, matrix, at, forward.scale(-1.0D), view, nozzle, 2.2F * motor.size * flicker, 0.22F * motor.size);
            }
        }
    }

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
