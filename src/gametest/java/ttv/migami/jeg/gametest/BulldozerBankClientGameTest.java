package ttv.migami.jeg.gametest;

import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;

/**
 * What the blade takes above the ground as it cuts through a bank. Five cases across its 4-block lane, at grade:
 * a rock shelf 2 blocks up with air under it (inside the machine's height: always cleared); a natural cave pocket of
 * 2 blocks with rock above (followed into the rock); planks over a 1-block gap (not natural: left); rock over a 3-block
 * gap (gap too big: left); and leaves 3 blocks up (above the machine: left).
 */
public class BulldozerBankClientGameTest implements FabricClientGameTest {
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
            server.runOnServer(s -> {
                var level = s.overworld();
                for (int x = -2; x < 2; x++) {
                    BlockState stone = Blocks.STONE.defaultBlockState();
                    level.setBlock(new BlockPos(x, G + 2, 5), stone, 3);                                   // A: shelf 2 up
                    for (int y = G; y <= G + 2; y++) level.setBlock(new BlockPos(x, y, 8), stone, 3);      // B: bank,
                    for (int y = G + 5; y <= G + 7; y++) level.setBlock(new BlockPos(x, y, 8), stone, 3);  //    cave, rock
                    for (int y = G; y <= G + 2; y++) level.setBlock(new BlockPos(x, y, 11), stone, 3);     // C: bank, gap,
                    level.setBlock(new BlockPos(x, G + 4, 11), Blocks.OAK_PLANKS.defaultBlockState(), 3);  //    planks
                    for (int y = G; y <= G + 2; y++) level.setBlock(new BlockPos(x, y, 14), stone, 3);     // D: bank,
                    level.setBlock(new BlockPos(x, G + 6, 14), stone, 3);                                  //    3-gap, rock
                    level.setBlock(new BlockPos(x, G + 3, 17), Blocks.OAK_LEAVES.defaultBlockState().setValue(
                            net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true), 3);             // E: canopy 3 up
                    for (int z = 23; z <= 27; z++) for (int y = G; y <= G + 2; y++)                         // F: a bank to push
                        level.setBlock(new BlockPos(x, y, z), (x + y + z) % 3 == 0 ? stone : Blocks.DIRT.defaultBlockState(), 3);
                }
                BulldozerEntity dozer = ModEntities.BULLDOZER.get().create(level, EntitySpawnReason.COMMAND);
                dozer.snapTo(0, G, 0, 0, 0);
                dozer.addEnergy(dozer.maxVehicleEnergy());
                level.addFreshEntity(dozer);
                s.getPlayerList().getPlayers().getFirst().startRiding(dozer, true, true);
                this.id = dozer.getUUID().toString();
            });
            context.waitTicks(BulldozerEntity.START_TICKS + 20);
            // First person, looking at the right-hand mirror: its glass should face the operator.
            context.runOnClient(mc -> { mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON); mc.player.setYRot(55); mc.player.setXRot(0); });
            context.waitTicks(5);
            context.takeScreenshot("bulldozer_mirror");
            context.runOnClient(mc -> { mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK); mc.player.setYRot(0); });
            context.getInput().holdKey(options -> options.keyUp);
            int stalls = 0, inBank = 0;
            double slowest = 1;
            double last = server.computeOnServer(s -> dozer(s).getZ());
            StringBuilder speeds = new StringBuilder();
            for (int tick = 0; tick < 360; tick++) {
                context.waitTick();
                double z = server.computeOnServer(s -> dozer(s).getZ());
                // The blade is in bank F while the machine's centre is between z 20 and 25 (the edge is 2.9 ahead).
                if (z > 20 && z < 25) {
                    inBank++;
                    double speed = z - last;
                    slowest = Math.min(slowest, speed);
                    if (speed < .005) stalls++;
                    speeds.append(String.format("%.3f ", speed));
                }
                last = z;
            }
            context.getInput().releaseKey(options -> options.keyUp);
            LOG.info("[dozer-bank] pushing bank F: {} ticks, {} stalled, slowest {} blocks/tick; speeds {}", inBank, stalls, slowest, speeds);
            if (stalls > 2) failures.append("the machine stopped and started pushing through the bank: ").append(stalls).append(" stalled ticks\n");
            context.waitTicks(10);
            String state = server.computeOnServer(s -> {
                var level = s.overworld();
                StringBuilder out = new StringBuilder();
                for (int x = -2; x < 2; x++) {
                    if (!level.getBlockState(new BlockPos(x, G + 2, 5)).isAir()) out.append("A shelf 2 up stayed at x").append(x).append('\n');
                    for (int y = G; y <= G + 7; y++) if (!level.getBlockState(new BlockPos(x, y, 8)).isAir()) out.append("B cave rock stayed at y").append(y).append('\n');
                    if (!level.getBlockState(new BlockPos(x, G + 4, 11)).is(Blocks.OAK_PLANKS)) out.append("C planks over a gap were cut\n");
                    if (!level.getBlockState(new BlockPos(x, G + 6, 14)).is(Blocks.STONE)) out.append("D rock over a 3-block gap was cut\n");
                    if (!level.getBlockState(new BlockPos(x, G + 3, 17)).is(Blocks.OAK_LEAVES)) out.append("E canopy 3 up was cut\n");
                    for (int z = 23; z <= 27; z++) for (int y = G; y <= G + 2; y++)
                        if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) out.append("F bank left at z").append(z).append("\n");
                }
                out.append(String.format("dozer at z %.1f, y %.2f%n", dozer(s).getZ(), dozer(s).getY()));
                return out.toString();
            });
            LOG.info("[dozer-bank] {}", state);
            if (!state.startsWith("dozer at")) failures.append(state);
            if (!server.computeOnServer(s -> dozer(s).getZ() > 28 && Math.abs(dozer(s).getY() - G) < .01)) failures.append("the dozer did not get through: ").append(state);
        }
        if (failures.length() > 0) {
            LOG.error("[dozer-bank] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[dozer-bank] COMPLETE");
    }

    private BulldozerEntity dozer(MinecraftServer server) {
        return (BulldozerEntity) server.overworld().getEntity(UUID.fromString(this.id));
    }
}
