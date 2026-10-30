package ttv.migami.jeg.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ttv.migami.jeg.vehicle.client.VehicleFrameTiming;

@Mixin(Minecraft.class)
public abstract class MinecraftVehicleFrameTimingMixin {
    @Inject(
            method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/DeltaTracker$Timer;advanceGameTime(J)I", shift = At.Shift.AFTER)
    )
    private void jeg$markFrameStart(boolean renderLevel, CallbackInfo callback) {
        VehicleFrameTiming.markFrameStart((Minecraft) (Object) this);
    }

    @Inject(
            method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;renderFrame(Z)V")
    )
    private void jeg$markRenderStart(boolean renderLevel, CallbackInfo callback) {
        VehicleFrameTiming.markRenderStart();
    }
}
