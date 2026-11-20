package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.AutoDefenceEntity;

/**
 * Automatic defences against what the user met (2026-11-19):
 * <ol>
 *   <li>A chicken jockey: a baby zombie riding a chicken in front of a sentry on automatic. The chicken is the zombie's
 *   mount, not something to protect: the sentry kills the zombie (it held its fire before).</li>
 *   <li>The speedboat's bow gun on automatic (new): with only a driver aboard and nobody at the gun, it kills a zombie
 *   on the bank by itself.</li>
 *   <li>Switching to automatic and back is heard ({@code vehicle.sentry.auto_on}, {@code auto_off}).</li>
 * </ol>
 */
public class AutoDefenceTargetsClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private final List<String> heard = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitTicks(60);
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty normal");
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            server.runCommand("forceload add -32 -32 48 48");
            SoundEventListener listener = (sound, events, range) -> {
                String path = sound.getIdentifier().getPath();
                if (path.startsWith("vehicle.sentry.auto")) this.heard.add(path);
            };
            context.runOnClient(mc -> mc.getSoundManager().addListener(listener));

            // ---- 1. A chicken jockey before a sentry.
            server.runCommand("tp @a 0.5 -60 -5.5 0 10");
            context.waitTicks(20);
            int sentry = server.computeOnServer(s -> {
                var entity = ModEntities.SENTRY.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                entity.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
                entity.addEnergy(entity.maxVehicleEnergy());
                s.overworld().addFreshEntity(entity);
                return entity.getId();
            });
            int[] jockey = server.computeOnServer(s -> {
                var chicken = EntityType.CHICKEN.create(s.overworld(), EntitySpawnReason.COMMAND);
                chicken.snapTo(0.5D, G, 14.5D, 180.0F, 0.0F);
                chicken.setNoAi(true);
                s.overworld().addFreshEntity(chicken);
                Zombie zombie = EntityType.ZOMBIE.create(s.overworld(), EntitySpawnReason.COMMAND);
                zombie.setBaby(true);
                zombie.snapTo(0.5D, G, 14.5D, 180.0F, 0.0F);
                zombie.setPersistenceRequired();
                zombie.setNoAi(true);
                s.overworld().addFreshEntity(zombie);
                zombie.startRiding(chicken, true, true);
                return new int[]{zombie.getId(), chicken.getId()};
            });
            context.waitTicks(5);
            this.heard.clear();
            server.runOnServer(s -> ((AutoDefenceEntity) s.overworld().getEntity(sentry)).towKey(player(s)));
            boolean jockeyDown = waitDead(context, server, jockey[0], 160);
            boolean riding = server.computeOnServer(s -> s.overworld().getEntity(jockey[0]) != null
                    && s.overworld().getEntity(jockey[0]).getVehicle() != null);
            context.takeScreenshot("auto_defence_jockey");
            server.runOnServer(s -> ((AutoDefenceEntity) s.overworld().getEntity(sentry)).towKey(player(s)));
            context.waitTicks(10);
            List<String> cues = List.copyOf(this.heard);
            LOGGER.info("[auto-defence] chicken jockey: zombie down {} (was riding {}), cues heard {}", jockeyDown, riding, cues);
            if (!jockeyDown) failures.append("the sentry did not kill the zombie riding a chicken\n");
            if (!cues.contains("vehicle.sentry.auto_on") || !cues.contains("vehicle.sentry.auto_off")) {
                failures.append("switching to automatic and back was not heard: ").append(cues).append('\n');
            }
            server.runOnServer(s -> {
                for (int id : new int[]{sentry, jockey[0], jockey[1]}) {
                    Entity e = s.overworld().getEntity(id);
                    if (e != null) e.discard();
                }
            });

            // ---- 2. The speedboat's bow gun on automatic, the driver alone aboard.
            server.runCommand("fill 20 -61 -10 40 -61 10 minecraft:water");
            server.runCommand("tp @a 30.5 -59 -8 0 10");
            context.waitTicks(20);
            int boat = server.computeOnServer(s -> {
                var entity = ModEntities.SPEEDBOAT.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                entity.snapTo(30.5D, -60.6D, -2.5D, 0.0F, 0.0F);
                entity.addEnergy(entity.maxVehicleEnergy());
                s.overworld().addFreshEntity(entity);
                return entity.getId();
            });
            context.waitTicks(10);
            server.runOnServer(s -> player(s).startRiding(s.overworld().getEntity(boat), true, true));
            context.waitTicks(10);
            int bankZombie = server.computeOnServer(s -> {
                Zombie zombie = EntityType.ZOMBIE.create(s.overworld(), EntitySpawnReason.COMMAND);
                zombie.snapTo(30.5D, G, 20.5D, 180.0F, 0.0F);
                zombie.setPersistenceRequired();
                zombie.setNoAi(true);
                s.overworld().addFreshEntity(zombie);
                return zombie.getId();
            });
            server.runOnServer(s -> ((AutoDefenceEntity) s.overworld().getEntity(boat)).towKey(player(s)));
            boolean bankDown = waitDead(context, server, bankZombie, 200);
            int seat = server.computeOnServer(s -> ((ttv.migami.jeg.vehicle.entity.base.VehicleEntity) s.overworld().getEntity(boat)).seatOf(player(s)));
            context.takeScreenshot("auto_defence_speedboat");
            LOGGER.info("[auto-defence] speedboat: player in seat {}, zombie on the bank down {}", seat, bankDown);
            if (!bankDown) failures.append("the speedboat's gun on automatic did not kill the zombie on the bank\n");
        }
        if (failures.length() > 0) {
            LOGGER.error("[auto-defence] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOGGER.info("[auto-defence] COMPLETE");
    }

    private static boolean waitDead(ClientGameTestContext context, TestServerContext server, int id, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            context.waitTick();
            if (server.computeOnServer(s -> !(s.overworld().getEntity(id) instanceof LivingEntity z) || !z.isAlive())) {
                return true;
            }
        }
        return false;
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }
}
