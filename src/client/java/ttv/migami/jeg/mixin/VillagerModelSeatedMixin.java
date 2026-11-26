package ttv.migami.jeg.mixin;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.npc.VillagerModel;
import net.minecraft.client.renderer.entity.state.VillagerRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ttv.migami.jeg.vehicle.client.VehiclePassengerRenderHandler;

/**
 * A villager in a vehicle's cab seat shows from the waist up, as anyone does behind a car's doors: it has no seated pose,
 * and in the pickup's cab its legs and robe stood through the floor below the doors (2026-11-26). Under the pack, Fresh
 * Animations (EMF) draws villagers with its own model and seated pose, which this doesn't reach.
 */
@Mixin(VillagerModel.class)
public abstract class VillagerModelSeatedMixin {
    @Shadow
    @Final
    private ModelPart rightLeg;
    @Shadow
    @Final
    private ModelPart leftLeg;
    @org.spongepowered.asm.mixin.Unique
    private ModelPart jeg$jacket;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/VillagerRenderState;)V", at = @At("TAIL"))
    private void jeg$sitInCab(VillagerRenderState state, CallbackInfo ci) {
        boolean seated = VehiclePassengerRenderHandler.seatedInCab(state);
        // The parts are shared by every villager drawn with this model: set back for the next one.
        this.rightLeg.visible = !seated;
        this.leftLeg.visible = !seated;
        if (this.jeg$jacket == null) {
            ModelPart body = ((net.minecraft.client.model.EntityModel<?>) (Object) this).root().getChild("body");
            this.jeg$jacket = body.hasChild("jacket") ? body.getChild("jacket") : body;
        }
        this.jeg$jacket.visible = !seated;
    }
}
