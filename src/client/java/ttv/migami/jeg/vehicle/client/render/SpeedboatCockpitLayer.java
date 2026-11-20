package ttv.migami.jeg.vehicle.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.geckolib.util.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Vector3f;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The speedboat's helm console, a boat's take on the helicopters' panels (the user's review, 2026-11-18): on the sloped
 * face in front of the wheel, a chart plotter (left) whose compass ring turns heading-up round the boat's own mark, an
 * engine page (right) with live bars for revs, fuel, trim and temperature, a tachometer and a speedometer in knots with
 * live needles between them, a compass bowl, and a row of lamps (engine, navigation lights, on the plane, low fuel, hull
 * damage, searchlight). With the engine running the screens flicker on. Textures: design/speedboat/make_console.py,
 * whose LAYOUT matches the places here.
 */
final class SpeedboatCockpitLayer<T extends VehicleEntity> extends GeoRenderLayer<T, Void, VehicleRenderState> {
    private static final Identifier FACE = A10CockpitLayer.id("speedboat_console");
    private static final Identifier CHART = A10CockpitLayer.id("speedboat_chart");
    private static final Identifier ENGINE = A10CockpitLayer.id("speedboat_engine");
    private static final Identifier TACHO = A10CockpitLayer.id("speedboat_tacho");
    private static final Identifier SPEEDO = A10CockpitLayer.id("speedboat_speedo");
    private static final Identifier WHITE = A10CockpitLayer.id("screen_white");

    /**
     * The console's sloped face (the top of the dash block in superstructure, tilted 45 degrees towards the helm): its
     * top edge's centre, then across, down the slope towards the helmsman and out of it. 15 x 7.8 px.
     */
    private static final CockpitPanel FACE_PANEL = new CockpitPanel(new Vector3f(0.0F, 25.95F, -17.74F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -1.0F, 1.0F), new Vector3f(0.0F, 1.0F, 1.0F));
    /** The bone the dash belongs to: it pitches with the hull as the bow rises on the plane. */
    private static final String DASH_BONE = "superstructure";
    private static final float HALF_WIDTH = 7.5F;
    private static final float HEIGHT = 7.8F;

    // The places in make_console.py's LAYOUT (model pixels: u from the face's centre, v down from its top edge).
    private static final float SCREEN = 4.3F;
    private static final float SCREEN_V = 0.45F;
    private static final float CHART_U = -7.0F;
    private static final float ENGINE_U = 2.7F;
    private static final float DIAL_U = 1.3F;
    private static final float DIAL_V = 2.0F;
    private static final float DIAL_R = 1.05F;
    private static final float COMPASS_V = 4.55F;
    private static final float LAMP_V = 5.45F;
    private static final float LAMP_SIZE = 0.7F;
    private static final float[] LAMP_U = {-6.6F, -5.55F, -4.5F, -3.45F, -2.4F, -1.35F};
    /** The engine page's bars, in its 172-texel texture: their rows' tops, and where they run across. */
    private static final float ENGINE_TEXELS = 172.0F;
    private static final float[] ENGINE_ROWS = {46.0F, 76.0F, 106.0F, 136.0F};
    private static final float BAR_X0 = 58.0F;
    private static final float BAR_X1 = 160.0F;
    private static final float BAR_H = 12.0F;

    private static final int NEEDLE = 0xFFF2E0;
    private static final int CYAN = 0x78DCFF;
    private static final int AMBER = 0xFFB040;
    /** The boat's speed flat out on the plane, blocks a tick: full revs. */
    private static final float FLAT_OUT = 0.84F;
    private static final float BOOT_TICKS = 20.0F;
    private static final double MAX_DISTANCE_SQ = 32.0D * 32.0D;

    private static final Map<VehicleEntity, Integer> POWERED_SINCE = new WeakHashMap<>();
    /** Needle and bar positions (revs, speed, temperature), eased towards their readings frame by frame. */
    private static final Map<VehicleEntity, float[]> EASED = new WeakHashMap<>();

