package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.entity.ChinookEntity;
import ttv.migami.jeg.vehicle.entity.ChinookSling;
import ttv.migami.jeg.vehicle.entity.SlingRope;

/**
 * The sling's ropes have weight (the user, 2026-11-21: "the rope should have gravity attached to it so it naturally
 * slings around"): as the Chinook moves off, the free legs and their hooks trail behind the ring and the pendant bows,
 * then they swing on forward as it stops. Watched from a camera
 * beside the Chinook (screenshots rope_*), and measured on the client's ropes.
 */
public class Ch47SlingRopeClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @p 0.5 -45 0.5 0 0");
            server.runCommand("summon jeg:ch47 0.5 -45 0.5");
            server.runCommand("ride @p mount @e[type=jeg:ch47,limit=1]");
            context.waitFor(mc -> mc.player.getVehicle() instanceof ChinookEntity);
            context.waitTicks(60);
            context.getInput().pressKey(KeyBindings.VEHICLE_TOW);
            context.waitTicks(90);
            // A camera beside the Chinook's right, looking at the rope under it.
            server.runCommand("summon armor_stand 16.5 -50 6.5 {NoGravity:1b,Invisible:1b,Marker:1b,Tags:[\"rope_cam\"],Rotation:[90f,8f]}");
            context.waitTicks(5);
            // Tags aren't sent to clients: the camera is found by its id on the server.
            int eye = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(net.minecraft.world.entity.Entity.class,
                    new net.minecraft.world.phys.AABB(10, -60, 0, 20, -40, 12), e -> e.entityTags().contains("rope_cam")).getFirst().getId());
            context.runOnClient(mc -> {
                mc.setCameraEntity(mc.level.getEntity(eye));
                mc.options.hideGui = true;
            });
            context.waitTicks(10);
            double hangSag = context.computeOnClient(mc -> sag(chinook(mc).sling().pendantRope()));
            context.takeScreenshot("rope_hover");

            // Off forward for two seconds: the legs trail and the pendant bows; then hands off as it slows.
            double maxTrail = 0.0D, maxBow = 0.0D;
            context.getInput().holdKey(options -> options.keyUp);
            for (int i = 0; i < 40; i++) {
                context.waitTick();
                double[] m = context.computeOnClient(mc -> measure(chinook(mc)));
                maxTrail = Math.max(maxTrail, m[0]);
                maxBow = Math.max(maxBow, m[1]);
                if (i == 20) {
                    context.takeScreenshot("rope_moving_off");
                }
            }
            context.getInput().releaseKey(options -> options.keyUp);
            double maxSwingBack = 0.0D;
            for (int i = 0; i < 60; i++) {
                context.waitTick();
                double[] m = context.computeOnClient(mc -> measure(chinook(mc)));
                maxSwingBack = Math.max(maxSwingBack, m[2]);
                if (i == 25) {
                    context.takeScreenshot("rope_stopping");
                }
            }
            context.runOnClient(mc -> {
                mc.setCameraEntity(mc.player);
                mc.options.hideGui = false;
            });
            LOG.info("[ch47-sling-rope] hanging sag {} m; moving off: legs trail up to {} m, pendant bows {} m; stopping: legs swing forward {} m",
                    hangSag, maxTrail, maxBow, maxSwingBack);
            StringBuilder failures = new StringBuilder();
            int state = server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ChinookEntity.class,
                    new net.minecraft.world.phys.AABB(-200, -80, -200, 200, 40, 200)).getFirst().sling().state());
            if (state != ChinookSling.HANGING) failures.append("the sling should be out and empty, state ").append(state).append('\n');
            // Hanging still it is straight (a vertical rope has nowhere to sag); it bows only when the Chinook moves.
            if (maxTrail < 0.15D) failures.append("moving off, the free legs should trail behind the ring, at most ").append(maxTrail).append(" m\n");
            if (maxBow < 0.1D) failures.append("moving off, the pendant should bow, at most ").append(maxBow).append(" m\n");
            if (maxSwingBack < 0.05D) failures.append("stopping, the legs should swing on forward, at most ").append(maxSwingBack).append(" m\n");
            if (!failures.isEmpty()) {
                throw new AssertionError("CH-47 sling ropes:\n" + failures);
            }
        }
    }

    private static ChinookEntity chinook(net.minecraft.client.Minecraft mc) {
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ChinookEntity chinook) {
                return chinook;
            }
        }
        throw new AssertionError("no Chinook");
    }

    /** How far the rope's middle hangs off the straight line between its ends. */
    private static double sag(SlingRope rope) {
        int last = rope.size() - 1;
        Vec3 a = rope.node(0, 1.0F), b = rope.node(last, 1.0F), mid = rope.node(last / 2, 1.0F);
        Vec3 ab = b.subtract(a);
        double t = ab.lengthSqr() < 1.0E-9D ? 0.0D : mid.subtract(a).dot(ab) / ab.lengthSqr();
        return mid.distanceTo(a.add(ab.scale(t)));
    }

    /**
     * {how far the legs' hooks trail behind the ring (against the Chinook's heading), the pendant's bow, how far the
     * hooks have swung on ahead of the ring}.
     */
    private static double[] measure(ChinookEntity chinook) {
        ChinookSling sling = chinook.sling();
        Vec3 ring = sling.pendantRope().node(sling.pendantRope().size() - 1, 1.0F);
        double yaw = Math.toRadians(chinook.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        double behind = 0.0D, ahead = 0.0D;
        for (SlingRope leg : sling.legRopes()) {
            double along = leg.node(leg.size() - 1, 1.0F).subtract(ring).dot(forward);
            behind = Math.max(behind, -along);
            ahead = Math.max(ahead, along);
        }
        return new double[]{behind, sag(sling.pendantRope()), ahead};
    }
}
