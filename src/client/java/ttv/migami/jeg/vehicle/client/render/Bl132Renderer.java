package ttv.migami.jeg.vehicle.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ttv.migami.jeg.vehicle.entity.Bl132Entity;

/** The BL-132 gun mount: turret, elevation and each gun's recoil are posed in {@link AbstractVehicleGeoRenderer}. */
public final class Bl132Renderer extends AbstractVehicleGeoRenderer<Bl132Entity> {
    public Bl132Renderer(EntityRendererProvider.Context context) {
        super(context, new Bl132GeoModel());
        this.shadowRadius = 3.0F;
    }
}
