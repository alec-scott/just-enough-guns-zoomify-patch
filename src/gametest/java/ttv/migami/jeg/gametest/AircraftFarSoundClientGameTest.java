package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.client.audio.FarAircraftAudio;
import ttv.migami.jeg.vehicle.client.audio.HelicopterEngineAudio;
import ttv.migami.jeg.vehicle.client.audio.JetAudio;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;

/**
 * How far off an A-10 and an AH-6 are heard, and how they fade. The player stands still (render distance 12, so the
 * server sends the vehicles themselves out to 192 blocks, as on yftm) while each aircraft, its engines running, is held
 * at one distance after another out to 1100 blocks. At each the volume of every one of its sounds the player hears is
 * read: the full engine, its muffled distant version, and past entity range the distant one played from the server's
 * tracks. Fails unless: each is still heard 800 blocks off and silent past 1024; it only ever gets quieter further out
 * (no jump where the vehicle leaves entity range); from 300 blocks on it is the distant version that is heard; and the
 * A-10's gun is heard 600 blocks off and the AH-6's 120 blocks off.
 */
public class AircraftFarSoundClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int[] DISTANCES = {40, 120, 180, 260, 400, 600, 800, 950, 1100};
    private static final double ALTITUDE = 40.0D;

    private record Heard(int distance, boolean entity, float near, float distant, float far, float gun) {
        float total() {
            return (float) Math.sqrt(this.near * this.near + this.distant * this.distant + this.far * this.far);
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        int previousDistance = context.computeOnClient(mc -> mc.options.renderDistance().get());
        context.runOnClient(mc -> mc.options.renderDistance().set(12));
        StringBuilder report = new StringBuilder("aircraft far sound report\n");
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
            for (String vehicle : new String[]{"a10", "ah6"}) {
                this.listen(context, server, vehicle, report, failures);
            }
        } finally {
            context.runOnClient(mc -> mc.options.renderDistance().set(previousDistance));
            LOGGER.info("{}", report);
        }
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    private void listen(ClientGameTestContext context, TestServerContext server, String vehicle, StringBuilder report, StringBuilder failures) {
        boolean jet = vehicle.equals("a10");
        String id = server.computeOnServer(minecraftServer -> {
            var level = minecraftServer.overworld();
            var type = (EntityType<?>) net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(Reference.id(vehicle));
            VehicleEntity aircraft = (VehicleEntity) type.create(level, EntitySpawnReason.COMMAND);
            aircraft.snapTo(DISTANCES[0], ALTITUDE, 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(aircraft);
            var pilot = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
            pilot.setNoAi(true);
            pilot.setSilent(true);
            pilot.snapTo(aircraft.getX(), aircraft.getY(), aircraft.getZ(), 0.0F, 0.0F);
            level.addFreshEntity(pilot);
            pilot.startRiding(aircraft, true, true);
            aircraft.addAmmoForAi(Reference.id("rifle_ammo"), 300);
            aircraft.reloadAiVehicleWeapons();
            return aircraft.getUUID().toString();
        });
        int entityId = server.computeOnServer(minecraftServer -> aircraft(minecraftServer, id).getId());
        // Engines up: the jet's spool from cold, the helicopter's hot start.
        this.hold(context, server, id, jet, DISTANCES[0], false, jet ? 200 : 60);

        List<Heard> heard = new ArrayList<>();
        for (int distance : DISTANCES) {
            // Long enough for the sound from 1100 blocks to arrive (64 ticks) and for the fades to settle.
            this.hold(context, server, id, jet, distance, false, 90);
            heard.add(this.sample(context, jet, entityId, distance));
            String serverState = server.computeOnServer(minecraftServer -> {
                VehicleEntity aircraft = aircraft(minecraftServer, id);
                return String.format(Locale.ROOT, "  %s at %d: server loudness %.3f, rotor %.4f, startup %.2f, spool %.2f%n", vehicle, distance,
                        ttv.migami.jeg.vehicle.util.VehicleSoundHelper.aircraftEngineLoudness(aircraft), aircraft.propellerSpeed(),
                        aircraft.helicopterStartupProgress(), aircraft.jetSpool());
            });
            report.append(serverState);
        }
        float gunFar = 0.0F;
        if (jet) {
            this.hold(context, server, id, true, 600, false, 40);
            this.hold(context, server, id, true, 600, true, 70);
            gunFar = this.sample(context, true, entityId, 600).gun();
            this.hold(context, server, id, true, 600, false, 5);
        } else {
            // The helicopter's gun loop, 120 blocks off and 99 up: caught as it starts, its volume read while it fires.
            java.util.concurrent.atomic.AtomicReference<net.minecraft.client.resources.sounds.SoundInstance> loop = new java.util.concurrent.atomic.AtomicReference<>();
            net.minecraft.client.sounds.SoundEventListener listener = (sound, events, range) -> {
                if (sound.getIdentifier().getPath().contains("fire")) {
                    loop.set(sound);
                }
            };
            context.runOnClient(mc -> mc.getSoundManager().addListener(listener));
            this.hold(context, server, id, false, 120, false, 30);
            this.hold(context, server, id, false, 120, true, 40);
            gunFar = context.computeOnClient(mc -> loop.get() == null ? 0.0F : loop.get().getVolume());
            context.runOnClient(mc -> mc.getSoundManager().removeListener(listener));
            this.hold(context, server, id, false, 120, false, 5);
        }
        server.runOnServer(minecraftServer -> {
            VehicleEntity aircraft = aircraft(minecraftServer, id);
            aircraft.ejectPassengers();
            aircraft.discard();
        });
        context.waitTicks(40);

        for (Heard h : heard) {
            report.append(String.format(Locale.ROOT, "%s at %4d blocks: %s near %.3f, distant %.3f, far %.3f -> %.3f%n", vehicle, h.distance(),
                    h.entity() ? "entity" : "tracked", h.near(), h.distant(), h.far(), h.total()));
        }
        int gunDistance = jet ? 600 : 120;
        report.append(String.format(Locale.ROOT, "%s gun heard %d blocks off at %.3f%n", vehicle, gunDistance, gunFar));
        if (gunFar < 0.05F) {
            failures.append(String.format(Locale.ROOT, "the %s's gun was not heard %d blocks off%n", vehicle, gunDistance));
        }
        float previous = Float.MAX_VALUE;
        for (Heard h : heard) {
            float total = h.total();
            if (total > previous * 1.15F + 0.002F) {
                failures.append(String.format(Locale.ROOT, "%s got louder going out to %d blocks (%.3f after %.3f)%n", vehicle, h.distance(), total, previous));
            }
            previous = total;
            if (h.distance() == 800 && total < 0.01F) {
                failures.append(String.format(Locale.ROOT, "%s was not heard 800 blocks off (%.3f)%n", vehicle, total));
            }
            if (h.distance() > 1024 && total > 0.002F) {
                failures.append(String.format(Locale.ROOT, "%s was still heard %d blocks off (%.3f)%n", vehicle, h.distance(), total));
            }
            if (h.distance() >= 300 && h.distance() <= 1024 && h.near() >= Math.max(h.distant(), h.far())) {
                failures.append(String.format(Locale.ROOT, "%s at %d blocks did not sound distant%n", vehicle, h.distance()));
            }
        }
        if (heard.getFirst().total() < 0.3F) {
            failures.append(String.format(Locale.ROOT, "%s was quiet close up (%.3f)%n", vehicle, heard.getFirst().total()));
        }
    }

    private Heard sample(ClientGameTestContext context, boolean jet, int entityId, int distance) {
        return context.computeOnClient(mc -> {
            boolean entity = mc.level.getEntity(entityId) instanceof VehicleEntity;
            float near = 0.0F;
            float distant = 0.0F;
            float gun = 0.0F;
            if (mc.level.getEntity(entityId) instanceof VehicleEntity aircraft) {
                String state = jet ? JetAudio.describe(aircraft) : HelicopterEngineAudio.describe(entityId);
                near = parse(state, "engineVol=");
                distant = parse(state, jet ? "distantEngineVol=" : "distantVol=");
                gun = jet ? parse(state, "gunVol=") + parse(state, "distantGunVol=") : 0.0F;
            }
            float far = FarAircraftAudio.engineVolume(entityId);
            gun += FarAircraftAudio.gunVolume(entityId);
            return new Heard(distance, entity, near, distant, far, gun);
        });
    }

    /** Keeps the aircraft at this distance (the jet flying across the line to the player at full throttle). */
    private void hold(ClientGameTestContext context, TestServerContext server, String id, boolean jet, int distance, boolean fire, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            server.runOnServer(minecraftServer -> {
                VehicleEntity aircraft = aircraft(minecraftServer, id);
                // The jet at full throttle; the helicopter on full collective, which keeps its rotor at operating speed.
                aircraft.setAiVehicleInput(new VehicleInput(jet, false, false, false, false, !jet, false, false, false, false,
                        false, false, -1, false, false, 0.0F, 0.0F));
                if (aircraft.getControllingPassenger() != null) {
                    aircraft.setAiWeaponControl(aircraft.getControllingPassenger(), fire, false);
                }
                aircraft.setDeltaMovement(jet ? new Vec3(0.0D, 0.0D, aircraft.jetTopSpeed() * 0.95D) : Vec3.ZERO);
                aircraft.setPos(distance, ALTITUDE, 0.5D);
            });
            context.waitTick();
        }
    }

    private static VehicleEntity aircraft(net.minecraft.server.MinecraftServer server, String id) {
        if (server.overworld().getEntity(UUID.fromString(id)) instanceof VehicleEntity vehicle) {
            return vehicle;
        }
        throw new AssertionError("the aircraft disappeared");
    }

    private static float parse(String state, String key) {
        for (String part : state.split(" ")) {
            if (part.startsWith(key)) {
                return Float.parseFloat(part.substring(key.length()));
            }
        }
        return 0.0F;
    }
}
