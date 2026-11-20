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
        if (entity == null || !(entity.getVehicle() instanceof VehicleEntity vehicle) || !vehicle.usesVehiclePoseTransform()) {
            return false;
        }
        float partialTick = net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double yaw = vehicle.poseAngles(partialTick).x;
        org.joml.Quaterniond tilt = vehicle.poseRotation(partialTick).rotateY(Math.toRadians(yaw));
        poseStack.pushPose();
        float hips = 0.6F;
        poseStack.translate(0.0F, hips, 0.0F);
        poseStack.mulPose(new org.joml.Quaternionf((float) tilt.x, (float) tilt.y, (float) tilt.z, (float) tilt.w));
        poseStack.translate(0.0F, -hips, 0.0F);
        return true;
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
