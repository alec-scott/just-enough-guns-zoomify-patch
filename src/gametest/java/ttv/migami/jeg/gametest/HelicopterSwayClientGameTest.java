package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Flies an AH-6, strafes right and lets go, logging the hull roll every tick and taking screenshots at the peak
 * sways in both the chase camera and first person. Expected: the hull swings left when the strafe starts, right when it
 * stops, and the first-person horizon stays level throughout.
 */
public class HelicopterSwayClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int STRAFE_TICKS = 60;
    private static final int SETTLE_TICKS = 80;
    private static final int PRESS_PEAK_TICK = 12;
    private static final int RELEASE_PEAK_TICK = 11;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 0");
            server.runCommand("execute as @p at @s run summon jeg:ah6 ~ ~ ~4");
            context.waitTicks(5);
            server.runCommand("ride @p mount @e[type=jeg:ah6,limit=1,sort=nearest]");
            context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);

            context.getInput().holdKey(options -> options.keyJump);
            int startup = context.computeOnClient(mc -> ((VehicleEntity) mc.player.getVehicle()).helicopterStartupTicks());
            context.waitTicks(startup + 80);
            context.getInput().releaseKey(options -> options.keyJump);
            context.waitTicks(SETTLE_TICKS);

            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            this.strafeRightAndRelease(context, "f5");
            context.waitTicks(SETTLE_TICKS);

            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            this.strafeRightAndRelease(context, "fp");
            context.waitTicks(SETTLE_TICKS);

            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            this.steerWhileFlyingForward(context);
        }
    }

    /** Mouse steering at speed used to read as sideways jolts and rock the hull; only a gentle turn bank should remain. */
    private void steerWhileFlyingForward(ClientGameTestContext context) {
        StringBuilder rolls = new StringBuilder();
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(40);
        for (int tick = 1; tick <= 80; tick++) {
            context.getInput().moveCursor((tick / 10) % 2 == 0 ? 40.0D : -40.0D, 0.0D);
            context.waitTick();
            rolls.append(this.sample(context, "steer", tick));
            if (tick == 35) {
                context.takeScreenshot("heli_steer");
            }
        }
        context.getInput().releaseKey(options -> options.keyUp);
        LOGGER.info("[sway] steer\n{}", rolls);
    }

    private void strafeRightAndRelease(ClientGameTestContext context, String label) {
        StringBuilder rolls = new StringBuilder();
        context.getInput().holdKey(options -> options.keyRight);
        for (int tick = 1; tick <= STRAFE_TICKS; tick++) {
            context.waitTick();
            rolls.append(this.sample(context, label + " press", tick));
            if (tick == PRESS_PEAK_TICK) {
                context.takeScreenshot("heli_" + label + "_press_peak");
            }
        }
        context.takeScreenshot("heli_" + label + "_steady_strafe");
        context.getInput().releaseKey(options -> options.keyRight);
        for (int tick = 1; tick <= SETTLE_TICKS; tick++) {
            context.waitTick();
            rolls.append(this.sample(context, label + " release", tick));
            if (tick == RELEASE_PEAK_TICK) {
                context.takeScreenshot("heli_" + label + "_release_peak");
            }
        }
        LOGGER.info("[sway] {}\n{}", label, rolls);
    }

    private String sample(ClientGameTestContext context, String phase, int tick) {
        return context.computeOnClient(mc -> {
            VehicleEntity vehicle = vehicle(mc);
            if (vehicle == null) {
                return phase + " t" + tick + " no vehicle\n";
            }
            Vec3 velocity = vehicle.getDeltaMovement();
            Vec3 forward = Vec3.directionFromRotation(0.0F, vehicle.getYRot());
            double leftSpeed = velocity.x * forward.z - velocity.z * forward.x;
            return String.format("%s t%d roll=%.1f yaw=%.1f leftSpeed=%.3f cameraRoll=%.1f y=%.1f%n",
                    phase, tick, vehicle.roll(), vehicle.getYRot(), leftSpeed, vehicle.cameraRoll(1.0F), vehicle.getY());
        });
    }

    private static VehicleEntity vehicle(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof VehicleEntity vehicle ? vehicle : null;
    }
}
