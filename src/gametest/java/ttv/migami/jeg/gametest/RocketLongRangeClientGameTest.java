package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.render.MissileTrailRenderer;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.vehicle.util.VehicleWeaponStats;

/**
 * Helicopter rockets (70 mm and 80 mm) fired level over real terrain, out past the simulation distance and everything
 * the server has loaded. Like a guided missile, a rocket has to keep flying every tick, stay followed by the client
 * beyond the range its entity is sent at, and be gone at the end of its range instead of hanging in the air.
 */
public class RocketLongRangeClientGameTest implements FabricClientGameTest {
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
        StringBuilder report = new StringBuilder("rocket long range report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).create()) {
            TestServerContext server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("tp @a 0 " + (int) ALTITUDE + " 0 -90 0");
            context.waitTicks(100);
            world.getClientLevel().waitForChunksRender();

            String[] weapons = {"vehicle_70mm_rocket", "vehicle_80mm_rocket"};
            for (int shot = 0; shot < weapons.length; shot++) {
                String weapon = weapons[shot];
                double z = shot == 0 ? -6.0D : 40.0D;
                var stats = VehicleWeaponStats.get(Reference.id(weapon));
                double speed = stats.projectileSpeed();
                AtomicReference<BulletEntity> fired = new AtomicReference<>();
                long launchTick = server.computeOnServer(s -> {
                    ServerLevel level = s.overworld();
                    var player = s.getPlayerList().getPlayers().get(0);
                    BulletEntity rocket = new BulletEntity(level, player, stats, new Vec3(speed, 0.0D, 0.0D));
                    rocket.initialisePosition(new Vec3(8.0D, ALTITUDE, z));
                    level.addFreshEntity(rocket);
                    fired.set(rocket);
                    return (long) s.getTickCount();
                });
                int rocketId = fired.get().getId();

                List<String> samples = new ArrayList<>();
                double maxX = 0.0D;
                long goneAfter = -1L;
                int followed = 0;
                int watched = 0;
                for (int tick = 1; tick <= 300; tick++) {
                    context.waitTick();
                    double[] state = server.computeOnServer(s -> {
                        BulletEntity rocket = fired.get();
                        return new double[]{rocket.getX(), rocket.isRemoved() ? 1.0D : 0.0D, s.getTickCount() - launchTick};
                    });
                    boolean seen = context.computeOnClient(mc -> mc.level.getEntity(rocketId) != null || MissileTrailRenderer.farMissiles() > 0);
                    maxX = Math.max(maxX, state[0]);
                    if (state[1] > 0.0D) {
                        goneAfter = (long) state[2];
                        break;
                    }
                    watched++;
                    if (seen) {
                        followed++;
                    }
                    if (tick % 10 == 0) {
                        samples.add(String.format("  t%d: server tick +%d, x=%.1f, client follows it=%s", tick, (long) state[2], state[0], seen));
                    }
                }
                report.append(String.format("%s: flew to x=%.1f, gone %d server ticks after launch, client followed it %d of %d ticks%n",
                        weapon, maxX, goneAfter, followed, watched));
                for (String sample : samples) {
                    report.append(sample).append('\n');
                }
                double range = speed * stats.projectileLife();
                if (goneAfter < 0L) {
                    failures.append(String.format("%s: still in the air 300 ticks after launch, hung at x=%.1f%n", weapon, maxX));
                } else if (goneAfter > stats.projectileLife() + 8) {
                    failures.append(String.format("%s: took %d ticks over a %d tick flight%n", weapon, goneAfter, stats.projectileLife()));
                }
                if (maxX < range * 0.9D) {
                    failures.append(String.format("%s: only got %.1f blocks out of a %.0f block range%n", weapon, maxX, range));
                }
                if (followed < watched - 6) {
                    failures.append(String.format("%s: the client lost sight of it for %d of %d ticks%n", weapon, watched - followed, watched));
                }
                context.waitTicks(20);
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
