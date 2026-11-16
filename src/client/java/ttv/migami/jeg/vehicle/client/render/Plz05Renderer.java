package ttv.migami.jeg.vehicle.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ttv.migami.jeg.vehicle.entity.Plz05Entity;

/** The PLZ-05: the tracks, the gun, the roof machine gun and the sight are posed in {@link AbstractVehicleGeoRenderer}. */
public final class Plz05Renderer extends AbstractVehicleGeoRenderer<Plz05Entity> {
    public Plz05Renderer(EntityRendererProvider.Context context) {
        super(context, new Plz05GeoModel());
        this.shadowRadius = 2.0F;
    }
}
