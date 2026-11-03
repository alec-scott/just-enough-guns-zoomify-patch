package ttv.migami.jeg.gun;

import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import ttv.migami.jeg.item.GunItem;

/**
 * Guns may carry Infinity and Mending together, which vanilla forbids for bows. Vanilla's compatibility check does not
 * know the item, so the anvil, enchanting table and /enchant mark the gun they are working on while they check
 * (see the Enchantment, AnvilMenu, EnchantmentHelper and EnchantCommand mixins).
 */
public final class GunEnchantmentRules {
    private static final ThreadLocal<Integer> GUN_DEPTH = ThreadLocal.withInitial(() -> 0);

    private GunEnchantmentRules() {}

    public static void enter(ItemStack stack) {
        if (stack.getItem() instanceof GunItem) {
            GUN_DEPTH.set(GUN_DEPTH.get() + 1);
        }
    }

    public static void exit(ItemStack stack) {
        if (stack.getItem() instanceof GunItem) {
            GUN_DEPTH.set(Math.max(0, GUN_DEPTH.get() - 1));
        }
    }

    /** True when the pair is Infinity and Mending and the item being enchanted is a gun. */
    public static boolean allowsOnGun(Holder<Enchantment> first, Holder<Enchantment> second) {
        if (GUN_DEPTH.get() <= 0) {
            return false;
        }
        return (first.is(Enchantments.INFINITY) && second.is(Enchantments.MENDING))
                || (first.is(Enchantments.MENDING) && second.is(Enchantments.INFINITY));
    }
}
