package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The helicopter's weapon list shows every weapon's own ammo (loaded / spare, or rounds aboard), with the ammo item beside
 * it, not just the selected weapon's. An ammo type with nothing aboard shows as empty.
 */
public class HeliAmmoHudClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("heli ammo hud report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 -60 0 0 0");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.gui.getChat().clearMessages(true);
            });
            // AH-6: rifle rounds and rockets aboard. Mi-28: no large missiles aboard, so that type shows empty.
            this.check(context, server, report, failures, "ah6", 0, new String[]{"rifle_ammo", "small_rocket"});
            this.check(context, server, report, failures, "mi28", 40, new String[]{"rifle_ammo", "small_rocket", "medium_anti_ground_missile"});
            this.checkThermobaricName(context, report, failures);
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    /**
     * The MI-28 pilot's 9M-120F is thermobaric; it loads the same medium anti-ground missiles as the gunner's plain 9M-120,
     * so once fired its ammo line has to name the weapon, not the ammo item.
     */
    private void checkThermobaricName(ClientGameTestContext context, StringBuilder report, StringBuilder failures) {
        String target = "vehicle_9m120_driver_missile";
        for (int press = 0; press < 8; press++) {
            String selected = context.computeOnClient(mc -> {
                VehicleEntity vehicle = (VehicleEntity) mc.player.getVehicle();
                int slot = vehicle.selectedVehicleWeaponIndex(mc.player);
                return vehicle.vehicleData().defaults().weapons().get(slot).weaponId().getPath();
            });
            if (selected.equals(target)) {
                break;
            }
            context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitTicks(4);
        }
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(10);
        context.takeScreenshot("heli_ammo_hud_mi28_thermobaric");
        String[] line = context.computeOnClient(mc -> {
            VehicleEntity vehicle = (VehicleEntity) mc.player.getVehicle();
            int slot = vehicle.selectedVehicleWeaponIndex(mc.player);
            return new String[]{vehicle.vehicleData().defaults().weapons().get(slot).weaponId().getPath(),
                    vehicle.selectedVehicleWeaponAmmo(mc.player) + "/" + vehicle.selectedVehicleWeaponMagazineSize(mc.player)};
        });
        report.append(String.format("mi28 pilot selected %s, loaded %s%n", line[0], line[1]));
        if (!line[0].equals(target)) {
            failures.append("could not select the MI-28 pilot's 9M-120F\n");
        }
    }

    private void check(ClientGameTestContext context, TestServerContext server, StringBuilder report, StringBuilder failures,
                       String id, int x, String[] ammo) {
        server.runCommand("ride @p dismount");
        server.runCommand("tp @a " + x + " -60 0 0 0");
        context.waitTicks(10);
        server.runCommand("execute as @p at @s run summon jeg:" + id + " ~ ~ ~4 {Rotation:[0f,0f]}");
        context.waitTicks(5);
        server.runOnServer(s -> {
            for (Entity entity : s.overworld().getAllEntities()) {
                if (entity instanceof VehicleEntity vehicle && vehicle.vehicleDataId().getPath().equals(id) && vehicle.getPassengers().isEmpty()) {
                    for (int slot = 0; slot < ammo.length; slot++) {
                        vehicle.vehicleInventory().setItem(slot, new ItemStack(BuiltInRegistries.ITEM.getValue(Reference.id(ammo[slot])), 48));
                    }
                }
            }
        });
        server.runCommand("ride @p mount @e[type=jeg:" + id + ",limit=1,sort=nearest]");
        context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
        context.waitTicks(40);
        context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
        context.takeScreenshot("heli_ammo_hud_" + id);
        String slots = context.computeOnClient(mc -> {
            VehicleEntity vehicle = (VehicleEntity) mc.player.getVehicle();
            StringBuilder line = new StringBuilder();
            var weapons = vehicle.vehicleData().defaults().weapons();
            for (int slot = 0; slot < weapons.size(); slot++) {
                line.append(String.format("%s (%s) %d/%d; ", weapons.get(slot).weaponId().getPath(), weapons.get(slot).ammoId().getPath(),
                        vehicle.vehicleWeaponSlotAmmo(slot), vehicle.vehicleWeaponSlotReserveAmmo(slot)));
            }
            return line.toString();
        });
        report.append(id).append(": ").append(slots).append('\n');
        // Every weapon whose ammo is aboard has a count the HUD can show, selected or not.
        for (String ammoName : ammo) {
            if (slots.contains("(" + ammoName + ") 0/0")) {
                failures.append(String.format("%s: a weapon taking %s shows no ammo although 48 are aboard%n", id, ammoName));
            }
        }
    }
}
