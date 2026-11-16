package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.client.ShellGunAim;
import ttv.migami.jeg.vehicle.entity.Bl132Entity;

/**
 * The BL-132 130 mm/58 four-gun mount (design/bl132/BRIEF.md), the numbers that must not regress: a held trigger fires
 * a salvo of four from four different muzzles (each where its gun is), four ticks apart, then the drum reloads from the
 * hold in 4.5 s; each barrel recoils as it fires (screenshot from the side); the hitbox follows the turret; a
 * 45-degree shell flies its full range through unloaded terrain to where the HUD says.
 */
public class Bl132ClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final double GROUND = -60.0D;
    private int gunId = -1;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> mc.options.renderDistance().set(12));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitTicks(100);
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            server.runCommand("tp @a 0.5 -60 -8.5 0 0");
            server.runCommand("summon jeg:bl132 0.5 -60 0.5 {Rotation:[0f,0f]}");
            context.waitTicks(20);
            this.gunId = server.computeOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity instanceof Bl132Entity gun) {
                        gun.vehicleInventory().setItem(0, new ItemStack(ModItems.SHELL_5IN_HE.get(), 16));
                        return gun.getId();
                    }
                }
                throw new AssertionError("the BL-132 was not summoned");
            });
            this.hitbox(context, server);
            this.salvo(context, server);
            this.fullRange(context, server);
            LOGGER.info("[bl132] COMPLETE");
        } catch (Throwable failure) {
            LOGGER.error("[bl132] FAILED: {}", failure.getMessage());
            throw failure;
        }
    }

    private void hitbox(ClientGameTestContext context, TestServerContext server) {
        Vec3 gunsRest = new Vec3(0.0D, 2.63D, 5.5D);
        for (float yaw : new float[] {0.0F, 90.0F}) {
            server.runOnServer(s -> gun(s).setAiTurretAim(yaw, 0.0F));
            context.waitTicks(3);
            boolean[] r = server.computeOnServer(s -> {
                Bl132Entity gun = gun(s);
                Vec3 now = gun.barrelPoint(gunsRest, 1.0F);
                Vec3 then = gun.position().add(gunsRest);
                return new boolean[] {
                        gun.clipHitbox(now.add(0, 3, 0), now.subtract(0, 3, 0), 0.0D) != null,
                        gun.clipHitbox(then.add(0, 3, 0), then.subtract(0, 3, 0), 0.0D) != null,
                        gun.getBoundingBox().inflate(0.01D).contains(now)};
            });
            LOGGER.info("[bl132] turret {}: hits guns {} / old spot {} / entity box holds guns {}", yaw, r[0], r[1], r[2]);
            check(r[0] && r[2], "at turret yaw " + yaw + " the guns must be hit where they point and inside the entity box");
            if (yaw != 0.0F) {
                check(!r[1], "turned " + yaw + " degrees, the guns' old place must be empty");
            }
        }
        server.runOnServer(s -> gun(s).setAiTurretAim(0.0F, 0.0F));
        context.waitTicks(3);
    }

    /** Four shells from four muzzles, four ticks apart, then a 4.5 s reload. */
    private void salvo(ClientGameTestContext context, TestServerContext server) {
        server.runOnServer(s -> check(s.getPlayerList().getPlayers().get(0).startRiding(gun(s), true, true), "the gunner must man the BL-132"));
        context.waitTicks(10);
        context.runOnClient(mc -> {
            mc.player.setYRot(0.0F);
            mc.player.setXRot(-5.0F);
        });
        context.getInput().pressKey(KeyBindings.RELOAD);
        context.waitTicks(110);
        int loaded = server.computeOnServer(s -> gun(s).selectedVehicleWeaponAmmo(s.getPlayerList().getPlayers().get(0)));
        check(loaded == 4, "R must load all four guns, loaded " + loaded);
        context.takeScreenshot("bl132_gunner_view");
        // Each barrel's recoil on the client as the salvo goes off: the one just fired is back, the rest run out.
        List<String> recoils = new ArrayList<>();
        List<Vec3> spawns = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        context.getInput().holdKey(options -> options.keyAttack);
        for (int tick = 0; tick < 24; tick++) {
            context.waitTick();
            float[] recoil = context.computeOnClient(mc -> {
                Bl132Entity gun = (Bl132Entity) mc.level.getEntity(this.gunId);
                return new float[] {gun.recoilPixels(0, 0.0F), gun.recoilPixels(1, 0.0F), gun.recoilPixels(2, 0.0F), gun.recoilPixels(3, 0.0F)};
            });
            recoils.add(String.format(Locale.ROOT, "%.0f/%.0f/%.0f/%.0f", recoil[0], recoil[1], recoil[2], recoil[3]));
            server.runOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity.getType() == ModEntities.CANNON_SHELL.get() && entity.tickCount <= 1
                            && entity.addTag("bl132_seen")) {
                        // A shell seen before its first tick is still at its muzzle.
                        spawns.add(entity.tickCount == 0 ? entity.position() : entity.position().subtract(entity.getDeltaMovement()));
                        times.add(s.overworld().getGameTime());
                    }
                }
            });
        }
        context.getInput().releaseKey(options -> options.keyAttack);
        LOGGER.info("[bl132] recoil by tick (guns in firing order): {}", recoils);
        int[] peaks = new int[4];
        for (String row : recoils) {
            String[] r = row.split("/");
            for (int i = 0; i < 4; i++) {
                peaks[i] = Math.max(peaks[i], Integer.parseInt(r[i]));
            }
        }
        check(peaks[0] >= 8 && peaks[1] >= 8 && peaks[2] >= 8 && peaks[3] >= 8, "every barrel must recoil as it fires: " + recoils);
        check(recoils.stream().noneMatch(row -> java.util.Arrays.stream(row.split("/")).allMatch(v -> Integer.parseInt(v) >= 8)),
                "the barrels must recoil in turn, not together: " + recoils);
        LOGGER.info("[bl132] salvo: {} shells at ticks {}; muzzles {}", spawns.size(), times, spawns);
        check(spawns.size() == 4, "a held trigger must fire the four loaded guns, fired " + spawns.size());
        for (int i = 0; i < spawns.size(); i++) {
            for (int j = i + 1; j < spawns.size(); j++) {
                check(spawns.get(i).distanceTo(spawns.get(j)) > 1.0D, "each shell must leave a different muzzle: " + spawns);
            }
        }
        long span = times.getLast() - times.getFirst();
        check(span >= 10 && span <= 14, "four rounds four ticks apart must take about 12 ticks, took " + span);
        // The reload starts when the held trigger finds the guns empty, a cooldown (4 ticks) after the last shot.
        int waited = 0;
        while (server.computeOnServer(s -> gun(s).selectedVehicleWeaponAmmo(s.getPlayerList().getPlayers().get(0))) < 4) {
            context.waitTick();
            if (++waited > 300) {
                throw new AssertionError("the guns never reloaded");
            }
        }
        long reloadTicks = server.computeOnServer(s -> s.overworld().getGameTime()) - times.getLast() - 4;
        LOGGER.info("[bl132] reloaded {} ticks after the guns ran dry", reloadTicks);
        check(reloadTicks >= 84 && reloadTicks <= 98, "reloading four guns must take SBW's 90 ticks, took " + reloadTicks);
        server.runCommand("kill @e[type=jeg:cannon_shell]");
    }

    private void fullRange(ClientGameTestContext context, TestServerContext server) {
        context.runOnClient(mc -> mc.player.setXRot(-45.0F));
        context.waitTicks(40);
        var hud = context.computeOnClient(mc -> ShellGunAim.predict((Bl132Entity) mc.level.getEntity(this.gunId), 1.0F));
        context.getInput().holdKey(options -> options.keyAttack);
        context.waitTicks(2);
        context.getInput().releaseKey(options -> options.keyAttack);
        Vec3[] spawn = null;
        for (int wait = 0; wait < 10 && spawn == null; wait++) {
            context.waitTick();
            spawn = server.computeOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity.getType() == ModEntities.CANNON_SHELL.get()) {
                        return new Vec3[] {entity.position(), entity.getDeltaMovement()};
                    }
                }
                return null;
            });
        }
        check(spawn != null, "the gun must fire at 45 degrees");
        Vec3 muzzle = spawn[0].subtract(spawn[1]);
        double flat = Bl132Entity.BALLISTICS.predictToHeight(muzzle, spawn[1].add(0.0D, Bl132Entity.BALLISTICS.gravity(), 0.0D), GROUND)
                .horizontalDistance(muzzle);
        Vec3 last = spawn[0];
        int ticks = 0;
        while (true) {
            Vec3 now = server.computeOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity.getType() == ModEntities.CANNON_SHELL.get()) {
                        return entity.position();
                    }
                }
                return null;
            });
            if (now == null) {
                break;
            }
            last = now;
            context.waitTick();
            if (++ticks > Bl132Entity.BALLISTICS.maxFlightTicks()) {
                throw new AssertionError("the shell never came down");
            }
        }
        double dx = last.x - muzzle.x, dz = last.z - muzzle.z;
        double range = Math.sqrt(dx * dx + dz * dz);
        LOGGER.info(String.format(Locale.ROOT, "[bl132] 45 deg: flew %.0f blocks in %d ticks; ballistics %.0f; HUD %.0f", range, ticks, flat, hud.range()));
        check(range > 3500.0D, "a 45 degree shell must fly about 3,700 blocks, flew " + range);
        check(Math.abs(range - flat) < 0.02D * flat + 6.0D, "the shell must land where the ballistics predict: " + range + " vs " + flat);
        check(Math.abs(hud.range() - range) < 0.02D * range + 10.0D, "the HUD must predict the range: " + hud.range() + " vs " + range);
    }

    private Bl132Entity gun(MinecraftServer server) {
        if (server.overworld().getEntity(this.gunId) instanceof Bl132Entity gun) {
            return gun;
        }
        throw new AssertionError("the BL-132 is gone");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
