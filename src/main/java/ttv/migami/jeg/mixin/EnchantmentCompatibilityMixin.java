package ttv.migami.jeg.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ttv.migami.jeg.gun.GunEnchantmentRules;

/** Lets Infinity and Mending share a gun (see GunEnchantmentRules); every other pair keeps vanilla's rule. */
@Mixin(Enchantment.class)
public abstract class EnchantmentCompatibilityMixin {
    @Inject(method = "areCompatible", at = @At("HEAD"), cancellable = true)
    private static void jeg$allowInfinityWithMendingOnGuns(Holder<Enchantment> first, Holder<Enchantment> second,
                                                            CallbackInfoReturnable<Boolean> cir) {
        if (GunEnchantmentRules.allowsOnGun(first, second)) {
            cir.setReturnValue(true);
        }
    }
}
