package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.ai.EnemyVehicleController;
import ttv.migami.jeg.vehicle.entity.Hpj11Entity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

/**
 * The HPJ-11 on automatic (design/hpj11/BRIEF.md), what must not regress:
 * <ol>
 *   <li>Intercept: an enemy's 80 mm rocket fired at it from 150 blocks out is shot down well short of it.</li>
 *   <li>Aircraft: an enemy AH-6 on a tower 90 blocks off is engaged and hit; a player's AH-6 nearer by, and the player,
 *   are not.</li>
 * </ol>
 */
public class Hpj11ClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private int ciwsId = -1;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> mc.options.renderDistance().set(12));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitTicks(100);
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set midnight");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            server.runCommand("forceload add -16 -16 16 176");
            server.runCommand("forceload add 64 -16 112 16");
            server.runCommand("tp @a -6.5 -60 -6.5 -30 -10");
            this.ciwsId = server.computeOnServer(s -> {
                Hpj11Entity ciws = ModEntities.HPJ11.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                ciws.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
                ciws.addEnergy(ciws.maxVehicleEnergy());
                for (int slot = 0; slot < 6; slot++) {
                    ciws.vehicleInventory().setItem(slot, new ItemStack(ModItems.AMMO.get(Reference.id("autocannon_shell")).get(), 64));
                }
                s.overworld().addFreshEntity(ciws);
                return ciws.getId();
            });
            context.waitTicks(20);
            context.runOnClient(mc -> ttv.migami.jeg.vehicle.client.VehicleFeel.drainEvents());
            server.runOnServer(s -> ciws(s).towKey(s.getPlayerList().getPlayers().get(0)));
            context.waitTicks(5);
            check(server.computeOnServer(s -> ciws(s).automatic()), "N must switch it to automatic");
            // Its search radar sweeps on automatic, and its drive motor is heard (VehicleFeel "servo").
            context.waitTicks(10);
            java.util.List<String> radar = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.VehicleFeel.drainEvents());
            LOGGER.info("[hpj11] movement sounds on automatic: {}", radar);
            check(radar.stream().anyMatch(e -> e.endsWith(":servo")), "the radar drive must be heard on automatic: " + radar);
            int rounds0 = server.computeOnServer(s -> ammo(ciws(s)));

            // 1. Intercept: a rocket from an enemy crewman 150 blocks south, 40 up, flying straight at the mount.
            context.runOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.CiwsAudio.drainEvents());
            int rocketId = server.computeOnServer(s -> {
                Zombie crew = EntityType.ZOMBIE.create(s.overworld(), EntitySpawnReason.COMMAND);
                crew.snapTo(0.5D, G + 40.0D, 150.5D, 0.0F, 0.0F);
                crew.setNoAi(true);
                crew.setNoGravity(true);
                crew.addTag(EnemyVehicleController.ENEMY_VEHICLE_CREW_TAG);
                s.overworld().addFreshEntity(crew);
                Vec3 from = new Vec3(0.5D, G + 40.0D, 146.5D);
                Vec3 to = new Vec3(0.5D, G + 1.5D, 0.5D);
                VehicleMissileEntity rocket = new VehicleMissileEntity(s.overworld(), crew, null, from,
                        to.subtract(from).normalize().scale(2.5D), Reference.id("vehicle_80mm_rocket"));
                s.overworld().addFreshEntity(rocket);
                return rocket.getId();
            });
            Vec3 last = null;
            for (int tick = 0; tick < 200; tick++) {
                context.waitTick();
                Vec3 at = server.computeOnServer(s -> s.overworld().getEntity(rocketId) instanceof VehicleMissileEntity r && r.isAlive() ? r.position() : null);
                if (at == null) {
                    break;
                }
                last = at;
                if (tick == 30) {
                    context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
                    context.takeScreenshot("hpj11_engaging");
                }
            }
            double shotDownAt = last == null ? -1.0D : last.distanceTo(new Vec3(0.5D, G, 0.5D));
            int rounds1 = server.computeOnServer(s -> ammo(ciws(s)));
            LOGGER.info("[hpj11] rocket gone {} blocks from the mount; hold {} -> {} rounds", shotDownAt, rounds0, rounds1);
            // Automatic fire costs no ammunition (the user's review, 2026-11-18): the hold stays full (the rocket's
            // interception is checked below).
            check(rounds1 == rounds0, "automatic fire must not draw rounds from the hold: " + rounds0 + " -> " + rounds1);
            // Heard (CiwsAudio, real CIWS recordings): the barrels spool up, the near and distant loops run, and the burst
            // rings out when it stops.
            context.waitTicks(30);
            java.util.List<String> gun = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.CiwsAudio.drainEvents()).stream()
                    .map(e -> e.substring(e.indexOf(':') + 1)).distinct().toList();
            LOGGER.info("[hpj11] gun sounds: {}", gun);
            for (String name : new String[]{"spin_up", "fire", "fire_distant", "fire_stop"}) {
                check(gun.contains(name), "the gun's " + name + " must be heard: " + gun);
            }
            check(shotDownAt > 20.0D, "the rocket must be shot down well short of the mount, gone at " + shotDownAt + " blocks");

            // 2. Aircraft: an enemy AH-6 on a tower 90 blocks east and a player's one on a tower 60 blocks north.
            server.runCommand("fill 89 -60 -2 93 -36 2 minecraft:stone");
            server.runCommand("fill -2 -60 59 2 -40 63 minecraft:stone");
            int[] helis = server.computeOnServer(s -> {
                VehicleEntity enemy = ModEntities.AH6.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                enemy.snapTo(91.5D, -35.0D, 0.5D, 90.0F, 0.0F);
                enemy.addTag(EnemyVehicleController.ENEMY_VEHICLE_TAG);
                s.overworld().addFreshEntity(enemy);
                VehicleEntity friendly = ModEntities.AH6.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                friendly.snapTo(0.5D, -39.0D, 61.5D, 0.0F, 0.0F);
                s.overworld().addFreshEntity(friendly);
                return new int[]{enemy.getId(), friendly.getId()};
            });
            context.waitTicks(10);
            float[] before = server.computeOnServer(s -> new float[]{heli(s, helis[0]).vehicleHealth(), heli(s, helis[1]).vehicleHealth(),
                    s.getPlayerList().getPlayers().get(0).getHealth()});
            context.waitTicks(160);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.takeScreenshot("hpj11_aircraft");
            float[] after = server.computeOnServer(s -> new float[]{heli(s, helis[0]) == null ? 0.0F : heli(s, helis[0]).vehicleHealth(),
                    heli(s, helis[1]) == null ? -1.0F : heli(s, helis[1]).vehicleHealth(), s.getPlayerList().getPlayers().get(0).getHealth()});
            LOGGER.info("[hpj11] enemy AH-6 {} -> {}; player's AH-6 {} -> {}; player {} -> {}", before[0], after[0], before[1], after[1], before[2], after[2]);
            check(after[0] < before[0], "the enemy AH-6 must be hit");
            check(after[1] == before[1], "the player's AH-6 must not be fired at");
            check(after[2] >= before[2], "the player must not be hurt");
            LOGGER.info("[hpj11] COMPLETE");
        } catch (Throwable failure) {
            LOGGER.error("[hpj11] FAILED: {}", failure.getMessage());
            throw failure;
        }
    }

    private static int ammo(Hpj11Entity ciws) {
        int count = 0;
        for (int slot = 0; slot < ciws.vehicleInventory().getContainerSize(); slot++) {
            count += ciws.vehicleInventory().getItem(slot).getCount();
        }
        return count;
    }

    private Hpj11Entity ciws(MinecraftServer server) {
        if (server.overworld().getEntity(this.ciwsId) instanceof Hpj11Entity ciws) {
            return ciws;
        }
        throw new AssertionError("the HPJ-11 is gone");
    }

    private static VehicleEntity heli(MinecraftServer server, int id) {
        return server.overworld().getEntity(id) instanceof VehicleEntity vehicle ? vehicle : null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
