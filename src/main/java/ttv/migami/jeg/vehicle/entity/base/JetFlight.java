package ttv.migami.jeg.vehicle.entity.base;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import ttv.migami.jeg.vehicle.data.subdata.EngineInfo;
import ttv.migami.jeg.vehicle.util.VehicleSoundHelper;

/**
 * Fixed-wing flight for jets (aircraft with a "jet" engine), run by the server and, for the pilot, predicted on their own
 * client with the same inputs. Units are blocks, ticks and degrees.
 *
 * <p>The airframe is a rigid body: the pilot commands body rates (the mouse pitches the nose and turns, A/D roll), the
 * rates build with the airframe's weight, and the orientation turns by them as a quaternion so loops and rolls have no
 * gimbal limits. The wing's lift grows with airspeed squared and angle of attack until the stall, thrust follows the
 * engine spool, drag grows with speed and with the lift being pulled, and gravity always acts: climbing costs speed and
 * diving gains it. A few assists make it easy to fly without making it unrealistic: the mouse sideways banks into a
 * coordinated turn on its own, hands off it holds the flight path and rolls the wings level, and an angle-of-attack
 * limiter stops a hard pull from stalling a fast jet (it still stalls when flown too slowly).
 *
 * <p>On the ground the wheels take over: nose-wheel steering, tyre grip, rolling friction and the wheel brake. Lift
 * builds through the take-off run and the nose can be lifted once at rotate speed.
 */
