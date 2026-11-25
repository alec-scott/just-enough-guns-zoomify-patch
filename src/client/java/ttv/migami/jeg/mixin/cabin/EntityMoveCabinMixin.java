package ttv.migami.jeg.mixin.cabin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ttv.migami.jeg.vehicle.cabin.CabinClient;

/** the local player aboard a cabin moves in the cabin's coordinates. */
@Mixin(Entity.class)
public abstract class EntityMoveCabinMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void jeg$cabinMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player && CabinClient.move(player, type, movement)) {
            ci.cancel();
        }
    }
}
