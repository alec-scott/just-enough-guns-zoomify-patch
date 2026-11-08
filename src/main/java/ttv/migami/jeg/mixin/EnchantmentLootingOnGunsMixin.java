package ttv.migami.jeg.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ttv.migami.jeg.gun.GunEnchantmentRules;

/**
 * Looting goes on guns (anvil, enchanting table, /enchant). Vanilla limits it to melee weapons; tagging guns as melee
 * weapons would also have let Knockback and Fire Aspect on, so only Looting is widened here.
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentLootingOnGunsMixin {
    @Inject(method = "isSupportedItem", at = @At("HEAD"), cancellable = true)
    private void jeg$lootingSupportsGuns(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (GunEnchantmentRules.isLootingOnGun((Enchantment) (Object) this, stack)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void jeg$lootingEnchantsGuns(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (GunEnchantmentRules.isLootingOnGun((Enchantment) (Object) this, stack)) {
            cir.setReturnValue(true);
        }
    }
}
