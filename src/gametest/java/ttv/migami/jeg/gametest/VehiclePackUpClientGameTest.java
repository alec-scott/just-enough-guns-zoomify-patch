package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
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

/**
 * Every vehicle in the creator can be picked up and moved (the user's question, 2026-11-19: "can the turrets be mined and
 * moved?"). Each is set down damaged, then sneak-right-clicked with the crowbar as a player does: it must come back as a
 * vehicle container item; the item placed on the ground and opened with the crowbar must give the same vehicle back, as
 * damaged as it went in. The mortar and the TOW, carried by hand, pack into their own items on a sneak-click with an
 * empty hand.
 */
public class VehiclePackUpClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private static final String[] BOXED = {"sentry", "hpj11", "mk42", "bl132", "type63", "howitzer", "bmp2", "lav150",
            "plz05", "yx100", "bulldozer", "speedboat", "tom6", "ah6", "mi28", "a10", "truck"};

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("gamerule spawn_mobs false");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            server.runCommand("forceload add -48 -48 48 48");
            for (String id : BOXED) {
                String result = server.computeOnServer(s -> this.boxAndUnbox(s, id));
                context.waitTicks(2);
                LOG.info("[pack-up] {}: {}", id, result);
                if (!result.startsWith("ok")) failures.append(id).append(": ").append(result).append('\n');
            }
            for (String id : new String[]{"mortar", "tow"}) {
                String result = server.computeOnServer(s -> this.carried(s, id));
                context.waitTicks(2);
                LOG.info("[pack-up] {}: {}", id, result);
                if (!result.startsWith("ok")) failures.append(id).append(": ").append(result).append('\n');
            }
        }
        if (failures.length() > 0) {
            LOG.error("[pack-up] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[pack-up] COMPLETE");
    }

    private String boxAndUnbox(MinecraftServer s, String id) {
        var level = s.overworld();
        ServerPlayer player = s.getPlayerList().getPlayers().get(0);
        player.getInventory().clearContent();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Reference.id(id));
        if (!(type.create(level, EntitySpawnReason.COMMAND) instanceof VehicleEntity vehicle)) return "not a vehicle";
        vehicle.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(vehicle);
        vehicle.hurtServer(level, level.damageSources().generic(), vehicle.maxVehicleHealth() * 0.3F);
        float health = vehicle.vehicleHealth();
        // Stand off its side (outside the FSV-6's hitch zone at the back), sneaking, crowbar in hand.
        player.teleportTo(level, 0.5D + vehicle.getBbWidth() * 0.5D + 1.5D, G, 0.5D, java.util.Set.of(), 90.0F, 20.0F, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CROWBAR.get()));
        player.setShiftKeyDown(true);
        vehicle.interact(player, InteractionHand.MAIN_HAND, new Vec3(-vehicle.getBbWidth() * 0.5D, 0.8D, 0.2D));
        player.setShiftKeyDown(false);
        if (vehicle.isAlive()) return "the crowbar did not pack it";
        ItemStack box = ItemStack.EMPTY;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.VEHICLE_CONTAINER.get())) box = player.getInventory().getItem(i);
        }
        if (box.isEmpty()) return "no container item came back";
        // Place the box on the ground ahead and open it with the crowbar.
        player.teleportTo(level, 0.5D, G, -4.5D, java.util.Set.of(), 0.0F, 30.0F, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, box.copy());
        BlockPos ground = new BlockPos(0, G - 1, 0);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(ground).add(0.0D, 0.5D, 0.0D), Direction.UP, ground, false)));
        if (!(level.getBlockEntity(ground.above()) instanceof VehicleContainerBlockEntity container)) return "the box could not be placed";
        if (!container.deploy(player)) return "the box would not open";
        var back = level.getEntitiesOfClass(VehicleEntity.class, new AABB(ground).inflate(12.0D), v -> v.getType() == type);
        if (back.size() != 1) return "the box gave " + back.size() + " vehicles back";
        float after = back.get(0).vehicleHealth();
        back.get(0).discard();
        if (Math.abs(after - health) > 0.5F) return "health " + health + " went in, " + after + " came out";
        return "ok (" + health + " hp kept)";
    }

    private String carried(MinecraftServer s, String id) {
        var level = s.overworld();
        ServerPlayer player = s.getPlayerList().getPlayers().get(0);
        player.getInventory().clearContent();
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Reference.id(id));
        if (!(type.create(level, EntitySpawnReason.COMMAND) instanceof VehicleEntity vehicle)) return "not a vehicle";
        vehicle.snapTo(0.5D, G, 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(vehicle);
        player.teleportTo(level, 2.5D, G, 0.5D, java.util.Set.of(), 90.0F, 20.0F, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setShiftKeyDown(true);
        vehicle.interact(player, InteractionHand.MAIN_HAND, new Vec3(-0.5D, 0.5D, 0.0D));
        player.setShiftKeyDown(false);
        if (vehicle.isAlive()) return "a sneak-click did not pack it";
        var item = id.equals("mortar") ? ModItems.MORTAR.get() : ModItems.TOW.get();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(item)) return "ok";
        }
        return "its item did not come back";
    }
}
