package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.SpeedboatEntity;

/**
 * The speedboat (design/speedboat/BRIEF.md), what must not regress. On a lake 3 blocks deep ending in a sand bank:
 * <ol>
 *   <li>Planing: from rest at full throttle it comes onto the plane and reaches about 60 km/h (0.75 blocks a tick or
 *   more); every client sees the bow rise.</li>
 *   <li>The gun: the driver alone may fire it; a gunner in seat 1 takes it over; when the gunner leaves, it goes back.</li>
 *   <li>Beaching: run at the bank flat out, the hull climbs onto the sand instead of stopping dead; then it backs off
 *   under power into the water.</li>
 * </ol>
 */
public class SpeedboatClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int WATER_TOP = -61;
    private static final int BANK_Z = 150;
    private int boatId = -1;

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
            server.runCommand("forceload add -16 -16 16 176");
            // The lake: 3 deep, 25 wide, up to a sand bank one block above the water.
            server.runCommand("fill -12 -63 0 12 -61 " + (BANK_Z - 1) + " minecraft:water");
            server.runCommand("fill -12 -63 " + BANK_Z + " 12 -60 " + (BANK_Z + 20) + " minecraft:sand");
            server.runCommand("tp @a 3.5 -60 -2.5 0 0");
            context.waitTicks(20);
            this.boatId = server.computeOnServer(s -> {
                SpeedboatEntity boat = ModEntities.SPEEDBOAT.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                boat.snapTo(0.5D, WATER_TOP + 0.6D, 8.5D, 0.0F, 0.0F);
                boat.addEnergy(boat.maxVehicleEnergy());
                boat.vehicleInventory().setItem(0, new ItemStack(ttv.migami.jeg.init.ModItems.AMMO.get(ttv.migami.jeg.Reference.id("rifle_ammo")).get(), 64));
                s.overworld().addFreshEntity(boat);
                return boat.getId();
            });
            context.waitTicks(40);
            server.runOnServer(s -> check(s.getPlayerList().getPlayers().get(0).startRiding(boat(s), true, true), "the driver must board"));
            context.waitTicks(10);
            // The real diesel start (lobster boat) plays as the helm is taken.
            java.util.List<String> starting = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.GroundVehicleAudio.shots(this.boatId));
            LOGGER.info("[speedboat] boarding: one-shots {}", starting);
            check(starting.contains("engine_start"), "the engine start must be heard as the helm is taken: " + starting);
            int driverSeat = server.computeOnServer(s -> boat(s).seatOf(s.getPlayerList().getPlayers().get(0)));
            check(driverSeat == SpeedboatEntity.DRIVER_SEAT, "the first aboard must take the helm, seat " + driverSeat);

            // 2. The gun: the driver's alone; a gunner takes it; it comes back.
            boolean driverAlone = server.computeOnServer(s -> boat(s).weaponUsableBySeat(boat(s).vehicleData().defaults().weapons().get(0), SpeedboatEntity.DRIVER_SEAT));
            int gunnerId = server.computeOnServer(s -> {
                Villager gunner = EntityType.VILLAGER.create(s.overworld(), EntitySpawnReason.COMMAND);
                gunner.snapTo(0.5D, WATER_TOP + 1.0D, 10.5D, 0.0F, 0.0F);
                s.overworld().addFreshEntity(gunner);
                // Since yftm.22 only a player or tagged crew may drive or man a gun.
                gunner.addTag(ttv.migami.jeg.vehicle.ai.EnemyVehicleController.ENEMY_VEHICLE_CREW_TAG);
                gunner.startRiding(boat(s), true, true);
                return gunner.getId();
            });
            context.waitTicks(10);
            int[] handover = server.computeOnServer(s -> {
                var gunner = s.overworld().getEntity(gunnerId);
                var weapon = boat(s).vehicleData().defaults().weapons().get(0);
                return new int[] {boat(s).seatOf(gunner), boat(s).turretSeat(),
                        boat(s).weaponUsableBySeat(weapon, SpeedboatEntity.DRIVER_SEAT) ? 1 : 0,
                        boat(s).weaponUsableBySeat(weapon, SpeedboatEntity.GUNNER_SEAT) ? 1 : 0};
            });
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(10);
            context.takeScreenshot("speedboat_gunner_aboard");
            server.runOnServer(s -> s.overworld().getEntity(gunnerId).stopRiding());
            context.waitTicks(5);
            boolean backToDriver = server.computeOnServer(s -> boat(s).weaponUsableBySeat(boat(s).vehicleData().defaults().weapons().get(0), SpeedboatEntity.DRIVER_SEAT)
                    && boat(s).turretSeat() == SpeedboatEntity.DRIVER_SEAT);
            server.runOnServer(s -> s.overworld().getEntity(gunnerId).discard());
            LOGGER.info("[speedboat] gun: driver alone {}, gunner in seat {} -> turret seat {}, driver {}, gunner {}; gunner gone -> driver {}",
                    driverAlone, handover[0], handover[1], handover[2] == 1, handover[3] == 1, backToDriver);
            check(driverAlone, "alone, the driver must have the gun");
            check(handover[0] == SpeedboatEntity.GUNNER_SEAT, "the second aboard must take the gunner's seat, seat " + handover[0]);
            check(handover[1] == SpeedboatEntity.GUNNER_SEAT && handover[2] == 0 && handover[3] == 1, "the gunner must take the gun from the driver");
            check(backToDriver, "with the gunner gone the gun must go back to the driver");
            // On automatic the gun is its own: the driver can neither lay nor fire it (the user's review, 2026-11-19).
            int[] automatic = server.computeOnServer(s -> {
                var weapon = boat(s).vehicleData().defaults().weapons().get(0);
                boat(s).setAutomatic(true);
                return new int[] {boat(s).turretSeat(), boat(s).weaponUsableBySeat(weapon, SpeedboatEntity.DRIVER_SEAT) ? 1 : 0};
            });
            context.waitTicks(5);
            int clientOnAuto = context.computeOnClient(mc -> mc.level.getEntity(this.boatId) instanceof SpeedboatEntity b ? b.turretSeat() : -9);
            // The driver looks well off to the side: the boat keeps its heading (it swung round to the driver's view on
            // automatic: the user's review, 2026-11-19, "speed boat bugs out").
            float headingBefore = server.computeOnServer(s -> boat(s).getYRot());
            context.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() + 60.0F));
            context.waitTicks(20);
            float headingAfter = server.computeOnServer(s -> boat(s).getYRot());
            LOGGER.info("[speedboat] automatic, driver looking 60 deg aside: heading {} -> {}", headingBefore, headingAfter);
            check(Math.abs(net.minecraft.util.Mth.wrapDegrees(headingAfter - headingBefore)) < 3.0F,
                    "the boat must not swing round to the driver's view on automatic: " + headingBefore + " -> " + headingAfter);
            context.runOnClient(mc -> mc.player.setYRot(mc.player.getYRot() - 60.0F));
            context.waitTicks(5);
            int[] manual = server.computeOnServer(s -> {
                var weapon = boat(s).vehicleData().defaults().weapons().get(0);
                boat(s).setAutomatic(false);
                return new int[] {boat(s).turretSeat(), boat(s).weaponUsableBySeat(weapon, SpeedboatEntity.DRIVER_SEAT) ? 1 : 0};
            });
            context.waitTicks(5);
            int clientOnManual = context.computeOnClient(mc -> mc.level.getEntity(this.boatId) instanceof SpeedboatEntity b ? b.turretSeat() : -9);
            LOGGER.info("[speedboat] automatic: turret seat {} (client {}), driver may fire {}; manual again: turret seat {} (client {}), driver {}",
                    automatic[0], clientOnAuto, automatic[1] == 1, manual[0], clientOnManual, manual[1] == 1);
            check(automatic[0] == SpeedboatEntity.GUNNER_SEAT && automatic[1] == 0 && clientOnAuto == SpeedboatEntity.GUNNER_SEAT,
                    "on automatic the driver must have no hand on the gun");
            check(manual[0] == SpeedboatEntity.DRIVER_SEAT && manual[1] == 1 && clientOnManual == SpeedboatEntity.DRIVER_SEAT,
                    "back on manual the driver must have the gun again");

            // 1. Planing: full throttle from rest.
            double[] start = server.computeOnServer(s -> new double[] {boat(s).getY(), boat(s).getZ()});
            context.getInput().holdKey(options -> options.keyUp);
            double top = 0.0D;
            int planedAt = -1;
            float rise = 0.0F, hull = 0.0F;
            double[] lastZ = new double[10];
            java.util.Arrays.fill(lastZ, start[1]);
            for (int tick = 0; tick < 160; tick++) {
                context.waitTick();
                double[] state = server.computeOnServer(s -> new double[] {0.0D, boat(s).isBoatPlaning() ? 1 : 0, boat(s).getZ()});
                // Speed from how far the hull moved over the last 10 ticks (the driver's client drives it and the server
                // follows in steps, so one tick's move is noisy).
                if (tick >= 10) {
                    top = Math.max(top, (state[2] - lastZ[tick % 10]) / 10.0D);
                }
                lastZ[tick % 10] = state[2];
                if (tick % 10 == 0) {
                    LOGGER.info("[speedboat] tick {}: z {}, planing {}", tick, state[2], state[1] > 0.0D);
                }
                if (planedAt < 0 && state[1] > 0.0D) {
                    planedAt = tick;
                }
                rise = Math.max(rise, context.computeOnClient(mc -> mc.level.getEntity(this.boatId) instanceof SpeedboatEntity b ? b.boatBowRise(1.0F) : 0.0F));
                if (tick == 70) {
                    context.takeScreenshot("speedboat_planing");
                    hull = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.GroundVehicleAudio.loopVolume(this.boatId, "hull"));
                }
                if (state[2] > BANK_Z - 30) {
                    break;
                }
            }
            LOGGER.info("[speedboat] planing: on the plane after {} ticks, top {} blocks a tick ({} km/h), bow rise {} deg",
                    planedAt, top, top * 72.0D, rise);
            check(planedAt > 0, "the hull must come onto the plane");
            check(top >= 0.75D, "on the plane it must reach about 60 km/h, reached " + top * 72.0D + " km/h");
            check(rise > 2.0F, "every client must see the bow rise, " + rise + " deg");
            LOGGER.info("[speedboat] hull water heard on the plane at {}", hull);
            check(hull > 0.05F, "the water along the hull must be heard on the plane, " + hull);

            // 3. Beaching: keep the throttle open into the bank.
            for (int tick = 0; tick < 120; tick++) {
                context.waitTick();
                if (server.computeOnServer(s -> boat(s).getZ()) > BANK_Z && tick > 40) {
                    break;
                }
            }
            context.waitTicks(30);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(40);
            context.takeScreenshot("speedboat_beached");
            double[] beached = server.computeOnServer(s -> new double[] {boat(s).getY(), boat(s).getZ(), boat(s).getDeltaMovement().horizontalDistance()});
            LOGGER.info("[speedboat] beached: y {} (afloat {}), bow z {} (bank at {}), speed {}", beached[0], start[0], beached[1], BANK_Z, beached[2]);
            check(beached[1] > BANK_Z - 2.5D, "run at the bank, the hull must ride up onto the sand, z " + beached[1]);
            check(beached[0] > start[0] + 0.5D, "beached, the hull must sit on the sand above the water, y " + beached[0]);
            check(beached[2] < 0.05D, "beached, it must come to rest, speed " + beached[2]);
            context.getInput().holdKey(options -> options.keyDown);
            double backedTo = beached[1];
            for (int tick = 0; tick < 240; tick++) {
                context.waitTick();
                backedTo = server.computeOnServer(s -> boat(s).getZ());
                if (backedTo < BANK_Z - 8) {
                    break;
                }
            }
            context.getInput().releaseKey(options -> options.keyDown);
            context.waitTicks(20);
            double[] afloat = server.computeOnServer(s -> new double[] {boat(s).getY(), boat(s).getZ()});
            LOGGER.info("[speedboat] backed off to z {}, y {}", afloat[1], afloat[0]);
            check(afloat[1] < BANK_Z - 6, "under power astern it must back off the beach, z " + afloat[1]);
            check(afloat[0] < start[0] + 0.4D, "backed off, it must float again, y " + afloat[0]);
            LOGGER.info("[speedboat] COMPLETE");
        } catch (Throwable failure) {
            LOGGER.error("[speedboat] FAILED: {}", failure.getMessage());
            throw failure;
        }
    }

    private SpeedboatEntity boat(MinecraftServer server) {
        if (server.overworld().getEntity(this.boatId) instanceof SpeedboatEntity boat) {
            return boat;
        }
        throw new AssertionError("the speedboat is gone");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
