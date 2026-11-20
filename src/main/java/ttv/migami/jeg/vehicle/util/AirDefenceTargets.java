package ttv.migami.jeg.vehicle.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.vehicle.ai.EnemyVehicleController;
import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

/**
 * What a base's air defence (the HPJ-11, design/hpj11/BRIEF.md) may shoot at, and what its rounds' proximity fuze bursts
 * on: missiles and rockets the enemy fired, the enemy's aircraft, and phantoms. Never a player, anything a player
 * fired, a player's vehicle, or a tame or passive creature: the enemy is marked by the raid spawner's tags
 * ({@link EnemyVehicleController}) or is a monster.
 */
public final class AirDefenceTargets {
    private AirDefenceTargets() {}

    /** Engagement order: incoming missiles first, then aircraft, then phantoms. Lower is sooner; -1 is not a target. */
    public static int priority(Entity entity) {
        if (!entity.isAlive() || entity.isRemoved()) {
            return -1;
        }
        if (entity instanceof VehicleMissileEntity missile) {
            return missile.enemyLaunched() || isEnemyLaunched(missile.owner()) ? 0 : -1;
        }
        if (entity instanceof VehicleEntity vehicle) {
            VehicleType type = vehicle.vehicleData().defaults().vehicleType();
            boolean aircraft = type == VehicleType.HELICOPTER || type == VehicleType.AIRCRAFT;
            return aircraft && !vehicle.isWrecked() && isEnemyVehicle(vehicle) ? 1 : -1;
        }
        if (entity instanceof Phantom) {
            return 2;
        }
        return -1;
    }

    public static boolean isThreat(Entity entity) {
        return priority(entity) >= 0;
    }

    /** A vehicle the raids brought (tagged by the spawner), or one a monster is driving. Never one with a player aboard. */
    public static boolean isEnemyVehicle(VehicleEntity vehicle) {
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player) {
                return false;
            }
        }
        if (vehicle.entityTags().contains(EnemyVehicleController.ENEMY_VEHICLE_TAG)) {
            return true;
        }
        return vehicle.getControllingPassenger() instanceof Enemy;
    }

    /** Whether a missile's launcher was the enemy: its crew, a monster, or an enemy vehicle. Players' never are. */
    public static boolean isEnemyLaunched(@Nullable Entity owner) {
        if (owner == null || owner instanceof Player) {
            return false;
        }
        if (owner.entityTags().contains(EnemyVehicleController.ENEMY_VEHICLE_CREW_TAG) || owner instanceof Enemy) {
            return true;
        }
        Entity vehicle = owner instanceof VehicleEntity ? owner : owner.getVehicle();
        return vehicle instanceof VehicleEntity v && isEnemyVehicle(v);
    }
}
