package ttv.migami.jeg.vehicle.data.subdata;

import java.util.List;

/**
 * A wheeled vehicle's road wheels for the shared wheel driver ({@code "wheels"} in the vehicle data, written by the
 * model's refit or generator; guide section 5): bones that roll with the distance driven, and front wheels that steer.
 * Nothing about it is synced: every client rolls the wheels from how far the hull moves and steers them from how fast it
 * turns for its speed, as a real wheelbase does.
 *
 * @param roll      groups of bones and the radius they roll on, model pixels
 * @param steer     bones that turn about their own vertical axis with the steering
 * @param wheelbase from the front axle to the rear, blocks
 */
public record WheelInfo(List<TrackInfo.Wheel> roll, List<String> steer, float wheelbase) {
    /** The steered wheels' furthest turn, degrees. */
    public static final float MAX_STEER = 35.0F;
}
