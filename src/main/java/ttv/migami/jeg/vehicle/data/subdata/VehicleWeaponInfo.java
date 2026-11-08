package ttv.migami.jeg.vehicle.data.subdata;

import net.minecraft.resources.Identifier;

/**
 * @param turretMounted whether the muzzle turns with the vehicle's turret; null keeps the default (unguided weapons and,
 *                      with guided_uses_turret, guided ones). Pylon weapons on a turreted helicopter set it false.
 * @param mirrored      a matched pair of guns, one each side: shots alternate between the muzzle and its mirror image
 *                      across the vehicle's centre line ({@code "mirror": true} in the muzzle).
 */
public record VehicleWeaponInfo(Identifier weaponId, Identifier ammoId, int energyCost, boolean guided, int seatIndex, double muzzleX, double muzzleY, double muzzleZ,
                                Boolean turretMounted, boolean mirrored) {
    public VehicleWeaponInfo(Identifier weaponId, Identifier ammoId, int energyCost, boolean guided, int seatIndex, double muzzleX, double muzzleY, double muzzleZ,
                             Boolean turretMounted) {
        this(weaponId, ammoId, energyCost, guided, seatIndex, muzzleX, muzzleY, muzzleZ, turretMounted, false);
    }

    public VehicleWeaponInfo(Identifier weaponId, Identifier ammoId, int energyCost, boolean guided, int seatIndex, double muzzleX, double muzzleY, double muzzleZ) {
        this(weaponId, ammoId, energyCost, guided, seatIndex, muzzleX, muzzleY, muzzleZ, null);
    }

    public VehicleWeaponInfo(Identifier weaponId, Identifier ammoId, int energyCost, boolean guided) {
        this(weaponId, ammoId, energyCost, guided, -1);
    }

    public VehicleWeaponInfo(Identifier weaponId, Identifier ammoId, int energyCost, boolean guided, int seatIndex) {
        this(weaponId, ammoId, energyCost, guided, seatIndex, Double.NaN, Double.NaN, Double.NaN);
    }

    public boolean usableBySeat(int seatIndex) {
        return this.seatIndex < 0 || this.seatIndex == seatIndex;
    }

    public boolean hasMuzzle() {
        return !Double.isNaN(this.muzzleX) && !Double.isNaN(this.muzzleY) && !Double.isNaN(this.muzzleZ);
    }
}
