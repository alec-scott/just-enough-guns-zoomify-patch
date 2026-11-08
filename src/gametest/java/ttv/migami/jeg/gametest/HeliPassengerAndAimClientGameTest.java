package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.client.overlay.VehicleHudOverlay;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * In the AH-6: the pilot can pitch the nose well down (past 40 degrees) to fire on targets close beneath; a passenger
 * with their own hands keeps their health bar and crosshair and (with -PwithZoomify) zooms with C, while the pilot's C
 * stays free look and Zoomify stays off.
 */
public class HeliPassengerAndAimClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("heli passenger/aim report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("justEnoughGuns config vehicle helicopter weightyFlight true");
            server.runCommand("tp @a 0 ~ 0 0 0");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runCommand("execute as @p at @s run summon jeg:ah6 ~ ~ ~4 {Rotation:[0f,0f]}");
            context.waitTicks(5);
            server.runCommand("ride @p mount @e[type=jeg:ah6,limit=1,sort=nearest]");
            context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
            context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));

            // Pilot: take off, then push the mouse down as far as it goes.
            context.getInput().holdKey(options -> options.keyJump);
            int startup = context.computeOnClient(mc -> vehicle(mc).helicopterStartupTicks());
            context.waitTicks(startup + 80);
            context.getInput().releaseKey(options -> options.keyJump);
            context.waitTicks(20);
            for (int tick = 0; tick < 60; tick++) {
                context.getInput().moveCursor(0.0D, 40.0D);
                context.waitTick();
            }
            context.waitTicks(20);
            float pitch = context.computeOnClient(mc -> vehicle(mc).getXRot());
            context.takeScreenshot("heli_pilot_aim_down");
            report.append(String.format("pilot full nose-down aim: hull pitch %.1f%n", pitch));
            if (pitch < 42.0F) {
                failures.append(String.format("pilot could only pitch %.1f degrees nose down%n", pitch));
            }
            boolean pilotStatusHidden = context.computeOnClient(mc -> VehicleHudOverlay.shouldReplaceHotbar());
            report.append("pilot: vehicle HUD replaces status bars = ").append(pilotStatusHidden).append('\n');
            if (!pilotStatusHidden) {
                failures.append("pilot should get the vehicle HUD in place of their status bars\n");
            }
            float pilotZoom = this.zoomWithC(context, report, "pilot");
            if (!Float.isNaN(pilotZoom) && pilotZoom < 0.9F) {
                failures.append("Zoomify zoomed for the pilot; C should stay free look\n");
            }

            // Passenger: same helicopter, other seat, own rifle.
            context.getInput().moveCursor(0.0D, -2400.0D);
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                ((VehicleEntity) player.getVehicle()).changeSeat(player);
            });
            context.waitTicks(10);
            server.runCommand("item replace entity @p weapon.mainhand with jeg:assault_rifle");
            context.waitTicks(20);
            boolean passengerStatusHidden = context.computeOnClient(mc -> VehicleHudOverlay.shouldReplaceHotbar());
            int seat = context.computeOnClient(mc -> vehicle(mc).getSeatIndex(mc.player));
            report.append("passenger seat ").append(seat).append(": vehicle HUD replaces status bars = ")
                    .append(passengerStatusHidden).append('\n');
            context.takeScreenshot("heli_passenger_hud");
            if (passengerStatusHidden) {
                failures.append("passenger lost their health bar and crosshair\n");
            }
            float passengerZoom = this.zoomWithC(context, report, "passenger");
            if (!Float.isNaN(passengerZoom) && passengerZoom > 0.75F) {
                failures.append("Zoomify did not zoom for the passenger\n");
            }
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    /** Holds C (free look, and Zoomify's zoom when loaded); returns zoomed/normal FOV, or NaN without Zoomify. */
    private float zoomWithC(ClientGameTestContext context, StringBuilder report, String who) {
        boolean zoomify = context.computeOnClient(mc -> zoomKey(mc.options) != null);
        float normal = context.computeOnClient(mc -> mc.gameRenderer.getMainCamera().getFov());
        context.getInput().holdKey(options -> KeyBindings.VEHICLE_FREE_LOOK);
        context.waitTicks(30);
        float held = context.computeOnClient(mc -> mc.gameRenderer.getMainCamera().getFov());
        boolean freeLook = context.computeOnClient(mc -> KeyBindings.vehicleFreeLookDown());
        context.takeScreenshot("heli_" + who + "_c_held");
        context.getInput().releaseKey(options -> KeyBindings.VEHICLE_FREE_LOOK);
        context.waitTicks(20);
        report.append(String.format("%s holding C: fov %.1f -> %.1f, free look %s, zoomify %s%n", who, normal, held,
                freeLook, zoomify ? "loaded" : "absent"));
        return zoomify ? held / normal : Float.NaN;
    }

    private static KeyMapping zoomKey(Options options) {
        for (KeyMapping mapping : options.keyMappings) {
            if (mapping.getName().startsWith(KeyBindings.ZOOMIFY_ZOOM_KEY_PREFIX)) {
                return mapping;
            }
        }
        return null;
    }

    private static VehicleEntity vehicle(Minecraft mc) {
        return (VehicleEntity) mc.player.getVehicle();
    }
}
