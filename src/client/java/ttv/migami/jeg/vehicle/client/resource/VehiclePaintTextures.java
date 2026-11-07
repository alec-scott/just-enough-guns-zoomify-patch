package ttv.migami.jeg.vehicle.client.resource;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ttv.migami.jeg.Reference;

/**
 * Repaints a vehicle texture in a dye's colour. Only the body paint changes: the muted, mid-tone pixels that make up
 * a vehicle's finish take the dye's hue, keeping their own light and shade so panel lines and weathering still show.
 * Strongly coloured details (warning stripes, lights, seats) and near-black parts (tyres, intakes) are left alone.
 */
public final class VehiclePaintTextures {
    private static final Logger LOGGER = LogManager.getLogger(Reference.MOD_ID + ".VehiclePaint");
    /** Pixels more saturated than this are markings and fittings, not paint. */
    private static final float DETAIL_SATURATION = 0.42F;
    /** Pixels darker than this are tyres, vents and shadow, not paint. */
    private static final float BLACK_LUMINANCE = 0.06F;
    /** How fully paint covers the original colour; a little of the old finish shows through as wear. */
    private static final float COVERAGE = 0.9F;

    private static final Map<String, Identifier> PAINTED = new ConcurrentHashMap<>();

    private VehiclePaintTextures() {}

    /** The base texture repainted in that dye, made once and then reused; the base texture if it cannot be made. */
    public static Identifier painted(Identifier base, DyeColor dye) {
        String key = base + "#" + dye.getName();
        Identifier existing = PAINTED.get(key);
        if (existing != null) {
            return existing;
        }
        Identifier created = create(base, dye);
        PAINTED.put(key, created);
        return created;
    }

    private static Identifier create(Identifier base, DyeColor dye) {
        Minecraft minecraft = Minecraft.getInstance();
        var resource = minecraft.getResourceManager().getResource(base);
        if (resource.isEmpty()) {
            return base;
        }
        try (InputStream in = resource.get().open()) {
            NativeImage image = NativeImage.read(in);
            repaint(image, dye.getTextureDiffuseColor());
            Identifier id = Reference.id("dynamic/vehicle_paint/" + base.getPath().replace('/', '_').replace(".png", "") + "_" + dye.getName());
            minecraft.getTextureManager().register(id, new DynamicTexture(id::toString, image));
            return id;
        } catch (Exception e) {
            LOGGER.warn("Could not paint {} {}", base, dye.getName(), e);
            return base;
        }
    }

    /** Recolours the paintable pixels of an ARGB image in place. Exposed for checks. */
    public static void repaint(NativeImage image, int dyeRgb) {
        int width = image.getWidth();
        int height = image.getHeight();
        // The finish's average brightness becomes the dye's own, so a red-painted vehicle reads as red, not maroon.
        double lumSum = 0.0D;
        int paintable = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getPixel(x, y);
                if (isPaint(argb)) {
                    lumSum += luminance(argb);
                    paintable++;
                }
            }
        }
        if (paintable == 0) {
            return;
        }
        float averageLum = (float) Math.max(0.05D, lumSum / paintable);
        float dyeR = ((dyeRgb >> 16) & 0xFF) / 255.0F;
        float dyeG = ((dyeRgb >> 8) & 0xFF) / 255.0F;
        float dyeB = (dyeRgb & 0xFF) / 255.0F;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getPixel(x, y);
                if (!isPaint(argb)) {
                    continue;
                }
                float shade = luminance(argb) / averageLum;
                float r = channel(dyeR, shade);
                float g = channel(dyeG, shade);
                float b = channel(dyeB, shade);
                float oldR = ((argb >> 16) & 0xFF) / 255.0F;
                float oldG = ((argb >> 8) & 0xFF) / 255.0F;
                float oldB = (argb & 0xFF) / 255.0F;
                image.setPixel(x, y, (argb & 0xFF000000)
                        | (to255(oldR + (r - oldR) * COVERAGE) << 16)
                        | (to255(oldG + (g - oldG) * COVERAGE) << 8)
                        | to255(oldB + (b - oldB) * COVERAGE));
            }
        }
    }

    /** The dye's channel lit to this pixel's shade; past full brightness it washes towards white like a highlight. */
    private static float channel(float dye, float shade) {
        float lit = dye * shade;
        if (shade > 1.0F) {
            lit += (1.0F - dye) * Math.min(1.0F, shade - 1.0F) * 0.5F;
        }
        return Math.min(1.0F, lit);
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
