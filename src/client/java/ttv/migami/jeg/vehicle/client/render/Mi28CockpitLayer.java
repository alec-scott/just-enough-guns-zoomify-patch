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
 * The Mi-28's front panel in the pilot's (rear) cockpit, Soviet style: turquoise paint and amber displays rather than the
 * A-10's grey and green. Two displays under the panel's hoods (a moving map, its next waypoint blinking, and an engine
 * page whose engine and rotor bars rise with the rotor), round backlit gauges with live needles below them (rotor speed in
 * the middle; airspeed, height, climb and engine temperature either side), a row of lamps, and an amber readout on the
 * sight unit in the middle. Above it, the model's head-up display frame holds a faint green combiner with a horizon and
 * pitch ladder that bank and pitch with the helicopter, open in the middle so the crosshair stays clear, and a heading
 * scale that runs with the turn. With power the screens flicker on; the HUD goes with the canopy glass when that is shot
 * away.
 */
final class Mi28CockpitLayer<T extends VehicleEntity> extends GeoRenderLayer<T, Void, VehicleRenderState> {
    private static final Identifier PANEL_TEXTURE = A10CockpitLayer.id("mi28_cockpit_panel");
    private static final Identifier MAP = A10CockpitLayer.id("mi28_mfd_map");
    private static final Identifier ENGINES = A10CockpitLayer.id("mi28_mfd_eng");
    private static final Identifier GAUGE = A10CockpitLayer.id("mi28_gauge");
    private static final Identifier READOUT = A10CockpitLayer.id("mi28_center");
    private static final Identifier WHITE = A10CockpitLayer.id("screen_white");

    /** The panel block's face, towards the pilot: its top edge's centre, then across, down it and out. */
    private static final CockpitPanel PANEL = new CockpitPanel(new Vector3f(0.0F, 53.0F, -50.8F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -9.8F, 0.4F), new Vector3f(0.0F, 0.4F, 9.8F));
    /** The face of the sight unit standing out of the panel's middle. */
    private static final CockpitPanel SIGHT_UNIT = new CockpitPanel(new Vector3f(0.0F, 53.0F, -47.0F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F));
    /** The combiner glass in the model's HUD frame, leaning back towards the pilot. */
    private static final CockpitPanel HUD = new CockpitPanel(new Vector3f(0.0F, 57.6F, -45.9F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -3.6F, -3.7F), new Vector3f(0.0F, -3.7F, 3.6F));

    private static final float HALF_WIDTH = 6.6F;
    private static final float SCREEN = 3.6F;
    private static final float SCREEN_TOP = 0.7F;
    private static final float LEFT_SCREEN = -6.3F;
    private static final float RIGHT_SCREEN = 2.7F;
    private static final int AMBER = 0xFFB040;
    private static final int HUD_GREEN = 0x6CFF8C;
    /** Where the pilot's line of sight meets the combiner, down it. */
    private static final float HUD_BORESIGHT = 0.9F;
    private static final float BOOT_TICKS = 24.0F;
    /** Rotor speed (as the vehicle syncs it) that reads 100%. */
    private static final float ROTOR_FULL = 0.1F;
    private static final double MAX_DISTANCE_SQ = 48.0D * 48.0D;

    private static final Map<VehicleEntity, Integer> POWERED_SINCE = new WeakHashMap<>();

