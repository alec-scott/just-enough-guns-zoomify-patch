package ttv.migami.jeg.vehicle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.data.subdata.SeatInfo;
import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * A helicopter pilot's head in first person (the user, 2026-11-26: "you should be able to move your head around in the
 * pilot seat"). The mouse flies the aircraft, so the head turns while free look (C) is held: the mouse turns it within
 * the cockpit, as far as the seat's limits, and it turns, pitches and banks with the airframe as a head would. Before,
 * free look held the view at a world heading, so in a turn the cockpit swung round a fixed view, and letting go snapped
 * the view back to the nose. Now it eases back over about a fifth of a second.
 */
public final class PilotHeadLook {
    /** How long the head takes to come most of the way back to the nose once free look is let go (seconds). */
    private static final float RETURN_TIME = 0.09F;

    private static float yaw;
    private static float pitch;
    private static int vehicleId = -1;
    private static int seat = -1;
    private static long lastFrame;

    private PilotHeadLook() {
    }

    /** Whether this player's view is a helicopter pilot's, pinned to the airframe unless they look about. */
    public static boolean applies(LocalPlayer player, VehicleEntity vehicle) {
        if (!Minecraft.getInstance().options.getCameraType().isFirstPerson()
                || vehicle.vehicleData().defaults().vehicleType() != VehicleType.HELICOPTER || !vehicle.keepsCameraLevel()) {
            return false;
        }
        SeatInfo seat = seatOf(player, vehicle);
        return seat != null && seat.driver() && seat.sensitivityY() == 0.0F && seat.sensitivityZ() == 0.0F
                && seat.zoomCamera().useFixedCameraPos() && !VehicleClientState.zoomDown();
    }

    private static SeatInfo seatOf(LocalPlayer player, VehicleEntity vehicle) {
        int index = vehicle.getSeatIndex(player);
        if (index < 0 || index >= vehicle.vehicleData().defaults().seats().size()) {
            return null;
        }
        return vehicle.vehicleData().defaults().seats().get(index);
    }

    /** Free look is held: the head turns by the mouse (degrees, as Entity.turn takes them), within the seat's limits. */
    public static void turn(LocalPlayer player, VehicleEntity vehicle, double turnX, double turnY) {
        SeatInfo seatInfo = seatOf(player, vehicle);
        track(player, vehicle);
        if (seatInfo == null) {
            return;
        }
        yaw = Mth.clamp(yaw + (float) turnX * 0.15F, seatInfo.minYaw(), seatInfo.maxYaw());
        pitch = Mth.clamp(pitch + (float) turnY * 0.15F, -90.0F, 90.0F);
    }

    /**
     * The pilot's view this frame: the airframe's pinned view, turned by the head. Returns null when the head is
     * forward and free look is not held (the pinned view as it is).
     *
     * @param pinned the airframe's view (yaw, pitch, roll) as the seat pins it
     */
    public static Vec3 view(LocalPlayer player, VehicleEntity vehicle, Vec3 pinned) {
        track(player, vehicle);
        long now = System.nanoTime();
        float seconds = lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - lastFrame) / 1.0E9F);
        lastFrame = now;
        boolean held = VehicleClientState.freeLookDown();
        if (!held) {
            float keep = (float) Math.exp(-seconds / RETURN_TIME);
            yaw *= keep;
            pitch *= keep;
            if (Math.abs(yaw) < 0.05F && Math.abs(pitch) < 0.05F) {
                yaw = 0.0F;
                pitch = 0.0F;
            }
        }
        if (!held && yaw == 0.0F && pitch == 0.0F) {
            return null;
        }
        SeatInfo seatInfo = seatOf(player, vehicle);
        float minPitch = seatInfo == null ? -90.0F : seatInfo.minPitch();
        float maxPitch = seatInfo == null ? 90.0F : seatInfo.maxPitch();
        float viewPitch = Mth.clamp((float) pinned.y + pitch, minPitch, maxPitch);
        // The pitch the head can still add at this attitude: past the seat's limit, the mouse winds no further.
        pitch = viewPitch - (float) pinned.y;
        return new Vec3(pinned.x + yaw, viewPitch, pinned.z);
    }

    /** The head's turn from the nose (degrees, right positive as yaw is). */
    public static float yaw() {
        return yaw;
    }

    /** The head's tilt from the pinned view (degrees, down positive as pitch is). */
    public static float pitch() {
        return pitch;
    }

    /** Whether the head is turned (looking about, or on its way back to the nose). */
    public static boolean turned() {
        return yaw != 0.0F || pitch != 0.0F;
    }

    /** A different aircraft or seat starts with the head forward. */
    private static void track(LocalPlayer player, VehicleEntity vehicle) {
        int index = vehicle.getSeatIndex(player);
        if (vehicle.getId() != vehicleId || index != seat) {
            vehicleId = vehicle.getId();
            seat = index;
            yaw = 0.0F;
            pitch = 0.0F;
            lastFrame = 0L;
        }
    }
}
