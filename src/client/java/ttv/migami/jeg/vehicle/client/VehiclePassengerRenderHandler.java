package ttv.migami.jeg.vehicle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

public final class VehiclePassengerRenderHandler {
    private VehiclePassengerRenderHandler() {}

    public static boolean shouldHideLocalPlayerPassenger(int renderStateId) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null
                && renderStateId == player.getId()
                && player.getVehicle() instanceof VehicleEntity vehicle
                && shouldHideLocalPlayerPassenger(vehicle);
    }

    public static boolean shouldHideLocalPlayerPassenger(VehicleEntity vehicle) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null
                && player.getVehicle() == vehicle
                && (vehicle.shouldHidePassenger(player) || shouldHideLocalZoomingHelicopterPassenger(vehicle));
    }

    private static boolean shouldHideLocalZoomingHelicopterPassenger(VehicleEntity vehicle) {
        return vehicle.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER
                && VehicleClientState.isRidingVehicle()
                && VehicleClientState.vehicleId() == vehicle.getId()
                && VehicleClientState.zoomDown();
    }

    /**
     * A rider of a helicopter or jet is drawn banked and pitched with it about their hips, as someone strapped into the
     * seat is: drawn upright, the AH-6's pilot leaned out of the open cabin in a hard sideslip (the user's review,
     * 2026-11-18). Pushes the pose and returns true when it did (the caller pops it).
     */
    public static boolean tiltWithAircraft(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state,
                                           com.mojang.blaze3d.vertex.PoseStack poseStack) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null || !(state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar)) {
            return false;
        }
        var entity = level.getEntity(avatar.id);
        if (entity == null) {
            return false;
        }
        VehicleEntity vehicle = entity.getVehicle() instanceof VehicleEntity seat && seat.usesVehiclePoseTransform() ? seat : null;
        // Seated: about the hips. Standing in a cabin: about the eye, which the cabin places (CabinMath.feetWorld puts the
        // body upright below it), so the tilt brings the feet back onto the floor. Drawn upright, someone standing in a
        // CH-47 banked 60 degrees hung 1.4 blocks to the low side, through the wall and the blocks stowed there (the
        // user, 2026-11-26: "the passenger in the back kind of bugs out when the chinook hard banks").
        float pivot = 0.6F;
        if (vehicle == null) {
            int cabin = ttv.migami.jeg.vehicle.cabin.CabinClient.cabinOf(entity);
            if (cabin < 0 || !(level.getEntity(cabin) instanceof VehicleEntity aboard) || !aboard.usesVehiclePoseTransform()) {
                return false;
            }
            vehicle = aboard;
            pivot = (float) ttv.migami.jeg.vehicle.cabin.CabinMath.EYE;
        }
        float partialTick = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double yaw = vehicle.poseAngles(partialTick).x;
        org.joml.Quaterniond tilt = vehicle.poseRotation(partialTick).rotateY(Math.toRadians(yaw));
        poseStack.pushPose();
        poseStack.translate(0.0F, pivot, 0.0F);
        poseStack.mulPose(new org.joml.Quaternionf((float) tilt.x, (float) tilt.y, (float) tilt.z, (float) tilt.w));
        poseStack.translate(0.0F, -pivot, 0.0F);
        return true;
    }

    /** Whether this render state's entity rides a vehicle's cab seat (a mob under a roof, VehicleEntity.seatsMobInCab). */
    public static boolean seatedInCab(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
        return state instanceof CabSeatedState cab && cab.jeg$seatedInCab();
    }

    /** Station poses (the user's review, 2026-11-18): a standing gunner ("standing_seats") stands, a launcher's gunner ("grip_seats") holds it. */
    public static void standAtStation(net.minecraft.client.renderer.entity.state.LivingEntityRenderState state) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null || !(state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState humanoid)
                || !(state instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState avatar)) {
            return;
        }
        var entity = level.getEntity(avatar.id);
        if (entity == null || !(entity.getVehicle() instanceof VehicleEntity vehicle)) {
            return;
        }
        int seat = vehicle.getSeatIndex(entity);
        if (vehicle.vehicleData().standingSeats().contains(seat)) {
            humanoid.isPassenger = false;
        }
        if (vehicle.vehicleData().gripSeats().contains(seat)) {
            // Both hands forward on the weapon's grips, as a gunner sighting a launcher (the TOW's gunner sat with his
            // arms at his sides in third person: the user's review, 2026-11-18).
            humanoid.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD;
            humanoid.leftArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.CROSSBOW_HOLD;
        }
    }
}
