package ttv.migami.jeg.vehicle.client.render;

import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Exterior and cockpit lights on the aircraft. All of it is client side and drawn from state the vehicles already sync: a
 * light is on, day and night, while the aircraft has power (someone at the controls or its engine turning), and goes dark
 * on a wreck. Only the beams wait for night.
 * <ul>
 *     <li>Position lights: red on the left (port) tip, green on the right, white at the tail. Steady.</li>
 *     <li>Anti-collision lights: red beacons that flash about once a second, and white strobes that double-flash (the
 *     A-10's and the Mi-28's wingtips). Each aircraft flashes on its own beat, as real ones are never in step.</li>
 *     <li>The Mi-28's formation lights, soft green strips along its tail boom and engines, and its rotor-tip lights,
 *     which blur into a ring as the rotor runs up.</li>
 *     <li>Cockpit lighting: the instrument glow that lights the panel and the frames round it (from the A-10's green
 *     displays, see {@link A10CockpitLayer}, and the Mi-28's amber ones, see {@link Mi28CockpitLayer}).</li>
 *     <li>A helicopter's searchlight under the nose, and the A-10's taxi light on its nose gear while the gear is down.
 *     These two come on only at night.</li>
 * </ul>
 * Lights cast a little of their colour onto the airframe round them.
 * <p>
 * Positions are in the baked model's render space, in pixels: GeckoLib negates the file's X, so -X is the aircraft's left
 * and +X its right, and the nose points to -Z.
 */
public final class AircraftLights {
    public enum Kind {
        POSITION, BEACON, STROBE, COCKPIT,
        /** A road vehicle's tail lamp: dim as a tail light, bright while braking. */
        BRAKE,
        /** A road vehicle's reversing lamp: on only while it reverses. */
        REVERSE,
        /**
         * A lamp inside a walk-in cabin (the CH-47's dome lights): on at night, on the battery, so it lights the cabin for
         * the people in it with the engines off.
         */
        CABIN,
        /** The red jump light over a ramp: flashes while the ramp travels (ChinookEntity#jumpLightRed). On the battery. */
        JUMP_RED,
        /** The green jump light over a ramp: steady while the ramp stands open. On the battery. */
        JUMP_GREEN
    }

    /** Lights that work on the battery: whenever the aircraft isn't a wreck, engines or no engines. */
    public static boolean onBattery(Kind kind) {
        return kind == Kind.CABIN || kind == Kind.JUMP_RED || kind == Kind.JUMP_GREEN;
    }

    /**
     * One light. {@code size} is the glow's width in blocks; it casts {@code spill} of its colour onto the airframe within
     * {@code spillRadius} blocks. {@code phase} offsets a flashing light within its cycle.
     */
    public record PointLight(Kind kind, float x, float y, float z, int colour, float size, float spillRadius,
                             float spill, float phase) {
    }

    /**
     * A light that throws a beam: tilted {@code pitchDown} degrees below the nose, spreading {@code halfAngle} degrees
     * either side, reaching {@code range} blocks. A taxi light shines only while the gear is down.
     */
    public record Searchlight(float x, float y, float z, float pitchDown, float halfAngle, float range, int colour,
                              boolean taxi, boolean lensGlow) {
        /**
         * {@code lensGlow}: the beam draws its own glow at its origin (one lamp: a helicopter's searchlight). Off for a
         * pair of headlamps, whose own glows show; a single glare in the middle of a tank's bow looked wrong.
         */
        public Searchlight(float x, float y, float z, float pitchDown, float halfAngle, float range, int colour, boolean taxi) {
            this(x, y, z, pitchDown, halfAngle, range, colour, taxi, true);
        }

        Vector3f modelPos() {
            return new Vector3f(this.x / 16.0F, this.y / 16.0F, this.z / 16.0F);
        }

        Vector3f modelDir() {
            float pitch = this.pitchDown * Mth.DEG_TO_RAD;
            return new Vector3f(0.0F, -Mth.sin(pitch), -Mth.cos(pitch));
        }
    }

    /**
     * A formation light: a flat strip of soft light on the skin, from {@code z0} to {@code z1} along the airframe and
     * {@code height} tall, on the side its {@code x} is (facing outwards).
     */
    public record Strip(float x, float y, float z0, float z1, float height, int colour, boolean nightOnly) {
        public Strip(float x, float y, float z0, float z1, float height, int colour) {
            this(x, y, z0, z1, height, colour, false);
        }
    }

    /**
     * Lights at the rotor's blade tips: the tip of each blade bone at {@code tip}, about the hub's axis through
     * {@code hub} (render-space pixels in the blade bones' frame).
     */
    public record RotorTips(List<String> blades, Vector3f tip, Vector3f hub, int colour) {
    }

    /**
     * {@code openSeat}: the occupant sits in the open among the lamps (an open ROPS canopy, not a cockpit). Seen from
     * the seat, the lamps' backs and the searchlight lens give no glare and spill would light the canopy at arm's
     * length, so the occupant view draws only the beacon (faintly) and the beam's pool on the ground.
     */
    public record LightSet(List<PointLight> lights, Searchlight searchlight, List<Strip> strips, RotorTips rotorTips, boolean openSeat) {
        LightSet(List<PointLight> lights, Searchlight searchlight, List<Strip> strips, RotorTips rotorTips) {
            this(lights, searchlight, strips, rotorTips, false);
        }

        LightSet(List<PointLight> lights, Searchlight searchlight) {
            this(lights, searchlight, List.of(), null, false);
        }

        /** Some of its lights work with the engines off ({@link #onBattery}). */
        public boolean hasBatteryLights() {
            for (PointLight light : this.lights) {
                if (onBattery(light.kind())) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final int RED = 0xFF2A1A;
    private static final int GREEN = 0x2AFF6A;
    private static final int WHITE = 0xFFF4E6;
    private static final int STROBE_WHITE = 0xEEF4FF;
    private static final int SEARCH_WHITE = 0xFFF2DC;
    private static final int FORMATION_GREEN = 0x8CFFB0;

    /** Construction work lamps share the existing beam, spill and optional dynamic-ground-light implementation. */
    /** Work lamps on the radiator guard and canopy, tail lamps, the amber beacon; the beam from the guard lamps. */
    private static final LightSet BULLDOZER = new LightSet(List.of(
            new PointLight(Kind.POSITION, -7.6F, 26.4F, -35.4F, SEARCH_WHITE, .16F, 1, .14F, 0),
            new PointLight(Kind.POSITION, 7.6F, 26.4F, -35.4F, SEARCH_WHITE, .16F, 1, .14F, 0),
            new PointLight(Kind.POSITION, -8.65F, 44.9F, -6.4F, SEARCH_WHITE, .17F, 1, .15F, 0),
            new PointLight(Kind.POSITION, 8.65F, 44.9F, -6.4F, SEARCH_WHITE, .17F, 1, .15F, 0),
            new PointLight(Kind.POSITION, -8.65F, 44.9F, 20.4F, SEARCH_WHITE, .14F, .9F, .12F, 0),
            new PointLight(Kind.POSITION, 8.65F, 44.9F, 20.4F, SEARCH_WHITE, .14F, .9F, .12F, 0),
            new PointLight(Kind.POSITION, -9.3F, 25.5F, 29F, RED, .1F, .7F, .1F, 0),
            new PointLight(Kind.POSITION, 9.3F, 25.5F, 29F, RED, .1F, .7F, .1F, 0),
            new PointLight(Kind.BEACON, 7.8F, 49.3F, 14.8F, 0xFFAC35, .45F, 1.3F, .3F, 0)
    // A pair of headlamps: no lens glare of its own between them (a white ball floating mid-grille, the user's report,
    // 2026-11-19), as every other road vehicle's.
    ), new Searchlight(0, 26.4F, -35.6F, 16, 26, 30, SEARCH_WHITE, false, false), List.of(), null, true);

    /**
     * The FSV-6: headlamps (the beam), tail lamps, cab marker lamps and the two amber beacons on the cab roof. Render
     * space negates the model's X. The room, awning and mast light their surroundings with real light while deployed
     * (the walk-in shell's lit cells) and glow through the glow texture.
     */
    private static final LightSet TRUCK = new LightSet(List.of(
            new PointLight(Kind.POSITION, -16F, 22F, -64.9F, SEARCH_WHITE, .2F, 1.2F, .16F, 0),
            new PointLight(Kind.POSITION, 16F, 22F, -64.9F, SEARCH_WHITE, .2F, 1.2F, .16F, 0),
            new PointLight(Kind.BRAKE, -15.3F, 7.5F, 63.3F, RED, .24F, 1.4F, .3F, 0),
            new PointLight(Kind.BRAKE, 15.3F, 7.5F, 63.3F, RED, .24F, 1.4F, .3F, 0),
            new PointLight(Kind.BRAKE, 0F, 47.1F, 60.7F, RED, .18F, 1.0F, .2F, 0),
            new PointLight(Kind.REVERSE, -10.9F, 7.5F, 63.3F, WHITE, .24F, 1.6F, .3F, 0),
            new PointLight(Kind.REVERSE, 10.9F, 7.5F, 63.3F, WHITE, .24F, 1.6F, .3F, 0),
            new PointLight(Kind.POSITION, -16.5F, 49.6F, -61.5F, 0xFFAC35, .06F, .4F, .05F, 0),
            new PointLight(Kind.POSITION, 16.5F, 49.6F, -61.5F, 0xFFAC35, .06F, .4F, .05F, 0),
            new PointLight(Kind.BEACON, -12F, 52F, -40F, 0xFFAC35, .4F, 1.3F, .3F, 0),
            new PointLight(Kind.BEACON, 12F, 52F, -40F, 0xFFAC35, .4F, 1.3F, .3F, .5F)
    // A pair of headlamps: no lens glare of its own in the middle of the grille (a white blob from the front and a sliver
    // from the side at night, 2026-11-17), as the armoured vehicles' headlamps.
    ), new Searchlight(0, 22F, -65F, 8, 22, 34, SEARCH_WHITE, false, false), List.of(), null, false);

    private static final int WARM = 0xFFC27A;

    /**
     * The FSV-6's room, lit only by its one fitted lamp (none fitted, the room is dark): a soft warm glow on the lamp and a
     * warm wash over the walls, bunks and bays round it (spill, wrapped like cockpit light). The shell's lit cells give
     * the matching block light. Render space negates the model's X: the lamp hook is at model x 0, z 16, the middle of
     * the room ({@code SupportVehicleRenderer.LAMP_HOOK}); the glow sits on the lantern's body, drawn at 0.65 scale.
     */
    /*
     * Softened on 2026-11-17 (the user wanted the lantern more ambient, with a softer exposure): the glow on the lantern
     * from .3 to .18 and the wash from .38/.16/.12 to .2/.08/.06, spread a little wider so it fills rather than pools.
     */
    private static final List<PointLight> TRUCK_ROOM_LAMP = List.of(
            new PointLight(Kind.POSITION, 0F, 41F, 16F, WARM, .18F, 0F, 0F, 0),
            new PointLight(Kind.COCKPIT, 0F, 40F, 16F, WARM, 0F, 3.6F, .2F, 0),
            new PointLight(Kind.COCKPIT, -16F, 34F, 16F, WARM, 0F, 1.8F, .08F, 0),
            new PointLight(Kind.COCKPIT, 16F, 34F, 16F, WARM, 0F, 1.8F, .06F, 0));
    /** Outside: the awning's light strip, on whenever the generator is. */
    private static final List<PointLight> TRUCK_AWNING = List.of(
            new PointLight(Kind.POSITION, -43F, 44.8F, 16F, WARM, .28F, 1.8F, .2F, 0));
    /** The mast's two floodlights once the mast is fully up (lenses at the head's front face). */
    private static final List<PointLight> TRUCK_MAST = List.of(
            new PointLight(Kind.POSITION, -11F, 103.9F, -21.8F, SEARCH_WHITE, .3F, 1.2F, .15F, 0),
            new PointLight(Kind.POSITION, -17F, 103.9F, -21.8F, SEARCH_WHITE, .3F, 1.2F, .15F, 0));

    /** The FSV-6 on its generator: deploying or packing (awning), deployed (and the mast), each with or without a lamp. */
    private static final LightSet TRUCK_DEPLOYING = truckSet(false, false), TRUCK_DEPLOYING_LAMP = truckSet(true, false),
            TRUCK_MAST_UP = truckSet(false, true), TRUCK_MAST_UP_LAMP = truckSet(true, true);

    /**
     * Driving (or parked off its generator) with a lamp fitted: the road lights and the lamp, so the room is still lit by
     * its lantern on the move. The room's block light only exists while deployed, so on the move the lamp's drawn light
     * is all there is.
     */
    private static final LightSet TRUCK_LAMP = new LightSet(
            java.util.stream.Stream.concat(TRUCK.lights().stream(), TRUCK_ROOM_LAMP.stream()).toList(),
            TRUCK.searchlight(), List.of(), null, false);

    private static LightSet truckSet(boolean lamp, boolean mast) {
        List<PointLight> lights = new java.util.ArrayList<>(TRUCK_AWNING);
        if (lamp) lights.addAll(TRUCK_ROOM_LAMP);
        if (mast) lights.addAll(TRUCK_MAST);
        return new LightSet(List.copyOf(lights), null, List.of(), null, false);
    }

    /**
     * The armoured vehicles (fleet plan Phase 0.8): headlamps (the beam at night), tail lamps that brighten as they brake
     * and reversing lamps where the hull has them, all on while the engine runs. Positions from the models' lamp bones
     * (design/<vehicle>/rig.json); render space negates the model's X.
     */
    private static final LightSet BMP2 = new LightSet(List.of(
            new PointLight(Kind.POSITION, -20.6F, 22.8F, -57.3F, SEARCH_WHITE, .16F, 1.1F, .14F, 0),
            new PointLight(Kind.POSITION, 20.6F, 22.8F, -57.3F, SEARCH_WHITE, .16F, 1.1F, .14F, 0),
            new PointLight(Kind.BRAKE, -18F, 21F, 51.4F, RED, .14F, 1.0F, .2F, 0),
            new PointLight(Kind.BRAKE, 18F, 21F, 51.4F, RED, .14F, 1.0F, .2F, 0)
    ), new Searchlight(0, 22.8F, -57.4F, 8, 24, 30, SEARCH_WHITE, false, false), List.of(), null, false);
    private static final LightSet PLZ05 = new LightSet(List.of(
            new PointLight(Kind.POSITION, -22F, 20.2F, -65F, SEARCH_WHITE, .18F, 1.2F, .15F, 0),
            new PointLight(Kind.POSITION, 22F, 20.2F, -65F, SEARCH_WHITE, .18F, 1.2F, .15F, 0),
            new PointLight(Kind.BRAKE, -23.4F, 22.1F, 44.1F, RED, .14F, 1.0F, .2F, 0),
            new PointLight(Kind.BRAKE, 23.4F, 22.1F, 44.1F, RED, .14F, 1.0F, .2F, 0)
    ), new Searchlight(0, 20.2F, -65.1F, 8, 24, 32, SEARCH_WHITE, false, false), List.of(), null, false);
    /** The YX-100: SBW's amber marker lamps at the front corners of the hull and the lamps at the rear plate's corners. */
    private static final LightSet YX100 = new LightSet(List.of(
            new PointLight(Kind.POSITION, -25.9F, 16.2F, -58.8F, SEARCH_WHITE, .16F, 1.1F, .14F, 0),
            new PointLight(Kind.POSITION, 25.9F, 16.2F, -58.8F, SEARCH_WHITE, .16F, 1.1F, .14F, 0),
            new PointLight(Kind.BRAKE, -26.4F, 18.0F, 52.0F, RED, .14F, 1.0F, .2F, 0),
            new PointLight(Kind.BRAKE, 26.4F, 18.0F, 52.0F, RED, .14F, 1.0F, .2F, 0)
    ), new Searchlight(0, 16.2F, -59.0F, 8, 24, 32, SEARCH_WHITE, false, false), List.of(), null, false);
    /**
     * The speedboat's navigation lights (design/speedboat): red to port and green to starboard at the cabin's front
     * corners, white on the masthead and at the stern, and the searchlight on the cabin roof.
     */
    private static final LightSet SPEEDBOAT = new LightSet(List.of(
            new PointLight(Kind.POSITION, 21.6F, 30.0F, -19.0F, RED, .14F, .9F, .14F, 0),
            new PointLight(Kind.POSITION, -21.6F, 30.0F, -19.0F, GREEN, .14F, .9F, .14F, 0),
            new PointLight(Kind.POSITION, 0.0F, 53.2F, 26.0F, WHITE, .14F, 1.0F, .12F, 0),
            new PointLight(Kind.POSITION, 0.0F, 15.2F, 55.0F, WHITE, .12F, .8F, .12F, 0)
    ), new Searchlight(0, 37.5F, -19.5F, 6, 18, 40, SEARCH_WHITE, false, false), List.of(), null, false);
    /**
     * The bush plane (design/bushplane): red and green navigation lights on the wingtips, the white tail light, the red
     * beacon on the fin, and the landing light in the left wing's leading edge.
     */
    private static final LightSet TOM6 = new LightSet(List.of(
            new PointLight(Kind.POSITION, -84.0F, 31.2F, -23.5F, RED, 0.4F, 1.4F, 0.45F, 0.0F),
            new PointLight(Kind.POSITION, 84.0F, 31.2F, -23.5F, GREEN, 0.4F, 1.4F, 0.45F, 0.0F),
            new PointLight(Kind.POSITION, 0.0F, 24.7F, 64.0F, WHITE, 0.32F, 1.0F, 0.35F, 0.0F),
            new PointLight(Kind.BEACON, 0.0F, 37.8F, 59.2F, RED, 0.7F, 1.8F, 0.6F, 0.0F)
    // The landing light in the wing: no lens glare or drawn shaft of its own (a white ball ahead and a stick off the wing
    // at night, the user's review, 2026-11-19); its light on the ground and the wing shows it.
    ), new Searchlight(-36.0F, 32.6F, -27.5F, 6.0F, 12.0F, 48.0F, SEARCH_WHITE, true, false));
    /** The LAV-150: the caged lamp boxes on the glacis and the small tail lamps at the rear corners (design/lav150). */
    private static final LightSet LAV150 = new LightSet(List.of(
            new PointLight(Kind.POSITION, -10.5F, 30.7F, -40.4F, SEARCH_WHITE, .16F, 1.1F, .14F, 0),
            new PointLight(Kind.POSITION, 10.5F, 30.7F, -40.4F, SEARCH_WHITE, .16F, 1.1F, .14F, 0),
            new PointLight(Kind.BRAKE, -16.2F, 23.1F, 45.0F, RED, .14F, 1.0F, .2F, 0),
            new PointLight(Kind.BRAKE, 16.2F, 23.1F, 45.0F, RED, .14F, 1.0F, .2F, 0)
    ), new Searchlight(0, 30.7F, -40.5F, 8, 24, 30, SEARCH_WHITE, false, false), List.of(), null, false);

    private static final LightSet A10 = new LightSet(List.of(
            new PointLight(Kind.POSITION, -142.7F, 39.5F, -8.0F, RED, 0.5F, 1.8F, 0.55F, 0.0F),
            new PointLight(Kind.POSITION, 142.7F, 39.5F, -8.0F, GREEN, 0.5F, 1.8F, 0.55F, 0.0F),
            new PointLight(Kind.POSITION, 0.0F, 41.0F, 125.0F, WHITE, 0.42F, 1.3F, 0.4F, 0.0F),
            new PointLight(Kind.STROBE, -142.0F, 41.8F, 6.0F, STROBE_WHITE, 1.5F, 3.2F, 0.85F, 0.0F),
            new PointLight(Kind.STROBE, 142.0F, 41.8F, 6.0F, STROBE_WHITE, 1.5F, 3.2F, 0.85F, 0.0F),
            new PointLight(Kind.BEACON, -50.0F, 76.6F, 117.0F, RED, 0.85F, 2.2F, 0.7F, 0.0F),
            new PointLight(Kind.BEACON, 50.0F, 76.6F, 117.0F, RED, 0.85F, 2.2F, 0.7F, 0.5F),
            // The glow of the two green displays (see A10CockpitLayer) and a little of it round the cockpit.
            new PointLight(Kind.COCKPIT, -7.3F, 49.4F, -74.4F, 0x5CFF84, 0.0F, 1.0F, 0.24F, 0.0F),
            new PointLight(Kind.COCKPIT, 7.3F, 49.4F, -74.4F, 0x5CFF84, 0.0F, 1.0F, 0.24F, 0.0F),
            new PointLight(Kind.COCKPIT, 0.0F, 56.0F, -66.0F, 0x5CFF84, 0.0F, 1.3F, 0.14F, 0.0F)
    ), new Searchlight(4.5F, 15.5F, -77.0F, 4.0F, 7.0F, 56.0F, SEARCH_WHITE, true));

    private static final LightSet AH6 = new LightSet(List.of(
            new PointLight(Kind.POSITION, -13.7F, 57.0F, 104.0F, RED, 0.4F, 1.3F, 0.5F, 0.0F),
            new PointLight(Kind.POSITION, 17.6F, 57.0F, 104.0F, GREEN, 0.4F, 1.3F, 0.5F, 0.0F),
            new PointLight(Kind.POSITION, -2.5F, 35.6F, 100.6F, WHITE, 0.32F, 1.0F, 0.35F, 0.0F),
            new PointLight(Kind.BEACON, 2.0F, 58.8F, 106.0F, RED, 0.75F, 1.9F, 0.7F, 0.0F),
            new PointLight(Kind.BEACON, 0.0F, 13.0F, 4.0F, RED, 0.7F, 1.8F, 0.6F, 0.5F),
            new PointLight(Kind.COCKPIT, 0.0F, 32.0F, -30.0F, 0x78FF96, 0.0F, 1.0F, 0.3F, 0.0F)
    ), new Searchlight(0.0F, 14.5F, -36.0F, 18.0F, 9.0F, 80.0F, SEARCH_WHITE, false));

    private static final LightSet MI28 = new LightSet(List.of(
            new PointLight(Kind.POSITION, -54.8F, 38.2F, -15.0F, RED, 0.45F, 1.5F, 0.6F, 0.0F),
            new PointLight(Kind.POSITION, 54.8F, 38.2F, -15.0F, GREEN, 0.45F, 1.5F, 0.6F, 0.0F),
            new PointLight(Kind.POSITION, 0.0F, 42.0F, 201.5F, WHITE, 0.38F, 1.2F, 0.35F, 0.0F),
            new PointLight(Kind.BEACON, 0.0F, 80.2F, 203.0F, RED, 0.85F, 2.2F, 0.7F, 0.0F),
            new PointLight(Kind.BEACON, 0.0F, 20.0F, 25.0F, RED, 0.8F, 2.0F, 0.6F, 0.5F),
            new PointLight(Kind.BEACON, 0.0F, 64.0F, 32.0F, RED, 0.8F, 2.0F, 0.6F, 0.25F),
            // White strobes at the back of the wingtip pods.
            new PointLight(Kind.STROBE, -54.6F, 38.2F, 19.8F, STROBE_WHITE, 1.2F, 2.6F, 0.75F, 0.0F),
            new PointLight(Kind.STROBE, 54.6F, 38.2F, 19.8F, STROBE_WHITE, 1.2F, 2.6F, 0.75F, 0.0F),
            // The pilot's amber displays (see Mi28CockpitLayer), and the gunner's cockpit ahead.
            new PointLight(Kind.COCKPIT, -4.5F, 50.3F, -49.0F, 0xFFB050, 0.0F, 0.9F, 0.24F, 0.0F),
            new PointLight(Kind.COCKPIT, 4.5F, 50.3F, -49.0F, 0xFFB050, 0.0F, 0.9F, 0.24F, 0.0F),
            new PointLight(Kind.COCKPIT, 0.0F, 56.0F, -42.0F, 0x64EBD7, 0.0F, 1.2F, 0.12F, 0.0F),
            new PointLight(Kind.COCKPIT, 0.0F, 48.0F, -72.0F, 0x64EBD7, 0.0F, 1.0F, 0.26F, 0.0F)
    ), new Searchlight(0.0F, 18.0F, -52.0F, 18.0F, 9.0F, 80.0F, SEARCH_WHITE, false), List.of(
            // Formation lights along both sides of the tail boom and on the engine nacelles.
            new Strip(-7.2F, 37.0F, 96.0F, 112.0F, 0.9F, FORMATION_GREEN),
            new Strip(7.2F, 37.0F, 96.0F, 112.0F, 0.9F, FORMATION_GREEN),
            new Strip(-31.0F, 44.0F, 30.0F, 44.0F, 0.9F, FORMATION_GREEN),
            new Strip(31.0F, 44.0F, 30.0F, 44.0F, 0.9F, FORMATION_GREEN)
    ), new RotorTips(List.of("wing", "wing2", "wing3", "wing4", "wing5"),
            new Vector3f(0.0F, 71.5F, 159.0F), new Vector3f(0.0F, 71.5F, -3.8F), 0xFFE6BE));

    /**
     * The CH-47 (design/chinook/generate.py, render space = the authored model grown: +X its right, nose to -Z). Outside:
     * red and green position lights on the sponsons, the white tail light, red anti-collision beacons on the aft pylon
     * and the belly, night formation strips along the fuselage and both pylons, the landing lights under the nose (the
     * beam, at night) and the cockpit's instrument glow. Inside: the three dome lights at night, and the jump lights
     * over the ramp, red flashing while it travels and green while it stands open (the user, 2026-11-21: "add
     * appropriate lighting inside and outside of the heli"). The lenses are dark glass in the texture; these light them.
     */
    private static final int CABIN_WHITE = 0xFFE4C4;
    private static final LightSet CH47 = new LightSet(List.of(
            new PointLight(Kind.POSITION, -36.2F, 22.5F, -54.0F, RED, 0.4F, 1.4F, 0.45F, 0.0F),
            new PointLight(Kind.POSITION, 36.2F, 22.5F, -54.0F, GREEN, 0.4F, 1.4F, 0.45F, 0.0F),
            new PointLight(Kind.POSITION, 0.0F, 89.0F, 115.9F, WHITE, 0.34F, 1.1F, 0.35F, 0.0F),
            new PointLight(Kind.BEACON, 0.0F, 105.4F, 99.5F, RED, 0.85F, 2.2F, 0.7F, 0.0F),
            new PointLight(Kind.BEACON, 0.0F, 8.2F, 21.5F, RED, 0.8F, 2.0F, 0.6F, 0.5F),
            new PointLight(Kind.COCKPIT, 0.0F, 33.0F, -112.0F, 0x78FF96, 0.0F, 1.2F, 0.24F, 0.0F),
            new PointLight(Kind.CABIN, 0.0F, 62.5F, -56.5F, CABIN_WHITE, 0.16F, 3.6F, 0.36F, 0.0F),
            new PointLight(Kind.CABIN, 0.0F, 62.5F, -7.5F, CABIN_WHITE, 0.16F, 3.6F, 0.36F, 0.0F),
            new PointLight(Kind.CABIN, 0.0F, 62.5F, 41.5F, CABIN_WHITE, 0.16F, 3.6F, 0.36F, 0.0F),
            new PointLight(Kind.JUMP_RED, -9.6F, 62.0F, 64.8F, RED, 0.24F, 2.2F, 0.32F, 0.0F),
            new PointLight(Kind.JUMP_GREEN, 9.6F, 62.0F, 64.8F, GREEN, 0.24F, 2.2F, 0.3F, 0.0F)
    // The two landing lights: one beam between them and no lens glare of its own (a pair, as the road vehicles' headlamps).
    ), new Searchlight(0.0F, 8.6F, -102.0F, 16.0F, 14.0F, 70.0F, SEARCH_WHITE, false, false), List.of(
            new Strip(-27.12F, 45.0F, -60.0F, -20.0F, 1.0F, FORMATION_GREEN, true),
            new Strip(27.12F, 45.0F, -60.0F, -20.0F, 1.0F, FORMATION_GREEN, true),
            new Strip(-22.02F, 82.5F, 86.0F, 96.0F, 1.0F, FORMATION_GREEN, true),
            new Strip(22.02F, 82.5F, 86.0F, 96.0F, 1.0F, FORMATION_GREEN, true),
            new Strip(-17.72F, 70.5F, -104.0F, -88.0F, 1.0F, FORMATION_GREEN, true),
            new Strip(17.72F, 70.5F, -104.0F, -88.0F, 1.0F, FORMATION_GREEN, true)
    ), null);

    /** Sky light above which the night lights switch off, and below which they switch on (15 by day, 4 at night). */
    private static final float NIGHT_OFF_SKY_LIGHT = 11.5F;
    private static final float NIGHT_ON_SKY_LIGHT = 10.5F;
    /** Ticks a night light takes to come up to full brightness once switched on. */
    private static final float SWITCH_TICKS = 6.0F;

    private static boolean nightOn;
    private static float nightRamp;
    private static float nightRampO;
    private static final Map<Integer, Spot> SPOTS = new HashMap<>();
    /** Each vehicle's searchlight spot as last smoothed. */
    private static final Map<Integer, Spot> SMOOTHED = new HashMap<>();

    /** Where a searchlight's beam lands this tick: a light {@code luminance} strong (0..15) at {@code pos}. */
    public record Spot(Vec3 pos, float radius, float luminance) {
    }

    private AircraftLights() {
    }

    public static LightSet forVehicle(VehicleEntity vehicle) {
        return switch (vehicle.vehicleDataId().getPath()) {
            case "bulldozer" -> BULLDOZER;
            case "truck" -> {
                if (!(vehicle instanceof ttv.migami.jeg.vehicle.entity.SupportVehicleEntity support)) yield TRUCK;
                boolean lamp = !support.fittedForDisplay(ttv.migami.jeg.vehicle.support.SupportModules.LAMP).isEmpty();
                if (!support.generatorRunning()) yield lamp ? TRUCK_LAMP : TRUCK;
                yield support.isDeployed() ? (lamp ? TRUCK_MAST_UP_LAMP : TRUCK_MAST_UP) : (lamp ? TRUCK_DEPLOYING_LAMP : TRUCK_DEPLOYING);
            }
            case "bmp2" -> BMP2;
            case "plz05" -> PLZ05;
            case "yx100" -> YX100;
            case "speedboat" -> SPEEDBOAT;
            case "tom6" -> TOM6;
            case "lav150" -> LAV150;
            case "a10" -> A10;
            case "ah6" -> AH6;
            case "mi28" -> MI28;
            case "ch47" -> CH47;
            default -> null;
        };
    }

    /** The aircraft has power: someone at the controls or the engine still turning. A wreck is dark. */
    public static boolean isPowered(VehicleEntity vehicle) {
        if (vehicle instanceof ttv.migami.jeg.vehicle.entity.BulldozerEntity dozer) return dozer.powered();
        if (vehicle instanceof ttv.migami.jeg.vehicle.entity.SupportVehicleEntity truck) return truck.engineRunning() || truck.generatorRunning();
        if (vehicle.isWrecked()) {
            return false;
        }
        if (vehicle.groundSounds() != null) {
            return vehicle.groundEngineRunning();
        }
        if (vehicle.getControllingPassenger() != null) {
            return true;
        }
        if (vehicle.isJet()) {
            return vehicle.jetSpool() > 0.02F;
        }
        return vehicle.propellerSpeed() > 0.01F || vehicle.helicopterStartupProgress() > 0.0F;
    }

    /** The beams' switch at dusk, 0 off to 1 on, easing up over a few ticks when it is thrown. */
    /** The CH-47's dome lamps: where they are in its model (blocks) and how far each lights the cabin (blocks). */
    public static float[][] ch47CabinLamps() {
        java.util.List<float[]> lamps = new java.util.ArrayList<>();
        for (PointLight light : CH47.lights()) {
            if (light.kind() == Kind.CABIN) {
                lamps.add(new float[]{light.x() / 16.0F, light.y() / 16.0F, light.z() / 16.0F, light.spillRadius() * 1.6F});
            }
        }
        return lamps.toArray(new float[0][]);
    }

    public static float nightSwitch(float partialTick) {
        return Mth.lerp(partialTick, nightRampO, nightRamp);
    }

    /** The searchlight's (or taxi light's) brightness, 0..1: on at night, and for a taxi light only with the gear down. */
    public static float searchlightLevel(VehicleEntity vehicle, Searchlight light, float partialTick) {
        float level = nightSwitch(partialTick);
        if (level <= 0.0F || !isPowered(vehicle)) {
            return 0.0F;
        }
        if (light.taxi()) {
            level *= Mth.clamp((vehicle.jetGear(partialTick) - 0.9F) / 0.1F, 0.0F, 1.0F);
        }
        return level;
    }

    /** The local player is in this aircraft and looking out of it: their lights must not get in the way. */
    public static boolean isOccupantView(VehicleEntity vehicle) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && minecraft.player.getVehicle() == vehicle
                && !minecraft.gameRenderer.getMainCamera().isDetached();
    }

    /**
     * Brightness of a light this instant, 0..1. A beacon flashes about once every 1.2 seconds with a short tail, as a
     * rotating or LED beacon does; a strobe double-flashes every 1.4 seconds. {@code seed} puts each aircraft on its own
     * beat.
     */
    /** Brake and reversing lamps follow what the vehicle is doing; other lights flash on their own beat. */
    public static float flash(VehicleEntity vehicle, PointLight light, float seconds, float seed) {
        if (vehicle instanceof ttv.migami.jeg.vehicle.entity.SupportVehicleEntity truck) {
            if (light.kind() == Kind.BRAKE) return truck.braking() ? 1.0F : 0.28F;
            if (light.kind() == Kind.REVERSE) return truck.reversing() ? 1.0F : 0.0F;
        } else if (light.kind() == Kind.BRAKE) {
            return vehicle.clientBraking() || vehicle.clientReversing() ? 1.0F : 0.28F;
        } else if (light.kind() == Kind.REVERSE) {
            return vehicle.clientReversing() ? 1.0F : 0.0F;
        }
        if (vehicle instanceof ttv.migami.jeg.vehicle.entity.ChinookEntity chinook) {
            if (light.kind() == Kind.JUMP_RED) return chinook.jumpLightRed() ? 1.0F : 0.0F;
            if (light.kind() == Kind.JUMP_GREEN) return chinook.jumpLightGreen() ? 1.0F : 0.0F;
        }
        if (light.kind() == Kind.CABIN) {
            return nightRamp;
        }
        return flash(light, seconds, seed);
    }

    public static float flash(PointLight light, float seconds, float seed) {
        return switch (light.kind()) {
            case BEACON -> {
                float p = frac(seconds / 1.2F + light.phase() + seed);
                yield p < 0.03F ? p / 0.03F : (float) Math.exp(-(p - 0.03F) * 22.0F);
            }
            case STROBE -> {
                float p = frac(seconds / 1.4F + light.phase() + seed);
                yield spike(p, 0.0F, 0.028F) + spike(p, 0.095F, 0.028F);
            }
            default -> 1.0F;
        };
    }

    private static float spike(float p, float start, float width) {
        float x = (p - start) / width;
        return x < 0.0F || x > 1.0F ? 0.0F : (1.0F - x) * (1.0F - x);
    }

    private static float frac(float v) {
        return v - Mth.floor(v);
    }

    /** A beat of its own for each aircraft, 0..1. */
    public static float seed(Entity entity) {
        return frac(entity.getId() * 0.618034F);
    }

    /** The sky's light level now (15 by day, 4 at night); a dimension without a sky counts as night. */
    private static float skyLight(Level level) {
        if (!level.dimensionType().hasSkyLight()) {
            return 0.0F;
        }
        Float sky = level.environmentAttributes().getDimensionValue(EnvironmentAttributes.SKY_LIGHT_LEVEL);
        return sky == null ? 15.0F : sky;
    }

    public static void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        nightRampO = nightRamp;
        if (level == null) {
            nightOn = false;
            nightRamp = nightRampO = 0.0F;
            SPOTS.clear();
            SMOOTHED.clear();
            return;
        }
        if (level.getGameTime() % 200 == 0) {
            SMOOTHED.keySet().removeIf(id -> level.getEntity(id) == null);
        }
        float sky = skyLight(level);
        if (nightOn ? sky > NIGHT_OFF_SKY_LIGHT : sky < NIGHT_ON_SKY_LIGHT) {
            nightOn = !nightOn;
        }
        float step = 1.0F / SWITCH_TICKS;
        nightRamp = nightOn ? Math.min(1.0F, nightRamp + step) : Math.max(0.0F, nightRamp - step);
        updateSpots(level);
    }

    /** Where each lit searchlight lands this tick: lights the ground through LambDynamicLights, and keeps a helicopter
     * whose beam is on screen from being culled when the helicopter itself is not. */
    private static void updateSpots(ClientLevel level) {
        SPOTS.clear();
        // A burning flare is a real light, by day as by night: it lights the airframe it left, the ground under it and
        // the smoke round it, flickering as it burns (the user's review, 2026-11-19).
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof ttv.migami.jeg.vehicle.projectile.VehicleDecoyEntity flare && !flare.isSmokeDecoy()) {
                float burn = flare.flareBurn(1.0F);
                if (burn > 0.05F) {
                    float flicker = 0.85F + 0.15F * Mth.sin(flare.tickCount * 2.3F + flare.getId());
                    SPOTS.put(-1 - flare.getId(), new Spot(flare.position().add(0.0D, 0.2D, 0.0D), 1.0F, 15.0F * burn * flicker));
                }
            }
        }
        if (nightRamp <= 0.0F) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof VehicleEntity vehicle)) {
                continue;
            }
            LightSet set = forVehicle(vehicle);
            if (set == null) {
                continue;
            }
            addLampSpots(level, vehicle, set);
            if (set.searchlight() == null) {
                continue;
            }
            Searchlight light = set.searchlight();
            float on = searchlightLevel(vehicle, light, 1.0F);
            if (on <= 0.0F) {
                continue;
            }
            Matrix4f toWorld = modelToWorld(vehicle, 1.0F);
            Vector3f lens = toWorld.transformPosition(light.modelPos());
            Vector3f dir = toWorld.transformDirection(light.modelDir()).normalize();
            Vec3 from = vehicle.position().add(lens.x, lens.y, lens.z);
            BlockHitResult hit = clip(level, vehicle, from, dir, light.range());
            if (hit == null) {
                continue;
            }
            double distance = hit.getLocation().distanceTo(from);
            float strength = on * Mth.clamp(1.15F - (float) distance / light.range(), 0.0F, 1.0F);
            float luminance = 15.0F * strength;
            if (luminance < 3.0F) {
                continue;
            }
            Vec3 normal = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
            float radius = Mth.clamp(0.4F + (float) distance * Mth.sin(light.halfAngle() * Mth.DEG_TO_RAD), 1.0F, 8.0F);
            Vec3 pos = hit.getLocation().add(normal.scale(0.6D));
            // Smoothed over a few ticks: a beam from a rocking hull swept its spot and its strength back and forth, and
            // the ground light flickered as a vehicle drove (the BD-40 and FSV-6, the user's review, 2026-11-18).
            Spot last = SMOOTHED.get(vehicle.getId());
            if (last != null && last.pos().distanceToSqr(pos) < 36.0D) {
                pos = last.pos().lerp(pos, 0.3D);
                luminance = Mth.lerp(0.3F, last.luminance(), luminance);
                radius = Mth.lerp(0.3F, last.radius(), radius);
            }
            Spot spot = new Spot(pos, radius, luminance);
            SMOOTHED.put(vehicle.getId(), spot);
            SPOTS.put(searchlightKey(vehicle), spot);
        }
    }

    /** The spot key of a vehicle's searchlight; its lamps follow it (up to {@link #MAX_LAMP_SPOTS}). */
    private static int searchlightKey(VehicleEntity vehicle) {
        return vehicle.getId() * (MAX_LAMP_SPOTS + 1);
    }

    private static final int MAX_LAMP_SPOTS = 15;
    /** How strongly a lamp lights what is round it (0..15): a navigation lamp lights a few blocks of airframe. */
    private static final float LAMP_LUMINANCE = 7.0F;

    /** Each lit lamp as a small light source at the lamp, so it lights the airframe and ground round it. */
    private static void addLampSpots(ClientLevel level, VehicleEntity vehicle, LightSet set) {
        if (!isPowered(vehicle) || set.lights().isEmpty()) {
            return;
        }
        // Lamps inside a cabin aren't world lights: the whole model takes one light level, so a cabin lamp lit the
        // outside of the hull with it; and a flashing jump light would rebuild the chunks round it twice a second.
        Matrix4f toWorld = modelToWorld(vehicle, 1.0F);
        float seconds = (vehicle.tickCount) / 20.0F;
        float seed = (vehicle.getId() * 0.37F) % 1.0F;
        int index = 0;
        for (PointLight light : set.lights()) {
            if (index >= MAX_LAMP_SPOTS) break;
            if (light.kind() == Kind.COCKPIT || light.kind() == Kind.STROBE || onBattery(light.kind())) continue;
            // World light has no colour of its own: vanilla's is warm and a shader pack tints every block light one
            // colour, so a red or green lamp lit the ground and airframe yellow-orange (the AH-6 under Photon,
            // 2026-11-26). Only a white lamp lights its surroundings; a coloured one keeps its glow and its drawn spill.
            if (!whiteLamp(light.colour())) continue;
            float on = flash(vehicle, light, seconds, seed);
            if (on <= 0.3F) continue;
            Vector3f at = toWorld.transformPosition(new Vector3f(light.x() / 16.0F, light.y() / 16.0F, light.z() / 16.0F));
            float luminance = LAMP_LUMINANCE * Math.min(1.0F, on);
            SPOTS.put(searchlightKey(vehicle) + 1 + index++, new Spot(vehicle.position().add(at.x, at.y, at.z), 1.0F, luminance));
        }
    }

    /** A lamp near enough white that the world's uncoloured light stands for it: no channel under 60% of the brightest. */
    static boolean whiteLamp(int colour) {
        int r = (colour >> 16) & 0xFF;
        int g = (colour >> 8) & 0xFF;
        int b = colour & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        return max > 0 && min >= max * 0.6F;
    }

    /** The searchlight spots lit this tick, by vehicle entity id. */
    public static Map<Integer, Spot> spots() {
        return SPOTS;
    }

    /** The bounds of this vehicle's searchlight spot, or null when its searchlight is off or reaches nothing. */
    public static AABB spotBounds(VehicleEntity vehicle) {
        Spot spot = SPOTS.get(searchlightKey(vehicle));
        return spot == null ? null : AABB.ofSize(spot.pos(), spot.radius() * 2.0F, spot.radius() * 2.0F, spot.radius() * 2.0F);
    }

    /** The first block a beam from {@code from} along {@code dir} reaches, or null within {@code range}. */
    static BlockHitResult clip(Level level, Entity source, Vec3 from, Vector3f dir, float range) {
        Vec3 to = from.add(dir.x * range, dir.y * range, dir.z * range);
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, source));
        return hit.getType() == HitResult.Type.MISS ? null : hit;
    }

    /**
     * The model's frame relative to the vehicle's position, as the renderer turns it (see
     * {@code AbstractVehicleGeoRenderer.vehicleAxis}): about the root height, by the jet's orientation or by yaw, pitch
     * and roll, after a half turn.
     */
    static Matrix4f modelToWorld(VehicleEntity vehicle, float partialTick) {
        float rootY = (float) vehicle.rotateOffsetHeight();
        Matrix4f matrix = new Matrix4f().translate(0.0F, rootY, 0.0F);
        if (vehicle.isJet()) {
            matrix.rotate(new Quaternionf(vehicle.poseRotation(partialTick)).mul(Axis.YP.rotationDegrees(180.0F)));
        } else {
            matrix.rotate(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, vehicle.yRotO, vehicle.getYRot())));
            matrix.rotate(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, vehicle.xRotO, vehicle.getXRot())));
            matrix.rotate(Axis.ZP.rotationDegrees(-vehicle.roll(partialTick)));
        }
        return matrix.translate(0.0F, -rootY, 0.0F);
    }
}
