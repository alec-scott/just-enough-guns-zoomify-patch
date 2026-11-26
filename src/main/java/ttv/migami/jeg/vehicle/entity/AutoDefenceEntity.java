package ttv.migami.jeg.vehicle.entity;

import java.util.Comparator;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.Config;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * A base defence that can fire on its own (the HPJ-11, the sentry; fleet plan sections 5 and 6). Manual, its operator
 * aims and fires it as any turret. Automatic (N, crewed or not), it picks the target that matters most within reach and
 * in sight ({@link #targetPriority}), leads it, lays its mount on it through the turret's own slew, and fires its first
 * weapon when on. The server may switch automatic fire off, and caps how many automatic defences fire in one chunk
 * (Config); a defence over the cap waits for a crew.
 */
public abstract class AutoDefenceEntity extends VehicleEntity {
    private static final EntityDataAccessor<Boolean> DATA_AUTO = SynchedEntityData.defineId(AutoDefenceEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_TRACKED = SynchedEntityData.defineId(AutoDefenceEntity.class, EntityDataSerializers.INT);
    private static final int SEARCH_INTERVAL = 4;

    @Nullable
    private Entity target;
    private int searchCooldown;
    private boolean allowedInChunk = true;
    /** Server: ticks since it last fired (the status lamp and the HUD). */
    private int sinceFired = 1000;

    protected AutoDefenceEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
    }

    // ---- What a defence says about itself.

    /** Engagement order: lower is sooner, -1 is not a target. Never players or what is theirs. */
    protected abstract int targetPriority(Entity entity);

    /** How far it looks for targets, blocks. */
    protected abstract double searchRange();

    /** The lowest it lays its gun on automatic, degrees above the horizon (an air defence never rakes its own base). */
    protected abstract float minAutoElevation();

    /** How close the gun has to lie to the lead before it fires, degrees. */
    protected abstract float fireTolerance();

    /** The first weapon's muzzle with the mount at rest, vehicle data coordinates. */
    protected abstract Vec3 muzzleRest();

    /** The round's speed, blocks a tick (its rounds fly flat: the long-range path). */
    protected abstract double roundSpeed();

    /** Where it looks out from (its sensor), above its feet, blocks. */
    protected abstract double sensorHeight();

    /** Whether a shot along this line now would endanger something friendly (a sentry's line through a villager). */
    protected boolean lineOfFireBlocked(Vec3 from, Vec3 to) {
        return false;
    }

    /** Anything that isn't a monster: players, villagers, pets and passive animals, and vehicles. */
    protected static boolean friendly(Entity entity) {
        return entity instanceof VehicleEntity || entity instanceof net.minecraft.world.entity.LivingEntity
                && !(entity instanceof net.minecraft.world.entity.monster.Enemy);
    }

    /** Whether anything friendly stands within reach of the line from {@code from} to {@code to}. */
    protected boolean friendlyInLine(Vec3 from, Vec3 to) {
        Vec3 path = to.subtract(from);
        double length2 = Math.max(1.0E-6D, path.lengthSqr());
        // Anything it may engage is never "friendly" (an enemy helicopter is a vehicle too: the HPJ-11 held its fire on it).
        for (Entity entity : this.level().getEntities(this, new AABB(from, to).inflate(1.5D),
                entity -> friendly(entity) && this.targetPriority(entity) < 0 && !this.carriesTarget(entity))) {
            Vec3 centre = entity.getBoundingBox().getCenter();
            double t = Mth.clamp(centre.subtract(from).dot(path) / length2, 0.0D, 1.0D);
            double reach = 0.6D + Math.max(entity.getBbWidth(), entity.getBbHeight()) * 0.5D;
            if (from.add(path.scale(t)).distanceToSqr(centre) <= reach * reach) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code entity} is an animal the target rides (a chicken jockey's chicken, a spider jockey's spider): part of
     * the threat, not something to protect. Its line of fire held while a zombie rode a chicken at the wall (the user's
     * report, 2026-11-19). Never a player or a vehicle, which a monster may ride with friends aboard.
     */
    private boolean carriesTarget(Entity entity) {
        if (this.target == null || entity instanceof Player || entity instanceof VehicleEntity) {
            return false;
        }
        for (Entity mount = this.target.getVehicle(); mount != null; mount = mount.getVehicle()) {
            if (mount == entity) {
                return true;
            }
        }
        return false;
    }

    // ---- State.

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_AUTO, false);
        builder.define(DATA_TRACKED, -1);
    }

    /** Switched to automatic (it engages on its own, crewed or not). */
    public boolean automatic() {
        return this.entityData.get(DATA_AUTO);
    }

    /**
     * On automatic the gun is its own: the driver doesn't stand in for an empty gunner's seat, so they can neither lay
     * nor fire it while it engages by itself (the speedboat; the user's review, 2026-11-19). A gunner still takes it over.
     */
    @Override
    protected boolean turretFallsBack() {
        return !this.automatic();
    }

    /** The entity it tracks, or null (both sides: the id is synced). */
    @Nullable
    public Entity tracked() {
        int id = this.entityData.get(DATA_TRACKED);
        return id < 0 ? null : this.level().getEntity(id);
    }

    /** Server: whether it fired within the last second (its status lamp shows red). */
    protected boolean firedLately() {
        return this.sinceFired < 20;
    }

    @Override
    public boolean hasTowKeyAction() {
        return true;
    }

    /** N: manual or automatic. */
    @Override
    public void towKey(ServerPlayer player) {
        if (!this.automatic() && !Config.autoDefenceEnabled()) {
            player.sendOverlayMessage(Component.translatable("message.jeg.auto_defence.disabled"));
            return;
        }
        this.setAutomatic(!this.automatic());
        player.sendOverlayMessage(Component.translatable(this.automatic() ? "message.jeg." + this.vehicleDataId().getPath() + ".auto_on"
                : "message.jeg.auto_defence.manual"));
    }

    public void setAutomatic(boolean automatic) {
        if (automatic != this.automatic() && !this.level().isClientSide()) {
            // Heard switching over: a relay, the mount stirring and a chirp, rising for automatic, falling for manual
            // (the user's review, 2026-11-19). Loud enough for whoever set it to hear from the screen or the seat.
            var cue = ttv.migami.jeg.init.ModSounds.ALL.get(ttv.migami.jeg.Reference.id(automatic ? "vehicle.sentry.auto_on" : "vehicle.sentry.auto_off"));
            if (cue != null) {
                this.level().playSound(null, this.getX(), this.getY() + this.sensorHeight(), this.getZ(), cue.get(),
                        net.minecraft.sounds.SoundSource.NEUTRAL, 1.4F, 1.0F);
            }
        }
        this.entityData.set(DATA_AUTO, automatic);
        this.target = null;
        this.entityData.set(DATA_TRACKED, -1);
    }

    // ---- The engagement (server).

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide() || this.isWrecked()) {
            return;
        }
        this.sinceFired++;
        if (this.tickCount % 40 == 0) {
            this.allowedInChunk = this.firstAutoInChunk();
        }
        if (!this.engagesOnItsOwn()) {
            if (this.target != null || this.entityData.get(DATA_TRACKED) >= 0) {
                this.target = null;
                this.entityData.set(DATA_TRACKED, -1);
            }
            return;
        }
        if (this.target != null && (!this.target.isAlive() || this.targetPriority(this.target) < 0 || !this.canSee(this.target)
                || this.target.distanceToSqr(this) > this.searchRange() * this.searchRange())) {
            this.target = null;
        }
        if (--this.searchCooldown <= 0) {
            this.searchCooldown = SEARCH_INTERVAL;
            int currentPriority = this.target == null ? Integer.MAX_VALUE : this.targetPriority(this.target);
            // Nothing can outrank priority 0, so skip the full area scan while already locked onto a top-priority target.
            if (currentPriority > 0) {
                Entity best = this.findTarget(currentPriority);
                if (best != null) {
                    this.target = best;
                }
            }
        }
        this.entityData.set(DATA_TRACKED, this.target == null ? -1 : this.target.getId());
        if (this.target == null) {
            return;
        }
        float[] lay = this.lead(this.target);
        if (lay == null || -lay[1] < this.minAutoElevation(this.target)) {
            return;
        }
        float yawError = Mth.wrapDegrees(this.turretYaw() - lay[0]);
        float pitchError = this.turretPitch() - lay[1];
        if (Math.abs(yawError) <= this.fireTolerance() && Math.abs(pitchError) <= this.fireTolerance()) {
            Vec3 muzzle = this.barrelPoint(this.muzzleRest(), 1.0F);
            if (!this.lineOfFireBlocked(muzzle, this.target.getBoundingBox().getCenter())
                    && this.fireUnmanned(0, this.barrelDirection())) {
                this.sinceFired = 0;
            }
        }
    }

    /**
     * On automatic it never runs dry: no ammunition or power is drawn (the user's review, 2026-11-18: "the gun in
     * auto-mode should have unlimited ammo"). Manned, it feeds from its hold as before.
     */
    @Override
    protected boolean unmannedFireIsFree() {
        return true;
    }

    /** The lowest it lays its gun for this target, degrees above the horizon; {@link #minAutoElevation()} by default. */
    protected float minAutoElevation(Entity target) {
        return this.minAutoElevation();
    }

    /** On automatic, allowed by the server, and not over the chunk's cap. */
    protected boolean engagesOnItsOwn() {
        return this.automatic() && Config.autoDefenceEnabled() && this.allowedInChunk;
    }

    /** Whether it is among the first automatic defences in its chunk (by entity id) that the server's cap allows. */
    private boolean firstAutoInChunk() {
        int chunkX = this.getBlockX() >> 4;
        int chunkZ = this.getBlockZ() >> 4;
        AABB chunk = new AABB(chunkX << 4, this.level().getMinY(), chunkZ << 4, (chunkX << 4) + 16, this.level().getMaxY(), (chunkZ << 4) + 16);
        long before = this.level().getEntitiesOfClass(AutoDefenceEntity.class, chunk,
                other -> other != this && other.automatic() && other.getId() < this.getId()).size();
        return before < Config.autoDefencePerChunk();
    }

    @Nullable
    private Entity findTarget(int betterThanPriority) {
        double range = this.searchRange();
        double rangeSq = range * range;
        var candidates = this.level().getEntities(this, this.getBoundingBox().inflate(range), entity -> {
            int priority = this.targetPriority(entity);
            return priority >= 0 && priority < betterThanPriority && entity.distanceToSqr(this) <= rangeSq;
        });
        if (candidates.isEmpty()) {
            return null;
        }
        // Check highest-priority and closest candidates first so we only raycast line-of-sight until a visible target is found.
        if (candidates.size() > 1) {
            candidates.sort(Comparator.<Entity>comparingInt(this::targetPriority)
                    .thenComparingDouble(entity -> entity.distanceToSqr(this)));
        }
        for (int i = 0, n = candidates.size(); i < n; i++) {
            Entity candidate = candidates.get(i);
            if (this.canSee(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    protected boolean canSee(Entity entity) {
        Vec3 eye = this.position().add(0.0D, this.sensorHeight(), 0.0D);
        Vec3 aim = entity.getBoundingBox().getCenter();
        return this.level().clip(new ClipContext(eye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /**
     * Where to lay the gun to meet the target (turret yaw and pitch in the turret's terms, pitch negative up): its
     * position when a round fired now would reach it.
     */
    @Nullable
    protected float[] lead(Entity target) {
        Vec3 muzzle = this.barrelPoint(this.muzzleRest(), 1.0F);
        Vec3 aim = target.getBoundingBox().getCenter();
        Vec3 velocity = target.getDeltaMovement();
        for (int i = 0; i < 3; i++) {
            double time = aim.distanceTo(muzzle) / Math.max(0.5D, this.roundSpeed());
            aim = target.getBoundingBox().getCenter().add(velocity.scale(time));
        }
        Vec3 to = aim.subtract(muzzle);
        float worldYaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
        float elevation = (float) (Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        return new float[]{Mth.wrapDegrees(this.getYRot() - worldYaw), -elevation};
    }

    @Override
    @Nullable
    protected float[] turretAutoLay() {
        if (!this.engagesOnItsOwn() || this.target == null) {
            return null;
        }
        float[] lay = this.lead(this.target);
        if (lay == null) {
            return null;
        }
        // Never laid below its floor: it follows a low target there and waits.
        return new float[]{lay[0], Math.min(lay[1], -this.minAutoElevation(this.target))};
    }

    /**
     * Its rotary gun (the HPJ-11's) spools up while it engages on its own. A machine gun has nothing to spool: the sentry
     * and the speedboat played the minigun's wind-up on automatic (the user's review, 2026-11-19).
     */
    @Override
    protected boolean rotaryGunDemanded() {
        var weapons = this.vehicleData().defaults().weapons();
        return !this.level().isClientSide() && this.engagesOnItsOwn() && this.target != null
                && !weapons.isEmpty() && this.isRotaryWeapon(weapons.getFirst());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Automatic", this.automatic());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_AUTO, input.getBooleanOr("Automatic", false));
    }
}
