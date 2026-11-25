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
 * The pilot getting up to walk the cabin in flight (the user, 2026-11-21: "it should still hover even if the pilot gets
 * out of his seat"): the CH-47 holds its hover with its engines running, on the server and on the client (whose copy of
 * the engine state a late flag would have reset, playing the shutdown). Saved and loaded in its hover, its engines run
 * on; with nobody aboard at all it lands itself, unhurt (2026-11-25: one left hovering in the user's world fell 60 m
 * when the world was loaded again, and an empty one dropped out of its hover the moment the last person left).
 */
public class Ch47CrewHoverClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @p 0.5 -45 0.5 0 0");
            // Summoned and boarded in the air: a hot start, hovering at once.
            server.runCommand("summon jeg:ch47 0.5 -45 6.5");
            server.runCommand("ride @p mount @e[type=jeg:ch47,limit=1]");
            context.waitFor(mc -> mc.player.getVehicle() instanceof ChinookEntity);
            context.waitTicks(80);
            double before = server.computeOnServer(s -> this.chinook(s).getY());

            float rotorBefore = context.computeOnClient(mc -> mc.player.getVehicle() instanceof ChinookEntity c ? c.propellerSpeed() : -1.0F);
            Object shutdownBefore = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.HelicopterEngineAudio.lastShutdown());
            context.getInput().pressKey(KeyBindings.VEHICLE_DISMOUNT);
            // Every tick after the pilot gets up, as the client hears it (the user, 2026-11-21: "there is a noise
            // indicating the helicopter is slowing down but it shouldn't as it should just be hovering"): the engines'
            // state, the rotor's speed and the engine sound's pitch must hold, and no shutdown may play.
            float minProgress = 1.0F, minRotor = Float.MAX_VALUE, minPitch = Float.MAX_VALUE;
            StringBuilder timeline = new StringBuilder();
            for (int i = 0; i < 200; i++) {
                context.waitTick();
                float[] sample = context.computeOnClient(mc -> {
                    ChinookEntity c = mc.level.getEntitiesOfClass(ChinookEntity.class, mc.player.getBoundingBox().inflate(16.0D)).getFirst();
                    return new float[]{c.helicopterStartupProgress(), c.propellerSpeed(),
                            ttv.migami.jeg.vehicle.client.audio.HelicopterEngineAudio.enginePitch(c.getId())};
                });
                minProgress = Math.min(minProgress, sample[0]);
                minRotor = Math.min(minRotor, sample[1]);
                if (sample[2] > 0.0F) minPitch = Math.min(minPitch, sample[2]);
                if (i < 30 || i % 20 == 0) {
                    timeline.append(String.format(java.util.Locale.ROOT, " %d:%.2f/%.4f/%.2f", i, sample[0], sample[1], sample[2]));
                }
            }
            boolean shutdownPlayed = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.HelicopterEngineAudio.lastShutdown()) != shutdownBefore;
            LOG.info("[ch47-crew-hover] after getting up: rotor before {}, min progress {}, min rotor {}, min pitch {}, shutdown {}; tick:progress/rotor/pitch{}",
                    rotorBefore, minProgress, minRotor, minPitch, shutdownPlayed, timeline);
            String state = server.computeOnServer(s -> {
                ChinookEntity chinook = this.chinook(s);
                var player = s.getPlayerList().getPlayers().get(0);
                return String.format(java.util.Locale.ROOT, "y=%.2f progress=%.2f rotor=%.3f seated=%s inCabin=%s",
                        chinook.getY(), chinook.helicopterStartupProgress(), chinook.propellerSpeed(),
                        player.getVehicle() != null, CabinNetwork.isAboard(player));
            });
            String client = context.computeOnClient(mc -> {
                ChinookEntity chinook = mc.level.getEntitiesOfClass(ChinookEntity.class, mc.player.getBoundingBox().inflate(16.0D)).getFirst();
                return String.format(java.util.Locale.ROOT, "progress=%.2f sound=%s",
                        chinook.helicopterStartupProgress(), ttv.migami.jeg.vehicle.client.audio.HelicopterEngineAudio.phase(chinook.getId()));
            });
            double after = server.computeOnServer(s -> this.chinook(s).getY());
            context.takeScreenshot("ch47_crew_hover");
            LOG.info("[ch47-crew-hover] y {} -> {} server {} client {}", before, after, state, client);
            StringBuilder failures = new StringBuilder();
            if (!state.contains("seated=false") || !state.contains("inCabin=true")) {
                failures.append("the pilot should have got up into the cabin: ").append(state).append('\n');
            }
            if (Math.abs(after - before) > 1.0D || !state.contains("progress=1.00")) {
                failures.append("with the pilot up in the cabin it should hover, engines on: y ").append(before).append(" -> ")
                        .append(after).append(", ").append(state).append('\n');
            }
            if (minProgress < 1.0F || shutdownPlayed) {
                failures.append("the client's engines should stay on as the pilot gets up: min progress ").append(minProgress)
                        .append(", shutdown played ").append(shutdownPlayed).append('\n');
            }
            if (minRotor < rotorBefore * 0.97F || minPitch < 0.97F) {
                failures.append("the rotor (and its sound) should keep its speed as the pilot gets up: ").append(rotorBefore)
                        .append(" -> min ").append(minRotor).append(", sound pitch min ").append(minPitch).append('\n');
            }
            if (!client.equals("progress=1.00 sound=FLIGHT")) {
                failures.append("the client should agree the engines run: ").append(client).append('\n');
            }

            // Saved and loaded in its hover (the world saved and loaded again): its engines run on.
            String loaded = server.computeOnServer(s -> {
                ChinookEntity original = this.chinook(s);
                var out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,
                        s.registryAccess());
                original.saveWithoutId(out);
                ChinookEntity copy = (ChinookEntity) original.getType().create(s.overworld(), net.minecraft.world.entity.EntitySpawnReason.LOAD);
                copy.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
                        s.registryAccess(), out.buildResult()));
                return String.format(java.util.Locale.ROOT, "progress=%.2f rotor=%.3f %s", copy.helicopterStartupProgress(),
                        copy.propellerSpeed(), copy.helicopterDebugState());
            });
            LOG.info("[ch47-crew-hover] saved and loaded in the hover: {}", loaded);
            if (!loaded.startsWith("progress=1.00") || !loaded.contains("over true")) {
                failures.append("saved and loaded in its hover, its engines should still run: ").append(loaded).append('\n');
            }

            // Nobody aboard: it lets itself down and lands, unhurt, engines winding down.
            float health = server.computeOnServer(s -> this.chinook(s).vehicleHealth());
            server.runCommand("tp @p 40.5 -60 0.5");
            double lowest = after;
            String landed = "";
            for (int i = 0; i < 30; i++) {
                context.waitTicks(20);
                double[] sample = server.computeOnServer(s -> {
                    ChinookEntity chinook = s.overworld().getEntitiesOfClass(ChinookEntity.class,
                            new net.minecraft.world.phys.AABB(-30, -70, -30, 30, 0, 30)).getFirst();
                    return new double[]{chinook.getY(), chinook.getDeltaMovement().y * 20.0D, chinook.onGround() ? 1 : 0,
                            chinook.vehicleHealth(), chinook.isWrecked() ? 1 : 0, chinook.helicopterStartupProgress()};
                });
                lowest = Math.min(lowest, sample[0]);
                landed = String.format(java.util.Locale.ROOT, "y=%.2f vy=%.1f ground=%s health=%.1f/%.1f wrecked=%s progress=%.2f",
                        sample[0], sample[1], sample[2] > 0, sample[3], health, sample[4] > 0, sample[5]);
                if (i < 12) {
                    LOG.info("[ch47-crew-hover] abandoned {} s: {}", i + 1, landed);
                }
                if (sample[2] > 0 && i > 2) {
                    break;
                }
            }
            LOG.info("[ch47-crew-hover] abandoned: {}", landed);
            if (lowest > after - 5.0D || !landed.contains("ground=true") || landed.contains("wrecked=true")) {
                failures.append("abandoned, it should let itself down and land: ").append(landed).append('\n');
            }
            float healthAfter = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ChinookEntity.class,
                    new net.minecraft.world.phys.AABB(-30, -70, -30, 30, 0, 30)).getFirst().vehicleHealth());
            if (healthAfter < health - 0.5F) {
                failures.append("abandoned, it should land unhurt: health ").append(health).append(" -> ").append(healthAfter).append('\n');
            }
            if (!failures.isEmpty()) {
                throw new AssertionError("CH-47 hover without a pilot:\n" + failures);
            }
        }
    }

    private ChinookEntity chinook(net.minecraft.server.MinecraftServer server) {
        var player = server.getPlayerList().getPlayers().get(0);
        return server.overworld().getEntitiesOfClass(ChinookEntity.class, player.getBoundingBox().inflate(16.0D)).getFirst();
    }
}