    SpeedboatCockpitLayer(GeoRenderer<T, Void, VehicleRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void submitRenderTask(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        VehicleEntity boat = passInfo.renderState().vehicle;
        if (boat == null || boat.isWrecked() || passInfo.renderState().distanceToCameraSq > MAX_DISTANCE_SQ) {
            return;
        }
        float partialTick = Mth.clamp(passInfo.renderState().ageInTicks - boat.tickCount, 0.0F, 1.0F);
        PoseStack modelPose = new PoseStack();
        modelPose.last().set(passInfo.getModelRenderMatrixPose());
        int light = passInfo.packedLight();
        collector.submitCustomGeometry(modelPose, RenderTypes.entityCutout(FACE), (root, buffer) -> inDash(passInfo, root, pose ->
                CockpitPanel.quad(pose, buffer, FACE_PANEL.p(-HALF_WIDTH, 0.0F, 0.04F), FACE_PANEL.p(HALF_WIDTH, 0.0F, 0.04F),
                        FACE_PANEL.p(HALF_WIDTH, HEIGHT, 0.04F), FACE_PANEL.p(-HALF_WIDTH, HEIGHT, 0.04F), 0.0F, 0.0F, 1.0F, 1.0F,
                        0xFFFFFFFF, light, FACE_PANEL.out)));

        // The live instruments only for the crew, who can read them.
        var viewer = net.minecraft.client.Minecraft.getInstance().player;
        if (viewer == null || viewer.getRootVehicle() != boat) {
            return;
        }
        if (!AircraftLights.isPowered(boat)) {
            POWERED_SINCE.remove(boat);
            EASED.remove(boat);
            return;
        }
        int since = POWERED_SINCE.computeIfAbsent(boat, b -> b.tickCount);
        float boot = CockpitPanel.bootLevel((boat.tickCount - since + partialTick) / BOOT_TICKS);
        if (boot <= 0.0F) {
            return;
        }
        float level = boot * Mth.lerp(AircraftLights.nightSwitch(partialTick), 1.0F, 0.8F);
        int screen = CockpitPanel.grey(level);
        float seconds = (boat.level().getGameTime() + partialTick) / 20.0F;

        // Readings.
        double dx = boat.getX() - boat.xo;
        double dz = boat.getZ() - boat.zo;
        float speed = (float) Math.sqrt(dx * dx + dz * dz);
        float knots = speed * 20.0F * 1.9438F;
        float throttle = Mth.clamp(speed / FLAT_OUT, 0.0F, 1.0F);
        float rpm = 750.0F + 4600.0F * throttle + 40.0F * Mth.sin(seconds * 11.0F);
        float[] eased = EASED.computeIfAbsent(boat, b -> new float[3]);
        eased[0] = Mth.lerp(0.15F, eased[0], Mth.clamp(rpm / 6000.0F, 0.0F, 1.0F));
        eased[1] = Mth.lerp(0.15F, eased[1], Mth.clamp(knots / 50.0F, 0.0F, 1.0F));
        eased[2] = Mth.lerp(0.004F, eased[2], 0.45F + 0.3F * throttle);
        float fuel = boat.maxVehicleEnergy() <= 0 ? 0.0F : boat.vehicleEnergy() / (float) boat.maxVehicleEnergy();
        float trim = Mth.clamp(boat.boatBowRise(partialTick) / 8.0F, -1.0F, 1.0F);
        float heading = Mth.wrapDegrees(Mth.rotLerp(partialTick, boat.yRotO, boat.getYRot()) + 180.0F);
        float hull = boat.maxVehicleHealth() <= 0 ? 1.0F : boat.vehicleHealth() / (float) boat.maxVehicleHealth();
        boolean blink = ((int) (seconds * 2.0F) & 1) == 0;
        boolean night = AircraftLights.nightSwitch(partialTick) > 0.5F;
        int[] lamps = {
                0x50FF70,                                             // engine running
                night ? 0x50FF70 : 0,                                 // navigation lights
                boat.isBoatPlaning() ? CYAN : 0,                      // on the plane
                fuel < 0.15F && blink ? AMBER : 0,                    // low fuel
                hull < 0.35F && blink ? 0xFF3A2A : 0,                 // hull damage
                night ? 0xF4F0E0 : 0                                  // searchlight
        };

        // Backlit screens and dials.
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(CHART), (root, buffer) -> inDash(passInfo, root, pose ->
                FACE_PANEL.face(pose, buffer, CHART_U, SCREEN_V, SCREEN, SCREEN, 0.12F, screen)));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(ENGINE), (root, buffer) -> inDash(passInfo, root, pose ->
                FACE_PANEL.face(pose, buffer, ENGINE_U, SCREEN_V, SCREEN, SCREEN, 0.12F, screen)));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(TACHO), (root, buffer) -> inDash(passInfo, root, pose ->
                FACE_PANEL.face(pose, buffer, -DIAL_U - DIAL_R, DIAL_V - DIAL_R, 2 * DIAL_R, 2 * DIAL_R, 0.1F, screen)));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(SPEEDO), (root, buffer) -> inDash(passInfo, root, pose ->
                FACE_PANEL.face(pose, buffer, DIAL_U - DIAL_R, DIAL_V - DIAL_R, 2 * DIAL_R, 2 * DIAL_R, 0.1F, screen)));

        // Needles, bars, the chart's marks, the compass and the lamps.
        collector.order(2).submitCustomGeometry(modelPose, RenderTypes.eyes(WHITE), (root, buffer) -> inDash(passInfo, root, pose -> {
            int needle = CockpitPanel.argb(NEEDLE, level);
            float sweep = 270.0F * Mth.DEG_TO_RAD;
            float start = -135.0F * Mth.DEG_TO_RAD;
            FACE_PANEL.needle(pose, buffer, -DIAL_U, DIAL_V, DIAL_R * 0.84F, start + sweep * eased[0], 0.09F, 0.18F, needle);
            FACE_PANEL.needle(pose, buffer, DIAL_U, DIAL_V, DIAL_R * 0.84F, start + sweep * eased[1], 0.09F, 0.18F, needle);
            engineBars(pose, buffer, eased[0], fuel, trim, eased[2], blink, level);
            chartMarks(pose, buffer, heading, throttle, level);
            compass(pose, buffer, heading, level);
            for (int i = 0; i < lamps.length; i++) {
                int colour = lamps[i] != 0 ? CockpitPanel.argb(lamps[i], level) : 0xFF1C1E20;
                FACE_PANEL.face(pose, buffer, LAMP_U[i], LAMP_V, LAMP_SIZE, LAMP_SIZE, 0.1F, colour);
            }
        }));
    }

    /**
     * Draws in the dash's own frame: the hull bone it belongs to pitches as the bow rises on the plane, and drawn in the
     * boat's root frame the console sank into the dash block under way.
     */
    private static void inDash(RenderPassInfo<VehicleRenderState> passInfo, PoseStack.Pose root, Consumer<PoseStack.Pose> draw) {
        GeoBone dash = passInfo.model().getBone(DASH_BONE).orElse(null);
        if (dash == null) {
            draw.accept(root);
            return;
        }
        PoseStack stack = new PoseStack();
        stack.last().set(root);
        passInfo.renderPosed(() -> {
            Deque<GeoBone> chain = new ArrayDeque<>();
            for (GeoBone bone = dash; bone != null; bone = bone.parent()) {
                chain.push(bone);
            }
            for (GeoBone bone : chain) {
                RenderUtil.prepMatrixForBone(stack, bone);
            }
            draw.accept(stack.last());
        });
    }

    /** The engine page's bars: revs, fuel, trim (from the middle: bow up right, bow down left) and temperature. */
    private static void engineBars(PoseStack.Pose pose, VertexConsumer buffer, float revs, float fuel, float trim,
                                   float temperature, boolean blink, float level) {
        int cyan = CockpitPanel.argb(CYAN, 0.9F * level);
        int amber = CockpitPanel.argb(AMBER, level);
        bar(pose, buffer, 0, 0.0F, revs, revs > 0.87F ? amber : cyan);
        bar(pose, buffer, 1, 0.0F, fuel, fuel < 0.15F && blink ? amber : cyan);
        bar(pose, buffer, 2, Math.min(0.5F, 0.5F + 0.5F * trim), Math.max(0.5F, 0.5F + 0.5F * trim), cyan);
        bar(pose, buffer, 3, 0.0F, temperature, cyan);
    }

    private static void bar(PoseStack.Pose pose, VertexConsumer buffer, int row, float from, float to, int colour) {
        if (to - from < 0.005F) {
            return;
        }
        float scale = SCREEN / ENGINE_TEXELS;
        float x0 = BAR_X0 + (BAR_X1 - BAR_X0) * Mth.clamp(from, 0.0F, 1.0F);
        float x1 = BAR_X0 + (BAR_X1 - BAR_X0) * Mth.clamp(to, 0.0F, 1.0F);
        float v = SCREEN_V + (ENGINE_ROWS[row] + 1.0F) * scale;
        FACE_PANEL.face(pose, buffer, ENGINE_U + x0 * scale, v, (x1 - x0) * scale, (BAR_H - 1.0F) * scale, 0.16F, colour);
    }

    /**
     * Over the chart: a compass ring turning heading-up (north marked red), the boat's own mark in the middle and its
     * course line ahead, longer the faster it goes.
     */
    private static void chartMarks(PoseStack.Pose pose, VertexConsumer buffer, float heading, float throttle, float level) {
        float cu = CHART_U + SCREEN * 0.5F;
        float cv = SCREEN_V + SCREEN * 0.52F;
        float radius = SCREEN * 0.38F;
        float u0 = CHART_U;
        float v0 = SCREEN_V;
        float u1 = CHART_U + SCREEN;
        float v1 = SCREEN_V + SCREEN * (1.0F - 18.0F / 172.0F);
        int ring = CockpitPanel.argb(CYAN, 0.75F * level);
        for (int deg = 0; deg < 360; deg += 30) {
            float a = (deg - heading) * Mth.DEG_TO_RAD;
            float s = Mth.sin(a);
            float c = Mth.cos(a);
            float inner = deg == 0 ? radius * 0.72F : deg % 90 == 0 ? radius * 0.82F : radius * 0.9F;
            int colour = deg == 0 ? CockpitPanel.argb(0xFF4030, level) : ring;
            FACE_PANEL.line(pose, buffer, cu + s * inner, cv - c * inner, cu + s * radius, cv - c * radius,
                    deg == 0 ? 0.09F : 0.05F, 0.16F, colour, u0, v0, u1, v1);
        }
        // Course line ahead (up the screen) and the boat's mark.
        int white = CockpitPanel.argb(0xFFFFFF, level);
        float course = radius * (0.25F + 0.75F * throttle);
        FACE_PANEL.line(pose, buffer, cu, cv - 0.18F, cu, cv - 0.18F - course, 0.04F, 0.16F,
                CockpitPanel.argb(0xFFFFFF, 0.6F * level), u0, v0, u1, v1);
        List<float[]> mark = new ArrayList<>();
        mark.add(new float[]{cu, cv - 0.24F});
        mark.add(new float[]{cu + 0.15F, cv + 0.16F});
        mark.add(new float[]{cu - 0.15F, cv + 0.16F});
        FACE_PANEL.fill(pose, buffer, mark, 0.18F, white);
    }

    /** The compass bowl's card: its north needle, red, turns against the heading. */
    private static void compass(PoseStack.Pose pose, VertexConsumer buffer, float heading, float level) {
        float north = -heading * Mth.DEG_TO_RAD;
        FACE_PANEL.needle(pose, buffer, 0.0F, COMPASS_V, 0.5F, north, 0.08F, 0.14F, CockpitPanel.argb(0xFF4030, level));
        FACE_PANEL.needle(pose, buffer, 0.0F, COMPASS_V, 0.4F, north + Mth.PI, 0.06F, 0.14F, CockpitPanel.argb(0xE8E8E8, level));
    }
}
