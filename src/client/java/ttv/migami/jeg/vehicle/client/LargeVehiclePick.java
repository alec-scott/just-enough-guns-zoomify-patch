package ttv.migami.jeg.vehicle.client;

import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.LargeVehicles;

/**
 * The local player's view meets long vehicles everywhere along them: vanilla's pick finds an entity only near the section
 * its origin is in, so a Chinook's cockpit, 7 m ahead of its origin, couldn't be clicked once it stood in the next
 * section (the two-player play-test, 2026-11-25).
 */
public final class LargeVehiclePick {
    private LargeVehiclePick() {
    }

    public static HitResult extend(HitResult result, float partialTick, Entity camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || camera == null) {
            return result;
        }
        double range = mc.player.entityInteractionRange();
        Vec3 eye = camera.getEyePosition(partialTick);
        Vec3 end = eye.add(camera.getViewVector(partialTick).scale(range));
        double best = result == null || result.getType() == HitResult.Type.MISS ? range * range : result.getLocation().distanceToSqr(eye);
        HitResult picked = result;
        for (VehicleEntity vehicle : LargeVehicles.meeting(mc.level, new AABB(eye, end).inflate(1.0D))) {
            if (vehicle == camera.getRootVehicle() || vehicle.getId() == ttv.migami.jeg.vehicle.cabin.CabinClient.aboardVehicle()
                    || !vehicle.isPickable() || (result instanceof EntityHitResult hit && hit.getEntity() == vehicle)) {
                continue;
            }
            AABB box = vehicle.getBoundingBox().inflate(vehicle.getPickRadius());
            Vec3 at = box.contains(eye) ? eye : box.clip(eye, end).orElse(null);
            if (at != null && eye.distanceToSqr(at) < best) {
                best = eye.distanceToSqr(at);
                picked = new EntityHitResult(vehicle, at);
            }
        }
        return picked;
    }
}
