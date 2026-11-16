package ttv.migami.jeg.vehicle.client;

import net.minecraft.client.Minecraft;
import ttv.migami.jeg.vehicle.entity.ShellGunEntity;

/**
 * A shell gun's fire control (the Mk42's, the BL-132's): the shared shell predictor ({@link ShellAim}) from the next
 * barrel's muzzle along the bore as the gun is laid this frame. Every few ticks it marks the predicted impact for the
 * gunner.
 */
public final class ShellGunAim {
    private static final int MARK_EVERY_TICKS = 4;

    private ShellGunAim() {}

    public static ShellAim.Prediction predict(ShellGunEntity gun, float partialTick) {
        return ShellAim.predict(gun.level(), gun, gun.muzzle(partialTick),
                gun.barrelDirection(partialTick).scale(gun.ballistics().muzzleVelocity()), gun.ballistics());
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.isPaused()
                || !(minecraft.player.getVehicle() instanceof ShellGunEntity gun)
                || !gun.laysGun(minecraft.player)
                || minecraft.level.getGameTime() % MARK_EVERY_TICKS != 0) {
            return;
        }
        ShellAim.mark(minecraft, predict(gun, 1.0F));
    }
}
