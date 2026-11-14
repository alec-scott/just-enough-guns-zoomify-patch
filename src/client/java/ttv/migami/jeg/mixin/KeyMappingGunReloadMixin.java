package ttv.migami.jeg.mixin;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.item.GunItem;

/** Iris and JEG both default to R: cooling or reloading a gun must not rebuild the world's shaders. */
@Mixin(KeyMapping.class)
public class KeyMappingGunReloadMixin {
    @Shadow
    private int clickCount;

    @Inject(method = "consumeClick()Z", at = @At("HEAD"), cancellable = true)
    private void jeg$keepGunReloadFromReloadingShaders(CallbackInfoReturnable<Boolean> callback) {
        KeyMapping mapping = (KeyMapping) (Object) this;
        if (!"iris.keybind.reload".equals(mapping.getName()) || KeyBindings.RELOAD.isUnbound()
                || !mapping.same(KeyBindings.RELOAD)) {
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player != null && (player.getMainHandItem().getItem() instanceof GunItem
                || player.getOffhandItem().getItem() instanceof GunItem)) {
            // Drain only Iris's queued clicks; JEG still receives the same physical key press. Dropping the gun later
            // must not replay a shader reload queued while it was held. Distinct user bindings work normally.
            this.clickCount = 0;
            callback.setReturnValue(false);
        }
    }
}
