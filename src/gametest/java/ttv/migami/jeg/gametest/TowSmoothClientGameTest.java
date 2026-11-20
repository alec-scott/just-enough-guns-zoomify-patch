package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.HowitzerEntity;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * A gun towed by the FSV-6 moves smoothly (the user, 2026-11-19: "can the vehicle being towed by the FSV not be glitchy
 * and jumpy but smooth"). The player drives the truck with the howitzer on the hitch, straight, then turning both ways;
 * every tick, at three points between ticks, the drawn gun's tow eye is measured against the drawn pintle: the bar
 * between them must keep its length (it stretched and snapped as the server's position packets arrived). The same
 * measure from the gun's interpolated network position is logged beside it, as it was drawn before.
 */
public class TowSmoothClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private int gunId;
    private int truckId;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            server.runCommand("forceload add -96 -96 96 96");
            server.runOnServer(s -> {
                HowitzerEntity gun = ModEntities.HOWITZER.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                gun.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
                s.overworld().addFreshEntity(gun);
                this.gunId = gun.getId();
                SupportVehicleEntity truck = ModEntities.TRUCK.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                truck.snapTo(0.5D, G, -9.0D, 180.0F, 0.0F);
                truck.addEnergy(truck.maxVehicleEnergy());
                s.overworld().addFreshEntity(truck);
                this.truckId = truck.getId();
            });
            context.waitTicks(10);
            server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(s.overworld().getEntity(this.truckId), true, true));
            context.waitTicks(SupportVehicleEntity.START_TICKS + 10);
            context.getInput().pressKey(options -> KeyBindings.VEHICLE_TOW);
            context.waitTicks(6);
            boolean hitched = server.computeOnServer(s -> s.overworld().getEntity(this.truckId) instanceof SupportVehicleEntity t
                    && t.towedEntityId() == this.gunId);
            if (!hitched) throw new AssertionError("the FSV-6 did not hitch the howitzer");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));

            List<Double> drawn = new ArrayList<>();
            List<Double> network = new ArrayList<>();
            context.getInput().holdKey(options -> options.keyUp);
            for (int tick = 0; tick < 200; tick++) {
                if (tick == 70) context.getInput().holdKey(options -> options.keyLeft);
                if (tick == 120) {
                    context.getInput().releaseKey(options -> options.keyLeft);
                    context.getInput().holdKey(options -> options.keyRight);
                }
                if (tick == 100) context.takeScreenshot("tow_smooth_turning");
                context.waitTick();
                if (tick < 20) continue; // under way first
                double[] sample = context.computeOnClient(this::measure);
                if (sample == null) continue;
                for (int i = 0; i < 3; i++) {
                    drawn.add(sample[i]);
                    network.add(sample[3 + i]);
                }
            }
            context.getInput().releaseKey(options -> options.keyRight);
            context.getInput().releaseKey(options -> options.keyUp);
            double[] d = stats(drawn);
            double[] n = stats(network);
            double moved = server.computeOnServer(s -> s.overworld().getEntity(this.gunId).position().distanceTo(new Vec3(0.5D, G, 0.5D)));
            LOG.info(String.format(Locale.ROOT, "[tow-smooth] %d samples; bar length as drawn: mean %.3f, spread (max - min) %.3f, "
                            + "largest step between samples %.3f; from the network position (before): mean %.3f, spread %.3f, largest step %.3f; "
                            + "the gun moved %.1f blocks", drawn.size(), d[0], d[1], d[2], n[0], n[1], n[2], moved));
            if (drawn.size() < 300) failures.append("too few samples: ").append(drawn.size()).append('\n');
            if (moved < 20.0D) failures.append("the gun was not towed: moved ").append(moved).append('\n');
            if (d[1] > 0.08D) failures.append("the bar's length still varies as drawn: ").append(d[1]).append(" blocks\n");
            if (d[2] > 0.04D) failures.append("the drawn gun still jumps between frames: ").append(d[2]).append(" blocks\n");
        }
        if (failures.length() > 0) {
            LOG.error("[tow-smooth] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[tow-smooth] COMPLETE");
    }

    /** The bar's horizontal length at three points between ticks: as drawn, then from the network position. */
    private double[] measure(Minecraft mc) {
        if (!(mc.level.getEntity(this.truckId) instanceof SupportVehicleEntity truck)
                || !(mc.level.getEntity(this.gunId) instanceof VehicleEntity gun) || gun.towEye() == null) return null;
        double[] out = new double[6];
        for (int i = 0; i < 3; i++) {
            float partial = i / 3.0F;
            Vec3 pin = SupportVehicleEntity.PINTLE;
            Vec3 pintle = SupportRig.world(truck.getPosition(partial), Mth.rotLerp(partial, truck.yRotO, truck.getYRot()), pin.x, pin.y, pin.z);
            var pose = SupportVehicleEntity.clientTowPose(gun, partial);
            Vec3 eye = gun.towEye();
            Vec3 lerped = gun.getPosition(partial);
            Vec3 drawnAt = pose == null ? lerped : new Vec3(pose.position().x, lerped.y, pose.position().z);
            float drawnYaw = pose == null ? Mth.rotLerp(partial, gun.yRotO, gun.getYRot()) : pose.yaw();
            Vec3 drawnEye = SupportRig.world(drawnAt, drawnYaw, eye.x * 16, eye.y * 16, -eye.z * 16);
            Vec3 netEye = SupportRig.world(lerped, Mth.rotLerp(partial, gun.yRotO, gun.getYRot()), eye.x * 16, eye.y * 16, -eye.z * 16);
            out[i] = Math.hypot(drawnEye.x - pintle.x, drawnEye.z - pintle.z);
            out[3 + i] = Math.hypot(netEye.x - pintle.x, netEye.z - pintle.z);
        }
        return out;
    }

    /** Mean, spread (max - min) and the largest change between consecutive samples. */
    private static double[] stats(List<Double> values) {
        if (values.isEmpty()) return new double[]{0, 0, 0};
        double sum = 0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE, step = 0;
        for (int i = 0; i < values.size(); i++) {
            double v = values.get(i);
            sum += v;
            min = Math.min(min, v);
            max = Math.max(max, v);
            if (i > 0) step = Math.max(step, Math.abs(v - values.get(i - 1)));
        }
        return new double[]{sum / values.size(), max - min, step};
    }
}
