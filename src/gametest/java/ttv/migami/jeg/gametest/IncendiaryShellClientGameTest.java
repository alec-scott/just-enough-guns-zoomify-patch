package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.entity.Mk42Entity;
import ttv.migami.jeg.vehicle.entity.Yx100Entity;
import ttv.migami.jeg.vehicle.projectile.ShellEntity;

/**
 * The incendiary shell that replaced grapeshot (the user, 2026-11-19: "change GS shells to incendiary shells which
 * explode over a target leaving flames below it"). Three shots at six husks standing round a spot on open ground:
 * <ul>
 *     <li>a Mk42 shell coming down at 45 degrees: it bursts in the air over the husks (3 to 12 blocks up), the ground
 *     round them burns, and most of them catch fire;</li>
 *     <li>the same with the server's tnt_explodes rule off: no fire is set, but the fragments still set the husks
 *     alight;</li>
 *     <li>the YX-100 firing it flat with the real keys at husks in two ranks 40 blocks ahead: they burn and the ground
 *     round them does, and the tank is not hurt.</li>
 * </ul>
 * Screenshots from the side: the burst, the fragments falling and the ground burning.
 */
public class IncendiaryShellClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;

    private int gunId;
    private int tankId;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("difficulty normal");
            server.runCommand("time set noon");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            server.runCommand("effect give @a minecraft:fire_resistance infinite 0 true");
            server.runCommand("forceload add -32 -32 48 96");
            server.runCommand("forceload add 140 -80 260 32");
            // The Mk42 the steep shots are fired from (its fuze stays safe near it), far from where they land.
            server.runCommand("summon jeg:mk42 0.5 -60 -20.5 {Rotation:[0f,0f]}");
            context.waitTicks(10);
            this.gunId = server.computeOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity instanceof Mk42Entity gun) {
                        return gun.getId();
                    }
                }
                throw new AssertionError("the Mk42 was not summoned");
            });

            this.steepShot(context, world, failures, new Vec3(160.5D, G, 0.5D), true);
            server.runCommand("gamerule tnt_explodes false");
            this.steepShot(context, world, failures, new Vec3(220.5D, G, 0.5D), false);
            server.runCommand("gamerule tnt_explodes true");
            this.directFire(context, server, failures);
        } catch (Throwable failure) {
            LOGGER.error("[incendiary] FAILED: {}", failure.getMessage());
            throw failure;
        } finally {
            context.runOnClient(mc -> {
                mc.options.hideGui = false;
                if (mc.player != null) {
                    mc.setCameraEntity(mc.player);
                }
            });
        }
        if (failures.length() > 0) {
            LOGGER.error("[incendiary] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOGGER.info("[incendiary] COMPLETE");
    }

    /** A Mk42 incendiary coming down at 45 degrees onto six husks round {@code target}, watched from the side. */
    private void steepShot(ClientGameTestContext context, TestSingleplayerContext world, StringBuilder failures, Vec3 target, boolean fireAllowed) {
        TestServerContext server = world.getServer();
        String tag = fireAllowed ? "steep" : "steep_no_fire";
        // The player stands off to the side, so the client has the chunks and entities there to draw.
        server.runCommand(String.format(Locale.ROOT, "tp @a %.1f %d %.1f", target.x - 30.0D, G, target.z - 14.0D));
        context.waitTicks(20);
        world.getClientLevel().waitForChunksRender();
        List<Integer> husks = this.husks(server, target, 3.0D, false);
        int eye = this.camera(server, target.add(-24.0D, 5.0D, -6.0D), -75.0F, -6.0F);
        context.waitTicks(10);
        context.runOnClient(mc -> {
            mc.options.hideGui = true;
            mc.setCameraEntity(mc.level.getEntity(eye));
        });
        context.waitTicks(5);
        // 15 blocks a tick, from 60 back and 60 up: about four ticks out.
        server.runOnServer(s -> {
            var level = s.overworld();
            Vec3 velocity = new Vec3(0.0D, -1.0D, 1.0D).normalize().scale(Mk42Entity.BALLISTICS.muzzleVelocity());
            ShellEntity shell = new ShellEntity(ModEntities.CANNON_SHELL.get(), level, s.getPlayerList().getPlayers().get(0),
                    level.getEntity(this.gunId), target.add(0.0D, 60.0D, -60.0D), velocity, Mk42Entity.BALLISTICS, Mk42Entity.INCENDIARY);
            shell.tickCount = 5;
            level.addFreshEntity(shell);
        });
        Vec3 burst = this.watchBurst(context, server, tag);
        int fire = this.peakFire(context, server, target, 10, tag);
        int alight = this.alight(server, husks);
        double height = burst == null ? Double.NaN : burst.y - G;
        double horizontal = burst == null ? Double.NaN : Math.hypot(burst.x - target.x, burst.z - target.z);
        LOGGER.info("[incendiary] {}: burst {} blocks up, {} blocks across from the husks; fire blocks at most {}; husks alight or dead {} of {}",
                tag, String.format(Locale.ROOT, "%.1f", height), String.format(Locale.ROOT, "%.1f", horizontal), fire, alight, husks.size());
        if (burst == null) {
            failures.append(tag).append(": no burst seen\n");
        } else if (height < 3.0D || height > 12.0D) {
            failures.append(tag).append(": burst ").append(String.format(Locale.ROOT, "%.1f", height)).append(" blocks up, not over them\n");
        }
        if (fireAllowed && fire < 8) {
            failures.append(tag).append(": only ").append(fire).append(" fire blocks round the husks\n");
        }
        if (!fireAllowed && fire > 0) {
            failures.append(tag).append(": ").append(fire).append(" fire blocks with tnt_explodes off\n");
        }
        if (alight < 4) {
            failures.append(tag).append(": only ").append(alight).append(" of 6 husks caught fire\n");
        }
        this.clear(server, husks);
    }

    /** The YX-100 fires the incendiary with the real keys, flat, at six husks 40 blocks ahead. */
    private void directFire(ClientGameTestContext context, TestServerContext server, StringBuilder failures) {
        Vec3 target = new Vec3(0.5D, G, 40.5D);
        List<Integer> husks = this.husks(server, target, 1.0D, true);
        this.tankId = server.computeOnServer(s -> {
            Yx100Entity tank = ModEntities.YX100.get().create(s.overworld(), EntitySpawnReason.COMMAND);
            tank.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
            tank.addEnergy(tank.maxVehicleEnergy());
            tank.vehicleInventory().setItem(0, new ItemStack(ModItems.SHELL_5IN_GS.get(), 8));
            s.overworld().addFreshEntity(tank);
            return tank.getId();
        });
        context.waitTicks(20);
        context.runOnClient(mc -> {
            mc.setCameraEntity(mc.player);
            mc.options.hideGui = false;
        });
        server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(s.overworld().getEntity(this.tankId), true, true));
        context.waitTicks(10);
        // Shells in the list: HE, AP, CM, then the incendiary.
        for (int i = 0; i < 3; i++) {
            context.getInput().pressKey(options -> KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitTicks(5);
        }
        context.runOnClient(mc -> {
            mc.player.setYRot(0.0F);
            mc.player.setXRot(1.5F);
        });
        for (int wait = 0; wait < 300 && server.computeOnServer(s -> tank(s).vehicleWeaponSlotAmmo(3)) < 1; wait++) {
            context.waitTick();
        }
        context.waitTicks(40);
        String selected = server.computeOnServer(s -> String.valueOf(tank(s).selectedVehicleWeaponId(s.getPlayerList().getPlayers().get(0))));
        float tankBefore = server.computeOnServer(s -> tank(s).vehicleHealth());
        int eye = this.camera(server, target.add(-20.0D, 4.0D, -14.0D), -55.0F, -4.0F);
        context.waitTicks(5);
        context.runOnClient(mc -> {
            mc.options.hideGui = true;
            mc.setCameraEntity(mc.level.getEntity(eye));
        });
        context.getInput().holdKey(options -> options.keyAttack);
        context.waitTicks(2);
        context.getInput().releaseKey(options -> options.keyAttack);
        Vec3 burst = this.watchBurst(context, server, "direct");
        int fire = this.peakFire(context, server, target, 9, "direct");
        int alight = this.alight(server, husks);
        float tankAfter = server.computeOnServer(s -> tank(s).vehicleHealth());
        LOGGER.info("[incendiary] direct: weapon {}, burst at {}; fire blocks at most {}; husks alight or dead {} of {}; tank {} -> {}",
                selected, burst, fire, alight, husks.size(), tankBefore, tankAfter);
        context.runOnClient(mc -> {
            mc.setCameraEntity(mc.player);
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        context.waitTicks(2);
        context.takeScreenshot("incendiary_direct_gunner");
        if (!selected.endsWith("vehicle_yx100_gs")) {
            failures.append("direct: the incendiary (the gs slot) was not selected: ").append(selected).append('\n');
        }
        if (burst == null) {
            failures.append("direct: no burst seen\n");
        }
        if (fire < 4) {
            failures.append("direct: only ").append(fire).append(" fire blocks round the husks\n");
        }
        if (alight < 4) {
            failures.append("direct: only ").append(alight).append(" of 6 husks caught fire\n");
        }
        if (tankAfter < tankBefore) {
            failures.append("direct: the tank was hurt by its own shell (").append(tankBefore).append(" -> ").append(tankAfter).append(")\n");
        }
    }

    /**
     * Ticks until the shell's burning fragments appear (they are thrown from where it burst, and sit there until the
     * next tick) and returns that point; screenshots of the burst and of the fragments coming down.
     */
    private Vec3 watchBurst(ClientGameTestContext context, TestServerContext server, String tag) {
        Vec3 burst = null;
        for (int tick = 0; tick < 40 && burst == null; tick++) {
            context.waitTick();
            burst = server.computeOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity instanceof ShellEntity shell && shell.isFireFragment()) {
                        return shell.position();
                    }
                }
                return null;
            });
        }
        int fragments = server.computeOnServer(s -> {
            int n = 0;
            for (var entity : s.overworld().getAllEntities()) {
                if (entity instanceof ShellEntity shell && shell.isFireFragment()) {
                    n++;
                }
            }
            return n;
        });
        LOGGER.info("[incendiary] {}: {} burning fragments thrown", tag, fragments);
        context.waitTicks(2);
        context.takeScreenshot("incendiary_" + tag + "_burst");
        context.waitTicks(5);
        context.takeScreenshot("incendiary_" + tag + "_falling");
        return burst;
    }

    /** The most fire blocks seen round {@code target} over the next two seconds; a screenshot as it burns. */
    private int peakFire(ClientGameTestContext context, TestServerContext server, Vec3 target, int radius, String tag) {
        int most = 0;
        for (int tick = 0; tick < 40; tick++) {
            context.waitTick();
            int now = server.computeOnServer(s -> {
                int n = 0;
                BlockPos centre = BlockPos.containing(target);
                for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-radius, -2, -radius), centre.offset(radius, 4, radius))) {
                    if (s.overworld().getBlockState(pos).is(Blocks.FIRE)) {
                        n++;
                    }
                }
                return n;
            });
            most = Math.max(most, now);
            if (tick == 12) {
                context.takeScreenshot("incendiary_" + tag + "_burning");
            }
        }
        return most;
    }

    /** Six husks round {@code target}: in a ring {@code spread} across, or (rows) two ranks of three, a block apart. */
    private List<Integer> husks(TestServerContext server, Vec3 target, double spread, boolean rows) {
        return server.computeOnServer(s -> {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                double angle = i * Math.PI / 3.0D;
                double x = rows ? target.x + (i % 3 - 1) * spread : target.x + Math.cos(angle) * spread;
                double z = rows ? target.z + (i / 3) * spread : target.z + Math.sin(angle) * spread;
                var husk = EntityType.HUSK.create(s.overworld(), EntitySpawnReason.COMMAND);
                husk.snapTo(x, G, z, 180.0F, 0.0F);
                husk.setNoAi(true);
                husk.setPersistenceRequired();
                s.overworld().addFreshEntity(husk);
                ids.add(husk.getId());
            }
            return ids;
        });
    }

    /** Husks burning or dead. */
    private int alight(TestServerContext server, List<Integer> husks) {
        return server.computeOnServer(s -> {
            int n = 0;
            for (int id : husks) {
                if (!(s.overworld().getEntity(id) instanceof LivingEntity husk) || !husk.isAlive() || husk.isOnFire()
                        || husk.getHealth() < husk.getMaxHealth()) {
                    n++;
                }
            }
            return n;
        });
    }

    private void clear(TestServerContext server, List<Integer> husks) {
        server.runOnServer(s -> {
            for (int id : husks) {
                var husk = s.overworld().getEntity(id);
                if (husk != null) {
                    husk.discard();
                }
            }
        });
    }

    /** An invisible stand to watch from, as a camera. */
    private int camera(TestServerContext server, Vec3 at, float yaw, float pitch) {
        return server.computeOnServer(s -> {
            var stand = EntityType.ARMOR_STAND.create(s.overworld(), EntitySpawnReason.COMMAND);
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.snapTo(at.x, at.y, at.z, yaw, pitch);
            stand.setYHeadRot(yaw);
            s.overworld().addFreshEntity(stand);
            return stand.getId();
        });
    }

    private Yx100Entity tank(MinecraftServer s) {
        return (Yx100Entity) s.overworld().getEntity(this.tankId);
    }
}
