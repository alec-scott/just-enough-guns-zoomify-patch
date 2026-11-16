package ttv.migami.jeg.vehicle.projectile;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
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
import ttv.migami.jeg.network.MissileBlastPayload;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.ShellBallistics;
import ttv.migami.jeg.vehicle.util.VehicleSoundHelper;

/**
 * A gun's shell in flight: the mortar's round and the Mk42's 5-inch shells. It falls under gravity exactly as its
 * {@link ShellBallistics} predicts (the gunner's HUD uses the same), keeps flying through unloaded terrain (FarFlight),
 * and bursts on impact with its {@link Warhead}: a direct hit on what it struck, a blast easing off to nothing at its
 * radius, and blocks broken as a jegn rocket breaks them. A warhead with a proximity fuze also bursts beside aircraft,
 * missiles and flying mobs, and knocks missiles out of the air.
 */
public class ShellEntity extends Entity {
    /** The firing gun is ignored this long, so a shell can't strike its own barrel on the way out. */
    private static final int ARMING_TICKS = 3;
    /** A proximity burst knocks down missiles this close to it (they can't be hurt, see VehicleMissileEntity). */
    private static final double MISSILE_KILL_RADIUS = 4.0D;

    /**
     * What a shell does when it bursts.
     *
     * @param blastRadius     blocks to which the blast reaches, easing off to nothing there
     * @param blastDamage     at the centre
     * @param directHitDamage added for what the shell struck
     * @param explosionPower  the block-breaking explosion's power (TNT is 4); 0 breaks nothing
     * @param proximityRadius bursts when it passes this close to an aircraft, missile or flying mob; 0 for none
     * @param proximityArmTicks the proximity fuze arms after this many ticks of flight
     * @param dispense        a shell that breaks into submunitions (cluster) or pellets (grapeshot); null for none
     * @param smoke           a smoke shell: where it lands it lays a smoke screen (SmokeGrenadeEntity#smokeScreen) and
     *                        hurts nothing but what it strikes
     */
    public record Warhead(float blastRadius, float blastDamage, float directHitDamage, float explosionPower,
                          double proximityRadius, int proximityArmTicks, @Nullable Dispense dispense, boolean smoke) {
        public Warhead(float blastRadius, float blastDamage, float directHitDamage, float explosionPower,
                       double proximityRadius, int proximityArmTicks, @Nullable Dispense dispense) {
            this(blastRadius, blastDamage, directHitDamage, explosionPower, proximityRadius, proximityArmTicks, dispense, false);
        }

        public Warhead(float blastRadius, float blastDamage, float directHitDamage, float explosionPower,
                       double proximityRadius, int proximityArmTicks) {
            this(blastRadius, blastDamage, directHitDamage, explosionPower, proximityRadius, proximityArmTicks, null, false);
        }

        /** A smoke shell striking for {@code directHitDamage}. */
        public static Warhead smokeShell(float directHitDamage) {
            return new Warhead(0.0F, 0.0F, directHitDamage, 0.0F, 0.0D, 0, null, true);
        }

        public boolean proximityFuzed() {
            return this.proximityRadius > 0.0D;
        }

        /** No blast and no block damage: a pellet that only hurts what it strikes. */
        public boolean kinetic() {
            return this.blastRadius <= 0.0F && this.explosionPower <= 0.0F && !this.smoke;
        }
    }

    /**
     * How a carrier shell breaks up (Superb Warfare's cluster and grapeshot shells). After {@code afterTicks} of
     * flight it releases {@code count} children along its own path, each thrown at {@code speedMin..speedMax} of its
     * speed with vanilla projectile spread {@code spread}. With {@code lookAheadTicks} it waits until a block or target
     * lies within that many ticks of flight ahead (the cluster's fuze); with 0 it breaks up at once (grapeshot, at the
     * muzzle).
     */
    public record Dispense(int count, double spread, double speedMin, double speedMax, int afterTicks, int lookAheadTicks,
                           Warhead child, ShellBallistics childBallistics) {}

    private int ownerId = -1;
    private int launcherId = -1;
    private ShellBallistics ballistics;
    private Warhead warhead;
    private final FarFlight farFlight = new FarFlight();

