package ttv.migami.jeg.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;

/**
 * A smoke screen going up: clients draw it as one billowing cloud of soft lit smoke (MissileBlastRenderer's smoke), not
 * vanilla's campfire puffs (the user's review, R17). {@code radius} is how far it spreads, {@code ticks} how long it
 * holds before it thins away.
 */
public record SmokeScreenPayload(Vec3 position, float radius, int ticks) implements CustomPacketPayload {
    public static final Type<SmokeScreenPayload> TYPE = new Type<>(Reference.id("smoke_screen"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SmokeScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.position.x);
                buf.writeDouble(payload.position.y);
                buf.writeDouble(payload.position.z);
                buf.writeFloat(payload.radius);
                buf.writeVarInt(payload.ticks);
            },
            buf -> new SmokeScreenPayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readVarInt())
    );

    /** Sends the screen to everyone within {@code range} blocks of it. */
    public static void send(ServerLevel level, Vec3 at, float radius, int ticks, double range) {
        SmokeScreenPayload payload = new SmokeScreenPayload(at, radius, ticks);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(at) < range * range && ServerPlayNetworking.canSend(player, TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    @Override
    public Type<SmokeScreenPayload> type() {
        return TYPE;
    }
}
