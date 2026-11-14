package ttv.migami.jeg.vehicle.terrain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModSounds;
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;
import ttv.migami.jeg.vehicle.util.BulldozerRig;

/**
 * The blade's earthwork, run on the server only, before the machine moves each tick.
 *
 * <p>Any block of hardness up to {@link #MAX_HARDNESS} that the cutting edge meets is cleared, together with the
 * contiguous bank stacked on it up to {@link #BANK_HEIGHT} blocks above the edge (material above the cut falls into
 * the blade). Logs are felled whole: with FallingTree installed the break is presented to it as an axe stroke, so the
 * pack's own tree mod brings the tree down; otherwise the dozer fells the connected trunk itself. The blade's work
 * per tick is limited, and what it cannot clear yet holds the machine back, so harder ground slows it.
 */
public final class BulldozerTerrainWork {
    public static final int IDLE = 0, WORKING = 1, RAISED = 2, PROTECTED = 3, SPOIL = 4, NO_POWER = 5, SOLID = 6, TREE = 7, BUSY = 8;
    public static final float MAX_HARDNESS = 10F;
    public static final int BANK_HEIGHT = 10;
    /** The machine's height in blocks (canopy roof): this much above the tracks is always cleared. */
    public static final double MACHINE_HEIGHT = 3.0;
    /** Largest air pocket the bank is followed across into natural ground above it. */
    public static final int MAX_GAP = 2;
    private static final int NO_GROUND = Integer.MIN_VALUE;
    /** Height of the blade face above its cutting edge, in blocks: all of it bites, attached bank or not. */
    private static final double FACE_HEIGHT = 1.6;
    /** Hardness the blade can clear per tick (dirt 0.5, stone 1.5, deepslate 3, ores 3, iron block 5). */
    private static final float CAPACITY = 10F;
    private static final int BLOCKS_PER_TICK = 40, LEVEL_BLOCKS_PER_TICK = 120, EFFECTS_PER_TICK = 3, TREE_LOGS = 256;
    private static final TagKey<Block> DENIED = TagKey.create(Registries.BLOCK, Reference.id("bulldozer_unbreakable"));
    private static final TagKey<Item> SPOIL_ITEMS = TagKey.create(Registries.ITEM, Reference.id("bulldozer_spoil"));
    private static final TagKey<Block> NATURAL_GROUND = TagKey.create(Registries.BLOCK, Reference.id("bulldozer_natural_ground"));
    private static final Map<ServerLevel, Budget> BUDGETS = new WeakHashMap<>();
    private static final class Budget { long tick; int count; }

    private float credit;
    /** Blocks this blade removed recently (position to game time), so its own holes do not read as caves. */
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap cuts = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    private static final int CUT_MEMORY = 100;
    private BlockPos cracking;
    private float load;
    private AABB collectArea;
    private int collectTicks;

    /** Fraction of the blade's capacity used this tick, 0 to 1. */
    public float load() { return this.load; }

    public void clear(BulldozerEntity dozer) {
        if (this.cracking != null && dozer.level() instanceof ServerLevel level) level.destroyBlockProgress(dozer.getId(), this.cracking, -1);
        this.cracking = null;
        this.credit = 0;
        this.load = 0;
    }

