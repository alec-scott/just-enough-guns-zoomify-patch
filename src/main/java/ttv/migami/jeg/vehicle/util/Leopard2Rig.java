package ttv.migami.jeg.vehicle.util;

import net.minecraft.world.phys.Vec3;

/**
 * The Leopard 2A7's geometry that gameplay depends on, mirroring design/leopard2/rig.json (written by
 * design/leopard2/generate.py). Vehicle data coordinates: blocks, +Z forward, +X left.
 */
public final class Leopard2Rig {
    /** The muzzle with the gun level and the turret straight ahead. */
    public static final Vec3 MUZZLE_REST = new Vec3(0.0D, 2.0625D, 7.625D);
    /** The remote weapon station's trunnion with the turret straight ahead, and its reach to the muzzle. */
    public static final Vec3 RWS_TRUNNION = new Vec3(0.75D, 2.7812D, -0.75D);
    public static final double RWS_ARM = 0.375D - RWS_TRUNNION.z;
    public static final float MIN_ELEVATION = -9.0F;
    public static final float MAX_ELEVATION = 20.0F;
    /** Within this many degrees of straight back the gun stays at or above REAR_MIN_ELEVATION (the rear deck). */
    public static final float REAR_ARC = 70.0F;
    public static final float REAR_MIN_ELEVATION = 0.0F;
    public static final float RWS_MIN_ELEVATION = -10.0F;
    public static final float RWS_MAX_ELEVATION = 60.0F;
    /** How far the tube slides back on firing, model pixels. */
    public static final float RECOIL_PIXELS = 6.0F;

    private Leopard2Rig() {}
}
