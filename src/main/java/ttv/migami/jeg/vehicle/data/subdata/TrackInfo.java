package ttv.migami.jeg.vehicle.data.subdata;

import java.util.List;

/**
 * A tracked vehicle's running gear for the shared track driver ({@code "tracks"} in the vehicle data, written by the
 * model's refit or generator; guide section 5). The links of each side are bones {@code track_link_<side>_<i>} (moved
 * along the path) with a child {@code track_link_<side>_<i>_turn} (turned to the path's direction), all modelled at
 * {@code rest}; both sides share one path, the model being a mirror image.
 *
 * @param links     links per side, spaced evenly round the path
 * @param restZ     the links' modelled position, file pixels (Z, the model's front is -Z)
 * @param restY     the links' modelled position, file pixels (Y)
 * @param restS     arc length along the path at the rest position: link {@code i} sits at
 *                  {@code restS + travel + i * length / links}
 * @param halfWidth from the vehicle's centre line to each track's centre, blocks (turning on the spot runs them apart)
 * @param pathZ     the links' centre line, a closed polygon in file pixels, counter-clockwise seen from the left: the
 *                  way the links run relative to the hull as the vehicle drives forward
 * @param pathY     its Y coordinates
 * @param wheels    road wheels, sprockets, idlers and rollers: bones that turn as the track runs over them
 */
public record TrackInfo(int links, float restZ, float restY, float restS, float halfWidth, float[] pathZ, float[] pathY,
                        float[] cumulative, float length, List<Wheel> wheels) {
    /** Bones turned by the track running over a radius of {@code radius} pixels. */
    public record Wheel(List<String> bones, float radius) {}

    public static TrackInfo of(int links, float restZ, float restY, float restS, float halfWidth, float[] pathZ, float[] pathY, List<Wheel> wheels) {
        float[] cumulative = new float[pathZ.length + 1];
        for (int i = 0; i < pathZ.length; i++) {
            int j = (i + 1) % pathZ.length;
            cumulative[i + 1] = cumulative[i] + (float) Math.hypot(pathZ[j] - pathZ[i], pathY[j] - pathY[i]);
        }
        return new TrackInfo(links, restZ, restY, restS, halfWidth, pathZ, pathY, cumulative, cumulative[pathZ.length], wheels);
    }

    /**
     * The point on the path at arc length {@code s} (wrapped) and the X rotation in radians that turns a link lying on
     * the top run onto the path's direction there. Writes {z, y, turn} into {@code out}.
     */
    public void sample(float s, float[] out) {
        float wrapped = s % this.length;
        if (wrapped < 0.0F) {
            wrapped += this.length;
        }
        int lo = 0, hi = this.pathZ.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (this.cumulative[mid] <= wrapped) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        int j = (lo + 1) % this.pathZ.length;
        float segment = this.cumulative[lo + 1] - this.cumulative[lo];
        float t = segment <= 0.0F ? 0.0F : (wrapped - this.cumulative[lo]) / segment;
        float dz = this.pathZ[j] - this.pathZ[lo];
        float dy = this.pathY[j] - this.pathY[lo];
        out[0] = this.pathZ[lo] + dz * t;
        out[1] = this.pathY[lo] + dy * t;
        // A link on the top run lies along -Z; turning it by a about X carries that direction to (dz, dy).
        out[2] = (float) Math.atan2(dy, -dz);
    }
}
