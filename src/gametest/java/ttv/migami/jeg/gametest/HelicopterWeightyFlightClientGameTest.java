package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import java.util.function.Function;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Flies the AH-6 and then the heavier Mi-28 through the same script with weighty flight on: take off, full forward,
 * let go (flare), a fast mouse turn at speed (drift), and a climb release (lift carry-on). Logs speed, tilt and height per
 * tick so the two airframes can be compared, plus screenshots at full tilt and mid-flare.
 */
public class HelicopterWeightyFlightClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("justEnoughGuns config vehicle helicopter weightyFlight true");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            this.fly(context, server, "ah6", 0);
            this.fly(context, server, "mi28", 400);
        }
    }

    private void fly(ClientGameTestContext context, TestServerContext server, String id, int x) {
        server.runCommand("ride @p dismount");
        server.runCommand("tp @a " + x + " ~ 0 0 0");
        context.waitTicks(5);
        server.runCommand("execute as @p at @s run summon jeg:" + id + " ~ ~ ~4");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=jeg:" + id + ",limit=1,sort=nearest]");
        context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);

        StringBuilder log = new StringBuilder();
        context.getInput().holdKey(options -> options.keyJump);
        this.record(context, log, id + " climb", 160);
        context.getInput().releaseKey(options -> options.keyJump);
        this.record(context, log, id + " climb-release", 40);

        context.getInput().holdKey(options -> options.keyUp);
        for (int tick = 1; tick <= 120; tick++) {
            context.waitTick();
            log.append(this.sample(context, id + " forward", tick));
            if (tick == 100) {
                context.takeScreenshot("weighty_" + id + "_full_tilt");
            }
        }
        // Fast turn at speed while still holding forward: momentum should drift round, not snap.
        for (int tick = 1; tick <= 20; tick++) {
            context.getInput().moveCursor(60.0D, 0.0D);
            context.waitTick();
            log.append(this.sample(context, id + " turn", tick));
        }
        this.record(context, log, id + " turn-exit", 20);
        context.getInput().releaseKey(options -> options.keyUp);
        for (int tick = 1; tick <= 100; tick++) {
            context.waitTick();
            log.append(this.sample(context, id + " release", tick));
            if (tick == 15) {
                context.takeScreenshot("weighty_" + id + "_flare");
            }
        }

        // Fly back up to speed and stop hard with S: big nose-up flare, a balloon upward, then a rock forward.
        context.getInput().holdKey(options -> options.keyUp);
        this.record(context, log, id + " reaccelerate", 100);
        context.getInput().releaseKey(options -> options.keyUp);
        context.getInput().holdKey(options -> options.keyDown);
        for (int tick = 1; tick <= 50; tick++) {
            context.waitTick();
            log.append(this.sample(context, id + " hardstop", tick));
            if (tick == 12) {
                context.takeScreenshot("weighty_" + id + "_hard_stop");
            }
        }
        context.getInput().releaseKey(options -> options.keyDown);
        this.record(context, log, id + " hardstop-settle", 60);

        // Double-tap D for a right barrel roll in the chase camera, then double-tap A for a left one in first person.
        this.barrelRoll(context, log, id, "right", options -> options.keyRight, CameraType.THIRD_PERSON_BACK);
        this.barrelRoll(context, log, id, "left", options -> options.keyLeft, CameraType.FIRST_PERSON);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        LOGGER.info("[weighty] {}\n{}", id, log);
    }

    private void barrelRoll(ClientGameTestContext context, StringBuilder log, String id, String side,
                            Function<Options, KeyMapping> key, CameraType camera) {
        context.runOnClient(mc -> mc.options.setCameraType(camera));
        // Double-tap and keep holding: the hull rolls by hand for as long as the key is down, then carries on a little and
        // rights itself once let go.
        int holdTicks = "mi28".equals(id) ? 25 : 20;
        context.getInput().holdKey(key);
        context.waitTicks(2);
        context.getInput().releaseKey(key);
        context.waitTicks(2);
        context.getInput().holdKey(key);
        for (int tick = 1; tick <= holdTicks; tick++) {
            context.waitTick();
            log.append(this.sample(context, id + " roll-" + side + " held", tick));
            if (tick == 12) {
                context.takeScreenshot("weighty_" + id + "_roll_" + side + "_inverted");
            }
        }
        context.getInput().releaseKey(key);
        for (int tick = 1; tick <= 60; tick++) {
            context.waitTick();
            log.append(this.sample(context, id + " roll-" + side + " released", tick));
        }
    }

    private void record(ClientGameTestContext context, StringBuilder log, String phase, int ticks) {
        for (int tick = 1; tick <= ticks; tick++) {
            context.waitTick();
            log.append(this.sample(context, phase, tick));
        }
    }

    private String sample(ClientGameTestContext context, String phase, int tick) {
        return context.computeOnClient(mc -> {
            VehicleEntity vehicle = vehicle(mc);
            if (vehicle == null) {
                return phase + " t" + tick + " no vehicle\n";
            }
            Vec3 velocity = vehicle.getDeltaMovement();
            Vec3 forward = Vec3.directionFromRotation(0.0F, vehicle.getYRot());
            double forwardSpeed = velocity.x * forward.x + velocity.z * forward.z;
            double slip = velocity.x * forward.z - velocity.z * forward.x;
            return String.format("%s t%d speed=%.3f fwd=%.3f slip=%.3f vy=%.3f pitch=%.1f roll=%.1f camRoll=%.1f yaw=%.1f y=%.1f%n",
                    phase, tick, velocity.horizontalDistance(), forwardSpeed, slip, velocity.y,
                    vehicle.getXRot(), vehicle.roll(), vehicle.cameraRoll(1.0F), vehicle.getYRot(), vehicle.getY());
        });
    }

    private static VehicleEntity vehicle(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof VehicleEntity vehicle ? vehicle : null;
    }
}
