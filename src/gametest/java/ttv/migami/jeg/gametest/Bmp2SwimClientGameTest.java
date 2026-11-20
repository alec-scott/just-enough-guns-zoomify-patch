package ttv.migami.jeg.gametest;

import java.lang.reflect.Method;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The BMP-2 swims (fleet plan section 2): driven off a bank into a river 4 deep, it floats with its waterline (1.25
 * blocks up the hull) at the surface, makes way at no more than 7 km/h (0.097 blocks a tick), steered by its tracks,
 * may not fire its ATGM afloat (the 30 mm still fires), and climbs out up the far bank, a block above the water.
 */
public class Bmp2SwimClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    /** The river's surface: water blocks up to y -60, so the surface is at -59. */
    private static final double SURFACE = -59.0D;
    private int bmpId = -1;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("BMP-2 swim report\n");
        StringBuilder failures = new StringBuilder();
        context.runOnClient(mc -> mc.options.renderDistance().set(10));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender(false, 2400);
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            server.runCommand("forceload add -16 -16 16 96");
            // Near bank, river, far bank: land 1 block above the water.
            server.runCommand("fill -12 -61 -10 12 -59 20 minecraft:dirt");
            server.runCommand("fill -12 -63 21 12 -60 50 minecraft:water");
            server.runCommand("fill -12 -61 51 12 -59 85 minecraft:dirt");
            // The far side shelves to a beach a block under the bank, as a real exit does (a sheer bank 2 blocks above a
            // floating hull's keel would stop a real BMP-2 too).
            server.runCommand("fill -12 -63 44 12 -62 50 minecraft:dirt");
            server.runCommand("fill -12 -61 47 12 -61 50 minecraft:dirt");
            server.runCommand("fill -12 -60 49 12 -60 50 minecraft:dirt");
            server.runCommand("tp @a 4.5 -58 6 0 0");
            context.waitTicks(20);
            this.bmpId = server.computeOnServer(s -> {
                VehicleEntity bmp = ModEntities.BMP2.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                bmp.snapTo(0.5D, -58.0D, 10.5D, 0.0F, 0.0F);
                bmp.addEnergy(bmp.maxVehicleEnergy());
                s.overworld().addFreshEntity(bmp);
                return bmp.getId();
            });
            context.waitTicks(20);
            server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).startRiding(this.bmp(s), true, true));
            context.waitTicks(80);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.getInput().holdKey(options -> options.keyUp);
            double maxAfloatSpeed = 0.0D, minFloat = Double.MAX_VALUE, maxFloat = -Double.MAX_VALUE, enterZ = Double.NaN, lastZ = 0.0D;
            int afloatTicks = 0;
            boolean atgmRefused = false, cannonAllowed = false, shot = false;
            for (int tick = 0; tick < 900; tick++) {
                context.waitTick();
                double[] s = server.computeOnServer(sv -> {
                    VehicleEntity v = this.bmp(sv);
                    double speed = Math.hypot(v.getX() - v.xo, v.getZ() - v.zo);
                    return new double[]{v.getZ(), v.getBoundingBox().minY, speed, v.amphibiousAfloat() ? 1 : 0, v.onGround() ? 1 : 0};
                });
                boolean afloat = s[3] > 0;
                if (afloat && s[0] > 33 && s[0] < 38) {
                    // (From z 33, after it lands from the bank at road speed; up to z 38: the bow, 3.8 ahead of the middle, meets the shelving bottom at z 44 and climbs.)
                    // Settled after the drop off the bank; the average over the stretch (one-tick position corrections
                    // as it came off the bank at land speed are not its swimming speed).
                    if (Double.isNaN(enterZ)) enterZ = s[0];
                    afloatTicks++;
                    lastZ = s[0];
                    maxAfloatSpeed = (lastZ - enterZ) / afloatTicks;
                    double waterlineAt = s[1] + 1.25D;
                    minFloat = Math.min(minFloat, waterlineAt);
                    maxFloat = Math.max(maxFloat, waterlineAt);
                    if (!shot && s[0] > 34) {
                        shot = true;
                        boolean[] fire = server.computeOnServer(sv -> new boolean[]{canFire(this.bmp(sv), 2), canFire(this.bmp(sv), 0)});
                        atgmRefused = !fire[0];
                        cannonAllowed = fire[1];
                        context.takeScreenshot("bmp2_swimming");
                    }
                }
                if (s[0] > 58 && s[4] > 0) {
                    report.append(String.format("out on the far bank at tick %d, z %.1f, y %.2f%n", tick, s[0], s[1]));
                    break;
                }
            }
            context.getInput().releaseKey(options -> options.keyUp);
            double endZ = server.computeOnServer(sv -> this.bmp(sv).getZ());
            report.append(String.format("afloat %d ticks; average speed afloat %.3f blocks/tick (%.1f km/h); waterline %.2f to %.2f (surface %.2f); ATGM refused %s, 30 mm allowed %s; end z %.1f%n",
                    afloatTicks, maxAfloatSpeed, maxAfloatSpeed * 72.0D, minFloat, maxFloat, SURFACE, atgmRefused, cannonAllowed, endZ));
            if (afloatTicks < 25) failures.append("it never swam across the river\n");
            if (maxAfloatSpeed > 0.105D) failures.append(String.format("swam too fast (%.3f)%n", maxAfloatSpeed));
            if (maxAfloatSpeed < 0.06D) failures.append(String.format("barely made way afloat (%.3f)%n", maxAfloatSpeed));
            if (minFloat < SURFACE - 0.35D || maxFloat > SURFACE + 0.35D) failures.append("did not float at its waterline\n");
            if (!atgmRefused) failures.append("the ATGM could fire afloat\n");
            if (!cannonAllowed) failures.append("the 30 mm could not fire afloat\n");
            if (endZ < 58.0D) failures.append("did not climb out up the far bank\n");
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            LOGGER.error("[bmp2-swim] FAILED: {}", failures);
            throw new AssertionError(failures.toString() + report);
        }
        LOGGER.info("[bmp2-swim] COMPLETE");
    }

    private static boolean canFire(VehicleEntity vehicle, int slot) {
        try {
            Method method = VehicleEntity.class.getDeclaredMethod("canFireWeapon", VehicleWeaponInfo.class);
            method.setAccessible(true);
            return (boolean) method.invoke(vehicle, vehicle.vehicleData().defaults().weapons().get(slot));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private VehicleEntity bmp(MinecraftServer server) {
        return (VehicleEntity) server.overworld().getEntity(this.bmpId);
    }
}
