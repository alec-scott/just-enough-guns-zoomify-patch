package ttv.migami.jeg.vehicle.cabin;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.support.SupportModules;
import ttv.migami.jeg.vehicle.support.SupportWorkstations;
import ttv.migami.jeg.vehicle.support.VehicleFurnace;

/**
 * What the blocks placed in a cabin do (design/chinook/PLAN.md, D6): the FSV-6's working set works aboard, the rest
 * are drawn and collide. Storage (chests, barrels, shulker boxes) holds its 27 slots, the ender chest opens the
 * player's own, the furnace family cooks (kept by the vehicle, {@link VehicleFurnace}), and the crafting table, anvil,
 * stonecutter, smithing table, grindstone, loom, cartography and enchanting tables open their screens. Anything that
 * would need a running block entity, redstone, or more than one cell is refused with a message, so nothing aboard is
 * silently broken.
 */
public final class CabinBlocks {
    public static final int STORAGE_SLOTS = 27;

    private CabinBlocks() {
    }

    /** The FSV-6's kind for a block (shulker boxes count as storage). */
    public static SupportModules.Kind kind(BlockState state) {
        if (state.getBlock() instanceof ShulkerBoxBlock) {
            return SupportModules.Kind.STORAGE;
        }
        return SupportModules.kindOf(new ItemStack(state.getBlock()));
    }

    public static boolean isFurnace(BlockState state) {
        return VehicleFurnace.recipeType(kind(state)) != null;
    }

    /** Whether a click on this block aboard opens it. */
    public static boolean usable(BlockState state) {
        return switch (kind(state)) {
            case STORAGE, ENDER_CHEST, CRAFTING, FURNACE, SMOKER, BLAST_FURNACE, ANVIL, STONECUTTER, SMITHING, GRINDSTONE,
                 LOOM, CARTOGRAPHY, ENCHANTING -> true;
            default -> false;
        };
    }

    /** Why this block can't be placed in a cabin, or null if it can. */
    @Nullable
    public static Component refusal(BlockState state) {
        if (state.hasProperty(BlockStateProperties.BED_PART) || state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return Component.translatable("message.jeg.cabin.too_big", state.getBlock().getName());
        }
        if (usable(state)) {
            return null;
        }
        if (state.isSignalSource()) {
            return Component.translatable("message.jeg.cabin.redstone", state.getBlock().getName());
        }
        if (state.getBlock() instanceof EntityBlock) {
            return Component.translatable("message.jeg.cabin.machine", state.getBlock().getName());
        }
        return null;
    }

    /** Each standing-or-wall item's wall block (a torch's wall torch), found once. */
    private static final Map<net.minecraft.world.item.Item, net.minecraft.world.level.block.Block> WALL_BLOCKS = new HashMap<>();

    @Nullable
    private static net.minecraft.world.level.block.Block wallBlock(net.minecraft.world.item.BlockItem item) {
        if (!(item instanceof net.minecraft.world.item.StandingAndWallBlockItem)) {
            return null;
        }
        return WALL_BLOCKS.computeIfAbsent(item, i -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
                .filter(block -> block.asItem() == i && block != item.getBlock()).findFirst().orElse(null));
    }

