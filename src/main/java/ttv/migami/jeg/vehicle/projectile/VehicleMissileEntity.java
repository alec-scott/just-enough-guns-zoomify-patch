package ttv.migami.jeg.vehicle.projectile;

import ttv.migami.jeg.network.CameraShakePayload;
import ttv.migami.jeg.network.MissileBlastPayload;
import net.minecraft.util.random.WeightedList;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.material.FluidState;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.init.ModParticleTypes;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.VehicleMissileProfile;

public final class VehicleMissileEntity extends Entity {
    private static final double DECOY_SEEK_RANGE = 32.0D;
    private static final float VEHICLE_DIRECT_HIT_BLOCK_DAMAGE_SCALE = 0.3F;
    private static final double VEHICLE_DIRECT_HIT_FALLOFF_DISTANCE_SCALE = 1.7D;
    private static final int BMP2_NO_DROP_TICKS = 5;

    /** Share of top speed a missile comes off the rail at, before the motor builds it up. */
    public static final double LAUNCH_SPEED_SHARE = 0.25D;
    private static final int MOTOR_BUILD_TICKS = 14;
    private static final float BIGGEST_BLAST = 9.0F;
    private static final double SHAKE_RANGE = 140.0D;
    private static final double BLAST_VISIBLE_RANGE = 512.0D;
    /**
     * How much of a block's blast resistance a warhead has to overcome. Medium missiles punch through stone and brick
     * walls; a large one flattens a building. Blocks meant to stop explosions (obsidian and tougher) keep their resistance.
     */
    private static final Map<Identifier, Float> BLOCK_RESISTANCE_SCALE = Map.of(
            Reference.id("vehicle_bmp2_missile"), 0.45F,
            Reference.id("vehicle_9m120_driver_missile"), 0.45F,
            Reference.id("vehicle_9m120_passenger_missile"), 0.45F,
            Reference.id("vehicle_9m336_missile"), 0.45F,
            Reference.id("vehicle_kh39_missile"), 0.2F
    );
    private static final float BLAST_PROOF_RESISTANCE = 1200.0F;
    /** Incendiary warheads: the MI-28 pilot's 9M-120 sets the ground around the blast alight. */
    private static final Set<Identifier> INCENDIARY = Set.of(Reference.id("vehicle_9m120_driver_missile"));
    private static final double INCENDIARY_RADIUS = 6.5D;
    private static final float INCENDIARY_COVERAGE = 0.55F;
    private static final int INCENDIARY_BURN_SECONDS = 8;
    private static final EntityDataAccessor<String> DATA_WEAPON = SynchedEntityData.defineId(VehicleMissileEntity.class, EntityDataSerializers.STRING);

    private int ownerId = -1;
    private int targetId = -1;
    private int directHitVehicleId = -1;
    private Identifier weaponId = Reference.id("vehicle_bmp2_missile");

    public VehicleMissileEntity(EntityType<? extends VehicleMissileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public VehicleMissileEntity(Level level, Entity owner, @Nullable Entity target, Vec3 position, Vec3 velocity, Identifier weaponId) {
        this(ModEntities.VEHICLE_MISSILE.get(), level);
        this.ownerId = owner.getId();
        this.targetId = target == null ? -1 : target.getId();
        this.weaponId = weaponId;
        this.entityData.set(DATA_WEAPON, weaponId.toString());
        this.setPos(position);
        this.setDeltaMovement(velocity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_WEAPON, "jeg:vehicle_bmp2_missile");
    }

    public Identifier missileWeaponId() {
        return Identifier.parse(this.entityData.get(DATA_WEAPON));
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 512.0D * 512.0D;
    }

    @Override
    public void tick() {
        super.tick();
        VehicleMissileProfile profile = this.profile();
        if (this.tickCount > profile.lifeTicks()) {
            this.explode();
            return;
        }
        if (!this.level().isClientSide()) {
            this.steerServer();
            if (!this.isRemoved()) {
                this.applyMotorThrust(profile);
            }
            this.setDeltaMovement(this.flattenBmp2StartupDrop(this.getDeltaMovement()));
            if (this.isRemoved()) {
                return;
            }
            Vec3 nextPosition = this.position().add(this.getDeltaMovement());
            if (this.hitAlongPath(this.position(), nextPosition)) {
                return;
            }
        }
        this.setPos(this.position().add(this.getDeltaMovement()));
        // The motor's glow and smoke trail are drawn client-side by MissileTrailRenderer.
    }

    /** The motor builds speed through the first moments of flight. */
    private void applyMotorThrust(VehicleMissileProfile profile) {
        if (Reference.id("vehicle_bmp2_missile").equals(this.weaponId)) {
            return;
        }
        Vec3 motion = this.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-6D) {
            return;
        }
        double build = Math.min(1.0D, LAUNCH_SPEED_SHARE + (1.0D - LAUNCH_SPEED_SHARE) * this.tickCount / MOTOR_BUILD_TICKS);
        this.setDeltaMovement(motion.normalize().scale(profile.maxSpeed() * build));
    }

