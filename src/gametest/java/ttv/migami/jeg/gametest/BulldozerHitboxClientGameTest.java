package ttv.migami.jeg.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;
import ttv.migami.jeg.vehicle.util.BulldozerRig;

/**
 * The bulldozer's hitbox fits the machine. Its collision boxes are the ones the model generator fitted (rig.json) and lie
 * within the model; real rifle rounds aimed at the blade, each track, the engine and the canopy hit it, while rounds
 * aimed into the empty space above the hood and beside the canopy (inside the old whole-vehicle box) pass by.
 */
public class BulldozerHitboxClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private String id;

    @Override public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("bulldozer hitbox report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule fall_damage false");
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("item replace entity @p weapon.mainhand with jeg:assault_rifle");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            this.id = server.computeOnServer(s -> {
                BulldozerEntity dozer = ModEntities.BULLDOZER.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                dozer.snapTo(0.5, -60, 0.5, 0, 0);
                s.overworld().addFreshEntity(dozer);
                return dozer.getUUID().toString();
            });
            context.waitTicks(20);

            // 1. The loaded boxes are the generator's fitted boxes, and every one lies within the model.
            try {
                JsonObject rig = JsonParser.parseString(Files.readString(Path.of("").toAbsolutePath().getParent().getParent().getParent()
                        .resolve("design/bulldozer/rig.json"))).getAsJsonObject();
                var bounds = rig.getAsJsonArray("model_bounds");
                var hitbox = rig.getAsJsonArray("hitbox");
                String boxes = server.computeOnServer(s -> {
                    StringBuilder out = new StringBuilder();
                    var list = dozer(s).vehicleData().defaults().obb().boxes();
                    if (list.size() != hitbox.size()) out.append("box count ").append(list.size()).append(" vs rig ").append(hitbox.size()).append('\n');
                    for (int i = 0; i < Math.min(list.size(), hitbox.size()); i++) {
                        var box = list.get(i);
                        var want = hitbox.get(i).getAsJsonObject();
                        double[] lo = {box.x() - box.halfWidth(), box.y() - box.halfHeight(), -(box.z() + box.halfDepth())};
                        double[] hi = {box.x() + box.halfWidth(), box.y() + box.halfHeight(), -(box.z() - box.halfDepth())};
                        for (int k = 0; k < 3; k++) {
                            double min = want.getAsJsonArray("min").get(k).getAsDouble() / 16, max = want.getAsJsonArray("max").get(k).getAsDouble() / 16;
                            double modelMin = bounds.get(0).getAsJsonArray().get(k).getAsDouble() / 16, modelMax = bounds.get(1).getAsJsonArray().get(k).getAsDouble() / 16;
                            if (Math.abs(lo[k] - (k == 1 ? Math.max(0, min) : min)) > .01 || Math.abs(hi[k] - max) > .01) {
                                out.append(String.format(Locale.ROOT, "%s axis %d is %.3f..%.3f, fitted %.3f..%.3f%n", want.get("covers").getAsString(), k, lo[k], hi[k], min, max));
                            }
                            if (lo[k] < modelMin - .07 || hi[k] > modelMax + .07) {
                                out.append(String.format(Locale.ROOT, "%s axis %d sticks out of the model%n", want.get("covers").getAsString(), k));
                            }
                        }
                    }
                    return out.toString();
                });
                report.append("collision boxes vs fitted: ").append(boxes.isEmpty() ? "match\n" : boxes);
                failures.append(boxes);
            } catch (java.io.IOException e) {
                failures.append("rig.json unreadable: ").append(e).append('\n');
            }

            // 2. Real rounds: hull parts are hit, empty space round the hull is not.
            // Each shot: a model point and the direction to the rifle's muzzle from it, 13 blocks off (beyond melee reach),
            // on a line that meets nothing else of the machine.
            Object[][] shots = {
                    {"blade face", new Vec3(0, 13, -48), true, new Vec3(0, .1, 1)},
                    {"left track", new Vec3(20, 8, 0), true, new Vec3(1, .05, 0)},
                    {"right track", new Vec3(-20, 8, 0), true, new Vec3(-1, .05, 0)},
                    {"engine hood", new Vec3(0, 22, -20), true, new Vec3(0, .66, 1)},
                    {"canopy post", new Vec3(-11, 40, 15), true, new Vec3(-1, .05, -.2)},
                    {"air above the hood", new Vec3(0, 42, -26), false, new Vec3(1, 0, 0)},
                    {"air beside the canopy", new Vec3(24, 38, 6), false, new Vec3(0, 0, -1)},
            };
            for (Object[] shot : shots) {
                String name = (String) shot[0];
                Vec3 model = (Vec3) shot[1];
                boolean expectHit = (Boolean) shot[2];
                Vec3 away = ((Vec3) shot[3]).normalize();
                float before = server.computeOnServer(s -> { var d = dozer(s); d.repairWithTool(10000, 10000); return d.vehicleHealth(); });
                Vec3 target = server.computeOnServer(s -> BulldozerRig.world(dozer(s), model.x, model.y, model.z));
                Vec3 eye = target.add(away.scale(13));
                Vec3 look = target.subtract(eye);
                float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90;
                float pitch = (float) (-Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG);
                String tp = String.format(Locale.ROOT, "tp @p %.3f %.3f %.3f %.2f %.2f", eye.x, eye.y - 1.62, eye.z, yaw, pitch);
                server.runCommand("gamemode spectator @p");
                server.runCommand(tp);
                server.runCommand("gamemode creative @p");
                context.waitTicks(5);
                context.getInput().holdKey(options -> options.keyAttack);
                for (int tick = 0; tick < 6; tick++) { server.runCommand(tp); context.waitTick(); }
                context.getInput().releaseKey(options -> options.keyAttack);
                context.waitTicks(10);
                float after = server.computeOnServer(s -> dozer(s).vehicleHealth());
                boolean hit = after < before - .01F;
                report.append(String.format(Locale.ROOT, "%-22s %s (health %.1f -> %.1f)%n", name, hit ? "HIT " : "miss", before, after));
                if (hit != expectHit) failures.append(String.format(Locale.ROOT, "%s: expected %s%n", name, expectHit ? "a hit" : "a miss"));
            }
            server.runCommand("tp @p 6 -59 -8 30 15");
            context.waitTicks(10);
            context.takeScreenshot("bulldozer_hitbox_range");
        } finally {
            LOG.info("{}", report);
        }
        if (failures.length() > 0) {
            LOG.error("[dozer-hitbox] FAILED: {}", failures);
            throw new AssertionError(failures.toString() + report);
        }
        LOG.info("[dozer-hitbox] COMPLETE");
    }

    private BulldozerEntity dozer(MinecraftServer server) {
        return (BulldozerEntity) server.overworld().getEntity(UUID.fromString(this.id));
    }
}