final class JetFlight {
    /** Gravity on a jet, blocks per tick squared. */
    static final double GRAVITY = 0.035D;
    private static final double ALPHA_STALL = Math.toRadians(15.0D);
    /** The wing is set a little nose-up on the fuselage, so a fast jet cruises with its nose level. */
    private static final double INCIDENCE = Math.toRadians(1.5D);
    /** Most lift the airframe takes, in g. */
    private static final double MAX_LOAD = 6.0D;
    /** Drag from the lift being pulled: hard turns bleed speed. */
    private static final double INDUCED_DRAG = 3.5D;
    /** Thrust falls off this share by top speed. */
    private static final double THRUST_LAPSE = 0.12D;
    private static final double IDLE = 0.08D;
    private static final double IDLE_SPOOL = IDLE;
    private static final double THROTTLE_UP = 0.03D;
    private static final double THROTTLE_DOWN = 0.035D;
    private static final double SPOOL_UP = 0.05D;
    private static final double SPOOL_DOWN = 0.075D;
    /**
     * Cruise control's autothrottle: how hard it moves the lever for each block a tick off the set airspeed, and how much
     * it eases off while the speed is already coming back (so it settles rather than hunting).
     */
    private static final double AUTOTHROTTLE_GAIN = 0.12D;
    private static final double AUTOTHROTTLE_DAMPING = 2.5D;
    /** Degrees per tick of body rate for each unit of mouse movement (10 pixels a tick at default sensitivity). */
    private static final double MOUSE_PITCH = 0.45D;
    private static final double MOUSE_TURN = 0.4D;
    private static final double MAX_PITCH_RATE = 2.6D;
    private static final double MAX_TURN_RATE = 2.0D;
    private static final double MAX_ROLL_RATE = 3.5D;
    /** How quickly the airframe's pitch, roll and yaw rates follow the stick (per tick, divided by weight). */
    private static final double PITCH_RESPONSE = 0.2D;
    private static final double ROLL_RESPONSE = 0.22D;
    private static final double YAW_RESPONSE = 0.15D;
    private static final double MAX_BANK = 55.0D;
    /** Auto-bank takes this share of a coordinated turn's bank, and rolls into it no faster than this. */
    private static final double AUTO_BANK_SHARE = 0.65D;
    private static final double AUTO_BANK_RATE = 1.6D;
    private static final double AUTO_BANK_GAIN = 0.045D;
    /** Share of the mouse's turn the rudder gives straight away, before the bank has built up. */
    private static final double RUDDER_LEAD = 0.15D;
    /**
     * The GAU-8 pushes back hard: its recoil, as a share of the engines' full thrust, is well over what they give flat
     * out, so a burst slows the jet a lot. It eases off as the airspeed comes down towards the stall (from this many
     * times the stall speed, over the next fade), so the gun alone never slows the jet below flying speed.
     */
    private static final double GUN_RECOIL_SHARE = 2.5D;
    private static final double GUN_RECOIL_FLOOR = 1.35D;
    private static final double GUN_RECOIL_FADE = 0.4D;
    /** From cold an engine takes about six seconds to wind up to idle (the start-up sound's length). */
    private static final double START_SPOOL_PER_TICK = IDLE_SPOOL / 120.0D;
    /**
     * Ground: distance between the nose wheel and the main gear, and the most the nose wheel turns at taxi speed (less as
     * it speeds up, a few degrees by take-off). A heavy jet on its wheels: the nose wheel swings over in about a second,
     * the turn takes a moment to build, and grip on the tyres caps how hard it can turn at speed, so the faster it rolls
     * the wider it turns. Crawling, it turns in a circle about 20 blocks across; taxiing briskly (about 18 km/h) it comes
     * full circle in about 20 seconds; at 70 km/h a 90-degree turn takes about 10 seconds; at take-off speed it only
     * corrects its line.
     */
    private static final double WHEELBASE = 5.4D;
    private static final double MAX_NOSE_WHEEL_ANGLE = 30.0D;
    private static final double TAKEOFF_NOSE_WHEEL_ANGLE = 3.0D;
    private static final double NOSE_WHEEL_SLEW = 0.05D;
    private static final double MAX_GROUND_YAW_RATE = 0.9D;
    private static final double GROUND_SIDE_ACCELERATION = 0.008D;
    private static final double GROUND_YAW_RESPONSE = 0.1D;
    /** How much the mouse steers the nose wheel on the ground, against the A and D keys' full lock. */
    private static final double GROUND_MOUSE_STEER = 0.5D;
    private static final float MAX_GROUND_NOSE_UP = 13.0F;
    /** Below this many times its stall speed, and this high above the ground, a jet's nose-up attitude is held in check. */
    private static final double TAKEOFF_PROTECTION_SPEED = 1.4D;
    private static final double TAKEOFF_PROTECTION_HEIGHT = 25.0D;
    private static final double ROLLING_FRICTION = 0.0006D;
    private static final double WHEEL_BRAKE = 0.016D;
    private static final double PARKING_BRAKE = 0.03D;
    /**
     * With the throttle closed on the ground the pilot holds the brakes: idle thrust alone (about four times the tyres'
     * rolling friction) would otherwise creep the jet forward. It slows a rollout without locking the wheels.
     */
    private static final double IDLE_HOLD_BRAKE = 0.008D;
    private static final double TYRE_GRIP = 0.55D;
    private static final double AIRBRAKE_DRAG = 1.4D;
    /**
     * Speed brakes (the A-10's split wingtip ailerons): out with the brake key in the air, or with reverse held in a dive
     * steeper than this many degrees, and kept out while reverse stays held. They take this long a tick to open or close.
     */
    private static final double DIVE_BRAKE_NOSE_DOWN = 10.0D;
    private static final float SPEED_BRAKE_TRAVEL = 0.1F;
    /**
     * With the brakes out the jet sheds this share of its speed over the braked limit each tick: a dive settles at that
     * limit (this share of top speed, never under 1.25 times the stall speed) rather than running away to the dive speed,
     * so it can be held on a target for a strafing run.
     */
    private static final double SPEED_BRAKE_SHED = 0.12D;
    private static final double SPEED_BRAKE_LIMIT_SHARE = 0.55D;
    private static final double GEAR_DRAG = 0.18D;
    /** Gear: comes up past this height above ground and goes back down below the lower one. */
    private static final double GEAR_UP_ALTITUDE = 14.0D;
    private static final double GEAR_DOWN_ALTITUDE = 12.0D;
    private static final float GEAR_TRAVEL_PER_TICK = 1.0F / 40.0F;
    /** Touchdown: a sink rate (blocks per tick) up to this is a normal landing; at the second it breaks the airframe. */
    /**
     * The top speed the thresholds below were set for (245 km/h). A jet's own top speed scales them: a slower jet lands,
     * strikes and stalls at proportionally lower speeds.
     */
    static final double REFERENCE_TOP_SPEED = 3.4D;
    private static final double SAFE_SINK = 0.22D;
    private static final double FATAL_SINK = 0.6D;
    /** Wing, fin, nose and engine points checked against the terrain each tick: the hull box only covers the fuselage. */
    private static final double[][] STRIKE_POINTS = {
            {0.0D, 2.2D, 8.0D, 0},
            {8.6D, 2.5D, 0.3D, 1},
            {-8.6D, 2.5D, 0.3D, 1},
            {4.6D, 2.4D, 0.0D, 1},
            {-4.6D, 2.4D, 0.0D, 1},
            {3.1D, 4.6D, -7.3D, 2},
            {-3.1D, 4.6D, -7.3D, 2},
            {0.0D, 2.6D, -7.6D, 2},
            {1.65D, 4.0D, -3.4D, 0},
            {-1.65D, 4.0D, -3.4D, 0}
    };
    private static final double STRIKE_MIN_SPEED = 0.35D;
    private static final int STRIKE_COOLDOWN_TICKS = 8;

    private final VehicleEntity jet;
    private double throttle;
    private double spool;
    private float gear = 1.0F;
    private boolean gearTargetDown = true;
    /** Speed brakes, 0 closed to 1 fully open. */
    private float speedBrake;
    private double pitchRate;
    private double yawRate;
    /** The nose wheel's steering, -1 (full left) to 1 (full right). */
    private double noseWheel;
    private double rollRate;
    private double gammaHold = Double.NaN;
    private double lastGamma;
    private double lastAirspeed;
    private double alphaDegrees;
    private boolean stalled;
    private boolean stallWarning;
    private int airborneTicks;
    private int strikeCooldown;
    private boolean predicting;

    JetFlight(VehicleEntity jet) {
        this.jet = jet;
    }

