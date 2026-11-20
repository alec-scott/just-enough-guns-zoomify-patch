package ttv.migami.jeg.vehicle.client;

import ttv.migami.jeg.vehicle.entity.ShellGunEntity;

/**
 * A shell gun's fire control (the Mk42's, the BL-132's): the shared shell predictor ({@link ShellAim}) from the next
 * barrel's muzzle along the bore as the gun is laid this frame. {@link ImpactMarkerRenderer} marks the predicted
 * impact for the gunner.
 */
public final class ShellGunAim {
    private ShellGunAim() {}

    public static ShellAim.Prediction predict(ShellGunEntity gun, float partialTick) {
        return ShellAim.predict(gun.level(), gun, gun.muzzle(partialTick),
                gun.barrelDirection(partialTick).scale(gun.ballistics().muzzleVelocity()), gun.ballistics());
    }
}