    Mi28CockpitLayer(GeoRenderer<T, Void, VehicleRenderState> renderer) {
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
        collector.submitCustomGeometry(modelPose, RenderTypes.entityCutout(PANEL_TEXTURE), (pose, buffer) ->
                CockpitPanel.quad(pose, buffer, PANEL.p(-HALF_WIDTH, 0.4F, 0.12F), PANEL.p(HALF_WIDTH, 0.4F, 0.12F),
                        PANEL.p(HALF_WIDTH, 9.8F, 0.12F), PANEL.p(-HALF_WIDTH, 9.8F, 0.12F), 0.0F, 0.0F, 1.0F, 1.0F,
                        0xFFFFFFFF, light, PANEL.out));

        if (!AircraftLights.isPowered(heli)) {
            POWERED_SINCE.remove(heli);
            return;
        }
        int since = POWERED_SINCE.computeIfAbsent(heli, h -> h.tickCount);
        float boot = CockpitPanel.bootLevel((heli.tickCount - since + partialTick) / BOOT_TICKS);
        if (boot <= 0.0F) {
            return;
        }
        float level = boot * Mth.lerp(AircraftLights.nightSwitch(partialTick), 1.0F, 0.85F);
        int screen = CockpitPanel.grey(level);

        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(MAP), (pose, buffer) ->
                PANEL.face(pose, buffer, LEFT_SCREEN, SCREEN_TOP, SCREEN, SCREEN, 0.3F, screen));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(ENGINES), (pose, buffer) ->
                PANEL.face(pose, buffer, RIGHT_SCREEN, SCREEN_TOP, SCREEN, SCREEN, 0.3F, screen));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(READOUT), (pose, buffer) ->
                SIGHT_UNIT.face(pose, buffer, -2.1F, 0.5F, 4.2F, 2.1F, 0.08F, screen));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(GAUGE), (pose, buffer) -> {
            PANEL.face(pose, buffer, -1.1F, 3.4F, 2.2F, 2.2F, 0.25F, screen);
            for (float u : new float[]{-5.4F, -3.6F, 3.6F, 5.4F}) {
                PANEL.face(pose, buffer, u - 0.8F, 4.7F, 1.6F, 1.6F, 0.25F, screen);
            }
        });

        // What the needles, bars and HUD show.
        float rotor = Mth.clamp(heli.propellerSpeed() / ROTOR_FULL, 0.0F, 1.08F);
        Vec3 motion = heli.getDeltaMovement();
        float airspeed = (float) (Math.sqrt(motion.x * motion.x + motion.z * motion.z) * 20.0D * 3.6D);
        float climb = (float) (motion.y * 20.0D);
        float height = (float) heli.altitudeAboveGround();
        Vec3 angles = heli.poseAngles(partialTick);
        float yaw = (float) angles.x;
        float pitchUp = (float) -angles.y;
        float bank = (float) angles.z;
        float seconds = (heli.level().getGameTime() + partialTick) / 20.0F;
        boolean hud = !heli.isWrecked() && heli.vehicleHealth() >= heli.maxVehicleHealth() * 0.35F;
        collector.order(2).submitCustomGeometry(modelPose, RenderTypes.eyes(WHITE), (pose, buffer) -> {
            needles(pose, buffer, rotor, airspeed, height, climb, level);
            engineBars(pose, buffer, rotor, seconds, level);
            mapBlink(pose, buffer, seconds, level);
            lamps(pose, buffer, seconds, boot);
            if (hud) {
                hud(pose, buffer, pitchUp, bank, yaw, level);
            }
        });
    }

    /** The gauges' needles: each sweeps 270 degrees from bottom left, through the top, to bottom right. */
    private static void needles(PoseStack.Pose pose, VertexConsumer buffer, float rotor, float airspeed, float height,
                                float climb, float level) {
        int white = CockpitPanel.argb(0xF4EEDC, level);
        float sweep = 270.0F * Mth.DEG_TO_RAD;
        float start = -135.0F * Mth.DEG_TO_RAD;
        PANEL.needle(pose, buffer, 0.0F, 4.5F, 0.85F, start + sweep * Mth.clamp(rotor / 1.08F, 0.0F, 1.0F), 0.09F, 0.33F, white);
        float[] readings = {
                Mth.clamp(airspeed / 300.0F, 0.0F, 1.0F),
                Mth.clamp(height / 200.0F, 0.0F, 1.0F),
                Mth.clamp(0.5F + climb / 20.0F, 0.0F, 1.0F),
                Mth.clamp(0.15F + 0.7F * rotor, 0.0F, 1.0F)
        };
        float[] centres = {-5.4F, -3.6F, 3.6F, 5.4F};
        for (int i = 0; i < 4; i++) {
            PANEL.needle(pose, buffer, centres[i], 5.5F, 0.62F, start + sweep * readings[i], 0.07F, 0.33F, white);
        }
    }

    /** The engine page's bars: both engines and the rotor, rising with the rotor, the engines flickering a little. */
    private static void engineBars(PoseStack.Pose pose, VertexConsumer buffer, float rotor, float seconds, float level) {
        float[] fills = {
                Mth.clamp(0.12F + 0.78F * rotor + 0.015F * Mth.sin(seconds * 7.0F), 0.0F, 1.0F),
                Mth.clamp(0.12F + 0.78F * rotor + 0.015F * Mth.sin(seconds * 6.1F + 1.0F), 0.0F, 1.0F),
                Mth.clamp(rotor * 0.86F, 0.0F, 1.0F)
        };
        int colour = CockpitPanel.argb(AMBER, 0.85F * level);
        for (int i = 0; i < 3; i++) {
            // Inside the bar outline drawn in the texture (x 35-45, y 27-89 of 128).
            float x = 35.0F + i * 22.0F;
            float u0 = RIGHT_SCREEN + SCREEN * x / 128.0F;
            float u1 = RIGHT_SCREEN + SCREEN * (x + 10.0F) / 128.0F;
            float vBottom = SCREEN_TOP + SCREEN * 89.0F / 128.0F;
            float vTop = vBottom - SCREEN * 62.0F / 128.0F * fills[i];
            PANEL.face(pose, buffer, u0, vTop, u1 - u0, vBottom - vTop, 0.34F, colour);
        }
    }

    /** The next waypoint on the map, blinking. */
    private static void mapBlink(PoseStack.Pose pose, VertexConsumer buffer, float seconds, float level) {
        if (((int) (seconds * 2.0F) & 1) == 0) {
            return;
        }
        float u = LEFT_SCREEN + SCREEN * 72.0F / 128.0F;
        float v = SCREEN_TOP + SCREEN * 34.0F / 128.0F;
        PANEL.face(pose, buffer, u - 0.12F, v - 0.12F, 0.24F, 0.24F, 0.34F, CockpitPanel.argb(0xFFE6A8, level));
    }

    /** A row of lamps under the gauges: a few green ones lit, and an amber one blinking. */
    private static void lamps(PoseStack.Pose pose, VertexConsumer buffer, float seconds, float boot) {
        for (int i = 0; i < 12; i++) {
            float u = -5.8F + i * 1.05F;
            int colour;
            if (i == 2 || i == 3 || i == 9) {
                colour = CockpitPanel.argb(0x50FF70, 0.8F * boot);
            } else if (i == 6 && ((int) (seconds * 1.5F) & 1) == 0) {
                colour = CockpitPanel.argb(0xFFB030, 0.9F * boot);
            } else {
                colour = 0xFF1A1E1E;
            }
            PANEL.face(pose, buffer, u - 0.2F, 7.0F, 0.4F, 0.4F, 0.25F, colour);
        }
    }

    /**
     * The head-up display: a faint green tint on the combiner, the horizon and pitch ladder (conformal, about where the
     * pilot's line of sight meets the glass), and the heading scale along the top running with the turn.
     */
    private static void hud(PoseStack.Pose pose, VertexConsumer buffer, float pitchUp, float bank, float yaw, float level) {
        float u0 = -2.55F;
        float u1 = 2.55F;
        float v0 = 0.15F;
        float v1 = 4.8F;
        HUD.face(pose, buffer, u0, v0, u1 - u0, v1 - v0, 0.0F, CockpitPanel.argb(HUD_GREEN, 0.05F * level));
        HUD.attitude(pose, buffer, 0.0F, HUD_BORESIGHT, u0, v0 + 0.5F, u1, v1, pitchUp, bank, 0.27F, 0.02F, HUD_GREEN,
                0.55F * level, 0.0F, 0.6F, 0.06F);
        // Heading ticks every 5 degrees, a long one every 10.
        int colour = CockpitPanel.argb(HUD_GREEN, 0.5F * level);
        float spacing = 0.12F;
        float offset = Mth.positiveModulo(yaw, 5.0F);
        for (int i = -4; i <= 4; i++) {
            float u = (i * 5.0F - offset) * spacing;
            boolean ten = Math.floorMod(Math.round((yaw - offset) / 5.0F) + i, 2) == 0;
            HUD.line(pose, buffer, u, v0 + 0.05F, u, v0 + (ten ? 0.38F : 0.24F), 0.035F, 0.02F, colour, u0, v0, u1, v1);
        }
        HUD.line(pose, buffer, 0.0F, v0 + 0.42F, 0.0F, v0 + 0.55F, 0.05F, 0.02F, colour, u0, v0, u1, v1);
    }
}
