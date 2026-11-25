package ttv.migami.jeg.vehicle.cabin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import ttv.migami.jeg.Reference;

/**
 * The server side of walk-in cabins and their packets.
 * <ul>
 *     <li>A player aboard reports where they stand in the cabin ({@link MovePayload}); the server places them there
 *     each tick from its own pose of the vehicle, moves their chunk loading with them, and ignores the world positions
 *     of their move packets (ServerGamePacketListenerCabinMixin), so moving at the vehicle's speed is neither
 *     rubber-banded nor kicked as flying.</li>
 *     <li>Other players are sent where each person aboard stands in the cabin ({@link OccupantsPayload}) and draw them
 *     there, on the cabin as they see it.</li>
 *     <li>Blocks placed aboard live in the vehicle's grid ({@link UsePayload}, {@link GridPayload}).</li>
 *     <li>Any teleport takes a player off the cabin first.</li>
 * </ul>
 */
public final class CabinNetwork {
    // ---- Payloads ----

    /** From a client: where its player stands in a cabin (id -1: not aboard any). */
    public record MovePayload(int id, double x, double y, double z, boolean onGround) implements CustomPacketPayload {
        public static final Type<MovePayload> TYPE = new Type<>(Reference.id("cabin_move"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MovePayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id + 1);
                    buf.writeDouble(p.x);
                    buf.writeDouble(p.y);
                    buf.writeDouble(p.z);
                    buf.writeBoolean(p.onGround);
                },
                buf -> new MovePayload(buf.readVarInt() - 1, buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()));

