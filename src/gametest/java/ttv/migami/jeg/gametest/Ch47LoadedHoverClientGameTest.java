package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.entity.ChinookEntity;
import ttv.migami.jeg.vehicle.entity.ChinookSling;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The user's rules of 2026-11-21 for the Chinook (design/chinook/PLAN.md):
 * <ul>
 *     <li>With a load on the sling and the controls left alone, it stays hovering (it sank about 2 m/s, then, half
 *     fixed, climbed at 0.17 m/s).</li>
 *     <li>Each door gun has its own ammunition: rounds in one gun's can never feed the other.</li>
 *     <li>The pilot's X moves the ramp one step a press, never missing one.</li>
 * </ul>
 */
public class Ch47LoadedHoverClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @p 0.5 -55 0.5");
            server.runCommand("summon jeg:lav150 0.5 -60 10.5");
            server.runCommand("summon jeg:ch47 0.5 -51.5 9.9");
            context.waitTicks(20);
            server.runCommand("ride @p mount @e[type=jeg:ch47,limit=1]");
            context.waitFor(mc -> mc.player.getVehicle() instanceof ChinookEntity);
            context.waitTicks(20);
            StringBuilder failures = new StringBuilder();

            // ---- X: one step a press, each press counted (closed -> level -> down).
            for (int press = 0; press < 2; press++) {
                context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
                context.waitTicks(25);
            }
            context.waitTicks(80);
            float ramp = server.computeOnServer(s -> this.chinook(s).rampAngle(1.0F));
            LOG.info("[ch47-loaded-hover] ramp after two X presses: {} deg", ramp);
            if (Math.abs(ramp - ChinookEntity.RAMP_DOWN) > 0.01F) {
                failures.append("two X presses should lower the ramp all the way, it is at ").append(ramp).append(" deg\n");
            }

            // ---- Hook the LAV-150 and lift it.
            context.getInput().pressKey(KeyBindings.VEHICLE_TOW);
            context.waitTicks(70);
            context.getInput().pressKey(KeyBindings.VEHICLE_TOW);
            context.waitTicks(5);
            int state = server.computeOnServer(s -> this.chinook(s).sling().state());
            if (state != ChinookSling.CARRYING) {
                String where = server.computeOnServer(s -> {
                    ChinookEntity chinook = this.chinook(s);
                    var lav = s.overworld().getEntitiesOfClass(ttv.migami.jeg.vehicle.entity.base.VehicleEntity.class,
                            chinook.getBoundingBox().inflate(20.0D), v -> v != chinook).stream().findFirst().orElse(null);
                    return "chinook " + chinook.position() + " hook " + chinook.sling().hookWorld(1.0F) + " end " + chinook.sling().endWorld()
                            + " load lift centre " + (lav == null ? "none" : ChinookSling.liftCentreOf(lav) + " " + lav.getType().getDescriptionId());
                });
                throw new AssertionError("the LAV-150 wasn't hooked (sling state " + state + "): " + where);
            }
            context.getInput().holdKey(options -> options.keyJump);
            context.waitTicks(50);
            context.getInput().releaseKey(options -> options.keyJump);
            // Let the climb and the load's first swings settle (a 6 s pendulum).
            context.waitTicks(300);

            // ---- Hands off for ten seconds: it holds its height.
            double start = server.computeOnServer(s -> this.chinook(s).getY());
            double lowest = start, highest = start;
            StringBuilder samples = new StringBuilder();
            for (int i = 0; i < 20; i++) {
                context.waitTicks(10);
                double y = server.computeOnServer(s -> this.chinook(s).getY());
                samples.append(String.format(java.util.Locale.ROOT, " %.2f", y));
                lowest = Math.min(lowest, y);
                highest = Math.max(highest, y);
            }
            LOG.info("[ch47-loaded-hover] height every half second:{}", samples);
            double load = server.computeOnServer(s -> {
                VehicleEntity carried = this.chinook(s).sling().loadEntity();
                return carried == null ? Double.NaN : carried.getY();
            });
            LOG.info("[ch47-loaded-hover] hands-off with the LAV-150: start y {}, lowest {}, highest {}, load at y {}", start, lowest, highest, load);
            if (start - lowest > 0.5D || highest - start > 0.5D) {
                failures.append("hands off with a load it should hold its height: from ").append(start).append(" it went to ")
                        .append(lowest).append("..").append(highest).append('\n');
            }
            if (!(load > -59.0D)) {
                failures.append("the LAV-150 should still hang clear of the ground, it is at y ").append(load).append('\n');
            }

            // ---- The door guns' ammunition: 64 rounds in the right gun's can feed the right gun only.
            String ammo = server.computeOnServer(s -> {
                ChinookEntity chinook = this.chinook(s);
                chinook.doorGunAmmo(ChinookEntity.RIGHT_GUNNER_SEAT).setItem(0, new ItemStack(ModItems.AMMO.get(ttv.migami.jeg.Reference.id("rifle_ammo")).get(), 64));
                chinook.reloadAiVehicleWeapons();
                return "";
            });
            context.waitTicks(5);
            int[] rounds = server.computeOnServer(s -> {
                ChinookEntity chinook = this.chinook(s);
                return new int[]{chinook.vehicleWeaponSlotAmmo(0) + chinook.vehicleWeaponSlotReserveAmmo(0),
                        chinook.vehicleWeaponSlotAmmo(1) + chinook.vehicleWeaponSlotReserveAmmo(1)};
            });
            LOG.info("[ch47-loaded-hover] rounds after loading only the right can{}: left gun {}, right gun {}", ammo, rounds[0], rounds[1]);
            if (rounds[0] != 0 || rounds[1] != 64) {
                failures.append("64 rounds in the right gun's can should give the right gun 64 and the left 0, got left ")
                        .append(rounds[0]).append(", right ").append(rounds[1]).append('\n');
            }

            if (!failures.isEmpty()) {
                throw new AssertionError(failures.toString());
            }
        }
    }

    private ChinookEntity chinook(net.minecraft.server.MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof ChinookEntity chinook) {
                    return chinook;
                }
            }
        }
        throw new AssertionError("no Chinook");
    }
}
