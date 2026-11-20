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
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Views the user's review of 2026-11-19 asked about, for screenshots: the AH-6 and the Mi-28 from the pilot's seat in
 * first person with the HUD (the helmet display, no reticle over the view), and the bush plane's lamps at night from
 * outside (ahead, behind, the side) and from its seat. Fails only if the pilot can't board.
 */
public class AircraftCockpitViewsClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;

    /** Holds free look and turns the pilot's head down and right to the centre stack, or lets go. */
    private static void lookAtPanel(ClientGameTestContext context, boolean look) {
        if (look) {
            context.getInput().holdKey(options -> ttv.migami.jeg.client.KeyBindings.VEHICLE_FREE_LOOK);
            context.waitTicks(3);
            context.runOnClient(mc -> {
                mc.player.setYRot(mc.player.getVehicle().getYRot() + 14.0F);
                mc.player.setXRot(34.0F);
            });
            context.waitTicks(25);
        } else {
            context.getInput().releaseKey(options -> ttv.migami.jeg.client.KeyBindings.VEHICLE_FREE_LOOK);
            context.waitTicks(15);
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("time set noon");
            server.runCommand("gamemode creative @a");
            server.runCommand("forceload add -96 -96 96 96");
            for (var heli : new Object[][]{{ModEntities.AH6.get(), "ah6"}, {ModEntities.MI28.get(), "mi28"}}) {
                @SuppressWarnings("unchecked")
                EntityType<? extends VehicleEntity> type = (EntityType<? extends VehicleEntity>) heli[0];
                String name = (String) heli[1];
                server.runCommand("ride @p dismount");
                server.runCommand("kill @e[type=jeg:" + name + "]");
                server.runCommand("tp @a 0.5 -60 -3.5 0 0");
                context.waitTicks(10);
                int id = server.computeOnServer(s -> {
                    VehicleEntity v = type.create(s.overworld(), EntitySpawnReason.COMMAND);
                    v.snapTo(0.5D, G, 2.5D, 0.0F, 0.0F);
                    v.addEnergy(v.maxVehicleEnergy());
                    s.overworld().addFreshEntity(v);
                    return v.getId();
                });
                context.waitTicks(5);
                boolean aboard = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(s.overworld().getEntity(id), true, true));
                if (!aboard) throw new AssertionError(name + ": the pilot could not board");
                context.runOnClient(mc -> {
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.options.hideGui = false;
                    mc.player.setXRot(0.0F);
                });
                context.waitTicks(60);
                context.takeScreenshot("cockpit_" + name + "_pilot_hud");
                if (name.equals("ah6")) {
                    // The instrument panel, looking down at it (free look, C; Zoomify's zoom on the same key in the
                    // pack): on the ground with the rotor still, then in the air.
                    lookAtPanel(context, true);
                    context.takeScreenshot("cockpit_ah6_panel_ground");
                    lookAtPanel(context, false);
                    server.runCommand("ride @p dismount");
                    server.runOnServer(s -> {
                        var v = s.overworld().getEntity(id);
                        v.setPos(0.5D, G + 30.0D, 2.5D);
                    });
                    context.waitTicks(5);
                    server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(s.overworld().getEntity(id), true, true));
                    context.runOnClient(mc -> {
                        mc.options.setCameraType(CameraType.FIRST_PERSON);
                        mc.player.setXRot(0.0F);
                    });
                    context.waitTicks(20);
                    context.getInput().holdKey(options -> options.keyUp);
                    context.getInput().holdKey(options -> options.keyLeft);
                    context.waitTicks(40);
                    context.takeScreenshot("cockpit_ah6_flight_view");
                    lookAtPanel(context, true);
                    context.takeScreenshot("cockpit_ah6_panel_flight");
                    server.runCommand("time set midnight");
                    context.waitTicks(10);
                    context.takeScreenshot("cockpit_ah6_panel_night");
                    lookAtPanel(context, false);
                    context.takeScreenshot("cockpit_ah6_flight_view_night");
                    context.getInput().releaseKey(options -> options.keyUp);
                    context.getInput().releaseKey(options -> options.keyLeft);
                    String state = server.computeOnServer(s -> {
                        VehicleEntity v = (VehicleEntity) s.overworld().getEntity(id);
                        return String.format("y %.1f, speed %.2f b/t, rotor %.3f, yaw %.0f, bank %.0f", v.getY(),
                                v.getDeltaMovement().horizontalDistance(), v.propellerSpeed(), v.getYRot(), v.poseAngles(1.0F).z);
                    });
                    LOG.info("[cockpit-views] ah6 in the air: {}", state);
                    server.runCommand("time set noon");
                }
                server.runCommand("ride @p dismount");
                server.runCommand("kill @e[type=jeg:" + name + "]");
            }

            // The bush plane at night, a crewed one with its lamps on.
            server.runCommand("time set midnight");
            server.runCommand("gamemode spectator @a");
            int plane = server.computeOnServer(s -> {
                VehicleEntity v = ModEntities.TOM6.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                v.snapTo(0.5D, G, 2.5D, 0.0F, 0.0F);
                v.addEnergy(v.maxVehicleEnergy());
                s.overworld().addFreshEntity(v);
                var pilot = EntityType.HUSK.create(s.overworld(), EntitySpawnReason.COMMAND);
                pilot.setNoAi(true);
                pilot.setSilent(true);
                pilot.snapTo(0.5D, G, 2.5D, 0.0F, 0.0F);
                s.overworld().addFreshEntity(pilot);
                pilot.addTag(ttv.migami.jeg.vehicle.ai.EnemyVehicleController.ENEMY_VEHICLE_CREW_TAG);
                pilot.startRiding(v, true, true);
                return v.getId();
            });
            context.runOnClient(mc -> mc.options.hideGui = true);
            context.waitTicks(60);
            String[][] views = {{"6.5 -58.5 12.5", "lights_tom6_ahead"}, {"0.5 -58 -9.5", "lights_tom6_behind"},
                    {"-9.5 -58.5 3.5", "lights_tom6_side"}};
            for (String[] view : views) {
                server.runCommand("tp @a " + view[0] + " facing 0.5 -59 2.5");
                context.waitTicks(15);
                context.takeScreenshot(view[1]);
            }
            context.runOnClient(mc -> mc.options.hideGui = false);
            LOG.info("[cockpit-views] COMPLETE (bush plane {})", plane);
        }
    }
}
