package ttv.migami.jeg.gametest;

import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;
import ttv.migami.jeg.vehicle.terrain.BulldozerTerrainWork;

/**
 * The dozer in a trench of its own, as a player leaves it after a pass (found driving it in the pack client):
 * a steady cut keeps "Clearing" up without flicker; with the blade raised a turn into the walls is refused and says so,
 * and pushing into a bank says to lower the blade (not "too hard"); with the blade lowered the turn cuts the walls and
 * the machine comes round. Also checks the dozer's name is its own, not "Vehicle Inventory".
 */
public class BulldozerCutTurnClientGameTest implements FabricClientGameTest {
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
            server.runOnServer(s -> {
                var level = s.overworld();
                // A 4-wide trench (x -2..1, the blade's lane on the grid) 2 deep, its far half still to be cut.
                for (int z = -6; z <= 26; z++) for (int y = G; y <= G + 1; y++) {
                    for (int x = -7; x <= 6; x++) {
                        boolean wall = x < -2 || x > 1;
                        if (wall || z >= 6 && z <= 12) level.setBlock(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState(), 3);
                    }
                }
                BulldozerEntity dozer = ModEntities.BULLDOZER.get().create(level, EntitySpawnReason.COMMAND);
                dozer.snapTo(0, G, 0, 0, 0);
                dozer.addEnergy(dozer.maxVehicleEnergy());
                level.addFreshEntity(dozer);
                s.getPlayerList().getPlayers().getFirst().startRiding(dozer, true, true);
                this.id = dozer.getUUID().toString();
            });
            context.waitTicks(BulldozerEntity.START_TICKS + 20);

            String name = context.computeOnClient(mc -> mc.player.getVehicle().getName().getString());
            LOG.info("[dozer-cut-turn] name: {}", name);
            if (!name.equals("BD-40 Bulldozer")) failures.append("the dozer is named '").append(name).append("'\n");

            // ---- A steady cut through z 6..12: once working, the status never drops back to Ready while pushing.
            context.getInput().holdKey(options -> options.keyUp);
            // Only a drop to Ready that comes back to Clearing is a flicker; Ready after the blade leaves the bank is real.
            int idleTicks = 0, workTicks = 0, idleRun = 0;
            boolean working = false;
            for (int tick = 0; tick < 160 && server.computeOnServer(s -> dozer(s).getZ()) < 11; tick++) {
                context.waitTick();
                int status = server.computeOnServer(s -> dozer(s).workStatus());
                if (status == BulldozerTerrainWork.WORKING) {
                    if (working) idleTicks += idleRun;
                    idleRun = 0;
                    working = true;
                    workTicks++;
                } else if (working && status == BulldozerTerrainWork.IDLE) {
                    idleRun++;
                }
            }
            context.getInput().releaseKey(options -> options.keyUp);
            double cutZ = server.computeOnServer(s -> dozer(s).getZ());
            LOG.info("[dozer-cut-turn] steady cut: {} working ticks, {} idle ticks between, reached z {}", workTicks, idleTicks, cutZ);
            if (workTicks == 0) failures.append("the blade never worked through the bank\n");
            if (idleTicks > 0) failures.append("the status flickered to Ready ").append(idleTicks).append(" times during a steady cut\n");

            // ---- Blade raised: a turn into the walls is refused, and the HUD says to lower the blade.
            context.getInput().holdKey(options -> options.keyJump);
            context.waitTicks(30);
            context.getInput().releaseKey(options -> options.keyJump);
            float yaw0 = server.computeOnServer(s -> dozer(s).getYRot());
            context.getInput().holdKey(options -> options.keyLeft);
            boolean saidRaised = false;
            for (int tick = 0; tick < 30; tick++) {
                context.waitTick();
                saidRaised |= server.computeOnServer(s -> dozer(s).workStatus()) == BulldozerTerrainWork.TURN_BLOCKED_RAISED;
            }
            context.getInput().releaseKey(options -> options.keyLeft);
            float raisedTurn = Mth.wrapDegrees(server.computeOnServer(s -> dozer(s).getYRot()) - yaw0);
            LOG.info("[dozer-cut-turn] raised blade: turned {} degrees, said lower the blade {}", raisedTurn, saidRaised);
            if (Math.abs(raisedTurn) > 2) failures.append("the raised blade turned through the walls: ").append(raisedTurn).append("°\n");
            if (!saidRaised) failures.append("a refused turn with the blade raised did not say to lower the blade\n");

