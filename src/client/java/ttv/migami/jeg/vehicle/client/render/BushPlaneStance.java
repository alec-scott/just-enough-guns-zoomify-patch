package ttv.migami.jeg.vehicle.client.render;

import net.minecraft.util.Mth;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The bush plane is a taildragger: on the ground it sits on its tailwheel, nose up, as a Super Cub does (design/bushplane:
 * the model's tailwheel hangs {@code TAILWHEEL_LIFT} above the main wheels' contact with the fuselage level, as in flight).
 * The renderer pitches it so about its main wheels while it is slow on the ground; the flight model is unchanged.
 */
public final class BushPlaneStance {
    /** The ground angle on its tailwheel: atan(15 / 81) px, about a Super Cub's 11 degrees. */
    public static final float ANGLE = 10.5F;
    /** The main wheels' contact, in model blocks (z -24 px; the model faces -Z). */
    public static final float MAIN_GEAR_Z = -24.0F / 16.0F;

    private BushPlaneStance() {}

    /**
     * How far it sits nose up: the full ground angle at rest on its wheels, easing to level as the tail comes up on the
     * take-off roll (by 45% of the stall speed, as a Cub's does) and in flight.
     */
    public static float of(VehicleEntity vehicle) {
        if (!"tom6".equals(vehicle.vehicleDataId().getPath()) || !vehicle.onGround() || vehicle.isWrecked()) return 0.0F;
        double stall = vehicle.vehicleData().defaults().engine().jetStallSpeed();
        double speed = Math.abs(vehicle.clientForwardSpeed());
        return ANGLE * (float) Mth.clamp(1.0D - speed / Math.max(0.05D, 0.45D * stall), 0.0D, 1.0D);
    }
}
