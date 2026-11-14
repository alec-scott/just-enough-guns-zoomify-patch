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
 * The A-10's instrument panel. The model has only a plain block where the panel goes, so this draws one over its face: a
 * dark fascia under a glare shield, two multi-function displays in green, an up-front display between them and the
 * three gear lamps. The left display is a live attitude indicator (horizon, pitch ladder and the ground shaded, turning
 * and sliding with the jet's bank and pitch); the right one a tactical display with a radar sweep going round. A strip
 * under the glare shield floods the panel with green. With power the screens flicker on over a second; without, they are
 * dark glass.
 * <p>
 * Only the top of the panel shows from the pilot's seat (the fixed camera looks straight ahead), so everything sits
 * high on it, the displays either side of the centre where the HUD text lies.
 */
final class A10CockpitLayer<T extends VehicleEntity> extends GeoRenderLayer<T, Void, VehicleRenderState> {
    private static final Identifier PANEL_TEXTURE = id("a10_cockpit_panel");
    private static final Identifier ADI = id("a10_mfd_adi");
    private static final Identifier TAD = id("a10_mfd_tad");
    private static final Identifier UFC = id("a10_ufc");
    private static final Identifier WHITE = id("screen_white");

    /**
     * The panel: its top edge's centre on the model's panel block, then across (the pilot's right, +X), down its face
     * (which leans away from the pilot at the top) and out towards the pilot.
     */
    private static final CockpitPanel PANEL = new CockpitPanel(new Vector3f(0.0F, 52.0F, -76.6F),
            new Vector3f(1.0F, 0.0F, 0.0F), new Vector3f(0.0F, -19.64F, 4.9F), new Vector3f(0.0F, 4.9F, 19.64F));

    private static final float HALF_WIDTH = 10.6F;
    private static final float SCREEN = 5.2F;
    private static final float SCREEN_TOP = 0.5F;
    private static final float LEFT_SCREEN = -9.9F;
    private static final float RIGHT_SCREEN = 4.7F;
    private static final int GREEN = 0x46FF78;
    private static final float BOOT_TICKS = 24.0F;
    private static final double MAX_DISTANCE_SQ = 64.0D * 64.0D;

    /** When each jet last came on, in its own ticks, for the displays' start-up. */
    private static final Map<VehicleEntity, Integer> POWERED_SINCE = new WeakHashMap<>();

    A10CockpitLayer(GeoRenderer<T, Void, VehicleRenderState> renderer) {
        super(renderer);
    }

    static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath("jeg", "textures/entity/vehicle/cockpit/" + name + ".png");
    }

    @Override
    public void submitRenderTask(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        VehicleEntity jet = passInfo.renderState().vehicle;
        if (jet == null || passInfo.renderState().distanceToCameraSq > MAX_DISTANCE_SQ) {
            return;
        }
        float partialTick = Mth.clamp(passInfo.renderState().ageInTicks - jet.tickCount, 0.0F, 1.0F);
        PoseStack modelPose = new PoseStack();
        modelPose.last().set(passInfo.getModelRenderMatrixPose());
        int light = passInfo.packedLight();

        collector.submitCustomGeometry(modelPose, RenderTypes.entityCutout(PANEL_TEXTURE), (pose, buffer) -> panel(pose, buffer, light));

        if (!AircraftLights.isPowered(jet)) {
            POWERED_SINCE.remove(jet);
            return;
        }
        int since = POWERED_SINCE.computeIfAbsent(jet, j -> j.tickCount);
        float boot = CockpitPanel.bootLevel((jet.tickCount - since + partialTick) / BOOT_TICKS);
        if (boot <= 0.0F) {
            return;
        }
        // Dimmed at night as a pilot turns them down, so they do not dazzle.
        float level = boot * Mth.lerp(AircraftLights.nightSwitch(partialTick), 1.0F, 0.8F);

        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(ADI), (pose, buffer) ->
                PANEL.face(pose, buffer, LEFT_SCREEN, SCREEN_TOP, SCREEN, SCREEN, 0.3F, CockpitPanel.grey(level)));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(TAD), (pose, buffer) ->
                PANEL.face(pose, buffer, RIGHT_SCREEN, SCREEN_TOP, SCREEN, SCREEN, 0.3F, CockpitPanel.grey(level)));
        collector.order(1).submitCustomGeometry(modelPose, RenderTypes.eyes(UFC), (pose, buffer) ->
                PANEL.face(pose, buffer, -3.6F, 0.6F, 7.2F, 1.8F, 0.25F, CockpitPanel.grey(level)));

        Vec3 angles = jet.poseAngles(partialTick);
        float pitchUp = (float) -angles.y;
        float bank = (float) angles.z;
        float seconds = (jet.level().getGameTime() + partialTick) / 20.0F;
        float gear = jet.jetGear(partialTick);
        collector.order(2).submitCustomGeometry(modelPose, RenderTypes.eyes(WHITE), (pose, buffer) -> {
            // The attitude display's window, from its texture: between the tapes, under the heading scale.
            PANEL.attitude(pose, buffer, LEFT_SCREEN + SCREEN * 0.5F, SCREEN_TOP + SCREEN * 0.5F,
                    LEFT_SCREEN + SCREEN * 30.0F / 128.0F, SCREEN_TOP + SCREEN * 44.0F / 128.0F,
                    LEFT_SCREEN + SCREEN * 98.0F / 128.0F, SCREEN_TOP + SCREEN * 100.0F / 128.0F,
                    pitchUp, bank, 0.075F, 0.36F, GREEN, level, 0.16F, 0.0F, 0.09F);
            sweep(pose, buffer, seconds, level);
            gearLamps(pose, buffer, gear, boot);
            // The flood strip under the glare shield.
            PANEL.face(pose, buffer, -HALF_WIDTH + 0.6F, -0.35F, 2.0F * HALF_WIDTH - 1.2F, 0.18F, 2.2F,
                    CockpitPanel.argb(GREEN, 0.55F * level));
        });
    }

    /** The fascia and the glare shield over it: solid, lit as the cockpit is. */
    private static void panel(PoseStack.Pose pose, VertexConsumer buffer, int light) {
        int colour = 0xFFFFFFFF;
        // Fascia, all the way down past what the pilot can see.
        CockpitPanel.quad(pose, buffer, PANEL.p(-HALF_WIDTH, -0.2F, 0.1F), PANEL.p(HALF_WIDTH, -0.2F, 0.1F),
                PANEL.p(HALF_WIDTH, 12.0F, 0.1F), PANEL.p(-HALF_WIDTH, 12.0F, 0.1F), 0.0F, 0.0F, 1.0F, 1.0F, colour, light,
                PANEL.out);
        // Glare shield: its face to the pilot, its top, its underside and its ends.
        float top = -1.7F;
        float bottom = -0.1F;
        float depth = 2.6F;
        CockpitPanel.quad(pose, buffer, PANEL.p(-HALF_WIDTH, top, depth), PANEL.p(HALF_WIDTH, top, depth),
                PANEL.p(HALF_WIDTH, bottom, depth), PANEL.p(-HALF_WIDTH, bottom, depth), 0.0F, 0.0F, 1.0F, 0.08F, colour,
                light, PANEL.out);
        Vector3f up = new Vector3f(PANEL.down).negate();
        CockpitPanel.quad(pose, buffer, PANEL.p(-HALF_WIDTH, top, 0.0F), PANEL.p(HALF_WIDTH, top, 0.0F),
                PANEL.p(HALF_WIDTH, top, depth), PANEL.p(-HALF_WIDTH, top, depth), 0.0F, 0.1F, 1.0F, 0.2F, colour, light, up);
        CockpitPanel.quad(pose, buffer, PANEL.p(-HALF_WIDTH, bottom, 0.0F), PANEL.p(HALF_WIDTH, bottom, 0.0F),
                PANEL.p(HALF_WIDTH, bottom, depth), PANEL.p(-HALF_WIDTH, bottom, depth), 0.0F, 0.2F, 1.0F, 0.3F, colour,
                light, PANEL.down);
        for (float u : new float[]{-HALF_WIDTH, HALF_WIDTH}) {
            CockpitPanel.quad(pose, buffer, PANEL.p(u, top, 0.0F), PANEL.p(u, top, depth), PANEL.p(u, bottom, depth),
                    PANEL.p(u, bottom, 0.0F), 0.0F, 0.3F, 0.1F, 0.4F, colour, light, PANEL.across);
        }
    }

    /** The tactical display's radar sweep going round, with a fading trail. */
    private static void sweep(PoseStack.Pose pose, VertexConsumer buffer, float seconds, float level) {
        float cu = RIGHT_SCREEN + SCREEN * 0.5F;
        float cv = SCREEN_TOP + SCREEN * 0.5F;
        float radius = SCREEN * 40.0F / 128.0F;
        float angle = seconds * 2.2F;
        for (int i = 0; i < 8; i++) {
            float fade = 1.0F - i / 8.0F;
            int colour = CockpitPanel.argb(GREEN, level * (i == 0 ? 1.0F : 0.45F * fade * fade));
            PANEL.needle(pose, buffer, cu, cv, radius, angle - i * 0.06F, i == 0 ? 0.07F : 0.1F, 0.36F, colour);
        }
    }

    /** Three green lamps with the gear down and locked, red while it moves, dark with it up. */
    private static void gearLamps(PoseStack.Pose pose, VertexConsumer buffer, float gear, float boot) {
        int colour;
        if (gear >= 0.99F) {
            colour = CockpitPanel.argb(0x3CFF5A, 0.8F * boot);
        } else if (gear > 0.01F) {
            colour = CockpitPanel.argb(0xFF3020, 0.8F * boot);
        } else {
            colour = 0xFF1A1C1C;
        }
        for (float u : new float[]{-1.1F, 0.0F, 1.1F}) {
            PANEL.face(pose, buffer, u - 0.25F, 3.1F, 0.5F, 0.5F, 0.25F, colour);
        }
    }
}
