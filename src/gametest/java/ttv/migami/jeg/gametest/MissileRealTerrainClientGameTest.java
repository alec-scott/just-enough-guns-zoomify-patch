package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.render.MissileBlastRenderer;
import ttv.migami.jeg.client.render.MissileTrailRenderer;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

/**
 * A long shot over real terrain, out past everything the server has loaded. Unlike the flat test world, these chunks take
 * time to load or generate, far longer than the missile takes to reach them: it has to keep flying every tick anyway,
 * stay followed by the client all the way, and go off on time rather than hang at the edge of the loaded world.
 */
public class MissileRealTerrainClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int RENDER_DISTANCE = 6;
    private static final int SIMULATION_DISTANCE = 5;
    private static final double ALTITUDE = 260.0D;

    @Override
    public void runTest(ClientGameTestContext context) {
        int[] previous = context.computeOnClient(mc -> new int[]{mc.options.renderDistance().get(), mc.options.simulationDistance().get()});
        context.runOnClient(mc -> {
            mc.options.renderDistance().set(RENDER_DISTANCE);
            mc.options.simulationDistance().set(SIMULATION_DISTANCE);
        });
        StringBuilder report = new StringBuilder("missile real terrain report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).create()) {
            TestServerContext server = world.getServer();
            // Spectating, so the player hangs in the air above the terrain, facing +X along the shot.
            server.runCommand("gamemode spectator @a");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("tp @a 0 " + (int) ALTITUDE + " 0 -90 0");
            context.waitTicks(100);
            world.getClientLevel().waitForChunksRender();
            int blastsBefore = context.computeOnClient(mc -> MissileBlastRenderer.blastsStarted());

            // Two shots: the first over terrain no one has been to, the second over the same ground once it exists but
            // has been unloaded again, as on a server whose world is already explored.
            for (int shot = 1; shot <= 2; shot++) {
                double z = shot == 1 ? -6.0D : 40.0D;
                AtomicReference<VehicleMissileEntity> fired = new AtomicReference<>();
                long launchTick = server.computeOnServer(s -> {
                    ServerLevel level = s.overworld();
                    Entity owner = EntityType.MARKER.create(level, EntitySpawnReason.COMMAND);
                    owner.setPos(0.0D, ALTITUDE, z);
                    level.addFreshEntity(owner);
                    VehicleMissileEntity missile = new VehicleMissileEntity(level, owner, null, new Vec3(8.0D, ALTITUDE, z),
                            new Vec3(0.8D, 0.0D, 0.0D), Reference.id("vehicle_9m120_passenger_missile"));
                    level.addFreshEntity(missile);
                    fired.set(missile);
                    return (long) s.getTickCount();
                });
                int missileId = fired.get().getId();

                List<String> samples = new ArrayList<>();
                double maxX = 0.0D;
                long goneAfter = -1L;
                int flownTicks = 0;
                int followed = 0;
                int watched = 0;
                for (int tick = 1; tick <= 400; tick++) {
                    context.waitTick();
                    double[] state = server.computeOnServer(s -> {
                        VehicleMissileEntity missile = fired.get();
                        return new double[]{missile.getX(), missile.tickCount, missile.isRemoved() ? 1.0D : 0.0D, s.getTickCount() - launchTick};
                    });
                    boolean seen = context.computeOnClient(mc -> mc.level.getEntity(missileId) != null || MissileTrailRenderer.farMissiles() > 0);
                    maxX = Math.max(maxX, state[0]);
                    flownTicks = (int) state[1];
                    if (state[2] > 0.0D) {
                        goneAfter = (long) state[3];
                        break;
                    }
                    watched++;
                    if (seen) {
                        followed++;
                    }
                    if (tick % 20 == 0) {
                        samples.add(String.format("  t%d: server tick +%d, missile age %d, x=%.1f, client follows it=%s",
                                tick, (long) state[3], (int) state[1], state[0], seen));
                    }
                }
                context.waitTicks(10);
                int blasts = context.computeOnClient(mc -> MissileBlastRenderer.blastsStarted()) - blastsBefore;
                report.append(String.format("shot %d: flew to x=%.1f in %d of its own ticks, gone %d server ticks after launch, "
                        + "client followed it %d of %d ticks, blasts drawn so far %d%n", shot, maxX, flownTicks, goneAfter, followed, watched, blasts));
                for (String sample : samples) {
                    report.append(sample).append('\n');
                }
                if (goneAfter < 0L) {
                    failures.append(String.format("shot %d: the missile was still in the air 400 ticks after launch, hung at x=%.1f%n", shot, maxX));
                } else if (goneAfter > flownTicks + 6) {
                    failures.append(String.format("shot %d: the missile hung in the air for %d ticks of its flight%n", shot, goneAfter - flownTicks));
                }
                if (maxX < 380.0D) {
                    failures.append(String.format("shot %d: the missile only got %.1f blocks out%n", shot, maxX));
                }
                if (followed < watched - 6) {
                    failures.append(String.format("shot %d: the client lost sight of the missile for %d ticks%n", shot, watched - followed));
                }
                if (blasts < shot) {
                    failures.append(String.format("shot %d: its blast was never drawn%n", shot));
                }
                if (shot == 1) {
                    // Long enough for the chunks the first shot loaded to be let go of again.
                    context.waitTicks(400);
                }
            }
        } finally {
            context.runOnClient(mc -> {
                mc.options.renderDistance().set(previous[0]);
                mc.options.simulationDistance().set(previous[1]);
            });
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }
}
