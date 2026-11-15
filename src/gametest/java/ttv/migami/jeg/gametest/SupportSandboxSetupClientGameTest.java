package ttv.migami.jeg.gametest;

import java.nio.file.Path;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.support.SupportModules;

/**
 * Not a check: builds the SupportTest play-test world for the FSV-6 in the pack client. A flat survival world (cheats
 * on, mobs off) at noon with the day running (the bunks need night), and:
 * <ul>
 *   <li>a charged FSV-6 at the origin facing south, fitted with a crafting table, furnace and chest, a red bed in the
 *   left bunk and a lantern; its hold carries a starter kit (a beacon and 192 iron blocks for the rack, a charging
 *   station, an anvil, more beds, lamps, coal, raw iron, iron ingots, rifle ammunition);</li>
 *   <li>a LAV-150 (out of charge) and a damaged BMP-2 parked 14 blocks south-east, to charge, rearm and repair;</li>
 *   <li>a clear camp spot straight ahead; a hill at x +30 (ground too uneven: try it); stone pillars at x -30
 *   (blocked: try it); a tree line at z 50.</li>
 * </ul>
 * Build: {@code ./gradlew runClientGameTest -PgameTests=SupportSandboxSetup -PwithPack ...}; saved into
 * {@code build/run/packClient/saves/SupportTest}. Play: {@code ./gradlew runPackClient -PwithPack ... --args="--quickPlaySingleplayer SupportTest"}.
 */
public class SupportSandboxSetupClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final String WORLD = "SupportTest";
    private static final int Y = -60;

    @Override
    public void runTest(ClientGameTestContext context) {
        Path saved;
        try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(settings -> {
            settings.setName(WORLD);
            settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
            settings.setAllowCommands(true);
        }).create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            for (String command : new String[]{
                    "defaultgamemode survival", "gamemode survival @a", "time set noon", "weather clear 1000000",
                    "gamerule advance_weather false", "gamerule spawn_mobs false", "gamerule keep_inventory true",
                    "gamerule spawn_radius 0", "difficulty peaceful", "forceload add -48 -16 48 64",
                    "setworldspawn 4 -60 -8", "tp @a 4 -60 -8 0 0"}) {
                server.runOnServer(s -> DozerSandboxSetupClientGameTest.run(s, command));
            }
            context.waitTicks(10);
            server.runOnServer(SupportSandboxSetupClientGameTest::build);
            context.waitTicks(20);
            String placed = server.computeOnServer(s -> {
                int trucks = 0;
                for (var entity : s.overworld().getAllEntities()) if (entity instanceof SupportVehicleEntity) trucks++;
                return trucks + " support vehicle(s)";
            });
            LOGGER.warn("[support-sandbox] {}", placed);
            if (placed.startsWith("0")) throw new AssertionError("The FSV-6 was not placed");
            server.runOnServer(s -> DozerSandboxSetupClientGameTest.run(s, "tp @a 9 -58 -10 30 12"));
            context.waitTicks(10);
            context.takeScreenshot("support_sandbox");
            saved = server.computeOnServer(s -> s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
        }
        Path target = Path.of("").toAbsolutePath().getParent().resolve("packClient").resolve("saves").resolve(WORLD);
        DozerSandboxSetupClientGameTest.copyWorld(saved, target);
        long entityBytes = DozerSandboxSetupClientGameTest.sizeOf(target.resolve("dimensions/minecraft/overworld/entities"));
        LOGGER.warn("[support-sandbox] saved {} to {} (entity data {} bytes)", saved, target, entityBytes);
        if (entityBytes == 0) throw new AssertionError("The vehicles were not saved with the world");
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        // A hill (uneven ground) at x +30 and stone pillars (blocked) at x -30.
        for (int x = 24; x <= 38; x++) {
            for (int z = -4; z <= 10; z++) {
                int height = Math.max(0, 3 - (Math.abs(x - 31) + Math.abs(z - 3)) / 3);
                for (int y = 0; y < height; y++) DozerSandboxSetupClientGameTest.set(level, x, Y + y, z, Blocks.DIRT.defaultBlockState());
                if (height > 0) DozerSandboxSetupClientGameTest.set(level, x, Y + height, z, Blocks.GRASS_BLOCK.defaultBlockState());
            }
        }
        for (int x = -34; x <= -26; x += 4) {
            for (int y = 0; y < 3; y++) DozerSandboxSetupClientGameTest.set(level, x, Y + y, 2, Blocks.COBBLESTONE.defaultBlockState());
        }
        for (int x = -20; x <= 20; x += 5) {
            DozerSandboxSetupClientGameTest.run(server, "place feature minecraft:oak " + x + " " + Y + " 50");
        }
        SupportVehicleEntity truck = ModEntities.TRUCK.get().create(level, EntitySpawnReason.COMMAND);
        truck.snapTo(0.5, Y, 0.5, 0, 0);
        truck.addEnergy(truck.maxVehicleEnergy());
        level.addFreshEntity(truck);
        var fit = truck.modules().fittings;
        fit.setItem(0, new ItemStack(Items.CRAFTING_TABLE));
        fit.setItem(1, new ItemStack(Items.FURNACE));
        fit.setItem(2, new ItemStack(Items.CHEST));
        fit.setItem(SupportModules.BUNK_LEFT, new ItemStack(Items.RED_BED));
        fit.setItem(SupportModules.LAMP, new ItemStack(Items.LANTERN));
        fit.setChanged();
        ItemStack[] kit = {new ItemStack(Items.BEACON), new ItemStack(Items.IRON_BLOCK, 64), new ItemStack(Items.IRON_BLOCK, 64),
                new ItemStack(Items.IRON_BLOCK, 64), new ItemStack(item("jeg:vehicle_charging_station")), new ItemStack(Items.ANVIL),
                new ItemStack(Items.BLUE_BED), new ItemStack(Items.SEA_LANTERN), new ItemStack(Items.BARREL), new ItemStack(Items.BREWING_STAND),
                new ItemStack(Items.SMOKER), new ItemStack(Items.ENDER_CHEST), new ItemStack(Items.JUKEBOX), new ItemStack(Items.MUSIC_DISC_CAT),
                new ItemStack(Items.COAL, 64), new ItemStack(Items.RAW_IRON, 32), new ItemStack(Items.IRON_INGOT, 64),
                new ItemStack(item("jeg:rifle_ammo"), 64), new ItemStack(item("jeg:small_shell"), 64), new ItemStack(Items.EMERALD, 8)};
        for (int i = 0; i < kit.length; i++) truck.vehicleInventory().setItem(i, kit[i]);
        VehicleEntity lav = (VehicleEntity) ModEntities.LAV150.get().create(level, EntitySpawnReason.COMMAND);
        lav.snapTo(14, Y, 14, 0, 0);
        level.addFreshEntity(lav);
        lav.consumeEnergy(lav.vehicleEnergy());
        VehicleEntity bmp = (VehicleEntity) ModEntities.BMP2.get().create(level, EntitySpawnReason.COMMAND);
        bmp.snapTo(16, Y, 24, 0, 0);
        level.addFreshEntity(bmp);
        bmp.hurtServer(level, level.damageSources().generic(), 120);
    }

    private static net.minecraft.world.item.Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }
}
