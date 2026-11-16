package ttv.migami.jeg.vehicle.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Fire missions for indirect-fire guns (the towed howitzer, the PLZ-05): a target from an observer's spyglass or typed
 * coordinates, handed to every gun that can reach it.
 */
public final class FireMissions {
    /** How far an observer can mark, blocks (the client picks the block looked at within this). */
    public static final double OBSERVER_MARK_RANGE = 512.0D;

    private FireMissions() {}

    /** A gun that takes fire missions. */
    public interface Gun {
        @Nullable
        BlockPos target();

        void setTarget(@Nullable BlockPos target);

        /** How far from an observer this gun still takes the observer's marks, blocks. */
        double observerReach();

        /** Tell the crew a mission came in (a ping and the target's grid). */
        void notifyCrew();

        /** Typed coordinates ({@code /justEnoughGuns firemission <x> <z>}): the ground there if loaded, else the gun's level. */
        default void setTargetByCoordinates(int x, int z) {
            Entity gun = (Entity) this;
            Level level = gun.level();
            int y = level.hasChunkAt(new BlockPos(x, 0, z))
                    ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1
                    : Mth.floor(gun.getY()) - 1;
            this.setTarget(new BlockPos(x, y, z));
            this.notifyCrew();
        }
    }

    /** The point a target block is aimed at: the middle of its top face. */
    public static Vec3 aimPoint(BlockPos target) {
        return new Vec3(target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D);
    }

    /** Tells a gun's crew of a new mission: the target's grid to the gunner and a bell to everyone aboard. */
    public static void notifyCrew(VehicleEntity gun, @Nullable ServerPlayer gunner) {
        BlockPos target = gun instanceof Gun mission ? mission.target() : null;
        if (target == null || gun.getPassengers().isEmpty()) {
            return;
        }
        if (gunner != null) {
            gunner.sendOverlayMessage(Component.translatable("message.jeg.howitzer.fire_mission", target.getX(), target.getZ()));
        }
        gun.level().playSound(null, gun.getX(), gun.getY() + 1.0D, gun.getZ(), SoundEvents.NOTE_BLOCK_BELL.value(),
                SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    /**
     * An observer looking through a spyglass marked {@code target}: every fire-mission gun within its reach of the
     * observer takes it, its crew hears a ping, and the observer is told the range from the nearest.
     */
    public static void observerMark(ServerPlayer observer, BlockPos target) {
        ServerLevel level = observer.level();
        List<VehicleEntity> guns = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof VehicleEntity vehicle && entity instanceof Gun gun && vehicle.isAlive() && !vehicle.isWrecked()
                    && vehicle.distanceToSqr(observer) <= gun.observerReach() * gun.observerReach()) {
                guns.add(vehicle);
            }
        }
        if (guns.isEmpty()) {
            observer.sendOverlayMessage(Component.translatable("message.jeg.howitzer.no_guns"));
            return;
        }
        Vec3 at = aimPoint(target);
        double best = Double.MAX_VALUE;
        for (VehicleEntity vehicle : guns) {
            Gun gun = (Gun) vehicle;
            gun.setTarget(target);
            gun.notifyCrew();
            best = Math.min(best, vehicle.position().distanceToSqr(at));
        }
        observer.sendOverlayMessage(Component.translatable("message.jeg.howitzer.marked", target.getX(), target.getZ(),
                Math.round(Math.sqrt(best)), guns.size()));
        level.sendParticles(observer, ParticleTypes.END_ROD, true, true, at.x, at.y + 1.5D, at.z, 24, 0.1D, 1.5D, 0.1D, 0.0D);
        level.playSound(null, observer.blockPosition(), SoundEvents.SPYGLASS_STOP_USING, SoundSource.PLAYERS, 1.0F, 1.6F);
    }
}
