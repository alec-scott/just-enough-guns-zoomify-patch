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
import ttv.migami.jeg.vehicle.entity.HowitzerEntity;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.util.FireSolution;
import ttv.migami.jeg.vehicle.util.HowitzerRig;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * The 105 mm howitzer (design/howitzer/BRIEF.md), what must not regress: packed it neither fires nor lays; the tow key
 * deploys it in 100 ticks; a fire mission by coordinates 2 km off, laid on the panel's numbers at charge 3, lands its
 * rounds on the target; packed again, the FSV-6 tows it trail first, its lunette on the pintle.
 */
public class HowitzerClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private static final int TARGET_Z = 2000;
    private int gunId = -1;
    private int truckId = -1;

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
            server.runCommand("tp @a 2.5 -60 -3.5 0 0");
            this.gunId = server.computeOnServer(s -> {
                HowitzerEntity gun = ModEntities.HOWITZER.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                gun.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
                gun.vehicleInventory().setItem(0, new ItemStack(ModItems.SHELL_105MM_HE.get(), 8));
                s.overworld().addFreshEntity(gun);
                return gun.getId();
            });
            context.waitTicks(10);
            context.takeScreenshot("howitzer_packed");
            server.runOnServer(s -> check(s.getPlayerList().getPlayers().get(0).startRiding(gun(s), true, true), "the gunner must man the howitzer"));
            context.waitTicks(100);

            // 1. Packed: it loads, but it neither lays nor fires.
            this.look(context, 0.0F, -30.0F);
            context.waitTicks(40);
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(10);
            context.getInput().releaseKey(options -> options.keyAttack);
            context.waitTicks(5);
            float packedElevation = server.computeOnServer(s -> gun(s).elevation(1.0F));
            int shellsPacked = server.computeOnServer(HowitzerClientGameTest::countShells);
            int loaded = server.computeOnServer(s -> gun(s).vehicleWeaponSlotAmmo(0));
            LOGGER.info("[howitzer] packed: elevation {} with the view 30 deg up, shells {}, loaded {}", packedElevation, shellsPacked, loaded);
            check(Math.abs(packedElevation) < 0.5F, "packed, the gun must stay at its travel lock, was at " + packedElevation);
            check(shellsPacked == 0 && loaded == 1, "packed, the gun must not fire (shells " + shellsPacked + ", loaded " + loaded + ")");

            // 2. The tow key deploys it: trails out and spades down in 100 ticks.
            context.getInput().pressKey(options -> KeyBindings.VEHICLE_TOW);
            context.waitTicks(50);
            float halfway = server.computeOnServer(s -> gun(s).deployProgress());
            context.waitTicks(55);
            boolean deployed = server.computeOnServer(s -> gun(s).isDeployed());
            LOGGER.info("[howitzer] tow key: {} at 50 ticks, deployed {} at 105", halfway, deployed);
            check(halfway > 0.4F && halfway < 0.6F && deployed, "the tow key must deploy it over 100 ticks: " + halfway + ", " + deployed);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(10);
            context.takeScreenshot("howitzer_deployed");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));

            // 3. Fire mission by coordinates, 2 km south, charge 3 (V), laid on the panel's numbers.
            server.runCommand("execute as @p run justEnoughGuns firemission 0 " + TARGET_Z);
            // V is read as held (the client sends it each tick): hold it a few ticks, as a player's press lasts.
            context.getInput().holdKey(options -> KeyBindings.VEHICLE_SEEK);
            context.waitTicks(3);
            context.getInput().releaseKey(options -> KeyBindings.VEHICLE_SEEK);
            context.waitTicks(5);
            int charge = server.computeOnServer(s -> gun(s).charge());
            BlockPos target = server.computeOnServer(s -> gun(s).target());
            check(charge == 2 && target != null, "V must select charge 3 and the command must set the target: charge " + charge + ", target " + target);
            FireSolution solution = server.computeOnServer(s -> gun(s).solution());
            LOGGER.info(String.format(Locale.ROOT, "[howitzer] fire mission %s: bearing %.1f, range %.0f, low %.2f (%d ticks), high %.2f (%d ticks)",
                    target, solution.bearing(), solution.range(), solution.low(), solution.lowTicks(), solution.high(), solution.highTicks()));
            // Charge 3 reaches about 4 km: 2 km is a low arc of about 15 degrees; its high arc (about 75) is past the
            // gun's 66, so the panel offers none.
            check(Math.abs(solution.low() - 15.0F) < 1.5F && Float.isNaN(solution.high()),
                    "charge 3 must lay about 15 degrees for 2 km, with no high arc past 66: " + solution.low() + ", " + solution.high());
            this.look(context, solution.bearing() - 180.0F, -solution.low());
            for (int wait = 0; wait < 200; wait++) {
                context.waitTick();
                float elevation = server.computeOnServer(s -> gun(s).elevation(1.0F));
                if (Math.abs(elevation - solution.low()) < 0.05F) {
                    break;
                }
            }
            context.waitTicks(5);
            float laid = server.computeOnServer(s -> gun(s).elevation(1.0F));
            float bearing = server.computeOnServer(s -> gun(s).bearingExact(1.0F));
            LOGGER.info("[howitzer] laid at {} deg, bearing {}", laid, bearing);
            check(Math.abs(laid - solution.low()) < 0.1F, "the gun must lay to the panel's elevation, is at " + laid);
            context.takeScreenshot("howitzer_panel");
            List<Vec3> impacts = new ArrayList<>();
            for (int round = 0; round < 3; round++) {
                for (int wait = 0; wait < 120 && server.computeOnServer(s -> gun(s).vehicleWeaponSlotAmmo(0)) < 1; wait++) {
                    context.waitTick();
                }
                context.getInput().holdKey(options -> options.keyAttack);
                context.waitTicks(3);
                context.getInput().releaseKey(options -> options.keyAttack);
                // The shell bursts and is gone within the tick it lands, so the impact is its last step (about 14
                // blocks at 15 degrees) carried down to the ground.
                Vec3[] last = null;
                for (int tick = 0; tick < 700; tick++) {
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
                Vec3 step = last[1].add(0.0D, -HowitzerEntity.GRAVITY, 0.0D);
                double share = step.y >= 0.0D ? 1.0D : Mth.clamp((last[0].y - G) / -step.y, 0.0D, 1.0D);
                impacts.add(last[0].add(step.scale(share)));
            }
            Vec3 aim = HowitzerEntity.aimPoint(target);
            double worst = 0.0D;
            for (Vec3 impact : impacts) {
                double miss = Math.sqrt(Mth.square(impact.x - aim.x) + Mth.square(impact.z - aim.z));
                worst = Math.max(worst, miss);
                LOGGER.info(String.format(Locale.ROOT, "[howitzer] impact %.1f %.1f %.1f: %.1f blocks from the target", impact.x, impact.y, impact.z, miss));
            }
            // The gun's dispersion (spread 0.15) scatters rounds a few blocks; a wrong solution misses by far more.
            check(worst < 20.0D, "every round must land within 1% of the range (20 blocks) of a target 2 km off, worst " + worst);

            // 4. Pack up, then the FSV-6 tows it trail first, its lunette on the pintle.
            context.getInput().pressKey(options -> KeyBindings.VEHICLE_TOW);
            context.waitTicks(105);
            check(server.computeOnServer(s -> gun(s).isPacked()), "the tow key must pack it up again");
            server.runOnServer(s -> {
                s.getPlayerList().getPlayers().get(0).stopRiding();
                SupportVehicleEntity truck = ModEntities.TRUCK.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                // The truck stands ahead of the trail, facing away from the gun.
                truck.snapTo(0.5D, G, -9.0D, 180.0F, 0.0F);
                truck.addEnergy(truck.maxVehicleEnergy());
                s.overworld().addFreshEntity(truck);
                this.truckId = truck.getId();
            });
            context.waitTicks(10);
            server.runOnServer(s -> check(s.getPlayerList().getPlayers().get(0).startRiding(truck(s), true, true), "the driver must board the FSV-6"));
            context.waitTicks(SupportVehicleEntity.START_TICKS + 10);
            boolean offered = server.computeOnServer(s -> truck(s).towCandidate() == gun(s));
            context.getInput().pressKey(options -> KeyBindings.VEHICLE_TOW);
            context.waitTicks(6);
            boolean hitched = server.computeOnServer(s -> truck(s).towedEntityId() == this.gunId);
            LOGGER.info("[howitzer] the FSV-6 offers the gun {}, hitched {}", offered, hitched);
            check(offered && hitched, "the FSV-6 must hitch the packed howitzer (offered " + offered + ", hitched " + hitched + ")");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.player.setXRot(20.0F);
            });
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(120);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(30);
            context.takeScreenshot("howitzer_towed");
            double[] towed = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                HowitzerEntity gun = gun(s);
                Vec3 pintle = SupportRig.world(truck, 0, 8, 64);
                Vec3 eye = gun.position().add(HowitzerRig.TOW_EYE.yRot(-gun.getYRot() * Mth.DEG_TO_RAD));
                Vec3 forward = Vec3.directionFromRotation(0.0F, gun.getYRot());
                Vec3 toTruck = truck.position().subtract(gun.position()).multiply(1, 0, 1).normalize();
                return new double[] {new Vec3(eye.x - pintle.x, 0, eye.z - pintle.z).length(), forward.dot(toTruck),
                        gun.position().distanceTo(new Vec3(0.5D, G, 0.5D))};
            });
            LOGGER.info("[howitzer] towed: lunette {} from the pintle, facing {} (-1 away from the truck), moved {}", towed[0], towed[1], towed[2]);
            check(towed[2] > 30.0D, "the howitzer must follow the FSV-6, moved " + towed[2]);
            check(towed[0] < 0.8D, "the lunette must ride on the pintle, " + towed[0] + " blocks off");
            check(towed[1] < -0.95D, "the gun must trail behind the truck, muzzle away from it (" + towed[1] + ")");
            LOGGER.info("[howitzer] COMPLETE");
        } catch (Throwable failure) {
            LOGGER.error("[howitzer] FAILED: {}", failure.getMessage());
            throw failure;
        }
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

    private HowitzerEntity gun(MinecraftServer server) {
        if (server.overworld().getEntity(this.gunId) instanceof HowitzerEntity gun) {
            return gun;
        }
        throw new AssertionError("the howitzer is gone");
    }

    private SupportVehicleEntity truck(MinecraftServer server) {
        if (server.overworld().getEntity(this.truckId) instanceof SupportVehicleEntity truck) {
            return truck;
        }
        throw new AssertionError("the FSV-6 is gone");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
