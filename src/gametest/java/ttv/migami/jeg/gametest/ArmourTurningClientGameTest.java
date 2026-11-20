package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The tracked armour at the wheel (the user's review, 2026-11-19: the BMP-2's wide turning circle and its jittering
 * first-person view; the PLZ-05 and YX-100 checked with it). For each, from the driver's seat:
 * <ol>
 *   <li>the turning radius from a crawl, measured as the FSV-6's and LAV-150's are: at most 12 blocks for the BMP-2,
 *   14 for the heavier PLZ-05 and YX-100 (before: about 29, 50 and 50);</li>
 *   <li>the driver's camera, tick by tick, flat out and turning: where it sits on the hull continues across each tick
 *   boundary, the largest break at most 0.03 blocks.</li>
 * </ol>
 */
public class ArmourTurningClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("time set noon");
            server.runCommand("gamemode creative @a");
            server.runCommand("forceload add -128 -128 128 128");
            this.drive(context, server, failures, ModEntities.BMP2.get(), "bmp2", 12.0D);
            this.drive(context, server, failures, ModEntities.PLZ05.get(), "plz05", 14.0D);
            this.drive(context, server, failures, ModEntities.YX100.get(), "yx100", 14.0D);
        }
        if (failures.length() > 0) {
            LOG.error("[armour-turning] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[armour-turning] COMPLETE");
    }

    private void drive(ClientGameTestContext context, TestServerContext server, StringBuilder failures,
                       EntityType<? extends VehicleEntity> type, String name, double maxRadius) {
        server.runCommand("ride @p dismount");
        server.runCommand("kill @e[type=jeg:" + name + "]");
        server.runCommand("tp @a 0.5 -60 -4.5");
        context.waitTicks(10);
        int id = server.computeOnServer(s -> {
            VehicleEntity v = type.create(s.overworld(), EntitySpawnReason.COMMAND);
            v.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
            v.addEnergy(v.maxVehicleEnergy());
            s.overworld().addFreshEntity(v);
            return v.getId();
        });
        context.waitTicks(5);
        server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(s.overworld().getEntity(id), true, true));
        context.waitTicks(80);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(10);
        context.getInput().holdKey(options -> options.keyLeft);
        context.waitTicks(10);
        double[] a = server.computeOnServer(s -> pose(s.overworld().getEntity(id)));
        context.waitTicks(40);
        double[] b = server.computeOnServer(s -> pose(s.overworld().getEntity(id)));
        context.getInput().releaseKey(options -> options.keyLeft);
        double travelled = Math.hypot(b[0] - a[0], b[1] - a[1]);
        double turned = Math.abs(Mth.wrapDegrees(b[2] - a[2])) * Mth.DEG_TO_RAD;
        double radius = turned > 1.0E-3 ? travelled / turned : 1.0E9;

        context.waitTicks(40);
        double straight = cameraJump(context, 40);
        context.getInput().holdKey(options -> options.keyRight);
        double turning = cameraJump(context, 40);
        context.getInput().releaseKey(options -> options.keyRight);
        context.getInput().releaseKey(options -> options.keyUp);
        context.takeScreenshot("armour_turning_" + name);
        LOG.info("[armour-turning] {}: {} blocks in 2 s turned {} degrees, radius {} blocks; driver camera break {} straight, {} turning",
                name, String.format("%.1f", travelled), String.format("%.1f", turned * Mth.RAD_TO_DEG), String.format("%.1f", radius),
                String.format("%.4f", straight), String.format("%.4f", turning));
        if (radius > maxRadius) failures.append(String.format("%s: turning circle too wide, radius %.1f blocks%n", name, radius));
        if (straight > 0.03D || turning > 0.03D) {
            failures.append(String.format("%s: the driver's camera jumps on the hull: %.4f straight, %.4f turning%n", name, straight, turning));
        }
        context.waitTicks(20);
    }

    private static double cameraJump(ClientGameTestContext context, int ticks) {
        double worst = 0.0D;
        Vec3 end = null;
        for (int i = 0; i < ticks; i++) {
            context.waitTick();
            Vec3[] at = context.computeOnClient(mc -> new Vec3[]{offset(mc, 0.0F), offset(mc, 1.0F)});
            if (end != null) {
                worst = Math.max(worst, at[0].distanceTo(end));
            }
            end = at[1];
        }
        return worst;
    }

    private static Vec3 offset(Minecraft mc, float partial) {
        VehicleEntity v = (VehicleEntity) mc.player.getVehicle();
        Vec3 d = v.cameraPositionFor(mc.player, partial).subtract(v.getPosition(partial));
        float yaw = Mth.rotLerp(partial, v.yRotO, v.getYRot()) * Mth.DEG_TO_RAD;
        return new Vec3(d.x * Math.cos(yaw) + d.z * Math.sin(yaw), d.y, -d.x * Math.sin(yaw) + d.z * Math.cos(yaw));
    }

    private static double[] pose(net.minecraft.world.entity.Entity e) {
        return new double[]{e.getX(), e.getZ(), e.getYRot()};
    }
}
