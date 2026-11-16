package ttv.migami.jeg.vehicle.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ttv.migami.jeg.vehicle.entity.HowitzerEntity;

/** The 105 mm howitzer: laying, recoil, trails, spades, wheels and handwheels are posed in {@link AbstractVehicleGeoRenderer}. */
public final class HowitzerRenderer extends AbstractVehicleGeoRenderer<HowitzerEntity> {
    public HowitzerRenderer(EntityRendererProvider.Context context) {
        super(context, new HowitzerGeoModel());
        this.shadowRadius = 1.0F;
    }
}
