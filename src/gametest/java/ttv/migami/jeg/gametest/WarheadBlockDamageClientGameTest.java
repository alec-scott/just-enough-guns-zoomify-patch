package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.item.GunItem;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;
import ttv.migami.jeg.vehicle.util.VehicleWeaponStats;

/**
 * Missiles and rockets against the dense blocks End cities are built of: each is fired into a solid 7x7x7 cube of purpur
 * (or end stone brick) and the blocks it blew out are counted. A warhead should tear a real hole, a heavy one gut the cube.
 */
public class WarheadBlockDamageClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int SIZE = 7;
    private static final int GROUND_Y = -60;

    private record Shot(String name, String weapon, Block block, int minDestroyed) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        Shot[] shots = {
                new Shot("80mm pod rocket, purpur", "vehicle_80mm_rocket", Blocks.PURPUR_BLOCK, 12),
                new Shot("rocket launcher, purpur", "rocket_launcher", Blocks.PURPUR_BLOCK, 25),
                new Shot("9M120 missile, purpur", "vehicle_9m120_passenger_missile", Blocks.PURPUR_BLOCK, 40),
                new Shot("9M120 missile, end stone brick", "vehicle_9m120_passenger_missile", Blocks.END_STONE_BRICKS, 40),
                new Shot("KH-39 missile, purpur", "vehicle_kh39_missile", Blocks.PURPUR_BLOCK, 200),
        };
        StringBuilder report = new StringBuilder("warhead block damage report (7x7x7 cube = 343 blocks)\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule show_advancement_messages false");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            for (int i = 0; i < shots.length; i++) {
                Shot shot = shots[i];
                int x = i * 40;
                int z = 30;
                // Stand back and to the side, looking at the cube's face.
                server.runCommand(String.format("tp @a %d %d %d -40 20", x - 12, GROUND_Y + 3, z - 14));
                context.waitTicks(30);
                server.runOnServer(s -> fill(s.overworld(), x, z, shot.block()));
                context.waitTicks(10);
                server.runOnServer(s -> fire(s.overworld(), shot.weapon(), x, z));
                context.waitTicks(40);
                int left = server.computeOnServer(s -> count(s.overworld(), x, z, shot.block()));
                int destroyed = SIZE * SIZE * SIZE - left;
                context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
                context.takeScreenshot("warhead_" + shot.weapon());
                report.append(String.format("%s: %d blocks blown out%n", shot.name(), destroyed));
                if (destroyed < shot.minDestroyed()) {
                    failures.append(String.format("%s blew out only %d blocks (want %d)%n", shot.name(), destroyed, shot.minDestroyed()));
                }
            }
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    private static void fill(ServerLevel level, int x, int z, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(x, GROUND_Y, z, x + SIZE - 1, GROUND_Y + SIZE - 1, z + SIZE - 1)) {
            level.setBlockAndUpdate(pos, block.defaultBlockState());
        }
    }

    private static int count(ServerLevel level, int x, int z, Block block) {
        int left = 0;
        for (BlockPos pos : BlockPos.betweenClosed(x, GROUND_Y, z, x + SIZE - 1, GROUND_Y + SIZE - 1, z + SIZE - 1)) {
            if (level.getBlockState(pos).is(block)) {
                left++;
            }
        }
        return left;
    }

    /** Straight into the middle of the cube's near (-Z) face from 12 blocks out. */
    private static void fire(ServerLevel level, String weapon, int x, int z) {
        Vec3 start = new Vec3(x + SIZE / 2.0D, GROUND_Y + SIZE / 2.0D, z - 12.0D);
        Vec3 direction = new Vec3(0.0D, 0.0D, 1.0D);
        var player = level.getServer().getPlayerList().getPlayers().getFirst();
        if (weapon.contains("missile")) {
            Entity owner = EntityType.MARKER.create(level, EntitySpawnReason.COMMAND);
            owner.setPos(x - 30.0D, GROUND_Y + 30.0D, z - 30.0D);
            level.addFreshEntity(owner);
            level.addFreshEntity(new VehicleMissileEntity(level, owner, null, start, direction.scale(1.5D), Reference.id(weapon)));
            return;
        }
        var stats = weapon.startsWith("vehicle_")
                ? VehicleWeaponStats.get(Reference.id(weapon))
                : ((GunItem) BuiltInRegistries.ITEM.getValue(Reference.id(weapon))).getStats();
        BulletEntity rocket = new BulletEntity(level, player, stats, direction.scale(stats.projectileSpeed()));
        rocket.initialisePosition(start);
        level.addFreshEntity(rocket);
    }
}
