package ttv.migami.jeg.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Collection;
import net.minecraft.core.Holder;
import net.minecraft.server.commands.EnchantCommand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ttv.migami.jeg.gun.GunEnchantmentRules;

/** /enchant: Infinity and Mending can both be applied to a held gun. */
@Mixin(EnchantCommand.class)
public abstract class EnchantCommandGunEnchantMixin {
    @WrapOperation(method = "enchant", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;isEnchantmentCompatible(Ljava/util/Collection;Lnet/minecraft/core/Holder;)Z"))
    private static boolean jeg$checkWithGunContext(Collection<Holder<Enchantment>> existing, Holder<Enchantment> added,
                                                   Operation<Boolean> original, @Local(ordinal = 0) ItemStack stack) {
        GunEnchantmentRules.enter(stack);
        try {
            return original.call(existing, added);
        } finally {
            GunEnchantmentRules.exit(stack);
        }
    }
}
