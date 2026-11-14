package ttv.migami.jeg.vehicle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import ttv.migami.jeg.vehicle.client.VehicleClientState;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

abstract class AbstractVehicleGeoRenderer<T extends VehicleEntity> extends GeoEntityRenderer<T, VehicleRenderState> {
    @SuppressWarnings("unchecked")
    protected AbstractVehicleGeoRenderer(EntityRendererProvider.Context context, GeoModel<? extends VehicleEntity> model) {
        super(context, (GeoModel<T>) model);
        this.withRenderLayer(new VehicleBoatWaterMaskLayer<>(this));
        this.withRenderLayer(new VehicleGlowLayer<>(this));
        this.withRenderLayer(new VehicleBarrelHeatLayer<>(this));
        this.withRenderLayer(new VehicleExhaustGlowLayer<>(this));
        this.withRenderLayer(new VehicleFanBlurLayer<>(this));
        this.shadowRadius = 0.9F;
    }

    @Override
    public RenderType getRenderType(VehicleRenderState renderState, Identifier texture) {
        return RenderTypes.entityTranslucent(texture);
    }

    /**
     * Battle damage on the hull: from 60% health down it darkens with scorching, to about half its colour at nothing
     * left, and a wreck is charred almost black.
     */
    @Override
    public int getRenderColor(T vehicle, Void relatedObject, float partialTick) {
        int colour = super.getRenderColor(vehicle, relatedObject, partialTick);
        float shade;
        if (vehicle.isWrecked()) {
            shade = vehicle.wreckState() == VehicleEntity.WRECK_BURNING ? 0.22F : 0.38F;
        } else {
            float max = vehicle.maxVehicleHealth();
            float ratio = max <= 0.0F ? 1.0F : Mth.clamp(vehicle.vehicleHealth() / max, 0.0F, 1.0F);
            shade = ratio >= 0.6F ? 1.0F : Mth.lerp(ratio / 0.6F, 0.5F, 1.0F);
        }
        if (shade >= 1.0F) {
            return colour;
        }
        int r = Math.round(((colour >> 16) & 0xFF) * shade);
        int g = Math.round(((colour >> 8) & 0xFF) * shade);
        int b = Math.round((colour & 0xFF) * shade);
        return (colour & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    @Override
    public VehicleRenderState createRenderState(T entity, Void context) {
        return new VehicleRenderState();
    }

    @Override
    public void extractRenderState(T vehicle, VehicleRenderState renderState, float partialTick) {
        super.extractRenderState(vehicle, renderState, partialTick);
        renderState.vehicle = vehicle;
        renderState.hideWhileZooming = shouldHideVehicleWhileZooming(vehicle);
        renderState.rootY = (float) vehicle.rotateOffsetHeight();
        renderState.yaw = Mth.rotLerp(partialTick, vehicle.yRotO, vehicle.getYRot());
        renderState.pitch = Mth.lerp(partialTick, vehicle.xRotO, vehicle.getXRot());
        renderState.roll = vehicle.roll(partialTick);
        renderState.jetPose = vehicle.isJet() ? new org.joml.Quaternionf(vehicle.poseRotation(partialTick)) : null;
    }

    @Override
    public void submit(VehicleRenderState renderState, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (renderState.hideWhileZooming) {
            return;
        }
        super.submit(renderState, poseStack, collector, cameraState);
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<VehicleRenderState> passInfo) {
        if (passInfo.renderState().hideWhileZooming) {
            return;
        }
        this.vehicleAxis(passInfo.renderState(), passInfo.poseStack());
    }

    @Override
    public void preRenderPass(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        VehicleEntity vehicle = passInfo.renderState().vehicle;
        if (vehicle != null) {
            passInfo.addBoneUpdater((info, snapshots) -> updateVehicleBones(vehicle, info.renderState().ageInTicks, snapshots));
        }
        super.preRenderPass(passInfo, collector);
    }

    @Override
    public boolean shouldRender(T vehicle, Frustum camera, double camX, double camY, double camZ) {
        if (shouldHideVehicleWhileZooming(vehicle)) {
            return false;
        }
        if (!vehicle.shouldRender(camX, camY, camZ)) {
            return false;
        }
        AABB bounds = vehicle.partBounds().inflate(5.0D);
        if (bounds.hasNaN() || bounds.getSize() == 0.0D) {
            bounds = new AABB(vehicle.getX() - 8.0D, vehicle.getY() - 6.0D, vehicle.getZ() - 8.0D, vehicle.getX() + 8.0D, vehicle.getY() + 6.0D, vehicle.getZ() + 8.0D);
        }
        return camera.isVisible(bounds);
    }

    private static boolean shouldHideVehicleWhileZooming(VehicleEntity vehicle) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null
                && player.getVehicle() == vehicle
                && vehicle.hasFocusedSightHud(player)
                && VehicleClientState.isRidingVehicle()
                && VehicleClientState.vehicleId() == vehicle.getId()
                && VehicleClientState.zoomDown();
    }

    private void vehicleAxis(VehicleRenderState renderState, PoseStack poseStack) {
        float rootY = renderState.rootY;
        if (renderState.jetPose != null) {
            // The model faces -Z: turn it round, then by the jet's own orientation.
            poseStack.rotateAround(new org.joml.Quaternionf(renderState.jetPose).mul(Axis.YP.rotationDegrees(180.0F)), 0.0F, rootY, 0.0F);
            return;
        }
        poseStack.rotateAround(Axis.YP.rotationDegrees(180.0F - renderState.yaw), 0.0F, rootY, 0.0F);
        poseStack.rotateAround(Axis.XP.rotationDegrees(-renderState.pitch), 0.0F, rootY, 0.0F);
        poseStack.rotateAround(Axis.ZP.rotationDegrees(-renderState.roll), 0.0F, rootY, 0.0F);
    }

    /**
     * Parts shot off a helicopter as its hull goes: the canopy glass below 35% health, the tail rotor below 15%, and two
     * main rotor blades on the wreck. Purely for show; the flight model is unchanged.
     */
    private static void hideBattleDamagedParts(VehicleEntity vehicle, com.geckolib.renderer.base.BoneSnapshots snapshots) {
        float max = vehicle.maxVehicleHealth();
        float ratio = max <= 0.0F ? 1.0F : vehicle.vehicleHealth() / max;
        boolean wrecked = vehicle.isWrecked();
        if (wrecked || ratio < 0.35F) {
            snapshots.ifPresent("glass", snapshot -> snapshot.skipRender(true));
        }
        if (wrecked || ratio < 0.15F) {
            snapshots.ifPresent("tailPropeller", snapshot -> {
                snapshot.skipRender(true);
                snapshot.skipChildrenRender(true);
            });
        }
        if (wrecked) {
            for (String blade : new String[]{"wing2", "wing4"}) {
                snapshots.ifPresent(blade, snapshot -> {
                    snapshot.skipRender(true);
                    snapshot.skipChildrenRender(true);
                });
            }
        }
    }

    private static void updateVehicleBones(VehicleEntity vehicle, float ageInTicks, com.geckolib.renderer.base.BoneSnapshots snapshots) {
        float partialTick = ageInTicks - vehicle.tickCount;
        if (vehicle.vehicleData().defaults().vehicleType() == ttv.migami.jeg.vehicle.data.subdata.VehicleType.HELICOPTER) {
            hideBattleDamagedParts(vehicle, snapshots);
        }
        switch (vehicle.vehicleDataId().getPath()) {
            case "ah6" -> updateHelicopterRotors(vehicle, partialTick, snapshots);
            case "bmp2" -> updateTurret(vehicle, partialTick, snapshots, -74.0F, 7.5F);
            case "lav150" -> updateTurret(vehicle, partialTick, snapshots, -15.0F, 32.5F);
            case "mi28" -> {
                updateTurret(vehicle, partialTick, snapshots, -70.0F, 30.0F);
                updateHelicopterRotors(vehicle, partialTick, snapshots);
                snapshots.ifPresent("barrel2", snapshot -> snapshot.setRotZ(vehicle.rotaryGunAngle(partialTick) * Mth.DEG_TO_RAD));
            }
            case "speedboat" -> updateTurret(vehicle, partialTick, snapshots, -25.0F, 50.0F);
            case "a10" -> updateA10(vehicle, partialTick, snapshots);
            default -> {
            }
        }
    }

    /**
     * The spinner and fan disc of each engine, centred on the nacelle's axis. The tapered rear cowling and the exhaust
     * duct inside it (houwaiyuan, houneiyuan) are fixed: spun, their eight-sided shells blurred into a see-through haze.
     */
    private static final String[] A10_FAN_BONES = {"qianzhou", "qianzhou2"};
    /**
     * The front fan blades, whose pivots sit 0.338 blocks off the nacelle's axis (outboard on the left engine, which is
     * +X in the model, and on the right one -X): spun as they are they swing round in a circle. Each is moved back by
     * as much as the spin carries its centre, so it turns in place.
     */
    private static final String[] A10_FRONT_FANS = {"qianwaiyuan", "qianwaiyuan2"};
    private static final float[] A10_FRONT_FAN_PIVOT_OFFSET = {0.338F, -0.338F};
    /** Rocket pods and bombs the A-10's loadout does not carry. */
    private static final String[] A10_UNUSED_STORES = {"weapon", "bomb1", "bomb2", "bomb3"};

    /**
     * The A-10: fans spin with the engines, the gear folds forward into its pods and nose once airborne, and each
     * pylon shows its missile while it is still loaded. Mirrored weapons fire the muzzle side (the left wing: the model's
     * +X, which GeckoLib's mirroring and the half turn leave on the jet's left) on an even count of rounds left and the
     * right wing on an odd count, so with one left it is on the right.
     */
    private static void updateA10(VehicleEntity vehicle, float partialTick, com.geckolib.renderer.base.BoneSnapshots snapshots) {
        float fan = vehicle.jetFanRotation(partialTick) * Mth.DEG_TO_RAD;
        for (String bone : A10_FAN_BONES) {
            snapshots.ifPresent(bone, snapshot -> snapshot.setRotZ(fan));
        }
        for (int i = 0; i < A10_FRONT_FANS.length; i++) {
            // Spinning by fan about the pivot carries the fan's centre, offset by v from it, to v rotated; translate it
            // back. Snapshot translations are in pixels, X negated, in the parent's frame.
            float offset = A10_FRONT_FAN_PIVOT_OFFSET[i];
            float dx = offset * (1.0F - Mth.cos(fan));
            float dy = -offset * Mth.sin(fan);
            snapshots.ifPresent(A10_FRONT_FANS[i], snapshot -> {
                snapshot.setRotZ(fan);
                snapshot.setTranslateX(-dx * 16.0F);
                snapshot.setTranslateY(dy * 16.0F);
            });
        }
        // The wheels roll forward (the model faces -Z, so that is a negative turn about X).
        float wheels = -vehicle.jetWheelRotation(partialTick) * Mth.DEG_TO_RAD;
        for (String bone : new String[]{"wheel1", "wheel2", "wheel3"}) {
            snapshots.ifPresent(bone, snapshot -> snapshot.setRotX(wheels));
        }
        // The GAU-8's seven barrels turn about the gun's axis.
        snapshots.ifPresent("maingun", snapshot -> snapshot.setRotZ(vehicle.jetGunAngle(partialTick) * Mth.DEG_TO_RAD));
        // Battle damage: below 30% health the ailerons and an elevator are shot away; a wreck has lost the outer
        // right wing, its pylons and a fin, and the burning hulk lies on its belly with the gear gone.
        float max = vehicle.maxVehicleHealth();
        float ratio = max <= 0.0F ? 1.0F : vehicle.vehicleHealth() / max;
        boolean wrecked = vehicle.isWrecked();
        hideIf(snapshots, "wingRR", wrecked || ratio < 0.3F);
        hideIf(snapshots, "wingRR2", wrecked || ratio < 0.3F);
        hideIf(snapshots, "wingLB", wrecked || ratio < 0.3F);
        hideIf(snapshots, "youyi", wrecked);
        hideIf(snapshots, "guajia2", wrecked);
        hideIf(snapshots, "guajia3", wrecked);
        hideIf(snapshots, "weiyiR", wrecked);
        boolean hulk = vehicle.wreckState() == VehicleEntity.WRECK_BURNING;
        for (String bone : new String[]{"gear", "gear2", "gear3"}) {
            hideIf(snapshots, bone, hulk);
        }
        float stowed = (1.0F - vehicle.jetGear(partialTick)) * 90.0F * Mth.DEG_TO_RAD;
        for (String bone : new String[]{"gear", "gear2", "gear3"}) {
            snapshots.ifPresent(bone, snapshot -> {
                // Folds forward (towards the nose, model -Z) and hides once fully up.
                snapshot.setRotX(stowed);
                if (stowed >= 89.0F * Mth.DEG_TO_RAD) {
                    snapshot.skipRender(true);
                    snapshot.skipChildrenRender(true);
                }
            });
        }
        for (String bone : A10_UNUSED_STORES) {
            snapshots.ifPresent(bone, snapshot -> {
                snapshot.skipRender(true);
                snapshot.skipChildrenRender(true);
            });
        }
        int medium = a10WeaponSlotAmmo(vehicle, "vehicle_agm65_missile");
        int large = a10WeaponSlotAmmo(vehicle, "vehicle_agm65g_missile");
        // missile2 / missile1 hang under the left wing (the muzzle side), missile3 / missile4 under the right.
        hideIf(snapshots, "missile2", medium < 2);
        hideIf(snapshots, "missile3", medium < 1);
        hideIf(snapshots, "missile1", large < 2);
        hideIf(snapshots, "missile4", large < 1);
    }

    private static int a10WeaponSlotAmmo(VehicleEntity vehicle, String weaponPath) {
        var weapons = vehicle.vehicleData().defaults().weapons();
        for (int slot = 0; slot < weapons.size(); slot++) {
            if (weapons.get(slot).weaponId().getPath().equals(weaponPath)) {
                return vehicle.vehicleWeaponSlotAmmo(slot);
            }
        }
        return 0;
    }

    private static void hideIf(com.geckolib.renderer.base.BoneSnapshots snapshots, String bone, boolean hide) {
        if (hide) {
            snapshots.ifPresent(bone, snapshot -> {
                snapshot.skipRender(true);
                snapshot.skipChildrenRender(true);
            });
        }
    }

    private static void updateTurret(VehicleEntity vehicle, float partialTick, com.geckolib.renderer.base.BoneSnapshots snapshots, float minPitch, float maxPitch) {
        snapshots.ifPresent("turret", snapshot -> snapshot.setRotY(vehicle.turretYaw(partialTick) * Mth.DEG_TO_RAD));
        snapshots.ifPresent("barrel", snapshot -> snapshot.setRotX(Mth.clamp(-vehicle.turretPitch(partialTick), minPitch, maxPitch) * Mth.DEG_TO_RAD));
    }

    private static void updateHelicopterRotors(VehicleEntity vehicle, float partialTick, com.geckolib.renderer.base.BoneSnapshots snapshots) {
        float rotorRotation = vehicle.propellerRot(partialTick);
        snapshots.ifPresent("propeller", snapshot -> snapshot.setRotY(rotorRotation));
        snapshots.ifPresent("tailPropeller", snapshot -> snapshot.setRotX(-6.0F * rotorRotation));
    }
}
