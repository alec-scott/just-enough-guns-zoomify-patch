package ttv.migami.jeg.vehicle.data.subdata;

import java.util.List;

public record OBBInfo(List<Box> boxes) {
    public static final OBBInfo DEFAULT = new OBBInfo(List.of(new Box(Part.BODY, 0.0D, 0.75D, 0.0D, 0.9D, 0.75D, 1.2D)));

    public enum Part {
        BODY,
        TURRET,
        WHEEL_LEFT,
        WHEEL_RIGHT,
        MAIN_ENGINE,
        SUB_ENGINE,
        INTERACTIVE,
        /** A support vehicle's module bays: hits there can knock out fitted modules. */
        MODULE
    }

    /**
     * What a box moves with. Boxes are written at rest (turret yaw 0, barrel level) in hull coordinates; a box that
     * follows the turret turns with its yaw about the turret origin, and one that follows the barrel also elevates
     * about the barrel's pivot (VehicleEntity carries the test point into the box's rest frame).
     */
    public enum Follows {
        HULL,
        TURRET,
        BARREL
    }

    public record Box(Part part, double x, double y, double z, double halfWidth, double halfHeight, double halfDepth, Follows follows) {
        public Box(Part part, double x, double y, double z, double halfWidth, double halfHeight, double halfDepth) {
            this(part, x, y, z, halfWidth, halfHeight, halfDepth, Follows.HULL);
        }

        public boolean followsHull() {
            return this.follows == Follows.HULL;
        }
    }
}
