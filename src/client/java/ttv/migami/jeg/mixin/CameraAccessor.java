package ttv.migami.jeg.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccessor {
    /** The field of view the first-person hand is drawn with this frame (70 degrees, less when dying or in a fluid). */
    @Accessor("hudFov")
    float jeg$getHudFov();
}
