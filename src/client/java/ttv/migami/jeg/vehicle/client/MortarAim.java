package ttv.migami.jeg.vehicle.client;

import ttv.migami.jeg.vehicle.entity.MortarEntity;
import ttv.migami.jeg.vehicle.util.MortarRig;

/**
 * The mortar gunner's fire-control computer: the shared shell predictor ({@link ShellAim}) at the mortar's laid
 * bearing and elevation. {@link ImpactMarkerRenderer} marks the predicted impact for the gunner.
 */
public final class MortarAim {
    private MortarAim() {}

    public static ShellAim.Prediction predict(MortarEntity mortar, float partialTick) {
        float elevation = mortar.elevation(partialTick);
        float yaw = mortar.getYRot();
        return ShellAim.predict(mortar.level(), mortar, MortarRig.muzzleWorld(mortar.position(), yaw, elevation),
                MortarRig.launchVelocity(yaw, elevation), MortarRig.BALLISTICS);
    }
}
