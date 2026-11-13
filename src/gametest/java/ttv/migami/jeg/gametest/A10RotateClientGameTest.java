package ttv.migami.jeg.gametest;

import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;

/**
 * Hauling the nose up on the runway. A husk at the controls pulls full back stick while the jet is kept rolling at a
 * taxi speed (below rotate speed) and at rotate speed. Fails if that damages or wrecks the jet: too slow, the nose must
 * stay down (or the jet settle back unharmed); fast enough, it lifts off and climbs away.
 */
public class A10RotateClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    /** Ground speed (blocks a tick) held while the stick is pulled, and whether the jet should be flying by the end. */
    private static final double[][] RUNS = {
            {0.40D, 0},
            {0.55D, 0},
            {0.62D, 0},
            {0.74D, 0},
            {0.80D, 1}
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("a10 rotate report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a 0 -60 -30 0 20");
            server.runCommand("forceload add -32 -16 32 240");
            context.waitTicks(10);
            for (double[] run : RUNS) {
                double speed = run[0];
                boolean shouldFly = run[1] > 0.5D;
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
                // Engines up and rolling straight first.
                this.roll(context, server, jetId, speed, false, 160, null);
                StringBuilder trace = new StringBuilder();
                double[] worst = {0.0D, 0.0D, 0.0D};
                this.roll(context, server, jetId, speed, true, 80, (jet, tick) -> {
                    worst[0] = Math.min(worst[0], jet.getXRot());
                    worst[1] = Math.max(worst[1], jet.getY() + 60.0D);
                    if (tick % 4 == 0 || jet.vehicleHealth() < jet.maxVehicleHealth()) {
                        trace.append(String.format(Locale.ROOT, "  t%d pitch %.1f y %.2f vy %.3f speed %.3f ground %s hp %.0f wreck %s%n", tick,
                                jet.getXRot(), jet.getY() + 60.0D, jet.getDeltaMovement().y, jet.getDeltaMovement().length(), jet.onGround(),
                                jet.vehicleHealth(), jet.isWrecked()));
                    }
                });
                this.roll(context, server, jetId, 0.0D, false, 120, (jet, tick) -> {
                    if (tick % 10 == 0) {
                        trace.append(String.format(Locale.ROOT, "  after t%d pitch %.1f y %.2f speed %.3f hp %.0f%n", tick, jet.getXRot(),
                                jet.getY() + 60.0D, jet.getDeltaMovement().length(), jet.vehicleHealth()));
                    }
                });
                String end = server.computeOnServer(minecraftServer -> {
                    var entity = minecraftServer.overworld().getEntity(UUID.fromString(jetId));
                    if (!(entity instanceof VehicleEntity jet)) {
                        return "gone";
                    }
                    return String.format(Locale.ROOT, "hp %.0f/%.0f wreck %s height %.1f", jet.vehicleHealth(), jet.maxVehicleHealth(), jet.isWrecked(), jet.getY() + 60.0D);
                });
                report.append(String.format(Locale.ROOT, "%.0f km/h, stick back 4 s: most nose-up %.1f, highest %.1f; end %s%n",
                        speed * 72.0D, -worst[0], worst[1], end));
                LOGGER.info("rotate {} km/h trace\n{}", Math.round(speed * 72.0D), trace);
                if (end.equals("gone") || end.contains("wreck true") || !end.startsWith("hp 180/180")) {
                    failures.append(String.format(Locale.ROOT, "pulling the nose up at %.0f km/h damaged the jet (%s)%n", speed * 72.0D, end));
                }
                if (shouldFly && worst[1] < 3.0D) {
                    failures.append(String.format(Locale.ROOT, "at %.0f km/h, rotate speed, it did not lift off%n", speed * 72.0D));
                }
                if (!shouldFly && -worst[0] > 1.0D) {
                    failures.append(String.format(Locale.ROOT, "below rotate speed (%.0f km/h) the nose came up %.1f degrees%n", speed * 72.0D, -worst[0]));
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
        }
        LOGGER.info("{}", report);
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    private interface Probe {
        void sample(VehicleEntity jet, int tick);
    }

    /**
     * Keeps the jet rolling at the speed along the ground (its climb left alone) for some ticks, the pilot pulling the
     * stick fully back or not; full throttle throughout.
     */
    private void roll(ClientGameTestContext context, TestServerContext server, String jetId, double speed, boolean pull, int ticks, Probe probe) {
        for (int tick = 0; tick < ticks; tick++) {
            int at = tick;
            server.runOnServer(minecraftServer -> {
                if (minecraftServer.overworld().getEntity(UUID.fromString(jetId)) instanceof VehicleEntity jet) {
                    jet.setAiVehicleInput(new VehicleInput(true, false, false, false, false, false, false, false, false, false,
                            false, false, -1, false, false, 0.0F, pull ? -3.0F : 0.0F));
                    if (speed > 0.0D && (jet.onGround() || !pull)) {
                        double yaw = Math.toRadians(jet.getYRot());
                        jet.setDeltaMovement(new Vec3(-Math.sin(yaw) * speed, jet.getDeltaMovement().y, Math.cos(yaw) * speed));
                    }
                    if (probe != null) {
                        probe.sample(jet, at);
                    }
                }
            });
            context.waitTick();
        }
    }
}
