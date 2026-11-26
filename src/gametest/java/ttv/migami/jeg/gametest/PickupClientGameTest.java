package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.entity.PickupRocketEntity;
import ttv.migami.jeg.vehicle.entity.PickupTowEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.projectile.ShellEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

/**
 * The 4x4 pickup and its technicals (design/pickup/BRIEF.md), what must not regress:
 * <ol>
 *   <li>each of the four turns on at most 9 blocks from a crawl (the guide's limit for wheeled vehicles), measured as
 *   ArmourTurningClientGameTest does;</li>
 *   <li>the rocket technical's pod never lays below 10 degrees, and a full ripple laid straight ahead over the cab at
 *   that lowest elevation leaves its own truck unharmed;</li>
 *   <li>the TOW technical's launcher stands on its pedestal: a missile fired level straight ahead clears the cab
 *   (SBW's tripod stood on the bed floor, its tube aimed into the roof, 2026-11-26) and flies on.</li>
 * </ol>
 */
public class PickupClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> mc.options.renderDistance().set(12));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitTicks(100);
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("forceload add -160 -160 160 160");
            for (String type : new String[]{"pickup", "pickup_hmg", "pickup_rocket", "pickup_tow"}) {
                this.turning(context, server, type);
            }
            this.rockets(context, server);
            this.tow(context, server);
            LOGGER.info("[pickup] COMPLETE");
        } catch (Throwable failure) {
            LOGGER.error("[pickup] FAILED: {}", failure.getMessage());
            throw failure;
        }
    }

    private void turning(ClientGameTestContext context, TestServerContext server, String type) {
        int id = this.board(context, server, type, 0.5D, 0.5D);
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(10);
        context.getInput().holdKey(options -> options.keyLeft);
        context.waitTicks(10);
        double[] a = server.computeOnServer(s -> pose(s.overworld().getEntity(id)));
        context.waitTicks(40);
        double[] b = server.computeOnServer(s -> pose(s.overworld().getEntity(id)));
        context.getInput().releaseKey(options -> options.keyLeft);
        context.getInput().releaseKey(options -> options.keyUp);
        double travelled = Math.hypot(b[0] - a[0], b[1] - a[1]);
        double turned = Math.abs(Mth.wrapDegrees(b[2] - a[2])) * Mth.DEG_TO_RAD;
        double radius = turned > 1.0E-3 ? travelled / turned : 1.0E9;
        LOGGER.info("[pickup] " + type + " from a crawl: {} blocks in 2 s, turned {} degrees, radius {} blocks",
                String.format("%.1f", travelled), String.format("%.1f", turned * Mth.RAD_TO_DEG), String.format("%.1f", radius));
        check(radius <= 9.0D, type + " must turn on at most 9 blocks from a crawl, radius " + radius);
        this.leave(context, server);
    }

    private void rockets(ClientGameTestContext context, TestServerContext server) {
        int id = this.board(context, server, "pickup_rocket", 0.5D, 0.5D);
        server.runOnServer(s -> ((VehicleEntity) s.overworld().getEntity(id)).vehicleInventory()
                .setItem(0, new ItemStack(ModItems.ROCKET_107MM_HE.get(), 12)));
        context.getInput().pressKey(KeyBindings.RELOAD);
        context.waitTicks(180);
        int loaded = server.computeOnServer(s -> ((PickupRocketEntity) s.overworld().getEntity(id)).rocketsLoaded());
        check(loaded == 12, "R must load all twelve tubes, loaded " + loaded);
        // Laid straight ahead and asked for 5 degrees below level: the pod stops at 10 up.
        this.look(context, id, 0.0F, 5.0F);
        context.waitTicks(100);
        float elevation = server.computeOnServer(s -> ((PickupRocketEntity) s.overworld().getEntity(id)).elevation(1.0F));
        LOGGER.info("[pickup] rocket pod asked 5 degrees down, laid at {} degrees", elevation);
        check(elevation >= 9.9F, "the pod must not lay below 10 degrees, laid " + elevation);
        float before = server.computeOnServer(s -> ((VehicleEntity) s.overworld().getEntity(id)).vehicleHealth());
        int[] fired = {0};
        context.getInput().holdKey(options -> options.keyAttack);
        for (int tick = 0; tick < 200 && fired[0] < 12; tick++) {
            context.waitTick();
            server.runOnServer(s -> {
                for (Entity entity : s.overworld().getAllEntities()) {
                    if (entity instanceof ShellEntity && entity.addTag("pickup_seen")) {
                        fired[0]++;
                    }
                }
            });
        }
        context.getInput().releaseKey(options -> options.keyAttack);
        context.waitTicks(60);
        float after = server.computeOnServer(s -> ((VehicleEntity) s.overworld().getEntity(id)).vehicleHealth());
        LOGGER.info("[pickup] ripple over the cab at {} degrees: {} rockets, hull {} -> {}", elevation, fired[0], before, after);
        context.takeScreenshot("pickup_rocket_ripple");
        check(fired[0] == 12, "a held trigger must ripple all twelve, fired " + fired[0]);
        check(after >= before, "a ripple laid over the cab must not strike its own truck: hull " + before + " -> " + after);
        server.runCommand("kill @e[type=jeg:cannon_shell]");
        this.leave(context, server);
    }

    private void tow(ClientGameTestContext context, TestServerContext server) {
        int id = this.board(context, server, "pickup_tow", 0.5D, 0.5D);
        server.runOnServer(s -> ((VehicleEntity) s.overworld().getEntity(id)).vehicleInventory()
                .setItem(0, new ItemStack(ModItems.AMMO.get(Reference.id("medium_anti_ground_missile")).get(), 2)));
        // The driver's seat first: two presses take the player round to the gunner's.
        for (int i = 0; i < 2 && server.computeOnServer(s -> seat(s, id)) != PickupTowEntity.GUNNER_SEAT; i++) {
            context.getInput().pressKey(KeyBindings.VEHICLE_CHANGE_SEAT);
            context.waitTicks(20);
        }
        int seat = server.computeOnServer(s -> seat(s, id));
        check(seat == PickupTowEntity.GUNNER_SEAT, "the player must reach the TOW gunner's seat, sits in " + seat);
        context.getInput().pressKey(KeyBindings.RELOAD);
        context.waitTicks(160);
        int loaded = server.computeOnServer(s -> ((VehicleEntity) s.overworld().getEntity(id)).vehicleWeaponSlotAmmo(0));
        check(loaded == 1, "R must load a missile into the TOW, loaded " + loaded);
        this.look(context, id, 0.0F, 0.0F);
        context.waitTicks(60);
        float before = server.computeOnServer(s -> ((VehicleEntity) s.overworld().getEntity(id)).vehicleHealth());
        double[] start = server.computeOnServer(s -> pose(s.overworld().getEntity(id)));
        context.getInput().holdKey(options -> options.keyAttack);
        double[] furthest = {0.0D};
        for (int tick = 0; tick < 60; tick++) {
            context.waitTick();
            if (tick == 5) {
                context.getInput().releaseKey(options -> options.keyAttack);
            }
            server.runOnServer(s -> {
                for (Entity entity : s.overworld().getAllEntities()) {
                    if (entity instanceof VehicleMissileEntity) {
                        furthest[0] = Math.max(furthest[0], entity.getZ() - start[1]);
                    }
                }
            });
        }
        float after = server.computeOnServer(s -> ((VehicleEntity) s.overworld().getEntity(id)).vehicleHealth());
        LOGGER.info("[pickup] TOW fired level ahead: missile reached {} blocks ahead, hull {} -> {}",
                String.format("%.1f", furthest[0]), before, after);
        context.takeScreenshot("pickup_tow_level_shot");
        check(after >= before, "a level TOW shot ahead must clear the cab: hull " + before + " -> " + after);
        check(furthest[0] > 30.0D, "the missile must fly on past the cab, reached " + furthest[0] + " blocks");
        this.leave(context, server);
    }

    private int board(ClientGameTestContext context, TestServerContext server, String type, double x, double z) {
        server.runCommand("tp @a " + x + " -60 " + (z - 5.0D) + " 0 0");
        context.waitTicks(10);
        server.runCommand("summon jeg:" + type + " " + x + " -60 " + z + " {Rotation:[0f,0f],Tags:[\"pickup_test\"]}");
        context.waitTicks(10);
        int id = server.computeOnServer(s -> {
            for (Entity entity : s.overworld().getAllEntities()) {
                if (entity instanceof VehicleEntity vehicle && entity.entityTags().contains("pickup_test")
                        && vehicle.vehicleDataId().getPath().equals(type)) {
                    vehicle.addEnergy(vehicle.maxVehicleEnergy());
                    return entity.getId();
                }
            }
            throw new AssertionError(type + " was not summoned");
        });
        server.runOnServer(s -> check(s.getPlayerList().getPlayers().get(0).startRiding(s.overworld().getEntity(id), true, true),
                "the player must board the " + type));
        context.waitTicks(80);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        return id;
    }

    private void leave(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("ride @a dismount");
        server.runCommand("kill @e[tag=pickup_test]");
        context.waitTicks(20);
    }

    /** Looks at a yaw and pitch relative to the vehicle's heading. */
    private void look(ClientGameTestContext context, int id, float yaw, float pitch) {
        context.runOnClient(mc -> {
            float heading = mc.level.getEntity(id).getYRot();
            mc.player.setYRot(heading + yaw);
            mc.player.setXRot(pitch);
        });
        context.waitTicks(3);
    }

    private static int seat(MinecraftServer server, int id) {
        VehicleEntity vehicle = (VehicleEntity) server.overworld().getEntity(id);
        return vehicle.getSeatIndex(server.getPlayerList().getPlayers().get(0));
    }

    private static double[] pose(Entity entity) {
        return new double[]{entity.getX(), entity.getZ(), entity.getYRot()};
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
