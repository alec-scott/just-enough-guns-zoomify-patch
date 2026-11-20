package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.client.VehicleFeel;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Tracks stay on the ground in a hard turn (the user's review, 2026-11-19: the BMP-2's and PLZ-05's tracks clipped into
 * the dirt turning hard): each is driven up to speed on a dirt field and thrown into a hard left turn, then braked hard,
 * photographed from low beside its outer track. The suspension's roll and dive (VehicleFeel) now move the hull alone,
 * so the hull is seen leaning over tracks that stay on the dirt. Fails only if the vehicle can't be boarded or doesn't
 * lean (no roll to show).
 */
public class TrackTurnClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("time set noon");
            server.runCommand("gamemode creative @a");
            server.runCommand("forceload add -64 -64 64 96");
            server.runCommand("fill -64 -61 -64 64 -61 96 minecraft:coarse_dirt");
            for (var entry : new Object[][]{{ModEntities.BMP2.get(), "bmp2"}, {ModEntities.PLZ05.get(), "plz05"}}) {
                @SuppressWarnings("unchecked")
                EntityType<? extends VehicleEntity> type = (EntityType<? extends VehicleEntity>) entry[0];
                String name = (String) entry[1];
                server.runCommand("ride @p dismount");
                server.runCommand("kill @e[type=jeg:" + name + "]");
                server.runCommand("kill @e[type=minecraft:armor_stand]");
                server.runCommand("tp @a 0.5 -60 -20.5 0 0");
                context.waitTicks(10);
                int id = server.computeOnServer(s -> {
                    VehicleEntity v = type.create(s.overworld(), EntitySpawnReason.COMMAND);
                    v.snapTo(0.5D, G, -16.5D, 0.0F, 0.0F);
                    v.addEnergy(v.maxVehicleEnergy());
                    s.overworld().addFreshEntity(v);
                    return v.getId();
                });
                int eye = server.computeOnServer(s -> {
                    var stand = EntityType.ARMOR_STAND.create(s.overworld(), EntitySpawnReason.COMMAND);
                    stand.setInvisible(true);
                    stand.setNoGravity(true);
                    stand.snapTo(-8.0D, G - 1.2D, -16.5D, -90.0F, 4.0F);
                    s.overworld().addFreshEntity(stand);
                    return stand.getId();
                });
                context.waitTicks(5);
                boolean aboard = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(s.overworld().getEntity(id), true, true));
                if (!aboard) throw new AssertionError(name + ": the driver could not board");
                context.waitTicks(100);
                context.runOnClient(mc -> {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.options.hideGui = true;
                    mc.setCameraEntity(mc.level.getEntity(eye));
                });
                // Up to speed, then hard left: its right track is the outer one.
                context.getInput().holdKey(options -> options.keyUp);
                for (int tick = 0; tick < 70; tick++) {
                    context.waitTick();
                    follow(context, id, eye);
                }
                context.getInput().holdKey(options -> options.keyLeft);
                float most = 0.0F;
                for (int tick = 0; tick < 30; tick++) {
                    context.waitTick();
                    follow(context, id, eye);
                    float roll = context.computeOnClient(mc -> VehicleFeel.roll((VehicleEntity) mc.level.getEntity(id), 1.0F));
                    most = Math.max(most, Math.abs(roll));
                    if (tick == 12 || tick == 22) {
                        context.takeScreenshot("track_turn_" + name + "_turning_" + tick);
                        LOG.info("[track-turn] {} turning, tick {}: roll {}", name, tick, roll);
                    }
                }
                context.getInput().releaseKey(options -> options.keyLeft);
                context.getInput().releaseKey(options -> options.keyUp);
                // Hard on the brakes: the nose dives.
                context.getInput().holdKey(options -> options.keyDown);
                float dive = 0.0F;
                for (int tick = 0; tick < 12; tick++) {
                    context.waitTick();
                    follow(context, id, eye);
                    float pitch = context.computeOnClient(mc -> VehicleFeel.pitch((VehicleEntity) mc.level.getEntity(id), 1.0F));
                    dive = Math.max(dive, Math.abs(pitch));
                    if (tick == 6) context.takeScreenshot("track_turn_" + name + "_braking");
                }
                context.getInput().releaseKey(options -> options.keyDown);
                context.runOnClient(mc -> {
                    mc.setCameraEntity(mc.player);
                    mc.options.hideGui = false;
                });
                LOG.info("[track-turn] {}: most roll {} deg, most dive {} deg", name, most, dive);
                if (most < 0.5F) throw new AssertionError(name + ": the hull never leaned in the turn (" + most + " deg)");
            }
            LOG.info("[track-turn] COMPLETE");
        }
    }

    /** The camera low beside the vehicle's right (outer) side, 8 blocks out, looking at it, its eye half a block up. */
    private static void follow(ClientGameTestContext context, int vehicle, int eye) {
        context.runOnClient(mc -> {
            Entity v = mc.level.getEntity(vehicle);
            Entity cam = mc.level.getEntity(eye);
            if (v == null || cam == null) return;
            float yaw = v.getYRot();
            double rx = -Mth.cos(yaw * Mth.DEG_TO_RAD), rz = -Mth.sin(yaw * Mth.DEG_TO_RAD);
            float look = yaw - 90.0F;
            cam.snapTo(v.getX() + rx * 8.0D, G - 1.2D, v.getZ() + rz * 8.0D, look, 4.0F);
            cam.setOldPosAndRot();
            cam.setYHeadRot(look);
            if (cam instanceof net.minecraft.world.entity.LivingEntity living) {
                living.yHeadRotO = look;
                living.yBodyRot = look;
                living.yBodyRotO = look;
            }
        });
    }
}
