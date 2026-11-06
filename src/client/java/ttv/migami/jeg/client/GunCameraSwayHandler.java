package ttv.migami.jeg.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import ttv.migami.jeg.item.GunItem;
import ttv.migami.jeg.vehicle.client.VehicleClientState;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

public final class GunCameraSwayHandler {
    private static final float YAW_SWAY_DEGREES = 0.45F;
    private static final float ROLL_SWAY_DEGREES = 1.35F;
    private static final float SMOOTHING = 0.22F;
    private static final float MAX_SHAKE_DEGREES = 3.2F;
    private static final float SHAKE_DECAY = 0.88F;
    private static float sway;
    private static float shake;

    private GunCameraSwayHandler() {}

    /** A blast nearby: the strongest one felt wins, and it dies away over about a second. */
    public static void addShake(float strength) {
        shake = Math.max(shake, Math.min(1.0F, strength));
    }

    private static void applyShake(PoseStack poseStack) {
        if (shake <= 0.001F) {
            shake = 0.0F;
            return;
        }
        float time = (float) (System.nanoTime() / 1.0E9D);
        float amount = shake * shake * MAX_SHAKE_DEGREES;
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(time * 41.0F) * amount));
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.sin(time * 53.0F) * amount * 0.7F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(time * 37.0F) * amount * 0.6F));
    }

    public static void tickShake() {
        shake *= SHAKE_DECAY;
    }

    public static void apply(PoseStack poseStack, float partialTick) {
        applyShake(poseStack);
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        float target = shouldSway(minecraft, player) ? lateralInput(minecraft) : 0.0F;
        sway = Mth.lerp(SMOOTHING, sway, target);
        if (Math.abs(sway) < 0.001F) {
            sway = 0.0F;
            return;
        }

        float yaw = sway * YAW_SWAY_DEGREES;
        float roll = -sway * ROLL_SWAY_DEGREES;
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(roll));
    }

    private static boolean shouldSway(Minecraft minecraft, LocalPlayer player) {
        if (player == null || player.isSpectator() || minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            return false;
        }
        if (!(player.getMainHandItem().getItem() instanceof GunItem)) {
            return false;
        }
        if (player.getVehicle() instanceof VehicleEntity vehicle) {
            return !VehicleClientState.isRidingVehicle()
                    || VehicleClientState.vehicleId() != vehicle.getId()
                    || !vehicle.shouldBanPassengerHand(player);
        }
        return true;
    }

    private static float lateralInput(Minecraft minecraft) {
        boolean left = minecraft.options.keyLeft.isDown();
        boolean right = minecraft.options.keyRight.isDown();
        if (left == right) {
            return 0.0F;
        }
        return left ? 1.0F : -1.0F;
    }
}
