package ttv.migami.jeg.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * A rotary gun (the Mi-28's chin gun) heating up as it fires: its heat builds over a few seconds of sustained fire and
 * falls away again over about ten. The barrels themselves redden with it (drawn on the model by VehicleBarrelHeatLayer),
 * and thin wisps of smoke curl off the muzzle, most of all once the firing stops. Kept faint and small: it is there to
 * be noticed, not to get in the gunner's way.
 */
public final class GunBarrelHeatRenderer {
    private static final int FULL_BRIGHT = 0x00F000F0;
    /** About 3.5 seconds of sustained fire to full heat, 10 seconds to cool right down. */
    private static final float HEAT_PER_TICK = 1.0F / 70.0F;
    private static final float COOL_PER_TICK = 1.0F / 200.0F;
    /** Heat from which the barrel smokes. */
    private static final float SMOKE_FROM = 0.3F;
    private static final int MAX_WISPS = 200;
    private static final Map<Integer, Float> HEAT = new HashMap<>();
    private static final List<Wisp> WISPS = new ArrayList<>();

    private GunBarrelHeatRenderer() {}

    /** How hot (0 to 1) a vehicle's gun barrel is, for checks. */
    public static float heat(int vehicleId) {
        return HEAT.getOrDefault(vehicleId, 0.0F);
    }

    /** How many wisps of smoke are in the air, for checks. */
    public static int wisps() {
        return WISPS.size();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            HEAT.clear();
            WISPS.clear();
            return;
        }
        RandomSource random = mc.level.getRandom();
        Map<Integer, Float> next = new HashMap<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof VehicleEntity vehicle) || !vehicle.isAlive()) {
                continue;
            }
            Vec3 muzzle = vehicle.rotaryGunMuzzle(1.0F);
            if (muzzle == null) {
                continue;
            }
            boolean firing = vehicle.rotaryGunFiring();
            float heat = HEAT.getOrDefault(vehicle.getId(), 0.0F);
            heat = firing ? Math.min(1.0F, heat + HEAT_PER_TICK) : Math.max(0.0F, heat - COOL_PER_TICK);
            if (heat <= 0.0F) {
                continue;
            }
            next.put(vehicle.getId(), heat);
            // While firing the muzzle blast blows most of it away; it shows once the gun falls silent.
            float chance = (heat - SMOKE_FROM) / (1.0F - SMOKE_FROM) * (firing ? 0.1F : 0.3F);
            if (chance > 0.0F && random.nextFloat() < chance) {
                Vec3 back = vehicle.turretBarrelDirection(1.0F).scale(-random.nextDouble() * 0.7D);
                Vec3 drift = new Vec3(random.nextGaussian() * 0.004D, 0.012D + random.nextDouble() * 0.008D, random.nextGaussian() * 0.004D);
                WISPS.add(new Wisp(muzzle.add(back), drift, 20 + random.nextInt(14), random));
            }
        }
        HEAT.clear();
        HEAT.putAll(next);
        Iterator<Wisp> iterator = WISPS.iterator();
        while (iterator.hasNext()) {
            Wisp wisp = iterator.next();
            if (++wisp.age > wisp.life) {
                iterator.remove();
            }
        }
        while (WISPS.size() > MAX_WISPS) {
            WISPS.remove(0);
        }
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || WISPS.isEmpty()) {
            return;
        }
        MissileBlastRenderer.ensureTextures(mc);
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 view = camera.position();
        Vector3f right = camera.rotation().transform(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f up = camera.rotation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Matrix4f matrix = poseStack.last().pose();

        VertexConsumer smoke = bufferSource.getBuffer(RenderTypes.entityTranslucent(MissileBlastRenderer.PUFF_TEXTURE));
        for (Wisp wisp : WISPS) {
            float t = (wisp.age + partialTick) / wisp.life;
            float size = Mth.lerp(t, 0.06F, 0.22F);
            float alpha = 0.16F * Math.min(1.0F, (wisp.age + partialTick) / 3.0F) * (1.0F - t);
            Vec3 at = wisp.origin.add(wisp.drift.scale(wisp.age + partialTick)).subtract(view);
            MissileBlastRenderer.billboard(smoke, matrix, at, right, up, size, wisp.rotation + 0.02F * (wisp.age + partialTick),
                    0.82F, 0.82F, 0.8F, alpha, FULL_BRIGHT, wisp.variant);
        }
    }

    public static void clear() {
        HEAT.clear();
        WISPS.clear();
    }

    private static final class Wisp {
        final Vec3 origin;
        final Vec3 drift;
        final int life;
        final float rotation;
        final int variant;
        int age;

        Wisp(Vec3 origin, Vec3 drift, int life, RandomSource random) {
            this.origin = origin;
            this.drift = drift;
            this.life = life;
            this.rotation = random.nextFloat() * Mth.TWO_PI;
            this.variant = random.nextInt(4);
        }
    }
}
