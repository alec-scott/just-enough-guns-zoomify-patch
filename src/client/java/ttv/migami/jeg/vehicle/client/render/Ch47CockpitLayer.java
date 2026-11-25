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
import ttv.migami.jeg.vehicle.entity.ChinookEntity;
import ttv.migami.jeg.vehicle.entity.ChinookSling;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The CH-47F's glass cockpit (the user, 2026-11-21: "a detailed textured console"), on the model's own furniture
 * (generate.py COCKPIT): the instrument panel's five displays under the glare shield (each pilot's flight display
 * outboard, the map beside the pilot's, the engine page in the middle, the cargo page beside the co-pilot's), master
 * caution and warning at either end and a row of caution lamps; the centre console's two CDUs, radios and cargo hook
 * panel; the overhead console's switch panels. Textures from design/chinook/make_cockpit.py, whose LAYOUT these places
 * follow. With power the screens flicker on; the faces are lit by the world.
 */
final class Ch47CockpitLayer<T extends VehicleEntity> extends GeoRenderLayer<T, Void, VehicleRenderState> {
    private static final Identifier PANEL_FACE = A10CockpitLayer.id("ch47_panel");
    private static final Identifier CONSOLE_FACE = A10CockpitLayer.id("ch47_console");
    private static final Identifier OVERHEAD_FACE = A10CockpitLayer.id("ch47_overhead");
    private static final Identifier PFD = A10CockpitLayer.id("ch47_pfd");
    private static final Identifier NAV = A10CockpitLayer.id("ch47_nav");
    private static final Identifier ENG = A10CockpitLayer.id("ch47_eng");
    private static final Identifier CARGO = A10CockpitLayer.id("ch47_cargo");
    private static final Identifier WHITE = A10CockpitLayer.id("screen_white");

    // The faces (render pixels, +X the aircraft's right): each one's top (or front) edge's centre, then across, down
    // it (or aft along it) and out towards the crew.
    private static final CockpitPanel PANEL = new CockpitPanel(new Vector3f(0.0F, 35.0F, -115.0F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F));
    private static final CockpitPanel CONSOLE = new CockpitPanel(new Vector3f(0.0F, 26.0F, -115.0F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F), new Vector3f(0.0F, 1.0F, 0.0F));
    /** Read looking up with the nose at the top of the view: across runs to the aircraft's left there. */
    private static final CockpitPanel OVERHEAD = new CockpitPanel(new Vector3f(0.0F, 61.0F, -112.0F),
            new Vector3f(-1.0F, 0.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F), new Vector3f(0.0F, -1.0F, 0.0F));
    private static final float PANEL_HALF = 20.6F;
    private static final float PANEL_HEIGHT = 9.0F;
    private static final float CONSOLE_HALF = 4.6F;
    private static final float CONSOLE_LENGTH = 18.0F;
    private static final float OVERHEAD_HALF = 6.0F;
    private static final float OVERHEAD_LENGTH = 15.0F;

    /** The displays, left to right from the seats (make_cockpit.py MFD_U): co-pilot's PFD, cargo, engines, map, pilot's PFD. */
    private static final float[] MFD_U = {-16.4F, -8.2F, 0.0F, 8.2F, 16.4F};
    private static final float MFD_TOP = 1.05F;
    private static final float MFD_W = 6.0F;
    private static final float MFD_H = 5.0F;
    private static final float PAGE_W = 256.0F;
    private static final float PAGE_H = 213.0F;
    private static final float[][] CDU = {{-4.35F, 0.7F, 4.1F, 2.2F}, {0.25F, 0.7F, 4.1F, 2.2F}};
    private static final float[][] RADIO = {{-4.2F, 10.45F, 3.2F, 0.7F}, {0.6F, 10.45F, 3.2F, 0.7F}};
    private static final float[] HOOK_WINDOW = {-3.9F, 14.0F, 2.4F, 0.6F};

