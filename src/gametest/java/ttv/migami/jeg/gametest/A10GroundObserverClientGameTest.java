package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.client.audio.JetAudio;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;

/**
 * What someone on the ground hears of an A-10 making a low gun run over them. An AI-flown jet comes in at 45 blocks,
 * full throttle, fires a burst while still 100 to 170 blocks out (the server sends it from about 190) and passes overhead. The player stands on the
 * ground and every sound they hear is recorded. Fails unless: the engines are heard, higher pitched coming in than
 * going away (Doppler); the gun is heard only once its sound could have travelled from the jet (about a tick per 17
 * blocks); and the A-10 flyby plays as it passes.
 */
public class A10GroundObserverClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final double SOUND_SPEED = 343.0D / 20.0D;

    private record Heard(int tick, String sound, double distance, float pitch) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder log = new StringBuilder();
        StringBuilder failures = new StringBuilder();
        List<Heard> heard = new ArrayList<>();
        int[] tick = {0};
        // The server's view distance: past about 190 blocks it does not send the jet at all.
        int previousDistance = context.computeOnClient(mc -> mc.options.renderDistance().get());
        context.runOnClient(mc -> mc.options.renderDistance().set(12));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender(false, 2400);
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 -60 0 180 -30");
            server.runCommand("forceload add -16 -720 16 400");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(20);
            int viewDistance = server.computeOnServer(s -> s.getPlayerList().getViewDistance());
            log.append("server view distance ").append(viewDistance).append(System.lineSeparator());
            SoundEventListener listener = (sound, events, range) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && sound.getIdentifier().getPath().contains("a10")) {
                    double distance = mc.player.position().distanceTo(new Vec3(sound.getX(), sound.getY(), sound.getZ()));
                    heard.add(new Heard(tick[0], sound.getIdentifier().getPath(), distance, sound.getPitch()));
                }
            };
            context.runOnClient(mc -> mc.getSoundManager().addListener(listener));
            // The jet, 720 blocks out and 45 up, heading this way at speed with a husk at the controls.
            String jetId = server.computeOnServer(minecraftServer -> {
                var level = minecraftServer.overworld();
                VehicleEntity jet = ModEntities.A10.get().create(level, EntitySpawnReason.COMMAND);
                jet.snapTo(0.0D, -15.0D, -720.0D, 0.0F, 0.0F);
                jet.setDeltaMovement(0.0D, 0.0D, 3.0D);
                level.addFreshEntity(jet);
                var pilot = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
                pilot.setNoAi(true);
                pilot.setSilent(true);
                pilot.snapTo(jet.getX(), jet.getY(), jet.getZ(), 0.0F, 0.0F);
                level.addFreshEntity(pilot);
                pilot.startRiding(jet, true, true);
                jet.addAmmoForAi(Reference.id("rifle_ammo"), 640);
                jet.reloadAiVehicleWeapons();
                return jet.getUUID().toString();
            });
            int firedAt = -1;
            double firedDistance = 0.0D;
            int heardGunAt = -1;
            float approachPitch = 0.0F;
            float awayPitch = 0.0F;
            for (tick[0] = 0; tick[0] < 520; tick[0]++) {
                double z = server.computeOnServer(minecraftServer -> {
                    var entity = minecraftServer.overworld().getEntity(java.util.UUID.fromString(jetId));
                    if (!(entity instanceof VehicleEntity jet)) {
                        return Double.NaN;
                    }
                    // Full throttle, level; the gun fires while the jet is 170 to 100 blocks out.
                    jet.setAiVehicleInput(new VehicleInput(true, false, false, false, false, false, false, false, false, false,
                            false, false, -1, false, false, 0.0F, 0.0F));
                    boolean fire = jet.getZ() > -170.0D && jet.getZ() < -100.0D;
                    if (jet.getControllingPassenger() != null) {
                        jet.setAiWeaponControl(jet.getControllingPassenger(), fire, false);
                    }
                    return jet.getZ();
                });
                context.waitTick();
                if (Double.isNaN(z)) {
                    failures.append("the jet disappeared\n");
                    break;
                }
                String state = context.computeOnClient(mc -> {
                    VehicleEntity jet = nearestJet(mc);
                    if (jet == null) {
                        return "no jet";
                    }
                    return String.format(java.util.Locale.ROOT, "z=%.0f dist=%.0f firing=%s spool=%.2f %s", jet.getZ(), mc.player.distanceTo(jet),
                            jet.isJetGunFiring(), jet.jetSpool(), JetAudio.describe(jet));
                });
                log.append("t").append(tick[0]).append(' ').append(state).append('\n');
                if (firedAt < 0 && state.contains("firing=true")) {
                    firedAt = tick[0];
                    firedDistance = context.computeOnClient(mc -> {
                        VehicleEntity jet = nearestJet(mc);
                        return jet == null ? 0.0D : (double) mc.player.distanceTo(jet);
                    });
                }
                if (heardGunAt < 0 && state.contains(" gun=true")) {
                    heardGunAt = tick[0];
                }
                float pitch = parse(state, "pitch=");
                if (z > -120.0D && z < -60.0D && state.contains("engine=true")) {
                    approachPitch = Math.max(approachPitch, pitch);
                }
                if (z > 60.0D && z < 120.0D && state.contains("engine=true")) {
                    awayPitch = awayPitch == 0.0F ? pitch : Math.min(awayPitch, pitch);
                }
                if (tick[0] % 40 == 0 || (z > -60.0D && z < 60.0D && tick[0] % 4 == 0)) {
                    context.takeScreenshot("a10_ground_t" + tick[0]);
                }
                if (z > 400.0D) {
                    break;
                }
            }
            context.runOnClient(mc -> mc.getSoundManager().removeListener(listener));
            long flybys = heard.stream().filter(h -> h.sound().contains("flyby")).count();
            long stops = heard.stream().filter(h -> h.sound().contains("gun_stop")).count();
            double expectedDelay = firedDistance / SOUND_SPEED;
            String report = String.format(java.util.Locale.ROOT,
                    "ground observer: engine pitch coming in %.3f, going away %.3f; gun fired at t%d from %.0f blocks, heard at t%d (%d ticks later, sound takes %.1f); flybys %d, gun stop tails %d%nsounds: %s",
                    approachPitch, awayPitch, firedAt, firedDistance, heardGunAt, heardGunAt - firedAt, expectedDelay, flybys, stops,
                    heard.stream().map(h -> "t" + h.tick() + ":" + h.sound() + "@" + Math.round(h.distance())).toList());
            LOGGER.info("[a10-ground]\n{}", log);
            LOGGER.info("{}", report);
            if (approachPitch <= 0.0F || awayPitch <= 0.0F || approachPitch < awayPitch * 1.05F) {
                failures.append("no Doppler on the engines (coming in ").append(approachPitch).append(", going away ").append(awayPitch).append(")\n");
            }
            if (firedAt < 0 || heardGunAt < 0 || Math.abs((heardGunAt - firedAt) - expectedDelay) > 4.0D || heardGunAt - firedAt < 3) {
                failures.append("the gun was not heard as late as its sound takes to arrive\n");
            }
            if (flybys == 0) {
                failures.append("no flyby as it passed overhead\n");
            }
            if (failures.length() > 0) {
                throw new AssertionError(failures + report);
            }
        } finally {
            context.runOnClient(mc -> mc.options.renderDistance().set(previousDistance));
        }
    }

    private static float parse(String state, String key) {
        int at = state.indexOf(key);
        if (at < 0) {
            return 0.0F;
        }
        String rest = state.substring(at + key.length());
        int end = rest.indexOf(' ');
        return Float.parseFloat(end < 0 ? rest : rest.substring(0, end));
    }

    private static VehicleEntity nearestJet(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        VehicleEntity best = null;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof VehicleEntity vehicle && vehicle.isJet()
                    && (best == null || mc.player.distanceTo(vehicle) < mc.player.distanceTo(best))) {
                best = vehicle;
            }
        }
        return best;
    }
}
