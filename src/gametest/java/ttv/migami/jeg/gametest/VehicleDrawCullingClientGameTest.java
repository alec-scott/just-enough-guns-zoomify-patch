package ttv.migami.jeg.gametest;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.CloudStatus;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.client.render.VehicleDrawCulling;

/**
 * The vehicle renderer's culling (VehicleDrawCulling: glass and glow cubes and quads with nothing on them left out, one
 * light for an evenly lit vehicle, no vertex light in the shadow pass, the lamp-spill reuse) must not change the picture.
 * Each vehicle with glass, a glow or cabin lamps is shot with each switch on and off, by day and at night, in the open and
 * half under a stone roof (where its vertices are lit differently). Without shaders the shots must match; with -PwithPack
 * the run checks the culling engages under Iris and Photon and logs the differences for reference. Throughout, every
 * lamp-spill face is also drawn both ways and compared vertex by vertex. Screenshots: vehicle_cull_{case}_{switch}.
 */
public class VehicleDrawCullingClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final double SPACING = 18.0D;
    private static final double GROUND = -60.0D;
    /** Share of pixels allowed to differ by more than {@link #TOLERANCE} (one in a thousand: a stray particle). */
    private static final double MAX_DIFFERENT = 0.001D;
    private static final int TOLERANCE = 6;

    private record Parked(String name, EntityType<?> type, double distance, double height) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        List<Parked> fleet = List.of(
                new Parked("truck", ModEntities.TRUCK.get(), 11.0D, 2.0D),
                new Parked("a10", ModEntities.A10.get(), 13.0D, 2.0D),
                new Parked("pickup", ModEntities.PICKUP.get(), 8.0D, 1.5D),
                new Parked("sentry", ModEntities.SENTRY.get(), 5.0D, 1.5D),
                new Parked("speedboat", ModEntities.SPEEDBOAT.get(), 8.0D, 1.0D),
                // Its cabin's dome lamps run on battery: the lamp-spill pass has work on it parked.
                new Parked("ch47", ModEntities.CH47.get(), 20.0D, 3.0D));
        // Every lamp-spill face is drawn both ways throughout and compared vertex by vertex (the reuse path must match).
        VehicleDrawCulling.spillCheck = true;
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("gamerule doWeatherCycle false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set noon");
            server.runOnServer(s -> {
                var level = s.overworld();
                for (int i = 0; i < fleet.size(); i++) {
                    Entity vehicle = fleet.get(i).type().create(level, EntitySpawnReason.COMMAND);
                    if (vehicle == null) {
                        throw new AssertionError("could not create " + fleet.get(i).name());
                    }
                    vehicle.setPos(i * SPACING, GROUND, 0.0D);
                    vehicle.setYRot(0.0F);
                    vehicle.yRotO = 0.0F;
                    level.addFreshEntity(vehicle);
                }
            });
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
                mc.options.cloudStatus().set(CloudStatus.OFF);
                mc.options.particles().set(ParticleStatus.MINIMAL);
            });
            context.waitTicks(60);
            int failures = 0;
            for (String time : new String[] {"day", "night"}) {
                server.runCommand("time set " + (time.equals("day") ? "noon" : "midnight"));
                context.waitTicks(40);
                for (int i = 0; i < fleet.size(); i++) {
                    failures += this.compare(context, server, i * SPACING, fleet.get(i), time + "_open_" + fleet.get(i).name());
                }
            }
            // Half under a roof: the back half of each vehicle in shade, so its vertices take different light.
            server.runCommand("time set noon");
            for (int i = 0; i < fleet.size(); i++) {
                double x = i * SPACING;
                server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone",
                        (int) x - 7, (int) GROUND + 7, -12, (int) x + 7, (int) GROUND + 7, 0));
                server.runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone",
                        (int) x - 7, (int) GROUND, -12, (int) x - 7, (int) GROUND + 6, 0));
            }
            context.waitTicks(40);
            for (int i = 0; i < fleet.size(); i++) {
                failures += this.compare(context, server, i * SPACING, fleet.get(i), "day_roof_" + fleet.get(i).name());
            }
            VehicleDrawCulling.set(true);
            context.runOnClient(mc -> mc.options.hideGui = false);
            VehicleDrawCulling.spillCheck = false;
            LOGGER.info("[vehicle-cull] lamp spill: {} faces drawn both ways, {} differed", VehicleDrawCulling.spillFacesChecked,
                    VehicleDrawCulling.spillFacesDiffering);
            if (VehicleDrawCulling.spillFacesChecked == 0 || VehicleDrawCulling.spillFacesDiffering > 0) {
                throw new AssertionError("lamp-spill reuse: " + VehicleDrawCulling.spillFacesDiffering + " of "
                        + VehicleDrawCulling.spillFacesChecked + " faces drawn differently (must be 0 of more than 0)");
            }
            long masked = VehicleDrawCulling.quadsMasked;
            long even = VehicleDrawCulling.evenDraws;
            LOGGER.info("[vehicle-cull] {} of {} glass/glow quads left out, {} model draws with one light", masked,
                    VehicleDrawCulling.quadsSeen, even);
            if (VehicleDrawCulling.quadsSeen == 0 || masked == 0 || even == 0) {
                throw new AssertionError("the culling never ran (glass/glow quads " + VehicleDrawCulling.quadsSeen
                        + ", masked " + masked + ", even-light draws " + even + "): the comparison proves nothing");
            }
            if (failures > 0) {
                throw new AssertionError(failures + " culled shots differ from the full draw (see vehicle_cull_* screenshots)");
            }
            LOGGER.info("[vehicle-cull] {} vehicles: culled and full draws compared by day, at night and half roofed", fleet.size());
        }
    }

    /**
     * Shoots the vehicle with each switch on by itself and all on, each straight after a shot with all off (so the
     * model has no time to move between the two), and counts a failure if any pair differs. Under a shader pack the
     * picture is not repeatable (dithering changes every frame, and the scene drifts between shots: two shots with
     * everything off differed in up to 0.54% of pixels under Photon, 2026-11-26), so there the differences are only
     * logged against an off/off pair for a person to judge; the exact check is the run without shaders.
     */
    private int compare(ClientGameTestContext context, TestServerContext server, double x, Parked parked, String name) {
        double d = parked.distance();
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f",
                x + d * 0.55D, GROUND + parked.height() + d * 0.25D, d * 0.85D, x, GROUND + parked.height() * 0.6D, 0.0D));
        context.waitTicks(30);
        boolean exact = !context.computeOnClient(mc -> ttv.migami.jeg.client.compat.ShaderPacks.inUse());
        StringBuilder line = new StringBuilder();
        int failures = 0;
        boolean[][] configs = {{false, false, false}, {true, false, false}, {false, true, false}, {false, false, true},
                {true, true, true}};
        String[] labels = {"noise", "mask", "even", "shadow", "on"};
        for (int k = exact ? 1 : 0; k < configs.length; k++) {
            Path off = shot(context, name, labels[k] + "_off", false, false, false);
            Path with = shot(context, name, labels[k], configs[k][0], configs[k][1], configs[k][2]);
            double share = differentShare(off, with);
            line.append(' ').append(labels[k]).append(' ').append(pct(share));
            if (exact && share > MAX_DIFFERENT) {
                LOGGER.error("[vehicle-cull] {}: with {} on the picture changed ({}% of pixels)", name, labels[k], pct(share));
                failures++;
            }
        }
        VehicleDrawCulling.set(true);
        LOGGER.info("[vehicle-cull] {} ({}): % of pixels differing from the full draw:{}", name,
                exact ? "no shaders, must match" : "shader pack, for reference", line);
        return failures > 0 ? 1 : 0;
    }

    private static Path shot(ClientGameTestContext context, String name, String label, boolean mask, boolean even,
                             boolean shadow) {
        VehicleDrawCulling.quadMask = mask;
        VehicleDrawCulling.evenLight = even;
        VehicleDrawCulling.shadowPassLight = shadow;
        context.waitTicks(10);
        return context.takeScreenshot("vehicle_cull_" + name + "_" + label);
    }

    private static String pct(double share) {
        return String.format(Locale.ROOT, "%.4f", share * 100.0D);
    }

    private static double differentShare(Path a, Path b) {
        try {
            BufferedImage first = ImageIO.read(a.toFile());
            BufferedImage second = ImageIO.read(b.toFile());
            if (first.getWidth() != second.getWidth() || first.getHeight() != second.getHeight()) {
                return 1.0D;
            }
            long different = 0;
            for (int y = 0; y < first.getHeight(); y++) {
                for (int x = 0; x < first.getWidth(); x++) {
                    int p = first.getRGB(x, y);
                    int q = second.getRGB(x, y);
                    if (Math.abs(((p >> 16) & 0xFF) - ((q >> 16) & 0xFF)) > TOLERANCE
                            || Math.abs(((p >> 8) & 0xFF) - ((q >> 8) & 0xFF)) > TOLERANCE
                            || Math.abs((p & 0xFF) - (q & 0xFF)) > TOLERANCE) {
                        different++;
                    }
                }
            }
            return (double) different / ((long) first.getWidth() * first.getHeight());
        } catch (java.io.IOException e) {
            throw new AssertionError("could not read screenshots " + a + ", " + b, e);
        }
    }
}
