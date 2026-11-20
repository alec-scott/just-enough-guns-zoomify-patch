package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;

/**
 * The BD-40's new finish and the earth on its blade (the user, 2026-11-19: the texture and animation upgrade): the dozer
 * pushes into a long dirt bank with the blade at grade, the earth builds against the moldboard (seen from the front),
 * then the blade lifts and the load spills over the cutting edge. Seen from a camera ahead of the blade (the land
 * vehicles' front view is the hull camera). Fails unless the earth drawn on the blade passes half while pushing and is
 * gone once the blade has lifted.
 */
public class BulldozerLoadClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private int dozerId;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("time set 2000");
            server.runOnServer(s -> {
                var level = s.overworld();
                for (int z = 5; z <= 30; z++) for (int y = G; y <= G + 1; y++) for (int x = -3; x <= 2; x++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState(), 3);
                }
                BulldozerEntity dozer = ModEntities.BULLDOZER.get().create(level, EntitySpawnReason.COMMAND);
                dozer.snapTo(0, G, 0, 0, 0);
                dozer.addEnergy(dozer.maxVehicleEnergy());
                level.addFreshEntity(dozer);
                s.getPlayerList().getPlayers().getFirst().startRiding(dozer, true, true);
                this.dozerId = dozer.getId();
            });
            context.waitTicks(BulldozerEntity.START_TICKS + 20);
            // A camera ahead and to the right of the blade, above the bank, looking down at it.
            int eye = server.computeOnServer(s -> {
                var stand = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(s.overworld(), EntitySpawnReason.COMMAND);
                stand.setInvisible(true);
                stand.setNoGravity(true);
                stand.snapTo(5.5D, G + 3.5D, 12.0D, 155.0F, 24.0F);
                stand.setYHeadRot(155.0F);
                s.overworld().addFreshEntity(stand);
                return stand.getId();
            });
            context.waitTicks(5);
            context.runOnClient(mc -> {
                mc.options.hideGui = true;
                mc.setCameraEntity(mc.level.getEntity(eye));
            });
            context.takeScreenshot("dozer_load_empty");
            float most = 0.0F;
            float drawn = 0.0F;
            context.getInput().holdKey(options -> options.keyUp);
            for (int tick = 0; tick < 140; tick++) {
                context.waitTick();
                float load = server.computeOnServer(s -> ((BulldozerEntity) s.overworld().getEntity(this.dozerId)).load());
                most = Math.max(most, load);
                drawn = Math.max(drawn, context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.render.BulldozerRenderer
                        .spoilShown((BulldozerEntity) mc.level.getEntity(this.dozerId))));
                if (tick % 20 == 0) {
                    // Follow the dozer down the bank: the camera keeps to its right front.
                    server.runOnServer(s -> {
                        var dozer = s.overworld().getEntity(this.dozerId);
                        var cam = (net.minecraft.world.entity.LivingEntity) s.overworld().getEntity(eye);
                        cam.snapTo(dozer.getX() + 5.5D, G + 3.5D, dozer.getZ() + 8.0D, 147.0F, 22.0F);
                        cam.setYHeadRot(147.0F);
                    });
                }
                if (tick == 90) context.takeScreenshot("dozer_load_pushing");
            }
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(10);
            server.runOnServer(s -> {
                var dozer = s.overworld().getEntity(this.dozerId);
                var cam = (net.minecraft.world.entity.LivingEntity) s.overworld().getEntity(eye);
                cam.snapTo(dozer.getX() + 4.5D, G + 3.0D, dozer.getZ() + 6.5D, 145.0F, 24.0F);
                cam.setYHeadRot(145.0F);
            });
            context.waitTicks(5);
            context.takeScreenshot("dozer_load_full_standing");
            // Lift the blade: the earth spills off.
            context.getInput().holdKey(options -> options.keyJump);
            context.waitTicks(12);
            context.takeScreenshot("dozer_load_spilling");
            context.waitTicks(40);
            context.getInput().releaseKey(options -> options.keyJump);
            context.takeScreenshot("dozer_load_spilled");
            float after = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.render.BulldozerRenderer
                    .spoilShown((BulldozerEntity) mc.level.getEntity(this.dozerId)));
            context.runOnClient(mc -> {
                mc.setCameraEntity(mc.player);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.options.hideGui = false;
            });
            LOG.info("[dozer-load] synced load peaked at {}; earth drawn on the blade peaked at {}, {} after lifting", most, drawn, after);
            if (drawn < 0.5F) failures.append("the earth on the blade never built up while cutting: ").append(drawn).append('\n');
            if (after > 0.1F) failures.append("the earth stayed on the lifted blade: ").append(after).append('\n');
        }
        if (failures.length() > 0) {
            LOG.error("[dozer-load] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[dozer-load] COMPLETE");
    }
}
