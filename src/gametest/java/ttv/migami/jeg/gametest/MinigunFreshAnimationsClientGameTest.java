package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The yftm pack's Fresh Animations resource packs (with EMF) re-animate the player model. Screenshots how the minigun
 * and a rifle are held in first and third person with them on. Run with -PwithFreshAnimations so EMF/ETF and the packs
 * are present; without them it only shows the vanilla look.
 */
public class MinigunFreshAnimationsClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> {
            PackRepository repository = mc.getResourcePackRepository();
            repository.reload();
            List<String> selected = new ArrayList<>(repository.getSelectedIds());
            // Fresh Animations first, its add-ons on top of it.
            for (String name : List.of("FreshAnimations", "FA+All_Extensions", "FA+Player")) {
                for (Pack pack : repository.getAvailablePacks()) {
                    if (pack.getId().startsWith("file/" + name) && !selected.contains(pack.getId())) {
                        selected.add(pack.getId());
                    }
                }
            }
            repository.setSelected(selected);
            LOGGER.info("[fresh-animations] resource packs: {}", selected);
            mc.reloadResourcePacks();
        });
        context.waitTicks(40);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 0");
            this.capture(context, server, "jeg:minigun", "minigun");
            this.capture(context, server, "jeg:assault_rifle", "rifle");
        }
    }

    private void capture(ClientGameTestContext context, TestServerContext server, String gun, String label) {
        server.runCommand("item replace entity @p weapon.mainhand with " + gun);
        for (CameraType camera : List.of(CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_FRONT, CameraType.THIRD_PERSON_BACK)) {
            context.runOnClient(mc -> mc.options.setCameraType(camera));
            context.waitTicks(40);
            String view = camera.name().toLowerCase();
            context.takeScreenshot("fa_" + label + "_" + view + "_idle");
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(30);
            context.takeScreenshot("fa_" + label + "_" + view + "_firing");
            context.getInput().releaseKey(options -> options.keyAttack);
            context.waitTicks(10);
        }
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
    }
}
