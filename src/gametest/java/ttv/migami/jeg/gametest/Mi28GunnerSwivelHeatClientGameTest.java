package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.client.render.GunBarrelHeatRenderer;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The Mi-28 gunner's seat. The gunner has to be able to swing the view and the chin gun well round to the side and rear,
 * steeply down and a fair way up, and no further than the seat allows. Holding the trigger has to heat the barrel (a glow
 * and wisps of smoke) and letting go has to cool it again. Screenshots show each view and the hot barrel.
 */
public class Mi28GunnerSwivelHeatClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    private record Look(String name, float yaw, float pitch, float wantYaw, float wantPitch) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        Look[] looks = {
                new Look("side_down", 90.0F, 55.0F, 90.0F, 55.0F),
                new Look("rear_quarter", -150.0F, 20.0F, -150.0F, 20.0F),
                new Look("up", 40.0F, -25.0F, 40.0F, -25.0F),
                new Look("past_the_stops", 178.0F, 88.0F, 160.0F, 70.0F),
                new Look("past_the_up_stop", -175.0F, -60.0F, -160.0F, -30.0F),
        };
        StringBuilder report = new StringBuilder("mi28 gunner swivel and heat report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("fill -5 -41 4 5 -41 20 minecraft:stone");
            server.runCommand("tp @a 0 -40 0 0 0");
            server.runCommand("execute as @p at @s run summon jeg:mi28 ~ ~ ~6 {Rotation:[0f,0f]}");
            context.waitTicks(5);
            server.runOnServer(minecraftServer -> {
                for (Entity entity : minecraftServer.overworld().getAllEntities()) {
                    if (entity instanceof VehicleEntity vehicle) {
                        for (int slot = 0; slot < 6; slot++) {
                            vehicle.vehicleInventory().setItem(slot, new ItemStack(BuiltInRegistries.ITEM.getValue(Reference.id("rifle_ammo")), 64));
                        }
                    }
                }
            });
            server.runCommand("ride @p mount @e[type=jeg:mi28,limit=1,sort=nearest]");
            context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
            context.getInput().pressKey(KeyBindings.VEHICLE_CHANGE_SEAT);
            context.waitTicks(10);
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.gui.getChat().clearMessages(true);
            });

            for (Look look : looks) {
                for (int tick = 0; tick < 12; tick++) {
                    context.runOnClient(mc -> {
                        mc.player.setYRot(vehicle(mc).getYRot() + look.yaw());
                        mc.player.setXRot(look.pitch());
                    });
                    context.waitTick();
                }
                float[] got = context.computeOnClient(mc -> {
                    VehicleEntity vehicle = vehicle(mc);
                    return new float[]{Mth.wrapDegrees(mc.player.getYRot() - vehicle.getYRot()), mc.player.getXRot(),
                            -vehicle.turretYaw(), vehicle.turretPitch()};
                });
                context.takeScreenshot("mi28_gunner_look_" + look.name());
                report.append(String.format("%s: asked yaw %.0f pitch %.0f; view yaw %.1f pitch %.1f; gun yaw %.1f pitch %.1f%n",
                        look.name(), look.yaw(), look.pitch(), got[0], got[1], got[2], got[3]));
                if (Math.abs(got[0] - look.wantYaw()) > 1.5F || Math.abs(got[1] - look.wantPitch()) > 1.5F) {
                    failures.append(String.format("%s: the view went to yaw %.1f pitch %.1f, want %.0f and %.0f%n",
                            look.name(), got[0], got[1], look.wantYaw(), look.wantPitch()));
                }
                if (Math.abs(Mth.wrapDegrees(got[2] - look.wantYaw())) > 3.0F || Math.abs(got[3] - look.wantPitch()) > 3.0F) {
                    failures.append(String.format("%s: the gun went to yaw %.1f pitch %.1f, want %.0f and %.0f%n",
                            look.name(), got[2], got[3], look.wantYaw(), look.wantPitch()));
                }
            }

            // Fire a long burst looking a little to the side, in third person so the barrel is in view.
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(130);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            int vehicleId = context.computeOnClient(mc -> vehicle(mc).getId());
            float before = context.computeOnClient(mc -> GunBarrelHeatRenderer.heat(vehicle(mc).getId()));
            context.getInput().holdKey(options -> options.keyAttack);
            for (int tick = 0; tick < 110; tick++) {
                context.runOnClient(mc -> {
                    mc.player.setYRot(vehicle(mc).getYRot() + 30.0F);
                    mc.player.setXRot(8.0F);
                });
                context.waitTick();
            }
            float hot = context.computeOnClient(mc -> GunBarrelHeatRenderer.heat(vehicle(mc).getId()));
            context.takeScreenshot("mi28_gun_hot_firing");
            context.getInput().releaseKey(options -> options.keyAttack);
            context.waitTicks(25);
            int wisps = context.computeOnClient(mc -> GunBarrelHeatRenderer.wisps());
            context.takeScreenshot("mi28_gun_hot_smoking");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(5);
            context.takeScreenshot("mi28_gun_hot_gunner_view");
            // Out of the seat and round to the front, close up on the muzzle while it is still hot.
            double[] muzzle = context.computeOnClient(mc -> {
                var at = vehicle(mc).rotaryGunMuzzle(1.0F);
                return new double[]{at.x, at.y, at.z};
            });
            server.runCommand("ride @p dismount");
            context.waitTicks(5);
            server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f -40 %.2f facing %.2f %.2f %.2f",
                    muzzle[0] + 1.6D, muzzle[2] + 1.2D, muzzle[0], muzzle[1], muzzle[2] - 0.4D));
            for (int tick = 0; tick < 6; tick++) {
                context.runOnClient(mc -> mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                        new net.minecraft.world.phys.Vec3(muzzle[0], muzzle[1], muzzle[2] - 0.4D)));
                context.waitTick();
            }
            report.append(String.format("muzzle at %.2f %.2f %.2f%n", muzzle[0], muzzle[1], muzzle[2]));
            context.takeScreenshot("mi28_gun_hot_close_up");
            context.waitTicks(220);
            float cooled = context.computeOnClient(mc -> GunBarrelHeatRenderer.heat(vehicleId));
            report.append(String.format("barrel heat: %.2f before, %.2f after a 5.5 s burst, %d wisps of smoke after it, %.2f once cooled%n",
                    before, hot, wisps, cooled));
            if (before > 0.0F) {
                failures.append("the barrel was hot before a shot was fired\n");
            }
            if (hot < 0.7F) {
                failures.append(String.format("the barrel only reached heat %.2f in a long burst%n", hot));
            }
            if (wisps < 2) {
                failures.append("no smoke came off the hot barrel\n");
            }
            if (cooled > 0.05F) {
                failures.append(String.format("the barrel was still at heat %.2f 13 seconds after firing%n", cooled));
            }
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    private static VehicleEntity vehicle(Minecraft mc) {
        return (VehicleEntity) mc.player.getVehicle();
    }
}
