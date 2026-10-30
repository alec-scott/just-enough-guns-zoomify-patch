package ttv.migami.jeg.mixin;

import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ttv.migami.jeg.vehicle.client.VehicleFrameTiming;

@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerVehicleTimingMixin {
    @Shadow
    @Final
    private float msPerTick;

    @Shadow
    private boolean paused;

    @Shadow
    private float deltaTickResidual;

    @Inject(method = "advanceGameTime", at = @At("RETURN"))
    private void jeg$trackSmoothTime(long timeMillis, CallbackInfoReturnable<Integer> callback) {
        VehicleFrameTiming.onAdvanceGameTime(callback.getReturnValueI(), this.deltaTickResidual, this.msPerTick);
    }

    @Inject(method = "getGameTimeDeltaPartialTick", at = @At("RETURN"), cancellable = true)
    private void jeg$includeTickTime(boolean runsNormally, CallbackInfoReturnable<Float> callback) {
        if (this.paused) {
            return;
        }
        float partialTick = callback.getReturnValueF();
        float adjusted = VehicleFrameTiming.adjustPartialTick(partialTick, this.msPerTick);
        if (adjusted != partialTick) {
            callback.setReturnValue(adjusted);
        }
    }
}
