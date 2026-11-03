package ttv.migami.jeg.gun;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Warzone-style "squad wipe": a kill that leaves none of that mob type alive near the shooter, after the shooter has
 * dropped several of them in quick succession.
 */
public final class SquadWipeTracker {
    private static final double VICINITY_RADIUS = 12.0D;
    private static final int MIN_KILLS = 3;
    private static final long KILL_WINDOW_TICKS = 30L * 20L;
    private static final Map<UUID, Map<EntityType<?>, Deque<Long>>> RECENT_KILLS = new HashMap<>();

    private SquadWipeTracker() {}

    /** Records a kill and reports whether it wiped out the last of that mob type around the shooter. */
    public static boolean recordKill(ServerPlayer shooter, LivingEntity victim) {
        long now = shooter.level().getGameTime();
        EntityType<?> type = victim.getType();
        Deque<Long> kills = RECENT_KILLS.computeIfAbsent(shooter.getUUID(), id -> new HashMap<>())
                .computeIfAbsent(type, key -> new ArrayDeque<>());
        kills.addLast(now);
        while (now - kills.peekFirst() > KILL_WINDOW_TICKS) {
            kills.removeFirst();
        }
        if (kills.size() < MIN_KILLS) {
            return false;
        }
        boolean survivorsNearby = !shooter.level().getEntitiesOfClass(LivingEntity.class,
                shooter.getBoundingBox().inflate(VICINITY_RADIUS),
                other -> other != victim && other.getType() == type && other.isAlive() && !other.isDeadOrDying()).isEmpty();
        if (survivorsNearby) {
            return false;
        }
        kills.clear();
        return true;
    }

    public static void forget(ServerPlayer player) {
        RECENT_KILLS.remove(player.getUUID());
    }
}