    /**
     * @param pushing the driver is driving forward into the work
     * @return the work status shown to the driver; {@link #SOLID}, {@link #PROTECTED} and {@link #BUSY} hold the machine
     */
    public int tick(BulldozerEntity dozer, boolean pushing) {
        if (!(dozer.level() instanceof ServerLevel level)) return IDLE;
        this.collectFelledLoot(dozer, level);
        if (!(dozer.getControllingPassenger() instanceof ServerPlayer player) || player.isSpectator()) { clear(dozer); return IDLE; }
        if (!dozer.powered()) { clear(dozer); return NO_POWER; }
        float lift = dozer.bladeLift();
        if (lift > BulldozerRig.TRAVEL_LIFT) {
            clear(dozer);
            return pushing && this.bladeObstructed(dozer, level, lift) ? SOLID : RAISED;
        }
        if (!pushing) { clear(dozer); return IDLE; }

        Budget budget = BUDGETS.computeIfAbsent(level, ignored -> new Budget());
        if (budget.tick != level.getGameTime()) { budget.tick = level.getGameTime(); budget.count = 0; }
        this.credit = Math.min(CAPACITY * 2, this.credit + CAPACITY);
        float available = this.credit;
        int status = IDLE, broken = 0, effects = 0;
        boolean spoil = false;
        Vec3 edge = BulldozerRig.cuttingCenter(dozer, lift);
        Set<BlockPos> felled = new HashSet<>();
        for (BlockPos pos : this.targets(dozer, level, lift, edge.y)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || felled.contains(pos)) continue;
            if (!cuttable(state, level, pos)) {
                // Too hard, a block entity or a server-protected block: the blade stops against it.
                if (!state.getCollisionShape(level, pos).isEmpty()) status = Math.max(status, SOLID);
                continue;
            }
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)
                    || player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
                status = Math.max(status, PROTECTED);
                continue;
            }
            float cost = cost(state, level, pos);
            if (cost > this.credit || broken >= BLOCKS_PER_TICK || budget.count >= LEVEL_BLOCKS_PER_TICK) {
                // Out of blade power this tick: crack the next block and hold the machine against it.
                if (this.cracking != null && !this.cracking.equals(pos)) level.destroyBlockProgress(dozer.getId(), this.cracking, -1);
                this.cracking = pos.immutable();
                level.destroyBlockProgress(dozer.getId(), pos, Mth.clamp((int) (this.credit / cost * 9), 0, 9));
                status = Math.max(status, BUSY);
                break;
            }
            int energy = 20 + Math.round(state.getDestroySpeed(level, pos) * 30);
            if (dozer.vehicleEnergy() < energy) { status = NO_POWER; break; }
            if (state.is(BlockTags.LOGS)) {
                int result = this.fell(dozer, level, player, pos, state, felled);
                if (result == PROTECTED) { status = Math.max(status, PROTECTED); continue; }
                this.credit -= cost;
                broken++; budget.count++;
                dozer.consumeEnergy(energy);
                status = Math.max(status, TREE);
                continue;
            }
            if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, null)) {
                status = Math.max(status, PROTECTED);
                continue;
            }
            if (!level.getBlockState(pos).equals(state)) continue;
            List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, tool(state));
            this.remove(level, player, pos, state, effects++ < EFFECTS_PER_TICK);
            spoil |= this.store(dozer, level, pos, drops);
            dozer.consumeEnergy(energy);
            PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, null);
            this.credit -= cost;
            broken++; budget.count++;
            if (broken <= 4) this.spray(level, dozer, pos, state);
            status = Math.max(status, WORKING);
        }
        if (status != BUSY && this.cracking != null) {
            level.destroyBlockProgress(dozer.getId(), this.cracking, -1);
            this.cracking = null;
        }
        // Load is the share of the blade's power spent this tick; an empty blade carries none.
        this.load = status == BUSY ? 1 : Mth.clamp((available - Math.max(0, this.credit)) / CAPACITY, 0, 1);
        if (status == IDLE || status == SOLID || status == PROTECTED) {
            this.credit = 0;
            this.load = 0;
        }
        if (spoil && status == WORKING) status = SPOIL;
        return status;
    }

    /** True when something solid sits in the raised blade's path. */
    private boolean bladeObstructed(BulldozerEntity dozer, ServerLevel level, float lift) {
        AABB bite = BulldozerRig.biteBounds(dozer, lift, FACE_HEIGHT);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bite.minX, bite.minY + .02, bite.minZ),
                BlockPos.containing(bite.maxX, bite.maxY, bite.maxZ))) {
            if (!level.hasChunkAt(pos)) return true;
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            for (AABB box : shape.toAabbs()) {
                AABB placed = box.move(pos);
                if (placed.maxY > bite.minY + .02 && placed.minY < bite.maxY && BulldozerRig.inBite(dozer, lift, placed)) return true;
            }
        }
        return false;
    }

    /**
     * Blocks in the bite, nearest first: everything the blade face touches, then each column's bank upward until air
     * or something the blade cannot move. Columns never extend into unloaded chunks.
     */
    private List<BlockPos> targets(BulldozerEntity dozer, ServerLevel level, float lift, double edgeY) {
        AABB bite = BulldozerRig.biteBounds(dozer, lift, FACE_HEIGHT);
        Vec3 f = BulldozerRig.forward(dozer.getYRot());
        int bottom = Mth.floor(edgeY + .02), bankTop = Mth.floor(edgeY + BANK_HEIGHT);
        // Always clear the blade face and the machine's own height, gaps or not, so the cut never leaves anything
        // that would stop the machine following it.
        int faceTop = Math.max(Mth.floor(edgeY + FACE_HEIGHT), Mth.floor(dozer.getY() + MACHINE_HEIGHT - 1.0E-3));
        // Below grade the blade cuts to one level and holds it, however far the machine has settled into the cut.
        if (dozer.cutFloor() != BulldozerEntity.NO_FLOOR) bottom = Math.max(bottom, dozer.cutFloor());
        List<long[]> columns = new ArrayList<>();
        for (int x = Mth.floor(bite.minX); x <= Mth.floor(bite.maxX); x++) {
            for (int z = Mth.floor(bite.minZ); z <= Mth.floor(bite.maxZ); z++) {
                if (!BulldozerRig.inBite(dozer, lift, new AABB(x + .02, edgeY, z + .02, x + .98, edgeY + 1, z + .98))) continue;
                double along = (x + .5 - dozer.getX()) * f.x + (z + .5 - dozer.getZ()) * f.z;
                columns.add(new long[]{x, z, Double.doubleToLongBits(along)});
            }
        }
        columns.sort((a, b) -> Double.compare(Double.longBitsToDouble(a[2]), Double.longBitsToDouble(b[2])));
        List<BlockPos> result = new ArrayList<>();
        for (long[] column : columns) {
            int x = (int) column[0], z = (int) column[1];
            if (!level.hasChunkAt(new BlockPos(x, bottom, z))) continue;
            // Whether the block under y belongs to the bank: there now, or cut by this blade a moment ago (a column cut
            // over several ticks must come out the same as one cut at once).
            boolean below = false;
            int y = bottom;
            while (y <= bankTop) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(pos);
                boolean air = state.isAir(), cut = air && this.recentlyCut(pos, level);
                if (y > faceTop) {
                    if (air && !cut) {
                        // Above the machine, carry on through a small air pocket (a cave) only into natural ground resting
                        // on the bank: an overhang of rock comes down, a tree canopy or a building over a gap does not.
                        int resume = below ? this.naturalGroundAbove(level, x, y, z, bankTop) : NO_GROUND;
                        if (resume == NO_GROUND) break;
                        y = resume;
                        continue;
                    }
                    if (!air && (!below || !cuttable(state, level, pos))) break;
                }
                y++;
                if (air) {
                    below = cut;
                    continue;
                }
                below = true;
                if (pos.getY() == bottom && pos.getY() < edgeY && !reachesAbove(state, level, pos, edgeY)) continue;
                result.add(pos);
                if (pos.getY() >= faceTop && state.is(BlockTags.LOGS)) break;
            }
        }
        return result;
    }

    /**
     * The first block above an air pocket of at most {@link #MAX_GAP} blocks, starting at {@code y}, when it is natural
     * ground (#jeg:bulldozer_natural_ground) the blade may cut; otherwise {@link #NO_GROUND}. (Not -1: world Y goes
     * down to -64, so a negative number is a real height.)
     */
    private int naturalGroundAbove(ServerLevel level, int x, int y, int z, int bankTop) {
        for (int up = y; up <= Math.min(bankTop, y + MAX_GAP); up++) {
            BlockPos pos = new BlockPos(x, up, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                if (this.recentlyCut(pos, level)) return up;
                continue;
            }
            return up > y && up - y <= MAX_GAP && state.is(NATURAL_GROUND) && cuttable(state, level, pos) ? up : NO_GROUND;
        }
        return NO_GROUND;
    }

    /** Whether a block in the edge's layer stands above the cutting edge (a slab below the edge is left alone). */
    private static boolean reachesAbove(BlockState state, ServerLevel level, BlockPos pos, double edgeY) {
        var shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) shape = state.getShape(level, pos);
        return shape.isEmpty() || pos.getY() + shape.max(net.minecraft.core.Direction.Axis.Y) > edgeY + .02;
    }

    public static boolean cuttable(BlockState state, ServerLevel level, BlockPos pos) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.hasBlockEntity() || state.is(DENIED)) return false;
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0 && hardness <= MAX_HARDNESS;
    }

    private static float cost(BlockState state, ServerLevel level, BlockPos pos) {
        float hardness = state.getDestroySpeed(level, pos);
        return Math.max(.05F, hardness * (state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? 1.4F : 1F));
    }

    private static ItemStack tool(BlockState state) {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) return new ItemStack(Items.IRON_PICKAXE);
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) return new ItemStack(Items.IRON_AXE);
        if (state.is(BlockTags.MINEABLE_WITH_HOE)) return new ItemStack(Items.IRON_HOE);
        return new ItemStack(Items.IRON_SHOVEL);
    }

    private boolean recentlyCut(BlockPos pos, ServerLevel level) {
        long at = this.cuts.getOrDefault(pos.asLong(), Long.MIN_VALUE);
        return at != Long.MIN_VALUE && level.getGameTime() - at <= CUT_MEMORY;
    }

    /** Removes a block as a player break would, with the vanilla break particles and sound for the first few. */
    private void remove(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, boolean effects) {
        if (this.cuts.size() > 4096) this.cuts.long2LongEntrySet().removeIf(e -> level.getGameTime() - e.getLongValue() > CUT_MEMORY);
        this.cuts.put(pos.asLong(), level.getGameTime());
        if (effects) {
            level.destroyBlock(pos, false, player);
        } else {
            level.setBlock(pos, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
            level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(player, state));
        }
    }

    /** Soil thrown up and rolling off the blade face. */
    private void spray(ServerLevel level, BulldozerEntity dozer, BlockPos pos, BlockState state) {
        Vec3 f = BulldozerRig.forward(dozer.getYRot());
        Vec3 at = Vec3.atCenterOf(pos).subtract(f.scale(.4));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y + .2, at.z, 10, .45, .3, .45, .12);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 1, .4, .2, .4, .01);
    }

    /**
     * Fells the tree a log belongs to. FallingTree checks the player's main-hand tool and sneaking in both of
     * Fabric's break callbacks, so the operator briefly holds an axe and is not sneaking for exactly those calls.
     */
    private int fell(BulldozerEntity dozer, ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, Set<BlockPos> felled) {
        ItemStack held = player.getMainHandItem();
        boolean sneaking = player.isShiftKeyDown();
        ItemStack axe = new ItemStack(Items.NETHERITE_AXE);
        boolean treeMod = FabricLoader.getInstance().isModLoaded("fallingtree");
        int logsBefore = countLogs(level, pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, axe);
        player.setShiftKeyDown(false);
        try {
            if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, state, null)) return PROTECTED;
            if (!level.getBlockState(pos).equals(state)) return TREE;
            List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, axe);
            this.remove(level, player, pos, state, true);
            this.store(dozer, level, pos, drops);
            felled.add(pos);
            PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, pos, state, null);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, held);
            player.setShiftKeyDown(sneaking);
        }
        // Without the tree mod, or when it declined (a tree beyond its size limit), fell the trunk here.
        if (logsBefore > 1 && (!treeMod || countLogs(level, pos) >= logsBefore - 1)) this.fellConnected(dozer, level, player, pos, state, felled);
        int logsAfter = countLogs(level, pos);
        if (logsBefore - logsAfter < 3) return TREE;
        level.playSound(null, pos, ModSounds.ALL.get(Reference.id("vehicle.bulldozer.tree_fall")).get(), SoundSource.BLOCKS, 1.6F,
                .92F + level.getRandom().nextFloat() * .14F);
        this.collectArea = new AABB(pos).inflate(9, 0, 9).expandTowards(0, 34, 0).expandTowards(0, -2, 0);
        this.collectTicks = 60;
        return TREE;
    }

    private static int countLogs(ServerLevel level, BlockPos origin) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, -1, -4), origin.offset(4, 24, 4))) {
            if (level.getBlockState(pos).is(BlockTags.LOGS) && ++count > TREE_LOGS) break;
        }
        return count;
    }

    /** Breadth-first fall of the connected trunk and branches above the cut, with their natural leaves. */
    private void fellConnected(BulldozerEntity dozer, ServerLevel level, ServerPlayer player, BlockPos origin, BlockState cut, Set<BlockPos> felled) {
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> logs = new HashSet<>();
        open.add(origin);
        while (!open.isEmpty() && logs.size() < TREE_LOGS) {
            BlockPos at = open.poll();
            for (BlockPos next : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 1, 1))) {
                if (next.getY() < origin.getY() || logs.contains(next) || !level.hasChunkAt(next)) continue;
                BlockState state = level.getBlockState(next);
                if (!state.is(BlockTags.LOGS)) continue;
                BlockPos fixed = next.immutable();
                logs.add(fixed);
                open.add(fixed);
            }
        }
        Set<BlockPos> leaves = new HashSet<>();
        for (BlockPos log : logs) {
            for (BlockPos near : BlockPos.betweenClosed(log.offset(-3, -1, -3), log.offset(3, 3, 3))) {
                BlockState leaf = level.getBlockState(near);
                if (leaf.getBlock() instanceof LeavesBlock && !leaf.getValue(LeavesBlock.PERSISTENT)) leaves.add(near.immutable());
            }
        }
        // Only a living tree falls whole. A log cabin or a placed log pile has no natural leaves: the blade just cuts
        // the logs in its path like any other block.
        if (leaves.size() < 4) return;
        int effects = 0;
        for (BlockPos log : logs) {
            BlockState state = level.getBlockState(log);
            if (!cuttable(state, level, log) || !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, log, state, null)) continue;
            List<ItemStack> drops = Block.getDrops(state, level, log, null, player, new ItemStack(Items.NETHERITE_AXE));
            this.remove(level, player, log, state, effects++ < 6);
            this.store(dozer, level, log, drops);
            felled.add(log);
            PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level, player, log, state, null);
        }
        for (BlockPos leaf : leaves) {
            BlockState state = level.getBlockState(leaf);
            if (!(state.getBlock() instanceof LeavesBlock) || !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, leaf, state, null)) continue;
            List<ItemStack> drops = Block.getDrops(state, level, leaf, null, player, ItemStack.EMPTY);
            this.remove(level, player, leaf, state, effects++ < 10);
            this.store(dozer, level, leaf, drops);
        }
    }

    /** For a few seconds after a felling, loot the tree mod drops around the stump goes into the hold. */
    private void collectFelledLoot(BulldozerEntity dozer, ServerLevel level) {
        if (this.collectTicks <= 0 || this.collectArea == null) return;
        this.collectTicks--;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, this.collectArea, e -> e.isAlive() && e.getAge() < 200)) {
            ItemStack left = insert(dozer, item.getItem());
            if (left.isEmpty()) item.discard();
            else item.setItem(left);
        }
    }

    /** Puts drops in the hold. Common spoil that will not fit is pushed aside (discarded); anything else is dropped. */
    private boolean store(BulldozerEntity dozer, ServerLevel level, BlockPos pos, List<ItemStack> drops) {
        boolean spoil = false;
        for (ItemStack drop : drops) {
            ItemStack left = insert(dozer, drop);
            if (left.isEmpty()) continue;
            if (left.is(SPOIL_ITEMS)) spoil = true;
            else Block.popResource(level, pos, left);
        }
        return spoil;
    }

    /** Slots in the ripper's paving bay: the hold's last row. Spoil never goes into it. */
    public static final int PAVING_SLOTS = 9;

    /** First slot of the paving bay. */
    public static int pavingStart(BulldozerEntity dozer) {
        return dozer.vehicleContainerSlots() - PAVING_SLOTS;
    }

    public static boolean isPavingSlot(BulldozerEntity dozer, int slot) {
        return slot >= pavingStart(dozer) && slot < dozer.vehicleContainerSlots();
    }

    /** The bay slot the ripper draws from now: the first holding a block it can lay, or -1. */
    public static int pavingSource(BulldozerEntity dozer) {
        for (int i = pavingStart(dozer); i < dozer.vehicleContainerSlots(); i++) {
            ItemStack stack = dozer.vehicleInventory().getItem(i);
            if (ttv.migami.jeg.vehicle.menu.VehicleMenu.isLayableBlock(stack)
                    && !((net.minecraft.world.item.BlockItem) stack.getItem()).getBlock().defaultBlockState().is(DENIED)) {
                return i;
            }
        }
        return -1;
    }

    /** The block the ripper lays now, or null when the bay holds nothing it can lay. */
    public static BlockState pavingBlock(BulldozerEntity dozer) {
        int source = pavingSource(dozer);
        if (source < 0) return null;
        return ((net.minecraft.world.item.BlockItem) dozer.vehicleInventory().getItem(source).getItem()).getBlock().defaultBlockState();
    }

    /** How many of the block being laid now are left in the whole bay. */
    public static int pavingCount(BulldozerEntity dozer) {
        int source = pavingSource(dozer);
        if (source < 0) return 0;
        ItemStack current = dozer.vehicleInventory().getItem(source);
        int count = 0;
        for (int i = pavingStart(dozer); i < dozer.vehicleContainerSlots(); i++) {
            ItemStack stack = dozer.vehicleInventory().getItem(i);
            if (ItemStack.isSameItem(stack, current)) count += stack.getCount();
        }
        return count;
    }

    /**
     * The lowered ripper lays a surface behind the machine: across the lane under its shanks, the same columns the
     * blade clears (5 blocks off the grid), the ground the tracks run on becomes the paving block. Ground it replaces goes into the hold (it must be clearable ground: the
     * blade's hardness limit, no block entities, not protected); holes are filled. One block per paving item.
     */
    public int pave(BulldozerEntity dozer) {
        if (!(dozer.level() instanceof ServerLevel level) || !(dozer.getControllingPassenger() instanceof ServerPlayer player)) return 0;
        BlockState paving = pavingBlock(dozer);
        if (paving == null) return PAVE_NONE;
        Vec3 f = BulldozerRig.forward(dozer.getYRot());
        Vec3 side = new Vec3(f.z, 0, -f.x);
        Vec3 line = BulldozerRig.world(dozer, 0, 0, BulldozerRig.RIPPER_SHANK_Z);
        int groundY = Mth.floor(dozer.getY() + 1.0E-3) - 1;
        var hold = dozer.vehicleInventory();
        int laid = 0, effects = 0;
        // Samples spread evenly across the band the blade bites (its edge less a sliver, as in BulldozerRig.inBite), never
        // more than a block apart: every column the blade clears is laid, 5 of them off the block grid, 4 on it.
        double across = BulldozerRig.PAVE_HALF_WIDTH - .05;
        int lanes = Mth.ceil(2 * across) + 1;
        for (int lane = 0; lane < lanes; lane++) {
            double s = -across + lane * (2 * across / (lanes - 1));
            for (double back = 0; back <= .5; back += .5) {
                Vec3 at = line.add(side.scale(s)).subtract(f.scale(back));
                BlockPos pos = BlockPos.containing(at.x, groundY + .5, at.z);
                if (!level.hasChunkAt(pos)) continue;
                BlockState ground = level.getBlockState(pos);
                if (ground.equals(paving)) continue;
                int source = pavingSource(dozer);
                if (source < 0) return laid == 0 ? PAVE_NONE : laid;
                BlockState next = pavingBlock(dozer);
                if (!next.equals(paving)) paving = next;
                if (ground.equals(paving)) continue;
                ItemStack material = hold.getItem(source);
                boolean fill = ground.isAir() || ground.canBeReplaced();
                if (!fill && !cuttable(ground, level, pos)) continue;
                if (!level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)
                        || player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE
                        || !PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, player, pos, ground, null)) continue;
                if (!fill) {
                    List<ItemStack> drops = Block.getDrops(ground, level, pos, null, player, tool(ground));
                    this.store(dozer, level, pos, drops);
                }
                level.setBlock(pos, paving, Block.UPDATE_ALL);
                level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(player, paving));
                if (effects++ < 2) {
                    var sound = paving.getSoundType();
                    level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * .8F);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, paving), pos.getX() + .5, pos.getY() + 1.05, pos.getZ() + .5, 6, .4, .05, .4, .05);
                }
                material.shrink(1);
                hold.setChanged();
                dozer.consumeEnergy(20);
                laid++;
            }
        }
        return laid;
    }

    public static final int PAVE_NONE = -1;

    private static ItemStack insert(BulldozerEntity dozer, ItemStack drop) {
        var hold = dozer.vehicleInventory();
        ItemStack left = drop.copy();
        // The shared backing inventory has hidden slots for larger vehicles; only the slots the driver can open count.
        // The paving bay only ever holds what the operator put there.
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < dozer.vehicleContainerSlots() && !left.isEmpty(); i++) {
            if (isPavingSlot(dozer, i)) continue;
            ItemStack slot = hold.getItem(i);
            if (pass == 0 && !slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, left)) {
                int moved = Math.min(left.getCount(), slot.getMaxStackSize() - slot.getCount());
                slot.grow(moved); left.shrink(moved);
            } else if (pass == 1 && slot.isEmpty()) {
                int moved = Math.min(left.getCount(), left.getMaxStackSize());
                hold.setItem(i, left.copyWithCount(moved)); left.shrink(moved);
            }
        }
        hold.setChanged();
        return left;
    }
}
