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
        this.withRenderLayer(new VehicleGlassLayer<>(this));
        this.withRenderLayer(new VehicleBoatWaterMaskLayer<>(this));
        this.withRenderLayer(new VehicleGlowLayer<>(this));
        this.withRenderLayer(new VehicleBarrelHeatLayer<>(this));
        this.withRenderLayer(new VehicleExhaustGlowLayer<>(this));
        this.withRenderLayer(new VehicleFanBlurLayer<>(this));
        this.withRenderLayer(new VehicleLightsLayer<>(this));
        this.shadowRadius = 0.9F;
    }

    @Override
    public RenderType getRenderType(VehicleRenderState renderState, Identifier texture) {
        // Opaque (cutout), so shader packs light and shadow vehicles as they do mobs. Drawn translucent (as since the
        // port), Photon lit them in its flatter translucent pass: washed out by day, no proper shading. Compared side by
        // side with VehicleLookCompareClientGameTest. Glass is split out and drawn translucent by VehicleGlassLayer.
        return RenderTypes.entityCutout(ttv.migami.jeg.vehicle.client.resource.VehicleGlassTextures.split(texture).solid());
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
        // On an FSV-6's hitch: drawn from the truck's smooth position (SupportVehicleEntity.clientTowPose).
        var towed = ttv.migami.jeg.vehicle.entity.SupportVehicleEntity.clientTowPose(vehicle, partialTick);
        if (towed != null) {
            renderState.x = towed.position().x;
            renderState.z = towed.position().z;
            renderState.yaw = towed.yaw();
        }
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
        if (camera.isVisible(bounds)) {
            return true;
        }
        // A searchlight's pool on the ground can be in view while the helicopter overhead is not.
        AABB spot = AircraftLights.spotBounds(vehicle);
        return spot != null && camera.isVisible(spot);
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

    /** The bush plane's control surfaces per airframe, smoothed per tick: aileron, elevator, rudder (degrees), tick. */
    private static final java.util.Map<VehicleEntity, float[]> TOM6_SURFACES = new java.util.WeakHashMap<>();
    private static final java.util.Map<VehicleEntity, float[]> A10_SURFACES = new java.util.WeakHashMap<>();
    /** Each A-10's canopy: open now and a tick ago (0 shut, 1 open), and the tick it was last moved. */
    private static final java.util.Map<VehicleEntity, float[]> A10_CANOPY = new java.util.WeakHashMap<>();

    /**
     * The A-10's ailerons, elevators and rudders from its attitude rates (its pose, which a jet's quaternion drives; bank
     * right and nose down are positive), eased tick to tick as the bush plane's.
     */
    private static float[] a10Surfaces(VehicleEntity vehicle) {
        float[] s = A10_SURFACES.computeIfAbsent(vehicle, v -> new float[]{0.0F, 0.0F, 0.0F, -1.0F});
        if (s[3] != vehicle.tickCount) {
            s[3] = vehicle.tickCount;
            net.minecraft.world.phys.Vec3 now = vehicle.poseAngles(1.0F), before = vehicle.poseAngles(0.0F);
            float yawRate = Mth.wrapDegrees((float) (now.x - before.x));
            float pitchRate = Mth.wrapDegrees((float) (now.y - before.y));
            float rollRate = Mth.wrapDegrees((float) (now.z - before.z));
            s[0] += (Mth.clamp(rollRate * 4.0F, -20.0F, 20.0F) - s[0]) * 0.35F;
            s[1] += (Mth.clamp(pitchRate * 5.0F, -18.0F, 18.0F) - s[1]) * 0.35F;
            s[2] += (Mth.clamp(yawRate * 5.0F, -18.0F, 18.0F) - s[2]) * 0.35F;
        }
        return s;
    }

    /** The A-10's canopy, 0 shut to 1 open: open while it stands still on its gear with nobody aboard. */
    static float a10Canopy(VehicleEntity vehicle, float partialTick) {
        float[] c = A10_CANOPY.computeIfAbsent(vehicle, v -> new float[]{vehicle.getPassengers().isEmpty() ? 1.0F : 0.0F, 0.0F, -1.0F});
        if (c[2] != vehicle.tickCount) {
            c[2] = vehicle.tickCount;
            c[1] = c[0];
            boolean parked = vehicle.getPassengers().isEmpty() && vehicle.getDeltaMovement().horizontalDistance() < 0.05D
                    && vehicle.jetGear(1.0F) > 0.95F;
            c[0] = Mth.approach(c[0], parked ? 1.0F : 0.0F, parked ? 0.025F : 0.035F);
        }
        float open = Mth.lerp(partialTick, c[1], c[0]);
        return open * open * (3.0F - 2.0F * open);
    }

    /**
     * Ailerons, elevator and rudder from the attitude rates every client sees (roll right is positive and turns the
     * nose right, pitch positive is nose down): the stick and pedals as the airframe answers them.
     */
    private static float[] tom6Surfaces(VehicleEntity vehicle) {
        float[] s = TOM6_SURFACES.computeIfAbsent(vehicle, v -> new float[]{0.0F, 0.0F, 0.0F, -1.0F});
        if (s[3] != vehicle.tickCount) {
            s[3] = vehicle.tickCount;
            float rollRate = Mth.wrapDegrees(vehicle.roll(1.0F) - vehicle.roll(0.0F));
            float pitchRate = vehicle.getXRot() - vehicle.xRotO;
            float yawRate = Mth.wrapDegrees(vehicle.getYRot() - vehicle.yRotO);
            s[0] += (Mth.clamp(rollRate * 5.0F, -20.0F, 20.0F) - s[0]) * 0.35F;
            s[1] += (Mth.clamp(pitchRate * 6.0F, -20.0F, 20.0F) - s[1]) * 0.35F;
            s[2] += (Mth.clamp(yawRate * 6.0F, -20.0F, 20.0F) - s[2]) * 0.35F;
        }
        return s;
    }

    private void vehicleAxis(VehicleRenderState renderState, PoseStack poseStack) {
        float rootY = renderState.rootY;
        if (renderState.jetPose != null) {
            // The model faces -Z: turn it round, then by the jet's own orientation.
            poseStack.rotateAround(new org.joml.Quaternionf(renderState.jetPose).mul(Axis.YP.rotationDegrees(180.0F)), 0.0F, rootY, 0.0F);
            float stance = renderState.vehicle == null ? 0.0F : BushPlaneStance.of(renderState.vehicle);
            if (stance > 0.0F) {
                // A taildragger at rest sits on its tailwheel, nose up, about its main wheels (model z -24 px).
                poseStack.rotateAround(Axis.XP.rotationDegrees(stance), 0.0F, rootY, BushPlaneStance.MAIN_GEAR_Z);
            }
            return;
        }
        poseStack.rotateAround(Axis.YP.rotationDegrees(180.0F - renderState.yaw), 0.0F, rootY, 0.0F);
        poseStack.rotateAround(Axis.XP.rotationDegrees(-renderState.pitch), 0.0F, rootY, 0.0F);
        poseStack.rotateAround(Axis.ZP.rotationDegrees(-renderState.roll), 0.0F, rootY, 0.0F);
        VehicleEntity vehicle = renderState.vehicle;
        ttv.migami.jeg.vehicle.data.subdata.VehicleType type = vehicle == null ? null : vehicle.vehicleData().defaults().vehicleType();
        if ((type == ttv.migami.jeg.vehicle.data.subdata.VehicleType.LAND || type == ttv.migami.jeg.vehicle.data.subdata.VehicleType.BOAT)
                && !SPRUNG.containsKey(vehicle.vehicleDataId().getPath())) {
            // The hull on its suspension (VehicleFeel): squat, dive, roll and the drop after a landing; a boat's bank into
            // a turn and its pitch and heave on the chop. A vehicle whose running gear is its own bone rides it on its
            // hull alone (SPRUNG, updateSuspension).
            float partialTick = renderState.ageInTicks - vehicle.tickCount;
            poseStack.translate(0.0F, ttv.migami.jeg.vehicle.client.VehicleFeel.lift(vehicle, partialTick) * 0.6F, 0.0F);
            poseStack.rotateAround(Axis.XP.rotationDegrees(-ttv.migami.jeg.vehicle.client.VehicleFeel.pitch(vehicle, partialTick)), 0.0F, rootY, 0.0F);
            poseStack.rotateAround(Axis.ZP.rotationDegrees(-ttv.migami.jeg.vehicle.client.VehicleFeel.roll(vehicle, partialTick)), 0.0F, rootY, 0.0F);
        }
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
        ttv.migami.jeg.vehicle.data.subdata.TrackInfo tracks = vehicle.vehicleData().tracks();
        if (tracks != null) {
            updateTracks(vehicle, tracks, partialTick, snapshots);
        }
        String[] sprung = SPRUNG.get(vehicle.vehicleDataId().getPath());
        if (sprung != null) {
            updateSuspension(vehicle, sprung, partialTick, snapshots);
        }
        ttv.migami.jeg.vehicle.data.subdata.WheelInfo wheels = vehicle.vehicleData().wheels();
        if (wheels != null) {
            updateWheels(vehicle, wheels, partialTick, snapshots);
        }
        switch (vehicle.vehicleDataId().getPath()) {
            case "ah6" -> updateHelicopterRotors(vehicle, partialTick, snapshots);
            case "bmp2" -> {
                updateTurret(vehicle, partialTick, snapshots, -74.0F, 7.5F);
                // The muzzle flashes show only while that gun fires, flickering shot to shot (the generic idle left
                // them drawn all the time). Weapon slots: 0 the 30 mm AP, 1 the coaxial machine gun, 2 the 30 mm HE, 3 the
                // ATGM (the HE moved next to the AP, 2026-11-19).
                int firing = vehicle.activeFireWeaponSlot();
                boolean flicker = (vehicle.tickCount & 1) == 0;
                hideIf(snapshots, "cannon_flash", firing != 0 && firing != 2 || !flicker);
                hideIf(snapshots, "coax_flash", firing != 1 || !flicker);
                // The squad's rear doors swing open on their outer hinges while someone boards or leaves.
                float doors = VehicleDoors.open(vehicle, partialTick) * 100.0F * Mth.DEG_TO_RAD;
                swingAboutHinge(snapshots, "rear_door_left", -doors, -13.30012F, 46.66886F, -16.5F, 50.5F);
                swingAboutHinge(snapshots, "rear_door_right", doors, 13.30012F, 46.66886F, 16.5F, 50.5F);
            }
            case "lav150" -> {
                updateTurret(vehicle, partialTick, snapshots, -15.0F, 32.5F);
                // As the BMP-2's: the flashes show only while their gun fires (0 the 20 mm, 1 the coax).
                int firing = vehicle.activeFireWeaponSlot();
                boolean flicker = (vehicle.tickCount & 1) == 0;
                hideIf(snapshots, "cannon_flash", firing != 0 || !flicker);
                hideIf(snapshots, "coax_flash", firing != 1 || !flicker);
                // The turret's left hatch stands open while the rifleman (seat 5) rides in it, on its outer hinge.
                float lid = HatchState.open(vehicle, vehicle.passengerForSeat(5) != null, partialTick) * 110.0F * Mth.DEG_TO_RAD;
                swingAboutHingeZ(snapshots, "turret_hatch_left", lid, -5.1F, 41.82F, -10.2F, 42.3F);
            }
            case "mi28" -> {
                updateTurret(vehicle, partialTick, snapshots, -70.0F, 30.0F);
                updateHelicopterRotors(vehicle, partialTick, snapshots);
                snapshots.ifPresent("barrel2", snapshot -> snapshot.setRotZ(vehicle.rotaryGunAngle(partialTick) * Mth.DEG_TO_RAD));
                // The Kh-39s under the wingtip pylons fire one side then the other, as the A-10's missiles do: missile2
                // hangs on the muzzle side (the right, the model's -X), fired first.
                int kh39 = weaponSlotAmmo(vehicle, "vehicle_kh39_missile");
                hideIf(snapshots, "missile2", kh39 < 2);
                hideIf(snapshots, "missile1", kh39 < 1);
            }
            case "tom6" -> {
                // The bush plane: the propeller turns with the engine; the flaps droop as it slows for take-off and
                // landing (from the speed every client sees, as the flight model sets them).
                // The same turn as the A-10's fans, so VehicleFanBlurLayer blurs it into a disc at power.
                float spin = vehicle.jetFanRotation(partialTick) * Mth.DEG_TO_RAD;
                snapshots.ifPresent("propeller", snapshot -> snapshot.setRotZ(spin));
                var aircraft = vehicle.vehicleData().aircraft();
                double stall = vehicle.vehicleData().defaults().engine().jetStallSpeed();
                double speed = Math.abs(vehicle.clientForwardSpeed());
                float flaps = aircraft.hasFlaps() ? (float) Mth.clamp((aircraft.flapSpeedFactor() * stall - speed) / (0.3D * stall), 0.0D, 1.0D) : 0.0F;
                float droop = flaps * 30.0F * Mth.DEG_TO_RAD;
                snapshots.ifPresent("flap_left", snapshot -> snapshot.setRotX(droop));
                snapshots.ifPresent("flap_right", snapshot -> snapshot.setRotX(droop));
                // The control surfaces move with the airframe's rates (a positive rotation is trailing edge down, as
                // the flaps): rolling right lifts the right aileron and lowers the left; the elevator goes up as the nose
                // comes up; the rudder swings into the turn.
                float[] surfaces = tom6Surfaces(vehicle);
                float aileron = surfaces[0] * Mth.DEG_TO_RAD, elevator = surfaces[1] * Mth.DEG_TO_RAD, rudder = surfaces[2] * Mth.DEG_TO_RAD;
                snapshots.ifPresent("aileron_left", snapshot -> snapshot.setRotX(aileron));
                snapshots.ifPresent("aileron_right", snapshot -> snapshot.setRotX(-aileron));
                snapshots.ifPresent("elevator", snapshot -> snapshot.setRotX(elevator));
                // GeckoLib's frame has X mirrored from the model's (the pilot's right, +X in model pixels, is its -X),
                // so a turn to the right swings the trailing edge to its -X: a negative rotation about Y.
                snapshots.ifPresent("rudder", snapshot -> snapshot.setRotY(-rudder));
            }
            case "sentry" -> {
                if (vehicle instanceof ttv.migami.jeg.vehicle.entity.SentryEntity sentry) {
                    updateTurret(sentry, partialTick, snapshots, -30.0F, 60.0F);
                    // The belt empties from the can end as the hold runs down (a full belt from 128 rounds).
                    int rounds = sentry.vehicleRifleAmmo();
                    for (int i = 1; i <= 4; i++) {
                        hideIf(snapshots, "belt_" + i, rounds <= (i - 1) * 32);
                    }
                    // Its operator in first person doesn't see the lamp: it sat in the sight's view, lit (review, 2026-11-18).
                    var mc = net.minecraft.client.Minecraft.getInstance();
                    boolean operatorView = mc.player != null && mc.player.getVehicle() == sentry && mc.options.getCameraType().isFirstPerson();
                    int lamp = operatorView ? 0 : sentry.statusLamp();
                    hideIf(snapshots, "lamp_green", lamp != 1);
                    hideIf(snapshots, "lamp_amber", lamp != 2);
                    hideIf(snapshots, "lamp_red", lamp != 3);
                }
            }
            case "hpj11" -> {
                if (vehicle instanceof ttv.migami.jeg.vehicle.entity.Hpj11Entity ciws) {
                    updateHpj11(ciws, partialTick, snapshots);
                }
            }
            case "speedboat" -> {
                // The bow gun; the hull rides bow-up on the plane; the screw and rudder follow the hull's motion.
                updateTurret(vehicle, partialTick, snapshots, -25.0F, 50.0F);
                float rise = vehicle.boatBowRise(partialTick) * Mth.DEG_TO_RAD;
                snapshots.ifPresent("root", snapshot -> snapshot.setRotX(rise));
                float screw = vehicle.boatPropellerAngle(partialTick) * Mth.DEG_TO_RAD;
                snapshots.ifPresent("propeller", snapshot -> snapshot.setRotZ(screw));
                float rudder = vehicle.boatRudder(partialTick) * Mth.DEG_TO_RAD;
                snapshots.ifPresent("rudder", snapshot -> snapshot.setRotY(rudder));
            }
            case "a10" -> updateA10(vehicle, partialTick, snapshots);
            case "mortar" -> {
                if (vehicle instanceof ttv.migami.jeg.vehicle.entity.MortarEntity mortar) {
                    updateMortar(mortar, partialTick, snapshots);
                }
            }
            case "tow" -> {
                updateTurret(vehicle, partialTick, snapshots, -40.0F, 40.0F);
                // The round shows in the tube while one is loaded.
                hideIf(snapshots, "missile", weaponSlotAmmo(vehicle, "vehicle_tow_missile") < 1);
                // The gunner looks through the sight: in their own first-person view its housing, around the camera, isn't drawn.
                var mc = net.minecraft.client.Minecraft.getInstance();
                hideIf(snapshots, "sight", mc.player != null && mc.player.getVehicle() == vehicle && mc.options.getCameraType().isFirstPerson());
            }
            default -> {
                if (vehicle instanceof ttv.migami.jeg.vehicle.entity.ShellGunEntity gun) {
                    updateTurret(vehicle, partialTick, snapshots, gun.minElevation(), gun.maxElevation());
                    // SBW's fire clips, driven from the synced shots: each barrel slides back and runs out.
                    var bones = gun.recoilBones();
                    for (int barrel = 0; barrel < bones.size(); barrel++) {
                        float recoil = gun.recoilPixels(barrel, partialTick);
                        snapshots.ifPresent(bones.get(barrel), snapshot -> snapshot.setTranslateZ(recoil));
                    }
                    if (gun instanceof ttv.migami.jeg.vehicle.entity.Type63Entity type63) {
                        // The tubes empty in order: rockets show only in the tubes still loaded.
                        int fired = ttv.migami.jeg.vehicle.entity.Type63Entity.TUBES - type63.rocketsLoaded();
                        for (int tube = 0; tube < ttv.migami.jeg.vehicle.entity.Type63Entity.TUBES; tube++) {
                            hideIf(snapshots, "rocket_" + (tube + 1), tube < fired);
                        }
                        // The handwheels turn as the mount is laid: several turns over the elevation and traverse.
                        float elevation = -vehicle.turretPitch(partialTick);
                        float traverse = vehicle.turretYaw(partialTick);
                        snapshots.ifPresent("handwheel_elevation", snapshot -> snapshot.setRotX(elevation * 8.0F * Mth.DEG_TO_RAD));
                        snapshots.ifPresent("handwheel_traverse", snapshot -> snapshot.setRotZ(traverse * 8.0F * Mth.DEG_TO_RAD));
                    }
                    if (gun instanceof ttv.migami.jeg.vehicle.entity.HowitzerEntity howitzer) {
                        updateHowitzer(howitzer, partialTick, snapshots);
                    }
                    if (gun instanceof ttv.migami.jeg.vehicle.entity.Plz05Entity plz05) {
                        updatePlz05(plz05, partialTick, snapshots);
                    }
                    if (gun instanceof ttv.migami.jeg.vehicle.entity.Yx100Entity yx100) {
                        updatePintleGun(yx100, yx100.passengerIn(ttv.migami.jeg.vehicle.entity.Yx100Entity.COMMANDER_SEAT),
                                "rws_mount", "rws", ttv.migami.jeg.vehicle.util.Yx100Rig.RWS_MIN_ELEVATION,
                                ttv.migami.jeg.vehicle.util.Yx100Rig.RWS_MAX_ELEVATION, partialTick, snapshots);
                    }
                }
            }
        }
    }

    /**
     * The HPJ-11: the mount and gun as any turret, the barrel cluster spinning with the rotary gun, the search radar
     * turning while the mount is manned or on automatic, and the tracking radar pointing at what it tracks.
     */
    private static void updateHpj11(ttv.migami.jeg.vehicle.entity.Hpj11Entity ciws, float partialTick, com.geckolib.renderer.base.BoneSnapshots snapshots) {
        updateTurret(ciws, partialTick, snapshots, -25.0F, 85.0F);
        float spin = ciws.rotaryGunAngle(partialTick) * Mth.DEG_TO_RAD;
        snapshots.ifPresent("barrel_cluster", snapshot -> snapshot.setRotZ(spin));
        boolean watching = ciws.automatic() || ciws.getControllingPassenger() != null;
        float sweep = watching ? (ciws.tickCount + partialTick) * 9.0F * Mth.DEG_TO_RAD : 0.0F;
        snapshots.ifPresent("search_radar", snapshot -> snapshot.setRotY(sweep));
        net.minecraft.world.entity.Entity target = ciws.tracked();
        float trackYaw = 0.0F, trackPitch = 0.0F;
        if (target != null) {
            net.minecraft.world.phys.Vec3 to = target.getBoundingBox().getCenter().subtract(ciws.position().add(0.0D, 3.25D, 0.0D));
            float worldYaw = (float) (Mth.atan2(-to.x, to.z) * Mth.RAD_TO_DEG);
            float hullYaw = Mth.rotLerp(partialTick, ciws.yRotO, ciws.getYRot());
            trackYaw = (-Mth.wrapDegrees(worldYaw - hullYaw) - ciws.turretYaw(partialTick)) * Mth.DEG_TO_RAD;
            trackPitch = (float) (Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z))) ;
        }
        float yaw = trackYaw, pitch = trackPitch;
        snapshots.ifPresent("tracking_radar", snapshot -> {
            snapshot.setRotY(yaw);
            snapshot.setRotX(pitch);
        });
    }

    /**
     * A gun on the turret roof aimed by its own gunner's view (the PLZ-05's 12.7 mm, the YX-100's weapon station): the
     * mount turns to the view's bearing, taken off the turret's (it rides the turret), and the gun elevates within its
     * limits; with nobody at it, it rests straight ahead.
     */
    private static void updatePintleGun(VehicleEntity vehicle, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity gunner,
                                        String mountBone, String gunBone, float minElevation, float maxElevation, float partialTick,
                                        com.geckolib.renderer.base.BoneSnapshots snapshots) {
        float yaw = 0.0F, elevation = 0.0F;
        if (gunner != null) {
            float hullYaw = Mth.rotLerp(partialTick, vehicle.yRotO, vehicle.getYRot());
            yaw = -Mth.wrapDegrees(gunner.getViewYRot(partialTick) - hullYaw) - vehicle.turretYaw(partialTick);
            elevation = Mth.clamp(-gunner.getViewXRot(partialTick), minElevation, maxElevation);
        }
        float mountYaw = Mth.wrapDegrees(yaw) * Mth.DEG_TO_RAD;
        float gunElevation = elevation * Mth.DEG_TO_RAD;
        snapshots.ifPresent(mountBone, snapshot -> snapshot.setRotY(mountYaw));
        snapshots.ifPresent(gunBone, snapshot -> snapshot.setRotX(gunElevation));
    }

    /**
     * The shared wheel driver (guide section 5): road wheels roll by the distance driven over their radius (the snapshot's
     * X rotation as the track driver's wheels), and the steered ones turn about their own vertical axis by the angle the
     * hull's turn rate gives for its wheelbase.
     */
    private static void updateWheels(VehicleEntity vehicle, ttv.migami.jeg.vehicle.data.subdata.WheelInfo wheels, float partialTick,
                                     com.geckolib.renderer.base.BoneSnapshots snapshots) {
        float travel = vehicle.wheelTravel(partialTick);
        for (var group : wheels.roll()) {
            float angle = travel / group.radius();
            for (String bone : group.bones()) {
                snapshots.ifPresent(bone, snapshot -> snapshot.setRotX(angle));
            }
        }
        float steer = -vehicle.wheelSteer(partialTick) * Mth.DEG_TO_RAD;
        for (String bone : wheels.steer()) {
            snapshots.ifPresent(bone, snapshot -> snapshot.setRotY(steer));
        }
    }

    /** Bone names of each side's links ({prefix + i, prefix + i + "_turn"}), by link count. */
    private static final java.util.Map<Integer, String[][]> TRACK_LINK_BONES = new java.util.concurrent.ConcurrentHashMap<>();
    /** Scratch for {@link ttv.migami.jeg.vehicle.data.subdata.TrackInfo#sample} (render thread only). */
    private static final float[] TRACK_SAMPLE = new float[3];

    /**
     * The shared track driver (guide section 5): each link of each side is moved along the track's path to where the
     * distance that track has run puts it, and turned to the path's direction; the road wheels, sprockets, idlers and
     * rollers turn by the same distance over their radius. The refit computed the path in the model's (file) sense; the
     * snapshot's X rotation is the negative of the file's.
     */
    /**
     * The sprung bones of vehicles whose running gear (tracks, road wheels, wheels) are bones of their own: the suspension's
     * squat, dive and roll (VehicleFeel) move these and leave the running gear on the ground. Rolling the whole model
     * pushed the outer track into the dirt in a hard turn and the front of the tracks into it braking (the user's review,
     * 2026-11-19: the BMP-2's and PLZ-05's). Each is listed with its pivot (file pixels): the first is the hull, about
     * whose pivot they all turn; any other is a top-level bone riding on it.
     */
    private static final java.util.Map<String, String[]> SPRUNG = java.util.Map.of(
            "bmp2", new String[]{"hull", "None_part_1"},
            "plz05", new String[]{"base"},
            "yx100", new String[]{"hull"},
            "lav150", new String[]{"hull"});
    /** The sprung bones' pivots (file pixels), where a second bone must turn about the hull's. */
    private static final java.util.Map<String, org.joml.Vector3f> SPRUNG_PIVOTS = java.util.Map.of(
            "bmp2/hull", new org.joml.Vector3f(0.0F, 11.64998F, 8.2125F),
            "bmp2/None_part_1", new org.joml.Vector3f(0.0F, 0.0F, 0.0F));

    /**
     * The hull on its suspension: the same turns the whole model had (VehicleFeel's pitch, roll and lift), on the sprung
     * bones alone. A further bone with a different pivot is moved by what turning about the hull's pivot instead of its
     * own adds, so the two stay together.
     */
    private static void updateSuspension(VehicleEntity vehicle, String[] bones, float partialTick, com.geckolib.renderer.base.BoneSnapshots snapshots) {
        float rotX = -ttv.migami.jeg.vehicle.client.VehicleFeel.pitch(vehicle, partialTick) * Mth.DEG_TO_RAD;
        float rotZ = -ttv.migami.jeg.vehicle.client.VehicleFeel.roll(vehicle, partialTick) * Mth.DEG_TO_RAD;
        float lift = ttv.migami.jeg.vehicle.client.VehicleFeel.lift(vehicle, partialTick) * 0.6F * 16.0F;
        String id = vehicle.vehicleDataId().getPath();
        org.joml.Vector3f hullPivot = SPRUNG_PIVOTS.get(id + "/" + bones[0]);
        org.joml.Matrix3f turn = new org.joml.Matrix3f().rotationZ(rotZ).rotateX(rotX);
        for (int i = 0; i < bones.length; i++) {
            float tx = 0.0F, ty = lift, tz = 0.0F;
            org.joml.Vector3f own = i == 0 || hullPivot == null ? null : SPRUNG_PIVOTS.get(id + "/" + bones[i]);
            if (own != null) {
                // Render space has the file's X negated (pivots and translations alike).
                org.joml.Vector3f d = new org.joml.Vector3f(-(hullPivot.x - own.x), hullPivot.y - own.y, hullPivot.z - own.z);
                org.joml.Vector3f turned = turn.transform(new org.joml.Vector3f(d));
                tx = -(d.x - turned.x);
                ty += d.y - turned.y;
                tz = d.z - turned.z;
            }
            float x = tx, y = ty, z = tz;
            snapshots.ifPresent(bones[i], snapshot -> {
                snapshot.setRotX(snapshot.getRotX() + rotX);
                snapshot.setRotZ(snapshot.getRotZ() + rotZ);
                snapshot.setTranslateX(snapshot.getTranslateX() + x);
                snapshot.setTranslateY(snapshot.getTranslateY() + y);
                snapshot.setTranslateZ(snapshot.getTranslateZ() + z);
            });
        }
    }

    private static void updateTracks(VehicleEntity vehicle, ttv.migami.jeg.vehicle.data.subdata.TrackInfo tracks, float partialTick,
                                     com.geckolib.renderer.base.BoneSnapshots snapshots) {
        int links = tracks.links();
        String[][] names = TRACK_LINK_BONES.computeIfAbsent(links, n -> {
            String[][] out = new String[4][n];
            for (int i = 0; i < n; i++) {
                out[0][i] = "track_link_left_" + i;
                out[1][i] = "track_link_left_" + i + "_turn";
                out[2][i] = "track_link_right_" + i;
                out[3][i] = "track_link_right_" + i + "_turn";
            }
            return out;
        });
        float pitch = tracks.length() / Math.max(1, links);
        for (int side = 0; side < 2; side++) {
            float travel = vehicle.trackTravel(side == 0, partialTick);
            for (int i = 0; i < links; i++) {
                tracks.sample(tracks.restS() + travel + i * pitch, TRACK_SAMPLE);
                float dz = TRACK_SAMPLE[0] - tracks.restZ();
                float dy = TRACK_SAMPLE[1] - tracks.restY();
                float turn = -TRACK_SAMPLE[2];
                snapshots.ifPresent(names[side * 2][i], snapshot -> {
                    snapshot.setTranslateY(dy);
                    snapshot.setTranslateZ(dz);
                });
                snapshots.ifPresent(names[side * 2 + 1][i], snapshot -> snapshot.setRotX(turn));
            }
        }
        float left = vehicle.trackTravel(true, partialTick);
        float right = vehicle.trackTravel(false, partialTick);
        for (var wheel : tracks.wheels()) {
            for (String bone : wheel.bones()) {
                float angle = (bone.contains("left") ? left : right) / wheel.radius();
                snapshots.ifPresent(bone, snapshot -> snapshot.setRotX(angle));
            }
        }
    }

    /**
     * The PLZ-05: the roof machine gun turns and elevates with its gunner's view (it rides the turret, so its yaw is
     * taken off the turret's), and the gunner's sight isn't drawn in the gunner's own first-person view.
     */
    private static void updatePlz05(ttv.migami.jeg.vehicle.entity.Plz05Entity gun, float partialTick, com.geckolib.renderer.base.BoneSnapshots snapshots) {
        updatePintleGun(gun, gun.passengerIn(ttv.migami.jeg.vehicle.entity.Plz05Entity.ROOF_GUNNER_SEAT), "hmg_mount", "hmg",
                ttv.migami.jeg.vehicle.util.Plz05Rig.HMG_MIN_ELEVATION, ttv.migami.jeg.vehicle.util.Plz05Rig.HMG_MAX_ELEVATION,
                partialTick, snapshots);
        var mc = net.minecraft.client.Minecraft.getInstance();
        hideIf(snapshots, "sight", mc.player != null && gun.passengerIn(ttv.migami.jeg.vehicle.entity.Plz05Entity.GUNNER_SEAT) == mc.player
                && mc.options.getCameraType().isFirstPerson());
    }

    /**
     * The howitzer (design/howitzer/generate.py's pose): the trails swing out as it deploys and in to meet for towing,
     * the spades fold down into the ground, the wheels roll as it is towed, and the handwheels turn as it is laid. Y
     * rotations are the negative of the generator's right-handed sense; X rotations are as authored.
     */
    private static void updateHowitzer(ttv.migami.jeg.vehicle.entity.HowitzerEntity gun, float partialTick, com.geckolib.renderer.base.BoneSnapshots snapshots) {
        float progress = gun.deployProgress(partialTick);
        float swing = ttv.migami.jeg.vehicle.util.HowitzerRig.trailAngle(progress) * Mth.DEG_TO_RAD;
        snapshots.ifPresent("trail_left", snapshot -> snapshot.setRotY(-swing));
        snapshots.ifPresent("trail_right", snapshot -> snapshot.setRotY(swing));
        float fold = ttv.migami.jeg.vehicle.util.HowitzerRig.spadeFold(progress) * Mth.DEG_TO_RAD;
        snapshots.ifPresent("spade_left", snapshot -> snapshot.setRotX(fold));
        snapshots.ifPresent("spade_right", snapshot -> snapshot.setRotX(fold));
        float roll = -gun.wheelRoll(partialTick);
        snapshots.ifPresent("wheel_left", snapshot -> snapshot.setRotX(roll));
        snapshots.ifPresent("wheel_right", snapshot -> snapshot.setRotX(roll));
        float elevation = gun.elevation(partialTick);
        float traverse = gun.turretYaw(partialTick);
        snapshots.ifPresent("handwheel_elevation", snapshot -> snapshot.setRotX(elevation * 6.0F * Mth.DEG_TO_RAD));
        snapshots.ifPresent("handwheel_traverse", snapshot -> snapshot.setRotX(traverse * 6.0F * Mth.DEG_TO_RAD));
    }

    /**
     * The mortar's tube rises to the laid elevation about its joint, and the bipod hinge takes the angle the refit
     * solved for it (MortarRig), so the feet stay on the ground at every elevation.
     */
    private static void updateMortar(ttv.migami.jeg.vehicle.entity.MortarEntity mortar, float partialTick, com.geckolib.renderer.base.BoneSnapshots snapshots) {
        float elevation = mortar.elevation(partialTick);
        snapshots.ifPresent("barrel", snapshot -> snapshot.setRotX(elevation * Mth.DEG_TO_RAD));
        float bipod = ttv.migami.jeg.vehicle.util.MortarRig.bipodAngle(elevation);
        snapshots.ifPresent("bipod_pivot", snapshot -> snapshot.setRotX(-bipod * Mth.DEG_TO_RAD));
    }

    /**
     * The spinner and fan disc of each engine, centred on the nacelle's axis: the fan's blades are painted on the disc.
     * Everything else is fixed: the intake's eight-panel lip (qianwaiyuan) was spun as if it were the fan, so it turned
     * out of line with the cowling behind it and its blurred copies stood out of the nacelle (the user's review,
     * 2026-11-19: "fix the engine animation and model"); spun, the tapered rear cowling and the exhaust duct
     * (houwaiyuan, houneiyuan) blurred into a see-through haze.
     */
    private static final String[] A10_FAN_BONES = {"qianzhou", "qianzhou2"};
    /** How far each half of a speed brake opens, in radians. */
    private static final float SPEED_BRAKE_ANGLE = 40.0F * Mth.DEG_TO_RAD;
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
        // The wheels roll forward (the model faces -Z, so that is a negative turn about X).
        float wheels = -vehicle.jetWheelRotation(partialTick) * Mth.DEG_TO_RAD;
        for (String bone : new String[]{"wheel1", "wheel2", "wheel3"}) {
            snapshots.ifPresent(bone, snapshot -> snapshot.setRotX(wheels));
        }
        // The GAU-8's seven barrels turn about the gun's axis.
        snapshots.ifPresent("maingun", snapshot -> snapshot.setRotZ(vehicle.jetGunAngle(partialTick) * Mth.DEG_TO_RAD));
        // Control surfaces (the A-10 animation upgrade, 2026-11-19), already their own hinged bones on the model
        // (design/a10/articulate.py names them): the elevators and rudders follow the airframe's rates. A positive turn
        // about X is trailing edge down, as the bush plane's. -X in model pixels is the pilot's left: wingRR (upper) and
        // wingRR2 (lower) there, wingLR and wingLR2 on the right wing. Nothing comes out from under the wing (the user,
        // 2026-11-15, and again 2026-11-19 when the speed brakes opened the lower halves): each aileron only rises, both
        // halves together, as the wing it is on goes up in a roll; the speed brake (below) lifts the upper halves alone.
        float[] surfaces = a10Surfaces(vehicle);
        float aileron = surfaces[0] * Mth.DEG_TO_RAD, elevator = surfaces[1] * Mth.DEG_TO_RAD, rudder = surfaces[2] * Mth.DEG_TO_RAD;
        float leftUp = Math.min(0.0F, aileron), rightUp = Math.min(0.0F, -aileron);
        snapshots.ifPresent("wingRR", snapshot -> snapshot.setRotX(leftUp));
        snapshots.ifPresent("wingRR2", snapshot -> snapshot.setRotX(leftUp));
        snapshots.ifPresent("wingLR", snapshot -> snapshot.setRotX(rightUp));
        snapshots.ifPresent("wingLR2", snapshot -> snapshot.setRotX(rightUp));
        snapshots.ifPresent("wingLB", snapshot -> snapshot.setRotX(elevator));
        snapshots.ifPresent("wingRB", snapshot -> snapshot.setRotX(elevator));
        snapshots.ifPresent("weiyiL", snapshot -> snapshot.setRotY(-rudder));
        snapshots.ifPresent("weiyiR", snapshot -> snapshot.setRotY(-rudder));
        // The canopy (its frame, bone61, hinged at the foot of its bow) stands open while the jet is parked with
        // nobody aboard, and closes over a second and a half once a pilot climbs in.
        float canopy = a10Canopy(vehicle, partialTick) * 42.0F * Mth.DEG_TO_RAD;
        snapshots.ifPresent("bone61", snapshot -> snapshot.setRotX(canopy));
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
        // Speed brakes: each wingtip aileron's upper half rises out of the top of the wing. The lower halves (wingLR2,
        // wingRR2) stay closed: the user wants nothing coming out under the wing (2026-11-15), although the real
        // A-10's decelerons split both ways.
        float split = vehicle.jetSpeedBrake(partialTick) * SPEED_BRAKE_ANGLE;
        if (split > 0.0F) {
            for (String upper : new String[]{"wingLR", "wingRR"}) {
                snapshots.ifPresent(upper, snapshot -> snapshot.setRotX(Math.min(snapshot.getRotX(), -split)));
            }
        }
        for (String bone : A10_UNUSED_STORES) {
            snapshots.ifPresent(bone, snapshot -> {
                snapshot.skipRender(true);
                snapshot.skipChildrenRender(true);
            });
        }
        int medium = weaponSlotAmmo(vehicle, "vehicle_agm65_missile");
        int large = weaponSlotAmmo(vehicle, "vehicle_agm65g_missile");
        // missile2 / missile1 hang under the left wing (the muzzle side), missile3 / missile4 under the right.
        hideIf(snapshots, "missile2", medium < 2);
        hideIf(snapshots, "missile3", medium < 1);
        hideIf(snapshots, "missile1", large < 2);
        hideIf(snapshots, "missile4", large < 1);
    }

    private static int weaponSlotAmmo(VehicleEntity vehicle, String weaponPath) {
        var weapons = vehicle.vehicleData().defaults().weapons();
        for (int slot = 0; slot < weapons.size(); slot++) {
            if (weapons.get(slot).weaponId().getPath().equals(weaponPath)) {
                return vehicle.vehicleWeaponSlotAmmo(slot);
            }
        }
        return 0;
    }

    /**
     * Turns a door about Y on a hinge other than its bone's pivot (SBW's pivots sit mid-door): the bone turns about its
     * pivot P and is moved by (I - R)(H - P), so it turns about the hinge H instead. Coordinates are GeckoLib's (model
     * pixels with X mirrored from the Bedrock file), x and z only.
     */
    private static void swingAboutHinge(com.geckolib.renderer.base.BoneSnapshots snapshots, String bone, float angle,
                                        float pivotX, float pivotZ, float hingeX, float hingeZ) {
        if (angle == 0.0F) {
            return;
        }
        float dx = hingeX - pivotX, dz = hingeZ - pivotZ;
        float cos = Mth.cos(angle), sin = Mth.sin(angle);
        float rx = dx * cos + dz * sin, rz = -dx * sin + dz * cos;
        snapshots.ifPresent(bone, snapshot -> {
            snapshot.setRotY(angle);
            snapshot.setTranslateX(dx - rx);
            snapshot.setTranslateZ(dz - rz);
        });
    }

    /** As {@link #swingAboutHinge}, about Z (a lid hinged along the hull's length), in GeckoLib's frame, x and y only. */
    private static void swingAboutHingeZ(com.geckolib.renderer.base.BoneSnapshots snapshots, String bone, float angle,
                                         float pivotX, float pivotY, float hingeX, float hingeY) {
        if (angle == 0.0F) {
            return;
        }
        float dx = hingeX - pivotX, dy = hingeY - pivotY;
        float cos = Mth.cos(angle), sin = Mth.sin(angle);
        float rx = dx * cos - dy * sin, ry = dx * sin + dy * cos;
        snapshots.ifPresent(bone, snapshot -> {
            snapshot.setRotZ(angle);
            snapshot.setTranslateX(dx - rx);
            snapshot.setTranslateY(dy - ry);
        });
    }

    /** A hatch that stands open while its seat is taken, easing open and shut over about half a second. 0 shut, 1 open. */
    static final class HatchState {
        private static final java.util.Map<Integer, float[]> STATE = new java.util.HashMap<>();

        static float open(VehicleEntity vehicle, boolean occupied, float partialTick) {
            float[] s = STATE.computeIfAbsent(vehicle.getId(), id -> new float[]{occupied ? 1.0F : 0.0F, occupied ? 1.0F : 0.0F, vehicle.tickCount});
            if (s[2] != vehicle.tickCount) {
                s[0] = s[1];
                s[1] = Mth.clamp(s[1] + (occupied ? 0.1F : -0.1F), 0.0F, 1.0F);
                s[2] = vehicle.tickCount;
            }
            if (STATE.size() > 256) {
                STATE.clear();
            }
            float x = Mth.lerp(partialTick, s[0], s[1]);
            return x * x * (3.0F - 2.0F * x);
        }
    }

    /**
     * Doors that open while someone boards or leaves (client side, from the passenger count): open over half a second,
     * held two seconds, closed over three quarters of one. 0 shut, 1 open.
     */
    static final class VehicleDoors {
        private static final java.util.Map<Integer, int[]> STATE = new java.util.HashMap<>();
        private static final int OPEN = 10, HOLD = 40, CLOSE = 15;

        static float open(VehicleEntity vehicle, float partialTick) {
            int[] s = STATE.computeIfAbsent(vehicle.getId(), id -> new int[]{vehicle.getPassengers().size(), Integer.MIN_VALUE / 2});
            int count = vehicle.getPassengers().size();
            if (count != s[0]) {
                // Already open: keep them open rather than swinging them shut and open again.
                float now = shape(vehicle.tickCount - s[1]);
                s[0] = count;
                s[1] = vehicle.tickCount - (now > 0.0F ? Math.round(now * OPEN) : 0);
            }
            if (STATE.size() > 256) {
                STATE.clear();
            }
            return shape(vehicle.tickCount - s[1] + partialTick);
        }

        private static float shape(float t) {
            if (t < 0.0F || t > OPEN + HOLD + CLOSE) return 0.0F;
            float x = t < OPEN ? t / OPEN : t < OPEN + HOLD ? 1.0F : 1.0F - (t - OPEN - HOLD) / CLOSE;
            return x * x * (3.0F - 2.0F * x);
        }
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
