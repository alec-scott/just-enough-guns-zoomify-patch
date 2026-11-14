package ttv.migami.jeg.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Random;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;

/**
 * Not a check: builds the DozerTest play-test world for the BD-40 in the pack client. A flat survival world (cheats on,
 * since FallingTree does not fell trees in creative) at noon, with a fully charged bulldozer facing south (+Z) down a
 * clearing course:
 * <ul>
 *   <li>z 8-20: meadow of grass and flowers</li>
 *   <li>z 26-46: mixed forest (oak, birch, spruce, fancy oak)</li>
 *   <li>z 54-84: rolling soil-and-stone hills, 4 to 8 high</li>
 *   <li>z 94-104: a 10-high ridge, soil over stone and deepslate with ore seams</li>
 *   <li>z 112: obsidian (stops the blade), a chest (stops the blade), iron blocks (cleared)</li>
 *   <li>x -40: giant trees (dark oak, mega spruce, mega jungle); x +30: a log cabin that must not fall whole</li>
 * </ul>
 * Build: {@code ./gradlew runClientGameTest -PgameTests=DozerSandboxSetup -PwithPack ...}; it is saved into
 * {@code build/run/packClient/saves/DozerTest}. Play: {@code ./gradlew runPackClient -PwithPack ... --args="--quickPlaySingleplayer DozerTest"}.
 */