    /** The orientation of a hull at this yaw, pitch and roll, as the renderer and the pose transforms build it. */
    static Quaterniond pose(double yaw, double pitch, double roll) {
        return new Quaterniond()
                .rotateY(Math.toRadians(-yaw))
                .rotateX(Math.toRadians(pitch))
                .rotateZ(Math.toRadians(roll));
    }

    /** Yaw, pitch (-90..90) and roll of an orientation, the inverse of {@link #pose}. */
    static double[] euler(Quaterniond rotation) {
        Vector3d forward = rotation.transform(new Vector3d(0.0D, 0.0D, 1.0D));
        double pitch = -Math.toDegrees(Math.asin(Mth.clamp(forward.y, -1.0D, 1.0D)));
        double yaw;
        double roll;
        if (Math.abs(forward.y) > 0.99995D) {
            // Straight up or down: yaw and roll turn the same axis. Keep the roll at zero and read the heading off the
            // canopy's direction instead.
            Vector3d up = rotation.transform(new Vector3d(0.0D, 1.0D, 0.0D));
            yaw = forward.y > 0.0D ? Math.toDegrees(Math.atan2(up.x, -up.z)) : Math.toDegrees(Math.atan2(-up.x, up.z));
            roll = 0.0D;
        } else {
            yaw = Math.toDegrees(Math.atan2(-forward.x, forward.z));
            Quaterniond rollOnly = pose(yaw, pitch, 0.0D).conjugate().mul(rotation);
            Vector3d up = rollOnly.transform(new Vector3d(0.0D, 1.0D, 0.0D));
            roll = Math.toDegrees(Math.atan2(-up.x, up.y));
        }
        return new double[]{Mth.wrapDegrees(yaw), pitch, Mth.wrapDegrees(roll)};
    }

    double throttle() {
        return this.throttle;
    }

    double spool() {
        return this.spool;
    }

    float gear() {
        return this.gear;
    }

    float speedBrake() {
        return this.speedBrake;
    }

    boolean stallWarning() {
        return this.stallWarning;
    }

    double alphaDegrees() {
        return this.alphaDegrees;
    }

    boolean predicting() {
        return this.predicting;
    }

    /** The pilot's client stopped predicting (seat change, dismount): the synced values are the ones to show again. */
    void stopPredicting() {
        this.predicting = false;
    }

