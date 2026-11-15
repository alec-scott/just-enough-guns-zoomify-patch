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
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * The FSV-6's hitbox fits it (packed). Its boxes are the ones the generator fitted (rig.json "hitbox.packed"), each lies
 * within the model; real rifle rounds aimed at the cab, the van side, a front wheel and the rear hit it, while rounds
 * aimed at the air above the cab and beside the body miss. A round at the cab while deployed still hits (the room's
 * shell blocks take hits on the van then).
 */
public class SupportHitboxClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private String id;

    @Override public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("support vehicle hitbox report\n");
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
                SupportVehicleEntity truck = ModEntities.TRUCK.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                truck.snapTo(0.5, -60, 0.5, 0, 0);
                s.overworld().addFreshEntity(truck);
                return truck.getUUID().toString();
            });
            context.waitTicks(20);

            try {
                JsonObject rig = JsonParser.parseString(Files.readString(Path.of("").toAbsolutePath().getParent().getParent().getParent()
                        .resolve("design/support/rig.json"))).getAsJsonObject();
                var bounds = rig.getAsJsonObject("model_bounds").getAsJsonArray("packed");
                var hitbox = rig.getAsJsonObject("hitbox").getAsJsonArray("packed");
                String boxes = server.computeOnServer(s -> {
                    StringBuilder out = new StringBuilder();
                    var list = truck(s).vehicleData().defaults().obb().boxes();
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

            Object[][] shots = {
                    {"cab front", new Vec3(0, 26, -64), true, new Vec3(0, .05, -1)},
                    {"van left side", new Vec3(20, 32, 16), true, new Vec3(1, .05, 0)},
                    {"van right side", new Vec3(-20, 32, 30), true, new Vec3(-1, .05, 0)},
                    {"left front wheel", new Vec3(19, 6, -44), true, new Vec3(1, .1, 0)},
                    {"rear door", new Vec3(0, 32, 60), true, new Vec3(0, .05, 1)},
                    {"air above the cab", new Vec3(0, 58, -50), false, new Vec3(1, 0, 0)},
                    {"air beside the body", new Vec3(32, 32, 16), false, new Vec3(0, 0, -1)},
            };
            for (Object[] shot : shots) {
                String name = (String) shot[0];
                Vec3 model = (Vec3) shot[1];
                boolean expectHit = (Boolean) shot[2];
                Vec3 away = ((Vec3) shot[3]).normalize();
                float before = server.computeOnServer(s -> { var t = truck(s); t.repairWithTool(10000, 10000); return t.vehicleHealth(); });
                Vec3 target = server.computeOnServer(s -> SupportRig.world(truck(s), model.x, model.y, model.z));
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
                float after = server.computeOnServer(s -> truck(s).vehicleHealth());
                boolean hit = after < before - .01F;
                report.append(String.format(Locale.ROOT, "%-22s %s (health %.1f -> %.1f)%n", name, hit ? "HIT " : "miss", before, after));
                if (hit != expectHit) failures.append(String.format(Locale.ROOT, "%s: expected %s%n", name, expectHit ? "a hit" : "a miss"));
            }
            server.runCommand("tp @p 9 -57 -10 35 15");
            context.waitTicks(10);
            context.takeScreenshot("support_hitbox_range");
        } finally {
            LOG.info("{}", report);
        }
        if (failures.length() > 0) {
            LOG.error("[support-hitbox] FAILED: {}", failures);
            throw new AssertionError(failures.toString() + report);
        }
        LOG.info("[support-hitbox] COMPLETE");
    }

    private SupportVehicleEntity truck(MinecraftServer server) {
        return (SupportVehicleEntity) server.overworld().getEntity(UUID.fromString(this.id));
    }
}
