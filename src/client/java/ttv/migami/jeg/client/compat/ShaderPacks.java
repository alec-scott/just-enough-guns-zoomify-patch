package ttv.migami.jeg.client.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Whether an Iris shader pack is in use, looked up reflectively so Iris stays optional. Shader packs (Complementary on
 * yftm) treat Minecraft's additive "eyes" layers as emissive and bloom them, so a soft drawn glow on the ground turns
 * into a hard white blob; light effects that the pack lights for real are left to it.
 */
public final class ShaderPacks {
    private static final MethodHandle IN_USE = find();
    private static final boolean DYNAMIC_LIGHTS = FabricLoader.getInstance().isModLoaded("lambdynlights");

    private ShaderPacks() {}

    private static MethodHandle find() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return null;
        }
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup()
                    .findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class))
                    .bindTo(instance);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean inUse() {
        if (IN_USE == null) {
            return false;
        }
        try {
            return (boolean) IN_USE.invokeExact();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** LambDynamicLights is loaded: searchlight and headlight spots light the ground for real (LambDynamicLightsCompat). */
    public static boolean dynamicLights() {
        return DYNAMIC_LIGHTS;
    }
}
