package ttv.migami.jeg.vehicle.client.resource;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;

/**
 * Where a texture has anything to draw: a coarse grid of {@link #CELL}-pixel cells, each set when any pixel in it has
 * alpha. The glass and glow layers draw the whole model again with a texture that is empty almost everywhere (glass is
 * the windows, glow the lamps and screens), and under a shader pack every one of those fragments costs: measured
 * 2026-11-26 with the ArmourTest fleet in view under Photon, the vehicles took 13 ms a frame. A quad whose texture area
 * falls in empty cells only is skipped ({@link ttv.migami.jeg.vehicle.client.render.QuadMaskConsumer}). The test is
 * conservative: a cell counts if any pixel in it is set, and a quad's area is widened by a cell each way, so a quad
 * with one visible pixel is always drawn and the look is unchanged.
 */
public final class TextureCoverage {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("jeg");
    /** Cell size in pixels: 4 keeps the grid of a 2048 texture at 512 x 512 (1 MB of sums), small for the few in use. */
    public static final int CELL = 4;
    private static final Map<Identifier, TextureCoverage> CACHE = new ConcurrentHashMap<>();
    /** No pixels could be read: everything counts as covered, so nothing is skipped. */
    private static final TextureCoverage EVERYWHERE = new TextureCoverage(1, 1, new int[]{0, 0, 0, 1});

    private final int columns;
    private final int rows;
    /** Summed-area table of set cells, (columns + 1) x (rows + 1). */
    private final int[] sums;

    private TextureCoverage(int columns, int rows, int[] sums) {
        this.columns = columns;
        this.rows = rows;
        this.sums = sums;
    }

    /** The coverage of {@code texture}'s pixels with any alpha, read once and kept until resources reload. */
    public static TextureCoverage of(Identifier texture) {
        return CACHE.computeIfAbsent(texture, TextureCoverage::read);
    }

    /** Resources reloaded (a resource pack, F3+T): textures may have changed. */
    public static void clear() {
        CACHE.clear();
    }

    private static TextureCoverage read(Identifier texture) {
        NativeImage image = VehicleGlassTextures.read(texture);
        if (image == null) {
            LOGGER.warn("[vehicle-cull] no pixels for {}: every quad drawn", texture);
            return EVERYWHERE;
        }
        try (image) {
            TextureCoverage coverage = build(image);
            int set = coverage.sums[coverage.sums.length - 1];
            LOGGER.debug("[vehicle-cull] {} {}x{}: {} of {} cells set", texture, image.getWidth(), image.getHeight(), set,
                    coverage.columns * coverage.rows);
            return coverage;
        } catch (RuntimeException e) {
            LOGGER.warn("[vehicle-cull] could not read {}: every quad drawn", texture, e);
            return EVERYWHERE;
        }
    }

    static TextureCoverage build(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int columns = (width + CELL - 1) / CELL;
        int rows = (height + CELL - 1) / CELL;
        boolean[] set = new boolean[columns * rows];
        for (int y = 0; y < height; y++) {
            int row = (y / CELL) * columns;
            for (int x = 0; x < width; x++) {
                if ((image.getPixel(x, y) >>> 24) != 0) {
                    set[row + x / CELL] = true;
                }
            }
        }
        return fromCells(columns, rows, set);
    }

    /** From a grid of set cells, row by row (also used by the test). */
    static TextureCoverage fromCells(int columns, int rows, boolean[] set) {
        int stride = columns + 1;
        int[] sums = new int[stride * (rows + 1)];
        for (int r = 0; r < rows; r++) {
            int line = 0;
            for (int c = 0; c < columns; c++) {
                line += set[r * columns + c] ? 1 : 0;
                sums[(r + 1) * stride + c + 1] = sums[r * stride + c + 1] + line;
            }
        }
        return new TextureCoverage(columns, rows, sums);
    }

    /**
     * Whether anything is drawn in the texture area spanned by these UVs (0 to 1 across the texture), widened by a cell
     * each way so filtering and rounding can never drop a visible pixel.
     */
    public boolean any(float minU, float minV, float maxU, float maxV) {
        int c0 = clamp((int) Math.floor(minU * this.columns) - 1, this.columns - 1);
        int c1 = clamp((int) Math.floor(maxU * this.columns) + 1, this.columns - 1);
        int r0 = clamp((int) Math.floor(minV * this.rows) - 1, this.rows - 1);
        int r1 = clamp((int) Math.floor(maxV * this.rows) + 1, this.rows - 1);
        int stride = this.columns + 1;
        int count = this.sums[(r1 + 1) * stride + c1 + 1] - this.sums[r0 * stride + c1 + 1]
                - this.sums[(r1 + 1) * stride + c0] + this.sums[r0 * stride + c0];
        return count > 0;
    }

    private static int clamp(int value, int max) {
        return value < 0 ? 0 : Math.min(value, max);
    }
}
