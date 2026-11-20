package ttv.migami.jeg.vehicle.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ttv.migami.jeg.vehicle.entity.Hpj11Entity;

/** The HPJ-11: mount, gun, spinning barrels and both radars are posed in {@link AbstractVehicleGeoRenderer}. */
public final class Hpj11Renderer extends AbstractVehicleGeoRenderer<Hpj11Entity> {
    public Hpj11Renderer(EntityRendererProvider.Context context) {
        super(context, new Hpj11GeoModel());
    }
}
