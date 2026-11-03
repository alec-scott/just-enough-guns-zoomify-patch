package ttv.migami.jeg.gun;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Bow enchantments on guns (guns are in the minecraft:enchantable/bow tag). Vanilla only applies these to arrows, so
 * bullets and reloads read them here:
 * <ul>
 *     <li>Power: +10% bullet damage per level.</li>
 *     <li>Punch: knocks the target back like a Punch arrow, shared across a second of rapid fire and a shotgun's pellets.</li>
 *     <li>Flame: sets hit targets on fire for 5 seconds.</li>
 *     <li>Infinity: firing and reloading never use up ammo or magazines while you carry at least one.</li>
 * </ul>
 */
public final class GunBowEnchantments {
    public static final float POWER_DAMAGE_PER_LEVEL = 0.10F;

    private GunBowEnchantments() {}

    public static int level(RegistryAccess access, ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty()) {
            return 0;
        }
        HolderLookup.RegistryLookup<Enchantment> lookup = access.lookupOrThrow(Registries.ENCHANTMENT);
        return lookup.get(key).map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
    }

    public static float powerDamageMultiplier(RegistryAccess access, ItemStack stack) {
        return 1.0F + POWER_DAMAGE_PER_LEVEL * level(access, stack, Enchantments.POWER);
    }

    public static int punch(RegistryAccess access, ItemStack stack) {
        return level(access, stack, Enchantments.PUNCH);
    }

    public static boolean flame(RegistryAccess access, ItemStack stack) {
        return level(access, stack, Enchantments.FLAME) > 0;
    }

    public static boolean infinity(RegistryAccess access, ItemStack stack) {
        return level(access, stack, Enchantments.INFINITY) > 0;
    }
}
