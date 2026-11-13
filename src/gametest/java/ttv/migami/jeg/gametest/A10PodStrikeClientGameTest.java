package ttv.migami.jeg.gametest;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

/**
 * The A-10's targeting pod as the missiles' sight. Parked, zoomed in, the pilot looks off to the side with the pod: a
 * Maverick fired then goes to what the pod is on (an iron golem well off the nose), and one fired at bare ground goes to
 * that spot. The gun, fired with the pod still looking aside, goes down the nose, at its marker. Also: the jet's
 * inventory has five rows, and a look through the third view's wing camera.
 */
public class A10PodStrikeClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("a10 pod strike report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 -60 0 0 0");
            server.runCommand("forceload add -96 -16 96 160");
            context.waitTicks(5);
            server.runCommand("summon jeg:a10 0 -60 0 {Rotation:[0f,0f]}");
            context.waitTicks(5);
            server.runCommand("ride @p mount @e[type=jeg:a10,limit=1,sort=nearest]");
            context.waitFor(mc -> vehicle(mc) != null);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            server.runOnServer(minecraftServer -> {
                if (minecraftServer.getPlayerList().getPlayers().getFirst().getVehicle() instanceof VehicleEntity jet) {
                    jet.addAmmoForAi(Reference.id("rifle_ammo"), 300);
                    jet.addAmmoForAi(Reference.id("medium_anti_ground_missile"), 4);
                    jet.reloadAiVehicleWeapons();
                }
            });
            context.waitTicks(40);

            int slots = context.computeOnClient(mc -> vehicle(mc).vehicleData().defaults().containerType().slots());
            report.append("inventory: ").append(slots).append(" slots").append(System.lineSeparator());
            if (slots != 45) {
                failures.append("the A-10's inventory has ").append(slots).append(" slots, not five rows (45)").append(System.lineSeparator());
            }

            // A golem 60 blocks ahead and 25 to the right: 23 degrees off the nose.
            Vec3 golemAt = new Vec3(-25.5D, -60.0D, 60.5D);
            UUID golem = server.computeOnServer(minecraftServer -> {
                var entity = EntityType.IRON_GOLEM.create(minecraftServer.overworld(), EntitySpawnReason.COMMAND);
                entity.setNoAi(true);
                entity.snapTo(golemAt.x, golemAt.y, golemAt.z, 0.0F, 0.0F);
                minecraftServer.overworld().addFreshEntity(entity);
                return entity.getUUID();
            });
            context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitTicks(5);
            Vec3 struck = this.podShot(context, server, golemAt.add(0.0D, 1.4D, 0.0D), null);
            boolean golemHit = server.computeOnServer(minecraftServer ->
                    !(minecraftServer.overworld().getEntity(golem) instanceof LivingEntity living) || !living.isAlive() || living.getHealth() < living.getMaxHealth());
            report.append(String.format(Locale.ROOT, "pod on the golem: missile ended %.1f blocks from it, golem hit %s%n",
                    struck == null ? -1.0D : struck.distanceTo(golemAt), golemHit));
            if (struck == null || struck.distanceTo(golemAt) > 6.0D || !golemHit) {
                failures.append("a Maverick fired with the pod on a golem 23 degrees off the nose did not hit it").append(System.lineSeparator());
            }

            // Bare ground 70 ahead and 30 to the left.
            Vec3 spot = new Vec3(30.5D, -60.0D, 70.5D);
            context.waitTicks(80);
            Vec3 landed = this.podShot(context, server, spot, null);
            // The Maverick's warhead cooks off after it: smaller blasts around the crater over the next second or so.
            int secondaries = server.computeOnServer(minecraftServer -> ttv.migami.jeg.vehicle.projectile.SecondaryBlasts.pending());
            context.waitTicks(8);
            context.takeScreenshot("a10_secondary_blasts");
            context.waitTicks(40);
            int left = server.computeOnServer(minecraftServer -> ttv.migami.jeg.vehicle.projectile.SecondaryBlasts.pending());
            // And the heavier AGM-65G's: more of them, bigger, further out.
            int heavy = server.computeOnServer(minecraftServer -> {
                ttv.migami.jeg.vehicle.projectile.SecondaryBlasts.afterImpact(minecraftServer.overworld(), Reference.id("vehicle_agm65g_missile"),
                        new Vec3(-30.5D, -60.0D, 70.5D), net.minecraft.util.RandomSource.create(3L));
                return ttv.migami.jeg.vehicle.projectile.SecondaryBlasts.pending();
            });
            context.waitTicks(12);
            context.takeScreenshot("a10_secondary_blasts_heavy");
            context.waitTicks(40);
            int heavyLeft = server.computeOnServer(minecraftServer -> ttv.migami.jeg.vehicle.projectile.SecondaryBlasts.pending());
            report.append(String.format(Locale.ROOT, "secondary blasts: AGM-65D %d after impact (%d left 2.4 s on), AGM-65G %d (%d left)%n",
                    secondaries, left, heavy, heavyLeft));
            if (secondaries < 3 || left != 0 || heavy < 5 || heavyLeft != 0) {
                failures.append("the Mavericks' secondary blasts did not all go off after the impact").append(System.lineSeparator());
            }
            report.append(String.format(Locale.ROOT, "pod on bare ground: missile ended %.1f blocks from the spot%n", landed == null ? -1.0D : landed.distanceTo(spot)));
            if (landed == null || landed.distanceTo(spot) > 4.0D) {
                failures.append("a Maverick fired with the pod on bare ground did not go to that spot").append(System.lineSeparator());
            }

            // Fired at one spot, then the pod swung to another while it flies: it follows the crosshair there.
            Vec3 first = new Vec3(-20.5D, -60.0D, 90.5D);
            Vec3 second = new Vec3(15.5D, -60.0D, 85.5D);
            context.waitTicks(140);
            Vec3 followed = this.podShot(context, server, first, second);
            report.append(String.format(Locale.ROOT, "pod swung after launch: missile ended %.1f blocks from the new spot (%.1f from the first)%n",
                    followed == null ? -1.0D : followed.distanceTo(second), followed == null ? -1.0D : followed.distanceTo(first)));
            if (followed == null || followed.distanceTo(second) > 5.0D) {
                failures.append("a Maverick did not follow the pod's crosshair to a new spot after launch").append(System.lineSeparator());
            }

            // The gun, with the pod still looking aside: down the nose.
            context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitTicks(3);
            context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitTicks(5);
            context.getInput().holdMouse(1);
            for (int tick = 0; tick < 10; tick++) {
                this.lookAt(context, spot);
                context.waitTick();
            }
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(6);
            double along = server.computeOnServer(minecraftServer -> {
                var player = minecraftServer.getPlayerList().getPlayers().getFirst();
                if (!(player.getVehicle() instanceof VehicleEntity jet)) {
                    return -2.0D;
                }
                org.joml.Vector3d nose = jet.poseRotation(1.0F).transform(new org.joml.Vector3d(0.0D, 0.0D, 1.0D));
                double worst = 2.0D;
                for (BulletEntity bullet : player.level().getEntitiesOfClass(BulletEntity.class, jet.getBoundingBox().inflate(200.0D))) {
                    Vec3 v = bullet.getDeltaMovement().normalize();
                    worst = Math.min(worst, v.x * nose.x + v.y * nose.y + v.z * nose.z);
                }
                return worst;
            });
            context.getInput().releaseKey(options -> options.keyAttack);
            context.getInput().releaseMouse(1);
            report.append(String.format(Locale.ROOT, "gun with the pod looking aside: rounds along the nose, least cos %.3f%n", along));
            if (along < 0.99D) {
                failures.append("the gun did not fire down the nose while the pod looked aside (cos ").append(along).append(")").append(System.lineSeparator());
            }

            // The third view: the camera on the left wing.
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(5);
            context.takeScreenshot("a10_wing_camera");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        }
        LOGGER.info("{}", report);
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    /** Zoomed in, the pod held on a point, a missile fired (the pod moved to {@code then} 6 ticks later); where it ended. */
    private Vec3 podShot(ClientGameTestContext context, TestServerContext server, Vec3 point, Vec3 then) {
        context.getInput().holdMouse(1);
        for (int tick = 0; tick < 15; tick++) {
            this.lookAt(context, point);
            context.waitTick();
        }
        context.takeScreenshot("a10_pod_designating");
        Map<Integer, Vec3> last = new HashMap<>();
        context.getInput().holdKeyFor(options -> options.keyAttack, 2);
        Vec3 ended = null;
        for (int tick = 0; tick < 120; tick++) {
            this.lookAt(context, then != null && tick >= 6 ? then : point);
            Map<Integer, Vec3> now = server.computeOnServer(minecraftServer -> {
                Map<Integer, Vec3> found = new HashMap<>();
                var player = minecraftServer.getPlayerList().getPlayers().getFirst();
                for (VehicleMissileEntity missile : player.level().getEntitiesOfClass(VehicleMissileEntity.class, player.getBoundingBox().inflate(300.0D))) {
                    found.put(missile.getId(), missile.position());
                }
                return found;
            });
            for (var entry : last.entrySet()) {
                if (!now.containsKey(entry.getKey())) {
                    ended = entry.getValue();
                }
            }
            last = now;
            if (ended != null) {
                break;
            }
            context.waitTick();
        }
        context.getInput().releaseMouse(1);
        return ended;
    }

    /** Turns the player's view (the pod, while zoomed) to look at a point from the camera. */
    private void lookAt(ClientGameTestContext context, Vec3 point) {
        context.runOnClient(mc -> {
            Vec3 eye = mc.gameRenderer.getMainCamera().position();
            Vec3 to = point.subtract(eye);
            float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(to.y, to.horizontalDistance()));
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
        });
    }

    private static VehicleEntity vehicle(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof VehicleEntity vehicle ? vehicle : null;
    }
}
