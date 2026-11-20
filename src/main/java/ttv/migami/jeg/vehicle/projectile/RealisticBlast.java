package ttv.migami.jeg.vehicle.projectile;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.network.MissileBlastPayload;

/**
 * Every explosion drawn and heard as the A-10's and the helicopters' missiles are (MissileBlastRenderer: fireball,
 * real smoke column, shockwave and debris; the bang arriving with the shockwave), never the classic JEG burst of
 * sprite puffs or vanilla's (the user's review, 2026-11-18). The blast itself (damage, blocks) is vanilla's explosion
 * with its own particles and sound left out.
 */
public final class RealisticBlast {
    private RealisticBlast() {
    }

    /** Sends the drawn blast to everyone in sight of it. */
    public static void show(ServerLevel level, Vec3 at, float power, float radius, boolean incendiary) {
        MissileBlastPayload payload = new MissileBlastPayload(at, power, radius, incendiary, true);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(at) < VehicleMissileEntity.BLAST_VISIBLE_RANGE * VehicleMissileEntity.BLAST_VISIBLE_RANGE
                    && ServerPlayNetworking.canSend(player, MissileBlastPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    /** The drawn blast and vanilla's explosion under it (damage and blocks), without vanilla's puffs and bang. */
    public static void explode(ServerLevel level, @Nullable Entity source, @Nullable ExplosionDamageCalculator blocks, Vec3 at,
                               float power, boolean incendiary, Level.ExplosionInteraction interaction) {
        show(level, at, Math.max(1.0F, power), Math.max(2.0F, power * 1.6F), incendiary);
        level.explode(source, null, blocks, at.x, at.y, at.z, power, incendiary, interaction,
                ParticleTypes.SMOKE, ParticleTypes.SMOKE, WeightedList.of(), Holder.direct(SoundEvents.EMPTY));
        DistantTerrainRefresh.afterBlast();
    }
}
