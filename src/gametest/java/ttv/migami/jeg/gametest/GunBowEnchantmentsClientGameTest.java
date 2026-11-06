package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.client.CameraType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.gun.GunBowEnchantments;
import ttv.migami.jeg.item.MagazineItem;

/**
 * Bow enchantments on a gun: /enchant must accept Power, Punch, Flame and Infinity on the assault rifle; Power V should
 * raise bullet damage by half, Punch should shove the target, Flame should set it alight, and Infinity should let a
 * survival player empty and reload the rifle without the carried rounds going down.
 */
public class GunBowEnchantmentsClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    private record Burst(float damage, double pushed, boolean burning) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        List<Float> damage = new ArrayList<>();
        AtomicInteger bullets = new AtomicInteger();
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof BulletEntity) {
                bullets.incrementAndGet();
            }
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (entity.getType() == EntityType.HUSK && source.getDirectEntity() instanceof BulletEntity) {
                synchronized (damage) {
                    damage.add(damageTaken);
                }
            }
        });
        StringBuilder report = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 7");
            server.runCommand("item replace entity @p weapon.mainhand with jeg:assault_rifle");
            context.waitTicks(20);

            // Compare Power on its own. Punch moves the target and Flame can change its damage state between rounds;
            // neither side effect belongs in the Power damage comparison.
            Burst plain = this.burst(context, server, damage, report, "plain");
            server.runCommand("enchant @p minecraft:power 5");
            context.waitTicks(5);
            Burst powered = this.burst(context, server, damage, report, "power5");
            report.append(String.format("damage ratio=%.2f (expected 1.50)%n", powered.damage() / plain.damage()));

            server.runCommand("enchant @p minecraft:punch 2");
            server.runCommand("enchant @p minecraft:flame 1");
            context.waitTicks(5);
            String levels = server.computeOnServer(minecraftServer -> {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
                ItemStack gun = player.getMainHandItem();
                return "power=" + GunBowEnchantments.level(player.registryAccess(), gun, Enchantments.POWER)
                        + " punch=" + GunBowEnchantments.level(player.registryAccess(), gun, Enchantments.PUNCH)
                        + " flame=" + GunBowEnchantments.level(player.registryAccess(), gun, Enchantments.FLAME);
            });
            report.append("enchanted: ").append(levels).append('\n');
            Burst enchanted = this.burst(context, server, damage, report, "power5-punch2-flame");

            // Infinity in survival, loose-round reloads (the revolver loads single rounds): the 30 carried rounds
            // must stay 30 through three reloads.
            server.runCommand("kill @e[type=minecraft:husk]");
            server.runCommand("gamemode survival @a");
            server.runCommand("clear @p");
            server.runCommand("item replace entity @p weapon.mainhand with jeg:revolver");
            server.runCommand("give @p jeg:pistol_ammo 30");
            server.runCommand("enchant @p minecraft:infinity 1");
            context.waitTicks(10);
            int ammoBefore = this.carriedAmmo(server, "pistol_ammo");
            bullets.set(0);
            this.reloadAndFire(context, 3, "loose", true);
            int ammoAfter = this.carriedAmmo(server, "pistol_ammo");
            int looseBullets = bullets.get();
            report.append(String.format("infinity loose ammo: bulletsFired=%d carriedRounds before=%d after=%d%n", looseBullets, ammoBefore, ammoAfter));

            // Infinity with magazines: reloading from a loaded rifle magazine keeps it in the inventory.
            server.runCommand("clear @p");
            server.runCommand("item replace entity @p weapon.mainhand with jeg:assault_rifle");
            server.runCommand("enchant @p minecraft:infinity 1");
            server.runOnServer(minecraftServer -> {
                ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
                ItemStack magazine = new ItemStack(BuiltInRegistries.ITEM.getValue(Reference.id("rifle_magazine")));
                ItemStack rounds = new ItemStack(BuiltInRegistries.ITEM.getValue(Reference.id("rifle_ammo")), 64);
                MagazineItem item = (MagazineItem) magazine.getItem();
                for (int i = 0; i < item.getCapacity(); i++) {
                    item.tryLoad(player.level(), player, magazine, rounds, false);
                }
                player.getInventory().add(magazine);
            });
            context.waitTicks(10);
            String magazineBefore = this.magazines(server);
            bullets.set(0);
            this.reloadAndFire(context, 3, "magazine", false);
            String magazineAfter = this.magazines(server);
            int magazineBullets = bullets.get();
            report.append(String.format("infinity magazines: bulletsFired=%d magazines before=[%s] after=[%s]%n",
                    magazineBullets, magazineBefore, magazineAfter));

            // Turning magazine feed off by command must take effect straight away in singleplayer: the assault rifle
            // then reloads loose rounds (it used to keep the stale synced setting and never load).
            server.runCommand("justEnoughGuns config combat magazineFeed false");
            server.runCommand("clear @p");
            server.runCommand("item replace entity @p weapon.mainhand with jeg:assault_rifle");
            server.runCommand("give @p jeg:rifle_ammo 30");
            context.waitTicks(10);
            bullets.set(0);
            this.reloadAndFire(context, 1, "feed_off", false);
            int feedOffBullets = bullets.get();
            int feedOffRoundsLeft = this.carriedAmmo(server, "rifle_ammo");
            server.runCommand("justEnoughGuns config combat magazineFeed true");
            report.append(String.format("magazineFeed off: bulletsFired=%d carriedRounds 30 -> %d%n", feedOffBullets, feedOffRoundsLeft));

            // Infinity + Mending: allowed together on a gun (command and anvil), still exclusive on a bow.
            server.runCommand("clear @p");
            server.runCommand("item replace entity @p weapon.mainhand with jeg:assault_rifle");
            server.runCommand("enchant @p minecraft:infinity 1");
            server.runCommand("enchant @p minecraft:mending 1");
            context.waitTicks(2);
            String gunCommand = this.infinityAndMending(server);
            server.runCommand("item replace entity @p weapon.mainhand with minecraft:bow");
            server.runCommand("enchant @p minecraft:infinity 1");
            server.runCommand("enchant @p minecraft:mending 1");
            context.waitTicks(2);
            String bowCommand = this.infinityAndMending(server);
            String gunAnvil = this.anvilMendingOnto(server, "jeg:assault_rifle");
            String bowAnvil = this.anvilMendingOnto(server, "minecraft:bow");
            report.append("gun /enchant: ").append(gunCommand).append(" | bow /enchant: ").append(bowCommand).append('\n');
            report.append("gun anvil: ").append(gunAnvil).append(" | bow anvil: ").append(bowAnvil).append('\n');
            LOGGER.info("[bow-enchants]\n{}", report);
            if (feedOffBullets == 0 || feedOffRoundsLeft >= 30) {
                throw new AssertionError("Magazine feed off should reload the rifle from loose rounds: " + report);
            }
            if (!gunCommand.equals("infinity=1 mending=1") || !gunAnvil.equals("infinity=1 mending=1")) {
                throw new AssertionError("Guns should take Infinity and Mending together: " + report);
            }
            if (bowCommand.equals("infinity=1 mending=1") || bowAnvil.equals("infinity=1 mending=1")) {
                throw new AssertionError("Bows must keep vanilla's Infinity/Mending exclusivity: " + report);
            }
            if (ammoAfter != ammoBefore || looseBullets <= 8) {
                throw new AssertionError("Infinity should fire past one load without using carried rounds: " + report);
            }
            // The helper magazine may hold only a few rounds; three reloads from it prove it is never used up.
            int magazineRounds = Integer.parseInt(magazineBefore.substring(magazineBefore.indexOf(':') + 1).trim().split(" ")[0]);
            if (!magazineBefore.equals(magazineAfter) || magazineBullets < 3 * magazineRounds) {
                throw new AssertionError("Infinity should reload from a magazine without using it up: " + report);
            }
            if (Math.abs(powered.damage() / plain.damage() - 1.5F) > 0.05F) {
                throw new AssertionError("Power V should add 50% bullet damage: " + report);
            }
            if (!enchanted.burning() || enchanted.pushed() <= powered.pushed() + 0.1D) {
                throw new AssertionError("Flame should ignite the target and Punch should increase knockback: " + report);
            }
        }
    }

    private String infinityAndMending(TestServerContext server) {
        return server.computeOnServer(minecraftServer -> {
            ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
            return levels(player, player.getMainHandItem());
        });
    }

    private static String levels(ServerPlayer player, ItemStack stack) {
        return "infinity=" + GunBowEnchantments.level(player.registryAccess(), stack, Enchantments.INFINITY)
                + " mending=" + GunBowEnchantments.level(player.registryAccess(), stack, Enchantments.MENDING);
    }

    /** Puts an Infinity item and a Mending book in an anvil and reports the result's enchantments. */
    private String anvilMendingOnto(TestServerContext server, String itemId) {
        return server.computeOnServer(minecraftServer -> {
            ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
            var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            ItemStack item = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId)));
            item.enchant(enchantments.getOrThrow(Enchantments.INFINITY), 1);
            ItemStack book = EnchantmentHelper.createBook(new EnchantmentInstance(enchantments.getOrThrow(Enchantments.MENDING), 1));
            AnvilMenu anvil = new AnvilMenu(0, player.getInventory());
            anvil.getSlot(0).set(item);
            anvil.getSlot(1).set(book);
            anvil.createResult();
            ItemStack result = anvil.getSlot(2).getItem();
            return result.isEmpty() ? "no result" : levels(player, result);
        });
    }

    /** Reloads and empties the held gun {@code loads} times. */
    private void reloadAndFire(ClientGameTestContext context, int loads, String label, boolean clickToFire) {
        for (int load = 0; load < loads; load++) {
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(100);
            if (load == 0) {
                context.takeScreenshot("bow_enchants_" + label + "_reloaded");
            }
            if (clickToFire) {
                for (int shot = 0; shot < 12; shot++) {
                    context.getInput().holdKey(options -> options.keyAttack);
                    context.waitTicks(2);
                    context.getInput().releaseKey(options -> options.keyAttack);
                    context.waitTicks(8);
                }
            } else {
                context.getInput().holdKey(options -> options.keyAttack);
                context.waitTicks(120);
                context.getInput().releaseKey(options -> options.keyAttack);
            }
            if (load == 0) {
                context.takeScreenshot("bow_enchants_" + label + "_fired");
            }
            context.waitTicks(5);
        }
    }

    private String magazines(TestServerContext server) {
        return server.computeOnServer(minecraftServer -> {
            ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
            StringBuilder found = new StringBuilder();
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.getItem() instanceof MagazineItem magazine) {
                    found.append(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath())
                            .append(':').append(magazine.getAmmoCount(stack)).append(' ');
                }
            }
            return found.toString().trim();
        });
    }

    private int carriedAmmo(TestServerContext server, String ammoId) {
        return server.computeOnServer(minecraftServer -> {
            ServerPlayer player = minecraftServer.getPlayerList().getPlayers().get(0);
            Item ammo = BuiltInRegistries.ITEM.getValue(Reference.id(ammoId));
            int count = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(ammo)) {
                    count += stack.getCount();
                }
            }
            return count;
        });
    }

    /** Fires a short burst into a fresh, sturdy husk and returns the average damage per bullet. */
    private Burst burst(ClientGameTestContext context, TestServerContext server, List<Float> damage, StringBuilder report, String label) {
        server.runCommand("kill @e[type=minecraft:husk]");
        server.runCommand("execute as @p at @s rotated 0 0 run summon minecraft:husk ^ ^ ^5 "
                + "{PersistenceRequired:1b,IsBaby:0b,Health:1000f,attributes:[{id:\"minecraft:max_health\",base:1000d}]}");
        server.runCommand("effect give @e[type=minecraft:husk] minecraft:slowness infinite 255 true");
        server.runCommand("effect give @e[type=minecraft:husk] minecraft:fire_resistance infinite 0 true");
        context.waitTicks(30);
        Vec3 start = this.huskPosition(server);
        synchronized (damage) {
            damage.clear();
        }
        context.getInput().holdKey(options -> options.keyAttack);
        for (int tick = 0; tick < 6; tick++) {
            server.runCommand("execute as @p at @s run tp @s ~ ~ ~ 0 7");
            context.waitTick();
        }
        context.getInput().releaseKey(options -> options.keyAttack);
        context.waitTicks(3);
        boolean burning = server.computeOnServer(minecraftServer -> {
            for (Entity entity : minecraftServer.overworld().getAllEntities()) {
                if (entity.getType() == EntityType.HUSK) {
                    return entity.getRemainingFireTicks() > 0;
                }
            }
            return false;
        });
        context.waitTicks(10);
        double pushed = this.huskPosition(server).subtract(start).horizontalDistance();
        float total = 0.0F;
        int hits;
        synchronized (damage) {
            hits = damage.size();
            for (float value : damage) {
                total += value;
            }
        }
        if (hits == 0) {
            throw new AssertionError("No bullets hit the " + label + " target: " + report);
        }
        float average = total / hits;
        report.append(String.format("%s: hits=%d avgDamage=%.2f pushed=%.2f burning=%s%n", label, hits, average, pushed, burning));
        return new Burst(average, pushed, burning);
    }

    private Vec3 huskPosition(TestServerContext server) {
        return server.computeOnServer(minecraftServer -> {
            for (Entity entity : minecraftServer.overworld().getAllEntities()) {
                if (entity.getType() == EntityType.HUSK) {
                    return entity.position();
                }
            }
            return Vec3.ZERO;
        });
    }
}
