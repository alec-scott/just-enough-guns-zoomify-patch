package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.FabricClientBootstrap;
import ttv.migami.jeg.client.MinigunClient;
import ttv.migami.jeg.gun.MinigunSpin;

/**
 * Aiming should spin the barrels without firing; the trigger should spin them up and only then fire, with the sound following
 * along. Also measures how fast the player walks with light and heavy guns, and shoots at noon and midnight to check the
 * flame and tracers.
 */
public class MinigunSpinClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a minecraft:resistance 600 5 true");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 0");
            server.runCommand("item replace entity @p hotbar.8 with jeg:pistol_ammo 64");
            server.runCommand("item replace entity @p weapon.mainhand with jeg:minigun");
            context.waitTicks(60);

            StringBuilder log = new StringBuilder();
            context.getInput().holdKey(options -> options.keyUse);
            this.record(context, log, "aim", 40, null);
            this.assertSpin(context, 1.0F);
            if (this.ammo(context) != 64) {
                throw new AssertionError("Aiming should spin the minigun without firing: " + log);
            }
            context.takeScreenshot("minigun_aim_spinning");
            context.getInput().releaseKey(options -> options.keyUse);
            // The heavy gun finishes lowering out of ADS before its barrel spin-down begins.
            this.record(context, log, "aim-release", MinigunSpin.SPIN_DOWN_TICKS + 18, null);
            this.assertSpin(context, 0.0F);

            context.getInput().holdKey(options -> options.keyAttack);
            this.record(context, log, "trigger", 45, "minigun_day");
            if (this.ammo(context) >= 64) {
                throw new AssertionError("Holding the trigger should fire after the minigun spins up: " + log);
            }
            context.getInput().releaseKey(options -> options.keyAttack);
            this.record(context, log, "trigger-release", 45, null);
            this.assertSpin(context, 0.0F);

            server.runCommand("time set midnight");
            context.runOnClient(mc -> mc.player.setXRot(0.0F));
            context.waitTicks(20);
            context.getInput().holdKey(options -> options.keyAttack);
            this.record(context, log, "night", 40, "minigun_night");
            context.getInput().releaseKey(options -> options.keyAttack);
            context.waitTicks(10);
            LOGGER.info("[minigun] spin\n{}", log);

            server.runCommand("time set noon");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(30);
            context.takeScreenshot("minigun_third_person");
            context.getInput().releaseKey(options -> options.keyAttack);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(30);

            server.runCommand("gamemode creative @a");
            server.runCommand("give @p minecraft:stone 1");
            StringBuilder speeds = new StringBuilder();
            for (String gun : new String[]{"custom_smg", "combat_pistol", "assault_rifle", "light_machine_gun", "minigun"}) {
                server.runCommand("tp @a 0 ~ 0 0 0");
                server.runCommand("item replace entity @p weapon.mainhand with jeg:" + gun);
                context.waitTicks(50);
                speeds.append(this.walk(context, gun));
            }
            LOGGER.info("[minigun] speeds\n{}", speeds);
        }
    }

    private String walk(ClientGameTestContext context, String gun) {
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(10);
        Vec3 from = context.computeOnClient(mc -> mc.player.position());
        context.waitTicks(40);
        Vec3 to = context.computeOnClient(mc -> mc.player.position());
        double attribute = context.computeOnClient(mc -> mc.player.getAttributeValue(Attributes.MOVEMENT_SPEED));
        double fov = context.computeOnClient(mc -> (double) mc.player.getFieldOfViewModifier(true, 1.0F));
        context.getInput().releaseKey(options -> options.keyUp);
        context.waitTicks(10);
        return String.format("%s blocks/tick=%.4f attribute=%.4f fov=%.3f%n", gun, from.distanceTo(to) / 40.0D, attribute, fov);
    }

    private void record(ClientGameTestContext context, StringBuilder log, String phase, int ticks, String shots) {
        int ammoAtStart = this.ammo(context);
        for (int tick = 1; tick <= ticks; tick++) {
            context.waitTick();
            int at = tick;
            String sample = context.computeOnClient(mc -> {
                int id = mc.player.getId();
                return String.format("%s t%d spin=%.2f audio=%s flash=%s ammo=%d heldMinigun=%s%n", phase, at, MinigunClient.spin(id),
                        MinigunClient.audioState(id), FabricClientBootstrap.hasMuzzleFlash(id),
                        mc.player.getInventory().getItem(8).getCount(), MinigunSpin.isMinigun(mc.player.getMainHandItem()));
            });
            log.append(sample);
            if ("trigger".equals(phase) && tick <= MinigunSpin.SPOOL_TICKS - 3 && this.ammo(context) != ammoAtStart) {
                throw new AssertionError("The minigun fired before its barrels spun up: " + log);
            }
            if (shots != null && tick > 14 && tick % 5 == 0) {
                context.takeScreenshot(shots + "_t" + tick);
            }
        }
    }

    private int ammo(ClientGameTestContext context) {
        return context.computeOnClient(mc -> mc.player.getInventory().getItem(8).getCount());
    }

    private void assertSpin(ClientGameTestContext context, float expected) {
        float spin = context.computeOnClient(mc -> MinigunClient.spin(mc.player.getId()));
        if (Math.abs(spin - expected) > 0.01F) {
            throw new AssertionError("Expected minigun spin " + expected + ", got " + spin);
        }
    }
}
