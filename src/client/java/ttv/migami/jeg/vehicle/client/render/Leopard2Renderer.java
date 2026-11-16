package ttv.migami.jeg.vehicle.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ttv.migami.jeg.vehicle.entity.Leopard2Entity;

/** The Leopard 2A7: tracks, gun, recoil and the weapon station are posed in {@link AbstractVehicleGeoRenderer}. */
public final class Leopard2Renderer extends AbstractVehicleGeoRenderer<Leopard2Entity> {
    public Leopard2Renderer(EntityRendererProvider.Context context) {
        super(context, new Leopard2GeoModel());
        this.shadowRadius = 2.2F;
    }
}