    void tick(boolean server) {
        if (!server && !this.predicting) {
            // Pick up where the server is, so taking the controls does not reset the throttle or drop the gear.
            this.throttle = this.jet.syncedJetThrottle();
            this.spool = this.jet.syncedJetSpool();
            this.gear = this.jet.syncedJetGear();
            this.gearTargetDown = this.gear > 0.5F;
            this.speedBrake = this.jet.syncedJetSpeedBrake();
        }
        this.predicting = !server;
        EngineInfo engine = this.jet.vehicleData().defaults().engine();
        VehicleInput input = this.jet.jetInput();
        LivingEntity pilot = this.jet.getControllingPassenger() instanceof LivingEntity living ? living : null;
        boolean wrecking = this.jet.isLowHealthDecayActive() || this.jet.vehicleHealth() <= 0.1F * this.jet.maxVehicleHealth();
        boolean hasEnergy = this.jet.hasEngineEnergy(engine, true);
        boolean controlled = pilot != null && hasEnergy && !wrecking;
        boolean grounded = this.jet.onGround() || this.jet.verticalCollisionBelow;
        double topSpeed = Math.max(0.2D, engine.maxForwardSpeed());
        double stallSpeed = engine.jetStallSpeed();
        double rotateSpeed = engine.jetRotateSpeed();
        double diveSpeed = engine.jetMaxDiveSpeed();
        double weight = Mth.clamp(engine.playerWeight(), 0.5D, 3.0D);
        double scale = topSpeed / REFERENCE_TOP_SPEED;
        // A slower jet flies the same, only smaller: its gravity (like its thrust, in its data) is scaled with its speed,
        // so it climbs, loops and turns as steeply and as quickly, covering less ground. Out of control it falls as
        // anything else does.
        double gravity = controlled ? GRAVITY * scale : GRAVITY;
        double mobility = this.jet.mobilityMultiplier();

        // Throttle lever and engine. The lever stays where it is left; the engine follows it with a turbofan's lag.
        double airspeedNow = this.jet.getDeltaMovement().length();
        if (controlled && input.cruiseSpeed() > 0.0F) {
            // Cruise control: the autothrottle works the lever to hold the set airspeed, through climbs, dives and turns.
            double error = input.cruiseSpeed() - airspeedNow;
            double trend = airspeedNow - this.lastAirspeed;
            this.throttle = Mth.clamp(this.throttle + error * AUTOTHROTTLE_GAIN - trend * AUTOTHROTTLE_DAMPING, 0.0D, 1.0D);
        } else if (controlled) {
            this.throttle = Mth.clamp(this.throttle + (input.forward() ? THROTTLE_UP : 0.0D) - (input.backward() ? THROTTLE_DOWN : 0.0D), 0.0D, 1.0D);
        } else if (pilot == null) {
            this.throttle = 0.0D;
        }
        double spoolTarget = pilot == null || !hasEnergy || wrecking ? 0.0D : Math.max(IDLE, this.throttle);
        if (this.jet.isEngineDamaged()) {
            spoolTarget *= 0.6D;
        }
        this.lastAirspeed = airspeedNow;
        if (spoolTarget > this.spool && this.spool < IDLE) {
            // Starting: the starter winds the engine up to idle at its own pace.
            this.spool = Math.min(spoolTarget, this.spool + START_SPOOL_PER_TICK);
        } else {
            this.spool += (spoolTarget - this.spool) * (spoolTarget > this.spool ? SPOOL_UP : SPOOL_DOWN);
        }
        if (spoolTarget <= 0.0D && this.spool < 1.0E-3D) {
            this.spool = 0.0D;
        }
        if (server && this.spool > 0.005D) {
            int cost = this.jet.scaledEngineEnergyCost(engine, true, 0.2D + 0.8D * this.spool);
            if (cost > 0) {
                this.jet.consumeEngineEnergyCost(cost);
            }
        }

        Vec3 motion = this.jet.getDeltaMovement();
        Vector3d velocity = new Vector3d(motion.x, motion.y, motion.z);
        if (grounded && velocity.y < 0.0D) {
            velocity.y = 0.0D;
        }
        double speed = velocity.length();
        double yaw = this.jet.getYRot();
        double pitch = this.jet.getXRot();
        double roll = this.jet.roll();
        Quaterniond rotation = pose(yaw, pitch, roll);
        Vector3d forward = rotation.transform(new Vector3d(0.0D, 0.0D, 1.0D));
        Vector3d up = rotation.transform(new Vector3d(0.0D, 1.0D, 0.0D));
        Vector3d right = rotation.transform(new Vector3d(-1.0D, 0.0D, 0.0D));
        double alpha = speed > 0.05D ? Math.atan2(-velocity.dot(up), velocity.dot(forward)) : 0.0D;
        double sideslip = speed > 0.05D ? Math.atan2(velocity.dot(right), velocity.dot(forward)) : 0.0D;
        double gamma = speed > 0.05D ? Math.toDegrees(Math.asin(Mth.clamp(velocity.y / speed, -1.0D, 1.0D))) : 0.0D;
        double authority = Math.min(1.0D, (speed / rotateSpeed) * (speed / rotateSpeed));
        double airAuthority = Math.max(0.08D, authority);
        boolean steering = controlled && !this.jet.isFreeLookInputDown();
        double pitchCommand = steering ? Mth.clamp(-input.mouseY() * MOUSE_PITCH * engine.pitchSpeed(), -MAX_PITCH_RATE, MAX_PITCH_RATE) : 0.0D;
        double turnCommand = steering ? Mth.clamp(input.mouseX() * MOUSE_TURN * engine.yawSpeed(), -MAX_TURN_RATE, MAX_TURN_RATE) : 0.0D;
        int rollKey = controlled ? (input.right() ? 1 : 0) - (input.left() ? 1 : 0) : 0;
        double effectiveAlpha = alpha + INCIDENCE;
        this.stalled = !grounded && Math.abs(effectiveAlpha) > ALPHA_STALL;

        if (grounded) {
            // Wheels: the nose wheel steers (A/D, or the mouse), well over at taxi speed and only a little by take-off. It
            // swings over gradually, and the tyres' grip caps the turn: the faster it rolls, the wider it turns.
            double steer = Mth.clamp(rollKey + GROUND_MOUSE_STEER * turnCommand / MAX_TURN_RATE, -1.0D, 1.0D);
            this.noseWheel = Mth.approach((float) this.noseWheel, (float) steer, (float) NOSE_WHEEL_SLEW);
            double wheelAngle = Math.toRadians(Mth.lerp(Math.min(1.0D, speed / rotateSpeed), MAX_NOSE_WHEEL_ANGLE, TAKEOFF_NOSE_WHEEL_ANGLE));
            double groundYawRate = speed > 0.01D ? Math.toDegrees(speed * Math.tan(this.noseWheel * wheelAngle) / WHEELBASE) : 0.0D;
            double gripLimit = speed > 0.01D ? Math.toDegrees(GROUND_SIDE_ACCELERATION / speed) : MAX_GROUND_YAW_RATE;
            groundYawRate = Mth.clamp(groundYawRate, -Math.min(MAX_GROUND_YAW_RATE, gripLimit), Math.min(MAX_GROUND_YAW_RATE, gripLimit));
            if (velocity.dot(forward) < 0.0D) {
                groundYawRate = -groundYawRate;
            }
            this.yawRate += (groundYawRate - this.yawRate) * GROUND_YAW_RESPONSE;
            yaw += this.yawRate;
            float previousPitch = (float) pitch;
            if (controlled && speed >= rotateSpeed && pitchCommand > 0.0D) {
                // Rotate: the elevator lifts the nose wheel once there is the airspeed to fly (not before: a jet hauled
                // off the runway short of flying speed falls straight back onto it).
                pitch -= pitchCommand * authority;
            } else if (pitch < 0.0D) {
                pitch += Math.min(0.6D, -pitch);
            }
            pitch = Mth.clamp(pitch, -MAX_GROUND_NOSE_UP, 0.5D);
            roll *= 0.5D;
            this.pitchRate = previousPitch - pitch;
            this.rollRate = 0.0D;
            this.gammaHold = Double.NaN;
            rotation = pose(yaw, pitch, roll);
        } else {
            boolean upright = up.y > 0.2D;
            boolean rolling = rollKey != 0;
            double pitchDemand = pitchCommand * airAuthority;
            double yawDemand = 0.0D;
            double rollDemand = 0.0D;
            if (rolling) {
                rollDemand = rollKey * MAX_ROLL_RATE * engine.rollSpeed();
            } else if (upright && controlled) {
                // Auto-bank: the mouse asks for a turn; the bank that turn takes at this speed, and back to level after.
                double bank = Mth.clamp(AUTO_BANK_SHARE * Math.toDegrees(Math.atan(Math.toRadians(turnCommand) * speed / gravity)), -MAX_BANK, MAX_BANK);
                rollDemand = Mth.clamp((bank - roll) * AUTO_BANK_GAIN, -AUTO_BANK_RATE, AUTO_BANK_RATE);
            }
            if (upright) {
                // Bank to turn: the nose comes round as fast as the bank it has makes the jet turn (a coordinated turn
                // at this airspeed), as a pull in the wings' plane and a little yaw; the mouse only leads it with a
                // touch of rudder. So the turn builds with the bank, stops as the wings level, and holds its height.
                double turnRate = Math.toDegrees(gravity * Math.tan(Math.toRadians(Mth.clamp(roll, -70.0D, 70.0D))) / Math.max(speed, 0.5D * scale));
                pitchDemand += turnRate * Math.sin(Math.toRadians(roll));
                yawDemand += turnRate * Math.cos(Math.toRadians(roll)) + turnCommand * RUDDER_LEAD * airAuthority;
            } else {
                yawDemand += turnCommand * 0.5D * airAuthority;
            }
            // Hands off the pitch, the flight path holds: the nose comes up as the jet slows to keep it there, until
            // the speed is too low to, when the held climb is eased off rather than flying the jet into a stall.
            if (controlled && Math.abs(pitchCommand) < 0.05D && (upright || rolling) && !this.stalled && Math.abs(gamma) < 60.0D) {
                if (Double.isNaN(this.gammaHold)) {
                    this.gammaHold = Mth.clamp(gamma, -25.0D, 25.0D);
                }
                if (speed < 1.25D * stallSpeed && this.gammaHold > -3.0D) {
                    this.gammaHold -= 0.15D;
                }
                double hold = Mth.clamp(0.3D * (this.gammaHold - gamma) - 1.6D * (gamma - this.lastGamma), -1.2D, 1.2D);
                double cosRoll = Math.cos(Math.toRadians(roll));
                pitchDemand += upright && !rolling ? hold / Math.max(cosRoll, 0.4D) : hold * cosRoll;
            } else {
                this.gammaHold = Double.NaN;
            }
            if (controlled && upright && pitchDemand > 0.0D && speed < TAKEOFF_PROTECTION_SPEED * stallSpeed) {
                // Take-off and landing protection: low and slow, the nose will not come up past an attitude the jet can
                // hold (8 degrees just above the stall, 25 with speed in hand), so hauling back off the runway climbs
                // away rather than stalling a few blocks up. Higher up a pilot can still stall it.
                double height = this.jet.helicopterAltitudeFromGround();
                if (height < TAKEOFF_PROTECTION_HEIGHT) {
                    double margin = Mth.clamp((speed / stallSpeed - 1.0D) / (TAKEOFF_PROTECTION_SPEED - 1.0D), 0.0D, 1.0D);
                    double maxNoseUp = Mth.lerp(margin, 8.0D, 25.0D);
                    double noseUp = -pitch;
                    pitchDemand = Math.min(pitchDemand, (maxNoseUp - noseUp) * 0.25D);
                }
            }
            if (pitchDemand > 0.0D && effectiveAlpha > 0.75D * ALPHA_STALL) {
                pitchDemand *= Mth.clamp((0.95D * ALPHA_STALL - effectiveAlpha) / (0.2D * ALPHA_STALL), 0.0D, 1.0D);
            }
            if (!controlled) {
                // Nobody flying it: it noses over to follow its own path and slowly rolls off.
                pitchDemand -= Math.toDegrees(alpha) * 0.05D;
                rollDemand += pilot == null ? 0.4D : 0.8D;
            }
            if (this.stalled) {
                // Stalled: the nose drops and a wing goes.
                pitchDemand -= (Math.toDegrees(Math.abs(effectiveAlpha) - ALPHA_STALL) * 0.2D + 0.5D) * Math.signum(effectiveAlpha);
                rollDemand += roll >= 0.0D ? 0.6D : -0.6D;
            }
            if (this.jet.isJetGunFiring()) {
                // The gun shakes the nose about a little as it fires.
                pitchDemand += Math.sin(this.jet.tickCount * 1.7D) * 0.12D;
                yawDemand += Math.sin(this.jet.tickCount * 2.3D) * 0.08D;
            }
            // The fin weathervanes the nose into the wind.
            yawDemand += Math.toDegrees(sideslip) * 0.12D * Math.max(0.3D, authority);
            this.pitchRate += (pitchDemand - this.pitchRate) * PITCH_RESPONSE / weight;
            this.rollRate += (rollDemand - this.rollRate) * ROLL_RESPONSE / weight;
            this.yawRate += (yawDemand - this.yawRate) * YAW_RESPONSE / weight;
            // Body axes: nose up is a negative turn about X, nose right a negative turn about Y, right wing down positive Z.
            rotation.rotateX(Math.toRadians(-this.pitchRate))
                    .rotateY(Math.toRadians(-this.yawRate))
                    .rotateZ(Math.toRadians(this.rollRate));
            double[] angles = euler(rotation);
            yaw = angles[0];
            pitch = angles[1];
            roll = angles[2];
        }
        this.lastGamma = gamma;

        forward = rotation.transform(new Vector3d(0.0D, 0.0D, 1.0D));
        up = rotation.transform(new Vector3d(0.0D, 1.0D, 0.0D));
        right = rotation.transform(new Vector3d(-1.0D, 0.0D, 0.0D));
        double ceiling = this.jet.level().getMaxY() + 64.0D;
        double ceilingFactor = Mth.clamp((ceiling - this.jet.getY()) / 12.0D, 0.0D, 1.0D);
        double lift = 0.0D;
        if (speed > 0.05D) {
            Vector3d direction = new Vector3d(velocity).div(speed);
            alpha = Math.atan2(-velocity.dot(up), velocity.dot(forward));
            effectiveAlpha = alpha + INCIDENCE;
            double coefficient;
            if (Math.abs(effectiveAlpha) <= ALPHA_STALL) {
                coefficient = effectiveAlpha;
            } else {
                coefficient = Math.copySign(ALPHA_STALL * Math.max(0.45D, 1.0D - (Math.abs(effectiveAlpha) - ALPHA_STALL) / ALPHA_STALL * 1.2D), effectiveAlpha);
            }
            double liftConstant = gravity / (stallSpeed * stallSpeed * ALPHA_STALL);
            lift = Mth.clamp(liftConstant * speed * speed * coefficient * (0.5D + 0.5D * ceilingFactor), -gravity * MAX_LOAD, gravity * MAX_LOAD);
            Vector3d liftDirection = new Vector3d(up).sub(new Vector3d(direction).mul(direction.dot(up)));
            if (liftDirection.lengthSquared() > 1.0E-8D) {
                velocity.add(liftDirection.normalize().mul(lift));
            }
            double sideGrip = grounded ? TYRE_GRIP : Math.min(0.22D, 0.045D * speed / scale);
            velocity.sub(new Vector3d(right).mul(velocity.dot(right) * sideGrip));
            this.alphaDegrees = Math.toDegrees(effectiveAlpha);
        } else {
            this.alphaDegrees = 0.0D;
        }
        velocity.y -= gravity;
        if (this.jet.isJetGunFiring()) {
            double margin = Mth.clamp((speed / stallSpeed - GUN_RECOIL_FLOOR) / GUN_RECOIL_FADE, 0.0D, 1.0D);
            velocity.sub(new Vector3d(forward).mul(GUN_RECOIL_SHARE * engine.acceleration() * margin));
        }
        double thrust = this.spool * engine.acceleration() * (1.0D - THRUST_LAPSE * Math.min(speed, diveSpeed) / topSpeed) * mobility * ceilingFactor;
        velocity.add(new Vector3d(forward).mul(thrust));
        double newSpeed = velocity.length();
        boolean airbrake = controlled && input.brake() && !grounded;
        // Reverse in a dive opens the speed brakes; they stay out while it is held, through the pull-out.
        double noseDown = -Math.toDegrees(Math.asin(Mth.clamp(forward.y, -1.0D, 1.0D)));
        boolean diveBrake = controlled && !grounded && input.backward()
                && (this.speedBrake > 0.0F || noseDown > DIVE_BRAKE_NOSE_DOWN);
        this.speedBrake = Mth.approach(this.speedBrake, airbrake || diveBrake ? 1.0F : 0.0F, SPEED_BRAKE_TRAVEL);
        double inducedDrag = INDUCED_DRAG * scale;
        double cruiseGravity = GRAVITY * scale;
        double parasiteDrag = (engine.acceleration() * (1.0D - THRUST_LAPSE) - inducedDrag * cruiseGravity * cruiseGravity / (topSpeed * topSpeed)) / (topSpeed * topSpeed);
        double drag = Math.max(0.0D, parasiteDrag) * newSpeed * newSpeed * (1.0D + AIRBRAKE_DRAG * this.speedBrake + GEAR_DRAG * this.gear);
        drag += Math.min(0.03D * scale, inducedDrag * lift * lift / Math.max(newSpeed * newSpeed, 0.36D * scale * scale));
        if (newSpeed > 1.0E-4D) {
            velocity.sub(new Vector3d(velocity).div(newSpeed).mul(Math.min(drag, newSpeed)));
        }
        if (grounded) {
            // The tyres and brakes act along the ground only: taken off the whole velocity, most of it went into this
            // tick's gravity (which the ground stops anyway), and a slow jet never quite came to rest.
            double wheelDrag = ROLLING_FRICTION + (controlled && input.brake() ? WHEEL_BRAKE : 0.0D) + (pilot == null ? PARKING_BRAKE : 0.0D)
                    + (controlled && this.throttle <= 1.0E-3D ? IDLE_HOLD_BRAKE : 0.0D);
            double rolling = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            if (rolling > 1.0E-6D) {
                double kept = Math.max(0.0D, rolling - wheelDrag) / rolling;
                velocity.x *= kept;
                velocity.z *= kept;
            }
        }
        newSpeed = velocity.length();
        double brakedLimit = Math.max(stallSpeed * 1.25D, topSpeed * SPEED_BRAKE_LIMIT_SHARE);
        if (this.speedBrake > 0.0F && !grounded && newSpeed > brakedLimit) {
            velocity.mul((newSpeed - (newSpeed - brakedLimit) * SPEED_BRAKE_SHED * this.speedBrake) / newSpeed);
            newSpeed = velocity.length();
        }
        if (newSpeed > diveSpeed) {
            velocity.mul((diveSpeed + (newSpeed - diveSpeed) * 0.5D) / newSpeed);
        }
        if (ceilingFactor <= 0.0D && velocity.y > 0.0D) {
            velocity.y *= 0.25D;
        }
        if (this.jet.isInWater()) {
            velocity.mul(0.7D);
        }

        this.jet.setYRot((float) yaw);
        this.jet.setXRot((float) pitch);
        this.jet.setRoll((float) roll);
        this.jet.updateVehicleBoundingBox();
        this.jet.moveHelicopter(new Vec3(velocity.x, velocity.y, velocity.z));

        boolean groundedNow = this.jet.onGround() || this.jet.verticalCollisionBelow;
        this.airborneTicks = groundedNow ? 0 : Math.min(this.airborneTicks + 1, 1000);
        double altitude = groundedNow ? 0.0D : this.jet.helicopterAltitudeFromGround();
        this.tickGear(server, groundedNow, altitude, velocity.y);
        double speedNow = velocity.length();
        this.stallWarning = !groundedNow && this.airborneTicks > 20 && altitude > 2.0D
                && (this.alphaDegrees > Math.toDegrees(ALPHA_STALL) * 0.85D || speedNow < stallSpeed * 1.05D);
        if (server) {
            this.tickStructureStrikes(speedNow);
            this.jet.setSyncedJetState((float) this.throttle, (float) this.spool, this.gear, this.speedBrake);
        }
    }

