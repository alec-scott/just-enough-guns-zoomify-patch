package ttv.migami.jeg.gametest;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The shared wheel driver on the refit LAV-150 (fleet plan section 3). The hull is moved on the server as driving moves
 * it; the client must roll the wheels by the distance (16 model pixels a block), steer the front pair right while it
 * turns right at the angle its wheelbase gives (about 25 degrees for this turn), the other way backing through the same
 * turn, and straighten them when it drives straight. Screenshots of the steered wheels from ahead.
 */
public class Lav150WheelsClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("time set noon");
            server.runCommand("gamemode spectator @a");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
            });
            int id = server.computeOnServer(s -> {
                VehicleEntity lav = ModEntities.LAV150.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                lav.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
                s.overworld().addFreshEntity(lav);
                return lav.getId();
            });
            context.waitTicks(30);

            // Straight ahead, 0.25 blocks a tick for 40 ticks: 10 blocks, 160 px of wheel travel.
            float travel0 = context.computeOnClient(mc -> lav(mc, id).wheelTravel(1.0F));
            this.drive(context, server, id, 0.25D, 0.0F, 40);
            context.waitTicks(10);
            float rolled = context.computeOnClient(mc -> lav(mc, id).wheelTravel(1.0F)) - travel0;
            float straightSteer = context.computeOnClient(mc -> lav(mc, id).wheelSteer(1.0F));
            LOGGER.info(String.format(Locale.ROOT, "[lav150-wheels] straight 10 blocks: wheels rolled %.1f px (160 expected), steer %.1f",
                    rolled, straightSteer));
            if (Math.abs(rolled - 160.0F) > 16.0F) failures.append("rolled ").append(rolled).append(" px over 10 blocks, not 160\n");
            if (Math.abs(straightSteer) > 2.0F) failures.append("wheels steered ").append(straightSteer).append(" driving straight\n");

            // Turning right, forwards: 0.15 blocks and 1.5 degrees a tick, tan(steer) = 2.68 x 0.0262 / 0.15, about 25 degrees.
            // From ahead and to the left of where the turn takes it (about -1.5, 14.5, heading south-west).
            server.runCommand("tp @a -8 -57.5 19 -125 15");
            float right = this.turn(context, server, id, 0.15D, 1.5F, 40, "lav150_wheels_steer_right");
            float back = this.turn(context, server, id, -0.15D, 1.5F, 40, null);
            this.drive(context, server, id, 0.25D, 0.0F, 15);
            float straightened = context.computeOnClient(mc -> lav(mc, id).wheelSteer(1.0F));
            LOGGER.info(String.format(Locale.ROOT, "[lav150-wheels] turning right %.1f deg, backing through the turn %.1f deg, straight again %.1f deg",
                    right, back, straightened));
            if (right < 15.0F || right > 35.0F) failures.append("turning right steered ").append(right).append(" deg, not about 25\n");
            if (back > -15.0F || back < -35.0F) failures.append("backing through a right turn steered ").append(back).append(" deg, not about -25\n");
            if (Math.abs(straightened) > 3.0F) failures.append("wheels stayed at ").append(straightened).append(" deg once straight\n");
        }
        if (failures.length() > 0) {
            LOGGER.error("[lav150-wheels] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOGGER.info("[lav150-wheels] COMPLETE");
    }

    private static VehicleEntity lav(net.minecraft.client.Minecraft mc, int id) {
        return (VehicleEntity) mc.level.getEntity(id);
    }

    /** Moves the hull along its heading by {@code step} and turns it by {@code yaw} each tick, as driving does. */
    private void drive(ClientGameTestContext context, TestServerContext server, int id, double step, float yaw, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            server.runOnServer(s -> {
                VehicleEntity lav = (VehicleEntity) s.overworld().getEntity(id);
                float heading = lav.getYRot() + yaw;
                Vec3 forward = Vec3.directionFromRotation(0.0F, heading);
                lav.snapTo(lav.getX() + forward.x * step, G, lav.getZ() + forward.z * step, heading, 0.0F);
            });
            context.waitTick();
        }
    }

    /** Turns for {@code ticks} and returns the client's mean steer over the last ten (a screenshot in between if named). */
    private float turn(ClientGameTestContext context, TestServerContext server, int id, double step, float yaw, int ticks, String shot) {
        this.drive(context, server, id, step, yaw, ticks - 10);
        if (shot != null) context.takeScreenshot(shot);
        float sum = 0.0F;
        for (int i = 0; i < 10; i++) {
            this.drive(context, server, id, step, yaw, 1);
            sum += context.computeOnClient(mc -> lav(mc, id).wheelSteer(1.0F));
        }
        return sum / 10.0F;
    }
}
