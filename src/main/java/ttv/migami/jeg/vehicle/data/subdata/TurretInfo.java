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
        float elevationRate
) {
    public static final TurretInfo NONE = new TurretInfo(-1, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, false, 0.0F, 0.0F);

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
