package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.render.BulletTrailRenderer;
import ttv.migami.jeg.client.render.gun.GunMuzzleTracker;
import ttv.migami.jeg.item.GunItem;

/**
 * A minigun in each hand: the trigger spins up and fires both (twice the rounds), each with its own heat, its tracers
 * leaving its own muzzle on its own side, and both guns held out in front in third person. A minigun in the off hand on
 * its own, or a second gun that is not a minigun, does not fire. Two miniguns are slower to carry than one.
 */
public class MinigunDualWieldClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int TRIGGER_TICKS = 40;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("minigun dual wield report\n");
        StringBuilder failures = new StringBuilder();
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
            this.ammo(server);

            // One minigun, for comparison.
            this.equip(server, "jeg:minigun", "minecraft:air");
            context.waitTicks(40);
            double singleSpeed = this.walkSpeed(server);
            int single = this.fire(context, server, null);

            // Two miniguns.
            this.equip(server, "jeg:minigun", "jeg:minigun");
            context.waitTicks(40);
            double dualSpeed = this.walkSpeed(server);
            // F1 would hide the hands too, so only the chat is cleared for the pictures.
            context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
            context.takeScreenshot("dual_minigun_first_person_idle");
            double[] tracerSides = {Double.MAX_VALUE, -Double.MAX_VALUE};
            int dual = this.fire(context, server, tracerSides);
            int[] heat = server.computeOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                return new int[]{((GunItem) player.getMainHandItem().getItem()).getOverheatPercent(player.getMainHandItem()),
                        ((GunItem) player.getOffhandItem().getItem()).getOverheatPercent(player.getOffhandItem())};
            });

            // Third person: both guns out in front, each with its own muzzle.
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(30);
            context.takeScreenshot("dual_minigun_third_person_front");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(5);
            context.takeScreenshot("dual_minigun_third_person_back");
            Vec3[] muzzles = context.computeOnClient(mc -> new Vec3[]{
                    GunMuzzleTracker.muzzle(mc.player.getId(), false, HumanoidArm.RIGHT),
                    GunMuzzleTracker.muzzle(mc.player.getId(), false, HumanoidArm.LEFT),
                    mc.player.position(), Vec3.directionFromRotation(0.0F, mc.player.getYRot())});
            context.getInput().releaseKey(options -> options.keyAttack);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(50);

            // Aiming with both: spins them up.
            context.getInput().holdKey(options -> options.keyUse);
            context.waitTicks(30);
            context.takeScreenshot("dual_minigun_first_person_aim");
            context.getInput().releaseKey(options -> options.keyUse);
            context.waitTicks(50);

            // A minigun in the off hand alone, and a rifle beside a minigun: only a pair of miniguns fires both.
            this.equip(server, "minecraft:air", "jeg:minigun");
            context.waitTicks(40);
            int offhandAlone = this.fire(context, server, null);
            this.equip(server, "jeg:minigun", "jeg:assault_rifle");
            context.waitTicks(40);
            int withRifle = this.fire(context, server, null);

            double muzzleGap = muzzles[0] != null && muzzles[1] != null ? muzzles[0].distanceTo(muzzles[1]) : -1.0D;
            // Across the body (positive: the player's right) and height above the feet: each gun out in front on its own
            // side at about chest height, not crossed over or hanging down.
            Vec3 bodyRight = muzzles[3].cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
            double rightSide = muzzles[0] != null ? muzzles[0].subtract(muzzles[2]).dot(bodyRight) : Double.NaN;
            double leftSide = muzzles[1] != null ? muzzles[1].subtract(muzzles[2]).dot(bodyRight) : Double.NaN;
            double muzzleHeight = muzzles[0] != null ? muzzles[0].y - muzzles[2].y : Double.NaN;
            report.append(String.format("third person: right gun %.2f across, left gun %.2f across, muzzles %.2f above the feet%n",
                    rightSide, leftSide, muzzleHeight));
            if (!(rightSide > 0.1D && leftSide < -0.1D)) {
                failures.append(String.format("third person: the guns are not each on their own side (right %.2f, left %.2f)%n", rightSide, leftSide));
            }
            if (!(muzzleHeight > 0.8D)) {
                failures.append(String.format("third person: the guns hang down (muzzle %.2f above the feet)%n", muzzleHeight));
            }
            report.append(String.format("rounds in %d ticks of trigger: one minigun %d, two %d, off hand alone %d, minigun + rifle %d%n",
                    TRIGGER_TICKS, single, dual, offhandAlone, withRifle));
            report.append(String.format("heat after firing two: main %d%%, off hand %d%%%n", heat[0], heat[1]));
            report.append(String.format("tracer starts across the view: from %.2f to %.2f (left negative)%n", tracerSides[0], tracerSides[1]));
            report.append(String.format("third person muzzles: right %s, left %s, %.2f apart%n", muzzles[0], muzzles[1], muzzleGap));
            report.append(String.format("walk speed: one minigun %.4f, two %.4f%n", singleSpeed, dualSpeed));

            if (dual < single * 1.7D) {
                failures.append(String.format("two miniguns fired %d rounds against one's %d%n", dual, single));
            }
            if (heat[1] <= 0) {
                failures.append("the off-hand minigun never heated up, so it never fired\n");
            }
            if (tracerSides[0] > -0.15D || tracerSides[1] < 0.15D) {
                failures.append(String.format("tracers did not leave both guns: starts ran %.2f to %.2f across the view%n", tracerSides[0], tracerSides[1]));
            }
            if (muzzleGap < 0.4D) {
                failures.append(String.format("third person: the two muzzles are not apart (%.2f)%n", muzzleGap));
            }
            if (offhandAlone > 0) {
                failures.append(String.format("a minigun in the off hand alone fired %d rounds%n", offhandAlone));
            }
            if (withRifle > single * 1.3D) {
                failures.append(String.format("a rifle in the off hand fired along with the minigun (%d rounds)%n", withRifle));
            }
            if (dualSpeed >= singleSpeed) {
                failures.append("two miniguns are no slower to carry than one\n");
            }
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    private void equip(TestServerContext server, String main, String offhand) {
        server.runCommand("item replace entity @p weapon.mainhand with " + main);
        server.runCommand("item replace entity @p weapon.offhand with " + offhand);
        this.ammo(server);
    }

    private void ammo(TestServerContext server) {
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

    private double walkSpeed(TestServerContext server) {
        return server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getAttributeValue(Attributes.MOVEMENT_SPEED));
    }

    /** Holds the trigger and returns the rounds used; with {@code tracerSides}, notes how far left and right tracers start. */
    private int fire(ClientGameTestContext context, TestServerContext server, double[] tracerSides) {
        int before = this.rounds(server);
        context.getInput().holdKey(options -> options.keyAttack);
        for (int tick = 0; tick < TRIGGER_TICKS; tick++) {
            context.waitTick();
            if (tracerSides != null) {
                double[] sides = context.computeOnClient(MinigunDualWieldClientGameTest::tracerSides);
                for (double side : sides) {
                    tracerSides[0] = Math.min(tracerSides[0], side);
                    tracerSides[1] = Math.max(tracerSides[1], side);
                }
                if (tick == 30) {
                    context.takeScreenshot("dual_minigun_first_person_firing");
                }
            }
        }
        context.getInput().releaseKey(options -> options.keyAttack);
        context.waitTicks(60);
        return before - this.rounds(server);
    }

    /** How far right (negative: left) of the eye the latest tracers start, across the view. */
    private static double[] tracerSides(Minecraft mc) {
        Vec3 look = mc.player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        return BulletTrailRenderer.recentTracerStarts().stream()
                .mapToDouble(start -> start.subtract(mc.player.getEyePosition()).dot(right))
                .toArray();
    }
}
