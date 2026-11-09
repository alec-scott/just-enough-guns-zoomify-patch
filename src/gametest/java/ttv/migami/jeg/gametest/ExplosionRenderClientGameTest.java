package ttv.migami.jeg.gametest;

import java.io.IOException;
import java.nio.file.Files;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders the things that use the entity overlay texture (Sampler1) in front of the player, one at a time, then a
 * vanilla TNT explosion, so pack runs check that explosions render. The phase logged last before a crash shows which
 * one triggers it: hurt (red hurt flash), flash (primed TNT's white flash), explode (the blast itself).
 * With -PwithPack -PrenderValidation (Mojang's dev-only render checks on, via MaLiLib) and an Iris shader pack, the
 * explode phase fails with "Missing sampler Sampler1"; players never run with those checks.
 */
public class ExplosionRenderClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a 0 ~ 0 0 10");
            context.waitTicks(20);

            LOGGER.info("[explosion-render] phase hurt");
            server.runCommand("execute as @p at @s run summon minecraft:pig ~ ~ ~4 {NoAI:1b,Invulnerable:0b}");
            context.waitTicks(5);
            server.runCommand("damage @e[type=minecraft:pig,limit=1] 1");
            context.waitTicks(5);
            screenshot(context, "explosion_render_hurt");
            context.waitTicks(20);

            LOGGER.info("[explosion-render] phase flash");
            server.runCommand("execute as @p at @s run summon minecraft:tnt ~-2 ~ ~5 {fuse:60}");
            context.waitTicks(30);
            screenshot(context, "explosion_render_flash");
            server.runCommand("kill @e[type=minecraft:tnt]");
            context.waitTicks(10);

            LOGGER.info("[explosion-render] phase explode");
            server.runCommand("execute as @p at @s run summon minecraft:tnt ~2 ~ ~6 {fuse:0}");
            context.waitTicks(3);
            screenshot(context, "explosion_render_explode");
            context.waitTicks(40);
            LOGGER.info("[explosion-render] phase done");
        }
    }

    /**
     * Fabric's screenshot renders an extra frame outside the game loop, and Sodium without an Iris shader pack crashes
     * on it ("Global terrain uniforms have not been updated"), so skip screenshots there.
     */
    private static void screenshot(ClientGameTestContext context, String name) {
        boolean sodium = FabricLoader.getInstance().isModLoaded("sodium");
        boolean shaders = false;
        if (FabricLoader.getInstance().isModLoaded("iris")) {
            try {
                shaders = Files.readString(FabricLoader.getInstance().getConfigDir().resolve("iris.properties"))
                        .contains("enableShaders=true");
            } catch (IOException ignored) {
            }
        }
        if (!sodium || shaders) {
            context.takeScreenshot(name);
        } else {
            LOGGER.info("[explosion-render] screenshot skipped: {}", name);
        }
    }
}
