package ttv.migami.jeg.vehicle.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/** The 4x4 pickup and its technicals (design/pickup): one renderer for the four, each its own model. */
public final class PickupRenderer<T extends VehicleEntity> extends AbstractVehicleGeoRenderer<T> {
    public PickupRenderer(EntityRendererProvider.Context context, String id) {
        super(context, new NamedVehicleGeoModel(id));
    }
}
