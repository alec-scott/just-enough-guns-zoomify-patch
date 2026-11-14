package ttv.migami.jeg.gametest;

import java.lang.reflect.Field;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.client.MinigunClient;
import ttv.migami.jeg.init.ModDataComponents;
import ttv.migami.jeg.item.GunItem;

/**
 * Diagnostic, not a check: traces a minigun's rapid cool-down on the reload key tick by tick (what the client sees of the
 * gun, the barrels' spin, the hiss, the hand's height) with frames, to find what jumps.
 */
public class MinigunCoolTraceClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a minecraft:resistance 600 5 true");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 0");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runCommand("item replace entity @p weapon.mainhand with jeg:minigun");
            server.runCommand("item replace entity @p weapon.offhand with minecraft:air");
            context.waitTicks(40);
            for (String view : new String[]{"fp", "tp"}) {
                context.runOnClient(mc -> mc.options.setCameraType(view.equals("fp") ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_FRONT));
                server.runOnServer(s -> {
                    var stack = s.getPlayerList().getPlayers().getFirst().getMainHandItem();
                    stack.set(ModDataComponents.GUN_HEAT.get(), 280);
                });
                context.waitTicks(10);
                StringBuilder trace = new StringBuilder();
                for (int tick = -4; tick <= 90; tick++) {
                    if (tick == 0) {
                        context.getInput().pressKey(KeyBindings.RELOAD);
                    }
                    context.waitTick();
                    final int at = tick;
                    String row = context.computeOnClient(mc -> line(mc, at));
                    trace.append(row).append(System.lineSeparator());
                    if (tick >= -2 && tick <= 24 && tick % 2 == 0 || tick >= 66 && tick <= 84 && tick % 3 == 0) {
                        context.takeScreenshot("minigun_cool_trace_" + view + "_" + (tick < 0 ? "m" + (-tick) : String.valueOf(tick)));
                    }
                }
                LOGGER.warn("minigun cool trace ({}):{}{}", view, System.lineSeparator(), trace);
                context.waitTicks(20);
            }
        }
    }

    private static String line(Minecraft mc, int tick) {
        var player = mc.player;
        var stack = player.getMainHandItem();
        float height = -1.0F;
        float heightO = -1.0F;
        try {
            Field main = ItemInHandRenderer.class.getDeclaredField("mainHandHeight");
            Field old = ItemInHandRenderer.class.getDeclaredField("oMainHandHeight");
            main.setAccessible(true);
            old.setAccessible(true);
            height = main.getFloat(mc.getEntityRenderDispatcher().getItemInHandRenderer());
            heightO = old.getFloat(mc.getEntityRenderDispatcher().getItemInHandRenderer());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Read through the game renderer instead where the dispatcher does not hold it.
            try {
                Field main = ItemInHandRenderer.class.getDeclaredField("mainHandHeight");
                main.setAccessible(true);
                height = main.getFloat(mc.gameRenderer.itemInHandRenderer);
            } catch (ReflectiveOperationException | RuntimeException ignoredToo) {
            }
        }
        return String.format("t%3d heat=%3d cooling=%s spin=%.2f angle=%.1f vent=%s using=%s hand=%.3f/%.3f prompt=%s audio=%s",
                tick, stack.getOrDefault(ModDataComponents.GUN_HEAT.get(), 0), GunItem.isRapidCooling(stack),
                MinigunClient.spin(player.getId()), MinigunClient.barrelAngle(player.getId(), 1.0F),
                MinigunClient.venting(player.getId()), player.isUsingItem(), height, heightO,
                mc.gui.getChat().getRecentChat().isEmpty() ? "" : "chat", MinigunClient.audioState(player.getId()));
    }
}
