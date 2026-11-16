package ttv.migami.jeg.vehicle.util;

import net.minecraft.world.phys.Vec3;

/**
 * The PLZ-05's geometry that gameplay depends on, mirroring design/plz05/rig.json (written by design/plz05/refit.py).
 * Vehicle data coordinates: blocks, +Z forward, +X left.
 */
public final class Plz05Rig {
    /** The turret's turning axis (the data's turret origin). */
    public static final Vec3 TURRET_ORIGIN = new Vec3(0.0D, 1.4865D, -0.6757D);
    /** The trunnions, about which the gun elevates, with the turret straight ahead. */
    public static final Vec3 TRUNNION = new Vec3(0.0D, 2.1622D, 0.0676D);
    /** The muzzle with the gun level and the turret straight ahead. */
    public static final Vec3 MUZZLE_REST = new Vec3(0.0D, 2.1622D, 8.018D);
    /** From the trunnions to the muzzle along the bore. */
    public static final double MUZZLE_ARM = MUZZLE_REST.z - TRUNNION.z;
    /** The roof machine gun's trunnion (on its pintle at the right-hand hatch), with the turret straight ahead. */
    public static final Vec3 HMG_TRUNNION = new Vec3(-0.9234D, 3.1388D, -1.1087D);
    /** From the roof gun's trunnion to its muzzle. */
    public static final double HMG_ARM = 0.1163D - HMG_TRUNNION.z;
    public static final float MIN_ELEVATION = -3.0F;
    public static final float MAX_ELEVATION = 68.0F;
    /** The roof gun's own limits, degrees above level. */
    public static final float HMG_MIN_ELEVATION = -10.0F;
    public static final float HMG_MAX_ELEVATION = 60.0F;
    /** How far the tube slides back on firing, model pixels (SBW's clip, scaled). */
    public static final float RECOIL_PIXELS = 8.47F;

    private Plz05Rig() {}
}
