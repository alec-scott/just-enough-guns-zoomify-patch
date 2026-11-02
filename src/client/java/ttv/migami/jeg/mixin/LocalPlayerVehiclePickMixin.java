package ttv.migami.jeg.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

import java.util.function.Predicate;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerVehiclePickMixin {
    /**
     * A seated passenger's eyes are inside the vehicle's box, so vanilla always targeted the vehicle itself and swallowed
     * every click. Passengers with free hands look past their own vehicle so they can eat, use items and interact.
     */
    @ModifyArg(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"
            ),
            index = 4
    )
    private static Predicate<Entity> jeg$skipOwnVehicleForFreeHands(Entity source, Vec3 from, Vec3 to, AABB searchBox,
                                                                    Predicate<Entity> predicate, double maxDistanceSqr) {
        if (!(source.getVehicle() instanceof VehicleEntity vehicle) || vehicle.shouldBanPassengerHand(source)) {
            return predicate;
        }
        return predicate.and(entity -> entity != vehicle);
    }
}
