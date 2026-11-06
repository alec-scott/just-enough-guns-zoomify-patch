package ttv.migami.jeg.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ttv.migami.jeg.Reference;

/** Tells nearby clients what another player's minigun is doing: see the states in {@link ttv.migami.jeg.gun.MinigunSpin}. */
public record MinigunStatePayload(int entityId, byte state) implements CustomPacketPayload {
    public static final Type<MinigunStatePayload> TYPE = new Type<>(Reference.id("minigun_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MinigunStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.entityId);
                buf.writeByte(payload.state);
            },
            buf -> new MinigunStatePayload(buf.readInt(), buf.readByte())
    );

    @Override
    public Type<MinigunStatePayload> type() {
        return TYPE;
    }
}
