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
import org.joml.Vector3f;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;

/**
 * The FSV-6's cab instruments, as the helicopters' cockpits: drawn live on the model's binnacle and status screen
 * ({@code design/support/generate.py}: BINNACLE, CODRIVER_SCREEN; textures from {@code make_cockpit.py}).
 * <ul>
 *     <li>The driver's binnacle on the dash top, over the steering wheel: a backlit tachometer (left) and speedometer
 *     (right) with needles that follow the diesel and the road speed, and between them eight warning lamps: brake,
 *     reverse, battery low, glow plug (while cranking), roof beacons, tow (blinking with a vehicle in reach, steady in
 *     tow), jacks out (not packed) and engine running.</li>
 *     <li>The co-driver's green status screen on the dash face: battery, hold and body (unfold) bars, the beacon's level
 *     as four pips, and the tow lamp.</li>
 *     <li>In the room, on the partition behind the cab (while the body is out and the generator runs): the support
 *     console's screen (power source lamps, battery, hold and room light bars) and its three dials (battery, hold,
 *     generator load); the beacon terminal's screen (the chosen effect's icon, the level as four pips, the beam
 *     pulsing while lit, the rack's level) and its four level lamps.</li>
 * </ul>
 * With power the screens flicker on; the dials are backlit (full bright), and dim a little at night.
 */
final class SupportCockpitLayer extends GeoRenderLayer<SupportVehicleEntity, Void, VehicleRenderState> {
    private static final Identifier BINNACLE = A10CockpitLayer.id("fsv6_binnacle");
    private static final Identifier TACHO = A10CockpitLayer.id("fsv6_tacho");
    private static final Identifier SPEEDO = A10CockpitLayer.id("fsv6_speedo");
    private static final Identifier LAMPS = A10CockpitLayer.id("fsv6_lamps");
    private static final Identifier STATUS = A10CockpitLayer.id("fsv6_status");
    private static final Identifier WHITE = A10CockpitLayer.id("screen_white");
    private static final Identifier WALL_CONSOLE = A10CockpitLayer.id("fsv6_wall_console");
    private static final Identifier WALL_BEACON = A10CockpitLayer.id("fsv6_wall_beacon");

    /**
     * The partition's two panels (render space; generate.py: "console" at model x -8, "beacon terminal" at +8), facing
     * the room: their top edge's centre (y FLOOR + 27), then the viewer's right, down and out into the room.
     */
    private static final CockpitPanel CONSOLE_PANEL = new CockpitPanel(new Vector3f(8.0F, 43.0F, -22.36F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F));
    private static final CockpitPanel BEACON_PANEL = new CockpitPanel(new Vector3f(-8.0F, 43.0F, -22.36F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F));
    /** Each panel's screen: 11.6 x 6 px, 1.5 px down; its texture 232 x 120. */
    private static final float WALL_SCREEN_HALF = 5.8F, WALL_SCREEN_TOP = 1.5F, WALL_SCREEN_HEIGHT = 6.0F, WALL_PX = 11.6F / 232.0F;

    /** The binnacle's face (render space: the model's X negated): its top edge's centre, then the driver's right, down and out. */
    private static final CockpitPanel DASH = new CockpitPanel(new Vector3f(-10.0F, 35.7F, -57.44F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F));
    private static final float DASH_HALF = 6.6F, DASH_HEIGHT = 4.4F;
    private static final float DIAL_U = 3.9F, DIAL_V = 2.2F, DIAL_R = 2.0F;
    /** The co-driver's status screen: 12 x 5 px, its texture 240 x 100. */
    private static final CockpitPanel STATUS_SCREEN = new CockpitPanel(new Vector3f(10.0F, 29.0F, -56.86F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F), new Vector3f(0.0F, 0.0F, 1.0F));
    private static final float STATUS_HALF = 6.0F, STATUS_HEIGHT = 5.0F, STATUS_PX = 12.0F / 240.0F;

    private static final int NEEDLE = 0xFF8A3C, PHOSPHOR = 0x7CFF9A;
    private static final int[] LAMP_COLOURS = {0xFF3A2A, 0xF4F0E0, 0xFF3A2A, 0xFFB030, 0xFFB030, 0xFFB030, 0xFFB030, 0x50FF70};
    private static final float BOOT_TICKS = 20.0F;
    private static final double MAX_DISTANCE_SQ = 32.0D * 32.0D;

