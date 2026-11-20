package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.entity.SentryEntity;

/**
 * The perimeter sentry on automatic (design/sentry/BRIEF.md), what must not regress: at night, five zombies 20 to 30
 * blocks off come for the player standing behind it; it cuts them down before they arrive, and hurts neither the player
 * nor a villager standing off to the side.
 */
public class SentryClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private int sentryId = -1;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> mc.options.renderDistance().set(12));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitTicks(100);
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("difficulty normal");
            server.runCommand("time set midnight");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            server.runCommand("tp @a 0.5 -60 -5.5 0 10");
            this.sentryId = server.computeOnServer(s -> {
                SentryEntity sentry = ModEntities.SENTRY.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                sentry.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
                sentry.addEnergy(sentry.maxVehicleEnergy());
                sentry.vehicleInventory().setItem(0, new ItemStack(ModItems.AMMO.get(Reference.id("rifle_ammo")).get(), 64));
                sentry.vehicleInventory().setItem(1, new ItemStack(ModItems.AMMO.get(Reference.id("rifle_ammo")).get(), 64));
                s.overworld().addFreshEntity(sentry);
                return sentry.getId();
            });
            context.waitTicks(10);
            server.runOnServer(s -> sentry(s).towKey(s.getPlayerList().getPlayers().get(0)));
            check(server.computeOnServer(s -> sentry(s).automatic()), "N must switch it to automatic");
            int villagerId = server.computeOnServer(s -> {
                var villager = EntityType.VILLAGER.create(s.overworld(), EntitySpawnReason.COMMAND);
                villager.snapTo(-9.5D, G, -3.5D, 0.0F, 0.0F);
                villager.setNoAi(true);
                s.overworld().addFreshEntity(villager);
                return villager.getId();
            });
            List<Integer> zombies = server.computeOnServer(s -> {
                List<Integer> ids = new ArrayList<>();
                double[][] at = {{-10, 22}, {-4, 27}, {2, 30}, {8, 25}, {13, 20}};
                for (double[] p : at) {
                    Zombie zombie = EntityType.ZOMBIE.create(s.overworld(), EntitySpawnReason.COMMAND);
                    zombie.snapTo(p[0] + 0.5D, G, p[1] + 0.5D, 180.0F, 0.0F);
                    zombie.setPersistenceRequired();
                    s.overworld().addFreshEntity(zombie);
                    zombie.setTarget(s.getPlayerList().getPlayers().get(0));
                    ids.add(zombie.getId());
                }
                return ids;
            });
            float[] before = server.computeOnServer(s -> new float[]{s.getPlayerList().getPlayers().get(0).getHealth(),
                    ((LivingEntity) s.overworld().getEntity(villagerId)).getHealth()});
            int rounds0 = server.computeOnServer(s -> sentry(s).vehicleRifleAmmo());
            // On automatic it fires without using ammunition (since the HPJ-11 and sentry's automatic switch), so the
            // shots are counted as the player hears them.
            int heard0 = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.GunReportAudio.played());
            int alive = zombies.size();
            // A machine gun: nothing spools up (it played the minigun's wind-up on automatic: the user's review, 2026-11-19).
            float spun = 0.0F;
            for (int tick = 0; tick < 400 && alive > 0; tick++) {
                context.waitTick();
                spun = Math.max(spun, context.computeOnClient(mc -> mc.level.getEntity(this.sentryId) instanceof ttv.migami.jeg.vehicle.entity.base.VehicleEntity v
                        ? v.rotaryGunSpin() : 0.0F));
                if (tick == 40) {
                    context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
                    context.takeScreenshot("sentry_engaging");
                }
                alive = server.computeOnServer(s -> (int) zombies.stream().filter(id -> s.overworld().getEntity(id) instanceof LivingEntity z && z.isAlive()).count());
            }
            float[] after = server.computeOnServer(s -> new float[]{s.getPlayerList().getPlayers().get(0).getHealth(),
                    s.overworld().getEntity(villagerId) instanceof LivingEntity v ? v.getHealth() : 0.0F});
            int rounds1 = server.computeOnServer(s -> sentry(s).vehicleRifleAmmo());
            int heard = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.GunReportAudio.played()) - heard0;
            double nearest = server.computeOnServer(s -> zombies.stream().map(s.overworld()::getEntity)
                    .filter(e -> e != null).mapToDouble(e -> e.distanceTo(sentry(s))).min().orElse(-1.0D));
            LOGGER.info("[sentry] zombies left {}; shots heard {} (ammunition used {}); player {} -> {}; villager {} -> {}; nearest corpse {} blocks",
                    alive, heard, rounds0 - rounds1, before[0], after[0], before[1], after[1], nearest);
            check(alive == 0, "it must cut the zombies down, " + alive + " left");
            check(heard > 0, "it must fire");
            LOGGER.info("[sentry] rotary spin while engaging {}", spun);
            check(spun == 0.0F, "a machine gun must not spool up like a minigun: spin " + spun);
            check(after[0] >= before[0], "the player must not be hurt");
            check(after[1] >= before[1], "the villager must not be hurt");
            LOGGER.info("[sentry] COMPLETE");
        } catch (Throwable failure) {
            LOGGER.error("[sentry] FAILED: {}", failure.getMessage());
            throw failure;
        }
    }

    private SentryEntity sentry(MinecraftServer server) {
        if (server.overworld().getEntity(this.sentryId) instanceof SentryEntity sentry) {
            return sentry;
        }
        throw new AssertionError("the sentry is gone");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
