package ttv.migami.jeg;

import net.fabricmc.api.ClientModInitializer;
import ttv.migami.jeg.client.FabricClientBootstrap;

public final class FabricClientEntrypoint implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FabricClientBootstrap.init();
        ttv.migami.jeg.vehicle.cabin.CabinClient.init();
        ttv.migami.jeg.vehicle.cabin.CabinBlockEffects.init();
    }
}
