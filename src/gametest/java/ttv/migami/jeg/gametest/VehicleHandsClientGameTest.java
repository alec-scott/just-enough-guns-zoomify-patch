package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * A pilot's or gunner's hands are on the vehicle's controls: right-click aims the vehicle weapon and must not also use
 * the item in hand (eating a golden apple, which can always be eaten), and left-click fires without swinging it.
 */
public class VehicleHandsClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("effect give @a resistance infinite 255 true");
            server.runCommand("time set noon");
            this.check(context, server, "mi28", 0);
            this.check(context, server, "ah6", 300);
        }
    }

    private void check(ClientGameTestContext context, TestServerContext server, String id, int x) {
        server.runCommand("ride @p dismount");
        server.runCommand("tp @p " + x + " -60 0 0 0");
        server.runCommand("execute as @p at @s run summon jeg:" + id + " ~ ~ ~5");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=jeg:" + id + ",sort=nearest,limit=1]");
        context.waitFor(mc -> mc.player.getVehicle() instanceof VehicleEntity);
        context.waitTicks(10);
        server.runCommand("item replace entity @p weapon.mainhand with minecraft:golden_apple 4");
        context.waitTicks(5);
        boolean banned = context.computeOnClient(mc -> ((VehicleEntity) mc.player.getVehicle()).shouldBanPassengerHand(mc.player));
        if (!banned) {
            throw new AssertionError(id + ": the pilot's seat should put the hands on the controls");
        }

        context.getInput().holdKey(options -> options.keyUse);
        context.waitTicks(50);
        boolean using = context.computeOnClient(mc -> mc.player.isUsingItem());
        context.getInput().releaseKey(options -> options.keyUse);
        context.waitTicks(5);
        int apples = context.computeOnClient(mc -> mc.player.getMainHandItem().getCount());
        int serverApples = server.computeOnServer(s -> s.getPlayerList().getPlayers().get(0).getMainHandItem().getCount());

        context.getInput().holdKey(options -> options.keyAttack);
        context.waitTicks(20);
        boolean swinging = context.computeOnClient(mc -> mc.player.swinging);
        context.getInput().releaseKey(options -> options.keyAttack);

        LOGGER.info("[vehicle-hands] {} using={} apples={} serverApples={} swinging={}", id, using, apples, serverApples, swinging);
        if (using || apples != 4 || serverApples != 4) {
            throw new AssertionError(id + ": aiming as the pilot must not eat: using=" + using + " apples=" + apples + "/" + serverApples);
        }
        if (swinging) {
            throw new AssertionError(id + ": firing as the pilot must not swing the held item");
        }
    }
}
