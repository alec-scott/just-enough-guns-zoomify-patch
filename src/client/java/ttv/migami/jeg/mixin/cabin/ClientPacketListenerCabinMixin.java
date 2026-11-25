package ttv.migami.jeg.mixin.cabin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ttv.migami.jeg.vehicle.cabin.CabinClient;

/** A teleport from the server takes the player off the cabin they stood in. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerCabinMixin {
    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void jeg$cabinCorrection(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            CabinClient.teleported();
        }
    }
}
