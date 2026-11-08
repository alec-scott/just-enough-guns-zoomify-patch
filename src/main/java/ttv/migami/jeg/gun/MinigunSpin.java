package ttv.migami.jeg.gun;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import ttv.migami.jeg.item.GunItem;
import ttv.migami.jeg.network.MinigunStatePayload;
import ttv.migami.jeg.network.NetworkHandler;

/**
 * The minigun's barrels have to be up to speed before it can fire. Aiming or holding the trigger spins them up; the trigger
 * then fires once they get there. The client runs the same spin for its own feel and sound; here the server enforces it and
 * tells everyone else what the gun is doing so they can hear and see it.
 */
public final class MinigunSpin {
    /** Barrels from a standstill to firing speed, in ticks. Matches the charge-up in the M134 sound. */
    public static final int SPOOL_TICKS = 15;
    /** Barrels from firing speed to a standstill, in ticks. Matches the spin-down in the M134 sound. */
    public static final int SPIN_DOWN_TICKS = 37;

    public static final byte STATE_IDLE = 0;
    public static final byte STATE_SPINNING = 1;
    public static final byte STATE_FIRING = 2;

    private static final int FIRING_HOLD_TICKS = 3;
    // The trigger's first shot and the hold it follows can land on different ticks over a real network.
    private static final float SPUN_UP_TOLERANCE = 2.0F / SPOOL_TICKS;
    private static final double BROADCAST_RANGE = 128.0D;
    private static final Map<UUID, State> STATES = new HashMap<>();

    private MinigunSpin() {}

    private static final class State {
        boolean trigger;
        float spin;
        int firingTicks;
        byte sent = STATE_IDLE;
    }

    /**
     * Loudness (gain) of a minigun firing. Its fire sound runs on without a break, so it sits at about a rifle shot's
     * loudest instead of above it; the yftm pack's Sound Physics reverb makes a constant sound seem louder still.
     */
    public static final float FIRE_VOLUME = 0.45F;
    /** How a single gunshot is played to everyone: full gain, heard out to 7.5 x the sound's 16 block range. */
    private static final float GUNSHOT_VOLUME = 7.5F;

    public static boolean isMinigun(ItemStack stack) {
        return stack.getItem() instanceof GunItem gun && isMinigun(gun.getStats());
    }

    public static boolean isMinigun(GunStats stats) {
        return "minigun".equals(stats.id().getPath());
    }

    /**
     * Volume for a mob's per-shot gunshot. A minigun's shots come so fast they overlap into one roar, so they play at
     * {@link #FIRE_VOLUME}; how far they carry then comes from the sound's own attenuation distance.
     */
    public static float gunshotVolume(GunStats stats) {
        return isMinigun(stats) ? FIRE_VOLUME : GUNSHOT_VOLUME;
    }

    public static void setTrigger(ServerPlayer player, boolean holding) {
        STATES.computeIfAbsent(player.getUUID(), id -> new State()).trigger = holding;
    }

    public static boolean isSpunUp(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.spin >= 1.0F - SPUN_UP_TOLERANCE;
    }

    public static boolean isSpinning(Player player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.spin > 0.0F;
    }

    public static void onShot(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        if (state != null) {
            state.firingTicks = FIRING_HOLD_TICKS;
        }
    }

    public static void tick(ServerPlayer player) {
        State state = STATES.get(player.getUUID());
        ItemStack held = player.getMainHandItem();
        boolean holding = isMinigun(held) && !GunItem.isDrawOperationLocked(held);
        if (state == null) {
            if (!holding || !NetworkHandler.isAiming(player)) {
                return;
            }
            state = new State();
            STATES.put(player.getUUID(), state);
        }

        boolean wanted = holding && (state.trigger || NetworkHandler.isAiming(player));
        if (wanted) {
            state.spin = Math.min(1.0F, state.spin + 1.0F / SPOOL_TICKS);
        } else {
            state.spin = Math.max(0.0F, state.spin - 1.0F / SPIN_DOWN_TICKS);
        }
        if (state.firingTicks > 0) {
            state.firingTicks--;
        }

        byte current = !wanted ? STATE_IDLE : state.firingTicks > 0 ? STATE_FIRING : STATE_SPINNING;
        if (current != state.sent) {
            state.sent = current;
            broadcast(player, current);
        }
        if (!holding && state.spin <= 0.0F && current == STATE_IDLE) {
            STATES.remove(player.getUUID());
        }
    }

    private static void broadcast(ServerPlayer player, byte state) {
        MinigunStatePayload payload = new MinigunStatePayload(player.getId(), state);
        for (ServerPlayer other : player.level().players()) {
            // The shooter runs this themselves, ahead of the server.
            if (other != player && other.distanceToSqr(player) <= BROADCAST_RANGE * BROADCAST_RANGE
                    && ServerPlayNetworking.canSend(other, MinigunStatePayload.TYPE)) {
                ServerPlayNetworking.send(other, payload);
            }
        }
    }
}
