package ttv.migami.jeg.vehicle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * On foot, looking at an FSV-6: at an outside locker, the action bar says how to open it (or that nothing is fitted
 * there yet); at the back of a packed one, which keys work the tow hitch and what it would take (or drop). The driver's own prompt is in the FSV-6 panel ({@code VehicleHudOverlay}).
 */
public final class SupportHints {
    private static int cooldown;

    private SupportHints() {
    }

    public static void tick(Minecraft mc) {
        if (cooldown > 0) cooldown--;
        if (mc.player == null || mc.level == null || mc.screen != null || mc.player.getVehicle() != null || cooldown > 0) return;
        if (!(mc.hitResult instanceof EntityHitResult hit) || !(hit.getEntity() instanceof SupportVehicleEntity truck)) return;
        var relative = hit.getLocation().subtract(truck.position());
        int locker = SupportVehicleEntity.lockerAt(truck, relative);
        if (locker >= 0) {
            var fitted = truck.fittedForDisplay(locker);
            String side = locker == ttv.migami.jeg.vehicle.support.SupportModules.LOCKER_LEFT ? "left" : "right";
            mc.gui.setOverlayMessage(fitted.isEmpty()
                    ? Component.translatable("hud.jeg.support.locker_empty_hint", Component.translatable("hud.jeg.support.locker_" + side))
                    : Component.translatable("hud.jeg.support.locker_hint", mc.options.keyUse.getTranslatedKeyMessage(),
                    Component.translatable("hud.jeg.support.locker_" + side), fitted.getHoverName()), false);
            cooldown = 10;
            return;
        }
        if (!truck.isPacked() || !SupportVehicleEntity.isRearClick(truck, relative)) return;
        Component keys = Component.translatable("hud.jeg.support.tow_keys", mc.options.keyShift.getTranslatedKeyMessage(), mc.options.keyUse.getTranslatedKeyMessage());
        var towed = truck.towedEntityId() >= 0 ? mc.level.getEntity(truck.towedEntityId()) : null;
        Component message;
        if (towed != null) {
            message = Component.translatable("hud.jeg.support.tow_foot_release", keys, towed.getType().getDescription());
        } else {
            VehicleEntity candidate = truck.towCandidate();
            message = candidate != null ? Component.translatable("hud.jeg.support.tow_foot_ready", keys, candidate.getType().getDescription())
                    : Component.translatable("hud.jeg.support.tow_foot_none", (int) SupportVehicleEntity.TOW_REACH);
        }
        mc.gui.setOverlayMessage(message, false);
        cooldown = 10;
    }
}
