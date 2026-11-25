package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.cabin.CabinNetwork;
import ttv.migami.jeg.vehicle.entity.ChinookEntity;

/**
 * Two faults the two-player play-test found (2026-11-25):
 * <ul>
 *     <li>A cold start finished on the ground cut the engines at once unless the collective was held, and so did every
 *     touchdown: the CH-47 now idles on the ground, rotors turning, until the pilot holds descend there.</li>
 *     <li>A survival player standing in the cabin was killed ("crushed by a vehicle") as the Chinook lifted off: the
 *     hull's ramming hit everyone inside its boxes. People aboard are carried, not rammed.</li>
 * </ul>
 */
public class Ch47GroundIdleCrewClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @p 0.5 -60 0.5 0 0");
            server.runCommand("summon jeg:ch47 0.5 -60 6.5");
            server.runCommand("ride @p mount @e[type=jeg:ch47,limit=1]");
            context.waitFor(mc -> mc.player.getVehicle() instanceof ChinookEntity);
            StringBuilder failures = new StringBuilder();

            // A tap of the collective starts it cold; hands off, it finishes the start and keeps running on the ground.
            context.getInput().holdKey(options -> options.keyJump);
            context.waitTicks(5);
            context.getInput().releaseKey(options -> options.keyJump);
            Object shutdownBefore = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.HelicopterEngineAudio.lastShutdown());
            context.waitTicks(460);
            String idleServer = server.computeOnServer(s -> state(this.chinook(s)));
            String idleClient = context.computeOnClient(mc -> state((ChinookEntity) mc.player.getVehicle()));
            boolean shutdownPlayed = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.HelicopterEngineAudio.lastShutdown()) != shutdownBefore;
            context.takeScreenshot("ch47_ground_idle");
            LOG.info("[ch47-ground-idle] after the start: server {} client {} shutdown played {}", idleServer, idleClient, shutdownPlayed);
            for (String side : new String[]{idleServer, idleClient}) {
                if (!side.contains("ground=true") || !side.contains("progress=1.00") || !side.contains("idle=true") || rotor(side) < 0.08F) {
                    failures.append("after a cold start on the ground it should idle, rotors at speed: ").append(side).append('\n');
                }
            }
            if (shutdownPlayed) {
                failures.append("no shut-down should play after the start\n");
            }

            // Descend held on the ground shuts it down.
            context.getInput().holdKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
            context.waitTicks(80);
            context.getInput().releaseKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
            context.waitTicks(5);
            String offServer = server.computeOnServer(s -> state(this.chinook(s)));
            String offClient = context.computeOnClient(mc -> state((ChinookEntity) mc.player.getVehicle()));
            LOG.info("[ch47-ground-idle] after holding descend: server {} client {}", offServer, offClient);
            if (!offServer.contains("progress=0.00") || !offClient.contains("progress=0.00")) {
                failures.append("holding descend on the ground should shut it down: server ").append(offServer)
                        .append(", client ").append(offClient).append('\n');
            }

            // A hot restart (the rotor still turning), a climb, and the pilot gets up into the cabin in survival.
            context.getInput().holdKey(options -> options.keyJump);
            context.waitTicks(80);
            context.getInput().releaseKey(options -> options.keyJump);
            context.waitTicks(20);
            server.runCommand("gamemode survival @a");
            context.getInput().pressKey(KeyBindings.VEHICLE_DISMOUNT);
            context.waitTicks(20);
            boolean aboard = server.computeOnServer(s -> CabinNetwork.isAboard(s.getPlayerList().getPlayers().get(0)));
            // Flown hard from the server (as a pilot's reports would): a climb and a 30 m/s circuit.
            server.runCommand("jegfly circuit 30 120");
            float minHealth = Float.MAX_VALUE;
            double fastest = 0.0D;
            for (int i = 0; i < 200; i++) {
                context.waitTick();
                float[] sample = server.computeOnServer(s -> {
                    var player = s.getPlayerList().getPlayers().get(0);
                    ChinookEntity chinook = this.chinook(s);
                    return new float[]{player.getHealth(), (float) chinook.position().subtract(chinook.xo, chinook.yo, chinook.zo).length() * 20.0F};
                });
                minHealth = Math.min(minHealth, sample[0]);
                fastest = Math.max(fastest, sample[1]);
            }
            server.runCommand("jegfly off");
            boolean stillAboard = server.computeOnServer(s -> CabinNetwork.isAboard(s.getPlayerList().getPlayers().get(0)));
            context.takeScreenshot("ch47_crew_flown");
            LOG.info("[ch47-crew] aboard {} -> {}, fastest {} m/s, lowest health {}", aboard, stillAboard, fastest, minHealth);
            if (!aboard || !stillAboard) {
                failures.append("the pilot should stand in the cabin through the flight: ").append(aboard).append(" -> ").append(stillAboard).append('\n');
            }
            if (fastest < 15.0D) {
                failures.append("the flight should reach speed (it was ").append(fastest).append(" m/s)\n");
            }
            if (minHealth < 20.0F) {
                failures.append("someone standing in the cabin must not be hurt by the flight: health fell to ").append(minHealth).append('\n');
            }
            if (!failures.isEmpty()) {
                throw new AssertionError("CH-47 ground idle and cabin crew:\n" + failures);
            }
        }
    }

    private static String state(ChinookEntity chinook) {
        return String.format(java.util.Locale.ROOT, "ground=%s progress=%.2f idle=%s rotor=%.4f",
                chinook.onGround(), chinook.helicopterStartupProgress(), chinook.helicopterGroundIdle(), chinook.propellerSpeed());
    }

    private static float rotor(String state) {
        return Float.parseFloat(state.substring(state.indexOf("rotor=") + 6));
    }

    private ChinookEntity chinook(net.minecraft.server.MinecraftServer server) {
        var player = server.getPlayerList().getPlayers().get(0);
        return server.overworld().getEntitiesOfClass(ChinookEntity.class, player.getBoundingBox().inflate(48.0D)).getFirst();
    }
}
