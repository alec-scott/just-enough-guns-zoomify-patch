package ttv.migami.jeg.gametest;

import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * The FSV-6's tow hitch, worked as a player does (real keys and clicks):
 * <ul>
 *     <li>from the driver's seat, with an empty LAV-150 parked behind: the panel offers the hitch, and the tow key
 *     (N) hitches it;</li>
 *     <li>driving forward and round a turn, the LAV follows nose to the pintle at the tow bar's length; the tow key
 *     again drops it;</li>
 *     <li>on foot, looking at the back of the vehicle, sneak and right-click hitches it, and again drops it.</li>
 * </ul>
 * Screenshots: the driver's panel offering the hitch, towing round the turn, and the on-foot prompt.
 */
public class SupportTowClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private String truckId, lavId;

    @Override public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("time set noon");
            server.runCommand("gamemode creative @a");
            server.runOnServer(s -> {
                SupportVehicleEntity truck = ModEntities.TRUCK.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                truck.snapTo(0.5, G, 0.5, 0, 0);
                truck.addEnergy(truck.maxVehicleEnergy());
                s.overworld().addFreshEntity(truck);
                this.truckId = truck.getUUID().toString();
                Vec3 behind = SupportRig.world(truck, 0, 0, 64 + 48);
                VehicleEntity lav = (VehicleEntity) ModEntities.LAV150.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                lav.snapTo(behind.x, G, behind.z, 0, 0);
                s.overworld().addFreshEntity(lav);
                this.lavId = lav.getUUID().toString();
            });
            context.waitTicks(10);
            // ---- From the driver's seat: the panel offers the hitch, and the tow key takes the LAV.
            server.runOnServer(s -> player(s).startRiding(truck(s), true, true));
            context.waitTicks(SupportVehicleEntity.START_TICKS + 10);
            boolean offered = context.computeOnClient(mc -> mc.player.getVehicle() instanceof SupportVehicleEntity t && t.towCandidate() != null);
            context.takeScreenshot("support_tow_prompt");
            context.getInput().pressKey(options -> ttv.migami.jeg.client.KeyBindings.VEHICLE_TOW);
            context.waitTicks(6);
            String hitched = server.computeOnServer(s -> "towing id " + truck(s).towedEntityId() + " (lav " + lav(s).getId() + ")");
            LOG.info("[support-tow] hitch offered to the driver {}; tow key -> {}", offered, hitched);
            if (!offered) failures.append("the driver's panel did not offer the hitch with the LAV behind\n");
            if (!server.computeOnServer(s -> truck(s).towedEntityId() == lav(s).getId())) failures.append("the tow key did not hitch the LAV: ").append(hitched).append('\n');
            // The load weighs on the drive: 15 t + the LAV's 9.9 t, inertia sqrt(12 / 24.9) = 0.69 against 0.89 alone;
            // the truck's own suspension (inertia()) is unchanged.
            double[] pull = server.computeOnServer(s -> new double[]{truck(s).driveInertia(), truck(s).inertia()});
            LOG.info("[support-tow] towing the LAV: drive inertia {} (suspension {})", pull[0], pull[1]);
            if (Math.abs(pull[0] - Math.sqrt(12.0D / 24.9D)) > 0.01D || Math.abs(pull[1] - Math.sqrt(12.0D / 15.0D)) > 0.01D)
                failures.append("the tow load does not weigh on the drive: ").append(pull[0]).append(" / ").append(pull[1]).append('\n');

            context.runOnClient(mc -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setYRot(180); mc.player.setXRot(20); });
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(120);
            context.getInput().holdKey(options -> options.keyLeft);
            context.waitTicks(50);
            context.takeScreenshot("support_towing_turn");
            context.getInput().releaseKey(options -> options.keyLeft);
            context.waitTicks(20);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(30);
            String follow = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                Vec3 pintle = SupportRig.world(truck, 0, 8, 64);
                VehicleEntity lav = lav(s);
                return String.format("truck at %.1f %.1f, lav at %.1f %.1f, lav to pintle %.2f blocks, lav moved %.1f",
                        truck.getX(), truck.getZ(), lav.getX(), lav.getZ(), new Vec3(lav.getX() - pintle.x, 0, lav.getZ() - pintle.z).length(),
                        lav.position().distanceTo(new Vec3(.5, G, .5 - 7)));
            });
            LOG.info("[support-tow] {}", follow);
            boolean followed = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                Vec3 pintle = SupportRig.world(truck, 0, 8, 64);
                VehicleEntity lav = lav(s);
                double gap = new Vec3(lav.getX() - pintle.x, 0, lav.getZ() - pintle.z).length();
                return gap < lav.getBbWidth() * .5 + 3 && lav.position().distanceTo(new Vec3(.5, G, .5 - 7)) > 40;
            });
            if (!followed) failures.append("the LAV did not follow on the tow bar: ").append(follow).append('\n');
            // The tow bar (SupportTowBarLayer) seen from beside the hitch.
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.player.setYRot(mc.player.getVehicle().getYRot() + 120.0F);
                mc.player.setXRot(12.0F);
            });
            context.waitTicks(5);
            context.takeScreenshot("support_tow_bar_side");
            context.getInput().pressKey(options -> ttv.migami.jeg.client.KeyBindings.VEHICLE_TOW);
            context.waitTicks(6);
            String released = server.computeOnServer(s -> "towing id after the tow key " + truck(s).towedEntityId());
            LOG.info("[support-tow] {}", released);
            if (!released.endsWith("-1")) failures.append("the tow key did not release: ").append(released).append('\n');

            // ---- On foot: stand off the rear corner, look at the back, sneak and right-click. (The LAV is parked square
            // behind again first: after the turn it was left beside the truck, in the player's line of sight.)
            server.runOnServer(s -> {
                player(s).stopRiding();
                SupportVehicleEntity truck = truck(s);
                truck.setDeltaMovement(Vec3.ZERO);
                Vec3 behind = SupportRig.world(truck, 0, 0, 64 + 48);
                lav(s).snapTo(behind.x, truck.getY(), behind.z, truck.getYRot(), 0);
                lav(s).setDeltaMovement(Vec3.ZERO);
            });
            context.waitTicks(10);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            String[] foot = new String[2];
            for (int round = 0; round < 2; round++) {
                Vec3[] eyeAndTarget = server.computeOnServer(s -> {
                    SupportVehicleEntity truck = truck(s);
                    // Beside the right rear quarter, looking at the corner: clear of the LAV, whose nose reaches the bumper.
                    Vec3 stand = SupportRig.world(truck, -36, 0, 48);
                    player(s).teleportTo(s.overworld(), stand.x, truck.getY(), stand.z, java.util.Set.of(), 0, 0, true);
                    return new Vec3[]{new Vec3(stand.x, truck.getY() + 1.62, stand.z), SupportRig.world(truck, -14, 24, 61)};
                });
                Vec3 look = eyeAndTarget[1].subtract(eyeAndTarget[0]);
                float yaw = (float) (net.minecraft.util.Mth.atan2(look.z, look.x) * net.minecraft.util.Mth.RAD_TO_DEG) - 90;
                float pitch = (float) (-net.minecraft.util.Mth.atan2(look.y, look.horizontalDistance()) * net.minecraft.util.Mth.RAD_TO_DEG);
                context.waitTicks(4);
                context.runOnClient(mc -> { mc.player.setYRot(yaw); mc.player.setXRot(pitch); });
                context.waitTicks(15);
                if (round == 0) context.takeScreenshot("support_tow_foot_hint");
                context.getInput().holdKey(options -> options.keyShift);
                context.waitTicks(3);
                context.getInput().pressKey(options -> options.keyUse);
                context.waitTicks(6);
                context.getInput().releaseKey(options -> options.keyShift);
                context.waitTicks(3);
                foot[round] = server.computeOnServer(s -> "towing id " + truck(s).towedEntityId() + " (lav " + (lav(s) == null ? "gone" : lav(s).getId()) + ")");
            }
            LOG.info("[support-tow] on foot, sneak + use: {}, then {}", foot[0], foot[1]);
            if (!server.computeOnServer(s -> lav(s) != null && foot[0].startsWith("towing id " + lav(s).getId() + " "))) {
                failures.append("sneak + use on the back did not hitch: ").append(foot[0]).append('\n');
            }
            if (!foot[1].startsWith("towing id -1")) failures.append("sneak + use again did not release: ").append(foot[1]).append('\n');
        }
        if (failures.length() > 0) {
            LOG.error("[support-tow] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[support-tow] COMPLETE");
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private SupportVehicleEntity truck(MinecraftServer server) {
        return (SupportVehicleEntity) server.overworld().getEntity(UUID.fromString(this.truckId));
    }

    private VehicleEntity lav(MinecraftServer server) {
        return (VehicleEntity) server.overworld().getEntity(UUID.fromString(this.lavId));
    }
}
