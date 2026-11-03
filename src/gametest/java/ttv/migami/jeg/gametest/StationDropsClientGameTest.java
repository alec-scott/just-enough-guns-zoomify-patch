package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;

/**
 * Mines the vehicle assembling table and charging station with a pickaxe in survival: each must drop itself as an item.
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
            for (String block : new String[] {"vehicle_charging_station", "vehicle_assembling_table"}) {
                server.runCommand("execute as @p at @s run setblock ~2 ~ ~2 jeg:" + block);
                context.waitTicks(5);
                int dropped = server.computeOnServer(minecraftServer -> {
                    ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
                    BlockPos pos = player.blockPosition().offset(2, 0, 2);
                    player.gameMode.destroyBlock(pos);
                    return 0;
                });
                context.waitTicks(5);
                dropped = server.computeOnServer(minecraftServer -> {
                    ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
                    int count = 0;
                    for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(6.0D))) {
                        if (BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).equals(Reference.id(block))) {
                            count += item.getItem().getCount();
                        }
                        item.discard();
                    }
                    return count;
                });
                report.append(block).append(" dropped=").append(dropped).append('\n');
                ok &= dropped == 1;
            }
        }
        LOGGER.info("[station-drops]\n{}", report);
        if (!ok) {
            throw new AssertionError("Stations should drop themselves:\n" + report);
        }
    }
}
