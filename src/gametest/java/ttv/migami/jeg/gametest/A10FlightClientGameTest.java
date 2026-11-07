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
 * Looks the A-10 over on the ground, then flies it: take-off roll, climb, turns, a dive and a landing approach.
 * Logs speed, attitude and height per tick and saves screenshots along the way.
 */
public class A10FlightClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("tp @a 0 ~ 0 0 0");
            context.waitTicks(5);
            server.runCommand("execute as @p at @s run summon jeg:a10 ~ ~ ~20");
            context.waitTicks(10);

            // Walk-around: front quarter, side and rear views of the parked jet.
            server.runCommand("execute as @p at @e[type=jeg:a10,limit=1] run tp @s ~-14 ~4 ~30 facing entity @e[type=jeg:a10,limit=1]");
            context.waitTicks(20);
            context.takeScreenshot("a10_parked_front");
            server.runCommand("execute as @p at @e[type=jeg:a10,limit=1] run tp @s ~-22 ~3 ~ facing entity @e[type=jeg:a10,limit=1]");
            context.waitTicks(10);
            context.takeScreenshot("a10_parked_side");
            server.runCommand("execute as @p at @e[type=jeg:a10,limit=1] run tp @s ~6 ~5 ~-20 facing entity @e[type=jeg:a10,limit=1]");
            context.waitTicks(10);
            context.takeScreenshot("a10_parked_rear");

            server.runCommand("ride @p mount @e[type=jeg:a10,limit=1]");
            context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
            context.waitTicks(10);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(5);
            context.takeScreenshot("a10_cockpit");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.takeScreenshot("a10_chase_parked");

            StringBuilder log = new StringBuilder();
            this.fly(context, log);
            LOGGER.info("[a10]\n{}", log);
        }
    }

    private void fly(ClientGameTestContext context, StringBuilder log) {
        // Throttle up and roll down the runway, then ease the nose up to rotate.
        context.getInput().holdKey(options -> options.keyUp);
        for (int tick = 1; tick <= 120; tick++) {
            context.waitTick();
            log.append(this.sample(context, "takeoff-roll", tick));
            if (tick == 60) {
                context.takeScreenshot("a10_takeoff_roll");
            }
        }
        for (int tick = 1; tick <= 60; tick++) {
            if (tick <= 15) {
                context.getInput().moveCursor(0.0D, -25.0D);
            }
            context.waitTick();
            log.append(this.sample(context, "rotate", tick));
            if (tick == 40) {
                context.takeScreenshot("a10_climb_out");
            }
        }
        this.record(context, log, "climb", 100);
        // Level off.
        for (int tick = 1; tick <= 40; tick++) {
            if (tick <= 12) {
                context.getInput().moveCursor(0.0D, 25.0D);
            }
            context.waitTick();
            log.append(this.sample(context, "level", tick));
        }
        // Hard right turn with the mouse.
        for (int tick = 1; tick <= 60; tick++) {
            context.getInput().moveCursor(40.0D, 0.0D);
            context.waitTick();
            log.append(this.sample(context, "turn-right", tick));
            if (tick == 30) {
                context.takeScreenshot("a10_turn_right_chase");
            }
        }
        this.record(context, log, "turn-exit", 40);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        for (int tick = 1; tick <= 50; tick++) {
            context.getInput().moveCursor(-40.0D, 0.0D);
            context.waitTick();
            log.append(this.sample(context, "turn-left-fp", tick));
            if (tick == 30) {
                context.takeScreenshot("a10_turn_left_cockpit");
            }
        }
        this.record(context, log, "turn-left-exit", 30);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        // Throttle back and dive.
        context.getInput().releaseKey(options -> options.keyUp);
        for (int tick = 1; tick <= 60; tick++) {
            if (tick <= 10) {
                context.getInput().moveCursor(0.0D, 30.0D);
            }
            context.waitTick();
            log.append(this.sample(context, "dive", tick));
            if (tick == 30) {
                context.takeScreenshot("a10_dive");
            }
        }
        this.record(context, log, "idle", 100);
        context.takeScreenshot("a10_end");
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
            Vec3 forward = Vec3.directionFromRotation(vehicle.getXRot(), vehicle.getYRot());
            double forwardSpeed = velocity.dot(forward);
            return String.format("%s t%d speed=%.3f fwd=%.3f vy=%.3f pitch=%.1f roll=%.1f yaw=%.1f y=%.1f ground=%s hp=%.1f%n",
                    phase, tick, velocity.length(), forwardSpeed, velocity.y,
                    vehicle.getXRot(), vehicle.roll(), vehicle.getYRot(), vehicle.getY(), vehicle.onGround(), vehicle.vehicleHealth());
        });
    }

    private static VehicleEntity vehicle(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof VehicleEntity vehicle ? vehicle : null;
    }
}
