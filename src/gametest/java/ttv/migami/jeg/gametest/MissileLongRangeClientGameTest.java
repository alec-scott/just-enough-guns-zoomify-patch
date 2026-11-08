package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.render.FarDraw;
import ttv.migami.jeg.client.render.MissileBlastRenderer;
import ttv.migami.jeg.client.render.MissileTrailRenderer;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

/**
 * A long shot on a server with a short view and simulation distance (here 96 and 80 blocks, like yftm's 192 and 160 at
 * scale). The missile has to keep flying once it is past the simulation distance (it used to freeze in mid-air and never
 * go off), its motor and trail have to stay in view once it is past entity range, and its blast has to show far out.
 */
public class MissileLongRangeClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int RENDER_DISTANCE = 6;
    private static final int SIMULATION_DISTANCE = 5;

    @Override
    public void runTest(ClientGameTestContext context) {
        int[] previous = context.computeOnClient(mc -> new int[]{mc.options.renderDistance().get(), mc.options.simulationDistance().get()});
        context.runOnClient(mc -> {
            mc.options.renderDistance().set(RENDER_DISTANCE);
            mc.options.simulationDistance().set(SIMULATION_DISTANCE);
        });
        StringBuilder report = new StringBuilder("missile long range report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule show_advancement_messages false");
            // Facing +X, along the shot, a little up.
            server.runCommand("tp @a 0 -59 0 -90 -4");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
            context.waitTicks(60);
            int viewDistance = server.computeOnServer(s -> s.getPlayerList().getViewDistance());
            int simulationDistance = server.computeOnServer(s -> s.getPlayerList().getSimulationDistance());
            int blastsBefore = context.computeOnClient(mc -> MissileBlastRenderer.blastsStarted());

            // Wire guided with no one to guide it (a marker owner), so it flies dead straight out along +X.
            int missileId = server.computeOnServer(s -> {
                ServerLevel level = s.overworld();
                Entity owner = EntityType.MARKER.create(level, EntitySpawnReason.COMMAND);
                owner.setPos(0.0D, -55.0D, -6.0D);
                level.addFreshEntity(owner);
                VehicleMissileEntity missile = new VehicleMissileEntity(level, owner, null, new Vec3(8.0D, -48.0D, -6.0D),
                        new Vec3(0.8D, 0.0D, 0.0D), Reference.id("vehicle_9m120_passenger_missile"));
                level.addFreshEntity(missile);
                return missile.getId();
            });

            List<String> samples = new ArrayList<>();
            double maxServerX = 0.0D;
            int farFollowed = 0;
            boolean exploded = false;
            for (int tick = 1; tick <= 220; tick++) {
                context.waitTick();
                double[] server_ = server.computeOnServer(s -> {
                    Entity missile = s.overworld().getEntity(missileId);
                    return missile == null ? new double[]{Double.NaN} : new double[]{missile.getX()};
                });
                Object[] client = context.computeOnClient(mc -> new Object[]{
                        mc.level.getEntity(missileId) != null, MissileTrailRenderer.farMissiles(), MissileBlastRenderer.blastsStarted()});
                if (!Double.isNaN(server_[0])) {
                    maxServerX = Math.max(maxServerX, server_[0]);
                }
                boolean clientHasEntity = (boolean) client[0];
                int far = (int) client[1];
                if (!clientHasEntity && far > 0) {
                    farFollowed++;
                }
                if ((int) client[2] > blastsBefore) {
                    exploded = true;
                }
                if (tick % 20 == 0) {
                    samples.add(String.format("t%d server x=%.1f, client entity=%s, far followed=%d, blasts=%d",
                            tick, server_[0], clientHasEntity, far, (int) client[2] - blastsBefore));
                }
                if (tick == 50 || tick == 80 || tick == 110) {
                    context.takeScreenshot("missile_long_range_t" + tick);
                }
                if (exploded && tick > 140) {
                    context.waitTicks(4);
                    context.takeScreenshot("missile_long_range_blast");
                    break;
                }
            }
            float fogStart = context.computeOnClient(mc -> FarDraw.renderFogStart());
            report.append(String.format("view distance %d chunks, simulation distance %d chunks, render fog starts at %.1f blocks%n",
                    viewDistance, simulationDistance, fogStart));
            for (String sample : samples) {
                report.append(sample).append('\n');
            }
            report.append(String.format("furthest out %.1f blocks; followed past entity range for %d ticks; blast drawn %s%n",
                    maxServerX, farFollowed, exploded));
            double simulationBlocks = simulationDistance * 16.0D;
            if (maxServerX < simulationBlocks + 150.0D) {
                failures.append(String.format("missile stopped at %.1f blocks, the simulation distance is %.0f%n", maxServerX, simulationBlocks));
            }
            if (farFollowed < 20) {
                failures.append(String.format("motor and trail were only followed beyond entity range for %d ticks%n", farFollowed));
            }
            if (!exploded) {
                failures.append("the far blast was never drawn\n");
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
