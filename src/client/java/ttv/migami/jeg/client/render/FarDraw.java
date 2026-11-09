package ttv.migami.jeg.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.world.phys.Vec3;

/**
 * Drawing missiles, trails and blasts beyond the render distance. Vanilla fades everything to nothing at the
 * render-distance fog, so a missile or blast past it was invisible even to players who see that far through LOD terrain.
 * Something far off is drawn closer instead, shrunk by the same factor around the camera: it covers exactly the same
 * pixels, but sits inside the fog. Within {@link #KNEE} of the reach nothing changes; past it distances are eased in
 * smoothly, so things keep their order front to back.
 * <p>
 * The reach is the nearer of the vanilla fog and the chunk render distance. Voxy pushes the vanilla fog out to a billion
 * blocks, which used to switch this off entirely, yet shader packs (Complementary, Photon) still fade the world out at
 * the chunk render distance themselves, and so hid far missiles and blasts.
 */
public final class FarDraw {
    /** Pulled-in geometry ends up no further than this share of the fog start, where the fog has not begun. */
    private static final double REACH = 0.9D;
    /** Share of the chunk render distance shader packs leave clear before their own edge fog sets in. */
    private static final double SHADER_CLEAR = 0.7D;
    /** Share of that reach within which nothing is moved. */
    private static final double KNEE = 0.8D;
    private static final double MIN_REACH = 24.0D;
    private static float renderFogStart = Float.MAX_VALUE;

    private FarDraw() {}

    /** Every frame, the fog the world is drawn with (after any mod that moves it, such as LOD renderers). */
    public static void recordFog(FogData fog) {
        renderFogStart = fog.renderDistanceStart;
    }

    /** Where the render-distance fog last began, for checks. */
    public static float renderFogStart() {
        return renderFogStart;
    }

    /** How far out things are drawn where they are; beyond this they are pulled in. */
    public static double reach() {
        double chunks = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0D * SHADER_CLEAR;
        return Math.max(MIN_REACH, Math.min(renderFogStart * REACH, chunks));
    }

    /**
     * How much to scale a camera-relative position (and everything drawn around it) by to bring it inside the fog:
     * 1 when it is already well inside.
     */
    public static float scale(Vec3 relative) {
        return scale(relative.x, relative.y, relative.z);
    }

    public static float scale(double x, double y, double z) {
        double reach = reach();
        // The render-distance fog is cylindrical: horizontal distance, or height above or below, whichever is greater.
        double distance = Math.max(Math.sqrt(x * x + z * z), Math.abs(y));
        double knee = reach * KNEE;
        if (!(distance > knee)) {
            return 1.0F;
        }
        double room = reach - knee;
        double pulled = knee + room * (1.0D - Math.exp(-(distance - knee) / room));
        return (float) (pulled / distance);
    }
}