    /** Automatic gear: up once well clear of the ground, down for the approach and whenever it is on its wheels. */
    private void tickGear(boolean server, boolean grounded, double altitude, double verticalSpeed) {
        boolean down = this.gearTargetDown;
        if (grounded) {
            down = true;
        } else if (this.gearTargetDown && altitude > GEAR_UP_ALTITUDE && this.airborneTicks > 30) {
            down = false;
        } else if (!this.gearTargetDown && altitude < GEAR_DOWN_ALTITUDE && verticalSpeed < 0.0D) {
            down = true;
        }
        if (down != this.gearTargetDown) {
            this.gearTargetDown = down;
            if (server) {
                this.playSound("gear", 1.6F, down ? 0.9F : 1.0F);
            }
        }
        float target = this.gearTargetDown ? 1.0F : 0.0F;
        this.gear = Mth.approach(this.gear, target, grounded ? 1.0F : GEAR_TRAVEL_PER_TICK);
    }

    /**
     * The hull touched down or ran into something (server side, from {@link VehicleEntity#move}). A landing within the
     * safe sink rate, wings level and nose up, is free; harder ones cost health and break the airframe past the limit.
     */
    /** This jet's top speed against the one the thresholds were set for. */
    private double speedScale() {
        return Math.max(0.1D, this.jet.vehicleData().defaults().engine().maxForwardSpeed()) / REFERENCE_TOP_SPEED;
    }