    private Vec3 flattenBmp2StartupDrop(Vec3 motion) {
        if (!Reference.id("vehicle_bmp2_missile").equals(this.weaponId) || this.tickCount > BMP2_NO_DROP_TICKS || motion.y >= 0.0D) {
            return motion;
        }
        Vec3 horizontal = new Vec3(motion.x, 0.0D, motion.z);
        if (horizontal.lengthSqr() < 1.0E-6D) {
            return Vec3.ZERO;
        }
        return horizontal.normalize().scale(motion.length());
    }

    private void steerServer() {
        VehicleMissileProfile profile = this.profile();
        Entity target = this.currentTarget();
        if (profile.usesLockOn()) {
            VehicleDecoyEntity.findNearest(this.level(), this.position(), DECOY_SEEK_RANGE).ifPresent(decoy -> this.targetId = decoy.getId());
            target = this.currentTarget();
        }
        Entity owner = this.ownerId < 0 ? null : this.level().getEntity(this.ownerId);
        Entity ownerVehicle = owner == null ? null : owner.getVehicle();
        boolean validTarget = target instanceof VehicleDecoyEntity
                || (target != null && profile.canContinueTracking(target, owner, ownerVehicle) && !VehicleDecoyEntity.isSmokeBlockingTarget(target));
        if (profile.usesLockOn() && target != null && validTarget) {
            this.warnTrackedTarget(target);
            Vec3 desired = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(this.position()).normalize().scale(profile.maxSpeed());
            this.setDeltaMovement(this.getDeltaMovement().scale(1.0D - profile.turnRate()).add(desired.scale(profile.turnRate())));
            if (this.distanceToSqr(target) < 1.4D) {
                if (target instanceof VehicleEntity) {
                    this.directHitVehicleId = target.getId();
                    this.setPos(target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D));
                }
                this.explode();
            }
        } else if (profile.usesWireGuidance() && owner instanceof LivingEntity livingOwner) {
            // Toward what the crosshair is on, where that is known; the camera is well away from the launcher.
            Vec3 aim = ownerVehicle instanceof VehicleEntity vehicle ? vehicle.freshAimPoint(livingOwner) : null;
            Vec3 desired = (aim != null && aim.distanceToSqr(this.position()) > 9.0D
                    ? aim.subtract(this.position()).normalize()
                    : livingOwner.getViewVector(1.0F).normalize()).scale(profile.maxSpeed());
            this.setDeltaMovement(this.getDeltaMovement().scale(1.0D - profile.turnRate()).add(desired.scale(profile.turnRate())));
        }
    }

    private void warnTrackedTarget(Entity target) {
        int interval = Math.max(2, (int) (0.04D * this.distanceTo(target)));
        if (this.tickCount % interval != 0) {
            return;
        }
        VehicleEntity.warnIncomingMissileTarget(target);
    }

    private boolean hitAlongPath(Vec3 start, Vec3 end) {
        HitResult blockHit = this.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            this.setPos(blockHit.getLocation());
            this.explode();
            return true;
        }

        Entity owner = this.ownerId < 0 ? null : this.level().getEntity(this.ownerId);
        Entity ownerVehicle = owner == null ? null : owner.getVehicle();
        AABB path = new AABB(start, end).inflate(0.55D);
        Entity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity target : this.level().getEntities(this, path, target -> this.canImpact(target, owner, ownerVehicle))) {
            double distance = target.distanceToSqr(start);
            if (distance < closestDistance) {
                closest = target;
                closestDistance = distance;
            }
        }
        if (closest != null) {
            this.setPos(closest.position().add(0.0D, closest.getBbHeight() * 0.5D, 0.0D));
            this.directHitVehicleId = closest instanceof VehicleEntity ? closest.getId() : -1;
            this.explode();
            return true;
        }
        return false;
    }

    private boolean canImpact(Entity target, @Nullable Entity owner, @Nullable Entity ownerVehicle) {
        return target.isAlive()
                && target != owner
                && target != ownerVehicle
                && target.getVehicle() != ownerVehicle
                && (target instanceof LivingEntity || target instanceof VehicleEntity);
    }

    @Nullable
    private Entity currentTarget() {
        return this.targetId < 0 ? null : this.level().getEntity(this.targetId);
    }

    private void explode() {
        if (!this.level().isClientSide() && this.level() instanceof ServerLevel serverLevel) {
            VehicleMissileProfile profile = this.profile();
            this.spawnMissileExplosionEffects(serverLevel);
            Entity owner = this.ownerId < 0 ? null : this.level().getEntity(this.ownerId);
            boolean directHitVehicle = this.directHitVehicleId >= 0;
            float explosionPower = directHitVehicle ? profile.explosionPower() * VEHICLE_DIRECT_HIT_BLOCK_DAMAGE_SCALE : profile.explosionPower();
            boolean incendiary = INCENDIARY.contains(this.weaponId);
            // The blast is drawn by clients from a MissileBlastPayload; vanilla's own puffs would sit on top of it.
            this.level().explode(this, null, this.blockBreakingCalculator(), this.getX(), this.getY(), this.getZ(),
                    explosionPower, incendiary, ExplosionInteraction.MOB, ParticleTypes.SMOKE, ParticleTypes.SMOKE,
                    WeightedList.of(), SoundEvents.GENERIC_EXPLODE);
            if (incendiary) {
                this.igniteSurroundings(serverLevel);
            }
            Entity ownerVehicle = owner == null ? null : owner.getVehicle();
            Vec3 explosionPos = this.position();
            for (VehicleEntity target : this.level().getEntitiesOfClass(VehicleEntity.class, this.getBoundingBox().inflate(profile.blastRadius()))) {
                if (target != ownerVehicle) {
                    float damage = profile.vehicleDamage();
                    if (directHitVehicle && target.getId() != this.directHitVehicleId) {
                        double effectiveDistance = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).distanceTo(explosionPos) * VEHICLE_DIRECT_HIT_FALLOFF_DISTANCE_SCALE;
                        if (effectiveDistance > profile.blastRadius()) {
                            continue;
                        }
                        damage *= 1.0F - (float) (effectiveDistance / profile.blastRadius());
                    }
                    if (damage > 0.5F) {
                        target.hurt(this.damageSources().explosion(this, owner), damage);
                    }
                }
            }
            for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(profile.blastRadius()))) {
                if (target != owner) {
                    float damage = profile.livingDamage();
                    if (directHitVehicle) {
                        double effectiveDistance = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).distanceTo(explosionPos) * VEHICLE_DIRECT_HIT_FALLOFF_DISTANCE_SCALE;
                        if (effectiveDistance > profile.blastRadius()) {
                            continue;
                        }
                        damage *= 1.0F - (float) (effectiveDistance / profile.blastRadius());
                    }
                    if (damage > 0.5F) {
                        target.hurt(this.damageSources().explosion(this, owner), damage);
                    }
                }
            }
        }
        this.discard();
    }

    /** Vanilla's explosion, with block resistance scaled down by the warhead's class (see BLOCK_RESISTANCE_SCALE). */
    private ExplosionDamageCalculator blockBreakingCalculator() {
        float scale = BLOCK_RESISTANCE_SCALE.getOrDefault(this.weaponId, 1.0F);
        return new EntityBasedExplosionDamageCalculator(this) {
            @Override
            public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, FluidState fluid) {
                Optional<Float> resistance = super.getBlockExplosionResistance(explosion, level, pos, state, fluid);
                if (scale >= 1.0F || !fluid.isEmpty()) {
                    return resistance;
                }
                return resistance.map(value -> value >= BLAST_PROOF_RESISTANCE ? value : value * scale);
            }
        };
    }

    /** Spreads fire over the ground around the blast and sets everything caught in it burning. */
    private void igniteSurroundings(ServerLevel level) {
        BlockPos centre = this.blockPosition();
        int radius = Mth.ceil(INCENDIARY_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius), centre.offset(radius, radius, radius))) {
            if (pos.distSqr(centre) > INCENDIARY_RADIUS * INCENDIARY_RADIUS || this.random.nextFloat() > INCENDIARY_COVERAGE) {
                continue;
            }
            if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, Direction.UP)) {
                level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
            }
        }
        for (Entity entity : level.getEntities(this, this.getBoundingBox().inflate(INCENDIARY_RADIUS))) {
            if (!entity.fireImmune() && entity.distanceToSqr(this) <= INCENDIARY_RADIUS * INCENDIARY_RADIUS
                    && !(entity instanceof VehicleEntity)) {
                entity.igniteForSeconds(INCENDIARY_BURN_SECONDS);
            }
        }
        double spread = INCENDIARY_RADIUS * 0.6D;
        sendLongDistanceParticles(level, ParticleTypes.FLAME, this.getX(), this.getY() + 0.5D, this.getZ(), 220, spread, 0.8D, spread, 0.08D);
        sendLongDistanceParticles(level, ParticleTypes.LAVA, this.getX(), this.getY() + 0.5D, this.getZ(), 40, spread * 0.6D, 0.5D, spread * 0.6D, 0.0D);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 6.0F, 0.6F);
    }

    /** Scales with the warhead: a fireball, flying fire and earth, a column of smoke, a deep boom and a shake felt at a distance. */
    private void spawnMissileExplosionEffects(ServerLevel serverLevel) {
        float power = this.profile().explosionPower();
        float size = power / BIGGEST_BLAST;
        double spread = 0.5D + size * 2.5D;
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        MissileBlastPayload blast = new MissileBlastPayload(this.position(), power, (float) this.profile().blastRadius(),
                INCENDIARY.contains(this.weaponId));
        for (ServerPlayer player : serverLevel.players()) {
            if (player.position().distanceToSqr(this.position()) < BLAST_VISIBLE_RANGE * BLAST_VISIBLE_RANGE
                    && ServerPlayNetworking.canSend(player, MissileBlastPayload.TYPE)) {
                ServerPlayNetworking.send(player, blast);
            }
        }
        serverLevel.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 8.0F + size * 18.0F, 0.75F - size * 0.2F);
        serverLevel.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 8.0F + size * 18.0F, 0.5F);

        double range = SHAKE_RANGE * Math.sqrt(size);
        for (ServerPlayer player : serverLevel.players()) {
            double distance = player.position().distanceTo(this.position());
            if (distance < range && ServerPlayNetworking.canSend(player, CameraShakePayload.TYPE)) {
                ServerPlayNetworking.send(player, new CameraShakePayload((float) (size * (1.0D - distance / range))));
            }
        }
    }

    private static <T extends ParticleOptions> void sendLongDistanceParticles(
            ServerLevel serverLevel,
            T particle,
            double x,
            double y,
            double z,
            int count,
            double xOffset,
            double yOffset,
            double zOffset,
            double speed
    ) {
        for (ServerPlayer player : serverLevel.players()) {
            serverLevel.sendParticles(player, particle, true, false, x, y, z, count, xOffset, yOffset, zOffset, speed);
        }
    }

    private VehicleMissileProfile profile() {
        return VehicleMissileProfile.get(this.level().isClientSide() ? this.missileWeaponId() : this.weaponId);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.ownerId = input.getIntOr("OwnerId", -1);
        this.targetId = input.getIntOr("TargetId", -1);
        this.weaponId = Identifier.parse(input.getStringOr("WeaponId", Reference.id("vehicle_bmp2_missile").toString()));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("OwnerId", this.ownerId);
        output.putInt("TargetId", this.targetId);
        output.putString("WeaponId", this.weaponId.toString());
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource source, float amount) {
        return false;
    }
}
