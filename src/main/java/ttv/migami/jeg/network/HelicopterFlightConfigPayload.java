package ttv.migami.jeg.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ttv.migami.jeg.Config;
import ttv.migami.jeg.Reference;

/** Sends the server's weighty-flight settings so the pilot's client predicts helicopter movement the same way. */
public record HelicopterFlightConfigPayload(Config.HelicopterFlightSettings settings) implements CustomPacketPayload {
    public static final Type<HelicopterFlightConfigPayload> TYPE = new Type<>(Reference.id("helicopter_flight_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HelicopterFlightConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.settings.weightyFlight());
                buf.writeDouble(payload.settings.weightMultiplier());
                buf.writeDouble(payload.settings.turnGrip());
                buf.writeDouble(payload.settings.tiltSink());
                buf.writeBoolean(payload.settings.barrelRolls());
            },
            buf -> new HelicopterFlightConfigPayload(new Config.HelicopterFlightSettings(
                    buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()))
    );

    @Override
    public Type<HelicopterFlightConfigPayload> type() {
        return TYPE;
    }
}
