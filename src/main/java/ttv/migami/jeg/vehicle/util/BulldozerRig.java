package ttv.migami.jeg.vehicle.util;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Geometry shared by the blade's server work volume and the client articulation. Source units are model pixels
 * (16 per block), the machine faces -Z and +X is the operator's left. Mirrors {@code design/bulldozer/rig.json}.
 */
public final class BulldozerRig {
    public static final double TRACK_LENGTH = 132.8407044966673;
    public static final double TRACK_RADIUS = 6.5;
    /** Distance from the centreline to each track's centre, in source pixels. */
    public static final double TRACK_HALF_GAUGE = 17;
    /** Rolling radius of each wheel bone, in order: idler, five bottom rollers, two carrier rollers, sprocket. */
    public static final double[] WHEEL_RADII = {6.5, 2.3, 2.3, 2.3, 2.3, 2.3, 1.45, 1.45, 6.5};
    public static final float LIFT_MIN = -4F, LIFT_MAX = 30F;
    /** Above this lift the blade is carried for travel and does no work. */
    public static final float TRAVEL_LIFT = 22F;
    public static final Vec3 LIFT_PIVOT = new Vec3(0, 8, -2);
    public static final Vec3 BLADE_PIVOT = new Vec3(0, 9, -39);
    public static final Vec3 CUT_EDGE = new Vec3(0, 0, -46.6);
    public static final Vec3 LIFT_BASE = new Vec3(12.5, 25, -19);
    public static final Vec3 LIFT_END = new Vec3(12.5, 16, -39.5);
    public static final double LIFT_BARREL = 15;
    public static final Vec3 TILT_BASE = new Vec3(21.2, 10, -24);
    public static final Vec3 TILT_END = new Vec3(21.2, 21, -39.8);
    public static final double TILT_BARREL = 13;
    public static final Vec3 EXHAUST_TIP = new Vec3(8.4, 42.6, -17);
    /** Ripper: links swing down up to this many degrees; at full depth the shanks are in the ground. */
    public static final float RIPPER_MAX = 45F;
    public static final double RIPPER_LINK = 9;
    public static final Vec3 RIPPER_BASE = new Vec3(4, 17, 29);
    public static final Vec3 RIPPER_END = new Vec3(4, 13, 40);
    public static final double RIPPER_BARREL = 7;
    /** Where the shanks meet the ground, in source pixels behind the machine's centre. */
    public static final double RIPPER_SHANK_Z = 39.5;
    /**
     * The strip the ripper lays, either side of the centreline, in blocks: the same lane the blade cuts. That is 4
     * blocks wide, so it covers 5 block columns unless the machine sits exactly on the grid.
     */
    public static final double PAVE_HALF_WIDTH = 2.0;

    /** How far the ripper beam moves (y, z source px) with the links swung down by {@code degrees}. */
    public static Vec3 ripperOffset(float degrees) {
        double a = degrees * Mth.DEG_TO_RAD;
        return new Vec3(0, -RIPPER_LINK * Math.sin(a), -(RIPPER_LINK - RIPPER_LINK * Math.cos(a)));
    }

    /** Barrel angle and pin-to-pin length of a ripper cylinder with the links at {@code degrees}. */
    public static double[] ripperCylinder(float degrees) {
        Vec3 end = RIPPER_END.add(ripperOffset(degrees));
        double dy = end.y - RIPPER_BASE.y, dz = end.z - RIPPER_BASE.z;
        return new double[]{Math.atan2(dz, dy), Math.hypot(dy, dz)};
    }
    /** Half-width of the cutting edge (blade 4 blocks wide) and the depth of material it bites per tick, in blocks. */
    public static final double CUT_HALF_WIDTH = 2.0, CUT_DEPTH = .7;
    private BulldozerRig() {}

    public static Vec3 forward(float yaw) {
        return Vec3.directionFromRotation(0, yaw);
    }

    /** A model point, in source pixels, to world space. */
    public static Vec3 world(VehicleEntity vehicle, double x, double y, double z) {
        return world(vehicle.position(), vehicle.getYRot(), x, y, z);
    }

    public static Vec3 world(Vec3 origin, float yaw, double x, double y, double z) {
        Vec3 f = forward(yaw);
        return origin.add(f.z * x / 16 - f.x * z / 16, y / 16, -f.x * x / 16 - f.z * z / 16);
    }

