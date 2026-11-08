package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.render.BulletTrailRenderer;
import ttv.migami.jeg.client.render.gun.GunMuzzleTracker;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * A passenger firing their own rifle out of the AH-6 while it flies: the tracer must leave the rifle, not the spot the
 * helicopter was at when the server fired the round (behind it, by the time the client draws it). Parked, cruising and
 * fast, in first and third person.
 */
public class PassengerRifleClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 -40 0 0 0");
            server.runCommand("execute as @p at @s run summon jeg:ah6 ~ ~ ~4 {Rotation:[0f,0f],NoGravity:1b}");
            context.waitTicks(5);
            server.runCommand("ride @p mount @e[type=jeg:ah6,limit=1,sort=nearest]");
            context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
            // Into a passenger seat, alone aboard.
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                ((VehicleEntity) player.getVehicle()).changeSeat(player);
            });
            context.waitTicks(10);
            int seat = context.computeOnClient(mc -> ((VehicleEntity) mc.player.getVehicle()).getSeatIndex(mc.player));
            server.runCommand("item replace entity @p weapon.mainhand with jeg:assault_rifle");
            context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));

            StringBuilder failures = new StringBuilder();
            for (double speed : new double[] {0.0D, 1.0D, 2.0D}) {
                for (CameraType camera : new CameraType[] {CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_BACK}) {
                    String result = this.fire(context, server, camera, speed, seat);
                    if (result != null) {
                        failures.append(result).append('\n');
                    }
                }
            }
            if (failures.length() > 0) {
                throw new AssertionError("Passenger rifle tracers did not leave the rifle:\n" + failures);
            }
        }
    }

    /** Flies the helicopter north at {@code speed} blocks a tick while the passenger fires; null when the tracer left the rifle. */
    private String fire(ClientGameTestContext context, TestServerContext server, CameraType camera, double speed, int seat) {
        boolean firstPerson = camera == CameraType.FIRST_PERSON;
        context.runOnClient(mc -> mc.options.setCameraType(camera));
        double worst = 0.0D;
        String detail = "";
        for (int tick = 0; tick < 30; tick++) {
            server.runOnServer(s -> {
                for (Entity entity : s.overworld().getAllEntities()) {
                    if (entity instanceof VehicleEntity vehicle) {
                        vehicle.setDeltaMovement(0.0D, 0.0D, speed);
                        vehicle.setPos(vehicle.getX(), -40.0D, vehicle.getZ() + speed);
                    }
                }
            });
            if (tick == 15) {
                context.getInput().holdKey(options -> options.keyAttack);
            }
            context.waitTick();
            if (tick >= 18) {
                Vec3[] points = context.computeOnClient(mc -> new Vec3[] {
                        GunMuzzleTracker.muzzle(mc.player.getId(), firstPerson), BulletTrailRenderer.lastDrawnStart(), mc.player.getEyePosition()});
                if (points[0] != null && points[1] != null) {
                    double off = points[0].distanceTo(points[1]);
                    if (off > worst) {
                        worst = off;
                        detail = String.format("muzzle=%s start=%s eye=%s", points[0], points[1], points[2]);
                    }
                }
            }
            if (tick == 22) {
                context.takeScreenshot("passenger_rifle_" + (int) speed + "_" + camera.name().toLowerCase());
            }
        }
        context.getInput().releaseKey(options -> options.keyAttack);
        context.waitTicks(5);
        LOGGER.info("[passenger-rifle] seat {} speed {} {}: worst tracer start to muzzle {} ({})", seat, speed, camera,
                String.format("%.2f", worst), detail);
        return worst < 0.6D ? null : String.format("speed %.0f %s: start %.2f from the rifle; %s", speed, camera, worst, detail);
    }
}
