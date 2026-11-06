package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.FabricClientBootstrap;

/**
 * Walks a gun through the states a player sees it in: settled at idle, walking, into and out of a sprint, firing and being
 * swapped for another gun. Logs where the gun sits on screen each tick so jumps show up as numbers, and takes screenshots
 * so the hands can be checked against the gun.
 */
public class GunMotionClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final String[] GUNS = {"minigun", "assault_rifle"};
    private static final String[] GALLERY = {
            "minigun", "assault_rifle", "combat_pistol", "pump_shotgun", "light_machine_gun", "rocket_launcher",
            "flamethrower", "hypersonic_cannon", "typhoonee", "grenade_launcher", "repeating_shotgun",
            "waterpipe_shotgun", "compound_bow", "finger_gun"
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 0");
            for (String gun : GALLERY) {
                this.give(server, gun);
                context.waitTicks(60);
                context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
                context.takeScreenshot("motion_gallery_" + gun);
            }
            for (String gun : GUNS) {
                this.give(server, gun);
                context.waitTicks(60);
                this.exercise(context, gun);
            }
            this.swap(context, server);
        }
    }

    private void give(TestServerContext server, String gun) {
        server.runCommand("item replace entity @p weapon.mainhand with jeg:" + gun);
    }

    private void exercise(ClientGameTestContext context, String gun) {
        StringBuilder log = new StringBuilder();
        this.record(context, log, gun + " idle", 10, null);

        context.getInput().holdKey(options -> options.keyUp);
        this.record(context, log, gun + " walk", 30, gun + "_walk");

        context.runOnClient(mc -> mc.player.setSprinting(true));
        context.getInput().holdKey(options -> options.keySprint);
        this.record(context, log, gun + " sprint-in", 30, gun + "_sprint_in");
        context.getInput().releaseKey(options -> options.keySprint);
        context.getInput().releaseKey(options -> options.keyUp);
        context.runOnClient(mc -> mc.player.setSprinting(false));
        this.record(context, log, gun + " sprint-out", 30, gun + "_sprint_out");

        context.getInput().holdKey(options -> options.keyAttack);
        this.record(context, log, gun + " fire", 30, gun + "_fire");
        context.getInput().releaseKey(options -> options.keyAttack);
        this.record(context, log, gun + " fire-stop", 20, null);
        context.runOnClient(mc -> mc.player.setXRot(0.0F));
        LOGGER.info("[gun-motion] {}\n{}", gun, log);
    }

    private void swap(ClientGameTestContext context, TestServerContext server) {
        StringBuilder log = new StringBuilder();
        this.give(server, "assault_rifle");
        context.runOnClient(mc -> mc.player.setXRot(0.0F));
        context.waitTicks(60);
        server.runCommand("item replace entity @p hotbar.1 with jeg:minigun");
        context.waitTicks(5);
        context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(1));
        this.record(context, log, "swap to-minigun", 45, "swap_to_minigun");
        context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
        this.record(context, log, "swap to-rifle", 45, "swap_to_rifle");
        LOGGER.info("[gun-motion] swap\n{}", log);
    }

    private void record(ClientGameTestContext context, StringBuilder log, String phase, int ticks, String shots) {
        for (int tick = 1; tick <= ticks; tick++) {
            context.waitTick();
            log.append(this.sample(context, phase, tick));
            if (shots != null && (tick <= 8 || tick % 4 == 0)) {
                context.takeScreenshot("motion_" + shots + "_t" + tick);
            }
        }
    }

    private String sample(ClientGameTestContext context, String phase, int tick) {
        return context.computeOnClient(mc -> {
            Matrix4f pose = FabricClientBootstrap.firstPersonGunPose(HumanoidArm.RIGHT);
            if (pose == null) {
                return phase + " t" + tick + " no pose\n";
            }
            Vector3f position = pose.getTranslation(new Vector3f());
            Vector3f forward = pose.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F)).normalize();
            return String.format("%s t%d x=%.3f y=%.3f z=%.3f fwd=%.3f,%.3f,%.3f sprinting=%s pitch=%.2f yaw=%.2f%n",
                    phase, tick, position.x, position.y, position.z, forward.x, forward.y, forward.z, mc.player.isSprinting(),
                    mc.player.getXRot(), mc.player.getYRot());
        });
    }
}
