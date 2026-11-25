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
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ttv.migami.jeg.vehicle.client.resource.DefaultVehicleResource;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Draws {@link AircraftLights}: a glow at each light, the light it casts on the airframe round it, formation strips,
 * rotor-tip lights, and a searchlight's beam, lens and the pool where it lands.
 * <p>
 * The airframe is lit by drawing the model again over itself, its own texture tinted by the lights near each vertex and
 * faded out beyond their reach, so a lit panel keeps its paint and rivets rather than turning a flat colour. Glows face
 * the camera; a beam is a strip along its axis turned to face the camera, soft at the edges, fading with distance.
 * <p>
 * From the pilot's seat the lights stay out of the way: no beam across the view, the glows smaller and dimmer, the
 * strobes faint, and the cockpit lighting kept low.
 */
final class VehicleLightsLayer<T extends VehicleEntity> extends GeoRenderLayer<T, Void, VehicleRenderState> {
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("jeg", "textures/effect/light_glow.png");
    /**
     * The glow under a shader pack: a soft halo whose colour fades with its alpha (premultiplied). The packs take the
     * colour as emitted light, so the plain glow, white to its rim, showed as a hard white or coloured disc: the Mi-28's
     * wingtip and tail lamps, the AH-6's, the A-10's tail lamp and the bush plane's (the user's review, 2026-11-19).
     */
    private static final Identifier GLOW_SOFT = Identifier.fromNamespaceAndPath("jeg", "textures/effect/light_glow_soft.png");
    /** Under a shader pack the halo is dimmer: the pack's bloom spreads it. */
    private static final float SHADER_GLOW_ALPHA = 0.6F;
    private static final Identifier BEAM = Identifier.fromNamespaceAndPath("jeg", "textures/effect/light_beam.png");
    private static final Identifier POOL = Identifier.fromNamespaceAndPath("jeg", "textures/effect/light_pool.png");
    /** The drawn pool's strength under a shader pack with no dynamic lights to light the ground instead. */
    private static final float SHADER_POOL_ALPHA = 0.25F;
    /** The airframe is lit only within this many blocks of the camera; beyond, the glows alone show. */
    private static final float SPILL_DISTANCE = 128.0F;
    /**
     * Light on a surface can at most bring it up to its own texture's colour, and much of the airframe and cockpit is
     * dark grey: these scale the lights' spill so that close to a lamp the paint shows in its colour.
     */
    private static final float SPILL_GAIN = 1.8F;
    private static final float COCKPIT_GAIN = 3.0F;
    /**
     * An aircraft's instrument light on its cockpit, under a shader pack: the spill is drawn additive, which the pack
     * takes as emissive and blooms (the AH-6's windscreen pillar glowed lime and the Mi-28's frame orange, 2026-11-17).
     */
    private static final float SHADER_COCKPIT_SPILL = 0.3F;
    /**
     * The FSV-6 room lamp's wash: under a shader pack it is halved (it is drawn additive, which the pack blooms: the room
     * read blown-out warm, 2026-11-17), and it wraps only a little onto faces turned away from the lamp, so it fills the
     * room without tinting the outside of the walls.
     */
    private static final float SHADER_ROOM_SPILL = 0.5F;
    private static final float ROOM_SPILL_WRAP = 0.12F;
    /** A walk-in cabin's dome lights: how far they wrap onto faces turned from them, and their wash under a shader pack. */
    private static final float CABIN_SPILL_WRAP = 0.45F;
    private static final float SHADER_CABIN_SPILL = 0.6F;
    /** Spilled light is worked out at the corners of faces no longer than this (blocks) near a light. */
    private static final float SPILL_CELL = 0.4F;

