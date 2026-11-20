package ttv.migami.jeg.vehicle.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The YX-100's geometry that gameplay depends on, mirroring design/yx100/rig.json (written by design/yx100/refit.py).
 * Vehicle data coordinates: blocks, +Z forward, +X left.
 */
public final class Yx100Rig {
    /** The muzzle with the gun level and the turret straight ahead. */
    public static final Vec3 MUZZLE_REST = new Vec3(0.0D, 1.8649D, 5.1471D);
    /** The remote weapon station's trunnion with the turret straight ahead, and its reach to the muzzle. */
    public static final Vec3 RWS_TRUNNION = new Vec3(-0.5598D, 2.6717D, -0.7024D);
    public static final double RWS_ARM = 0.7529D - RWS_TRUNNION.z;
    /** SBW's TurretPitchRange. */
    public static final float MIN_ELEVATION = -10.0F;
    public static final float MAX_ELEVATION = 30.0F;
    public static final float RWS_MIN_ELEVATION = -10.0F;
    public static final float RWS_MAX_ELEVATION = 60.0F;
    /** How far the tube slides back on firing, model pixels (SBW's 9.25 at scale 0.74). */
    public static final float RECOIL_PIXELS = 6.845F;
    /**
     * The lowest elevation at which the tube and its shroud clear the hull, by the turret's bearing from straight
     * ahead in 5 degree steps, the same either side (rig.json "lowest_elevation_by_bearing"). The gun sits low over
     * the front deck's fittings, and the engine deck stands higher than it behind.
     */
    private static final float[] LOWEST_ELEVATION = {
            -3.5F, -3.5F, -3.5F, -2.0F, -2.0F, -2.0F, -2.0F, -2.0F, -2.5F, -3.5F, -4.0F, -4.5F, -5.0F, -5.5F, -5.5F, -6.0F,
            -7.0F, -7.0F, -7.0F, -6.5F, -6.0F, -5.5F, -5.0F, -4.5F, -4.0F, -4.0F, -3.5F, -3.0F, -2.5F, 2.0F, 2.0F, 2.5F,
            3.0F, 3.5F, 3.5F, 3.5F, 3.5F};

    private Yx100Rig() {}

    /**
     * The lowest elevation the gun may take with the turret at this yaw (degrees, either sign, any wrap): the stricter
     * of the two measured bearings either side, so the gun never dips into the deck between them.
     */
    public static float lowestElevation(float turretYaw) {
        float bearing = Math.abs(Mth.wrapDegrees(turretYaw)) / 5.0F;
        int i = Math.min((int) bearing, LOWEST_ELEVATION.length - 2);
        return Math.max(MIN_ELEVATION, Math.max(LOWEST_ELEVATION[i], LOWEST_ELEVATION[i + 1]));
    }
}
