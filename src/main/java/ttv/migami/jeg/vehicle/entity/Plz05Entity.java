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
import ttv.migami.jeg.vehicle.data.subdata.SeatInfo;
import ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;
import ttv.migami.jeg.vehicle.util.FireMissions;
import ttv.migami.jeg.vehicle.util.FireSolution;
import ttv.migami.jeg.vehicle.util.Plz05Rig;
import ttv.migami.jeg.vehicle.util.ShellBallistics;

/**
 * The PLZ-05 155 mm self-propelled howitzer (Superb Warfare's, refit; design/plz05/BRIEF.md). A tracked hull with a
 * driver, a gunner in the turret, a 12.7 mm machine gun on the commander's hatch and a rider at the other hatch. It
 * fires the Mk42's 5-inch shells on three charges to about 6 km, only from a halt. With a fire mission (an observer's
 * spyglass mark or typed coordinates) the gunner engages the fire control (N) and the turret lays itself on the
 * solution for the selected charge and arc (V); otherwise the gun follows the gunner's view for direct fire.
 */
public final class Plz05Entity extends ShellGunEntity implements FireMissions.Gun {
    public static final String ID = "plz05";
    public static final int DRIVER_SEAT = 0, GUNNER_SEAT = 1, ROOF_GUNNER_SEAT = 2, RIDER_SEAT = 3;
    /** SBW's shell gravity; three charges, each reaching about twice as far as the last (to about 6 km). */
    public static final double GRAVITY = 0.06D;
    public static final List<ShellBallistics> CHARGES = List.of(
            new ShellBallistics(10.0D, GRAVITY, 700),
            new ShellBallistics(14.0D, GRAVITY, 700),
            new ShellBallistics(19.0D, GRAVITY, 700));
    public static final Identifier HE_WEAPON = Reference.id("vehicle_plz05_he");
    public static final Identifier AP_WEAPON = Reference.id("vehicle_plz05_ap");
    public static final Identifier CM_WEAPON = Reference.id("vehicle_plz05_cm");
    public static final Identifier GS_WEAPON = Reference.id("vehicle_plz05_gs");
    public static final Identifier HMG_WEAPON = Reference.id("vehicle_plz05_hmg");
    /** The Mk42's shells and warheads, as SBW's PLZ-05 fires them. */
    private static final List<Shell> SHELLS = List.of(
            new Shell(HE_WEAPON, Mk42Entity.HE, "hud.jeg.shell.he_proximity"),
            new Shell(AP_WEAPON, Mk42Entity.AP, "hud.jeg.shell.ap"),
            new Shell(CM_WEAPON, Mk42Entity.CM, "hud.jeg.shell.cluster"),
            new Shell(GS_WEAPON, Mk42Entity.GRAPESHOT, "hud.jeg.shell.grapeshot"));
    /** How far an observer's mark reaches this gun: its top charge's range and some. */
    public static final double OBSERVER_REACH = 6500.0D;
    /** The hull must have been still (under this many blocks a tick) for a few ticks before the gun fires. */
    public static final double HALT_SPEED = 0.03D;
    public static final int HALT_TICKS = 5;
    /** The fire control reports the gun laid within this much of the solution, degrees. */
    public static final float LAID_TOLERANCE = 0.05F;

