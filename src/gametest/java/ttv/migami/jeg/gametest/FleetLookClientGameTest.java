package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The fleet's model and texture review (the user, 2026-11-19: "do a model and texture review of all the vehicles ...
 * it should be high quality and look realistic"): each reworked vehicle on its own, by day, from a front three-quarter
 * view and from behind, for screenshots in the pack with its shaders; the sentry also at night (its lamp). Fails only
 * if a vehicle can't be made.
 */
public class FleetLookClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    /** id, and the camera's distance in blocks. */
    private static final Object[][] FLEET = {
            {"mk42", 7.0}, {"bl132", 8.0}, {"speedboat", 9.0}, {"yx100", 11.0}, {"plz05", 11.0}, {"type63", 6.0},
            {"tow", 4.0}, {"howitzer", 8.0}, {"sentry", 3.6}, {"tom6", 14.0}, {"ah6", 10.0}, {"mi28", 15.0}, {"truck", 11.0}};

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("time set 2000");
            server.runCommand("weather clear");
            server.runCommand("gamemode spectator @a");
            server.runCommand("forceload add -32 -32 32 32");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
            });
            for (Object[] entry : FLEET) {
                String id = (String) entry[0];
                double distance = (Double) entry[1];
                server.runCommand("kill @e[type=!minecraft:player]");
                int made = server.computeOnServer(s -> {
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Reference.id(id));
                    if (!(type.create(s.overworld(), EntitySpawnReason.COMMAND) instanceof VehicleEntity vehicle)) return -1;
                    vehicle.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
                    s.overworld().addFreshEntity(vehicle);
                    return vehicle.getId();
                });
                if (made < 0) throw new AssertionError(id + " could not be made");
                // Front three-quarter (the vehicle faces +Z), then from behind the other quarter.
                double d = distance;
                view(context, server, 0.5D + d * 0.62D, G + d * 0.32D, 0.5D + d * 0.78D, id + "_front");
                view(context, server, 0.5D - d * 0.7D, G + d * 0.28D, 0.5D - d * 0.72D, id + "_rear");
                if (id.equals("sentry")) {
                    server.runCommand("time set 18000");
                    view(context, server, 0.5D + d * 0.62D, G + d * 0.32D, 0.5D + d * 0.78D, id + "_night");
                    server.runCommand("time set 2000");
                }
                LOG.info("[fleet-look] {} shot", id);
            }
            context.runOnClient(mc -> mc.options.hideGui = false);
            LOG.info("[fleet-look] COMPLETE");
        }
    }

    private static void view(ClientGameTestContext context, TestServerContext server, double x, double y, double z, String name) {
        server.runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.2f %.2f facing 0.5 %d 0.5", x, y, z, G + 1));
        context.waitTicks(25);
        context.takeScreenshot("look_" + name);
    }
}
