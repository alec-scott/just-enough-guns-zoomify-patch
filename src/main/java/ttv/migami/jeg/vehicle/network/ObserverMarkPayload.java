package ttv.migami.jeg.vehicle.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ttv.migami.jeg.Reference;

/** A forward observer looking through a spyglass marked a block as the howitzers' target (HowitzerEntity#observerMark). */
public record ObserverMarkPayload(BlockPos target) implements CustomPacketPayload {
    public static final Type<ObserverMarkPayload> TYPE = new Type<>(Reference.id("observer_mark"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ObserverMarkPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeBlockPos(payload.target()),
            buf -> new ObserverMarkPayload(buf.readBlockPos())
    );

    @Override
    public Type<ObserverMarkPayload> type() {
        return TYPE;
    }
}