    static Vec3 rotateX(Vec3 point, Vec3 pivot, double degrees) {
        double a = degrees * Mth.DEG_TO_RAD, c = Math.cos(a), s = Math.sin(a);
        double y = point.y - pivot.y, z = point.z - pivot.z;
        return new Vec3(point.x, pivot.y + y * c - z * s, pivot.z + y * s + z * c);
    }

    /** A point fixed to the blade: the push arms carry it while the blade itself keeps its pitch. */
    public static Vec3 bladePoint(Vec3 point, float lift) {
        return point.subtract(BLADE_PIVOT).add(rotateX(BLADE_PIVOT, LIFT_PIVOT, lift));
    }

    public static Vec3 cuttingEdge(float lift) {
        return bladePoint(CUT_EDGE, lift);
    }

    /** World position of the middle of the cutting edge (its lowest, foremost line). */
    public static Vec3 cuttingCenter(VehicleEntity entity, float lift) {
        Vec3 edge = cuttingEdge(lift);
        return world(entity, edge.x, edge.y, edge.z);
    }

    /** Barrel angle (radians from the source +Y axis about X) and pin-to-pin length of a lift cylinder. */
    public static double[] liftCylinder(float lift) {
        Vec3 end = bladePoint(LIFT_END, lift);
        double dy = end.y - LIFT_BASE.y, dz = end.z - LIFT_BASE.z;
        return new double[]{Math.atan2(dz, dy), Math.hypot(dy, dz)};
    }

    public static Vec3 liftCylinderEnd(float lift) {
        return bladePoint(LIFT_END, lift);
    }

    /** The tilt strut lives in the push arm's frame, where the blade turns back by the lift angle. */
    public static double[] tiltStrut(float lift) {
        Vec3 end = rotateX(TILT_END, BLADE_PIVOT, -lift);
        double dy = end.y - TILT_BASE.y, dz = end.z - TILT_BASE.z;
        return new double[]{Math.atan2(dz, dy), Math.hypot(dy, dz)};
    }

    /** y, z and source X rotation along a closed tread loop, in source units. */
    public static double[] tread(double distance) {
        double s = (distance % TRACK_LENGTH + TRACK_LENGTH) % TRACK_LENGTH, arc = Math.PI * TRACK_RADIUS;
        if (s < 46) return new double[]{1.5, -23 + s, 0};
        if (s < 46 + arc) {
            double a = (s - 46) / TRACK_RADIUS;
            return new double[]{8 - TRACK_RADIUS * Math.cos(a), 23 + TRACK_RADIUS * Math.sin(a), -a};
        }
        if (s < 92 + arc) return new double[]{14.5, 23 - (s - 46 - arc), -Math.PI};
        double a = Math.PI + (s - 92 - arc) / TRACK_RADIUS;
        return new double[]{8 - TRACK_RADIUS * Math.cos(a), -23 + TRACK_RADIUS * Math.sin(a), -a};
    }

    /**
     * World-axis bounds of the blade's bite: the 4-block cutting edge swept {@link #CUT_DEPTH} ahead, from the
     * cutting edge up to {@code height} blocks above it.
     */
    public static AABB biteBounds(VehicleEntity entity, float lift, double height) {
        Vec3 center = cuttingCenter(entity, lift), f = forward(entity.getYRot());
        Vec3 ahead = center.add(f.scale(CUT_DEPTH));
        double sx = Math.abs(f.z) * CUT_HALF_WIDTH, sz = Math.abs(f.x) * CUT_HALF_WIDTH;
        return new AABB(Math.min(center.x, ahead.x) - sx, center.y, Math.min(center.z, ahead.z) - sz,
                Math.max(center.x, ahead.x) + sx, center.y + height, Math.max(center.z, ahead.z) + sz);
    }

    /** Separating-axis test of the rotated bite slab (behind the edge by a sliver) against a world-axis box. */
    public static boolean inBite(VehicleEntity entity, float lift, AABB box) {
        Vec3 center = cuttingCenter(entity, lift), f = forward(entity.getYRot());
        Vec3 mid = center.add(f.scale(CUT_DEPTH / 2 - .05));
        Vec3 d = box.getCenter().subtract(mid);
        double hx = box.getXsize() / 2, hz = box.getZsize() / 2;
        double along = CUT_DEPTH / 2 + .05, across = CUT_HALF_WIDTH - .05;
        return Math.abs(d.x * f.x + d.z * f.z) < along + Math.abs(f.x) * hx + Math.abs(f.z) * hz
                && Math.abs(d.x * f.z - d.z * f.x) < across + Math.abs(f.z) * hx + Math.abs(f.x) * hz;
    }
}
