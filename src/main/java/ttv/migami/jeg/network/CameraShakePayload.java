package ttv.migami.jeg.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ttv.migami.jeg.Reference;

/** A big blast nearby: shakes this player's view. Strength 1 is standing next to the largest missile. */
public record CameraShakePayload(float strength) implements CustomPacketPayload {
    public static final Type<CameraShakePayload> TYPE = new Type<>(Reference.id("camera_shake"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CameraShakePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeFloat(payload.strength),
            buf -> new CameraShakePayload(buf.readFloat())
    );

    @Override
    public Type<CameraShakePayload> type() {
        return TYPE;
    }
}
