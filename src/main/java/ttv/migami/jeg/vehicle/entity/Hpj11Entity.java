package ttv.migami.jeg.vehicle.entity;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.AirDefenceTargets;

/**
 * The HPJ-11 close-in weapon system (design/hpj11/BRIEF.md): an eleven-barrel 30 mm Gatling on a powered mount, base
 * air and missile defence. On automatic ({@link AutoDefenceEntity}) it engages incoming enemy missiles, enemy aircraft
 * and phantoms (AirDefenceTargets) with proximity-fuzed rounds that can kill a missile in flight, never laying its gun
 * below {@link #MIN_AUTO_ELEVATION} for them; then hostile mobs within {@link #MOB_RANGE}, down to
 * {@link #MOB_MIN_ELEVATION}, holding fire while anything friendly is in the line.
 */
public final class Hpj11Entity extends AutoDefenceEntity {
    public static final String ID = "hpj11";
    public static final Identifier CIWS_WEAPON = Reference.id("vehicle_hpj11_ciws");
    public static final double SEARCH_RANGE = 256.0D;
    public static final float MIN_AUTO_ELEVATION = 5.0F;
    /** The proximity fuze's reach, blocks. */
    public static final double FUZE_RADIUS = 1.6D;
    private static final Vec3 MUZZLE_REST = new Vec3(0.0D, 1.625D, 2.875D);

    public Hpj11Entity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.setVehicleData(Reference.id(ID));
    }

    @Override
    protected int targetPriority(Entity entity) {
        int air = AirDefenceTargets.priority(entity);
        if (air >= 0) {
            return air;
        }
        // Hostile mobs too, after anything in the air (the user's review, 2026-11-18: on automatic it attacks hostile
        // mobs that come into its radius), within MOB_RANGE and never through anything friendly.
        if (entity instanceof net.minecraft.world.entity.monster.Enemy && entity instanceof net.minecraft.world.entity.LivingEntity living
                && living.isAlive() && !living.isInvisible() && entity.distanceToSqr(this) <= MOB_RANGE * MOB_RANGE) {
            return 3;
        }
        return -1;
    }

    /** Hostile mobs on the ground are engaged out to here, blocks. */
    public static final double MOB_RANGE = 64.0D;
    /** For a mob on the ground it may lay its gun this far below the horizon, degrees. */
    public static final float MOB_MIN_ELEVATION = -15.0F;

    @Override
    protected float minAutoElevation(Entity target) {
        return AirDefenceTargets.priority(target) < 0 ? MOB_MIN_ELEVATION : MIN_AUTO_ELEVATION;
    }

    @Override
    protected boolean lineOfFireBlocked(Vec3 from, Vec3 to) {
        return this.friendlyInLine(from, to);
    }

    @Override
    protected double searchRange() {
        return SEARCH_RANGE;
    }

    @Override
    protected float minAutoElevation() {
        return MIN_AUTO_ELEVATION;
    }

    @Override
    protected float fireTolerance() {
        return 2.5F;
    }

    @Override
    protected Vec3 muzzleRest() {
        return MUZZLE_REST;
    }

    @Override
    protected double roundSpeed() {
        return 30.0D;
    }

    @Override
    protected double sensorHeight() {
        return 2.4D;
    }

    // ---- The gun.

    @Override
    protected boolean isRotaryWeapon(VehicleWeaponInfo weapon) {
        return CIWS_WEAPON.equals(weapon.weaponId());
    }

    @Override
    protected void configureVehicleRound(BulletEntity bullet, VehicleWeaponInfo weapon) {
        if (CIWS_WEAPON.equals(weapon.weaponId())) {
            bullet.setProximityFuze(FUZE_RADIUS);
        }
    }

    @Override
    protected boolean firesLongRangeRounds(VehicleWeaponInfo weapon) {
        return CIWS_WEAPON.equals(weapon.weaponId()) || super.firesLongRangeRounds(weapon);
    }
}
