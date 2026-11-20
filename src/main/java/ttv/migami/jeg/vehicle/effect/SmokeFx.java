package ttv.migami.jeg.vehicle.effect;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Smoke and fire puffs drawn as real smoke (the blasts' soft lit cloud, MissileBlastRenderer on the client), never
 * vanilla's particle sprites (the user's review, 2026-11-19: "use real smoke animation, not puffs"). Vehicle code that
 * runs on both sides calls {@link #emit}; on a client it reaches the renderer, on a server it does nothing. The client
 * installs the {@link #emitter} at start-up.
 */
public final class SmokeFx {
    /** Light grey smoke from a hit engine. */
    public static final int GREY = 0;
    /** Dark smoke, a vehicle badly hurt. */
    public static final int DARK = 1;
    /** Thick black smoke, a fire: a column off a burning hulk. */
    public static final int BLACK = 2;
    /** A tongue of flame, cooling as it rises. */
    public static final int FIRE = 3;
    /** A flare's white trail. */
    public static final int WHITE = 4;

    public interface Emitter {
        void emit(Level level, Vec3 at, Vec3 velocity, int kind, float scale);
    }

    private static volatile Emitter emitter = (level, at, velocity, kind, scale) -> {};

    private SmokeFx() {
    }

    public static void install(Emitter installed) {
        emitter = installed;
    }

    /** One puff of {@code kind}, {@code scale} times its usual size, moving off at {@code velocity} (blocks a tick). */
    public static void emit(Level level, Vec3 at, Vec3 velocity, int kind, float scale) {
        if (level.isClientSide()) {
            emitter.emit(level, at, velocity, kind, scale);
        }
    }
}
