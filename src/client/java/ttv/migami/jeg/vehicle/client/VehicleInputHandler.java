package ttv.migami.jeg.vehicle.client;

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
        boolean freeLook = KeyBindings.VEHICLE_FREE_LOOK.isDown();
        boolean seek = KeyBindings.VEHICLE_SEEK.isDown();
        boolean aircraftControls = isAircraftDriver(player, vehicle);
        if (aircraftControls && minecraft.screen == null) {
            VehicleClientState.updateAircraftMouse(0.1F, 0.5F, 0.35F, false, freeLook);
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
        int barrelRoll = tickBarrelRollRequest(minecraft, helicopterPilot);
        VehicleInput input = new VehicleInput(
                minecraft.options.keyUp.isDown() || (cruising && cruiseSpeed > 0.0F),
                minecraft.options.keyDown.isDown(),
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
                aircraftControls && !freeLook ? VehicleClientState.mouseLerpX() : 0.0F,
                aircraftControls && !freeLook ? VehicleClientState.mouseLerpY() : 0.0F,
                cruising ? cruiseSpeed : 0.0F,
                barrelRoll
        );
        vehicle.processClientInput(player, input);
        ClientNetworkHandler.sendVehicleInput(vehicle.getId(), input);
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
            previousPitch = Mth.clamp(vehicle.xRotO, seat.minPitch(), seat.maxPitch());
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
        return isAircraftDriver(player, vehicle) && seat.sensitivityY() == 0.0F && seat.sensitivityZ() == 0.0F && !VehicleClientState.freeLookDown();
    }

    private static float aircraftBoundYaw(LocalPlayer player, VehicleEntity vehicle, ttv.migami.jeg.vehicle.data.subdata.SeatInfo seat) {
        if (isAircraftDriver(player, vehicle) && seat.sensitivityY() == 0.0F && seat.sensitivityZ() == 0.0F && !VehicleClientState.freeLookDown()) {
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
        if (isAircraftDriver(player, vehicle) && seat.sensitivityY() == 0.0F && seat.sensitivityZ() == 0.0F && !VehicleClientState.freeLookDown()) {
            return Mth.clamp(vehicle.getXRot(), seat.minPitch(), seat.maxPitch());
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
