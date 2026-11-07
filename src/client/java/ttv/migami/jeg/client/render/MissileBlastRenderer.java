package ttv.migami.jeg.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.network.MissileBlastPayload;

/**
 * Missile detonations drawn on the client: a white flash, a fireball that billows out and cools from white through
 * orange to soot, a shock bubble and a dust ring racing out across the ground, debris and embers thrown up, and a smoke
 * column that climbs, spreads and drifts for several seconds. Everything scales with the warhead.
 */
public final class MissileBlastRenderer {
    static final Identifier PUFF_TEXTURE = Reference.id("dynamic/blast_puff");
    private static final Identifier SOFT_TEXTURE = Reference.id("dynamic/blast_soft");
    private static final int FULL_BRIGHT = 0x00F000F0;
    /** A medium anti-ground missile (power 4.5) is the reference size. */
    private static final float REFERENCE_POWER = 4.5F;
    private static final int FLASH_TICKS = 5;
    private static final int SHOCK_TICKS = 7;
    private static final int RING_TICKS = 16;
    private static final List<Blast> BLASTS = new ArrayList<>();
    private static boolean texturesReady;
    private static int blastsStarted;

    private MissileBlastRenderer() {}

    public static void add(MissileBlastPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ensureTextures(mc);
        Blast blast = new Blast(payload, mc.level.getRandom().nextLong());
        blast.findGround(mc);
        blast.throwDebris(mc);
        BLASTS.add(blast);
        blastsStarted++;
    }

    /** How many blasts have been drawn since the game started, for checks. */
    public static int blastsStarted() {
        return blastsStarted;
    }

