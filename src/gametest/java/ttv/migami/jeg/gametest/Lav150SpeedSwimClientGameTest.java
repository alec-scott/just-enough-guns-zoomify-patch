package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The LAV-150 (fleet plan section 3), what must not regress:
 * <ol>
 *   <li>Speed: flat out on a road it reaches about 72 km/h (1.0 blocks a tick; 88 until the user found it too fast, 2026-11-18), and the brakes bring it to a stop.</li>
 *   <li>Swimming: off a bank into a river 4 deep, it floats, makes way at about 5 km/h (0.069) and climbs out up the
 *   shelving far side.</li>
 *   <li>The hatch: a rifleman in seat 5 stands in the turret's left hatch, rides the turret round, and is drawn.</li>
 * </ol>
 */
public class Lav150SpeedSwimClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private int lavId = -1;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("LAV-150 speed and swim report\n");
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
            server.runCommand("forceload add -16 -330 16 96");
            // A straight road west of the river, running north (negative z) for 300 blocks.
            server.runCommand("fill -12 -61 -320 12 -59 20 minecraft:dirt");
            server.runCommand("fill -12 -63 21 12 -60 50 minecraft:water");
            server.runCommand("fill -12 -61 51 12 -59 85 minecraft:dirt");
            server.runCommand("fill -12 -63 44 12 -62 50 minecraft:dirt");
            server.runCommand("fill -12 -61 47 12 -61 50 minecraft:dirt");
            server.runCommand("fill -12 -60 49 12 -60 50 minecraft:dirt");
            server.runCommand("tp @a 4.5 -58 6 0 0");
            context.waitTicks(20);
            this.lavId = server.computeOnServer(s -> {
                VehicleEntity lav = ModEntities.LAV150.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                lav.snapTo(0.5D, -58.0D, 10.5D, 180.0F, 0.0F);
                lav.addEnergy(lav.maxVehicleEnergy());
                s.overworld().addFreshEntity(lav);
                return lav.getId();
            });
            context.waitTicks(20);
            server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(this.lav(s), true, true));
            context.waitTicks(70);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));

            // 1. Flat out north along the road, then brake.
            context.getInput().holdKey(options -> options.keyUp);
            double top = 0.0D;
            for (int tick = 0; tick < 200; tick++) {
                context.waitTick();
                top = Math.max(top, server.computeOnServer(s -> {
                    VehicleEntity v = this.lav(s);
                    return Math.hypot(v.getX() - v.xo, v.getZ() - v.zo);
                }));
            }
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().holdKey(options -> options.keyDown);
            int stopTicks = -1;
            for (int tick = 0; tick < 120; tick++) {
                context.waitTick();
                double speed = server.computeOnServer(s -> {
                    VehicleEntity v = this.lav(s);
                    Vec3 d = v.getDeltaMovement();
                    return Math.hypot(d.x, d.z);
                });
                if (speed < 0.02D) {
                    stopTicks = tick;
                    break;
                }
            }
            context.getInput().releaseKey(options -> options.keyDown);
            report.append(String.format("road: top %.3f blocks/tick (%.1f km/h); stopped %d ticks after braking%n", top, top * 72.0D, stopTicks));
            if (top < 0.92D || top > 1.08D) failures.append(String.format("top speed %.3f, not about 1.0 (72 km/h)%n", top));
            if (stopTicks < 0) failures.append("the brakes did not stop it within 6 s\n");

            // 3. The hatch rider, while it is parked.
            int rider = server.computeOnServer(s -> {
                var villager = EntityType.VILLAGER.create(s.overworld(), EntitySpawnReason.COMMAND);
                VehicleEntity v = this.lav(s);
                villager.snapTo(v.getX(), v.getY(), v.getZ(), 0.0F, 0.0F);
                s.overworld().addFreshEntity(villager);
                villager.startRiding(v, true, true);
                return villager.getId();
            });
            context.waitTicks(10);
            server.runOnServer(s -> {
                // Put the rider in the hatch seat and swing the turret a quarter turn.
                VehicleEntity v = this.lav(s);
                v.rememberSeatAssignment(s.overworld().getEntity(rider), 5);
                v.setAiTurretAim(90.0F, 0.0F);
            });
            context.waitTicks(40);
            double[] hatch = server.computeOnServer(s -> {
                VehicleEntity v = this.lav(s);
                var r = s.overworld().getEntity(rider);
                Vec3 local = r.position().subtract(v.position());
                return new double[]{v.seatOf(r), local.y, v.turretYaw(), local.x, local.z};
            });
            context.takeScreenshot("lav150_hatch_rider");
            report.append(String.format("hatch rider: seat %d, %.2f up, turret %.0f deg, offset (%.2f, %.2f)%n", (int) hatch[0], hatch[1], hatch[2], hatch[3], hatch[4]));
            if ((int) hatch[0] != 5) failures.append("the rider did not take the hatch seat\n");
            if (hatch[1] < 1.4D) failures.append("the hatch rider sits inside the hull, not in the hatch\n");
            server.runOnServer(s -> s.overworld().getEntity(rider).discard());

            // 2. Back south into the river and across.
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().get(0);
                player.stopRiding();
                this.lav(s).discard();
                VehicleEntity lav = ModEntities.LAV150.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                lav.snapTo(0.5D, -58.0D, 10.5D, 0.0F, 0.0F);
                lav.addEnergy(lav.maxVehicleEnergy());
                s.overworld().addFreshEntity(lav);
                this.lavId = lav.getId();
            });
            context.waitTicks(20);
            server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(this.lav(s), true, true));
            context.waitTicks(60);
            context.getInput().holdKey(options -> options.keyUp);
            double enterZ = Double.NaN, lastZ = 0.0D;
            int afloatTicks = 0;
            boolean out = false;
            for (int tick = 0; tick < 1200 && !out; tick++) {
                context.waitTick();
                double[] s = server.computeOnServer(sv -> {
                    VehicleEntity v = this.lav(sv);
                    return new double[]{v.getZ(), v.amphibiousAfloat() ? 1 : 0, v.onGround() ? 1 : 0};
                });
                if (s[1] > 0 && s[0] > 33 && s[0] < 40) {
                    if (Double.isNaN(enterZ)) enterZ = s[0];
                    afloatTicks++;
                    lastZ = s[0];
                    if (afloatTicks == 20) context.takeScreenshot("lav150_swimming");
                }
                out = s[0] > 58 && s[2] > 0;
            }
            context.getInput().releaseKey(options -> options.keyUp);
            double swim = afloatTicks == 0 ? 0.0D : (lastZ - enterZ) / afloatTicks;
            report.append(String.format("river: afloat %d ticks at %.3f blocks/tick (%.1f km/h); out on the far bank %s%n", afloatTicks, swim, swim * 72.0D, out));
            if (afloatTicks < 15) failures.append("it never swam\n");
            if (swim < 0.05D || swim > 0.08D) failures.append(String.format("swam at %.3f, not about 0.069%n", swim));
            if (!out) failures.append("it did not climb out up the far bank\n");
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            LOGGER.error("[lav150-speed-swim] FAILED: {}", failures);
            throw new AssertionError(failures.toString() + report);
        }
        LOGGER.info("[lav150-speed-swim] COMPLETE");
    }

    private VehicleEntity lav(MinecraftServer server) {
        return (VehicleEntity) server.overworld().getEntity(this.lavId);
    }
}
