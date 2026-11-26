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
    private static final MethodHandle IN_USE = find("isShaderPackInUse");
    private static final MethodHandle SHADOW_PASS = find("isRenderingShadowPass");
    private static final boolean DYNAMIC_LIGHTS = FabricLoader.getInstance().isModLoaded("lambdynlights");

    private ShaderPacks() {}

    private static MethodHandle find(String method) {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return null;
        }
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            return MethodHandles.publicLookup()
                    .findVirtual(api, method, MethodType.methodType(boolean.class))
                    .bindTo(instance);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean inUse() {
        return call(IN_USE);
    }

    /**
     * Iris is drawing the shadow map: only depth (and, for translucents, colour) is kept, so lighting work is wasted.
     * Looked up once, so it is cheap enough to ask per draw.
     */
    public static boolean renderingShadowPass() {
        return call(SHADOW_PASS);
    }

    private static boolean call(MethodHandle handle) {
        if (handle == null) {
            return false;
        }
        try {
            return (boolean) handle.invokeExact();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** LambDynamicLights is loaded: searchlight and headlight spots light the ground for real (LambDynamicLightsCompat). */
    public static boolean dynamicLights() {
        return DYNAMIC_LIGHTS;
    }
}
