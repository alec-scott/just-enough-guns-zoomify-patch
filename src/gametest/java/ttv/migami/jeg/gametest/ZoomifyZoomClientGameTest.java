package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zoomify (in the yftm pack) zooms while its key, C by default, is held. Holding a gun must not stop that. Run with
 * -PwithZoomify; without Zoomify the test only logs that the key is missing.
 */
public class ZoomifyZoomClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final String ZOOM_KEY = "zoomify.key.zoom";

    @Override
    public void runTest(ClientGameTestContext context) {
        boolean present = context.computeOnClient(mc -> zoomKey(mc.options) != null);
        if (!present) {
            LOGGER.info("[zoomify] Zoomify is not loaded; run with -PwithZoomify");
            return;
        }
        StringBuilder report = new StringBuilder("zoomify report\n");
        boolean failed = false;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 0");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            for (String item : new String[] {"minecraft:air", "jeg:assault_rifle", "jeg:minigun"}) {
                server.runCommand("item replace entity @p weapon.mainhand with " + item);
                context.waitTicks(30);
                float normal = context.computeOnClient(mc -> mc.gameRenderer.getMainCamera().getFov());
                context.getInput().holdKey(ZoomifyZoomClientGameTest::zoomKey);
                context.waitTicks(30);
                float zoomed = context.computeOnClient(mc -> mc.gameRenderer.getMainCamera().getFov());
                String label = item.substring(item.indexOf(':') + 1);
                context.takeScreenshot("zoomify_" + label + "_zoomed");
                context.getInput().releaseKey(ZoomifyZoomClientGameTest::zoomKey);
                context.waitTicks(30);
                boolean zoomWorks = zoomed < normal * 0.75F;
                failed |= !zoomWorks;
                report.append(String.format("%s: fov %.1f -> %.1f while C held (%s)%n", label, normal, zoomed,
                        zoomWorks ? "zooms" : "NO ZOOM"));
            }
        }
        LOGGER.info(report.toString());
        if (failed) {
            throw new AssertionError("Zoomify did not zoom:\n" + report);
        }
    }

    private static KeyMapping zoomKey(Options options) {
        for (KeyMapping mapping : options.keyMappings) {
            if (mapping.getName().equals(ZOOM_KEY)) {
                return mapping;
            }
        }
        return null;
    }
}
