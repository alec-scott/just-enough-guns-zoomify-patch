package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The LAV-150 at the wheel (the user's report, 2026-11-19: a wide turning circle, and the first-person view jittering
 * while driving).
 * <ol>
 *   <li>The turning circle from a crawl, as the FSV-6's is measured: a radius of at most 9 blocks (a real V-150 turns
 *   in about 17 m across).</li>
 *   <li>The driver's camera, tick by tick, flat out and then turning: where it sits on the hull continues across each
 *   tick boundary. The largest break is held to 0.03 blocks.</li>
 * </ol>
 * Screenshots from the seat, driving straight and in the turn, and at night looking down at the headlamps.
 */
public class Lav150DriveClientGameTest implements FabricClientGameTest {
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
            server.runCommand("forceload add -96 -96 96 96");
            int id = server.computeOnServer(s -> {
                VehicleEntity lav = ModEntities.LAV150.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                lav.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
                lav.addEnergy(lav.maxVehicleEnergy());
                s.overworld().addFreshEntity(lav);
                return lav.getId();
            });
            context.waitTicks(5);
            server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(s.overworld().getEntity(id), true, true));
            context.waitTicks(60);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

            // ---- 1. The turning circle from a crawl.
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(8);
            context.getInput().holdKey(options -> options.keyLeft);
            context.waitTicks(10);
            double[] a = server.computeOnServer(s -> pose(s.overworld().getEntity(id)));
            context.waitTicks(40);
            double[] b = server.computeOnServer(s -> pose(s.overworld().getEntity(id)));
            context.getInput().releaseKey(options -> options.keyLeft);
            double travelled = Math.hypot(b[0] - a[0], b[1] - a[1]);
            double turned = Math.abs(Mth.wrapDegrees(b[2] - a[2])) * Mth.DEG_TO_RAD;
            double radius = turned > 1.0E-3 ? travelled / turned : 1.0E9;
            LOG.info("[lav150-drive] turning: {} blocks in 2 s, turned {} degrees, radius {} blocks",
                    String.format("%.1f", travelled), String.format("%.1f", turned * Mth.RAD_TO_DEG), String.format("%.1f", radius));
            if (radius > 9.0D) failures.append(String.format("turning circle too wide: radius %.1f blocks%n", radius));

            // ---- 2. The driver's camera, frame by frame: straight at speed, then a turn.
            context.waitTicks(60);
            double straight = this.cameraJump(context, id, 60);
            context.takeScreenshot("lav150_drive_straight");
            context.getInput().holdKey(options -> options.keyRight);
            double turning = this.cameraJump(context, id, 60);
            context.takeScreenshot("lav150_drive_turning");
            context.getInput().releaseKey(options -> options.keyRight);
            context.getInput().releaseKey(options -> options.keyUp);
            double speed = context.computeOnClient(mc -> vehicle(mc).getDeltaMovement().horizontalDistance() * 20.0D * 3.6D);
            LOG.info("[lav150-drive] driver camera: largest frame-to-frame jump of its place on the hull {} straight, {} turning (blocks); {} km/h",
                    String.format("%.4f", straight), String.format("%.4f", turning), String.format("%.0f", speed));
            if (straight > 0.03D || turning > 0.03D) {
                failures.append(String.format("the driver's camera jumps on the hull: %.4f straight, %.4f turning%n", straight, turning));
            }

            // ---- 3. At night from the seat, looking down at the headlamps' cages: no glow floats over them from behind.
            server.runCommand("time set midnight");
            context.waitTicks(40);
            for (float pitch : new float[]{32.0F, 50.0F}) {
                context.runOnClient(mc -> {
                    mc.options.hideGui = true;
                    mc.player.setXRot(pitch);
                    mc.player.xRotO = pitch;
                });
                context.waitTicks(40);
                context.takeScreenshot("lav150_driver_night_lamps_" + (int) pitch);
            }
            context.runOnClient(mc -> mc.options.hideGui = false);
        }
        if (failures.length() > 0) {
            LOG.error("[lav150-drive] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[lav150-drive] COMPLETE");
    }

    /**
     * Over {@code ticks} ticks, the largest break in where the driver's camera sits on the hull (in the hull's own frame,
     * so travel and turning cancel out): where it is drawn at the end of one tick against the start of the next. A
     * smooth camera continues where it left off; a jittering one jumps at tick boundaries.
     */
    private double cameraJump(ClientGameTestContext context, int id, int ticks) {
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
        VehicleEntity lav = vehicle(mc);
        Vec3 d = lav.cameraPositionFor(mc.player, partial).subtract(lav.getPosition(partial));
        float yaw = Mth.rotLerp(partial, lav.yRotO, lav.getYRot()) * Mth.DEG_TO_RAD;
        double x = d.x * Math.cos(yaw) + d.z * Math.sin(yaw);
        double z = -d.x * Math.sin(yaw) + d.z * Math.cos(yaw);
        return new Vec3(x, d.y, z);
    }

    private static double[] pose(net.minecraft.world.entity.Entity e) {
        return new double[]{e.getX(), e.getZ(), e.getYRot()};
    }

    private static VehicleEntity vehicle(Minecraft mc) {
        return (VehicleEntity) mc.player.getVehicle();
    }
}
