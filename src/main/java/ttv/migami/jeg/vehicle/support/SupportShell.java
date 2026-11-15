package ttv.migami.jeg.vehicle.support;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.block.VehicleShellBlock;
import ttv.migami.jeg.init.ModBlocks;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * The walk-in room of a deployed field support vehicle. Deploying first checks that the vehicle can stand square on
 * the block grid with every cell of its room free ({@link #plan}); once unfolded, the room's floor, walls, ceiling,
 * steps and bays are placed as {@link VehicleShellBlock}s ({@link #place}), and removed again before it packs up.
 * A saved registry per dimension maps every shell block to its vehicle, so a block left behind by a vehicle that no
 * longer exists clears itself.
 */
public final class SupportShell {
    /** Turned up to this far from a cardinal direction, the vehicle squares itself up as it lifts. */
    public static final float HEADING_TOLERANCE = 10.0F;

    private SupportShell() {}

    /** Why a deploy or pack-up was refused (the HUD's message), or OK. */
    public enum Status {
        OK, MOVING, HEADING, BLOCKED, UNSUPPORTED, PROTECTED, IN_THE_WAY, PLAYERS_INSIDE, WATER, NO_DRIVER
    }

    /** Where the vehicle will stand when deployed, or why it cannot. */
    public record Plan(Status status, @Nullable BlockPos where, Vec3 target, float yaw, int floor) {
        static Plan refused(Status status, @Nullable BlockPos where) {
            return new Plan(status, where, Vec3.ZERO, 0, 0);
        }

        public boolean ok() {
            return this.status == Status.OK;
        }
    }

    /** One placed cell: where it is and the block state it holds. */
    public record Placed(BlockPos pos, SupportRig.ShellCell cell, BlockState state) {}

    /**
     * Square the vehicle up: the nearest cardinal heading, the room's corner on whole block coordinates and the floor
     * one block above the ground (lifting the body up to a block and a half on its jacks where the ground falls
     * away), then check every cell of the room.
     */
    public static Plan plan(SupportVehicleEntity vehicle, ServerPlayer operator) {
        ServerLevel level = (ServerLevel) vehicle.level();
        float yaw = Mth.wrapDegrees(vehicle.getYRot());
        float snapped = Math.round(yaw / 90.0F) * 90.0F;
        if (Math.abs(Mth.wrapDegrees(yaw - snapped)) > HEADING_TOLERANCE) {
            return Plan.refused(Status.HEADING, null);
        }
        Vec3 corner = SupportRig.world(vehicle.position(), snapped, SupportRig.CORNER_X, 0, SupportRig.CORNER_Z);
        Vec3 origin = vehicle.position().add(Math.round(corner.x) - corner.x, 0, Math.round(corner.z) - corner.z);
        int base = Mth.floor(vehicle.getY() + 1.0E-3);
        Plan best = null;
        for (int floorBelow = base; floorBelow <= base + 1; floorBelow++) {
            Vec3 target = new Vec3(origin.x, floorBelow, origin.z);
            Plan plan = check(level, vehicle, operator, target, snapped, floorBelow + 1);
            if (plan.ok()) {
                return plan;
            }
            if (best == null) {
                best = plan;
            }
        }
        return best;
    }

    private static Plan check(ServerLevel level, SupportVehicleEntity vehicle, ServerPlayer operator, Vec3 target, float yaw, int floor) {
        for (Placed placed : layout(target, yaw, null)) {
            BlockPos pos = placed.pos();
            BlockState state = level.getBlockState(pos);
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(operator, pos)
                    || operator.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
                return new Plan(Status.PROTECTED, pos, target, yaw, floor);
            }
            if (!state.getFluidState().isEmpty()) {
                return new Plan(Status.WATER, pos, target, yaw, floor);
            }
            if (!isClear(level, pos, state, vehicle.getUUID())) {
                return new Plan(Status.BLOCKED, pos, target, yaw, floor);
            }
            // The ramp's steps must stand on the ground, or the room could not be walked into.
            if (placed.cell().kind() == SupportRig.Cell.STEP_LOW || placed.cell().kind() == SupportRig.Cell.STEP_HIGH) {
                BlockPos below = pos.below();
                if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                    return new Plan(Status.UNSUPPORTED, below, target, yaw, floor);
                }
            }
        }
        // Something standing where the slide-outs and walls will go.
        for (Placed placed : layout(target, yaw, null)) {
            if (placed.state().getCollisionShape(level, placed.pos()).isEmpty()) {
                continue;
            }
            AABB cell = new AABB(placed.pos());
            for (Entity entity : level.getEntities(vehicle, cell, e -> e.isAlive() && !e.isSpectator() && e.getVehicle() != vehicle
                    && !(e instanceof net.minecraft.world.entity.item.ItemEntity))) {
                return new Plan(Status.IN_THE_WAY, placed.pos(), target, yaw, floor);
            }
        }
        // Each jack needs ground within its reach below the lifted body.
        for (double[] jack : SupportRig.JACKS) {
            Vec3 foot = SupportRig.world(target, yaw, jack[0], SupportRig.JACK_FOOT_Y, jack[1]);
            if (groundBelow(level, foot, SupportRig.JACK_REACH_BLOCKS) == null) {
                return new Plan(Status.UNSUPPORTED, BlockPos.containing(foot), target, yaw, floor);
            }
        }
        return new Plan(Status.OK, null, target, yaw, floor);
    }

    /** A cell is free if it is air, a plant or snow layer that can be replaced, or already this vehicle's shell. */
    private static boolean isClear(ServerLevel level, BlockPos pos, BlockState state, UUID vehicle) {
        if (state.isAir()) {
            return true;
        }
        if (state.getBlock() instanceof VehicleShellBlock || state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                && vehicle.equals(registry(level).owner(pos))) {
            UUID owner = registry(level).owner(pos);
            return owner == null || owner.equals(vehicle) || state.getValue(VehicleShellBlock.KIND) == VehicleShellBlock.Kind.LAMP;
        }
        return state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    /** The top of the ground below a point within {@code reach} blocks, or null. */
    @Nullable
    public static Double groundBelow(Level level, Vec3 point, double reach) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = Mth.floor(point.y);
        int bottom = Mth.floor(point.y - reach);
        for (int y = top; y >= bottom; y--) {
            pos.set(Mth.floor(point.x), y, Mth.floor(point.z));
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof VehicleShellBlock) {
                continue;
            }
            var shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                double surface = y + shape.max(Direction.Axis.Y);
                if (surface <= point.y + 1.0E-3 && surface >= point.y - reach) {
                    return surface;
                }
            }
        }
        return null;
    }

    /** Every shell cell of a vehicle deployed at {@code origin}, with the state it takes. */
    public static List<Placed> layout(Vec3 origin, float yaw, @Nullable SupportVehicleEntity vehicle) {
        List<Placed> out = new ArrayList<>();
        Direction left = Direction.fromYRot(yaw - 90.0F);
        Direction forward = Direction.fromYRot(yaw);
        int roomLight = vehicle != null ? vehicle.modules().roomLight() : 0;
        for (SupportRig.ShellCell cell : SupportRig.shell()) {
            BlockPos pos = cellPos(origin, yaw, cell);
            VehicleShellBlock.Kind kind = switch (cell.kind()) {
                case FLOOR -> VehicleShellBlock.Kind.FLOOR;
                case ROOM, BUNK -> VehicleShellBlock.Kind.ROOM;
                case BAY -> VehicleShellBlock.Kind.BAY;
                // The front wall between the bunks (the partition behind the cab) holds two panels side by side: the
                // support console on the right of the aisle (column 1), the beacon terminal on the left (column 2).
                case WALL -> cell.row() != -1 ? VehicleShellBlock.Kind.WALL
                        : cell.column() == 1 ? VehicleShellBlock.Kind.PANEL
                        : cell.column() == 2 ? VehicleShellBlock.Kind.BEACON_PANEL : VehicleShellBlock.Kind.WALL;
                case ROOF -> VehicleShellBlock.Kind.ROOF;
                case STEP_HIGH -> VehicleShellBlock.Kind.STEP_HIGH;
                case STEP_LOW -> VehicleShellBlock.Kind.STEP_LOW;
            };
            // FACING is the side of the cell its skin is on, the side next to the room: the left wall's cell lies
            // left of the room, so its skin is on its right side, and so on.
            Direction facing = switch (cell.facing()) {
                case SupportRig.FACE_LEFT -> left.getOpposite();
                case SupportRig.FACE_RIGHT -> left;
                case SupportRig.FACE_FRONT -> forward.getOpposite();
                case SupportRig.FACE_REAR -> forward;
                default -> Direction.NORTH;
            };
            boolean lit = kind == VehicleShellBlock.Kind.ROOM && cell.level() == 1;
            BlockState state = ModBlocks.VEHICLE_SHELL.get().defaultBlockState()
                    .setValue(VehicleShellBlock.KIND, kind)
                    .setValue(VehicleShellBlock.FACING, kind == VehicleShellBlock.Kind.WALL || kind == VehicleShellBlock.Kind.PANEL || kind == VehicleShellBlock.Kind.BEACON_PANEL ? facing : Direction.NORTH)
                    .setValue(VehicleShellBlock.LIGHT, lit ? roomLight : 0);
            // A bunk with a bed fitted becomes that bed, head to the front of the room.
            if (vehicle != null && cell.kind() == SupportRig.Cell.BUNK) {
                int slot = cell.column() == 3 ? SupportModules.BUNK_LEFT : SupportModules.BUNK_RIGHT;
                if (vehicle.modules().fitted(slot).getItem() instanceof net.minecraft.world.item.BlockItem item
                        && item.getBlock() instanceof net.minecraft.world.level.block.BedBlock bed) {
                    state = bed.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, forward)
                            .setValue(net.minecraft.world.level.block.BedBlock.PART, cell.row() == 0
                                    ? net.minecraft.world.level.block.state.properties.BedPart.HEAD
                                    : net.minecraft.world.level.block.state.properties.BedPart.FOOT);
                }
            }
            out.add(new Placed(pos, cell, state));
        }
        return out;
    }

    public static BlockPos cellPos(Vec3 origin, float yaw, SupportRig.ShellCell cell) {
        Vec3 centre = cell.centre();
        return BlockPos.containing(SupportRig.world(origin, yaw, centre.x, centre.y, centre.z));
    }

    /**
     * Camp floodlight cells: ground-level light under the awning, behind the ramp and down the right side, so mobs do
     * not spawn round a lit camp. Placed only in open air, and given up to anything built there.
     */
    public static List<BlockPos> lampCells(Vec3 origin, float yaw) {
        List<BlockPos> out = new ArrayList<>();
        for (int row = 0; row < SupportRig.ROWS; row += 2) {
            for (int column : new int[]{5, -2, -4}) out.add(cellPos(origin, yaw, new SupportRig.ShellCell(column, row, -1, SupportRig.Cell.ROOM, 0)));
        }
        for (int column = -1; column <= SupportRig.COLUMNS; column += 2) {
            out.add(cellPos(origin, yaw, new SupportRig.ShellCell(column, SupportRig.ROWS + 3, -1, SupportRig.Cell.ROOM, 0)));
        }
        out.add(cellPos(origin, yaw, new SupportRig.ShellCell(7, 2, -1, SupportRig.Cell.ROOM, 0)));
        return out;
    }

    /** Place the room. Returns false (and places nothing) if a cell is no longer free. */
    public static boolean place(SupportVehicleEntity vehicle, Vec3 origin, float yaw) {
        ServerLevel level = (ServerLevel) vehicle.level();
        List<Placed> cells = layout(origin, yaw, vehicle);
        for (Placed placed : cells) {
            if (!isClear(level, placed.pos(), level.getBlockState(placed.pos()), vehicle.getUUID())) {
                return false;
            }
        }
        Registry registry = registry(level);
        List<Long> positions = new ArrayList<>();
        for (Placed placed : cells) {
            level.setBlock(placed.pos(), placed.state(), Block.UPDATE_ALL);
            positions.add(placed.pos().asLong());
        }
        if (vehicle.modules().campLit()) {
            for (BlockPos lamp : lampCells(origin, yaw)) {
                if (level.getBlockState(lamp).isAir() && level.getWorldBorder().isWithinBounds(lamp)) {
                    level.setBlock(lamp, lampState(), Block.UPDATE_ALL);
                    positions.add(lamp.asLong());
                }
            }
        }
        registry.put(vehicle.getUUID(), positions);
        for (int side = 0; side < 2; side++) {
            int slot = side == 0 ? SupportModules.BUNK_LEFT : SupportModules.BUNK_RIGHT;
            vehicle.setBunkBed(side, vehicle.modules().fitted(slot).getItem() instanceof net.minecraft.world.item.BlockItem item
                    && item.getBlock() instanceof net.minecraft.world.level.block.BedBlock);
        }
        return true;
    }

    private static BlockState lampState() {
        return ModBlocks.VEHICLE_SHELL.get().defaultBlockState().setValue(VehicleShellBlock.KIND, VehicleShellBlock.Kind.LAMP)
                .setValue(VehicleShellBlock.LIGHT, 15);
    }

    /**
     * Remove every block this vehicle placed (loaded chunks; shell blocks elsewhere clear themselves when they load).
     * Returns which bunks' beds were gone (destroyed while deployed), so the vehicle can use up those fitted beds.
     */
    public static boolean[] remove(ServerLevel level, UUID vehicle) {
        boolean[] lostBeds = new boolean[2];
        Registry registry = registry(level);
        List<Long> positions = registry.take(vehicle);
        if (positions == null) {
            return lostBeds;
        }
        Entity entity = level.getEntity(vehicle);
        java.util.Set<Long> bedCells = new java.util.HashSet<>();
        if (entity instanceof SupportVehicleEntity truck) {
            for (Placed placed : layout(truck.position(), truck.getYRot(), truck)) {
                if (placed.state().getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                    bedCells.add(placed.pos().asLong());
                    int side = placed.cell().column() == 3 ? 0 : 1;
                    if (!(level.getBlockState(placed.pos()).getBlock() instanceof net.minecraft.world.level.block.BedBlock)) lostBeds[side] = true;
                }
            }
        }
        for (long packed : positions) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof VehicleShellBlock || bedCells.contains(packed) && state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                // Beds go quietly: no drop (the bed is still fitted in the vehicle).
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return lostBeds;
    }

    /** Set the room's light, the camp lamps and the bunks' beds after the fittings of a deployed vehicle change. */
    public static void relight(SupportVehicleEntity vehicle) {
        if (!(vehicle.level() instanceof ServerLevel level) || !isPlaced(vehicle)) return;
        checkBeds(vehicle);
        int light = vehicle.modules().roomLight();
        for (Placed placed : layout(vehicle.position(), vehicle.getYRot(), vehicle)) {
            BlockState state = level.getBlockState(placed.pos());
            if (state.getBlock() instanceof VehicleShellBlock && state.getValue(VehicleShellBlock.KIND) == VehicleShellBlock.Kind.ROOM
                    && placed.cell().level() == 1 && state.getValue(VehicleShellBlock.LIGHT) != light) {
                level.setBlock(placed.pos(), state.setValue(VehicleShellBlock.LIGHT, light), Block.UPDATE_ALL);
            }
        }
        Registry registry = registry(level);
        List<Long> positions = new ArrayList<>(registry.cells.get(vehicle.getUUID()));
        boolean lit = vehicle.modules().campLit();
        for (BlockPos lamp : lampCells(vehicle.position(), vehicle.getYRot())) {
            BlockState state = level.getBlockState(lamp);
            boolean ours = vehicle.getUUID().equals(registry.owner(lamp));
            if (lit && !ours && state.isAir()) {
                level.setBlock(lamp, lampState(), Block.UPDATE_ALL);
                positions.add(lamp.asLong());
            } else if (!lit && ours && state.getBlock() instanceof VehicleShellBlock) {
                level.setBlock(lamp, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                positions.remove(lamp.asLong());
            }
        }
        registry.put(vehicle.getUUID(), positions);
    }

    /**
     * Bunks follow the fitted beds while deployed: a bed fitted is placed (head to the front), a bed taken out of the
     * Fit-out screen is removed quietly (the item is already in the player's hand). A bunk's bed found destroyed some
     * other way (an explosion, a mob) has dropped as an item, so the fitted bed is used up: never doubled.
     */
    public static void checkBeds(SupportVehicleEntity vehicle) {
        if (!(vehicle.level() instanceof ServerLevel level) || !isPlaced(vehicle)) return;
        Direction forward = Direction.fromYRot(vehicle.getYRot());
        for (int side = 0; side < 2; side++) {
            int column = side == 0 ? 3 : 0;
            int slot = side == 0 ? SupportModules.BUNK_LEFT : SupportModules.BUNK_RIGHT;
            BlockPos head = cellPos(vehicle.position(), vehicle.getYRot(), new SupportRig.ShellCell(column, 0, 0, SupportRig.Cell.BUNK, 0));
            BlockPos foot = cellPos(vehicle.position(), vehicle.getYRot(), new SupportRig.ShellCell(column, 1, 0, SupportRig.Cell.BUNK, 0));
            BlockState headNow = level.getBlockState(head), footNow = level.getBlockState(foot);
            boolean headBed = headNow.getBlock() instanceof net.minecraft.world.level.block.BedBlock;
            boolean footBed = footNow.getBlock() instanceof net.minecraft.world.level.block.BedBlock;
            if (vehicle.bunkBed(side) && !headBed && !footBed) {
                // The bed this vehicle placed is gone (blown up, broken by a mob): it dropped, so the fitting is used up.
                vehicle.modules().fittings.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
                vehicle.setBunkBed(side, false);
            }
            BlockState headWant = roomState(), footWant = roomState();
            if (vehicle.modules().fitted(slot).getItem() instanceof net.minecraft.world.item.BlockItem item
                    && item.getBlock() instanceof net.minecraft.world.level.block.BedBlock bed) {
                BlockState base = bed.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, forward);
                headWant = base.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD);
                footWant = base.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT);
            }
            // Only cells that are this vehicle's (its shell, its beds, or emptied by them) are changed.
            boolean ours = (headBed || headNow.getBlock() instanceof VehicleShellBlock || headNow.isAir())
                    && (footBed || footNow.getBlock() instanceof VehicleShellBlock || footNow.isAir());
            if (!ours) continue;
            int flags = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;
            if (!headNow.equals(headWant)) level.setBlock(head, headWant, flags);
            if (!level.getBlockState(foot).equals(footWant)) level.setBlock(foot, footWant, flags);
            vehicle.setBunkBed(side, headWant.getBlock() instanceof net.minecraft.world.level.block.BedBlock);
        }
    }

    private static BlockState roomState() {
        return ModBlocks.VEHICLE_SHELL.get().defaultBlockState().setValue(VehicleShellBlock.KIND, VehicleShellBlock.Kind.ROOM);
    }

    /** Which wall bay (0..5) a shell cell is, or -1. */
    public static int bayAt(SupportVehicleEntity vehicle, BlockPos pos) {
        for (SupportRig.ShellCell cell : SupportRig.shell()) {
            if (cell.kind() == SupportRig.Cell.BAY && cellPos(vehicle.position(), vehicle.getYRot(), cell).equals(pos)) {
                return SupportRig.bayIndex(cell.column(), cell.row());
            }
        }
        return -1;
    }

    /** Players standing in the room (who would be shut in by packing up). */
    public static int playersInside(SupportVehicleEntity vehicle) {
        if (!(vehicle.level() instanceof ServerLevel level)) {
            return 0;
        }
        List<Long> positions = registry(level).cells.get(vehicle.getUUID());
        if (positions == null) {
            return 0;
        }
        int count = 0;
        for (Player player : level.players()) {
            if (player.isSpectator() || player.getVehicle() == vehicle) {
                continue;
            }
            BlockPos feet = player.blockPosition();
            UUID owner = registry(level).owner(feet);
            if (vehicle.getUUID().equals(owner)) {
                count++;
            }
        }
        return count;
    }

    /** Whether a player is standing in this vehicle's room. */
    public static boolean isInside(SupportVehicleEntity vehicle, Player player) {
        if (!(vehicle.level() instanceof ServerLevel level)) {
            // Client side: inside the deployed room's footprint, above the floor.
            Vec3 f = SupportRig.forward(vehicle.getYRot());
            Vec3 rel = player.position().subtract(vehicle.position());
            double x = (rel.x * f.z - rel.z * f.x) * 16, z = -(rel.x * f.x + rel.z * f.z) * 16, y = rel.y * 16;
            return vehicle.isDeployed() && Math.abs(x) < SupportRig.INTERIOR_X && z > SupportRig.INTERIOR_FRONT
                    && z < SupportRig.INTERIOR_REAR + 4 && y > SupportRig.FLOOR_Y - 2;
        }
        return vehicle.getUUID().equals(registry(level).owner(player.blockPosition()));
    }

    /** True if this vehicle's room is standing. */
    public static boolean isPlaced(SupportVehicleEntity vehicle) {
        return vehicle.level() instanceof ServerLevel level && registry(level).cells.containsKey(vehicle.getUUID());
    }

    /** Right-clicking a shell cell: a bay opens its module, the support panel the console, the beacon panel the terminal. */
    public static InteractionResult use(Level level, BlockPos pos, BlockState state, Player player, InteractionHand hand) {
        VehicleShellBlock.Kind kind = state.getValue(VehicleShellBlock.KIND);
        if (kind != VehicleShellBlock.Kind.BAY && kind != VehicleShellBlock.Kind.PANEL && kind != VehicleShellBlock.Kind.BEACON_PANEL) {
            return InteractionResult.PASS;
        }
        if (kind == VehicleShellBlock.Kind.BEACON_PANEL) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            SupportVehicleEntity vehicle = owner((ServerLevel) level, pos);
            if (vehicle == null || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            vehicle.openBeaconMenu(serverPlayer);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (kind == VehicleShellBlock.Kind.PANEL) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            SupportVehicleEntity vehicle = owner((ServerLevel) level, pos);
            if (vehicle == null || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            vehicle.openFitMenu(serverPlayer);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        SupportVehicleEntity vehicle = owner((ServerLevel) level, pos);
        if (vehicle == null || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        return vehicle.useBay(serverPlayer, pos) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
    }

    @Nullable
    public static SupportVehicleEntity owner(ServerLevel level, BlockPos pos) {
        UUID owner = registry(level).owner(pos);
        if (owner == null) {
            return null;
        }
        return level.getEntity(owner) instanceof SupportVehicleEntity vehicle ? vehicle : null;
    }

    /**
     * A shell block whose vehicle is gone clears itself: no registry entry for it, or the registry names a vehicle
     * that is not in the world although its chunk's entities are loaded.
     */
    public static void removeIfOrphaned(ServerLevel level, BlockPos pos) {
        Registry registry = registry(level);
        UUID owner = registry.owner(pos);
        boolean orphan;
        if (owner == null) {
            orphan = true;
        } else {
            Entity entity = level.getEntity(owner);
            if (entity instanceof SupportVehicleEntity vehicle && !vehicle.isRemoved()) {
                orphan = false;
            } else {
                Long anchor = registry.anchors.get(owner);
                orphan = anchor != null && level.areEntitiesLoaded(ChunkPos.pack(BlockPos.of(anchor)));
                if (orphan) {
                    remove(level, owner);
                    return;
                }
            }
        }
        if (orphan) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Remember where the vehicle stands, so its blocks can tell whether its chunk is loaded. */
    public static void anchor(SupportVehicleEntity vehicle) {
        if (vehicle.level() instanceof ServerLevel level) {
            registry(level).anchor(vehicle.getUUID(), vehicle.blockPosition());
        }
    }

    public static Registry registry(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(Registry.TYPE);
    }

    /** Shell blocks by vehicle, and the vehicle's position for the orphan check. Saved with the dimension. */
    public static final class Registry extends SavedData {
        private static final Codec<Registry> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG.listOf()).fieldOf("cells").forGetter(r -> r.cells),
                Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG).fieldOf("anchors").forGetter(r -> r.anchors)
        ).apply(instance, Registry::new));
        public static final SavedDataType<Registry> TYPE = new SavedDataType<>(Reference.id("support_vehicle_shells"), Registry::new, CODEC, null);

        private final Map<UUID, List<Long>> cells = new HashMap<>();
        private final Map<UUID, Long> anchors = new HashMap<>();
        private final Map<Long, UUID> owners = new HashMap<>();

        public Registry() {}

        private Registry(Map<UUID, List<Long>> cells, Map<UUID, Long> anchors) {
            this.cells.putAll(cells);
            this.anchors.putAll(anchors);
            cells.forEach((vehicle, positions) -> positions.forEach(pos -> this.owners.put(pos, vehicle)));
        }

        @Nullable
        public UUID owner(BlockPos pos) {
            return this.owners.get(pos.asLong());
        }

        void put(UUID vehicle, List<Long> positions) {
            List<Long> old = this.cells.put(vehicle, List.copyOf(positions));
            if (old != null) {
                old.forEach(this.owners::remove);
            }
            positions.forEach(pos -> this.owners.put(pos, vehicle));
            this.setDirty();
        }

        @Nullable
        List<Long> take(UUID vehicle) {
            List<Long> positions = this.cells.remove(vehicle);
            this.anchors.remove(vehicle);
            if (positions != null) {
                positions.forEach(pos -> this.owners.remove(pos, vehicle));
                this.setDirty();
            }
            return positions;
        }

        void anchor(UUID vehicle, BlockPos pos) {
            Long previous = this.anchors.put(vehicle, pos.asLong());
            if (previous == null || previous != pos.asLong()) {
                this.setDirty();
            }
        }

        public int size() {
            return this.cells.size();
        }
    }
}
