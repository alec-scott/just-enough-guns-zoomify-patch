package ttv.migami.jeg.vehicle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.entity.MortarEntity;
import ttv.migami.jeg.vehicle.util.MortarRig;

/**
 * The gunner's fire-control computer: where a round fired now would land. It flies the round tick by tick exactly as
 * the server does (MortarRig), against the terrain while that is loaded; past the loaded world it takes the ground as
 * level with the mortar and says so. Every few ticks it marks the predicted impact with particles for the gunner.
 */
public final class MortarAim {
    private static final int MARK_EVERY_TICKS = 4;

    private MortarAim() {}

    public record Prediction(Vec3 impact, int ticks, double range, boolean assumedLevel) {}

    public static Prediction predict(MortarEntity mortar, float partialTick) {
        float elevation = mortar.elevation(partialTick);
        float yaw = mortar.getYRot();
        Vec3 origin = mortar.position();
        Vec3 position = MortarRig.muzzleWorld(origin, yaw, elevation);
        Vec3 motion = MortarRig.launchVelocity(yaw, elevation);
        Level level = mortar.level();
        for (int tick = 1; tick <= MortarRig.MAX_FLIGHT_TICKS; tick++) {
            Vec3 stepped = MortarRig.stepVelocity(motion);
            Vec3 next = position.add(stepped);
            // The client has only the chunks within render distance; any other is an empty stand-in that a ray passes
            // through (hasChunkAt is true for it, and false above the build limit, where a high round flies).
            if (level.getChunk(SectionPos.blockToSectionCoord(next.x), SectionPos.blockToSectionCoord(next.z)) instanceof EmptyLevelChunk) {
                MortarRig.Impact rest = MortarRig.predictToHeight(position, motion, origin.y);
                int ticks = tick - 1 + rest.ticks();
                return new Prediction(rest.position(), ticks, rest.horizontalDistance(origin), true);
            }
            // Ground first: a steep round falls ~10 blocks a step and can pass the ground and the world's floor in one.
            HitResult hit = level.clip(new ClipContext(position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, mortar));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 at = hit.getLocation();
                return new Prediction(at, tick, horizontal(origin, at), false);
            }
            if (next.y < level.getMinY()) {
                break;
            }
            position = next;
            motion = stepped;
        }
        return new Prediction(position, MortarRig.MAX_FLIGHT_TICKS, horizontal(origin, position), true);
    }

    private static double horizontal(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Marks the predicted impact for the gunner (only for them, and only on loaded terrain). */
    public static void tick(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.isPaused()
                || !(minecraft.player.getVehicle() instanceof MortarEntity mortar)
                || mortar.getControllingPassenger() != minecraft.player
                || minecraft.level.getGameTime() % MARK_EVERY_TICKS != 0) {
            return;
        }
        Prediction prediction = predict(mortar, 1.0F);
        if (prediction.assumedLevel()) {
            return;
        }
        Vec3 at = prediction.impact();
        for (int i = 0; i < 6; i++) {
            minecraft.level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, at.x, at.y + 0.2D + i * 0.6D, at.z,
                    0.0D, 0.02D, 0.0D);
        }
    }
}
