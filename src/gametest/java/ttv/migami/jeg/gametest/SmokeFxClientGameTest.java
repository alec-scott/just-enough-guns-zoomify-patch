package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.render.MissileBlastRenderer;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.network.GunReportPayload;
import ttv.migami.jeg.vehicle.entity.ShellGunEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleDecoyEntity;

/**
 * Real smoke everywhere (the user's review, 2026-11-19: "use real smoke animation, not puff visualisations"; damage and
 * destruction on every vehicle). A row of BMP-2s at 55%, 30% and 10% of their hull and one destroyed and burning, an
 * A-10 at 30%; the howitzer and the Mk42 fired (their muzzle blasts); a salvo of flares. Fails unless the damage, the
 * guns and the flares all put real smoke (SmokeFx puffs) in the air. Screenshots by day and at dusk.
 */
public class SmokeFxClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("time set noon");
            server.runCommand("gamemode spectator @a");
            server.runCommand("forceload add -64 -64 64 64");
            server.runCommand("tp @a 0.5 -55 -22 0 12");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
            });
            context.waitTicks(10);
            float[] shares = {0.55F, 0.3F, 0.1F, -1.0F};
            server.runOnServer(s -> {
                var level = s.overworld();
                for (int i = 0; i < shares.length; i++) {
                    VehicleEntity bmp = ModEntities.BMP2.get().create(level, EntitySpawnReason.COMMAND);
                    bmp.snapTo(-13.5D + i * 9.0D, G, 6.5D, 180.0F, 0.0F);
                    level.addFreshEntity(bmp);
                    float hurt = shares[i] < 0.0F ? bmp.maxVehicleHealth() * 2.0F : bmp.maxVehicleHealth() * (1.0F - shares[i]);
                    bmp.hurtServer(level, level.damageSources().explosion(null, null), hurt);
                }
                VehicleEntity a10 = ModEntities.A10.get().create(level, EntitySpawnReason.COMMAND);
                a10.snapTo(18.5D, G, 26.5D, 180.0F, 0.0F);
                level.addFreshEntity(a10);
                a10.hurtServer(level, level.damageSources().explosion(null, null), a10.maxVehicleHealth() * 0.7F);
            });
            context.waitTicks(80);
            int damage = context.computeOnClient(mc -> MissileBlastRenderer.plumesShown());
            context.takeScreenshot("smokefx_damage_row");

            // The guns: their muzzle blasts, as a client draws one from the report it is sent.
            int[] guns = server.computeOnServer(s -> {
                var level = s.overworld();
                ShellGunEntity howitzer = ModEntities.HOWITZER.get().create(level, EntitySpawnReason.COMMAND);
                howitzer.snapTo(-8.5D, G, 20.5D, 90.0F, 0.0F);
                level.addFreshEntity(howitzer);
                ShellGunEntity mk42 = ModEntities.MK42.get().create(level, EntitySpawnReason.COMMAND);
                mk42.snapTo(6.5D, G, 22.5D, 90.0F, 0.0F);
                level.addFreshEntity(mk42);
                return new int[]{howitzer.getId(), mk42.getId()};
            });
            context.waitTicks(20);
            int before = context.computeOnClient(mc -> MissileBlastRenderer.plumesShown());
            context.runOnClient(mc -> {
                for (int id : guns) {
                    ShellGunEntity gun = (ShellGunEntity) mc.level.getEntity(id);
                    MissileBlastRenderer.gunFired(new GunReportPayload(gun.muzzle(1.0F), gun.reportSound(), gun.reportLoudness(), id));
                }
            });
            context.waitTicks(4);
            int gunSmoke = context.computeOnClient(mc -> MissileBlastRenderer.plumesShown()) - before;
            context.takeScreenshot("smokefx_muzzle_blasts");
            context.waitTicks(30);
            context.takeScreenshot("smokefx_muzzle_blasts_after");

            // Flares.
            server.runOnServer(s -> {
                var level = s.overworld();
                var shooter = s.getPlayerList().getPlayers().get(0);
                for (int i = 0; i < 6; i++) {
                    Vec3 from = new Vec3(0.5D + i - 3.0D, -48.0D, 14.5D);
                    level.addFreshEntity(VehicleDecoyEntity.flare(level, shooter, from, new Vec3(i - 2.5D, 0.6D, 0.4D).normalize(), 0.7F, 6.0F));
                }
            });
            int flaresBefore = context.computeOnClient(mc -> MissileBlastRenderer.plumesShown());
            context.waitTicks(25);
            int flareSmoke = context.computeOnClient(mc -> MissileBlastRenderer.plumesShown()) - flaresBefore;
            context.takeScreenshot("smokefx_flares_day");
            server.runCommand("time set 13500");
            server.runOnServer(s -> {
                var level = s.overworld();
                var shooter = s.getPlayerList().getPlayers().get(0);
                for (int i = 0; i < 6; i++) {
                    Vec3 from = new Vec3(0.5D + i - 3.0D, -48.0D, 14.5D);
                    level.addFreshEntity(VehicleDecoyEntity.flare(level, shooter, from, new Vec3(i - 2.5D, 0.6D, 0.4D).normalize(), 0.7F, 6.0F));
                }
            });
            context.waitTicks(30);
            context.takeScreenshot("smokefx_flares_dusk");
            context.runOnClient(mc -> mc.options.hideGui = false);
            LOG.info("[smokefx] puffs: damage row {}, gun blasts {}, flare trails {}", damage, gunSmoke, flareSmoke);
            if (damage < 150) failures.append("the damaged and burning vehicles put little smoke in the air: ").append(damage).append('\n');
            if (gunSmoke < 20) failures.append("the guns' muzzle blasts made little smoke: ").append(gunSmoke).append('\n');
            if (flareSmoke < 30) failures.append("the flares left little smoke: ").append(flareSmoke).append('\n');
        }
        if (failures.length() > 0) {
            LOG.error("[smokefx] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[smokefx] COMPLETE");
    }
}
