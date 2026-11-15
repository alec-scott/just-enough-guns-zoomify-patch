package ttv.migami.jeg.vehicle.menu;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.jetbrains.annotations.NotNull;
import ttv.migami.jeg.init.ModMenuTypes;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.support.SupportModules;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * The FSV-6's beacon terminal, on the room's front wall: everything the roof beacon needs in one place.
 * <ul>
 *     <li>The power rack: nine slots for raw ore, ingots, gems or their blocks (nine to a block), which set the level
 *     as a beacon's pyramid would (9, 34, 83 and 164 blocks).</li>
 *     <li>The effects: the primary from the tiers the level allows, and at level 4 regeneration or the primary made
 *     stronger. Choosing costs nothing: the rack is the beacon's price.</li>
 * </ul>
 * Data synced to the screen: level, rack blocks, primary, secondary and a status bitfield (beacon fitted, mast up, lit).
 */
@org.anti_ad.mc.ipn.api.IPNIgnore
public final class SupportBeaconMenu extends AbstractContainerMenu {
    public static final int RACK_X = 8, RACK_Y = 92, PLAYER_Y = 124, HOTBAR_Y = 182, HEIGHT = 206, WIDTH = 176;
    public static final int DATA_LEVEL = 0, DATA_BLOCKS = 1, DATA_PRIMARY = 2, DATA_SECONDARY = 3, DATA_FLAGS = 4, DATA_COUNT = 5;
    public static final int FLAG_FITTED = 1, FLAG_MAST_UP = 2, FLAG_LIT = 4;
    /** Button ids: clear, then primary and secondary effects by their encoded id. */
    public static final int BUTTON_CLEAR = 0, BUTTON_PRIMARY = 1000, BUTTON_SECONDARY = 2000;

    private final Container fittings;
    private final ContainerData data;
    @Nullable private final SupportVehicleEntity vehicle;
    private int vehicleId = -1;

    /** Client side: an empty copy the server fills. */
    public SupportBeaconMenu(int containerId, Inventory inventory, Integer vehicleId) {
        this(containerId, inventory, new SimpleContainer(SupportModules.FITTINGS), new SimpleContainerData(DATA_COUNT), null);
        this.vehicleId = vehicleId;
    }

    public SupportBeaconMenu(int containerId, Inventory inventory, SupportVehicleEntity vehicle) {
        this(containerId, inventory, vehicle.modules().fittings, serverData(vehicle), vehicle);
        this.vehicleId = vehicle.getId();
    }

    private SupportBeaconMenu(int containerId, Inventory inventory, Container fittings, ContainerData data, @Nullable SupportVehicleEntity vehicle) {
        super(ModMenuTypes.SUPPORT_BEACON_MENU.get(), containerId);
        this.fittings = fittings;
        this.data = data;
        this.vehicle = vehicle;
        for (int i = 0; i < SupportModules.RACK_SLOTS; i++) {
            this.addSlot(new RackSlot(fittings, SupportModules.RACK_START + i, RACK_X + i * 18, RACK_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                this.addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, PLAYER_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            this.addSlot(new Slot(inventory, column, 8 + column * 18, HOTBAR_Y));
        }
        this.addDataSlots(data);
    }

    private static ContainerData serverData(SupportVehicleEntity vehicle) {
        SupportModules modules = vehicle.modules();
        return new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case DATA_LEVEL -> modules.beaconLevel();
                    case DATA_BLOCKS -> modules.beaconBlocks();
                    case DATA_PRIMARY -> modules.services().primary();
                    case DATA_SECONDARY -> modules.services().secondary();
                    case DATA_FLAGS -> (modules.fitted(SupportModules.ROOF).isEmpty() ? 0 : FLAG_FITTED)
                            | (SupportRig.mast(vehicle.deployProgress()) >= 1 ? FLAG_MAST_UP : 0)
                            | (ttv.migami.jeg.vehicle.support.SupportServices.beaconLit(vehicle) ? FLAG_LIT : 0);
                    default -> 0;
                };
            }

            @Override public void set(int index, int value) {
            }

            @Override public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    public int vehicleId() { return this.vehicleId; }
    public int level() { return this.data.get(DATA_LEVEL); }
    public int rackBlocks() { return this.data.get(DATA_BLOCKS); }
    public int primary() { return this.data.get(DATA_PRIMARY); }
    public int secondary() { return this.data.get(DATA_SECONDARY); }
    public boolean flag(int flag) { return (this.data.get(DATA_FLAGS) & flag) != 0; }

    /** The tier (0..3) an effect belongs to in a beacon, or -1. Tier 3 is the secondary's (regeneration). */
    public static int tierOf(Holder<MobEffect> effect) {
        List<List<Holder<MobEffect>>> tiers = BeaconBlockEntity.BEACON_EFFECTS;
        for (int tier = 0; tier < tiers.size(); tier++) if (tiers.get(tier).contains(effect)) return tier;
        return -1;
    }

    /** Choose an effect: primary if the level allows its tier; secondary at level 4 (regeneration, or the primary again). */
    @Override public boolean clickMenuButton(@NotNull Player player, int id) {
        if (this.vehicle == null) return false;
        SupportModules modules = this.vehicle.modules();
        if (id == BUTTON_CLEAR) {
            modules.setBeaconEffect(0);
            modules.setBeaconSecondary(0);
        } else if (id >= BUTTON_PRIMARY && id < BUTTON_SECONDARY) {
            Holder<MobEffect> effect = BeaconMenu.decodeEffect(id - BUTTON_PRIMARY);
            int tier = effect == null ? -1 : tierOf(effect);
            if (tier < 0 || tier > 2 || tier >= modules.beaconLevel()) return false;
            modules.setBeaconEffect(id - BUTTON_PRIMARY);
            // A secondary that was the old primary made stronger no longer matches: clear it.
            Holder<MobEffect> secondary = BeaconMenu.decodeEffect(modules.services().secondary());
            if (secondary != null && tierOf(secondary) != 3 && !secondary.equals(effect)) modules.setBeaconSecondary(0);
        } else if (id >= BUTTON_SECONDARY) {
            Holder<MobEffect> effect = BeaconMenu.decodeEffect(id - BUTTON_SECONDARY);
            Holder<MobEffect> primary = BeaconMenu.decodeEffect(modules.services().primary());
            if (effect == null || modules.beaconLevel() < 4 || primary == null) return false;
            if (tierOf(effect) != 3 && !effect.equals(primary)) return false;
            modules.setBeaconSecondary(id - BUTTON_SECONDARY);
        } else {
            return false;
        }
        this.vehicle.onFittingsChanged();
        // Choosing an effect sounds as on a placed beacon.
        this.vehicle.playBeaconSound(net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT);
        return true;
    }

    private static final class RackSlot extends Slot {
        RackSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override public boolean mayPlace(@NotNull ItemStack stack) {
            return SupportModules.accepts(this.getContainerSlot(), stack);
        }

        @Override public int getMaxStackSize() {
            return SupportModules.maxStack(this.getContainerSlot());
        }

        @Override public int getMaxStackSize(@NotNull ItemStack stack) {
            return Math.min(stack.getMaxStackSize(), this.getMaxStackSize());
        }
    }

    @Override public boolean stillValid(@NotNull Player player) {
        return this.fittings.stillValid(player);
    }

    @Override public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int rack = SupportModules.RACK_SLOTS;
        if (index < rack) {
            if (!this.moveItemStackTo(stack, rack, this.slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!SupportModules.accepts(SupportModules.RACK_START, stack) || !this.moveItemStackTo(stack, 0, rack, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }
}