    public ShellEntity(EntityType<? extends ShellEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public ShellEntity(EntityType<? extends ShellEntity> type, Level level, @Nullable Entity owner, Entity launcher,
                       Vec3 position, Vec3 velocity, ShellBallistics ballistics, Warhead warhead) {
        this(type, level);
        this.ownerId = owner == null ? -1 : owner.getId();
        this.launcherId = launcher.getId();
        this.ballistics = ballistics;
        this.warhead = warhead;
        this.setPos(position);
        this.setDeltaMovement(velocity);
    }

    /** For a shell type with fixed ballistics (the mortar round), so its client copy flies between updates too. */
    protected void setFlight(ShellBallistics ballistics, Warhead warhead) {
        this.ballistics = ballistics;
        this.warhead = warhead;
    }

    public Warhead warhead() {
        return this.warhead;
    }

    public ShellBallistics ballistics() {
        return this.ballistics;
    }

    /** A rocket (the Type-63's): drawn with a motor glow and a short trail (MissileTrailRenderer). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_ROCKET =
            SynchedEntityData.defineId(ShellEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** Ticks a rocket's motor burns (and lays smoke). */
    public static final int ROCKET_MOTOR_TICKS = 20;

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ROCKET, false);
    }

    public void markRocket() {
        this.entityData.set(DATA_ROCKET, true);
    }

    public boolean isRocket() {
        return this.entityData.get(DATA_ROCKET);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.ballistics == null || this.warhead == null) {
            // The client's copy has no ballistics (they aren't synced) and follows the server's position updates. On
            // the server only a shell fired by a gun flies.
            if (!this.level().isClientSide()) {
                this.discard();
            }
            return;
        }
        if (this.level() instanceof ServerLevel serverLevel && this.warhead.dispense() != null
                && this.tickCount > this.warhead.dispense().afterTicks() && this.shouldDispense(serverLevel)) {
            this.dispense(serverLevel);
            return;
        }
        Vec3 motion = this.ballistics.step(this.getDeltaMovement());
        this.setDeltaMovement(motion);
        Vec3 next = this.position().add(motion);
        if (this.level() instanceof ServerLevel serverLevel) {
            if (this.tickCount > this.ballistics.maxFlightTicks() || next.y < this.level().getMinY() - 64) {
                this.discard();
                return;
            }
            if (this.hitAlongPath(serverLevel, this.position(), next)) {
                return;
            }
            if (this.warhead.proximityFuzed() && this.tickCount > this.warhead.proximityArmTicks()
                    && this.proximityAlongPath(serverLevel, this.position(), next)) {
                return;
            }
        }
        this.setPos(next);
        if (this.level() instanceof ServerLevel serverLevel) {
            this.farFlight.keepTicking(serverLevel, this, this.ballistics.maxFlightTicks() - this.tickCount);
        }
    }

    private boolean hitAlongPath(ServerLevel level, Vec3 start, Vec3 end) {
        HitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
        Vec3 reach = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        AABB path = new AABB(start, reach).inflate(0.3D);
        Entity closest = null;
        Vec3 closestAt = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity target : level.getEntities(this, path, this::canImpact)) {
            Vec3 at = target instanceof VehicleEntity vehicle && vehicle.preciseHitbox()
                    ? vehicle.clipHitbox(start, reach, 0.05D)
                    : target.getBoundingBox().inflate(0.3D).clip(start, reach).orElse(null);
            if (at == null) {
                continue;
            }
            double distance = at.distanceToSqr(start);
            if (distance < closestDistance) {
                closest = target;
                closestAt = at;
                closestDistance = distance;
            }
        }
        if (closest != null) {
            this.setPos(closestAt);
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

    private boolean shouldDispense(ServerLevel level) {
        int lookAhead = this.warhead.dispense().lookAheadTicks();
        if (lookAhead <= 0) {
            return true;
        }
        Vec3 start = this.position();
        Vec3 end = start.add(this.getDeltaMovement().scale(lookAhead));
        if (level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, this)).getType() != HitResult.Type.MISS) {
            return true;
        }
        for (Entity target : level.getEntities(this, new AABB(start, end).inflate(1.0D), this::canImpact)) {
            if (target.getBoundingBox().inflate(0.5D).clip(start, end).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** Breaks the carrier into its submunitions or pellets, which fly on from here. */
    private void dispense(ServerLevel level) {
        Dispense d = this.warhead.dispense();
        Vec3 velocity = this.getDeltaMovement();
        double speed = velocity.length();
        Vec3 direction = speed < 1.0E-6D ? new Vec3(0.0D, -1.0D, 0.0D) : velocity.scale(1.0D / speed);
        Entity owner = this.ownerId < 0 ? null : level.getEntity(this.ownerId);
        Entity launcher = this.launcherId < 0 ? null : level.getEntity(this.launcherId);
        double spread = 0.0172275D * d.spread();
        for (int i = 0; i < d.count(); i++) {
            Vec3 aim = direction.add(this.random.triangle(0.0D, spread), this.random.triangle(0.0D, spread),
                    this.random.triangle(0.0D, spread)).normalize();
            double share = d.speedMin() + this.random.nextDouble() * (d.speedMax() - d.speedMin());
            ShellEntity child = new ShellEntity(ttv.migami.jeg.init.ModEntities.CANNON_SHELL.get(),
                    level, owner, launcher == null ? this : launcher, this.position(), aim.scale(speed * share), d.childBallistics(), d.child());
            // Children of a shell already in flight are armed: they may strike the gun's own cover only if aimed at it.
            child.tickCount = ARMING_TICKS + 1;
            level.addFreshEntity(child);
        }
        if (!d.child().kinetic()) {
            level.sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(), this.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        this.discard();
    }

    /** The proximity fuze: bursts at the point of the path nearest anything airborne it passes within range of. */
    private boolean proximityAlongPath(ServerLevel level, Vec3 start, Vec3 end) {
        double radius = this.warhead.proximityRadius();
        Vec3 segment = end.subtract(start);
        double lengthSqr = segment.lengthSqr();
        Vec3 best = null;
        double bestShare = Double.MAX_VALUE;
        for (Entity target : level.getEntities(this, new AABB(start, end).inflate(radius), this::isAirTarget)) {
            Vec3 centre = target.getBoundingBox().getCenter();
            double share = lengthSqr < 1.0E-9D ? 0.0D : Math.clamp(centre.subtract(start).dot(segment) / lengthSqr, 0.0D, 1.0D);
            Vec3 point = start.add(segment.scale(share));
            if (Math.sqrt(target.getBoundingBox().distanceToSqr(point)) <= radius && share < bestShare) {
                best = point;
                bestShare = share;
            }
        }
        if (best == null) {
            return false;
        }
        this.setPos(best);
        this.burst(level, null);
        return true;
    }

    private boolean isAirTarget(Entity target) {
        if (!target.isAlive() || target.getId() == this.launcherId || target.getRootVehicle().getId() == this.launcherId) {
            return false;
        }
        if (target instanceof VehicleEntity vehicle) {
            return VehicleSoundHelper.isAircraft(vehicle) && !vehicle.onGround();
        }
        return target instanceof VehicleMissileEntity || target instanceof Phantom || target instanceof Ghast
                || target instanceof Blaze && !target.onGround();
    }

    private boolean canImpact(Entity target) {
        if (!target.isAlive() || target.isSpectator() || !target.isPickable() && !(target instanceof VehicleEntity)) {
            return false;
        }
        return this.tickCount > ARMING_TICKS || target.getId() != this.launcherId && target.getRootVehicle().getId() != this.launcherId;
    }

    private void burst(ServerLevel level, @Nullable Entity struck) {
        Vec3 centre = this.position();
        Warhead w = this.warhead;
        if (!w.kinetic() && !this.armedAt(level, centre)) {
            // Fuze still safe: a burst this close would hit its own gun. It strikes as solid shot and breaks up.
            if (struck != null) {
                Entity shooter = this.ownerId < 0 ? null : level.getEntity(this.ownerId);
                struck.hurtServer(level, this.damageSources().thrown(this, shooter), w.directHitDamage());
            }
            level.sendParticles(ParticleTypes.SMOKE, centre.x, centre.y, centre.z, 6, 0.2D, 0.2D, 0.2D, 0.02D);
            this.discard();
            return;
        }
        if (w.smoke()) {
            if (struck != null) {
                Entity shooter = this.ownerId < 0 ? null : level.getEntity(this.ownerId);
                struck.hurtServer(level, this.damageSources().thrown(this, shooter), w.directHitDamage());
            }
            ttv.migami.jeg.entity.SmokeGrenadeEntity.smokeScreen(level, centre, 3);
            this.discard();
            return;
        }
        if (w.kinetic()) {
            // A pellet: it hurts what it strikes, every pellet counting (a canister's pellets arrive together).
            if (struck != null) {
                Entity shooter = this.ownerId < 0 ? null : level.getEntity(this.ownerId);
                struck.invulnerableTime = 0;
                struck.hurtServer(level, this.damageSources().thrown(this, shooter), w.directHitDamage());
            }
            level.sendParticles(ParticleTypes.CRIT, centre.x, centre.y, centre.z, 3, 0.05D, 0.05D, 0.05D, 0.1D);
            this.discard();
            return;
        }
        MissileBlastPayload blast = new MissileBlastPayload(centre, Math.max(w.explosionPower(), 2.0F), w.blastRadius(), false, true);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(centre) < FarFlight.VISIBLE_RANGE * FarFlight.VISIBLE_RANGE
                    && ServerPlayNetworking.canSend(player, MissileBlastPayload.TYPE)) {
                ServerPlayNetworking.send(player, blast);
            }
        }
        if (w.explosionPower() > 0.0F) {
            // The blast's look and bang come from the payload above (the bang arrives with the shockwave).
            level.explode(this, null, WarheadBlockDamage.blocksOnly(this, 1.0F), centre.x, centre.y, centre.z, w.explosionPower(),
                    false, ExplosionInteraction.TNT, ParticleTypes.SMOKE, ParticleTypes.SMOKE, WeightedList.of(),
                    Holder.direct(SoundEvents.EMPTY));
            DistantTerrainRefresh.afterBlast();
        }
        Entity owner = this.ownerId < 0 ? null : level.getEntity(this.ownerId);
        DamageSource source = this.damageSources().explosion(this, owner instanceof LivingEntity living ? living : null);
        // One hit per target (a second within the hurt cooldown would only count its excess): the struck target takes
        // the direct hit and the blast together.
        AABB area = new AABB(centre, centre).inflate(w.blastRadius());
        boolean struckHurt = false;
        for (Entity target : level.getEntities(this, area, entity -> entity instanceof LivingEntity || entity instanceof VehicleEntity)) {
            float damage = w.blastDamage() * falloff(target, centre, w.blastRadius());
            if (target == struck) {
                damage += w.directHitDamage();
                struckHurt = true;
            }
            if (damage > 0.5F) {
                target.hurtServer(level, source, damage);
            }
        }
        if (struck != null && !struckHurt) {
            struck.hurtServer(level, source, w.directHitDamage() + w.blastDamage());
        }
        if (w.proximityFuzed()) {
            for (Entity missile : level.getEntities(this, new AABB(centre, centre).inflate(MISSILE_KILL_RADIUS),
                    entity -> entity instanceof VehicleMissileEntity && entity.position().distanceTo(centre) <= MISSILE_KILL_RADIUS)) {
                level.sendParticles(ParticleTypes.EXPLOSION, missile.getX(), missile.getY(), missile.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
                missile.discard();
            }
        }
        this.discard();
    }

    /**
     * The fuze arms once the shell is clear of its own gun: a burst point within the blast radius (plus a block) of the
     * gun that fired it would hit the gun, so the shell is a dud there (as real fuzes stay safe near the muzzle).
     */
    private boolean armedAt(ServerLevel level, Vec3 centre) {
        Entity launcher = this.launcherId < 0 ? null : level.getEntity(this.launcherId);
        if (launcher == null || launcher instanceof ShellEntity) {
            return true;
        }
        return safeDistance(launcher, centre, this.warhead);
    }

    /** True when a burst at {@code centre} is clear of the gun {@code launcher} (see armedAt); for the HUD too. */
    public static boolean safeDistance(Entity launcher, Vec3 centre, Warhead warhead) {
        double clear = warhead.blastRadius() + 1.0D;
        return launcher.getBoundingBox().distanceToSqr(centre) > clear * clear;
    }

    /** Full at the centre, three quarters at half the radius, nothing at the rim; to the target's nearest part. */
    public static float falloff(Entity target, Vec3 centre, float radius) {
        double distance = Math.sqrt(target.getBoundingBox().distanceToSqr(centre));
        if (distance >= radius) {
            return 0.0F;
        }
        double share = distance / radius;
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
        this.launcherId = input.getIntOr("LauncherId", -1);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("OwnerId", this.ownerId);
        output.putInt("LauncherId", this.launcherId);
    }
}
