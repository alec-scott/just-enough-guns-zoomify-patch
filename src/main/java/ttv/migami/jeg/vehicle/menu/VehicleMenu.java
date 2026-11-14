package ttv.migami.jeg.vehicle.menu;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.init.ModMenuTypes;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

public final class VehicleMenu extends AbstractContainerMenu {
    /**
     * What the client needs to build the menu: the hold's size and the bay feeding a vehicle's tool
     * (the bulldozer's ripper paving row). The bay starts at -1 when it has none.
     */
    public record OpeningData(int slots, int toolStart, int toolCount) {
        public static final StreamCodec<ByteBuf, OpeningData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpeningData::slots, ByteBufCodecs.VAR_INT, OpeningData::toolStart,
                ByteBufCodecs.VAR_INT, OpeningData::toolCount, OpeningData::new);
    }

    /** A hold slot that feeds a tool: it takes only full solid blocks (what the ripper can lay), so nothing else lands there. */
    public static final class ToolSlot extends Slot {
        public ToolSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return isLayableBlock(stack);
        }
    }

    public static boolean isLayableBlock(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) {
            return false;
        }
        var state = item.getBlock().defaultBlockState();
        return !state.hasBlockEntity() && state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, net.minecraft.core.BlockPos.ZERO);
    }

    public static final int MAX_VEHICLE_SLOT_COUNT = 102;
    private static final int DEFAULT_VEHICLE_SLOT_COUNT = 9;
    private static final int SLOT_SIZE = 18;
    private static final int VEHICLE_SLOT_X = 16;
    private static final int VEHICLE_SLOT_Y = 18;
    private static final int PLAYER_SLOT_Y = 104;
    private static final int HOTBAR_SLOT_Y = 162;
    private static final int TOOL_BAY_GAP = 18;

    private final Container vehicleInventory;
    private final int vehicleSlotCount;
    private final int vehicleRows;
    private final int vehicleColumns;
    private final int playerInventoryY;
    private final int toolStart, toolCount;
    @Nullable
    private final VehicleEntity vehicle;

    public VehicleMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, DEFAULT_VEHICLE_SLOT_COUNT);
    }

    public VehicleMenu(int containerId, Inventory playerInventory, int vehicleSlotCount) {
        this(containerId, playerInventory, new SimpleContainer(vehicleSlotCount), vehicleSlotCount);
    }

    public VehicleMenu(int containerId, Inventory playerInventory, OpeningData data) {
        this(containerId, playerInventory, new SimpleContainer(data.slots()), data.slots(), null, data.toolStart(), data.toolCount());
    }

    public VehicleMenu(int containerId, Inventory playerInventory, Container vehicleInventory) {
        this(containerId, playerInventory, vehicleInventory, Math.min(vehicleInventory.getContainerSize(), MAX_VEHICLE_SLOT_COUNT));
    }

    public VehicleMenu(int containerId, Inventory playerInventory, Container vehicleInventory, int vehicleSlotCount) {
        this(containerId, playerInventory, vehicleInventory, vehicleSlotCount, null);
    }

    public VehicleMenu(int containerId, Inventory playerInventory, Container vehicleInventory, int vehicleSlotCount, @Nullable VehicleEntity vehicle) {
        this(containerId, playerInventory, vehicleInventory, vehicleSlotCount, vehicle, vehicle == null ? -1 : vehicle.toolSlotStart(),
                vehicle == null ? 0 : vehicle.toolSlotCount());
    }

    public VehicleMenu(int containerId, Inventory playerInventory, Container vehicleInventory, int vehicleSlotCount, @Nullable VehicleEntity vehicle,
                       int toolStart, int toolCount) {
        super(ModMenuTypes.VEHICLE_MENU.get(), containerId);
        this.toolStart = toolStart;
        this.toolCount = Math.max(0, toolCount);
        this.vehicleSlotCount = Math.max(0, Math.min(vehicleSlotCount, MAX_VEHICLE_SLOT_COUNT));
        checkContainerSize(vehicleInventory, this.vehicleSlotCount);
        this.vehicleInventory = vehicleInventory;
        this.vehicle = vehicle;
        this.vehicleColumns = columnsFor(this.vehicleSlotCount);
        this.vehicleRows = this.vehicleColumns == 0 ? 0 : (this.vehicleSlotCount + this.vehicleColumns - 1) / this.vehicleColumns;
        this.vehicleInventory.startOpen(playerInventory.player);
        int rowOffset = (this.vehicleRows - 4) * SLOT_SIZE;
        int bayOffset = this.toolCount() > 0 ? TOOL_BAY_GAP : 0;
        int columnOffset = this.playerInventoryXOffset();
        // Leave space for the tool-bay tab and the player's own inventory heading.
        this.playerInventoryY = PLAYER_SLOT_Y + rowOffset + bayOffset;

        for (int slot = 0; slot < this.vehicleSlotCount; slot++) {
            int x = VEHICLE_SLOT_X + slot % this.vehicleColumns * SLOT_SIZE, y = VEHICLE_SLOT_Y + slot / this.vehicleColumns * SLOT_SIZE;
            this.addSlot(this.isToolSlot(slot) ? new ToolSlot(vehicleInventory, slot, x, y) : new Slot(vehicleInventory, slot, x, y));
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, VEHICLE_SLOT_X + columnOffset + col * SLOT_SIZE, this.playerInventoryY + row * SLOT_SIZE));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, VEHICLE_SLOT_X + columnOffset + col * SLOT_SIZE, HOTBAR_SLOT_Y + rowOffset + bayOffset));
        }
    }

    /** First slot of the bay feeding a tool (the bulldozer's ripper), or -1. */
    public int toolStart() {
        return this.toolStart;
    }

    public int toolCount() {
        return this.toolStart < 0 ? 0 : this.toolCount;
    }

    public boolean isToolSlot(int slot) {
        return this.toolStart >= 0 && slot >= this.toolStart && slot < this.toolStart + this.toolCount;
    }

    public int vehicleRows() {
        return this.vehicleRows;
    }

    public int vehicleColumns() {
        return this.vehicleColumns;
    }

    public int playerInventoryXOffset() {
        return Math.max(0, (this.vehicleColumns - 9) / 2 * SLOT_SIZE);
    }

    public int playerInventoryY() {
        return this.playerInventoryY;
    }

    public int screenHeight() {
        return 222 + (this.toolCount() > 0 ? TOOL_BAY_GAP : 0);
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            int playerInventoryStart = this.vehicleSlotCount;
            int hotbarEnd = playerInventoryStart + 36;
            if (index < this.vehicleSlotCount) {
                if (!this.moveItemStackTo(stack, playerInventoryStart, hotbarEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, this.vehicleSlotCount, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return copy;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        if (this.vehicle != null) {
            return this.vehicle.isAlive() && (player.getVehicle() == this.vehicle || player.distanceToSqr(this.vehicle) <= 64.0D);
        }
        return this.vehicleInventory.stillValid(player);
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        this.vehicleInventory.stopOpen(player);
    }

    private static int columnsFor(int slotCount) {
        if (slotCount <= 0) {
            return 0;
        }
        if (slotCount > 78) {
            return 17;
        }
        if (slotCount > 54) {
            return 13;
        }
        return 9;
    }

}
