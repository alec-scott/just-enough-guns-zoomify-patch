package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The CH-47 flies like the Mi-28, the bigger of the other helicopters, but slower (the user, 2026-11-21: "the helicopter
 * movement and feeling should feel closer to the bigger heli (but slower)"). Both are flown the same way from a hover: W
 * held for twelve seconds. The Chinook's top speed is 75-90% of the Mi-28's, and it takes no more than 1.4 times as long
 * to reach 90% of its own top speed, where it used to answer its controls at 60% of the Mi-28's rates.
 */
public class Ch47FlightFeelClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            double[] mi28 = this.fly(context, server, "mi28", 0.5D);
            double[] ch47 = this.fly(context, server, "ch47", 60.5D);
            double[] mi28Side = this.strafe(context, server, "mi28", 120.5D);
            double[] ch47Side = this.strafe(context, server, "ch47", 180.5D);
            double speedShare = ch47[0] / mi28[0];
            double timeShare = ch47[1] / mi28[1];
            LOG.info("[ch47-flight-feel] top speed: Mi-28 {} m/s, CH-47 {} m/s ({}%); ticks to 90%: Mi-28 {}, CH-47 {} ({}x)",
                    mi28[0], ch47[0], Math.round(speedShare * 100.0D), mi28[1], ch47[1], timeShare);
            LOG.info("[ch47-flight-feel] strafing left: the view first moved the wrong way {} m (Mi-28) and {} m (CH-47); after 1 s it is {} m and {} m to the left",
                    mi28Side[0], ch47Side[0], mi28Side[1], ch47Side[1]);
            StringBuilder failures = new StringBuilder();
            // The user, 2026-11-21: "when swaying to the left on the chinook it goes right a little bit before going left
            // and vice versa, emulate the movement of the mi28 but slower". It used to slide right 0.08 m (the Mi-28 0.05).
            if (ch47Side[0] > Math.min(0.05D, mi28Side[0])) {
                failures.append("strafing left, the CH-47's view should not first slide right (").append(ch47Side[0])
                        .append(" m; the Mi-28's ").append(mi28Side[0]).append(" m)\n");
            }
            if (speedShare < 0.75D || speedShare > 0.9D) {
                failures.append("the CH-47's top speed should be 75-90% of the Mi-28's, it is ").append(Math.round(speedShare * 100.0D)).append("%\n");
            }
            if (timeShare > 1.4D) {
                failures.append("the CH-47 should reach its speed nearly as briskly as the Mi-28, it takes ").append(timeShare).append("x as long\n");
            }
            if (!failures.isEmpty()) {
                throw new AssertionError("CH-47 flight feel:\n" + failures);
            }
        }
    }

    /**
     * {how far the pilot's view first slides right, the wrong way, in metres; how far left it is after a second} as
     * {@code id} strafes left (A) from a hover. Logs the first second tick by tick: sideways speed, roll, view.
     */
    private double[] strafe(ClientGameTestContext context, TestServerContext server, String id, double x) {
        server.runCommand("tp @p " + x + " -40 0.5 0 0");
        server.runCommand("summon jeg:" + id + " " + x + " -40 0.5");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=jeg:" + id + ",limit=1,sort=nearest,x=" + x + ",y=-40,z=0.5]");
        context.waitFor(mc -> mc.player.getVehicle() instanceof VehicleEntity);
        context.waitTicks(80);
        double[] start = context.computeOnClient(mc -> {
            var cam = mc.gameRenderer.getMainCamera().position();
            VehicleEntity v = (VehicleEntity) mc.player.getVehicle();
            return new double[]{cam.x, cam.z, v.getX(), v.getZ(), v.getYRot()};
        });
        double yaw = Math.toRadians(start[4]);
        double leftX = Math.cos(yaw), leftZ = Math.sin(yaw);
        double wrong = 0.0D, after = 0.0D;
        StringBuilder log = new StringBuilder();
        context.getInput().holdKey(options -> options.keyLeft);
        for (int i = 0; i < 20; i++) {
            context.waitTick();
            double[] s = context.computeOnClient(mc -> {
                var cam = mc.gameRenderer.getMainCamera().position();
                VehicleEntity v = (VehicleEntity) mc.player.getVehicle();
                return new double[]{cam.x, cam.z, v.getX(), v.getZ(), v.roll(1.0F)};
            });
            double view = (s[0] - start[0]) * leftX + (s[1] - start[1]) * leftZ;
            double hull = (s[2] - start[2]) * leftX + (s[3] - start[3]) * leftZ;
            wrong = Math.max(wrong, -view);
            after = view;
            log.append(String.format(java.util.Locale.ROOT, " %d:hull%+.2f/roll%+.1f/view%+.2f", i, hull, s[4], view));
        }
        context.getInput().releaseKey(options -> options.keyLeft);
        LOG.info("[ch47-flight-feel] {} strafing left, tick:hull/roll/view{}", id, log);
        context.getInput().pressKey(KeyBindings.VEHICLE_DISMOUNT);
        context.waitTicks(20);
        server.runCommand("kill @e[type=jeg:" + id + "]");
        context.waitTicks(10);
        return new double[]{wrong, after};
    }

    /** {top speed in m/s, ticks to reach 90% of it} flying {@code id} forward from a hover. */
    private double[] fly(ClientGameTestContext context, TestServerContext server, String id, double x) {
        server.runCommand("tp @p " + x + " -40 0.5 0 0");
        server.runCommand("summon jeg:" + id + " " + x + " -40 0.5");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=jeg:" + id + ",limit=1,sort=nearest,x=" + x + ",y=-40,z=0.5]");
        context.waitFor(mc -> mc.player.getVehicle() instanceof VehicleEntity);
        context.waitTicks(60);
        double[] speeds = new double[240];
        context.getInput().holdKey(options -> options.keyUp);
        for (int i = 0; i < speeds.length; i++) {
            context.waitTick();
            speeds[i] = context.computeOnClient(mc -> {
                VehicleEntity v = (VehicleEntity) mc.player.getVehicle();
                return Math.hypot(v.getX() - v.xo, v.getZ() - v.zo) * 20.0D;
            });
        }
        context.getInput().releaseKey(options -> options.keyUp);
        double top = 0.0D;
        for (double s : speeds) top = Math.max(top, s);
        int reach = speeds.length;
        for (int i = 0; i < speeds.length; i++) {
            if (speeds[i] >= 0.9D * top) {
                reach = i;
                break;
            }
        }
        context.getInput().pressKey(KeyBindings.VEHICLE_DISMOUNT);
        context.waitTicks(20);
        server.runCommand("kill @e[type=jeg:" + id + "]");
        context.waitTicks(10);
        return new double[]{top, reach};
    }
}
