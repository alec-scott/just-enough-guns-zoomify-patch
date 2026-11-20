package ttv.migami.jeg.vehicle.client.render;

import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The AH-6's glass cockpit, on the model's own dash (the user's review, 2026-11-19: "give the AH-6 the detailed instrument
 * panel similar to the Mi-28"): a colour attitude display on the left (sky and ground, the pitch ladder and bank pointer
 * turning with the helicopter, airspeed, radar height and heading written in its boxes), an engine page on the right (rotor,
 * power turbine, torque, turbine temperature and fuel bars and their figures, rising with the rotor), and between them the
 * centre stack: a navigation display whose compass rose turns with the heading and whose magenta line shows the drift, the
 * standby airspeed indicator and altimeter with live needles, and a row of caution lamps. On the console between the
 * seats, the weapons page: rounds and rockets left, the selected weapon boxed, and whether the flares are ready, over the
 * radio's head. Master caution and warning lamps either side of the stack. Textures from design/ah6/make_cockpit.py, whose
 * LAYOUT these places follow. With power the screens flicker on; the faces are lit by the world.
 */
final class Ah6CockpitLayer<T extends VehicleEntity> extends GeoRenderLayer<T, Void, VehicleRenderState> {
    private static final Identifier CENTRE_FACE = A10CockpitLayer.id("ah6_centre_panel");
    private static final Identifier SIDE_FACE = A10CockpitLayer.id("ah6_side_panel");
    private static final Identifier CONSOLE_FACE = A10CockpitLayer.id("ah6_console_panel");
    private static final Identifier PFD = A10CockpitLayer.id("ah6_pfd");
    private static final Identifier ENG = A10CockpitLayer.id("ah6_eng");
    private static final Identifier NAV = A10CockpitLayer.id("ah6_nav");
    private static final Identifier ARMT = A10CockpitLayer.id("ah6_armt");
    private static final Identifier ASI = A10CockpitLayer.id("ah6_asi");
    private static final Identifier ALT = A10CockpitLayer.id("ah6_alt");
    private static final Identifier WHITE = A10CockpitLayer.id("screen_white");

    // The dash faces (GeckoLib render pixels, from ah6.geo.json's bone4): each face's top edge's centre, then across (the
    // pilot's right), down it and out towards the crew.
    private static final CockpitPanel CENTRE = new CockpitPanel(new Vector3f(0.0F, 33.697F, -32.184F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -10.227F, 0.447F), new Vector3f(0.0F, 0.447F, 10.227F));
    private static final CockpitPanel LEFT = new CockpitPanel(new Vector3f(-5.899F, 31.79F, -33.023F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -3.807F, 0.845F), new Vector3f(0.0F, 0.845F, 3.807F));
    private static final CockpitPanel RIGHT = new CockpitPanel(new Vector3f(5.899F, 31.79F, -33.023F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -3.807F, 0.845F), new Vector3f(0.0F, 0.845F, 3.807F));
    private static final CockpitPanel CONSOLE = new CockpitPanel(new Vector3f(0.0F, 23.991F, -32.625F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -2.668F, 7.33F), new Vector3f(0.0F, 7.33F, 2.668F));
    /** The strip under the side displays; the stack hides its middle, the master lamps sit on its ends. */
    private static final CockpitPanel SHELF = new CockpitPanel(new Vector3f(0.0F, 27.772F, -33.13F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -0.952F, 0.211F), new Vector3f(0.0F, 0.211F, 0.952F));

    private static final float CENTRE_HALF = 2.73F;
    private static final float CENTRE_HEIGHT = 10.227F;
    private static final float SIDE_HALF = 2.681F;
    private static final float SIDE_HEIGHT = 3.9F;
    private static final float CONSOLE_HEIGHT = 7.8F;
    // Screens, as make_cockpit.py's LAYOUT: u of the left edge, v of the top, width, height.
    private static final float[] SIDE_SCREEN = {-2.43F, 0.22F, 4.86F, 3.2F};
    private static final float[] MFD = {-2.38F, 0.42F, 4.76F, 4.76F};
    private static final float[] ARMT_SCREEN = {-2.33F, 0.3F, 4.66F, 3.8F};
    private static final float[] RADIO = {-2.1F, 4.55F, 3.0F, 0.8F};
    private static final float GAUGE_V = 7.0F;
    private static final float GAUGE_U = 1.33F;
    private static final float GAUGE_R = 1.18F;