        @Override
        public Type<MovePayload> type() {
            return TYPE;
        }
    }

    /** From a client: place (0), use (1) or take (2) the block at a cabin cell, with the main hand; or work control
     * {@code cell.getX()} (3: a ramp panel, a door). */
    public record UsePayload(int id, int action, BlockPos cell, int face) implements CustomPacketPayload {
        public static final Type<UsePayload> TYPE = new Type<>(Reference.id("cabin_use"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UsePayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeVarInt(p.action);
                    buf.writeBlockPos(p.cell);
                    buf.writeVarInt(p.face);
                },
                buf -> new UsePayload(buf.readVarInt(), buf.readVarInt(), buf.readBlockPos(), buf.readVarInt()));

        @Override
        public Type<UsePayload> type() {
            return TYPE;
        }
    }

    /** To clients: the blocks placed aboard a vehicle. */
    public record GridPayload(int id, List<BlockPos> cells, List<Integer> states) implements CustomPacketPayload {
        public static final Type<GridPayload> TYPE = new Type<>(Reference.id("cabin_grid"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GridPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeVarInt(p.cells.size());
                    for (int i = 0; i < p.cells.size(); i++) {
                        buf.writeBlockPos(p.cells.get(i));
                        buf.writeVarInt(p.states.get(i));
                    }
                },
                buf -> {
                    int id = buf.readVarInt();
                    int n = buf.readVarInt();
                    List<BlockPos> cells = new ArrayList<>(n);
                    List<Integer> states = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        cells.add(buf.readBlockPos());
                        states.add(buf.readVarInt());
                    }
                    return new GridPayload(id, cells, states);
                });

        @Override
        public Type<GridPayload> type() {
            return TYPE;
        }
    }

    /** To clients: a container aboard opened (its lid lifts) or closed by its last user. */
    public record LidPayload(int id, BlockPos cell, boolean open) implements CustomPacketPayload {
        public static final Type<LidPayload> TYPE = new Type<>(Reference.id("cabin_lid"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LidPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeBlockPos(p.cell);
                    buf.writeBoolean(p.open);
                },
                buf -> new LidPayload(buf.readVarInt(), buf.readBlockPos(), buf.readBoolean()));

        @Override
        public Type<LidPayload> type() {
            return TYPE;
        }
    }

    /** A container aboard opened or shut: everyone who sees the vehicle sees its lid move. */
    public static void sendLid(Entity vehicle, CabinVehicle cabin, BlockPos cell, boolean open) {
        LidPayload payload = new LidPayload(cabin.cabinId(), cell, open);
        for (ServerPlayer player : PlayerLookup.tracking(vehicle)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    /** To clients: who stands where in a vehicle's cabin (entity ids and cabin coordinates). */
    public record OccupantsPayload(int id, int[] players, double[] locals) implements CustomPacketPayload {
        public static final Type<OccupantsPayload> TYPE = new Type<>(Reference.id("cabin_occupants"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OccupantsPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.id);
                    buf.writeVarInt(p.players.length);
                    for (int i = 0; i < p.players.length; i++) {
                        buf.writeVarInt(p.players[i]);
                        buf.writeDouble(p.locals[i * 3]);
                        buf.writeDouble(p.locals[i * 3 + 1]);
                        buf.writeDouble(p.locals[i * 3 + 2]);
                    }
                },
                buf -> {
                    int id = buf.readVarInt();
                    int n = buf.readVarInt();
                    int[] players = new int[n];
                    double[] locals = new double[n * 3];
                    for (int i = 0; i < n; i++) {
                        players[i] = buf.readVarInt();
                        locals[i * 3] = buf.readDouble();
                        locals[i * 3 + 1] = buf.readDouble();
                        locals[i * 3 + 2] = buf.readDouble();
                    }
                    return new OccupantsPayload(id, players, locals);
                });

        @Override
        public Type<OccupantsPayload> type() {
            return TYPE;
        }
    }

    // ---- People aboard (server) ----

    public record Aboard(int vehicleId, Vec3 local, boolean onGround) {
    }

    private static final Map<UUID, Aboard> ABOARD = new ConcurrentHashMap<>();
    /** On a client, the cabin vehicle the local player stands in, or -1 (CabinClient sets it; common code reads it). */
    public static volatile java.util.function.IntSupplier clientAboard = () -> -1;
    /** Cabin vehicles alive on the server, for rounds to test against. */
    public static final Set<Entity> SERVER_CABINS = ConcurrentHashMap.newKeySet();

    private CabinNetwork() {
    }

    public static boolean isAboard(ServerPlayer player) {
        return ABOARD.containsKey(player.getUUID());
    }

    /** A teleport, death or dimension change takes the player off whatever cabin they stood in. */
    public static void detach(ServerPlayer player) {
        ABOARD.remove(player.getUUID());
    }

    /** Whether anyone stands in this vehicle's cabin (server). */
    public static boolean anyoneAboard(int vehicleId) {
        for (Aboard aboard : ABOARD.values()) {
            if (aboard.vehicleId() == vehicleId) {
                return true;
            }
        }
        return false;
    }

    public static Aboard aboard(ServerPlayer player) {
        return ABOARD.get(player.getUUID());
    }

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(MovePayload.TYPE, MovePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UsePayload.TYPE, UsePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(GridPayload.TYPE, GridPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OccupantsPayload.TYPE, OccupantsPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LidPayload.TYPE, LidPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(MovePayload.TYPE, (payload, context) -> context.server().execute(() -> {
            ServerPlayer player = context.player();
            if (payload.id() < 0 || !(player.level().getEntity(payload.id()) instanceof CabinVehicle)) {
                ABOARD.remove(player.getUUID());
            } else if (player.getVehicle() == null && !player.isDeadOrDying()) {
                ABOARD.put(player.getUUID(), new Aboard(payload.id(), new Vec3(payload.x(), payload.y(), payload.z()), payload.onGround()));
            }
        }));
        ServerPlayNetworking.registerGlobalReceiver(UsePayload.TYPE, (payload, context) -> context.server().execute(() -> use(context.player(), payload)));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ABOARD.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(CabinNetwork::serverTick);
    }

    public static void sendGrid(Entity vehicle, CabinVehicle cabin, ServerPlayer only) {
        List<BlockPos> cells = new ArrayList<>(cabin.cabinGrid().keySet());
        List<Integer> states = new ArrayList<>();
        for (BlockPos cell : cells) {
            states.add(Block.getId(cabin.cabinGrid().get(cell)));
        }
        GridPayload payload = new GridPayload(cabin.cabinId(), cells, states);
        if (only != null) {
            ServerPlayNetworking.send(only, payload);
            return;
        }
        for (ServerPlayer player : PlayerLookup.tracking(vehicle)) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static void serverTick(MinecraftServer server) {
        Map<Integer, List<ServerPlayer>> byVehicle = new java.util.HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                Aboard aboard = ABOARD.get(player.getUUID());
                if (aboard == null) {
                    continue;
                }
                Entity entity = level.getEntity(aboard.vehicleId());
                if (!(entity instanceof CabinVehicle cabin) || entity.isRemoved() || player.getVehicle() != null || player.isDeadOrDying()) {
                    ABOARD.remove(player.getUUID());
                    continue;
                }
                Vec3 world = CabinMath.feetWorld(cabin.localToWorld(1.0F), aboard.local());
                player.setPos(world.x, world.y, world.z);
                // Vanilla's move handling (skipped for people aboard) is also what moves their chunk loading with them:
                // without this a cabin flew out of the loaded area and froze (the 2026-11-20 trial).
                level.getChunkSource().move(player);
                player.resetFallDistance();
                player.setOnGround(aboard.onGround());
                // Their motion is the cabin's (what they throw leaves with it, GunItem's grenades read it).
                player.setDeltaMovement(carriedVelocity(player));
                byVehicle.computeIfAbsent(aboard.vehicleId(), id -> new ArrayList<>()).add(player);
            }
        }
        for (Entity vehicle : SERVER_CABINS) {
            if (vehicle.isRemoved()) {
                SERVER_CABINS.remove(vehicle);
                continue;
            }
            List<ServerPlayer> aboard = byVehicle.getOrDefault(vehicle.getId(), List.of());
            int[] players = new int[aboard.size()];
            double[] locals = new double[aboard.size() * 3];
            for (int i = 0; i < aboard.size(); i++) {
                Vec3 local = ABOARD.get(aboard.get(i).getUUID()).local();
                players[i] = aboard.get(i).getId();
                locals[i * 3] = local.x;
                locals[i * 3 + 1] = local.y;
                locals[i * 3 + 2] = local.z;
            }
            OccupantsPayload payload = new OccupantsPayload(vehicle.getId(), players, locals);
            for (ServerPlayer viewer : PlayerLookup.tracking(vehicle)) {
                ServerPlayNetworking.send(viewer, payload);
            }
        }
    }

    // ---- Blocks aboard ----

    private static void use(ServerPlayer player, UsePayload payload) {
        if (!(player.level().getEntity(payload.id()) instanceof CabinVehicle cabin)) {
            return;
        }
        Entity vehicle = (Entity) cabin;
        Aboard aboard = ABOARD.get(player.getUUID());
        if (payload.action() == 3) {
            if (aboard != null && aboard.vehicleId() == vehicle.getId()) {
                cabin.cabinControl(player, payload.cell().getX());
            }
            return;
        }
        if (!cabin.cabinLayout().isCell(payload.cell())) {
            return;
        }
        if (aboard == null || aboard.vehicleId() != vehicle.getId()
                || aboard.local().distanceTo(cabin.cabinLayout().cellBox(payload.cell()).getCenter()) > 6.0D) {
            return;
        }
        BlockPos cell = payload.cell();
        ServerLevel level = (ServerLevel) player.level();
        Vec3 at = CabinMath.toWorld(cabin.localToWorld(1.0F), cabin.cabinLayout().cellBox(cell).getCenter());
        switch (payload.action()) {
            case 0 -> {
                ItemStack stack = player.getMainHandItem();
                if (!(stack.getItem() instanceof BlockItem item) || cabin.cabinGrid().containsKey(cell)) {
                    cabin.cabinGridChanged();
                    return;
                }
                for (Aboard other : ABOARD.values()) {
                    if (other.vehicleId() == vehicle.getId() && CabinMath.body(other.local()).intersects(cabin.cabinLayout().cellBox(cell))) {
                        cabin.cabinGridChanged();
                        return;
                    }
                }
                BlockState state = CabinBlocks.placedState(item, Direction.from3DDataValue(payload.face()), facingToward(cabin, cell, aboard.local()));
                Component refusal = CabinBlocks.refusal(state);
                if (refusal != null || cabin.cabinCellBlocked(cell)) {
                    if (refusal != null) {
                        player.sendOverlayMessage(refusal);
                    }
                    cabin.cabinGridChanged();
                    return;
                }
                cabin.cabinGrid().put(cell, state);
                CabinBlocks.created(cabin, cell, state);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.playSound(null, at.x, at.y, at.z, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
                cabin.cabinGridChanged();
            }
            case 1 -> {
                BlockState state = cabin.cabinGrid().get(cell);
                if (state != null && CabinBlocks.lightable(state, player.getMainHandItem())) {
                    CabinBlocks.toggleLight(player, cabin, cell, state, at);
                } else if (state != null) {
                    CabinBlocks.open(player, cabin, cell, state, at);
                }
            }
            case 2 -> {
                BlockState state = cabin.cabinGrid().remove(cell);
                if (state == null) {
                    cabin.cabinGridChanged();
                    return;
                }
                SimpleContainer chest = cabin.cabinChests().get(cell);
                CabinBlocks.removed(cabin, cell);
                if (!player.getAbilities().instabuild) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(state.getBlock()));
                }
                if (chest != null) {
                    for (ItemStack item : chest.removeAllItems()) {
                        player.getInventory().placeItemBackInInventory(item);
                    }
                }
                level.playSound(null, at.x, at.y, at.z, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
                cabin.cabinGridChanged();
            }
            default -> {
            }
        }
    }

    /** A placed chest or furnace faces whoever placed it (in cabin coordinates, drawn turned half round, see the renderer). */
    private static Direction facingToward(CabinVehicle cabin, BlockPos cell, Vec3 placer) {
        Vec3 centre = cabin.cabinLayout().cellBox(cell).getCenter();
        double dx = placer.x - centre.x;
        double dz = placer.z - centre.z;
        return CabinMath.drawnFacing(Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH));
    }

    // ---- Rounds against the cabin ----

    /**
     * Called by rounds each server tick, before their own block and entity checks. A round whose step meets a cabin's
     * walls or the blocks aboard stops there with a spark; one fired by someone aboard does no harm to its own vehicle,
     * and one leaving by the open ramp or door flies on.
     */
    public static boolean clipRound(ServerLevel level, Vec3 from, Vec3 to, Entity round) {
        for (Entity vehicle : SERVER_CABINS) {
            if (vehicle.level() != level || vehicle.isRemoved() || !(vehicle instanceof CabinVehicle cabin)
                    || vehicle.position().distanceToSqr(from) > 48.0D * 48.0D + from.distanceToSqr(to)) {
                continue;
            }
            Matrix4d pose = cabin.localToWorld(1.0F);
            Vec3 a = CabinMath.toLocal(pose, from);
            // Only rounds inside the room: one from outside meets the hull's fitted parts as usual and does damage.
            if (!cabin.cabinLayout().inside().contains(a)) {
                continue;
            }
            Vec3 b = CabinMath.toLocal(pose, to);
            CabinMath.Hit hit = CabinMath.clip(a, b, cabin.cabinShell(), cabin.cabinGrid(), cabin.cabinLayout(), false);
            if (hit != null) {
                Vec3 at = CabinMath.toWorld(pose, hit.point());
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.05D, 0.05D, 0.05D, 0.2D);
                round.discard();
                return true;
            }
        }
        return false;
    }

    /**
     * The motion a person standing in a cabin has from it, over the last tick (blocks a tick), or zero for anyone else:
     * a round fired from the ramp of a Chinook at 60 m/s leaves at the round's speed plus the Chinook's.
     */
    public static Vec3 carriedVelocity(Entity entity) {
        if (!(entity instanceof ServerPlayer player)) {
            return Vec3.ZERO;
        }
        Aboard aboard = ABOARD.get(player.getUUID());
        Entity vehicle = aboard == null ? null : player.level().getEntity(aboard.vehicleId());
        return vehicle == null ? Vec3.ZERO : vehicle.position().subtract(vehicle.xo, vehicle.yo, vehicle.zo);
    }

    /** Whether a round's owner stands in this vehicle's cabin (its rounds leave through the openings, not the walls). */
    public static boolean ownerAboard(Entity owner, Entity vehicle) {
        return owner instanceof ServerPlayer player && ABOARD.containsKey(player.getUUID()) && ABOARD.get(player.getUUID()).vehicleId() == vehicle.getId();
    }
}
