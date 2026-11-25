package ttv.migami.jeg.mixin.cabin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ttv.migami.jeg.vehicle.cabin.CabinNetwork;

/**
 * A player aboard a cabin reports where they stand in it (CabinNetwork.MovePayload), so their world position moves at
 * the cabin's speed. Vanilla's move checks would rubber-band that and its flying check would kick them, so for them a
 * move packet carries only the view. Any teleport takes them off the cabin first.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerCabinMixin {
    @Shadow public ServerPlayer player;
    @Shadow private boolean clientIsFloating;
    @Shadow private int aboveGroundTickCount;

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
            shift = At.Shift.AFTER), cancellable = true)
    private void jeg$cabinMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (CabinNetwork.isAboard(this.player)) {
            if (packet.hasRotation()) {
                float yaw = packet.getYRot(this.player.getYRot());
                this.player.setYRot(yaw);
                this.player.setXRot(packet.getXRot(this.player.getXRot()));
                this.player.setYHeadRot(yaw);
            }
            this.clientIsFloating = false;
            this.aboveGroundTickCount = 0;
            ci.cancel();
        }
    }

    /** Commands, ender pearls and the like: the cabin would otherwise pull the player straight back (the 2026-11-20 trial). */
    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("HEAD"))
    private void jeg$cabinTeleport(net.minecraft.world.entity.PositionMoveRotation position,
                                   java.util.Set<net.minecraft.world.entity.Relative> relatives, CallbackInfo ci) {
        CabinNetwork.detach(this.player);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void jeg$cabinTick(CallbackInfo ci) {
        if (CabinNetwork.isAboard(this.player)) {
            this.clientIsFloating = false;
            this.aboveGroundTickCount = 0;
        }
    }
}
