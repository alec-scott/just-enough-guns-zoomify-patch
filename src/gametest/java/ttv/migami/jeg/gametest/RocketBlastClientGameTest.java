package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;
import ttv.migami.jeg.vehicle.util.VehicleWeaponStats;

/** Real impacts must damage both a nearby target and a probe beyond the previous blast radius. */
public class RocketBlastClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            this.impact(context, server, "vehicle_70mm_rocket", 6.8D, 40.0F, false);
            this.impact(context, server, "vehicle_80mm_rocket", 8.6D, 55.0F, false);
            this.impact(context, server, "vehicle_9m120_driver_missile", 9.6D, 80.0F, true);
            this.impact(context, server, "vehicle_9m120_passenger_missile", 9.6D, 80.0F, true);
            this.impact(context, server, "vehicle_kh39_missile", 23.0D, 100.0F, true);
        }
    }

    private void impact(ClientGameTestContext context, TestServerContext server, String weapon, double edge, float minimumDamage, boolean guided) {
        server.runCommand("kill @e[type=minecraft:husk]");
        server.runCommand("setblock 0 40 2 minecraft:obsidian");
        String nearTag = "blast_near_" + weapon;
        String edgeTag = "blast_edge_" + weapon;
        this.target(server, nearTag, 2.5D);
        this.target(server, edgeTag, 0.5D + edge);
        context.waitTicks(2);
        float nearBefore = this.health(server, nearTag);
        float edgeBefore = this.health(server, edgeTag);
        server.runOnServer(minecraftServer -> {
            var level = minecraftServer.overworld();
            var player = minecraftServer.getPlayerList().getPlayers().get(0);
            Vec3 position = new Vec3(0.5D, 40.5D, 0.5D);
            if (guided) {
                level.addFreshEntity(new VehicleMissileEntity(level, player, null, position, new Vec3(0.0D, 0.0D, 1.0D), Reference.id(weapon)));
            } else {
                var stats = VehicleWeaponStats.get(Reference.id(weapon));
                BulletEntity rocket = new BulletEntity(level, player, stats, new Vec3(0.0D, 0.0D, stats.projectileSpeed()));
                rocket.initialisePosition(position);
                level.addFreshEntity(rocket);
            }
        });
        context.waitTicks(12);
        float nearDamage = nearBefore - this.health(server, nearTag);
        float edgeDamage = edgeBefore - this.health(server, edgeTag);
        LOGGER.info("[rocket-blast] {} nearDamage={} probeDistance={} probeDamage={}", weapon, nearDamage, edge, edgeDamage);
        if (nearDamage < minimumDamage || edgeDamage <= 0.0F) {
            throw new AssertionError(weapon + " must deal stronger close damage and reach its expanded blast radius: near=" + nearDamage + ", edge=" + edgeDamage);
        }
    }

    private void target(TestServerContext server, String tag, double x) {
        server.runCommand("summon minecraft:husk " + x + " 40.5 2 {NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Health:1000f,Tags:[\"" + tag
                + "\"],attributes:[{id:\"minecraft:max_health\",base:1000d}]}");
    }

    private float health(TestServerContext server, String tag) {
        return server.computeOnServer(minecraftServer -> {
            for (var entity : minecraftServer.overworld().getAllEntities()) {
                if (entity instanceof LivingEntity living && entity.entityTags().contains(tag)) {
                    return living.getHealth();
                }
            }
            throw new AssertionError("Blast probe was not spawned: " + tag);
        });
    }
}
