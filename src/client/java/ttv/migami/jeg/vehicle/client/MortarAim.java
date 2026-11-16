package ttv.migami.jeg.vehicle.client;

import net.minecraft.client.Minecraft;
import ttv.migami.jeg.vehicle.entity.MortarEntity;
import ttv.migami.jeg.vehicle.util.MortarRig;

/**
 * The mortar gunner's fire-control computer: the shared shell predictor ({@link ShellAim}) at the mortar's laid
 * bearing and elevation. Every few ticks it marks the predicted impact with particles for the gunner.
 */
public final class MortarAim {
    private static final int MARK_EVERY_TICKS = 4;

    private MortarAim() {}

    public static ShellAim.Prediction predict(MortarEntity mortar, float partialTick) {
        float elevation = mortar.elevation(partialTick);
        float yaw = mortar.getYRot();
        return ShellAim.predict(mortar.level(), mortar, MortarRig.muzzleWorld(mortar.position(), yaw, elevation),
                MortarRig.launchVelocity(yaw, elevation), MortarRig.BALLISTICS);
    }

    /** Marks the predicted impact for the gunner (only for them, and only on loaded terrain). */
    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.isPaused()
                || !(minecraft.player.getVehicle() instanceof MortarEntity mortar)
                || mortar.getControllingPassenger() != minecraft.player
                || minecraft.level.getGameTime() % MARK_EVERY_TICKS != 0) {
            return;
        }
        ShellAim.mark(minecraft, predict(mortar, 1.0F));
    }
}
