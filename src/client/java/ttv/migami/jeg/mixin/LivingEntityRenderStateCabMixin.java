package ttv.migami.jeg.mixin;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ttv.migami.jeg.vehicle.client.CabSeatedState;

/** Carries whether the entity rides a vehicle's cab seat from the renderer's extraction to its model. */
@Mixin(LivingEntityRenderState.class)
public abstract class LivingEntityRenderStateCabMixin implements CabSeatedState {
    @Unique
    private boolean jeg$seatedInCab;

    @Override
    public boolean jeg$seatedInCab() {
        return this.jeg$seatedInCab;
    }

    @Override
    public void jeg$setSeatedInCab(boolean seated) {
        this.jeg$seatedInCab = seated;
    }
}
