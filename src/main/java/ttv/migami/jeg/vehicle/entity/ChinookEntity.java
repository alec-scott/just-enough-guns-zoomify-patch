package ttv.migami.jeg.vehicle.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.cabin.CabinLayout;
import ttv.migami.jeg.vehicle.cabin.CabinMath;
import ttv.migami.jeg.vehicle.cabin.CabinNetwork;
import ttv.migami.jeg.vehicle.cabin.CabinVehicle;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The CH-47F Chinook, a tandem-rotor heavy-lift helicopter (design/chinook/PLAN.md and BRIEF.md). It flies in the
 * helicopters' rotor family from its data; this class adds what a tandem transport has that the AH-6 and Mi-28 don't:
 * <ul>
 *     <li>two rotors, both checked against the ground; a longer cold start (APU, then each T55);</li>
 *     <li>a walk-in cabin ({@link CabinVehicle}): people walk about in it while it flies, and place blocks in it.</li>
 * </ul>
 * Geometry comes from design/chinook/generate.py (rig.json): vehicle data coordinates, +Z forward, +X the left.
 */
public final class ChinookEntity extends VehicleEntity implements CabinVehicle {
    public static final String ID = "ch47";
    /** The two rotor discs (rig.json "rotors"): hub positions and the 9.15 m radius, the aft hub higher. */
    private static final List<RotorContactInfo> ROTORS = List.of(
            new RotorContactInfo(0.0D, 85.0D / 16.0D, 95.5D / 16.0D, 9.15D),
            new RotorContactInfo(0.0D, 106.0D / 16.0D, -95.5D / 16.0D, 9.15D));

    // ---- The cabin (rig.json "cabin"): 3 m wide inside, 3 m high (grown from the real 2.3 x 2 m for walking about in,
    // the user, 2026-11-21; generate.py grow), from the ramp's hinge to the cockpit bulkhead.
    public static final double FLOOR_Y = 1.0D, ROOF_Y = 4.0D, HALF_IN = 1.5D, HALF_OUT = 1.6875D;
    public static final double HINGE_Z = -4.125D, BULKHEAD_Z = 5.0625D, REAR_SIDES_Z = -5.375D;
    public static final double RAMP_LENGTH = 2.0D;
    public static final float RAMP_CLOSED = 62.0F;
    /** The crew door's opening in the right wall (-X): data z, top. */
    public static final double DOOR_Z0 = 4.125D, DOOR_Z1 = 4.9375D, DOOR_TOP = 2.875D;
    /** How far the crew door's lower half, folded out at the sill, reaches out from the skin as a step (its 0.7 m). */
    public static final double DOOR_STEP_OUT = 0.75D;
    /** Three cells across, three up, nine along, from the ramp's hinge forward. */
    public static final CabinLayout LAYOUT = new CabinLayout(new AABB(-HALF_IN, FLOOR_Y, HINGE_Z, HALF_IN, ROOF_Y, BULKHEAD_Z),
            new Vec3(-1.5D, FLOOR_Y, HINGE_Z), 3, 3, 9);

    private final Map<BlockPos, BlockState> grid = new HashMap<>();
    private final Map<BlockPos, SimpleContainer> chests = new HashMap<>();
    private final Map<BlockPos, ttv.migami.jeg.vehicle.support.VehicleFurnace> furnaces = new HashMap<>();

