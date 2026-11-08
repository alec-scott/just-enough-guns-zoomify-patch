package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * A charging station tops up a drained helicopter parked well out on the pad around it, and one set down on a roof above
 * it, but not one parked out of range.
 */
public class ChargingStationRangeClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int GROUND = -60;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("charging station range report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a 30 " + GROUND + " -20");
            context.waitTicks(20);
            // Out on the pad, out of range, and on a roof five blocks up.
            this.setUp(server, 0, 7, 0);
            this.setUp(server, 40, 12, 0);
            server.runCommand("fill 80 " + (GROUND + 4) + " 0 84 " + (GROUND + 4) + " 4 minecraft:stone");
            this.setUp(server, 80, 2, 5);
            context.waitTicks(10);
            int[] before = {this.energy(server, 0), this.energy(server, 40), this.energy(server, 80)};
            context.waitTicks(70);
            int[] after = {this.energy(server, 0), this.energy(server, 40), this.energy(server, 80)};
            String[] cases = {"7 blocks out on the pad", "12 blocks out", "on a roof 5 blocks up"};
            boolean[] shouldCharge = {true, false, true};
            for (int i = 0; i < cases.length; i++) {
                report.append(String.format("%s: energy %d -> %d%n", cases[i], before[i], after[i]));
                if (before[i] < 0) {
                    failures.append(cases[i]).append(": no helicopter\n");
                } else if (shouldCharge[i] && after[i] <= before[i]) {
                    failures.append(cases[i]).append(": was not charged\n");
                } else if (!shouldCharge[i] && after[i] > before[i]) {
                    failures.append(cases[i]).append(": was charged from out of range\n");
                }
            }
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    /** A station at (x, ground, 0) and a half-drained AH-6 dx blocks east of it, dy blocks up. */
    private void setUp(TestServerContext server, int x, int dx, int dy) {
        server.runCommand("setblock " + x + " " + GROUND + " 0 jeg:vehicle_charging_station");
        server.runCommand("summon jeg:ah6 " + (x + dx + 0.5D) + " " + (GROUND + dy) + " 0.5");
        server.runOnServer(s -> {
            for (VehicleEntity vehicle : this.vehicles(s, x)) {
                vehicle.consumeEnergy(vehicle.vehicleEnergy() / 2);
            }
        });
    }

    private int energy(TestServerContext server, int x) {
        return server.computeOnServer(s -> {
            var vehicles = this.vehicles(s, x);
            return vehicles.isEmpty() ? -1 : vehicles.getFirst().vehicleEnergy();
        });
    }

    private java.util.List<VehicleEntity> vehicles(net.minecraft.server.MinecraftServer server, int x) {
        return server.overworld().getEntitiesOfClass(VehicleEntity.class, new AABB(x - 1, GROUND - 5, -5, x + 20, GROUND + 15, 5));
    }
}
