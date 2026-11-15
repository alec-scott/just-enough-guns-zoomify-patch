package ttv.migami.jeg.gametest;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.block.entity.VehicleContainerBlockEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The A-10 on the ground, as a player meets it:
 * <ul>
 *     <li>unpacked from its vehicle container (the box) by real clicks (placed, then opened with the crowbar), it stands level on its wheels where the box was
 *     (it used to drop in from half its height and land nose first);</li>
 *     <li>with the throttle closed, holding reverse backs it up slowly (reverse taxi), and releasing it stops;</li>
 *     <li>screenshots of the engines from in front, behind and the side (the nacelle rims must be solid).</li>
 * </ul>
 */
public class A10GroundClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private String jetId;

    @Override public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("time set noon");
            server.runCommand("gamemode survival @a");
            server.runCommand("forceload add -48 -48 48 48");
            // ---- Unpack from the box: place the container on the ground ahead, then right-click it, both with the use key.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                player.teleportTo(s.overworld(), 0.5, G, 0.5, java.util.Set.of(), 0, 0, true);
                player.getInventory().setItem(0, VehicleContainerBlockEntity.createItemForVehicle(Reference.id("a10")));
                player.getInventory().setSelectedSlot(0);
            });
            context.waitTicks(10);
            context.runOnClient(mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setYRot(0); mc.player.setXRot(45); });
            context.waitTicks(4);
            context.getInput().pressKey(options -> options.keyUse);
            context.waitTicks(6);
            BlockPos box = server.computeOnServer(s -> {
                for (BlockPos pos : BlockPos.betweenClosed(-4, G - 2, -4, 4, G + 2, 6)) {
                    if (s.overworld().getBlockEntity(pos) instanceof VehicleContainerBlockEntity) return pos.immutable();
                }
                return null;
            });
            LOG.info("[a10-ground] container placed at {}", box);
            if (box == null) {
                failures.append("the container was not placed\n");
            } else {
                // Step back to its side and open it: right-click with the crowbar, as a player does.
                server.runOnServer(s -> {
                    player(s).getInventory().setItem(0, new net.minecraft.world.item.ItemStack(ttv.migami.jeg.init.ModItems.CROWBAR.get()));
                    player(s).teleportTo(s.overworld(), box.getX() + 4.5, G, box.getZ() + 0.5, java.util.Set.of(), 90, 30, true);
                });
                context.waitTicks(6);
                context.runOnClient(mc -> {
                    Vec3 eye = mc.player.getEyePosition();
                    Vec3 look = Vec3.atCenterOf(box).subtract(eye);
                    mc.player.setYRot((float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90));
                    mc.player.setXRot((float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance())));
                });
                context.waitTicks(3);
                context.getInput().pressKey(options -> options.keyUse);
                context.waitTicks(80);
                String spawned = server.computeOnServer(s -> {
                    List<VehicleEntity> jets = s.overworld().getEntitiesOfClass(VehicleEntity.class, new AABB(box).inflate(12), VehicleEntity::isJet);
                    if (jets.isEmpty()) return "none";
                    VehicleEntity jet = jets.getFirst();
                    this.jetId = jet.getUUID().toString();
                    Vec3 pose = jet.poseAngles(1.0F);
                    return String.format(Locale.ROOT, "pitch %.1f roll %.1f y %.2f (box at %d) onGround %s", pose.y, pose.z, jet.getY(), box.getY(), jet.onGround());
                });
                LOG.info("[a10-ground] unpacked: {}", spawned);
                context.runOnClient(mc -> mc.options.hideGui = true);
                context.takeScreenshot("a10_unpacked");
                if (this.jetId == null) {
                    failures.append("no A-10 came out of the box\n");
                } else {
                    double pitch = server.computeOnServer(s -> jet(s).poseAngles(1.0F).y);
                    if (Math.abs(pitch) > 3.0) failures.append("unpacked A-10 is not level: ").append(spawned).append('\n');
                }
            }

            if (this.jetId != null) {
                server.runCommand("gamemode creative @a");
                // ---- The engines, close: the right nacelle (model x 26.5, y 54, z 51 px; the nose is -Z) from in front,
                // behind and beside, the camera aimed at it.
                double[][] views = {{1.2, 0.6, -4.5}, {0.4, 0.4, 5.5}, {5.0, 0.3, 0.0}};
                String[] names = {"a10_engines_front", "a10_engines_rear", "a10_engine_side"};
                for (int i = 0; i < views.length; i++) {
                    double[] v = views[i];
                    Vec3[] eyeAndTarget = server.computeOnServer(s -> {
                        VehicleEntity jet = jet(s);
                        Vec3 f = Vec3.directionFromRotation(0, jet.getYRot());
                        Vec3 r = new Vec3(-f.z, 0, f.x);
                        Vec3 engine = jet.position().add(f.scale(-51 / 16.0)).add(r.scale(26.5 / 16.0)).add(0, 54 / 16.0, 0);
                        Vec3 eye = engine.add(r.scale(v[0])).add(0, v[1], 0).add(f.scale(-v[2]));
                        player(s).getAbilities().flying = true;
                        player(s).onUpdateAbilities();
                        player(s).teleportTo(s.overworld(), eye.x, eye.y - 1.62, eye.z, java.util.Set.of(), 0, 0, true);
                        return new Vec3[]{eye, engine};
                    });
                    Vec3 look = eyeAndTarget[1].subtract(eyeAndTarget[0]);
                    float lookYaw = (float) (Math.toDegrees(Math.atan2(look.z, look.x)) - 90);
                    float lookPitch = (float) -Math.toDegrees(Math.atan2(look.y, look.horizontalDistance()));
                    context.waitTicks(6);
                    context.runOnClient(mc -> { mc.player.getAbilities().flying = true; mc.player.setYRot(lookYaw); mc.player.setXRot(lookPitch); });
                    context.waitTicks(6);
                    context.takeScreenshot(names[i]);
                }
                context.runOnClient(mc -> mc.options.hideGui = false);

                // ---- Reverse taxi: board, close the throttle, hold reverse.
                server.runCommand("gamemode creative @a");
                server.runOnServer(s -> {
                    VehicleEntity jet = jet(s);
                    jet.addEnergy(jet.maxVehicleEnergy());
                    player(s).startRiding(jet, true, true);
                });
                context.waitTicks(60);
                Vec3 start = server.computeOnServer(s -> jet(s).position());
                float yaw = server.computeOnServer(s -> jet(s).getYRot());
                context.getInput().holdKey(options -> options.keyDown);
                context.waitTicks(100);
                Vec3 during = server.computeOnServer(s -> jet(s).position());
                double speed = server.computeOnServer(s -> jet(s).getDeltaMovement().horizontalDistance());
                context.getInput().releaseKey(options -> options.keyDown);
                context.waitTicks(60);
                Vec3 end = server.computeOnServer(s -> jet(s).position());
                double settled = server.computeOnServer(s -> jet(s).getDeltaMovement().horizontalDistance());
                Vec3 forward = Vec3.directionFromRotation(0, yaw);
                double backed = -during.subtract(start).dot(forward);
                LOG.info("[a10-ground] reverse: backed {} blocks in 5 s, speed {} b/t ({} km/h); after release moved {} more, speed {}",
                        String.format(Locale.ROOT, "%.2f", backed), String.format(Locale.ROOT, "%.3f", speed), String.format(Locale.ROOT, "%.1f", speed * 72),
                        String.format(Locale.ROOT, "%.2f", end.subtract(during).horizontalDistance()), String.format(Locale.ROOT, "%.4f", settled));
                if (backed < 2.0) failures.append("holding reverse with the throttle closed did not back the A-10 up: ").append(backed).append('\n');
                if (speed > 0.2) failures.append("reverse taxi too fast: ").append(speed).append('\n');
                if (settled > 0.01) failures.append("the A-10 kept rolling after reverse was released: ").append(settled).append('\n');
            }
        }
        if (failures.length() > 0) {
            LOG.error("[a10-ground] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[a10-ground] COMPLETE");
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private VehicleEntity jet(MinecraftServer server) {
        return (VehicleEntity) server.overworld().getEntity(UUID.fromString(this.jetId));
    }
}
