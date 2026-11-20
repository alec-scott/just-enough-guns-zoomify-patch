package ttv.migami.jeg.gametest;

import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;
import ttv.migami.jeg.vehicle.terrain.BulldozerTerrainWork;

/**
 * The BD-40's lane lock and the ripper's restock (user request 2026-11-17: "only go straight aligned with the blocks so
 * you can pave a straight path", "if the ripper runs out of blocks it pulls more from your inventory").
 * <p>
 * Started 7 degrees off and 0.3 blocks off the grid, the lock squares the machine to south and pulls it onto the block
 * edge at x 0, so the ripper paves the same four whole columns (x -2..1) down the whole road. The bay holds only 6
 * blocks and the operator carries three stacks: the ripper must restock from the inventory and keep paving without a gap. A tap
 * of D is one quarter turn, and the next stretch stays on its own lane line.
 */
public class BulldozerLaneLockClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private String id;

    @Override public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("time set noon");
            server.runCommand("gamemode creative @a");
            server.runCommand("fill -40 " + G + " -6 8 " + (G + 12) + " 40 air");
            server.runCommand("fill -40 " + (G - 1) + " -6 8 " + (G - 1) + " 40 grass_block");
            server.runOnServer(s -> {
                var level = s.overworld();
                BulldozerEntity dozer = ModEntities.BULLDOZER.get().create(level, EntitySpawnReason.COMMAND);
                dozer.snapTo(.3, G, 0, 7, 0);
                dozer.addEnergy(dozer.maxVehicleEnergy());
                level.addFreshEntity(dozer);
                var player = s.getPlayerList().getPlayers().getFirst();
                player.getInventory().clearContent();
                for (int slot = 3; slot < 6; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE_BRICKS, 64));
                player.startRiding(dozer, true, true);
                dozer.vehicleInventory().clearContent();
                dozer.vehicleInventory().setItem(BulldozerTerrainWork.pavingStart(dozer), new ItemStack(Items.STONE_BRICKS, 6));
                this.id = dozer.getUUID().toString();
            });
            context.waitTicks(BulldozerEntity.START_TICKS + 20);
            context.runOnClient(mc -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setYRot(0); mc.player.setXRot(25); });

            // Lane lock on, ripper down.
            context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitTicks(4);
            if (!server.computeOnServer(s -> dozer(s).laneLock())) failures.append("X did not turn the lane lock on\n");
            if (server.computeOnServer(s -> Math.abs(Mth.wrapDegrees(dozer(s).laneYaw()))) > .01F) failures.append("the lock did not pick south (the nearest quarter)\n");
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(22);
            context.takeScreenshot("bulldozer_lane_lock_hud");

            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(130);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(10);
            context.takeScreenshot("bulldozer_lane_lock_road");
            double[] a = server.computeOnServer(s -> { var d = dozer(s); return new double[]{d.getX(), d.getZ(), Mth.wrapDegrees(d.getYRot())}; });
            LOG.info("[dozer-lane] after the first stretch: x {} z {} yaw {}", a[0], a[1], a[2]);
            if (Math.abs(a[2]) > .01) failures.append("heading not square to south: ").append(a[2]).append('\n');
            if (Math.abs(a[0]) > .03) failures.append("not on the lane line x 0: x ").append(a[0]).append('\n');
            if (a[1] < 14) failures.append("travelled only to z ").append(a[1]).append('\n');
            // The road: four whole columns, x -2..1, on every row once the machine has squared up (it starts 7 degrees off).
            int last = (int) Math.floor(a[1]) - 4;
            String rows = server.computeOnServer(s -> {
                StringBuilder out = new StringBuilder();
                for (int z = 4; z <= last; z++) {
                    for (int col = -4; col <= 4; col++)
                        out.append(s.overworld().getBlockState(new BlockPos(col, G - 1, z)).is(Blocks.STONE_BRICKS) ? '#' : '.');
                    out.append(' ');
                }
                return out.toString();
            });
            LOG.info("[dozer-lane] road rows z 4..{}: {}", last, rows);
            for (String row : rows.trim().split(" ")) {
                if (!row.equals("..####...")) {
                    failures.append("road not four straight columns x -2..1: ").append(rows).append('\n');
                    break;
                }
            }
            int[] stock = server.computeOnServer(s -> {
                var d = dozer(s);
                int carried = 0;
                for (ItemStack stack : s.getPlayerList().getPlayers().getFirst().getInventory().getNonEquipmentItems())
                    if (stack.is(Items.STONE_BRICKS)) carried += stack.getCount();
                return new int[]{carried, BulldozerTerrainWork.pavingCount(d)};
            });
            LOG.info("[dozer-lane] stone bricks carried {} in the bay {}", stock[0], stock[1]);
            if (stock[0] >= 192) failures.append("the ripper did not restock from the inventory (still carried: ").append(stock[0]).append(")\n");
            if (stock[0] % 64 != 0) failures.append("a restock took a part stack: ").append(stock[0]).append(" carried\n");
            if (rows.length() / 10 < 8) failures.append("too short a road to show the restock\n");

            // One tap of D: a quarter turn to the west, then a straight run on that lane's own line.
            server.runCommand("give @p stone_bricks 64");
            context.getInput().holdKey(options -> options.keyRight);
            context.waitTicks(3);
            context.getInput().releaseKey(options -> options.keyRight);
            context.waitTicks(40);
            float yaw = server.computeOnServer(s -> Mth.wrapDegrees(dozer(s).getYRot()));
            if (Math.abs(Mth.wrapDegrees(yaw - 90)) > .01F) failures.append("a tap of D did not make one quarter turn: yaw ").append(yaw).append('\n');
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(70);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(10);
            double[] b = server.computeOnServer(s -> { var d = dozer(s); return new double[]{d.getX(), d.getZ()}; });
            LOG.info("[dozer-lane] after the turn: x {} z {}", b[0], b[1]);
            if (Math.abs(b[1] - Math.round(b[1])) > .03) failures.append("west run not on a block edge: z ").append(b[1]).append('\n');
            if (b[0] > a[0] - 8) failures.append("west run too short: x ").append(b[0]).append('\n');
            context.takeScreenshot("bulldozer_lane_lock_turned");

            context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitTicks(4);
            if (server.computeOnServer(s -> dozer(s).laneLock())) failures.append("X did not turn the lane lock off\n");
            server.runOnServer(s -> dozer(s).setDeltaMovement(Vec3.ZERO));
        } finally {
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().releaseKey(options -> options.keyRight);
        }
        if (failures.length() > 0) {
            LOG.error("[dozer-lane] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[dozer-lane] COMPLETE: squared up, held the lane, restocked, quarter turn");
    }

    private BulldozerEntity dozer(MinecraftServer server) {
        return (BulldozerEntity) server.overworld().getEntity(UUID.fromString(this.id));
    }
}
