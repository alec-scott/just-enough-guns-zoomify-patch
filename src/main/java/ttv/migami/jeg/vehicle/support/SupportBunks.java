package ttv.migami.jeg.vehicle.support;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * Bunks and respawning. A deployed support vehicle's fitted beds are real beds, so sleeping, skipping the night, the
 * phantom timer and other mods' rules (Undead Nights' horde nights) work as for any bed, and sleeping sets the
 * player's spawn there as a bed does. This remembers that the bed was a vehicle's bunk: if the bed is gone when the
 * player respawns (the vehicle packed up or drove off), they respawn beside the vehicle wherever it is now. Blocks a
 * deployed vehicle placed cannot be broken by players (so a bunk's bed cannot be taken out of the world and kept).
 */
public final class SupportBunks {
    private SupportBunks() {}

    public static void register() {
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !(level instanceof ServerLevel serverLevel) || SupportShell.registry(serverLevel).owner(pos) == null);
        EntitySleepEvents.START_SLEEPING.register((entity, sleepingPos) -> {
            if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
            UUID owner = SupportShell.registry(level).owner(sleepingPos);
            Memory memory = memory(level.getServer());
            if (owner != null) memory.players.put(player.getUUID(), new Bunk(owner, sleepingPos.asLong()));
            else memory.players.remove(player.getUUID());
            memory.setDirty();
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (alive) return;
            Memory memory = memory(newPlayer.level().getServer());
            Bunk bunk = memory.players.get(newPlayer.getUUID());
            if (bunk == null) return;
            if (newPlayer.blockPosition().distSqr(BlockPos.of(bunk.bed())) < 9) return;   // respawned in the bunk itself
            Where where = memory.vehicles.get(bunk.vehicle());
            if (where == null) {
                memory.players.remove(newPlayer.getUUID());
                memory.setDirty();
                return;
            }
            ServerLevel level = newPlayer.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(where.dimension())));
            if (level == null) return;
            Vec3 at = Vec3.atBottomCenterOf(BlockPos.of(where.pos()));
            level.getChunk(BlockPos.of(where.pos()));
            if (level.getEntity(bunk.vehicle()) instanceof SupportVehicleEntity truck) {
                // Beside the cab's left door, on whatever ground is there.
                at = SupportRig.world(truck, 30, 0, -46);
                Double ground = SupportShell.groundBelow(level, at.add(0, 3, 0), 8);
                if (ground != null) at = new Vec3(at.x, ground, at.z);
            }
            newPlayer.teleportTo(level, at.x, at.y, at.z, Set.of(), newPlayer.getYRot(), newPlayer.getXRot(), true);
        });
    }

    /** Called by each support vehicle now and then: where it is, for players who bunked in it. */
    public static void track(SupportVehicleEntity vehicle) {
        if (!(vehicle.level() instanceof ServerLevel level)) return;
        Memory memory = memory(level.getServer());
        if (!memory.players.isEmpty() && memory.players.values().stream().anyMatch(b -> b.vehicle().equals(vehicle.getUUID()))) {
            memory.vehicles.put(vehicle.getUUID(), new Where(level.dimension().identifier().toString(), vehicle.blockPosition().asLong()));
            memory.setDirty();
        }
    }

    /** The vehicle is gone for good: its bunks no longer bring anyone back. */
    public static void forget(SupportVehicleEntity vehicle) {
        if (!(vehicle.level() instanceof ServerLevel level)) return;
        Memory memory = memory(level.getServer());
        if (memory.vehicles.remove(vehicle.getUUID()) != null | memory.players.values().removeIf(b -> b.vehicle().equals(vehicle.getUUID()))) {
            memory.setDirty();
        }
    }

    private static Memory memory(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Memory.TYPE);
    }

    record Bunk(UUID vehicle, long bed) {
        static final Codec<Bunk> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.STRING_CODEC.fieldOf("vehicle").forGetter(Bunk::vehicle),
                Codec.LONG.fieldOf("bed").forGetter(Bunk::bed)).apply(i, Bunk::new));
    }

    record Where(String dimension, long pos) {
        static final Codec<Where> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Where::dimension),
                Codec.LONG.fieldOf("pos").forGetter(Where::pos)).apply(i, Where::new));
    }

    /** Who last slept in which vehicle's bunk, and where those vehicles were last seen. */
    static final class Memory extends SavedData {
        static final Codec<Memory> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Bunk.CODEC).fieldOf("players").forGetter(m -> m.players),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Where.CODEC).fieldOf("vehicles").forGetter(m -> m.vehicles)
        ).apply(i, Memory::new));
        static final SavedDataType<Memory> TYPE = new SavedDataType<>(Reference.id("support_vehicle_bunks"), Memory::new, CODEC, null);
        final Map<UUID, Bunk> players = new HashMap<>();
        final Map<UUID, Where> vehicles = new HashMap<>();

        Memory() {}

        Memory(Map<UUID, Bunk> players, Map<UUID, Where> vehicles) {
            this.players.putAll(players);
            this.vehicles.putAll(vehicles);
        }
    }
}
