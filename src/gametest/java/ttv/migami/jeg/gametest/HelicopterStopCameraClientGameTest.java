package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Flies forward at full speed and stops, once by letting go and once hard on S, in both cameras. Logs hull and camera pitch
 * per tick so the swing back can be read off. Then climbs from high up to check the altitude ceiling sits above the world.
 */
public class HelicopterStopCameraClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("justEnoughGuns config vehicle helicopter weightyFlight true");
            this.fly(context, server, "ah6", 0);
            this.fly(context, server, "mi28", 400);
            this.ceiling(context, server);
        }
    }

    private void mount(ClientGameTestContext context, TestServerContext server, String id, int x) {
        server.runCommand("ride @p dismount");
        server.runCommand("tp @a " + x + " ~ 0 0 0");
        context.waitTicks(5);
        server.runCommand("execute as @p at @s run summon jeg:" + id + " ~ ~ ~4");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=jeg:" + id + ",limit=1,sort=nearest]");
        context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
    }

    private void fly(ClientGameTestContext context, TestServerContext server, String id, int x) {
        this.mount(context, server, id, x);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        StringBuilder log = new StringBuilder();
        context.getInput().holdKey(options -> options.keyJump);
        int startup = context.computeOnClient(mc -> vehicle(mc).helicopterStartupTicks());
        context.waitTicks(startup + 120);
        context.getInput().releaseKey(options -> options.keyJump);
        context.waitTicks(30);

        for (CameraType camera : new CameraType[]{CameraType.THIRD_PERSON_BACK, CameraType.FIRST_PERSON}) {
            String view = camera == CameraType.FIRST_PERSON ? "fp" : "chase";
            context.runOnClient(mc -> mc.options.setCameraType(camera));
            // Hands off.
            context.getInput().holdKey(options -> options.keyUp);
            this.record(context, log, id + " " + view + " accel", 110, null);
            context.getInput().releaseKey(options -> options.keyUp);
            this.record(context, log, id + " " + view + " release", 80, id + "_" + view + "_release");
            // Hard on S.
            context.getInput().holdKey(options -> options.keyUp);
            this.record(context, log, id + " " + view + " accel2", 110, null);
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().holdKey(options -> options.keyDown);
            this.record(context, log, id + " " + view + " hardstop", 40, id + "_" + view + "_hardstop");
            context.getInput().releaseKey(options -> options.keyDown);
            this.record(context, log, id + " " + view + " settle", 50, null);
        }
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        // Side to side: build up a strafe, let go and coast, then a strafe reversed hard the other way.
        context.getInput().holdKey(options -> options.keyRight);
        this.record(context, log, id + " strafe", 70, id + "_strafe");
        context.getInput().releaseKey(options -> options.keyRight);
        this.record(context, log, id + " strafe-release", 90, null);
        context.getInput().holdKey(options -> options.keyRight);
        this.record(context, log, id + " strafe2", 70, null);
        context.getInput().releaseKey(options -> options.keyRight);
        context.getInput().holdKey(options -> options.keyLeft);
        this.record(context, log, id + " strafe-reverse", 90, null);
        context.getInput().releaseKey(options -> options.keyLeft);
        this.record(context, log, id + " strafe-settle", 60, null);
        // Up and down: the climb should carry on a little after letting go, and the stop should dip.
        context.getInput().holdKey(options -> options.keyJump);
        this.record(context, log, id + " climb", 60, null);
        context.getInput().releaseKey(options -> options.keyJump);
        this.record(context, log, id + " climb-release", 50, null);
        LOGGER.info("[heli-stop] {}\n{}", id, log);
    }

    private void ceiling(ClientGameTestContext context, TestServerContext server) {
        this.mount(context, server, "ah6", 800);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        StringBuilder log = new StringBuilder();
        context.getInput().holdKey(options -> options.keyJump);
        int startup = context.computeOnClient(mc -> vehicle(mc).helicopterStartupTicks());
        context.waitTicks(startup + 60);
        // Well past the old limit of 172 blocks above the ground, with the ground out of probe range.
        server.runCommand("execute as @e[type=jeg:ah6,limit=1,sort=nearest] at @s run tp @s ~ 250 ~");
        for (int tick = 1; tick <= 520; tick++) {
            context.waitTick();
            if (tick % 20 == 0) {
                log.append(this.sample(context, "ceiling", tick));
            }
        }
        context.takeScreenshot("heli_ceiling");
        context.getInput().releaseKey(options -> options.keyJump);
        LOGGER.info("[heli-stop] ceiling\n{}", log);
    }

    private void record(ClientGameTestContext context, StringBuilder log, String phase, int ticks, String shots) {
        for (int tick = 1; tick <= ticks; tick++) {
            context.waitTick();
            log.append(this.sample(context, phase, tick));
            if (shots != null && (tick % 8 == 0 && tick <= 32 || tick == 64)) {
                context.takeScreenshot("stop_" + shots + "_t" + tick);
            }
        }
    }

    private String sample(ClientGameTestContext context, String phase, int tick) {
        return context.computeOnClient(mc -> {
            VehicleEntity vehicle = vehicle(mc);
            if (vehicle == null) {
                return phase + " t" + tick + " no vehicle\n";
            }
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 velocity = vehicle.getDeltaMovement();
            Vec3 forward = Vec3.directionFromRotation(0.0F, vehicle.getYRot());
            double forwardSpeed = velocity.x * forward.x + velocity.z * forward.z;
            Vec3 toCamera = camera.position().subtract(vehicle.position());
            double slip = velocity.x * forward.z - velocity.z * forward.x;
            return String.format("%s t%d fwd=%.3f slip=%.3f vy=%.3f hull=%.1f roll=%.1f cam=%.1f playerX=%.1f camDy=%.2f y=%.1f%n",
                    phase, tick, forwardSpeed, slip, velocity.y, vehicle.getXRot(), vehicle.roll(), camera.xRot(),
                    mc.player.getXRot(), toCamera.y, vehicle.getY());
        });
    }

    private static VehicleEntity vehicle(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof VehicleEntity vehicle ? vehicle : null;
    }
}
