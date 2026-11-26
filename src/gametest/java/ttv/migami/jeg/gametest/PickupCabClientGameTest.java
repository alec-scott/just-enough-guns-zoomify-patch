package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The technical's cab with a mob aboard (2026-11-26), what must not regress:
 * <ol>
 *   <li>a villager in the cab's passenger seat keeps its head under the cab roof (it sat 0.6 up by its feet and its head
 *   stood through the roof), and still high enough to see out of the windows;</li>
 *   <li>the cab doors answer only to the cab: a gunner climbing into the bed leaves the cab's count unchanged (the doors
 *   swung open for him), someone getting into the cab changes it;</li>
 *   <li>a mob in the bed stands on the bed's floor, as a player does (bed riders floated 0.6 above it).</li>
 * </ol>
 * Seen on both sides: the server places riders and the client draws them from the same seat.
 */
public class PickupCabClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    /** The top of the cab roof above the hull's origin: the cab's hitbox part, from the refit (pickup_hmg.json obb 0). */
    private static final double ROOF = 1.1097D + 0.6896D;
    /** The windows' sill above the hull's origin, about: a rider's eyes must be above it to see out. */
    private static final double SILL = 1.1D;
    /** The bed's floor above the hull's origin: the bed seats' height ("floor_seats"). */
    private static final double BED_FLOOR = 0.62D;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitTicks(60);
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("difficulty easy");
            server.runCommand("tp @a 0.5 -60 -6 0 0");
            server.runCommand("summon jeg:pickup_hmg 0.5 -60 0.5 {Rotation:[0f,0f],Tags:[\"cab_test\"]}");
            context.waitTicks(10);
            int id = server.computeOnServer(s -> {
                for (Entity entity : s.overworld().getAllEntities()) {
                    if (entity instanceof VehicleEntity && entity.entityTags().contains("cab_test")) {
                        return entity.getId();
                    }
                }
                throw new AssertionError("the technical was not summoned");
            });

            // A villager boards: the driver's seat is for crew, so it takes the cab's passenger seat.
            int villager = server.computeOnServer(s -> this.board(s.overworld(), id, EntityType.VILLAGER));
            context.waitTicks(20);
            double[] serverSeat = server.computeOnServer(s -> this.seat(s.overworld().getEntity(id), s.overworld().getEntity(villager)));
            double[] clientSeat = context.computeOnClient(mc -> this.seat(mc.level.getEntity(id), mc.level.getEntity(villager)));
            LOGGER.info("[pickup-cab] villager in seat {}: head top {} above the hull (roof {}), eyes {} (sill {}); client head {}",
                    (int) serverSeat[0], String.format("%.2f", serverSeat[1]), String.format("%.2f", ROOF),
                    String.format("%.2f", serverSeat[2]), String.format("%.2f", SILL), String.format("%.2f", clientSeat[1]));
            check(serverSeat[0] == 1, "the villager must sit in the cab's passenger seat, not " + serverSeat[0]);
            check(serverSeat[1] <= ROOF, "the villager's head must stay under the cab roof: " + serverSeat[1] + " > " + ROOF);
            check(serverSeat[2] >= SILL, "the villager must see out of the windows: eyes " + serverSeat[2]);
            check(Math.abs(clientSeat[1] - serverSeat[1]) < 0.05D, "the client must seat the villager where the server does");
            int cab = server.computeOnServer(s -> ((VehicleEntity) s.overworld().getEntity(id)).cabOccupants());
            check(cab == 1, "one rides in the cab, counted " + cab);
            // From the villager's side, low, after the doors have shut: nothing of it below the sill or above the roof.
            server.runCommand("tp @a -4.0 -60 1.1 -90 8");
            context.waitTicks(80);
            context.takeScreenshot("pickup_cab_villager");

            // A zombie climbs into the bed behind the gun: the cab's count, which swings the doors, stays as it was.
            int zombie = server.computeOnServer(s -> this.board(s.overworld(), id, EntityType.ZOMBIE));
            context.waitTicks(20);
            int[] after = context.computeOnClient(mc -> {
                VehicleEntity vehicle = (VehicleEntity) mc.level.getEntity(id);
                return new int[]{vehicle.getSeatIndex(mc.level.getEntity(zombie)), vehicle.cabOccupants()};
            });
            LOGGER.info("[pickup-cab] zombie in seat {}; cab count on the client {}", after[0], after[1]);
            check(after[0] == 2, "the zombie must take the gunner's place in the bed, not seat " + after[0]);
            check(after[1] == 1, "a gunner in the bed must not count as someone in the cab: " + after[1]);
            // On the bed floor, where a player gunner's feet are: lifted by its attachment point it floated 0.6 above.
            double feet = server.computeOnServer(s -> s.overworld().getEntity(zombie).getY() - s.overworld().getEntity(id).getY());
            LOGGER.info("[pickup-cab] the zombie gunner's feet {} above the hull (bed floor {})", String.format("%.2f", feet),
                    String.format("%.2f", BED_FLOOR));
            check(Math.abs(feet - BED_FLOOR) < 0.05D, "a mob in the bed must stand on its floor, feet at " + feet);
            LOGGER.info("[pickup-cab] COMPLETE");
        } catch (Throwable failure) {
            LOGGER.error("[pickup-cab] FAILED: {}", failure.getMessage());
            throw failure;
        }
    }

    private int board(net.minecraft.server.level.ServerLevel level, int vehicleId, EntityType<? extends Mob> type) {
        Mob mob = type.create(level, EntitySpawnReason.COMMAND);
        Entity vehicle = level.getEntity(vehicleId);
        mob.snapTo(vehicle.getX() + 2.0D, vehicle.getY(), vehicle.getZ(), 0.0F, 0.0F);
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        check(mob.startRiding(vehicle, true, true), "the " + type.getDescriptionId() + " must board");
        return mob.getId();
    }

    /** The rider's seat, its head's top and its eyes above the hull's origin. */
    private double[] seat(Entity vehicleEntity, Entity rider) {
        VehicleEntity vehicle = (VehicleEntity) vehicleEntity;
        return new double[]{vehicle.getSeatIndex(rider), rider.getBoundingBox().maxY - vehicle.getY(), rider.getEyeY() - vehicle.getY()};
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
