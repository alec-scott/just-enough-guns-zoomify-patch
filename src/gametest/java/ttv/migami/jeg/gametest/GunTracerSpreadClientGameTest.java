package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.render.BulletTrailRenderer;
import ttv.migami.jeg.entity.BulletEntity;

/**
 * A hand-held gun's tracers must leave the barrel where the barrel shows on screen, whatever the field of view: the
 * first-person hand is drawn at a fixed 70 degrees while the world follows the FOV setting, sprinting and aiming. The
 * tracer start is projected with the world's camera and must land on the screen where the muzzle is drawn. And a
 * player's guns must group tightly: sustained hip fire with an assault rifle stays within a few degrees.
 */
public class GunTracerSpreadClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    private final List<double[]> shots = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof BulletEntity bullet && bullet.getOwner() instanceof net.minecraft.world.entity.player.Player owner) {
                Vec3 look = owner.getViewVector(1.0F).normalize();
                Vec3 dir = bullet.getDeltaMovement().normalize();
                double angle = Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, look.dot(dir)))));
                synchronized (this.shots) {
                    this.shots.add(new double[] {angle});
                }
            }
        });
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            server.runCommand("fill -12 -60 24 12 -50 24 minecraft:white_concrete");
            server.runCommand("item replace entity @p weapon.mainhand with jeg:assault_rifle");
            context.runOnClient(mc -> {
                mc.gui.getChat().clearMessages(true);
                mc.options.hideGui = false;
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            });
            context.waitTicks(40);

            for (int fov : new int[] {70, 100}) {
                context.runOnClient(mc -> mc.options.fov().set(fov));
                context.waitTicks(10);
                this.fire(context, "fov" + fov);
            }
            // The hand looks the same at any FOV, so the tracer must start on the same spot of the screen.
            double[] narrow = this.lastNdc.get(0);
            double[] wide = this.lastNdc.get(1);
            double moved = Math.hypot(wide[0] - narrow[0], wide[1] - narrow[1]);
            LOGGER.info("[gun-tracer] tracer start moved {} (ndc) between FOV 70 and 100", String.format("%.3f", moved));
            check(moved < 0.08D, "the tracer should leave the drawn muzzle at any FOV; its start moved " + moved + " on screen");

            synchronized (this.shots) {
                this.shots.clear();
            }
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(40);
            context.getInput().releaseKey(options -> options.keyAttack);
            context.waitTicks(5);
            double max = 0.0D;
            double sum = 0.0D;
            int count;
            synchronized (this.shots) {
                count = this.shots.size();
                for (double[] shot : this.shots) {
                    max = Math.max(max, shot[0]);
                    sum += shot[0];
                }
            }
            LOGGER.info("[gun-spread] assault rifle sustained hip fire: {} rounds, mean {} deg, max {} deg",
                    count, String.format("%.2f", count == 0 ? 0.0D : sum / count), String.format("%.2f", max));
            check(count >= 8, "the rifle should have fired, rounds=" + count);
            // Stock tuning reached 10.8 degrees in sustained hip fire; at 40% the cone is 4.3.
            check(max <= 4.5D, "sustained hip fire should stay within 4.5 degrees, max was " + max);
        }
    }

    /** Fires a short burst, screenshotting the tracers as they leave, and checks each tracer starts on the drawn muzzle. */
    private void fire(ClientGameTestContext context, String label) {
        context.getInput().holdKey(options -> options.keyAttack);
        for (int tick = 1; tick <= 4; tick++) {
            context.waitTick();
            context.takeScreenshot("tracer_" + label + "_t" + tick);
        }
        double[] screen = context.computeOnClient(mc -> {
            Vec3 start = BulletTrailRenderer.lastTracerStart();
            if (start == null) {
                return null;
            }
            var camera = mc.gameRenderer.getMainCamera();
            Vec3 rel = start.subtract(camera.position());
            Matrix4f viewProjection = camera.getViewRotationProjectionMatrix(new Matrix4f());
            Vector4f clip = viewProjection.transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0F));
            return new double[] {clip.x / clip.w, clip.y / clip.w, camera.getFov()};
        });
        context.getInput().releaseKey(options -> options.keyAttack);
        context.waitTicks(10);
        check(screen != null, label + ": no tracer was drawn");
        LOGGER.info("[gun-tracer] {}: tracer start on screen (ndc x, y) = ({}, {}) at world fov {}", label,
                String.format("%.3f", screen[0]), String.format("%.3f", screen[1]), String.format("%.1f", screen[2]));
        // The muzzle is drawn low and to the right of the crosshair; the tracer must start there on screen.
        check(screen[0] > 0.05D && screen[0] < 0.9D && screen[1] < -0.05D && screen[1] > -0.95D,
                label + ": the tracer should start low and right, on the gun: " + screen[0] + ", " + screen[1]);
        this.lastNdc.add(screen);
    }

    private final List<double[]> lastNdc = new ArrayList<>();

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