    // Deep, as a display's are: a shader pack's bloom washed the first, lighter blue out to white.
    private static final int SKY = 0x1A50B0;
    private static final int EARTH = 0x7A4419;
    /** Pixels of the attitude display a degree of pitch. */
    private static final float PITCH_SCALE = 0.075F;
    private static final int WHITE_RGB = 0xF2F4F0;
    private static final int GREEN = 0x5AFF8C;
    private static final int CYAN = 0x5ADCFF;
    private static final int MAGENTA = 0xFF5AE6;
    private static final int AMBER = 0xFFBE3C;
    private static final int RED = 0xFF3A2C;
    private static final int LAMP_OFF = 0xFF1C1D1E;

    /** Rotor speed (as the vehicle syncs it) that reads 100%: the speed it runs at in flight. */
    private static final float ROTOR_FULL = 0.085F;
    private static final float BOOT_TICKS = 24.0F;
    private static final double MAX_DISTANCE_SQ = 48.0D * 48.0D;
    private static final double SEA_LEVEL = 63.0D;
    private static final float FEET = 3.2808F;
    /** Blocks a tick to knots. */
    private static final float KNOTS = 20.0F * 1.9438F;

    private static final float TURN = (float) (Math.PI * 2.0D);

    private static final Map<VehicleEntity, Integer> POWERED_SINCE = new WeakHashMap<>();

