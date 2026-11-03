package ttv.migami.jeg.mixin;

import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ttv.migami.jeg.gun.GunEnchantmentRules;

/** Enchanting table and enchanted loot: a gun can roll Infinity and Mending together. */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperGunEnchantMixin {
    @Inject(method = "selectEnchantment", at = @At("HEAD"))
    private static void jeg$enterGunContext(RandomSource random, ItemStack stack, int level, Stream<Holder<Enchantment>> enchantments,
                                            CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        GunEnchantmentRules.enter(stack);
    }

    @Inject(method = "selectEnchantment", at = @At("RETURN"))
    private static void jeg$exitGunContext(RandomSource random, ItemStack stack, int level, Stream<Holder<Enchantment>> enchantments,
                                           CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        GunEnchantmentRules.exit(stack);
    }
}
