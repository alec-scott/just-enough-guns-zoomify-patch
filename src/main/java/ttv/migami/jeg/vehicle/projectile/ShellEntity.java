package ttv.migami.jeg.vehicle.projectile;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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
 * missiles and flying mobs, and knocks missiles out of the air. An incendiary bursts over where it would land and its
 * burning fragments, shells of this type too, set the ground there alight.
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
     * @param dispense        a shell that breaks into submunitions (cluster); null for none
     * @param smoke           a smoke shell: where it lands it lays a smoke screen (SmokeGrenadeEntity#smokeScreen) and
     *                        hurts nothing but what it strikes
     * @param incendiary      an incendiary shell: it bursts over where it would land and rains burning fragments there;
     *                        null for none
     * @param burnSeconds     a burning fragment (of an incendiary shell): what it strikes burns this long, and where it
     *                        lands it sets the ground alight; 0 for none
     */
    public record Warhead(float blastRadius, float blastDamage, float directHitDamage, float explosionPower,
                          double proximityRadius, int proximityArmTicks, @Nullable Dispense dispense, boolean smoke,
                          @Nullable Incendiary incendiary, int burnSeconds) {
        public Warhead(float blastRadius, float blastDamage, float directHitDamage, float explosionPower,
                       double proximityRadius, int proximityArmTicks, @Nullable Dispense dispense, boolean smoke) {
            this(blastRadius, blastDamage, directHitDamage, explosionPower, proximityRadius, proximityArmTicks, dispense, smoke, null, 0);
        }

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

        /**
         * An incendiary shell: a small bursting charge ({@code blastDamage} within {@code blastRadius}, breaking no
         * blocks; {@code directHitDamage} on what it strikes if it strikes before its fuze acts), and its fill.
         */
        public static Warhead incendiaryShell(float blastRadius, float blastDamage, float directHitDamage, Incendiary fill) {
            return new Warhead(blastRadius, blastDamage, directHitDamage, 0.0F, 0.0D, 0, null, false, fill, 0);
        }

        /** One of an incendiary shell's burning fragments: {@code damage} on what it strikes, which burns for a while. */
        public static Warhead fireFragment(float damage, int burnSeconds) {
            return new Warhead(0.0F, 0.0F, damage, 0.0F, 0.0D, 0, null, false, null, burnSeconds);
        }

        public boolean proximityFuzed() {
            return this.proximityRadius > 0.0D;
        }

        /** No blast and no block damage: solid shot, or a fragment, that only hurts what it strikes. */
        public boolean kinetic() {
            return this.blastRadius <= 0.0F && this.explosionPower <= 0.0F && !this.smoke && this.incendiary == null;
        }

        /** How far from its burst this warhead reaches: its blast, or the circle an incendiary's fragments rain over. */
        public double reach() {
            return Math.max(this.blastRadius, this.incendiary == null ? 0.0D : this.incendiary.radius());
        }
    }

    /**
     * An incendiary shell's fill (the user, 2026-11-19: "change GS shells to incendiary shells which explode over a
     * target leaving flames below it"). Once armed, a height fuze bursts the shell {@code burstHeight} blocks above
     * where it would come down (on a shallow path, at most {@code maxLead} blocks short of it along the path), and
     * {@code fragments} burning fragments rain over a circle of {@code radius} round that point, landing over half a
     * second or so: each burns what it strikes and sets alight where it lands (ShellEntity#setAlight).
     *
     * @param armTicks the height fuze arms after this many ticks of flight; before that the shell bursts only on impact
     */
    public record Incendiary(double burstHeight, double maxLead, int armTicks, int fragments, double radius,
                             Warhead fragment, ShellBallistics fragmentBallistics) {}

    /**
     * How a carrier shell breaks up (Superb Warfare's cluster shell). After {@code afterTicks} of flight it releases
     * {@code count} children along its own path, each thrown at {@code speedMin..speedMax} of its speed with vanilla
     * projectile spread {@code spread}. With {@code lookAheadTicks} it waits until a block or target lies within that
     * many ticks of flight ahead (the cluster's fuze); with 0 it breaks up at once.
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
    /** An incendiary shell's burning fragment: drawn as flame trailing smoke as it falls (trailFire). */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_BURNING =
            SynchedEntityData.defineId(ShellEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    /** Ticks of flight ahead the height fuze looks for where the shell will come down. */
    private static final int FUZE_LOOK_AHEAD_TICKS = 3;
    /** A burning fragment takes this many ticks, give or take FRAGMENT_FALL_SPREAD, to come down from the burst. */
    private static final int FRAGMENT_FALL_TICKS = 8;
    private static final int FRAGMENT_FALL_SPREAD = 8;
    /** What stands this close to where a fragment lands catches fire. */
    private static final double FRAGMENT_SPLASH = 1.2D;
    @Nullable
    private BlockHitResult blockStruck;

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ROCKET, false);
        builder.define(DATA_BURNING, false);
    }

    public boolean isFireFragment() {
        return this.entityData.get(DATA_BURNING);
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
        if (this.level().isClientSide() && this.isFireFragment()) {
            this.trailFire();
        }
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
            if (this.warhead.kinetic() && this.warhead.burnSeconds() <= 0) {
                // Solid shot cuts what is flimsy on its way (grass, flowers, crops, leaves, glass), as a helicopter's
                // rounds do. Claims and the server's switch get their say. (A burning fragment leaves the grass to burn.)
                Entity shooter = this.ownerId < 0 ? null : serverLevel.getEntity(this.ownerId);
                ttv.migami.jeg.gun.VehicleGunBlockDamage.alongPath(serverLevel, this.position(), next, shooter, this);
            }
            Incendiary fill = this.warhead.incendiary();
            if (fill != null && this.tickCount > fill.armTicks() && this.heightFuze(serverLevel, motion)) {
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

    /** What a step of flight strikes first: a target (entity set) or a block (block set), and where. */
    private record Strike(Vec3 at, @Nullable Entity entity, @Nullable BlockHitResult block) {}

    @Nullable
    private Strike strikeAlong(ServerLevel level, Vec3 start, Vec3 end) {
        BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, this));
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
            return new Strike(closestAt, closest, null);
        }
        return blockHit.getType() == HitResult.Type.MISS ? null : new Strike(reach, null, blockHit);
    }

    private boolean hitAlongPath(ServerLevel level, Vec3 start, Vec3 end) {
        Strike strike = this.strikeAlong(level, start, end);
        if (strike == null) {
            return false;
        }
        if (strike.entity() != null) {
            this.setPos(strike.at());
            this.burst(level, strike.entity());
            return true;
        }
        // Burst just short of the face it struck, so the blast works on the surface rather than inside it.
        this.setPos(strike.at().subtract(end.subtract(start).normalize().scale(0.1D)));
        this.blockStruck = strike.block();
        this.burst(level, null);
        return true;
    }

    /**
     * An incendiary's height fuze: follows the shell's path {@link #FUZE_LOOK_AHEAD_TICKS} ticks ahead to where it
     * will come down (the ground, a wall or a target), and bursts this tick if the point {@code burstHeight} above that,
     * back along the path, lies within this tick's flight (or already behind it: a target close ahead). On a steep path
     * that point is right over where it lands; on a shallow one it is {@code maxLead} blocks short at most, low down.
     */
    private boolean heightFuze(ServerLevel level, Vec3 motion) {
        Incendiary fill = this.warhead.incendiary();
        double firstStep = motion.length();
        if (firstStep < 1.0E-6D) {
            return false;
        }
        Vec3 position = this.position();
        Vec3 velocity = motion;
        double travelled = 0.0D;
        for (int tick = 0; tick < FUZE_LOOK_AHEAD_TICKS; tick++) {
            Vec3 next = position.add(velocity);
            Strike strike = this.strikeAlong(level, position, next);
            if (strike != null) {
                double along = travelled + strike.at().distanceTo(position);
                double descent = Math.max(0.0D, -velocity.y / velocity.length());
                double lead = Math.min(fill.burstHeight() / Math.max(descent, 0.2D), fill.maxLead());
                if (along - lead > firstStep) {
                    return false;
                }
                Vec3 burstAt = this.position().add(motion.scale(Math.max(0.0D, along - lead) / firstStep));
                this.airburst(level, burstAt, strike.at(), null);
                return true;
            }
            travelled += velocity.length();
            position = next;
            velocity = this.ballistics.step(velocity);
        }
        return false;
    }

    /**
     * The incendiary bursts at {@code burstAt}: a flash and a small blast there, and its burning fragments thrown to
     * land over the circle round {@code below}, where it would have come down. Each is given the velocity that brings it
     * down on its spot under its own gravity in FRAGMENT_FALL_TICKS give or take, so they rain onto the target, not
     * short of it, whatever the angle the shell came in at. Too near its own gun, it is a dud.
     */
    private void airburst(ServerLevel level, Vec3 burstAt, Vec3 below, @Nullable Entity struck) {
        Incendiary fill = this.warhead.incendiary();
        this.setPos(burstAt);
        Entity owner = this.ownerId < 0 ? null : level.getEntity(this.ownerId);
        if (!this.armedAt(level, below) || !this.armedAt(level, burstAt)) {
            if (struck != null) {
                struck.hurtServer(level, this.damageSources().thrown(this, owner), this.warhead.directHitDamage());
            }
            level.sendParticles(ParticleTypes.SMOKE, burstAt.x, burstAt.y, burstAt.z, 6, 0.2D, 0.2D, 0.2D, 0.02D);
            this.discard();
            return;
        }
        RealisticBlast.show(level, burstAt, 2.0F, this.warhead.blastRadius(), true);
        level.playSound(null, burstAt.x, burstAt.y, burstAt.z, SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 6.0F, 0.6F);
        level.sendParticles(ParticleTypes.FLAME, burstAt.x, burstAt.y, burstAt.z, 60, 0.8D, 0.8D, 0.8D, 0.12D);
        level.sendParticles(ParticleTypes.LAVA, burstAt.x, burstAt.y, burstAt.z, 16, 0.6D, 0.4D, 0.6D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, burstAt.x, burstAt.y, burstAt.z, 24, 1.0D, 0.8D, 1.0D, 0.02D);
        this.blastEntities(level, burstAt, struck, owner);
        Entity launcher = this.launcherId < 0 ? null : level.getEntity(this.launcherId);
        double gravity = fill.fragmentBallistics().gravity();
        for (int i = 0; i < fill.fragments(); i++) {
            double distance = fill.radius() * Math.sqrt((i + 0.5D) / fill.fragments()) * (0.85D + this.random.nextDouble() * 0.3D);
            double angle = i * 2.399963229728653D + this.random.nextDouble() * 0.4D;
            Vec3 landing = below.add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance);
            int ticks = FRAGMENT_FALL_TICKS + this.random.nextInt(FRAGMENT_FALL_SPREAD + 1);
            // n ticks of (gravity, then the step) from v: n v - g n (n + 1) / 2.
            Vec3 velocity = landing.subtract(burstAt).scale(1.0D / ticks).add(0.0D, gravity * (ticks + 1) / 2.0D, 0.0D);
            ShellEntity fragment = new ShellEntity(ttv.migami.jeg.init.ModEntities.CANNON_SHELL.get(), level, owner,
                    launcher == null ? this : launcher, burstAt, velocity, fill.fragmentBallistics(), fill.fragment());
            fragment.tickCount = ARMING_TICKS + 1;
            fragment.entityData.set(DATA_BURNING, true);
            level.addFreshEntity(fragment);
        }
        this.discard();
    }

    /** A burning fragment, as it falls: flame streaming off it and a smoke trail, seen from afar. */
    private void trailFire() {
        Level level = this.level();
        double dx = this.getX() - this.xo, dy = this.getY() - this.yo, dz = this.getZ() - this.zo;
        for (int i = 0; i < 3; i++) {
            double t = i / 3.0D;
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, this.xo + dx * t, this.yo + dy * t, this.zo + dz * t,
                    (this.random.nextDouble() - 0.5D) * 0.03D, 0.02D, (this.random.nextDouble() - 0.5D) * 0.03D);
        }
        if (this.tickCount % 2 == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, this.getX(), this.getY(), this.getZ(), 0.0D, 0.02D, 0.0D);
        }
        if (this.random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.LAVA, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * Sets the ground alight at {@code pos} where a burning fragment lands: a fire block in the air (or in the grass or
     * flowers) over something it can burn on. Only where the shell could break blocks: the server's tnt_explodes rule
     * (as the HE shells' explosions), spawn protection and land claims (as if the gunner had broken it). The fire then
     * burns and spreads as any fire does under the server's rules.
     */
    static boolean setAlight(ServerLevel level, BlockPos pos, @Nullable Entity owner) {
        if (!level.isLoaded(pos) || !Boolean.TRUE.equals(level.getGameRules().get(GameRules.TNT_EXPLODES))) {
            return false;
        }
        BlockState here = level.getBlockState(pos);
        if (!here.isAir() && !(here.canBeReplaced() && here.getFluidState().isEmpty())) {
            return false;
        }
        BlockState fire = BaseFireBlock.getState(level, pos);
        if (!fire.canSurvive(level, pos)) {
            return false;
        }
        if (owner instanceof ServerPlayer player && (!level.mayInteract(player, pos)
                || !net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, here, null))) {
            return false;
        }
        return level.setBlock(pos, fire, Block.UPDATE_ALL_IMMEDIATE);
    }

    /** A burning fragment comes down: it burns what it strikes and what stands where it lands, and sets the spot alight. */
    private void land(ServerLevel level, @Nullable Entity struck) {
        Vec3 centre = this.position();
        Entity owner = this.ownerId < 0 ? null : level.getEntity(this.ownerId);
        int seconds = this.warhead.burnSeconds();
        if (struck != null) {
            struck.invulnerableTime = 0;
            struck.hurtServer(level, this.damageSources().thrown(this, owner), this.warhead.directHitDamage());
            if (!(struck instanceof VehicleEntity)) {
                struck.igniteForSeconds(seconds);
            }
        }
        for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(FRAGMENT_SPLASH),
                entity -> entity.isAlive() && entity.getBoundingBox().distanceToSqr(centre) <= FRAGMENT_SPLASH * FRAGMENT_SPLASH)) {
            near.igniteForSeconds(seconds * 0.5F);
        }
        BlockHitResult block = this.blockStruck;
        if (block != null) {
            BlockPos at = block.getBlockPos();
            if (!level.getFluidState(at).isEmpty()) {
                // Into water: it hisses out.
                level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y, centre.z, 3, 0.1D, 0.05D, 0.1D, 0.01D);
                this.discard();
                return;
            }
            setAlight(level, at.relative(block.getDirection()), owner);
        }
        level.sendParticles(ParticleTypes.FLAME, centre.x, centre.y, centre.z, 6, 0.15D, 0.1D, 0.15D, 0.03D);
        level.sendParticles(ParticleTypes.SMOKE, centre.x, centre.y, centre.z, 3, 0.1D, 0.1D, 0.1D, 0.01D);
        this.discard();
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
            // Children of a shell already well in flight are armed: they may strike the gun's own cover only if aimed
            // at it. Children of a shell that breaks up at the muzzle keep its arming time, or they strike the gun's
            // own barrel (the YX-100's muzzle sits on its barrel box: 1,000 damage to itself, 2026-11-18).
            child.tickCount = Math.min(this.tickCount, ARMING_TICKS + 1);
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
        if (w.burnSeconds() > 0) {
            this.land(level, struck);
            return;
        }
        if (w.incendiary() != null) {
            // Struck before the height fuze acted (a target or wall close ahead, or not yet armed): it bursts as it
            // strikes, a little back along its path and up, and the fragments come down round where it struck.
            Vec3 back = this.getDeltaMovement().lengthSqr() < 1.0E-6D ? Vec3.ZERO : this.getDeltaMovement().normalize();
            this.airburst(level, centre.subtract(back).add(0.0D, 1.5D, 0.0D), centre, struck);
            return;
        }
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
            // Solid shot: it hurts what it strikes, every one counting when several arrive together.
            if (struck != null) {
                Entity shooter = this.ownerId < 0 ? null : level.getEntity(this.ownerId);
                struck.invulnerableTime = 0;
                struck.hurtServer(level, this.damageSources().thrown(this, shooter), w.directHitDamage());
            }
            level.sendParticles(ParticleTypes.CRIT, centre.x, centre.y, centre.z, 3, 0.05D, 0.05D, 0.05D, 0.1D);
            if (struck instanceof VehicleEntity) {
                // Iron on armour: a spark where it strikes.
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y, centre.z, 4, 0.05D, 0.05D, 0.05D, 0.25D);
            }
            if (struck == null) {
                // Where it strikes the ground or a wall it kicks up the block's dust.
                Vec3 into = this.getDeltaMovement().lengthSqr() < 1.0E-6D ? new Vec3(0.0D, -0.3D, 0.0D) : this.getDeltaMovement().normalize().scale(0.3D);
                var state = level.getBlockState(net.minecraft.core.BlockPos.containing(centre.add(into)));
                if (!state.isAir()) {
                    level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
                            centre.x, centre.y, centre.z, 8, 0.15D, 0.1D, 0.15D, 0.15D);
                }
            }
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
        this.blastEntities(level, centre, struck, owner);
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
     * The blast's harm round {@code centre}: easing off to nothing at the warhead's radius, one hit per target (a second
     * within the hurt cooldown would only count its excess), so the struck target takes the direct hit and the blast
     * together.
     */
    private void blastEntities(ServerLevel level, Vec3 centre, @Nullable Entity struck, @Nullable Entity owner) {
        Warhead w = this.warhead;
        DamageSource source = this.damageSources().explosion(this, owner instanceof LivingEntity living ? living : null);
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
    }

    /**
     * The fuze arms once the shell is clear of its own gun: a burst point within the warhead's reach (its blast, or an
     * incendiary's rain of fragments; plus a block) of the gun that fired it would hit the gun, so the shell is a dud
     * there (as real fuzes stay safe near the muzzle).
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
        double clear = warhead.reach() + 1.0D;
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
