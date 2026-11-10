package ttv.migami.jeg.gametest;

import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.render.MissileBlastRenderer;
import ttv.migami.jeg.vehicle.projectile.DistantTerrainRefresh;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

/**
 * How long a missile's damage to a building takes to show for a player who is too far away to have its chunks: on yftm
 * those are drawn by Voxy from terrain sent by Voxy Server Side, which notes a change when the chunk is saved and tells
 * players about changes on a timer (10 seconds). {@link DistantTerrainRefresh} hurries that after a blast. Pack runs
 * only (-PwithPack); without Voxy Server Side there is nothing to measure.
 * <p>
 * Two black walls, 17 blocks square and 4 thick, hang in the sky 400 blocks east and west. A 9M-120 is flown into the
 * east one with the hurrying off, for comparison, and into the west one with it on. The blast's smoke is cleared away
 * so the wall can be watched; the hole has shown once sky is seen through the middle of the wall. A 70 mm and an 80 mm
 * helicopter rocket then hit the west wall's corners: their damage has to be pushed as promptly as the missile's.
 */
public class DistantDamageDelayClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int ALTITUDE = 240;
    private static final int TARGET_X = 400;
    /** The yftm server's view and simulation distance. */
    private static final int RENDER_DISTANCE = 12;
    private static final int SIMULATION_DISTANCE = 10;
    private static final int ZOOM_FOV = 30;
    private static final int WATCH_TICKS = 400;
    private static final int LOOK_EVERY_TICKS = 4;
    /** With the hurrying on, the hole has to show within this many ticks of the blast. */
    private static final int SHOWN_WITHIN_TICKS = 110;
    /** With the hurrying on, a changed column has to be pushed within this many ticks of being noted. */
    private static final int PUSHED_WITHIN_TICKS = 25;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("lss") || !FabricLoader.getInstance().isModLoaded("voxy")) {
            LOGGER.info("distant damage delay: skipped, needs Voxy and Voxy Server Side (-PwithPack)");
            return;
        }
        int[] previous = context.computeOnClient(mc -> new int[]{mc.options.renderDistance().get(),
                mc.options.simulationDistance().get(), mc.options.fov().get(), mc.options.hideGui ? 1 : 0});
        context.runOnClient(mc -> {
            mc.options.renderDistance().set(RENDER_DISTANCE);
            mc.options.simulationDistance().set(SIMULATION_DISTANCE);
            mc.options.fov().set(ZOOM_FOV);
            mc.options.hideGui = true;
        });
        StringBuilder report = new StringBuilder("distant damage delay report\n");
        int[] usual;
        int[] hurried;
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).create()) {
            TestServerContext server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("tp @a 0 " + ALTITUDE + " 0 -90 0");
            context.waitTicks(60);

            // The buildings: chunks held loaded just long enough to place them, then let go so they save and unload.
            server.runCommand("forceload add 384 -16 415 15");
            server.runCommand("forceload add -416 -16 -385 15");
            context.waitTicks(60);
            server.runCommand(String.format("fill %d %d -8 %d %d 8 minecraft:black_concrete",
                    TARGET_X, ALTITUDE - 8, TARGET_X + 3, ALTITUDE + 8));
            server.runCommand(String.format("fill %d %d -8 %d %d 8 minecraft:black_concrete",
                    -TARGET_X - 3, ALTITUDE - 8, -TARGET_X, ALTITUDE + 8));
            context.waitTicks(10);
            server.runCommand("forceload remove 384 -16 415 15");
            server.runCommand("forceload remove -416 -16 -385 15");

            // Voxy Server Side only serves a singleplayer world once it is open to LAN.
            boolean published = context.computeOnClient(mc ->
                    mc.getSingleplayerServer().publishServer(GameType.SPECTATOR, true, HttpUtil.getAvailablePort()));
            report.append("opened to LAN: ").append(published).append('\n');

            usual = this.shoot(context, server, report, 1, false);
            hurried = this.shoot(context, server, report, -1, true);
            this.rockets(context, server, report, failures);
        } finally {
            DistantTerrainRefresh.enabled = true;
            context.runOnClient(mc -> {
                mc.options.renderDistance().set(previous[0]);
                mc.options.simulationDistance().set(previous[1]);
                mc.options.fov().set(previous[2]);
                mc.options.hideGui = previous[3] == 1;
            });
        }
        LOGGER.info(report.toString());
        if (hurried[0] < 0 || hurried[0] > SHOWN_WITHIN_TICKS) {
            failures.append(String.format("the hole in the far wall showed %d ticks after the blast (limit %d; on the usual timer: %d)%n",
                    hurried[0], SHOWN_WITHIN_TICKS, usual[0]));
        }
        if (hurried[1] < 0 || hurried[2] < 0 || hurried[2] - hurried[1] > PUSHED_WITHIN_TICKS) {
            failures.append(String.format("the missile's damage was noted at +%d and pushed at +%d (limit %d ticks apart)%n",
                    hurried[1], hurried[2], PUSHED_WITHIN_TICKS));
        }
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    /** Helicopter rockets into the corners of the west wall, with the hurrying on: noted and pushed promptly. */
    private void rockets(ClientGameTestContext context, TestServerContext server, StringBuilder report, StringBuilder failures) {
        String[] weapons = {"vehicle_70mm_rocket", "vehicle_80mm_rocket"};
        for (int shot = 0; shot < weapons.length; shot++) {
            String weapon = weapons[shot];
            double z = shot == 0 ? -6.5D : 6.5D;
            // Let the last hurried push's one second gap and the chunks' save cooldown from the shot before run out.
            context.waitTicks(220);
            var stats = ttv.migami.jeg.vehicle.util.VehicleWeaponStats.get(Reference.id(weapon));
            AtomicReference<ttv.migami.jeg.entity.BulletEntity> fired = new AtomicReference<>();
            server.runOnServer(s -> {
                ServerLevel level = s.overworld();
                var rocket = new ttv.migami.jeg.entity.BulletEntity(level, s.getPlayerList().getPlayers().get(0), stats,
                        new Vec3(-stats.projectileSpeed(), 0.0D, 0.0D));
                rocket.initialisePosition(new Vec3(-8.0D, ALTITUDE + 6.0D, z));
                level.addFreshEntity(rocket);
                fired.set(rocket);
            });
            double[] end = new double[]{0.0D, 0.0D};
            for (int tick = 0; tick < 200 && end[1] == 0.0D; tick++) {
                context.waitTick();
                end = server.computeOnServer(s -> new double[]{fired.get().getX(), fired.get().isRemoved() ? 1.0D : 0.0D});
            }
            MARKS.clear();
            int markedAt = -1;
            int pushedAt = -1;
            for (int tick = 1; tick <= 240 && pushedAt < 0; tick++) {
                context.waitTick();
                long[] counts = server.computeOnServer(s -> serverCounts());
                if (markedAt < 0 && wallMarked(-1)) {
                    markedAt = tick;
                }
                if (markedAt >= 0 && counts[2] == 0) {
                    pushedAt = tick;
                }
            }
            report.append(String.format("%s: went off at x=%.1f; ticks after the blast: the wall's column was noted as changed at +%d, pushed at +%d%n",
                    weapon, end[0], markedAt, pushedAt));
            if (end[0] > -390.0D) {
                failures.append(String.format("%s: did not reach the wall (went off at x=%.1f)%n", weapon, end[0]));
            } else if (markedAt < 0 || pushedAt < 0 || pushedAt - markedAt > PUSHED_WITHIN_TICKS) {
                failures.append(String.format("%s: its damage was noted at +%d and pushed at +%d (limit %d ticks apart)%n",
                        weapon, markedAt, pushedAt, PUSHED_WITHIN_TICKS));
            }
        }
    }

    /** Whether a chunk column of the wall on that side (1 east, -1 west) has been noted as changed. */
    private static boolean wallMarked(int side) {
        int near = (TARGET_X * side) >> 4;
        int far = ((TARGET_X + 3) * side) >> 4;
        return MARKS.stream().anyMatch(mark -> (mark[0] == near || mark[0] == far) && (mark[1] == 0 || mark[1] == -1));
    }

    /**
     * Flies a missile into the wall on the east ({@code side} 1) or west (-1) and watches it. Returns, in ticks after
     * the blast (-1: never): when sky first showed through the middle of the wall, when the server noted the change,
     * and when it pushed it.
     */
    private int[] shoot(ClientGameTestContext context, TestServerContext server, StringBuilder report, int side, boolean hurry) {
        String name = hurry ? "hurried" : "usual";
        server.runCommand("tp @a 0 " + ALTITUDE + " 0 " + (side > 0 ? -90 : 90) + " 0");
        server.runOnServer(s -> DistantTerrainRefresh.enabled = hurry);

        // Wait until the wall has reached the client as distant terrain and is drawn.
        int waited = 0;
        while (waited < 3000) {
            context.waitTicks(100);
            waited += 100;
            if (darkPixels(context.takeScreenshot("distant_damage_wait")) > 150) {
                break;
            }
        }
        context.waitTicks(100);
        double skyBefore = skyInMiddle(context.takeScreenshot("distant_damage_" + name + "_before"));
        report.append(String.format("%s: the wall showed up after %d ticks; sky through its middle before the shot: %.0f%%%n",
                name, waited, skyBefore * 100.0D));

        AtomicReference<VehicleMissileEntity> fired = new AtomicReference<>();
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            Entity owner = EntityType.MARKER.create(level, EntitySpawnReason.COMMAND);
            owner.setPos(0.0D, ALTITUDE, 0.0D);
            level.addFreshEntity(owner);
            VehicleMissileEntity missile = new VehicleMissileEntity(level, owner, null, new Vec3(8.0D * side, ALTITUDE, 0.5D),
                    new Vec3(0.8D * side, 0.0D, 0.0D), Reference.id("vehicle_9m120_passenger_missile"));
            level.addFreshEntity(missile);
            fired.set(missile);
        });
        int flight = 0;
        double[] end = new double[]{0.0D, 0.0D};
        while (flight < 300) {
            context.waitTick();
            flight++;
            end = server.computeOnServer(s -> new double[]{fired.get().getX(), fired.get().isRemoved() ? 1.0D : 0.0D});
            if (end[1] > 0.0D) {
                break;
            }
        }
        int broken = server.computeOnServer(s -> {
            int air = 0;
            for (int y = ALTITUDE - 8; y <= ALTITUDE + 8; y++) {
                for (int z = -8; z <= 8; z++) {
                    if (s.overworld().getBlockState(new net.minecraft.core.BlockPos(TARGET_X * side, y, z)).isAir()) {
                        air++;
                    }
                }
            }
            return air;
        });
        report.append(String.format("%s: missile went off at x=%.1f after %d ticks; %d of 289 blocks of the wall's face gone%n",
                name, end[0], flight, broken));

        int shown = -1;
        StringBuilder seen = new StringBuilder();
        MARKS.clear();
        long[] start = server.computeOnServer(s -> serverCounts());
        long columnsStart = context.computeOnClient(mc -> clientColumns());
        int markedAt = -1;
        int pushedAt = -1;
        int receivedAt = -1;
        long columnsAtPush = 0L;
        for (int tick = 1; tick <= WATCH_TICKS; tick++) {
            context.waitTick();
            long[] counts = server.computeOnServer(s -> serverCounts());
            long columns = context.computeOnClient(mc -> clientColumns());
            if (markedAt < 0 && wallMarked(side)) {
                markedAt = tick;
            }
            if (markedAt >= 0 && pushedAt < 0 && counts[2] == 0) {
                pushedAt = tick;
                columnsAtPush = columns;
            }
            if (pushedAt >= 0 && receivedAt < 0 && columns > columnsAtPush) {
                receivedAt = tick;
            }
            // The smoke hangs over the hole for some 10 seconds; cleared so the terrain behind it can be timed.
            context.runOnClient(mc -> {
                MissileBlastRenderer.clear();
                mc.particleEngine.clearParticles();
            });
            if (tick % LOOK_EVERY_TICKS == 0) {
                double sky = skyInMiddle(context.takeScreenshot(String.format("distant_damage_%s_t%04d", name, tick)));
                if (tick % 20 == 0) {
                    seen.append(String.format(" +%d:%.0f%%", tick, sky * 100.0D));
                }
                if (shown < 0 && sky >= 0.6D) {
                    shown = tick;
                }
                if (shown >= 0 && tick >= shown + 40) {
                    break;
                }
            }
        }
        report.append(String.format("%s: sky through the middle of the wall, by ticks after the blast:%s%n", name, seen));
        long[] finish = server.computeOnServer(s -> serverCounts());
        report.append(String.format("%s: ticks after the blast: the wall's column was noted as changed at +%d, pushed to the client at +%d, "
                        + "the client next got terrain at +%d (columns marked %d, pushed %d, received %d; pushes hurried %d)%n",
                name, markedAt, pushedAt, receivedAt, finish[0] - start[0], finish[1] - start[1],
                context.computeOnClient(mc -> clientColumns()) - columnsStart, finish[3]));
        report.append(String.format("%s: the hole showed %d ticks after the blast%n", name, shown));
        return new int[]{shown, markedAt, pushedAt};
    }

    /** Chunk columns Voxy Server Side has noted as changed, as [x, z], since last cleared. */
    private static final java.util.List<int[]> MARKS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean listening;

    /**
     * Voxy Server Side's counts: columns noted as changed, columns pushed to clients, columns waiting to be pushed, and
     * how many pushes jegn has hurried. Also starts listening for which columns are noted.
     */
    private static long[] serverCounts() {
        try {
            Object service = Class.forName("dev.vox.lss.networking.server.LSSServerNetworking").getMethod("getRequestService").invoke(null);
            if (service == null) {
                return new long[]{0L, 0L, 0L, DistantTerrainRefresh.hurried};
            }
            Object tracker = service.getClass().getMethod("getDirtyTracker").invoke(service);
            if (!listening) {
                Class<?> listenerType = Class.forName("dev.vox.lss.common.tracking.DirtyColumnTracker$MarkListener");
                java.lang.reflect.Field field = tracker.getClass().getDeclaredField("markListener");
                field.setAccessible(true);
                Object existing = field.get(tracker);
                Object both = java.lang.reflect.Proxy.newProxyInstance(listenerType.getClassLoader(), new Class<?>[]{listenerType},
                        (proxy, method, args) -> {
                            if (method.getName().equals("onMarkDirty")) {
                                MARKS.add(new int[]{(Integer) args[1], (Integer) args[2]});
                                if (existing != null) {
                                    method.invoke(existing, args);
                                }
                                return null;
                            }
                            return method.invoke(existing == null ? MARKS : existing, args);
                        });
                tracker.getClass().getMethod("setMarkListener", listenerType).invoke(tracker, both);
                listening = true;
            }
            return new long[]{(Long) tracker.getClass().getMethod("getTotalMarked").invoke(tracker),
                    (Long) tracker.getClass().getMethod("getTotalDrained").invoke(tracker),
                    (Integer) tracker.getClass().getMethod("pendingCount").invoke(tracker), DistantTerrainRefresh.hurried};
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** How many distant terrain columns the client has been sent. */
    private static long clientColumns() {
        try {
            return (Long) Class.forName("dev.vox.lss.networking.client.LSSClientNetworking").getMethod("getColumnsReceived").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** How much of the very middle of a screenshot (9 by 9 pixels, about 4 blocks of the wall) is bright sky. */
    private static double skyInMiddle(java.nio.file.Path screenshot) {
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(screenshot.toFile());
            int sky = 0;
            int cx = image.getWidth() / 2;
            int cy = image.getHeight() / 2;
            for (int y = cy - 4; y <= cy + 4; y++) {
                for (int x = cx - 4; x <= cx + 4; x++) {
                    int rgb = image.getRGB(x, y);
                    if (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF) > 480) {
                        sky++;
                    }
                }
            }
            return sky / 81.0D;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Pixels of the black wall (or of smoke) in the middle fifth of a screenshot. */
    private static int darkPixels(java.nio.file.Path screenshot) {
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(screenshot.toFile());
            int dark = 0;
            for (int y = image.getHeight() * 2 / 5; y < image.getHeight() * 3 / 5; y++) {
                for (int x = image.getWidth() * 2 / 5; x < image.getWidth() * 3 / 5; x++) {
                    int rgb = image.getRGB(x, y);
                    if (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF) < 240) {
                        dark++;
                    }
                }
            }
            return dark;
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
