package ttv.migami.jeg.vehicle.projectile;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.network.MissileBlastPayload;

/**
 * The secondary explosions after a big warhead goes off: whatever it hit (fuel, ammunition, the warhead's own unburnt
 * charge) cooks off in smaller blasts scattered around the crater over the next second or two, each drawn and heard like
 * a missile blast of its size, with its own crater and damage. The A-10's Mavericks have them: three for the AGM-65D,
 * five and bigger, with burning fuel among them, for the heavier AGM-65G.
 */
public final class SecondaryBlasts {
    /** Per warhead: how many, how strong, how wide each one's damage reaches, how far from the impact, over how long, and whether one burns. */
    private record Profile(int count, float power, float radius, double minSpread, double maxSpread, int minDelay, int maxDelay, boolean fuel) {}

    private static final Map<Identifier, Profile> PROFILES = Map.of(
            Reference.id("vehicle_agm65_missile"), new Profile(3, 2.6F, 4.5F, 2.5D, 6.0D, 5, 26, false),
            Reference.id("vehicle_agm65g_missile"), new Profile(5, 3.8F, 6.5F, 3.5D, 10.0D, 6, 42, true)
    );

    private record Pending(ServerLevel level, Vec3 position, float power, float radius, boolean incendiary, int at) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static int tick;

    private SecondaryBlasts() {}

    /** Schedules a warhead's secondary blasts around where it went off, if it has any. */
    public static void afterImpact(ServerLevel level, Identifier weaponId, Vec3 impact, RandomSource random) {
        Profile profile = PROFILES.get(weaponId);
        if (profile == null) {
            return;
        }
        for (int i = 0; i < profile.count(); i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = Mth.lerp(random.nextDouble(), profile.minSpread(), profile.maxSpread());
            double x = impact.x + Math.cos(angle) * distance;
            double z = impact.z + Math.sin(angle) * distance;
            double y = groundNear(level, x, impact.y, z);
            // Spread over the time, roughly in order, so they come one after another rather than together.
            int delay = Mth.floor(Mth.lerp((i + random.nextDouble()) / profile.count(), profile.minDelay(), profile.maxDelay()));
            float power = profile.power() * (0.8F + 0.4F * random.nextFloat());
            boolean burning = profile.fuel() && i == profile.count() / 2;
            PENDING.add(new Pending(level, new Vec3(x, y, z), power, profile.radius(), burning, tick + delay));
        }
    }

    /** The ground (or what is on it) near a point, not far above or below the impact; else the impact's own height. */
    private static double groundNear(ServerLevel level, double x, double y, double z) {
        BlockPos column = BlockPos.containing(x, y, z);
        if (!level.hasChunkAt(column)) {
            return y;
        }
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
        return Math.abs(surface - y) <= 6.0D ? surface + 0.2D : y;
    }

    public static void onServerTick(MinecraftServer server) {
        tick++;
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> iterator = PENDING.iterator();
        List<Pending> due = new ArrayList<>();
        while (iterator.hasNext()) {
            Pending pending = iterator.next();
            if (pending.at() <= tick) {
                iterator.remove();
                due.add(pending);
            }
        }
        for (Pending blast : due) {
            detonate(blast);
        }
    }

    private static void detonate(Pending blast) {
        ServerLevel level = blast.level();
        Vec3 at = blast.position();
        if (!level.hasChunkAt(BlockPos.containing(at))) {
            return;
        }
        // Drawn and heard like a missile blast of its size (the boom arriving with the shockwave); vanilla's own puffs
        // and bang are left out, as for the missile.
        MissileBlastPayload payload = new MissileBlastPayload(at, blast.power(), blast.radius(), blast.incendiary(), true);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(at) < VehicleMissileEntity.BLAST_VISIBLE_RANGE * VehicleMissileEntity.BLAST_VISIBLE_RANGE
                    && ServerPlayNetworking.canSend(player, MissileBlastPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
        level.explode(null, null, null, at.x, at.y, at.z, blast.power(), blast.incendiary(), Level.ExplosionInteraction.MOB,
                ParticleTypes.SMOKE, ParticleTypes.SMOKE, WeightedList.of(), Holder.direct(SoundEvents.EMPTY));
        DistantTerrainRefresh.afterBlast();
    }

    /** When the server stops, nothing is left waiting to go off. */
    public static void clear() {
        PENDING.clear();
    }

    /** For tests: how many are still to go off. */
    public static int pending() {
        return PENDING.size();
    }
}
