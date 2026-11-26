package ttv.migami.jeg.gametest.mixin;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.GpuBackend;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dev clients only (the game tests and runPackClient; this mod never ships): the window opens behind whatever the user
 * is working in, without taking focus, so a test run or a play-test driven through mcpfabric doesn't pull the user out
 * of their own window. Minecraft grabs the mouse only while its window is active (MouseHandler.grabMouse), so the
 * cursor stays free too; clicking into the window still focuses it as usual. The user's standing rule, 2026-11-26.
 */
@Mixin(Window.class)
abstract class WindowNoFocusMixin {
    @Inject(method = "createGlfwWindow", at = @At(value = "INVOKE",
            target = "Lorg/lwjgl/glfw/GLFW;glfwCreateWindow(IILjava/lang/CharSequence;JJ)J"))
    private static void jeg$openUnfocused(int width, int height, String title, long monitor, GpuBackend backend,
                                          CallbackInfoReturnable<Long> cir) {
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
    }
}