    private static final EntityDataAccessor<Integer> DATA_MODE = SynchedEntityData.defineId(Plz05Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FIRE_CONTROL = SynchedEntityData.defineId(Plz05Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HALTED = SynchedEntityData.defineId(Plz05Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_TARGET = SynchedEntityData.defineId(Plz05Entity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);

    private boolean modeKeyHeld;
    private int stillTicks;
    private Vec3 lastPosition = Vec3.ZERO;
    /** The fire mission's solution for the present target, mode and position (both sides; recomputed when they change). */
    @Nullable
    private Lay lay;
    private long laidAt = Long.MIN_VALUE;
    @Nullable
    private Object layKey;

    /** A solution and the world yaw (degrees) the gun must point along to reach the target. */
    public record Lay(FireSolution solution, float worldYaw, boolean high) {
        public float elevation() {
            return this.high ? this.solution.high() : this.solution.low();
        }

        public int ticks() {
            return this.high ? this.solution.highTicks() : this.solution.lowTicks();
        }

        public boolean reachable() {
            return !Float.isNaN(this.elevation());
        }
    }

    public Plz05Entity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level, ID, 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODE, 4);
        builder.define(DATA_FIRE_CONTROL, false);
        builder.define(DATA_HALTED, true);
        builder.define(DATA_TARGET, Optional.empty());
    }

    // ---- Charge and arc: V steps through 1 low, 1 high, 2 low ... 3 high.

    public int mode() {
        return Mth.clamp(this.entityData.get(DATA_MODE), 0, CHARGES.size() * 2 - 1);
    }

    public void setMode(int mode) {
        this.entityData.set(DATA_MODE, Math.floorMod(mode, CHARGES.size() * 2));
        this.lay = null;
    }

    /** The selected charge, 0 to 2 (shown as 1 to 3). */
    public int charge() {
        return this.mode() / 2;
    }

    public boolean highArc() {
        return (this.mode() & 1) == 1;
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
        return List.of(Plz05Rig.MUZZLE_REST);
    }

    @Override
    public List<String> recoilBones() {
        return List.of("tube");
    }

    /** SBW's spread. */
    @Override
    public double spread() {
        return 0.06D;
    }

    @Override
    public String reportSound() {
        return "vehicle.plz05";
    }

    /** A 155 mm gun: the loudest in the mod with the 105. */
    @Override
    public float reportLoudness() {
        return 3.2F;
    }

    @Override
    public float recoilPixels() {
        return Plz05Rig.RECOIL_PIXELS;
    }

    @Override
    public int recoilTicks() {
        return 14;
    }

    @Override
    public float minElevation() {
        return Plz05Rig.MIN_ELEVATION;
    }

    @Override
    public float maxElevation() {
        return Plz05Rig.MAX_ELEVATION;
    }

    @Override
    public String hudTitle() {
        return "hud.jeg.plz05.title";
    }

    // ---- Seats and the roof machine gun.

    /** The gunner in the turret lays the gun, not the driver. */
    @Override
    public boolean laysGun(@Nullable Entity passenger) {
        return passenger != null && this.seatOf(passenger) == GUNNER_SEAT;
    }

    /** The hatch seats ride the turret roof and turn with it. */
    @Override
    protected boolean seatRidesTurret(SeatInfo seat) {
        return seat.index() == ROOF_GUNNER_SEAT || seat.index() == RIDER_SEAT;
    }

    @Nullable
    public Entity passengerIn(int seat) {
        for (Entity passenger : this.getPassengers()) {
            if (this.seatOf(passenger) == seat) {
                return passenger;
            }
        }
        return null;
    }

    /** A point given in hull coordinates with the turret straight ahead, in the world as the turret is turned now. */
    public Vec3 onTurret(Vec3 point, float partialTick) {
        float hullYaw = Mth.rotLerp(partialTick, this.yRotO, this.getYRot());
        float gunYaw = hullYaw - this.turretYaw(partialTick);
        Vec3 origin = Plz05Rig.TURRET_ORIGIN;
        return this.getPosition(partialTick).add(origin.yRot(-hullYaw * Mth.DEG_TO_RAD))
                .add(point.subtract(origin).yRot(-gunYaw * Mth.DEG_TO_RAD));
    }

    @Override
    @Nullable
    protected Vec3 seatGunMuzzle(VehicleWeaponInfo weapon, Vec3 direction) {
        if (!HMG_WEAPON.equals(weapon.weaponId())) {
            return null;
        }
        return this.onTurret(Plz05Rig.HMG_TRUNNION, 1.0F).add(direction.normalize().scale(Plz05Rig.HMG_ARM));
    }

    /** The gun fires only from a halt; the roof gun fires on the move. */
    @Override
    protected boolean canFireWeapon(VehicleWeaponInfo weapon) {
        return HMG_WEAPON.equals(weapon.weaponId()) || this.isHalted();
    }

    public boolean isHalted() {
        return this.entityData.get(DATA_HALTED);
    }

    // ---- Fire control: the gunner's N engages it; the turret then lays itself on the mission.

    public boolean fireControlEngaged() {
        return this.entityData.get(DATA_FIRE_CONTROL);
    }

    public void setFireControl(boolean engaged) {
        this.entityData.set(DATA_FIRE_CONTROL, engaged);
    }

    @Override
    public boolean hasTowKeyAction() {
        return true;
    }

    @Override
    public boolean takesTowKeyFrom(Player player) {
        return this.seatOf(player) == GUNNER_SEAT;
    }

    @Override
    public void towKey(ServerPlayer player) {
        if (this.fireControlEngaged()) {
            this.setFireControl(false);
            player.sendOverlayMessage(Component.translatable("message.jeg.plz05.fire_control_off"));
            return;
        }
        Lay lay = this.lay();
        if (lay == null) {
            player.sendOverlayMessage(Component.translatable("message.jeg.plz05.no_mission"));
            return;
        }
        if (!lay.reachable()) {
            player.sendOverlayMessage(Component.translatable("message.jeg.plz05.out_of_reach"));
            return;
        }
        this.setFireControl(true);
        player.sendOverlayMessage(Component.translatable("message.jeg.plz05.fire_control_on"));
    }

    @Override
    @Nullable
    protected float[] turretAutoLay() {
        if (!this.fireControlEngaged()) {
            return null;
        }
        Lay lay = this.lay();
        if (lay == null || !lay.reachable()) {
            return null;
        }
        return new float[]{Mth.wrapDegrees(this.getYRot() - lay.worldYaw()), -lay.elevation()};
    }

    /** Whether the gun is laid on the solution (the fire control's "ON"). */
    public boolean laidOnMission(float partialTick) {
        Lay lay = this.lay();
        if (lay == null || !lay.reachable()) {
            return false;
        }
        float worldYaw = Mth.rotLerp(partialTick, this.yRotO, this.getYRot()) - this.turretYaw(partialTick);
        return Math.abs(Mth.wrapDegrees(worldYaw - lay.worldYaw())) <= LAID_TOLERANCE
                && Math.abs(this.elevation(partialTick) - lay.elevation()) <= LAID_TOLERANCE;
    }

    @Override
    @Nullable
    public BlockPos target() {
        return this.entityData.get(DATA_TARGET).orElse(null);
    }

    @Override
    public void setTarget(@Nullable BlockPos target) {
        this.entityData.set(DATA_TARGET, Optional.ofNullable(target));
        this.lay = null;
    }

    @Override
    public double observerReach() {
        return OBSERVER_REACH;
    }

    @Override
    public void notifyCrew() {
        FireMissions.notifyCrew(this, this.passengerIn(GUNNER_SEAT) instanceof ServerPlayer gunner ? gunner : null);
    }

    /** The turret ring's centre in the world: the gun points along a line through it, so it is the solver's origin. */
    private Vec3 ringCentre() {
        return this.position().add(Plz05Rig.TURRET_ORIGIN.yRot(-this.getYRot() * Mth.DEG_TO_RAD));
    }

    /** The trunnions in the world with the gun pointing along {@code worldYaw}. */
    public Vec3 trunnionAt(float worldYaw) {
        return this.ringCentre().add(Plz05Rig.TRUNNION.subtract(Plz05Rig.TURRET_ORIGIN).yRot(-worldYaw * Mth.DEG_TO_RAD));
    }

    /**
     * The fire mission's lay for the selected charge and arc from where the hull is now, or null with no target. Kept
     * until the target, the mode or the hull's place changes (and refreshed every few seconds).
     */
    @Nullable
    public Lay lay() {
        BlockPos target = this.target();
        if (target == null) {
            return null;
        }
        Vec3 ring = this.ringCentre();
        Object key = List.of(target, this.mode(), Math.round(ring.x * 8.0D), Math.round(ring.y * 8.0D), Math.round(ring.z * 8.0D));
        long now = this.level().getGameTime();
        if (this.lay == null || !key.equals(this.layKey) || now - this.laidAt >= 100) {
            Vec3 aim = FireMissions.aimPoint(target);
            // The trunnions sit on the line from the ring's centre along the bore, so the bearing from the centre is
            // the bearing to lay; the solver then flies the shell from the trunnions on that bearing.
            float worldYaw = (float) (Mth.atan2(-(aim.x - ring.x), aim.z - ring.z) * Mth.RAD_TO_DEG);
            FireSolution solution = FireSolution.solve(this.ballistics(), this.trunnionAt(worldYaw), Plz05Rig.MUZZLE_ARM, aim,
                    Plz05Rig.MIN_ELEVATION, Plz05Rig.MAX_ELEVATION);
            this.lay = new Lay(solution, worldYaw, this.highArc());
            this.layKey = key;
            this.laidAt = now;
        }
        return this.lay;
    }

    // ---- Ticking.

    @Override
    public void processInput(ServerPlayer player, VehicleInput input) {
        super.processInput(player, input);
        if (this.seatOf(player) != GUNNER_SEAT) {
            return;
        }
        // V steps the charge and arc (on the press, not while it is held).
        if (input.seekTarget() && !this.modeKeyHeld) {
            this.setMode(this.mode() + 1);
            ShellBallistics next = this.ballistics();
            player.sendOverlayMessage(Component.translatable(this.highArc() ? "message.jeg.plz05.mode_high" : "message.jeg.plz05.mode_low",
                    this.charge() + 1, Math.round(next.muzzleVelocity() * next.muzzleVelocity() / next.gravity())));
            Lay lay = this.lay();
            if (this.fireControlEngaged() && lay != null && !lay.reachable()) {
                this.setFireControl(false);
                player.sendOverlayMessage(Component.translatable("message.jeg.plz05.out_of_reach"));
            }
        }
        this.modeKeyHeld = input.seekTarget();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        Vec3 now = this.position();
        double moved = now.subtract(this.lastPosition).horizontalDistance();
        this.lastPosition = now;
        this.stillTicks = moved < HALT_SPEED ? this.stillTicks + 1 : 0;
        boolean halted = this.stillTicks >= HALT_TICKS;
        if (halted != this.isHalted()) {
            this.entityData.set(DATA_HALTED, halted);
        }
        if (this.fireControlEngaged() && this.target() == null) {
            this.setFireControl(false);
        }
        if (this.fireControlEngaged() && this.tickCount % 20 == 0 && this.laidOnMission(1.0F)
                && this.passengerIn(GUNNER_SEAT) instanceof ServerPlayer gunner) {
            this.level().playSound(null, gunner.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.25F, 1.8F);
        }
    }

    // ---- The fire-control panel's extra rows.

    @Override
    public List<PanelRow> panelRows(@Nullable Entity viewer, float partialTick) {
        List<PanelRow> rows = new ArrayList<>();
        ShellBallistics ballistics = this.ballistics();
        rows.add(new PanelRow(Component.translatable("hud.jeg.howitzer.charge"), Component.translatable(
                this.highArc() ? "hud.jeg.plz05.mode_high" : "hud.jeg.plz05.mode_low", this.charge() + 1,
                Math.round(ballistics.muzzleVelocity() * ballistics.muzzleVelocity() / ballistics.gravity())), 0xFFECE3C4));
        if (!this.isHalted()) {
            rows.add(new PanelRow(Component.translatable("hud.jeg.plz05.gun"), Component.translatable("hud.jeg.plz05.halt"), 0xFFE05A40));
        }
        BlockPos target = this.target();
        if (target == null) {
            rows.add(new PanelRow(Component.translatable("hud.jeg.howitzer.target"), Component.translatable("hud.jeg.howitzer.no_target"), 0xFF8C9490));
            return rows;
        }
        Lay lay = this.lay();
        rows.add(new PanelRow(Component.translatable("hud.jeg.howitzer.target"), Component.translatable("hud.jeg.howitzer.target_value",
                target.getX(), target.getZ(), Math.round(lay.solution().range())), 0xFFECE3C4));
        if (!lay.reachable()) {
            rows.add(new PanelRow(Component.translatable("hud.jeg.plz05.fire_control"), Component.translatable("hud.jeg.plz05.out_of_reach"), 0xFFE05A40));
            return rows;
        }
        rows.add(new PanelRow(Component.translatable("hud.jeg.plz05.solution"), Component.literal(String.format(Locale.ROOT,
                "%.1f°, %.1f°, %.1f s", Math.floorMod(Math.round((lay.worldYaw() + 180.0F) * 10.0F), 3600) / 10.0F,
                lay.elevation(), lay.ticks() / 20.0F)), 0xFFECE3C4));
        Component state;
        int colour;
        if (!this.fireControlEngaged()) {
            state = Component.translatable("hud.jeg.plz05.fire_control_off");
            colour = 0xFF8C9490;
        } else if (this.laidOnMission(partialTick)) {
            state = Component.translatable("hud.jeg.plz05.laid");
            colour = 0xFF6BD36B;
        } else {
            state = Component.translatable("hud.jeg.plz05.laying");
            colour = 0xFFE0B040;
        }
        rows.add(new PanelRow(Component.translatable("hud.jeg.plz05.fire_control"), state, colour));
        return rows;
    }

    // ---- Saving.

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Plz05Mode", this.mode());
        output.putBoolean("Plz05FireControl", this.fireControlEngaged());
        BlockPos target = this.target();
        if (target != null) {
            output.putLong("Plz05Target", target.asLong());
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setMode(input.getIntOr("Plz05Mode", 4));
        long target = input.getLongOr("Plz05Target", Long.MIN_VALUE);
        this.setTarget(target == Long.MIN_VALUE ? null : BlockPos.of(target));
        this.setFireControl(input.getBooleanOr("Plz05FireControl", false) && this.target() != null);
    }
}
