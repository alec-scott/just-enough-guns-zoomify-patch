package ttv.migami.jeg.vehicle.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ttv.migami.jeg.vehicle.entity.Type63Entity;

/** The Type-63: laying, the rockets left in the tubes and the handwheels are posed in {@link AbstractVehicleGeoRenderer}. */
public final class Type63Renderer extends AbstractVehicleGeoRenderer<Type63Entity> {
    public Type63Renderer(EntityRendererProvider.Context context) {
        super(context, new Type63GeoModel());
        this.shadowRadius = 0.9F;
    }
}
