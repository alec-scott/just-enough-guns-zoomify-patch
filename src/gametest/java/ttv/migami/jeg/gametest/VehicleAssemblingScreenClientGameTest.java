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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.client.screen.VehicleAssemblingScreen;

import java.util.List;

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

            // The list runs by category (Ground first); the helicopters and planes are under Air.
            click(context, Component.translatable("gui.jeg.vehicle_assembling.category.air").getString(), failures);
            context.waitTicks(2);
            List<String> air = context.computeOnClient(mc -> screen(mc).listedVehicles().stream().map(Identifier::getPath).toList());
            if (!air.equals(List.of("ah6", "mi28", "tom6", "a10"))) {
                failures.append("the Air tab should list the AH-6, Mi-28, bush plane and A-10, got ").append(air).append('\n');
            }
            click(context, "AH-6", failures);
            context.waitTicks(2);
            String ah6 = context.computeOnClient(mc -> String.valueOf(screen(mc).selectedVehicle()) + " " + screen(mc).canAssemble());
            if (!ah6.equals(Reference.id("ah6") + " false")) {
                failures.append("the AH-6 is short of feathers, Assemble should be off, got ").append(ah6).append('\n');
            }
            shot(context, "vehicle_assembling_ah6");

            // The A-10 is on the list too, a little dearer than the MI-28; nowhere near enough carried for it.
            click(context, "A-10", failures);
            context.waitTicks(2);
            String a10 = context.computeOnClient(mc -> String.valueOf(screen(mc).selectedVehicle()) + " " + screen(mc).canAssemble());
            if (!a10.equals(Reference.id("a10") + " false")) {
                failures.append("the A-10 should be listed and need more than is carried, got ").append(a10).append('\n');
            }
            shot(context, "vehicle_assembling_a10");

            // The guns that fire on their own have a tab of their own.
            click(context, Component.translatable("gui.jeg.vehicle_assembling.category.defences").getString(), failures);
            context.waitTicks(2);
            List<String> defences = context.computeOnClient(mc -> screen(mc).listedVehicles().stream().map(Identifier::getPath).toList());
            if (!defences.equals(List.of("sentry", "hpj11"))) {
                failures.append("the Defences tab should list the sentry and the HPJ-11, got ").append(defences).append('\n');
            }
            shot(context, "vehicle_assembling_defences");

            click(context, Component.translatable("gui.jeg.vehicle_assembling.filter.all").getString(), failures);
            context.waitTicks(2);
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

            // Each vehicle's turning model in the card (the user, 2026-11-20: show the model in 3D, names that fit), for
            // looking at: every model must sit whole inside its stage. Then the screen at GUI scales 2, 3 and 4.
            // Dragging across the stage turns the model with the mouse (1.4 degrees a GUI pixel), and it stays put
            // while held.
            context.runOnClient(mc -> screen(mc).show(Reference.id("bmp2")));
            context.waitTicks(2);
            double[] stage = context.computeOnClient(mc -> {
                double gui = mc.getWindow().getGuiScale();
                int left = (mc.getWindow().getGuiScaledWidth() - 320) / 2, top = (mc.getWindow().getGuiScaledHeight() - 238) / 2;
                return new double[]{(left + 248) * gui, (top + 78) * gui, gui};
            });
            context.getInput().setCursorPos(stage[0], stage[1]);
            context.waitTick();
            context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTick();
            float before = context.computeOnClient(mc -> screen(mc).modelTurn());
            context.getInput().setCursorPos(stage[0] + 30 * stage[2], stage[1]);
            context.waitTicks(3);
            float dragged = context.computeOnClient(mc -> screen(mc).modelTurn());
            context.waitTicks(10);
            float held = context.computeOnClient(mc -> screen(mc).modelTurn());
            context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            float turned = ((before - dragged) % 360.0F + 360.0F) % 360.0F;
            LOGGER.info("vehicle assembling: drag 30 px turned the model {} degrees (before {}, after {}, held {})", turned, before, dragged, held);
            if (Math.abs(turned - 42.0F) > 6.0F || Math.abs(held - dragged) > 0.01F) {
                failures.append("dragging 30 px should turn the model 42 degrees and hold it, turned ").append(turned)
                        .append(", moved ").append(held - dragged).append(" while held\n");
            }

            List<Identifier> offered = context.computeOnClient(mc -> screen(mc).offeredVehicles());
            for (Identifier id : offered) {
                context.runOnClient(mc -> screen(mc).show(id));
                context.waitTicks(3);
                shot(context, "vehicle_assembling_model_" + id.getPath());
            }
            int scale = context.computeOnClient(mc -> mc.options.guiScale().get());
            for (int gui : new int[]{2, 3, 4}) {
                context.runOnClient(mc -> {
                    mc.options.guiScale().set(gui);
                    mc.resizeGui();
                    screen(mc).show(Reference.id("plz05"));
                });
                context.waitTicks(3);
                shot(context, "vehicle_assembling_gui" + gui);
            }
            context.runOnClient(mc -> {
                mc.options.guiScale().set(scale);
                mc.resizeGui();
            });
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
