package ttv.migami.jeg.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.client.MinigunClient;
import ttv.migami.jeg.gun.MinigunSpin;
import ttv.migami.jeg.init.ModDataComponents;
import ttv.migami.jeg.item.GunItem;

/**
 * The reload key on a minigun runs the barrels without firing to cool them fast. Checks, with the real key: an
 * overheated minigun left alone barely cools; after the key it is cold within 4 seconds, cannot fire meanwhile, spins
 * its barrels, and fires again afterwards; the key again stops the cooling; two miniguns cool together; putting the gun
 * away stops it; and the key does nothing to a cold minigun. While cooling the gun hisses and lets off gas, in first
 * and third person, and both stop with the cooling. Unequal heat in two miniguns must keep the barrels running until
 * both are cold, and stopped barrels must keep their angle instead of snapping back to the model's default pose.
 */
public class MinigunRapidCoolClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    /** Heat right after overheating: the limit (200) plus the recovery buffer. */
    private static final int OVERHEATED = 280;
    private static final int COOL_LIMIT_TICKS = 80;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("minigun rapid cooling report\n");
        StringBuilder failures = new StringBuilder();
        boolean hidGui = context.computeOnClient(mc -> mc.options.hideGui);
        context.runOnClient(mc -> mc.options.hideGui = false);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a minecraft:resistance 600 5 true");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 0");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.gui.getChat().clearMessages(true);
            });

            // Left alone, an overheated minigun in the hand stays overheated.
            this.equip(server, "jeg:minigun", "minecraft:air");
            context.waitTicks(40);
            this.setHeat(server, OVERHEATED);
            context.waitTicks(COOL_LIMIT_TICKS);
            int passive = this.heat(server)[0];
            report.append(String.format("left alone for %d ticks: heat %d -> %d%n", COOL_LIMIT_TICKS, OVERHEATED, passive));
            context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
            context.takeScreenshot("minigun_rapid_cool_0_overheated");

            // The key: cooling, barrels turning, no rounds leave while the trigger is held, cold within the limit.
            this.setHeat(server, OVERHEATED);
            context.waitTicks(2);
            int roundsBefore = this.rounds(server);
            int puffsBefore = context.computeOnClient(mc -> MinigunClient.ventPuffs);
            boolean hissBefore = context.computeOnClient(mc -> MinigunClient.venting(mc.player.getId()));
            this.pressReload(context);
            context.waitTicks(3);
            boolean started = this.cooling(server)[0];
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(20);
            boolean spinning = server.computeOnServer(s -> MinigunSpin.isSpinning(s.getPlayerList().getPlayers().getFirst()));
            context.takeScreenshot("minigun_rapid_cool_1_cooling");
            boolean hiss = context.computeOnClient(mc -> MinigunClient.venting(mc.player.getId()));
            int puffs = context.computeOnClient(mc -> MinigunClient.ventPuffs) - puffsBefore;
            int coldAfter = -1;
            for (int tick = 23; tick <= COOL_LIMIT_TICKS + 20; tick++) {
                context.waitTick();
                if (this.heat(server)[0] == 0) {
                    coldAfter = tick;
                    break;
                }
            }
            int firedWhileCooling = roundsBefore - this.rounds(server);
            // The trigger is still down: once cold the gun should fire again by itself.
            context.waitTicks(40);
            boolean stillCooling = this.cooling(server)[0];
            int firedAfter = roundsBefore - firedWhileCooling - this.rounds(server);
            context.getInput().releaseKey(options -> options.keyAttack);
            int puffsCold = context.computeOnClient(mc -> MinigunClient.ventPuffs);
            context.waitTicks(60);
            boolean hissCold = context.computeOnClient(mc -> MinigunClient.venting(mc.player.getId()));
            int puffsAfterCold = context.computeOnClient(mc -> MinigunClient.ventPuffs) - puffsCold;
            report.append(String.format("hiss playing before the key %s, 23 ticks into the cooling %s, once cold %s; puffs of gas in those 23 ticks %d, "
                    + "in 60 ticks once cold %d%n", hissBefore, hiss, hissCold, puffs, puffsAfterCold));
            if (hissBefore || !hiss || hissCold) {
                failures.append(String.format("the hiss should play only while cooling (before %s, during %s, after %s)%n", hissBefore, hiss, hissCold));
            }
            if (puffs < 30 || puffsAfterCold > 0) {
                failures.append(String.format("gas should leave the barrels only while cooling (%d puffs during, %d after)%n", puffs, puffsAfterCold));
            }
            report.append(String.format("after the key: cooling %s, barrels turning %s, cold after %d ticks, %d rounds fired while cooling, "
                    + "%d in the 40 ticks after%n", started, spinning, coldAfter, firedWhileCooling, firedAfter));
            if (passive < OVERHEATED - 40) {
                failures.append(String.format("the comparison is off: left alone the gun cooled to %d%n", passive));
            }
            if (!started) {
                failures.append("the reload key did not start cooling an overheated minigun\n");
            }
            if (!spinning) {
                failures.append("the barrels did not turn while cooling\n");
            }
            if (coldAfter < 0) {
                failures.append(String.format("the minigun was not cold %d ticks after the key (heat %d)%n", COOL_LIMIT_TICKS + 20, this.heat(server)[0]));
            }
            if (firedWhileCooling > 0) {
                failures.append(String.format("the minigun fired %d rounds while cooling%n", firedWhileCooling));
            }
            if (stillCooling) {
                failures.append("the minigun was still marked as cooling once cold\n");
            }
            if (firedAfter <= 0) {
                failures.append("the minigun did not fire again once cold\n");
            }

            // The key again stops it part way, still too hot to fire.
            this.setHeat(server, OVERHEATED);
            context.waitTicks(2);
            this.pressReload(context);
            context.waitTicks(10);
            this.pressReload(context);
            context.waitTicks(5);
            boolean stopped = !this.cooling(server)[0];
            int partWay = this.heat(server)[0];
            report.append(String.format("key again after 10 ticks: stopped %s at heat %d%n", stopped, partWay));
            if (!stopped || partWay <= 0 || partWay >= OVERHEATED - 10) {
                failures.append(String.format("pressing the key again did not stop the cooling part way (stopped %s, heat %d)%n", stopped, partWay));
            }

            // Seen from outside: the gas comes off the gun in the hand.
            this.setHeat(server, OVERHEATED);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(10);
            int puffsThird = context.computeOnClient(mc -> MinigunClient.ventPuffs);
            this.pressReload(context);
            context.waitTicks(25);
            context.takeScreenshot("minigun_rapid_cool_2_third_person");
            report.append(context.<String, RuntimeException>computeOnClient(mc -> {
                var muzzle = ttv.migami.jeg.client.render.gun.GunMuzzleTracker.muzzle(mc.player.getId(), false);
                return "third person: muzzle relative to the feet (player faces +z): "
                        + (muzzle == null ? "none" : muzzle.subtract(mc.player.position())) + '\n';
            }));
            puffsThird = context.computeOnClient(mc -> MinigunClient.ventPuffs) - puffsThird;
            context.waitTicks(60);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            report.append(String.format("third person: %d puffs of gas in 25 ticks%n", puffsThird));
            if (puffsThird < 30) {
                failures.append(String.format("in third person only %d puffs of gas left the barrels%n", puffsThird));
            }

            // Putting the gun away stops it.
            this.setHeat(server, OVERHEATED);
            context.waitTicks(2);
            this.pressReload(context);
            context.waitTicks(5);
            boolean coolingInHand = this.cooling(server)[0];
            context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
            context.waitTicks(10);
            boolean coolingAway = server.computeOnServer(s ->
                    GunItem.isRapidCooling(s.getPlayerList().getPlayers().getFirst().getInventory().getItem(0)));
            context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
            context.waitTicks(40);
            report.append(String.format("put away while cooling: cooling in hand %s, still cooling in the hotbar %s%n", coolingInHand, coolingAway));
            if (!coolingInHand || coolingAway) {
                failures.append("a minigun put away kept on rapid cooling\n");
            }

            // A cold minigun: the key does nothing.
            this.setHeat(server, 0);
            context.waitTicks(2);
            this.pressReload(context);
            context.waitTicks(5);
            if (this.cooling(server)[0]) {
                failures.append("the key started cooling a cold minigun\n");
            }

            // One in each hand: both cool.
            this.equip(server, "jeg:minigun", "jeg:minigun");
            context.waitTicks(40);
            this.setHeat(server, OVERHEATED);
            context.waitTicks(2);
            this.pressReload(context);
            context.waitTicks(5);
            boolean[] bothCooling = this.cooling(server);
            int puffsBoth = context.computeOnClient(mc -> MinigunClient.ventPuffs);
            context.waitTicks(20);
            context.takeScreenshot("minigun_rapid_cool_3_dual");
            puffsBoth = context.computeOnClient(mc -> MinigunClient.ventPuffs) - puffsBoth;
            report.append(String.format("two miniguns: %d puffs of gas in 20 ticks%n", puffsBoth));
            if (puffsBoth < 90) {
                failures.append(String.format("with a minigun in each hand only %d puffs of gas in 20 ticks: both should vent%n", puffsBoth));
            }
            context.waitTicks(COOL_LIMIT_TICKS - 20);
            int[] bothHeat = this.heat(server);
            report.append(String.format("two miniguns: cooling main %s, off hand %s; heat %d ticks later main %d, off hand %d%n",
                    bothCooling[0], bothCooling[1], COOL_LIMIT_TICKS + 5, bothHeat[0], bothHeat[1]));
            if (!bothCooling[0] || !bothCooling[1] || bothHeat[0] != 0 || bothHeat[1] != 0) {
                failures.append("with a minigun in each hand the key did not cool both\n");
            }

            // The main gun can finish much earlier than the off hand (or already be cold when R is pressed).
            // Neither case may stop and restart the shared barrel animation and hiss while the off hand cools.
            for (int mainHeat : new int[]{40, 0}) {
                context.waitTicks(50);
                server.runOnServer(s -> {
                    var player = s.getPlayerList().getPlayers().getFirst();
                    player.getMainHandItem().set(ModDataComponents.GUN_HEAT.get(), mainHeat);
                    player.getOffhandItem().set(ModDataComponents.GUN_HEAT.get(), OVERHEATED);
                });
                context.waitTicks(2);
                this.pressReload(context);
                context.waitTicks(25);
                boolean[] unequalCooling = this.cooling(server);
                float spin = context.computeOnClient(mc -> MinigunClient.spin(mc.player.getId()));
                boolean serverSpin = server.computeOnServer(s -> MinigunSpin.isSpinning(s.getPlayerList().getPlayers().getFirst()));
                boolean vent = context.computeOnClient(mc -> MinigunClient.venting(mc.player.getId()));
                report.append(String.format("unequal heat %d/%d: cooling %s/%s, client spin %.2f, server spinning %s, hiss %s%n",
                        mainHeat, OVERHEATED, unequalCooling[0], unequalCooling[1], spin, serverSpin, vent));
                if (unequalCooling[0] || !unequalCooling[1] || spin < 0.99F || !serverSpin || !vent) {
                    failures.append("off-hand cooling did not keep the barrels and hiss running with main heat " + mainHeat + "\n");
                }
                // Follow the last part of cooling and the entire spin-down, including the first idle ticks.
                float previousSpin = spin;
                float previousAngle = context.computeOnClient(mc -> MinigunClient.barrelAngle(mc.player.getId(), 1.0F));
                boolean reachedRest = false;
                for (int tick = 0; tick < 100; tick++) {
                    context.waitTick();
                    float[] motion = context.computeOnClient(mc -> new float[]{MinigunClient.spin(mc.player.getId()),
                            MinigunClient.barrelAngle(mc.player.getId(), 1.0F)});
                    if (previousSpin <= 0.1F && motion[0] <= previousSpin
                            && Math.abs(Mth.wrapDegrees(motion[1] - previousAngle)) > 2.0F) {
                        failures.append(String.format("barrels snapped at rest: %.2f -> %.2f degrees (main heat %d)%n",
                                previousAngle, motion[1], mainHeat));
                    }
                    reachedRest |= motion[0] == 0.0F;
                    previousSpin = motion[0];
                    previousAngle = motion[1];
                }
                if (!reachedRest || this.cooling(server)[1]) {
                    failures.append("off-hand cooling did not finish and spin down\n");
                }
            }
            this.equip(server, "minecraft:air", "minecraft:air");
            context.waitTicks(5);
            KeyMapping irisReload = context.computeOnClient(mc -> {
                for (KeyMapping mapping : mc.options.keyMappings) {
                    if ("iris.keybind.reload".equals(mapping.getName())) {
                        return mapping;
                    }
                }
                return null;
            });
            if (irisReload != null) {
                // R still reloads shaders without a gun; an explicitly distinct shader key works with a gun too.
                this.checkShaderReload(context, irisReload);
                this.equip(server, "jeg:minigun", "minecraft:air");
                context.waitTicks(40);
                InputConstants.Key original = InputConstants.getKey(irisReload.saveString());
                try {
                    context.runOnClient(mc -> {
                        irisReload.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F8));
                        KeyMapping.resetMapping();
                    });
                    this.checkShaderReload(context, irisReload);
                } finally {
                    context.runOnClient(mc -> {
                        irisReload.setKey(original);
                        KeyMapping.resetMapping();
                    });
                }
                report.append("Iris: gun reload preserves the shader pipeline; empty hands and a distinct shader key still reload it\n");
            }
        } finally {
            context.runOnClient(mc -> mc.options.hideGui = hidGui);
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    private void pressReload(ClientGameTestContext context) {
        Object pipeline = context.computeOnClient(mc -> shaderPipeline());
        context.getInput().holdKey(options -> KeyBindings.RELOAD);
        context.waitTicks(2);
        context.getInput().releaseKey(options -> KeyBindings.RELOAD);
        if (pipeline != null && context.computeOnClient(mc -> shaderPipeline()) != pipeline) {
            throw new AssertionError("The gun reload key also rebuilt the Iris shader pipeline");
        }
    }

    private void checkShaderReload(ClientGameTestContext context, KeyMapping key) {
        Object pipeline = context.computeOnClient(mc -> shaderPipeline());
        context.getInput().pressKey(key);
        context.waitTicks(3);
        if (pipeline == null || context.computeOnClient(mc -> shaderPipeline()) == pipeline) {
            throw new AssertionError("The Iris shader reload key stopped working outside the conflicting gun binding");
        }
    }

    /** Keep the regression runnable without Iris on the bare client. */
    private static Object shaderPipeline() {
        try {
            Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
            Object manager = iris.getMethod("getPipelineManager").invoke(null);
            return manager.getClass().getMethod("getPipelineNullable").invoke(manager);
        } catch (ClassNotFoundException absent) {
            return null;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not inspect Iris's shader pipeline", failure);
        }
    }

    private void equip(TestServerContext server, String main, String offhand) {
        server.runCommand("item replace entity @p hotbar.0 with " + main);
        server.runCommand("item replace entity @p weapon.offhand with " + offhand);
        server.runCommand("item replace entity @p hotbar.8 with jeg:pistol_ammo 64");
        server.runCommand("item replace entity @p hotbar.7 with jeg:pistol_ammo 64");
        server.runCommand("item replace entity @p hotbar.6 with jeg:pistol_ammo 64");
    }

    private int rounds(TestServerContext server) {
        return server.computeOnServer(s -> {
            var inventory = s.getPlayerList().getPlayers().getFirst().getInventory();
            return inventory.getItem(6).getCount() + inventory.getItem(7).getCount() + inventory.getItem(8).getCount();
        });
    }

    /** Sets the heat of the miniguns in hand. */
    private void setHeat(TestServerContext server, int heat) {
        server.runOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
                if (MinigunSpin.isMinigun(stack)) {
                    stack.set(ModDataComponents.GUN_HEAT.get(), heat);
                }
            }
        });
    }

    /** Heat of the main-hand and off-hand items. */
    private int[] heat(TestServerContext server) {
        return server.computeOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            return new int[]{player.getMainHandItem().getOrDefault(ModDataComponents.GUN_HEAT.get(), 0),
                    player.getOffhandItem().getOrDefault(ModDataComponents.GUN_HEAT.get(), 0)};
        });
    }

    private boolean[] cooling(TestServerContext server) {
        return server.computeOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            return new boolean[]{GunItem.isRapidCooling(player.getMainHandItem()), GunItem.isRapidCooling(player.getOffhandItem())};
        });
    }
}
