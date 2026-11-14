package ttv.migami.jeg.gametest;

import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;

/**
 * The A-10's speed brakes in a dive. The jet is held high over flat ground in a 35-degree dive (its position pinned, its
 * speed its own): left alone it runs away towards its dive speed; with reverse held (which also closes the throttle) its
 * split wingtip ailerons open and it settles at the braked limit instead, slow enough to hold a strafing run. Screenshots: the brakes
 * open, from behind and from the side, and shut. Fails unless the braked dive ends well under the free one and near the
 * braked limit, with the brakes out, and they close again when reverse is let go.
 */
public class A10DiveBrakeClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final Vec3 SPOT = new Vec3(0.5D, 40.0D, 0.5D);
    private static final float DIVE = 35.0F;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 44 -14 0 0");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
            });
            context.waitTicks(10);
            String id = server.computeOnServer(minecraftServer -> {
                var level = minecraftServer.overworld();
                VehicleEntity jet = ModEntities.A10.get().create(level, EntitySpawnReason.COMMAND);
                jet.snapTo(SPOT.x, SPOT.y, SPOT.z, 0.0F, DIVE);
                level.addFreshEntity(jet);
                var pilot = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
                pilot.setNoAi(true);
                pilot.setSilent(true);
                pilot.snapTo(SPOT.x, SPOT.y, SPOT.z, 0.0F, 0.0F);
                level.addFreshEntity(pilot);
                pilot.startRiding(jet, true, true);
                jet.addEnergy(jet.maxVehicleEnergy());
                return jet.getUUID().toString();
            });
            // Engines up, then into the dive at cruising speed.
            this.dive(context, server, id, 160, false, 1.4D);
            double free = this.dive(context, server, id, 120, false, -1.0D);
            this.look(server, "^ ^2 ^-14", "^ ^ ^");
            context.takeScreenshot("a10_dive_brake_shut");

            this.dive(context, server, id, 1, false, 2.4D);
            double braked = this.dive(context, server, id, 120, true, -1.0D);
            float open = context.computeOnClient(mc -> nearest(mc).jetSpeedBrake(1.0F));
            this.look(server, "^ ^2.5 ^-14", "^ ^ ^");
            this.dive(context, server, id, 3, true, -1.0D);
            context.takeScreenshot("a10_dive_brake_open_behind");
            this.look(server, "^-11 ^1 ^-3", "^ ^ ^");
            this.dive(context, server, id, 3, true, -1.0D);
            context.takeScreenshot("a10_dive_brake_open_side");
            // The left wingtip (facing south, the jet's left is +X), from above behind it and from below.
            this.look(server, "^9 ^5 ^-6", "^6.5 ^0.5 ^1");
            this.dive(context, server, id, 3, true, -1.0D);
            context.takeScreenshot("a10_dive_brake_open_wingtip");
            this.look(server, "^9 ^-4 ^-2", "^6.5 ^0.5 ^1");
            this.dive(context, server, id, 3, true, -1.0D);
            context.takeScreenshot("a10_dive_brake_open_wingtip_below");

            this.dive(context, server, id, 30, false, -1.0D);
            float shut = context.computeOnClient(mc -> nearest(mc).jetSpeedBrake(1.0F));
            this.look(server, "^9 ^5 ^-6", "^6.5 ^0.5 ^1");
            this.dive(context, server, id, 3, false, -1.0D);
            context.takeScreenshot("a10_dive_brake_shut_wingtip");

            double limit = Math.max(1.062D * 1.25D, 2.778D * 0.55D);
            LOGGER.warn("a10 dive brake: free dive {} b/t ({} km/h), braked {} b/t ({} km/h, limit {}), brakes {} open, {} after letting go",
                    String.format("%.3f", free), Math.round(free * 72.0D), String.format("%.3f", braked), Math.round(braked * 72.0D),
                    String.format("%.3f", limit), open, shut);
            StringBuilder failures = new StringBuilder();
            if (open < 0.95F) {
                failures.append("the speed brakes did not open (").append(open).append(")").append(System.lineSeparator());
            }
            if (shut > 0.05F) {
                failures.append("the speed brakes did not close after letting go (").append(shut).append(")").append(System.lineSeparator());
            }
            if (braked > free * 0.8D || braked > limit * 1.15D) {
                failures.append(String.format("the braked dive ran to %.3f b/t (free dive %.3f, limit %.3f)", braked, free, limit))
                        .append(System.lineSeparator());
            }
            if (!failures.isEmpty()) {
                throw new AssertionError(failures.toString());
            }
        } finally {
            context.runOnClient(mc -> mc.options.hideGui = false);
        }
    }

    /**
     * Holds the jet at its spot in the dive for some ticks, throttle closed, reverse held or not, then returns its speed.
     * A {@code start} speed (blocks a tick) above 0 sets it diving at that speed first (with the throttle open while
     * starting the engines).
     */
    private double dive(ClientGameTestContext context, TestServerContext server, String id, int ticks, boolean reverse,
                        double start) {
        double[] speed = new double[1];
        for (int tick = 0; tick < ticks; tick++) {
            boolean first = tick == 0;
            server.runOnServer(minecraftServer -> {
                if (!(minecraftServer.overworld().getEntity(UUID.fromString(id)) instanceof VehicleEntity jet)) {
                    return;
                }
                jet.setAiVehicleInput(new VehicleInput(start > 1.0D && ticks > 1, reverse, false, false, false, false, false,
                        false, false, false, false, false, -1, false, false, 0.0F, 0.0F));
                jet.setYRot(0.0F);
                jet.setXRot(DIVE);
                if (first && start > 0.0D) {
                    // Along the nose: facing south (+Z), diving.
                    float pitch = DIVE * Mth.DEG_TO_RAD;
                    jet.setDeltaMovement(0.0D, -Math.sin(pitch) * start, Math.cos(pitch) * start);
                }
                jet.setPos(SPOT);
                speed[0] = jet.getDeltaMovement().length();
            });
            context.waitTick();
        }
        return speed[0];
    }

    private void look(TestServerContext server, String from, String at) {
        server.runCommand("execute positioned " + SPOT.x + " " + SPOT.y + " " + SPOT.z + " rotated 0 0 run tp @p " + from
                + " facing " + at);
    }

    private static VehicleEntity nearest(net.minecraft.client.Minecraft mc) {
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