public class DozerSandboxSetupClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final String WORLD = "DozerTest";
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
                    "forceload add -64 -16 48 128", "setworldspawn 5 -60 -3", "tp @a 5 -60 -3 0 0"}) {
                server.runOnServer(s -> run(s, command));
            }
            context.waitTicks(10);
            server.runOnServer(DozerSandboxSetupClientGameTest::buildCourse);
            context.waitTicks(10);
            server.runOnServer(DozerSandboxSetupClientGameTest::plantTrees);
            server.runOnServer(s -> run(s, "summon jeg:bulldozer 0 -60 0 {Rotation:[0f,0f]}"));
            context.waitTicks(20);
            String dozer = server.computeOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity instanceof BulldozerEntity d) {
                        d.addEnergy(d.maxVehicleEnergy());
                        return "energy " + d.vehicleEnergy() + "/" + d.maxVehicleEnergy();
                    }
                }
                return "";
            });
            int logs = server.computeOnServer(s -> {
                int n = 0;
                for (BlockPos pos : BlockPos.betweenClosed(-60, Y, -10, 48, Y + 40, 128)) {
                    if (s.overworld().getBlockState(pos).is(net.minecraft.tags.BlockTags.LOGS)) n++;
                }
                return n;
            });
            LOGGER.warn("[dozer-sandbox] bulldozer {}; {} log blocks on the course", dozer, logs);
            if (dozer.isEmpty()) throw new AssertionError("The bulldozer was not placed");
            if (logs < 100) throw new AssertionError("Too few trees grew: " + logs + " logs");
            server.runOnServer(s -> run(s, "tp @a 6 -59 -5 25 12"));
            context.waitTicks(10);
            context.takeScreenshot("dozer_sandbox");
            saved = server.computeOnServer(s -> s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
        }
        // Copied once the world has closed (entities are written as it shuts down). The game test runs in
        // build/run/clientGameTest; the pack client in build/run/packClient.
        Path target = Path.of("").toAbsolutePath().getParent().resolve("packClient").resolve("saves").resolve(WORLD);
        copyWorld(saved, target);
        long entityBytes = sizeOf(target.resolve("dimensions/minecraft/overworld/entities"));
        LOGGER.warn("[dozer-sandbox] saved {} to {} (entity data {} bytes)", saved, target, entityBytes);
        if (entityBytes == 0) throw new AssertionError("The bulldozer was not saved with the world");
    }

    static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }

    private static void buildCourse(MinecraftServer server) {
        ServerLevel level = server.overworld();
        Random random = new Random(4040);
        // Meadow: grass with flowers to drive through.
        BlockState[] flowers = {Blocks.POPPY.defaultBlockState(), Blocks.DANDELION.defaultBlockState(), Blocks.CORNFLOWER.defaultBlockState(),
                Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState()};
        for (int x = -16; x <= 16; x++) for (int z = 8; z <= 20; z++) {
            set(level, x, Y, z, random.nextInt(6) == 0 ? flowers[random.nextInt(flowers.length)] : Blocks.SHORT_GRASS.defaultBlockState());
        }
        // Rolling hills: stepped mounds, soil over a stone core.
        mound(level, random, -8, 60, 8, 4);
        mound(level, random, 7, 66, 9, 6);
        mound(level, random, -3, 77, 8, 8);
        // A ten-high ridge across the course: soil over stone with deepslate below and ore seams through it.
        BlockState[] ores = {Blocks.COAL_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(), Blocks.COPPER_ORE.defaultBlockState(),
                Blocks.GOLD_ORE.defaultBlockState(), Blocks.REDSTONE_ORE.defaultBlockState(), Blocks.LAPIS_ORE.defaultBlockState(),
                Blocks.DIAMOND_ORE.defaultBlockState()};
        for (int x = -18; x <= 18; x++) for (int z = 94; z <= 104; z++) for (int y = Y; y < Y + 10; y++) {
            int fromTop = Y + 9 - y;
            BlockState state;
            if (fromTop == 0) state = Blocks.GRASS_BLOCK.defaultBlockState();
            else if (fromTop <= 2) state = Blocks.DIRT.defaultBlockState();
            else if (y <= Y + 1) state = Blocks.DEEPSLATE.defaultBlockState();
            else if (random.nextInt(14) == 0) state = ores[random.nextInt(ores.length)];
            else if (random.nextInt(9) == 0) state = random.nextBoolean() ? Blocks.GRAVEL.defaultBlockState() : Blocks.ANDESITE.defaultBlockState();
            else state = Blocks.STONE.defaultBlockState();
            set(level, x, y, z, state);
        }
        // Hard stops and a valuable wall: obsidian and a chest stop the blade, iron blocks (hardness 5) are cleared.
        for (int y = Y; y < Y + 2; y++) {
            for (int x = -14; x <= -6; x++) set(level, x, y, 112, Blocks.OBSIDIAN.defaultBlockState());
            for (int x = 4; x <= 12; x++) set(level, x, y, 112, Blocks.IRON_BLOCK.defaultBlockState());
        }
        set(level, -1, Y, 112, Blocks.CHEST.defaultBlockState());
        // A log cabin: logs without leaves are cut where the blade meets them, never felled whole.
        for (int x = 26; x <= 34; x++) for (int z = 20; z <= 28; z++) for (int y = Y; y < Y + 4; y++) {
            boolean wall = x == 26 || x == 34 || z == 20 || z == 28;
            boolean corner = (x == 26 || x == 34) && (z == 20 || z == 28);
            if (corner) set(level, x, y, z, Blocks.OAK_LOG.defaultBlockState());
            else if (wall) set(level, x, y, z, (y == Y + 1 || y == Y + 2) && (x == 30 || z == 24) ? Blocks.GLASS_PANE.defaultBlockState() : Blocks.SPRUCE_LOG.defaultBlockState());
        }
        for (int x = 25; x <= 35; x++) for (int z = 19; z <= 29; z++) set(level, x, Y + 4, z, Blocks.SPRUCE_PLANKS.defaultBlockState());
    }

    private static void mound(ServerLevel level, Random random, int cx, int cz, int radius, int height) {
        for (int h = 0; h < height; h++) {
            double r = radius * (1 - h / (double) height) + .5;
            for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
                double d = Math.hypot(x - cx, z - cz) + random.nextDouble() * .6;
                if (d > r) continue;
                boolean top = h == height - 1 || Math.hypot(x - cx, z - cz) > radius * (1 - (h + 1) / (double) height) + .5;
                BlockState state = top ? Blocks.GRASS_BLOCK.defaultBlockState()
                        : d < r - 2.5 && h < height - 2 ? (random.nextInt(5) == 0 ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.STONE.defaultBlockState())
                        : random.nextInt(7) == 0 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                set(level, x, Y + h, z, state);
            }
        }
    }

    private static void plantTrees(MinecraftServer server) {
        String[] small = {"oak", "birch", "spruce", "fancy_oak", "birch", "oak", "spruce"};
        Random random = new Random(1234);
        int i = 0;
        for (int z = 27; z <= 45; z += 6) {
            for (int x = -13; x <= 13; x += 6) {
                int jx = x + random.nextInt(3) - 1, jz = z + random.nextInt(3) - 1;
                run(server, "place feature minecraft:" + small[i++ % small.length] + " " + jx + " " + Y + " " + jz);
            }
        }
        // Giant trees: beyond FallingTree's 100-log limit the dozer's own feller takes them down.
        run(server, "place feature minecraft:dark_oak -38 -60 22");
        run(server, "place feature minecraft:dark_oak -44 -60 30");
        run(server, "place feature minecraft:mega_spruce -40 -60 42");
        run(server, "place feature minecraft:mega_jungle_tree -40 -60 58");
    }

    static long sizeOf(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).mapToLong(file -> file.toFile().length()).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    static void copyWorld(Path from, Path to) {
        try {
            if (Files.exists(to)) {
                try (Stream<Path> old = Files.walk(to)) {
                    old.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
                }
            }
            try (Stream<Path> files = Files.walk(from)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (file.getFileName().toString().equals("session.lock")) continue;
                    Path dest = to.resolve(from.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException e) {
            throw new AssertionError("Could not copy the play-test world", e);
        }
    }
}
