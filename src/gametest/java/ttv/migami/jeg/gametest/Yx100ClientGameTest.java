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
import ttv.migami.jeg.vehicle.entity.Yx100Entity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.Yx100Rig;

/**
 * The YX-100 (design/yx100/BRIEF.md), what must not regress: the gun depresses only as far as the hull lets it at each
 * bearing (rig.json: -3.5 degrees ahead, -6.5 abeam, held at +3.5 over the engine deck); an AP round fired on the move
 * strikes a BMP-2 120 blocks ahead; the commander's weapon station fires from its own muzzle on the roof.
 */
public class Yx100ClientGameTest implements FabricClientGameTest {
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
                Yx100Entity tank = ModEntities.YX100.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                tank.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
                tank.addEnergy(tank.maxVehicleEnergy());
                tank.vehicleInventory().setItem(0, new ItemStack(ModItems.SHELL_5IN_AP.get(), 8));
                tank.vehicleInventory().setItem(1, new ItemStack(ttv.migami.jeg.init.ModItems.AMMO.get(ttv.migami.jeg.Reference.id("rifle_ammo")).get(), 64));
                s.overworld().addFreshEntity(tank);
                return tank.getId();
            });
            context.waitTicks(20);
            context.takeScreenshot("yx100_parked");
            server.runOnServer(s -> check(s.getPlayerList().getPlayers().get(0).startRiding(tank(s), true, true), "the crew must board"));
            context.waitTicks(10);

            // 1. Depression: the gunner looks 30 degrees down ahead, abeam and astern; the gun stops where the hull is.
            float[][] laid = new float[3][];
            float[] views = {0.0F, 90.0F, 180.0F};
            for (int i = 0; i < views.length; i++) {
                this.look(context, views[i], 30.0F);
                context.waitTicks(i == 0 ? 40 : 70);
                laid[i] = server.computeOnServer(s -> new float[] {tank(s).elevation(1.0F), tank(s).turretYaw()});
                LOGGER.info("[yx100] depression looking {} deg: gun at {} deg, turret yaw {}", views[i], laid[i][0], laid[i][1]);
            }
            check(Math.abs(laid[0][0] - Yx100Rig.lowestElevation(0.0F)) < 0.2F, "ahead the gun must stop at -3.5, is at " + laid[0][0]);
            check(Math.abs(Math.abs(laid[1][1]) - 90.0F) < 2.0F, "the turret must turn abeam, yaw " + laid[1][1]);
            check(Math.abs(laid[1][0] - Yx100Rig.lowestElevation(laid[1][1])) < 0.2F, "abeam the gun must stop at "
                    + Yx100Rig.lowestElevation(laid[1][1]) + ", is at " + laid[1][0]);
            check(Math.abs(Math.abs(laid[2][1]) - 180.0F) < 2.0F, "the turret must turn to the rear, yaw " + laid[2][1]);
            check(laid[2][0] > 3.3F, "over the engine deck the gun must stay at +3.5, is at " + laid[2][0]);

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
            LOGGER.info("[yx100] AP on the move at {} blocks a tick: BMP-2 {} -> {}", speed, targetHealth, after);
            check(speed > 0.1D, "the tank must be moving when it fires, speed " + speed);
            check(after < targetHealth, "the AP round must strike the BMP-2 (" + targetHealth + " -> " + after + ")");

            // 3. The commander's weapon station: change seat, fire; the round leaves the station's muzzle on the roof.
            context.getInput().pressKey(options -> KeyBindings.VEHICLE_CHANGE_SEAT);
            context.waitTicks(10);
            int seat = server.computeOnServer(s -> tank(s).seatOf(s.getPlayerList().getPlayers().get(0)));
            check(seat == Yx100Entity.COMMANDER_SEAT, "change seat must reach the commander, seat " + seat);
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
                            return new Vec3[] {bullet.position(), tank(s).onTurret(Yx100Rig.RWS_TRUNNION, 1.0F)};
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
            context.takeScreenshot("yx100_rws");
            check(shot[0] != null, "the weapon station must fire");
            double fromStation = shot[0].distanceTo(shot[1]);
            LOGGER.info("[yx100] weapon station round first seen {} blocks from its trunnion", fromStation);
            check(fromStation < 4.0D, "the round must leave the weapon station on the roof, " + fromStation + " blocks off");
            LOGGER.info("[yx100] COMPLETE");
        } catch (Throwable failure) {
            LOGGER.error("[yx100] FAILED: {}", failure.getMessage());
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

    private Yx100Entity tank(MinecraftServer server) {
        if (server.overworld().getEntity(this.tankId) instanceof Yx100Entity tank) {
            return tank;
        }
        throw new AssertionError("the YX-100 is gone");
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
