package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * AH-6 and Mi-28 takeoffs and landings with a villager riding along: in the open and beside a three-block wall inside
 * the rotor disc (the blades pass over its top). Neither should chip the helicopter much or hurt the passenger. Then a run
 * at full speed, which should fly smoothly instead of the pilot's view snapping back to the server's position.
 */
public class HeliTakeoffSafetyClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        List<String> passengerHits = new ArrayList<>();
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (entity.getType() == EntityType.VILLAGER) {
                synchronized (passengerHits) {
                    passengerHits.add(String.format("%s %.2f", source.getMsgId(), damageTaken));
                }
            }
        });
        StringBuilder report = new StringBuilder("heli takeoff safety report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("justEnoughGuns config vehicle helicopter weightyFlight true");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));

            this.takeoffAndLand(context, server, report, failures, passengerHits, "ah6", "open", 0, false);
            this.takeoffAndLand(context, server, report, failures, passengerHits, "ah6", "wall", 200, true);
            this.takeoffAndLand(context, server, report, failures, passengerHits, "mi28", "open", 600, false);
            this.takeoffAndLand(context, server, report, failures, passengerHits, "mi28", "wall", 800, true);
            this.fullSpeed(context, server, report, failures, 400);
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    private void takeoffAndLand(ClientGameTestContext context, TestServerContext server, StringBuilder report,
                                StringBuilder failures, List<String> passengerHits, String id, String scene, int x, boolean wall) {
        String name = id + " " + scene;
        server.runCommand("ride @p dismount");
        server.runCommand("kill @e[type=minecraft:villager]");
        server.runCommand("tp @a " + x + " ~ 0 0 0");
        context.waitTicks(10);
        if (wall) {
            // Three blocks tall, 4 blocks to the side of the helicopter: inside the 5.5-block rotor disc, below the blades.
            server.runCommand("execute as @p at @s run fill ~4 ~ ~-2 ~4 ~2 ~10 minecraft:stone_bricks");
            server.runCommand("execute as @p at @s run fill ~-5 ~ ~-2 ~-5 ~2 ~10 minecraft:oak_log");
            // A post right under the tail boom, which the hull settles onto when it drifts back on landing. The Mi-28's
            // boom hangs lower, so its post is one block.
            String postTop = "mi28".equals(id) ? "~" : "~1";
            server.runCommand("execute as @p at @s run fill ~ ~ ~-1 ~ " + postTop + " ~ minecraft:cobblestone_wall");
        }
        server.runCommand("execute as @p at @s run summon jeg:" + id + " ~ ~ ~4 {Rotation:[0f,0f]}");
        server.runCommand("execute as @p at @s run summon minecraft:villager ~ ~ ~6 {NoAI:1b}");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=jeg:" + id + ",limit=1,sort=nearest]");
        server.runCommand("ride @e[type=minecraft:villager,limit=1,sort=nearest] mount @e[type=jeg:" + id + ",limit=1,sort=nearest]");
        context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
        context.waitTicks(10);
        synchronized (passengerHits) {
            passengerHits.clear();
        }
        float healthStart = this.heliHealth(server);
        float villagerStart = this.villagerHealth(server);
        boolean villagerAboard = server.computeOnServer(s -> this.villager(s) != null && this.villager(s).getVehicle() instanceof VehicleEntity);

        context.getInput().holdKey(options -> options.keyJump);
        int startup = context.computeOnClient(mc -> vehicle(mc).helicopterStartupTicks());
        context.waitTicks(startup + 50);
        context.getInput().releaseKey(options -> options.keyJump);
        context.waitTicks(10);
        double altitude = context.computeOnClient(mc -> vehicle(mc).getY() - mc.player.level().getMinY());
        float healthAfterTakeoff = this.heliHealth(server);
        float villagerAfterTakeoff = this.villagerHealth(server);
        context.takeScreenshot("heli_takeoff_" + id + "_" + scene);

        context.getInput().holdKey(options -> KeyBindings.VEHICLE_BRAKE_DESCEND);
        context.waitFor(mc -> vehicle(mc) != null && vehicle(mc).onGround(), 600);
        context.getInput().releaseKey(options -> KeyBindings.VEHICLE_BRAKE_DESCEND);
        context.waitTicks(20);
        float healthAfterLanding = this.heliHealth(server);
        float villagerAfterLanding = this.villagerHealth(server);

        String hits;
        synchronized (passengerHits) {
            hits = String.join(", ", passengerHits);
        }
        String landed = server.computeOnServer(s -> {
            for (var level : s.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof VehicleEntity vehicle && !vehicle.getPassengers().isEmpty()) {
                        return String.format("server onGround=%s y=%.2f pitch=%.1f roll=%.1f", vehicle.onGround(),
                                vehicle.getY() - level.getMinY(), vehicle.getXRot(), vehicle.roll());
                    }
                }
            }
            return "no vehicle";
        });

        // The pilot gets out of the landed helicopter: nothing should happen to it or to them.
        float playerBefore = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getHealth());
        server.runCommand("ride @p dismount");
        context.waitTicks(20);
        float playerAfter = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getHealth());
        String parked = server.computeOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            for (VehicleEntity vehicle : player.level().getEntitiesOfClass(VehicleEntity.class, player.getBoundingBox().inflate(16.0D))) {
                return String.format("parked health %.2f onGround=%s", vehicle.vehicleHealth(), vehicle.onGround());
            }
            return "helicopter gone";
        });
        String line = String.format("%s: villager aboard=%s, peak y=%.1f; heli %.2f -> %.2f (takeoff) -> %.2f (landing, %s); villager %.2f -> %.2f -> %.2f; passenger hits [%s]; pilot gets out: health %.2f -> %.2f, %s%n",
                name, villagerAboard, altitude, healthStart, healthAfterTakeoff, healthAfterLanding, landed,
                villagerStart, villagerAfterTakeoff, villagerAfterLanding, hits, playerBefore, playerAfter, parked);
        LOGGER.info("[heli-takeoff] {}", line.trim());
        report.append(line);
        if (playerAfter < playerBefore || parked.equals("helicopter gone")) {
            failures.append(String.format("%s: getting out of the landed helicopter: %s, pilot health %.2f -> %.2f%n", name, parked, playerBefore, playerAfter));
        }
        if (!villagerAboard) {
            failures.append(name).append(": villager never boarded\n");
        }
        if (healthStart - healthAfterLanding > 3.0F) {
            failures.append(String.format("%s: a gentle takeoff and landing cost the heli %.2f health%n", name, healthStart - healthAfterLanding));
        }
        if (villagerStart - villagerAfterLanding > 0.01F) {
            failures.append(String.format("%s: passenger lost %.2f health [%s]%n", name, villagerStart - villagerAfterLanding, hits));
        }
    }

    private void fullSpeed(ClientGameTestContext context, TestServerContext server, StringBuilder report,
                           StringBuilder failures, int x) {
        server.runCommand("ride @p dismount");
        server.runCommand("kill @e[type=minecraft:villager]");
        server.runCommand("tp @a " + x + " ~ 0 0 0");
        context.waitTicks(10);
        server.runCommand("execute as @p at @s run summon jeg:ah6 ~ ~ ~4 {Rotation:[0f,0f]}");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=jeg:ah6,limit=1,sort=nearest]");
        context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
        context.getInput().holdKey(options -> options.keyJump);
        int startup = context.computeOnClient(mc -> vehicle(mc).helicopterStartupTicks());
        context.waitTicks(startup + 120);
        context.getInput().releaseKey(options -> options.keyJump);
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(100);

        // At full speed every tick should advance by about the velocity; a snap shows as a step far off it.
        int snaps = 0;
        double maxSpeed = 0.0D;
        double worstStepError = 0.0D;
        Vec3 last = context.computeOnClient(mc -> vehicle(mc).position());
        for (int tick = 0; tick < 200; tick++) {
            if (tick == 100) {
                // Turn while flat out as well.
                context.getInput().moveCursor(25.0D, 0.0D);
            }
            if (tick > 100 && tick < 130) {
                context.getInput().moveCursor(25.0D, 0.0D);
            }
            context.waitTick();
            Vec3[] state = context.computeOnClient(mc -> new Vec3[]{vehicle(mc).position(), vehicle(mc).getDeltaMovement()});
            Vec3 step = state[0].subtract(last);
            double error = new Vec3(step.x - state[1].x, 0.0D, step.z - state[1].z).length();
            maxSpeed = Math.max(maxSpeed, state[1].horizontalDistance());
            worstStepError = Math.max(worstStepError, error);
            if (error > 0.75D) {
                snaps++;
            }
            last = state[0];
        }
        context.takeScreenshot("heli_full_speed");
        context.getInput().releaseKey(options -> options.keyUp);
        report.append(String.format("full speed: max speed %.2f b/t, snaps %d, worst step error %.2f%n", maxSpeed, snaps, worstStepError));
        if (snaps > 0) {
            failures.append(String.format("full speed: client position snapped %d times (worst %.2f blocks)%n", snaps, worstStepError));
        }
    }

    private float heliHealth(TestServerContext server) {
        return server.computeOnServer(s -> {
            for (var level : s.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof VehicleEntity vehicle && !vehicle.getPassengers().isEmpty()) {
                        return vehicle.vehicleHealth();
                    }
                }
            }
            return -1.0F;
        });
    }

    private float villagerHealth(TestServerContext server) {
        return server.computeOnServer(s -> {
            var villager = this.villager(s);
            return villager == null ? -1.0F : villager.getHealth();
        });
    }

    private net.minecraft.world.entity.LivingEntity villager(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.getType() == EntityType.VILLAGER && entity.isAlive()) {
                    return (net.minecraft.world.entity.LivingEntity) entity;
                }
            }
        }
        return null;
    }

    private static VehicleEntity vehicle(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof VehicleEntity vehicle ? vehicle : null;
    }
}
