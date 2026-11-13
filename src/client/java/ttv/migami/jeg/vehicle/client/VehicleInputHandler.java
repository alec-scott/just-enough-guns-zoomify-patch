package ttv.migami.jeg.vehicle.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.network.ClientNetworkHandler;
import ttv.migami.jeg.vehicle.client.screen.VehicleScreen;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;

public final class VehicleInputHandler {
    private static final double CROSSHAIR_RANGE = 320.0D;
    private static final double POD_CROSSHAIR_RANGE = 640.0D;
    private static final int VEHICLE_INVENTORY_INPUT_BLOCK_TICKS = 4;
    private static boolean suppressPlayerInventoryClick;
    private static boolean suppressVehicleInventoryClick;
    private static boolean ignorePlayerInventoryUntilRelease;
    private static boolean ignoreVehicleInventoryUntilRelease;
    private static boolean vehicleInventoryWasDown;
    private static int vehicleInventoryInputBlockTicks;

    private static boolean autoForward;
    /** Forward speed (blocks per tick) that cruise control holds; locked when it is switched on. */
    private static float cruiseSpeed;
    /** A jet's cruise control: the airspeed (blocks a tick) its autothrottle holds, or below zero when off. */
    private static float jetCruise = -1.0F;
    /** How fast W and S move a jet's cruise speed while it is on: about 11 km/h a second. */
    private static final float JET_CRUISE_STEP = 0.008F;

    /** The airspeed a jet's cruise control holds (blocks a tick), or below zero when it is off. */
    public static float jetCruiseTarget() {
        return jetCruise;
    }
    private static boolean wasRidingVehicle;

    // Double-tap a strafe key and keep it held to roll the helicopter that way by hand; letting go hands the roll back.
    private static final int BARREL_ROLL_DOUBLE_TAP_TICKS = 7;
    private static boolean leftWasDown;
    private static boolean rightWasDown;
    private static int leftTapAge = Integer.MAX_VALUE;
    private static int rightTapAge = Integer.MAX_VALUE;
    private static int barrelRollRequest;

    private VehicleInputHandler() {}

    /** Returns -1 (roll left), 1 (roll right) or 0: the double-tapped strafe key, for as long as it stays held. */
    private static int tickBarrelRollRequest(Minecraft minecraft, boolean helicopterPilot) {
        boolean leftDown = minecraft.options.keyLeft.isDown();
        boolean rightDown = minecraft.options.keyRight.isDown();
        if (leftTapAge < Integer.MAX_VALUE) {
            leftTapAge++;
        }
        if (rightTapAge < Integer.MAX_VALUE) {
            rightTapAge++;
        }
        if (helicopterPilot && leftDown && !leftWasDown) {
            if (leftTapAge <= BARREL_ROLL_DOUBLE_TAP_TICKS) {
                barrelRollRequest = -1;
                leftTapAge = Integer.MAX_VALUE;
            } else {
                leftTapAge = 0;
            }
        }
        if (helicopterPilot && rightDown && !rightWasDown) {
            if (rightTapAge <= BARREL_ROLL_DOUBLE_TAP_TICKS) {
                barrelRollRequest = 1;
                rightTapAge = Integer.MAX_VALUE;
            } else {
                rightTapAge = 0;
            }
        }
        if ((barrelRollRequest < 0 && !leftDown) || (barrelRollRequest > 0 && !rightDown) || !helicopterPilot) {
            barrelRollRequest = 0;
        }
        leftWasDown = leftDown;
        rightWasDown = rightDown;
        return barrelRollRequest;
    }

    private static void setAutoForward(Minecraft minecraft, VehicleEntity vehicle, boolean enabled) {
        autoForward = enabled;
        if (enabled) {
            cruiseSpeed = forwardSpeed(vehicle);
        }
        minecraft.gui.setOverlayMessage(enabled
                ? net.minecraft.network.chat.Component.translatable("message.jeg.vehicle.auto_forward_on", Math.round(cruiseSpeed * 20.0F))
                : net.minecraft.network.chat.Component.translatable("message.jeg.vehicle.auto_forward_off"), false);
    }

