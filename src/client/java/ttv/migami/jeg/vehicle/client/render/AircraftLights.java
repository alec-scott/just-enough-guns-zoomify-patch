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
    public enum Kind { POSITION, BEACON, STROBE, COCKPIT }

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
                              boolean taxi) {
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
    public record Strip(float x, float y, float z0, float z1, float height, int colour) {
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
    ), new Searchlight(0, 26.4F, -35.6F, 16, 26, 30, SEARCH_WHITE, false), List.of(), null, true);

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

    /** Sky light above which the night lights switch off, and below which they switch on (15 by day, 4 at night). */
    private static final float NIGHT_OFF_SKY_LIGHT = 11.5F;
    private static final float NIGHT_ON_SKY_LIGHT = 10.5F;
    /** Ticks a night light takes to come up to full brightness once switched on. */
    private static final float SWITCH_TICKS = 6.0F;

    private static boolean nightOn;
    private static float nightRamp;
    private static float nightRampO;
    private static final Map<Integer, Spot> SPOTS = new HashMap<>();

    /** Where a searchlight's beam lands this tick: a light {@code luminance} strong (0..15) at {@code pos}. */
    public record Spot(Vec3 pos, float radius, float luminance) {
    }

    private AircraftLights() {
    }

    public static LightSet forVehicle(VehicleEntity vehicle) {
        return switch (vehicle.vehicleDataId().getPath()) {
            case "bulldozer" -> BULLDOZER;
            case "a10" -> A10;
            case "ah6" -> AH6;
            case "mi28" -> MI28;
            default -> null;
        };
    }

    /** The aircraft has power: someone at the controls or the engine still turning. A wreck is dark. */
    public static boolean isPowered(VehicleEntity vehicle) {
        if (vehicle instanceof ttv.migami.jeg.vehicle.entity.BulldozerEntity dozer) return dozer.powered();
        if (vehicle.isWrecked()) {
            return false;
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
            return;
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
        if (nightRamp <= 0.0F) {
            return;
        }
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof VehicleEntity vehicle)) {
                continue;
            }
            LightSet set = forVehicle(vehicle);
            if (set == null || set.searchlight() == null) {
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
            SPOTS.put(vehicle.getId(), new Spot(hit.getLocation().add(normal.scale(0.6D)), radius, luminance));
        }
    }

    /** The searchlight spots lit this tick, by vehicle entity id. */
    public static Map<Integer, Spot> spots() {
        return SPOTS;
    }

    /** The bounds of this vehicle's searchlight spot, or null when its searchlight is off or reaches nothing. */
    public static AABB spotBounds(VehicleEntity vehicle) {
        Spot spot = SPOTS.get(vehicle.getId());
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
