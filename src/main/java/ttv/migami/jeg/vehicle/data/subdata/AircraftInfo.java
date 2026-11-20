package ttv.migami.jeg.vehicle.data.subdata;

import java.util.List;

/**
 * What sets one fixed-wing aircraft apart from the A-10 that JetFlight was written for ("aircraft" in the vehicle data;
 * the bush plane, design/bushplane/BRIEF.md).
 *
 * @param thrustLapse     the share of its thrust it loses by top speed (a turbofan's 0.12; a propeller's far more)
 * @param fixedGear       its wheels stay down (no retraction, no gear sound)
 * @param flapStallFactor flaps out, its stall and rotate speeds are this share of clean (1: no flaps)
 * @param flapSpeedFactor the flaps come out below this many times its clean stall speed (take-off and landing)
 * @param strikePoints    points on the wingtips, nose and tail checked against the terrain, vehicle data coordinates
 *                        (blocks, +Z forward) with a fourth value: 0 the nose or engine, 1 a wing, 2 the tail. Empty
 *                        uses the A-10's
 * @param wingCamera      where the reversed third-person view's action camera sits, on top of the left wing near its
 *                        tip (vehicle data coordinates, blocks); the A-10's spot floated off a smaller wing's tip
 */
public record AircraftInfo(double thrustLapse, boolean fixedGear, double flapStallFactor, double flapSpeedFactor,
                           List<double[]> strikePoints, double[] wingCamera) {
    public static final double[] A10_WING_CAMERA = {7.6D, 3.75D, -2.2D};
    public static final AircraftInfo DEFAULT = new AircraftInfo(0.12D, false, 1.0D, 0.0D, List.of(), A10_WING_CAMERA);

    public boolean hasFlaps() {
        return this.flapStallFactor < 1.0D && this.flapSpeedFactor > 0.0D;
    }
}