    private static final int SKY = 0x1A50B0;
    private static final int EARTH = 0x7A4419;
    private static final float PITCH_SCALE = 0.085F;
    private static final int WHITE_RGB = 0xF2F4F0;
    private static final int GREEN = 0x5AFF8C;
    private static final int CYAN = 0x5ADCFF;
    private static final int MAGENTA = 0xFF5AE6;
    private static final int AMBER = 0xFFBE3C;
    private static final int RED = 0xFF3A2C;
    private static final int LAMP_OFF = 0xFF1C1D1E;

    private static final float ROTOR_FULL = 0.085F;
    private static final float BOOT_TICKS = 30.0F;
    private static final double MAX_DISTANCE_SQ = 48.0D * 48.0D;
    private static final double SEA_LEVEL = 63.0D;
    private static final float FEET = 3.2808F;
    private static final float KNOTS = 20.0F * 1.9438F;

    private static final Map<VehicleEntity, Integer> POWERED_SINCE = new WeakHashMap<>();

    Ch47CockpitLayer(GeoRenderer<T, Void, VehicleRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void submitRenderTask(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        if (!(passInfo.renderState().vehicle instanceof ChinookEntity heli) || passInfo.renderState().distanceToCameraSq > MAX_DISTANCE_SQ) {
            return;
        }
        float partialTick = Mth.clamp(passInfo.renderState().ageInTicks - heli.tickCount, 0.0F, 1.0F);
        PoseStack modelPose = new PoseStack();
        modelPose.last().set(passInfo.getModelRenderMatrixPose());
        int light = passInfo.packedLight();
        collector.submitCustomGeometry(modelPose, RenderTypes.entityCutout(PANEL_FACE), (pose, buffer) ->
                lit(pose, buffer, PANEL, PANEL_HALF, PANEL_HEIGHT, light));
        collector.submitCustomGeometry(modelPose, RenderTypes.entityCutout(CONSOLE_FACE), (pose, buffer) ->
                lit(pose, buffer, CONSOLE, CONSOLE_HALF, CONSOLE_LENGTH, light));
        collector.submitCustomGeometry(modelPose, RenderTypes.entityCutout(OVERHEAD_FACE), (pose, buffer) ->
                lit(pose, buffer, OVERHEAD, OVERHEAD_HALF, OVERHEAD_LENGTH, light));

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
        ChinookSling sling = heli.sling();
        VehicleEntity load = sling.loadEntity();
        Readings r = new Readings(heading, pitchUp, bank, knots, track, climb, radarFeet, baroFeet, rotor, fuel, hull, seconds,
                sling.state(), sling.length(), load == null ? 0.0F : (float) load.vehicleData().mass(),
                heli.rampAngle(partialTick), heli.crewDoorOpen(), level, boot);

        // Both flight displays' sky, ground and ladder under their overlays, each in its own pass (quads in one
        // translucent buffer are sorted by distance, and a sky quad was drawn over parts of the ground).
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(WHITE), (pose, buffer) -> {
            sky(pose, buffer, 0, r);
            sky(pose, buffer, 4, r);
        });
        collector.order(2).submitCustomGeometry(modelPose, RenderTypes.eyes(WHITE), (pose, buffer) -> {
            earth(pose, buffer, 0, r);
            earth(pose, buffer, 4, r);
        });
        collector.order(3).submitCustomGeometry(modelPose, RenderTypes.eyes(WHITE), (pose, buffer) -> {
            ladder(pose, buffer, 0, r);
            ladder(pose, buffer, 4, r);
        });
        collector.order(4).submitCustomGeometry(modelPose, RenderTypes.eyes(PFD), (pose, buffer) -> {
            display(pose, buffer, 0, screen);
            display(pose, buffer, 4, screen);
        });
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(CARGO), (pose, buffer) -> display(pose, buffer, 1, screen));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(ENG), (pose, buffer) -> display(pose, buffer, 2, screen));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(NAV), (pose, buffer) -> display(pose, buffer, 3, screen));
        collector.order(5).submitCustomGeometry(modelPose, RenderTypes.eyes(WHITE), (pose, buffer) -> {
            pfdFigures(pose, buffer, 0, r);
            pfdFigures(pose, buffer, 4, r);
            cargo(pose, buffer, r);
            engines(pose, buffer, r);
            nav(pose, buffer, r);
            lamps(pose, buffer, r);
            console(pose, buffer, r, screen);
        });
    }

    private record Readings(float heading, float pitchUp, float bank, float knots, float track, float climb, float radarFeet,
                            float baroFeet, float rotor, float fuel, float hull, float seconds, int hook, float rope,
                            float loadTons, float ramp, boolean doorOpen, float level, float boot) {
        int colour(int rgb, float alpha) {
            return CockpitPanel.argb(rgb, alpha * this.level);
        }

        boolean blink(float rate) {
            return ((int) (this.seconds * rate) & 1) == 0;
        }
    }

    private static void lit(PoseStack.Pose pose, VertexConsumer buffer, CockpitPanel face, float half, float height, int light) {
        CockpitPanel.quad(pose, buffer, face.p(-half, 0.0F, 0.03F), face.p(half, 0.0F, 0.03F), face.p(half, height, 0.03F),
                face.p(-half, height, 0.03F), 0.0F, 0.0F, 1.0F, 1.0F, 0xFFFFFFFF, light, face.out);
    }

    /** Display {@code i}'s screen rectangle on the panel: u of the left edge, v of the top, width, height. */
    private static float[] mfd(int i) {
        return new float[]{MFD_U[i] - MFD_W / 2.0F, MFD_TOP, MFD_W, MFD_H};
    }

    private static void display(PoseStack.Pose pose, VertexConsumer buffer, int i, int colour) {
        float[] s = mfd(i);
        PANEL.face(pose, buffer, s[0], s[1], s[2], s[3], 0.05F, colour);
    }

    private static float tu(float[] s, float x) {
        return s[0] + s[2] * x / PAGE_W;
    }

    private static float tv(float[] s, float y) {
        return s[1] + s[3] * y / PAGE_H;
    }

    private static void sky(PoseStack.Pose pose, VertexConsumer buffer, int i, Readings r) {
        float[] s = mfd(i);
        PANEL.face(pose, buffer, s[0], s[1], s[2], s[3], 0.05F, darken(SKY, r.level));
    }

    private static void earth(PoseStack.Pose pose, VertexConsumer buffer, int i, Readings r) {
        float[] s = mfd(i);
        PANEL.ground(pose, buffer, tu(s, 128.0F), tv(s, 105.0F), s[0], s[1], s[0] + s[2], s[1] + s[3], r.pitchUp, r.bank,
                PITCH_SCALE, 0.055F, darken(EARTH, r.level));
    }

    private static void ladder(PoseStack.Pose pose, VertexConsumer buffer, int i, Readings r) {
        float[] s = mfd(i);
        PANEL.attitude(pose, buffer, tu(s, 128.0F), tv(s, 105.0F), tu(s, 50.0F), s[1], tu(s, 206.0F), s[1] + s[3], r.pitchUp,
                r.bank, PITCH_SCALE, 0.06F, WHITE_RGB, 0.95F * r.level, 0.0F, 0.0F, 0.045F);
    }

    private static int darken(int rgb, float level) {
        int red = Math.round(((rgb >> 16) & 0xFF) * level);
        int green = Math.round(((rgb >> 8) & 0xFF) * level);
        int blue = Math.round((rgb & 0xFF) * level);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    /** A flight display's figures over its overlay: speed, barometric altitude, heading, radar height, the bank pointer. */
    private static void pfdFigures(PoseStack.Pose pose, VertexConsumer buffer, int i, Readings r) {
        float[] s = mfd(i);
        int white = r.colour(WHITE_RGB, 1.0F);
        float h = 0.24F;
        right(PANEL, pose, buffer, String.valueOf(Math.round(r.knots)), tu(s, 42.0F), tv(s, 105.0F) - h / 2, h, white);
        right(PANEL, pose, buffer, String.valueOf(Math.round(r.baroFeet / 10.0F) * 10), tu(s, 250.0F), tv(s, 105.0F) - h / 2,
                h * 0.85F, white);
        centred(PANEL, pose, buffer, three(r.heading), tu(s, 128.0F), tv(s, 197.0F) - h / 2, h, white);
        PANEL.text(pose, buffer, String.valueOf(Math.round(Math.min(r.radarFeet, 2500.0F))), tu(s, 160.0F), tv(s, 197.0F) - 0.1F,
                0.2F, 0.075F, r.colour(GREEN, 1.0F), -100, -100, 100, 100);
        float cu = tu(s, 128.0F);
        float cv = tv(s, 120.0F);
        float radius = s[2] * 74.0F / PAGE_W;
        float a = -r.bank * Mth.DEG_TO_RAD;
        PANEL.line(pose, buffer, cu + Mth.sin(a) * (radius - 0.02F), cv - Mth.cos(a) * (radius - 0.02F),
                cu + Mth.sin(a) * (radius - 0.24F), cv - Mth.cos(a) * (radius - 0.24F), 0.08F, 0.075F, white, -100, -100, 100, 100);
        float climbV = Mth.clamp(tv(s, 105.0F) - r.climb * 0.14F, tv(s, 40.0F), tv(s, 170.0F));
        PANEL.line(pose, buffer, tu(s, 202.0F), climbV, tu(s, 210.0F), climbV, 0.07F, 0.075F, r.colour(CYAN, 1.0F), -100, -100, 100, 100);
    }

    /** The engine page: both T55s' torque, gas producer and turbine temperature, rotor speed and fuel. */
    private static void engines(PoseStack.Pose pose, VertexConsumer buffer, Readings r) {
        float[] s = mfd(2);
        float load = Mth.clamp(42.0F + r.knots * 0.25F + Math.max(-12.0F, r.climb) * 3.2F + r.loadTons * 2.5F, 0.0F, 118.0F);
        float[] values = new float[8];
        for (int engine = 0; engine < 2; engine++) {
            float wobble = Mth.sin(r.seconds * (4.1F + engine * 0.7F)) * 1.1F;
            float tq = r.rotor * load + wobble * r.rotor + (engine == 0 ? 0.6F : -0.4F);
            float ng = r.rotor <= 0.0F ? 0.0F : Math.min(105.0F, 60.0F + r.rotor * 30.0F + tq * 0.1F + wobble * 0.3F);
            float tgt = r.rotor <= 0.0F ? 30.0F : 380.0F + r.rotor * 260.0F + tq * 1.6F + engine * 6.0F + wobble * 2.0F;
            values[engine] = tq;
            values[2 + engine] = ng;
            values[4 + engine] = tgt;
        }
        values[6] = r.rotor * 100.0F;
        values[7] = r.fuel * 100.0F;
        float[] x = {22, 46, 82, 106, 142, 166, 202, 230};
        float[] full = {120, 120, 110, 110, 900, 900, 110, 100};
        for (int k = 0; k < 8; k++) {
            float fill = Mth.clamp(values[k] / full[k], 0.0F, 1.0F);
            boolean high = k < 2 && values[k] > 100.0F || (k == 4 || k == 5) && values[k] > 810.0F || k == 7 && values[k] < 15.0F;
            int colour = r.colour(high ? (r.blink(3.0F) ? RED : AMBER) : GREEN, 0.95F);
            float u0 = tu(s, x[k] - 7.0F);
            float u1 = tu(s, x[k] + 7.0F);
            float bottom = tv(s, 160.0F);
            float top = bottom - (tv(s, 160.0F) - tv(s, 44.0F)) * fill;
            if (fill > 0.0F) {
                PANEL.face(pose, buffer, u0, top, u1 - u0, bottom - top, 0.075F, colour);
            }
            centred(PANEL, pose, buffer, String.valueOf(Math.round(values[k])), tu(s, x[k]), tv(s, 22.0F), 0.17F,
                    r.colour(high ? AMBER : WHITE_RGB, 1.0F));
        }
    }

    /** The map: the compass rose turning with the heading, the drift line, the heading and ground speed. */
    private static void nav(PoseStack.Pose pose, VertexConsumer buffer, Readings r) {
        float[] s = mfd(3);
        float u0 = s[0];
        float v0 = s[1];
        float u1 = s[0] + s[2];
        float v1 = s[1] + s[3];
        float cu = tu(s, 128.0F);
        float cv = tv(s, 140.0F);
        float radius = s[2] * 92.0F / PAGE_W;
        int rose = r.colour(WHITE_RGB, 0.9F);
        for (int bearing = 0; bearing < 360; bearing += 10) {
            float a = (bearing - r.heading) * Mth.DEG_TO_RAD;
            float inner = radius - (bearing % 30 == 0 ? 0.22F : 0.12F);
            PANEL.line(pose, buffer, cu + Mth.sin(a) * inner, cv - Mth.cos(a) * inner, cu + Mth.sin(a) * radius,
                    cv - Mth.cos(a) * radius, 0.04F, 0.075F, rose, u0, v0, u1, v1);
            if (bearing % 30 == 0) {
                String label = switch (bearing) {
                    case 0 -> "N";
                    case 90 -> "E";
                    case 180 -> "S";
                    case 270 -> "W";
                    default -> String.valueOf(bearing / 10);
                };
                float h = bearing % 90 == 0 ? 0.22F : 0.17F;
                float lu = cu + Mth.sin(a) * (radius - 0.42F);
                float lv = cv - Mth.cos(a) * (radius - 0.42F);
                PANEL.text(pose, buffer, label, lu - CockpitPanel.textWidth(label, h) / 2, lv - h / 2, h, 0.075F,
                        r.colour(bearing == 0 ? AMBER : WHITE_RGB, 1.0F), u0, v0, u1, v1);
            }
        }
        PANEL.line(pose, buffer, cu, cv - radius - 0.02F, cu, cv - radius + 0.24F, 0.07F, 0.08F, r.colour(AMBER, 1.0F), u0, v0, u1, v1);
        int own = r.colour(WHITE_RGB, 1.0F);
        // The Chinook's symbol: a long body with both rotors.
        PANEL.line(pose, buffer, cu, cv - 0.3F, cu, cv + 0.3F, 0.09F, 0.08F, own, u0, v0, u1, v1);
        PANEL.line(pose, buffer, cu - 0.16F, cv - 0.22F, cu + 0.16F, cv - 0.22F, 0.04F, 0.08F, own, u0, v0, u1, v1);
        PANEL.line(pose, buffer, cu - 0.16F, cv + 0.22F, cu + 0.16F, cv + 0.22F, 0.04F, 0.08F, own, u0, v0, u1, v1);
        if (r.knots > 2.0F) {
            float a = (r.track - r.heading) * Mth.DEG_TO_RAD;
            for (float d = 0.36F; d < radius - 0.1F; d += 0.24F) {
                PANEL.line(pose, buffer, cu + Mth.sin(a) * d, cv - Mth.cos(a) * d, cu + Mth.sin(a) * (d + 0.13F),
                        cv - Mth.cos(a) * (d + 0.13F), 0.055F, 0.078F, r.colour(MAGENTA, 0.95F), u0, v0, u1, v1);
            }
        }
        centred(PANEL, pose, buffer, three(r.heading), cu, tv(s, 8.0F), 0.28F, own);
        PANEL.text(pose, buffer, String.valueOf(Math.round(r.knots)), tu(s, 36.0F), tv(s, 192.0F), 0.22F, 0.075F,
                r.colour(GREEN, 1.0F), u0, v0, u1, v1);
    }

    /**
     * The cargo page: the hook's state on the planform and its bar (grey stowed, amber hanging, green with a load),
     * the rope out (metres), the load (tonnes), the ramp's angle and bar (green shut, amber open), the crew door.
     */
    private static void cargo(PoseStack.Pose pose, VertexConsumer buffer, Readings r) {
        float[] s = mfd(1);
        int hookColour = switch (r.hook) {
            case ChinookSling.CARRYING -> GREEN;
            case ChinookSling.HANGING -> AMBER;
            default -> 0x8A9096;
        };
        float hu = tu(s, 70.0F);
        float hv = tv(s, 122.0F);
        PANEL.face(pose, buffer, hu - 0.1F, hv - 0.1F, 0.2F, 0.2F, 0.08F, r.colour(hookColour, 1.0F));
        bar(pose, buffer, s, 30.0F, r.colour(hookColour, 1.0F));
        if (r.hook != ChinookSling.STOWED) {
            PANEL.text(pose, buffer, String.format(java.util.Locale.ROOT, "%.1f", r.rope), tu(s, 196.0F), tv(s, 66.0F), 0.22F, 0.075F,
                    r.colour(WHITE_RGB, 1.0F), -100, -100, 100, 100);
        }
        if (r.loadTons > 0.0F) {
            PANEL.text(pose, buffer, String.format(java.util.Locale.ROOT, "%.1f", r.loadTons), tu(s, 196.0F), tv(s, 102.0F), 0.22F,
                    0.075F, r.colour(GREEN, 1.0F), -100, -100, 100, 100);
        }
        boolean rampShut = r.ramp > ChinookEntity.RAMP_CLOSED - 0.5F;
        bar(pose, buffer, s, 138.0F, r.colour(rampShut ? GREEN : AMBER, 1.0F));
        PANEL.text(pose, buffer, String.valueOf(Math.round(r.ramp)), tu(s, 196.0F), tv(s, 138.0F), 0.22F, 0.075F,
                r.colour(WHITE_RGB, 1.0F), -100, -100, 100, 100);
        bar(pose, buffer, s, 174.0F, r.colour(r.doorOpen ? AMBER : GREEN, 1.0F));
        // The ramp and door on the planform light amber while open.
        if (!rampShut) {
            PANEL.face(pose, buffer, tu(s, 58.0F), tv(s, 174.0F), tu(s, 82.0F) - tu(s, 58.0F), tv(s, 190.0F) - tv(s, 174.0F), 0.078F,
                    r.colour(AMBER, 0.8F));
        }
        if (r.doorOpen) {
            PANEL.face(pose, buffer, tu(s, 86.0F), tv(s, 56.0F), tu(s, 90.0F) - tu(s, 86.0F), tv(s, 70.0F) - tv(s, 56.0F), 0.078F,
                    r.colour(AMBER, 1.0F));
        }
    }

    /** A status bar under a cargo page label at page row y. */
    private static void bar(PoseStack.Pose pose, VertexConsumer buffer, float[] s, float y, int colour) {
        PANEL.face(pose, buffer, tu(s, 132.0F), tv(s, y + 18.0F), tu(s, 186.0F) - tu(s, 132.0F), tv(s, y + 26.0F) - tv(s, y + 18.0F),
                0.078F, colour);
    }

    /** Master caution and warning at each end, the caution lamps under the engine page. */
    private static void lamps(PoseStack.Pose pose, VertexConsumer buffer, Readings r) {
        boolean lowFuel = r.fuel < 0.2F;
        boolean damaged = r.hull < 0.5F;
        boolean rampOpen = r.ramp < ChinookEntity.RAMP_CLOSED - 0.5F;
        int[] colours = {
                r.rotor > 0.5F ? CockpitPanel.argb(GREEN, 0.85F * r.boot) : CockpitPanel.argb(AMBER, 0.9F * r.boot),
                r.rotor > 0.5F ? CockpitPanel.argb(GREEN, 0.85F * r.boot) : CockpitPanel.argb(AMBER, 0.9F * r.boot),
                damaged && (r.hull >= 0.25F || r.blink(2.5F)) ? CockpitPanel.argb(AMBER, 0.95F * r.boot) : LAMP_OFF,
                damaged ? CockpitPanel.argb(AMBER, 0.9F * r.boot) : LAMP_OFF,
                lowFuel && (r.fuel >= 0.1F || r.blink(2.0F)) ? CockpitPanel.argb(AMBER, 0.95F * r.boot) : LAMP_OFF,
                r.hook == ChinookSling.CARRYING ? CockpitPanel.argb(GREEN, 0.85F * r.boot)
                        : r.hook == ChinookSling.HANGING ? CockpitPanel.argb(AMBER, 0.9F * r.boot) : LAMP_OFF,
                rampOpen ? CockpitPanel.argb(AMBER, 0.9F * r.boot) : LAMP_OFF,
                r.doorOpen ? CockpitPanel.argb(AMBER, 0.9F * r.boot) : LAMP_OFF
        };
        for (int i = 0; i < 8; i++) {
            PANEL.face(pose, buffer, -6.3F + i * 1.6F, 7.25F, 1.3F, 0.55F, 0.06F, colours[i]);
        }
        boolean caution = lowFuel || damaged;
        boolean warning = r.hull < 0.25F;
        for (float u : new float[]{-20.1F, 18.8F}) {
            PANEL.face(pose, buffer, u, 7.05F, 1.3F, 0.75F, 0.06F, caution ? CockpitPanel.argb(AMBER, 0.95F * r.boot) : LAMP_OFF);
            PANEL.face(pose, buffer, u, 7.95F, 1.3F, 0.75F, 0.06F,
                    warning && r.blink(3.0F) ? CockpitPanel.argb(RED, 0.95F * r.boot) : LAMP_OFF);
        }
    }

    /** The console: both CDUs' pages (the heading, ground speed and height to hold), the radios, the hook window. */
    private static void console(PoseStack.Pose pose, VertexConsumer buffer, Readings r, int screen) {
        int green = r.colour(GREEN, 1.0F);
        int amber = r.colour(AMBER, 0.95F);
        for (int i = 0; i < 2; i++) {
            float[] c = CDU[i];
            CONSOLE.face(pose, buffer, c[0], c[1], c[2], c[3], 0.05F, darken(0x06090C, 1.0F));
            float h = 0.26F;
            CONSOLE.text(pose, buffer, three(r.heading), c[0] + 0.2F, c[1] + 0.2F, h, 0.075F, green, -100, -100, 100, 100);
            CONSOLE.text(pose, buffer, String.valueOf(Math.round(r.knots)), c[0] + 2.3F, c[1] + 0.2F, h, 0.075F, green,
                    -100, -100, 100, 100);
            CONSOLE.text(pose, buffer, String.valueOf(Math.round(r.baroFeet / 10.0F) * 10), c[0] + 0.2F, c[1] + 0.85F, h, 0.075F,
                    r.colour(CYAN, 1.0F), -100, -100, 100, 100);
            CONSOLE.text(pose, buffer, String.valueOf(Math.round(r.fuel * 100.0F)), c[0] + 2.3F, c[1] + 0.85F, h, 0.075F,
                    r.colour(WHITE_RGB, 1.0F), -100, -100, 100, 100);
            CONSOLE.text(pose, buffer, "251.75", c[0] + 0.2F, c[1] + 1.5F, h * 0.85F, 0.075F, amber, -100, -100, 100, 100);
            float[] radio = RADIO[i];
            CONSOLE.text(pose, buffer, i == 0 ? "251.75" : "138.40", radio[0] + 0.2F, radio[1] + 0.15F, 0.4F, 0.06F, amber,
                    -100, -100, 100, 100);
        }
        int hookColour = r.hook == ChinookSling.CARRYING ? GREEN : r.hook == ChinookSling.HANGING ? AMBER : 0x8A9096;
        CONSOLE.face(pose, buffer, HOOK_WINDOW[0] + 0.1F, HOOK_WINDOW[1] + 0.1F, 0.4F, 0.4F, 0.06F, r.colour(hookColour, 1.0F));
        if (r.hook != ChinookSling.STOWED) {
            CONSOLE.text(pose, buffer, String.format(java.util.Locale.ROOT, "%.1f", r.rope), HOOK_WINDOW[0] + 0.7F, HOOK_WINDOW[1] + 0.12F,
                    0.36F, 0.06F, r.colour(WHITE_RGB, 1.0F), -100, -100, 100, 100);
        }
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
