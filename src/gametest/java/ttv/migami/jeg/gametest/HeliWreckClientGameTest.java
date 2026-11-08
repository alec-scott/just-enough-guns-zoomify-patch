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
 * A damaged helicopter smokes, sparks and burns more as its hull goes; a destroyed one does not vanish but goes down
 * spinning and burning, blows up where it hits, throws out everything aboard, burns as a charred hulk and only then
 * breaks apart. One killed on the ground cooks off first.
 */
public class HeliWreckClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int GROUND = -60;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("heli wreck report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule show_advancement_messages false");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

            // Damage stages, on the ground in front of the camera.
            server.runCommand("tp @a -3 " + GROUND + " -13 -12 -4");
            server.runCommand("summon jeg:ah6 0.5 " + GROUND + " 0.5");
            context.waitTicks(30);
            float max = server.computeOnServer(s -> this.vehicle(s, 0).maxVehicleHealth());
            for (float stage : new float[]{0.5F, 0.25F, 0.1F}) {
                server.runOnServer(s -> {
                    VehicleEntity vehicle = this.vehicle(s, 0);
                    vehicle.hurtWithRepairTool(s.overworld().damageSources().generic(), vehicle.vehicleHealth() - max * stage);
                });
                context.waitTicks(40);
                context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
                context.takeScreenshot("heli_damage_" + Math.round(stage * 100));
            }

            // Destroyed in the air with cargo aboard.
            server.runCommand("tp @a 40 " + GROUND + " -22 0 -30");
            context.waitTicks(10);
            server.runCommand("summon jeg:ah6 40.5 " + (GROUND + 30) + " 0.5");
            context.waitTicks(2);
            double killHeight = server.computeOnServer(s -> {
                VehicleEntity vehicle = this.vehicle(s, 40);
                vehicle.vehicleInventory().setItem(0, new ItemStack(Items.DIAMOND, 16));
                vehicle.vehicleInventory().setItem(1, new ItemStack(Items.GOLD_INGOT, 32));
                vehicle.hurtWithRepairTool(s.overworld().damageSources().generic(), vehicle.maxVehicleHealth() * 2.0F);
                return vehicle.getY() - GROUND;
            });
            report.append(String.format("airborne kill: destroyed %.1f blocks up%n", killHeight));
            context.waitTicks(2);
            int justAfter = server.computeOnServer(s -> this.vehicle(s, 40) == null ? -1 : this.vehicle(s, 40).wreckState());
            report.append("airborne kill: state right after ").append(justAfter).append('\n');
            if (justAfter != VehicleEntity.WRECK_GOING_DOWN) {
                failures.append("a helicopter destroyed in the air did not go down as a wreck (state ").append(justAfter).append(")\n");
            }
            context.waitTicks(15);
            context.takeScreenshot("heli_wreck_going_down");
            int ticks = 0;
            while (ticks < 260 && server.computeOnServer(s -> this.vehicle(s, 40) != null && this.vehicle(s, 40).wreckState() == VehicleEntity.WRECK_GOING_DOWN)) {
                context.waitTick();
                ticks++;
            }
            double crashY = server.computeOnServer(s -> this.vehicle(s, 40) == null ? Double.NaN : this.vehicle(s, 40).getY());
            int fallTicks = ticks + 17;
            report.append(String.format("airborne kill: came down and blew up %d ticks after it was hit, %.1f above the ground%n", fallTicks, crashY - GROUND));
            if (killHeight < 20.0D || fallTicks < 15) {
                failures.append(String.format("the wreck did not fall: hit %.1f up, down after %d ticks%n", killHeight, fallTicks));
            }
            context.waitTicks(10);
            context.takeScreenshot("heli_wreck_blast");
            context.waitTicks(60);
            context.takeScreenshot("heli_wreck_burning");
            int[] cargo = server.computeOnServer(s -> new int[]{this.dropped(s, 40, Items.DIAMOND.getDefaultInstance()), this.dropped(s, 40, Items.GOLD_INGOT.getDefaultInstance())});
            report.append(String.format("cargo thrown clear: %d diamonds, %d gold ingots%n", cargo[0], cargo[1]));
            if (cargo[0] != 16 || cargo[1] != 32) {
                failures.append(String.format("cargo lost in the crash: %d/16 diamonds, %d/32 gold%n", cargo[0], cargo[1]));
            }
            if (!(crashY - GROUND < 3.0D)) {
                failures.append(String.format("the wreck blew up %.1f blocks up, not where it hit the ground%n", crashY - GROUND));
            }
            context.waitTicks(160);
            boolean gone = server.computeOnServer(s -> this.vehicle(s, 40) == null);
            report.append("burnt out and broke apart: ").append(gone).append('\n');
            if (!gone) {
                failures.append("the burning wreck never broke apart\n");
            }

            // Destroyed on the ground: cooks off, then goes up.
            server.runCommand("tp @a 80 " + GROUND + " -12 0 10");
            server.runCommand("summon jeg:mi28 80.5 " + GROUND + " 0.5");
            context.waitTicks(20);
            server.runOnServer(s -> {
                VehicleEntity vehicle = this.vehicle(s, 80);
                vehicle.hurtWithRepairTool(s.overworld().damageSources().generic(), vehicle.maxVehicleHealth() * 2.0F);
            });
            context.waitTicks(15);
            int cooking = server.computeOnServer(s -> this.vehicle(s, 80) == null ? -1 : this.vehicle(s, 80).wreckState());
            context.takeScreenshot("heli_wreck_cook_off");
            context.waitTicks(30);
            int blown = server.computeOnServer(s -> this.vehicle(s, 80) == null ? -1 : this.vehicle(s, 80).wreckState());
            report.append(String.format("ground kill: state %d while cooking off, %d after%n", cooking, blown));
            if (cooking != VehicleEntity.WRECK_GOING_DOWN || blown != VehicleEntity.WRECK_BURNING) {
                failures.append(String.format("ground kill did not cook off and then burn (states %d, %d)%n", cooking, blown));
            }
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    private VehicleEntity vehicle(MinecraftServer server, int x) {
        var found = server.overworld().getEntitiesOfClass(VehicleEntity.class, new AABB(x - 15, GROUND - 5, -15, x + 15, GROUND + 40, 15));
        return found.isEmpty() ? null : found.getFirst();
    }

    private int dropped(MinecraftServer server, int x, ItemStack kind) {
        int count = 0;
        for (ItemEntity item : server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(x - 15, GROUND - 5, -15, x + 15, GROUND + 40, 15))) {
            if (ItemStack.isSameItem(item.getItem(), kind)) {
                count += item.getItem().getCount();
            }
        }
        return count;
    }
}
