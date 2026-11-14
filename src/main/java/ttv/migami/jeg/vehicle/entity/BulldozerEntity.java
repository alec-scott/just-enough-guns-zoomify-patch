package ttv.migami.jeg.vehicle.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.terrain.BulldozerTerrainWork;
import ttv.migami.jeg.vehicle.util.BulldozerRig;

/**
 * BD-40 crawler dozer. It drives with the same steered-track model as the other land vehicles (W/S throttle,
 * A/D steer or pivot, Ctrl brake); Space raises and Shift lowers the blade, G toggles the grade lock. A lowered
 * blade clears whatever it is pushed into, and the material it has not cleared yet holds the machine back.
 */
public final class BulldozerEntity extends VehicleEntity {
    private static final EntityDataAccessor<Float> LIFT = SynchedEntityData.defineId(BulldozerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LOAD = SynchedEntityData.defineId(BulldozerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> POWER = SynchedEntityData.defineId(BulldozerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> GRADE = SynchedEntityData.defineId(BulldozerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> STATUS = SynchedEntityData.defineId(BulldozerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(BulldozerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CARGO = SynchedEntityData.defineId(BulldozerEntity.class, EntityDataSerializers.INT);
    /** Lowest block Y the blade may cut while it is below grade; {@link #NO_FLOOR} at or above grade. */
    private static final EntityDataAccessor<Integer> FLOOR = SynchedEntityData.defineId(BulldozerEntity.class, EntityDataSerializers.INT);
    public static final int NO_FLOOR = Integer.MIN_VALUE;
    /** Ripper link angle, 0 carried to {@link BulldozerRig#RIPPER_MAX} with the shanks in the ground. */
    private static final EntityDataAccessor<Float> RIPPER = SynchedEntityData.defineId(BulldozerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> RIPPER_DOWN = SynchedEntityData.defineId(BulldozerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HORN = SynchedEntityData.defineId(BulldozerEntity.class, EntityDataSerializers.BOOLEAN);
    /** The block the ripper is laying and how many are left in the bay, for the operator's panel. */
    private static final EntityDataAccessor<net.minecraft.world.item.ItemStack> PAVING = SynchedEntityData.defineId(BulldozerEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> PAVING_COUNT = SynchedEntityData.defineId(BulldozerEntity.class, EntityDataSerializers.INT);
    /** Forward speed while the blade has more in its bite than it can clear in a tick, blocks per tick. */
    private static final double BUSY_CRAWL = .045;
    /** Ripper link travel per tick, degrees. */
    private static final float RIPPER_RATE = 3F;
    /** Ticks of cranking before the diesel takes the throttle (the length of the start-up recording). */
    public static final int START_TICKS = 34;
    /** The engine idles this long after the operator climbs out, then shuts down. */
    private static final int SHUTDOWN_TICKS = 80;
    /** Blade travel per tick of Space/Shift, in degrees of push-arm rotation. */
    private static final float LIFT_RATE = 1.2F;
    private final BulldozerTerrainWork work = new BulldozerTerrainWork();
    private int parkedTicks;
    private boolean gradePressed, ripperPressed;
    /** Forward speed limit while pushing material, eased so the machine never jerks between bites. */
    private double pushCap = .26;
    private float ripperO, ripperShown;
    // Client-side presentation state.
    private float liftO, liftShown;
    private float leftTravel, rightTravel, leftTravelO, rightTravelO;
    private float rpm, rpmO, lean, leanO, flap, flapO;
    private double speedO;
    private boolean clientInitialized;

    public BulldozerEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.setVehicleData(Reference.id("bulldozer"));
    }

    /** Civilian machinery must never inherit the generic vehicle's fallback guns. */
    @Override public boolean hasVehicleWeapons() { return false; }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(LIFT, 0F);
        builder.define(LOAD, 0F);
        builder.define(POWER, false);
        builder.define(GRADE, true);
        builder.define(STATUS, 0);
        builder.define(AGE, 0);
        builder.define(CARGO, 0);
        builder.define(FLOOR, NO_FLOOR);
        builder.define(RIPPER, 0F);
        builder.define(RIPPER_DOWN, false);
        builder.define(HORN, false);
        builder.define(PAVING, net.minecraft.world.item.ItemStack.EMPTY);
        builder.define(PAVING_COUNT, 0);
    }

    public float bladeLift() { return this.entityData.get(LIFT); }
    public float bladeLift(float partial) { return Mth.lerp(partial, this.liftO, this.level().isClientSide() ? this.liftShown : this.bladeLift()); }
    public float leftTravel(float partial) { return Mth.lerp(partial, this.leftTravelO, this.leftTravel); }
    public float rightTravel(float partial) { return Mth.lerp(partial, this.rightTravelO, this.rightTravel); }
    /** Engine speed from idle (0) to rated (1), smoothed on the client from throttle, travel and blade load. */
    public float engineRpm(float partial) { return Mth.lerp(partial, this.rpmO, this.rpm); }
    /** Chassis pitch from acceleration and braking, degrees; positive raises the nose. */
    public float lean(float partial) { return Mth.lerp(partial, this.leanO, this.lean); }
    /** Exhaust rain-flap opening, degrees. */
    public float exhaustFlap(float partial) { return Mth.lerp(partial, this.flapO, this.flap); }
    public float load() { return this.entityData.get(LOAD); }
    public int powerAge() { return this.entityData.get(AGE); }
    public boolean powered() { return !this.isWrecked() && this.entityData.get(POWER); }
    public boolean engineRunning() { return this.powered() && this.powerAge() >= START_TICKS; }
    public boolean gradeAssist() { return this.entityData.get(GRADE); }
    public int workStatus() { return this.entityData.get(STATUS); }
    public int occupiedCargoSlots() { return this.entityData.get(CARGO); }
    /**
     * With the blade below grade, the level it cuts to: one block under where the machine stood when the blade went
     * down. The machine drops into its own cut but the blade holds that level, so land is lowered by exactly one block.
     */
    public int cutFloor() { return this.entityData.get(FLOOR); }
    /** Ripper link angle in degrees, interpolated for rendering. */
    public float ripper(float partial) { return Mth.lerp(partial, this.ripperO, this.level().isClientSide() ? this.ripperShown : this.entityData.get(RIPPER)); }
    /** The operator has lowered the ripper to lay a surface behind the machine. */
    public boolean ripperDown() { return this.entityData.get(RIPPER_DOWN); }
    /** The ripper's shanks are fully in the ground. */
    public boolean ripperDeployed() { return this.entityData.get(RIPPER) >= BulldozerRig.RIPPER_MAX - .01F; }
    public boolean hornOn() { return this.powered() && this.entityData.get(HORN); }
    /** The block the ripper lays now (count 1), or empty. */
    public net.minecraft.world.item.ItemStack pavingStack() { return this.entityData.get(PAVING); }
    /** How many of that block are left in the ripper's bay. */
    public int pavingCount() { return this.entityData.get(PAVING_COUNT); }
    /** The ripper's paving bay (the hold's last row), marked in the hold's menu and taking full blocks only. */
    @Override public int toolSlotStart() { return BulldozerTerrainWork.pavingStart(this); }
    @Override public int toolSlotCount() { return BulldozerTerrainWork.PAVING_SLOTS; }

    /** Shots meet the fitted hull boxes (blade, arms, tracks, engine, canopy, tanks, ripper), not the empty space round them. */
    @Override public boolean preciseHitbox() { return true; }

    // Heard like the aircraft beyond entity range: a diesel working hard carries, its horn further still.
    @Override protected boolean sendsFarSoundTracks() { return true; }
    @Override protected float farSoundLoudness() {
        if (!this.engineRunning()) return 0;
        return 1.0F + .35F * Math.abs(this.landInput().forwardAxis()) + .45F * this.load();
    }
    @Override protected float farSoundPitch() { return .9F + .1F * Math.abs(this.landInput().forwardAxis()) + .08F * this.load(); }
    @Override protected boolean farSoundSignal() { return this.hornOn(); }
    public boolean hydraulicMoving() { return Math.abs((this.level().isClientSide() ? this.liftShown : this.bladeLift()) - this.liftO) > .01F; }
    /** Material in the bite the blade has not cleared, or something it cannot move, stops forward travel. */
    public boolean bladeHolding() {
        int status = this.workStatus();
        return status == BulldozerTerrainWork.BUSY || status == BulldozerTerrainWork.SOLID || status == BulldozerTerrainWork.PROTECTED;
    }

    /** Speed along the machine's heading, blocks per tick (negative in reverse). */
    public double travelSpeed() {
        Vec3 f = BulldozerRig.forward(this.getYRot());
        Vec3 m = this.getDeltaMovement();
        return m.x * f.x + m.z * f.z;
    }

    @Override public void tick() {
        this.liftO = this.level().isClientSide() ? this.liftShown : this.bladeLift();
        this.ripperO = this.level().isClientSide() ? this.ripperShown : this.entityData.get(RIPPER);
        super.tick();
        if (this.level().isClientSide()) {
            this.tickPresentation();
        } else {
            if (this.tickCount % 5 == 0) {
                int occupied = 0;
                for (int i = 0; i < this.vehicleContainerSlots(); i++) if (!this.vehicleInventory().getItem(i).isEmpty()) occupied++;
                this.entityData.set(CARGO, occupied);
                int source = BulldozerTerrainWork.pavingSource(this);
                this.entityData.set(PAVING, source < 0 ? net.minecraft.world.item.ItemStack.EMPTY : this.vehicleInventory().getItem(source).copyWithCount(1));
                this.entityData.set(PAVING_COUNT, BulldozerTerrainWork.pavingCount(this));
            }
            if (this.isWrecked()) {
                this.work.clear(this);
                this.entityData.set(POWER, false);
            }
        }
    }

    @Override protected boolean tickSpecialLandMovement(boolean server) {
        var input = this.landInput();
        Entity driver = this.getControllingPassenger();
        if (server) {
            this.parkedTicks = driver != null ? 0 : this.parkedTicks + 1;
            boolean power = this.vehicleEnergy() > 0 && !this.isWrecked() && (driver != null || this.powered() && this.parkedTicks < SHUTDOWN_TICKS);
            this.entityData.set(POWER, power);
            this.entityData.set(AGE, power ? Math.min(100000, this.powerAge() + 1) : 0);
            if (power && this.tickCount % 4 == 0) this.consumeEnergy(driver != null ? 6 : 2);
            boolean grade = driver != null && input.deployDecoy();
            if (grade && !this.gradePressed) this.entityData.set(GRADE, !this.gradeAssist());
            this.gradePressed = grade;
            float lift = this.bladeLift();
            float floor = this.gradeAssist() ? 0 : BulldozerRig.LIFT_MIN;
            if (driver != null && this.engineRunning()) lift += input.verticalAxis() * LIFT_RATE;
            lift = Mth.clamp(lift, floor, BulldozerRig.LIFT_MAX);
            this.entityData.set(LIFT, lift);
            if (lift >= 0) this.entityData.set(FLOOR, NO_FLOOR);
            else if (this.cutFloor() == NO_FLOOR) this.entityData.set(FLOOR, Mth.floor(this.getY() + 1.0E-3) - 1);
            boolean pushing = driver != null && this.engineRunning() && input.forwardAxis() > 0 && !input.brake();
            // R lowers and raises the ripper; the left mouse button sounds the horn.
            boolean rip = driver != null && input.reload();
            if (rip && !this.ripperPressed && this.engineRunning()) this.entityData.set(RIPPER_DOWN, !this.ripperDown());
            this.ripperPressed = rip;
            if (driver == null && !this.powered()) this.entityData.set(RIPPER_DOWN, false);
            float ripper = this.entityData.get(RIPPER);
            float ripperTarget = this.ripperDown() ? BulldozerRig.RIPPER_MAX : 0;
            if (this.powered()) ripper += Mth.clamp(ripperTarget - ripper, -RIPPER_RATE, RIPPER_RATE);
            this.entityData.set(RIPPER, ripper);
            this.entityData.set(HORN, driver != null && this.powered() && input.fire());
            int status = this.work.tick(this, pushing);
            if (this.ripperDeployed() && pushing && this.travelSpeed() > .01) this.work.pave(this);
            this.entityData.set(STATUS, status);
            this.entityData.set(LOAD, Mth.lerp(.35F, this.load(), this.work.load()));
        }
        if (!this.engineRunning()) {
            // Cranking or stopped: the tracks are locked, only gravity acts.
            Vec3 m = this.getDeltaMovement();
            this.moveGroundVehicle(new Vec3(m.x * .5, m.y - .08, m.z * .5));
            return true;
        }
        return false;
    }

    @Override protected double mobilityMultiplier() {
        // Pushing a full blade drags the machine down to a crawl; shanks in the ground slow it too.
        return super.mobilityMultiplier() * (1 - .6 * Mth.clamp(this.load(), 0, 1)) * (this.ripperDeployed() ? .7 : 1);
    }

    @Override protected void moveGroundVehicle(Vec3 velocity) {
        Vec3 f = BulldozerRig.forward(this.getYRot());
        double along = velocity.x * f.x + velocity.z * f.z;
        // Pushing into material the machine eases down towards a crawl as the blade loads up and back as it clears,
        // so it bulldozes steadily instead of stopping and starting at each bite. (Against something it cannot cut it
        // stops; the blade's collision box stops it too if it ever reaches uncut ground.)
        boolean busy = this.workStatus() == BulldozerTerrainWork.BUSY;
        double capTarget = busy ? BUSY_CRAWL : Mth.lerp(Mth.clamp(this.load(), 0, 1), .26, BUSY_CRAWL * 1.6);
        this.pushCap += (capTarget - this.pushCap) * (capTarget < this.pushCap ? .35 : .12);
        if (along > 0) {
            double keep = this.bladeHolding() && !busy ? 0 : Math.min(along, this.pushCap);
            velocity = velocity.subtract(f.x * (along - keep), 0, f.z * (along - keep));
        }
        if (this.landInput().forwardAxis() == 0) {
            // Hydrostatic drive: with the travel lever centred the tracks hold, so the machine stops rather than coasts.
            double scale = velocity.horizontalDistanceSqr() < 1.0E-4 ? 0 : .55;
            velocity = new Vec3(velocity.x * scale, velocity.y, velocity.z * scale);
        }
        super.moveGroundVehicle(velocity);
    }

    private void tickPresentation() {
        this.liftShown = this.bladeLift();
        this.ripperShown = this.entityData.get(RIPPER);
        this.leftTravelO = this.leftTravel;
        this.rightTravelO = this.rightTravel;
        this.rpmO = this.rpm;
        this.leanO = this.lean;
        this.flapO = this.flap;
        if (!this.clientInitialized) {
            this.liftO = this.liftShown;
            this.clientInitialized = true;
        }
        // Tracks follow the machine's actual motion: travel plus counter-rotation in turns (half gauge 17 px).
        Vec3 f = BulldozerRig.forward(this.getYRot());
        double along = (this.getX() - this.xo) * f.x + (this.getZ() - this.zo) * f.z;
        double turn = Mth.wrapDegrees(this.getYRot() - this.yRotO) * Mth.DEG_TO_RAD * (BulldozerRig.TRACK_HALF_GAUGE / 16);
        if (Math.abs(along) > 2 || Math.abs(turn) > 2) along = turn = 0;
        this.leftTravel += (float) (along + turn);
        this.rightTravel += (float) (along - turn);

        double speed = Math.abs(along);
        float throttle = Math.abs(this.landInput().forwardAxis()) + (this.landInput().strafeAxis() != 0 ? .6F : 0);
        float target = !this.engineRunning() ? 0 : Mth.clamp(.08F + (float) speed / .22F * .5F + Math.min(1, throttle) * .3F
                + this.load() * .5F + (this.hydraulicMoving() ? .12F : 0), 0, 1);
        this.rpm += (target - this.rpm) * (target > this.rpm ? .14F : .06F);
        double accel = along - this.speedO;
        this.speedO = along;
        float leanTarget = Mth.clamp((float) accel * -60F, -1.1F, 1.1F) + (this.load() > .5F ? -.35F : 0);
        this.lean += (leanTarget - this.lean) * .2F;
        float flapTarget = !this.powered() ? 0 : 14 + this.rpm * 55 + (this.random.nextFloat() - .5F) * (8 + this.rpm * 18);
        this.flap += (flapTarget - this.flap) * .5F;
        if (this.powered()) this.emitEffects(f, speed);
    }

    private void emitEffects(Vec3 f, double speed) {
        Vec3 stack = BulldozerRig.world(this, BulldozerRig.EXHAUST_TIP.x, BulldozerRig.EXHAUST_TIP.y, BulldozerRig.EXHAUST_TIP.z);
        float rising = this.rpm - this.rpmO;
        if (this.powerAge() < START_TICKS && this.powerAge() > 10 && this.tickCount % 2 == 0) {
            // Cranking: dark puffs until the engine catches.
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, stack.x, stack.y, stack.z, 0, .08, 0);
        } else if (rising > .012F || this.load() > .6F && this.tickCount % 2 == 0) {
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, stack.x, stack.y, stack.z, (this.random.nextFloat() - .5F) * .02, .1 + this.rpm * .06, (this.random.nextFloat() - .5F) * .02);
        } else if (this.tickCount % Math.max(2, 7 - Math.round(this.rpm * 5)) == 0) {
            this.level().addParticle(ParticleTypes.SMOKE, stack.x, stack.y, stack.z, 0, .05 + this.rpm * .05, 0);
        }
        if (speed > .04 && this.onGround()) {
            // Soil kicked up behind the tracks.
            for (int side = -1; side <= 1; side += 2) {
                if (this.random.nextFloat() > speed * 4) continue;
                Vec3 at = BulldozerRig.world(this, side * BulldozerRig.TRACK_HALF_GAUGE, 1, 28);
                BlockPos ground = BlockPos.containing(at.x, this.getY() - .2, at.z);
                var state = this.level().getBlockState(ground);
                if (state.getRenderShape() == RenderShape.INVISIBLE) continue;
                this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z,
                        -f.x * .05 + (this.random.nextFloat() - .5F) * .05, .12, -f.z * .05 + (this.random.nextFloat() - .5F) * .05);
            }
        }
    }

    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putFloat("BladeLift", this.bladeLift());
        out.putBoolean("GradeAssist", this.gradeAssist());
        out.putInt("CutFloor", this.cutFloor());
        out.putBoolean("RipperDown", this.ripperDown());
        out.putFloat("Ripper", this.entityData.get(RIPPER));
    }

    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        this.entityData.set(GRADE, in.getBooleanOr("GradeAssist", true));
        this.entityData.set(LIFT, Mth.clamp(in.getFloatOr("BladeLift", 0), this.gradeAssist() ? 0 : BulldozerRig.LIFT_MIN, BulldozerRig.LIFT_MAX));
        this.entityData.set(FLOOR, this.bladeLift() < 0 ? in.getIntOr("CutFloor", NO_FLOOR) : NO_FLOOR);
        this.entityData.set(RIPPER_DOWN, in.getBooleanOr("RipperDown", false));
        this.entityData.set(RIPPER, Mth.clamp(in.getFloatOr("Ripper", 0), 0, BulldozerRig.RIPPER_MAX));
        this.ripperO = this.entityData.get(RIPPER);
        this.liftO = this.bladeLift();
    }
}
