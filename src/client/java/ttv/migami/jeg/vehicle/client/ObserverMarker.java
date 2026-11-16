package ttv.migami.jeg.vehicle.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import ttv.migami.jeg.vehicle.network.ObserverMarkPayload;

/**
 * The forward observer: looking through a spyglass, the attack button marks the block in the middle of the view as the
 * target of the howitzers around (FireMissions#observerMark). Runs at the start of the client tick, before vanilla
 * swallows attack clicks made while an item is in use.
 */
public final class ObserverMarker {
    private ObserverMarker() {}

    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null || !minecraft.player.isScoping()) {
            return;
        }
        boolean clicked = false;
        while (minecraft.options.keyAttack.consumeClick()) {
            clicked = true;
        }
        if (!clicked) {
            return;
        }
        HitResult hit = minecraft.player.pick(ttv.migami.jeg.vehicle.util.FireMissions.OBSERVER_MARK_RANGE, 1.0F, false);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            ClientPlayNetworking.send(new ObserverMarkPayload(block.getBlockPos()));
        } else {
            minecraft.player.sendOverlayMessage(Component.translatable("message.jeg.howitzer.nothing_to_mark"));
        }
    }
}
