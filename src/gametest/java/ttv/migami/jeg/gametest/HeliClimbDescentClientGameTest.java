package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The AH-6 and Mi-28 climb on Space and come back down on the descend key from high up: both rates are brisk (0.5 and
 * 0.55 blocks a tick), the landing assist still brings the skids down gently enough to cost no health, and holding
 * descend never sets off the descent warning.
 */
public class HeliClimbDescentClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final double MIN_CLIMB = 0.46D;
    private static final double MIN_SINK = 0.51D;
    private static final double SAFE_TOUCHDOWN = 0.35D;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("heli climb/descent report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("justEnoughGuns config vehicle helicopter weightyFlight true");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            this.climbAndDescend(context, server, report, failures, "ah6", 0);
            this.climbAndDescend(context, server, report, failures, "mi28", 300);
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    private void climbAndDescend(ClientGameTestContext context, TestServerContext server, StringBuilder report,
                                 StringBuilder failures, String id, int x) {
        server.runCommand("ride @p dismount");
        server.runCommand("tp @a " + x + " ~ 0 0 0");
        context.waitTicks(10);
        server.runCommand("execute as @p at @s run summon jeg:" + id + " ~ ~ ~4 {Rotation:[0f,0f]}");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=jeg:" + id + ",limit=1,sort=nearest]");
        context.waitFor(mc -> vehicle(mc) != null);
        context.waitTicks(10);
        float healthStart = this.heliHealth(server);

        context.getInput().holdKey(options -> options.keyJump);
        int startup = context.computeOnClient(mc -> vehicle(mc).helicopterStartupTicks());
        context.waitTicks(startup);
        double peakClimb = 0.0D;
        double last = context.computeOnClient(mc -> vehicle(mc).getY());
        for (int tick = 0; tick < 160; tick++) {
            context.waitTick();
            double y = context.computeOnClient(mc -> vehicle(mc).getY());
            peakClimb = Math.max(peakClimb, y - last);
            last = y;
        }
        context.getInput().releaseKey(options -> options.keyJump);
        context.waitTicks(40);
        double altitude = context.computeOnClient(mc -> vehicle(mc).getY() - mc.player.level().getMinY());
        context.takeScreenshot("heli_climb_" + id);

        context.getInput().holdKey(options -> KeyBindings.VEHICLE_BRAKE_DESCEND);
        double peakSink = 0.0D;
        double touchdown = 0.0D;
        int descentTicks = 0;
        String warning = null;
        last = context.computeOnClient(mc -> vehicle(mc).getY());
        while (descentTicks < 800) {
            context.waitTick();
            descentTicks++;
            Object[] state = context.computeOnClient(mc -> new Object[]{vehicle(mc).getY(), vehicle(mc).onGround(),
                    vehicle(mc).activeWarningMessageKey()});
            double y = (double) state[0];
            double sink = last - y;
            last = y;
            if (!((String) state[2]).isEmpty() && warning == null) {
                warning = (String) state[2];
            }
            if ((boolean) state[1]) {
                break;
            }
            peakSink = Math.max(peakSink, sink);
            touchdown = sink;
        }
        context.getInput().releaseKey(options -> KeyBindings.VEHICLE_BRAKE_DESCEND);
        context.waitTicks(20);
        float healthAfter = this.heliHealth(server);

        String line = String.format("%s: peak climb %.3f b/t to %.1f blocks up; descent %d ticks, peak sink %.3f b/t, "
                        + "touchdown sink %.3f b/t; health %.2f -> %.2f; warning %s%n",
                id, peakClimb, altitude, descentTicks, peakSink, touchdown, healthStart, healthAfter, warning);
        LOGGER.info("[heli-climb-descent] {}", line.trim());
        report.append(line);
        if (peakClimb < MIN_CLIMB) {
            failures.append(String.format("%s: climbs at only %.3f b/t%n", id, peakClimb));
        }
        if (peakSink < MIN_SINK) {
            failures.append(String.format("%s: descends at only %.3f b/t%n", id, peakSink));
        }
        if (touchdown > SAFE_TOUCHDOWN) {
            failures.append(String.format("%s: touched down at %.3f b/t, faster than is safe%n", id, touchdown));
        }
        if (healthStart - healthAfter > 0.5F) {
            failures.append(String.format("%s: a held descent cost %.2f health%n", id, healthStart - healthAfter));
        }
        if (warning != null) {
            failures.append(String.format("%s: holding descend set off the warning %s%n", id, warning));
        }
        if (descentTicks >= 800) {
            failures.append(id).append(": never landed\n");
        }
    }

    private float heliHealth(TestServerContext server) {
        return server.computeOnServer(s -> {
            for (var level : s.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof VehicleEntity vehicle && !vehicle.getPassengers().isEmpty()) {
                        return vehicle.vehicleHealth();
                    }
                }
            }
            return -1.0F;
        });
    }

    private static VehicleEntity vehicle(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof VehicleEntity vehicle ? vehicle : null;
    }
}
