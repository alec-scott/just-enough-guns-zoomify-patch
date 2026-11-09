package ttv.migami.jeg.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.GunCameraSwayHandler;
import ttv.migami.jeg.network.MissileBlastPayload;

/**
 * Missile and rocket detonations drawn on the client, scaled to the warhead:
 * <ul>
 *     <li>a blinding flash with a lens streak, a flash of the whole screen for anyone close and facing it, and firelight
 *     thrown across the ground;</li>
 *     <li>a fireball that billows out and cools from white through orange to soot, then rises into a rolling mushroom cap
 *     on a column of smoke;</li>
 *     <li>a shockwave: a white condensation dome and a bright ring tearing out across the ground, raising a wall of dust
 *     behind it. When it reaches the camera the view is jolted and the boom is heard, so a far blast is seen before it
 *     is heard;</li>
 *     <li>burning debris arcing out on smoke trails, sparks and thrown earth.</li>
 * </ul>
 */
public final class MissileBlastRenderer {
    static final Identifier PUFF_TEXTURE = Reference.id("dynamic/blast_puff");
    private static final Identifier SOFT_TEXTURE = Reference.id("dynamic/blast_soft");
    private static final int FULL_BRIGHT = 0x00F000F0;
    /** A medium anti-ground missile (power 4.5) is the reference size. */
    private static final float REFERENCE_POWER = 4.5F;
    /** The biggest warhead's power: a blast's shake and boom are measured against it. */
    private static final float BIGGEST_POWER = 9.0F;
    private static final int FLASH_TICKS = 6;
    /** The visible shockwave tears out over this long; past it, it carries on unseen at the speed of sound. */
    private static final int SHOCK_TICKS = 12;
    private static final int RING_TICKS = SHOCK_TICKS + 8;
    /** 343 m/s in blocks per tick. */
    private static final double SOUND_SPEED = 17.15D;
    /** How far the biggest blast's shock is felt; smaller ones by the square root of their size. */
    private static final double SHAKE_RANGE = 140.0D;
    private static final int TRAIL_TICKS = 36;
    /** Smoke puffs laid along each tick of a burning scrap's flight. */
    private static final int TRAIL_STEPS = 3;
    /** How strongly smoke and dust are lit from above and shadowed underneath; fire glows a little from below. */
    private static final float SMOKE_GRADIENT = 0.32F;
    private static final float DUST_GRADIENT = 0.36F;
    private static final float FIRE_GRADIENT = -0.14F;
    /** Sprite cell size: large enough to stay soft when a puff fills the view. */
    private static final int SPRITE_CELL = 128;
    private static final List<Blast> BLASTS = new ArrayList<>();
    private static boolean texturesReady;
    private static int blastsStarted;
    private static int shocksFelt;
    private static float screenFlash;
    private static long screenFlashNanos;

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
        flashScreen(mc, blast);
    }

    /** How many blasts have been drawn since the game started, for checks. */
    public static int blastsStarted() {
        return blastsStarted;
    }

    /** How many blasts' shockwaves have reached the camera since the game started, for checks. */
    public static int shocksFelt() {
        return shocksFelt;
    }

    public static void clear() {
        BLASTS.clear();
        screenFlash = 0.0F;
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
            blast.tick(mc);
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
        Vector3f forward = new Vector3f(camera.forwardVector());
        // Puffs face the camera but stay upright in the world: a banking helicopter's view rolls past them instead of
        // the whole cloud rolling with the screen, which is what gave it away as flat cards.
        Vector3f right = new Vector3f(forward).cross(0.0F, 1.0F, 0.0F);
        Vector3f up;
        if (right.lengthSquared() < 1.0E-4F) {
            right = camera.rotation().transform(new Vector3f(1.0F, 0.0F, 0.0F));
            up = camera.rotation().transform(new Vector3f(0.0F, 1.0F, 0.0F));
        } else {
            right.normalize();
            up = new Vector3f(right).cross(forward).normalize();
        }
        Matrix4f matrix = poseStack.last().pose();
        // A blast beyond the render distance is drawn as a smaller copy nearer the camera, the same on screen but inside
        // the fog (see FarDraw); the whole blast shrinks by one factor so it keeps its shape.
        float[] far = new float[BLASTS.size()];
        Matrix4f[] matrices = new Matrix4f[BLASTS.size()];
        for (int i = 0; i < BLASTS.size(); i++) {
            far[i] = FarDraw.scale(BLASTS.get(i).centre.subtract(view));
            matrices[i] = far[i] < 1.0F ? new Matrix4f(matrix).scale(far[i]) : matrix;
        }

        // Marks and dust lying on the ground first, then smoke and fire together, and light added on top last (no depth
        // writes): flash, glare, shock rims, sparks.
        VertexConsumer ground = bufferSource.getBuffer(RenderTypes.entityTranslucent(SOFT_TEXTURE));
        for (int i = 0; i < BLASTS.size(); i++) {
            BLASTS.get(i).renderDustRing(mc, ground, matrices[i], view, partialTick);
        }
        // Smoke and fire are one cloud, sorted back to front as a whole: drawn as two layers, each puff's depth cut hard
        // edges out of the other wherever they overlapped.
        List<Quad> quads = new ArrayList<>();
        for (int i = 0; i < BLASTS.size(); i++) {
            int first = quads.size();
            BLASTS.get(i).collectSmoke(mc, quads, view, forward, partialTick);
            BLASTS.get(i).collectFire(quads, view, partialTick);
            if (far[i] < 1.0F) {
                for (int q = first; q < quads.size(); q++) {
                    quads.set(q, quads.get(q).scaled(far[i]));
                }
            }
        }
        VertexConsumer puffs = bufferSource.getBuffer(RenderTypes.entityTranslucent(PUFF_TEXTURE));
        drawSorted(puffs, matrix, right, up, forward, quads);
        VertexConsumer glow = bufferSource.getBuffer(RenderTypes.lightning());
        for (int i = 0; i < BLASTS.size(); i++) {
            BLASTS.get(i).renderGlow(glow, matrices[i], view, right, up, partialTick);
        }
    }

    /** The whole screen whites out for a moment when a blast goes off close by and in view. */
    public static void renderScreenFlash(GuiGraphicsExtractor guiGraphics) {
        if (screenFlash <= 0.0F) {
            return;
        }
        double elapsed = (System.nanoTime() - screenFlashNanos) / 1.0E9D;
        float alpha = (float) (screenFlash * (0.75D * Math.exp(-elapsed / 0.07D) + 0.25D * Math.exp(-elapsed / 0.45D)));
        if (alpha < 0.004F) {
            screenFlash = 0.0F;
            return;
        }
        int a = Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), a << 24 | 0xFFF2DC);
    }

    private static void flashScreen(Minecraft mc, Blast blast) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 eye = camera.position();
        Vec3 target = blast.centre.add(0.0D, 0.8D * blast.scale, 0.0D);
        Vec3 toBlast = target.subtract(eye);
        double distance = toBlast.length();
        double reach = 70.0D * Math.sqrt(blast.scale);
        if (distance >= reach) {
            return;
        }
        Vector3f forward = camera.rotation().transform(new Vector3f(0.0F, 0.0F, -1.0F));
        double facing = distance < 1.0E-3D ? 1.0D
                : Math.max(0.0D, (toBlast.x * forward.x() + toBlast.y * forward.y() + toBlast.z * forward.z()) / distance);
        float strength = (float) ((1.0D - distance / reach) * (0.3D + 0.7D * facing));
        HitResult hit = mc.level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.MISS) {
            strength *= 0.3F;
        }
        float now = renderedFlash();
        screenFlash = Math.max(now, Math.min(0.85F, strength));
        screenFlashNanos = System.nanoTime();
    }

    private static float renderedFlash() {
        if (screenFlash <= 0.0F) {
            return 0.0F;
        }
        double elapsed = (System.nanoTime() - screenFlashNanos) / 1.0E9D;
        return (float) (screenFlash * (0.75D * Math.exp(-elapsed / 0.07D) + 0.25D * Math.exp(-elapsed / 0.45D)));
    }

    private static final class Blast {
        final Vec3 centre;
        final float power;
        final float scale;
        final float radius;
        /** How far the visible shockwave reaches. */
        final float shockRadius;
        final boolean incendiary;
        final boolean boom;
        final long seed;
        final List<Puff> fire = new ArrayList<>();
        final List<Puff> smoke = new ArrayList<>();
        final List<CapPuff> cap = new ArrayList<>();
        final List<WallPuff> wall = new ArrayList<>();
        final List<Vec3> shell = new ArrayList<>();
        final List<Streamer> streamers = new ArrayList<>();
        final List<Spark> sparks = new ArrayList<>();
        int lifetime;
        int age;
        boolean felt;
        double groundY = Double.NaN;
        BlockState ground;
        float dustR = 0.55F;
        float dustG = 0.5F;
        float dustB = 0.42F;

        Blast(MissileBlastPayload payload, long seed) {
            this.centre = payload.position();
            this.power = payload.power();
            this.scale = Mth.clamp(payload.power() / REFERENCE_POWER, 0.55F, 2.6F);
            this.radius = Math.max(payload.blastRadius(), 4.0F * this.scale);
            this.shockRadius = Math.max(this.radius * 2.2F, 12.0F * this.scale);
            this.incendiary = payload.incendiary();
            this.boom = payload.boom();
            this.seed = seed;
            RandomSource random = RandomSource.create(seed);

            int fireCount = 46 + Math.round(36.0F * this.scale) + (this.incendiary ? 16 : 0);
            for (int i = 0; i < fireCount; i++) {
                Vec3 dir = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.8D + 0.15D, random.nextGaussian()).normalize();
                this.fire.add(new Puff(dir, 0.3D + random.nextDouble() * 0.75D, 0.45D + random.nextDouble() * 0.5D,
                        random.nextFloat() * Mth.TWO_PI, (random.nextFloat() - 0.5F) * 0.12F, 0,
                        (this.incendiary ? 30 : 20) + random.nextInt(12), random.nextFloat(), false));
            }

            // The stem and the low skirt of smoke.
            int smokeCount = 30 + Math.round(30.0F * this.scale);
            float smokeLife = 160.0F * Mth.sqrt(this.scale);
            for (int i = 0; i < smokeCount; i++) {
                boolean column = random.nextFloat() < 0.62F;
                Vec3 dir = new Vec3(random.nextGaussian(), 0.0D, random.nextGaussian()).normalize();
                this.smoke.add(new Puff(dir, column ? random.nextDouble() * 0.5D : 0.6D + random.nextDouble() * 0.6D,
                        0.7D + random.nextDouble() * 0.5D, random.nextFloat() * Mth.TWO_PI, (random.nextFloat() - 0.5F) * 0.02F,
                        8 + random.nextInt(11), Math.round(smokeLife * (0.7F + random.nextFloat() * 0.6F)), random.nextFloat(), column));
            }

            // The mushroom cap: puffs spread around a rising, rolling ring vortex.
            int capCount = 24 + Math.round(22.0F * this.scale);
            for (int i = 0; i < capCount; i++) {
                this.cap.add(new CapPuff(random.nextFloat() * Mth.TWO_PI, random.nextFloat() * Mth.TWO_PI,
                        0.75F + random.nextFloat() * 0.6F, random.nextFloat(),
                        Math.round(smokeLife * 1.15F * (0.8F + random.nextFloat() * 0.4F)),
                        random.nextFloat() * Mth.TWO_PI, (random.nextFloat() - 0.5F) * 0.015F));
            }

            // The condensation skirt: points low over the shock bubble, crowded towards the ground where it is thickest. Kept
            // low: a full white dome swept over a nearby helicopter as one big flat sheet.
            int shellCount = 70 + Math.round(50.0F * this.scale);
            for (int i = 0; i < shellCount; i++) {
                double elevation = Math.asin(Math.pow(random.nextDouble(), 1.6D)) * 0.42D;
                double azimuth = random.nextDouble() * Math.PI * 2.0D;
                this.shell.add(new Vec3(Math.cos(elevation) * Math.cos(azimuth), Math.sin(elevation), Math.cos(elevation) * Math.sin(azimuth)));
            }

            // The wall of dust raised along the ground as the shock front passes.
            int wallCount = 60 + Math.round(50.0F * this.scale);
            for (int i = 0; i < wallCount; i++) {
                this.wall.add(new WallPuff(random.nextFloat() * Mth.TWO_PI, 0.15F + 0.85F * (float) Math.sqrt(random.nextFloat()),
                        0.8F + random.nextFloat() * 0.7F, random.nextFloat(), 50 + random.nextInt(50),
                        random.nextFloat() * Mth.TWO_PI, (random.nextFloat() - 0.5F) * 0.03F));
            }

            // Burning scraps flung out in arcs, trailing smoke.
            int streamerCount = Math.round(5.0F + 7.0F * this.scale) + (this.incendiary ? 4 : 0);
            for (int i = 0; i < streamerCount; i++) {
                double elevation = Math.toRadians(25.0D + random.nextDouble() * 50.0D);
                double azimuth = random.nextDouble() * Math.PI * 2.0D;
                double speed = (0.55D + random.nextDouble() * 0.6D) * Math.sqrt(this.scale);
                Vec3 velocity = new Vec3(Math.cos(elevation) * Math.cos(azimuth), Math.sin(elevation), Math.cos(elevation) * Math.sin(azimuth)).scale(speed);
                this.streamers.add(new Streamer(this.centre.add(0.0D, 0.4D, 0.0D), velocity, 22 + random.nextInt(20)));
            }

            // White-hot sparks spraying out.
            int sparkCount = Math.round(50.0F + 50.0F * this.scale);
            for (int i = 0; i < sparkCount; i++) {
                Vec3 dir = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.9D + 0.1D, random.nextGaussian()).normalize();
                double speed = (0.8D + random.nextDouble() * 1.4D) * Math.sqrt(this.scale);
                this.sparks.add(new Spark(this.centre.add(0.0D, 0.3D, 0.0D), dir.scale(speed), 8 + random.nextInt(13)));
            }

            int longest = RING_TICKS + 2;
            for (Puff puff : this.smoke) {
                longest = Math.max(longest, puff.delay + puff.life);
            }
            for (CapPuff puff : this.cap) {
                longest = Math.max(longest, puff.life);
            }
            this.lifetime = longest;
        }

        /** The ground under the blast (for the dust and ring), unless it went off in the air. */
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

        /**
         * The blast arrives a moment before the blocks it destroyed are gone: look again at the ground once the crater
         * is there. The ring, dust wall and dome sit on the ground around the blast (the middle of a ring of samples, so
         * a crater or a roof does not lift or sink them).
         */
        void settleGround(Minecraft mc) {
            if (Double.isNaN(this.groundY)) {
                return;
            }
            double top = this.centre.y + 3.0D;
            int depth = Math.round(this.radius) + 8;
            List<Double> samples = new ArrayList<>();
            double around = Math.min(this.radius * 1.2D, 20.0D);
            for (int i = 0; i < 12; i++) {
                double angle = Math.PI * 2.0D * i / 12;
                double y = surfaceBelow(mc, this.centre.x + Math.cos(angle) * around, this.centre.z + Math.sin(angle) * around, top, depth);
                if (!Double.isNaN(y)) {
                    samples.add(y);
                }
            }
            if (samples.size() >= 4) {
                samples.sort(Double::compare);
                this.groundY = samples.get(samples.size() / 2);
            }
        }

        /** The top of the first solid block at or below {@code fromY} in that column, or NaN within {@code depth} blocks. */
        private static double surfaceBelow(Minecraft mc, double x, double z, double fromY, int depth) {
            BlockPos start = BlockPos.containing(x, fromY, z);
            for (int dy = 0; dy <= depth; dy++) {
                BlockPos pos = start.below(dy);
                BlockState state = mc.level.getBlockState(pos);
                if (!state.isAir() && !state.getCollisionShape(mc.level, pos).isEmpty()) {
                    return pos.getY() + state.getCollisionShape(mc.level, pos).max(net.minecraft.core.Direction.Axis.Y);
                }
            }
            return Double.NaN;
        }

        /** Earth and stone thrown up and out by the blast, and embers. */
        void throwDebris(Minecraft mc) {
            RandomSource random = RandomSource.create(this.seed ^ 0x5DEECE66DL);
            BlockState debris = this.ground != null ? this.ground : mc.level.getBlockState(BlockPos.containing(this.centre).below());
            int pieces = Math.round(48.0F * this.scale);
            for (int i = 0; i < pieces && !debris.isAir(); i++) {
                double out = 0.25D + random.nextDouble() * 0.55D * this.scale;
                double angle = random.nextDouble() * Math.PI * 2.0D;
                mc.level.addAlwaysVisibleParticle(new BlockParticleOption(ParticleTypes.BLOCK, debris), true,
                        this.centre.x, this.centre.y + 0.3D, this.centre.z,
                        Math.cos(angle) * out, 0.35D + random.nextDouble() * 0.7D * this.scale, Math.sin(angle) * out);
            }
            int embers = Math.round((this.incendiary ? 30.0F : 14.0F) * this.scale);
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

        void tick(Minecraft mc) {
            if (this.age == 1) {
                this.settleGround(mc);
            }
            this.kickUpDust(mc);
            for (Streamer streamer : this.streamers) {
                streamer.tick(this.age, this.groundY);
            }
            for (Spark spark : this.sparks) {
                spark.tick();
            }
            this.feelShock(mc);
        }

        /** Lifts a low puff so most of it sits above the ground; what still reaches it fades out (see billboard). */
        Vec3 aboveGround(Vec3 at, double size) {
            if (Double.isNaN(this.groundY)) {
                return at;
            }
            double floor = this.groundY + size * 0.55D;
            return at.y < floor ? new Vec3(at.x, floor, at.z) : at;
        }

        /** The base of the dome, ring and dust: the ground, or the blast itself in the air. */
        Vec3 base() {
            return Double.isNaN(this.groundY) ? this.centre : new Vec3(this.centre.x, this.groundY, this.centre.z);
        }

        /** How far the shock front has travelled: racing out and slowing over the visible part, then at the speed of sound. */
        double shockFront(float t) {
            if (t <= SHOCK_TICKS) {
                return this.shockRadius * easeOut(Mth.clamp(t / SHOCK_TICKS, 0.0F, 1.0F));
            }
            return this.shockRadius + SOUND_SPEED * (t - SHOCK_TICKS);
        }

        /** When the front reaches the camera: the view is jolted, and (for missiles) the boom is heard. */
        void feelShock(Minecraft mc) {
            if (this.felt) {
                return;
            }
            Vec3 eye = mc.gameRenderer.getMainCamera().position();
            double distance = eye.distanceTo(this.centre);
            if (this.shockFront(this.age) < distance) {
                return;
            }
            this.felt = true;
            shocksFelt++;
            float size = Mth.clamp(this.power / BIGGEST_POWER, 0.1F, 1.3F);
            double range = SHAKE_RANGE * Math.sqrt(size);
            if (distance < range) {
                GunCameraSwayHandler.addShake((float) (size * (1.0D - distance / range)));
            }
            if (this.boom) {
                float volume = 8.0F + size * 18.0F;
                mc.level.playLocalSound(this.centre.x, this.centre.y, this.centre.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
                        volume, 0.75F - size * 0.2F, false);
                mc.level.playLocalSound(this.centre.x, this.centre.y, this.centre.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS,
                        volume, 0.5F, false);
                if (distance > 40.0D) {
                    // Far off, the bang arrives as a rolling rumble.
                    mc.level.playLocalSound(this.centre.x, this.centre.y, this.centre.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS,
                            2.0F + size * 8.0F, 0.5F + mc.level.getRandom().nextFloat() * 0.12F, false);
                }
            }
        }

        /** Dust and earth thrown up along the ground at the shock front as it passes. */
        void kickUpDust(Minecraft mc) {
            if (Double.isNaN(this.groundY) || this.age > SHOCK_TICKS) {
                return;
            }
            RandomSource random = mc.level.getRandom();
            double front = this.shockFront(this.age);
            int count = Math.round(12.0F * this.scale);
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double x = this.centre.x + Math.cos(angle) * front;
                double z = this.centre.z + Math.sin(angle) * front;
                double speed = 0.2D + random.nextDouble() * 0.3D;
                if (this.ground != null) {
                    mc.level.addAlwaysVisibleParticle(new BlockParticleOption(ParticleTypes.BLOCK, this.ground), true,
                            x, this.groundY + 0.1D, z, Math.cos(angle) * speed, 0.1D + random.nextDouble() * 0.25D, Math.sin(angle) * speed);
                }
            }
        }

        // ---- Ground: scorch and the dust ring ------------------------------------------------------------------

        /** A band of dust skimming out across the ground just behind the shock front. */
        void renderDustRing(Minecraft mc, VertexConsumer consumer, Matrix4f matrix, Vec3 view, float partialTick) {
            float t = this.age + partialTick;
            if (Double.isNaN(this.groundY) || t >= RING_TICKS) {
                return;
            }
            float progress = t / RING_TICKS;
            double radius = this.shockFront(Math.min(t, SHOCK_TICKS)) * 0.94D;
            double width = 0.8D * this.scale + radius * 0.3D;
            double inner = Math.max(0.0D, radius - width);
            double outer = radius + width * 0.3D;
            float alpha = 0.7F * (1.0F - progress) * Math.min(1.0F, t * 2.0F);
            float daylight = daylight(mc, this.centre, 0.3F);
            ring(consumer, matrix, this.base().subtract(view).add(0.0D, 0.08D, 0.0D), inner, radius, outer,
                    this.dustR * daylight, this.dustG * daylight, this.dustB * daylight, alpha, true);
        }

        // ---- Smoke: stem, skirt, cap, dust wall, condensation dome and debris trails -------------------------

        void collectSmoke(Minecraft mc, List<Quad> quads, Vec3 view, Vector3f forward, float partialTick) {
            float t = this.age + partialTick;
            float daylight = daylight(mc, this.centre, 0.25F);
            Vec3 wind = new Vec3(0.02D, 0.0D, 0.008D);
            Vec3 look = new Vec3(forward.x(), forward.y(), forward.z());
            double floor = this.floor(view);

            for (Puff puff : this.smoke) {
                float tau = t - puff.delay;
                if (tau <= 0.0F || tau > puff.life) {
                    continue;
                }
                float life = tau / puff.life;
                Vec3 at;
                Vec3 middle;
                double size = Math.min(4.8D * this.scale, this.scale * puff.size * (1.0D + tau / 50.0D));
                if (puff.column) {
                    // The stem climbs fast, then slows and spreads as it cools, leaning with the wind.
                    double rise = (4.0D + 7.0D * puff.reach) * this.scale * (1.0D - Math.exp(-tau / 35.0D)) + 0.012D * tau;
                    double spread = (0.3D + 0.8D * puff.reach) * this.scale * (0.4D + rise / (8.0D * this.scale));
                    at = this.centre.add(puff.dir.scale(spread)).add(wind.scale(tau)).add(0.0D, rise, 0.0D);
                    middle = this.centre.add(wind.scale(tau)).add(0.0D, rise - 0.4D * size, 0.0D);
                } else {
                    // The skirt rolls outward along the ground with the blast, then lifts and thins.
                    double reach = 4.2D * this.scale * puff.reach * (1.0D - Math.exp(-tau / 7.0D)) + 0.01D * tau;
                    at = this.centre.add(puff.dir.scale(reach)).add(wind.scale(tau * 0.75D)).add(0.0D, 0.4D * this.scale + 0.012D * tau, 0.0D);
                    middle = this.centre.add(wind.scale(tau * 0.75D)).add(0.0D, -0.5D * size, 0.0D);
                }
                at = this.aboveGround(at, size);
                float grey = 0.13F + 0.3F * Math.min(1.0F, tau / 120.0F) + 0.06F * puff.shade;
                float warm = Math.max(0.0F, 1.0F - tau / 14.0F);
                float alpha = 0.82F * Math.min(1.0F, tau / 5.0F) * (1.0F - life * life);
                float lit = litFrom(at, middle, size, look) * daylight;
                quads.add(new Quad(at.subtract(view), (float) size, puff.rotation + puff.spin * tau,
                        new float[] {(grey + warm * 0.45F) * lit, (grey + warm * 0.18F) * lit, grey * lit * 0.97F},
                        alpha, FULL_BRIGHT, puff.variant(), SMOKE_GRADIENT, floor));
            }

            Vec3 capMiddle = this.capCentre(t, wind);
            for (CapPuff puff : this.cap) {
                if (t > puff.life || t < 2.0F) {
                    continue;
                }
                Vec3 at = this.capPosition(puff, t, wind);
                double size = this.capSize(puff, t);
                float fire = this.capFireWeight(t);
                float life = t / puff.life;
                float alpha = 0.88F * Mth.clamp((t - 2.0F) / 6.0F, 0.0F, 1.0F) * (1.0F - life * life) * (1.0F - fire * 0.75F);
                float grey = 0.12F + 0.3F * Math.min(1.0F, t / 140.0F) + 0.07F * puff.shade;
                // The underside glows with the fire below for a while.
                float underside = Mth.clamp(-Mth.sin(puff.theta(t)), 0.0F, 1.0F);
                float warm = Math.max(0.0F, 1.0F - t / 45.0F) * (0.35F + 0.65F * underside);
                float lit = litFrom(at, capMiddle, size, look) * daylight;
                quads.add(new Quad(at.subtract(view), (float) size, puff.rotation + puff.spin * t,
                        new float[] {(grey + warm * 0.55F) * lit, (grey + warm * 0.22F) * lit, grey * lit},
                        alpha, FULL_BRIGHT, puff.variant(), SMOKE_GRADIENT, floor));
            }

            if (!Double.isNaN(this.groundY)) {
                Vec3 base = this.base();
                for (WallPuff puff : this.wall) {
                    float tau = t - this.wallBirth(puff);
                    if (tau <= 0.0F || tau > puff.life) {
                        continue;
                    }
                    double from = puff.reach * this.shockRadius;
                    double out = from + this.scale * 1.8D * (1.0D - Math.exp(-tau / 10.0D));
                    double size = Math.min(3.4D * this.scale, this.scale * puff.size * (0.6D + 1.3D * (1.0D - Math.exp(-tau / 18.0D))) * (1.2D - 0.4D * puff.reach));
                    double rise = size * 0.6D + 1.2D * this.scale * (1.0D - Math.exp(-tau / 20.0D)) + 0.006D * tau;
                    Vec3 at = new Vec3(this.centre.x + Math.cos(puff.angle) * out, this.groundY + rise, this.centre.z + Math.sin(puff.angle) * out)
                            .add(wind.scale(tau * 0.5D));
                    float life = tau / puff.life;
                    float alpha = 0.72F * Math.min(1.0F, tau / 3.0F) * (1.0F - life * life);
                    float shade = (0.85F + 0.3F * puff.shade) * daylight * litFrom(at, base, size, look);
                    quads.add(new Quad(at.subtract(view), (float) size, puff.rotation + puff.spin * tau,
                            new float[] {this.dustR * shade, this.dustG * shade, this.dustB * shade}, alpha, FULL_BRIGHT, puff.variant(),
                            DUST_GRADIENT, floor));
                }
            }

            // The condensation skirt: a faint white veil low over the shock bubble that bursts out and vanishes.
            float domeTicks = SHOCK_TICKS * 1.1F;
            if (t < domeTicks) {
                float progress = t / domeTicks;
                double front = this.shockFront(t);
                float alpha = 0.1F * (float) Math.pow(1.0F - progress, 1.6D) * Math.min(1.0F, t * 1.5F);
                float white = 0.93F * Math.max(0.6F, daylight);
                Vec3 base = this.base();
                int index = 0;
                for (Vec3 dir : this.shell) {
                    Vec3 at = base.add(dir.x * front, Math.abs(dir.y) * front * (Double.isNaN(this.groundY) && (index & 1) == 0 ? -1.0D : 1.0D), dir.z * front);
                    double size = 0.1D * front + 0.6D * this.scale;
                    quads.add(new Quad(at.subtract(view), (float) size, index * 1.7F, new float[] {white, white, white * 1.02F}, alpha,
                            FULL_BRIGHT, index & 3, SMOKE_GRADIENT, floor));
                    index++;
                }
            }

            // Thin grey trails behind the burning scraps, breaking up and drifting away rather than hanging as thick ropes.
            for (Streamer streamer : this.streamers) {
                int index = 0;
                for (TrailPoint point : streamer.trail) {
                    float tau = t - point.born();
                    if (tau < 0.0F || tau > TRAIL_TICKS) {
                        continue;
                    }
                    index++;
                    float fade = 1.0F - tau / TRAIL_TICKS;
                    double size = (0.24D + 0.03D * tau) * Math.sqrt(this.scale);
                    float grey = (0.3F + 0.22F * Math.min(1.0F, tau / 25.0F)) * daylight;
                    float warm = Math.max(0.0F, 1.0F - tau / 5.0F) * daylight;
                    Vec3 drift = new Vec3(Mth.sin(index * 2.1F + tau * 0.05F), 0.6D, Mth.cos(index * 1.7F + tau * 0.05F)).scale(0.02D * tau);
                    quads.add(new Quad(point.at().add(drift).add(wind.scale(tau)).subtract(view), (float) size, index * 2.39996F,
                            new float[] {grey + warm * 0.5F, grey + warm * 0.22F, grey}, 0.26F * fade * fade, FULL_BRIGHT, (index * 7 + 3) & 3,
                            SMOKE_GRADIENT, floor));
                }
            }
        }

        // ---- Fire: fireball, the cap while it still burns, debris heads ---------------------------------------

        void collectFire(List<Quad> quads, Vec3 view, float partialTick) {
            float t = this.age + partialTick;
            double floor = this.floor(view);
            for (Puff puff : this.fire) {
                if (t > puff.life) {
                    continue;
                }
                // The core burns hottest and longest; the edges cool first and some pockets are already soot.
                float heat = Mth.clamp(t / puff.life + (float) (puff.reach - 0.6D) * 0.35F + (puff.shade < 0.22F ? 0.3F : 0.0F), 0.0F, 1.0F);
                double reach = 2.8D * this.scale * puff.reach * (1.0D - Math.exp(-t / 2.5D));
                Vec3 at = this.centre.add(puff.dir.scale(reach)).add(0.0D, 0.07D * this.scale * t, 0.0D);
                double size = this.scale * puff.size * (0.6D + 1.6D * (1.0D - Math.exp(-t / 3.0D)));
                float alpha = heat < 0.55F ? 1.0F : 1.0F - (heat - 0.55F) / 0.45F;
                at = this.aboveGround(at, size);
                quads.add(new Quad(at.subtract(view), (float) size, puff.rotation + puff.spin * t, fireColour(heat, puff.shade), alpha * 0.95F,
                        FULL_BRIGHT, puff.variant(), FIRE_GRADIENT, floor));
            }
            float capFire = this.capFireWeight(t);
            if (capFire > 0.0F) {
                Vec3 wind = new Vec3(0.02D, 0.0D, 0.008D);
                for (CapPuff puff : this.cap) {
                    if (t < 1.0F) {
                        continue;
                    }
                    float heat = Mth.clamp(t / 28.0F + puff.shade * 0.15F, 0.0F, 1.0F);
                    float alpha = capFire * 0.92F * Mth.clamp((t - 1.0F) / 3.0F, 0.0F, 1.0F);
                    quads.add(new Quad(this.capPosition(puff, t, wind).subtract(view), (float) (this.capSize(puff, t) * 0.92D),
                            puff.rotation + puff.spin * t, fireColour(heat, puff.shade), alpha, FULL_BRIGHT, puff.variant(), FIRE_GRADIENT, floor));
                }
            }
            for (Streamer streamer : this.streamers) {
                if (streamer.dead) {
                    continue;
                }
                float burn = streamer.age / (float) streamer.life;
                Vec3 at = streamer.position(partialTick);
                quads.add(new Quad(at.subtract(view), (float) (0.42D * Math.sqrt(this.scale) * (1.0F - 0.5F * burn)), t * 0.3F,
                        fireColour(0.12F + 0.5F * burn, 0.5F), 1.0F, FULL_BRIGHT, 0, FIRE_GRADIENT, Double.NaN));
            }
        }

        /** The ground's height relative to the camera, which puffs fade into instead of being cut off; NaN in the air. */
        double floor(Vec3 view) {
            return Double.isNaN(this.groundY) ? Double.NaN : this.groundY - view.y;
        }

        /** The middle of the mushroom cap's ring. */
        Vec3 capCentre(float t, Vec3 wind) {
            double height = this.scale * (1.0D + 7.5D * (1.0D - Math.exp(-t / 40.0D))) + 0.01D * t;
            return this.centre.add(0.0D, height, 0.0D).add(wind.scale(t));
        }

        // ---- Light added on top ---------------------------------------------------------------------------------

        void renderGlow(VertexConsumer consumer, Matrix4f matrix, Vec3 view, Vector3f right, Vector3f up, float partialTick) {
            float t = this.age + partialTick;
            Vec3 core = this.centre.add(0.0D, 0.6D * this.scale, 0.0D).subtract(view);

            // The flash: a blinding core, a wide bloom and a streak across the lens.
            if (t < FLASH_TICKS) {
                float fade = 1.0F - t / FLASH_TICKS;
                float size = this.scale * (3.5F + 5.5F * t / FLASH_TICKS);
                disc(consumer, matrix, core, right, up, size * 3.0F, 1.0F, 0.78F, 0.5F, fade * fade * 0.7F);
                disc(consumer, matrix, core, right, up, size, 1.0F, 0.98F, 0.92F, fade * fade);
                streak(consumer, matrix, core, right, up, size * 5.5F, size * 0.11F, 1.0F, 0.86F, 0.72F, fade * fade * 0.65F);
            }

            // The fireball's glare while it burns.
            float burn = this.incendiary ? 34.0F : 24.0F;
            if (t < burn) {
                float strength = 0.6F * (1.0F - t / burn) * Math.min(1.0F, t / 1.5F + 0.3F);
                Vec3 at = this.centre.add(0.0D, (1.0D + 0.08D * t) * this.scale, 0.0D).subtract(view);
                float size = this.scale * (5.0F + 4.0F * (1.0F - (float) Math.exp(-t / 4.0D)));
                disc(consumer, matrix, at, right, up, size, 1.0F, 0.55F, 0.18F, strength);
            }

            // Firelight thrown across the ground; an incendiary keeps flickering as the ground burns.
            if (!Double.isNaN(this.groundY)) {
                float light = t < 16.0F ? 0.55F * (float) Math.pow(1.0F - t / 16.0F, 1.3D) : 0.0F;
                if (this.incendiary && t < 140.0F) {
                    light = Math.max(light, 0.22F * (1.0F - t / 140.0F) * (0.8F + 0.2F * Mth.sin(t * 1.7F) * Mth.sin(t * 0.63F)));
                }
                if (light > 0.003F) {
                    double reach = this.radius * 1.3D + 5.0D * this.scale;
                    Vec3 base = this.base().subtract(view).add(0.0D, 0.06D, 0.0D);
                    ring(consumer, matrix, base, 0.0D, 0.0D, reach, 1.0F, 0.52F, 0.18F, light, false);
                }

                // A sharp bright ring at the shock front, tearing out across the ground.
                if (t < SHOCK_TICKS) {
                    float progress = t / SHOCK_TICKS;
                    double front = this.shockFront(t);
                    double width = 0.5D + 0.07D * front;
                    Vec3 base = this.base().subtract(view).add(0.0D, 0.1D, 0.0D);
                    ring(consumer, matrix, base, Math.max(0.0D, front - width), front, front + width * 0.25D,
                            1.0F, 0.93F, 0.82F, 0.6F * (float) Math.pow(1.0F - progress, 1.2D), false);
                }
            }

            // The shock bubble's rim, where the light bends most.
            if (t < SHOCK_TICKS) {
                this.renderShockBubble(consumer, matrix, view, t);
            }

            for (Spark spark : this.sparks) {
                if (spark.age >= spark.life) {
                    continue;
                }
                float f = (spark.age + partialTick) / spark.life;
                Vec3 head = spark.position(partialTick).subtract(view);
                Vec3 tail = head.subtract(spark.velocity.scale(2.2D));
                line(consumer, matrix, head, tail, 0.05D * Math.sqrt(this.scale), 1.0F, 0.88F - 0.3F * f, 0.55F - 0.4F * f, 1.0F - f);
            }

            for (Streamer streamer : this.streamers) {
                if (!streamer.dead) {
                    float fade = 1.0F - streamer.age / (float) streamer.life;
                    disc(consumer, matrix, streamer.position(partialTick).subtract(view), right, up,
                            (float) (1.4D * Math.sqrt(this.scale)), 1.0F, 0.6F, 0.25F, 0.45F * fade);
                }
            }
        }

        /** A translucent bubble of compressed air, bright at its rim, racing outward in the first few ticks. */
        void renderShockBubble(VertexConsumer consumer, Matrix4f matrix, Vec3 view, float t) {
            float progress = t / SHOCK_TICKS;
            double bubble = this.shockFront(t);
            float strength = 0.3F * (1.0F - progress);
            Vec3 c = this.base().subtract(view);
            boolean full = Double.isNaN(this.groundY);
            int rings = 12;
            int segments = 32;
            double from = full ? -Math.PI / 2.0D : -0.1D;
            for (int ring = 0; ring < rings; ring++) {
                double a0 = from + (Math.PI / 2.0D - from) * ring / rings;
                double a1 = from + (Math.PI / 2.0D - from) * (ring + 1) / rings;
                for (int seg = 0; seg < segments; seg++) {
                    double b0 = Math.PI * 2.0D * seg / segments;
                    double b1 = Math.PI * 2.0D * (seg + 1) / segments;
                    shellVertex(consumer, matrix, c, sphere(a0, b0), bubble, strength);
                    shellVertex(consumer, matrix, c, sphere(a0, b1), bubble, strength);
                    shellVertex(consumer, matrix, c, sphere(a1, b1), bubble, strength);
                    shellVertex(consumer, matrix, c, sphere(a1, b0), bubble, strength);
                }
            }
        }

        // ---- The mushroom cap -----------------------------------------------------------------------------------

        /** Burning at first, it is still the fireball as it starts to rise; then it cools into smoke. */
        float capFireWeight(float t) {
            float burn = this.incendiary ? 34.0F : 24.0F;
            return Mth.clamp(1.0F - (t - 2.0F) / burn, 0.0F, 1.0F);
        }

        /**
         * A ring vortex: rising, widening and rolling over, so the smoke flows up the middle, out over the top and
         * down the outside.
         */
        Vec3 capPosition(CapPuff puff, float t, Vec3 wind) {
            double grow = 1.0D - Math.exp(-t / 30.0D);
            double ring = this.scale * (0.6D + 2.4D * grow);
            double tube = this.scale * (0.5D + 1.3D * grow);
            float theta = puff.theta(t);
            double out = ring + tube * Mth.cos(theta);
            return this.capCentre(t, wind).add(Math.cos(puff.phi) * out, tube * Mth.sin(theta), Math.sin(puff.phi) * out);
        }

        double capSize(CapPuff puff, float t) {
            return this.scale * puff.size * (0.9D + 1.0D * (1.0D - Math.exp(-t / 35.0D)));
        }

        /** When the shock front reaches this dust puff's spot on the ground. */
        float wallBirth(WallPuff puff) {
            // Inverse of the front's ease-out over the visible shock.
            float x = 1.0F - (float) Math.cbrt(1.0F - Math.min(0.999F, puff.reach));
            return x * SHOCK_TICKS;
        }

        private static Vec3 sphere(double elevation, double azimuth) {
            double horizontal = Math.cos(elevation);
            return new Vec3(horizontal * Math.cos(azimuth), Math.sin(elevation), horizontal * Math.sin(azimuth));
        }

        private static void shellVertex(VertexConsumer consumer, Matrix4f matrix, Vec3 centre, Vec3 normal, double radius, float strength) {
            Vec3 at = centre.add(normal.scale(radius));
            // Seen face-on the bubble is clear; along its edge the light bends most, so it shows there.
            double distance = at.length();
            double facing = distance < 1.0E-4D ? 1.0D : Math.abs(normal.dot(at.scale(1.0D / distance)));
            // Where the bubble sweeps past the camera it is seen edge-on right in front of the eye: it would flash as a
            // bright sheet across the view, so it thins out there.
            float near = (float) Mth.clamp((distance - 2.0D) / 8.0D, 0.0D, 1.0D);
            float alpha = strength * near * (float) Math.pow(1.0D - facing, 2.0D);
            consumer.addVertex(matrix, (float) at.x, (float) at.y, (float) at.z).setColor(1.0F, 0.97F, 0.92F, alpha);
        }
    }

    /** How brightly the world lights the smoke there. */
    private static float daylight(Minecraft mc, Vec3 at, float floor) {
        return Mth.clamp(mc.level.getMaxLocalRawBrightness(BlockPos.containing(at).above(2)) / 15.0F, floor, 1.0F);
    }

    /**
     * A flat ring on the ground: transparent at {@code inner}, full {@code alpha} at {@code mid}, transparent again at
     * {@code outer}. With {@code inner == mid == 0} it is a disc fading outward. Textured (the soft sprite's centre) for
     * the lit layers, or plain coloured vertices for added light.
     */
    private static void ring(VertexConsumer consumer, Matrix4f matrix, Vec3 centre, double inner, double mid, double outer,
                             float r, float g, float b, float alpha, boolean textured) {
        if (alpha <= 0.003F) {
            return;
        }
        int segments = 56;
        boolean disc = mid <= 1.0E-3D;
        for (int seg = 0; seg < segments; seg++) {
            double a0 = Math.PI * 2.0D * seg / segments;
            double a1 = Math.PI * 2.0D * (seg + 1) / segments;
            double c0 = Math.cos(a0);
            double s0 = Math.sin(a0);
            double c1 = Math.cos(a1);
            double s1 = Math.sin(a1);
            if (!disc) {
                flatVertex(consumer, matrix, centre, c0 * inner, s0 * inner, r, g, b, 0.0F, textured);
                flatVertex(consumer, matrix, centre, c1 * inner, s1 * inner, r, g, b, 0.0F, textured);
                flatVertex(consumer, matrix, centre, c1 * mid, s1 * mid, r, g, b, alpha, textured);
                flatVertex(consumer, matrix, centre, c0 * mid, s0 * mid, r, g, b, alpha, textured);
            }
            flatVertex(consumer, matrix, centre, c0 * mid, s0 * mid, r, g, b, alpha, textured);
            flatVertex(consumer, matrix, centre, c1 * mid, s1 * mid, r, g, b, alpha, textured);
            flatVertex(consumer, matrix, centre, c1 * outer, s1 * outer, r, g, b, 0.0F, textured);
            flatVertex(consumer, matrix, centre, c0 * outer, s0 * outer, r, g, b, 0.0F, textured);
        }
    }

    private static void flatVertex(VertexConsumer consumer, Matrix4f matrix, Vec3 centre, double dx, double dz,
                                   float r, float g, float b, float alpha, boolean textured) {
        if (textured) {
            groundVertex(consumer, matrix, centre.x + dx, centre.y, centre.z + dz, r, g, b, alpha, 0.5F, 0.5F);
        } else {
            consumer.addVertex(matrix, (float) (centre.x + dx), (float) centre.y, (float) (centre.z + dz)).setColor(r, g, b, alpha);
        }
    }

    private static void groundVertex(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z,
                                     float r, float g, float b, float alpha, float u, float v) {
        consumer.addVertex(matrix, (float) x, (float) y, (float) z)
                .setColor(r, g, b, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /** A bright line from {@code head} to {@code tail}, turned to face the camera and fading towards the tail. */
    private static void line(VertexConsumer consumer, Matrix4f matrix, Vec3 head, Vec3 tail, double width,
                             float r, float g, float b, float alpha) {
        if (alpha <= 0.003F) {
            return;
        }
        Vec3 along = head.subtract(tail);
        Vec3 side = along.cross(head.add(tail).scale(0.5D));
        if (side.lengthSqr() < 1.0E-10D) {
            return;
        }
        side = side.normalize().scale(width);
        consumer.addVertex(matrix, (float) (tail.x - side.x), (float) (tail.y - side.y), (float) (tail.z - side.z)).setColor(r, g, b, 0.0F);
        consumer.addVertex(matrix, (float) (head.x - side.x), (float) (head.y - side.y), (float) (head.z - side.z)).setColor(r, g, b, alpha);
        consumer.addVertex(matrix, (float) (head.x + side.x), (float) (head.y + side.y), (float) (head.z + side.z)).setColor(r, g, b, alpha);
        consumer.addVertex(matrix, (float) (tail.x + side.x), (float) (tail.y + side.y), (float) (tail.z + side.z)).setColor(r, g, b, 0.0F);
    }

    /** A thin horizontal streak across the view, brightest in the middle: a lens flare off the flash. */
    private static void streak(VertexConsumer consumer, Matrix4f matrix, Vec3 at, Vector3f right, Vector3f up,
                               float halfLength, float halfHeight, float r, float g, float b, float alpha) {
        if (alpha <= 0.003F) {
            return;
        }
        float x = (float) at.x;
        float y = (float) at.y;
        float z = (float) at.z;
        for (int side = -1; side <= 1; side += 2) {
            float ex = right.x() * halfLength * side;
            float ey = right.y() * halfLength * side;
            float ez = right.z() * halfLength * side;
            float ux = up.x() * halfHeight;
            float uy = up.y() * halfHeight;
            float uz = up.z() * halfHeight;
            consumer.addVertex(matrix, x - ux, y - uy, z - uz).setColor(r, g, b, alpha);
            consumer.addVertex(matrix, x + ex - ux * 0.2F, y + ey - uy * 0.2F, z + ez - uz * 0.2F).setColor(r, g, b, 0.0F);
            consumer.addVertex(matrix, x + ex + ux * 0.2F, y + ey + uy * 0.2F, z + ez + uz * 0.2F).setColor(r, g, b, 0.0F);
            consumer.addVertex(matrix, x + ux, y + uy, z + uz).setColor(r, g, b, alpha);
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

    /** A puff of the mushroom cap: where it sits around the ring ({@code phi}) and on the rolling tube ({@code theta}). */
    private record CapPuff(float phi, float theta0, float size, float shade, int life, float rotation, float spin) {
        float theta(float t) {
            // Rolling over the top and down the outside, slowing as the cap cools.
            return this.theta0 - 1.8F * (1.0F - (float) Math.exp(-t / 50.0D));
        }

        int variant() {
            return Math.min(3, (int) (this.shade * 4.0F));
        }
    }

    /** A puff of the dust wall, raised where the shock front crosses {@code reach} of the way out. */
    private record WallPuff(float angle, float reach, float size, float shade, int life, float rotation, float spin) {
        int variant() {
            return Math.min(3, (int) (this.shade * 4.0F));
        }
    }

    private record TrailPoint(Vec3 at, int born) {}


    /** A burning scrap on a ballistic arc, leaving smoke behind it until it lands or burns out. */
    private static final class Streamer {
        Vec3 position;
        Vec3 velocity;
        final int life;
        int age;
        boolean dead;
        final ArrayDeque<TrailPoint> trail = new ArrayDeque<>();

        Streamer(Vec3 position, Vec3 velocity, int life) {
            this.position = position;
            this.velocity = velocity;
            this.life = life;
        }

        void tick(int blastAge, double groundY) {
            while (!this.trail.isEmpty() && blastAge - this.trail.peekFirst().born() > TRAIL_TICKS) {
                this.trail.pollFirst();
            }
            if (this.dead) {
                return;
            }
            this.age++;
            Vec3 from = this.position;
            this.velocity = this.velocity.scale(0.985D).add(0.0D, -0.035D, 0.0D);
            this.position = this.position.add(this.velocity);
            // Several puffs along each tick's flight, so the trail is a continuous ribbon of smoke, not a string of beads.
            for (int i = 1; i <= TRAIL_STEPS; i++) {
                this.trail.addLast(new TrailPoint(from.lerp(this.position, i / (double) TRAIL_STEPS), blastAge));
            }
            if (this.age >= this.life || (!Double.isNaN(groundY) && this.position.y <= groundY && this.velocity.y < 0.0D)) {
                this.dead = true;
            }
        }

        Vec3 position(float partialTick) {
            return this.position.add(this.velocity.scale(partialTick));
        }
    }

    private static final class Spark {
        Vec3 position;
        Vec3 velocity;
        final int life;
        int age;

        Spark(Vec3 position, Vec3 velocity, int life) {
            this.position = position;
            this.velocity = velocity;
            this.life = life;
        }

        void tick() {
            if (this.age >= this.life) {
                return;
            }
            this.age++;
            this.position = this.position.add(this.velocity);
            this.velocity = this.velocity.scale(0.93D).add(0.0D, -0.05D, 0.0D);
        }

        Vec3 position(float partialTick) {
            return this.position.add(this.velocity.scale(partialTick));
        }
    }

    /**
     * One puff to draw. {@code gradient} shades it from below to above: positive is lit from the sky (a darker,
     * self-shadowed underside), negative from below (fire under smoke).
     */
    private record Quad(Vec3 at, float size, float rotation, float[] colour, float alpha, int light, int variant, float gradient, double floor) {
        /** The same puff shrunk towards the camera by {@code factor}: positions, size and the ground below it alike. */
        Quad scaled(float factor) {
            return new Quad(this.at.scale(factor), this.size * factor, this.rotation, this.colour, this.alpha, this.light,
                    this.variant, this.gradient, this.floor * factor);
        }
    }

    /** The direction light falls on the smoke from: high and to one side, like the sun. */
    private static final Vec3 LIGHT = new Vec3(0.5D, 0.78D, 0.38D).normalize();

    /**
     * How brightly a puff is lit for where it sits in its cloud: puffs on the sunny side of the column or cap are lighter,
     * those on the far side and underneath darker, and puffs behind the middle of the cloud (seen through it) a little
     * darker again. That shading across the cloud is what makes it read as a solid billow rather than a flat cut-out.
     */
    private static float litFrom(Vec3 at, Vec3 middle, double size, Vec3 look) {
        Vec3 out = at.subtract(middle);
        double length = out.length();
        if (length < 1.0E-3D) {
            return 1.0F;
        }
        double weight = Math.min(1.0D, length / Math.max(0.5D, size * 0.8D));
        double sun = out.scale(1.0D / length).dot(LIGHT) * weight;
        double behind = Mth.clamp(out.dot(look) / Math.max(0.5D, size), 0.0D, 1.0D);
        return (float) Mth.clamp(0.94D + 0.36D * sun - 0.16D * behind, 0.5D, 1.35D);
    }

    /**
     * Back to front, so overlapping translucent puffs blend correctly. A puff stays solid until it is about to reach the
     * camera, then thins smoothly to nothing just before it passes the eye, so smoke rolling over a helicopter surrounds
     * it instead of vanishing (it used to thin to a faint haze anywhere within two puff-widths) or popping out of view.
     */
    private static void drawSorted(VertexConsumer consumer, Matrix4f matrix, Vector3f right, Vector3f up, Vector3f forward, List<Quad> quads) {
        quads.sort(Comparator.comparingDouble((Quad quad) -> quad.at().lengthSqr()).reversed());
        for (Quad quad : quads) {
            Vec3 at = quad.at();
            double depth = at.x * forward.x() + at.y * forward.y() + at.z * forward.z();
            float near = (float) Mth.clamp((depth - 0.3D) / Math.max(0.6D, quad.size() * 0.9D), 0.0D, 1.0D);
            near = near * near * (3.0F - 2.0F * near);
            billboard(consumer, matrix, at, right, up, quad.size(), quad.rotation(),
                    quad.colour()[0], quad.colour()[1], quad.colour()[2], quad.alpha() * near, quad.light(), quad.variant(), quad.gradient(),
                    quad.floor());
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

    /** The soft round sprite, for points of light that have to show against a bright sky (see {@link #dot}). */
    static Identifier softTexture() {
        return SOFT_TEXTURE;
    }

    /**
     * A soft round spot facing the camera, drawn with the soft sprite and ordinary blending. Added light (see
     * {@link #disc}) washes out to nothing against a pale daytime sky; this stays a visible spot of colour.
     */
    static void dot(VertexConsumer consumer, Matrix4f matrix, Vec3 at, Vector3f right, Vector3f up, float size,
                    float r, float g, float b, float alpha) {
        if (alpha <= 0.003F) {
            return;
        }
        float ax = right.x() * size;
        float ay = right.y() * size;
        float az = right.z() * size;
        float bx = up.x() * size;
        float by = up.y() * size;
        float bz = up.z() * size;
        float x = (float) at.x;
        float y = (float) at.y;
        float z = (float) at.z;
        corner(consumer, matrix, x - ax - bx, y - ay - by, z - az - bz, r, g, b, alpha, 0.0F, 1.0F, FULL_BRIGHT);
        corner(consumer, matrix, x + ax - bx, y + ay - by, z + az - bz, r, g, b, alpha, 1.0F, 1.0F, FULL_BRIGHT);
        corner(consumer, matrix, x + ax + bx, y + ay + by, z + az + bz, r, g, b, alpha, 1.0F, 0.0F, FULL_BRIGHT);
        corner(consumer, matrix, x - ax + bx, y - ay + by, z - az + bz, r, g, b, alpha, 0.0F, 0.0F, FULL_BRIGHT);
    }

    static void billboard(VertexConsumer consumer, Matrix4f matrix, Vec3 at, Vector3f right, Vector3f up, float size,
                                  float rotation, float r, float g, float b, float alpha, int light, int variant) {
        billboard(consumer, matrix, at, right, up, size, rotation, r, g, b, alpha, light, variant, 0.0F, Double.NaN);
    }

    /**
     * A camera-facing puff. With a {@code gradient} its corners are shaded by how high they sit, so it reads as a
     * lit, rounded billow rather than a flat cut-out.
     */
    static void billboard(VertexConsumer consumer, Matrix4f matrix, Vec3 at, Vector3f right, Vector3f up, float size,
                                  float rotation, float r, float g, float b, float alpha, int light, int variant, float gradient) {
        billboard(consumer, matrix, at, right, up, size, rotation, r, g, b, alpha, light, variant, gradient, Double.NaN);
    }

    /**
     * As above; with a {@code floor} (the height of the ground relative to the camera) the part of the puff that reaches
     * down to the ground fades out towards it, instead of the ground slicing it off in a hard straight line.
     */
    static void billboard(VertexConsumer consumer, Matrix4f matrix, Vec3 at, Vector3f right, Vector3f up, float size,
                                  float rotation, float r, float g, float b, float alpha, int light, int variant, float gradient, double floor) {
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
        float inv = size <= 1.0E-4F ? 0.0F : gradient / (size * 1.4142F);
        float s0 = 1.0F + (-ay - by) * inv;
        float s1 = 1.0F + (ay - by) * inv;
        float s2 = 1.0F + (ay + by) * inv;
        float s3 = 1.0F + (-ay + by) * inv;
        float a0 = alpha * groundFade(y - ay - by, floor, size);
        float a1 = alpha * groundFade(y + ay - by, floor, size);
        float a2 = alpha * groundFade(y + ay + by, floor, size);
        float a3 = alpha * groundFade(y - ay + by, floor, size);
        corner(consumer, matrix, x - ax - bx, y - ay - by, z - az - bz, r * s0, g * s0, b * s0, a0, u0, v0 + 0.5F, light);
        corner(consumer, matrix, x + ax - bx, y + ay - by, z + az - bz, r * s1, g * s1, b * s1, a1, u0 + 0.5F, v0 + 0.5F, light);
        corner(consumer, matrix, x + ax + bx, y + ay + by, z + az + bz, r * s2, g * s2, b * s2, a2, u0 + 0.5F, v0, light);
        corner(consumer, matrix, x - ax + bx, y - ay + by, z - az + bz, r * s3, g * s3, b * s3, a3, u0, v0, light);
    }

    /** A puff corner at or below the ground is clear, rising to full strength most of a puff-width above it. */
    private static float groundFade(float cornerY, double floor, float size) {
        if (Double.isNaN(floor)) {
            return 1.0F;
        }
        return (float) Mth.clamp((cornerY - floor) / Math.max(0.3D, size * 0.9D), 0.0D, 1.0D);
    }

    private static void corner(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                               float r, float g, float b, float alpha, float u, float v, int light) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(Math.min(1.0F, r), Math.min(1.0F, g), Math.min(1.0F, b), alpha)
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
        int cellSize = SPRITE_CELL;
        NativeImage atlas = new NativeImage(cellSize * 2, cellSize * 2, false);
        for (int variant = 0; variant < 4; variant++) {
            NativeImage cell = cloudImage(cellSize, 1337L + variant * 7919L);
            for (int y = 0; y < cellSize; y++) {
                for (int x = 0; x < cellSize; x++) {
                    atlas.setPixel((variant & 1) * cellSize + x, ((variant >> 1) & 1) * cellSize + y, cell.getPixel(x, y));
                }
            }
            cell.close();
        }
        mc.getTextureManager().register(PUFF_TEXTURE, new SmoothTexture(PUFF_TEXTURE::toString, atlas));
        mc.getTextureManager().register(SOFT_TEXTURE, new SmoothTexture(SOFT_TEXTURE::toString, softImage(64)));
        texturesReady = true;
    }

    /**
     * A texture sampled smoothly (bilinear) rather than as crisp texels: smoke and glow are soft, and blocky stair-step
     * edges on a puff filling the view are what made the blast look like a game sprite.
     */
    private static final class SmoothTexture extends DynamicTexture {
        SmoothTexture(java.util.function.Supplier<String> label, NativeImage image) {
            super(label, image);
            this.sampler = com.mojang.blaze3d.systems.RenderSystem.getSamplerCache()
                    .getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);
        }
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

    /** A soft round glow: opaque in the middle, fading to nothing at the edge. Its centre texel is solid white. */
    private static NativeImage softImage(int size) {
        NativeImage image = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = (x + 0.5F) / size * 2.0F - 1.0F;
                float dy = (y + 0.5F) / size * 2.0F - 1.0F;
                float d = Math.min(1.0F, Mth.sqrt(dx * dx + dy * dy));
                // Flat-topped, then falling away smoothly.
                float alpha = Mth.clamp((1.0F - d) * 1.6F, 0.0F, 1.0F);
                alpha = alpha * alpha * (3.0F - 2.0F * alpha);
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
        for (int cells = 4; cells <= Math.min(64, size / 2); cells *= 2) {
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
