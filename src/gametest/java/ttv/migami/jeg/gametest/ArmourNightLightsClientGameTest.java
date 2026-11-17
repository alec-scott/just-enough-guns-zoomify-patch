package ttv.migami.jeg.gametest;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.client.render.AircraftLights;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Fleet plan Phase 0.8, the armoured vehicles' lights. A BMP-2, a PLZ-05, a Leopard and a LAV-150, each with a husk at the
 * controls, and an uncrewed Leopard stand side by side at midnight. Fails unless the crewed ones are lit and throw their
 * headlamp beams at night, the uncrewed one is dark, the beams are off by day, and a hull that slows or reverses shows
 * bright tail lamps on the client. Screenshots from outside, at night and by day, and from the BMP-2 driver's seat.
 */
public class ArmourNightLightsClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set midnight");
            server.runCommand("tp @a 2.5 -55 -24 0 10");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
            });
            context.waitTicks(10);
            int[] ids = server.computeOnServer(s -> {
                var level = s.overworld();
                Object[][] spawns = {{ModEntities.BMP2.get(), -13.5D, true}, {ModEntities.PLZ05.get(), -5.5D, true},
                        {ModEntities.LEOPARD2.get(), 2.5D, true}, {ModEntities.LAV150.get(), 10.5D, true},
                        {ModEntities.LEOPARD2.get(), 18.5D, false}};
                int[] out = new int[spawns.length];
                for (int i = 0; i < spawns.length; i++) {
                    @SuppressWarnings("unchecked")
                    EntityType<? extends VehicleEntity> type = (EntityType<? extends VehicleEntity>) spawns[i][0];
                    VehicleEntity vehicle = type.create(level, EntitySpawnReason.COMMAND);
                    vehicle.snapTo((double) spawns[i][1], G, 6.5D, 180.0F, 0.0F);
                    vehicle.addEnergy(vehicle.maxVehicleEnergy());
                    level.addFreshEntity(vehicle);
                    if ((boolean) spawns[i][2]) {
                        var driver = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
                        driver.setNoAi(true);
                        driver.setSilent(true);
                        driver.snapTo(vehicle.getX(), vehicle.getY(), vehicle.getZ(), 0, 0);
                        level.addFreshEntity(driver);
                        driver.startRiding(vehicle, true, true);
                    }
                    out[i] = vehicle.getId();
                }
                return out;
            });
            context.waitTicks(60);
            context.takeScreenshot("armour_lights_night");
            float[][] night = context.computeOnClient(mc -> this.levels(mc, ids));
            server.runCommand("time set noon");
            context.waitTicks(60);
            context.takeScreenshot("armour_lights_day");
            float[][] day = context.computeOnClient(mc -> this.levels(mc, ids));
            String[] names = {"BMP-2", "PLZ-05", "Leopard", "LAV-150", "uncrewed Leopard"};
            for (int i = 0; i < ids.length; i++) {
                LOGGER.info(String.format(Locale.ROOT, "[armour-lights] %s: powered %s/%s, beam night %.2f, day %.2f",
                        names[i], night[i][0] > 0, day[i][0] > 0, night[i][1], day[i][1]));
                boolean crewed = i < 4;
                if (crewed && (night[i][0] <= 0 || night[i][1] < 0.5F)) failures.append(names[i]).append(": not lit with its beam at night\n");
                if (!crewed && (night[i][0] > 0 || night[i][1] > 0)) failures.append(names[i]).append(": lit with nobody at the controls\n");
                if (day[i][1] > 0.01F) failures.append(names[i]).append(": beam on by day\n");
            }

            // Brake and reversing lamps follow the hull's motion: slowing, then backing.
            server.runCommand("time set midnight");
            boolean[] braking = {false, false};
            for (int tick = 0; tick < 30; tick++) {
                double speed = tick < 15 ? 0.4D : 0.4D - (tick - 15) * 0.03D;
                int step = tick;
                server.runOnServer(s -> {
                    VehicleEntity bmp = (VehicleEntity) s.overworld().getEntity(ids[0]);
                    bmp.snapTo(bmp.getX(), G, bmp.getZ() - speed, 180.0F, 0.0F);
                });
                context.waitTick();
                if (step > 18) {
                    braking[0] |= context.computeOnClient(mc -> ((VehicleEntity) mc.level.getEntity(ids[0])).clientBraking());
                }
            }
            for (int tick = 0; tick < 15; tick++) {
                server.runOnServer(s -> {
                    VehicleEntity bmp = (VehicleEntity) s.overworld().getEntity(ids[0]);
                    bmp.snapTo(bmp.getX(), G, bmp.getZ() + 0.2D, 180.0F, 0.0F);
                });
                context.waitTick();
                braking[1] |= context.computeOnClient(mc -> ((VehicleEntity) mc.level.getEntity(ids[0])).clientReversing());
            }
            LOGGER.info("[armour-lights] BMP-2 slowing showed braking {}, backing showed reversing {}", braking[0], braking[1]);
            if (!braking[0]) failures.append("BMP-2: slowing never lit the brake lamps\n");
            if (!braking[1]) failures.append("BMP-2: backing never registered as reversing\n");

            // From the driver's seat at night: the player takes the BMP-2's controls from the husk.
            server.runOnServer(s -> {
                VehicleEntity bmp = (VehicleEntity) s.overworld().getEntity(ids[0]);
                bmp.getPassengers().forEach(p -> p.discard());
            });
            context.waitTicks(5);
            server.runCommand("gamemode survival @a");
            server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding((VehicleEntity) s.overworld().getEntity(ids[0]), true, true));
            context.waitTicks(40);
            context.takeScreenshot("armour_lights_bmp2_driver_night");
        }
        if (failures.length() > 0) {
            LOGGER.error("[armour-lights] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOGGER.info("[armour-lights] COMPLETE");
    }

    /** {powered 1/0, beam level} for each vehicle, read on the client. */
    private float[][] levels(net.minecraft.client.Minecraft mc, int[] ids) {
        float[][] out = new float[ids.length][2];
        for (int i = 0; i < ids.length; i++) {
            VehicleEntity vehicle = (VehicleEntity) mc.level.getEntity(ids[i]);
            var set = AircraftLights.forVehicle(vehicle);
            out[i][0] = AircraftLights.isPowered(vehicle) ? 1 : 0;
            out[i][1] = set == null || set.searchlight() == null ? 0 : AircraftLights.searchlightLevel(vehicle, set.searchlight(), 1.0F);
        }
        return out;
    }
}
