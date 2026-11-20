package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.block.entity.VehicleContainerBlockEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

import java.util.List;
import java.util.Locale;

/**
 * Every vehicle comes out of its box standing on the ground where the box was (the user, 2026-11-20: the sentry came out
 * of its box in the air; "check all the vehicles when placed out of the box are landed properly"). Each box is placed on
 * flat ground as a player places it and opened with the crowbar; a second later the vehicle must rest on the ground (not
 * above it, not sunk), level, and where the box stood. The mortar and the TOW, set down from their own items, too. Also:
 * a sentry left in the air (its ground dug out) falls back to the ground. Takes a low side view of each for the model.
 */
public class VehicleBoxLandingClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    /** Every vehicle with an assembly recipe (data/jeg/vehicle_assembly). */
    private static final String[] BOXED = {"sentry", "hpj11", "mk42", "bl132", "type63", "howitzer", "bmp2", "lav150",
            "plz05", "yx100", "bulldozer", "truck", "speedboat", "tom6", "ah6", "mi28", "a10", "test_artillery",
            "test_wheel_vehicle", "test_boat", "test_helicopter", "test_aircraft"};
    private static final String[] CARRIED = {"mortar", "tow"};

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("time set 2000");
            server.runCommand("weather clear");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            server.runCommand("forceload add -48 -48 48 48");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
            });
            for (String id : BOXED) {
                this.check(context, server, id, true, failures);
            }
            for (String id : CARRIED) {
                this.check(context, server, id, false, failures);
            }
            // A sentry whose ground went from under it falls back down (artillery has no drive, but it has weight).
            server.runCommand("kill @e[type=jeg:sentry]");
            server.runOnServer(s -> {
                if (BuiltInRegistries.ENTITY_TYPE.getValue(Reference.id("sentry")).create(s.overworld(), EntitySpawnReason.COMMAND) instanceof VehicleEntity v) {
                    v.snapTo(0.5D, G + 2.5D, 0.5D, 0.0F, 0.0F);
                    s.overworld().addFreshEntity(v);
                }
            });
            context.waitTicks(40);
            String fell = server.computeOnServer(s -> {
                var found = s.overworld().getEntitiesOfClass(VehicleEntity.class, new AABB(new BlockPos(0, G, 0)).inflate(6.0D));
                return found.isEmpty() ? "gone" : String.format(Locale.ROOT, "%.3f", found.get(0).getY());
            });
            LOG.info("[box-landing] sentry left at y {} came to rest at y {}", G + 2.5D, fell);
            if (fell.equals("gone") || Math.abs(Double.parseDouble(fell) - G) > 0.06D) {
                failures.append("a sentry left in the air stayed at y ").append(fell).append('\n');
            }
            context.runOnClient(mc -> mc.options.hideGui = false);
        }
        if (failures.length() > 0) {
            LOG.error("[box-landing] FAILED:\n{}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[box-landing] COMPLETE");
    }

    private void check(ClientGameTestContext context, TestServerContext server, String id, boolean boxed, StringBuilder failures) {
        server.runCommand("gamemode survival @a");
        String placed = server.computeOnServer(s -> boxed ? this.unbox(s, id) : this.setDown(s, id));
        if (!placed.equals("ok")) {
            LOG.info("[box-landing] {}: {}", id, placed);
            failures.append(id).append(": ").append(placed).append('\n');
            return;
        }
        context.waitTicks(30);
        double[] pose = server.computeOnServer(s -> {
            List<VehicleEntity> found = s.overworld().getEntitiesOfClass(VehicleEntity.class, new AABB(new BlockPos(0, G, 0)).inflate(16.0D),
                    v -> BuiltInRegistries.ENTITY_TYPE.getKey(v.getType()).getPath().equals(id));
            if (found.size() != 1) return new double[]{found.size()};
            VehicleEntity v = found.get(0);
            return new double[]{1, v.getX(), v.getY(), v.getZ(), v.onGround() ? 1 : 0, v.getXRot(), v.roll(), v.getBbWidth(), v.getBbHeight()};
        });
        if (pose.length == 1) {
            LOG.info("[box-landing] {}: {} vehicles found", id, (int) pose[0]);
            failures.append(id).append(": ").append((int) pose[0]).append(" vehicles found after deploying\n");
            return;
        }
        double drift = Math.hypot(pose[1] - 0.5D, pose[3] - 0.5D);
        LOG.info(String.format(Locale.ROOT, "[box-landing] %s: y %.3f (ground %d) onGround %s pitch %.1f roll %.1f drift %.2f",
                id, pose[2], G, pose[4] > 0, pose[5], pose[6], drift));
        // Above the ground is the bug. A collision box whose bottom sits a pixel or so above the model's settles that much
        // (measured 2026-11-20: the test placeholders 0.1, the BMP-2 and YX-100 about 0.03).
        if (pose[2] - G > 0.02D) {
            failures.append(String.format(Locale.ROOT, "%s: stands in the air at y %.3f, the ground is %d%n", id, pose[2], G));
        }
        if (G - pose[2] > 0.125D) {
            failures.append(String.format(Locale.ROOT, "%s: sunk to y %.3f, the ground is %d%n", id, pose[2], G));
        }
        if (pose[4] <= 0) {
            failures.append(id).append(": not on the ground\n");
        }
        if (Math.abs(pose[5]) > 3.0D || Math.abs(pose[6]) > 3.0D) {
            failures.append(String.format(Locale.ROOT, "%s: not level (pitch %.1f, roll %.1f)%n", id, pose[5], pose[6]));
        }
        if (drift > 0.5D) {
            failures.append(String.format(Locale.ROOT, "%s: moved %.2f blocks from where it was set down%n", id, drift));
        }
        // A low side view: daylight under the wheels, tracks or legs shows a vehicle standing above the ground.
        // A jet's entity box is narrower than its wings: frame the aircraft by span.
        double d = switch (id) {
            case "a10" -> 24.0D;
            case "tom6" -> 16.0D;
            case "mi28" -> 15.0D;
            case "ah6" -> 11.0D;
            default -> Math.max(4.0D, pose[7] * 1.3D + 2.0D);
        };
        server.runCommand("gamemode spectator @a");
        server.runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f facing 0.5 %.2f 0.5", 0.5D + d, G + 0.9D, 0.5D, G + Math.min(pose[8] * 0.4D, 2.0D)));
        context.waitTicks(20);
        context.takeScreenshot("box_landing_" + id);
        server.runCommand("kill @e[type=jeg:" + id + "]");
        context.waitTicks(2);
    }

    /** The box placed on the ground ahead of the player, then opened (as the crowbar does). */
    private String unbox(MinecraftServer s, String id) {
        var level = s.overworld();
        ServerPlayer player = s.getPlayerList().getPlayers().get(0);
        player.getInventory().clearContent();
        player.teleportTo(level, 0.5D, G, -4.5D, java.util.Set.of(), 0.0F, 30.0F, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, VehicleContainerBlockEntity.createItemForVehicle(Reference.id(id)));
        BlockPos ground = new BlockPos(0, G - 1, 0);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0.0D, 0.5D, 0.0D), Direction.UP, ground, false)));
        if (!(level.getBlockEntity(ground.above()) instanceof VehicleContainerBlockEntity container)) return "the box could not be placed";
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CROWBAR.get()));
        if (!container.deploy(player)) return "the box would not open";
        if (!level.getBlockState(ground.above()).isAir()) return "the box was left behind";
        return "ok";
    }

    /** The mortar and the TOW: set down from their own item on the ground ahead. */
    private String setDown(MinecraftServer s, String id) {
        var level = s.overworld();
        ServerPlayer player = s.getPlayerList().getPlayers().get(0);
        player.getInventory().clearContent();
        player.teleportTo(level, 0.5D, G, -3.5D, java.util.Set.of(), 0.0F, 30.0F, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(id.equals("mortar") ? ModItems.MORTAR.get() : ModItems.TOW.get()));
        BlockPos ground = new BlockPos(0, G - 1, 0);
        var result = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0.0D, 0.5D, 0.0D), Direction.UP, ground, false)));
        return result.consumesAction() ? "ok" : "its item would not set it down (" + result + ")";
    }
}
