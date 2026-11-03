package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;

/**
 * Screenshots the first-person arms every other tick through an automatic burst and a few semi-automatic shots. The arms
 * and gun should stay in the aiming pose with only the recoil kick; they must not slide in from the default (bind) pose
 * on each shot.
 */
public class GunHandPoseClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 0");
            this.shoot(context, server, "jeg:assault_rifle", "auto", true);
            // Same rifle again: the first burst also covers a brand-new stack's first ever shot.
            this.shoot(context, server, null, "auto_again", true);
            this.shoot(context, server, "jeg:combat_pistol", "semi", false);
        }
    }

    private void shoot(ClientGameTestContext context, TestServerContext server, String gun, String label, boolean hold) {
        if (gun != null) {
            server.runCommand("item replace entity @p weapon.mainhand with " + gun);
        }
        context.waitTicks(40);
        context.takeScreenshot("hands_" + label + "_idle");
        if (hold) {
            context.getInput().holdKey(options -> options.keyAttack);
            for (int tick = 1; tick <= 12; tick++) {
                context.waitTick();
                if (tick % 2 == 0) {
                    context.takeScreenshot("hands_" + label + "_t" + tick);
                }
            }
            context.getInput().releaseKey(options -> options.keyAttack);
        } else {
            for (int shot = 1; shot <= 3; shot++) {
                context.getInput().holdKey(options -> options.keyAttack);
                context.waitTick();
                context.takeScreenshot("hands_" + label + "_shot" + shot + "_a");
                context.getInput().releaseKey(options -> options.keyAttack);
                context.waitTick();
                context.takeScreenshot("hands_" + label + "_shot" + shot + "_b");
                context.waitTicks(6);
            }
        }
        context.waitTicks(20);
        context.takeScreenshot("hands_" + label + "_after");
    }
}