    /**
     * The block a held block item puts in a cell aboard, as it would go against the face clicked in the world (the user,
     * 2026-11-26: every placeable block as placed): a torch on a wall is a wall torch, a lantern under the ceiling hangs,
     * a grindstone stands on the floor, an end rod or amethyst points out of the face, and a chest or furnace faces whoever
     * placed it. The same on the placing client (drawn at once) and the server.
     *
     * @param face         the face clicked, in cabin (data) coordinates, pointing out of it
     * @param towardPlacer the horizontal facing that looks at the placer, as drawn (CabinMath#drawnFacing)
     */
    public static BlockState placedState(net.minecraft.world.item.BlockItem item, net.minecraft.core.Direction face,
                                         net.minecraft.core.Direction towardPlacer) {
        net.minecraft.core.Direction drawnFace = CabinMath.drawnFacing(face);
        net.minecraft.world.level.block.Block wall = face.getAxis().isHorizontal() ? wallBlock(item) : null;
        BlockState state = (wall != null ? wall : item.getBlock()).defaultBlockState();
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            net.minecraft.world.level.block.state.properties.AttachFace attach = face == net.minecraft.core.Direction.UP
                    ? net.minecraft.world.level.block.state.properties.AttachFace.FLOOR : face == net.minecraft.core.Direction.DOWN
                    ? net.minecraft.world.level.block.state.properties.AttachFace.CEILING : net.minecraft.world.level.block.state.properties.AttachFace.WALL;
            state = state.setValue(BlockStateProperties.ATTACH_FACE, attach);
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING,
                        attach == net.minecraft.world.level.block.state.properties.AttachFace.WALL ? drawnFace : towardPlacer);
            }
        } else if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, wall != null ? drawnFace : towardPlacer);
        }
        if (state.hasProperty(BlockStateProperties.FACING)) {
            state = state.setValue(BlockStateProperties.FACING, drawnFace);
        }
        if (state.hasProperty(BlockStateProperties.HANGING)) {
            state = state.setValue(BlockStateProperties.HANGING, face == net.minecraft.core.Direction.DOWN);
        }
        return state;
    }

    /**
     * Whether a click with this in hand lights or puts out the block, as in the world: a candle is lit with flint and
     * steel or a fire charge, and put out with an empty hand (a waterlogged one can't be lit).
     */
    public static boolean lightable(BlockState state, ItemStack held) {
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.AbstractCandleBlock) || !state.hasProperty(BlockStateProperties.LIT)) {
            return false;
        }
        if (state.getValue(BlockStateProperties.LIT)) {
            return held.isEmpty();
        }
        return !state.getValueOrElse(BlockStateProperties.WATERLOGGED, false)
                && (held.is(net.minecraft.world.item.Items.FLINT_AND_STEEL) || held.is(net.minecraft.world.item.Items.FIRE_CHARGE));
    }

    /** Light or put out a candle aboard ({@link #lightable}), with the sound and the cost it has in the world. */
    public static void toggleLight(ServerPlayer player, CabinVehicle cabin, BlockPos cell, BlockState state, Vec3 at) {
        ServerLevel level = (ServerLevel) player.level();
        ItemStack held = player.getMainHandItem();
        boolean lit = state.getValue(BlockStateProperties.LIT);
        cabin.cabinGrid().put(cell, state.setValue(BlockStateProperties.LIT, !lit));
        float pitch = level.getRandom().nextFloat() * 0.4F + 0.8F;
        if (lit) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        } else if (held.is(net.minecraft.world.item.Items.FIRE_CHARGE)) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, pitch);
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
        } else {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, pitch);
            if (!player.getAbilities().instabuild) {
                held.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            }
        }
        cabin.cabinGridChanged();
    }

    /** A block was put in a cell (placed, or loaded): give it its storage or furnace. */
    public static void created(CabinVehicle cabin, BlockPos cell, BlockState state) {
        if (isFurnace(state)) {
            VehicleFurnace furnace = new VehicleFurnace(player -> stillUsable(cabin, cell, player), () -> furnaceLit(cabin, cell));
            cabin.cabinFurnaces().put(cell, furnace);
            // Its items sit with the other containers' (saved, spilled, given back with them).
            cabin.cabinChests().put(cell, furnace.items);
        } else if (kind(state) == SupportModules.Kind.STORAGE) {
            cabin.cabinChests().put(cell, new SimpleContainer(STORAGE_SLOTS) {
                @Override
                public boolean stillValid(Player player) {
                    return stillUsable(cabin, cell, player);
                }
            });
        }
    }

    /** A block left its cell: forget its storage and furnace (the caller has their items). */
    public static void removed(CabinVehicle cabin, BlockPos cell) {
        cabin.cabinChests().remove(cell);
        cabin.cabinFurnaces().remove(cell);
    }

    /** Whether a player may keep a block's screen open: still aboard, and the block still there. */
    public static boolean stillUsable(CabinVehicle cabin, BlockPos cell, Player player) {
        Entity vehicle = (Entity) cabin;
        return !vehicle.isRemoved() && cabin.cabinGrid().containsKey(cell) && CabinNetwork.ownerAboard(player, vehicle);
    }

    /** The furnace's block glows while it burns, as a placed one does. */
    private static void furnaceLit(CabinVehicle cabin, BlockPos cell) {
        BlockState state = cabin.cabinGrid().get(cell);
        VehicleFurnace furnace = cabin.cabinFurnaces().get(cell);
        if (state != null && furnace != null && state.hasProperty(BlockStateProperties.LIT)) {
            cabin.cabinGrid().put(cell, state.setValue(BlockStateProperties.LIT, furnace.lit()));
            cabin.cabinGridChanged();
        }
    }

    /** Every server tick, flying or not: the furnaces aboard cook. */
    public static void tick(CabinVehicle cabin, ServerLevel level) {
        for (var entry : cabin.cabinFurnaces().entrySet()) {
            BlockState state = cabin.cabinGrid().get(entry.getKey());
            if (state != null) {
                entry.getValue().tick(level, kind(state));
            }
        }
    }

    /** Open a block aboard for a player; false if it opens nothing. */
    public static boolean open(ServerPlayer player, CabinVehicle cabin, BlockPos cell, BlockState state, Vec3 at) {
        ServerLevel level = (ServerLevel) player.level();
        SupportModules.Kind kind = kind(state);
        Component title = state.getBlock().getName();
        switch (kind) {
            case STORAGE -> {
                SimpleContainer storage = cabin.cabinChests().get(cell);
                if (storage == null) {
                    return false;
                }
                player.openMenu(new SimpleMenuProvider((id, inventory, p) -> lidded(id, inventory, storage, cabin, cell, at), title));
            }
            case ENDER_CHEST -> player.openMenu(new SimpleMenuProvider((id, inventory, p) -> lidded(id, inventory, p.getEnderChestInventory(),
                    cabin, cell, at), Component.translatable("container.enderchest")));
            case FURNACE, SMOKER, BLAST_FURNACE -> {
                VehicleFurnace furnace = cabin.cabinFurnaces().get(cell);
                if (furnace == null) {
                    return false;
                }
                player.openMenu(new SimpleMenuProvider((id, inventory, p) -> furnace.menu(id, inventory, kind), title));
            }
            case CRAFTING -> player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new CraftingMenu(id, inventory, access(level, at)) {
                @Override
                public boolean stillValid(Player who) {
                    return stillUsable(cabin, cell, who);
                }
            }, title));
            default -> {
                return SupportWorkstations.open(kind, access(level, at), player, who -> stillUsable(cabin, cell, who)) != null;
            }
        }
        return true;
    }

    // ---- Lids ----

    /** How many people have each container aboard open (server), by vehicle and cell. */
    private static final Map<Entity, Map<BlockPos, Integer>> OPEN = new WeakHashMap<>();

    /**
     * A chest's screen whose container aboard opens as a placed one does: the lid lifts (a chest's, an ender chest's) or
     * the barrel's face opens for the first to open it, and shuts with its sound as the last closes it. Aboard, the
     * chests stayed shut and silent while their screens were open (the user, 2026-11-25: "no furnace or chest
     * animations").
     */
    private static ChestMenu lidded(int id, Inventory inventory, net.minecraft.world.Container container, CabinVehicle cabin, BlockPos cell, Vec3 at) {
        lid(cabin, cell, at, true);
        return new ChestMenu(MenuType.GENERIC_9x3, id, inventory, container, 3) {
            private boolean closed;

            @Override
            public void removed(Player player) {
                super.removed(player);
                if (!this.closed) {
                    this.closed = true;
                    lid(cabin, cell, at, false);
                }
            }
        };
    }

    private static void lid(CabinVehicle cabin, BlockPos cell, Vec3 at, boolean open) {
        Entity vehicle = (Entity) cabin;
        if (!(vehicle.level() instanceof ServerLevel level)) {
            return;
        }
        Map<BlockPos, Integer> counts = OPEN.computeIfAbsent(vehicle, v -> new HashMap<>());
        int before = counts.getOrDefault(cell, 0);
        int after = Math.max(0, before + (open ? 1 : -1));
        if (after == 0) {
            counts.remove(cell);
        } else {
            counts.put(cell, after);
        }
        if ((before == 0) == (after == 0)) {
            return;
        }
        BlockState state = cabin.cabinGrid().get(cell);
        if (state == null) {
            return;
        }
        sound(level, at, lidSound(state, open));
        if (state.hasProperty(BlockStateProperties.OPEN)) {
            // A barrel: its open face is part of its block.
            cabin.cabinGrid().put(cell, state.setValue(BlockStateProperties.OPEN, open));
            cabin.cabinGridChanged();
        } else {
            CabinNetwork.sendLid(vehicle, cabin, cell, open);
        }
    }

    private static SoundEvent lidSound(BlockState state, boolean open) {
        if (state.getBlock() instanceof BarrelBlock) {
            return open ? SoundEvents.BARREL_OPEN : SoundEvents.BARREL_CLOSE;
        }
        if (state.getBlock() instanceof ShulkerBoxBlock) {
            return open ? SoundEvents.SHULKER_BOX_OPEN : SoundEvents.SHULKER_BOX_CLOSE;
        }
        if (state.getBlock() instanceof net.minecraft.world.level.block.EnderChestBlock) {
            return open ? SoundEvents.ENDER_CHEST_OPEN : SoundEvents.ENDER_CHEST_CLOSE;
        }
        if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock chest) {
            return open ? chest.getOpenChestSound() : chest.getCloseChestSound();
        }
        return open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE;
    }

    private static ContainerLevelAccess access(ServerLevel level, Vec3 at) {
        return ContainerLevelAccess.create(level, BlockPos.containing(at));
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, 0.5F, 1.0F);
    }
}
