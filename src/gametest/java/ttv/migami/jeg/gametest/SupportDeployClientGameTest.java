package ttv.migami.jeg.gametest;

import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.block.VehicleShellBlock;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.support.SupportShell;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * The FSV-6's deploy cycle, driven through the real keys where a player would use them:
 * <ul>
 *     <li>Parked 7 degrees off south, holding R from the driver's seat squares it up on the grid, lifts it onto its
 *     jacks, unfolds it and places the whole walk-in shell.</li>
 *     <li>A player walks up the ramp into the room and stands on its floor, one block above the ground.</li>
 *     <li>Refusals: turned 30 degrees off a cardinal (heading), a stone block where a slide-out goes (blocked), and
 *     packing up with someone inside.</li>
 *     <li>Packing up from the driver's seat removes every shell block and sets it back on its wheels.</li>
 *     <li>A deployed vehicle removed outright (the crowbar's discard) takes its shell with it.</li>
 * </ul>
 * Screenshots: outside deployed, the room from the aisle, the bays, and the room at night.
 */
public class SupportDeployClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private String main, skewed, blocked;

    @Override public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("time set noon");
            server.runCommand("gamemode creative @a");
            server.runOnServer(s -> {
                ServerLevel level = s.overworld();
                this.main = spawn(level, 0.3, 0.6, 7).getUUID().toString();
                this.skewed = spawn(level, 30, 0, 30).getUUID().toString();
                SupportVehicleEntity third = spawn(level, 60, 0, 0);
                this.blocked = third.getUUID().toString();
                // A stone block where the left slide-out goes (two blocks left of the centreline, mid-body).
                var at = SupportRig.world(third.position(), 0, 40, 24, 16);
                level.setBlock(BlockPos.containing(at), Blocks.STONE.defaultBlockState(), 3);
                s.getPlayerList().getPlayers().getFirst().startRiding(truck(s, this.main), true, true);
            });
            context.waitTicks(SupportVehicleEntity.START_TICKS + 10);

            // ---- Deploy through the real key: hold R for a second.
            context.getInput().holdKey(options -> KeyBindings.RELOAD);
            context.waitTicks(SupportVehicleEntity.HOLD_TICKS + 4);
            context.getInput().releaseKey(options -> KeyBindings.RELOAD);
            String started = server.computeOnServer(s -> truck(s, this.main).status() + " target " + truck(s, this.main).deployTarget());
            LOG.info("[support-deploy] after holding R: {}", started);
            if (!started.endsWith("true")) failures.append("holding R did not start deploying: ").append(started).append('\n');
            context.runOnClient(mc -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setYRot(150); mc.player.setXRot(18); });
            context.waitTicks(SupportRig.DEPLOY_TICKS / 2);
            context.takeScreenshot("support_deploying");
            context.waitTicks(SupportRig.DEPLOY_TICKS / 2 + 20);
            String deployed = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s, this.main);
                int shell = countShell(s.overworld(), truck);
                var corner = SupportRig.world(truck.position(), truck.getYRot(), SupportRig.CORNER_X, 0, SupportRig.CORNER_Z);
                return String.format("progress %.2f yaw %.2f pos %.3f %.3f %.3f corner %.3f %.3f shell %d of %d placed %s",
                        truck.deployProgress(), truck.getYRot(), truck.getX(), truck.getY(), truck.getZ(), corner.x, corner.z,
                        shell, SupportRig.shell().size(), SupportShell.isPlaced(truck));
            });
            LOG.info("[support-deploy] deployed: {}", deployed);
            boolean ok = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s, this.main);
                var corner = SupportRig.world(truck.position(), truck.getYRot(), SupportRig.CORNER_X, 0, SupportRig.CORNER_Z);
                return truck.isDeployed() && Math.abs(net.minecraft.util.Mth.wrapDegrees(truck.getYRot())) < .01
                        && Math.abs(corner.x - Math.round(corner.x)) < 1.0E-3 && Math.abs(corner.z - Math.round(corner.z)) < 1.0E-3
                        && Math.abs(truck.getY() - G) < 1.0E-3 && countShell(s.overworld(), truck) == SupportRig.shell().size();
            });
            if (!ok) failures.append("deploy did not square up on the grid with the whole shell: ").append(deployed).append('\n');

            // ---- Outside, then walk up the ramp into the room.
            server.runOnServer(s -> player(s).stopRiding());
            context.waitTicks(5);
            server.runOnServer(s -> {
                SupportVehicleEntity truck = truck(s, this.main);
                var view = SupportRig.world(truck.position(), truck.getYRot(), 70, 40, 150);
                player(s).teleportTo(s.overworld(), view.x, view.y, view.z, java.util.Set.of(), 25, 20, true);
            });
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(10);
            context.takeScreenshot("support_deployed_outside");
            // Deployed, the generator runs the shelter: heard from outside, and the diesel is off.
            int clientId = server.computeOnServer(s -> truck(s, this.main).getId());
            String audio = context.computeOnClient(mc -> String.format("loops %d generator %.3f diesel %.3f",
                    ttv.migami.jeg.vehicle.client.audio.SupportAudio.loopCount(clientId),
                    ttv.migami.jeg.vehicle.client.audio.SupportAudio.loopVolume(clientId, "generator"),
                    ttv.migami.jeg.vehicle.client.audio.SupportAudio.loopVolume(clientId, "engine_idle")));
            LOG.info("[support-deploy] deployed audio: {}", audio);
            // The client's copy must stand where the server squared it up, or the model and the room disagree.
            String clientPose = context.computeOnClient(mc -> {
                var entity = mc.level.getEntity(clientId);
                return entity == null ? "missing" : String.format("%.3f %.3f %.3f yaw %.2f", entity.getX(), entity.getY(), entity.getZ(), entity.getYRot());
            });
            String serverPose = server.computeOnServer(s -> {
                var truck = truck(s, this.main);
                return String.format("%.3f %.3f %.3f yaw %.2f", truck.getX(), truck.getY(), truck.getZ(), truck.getYRot());
            });
            LOG.info("[support-deploy] pose: client {} server {}", clientPose, serverPose);
            if (!clientPose.equals(serverPose)) failures.append("client pose ").append(clientPose).append(" differs from server ").append(serverPose).append('\n');
            if (!context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.SupportAudio.loopVolume(clientId, "generator") > .01F
                    && ttv.migami.jeg.vehicle.client.audio.SupportAudio.loopVolume(clientId, "engine_idle") < .01F)) {
                failures.append("deployed: the generator should be heard and the diesel not: ").append(audio).append('\n');
            }
            server.runOnServer(s -> {
                SupportVehicleEntity truck = truck(s, this.main);
                var foot = SupportRig.world(truck.position(), truck.getYRot(), 0, 0, 100);
                player(s).teleportTo(s.overworld(), foot.x, foot.y, foot.z, java.util.Set.of(), truck.getYRot(), 0, true);
            });
            context.waitTicks(10);
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(40);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(5);
            String walked = server.computeOnServer(s -> {
                ServerPlayer player = player(s);
                return String.format("player at %.2f %.2f %.2f, inside %d", player.getX(), player.getY(), player.getZ(),
                        SupportShell.playersInside(truck(s, this.main)));
            });
            LOG.info("[support-deploy] walked up the ramp: {}", walked);
            if (!server.computeOnServer(s -> SupportShell.playersInside(truck(s, this.main)) == 1 && Math.abs(player(s).getY() - (G + 1)) < .01)) {
                failures.append("could not walk up the ramp into the room: ").append(walked).append('\n');
            }
            // Room views with the HUD hidden: from above, from the left side, from the doorway looking forward, then
            // side-on to the left bays.
            context.runOnClient(mc -> mc.options.hideGui = true);
            server.runOnServer(s -> {
                SupportVehicleEntity truck = truck(s, this.main);
                var above = SupportRig.world(truck.position(), truck.getYRot(), 0, 260, 0);
                player(s).teleportTo(s.overworld(), above.x, above.y, above.z, java.util.Set.of(), truck.getYRot(), 90, true);
                player(s).getAbilities().flying = true;
                player(s).onUpdateAbilities();
            });
            context.waitTicks(8);
            context.runOnClient(mc -> { mc.player.setYRot(0); mc.player.setXRot(90); });
            context.waitTicks(4);
            context.takeScreenshot("support_deployed_top");
            server.runOnServer(s -> {
                SupportVehicleEntity truck = truck(s, this.main);
                var side = SupportRig.world(truck.position(), truck.getYRot(), 150, 30, 16);
                player(s).teleportTo(s.overworld(), side.x, side.y, side.z, java.util.Set.of(), 90, 5, true);
            });
            context.waitTicks(8);
            context.runOnClient(mc -> { mc.player.setYRot(90); mc.player.setXRot(5); });
            context.waitTicks(4);
            context.takeScreenshot("support_deployed_left_side");
            server.runOnServer(s -> { player(s).getAbilities().flying = false; player(s).onUpdateAbilities(); });
            server.runOnServer(s -> {
                SupportVehicleEntity truck = truck(s, this.main);
                var door = SupportRig.world(truck.position(), truck.getYRot(), 0, SupportRig.FLOOR_Y, 54);
                player(s).teleportTo(s.overworld(), door.x, door.y, door.z, java.util.Set.of(), truck.getYRot(), 12, true);
            });
            context.waitTicks(5);
            context.runOnClient(mc -> { mc.player.setYRot(0); mc.player.setXRot(12); });
            context.waitTicks(5);
            String light = context.computeOnClient(mc -> "block light at eye " + mc.level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,
                    net.minecraft.core.BlockPos.containing(mc.player.getEyePosition())) + ", sky " + mc.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,
                    net.minecraft.core.BlockPos.containing(mc.player.getEyePosition())));
            LOG.info("[support-deploy] room {}", light);
            context.takeScreenshot("support_room_aisle");
            server.runOnServer(s -> {
                SupportVehicleEntity truck = truck(s, this.main);
                var aisle = SupportRig.world(truck.position(), truck.getYRot(), -8, SupportRig.FLOOR_Y, 32);
                player(s).teleportTo(s.overworld(), aisle.x, aisle.y, aisle.z, java.util.Set.of(), truck.getYRot() - 90, 10, true);
            });
            context.waitTicks(5);
            context.runOnClient(mc -> { mc.player.setYRot(-90); mc.player.setXRot(10); });
            context.waitTicks(5);
            context.takeScreenshot("support_room_bays_left");
            server.runCommand("time set midnight");
            context.runOnClient(mc -> { mc.player.setYRot(0); mc.player.setXRot(10); });
            context.waitTicks(10);
            context.takeScreenshot("support_room_night");
            context.runOnClient(mc -> mc.options.hideGui = false);
            server.runCommand("time set noon");

            // ---- Refusals.
            String refusals = server.computeOnServer(s -> {
                ServerPlayer player = player(s);
                SupportVehicleEntity inside = truck(s, this.main);
                String packInside = inside.requestPackUp().name();
                String heading = truck(s, this.skewed).requestDeploy(player).name();
                String block = truck(s, this.blocked).requestDeploy(player).name();
                return "pack with player inside " + packInside + ", 30 degrees off " + heading + ", stone in the slide-out " + block
                        + " at " + truck(s, this.blocked).statusPos().toShortString();
            });
            LOG.info("[support-deploy] refusals: {}", refusals);
            if (refusals.contains("PLAYERS_INSIDE") && refusals.contains("HEADING") && refusals.contains("BLOCKED")) {
                LOG.info("[support-deploy] refusals as expected");
            } else {
                failures.append("refusals wrong: ").append(refusals).append('\n');
            }

            // ---- Pack up from the driver's seat.
            server.runOnServer(s -> player(s).startRiding(truck(s, this.main), true, true));
            context.waitTicks(5);
            context.getInput().holdKey(options -> KeyBindings.RELOAD);
            context.waitTicks(SupportVehicleEntity.HOLD_TICKS + 4);
            context.getInput().releaseKey(options -> KeyBindings.RELOAD);
            context.waitTicks(SupportRig.DEPLOY_TICKS + 30);
            String packed = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s, this.main);
                return String.format("progress %.2f y %.3f shell %d", truck.deployProgress(), truck.getY(), countShell(s.overworld(), truck));
            });
            LOG.info("[support-deploy] packed: {}", packed);
            if (!server.computeOnServer(s -> truck(s, this.main).isPacked() && countShell(s.overworld(), truck(s, this.main)) == 0
                    && Math.abs(truck(s, this.main).getY() - G) < .05)) {
                failures.append("pack-up left shell blocks or did not come down: ").append(packed).append('\n');
            }

            // ---- Removed while deployed: the shell goes with it.
            server.runOnServer(s -> {
                player(s).stopRiding();
                SupportVehicleEntity truck = truck(s, this.blocked);
                s.overworld().setBlock(BlockPos.containing(SupportRig.world(truck.position(), 0, 40, 24, 16)), Blocks.AIR.defaultBlockState(), 3);
                truck.requestDeploy(player(s));
            });
            context.waitTicks(SupportRig.DEPLOY_TICKS + 10);
            String removed = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s, this.blocked);
                int before = countShell(s.overworld(), truck);
                var centre = truck.position();
                truck.discard();
                int after = countShellAround(s.overworld(), centre);
                return "deployed with " + before + " shell blocks, after discard " + after;
            });
            LOG.info("[support-deploy] {}", removed);
            if (!removed.endsWith(" 0") || removed.contains("with 0 ")) failures.append("discarding a deployed vehicle: ").append(removed).append('\n');
        }
        if (failures.length() > 0) {
            LOG.error("[support-deploy] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[support-deploy] COMPLETE");
    }

    private static SupportVehicleEntity spawn(ServerLevel level, double x, double z, float yaw) {
        SupportVehicleEntity truck = ModEntities.TRUCK.get().create(level, EntitySpawnReason.COMMAND);
        truck.snapTo(x, G, z, yaw, 0);
        truck.addEnergy(truck.maxVehicleEnergy());
        level.addFreshEntity(truck);
        return truck;
    }

    private static int countShell(ServerLevel level, SupportVehicleEntity truck) {
        return countShellAround(level, truck.position());
    }

    private static int countShellAround(ServerLevel level, net.minecraft.world.phys.Vec3 centre) {
        int count = 0;
        BlockPos c = BlockPos.containing(centre);
        for (BlockPos pos : BlockPos.betweenClosed(c.offset(-8, -2, -8), c.offset(8, 5, 8))) {
            if (level.getBlockState(pos).getBlock() instanceof VehicleShellBlock) count++;
        }
        return count;
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static SupportVehicleEntity truck(MinecraftServer server, String id) {
        return (SupportVehicleEntity) server.overworld().getEntity(UUID.fromString(id));
    }
}
