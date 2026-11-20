package ttv.migami.jeg.vehicle.client.render;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import ttv.migami.jeg.vehicle.entity.SentryEntity;

/** The sentry: head, gun, belt and status lamp are posed in {@link AbstractVehicleGeoRenderer}. */
public final class SentryRenderer extends AbstractVehicleGeoRenderer<SentryEntity> {
    public SentryRenderer(EntityRendererProvider.Context context) {
        super(context, new SentryGeoModel());
    }
}
