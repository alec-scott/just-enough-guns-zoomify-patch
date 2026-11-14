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
 * Not a check: builds a play-test world for the pack client ({@code runPackClient}). A flat creative world (cheats on)
 * at noon with an A-10 at the head of a long runway and an AH-6 and an Mi-28 either side of it, each with its hold full
 * of every round, rocket, missile and flare it uses, its weapons loaded and its energy full; targets down-range (a BMP-2,
 * a LAV-150, a truck and iron golems at about 120 and 300 blocks). Saved into {@code build/run/packClient/saves/AircraftTest}.
 * Excluded from normal runs; build it inside the pack with
 * {@code ./gradlew runClientGameTest -PgameTests=AircraftSandboxSetup -PwithPack ...}, then play it with
 * {@code ./gradlew runPackClient -PwithPack ... --args="--quickPlaySingleplayer AircraftTest"}.
 */
public class AircraftSandboxSetupClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final String WORLD = "AircraftTest";
    /** Everything any of the three fires, a full hold's worth. */
    private static final String[][] AMMO = {
            {"rifle_ammo", "64"}, {"rifle_ammo", "64"}, {"rifle_ammo", "64"}, {"rifle_ammo", "64"}, {"rifle_ammo", "64"},
            {"rifle_ammo", "64"}, {"rifle_ammo", "64"}, {"rifle_ammo", "64"},
            {"small_rocket", "64"}, {"small_rocket", "64"},
            {"medium_anti_ground_missile", "64"}, {"medium_anti_air_missile", "64"}, {"large_anti_ground_missile", "64"},
            {"flare", "64"}, {"flare", "64"}
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
            server.runCommand("setworldspawn 0 -60 -8");
            server.runCommand("tp @a 0 -60 -8 0 0");
            // All three face south (+Z) down the runway, the helicopters on either side of the jet.
            server.runCommand("summon jeg:a10 0 -60 4 {Rotation:[0f,0f]}");
            server.runCommand("summon jeg:ah6 -16 -60 6 {Rotation:[0f,0f]}");
            server.runCommand("summon jeg:mi28 18 -60 8 {Rotation:[0f,0f]}");
            server.runCommand("forceload add -32 -16 32 360");
            server.runCommand("summon jeg:truck 6 -60 120 {Rotation:[90f,0f]}");
            server.runCommand("summon jeg:bmp2 -12 -60 300 {Rotation:[180f,0f]}");
            server.runCommand("summon jeg:lav150 10 -60 310 {Rotation:[160f,0f]}");
            server.runCommand("summon jeg:truck -2 -60 325 {Rotation:[90f,0f]}");
            for (int i = 0; i < 6; i++) {
                server.runCommand(String.format("summon minecraft:iron_golem %d -60 %d {NoAI:1b,PersistenceRequired:1b}", -6 + i * 2, 125 + (i % 2) * 2));
                server.runCommand(String.format("summon minecraft:iron_golem %d -60 %d {NoAI:1b,PersistenceRequired:1b}", -6 + i * 2, 290 + (i % 2) * 2));
            }
            context.waitTicks(20);
            String stocked = server.computeOnServer(minecraftServer -> {
                StringBuilder out = new StringBuilder();
                for (Entity entity : minecraftServer.overworld().getAllEntities()) {
                    if (!(entity instanceof VehicleEntity vehicle)) {
                        continue;
                    }
                    String type = vehicle.vehicleDataId().getPath();
                    if (!type.equals("a10") && !type.equals("ah6") && !type.equals("mi28")) {
                        continue;
                    }
                    var hold = vehicle.vehicleInventory();
                    hold.clearContent();
                    for (int i = 0; i < AMMO.length && i < hold.getContainerSize(); i++) {
                        hold.setItem(i, new ItemStack(BuiltInRegistries.ITEM.getValue(Reference.id(AMMO[i][0])), Integer.parseInt(AMMO[i][1])));
                    }
                    vehicle.addEnergy(vehicle.maxVehicleEnergy());
                    vehicle.reloadAiVehicleWeapons();
                    out.append(type).append(" loaded");
                    int weapons = vehicle.vehicleData().defaults().weapons().size();
                    for (int slot = 0; slot < weapons; slot++) {
                        out.append(' ').append(vehicle.vehicleData().defaults().weapons().get(slot).weaponId().getPath())
                                .append('=').append(vehicle.vehicleWeaponSlotAmmo(slot));
                    }
                    out.append(" energy=").append(vehicle.vehicleEnergy()).append('/').append(vehicle.maxVehicleEnergy()).append("; ");
                }
                return out.toString();
            });
            LOGGER.warn("[aircraft-sandbox] stocked: {}", stocked);
            for (String type : new String[]{"a10", "ah6", "mi28"}) {
                if (!stocked.contains(type + " loaded")) {
                    throw new AssertionError("The " + type + " should be in the world, stocked: " + stocked);
                }
            }
            context.waitTicks(5);
            context.takeScreenshot("aircraft_sandbox");
            saved = server.computeOnServer(minecraftServer -> minecraftServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
        }
        // Copied only once the world has closed: entities are written out as it shuts down, not by save-all. The game
        // test runs in build/run/clientGameTest; the pack client in build/run/packClient.
        Path target = Path.of("").toAbsolutePath().getParent().resolve("packClient").resolve("saves").resolve(WORLD);
        copyWorld(saved, target);
        long entityBytes = sizeOf(target.resolve("dimensions/minecraft/overworld/entities"));
        LOGGER.warn("[aircraft-sandbox] saved {} to {} (entity data {} bytes)", saved, target, entityBytes);
        if (entityBytes == 0) {
            throw new AssertionError("The aircraft were not saved with the world");
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
        } catch (IOException e) {
            throw new AssertionError("Could not copy the play-test world", e);
        }
    }
}
