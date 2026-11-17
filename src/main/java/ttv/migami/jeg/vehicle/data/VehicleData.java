package ttv.migami.jeg.vehicle.data;

import net.minecraft.resources.Identifier;

public final class VehicleData {
    private final DefaultVehicleData defaults;
    /** Hitboxes for a deployed pose ("obb_deployed"), for vehicles that unfold when parked; null when it has none. */
    @org.jetbrains.annotations.Nullable
    private final ttv.migami.jeg.vehicle.data.subdata.OBBInfo deployedObb;
    @org.jetbrains.annotations.Nullable
    private final ttv.migami.jeg.vehicle.data.subdata.TrackInfo tracks;
    @org.jetbrains.annotations.Nullable
    private final ttv.migami.jeg.vehicle.data.subdata.WheelInfo wheels;

    public VehicleData(DefaultVehicleData defaults) {
        this(defaults, null);
    }

    public VehicleData(DefaultVehicleData defaults, @org.jetbrains.annotations.Nullable ttv.migami.jeg.vehicle.data.subdata.OBBInfo deployedObb) {
        this(defaults, deployedObb, null);
    }

    public VehicleData(DefaultVehicleData defaults, @org.jetbrains.annotations.Nullable ttv.migami.jeg.vehicle.data.subdata.OBBInfo deployedObb,
                       @org.jetbrains.annotations.Nullable ttv.migami.jeg.vehicle.data.subdata.TrackInfo tracks) {
        this(defaults, deployedObb, tracks, null);
    }

    public VehicleData(DefaultVehicleData defaults, @org.jetbrains.annotations.Nullable ttv.migami.jeg.vehicle.data.subdata.OBBInfo deployedObb,
                       @org.jetbrains.annotations.Nullable ttv.migami.jeg.vehicle.data.subdata.TrackInfo tracks,
                       @org.jetbrains.annotations.Nullable ttv.migami.jeg.vehicle.data.subdata.WheelInfo wheels) {
        this.defaults = defaults;
        this.deployedObb = deployedObb;
        this.tracks = tracks;
        this.wheels = wheels;
    }

    /** The road wheels the shared wheel driver rolls and steers ("wheels"); null for a vehicle without them. */
    @org.jetbrains.annotations.Nullable
    public ttv.migami.jeg.vehicle.data.subdata.WheelInfo wheels() {
        return this.wheels;
    }

    /** The running gear the shared track driver moves ("tracks"); null for a vehicle without moving track links. */
    @org.jetbrains.annotations.Nullable
    public ttv.migami.jeg.vehicle.data.subdata.TrackInfo tracks() {
        return this.tracks;
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
