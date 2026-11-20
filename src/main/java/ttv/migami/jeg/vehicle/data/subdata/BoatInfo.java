package ttv.migami.jeg.vehicle.data.subdata;

/**
 * How a boat's hull behaves ("boat" in the vehicle data; fleet plan section 4 and Phase 0.5, which moved the speedboat's
 * constants out of VehicleEntity). Speeds are blocks a tick.
 *
 * @param collisionLengthScale  the entity box stretched along the hull for block collision (a boat is long and thin)
 * @param collisionForwardShift the stretched box moved forward this far (blocks)
 * @param bowProbeDistance      how far ahead of the bow blocks are looked for (blocks)
 * @param bowProbeHalfWidth     half the bow's width for that probe
 * @param bowProbeLowY          the probe's heights above the keel
 * @param bowProbeHighY
 * @param thrust               the engine's push each tick at full power, as a share of top speed
 * @param planeSpeed            above this the hull planes: it rides on top of the water, drag drops and the bow rises
 * @param displacementFriction  water drag (velocity kept per tick) below planing speed
 * @param planedFriction        water drag once planing
 * @param bowRiseDegrees        the bow's rise as the hull comes onto the plane (it settles to half at full speed)
 * @param keelGrip              the share of sideways slip the keel removes each tick (it carves through turns)
 * @param beachSpeed            running onto a shore at this speed or more grounds the bow on it instead of stopping dead
 * @param beachStep             the highest shore (blocks above the waterline) it will run up onto
 * @param draft                 how deep the keel sits below the water surface afloat (blocks); NaN floats it the old
 *                              way, its keel a little above the surface
 */
public record BoatInfo(float collisionLengthScale, double collisionForwardShift, double bowProbeDistance, double bowProbeHalfWidth,
                       double bowProbeLowY, double bowProbeHighY, double thrust, double planeSpeed, double displacementFriction,
                       double planedFriction, float bowRiseDegrees, double keelGrip, double beachSpeed, double beachStep,
                       double draft) {
    /** A hull that never planes or beaches: the vanilla-like default for boats without a "boat" block. */
    public static final BoatInfo DEFAULT = new BoatInfo(1.0F, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D,
            0.11D, Double.MAX_VALUE, -1.0D, -1.0D, 0.0F, 0.0D, Double.MAX_VALUE, 0.0D, Double.NaN);

    /** Whether the hull probes ahead of its bow for blocks (it stops, or beaches, instead of grinding into them). */
    public boolean probesBow() {
        return this.bowProbeDistance > 0.0D;
    }

    public boolean planes() {
        return this.planeSpeed < Double.MAX_VALUE;
    }

    public boolean beaches() {
        return this.beachStep > 0.0D;
    }
}
