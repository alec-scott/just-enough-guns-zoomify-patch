package ttv.migami.jeg.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.client.audio.BulldozerAudio;
import ttv.migami.jeg.vehicle.client.audio.FarAircraftAudio;
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;

/**
 * The bulldozer heard as the aircraft are (AircraftFarSoundClientGameTest is the model). The player stands still,
 * render distance 12 so the server sends the dozer itself out to 192 blocks, as on yftm; the dozer, engine running,
 * is held at one distance after another out to 1100 blocks. At each, every sound of it the player hears is read: the
 * full engine, its muffled distant version, and past entity range the distant one from the server's tracks. Fails
 * unless it is still heard 800 blocks off and silent past 1024; it only gets quieter further out (no jump where it
 * leaves entity range); from 300 blocks on the distant version dominates; and the horn is heard 600 blocks off.
 */
public class BulldozerFarSoundClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int[] DISTANCES = {30, 120, 180, 260, 400, 600, 800, 950, 1100};
    private static final VehicleInput HORN = new VehicleInput(false, false, false, false, false, false, false, true,
            false, false, false, false, -1, false, false, 0F, 0F);

    private record Heard(int distance, boolean entity, float near, float distant, float far, float horn) {
        float total() { return (float) Math.sqrt(near * near + distant * distant + far * far); }
    }

    @Override public void runTest(ClientGameTestContext context) {
        int previousDistance = context.computeOnClient(mc -> mc.options.renderDistance().get());
        context.runOnClient(mc -> mc.options.renderDistance().set(12));
        StringBuilder report = new StringBuilder("bulldozer far sound report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender(false, 2400);
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 -59 0 -90 0");
            for (int distance : DISTANCES) server.runCommand(String.format(Locale.ROOT, "forceload add %d -8 %d 8", distance - 8, distance + 8));
            context.waitTicks(40);
            String id = server.computeOnServer(s -> {
                var level = s.overworld();
                BulldozerEntity dozer = ModEntities.BULLDOZER.get().create(level, EntitySpawnReason.COMMAND);
                dozer.snapTo(DISTANCES[0], -60, .5, 90, 0);
                dozer.addEnergy(dozer.maxVehicleEnergy());
                level.addFreshEntity(dozer);
                var driver = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
                driver.setNoAi(true);
                driver.setSilent(true);
                driver.snapTo(dozer.getX(), dozer.getY(), dozer.getZ(), 0, 0);
                level.addFreshEntity(driver);
                driver.startRiding(dozer, true, true);
                return dozer.getUUID().toString();
            });
            int entityId = server.computeOnServer(s -> dozer(s, id).getId());
            this.hold(context, server, id, DISTANCES[0], false, BulldozerEntity.START_TICKS + 40);
            List<Heard> heard = new ArrayList<>();
            for (int distance : DISTANCES) {
                this.hold(context, server, id, distance, false, 90);
                heard.add(this.sample(context, entityId, distance));
            }
            this.hold(context, server, id, 600, true, 80);
            float hornFar = this.sample(context, entityId, 600).horn();
            this.hold(context, server, id, 600, false, 5);
            for (Heard h : heard) {
                report.append(String.format(Locale.ROOT, "dozer at %4d blocks: %s near %.3f, distant %.3f, far %.3f -> %.3f%n", h.distance(),
                        h.entity() ? "entity " : "tracked", h.near(), h.distant(), h.far(), h.total()));
            }
            report.append(String.format(Locale.ROOT, "horn heard 600 blocks off at %.3f%n", hornFar));
            if (hornFar < .05F) failures.append("the horn was not heard 600 blocks off\n");
            float previous = Float.MAX_VALUE;
            for (Heard h : heard) {
                float total = h.total();
                if (total > previous * 1.15F + .002F) failures.append(String.format(Locale.ROOT, "got louder going out to %d (%.3f after %.3f)%n", h.distance(), total, previous));
                previous = total;
                if (h.distance() == 800 && total < .01F) failures.append(String.format(Locale.ROOT, "not heard 800 blocks off (%.3f)%n", total));
                if (h.distance() > 1024 && total > .002F) failures.append(String.format(Locale.ROOT, "still heard %d blocks off (%.3f)%n", h.distance(), total));
                if (h.distance() >= 300 && h.distance() <= 1024 && h.near() >= Math.max(h.distant(), h.far())) {
                    failures.append(String.format(Locale.ROOT, "did not sound distant at %d blocks%n", h.distance()));
                }
            }
            if (heard.getFirst().total() < .3F) failures.append(String.format(Locale.ROOT, "quiet close up (%.3f)%n", heard.getFirst().total()));
        } finally {
            context.runOnClient(mc -> mc.options.renderDistance().set(previousDistance));
            LOG.info("{}", report);
        }
        if (failures.length() > 0) {
            LOG.error("[dozer-far-sound] FAILED: {}", failures);
            throw new AssertionError(failures.toString() + report);
        }
        LOG.info("[dozer-far-sound] COMPLETE");
    }

    private Heard sample(ClientGameTestContext context, int entityId, int distance) {
        return context.computeOnClient(mc -> {
            boolean entity = mc.level.getEntity(entityId) instanceof BulldozerEntity;
            float near = (float) Math.hypot(BulldozerAudio.loopVolume(entityId, "engine_idle"), BulldozerAudio.loopVolume(entityId, "engine_load"));
            float distant = BulldozerAudio.loopVolume(entityId, "engine_distant");
            float horn = BulldozerAudio.loopVolume(entityId, "horn") + FarAircraftAudio.gunVolume(entityId);
            return new Heard(distance, entity, near < .002F ? 0 : near, distant < .002F ? 0 : distant, FarAircraftAudio.engineVolume(entityId), horn);
        });
    }

    /** Keeps the dozer at this distance on the +X axis, engine running, the horn sounding when asked. */
    private void hold(ClientGameTestContext context, TestServerContext server, String id, int distance, boolean horn, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            server.runOnServer(s -> {
                BulldozerEntity dozer = dozer(s, id);
                dozer.snapTo(distance, -60, .5, 90, 0);
                dozer.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                setInput(dozer, horn ? HORN : VehicleInput.EMPTY);
            });
            context.waitTick();
        }
    }

    private static void setInput(VehicleEntity vehicle, VehicleInput input) {
        try {
            Field field = VehicleEntity.class.getDeclaredField("input");
            field.setAccessible(true);
            field.set(vehicle, input);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static BulldozerEntity dozer(MinecraftServer server, String id) {
        return (BulldozerEntity) server.overworld().getEntity(UUID.fromString(id));
    }
}
