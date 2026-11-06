package ttv.migami.jeg.gun;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Config;

/**
 * A helicopter's gun tears up what is flimsy: enough rounds strip leaves and glass, and cut through grass, flowers, crops and
 * the like. Anything sturdier stops the check, so rounds do not chew through walls to reach what is behind them.
 */
public final class VehicleGunBlockDamage {
    private static final double STEP = 0.5D;
    private static final int MAX_BLOCKS_PER_TICK = 3;
    private static final int BASE_HITS = 2;
    private static final float HITS_PER_HARDNESS = 5.0F;
    private static final float MAX_LEAF_OR_GLASS_HARDNESS = 0.35F;
    private static final float MAX_PLANT_HARDNESS = 0.1F;
    private static final long FORGET_AFTER_TICKS = 200L;
    private static final int PRUNE_ABOVE = 512;
    private static final Map<Key, Damage> DAMAGE = new HashMap<>();

    private VehicleGunBlockDamage() {}

    private record Key(ResourceKey<Level> dimension, long position) {}

    private static final class Damage {
        int hits;
        long lastTick;
    }

    /** Runs a round's movement for this tick. */
    public static void alongPath(ServerLevel level, Vec3 from, Vec3 to, Entity owner, Entity bullet) {
        if (!Config.helicopterGunBreaksFoliage()) {
            return;
        }
        double length = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(length / STEP));
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        long last = Long.MIN_VALUE;
        int damaged = 0;
        for (int step = 1; step <= steps && damaged < MAX_BLOCKS_PER_TICK; step++) {
            Vec3 point = from.lerp(to, step / (double) steps);
            position.set(point.x, point.y, point.z);
            if (position.asLong() == last) {
                continue;
            }
            last = position.asLong();
            if (!level.isLoaded(position)) {
                return;
            }
            BlockState state = level.getBlockState(position);
            if (state.isAir()) {
                continue;
            }
            if (isFlimsy(level, position, state)) {
                damaged++;
                hit(level, position.immutable(), state, owner, bullet);
            } else if (!state.getCollisionShape(level, position).isEmpty()) {
                return;
            }
        }
    }

    private static boolean isFlimsy(ServerLevel level, BlockPos position, BlockState state) {
        float hardness = state.getDestroySpeed(level, position);
        if (hardness < 0.0F || state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.is(BlockTags.FIRE)) {
            return false;
        }
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.IMPERMEABLE)) {
            return hardness <= MAX_LEAF_OR_GLASS_HARDNESS;
        }
        return hardness <= MAX_PLANT_HARDNESS && state.getCollisionShape(level, position).isEmpty();
    }

    private static void hit(ServerLevel level, BlockPos position, BlockState state, Entity owner, Entity bullet) {
        long now = level.getGameTime();
        if (DAMAGE.size() > PRUNE_ABOVE) {
            Iterator<Damage> iterator = DAMAGE.values().iterator();
            while (iterator.hasNext()) {
                if (now - iterator.next().lastTick > FORGET_AFTER_TICKS) {
                    iterator.remove();
                }
            }
        }
        Key key = new Key(level.dimension(), position.asLong());
        Damage damage = DAMAGE.computeIfAbsent(key, ignored -> new Damage());
        if (now - damage.lastTick > FORGET_AFTER_TICKS) {
            damage.hits = 0;
        }
        damage.hits++;
        damage.lastTick = now;
        int needed = BASE_HITS + (int) Math.ceil(state.getDestroySpeed(level, position) * HITS_PER_HARDNESS);
        int crackId = position.hashCode();
        if (damage.hits < needed) {
            level.destroyBlockProgress(crackId, position, damage.hits * 9 / needed);
            return;
        }
        DAMAGE.remove(key);
        level.destroyBlockProgress(crackId, position, -1);
        // Land claims and spawn protection get their say, as if the pilot had broken it.
        if (owner instanceof ServerPlayer player
                && (!level.mayInteract(player, position)
                || !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, position, state, null))) {
            return;
        }
        level.destroyBlock(position, true, bullet);
    }
}