    VehicleLightsLayer(GeoRenderer<T, Void, VehicleRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void submitRenderTask(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        VehicleEntity vehicle = passInfo.renderState().vehicle;
        if (vehicle == null) {
            return;
        }
        AircraftLights.LightSet set = AircraftLights.forVehicle(vehicle);
        if (set == null) {
            return;
        }
        // With the engines off only the battery's lights (a cabin's lamps, the jump lights) work; a wreck has none.
        boolean powered = AircraftLights.isPowered(vehicle);
        if (!powered && (vehicle.isWrecked() || !set.hasBatteryLights())) {
            return;
        }
        float partialTick = Mth.clamp(passInfo.renderState().ageInTicks - vehicle.tickCount, 0.0F, 1.0F);
        boolean occupant = AircraftLights.isOccupantView(vehicle);
        boolean openSeat = occupant && set.openSeat();
        float seconds = (vehicle.level().getGameTime() + partialTick) / 20.0F;
        float seed = AircraftLights.seed(vehicle);
        CameraRenderState camera = passInfo.cameraState();
        Quaternionf facing = new Quaternionf(camera.orientation);
        Matrix4f model = new Matrix4f(passInfo.getModelRenderMatrixState());
        float cameraDistance = model.transformPosition(new Vector3f()).length();
        boolean aircraft = vehicle.isJet()
                || vehicle.vehicleData().defaults().vehicleType() == ttv.migami.jeg.vehicle.data.subdata.VehicleType.HELICOPTER;
        boolean shaders = ttv.migami.jeg.client.compat.ShaderPacks.inUse();

        // Each light's glow and the light it casts, in the camera-relative frame the model is drawn in.
        List<Glow> glows = new ArrayList<>();
        List<float[]> spills = new ArrayList<>();
        for (AircraftLights.PointLight light : set.lights()) {
            if (!powered && !AircraftLights.onBattery(light.kind())) {
                continue;
            }
            Vector3f at = model.transformPosition(new Vector3f(light.x() / 16.0F, light.y() / 16.0F, light.z() / 16.0F));
            float r = ((light.colour() >> 16) & 0xFF) / 255.0F;
            float g = ((light.colour() >> 8) & 0xFF) / 255.0F;
            float b = (light.colour() & 0xFF) / 255.0F;
            if (light.kind() == AircraftLights.Kind.COCKPIT) {
                float level = COCKPIT_GAIN * (occupant ? 0.6F : 1.0F);
                if (aircraft) {
                    // Instrument light shows on the cockpit only once it is dark: by day the sun drowns it, and drawn
                    // anyway it painted the AH-6's pillar lime at noon. (The FSV-6's room lamp lights a closed room by
                    // day too, so it keeps its level.)
                    level *= AircraftLights.nightSwitch(partialTick) * (shaders ? SHADER_COCKPIT_SPILL : 1.0F);
                    if (level <= 0.01F) {
                        continue;
                    }
                }
                float wrap = 0.4F;
                if (vehicle instanceof ttv.migami.jeg.vehicle.entity.SupportVehicleEntity) {
                    level *= shaders ? SHADER_ROOM_SPILL : 1.0F;
                    wrap = ROOM_SPILL_WRAP;
                }
                // Instrument light falls mostly forward and down, but bounces round a small cockpit: wrap it.
                spills.add(new float[]{at.x, at.y, at.z, r, g, b, light.spill() * level, light.spillRadius() * 1.25F, wrap});
                continue;
            }
            float flash = AircraftLights.flash(vehicle, light, seconds, seed);
            if (flash <= 0.002F) {
                continue;
            }
            if (openSeat && light.kind() != AircraftLights.Kind.BEACON) {
                // Lamps around an open seat face away from the operator: no glare, and no spill on the canopy beside them.
                continue;
            }
            float shown = flash * lampFacing(vehicle, light, at, model, aircraft);
            if (shown <= 0.002F) {
                continue;
            }
            // A road lamp keeps its size and only brightens (tail to brake, off to reversing): shrunk with its level, the
            // tail glow looked tiny beside the lit one (the user's review, 2026-11-18).
            boolean roadLamp = light.kind() == AircraftLights.Kind.BRAKE || light.kind() == AircraftLights.Kind.REVERSE;
            float size = light.size() * (roadLamp ? 1.0F : 0.55F + 0.45F * flash);
            if (occupant) {
                shown *= openSeat ? 0.3F : light.kind() == AircraftLights.Kind.STROBE ? 0.25F : 0.5F;
                size *= 0.7F;
            }
            boolean cabinLamp = light.kind() == AircraftLights.Kind.CABIN;
            boolean jumpLight = light.kind() == AircraftLights.Kind.JUMP_RED || light.kind() == AircraftLights.Kind.JUMP_GREEN;
            if (cabinLamp || jumpLight) {
                // Seen from a metre or two inside the cabin: a small lit lens, not a flare (the green jump light's halo
                // filled a quarter of the view and the dome lights showed white stars, the user's report, 2026-11-25).
                size *= jumpLight ? 0.45F : 0.6F;
                shown *= 0.7F;
            }
            glows.add(new Glow(at, size, r, g, b, shown, !occupant));
            if (!openSeat) {
                if (cabinLamp) {
                    // A dome light washes the cabin and the ceiling round it (light bounces in a small room), not the
                    // outside of the hull: a wide wrap, gentler under a shader pack, which blooms what this draws.
                    spills.add(new float[]{at.x, at.y, at.z, r, g, b, light.spill() * SPILL_GAIN * flash * (shaders ? SHADER_CABIN_SPILL : 1.0F),
                            light.spillRadius() * 1.6F, CABIN_SPILL_WRAP});
                } else if (jumpLight) {
                    // A jump light is a signal, not a lamp: a little colour on the frame round it.
                    spills.add(new float[]{at.x, at.y, at.z, r, g, b, light.spill() * SPILL_GAIN * flash * 0.35F, 0.9F, 0.0F});
                } else {
                    spills.add(new float[]{at.x, at.y, at.z, r, g, b, light.spill() * SPILL_GAIN * flash,
                            light.spillRadius() * 1.2F, 0.1F});
                }
            }
        }

        // Blocks placed in the CH-47's cabin that give light wash the cabin round them in their colour (a furnace from its
        // mouth, flickering as fire does, as torches, lanterns and candles do), as far and as bright as their light level
        // reaches: warm light on the floor and walls beside them, more of it at night (the user, 2026-11-25: "lighting
        // looks off"; 2026-11-26: every placeable block's light).
        if (vehicle instanceof ttv.migami.jeg.vehicle.entity.ChinookEntity chinook) {
            var grid = ttv.migami.jeg.vehicle.cabin.CabinClient.grid(chinook.getId());
            float night = AircraftLights.nightSwitch(partialTick);
            // The brightest 16 (each light is worked out at every vertex of the model): a cabin full of lamps is lit
            // by them alone anyway.
            List<net.minecraft.core.BlockPos> emitters = new ArrayList<>(ttv.migami.jeg.vehicle.cabin.CabinBlockEffects.emitters(grid));
            emitters.sort(java.util.Comparator.comparingInt(cell -> -ttv.migami.jeg.vehicle.cabin.CabinBlockEffects.emission(grid.get(cell))));
            for (net.minecraft.core.BlockPos cell : emitters.subList(0, Math.min(16, emitters.size()))) {
                var state = grid.get(cell);
                var corner = ttv.migami.jeg.vehicle.entity.ChinookEntity.LAYOUT.cellCorner(cell);
                float x = (float) (-(corner.x + 1.0D) + 0.5D);
                float y = (float) (corner.y + 0.5D);
                float z = (float) (-(corner.z + 1.0D) + 0.5D);
                if (ttv.migami.jeg.vehicle.cabin.CabinBlockEffects.furnace(state)) {
                    net.minecraft.core.Direction drawn = state.getValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
                    x += drawn.getStepX() * 0.7F;
                    y -= 0.15F;
                    z += drawn.getStepZ() * 0.7F;
                }
                Vector3f at = model.transformPosition(new Vector3f(x, y, z));
                float strength = ttv.migami.jeg.vehicle.cabin.CabinBlockEffects.emission(state) / 13.0F;
                float flicker = 1.0F;
                if (ttv.migami.jeg.vehicle.cabin.CabinBlockEffects.flame(state)) {
                    float phase = (cell.hashCode() & 255) * 0.1F;
                    flicker = 0.82F + 0.1F * Mth.sin(seconds * 7.3F + phase) + 0.08F * Mth.sin(seconds * 17.9F + phase * 2.0F);
                }
                float[] c = ttv.migami.jeg.vehicle.cabin.CabinBlockEffects.colour(state);
                spills.add(new float[]{at.x, at.y, at.z, c[0], c[1], c[2],
                        0.55F * Math.min(strength, 1.0F) * flicker * (0.45F + 0.55F * night) * (shaders ? SHADER_CABIN_SPILL : 1.0F),
                        3.2F * strength, 0.3F});
            }
        }

        // Formation strips: flat panels of soft light on the skin, and a little of it on the panels round them.
        List<Vector3f[]> strips = new ArrayList<>();
        List<float[]> stripColours = new ArrayList<>();
        float night = AircraftLights.nightSwitch(partialTick);
        for (AircraftLights.Strip strip : set.strips()) {
            // Formation lights are a night light: by day they are dark panels (the CH-47's read as neon bars, 2026-11-21).
            float level = !powered ? 0.0F : strip.nightOnly() ? night : 1.0F;
            if (level <= 0.01F) {
                continue;
            }
            float out = Math.signum(strip.x()) * 0.15F;
            float x = (strip.x() + out) / 16.0F;
            float y0 = (strip.y() - strip.height() * 0.5F) / 16.0F;
            float y1 = (strip.y() + strip.height() * 0.5F) / 16.0F;
            float z0 = strip.z0() / 16.0F;
            float z1 = strip.z1() / 16.0F;
            strips.add(new Vector3f[]{
                    model.transformPosition(new Vector3f(x, y0, z0)), model.transformPosition(new Vector3f(x, y0, z1)),
                    model.transformPosition(new Vector3f(x, y1, z1)), model.transformPosition(new Vector3f(x, y1, z0))});
            float r = ((strip.colour() >> 16) & 0xFF) / 255.0F;
            float g = ((strip.colour() >> 8) & 0xFF) / 255.0F;
            float b = (strip.colour() & 0xFF) / 255.0F;
            stripColours.add(new float[]{r, g, b, level});
            Vector3f centre = model.transformPosition(new Vector3f(x, strip.y() / 16.0F, (z0 + z1) * 0.5F));
            // Only on the skin round it: with a wrap it showed through the CH-47's walls on its cabin's cable runs.
            spills.add(new float[]{centre.x, centre.y, centre.z, r, g, b, 0.5F * level, 1.0F, 0.0F});
        }

        // The searchlight: from the lens along the beam to the first block it meets.
        AircraftLights.Searchlight search = set.searchlight();
        float searchLevel = search == null ? 0.0F : AircraftLights.searchlightLevel(vehicle, search, partialTick);
        Beam beam = null;
        if (searchLevel > 0.0F) {
            beam = beam(vehicle, search, searchLevel, model, camera);
            float r = ((search.colour() >> 16) & 0xFF) / 255.0F;
            float g = ((search.colour() >> 8) & 0xFF) / 255.0F;
            float b = (search.colour() & 0xFF) / 255.0F;
            if (!openSeat) {
                spills.add(new float[]{beam.lens.x, beam.lens.y, beam.lens.z, r, g, b, 0.55F * searchLevel, 1.1F, 0.0F});
            }
        }

        PoseStack modelPose = new PoseStack();
        modelPose.last().set(passInfo.getModelRenderMatrixPose());
        if (!spills.isEmpty() && cameraDistance < SPILL_DISTANCE) {
            float[][] lights = spills.toArray(new float[0][]);
            collector.order(1).submitCustomGeometry(modelPose,
                    RenderTypes.eyes(DefaultVehicleResource.texture(vehicle)),
                    (pose, buffer) -> {
                        PoseStack spillPose = new PoseStack();
                        spillPose.last().set(pose);
                        SpillConsumer lit = new SpillConsumer(buffer, lights);
                        passInfo.renderPosed(() -> {
                            // As GeckoLib draws a bone: into its frame, its own cubes, then its children (which skip
                            // whatever the battle damage hides).
                            for (GeoBone bone : passInfo.model().topLevelBones()) {
                                spillPose.pushPose();
                                RenderUtil.prepMatrixForBone(spillPose, bone);
                                bone.render(passInfo, spillPose, lit, 0x00F000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                                bone.renderChildren(passInfo, spillPose, lit, 0x00F000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                                spillPose.popPose();
                            }
                        });
                    });
        }

        // Glows and the beam are built in the camera-relative frame directly; an identity pose leaves them there.
        PoseStack identity = new PoseStack();
        if (!strips.isEmpty()) {
            collector.order(2).submitCustomGeometry(identity, RenderTypes.eyes(POOL), (pose, buffer) -> {
                for (int i = 0; i < strips.size(); i++) {
                    Vector3f[] c = strips.get(i);
                    float[] rgb = stripColours.get(i);
                    // Faded in at dusk: the eyes type adds its colour, so the colour itself is scaled.
                    int[] colour = colour(rgb[0] * rgb[3], rgb[1] * rgb[3], rgb[2] * rgb[3], 0.95F * rgb[3]);
                    quad(buffer, c[0], c[1], c[2], c[3], 0.0F, 1.0F, colour, colour);
                }
            });
        }
        AircraftLights.RotorTips tips = set.rotorTips();
        if (tips != null && powered) {
            // Radians the rotor turns in a tick: the tip lights are drawn smeared over half of that, as the eye sees a
            // spinning light, which closes into a ring once the rotor is up to speed.
            float turn = Math.abs(vehicle.propellerRot(1.0F) - vehicle.propellerRot(0.0F));
            float tipAlpha = occupant ? 0.45F : 0.9F;
            collector.order(2).submitCustomGeometry(modelPose, shaders ? RenderTypes.entityTranslucentEmissive(GLOW_SOFT) : RenderTypes.eyes(GLOW),
                    (pose, buffer) -> rotorTips(passInfo, pose, buffer, tips, turn, facing, shaders ? tipAlpha * SHADER_GLOW_ALPHA : tipAlpha));
        }
        if (!glows.isEmpty() || beam != null) {
            Beam lensBeam = beam;
            // Under a shader pack (Iris) the eyes type cut the glow's soft edge into a hard disc, a bare white bulb (the
            // AH-6, Mi-28 and A-10 at night, the user's review, 2026-11-18): the translucent emissive type keeps the
            // falloff and still blooms. The lamps also light what is round them (LambDynamicLightsCompat).
            var glowType = shaders ? RenderTypes.entityTranslucentEmissive(GLOW_SOFT) : RenderTypes.eyes(GLOW);
            float glowAlpha = shaders ? SHADER_GLOW_ALPHA : 1.0F;
            collector.order(2).submitCustomGeometry(identity, glowType, (pose, buffer) -> {
                for (Glow glow : glows) {
                    glow(buffer, facing, glow.at, glow.size, glow.r, glow.g, glow.b, glow.alpha * glowAlpha, glow.pullForward);
                }
                if (lensBeam != null && !openSeat && set.searchlight().lensGlow()) {
                    // Under a shader pack the glare is smaller and fainter: the pack's bloom makes the rest, and full size
                    // it was a white ball under the nose (the user's review, 2026-11-19).
                    float lensSize = shaders ? lensBeam.lensSize * 0.45F : lensBeam.lensSize;
                    glow(buffer, facing, lensBeam.lens, lensSize, lensBeam.r, lensBeam.g, lensBeam.b,
                            lensBeam.lensAlpha * glowAlpha, !occupant);
                }
            });
        }
        if (beam != null) {
            Beam shaft = beam;
            // No shaft for a pair of headlamps (no lens glow of its own): the beam is modelled from one point between the
            // lamps, so its strip floated off the grille as a white sliver from the side (the FSV-6 at night, 2026-11-17).
            // The ground pool and the lamps' own glows show the headlights.
            // Only an aircraft's searchlight shows its shaft: a ground vehicle's drawn beam read as a hand-drawn shape and
            // flickered as the hull rocked (the BD-40, the user's review, 2026-11-18); its light on the ground shows it.
            // Under a shader pack the drawn shaft is bloomed into a hard white stick (the AH-6's, 2026-11-19); there the
            // beam lights the ground for real (LambDynamicLights) and the air shows no shaft.
            if (!occupant && aircraft && !shaders && shaft.shaftAlpha > 0.003F && set.searchlight().lensGlow()) {
                collector.order(2).submitCustomGeometry(identity, RenderTypes.eyes(BEAM),
                        (pose, buffer) -> shaft(buffer, shaft));
            }
            // Under a shader pack the drawn pool is bloomed into a hard white blob (the FSV-6's headlights at night on
            // yftm). There the ground is lit for real by LambDynamicLights (LambDynamicLightsCompat), so the drawn pool
            // is left out; without that mod it is kept but faint enough not to blow out.
            if (shaft.hit != null && !(shaders && ttv.migami.jeg.client.compat.ShaderPacks.dynamicLights())) {
                if (shaders) {
                    shaft.poolAlpha *= SHADER_POOL_ALPHA;
                }
                collector.order(2).submitCustomGeometry(identity, RenderTypes.eyes(POOL),
                        (pose, buffer) -> pool(buffer, shaft));
            }
        }
    }

    private record Glow(Vector3f at, float size, float r, float g, float b, float alpha, boolean pullForward) {
    }

    /** White lamps (headlamps, work lights) on a land vehicle's or gun's hull. */
    private static final int HEADLAMP_WHITE = 0xFFF2DC;

    /**
     * How much of a land vehicle's headlamp or tail lamp glow shows from where the camera is: a lamp shines one way, ahead
     * for a lamp in the front half, astern for one in the back half and for brake and reversing lamps. From behind it the
     * lens is out of sight: the LAV-150's headlamps glowed as balls above their cages in the driver's view (the user's
     * report, 2026-11-19). Navigation lights, beacons and aircraft lights show all round.
     */
    private static float lampFacing(VehicleEntity vehicle, AircraftLights.PointLight light, Vector3f at, Matrix4f model, boolean aircraft) {
        if (aircraft || vehicle.vehicleData().defaults().vehicleType() == ttv.migami.jeg.vehicle.data.subdata.VehicleType.BOAT) {
            return 1.0F;
        }
        boolean road = light.kind() == AircraftLights.Kind.BRAKE || light.kind() == AircraftLights.Kind.REVERSE;
        boolean headlamp = light.kind() == AircraftLights.Kind.POSITION && light.colour() == HEADLAMP_WHITE;
        if (!road && !headlamp) {
            return 1.0F;
        }
        float ahead = road || light.z() > 0.0F ? 1.0F : -1.0F;
        Vector3f shines = model.transformDirection(new Vector3f(0.0F, 0.0F, ahead), new Vector3f());
        if (shines.lengthSquared() < 1.0E-6F || at.lengthSquared() < 1.0E-6F) {
            return 1.0F;
        }
        float facing = -new Vector3f(at).normalize().dot(shines.normalize());
        float t = Mth.clamp((facing + 0.15F) / 0.45F, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /** Each blade's tip light, smeared along its arc by {@code turn} (radians a tick) about the hub. */
    private static void rotorTips(RenderPassInfo<VehicleRenderState> passInfo, PoseStack.Pose pose, VertexConsumer buffer,
                                  AircraftLights.RotorTips tips, float turn, Quaternionf facing, float alpha) {
        float r = ((tips.colour() >> 16) & 0xFF) / 255.0F;
        float g = ((tips.colour() >> 8) & 0xFF) / 255.0F;
        float b = (tips.colour() & 0xFF) / 255.0F;
        float half = Math.min(turn * 0.5F, Mth.PI / 5.0F + 0.05F);
        float step = 1.5F * Mth.DEG_TO_RAD;
        int points = Math.max(1, Mth.ceil(2.0F * half / step));
        float each = alpha * Mth.clamp(3.0F / points, 0.1F, 1.0F);
        Vector3f tipModel = new Vector3f(tips.tip()).div(16.0F);
        Vector3f hubModel = new Vector3f(tips.hub()).div(16.0F);
        passInfo.renderPosed(() -> {
            for (String name : tips.blades()) {
                GeoBone blade = passInfo.model().getBone(name).orElse(null);
                if (blade == null) {
                    continue;
                }
                PoseStack bonePose = new PoseStack();
                bonePose.last().set(pose);
                Deque<GeoBone> chain = new ArrayDeque<>();
                for (GeoBone bone = blade; bone != null; bone = bone.parent()) {
                    chain.push(bone);
                }
                for (GeoBone bone : chain) {
                    RenderUtil.prepMatrixForBone(bonePose, bone);
                }
                Matrix4f matrix = bonePose.last().pose();
                Vector3f tip = matrix.transformPosition(new Vector3f(tipModel));
                Vector3f hub = matrix.transformPosition(new Vector3f(hubModel));
                Vector3f axis = matrix.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
                Vector3f arm = new Vector3f(tip).sub(hub);
                for (int i = 0; i < points; i++) {
                    float angle = points == 1 ? 0.0F : -half + 2.0F * half * i / (points - 1);
                    Vector3f at = new Quaternionf().fromAxisAngleRad(axis, angle).transform(new Vector3f(arm)).add(hub);
                    glow(buffer, facing, at, 0.36F, r, g, b, each, false);
                }
            }
        });
    }

    /** A searchlight this frame, in the camera-relative frame. */
    private static final class Beam {
        Vector3f lens;
        Vector3f dir;
        float length;
        float halfAngle;
        float r;
        float g;
        float b;
        float lensSize;
        float lensAlpha;
        float shaftAlpha;
        BlockHitResult hit;
        Vector3f hitAt;
        float poolRadius;
        float poolAlpha;
    }

    private static Beam beam(VehicleEntity vehicle, AircraftLights.Searchlight light, float level, Matrix4f model,
                             CameraRenderState camera) {
        Beam beam = new Beam();
        beam.lens = model.transformPosition(light.modelPos());
        beam.dir = model.transformDirection(light.modelDir()).normalize();
        beam.halfAngle = light.halfAngle() * Mth.DEG_TO_RAD;
        beam.r = ((light.colour() >> 16) & 0xFF) / 255.0F;
        beam.g = ((light.colour() >> 8) & 0xFF) / 255.0F;
        beam.b = (light.colour() & 0xFF) / 255.0F;
        Vec3 from = camera.pos.add(beam.lens.x, beam.lens.y, beam.lens.z);
        beam.hit = AircraftLights.clip(vehicle.level(), vehicle, from, beam.dir, light.range());
        float distance = beam.hit == null ? light.range() : (float) beam.hit.getLocation().distanceTo(from);
        beam.length = Math.min(distance, 40.0F);
        // Glare: the lens flares when the beam points at the camera.
        Vector3f toCamera = new Vector3f(beam.lens).negate().normalize();
        float glare = (float) Math.pow(Math.max(0.0F, toCamera.dot(beam.dir)), 6.0D);
        beam.lensSize = 0.55F + 2.4F * glare;
        beam.lensAlpha = level * (0.85F + 0.15F * glare);
        // A faint shaft through the air, plainer in rain.
        float rain = vehicle.level().getRainLevel(1.0F);
        beam.shaftAlpha = level * 0.17F * (1.0F + 1.3F * rain);
        if (beam.hit != null) {
            Vec3 hit = beam.hit.getLocation().subtract(camera.pos);
            beam.hitAt = new Vector3f((float) hit.x, (float) hit.y, (float) hit.z);
            beam.poolRadius = Mth.clamp(0.4F + distance * Mth.sin(beam.halfAngle), 1.0F, 8.0F);
            beam.poolAlpha = level * 0.6F * Mth.clamp(1.25F - distance / light.range(), 0.0F, 1.0F);
        }
        return beam;
    }

    /**
     * A glow facing the camera. Pulled a little towards the camera so a light set on a curved skin is not cut in half by
     * it (not from the pilot's seat, where it would show through the airframe).
     */
    private static void glow(VertexConsumer buffer, Quaternionf facing, Vector3f at, float size, float r, float g,
                             float b, float alpha, boolean pullForward) {
        float distance = at.length();
        // Far off, a light keeps a minimum apparent size, so a distant aircraft still shows as a point of light.
        float half = Math.max(size * 0.7F, distance * 0.006F);
        Vector3f centre = new Vector3f(at);
        if (pullForward && distance > 0.5F) {
            centre.mul(1.0F - Math.min(0.3F, half * 0.6F) / distance);
        }
        Vector3f right = facing.transform(new Vector3f(half, 0.0F, 0.0F));
        Vector3f up = facing.transform(new Vector3f(0.0F, half, 0.0F));
        // A coloured halo round a core near white, as a bright lamp looks to the eye.
        billboard(buffer, centre, right, up, colour(r, g, b, alpha));
        float core = 0.38F;
        billboard(buffer, centre, new Vector3f(right).mul(core), new Vector3f(up).mul(core),
                colour(Mth.lerp(0.6F, r, 1.0F), Mth.lerp(0.6F, g, 1.0F), Mth.lerp(0.6F, b, 1.0F), alpha));
    }

    private static void billboard(VertexConsumer buffer, Vector3f centre, Vector3f right, Vector3f up, int[] colour) {
        quad(buffer,
                new Vector3f(centre).sub(right).sub(up),
                new Vector3f(centre).add(right).sub(up),
                new Vector3f(centre).add(right).add(up),
                new Vector3f(centre).sub(right).add(up),
                0.0F, 1.0F, colour, colour);
    }

    /** The beam: a strip along its axis turned to face the camera, widening with the cone, fading as it goes. */
    private static void shaft(VertexConsumer buffer, Beam beam) {
        int segments = 5;
        float tan = Mth.sin(beam.halfAngle) / Mth.cos(beam.halfAngle);
        for (int i = 0; i < segments; i++) {
            float s0 = beam.length * i / segments;
            float s1 = beam.length * (i + 1) / segments;
            Vector3f p0 = new Vector3f(beam.dir).mul(s0).add(beam.lens);
            Vector3f p1 = new Vector3f(beam.dir).mul(s1).add(beam.lens);
            Vector3f middle = new Vector3f(p0).add(p1).mul(0.5F);
            Vector3f side = new Vector3f(beam.dir).cross(middle);
            if (side.lengthSquared() < 1.0E-6F) {
                continue;
            }
            side.normalize();
            // Seen end on the strip has no width to show; let it go rather than flicker as a line.
            float endOn = Math.abs(new Vector3f(middle).normalize().dot(beam.dir));
            float view = 1.0F - endOn * endOn * endOn * endOn;
            float w0 = 0.12F + s0 * tan * 0.85F;
            float w1 = 0.12F + s1 * tan * 0.85F;
            int[] c0 = colour(beam.r, beam.g, beam.b, beam.shaftAlpha * view * fade(s0, beam.length));
            int[] c1 = colour(beam.r, beam.g, beam.b, beam.shaftAlpha * view * fade(s1, beam.length));
            quad(buffer,
                    new Vector3f(side).mul(-w0).add(p0),
                    new Vector3f(side).mul(w0).add(p0),
                    new Vector3f(side).mul(w1).add(p1),
                    new Vector3f(side).mul(-w1).add(p1),
                    0.0F, 1.0F, c0, c1);
        }
    }

    private static float fade(float s, float length) {
        float along = 1.0F - s / length;
        return along * along / (1.0F + s / 20.0F);
    }

    /** The pool of light on the surface the beam meets: stretched along the beam where it strikes at a slant. */
    private static void pool(VertexConsumer buffer, Beam beam) {
        Direction face = beam.hit.getDirection();
        Vector3f normal = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
        Vector3f along = new Vector3f(beam.dir).sub(new Vector3f(normal).mul(beam.dir.dot(normal)));
        if (along.lengthSquared() < 1.0E-4F) {
            along = Math.abs(normal.y) > 0.5F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
        }
        along.normalize();
        Vector3f across = new Vector3f(normal).cross(along).normalize();
        // A low beam's pool runs long; past two and a half times as long as wide it only spreads thin.
        float slant = Math.max(Math.abs(beam.dir.dot(normal)), 0.4F);
        float lengthways = beam.poolRadius / slant;
        Vector3f centre = new Vector3f(normal).mul(0.04F).add(beam.hitAt);
        Vector3f a = new Vector3f(along).mul(lengthways);
        Vector3f c = new Vector3f(across).mul(beam.poolRadius);
        int[] colour = colour(beam.r, beam.g, beam.b, beam.poolAlpha);
        quad(buffer,
                new Vector3f(centre).sub(a).sub(c),
                new Vector3f(centre).add(a).sub(c),
                new Vector3f(centre).add(a).add(c),
                new Vector3f(centre).sub(a).add(c),
                0.0F, 1.0F, colour, colour);
    }

    private static int[] colour(float r, float g, float b, float alpha) {
        float a = Mth.clamp(alpha, 0.0F, 1.0F);
        return new int[]{Math.round(r * 255.0F), Math.round(g * 255.0F), Math.round(b * 255.0F), Math.round(a * 255.0F)};
    }

    /** A quad drawn from both sides: v0 at the first two corners, v1 at the last two. */
    private static void quad(VertexConsumer buffer, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float v0,
                             float v1, int[] c0, int[] c1) {
        vertex(buffer, p0, 0.0F, v0, c0);
        vertex(buffer, p1, 1.0F, v0, c0);
        vertex(buffer, p2, 1.0F, v1, c1);
        vertex(buffer, p3, 0.0F, v1, c1);
        vertex(buffer, p3, 0.0F, v1, c1);
        vertex(buffer, p2, 1.0F, v1, c1);
        vertex(buffer, p1, 1.0F, v0, c0);
        vertex(buffer, p0, 0.0F, v0, c0);
    }

    private static void vertex(VertexConsumer buffer, Vector3f p, float u, float v, int[] colour) {
        buffer.addVertex(p.x, p.y, p.z)
                .setColor(colour[0], colour[1], colour[2], colour[3])
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /**
     * Takes the model's quads as GeckoLib writes them and passes on only those a light reaches, each vertex coloured by
     * the lights round it: brighter nearer, and on the side that faces the light ({@code wrap} lets some round the
     * back, as light does in a small cockpit). Each light is {x, y, z, r, g, b, intensity, radius, wrap}.
     */
    private static final class SpillConsumer implements VertexConsumer {
        private final VertexConsumer out;
        private final float[][] lights;
        private final float[] position = new float[12];
        private final float[] uv = new float[8];
        private final int[] colour = new int[4];
        private final float[] normal = new float[3];
        private int overlay;
        private int count;

        SpillConsumer(VertexConsumer out, float[][] lights) {
            this.out = out;
            this.lights = lights;
        }

        @Override
        public void addVertex(float x, float y, float z, int argb, float u, float v, int overlay, int light,
                              float nx, float ny, float nz) {
            int i = this.count;
            this.position[i * 3] = x;
            this.position[i * 3 + 1] = y;
            this.position[i * 3 + 2] = z;
            this.uv[i * 2] = u;
            this.uv[i * 2 + 1] = v;
            if (i == 0) {
                float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
                float scale = length > 1.0E-6F ? 1.0F / length : 0.0F;
                this.normal[0] = nx * scale;
                this.normal[1] = ny * scale;
                this.normal[2] = nz * scale;
                this.overlay = overlay;
            }
            if (++this.count == 4) {
                this.count = 0;
                this.flush();
            }
        }

        private void flush() {
            float[] p = this.position;
            float edgeU = Mth.sqrt(square(p[3] - p[0]) + square(p[4] - p[1]) + square(p[5] - p[2]));
            float edgeV = Mth.sqrt(square(p[9] - p[0]) + square(p[10] - p[1]) + square(p[11] - p[2]));
            int n = Math.min(16, Math.max(1, (int) Math.ceil(Math.max(edgeU, edgeV) / SPILL_CELL)));
            if (n > 1 && this.nearLight(edgeU, edgeV)) {
                // A long face near a light (a cabin's cable run, a ceiling panel) is lit cell by cell: worked out only at
                // its corners, one end's colour ran its whole length as a neon line, and a panel whose corners were all
                // out of a lamp's reach stayed black round it (the CH-47's cabin at night, 2026-11-25). The cells are
                // an n by n grid laid on the face's own two triangles (split along corners 0-2, as it is drawn), so on a
                // face that isn't flat they stay on the drawn surface: laid on a bilinear patch, they sank behind it in
                // dark jagged streaks.
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < n; i++) {
                        this.cell(i / (float) n, j / (float) n, (i + 1) / (float) n, (j + 1) / (float) n);
                    }
                }
                return;
            }
            boolean any = false;
            for (int i = 0; i < 4; i++) {
                this.colour[i] = this.light(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]);
                any |= (this.colour[i] >>> 24) > 1;
            }
            if (!any) {
                return;
            }
            for (int i = 0; i < 4; i++) {
                this.out.addVertex(p[i * 3], p[i * 3 + 1], p[i * 3 + 2],
                        this.colour[i], this.uv[i * 2], this.uv[i * 2 + 1], this.overlay, 0x00F000F0,
                        this.normal[0], this.normal[1], this.normal[2]);
            }
        }

        private static float square(float v) {
            return v * v;
        }

        /** Whether any light reaches the face: from its middle, within its radius and half the face's size. */
        private boolean nearLight(float edgeU, float edgeV) {
            float[] p = this.position;
            float cx = (p[0] + p[3] + p[6] + p[9]) * 0.25F;
            float cy = (p[1] + p[4] + p[7] + p[10]) * 0.25F;
            float cz = (p[2] + p[5] + p[8] + p[11]) * 0.25F;
            float half = 0.5F * Mth.sqrt(edgeU * edgeU + edgeV * edgeV);
            for (float[] light : this.lights) {
                float reach = light[7] + half;
                if (square(light[0] - cx) + square(light[1] - cy) + square(light[2] - cz) < reach * reach) {
                    return true;
                }
            }
            return false;
        }

        /** One cell of the face, from (u0, v0) to (u1, v1) across it (corners 0-1 along u, 0-3 along v). */
        private void cell(float u0, float v0, float u1, float v1) {
            float[][] corners = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
            float[] at = new float[12];
            float[] tex = new float[8];
            int[] lit = new int[4];
            boolean any = false;
            for (int k = 0; k < 4; k++) {
                float s = corners[k][0], t = corners[k][1];
                // On triangle (0, 1, 2) below the diagonal, (0, 2, 3) above it.
                float w0, w1, w2, w3;
                if (s >= t) {
                    w0 = 1 - s;
                    w1 = s - t;
                    w2 = t;
                    w3 = 0;
                } else {
                    w0 = 1 - t;
                    w1 = 0;
                    w2 = s;
                    w3 = t - s;
                }
                for (int c = 0; c < 3; c++) {
                    // A hair toward the camera (positions are camera-relative), so the wash never loses the depth test
                    // to the face it lights.
                    at[k * 3 + c] = (w0 * this.position[c] + w1 * this.position[3 + c] + w2 * this.position[6 + c] + w3 * this.position[9 + c]) * 0.999F;
                }
                for (int c = 0; c < 2; c++) {
                    tex[k * 2 + c] = w0 * this.uv[c] + w1 * this.uv[2 + c] + w2 * this.uv[4 + c] + w3 * this.uv[6 + c];
                }
                lit[k] = this.light(at[k * 3], at[k * 3 + 1], at[k * 3 + 2]);
                any |= (lit[k] >>> 24) > 1;
            }
            if (!any) {
                return;
            }
            for (int k = 0; k < 4; k++) {
                this.out.addVertex(at[k * 3], at[k * 3 + 1], at[k * 3 + 2], lit[k], tex[k * 2], tex[k * 2 + 1], this.overlay,
                        0x00F000F0, this.normal[0], this.normal[1], this.normal[2]);
            }
        }

        private int light(float x, float y, float z) {
            float r = 0.0F;
            float g = 0.0F;
            float b = 0.0F;
            for (float[] light : this.lights) {
                float dx = light[0] - x;
                float dy = light[1] - y;
                float dz = light[2] - z;
                float d2 = dx * dx + dy * dy + dz * dz;
                float radius = light[7];
                if (d2 >= radius * radius) {
                    continue;
                }
                float d = Mth.sqrt(d2);
                float reach = 1.0F - d / radius;
                float facing = d > 1.0E-4F ? (this.normal[0] * dx + this.normal[1] * dy + this.normal[2] * dz) / d : 1.0F;
                float wrap = light[8];
                float lit = Math.max(0.0F, facing * (1.0F - wrap) + wrap);
                float w = light[6] * reach * reach * lit;
                r += light[3] * w;
                g += light[4] * w;
                b += light[5] * w;
            }
            float max = Math.max(r, Math.max(g, b));
            if (max <= 0.004F) {
                return 0;
            }
            float alpha = Math.min(1.0F, max);
            return (Math.round(alpha * 255.0F) << 24)
                    | (Math.round(r / max * 255.0F) << 16)
                    | (Math.round(g / max * 255.0F) << 8)
                    | Math.round(b / max * 255.0F);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }
}
