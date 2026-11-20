package ttv.migami.jeg.vehicle.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.data.subdata.OBBInfo;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.support.SupportModules;
import ttv.migami.jeg.vehicle.support.SupportServices;
import ttv.migami.jeg.vehicle.support.SupportShell;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * FSV-6 field support vehicle: a 6x6 truck with an expansible van body. It drives like the old truck (W/S throttle,
 * coasting when released; A/D steer; Ctrl brakes). Parked and squared up, holding R for a second deploys it: the jacks
 * level the body, the sides slide out, the rear door folds into a ramp, the awning unrolls and the light mast rises,
 * and the room becomes walk-in ({@link SupportShell}). Holding R from the driver's seat again packs it up.
 */
public final class SupportVehicleEntity extends VehicleEntity {
    private static final EntityDataAccessor<Float> PROGRESS = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DEPLOYING = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> STATUS = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> STATUS_POS = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> STATUS_AGE = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> POWER = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> HORN = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> HOLD = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LIFT = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CARGO = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.INT);
    /** What is fitted in each slot up to the lamp (drawn by the client); the beacon's power rack is not drawn. */
    private static final int DRAWN_FITTINGS = SupportModules.FIT_SLOTS;
    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<net.minecraft.world.item.ItemStack>[] FITTED = new EntityDataAccessor[DRAWN_FITTINGS];
    static {
        for (int i = 0; i < DRAWN_FITTINGS; i++) FITTED[i] = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.ITEM_STACK);
    }
    /** Bit per wall bay: its furnace is burning. */
    private static final EntityDataAccessor<Integer> FURNACES_LIT = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.INT);
    /** The vehicle on the tow hitch (entity id), or -1. */
    private static final EntityDataAccessor<Integer> TOWED = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BRAKING = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> REVERSING = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> BEACON_LEVEL = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BEACON_EFFECT = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BEACON_LIT = SynchedEntityData.defineId(SupportVehicleEntity.class, EntityDataSerializers.BOOLEAN);

    /** Ticks of cranking before the diesel takes the throttle. */
    public static final int START_TICKS = 30;
    /** The engine idles this long after the driver leaves, then stops. */
    private static final int SHUTDOWN_TICKS = 80;
    /** Holding R this long deploys or packs up. */
    public static final int HOLD_TICKS = 20;
    /** How long a refusal stays on the HUD. */
    private static final int STATUS_SHOW_TICKS = 80;
    /** Below this speed (blocks per tick) the vehicle counts as stopped. */
    private static final double STOPPED = .02;

    private int parkedTicks;
    private boolean holdFired;
    // The deploy plan: where the vehicle started and where it stands deployed.
    private Vec3 planFrom = Vec3.ZERO, planTarget = Vec3.ZERO;
    private float planFromYaw, planYaw;
    private int planFloor;
    private double restY;
    private boolean hasPlan;
    // Client-side presentation.
    private float progressO, progressShown;
    private float travel, travelO, steer, steerO;
    private int boxMode = -1;
    private final SupportModules modules = new SupportModules(this);
    /** Which bunks hold a bed this vehicle placed (left, right), to tell a bed destroyed from one taken out. */
    private final boolean[] bunkBeds = new boolean[2];
    @org.jetbrains.annotations.Nullable private java.util.UUID towedId;
    /** The tow bar from the pintle to the towed vehicle's nose, in blocks. */
    private static final double TOW_BAR = 1.2;
    /** A tow eye (a gun's lunette) drops straight onto the pintle: only the coupling's length between them. */
    private static final double TOW_BAR_EYE = 0.3;
    /** The rear pintle, model pixels. */
    public static final Vec3 PINTLE = new Vec3(0, 8, 64);
    private boolean modulesDropped;

    public SupportVehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.setVehicleData(Reference.id("truck"));
    }

    @Override public boolean hasVehicleWeapons() { return false; }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PROGRESS, 0F);
        builder.define(DEPLOYING, false);
        builder.define(STATUS, 0);
        builder.define(STATUS_POS, BlockPos.ZERO);
        builder.define(STATUS_AGE, 0);
        builder.define(POWER, false);
        builder.define(AGE, 0);
        builder.define(HORN, false);
        builder.define(HOLD, 0);
        builder.define(LIFT, 0F);
        builder.define(CARGO, 0);
        for (var fitted : FITTED) builder.define(fitted, net.minecraft.world.item.ItemStack.EMPTY);
        builder.define(FURNACES_LIT, 0);
        builder.define(BEACON_LIT, false);
        builder.define(BEACON_LEVEL, 0);
        builder.define(BEACON_EFFECT, 0);
        builder.define(BRAKING, false);
        builder.define(REVERSING, false);
        builder.define(TOWED, -1);
    }

    // ---- State.

    /** 0 packed to 1 deployed. */
    public float deployProgress() { return this.entityData.get(PROGRESS); }
    public float deployProgress(float partial) { return Mth.lerp(partial, this.progressO, this.level().isClientSide() ? this.progressShown : this.deployProgress()); }
    /** Heading for deployed (true) or packed (false). */
    public boolean deployTarget() { return this.entityData.get(DEPLOYING); }
    public boolean isDeployed() { return this.deployProgress() >= 1F; }
    public boolean isPacked() { return this.deployProgress() <= 0F; }
    public boolean isMovingParts() { return !this.isDeployed() && !this.isPacked(); }
    /** How far the jacks have lifted the body, in blocks (for the jacks' reach). */
    public float bodyLift() { return this.entityData.get(LIFT); }
    public SupportShell.Status status() {
        int ordinal = this.entityData.get(STATUS);
        SupportShell.Status[] values = SupportShell.Status.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SupportShell.Status.OK;
    }
    public BlockPos statusPos() { return this.entityData.get(STATUS_POS); }
    /** Ticks since the status was set (the HUD shows refusals for a few seconds). */
    public int statusAge() { return this.entityData.get(STATUS_AGE); }
    public int holdTicks() { return this.entityData.get(HOLD); }
    public boolean powered() { return !this.isWrecked() && this.entityData.get(POWER); }
    public int powerAge() { return this.entityData.get(AGE); }
    public boolean engineRunning() { return this.powered() && this.powerAge() >= START_TICKS && this.isPacked(); }
    /** Deployed, the engine is off and the generator runs the shelter. */
    public boolean generatorRunning() { return !this.isWrecked() && this.deployProgress() > SupportRig.DOORS_END; }
    public boolean hornOn() { return this.powered() && this.entityData.get(HORN); }
    /** The driver's throttle (-1..1), for the engine's sound on the driver's own client. */
    public int landInputForAudio() { return this.landInput().forwardAxis(); }
    public SupportModules modules() { return this.modules; }
    /** The block fitted in a slot, as the client draws it. */
    public net.minecraft.world.item.ItemStack fittedForDisplay(int slot) {
        return slot >= 0 && slot < DRAWN_FITTINGS ? this.entityData.get(FITTED[slot]) : net.minecraft.world.item.ItemStack.EMPTY;
    }
    public boolean bunkBed(int side) { return this.bunkBeds[side]; }
    public void setBunkBed(int side, boolean placed) { this.bunkBeds[side] = placed; }

    /** The towed vehicle's entity id, or -1. */
    public int towedEntityId() { return this.entityData.get(TOWED); }

    /** The truck's own mass plus the load on the hitch (its {@code "mass"}): towing a gun or a hull costs pull. */
    @Override
    public double drivenMass() {
        double own = super.drivenMass();
        int towedId = this.towedEntityId();
        if (Double.isNaN(own) || towedId < 0 || !(this.level().getEntity(towedId) instanceof VehicleEntity towed)) return own;
        double load = towed.vehicleData().mass();
        return Double.isNaN(load) || load <= 0.0D ? own : own + load;
    }

    /** The roof beacon's pyramid level (from the mineral rack) and chosen effect (vanilla's encoding), for the console. */
    public int beaconLevelForDisplay() { return this.entityData.get(BEACON_LEVEL); }
    public int beaconEffectForDisplay() { return this.entityData.get(BEACON_EFFECT); }

    /** The driver is braking (the brake lights). */
    public boolean braking() { return this.entityData.get(BRAKING); }
    /** Moving backwards under power (the reversing lamps and alarm). */
    public boolean reversing() { return this.entityData.get(REVERSING); }

    /** The roof beacon is lit, for its beam. */
    public boolean beaconLit() { return this.entityData.get(BEACON_LIT); }
    public boolean furnaceLitForDisplay(int bay) { return (this.entityData.get(FURNACES_LIT) >> bay & 1) != 0; }

    /** The fittings changed: tell clients what to draw, and relight a deployed room for its lamps. */
    public void onFittingsChanged() {
        if (this.level().isClientSide()) return;
        for (int i = 0; i < DRAWN_FITTINGS; i++) {
            var stack = this.modules.fitted(i);
            if (!net.minecraft.world.item.ItemStack.matches(stack, this.entityData.get(FITTED[i]))) this.entityData.set(FITTED[i], stack.copy());
        }
        int lit = 0;
        for (int bay = 0; bay < SupportModules.WALL_BAYS; bay++) if (this.modules.furnaceLit(bay)) lit |= 1 << bay;
        this.entityData.set(FURNACES_LIT, lit);
        this.updateBeaconLit();
        this.entityData.set(BEACON_LEVEL, this.modules.beaconLevel());
        this.entityData.set(BEACON_EFFECT, this.modules.services().primary());
        if (this.isDeployed()) SupportShell.relight(this);
    }

    /** Hold slots in use, for the driver's panel. */
    public int occupiedCargoSlots() { return this.entityData.get(CARGO); }
    public float wheelTravel(float partial) { return Mth.lerp(partial, this.travelO, this.travel); }
    public float steering(float partial) { return Mth.lerp(partial, this.steerO, this.steer); }

    // ---- Hitboxes: the deployed set once the sides are out; while deploying or deployed, only the cab and front
    // wheels make the entity's own box, so the room's floor and walls (shell blocks) take clicks and walking.

    @Override protected List<OBBInfo.Box> obbBoxes() {
        OBBInfo deployed = this.vehicleData().deployedObb();
        if (deployed != null && this.deployProgress() >= SupportRig.SLIDES_OUT) {
            return deployed.boxes();
        }
        return super.obbBoxes();
    }

    @Override protected List<OBBInfo.Box> envelopeBoxes() {
        return this.isPacked() ? this.obbBoxes() : this.cabBoxes();
    }

    @Override protected List<OBBInfo.Box> entityPushBoxes() {
        return this.isPacked() ? this.obbBoxes() : this.cabBoxes();
    }

    private List<OBBInfo.Box> cabBoxes() {
        // The cab and the front wheels are everything ahead of the van body (data +Z is forward).
        List<OBBInfo.Box> cab = this.obbBoxes().stream().filter(box -> box.z() - box.halfDepth() > 1.5).toList();
        return cab.isEmpty() ? this.obbBoxes() : cab;
    }

    @Override public boolean preciseHitbox() { return true; }

    /**
     * Render culling and nearby-entity searches cover the whole vehicle. Unfolded, its entity box is only the cab (so the
     * room takes clicks), and culling by that made the whole vehicle vanish as the cab left the view.
     */
    @Override public net.minecraft.world.phys.AABB partBounds() {
        net.minecraft.world.phys.AABB box = super.partBounds();
        if (this.isPacked()) return box;
        double r = 6.5;
        return box.minmax(new net.minecraft.world.phys.AABB(this.getX() - r, this.getY() - .5, this.getZ() - r,
                this.getX() + r, this.getY() + 7.2, this.getZ() + r));
    }

    /**
     * Vanilla lets the driver's client report the vehicle's position (the move-vehicle packet). From the moment it starts
     * deploying until it is packed again, only the server places it: squared up on the grid, lifted on its jacks.
     */
    @Override protected boolean isLocalClientAuthoritative() {
        return this.isPacked() && !this.deployTarget() && super.isLocalClientAuthoritative();
    }

    // ---- Sound beyond entity range: the diesel on the road, the generator when deployed, the horn as the signal.
    @Override protected boolean sendsFarSoundTracks() { return true; }
    @Override protected float farSoundLoudness() {
        if (this.generatorRunning()) return .6F;
        if (!this.engineRunning()) return 0;
        return .9F + .5F * Math.abs(this.landInput().forwardAxis());
    }
    @Override protected float farSoundPitch() { return .9F + .12F * Math.abs(this.landInput().forwardAxis()); }
    @Override protected boolean farSoundSignal() { return this.hornOn(); }

    // ---- Ticking.

    @Override public void tick() {
        this.progressO = this.level().isClientSide() ? this.progressShown : this.deployProgress();
        super.tick();
        int mode = this.isPacked() ? 0 : this.deployProgress() >= SupportRig.SLIDES_OUT ? 2 : 1;
        if (mode != this.boxMode) {
            this.boxMode = mode;
            this.refreshDimensions();
        }
        if (this.level().isClientSide()) {
            this.tickPresentation();
            this.noteClientTow();
            return;
        }
        this.entityData.set(STATUS_AGE, Math.min(10000, this.statusAge() + 1));
        if (!this.isWrecked()) {
            this.modules.tick((ServerLevel) this.level());
            SupportServices.tick(this, (ServerLevel) this.level());
            if (this.tickCount % 10 == 0) this.onFittingsChanged();
        } else if (!this.modulesDropped) {
            // A wreck spills everything fitted in it and everything those modules held, as a broken chest would.
            this.modulesDropped = true;
            for (var stack : this.modules.allItems()) {
                if (!stack.isEmpty()) this.spawnAtLocation((ServerLevel) this.level(), stack.copy());
            }
            this.modules.fittings.clearContent();
        }
        if (this.tickCount % 5 == 0) {
            int occupied = 0;
            for (int i = 0; i < this.vehicleContainerSlots(); i++) if (!this.vehicleInventory().getItem(i).isEmpty()) occupied++;
            this.entityData.set(CARGO, occupied);
        }
        if (this.isWrecked() && !this.isPacked()) {
            SupportShell.remove((ServerLevel) this.level(), this.getUUID());
        }
        if (this.tickCount % 100 == 0 && !this.isPacked()) {
            SupportShell.anchor(this);
        }
        if (this.tickCount % 100 == 0) ttv.migami.jeg.vehicle.support.SupportBunks.track(this);
        if (this.tickCount % 20 == 0 && this.isDeployed()) SupportShell.checkBeds(this);
        this.tickTow((ServerLevel) this.level());
        this.updateBeaconLit();
        // A lit beacon hums as a placed one does, every four seconds.
        if (this.beaconLit() && this.tickCount % 80 == 0) this.playBeaconSound(net.minecraft.sounds.SoundEvents.BEACON_AMBIENT);
    }

    /** Set the synced lit state; switching on or off sounds as a beacon does (on deploying, mast up, a rack filled...). */
    private void updateBeaconLit() {
        boolean lit = ttv.migami.jeg.vehicle.support.SupportServices.beaconLit(this);
        if (lit == this.entityData.get(BEACON_LIT)) return;
        this.entityData.set(BEACON_LIT, lit);
        this.playBeaconSound(lit ? net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE : net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE);
    }

    /** A beacon sound from the roof mount. */
    public void playBeaconSound(net.minecraft.sounds.SoundEvent sound) {
        Vec3 at = SupportRig.world(this, 0, SupportRig.ROOF_TOP + 8, 24);
        this.level().playSound(null, at.x, at.y, at.z, sound, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Override protected boolean tickSpecialLandMovement(boolean server) {
        Entity driver = this.getControllingPassenger();
        var input = this.landInput();
        if (server) {
            this.parkedTicks = driver != null ? 0 : this.parkedTicks + 1;
            boolean power = this.vehicleEnergy() > 0 && !this.isWrecked() && this.isPacked()
                    && (driver != null || this.powered() && this.parkedTicks < SHUTDOWN_TICKS);
            this.entityData.set(POWER, power);
            this.entityData.set(AGE, power ? Math.min(100000, this.powerAge() + 1) : 0);
            if (power && this.tickCount % 4 == 0) this.consumeEnergy(driver != null ? 4 : 1);
            this.entityData.set(HORN, driver != null && power && input.fire());
            Vec3 heading = SupportRig.forward(this.getYRot());
            double along = this.getDeltaMovement().x * heading.x + this.getDeltaMovement().z * heading.z;
            this.entityData.set(BRAKING, driver != null && power && (input.brake() || input.forwardAxis() * along < -1.0E-3));
            this.entityData.set(REVERSING, power && this.engineRunning() && along < -.01);
            // Hold R from the driver's seat to deploy or pack up.
            boolean holding = driver instanceof ServerPlayer && input.reload();
            int hold = holding ? Math.min(HOLD_TICKS, this.holdTicks() + 1) : 0;
            this.entityData.set(HOLD, hold);
            if (!holding) this.holdFired = false;
            if (hold >= HOLD_TICKS && !this.holdFired) {
                this.holdFired = true;
                this.requestToggle((ServerPlayer) driver);
            }
            this.tickDeploy();
        }
        if (!this.isPacked()) {
            // Standing on its jacks (or lifting onto them): the wheels do nothing.
            this.setDeltaMovement(Vec3.ZERO);
            return true;
        }
        if (!this.engineRunning() && driver != null) {
            // Cranking: the wheels are braked, only gravity acts.
            Vec3 m = this.getDeltaMovement();
            this.moveGroundVehicle(new Vec3(m.x * .5, m.y - .08, m.z * .5));
            return true;
        }
        return false;
    }

    private void requestToggle(ServerPlayer operator) {
        if (this.deployTarget()) {
            this.requestPackUp();
        } else {
            this.requestDeploy(operator);
        }
    }

    /** Check the parking spot and start deploying, or say why not. */
    public SupportShell.Status requestDeploy(ServerPlayer operator) {
        if (this.isWrecked()) return SupportShell.Status.OK;
        if (this.isMovingParts() && !this.deployTarget()) {
            // Packing up: reverse straight back out again (the room is clear, it was just vacated).
            this.entityData.set(DEPLOYING, true);
            return SupportShell.Status.OK;
        }
        if (this.getDeltaMovement().horizontalDistance() > STOPPED) {
            return this.refuse(SupportShell.Status.MOVING, null);
        }
        SupportShell.Plan plan = SupportShell.plan(this, operator);
        if (!plan.ok()) {
            return this.refuse(plan.status(), plan.where());
        }
        this.planFrom = this.position();
        this.planFromYaw = this.getYRot();
        this.planTarget = plan.target();
        this.planYaw = plan.yaw();
        this.planFloor = plan.floor();
        this.hasPlan = true;
        this.entityData.set(DEPLOYING, true);
        this.refuse(SupportShell.Status.OK, null);
        return SupportShell.Status.OK;
    }

    /** Clear the room and start packing up, or say why not (someone still inside). */
    public SupportShell.Status requestPackUp() {
        if (!this.deployTarget()) return SupportShell.Status.OK;
        int inside = SupportShell.playersInside(this);
        if (inside > 0) {
            return this.refuse(SupportShell.Status.PLAYERS_INSIDE, new BlockPos(inside, 0, 0));
        }
        SupportShell.checkBeds(this);
        boolean[] lostBeds = SupportShell.remove((ServerLevel) this.level(), this.getUUID());
        this.bunkBeds[0] = this.bunkBeds[1] = false;
        // A bed destroyed while deployed (an explosion, a mob) dropped as an item: it is used up here, never doubled.
        if (lostBeds[0]) this.modules.fittings.setItem(SupportModules.BUNK_LEFT, net.minecraft.world.item.ItemStack.EMPTY);
        if (lostBeds[1]) this.modules.fittings.setItem(SupportModules.BUNK_RIGHT, net.minecraft.world.item.ItemStack.EMPTY);
        this.restY = this.restingY();
        this.entityData.set(DEPLOYING, false);
        this.refuse(SupportShell.Status.OK, null);
        return SupportShell.Status.OK;
    }

    private SupportShell.Status refuse(SupportShell.Status status, BlockPos where) {
        this.entityData.set(STATUS, status.ordinal());
        this.entityData.set(STATUS_POS, where == null ? BlockPos.ZERO : where);
        this.entityData.set(STATUS_AGE, status == SupportShell.Status.OK ? STATUS_SHOW_TICKS : 0);
        return status;
    }

    private void tickDeploy() {
        float progress = this.deployProgress();
        boolean target = this.deployTarget();
        if (target && progress < 1F) {
            if (!this.hasPlan) {
                this.entityData.set(DEPLOYING, false);
                return;
            }
            float next = Math.min(1F, progress + 1F / SupportRig.DEPLOY_TICKS);
            if (next >= 1F && !SupportShell.place(this, this.planTarget, this.planYaw)) {
                // Something moved into the room while it unfolded: fold back up.
                this.refuse(SupportShell.Status.BLOCKED, BlockPos.containing(this.planTarget));
                this.entityData.set(DEPLOYING, false);
                this.restY = this.planFrom.y;
                return;
            }
            this.setProgress(next);
            if (next >= 1F) SupportShell.anchor(this);
        } else if (!target && progress > 0F) {
            float next = Math.max(0F, progress - 1F / SupportRig.DEPLOY_TICKS);
            this.setProgress(next);
            if (next <= 0F) {
                this.hasPlan = false;
                this.entityData.set(LIFT, 0F);
            }
        }
    }

    /** Move the body with the jacks: squared up and lifted while they extend, lowered onto its wheels as they retract. */
    private void setProgress(float progress) {
        this.entityData.set(PROGRESS, progress);
        float jacks = SupportRig.jacks(progress);
        if (this.deployTarget()) {
            float yaw = this.planFromYaw + Mth.wrapDegrees(this.planYaw - this.planFromYaw) * jacks;
            Vec3 at = this.planFrom.lerp(this.planTarget, jacks);
            this.setYRot(yaw);
            this.yRotO = yaw;
            this.setPos(at.x, at.y, at.z);
            this.entityData.set(LIFT, (float) (this.planTarget.y - this.planFrom.y) * jacks);
        } else {
            double y = Mth.lerp(jacks, this.restY, this.planTarget.y);
            this.setPos(this.getX(), y, this.getZ());
            this.entityData.set(LIFT, (float) (y - this.restY));
        }
        this.hurtMarked = true;
    }

    /** Where the wheels come down when the jacks retract: the highest ground under any tyre. */
    private double restingY() {
        double best = Double.NEGATIVE_INFINITY;
        for (double z : SupportRig.AXLE_Z) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 contact = SupportRig.world(this, side * SupportRig.WHEEL_X, 0, z);
                Double ground = SupportShell.groundBelow(this.level(), contact, 3);
                if (ground != null) best = Math.max(best, ground);
            }
        }
        return best == Double.NEGATIVE_INFINITY ? this.getY() : best;
    }

    /**
     * A bay was right-clicked in the deployed room: its module opens, or the Fit-out screen if the bay is empty or the
     * player is sneaking.
     */
    public boolean useBay(ServerPlayer player, BlockPos pos) {
        int bay = SupportShell.bayAt(this, pos);
        if (bay < 0) return false;
        if (player.isShiftKeyDown() || this.modules.fitted(bay).isEmpty() || !this.modules.open(player, bay, pos)) {
            this.openFitMenu(player);
        }
        return true;
    }

    /** Open the Fit-out screen. */
    public void openFitMenu(ServerPlayer player) {
        if (!this.modules.canUse(player)) return;
        int id = this.getId();
        player.openMenu(new net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<Integer>() {
            @Override public Integer getScreenOpeningData(ServerPlayer who) { return id; }
            @Override public net.minecraft.network.chat.Component getDisplayName() {
                return net.minecraft.network.chat.Component.translatable("gui.jeg.support_fit.title");
            }
            @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory,
                                                                                         net.minecraft.world.entity.player.Player who) {
                return new ttv.migami.jeg.vehicle.menu.SupportFitMenu(containerId, inventory, SupportVehicleEntity.this.modules.fittings, SupportVehicleEntity.this.modules);
            }
        });
    }

    /**
     * The screens reachable from a seat or the console's keys: the hold (-2) and the Fit-out console (-1). A module's own
     * screen opens only by clicking that module in the room (its bay, the beacon terminal), one at a time.
     */
    public void openModule(ServerPlayer player, int slot) {
        if (slot == -2) player.openMenu(this);
        else if (slot == -1) this.openFitMenu(player);
    }

    /** Open the beacon terminal: the power rack and the effects. */
    public void openBeaconMenu(ServerPlayer player) {
        if (!this.modules.canUse(player)) return;
        SupportVehicleEntity self = this;
        player.openMenu(new net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider<Integer>() {
            @Override public Integer getScreenOpeningData(ServerPlayer who) { return self.getId(); }
            @Override public net.minecraft.network.chat.Component getDisplayName() {
                return net.minecraft.network.chat.Component.translatable("gui.jeg.support_beacon.title");
            }
            @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory,
                                                                                         net.minecraft.world.entity.player.Player who) {
                return new ttv.migami.jeg.vehicle.menu.SupportBeaconMenu(containerId, inventory, self);
            }
        });
    }

    /** Modules with no screen of their own: the jukebox, the charging station, the beacon. */
    public boolean useModule(ServerPlayer player, int slot, SupportModules.Kind kind) {
        return SupportServices.use(this, player, slot, kind);
    }

    // ---- Tow hitch: the driver's tow key, or sneak-clicking the rear on foot, hitches the nearest empty vehicle behind
    // (a wreck too); again drops it.

    /** How far from the pintle a vehicle can be hitched, in blocks (to its position). */
    public static final double TOW_REACH = 7;

    /** The vehicle the hitch would take now: the nearest empty one within reach of the pintle, or null. Both sides. */
    @org.jetbrains.annotations.Nullable
    public VehicleEntity towCandidate() {
        if (!this.isPacked()) return null;
        Vec3 pintle = SupportRig.world(this, PINTLE.x, PINTLE.y, PINTLE.z);
        VehicleEntity best = null;
        double bestDistance = TOW_REACH * TOW_REACH;
        for (VehicleEntity other : this.level().getEntitiesOfClass(VehicleEntity.class, new net.minecraft.world.phys.AABB(pintle, pintle).inflate(TOW_REACH),
                v -> v != this && v.getPassengers().isEmpty() && v.canBeTowed() && !(v instanceof SupportVehicleEntity s && !s.isPacked()))) {
            double distance = other.position().distanceToSqr(pintle);
            if (distance < bestDistance) { best = other; bestDistance = distance; }
        }
        return best;
    }

    @Override
    public boolean hasTowKeyAction() {
        return true;
    }

    /** The driver pressed the tow key. */
    @Override
    public void towKey(ServerPlayer player) {
        if (this.getControllingPassenger() == player) this.toggleTow(player);
    }

    private void toggleTow(ServerPlayer player) {
        if (this.towedId != null) {
            this.releaseTow();
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jeg.support.tow_released"));
            return;
        }
        if (!this.isPacked()) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jeg.support.deploy_first_tow"));
            return;
        }
        VehicleEntity best = this.towCandidate();
        if (best == null) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jeg.support.tow_nothing"));
            return;
        }
        this.towedId = best.getUUID();
        this.entityData.set(TOWED, best.getId());
        player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jeg.support.tow_hitched", best.getType().getDescription()));
    }

    public void releaseTow() {
        this.towedId = null;
        this.entityData.set(TOWED, -1);
    }

    /** Where a towed vehicle stands (x and z; y is its own) and which way it faces. */
    public record TowPose(Vec3 position, float yaw) {}

    /**
     * The tow's geometry, shared by the server (which moves the towed vehicle) and the client (which draws it): with
     * the truck at {@code truckAt} facing {@code truckYaw} and the towed vehicle at {@code towedAt}, the towed vehicle
     * stands a bar's length from the pintle along the line from it to the pintle, facing along that line. Null when the
     * two are on top of each other.
     */
    @org.jetbrains.annotations.Nullable
    public TowPose towPose(VehicleEntity towed, Vec3 towedAt, Vec3 truckAt, float truckYaw) {
        Vec3 pintle = SupportRig.world(truckAt, truckYaw, PINTLE.x, PINTLE.y, PINTLE.z);
        Vec3 toPintle = new Vec3(pintle.x - towedAt.x, 0, pintle.z - towedAt.z);
        double distance = toPintle.length();
        if (distance < 1.0E-3) return null;
        Vec3 dir = toPintle.scale(1 / distance);
        Vec3 eye = towed.towEye();
        if (eye != null) {
            // Towed by its tow eye (a gun's trail): the eye sits a bar's length behind the pintle, the gun trailing
            // away from it, so it faces away from the truck.
            double reach = Math.sqrt(eye.x * eye.x + eye.z * eye.z);
            Vec3 target = pintle.subtract(dir.scale(TOW_BAR_EYE + reach));
            return new TowPose(new Vec3(target.x, towedAt.y, target.z), (float) (Mth.atan2(dir.x, -dir.z) * Mth.RAD_TO_DEG));
        }
        double nose = towed.getBbWidth() * .5 + .4;
        Vec3 target = pintle.subtract(dir.scale(nose + TOW_BAR));
        return new TowPose(new Vec3(target.x, towedAt.y, target.z), (float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG));
    }

    /** On a client: which truck has which vehicle on its hitch (the towed vehicle's id), as the trucks last ticked. */
    private static final java.util.Map<Integer, SupportVehicleEntity> CLIENT_TOWS = new java.util.HashMap<>();

    /** On a client: the truck towing {@code towed}, or null. Its renderer draws it from the truck's smooth position. */
    @org.jetbrains.annotations.Nullable
    public static SupportVehicleEntity clientTower(VehicleEntity towed) {
        SupportVehicleEntity truck = CLIENT_TOWS.get(towed.getId());
        if (truck == null) return null;
        if (truck.isRemoved() || truck.level() != towed.level() || truck.towedEntityId() != towed.getId()) {
            CLIENT_TOWS.remove(towed.getId());
            return null;
        }
        return truck;
    }

    /**
     * On a client: a vehicle on an FSV-6's hitch, placed where the tow puts it from the truck's smooth (interpolated)
     * position this frame, not where the server last sent it. The server moves it once a tick and the client eases
     * between packets, so behind a truck the driver moves smoothly it lagged a step and jumped, and the tow bar stretched
     * and snapped (the user, 2026-11-19: "can the vehicle being towed by the FSV not be glitchy and jumpy but smooth").
     * Its renderer and the tow bar draw it here; its height stays its own (it rides the ground). Null when nothing tows it.
     */
    @org.jetbrains.annotations.Nullable
    public static TowPose clientTowPose(VehicleEntity towed, float partialTick) {
        SupportVehicleEntity truck = clientTower(towed);
        if (truck == null) return null;
        return truck.towPose(towed, towed.getPosition(partialTick), truck.getPosition(partialTick),
                Mth.rotLerp(partialTick, truck.yRotO, truck.getYRot()));
    }

    /** On a client: the trucks whose room is out (deployed past the slide-outs), as they last ticked. */
    private static final java.util.Set<SupportVehicleEntity> CLIENT_ROOMS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    /**
     * On a client: whether a point is inside, or within {@code margin} blocks of, a deployed FSV-6's room (floor to roof,
     * slide-out wall to slide-out wall, front wall to back). Smoke puffs carry no collision, so the blast renderer hides
     * those that come in through the floor or walls.
     */
    public static boolean insideClientRoom(double x, double y, double z, double margin) {
        if (CLIENT_ROOMS.isEmpty()) return false;
        double m = margin * 16.0;
        for (SupportVehicleEntity truck : CLIENT_ROOMS) {
            if (truck.isRemoved() || !truck.isDeployed()) continue;
            Vec3 f = SupportRig.forward(truck.getYRot());
            double dx = (x - truck.getX()) * 16.0, dz = (z - truck.getZ()) * 16.0, my = (y - truck.getY()) * 16.0;
            double mx = f.z * dx - f.x * dz, mz = -f.x * dx - f.z * dz;
            if (Math.abs(mx) <= SupportRig.INTERIOR_X + 2.0 + m && my >= SupportRig.FLOOR_Y - 2.0 - m && my <= SupportRig.ROOF_TOP + m
                    && mz >= SupportRig.INTERIOR_FRONT - 4.0 - m && mz <= SupportRig.INTERIOR_REAR + 4.0 + m) {
                return true;
            }
        }
        return false;
    }

    private void noteClientTow() {
        if (this.isDeployed()) CLIENT_ROOMS.add(this);
        else CLIENT_ROOMS.remove(this);
        CLIENT_TOWS.values().removeIf(truck -> truck == this || truck.isRemoved());
        int towed = this.towedEntityId();
        if (towed >= 0) CLIENT_TOWS.put(towed, this);
    }

    /** The towed vehicle's nose follows the pintle at the tow bar's length, turning to point at it; gravity and terrain act on it. */
    private void tickTow(ServerLevel level) {
        if (this.towedId == null) return;
        if (!(level.getEntity(this.towedId) instanceof VehicleEntity towed) || towed.isRemoved() || !towed.getPassengers().isEmpty()
                || !towed.canBeTowed() || !this.isPacked() || this.isWrecked()) {
            this.releaseTow();
            return;
        }
        if (towed.getId() != this.towedEntityId()) this.entityData.set(TOWED, towed.getId());
        Vec3 pintle = SupportRig.world(this, PINTLE.x, PINTLE.y, PINTLE.z);
        if (new Vec3(pintle.x - towed.getX(), 0, pintle.z - towed.getZ()).length() > 16) {
            this.releaseTow();
            return;
        }
        TowPose pose = towPose(towed, towed.position(), this.position(), this.getYRot());
        if (pose == null) return;
        towed.setYRot(pose.yaw());
        Vec3 target = pose.position();
        Vec3 move = new Vec3(target.x - towed.getX(), Math.min(0, towed.getDeltaMovement().y) - .08, target.z - towed.getZ());
        towed.move(net.minecraft.world.entity.MoverType.SELF, move);
        towed.setDeltaMovement(this.getDeltaMovement().x, towed.getDeltaMovement().y, this.getDeltaMovement().z);
        towed.hurtMarked = true;
    }

    /** The outside lockers are opened by clicking them (the underbody boxes either side, ahead of the middle axle). */
    @Override public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, Vec3 hitLocation) {
        if (!this.isPacked() && SupportShell.isInside(this, player)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer && hand == net.minecraft.world.InteractionHand.MAIN_HAND && player.getVehicle() == null
                && player.isShiftKeyDown() && isRearClick(this, hitLocation)) {
            // Sneak-clicking anywhere on the back of the vehicle works the hitch, whatever is in hand.
            this.toggleTow(serverPlayer);
            return net.minecraft.world.InteractionResult.SUCCESS_SERVER;
        }
        if (player instanceof ServerPlayer serverPlayer && player.getItemInHand(hand).isEmpty()) {
            // hitLocation is relative to the entity's position: turn it into the model's frame (pixels).
            Vec3 f = SupportRig.forward(this.getYRot());
            double mx = (hitLocation.x * f.z - hitLocation.z * f.x) * 16, my = hitLocation.y * 16;
            double mz = -(hitLocation.x * f.x + hitLocation.z * f.z) * 16;
            if (player.isShiftKeyDown()) return super.interact(player, hand, hitLocation);
            int locker = lockerAt(this, hitLocation);
            if (locker >= 0) {
                if (!this.modules.fitted(locker).isEmpty()) {
                    this.modules.open(serverPlayer, locker, null);
                } else {
                    serverPlayer.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jeg.support.locker_empty"));
                }
                return net.minecraft.world.InteractionResult.SUCCESS_SERVER;
            }
        }
        return super.interact(player, hand, hitLocation);
    }

    /**
     * The outside locker a click (relative to the entity's position) lands on: the underbody boxes either side, ahead of
     * the middle axle ({@code generate.py}: "locker box"). Returns its fitting slot, or -1.
     */
    public static int lockerAt(SupportVehicleEntity vehicle, Vec3 hitLocation) {
        Vec3 f = SupportRig.forward(vehicle.getYRot());
        double mx = (hitLocation.x * f.z - hitLocation.z * f.x) * 16, my = hitLocation.y * 16;
        double mz = -(hitLocation.x * f.x + hitLocation.z * f.z) * 16;
        if (my < 14 && Math.abs(mx) > 9 && mz > -4 && mz < 13) return mx > 0 ? SupportModules.LOCKER_LEFT : SupportModules.LOCKER_RIGHT;
        return -1;
    }

    /** A click (relative to the entity's position) on the rear quarter of the body: where the hitch is worked from on foot. */
    public static boolean isRearClick(SupportVehicleEntity vehicle, Vec3 hitLocation) {
        Vec3 f = SupportRig.forward(vehicle.getYRot());
        double mz = -(hitLocation.x * f.x + hitLocation.z * f.z) * 16;
        return mz > 44;
    }

    @Override public void remove(RemovalReason reason) {
        if (reason.shouldDestroy() && this.level() instanceof ServerLevel level) {
            SupportShell.remove(level, this.getUUID());
            ttv.migami.jeg.vehicle.support.SupportBunks.forget(this);
        }
        super.remove(reason);
    }

    private void tickPresentation() {
        this.progressShown = this.deployProgress();
        this.travelO = this.travel;
        this.steerO = this.steer;
        Vec3 f = SupportRig.forward(this.getYRot());
        double along = (this.getX() - this.xo) * f.x + (this.getZ() - this.zo) * f.z;
        if (Math.abs(along) > 2) along = 0;
        this.travel += (float) along;
        float turn = Mth.wrapDegrees(this.getYRot() - this.yRotO);
        float steerTarget = Math.abs(along) < 1.0E-3 ? this.landInput().strafeAxis() * 20F : Mth.clamp(turn * 6F, -32F, 32F);
        this.steer += (steerTarget - this.steer) * .2F;
        if (this.engineRunning() && this.tickCount % Math.max(2, 8 - 4 * Math.abs(this.landInput().forwardAxis())) == 0) {
            Vec3 stack = SupportRig.world(this, SupportRig.EXHAUST_TIP.x, SupportRig.EXHAUST_TIP.y, SupportRig.EXHAUST_TIP.z);
            ttv.migami.jeg.vehicle.effect.SmokeFx.emit(this.level(), stack, new Vec3(0, .06, 0), ttv.migami.jeg.vehicle.effect.SmokeFx.GREY, 0.3F);
        } else if (this.powered() && this.powerAge() < START_TICKS && this.powerAge() > 8 && this.tickCount % 2 == 0) {
            Vec3 stack = SupportRig.world(this, SupportRig.EXHAUST_TIP.x, SupportRig.EXHAUST_TIP.y, SupportRig.EXHAUST_TIP.z);
            ttv.migami.jeg.vehicle.effect.SmokeFx.emit(this.level(), stack, new Vec3(0, .08, 0), ttv.migami.jeg.vehicle.effect.SmokeFx.DARK, 0.45F);
        }
        if (this.generatorRunning() && this.tickCount % 10 == 0) {
            // The generator vents sideways, out from under the slide-out and clear of its wall (its stack under the floor
            // sent the smoke up through the room, the user's report of 2026-11-19); RoomSmoke hides any that drifts in.
            Vec3 gen = SupportRig.world(this, SupportRig.GENERATOR_OUTLET.x, SupportRig.GENERATOR_OUTLET.y, SupportRig.GENERATOR_OUTLET.z);
            Vec3 out = SupportRig.world(Vec3.ZERO, this.getYRot(), -16.0, 0.0, 0.0).scale(0.08);
            ttv.migami.jeg.vehicle.effect.SmokeFx.emit(this.level(), gen, out.add(0, .015, 0), ttv.migami.jeg.vehicle.effect.SmokeFx.GREY, 0.22F);
        }
    }

    // ---- Saving.

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putFloat("DeployProgress", this.deployProgress());
        out.putBoolean("Deploying", this.deployTarget());
        out.putBoolean("HasPlan", this.hasPlan);
        out.putDouble("PlanFromX", this.planFrom.x);
        out.putDouble("PlanFromY", this.planFrom.y);
        out.putDouble("PlanFromZ", this.planFrom.z);
        out.putFloat("PlanFromYaw", this.planFromYaw);
        out.putDouble("PlanX", this.planTarget.x);
        out.putDouble("PlanY", this.planTarget.y);
        out.putDouble("PlanZ", this.planTarget.z);
        out.putFloat("PlanYaw", this.planYaw);
        out.putInt("PlanFloor", this.planFloor);
        out.putDouble("RestY", this.restY);
        this.modules.save(out.child("Support"));
        if (this.towedId != null) out.store("Towed", net.minecraft.core.UUIDUtil.CODEC, this.towedId);
        out.putBoolean("BunkBedLeft", this.bunkBeds[0]);
        out.putBoolean("BunkBedRight", this.bunkBeds[1]);
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        this.hasPlan = in.getBooleanOr("HasPlan", false);
        this.planFrom = new Vec3(in.getDoubleOr("PlanFromX", this.getX()), in.getDoubleOr("PlanFromY", this.getY()), in.getDoubleOr("PlanFromZ", this.getZ()));
        this.planFromYaw = in.getFloatOr("PlanFromYaw", this.getYRot());
        this.planTarget = new Vec3(in.getDoubleOr("PlanX", this.getX()), in.getDoubleOr("PlanY", this.getY()), in.getDoubleOr("PlanZ", this.getZ()));
        this.planYaw = in.getFloatOr("PlanYaw", this.getYRot());
        this.planFloor = in.getIntOr("PlanFloor", 0);
        this.restY = in.getDoubleOr("RestY", this.getY());
        in.child("Support").ifPresent(this.modules::load);
        this.towedId = in.read("Towed", net.minecraft.core.UUIDUtil.CODEC).orElse(null);
        this.bunkBeds[0] = in.getBooleanOr("BunkBedLeft", false);
        this.bunkBeds[1] = in.getBooleanOr("BunkBedRight", false);
        float progress = Mth.clamp(in.getFloatOr("DeployProgress", 0F), 0F, 1F);
        boolean deploying = in.getBooleanOr("Deploying", false) && this.hasPlan;
        if (!this.hasPlan) progress = 0F;
        this.entityData.set(PROGRESS, progress);
        this.entityData.set(DEPLOYING, deploying);
        this.progressO = progress;
    }
}
