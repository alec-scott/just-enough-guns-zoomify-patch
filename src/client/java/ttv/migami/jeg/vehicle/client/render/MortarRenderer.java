package ttv.migami.jeg.vehicle.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ttv.migami.jeg.vehicle.entity.MortarEntity;

/** The 81 mm mortar: tube and bipod are posed in {@link AbstractVehicleGeoRenderer}'s bone updater. */
public final class MortarRenderer extends AbstractVehicleGeoRenderer<MortarEntity> {
    public MortarRenderer(EntityRendererProvider.Context context) {
        super(context, new MortarGeoModel());
        this.shadowRadius = 0.35F;
    }
}
