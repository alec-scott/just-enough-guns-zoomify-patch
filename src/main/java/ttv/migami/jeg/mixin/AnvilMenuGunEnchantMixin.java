package ttv.migami.jeg.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ttv.migami.jeg.gun.GunEnchantmentRules;

/** Anvil: combining books or guns onto a gun may put Infinity and Mending together. */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuGunEnchantMixin extends ItemCombinerMenu {
    @Unique
    private ItemStack jeg$enchantedItem = ItemStack.EMPTY;

    protected AnvilMenuGunEnchantMixin(MenuType<?> type, int containerId, Inventory inventory, ContainerLevelAccess access,
                                       ItemCombinerMenuSlotDefinition slots) {
        super(type, containerId, inventory, access, slots);
    }

    @Inject(method = "createResult", at = @At("HEAD"))
    private void jeg$enterGunContext(CallbackInfo ci) {
        this.jeg$enchantedItem = this.inputSlots.getItem(0);
        GunEnchantmentRules.enter(this.jeg$enchantedItem);
    }

    @Inject(method = "createResult", at = @At("RETURN"))
    private void jeg$exitGunContext(CallbackInfo ci) {
        GunEnchantmentRules.exit(this.jeg$enchantedItem);
        this.jeg$enchantedItem = ItemStack.EMPTY;
    }
}
