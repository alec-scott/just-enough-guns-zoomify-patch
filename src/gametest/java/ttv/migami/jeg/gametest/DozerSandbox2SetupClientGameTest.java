package ttv.migami.jeg.gametest;

import static ttv.migami.jeg.gametest.DozerSandboxSetupClientGameTest.copyWorld;
import static ttv.migami.jeg.gametest.DozerSandboxSetupClientGameTest.run;
import static ttv.migami.jeg.gametest.DozerSandboxSetupClientGameTest.set;
import static ttv.migami.jeg.gametest.DozerSandboxSetupClientGameTest.sizeOf;

import java.nio.file.Path;
import java.util.Random;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;
import ttv.migami.jeg.vehicle.terrain.BulldozerTerrainWork;

/**
 * Not a check: builds DozerTest2, the second play-test world for the BD-40, aimed at what came in its second round:
 * levelling natural-looking ground, cutting and paving a road, and hearing the machine from far off. A flat survival
 * world (cheats on; FallingTree does not fell in creative) at noon with:
 * <ul>
 *   <li>the operator's dozer at the origin facing south (+Z), charged, 256 stone bricks in its ripper bay (the hold's
 *       last row), and a supply chest of paving blocks beside it</li>
 *   <li>an 80 × 80 field of rolling hills (z 12-92), soil over stone with ore, trees on top</li>
 *   <li>a road marked with fence posts down the middle of the field, crossing a creek at z 52</li>
 *   <li>a second dozer idling 400 blocks west (an armour stand at the controls), with listening posts 150 and 300 blocks
 *       from it, to hear the machine fade with distance</li>
 * </ul>
 * Build: {@code ./gradlew runClientGameTest -PgameTests=DozerSandbox2Setup -PwithPack ...}; saved into
 * {@code build/run/packClient/saves/DozerTest2}. Play: {@code ./gradlew runPackClient -PwithPack ... --args="--quickPlaySingleplayer DozerTest2"}.
 */