            // ---- Blade raised against a bank: it stops and says to lower the blade, not "too hard".
            int bankZ = Mth.floor(cutZ) + 5;
            server.runOnServer(s -> {
                for (int x = -2; x <= 1; x++) for (int y = G; y <= G + 1; y++) s.overworld().setBlock(new BlockPos(x, y, bankZ), Blocks.DIRT.defaultBlockState(), 3);
            });
            context.getInput().holdKey(options -> options.keyUp);
            boolean saidLower = false, saidHard = false;
            for (int tick = 0; tick < 50; tick++) {
                context.waitTick();
                int status = server.computeOnServer(s -> dozer(s).workStatus());
                saidLower |= status == BulldozerTerrainWork.RAISED_BLOCKED;
                saidHard |= status == BulldozerTerrainWork.SOLID;
            }
            context.getInput().releaseKey(options -> options.keyUp);
            LOG.info("[dozer-cut-turn] raised blade at a bank: said lower {}, said too hard {}", saidLower, saidHard);
            if (!saidLower || saidHard) failures.append("raised blade at a dirt bank: said lower ").append(saidLower).append(", said too hard ").append(saidHard).append('\n');
            if (server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(0, G, bankZ)).isAir())) failures.append("the raised blade cut the bank\n");

            // ---- Blade lowered: the turn cuts the trench walls and the machine comes round; the soil goes into the hold.
            context.getInput().holdKey(options -> options.keyShift);
            context.waitTicks(30);
            context.getInput().releaseKey(options -> options.keyShift);
            int dirt0 = server.computeOnServer(s -> dozer(s).vehicleInventory().countItem(Items.DIRT));
            float yaw1 = server.computeOnServer(s -> dozer(s).getYRot());
            context.getInput().holdKey(options -> options.keyLeft);
            int blocked = 0;
            for (int tick = 0; tick < 100; tick++) {
                context.waitTick();
                int status = server.computeOnServer(s -> dozer(s).workStatus());
                if (status == BulldozerTerrainWork.TURN_BLOCKED || status == BulldozerTerrainWork.TURN_BLOCKED_RAISED) blocked++;
            }
            context.getInput().releaseKey(options -> options.keyLeft);
            float loweredTurn = Mth.wrapDegrees(server.computeOnServer(s -> dozer(s).getYRot()) - yaw1);
            int dirtGained = server.computeOnServer(s -> dozer(s).vehicleInventory().countItem(Items.DIRT)) - dirt0;
            LOG.info("[dozer-cut-turn] lowered blade: turned {} degrees, {} blocked ticks, {} dirt into the hold", loweredTurn, blocked, dirtGained);
            if (Math.abs(loweredTurn) < 45) failures.append("the lowered blade did not bring the machine round in the trench: ").append(loweredTurn).append("°\n");
            if (dirtGained <= 0) failures.append("the cut walls did not go into the hold\n");
            if (Math.abs(server.computeOnServer(s -> dozer(s).getY()) - G) > .01) failures.append("the machine sank or climbed while turning\n");

            context.runOnClient(mc -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); mc.player.setXRot(35); });
            context.waitTicks(10);
            context.takeScreenshot("bulldozer_cut_turn");
            context.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
        }
        if (failures.length() > 0) {
            LOG.error("[dozer-cut-turn] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[dozer-cut-turn] COMPLETE");
    }

    private BulldozerEntity dozer(MinecraftServer server) {
        return (BulldozerEntity) server.overworld().getEntity(UUID.fromString(this.id));
    }
}
