package ttv.migami.jeg.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;

/**
 * Where an aircraft is and how hard its engines run, for a player too far off to be sent the vehicle itself (beyond the
 * server's view distance) but close enough to hear it: the client plays its distant engine (and gun) from these.
 * {@code entityId} is the vehicle's own id, so once it comes into entity range its own sounds take over.
 * {@code loudness} and {@code pitch} are its engine's as heard outside (see VehicleSoundHelper#aircraftEngineLoudness).
 */
public record AircraftTrackPayload(int entityId, String vehicleId, Vec3 position, Vec3 motion, float loudness, float pitch,
                                   boolean gunFiring) implements CustomPacketPayload {
    public static final Type<AircraftTrackPayload> TYPE = new Type<>(Reference.id("aircraft_track"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AircraftTrackPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId);
                buf.writeUtf(payload.vehicleId, 64);
                buf.writeDouble(payload.position.x);
                buf.writeDouble(payload.position.y);
                buf.writeDouble(payload.position.z);
                buf.writeFloat((float) payload.motion.x);
                buf.writeFloat((float) payload.motion.y);
                buf.writeFloat((float) payload.motion.z);
                buf.writeFloat(payload.loudness);
                buf.writeFloat(payload.pitch);
                buf.writeBoolean(payload.gunFiring);
            },
            buf -> new AircraftTrackPayload(buf.readVarInt(), buf.readUtf(64),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()),
                    buf.readFloat(), buf.readFloat(), buf.readBoolean())
    );

    @Override
    public Type<AircraftTrackPayload> type() {
        return TYPE;
    }
}
