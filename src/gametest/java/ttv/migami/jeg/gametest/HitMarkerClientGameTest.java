package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.entity.BulletEntity;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds the trigger of an automatic rifle on a stationary husk and compares bullets fired, bullets that damaged the
 * husk and hitmarker sounds the client actually started. Every damaging bullet should produce one hitmarker sound.
 */
public class HitMarkerClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int TRIGGER_TICKS = 60;

    @Override
    public void runTest(ClientGameTestContext context) {
        AtomicInteger bullets = new AtomicInteger();
        AtomicInteger impacts = new AtomicInteger();
        AtomicInteger damagingHits = new AtomicInteger();
        AtomicInteger hitSounds = new AtomicInteger();
        AtomicInteger fireSounds = new AtomicInteger();
        AtomicInteger killSounds = new AtomicInteger();
        AtomicInteger wipeSounds = new AtomicInteger();
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof BulletEntity) {
                bullets.incrementAndGet();
            }
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity.getType() == EntityType.HUSK && source.getDirectEntity() instanceof BulletEntity) {
                impacts.incrementAndGet();
            }
            return true;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (entity.getType() == EntityType.HUSK && source.getDirectEntity() instanceof BulletEntity) {
                damagingHits.incrementAndGet();
            }
        });
        context.runOnClient(mc -> mc.getSoundManager().addListener((sound, events, range) -> {
            String path = sound.getIdentifier().getPath();
            if (path.equals("ui.hitmarker.kill")) {
                killSounds.incrementAndGet();
            }
            if (path.equals("ui.hitmarker.squad_wipe")) {
                wipeSounds.incrementAndGet();
            }
            if (path.startsWith("ui.hitmarker")) {
                hitSounds.incrementAndGet();
            } else if (path.endsWith("fire")) {
                fireSounds.incrementAndGet();
            }
        }));

        double pushed;
        int burstHits;
        int burstSounds;
        int killsBeforeWipe;
        int survivors;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 7");
            // A live (AI-enabled) husk so knockback moves it; slowness stops it walking around on its own.
            server.runCommand("execute as @p at @s rotated 0 0 run summon minecraft:husk ^ ^ ^5 "
                    + "{PersistenceRequired:1b,Health:1000f,attributes:[{id:\"minecraft:max_health\",base:1000d}]}");
            server.runCommand("effect give @e[type=minecraft:husk] minecraft:slowness infinite 255 true");
            server.runCommand("item replace entity @p weapon.mainhand with jeg:assault_rifle");
            context.waitTicks(40);
            Vec3 huskStart = huskPosition(server);

            bullets.set(0);
            impacts.set(0);
            damagingHits.set(0);
            hitSounds.set(0);
            fireSounds.set(0);
            context.getInput().holdKey(options -> options.keyAttack);
            for (int tick = 0; tick < TRIGGER_TICKS; tick++) {
                // Pull the aim back onto the husk every tick so recoil does not walk the burst off target.
                server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 7");
                context.waitTick();
            }
            context.getInput().releaseKey(options -> options.keyAttack);
            context.waitTicks(20);
            context.takeScreenshot("hitmarker_after_burst");
            pushed = huskPosition(server).subtract(huskStart).horizontalDistance();
            burstHits = damagingHits.get();
            burstSounds = hitSounds.get();

            // Swap in a one-health husk and shoot it: the kill should play the kill sound.
            server.runCommand("kill @e[type=minecraft:husk]");
            context.waitTicks(30);
            server.runCommand("execute as @p at @s rotated 0 0 run summon minecraft:husk ^ ^ ^5 {PersistenceRequired:1b,Health:1f}");
            server.runCommand("effect give @e[type=minecraft:husk] minecraft:slowness infinite 255 true");
            context.waitTicks(10);
            killSounds.set(0);
            context.getInput().holdKey(options -> options.keyAttack);
            for (int tick = 0; tick < 15; tick++) {
                server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 7");
                context.waitTick();
            }
            context.getInput().releaseKey(options -> options.keyAttack);
            context.waitTicks(10);
            killsBeforeWipe = killSounds.get();

            // Squad wipe: a pack of three; only the kill that leaves none alive nearby should play the wipe sound.
            for (int i = 0; i < 3; i++) {
                server.runCommand("execute as @p at @s rotated 0 0 run summon minecraft:husk ^ ^ ^5 {NoAI:1b,PersistenceRequired:1b,Health:1f}");
            }
            context.waitTicks(10);
            wipeSounds.set(0);
            context.getInput().holdKey(options -> options.keyAttack);
            for (int tick = 0; tick < 40; tick++) {
                server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 7");
                context.waitTick();
            }
            context.getInput().releaseKey(options -> options.keyAttack);
            context.waitTicks(10);
            survivors = server.computeOnServer(minecraftServer -> {
                int alive = 0;
                for (Entity entity : minecraftServer.overworld().getAllEntities()) {
                    if (entity.getType() == EntityType.HUSK && entity.isAlive()) {
                        alive++;
                    }
                }
                return alive;
            });
        }
        LOGGER.info("[hitmarker] killSounds={} wipeSounds={} survivors={}", killsBeforeWipe, wipeSounds.get(), survivors);
        if (survivors != 0 || wipeSounds.get() != 4) {
            throw new AssertionError("Expected one (four-layered) squad wipe sound after clearing the pack, got "
                    + wipeSounds.get() + " with " + survivors + " husks left");
        }
        if (killsBeforeWipe != 2) {
            throw new AssertionError("Expected the (double-layered) kill sound once for the kill, got " + killsBeforeWipe);
        }
        LOGGER.info("[hitmarker] bullets={} impacts={} damagingHits={} hitmarkerSounds={} fireSounds={} huskPushed={}",
                bullets.get(), impacts.get(), burstHits, burstSounds, fireSounds.get(), String.format("%.2f", pushed));
        // CrosshairHandler stacks two copies of each hitmarker sound for loudness.
        if (burstHits == 0 || burstSounds != burstHits * 2) {
            throw new AssertionError("Expected one (double-layered) hitmarker sound per damaging bullet, got " + burstSounds
                    + " sound instances for " + burstHits + " damaging hits");
        }
        if (pushed > 1.5D) {
            throw new AssertionError("Bullets should only nudge the target, but the burst pushed it " + pushed + " blocks");
        }
    }

    private static Vec3 huskPosition(TestServerContext server) {
        return server.computeOnServer(minecraftServer -> {
            for (Entity entity : minecraftServer.overworld().getAllEntities()) {
                if (entity.getType() == EntityType.HUSK) {
                    return entity.position();
                }
            }
            throw new AssertionError("Husk missing");
        });
    }
}
