package ttv.migami.jeg.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ExplodeParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

public final class GunMuzzleFlashParticle extends ExplodeParticle {
    private final SpriteSet sprites;

    private GunMuzzleFlashParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            SpriteSet sprites,
            float sizeScale
    ) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        this.sprites = sprites;
        this.lifetime = sizeScale < 1.0F ? 1 : 2;
        this.quadSize = (1.15F + this.random.nextFloat() * 0.25F) * sizeScale;
        this.alpha = sizeScale < 1.0F ? 0.5F : 1.0F;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = xSpeed * 0.1D;
        this.yd = ySpeed * 0.1D;
        this.zd = zSpeed * 0.1D;
        this.setSpriteFromAge(sprites);
        this.roll = this.random.nextFloat() * ((float) Math.PI * 2.0F);
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return 15728880;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.setSpriteFromAge(this.sprites);
            this.alpha *= 0.55F;
            this.quadSize *= 0.92F;
        }
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        private final float sizeScale;

        public Provider(SpriteSet sprites) {
            this(sprites, 1.0F);
        }

        public Provider(SpriteSet sprites, float sizeScale) {
            this.sprites = sprites;
            this.sizeScale = sizeScale;
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
            return new GunMuzzleFlashParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites, this.sizeScale);
        }
    }
}
