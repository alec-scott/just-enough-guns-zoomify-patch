package ttv.migami.jeg.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;

/**
 * A missile warhead going off: clients draw its fireball, smoke, shockwave and debris. {@code power} is the explosion
 * power the blast was made with; {@code blastRadius} how far its damage reaches.
 */
public record MissileBlastPayload(Vec3 position, float power, float blastRadius, boolean incendiary) implements CustomPacketPayload {
    public static final Type<MissileBlastPayload> TYPE = new Type<>(Reference.id("missile_blast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MissileBlastPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.position.x);
                buf.writeDouble(payload.position.y);
                buf.writeDouble(payload.position.z);
                buf.writeFloat(payload.power);
                buf.writeFloat(payload.blastRadius);
                buf.writeBoolean(payload.incendiary);
            },
            buf -> new MissileBlastPayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readFloat(), buf.readFloat(), buf.readBoolean())
    );

    @Override
    public Type<MissileBlastPayload> type() {
        return TYPE;
    }
}
