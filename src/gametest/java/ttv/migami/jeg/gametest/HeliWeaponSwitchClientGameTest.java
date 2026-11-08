package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Each helicopter weapon reloads on its own. The AH-6 pilot empties the rocket pods, switches to the gun while the pods
 * are still reloading, and the gun must fire straight away. A rocket's fire delay must not hold the gun back either.
 * Reloads take 0.448x a hand-held gun's time (another 30% off the vehicle's 0.64x).
 */
public class HeliWeaponSwitchClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int GUN = 0;
    private static final int ROCKETS = 1;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            // Parked on a tower so the rockets fly out over open ground, well away from the helicopter.
            server.runCommand("fill -2 -60 -2 2 -41 2 minecraft:obsidian");
            server.runCommand("tp @p 0.5 -40 -2 0 0");
            server.runCommand("summon jeg:ah6 0.5 -40 0.5 {Rotation:[0f,0f]}");
            context.waitTicks(5);
            server.runCommand("ride @p mount @e[type=jeg:ah6,sort=nearest,limit=1]");
            context.waitFor(mc -> mc.player.getVehicle() instanceof VehicleEntity);

            server.runOnServer(s -> {
                VehicleEntity vehicle = vehicle(s.overworld().getAllEntities());
                String[] ammo = {"rifle_ammo", "rifle_ammo", "rifle_ammo", "small_rocket"};
                for (int slot = 0; slot < ammo.length; slot++) {
                    vehicle.vehicleInventory().setItem(slot, new ItemStack(BuiltInRegistries.ITEM.getValue(Reference.id(ammo[slot])), 64));
                }
            });
            // Both empty weapons start reloading on their own as soon as there is ammo aboard.
            context.waitTicks(2);
            int[] firstReload = server.computeOnServer(s -> {
                VehicleEntity vehicle = vehicle(s.overworld().getAllEntities());
                return new int[] {vehicle.vehicleWeaponSlotReloadTicks(GUN), vehicle.vehicleWeaponSlotReloadTicks(ROCKETS)};
            });
            LOGGER.info("[heli-switch] reload ticks just after loading ammo: gun={} rockets={}", firstReload[0], firstReload[1]);
            check(firstReload[0] > 0 && firstReload[0] <= 47 && firstReload[0] >= 40,
                    "the gun should reload in 47 ticks (0.448x of 105), was " + firstReload[0]);
            check(firstReload[1] > 0 && firstReload[1] <= 45 && firstReload[1] >= 38,
                    "the rockets should reload in 45 ticks (0.448x of 100), was " + firstReload[1]);
            context.waitTicks(55);
            int[] loaded = slots(server);
            LOGGER.info("[heli-switch] loaded: gun={} rockets={}", loaded[0], loaded[1]);
            check(loaded[0] == 150 && loaded[1] == 14, "both weapons should be loaded: gun=" + loaded[0] + " rockets=" + loaded[1]);

            // Empty the pods.
            context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitFor(mc -> ((VehicleEntity) mc.player.getVehicle()).selectedVehicleWeaponIndex(mc.player) == ROCKETS, 40);
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitFor(mc -> ((VehicleEntity) mc.player.getVehicle()).vehicleWeaponSlotAmmo(ROCKETS) == 0, 120);
            context.getInput().releaseKey(options -> options.keyAttack);

            // Straight to the gun while the pods reload.
            context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitFor(mc -> ((VehicleEntity) mc.player.getVehicle()).selectedVehicleWeaponIndex(mc.player) == GUN, 40);
            int[] before = slots(server);
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(10);
            int[] during = slots(server);
            int rocketReload = server.computeOnServer(s -> vehicle(s.overworld().getAllEntities()).vehicleWeaponSlotReloadTicks(ROCKETS));
            context.takeScreenshot("heli_switch_gun_firing_while_pods_reload");
            context.getInput().releaseKey(options -> options.keyAttack);
            LOGGER.info("[heli-switch] gun {} -> {} rounds in 10 ticks while the pods reload ({} ticks left)",
                    before[0], during[0], rocketReload);
            check(rocketReload > 0, "the pods should still be reloading while the gun fires");
            check(during[0] < before[0] - 3, "the gun should fire while the rocket pods reload: " + before[0] + " -> " + during[0]);

            // Pods reloaded in the background: one rocket, then the gun at once.
            context.waitFor(mc -> ((VehicleEntity) mc.player.getVehicle()).vehicleWeaponSlotAmmo(ROCKETS) == 14, 80);
            context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitFor(mc -> ((VehicleEntity) mc.player.getVehicle()).selectedVehicleWeaponIndex(mc.player) == ROCKETS, 40);
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitFor(mc -> ((VehicleEntity) mc.player.getVehicle()).vehicleWeaponSlotAmmo(ROCKETS) < 14, 20);
            context.getInput().releaseKey(options -> options.keyAttack);
            context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitFor(mc -> ((VehicleEntity) mc.player.getVehicle()).selectedVehicleWeaponIndex(mc.player) == GUN, 40);
            int gunBefore = slots(server)[0];
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(4);
            int gunAfter = slots(server)[0];
            context.getInput().releaseKey(options -> options.keyAttack);
            LOGGER.info("[heli-switch] gun right after a rocket: {} -> {} in 4 ticks", gunBefore, gunAfter);
            check(gunAfter < gunBefore, "the gun should fire right after a rocket: " + gunBefore + " -> " + gunAfter);
        }
    }

    private static int[] slots(TestServerContext server) {
        return server.computeOnServer(s -> {
            VehicleEntity vehicle = vehicle(s.overworld().getAllEntities());
            return new int[] {vehicle.vehicleWeaponSlotAmmo(GUN), vehicle.vehicleWeaponSlotAmmo(ROCKETS)};
        });
    }

    private static VehicleEntity vehicle(Iterable<Entity> entities) {
        for (Entity entity : entities) {
            if (entity instanceof VehicleEntity vehicle) {
                return vehicle;
            }
        }
        throw new AssertionError("no helicopter");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
