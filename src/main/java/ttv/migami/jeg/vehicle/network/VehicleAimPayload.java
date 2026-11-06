package ttv.migami.jeg.vehicle.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ttv.migami.jeg.Reference;

/** What a crew member's crosshair is on. The camera sits well away from the guns, so only the client knows where it points. */
public record VehicleAimPayload(int vehicleId, double x, double y, double z) implements CustomPacketPayload {
    public static final Type<VehicleAimPayload> TYPE = new Type<>(Reference.id("vehicle_aim"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleAimPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeInt(payload.vehicleId);
                buf.writeDouble(payload.x);
                buf.writeDouble(payload.y);
                buf.writeDouble(payload.z);
            },
            buf -> new VehicleAimPayload(buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble())
    );

    @Override
    public Type<VehicleAimPayload> type() {
        return TYPE;
    }
}
