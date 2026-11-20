package ttv.migami.jeg.vehicle.data.subdata;

/**
 * A land vehicle that swims ("amphibious" in the vehicle data; fleet plan sections 2 and 3: the BMP-2 and LAV-150).
 * Afloat, buoyancy holds the hull with its waterline at the surface, it makes way slowly (tracks or wheels churning, or
 * the LAV's propeller-less wheel drive) and steers as on land, throws a bow wave, and may not fire the weapons listed
 * as land-only.
 *
 * @param waterline   height of the waterline above the hull's bottom, in blocks (how deep it floats)
 * @param maxSpeed    top speed afloat, blocks a tick (the BMP-2 swims at 7 km/h, 0.097)
 * @param noFireSlots weapon slots that may not fire afloat (the BMP-2's ATGM: the launcher can't be laid from a pitching
 *                    hull), as indices into the vehicle's weapons
 */
public record AmphibiousInfo(double waterline, double maxSpeed, java.util.List<Integer> noFireSlots) {
    public static final AmphibiousInfo NONE = new AmphibiousInfo(Double.NaN, 0.0D, java.util.List.of());

    public boolean swims() {
        return !Double.isNaN(this.waterline);
    }
}