    public static void clear() {
        BLASTS.clear();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BLASTS.clear();
            return;
        }
        Iterator<Blast> iterator = BLASTS.iterator();
        while (iterator.hasNext()) {
            Blast blast = iterator.next();
            blast.age++;
            blast.kickUpDust(mc);
            if (blast.age > blast.lifetime) {
                iterator.remove();
            }
        }
    }

    static boolean texturesReady() {
        return texturesReady;
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        if (BLASTS.isEmpty() || !texturesReady) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 view = camera.position();
        Vector3f right = camera.rotation().transform(new Vector3f(1.0F, 0.0F, 0.0F));
        Vector3f up = camera.rotation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
        Matrix4f matrix = poseStack.last().pose();

        // Smoke first (lit by the world), then the glowing fire, flash and shock in front of it.
        VertexConsumer smoke = bufferSource.getBuffer(RenderTypes.entityTranslucent(PUFF_TEXTURE));
        for (Blast blast : BLASTS) {
            blast.renderSmoke(mc, smoke, matrix, view, right, up, partialTick);
            blast.renderGroundRing(mc, smoke, matrix, view, partialTick);
        }
        VertexConsumer fire = bufferSource.getBuffer(RenderTypes.entityTranslucentEmissive(PUFF_TEXTURE));
        for (Blast blast : BLASTS) {
            blast.renderFire(fire, matrix, view, right, up, partialTick);
        }
        // Light added on top (no depth writes): the flash, the fireball's glare and the shock bubble's rim.
        VertexConsumer glow = bufferSource.getBuffer(RenderTypes.lightning());
        for (Blast blast : BLASTS) {
            blast.renderFlash(glow, matrix, view, right, up, partialTick);
            blast.renderFireGlow(glow, matrix, view, right, up, partialTick);
            blast.renderShockBubble(glow, matrix, view, partialTick);
        }
    }

    private static final class Blast {
        final Vec3 centre;
        final float scale;
        final float radius;
        final boolean incendiary;
        final long seed;
        final List<Puff> fire = new ArrayList<>();
        final List<Puff> smoke = new ArrayList<>();
        final int lifetime;
        int age;
        double groundY = Double.NaN;
        BlockState ground;
        float dustR = 0.55F;
        float dustG = 0.5F;
        float dustB = 0.42F;

        Blast(MissileBlastPayload payload, long seed) {
            this.centre = payload.position();
            this.scale = Mth.clamp(payload.power() / REFERENCE_POWER, 0.55F, 2.6F);
            this.radius = Math.max(payload.blastRadius(), 4.0F * this.scale);
            this.incendiary = payload.incendiary();
            this.seed = seed;
            RandomSource random = RandomSource.create(seed);
            int fireCount = 22 + Math.round(18.0F * this.scale) + (this.incendiary ? 10 : 0);
            for (int i = 0; i < fireCount; i++) {
                Vec3 dir = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.8D + 0.15D, random.nextGaussian()).normalize();
                Puff puff = new Puff(dir, 0.3D + random.nextDouble() * 0.7D, 0.55D + random.nextDouble() * 0.5D,
                        random.nextFloat() * Mth.TWO_PI, (random.nextFloat() - 0.5F) * 0.12F, 0,
                        (this.incendiary ? 30 : 20) + random.nextInt(12), random.nextFloat(), false);
                this.fire.add(puff);
            }
            int smokeCount = 30 + Math.round(30.0F * this.scale);
            float smokeLife = 150.0F * Mth.sqrt(this.scale);
            for (int i = 0; i < smokeCount; i++) {
                boolean column = random.nextFloat() < 0.62F;
                Vec3 dir = new Vec3(random.nextGaussian(), 0.0D, random.nextGaussian()).normalize();
                Puff puff = new Puff(dir, column ? random.nextDouble() * 0.5D : 0.6D + random.nextDouble() * 0.6D,
                        0.7D + random.nextDouble() * 0.5D, random.nextFloat() * Mth.TWO_PI, (random.nextFloat() - 0.5F) * 0.02F,
                        8 + random.nextInt(11), Math.round(smokeLife * (0.7F + random.nextFloat() * 0.6F)), random.nextFloat(), column);
                this.smoke.add(puff);
            }
            int longest = 0;
            for (Puff puff : this.smoke) {
                longest = Math.max(longest, puff.delay + puff.life);
            }
            this.lifetime = Math.max(longest, RING_TICKS + 2);
        }

        /** The ground under the blast (for the dust ring), unless it went off in the air. */
        void findGround(Minecraft mc) {
            BlockPos start = BlockPos.containing(this.centre);
            for (int dy = 0; dy <= Math.round(3.0F * this.scale) + 2; dy++) {
                BlockPos pos = start.below(dy);
                BlockState state = mc.level.getBlockState(pos);
                if (!state.isAir() && !state.getCollisionShape(mc.level, pos).isEmpty()) {
                    this.groundY = pos.getY() + state.getCollisionShape(mc.level, pos).max(net.minecraft.core.Direction.Axis.Y);
                    this.ground = state;
                    int colour = state.getMapColor(mc.level, pos).col;
                    // Dust is the ground's colour, washed out towards grey.
                    this.dustR = 0.5F + 0.25F * ((colour >> 16) & 0xFF) / 255.0F;
                    this.dustG = 0.44F + 0.2F * ((colour >> 8) & 0xFF) / 255.0F;
                    this.dustB = 0.34F + 0.2F * (colour & 0xFF) / 255.0F;
                    return;
                }
            }
        }

        /** Earth, stone and burning scraps thrown up and out by the blast. */
        void throwDebris(Minecraft mc) {
            RandomSource random = RandomSource.create(this.seed ^ 0x5DEECE66DL);
            BlockState debris = this.ground != null ? this.ground : mc.level.getBlockState(BlockPos.containing(this.centre).below());
            int pieces = Math.round(36.0F * this.scale);
            for (int i = 0; i < pieces && !debris.isAir(); i++) {
                double out = 0.25D + random.nextDouble() * 0.45D * this.scale;
                double angle = random.nextDouble() * Math.PI * 2.0D;
                mc.level.addAlwaysVisibleParticle(new BlockParticleOption(ParticleTypes.BLOCK, debris), true,
                        this.centre.x, this.centre.y + 0.3D, this.centre.z,
                        Math.cos(angle) * out, 0.35D + random.nextDouble() * 0.6D * this.scale, Math.sin(angle) * out);
            }
            int embers = Math.round((this.incendiary ? 30.0F : 12.0F) * this.scale);
            for (int i = 0; i < embers; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double out = random.nextDouble() * 0.5D * this.scale;
                mc.level.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, this.centre.x, this.centre.y + 0.5D, this.centre.z,
                        Math.cos(angle) * out, 0.3D + random.nextDouble() * 0.4D, Math.sin(angle) * out);
            }
            if (this.incendiary) {
                for (int i = 0; i < 60; i++) {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double out = random.nextDouble() * 0.35D * this.scale;
                    mc.level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, this.centre.x, this.centre.y + 0.3D, this.centre.z,
                            Math.cos(angle) * out, 0.05D + random.nextDouble() * 0.25D, Math.sin(angle) * out);
                }
            }
        }

        /** Dust thrown along the ground at the front of the shockwave as it passes. */
        void kickUpDust(Minecraft mc) {
            if (Double.isNaN(this.groundY) || this.age > RING_TICKS * 3 / 4) {
                return;
            }
            RandomSource random = mc.level.getRandom();
            double front = this.ringRadius(this.age);
            int count = Math.round(10.0F * this.scale);
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double x = this.centre.x + Math.cos(angle) * front;
                double z = this.centre.z + Math.sin(angle) * front;
                double speed = 0.15D + random.nextDouble() * 0.25D;
                if (this.ground != null) {
                    mc.level.addAlwaysVisibleParticle(new BlockParticleOption(ParticleTypes.BLOCK, this.ground), true,
                            x, this.groundY + 0.1D, z, Math.cos(angle) * speed, 0.1D + random.nextDouble() * 0.2D, Math.sin(angle) * speed);
                }
                if (i % 3 == 0) {
                    mc.level.addAlwaysVisibleParticle(ParticleTypes.POOF, true, x, this.groundY + 0.2D, z,
                            Math.cos(angle) * speed * 0.5D, 0.02D, Math.sin(angle) * speed * 0.5D);
                }
            }
        }

        double ringRadius(float t) {
            float maxRadius = Math.max(this.radius * 1.6F, 9.0F * this.scale);
            return maxRadius * easeOut(Mth.clamp(t / RING_TICKS, 0.0F, 1.0F));
        }

        void renderFlash(VertexConsumer consumer, Matrix4f matrix, Vec3 view, Vector3f right, Vector3f up, float partialTick) {
            float t = this.age + partialTick;
            if (t >= FLASH_TICKS) {
                return;
            }
            float fade = 1.0F - t / FLASH_TICKS;
            Vec3 at = this.centre.add(0.0D, 0.6D * this.scale, 0.0D).subtract(view);
            float size = this.scale * (2.5F + 3.5F * t / FLASH_TICKS);
            disc(consumer, matrix, at, right, up, size * 2.6F, 1.0F, 0.82F, 0.55F, fade * fade * 0.6F);
            disc(consumer, matrix, at, right, up, size, 1.0F, 0.98F, 0.9F, fade * fade);
        }

        /** The glare around the fireball while it is still burning hot. */
        void renderFireGlow(VertexConsumer consumer, Matrix4f matrix, Vec3 view, Vector3f right, Vector3f up, float partialTick) {
            float t = this.age + partialTick;
            float burn = (this.incendiary ? 34.0F : 22.0F);
            if (t >= burn) {
                return;
            }
            float strength = 0.55F * (1.0F - t / burn) * Math.min(1.0F, t / 1.5F + 0.3F);
            Vec3 at = this.centre.add(0.0D, (1.0D + 0.05D * t) * this.scale, 0.0D).subtract(view);
            float size = this.scale * (4.0F + 3.0F * (1.0F - (float) Math.exp(-t / 4.0D)));
            disc(consumer, matrix, at, right, up, size, 1.0F, 0.55F, 0.18F, strength);
        }

        void renderFire(VertexConsumer consumer, Matrix4f matrix, Vec3 view, Vector3f right, Vector3f up, float partialTick) {
            float t = this.age + partialTick;
            List<Quad> quads = new ArrayList<>();
            for (Puff puff : this.fire) {
                if (t > puff.life) {
                    continue;
                }
                // The core burns hottest and longest; the edges cool first and some pockets are already soot.
                float heat = Mth.clamp(t / puff.life + (float) (puff.reach - 0.6D) * 0.35F + (puff.shade < 0.22F ? 0.3F : 0.0F), 0.0F, 1.0F);
                double reach = 2.3D * this.scale * puff.reach * (1.0D - Math.exp(-t / 2.5D));
                Vec3 at = this.centre.add(puff.dir.scale(reach)).add(0.0D, 0.05D * this.scale * t, 0.0D);
                double size = this.scale * puff.size * (0.55D + 1.5D * (1.0D - Math.exp(-t / 3.0D)));
                float[] colour = fireColour(heat, puff.shade);
                float alpha = heat < 0.55F ? 1.0F : 1.0F - (heat - 0.55F) / 0.45F;
                quads.add(new Quad(at.subtract(view), (float) size, puff.rotation + puff.spin * t, colour, alpha * 0.95F, FULL_BRIGHT, puff.variant()));
            }
            drawSorted(consumer, matrix, right, up, quads);
        }

        void renderSmoke(Minecraft mc, VertexConsumer consumer, Matrix4f matrix, Vec3 view, Vector3f right, Vector3f up, float partialTick) {
            float t = this.age + partialTick;
            float daylight = Mth.clamp(mc.level.getMaxLocalRawBrightness(BlockPos.containing(this.centre).above(2)) / 15.0F, 0.25F, 1.0F);
            List<Quad> quads = new ArrayList<>();
            for (Puff puff : this.smoke) {
                float tau = t - puff.delay;
                if (tau <= 0.0F || tau > puff.life) {
                    continue;
                }
                float life = tau / puff.life;
                Vec3 at;
                if (puff.column) {
                    // The column climbs fast, then slows and spreads as it cools, leaning with the wind.
                    double rise = (4.0D + 7.0D * puff.reach) * this.scale * (1.0D - Math.exp(-tau / 35.0D)) + 0.012D * tau;
                    double spread = (0.4D + puff.reach) * this.scale * (0.4D + rise / (8.0D * this.scale));
                    at = this.centre.add(puff.dir.scale(spread)).add(0.02D * tau, rise, 0.008D * tau);
                } else {
                    // The skirt rolls outward along the ground with the blast, then lifts and thins.
                    double reach = 3.6D * this.scale * puff.reach * (1.0D - Math.exp(-tau / 7.0D)) + 0.01D * tau;
                    at = this.centre.add(puff.dir.scale(reach)).add(0.015D * tau, 0.4D * this.scale + 0.012D * tau, 0.006D * tau);
                }
                double size = Math.min(4.8D * this.scale, this.scale * puff.size * (1.0D + tau / 50.0D));
                float grey = 0.13F + 0.3F * Math.min(1.0F, tau / 120.0F) + 0.06F * puff.shade;
                float warm = Math.max(0.0F, 1.0F - tau / 14.0F);
                float r = (grey + warm * 0.45F) * daylight;
                float g = (grey + warm * 0.18F) * daylight;
                float b = grey * daylight * 0.97F;
                float alpha = 0.82F * Math.min(1.0F, tau / 5.0F) * (1.0F - life * life);
                quads.add(new Quad(at.subtract(view), (float) size, puff.rotation + puff.spin * tau, new float[] {r, g, b}, alpha, FULL_BRIGHT, puff.variant()));
            }
            drawSorted(consumer, matrix, right, up, quads);
        }

        /** A translucent bubble of compressed air, bright at its rim, racing outward in the first few ticks. */
        void renderShockBubble(VertexConsumer consumer, Matrix4f matrix, Vec3 view, float partialTick) {
            float t = this.age + partialTick;
            if (t >= SHOCK_TICKS) {
                return;
            }
            float progress = t / SHOCK_TICKS;
            float bubble = Math.max(this.radius * 0.9F, 5.0F * this.scale) * easeOut(progress);
            float strength = 0.38F * (1.0F - progress);
            Vec3 c = this.centre.subtract(view);
            int rings = 10;
            int segments = 24;
            for (int ring = 0; ring < rings; ring++) {
                // The upper hemisphere and a little below the horizon; the ground hides the rest.
                double a0 = -0.15D + (Math.PI / 2.0D + 0.15D) * ring / rings;
                double a1 = -0.15D + (Math.PI / 2.0D + 0.15D) * (ring + 1) / rings;
                for (int seg = 0; seg < segments; seg++) {
                    double b0 = Math.PI * 2.0D * seg / segments;
                    double b1 = Math.PI * 2.0D * (seg + 1) / segments;
                    Vec3 n00 = sphere(a0, b0);
                    Vec3 n01 = sphere(a0, b1);
                    Vec3 n11 = sphere(a1, b1);
                    Vec3 n10 = sphere(a1, b0);
                    shellVertex(consumer, matrix, c, n00, bubble, strength);
                    shellVertex(consumer, matrix, c, n01, bubble, strength);
                    shellVertex(consumer, matrix, c, n11, bubble, strength);
                    shellVertex(consumer, matrix, c, n10, bubble, strength);
                }
            }
        }

        /** A ring of dust and distorted air skimming out across the ground ahead of the smoke. */
        void renderGroundRing(Minecraft mc, VertexConsumer consumer, Matrix4f matrix, Vec3 view, float partialTick) {
            float t = this.age + partialTick;
            if (Double.isNaN(this.groundY) || t >= RING_TICKS) {
                return;
            }
            float progress = t / RING_TICKS;
            double radius = this.ringRadius(t);
            double width = 0.8D * this.scale + radius * 0.28D;
            double inner = Math.max(0.0D, radius - width);
            double outer = radius + width * 0.35D;
            float alpha = 0.6F * (1.0F - progress) * Math.min(1.0F, t * 2.0F);
            float daylight = Mth.clamp(mc.level.getMaxLocalRawBrightness(BlockPos.containing(this.centre).above(2)) / 15.0F, 0.3F, 1.0F);
            float r = this.dustR * daylight;
            float g = this.dustG * daylight;
            float b = this.dustB * daylight;
            double y = this.groundY + 0.08D - view.y;
            double cx = this.centre.x - view.x;
            double cz = this.centre.z - view.z;
            int segments = 48;
            for (int seg = 0; seg < segments; seg++) {
                double a0 = Math.PI * 2.0D * seg / segments;
                double a1 = Math.PI * 2.0D * (seg + 1) / segments;
                double c0 = Math.cos(a0);
                double s0 = Math.sin(a0);
                double c1 = Math.cos(a1);
                double s1 = Math.sin(a1);
                // Inner half fades in to the front; outer half fades out beyond it.
                groundVertex(consumer, matrix, cx + c0 * inner, y, cz + s0 * inner, r, g, b, 0.0F);
                groundVertex(consumer, matrix, cx + c1 * inner, y, cz + s1 * inner, r, g, b, 0.0F);
                groundVertex(consumer, matrix, cx + c1 * radius, y, cz + s1 * radius, r, g, b, alpha);
                groundVertex(consumer, matrix, cx + c0 * radius, y, cz + s0 * radius, r, g, b, alpha);

                groundVertex(consumer, matrix, cx + c0 * radius, y, cz + s0 * radius, r, g, b, alpha);
                groundVertex(consumer, matrix, cx + c1 * radius, y, cz + s1 * radius, r, g, b, alpha);
                groundVertex(consumer, matrix, cx + c1 * outer, y, cz + s1 * outer, r, g, b, 0.0F);
                groundVertex(consumer, matrix, cx + c0 * outer, y, cz + s0 * outer, r, g, b, 0.0F);
            }
        }

        private static Vec3 sphere(double elevation, double azimuth) {
            double horizontal = Math.cos(elevation);
            return new Vec3(horizontal * Math.cos(azimuth), Math.sin(elevation), horizontal * Math.sin(azimuth));
        }

        private static void shellVertex(VertexConsumer consumer, Matrix4f matrix, Vec3 centre, Vec3 normal, float radius, float strength) {
            Vec3 at = centre.add(normal.scale(radius));
            // Seen face-on the bubble is clear; along its edge the light bends most, so it shows there.
            double facing = Math.abs(normal.dot(at.normalize()));
            float alpha = strength * (float) Math.pow(1.0D - facing, 2.0D);
            consumer.addVertex(matrix, (float) at.x, (float) at.y, (float) at.z).setColor(1.0F, 0.97F, 0.92F, alpha);
        }

        private static void groundVertex(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z,
                                         float r, float g, float b, float alpha) {
            consumer.addVertex(matrix, (float) x, (float) y, (float) z)
                    .setColor(r, g, b, alpha)
                    .setUv(0.5F, 0.5F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT)
                    .setNormal(0.0F, 1.0F, 0.0F);
        }
    }

    /** White-hot, then yellow, orange, deep red and finally soot as the fireball cools. */
    private static float[] fireColour(float heat, float shade) {
        float[][] stops = {
                {1.0F, 0.98F, 0.88F},
                {1.0F, 0.82F, 0.42F},
                {1.0F, 0.52F, 0.14F},
                {0.72F, 0.2F, 0.05F},
                {0.22F, 0.11F, 0.07F}
        };
        float[] at = {0.0F, 0.12F, 0.32F, 0.58F, 1.0F};
        heat = Mth.clamp(heat + (shade - 0.5F) * 0.12F, 0.0F, 1.0F);
        for (int i = 1; i < at.length; i++) {
            if (heat <= at[i]) {
                float f = (heat - at[i - 1]) / (at[i] - at[i - 1]);
                return new float[] {
                        Mth.lerp(f, stops[i - 1][0], stops[i][0]),
                        Mth.lerp(f, stops[i - 1][1], stops[i][1]),
                        Mth.lerp(f, stops[i - 1][2], stops[i][2])
                };
            }
        }
        return stops[stops.length - 1];
    }

    private static float easeOut(float x) {
        float inv = 1.0F - x;
        return 1.0F - inv * inv * inv;
    }

    private record Puff(Vec3 dir, double reach, double size, float rotation, float spin, int delay, int life, float shade, boolean column) {
        int variant() {
            return Math.min(3, (int) (this.shade * 4.0F));
        }
    }

    private record Quad(Vec3 at, float size, float rotation, float[] colour, float alpha, int light, int variant) {}

    /** Back to front, so overlapping translucent puffs blend correctly. */
    private static void drawSorted(VertexConsumer consumer, Matrix4f matrix, Vector3f right, Vector3f up, List<Quad> quads) {
        quads.sort(Comparator.comparingDouble((Quad quad) -> quad.at().lengthSqr()).reversed());
        for (Quad quad : quads) {
            billboard(consumer, matrix, quad.at(), right, up, quad.size(), quad.rotation(),
                    quad.colour()[0], quad.colour()[1], quad.colour()[2], quad.alpha(), quad.light(), quad.variant());
        }
    }

    /** A glowing disc facing the camera: bright in the middle, fading to nothing at its edge. */
    static void disc(VertexConsumer consumer, Matrix4f matrix, Vec3 at, Vector3f right, Vector3f up, float radius,
                             float r, float g, float b, float alpha) {
        if (alpha <= 0.003F) {
            return;
        }
        int segments = 24;
        float x = (float) at.x;
        float y = (float) at.y;
        float z = (float) at.z;
        for (int i = 0; i < segments; i++) {
            float a0 = Mth.TWO_PI * i / segments;
            float a1 = Mth.TWO_PI * (i + 1) / segments;
            float c0 = Mth.cos(a0) * radius;
            float s0 = Mth.sin(a0) * radius;
            float c1 = Mth.cos(a1) * radius;
            float s1 = Mth.sin(a1) * radius;
            consumer.addVertex(matrix, x, y, z).setColor(r, g, b, alpha);
            consumer.addVertex(matrix, x, y, z).setColor(r, g, b, alpha);
            consumer.addVertex(matrix, x + right.x() * c1 + up.x() * s1, y + right.y() * c1 + up.y() * s1, z + right.z() * c1 + up.z() * s1)
                    .setColor(r, g, b, 0.0F);
            consumer.addVertex(matrix, x + right.x() * c0 + up.x() * s0, y + right.y() * c0 + up.y() * s0, z + right.z() * c0 + up.z() * s0)
                    .setColor(r, g, b, 0.0F);
        }
    }

    private static void billboard(VertexConsumer consumer, Matrix4f matrix, Vec3 at, Vector3f right, Vector3f up, float size,
                                  float rotation, float r, float g, float b, float alpha, int light) {
        billboard(consumer, matrix, at, right, up, size, rotation, r, g, b, alpha, light, 0);
    }

    static void billboard(VertexConsumer consumer, Matrix4f matrix, Vec3 at, Vector3f right, Vector3f up, float size,
                                  float rotation, float r, float g, float b, float alpha, int light, int variant) {
        if (alpha <= 0.003F) {
            return;
        }
        float cos = Mth.cos(rotation) * size;
        float sin = Mth.sin(rotation) * size;
        // The quad's corners, turned about the line of sight so neighbouring puffs do not repeat.
        float ax = right.x() * cos + up.x() * sin;
        float ay = right.y() * cos + up.y() * sin;
        float az = right.z() * cos + up.z() * sin;
        float bx = -right.x() * sin + up.x() * cos;
        float by = -right.y() * sin + up.y() * cos;
        float bz = -right.z() * sin + up.z() * cos;
        float x = (float) at.x;
        float y = (float) at.y;
        float z = (float) at.z;
        // Four cloud shapes share the sprite sheet, one per quarter.
        float u0 = (variant & 1) * 0.5F;
        float v0 = ((variant >> 1) & 1) * 0.5F;
        corner(consumer, matrix, x - ax - bx, y - ay - by, z - az - bz, r, g, b, alpha, u0, v0 + 0.5F, light);
        corner(consumer, matrix, x + ax - bx, y + ay - by, z + az - bz, r, g, b, alpha, u0 + 0.5F, v0 + 0.5F, light);
        corner(consumer, matrix, x + ax + bx, y + ay + by, z + az + bz, r, g, b, alpha, u0 + 0.5F, v0, light);
        corner(consumer, matrix, x - ax + bx, y - ay + by, z - az + bz, r, g, b, alpha, u0, v0, light);
    }

    private static void corner(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                               float r, float g, float b, float alpha, float u, float v, int light) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /** A billowing cloud sprite (soft edge broken up by noise) and a plain soft glow, made once on first use. */
    static void ensureTextures(Minecraft mc) {
        if (texturesReady) {
            return;
        }
        NativeImage atlas = new NativeImage(128, 128, false);
        for (int variant = 0; variant < 4; variant++) {
            NativeImage cell = cloudImage(64, 1337L + variant * 7919L);
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    atlas.setPixel((variant & 1) * 64 + x, ((variant >> 1) & 1) * 64 + y, cell.getPixel(x, y));
                }
            }
            cell.close();
        }
        mc.getTextureManager().register(PUFF_TEXTURE, new DynamicTexture(PUFF_TEXTURE::toString, atlas));
        mc.getTextureManager().register(SOFT_TEXTURE, new DynamicTexture(SOFT_TEXTURE::toString, softImage(32)));
        texturesReady = true;
    }

    private static NativeImage cloudImage(int size, long seed) {
        NativeImage image = new NativeImage(size, size, false);
        float[][] noise = fractalNoise(size, seed);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5F) / size * 2.0F - 1.0F;
                float dy = (y + 0.5F) / size * 2.0F - 1.0F;
                float d = Mth.sqrt(dx * dx + dy * dy);
                // The outline is pushed in and out by the noise, so puffs are lumpy rather than round.
                float lumpy = d + (0.5F - noise[(x * 3) % size][(y * 3) % size]) * 0.45F;
                float edge = Mth.clamp((1.0F - lumpy) / 0.5F, 0.0F, 1.0F);
                edge = edge * edge * (3.0F - 2.0F * edge);
                float density = Mth.clamp(edge * (0.35F + 1.0F * noise[x][y]) - 0.1F, 0.0F, 1.0F);
                // Lighter on top, as if lit from above, so the cloud reads as a solid billow rather than a flat disc.
                float lightShade = Mth.clamp(0.78F + 0.22F * (-dy) + 0.15F * (noise[x][y] - 0.5F), 0.55F, 1.0F);
                int value = Math.round(lightShade * 255.0F);
                int alpha = Math.round(density * 255.0F);
                image.setPixel(x, y, alpha << 24 | value << 16 | value << 8 | value);
            }
        }
        return image;
    }

    private static NativeImage softImage(int size) {
        NativeImage image = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5F) / size * 2.0F - 1.0F;
                float dy = (y + 0.5F) / size * 2.0F - 1.0F;
                float d = Math.min(1.0F, Mth.sqrt(dx * dx + dy * dy));
                float alpha = (1.0F - d) * (1.0F - d);
                image.setPixel(x, y, Math.round(alpha * 255.0F) << 24 | 0xFFFFFF);
            }
        }
        return image;
    }

    /** Smooth value noise summed over a few octaves, 0..1, wrapping so the sprite has no seams. */
    private static float[][] fractalNoise(int size, long seed) {
        RandomSource random = RandomSource.create(seed);
        float[][] out = new float[size][size];
        float amplitude = 1.0F;
        float total = 0.0F;
        for (int cells = 4; cells <= 32; cells *= 2) {
            float[][] lattice = new float[cells][cells];
            for (int i = 0; i < cells; i++) {
                for (int j = 0; j < cells; j++) {
                    lattice[i][j] = random.nextFloat();
                }
            }
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float fx = (float) x / size * cells;
                    float fy = (float) y / size * cells;
                    int x0 = (int) fx;
                    int y0 = (int) fy;
                    float tx = smooth(fx - x0);
                    float ty = smooth(fy - y0);
                    float a = Mth.lerp(tx, lattice[x0 % cells][y0 % cells], lattice[(x0 + 1) % cells][y0 % cells]);
                    float b = Mth.lerp(tx, lattice[x0 % cells][(y0 + 1) % cells], lattice[(x0 + 1) % cells][(y0 + 1) % cells]);
                    out[x][y] += Mth.lerp(ty, a, b) * amplitude;
                }
            }
            total += amplitude;
            amplitude *= 0.55F;
        }
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                out[x][y] /= total;
            }
        }
        return out;
    }

    private static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }
}
