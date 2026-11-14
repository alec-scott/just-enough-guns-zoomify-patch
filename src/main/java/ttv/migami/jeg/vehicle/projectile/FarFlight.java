package ttv.migami.jeg.vehicle.projectile;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.network.MissileTrackPayload;

/**
 * Keeps a missile, rocket or aircraft gun round flying and in sight over a long shot, well past where entities normally
 * tick and are sent to players.
 * <p>
 * Entities only move in chunks within a player's simulation distance (160 blocks on yftm), and only once those chunks
 * and the ones around them have finished loading, so a long shot used to stop dead in mid-air where the loaded world
 * ends: the chunks ahead take longer to load than the projectile takes to reach them, and one that is not moving cannot
 * ask for them again. It hung there until a player came near.
 * <p>
 * The projectile itself has to return true from {@code isAlwaysTicking()} so it ticks whatever state its chunk is in.
 * The tickets here keep it in simulation range, which takes effect at once without waiting on any loading, and start the
 * terrain along its path loading before it is needed for hit checks.
 */
public final class FarFlight {
    /** Out to here players are sent a projectile's position each tick, and its blast. */
    public static final double VISIBLE_RANGE = 1024.0D;
    /** Within this (horizontally) every player is sent the entity itself, whatever their render distance. */
    private static final double TRACK_MIN_RANGE = 32.0D;
    /** Ender pearl tickets last 40 ticks; refreshed well before that, and at once on entering a new chunk. */
    private static final int TICKET_REFRESH_TICKS = 20;
    /**
     * Radius 3 puts the projectile's chunk and the eight around it in entity-ticking range, so whichever one it flies
     * into next is already in range when it gets there.
     */
    private static final int TICKET_RADIUS = 3;
    /**
     * Chunks this far along the flight path are loaded ahead, 5 chunks across each: at least this many blocks, and at
     * least this many ticks of flight for something faster.
     */
    private static final double[] LOAD_AHEAD_BLOCKS = {80.0D, 160.0D};
    private static final double[] LOAD_AHEAD_TICKS = {25.0D, 50.0D};
    private static final int LOAD_AHEAD_RADIUS = 2;

    /**
     * Tickets asked for lately, so a gun's stream of rounds down the same line asks for each chunk once rather than once
     * a round: placed (game time) by level, chunk and radius. A ticket lasts 40 ticks; one asked for within the last
     * {@link #SHARED_TICKET_TICKS} is left to stand.
     */
    private static final Map<TicketKey, Long> RECENT_TICKETS = new HashMap<>();
    private static final int SHARED_TICKET_TICKS = 10;

    private record TicketKey(ResourceKey<Level> level, long chunk, int radius) {}

    private long ticketChunk = ChunkPos.INVALID_CHUNK_POS;
    private int ticketRefresh;

    /** Call every server tick after the projectile has moved; {@code ticksLeft} is how much longer it can fly. */
    public void keepTicking(ServerLevel level, Entity entity, int ticksLeft) {
        ChunkPos here = entity.chunkPosition();
        long key = here.pack();
        if (key == this.ticketChunk && --this.ticketRefresh > 0) {
            return;
        }
        this.ticketChunk = key;
        this.ticketRefresh = TICKET_REFRESH_TICKS;
        ticket(level, here, TICKET_RADIUS);
        Vec3 motion = entity.getDeltaMovement();
        double speed = motion.length();
        if (speed < 1.0E-3D) {
            return;
        }
        double left = ticksLeft * speed;
        for (int i = 0; i < LOAD_AHEAD_BLOCKS.length; i++) {
            double ahead = Math.max(LOAD_AHEAD_BLOCKS[i], LOAD_AHEAD_TICKS[i] * speed);
            if (ahead > left + 48.0D) {
                break;
            }
            Vec3 point = entity.position().add(motion.scale(ahead / speed));
            ticket(level, ChunkPos.containing(BlockPos.containing(point)), LOAD_AHEAD_RADIUS);
        }
    }

    private static void ticket(ServerLevel level, ChunkPos chunk, int radius) {
        long now = level.getGameTime();
        TicketKey key = new TicketKey(level.dimension(), chunk.pack(), radius);
        Long placed = RECENT_TICKETS.get(key);
        if (placed != null && now - placed >= 0 && now - placed < SHARED_TICKET_TICKS) {
            return;
        }
        if (RECENT_TICKETS.size() > 4096) {
            RECENT_TICKETS.values().removeIf(time -> now - time >= SHARED_TICKET_TICKS || now < time);
        }
        RECENT_TICKETS.put(key, now);
        level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, chunk, radius);
    }

    /**
     * The entity itself only reaches players within its tracking range and the server's view distance (192 blocks on
     * yftm). Players further off, out to where the blast is still shown, get its position each tick instead and draw
     * its motor and trail from that.
     */
    public static void sendTrack(ServerLevel level, Entity entity, boolean heavy, int age) {
        MissileTrackPayload track = null;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - entity.getX();
            double dz = player.getZ() - entity.getZ();
            double horizontal = dx * dx + dz * dz;
            if (horizontal < TRACK_MIN_RANGE * TRACK_MIN_RANGE
                    || player.position().distanceToSqr(entity.position()) > VISIBLE_RANGE * VISIBLE_RANGE
                    || !ServerPlayNetworking.canSend(player, MissileTrackPayload.TYPE)) {
                continue;
            }
            if (track == null) {
                track = new MissileTrackPayload(entity.getId(), entity.position(), entity.getDeltaMovement(), heavy, age);
            }
            ServerPlayNetworking.send(player, track);
        }
    }
}
