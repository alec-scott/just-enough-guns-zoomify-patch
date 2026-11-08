package ttv.migami.jeg.mixin;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ttv.migami.jeg.client.render.FarDraw;

/** Notes the fog each frame is drawn with, so far missiles and blasts can be drawn inside it (see FarDraw). */
@Mixin(FogRenderer.class)
public class FogRendererFarDrawMixin {
    @Inject(method = "updateBuffer(Lnet/minecraft/client/renderer/fog/FogData;)V", at = @At("HEAD"))
    private void jeg$recordFog(FogData fog, CallbackInfo ci) {
        FarDraw.recordFog(fog);
    }
}
