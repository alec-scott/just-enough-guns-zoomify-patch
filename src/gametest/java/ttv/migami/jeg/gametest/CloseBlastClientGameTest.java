package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.render.MissileBlastRenderer;
import ttv.migami.jeg.network.MissileBlastPayload;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * A missile going off close to a helicopter: the pilot sits in a parked AH-6 and watches medium warheads land 8 and 16
 * blocks ahead, in first and third person. The fireball, smoke and dust must still be drawn as they roll up around the
 * helicopter, not thinned away or sliced off near the camera. Then, on foot, the view circles the smoke column: it must
 * read as a solid, rounded column from every side, not a flat card. Screenshots of each.
 */
public class CloseBlastClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final double GROUND = -60.0D;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("tp @p 0.5 -60 -3 0 0");
            server.runCommand("summon jeg:ah6 0.5 -60 0.5 {Rotation:[0f,0f]}");
            context.waitTicks(5);
            server.runCommand("ride @p mount @e[type=jeg:ah6,sort=nearest,limit=1]");
            context.waitFor(mc -> mc.player.getVehicle() instanceof VehicleEntity);
            context.runOnClient(mc -> {
                mc.options.hideGui = true;
                mc.gui.getChat().clearMessages(false);
            });
            world.getClientLevel().waitForChunksRender();

            this.blast(context, CameraType.FIRST_PERSON, 8.0D, "fp_8");
            this.blast(context, CameraType.FIRST_PERSON, 16.0D, "fp_16");
            this.blast(context, CameraType.THIRD_PERSON_BACK, 8.0D, "tp_8");
            this.blast(context, CameraType.THIRD_PERSON_BACK, 16.0D, "tp_16");

            server.runCommand("ride @p dismount");
            context.waitTicks(5);
            this.orbit(context, server);
            context.runOnClient(mc -> {
                mc.options.hideGui = false;
                MissileBlastRenderer.clear();
            });
        }
    }

    /** A medium warhead on the ground {@code ahead} blocks in front of the helicopter, watched from the given view. */
    private void blast(ClientGameTestContext context, CameraType view, double ahead, String label) {
        Vec3 at = new Vec3(0.5D, GROUND, 0.5D + ahead);
        context.runOnClient(mc -> {
            MissileBlastRenderer.clear();
            mc.options.setCameraType(view);
        });
        context.waitTicks(10);
        context.runOnClient(mc -> MissileBlastRenderer.add(new MissileBlastPayload(at, 4.5F, 10.0F, false, false)));
        int[] shots = {2, 5, 9, 14, 22, 40, 70, 120};
        int waited = 0;
        for (int shot : shots) {
            context.waitTicks(shot - waited);
            waited = shot;
            context.takeScreenshot("closeblast_" + label + "_t" + shot);
        }
        LOGGER.info("[close-blast] {} drawn", label);
    }

    /** Circles a settled smoke column at 14 blocks, looking at it from four sides. */
    private void orbit(ClientGameTestContext context, TestServerContext server) {
        Vec3 at = new Vec3(0.5D, GROUND, 40.5D);
        context.runOnClient(mc -> {
            MissileBlastRenderer.clear();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.player.getAbilities().flying = true;
        });
        server.runCommand("tp @p 0.5 -56 26.5 0 0");
        context.waitTicks(10);
        context.runOnClient(mc -> MissileBlastRenderer.add(new MissileBlastPayload(at, 4.5F, 10.0F, false, false)));
        context.waitTicks(50);
        for (int side = 0; side < 4; side++) {
            double angle = Math.toRadians(side * 90.0D);
            double x = at.x - Math.sin(angle) * 14.0D;
            double z = at.z - Math.cos(angle) * 14.0D;
            float yaw = (float) -(side * 90.0D);
            server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f -55 %.2f %.1f -12", x, z, yaw));
            context.waitTicks(3);
            context.takeScreenshot("closeblast_orbit_" + side);
        }
        // Standing in the smoke.
        server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f -55 %.2f 0 -20", at.x, at.z - 3.0D));
        context.waitTicks(3);
        context.takeScreenshot("closeblast_orbit_inside");
    }
}
