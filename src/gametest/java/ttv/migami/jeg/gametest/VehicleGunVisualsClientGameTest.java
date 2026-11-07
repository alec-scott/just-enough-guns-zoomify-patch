package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.client.render.BulletTrailRenderer;
import ttv.migami.jeg.client.render.gun.GunMuzzleTracker;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Helicopter guns as seen by their crew: the MI-28 gunner's rounds must be seen landing through the zoomed sight, the
 * AH-6's gun must look heavy, and a passenger's own rifle must shoot from the rifle, not from the helicopter.
 */
public class VehicleGunVisualsClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
            this.mi28Gunner(context, server);
            this.ah6Gun(context, server);
            this.ah6Passenger(context, server);
        }
    }

    private void mi28Gunner(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("fill -3 -51 2 3 -51 10 minecraft:stone");
        server.runCommand("tp @a 0 -50 0 0 0");
        server.runCommand("execute as @p at @s run summon jeg:mi28 ~ ~ ~6 {Rotation:[0f,0f]}");
        // Targets on the ground ahead: a wall and some husks in front of it.
        server.runCommand("fill -6 -60 34 6 -57 34 minecraft:stone_bricks");
        server.runCommand("summon minecraft:husk 0 -60 28 {NoAI:1b,PersistenceRequired:1b}");
        server.runCommand("summon minecraft:husk 2 -60 30 {NoAI:1b,PersistenceRequired:1b}");
        context.waitTicks(5);
        stock(server, "rifle_ammo", 64);
        server.runCommand("ride @p mount @e[type=jeg:mi28,limit=1,sort=nearest]");
        context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
        context.getInput().pressKey(KeyBindings.VEHICLE_CHANGE_SEAT);
        context.waitTicks(10);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        context.getInput().pressKey(KeyBindings.RELOAD);
        context.waitTicks(130);
        int before = BulletTrailRenderer.impactsShown();
        context.getInput().holdKey(options -> options.keyUse);
        context.getInput().holdKey(options -> options.keyAttack);
        for (int tick = 1; tick <= 70; tick++) {
            context.runOnClient(mc -> {
                mc.player.setYRot(mc.player.getVehicle().getYRot());
                mc.player.setXRot(16.0F);
            });
            context.waitTick();
            if (tick == 40 || tick == 50 || tick == 60) {
                context.takeScreenshot("mi28_zoom_firing_t" + tick);
            }
        }
        context.getInput().releaseKey(options -> options.keyAttack);
        context.getInput().releaseKey(options -> options.keyUse);
        int landed = BulletTrailRenderer.impactsShown() - before;
        LOGGER.info("[vehicle-gun] mi28 tracers drawn reaching their impact: {}", landed);
        check(landed >= 10, "MI-28 rounds should be seen landing through the sight, saw " + landed);
        server.runCommand("ride @p dismount");
        server.runCommand("kill @e[type=!minecraft:player]");
        context.waitTicks(10);
    }

    private void ah6Gun(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 100 -60 0 0 0");
        server.runCommand("fill 94 -60 36 106 -54 36 minecraft:stone_bricks");
        server.runCommand("execute as @p at @s run summon jeg:ah6 ~ ~ ~4 {Rotation:[0f,0f]}");
        context.waitTicks(5);
        stock(server, "rifle_ammo", 64);
        server.runCommand("ride @p mount @e[type=jeg:ah6,limit=1,sort=nearest]");
        context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.getInput().pressKey(KeyBindings.RELOAD);
        context.waitTicks(130);
        context.getInput().holdKey(options -> options.keyAttack);
        for (int tick = 1; tick <= 50; tick++) {
            context.runOnClient(mc -> {
                mc.player.setYRot(mc.player.getVehicle().getYRot());
                mc.player.setXRot(4.0F);
            });
            context.waitTick();
            if (tick == 30 || tick == 40) {
                context.takeScreenshot("ah6_gun_firing_t" + tick);
            }
        }
        context.getInput().releaseKey(options -> options.keyAttack);
        server.runCommand("ride @p dismount");
        server.runCommand("kill @e[type=!minecraft:player]");
        context.waitTicks(10);

        // The same gun seen from the side, fired by a husk at the controls.
        server.runCommand("execute positioned 100 -60 4 run summon jeg:ah6 ~ ~ ~ {Rotation:[0f,0f]}");
        server.runCommand("summon minecraft:husk 100 -60 4 {NoAI:1b,PersistenceRequired:1b,Silent:1b,Tags:[\"pilot\"]}");
        context.waitTicks(5);
        stock(server, "rifle_ammo", 64);
        server.runCommand("ride @e[tag=pilot,limit=1] mount @e[type=jeg:ah6,limit=1]");
        server.runCommand("tp @p 112 -59 12 120 5");
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
        context.waitTicks(20);
        for (int tick = 1; tick <= 30; tick++) {
            // Rounds as the AH-6's gun fires them: from its muzzle, flagged as a vehicle gun's.
            server.runOnServer(minecraftServer -> {
                for (Entity entity : minecraftServer.overworld().getAllEntities()) {
                    if (entity instanceof VehicleEntity vehicle && !vehicle.getPassengers().isEmpty()
                            && vehicle.getPassengers().get(0) instanceof net.minecraft.world.entity.LivingEntity pilot) {
                        var stats = ttv.migami.jeg.vehicle.util.VehicleWeaponStats.get(Reference.id("light_machine_gun"));
                        Vec3 direction = vehicle.getLookAngle().normalize();
                        var bullet = new ttv.migami.jeg.entity.BulletEntity(vehicle.level(), pilot, stats,
                                direction.scale(stats.projectileSpeed()), stats.damage(), false);
                        bullet.setVehicleRound();
                        bullet.setHelicopterGunRound();
                        bullet.initialisePosition(vehicle.aiWeaponMuzzlePosition(0));
                        vehicle.level().addFreshEntity(bullet);
                        bullet.sendTrailToClients((net.minecraft.server.level.ServerLevel) vehicle.level());
                    }
                }
            });
            context.waitTick();
            if (tick == 12 || tick == 18 || tick == 26) {
                context.takeScreenshot("ah6_gun_side_t" + tick);
            }
        }
        server.runCommand("kill @e[type=!minecraft:player]");
        context.waitTicks(10);
    }

    /** A passenger shooting their own rifle out of the AH-6: the tracer starts at the rifle. */
    private void ah6Passenger(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 200 -60 0 0 0");
        server.runCommand("execute as @p at @s run summon jeg:ah6 ~ ~ ~4 {Rotation:[0f,0f]}");
        server.runCommand("execute as @p at @s run summon minecraft:husk ~ ~ ~4 {NoAI:1b,PersistenceRequired:1b,Tags:[\"pilot\"]}");
        context.waitTicks(5);
        server.runCommand("ride @e[tag=pilot,limit=1] mount @e[type=jeg:ah6,limit=1,sort=nearest]");
        server.runCommand("ride @p mount @e[type=jeg:ah6,limit=1,sort=nearest]");
        context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
        int seat = context.computeOnClient(mc -> ((VehicleEntity) mc.player.getVehicle()).getSeatIndex(mc.player));
        server.runCommand("item replace entity @p weapon.mainhand with jeg:assault_rifle");
        for (CameraType camera : new CameraType[] {CameraType.FIRST_PERSON, CameraType.THIRD_PERSON_BACK}) {
            boolean firstPerson = camera == CameraType.FIRST_PERSON;
            context.runOnClient(mc -> mc.options.setCameraType(camera));
            context.waitTicks(20);
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(3);
            context.takeScreenshot("ah6_passenger_rifle_" + camera.name().toLowerCase());
            context.waitTicks(3);
            context.getInput().releaseKey(options -> options.keyAttack);
            String result = context.computeOnClient(mc -> {
                Vec3 muzzle = GunMuzzleTracker.muzzle(mc.player.getId(), firstPerson);
                Vec3 start = BulletTrailRenderer.lastTracerStart();
                Vec3 eye = mc.player.getEyePosition();
                return "muzzle=" + muzzle + " start=" + start + " eye=" + eye
                        + " startToMuzzle=" + (muzzle == null || start == null ? "n/a" : String.format("%.3f", start.distanceTo(muzzle)));
            });
            LOGGER.info("[vehicle-gun] ah6 passenger seat {} {}: {}", seat, camera, result);
            Vec3[] points = context.computeOnClient(mc -> new Vec3[] {
                    GunMuzzleTracker.muzzle(mc.player.getId(), firstPerson), BulletTrailRenderer.lastTracerStart()});
            check(points[0] != null && points[1] != null && points[0].distanceTo(points[1]) < 0.35D,
                    "Passenger's rifle tracer must start at the rifle (" + camera + "): " + result);
        }
        server.runCommand("ride @p dismount");
    }

    private static void stock(TestServerContext server, String item, int count) {
        server.runOnServer(minecraftServer -> {
            for (Entity entity : minecraftServer.overworld().getAllEntities()) {
                if (entity instanceof VehicleEntity vehicle) {
                    vehicle.vehicleInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(Reference.id(item)), count));
                }
            }
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
