package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Climbing into a parked helicopter and starting it must be quiet apart from the engine: no "airspeed too low" alarm
 * on the ground, and no clanking reload or mechanical sounds during startup. Every sound the client starts is logged.
 */
public class HelicopterBoardingClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        List<String> sounds = Collections.synchronizedList(new ArrayList<>());
        long[] tick = {0};
        context.runOnClient(mc -> mc.getSoundManager().addListener((sound, events, range) ->
                sounds.add(tick[0] + ":" + sound.getIdentifier())));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a resistance infinite 255 true");
            server.runCommand("time set noon");
            server.runCommand("give @p jeg:rifle_ammo 64");
            server.runCommand("give @p jeg:small_rocket 64");
            server.runCommand("give @p jeg:medium_anti_ground_missile 16");
            server.runCommand("give @p jeg:medium_anti_air_missile 16");
            server.runCommand("give @p jeg:large_anti_ground_missile 8");
            this.board(context, server, "ah6", 0, sounds, tick);
            this.board(context, server, "mi28", 300, sounds, tick);
        }
    }

    private void board(ClientGameTestContext context, TestServerContext server, String id, int x, List<String> sounds, long[] tick) {
        server.runCommand("ride @p dismount");
        server.runCommand("tp @p " + x + " -60 0 0 0");
        server.runCommand("execute as @p at @s run summon jeg:" + id + " ~ ~ ~5");
        context.waitTicks(20);
        sounds.clear();
        tick[0] = 0;
        float healthBefore = server.computeOnServer(s -> vehicle(s, id).vehicleHealth());
        server.runCommand("ride @p mount @e[type=jeg:" + id + ",sort=nearest,limit=1]");
        context.waitFor(mc -> mc.player.getVehicle() instanceof VehicleEntity);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        List<String> warnings = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            context.waitTick();
            tick[0]++;
            String warning = context.computeOnClient(mc -> ((VehicleEntity) mc.player.getVehicle()).activeWarningMessageKey());
            if (!warning.isEmpty()) {
                warnings.add(tick[0] + ":" + warning);
            }
        }
        List<String> boarding = List.copyOf(sounds);
        LOGGER.info("[heli-boarding] {} sounds on boarding: {}", id, boarding);
        LOGGER.info("[heli-boarding] {} warnings on boarding: {}", id, warnings);

        sounds.clear();
        int duration = context.computeOnClient(mc -> ((VehicleEntity) mc.player.getVehicle()).helicopterStartupTicks());
        context.getInput().pressKey(options -> options.keyJump);
        for (int i = 0; i < duration + 40; i++) {
            context.waitTick();
            tick[0]++;
            String warning = context.computeOnClient(mc -> ((VehicleEntity) mc.player.getVehicle()).activeWarningMessageKey());
            if (!warning.isEmpty()) {
                warnings.add(tick[0] + ":" + warning);
            }
        }
        List<String> startup = List.copyOf(sounds);
        LOGGER.info("[heli-boarding] {} sounds during startup: {}", id, startup);
        LOGGER.info("[heli-boarding] {} warnings during startup: {}", id, warnings);

        float healthAfter = server.computeOnServer(s -> vehicle(s, id).vehicleHealth());
        LOGGER.info("[heli-boarding] {} health {} -> {}", id, healthBefore, healthAfter);
        check(healthAfter >= healthBefore, id + ": a parked helicopter must not lose health: " + healthBefore + " -> " + healthAfter);
        check(warnings.isEmpty(), id + ": no warnings while parked and starting: " + warnings);
        for (String sound : boarding) {
            check(!isClank(sound), id + ": clanking sound on boarding: " + sound);
        }
        for (String sound : startup) {
            check(!isClank(sound), id + ": clanking sound during startup: " + sound);
        }
    }

    /** Anything mechanical that is not the engine: reload clatter, rotary-gun whirr, lids and latches. */
    private static boolean isClank(String sound) {
        return sound.contains("lid_") || sound.contains("rocket_in") || sound.contains("reload")
                || sound.contains("spin") || sound.contains("cock") || sound.contains("clank") || sound.contains("click");
    }

    private static VehicleEntity vehicle(net.minecraft.server.MinecraftServer server, String id) {
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof VehicleEntity vehicle && vehicle.vehicleDataId().getPath().equals(id)) {
                    return vehicle;
                }
            }
        }
        throw new AssertionError(id + " missing");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
