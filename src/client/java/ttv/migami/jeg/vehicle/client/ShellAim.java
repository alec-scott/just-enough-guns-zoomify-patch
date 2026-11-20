package ttv.migami.jeg.vehicle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.util.ShellBallistics;

/**
 * A gun's fire-control computer (the mortar's, the Mk42's): where a shell fired now would land. It flies the shell
 * tick by tick exactly as the server does (ShellBallistics), against the terrain while that is loaded; past the loaded
 * world it takes the ground as level with the gun and says so.
 */
public final class ShellAim {
    private ShellAim() {}

    /**
     * @param impact       where the shell lands
     * @param ticks        time of flight
     * @param range        horizontal distance from the gun
     * @param assumedLevel true when the shell left the loaded world and the ground was taken as level with the gun
     */
    public record Prediction(Vec3 impact, int ticks, double range, boolean assumedLevel) {}

    public static Prediction predict(Level level, Entity gun, Vec3 muzzle, Vec3 velocity, ShellBallistics ballistics) {
        Vec3 origin = gun.position();
        Vec3 position = muzzle;
        Vec3 motion = velocity;
        for (int tick = 1; tick <= ballistics.maxFlightTicks(); tick++) {
            Vec3 stepped = ballistics.step(motion);
            Vec3 next = position.add(stepped);
            // The client has only the chunks within render distance; any other is an empty stand-in that a ray passes
            // through (hasChunkAt is true for it, and false above the build limit, where a high shell flies).
            if (level.getChunk(SectionPos.blockToSectionCoord(next.x), SectionPos.blockToSectionCoord(next.z)) instanceof EmptyLevelChunk) {
                ShellBallistics.Impact rest = ballistics.predictToHeight(position, motion, origin.y);
                return new Prediction(rest.position(), tick - 1 + rest.ticks(), rest.horizontalDistance(origin), true);
            }
            // Ground first: a steep shell falls many blocks a step and can pass the ground and the world's floor in one.
            HitResult hit = level.clip(new ClipContext(position, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, gun));
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
        return new Prediction(position, ballistics.maxFlightTicks(), horizontal(origin, position), true);
    }

    private static double horizontal(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
