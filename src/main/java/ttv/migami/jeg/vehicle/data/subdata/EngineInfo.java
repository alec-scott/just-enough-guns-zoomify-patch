package ttv.migami.jeg.vehicle.data.subdata;

import net.minecraft.resources.Identifier;

public record EngineInfo(
        EngineType type,
        double acceleration,
        double maxForwardSpeed,
        double maxReverseSpeed,
        double friction,
        double steeringSpeed,
        int energyCostRate,
        double increment,
        double decrement,
        double pitchSpeed,
        double yawSpeed,
        double rollSpeed,
        double liftSpeed,
        Identifier engineStartSound,
        float engineSoundVolume,
        // Player-flown helicopters: top speed in blocks per tick (0 = twice max_forward_speed) and acceleration multiplier.
        double playerMaxSpeed,
        double playerAcceleration,
        // Weighty helicopter flight: how heavy the airframe feels (1 = light scout). Slows tilt, speed and climb response.
        double playerWeight,
        // Jets (engine type "jet"), all in blocks per tick: the speed below which the wing cannot hold the aircraft up in
        // level flight, the take-off speed at which the nose can be lifted off the runway, and the never-exceed speed of a
        // dive. Zero derives them from max_forward_speed (40%, 48% and 125% of it).
        double stallSpeed,
        double rotateSpeed,
        double maxDiveSpeed
) {
    public static final EngineInfo WHEEL_TEST = new EngineInfo(EngineType.WHEEL, 0.045D, 0.42D, 0.18D, 0.82D, 0.0D, 0, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D, null, 1.0F, 0.0D, 1.0D, 1.0D, 0.0D, 0.0D, 0.0D);

    public EngineInfo(EngineType type, double acceleration, double maxForwardSpeed, double maxReverseSpeed, double friction, double steeringSpeed, int energyCostRate) {
        this(type, acceleration, maxForwardSpeed, maxReverseSpeed, friction, steeringSpeed, energyCostRate, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D, 1.0D, null, 1.0F, 0.0D, 1.0D, 1.0D, 0.0D, 0.0D, 0.0D);
    }

    public double jetStallSpeed() {
        return this.stallSpeed > 0.0D ? this.stallSpeed : this.maxForwardSpeed * 0.4D;
    }

    public double jetRotateSpeed() {
        return this.rotateSpeed > 0.0D ? this.rotateSpeed : Math.max(this.jetStallSpeed() * 1.2D, this.maxForwardSpeed * 0.48D);
    }

    public double jetMaxDiveSpeed() {
        return this.maxDiveSpeed > 0.0D ? this.maxDiveSpeed : this.maxForwardSpeed * 1.25D;
    }
}
