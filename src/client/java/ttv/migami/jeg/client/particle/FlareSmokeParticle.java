package ttv.migami.jeg.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ExplodeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import ttv.migami.jeg.particle.ColoredFlareOption;

public final class FlareSmokeParticle extends ExplodeParticle {
    /** Flares glow (full bright); a smoke screen is lit by the world, or shaders tint it as block light and it glows at night. */
    private boolean glows = true;

    private FlareSmokeParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            SpriteSet sprites,
            float red,
            float green,
            float blue,
            float sizeMultiplier,
            int minLifetime,
            int randomLifetime
    ) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        this.setColor(red, green, blue);
        this.quadSize *= sizeMultiplier;
        this.lifetime = minLifetime + this.random.nextInt(Math.max(1, randomLifetime));
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return this.glows ? 240 : super.getLightCoords(partialTick);
    }

    /** A smoke shell's screen (SmokeGrenadeEntity#smokeScreen): big grey puffs that hang 10 to 16 seconds. */
    public static final class ScreenProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public ScreenProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
            FlareSmokeParticle particle = new FlareSmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites,
                    0.82F, 0.82F, 0.8F, 7.0F, 200, 121);
            particle.glows = false;
            return particle;
        }
    }

    public static final class SmokeProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public SmokeProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed,
                RandomSource random
        ) {
            return new FlareSmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites,
                    1.0F, 0.2F, 0.2F, 6.0F, 120, 121);
        }
    }

    public static final class RedProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public RedProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed,
                RandomSource random
        ) {
            return new FlareSmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites,
                    1.0F, 0.2F, 0.2F, 5.0F, 80, 81);
        }
    }

    public static final class BlueProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public BlueProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed,
                RandomSource random
        ) {
            return new FlareSmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites,
                    0.0F, 0.6F, 0.8F, 5.0F, 80, 81);
        }
    }

    public static final class ColoredProvider implements ParticleProvider<ColoredFlareOption> {
        private final SpriteSet sprites;

        public ColoredProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                ColoredFlareOption option,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed,
                RandomSource random
        ) {
            return new FlareSmokeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites,
                    option.red(), option.green(), option.blue(), 6.0F, 120, 121);
        }
    }
}