    // ---- The ramp and the crew door (P4), synced: the ramp's target (0 closed, 1 level, 2 down) and angle, the door's
    // open fraction and target. Driven on the server, eased between ticks on clients.
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> RAMP_TARGET =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ChinookEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> RAMP_ANGLE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ChinookEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DOOR_TARGET =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ChinookEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private static final net.minecraft.network.syncher.EntityDataAccessor<Float> DOOR_OPEN =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ChinookEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    // ---- The sling (P7, ChinookSling): state, the load's id, the rope's length, its end against the hook and its speed.
    static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SLING_STATE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ChinookEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SLING_LOAD =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ChinookEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    static final net.minecraft.network.syncher.EntityDataAccessor<Float> SLING_LENGTH =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ChinookEntity.class, net.minecraft.network.syncher.EntityDataSerializers.FLOAT);
    static final net.minecraft.network.syncher.EntityDataAccessor<org.joml.Vector3fc> SLING_OFFSET =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ChinookEntity.class, net.minecraft.network.syncher.EntityDataSerializers.VECTOR3);
    static final net.minecraft.network.syncher.EntityDataAccessor<org.joml.Vector3fc> SLING_VELOCITY =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ChinookEntity.class, net.minecraft.network.syncher.EntityDataSerializers.VECTOR3);
    /** The vehicle the hanging hook would take now (ChinookSling#candidate), or -1: the pilot's HUD says it is in reach. */
    static final net.minecraft.network.syncher.EntityDataAccessor<Integer> SLING_CANDIDATE =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ChinookEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    /** Someone stands in the cabin (CabinNetwork, server): synced so clients agree the autopilot is holding the hover. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> CABIN_CREW =
            net.minecraft.network.syncher.SynchedEntityData.defineId(ChinookEntity.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);
    private final ChinookSling sling = new ChinookSling(this);
    private int crewGraceTicks;

    /** Level, and down to the ground: the ramp's lip on the ground with the Chinook standing (1 m floor, 2 m ramp). */
    public static final float RAMP_LEVEL = 0.0F, RAMP_DOWN = -30.0F;
    /** The ramp's hydraulics: about five seconds from closed to down at full speed, easing in and out. */
    private static final float RAMP_RATE = 1.0F;
    /** The crew door: about 1.4 s, the upper half sliding, then the lower half folding down into the steps. */
    private static final float DOOR_RATE = 0.035F;
    /** The left gun window: a little under a second to slide open. */
    private static final float WINDOW_RATE = 0.06F;
    /** Cabin controls (CabinVehicle#cabinControlAt): the ramp panel at the aft right, the crew door. */
    public static final int CONTROL_RAMP = 0, CONTROL_DOOR = 1;
    private float rampAngleO = RAMP_CLOSED;
    private float rampSpeed;
    /** Ticks the ramp has been travelling (0 while it stands): the jump lights' flashes and the beeper keep time to it. */
    private int rampMoveTicks;
    /** The jump lights' and beeper's beat while the ramp travels: on for 8 ticks of every 16. */
    public static final int RAMP_WARN_PERIOD = 16;
    private float doorOpenO;
    private float windowGoalO;

    // ---- The door guns (P5): an M240H in the crew door (right) and one in the left gunner's window, each aimed by
    // its gunner's view (VehicleEntity's pintle guns, as the PLZ-05's roof gun). rig.json "door_guns".
    public static final int LEFT_GUNNER_SEAT = 2, RIGHT_GUNNER_SEAT = 3;
    public static final Vec3 LEFT_GUN_TRUNNION = new Vec3(1.4062D, 2.2812D, 4.5938D);
    public static final Vec3 RIGHT_GUN_TRUNNION = new Vec3(-1.4062D, 2.2812D, 4.5312D);
    /** Trunnion to muzzle. */
    private static final double GUN_REACH = 14.6D / 16.0D;
    /** The left gunner's window in the left wall (+X): data z and y. It slides open while someone is at the gun. */
    public static final double WINDOW_Z0 = 4.25D, WINDOW_Z1 = 4.9375D, WINDOW_Y0 = 1.8125D, WINDOW_Y1 = 2.625D;
    private float gunWindow, gunWindowO;
    private boolean rightGunnerSeated;
    /** Each door gun's own ammunition can (the user, 2026-11-21: separate from the other gun's and from the hold). */
    private final SimpleContainer leftAmmo = new SimpleContainer(9), rightAmmo = new SimpleContainer(9);

    public ChinookEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.setVehicleData(Reference.id(ID));
    }

    /** A door gun feeds from its own can; anything else (there is nothing else) from the hold. */
    @Override
    protected net.minecraft.world.Container ammoContainer(ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo weapon) {
        return weapon.seatIndex() == LEFT_GUNNER_SEAT ? this.leftAmmo : weapon.seatIndex() == RIGHT_GUNNER_SEAT ? this.rightAmmo
                : super.ammoContainer(weapon);
    }

    /** A door gun's ammunition can, by its gunner's seat (2 left, 3 right). */
    public SimpleContainer doorGunAmmo(int seat) {
        return seat == LEFT_GUNNER_SEAT ? this.leftAmmo : this.rightAmmo;
    }

    /** The door guns are M240s: heard shot by shot as a 7.62 mm machine gun, not as a helicopter's minigun loop. */
    @Override
    protected ttv.migami.jeg.vehicle.util.VehicleGunReports.Report ownGunReport(ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo weapon) {
        return weapon.seatIndex() == LEFT_GUNNER_SEAT || weapon.seatIndex() == RIGHT_GUNNER_SEAT
                ? ttv.migami.jeg.vehicle.util.VehicleGunReports.DOOR_GUN : null;
    }

    /** A door gunner's hold key opens their gun's ammunition can; from any other seat, the hold. */
    @Override
    public void openHoldFor(ServerPlayer player) {
        int seat = this.seatOf(player);
        SimpleContainer can = seat == LEFT_GUNNER_SEAT ? this.leftAmmo : seat == RIGHT_GUNNER_SEAT ? this.rightAmmo : null;
        if (can == null) {
            super.openHoldFor(player);
            return;
        }
        net.minecraft.network.chat.Component title = net.minecraft.network.chat.Component.translatable(
                seat == LEFT_GUNNER_SEAT ? "container.jeg.ch47.ammo_left" : "container.jeg.ch47.ammo_right");
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inventory, who) ->
                new net.minecraft.world.inventory.ChestMenu(net.minecraft.world.inventory.MenuType.GENERIC_9x1, id, inventory, can, 1) {
                    @Override
                    public boolean stillValid(net.minecraft.world.entity.player.Player user) {
                        return user.getVehicle() == ChinookEntity.this && ChinookEntity.this.seatOf(user) == seat && !ChinookEntity.this.isRemoved();
                    }
                }, title));
    }

    /**
     * The pilot looks 12 degrees down from the airframe's line, the eye 0.7 m from the panel at 2.5 m (raised 0.2 m to see
     * over the glare shield, the user, 2026-11-25; the seat's fixed camera,
     * ch47.json), so the instrument panel's five displays sit across the lower part of the view with the windscreen and
     * the horizon above (the user, 2026-11-21: "fix the first person POV to show the console and navigation").
     */
    @Override
    public float cockpitViewDown() {
        return 12.0F;
    }

    /** Each T55 lighting in turn, then the rotors engaging: twenty seconds cold, the length of its startup sound. */
    @Override
    public int helicopterStartupTicks() {
        return 400;
    }

    @Override
    public int helicopterShutdownTicks() {
        return 400;
    }

    @Override
    protected List<RotorContactInfo> rotorContacts() {
        return ROTORS;
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(RAMP_TARGET, 0);
        builder.define(RAMP_ANGLE, RAMP_CLOSED);
        builder.define(DOOR_TARGET, false);
        builder.define(DOOR_OPEN, 0.0F);
        builder.define(SLING_STATE, ChinookSling.STOWED);
        builder.define(SLING_LOAD, -1);
        builder.define(SLING_LENGTH, 0.0F);
        builder.define(SLING_OFFSET, new org.joml.Vector3f());
        builder.define(SLING_VELOCITY, new org.joml.Vector3f());
        builder.define(SLING_CANDIDATE, -1);
        builder.define(CABIN_CREW, false);
    }

    /**
     * The pilot who gets up to walk the cabin (the user, 2026-11-21: "it should still hover even if the pilot gets out of
     * his seat") leaves it in the autopilot's hover, as a pilot who moves to a gun does.
     */
    @Override
    protected boolean hasCrewOffSeats() {
        return this.entityData.get(CABIN_CREW);
    }

    /**
     * The hull rolls and pitches about the middle of its cabin, at the pilot's eye height, as the Mi-28 does about its
     * own (its render_pivot_y): about the ground, a strafe's first sway carried the cockpit and the rotors the wrong way
     * before the bank took it across (the user, 2026-11-21: "when swaying to the left it goes right a little bit before
     * going left"). Every pose (drawing, the cabin, seats, lights) turns about this height.
     */
    @Override
    public double rotateOffsetHeight() {
        return 2.2D;
    }

    /** Half the Mi-28's kick the other way as a strafe starts: a 15 t tandem barely swings against it. */
    @Override
    protected double strafeSwayScale() {
        return 0.5D;
    }

    /** Airborne and running, the pilot getting up leaves the engines on (they stand in the cabin a moment later). */
    @Override
    protected boolean holdsHoverWhenPilotLeaves() {
        return !this.onGround() && this.helicopterStartupProgress() >= 1.0F;
    }

    /**
     * Left hovering with nobody aboard (the last of its crew jumped or logged off, or the world was loaded with it in the
     * air), it lets itself down and winds down on the ground: a 15 t aircraft and what its cabin holds aren't thrown away
     * because nobody stayed with it (2026-11-25).
     */
    @Override
    protected boolean autolandsUnattended() {
        return true;
    }

    /**
     * Started, or set down, it keeps its rotors turning on the ground until the pilot holds descend there: people walk on
     * and off over the ramp and it lifts again at once. It cut its engines when the start finished and at every
     * touchdown, so a pilot who let go of the collective sat through a twenty-second start again (two-player play-test,
     * 2026-11-25).
     */
    @Override
    protected boolean idlesOnGround() {
        return true;
    }

    /** The rotors' turn a tick at their 225 rpm: 3.75 turns a second. */
    public static final float ROTOR_TURN = (float) (225.0D / 60.0D / 20.0D * Math.PI * 2.0D);
    /** Rotor power at the hover: the rotors are up to speed from here (VehicleEntity's HELICOPTER_HOVER_POWER). */
    private static final float HOVER_ROTOR = 0.085F;

    /**
     * Governed at 225 rpm, as a real CH-47's rotors are: the collective changes the blades' pitch, not their speed. It
     * spun with the engines' power, 146 degrees a tick at the hover and 180 in a climb, which with three blades 120
     * degrees apart showed as blades standing still or flickering: "when ascending the blades seem to rotate slower,
     * giving the impression it's not doing anything" (the user, 2026-11-25). At 67.5 degrees a tick they are seen to turn.
     * Spooling up and coasting down still follow the rotor.
     */
    @Override
    protected float rotorTurnPerTick(float rotorSpeed) {
        float spooled = this.helicopterStartupProgress() >= 1.0F && !this.isWrecked() ? 1.0F : Math.min(1.0F, rotorSpeed / HOVER_ROTOR);
        return ROTOR_TURN * spooled;
    }

    /**
     * How hard the rotors are working against the hover, -1 to 1: more collective in a climb (and under a load), less
     * coming down. Drawn as the blades' coning and heard in the engines (all clients, from the synced rotor power).
     */
    public float rotorLoad() {
        if (this.helicopterStartupProgress() < 1.0F) {
            return 0.0F;
        }
        float power = this.propellerSpeed();
        return power >= HOVER_ROTOR ? Math.min(1.0F, (power - HOVER_ROTOR) / 0.02F) : Math.max(-1.0F, (power - HOVER_ROTOR) / 0.025F);
    }

    /** A heavy tandem climbs at 6 m/s and comes down at 6.6, not the light helicopters' 10 and 11. */
    @Override
    protected double helicopterVerticalRateScale() {
        return 0.6D;
    }

    /** People standing in the cabin, on the server by their reports, on a client the local player. */
    @Override
    public boolean carriesAboard(net.minecraft.world.entity.Entity entity) {
        if (entity instanceof ServerPlayer player) {
            CabinNetwork.Aboard aboard = CabinNetwork.aboard(player);
            return aboard != null && aboard.vehicleId() == this.getId();
        }
        return this.level().isClientSide() && entity instanceof net.minecraft.world.entity.player.Player player && player.isLocalPlayer()
                && CabinNetwork.clientAboard.getAsInt() == this.getId();
    }

    /** The ramp's target: 0 closed, 1 level, 2 down. */
    public int rampTarget() {
        return this.entityData.get(RAMP_TARGET);
    }

    /** The crew door is shut or shutting (false), open or opening (true). */
    public boolean crewDoorTarget() {
        return this.entityData.get(DOOR_TARGET);
    }

    public ChinookSling sling() {
        return this.sling;
    }

    /** The vehicle the hanging hook would take now, or -1 (synced for the pilot's HUD). */
    public int slingCandidate() {
        return this.entityData.get(SLING_CANDIDATE);
    }

    /** The slung load's pull on the airframe. */
    @Override
    protected Vec3 externalAcceleration() {
        return this.sling.pull();
    }

    /** Its load on the sling (and one just let go, till they're apart): never collided with (ChinookSling#keepsClearOf). */
    @Override
    public boolean keepsClearOf(net.minecraft.world.entity.Entity other) {
        return this.sling.keepsClearOf(other);
    }

    /** Coming down onto its own load, it stops over it (ChinookSling#cushion). */
    @Override
    protected Vec3 cushionHelicopterDescent(Vec3 velocity) {
        return this.sling.cushion(velocity);
    }

    @Override
    protected double flightWeightScale() {
        return this.sling.weightScale();
    }

    /** The pilot's tow key (N) works the sling. */
    @Override
    public boolean hasTowKeyAction() {
        return true;
    }

    @Override
    public void towKey(ServerPlayer player) {
        if (this.getControllingPassenger() == player) {
            this.sling.key(player);
        }
    }

    /** A Chinook doesn't go under another's hook. */
    @Override
    public boolean canBeSlung() {
        return false;
    }

    @Override
    public void tick() {
        this.rampAngleO = this.rampAngle(1.0F);
        this.rampMoveTicks = this.rampMoving() ? this.rampMoveTicks + 1 : 0;
        this.doorOpenO = this.doorOpen(1.0F);
        this.gunWindowO = this.gunWindow;
        float windowGoal = this.passengerIn(LEFT_GUNNER_SEAT) != null ? 1.0F : 0.0F;
        this.gunWindow = this.gunWindow < windowGoal ? Math.min(windowGoal, this.gunWindow + WINDOW_RATE)
                : Math.max(windowGoal, this.gunWindow - WINDOW_RATE);
        if (windowGoal != this.windowGoalO && this.level().isClientSide() && this.tickCount > 1) {
            // Each client hears the window it sees slide (the gunner taking or leaving the gun moves it on every side).
            Vec3 at = CabinMath.toWorld(this.localToWorld(1.0F), new Vec3(HALF_OUT, (WINDOW_Y0 + WINDOW_Y1) / 2.0D, (WINDOW_Z0 + WINDOW_Z1) / 2.0D));
            this.level().playLocalSound(at.x, at.y, at.z, cabinSound("gun_window"), net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F,
                    windowGoal > 0.5F ? 1.0F : 0.94F, false);
        }
        this.windowGoalO = windowGoal;
        if (!this.level().isClientSide()) {
            // A player getting up from a seat stands in the cabin a few ticks later (their client reports it): bridge
            // that gap, or the engines would be cut in it.
            // Seated players count too, so clients already hold the flag when the pilot gets up (one sync later would
            // reset their copy of the engine state).
            boolean seatedPlayer = this.getPassengers().stream().anyMatch(p -> p instanceof net.minecraft.world.entity.player.Player);
            this.crewGraceTicks = seatedPlayer ? 40 : Math.max(0, this.crewGraceTicks - 1);
            this.entityData.set(CABIN_CREW, seatedPlayer || this.crewGraceTicks > 0 || CabinNetwork.anyoneAboard(this.getId()));
            if (this.getControllingPassenger() == null && this.entityData.get(CABIN_CREW) && this.tickCount % 10 == 0) {
                this.resendHelicopterStartup();
            }
        }
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel) {
            ttv.migami.jeg.vehicle.cabin.CabinBlocks.tick(this, serverLevel);
            this.sling.tick(serverLevel);
            // The right gunner slides the crew door open as they take the gun (they can't reach its handle from the seat).
            boolean seated = this.passengerIn(RIGHT_GUNNER_SEAT) != null;
            if (seated && !this.rightGunnerSeated && !this.entityData.get(DOOR_TARGET)) {
                this.toggleCrewDoor(serverLevel);
            }
            this.rightGunnerSeated = seated;
        } else {
            this.sling.clientTick();
        }
        if (this.level() instanceof ServerLevel server) {
            CabinNetwork.SERVER_CABINS.add(this);
            if (this.tickCount % 100 == 7) {
                this.cabinGridChanged();
            }
            // The pilot works the ramp with the weapon-switch key (X): the Chinook's pilot has no weapons. Presses are
            // counted as they arrive (VehicleEntity#takeDriverSwitchPresses); read from this tick's input, some were lost.
            int presses = this.takeDriverSwitchPresses();
            if (presses > 0 && this.getControllingPassenger() != null) {
                this.cycleRamp(server);
            }
            float angle = this.entityData.get(RAMP_ANGLE);
            float goal = this.rampGoal();
            if (angle != goal) {
                // The hydraulics take half a second to full speed and slow over the last few degrees onto the stop.
                this.rampSpeed = Math.min(RAMP_RATE, this.rampSpeed + RAMP_RATE / 10.0F);
                float speed = Math.min(this.rampSpeed, Math.max(0.2F, Math.abs(goal - angle) * 0.15F));
                float next = angle < goal ? Math.min(goal, angle + speed) : Math.max(goal, angle - speed);
                this.entityData.set(RAMP_ANGLE, next);
                if (next == goal) {
                    this.rampSpeed = 0.0F;
                    this.playCabinSound(server, cabinSound("ramp_stop"), 1.0F, 1.0F);
                }
            }
            float door = this.entityData.get(DOOR_OPEN);
            float doorGoal = this.entityData.get(DOOR_TARGET) ? 1.0F : 0.0F;
            if (door != doorGoal) {
                float next = door < doorGoal ? Math.min(doorGoal, door + DOOR_RATE) : Math.max(doorGoal, door - DOOR_RATE);
                this.entityData.set(DOOR_OPEN, next);
                // The step lands as the door finishes opening; shutting, the upper half starts its slide once the step
                // is up (Ch47Renderer's timing), and its sound with it.
                if (next >= 1.0F) {
                    this.playDoorSound(server, "door_step", 0.9F);
                } else if (door > 0.6F && next <= 0.6F && doorGoal == 0.0F) {
                    this.playDoorSound(server, "door_close", 1.0F);
                }
            }
        }
    }

    /** Closed, then level, then down to the ground, then closed again. The hydraulics are heard while it moves
     * (Ch47CabinAudio), and its lock as it reaches each stop. */
    public void cycleRamp(ServerLevel server) {
        int next = (this.entityData.get(RAMP_TARGET) + 1) % 3;
        this.entityData.set(RAMP_TARGET, next);
    }

    public void toggleCrewDoor(ServerLevel server) {
        boolean open = !this.entityData.get(DOOR_TARGET);
        this.entityData.set(DOOR_TARGET, open);
        if (open) {
            this.playDoorSound(server, "door_open", 1.0F);
        }
    }

    public static net.minecraft.sounds.SoundEvent cabinSound(String name) {
        return ttv.migami.jeg.init.ModSounds.ALL.get(Reference.id("vehicle.ch47." + name)).get();
    }

    /** At the ramp's hinge. */
    private void playCabinSound(ServerLevel server, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        Vec3 at = CabinMath.toWorld(this.localToWorld(1.0F), new Vec3(0.0D, 1.5D, HINGE_Z));
        server.playSound(null, at.x, at.y, at.z, sound, net.minecraft.sounds.SoundSource.NEUTRAL, volume, pitch);
    }

    /** At the crew door. */
    private void playDoorSound(ServerLevel server, String name, float volume) {
        Vec3 at = CabinMath.toWorld(this.localToWorld(1.0F), new Vec3(-HALF_OUT, 1.8D, (DOOR_Z0 + DOOR_Z1) / 2.0D));
        server.playSound(null, at.x, at.y, at.z, cabinSound(name), net.minecraft.sounds.SoundSource.NEUTRAL, volume, 1.0F);
    }

    /** The left gun window's open fraction at this point between ticks (0 shut, 1 slid fully aft). */
    public float gunWindow(float partialTick) {
        return this.gunWindowO + (this.gunWindow - this.gunWindowO) * partialTick;
    }

    @org.jetbrains.annotations.Nullable
    public ttv.migami.jeg.vehicle.data.subdata.SeatInfo seatInfo(int index) {
        for (var seat : this.vehicleData().defaults().seats()) {
            if (seat.index() == index) {
                return seat;
            }
        }
        return null;
    }

    @org.jetbrains.annotations.Nullable
    public net.minecraft.world.entity.Entity passengerIn(int seat) {
        for (net.minecraft.world.entity.Entity passenger : this.getPassengers()) {
            if (this.seatOf(passenger) == seat) {
                return passenger;
            }
        }
        return null;
    }

    /** A door gun's rounds leave its muzzle: the trunnion, where the hull holds it, then the barrel's length along the shot. */
    @Override
    @org.jetbrains.annotations.Nullable
    protected Vec3 seatGunMuzzle(ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo weapon, Vec3 direction) {
        Vec3 trunnion = weapon.seatIndex() == LEFT_GUNNER_SEAT ? LEFT_GUN_TRUNNION : weapon.seatIndex() == RIGHT_GUNNER_SEAT ? RIGHT_GUN_TRUNNION : null;
        if (trunnion == null) {
            return null;
        }
        return CabinMath.toWorld(this.localToWorld(1.0F), trunnion).add(direction.normalize().scale(GUN_REACH));
    }

    /** The right gun fires out of the open crew door, the left out of its open window. */
    @Override
    protected boolean canFireWeapon(ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo weapon) {
        if (weapon.seatIndex() == RIGHT_GUNNER_SEAT) {
            return this.crewDoorOpen() && super.canFireWeapon(weapon);
        }
        if (weapon.seatIndex() == LEFT_GUNNER_SEAT) {
            return this.gunWindow > 0.8F && super.canFireWeapon(weapon);
        }
        return super.canFireWeapon(weapon);
    }

    /** The crew door's open fraction at this point between ticks (0 shut, 1 slid fully aft). */
    public float doorOpen(float partialTick) {
        float now = this.entityData.get(DOOR_OPEN);
        return partialTick >= 1.0F ? now : this.doorOpenO + (now - this.doorOpenO) * partialTick;
    }

    @Override
    public int cabinControlAt(Vec3 local) {
        // The ramp panel on the aft right wall, by the hinge (the model's ramp control panel).
        if (local.x < -1.3D && local.y > 1.6D && local.y < 2.4D && local.z > HINGE_Z && local.z < HINGE_Z + 0.5D) {
            return CONTROL_RAMP;
        }
        if (local.x < -HALF_IN + 0.05D && local.z > DOOR_Z0 && local.z < DOOR_Z1 && local.y < DOOR_TOP) {
            return CONTROL_DOOR;
        }
        return -1;
    }

    /** The open crew door has no structure in its opening: a click through the opening works the door. */
    @Override
    public double[] cabinControlOnRay(Vec3 from, Vec3 to) {
        if (!this.crewDoorOpen() || from.x <= -HALF_IN || to.x >= -HALF_IN) {
            return null;
        }
        double t = (from.x + HALF_IN) / (from.x - to.x);
        Vec3 at = from.lerp(to, t);
        return at.z > DOOR_Z0 && at.z < DOOR_Z1 && at.y > FLOOR_Y && at.y < DOOR_TOP ? new double[]{CONTROL_DOOR, t} : null;
    }

    @Override
    public void cabinControl(ServerPlayer player, int control) {
        ServerLevel server = (ServerLevel) this.level();
        if (control == CONTROL_RAMP) {
            this.cycleRamp(server);
        } else if (control == CONTROL_DOOR) {
            this.toggleCrewDoor(server);
        } else if (control >= SEAT_CONTROL && control < SEAT_CONTROL + 4) {
            int seat = control - SEAT_CONTROL;
            CabinNetwork.Aboard aboard = CabinNetwork.aboard(player);
            var info = this.seatInfo(seat);
            Vec3 reachFrom = seat == LEFT_GUNNER_SEAT ? LEFT_GUN_TRUNNION : seat == RIGHT_GUNNER_SEAT ? RIGHT_GUN_TRUNNION : null;
            double allowed = reachFrom != null ? GUN_TAKE_FEET + 0.4D : SEAT_REACH + 1.0D;
            double fromX = reachFrom != null ? reachFrom.x : info == null ? 0.0D : info.x();
            double fromZ = reachFrom != null ? reachFrom.z : info == null ? 0.0D : info.z();
            if (aboard == null || info == null || this.passengerIn(seat) != null || this.seatBlocked(seat)
                    || Math.hypot(aboard.local().x - fromX, aboard.local().z - fromZ) > allowed) {
                return;
            }
            // The seat they pointed at, not the first free one (which put anyone clicking in the cabin in the pilot's).
            this.rememberSeatAssignment(player, seat);
            player.startRiding(this);
        }
    }

    /** How far a cockpit seat's cushion may be from the eye for a click from the cabin to take it. */
    private static final double SEAT_REACH = 3.2D;
    /**
     * A door gun is taken standing at it: the gun or its seat within this of the eye, the feet within
     * {@link #GUN_TAKE_FEET} of its pintle (the user, 2026-11-25: "the trigger to use the side guns should happen
     * closer to each of the guns"; it worked from 3 m across the cabin).
     */
    private static final double GUN_TAKE_REACH = 2.1D, GUN_TAKE_FEET = 1.9D;

    /** Where a seat is clicked from inside: the cushion, about knee height over its floor. */
    private Vec3 seatPoint(ttv.migami.jeg.vehicle.data.subdata.SeatInfo seat) {
        return new Vec3(seat.x(), FLOOR_Y + 0.45D, seat.z());
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public double[] cabinSeatOnRay(Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double length = dir.length();
        if (length < 1.0E-6D) {
            return null;
        }
        dir = dir.scale(1.0D / length);
        double[] best = null;
        for (int seat = 0; seat < 4; seat++) {
            var info = this.seatInfo(seat);
            if (info == null || this.passengerIn(seat) != null) {
                continue;
            }
            Vec3 trunnion = seat == LEFT_GUNNER_SEAT ? LEFT_GUN_TRUNNION : seat == RIGHT_GUNNER_SEAT ? RIGHT_GUN_TRUNNION : null;
            double reach = trunnion != null ? GUN_TAKE_REACH : SEAT_REACH;
            // The cushion (0.55 m round it), and for a door gun the gun itself where it stands stowed: its receiver at the
            // pintle and the barrel above it, up and aft.
            java.util.List<double[]> targets = new java.util.ArrayList<>();
            Vec3 cushion = this.seatPoint(info);
            targets.add(new double[]{cushion.x, cushion.y, cushion.z, 0.55D});
            if (trunnion != null) {
                targets.add(new double[]{trunnion.x, trunnion.y, trunnion.z, 0.32D});
                targets.add(new double[]{trunnion.x, trunnion.y + 0.45D, trunnion.z - 0.15D, 0.3D});
            }
            for (double[] target : targets) {
                Vec3 toCentre = new Vec3(target[0], target[1], target[2]).subtract(from);
                double along = toCentre.dot(dir);
                if (along < 0.0D || along > reach) {
                    continue;
                }
                double miss = toCentre.subtract(dir.scale(along)).length();
                if (miss < target[3] && (best == null || along < best[1] * length)) {
                    best = new double[]{SEAT_CONTROL + seat, along / length};
                }
            }
        }
        return best;
    }

    @Override
    public net.minecraft.network.chat.Component cabinControlPrompt(int control) {
        if (control == CONTROL_RAMP) {
            int next = (this.rampTarget() + 1) % 3;
            return net.minecraft.network.chat.Component.translatable("hud.jeg.ch47.prompt.ramp",
                    net.minecraft.network.chat.Component.translatable("hud.jeg.ch47.ramp_" + next));
        }
        if (control == CONTROL_DOOR) {
            return net.minecraft.network.chat.Component.translatable(this.crewDoorTarget() ? "hud.jeg.ch47.prompt.door_close" : "hud.jeg.ch47.prompt.door_open");
        }
        if (control >= SEAT_CONTROL && control < SEAT_CONTROL + 4) {
            return net.minecraft.network.chat.Component.translatable("hud.jeg.ch47.prompt.seat",
                    net.minecraft.network.chat.Component.translatable("hud.jeg.ch47.seat_" + (control - SEAT_CONTROL)));
        }
        return null;
    }

    /** From outside, a click by the crew door works the door; anywhere else boards as on any vehicle. */
    @Override
    public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, Vec3 hitLocation) {
        Vec3 local = CabinMath.toLocal(this.localToWorld(1.0F), this.position().add(hitLocation));
        if (player.getVehicle() == null && atCrewDoorFromOutside(local)) {
            if (player instanceof ServerPlayer serverPlayer && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
                this.toggleCrewDoor((ServerLevel) serverPlayer.level());
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        return super.interact(player, hand, hitLocation);
    }

    /** A click from outside at this point of the hull (data coordinates) works the crew door. */
    public static boolean atCrewDoorFromOutside(Vec3 local) {
        return local.x < -HALF_IN && local.z > DOOR_Z0 - 0.3D && local.z < DOOR_Z1 + 0.3D && local.y < DOOR_TOP + 0.3D;
    }

    /**
     * The ramp's hitbox drops out while the ramp is open and the crew door's while the door is, or they would stop rounds
     * and keep people out (generate.py's "ramp" and "crew door" rows).
     */
    @Override
    protected int boundsStateKey() {
        float angle = this.rampAngle(1.0F);
        int key = angle < 45.0F ? 1 : 0;
        if (this.doorOpen(1.0F) > 0.5F) {
            key |= 2;
        }
        if (Math.abs(angle - RAMP_LEVEL) < 3.0F) {
            key |= 4;
        }
        return key;
    }

    @Override
    protected List<ttv.migami.jeg.vehicle.data.subdata.OBBInfo.Box> obbBoxes() {
        List<ttv.migami.jeg.vehicle.data.subdata.OBBInfo.Box> boxes = super.obbBoxes();
        float angle = this.rampAngle(1.0F);
        boolean rampOpen = angle < 45.0F;
        boolean doorOpen = this.doorOpen(1.0F) > 0.5F;
        if (!rampOpen && !doorOpen) {
            return boxes;
        }
        List<ttv.migami.jeg.vehicle.data.subdata.OBBInfo.Box> open = new ArrayList<>(
                boxes.stream().filter(box -> !(rampOpen && isRampBox(box)) && !(doorOpen && isDoorBox(box))).toList());
        if (Math.abs(angle - RAMP_LEVEL) < 3.0F) {
            // The level ramp is a deck at the floor's height: solid from outside, or people on the ground walked through
            // it (play-test 2026-11-20). People aboard stand on its top, as on the floor's box.
            open.add(new ttv.migami.jeg.vehicle.data.subdata.OBBInfo.Box(ttv.migami.jeg.vehicle.data.subdata.OBBInfo.Part.BODY,
                    0.0D, FLOOR_Y - 0.1D, HINGE_Z - RAMP_LENGTH * 0.5D, HALF_IN, 0.1D, RAMP_LENGTH * 0.5D));
        }
        return open;
    }

    private static boolean isRampBox(ttv.migami.jeg.vehicle.data.subdata.OBBInfo.Box box) {
        return box.part() == ttv.migami.jeg.vehicle.data.subdata.OBBInfo.Part.BODY && Math.abs(box.x()) < 0.01D && box.z() < HINGE_Z - 0.2D && box.y() > FLOOR_Y;
    }

    private static boolean isDoorBox(ttv.migami.jeg.vehicle.data.subdata.OBBInfo.Box box) {
        return box.part() == ttv.migami.jeg.vehicle.data.subdata.OBBInfo.Part.BODY && box.x() < -1.0D && box.z() > DOOR_Z0 && box.z() < DOOR_Z1
                && box.y() < DOOR_TOP - 0.5D;
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        CabinNetwork.sendGrid(this, this, player);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.level() instanceof ServerLevel level && this.sling.state() == ChinookSling.CARRYING) {
            // Whatever it carried goes its own way.
            this.sling.release(level);
        }
        if (reason.shouldDestroy() && this.level() instanceof ServerLevel level) {
            // The door guns' ammunition spills where the guns were.
            for (SimpleContainer can : List.of(this.leftAmmo, this.rightAmmo)) {
                Vec3 at = CabinMath.toWorld(this.localToWorld(1.0F), can == this.leftAmmo ? LEFT_GUN_TRUNNION : RIGHT_GUN_TRUNNION);
                for (ItemStack stack : can.removeAllItems()) {
                    level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, stack));
                }
            }
        }
        if (reason.shouldDestroy() && this.level() instanceof ServerLevel level && !this.grid.isEmpty()) {
            // Nothing vanishes: what was placed aboard, and what was in it, falls where the cabin was.
            Matrix4d pose = this.localToWorld(1.0F);
            for (Map.Entry<BlockPos, BlockState> cell : this.grid.entrySet()) {
                Vec3 at = CabinMath.toWorld(pose, LAYOUT.cellBox(cell.getKey()).getCenter());
                level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, new ItemStack(cell.getValue().getBlock())));
                SimpleContainer chest = this.chests.get(cell.getKey());
                if (chest != null) {
                    for (ItemStack stack : chest.removeAllItems()) {
                        level.addFreshEntity(new ItemEntity(level, at.x, at.y, at.z, stack));
                    }
                }
            }
            this.grid.clear();
            this.chests.clear();
            this.furnaces.clear();
        }
        CabinNetwork.SERVER_CABINS.remove(this);
        super.remove(reason);
    }

    // ---- CabinVehicle ----

    @Override
    public int cabinId() {
        return this.getId();
    }

    @Override
    public CabinLayout cabinLayout() {
        return LAYOUT;
    }

    /** Where the ramp is going: its synced target's angle. */
    public float rampGoal() {
        return switch (this.entityData.get(RAMP_TARGET)) {
            case 1 -> RAMP_LEVEL;
            case 2 -> RAMP_DOWN;
            default -> RAMP_CLOSED;
        };
    }

    /** The ramp is travelling to its target (every client sees it from the synced target and angle). */
    public boolean rampMoving() {
        return Math.abs(this.entityData.get(RAMP_ANGLE) - this.rampGoal()) > 1.0E-3F;
    }

    /**
     * The red jump light over the ramp, on or off this tick: it flashes while the ramp travels, from the moment it starts
     * (the user, 2026-11-21: "make the heli lights flash when the ramp goes down with a beep sound"). The beeper sounds
     * at each flash ({@link #rampBeepTick}).
     */
    public boolean jumpLightRed() {
        return this.rampMoveTicks > 0 && (this.rampMoveTicks - 1) % RAMP_WARN_PERIOD < RAMP_WARN_PERIOD / 2;
    }

    /** The green jump light: steady once the ramp stands open (level or down), dark while it moves or is shut. */
    public boolean jumpLightGreen() {
        return this.rampMoveTicks == 0 && this.entityData.get(RAMP_ANGLE) < 45.0F;
    }

    /** The ramp warning's beep starts this tick (with each red flash). */
    public boolean rampBeepTick() {
        return this.rampMoveTicks > 0 && (this.rampMoveTicks - 1) % RAMP_WARN_PERIOD == 0;
    }

    /** The ramp's angle at this point between ticks, degrees up from level: closed {@value #RAMP_CLOSED}, down {@value #RAMP_DOWN}. */
    public float rampAngle(float partialTick) {
        float now = this.entityData.get(RAMP_ANGLE);
        return partialTick >= 1.0F ? now : this.rampAngleO + (now - this.rampAngleO) * partialTick;
    }

    /** Whether the crew door is open far enough to walk through. */
    public boolean crewDoorOpen() {
        return this.doorOpen(1.0F) > 0.8F;
    }

    @Override
    public List<AABB> cabinShell() {
        List<AABB> shell = new ArrayList<>();
        shell.add(new AABB(-HALF_OUT, FLOOR_Y - 0.4D, HINGE_Z, HALF_OUT, FLOOR_Y, BULKHEAD_Z));                 // floor
        shell.add(new AABB(-HALF_OUT, ROOF_Y, REAR_SIDES_Z, HALF_OUT, ROOF_Y + 0.25D, BULKHEAD_Z));             // roof
        shell.add(new AABB(-HALF_OUT, FLOOR_Y, BULKHEAD_Z, HALF_OUT, ROOF_Y, BULKHEAD_Z + 0.1D));               // bulkhead
        if (this.gunWindow < 0.8F) {
            shell.add(new AABB(HALF_IN, FLOOR_Y, REAR_SIDES_Z, HALF_OUT, ROOF_Y, BULKHEAD_Z));                  // left wall
        } else {
            // The left wall round the gun window's opening, while it stands open.
            shell.add(new AABB(HALF_IN, FLOOR_Y, REAR_SIDES_Z, HALF_OUT, ROOF_Y, WINDOW_Z0));
            shell.add(new AABB(HALF_IN, FLOOR_Y, WINDOW_Z0, HALF_OUT, WINDOW_Y0, WINDOW_Z1));
            shell.add(new AABB(HALF_IN, WINDOW_Y1, WINDOW_Z0, HALF_OUT, ROOF_Y, WINDOW_Z1));
            shell.add(new AABB(HALF_IN, FLOOR_Y, WINDOW_Z1, HALF_OUT, ROOF_Y, BULKHEAD_Z));
        }
        // The right wall, round the crew door's opening; the door fills it while shut.
        shell.add(new AABB(-HALF_OUT, FLOOR_Y, REAR_SIDES_Z, -HALF_IN, ROOF_Y, DOOR_Z0));
        shell.add(new AABB(-HALF_OUT, DOOR_TOP, DOOR_Z0, -HALF_IN, ROOF_Y, DOOR_Z1));
        shell.add(new AABB(-HALF_OUT, FLOOR_Y, DOOR_Z1, -HALF_IN, ROOF_Y, BULKHEAD_Z));
        if (!this.crewDoorOpen()) {
            shell.add(new AABB(-HALF_OUT, FLOOR_Y, DOOR_Z0, -HALF_IN, DOOR_TOP, DOOR_Z1));
        } else {
            // The door's lower half folded out at the sill (Ch47Renderer): a step to land on, level with the floor.
            shell.add(new AABB(-HALF_OUT - DOOR_STEP_OUT, FLOOR_Y - 0.1D, DOOR_Z0, -HALF_OUT, FLOOR_Y, DOOR_Z1));
        }
        // The ramp, as a run of thin steps along its length at its present angle (walked up when it is down).
        double angle = Math.toRadians(this.rampAngle(1.0F));
        double cos = Math.cos(angle), sin = Math.sin(angle);
        int steps = 8;
        for (int i = 0; i < steps; i++) {
            double s0 = RAMP_LENGTH * i / steps, s1 = RAMP_LENGTH * (i + 1) / steps;
            double zA = HINGE_Z - s0 * cos, zB = HINGE_Z - s1 * cos;
            double yA = FLOOR_Y + s0 * sin, yB = FLOOR_Y + s1 * sin;
            double top = Math.max(yA, yB);
            shell.add(new AABB(-HALF_IN, Math.min(yA, yB) - 0.2D, Math.min(zA, zB), HALF_IN, top, Math.max(zA, zB) + 0.01D));
        }
        if (this.rampAngle(1.0F) > 45.0F) {
            // The upper ramp door closes the gap over the raised ramp.
            shell.add(new AABB(-HALF_IN, FLOOR_Y + RAMP_LENGTH * sin - 0.2D, HINGE_Z - RAMP_LENGTH * cos - 0.15D, HALF_IN, ROOF_Y,
                    HINGE_Z - RAMP_LENGTH * cos + 0.05D));
        }
        return shell;
    }

    @Override
    public boolean cabinAboard(Vec3 feet) {
        if (feet.x <= -HALF_IN || feet.x >= HALF_IN || feet.y >= ROOF_Y || feet.z >= BULKHEAD_Z) {
            // The crew door, open: in at its opening and over its folded-out step, from a jump off the ground (feet a
            // little up) to the top of the jump. Only 0.3 m over the floor, the jump that boarded carried the feet out
            // of it again, and the player was let go mid-air outside the hull and fell back (the user, 2026-11-21).
            return this.crewDoorOpen() && feet.x > -HALF_OUT - DOOR_STEP_OUT - 0.15D && feet.x <= -HALF_IN
                    && feet.z > DOOR_Z0 && feet.z < DOOR_Z1 && feet.y > FLOOR_Y - 0.7D && feet.y < FLOOR_Y + 1.6D;
        }
        if (feet.z >= HINGE_Z) {
            return feet.y > FLOOR_Y - 0.7D;
        }
        float angle = this.rampAngle(1.0F);
        if (angle > 45.0F) {
            return feet.z > HINGE_Z - 0.2D && feet.y > FLOOR_Y - 0.7D;
        }
        // On the ramp: from its surface at this distance aft of the hinge, a little below it (stepping on from the ground).
        double along = (HINGE_Z - feet.z) / Math.cos(Math.toRadians(angle));
        if (along > RAMP_LENGTH) {
            // Past the lip there is no cabin floor, and cabin movement doesn't collide with the world: feet counted
            // aboard there fell into the ground before letting go (play-test 2026-11-20). The world's ground holds them.
            return false;
        }
        double surface = FLOOR_Y + Math.min(along, RAMP_LENGTH) * Math.sin(Math.toRadians(angle));
        return feet.y > surface - 0.7D;
    }

    @Override
    public Map<BlockPos, BlockState> cabinGrid() {
        return this.grid;
    }

    @Override
    public Map<BlockPos, SimpleContainer> cabinChests() {
        return this.chests;
    }

    @Override
    public Map<BlockPos, ttv.migami.jeg.vehicle.support.VehicleFurnace> cabinFurnaces() {
        return this.furnaces;
    }

    // ---- Seats and the cargo floor: the cabin has no benches (left empty for players' blocks); a door gunner's seat
    // can't be taken while a block stands in its cell, and no block goes in a cell where someone sits.

    /** The cabin cell a seat is in (the door gunners'), or null for the cockpit's. */
    @org.jetbrains.annotations.Nullable
    private static BlockPos seatCell(ttv.migami.jeg.vehicle.data.subdata.SeatInfo seat) {
        if (seat.index() < LEFT_GUNNER_SEAT) {
            return null;
        }
        BlockPos cell = LAYOUT.cellAt(new Vec3(seat.x(), FLOOR_Y + 0.2D, seat.z()));
        return LAYOUT.isCell(cell) ? cell : null;
    }

    @Override
    protected boolean seatBlocked(int seatIndex) {
        if (this.level().isClientSide()) {
            return false;
        }
        var seat = this.seatInfo(seatIndex);
        BlockPos cell = seat == null ? null : seatCell(seat);
        return cell != null && this.grid.containsKey(cell);
    }

    /** Nobody sitting in a cell has a block put on them. */
    @Override
    public boolean cabinCellBlocked(BlockPos cell) {
        for (net.minecraft.world.entity.Entity passenger : this.getPassengers()) {
            var seat = this.seatInfo(this.seatOf(passenger));
            if (seat != null && cell.equals(seatCell(seat))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void cabinGridChanged() {
        if (this.level() instanceof ServerLevel) {
            CabinNetwork.sendGrid(this, this, null);
        }
    }

    // ---- Saving: the blocks aboard and what the chests hold ----

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        this.sling.save(output.child("Sling"));
        // The ramp and the crew door keep where they were: a ramp left down mustn't swing shut under people on reload.
        output.putInt("RampTarget", this.entityData.get(RAMP_TARGET));
        output.putFloat("RampAngle", this.entityData.get(RAMP_ANGLE));
        output.putBoolean("DoorTarget", this.entityData.get(DOOR_TARGET));
        output.putFloat("DoorOpen", this.entityData.get(DOOR_OPEN));
        output.store("AmmoLeft", ItemStack.OPTIONAL_CODEC.listOf(), this.leftAmmo.getItems());
        output.store("AmmoRight", ItemStack.OPTIONAL_CODEC.listOf(), this.rightAmmo.getItems());
        List<BlockPos> cells = new ArrayList<>(this.grid.keySet());
        List<BlockState> states = new ArrayList<>();
        for (BlockPos cell : cells) {
            states.add(this.grid.get(cell));
        }
        output.store("CabinCells", BlockPos.CODEC.listOf(), cells);
        output.store("CabinStates", BlockState.CODEC.listOf(), states);
        for (int i = 0; i < cells.size(); i++) {
            SimpleContainer chest = this.chests.get(cells.get(i));
            if (chest != null) {
                List<ItemStack> items = new ArrayList<>();
                for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                    items.add(chest.getItem(slot));
                }
                output.store("CabinChest" + i, ItemStack.OPTIONAL_CODEC.listOf(), items);
            }
            ttv.migami.jeg.vehicle.support.VehicleFurnace furnace = this.furnaces.get(cells.get(i));
            if (furnace != null) {
                furnace.saveState(output.child("CabinFurnace" + i));
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.child("Sling").ifPresent(this.sling::load);
        this.entityData.set(RAMP_TARGET, Math.floorMod(input.getIntOr("RampTarget", 0), 3));
        this.entityData.set(RAMP_ANGLE, input.getFloatOr("RampAngle", RAMP_CLOSED));
        this.rampAngleO = this.entityData.get(RAMP_ANGLE);
        this.entityData.set(DOOR_TARGET, input.getBooleanOr("DoorTarget", false));
        this.entityData.set(DOOR_OPEN, input.getFloatOr("DoorOpen", 0.0F));
        this.doorOpenO = this.entityData.get(DOOR_OPEN);
        for (SimpleContainer can : List.of(this.leftAmmo, this.rightAmmo)) {
            List<ItemStack> items = input.read(can == this.leftAmmo ? "AmmoLeft" : "AmmoRight", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
            can.clearContent();
            for (int slot = 0; slot < Math.min(items.size(), can.getContainerSize()); slot++) {
                can.setItem(slot, items.get(slot));
            }
        }
        List<BlockPos> cells = input.read("CabinCells", BlockPos.CODEC.listOf()).orElse(List.of());
        List<BlockState> states = input.read("CabinStates", BlockState.CODEC.listOf()).orElse(List.of());
        this.grid.clear();
        this.chests.clear();
        this.furnaces.clear();
        for (int i = 0; i < Math.min(cells.size(), states.size()); i++) {
            this.grid.put(cells.get(i), states.get(i));
            ttv.migami.jeg.vehicle.cabin.CabinBlocks.created(this, cells.get(i), states.get(i));
            SimpleContainer container = this.chests.get(cells.get(i));
            if (container != null) {
                List<ItemStack> items = input.read("CabinChest" + i, ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
                for (int slot = 0; slot < Math.min(items.size(), container.getContainerSize()); slot++) {
                    container.setItem(slot, items.get(slot));
                }
            }
            ttv.migami.jeg.vehicle.support.VehicleFurnace furnace = this.furnaces.get(cells.get(i));
            if (furnace != null) {
                input.child("CabinFurnace" + i).ifPresent(furnace::loadState);
            }
        }
    }
}
