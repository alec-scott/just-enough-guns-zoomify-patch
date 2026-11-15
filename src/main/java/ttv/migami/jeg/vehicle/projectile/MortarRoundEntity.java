package ttv.migami.jeg.vehicle.projectile;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.entity.WarheadBlockDamage;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.network.MissileBlastPayload;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.MortarRig;

/**
 * An 81 mm high-explosive mortar round in flight. It falls under gravity exactly as {@link MortarRig} predicts, keeps
 * flying through unloaded terrain (FarFlight), and bursts with Superb Warfare's numbers: 100 damage easing off to
 * nothing at 8 blocks, plus 60 to whatever it struck. Blocks break as a jegn rocket breaks them.
 */
public final class MortarRoundEntity extends Entity {
    /** Superb Warfare's mortar: explosion radius and damage, and the direct-hit damage. */
    public static final float BLAST_RADIUS = 8.0F;
    public static final float BLAST_DAMAGE = 100.0F;
    public static final float DIRECT_HIT_DAMAGE = 60.0F;
    /** The block-breaking explosion's power, as SBW's (its radius is its power). */
    public static final float EXPLOSION_POWER = 8.0F;
    /** The firing mortar is ignored this long, so a round can't strike its own tube on the way out. */
    private static final int ARMING_TICKS = 3;

    private int ownerId = -1;
    private int mortarId = -1;
    private final FarFlight farFlight = new FarFlight();

    public MortarRoundEntity(EntityType<? extends MortarRoundEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public MortarRoundEntity(Level level, @Nullable Entity owner, Entity mortar, Vec3 position, Vec3 velocity) {
        this(ModEntities.MORTAR_ROUND.get(), level);
        this.ownerId = owner == null ? -1 : owner.getId();
        this.mortarId = mortar.getId();
        this.setPos(position);
        this.setDeltaMovement(velocity);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 motion = MortarRig.stepVelocity(this.getDeltaMovement());
        this.setDeltaMovement(motion);
        Vec3 next = this.position().add(motion);
        if (this.level() instanceof ServerLevel serverLevel) {
            if (this.tickCount > MortarRig.MAX_FLIGHT_TICKS || next.y < this.level().getMinY() - 64) {
                this.discard();
                return;
            }
            if (this.hitAlongPath(serverLevel, this.position(), next)) {
                return;
            }
        }
        this.setPos(next);
        if (this.level() instanceof ServerLevel serverLevel) {
            this.farFlight.keepTicking(serverLevel, this, MortarRig.MAX_FLIGHT_TICKS - this.tickCount);
        }
    }

    private boolean hitAlongPath(ServerLevel level, Vec3 start, Vec3 end) {
        HitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        Vec3 reach = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        AABB path = new AABB(start, reach).inflate(0.3D);
        Entity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity target : level.getEntities(this, path, this::canImpact)) {
            if (target.getBoundingBox().inflate(0.3D).clip(start, reach).isEmpty()) {
                continue;
            }
            double distance = target.distanceToSqr(start);
            if (distance < closestDistance) {
                closest = target;
                closestDistance = distance;
            }
        }
        if (closest != null) {
            this.setPos(closest.getBoundingBox().getCenter());
            this.burst(level, closest);
            return true;
        }
        if (blockHit.getType() != HitResult.Type.MISS) {
            // Burst just short of the face it struck, so the blast works on the surface rather than inside it.
            this.setPos(reach.subtract(end.subtract(start).normalize().scale(0.1D)));
            this.burst(level, null);
            return true;
        }
        return false;
    }

    private boolean canImpact(Entity target) {
        if (!target.isAlive() || target.isSpectator() || !target.isPickable() && !(target instanceof VehicleEntity)) {
            return false;
        }
        return this.tickCount > ARMING_TICKS || target.getId() != this.mortarId && target.getRootVehicle().getId() != this.mortarId;
    }

    private void burst(ServerLevel level, @Nullable Entity struck) {
        Vec3 centre = this.position();
        MissileBlastPayload blast = new MissileBlastPayload(centre, EXPLOSION_POWER, BLAST_RADIUS, false, true);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(centre) < FarFlight.VISIBLE_RANGE * FarFlight.VISIBLE_RANGE
                    && ServerPlayNetworking.canSend(player, MissileBlastPayload.TYPE)) {
                ServerPlayNetworking.send(player, blast);
            }
        }
        // The blast's look and bang come from the payload above (the bang arrives with the shockwave).
        level.explode(this, null, WarheadBlockDamage.blocksOnly(this, 1.0F), centre.x, centre.y, centre.z, EXPLOSION_POWER,
                false, ExplosionInteraction.TNT, ParticleTypes.SMOKE, ParticleTypes.SMOKE, WeightedList.of(),
                Holder.direct(SoundEvents.EMPTY));
        DistantTerrainRefresh.afterBlast();
        Entity owner = this.ownerId < 0 ? null : level.getEntity(this.ownerId);
        DamageSource source = this.damageSources().explosion(this, owner instanceof LivingEntity living ? living : null);
        // One hit per target (a second within the hurt cooldown would only count its excess): the struck target takes
        // the direct hit and the blast together.
        AABB area = new AABB(centre, centre).inflate(BLAST_RADIUS);
        boolean struckHurt = false;
        for (Entity target : level.getEntities(this, area, entity -> entity instanceof LivingEntity || entity instanceof VehicleEntity)) {
            float damage = BLAST_DAMAGE * falloff(target, centre);
            if (target == struck) {
                damage += DIRECT_HIT_DAMAGE;
                struckHurt = true;
            }
            if (damage > 0.5F) {
                target.hurtServer(level, source, damage);
            }
        }
        if (struck != null && !struckHurt) {
            struck.hurtServer(level, source, DIRECT_HIT_DAMAGE + BLAST_DAMAGE);
        }
        this.discard();
    }

    /** Full at the centre, three quarters at half the radius, nothing at the rim; to the target's nearest part. */
    public static float falloff(Entity target, Vec3 centre) {
        double distance = Math.sqrt(target.getBoundingBox().distanceToSqr(centre));
        if (distance >= BLAST_RADIUS) {
            return 0.0F;
        }
        double share = distance / BLAST_RADIUS;
        return (float) (1.0D - share * share);
    }

    @Override
    public boolean isAlwaysTicking() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0D * 256.0D;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.ownerId = input.getIntOr("OwnerId", -1);
        this.mortarId = input.getIntOr("MortarId", -1);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("OwnerId", this.ownerId);
        output.putInt("MortarId", this.mortarId);
    }
}
