package ttv.migami.jeg.vehicle.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.data.subdata.OBBInfo;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;
import ttv.migami.jeg.vehicle.projectile.ShellEntity;
import ttv.migami.jeg.vehicle.util.FireMissions;
import ttv.migami.jeg.vehicle.util.FireSolution;
import ttv.migami.jeg.vehicle.util.HowitzerRig;
import ttv.migami.jeg.vehicle.util.ShellBallistics;

/**
 * The 105 mm light towed howitzer (design/howitzer/BRIEF.md), an M101A1-class gun on split trails. It is towed by the
 * FSV-6 by its lunette, trail first, packed; the gunner deploys it with the tow key (the trails open and the spades go
 * down), lays it by the sight within 23 degrees of the carriage (A/D shifts the trails for more), picks one of three
 * propellant charges, and fires HE or smoke. A fire mission comes from an observer's spyglass or typed coordinates: the
 * panel gives the bearing and the elevations (low and high arc) that land on it.
 */
public final class HowitzerEntity extends ShellGunEntity implements FireMissions.Gun {
    public static final String ID = "howitzer";
    /** Ticks to open the trails and set the spades, or to pack up. */
    public static final int DEPLOY_TICKS = 100;
    /** Gravity as the Type-63's rockets; three charges, each reaching about twice as far as the last. */
    public static final double GRAVITY = 0.05D;
    public static final List<ShellBallistics> CHARGES = List.of(
            new ShellBallistics(7.0D, GRAVITY, 600),
            new ShellBallistics(10.0D, GRAVITY, 600),
            new ShellBallistics(14.15D, GRAVITY, 600));
    /** 105 mm HE: 300 on what it strikes, 200 within 12; blocks as the mortar's round (power 8). */
    public static final ShellEntity.Warhead HE = new ShellEntity.Warhead(12.0F, 200.0F, 300.0F, 8.0F, 0.0D, 0);
    /** 105 mm smoke (base ejection): a screen where it lands, three smoke grenades wide; it hurts only what it strikes. */
    public static final ShellEntity.Warhead SMOKE = ShellEntity.Warhead.smokeShell(60.0F);
    public static final Identifier HE_WEAPON = Reference.id("vehicle_howitzer_he");
    public static final Identifier SMOKE_WEAPON = Reference.id("vehicle_howitzer_smoke");
    private static final List<Shell> SHELLS = List.of(
            new Shell(HE_WEAPON, HE, "hud.jeg.shell.he"),
            new Shell(SMOKE_WEAPON, SMOKE, "hud.jeg.shell.smoke"));
    /** A/D shifts the trails round, slowly (degrees per tick). */
    public static final float TRAIL_SHIFT_RATE = 0.5F;
    /** How far an observer's mark reaches the guns. */
    public static final double OBSERVER_GUN_RANGE = 4500.0D;
    /** From the trunnions to the muzzle along the bore, blocks. */
    public static final double MUZZLE_ARM = HowitzerRig.MUZZLE_REST.z - HowitzerRig.TRUNNION.z;