public class DozerSandbox2SetupClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final String WORLD = "DozerTest2";
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
                    "gamerule advance_time false", "gamerule advance_weather false", "gamerule spawn_mobs false",
                    "gamerule keep_inventory true", "gamerule spawn_radius 0", "difficulty peaceful",
                    "forceload add -48 -16 48 112", "forceload add -416 -16 -384 16",
                    "setworldspawn 4 -60 -4", "tp @a 4 -60 -4 20 10"}) {
                server.runOnServer(s -> run(s, command));
            }
            context.waitTicks(10);
            server.runOnServer(DozerSandbox2SetupClientGameTest::buildField);
            context.waitTicks(10);
            server.runOnServer(DozerSandbox2SetupClientGameTest::plantTrees);
            server.runOnServer(DozerSandbox2SetupClientGameTest::placeMachines);
            context.waitTicks(30);
            String machines = server.computeOnServer(s -> {
                StringBuilder out = new StringBuilder();
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity instanceof BulldozerEntity d) {
                        out.append(String.format("dozer at %.0f,%.0f powered=%s paving=%s; ", d.getX(), d.getZ(), d.powered(),
                                BulldozerTerrainWork.pavingCount(d)));
                    }
                }
                return out.toString();
            });
            LOGGER.warn("[dozer-sandbox-2] {}", machines);
            if (!machines.contains("dozer at 0,0") || !machines.contains("dozer at -400,0 powered=true")) {
                throw new AssertionError("Both dozers should be placed, the far one idling: " + machines);
            }
            context.waitTicks(10);
            context.takeScreenshot("dozer_sandbox_2");
            saved = server.computeOnServer(s -> s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
        }
        Path target = Path.of("").toAbsolutePath().getParent().resolve("packClient").resolve("saves").resolve(WORLD);
        copyWorld(saved, target);
        long entityBytes = sizeOf(target.resolve("dimensions/minecraft/overworld/entities"));
        LOGGER.warn("[dozer-sandbox-2] saved {} to {} (entity data {} bytes)", saved, target, entityBytes);
        if (entityBytes == 0) throw new AssertionError("The dozers were not saved with the world");
    }

    /** Height of the rolling field above the flat ground at a column (0 to 9). */
    private static int height(int x, int z) {
        double h = 3.2 + 2.4 * Math.sin(x / 7.0) + 2.0 * Math.cos(z / 9.0) + 1.6 * Math.sin((x + z) / 11.0) + 1.1 * Math.cos((x - 2 * z) / 13.0);
        return (int) Math.max(0, Math.min(9, Math.round(h)));
    }

    private static void buildField(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Random random = new Random(26);
        BlockState[] ores = {Blocks.COAL_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(), Blocks.COPPER_ORE.defaultBlockState(),
                Blocks.GOLD_ORE.defaultBlockState(), Blocks.REDSTONE_ORE.defaultBlockState()};
        for (int x = -40; x <= 40; x++) {
            for (int z = 12; z <= 92; z++) {
                int h = height(x, z);
                if (z >= 51 && z <= 53) continue;
                for (int y = 0; y < h; y++) {
                    int fromTop = h - 1 - y;
                    BlockState state = fromTop == 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
                            : fromTop <= 2 ? (random.nextInt(8) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState())
                            : random.nextInt(16) == 0 ? ores[random.nextInt(ores.length)]
                            : random.nextInt(10) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    set(level, x, Y + y, z, state);
                }
                if (h == 0 && random.nextInt(4) == 0) set(level, x, Y, z, Blocks.SHORT_GRASS.defaultBlockState());
            }
        }
        // A creek across the field: two blocks deep in the ground, for the ripper to pave over.
        for (int x = -40; x <= 40; x++) for (int z = 51; z <= 53; z++) {
            set(level, x, Y - 1, z, Blocks.WATER.defaultBlockState());
            set(level, x, Y - 2, z, Blocks.WATER.defaultBlockState());
        }
        // The road to cut and pave: fence posts either side of a 4-wide lane down the middle of the field.
        for (int z = 12; z <= 92; z += 8) {
            for (int x : new int[]{-3, 2}) {
                if (z >= 50 && z <= 54) continue;
                int top = Y + height(x, z);
                set(level, x, top, z, Blocks.OAK_FENCE.defaultBlockState());
                set(level, x, top + 1, z, Blocks.OAK_FENCE.defaultBlockState());
                set(level, x, top + 2, z, Blocks.YELLOW_WOOL.defaultBlockState());
            }
        }
        // The far end of the road: a small hut to reach.
        for (int x = -3; x <= 3; x++) for (int z = 96; z <= 102; z++) for (int y = 0; y < 4; y++) {
            boolean wall = x == -3 || x == 3 || z == 96 || z == 102;
            if (wall && !(z == 96 && x >= -1 && x <= 1 && y < 3)) set(level, x, Y + y, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
        }
        for (int x = -4; x <= 4; x++) for (int z = 95; z <= 103; z++) set(level, x, Y + 4, z, Blocks.SPRUCE_SLAB.defaultBlockState());
        // Listening posts on the way to the far dozer: pillars 150 and 300 blocks from it (x -250 and x -100).
        for (int x : new int[]{-250, -100}) {
            for (int y = 0; y < 6; y++) set(level, x, Y + y, 0, Blocks.STONE_BRICKS.defaultBlockState());
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(level, x + dx, Y + 6, dz, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
            set(level, x, Y + 7, 0, Blocks.LANTERN.defaultBlockState());
        }
        // Supply chest of paving blocks by the spawn.
        set(level, 6, Y, -3, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(new BlockPos(6, Y, -3)) instanceof ChestBlockEntity chest) {
            var supplies = new net.minecraft.world.item.Item[]{Items.STONE_BRICKS, Items.STONE_BRICKS, Items.STONE_BRICKS, Items.STONE_BRICKS,
                    Items.SMOOTH_STONE, Items.SMOOTH_STONE, Items.POLISHED_ANDESITE, Items.POLISHED_ANDESITE, Items.GRAVEL, Items.GRAVEL,
                    Items.COBBLESTONE, Items.COBBLESTONE, Items.OAK_PLANKS, Items.DEEPSLATE_TILES, Items.DEEPSLATE_TILES, Items.BRICKS};
            for (int i = 0; i < supplies.length; i++) chest.setItem(i, new ItemStack(supplies[i], 64));
        }
    }

    private static void plantTrees(MinecraftServer server) {
        String[] kinds = {"oak", "birch", "oak", "spruce", "fancy_oak"};
        Random random = new Random(77);
        for (int i = 0; i < 26; i++) {
            int x = random.nextInt(78) - 39, z = 14 + random.nextInt(76);
            if (Math.abs(x) <= 4 || z >= 49 && z <= 55) {
                continue;
            }
            run(server, "place feature minecraft:" + kinds[random.nextInt(kinds.length)] + " " + x + " " + (Y + height(x, z)) + " " + z);
        }
    }

    private static void placeMachines(MinecraftServer server) {
        ServerLevel level = server.overworld();
        BulldozerEntity mine = ModEntities.BULLDOZER.get().create(level, EntitySpawnReason.COMMAND);
        mine.snapTo(0, Y, 0, 0, 0);
        mine.addEnergy(mine.maxVehicleEnergy());
        for (int i = 0; i < 4; i++) mine.vehicleInventory().setItem(BulldozerTerrainWork.pavingStart(mine) + i, new ItemStack(Items.STONE_BRICKS, 64));
        level.addFreshEntity(mine);
        // The far dozer idles for good: an armour stand holds the controls, so its engine never shuts down.
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) set(level, -400 + dx, Y - 1, dz, Blocks.GRAVEL.defaultBlockState());
        BulldozerEntity far = ModEntities.BULLDOZER.get().create(level, EntitySpawnReason.COMMAND);
        far.snapTo(-400, Y, 0, 90, 0);
        far.addEnergy(far.maxVehicleEnergy());
        level.addFreshEntity(far);
        var stand = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
        stand.snapTo(far.getX(), far.getY(), far.getZ(), 90, 0);
        stand.setInvisible(true);
        level.addFreshEntity(stand);
        stand.startRiding(far, true, true);
    }
}
