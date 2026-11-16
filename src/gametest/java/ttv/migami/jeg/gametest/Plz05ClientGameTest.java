package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.entity.Plz05Entity;
import ttv.migami.jeg.vehicle.util.FireMissions;

/**
 * The PLZ-05 (design/plz05/BRIEF.md), what must not regress: the tracks run with the hull (both forward when it drives,
 * opposite ways when it pivots); the gun won't fire on the move; a fire mission 4 km off, with the fire control
 * engaged, lays the gun by itself and its rounds land on the target.
 */
public class Plz05ClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private static final int TARGET_Z = 4000;
    private int gunId = -1;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> mc.options.renderDistance().set(12));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitTicks(100);
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            server.runCommand("tp @a 4.5 -60 -6.5 0 0");
            this.gunId = server.computeOnServer(s -> {
                Plz05Entity gun = ModEntities.PLZ05.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                gun.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
                gun.vehicleInventory().setItem(0, new ItemStack(ModItems.SHELL_5IN_HE.get(), 16));
                s.overworld().addFreshEntity(gun);
                return gun.getId();
            });
            context.waitTicks(20);
            context.takeScreenshot("plz05_parked");

            // 1. The tracks run with the hull: 10 blocks forward, then a quarter turn on the spot.
            float[] before = context.computeOnClient(mc -> travel(mc.level.getEntity(this.gunId)));
            for (int tick = 0; tick < 20; tick++) {
                int step = tick + 1;
                server.runOnServer(s -> gun(s).snapTo(0.5D, G, 0.5D + step * 0.5D, 0.0F, 0.0F));
                context.waitTick();
            }
            context.waitTicks(5);
            float[] driven = context.computeOnClient(mc -> travel(mc.level.getEntity(this.gunId)));
            for (int tick = 0; tick < 18; tick++) {
                float yaw = (tick + 1) * 5.0F;
                server.runOnServer(s -> gun(s).snapTo(0.5D, G, 10.5D, yaw, 0.0F));
                context.waitTick();
            }
            context.waitTicks(5);
            float[] turned = context.computeOnClient(mc -> travel(mc.level.getEntity(this.gunId)));
            float forwardLeft = driven[0] - before[0], forwardRight = driven[1] - before[1];
            float turnLeft = turned[0] - driven[0], turnRight = turned[1] - driven[1];
            LOGGER.info("[plz05] tracks: 10 blocks forward ran {} / {} px; a right quarter turn ran {} / {} px", forwardLeft, forwardRight, turnLeft, turnRight);
            check(Math.abs(forwardLeft - 160.0F) < 8.0F && Math.abs(forwardRight - 160.0F) < 8.0F,
                    "driving 10 blocks forward must run both tracks 160 px forward: " + forwardLeft + ", " + forwardRight);
            // Turning right on the spot runs the left track forward and the right one back, by a quarter of the
            // circle the tracks' centres go round (half width 1.40 blocks: 35 px).
            check(turnLeft > 30.0F && turnRight < -30.0F && Math.abs(turnLeft + turnRight) < 3.0F,
                    "a pivot must counter-rotate the tracks: " + turnLeft + ", " + turnRight);
            server.runOnServer(s -> gun(s).snapTo(0.5D, G, 0.5D, 0.0F, 0.0F));
            context.waitTicks(10);

            // 2. The gunner's seat: board (the driver's seat), then change to the gunner's.
            server.runOnServer(s -> check(s.getPlayerList().getPlayers().get(0).startRiding(gun(s), true, true), "the crew must board"));
            context.waitTicks(10);
            context.getInput().pressKey(options -> KeyBindings.VEHICLE_CHANGE_SEAT);
            context.waitTicks(10);
            int seat = server.computeOnServer(s -> gun(s).seatOf(s.getPlayerList().getPlayers().get(0)));
            check(seat == Plz05Entity.GUNNER_SEAT, "the change-seat key must move the driver to the gunner's seat, is in " + seat);
            for (int wait = 0; wait < 200 && server.computeOnServer(s -> gun(s).vehicleWeaponSlotAmmo(0)) < 1; wait++) {
                context.waitTick();
            }

            // 3. On the move it won't fire.
            context.getInput().holdKey(options -> options.keyAttack);
            for (int tick = 0; tick < 10; tick++) {
                int step = tick + 1;
                server.runOnServer(s -> gun(s).snapTo(0.5D, G, 0.5D + step * 0.2D, 0.0F, 0.0F));
                context.waitTick();
            }
            context.getInput().releaseKey(options -> options.keyAttack);
            int shellsMoving = server.computeOnServer(Plz05ClientGameTest::countShells);
            boolean haltedMoving = server.computeOnServer(s -> gun(s).isHalted());
            LOGGER.info("[plz05] moving: halted {}, shells {}", haltedMoving, shellsMoving);
            check(!haltedMoving && shellsMoving == 0, "on the move the gun must not fire (halted " + haltedMoving + ", shells " + shellsMoving + ")");
            context.waitTicks(10);

            // 4. A fire mission 4 km south; charge 3, low arc (the default); the gunner engages the fire control and
            // looks away: the turret lays itself.
            server.runCommand("execute as @p run justEnoughGuns firemission 0 " + TARGET_Z);
            context.waitTicks(5);
            BlockPos target = server.computeOnServer(s -> gun(s).target());
            check(target != null, "the command must set the target");
            this.look(context, 90.0F, -5.0F);
            context.getInput().pressKey(options -> KeyBindings.VEHICLE_TOW);
            context.waitTicks(5);
            check(server.computeOnServer(s -> gun(s).fireControlEngaged()), "N must engage the fire control");
            Plz05Entity.Lay lay = server.computeOnServer(s -> gun(s).lay());
            LOGGER.info(String.format(Locale.ROOT, "[plz05] mission %s: range %.0f, yaw %.2f, elevation %.2f (%d ticks)",
                    target, lay.solution().range(), lay.worldYaw(), lay.elevation(), lay.ticks()));
            check(lay.reachable() && lay.elevation() > 20.0F && lay.elevation() < 45.0F,
                    "charge 3 must reach 4 km on a low arc between 20 and 45 degrees: " + lay.elevation());
            int laidAfter = -1;
            for (int tick = 0; tick < 200; tick++) {
                context.waitTick();
                if (server.computeOnServer(s -> gun(s).laidOnMission(1.0F))) {
                    laidAfter = tick + 1;
                    break;
                }
            }
            float elevation = server.computeOnServer(s -> gun(s).elevation(1.0F));
            LOGGER.info("[plz05] laid after {} ticks at {} deg (the gunner looks 90 deg off)", laidAfter, elevation);
            check(laidAfter > 0, "the fire control must lay the gun on the mission, elevation " + elevation);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(10);
            context.takeScreenshot("plz05_laid");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(3);
            context.takeScreenshot("plz05_panel");

            List<Vec3> impacts = new ArrayList<>();
            for (int round = 0; round < 3; round++) {
                for (int wait = 0; wait < 200 && server.computeOnServer(s -> gun(s).vehicleWeaponSlotAmmo(0)) < 1; wait++) {
                    context.waitTick();
                }
                context.getInput().holdKey(options -> options.keyAttack);
                context.waitTicks(3);
                context.getInput().releaseKey(options -> options.keyAttack);
                Vec3[] last = null;
                for (int tick = 0; tick < 800; tick++) {
                    context.waitTick();
                    Vec3[] now = server.computeOnServer(s -> {
                        for (var entity : s.overworld().getAllEntities()) {
                            if (entity.getType() == ModEntities.CANNON_SHELL.get()) {
                                return new Vec3[] {entity.position(), entity.getDeltaMovement()};
                            }
                        }
                        return null;
                    });
                    if (now == null && last != null) {
                        break;
                    }
                    if (now != null) {
                        last = now;
                    }
                }
                check(last != null, "round " + (round + 1) + " was not fired");
                Vec3 step = last[1].add(0.0D, -Plz05Entity.GRAVITY, 0.0D);
                double share = step.y >= 0.0D ? 1.0D : Mth.clamp((last[0].y - G) / -step.y, 0.0D, 1.0D);
                impacts.add(last[0].add(step.scale(share)));
            }
            Vec3 aim = FireMissions.aimPoint(target);
            double worst = 0.0D;
            for (Vec3 impact : impacts) {
                double miss = Math.sqrt(Mth.square(impact.x - aim.x) + Mth.square(impact.z - aim.z));
                worst = Math.max(worst, miss);
                LOGGER.info(String.format(Locale.ROOT, "[plz05] impact %.1f %.1f %.1f: %.1f blocks from the target", impact.x, impact.y, impact.z, miss));
            }
            // SBW's spread (0.06) scatters rounds a few blocks at 4 km; a wrong solution or lay misses by far more.
            check(worst < 40.0D, "every round must land within 1% of the range (40 blocks) of a target 4 km off, worst " + worst);
            LOGGER.info("[plz05] COMPLETE");
        } catch (Throwable failure) {
            LOGGER.error("[plz05] FAILED: {}", failure.getMessage());
            throw failure;
        }
    }

    private static float[] travel(net.minecraft.world.entity.Entity entity) {
        Plz05Entity gun = (Plz05Entity) entity;
        return new float[] {gun.trackTravel(true, 1.0F), gun.trackTravel(false, 1.0F)};
    }

    private static int countShells(MinecraftServer server) {
        int count = 0;
        for (var entity : server.overworld().getAllEntities()) {
            if (entity.getType() == ModEntities.CANNON_SHELL.get()) {
                count++;
            }
        }
        return count;
    }

    private void look(ClientGameTestContext context, float yaw, float pitch) {
        context.runOnClient(mc -> {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        });
        context.waitTicks(3);
    }

    private Plz05Entity gun(MinecraftServer server) {
        if (server.overworld().getEntity(this.gunId) instanceof Plz05Entity gun) {
            return gun;
        }
        throw new AssertionError("the PLZ-05 is gone");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
