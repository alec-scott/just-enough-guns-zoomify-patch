package ttv.migami.jeg.vehicle.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

/**
 * The TOW technical (design/pickup/BRIEF.md): a 4x4 pickup with a TOW launcher on its tripod in the bed. The gunner
 * stands at it and steers each missile down the sight line by wire, as on the ground launcher (TowEntity).
 */
public final class PickupTowEntity extends VehicleEntity implements VehicleMissileEntity.LineOfSightLauncher {
    public static final String ID = "pickup_tow";
    /** The sight's axis at the trunnion, the launcher at rest on its pedestal, hull coordinates (design/pickup/rig.json). */
    public static final Vec3 SIGHT_REST = new Vec3(0.0D, 1.9985D, -1.14D);
    /** The gunner's seat. */
    public static final int GUNNER_SEAT = 2;

    public PickupTowEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.setVehicleData(Reference.id(ID));
    }

    @Override
    public boolean preciseHitbox() {
        return true;
    }

    @Override
    protected void onMissileLaunched(VehicleMissileEntity missile, LivingEntity shooter, VehicleWeaponInfo weapon) {
        if (TowEntity.MISSILE_WEAPON.equals(weapon.weaponId())) {
            missile.setLineOfSightGuided(this);
        }
    }

    @Override
    public Vec3 sightOrigin(float partialTick) {
        return this.barrelPoint(SIGHT_REST, partialTick);
    }

    @Override
    public Vec3 sightDirection(float partialTick) {
        return this.barrelDirection(partialTick);
    }

    /** The wire holds while the one who fired stands at the launcher. */
    @Override
    public boolean gunnerAtSight(Entity owner) {
        return owner.getVehicle() == this && this.passengerForSeat(GUNNER_SEAT) == owner && !this.isWrecked();
    }

    @Override
    public double wireLength() {
        return TowEntity.WIRE_LENGTH;
    }
}
