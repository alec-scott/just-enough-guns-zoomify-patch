package ttv.migami.jeg.vehicle.data.subdata;

import net.minecraft.resources.Identifier;

/**
 * @param turretMounted whether the muzzle turns with the vehicle's turret; null keeps the default (unguided weapons and,
 *                      with guided_uses_turret, guided ones). Pylon weapons on a turreted helicopter set it false.
 */
public record VehicleWeaponInfo(Identifier weaponId, Identifier ammoId, int energyCost, boolean guided, int seatIndex, double muzzleX, double muzzleY, double muzzleZ,
                                Boolean turretMounted) {
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
