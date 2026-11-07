package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The reversed third-person view in a helicopter is an action camera on the tail boom looking back at the cabin. It must
 * sit behind the hull, face forward along it, and bank with the airframe.
 */
public class HelicopterTailCameraClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            this.fly(context, server, "ah6", 0);
            this.fly(context, server, "mi28", 300);
        }
    }

    private void fly(ClientGameTestContext context, TestServerContext server, String id, int x) {
        server.runCommand("ride @p dismount");
        server.runCommand("tp @p " + x + " -60 0 0 0");
        server.runCommand("execute as @p at @s run summon jeg:" + id + " ~ ~ ~5 {Rotation:[0f,0f]}");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=jeg:" + id + ",sort=nearest,limit=1]");
        context.waitFor(mc -> mc.player.getVehicle() instanceof VehicleEntity);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.waitTicks(20);
        context.takeScreenshot("tailcam_" + id + "_parked");
        String parked = this.check(context, id, "parked");

        int duration = context.computeOnClient(mc -> ((VehicleEntity) mc.player.getVehicle()).helicopterStartupTicks());
        context.getInput().holdKey(options -> options.keyJump);
        context.waitTicks(duration + 40);
        context.getInput().holdKey(options -> options.keyLeft);
        context.waitTicks(12);
        context.takeScreenshot("tailcam_" + id + "_banking");
        String banking = this.check(context, id, "banking");
        context.getInput().releaseKey(options -> options.keyLeft);
        context.getInput().releaseKey(options -> options.keyJump);
        context.waitTicks(10);
        LOGGER.info("[tail-cam] {} parked: {} | banking: {}", id, parked, banking);
    }

    private String check(ClientGameTestContext context, String id, String label) {
        return context.computeOnClient(mc -> {
            VehicleEntity vehicle = (VehicleEntity) mc.player.getVehicle();
            var camera = mc.gameRenderer.getMainCamera();
            Vec3 forward = Vec3.directionFromRotation(0.0F, vehicle.getYRot());
            Vec3 offset = camera.position().subtract(vehicle.position());
            Vec3 look = new Vec3(camera.forwardVector());
            double behind = offset.dot(forward);
            String result = String.format("behind=%.2f lookForward=%.2f roll=%.1f camRoll=%.1f fov=%.1f",
                    behind, look.dot(forward), vehicle.roll(1.0F), Math.toDegrees(Math.asin(Math.max(-1.0D, Math.min(1.0D, -camera.leftVector().y())))),
                    mc.options.fov().get() * 1.0D);
            if (behind > -1.5D || look.dot(forward) < 0.85D) {
                throw new AssertionError(id + " " + label + ": the tail camera must sit behind the cabin looking forward along the boom: " + result);
            }
            return result;
        });
    }
}
