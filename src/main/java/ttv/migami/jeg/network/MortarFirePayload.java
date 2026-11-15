package ttv.migami.jeg.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;

/**
 * A mortar firing at {@code muzzle}: each client within 1024 blocks plays the report itself, on the aircraft distance
 * curve (AircraftSoundRange), crossfaded to the distant recording and delayed by the speed of sound.
 */
public record MortarFirePayload(Vec3 muzzle) implements CustomPacketPayload {
    public static final Type<MortarFirePayload> TYPE = new Type<>(Reference.id("mortar_fire"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MortarFirePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.muzzle.x);
                buf.writeDouble(payload.muzzle.y);
                buf.writeDouble(payload.muzzle.z);
            },
            buf -> new MortarFirePayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()))
    );

    @Override
    public Type<MortarFirePayload> type() {
        return TYPE;
    }
}
