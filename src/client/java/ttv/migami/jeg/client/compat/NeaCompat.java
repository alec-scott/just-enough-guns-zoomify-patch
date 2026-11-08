package ttv.migami.jeg.client.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/**
 * Hooks into Not Enough Animations when it is installed. This class never touches NEA's classes itself, so it loads fine
 * without it; NeaDualMinigunAnimation (which extends NEA's API class) is only loaded once NEA is known to be there.
 */
public final class NeaCompat {
    private static boolean done;

    private NeaCompat() {}

    /** Called every client tick; registers once, after NEA has started (its API needs its instance). */
    public static void registerOnce() {
        if (done) {
            return;
        }
        done = true;
        if (!FabricLoader.getInstance().isModLoaded("notenoughanimations")) {
            return;
        }
        try {
            NeaDualMinigunAnimation.register();
        } catch (Throwable t) {
            LoggerFactory.getLogger("jeg").warn("Could not register the dual-minigun arm pose with Not Enough Animations ({})", t.toString());
        }
    }
}
