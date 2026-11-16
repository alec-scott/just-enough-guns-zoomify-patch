package ttv.migami.jeg.gametest;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.client.ShellGunAim;
import ttv.migami.jeg.vehicle.client.audio.GunReportAudio;
import ttv.migami.jeg.vehicle.entity.Mk42Entity;
import ttv.migami.jeg.vehicle.projectile.ShellEntity;
import ttv.migami.jeg.vehicle.util.ShellBallistics;

/**
 * The Mk42 5"/54 (design/mk42/BRIEF.md), the numbers that must not regress: a 45-degree HE shot fired with the real
 * keys flies its full range (about 3.7 km) through unloaded terrain and lands where the HUD's predictor says; the gunner
 * hears the interior report; SBW's AP and HE damage at the burst and at range; the HE proximity fuze bursts beside an
 * aircraft and not beside one 6 blocks off (nor for AP); and the turret-following hitbox (OBBInfo.Follows) hits the gun
 * and the gunhouse where they are turned to, and misses where they were.
 */
public class Mk42ClientGameTest implements FabricClientGameTest {
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
            server.runCommand("tp @a 0.5 -60 -6.5 0 0");
            server.runCommand("summon jeg:mk42 0.5 -60 0.5 {Rotation:[0f,0f]}");
            context.waitTicks(20);
            this.gunId = server.computeOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity instanceof Mk42Entity gun) {
                        gun.vehicleInventory().setItem(0, new ItemStack(ModItems.SHELL_5IN_HE.get(), 16));
                        gun.vehicleInventory().setItem(1, new ItemStack(ModItems.SHELL_5IN_AP.get(), 16));
                        return gun.getId();
                    }
                }
                throw new AssertionError("the Mk42 was not summoned");
            });

            this.hitbox(context, server);
            this.fullRange(context, server);
            this.damage(context, server);
            this.proximity(context, server);
            this.cluster(context, server);
            this.grapeshot(context, server);
            LOGGER.info("[mk42] COMPLETE");
        } catch (Throwable failure) {
            LOGGER.error("[mk42] FAILED: {}", failure.getMessage());
            throw failure;
        }
    }

    /** The gun and gunhouse boxes follow the turret: hit where they are now, miss where they were. */
    private void hitbox(ClientGameTestContext context, TestServerContext server) {
        // At rest the gun points along +Z (yaw 0): a point on it 5 blocks out, and the gunhouse's rear edge.
        Vec3 gunRest = new Vec3(0.0D, 2.16D, 5.0D);
        Vec3 houseRear = new Vec3(0.0D, 2.0D, -2.6D);
        for (float yaw : new float[] {0.0F, 90.0F, 180.0F}) {
            server.runOnServer(s -> {
                gun(s).setAiTurretAim(yaw, 0.0F);
            });
            context.waitTicks(3);
            String result = server.computeOnServer(s -> {
                Mk42Entity gun = gun(s);
                Vec3 gunNow = gun.barrelPoint(gunRest, 1.0F);
                Vec3 gunThen = gun.position().add(gunRest.x, gunRest.y, gunRest.z);
                Vec3 rearThen = gun.position().add(houseRear);
                boolean hitsGun = gun.clipHitbox(gunNow.add(0, 2.5, 0), gunNow.subtract(0, 2.5, 0), 0.0D) != null;
                boolean hitsOldGun = gun.clipHitbox(gunThen.add(0, 2.5, 0), gunThen.subtract(0, 2.5, 0), 0.0D) != null;
                boolean hitsRear = gun.clipHitbox(rearThen.add(0, 3.0, 0), rearThen.subtract(0, 1.0, 0), 0.0D) != null;
                boolean boxHoldsGun = gun.getBoundingBox().inflate(0.01D).contains(gunNow);
                return hitsGun + " " + hitsOldGun + " " + hitsRear + " " + boxHoldsGun;
            });
            LOGGER.info("[mk42] turret {}: hits gun / old gun spot / rear at rest / entity box holds gun = {}", yaw, result);
            String[] r = result.split(" ");
            check(Boolean.parseBoolean(r[0]), "at turret yaw " + yaw + " a shot at the gun must hit it");
            check(Boolean.parseBoolean(r[3]), "at turret yaw " + yaw + " the entity box must hold the gun (bullets find vehicles by it)");
            if (yaw == 0.0F) {
                check(Boolean.parseBoolean(r[2]), "at rest a shot at the gunhouse's rear must hit");
            } else {
                check(!Boolean.parseBoolean(r[1]), "at turret yaw " + yaw + " the gun's rest position is empty and must miss");
            }
            if (yaw == 90.0F) {
                check(!Boolean.parseBoolean(r[2]), "turned 90 degrees, the gunhouse's old rear edge is empty and must miss");
            }
        }
        server.runOnServer(s -> gun(s).setAiTurretAim(0.0F, 0.0F));
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(5);
    }

    /** A 45-degree HE shot with the real keys: through unloaded terrain to about 3.7 km, where the HUD said. */
    private void fullRange(ClientGameTestContext context, TestServerContext server) {
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().get(0);
            check(player.startRiding(gun(s), true, true), "the gunner must be able to man the Mk42");
        });
        context.waitTicks(10);
        // The gun follows the gunner's view at the mount's real rates (40 deg/s train, 25 deg/s elevation): look 45
        // degrees up along +Z, then 90 degrees to the side, and time it.
        context.runOnClient(mc -> {
            mc.player.setYRot(0.0F);
            mc.player.setXRot(-45.0F);
        });
        context.waitTicks(20);
        float halfway = server.computeOnServer(s -> gun(s).turretPitch());
        context.waitTicks(30);
        float pitch = server.computeOnServer(s -> gun(s).turretPitch());
        LOGGER.info("[mk42] elevation after 20 ticks {}, after 50 {}", -halfway, -pitch);
        check(Math.abs(-halfway - 25.0F) < 3.0F, "the gun must elevate at 25 deg/s (about 25 degrees in 20 ticks), at " + -halfway);
        check(Math.abs(pitch + 45.0F) < 1.0F, "the gun must reach the gunner's 45 degrees, at " + pitch);
        context.runOnClient(mc -> mc.player.setYRot(90.0F));
        context.waitTicks(20);
        float trained = server.computeOnServer(s -> Math.abs(gun(s).turretYaw()));
        context.waitTicks(40);
        float trainedAll = server.computeOnServer(s -> Math.abs(gun(s).turretYaw()));
        LOGGER.info("[mk42] train after 20 ticks {}, after 60 {}", trained, trainedAll);
        check(Math.abs(trained - 40.0F) < 4.0F, "the gun must train at 40 deg/s (about 40 degrees in 20 ticks), at " + trained);
        check(Math.abs(trainedAll - 90.0F) < 1.0F, "the gun must reach the gunner's bearing, at " + trainedAll);
        context.takeScreenshot("mk42_bore_marker");
        context.runOnClient(mc -> mc.player.setYRot(0.0F));
        context.waitTicks(60);
        context.getInput().pressKey(KeyBindings.RELOAD);
        context.waitTicks(140);
        int drum = server.computeOnServer(s -> gun(s).selectedVehicleWeaponAmmo(s.getPlayerList().getPlayers().get(0)));
        check(drum == 16, "R must load the drum from the hold (16 HE in it), loaded " + drum);
        context.takeScreenshot("mk42_laid_45");
        var hud = context.computeOnClient(mc -> ShellGunAim.predict((Mk42Entity) mc.level.getEntity(this.gunId), 1.0F));
        int reportsBefore = GunReportAudio.played();
        // Held a few ticks, as a click is: the vehicle input samples the key on its tick.
        context.getInput().holdKey(options -> options.keyAttack);
        context.waitTicks(3);
        context.getInput().releaseKey(options -> options.keyAttack);
        Vec3[] spawn = null;
        for (int wait = 0; wait < 20 && spawn == null; wait++) {
            context.waitTick();
            spawn = server.computeOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity instanceof ShellEntity shell && entity.getType() == ModEntities.CANNON_SHELL.get()) {
                        return new Vec3[] {shell.position(), shell.getDeltaMovement()};
                    }
                }
                return null;
            });
        }
        check(spawn != null, "the left mouse button must fire a shell");
        context.takeScreenshot("mk42_fired");
        Vec3 velocity = spawn[1].add(0.0D, Mk42Entity.BALLISTICS.gravity(), 0.0D);
        Vec3 muzzle = spawn[0].subtract(spawn[1]);
        ShellBallistics.Impact predicted = Mk42Entity.BALLISTICS.predictToHeight(muzzle, velocity, GROUND);
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
            if (++ticks > Mk42Entity.BALLISTICS.maxFlightTicks()) {
                throw new AssertionError("the shell never came down");
            }
        }
        double range = horizontal(muzzle, last);
        double flat = predicted.horizontalDistance(muzzle);
        LOGGER.info(String.format(Locale.ROOT, "[mk42] 45 deg: flew %.0f blocks in %d ticks; ballistics %.0f; HUD %.0f (assumed level %s); reports %d -> %d, interior %.2f",
                range, ticks, flat, hud.range(), hud.assumedLevel(), reportsBefore, GunReportAudio.played(), GunReportAudio.lastInterior()));
        check(range > 3500.0D, "a 45 degree shell must fly about 3,700 blocks, flew " + range);
        check(Math.abs(range - flat) < 0.02D * flat + 6.0D, "the shell must land where the ballistics predict: " + range + " vs " + flat);
        check(Math.abs(hud.range() - range) < 0.02D * range + 10.0D, "the HUD must predict the range: " + hud.range() + " vs " + range);
        check(GunReportAudio.played() > reportsBefore && GunReportAudio.lastInterior() > 0.5F, "the gunner must hear the interior report");
        server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).stopRiding());
        context.waitTicks(5);
    }

    /** SBW's numbers: HE 120 at the burst easing off to nothing at 10 blocks; AP 450 on what it strikes, 120 within 3. */
    private void damage(ClientGameTestContext context, TestServerContext server) {
        float[] he = this.burstAt(server, context, Mk42Entity.HE, 40.5D, new double[] {2.0D, 6.0D, 11.0D}, false);
        float[] ap = this.burstAt(server, context, Mk42Entity.AP, 80.5D, new double[] {2.0D, 4.0D}, true);
        LOGGER.info("[mk42] HE at 2/6/11 blocks: {} {} {}; AP direct {} at 2 {} at 4 {}", he[0], he[1], he[2], ap[2], ap[0], ap[1]);
        check(he[0] > 90.0F && he[0] <= 120.5F, "2 blocks from an HE burst a target must take most of 120, took " + he[0]);
        check(he[1] > 30.0F && he[1] < 100.0F, "6 blocks from an HE burst the blast must have eased off, took " + he[1]);
        check(he[2] == 0.0F, "outside HE's 10-block radius nothing must be hurt, took " + he[2]);
        check(ap[2] > 450.0F, "an AP shell must do SBW's 450 to what it strikes, plus blast, did " + ap[2]);
        check(ap[0] > 0.0F && ap[0] < 80.0F, "2 blocks from an AP burst the small blast must still hurt, took " + ap[0]);
        check(ap[1] == 0.0F, "outside AP's 3-block radius nothing must be hurt, took " + ap[1]);

        // Danger close: an HE shell coming down 9 blocks from the gun that fired it stays safe (no blast on its own gun).
        float hull = server.computeOnServer(s -> gun(s).vehicleHealth());
        server.runOnServer(s -> {
            var level = s.overworld();
            level.addFreshEntity(new ShellEntity(ModEntities.CANNON_SHELL.get(), level, s.getPlayerList().getPlayers().get(0), gun(s),
                    new Vec3(0.5D, -52.0D, 9.5D), new Vec3(0.0D, -1.5D, 0.0D), Mk42Entity.BALLISTICS, Mk42Entity.HE));
        });
        context.waitTicks(20);
        float hullAfter = server.computeOnServer(s -> gun(s).vehicleHealth());
        LOGGER.info("[mk42] HE 9 blocks from its own gun: hull {} -> {}", hull, hullAfter);
        check(hullAfter == hull, "an HE shell bursting within its blast radius of its own gun must stay safe, hull " + hull + " -> " + hullAfter);
    }

    /** Drops a shell onto the ground at {@code x}; probes stand at the given distances along +Z (and one under it for AP). */
    private float[] burstAt(TestServerContext server, ClientGameTestContext context, ShellEntity.Warhead warhead, double x,
                            double[] distances, boolean direct) {
        for (int i = 0; i < distances.length; i++) {
            server.runCommand(String.format(Locale.ROOT, "summon minecraft:husk %.1f -60 %.1f {NoAI:1b,PersistenceRequired:1b,Health:2000f,Tags:[\"mk42_%d_%d\"],attributes:[{id:\"minecraft:max_health\",base:2000d}]}",
                    x, 0.5D + distances[i] + 0.3D, (int) x, i));
        }
        if (direct) {
            server.runCommand(String.format(Locale.ROOT, "summon minecraft:husk %.1f -60 0.5 {NoAI:1b,PersistenceRequired:1b,Health:2000f,Tags:[\"mk42_direct_%d\"],attributes:[{id:\"minecraft:max_health\",base:2000d}]}", x, (int) x));
        }
        context.waitTicks(5);
        float[] before = new float[distances.length + 1];
        for (int i = 0; i < distances.length; i++) {
            before[i] = health(server, "mk42_" + (int) x + "_" + i);
        }
        if (direct) {
            before[distances.length] = health(server, "mk42_direct_" + (int) x);
        }
        server.runOnServer(s -> {
            var level = s.overworld();
            level.addFreshEntity(new ShellEntity(ModEntities.CANNON_SHELL.get(), level, s.getPlayerList().getPlayers().get(0), gun(s),
                    new Vec3(x, -52.0D, 0.5D), new Vec3(0.0D, -1.5D, 0.0D), Mk42Entity.BALLISTICS, warhead));
        });
        context.waitTicks(20);
        float[] taken = new float[distances.length + 1];
        for (int i = 0; i < distances.length; i++) {
            taken[i] = before[i] - health(server, "mk42_" + (int) x + "_" + i);
        }
        if (direct) {
            taken[distances.length] = before[distances.length] - health(server, "mk42_direct_" + (int) x);
        }
        return taken;
    }

    /** HE bursts when it passes 2.5 blocks from a phantom; not at 6 blocks, and AP never does. */
    private void proximity(ClientGameTestContext context, TestServerContext server) {
        boolean near = this.passPhantom(server, context, Mk42Entity.HE, 2.5D, "mk42_air_near");
        boolean far = this.passPhantom(server, context, Mk42Entity.HE, 6.0D, "mk42_air_far");
        boolean ap = this.passPhantom(server, context, Mk42Entity.AP, 2.5D, "mk42_air_ap");
        LOGGER.info("[mk42] proximity: HE at 2.5 burst {}, HE at 6 burst {}, AP at 2.5 burst {}", near, far, ap);
        check(near, "an HE shell passing 2.5 blocks from an aircraft must burst beside it");
        check(!far, "an HE shell passing 6 blocks off must fly on");
        check(!ap, "an AP shell has no proximity fuze");
    }

    /** Fires a level shell past a hovering phantom at {@code miss} blocks; true when it burst there (and hurt it, for HE). */
    private boolean passPhantom(TestServerContext server, ClientGameTestContext context, ShellEntity.Warhead warhead, double miss, String tag) {
        double z = 120.5D;
        server.runCommand(String.format(Locale.ROOT, "summon minecraft:phantom 0.5 -30 %.1f {NoAI:1b,PersistenceRequired:1b,Tags:[\"%s\"]}", z, tag));
        context.waitTicks(5);
        Vec3 phantom = server.computeOnServer(s -> {
            for (var entity : s.overworld().getAllEntities()) {
                if (entity.entityTags().contains(tag)) {
                    return entity.getBoundingBox().getCenter();
                }
            }
            throw new AssertionError("no phantom " + tag);
        });
        // Launched armed (its fuze arms after 10 ticks of flight), level and off to the side by the miss distance plus
        // the phantom's half width, so the closest approach is `miss` from its box.
        Vec3 start = new Vec3(phantom.x + miss + 0.45D, phantom.y, phantom.z - 60.0D);
        int[] shellId = new int[1];
        server.runOnServer(s -> {
            var level = s.overworld();
            // Gravity pulls it 0.06 a tick: aim up by what it will drop over the 4-tick run to the phantom.
            ShellEntity shell = new ShellEntity(ModEntities.CANNON_SHELL.get(), level, null, gun(s), start,
                    new Vec3(0.0D, 0.15D, 15.0D), Mk42Entity.BALLISTICS, warhead);
            shell.tickCount = 40;
            level.addFreshEntity(shell);
            shellId[0] = shell.getId();
        });
        double burstZ = Double.NaN;
        for (int tick = 0; tick < 12; tick++) {
            context.waitTick();
            Vec3 at = server.computeOnServer(s -> {
                var entity = s.overworld().getEntity(shellId[0]);
                return entity == null || entity.isRemoved() ? null : entity.position();
            });
            if (at == null) {
                break;
            }
            burstZ = at.z;
        }
        boolean gone = server.computeOnServer(s -> {
            var entity = s.overworld().getEntity(shellId[0]);
            return entity == null || entity.isRemoved();
        });
        boolean burstNear = gone && Math.abs(burstZ - phantom.z) < 20.0D;
        server.runCommand("kill @e[type=jeg:cannon_shell]");
        server.runCommand("kill @e[tag=" + tag + "]");
        context.waitTicks(2);
        return burstNear;
    }

    /** A cluster shell fired at the ground breaks up short of it into 18 bomblets that burst around a target. */
    private void cluster(ClientGameTestContext context, TestServerContext server) {
        double x = 120.5D;
        // Bomblets scatter several blocks, so the target area is a 3x3 grid of targets 3 blocks apart, summed.
        for (int i = 0; i < 9; i++) {
            server.runCommand(String.format(Locale.ROOT, "summon minecraft:husk %.1f -60 %.1f {NoAI:1b,PersistenceRequired:1b,Health:2000f,Tags:[\"mk42_cm_%d\"],attributes:[{id:\"minecraft:max_health\",base:2000d}]}",
                    x + (i % 3 - 1) * 3.0D, 0.5D + (i / 3 - 1) * 3.0D, i));
        }
        context.waitTicks(5);
        float before = 0.0F;
        for (int i = 0; i < 9; i++) {
            before += health(server, "mk42_cm_" + i);
        }
        int[] counts = new int[2];
        server.runOnServer(s -> {
            var level = s.overworld();
            // Coming down at 30 degrees from 30 blocks up and back, as a fired shell would.
            Vec3 start = new Vec3(x, -60.0D + 30.0D, 0.5D - 52.0D);
            Vec3 velocity = new Vec3(0.0D, -30.0D, 52.0D).normalize().scale(15.0D);
            ShellEntity shell = new ShellEntity(ModEntities.CANNON_SHELL.get(), level, s.getPlayerList().getPlayers().get(0), gun(s),
                    start, velocity, Mk42Entity.BALLISTICS, Mk42Entity.CM);
            shell.tickCount = 5;
            level.addFreshEntity(shell);
        });
        int most = 0;
        for (int tick = 0; tick < 12; tick++) {
            context.waitTick();
            int now = server.computeOnServer(s -> {
                int n = 0;
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity instanceof ShellEntity shell && shell.warhead() == Mk42Entity.BOMBLET) {
                        n++;
                    }
                }
                return n;
            });
            most = Math.max(most, now);
        }
        context.waitTicks(20);
        float after = 0.0F;
        for (int i = 0; i < 9; i++) {
            after += health(server, "mk42_cm_" + i);
        }
        float taken = before - after;
        LOGGER.info("[mk42] cluster: {} bomblets in the air at once, 9 targets took {} in all", most, taken);
        check(most == 18, "a cluster shell must break into 18 bomblets, saw " + most);
        check(taken > 150.0F, "the bomblets must cover the target area (9 targets 3 blocks apart), took " + taken + " in all");
    }

    /** Grapeshot breaks up at the muzzle into 36 pellets; a target 30 blocks out takes several of them. */
    private void grapeshot(ClientGameTestContext context, TestServerContext server) {
        double x = 160.5D;
        server.runCommand(String.format(Locale.ROOT, "summon minecraft:husk %.1f -60 30.5 {NoAI:1b,PersistenceRequired:1b,Health:2000f,Tags:[\"mk42_gs\"],attributes:[{id:\"minecraft:max_health\",base:2000d}]}", x));
        context.waitTicks(5);
        float before = health(server, "mk42_gs");
        int[] pellets = new int[1];
        server.runOnServer(s -> {
            var level = s.overworld();
            ShellEntity shell = new ShellEntity(ModEntities.CANNON_SHELL.get(), level, s.getPlayerList().getPlayers().get(0), gun(s),
                    new Vec3(x, -59.0D, 0.5D), new Vec3(0.0D, 0.0D, 15.0D), Mk42Entity.BALLISTICS, Mk42Entity.GRAPESHOT);
            shell.tickCount = 5;
            level.addFreshEntity(shell);
        });
        context.waitTick();
        pellets[0] = server.computeOnServer(s -> {
            int n = 0;
            for (var entity : s.overworld().getAllEntities()) {
                if (entity instanceof ShellEntity shell && shell.warhead() == Mk42Entity.PELLET) {
                    n++;
                }
            }
            return n;
        });
        context.waitTicks(20);
        float taken = before - health(server, "mk42_gs");
        LOGGER.info("[mk42] grapeshot: {} pellets, target 30 blocks out took {} ({} pellets' worth)", pellets[0], taken, taken / Mk42Entity.PELLET.directHitDamage());
        check(pellets[0] >= 30, "grapeshot must break into 36 pellets at the muzzle, saw " + pellets[0]);
        check(taken > 2.0F * Mk42Entity.PELLET.directHitDamage(), "a target 30 blocks out must take several pellets, took " + taken);
    }

    private Mk42Entity gun(MinecraftServer server) {
        if (server.overworld().getEntity(this.gunId) instanceof Mk42Entity gun) {
            return gun;
        }
        throw new AssertionError("the Mk42 is gone");
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static float health(TestServerContext server, String tag) {
        return server.computeOnServer(s -> {
            for (var entity : s.overworld().getAllEntities()) {
                if (entity instanceof LivingEntity living && entity.entityTags().contains(tag)) {
                    return living.getHealth();
                }
            }
            throw new AssertionError("probe missing: " + tag);
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
