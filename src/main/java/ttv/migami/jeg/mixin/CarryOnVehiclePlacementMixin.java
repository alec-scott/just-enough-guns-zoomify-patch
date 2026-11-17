package ttv.migami.jeg.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Carry On compat (does nothing without Carry On). Right-clicking a vehicle while carrying a mob puts the mob in a free
 * passenger seat instead of boarding the player. Carry On stacks a carried mob on the topmost of a chain of first
 * passengers, so in a vehicle with someone already aboard it tried to sit the mob on that passenger (and failed); here a
 * vehicle is always the top, and the vehicle's own rules pick the seat (never the pilot's/driver's).
 */
@Pseudo
@Mixin(targets = "tschipp.carryon.common.carry.PlacementHandler", remap = false)
public abstract class CarryOnVehiclePlacementMixin {
    @Inject(method = "tryStackEntity(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void jeg$placeIntoVehicle(ServerPlayer player, Entity target, CallbackInfo ci) {
        if (!(target.getRootVehicle() instanceof VehicleEntity vehicle)) {
            return;
        }
        vehicle.markCarryOnPlacement(player);
        if (!vehicle.hasFreePassengerSeat()) {
            // Keep holding the mob rather than dropping it beside a full vehicle.
            player.sendOverlayMessage(Component.translatable("message.jeg.vehicle.no_passenger_seat"));
            ci.cancel();
        }
    }

    @Inject(method = "getTopPassenger(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void jeg$vehicleIsTop(Entity entity, CallbackInfoReturnable<Entity> cir) {
        if (entity instanceof VehicleEntity) {
            cir.setReturnValue(entity);
        }
    }

    @Inject(method = "getPassengerCount(Lnet/minecraft/world/entity/Entity;)I", at = @At("HEAD"), cancellable = true, require = 0)
    private static void jeg$vehicleSeatsAreNotAStack(Entity entity, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof VehicleEntity) {
            cir.setReturnValue(0);
        }
    }
}
