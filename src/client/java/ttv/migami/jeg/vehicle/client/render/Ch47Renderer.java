package ttv.migami.jeg.vehicle.client.render;

import com.geckolib.renderer.base.RenderPassInfo;
import java.util.Map;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.cabin.CabinClient;
import ttv.migami.jeg.vehicle.cabin.CabinMath;
import ttv.migami.jeg.vehicle.entity.ChinookEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The CH-47F Chinook. Its moving parts are posed here from state every client sees (never canned animation):
 * <ul>
 *     <li>the two rotors turn opposite ways at the same speed; the model sets their blades 60 degrees apart, so they
 *     intermesh as the real synchronising shaft keeps them;</li>
 *     <li>the ramp at its angle about its hinge.</li>
 * </ul>
 * The blocks placed in its cabin are drawn in its model's cabin bone, so they move exactly with the drawn aircraft.
 */
public final class Ch47Renderer extends AbstractVehicleGeoRenderer<ChinookEntity> {
    /**
     * The blades' coning, degrees up from the hub's plane: drooping on their stops at rest, lifted by the air's load once
     * they turn, more as the collective comes up (a climb, a load on the hook) and less as it goes down: the climb shows
     * in the rotor, as on the real aircraft, whose rotors turn at one speed (the user, 2026-11-25: "when ascending the
     * blades seem to rotate slower, giving the impression it's not doing anything").
     */
    static float coneDegrees(ChinookEntity chinook) {
        float turning = Mth.clamp(chinook.propellerSpeed() / 0.085F, 0.0F, 1.0F);
        float load = chinook.rotorLoad();
        float flying = 3.0F + 3.0F * Math.max(0.0F, load) - 1.5F * Math.max(0.0F, -load);
        return Mth.lerp(turning * turning, -MODEL_DROOP, flying);
    }

    /** The droop the blades are modelled with (mesh.py's BLADE_DROOP): the turn drawn is the cone less this. */
    private static final float MODEL_DROOP = 3.0F;

    /** Each blade bone's azimuth on its hub as GeckoLib holds it (the model file's turns, negated). */
    private static final float[] FRONT_AZIMUTH = {0.0F, 120.0F, 240.0F};
    private static final float[] REAR_AZIMUTH = {60.0F, 180.0F, 300.0F};
    /** Which way a positive turn about a blade's chord axis moves its tip, in GeckoLib's render space. */
    private static final float CONE_SENSE = 1.0F;

