package ttv.migami.jeg.vehicle.client.render;

import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.support.SupportShell;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * Drives every articulation of the FSV-6 from its synced state: the deploy sequence (jacks, slide-outs, ramp, awning,
 * mast, beacon mount, bunks), the wheels, steering and the turning beacon reflectors.
 * <p>
 * GeckoLib snapshot values: translations in pixels in the authored sense (checked in game: +X moves a bone to the
 * vehicle's left); X rotations as authored; Y and Z rotations negated from the authored (right-handed) sense.
 */
public final class SupportVehicleRenderer extends AbstractVehicleGeoRenderer<SupportVehicleEntity> {
    private static final String[] WHEELS = {"wheel_front_left", "wheel_front_right", "wheel_middle_left", "wheel_middle_right",
            "wheel_rear_left", "wheel_rear_right"};
    private static final String[] JACK_NAMES = {"jack_front_left", "jack_front_right", "jack_rear_left", "jack_rear_right"};

    /** Draws blocks without a plain model (chests, ender chests) from their special model, as an enderman's held block. */
    private final net.minecraft.client.renderer.block.BlockModelResolver blockModels;
    private static final net.minecraft.client.renderer.block.model.BlockDisplayContext BLOCK_DISPLAY =
            net.minecraft.client.renderer.block.model.BlockDisplayContext.create();

    public SupportVehicleRenderer(EntityRendererProvider.Context context) {
        super(context, new NamedVehicleGeoModel("truck"));
        this.blockModels = context.getBlockModelResolver();
        this.withRenderLayer(new SupportCockpitLayer(this));
    }

    /**
     * The model takes one light value. Its origin is under the floor, so read the light in the room instead (its lit
     * cells while deployed) and over the roof, whichever is brighter, and the cab in daylight is not darkened by it.
     */
    @Override protected int getBlockLightLevel(SupportVehicleEntity truck, net.minecraft.core.BlockPos pos) {
        int light = super.getBlockLightLevel(truck, pos);
        var room = SupportRig.world(truck, 0, SupportRig.FLOOR_Y + 20, 16);
        return Math.max(light, truck.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, net.minecraft.core.BlockPos.containing(room)));
    }

    @Override protected int getSkyLightLevel(SupportVehicleEntity truck, net.minecraft.core.BlockPos pos) {
        var above = SupportRig.world(truck, 0, SupportRig.ROOF_TOP + 4, 16);
        return Math.max(super.getSkyLightLevel(truck, pos),
                truck.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, net.minecraft.core.BlockPos.containing(above)));
    }

    @Override public void preRenderPass(RenderPassInfo<VehicleRenderState> pass, SubmitNodeCollector collector) {
        super.preRenderPass(pass, collector);
        if (!(pass.renderState().vehicle instanceof SupportVehicleEntity truck)) return;
        float partial = Mth.clamp(pass.renderState().ageInTicks - truck.tickCount, 0, 1);
        float time = pass.renderState().ageInTicks;
        float progress = truck.deployProgress(partial);
        float slides = SupportRig.slides(progress), doors = SupportRig.doors(progress);
        float mastRaise = SupportRig.mastRaise(progress), mast = SupportRig.mast(progress), jacks = SupportRig.jacks(progress);
        float wheelAngle = (float) (-truck.wheelTravel(partial) * 16 / SupportRig.WHEEL_RADIUS);
        float steer = truck.steering(partial) * Mth.DEG_TO_RAD;
        float legSwing = 90 * (1 - Mth.clamp(doors * 1.25F - .25F, 0, 1));
        float legScale = (float) ((SupportRig.AWNING_LEG + truck.bodyLift() * 16) / SupportRig.AWNING_LEG);
        boolean beaconsOn = truck.engineRunning();
        float beaconSpin = beaconsOn ? time * .35F : 0;
        // Each jack reaches down to whatever ground is under its foot.
        float[] jackDrop = new float[4];
        for (int i = 0; i < 4; i++) {
            if (jacks <= 0) continue;
            Vec3 foot = SupportRig.world(truck, SupportRig.JACKS[i][0], SupportRig.JACK_FOOT_Y, SupportRig.JACKS[i][1]);
            Double ground = SupportShell.groundBelow(truck.level(), foot, SupportRig.JACK_REACH_BLOCKS);
            double reach = ground == null ? SupportRig.JACK_FOOT_Y / 16 : foot.y - ground;
            jackDrop[i] = (float) (reach * 16 * Math.min(1, jacks * 1.6F));
        }
        pass.addBoneUpdater((info, bones) -> {
            bones.ifPresent("slide_left", b -> b.setTranslateX((float) (SupportRig.SLIDE_TRAVEL * slides)));
            bones.ifPresent("slide_right", b -> b.setTranslateX((float) (-SupportRig.SLIDE_TRAVEL * slides)));
            bones.ifPresent("rear_door", b -> b.setRotX((float) (SupportRig.DOOR_OPEN_DEGREES * doors) * Mth.DEG_TO_RAD));
            bones.ifPresent("awning", b -> b.setScaleX(Math.max(.04F, doors)));
            for (int k = 0; k < 2; k++) {
                bones.ifPresent("awning_leg_" + k, b -> {
                    b.setRotZ(legSwing * Mth.DEG_TO_RAD);
                    b.setScaleY(legScale);
                    if (doors < .2F) b.skipRender(true);
                });
            }
            bones.ifPresent("mast", b -> b.setRotX(90 * (1 - mastRaise) * Mth.DEG_TO_RAD));
            for (int k = 1; k <= 3; k++) {
                bones.ifPresent("mast_" + k, b -> b.setTranslateY((float) (SupportRig.MAST_STAGE * mast)));
            }
            bones.ifPresent("beacon_mount", b -> b.setTranslateY((float) (SupportRig.BEACON_RAISE * mast)));
            // Bunks fold up against the slide-outs' walls for travel and down once deployed.
            bones.ifPresent("bunk_left", b -> b.setRotZ(90 * (1 - mast) * Mth.DEG_TO_RAD));
            bones.ifPresent("bunk_right", b -> b.setRotZ(-90 * (1 - mast) * Mth.DEG_TO_RAD));
            for (int i = 0; i < 4; i++) {
                float drop = jackDrop[i];
                bones.ifPresent(JACK_NAMES[i], b -> b.setTranslateY(-drop));
            }
            for (String wheel : WHEELS) {
                bones.ifPresent(wheel, b -> b.setRotX(wheelAngle));
            }
            bones.ifPresent("steer_left", b -> b.setRotY(steer));
            bones.ifPresent("steer_right", b -> b.setRotY(steer));
            bones.ifPresent("steering_wheel", b -> b.setRotZ(steer * 6));
            bones.ifPresent("beacon_left", b -> b.setRotY(beaconSpin));
            bones.ifPresent("beacon_right", b -> b.setRotY(beaconSpin + 1.7F));
        });
        this.drawFittings(pass, truck, time, progress);
    }

    /**
     * The fitted blocks, drawn as their own models where they are fitted: the bay modules on the slide-outs (facing the
     * aisle; a burning furnace shows lit), the room's one lamp, and the beacon on its roof mount with its beam while lit.
     * Bunk beds are real blocks while deployed and stowed in the walls while packed, so they are not drawn here.
     */
    private void drawFittings(RenderPassInfo<VehicleRenderState> pass, SupportVehicleEntity truck, float time, float progress) {
        var model = pass.model();
        for (int side = 0; side < 2; side++) {
            String boneName = side == 0 ? "slide_left" : "slide_right";
            int first = side == 0 ? 0 : 3;
            float sign = side == 0 ? 1 : -1;
            // Fronts face the aisle: the left bays (render -X) face east in the vehicle's render frame, the right ones west.
            net.minecraft.core.Direction facing = side == 0 ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.WEST;
            model.getBone(boneName).ifPresent(bone -> pass.addPerBoneRender(bone, (info, drawn, out) -> {
                for (int i = 0; i < 3; i++) {
                    int bay = first + i;
                    var stack = truck.fittedForDisplay(bay);
                    if (stack.isEmpty()) continue;
                    // In the slide-out's own frame (its packed position) the bay column is 0..16 px out from the
                    // centreline; the bone's slide moves it to 16..32 when deployed.
                    double z = SupportRig.INTERIOR_FRONT + (2 + i + .5) * SupportRig.CELL;
                    var state = modelBlock(stack);
                    if (state != null) {
                        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                            state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing);
                        }
                        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT)) {
                            state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, truck.furnaceLitForDisplay(bay));
                        }
                        drawBlock(info, drawn, out, truck, state, sign * 8, SupportRig.FLOOR_Y + 7.6, z, .94F);
                    } else {
                        var special = specialBlock(stack);
                        if (special != null) {
                            if (special.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                                special = special.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing);
                            }
                            this.drawSpecialBlock(info, drawn, out, special, sign * 8, SupportRig.FLOOR_Y + 7.6, z, .94F);
                        } else {
                            drawItem(info, drawn, out, stack, sign * 8, SupportRig.FLOOR_Y + 7.6, z, .94F, sign > 0 ? -90 : 90);
                        }
                    }
                }
            }));
        }
        // The fitted lamp hangs from the ceiling hook over the aisle's edge, the room's only light; none fitted, none drawn.
        model.getBone("body").ifPresent(bone -> pass.addPerBoneRender(bone, (info, drawn, out) -> {
            var fitted = truck.fittedForDisplay(ttv.migami.jeg.vehicle.support.SupportModules.LAMP);
            var lamp = fitted.isEmpty() ? null : modelBlock(fitted);
            if (lamp != null) {
                var state = lamp;
                boolean hangs = state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING);
                if (hangs) state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HANGING, true);
                // A hanging lantern's block reaches the ceiling (its chain at the top); a cube lamp hangs small under it.
                float scale = hangs ? .8F : .4F;
                double y = SupportRig.CEILING_Y - 8 * scale;
                drawBlock(info, drawn, out, truck, state, LAMP_HOOK[0], y, LAMP_HOOK[1], scale);
            } else if (!fitted.isEmpty()) {
                drawItem(info, drawn, out, fitted, LAMP_HOOK[0], SupportRig.CEILING_Y - 4, LAMP_HOOK[1], .4F, 0);
            }
        }));
        float mast = SupportRig.mast(progress);
        model.getBone("beacon_mount").ifPresent(bone -> pass.addPerBoneRender(bone, (info, drawn, out) -> {
            var beacon = truck.fittedForDisplay(ttv.migami.jeg.vehicle.support.SupportModules.ROOF);
            if (beacon.isEmpty()) return;
            var state = modelBlock(beacon);
            if (state != null) drawBlock(info, drawn, out, truck, state, 0, SupportRig.ROOF_TOP + 6.8, 24, .8F);
            else drawItem(info, drawn, out, beacon, 0, SupportRig.ROOF_TOP + 6.8, 24, .8F, 0);
            if (truck.beaconLit() && mast >= 1) {
                var pose = info.poseStack();
                pose.pushPose();
                pose.translate(-(0 / 16F) - drawn.pivotX() / 16 - .5F, (float) ((SupportRig.ROOF_TOP + 13) / 16) - drawn.pivotY() / 16,
                        24 / 16F - drawn.pivotZ() / 16 - .5F);
                net.minecraft.client.renderer.blockentity.BeaconRenderer.submitBeaconBeam(pose, out,
                        net.minecraft.client.renderer.blockentity.BeaconRenderer.BEAM_LOCATION, 1F, time, 0, 320, 0xFFFFFFFF,
                        net.minecraft.client.renderer.blockentity.BeaconRenderer.SOLID_BEAM_RADIUS,
                        net.minecraft.client.renderer.blockentity.BeaconRenderer.BEAM_GLOW_RADIUS);
                pose.popPose();
            }
        }));
    }

    /** The lamp hook in the ceiling (model px x, z; generate.py's LAMP_HOOK): mid-room over the aisle's left edge, clear of a walking player's head. */
    public static final double[] LAMP_HOOK = {11, 16};

    /** The block a fitted item stands for, if it is drawn from its block model (not a chest or bed's special model). */
    @org.jetbrains.annotations.Nullable
    private static net.minecraft.world.level.block.state.BlockState modelBlock(net.minecraft.world.item.ItemStack stack) {
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem item) || specialBlock(stack) != null) return null;
        var state = item.getBlock().defaultBlockState();
        return state.getRenderShape() == net.minecraft.world.level.block.RenderShape.MODEL ? state : null;
    }

    /**
     * A fitted block whose look comes from its block entity's renderer (chests, trapped chests, ender chests, shulker
     * boxes), or null. Their block models are empty, so they are drawn from their special model instead.
     */
    @org.jetbrains.annotations.Nullable
    private static net.minecraft.world.level.block.state.BlockState specialBlock(net.minecraft.world.item.ItemStack stack) {
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem item)) return null;
        var block = item.getBlock();
        return block instanceof net.minecraft.world.level.block.AbstractChestBlock<?> || block instanceof net.minecraft.world.level.block.ShulkerBoxBlock
                ? block.defaultBlockState() : null;
    }

    /**
     * Draw a block without a plain model (a chest) centred at a model point (pixels), through the block model resolver as
     * an enderman draws the block it holds. (Its item model, drawn with no display context, never appeared: chests
     * were invisible in the bays.)
     */
    private void drawSpecialBlock(RenderPassInfo<VehicleRenderState> info, com.geckolib.cache.model.GeoBone drawn,
                                  net.minecraft.client.renderer.SubmitNodeCollector out, net.minecraft.world.level.block.state.BlockState state,
                                  double x, double y, double z, float scale) {
        var render = new net.minecraft.client.renderer.block.BlockModelRenderState();
        this.blockModels.update(render, state, BLOCK_DISPLAY);
        if (render.isEmpty()) return;
        var pose = info.poseStack();
        pose.pushPose();
        pose.translate((float) (-x / 16) - drawn.pivotX() / 16, (float) (y / 16) - drawn.pivotY() / 16, (float) (z / 16) - drawn.pivotZ() / 16);
        pose.scale(scale, scale, scale);
        pose.translate(-.5F, -.5F, -.5F);
        render.submit(pose, out, info.packedLight(), net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }

    /** Draw a block's own 3D model centred at a model point (pixels), lit by the world at that point. */
    private static void drawBlock(RenderPassInfo<VehicleRenderState> info, com.geckolib.cache.model.GeoBone drawn,
                                  net.minecraft.client.renderer.SubmitNodeCollector out, SupportVehicleEntity truck,
                                  net.minecraft.world.level.block.state.BlockState state, double x, double y, double z, float scale) {
        var level = (net.minecraft.client.multiplayer.ClientLevel) truck.level();
        var at = net.minecraft.core.BlockPos.containing(SupportRig.world(truck, x, y, z));
        var block = new net.minecraft.client.renderer.block.MovingBlockRenderState();
        block.randomSeedPos = at;
        block.blockPos = at;
        block.blockState = state;
        block.biome = level.getBiome(at);
        block.cardinalLighting = level.cardinalLighting();
        block.lightEngine = level.getLightEngine();
        var pose = info.poseStack();
        pose.pushPose();
        pose.translate((float) (-x / 16) - drawn.pivotX() / 16, (float) (y / 16) - drawn.pivotY() / 16, (float) (z / 16) - drawn.pivotZ() / 16);
        pose.scale(scale, scale, scale);
        pose.translate(-.5F, -.5F, -.5F);
        out.submitMovingBlock(pose, block);
        pose.popPose();
    }

    /** Draw an item's model centred at a model point (pixels), turned about Y by {@code yaw} degrees and scaled. */
    private static void drawItem(RenderPassInfo<VehicleRenderState> info, com.geckolib.cache.model.GeoBone drawn,
                                 net.minecraft.client.renderer.SubmitNodeCollector out, net.minecraft.world.item.ItemStack stack,
                                 double x, double y, double z, float scale, float yaw) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(state, stack, net.minecraft.world.item.ItemDisplayContext.NONE, minecraft.level, null, 0);
        var pose = info.poseStack();
        pose.pushPose();
        // Per-bone callbacks run at the bone's pivot, in render space (the model's X negated).
        pose.translate((float) (-x / 16) - drawn.pivotX() / 16, (float) (y / 16) - drawn.pivotY() / 16, (float) (z / 16) - drawn.pivotZ() / 16);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
        pose.scale(scale, scale, scale);
        // With no display context the item's model spans its own 0..1 cube: centre it on the point, as drawBlock does.
        // (Without this a chest, whose item is a special model, was drawn half a block off, inside the slide-out's wall.)
        pose.translate(-.5F, -.5F, -.5F);
        state.submit(pose, out, info.packedLight(), net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
