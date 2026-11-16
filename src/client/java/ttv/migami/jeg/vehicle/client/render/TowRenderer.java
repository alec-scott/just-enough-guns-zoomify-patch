package ttv.migami.jeg.vehicle.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ttv.migami.jeg.vehicle.entity.TowEntity;

/** The TOW launcher: traverse, elevation and the loaded round are posed in {@link AbstractVehicleGeoRenderer}. */
public final class TowRenderer extends AbstractVehicleGeoRenderer<TowEntity> {
    public TowRenderer(EntityRendererProvider.Context context) {
        super(context, new TowGeoModel());
        this.shadowRadius = 0.6F;
    }
}
