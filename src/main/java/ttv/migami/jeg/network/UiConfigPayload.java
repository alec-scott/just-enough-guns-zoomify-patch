package ttv.migami.jeg.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ttv.migami.jeg.Reference;

public record UiConfigPayload(boolean showCrosshair, boolean showHitFeedback, boolean hideMedals, Boolean magazineFeedEnabled) implements CustomPacketPayload {
    public static final Type<UiConfigPayload> TYPE = new Type<>(Reference.id("ui_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UiConfigPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.showCrosshair);
                buf.writeBoolean(payload.showHitFeedback);
                buf.writeBoolean(payload.hideMedals);
                if (payload.magazineFeedEnabled != null) {
                    buf.writeBoolean(payload.magazineFeedEnabled);
                }
            },
            // Servers on older builds omit magazineFeedEnabled; treat it as unknown so the client keeps its local value.
            buf -> new UiConfigPayload(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.isReadable() ? buf.readBoolean() : null)
    );

    @Override
    public Type<UiConfigPayload> type() {
        return TYPE;
    }
}
