package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.EntitySpawnReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The speedboat's bow gunner stands at the gun with both hands on it (the user's report, 2026-11-19: he stood with his
 * arms at his sides). A player in the gunner's seat of a speedboat on a lake; fails unless the seat is a standing,
 * gripping station; screenshots in third person from behind, the gun ahead and swung abeam. (From outside, a camera
 * that is not the player does not draw him while he rides: his stance was checked by playing, 2026-11-19.)
 */
public class SpeedboatGunnerPoseClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("time set noon");
            server.runCommand("gamemode creative @a");
            server.runCommand("fill -12 -61 -12 12 -61 12 minecraft:water");
            server.runCommand("tp @a 0.5 -59 -4 0 0");
            context.waitTicks(10);
            int id = server.computeOnServer(s -> {
                VehicleEntity boat = ModEntities.SPEEDBOAT.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                boat.snapTo(0.5D, -60.6D, 0.5D, 0.0F, 0.0F);
                s.overworld().addFreshEntity(boat);
                return boat.getId();
            });
            context.waitTicks(10);
            int seat = server.computeOnServer(s -> {
                VehicleEntity boat = (VehicleEntity) s.overworld().getEntity(id);
                var player = s.getPlayerList().getPlayers().get(0);
                player.startRiding(boat, true, true);
                // Seat 0 is taken by the first aboard: move on to the gunner's seat as the change-seat key does.
                boat.changeSeat(player);
                return boat.seatOf(player);
            });
            context.waitTicks(20);
            boolean[] station = context.computeOnClient(mc -> {
                VehicleEntity boat = (VehicleEntity) mc.level.getEntity(id);
                int clientSeat = boat.getSeatIndex(mc.player);
                return new boolean[]{boat.vehicleData().standingSeats().contains(clientSeat),
                        boat.vehicleData().gripSeats().contains(clientSeat)};
            });
            context.runOnClient(mc -> {
                mc.options.hideGui = true;
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            });
            context.waitTicks(10);
            context.takeScreenshot("speedboat_gunner_behind");
            context.runOnClient(mc -> {
                mc.player.setYRot(mc.player.getYRot() + 80.0F);
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            });
            context.waitTicks(40);
            context.takeScreenshot("speedboat_gunner_abeam");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            LOGGER.info("[speedboat-gunner] seat {}, standing {}, gripping {}", seat, station[0], station[1]);
            if (seat != 1 || !station[0] || !station[1]) {
                throw new AssertionError("the speedboat's gunner must stand at the gun with both hands on it (seat " + seat
                        + ", standing " + station[0] + ", gripping " + station[1] + ")");
            }
        }
        LOGGER.info("[speedboat-gunner] COMPLETE");
    }
}
