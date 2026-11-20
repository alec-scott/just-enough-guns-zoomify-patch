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
    private final ttv.migami.jeg.vehicle.data.subdata.BoatInfo boat;
    private ttv.migami.jeg.vehicle.data.subdata.AircraftInfo aircraft = ttv.migami.jeg.vehicle.data.subdata.AircraftInfo.DEFAULT;
    private ttv.migami.jeg.vehicle.data.subdata.AmphibiousInfo amphibious = ttv.migami.jeg.vehicle.data.subdata.AmphibiousInfo.NONE;
    private java.util.List<Integer> turretRiders = java.util.List.of();
    private java.util.List<Integer> noFirstPersonSeats = java.util.List.of();
    private java.util.List<Integer> standingSeats = java.util.List.of();
    private java.util.List<Integer> gripSeats = java.util.List.of();
    private double mass = Double.NaN;

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
        this(defaults, deployedObb, tracks, wheels, ttv.migami.jeg.vehicle.data.subdata.BoatInfo.DEFAULT);
    }

    public VehicleData(DefaultVehicleData defaults, @org.jetbrains.annotations.Nullable ttv.migami.jeg.vehicle.data.subdata.OBBInfo deployedObb,
                       @org.jetbrains.annotations.Nullable ttv.migami.jeg.vehicle.data.subdata.TrackInfo tracks,
                       @org.jetbrains.annotations.Nullable ttv.migami.jeg.vehicle.data.subdata.WheelInfo wheels,
                       ttv.migami.jeg.vehicle.data.subdata.BoatInfo boat) {
        this.boat = boat;
        this.defaults = defaults;
        this.deployedObb = deployedObb;
        this.tracks = tracks;
        this.wheels = wheels;
    }

    /** A fixed-wing aircraft's own flight settings ("aircraft"); AircraftInfo.DEFAULT (the A-10's) when absent. */
    public ttv.migami.jeg.vehicle.data.subdata.AircraftInfo aircraft() {
        return this.aircraft;
    }

    public VehicleData withAircraft(ttv.migami.jeg.vehicle.data.subdata.AircraftInfo aircraft) {
        this.aircraft = aircraft;
        return this;
    }

    /** A land vehicle that swims ("amphibious"); AmphibiousInfo.NONE when absent. */
    public ttv.migami.jeg.vehicle.data.subdata.AmphibiousInfo amphibious() {
        return this.amphibious;
    }

    /** Seats that ride the turret and turn with it ("turret_riders": a hatch seat on the turret roof). */
    public java.util.List<Integer> turretRiders() {
        return this.turretRiders;
    }

    /** The vehicle's mass in tonnes ("mass"), NaN when unset: how heavy it feels to drive and ride. */
    public double mass() {
        return this.mass;
    }

    public VehicleData withMass(double mass) {
        this.mass = mass;
        return this;
    }

    /**
     * Seats with no first-person view ("no_first_person_seats"): a troop compartment's benches, whose eye would be
     * outside the hull or inside its walls. F5 skips first person there (the user's review, 2026-11-18).
     */
    public java.util.List<Integer> noFirstPersonSeats() {
        return this.noFirstPersonSeats;
    }

    /** Seats whose rider stands at their station ("standing_seats": a boat's bow gunner), drawn standing, not seated. */
    public java.util.List<Integer> standingSeats() {
        return this.standingSeats;
    }

    /** Seats whose rider holds the weapon with both hands ("grip_seats": the TOW's gunner on its grips). */
    public java.util.List<Integer> gripSeats() {
        return this.gripSeats;
    }

    public VehicleData withGripSeats(java.util.List<Integer> seats) {
        this.gripSeats = java.util.List.copyOf(seats);
        return this;
    }

    public VehicleData withStandingSeats(java.util.List<Integer> seats) {
        this.standingSeats = java.util.List.copyOf(seats);
        return this;
    }

    public VehicleData withNoFirstPersonSeats(java.util.List<Integer> seats) {
        this.noFirstPersonSeats = java.util.List.copyOf(seats);
        return this;
    }

    public VehicleData withTurretRiders(java.util.List<Integer> seats) {
        this.turretRiders = java.util.List.copyOf(seats);
        return this;
    }

    public VehicleData withAmphibious(ttv.migami.jeg.vehicle.data.subdata.AmphibiousInfo amphibious) {
        this.amphibious = amphibious;
        return this;
    }

    /** A boat's hull behaviour ("boat"): planing, beaching, the bow probe; BoatInfo.DEFAULT when absent. */
    public ttv.migami.jeg.vehicle.data.subdata.BoatInfo boat() {
        return this.boat;
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
