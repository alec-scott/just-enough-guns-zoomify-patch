package ttv.migami.jeg.vehicle.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ttv.migami.jeg.vehicle.entity.Mk42Entity;

/** The Mk42 gun mount: turret, gun elevation and recoil are posed in {@link AbstractVehicleGeoRenderer}'s bone updater. */
public final class Mk42Renderer extends AbstractVehicleGeoRenderer<Mk42Entity> {
    public Mk42Renderer(EntityRendererProvider.Context context) {
        super(context, new Mk42GeoModel());
        this.shadowRadius = 2.0F;
    }
}
