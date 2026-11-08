package ttv.migami.jeg.vehicle.client.resource;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.Reference;

/**
 * Repaints a vehicle texture in a dye's colour, as a real paint job rather than a flat tint. Only the body paint changes:
 * the muted, mid-tone pixels that make up a vehicle's finish take the paint, keeping their own light and shade so panel
 * lines and weathering still show.
 * Strongly coloured details (warning stripes, lights, seats) and near-black parts (tyres, intakes) are left alone, and
 * so are whole parts the vehicle lists as keeping their own finish (rotors, glass, weapons, gear: see {@link VehiclePaintMask}).
 */
public final class VehiclePaintTextures {
    private static final Logger LOGGER = LogManager.getLogger(Reference.MOD_ID + ".VehiclePaint");
    /** Pixels more saturated than this are markings and fittings, not paint. */
    private static final float DETAIL_SATURATION = 0.42F;
    /** Pixels darker than this are tyres, vents and shadow, not paint. */
    private static final float BLACK_LUMINANCE = 0.06F;
    /** How fully paint covers the original colour; a little of the old finish shows through as wear. */
    private static final float COVERAGE = 0.88F;
    /** How much more of the old finish shows through in dirty, worn and shadowed spots. */
    private static final float GRIME_SHOW_THROUGH = 0.38F;
    /** The dye swatch's saturation and brightness kept in the paint. */
    private static final float PAINT_SATURATION = 0.86F;
    private static final float PAINT_VALUE = 0.74F;
    /** Extra contrast on the texture's light and shade, and how bright a panel can get relative to the paint. */
    private static final float SHADE_CONTRAST = 1.3F;
    private static final float MAX_SHADE = 1.7F;
    /** A faint satin sheen on panels brighter than this. */
    private static final float SHEEN_FROM = 1.2F;
    private static final float SHEEN = 0.1F;

    private static final Map<String, Identifier> PAINTED = new ConcurrentHashMap<>();

    private VehiclePaintTextures() {}

    /** The base texture repainted in that dye, made once and then reused; the base texture if it cannot be made. */
    public static Identifier painted(Identifier base, DyeColor dye, String vehicle) {
        String key = base + "#" + dye.getName();
        Identifier existing = PAINTED.get(key);
        if (existing != null) {
            return existing;
        }
        Identifier created = create(base, dye, vehicle);
        PAINTED.put(key, created);
        return created;
    }

    private static Identifier create(Identifier base, DyeColor dye, String vehicle) {
        Minecraft minecraft = Minecraft.getInstance();
        var resource = minecraft.getResourceManager().getResource(base);
        if (resource.isEmpty()) {
            return base;
        }
        try (InputStream in = resource.get().open()) {
            NativeImage image = NativeImage.read(in);
            boolean[] unpainted = VehiclePaintMask.unpainted(minecraft.getResourceManager(), base, vehicle, image.getWidth(), image.getHeight());
            repaint(image, dye.getTextureDiffuseColor(), unpainted);
            Identifier id = Reference.id("dynamic/vehicle_paint/" + base.getPath().replace('/', '_').replace(".png", "") + "_" + dye.getName());
            minecraft.getTextureManager().register(id, new DynamicTexture(id::toString, image));
            return id;
        } catch (Exception e) {
            LOGGER.warn("Could not paint {} {}", base, dye.getName(), e);
            return base;
        }
    }

