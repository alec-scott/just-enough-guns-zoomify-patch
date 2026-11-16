package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.client.ShellGunAim;
import ttv.migami.jeg.vehicle.entity.Type63Entity;
import ttv.migami.jeg.vehicle.projectile.ShellEntity;

/**
 * The Type-63 (design/type63/BRIEF.md), what must not regress: R loads twelve rockets from the crate; a held trigger
 * ripples them off in order, each from its own tube, about 14 ticks apart, and the rockets drawn in the tubes go with
 * them; the shells are rockets (motor glow); a 45-degree rocket flies about 2,000 blocks to where the HUD says; A/D
 * turns the carriage, and the mount lays only 15 degrees either side of it.
 */
public class Type63ClientGameTest implements FabricClientGameTest {
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
            server.runCommand("tp @a 0.5 -60 -4.5 0 0");
            server.runCommand("summon jeg:type63 0.5 -60 0.5 {Rotation:[0f,0f]}");
            context.waitTicks(20);
            this.gunId = server.computeOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity instanceof Type63Entity gun) {
                        // Rockets stack to 12: the thirteenth, for the range shot, goes in the next slot.
                        gun.vehicleInventory().setItem(0, new ItemStack(ModItems.ROCKET_107MM_HE.get(), 12));
                        gun.vehicleInventory().setItem(1, new ItemStack(ModItems.ROCKET_107MM_HE.get(), 1));
                        return gun.getId();
                    }
                }
                throw new AssertionError("the Type-63 was not summoned");
            });
            server.runOnServer(s -> check(s.getPlayerList().getPlayers().get(0).startRiding(gun(s), true, true), "the gunner must man the Type-63"));
            context.waitTicks(10);

            // 1. Load twelve from the crate.
            this.look(context, 0.0F, -10.0F);
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(620);
            int loaded = server.computeOnServer(s -> gun(s).rocketsLoaded());
            int shown = context.computeOnClient(mc -> ((Type63Entity) mc.level.getEntity(this.gunId)).rocketsLoaded());
            check(loaded == 12 && shown == 12, "R must load all twelve tubes (server " + loaded + ", client " + shown + ")");
            context.takeScreenshot("type63_loaded");

            // 2. The mount lays only 15 degrees either side of the carriage; A/D turns the carriage itself.
            this.look(context, 40.0F, -10.0F);
            context.waitTicks(80);
            float laid = server.computeOnServer(s -> gun(s).turretYaw());
            LOGGER.info("[type63] asked 40 deg off the carriage, laid {}", laid);
            check(Math.abs(Math.abs(laid) - 15.0F) < 0.6F, "the mount must stop at 15 degrees either side, laid " + laid);
            float carriage0 = context.computeOnClient(mc -> mc.level.getEntity(this.gunId).getYRot());
            context.getInput().holdKey(options -> options.keyLeft);
            context.waitTicks(20);
            context.getInput().releaseKey(options -> options.keyLeft);
            context.waitTicks(10);
            float carriage1 = context.computeOnClient(mc -> mc.level.getEntity(this.gunId).getYRot());
            float serverCarriage = server.computeOnServer(s -> gun(s).getYRot());
            LOGGER.info("[type63] A for 20 ticks: carriage {} -> {} (server {})", carriage0, carriage1, serverCarriage);
            check(Math.abs(Math.abs(net.minecraft.util.Mth.wrapDegrees(carriage1 - carriage0)) - 20.0F * Type63Entity.CARRIAGE_TURN_RATE) < 2.0F,
                    "A must turn the carriage at " + Type63Entity.CARRIAGE_TURN_RATE + " deg/tick: " + carriage0 + " -> " + carriage1);
            check(Math.abs(net.minecraft.util.Mth.wrapDegrees(serverCarriage - carriage1)) < 0.5F, "the server must follow the carriage's bearing");
            server.runOnServer(s -> gun(s).setYRot(0.0F));
            context.runOnClient(mc -> mc.level.getEntity(this.gunId).setYRot(0.0F));

            // 3. A held trigger ripples the twelve off in order, each from its own tube.
            this.look(context, 0.0F, -10.0F);
            context.waitTicks(80);
            List<Vec3> spawns = new ArrayList<>();
            List<Long> times = new ArrayList<>();
            List<Integer> drawn = new ArrayList<>();
            boolean[] rockets = {true};
            context.getInput().holdKey(options -> options.keyAttack);
            for (int tick = 0; tick < 180 && spawns.size() < 12; tick++) {
                context.waitTick();
                server.runOnServer(s -> {
                    for (var entity : s.overworld().getAllEntities()) {
                        if (entity instanceof ShellEntity shell && entity.getType() == ModEntities.CANNON_SHELL.get()
                                && entity.addTag("type63_seen")) {
                            spawns.add(entity.tickCount == 0 ? entity.position() : entity.position().subtract(entity.getDeltaMovement()));
                            times.add(s.overworld().getGameTime());
                            rockets[0] &= shell.isRocket();
                        }
                    }
                });
                if (tick % 20 == 10) {
                    drawn.add(context.computeOnClient(mc -> ((Type63Entity) mc.level.getEntity(this.gunId)).rocketsLoaded()));
                }
            }
            context.getInput().releaseKey(options -> options.keyAttack);
            LOGGER.info("[type63] ripple: {} rockets at ticks {}; rockets drawn {}", spawns.size(), times, drawn);
            check(spawns.size() == 12, "a held trigger must fire all twelve, fired " + spawns.size());
            check(rockets[0], "the Type-63's shells must be rockets (motor glow and trail)");
            for (int i = 0; i < spawns.size(); i++) {
                for (int j = i + 1; j < spawns.size(); j++) {
                    check(spawns.get(i).distanceTo(spawns.get(j)) > 0.12D, "each rocket must leave its own tube: " + spawns);
                }
            }
            long span = times.getLast() - times.getFirst();
            check(span >= 140 && span <= 170, "twelve rockets 14 ticks apart must take about 154 ticks, took " + span);
            for (int i = 1; i < drawn.size(); i++) {
                check(drawn.get(i) <= drawn.get(i - 1), "the rockets drawn in the tubes must only go down as they fire: " + drawn);
            }
            server.runCommand("kill @e[type=jeg:cannon_shell]");

            // 4. Full range at 45 degrees.
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(620);
            int spare = server.computeOnServer(s -> gun(s).rocketsLoaded());
            check(spare == 1, "the reload must load the crate's last rocket, loaded " + spare);
            // The handwheels lay at 0.5 degrees a tick: 55 degrees up takes 110 ticks.
            this.look(context, 0.0F, -45.0F);
            context.waitTicks(130);
            float elevation = server.computeOnServer(s -> gun(s).elevation(1.0F));
            check(Math.abs(elevation - 45.0F) < 0.6F, "the launcher must be laid at 45 degrees, is at " + elevation);
            var hud = context.computeOnClient(mc -> ShellGunAim.predict((Type63Entity) mc.level.getEntity(this.gunId), 1.0F));
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(2);
            context.getInput().releaseKey(options -> options.keyAttack);
            Vec3[] spawn = null;
            for (int wait = 0; wait < 10 && spawn == null; wait++) {
                context.waitTick();
                spawn = server.computeOnServer(s -> {
                    for (var entity : s.overworld().getAllEntities()) {
                        if (entity.getType() == ModEntities.CANNON_SHELL.get()) {
                            return new Vec3[] {entity.tickCount == 0 ? entity.position() : entity.position().subtract(entity.getDeltaMovement()), entity.position()};
                        }
                    }
                    return null;
                });
            }
            check(spawn != null, "the launcher must fire at 45 degrees");
            Vec3 muzzle = spawn[0];
            Vec3 last = spawn[1];
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
                if (++ticks > Type63Entity.BALLISTICS.maxFlightTicks()) {
                    throw new AssertionError("the rocket never came down");
                }
            }
            double dx = last.x - muzzle.x, dz = last.z - muzzle.z;
            double range = Math.sqrt(dx * dx + dz * dz);
            LOGGER.info(String.format(Locale.ROOT, "[type63] 45 deg: flew %.0f blocks in %d ticks; HUD %.0f", range, ticks, hud.range()));
            check(range > 1700.0D, "a 45 degree rocket must fly about 2,000 blocks, flew " + range);
            // SBW's rocket spread (0.5) scatters a ripple: the HUD gives the centre of it.
            check(Math.abs(hud.range() - range) < 0.06D * range, "the HUD must predict the range within the spread: " + hud.range() + " vs " + range);
            LOGGER.info("[type63] COMPLETE");
        } catch (Throwable failure) {
            LOGGER.error("[type63] FAILED: {}", failure.getMessage());
            throw failure;
        }
    }

    private void look(ClientGameTestContext context, float yaw, float pitch) {
        context.runOnClient(mc -> {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        });
        context.waitTicks(3);
    }

    private Type63Entity gun(MinecraftServer server) {
        if (server.overworld().getEntity(this.gunId) instanceof Type63Entity gun) {
            return gun;
        }
        throw new AssertionError("the Type-63 is gone");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
