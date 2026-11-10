package ttv.migami.jeg.entity;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

/**
 * What a missile's or rocket's blast does to blocks. Vanilla's explosion barely scratches dense blocks, so a warhead used
 * to bounce off stone, deepslate, purpur and end stone brick (an End city shrugged off a missile). Here ordinary building
 * blocks denser than {@link #DENSE_RESISTANCE} crumble as if they were that soft, and a warhead's {@code scale} softens
 * them further: a heavy one tears a building open. Soft ground (dirt, sand, grass) is unchanged by rockets.
 * <p>
 * Blocks with a blast resistance of {@link #ARMOUR_RESISTANCE} or more keep it in full, so there are materials worth
 * building a bunker from: trial spawners and vaults (50), ender chests (600), obsidian, netherite and the like (1200),
 * and whatever tough blocks other mods add.
 */
public final class WarheadBlockDamage {
    /** Blast resistance the densest ordinary block counts as (wool and glass are about this; stone is 6, end stone 9). */
    public static final float DENSE_RESISTANCE = 1.0F;
    /** Resistance from which a block is armour and resists in full. Stone and metal blocks are 6, end stone is 9. */
    public static final float ARMOUR_RESISTANCE = 10.0F;

    private WarheadBlockDamage() {}

    /**
     * The blast's view of blocks for a warhead. {@code scale} multiplies the (capped) resistance: 1 for rockets, less for
     * bigger warheads.
     */
    public static ExplosionDamageCalculator calculator(Entity source, float scale) {
        return new EntityBasedExplosionDamageCalculator(source) {
            @Override
            public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, FluidState fluid) {
                Optional<Float> resistance = super.getBlockExplosionResistance(explosion, level, pos, state, fluid);
                if (!fluid.isEmpty()) {
                    return resistance;
                }
                return resistance.map(value -> value >= ARMOUR_RESISTANCE ? value : Math.min(value, DENSE_RESISTANCE) * scale);
            }
        };
    }
}
