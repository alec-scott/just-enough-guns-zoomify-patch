package ttv.migami.jeg.vehicle.projectile;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.util.MortarRig;

/**
 * An 81 mm high-explosive mortar round in flight: a {@link ShellEntity} that falls exactly as {@link MortarRig}
 * predicts, with Superb Warfare's numbers: 100 damage easing off to nothing at 8 blocks, plus 60 to whatever it struck.
 * Blocks break as a jegn rocket breaks them.
 */
public final class MortarRoundEntity extends ShellEntity {
    /** Superb Warfare's mortar: explosion radius and damage, and the direct-hit damage. */
    public static final float BLAST_RADIUS = 8.0F;
    public static final float BLAST_DAMAGE = 100.0F;
    public static final float DIRECT_HIT_DAMAGE = 60.0F;
    /** The block-breaking explosion's power, as SBW's (its radius is its power). */
    public static final float EXPLOSION_POWER = 8.0F;
    public static final Warhead WARHEAD = new Warhead(BLAST_RADIUS, BLAST_DAMAGE, DIRECT_HIT_DAMAGE, EXPLOSION_POWER, 0.0D, 0);

    public MortarRoundEntity(EntityType<? extends MortarRoundEntity> type, Level level) {
        super(type, level);
        this.setFlight(MortarRig.BALLISTICS, WARHEAD);
    }

    public MortarRoundEntity(Level level, @Nullable Entity owner, Entity mortar, Vec3 position, Vec3 velocity) {
        super(ModEntities.MORTAR_ROUND.get(), level, owner, mortar, position, velocity, MortarRig.BALLISTICS, WARHEAD);
    }

    /** Full at the centre, three quarters at half the radius, nothing at the rim; to the target's nearest part. */
    public static float falloff(Entity target, Vec3 centre) {
        return ShellEntity.falloff(target, centre, BLAST_RADIUS);
    }
}
