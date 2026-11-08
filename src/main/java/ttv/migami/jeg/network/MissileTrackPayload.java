package ttv.migami.jeg.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;

/**
 * Where a missile is this tick, for a player too far off to be sent the entity itself (beyond the server's view
 * distance): the client draws its motor glow and smoke trail from these. {@code entityId} is the missile's own id, so a
 * missile flying into or out of entity range carries on as the same trail.
 */
public record MissileTrackPayload(int entityId, Vec3 position, Vec3 motion, boolean heavy, int age) implements CustomPacketPayload {
    public static final Type<MissileTrackPayload> TYPE = new Type<>(Reference.id("missile_track"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MissileTrackPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId);
                buf.writeDouble(payload.position.x);
                buf.writeDouble(payload.position.y);
                buf.writeDouble(payload.position.z);
                buf.writeFloat((float) payload.motion.x);
                buf.writeFloat((float) payload.motion.y);
                buf.writeFloat((float) payload.motion.z);
                buf.writeBoolean(payload.heavy);
                buf.writeVarInt(payload.age);
            },
            buf -> new MissileTrackPayload(buf.readVarInt(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()),
                    buf.readBoolean(), buf.readVarInt())
    );

    @Override
    public Type<MissileTrackPayload> type() {
        return TYPE;
    }
}
