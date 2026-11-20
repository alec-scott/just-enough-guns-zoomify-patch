package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
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
import ttv.migami.jeg.vehicle.client.VehicleFeel;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Vehicles feel their weight (the user's rule of 2026-11-17), compared on the 10-tonne LAV-150 and the 55-tonne YX-100
 * driven flat out from rest along a road and braked:
 * <ol>
 *   <li>the tank takes far longer to wind up and to stop, and each still reaches its own top speed;</li>
 *   <li>the hulls squat pulling away (nose up) and dive braking (nose down);</li>
 *   <li>the brakes are heard on the hard stop, the turret's drive while it slews, and a knock when a hull lands from a
 *   drop.</li>
 * </ol>
 */
public class VehicleWeightClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private int vehicleId = -1;

    private record Run(String id, int to80, double top, int stopTicks, float squat, float dive, List<String> sounds) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("vehicle weight report\n");
        StringBuilder failures = new StringBuilder();
        context.runOnClient(mc -> mc.options.renderDistance().set(10));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender(false, 2400);
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            server.runCommand("forceload add -16 -400 16 16");
            context.waitTicks(20);
            List<Run> runs = new ArrayList<>();
            for (String id : new String[]{"lav150", "truck", "yx100"}) {
                runs.add(this.drive(context, server, id));
            }
            for (Run r : runs) {
                report.append(String.format("%s: 80%% of top in %d ticks, top %.3f blocks/tick; stopped in %d ticks; squat %.2f deg, dive %.2f deg; sounds %s%n",
                        r.id(), r.to80(), r.top(), r.stopTicks(), r.squat(), r.dive(), r.sounds()));
            }
            Run lav = runs.get(0), truck = runs.get(1), tank = runs.get(2);
            // The FSV-6 (15 t) is heavier than the LAV-150 (9.9 t), far lighter than the tank, and rolls on six tyres.
            if (truck.to80() < lav.to80()) failures.append("the FSV-6 winds up faster than the LAV-150\n");
            if (truck.to80() > tank.to80()) failures.append("the FSV-6 winds up slower than the tank\n");
            if (this.truckTyres < 0.02F) failures.append("the FSV-6's tyres were not heard at speed (").append(this.truckTyres).append(")\n");
            report.append(String.format("truck tyres at speed %.3f%n", this.truckTyres));
            if (tank.to80() < lav.to80() * 1.6D) failures.append("the tank winds up nearly as fast as the LAV-150\n");
            if (tank.stopTicks() < lav.stopTicks() * 1.4D) failures.append("the tank stops nearly as short as the LAV-150\n");
            for (Run r : runs) {
                if (r.squat() > -0.3F) failures.append(r.id()).append(" did not squat pulling away\n");
                if (r.dive() < 0.3F) failures.append(r.id()).append(" did not dive braking\n");
                if (r.sounds().stream().noneMatch(s -> s.startsWith("brake"))) failures.append(r.id()).append(": no brakes heard\n");
            }

            // A hull dropped 2.5 blocks lands with a knock.
            int dropped = server.computeOnServer(s -> {
                var player = s.getPlayerList().getPlayers().get(0);
                VehicleEntity v = ModEntities.LAV150.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                v.snapTo(player.getX() + 8.0D, -57.5D, player.getZ(), 0.0F, 0.0F);
                s.overworld().addFreshEntity(v);
                return v.getId();
            });
            context.runOnClient(mc -> VehicleFeel.drainEvents());
            context.waitTicks(40);
            List<String> landing = context.computeOnClient(mc -> VehicleFeel.drainEvents());
            report.append("dropped LAV-150: ").append(landing).append('\n');
            if (!landing.contains(dropped + ":bump")) failures.append("no knock when a hull landed from a drop\n");

            // A parked YX-100's turret slewed a quarter turn: its electric drive is heard while it turns. (A driver's pinned
            // turret camera follows the raw mouse, which a test can't move.)
            int parked = server.computeOnServer(s -> {
                var player = s.getPlayerList().getPlayers().get(0);
                VehicleEntity v = ModEntities.YX100.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                v.snapTo(player.getX() - 8.0D, -60.0D, player.getZ(), 0.0F, 0.0F);
                s.overworld().addFreshEntity(v);
                return v.getId();
            });
            context.waitTicks(20);
            context.runOnClient(mc -> VehicleFeel.drainEvents());
            server.runOnServer(s -> ((VehicleEntity) s.overworld().getEntity(parked)).setAiTurretAim(90.0F, 0.0F));
            context.waitTicks(60);
            List<String> slew = context.computeOnClient(mc -> VehicleFeel.drainEvents());
            report.append("parked YX-100 slewed: ").append(slew).append('\n');
            if (!slew.contains(parked + ":traverse")) failures.append("the turret's drive was not heard while it slewed\n");
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            LOGGER.error("[vehicle-weight] FAILED: {}", failures);
            throw new AssertionError(failures.toString() + report);
        }
        LOGGER.info("[vehicle-weight] COMPLETE");
    }

    private Run drive(ClientGameTestContext context, TestServerContext server, String id) {
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().get(0);
            player.stopRiding();
            if (this.vehicleId >= 0 && s.overworld().getEntity(this.vehicleId) != null) s.overworld().getEntity(this.vehicleId).discard();
            EntityType<? extends VehicleEntity> type = id.equals("lav150") ? ModEntities.LAV150.get()
                    : id.equals("truck") ? ModEntities.TRUCK.get() : ModEntities.YX100.get();
            VehicleEntity v = type.create(s.overworld(), EntitySpawnReason.COMMAND);
            v.snapTo(0.5D, -60.0D, 0.5D, 180.0F, 0.0F);
            v.addEnergy(v.maxVehicleEnergy());
            s.overworld().addFreshEntity(v);
            this.vehicleId = v.getId();
            player.teleportTo(0.5D, -60.0D, 4.5D);
        });
        context.waitTicks(20);
        server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(this.vehicle(s), true, true));
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(100);
        context.runOnClient(mc -> VehicleFeel.drainEvents());
        // Slew the turret a quarter turn by looking aside, then back ahead.
        // As the mouse does (a turret camera pinned to the gun follows the mouse, not the player's yaw).
        for (int i = 0; i < 20; i++) {
            context.runOnClient(mc -> mc.player.turn(30.0D, 0.0D));
            context.waitTick();
        }
        context.waitTicks(30);
        for (int i = 0; i < 20; i++) {
            context.runOnClient(mc -> mc.player.turn(-30.0D, 0.0D));
            context.waitTick();
        }
        context.waitTicks(40);
        double top = 0.0D;
        double[] trace = new double[400];
        float squat = 0.0F;
        context.getInput().holdKey(options -> options.keyUp);
        for (int tick = 0; tick < 400; tick++) {
            context.waitTick();
            trace[tick] = server.computeOnServer(s -> {
                VehicleEntity v = this.vehicle(s);
                return Math.hypot(v.getX() - v.xo, v.getZ() - v.zo);
            });
            top = Math.max(top, trace[tick]);
            if (tick < 40) squat = Math.min(squat, context.computeOnClient(mc -> this.clientPitch(mc)));
            if (id.equals("truck") && tick == 300) {
                this.truckTyres = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.SupportAudio.loopVolume(this.vehicleId, "tyres"));
            }
        }
        context.getInput().releaseKey(options -> options.keyUp);
        int to80 = -1;
        for (int tick = 0; tick < 400; tick++) {
            if (trace[tick] >= top * 0.8D) {
                to80 = tick;
                break;
            }
        }
        context.getInput().holdKey(options -> options.keyDown);
        int stopTicks = -1;
        float dive = 0.0F;
        for (int tick = 0; tick < 300; tick++) {
            context.waitTick();
            dive = Math.max(dive, context.computeOnClient(mc -> this.clientPitch(mc)));
            double speed = server.computeOnServer(s -> {
                VehicleEntity v = this.vehicle(s);
                return Math.hypot(v.getDeltaMovement().x, v.getDeltaMovement().z);
            });
            if (speed < 0.02D && stopTicks < 0) {
                stopTicks = tick;
            }
            if (stopTicks >= 0 && tick > stopTicks + 20) break;
        }
        context.getInput().releaseKey(options -> options.keyDown);
        context.takeScreenshot("weight_" + id + "_stopped");
        List<String> sounds = context.computeOnClient(mc -> VehicleFeel.drainEvents()).stream()
                .map(e -> e.substring(e.indexOf(':') + 1)).distinct().toList();
        return new Run(id, to80, top, stopTicks, squat, dive, sounds);
    }

    private float truckTyres;

    private VehicleEntity vehicle(MinecraftServer server) {
        return (VehicleEntity) server.overworld().getEntity(this.vehicleId);
    }

    private float clientPitch(net.minecraft.client.Minecraft mc) {
        VehicleEntity v = this.clientVehicle(mc);
        return v == null ? 0.0F : VehicleFeel.pitch(v, 1.0F);
    }

    private VehicleEntity clientVehicle(net.minecraft.client.Minecraft mc) {
        return mc.player.getVehicle() instanceof VehicleEntity v ? v : null;
    }
}
