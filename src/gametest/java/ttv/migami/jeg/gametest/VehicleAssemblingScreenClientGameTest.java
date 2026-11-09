package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.client.screen.VehicleAssemblingScreen;

/**
 * The vehicle assembling table shows what each vehicle needs: picking one lists its items with how many are carried, and
 * Assemble only works once everything is there. Carrying enough for a BMP-2 but too few feathers for an AH-6, the BMP-2
 * can be built and the AH-6 cannot; building the BMP-2 takes its 48 iron.
 */
public class VehicleAssemblingScreenClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("clear @a");
            server.runCommand("give @a minecraft:iron_ingot 64");
            server.runCommand("give @a minecraft:copper_ingot 20");
            server.runCommand("give @a minecraft:redstone 14");
            server.runCommand("give @a minecraft:feather 4");
            server.runCommand("setblock 2 -60 0 jeg:vehicle_assembling_table");
            context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
            context.waitTicks(10);
            server.runOnServer(s -> {
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                player.openMenu((MenuProvider) s.overworld().getBlockEntity(new BlockPos(2, -60, 0)));
            });
            context.waitTicks(10);
            boolean open = context.computeOnClient(mc -> mc.screen instanceof VehicleAssemblingScreen);
            if (!open) {
                throw new AssertionError("the vehicle assembling screen did not open");
            }
            shot(context, "vehicle_assembling_open");

            click(context, "BMP-2", failures);
            context.waitTicks(2);
            String bmp = context.computeOnClient(mc -> String.valueOf(screen(mc).selectedVehicle()) + " " + screen(mc).canAssemble());
            if (!bmp.equals(Reference.id("bmp2") + " true")) {
                failures.append("picking the BMP-2 with its materials carried should allow Assemble, got ").append(bmp).append('\n');
            }
            shot(context, "vehicle_assembling_bmp2");

            click(context, "AH-6", failures);
            context.waitTicks(2);
            String ah6 = context.computeOnClient(mc -> String.valueOf(screen(mc).selectedVehicle()) + " " + screen(mc).canAssemble());
            if (!ah6.equals(Reference.id("ah6") + " false")) {
                failures.append("the AH-6 is short of feathers, Assemble should be off, got ").append(ah6).append('\n');
            }
            shot(context, "vehicle_assembling_ah6");

            click(context, "BMP-2", failures);
            context.waitTicks(2);
            click(context, Component.translatable("gui.jeg.vehicle_assembling.assemble").getString(), failures);
            context.waitTicks(10);
            int iron = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getInventory().countItem(Items.IRON_INGOT));
            if (iron != 16) {
                failures.append("building the BMP-2 should leave 16 iron, there are ").append(iron).append('\n');
            }
            shot(context, "vehicle_assembling_built");
            LOGGER.info("vehicle assembling: bmp2 pick {}, ah6 pick {}, iron after build {}", bmp, ah6, iron);
            context.runOnClient(mc -> mc.setScreen(null));
        }
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString());
        }
    }

    /** A screenshot without the recipe and advancement toasts the items given at the start pop up over the panel. */
    private static void shot(ClientGameTestContext context, String name) {
        context.runOnClient(mc -> mc.getToastManager().clear());
        context.waitTick();
        context.takeScreenshot(name);
    }

    private static VehicleAssemblingScreen screen(Minecraft mc) {
        return (VehicleAssemblingScreen) mc.screen;
    }

    /** Clicks the screen's button with this label with the mouse, as a player would. */
    private static void click(ClientGameTestContext context, String label, StringBuilder failures) {
        double[] at = context.computeOnClient(mc -> {
            for (GuiEventListener child : mc.screen.children()) {
                if (child instanceof Button button && button.getMessage().getString().equals(label)) {
                    double scale = mc.getWindow().getGuiScale();
                    return new double[]{(button.getX() + button.getWidth() / 2.0D) * scale, (button.getY() + button.getHeight() / 2.0D) * scale};
                }
            }
            return null;
        });
        if (at == null) {
            failures.append("no button labelled ").append(label).append('\n');
            return;
        }
        context.getInput().setCursorPos(at[0], at[1]);
        context.waitTick();
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }
}
