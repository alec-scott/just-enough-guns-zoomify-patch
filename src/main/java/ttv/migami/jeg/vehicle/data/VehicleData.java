package ttv.migami.jeg.vehicle.data;

import net.minecraft.resources.Identifier;

public final class VehicleData {
    private final DefaultVehicleData defaults;
    /** Hitboxes for a deployed pose ("obb_deployed"), for vehicles that unfold when parked; null when it has none. */
    @org.jetbrains.annotations.Nullable
    private final ttv.migami.jeg.vehicle.data.subdata.OBBInfo deployedObb;

    public VehicleData(DefaultVehicleData defaults) {
        this(defaults, null);
    }

    public VehicleData(DefaultVehicleData defaults, @org.jetbrains.annotations.Nullable ttv.migami.jeg.vehicle.data.subdata.OBBInfo deployedObb) {
        this.defaults = defaults;
        this.deployedObb = deployedObb;
    }

    @org.jetbrains.annotations.Nullable
    public ttv.migami.jeg.vehicle.data.subdata.OBBInfo deployedObb() {
        return this.deployedObb;
    }

    public Identifier id() {
        return this.defaults.id();
    }

    public DefaultVehicleData defaults() {
        return this.defaults;
    }
}
