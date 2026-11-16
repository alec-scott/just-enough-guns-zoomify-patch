package ttv.migami.jeg.vehicle.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The 105 mm howitzer's geometry that gameplay depends on, mirroring design/howitzer/rig.json (written by
 * design/howitzer/generate.py). Vehicle data coordinates: blocks, +Z forward, +X left.
 */
public final class HowitzerRig {
    /** The muzzle with the gun level and laid straight ahead. */
    public static final Vec3 MUZZLE_REST = new Vec3(0.0D, 1.1562D, 2.125D);
    /** The trunnions, about which the cradle elevates. */
    public static final Vec3 TRUNNION = new Vec3(0.0D, 1.125D, -0.125D);
    /** The lunette on the left trail's end, with the trails closed for towing. */
    public static final Vec3 TOW_EYE = new Vec3(-0.0606D, 0.1609D, -4.0575D);
    public static final float MIN_ELEVATION = -5.0F;
    public static final float MAX_ELEVATION = 66.0F;
    /** Tube travel along the cradle (model pixels), at no elevation and at full elevation (variable recoil). */
    public static final float RECOIL_LOW = 16.0F;
    public static final float RECOIL_HIGH = 6.0F;
    /** Each trail's swing from straight: in to meet the other for towing, out to fire (degrees). */
    public static final float TRAIL_CLOSED = 3.0F;
    public static final float TRAIL_OPEN = 26.0F;
    /** The spades' fold up onto the trails for travel (degrees). */
    public static final float SPADE_FOLDED = 84.0F;
    /** Deploy progress over which the trails open, then the spades go down. */
    public static final float TRAILS_END = 0.6F;
    public static final double WHEEL_RADIUS = 0.5D;

    private HowitzerRig() {}

    private static float stage(float progress, float from, float to) {
        float t = Mth.clamp((progress - from) / (to - from), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Each trail's swing out from straight, degrees (the left trail's sense; the right swings the other way). */
    public static float trailAngle(float progress) {
        return -TRAIL_CLOSED + (TRAIL_OPEN + TRAIL_CLOSED) * stage(progress, 0.0F, TRAILS_END);
    }

    /** How far the spades are folded up, degrees (0 dug in). */
    public static float spadeFold(float progress) {
        return SPADE_FOLDED * (1.0F - stage(progress, TRAILS_END, 1.0F));
    }

    /** The tube's full recoil at this elevation, model pixels: it recoils less the higher it is laid. */
    public static float recoilLength(float elevation) {
        float t = Mth.clamp((elevation - MIN_ELEVATION) / (MAX_ELEVATION - MIN_ELEVATION), 0.0F, 1.0F);
        return RECOIL_LOW + (RECOIL_HIGH - RECOIL_LOW) * t;
    }
}
