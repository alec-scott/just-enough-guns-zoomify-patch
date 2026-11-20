package ttv.migami.jeg.vehicle.data.subdata;

public record TurretInfo(
        int seatIndex,
        double renderPivotY,
        double originX,
        double originY,
        double originZ,
        double barrelX,
        double barrelY,
        double barrelZ,
        boolean guidedUsesTurret,
        float traverseRate,
        float elevationRate,
        int fallbackSeat
) {
    public static final TurretInfo NONE = new TurretInfo(-1, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, false, 0.0F, 0.0F, -1);

    /**
     * The seat that works the turret while its own seat ("seat") is empty ("fallback_seat", -1 for none): the driver keeps
     * the gun when alone, and a passenger in the gunner's seat takes it over (fleet plan decision 2).
     */
    public boolean hasFallbackSeat() {
        return this.fallbackSeat >= 0 && this.fallbackSeat != this.seatIndex;
    }

    /**
     * True when the turret turns at a limited rate (degrees per tick, "traverse_rate"/"elevation_rate" in the data):
     * the gunner's view moves freely and the server turns the turret toward it, as a real powered mount does.
     */
    public boolean slewLimited() {
        return this.traverseRate > 0.0F || this.elevationRate > 0.0F;
    }

    public boolean enabled() {
        return this.seatIndex >= 0;
    }
}
