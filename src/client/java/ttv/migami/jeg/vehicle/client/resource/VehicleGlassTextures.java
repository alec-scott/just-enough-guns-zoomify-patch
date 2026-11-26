package ttv.migami.jeg.vehicle.client.resource;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.Reference;

/**
 * Vehicles are drawn opaque (cutout), so shader packs light and shadow them as they do mobs: drawn translucent, Photon
 * gave them a flat, washed-out look. Glass is the one thing that needs blending, so a texture with partly transparent
 * pixels (the FSV-6's windows, the A-10's canopy) is split once into a solid copy, without those pixels, for the main
 * pass, and a glass copy, with only them, drawn translucent by VehicleGlassLayer. Textures with no such pixels are used
 * as they are, with no glass pass.
 */
public final class VehicleGlassTextures {
    private static final Logger LOGGER = LogManager.getLogger(Reference.MOD_ID + ".VehicleGlass");
    private static final Map<Identifier, Split> SPLITS = new ConcurrentHashMap<>();

    /** The solid texture for the main pass, and the glass texture (null when there is no glass). */
    public record Split(Identifier solid, @Nullable Identifier glass) {}

    private VehicleGlassTextures() {}

    public static Split split(Identifier texture) {
        return SPLITS.computeIfAbsent(texture, VehicleGlassTextures::create);
    }

    private static Split create(Identifier texture) {
        NativeImage image = read(texture);
        if (image == null) {
            return new Split(texture, null);
        }
        try (image) {
            int width = image.getWidth();
            int height = image.getHeight();
            boolean anyGlass = false;
            for (int y = 0; y < height && !anyGlass; y++) {
                for (int x = 0; x < width; x++) {
                    int alpha = image.getPixel(x, y) >>> 24;
                    if (alpha > 0 && alpha < 255) {
                        anyGlass = true;
                        break;
                    }
                }
            }
            if (!anyGlass) {
                return new Split(texture, null);
            }
            NativeImage solid = new NativeImage(width, height, false);
            NativeImage glass = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getPixel(x, y);
                    int alpha = argb >>> 24;
                    boolean isGlass = alpha > 0 && alpha < 255;
                    solid.setPixel(x, y, isGlass ? 0 : argb);
                    glass.setPixel(x, y, isGlass ? argb : 0);
                }
            }
            String key = texture.getNamespace() + "_" + texture.getPath().replace('/', '_').replace(".png", "");
            Identifier solidId = Reference.id("dynamic/vehicle_solid/" + key);
            Identifier glassId = Reference.id("dynamic/vehicle_glass/" + key);
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.getTextureManager().register(solidId, new DynamicTexture(solidId::toString, solid));
            minecraft.getTextureManager().register(glassId, new DynamicTexture(glassId::toString, glass));
            return new Split(solidId, glassId);
        } catch (Exception e) {
            LOGGER.warn("Could not split the glass out of {}", texture, e);
            return new Split(texture, null);
        }
    }

    /** A copy of the texture's pixels: from the resource pack, or from a texture made at runtime (a paint job). */
    @Nullable
    static NativeImage read(Identifier texture) {
        Minecraft minecraft = Minecraft.getInstance();
        var resource = minecraft.getResourceManager().getResource(texture);
        if (resource.isPresent()) {
            try (InputStream in = resource.get().open()) {
                return NativeImage.read(in);
            } catch (Exception e) {
                LOGGER.warn("Could not read {}", texture, e);
                return null;
            }
        }
        if (minecraft.getTextureManager().getTexture(texture) instanceof DynamicTexture dynamic && dynamic.getPixels() != null) {
            NativeImage pixels = dynamic.getPixels();
            NativeImage copy = new NativeImage(pixels.getWidth(), pixels.getHeight(), false);
            copy.copyFrom(pixels);
            return copy;
        }
        return null;
    }
}