    Ah6CockpitLayer(GeoRenderer<T, Void, VehicleRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void submitRenderTask(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        VehicleEntity heli = passInfo.renderState().vehicle;
        if (heli == null || passInfo.renderState().distanceToCameraSq > MAX_DISTANCE_SQ) {
            return;
        }
        float partialTick = Mth.clamp(passInfo.renderState().ageInTicks - heli.tickCount, 0.0F, 1.0F);
        PoseStack modelPose = new PoseStack();
        modelPose.last().set(passInfo.getModelRenderMatrixPose());
        int light = passInfo.packedLight();
        collector.submitCustomGeometry(modelPose, RenderTypes.entityCutout(CENTRE_FACE), (pose, buffer) ->
                lit(pose, buffer, CENTRE, CENTRE_HALF, CENTRE_HEIGHT, light));
        collector.submitCustomGeometry(modelPose, RenderTypes.entityCutout(SIDE_FACE), (pose, buffer) -> {
            lit(pose, buffer, LEFT, SIDE_HALF, SIDE_HEIGHT, light);
            lit(pose, buffer, RIGHT, SIDE_HALF, SIDE_HEIGHT, light);
        });
        collector.submitCustomGeometry(modelPose, RenderTypes.entityCutout(CONSOLE_FACE), (pose, buffer) ->
                lit(pose, buffer, CONSOLE, CENTRE_HALF, CONSOLE_HEIGHT, light));

        if (!AircraftLights.isPowered(heli)) {
            POWERED_SINCE.remove(heli);
            return;
        }
        int since = POWERED_SINCE.computeIfAbsent(heli, h -> h.tickCount);
        float boot = CockpitPanel.bootLevel((heli.tickCount - since + partialTick) / BOOT_TICKS);
        if (boot <= 0.0F) {
            return;
        }
        float level = boot * Mth.lerp(AircraftLights.nightSwitch(partialTick), 1.0F, 0.82F);
        int screen = CockpitPanel.grey(level);

        // What the instruments read.
        Vec3 angles = heli.poseAngles(partialTick);
        float heading = Mth.positiveModulo((float) angles.x + 180.0F, 360.0F);
        float pitchUp = (float) -angles.y;
        float bank = (float) angles.z;
        Vec3 motion = heli.getDeltaMovement();
        float knots = (float) Math.sqrt(motion.x * motion.x + motion.z * motion.z) * KNOTS;
        float track = (float) Math.toDegrees(Math.atan2(-motion.x, motion.z)) + 180.0F;
        float climb = (float) motion.y * 20.0F;
        float radarFeet = (float) heli.altitudeAboveGround() * FEET;
        float baroFeet = (float) Math.max(0.0D, heli.getY() - SEA_LEVEL) * FEET;
        float rotor = Mth.clamp(heli.propellerSpeed() / ROTOR_FULL, 0.0F, 1.1F);
        float fuel = heli.maxVehicleEnergy() <= 0 ? 0.0F : heli.vehicleEnergy() / (float) heli.maxVehicleEnergy();
        float hull = heli.vehicleHealth() / Math.max(1.0F, heli.maxVehicleHealth());
        float seconds = (heli.level().getGameTime() + partialTick) / 20.0F;
        int rounds = heli.vehicleWeaponSlotAmmo(0);
        int rockets = heli.vehicleWeaponSlotAmmo(1);
        int selected = heli.selectedVehicleWeaponIndex();
        boolean flares = heli.vehicleFlareAmmo() > 0;
        float flareWait = heli.vehicleDecoyCooldown() / 20.0F;
        Readings r = new Readings(heading, pitchUp, bank, knots, track, climb, radarFeet, baroFeet, rotor, fuel, hull,
                seconds, rounds, rockets, selected, flares, flareWait, level, boot);

        // The attitude display's sky, ground and ladder go under its overlay, each in its own pass: quads in one
        // translucent buffer are sorted by distance, and the big sky quad was drawn over parts of the ground.
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(WHITE), (pose, buffer) -> sky(pose, buffer, r));
        collector.order(2).submitCustomGeometry(modelPose, RenderTypes.eyes(WHITE), (pose, buffer) -> earth(pose, buffer, r));
        collector.order(3).submitCustomGeometry(modelPose, RenderTypes.eyes(WHITE), (pose, buffer) -> ladder(pose, buffer, r));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(ENG), (pose, buffer) ->
                screen(pose, buffer, RIGHT, SIDE_SCREEN, screen));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(NAV), (pose, buffer) ->
                screen(pose, buffer, CENTRE, MFD, screen));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(ARMT), (pose, buffer) ->
                screen(pose, buffer, CONSOLE, ARMT_SCREEN, screen));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(ASI), (pose, buffer) ->
                CENTRE.face(pose, buffer, -GAUGE_U - GAUGE_R, GAUGE_V - GAUGE_R, 2 * GAUGE_R, 2 * GAUGE_R, 0.05F, screen));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(ALT), (pose, buffer) ->
                CENTRE.face(pose, buffer, GAUGE_U - GAUGE_R, GAUGE_V - GAUGE_R, 2 * GAUGE_R, 2 * GAUGE_R, 0.05F, screen));
        collector.order(4).submitCustomGeometry(modelPose, RenderTypes.eyes(PFD), (pose, buffer) ->
                screen(pose, buffer, LEFT, SIDE_SCREEN, screen));
        collector.order(5).submitCustomGeometry(modelPose, RenderTypes.eyes(WHITE), (pose, buffer) -> {
            pfdFigures(pose, buffer, r);
            engine(pose, buffer, r);
            nav(pose, buffer, r);
            gauges(pose, buffer, r);
            armament(pose, buffer, r);
            lamps(pose, buffer, r);
        });
    }

    private record Readings(float heading, float pitchUp, float bank, float knots, float track, float climb,
                            float radarFeet, float baroFeet, float rotor, float fuel, float hull, float seconds, int rounds,
                            int rockets, int selected, boolean flares, float flareWait, float level, float boot) {
        int colour(int rgb, float alpha) {
            return CockpitPanel.argb(rgb, alpha * this.level);
        }

        boolean blink(float rate) {
            return ((int) (this.seconds * rate) & 1) == 0;
        }
    }

    /** A whole dash face in its texture, lit by the world, a hair proud of the model's face. */
    private static void lit(PoseStack.Pose pose, VertexConsumer buffer, CockpitPanel face, float half, float height, int light) {
        CockpitPanel.quad(pose, buffer, face.p(-half, 0.0F, 0.03F), face.p(half, 0.0F, 0.03F), face.p(half, height, 0.03F),
                face.p(-half, height, 0.03F), 0.0F, 0.0F, 1.0F, 1.0F, 0xFFFFFFFF, light, face.out);
    }

    private static void screen(PoseStack.Pose pose, VertexConsumer buffer, CockpitPanel face, float[] at, int colour) {
        face.face(pose, buffer, at[0], at[1], at[2], at[3], 0.05F, colour);
    }

    /** A texel of a {@code size}-texel-wide page to model pixels across the screen {@code at}. */
    private static float tu(float[] at, float x, float size) {
        return at[0] + at[2] * x / size;
    }

    private static float tv(float[] at, float y, float size) {
        return at[1] + at[3] * y / size;
    }

    /** The attitude display's sky: the whole screen. */
    private static void sky(PoseStack.Pose pose, VertexConsumer buffer, Readings r) {
        float[] s = SIDE_SCREEN;
        LEFT.face(pose, buffer, s[0], s[1], s[2], s[3], 0.05F, darken(SKY, r.level));
    }

    /** Its ground, below the horizon. */
    private static void earth(PoseStack.Pose pose, VertexConsumer buffer, Readings r) {
        float[] s = SIDE_SCREEN;
        LEFT.ground(pose, buffer, tu(s, 128.0F, 256.0F), tv(s, 85.0F, 168.0F), s[0], s[1], s[0] + s[2], s[1] + s[3],
                r.pitchUp, r.bank, PITCH_SCALE, 0.055F, darken(EARTH, r.level));
    }

    /** Its horizon and pitch ladder, between the tapes. */
    private static void ladder(PoseStack.Pose pose, VertexConsumer buffer, Readings r) {
        float[] s = SIDE_SCREEN;
        LEFT.attitude(pose, buffer, tu(s, 128.0F, 256.0F), tv(s, 85.0F, 168.0F), tu(s, 48.0F, 256.0F), s[1],
                tu(s, 208.0F, 256.0F), s[1] + s[3], r.pitchUp, r.bank, PITCH_SCALE, 0.06F, WHITE_RGB, 0.95F * r.level,
                0.0F, 0.0F, 0.045F);
    }

    private static int darken(int rgb, float level) {
        int red = Math.round(((rgb >> 16) & 0xFF) * level);
        int green = Math.round(((rgb >> 8) & 0xFF) * level);
        int blue = Math.round((rgb & 0xFF) * level);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    /** The attitude display's figures, over its overlay: speed, radar height, heading, and the bank pointer. */
    private static void pfdFigures(PoseStack.Pose pose, VertexConsumer buffer, Readings r) {
        float[] s = SIDE_SCREEN;
        int white = r.colour(WHITE_RGB, 1.0F);
        float h = 0.22F;
        right(LEFT, pose, buffer, String.valueOf(Math.round(r.knots)), tu(s, 40.0F, 256.0F), tv(s, 84.0F, 168.0F) - h / 2, h, white);
        right(LEFT, pose, buffer, String.valueOf(Math.round(r.radarFeet / 10.0F) * 10), tu(s, 250.0F, 256.0F),
                tv(s, 84.0F, 168.0F) - h / 2, h * 0.9F, white);
        centred(LEFT, pose, buffer, three(r.heading), tu(s, 128.0F, 256.0F), tv(s, 156.0F, 168.0F) - h / 2, h, white);
        // The sky pointer under the bank scale, turning with the horizon.
        float cu = tu(s, 128.0F, 256.0F);
        float cv = tv(s, 98.0F, 168.0F);
        float radius = s[2] * 66.0F / 256.0F;
        float a = -r.bank * Mth.DEG_TO_RAD;
        float tipU = cu + Mth.sin(a) * (radius - 0.02F);
        float tipV = cv - Mth.cos(a) * (radius - 0.02F);
        LEFT.line(pose, buffer, tipU, tipV, cu + Mth.sin(a) * (radius - 0.2F), cv - Mth.cos(a) * (radius - 0.2F), 0.07F,
                0.075F, r.colour(0xFFFFFF, 1.0F), -100, -100, 100, 100);
        // The climb as a cyan tick on the altitude tape's inner edge: up a pixel for 5 m/s.
        float climbV = Mth.clamp(tv(s, 84.0F, 168.0F) - r.climb * 0.12F, tv(s, 28.0F, 168.0F), tv(s, 140.0F, 168.0F));
        LEFT.line(pose, buffer, tu(s, 204.0F, 256.0F), climbV, tu(s, 212.0F, 256.0F), climbV, 0.06F, 0.075F,
                r.colour(CYAN, 1.0F), -100, -100, 100, 100);
    }

    /** The engine page's bars and figures. */
    private static void engine(PoseStack.Pose pose, VertexConsumer buffer, Readings r) {
        float[] s = SIDE_SCREEN;
        float nr = r.rotor * 100.0F;
        float np = Math.min(110.0F, r.rotor * 100.0F + (r.rotor > 0.9F ? 1.0F + 0.6F * Mth.sin(r.seconds * 3.1F) : 0.0F));
        float tq = Mth.clamp(r.rotor * (38.0F + r.knots * 0.28F + Math.max(-12.0F, r.climb) * 3.5F)
                + 1.2F * Mth.sin(r.seconds * 5.3F) * r.rotor, 0.0F, 118.0F);
        float tot = r.rotor <= 0.0F ? 30.0F : 280.0F + 380.0F * r.rotor + tq * 1.2F + 4.0F * Mth.sin(r.seconds * 2.3F);
        float fuel = r.fuel * 100.0F;
        float[][] bars = {{26, nr / 110.0F, nr}, {74, np / 110.0F, np}, {122, tq / 120.0F, tq}, {170, tot / 900.0F, tot},
                {218, fuel / 100.0F, fuel}};
        for (float[] bar : bars) {
            float x = bar[0];
            float fill = Mth.clamp(bar[1], 0.0F, 1.0F);
            boolean high = bar == bars[2] && tq > 100.0F || bar == bars[3] && tot > 810.0F || bar == bars[4] && fuel < 15.0F;
            int colour = r.colour(high ? (r.blink(3.0F) ? RED : AMBER) : GREEN, 0.95F);
            float u0 = tu(s, x - 8.0F, 256.0F);
            float u1 = tu(s, x + 8.0F, 256.0F);
            float bottom = tv(s, 135.0F, 168.0F);
            float top = bottom - (tv(s, 135.0F, 168.0F) - tv(s, 41.0F, 168.0F)) * fill;
            if (fill > 0.0F) {
                RIGHT.face(pose, buffer, u0, top, u1 - u0, bottom - top, 0.075F, colour);
            }
            centred(RIGHT, pose, buffer, String.valueOf(Math.round(bar[2])), tu(s, x, 256.0F), tv(s, 15.0F, 168.0F), 0.2F,
                    r.colour(high ? AMBER : WHITE_RGB, 1.0F));
        }
    }

    /** The navigation display: the compass rose turning with the heading, the drift line, the heading and ground speed. */
    private static void nav(PoseStack.Pose pose, VertexConsumer buffer, Readings r) {
        float[] s = MFD;
        float u0 = s[0];
        float v0 = s[1];
        float u1 = s[0] + s[2];
        float v1 = s[1] + s[3];
        float cu = tu(s, 128.0F, 256.0F);
        float cv = tv(s, 150.0F, 256.0F);
        float radius = s[2] * 100.0F / 256.0F;
        int rose = r.colour(WHITE_RGB, 0.9F);
        for (int bearing = 0; bearing < 360; bearing += 10) {
            float a = (bearing - r.heading) * Mth.DEG_TO_RAD;
            float inner = radius - (bearing % 30 == 0 ? 0.2F : 0.11F);
            CENTRE.line(pose, buffer, cu + Mth.sin(a) * inner, cv - Mth.cos(a) * inner, cu + Mth.sin(a) * radius,
                    cv - Mth.cos(a) * radius, 0.035F, 0.075F, rose, u0, v0, u1, v1);
            if (bearing % 30 == 0) {
                String label = switch (bearing) {
                    case 0 -> "N";
                    case 90 -> "E";
                    case 180 -> "S";
                    case 270 -> "W";
                    default -> String.valueOf(bearing / 10);
                };
                float h = bearing % 90 == 0 ? 0.2F : 0.15F;
                float lu = cu + Mth.sin(a) * (radius - 0.38F);
                float lv = cv - Mth.cos(a) * (radius - 0.38F);
                CENTRE.text(pose, buffer, label, lu - CockpitPanel.textWidth(label, h) / 2, lv - h / 2, h, 0.075F,
                        r.colour(bearing == 0 ? AMBER : WHITE_RGB, 1.0F), u0, v0, u1, v1);
            }
        }
        // The lubber line and the helicopter's own symbol.
        CENTRE.line(pose, buffer, cu, cv - radius - 0.02F, cu, cv - radius + 0.22F, 0.06F, 0.08F, r.colour(AMBER, 1.0F), u0, v0, u1, v1);
        int own = r.colour(WHITE_RGB, 1.0F);
        CENTRE.line(pose, buffer, cu, cv - 0.2F, cu, cv + 0.26F, 0.07F, 0.08F, own, u0, v0, u1, v1);
        CENTRE.line(pose, buffer, cu - 0.17F, cv - 0.02F, cu + 0.17F, cv - 0.02F, 0.05F, 0.08F, own, u0, v0, u1, v1);
        CENTRE.line(pose, buffer, cu - 0.08F, cv + 0.24F, cu + 0.08F, cv + 0.24F, 0.04F, 0.08F, own, u0, v0, u1, v1);
        // The ground track, dashed magenta, when the helicopter is moving.
        if (r.knots > 2.0F) {
            float a = (r.track - r.heading) * Mth.DEG_TO_RAD;
            for (float d = 0.32F; d < radius - 0.1F; d += 0.22F) {
                CENTRE.line(pose, buffer, cu + Mth.sin(a) * d, cv - Mth.cos(a) * d, cu + Mth.sin(a) * (d + 0.12F),
                        cv - Mth.cos(a) * (d + 0.12F), 0.05F, 0.078F, r.colour(MAGENTA, 0.95F), u0, v0, u1, v1);
            }
        }
        centred(CENTRE, pose, buffer, three(r.heading), cu, tv(s, 8.0F, 256.0F), 0.26F, own);
        CENTRE.text(pose, buffer, String.valueOf(Math.round(r.knots)), tu(s, 40.0F, 256.0F), tv(s, 236.0F, 256.0F), 0.2F,
                0.075F, r.colour(GREEN, 1.0F), u0, v0, u1, v1);
    }

    /** The standby airspeed indicator's and altimeter's needles. */
    private static void gauges(PoseStack.Pose pose, VertexConsumer buffer, Readings r) {
        int needle = r.colour(0xF6F2E4, 1.0F);
        float asi = -135.0F * Mth.DEG_TO_RAD + 270.0F * Mth.DEG_TO_RAD * Mth.clamp(r.knots / 160.0F, 0.0F, 1.0F);
        CENTRE.needle(pose, buffer, -GAUGE_U, GAUGE_V, GAUGE_R * 0.82F, asi, 0.08F, 0.08F, needle);
        float hundreds = (r.baroFeet % 1000.0F) / 1000.0F * TURN;
        float thousands = (r.baroFeet % 10000.0F) / 10000.0F * TURN;
        CENTRE.needle(pose, buffer, GAUGE_U, GAUGE_V, GAUGE_R * 0.55F, thousands, 0.13F, 0.075F, needle);
        CENTRE.needle(pose, buffer, GAUGE_U, GAUGE_V, GAUGE_R * 0.85F, hundreds, 0.07F, 0.082F, needle);
        for (float u : new float[]{-GAUGE_U, GAUGE_U}) {
            CENTRE.face(pose, buffer, u - 0.07F, GAUGE_V - 0.07F, 0.14F, 0.14F, 0.085F, r.colour(0x303234, 1.0F) | 0xFF000000);
        }
    }

    /** The weapons page: rounds and rockets left, the selected weapon boxed and its pod lit, the flares, the radio. */
    private static void armament(PoseStack.Pose pose, VertexConsumer buffer, Readings r) {
        float[] s = ARMT_SCREEN;
        float[][] boxes = {{16, 120, 104, 156}, {152, 120, 240, 156}};
        int[] counts = {r.rounds, r.rockets};
        for (int i = 0; i < 2; i++) {
            float[] b = boxes[i];
            float u0 = tu(s, b[0], 256.0F);
            float u1 = tu(s, b[2], 256.0F);
            float v0 = tv(s, b[1], 210.0F);
            float v1 = tv(s, b[3], 210.0F);
            boolean picked = r.selected == i;
            boolean empty = counts[i] <= 0;
            centred(CONSOLE, pose, buffer, String.valueOf(counts[i]), (u0 + u1) / 2, (v0 + v1) / 2 - 0.17F, 0.34F,
                    r.colour(empty ? AMBER : GREEN, 1.0F));
            if (picked) {
                CONSOLE.outline(pose, buffer, u0, v0, u1, v1, 0.06F, 0.075F, r.colour(GREEN, 1.0F));
                // The selected pod on the planform.
                float pod = tu(s, i == 0 ? 66.0F : 190.0F, 256.0F);
                CONSOLE.face(pose, buffer, pod - 0.13F, tv(s, 37.0F, 210.0F), 0.26F, tv(s, 69.0F, 210.0F) - tv(s, 37.0F, 210.0F),
                        0.075F, r.colour(GREEN, 0.75F));
            }
        }
        // Flares: green when ready, amber with the seconds left while the dispenser recycles.
        float lampU = tu(s, 40.0F, 256.0F);
        float lampV = tv(s, 187.0F, 210.0F);
        CONSOLE.face(pose, buffer, lampU, lampV, 0.28F, 0.24F, 0.075F, r.colour(r.flares ? GREEN : AMBER, 1.0F));
        if (!r.flares && r.flareWait > 0.0F) {
            CONSOLE.text(pose, buffer, String.valueOf((int) Math.ceil(r.flareWait)), lampU + 0.38F, lampV, 0.24F, 0.075F,
                    r.colour(AMBER, 1.0F), -100, -100, 100, 100);
        }
        // Master arm: lit while either weapon has rounds.
        boolean armed = r.rounds > 0 || r.rockets > 0;
        CONSOLE.face(pose, buffer, tu(s, 160.0F, 256.0F), tv(s, 188.0F, 210.0F), 0.24F, 0.22F, 0.075F,
                armed ? r.colour(GREEN, 1.0F) : LAMP_OFF);
        // The radio's window: the UHF frequency in use.
        CONSOLE.text(pose, buffer, "251.75", RADIO[0] + 0.25F, RADIO[1] + 0.2F, 0.4F, 0.06F, r.colour(AMBER, 0.95F),
                -100, -100, 100, 100);
    }

    /** The caution lamps under the gauges, and master caution and warning either side of the stack. */
    private static void lamps(PoseStack.Pose pose, VertexConsumer buffer, Readings r) {
        boolean lowFuel = r.fuel < 0.2F;
        boolean chip = r.hull < 0.5F;
        boolean armed = r.selected == 0 ? r.rounds > 0 : r.rockets > 0;
        int[] colours = {
                CockpitPanel.argb(GREEN, 0.85F * r.boot),
                lowFuel && (r.fuel >= 0.1F || r.blink(2.0F)) ? CockpitPanel.argb(AMBER, 0.95F * r.boot) : LAMP_OFF,
                armed ? CockpitPanel.argb(GREEN, 0.85F * r.boot) : LAMP_OFF,
                chip && (r.hull >= 0.25F || r.blink(2.5F)) ? CockpitPanel.argb(AMBER, 0.95F * r.boot) : LAMP_OFF,
                r.flares ? CockpitPanel.argb(GREEN, 0.85F * r.boot) : CockpitPanel.argb(AMBER, 0.8F * r.boot)
        };
        for (int i = 0; i < 5; i++) {
            CENTRE.face(pose, buffer, -2.5F + i * 1.02F, 8.62F, 0.86F, 0.42F, 0.06F, colours[i]);
        }
        boolean caution = lowFuel || chip;
        boolean warning = r.hull < 0.25F;
        SHELF.face(pose, buffer, -4.45F, 0.15F, 1.5F, 0.66F, 0.06F,
                caution ? CockpitPanel.argb(AMBER, 0.95F * r.boot) : LAMP_OFF);
        SHELF.face(pose, buffer, 2.95F, 0.15F, 1.5F, 0.66F, 0.06F,
                warning && r.blink(3.0F) ? CockpitPanel.argb(RED, 0.95F * r.boot) : LAMP_OFF);
    }

    private static String three(float heading) {
        int h = Math.round(heading) % 360;
        return (h < 100 ? "0" : "") + (h < 10 ? "0" : "") + h;
    }

    private static void centred(CockpitPanel face, PoseStack.Pose pose, VertexConsumer buffer, String s, float u, float v,
                                float height, int colour) {
        face.text(pose, buffer, s, u - CockpitPanel.textWidth(s, height) / 2, v, height, 0.075F, colour, -100, -100, 100, 100);
    }

    private static void right(CockpitPanel face, PoseStack.Pose pose, VertexConsumer buffer, String s, float u, float v,
                              float height, int colour) {
        face.text(pose, buffer, s, u - CockpitPanel.textWidth(s, height), v, height, 0.075F, colour, -100, -100, 100, 100);
    }
}
