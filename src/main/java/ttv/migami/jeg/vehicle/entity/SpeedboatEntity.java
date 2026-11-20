package ttv.migami.jeg.vehicle.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.data.subdata.SeatInfo;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The speedboat, a SURC-class riverine assault boat (design/speedboat/BRIEF.md). The hull's planing and beaching come
 * from its data ("boat"); this class only seats the bow gunner on the gun: they stand on the foredeck behind the pintle
 * and swing round with it. The driver keeps the gun while the gunner's seat is empty (the turret's "fallback_seat").
 * <p>
 * Like the sentry, the bow gun has an automatic mode (N, or the switch on the hold's screen; the user's review,
 * 2026-11-19): with no one at the gun it engages hostile mobs within 48 blocks by itself, those going for a player or a
 * villager first, and never through anything friendly. A player at the gun always lays it by hand; the driver has no
 * hand on it while it is on automatic (only the N switch).
 */
public final class SpeedboatEntity extends AutoDefenceEntity {
    public static final String ID = "speedboat";
    public static final int DRIVER_SEAT = 0, GUNNER_SEAT = 1;
    public static final double SEARCH_RANGE = 48.0D;
    /** It rakes a bank a little below the gun, and the water round it. */
    public static final float MIN_ELEVATION = -20.0F;
    /** The gun's muzzle at rest (the weapon's muzzle in the data). */
    private static final Vec3 MUZZLE_REST = new Vec3(0.0D, 2.7812D, 4.9199D);

    public SpeedboatEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.setVehicleData(Reference.id(ID));
    }

    @Override
    protected boolean seatRidesTurret(SeatInfo seat) {
        return seat.index() == GUNNER_SEAT;
    }

    /** On automatic only while no player stands at the gun: a gunner lays it by hand. */
    @Override
    protected boolean engagesOnItsOwn() {
        return super.engagesOnItsOwn() && !(this.passengerForSeat(GUNNER_SEAT) instanceof Player);
    }

    @Override
    protected int targetPriority(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !(entity instanceof Enemy) || !living.isAlive() || living.isInvisible()) {
            return -1;
        }
        if (entity instanceof Mob mob && (mob.getTarget() instanceof Player || mob.getTarget() != null && !(mob.getTarget() instanceof Enemy))) {
            return 0;
        }
        return 1;
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
        return MIN_ELEVATION;
    }

    @Override
    protected float fireTolerance() {
        // A rocking hull: a little more latitude than the sentry on its tripod.
        return 4.0F;
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
        return 2.8D;
    }
}
