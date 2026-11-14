package ttv.migami.jeg.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.Channel;
import java.util.function.Consumer;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ttv.migami.jeg.vehicle.client.audio.DynamicRelativeSound;

@Mixin(SoundEngine.class)
public abstract class VehicleSoundEngineMixin {
    @ModifyArg(method = "tickInGameSound", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/ChannelAccess$ChannelHandle;execute(Ljava/util/function/Consumer;)V"), index = 0)
    private Consumer<Channel> jeg$updateRelativePosition(Consumer<Channel> update,
                                                        @Local TickableSoundInstance sound) {
        if (!(sound instanceof DynamicRelativeSound)) return update;
        // Vanilla sets this only at play(). Update mode and position in the same sound-thread task so a view
        // switch keeps the buffer playing, and a centred source never lags behind the moving listener.
        boolean relative = sound.isRelative();
        return channel -> {
            channel.setRelative(relative);
            update.accept(channel);
        };
    }
}
