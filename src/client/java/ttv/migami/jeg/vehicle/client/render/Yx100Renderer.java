package ttv.migami.jeg.vehicle.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ttv.migami.jeg.vehicle.entity.Yx100Entity;

/** The YX-100: tracks, gun, recoil and the weapon station are posed in {@link AbstractVehicleGeoRenderer}. */
public final class Yx100Renderer extends AbstractVehicleGeoRenderer<Yx100Entity> {
    public Yx100Renderer(EntityRendererProvider.Context context) {
        super(context, new Yx100GeoModel());
        this.shadowRadius = 2.2F;
    }
}