    void onHullImpact(Vec3 requestedMovement, boolean wasSupported) {
        DamageSource source = this.jet.vehicleStrikeDamageSource();
        double scale = this.speedScale();
        // A slower jet comes down the same slope more slowly: sink rates shrink with its speed.
        double sinkScale = Math.max(0.4D, scale);
        double safeSink = SAFE_SINK * sinkScale;
        double fatalSink = FATAL_SINK * sinkScale;
        if (this.jet.verticalCollision && !wasSupported && requestedMovement.y < 0.0D) {
            double sink = -requestedMovement.y;
            double groundSpeed = requestedMovement.horizontalDistance();
            float bank = Math.abs(Mth.wrapDegrees(this.jet.roll()));
            float pitch = this.jet.getXRot();
            float max = this.jet.maxVehicleHealth();
            float damage = 0.0F;
            if (sink > safeSink) {
                double t = Mth.clamp((sink - safeSink) / (fatalSink - safeSink), 0.0D, 1.0D);
                damage += (float) (max * 0.8D * t * t);
            }
            if (bank > 20.0F && groundSpeed > 0.4D * scale) {
                damage += max * 0.25F; // a wingtip hits first
            }
            if (pitch > 4.0F && groundSpeed > 0.4D * scale) {
                damage += max * 0.3F; // nose wheel first
            }
            if (this.gear < 0.9F && groundSpeed > 0.4D * scale) {
                damage += max * 0.4F; // on its belly
            }
            if (sink >= fatalSink || bank > 75.0F && groundSpeed > 0.8D * scale) {
                this.jet.hurtVehicleIgnoringArmor(source, max + 1.0F);
            } else if (damage > 0.5F) {
                this.jet.hurtVehicleStrikeWithArmor(source, damage);
                if (sink > safeSink) {
                    // A hard landing bounces.
                    Vec3 motion = this.jet.getDeltaMovement();
                    this.jet.setDeltaMovement(motion.x, sink * 0.3D, motion.z);
                }
            }
            if (groundSpeed > 0.5D * scale && this.airborneTicks > 0 && this.jet.level() instanceof ServerLevel level) {
                // A puff of tyre smoke off each main wheel as it spins up on the runway.
                for (double side : new double[]{2.55D, -2.55D}) {
                    Vec3 wheel = this.jet.position().add(this.jet.rotateLocalOffsetWithPose(side, 0.2D, -0.9D, 1.0F));
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, wheel.x, wheel.y, wheel.z, 5, 0.25D, 0.05D, 0.35D, 0.02D);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, wheel.x, wheel.y, wheel.z, 4, 0.2D, 0.05D, 0.3D, 0.01D);
                }
            }
            if (groundSpeed > 0.5D * scale && this.airborneTicks > 0) {
                float volume = (float) Mth.clamp(0.8D + groundSpeed / scale * 0.6D + sink / sinkScale * 2.0D, 0.8D, 3.0D);
                this.playSound("touchdown", volume, 0.9F + this.jet.getRandom().nextFloat() * 0.2F);
            }
        }
        if (this.jet.horizontalCollision) {
            Vec3 now = this.jet.getDeltaMovement();
            double blocked = new Vec3(requestedMovement.x - now.x, 0.0D, requestedMovement.z - now.z).length();
            this.strike(blocked, true, source);
        }
    }

    /** The wingtips, fins, nose and engines reach well past the hull box: anything solid there is a strike. */
    private void tickStructureStrikes(double speed) {
        if (this.strikeCooldown > 0) {
            this.strikeCooldown--;
            return;
        }
        if (speed < STRIKE_MIN_SPEED * this.speedScale()) {
            return;
        }
        Vec3 origin = this.jet.position();
        for (double[] point : STRIKE_POINTS) {
            Vec3 world = origin.add(this.jet.rotateLocalOffsetWithPose(point[0], point[1], point[2], 1.0F));
            AABB probe = new AABB(world.x - 0.2D, world.y - 0.2D, world.z - 0.2D, world.x + 0.2D, world.y + 0.2D, world.z + 0.2D);
            if (!this.jet.level().hasChunkAt(BlockPos.containing(world)) || !this.jet.intersectsBlockCollision(probe)) {
                continue;
            }
            // Wings and the tail tear before the fuselage gives; the nose and engines are the airframe itself.
            boolean structure = point[3] == 0;
            this.strike(speed, structure, this.jet.vehicleStrikeDamageSource());
            return;
        }
    }

    private void strike(double speed, boolean structure, DamageSource source) {
        // Measured against the jet's own top speed: a strike at half its top speed does what it does at half of any.
        speed /= this.speedScale();
        if (speed < STRIKE_MIN_SPEED || this.strikeCooldown > 0) {
            return;
        }
        this.strikeCooldown = STRIKE_COOLDOWN_TICKS;
        float max = this.jet.maxVehicleHealth();
        if (speed >= (structure ? 1.0D : 1.4D)) {
            this.jet.hurtVehicleIgnoringArmor(source, max + 1.0F);
            return;
        }
        this.jet.hurtVehicleStrikeWithArmor(source, (float) ((speed - STRIKE_MIN_SPEED) * max * (structure ? 0.9D : 0.5D)));
        this.velocityScale(structure ? 0.6D : 0.85D);
    }

    private void velocityScale(double scale) {
        this.jet.setDeltaMovement(this.jet.getDeltaMovement().scale(scale));
    }

    /**
     * One of the jet's sounds, for everyone around it but its pilot: the pilot's own client plays it (JetAudio), muffled
     * from the cockpit view or as it sounds outside from the chase camera, which only it knows.
     */
    private void playSound(String name, float volume, float pitch) {
        SoundEvent sound = VehicleSoundHelper.jetSound(this.jet, name);
        if (sound == null || !(this.jet.level() instanceof ServerLevel level)) {
            return;
        }
        ServerPlayer pilot = this.jet.getControllingPassenger() instanceof ServerPlayer player ? player : null;
        level.playSound(pilot, this.jet.getX(), this.jet.getY(), this.jet.getZ(), sound, SoundSource.NEUTRAL, volume, pitch);
    }
}
