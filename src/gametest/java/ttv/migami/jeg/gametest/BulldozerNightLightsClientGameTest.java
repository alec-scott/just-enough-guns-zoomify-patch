package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.client.render.AircraftLights;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The BD-40's lamps at night (the user's report, 2026-11-19: a white ball floated in the middle of the grille, between
 * the headlamps; it was the beam's own lens glare, which a pair of headlamps must not draw). A crewed BD-40 at midnight
 * and at dusk; fails if its beam draws a lens glow or is not lit. Screenshots from ahead, from above the right front
 * (the user's view) and from the side.
 */
public class BulldozerNightLightsClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set midnight");
            server.runCommand("tp @a 0.5 -58 -2 0 15");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
            });
            context.waitTicks(10);
            int id = server.computeOnServer(s -> {
                var level = s.overworld();
                VehicleEntity dozer = ModEntities.BULLDOZER.get().create(level, EntitySpawnReason.COMMAND);
                dozer.snapTo(0.5D, G, 6.5D, 180.0F, 0.0F);
                dozer.addEnergy(dozer.maxVehicleEnergy());
                level.addFreshEntity(dozer);
                var driver = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
                driver.setNoAi(true);
                driver.setSilent(true);
                driver.snapTo(dozer.getX(), dozer.getY(), dozer.getZ(), 0, 0);
                level.addFreshEntity(driver);
                driver.addTag(ttv.migami.jeg.vehicle.ai.EnemyVehicleController.ENEMY_VEHICLE_CREW_TAG);
                driver.startRiding(dozer, true, true);
                return dozer.getId();
            });
            context.waitTicks(80);
            float[] state = context.computeOnClient(mc -> {
                VehicleEntity dozer = (VehicleEntity) mc.level.getEntity(id);
                var set = AircraftLights.forVehicle(dozer);
                return new float[]{AircraftLights.isPowered(dozer) ? 1 : 0,
                        AircraftLights.searchlightLevel(dozer, set.searchlight(), 1.0F),
                        set.searchlight().lensGlow() ? 1 : 0};
            });
            context.takeScreenshot("bd40_lights_ahead");
            server.runCommand("tp @a 3.5 -56.5 1.0 facing 0.5 -58.6 4.0");
            context.waitTicks(10);
            context.takeScreenshot("bd40_lights_above_right");
            server.runCommand("tp @a 7.5 -58.5 4.5 facing 0.5 -58.5 5.0");
            context.waitTicks(10);
            context.takeScreenshot("bd40_lights_side");
            server.runCommand("time set 13000");
            server.runCommand("tp @a 3.5 -56.5 1.0 facing 0.5 -58.6 4.0");
            context.waitTicks(40);
            context.takeScreenshot("bd40_lights_dusk_above_right");
            LOGGER.info("[bd40-lights] powered {}, beam {}, lens glow {}", state[0], state[1], state[2]);
            if (state[0] <= 0 || state[1] < 0.5F) {
                throw new AssertionError("the crewed BD-40 is not lit with its beam at night");
            }
            if (state[2] > 0) {
                throw new AssertionError("the BD-40's headlamp beam draws its own lens glare between the lamps");
            }
        }
        LOGGER.info("[bd40-lights] COMPLETE");
    }
}