    private static float forwardSpeed(VehicleEntity vehicle) {
        return (float) Math.max(0.0D, vehicle.getDeltaMovement().dot(net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, vehicle.getYRot())));
    }

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getConnection() == null || !(player.getVehicle() instanceof VehicleEntity vehicle)) {
            VehicleClientState.clear();
            autoForward = false;
            jetCruise = -1.0F;
            ignorePlayerInventoryUntilRelease = false;
            ignoreVehicleInventoryUntilRelease = false;
            vehicleInventoryWasDown = false;
            vehicleInventoryInputBlockTicks = 0;
            if (wasRidingVehicle && player != null && !minecraft.options.getCameraType().isFirstPerson()) {
                // Leaving a vehicle drops a third-person chase view back to first person.
                minecraft.options.setCameraType(CameraType.FIRST_PERSON);
            }
            wasRidingVehicle = false;
            return;
        }
        wasRidingVehicle = true;

        boolean vehicleInventoryDown = minecraft.options.keyInventory.isDown();
        if (vehicleInventoryInputBlockTicks > 0) {
            vehicleInventoryInputBlockTicks--;
            while (minecraft.options.keyInventory.consumeClick()) {
            }
        }
        if (ignoreVehicleInventoryUntilRelease && !vehicleInventoryDown) {
            ignoreVehicleInventoryUntilRelease = false;
        }
        if (ignorePlayerInventoryUntilRelease && !KeyBindings.VEHICLE_PLAYER_INVENTORY.isDown()) {
            ignorePlayerInventoryUntilRelease = false;
        }

        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        if (minecraft.screen != null) {
            VehicleClientState.syncMousePosition(mouseX, mouseY);
        } else {
            VehicleClientState.setMousePosition(mouseX, mouseY);
        }
        boolean freeLook = KeyBindings.vehicleFreeLookDown();
        boolean seek = KeyBindings.VEHICLE_SEEK.isDown();
        boolean aircraftControls = isAircraftDriver(player, vehicle);
        // Zoomed in a jet the mouse turns the targeting pod's view, not the jet.
        boolean podView = vehicle.isJet() && minecraft.options.keyUse.isDown() && vehicle.canPassengerUseSelectedVehicleWeapon(player);
        if (aircraftControls && minecraft.screen == null) {
            if (vehicle.isJet()) {
                // A jet's stick follows the player's own mouse sensitivity and invert setting (1x at the default).
                double setting = minecraft.options.sensitivity().get() * 0.6D + 0.2D;
                float scale = (float) (setting * setting * setting / (0.5D * 0.5D * 0.5D));
                VehicleClientState.updateAircraftMouse(0.1F * scale, 0.35F, 0.3F, minecraft.options.invertMouseY().get(), freeLook || podView);
            } else {
                VehicleClientState.updateAircraftMouse(0.1F, 0.5F, 0.35F, false, freeLook);
            }
        }
        boolean reload = KeyBindings.RELOAD.consumeClick();
        int weaponSlot = -1;
        for (int index = 0; index < minecraft.options.keyHotbarSlots.length; index++) {
            if (minecraft.options.keyHotbarSlots[index].consumeClick()) {
                weaponSlot = vehicle.vehicleWeaponIndexForDisplaySlot(player, index);
                break;
            }
        }
        boolean vehicleZoom = minecraft.options.keyUse.isDown() && vehicle.canPassengerUseSelectedVehicleWeapon(player);
        VehicleClientState.update(vehicle, freeLook, vehicleZoom, seek);
        if (KeyBindings.VEHICLE_CHANGE_SEAT.consumeClick()) {
            ClientNetworkHandler.sendVehicleChangeSeat(vehicle.getId());
        }
        if (KeyBindings.VEHICLE_DISMOUNT.consumeClick()) {
            vehicle.clearClientControlState();
            VehicleClientState.clear();
            ClientNetworkHandler.sendVehicleDismount(vehicle.getId());
            return;
        }
        boolean vehicleInventoryClick = !ignoreVehicleInventoryUntilRelease
                && vehicleInventoryInputBlockTicks <= 0
                && (minecraft.options.keyInventory.consumeClick() || (vehicleInventoryDown && !vehicleInventoryWasDown));
        vehicleInventoryWasDown = vehicleInventoryDown;
        if (suppressVehicleInventoryClick) {
            suppressVehicleInventoryClick = false;
            vehicleInventoryClick = false;
        }
        if (vehicleInventoryClick) {
            VehicleClientState.syncMousePosition(minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos());
            if (minecraft.screen instanceof VehicleScreen) {
                player.closeContainer();
                minecraft.setScreen(null);
                clearPendingVehicleInventoryClicks();
            } else if (minecraft.screen == null) {
                ClientNetworkHandler.sendVehicleOpenMenu(vehicle.getId());
                clearPendingVehicleInventoryClicks();
            }
        }
        boolean playerInventoryClick = !ignorePlayerInventoryUntilRelease && KeyBindings.VEHICLE_PLAYER_INVENTORY.consumeClick();
        if (suppressPlayerInventoryClick) {
            suppressPlayerInventoryClick = false;
            playerInventoryClick = false;
        }
        if (playerInventoryClick) {
            VehicleClientState.syncMousePosition(minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos());
            if (minecraft.screen instanceof InventoryScreen) {
                player.closeContainer();
                minecraft.setScreen(null);
            } else if (minecraft.screen == null) {
                minecraft.setScreen(new InventoryScreen(player));
            }
        }
        boolean helicopterPilot = aircraftControls && vehicle.vehicleData().defaults().vehicleType() == ttv.migami.jeg.vehicle.data.subdata.VehicleType.HELICOPTER;
        boolean autoForwardToggled = KeyBindings.VEHICLE_AUTO_FORWARD.consumeClick();
        if (!helicopterPilot) {
            autoForward = false;
        } else if (autoForwardToggled) {
            setAutoForward(minecraft, vehicle, !autoForward);
        } else if (autoForward && minecraft.options.keyDown.isDown()) {
            setAutoForward(minecraft, vehicle, false);
        } else if (autoForward && minecraft.options.keyUp.isDown()) {
            // Holding forward while cruising speeds up as usual; releasing it keeps the new speed.
            cruiseSpeed = Math.max(cruiseSpeed, forwardSpeed(vehicle));
        }
        boolean cruising = autoForward && !minecraft.options.keyUp.isDown();
        // A jet's cruise control is an autothrottle: switched on it holds the airspeed it had; W and S then set the
        // speed it holds rather than moving the throttle.
        boolean jetPilot = aircraftControls && vehicle.isJet();
        if (!jetPilot) {
            jetCruise = -1.0F;
        } else if (autoForwardToggled) {
            jetCruise = jetCruise >= 0.0F ? -1.0F : (float) vehicle.getDeltaMovement().length();
            minecraft.gui.setOverlayMessage(jetCruise >= 0.0F
                    ? net.minecraft.network.chat.Component.translatable("message.jeg.vehicle.jet_cruise_on", Math.round(jetCruise * 72.0F))
                    : net.minecraft.network.chat.Component.translatable("message.jeg.vehicle.auto_forward_off"), false);
        } else if (jetCruise >= 0.0F) {
            float top = (float) vehicle.jetTopSpeed();
            if (minecraft.options.keyUp.isDown()) {
                jetCruise = Math.min(top, jetCruise + JET_CRUISE_STEP);
            }
            if (minecraft.options.keyDown.isDown()) {
                jetCruise = Math.max(0.0F, jetCruise - JET_CRUISE_STEP);
            }
        }
        boolean jetCruising = jetPilot && jetCruise >= 0.0F;
        int barrelRoll = tickBarrelRollRequest(minecraft, helicopterPilot);
        VehicleInput input = new VehicleInput(
                !jetCruising && (minecraft.options.keyUp.isDown() || (cruising && cruiseSpeed > 0.0F)),
                !jetCruising && minecraft.options.keyDown.isDown(),
                minecraft.options.keyLeft.isDown(),
                minecraft.options.keyRight.isDown(),
                KeyBindings.VEHICLE_BRAKE_DESCEND.isDown(),
                minecraft.options.keyJump.isDown(),
                KeyBindings.VEHICLE_BRAKE_DESCEND.isDown(),
                minecraft.options.keyAttack.isDown(),
                reload,
                freeLook,
                KeyBindings.VEHICLE_SWITCH_WEAPON.consumeClick(),
                KeyBindings.VEHICLE_PREVIOUS_WEAPON.consumeClick(),
                weaponSlot,
                seek,
                KeyBindings.VEHICLE_DEPLOY_DECOY.consumeClick(),
                aircraftControls && !freeLook && !podView ? VehicleClientState.mouseLerpX() : 0.0F,
                aircraftControls && !freeLook && !podView ? VehicleClientState.mouseLerpY() : 0.0F,
                jetCruising ? Math.max(0.001F, jetCruise) : cruising ? cruiseSpeed : 0.0F,
                barrelRoll
        );
        vehicle.processClientInput(player, input);
        // A jet's guns are fixed down the nose, so its crosshair point is only sent from the targeting pod (zoomed),
        // where it designates the missiles' target.
        if (vehicle.usesVehiclePoseTransform() && !freeLook && (!vehicle.isJet() || podView)) {
            sendCrosshairPoint(minecraft, player, vehicle);
        }
        // The server can fire as soon as it receives this input, so send the matching aim point first.
        ClientNetworkHandler.sendVehicleInput(vehicle.getId(), input);
    }

    /** Tells the server what the crosshair is on, so rounds can be fired at it from muzzles away from the camera. */
    private static void sendCrosshairPoint(Minecraft minecraft, LocalPlayer player, VehicleEntity vehicle) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 from = camera.position();
        Vec3 look = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        // Skip what is between a chase camera and the aircraft: the crosshair is not meant to aim at that.
        double skip = minecraft.options.getCameraType().isFirstPerson() ? 0.0D : from.distanceTo(vehicle.position());
        Vec3 start = from.add(look.scale(skip));
        // A jet's targeting pod looks a long way down from altitude.
        Vec3 end = from.add(look.scale(vehicle.isJet() ? POD_CROSSHAIR_RANGE : CROSSHAIR_RANGE));
        BlockHitResult blockHit = player.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 reach = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, start, reach, new AABB(start, reach).inflate(1.0D),
                entity -> !entity.isSpectator() && entity.isPickable() && entity != vehicle && entity != player && entity.getVehicle() != vehicle,
                start.distanceToSqr(reach));
        Vec3 point = entityHit != null ? entityHit.getLocation() : reach;
        ClientNetworkHandler.sendVehicleAim(vehicle.getId(), point);
    }

    public static double adjustMouseSensitivity(double base) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !(player.getVehicle() instanceof VehicleEntity vehicle)) {
            return base;
        }
        int seatIndex = vehicle.getSeatIndex(player);
        if (seatIndex < 0 || seatIndex >= vehicle.vehicleData().defaults().seats().size()) {
            return base;
        }
        var seat = vehicle.vehicleData().defaults().seats().get(seatIndex);
        if (minecraft.options.getCameraType().isFirstPerson()) {
            clampSeatView(player, vehicle, seat);
        }
        float deltaX = VehicleClientState.mouseDeltaX();
        float deltaY = VehicleClientState.mouseDeltaY();
        if (deltaX == 0.0F && deltaY == 0.0F) {
            deltaX = Mth.wrapDegrees(player.getYRot() - player.yRotO);
            deltaY = player.getXRot() - player.xRotO;
        }
        VehicleClientState.setMouseDelta(deltaX, deltaY);
        float sensitivity = minecraft.options.getCameraType().isFirstPerson() ? seat.sensitivityY() : seat.sensitivityZ();
        if (VehicleClientState.isRidingVehicle()
                && VehicleClientState.vehicleId() == vehicle.getId()
                && VehicleClientState.zoomDown()) {
            sensitivity = seat.sensitivityX();
        } else if (VehicleCameraHandler.isFreeLookActive(vehicle)) {
            // Pilot seats use zero sensitivity because the mouse steers; free look needs normal mouse look.
            sensitivity = 1.0F;
        }
        return Math.max(0.0F, base * sensitivity);
    }

    public static boolean handleVehicleMouseTurn(Minecraft minecraft, double accumulatedDX, double accumulatedDY, double frameTime) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null || !(player.getVehicle() instanceof VehicleEntity vehicle)) {
            return false;
        }
        int seatIndex = vehicle.getSeatIndex(player);
        if (seatIndex < 0 || seatIndex >= vehicle.vehicleData().defaults().seats().size()) {
            return false;
        }

        double sensitivitySetting = minecraft.options.sensitivity().get() * 0.6000000238418579D + 0.20000000298023224D;
        double baseSensitivity = sensitivitySetting * sensitivitySetting * sensitivitySetting * 8.0D;
        double vehicleSensitivity = adjustMouseSensitivity(baseSensitivity);
        double turnX = accumulatedDX * vehicleSensitivity;
        double turnY = accumulatedDY * vehicleSensitivity;
        if (minecraft.options.invertMouseX().get()) {
            turnX = -turnX;
        }
        if (minecraft.options.invertMouseY().get()) {
            turnY = -turnY;
        }
        minecraft.getTutorial().onMouse(turnX, turnY);
        player.turn(turnX, turnY);

        var seat = vehicle.vehicleData().defaults().seats().get(seatIndex);
        clampSeatView(player, vehicle, seat);
        vehicle.refreshClientTurretAim(player);
        return true;
    }

    private static void clampSeatView(LocalPlayer player, VehicleEntity vehicle, ttv.migami.jeg.vehicle.data.subdata.SeatInfo seat) {
        float targetYaw = aircraftBoundYaw(player, vehicle, seat);
        float targetPitch = aircraftBoundPitch(player, vehicle, seat);
        float previousYaw = targetYaw;
        float previousPitch = targetPitch;
        if (isViewLockedToAircraft(player, vehicle, seat)) {
            // Interpolate the pilot with the aircraft; pinning both values to the latest tick makes the model step while turning.
            previousYaw = vehicle.yRotO;
            previousPitch = Mth.clamp(vehicle.cameraPitch(0.0F), seat.minPitch(), seat.maxPitch());
        }
        player.setYRot(targetYaw);
        player.yRotO = previousYaw;
        player.yHeadRot = targetYaw;
        player.yHeadRotO = previousYaw;
        player.yBodyRot = targetYaw;
        player.yBodyRotO = previousYaw;
        player.setXRot(targetPitch);
        player.xRotO = previousPitch;
    }

    private static boolean isViewLockedToAircraft(LocalPlayer player, VehicleEntity vehicle, ttv.migami.jeg.vehicle.data.subdata.SeatInfo seat) {
        return isAircraftDriver(player, vehicle) && seat.sensitivityY() == 0.0F && seat.sensitivityZ() == 0.0F && !VehicleClientState.freeLookDown()
                && !vehicle.isJetPodView(player);
    }

    private static float aircraftBoundYaw(LocalPlayer player, VehicleEntity vehicle, ttv.migami.jeg.vehicle.data.subdata.SeatInfo seat) {
        if (isViewLockedToAircraft(player, vehicle, seat)) {
            return vehicle.getYRot();
        }
        if (vehicle.usesVehiclePoseTransform()
                && !Minecraft.getInstance().options.getCameraType().isFirstPerson()
                && VehicleCameraHandler.isFreeLookActive(vehicle)) {
            // Third-person free look orbits the whole aircraft, so skip the seat's head-turn limits.
            return player.getYRot();
        }
        float relativeYaw = Mth.wrapDegrees(player.getYRot() - vehicle.getYRot());
        float clampedYaw = Mth.clamp(relativeYaw, seat.minYaw(), seat.maxYaw());
        return vehicle.getYRot() + clampedYaw;
    }

    private static float aircraftBoundPitch(LocalPlayer player, VehicleEntity vehicle, ttv.migami.jeg.vehicle.data.subdata.SeatInfo seat) {
        if (isViewLockedToAircraft(player, vehicle, seat)) {
            return Mth.clamp(vehicle.cameraPitch(1.0F), seat.minPitch(), seat.maxPitch());
        }
        return Mth.clamp(player.getXRot(), seat.minPitch(), seat.maxPitch());
    }

    private static boolean isAircraftDriver(LocalPlayer player, VehicleEntity vehicle) {
        int seatIndex = vehicle.getSeatIndex(player);
        if (seatIndex < 0 || seatIndex >= vehicle.vehicleData().defaults().seats().size()) {
            return false;
        }
        var seat = vehicle.vehicleData().defaults().seats().get(seatIndex);
        var type = vehicle.vehicleData().defaults().vehicleType();
        return seat.driver() && (type == ttv.migami.jeg.vehicle.data.subdata.VehicleType.HELICOPTER || type == ttv.migami.jeg.vehicle.data.subdata.VehicleType.AIRCRAFT);
    }

    public static void syncMouseToCurrentCursor() {
        Minecraft minecraft = Minecraft.getInstance();
        VehicleClientState.syncMousePosition(minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos());
    }

    public static void clearPendingPlayerInventoryClicks() {
        while (KeyBindings.VEHICLE_PLAYER_INVENTORY.consumeClick()) {
        }
        suppressPlayerInventoryClick = true;
        ignorePlayerInventoryUntilRelease = true;
    }

    public static void clearPendingVehicleInventoryClicks() {
        Minecraft minecraft = Minecraft.getInstance();
        while (minecraft.options.keyInventory.consumeClick()) {
        }
        suppressVehicleInventoryClick = true;
        ignoreVehicleInventoryUntilRelease = true;
        vehicleInventoryWasDown = minecraft.options.keyInventory.isDown();
        vehicleInventoryInputBlockTicks = VEHICLE_INVENTORY_INPUT_BLOCK_TICKS;
    }

    public static boolean shouldIgnorePlayerInventoryKey() {
        return ignorePlayerInventoryUntilRelease;
    }

    public static boolean shouldIgnoreVehicleInventoryKey() {
        return ignoreVehicleInventoryUntilRelease || vehicleInventoryInputBlockTicks > 0;
    }

    public static void suppressPlayerInventoryClickOnce() {
        suppressPlayerInventoryClick = true;
    }

    public static void suppressVehicleInventoryClickOnce() {
        suppressVehicleInventoryClick = true;
    }
}
