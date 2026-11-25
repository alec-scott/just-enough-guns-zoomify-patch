package ttv.migami.jeg.mixin.cabin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ttv.migami.jeg.vehicle.cabin.CabinClient;
import ttv.migami.jeg.vehicle.client.LargeVehiclePick;

/**
 * What the local player's view is on: long vehicles along their whole length (LargeVehiclePick, vanilla misses parts
 * far from their origin), and aboard a cabin never the cabin vehicle itself nor anything in the world behind its walls
 * (CabinClient#filterPick); the cabin's own controls, seats and blocks are clicked through CabinClient.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerCabinPickMixin {
    @Inject(method = "raycastHitResult", at = @At("RETURN"), cancellable = true)
    private void jeg$cabinPick(float partialTick, Entity camera, CallbackInfoReturnable<HitResult> cir) {
        HitResult result = LargeVehiclePick.extend(cir.getReturnValue(), partialTick, camera);
        if (CabinClient.isAboard()) {
            result = CabinClient.filterPick(result, partialTick, camera);
        }
        cir.setReturnValue(result);
    }
}
