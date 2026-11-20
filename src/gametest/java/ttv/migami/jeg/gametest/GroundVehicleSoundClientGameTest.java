package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.client.audio.FarAircraftAudio;
import ttv.migami.jeg.vehicle.client.audio.GroundVehicleAudio;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Fleet plan Phase 0.1 and 0.2: the armoured vehicles heard as the aircraft are (BulldozerFarSoundClientGameTest and
 * BulldozerAudioClientGameTest are the models).
 * <ul>
 *   <li>Far: a crewed YX-100 is held at one distance after another out to 1,100 blocks (render distance 12, so entity
 *   range is 192 as on yftm). Fails unless it is heard 800 blocks off and silent past 1,024, only gets quieter going
 *   out (no jump where it leaves entity range), and from 300 blocks the distant layer dominates.</li>
 *   <li>Views: the player drives a BMP-2. In first person the crew hears the muffled cab loop centred on them and not
 *   the exterior engine; in third person the exterior engine, positioned at the vehicle; switching views keeps the same
 *   loops playing rather than restarting them.</li>
 * </ul>
 */
public class GroundVehicleSoundClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int[] DISTANCES = {30, 120, 180, 260, 400, 600, 800, 950, 1100};

    private record Heard(int distance, boolean entity, float near, float distant, float far) {
        float total() {
            return (float) Math.sqrt(near * near + distant * distant + far * far);
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        int previousDistance = context.computeOnClient(mc -> mc.options.renderDistance().get());
        context.runOnClient(mc -> mc.options.renderDistance().set(12));
        StringBuilder report = new StringBuilder("ground vehicle sound report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender(false, 2400);
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 -59 0 -90 0");
            for (int distance : DISTANCES) {
                server.runCommand(String.format(Locale.ROOT, "forceload add %d -8 %d 8", distance - 8, distance + 8));
            }
            context.waitTicks(40);

            // ---- Far: a crewed YX-100 held at each distance.
            String id = server.computeOnServer(s -> {
                var level = s.overworld();
                VehicleEntity tank = ModEntities.YX100.get().create(level, EntitySpawnReason.COMMAND);
                tank.snapTo(DISTANCES[0], -60, 0.5D, 90, 0);
                tank.addEnergy(tank.maxVehicleEnergy());
                level.addFreshEntity(tank);
                var driver = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
                driver.setNoAi(true);
                driver.setSilent(true);
                driver.snapTo(tank.getX(), tank.getY(), tank.getZ(), 0, 0);
                level.addFreshEntity(driver);
                // Since yftm.22 only a player or tagged crew may drive or man a gun.
                driver.addTag(ttv.migami.jeg.vehicle.ai.EnemyVehicleController.ENEMY_VEHICLE_CREW_TAG);
                driver.startRiding(tank, true, true);
                return tank.getUUID().toString();
            });
            int entityId = server.computeOnServer(s -> vehicle(s, id).getId());
            this.hold(context, server, id, DISTANCES[0], 60);
            // Crewed in view: this client saw its engine start, and hears the start.
            List<String> crewedShots = context.computeOnClient(mc -> GroundVehicleAudio.shots(entityId));
            report.append("YX-100 crewed in view, one-shots: ").append(crewedShots).append('\n');
            if (!crewedShots.contains("engine_start")) failures.append("a vehicle crewed in view did not play its start\n");
            List<Heard> heard = new ArrayList<>();
            for (int distance : DISTANCES) {
                this.hold(context, server, id, distance, 90);
                heard.add(this.sample(context, entityId, distance));
            }
            for (Heard h : heard) {
                report.append(String.format(Locale.ROOT, "YX-100 at %4d blocks: %s near %.3f, distant %.3f, far %.3f -> %.3f%n", h.distance(),
                        h.entity() ? "entity " : "tracked", h.near(), h.distant(), h.far(), h.total()));
            }
            float previous = Float.MAX_VALUE;
            for (Heard h : heard) {
                float total = h.total();
                if (total > previous * 1.15F + 0.002F) {
                    failures.append(String.format(Locale.ROOT, "got louder going out to %d (%.3f after %.3f)%n", h.distance(), total, previous));
                }
                previous = total;
                if (h.distance() == 800 && total < 0.01F) failures.append(String.format(Locale.ROOT, "not heard 800 blocks off (%.3f)%n", total));
                if (h.distance() > 1024 && total > 0.002F) failures.append(String.format(Locale.ROOT, "still heard %d blocks off (%.3f)%n", h.distance(), total));
                if (h.distance() >= 300 && h.distance() <= 1024 && h.near() >= Math.max(h.distant(), h.far())) {
                    failures.append(String.format(Locale.ROOT, "did not sound distant at %d blocks%n", h.distance()));
                }
            }
            if (heard.getFirst().total() < 0.2F) failures.append(String.format(Locale.ROOT, "quiet close up (%.3f)%n", heard.getFirst().total()));
            // Back from beyond entity range, still running: arriving at a running vehicle never plays a second start.
            this.hold(context, server, id, DISTANCES[0], 40);
            int returnedId = server.computeOnServer(s -> vehicle(s, id).getId());
            List<String> arrivalShots = context.computeOnClient(mc -> GroundVehicleAudio.shots(returnedId));
            report.append("YX-100 back in entity range, running: one-shots ").append(arrivalShots).append('\n');
            if (arrivalShots.contains("engine_start")) failures.append("a vehicle arriving already running played its start\n");
            server.runCommand("kill @e[type=!player]");
            context.waitTicks(20);

            // ---- Views: the player drives a BMP-2.
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0 -60 -6 0 0");
            int bmp = server.computeOnServer(s -> {
                VehicleEntity vehicle = ModEntities.BMP2.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                vehicle.snapTo(0.5D, -60, 0.5D, 0, 0);
                vehicle.addEnergy(vehicle.maxVehicleEnergy());
                s.overworld().addFreshEntity(vehicle);
                return vehicle.getId();
            });
            context.waitTicks(10);
            server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(s.overworld().getEntity(bmp), true, true));
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            // The real start plays at once; the loops wait for the engine to catch in it (3 s into the BMP-2's).
            context.waitTicks(8);
            List<String> startShots = context.computeOnClient(mc -> GroundVehicleAudio.shots(bmp));
            context.waitTicks(22);
            float cabCranking = context.computeOnClient(mc -> Math.max(GroundVehicleAudio.loopVolume(bmp, "engine_cab"), GroundVehicleAudio.loopVolume(bmp, "engine_cab_idle")));
            report.append(String.format(Locale.ROOT, "BMP-2 boarded: one-shots %s; cab loop while cranking %.3f%n", startShots, cabCranking));
            if (!startShots.contains("engine_start")) failures.append("boarding did not play the engine start\n");
            if (cabCranking > 0.05F) failures.append(String.format(Locale.ROOT, "the loops ran before the start caught (%.3f)%n", cabCranking));
            context.waitTicks(80);
            Map<String, SoundInstance> firstLoops = context.computeOnClient(mc -> GroundVehicleAudio.loops(bmp));
            float cabFirst = context.computeOnClient(mc -> Math.max(GroundVehicleAudio.loopVolume(bmp, "engine_cab"), GroundVehicleAudio.loopVolume(bmp, "engine_cab_idle")));
            float outsideFirst = context.computeOnClient(mc -> Math.max(GroundVehicleAudio.loopVolume(bmp, "engine_idle"), GroundVehicleAudio.loopVolume(bmp, "engine_load")));
            boolean relativeFirst = context.computeOnClient(mc -> GroundVehicleAudio.loops(bmp).get("engine_cab").isRelative());
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(30);
            Map<String, SoundInstance> thirdLoops = context.computeOnClient(mc -> GroundVehicleAudio.loops(bmp));
            float cabThird = context.computeOnClient(mc -> Math.max(GroundVehicleAudio.loopVolume(bmp, "engine_cab"), GroundVehicleAudio.loopVolume(bmp, "engine_cab_idle")));
            float outsideThird = context.computeOnClient(mc -> Math.max(GroundVehicleAudio.loopVolume(bmp, "engine_idle"), GroundVehicleAudio.loopVolume(bmp, "engine_load")));
            boolean relativeThird = context.computeOnClient(mc -> GroundVehicleAudio.loops(bmp).get("engine_idle").isRelative());
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            report.append(String.format(Locale.ROOT, "BMP-2 first person: cab %.3f, exterior %.3f, relative %s; third person: cab %.3f, exterior %.3f, relative %s; loops %s%n",
                    cabFirst, outsideFirst, relativeFirst, cabThird, outsideThird, relativeThird, firstLoops.keySet()));
            if (!(cabFirst > 0.2F && outsideFirst < 0.01F && relativeFirst)) {
                failures.append("first person aboard must hear the cab loop, centred, and not the exterior engine\n");
            }
            if (!(outsideThird > 0.2F && cabThird < 0.01F && !relativeThird)) {
                failures.append("third person aboard must hear the exterior engine at the vehicle, not the cab loop\n");
            }
            if (firstLoops.isEmpty() || !firstLoops.keySet().equals(thirdLoops.keySet())
                    || firstLoops.entrySet().stream().anyMatch(e -> thirdLoops.get(e.getKey()) != e.getValue())) {
                failures.append("the loops restarted across the view switch\n");
            }
            // Leaving the controls stops the engine with its real shutdown.
            server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).stopRiding());
            context.waitTicks(6);
            List<String> stopShots = context.computeOnClient(mc -> GroundVehicleAudio.shots(bmp));
            report.append("BMP-2 left: one-shots ").append(stopShots).append('\n');
            if (!stopShots.contains("engine_stop")) failures.append("leaving did not play the engine stop\n");
        } finally {
            context.runOnClient(mc -> mc.options.renderDistance().set(previousDistance));
            LOG.info("{}", report);
        }
        if (failures.length() > 0) {
            LOG.error("[ground-vehicle-sound] FAILED: {}", failures);
            throw new AssertionError(failures.toString() + report);
        }
        LOG.info("[ground-vehicle-sound] COMPLETE");
    }

    private Heard sample(ClientGameTestContext context, int entityId, int distance) {
        return context.computeOnClient(mc -> {
            boolean entity = mc.level.getEntity(entityId) instanceof VehicleEntity;
            float near = (float) Math.hypot(GroundVehicleAudio.loopVolume(entityId, "engine_idle"), GroundVehicleAudio.loopVolume(entityId, "engine_load"));
            float distant = GroundVehicleAudio.loopVolume(entityId, "engine_distant");
            return new Heard(distance, entity, near < 0.002F ? 0 : near, distant < 0.002F ? 0 : distant, FarAircraftAudio.engineVolume(entityId));
        });
    }

    private void hold(ClientGameTestContext context, TestServerContext server, String id, int distance, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            server.runOnServer(s -> {
                VehicleEntity vehicle = vehicle(s, id);
                vehicle.snapTo(distance, -60, 0.5D, 90, 0);
                vehicle.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            });
            context.waitTick();
        }
    }

    private static VehicleEntity vehicle(MinecraftServer server, String id) {
        if (server.overworld().getEntity(java.util.UUID.fromString(id)) instanceof VehicleEntity vehicle) {
            return vehicle;
        }
        throw new AssertionError("the vehicle is gone");
    }
}
