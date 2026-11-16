package ttv.migami.jeg.network;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.projectile.FarFlight;

/**
 * A gun firing a shell at {@code muzzle} (the mortar, the Mk42): each client within 1024 blocks plays the report
 * itself, on the aircraft distance curve (AircraftSoundRange) with {@code loudness}, crossfaded from
 * {@code <sound>.fire} to {@code <sound>.fire_distant} and delayed by the speed of sound. The gun's own crew hears
 * {@code <sound>.fire_interior} instead, when the gun has one.
 */
public record GunReportPayload(Vec3 muzzle, String sound, float loudness, int gunId) implements CustomPacketPayload {
    public static final Type<GunReportPayload> TYPE = new Type<>(Reference.id("gun_report"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GunReportPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeDouble(payload.muzzle.x);
                buf.writeDouble(payload.muzzle.y);
                buf.writeDouble(payload.muzzle.z);
                buf.writeUtf(payload.sound);
                buf.writeFloat(payload.loudness);
                buf.writeVarInt(payload.gunId);
            },
            buf -> new GunReportPayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readUtf(),
                    buf.readFloat(), buf.readVarInt())
    );

    /** Sends the report to every player within earshot (1024 blocks). */
    public static void send(ServerLevel level, Entity gun, Vec3 muzzle, String sound, float loudness) {
        GunReportPayload report = new GunReportPayload(muzzle, sound, loudness, gun.getId());
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(muzzle) < FarFlight.VISIBLE_RANGE * FarFlight.VISIBLE_RANGE
                    && ServerPlayNetworking.canSend(player, TYPE)) {
                ServerPlayNetworking.send(player, report);
            }
        }
    }

    @Override
    public Type<GunReportPayload> type() {
        return TYPE;
    }
}
