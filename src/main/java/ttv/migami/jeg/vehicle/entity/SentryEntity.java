package ttv.migami.jeg.vehicle.entity;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModSounds;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The perimeter sentry (design/sentry/BRIEF.md): a remote weapon station on a tripod with a 7.62 mm machine gun. On
 * automatic ({@link AutoDefenceEntity}) it holds a wall on horde nights: it engages monsters within 48 blocks in sight,
 * those going for a player or a villager first, then the nearest. It never fires while anything friendly (a player, a
 * villager, a pet, a passive animal, a vehicle) stands in its line of fire.
 */
public final class SentryEntity extends AutoDefenceEntity {
    public static final String ID = "sentry";
    public static final Identifier MG_WEAPON = Reference.id("vehicle_sentry_machine_gun");
    public static final double SEARCH_RANGE = 48.0D;
    /** It looks down a wall at the ground before it. */
    public static final float MIN_ELEVATION = -30.0F;
    private static final Vec3 MUZZLE_REST = new Vec3(0.0D, 1.8125D, 1.375D);
    /** Ticks between the empty gun's clicks while it tracks a target it cannot shoot. */

    public SentryEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.setVehicleData(Reference.id(ID));
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
        return 3.0F;
    }

    @Override
    protected Vec3 muzzleRest() {
        return MUZZLE_REST;
    }

    @Override
    protected double roundSpeed() {
        return 24.0D;
    }

    @Override
    protected double sensorHeight() {
        return 2.07D;
    }

    /** Client: the status lamp, from what every client knows: 0 off, 1 green (watching), 2 amber (tracking), 3 red. */
    public int statusLamp() {
        if (!this.automatic() && this.getControllingPassenger() == null) {
            return 0;
        }
        // Steady, not flicking between green and red with every burst (the user's review, 2026-11-18): red holds a
        // second after the last round, and an empty belt only shows when manned (automatic needs no rounds).
        if (this.isWeaponFiring()) {
            this.lampRedUntil = this.tickCount + 20;
        }
        if (this.tickCount < this.lampRedUntil || !this.automatic() && this.vehicleRifleAmmo() <= 0) {
            return 3;
        }
        return this.tracked() != null ? 2 : 1;
    }

    /** Client: the lamp stays red until this tick. */
    private int lampRedUntil;
}
