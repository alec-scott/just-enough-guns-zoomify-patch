package ttv.migami.jeg.vehicle.projectile;

import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.particle.ColoredFlareOption;

public final class VehicleDecoyEntity extends Entity {
    private static final EntityDataAccessor<Boolean> DATA_SMOKE = SynchedEntityData.defineId(VehicleDecoyEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int FLARE_LIFE_TICKS = 200;
    private static final float FLARE_FADE_TICKS = 50.0F;
    /** A flare's smoke: off-white. */
    private static final int TRAIL_COLOUR = 0xFFFFFF;
    private static final int SMOKE_LIFE_TICKS = 400;
    private static final int SMOKE_IGNITE_TICKS = 4;
    private static final double SMOKE_BLOCK_RANGE = 12.0D;
    private static final double SMOKE_THICKNESS_MULTIPLIER = 1.5D;

    public VehicleDecoyEntity(EntityType<? extends VehicleDecoyEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    private VehicleDecoyEntity(Level level, Vec3 position, Vec3 velocity, boolean smoke) {
        this(ModEntities.VEHICLE_DECOY.get(), level);
        this.entityData.set(DATA_SMOKE, smoke);
        this.setPos(position);
        this.setDeltaMovement(velocity);
    }

    public static VehicleDecoyEntity flare(Level level, Entity shooter, Vec3 position, Vec3 direction, float velocity, float inaccuracy) {
        VehicleDecoyEntity decoy = new VehicleDecoyEntity(level, position, Vec3.ZERO, false);
        decoy.shootFrom(shooter, direction, velocity, inaccuracy);
        return decoy;
    }

    public static VehicleDecoyEntity smoke(Level level, Entity shooter, Vec3 position, Vec3 direction, float velocity, float inaccuracy) {
        VehicleDecoyEntity decoy = new VehicleDecoyEntity(level, position, Vec3.ZERO, true);
        decoy.shootFrom(shooter, direction, velocity, inaccuracy);
        return decoy;
    }

    public static Optional<VehicleDecoyEntity> findNearest(Level level, Vec3 position, double range) {
        AABB area = new AABB(position, position).inflate(range);
        return level.getEntitiesOfClass(VehicleDecoyEntity.class, area).stream()
                .min(Comparator.comparingDouble(decoy -> decoy.distanceToSqr(position)));
    }

    public static boolean isSmokeBlockingTarget(Entity target) {
        AABB area = target.getBoundingBox().inflate(SMOKE_BLOCK_RANGE);
        return !target.level().getEntitiesOfClass(VehicleDecoyEntity.class, area, VehicleDecoyEntity::isSmokeDecoy).isEmpty();
    }

    public boolean isSmokeDecoy() {
        return this.entityData.get(DATA_SMOKE);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SMOKE, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isSmokeDecoy()) {
            this.tickSmoke();
            return;
        }
        this.tickFlare();
    }

    private void tickFlare() {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.02D, 0.0D));
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (this.level().isClientSide()) {
            this.flareParticles();
        }
        if (this.tickCount > FLARE_LIFE_TICKS || this.isInWater() || this.onGround()) {
            this.discard();
        }
    }

    /**
     * A flare burning: thick white smoke left hanging along its path (the arcs a salvo draws across the sky), white
     * sparks shedding off it and falling, a lick of flame at its heart; a spray of sparks as it ignites.
     */
    private void flareParticles() {
        float burn = this.flareBurn(1.0F);
        double x = this.getX();
        double y = this.getY() + 0.17D;
        double z = this.getZ();
        if (this.tickCount <= 2) {
            for (int i = 0; i < 14; i++) {
                this.level().addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, x, y, z,
                        this.random.nextGaussian() * 0.22D, this.random.nextGaussian() * 0.18D + 0.05D, this.random.nextGaussian() * 0.22D);
            }
        }
        if (burn <= 0.05F) {
            return;
        }
        // The smoke trail, drawn back along this tick's path so it stays unbroken at speed.
        double px = this.xo;
        double py = this.yo + 0.17D;
        double pz = this.zo;
        int puffs = this.getDeltaMovement().length() > 0.8D ? 2 : 1;
        for (int i = 0; i < puffs; i++) {
            double t = (i + this.random.nextDouble() * 0.5D) / puffs;
            this.level().addAlwaysVisibleParticle(new ColoredFlareOption(TRAIL_COLOUR), true,
                    Mth.lerp(t, px, x), Mth.lerp(t, py, y), Mth.lerp(t, pz, z),
                    this.random.nextGaussian() * 0.004D, 0.006D, this.random.nextGaussian() * 0.004D);
        }
        // Fresh smoke is bright white right behind the flare, greying as it hangs.
        this.level().addAlwaysVisibleParticle(ParticleTypes.CLOUD, true, px, py, pz,
                this.random.nextGaussian() * 0.01D, 0.01D, this.random.nextGaussian() * 0.01D);
        int sparks = burn > 0.5F ? 2 : 1;
        for (int i = 0; i < sparks; i++) {
            this.level().addAlwaysVisibleParticle(ParticleTypes.FIREWORK, true, x, y, z,
                    this.random.nextGaussian() * 0.06D, -0.04D + this.random.nextGaussian() * 0.03D, this.random.nextGaussian() * 0.06D);
        }
        if (this.random.nextFloat() < 0.5F) {
            this.level().addParticle(ParticleTypes.FLAME, x, y, z, this.random.nextGaussian() * 0.01D, 0.01D, this.random.nextGaussian() * 0.01D);
        }
    }

    /** How brightly a flare still burns: fully for most of its life, dying away over its last two and a half seconds. */
    public float flareBurn(float partialTick) {
        if (this.isSmokeDecoy()) {
            return 0.0F;
        }
        float age = this.tickCount + partialTick;
        return Mth.clamp((FLARE_LIFE_TICKS - age) / FLARE_FADE_TICKS, 0.0F, 1.0F);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // A burning flare lights the sky a long way off.
        return distance < 320.0D * 320.0D;
    }

    private void tickSmoke() {
        this.move(MoverType.SELF, this.getDeltaMovement());
        if (this.tickCount == SMOKE_IGNITE_TICKS) {
            this.setDeltaMovement(Vec3.ZERO);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY(), this.getZ(), 240, 6.0D, 3.2D, 6.0D, 0.08D);
                serverLevel.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY(), this.getZ(), 30, 0.25D, 0.25D, 0.25D, 0.12D);
            }
        }
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel && this.tickCount > SMOKE_IGNITE_TICKS && this.tickCount % 5 == 0) {
            double radius = this.smokeRadius();
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY() + 0.5D, this.getZ(), 72, radius, 2.8D, radius, 0.015D);
        }
        if (this.level().isClientSide()) {
            for (int i = 0; i < 10; i++) {
                double radius = this.smokeRadius();
                double x = this.getX() + (this.random.nextDouble() - 0.5D) * radius * 2.0D;
                double y = this.getY() + this.random.nextDouble() * 5.6D;
                double z = this.getZ() + (this.random.nextDouble() - 0.5D) * radius * 2.0D;
                this.level().addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, this.random.nextGaussian() * 0.01D, 0.015D, this.random.nextGaussian() * 0.01D);
            }
        }
        if (this.tickCount > SMOKE_LIFE_TICKS) {
            this.discard();
        }
    }

    private double smokeRadius() {
        return Mth.clamp(1.6D + (this.tickCount - SMOKE_IGNITE_TICKS) / 15.0D, 1.6D, 7.0D) * SMOKE_THICKNESS_MULTIPLIER;
    }

    private void shootFrom(Entity shooter, Vec3 direction, float velocity, float inaccuracy) {
        Vec3 randomOffset = new Vec3(
                this.random.triangle(0.0D, 0.0172275D * inaccuracy),
                this.random.triangle(0.0D, 0.0172275D * inaccuracy),
                this.random.triangle(0.0D, 0.0172275D * inaccuracy)
        );
        Vec3 shot = direction.normalize().add(randomOffset).normalize().scale(velocity);
        this.setDeltaMovement(shooter.getDeltaMovement().scale(0.75D).add(shot));
        double horizontal = shot.horizontalDistance();
        this.setYRot((float) (Mth.atan2(shot.x, shot.z) * Mth.RAD_TO_DEG));
        this.setXRot((float) (Mth.atan2(shot.y, horizontal) * Mth.RAD_TO_DEG));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.entityData.set(DATA_SMOKE, input.getBooleanOr("Smoke", false));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putBoolean("Smoke", this.isSmokeDecoy());
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource source, float amount) {
        return false;
    }
}
