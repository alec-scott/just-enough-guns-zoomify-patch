package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModBlocks;
import ttv.migami.jeg.vehicle.block.VehicleAssemblingTableBlock;

/**
 * Mines the vehicle charging station and a full multi-block assembling table with a pickaxe in survival: each must
 * come back as exactly one item, and the whole table must go. A TNT blast on a table must not drop more than one.
 */
public class StationDropsClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder();
        boolean ok = true;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0 ~ 0 0 0");
            server.runCommand("item replace entity @p weapon.mainhand with minecraft:iron_pickaxe");
            context.waitTicks(10);

            server.runCommand("execute as @p at @s run setblock ~3 ~ ~3 jeg:vehicle_charging_station");
            context.waitTicks(5);
            server.runOnServer(minecraftServer -> {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
                player.gameMode.destroyBlock(player.blockPosition().offset(3, 0, 3));
            });
            context.waitTicks(5);
            int charger = this.collect(server, "vehicle_charging_station");
            report.append("charging station mined: dropped=").append(charger).append('\n');
            ok &= charger == 1;

            // Build a whole table as placing the item does, then mine one corner.
            int parts = this.buildTable(server);
            server.runOnServer(minecraftServer -> {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
                player.gameMode.destroyBlock(this.tablePart(minecraftServer.overworld(), player.blockPosition(), true));
            });
            context.waitTicks(5);
            int tableMined = this.collect(server, "vehicle_assembling_table");
            int partsLeft = this.countParts(server);
            report.append("assembling table (").append(parts).append(" parts) mined: dropped=").append(tableMined)
                    .append(" partsLeft=").append(partsLeft).append('\n');
            ok &= parts > 1 && tableMined == 1 && partsLeft == 0;

            // And blown up.
            this.buildTable(server);
            server.runCommand("execute as @p at @s run summon minecraft:tnt ~6 ~ ~5 {fuse:0}");
            context.waitTicks(10);
            int tableBlown = this.collect(server, "vehicle_assembling_table");
            int blownLeft = this.countParts(server);
            report.append("assembling table blown up: dropped=").append(tableBlown).append(" partsLeft=").append(blownLeft).append('\n');
            ok &= tableBlown <= 1;
        }
        LOGGER.info("[station-drops]\n{}", report);
        if (!ok) {
            throw new AssertionError("Stations should come back as exactly one item:\n" + report);
        }
    }

    private int buildTable(TestServerContext server) {
        int built = server.computeOnServer(minecraftServer -> {
            ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
            ServerLevel level = minecraftServer.overworld();
            BlockPos pos = player.blockPosition().offset(5, 0, 4);
            BlockState state = ModBlocks.VEHICLE_ASSEMBLING_TABLE.get().defaultBlockState()
                    .setValue(VehicleAssemblingTableBlock.FACING, Direction.SOUTH);
            level.setBlockAndUpdate(pos, state);
            state.getBlock().setPlacedBy(level, pos, state, player, new ItemStack(ModBlocks.VEHICLE_ASSEMBLING_TABLE.get()));
            return 0;
        });
        return built + this.countParts(server);
    }

    private BlockPos tablePart(ServerLevel level, BlockPos origin, boolean any) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -2, -8), origin.offset(8, 4, 8))) {
            if (level.getBlockState(pos).getBlock() instanceof VehicleAssemblingTableBlock) {
                return pos.immutable();
            }
        }
        return origin;
    }

    private int countParts(TestServerContext server) {
        return server.computeOnServer(minecraftServer -> {
            ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
            ServerLevel level = minecraftServer.overworld();
            int count = 0;
            BlockPos origin = player.blockPosition();
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-8, -2, -8), origin.offset(8, 4, 8))) {
                if (level.getBlockState(pos).getBlock() instanceof VehicleAssemblingTableBlock) {
                    count++;
                }
            }
            return count;
        });
    }

    /** Counts and removes dropped items of the given JEG block near the player. */
    private int collect(TestServerContext server, String block) {
        return server.computeOnServer(minecraftServer -> {
            ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
            int count = 0;
            for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(12.0D))) {
                if (BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).equals(Reference.id(block))) {
                    count += item.getItem().getCount();
                }
                item.discard();
            }
            return count;
        });
    }
}
