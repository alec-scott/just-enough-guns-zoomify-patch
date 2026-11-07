package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Right-clicking a vehicle with a dye paints it and uses up the dye without climbing in; the paint survives saving
 * and a water bucket washes it off. Screenshots show each helicopter before and after.
 */
public class VehiclePaintClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a resistance infinite 255 true");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 5");
            this.paint(context, server, "ah6", DyeColor.RED, "red_dye");
            this.paint(context, server, "mi28", DyeColor.BLUE, "blue_dye");
        }
    }

    private void paint(ClientGameTestContext context, TestServerContext server, String id, DyeColor dye, String item) {
        server.runCommand("kill @e[type=!minecraft:player]");
        server.runCommand("execute as @p at @s rotated 0 0 run summon jeg:" + id + " ^ ^ ^3.5");
        context.waitTicks(40);
        context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
        String target = context.computeOnClient(mc -> String.valueOf(mc.hitResult != null ? mc.hitResult.getType() : null));
        LOGGER.info("{} crosshair target: {}", id, target);
        context.takeScreenshot("paint_" + id + "_before");

        server.runCommand("item replace entity @p weapon.mainhand with minecraft:" + item + " 3");
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(10);
        String painted = server.computeOnServer(s -> describe(s, id));
        LOGGER.info("{} after {}: {}", id, item, painted);
        check(painted.contains("paint=" + dye.getName()), id + " should be painted " + dye.getName() + ": " + painted);
        check(painted.contains("passengers=0"), id + ": dyeing must not board the vehicle: " + painted);
        int left = context.computeOnClient(mc -> mc.player.getMainHandItem().getCount());
        check(left == 2, id + ": one dye should be used, " + left + " left");
        check(painted.contains("saved=" + dye.getName()), id + ": paint must be saved: " + painted);
        // Step back to see the whole aircraft, then return within reach.
        server.runCommand("tp @p 0 ~ -9 0 0");
        context.waitTicks(15);
        context.takeScreenshot("paint_" + id + "_" + dye.getName());
        server.runCommand("tp @p 0 ~ 0 0 5");
        context.waitTicks(5);

        // A second click with the same colour changes nothing and keeps the dye.
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(5);
        check(context.computeOnClient(mc -> mc.player.getMainHandItem().getCount()) == 2, id + ": repainting the same colour used a dye");

        server.runCommand("item replace entity @p weapon.mainhand with minecraft:water_bucket");
        context.waitTicks(5);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(10);
        String washed = server.computeOnServer(s -> describe(s, id));
        LOGGER.info("{} after washing: {}", id, washed);
        check(washed.contains("paint=none"), id + " should be washed clean: " + washed);
        check(context.computeOnClient(mc -> mc.player.getMainHandItem().is(Items.WATER_BUCKET)), id + ": washing should keep the bucket");
        context.waitTicks(5);
        context.takeScreenshot("paint_" + id + "_washed");
    }

    private static String describe(net.minecraft.server.MinecraftServer server, String id) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof VehicleEntity vehicle && vehicle.vehicleDataId().getPath().equals(id)) {
                    CompoundTag saved = vehicle.saveVehicleContainerState();
                    DyeColor paint = vehicle.paint();
                    return "paint=" + (paint == null ? "none" : paint.getName())
                            + " saved=" + saved.getStringOr("Paint", "none")
                            + " passengers=" + vehicle.getPassengers().size();
                }
            }
        }
        return "missing";
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
