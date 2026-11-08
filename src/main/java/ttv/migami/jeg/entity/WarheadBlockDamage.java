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
 * to bounce off stone, deepslate, purpur and end stone brick (an End city shrugged off a missile). Here anything denser
 * than {@link #DENSE_RESISTANCE} crumbles as if it were that soft, and a warhead's {@code scale} softens it further: a
 * heavy one tears a building open. Soft ground (dirt, sand, grass) is unchanged, so craters in the open stay their size.
 * Blocks made to stop explosions (obsidian, bedrock, ancient debris and anything as tough) keep their full resistance.
 */
public final class WarheadBlockDamage {
    /** Blast resistance the densest ordinary block counts as (wool and glass are about this; stone is 6, end stone 9). */
    public static final float DENSE_RESISTANCE = 1.0F;
    /** Resistance from which a block is blast proof (obsidian is 1200). */
    public static final float BLAST_PROOF_RESISTANCE = 1200.0F;

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
                return resistance.map(value -> value >= BLAST_PROOF_RESISTANCE ? value : Math.min(value, DENSE_RESISTANCE) * scale);
            }
        };
    }
}
