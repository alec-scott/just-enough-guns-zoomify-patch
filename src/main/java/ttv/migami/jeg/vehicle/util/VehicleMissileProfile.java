package ttv.migami.jeg.vehicle.util;

import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.entity.monster.phantom.AbstractTerrorPhantom;
import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

public record VehicleMissileProfile(
        GuidanceMode guidanceMode,
        TargetMode targetMode,
        int lifeTicks,
        double maxSpeed,
        double turnRate,
        float explosionPower,
        double blastRadius,
        float vehicleDamage,
        float livingDamage
) {
    private static final VehicleMissileProfile DEFAULT = new VehicleMissileProfile(
            GuidanceMode.LOCK_ON,
            TargetMode.ANY_LIVING,
            120,
            0.95D,
            0.16D,
            2.4F,
            3.0D,
            18.0F,
            12.0F
    );

    private static final Map<Identifier, VehicleMissileProfile> PROFILES = Map.of(
            Reference.id("vehicle_bmp2_missile"), new VehicleMissileProfile(
                    GuidanceMode.WIRE_GUIDED,
                    TargetMode.SURFACE_VEHICLE,
                    120,
                    0.95D,
                    0.16D,
                    4.0F,
                    6.0D,
                    180.0F,
                    12.0F
            ),
            Reference.id("vehicle_9m120_driver_missile"), new VehicleMissileProfile(
                    GuidanceMode.WIRE_GUIDED,
                    TargetMode.GROUND_ENTITY,
                    140,
                    3.2D,
                    0.14D,
                    7.5F,
                    10.0D,
                    240.0F,
                    90.0F
            ),
            Reference.id("vehicle_9m120_passenger_missile"), new VehicleMissileProfile(
                    GuidanceMode.WIRE_GUIDED,
                    TargetMode.GROUND_ENTITY,
                    140,
                    3.2D,
                    0.14D,
                    7.5F,
                    10.0D,
                    240.0F,
                    90.0F
            ),
            Reference.id("vehicle_kh39_missile"), new VehicleMissileProfile(
                    GuidanceMode.LOCK_ON,
                    TargetMode.GROUND_ENTITY,
                    180,
                    3.0D,
                    0.12D,
                    14.0F,
                    24.0D,
                    600.0F,
                    110.0F
            ),
            // The A-10's Mavericks: fire-and-forget, locked on with the seek key, launched from a fast jet.
            Reference.id("vehicle_agm65_missile"), new VehicleMissileProfile(
                    GuidanceMode.LOCK_ON,
                    TargetMode.GROUND_ENTITY,
                    200,
                    3.8D,
                    0.13D,
                    7.5F,
                    10.0D,
                    260.0F,
                    90.0F
            ),
            Reference.id("vehicle_agm65g_missile"), new VehicleMissileProfile(
                    GuidanceMode.LOCK_ON,
                    TargetMode.GROUND_ENTITY,
                    220,
                    3.4D,
                    0.11D,
                    14.0F,
                    24.0D,
                    600.0F,
                    110.0F
            ),
            Reference.id("vehicle_9m336_missile"), new VehicleMissileProfile(
                    GuidanceMode.LOCK_ON,
                    TargetMode.AIR_ENTITY,
                    160,
                    3.8D,
                    0.18D,
                    4.0F,
                    6.0D,
                    70.0F,
                    20.0F
            )
    );

    public static VehicleMissileProfile get(Identifier weaponId) {
        return PROFILES.getOrDefault(weaponId, DEFAULT);
    }

    public boolean usesLockOn() {
        return this.guidanceMode == GuidanceMode.LOCK_ON;
    }

    public boolean usesWireGuidance() {
        return this.guidanceMode == GuidanceMode.WIRE_GUIDED;
    }

    public boolean canLock(Entity candidate, LivingEntity shooter, VehicleEntity shooterVehicle) {
        if (!candidate.isAlive() || candidate == shooter || candidate == shooterVehicle || candidate.getVehicle() == shooterVehicle) {
            return false;
        }
        return this.targetMode.canLock(candidate);
    }

    public boolean canContinueTracking(Entity target, Entity owner, Entity ownerVehicle) {
        if (!target.isAlive() || target == owner || target == ownerVehicle || target.getVehicle() == ownerVehicle) {
            return false;
        }
        return this.targetMode.canLock(target);
    }

    public enum GuidanceMode {
        DIRECT,
        WIRE_GUIDED,
        LOCK_ON
    }

    public enum TargetMode {
        ANY_LIVING {
            @Override
            boolean canLock(Entity target) {
                return target instanceof LivingEntity;
            }
        },
        SURFACE_VEHICLE {
            @Override
            boolean canLock(Entity target) {
                return target instanceof VehicleEntity vehicle && isSurfaceVehicle(vehicle);
            }
        },
        GROUND_ENTITY {
            @Override
            boolean canLock(Entity target) {
                if (target instanceof VehicleEntity vehicle) {
                    return isGroundVehicle(vehicle);
                }
                return target instanceof LivingEntity && isGroundedEntity(target);
            }
        },
        AIR_ENTITY {
            @Override
            boolean canLock(Entity target) {
                if (target instanceof VehicleEntity vehicle) {
                    return isAirVehicle(vehicle);
                }
                if (isExplicitAntiAirTarget(target)) {
                    return true;
                }
                return target instanceof LivingEntity && !isGroundedEntity(target);
            }
        };

        abstract boolean canLock(Entity target);
    }

    private static boolean isGroundedEntity(Entity target) {
        return target.onGround() || target.isInWater();
    }

    private static boolean isExplicitAntiAirTarget(Entity target) {
        return target instanceof AbstractTerrorPhantom
                || target instanceof EnderDragon
                || target instanceof WitherBoss;
    }

    private static boolean isSurfaceVehicle(VehicleEntity vehicle) {
        VehicleType type = vehicle.vehicleData().defaults().vehicleType();
        return type == VehicleType.LAND || type == VehicleType.BOAT || type == VehicleType.ARTILLERY;
    }

    private static boolean isGroundVehicle(VehicleEntity vehicle) {
        VehicleType type = vehicle.vehicleData().defaults().vehicleType();
        return type == VehicleType.LAND || type == VehicleType.BOAT || type == VehicleType.ARTILLERY;
    }

    private static boolean isAirVehicle(VehicleEntity vehicle) {
        VehicleType type = vehicle.vehicleData().defaults().vehicleType();
        return type == VehicleType.HELICOPTER || type == VehicleType.AIRCRAFT;
    }
}
