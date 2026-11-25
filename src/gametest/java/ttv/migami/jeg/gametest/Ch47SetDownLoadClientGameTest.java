package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.entity.ChinookEntity;
import ttv.migami.jeg.vehicle.entity.ChinookSling;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Setting a slung helicopter down and letting it go (the user, 2026-11-25: "if the vehicle it carries glitches out the
 * heli also glitches out ... I tried to carry another heli with the rope and when it bugged out I disconnected it,
 * which also glitched the Chinook out"). Come down with an AH-6 on the hook until it stands on the ground: the Chinook
 * stops over it, its belly clear of the load's top, instead of landing on its roof, and holds there without bobbing;
 * let go, it stays steady, and neither aircraft is hurt.
 */
public class Ch47SetDownLoadClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @p 0.5 -55 0.5");
            server.runCommand("summon jeg:ch47 0.5 -51.5 9.9");
            server.runCommand("ride @p mount @e[type=jeg:ch47,limit=1]");
            context.waitFor(mc -> mc.player.getVehicle() instanceof ChinookEntity);
            context.waitTicks(20);
            server.runCommand("summon jeg:ah6 0.5 -60 10.5");
            context.waitTicks(20);
            float[] healthBefore = this.health(server);

            // Hook the AH-6 and lift it clear.
            context.getInput().pressKey(KeyBindings.VEHICLE_TOW);
            context.waitTicks(70);
            context.getInput().pressKey(KeyBindings.VEHICLE_TOW);
            context.waitTicks(5);
            int state = server.computeOnServer(s -> this.chinook(s).sling().state());
            if (state != ChinookSling.CARRYING) {
                throw new AssertionError("the AH-6 wasn't hooked (sling state " + state + ")");
            }
            context.getInput().holdKey(options -> options.keyJump);
            context.waitTicks(50);
            context.getInput().releaseKey(options -> options.keyJump);
            context.waitTicks(160);

            // Come down with it until it stands on the ground, and keep holding descend.
            StringBuilder failures = new StringBuilder();
            context.getInput().holdKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
            context.waitTicks(200);
            double[] held = this.watch(context, server, 60);
            context.getInput().releaseKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
            context.takeScreenshot("ch47_set_down_load");
            LOG.info("[ch47-set-down] holding descend over the set-down AH-6: clearance min {} max {}, reversals {}, largest step {}",
                    held[0], held[1], held[2], held[3]);
            if (held[0] < 0.8D) {
                failures.append("coming down on its load it should stop over it, belly clear of the load's top: clearance ")
                        .append(held[0]).append('\n');
            }
            if (held[2] > 3 || held[3] > 0.15D) {
                failures.append("held over the set-down load it should be steady: ").append((int) held[2])
                        .append(" up-down reversals in 3 s, largest step ").append(held[3]).append(" m\n");
            }

            // Let it go (the second press within the release window).
            context.getInput().pressKey(KeyBindings.VEHICLE_TOW);
            context.waitTicks(5);
            context.getInput().pressKey(KeyBindings.VEHICLE_TOW);
            context.waitTicks(10);
            int after = server.computeOnServer(s -> this.chinook(s).sling().state());
            if (after == ChinookSling.CARRYING) {
                failures.append("two presses of the tow key should let the load go\n");
            }
            double[] released = this.watch(context, server, 60);
            LOG.info("[ch47-set-down] after letting go: reversals {}, largest step {}", released[2], released[3]);
            if (released[2] > 3 || released[3] > 0.15D) {
                failures.append("after letting the load go it should be steady: ").append((int) released[2])
                        .append(" up-down reversals in 3 s, largest step ").append(released[3]).append(" m\n");
            }
            float[] healthAfter = this.health(server);
            LOG.info("[ch47-set-down] health: Chinook {} -> {}, AH-6 {} -> {}", healthBefore[0], healthAfter[0], healthBefore[1], healthAfter[1]);
            if (healthAfter[0] < healthBefore[0] - 0.5F || healthAfter[1] < healthBefore[1] - 0.5F) {
                failures.append("neither aircraft should be hurt: Chinook ").append(healthBefore[0]).append(" -> ").append(healthAfter[0])
                        .append(", AH-6 ").append(healthBefore[1]).append(" -> ").append(healthAfter[1]).append('\n');
            }
            if (!failures.isEmpty()) {
                throw new AssertionError("CH-47 setting a slung helicopter down:\n" + failures);
            }
        }
    }

    /**
     * Each tick for {@code ticks}: the Chinook's belly over the AH-6's top (min, max), how often its height turned between
     * going up and going down by more than a centimetre (a bob), and its largest move in a tick.
     */
    private double[] watch(ClientGameTestContext context, TestServerContext server, int ticks) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, largest = 0.0D;
        int reversals = 0;
        double lastY = Double.NaN, lastStep = 0.0D;
        for (int i = 0; i < ticks; i++) {
            context.waitTick();
            double[] sample = server.computeOnServer(s -> {
                ChinookEntity chinook = this.chinook(s);
                VehicleEntity ah6 = this.ah6(s);
                return new double[]{chinook.getY(), chinook.position().distanceTo(new net.minecraft.world.phys.Vec3(chinook.xo, chinook.yo, chinook.zo)),
                        chinook.getBoundingBox().minY - ah6.getBoundingBox().maxY};
            });
            min = Math.min(min, sample[2]);
            max = Math.max(max, sample[2]);
            largest = Math.max(largest, sample[1]);
            if (!Double.isNaN(lastY)) {
                double step = sample[0] - lastY;
                if (Math.abs(step) > 0.01D && Math.abs(lastStep) > 0.01D && Math.signum(step) != Math.signum(lastStep)) {
                    reversals++;
                }
                if (Math.abs(step) > 0.01D) {
                    lastStep = step;
                }
            }
            lastY = sample[0];
        }
        return new double[]{min, max, reversals, largest};
    }

    private float[] health(TestServerContext server) {
        return server.computeOnServer(s -> new float[]{this.chinook(s).vehicleHealth(), this.ah6(s).vehicleHealth()});
    }

    private ChinookEntity chinook(net.minecraft.server.MinecraftServer server) {
        for (var entity : server.overworld().getAllEntities()) {
            if (entity instanceof ChinookEntity chinook) {
                return chinook;
            }
        }
        throw new AssertionError("no Chinook");
    }

    private VehicleEntity ah6(net.minecraft.server.MinecraftServer server) {
        for (var entity : server.overworld().getAllEntities()) {
            if (entity instanceof VehicleEntity vehicle && !(entity instanceof ChinookEntity)) {
                return vehicle;
            }
        }
        throw new AssertionError("no AH-6");
    }
}