    private static final EntityDataAccessor<Float> DATA_DEPLOY = SynchedEntityData.defineId(HowitzerEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_DEPLOYING = SynchedEntityData.defineId(HowitzerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_CHARGE = SynchedEntityData.defineId(HowitzerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_TARGET = SynchedEntityData.defineId(HowitzerEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    private float deployO;
    private boolean chargeKeyHeld;
    /** Client: the wheels' roll (radians), from the distance the carriage moves. */
    private float wheelRoll, wheelRollO;
    /** Client: the fire mission's solution, recomputed every few ticks. */
    @Nullable
    private FireSolution solution;
    private long solvedAt = Long.MIN_VALUE;

    public HowitzerEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level, ID, 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_DEPLOY, 0.0F);
        builder.define(DATA_DEPLOYING, false);
        builder.define(DATA_CHARGE, 1);
        builder.define(DATA_TARGET, Optional.empty());
    }

    // ---- Charges and shells.

    /** The selected charge, 0 to 2 (shown as 1 to 3). */
    public int charge() {
        return Mth.clamp(this.entityData.get(DATA_CHARGE), 0, CHARGES.size() - 1);
    }

    public void setCharge(int charge) {
        this.entityData.set(DATA_CHARGE, Math.floorMod(charge, CHARGES.size()));
    }

    @Override
    public ShellBallistics ballistics() {
        return CHARGES.get(this.charge());
    }

    @Override
    public List<Shell> shells() {
        return SHELLS;
    }

    @Override
    public List<Vec3> muzzlesRest() {
        return List.of(HowitzerRig.MUZZLE_REST);
    }

    @Override
    public List<String> recoilBones() {
        return List.of("tube");
    }

    /** A howitzer's dispersion: a few blocks at 2 km. */
    @Override
    public double spread() {
        return 0.15D;
    }

    @Override
    public String reportSound() {
        return "vehicle.howitzer";
    }

    /** The loudest gun in the mod. */
    @Override
    public float reportLoudness() {
        return 3.2F;
    }

    /** Variable recoil: about a metre level, less as the gun is laid higher, so the breech clears the ground. */
    @Override
    public float recoilPixels() {
        return HowitzerRig.recoilLength(this.elevation(1.0F));
    }

    @Override
    public int recoilTicks() {
        return 14;
    }

    @Override
    public float minElevation() {
        return HowitzerRig.MIN_ELEVATION;
    }

    @Override
    public float maxElevation() {
        return HowitzerRig.MAX_ELEVATION;
    }

    @Override
    public String hudTitle() {
        return "hud.jeg.howitzer.title";
    }

    // ---- Deploying: the tow key opens the trails and sets the spades, or packs up for towing.

    /** 0 packed for towing, 1 deployed to fire. */
    public float deployProgress() {
        return this.entityData.get(DATA_DEPLOY);
    }

    public float deployProgress(float partialTick) {
        return Mth.lerp(partialTick, this.deployO, this.deployProgress());
    }

    public boolean isDeployed() {
        return this.deployProgress() >= 1.0F;
    }

    public boolean isPacked() {
        return this.deployProgress() <= 0.0F;
    }

    /** Where it is heading: deployed (true) or packed (false). */
    public boolean deployTarget() {
        return this.entityData.get(DATA_DEPLOYING);
    }

    public void setDeployTarget(boolean deployed) {
        this.entityData.set(DATA_DEPLOYING, deployed);
    }

    @Override
    public boolean hasTowKeyAction() {
        return true;
    }

    @Override
    public void towKey(ServerPlayer player) {
        boolean deploy = !this.deployTarget();
        this.setDeployTarget(deploy);
        player.sendOverlayMessage(Component.translatable(deploy ? "message.jeg.howitzer.deploying" : "message.jeg.howitzer.packing"));
    }

    @Override
    protected boolean canFireWeapons() {
        return this.isDeployed();
    }

    @Override
    protected boolean turretTravelLock() {
        return !this.isDeployed();
    }

    @Override
    @Nullable
    public Vec3 towEye() {
        return HowitzerRig.TOW_EYE;
    }

    @Override
    public boolean canBeTowed() {
        return this.isPacked() && !this.deployTarget();
    }

    @Override
    protected List<OBBInfo.Box> obbBoxes() {
        OBBInfo deployed = this.vehicleData().deployedObb();
        if (deployed != null && this.deployProgress() >= HowitzerRig.TRAILS_END) {
            return deployed.boxes();
        }
        return super.obbBoxes();
    }

    // ---- Fire mission.

    @Override
    @Nullable
    public BlockPos target() {
        return this.entityData.get(DATA_TARGET).orElse(null);
    }

    @Override
    public void setTarget(@Nullable BlockPos target) {
        this.entityData.set(DATA_TARGET, Optional.ofNullable(target));
        this.solution = null;
    }

    @Override
    public double observerReach() {
        return OBSERVER_GUN_RANGE;
    }

    /** The trunnions in the world (the solver's origin). */
    public Vec3 trunnion() {
        return this.position().add(HowitzerRig.TRUNNION.yRot(-this.getYRot() * Mth.DEG_TO_RAD));
    }

    /** The point a target block is aimed at: the middle of its top face. */
    public static Vec3 aimPoint(BlockPos target) {
        return FireMissions.aimPoint(target);
    }

    /** The solution for the target and charge now (client: cached for a few ticks); null with no target. */
    @Nullable
    public FireSolution solution() {
        BlockPos target = this.target();
        if (target == null) {
            return null;
        }
        long now = this.level().getGameTime();
        if (this.solution == null || now - this.solvedAt >= 5 || !this.level().isClientSide()) {
            this.solution = solve(this.ballistics(), this.trunnion(), target);
            this.solvedAt = now;
        }
        return this.solution;
    }

    public static FireSolution solve(ShellBallistics ballistics, Vec3 trunnion, BlockPos target) {
        return FireSolution.solve(ballistics, trunnion, MUZZLE_ARM, aimPoint(target), HowitzerRig.MIN_ELEVATION, HowitzerRig.MAX_ELEVATION);
    }

    @Override
    public void notifyCrew() {
        FireMissions.notifyCrew(this, this.getControllingPassenger() instanceof ServerPlayer gunner ? gunner : null);
    }

    // ---- Ticking.

    @Override
    public void processInput(ServerPlayer player, VehicleInput input) {
        super.processInput(player, input);
        // V picks the next charge (on the press, not while it is held).
        if (input.seekTarget() && !this.chargeKeyHeld && player == this.getControllingPassenger()) {
            this.setCharge(this.charge() + 1);
            ShellBallistics next = this.ballistics();
            player.sendOverlayMessage(Component.translatable("message.jeg.howitzer.charge", this.charge() + 1,
                    Math.round(next.muzzleVelocity() * next.muzzleVelocity() / next.gravity())));
        }
        this.chargeKeyHeld = input.seekTarget();
    }

    @Override
    public void tick() {
        this.deployO = this.deployProgress();
        this.wheelRollO = this.wheelRoll;
        double xo = this.getX(), zo = this.getZ();
        super.tick();
        if (!this.level().isClientSide()) {
            float progress = this.deployProgress();
            float step = 1.0F / DEPLOY_TICKS;
            float next = Mth.clamp(progress + (this.deployTarget() ? step : -step), 0.0F, 1.0F);
            if (next != progress) {
                this.entityData.set(DATA_DEPLOY, next);
                if (next == 1.0F || next == 0.0F) {
                    this.level().playSound(null, this.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.NEUTRAL, 0.35F, 0.7F);
                }
            }
        } else {
            // The wheels roll with the carriage as it is towed.
            Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
            double moved = (this.getX() - xo) * forward.x + (this.getZ() - zo) * forward.z;
            this.wheelRoll += (float) (moved / HowitzerRig.WHEEL_RADIUS);
        }
        this.tickTrails();
    }

    public float wheelRoll(float partialTick) {
        return Mth.lerp(partialTick, this.wheelRollO, this.wheelRoll);
    }

    /**
     * A/D, deployed, shifts the trails round. A player gunner's client is authoritative for the gun's yaw (vanilla sends
     * it up in its vehicle-move packet), so it turns there and the server follows, as the Type-63's carriage.
     */
    private void tickTrails() {
        Entity gunner = this.getControllingPassenger();
        VehicleInput input = this.landInput();
        if (!(gunner instanceof Player) || input == null || input.strafeAxis() == 0 || this.isWrecked() || !this.isDeployed()) {
            return;
        }
        boolean authoritative = this.level().isClientSide() ? this.isLocalInstanceAuthoritative() : !(gunner instanceof ServerPlayer);
        if (authoritative) {
            this.setYRot(Mth.wrapDegrees(this.getYRot() - input.strafeAxis() * TRAIL_SHIFT_RATE));
        }
    }

    // ---- The fire-control panel's extra rows: deploy state, charge and the fire mission.

    @Override
    public List<PanelRow> panelRows(@Nullable Entity viewer, float partialTick) {
        List<PanelRow> rows = new ArrayList<>();
        Component state;
        int stateColour;
        if (this.isDeployed()) {
            state = Component.translatable("hud.jeg.howitzer.deployed");
            stateColour = 0xFF6BD36B;
        } else if (this.isPacked() && !this.deployTarget()) {
            state = Component.translatable("hud.jeg.howitzer.packed");
            stateColour = 0xFFE0B040;
        } else {
            state = Component.translatable(this.deployTarget() ? "hud.jeg.howitzer.deploying" : "hud.jeg.howitzer.packing",
                    Math.round(this.deployProgress() * 100.0F));
            stateColour = 0xFFE0B040;
        }
        rows.add(new PanelRow(Component.translatable("hud.jeg.howitzer.trails"), state, stateColour));
        ShellBallistics ballistics = this.ballistics();
        rows.add(new PanelRow(Component.translatable("hud.jeg.howitzer.charge"), Component.translatable("hud.jeg.howitzer.charge_value",
                this.charge() + 1, Math.round(ballistics.muzzleVelocity() * ballistics.muzzleVelocity() / ballistics.gravity())), 0xFFECE3C4));
        BlockPos target = this.target();
        if (target == null) {
            rows.add(new PanelRow(Component.translatable("hud.jeg.howitzer.target"), Component.translatable("hud.jeg.howitzer.no_target"), 0xFF8C9490));
            return rows;
        }
        FireSolution solution = this.solution();
        rows.add(new PanelRow(Component.translatable("hud.jeg.howitzer.target"), Component.translatable("hud.jeg.howitzer.target_value",
                target.getX(), target.getZ(), Math.round(solution.range())), 0xFFECE3C4));
        float bearingNow = this.bearingExact(partialTick);
        float bearingError = Mth.wrapDegrees(solution.bearing() - bearingNow);
        rows.add(new PanelRow(Component.translatable("hud.jeg.howitzer.lay_bearing"),
                Component.literal(String.format(Locale.ROOT, "%.1f°%s", solution.bearing(), onMark(bearingError))),
                Math.abs(bearingError) <= 0.2F ? 0xFF6BD36B : 0xFFECE3C4));
        if (!solution.reachable()) {
            int reaching = -1;
            for (int c = 0; c < CHARGES.size() && reaching < 0; c++) {
                if (solve(CHARGES.get(c), this.trunnion(), target).reachable()) {
                    reaching = c;
                }
            }
            rows.add(new PanelRow(Component.translatable("hud.jeg.howitzer.lay_elevation"), reaching < 0
                    ? Component.translatable("hud.jeg.howitzer.out_of_range")
                    : Component.translatable("hud.jeg.howitzer.use_charge", reaching + 1), 0xFFE05A40));
            return rows;
        }
        float elevation = this.elevation(partialTick);
        rows.add(new PanelRow(Component.translatable("hud.jeg.howitzer.lay_low"), arc(solution.low(), solution.lowTicks(), elevation),
                colourFor(solution.low(), elevation)));
        rows.add(new PanelRow(Component.translatable("hud.jeg.howitzer.lay_high"), arc(solution.high(), solution.highTicks(), elevation),
                colourFor(solution.high(), elevation)));
        return rows;
    }

    /** The gun's compass bearing to a tenth of a degree. */
    public float bearingExact(float partialTick) {
        float worldYaw = Mth.rotLerp(partialTick, this.yRotO, this.getYRot()) - this.turretYaw(partialTick);
        return (float) Math.floorMod(Math.round((worldYaw + 180.0F) * 10.0F), 3600) / 10.0F;
    }

    private static String onMark(float error) {
        if (Math.abs(error) <= 0.2F) {
            return "  ON";
        }
        return String.format(Locale.ROOT, "  (%+.1f)", error);
    }

    private static Component arc(float elevation, int ticks, float now) {
        if (Float.isNaN(elevation)) {
            return Component.literal("—");
        }
        return Component.literal(String.format(Locale.ROOT, "%.1f°, %.1f s%s", elevation, ticks / 20.0F, onMark(elevation - now)));
    }

    private static int colourFor(float elevation, float now) {
        if (Float.isNaN(elevation)) {
            return 0xFF8C9490;
        }
        return Math.abs(elevation - now) <= 0.2F ? 0xFF6BD36B : 0xFFECE3C4;
    }

    // ---- Saving.

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putFloat("HowitzerDeploy", this.deployProgress());
        output.putBoolean("HowitzerDeploying", this.deployTarget());
        output.putInt("HowitzerCharge", this.charge());
        BlockPos target = this.target();
        if (target != null) {
            output.putLong("HowitzerTarget", target.asLong());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        float deploy = input.getFloatOr("HowitzerDeploy", 0.0F);
        this.entityData.set(DATA_DEPLOY, deploy);
        this.deployO = deploy;
        this.setDeployTarget(input.getBooleanOr("HowitzerDeploying", false));
        this.setCharge(input.getIntOr("HowitzerCharge", 1));
        long target = input.getLongOr("HowitzerTarget", Long.MIN_VALUE);
        this.setTarget(target == Long.MIN_VALUE ? null : BlockPos.of(target));
    }
}