    private static final Map<SupportVehicleEntity, Integer> POWERED_SINCE = new WeakHashMap<>();
    /** Needle positions (tacho, speedo, 0..1), eased towards their readings frame by frame. */
    private static final Map<SupportVehicleEntity, float[]> NEEDLES = new WeakHashMap<>();
    /** Whether a vehicle is in reach of the hitch, checked once a tick: [game time, result]. */
    private static final Map<SupportVehicleEntity, long[]> TOW_READY = new WeakHashMap<>();

    SupportCockpitLayer(GeoRenderer<SupportVehicleEntity, Void, VehicleRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void submitRenderTask(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        if (!(passInfo.renderState().vehicle instanceof SupportVehicleEntity truck) || passInfo.renderState().distanceToCameraSq > MAX_DISTANCE_SQ) {
            return;
        }
        float partialTick = Mth.clamp(passInfo.renderState().ageInTicks - truck.tickCount, 0.0F, 1.0F);
        PoseStack modelPose = new PoseStack();
        modelPose.last().set(passInfo.getModelRenderMatrixPose());
        int light = passInfo.packedLight();
        collector.submitCustomGeometry(modelPose, RenderTypes.entityCutout(BINNACLE), (pose, buffer) ->
                CockpitPanel.quad(pose, buffer, DASH.p(-DASH_HALF, 0.0F, 0.04F), DASH.p(DASH_HALF, 0.0F, 0.04F),
                        DASH.p(DASH_HALF, DASH_HEIGHT, 0.04F), DASH.p(-DASH_HALF, DASH_HEIGHT, 0.04F), 0.0F, 0.0F, 1.0F, 1.0F,
                        0xFFFFFFFF, light, DASH.out));

        // Each set of instruments is drawn only for the players who can read it: the room's panels for players in the
        // room, the cab's for those aboard.
        var viewer = net.minecraft.client.Minecraft.getInstance().player;
        if (!truck.isPacked() && truck.generatorRunning() && viewer != null && ttv.migami.jeg.vehicle.support.SupportShell.isInside(truck, viewer)) {
            this.wallPanels(collector, modelPose, truck, partialTick);
        }
        if (viewer == null || viewer.getVehicle() != truck) {
            return;
        }
        if (!truck.powered()) {
            POWERED_SINCE.remove(truck);
            NEEDLES.remove(truck);
            return;
        }
        int since = POWERED_SINCE.computeIfAbsent(truck, t -> t.tickCount);
        float boot = CockpitPanel.bootLevel((truck.tickCount - since + partialTick) / BOOT_TICKS);
        if (boot <= 0.0F) {
            return;
        }
        float level = boot * Mth.lerp(AircraftLights.nightSwitch(partialTick), 1.0F, 0.8F);
        int screen = CockpitPanel.grey(level);
        float seconds = (truck.level().getGameTime() + partialTick) / 20.0F;

        // Readings.
        double dx = truck.getX() - truck.xo, dz = truck.getZ() - truck.zo;
        float kmh = (float) (Math.sqrt(dx * dx + dz * dz) * 20.0D * 3.6D);
        boolean cranking = truck.isPacked() && !truck.engineRunning();
        float rpm = truck.engineRunning() ? 650.0F + 1750.0F * Mth.clamp(kmh / 90.0F, 0.0F, 1.0F) + 25.0F * Mth.sin(seconds * 9.0F)
                : cranking ? 200.0F + 70.0F * Mth.sin(seconds * 31.0F) : 0.0F;
        float[] needles = NEEDLES.computeIfAbsent(truck, t -> new float[2]);
        needles[0] = Mth.lerp(0.18F, needles[0], Mth.clamp(rpm / 3000.0F, 0.0F, 1.0F));
        needles[1] = Mth.lerp(0.18F, needles[1], Mth.clamp(kmh / 100.0F, 0.0F, 1.0F));
        float battery = truck.maxVehicleEnergy() <= 0 ? 0.0F : truck.vehicleEnergy() / (float) truck.maxVehicleEnergy();
        boolean blink = ((int) (seconds * 2.0F) & 1) == 0;
        boolean towing = truck.towedEntityId() >= 0;
        boolean[] lamps = {
                truck.braking(), truck.reversing(), battery < 0.15F, cranking, truck.engineRunning(),
                towing || (blink && towReady(truck)), !truck.isPacked(), truck.engineRunning()};
        float hold = truck.occupiedCargoSlots() / (float) Math.max(1, truck.vehicleContainerSlots());
        float body = truck.deployProgress(partialTick);
        int beaconLevel = truck.fittedForDisplay(ttv.migami.jeg.vehicle.support.SupportModules.ROOF).isEmpty() ? 0 : truck.beaconLevelForDisplay();

        // Backlit faces.
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(TACHO), (pose, buffer) ->
                DASH.face(pose, buffer, -DIAL_U - DIAL_R, DIAL_V - DIAL_R, 2 * DIAL_R, 2 * DIAL_R, 0.1F, screen));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(SPEEDO), (pose, buffer) ->
                DASH.face(pose, buffer, DIAL_U - DIAL_R, DIAL_V - DIAL_R, 2 * DIAL_R, 2 * DIAL_R, 0.1F, screen));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(STATUS), (pose, buffer) ->
                STATUS_SCREEN.face(pose, buffer, -STATUS_HALF, 0.0F, 2 * STATUS_HALF, STATUS_HEIGHT, 0.04F, screen));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(LAMPS), (pose, buffer) -> {
            for (int i = 0; i < 8; i++) {
                float u = (i % 2 == 0 ? -1.05F : 0.25F), v = 0.45F + (i / 2) * 0.9F, size = 0.8F;
                int colour = lamps[i] ? CockpitPanel.argb(LAMP_COLOURS[i], level) | 0xFF000000 : 0xFF2A2C2C;
                CockpitPanel.quad(pose, buffer, DASH.p(u, v, 0.1F), DASH.p(u + size, v, 0.1F), DASH.p(u + size, v + size, 0.1F),
                        DASH.p(u, v + size, 0.1F), i / 8.0F, 0.0F, (i + 1) / 8.0F, 1.0F, colour, CockpitPanel.FULL_BRIGHT, DASH.out);
            }
        });

        // Needles, bars and pips.
        collector.order(2).submitCustomGeometry(modelPose, RenderTypes.eyes(WHITE), (pose, buffer) -> {
            int needle = CockpitPanel.argb(NEEDLE, level);
            float sweep = 270.0F * Mth.DEG_TO_RAD, start = -135.0F * Mth.DEG_TO_RAD;
            DASH.needle(pose, buffer, -DIAL_U, DIAL_V, DIAL_R * 0.86F, start + sweep * needles[0], 0.11F, 0.18F, needle);
            DASH.needle(pose, buffer, DIAL_U, DIAL_V, DIAL_R * 0.86F, start + sweep * needles[1], 0.11F, 0.18F, needle);
            int phosphor = CockpitPanel.argb(PHOSPHOR, 0.9F * level);
            bar(pose, buffer, 30, battery, battery < 0.15F && blink ? CockpitPanel.argb(0xFFB040, level) : phosphor);
            bar(pose, buffer, 48, hold, hold >= 1.0F ? CockpitPanel.argb(0xFFB040, level) : phosphor);
            bar(pose, buffer, 66, body, phosphor);
            for (int k = 0; k < 4; k++) {
                if (beaconLevel > k) statusRect(pose, buffer, 64 + k * 22, 86, 13, 7, phosphor);
            }
            if (towing || (blink && towReady(truck))) statusRect(pose, buffer, 208, 86, 13, 7, CockpitPanel.argb(0xFFB040, level));
        });
    }

    /** The room's two wall panels, live while the generator runs (the body is out). */
    private void wallPanels(SubmitNodeCollector collector, PoseStack modelPose, SupportVehicleEntity truck, float partialTick) {
        float seconds = (truck.level().getGameTime() + partialTick) / 20.0F;
        float level = Mth.lerp(AircraftLights.nightSwitch(partialTick), 1.0F, 0.85F);
        int screen = CockpitPanel.grey(level);
        int phosphor = CockpitPanel.argb(PHOSPHOR, 0.9F * level), amber = CockpitPanel.argb(0xFFB040, level);
        float battery = truck.maxVehicleEnergy() <= 0 ? 0.0F : truck.vehicleEnergy() / (float) truck.maxVehicleEnergy();
        float hold = truck.occupiedCargoSlots() / (float) Math.max(1, truck.vehicleContainerSlots());
        float light = ttv.migami.jeg.vehicle.support.SupportModules.lampLight(truck.fittedForDisplay(ttv.migami.jeg.vehicle.support.SupportModules.LAMP)) / 15.0F;
        float load = 0.55F + 0.06F * Mth.sin(seconds * 2.3F) + 0.03F * Mth.sin(seconds * 7.1F);
        boolean beaconFitted = !truck.fittedForDisplay(ttv.migami.jeg.vehicle.support.SupportModules.ROOF).isEmpty();
        int beaconLevel = beaconFitted ? truck.beaconLevelForDisplay() : 0;
        boolean lit = truck.beaconLit();
        var effect = truck.beaconEffectForDisplay() > 0 ? net.minecraft.world.inventory.BeaconMenu.decodeEffect(truck.beaconEffectForDisplay()) : null;
        // The effect shows as its own colour (a vanilla effect icon file drawn here as a texture of its own broke the
        // other faces' textures in the same pass).
        int effectColour = effect == null ? 0 : effect.value().getColor() & 0xFFFFFF;

        // Screens and dials (backlit).
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(WALL_CONSOLE), (pose, buffer) ->
                CONSOLE_PANEL.face(pose, buffer, -WALL_SCREEN_HALF, WALL_SCREEN_TOP, 2 * WALL_SCREEN_HALF, WALL_SCREEN_HEIGHT, 0.02F, screen));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(WALL_BEACON), (pose, buffer) ->
                BEACON_PANEL.face(pose, buffer, -WALL_SCREEN_HALF, WALL_SCREEN_TOP, 2 * WALL_SCREEN_HALF, WALL_SCREEN_HEIGHT, 0.02F, screen));

        collector.order(2).submitCustomGeometry(modelPose, RenderTypes.eyes(WHITE), (pose, buffer) -> {
            // The console: power source lamps (generator on; diesel off while deployed; battery low), bars, needles.
            wallRect(CONSOLE_PANEL, pose, buffer, 79, 35, 10, 8, phosphor);
            if (battery < 0.15F) wallRect(CONSOLE_PANEL, pose, buffer, 175, 35, 10, 8, amber);
            wallBar(CONSOLE_PANEL, pose, buffer, 54, battery, battery < 0.15F ? amber : phosphor);
            wallBar(CONSOLE_PANEL, pose, buffer, 74, hold, hold >= 1.0F ? amber : phosphor);
            wallBar(CONSOLE_PANEL, pose, buffer, 94, light, CockpitPanel.argb(0xFFC27A, level));
            int needle = CockpitPanel.argb(NEEDLE, level);
            float sweep = 270.0F * Mth.DEG_TO_RAD, start = -135.0F * Mth.DEG_TO_RAD;
            float[] readings = {battery, hold, load};
            float[] us = {-4.0F, 0.0F, 4.0F};
            // The dials are drawn, not textured (a third texture in this pass came out on the other faces): a dark face,
            // a backlit ring of ticks every eighth of the sweep, the top eighth red.
            for (float uc : us) {
                java.util.List<float[]> disc = new java.util.ArrayList<>();
                for (int k = 0; k < 16; k++) {
                    double a = k * Math.PI * 2 / 16;
                    disc.add(new float[]{uc + (float) Math.sin(a) * 1.25F, 10.2F - (float) Math.cos(a) * 1.25F});
                }
                CONSOLE_PANEL.fill(pose, buffer, disc, 0.05F, 0xFF101212);
                for (int k = 0; k <= 8; k++) {
                    float a = start + sweep * k / 8.0F;
                    int tick = k == 8 ? CockpitPanel.argb(0xFF4030, level) : CockpitPanel.argb(0xEDE6D2, 0.85F * level);
                    CONSOLE_PANEL.line(pose, buffer, uc + Mth.sin(a) * 0.9F, 10.2F - Mth.cos(a) * 0.9F, uc + Mth.sin(a) * 1.15F,
                            10.2F - Mth.cos(a) * 1.15F, k % 2 == 0 ? 0.09F : 0.05F, 0.07F, tick, -1000F, -1000F, 1000F, 1000F);
                }
            }
            for (int i = 0; i < 3; i++) {
                CONSOLE_PANEL.needle(pose, buffer, us[i], 10.2F, 1.05F, start + sweep * Mth.clamp(readings[i], 0.0F, 1.0F), 0.08F, 0.1F, needle);
            }
            // The beacon terminal: level pips and lamps, the beam pulsing while lit, the rack's level.
            for (int k = 0; k < 4; k++) {
                if (beaconLevel > k) wallRect(BEACON_PANEL, pose, buffer, 82 + k * 36, 50, 25, 11, amber);
                // Lit only: an "off" colour drawn emissive came out as pale beige squares under shaders; off, the
                // model's dark lens shows.
                float u = -4.5F + 3.0F * k;
                if (beaconLevel > k) BEACON_PANEL.face(pose, buffer, u - 0.95F, 9.45F, 1.9F, 1.5F, 0.3F, amber);
            }
            if (lit) {
                float pulse = 0.55F + 0.45F * (0.5F + 0.5F * Mth.sin(seconds * 3.0F));
                wallRect(BEACON_PANEL, pose, buffer, 130, 73, 90, 9, CockpitPanel.argb(0x9CE8FF, pulse * level));
            }
            wallRect(BEACON_PANEL, pose, buffer, 60, 97, 160 * beaconLevel / 4.0F, 9, beaconLevel >= 4 ? phosphor : amber);
            if (effect != null) {
                // The chosen effect's colour in the icon box, a diamond in it like the beacon beam's glint.
                wallRect(BEACON_PANEL, pose, buffer, 14, 36, 44, 44, CockpitPanel.argb(effectColour, (lit ? 0.9F : 0.4F) * level));
                wallRect(BEACON_PANEL, pose, buffer, 30, 52, 12, 12, CockpitPanel.argb(0xFFFFFF, (lit ? 0.8F : 0.3F) * level));
            }
        });
    }

    private static float wallU(float x) {
        return -WALL_SCREEN_HALF + x * WALL_PX;
    }

    private static float wallV(float y) {
        return WALL_SCREEN_TOP + y * WALL_PX;
    }

    /** A rectangle on a wall panel's screen, in its texture's pixels. */
    private static void wallRect(CockpitPanel panel, PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float w, float h, int colour) {
        if (w <= 0.5F) return;
        panel.face(pose, buffer, wallU(x), wallV(y), w * WALL_PX, h * WALL_PX, 0.04F, colour);
    }

    /** A wall screen's bar fill (x 78..220, the row's top y, 10 high). */
    private static void wallBar(CockpitPanel panel, PoseStack.Pose pose, VertexConsumer buffer, int y, float fill, int colour) {
        wallRect(panel, pose, buffer, 78, y + 1, 142 * Mth.clamp(fill, 0.0F, 1.0F), 9, colour);
    }

    /** A status bar's fill (texture pixels: x 62..196, its row's top y + 1, 10 high). */
    private static void bar(PoseStack.Pose pose, VertexConsumer buffer, int y, float fill, int colour) {
        float width = 134.0F * Mth.clamp(fill, 0.0F, 1.0F);
        if (width > 0.5F) statusRect(pose, buffer, 62, y + 1, width, 9, colour);
    }

    private static void statusRect(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float w, float h, int colour) {
        STATUS_SCREEN.face(pose, buffer, -STATUS_HALF + x * STATUS_PX, y * STATUS_PX, w * STATUS_PX, h * STATUS_PX, 0.06F, colour);
    }

    private static boolean towReady(SupportVehicleEntity truck) {
        long now = truck.level().getGameTime();
        long[] cached = TOW_READY.computeIfAbsent(truck, t -> new long[]{-1, 0});
        if (cached[0] != now) {
            cached[0] = now;
            cached[1] = truck.towedEntityId() < 0 && truck.towCandidate() != null ? 1 : 0;
        }
        return cached[1] == 1;
    }
}
