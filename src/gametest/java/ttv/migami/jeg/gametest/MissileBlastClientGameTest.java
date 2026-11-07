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
 * blast is drawn (fireball, shockwave, smoke) and screenshotted as it develops.
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
            LOGGER.info("[missile-blast] hut blocks destroyed: medium={} large={} incendiary={} fires={}", medium, large, incendiary, fires);
            check(medium >= 20, "A medium missile should break through the stone hut, broke " + medium);
            check(large > medium * 2, "A large missile should be far more destructive: large=" + large + " medium=" + medium);
            check(fires >= 12, "The incendiary missile should set the ground alight, fires=" + fires);
        }
    }

    /** Builds a hut 40 blocks ahead, drops the missile on it while the player watches, and returns how many hut blocks went. */
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
        context.waitFor(mc -> MissileBlastRenderer.blastsStarted() > blasts, 100);
        int[] shots = {1, 3, 6, 12, 30, 80};
        int waited = 0;
        for (int shot : shots) {
            context.waitTicks(shot - waited);
            waited = shot;
            context.takeScreenshot("missile_" + label + "_t" + shot);
        }
        int after = server.computeOnServer(s -> countHut(s.overworld(), x, z));
        return before - after;
    }

    private static int countHut(ServerLevel level, int x, int z) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(x - 4, -61, z - 4, x + 4, -57, z + 4)) {
            var state = level.getBlockState(pos);
            if (state.is(Blocks.COBBLESTONE) || state.is(Blocks.OAK_PLANKS)) {
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
