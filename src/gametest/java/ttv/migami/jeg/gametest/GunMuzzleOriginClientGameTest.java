package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.render.BulletTrailRenderer;
import ttv.migami.jeg.client.render.gun.GunMuzzleTracker;
import ttv.migami.jeg.item.GunItem;

/**
 * Tracers must leave the gun's muzzle, not the shooter's eyes: in first person, in third person and when watching
 * someone else shoot. The muzzle is read from the drawn gun model; it must sit forward of the eye, out to the gun
 * hand's side and below the eye line, and the newest tracer must start right on it.
 */
public class GunMuzzleOriginClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("muzzle origin report\n");
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 0");
            context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
            server.runCommand("execute as @p at @s run fill ~-4 ~ ~14 ~4 ~4 ~14 minecraft:white_concrete");
            server.runCommand("item replace entity @p weapon.mainhand with jeg:assault_rifle");
            context.waitTicks(40);

            this.checkLocal(context, report, CameraType.FIRST_PERSON, "first_person", true);
            this.checkLocal(context, report, CameraType.THIRD_PERSON_BACK, "third_person", false);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(10);
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(2);
            context.takeScreenshot("muzzle_third_person_front_firing");
            context.getInput().releaseKey(options -> options.keyAttack);

            this.checkObserved(context, server, report);
        }
        LOGGER.info(report.toString());
    }

    private void checkLocal(ClientGameTestContext context, StringBuilder report, CameraType camera, String label, boolean firstPerson) {
        context.runOnClient(mc -> mc.options.setCameraType(camera));
        context.waitTicks(20);
        Vec3 local = context.computeOnClient(mc -> relative(mc.player, GunMuzzleTracker.muzzle(mc.player.getId(), firstPerson)));
        report.append(String.format("%s muzzle (right, up, forward) = %s%n", label, local));
        LOGGER.info("{} muzzle (right, up, forward) = {}", label, local);
        context.getInput().holdKey(options -> options.keyAttack);
        for (int tick = 1; tick <= 6; tick++) {
            context.waitTick();
            if (tick == 2 || tick == 4) {
                context.takeScreenshot("muzzle_" + label + "_t" + tick);
            }
        }
        context.getInput().releaseKey(options -> options.keyAttack);
        Vec3 tracer = context.computeOnClient(mc -> {
            Vec3 muzzle = GunMuzzleTracker.muzzle(mc.player.getId(), firstPerson);
            Vec3 start = BulletTrailRenderer.lastTracerStart();
            return muzzle == null || start == null ? null : new Vec3(start.distanceTo(muzzle), 0.0D, 0.0D);
        });
        report.append(String.format("%s tracer start to muzzle = %s%n", label, tracer));
        LOGGER.info("{} tracer start to muzzle = {}", label, tracer);
        checkHeldMuzzle(label, local);
        check(tracer != null && tracer.x < 0.35D, label + ": tracer must start at the muzzle, was " + tracer);
        context.waitTicks(10);
    }

    /** A zombie shoots from beside the player; its tracer and flash must leave its gun, not its head. */
    private void checkObserved(ClientGameTestContext context, TestServerContext server, StringBuilder report) {
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        server.runCommand("execute as @p at @s run summon minecraft:husk ~3 ~ ~2 {NoAI:1b,Silent:1b,PersistenceRequired:1b,"
                + "Rotation:[0f,0f],Tags:[\"muzzle_test\"],HandItems:[{id:\"jeg:assault_rifle\",count:1}]}");
        server.runCommand("item replace entity @e[tag=muzzle_test,limit=1] weapon.mainhand with jeg:assault_rifle");
        // Stand off to the side and look at the zombie's gun.
        server.runCommand("tp @p -3 ~ 3 -90 10");
        context.waitTicks(30);
        int zombieId = server.computeOnServer(s -> {
            for (ServerLevel level : s.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity.entityTags().contains("muzzle_test")) {
                        return entity.getId();
                    }
                }
            }
            return -1;
        });
        check(zombieId >= 0, "zombie shooter was not spawned");
        Vec3 local = context.computeOnClient(mc -> relative((LivingEntity) mc.level.getEntity(zombieId),
                GunMuzzleTracker.muzzle(zombieId, false)));
        report.append(String.format("observed zombie muzzle (right, up, forward) = %s%n", local));
        checkHeldMuzzle("observed", local);
        for (int shot = 0; shot < 6; shot++) {
            server.runOnServer(s -> {
                for (ServerLevel level : s.getAllLevels()) {
                    for (Entity entity : level.getAllEntities()) {
                        if (entity.entityTags().contains("muzzle_test") && entity instanceof LivingEntity living
                                && living.getMainHandItem().getItem() instanceof GunItem gun) {
                            gun.fireDirectionally(level, living, living.getMainHandItem(), living.getLookAngle());
                        }
                    }
                }
            });
            context.waitTick();
            if (shot == 1 || shot == 4) {
                context.takeScreenshot("muzzle_observed_shot" + shot);
            }
        }
        Vec3 tracer = context.computeOnClient(mc -> {
            Vec3 muzzle = GunMuzzleTracker.muzzle(zombieId, false);
            Vec3 start = BulletTrailRenderer.lastTracerStart();
            return muzzle == null || start == null ? null : new Vec3(start.distanceTo(muzzle), 0.0D, 0.0D);
        });
        report.append(String.format("observed tracer start to muzzle = %s%n", tracer));
        check(tracer != null && tracer.x < 0.35D, "observed: tracer must start at the zombie's muzzle, was " + tracer);
    }

    private static void checkHeldMuzzle(String label, Vec3 local) {
        check(local != null, label + ": the gun's muzzle was never tracked");
        check(local.z > 0.3D && local.z < 2.2D, label + ": muzzle should be ahead of the eye, was " + local);
        // Two-handed aiming poses hold the gun across the chest, so third-person barrels can sit near the centre line.
        check(Math.abs(local.x) < 0.8D, label + ": muzzle should be near the shooter's shoulders, was " + local);
        check(local.y < 0.0D && local.y > -0.9D, label + ": muzzle should be below the eye line, was " + local);
    }

    /** The point in the shooter's eye frame: (right, up, forward). */
    private static Vec3 relative(LivingEntity shooter, Vec3 point) {
        if (shooter == null || point == null) {
            return null;
        }
        float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 eye = shooter.getEyePosition(partial);
        Vec3 look = shooter.getViewVector(partial).normalize();
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        Vec3 up = right.cross(look);
        Vec3 d = point.subtract(eye);
        return new Vec3(d.dot(right), d.dot(up), d.dot(look));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
