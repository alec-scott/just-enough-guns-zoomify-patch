package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Land vehicles and guns, destroyed, no longer vanish: they cook off where they stand (smoke and sparks), go up, throw out
 * what was aboard, and burn as a charred hulk before breaking apart, as a helicopter killed on the ground does. They stay
 * upright and in place throughout (no helicopter spin). Boats sink instead (BoatWreckClientGameTest).
 */
public class GroundWreckClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int GROUND = -60;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("ground wreck report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule show_advancement_messages false");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

            for (String id : new String[]{"bmp2", "mk42", "sentry", "hpj11"}) {
                int x = switch (id) { case "bmp2" -> 0; case "mk42" -> 60; case "sentry" -> 120; default -> 180; };
                server.runCommand("tp @a " + (x - 4) + " " + GROUND + " -14 -12 -2");
                server.runCommand("summon jeg:" + id + " " + (x + 0.5) + " " + GROUND + " 0.5");
                context.waitTicks(30);
                float yaw = server.computeOnServer(s -> {
                    VehicleEntity vehicle = this.vehicle(s, x);
                    vehicle.vehicleInventory().setItem(0, new ItemStack(Items.DIAMOND, 16));
                    float before = vehicle.getYRot();
                    vehicle.hurtWithRepairTool(s.overworld().damageSources().generic(), vehicle.maxVehicleHealth() * 2.0F);
                    return before;
                });
                context.waitTicks(15);
                int cooking = server.computeOnServer(s -> this.vehicle(s, x) == null ? -1 : this.vehicle(s, x).wreckState());
                context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
                context.takeScreenshot("ground_wreck_" + id + "_cook_off");
                context.waitTicks(30);
                int burning = server.computeOnServer(s -> this.vehicle(s, x) == null ? -1 : this.vehicle(s, x).wreckState());
                double[] pose = server.computeOnServer(s -> {
                    VehicleEntity v = this.vehicle(s, x);
                    return v == null ? new double[]{Double.NaN, 0, 0, 0} : new double[]{v.getX() - (x + 0.5), v.getZ() - 0.5, v.getYRot() - yaw, v.getXRot()};
                });
                int diamonds = server.computeOnServer(s -> this.dropped(s, x));
                context.waitTicks(20);
                context.takeScreenshot("ground_wreck_" + id + "_burning");
                boolean gone = false;
                for (int i = 0; i < 16 && !gone; i++) {
                    context.waitTicks(20);
                    gone = server.computeOnServer(s -> this.vehicle(s, x) == null);
                }
                report.append(String.format("%s: state %d cooking off, %d after; moved %.2f, %.2f; turned %.1f, pitch %.1f; %d diamonds thrown out; broke apart %s%n",
                        id, cooking, burning, pose[0], pose[1], pose[2], pose[3], diamonds, gone));
                if (cooking != VehicleEntity.WRECK_GOING_DOWN || burning != VehicleEntity.WRECK_BURNING) {
                    failures.append(String.format("%s did not cook off and then burn (states %d, %d)%n", id, cooking, burning));
                }
                if (Double.isNaN(pose[0]) || Math.abs(pose[0]) > 0.3D || Math.abs(pose[1]) > 0.3D || Math.abs(pose[2]) > 1.0D || Math.abs(pose[3]) > 1.0D) {
                    failures.append(id).append(" wreck moved or spun as an aircraft's does\n");
                }
                if (diamonds != 16) failures.append(id).append(" wreck did not throw out what was aboard\n");
                if (!gone) failures.append(id).append(" burning wreck never broke apart\n");
            }
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            LOGGER.error("[ground-wreck] FAILED: {}", failures);
            throw new AssertionError(failures.toString() + report);
        }
        LOGGER.info("[ground-wreck] COMPLETE");
    }

    private VehicleEntity vehicle(MinecraftServer server, int x) {
        var found = server.overworld().getEntitiesOfClass(VehicleEntity.class, new AABB(x - 15, GROUND - 5, -15, x + 15, GROUND + 20, 15));
        return found.isEmpty() ? null : found.getFirst();
    }

    private int dropped(MinecraftServer server, int x) {
        int count = 0;
        for (ItemEntity item : server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(x - 15, GROUND - 5, -15, x + 15, GROUND + 20, 15))) {
            if (item.getItem().is(Items.DIAMOND)) count += item.getItem().getCount();
        }
        return count;
    }
}
