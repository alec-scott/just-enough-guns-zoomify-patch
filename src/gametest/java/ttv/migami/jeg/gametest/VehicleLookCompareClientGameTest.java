package ttv.migami.jeg.gametest;

import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;

/**
 * Same scene, same angles, for comparing releases: the fleet parked in a row, each shot from three-quarter rear and
 * three-quarter front by day and at night. Only uses classes that exist since yftm.17, so the identical file runs on an
 * older checkout. Screenshots: vehicle_look_{day|night}_{vehicle}_{rear|front}.
 */
public class VehicleLookCompareClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final double SPACING = 16.0D;
    private static final double GROUND = -60.0D;

    private record Parked(String name, EntityType<?> type, double distance, double height) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        List<Parked> fleet = List.of(
                new Parked("a10", ModEntities.A10.get(), 13.0D, 2.0D),
                new Parked("ah6", ModEntities.AH6.get(), 9.0D, 2.0D),
                new Parked("mi28", ModEntities.MI28.get(), 12.0D, 2.5D),
                new Parked("bmp2", ModEntities.BMP2.get(), 10.0D, 2.0D),
                new Parked("lav150", ModEntities.LAV150.get(), 9.0D, 2.0D),
                new Parked("truck", ModEntities.TRUCK.get(), 11.0D, 2.0D));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set noon");
            server.runOnServer(s -> {
                var level = s.overworld();
                for (int i = 0; i < fleet.size(); i++) {
                    Entity vehicle = fleet.get(i).type().create(level, EntitySpawnReason.COMMAND);
                    if (vehicle == null) {
                        throw new AssertionError("could not create " + fleet.get(i).name());
                    }
                    vehicle.setPos(i * SPACING, GROUND, 0.0D);
                    vehicle.setYRot(0.0F);
                    vehicle.yRotO = 0.0F;
                    level.addFreshEntity(vehicle);
                }
            });
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
            });
            context.waitTicks(60);
            for (String time : new String[] {"day", "night"}) {
                server.runCommand("time set " + (time.equals("day") ? "noon" : "midnight"));
                context.waitTicks(40);
                for (int i = 0; i < fleet.size(); i++) {
                    Parked parked = fleet.get(i);
                    double x = i * SPACING;
                    // Vehicles face +Z at yaw 0: "rear" stands behind (-Z), "front" ahead (+Z), both off to one side.
                    this.shoot(context, server, x, parked, -1.0D, time + "_" + parked.name() + "_rear");
                    this.shoot(context, server, x, parked, 1.0D, time + "_" + parked.name() + "_front");
                }
            }
            context.runOnClient(mc -> mc.options.hideGui = false);
            LOGGER.info("[vehicle-look] {} vehicles shot by day and night", fleet.size());
        }
    }

    private void shoot(ClientGameTestContext context, TestServerContext server, double x, Parked parked, double side, String name) {
        double d = parked.distance();
        double cx = x + d * 0.55D;
        double cz = side * d * 0.85D;
        double cy = GROUND + parked.height() + d * 0.25D;
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f",
                cx, cy, cz, x, GROUND + parked.height() * 0.6D, 0.0D));
        context.waitTicks(25);
        context.takeScreenshot("vehicle_look_" + name);
    }
}