    /**
     * A blade turned {@code cone} about its own chord (its span lies along the bone's X before the azimuth turn): the
     * azimuth turn then the cone, written as GeckoLib's Z-Y-X angles added to the bone's own.
     */
    private static void cone(com.geckolib.renderer.base.BoneSnapshots snapshots, String bone, float azimuth, float cone) {
        org.joml.Vector3f euler = new org.joml.Matrix3f().rotateY(azimuth).rotateZ(cone).getEulerAnglesZYX(new org.joml.Vector3f());
        snapshots.ifPresent(bone, snapshot -> {
            snapshot.setRotX(euler.x);
            snapshot.setRotY(Mth.wrapDegrees((euler.y - azimuth) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD);
            snapshot.setRotZ(euler.z);
        });
    }

    private static final BlockDisplayContext BLOCK_DISPLAY = BlockDisplayContext.create();
    private static final net.minecraft.resources.Identifier SKIN_MESH = ttv.migami.jeg.Reference.id("models/mesh/ch47.bin");
    private static final net.minecraft.resources.Identifier SKIN_TEXTURE = ttv.migami.jeg.Reference.id("textures/entity/vehicle/ch47_skin.png");
    private final BlockModelResolver blockModels;

    public Ch47Renderer(EntityRendererProvider.Context context) {
        super(context, new Ch47GeoModel());
        this.blockModels = context.getBlockModelResolver();
        this.withRenderLayer(new Ch47SlingLayer(this));
        this.withRenderLayer(new Ch47CockpitLayer<>(this));
    }

    static void pose(VehicleEntity vehicle, float partialTick, com.geckolib.renderer.base.BoneSnapshots snapshots) {
        float spin = vehicle.propellerRot(partialTick);
        snapshots.ifPresent("rotor_front", snapshot -> snapshot.setRotY(spin));
        snapshots.ifPresent("rotor_rear", snapshot -> snapshot.setRotY(-spin));
        if (vehicle instanceof ChinookEntity chinook) {
            float cone = (coneDegrees(chinook) + MODEL_DROOP) * Mth.DEG_TO_RAD * CONE_SENSE;
            for (int i = 0; i < 3; i++) {
                cone(snapshots, "rotor_f_blade_" + i, FRONT_AZIMUTH[i] * Mth.DEG_TO_RAD, cone);
                cone(snapshots, "rotor_r_blade_" + i, REAR_AZIMUTH[i] * Mth.DEG_TO_RAD, cone);
            }
            float angle = chinook.rampAngle(partialTick);
            // The ramp is modelled level, reaching aft (model +Z); in GeckoLib a positive X turn swings its lip down, so
            // "degrees up" is a negative turn (generate.py's review renders use the opposite sense).
            snapshots.ifPresent("ramp", snapshot -> snapshot.setRotX(-angle * Mth.DEG_TO_RAD));
            // The upper ramp door (1.5 m tall in the grown cabin) slides up into the pylon as the ramp leaves closed.
            float lift = ease((ChinookEntity.RAMP_CLOSED - angle) / 20.0F) * 25.0F;
            snapshots.ifPresent("ramp_door", snapshot -> snapshot.setTranslateY(lift));
            // The split crew door: opening, the upper half slides aft along its rails (1 m), then the lower half folds out
            // and down on its sill hinge into the boarding steps; shutting, the other way round.
            float door = chinook.doorOpen(partialTick);
            float slide = ease(door / 0.75F) * 15.0F;
            float fold = ease((door - 0.45F) / 0.55F) * STEP_FOLD_DEGREES * Mth.DEG_TO_RAD;
            snapshots.ifPresent("crew_door", snapshot -> snapshot.setTranslateZ(slide));
            snapshots.ifPresent("crew_door_step", snapshot -> snapshot.setRotZ(fold));
            float window = ease(chinook.gunWindow(partialTick)) * 11.0F;
            snapshots.ifPresent("gun_window", snapshot -> snapshot.setTranslateZ(window));
            doorGun(chinook, ChinookEntity.LEFT_GUNNER_SEAT, "gun_l", partialTick, snapshots);
            doorGun(chinook, ChinookEntity.RIGHT_GUNNER_SEAT, "gun_r", partialTick, snapshots);
        }
    }

    /**
     * How far the crew door's lower half folds out (degrees). Negative: in GeckoLib's frame a positive Z turn swung its top
     * in across the cabin floor (seen in game, 2026-11-21).
     */
    private static final float STEP_FOLD_DEGREES = -96.0F;

    /** Smoothstep of a fraction (clamped): motion that starts and stops gently. */
    private static float ease(float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * A door gun turns and elevates with its gunner's view, within the seat's arc (the window or the door it fires
     * through); with nobody at it, it is stowed pointing up and aft beside its post, clear of the doorway (lying aft
     * along the wall, the right gun crossed the crew door at chest height).
     */
    private static void doorGun(ChinookEntity chinook, int seat, String bone, float partialTick, com.geckolib.renderer.base.BoneSnapshots snapshots) {
        net.minecraft.world.entity.Entity gunner = chinook.passengerIn(seat);
        float yaw = 180.0F, elevation = 70.0F;
        if (gunner != null) {
            var info = chinook.seatInfo(seat);
            float hullYaw = Mth.rotLerp(partialTick, chinook.yRotO, chinook.getYRot());
            float bearing = Mth.wrapDegrees(gunner.getViewYRot(partialTick) - hullYaw);
            if (info != null) {
                bearing = Mth.clamp(bearing, info.minYaw(), info.maxYaw());
            }
            yaw = -bearing;
            float pitch = gunner.getViewXRot(partialTick);
            if (info != null) {
                pitch = Mth.clamp(pitch, info.minPitch(), info.maxPitch());
            }
            elevation = -pitch;
        }
        float mountYaw = Mth.wrapDegrees(yaw) * Mth.DEG_TO_RAD;
        float gunElevation = elevation * Mth.DEG_TO_RAD;
        snapshots.ifPresent(bone + "_mount", snapshot -> snapshot.setRotY(mountYaw));
        snapshots.ifPresent(bone, snapshot -> snapshot.setRotX(gunElevation));
    }

    /**
     * A block aboard drawn by the game's own block entity renderer: a chest's or shulker box's lid at the angle its block
     * entity swings it to, an enchanting table's book (CabinBlockEffects#blockEntity). False if there is no renderer for
     * it (it is then drawn by its model, shut).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean submitLid(net.minecraft.world.level.block.entity.BlockEntity lid, BlockState state, int light, float partialTick,
                                     com.mojang.blaze3d.vertex.PoseStack poseStack, SubmitNodeCollector out,
                                     net.minecraft.client.renderer.state.level.CameraRenderState camera) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.renderer.blockentity.BlockEntityRenderer renderer = mc.getBlockEntityRenderDispatcher().getRenderer(lid);
        if (renderer == null) {
            return false;
        }
        var render = (net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState) renderer.createRenderState();
        renderer.extractRenderState(lid, render, partialTick, camera.pos, null);
        if (render instanceof net.minecraft.client.renderer.blockentity.state.ChestRenderState chest) {
            // Without a level the renderer takes a plain chest facing south: this one faces as placed, single.
            chest.facing = state.getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
                    .orElse(net.minecraft.core.Direction.SOUTH);
            chest.type = net.minecraft.world.level.block.state.properties.ChestType.SINGLE;
        } else if (render instanceof net.minecraft.client.renderer.blockentity.state.ShulkerBoxRenderState shulker) {
            shulker.direction = state.getOptionalValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)
                    .orElse(net.minecraft.core.Direction.UP);
        }
        render.lightCoords = light;
        renderer.submit(render, poseStack, out, camera);
        return true;
    }

    @Override
    public void preRenderPass(RenderPassInfo<VehicleRenderState> pass, SubmitNodeCollector collector) {
        super.preRenderPass(pass, collector);
        if (!(pass.renderState().vehicle instanceof ChinookEntity chinook)) {
            return;
        }
        // The curved skin (design/chinook/mesh.py): fuselage, pylons, sponsons, engines, blades, wheels and gear.
        boolean near = pass.cameraState().pos.distanceToSqr(chinook.position()) < 32.0D * 32.0D;
        VehicleMesh.get(SKIN_MESH).attach(pass, SKIN_TEXTURE, near);
        Map<BlockPos, BlockState> grid = CabinClient.grid(chinook.getId());
        if (grid.isEmpty()) {
            return;
        }
        // Every block aboard is drawn as the cabin is (the aircraft's light, faces shaded by their normals) and lit by the
        // dome lamps and any block aboard that gives light (CabinBlockEffects#light): drawn as moving blocks lit by the world at
        // the cabin, a furnace stood in full daylight at midnight under a shader pack, while the chests drawn by the
        // aircraft's light alone stood black beside the lamp-lit walls (the user, 2026-11-25).
        float[][] lamps = AircraftLights.ch47CabinLamps();
        float partialTick = Mth.clamp(pass.renderState().ageInTicks - chinook.tickCount, 0.0F, 1.0F);
        float lampLevel = AircraftLights.nightSwitch(partialTick);
        pass.model().getBone("cabin").ifPresent(bone -> pass.addPerBoneRender(bone, (info, drawn, out) -> {
            var poseStack = info.poseStack();
            int hullLight = info.packedLight();
            for (Map.Entry<BlockPos, BlockState> entry : grid.entrySet()) {
                Vec3 corner = ChinookEntity.LAYOUT.cellCorner(entry.getKey());
                poseStack.pushPose();
                // Render space is the data frame turned half round about Y: the cell's far corner in data is its near one here.
                Vec3 drawnCorner = new Vec3(-(corner.x + 1.0D), corner.y, -(corner.z + 1.0D));
                poseStack.translate((float) drawnCorner.x, (float) drawnCorner.y, (float) drawnCorner.z);
                BlockState state = entry.getValue();
                int light = ttv.migami.jeg.vehicle.cabin.CabinBlockEffects.light(hullLight, grid, entry.getKey(),
                        drawnCorner.add(0.5D, 0.5D, 0.5D), lamps, lampLevel);
                // Chests, shulker boxes and the enchanting table's book are drawn by their block entity (lids, pages).
                net.minecraft.world.level.block.entity.BlockEntity entity = ttv.migami.jeg.vehicle.cabin.CabinBlockEffects
                        .blockEntity(chinook.getId(), entry.getKey(), state);
                boolean byEntity = entity != null && submitLid(entity, state, light, partialTick, poseStack, out, info.cameraState());
                if (!byEntity || ttv.migami.jeg.vehicle.cabin.CabinBlockEffects.drawsModelToo(state)) {
                    BlockModelRenderState render = new BlockModelRenderState();
                    this.blockModels.update(render, state, BLOCK_DISPLAY);
                    if (!render.isEmpty()) {
                        render.submit(poseStack, out, light, OverlayTexture.NO_OVERLAY, 0);
                    }
                }
                poseStack.popPose();
            }
        }));
    }
}
