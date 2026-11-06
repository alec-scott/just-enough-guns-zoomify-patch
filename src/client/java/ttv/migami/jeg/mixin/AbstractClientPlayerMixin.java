package ttv.migami.jeg.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ttv.migami.jeg.client.handler.AimingHandler;
import ttv.migami.jeg.gun.GunScopeSupport;
import ttv.migami.jeg.gun.GunWeight;
import ttv.migami.jeg.item.GunItem;

@Mixin(AbstractClientPlayer.class)
public final class AbstractClientPlayerMixin {
    private static final float ADS_FOV_FACTOR = 0.35F;
    private static final float SCOPE_VIEWPORT_FOV = 20.0F;

    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void jeg$applyAdsFovModifier(boolean firstPerson, float tickProgress, CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        jeg$cancelGunWeightFov(player, cir);
        ItemStack mainHand = player.getMainHandItem();
        if (!(mainHand.getItem() instanceof GunItem gun)) {
            return;
        }

        float ads = AimingHandler.get().getRenderAdsProgress();
        if (ads <= 0.0F) {
            return;
        }

        if (GunScopeSupport.hasTelescopicSight(mainHand)) {
            float current = cir.getReturnValueF();
            float target = SCOPE_VIEWPORT_FOV / configuredFov();
            cir.setReturnValue(Math.max(0.1F, current + (target - current) * ads));
            return;
        }

        float fovFactor = ADS_FOV_FACTOR;
        float factor = 1.0F - fovFactor * ads;
        cir.setReturnValue(Math.max(0.1F, cir.getReturnValueF() * factor));
    }

    /**
     * Vanilla widens and narrows the view with walking speed. Carrying a heavier gun is not meant to zoom the camera in, so
     * take the gun's share of the speed back out of the modifier.
     */
    private static void jeg$cancelGunWeightFov(AbstractClientPlayer player, CallbackInfoReturnable<Float> cir) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier weight = speed != null ? speed.getModifier(GunWeight.MODIFIER_ID) : null;
        float walking = player.getAbilities().getWalkingSpeed();
        if (weight == null || walking <= 0.0F) {
            return;
        }
        double value = speed.getValue();
        double without = value / (1.0D + weight.amount());
        float ratio = (float) ((without / walking + 1.0D) / (value / walking + 1.0D));
        float effect = Minecraft.getInstance().options != null ? Minecraft.getInstance().options.fovEffectScale().get().floatValue() : 1.0F;
        cir.setReturnValue(cir.getReturnValueF() * Mth.lerp(effect, 1.0F, ratio));
    }

    private static float configuredFov() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.options == null) {
            return 70.0F;
        }
        return Math.max(1.0F, minecraft.options.fov().get());
    }
}
