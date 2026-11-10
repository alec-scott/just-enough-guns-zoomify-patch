package ttv.migami.jeg.vehicle.projectile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shows the damage of a far-off blast sooner to players who see that terrain through Voxy.
 * <p>
 * Beyond the server's view distance players are drawn terrain sent by Voxy Server Side (mod id {@code lss}). It notes a
 * changed column when its chunk is saved, which happens within a tick or two of a blast, but only tells players about
 * changed columns on a timer ({@code dirty_broadcast_interval_ticks}, 10 seconds on yftm), so a crater showed up to 10
 * seconds after the explosion. Shortening that timer in the server config would make every change in the world go out
 * that often. Instead, for a short while after one of our blasts, changed columns are pushed on the next tick, at most
 * once a second (the shortest the config itself allows): an extra push or two per blast. Measured in the pack 400
 * blocks out, the crater then shows about 4 seconds after the blast, under the smoke, instead of 4 to 14: the chunk is
 * saved 1.5 seconds after the blast and Voxy takes 2 more to redraw.
 * <p>
 * This reaches into Voxy Server Side's internals by reflection, since it has no API for it. If that mod is missing or
 * has changed shape this does nothing and the crater arrives on the usual timer.
 */
public final class DistantTerrainRefresh {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg");
    /**
     * How long after a blast changed columns go out at once. A chunk saved in the last 10 seconds is not saved again
     * until those are up, so the change can take that long to be noticed.
     */
    private static final int WINDOW_TICKS = 240;
    /** Pushes are hurried no more often than this, however many blasts there are. */
    private static final int MIN_GAP_TICKS = 20;

    /** Off for comparison in tests. */
    public static boolean enabled = true;
    /** How many times a push has been hurried, for checks. */
    public static int hurried;
    private static int ticksLeft;
    private static long now;
    private static long lastHurry = -MIN_GAP_TICKS;
    private static boolean unavailable;
    private static Method requestService;
    private static Method dirtyTracker;
    private static Method pendingCount;
    private static Field broadcaster;
    private static Field counter;

    private DistantTerrainRefresh() {}

    /** Call on the server when a blast has broken blocks. */
    public static void afterBlast() {
        if (enabled && !unavailable) {
            ticksLeft = WINDOW_TICKS;
        }
    }

    /** Call once every server tick. */
    public static void onServerTick() {
        now++;
        if (ticksLeft <= 0) {
            return;
        }
        ticksLeft--;
        if (!enabled || now - lastHurry < MIN_GAP_TICKS) {
            return;
        }
        try {
            if (requestService == null && !resolve()) {
                return;
            }
            Object service = requestService.invoke(null);
            if (service == null) {
                // Not serving distant terrain (yet): nothing to hurry.
                return;
            }
            if ((Integer) pendingCount.invoke(dirtyTracker.invoke(service)) > 0) {
                // Its timer counts up to the interval and then pushes; this makes the next count the last.
                counter.setInt(broadcaster.get(service), Integer.MAX_VALUE - 1);
                hurried++;
                lastHurry = now;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            unavailable = true;
            ticksLeft = 0;
            LOGGER.warn("Could not hurry Voxy Server Side's terrain updates after a blast; distant damage will show on its usual timer", e);
        }
    }

    private static boolean resolve() throws ReflectiveOperationException {
        if (!FabricLoader.getInstance().isModLoaded("lss")) {
            unavailable = true;
            ticksLeft = 0;
            return false;
        }
        Method service = Class.forName("dev.vox.lss.networking.server.LSSServerNetworking").getMethod("getRequestService");
        Class<?> serviceClass = service.getReturnType();
        dirtyTracker = serviceClass.getMethod("getDirtyTracker");
        pendingCount = dirtyTracker.getReturnType().getMethod("pendingCount");
        broadcaster = serviceClass.getDeclaredField("dirtyBroadcaster");
        broadcaster.setAccessible(true);
        counter = broadcaster.getType().getDeclaredField("counter");
        counter.setAccessible(true);
        requestService = service;
        return true;
    }
}
