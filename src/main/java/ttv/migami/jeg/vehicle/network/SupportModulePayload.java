package ttv.migami.jeg.vehicle.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ttv.migami.jeg.Reference;

/** Open a support vehicle's fitted module by fitting slot, or its Fit-out screen for slot -1 (from the hold screen). */
public record SupportModulePayload(int vehicleId, int slot) implements CustomPacketPayload {
    public static final Type<SupportModulePayload> TYPE = new Type<>(Reference.id("support_module"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SupportModulePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.vehicleId());
                buf.writeVarInt(payload.slot());
            },
            buf -> new SupportModulePayload(buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public Type<SupportModulePayload> type() {
        return TYPE;
    }
}
