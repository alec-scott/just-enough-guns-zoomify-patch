package ttv.migami.jeg.vehicle.client.audio;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Radial source and listener motion, in Minecraft's blocks per tick. */
public final class VehicleDoppler {
    private static final double SOUND_SPEED = 343.0D / 20.0D;

    private VehicleDoppler() {}

    public static float multiplier(Vec3 sourcePosition, Vec3 sourceVelocity, Vec3 listenerPosition, Vec3 listenerVelocity) {
        Vec3 separation = sourcePosition.subtract(listenerPosition);
        if (separation.lengthSqr() < 1.0D) {
            return 1.0F;
        }
        Vec3 direction = separation.normalize();
        double sourceRadial = Mth.clamp(sourceVelocity.dot(direction), -SOUND_SPEED * 0.8D, SOUND_SPEED * 0.8D);
        double listenerRadial = Mth.clamp(listenerVelocity.dot(direction), -SOUND_SPEED * 0.8D, SOUND_SPEED * 0.8D);
        return (float) Mth.clamp((SOUND_SPEED + listenerRadial) / (SOUND_SPEED + sourceRadial), 0.65D, 1.55D);
    }
}
