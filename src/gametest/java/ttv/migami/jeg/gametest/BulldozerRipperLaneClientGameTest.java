package ttv.migami.jeg.gametest;

import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;
import ttv.migami.jeg.vehicle.menu.VehicleMenu;
import ttv.migami.jeg.vehicle.terrain.BulldozerTerrainWork;

/**
 * The ripper lays the same columns the blade clears. Driven from a block centre (x 0.5) the 4-block blade spans 5
 * columns, x -2 to 2, and so does the paving; driven along the grid (x 0) both span 4, x -2 to 1, and x 2 stays grass.
 */
public class BulldozerRipperLaneClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private String id;

    @Override public void runTest(ClientGameTestContext context) {
        int[] display = context.computeOnClient(mc -> new int[]{mc.getWindow().getScreenWidth(), mc.getWindow().getScreenHeight(), mc.options.guiScale().get()});
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            // The default 854x480 test window cannot use GUI scale 3, where fractional HUD scaling blurred text.
            context.runOnClient(mc -> {
                mc.getWindow().setWindowed(1280, 900);
                mc.options.guiScale().set(3);
                mc.resizeGui();
            });
            context.waitTicks(5);
            if (context.computeOnClient(mc -> mc.getWindow().getGuiScale()) != 3) failures.append("GUI scale 3 was not exercised\n");
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("time set noon");
            server.runCommand("gamemode creative @a");
            server.runOnServer(s -> {
                var level = s.overworld();
                BulldozerEntity dozer = ModEntities.BULLDOZER.get().create(level, EntitySpawnReason.COMMAND);
                dozer.snapTo(.5, G, 0, 0, 0);
                dozer.addEnergy(dozer.maxVehicleEnergy());
                level.addFreshEntity(dozer);
                s.getPlayerList().getPlayers().getFirst().startRiding(dozer, true, true);
                this.id = dozer.getUUID().toString();
            });
            context.waitTicks(BulldozerEntity.START_TICKS + 20);
            context.runOnClient(mc -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setYRot(0); mc.player.setXRot(20); });
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(22);
            if (!server.computeOnServer(s -> dozer(s).ripperDeployed())) failures.append("R did not lower the ripper\n");
            context.takeScreenshot("bulldozer_ripper_hints_gui3");

            this.lane(context, server, .5, -2, 2, failures, "off_grid");
            this.lane(context, server, 0, -2, 1, failures, "on_grid");

            // The operator's panel and the hold's Ripper bay, for a look: the row above the bay full of counted stacks.
            server.runOnServer(s -> {
                var d = dozer(s);
                int start = BulldozerTerrainWork.pavingStart(d);
                for (int i = 0; i < start; i++) d.vehicleInventory().setItem(i, new ItemStack(i % 2 == 0 ? Items.DIRT : Items.COBBLESTONE, 17 + i));
                d.vehicleInventory().setItem(start, new ItemStack(Items.DIRT, 24));
                for (int i = start + 1; i < start + 5; i++) d.vehicleInventory().setItem(i, new ItemStack(Items.DIRT, 64));
            });
            context.runOnClient(mc -> mc.options.hideGui = false);
            context.waitTicks(10);
            if (context.computeOnClient(mc -> ((BulldozerEntity) mc.player.getVehicle()).pavingCount()) != 280)
                failures.append("the HUD did not receive the material count across all five loaded bay stacks\n");
            context.takeScreenshot("bulldozer_ripper_hud");
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitTicks(10);
            if (!context.computeOnClient(mc -> mc.screen instanceof ttv.migami.jeg.vehicle.client.screen.VehicleScreen)) failures.append("the hold did not open\n");
            context.runOnClient(mc -> {
                if (!(mc.player.containerMenu instanceof VehicleMenu menu)) { failures.append("hold menu missing\n"); return; }
                if (menu.toolStart() != 45 || menu.toolCount() != 9) failures.append("the paving bay is not the hold's complete last row\n");
                var ordinary = new VehicleMenu(0, mc.player.getInventory(), 54);
                if (ordinary.playerInventoryY() != 140 || ordinary.screenHeight() != 222
                        || ordinary.slots.get(ordinary.slots.size() - 1).y != 198)
                    failures.append("the ordinary vehicle inventory layout changed\n");
                int bayBottom = menu.slots.get(menu.toolStart()).y + 16;
                if (menu.playerInventoryY() - bayBottom < 30
                        || menu.slots.get(menu.slots.size() - 1).y + 16 > menu.screenHeight() - 8)
                    failures.append("no room for the bay tab, Inventory header or hotbar\n");
                for (int i = 0; i < menu.toolCount(); i++) {
                    var slot = menu.slots.get(menu.toolStart() + i);
                    if (!(slot instanceof VehicleMenu.ToolSlot) || !slot.mayPlace(new ItemStack(Items.STONE_BRICKS))
                            || slot.mayPlace(new ItemStack(Items.STONE_BRICK_SLAB)) || slot.mayPlace(new ItemStack(Items.STICK)))
                        failures.append("bay slot ").append(i).append(" accepts the wrong materials\n");
                }
            });
            context.getInput().setCursorPos(4, 4);
            context.waitTicks(2);
            context.takeScreenshot("bulldozer_ripper_bay");
            context.runOnClient(mc -> { mc.options.guiScale().set(2); mc.resizeGui(); });
            context.waitTicks(4);
            context.takeScreenshot("bulldozer_ripper_bay_gui2");
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitTicks(6);
            context.takeScreenshot("bulldozer_ripper_hud_gui2");
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
            context.runOnClient(mc -> {
                mc.getWindow().setWindowed(display[0], display[1]);
                mc.options.guiScale().set(display[2]);
                mc.options.hideGui = false;
                mc.resizeGui();
            });
        }
        if (failures.length() > 0) {
            LOG.error("[dozer-ripper-lane] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[dozer-ripper-lane] COMPLETE: both lanes, nine full-block-only bay slots, material count and GUI scales 2/3");
    }

    /** Drives the lowered ripper up fresh grass from x {@code x} and checks it paved exactly columns {@code from}..{@code to}. */
    private void lane(ClientGameTestContext context, TestServerContext server, double x, int from, int to, StringBuilder failures, String name) {
        server.runCommand("fill -8 " + G + " -6 8 " + (G + 15) + " 30 air");
        server.runCommand("fill -8 " + (G - 1) + " -6 8 " + (G - 1) + " 30 grass_block");
        server.runCommand("kill @e[type=item]");
        server.runOnServer(s -> {
            var d = dozer(s);
            d.snapTo(x, G, 0, 0, 0);
            d.setDeltaMovement(Vec3.ZERO);
            d.vehicleInventory().clearContent();
            d.vehicleInventory().setItem(BulldozerTerrainWork.pavingStart(d), new ItemStack(Items.STONE_BRICKS, 64));
            d.vehicleInventory().setItem(BulldozerTerrainWork.pavingStart(d) + 1, new ItemStack(Items.STONE_BRICKS, 64));
        });
        context.waitTicks(6);
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(110);
        context.getInput().releaseKey(options -> options.keyUp);
        context.waitTicks(10);
        context.takeScreenshot("bulldozer_ripper_lane_" + name);
        String rows = server.computeOnServer(s -> {
            StringBuilder out = new StringBuilder();
            for (int z = 0; z <= 7; z++) {
                for (int col = -4; col <= 4; col++)
                    out.append(s.overworld().getBlockState(new BlockPos(col, G - 1, z)).is(Blocks.STONE_BRICKS) ? '#' : '.');
                out.append(' ');
            }
            return out.toString();
        });
        LOG.info("[dozer-ripper-lane] {} (x {}): columns -4..4 per row z 0..7: {}", name, x, rows);
        String expected = ".".repeat(from + 4) + "#".repeat(to - from + 1) + ".".repeat(4 - to);
        for (String row : rows.trim().split(" "))
            if (!row.equals(expected)) {
                failures.append(name).append(": expected every row ").append(expected).append(", got ").append(rows).append('\n');
                break;
            }
    }

    private BulldozerEntity dozer(MinecraftServer server) {
        return (BulldozerEntity) server.overworld().getEntity(UUID.fromString(this.id));
    }
}
