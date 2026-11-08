package ttv.migami.jeg.gametest;

import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;

/**
 * Looting on a gun: /enchant and the anvil accept it on the assault rifle, the enchanting table can roll it, a bow still
 * cannot take it, and skeletons shot dead with a Looting III rifle drop clearly more than with a plain one.
 */
public class GunLootingClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int KILLS = 10;

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("gun looting report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule do_mob_loot true");
            server.runCommand("time set midnight");
            server.runCommand("gamemode survival @a");
            server.runCommand("justEnoughGuns config combat magazineFeed false");
            server.runCommand("tp @a 0 ~ 0 0 7");

            server.runCommand("item replace entity @p weapon.mainhand with jeg:assault_rifle");
            server.runCommand("enchant @p minecraft:looting 3");
            context.waitTicks(2);
            int commandLevel = this.heldLooting(server);
            server.runCommand("item replace entity @p weapon.mainhand with minecraft:bow");
            server.runCommand("enchant @p minecraft:looting 3");
            context.waitTicks(2);
            int bowLevel = this.heldLooting(server);
            int anvilLevel = this.anvilLootingOnto(server, "jeg:assault_rifle");
            boolean tableOffers = this.enchantingTableOffersLooting(server);
            report.append(String.format("/enchant looting on rifle -> %d, on bow -> %d; anvil book onto rifle -> %d; enchanting table can roll it: %s%n",
                    commandLevel, bowLevel, anvilLevel, tableOffers));
            if (commandLevel != 3) {
                failures.append("/enchant should put Looting III on a gun\n");
            }
            if (bowLevel != 0) {
                failures.append("a bow must still refuse Looting\n");
            }
            if (anvilLevel != 3) {
                failures.append("the anvil should put a Looting III book onto a gun\n");
            }
            if (!tableOffers) {
                failures.append("the enchanting table should be able to roll Looting on a gun\n");
            }

            int plainDrops = this.shootSkeletons(context, server, report, "plain", false);
            int lootingDrops = this.shootSkeletons(context, server, report, "looting3", true);
            report.append(String.format("drops from %d skeleton kills: plain %d, looting III %d%n", KILLS, plainDrops, lootingDrops));
            if (lootingDrops < plainDrops + KILLS || lootingDrops < plainDrops * 1.5F) {
                failures.append("Looting III gun kills should drop clearly more than plain ones\n");
            }
            server.runCommand("justEnoughGuns config combat magazineFeed true");
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    /** Shoots {@code KILLS} one-health skeletons dead one at a time and returns how many bones and arrows they dropped. */
    private int shootSkeletons(ClientGameTestContext context, TestServerContext server, StringBuilder report, String label, boolean looting) {
        server.runCommand("clear @p");
        server.runCommand("item replace entity @p weapon.mainhand with jeg:assault_rifle");
        if (looting) {
            server.runCommand("enchant @p minecraft:looting 3");
        }
        server.runCommand("give @p jeg:rifle_ammo 64");
        context.waitTicks(5);
        context.getInput().pressKey(KeyBindings.RELOAD);
        context.waitTicks(100);
        int drops = 0;
        int kills = 0;
        for (int i = 0; i < KILLS; i++) {
            server.runCommand("kill @e[type=minecraft:item]");
            server.runCommand("execute as @p at @s rotated 0 0 run summon minecraft:skeleton ^ ^ ^5 "
                    + "{NoAI:1b,PersistenceRequired:1b,Health:1f}");
            context.waitTicks(10);
            for (int shot = 0; shot < 10 && this.skeletonAlive(server); shot++) {
                server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 7");
                context.getInput().holdKey(options -> options.keyAttack);
                context.waitTicks(2);
                context.getInput().releaseKey(options -> options.keyAttack);
                context.waitTicks(8);
            }
            if (!this.skeletonAlive(server)) {
                kills++;
            }
            context.waitTicks(10);
            drops += this.countDrops(server);
            server.runCommand("kill @e[type=minecraft:skeleton]");
        }
        context.takeScreenshot("gun_looting_" + label);
        report.append(String.format("%s: %d/%d skeletons shot dead%n", label, kills, KILLS));
        if (kills < KILLS) {
            throw new AssertionError("Only " + kills + " of " + KILLS + " skeletons were shot dead (" + label + "): " + report);
        }
        return drops;
    }

    private boolean skeletonAlive(TestServerContext server) {
        return server.computeOnServer(s -> {
            for (Entity entity : s.overworld().getAllEntities()) {
                if (entity.getType() == net.minecraft.world.entity.EntityType.SKELETON && entity.isAlive()) {
                    return true;
                }
            }
            return false;
        });
    }

    private int countDrops(TestServerContext server) {
        return server.computeOnServer(s -> {
            int count = 0;
            for (Entity entity : s.overworld().getAllEntities()) {
                if (entity instanceof ItemEntity item) {
                    String id = BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).getPath();
                    if (id.equals("bone") || id.equals("arrow")) {
                        count += item.getItem().getCount();
                    }
                }
            }
            return count;
        });
    }

    private int heldLooting(TestServerContext server) {
        return server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            var looting = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
            return EnchantmentHelper.getItemEnchantmentLevel(looting, player.getMainHandItem());
        });
    }

    private int anvilLootingOnto(TestServerContext server, String itemId) {
        return server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            var looting = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
            ItemStack item = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId)));
            ItemStack book = EnchantmentHelper.createBook(new EnchantmentInstance(looting, 3));
            player.experienceLevel = 100;
            AnvilMenu anvil = new AnvilMenu(0, player.getInventory());
            anvil.getSlot(0).set(item);
            anvil.getSlot(1).set(book);
            anvil.createResult();
            ItemStack result = anvil.getSlot(2).getItem();
            return result.isEmpty() ? 0 : EnchantmentHelper.getItemEnchantmentLevel(looting, result);
        });
    }

    private boolean enchantingTableOffersLooting(TestServerContext server) {
        return server.computeOnServer(s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            var registry = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var looting = registry.getOrThrow(Enchantments.LOOTING);
            ItemStack rifle = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("jeg:assault_rifle")));
            var tableEnchantments = registry.get(EnchantmentTags.IN_ENCHANTING_TABLE).orElseThrow();
            List<EnchantmentInstance> results = EnchantmentHelper.getAvailableEnchantmentResults(30, rifle, tableEnchantments.stream());
            return results.stream().anyMatch(instance -> instance.enchantment().equals(looting));
        });
    }
}
