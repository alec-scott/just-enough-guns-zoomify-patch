package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.sounds.SoundEventListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.entity.SentryEntity;

/**
 * The perimeter sentry's brief, its success line (design/sentry/BRIEF.md): "three sentries on a wall, fed rifle
 * ammunition, hold off a horde night's first wave, and never shoot a player, villager or pet".
 * <ol>
 *   <li>Line of fire: a villager and the player's tamed wolf stand between a sentry and a zombie. It holds its fire
 *   (not a round spent, neither hurt); once they are gone it fires and kills the zombie, and its own report (the
 *   Negev recording, {@code vehicle.gun.sentry.fire}) is heard.</li>
 *   <li>Automatic needs no ammunition (the user's review, 2026-11-18): with an empty hold it still fires on a zombie
 *   and kills it, and the hold stays empty.</li>
 *   <li>The wall: three sentries on a 3-high wall, 20 zombies 24 to 40 blocks out coming for the player behind it,
 *   at night on normal difficulty. All are down before any reaches the wall; the player and a villager behind the wall
 *   are unhurt.</li>
 * </ol>
 */
public class SentryWallClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private final List<String> heard = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> mc.options.renderDistance().set(12));
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitTicks(100);
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty normal");
            server.runCommand("time set midnight");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            server.runCommand("forceload add -48 -48 64 64");
            SoundEventListener listener = (sound, events, range) -> {
                String path = sound.getIdentifier().getPath();
                if (path.contains("sentry")) this.heard.add(path);
            };
            context.runOnClient(mc -> mc.getSoundManager().addListener(listener));

            // ---- 1. Line of fire, off to the east.
            server.runCommand("tp @a 40.5 -60 -6.5 0 10");
            context.waitTicks(20);
            int lone = server.computeOnServer(s -> sentry(s, 40.5D, G, 0.5D, 128));
            int target = server.computeOnServer(s -> zombie(s, 40.5D, 16.5D, true));
            int villager = server.computeOnServer(s -> {
                var v = EntityType.VILLAGER.create(s.overworld(), EntitySpawnReason.COMMAND);
                v.snapTo(40.5D, G, 6.5D, 0.0F, 0.0F);
                v.setNoAi(true);
                s.overworld().addFreshEntity(v);
                return v.getId();
            });
            int wolf = server.computeOnServer(s -> {
                var w = (TamableAnimal) EntityType.WOLF.create(s.overworld(), EntitySpawnReason.COMMAND);
                w.snapTo(40.5D, G, 11.5D, 0.0F, 0.0F);
                w.setNoAi(true);
                s.overworld().addFreshEntity(w);
                w.tame(player(s));
                return w.getId();
            });
            context.waitTicks(5);
            server.runOnServer(s -> ((SentryEntity) s.overworld().getEntity(lone)).towKey(player(s)));
            // Automatic fire is free now, so held fire is measured by the reports heard, not rounds spent.
            this.heard.clear();
            context.waitTicks(80);
            long shotsBlocked = this.heard.stream().filter(p -> p.startsWith("vehicle.gun.sentry.fire")).count();
            float[] friends = server.computeOnServer(s -> new float[]{health(s, villager), health(s, wolf)});
            boolean tracked = server.computeOnServer(s -> ((SentryEntity) s.overworld().getEntity(lone)).tracked() != null);
            boolean standing = server.computeOnServer(s -> s.overworld().getEntity(target) instanceof LivingEntity z && z.isAlive());
            LOGGER.info("[sentry-wall] blocked line of fire: tracking {}, rounds {} -> {}, villager {}, wolf {}, zombie standing {}, heard {}",
                    tracked, 0, shotsBlocked, friends[0], friends[1], standing, List.copyOf(this.heard));
            if (!standing) failures.append("the zombie behind the villager and the wolf died\n");
            if (!tracked) failures.append("the sentry did not track the zombie behind the villager and the wolf\n");
            if (shotsBlocked > 0) failures.append("it fired with a villager and a pet in the line of fire\n");
            if (friends[0] < 20.0F || friends[1] < 8.0F) failures.append("a villager or the pet was hurt: ").append(friends[0]).append(", ").append(friends[1]).append('\n');
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.takeScreenshot("sentry_wall_held_fire");
            server.runOnServer(s -> {
                s.overworld().getEntity(villager).discard();
                s.overworld().getEntity(wolf).discard();
            });
            this.heard.clear();
            boolean killed = false;
            for (int tick = 0; tick < 100 && !killed; tick++) {
                context.waitTick();
                killed = server.computeOnServer(s -> !(s.overworld().getEntity(target) instanceof LivingEntity z) || !z.isAlive());
            }
            context.waitTicks(10);
            List<String> reports = List.copyOf(this.heard);
            LOGGER.info("[sentry-wall] line clear: zombie down {}, heard {}", killed, reports.stream().distinct().toList());
            if (!killed) failures.append("with the line clear it did not kill the zombie\n");
            if (reports.stream().noneMatch(p -> p.startsWith("vehicle.gun.sentry.fire"))) failures.append("its own report was not heard: ").append(reports).append('\n');

            // ---- 2. On automatic with an empty hold: it still fires (unlimited), and kills the zombie.
            server.runOnServer(s -> {
                SentryEntity sentry = (SentryEntity) s.overworld().getEntity(lone);
                for (int i = 0; i < sentry.vehicleInventory().getContainerSize(); i++) sentry.vehicleInventory().setItem(i, ItemStack.EMPTY);
            });
            int dryTarget = server.computeOnServer(s -> zombie(s, 40.5D, 14.5D, true));
            this.heard.clear();
            boolean dryKilled = false;
            for (int tick = 0; tick < 120 && !dryKilled; tick++) {
                context.waitTick();
                dryKilled = server.computeOnServer(s -> !(s.overworld().getEntity(dryTarget) instanceof LivingEntity z) || !z.isAlive());
            }
            int emptyHold = server.computeOnServer(s -> ((SentryEntity) s.overworld().getEntity(lone)).vehicleRifleAmmo());
            long emptyShots = this.heard.stream().filter(p -> p.startsWith("vehicle.gun.sentry.fire")).count();
            LOGGER.info("[sentry-wall] empty hold on automatic: zombie down {}, reports {}, rounds in hold {}", dryKilled, emptyShots, emptyHold);
            if (!dryKilled || emptyShots == 0) failures.append("on automatic with an empty hold it did not fire and kill the zombie\n");
            if (emptyHold != 0) failures.append("rounds appeared in the empty hold: ").append(emptyHold).append('\n');
            server.runOnServer(s -> {
                s.overworld().getEntity(dryTarget).discard();
                s.overworld().getEntity(lone).discard();
            });

            // ---- 3. Three sentries on a wall against twenty zombies.
            server.runCommand("fill -14 -60 0 14 -58 0 minecraft:stone_bricks");
            server.runCommand("tp @a 0.5 -60 -6.5 0 -10");
            int behind = server.computeOnServer(s -> {
                var v = EntityType.VILLAGER.create(s.overworld(), EntitySpawnReason.COMMAND);
                v.snapTo(-4.5D, G, -8.5D, 0.0F, 0.0F);
                v.setNoAi(true);
                s.overworld().addFreshEntity(v);
                return v.getId();
            });
            List<Integer> sentries = server.computeOnServer(s -> List.of(
                    sentry(s, -7.5D, G + 3, 0.5D, 192), sentry(s, 0.5D, G + 3, 0.5D, 192), sentry(s, 8.5D, G + 3, 0.5D, 192)));
            context.waitTicks(10);
            server.runOnServer(s -> sentries.forEach(id -> ((SentryEntity) s.overworld().getEntity(id)).towKey(player(s))));
            List<Integer> horde = server.computeOnServer(s -> {
                List<Integer> ids = new ArrayList<>();
                for (int i = 0; i < 20; i++) {
                    double x = -14.0D + (i % 7) * 4.5D + (i / 7) * 1.5D;
                    double z = 24.0D + (i / 7) * 7.0D + (i % 3) * 1.5D;
                    ids.add(zombie(s, x + 0.5D, z + 0.5D, false));
                }
                return ids;
            });
            float[] before = server.computeOnServer(s -> new float[]{player(s).getHealth(), health(s, behind)});
            int fed = server.computeOnServer(s -> sentries.stream().mapToInt(id -> ((SentryEntity) s.overworld().getEntity(id)).vehicleRifleAmmo()).sum());
            int alive = horde.size(), tick = 0;
            double closest = Double.MAX_VALUE;
            for (; tick < 900 && alive > 0; tick++) {
                context.waitTick();
                if (tick == 60) {
                    context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
                    context.takeScreenshot("sentry_wall_horde");
                }
                double[] state = server.computeOnServer(s -> {
                    double near = Double.MAX_VALUE;
                    int left = 0;
                    for (int id : horde) {
                        if (s.overworld().getEntity(id) instanceof LivingEntity z && z.isAlive()) {
                            left++;
                            near = Math.min(near, z.getZ());
                        }
                    }
                    return new double[]{left, near};
                });
                alive = (int) state[0];
                closest = Math.min(closest, state[1]);
            }
            float[] after = server.computeOnServer(s -> new float[]{player(s).getHealth(), health(s, behind)});
            int left = server.computeOnServer(s -> sentries.stream().mapToInt(id -> ((SentryEntity) s.overworld().getEntity(id)).vehicleRifleAmmo()).sum());
            LOGGER.info("[sentry-wall] horde: {} of 20 left after {} ticks; nearest any came to the wall {} blocks; rounds {} of {}; player {} -> {}; villager {} -> {}",
                    alive, tick, closest, fed - left, fed, before[0], after[0], before[1], after[1]);
            if (alive > 0) failures.append(alive).append(" zombies still up after 45 s\n");
            if (closest < 2.0D) failures.append("a zombie reached the wall (z ").append(closest).append(")\n");
            if (after[0] < before[0] || after[1] < before[1]) failures.append("the player or the villager behind the wall was hurt\n");
            context.runOnClient(mc -> mc.getSoundManager().removeListener(listener));
            if (!failures.isEmpty()) throw new AssertionError(failures.toString());
            LOGGER.info("[sentry-wall] COMPLETE");
        } catch (Throwable failure) {
            LOGGER.error("[sentry-wall] FAILED: {}", failure.getMessage());
            throw failure;
        }
    }

    private static int sentry(MinecraftServer s, double x, double y, double z, int rounds) {
        SentryEntity sentry = ModEntities.SENTRY.get().create(s.overworld(), EntitySpawnReason.COMMAND);
        sentry.snapTo(x, y, z, 0.0F, 0.0F);
        sentry.addEnergy(sentry.maxVehicleEnergy());
        for (int slot = 0; rounds > 0; slot++, rounds -= 64) {
            sentry.vehicleInventory().setItem(slot, new ItemStack(ModItems.AMMO.get(Reference.id("rifle_ammo")).get(), Math.min(64, rounds)));
        }
        s.overworld().addFreshEntity(sentry);
        return sentry.getId();
    }

    private static int zombie(MinecraftServer s, double x, double z, boolean still) {
        Zombie zombie = EntityType.ZOMBIE.create(s.overworld(), EntitySpawnReason.COMMAND);
        zombie.snapTo(x, G, z, 180.0F, 0.0F);
        zombie.setPersistenceRequired();
        if (still) zombie.setNoAi(true);
        s.overworld().addFreshEntity(zombie);
        if (!still) zombie.setTarget(player(s));
        return zombie.getId();
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static float health(MinecraftServer s, int id) {
        Entity e = s.overworld().getEntity(id);
        return e instanceof LivingEntity living ? living.getHealth() : 0.0F;
    }
}
