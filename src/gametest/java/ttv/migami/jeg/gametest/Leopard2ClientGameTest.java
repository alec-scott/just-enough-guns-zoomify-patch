package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.entity.Leopard2Entity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.Leopard2Rig;

/**
 * The Leopard 2A7 (design/leopard2/BRIEF.md), what must not regress: the gun depresses to -9 degrees over the front but
 * stays level over the rear deck; an AP round fired on the move kills a BMP-2 120 blocks ahead; the commander's weapon
 * station fires from its own muzzle on the roof.
 */
public class Leopard2ClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private int tankId = -1;
    private int targetId = -1;

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
            this.tankId = server.computeOnServer(s -> {
                Leopard2Entity tank = ModEntities.LEOPARD2.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                tank.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
                tank.addEnergy(tank.maxVehicleEnergy());
                tank.vehicleInventory().setItem(0, new ItemStack(ModItems.SHELL_5IN_AP.get(), 8));
                tank.vehicleInventory().setItem(1, new ItemStack(ttv.migami.jeg.init.ModItems.AMMO.get(ttv.migami.jeg.Reference.id("rifle_ammo")).get(), 64));
                s.overworld().addFreshEntity(tank);
                return tank.getId();
            });
            context.waitTicks(20);
            context.takeScreenshot("leopard2_parked");
            server.runOnServer(s -> check(s.getPlayerList().getPlayers().get(0).startRiding(tank(s), true, true), "the crew must board"));
            context.waitTicks(10);

            // 1. Depression: -9 over the front; over the rear deck the gun stays level whatever the view.
            this.look(context, 0.0F, 30.0F);
            context.waitTicks(40);
            float front = server.computeOnServer(s -> tank(s).elevation(1.0F));
            this.look(context, 180.0F, 30.0F);
            context.waitTicks(130);
            float[] rear = server.computeOnServer(s -> new float[] {tank(s).elevation(1.0F), tank(s).turretYaw()});
            LOGGER.info("[leopard2] depression: front {} deg; rear {} deg at turret yaw {}", front, rear[0], rear[1]);
            check(Math.abs(front - Leopard2Rig.MIN_ELEVATION) < 0.2F, "the gun must depress to -9 over the front, is at " + front);
            check(Math.abs(Math.abs(rear[1]) - 180.0F) < 2.0F, "the turret must turn to the rear, yaw " + rear[1]);
            check(rear[0] > -0.2F, "over the rear deck the gun must stay level, is at " + rear[0]);

            // 2. AP on the move: a BMP-2 120 blocks ahead, the tank driving at it.
            this.targetId = server.computeOnServer(s -> {
                VehicleEntity bmp = ModEntities.BMP2.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                bmp.snapTo(0.5D, G, 120.5D, 0.0F, 0.0F);
                s.overworld().addFreshEntity(bmp);
                return bmp.getId();
            });
            server.runCommand("forceload add 0 0 0 160");
            context.getInput().pressKey(options -> KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitTicks(5);
            this.look(context, 0.0F, 0.0F);
            for (int wait = 0; wait < 200 && server.computeOnServer(s -> tank(s).vehicleWeaponSlotAmmo(1)) < 1; wait++) {
                context.waitTick();
            }
            for (int wait = 0; wait < 200 && server.computeOnServer(s -> Math.abs(tank(s).turretYaw()) > 0.5F); wait++) {
                context.waitTick();
            }
            context.waitTicks(10);
            float targetHealth = server.computeOnServer(s -> target(s) == null ? 0.0F : target(s).vehicleHealth());
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(30);
            double speed = server.computeOnServer(s -> tank(s).getDeltaMovement().horizontalDistance());
            context.runOnClient(mc -> mc.player.setXRot(0.0F));
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(3);
            context.getInput().releaseKey(options -> options.keyAttack);
            context.waitTicks(10);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(20);
            float after = server.computeOnServer(s -> target(s) == null || !target(s).isAlive() || target(s).isWrecked() ? 0.0F : target(s).vehicleHealth());
            LOGGER.info("[leopard2] AP on the move at {} blocks a tick: BMP-2 {} -> {}", speed, targetHealth, after);
            check(speed > 0.1D, "the tank must be moving when it fires, speed " + speed);
            check(after < targetHealth, "the AP round must strike the BMP-2 (" + targetHealth + " -> " + after + ")");

            // 3. The commander's weapon station: change seat, fire; the round leaves the station's muzzle on the roof.
            context.getInput().pressKey(options -> KeyBindings.VEHICLE_CHANGE_SEAT);
            context.waitTicks(10);
            int seat = server.computeOnServer(s -> tank(s).seatOf(s.getPlayerList().getPlayers().get(0)));
            check(seat == Leopard2Entity.COMMANDER_SEAT, "change seat must reach the commander, seat " + seat);
            this.look(context, 90.0F, -5.0F);
            context.getInput().pressKey(options -> KeyBindings.RELOAD);
            context.waitTicks(200);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            Vec3[] shot = new Vec3[2];
            context.getInput().holdKey(options -> options.keyAttack);
            for (int tick = 0; tick < 20 && shot[0] == null; tick++) {
                context.waitTick();
                Vec3[] found = server.computeOnServer(s -> {
                    for (var entity : s.overworld().getAllEntities()) {
                        if (entity instanceof BulletEntity bullet && bullet.tickCount <= 1) {
                            return new Vec3[] {bullet.position(), tank(s).onTurret(Leopard2Rig.RWS_TRUNNION, 1.0F)};
                        }
                    }
                    return null;
                });
                if (found != null) {
                    shot[0] = found[0];
                    shot[1] = found[1];
                }
            }
            context.getInput().releaseKey(options -> options.keyAttack);
            context.takeScreenshot("leopard2_rws");
            check(shot[0] != null, "the weapon station must fire");
            double fromStation = shot[0].distanceTo(shot[1]);
            LOGGER.info("[leopard2] weapon station round first seen {} blocks from its trunnion", fromStation);
            check(fromStation < 4.0D, "the round must leave the weapon station on the roof, " + fromStation + " blocks off");
            LOGGER.info("[leopard2] COMPLETE");
        } catch (Throwable failure) {
            LOGGER.error("[leopard2] FAILED: {}", failure.getMessage());
            throw failure;
        }
    }

    private void look(ClientGameTestContext context, float yaw, float pitch) {
        context.runOnClient(mc -> {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        });
        context.waitTicks(3);
    }

    private Leopard2Entity tank(MinecraftServer server) {
        if (server.overworld().getEntity(this.tankId) instanceof Leopard2Entity tank) {
            return tank;
        }
        throw new AssertionError("the Leopard is gone");
    }

    private VehicleEntity target(MinecraftServer server) {
        return server.overworld().getEntity(this.targetId) instanceof VehicleEntity vehicle ? vehicle : null;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
