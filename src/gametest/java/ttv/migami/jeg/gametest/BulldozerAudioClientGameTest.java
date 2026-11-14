package ttv.migami.jeg.gametest;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.client.audio.BulldozerAudio;
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;

/**
 * Reads every engine loop's OpenAL source (playing, gain, positioning mode and coordinates) with the driver in
 * third person and in first person, including looking sideways while moving with Sound Physics installed.
 */
public class BulldozerAudioClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");

    @Override public void runTest(ClientGameTestContext context) {
        try (var server = context.worldBuilder().createServer(); var world = server.connect()) {
            world.getClientLevel().waitForChunksRender();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 -60 0 0 0");
            server.runCommand("summon jeg:bulldozer 0 -60 3 {Rotation:[0f,0f]}");
            context.waitTicks(5);
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                var dozer = s.overworld().getEntitiesOfClass(BulldozerEntity.class, new AABB(-8, -64, -8, 8, -50, 12)).getFirst();
                dozer.addEnergy(dozer.maxVehicleEnergy());
                player.startRiding(dozer, true, true);
            });
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(BulldozerEntity.START_TICKS + 40);
            String third = context.computeOnClient(BulldozerAudioClientGameTest::report);
            var instances = context.computeOnClient(mc -> BulldozerAudio.loops(mc.player.getVehicle().getId()));
            float idleThird = context.computeOnClient(mc -> BulldozerAudio.loopVolume(mc.player.getVehicle().getId(), "engine_idle"));
            LOG.info("[dozer-audio] third person: {}", third);
            var sourceIds = context.computeOnClient(BulldozerAudioClientGameTest::sources);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(20);
            String first = context.computeOnClient(BulldozerAudioClientGameTest::report);
            float idleFirst = context.computeOnClient(mc -> BulldozerAudio.loopVolume(mc.player.getVehicle().getId(), "engine_idle"));
            boolean sameInFirst = context.computeOnClient(mc -> BulldozerAudio.loops(mc.player.getVehicle().getId()).equals(instances));
            assertPosition(context, true);
            LOG.info("[dozer-audio] first person: {}", first);

            // Capture the actual seated view and a glance at the mirror, with shaders and pack animations on.
            context.runOnClient(mc -> { mc.options.hideGui = true; mc.player.setXRot(4); mc.player.setYRot(mc.player.getVehicle().getYRot()); });
            context.waitTicks(4);
            context.takeScreenshot("bulldozer_mirror_forward");
            context.runOnClient(mc -> mc.player.setYRot(mc.player.getVehicle().getYRot() + 65));
            context.waitTicks(4);
            context.takeScreenshot("bulldozer_mirror_seated");
            context.runOnClient(mc -> mc.options.hideGui = false);

            Vec3 start = context.computeOnClient(mc -> mc.player.getVehicle().position());
            context.getInput().holdKey(options -> options.keyUp);
            try {
                context.waitTicks(30);
                for (int yaw : new int[]{-90, 90, -150, 150}) {
                    context.runOnClient(mc -> mc.player.setYRot(mc.player.getVehicle().getYRot() + yaw));
                    for (int tick = 0; tick < 12; tick++) {
                        context.waitTick();
                        assertPosition(context, true);
                        check(context.computeOnClient(mc -> sources(mc).equals(sourceIds)), "OpenAL channels restarted while looking sideways");
                    }
                    LOG.info("[dozer-audio] moving, looking {} degrees: {}", yaw, context.computeOnClient(BulldozerAudioClientGameTest::report));
                }
            } finally {
                context.getInput().releaseKey(options -> options.keyUp);
            }
            check(context.computeOnClient(mc -> mc.player.getVehicle().position().distanceTo(start)) > 2, "sideways sound test did not drive the machine");
            context.waitTicks(30);
            context.runOnClient(mc -> mc.player.setYRot(mc.player.getVehicle().getYRot()));
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(20);
            String back = context.computeOnClient(BulldozerAudioClientGameTest::report);
            boolean sameBack = context.computeOnClient(mc -> BulldozerAudio.loops(mc.player.getVehicle().getId()).equals(instances));
            assertPosition(context, false);
            check(context.computeOnClient(mc -> sources(mc).equals(sourceIds)), "OpenAL channels restarted across the view switch");
            LOG.info("[dozer-audio] third person again: {}", back);
            LOG.info("[dozer-audio] continuity: same loops in first person {} and back {}, idle {} -> {}",
                    sameInFirst, sameBack, idleThird, idleFirst);
            if (!sameInFirst || !sameBack || Math.abs(idleFirst - idleThird) > .1F) {
                LOG.error("[dozer-audio] FAILED: the sound did not carry on unbroken across the view switch");
                throw new AssertionError("loops restarted or jumped on a view switch");
            }
            for (String result : new String[]{third, first, back}) {
                if (!result.contains("engine_idle=PLAYING") || result.contains("NO_CHANNEL")) {
                    // Dedicated-server shutdown can swallow the assertion: log the verdict explicitly.
                    LOG.error("[dozer-audio] FAILED: a loop is not playing: {}", result);
                    throw new AssertionError("engine loops not playing: " + result);
                }
            }
            LOG.info("[dozer-audio] COMPLETE");
        }
    }

    private static void check(boolean passed, String message) {
        if (!passed) { LOG.error("[dozer-audio] FAILED: {}", message); throw new AssertionError(message); }
    }

    private static void assertPosition(ClientGameTestContext context, boolean relative) {
        context.runOnClient(mc -> {
            var dozer = (BulldozerEntity) mc.player.getVehicle();
            var samples = sample(mc);
            check(samples.size() == 8, "missing sound layers");
            for (var entry : samples.entrySet()) {
                var s = entry.getValue();
                check(s.playing(), entry.getKey() + " stopped");
                check(s.relative() == relative, entry.getKey() + " has wrong OpenAL positioning mode: " + s);
                Vec3 target = relative ? Vec3.ZERO : dozer.position().add(0, 1.6, 0);
                check(s.position().distanceTo(target) < .05, entry.getKey() + " drifted from its audio origin: " + s);
            }
        });
    }

    private record Sample(int source, boolean playing, boolean relative, Vec3 position, float gain) {}

    private static Map<String, Integer> sources(Minecraft mc) {
        Map<String, Integer> ids = new HashMap<>();
        sample(mc).forEach((name, s) -> ids.put(name, s.source()));
        return ids;
    }

    /** Query OpenAL on its owning thread; a render-thread query can return stale or invalid source state. */
    private static Map<String, Sample> sample(Minecraft mc) {
        try {
            Field engineField = SoundManager.class.getDeclaredField("soundEngine");
            engineField.setAccessible(true);
            SoundEngine engine = (SoundEngine) engineField.get(mc.getSoundManager());
            Field mapField = SoundEngine.class.getDeclaredField("instanceToChannel");
            mapField.setAccessible(true);
            Map<?, ?> channels = (Map<?, ?>) mapField.get(engine);
            Map<String, Sample> samples = new HashMap<>();
            for (var entry : BulldozerAudio.loops(mc.player.getVehicle().getId()).entrySet()) {
                var handle = (ChannelAccess.ChannelHandle) channels.get(entry.getValue());
                check(handle != null && !handle.isStopped(), entry.getKey() + " has no channel");
                var result = new CompletableFuture<Sample>();
                handle.execute(channel -> {
                    try {
                        Field field = channel.getClass().getDeclaredField("source");
                        field.setAccessible(true);
                        int source = field.getInt(channel);
                        float[] pos = new float[3];
                        AL10.alGetSourcefv(source, AL10.AL_POSITION, pos);
                        // AL_DIRECT_FILTER is write-only in EFX; trying to query it contaminates the sound
                        // thread's error state and hides real channel errors behind invalid-enum messages.
                        result.complete(new Sample(source, AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING,
                                AL10.alGetSourcei(source, AL10.AL_SOURCE_RELATIVE) != 0, new Vec3(pos[0], pos[1], pos[2]),
                                AL10.alGetSourcef(source, AL10.AL_GAIN)));
                    } catch (Throwable error) { result.completeExceptionally(error); }
                });
                samples.put(entry.getKey(), result.get(5, TimeUnit.SECONDS));
            }
            return samples;
        } catch (Exception e) { throw new AssertionError("Cannot read actual OpenAL channels", e); }
    }

    private static String report(Minecraft mc) {
        StringBuilder out = new StringBuilder("camera=" + mc.gameRenderer.getMainCamera().position() + " source=" + mc.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.AMBIENT) + "; ");
        for (var entry : sample(mc).entrySet()) {
            var s = entry.getValue();
            out.append(entry.getKey()).append('=').append(s.playing() ? "PLAYING" : "STOPPED")
                    .append(String.format(" alGain=%.3f rel=%s pos=%s; ", s.gain(), s.relative(), s.position()));
        }
        return out.toString();
    }
}
