package ttv.migami.jeg.vehicle.menu;

import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import ttv.migami.jeg.init.ModMenuTypes;
import ttv.migami.jeg.vehicle.support.SupportModules;

/**
 * The FSV-6's Fit-out screen: the six wall bays, two bunks, two outside lockers, the roof mount and the room's lamp
 * (the beacon's power rack is at the beacon terminal). A slot takes only what fits there; a storage module, furnace or
 * brewing stand cannot be taken out while it still holds anything.
 */
@org.anti_ad.mc.ipn.api.IPNIgnore
public final class SupportFitMenu extends AbstractContainerMenu {
    /**
     * Slot positions in the screen, in fitting-slot order: a top-down plan of the vehicle (cab at the top). The bunks at
     * the front and three bays down each side (the vehicle's left on the left), the lamp hook mid-aisle, the roof beacon
     * mount over the rear, and the outside lockers beyond the walls.
     */
    public static final int[][] POSITIONS = new int[SupportModules.FIT_SLOTS][];
    public static final int LEFT_X = 44, RIGHT_X = 116, AISLE_X = 80, BUNK_Y = 40, BAY_Y = 62, BAY_STEP = 18;
    static {
        for (int i = 0; i < 3; i++) POSITIONS[i] = new int[]{LEFT_X, BAY_Y + i * BAY_STEP};       // left bays (front to rear)
        for (int i = 0; i < 3; i++) POSITIONS[3 + i] = new int[]{RIGHT_X, BAY_Y + i * BAY_STEP};  // right bays
        POSITIONS[SupportModules.BUNK_LEFT] = new int[]{LEFT_X, BUNK_Y};
        POSITIONS[SupportModules.BUNK_RIGHT] = new int[]{RIGHT_X, BUNK_Y};
        POSITIONS[SupportModules.LOCKER_LEFT] = new int[]{12, BAY_Y};
        POSITIONS[SupportModules.LOCKER_RIGHT] = new int[]{148, BAY_Y};
        POSITIONS[SupportModules.ROOF] = new int[]{AISLE_X, BAY_Y + 2 * BAY_STEP};
        POSITIONS[SupportModules.LAMP] = new int[]{AISLE_X, BAY_Y + 8};
    }
    public static final int PLAYER_Y = 130, HOTBAR_Y = 188, HEIGHT = 212;

    private final Container fittings;
    @Nullable private final SupportModules modules;
    /** The vehicle's entity id on the client (for the screen's buttons), or -1. */
    private int vehicleId = -1;

    /** Client side: an empty copy the server fills. */
    public SupportFitMenu(int containerId, Inventory inventory, Integer vehicleId) {
        this(containerId, inventory, new SimpleContainer(SupportModules.FITTINGS), null);
        this.vehicleId = vehicleId;
    }

    public int vehicleId() {
        return this.vehicleId;
    }

    public SupportFitMenu(int containerId, Inventory inventory, Container fittings, @Nullable SupportModules modules) {
        super(ModMenuTypes.SUPPORT_FIT_MENU.get(), containerId);
        this.fittings = fittings;
        this.modules = modules;
        for (int slot = 0; slot < SupportModules.FIT_SLOTS; slot++) {
            this.addSlot(new FitSlot(fittings, slot, POSITIONS[slot][0], POSITIONS[slot][1]));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, PLAYER_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, HOTBAR_Y));
        }
    }

    private final class FitSlot extends Slot {
        FitSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override public boolean mayPlace(@NotNull ItemStack stack) {
            SupportModules modules = SupportFitMenu.this.modules;
            return SupportModules.accepts(this.getContainerSlot(), stack) && (modules == null || !modules.locked(this.getContainerSlot()));
        }

        @Override public int getMaxStackSize() {
            return SupportModules.maxStack(this.getContainerSlot());
        }

        @Override public int getMaxStackSize(@NotNull ItemStack stack) {
            return Math.min(stack.getMaxStackSize(), this.getMaxStackSize());
        }

        @Override public void setByPlayer(@NotNull ItemStack stack, @NotNull ItemStack previous) {
            super.setByPlayer(stack, previous);
            SupportModules modules = SupportFitMenu.this.modules;
            if (modules == null) return;
            if (!stack.isEmpty() && !ItemStack.isSameItem(stack, previous)) modules.fittingSound(this.getContainerSlot(), stack, true);
            else if (stack.isEmpty() && !previous.isEmpty()) modules.fittingSound(this.getContainerSlot(), previous, false);
        }

        @Override public void onTake(@NotNull Player player, @NotNull ItemStack stack) {
            super.onTake(player, stack);
            SupportModules modules = SupportFitMenu.this.modules;
            if (modules != null && !stack.isEmpty() && !this.hasItem()) modules.fittingSound(this.getContainerSlot(), stack, false);
        }

        @Override public boolean mayPickup(@NotNull Player player) {
            return SupportFitMenu.this.modules == null || !SupportFitMenu.this.modules.holdsItems(this.getContainerSlot());
        }
    }

    @Override public boolean stillValid(@NotNull Player player) {
        return this.fittings.stillValid(player);
    }

    @Override public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int fittings = SupportModules.FIT_SLOTS;
        if (index < fittings) {
            if (!this.moveItemStackTo(stack, fittings, this.slots.size(), true)) return ItemStack.EMPTY;
        } else {
            boolean moved = false;
            for (int target = 0; target < fittings && !stack.isEmpty(); target++) {
                Slot fit = this.slots.get(target);
                if (fit.mayPlace(stack) && (!fit.hasItem() || ItemStack.isSameItemSameComponents(fit.getItem(), stack))) {
                    moved |= this.moveItemStackTo(stack, target, target + 1, false);
                }
            }
            if (!moved) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
