package ttv.migami.jeg.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import ttv.migami.jeg.vehicle.support.SupportShell;

/**
 * The invisible structure a deployed field support vehicle stands in its room with: floor, walls, ceiling, steps,
 * module bays and the open room itself (which can hold nothing else). The vehicle model draws all of it. The blocks
 * are unbreakable, immovable and never dropped; each one knows its vehicle through {@link SupportShell}'s registry
 * and removes itself if that vehicle no longer exists.
 */
public final class VehicleShellBlock extends Block {
    public static final MapCodec<VehicleShellBlock> CODEC = simpleCodec(VehicleShellBlock::new);
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Light the cell gives: the room's lamps, or a camp floodlight outside. */
    public static final IntegerProperty LIGHT = BlockStateProperties.LEVEL;

    private static final VoxelShape FULL = Shapes.block();
    private static final VoxelShape ROOF = Block.box(0, 0, 0, 16, 2, 16);
    private static final VoxelShape STEP_HIGH = Block.box(0, 0, 0, 16, 12, 16);
    private static final VoxelShape STEP_LOW = Block.box(0, 0, 0, 16, 4, 16);
    private static final VoxelShape WALL_NORTH = Block.box(0, 0, 0, 16, 16, 2);
    private static final VoxelShape WALL_SOUTH = Block.box(0, 0, 14, 16, 16, 16);
    private static final VoxelShape WALL_WEST = Block.box(0, 0, 0, 2, 16, 16);
    private static final VoxelShape WALL_EAST = Block.box(14, 0, 0, 16, 16, 16);

    public enum Kind implements StringRepresentable {
        FLOOR, ROOM, BAY, WALL, ROOF, STEP_HIGH, STEP_LOW, LAMP,
        /** The front wall's support panel: a wall that can be clicked to fit the vehicle out. */
        PANEL,
        /** The front wall's beacon terminal: a wall that can be clicked to power and set the roof beacon. */
        BEACON_PANEL;

        @Override
        public @NotNull String getSerializedName() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public VehicleShellBlock(BlockBehaviour.Properties properties) {
        super(properties
                .mapColor(MapColor.NONE)
                .sound(SoundType.METAL)
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .noOcclusion()
                .noTerrainParticles()
                .pushReaction(PushReaction.BLOCK)
                .isValidSpawn((state, level, pos, type) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .lightLevel(state -> state.getValue(LIGHT))
                .dynamicShape()
                .randomTicks());
        this.registerDefaultState(this.getStateDefinition().any().setValue(KIND, Kind.ROOM).setValue(FACING, Direction.NORTH).setValue(LIGHT, 0));
    }

    @Override
    protected @NotNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND, FACING, LIGHT);
    }

    private static VoxelShape collision(BlockState state) {
        return switch (state.getValue(KIND)) {
            case FLOOR, BAY -> FULL;
            case ROOF -> ROOF;
            case STEP_HIGH -> STEP_HIGH;
            case STEP_LOW -> STEP_LOW;
            case WALL, PANEL, BEACON_PANEL -> switch (state.getValue(FACING)) {
                case SOUTH -> WALL_SOUTH;
                case WEST -> WALL_WEST;
                case EAST -> WALL_EAST;
                default -> WALL_NORTH;
            };
            case ROOM, LAMP -> Shapes.empty();
        };
    }

    @Override
    protected @NotNull VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return collision(state);
    }

    /**
     * Only the bays and the support panel can be targeted (to use them); the rest is drawn by the vehicle and clicks pass
     * through to the real blocks (the bunks' beds).
     */
    @Override
    protected @NotNull VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(KIND)) {
            case BAY -> FULL;
            case PANEL, BEACON_PANEL -> collision(state);
            default -> Shapes.empty();
        };
    }

    @Override
    protected @NotNull VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected @NotNull VoxelShape getOcclusionShape(BlockState state) {
        return Shapes.empty();
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return state.getValue(KIND) != Kind.ROOF;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return collision(state).isEmpty();
    }

    /** Room cells hold their place: nothing can be built into a deployed vehicle's room. Outdoor lamps give way. */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return state.getValue(KIND) == Kind.LAMP;
    }

    @Override
    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return state.getValue(KIND) == Kind.LAMP;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return SupportShell.use(level, pos, state, player, InteractionHand.MAIN_HAND);
    }

    @Override
    protected @NotNull InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = SupportShell.use(level, pos, state, player, hand);
        return result == InteractionResult.PASS ? InteractionResult.TRY_WITH_EMPTY_HAND : result;
    }

    /** A shell whose vehicle is gone (killed, packed into a container, lost) clears itself. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        SupportShell.removeIfOrphaned(level, pos);
    }
}
