package ttv.migami.jeg.vehicle.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The 81 mm mortar's geometry and ballistics, shared by the server (where the round leaves the muzzle, how it flies)
 * and the client (the bipod pose, the HUD's predicted impact). Model units are pixels (16 per block); the model faces
 * -Z. Mirrors {@code design/mortar/rig.json}, written by {@code design/mortar/refit.py}.
 */
public final class MortarRig {
    /** Real 81 mm mortars lay between these elevations. */
    public static final float ELEVATION_MIN = 45.0F;
    public static final float ELEVATION_MAX = 85.0F;
    /** Muzzle velocity in blocks per tick and gravity in blocks per tick squared (Superb Warfare's mortar). */
    public static final double MUZZLE_VELOCITY = 10.0D;
    public static final double GRAVITY = 0.13D;
    /** Superb Warfare's spread for the mortar, in vanilla projectile inaccuracy units. */
    public static final double SPREAD = 0.5D;
    /** Elevation joint (the {@code barrel} bone's pivot), model pixels. */
    public static final Vec3 ELEVATION_PIVOT = new Vec3(0.0D, 2.6068D, 0.0D);
    /** Muzzle with the tube level (elevation 0), model pixels: the tube lies along -Z from the pivot. */
    public static final Vec3 MUZZLE_REST = new Vec3(0.0D, 3.664D, -18.962D);
    /** Bipod hinge angle (file degrees) every 5 degrees of elevation from {@link #ELEVATION_MIN}, solved by the refit. */
    private static final float[] BIPOD = {85.218F, 95.283F, 105.491F, 115.902F, 126.633F, 137.953F, 150.759F, 164.882F, 177.187F};
    private static final float BIPOD_STEP = 5.0F;
    /** Longest flight the solver follows: well past the 85-degree flight time (about 155 ticks). */
    public static final int MAX_FLIGHT_TICKS = 400;

    private MortarRig() {}

    public static float clampElevation(float elevation) {
        return Mth.clamp(elevation, ELEVATION_MIN, ELEVATION_MAX);
    }

    /** The bipod hinge's file X rotation at this elevation; the renderer sets {@code setRotX(-angle)}. */
    public static float bipodAngle(float elevation) {
        float index = (clampElevation(elevation) - ELEVATION_MIN) / BIPOD_STEP;
        int low = Mth.clamp(Mth.floor(index), 0, BIPOD.length - 1);
        int high = Math.min(low + 1, BIPOD.length - 1);
        return Mth.lerp(index - low, BIPOD[low], BIPOD[high]);
    }

    /** The muzzle in model pixels at this elevation: the rest point turned about the elevation joint. */
    public static Vec3 muzzleModel(float elevation) {
        double e = Math.toRadians(elevation);
        double a = MUZZLE_REST.y - ELEVATION_PIVOT.y;
        double b = MUZZLE_REST.z - ELEVATION_PIVOT.z;
        double y = a * Math.cos(e) - b * Math.sin(e);
        double z = a * Math.sin(e) + b * Math.cos(e);
        return new Vec3(MUZZLE_REST.x, ELEVATION_PIVOT.y + y, ELEVATION_PIVOT.z + z);
    }

    /** The muzzle in the world, for a mortar standing at {@code origin} facing {@code yaw}. */
    public static Vec3 muzzleWorld(Vec3 origin, float yaw, float elevation) {
        Vec3 model = muzzleModel(elevation);
        double forward = -model.z / 16.0D;
        double rad = Math.toRadians(yaw);
        return origin.add(-Math.sin(rad) * forward, model.y / 16.0D, Math.cos(rad) * forward);
    }

    /** Launch velocity (blocks per tick) for this bearing and elevation, without spread. */
    public static Vec3 launchVelocity(float yaw, float elevation) {
        return Vec3.directionFromRotation(-elevation, yaw).scale(MUZZLE_VELOCITY);
    }

    /** One tick of flight, exactly as {@code MortarRoundEntity} moves: gravity first, then the step. */
    public static Vec3 stepVelocity(Vec3 velocity) {
        return velocity.add(0.0D, -GRAVITY, 0.0D);
    }

    /**
     * Where a round fired from {@code muzzle} with {@code velocity} comes down to {@code groundY}, ignoring terrain in
     * between: the HUD's fallback when the target area is not loaded.
     */
    public static Impact predictToHeight(Vec3 muzzle, Vec3 velocity, double groundY) {
        Vec3 position = muzzle;
        Vec3 motion = velocity;
        for (int tick = 1; tick <= MAX_FLIGHT_TICKS; tick++) {
            motion = stepVelocity(motion);
            Vec3 next = position.add(motion);
            if (motion.y < 0.0D && next.y <= groundY) {
                double share = (position.y - groundY) / (position.y - next.y);
                return new Impact(position.add(motion.scale(Mth.clamp(share, 0.0D, 1.0D))), tick);
            }
            position = next;
        }
        return new Impact(position, MAX_FLIGHT_TICKS);
    }

    public record Impact(Vec3 position, int ticks) {
        public double horizontalDistance(Vec3 from) {
            double dx = this.position.x - from.x;
            double dz = this.position.z - from.z;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }
}
