package ttv.migami.jeg.gametest;

import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.client.render.AircraftLights;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;

/**
 * The aircraft's lights at night. An Mi-28 and an AH-6 hover ten blocks up, held in place, and an A-10 sits on its gear,
 * each with a husk at the controls, while the player looks at them at midnight: position lights, flashing beacons and
 * strobes, the cockpit glow, the helicopters' searchlights on the ground and the A-10's taxi light. Then from the pilot's
 * seat of an empty Mi-28 and of the A-10 (its green displays starting up, then running), and by day, when every light
 * but the beams still shows. Fails unless the searchlights and the taxi light switch on at night and every one of them
 * is off by day.
 */
public class AircraftNightLightsClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final Vec3 MI28 = new Vec3(-28.5D, -50.0D, 0.5D);
    private static final Vec3 AH6 = new Vec3(28.5D, -50.0D, 0.5D);
    private static final Vec3 A10 = new Vec3(0.5D, -60.0D, 40.5D);
    private static final Vec3 EMPTY_MI28 = new Vec3(0.5D, -60.0D, -40.5D);

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule advance_weather false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set midnight");
            server.runCommand("tp @a 0 -55 0 0 0");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
            });
            context.waitTicks(10);
            String[] ids = server.computeOnServer(minecraftServer -> {
                var level = minecraftServer.overworld();
                String[] out = new String[4];
                int i = 0;
                for (var spawn : new Object[][]{{ModEntities.MI28.get(), MI28, true}, {ModEntities.AH6.get(), AH6, true},
                        {ModEntities.A10.get(), A10, true}, {ModEntities.MI28.get(), EMPTY_MI28, false}}) {
                    @SuppressWarnings("unchecked")
                    var type = (EntityType<? extends VehicleEntity>) spawn[0];
                    Vec3 at = (Vec3) spawn[1];
                    VehicleEntity vehicle = type.create(level, EntitySpawnReason.COMMAND);
                    vehicle.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
                    level.addFreshEntity(vehicle);
                    if ((Boolean) spawn[2]) {
                        Mob pilot = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
                        pilot.setNoAi(true);
                        pilot.setSilent(true);
                        pilot.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
                        level.addFreshEntity(pilot);
                        pilot.startRiding(vehicle, true, true);
                    }
                    out[i++] = vehicle.getUUID().toString();
                }
                return out;
            });
            this.hold(context, server, ids, 60);

            float night = context.computeOnClient(mc -> AircraftLights.nightSwitch(1.0F));
            int nightSpots = context.computeOnClient(mc -> AircraftLights.spots().size());
            LOGGER.info("aircraft night lights: switch {}, lit spots {}", night, nightSpots);

            // The Mi-28 from ahead and to its left: port light, cockpit, searchlight beam and pool.
            this.view(context, server, ids, MI28, "^7 ^1 ^14", "^ ^-4 ^6", "lights_mi28_front_left");
            this.view(context, server, ids, MI28, "^ ^1 ^-16", "^ ^ ^", "lights_mi28_behind");
            this.view(context, server, ids, MI28, "^-12 ^-6 ^2", "^ ^ ^", "lights_mi28_right_low");
            this.view(context, server, ids, MI28, "^14 ^6 ^-4", "^ ^ ^", "lights_mi28_left_high");
            // From above and behind: the rotor-tip lights' ring, the formation strips and the top beacon.
            this.view(context, server, ids, MI28, "^-7 ^10 ^-17", "^ ^3 ^", "lights_mi28_rotor_ring");
            // The AH-6, side on and from behind.
            this.view(context, server, ids, AH6, "^-9 ^1 ^4", "^ ^ ^", "lights_ah6_right");
            this.view(context, server, ids, AH6, "^2 ^2 ^-10", "^ ^ ^", "lights_ah6_behind");
            this.view(context, server, ids, AH6, "^ ^-3 ^20", "^ ^ ^", "lights_ah6_ahead");
            // The A-10: taxi light, wingtips, fins, cockpit.
            this.view(context, server, ids, A10, "^10 ^4 ^14", "^ ^2 ^", "lights_a10_front_left");
            this.view(context, server, ids, A10, "^ ^5 ^-22", "^ ^2 ^", "lights_a10_behind");
            this.view(context, server, ids, A10, "^-4 ^4.5 ^7.5", "^ ^3.2 ^6", "lights_a10_cockpit_outside");
            this.view(context, server, ids, A10, "^-1.4 ^5.4 ^2.4", "^ ^3.2 ^4.7", "lights_a10_cockpit_close");
            // Strobes and beacons flash: a run of frames from behind the A-10.
            this.lookAt(server, A10, "^ ^7 ^-26", "^ ^2 ^");
            for (int frame = 0; frame < 6; frame++) {
                this.hold(context, server, ids, 2);
                context.takeScreenshot("lights_a10_flash_" + frame);
            }

            // From the pilot's seat of an empty Mi-28: boarding it powers it up.
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runCommand("gamemode creative @a");
            boolean boarded = server.computeOnServer(minecraftServer -> {
                var player = minecraftServer.getPlayerList().getPlayers().getFirst();
                return minecraftServer.overworld().getEntity(UUID.fromString(ids[3])) instanceof VehicleEntity heli
                        && player.startRiding(heli, true, true);
            });
            this.hold(context, server, ids, 20);
            context.runOnClient(mc -> mc.player.setXRot(18.0F));
            this.hold(context, server, ids, 2);
            context.takeScreenshot("lights_mi28_pilot_night");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            this.hold(context, server, ids, 4);
            context.takeScreenshot("lights_mi28_pilot_chase_night");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

            // From the A-10's seat: the instrument panel and its screens, ahead and below.
            server.computeOnServer(minecraftServer -> {
                var player = minecraftServer.getPlayerList().getPlayers().getFirst();
                player.stopRiding();
                if (!(minecraftServer.overworld().getEntity(UUID.fromString(ids[2])) instanceof VehicleEntity jet)) {
                    return false;
                }
                // The husk gives up its seat.
                for (var passenger : java.util.List.copyOf(jet.getPassengers())) {
                    passenger.discard();
                }
                return player.startRiding(jet, true, true);
            });
            // The displays flicker on as the pilot boards, then settle.
            this.hold(context, server, ids, 6);
            context.takeScreenshot("lights_a10_pilot_booting");
            this.hold(context, server, ids, 30);
            context.takeScreenshot("lights_a10_pilot_night");
            String eye = context.computeOnClient(mc -> {
                var jet = mc.player.getVehicle();
                var camera = mc.gameRenderer.getMainCamera().position();
                return String.format("camera %.3f %.3f %.3f from jet %.3f %.3f %.3f", camera.x, camera.y, camera.z,
                        jet.getX(), jet.getY(), jet.getZ());
            });
            LOGGER.warn("aircraft night lights: A-10 pilot {}", eye);
            server.computeOnServer(minecraftServer -> {
                var player = minecraftServer.getPlayerList().getPlayers().getFirst();
                player.stopRiding();
                return minecraftServer.overworld().getEntity(UUID.fromString(ids[3])) instanceof VehicleEntity heli
                        && player.startRiding(heli, true, true);
            });
            this.hold(context, server, ids, 5);

            // By day: no searchlights, the position lights faint points.
            server.runCommand("time set noon");
            this.hold(context, server, ids, 20);
            context.takeScreenshot("lights_mi28_pilot_day");
            server.computeOnServer(minecraftServer -> {
                var player = minecraftServer.getPlayerList().getPlayers().getFirst();
                player.stopRiding();
                return minecraftServer.overworld().getEntity(UUID.fromString(ids[2])) instanceof VehicleEntity jet
                        && player.startRiding(jet, true, true);
            });
            this.hold(context, server, ids, 30);
            context.takeScreenshot("lights_a10_pilot_day");
            int daySpots = context.computeOnClient(mc -> AircraftLights.spots().size());
            server.computeOnServer(minecraftServer -> {
                minecraftServer.getPlayerList().getPlayers().getFirst().stopRiding();
                return true;
            });
            server.runCommand("gamemode spectator @a");
            this.view(context, server, ids, MI28, "^7 ^1 ^14", "^ ^-4 ^6", "lights_mi28_front_left_day");
            this.view(context, server, ids, A10, "^10 ^4 ^14", "^ ^2 ^", "lights_a10_front_left_day");
            this.view(context, server, ids, AH6, "^2 ^2 ^-10", "^ ^ ^", "lights_ah6_behind_day");
            LOGGER.info("aircraft night lights: boarded {}, lit spots by day {}", boarded, daySpots);

            if (night < 1.0F) {
                throw new AssertionError("the night lights did not switch on at midnight (switch " + night + ")");
            }
            if (nightSpots < 3) {
                throw new AssertionError("expected both searchlights and the taxi light to light the ground at night, got " + nightSpots);
            }
            if (daySpots != 0) {
                throw new AssertionError("searchlights still lit by day: " + daySpots);
            }
        } finally {
            context.runOnClient(mc -> {
                mc.options.hideGui = false;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
        }
    }

    /** Standing at `from` relative to the aircraft parked at `spot`, looking at `at`, then a screenshot. */
    private void view(ClientGameTestContext context, TestServerContext server, String[] ids, Vec3 spot, String from,
                      String at, String name) {
        this.lookAt(server, spot, from, at);
        this.hold(context, server, ids, 4);
        context.takeScreenshot(name);
    }

    private void lookAt(TestServerContext server, Vec3 spot, String from, String at) {
        server.runCommand("execute positioned " + spot.x + " " + spot.y + " " + spot.z + " rotated 0 0 run tp @p " + from
                + " facing " + at);
    }

    /**
     * Holds the aircraft where they are: the helicopters hovering with their collective up (so the rotors stay at speed),
     * the jet at idle, the boarded Mi-28 on the ground.
     */
    private void hold(ClientGameTestContext context, TestServerContext server, String[] ids, int ticks) {
        Vec3[] spots = {MI28, AH6, A10, EMPTY_MI28};
        for (int tick = 0; tick < ticks; tick++) {
            server.runOnServer(minecraftServer -> {
                for (int i = 0; i < ids.length; i++) {
                    if (minecraftServer.overworld().getEntity(UUID.fromString(ids[i])) instanceof VehicleEntity vehicle) {
                        if (i < 3) {
                            vehicle.setAiVehicleInput(new VehicleInput(false, false, false, false, false, i < 2, false, false,
                                    false, false, false, false, -1, false, false, 0.0F, 0.0F));
                        }
                        vehicle.setDeltaMovement(Vec3.ZERO);
                        vehicle.setPos(spots[i]);
                        vehicle.setYRot(0.0F);
                        vehicle.setXRot(0.0F);
                    }
                }
            });
            context.waitTick();
        }
    }
}
