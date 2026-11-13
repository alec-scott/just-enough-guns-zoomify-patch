package ttv.migami.jeg.gametest;

import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;

/**
 * How tightly the A-10 turns on its wheels. A husk at the controls holds full right nose-wheel steering while the jet is
 * kept rolling at a set speed: a crawl, a brisk taxi, 36 km/h and nearly take-off speed (45 km/h). Fails if it turns faster than a
 * heavy jet should, if the turn snaps in rather than building up, or if it cannot be steered at all.
 */
public class A10TaxiClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private int groundedTicks;
    private int rolledTicks;
    private float maxRoll;

    /** Ground speed (blocks a tick), and the fastest and slowest steady turn allowed (degrees a tick). */
    private static final double[][] RUNS = {
            {0.12D, 0.95D, 0.25D},
            {0.25D, 0.95D, 0.5D},
            {0.5D, 0.95D, 0.4D},
            {0.62D, 0.8D, 0.3D}
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("a10 taxi report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a 0 -60 -30 0 20");
            // The runs roll up to 130 blocks, curving left; keep that ground simulated.
            server.runCommand("forceload add -112 -16 32 176");
            context.waitTicks(10);
            this.idleHolds(context, server, report, failures);
            for (double[] run : RUNS) {
                double speed = run[0];
                String jetId = server.computeOnServer(minecraftServer -> {
                    var level = minecraftServer.overworld();
                    VehicleEntity jet = ModEntities.A10.get().create(level, EntitySpawnReason.COMMAND);
                    jet.snapTo(0.5D, -60.0D, 0.5D, 0.0F, 0.0F);
                    level.addFreshEntity(jet);
                    var pilot = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
                    pilot.setNoAi(true);
                    pilot.setSilent(true);
                    pilot.snapTo(jet.getX(), jet.getY(), jet.getZ(), 0.0F, 0.0F);
                    level.addFreshEntity(pilot);
                    pilot.startRiding(jet, true, true);
                    return jet.getUUID().toString();
                });
                this.groundedTicks = 0;
                this.rolledTicks = 0;
                this.maxRoll = 0.0F;
                // Settle on the wheels, rolling straight.
                this.roll(context, server, jetId, speed, false, 20);
                float start = this.yaw(server, jetId);
                this.roll(context, server, jetId, speed, true, 10);
                float early = Mth.wrapDegrees(this.yaw(server, jetId) - start);
                this.roll(context, server, jetId, speed, true, 30);
                float settled = this.yaw(server, jetId);
                this.roll(context, server, jetId, speed, true, 40);
                double rate = Mth.wrapDegrees(this.yaw(server, jetId) - settled) / 40.0D;
                double radius = rate > 1.0E-3D ? speed / Math.toRadians(rate) : Double.POSITIVE_INFINITY;
                report.append(String.format(Locale.ROOT, "%.0f km/h: steady %.2f deg/tick (%.0f deg/s), radius %.1f blocks; first 10 ticks %.1f deg; on the ground %d of %d ticks, most roll %.1f%n",
                        speed * 72.0D, rate, rate * 20.0D, radius, early, this.groundedTicks, this.rolledTicks, this.maxRoll));
                if (this.groundedTicks < this.rolledTicks - 2) {
                    failures.append(String.format(Locale.ROOT, "left the ground while taxiing at %.0f km/h%n", speed * 72.0D));
                }
                if (rate > run[1]) {
                    failures.append(String.format(Locale.ROOT, "turns too fast at %.0f km/h (%.2f deg/tick, at most %.2f)%n", speed * 72.0D, rate, run[1]));
                }
                if (rate < run[2]) {
                    failures.append(String.format(Locale.ROOT, "barely steers at %.0f km/h (%.2f deg/tick, at least %.2f)%n", speed * 72.0D, rate, run[2]));
                }
                if (early > 10.0D * rate * 0.6D) {
                    failures.append(String.format(Locale.ROOT, "the turn snaps in at %.0f km/h (%.1f deg in the first 10 ticks)%n", speed * 72.0D, early));
                }
                server.runOnServer(minecraftServer -> {
                    var entity = minecraftServer.overworld().getEntity(UUID.fromString(jetId));
                    if (entity != null) {
                        entity.getPassengers().forEach(passenger -> passenger.discard());
                        entity.discard();
                    }
                });
                context.waitTicks(2);
            }
            // The weapon list, with the A-10's own icons: GAU-8, AGM-65D, AGM-65G.
            server.runCommand("execute as @p at @s run summon jeg:a10 ~ ~ ~20 {Rotation:[0f,0f]}");
            context.waitTicks(5);
            server.runCommand("ride @p mount @e[type=jeg:a10,limit=1,sort=nearest]");
            context.waitTicks(20);
            context.takeScreenshot("a10_weapon_icons");
        }
        LOGGER.info("{}", report);
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    /** A pilot aboard with the engines running at idle and the throttle closed: the jet stays where it is. */
    private void idleHolds(ClientGameTestContext context, TestServerContext server, StringBuilder report, StringBuilder failures) {
        String jetId = server.computeOnServer(minecraftServer -> {
            var level = minecraftServer.overworld();
            VehicleEntity jet = ModEntities.A10.get().create(level, EntitySpawnReason.COMMAND);
            jet.snapTo(0.5D, -60.0D, 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(jet);
            var pilot = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
            pilot.setNoAi(true);
            pilot.setSilent(true);
            pilot.snapTo(jet.getX(), jet.getY(), jet.getZ(), 0.0F, 0.0F);
            level.addFreshEntity(pilot);
            pilot.startRiding(jet, true, true);
            return jet.getUUID().toString();
        });
        double[] info = new double[2];
        for (int tick = 0; tick < 320; tick++) {
            server.runOnServer(minecraftServer -> {
                if (minecraftServer.overworld().getEntity(UUID.fromString(jetId)) instanceof VehicleEntity jet) {
                    jet.setAiVehicleInput(new VehicleInput(false, false, false, false, false, false, false, false, false, false,
                            false, false, -1, false, false, 0.0F, 0.0F));
                }
            });
            if (tick >= 120 && tick % 40 == 0) {
                String at = server.computeOnServer(minecraftServer -> {
                    var jet = minecraftServer.overworld().getEntity(UUID.fromString(jetId));
                    return String.format(Locale.ROOT, "idle t%d pos %.3f %.3f %.3f v %.4f %.4f %.4f", 0, jet.getX(), jet.getY(), jet.getZ(),
                            jet.getDeltaMovement().x, jet.getDeltaMovement().y, jet.getDeltaMovement().z);
                });
                LOGGER.info("{} (tick {})", at, tick);
            }
            if (tick == 120) {
                // The engines are at idle by now; measure from here.
                info[0] = server.computeOnServer(minecraftServer -> minecraftServer.overworld().getEntity(UUID.fromString(jetId)).getZ());
            }
            context.waitTick();
        }
        double moved = server.computeOnServer(minecraftServer -> minecraftServer.overworld().getEntity(UUID.fromString(jetId)).getZ()) - info[0];
        float spool = server.computeOnServer(minecraftServer -> ((VehicleEntity) minecraftServer.overworld().getEntity(UUID.fromString(jetId))).jetSpool());
        report.append(String.format(Locale.ROOT, "idle, throttle closed: spool %.2f, moved %.2f blocks in 10 seconds%n", spool, moved));
        if (spool < 0.07F) {
            failures.append("the engines never reached idle" + System.lineSeparator());
        }
        if (Math.abs(moved) > 0.5D) {
            failures.append(String.format(Locale.ROOT, "creeps forward at idle with the throttle closed (%.2f blocks)%n", moved));
        }
        server.runOnServer(minecraftServer -> {
            var entity = minecraftServer.overworld().getEntity(UUID.fromString(jetId));
            entity.getPassengers().forEach(passenger -> passenger.discard());
            entity.discard();
        });
        context.waitTicks(2);
    }

    /** Keeps the jet rolling at the speed along its nose, the pilot holding right steering or not, for some ticks. */
    private void roll(ClientGameTestContext context, TestServerContext server, String jetId, double speed, boolean right, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            server.runOnServer(minecraftServer -> {
                if (minecraftServer.overworld().getEntity(UUID.fromString(jetId)) instanceof VehicleEntity jet) {
                    this.rolledTicks++;
                    if (jet.onGround()) {
                        this.groundedTicks++;
                    }
                    this.maxRoll = Math.max(this.maxRoll, Math.abs(jet.roll()));
                    jet.setAiVehicleInput(new VehicleInput(false, false, false, right, false, false, false, false, false, false,
                            false, false, -1, false, false, 0.0F, 0.0F));
                    double yaw = Math.toRadians(jet.getYRot());
                    jet.setDeltaMovement(new Vec3(-Math.sin(yaw) * speed, jet.getDeltaMovement().y, Math.cos(yaw) * speed));
                }
            });
            context.waitTick();
        }
    }

    private float yaw(TestServerContext server, String jetId) {
        return server.computeOnServer(minecraftServer ->
                minecraftServer.overworld().getEntity(UUID.fromString(jetId)) instanceof VehicleEntity jet ? jet.getYRot() : Float.NaN);
    }
}
