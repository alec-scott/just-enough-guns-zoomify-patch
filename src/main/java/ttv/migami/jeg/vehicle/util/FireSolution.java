package ttv.migami.jeg.vehicle.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A fire mission's numbers: the bearing to lay and the elevations (low and high arc) that land a shell on a target,
 * solved by flying the shell tick by tick with the same ballistics as the flight ({@link ShellBallistics}), so the
 * answer is what the shell will do. The muzzle is taken on its arm about the trunnions at each trial elevation.
 *
 * @param bearing   compass degrees from north, as the gun's own bearing is shown
 * @param range     horizontal distance from the trunnions to the target, blocks
 * @param low       elevation of the low arc (below the elevation of greatest range), or NaN if out of reach
 * @param high      elevation of the high arc, or NaN if out of reach or above the gun's limit
 * @param lowTicks  flight time on the low arc
 * @param highTicks flight time on the high arc
 */
public record FireSolution(float bearing, double range, float low, float high, int lowTicks, int highTicks) {
    public boolean reachable() {
        return !Float.isNaN(this.low) || !Float.isNaN(this.high);
    }

    /**
     * @param trunnion   the trunnions in the world
     * @param muzzleArm  distance from the trunnions to the muzzle along the bore
     * @param target     the point to land on
     * @param minElevation the gun's limits
     */
    public static FireSolution solve(ShellBallistics ballistics, Vec3 trunnion, double muzzleArm, Vec3 target,
                                     float minElevation, float maxElevation) {
        double dx = target.x - trunnion.x;
        double dz = target.z - trunnion.z;
        double range = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
        float bearing = (float) Math.floorMod(Math.round((yaw + 180.0F) * 10.0F), 3600) / 10.0F;
        // The elevation of greatest range for this charge and height: below it the low arc, above it the high arc.
        float best = minElevation;
        double bestRange = -1.0D;
        for (float e = Math.max(minElevation, 10.0F); e <= maxElevation; e += 1.0F) {
            double r = reach(ballistics, trunnion, muzzleArm, yaw, e, target.y);
            if (r > bestRange) {
                bestRange = r;
                best = e;
            }
        }
        float low = Float.NaN, high = Float.NaN;
        int lowTicks = 0, highTicks = 0;
        if (bestRange >= range) {
            low = bisect(ballistics, trunnion, muzzleArm, yaw, target.y, range, minElevation, best, true);
            if (!Float.isNaN(low)) {
                lowTicks = flight(ballistics, trunnion, muzzleArm, yaw, low, target.y).ticks();
            }
            high = bisect(ballistics, trunnion, muzzleArm, yaw, target.y, range, best, maxElevation, false);
            if (!Float.isNaN(high)) {
                highTicks = flight(ballistics, trunnion, muzzleArm, yaw, high, target.y).ticks();
            }
        }
        return new FireSolution(bearing, range, low, high, lowTicks, highTicks);
    }

    /** The muzzle at this lay, and where its shell comes down to the target's height. */
    private static ShellBallistics.Impact flight(ShellBallistics ballistics, Vec3 trunnion, double muzzleArm, float yaw, float elevation, double groundY) {
        Vec3 bore = Vec3.directionFromRotation(-elevation, yaw);
        return ballistics.predictToHeight(trunnion.add(bore.scale(muzzleArm)), bore.scale(ballistics.muzzleVelocity()), groundY);
    }

    private static double reach(ShellBallistics ballistics, Vec3 trunnion, double muzzleArm, float yaw, float elevation, double groundY) {
        ShellBallistics.Impact impact = flight(ballistics, trunnion, muzzleArm, yaw, elevation, groundY);
        if (impact.ticks() >= ballistics.maxFlightTicks()) {
            return -1.0D;
        }
        return impact.horizontalDistance(trunnion);
    }

    /** The elevation in [from, to] whose shell lands at {@code range}; range rises with elevation on the low arc. */
    private static float bisect(ShellBallistics ballistics, Vec3 trunnion, double muzzleArm, float yaw, double groundY,
                                double range, float from, float to, boolean rising) {
        double atFrom = reach(ballistics, trunnion, muzzleArm, yaw, from, groundY);
        double atTo = reach(ballistics, trunnion, muzzleArm, yaw, to, groundY);
        double lo = rising ? atFrom : atTo, hi = rising ? atTo : atFrom;
        if (range < lo || range > hi) {
            return Float.NaN;
        }
        float a = from, b = to;
        for (int i = 0; i < 28; i++) {
            float mid = (a + b) * 0.5F;
            double r = reach(ballistics, trunnion, muzzleArm, yaw, mid, groundY);
            boolean shortOf = r < range;
            if (shortOf == rising) {
                a = mid;
            } else {
                b = mid;
            }
        }
        return (a + b) * 0.5F;
    }
}
