package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Sneaking (Shift) must not throw the player out of a JEG vehicle, parked or in the air; the Vehicle Dismount key
 * still does. A vanilla boat is the control: sneaking still leaves that.
 */
public class VehicleSneakDismountClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("vehicle sneak-dismount report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a resistance infinite 255 true");
            server.runCommand("time set noon");
            this.sneakInVehicle(context, server, report, failures, "ah6", 0);
            this.sneakInVehicle(context, server, report, failures, "mi28", 300);
            this.sneakInBoat(context, server, report, failures);
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    private void sneakInVehicle(ClientGameTestContext context, TestServerContext server, StringBuilder report,
                                StringBuilder failures, String id, int x) {
        server.runCommand("ride @p dismount");
        server.runCommand("tp @a " + x + " ~ 0 0 0");
        context.waitTicks(10);
        server.runCommand("execute as @p at @s run summon jeg:" + id + " ~ ~ ~4 {Rotation:[0f,0f]}");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=jeg:" + id + ",limit=1,sort=nearest]");
        context.waitFor(mc -> vehicle(mc) != null);
        context.waitTicks(10);

        // Parked: hold sneak for two seconds.
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(40);
        context.getInput().releaseKey(options -> options.keyShift);
        context.waitTicks(5);
        boolean parked = this.serverPlayerInJegVehicle(server);

        // Airborne: climb, then hold sneak again.
        context.getInput().holdKey(options -> options.keyJump);
        int startup = context.computeOnClient(mc -> vehicle(mc) == null ? 0 : vehicle(mc).helicopterStartupTicks());
        context.waitTicks(startup + 60);
        context.getInput().releaseKey(options -> options.keyJump);
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(40);
        context.getInput().releaseKey(options -> options.keyShift);
        context.waitTicks(5);
        boolean airborne = this.serverPlayerInJegVehicle(server);
        double height = context.computeOnClient(mc -> vehicle(mc) == null ? -1.0D : vehicle(mc).getY() - mc.player.level().getMinY());
        context.takeScreenshot("sneak_in_" + id);

        // Land before getting out so the fall does not matter, then use the dismount key.
        context.getInput().holdKey(options -> KeyBindings.VEHICLE_BRAKE_DESCEND);
        context.waitFor(mc -> vehicle(mc) == null || vehicle(mc).onGround(), 800);
        context.getInput().releaseKey(options -> KeyBindings.VEHICLE_BRAKE_DESCEND);
        context.waitTicks(10);
        context.getInput().pressKey(options -> KeyBindings.VEHICLE_DISMOUNT);
        context.waitTicks(20);
        boolean afterDismountKey = this.serverPlayerInJegVehicle(server);

        report.append(String.format("%s: riding after sneak parked=%s, airborne=%s (%.1f blocks up); riding after dismount key=%s%n",
                id, parked, airborne, height, afterDismountKey));
        if (!parked) {
            failures.append(id).append(": sneaking threw the player out of the parked helicopter\n");
        }
        if (!airborne) {
            failures.append(id).append(": sneaking threw the player out of the helicopter in the air\n");
        }
        if (afterDismountKey) {
            failures.append(id).append(": the Vehicle Dismount key did not get the player out\n");
        }
    }

    private void sneakInBoat(ClientGameTestContext context, TestServerContext server, StringBuilder report, StringBuilder failures) {
        server.runCommand("ride @p dismount");
        server.runCommand("tp @a 600 ~ 0 0 0");
        context.waitTicks(10);
        server.runCommand("execute as @p at @s run summon minecraft:oak_boat ~ ~ ~3");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=minecraft:oak_boat,limit=1,sort=nearest]");
        context.waitFor(mc -> mc.player != null && mc.player.getVehicle() != null);
        context.waitTicks(10);
        context.getInput().holdKey(options -> options.keyShift);
        context.waitTicks(20);
        context.getInput().releaseKey(options -> options.keyShift);
        context.waitTicks(5);
        boolean stillInBoat = server.computeOnServer(s -> {
            for (Player player : s.getPlayerList().getPlayers()) {
                return player.getVehicle() != null;
            }
            return false;
        });
        report.append("vanilla boat: riding after sneak=").append(stillInBoat).append('\n');
        if (stillInBoat) {
            failures.append("vanilla boat: sneaking no longer dismounts (the change must only affect JEG vehicles)\n");
        }
    }

    private boolean serverPlayerInJegVehicle(TestServerContext server) {
        return server.computeOnServer(s -> {
            for (Player player : s.getPlayerList().getPlayers()) {
                Entity riding = player.getVehicle();
                return riding instanceof VehicleEntity;
            }
            return false;
        });
    }

    private static VehicleEntity vehicle(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof VehicleEntity vehicle ? vehicle : null;
    }
}
