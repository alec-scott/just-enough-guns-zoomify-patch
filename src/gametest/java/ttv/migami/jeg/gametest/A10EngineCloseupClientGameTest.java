package ttv.migami.jeg.gametest;

import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;

/**
 * A close look at the A-10's engines. A parked jet, held in place, is run from idle to full power by a husk at the
 * controls, while the player stands behind it looking into the exhausts. Screenshots: idle, full power (two frames a few
 * ticks apart, for the fan blur), side on (the glow fading), from further back, and at night. Fails unless the fans reach
 * blurring speed at full power.
 */
public class A10EngineCloseupClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final Vec3 PARKED = new Vec3(0.5D, -60.0D, 20.5D);

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 -60 0 0 0");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
            });
            context.waitTicks(10);
            String jetId = server.computeOnServer(minecraftServer -> {
                var level = minecraftServer.overworld();
                VehicleEntity jet = ModEntities.A10.get().create(level, EntitySpawnReason.COMMAND);
                jet.snapTo(PARKED.x, PARKED.y, PARKED.z, 0.0F, 0.0F);
                level.addFreshEntity(jet);
                var pilot = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
                pilot.setNoAi(true);
                pilot.setSilent(true);
                pilot.snapTo(jet.getX(), jet.getY(), jet.getZ(), 0.0F, 0.0F);
                level.addFreshEntity(pilot);
                pilot.startRiding(jet, true, true);
                return jet.getUUID().toString();
            });
            // Idle: no throttle until the engines have wound up.
            this.hold(context, server, jetId, false, 200);
            this.lookIntoExhausts(context, server, "^-2.5 ^2.2 ^-9.5");
            context.takeScreenshot("a10_engines_idle");

            this.hold(context, server, jetId, true, 160);
            float spool = context.computeOnClient(mc -> nearestJet(mc).jetSpool());
            float rate = context.computeOnClient(mc -> nearestJet(mc).jetFanRate());
            this.lookIntoExhausts(context, server, "^-2.5 ^2.2 ^-9.5");
            context.takeScreenshot("a10_engines_full_a");
            this.hold(context, server, jetId, true, 3);
            context.takeScreenshot("a10_engines_full_b");
            this.lookIntoExhausts(context, server, "^-9 ^2.5 ^-5");
            context.takeScreenshot("a10_engines_full_side");
            this.lookIntoExhausts(context, server, "^ ^3 ^-22");
            context.takeScreenshot("a10_engines_full_far");
            // The intake fan, from in front of the left engine: a blurred disc, not blades turning backwards.
            this.lookAt(context, server, "^4 ^3.6 ^7", "^1.65 ^3.2 ^2.2");
            context.takeScreenshot("a10_engines_full_intake_a");
            this.hold(context, server, jetId, true, 2);
            context.takeScreenshot("a10_engines_full_intake_b");
            server.runCommand("time set midnight");
            this.hold(context, server, jetId, true, 5);
            this.lookIntoExhausts(context, server, "^-2.5 ^2.2 ^-9.5");
            context.takeScreenshot("a10_engines_full_night");
            server.runCommand("time set noon");
            LOGGER.info("a10 engine close-up: full power spool {}, fans {} degrees a tick", spool, rate);
            if (spool < 0.9F || rate < 60.0F) {
                throw new AssertionError("the engines did not reach full power (spool " + spool + ", fans " + rate + " degrees a tick)");
            }
        } finally {
            context.runOnClient(mc -> mc.options.hideGui = false);
        }
    }

    /** Holds the parked jet where it is for some ticks, its pilot at full throttle or not touching it. */
    private void hold(ClientGameTestContext context, TestServerContext server, String jetId, boolean fullThrottle, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            server.runOnServer(minecraftServer -> {
                if (minecraftServer.overworld().getEntity(UUID.fromString(jetId)) instanceof VehicleEntity jet) {
                    jet.setAiVehicleInput(new VehicleInput(fullThrottle, false, false, false, false, false, false, false, false, false,
                            false, false, -1, false, false, 0.0F, 0.0F));
                    jet.setDeltaMovement(Vec3.ZERO);
                    jet.setPos(PARKED);
                }
            });
            context.waitTick();
        }
    }

    private void lookIntoExhausts(ClientGameTestContext context, TestServerContext server, String from) {
        this.lookAt(context, server, from, "^ ^3.4 ^-4.5");
    }

    /** Relative to the jet: standing at `from`, looking at `at`. */
    private void lookAt(ClientGameTestContext context, TestServerContext server, String from, String at) {
        server.runCommand("execute as @p at @e[type=jeg:a10,limit=1,sort=nearest] run tp @s " + from + " facing " + at);
        context.waitTicks(3);
    }

    private static VehicleEntity nearestJet(Minecraft mc) {
        VehicleEntity best = null;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof VehicleEntity vehicle && vehicle.isJet()
                    && (best == null || mc.player.distanceTo(vehicle) < mc.player.distanceTo(best))) {
                best = vehicle;
            }
        }
        return best;
    }
}
