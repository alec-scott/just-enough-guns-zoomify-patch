package ttv.migami.jeg.vehicle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.cabin.CabinClient;
import ttv.migami.jeg.vehicle.cabin.CabinMath;
import ttv.migami.jeg.vehicle.entity.ChinookEntity;

/**
 * On foot outside a Chinook, the action bar says what a right-click on it does: work the crew door by it, or board
 * (and which seat that is). Inside, the cabin's own prompts take over (CabinClient). As the FSV-6's lockers do
 * (SupportHints).
 */
public final class Ch47Hints {
    private static int cooldown;

    private Ch47Hints() {
    }

    public static void tick(Minecraft mc) {
        if (cooldown > 0) cooldown--;
        if (mc.player == null || mc.level == null || mc.screen != null || mc.player.getVehicle() != null || CabinClient.isAboard()
                || cooldown > 0 || mc.options.hideGui) return;
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof ChinookEntity chinook)) return;
        Vec3 local = CabinMath.toLocal(chinook.localToWorld(1.0F), hit.getLocation());
        Component what;
        if (ChinookEntity.atCrewDoorFromOutside(local)) {
            what = Component.translatable(chinook.crewDoorTarget() ? "hud.jeg.ch47.prompt.door_close" : "hud.jeg.ch47.prompt.door_open");
        } else {
            int seat = -1;
            for (int i = 0; i < 4 && seat < 0; i++) {
                if (chinook.passengerIn(i) == null) seat = i;
            }
            if (seat < 0) return;
            what = Component.translatable("hud.jeg.ch47.prompt.board", Component.translatable("hud.jeg.ch47.seat_" + seat));
        }
        mc.gui.setOverlayMessage(Component.translatable("hud.jeg.cabin.prompt", mc.options.keyUse.getTranslatedKeyMessage(), what), false);
        cooldown = 20;
    }
}
