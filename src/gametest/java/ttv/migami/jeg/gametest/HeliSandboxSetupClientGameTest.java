package ttv.migami.jeg.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Not a check: builds a play-test world for the dev client. A flat creative world (cheats on) at noon with an AH-6 and an
 * MI-28 side by side, their holds full of every ammunition they use and their energy full, saved into
 * {@code run/saves/HeliTest}. Excluded
 * from normal runs; build it with {@code ./gradlew runClientGameTest -PgameTests=HeliSandboxSetup}, then play it with
 * {@code ./gradlew runClient --args="--quickPlaySingleplayer HeliTest"}.
 */
public class HeliSandboxSetupClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final String WORLD = "HeliTest";
    private static final String[][] AMMO = {
            {"rifle_ammo", "64"}, {"rifle_ammo", "64"}, {"rifle_ammo", "64"}, {"rifle_ammo", "64"},
            {"small_rocket", "64"}, {"small_rocket", "64"},
            {"medium_anti_ground_missile", "64"}, {"medium_anti_air_missile", "64"}, {"large_anti_ground_missile", "64"},
            {"flare", "64"}
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        Path saved;
        // Creative with cheats on: whoever opens it (the dev client logs in as a different player) starts in creative.
        try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(settings -> {
            settings.setName(WORLD);
            settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            settings.setAllowCommands(true);
        }).create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("defaultgamemode creative");
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("weather clear 1000000");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule advance_weather false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("setworldspawn 0 -60 0");
            server.runCommand("tp @a 0 -60 0 0 0");
            server.runCommand("summon jeg:ah6 -9 -60 14 {Rotation:[0f,0f]}");
            server.runCommand("summon jeg:mi28 11 -60 16 {Rotation:[0f,0f]}");
            context.waitTicks(10);
            String stocked = server.computeOnServer(minecraftServer -> {
                StringBuilder out = new StringBuilder();
                for (Entity entity : minecraftServer.overworld().getAllEntities()) {
                    if (entity instanceof VehicleEntity vehicle) {
                        var hold = vehicle.vehicleInventory();
                        hold.clearContent();
                        for (int i = 0; i < AMMO.length && i < hold.getContainerSize(); i++) {
                            hold.setItem(i, new ItemStack(BuiltInRegistries.ITEM.getValue(Reference.id(AMMO[i][0])), Integer.parseInt(AMMO[i][1])));
                        }
                        vehicle.addEnergy(vehicle.maxVehicleEnergy());
                        out.append(vehicle.vehicleDataId().getPath()).append(" slots=").append(hold.getContainerSize())
                                .append(" energy=").append(vehicle.vehicleEnergy()).append('/').append(vehicle.maxVehicleEnergy()).append("; ");
                    }
                }
                return out.toString();
            });
            LOGGER.info("[heli-sandbox] stocked: {}", stocked);
            if (!stocked.contains("ah6") || !stocked.contains("mi28")) {
                throw new AssertionError("Both helicopters should be in the world: " + stocked);
            }
            context.waitTicks(5);
            context.takeScreenshot("heli_sandbox");
            saved = server.computeOnServer(minecraftServer -> minecraftServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
        }
        // Copied only once the world has closed: entities are written out as it shuts down, not by save-all.
        Path target = Path.of("").toAbsolutePath().getParent().getParent().getParent().resolve("run").resolve("saves").resolve(WORLD);
        copyWorld(saved, target);
        long entityBytes = sizeOf(target.resolve("dimensions/minecraft/overworld/entities"));
        LOGGER.info("[heli-sandbox] saved {} to {} (entity data {} bytes)", saved, target, entityBytes);
        if (entityBytes == 0) {
            throw new AssertionError("The helicopters were not saved with the world");
        }
    }

    private static long sizeOf(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).mapToLong(file -> file.toFile().length()).sum();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static void copyWorld(Path from, Path to) {
        try {
            if (Files.exists(to)) {
                try (Stream<Path> old = Files.walk(to)) {
                    old.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
                }
            }
            try (Stream<Path> files = Files.walk(from)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (file.getFileName().toString().equals("session.lock")) {
                        continue;
                    }
                    Path dest = to.resolve(from.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            // Name it in the world list.
            LOGGER.info("[heli-sandbox] copied world, level.dat present: {}", Files.exists(to.resolve("level.dat")));
        } catch (IOException e) {
            throw new AssertionError("Could not copy the play-test world", e);
        }
    }
}