    /**
     * Recolours the paintable pixels of an ARGB image in place, skipping those {@code unpainted} marks (row-major; null
     * masks nothing). Exposed for checks.
     *
     * <p>The dye is turned into a real paint colour (a little less saturated and darker than the bright dye swatch), then
     * laid over the finish keeping the texture's own light and shade with a touch more contrast, so panel lines and
     * wear stay sharp. Bright panels keep the paint's hue rather than washing out to pastel, with only a faint satin
     * sheen, and dirty, worn and shadowed spots let more of the old finish show through.
     */
    public static void repaint(NativeImage image, int dyeRgb, boolean @Nullable [] unpainted) {
        int width = image.getWidth();
        int height = image.getHeight();
        double lumSum = 0.0D;
        int paintPixels = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getPixel(x, y);
                if (paintable(unpainted, width, x, y) && isPaint(argb)) {
                    lumSum += luminance(argb);
                    paintPixels++;
                }
            }
        }
        if (paintPixels == 0) {
            return;
        }
        float averageLum = (float) Math.max(0.05D, lumSum / paintPixels);
        float[] paint = paintColour(dyeRgb);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getPixel(x, y);
                if (!paintable(unpainted, width, x, y) || !isPaint(argb)) {
                    continue;
                }
                float relative = luminance(argb) / averageLum;
                float shade = Math.min(MAX_SHADE, (float) Math.pow(relative, SHADE_CONTRAST));
                float r = paint[0] * shade;
                float g = paint[1] * shade;
                float b = paint[2] * shade;
                // Past full brightness the colour is scaled back as a whole, so a bright red panel stays red, not pink.
                float top = Math.max(r, Math.max(g, b));
                if (top > 1.0F) {
                    r /= top;
                    g /= top;
                    b /= top;
                }
                if (shade > SHEEN_FROM) {
                    float sheen = Math.min(1.0F, shade - SHEEN_FROM) * SHEEN;
                    r += (1.0F - r) * sheen;
                    g += (1.0F - g) * sheen;
                    b += (1.0F - b) * sheen;
                }
                float grime = Math.clamp((1.0F - relative) * 1.2F, 0.0F, 1.0F);
                float coverage = COVERAGE - GRIME_SHOW_THROUGH * grime;
                float oldR = ((argb >> 16) & 0xFF) / 255.0F;
                float oldG = ((argb >> 8) & 0xFF) / 255.0F;
                float oldB = (argb & 0xFF) / 255.0F;
                image.setPixel(x, y, (argb & 0xFF000000)
                        | (to255(oldR + (r - oldR) * coverage) << 16)
                        | (to255(oldG + (g - oldG) * coverage) << 8)
                        | to255(oldB + (b - oldB) * coverage));
            }
        }
    }

    /** The dye as a vehicle paint: the dye swatch is bright and toy-like, real paint is deeper and less saturated. */
    static float[] paintColour(int dyeRgb) {
        float r = ((dyeRgb >> 16) & 0xFF) / 255.0F;
        float g = ((dyeRgb >> 8) & 0xFF) / 255.0F;
        float b = (dyeRgb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float value = max;
        float saturation = max <= 0.0F ? 0.0F : (max - min) / max;
        float hue = 0.0F;
        if (max > min) {
            float delta = max - min;
            if (max == r) {
                hue = ((g - b) / delta) % 6.0F;
            } else if (max == g) {
                hue = (b - r) / delta + 2.0F;
            } else {
                hue = (r - g) / delta + 4.0F;
            }
            hue /= 6.0F;
            if (hue < 0.0F) {
                hue += 1.0F;
            }
        }
        int rgb = Mth.hsvToRgb(hue, saturation * PAINT_SATURATION, value * PAINT_VALUE);
        return new float[] {((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F};
    }

    private static boolean paintable(boolean @Nullable [] unpainted, int width, int x, int y) {
        return unpainted == null || !unpainted[y * width + x];
    }

    static boolean isPaint(int argb) {
        if (((argb >>> 24) & 0xFF) < 16) {
            return false;
        }
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float saturation = max <= 0.0F ? 0.0F : (max - min) / max;
        return luminance(argb) >= BLACK_LUMINANCE && saturation <= DETAIL_SATURATION;
    }

    private static float luminance(int argb) {
        return (0.299F * ((argb >> 16) & 0xFF) + 0.587F * ((argb >> 8) & 0xFF) + 0.114F * (argb & 0xFF)) / 255.0F;
    }

    private static int to255(float value) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, value)) * 255.0F);
    }
}
