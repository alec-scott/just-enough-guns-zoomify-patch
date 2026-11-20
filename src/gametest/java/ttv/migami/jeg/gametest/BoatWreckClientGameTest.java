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
 * A destroyed boat sinks instead of vanishing (READINESS: "boats sink as a wreck"). The speedboat on a glass-walled pool
 * seven blocks deep: it burns on the water, its fuel goes up and throws out what was aboard, then it goes under stern
 * first (bow up), sinks to the bottom and breaks up there.
 */
public class BoatWreckClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    /** The pool's water fills y -63 to -56: its surface is at -55, its floor (bedrock) at -63. */
    private static final double SURFACE = -55.0D, FLOOR = -63.0D;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("boat wreck report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
            server.runCommand("time set noon");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("fill -10 -63 -10 10 -56 10 minecraft:glass");
            server.runCommand("fill -9 -63 -9 9 -56 9 minecraft:water");
            // Above the pool's near rim, looking down at the boat in the middle.
            server.runCommand("tp @a 0.5 -50 -8.5 0 35");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runCommand("summon jeg:speedboat 0.5 -55.2 0.5");
            context.waitTicks(60);
            double floated = server.computeOnServer(s -> this.boat(s) == null ? Double.NaN : this.boat(s).getY());
            server.runOnServer(s -> {
                VehicleEntity boat = this.boat(s);
                boat.vehicleInventory().setItem(0, new ItemStack(Items.DIAMOND, 16));
                boat.hurtWithRepairTool(s.overworld().damageSources().generic(), boat.maxVehicleHealth() * 2.0F);
            });
            context.waitTicks(15);
            int burningAfloat = this.state(server);
            context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
            context.takeScreenshot("boat_wreck_burning_afloat");
            context.waitTicks(30);
            int afterBlast = this.state(server);
            int diamonds = server.computeOnServer(this::dropped);
            double lowest = Double.MAX_VALUE, bowUp = 0.0D;
            int underAt = -1, bottomAt = -1, goneAt = -1;
            boolean sinkingShot = false;
            for (int tick = 0; tick < 900; tick += 5) {
                context.waitTicks(5);
                double[] pose = server.computeOnServer(s -> {
                    VehicleEntity b = this.boat(s);
                    return b == null ? null : new double[]{b.getY(), b.getXRot(), b.onGround() ? 1 : 0, b.wreckSubmerged() ? 1 : 0};
                });
                if (pose == null) {
                    goneAt = tick;
                    break;
                }
                lowest = Math.min(lowest, pose[0]);
                bowUp = Math.min(bowUp, pose[1]);
                if (underAt < 0 && pose[3] > 0) underAt = tick;
                if (bottomAt < 0 && pose[2] > 0 && pose[0] < SURFACE - 3.0D) bottomAt = tick;
                if (!sinkingShot && pose[0] < SURFACE - 2.5D) {
                    // Under water in a corner of the pool, looking at the hull going down (from above, shader water
                    // hides it).
                    server.runCommand("tp @a -6.5 -59 -6.5 -45 -5");
                    context.waitTicks(3);
                    context.takeScreenshot("boat_wreck_sinking");
                    sinkingShot = true;
                }
            }
            report.append(String.format("floated at %.2f; states %d then %d; %d diamonds thrown out; under at +%d, on the bottom at +%d (lowest %.2f), bow up to %.1f deg; gone at +%d%n",
                    floated, burningAfloat, afterBlast, diamonds, underAt, bottomAt, lowest, bowUp, goneAt));
            if (burningAfloat != VehicleEntity.WRECK_GOING_DOWN || afterBlast != VehicleEntity.WRECK_BURNING) {
                failures.append(String.format("the boat did not burn and then blow up (states %d, %d)%n", burningAfloat, afterBlast));
            }
            if (diamonds != 16) failures.append("the wreck did not throw out what was aboard (").append(diamonds).append(")\n");
            if (underAt < 0) failures.append("the wreck never went under\n");
            if (bottomAt < 0 || lowest > FLOOR + 1.5D) failures.append("the wreck did not sink to the bottom\n");
            if (bowUp > -5.0D) failures.append("the wreck did not go down by the stern (bow up)\n");
            if (goneAt < 0) failures.append("the sunk wreck never broke up\n");
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            LOGGER.error("[boat-wreck] FAILED: {}", failures);
            throw new AssertionError(failures.toString() + report);
        }
        LOGGER.info("[boat-wreck] COMPLETE");
    }

    private int state(TestServerContext server) {
        return server.computeOnServer(s -> this.boat(s) == null ? -1 : this.boat(s).wreckState());
    }

    private VehicleEntity boat(MinecraftServer server) {
        var found = server.overworld().getEntitiesOfClass(VehicleEntity.class, new AABB(-15, -70, -15, 15, -40, 15));
        return found.isEmpty() ? null : found.getFirst();
    }

    private int dropped(MinecraftServer server) {
        int count = 0;
        for (ItemEntity item : server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(-20, -70, -20, 20, -30, 20))) {
            if (item.getItem().is(Items.DIAMOND)) count += item.getItem().getCount();
        }
        return count;
    }
}
