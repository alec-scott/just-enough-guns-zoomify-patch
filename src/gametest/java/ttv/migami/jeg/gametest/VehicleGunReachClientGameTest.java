package ttv.migami.jeg.gametest;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.client.audio.GunReportAudio;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Fleet plan Phase 0.3: the ground vehicles' guns reach as the aircraft guns do and are heard the same way. A BMP-2 fires
 * its 30 mm and coax, a LAV-150 its 20 mm and a Leopard's commander the weapon station, level along +Z, with the render
 * distance at 6 and the simulation at 5. Fails unless each round is on the long-range path, flies about its full range
 * (1,200 blocks) out past everything loaded and is then gone, and the shot is heard through its report.
 */
public class VehicleGunReachClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    /** A firing range on a platform high above the (normal, not flat) test world's terrain, as the aircraft test flies high. */
    private static final int G = 300;
    private static final double RANGE = 1200.0D;
    private int vehicleId = -1;

    @Override
    public void runTest(ClientGameTestContext context) {
        int[] previous = context.computeOnClient(mc -> new int[] {mc.options.renderDistance().get(), mc.options.simulationDistance().get()});
        context.runOnClient(mc -> {
            mc.options.renderDistance().set(6);
            mc.options.simulationDistance().set(5);
        });
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).create()) {
            context.waitTicks(100);
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            // The range is built where nothing is loaded yet (a normal world's spawn may be far off): load it first.
            server.runCommand("forceload add -16 -16 16 16");
            context.waitTicks(40);
            server.runCommand("fill -12 " + (G - 1) + " -16 12 " + (G - 1) + " 16 minecraft:stone");
            server.runCommand("tp @a 0.5 " + G + " -6.5 0 0");
            context.waitTicks(40);
            this.fire(context, server, ModEntities.BMP2.get(), "autocannon_shell", 0, 0, "vehicle_30mm_cannon", failures);
            this.fire(context, server, ModEntities.BMP2.get(), "rifle_ammo", 1, 0, "vehicle_coax_machine_gun", failures);
            this.fire(context, server, ModEntities.LAV150.get(), "small_shell", 0, 0, "vehicle_20mm_cannon", failures);
            this.fire(context, server, ModEntities.LEOPARD2.get(), "rifle_ammo", 0, 1, "vehicle_leopard2_rws", failures);
        } finally {
            context.runOnClient(mc -> {
                mc.options.renderDistance().set(previous[0]);
                mc.options.simulationDistance().set(previous[1]);
            });
        }
        if (failures.length() > 0) {
            LOGGER.error("[vehicle-gun-reach] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOGGER.info("[vehicle-gun-reach] COMPLETE");
    }

    /** Board the vehicle in {@code seat}, select weapon {@code switches} presses on, fire level along +Z, follow the round. */
    private void fire(ClientGameTestContext context, TestServerContext server, EntityType<? extends VehicleEntity> type, String ammo,
                      int switches, int seat, String weapon, StringBuilder failures) {
        server.runCommand("kill @e[type=!player]");
        server.runCommand("tp @a 0.5 " + G + " -6.5 0 0");
        context.waitTicks(10);
        this.vehicleId = server.computeOnServer(s -> {
            VehicleEntity vehicle = type.create(s.overworld(), EntitySpawnReason.COMMAND);
            vehicle.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
            vehicle.addEnergy(vehicle.maxVehicleEnergy());
            vehicle.vehicleInventory().setItem(0, new ItemStack(ModItems.AMMO.get(Reference.id(ammo)).get(), 64));
            s.overworld().addFreshEntity(vehicle);
            return vehicle.getId();
        });
        context.waitTicks(10);
        server.runOnServer(s -> check(s.getPlayerList().getPlayers().get(0).startRiding(vehicle(s), true, true), "the crew must board"));
        context.waitTicks(10);
        for (int i = 0; i < seat; i++) {
            context.getInput().pressKey(options -> KeyBindings.VEHICLE_CHANGE_SEAT);
            context.waitTicks(10);
        }
        for (int i = 0; i < switches; i++) {
            context.getInput().pressKey(options -> KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitTicks(5);
        }
        context.runOnClient(mc -> {
            mc.player.setYRot(0.0F);
            mc.player.setXRot(0.0F);
        });
        context.getInput().pressKey(options -> KeyBindings.RELOAD);
        context.waitTicks(200);
        int heard = context.computeOnClient(mc -> GunReportAudio.played());
        float healthBefore = server.computeOnServer(s -> vehicle(s).vehicleHealth());
        context.getInput().holdKey(options -> options.keyAttack);
        // Hold the round itself: out past the chunks the server keeps entities in, a lookup by id misses a round that is
        // still flying (AircraftLongRangeClientGameTest does the same).
        BulletEntity[] round = {null};
        for (int tick = 0; tick < 40 && round[0] == null; tick++) {
            context.waitTick();
            round[0] = server.computeOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity instanceof BulletEntity bullet && bullet.getOwner() != null && bullet.getOwner().getVehicle() == vehicle(s)) {
                        return bullet;
                    }
                }
                return null;
            });
        }
        context.getInput().releaseKey(options -> options.keyAttack);
        if (round[0] == null) {
            failures.append(weapon).append(": no round was fired\n");
            return;
        }
        boolean longRange = server.computeOnServer(s -> round[0].isLongRangeRound());
        Vec3 start = server.computeOnServer(s -> round[0].position());
        Vec3 velocity = server.computeOnServer(s -> round[0].getDeltaMovement());
        double[] flight = {0.0D, -1.0D};
        Vec3[] last = {start};
        for (int tick = 0; tick < 400; tick++) {
            context.waitTick();
            Vec3 now = server.computeOnServer(s -> round[0].isRemoved() ? null : round[0].position());
            if (now == null) {
                flight[1] = tick;
                break;
            }
            last[0] = now;
            flight[0] = Math.max(flight[0], now.z - start.z);
        }
        String removal = server.computeOnServer(s -> String.valueOf(round[0].getRemovalReason()));
        float healthAfter = server.computeOnServer(s -> vehicle(s).vehicleHealth());
        LOGGER.info("[vehicle-gun-reach] {}: own vehicle {} -> {}", weapon, healthBefore, healthAfter);
        LOGGER.info(String.format(Locale.ROOT, "[vehicle-gun-reach] %s: from %.1f %.1f %.1f at %.2f %.2f %.2f a tick, last seen %.1f %.1f %.1f, removed %s",
                weapon, start.x, start.y, start.z, velocity.x, velocity.y, velocity.z, last[0].x, last[0].y, last[0].z, removal));
        int heardAfter = context.computeOnClient(mc -> GunReportAudio.played());
        LOGGER.info(String.format(Locale.ROOT, "[vehicle-gun-reach] %s: long-range %s, flew %.0f blocks, gone after %.0f ticks, reports heard %d",
                weapon, longRange, flight[0], flight[1], heardAfter - heard));
        if (!longRange) {
            failures.append(weapon).append(": its round is not on the long-range path\n");
        }
        if (flight[0] < RANGE * 0.85D || flight[1] < 0.0D) {
            failures.append(String.format(Locale.ROOT, "%s: flew %.0f blocks (gone after %.0f ticks), expected about %.0f%n", weapon, flight[0], flight[1], RANGE));
        }
        if (heardAfter <= heard) {
            failures.append(weapon).append(": the shot was not heard through its report\n");
        }
        server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).stopRiding());
        context.waitTicks(5);
    }

    private VehicleEntity vehicle(MinecraftServer server) {
        if (server.overworld().getEntity(this.vehicleId) instanceof VehicleEntity vehicle) {
            return vehicle;
        }
        throw new AssertionError("the vehicle is gone");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
