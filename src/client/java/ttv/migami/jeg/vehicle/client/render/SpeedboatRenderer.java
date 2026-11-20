package ttv.migami.jeg.vehicle.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ttv.migami.jeg.vehicle.entity.SpeedboatEntity;

/**
 * The speedboat: bow gun, bow rise on the plane, propeller and rudder are posed in {@link AbstractVehicleGeoRenderer}; the
 * helm console's instruments in {@link SpeedboatCockpitLayer}.
 */
public final class SpeedboatRenderer extends AbstractVehicleGeoRenderer<SpeedboatEntity> {
    public SpeedboatRenderer(EntityRendererProvider.Context context) {
        super(context, new SpeedboatGeoModel());
        this.withRenderLayer(new SpeedboatCockpitLayer<>(this));
    }
}
