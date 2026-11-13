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
 * Not a check: builds a play-test world for the A-10 in the dev client. A flat creative world (cheats on) at noon with an
 * A-10 at the end of a long runway, its hold full of rifle ammo, missiles and flares, every weapon loaded and its energy
 * full; and targets down-range (a BMP-2, a LAV-150, a truck and a group of iron golems). Saved into
 * {@code run/saves/A10Test}. Excluded from normal runs; build it with
 * {@code ./gradlew runClientGameTest -PgameTests=A10SandboxSetup}, then play it with
 * {@code ./gradlew runClient --args="--quickPlaySingleplayer A10Test"}.
 */
public class A10SandboxSetupClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final String WORLD = "A10Test";
    private static final String[][] AMMO = {
            {"rifle_ammo", "64"}, {"rifle_ammo", "64"}, {"rifle_ammo", "64"}, {"rifle_ammo", "64"}, {"rifle_ammo", "64"},
            {"rifle_ammo", "64"}, {"rifle_ammo", "64"}, {"rifle_ammo", "64"}, {"rifle_ammo", "64"}, {"rifle_ammo", "64"},
            {"medium_anti_ground_missile", "16"}, {"large_anti_ground_missile", "16"}, {"flare", "64"}
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
            server.runCommand("setworldspawn 0 -60 -6");
            server.runCommand("tp @a 0 -60 -6 0 0");
            // The jet faces south (+Z) down a flat runway; the targets are about 300 blocks out.
            server.runCommand("summon jeg:a10 0 -60 4 {Rotation:[0f,0f]}");
            server.runCommand("forceload add -16 -16 16 360");
            server.runCommand("summon jeg:bmp2 -12 -60 300 {Rotation:[180f,0f]}");
            server.runCommand("summon jeg:lav150 10 -60 310 {Rotation:[160f,0f]}");
            server.runCommand("summon jeg:truck -2 -60 325 {Rotation:[90f,0f]}");
            for (int i = 0; i < 6; i++) {
                server.runCommand(String.format("summon minecraft:iron_golem %d -60 %d {NoAI:1b,PersistenceRequired:1b}", -6 + i * 2, 290 + (i % 2) * 2));
            }
            context.waitTicks(20);
            String stocked = server.computeOnServer(minecraftServer -> {
                StringBuilder out = new StringBuilder();
                for (Entity entity : minecraftServer.overworld().getAllEntities()) {
                    if (entity instanceof VehicleEntity vehicle && "a10".equals(vehicle.vehicleDataId().getPath())) {
                        var hold = vehicle.vehicleInventory();
                        hold.clearContent();
                        for (int i = 0; i < AMMO.length && i < hold.getContainerSize(); i++) {
                            hold.setItem(i, new ItemStack(BuiltInRegistries.ITEM.getValue(Reference.id(AMMO[i][0])), Integer.parseInt(AMMO[i][1])));
                        }
                        vehicle.addEnergy(vehicle.maxVehicleEnergy());
                        vehicle.reloadAiVehicleWeapons();
                        out.append("a10 loaded gun=").append(vehicle.vehicleWeaponSlotAmmo(0))
                                .append(" agm65d=").append(vehicle.vehicleWeaponSlotAmmo(1))
                                .append(" agm65g=").append(vehicle.vehicleWeaponSlotAmmo(2))
                                .append(" energy=").append(vehicle.vehicleEnergy()).append('/').append(vehicle.maxVehicleEnergy()).append("; ");
                    } else if (entity instanceof VehicleEntity vehicle) {
                        out.append(vehicle.vehicleDataId().getPath()).append("; ");
                    }
                }
                return out.toString();
            });
            LOGGER.info("[a10-sandbox] stocked: {}", stocked);
            if (!stocked.contains("a10 loaded gun=300")) {
                throw new AssertionError("The A-10 should be in the world with its gun loaded: " + stocked);
            }
            context.waitTicks(5);
            context.takeScreenshot("a10_sandbox");
            saved = server.computeOnServer(minecraftServer -> minecraftServer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
        }
        // Copied only once the world has closed: entities are written out as it shuts down, not by save-all.
        Path target = Path.of("").toAbsolutePath().getParent().getParent().getParent().resolve("run").resolve("saves").resolve(WORLD);
        copyWorld(saved, target);
        long entityBytes = sizeOf(target.resolve("dimensions/minecraft/overworld/entities"));
        LOGGER.info("[a10-sandbox] saved {} to {} (entity data {} bytes)", saved, target, entityBytes);
        if (entityBytes == 0) {
            throw new AssertionError("The A-10 was not saved with the world");
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
