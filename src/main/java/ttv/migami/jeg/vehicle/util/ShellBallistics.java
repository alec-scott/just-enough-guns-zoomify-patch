package ttv.migami.jeg.vehicle.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * How a gun's shell flies, shared by the server (ShellEntity moves by it) and the client (the gunner's HUD predicts by
 * it), so the prediction is the flight. No drag: gravity each tick, then the step. Units are blocks and ticks.
 *
 * @param muzzleVelocity blocks per tick
 * @param gravity        blocks per tick squared
 * @param maxFlightTicks the longest flight followed, past the longest the gun can fire
 */
public record ShellBallistics(double muzzleVelocity, double gravity, int maxFlightTicks) {
    /** One tick of flight: gravity first, then the step. */
    public Vec3 step(Vec3 velocity) {
        return velocity.add(0.0D, -this.gravity, 0.0D);
    }

    /** Launch velocity for this bearing (vehicle-style yaw) and elevation (degrees above level), without spread. */
    public Vec3 launchVelocity(float yaw, float elevation) {
        return Vec3.directionFromRotation(-elevation, yaw).scale(this.muzzleVelocity);
    }

    /**
     * Where a shell fired from {@code muzzle} with {@code velocity} comes down to {@code groundY}, ignoring terrain in
     * between: the HUD's fallback where the target area is not loaded.
     */
    public Impact predictToHeight(Vec3 muzzle, Vec3 velocity, double groundY) {
        Vec3 position = muzzle;
        Vec3 motion = velocity;
        for (int tick = 1; tick <= this.maxFlightTicks; tick++) {
            motion = this.step(motion);
            Vec3 next = position.add(motion);
            if (motion.y < 0.0D && next.y <= groundY) {
                double share = (position.y - groundY) / (position.y - next.y);
                return new Impact(position.add(motion.scale(Mth.clamp(share, 0.0D, 1.0D))), tick);
            }
            position = next;
        }
        return new Impact(position, this.maxFlightTicks);
    }

    public record Impact(Vec3 position, int ticks) {
        public double horizontalDistance(Vec3 from) {
            double dx = this.position.x - from.x;
            double dz = this.position.z - from.z;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }
}
