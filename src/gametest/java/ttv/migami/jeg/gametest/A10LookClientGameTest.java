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
 * The A-10's texture and animation upgrade (the user, 2026-11-19), parked: its new finish (Compass Ghost greys, panel
 * lines, gun gas on the nose, exhaust staining on the tail, the "DM" tail code) from the front and rear quarters with its
 * canopy open (nobody aboard), then the canopy closing once a pilot climbs in. Screenshots; fails only if the jet can't
 * be made or boarded. The speed brakes and control surfaces are seen in A10DiveBrake.
 */
public class A10LookClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("time set 2000");
            server.runCommand("gamemode spectator @a");
            server.runCommand("forceload add -32 -32 32 32");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
            });
            // Facing south (yaw 0): its nose is at +Z.
            int id = server.computeOnServer(s -> {
                VehicleEntity jet = ModEntities.A10.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                jet.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
                s.overworld().addFreshEntity(jet);
                return jet.getId();
            });
            context.waitTicks(40);
            look(context, server, "8.5 -56.5 13.5", "0.5 -57.5 4.5", "a10_look_front_canopy_open");
            look(context, server, "-4.5 -55.5 10.5", "0.5 -57 5", "a10_look_cockpit_open");
            look(context, server, "-11.5 -55 -15.5", "0.5 -58 -3.5", "a10_look_rear");
            look(context, server, "10.5 -57 -13.5", "3.2 -56.5 -6.8", "a10_look_tail_code");
            boolean boarded = server.computeOnServer(s -> {
                var pilot = EntityType.HUSK.create(s.overworld(), EntitySpawnReason.COMMAND);
                pilot.setNoAi(true);
                pilot.setSilent(true);
                pilot.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
                s.overworld().addFreshEntity(pilot);
                pilot.addTag(ttv.migami.jeg.vehicle.ai.EnemyVehicleController.ENEMY_VEHICLE_CREW_TAG);
                return pilot.startRiding(s.overworld().getEntity(id), true, true);
            });
            if (!boarded) throw new AssertionError("the pilot could not board the A-10");
            context.waitTicks(60);
            look(context, server, "-4.5 -55.5 10.5", "0.5 -57 5", "a10_look_cockpit_shut");
            // The pilot's own view with the HUD on: no gun marker over the nose from the cockpit (the user's review,
            // 2026-11-19: "remove crosshair from A10 in first person"); the chase camera keeps it.
            server.runOnServer(s -> s.overworld().getEntity(id).getPassengers().forEach(p -> { p.stopRiding(); p.discard(); }));
            server.runCommand("gamemode creative @a");
            context.waitTicks(5);
            boolean flying = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(s.overworld().getEntity(id), true, true));
            if (!flying) throw new AssertionError("the player could not take the A-10's seat");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = false;
            });
            context.waitTicks(40);
            context.takeScreenshot("a10_look_pilot_first_person");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(20);
            context.takeScreenshot("a10_look_pilot_chase");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runCommand("ride @p dismount");
            LOG.info("[a10-look] COMPLETE");
        }
    }

    private static void look(ClientGameTestContext context, TestServerContext server, String from, String at, String name) {
        server.runCommand("tp @a " + from + " facing " + at);
        context.waitTicks(15);
        context.takeScreenshot(name);
    }
}
