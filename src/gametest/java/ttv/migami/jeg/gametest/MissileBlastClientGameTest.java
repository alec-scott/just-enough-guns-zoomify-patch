package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.render.MissileBlastRenderer;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

/**
 * Drops each class of missile on an identical cobblestone-and-plank hut. A large missile must flatten far more of it
 * than a medium one, which in turn must punch through stone; the incendiary 9M-120 must leave the ground burning. Each
 * blast is drawn (flash, fireball, mushroom cap, shockwave, dust wall, debris, scorch) and screenshotted as it develops,
 * and its shockwave must reach the watcher.
 */
public class MissileBlastClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule mob_griefing true");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            int medium = this.strike(context, server, "vehicle_9m120_passenger_missile", 0, "medium");
            int large = this.strike(context, server, "vehicle_kh39_missile", 200, "large");
            int incendiary = this.strike(context, server, "vehicle_9m120_driver_missile", 400, "incendiary");
            int fires = server.computeOnServer(s -> countBlocks(s.overworld(), new BlockPos(400, -61, 40), 9, Blocks.FIRE));
            LOGGER.info("[missile-blast] hut and ground blocks destroyed: medium={} large={} incendiary={} fires={}", medium, large, incendiary, fires);
            check(medium >= 20, "A medium missile should break through the stone hut, broke " + medium);
            check(large > medium * 2, "A large missile should be far more destructive: large=" + large + " medium=" + medium);
            check(fires >= 12, "The incendiary missile should set the ground alight, fires=" + fires);
            // Real village houses, as the game builds them: a medium missile has to wreck one, not just scorch it.
            for (String house : new String[]{"plains/houses/plains_small_house_1", "plains/houses/plains_medium_house_1", "taiga/houses/taiga_medium_house_1"}) {
                int[] result = this.strikeHouse(context, server, house, 600 + 60 * houses++);
                LOGGER.info("[missile-blast] village house {}: {} of {} blocks destroyed", house, result[0], result[1]);
                check(result[1] > 40, house + " was not placed (" + result[1] + " blocks)");
                check(result[0] * 2 >= result[1], "A medium missile should wreck a village house: " + house + " lost " + result[0] + " of " + result[1] + " blocks");
            }
        }
    }

    private int houses;

    /** Places a village house, drops a medium missile on its middle, and returns {blocks destroyed, blocks it had}. */
    private int[] strikeHouse(ClientGameTestContext context, TestServerContext server, String house, int x) {
        int z = 40;
        server.runCommand("tp @p " + x + " -59 " + (z - 20) + " 0 5");
        context.waitTicks(40);
        server.runCommand(String.format("place template minecraft:village/%s %d -60 %d", house, x, z));
        context.waitTicks(20);
        int before = server.computeOnServer(s -> countHouse(s.overworld(), x, z));
        int blasts = MissileBlastRenderer.blastsStarted();
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            Entity owner = net.minecraft.world.entity.EntityType.MARKER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            owner.setPos(x + 4.5D, -30.0D, z + 4.5D);
            level.addFreshEntity(owner);
            // Into the middle of the front wall at head height, as a shot from a helicopter comes in.
            int minX = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(x - 2, -59, z - 2, x + 16, -45, z + 16)) {
                if (!level.getBlockState(pos).isAir()) {
                    minX = Math.min(minX, pos.getX());
                    maxX = Math.max(maxX, pos.getX());
                }
            }
            level.addFreshEntity(new VehicleMissileEntity(level, owner, null, new Vec3((minX + maxX + 1) / 2.0D, -57.5D, z - 14.0D),
                    new Vec3(0.0D, 0.0D, 1.5D), Reference.id("vehicle_9m120_passenger_missile")));
        });
        context.waitFor(mc -> MissileBlastRenderer.blastsStarted() > blasts, 100);
        context.waitTicks(40);
        context.takeScreenshot("missile_house_" + house.substring(house.lastIndexOf('/') + 1));
        int after = server.computeOnServer(s -> countHouse(s.overworld(), x, z));
        return new int[]{before - after, before};
    }

    private static int countHouse(ServerLevel level, int x, int z) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(x - 2, -59, z - 2, x + 16, -45, z + 16)) {
            var state = level.getBlockState(pos);
            if (!state.isAir() && !state.is(Blocks.FIRE)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Builds a hut 40 blocks ahead, drops the missile on it while the player watches, and returns how many blocks went:
     * of the hut and of the ground around and under it (a medium missile alone levels most of the hut).
     */
    private int strike(ClientGameTestContext context, TestServerContext server, String weapon, int x, String label) {
        int z = 40;
        server.runCommand("tp @p " + x + " -59 " + (z - 34) + " 0 2");
        context.waitTicks(40);
        // Walls of cobblestone with a plank floor and roof: 9 x 9, 4 high.
        server.runCommand(String.format("fill %d -60 %d %d -57 %d minecraft:cobblestone hollow", x - 4, z - 4, x + 4, z + 4));
        server.runCommand(String.format("fill %d -57 %d %d -57 %d minecraft:oak_planks", x - 4, z - 4, x + 4, z + 4));
        server.runCommand(String.format("fill %d -61 %d %d -61 %d minecraft:oak_planks", x - 4, z - 4, x + 4, z + 4));
        context.waitTicks(30);
        int before = server.computeOnServer(s -> countHut(s.overworld(), x, z));
        int blasts = MissileBlastRenderer.blastsStarted();
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            // A marker launches it: not a living shooter, so wire guidance does not steer it off along anyone's view.
            Entity owner = net.minecraft.world.entity.EntityType.MARKER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            owner.setPos(x + 0.5D, -40.0D, z + 0.5D);
            level.addFreshEntity(owner);
            Vec3 start = new Vec3(x + 0.5D, -50.0D, z + 0.5D);
            VehicleMissileEntity missile = new VehicleMissileEntity(level, owner, null, start, new Vec3(0.0D, -1.5D, 0.0D),
                    Reference.id(weapon));
            level.addFreshEntity(missile);
        });
        int shocks = MissileBlastRenderer.shocksFelt();
        context.waitFor(mc -> MissileBlastRenderer.blastsStarted() > blasts, 100);
        int[] shots = {1, 3, 6, 9, 12, 18, 30, 50, 80, 160};
        int waited = 0;
        for (int shot : shots) {
            context.waitTicks(shot - waited);
            waited = shot;
            context.takeScreenshot("missile_" + label + "_t" + shot);
        }
        // The watcher stands 34 blocks off: the shockwave (and the boom) must have reached them.
        check(MissileBlastRenderer.shocksFelt() > shocks, label + ": the shockwave should reach the watcher");
        int after = server.computeOnServer(s -> countHut(s.overworld(), x, z));
        return before - after;
    }

    private static int countHut(ServerLevel level, int x, int z) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(x - 16, -63, z - 16, x + 16, -57, z + 16)) {
            var state = level.getBlockState(pos);
            if (!state.isAir() && !state.is(Blocks.FIRE)) {
                count++;
            }
        }
        return count;
    }

    private static int countBlocks(ServerLevel level, BlockPos centre, int radius, net.minecraft.world.level.block.Block block) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -3, -radius), centre.offset(radius, 4, radius))) {
            if (level.getBlockState(pos).is(block)) {
                count++;
            }
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
