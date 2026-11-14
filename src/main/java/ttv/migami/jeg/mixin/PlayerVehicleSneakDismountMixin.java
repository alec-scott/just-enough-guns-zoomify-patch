package ttv.migami.jeg.mixin;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Vanilla throws a player out of whatever they are riding as soon as they sneak. In a JEG vehicle that made Shift an
 * eject button, most painfully in a helicopter where it reads as "go down". Sneaking no longer leaves a JEG vehicle;
 * the Vehicle Dismount key (VehicleDismountPayload) is the way out.
 * <p>
 * Nor does Shift crouch a seated occupant: vehicles use it as a control (a helicopter's descent, a bulldozer's blade),
 * and the crouching pose dropped the eye height, so the first-person camera sank into the seat.
 */
@Mixin(Player.class)
public abstract class PlayerVehicleSneakDismountMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void jeg$keepSeatWhileSneaking(CallbackInfoReturnable<Boolean> cir) {
        if (((Player) (Object) this).getVehicle() instanceof VehicleEntity) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getDesiredPose", at = @At("HEAD"), cancellable = true)
    private void jeg$stayUprightInVehicle(CallbackInfoReturnable<Pose> cir) {
        if (((Player) (Object) this).getVehicle() instanceof VehicleEntity) {
            cir.setReturnValue(Pose.STANDING);
        }
    }
}
