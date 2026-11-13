package ttv.migami.jeg.gametest;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.projectile.VehicleDecoyEntity;

/**
 * A salvo of countermeasure flares, watched from the ground by day and by night: their burning light (drawn), smoke
 * trails and sparks. Fails unless the flares fly, burn bright while they fly and burn out at the end of their life.
 */
public class A10FlaresClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        StringBuilder report = new StringBuilder("a10 flares report\n");
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 -60 0 0 -22");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
            });
            context.waitTicks(10);
            for (String time : new String[]{"noon", "midnight"}) {
                server.runCommand("time set " + time);
                int fired = this.salvo(server);
                context.waitTicks(12);
                context.takeScreenshot("a10_flares_" + time + "_a");
                context.waitTicks(20);
                context.takeScreenshot("a10_flares_" + time + "_b");
                float burn = this.brightest(context);
                report.append(String.format(Locale.ROOT, "%s: %d fired, brightest %.2f a second and a half on%n", time, fired, burn));
                if (fired < 6 || burn < 0.9F) {
                    failures.append(time).append(": the salvo did not fly and burn brightly").append(System.lineSeparator());
                }
                context.waitTicks(200);
                int left = context.computeOnClient(mc -> mc.level.getEntitiesOfClass(VehicleDecoyEntity.class,
                        mc.player.getBoundingBox().inflate(300.0D)).size());
                report.append(String.format(Locale.ROOT, "%s: %d still burning 11.6 s on%n", time, left));
                if (left > 0) {
                    failures.append(time).append(": flares did not burn out").append(System.lineSeparator());
                }
            }
        } finally {
            context.runOnClient(mc -> mc.options.hideGui = false);
        }
        LOGGER.info("{}", report);
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    /** Six flares out of a point 40 blocks ahead and 25 up, spreading sideways and upward like a jet's salvo. */
    private int salvo(TestServerContext server) {
        return server.computeOnServer(minecraftServer -> {
            var level = minecraftServer.overworld();
            var player = minecraftServer.getPlayerList().getPlayers().getFirst();
            Vec3 from = new Vec3(0.5D, -35.0D, 40.5D);
            int count = 0;
            for (int i = 0; i < 6; i++) {
                double side = i % 2 == 0 ? 1.0D : -1.0D;
                Vec3 direction = new Vec3(side * (0.6D + 0.15D * i), 0.5D, 0.3D);
                VehicleDecoyEntity flare = VehicleDecoyEntity.flare(level, player, from, direction, 0.7F, 6.0F);
                if (level.addFreshEntity(flare)) {
                    count++;
                }
            }
            return count;
        });
    }

    private float brightest(ClientGameTestContext context) {
        return context.computeOnClient(mc -> {
            float best = 0.0F;
            for (VehicleDecoyEntity flare : mc.level.getEntitiesOfClass(VehicleDecoyEntity.class, mc.player.getBoundingBox().inflate(300.0D))) {
                best = Math.max(best, flare.flareBurn(1.0F));
            }
            return best;
        });
    }
}
