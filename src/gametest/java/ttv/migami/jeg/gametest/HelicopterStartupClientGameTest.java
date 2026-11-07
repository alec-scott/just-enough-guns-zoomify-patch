package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.client.audio.HelicopterEngineAudio;
import ttv.migami.jeg.vehicle.client.audio.VehicleDoppler;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import java.lang.reflect.Field;
import java.util.Map;

/** Cold entry, a latched ignition, gradual rotor speed, no early lift, and the transition into flight audio. */
public class HelicopterStartupClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    private record State(double y, float rotor, float progress, String sound) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        this.checkDoppler();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            this.start(context, server, "ah6", 0);
            this.start(context, server, "mi28", 300);
            this.emptyBattery(context, server);
        }
    }

    private void start(ClientGameTestContext context, TestServerContext server, String id, int x) {
        server.runCommand("ride @p dismount");
        server.runCommand("tp @p " + x + " -60 0 0 0");
        server.runCommand("execute as @p at @s run summon jeg:" + id + " ~ ~ ~5");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=jeg:" + id + ",sort=nearest,limit=1]");
        context.waitFor(mc -> mc.player.getVehicle() instanceof VehicleEntity);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(40);
        State cold = this.state(context);
        this.check(cold.progress() == 0.0F && cold.rotor() < 0.0005F && cold.sound().equals("SILENT"), "Entry must leave the engine off", cold);

        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(20);
        State forward = this.state(context);
        this.check(forward.progress() == 0.0F && forward.rotor() < 0.0005F, "W must not start the engine", forward);
        context.getInput().releaseKey(options -> options.keyUp);

        int duration = context.computeOnClient(mc -> ((VehicleEntity) mc.player.getVehicle()).helicopterStartupTicks());
        context.getInput().holdKey(options -> options.keyJump);
        context.waitTicks(duration / 4);
        State quarter = this.state(context);
        this.check(quarter.progress() > 0.1F && quarter.progress() < 0.4F && quarter.rotor() > 0.001F && quarter.rotor() < 0.04F,
                "Rotor speed must rise gradually", quarter);
        this.check(quarter.sound().equals("STARTUP") && Math.abs(quarter.y() - cold.y()) < 0.3D, "Startup must play on the ground", quarter);
        context.takeScreenshot("startup_" + id + "_quarter");

        // Releasing collective does not cancel ignition, and still cannot lift the helicopter early.
        context.getInput().releaseKey(options -> options.keyJump);
        context.waitTicks(duration / 4);
        State half = this.state(context);
        this.check(half.progress() > quarter.progress() && half.rotor() > quarter.rotor() && Math.abs(half.y() - cold.y()) < 0.3D,
                "Ignition should continue after releasing collective", half);
        context.getInput().holdKey(options -> options.keyJump);
        context.waitTicks(duration / 2 - 6);
        State beforeLift = this.state(context);
        this.check(beforeLift.progress() < 1.0F && Math.abs(beforeLift.y() - cold.y()) < 0.3D, "No lift before operating RPM", beforeLift);
        SoundInstance flightLoop = context.computeOnClient(mc -> {
            int vehicleId = ((VehicleEntity) mc.player.getVehicle()).getId();
            SoundInstance startupSound = engineSound("FADING", vehicleId);
            SoundInstance loop = engineSound("SOUNDS", vehicleId);
            if (startupSound == null || loop == null || startupSound == loop
                    || !mc.getSoundManager().isActive(startupSound) || !mc.getSoundManager().isActive(loop)
                    || startupSound.getVolume() <= 0.01F || loop.getVolume() <= 0.01F) {
                throw new AssertionError("Startup and flight must overlap on active nonzero-volume channels");
            }
            double energy = Math.pow(startupSound.getVolume() / 0.65F, 2.0D) + Math.pow(loop.getVolume() / 0.65F, 2.0D);
            if (energy < 0.65D || energy > 1.1D) {
                throw new AssertionError("The engine crossfade must maintain a steady level: " + energy);
            }
            return loop;
        });
        context.takeScreenshot("startup_" + id + "_before_lift");
        context.waitTicks(50);
        State flying = this.state(context);
        this.check(flying.progress() == 1.0F && flying.rotor() > 0.075F && flying.y() > cold.y() + 1.0D && flying.sound().equals("FLIGHT"),
                "Full rotor speed must permit lift and switch to the flight loop", flying);
        boolean serverFlying = server.computeOnServer(minecraftServer -> {
            var player = minecraftServer.getPlayerList().getPlayers().get(0);
            VehicleEntity vehicle = (VehicleEntity) player.getVehicle();
            return vehicle.helicopterStartupProgress() == 1.0F && vehicle.getY() > cold.y() + 1.0D;
        });
        this.check(serverFlying, "The authoritative server must agree that takeoff completed", flying);
        context.getInput().releaseKey(options -> options.keyJump);
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(20);
        this.check(context.computeOnClient(mc -> engineSound("SOUNDS", ((VehicleEntity) mc.player.getVehicle()).getId()) == flightLoop
                && mc.getSoundManager().isActive(flightLoop)), "Taking off and accelerating must keep the same engine loop", flying);
        context.getInput().releaseKey(options -> options.keyUp);
        context.takeScreenshot("startup_" + id + "_flying");
        LOGGER.info("[heli-startup] {} duration={} cold={} quarter={} half={} beforeLift={} flying={}", id, duration, cold, quarter, half, beforeLift, flying);
    }

    private void emptyBattery(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("ride @p dismount");
        server.runCommand("tp @p 600 -60 0 0 0");
        server.runCommand("execute as @p at @s run summon jeg:ah6 ~ ~ ~5 {Energy:0}");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=jeg:ah6,sort=nearest,limit=1]");
        context.waitFor(mc -> mc.player.getVehicle() instanceof VehicleEntity);
        server.runOnServer(minecraftServer -> {
            VehicleEntity vehicle = (VehicleEntity) minecraftServer.getPlayerList().getPlayers().get(0).getVehicle();
            vehicle.consumeEnergy(vehicle.vehicleEnergy());
        });
        context.waitTicks(10);
        context.getInput().holdKey(options -> options.keyJump);
        context.waitTicks(60);
        State state = this.state(context);
        context.getInput().releaseKey(options -> options.keyJump);
        this.check(state.progress() == 0.0F && state.rotor() < 0.0005F && state.sound().equals("SILENT"), "An empty battery must not start the engine", state);
        LOGGER.info("[heli-startup] empty battery={}", state);
    }

    private State state(ClientGameTestContext context) {
        return context.computeOnClient(mc -> {
            VehicleEntity vehicle = (VehicleEntity) mc.player.getVehicle();
            return new State(vehicle.getY(), vehicle.propellerSpeed(), vehicle.helicopterStartupProgress(), HelicopterEngineAudio.phase(vehicle.getId()));
        });
    }

    private static SoundInstance engineSound(String mapName, int id) {
        try {
            Field field = HelicopterEngineAudio.class.getDeclaredField(mapName);
            field.setAccessible(true);
            return (SoundInstance) ((Map<?, ?>) field.get(null)).get(id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private void check(boolean condition, String message, State state) {
        if (!condition) {
            throw new AssertionError(message + ": " + state);
        }
    }

    private void checkDoppler() {
        Vec3 source = new Vec3(0.0D, 0.0D, 100.0D);
        Vec3 approach = new Vec3(0.0D, 0.0D, -2.0D);
        float approaching = VehicleDoppler.multiplier(source, approach, Vec3.ZERO, Vec3.ZERO);
        float receding = VehicleDoppler.multiplier(source, approach.scale(-1.0D), Vec3.ZERO, Vec3.ZERO);
        float coMoving = VehicleDoppler.multiplier(source, approach, Vec3.ZERO, approach);
        float sideways = VehicleDoppler.multiplier(source, new Vec3(2.0D, 0.0D, 0.0D), Vec3.ZERO, Vec3.ZERO);
        if (approaching <= 1.0F || receding >= 1.0F || Math.abs(coMoving - 1.0F) > 0.001F || Math.abs(sideways - 1.0F) > 0.001F) {
            throw new AssertionError("Doppler must raise approach pitch, lower receding pitch, and preserve co-moving/transverse pitch");
        }
        LOGGER.info("[doppler] approach={} recede={} coMoving={} transverse={}", approaching, receding, coMoving, sideways);
    }
}
