package ttv.migami.jeg.vehicle.support;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;

/**
 * What is fitted to a field support vehicle and what each fitted block does. Fittings are data on the vehicle, not
 * blocks in the world, so they keep working while it drives, survive being packed into a vehicle container by the
 * crowbar and are out of reach of mobs and pistons. Which blocks fit where is set by item tags under
 * {@code #jeg:support/}, so a data pack can add more.
 */
public final class SupportModules {
    /**
     * Fitting slots: six wall bays, two bunks, two outside lockers, the roof mount and the room's one lamp (the
     * Fit-out screen's {@link #FIT_SLOTS}), then the beacon's power rack (filled at the beacon terminal).
     */
    public static final int WALL_BAYS = 6, BUNK_LEFT = 6, BUNK_RIGHT = 7, LOCKER_LEFT = 8, LOCKER_RIGHT = 9, ROOF = 10,
            LAMP = 11, FIT_SLOTS = 12, RACK_START = 12, RACK_SLOTS = 9, FITTINGS = RACK_START + RACK_SLOTS;
    /** A storage module holds three rows, as a chest. */
    public static final int STORAGE_SLOTS = 27;
    /** How close a player must be to the vehicle to use its modules (or be aboard). */
    private static final double REACH = 8;

    public enum Kind {
        NONE, STORAGE, ENDER_CHEST, CRAFTING, FURNACE, SMOKER, BLAST_FURNACE, ANVIL, CHARGING, STONECUTTER, SMITHING,
        GRINDSTONE, LOOM, CARTOGRAPHY, ENCHANTING, BREWING, JUKEBOX, BED, BEACON, LIGHT, BEACON_BASE
    }

    public static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, Reference.id("support/" + path));
    }

    public static final TagKey<Item> STORAGE = tag("storage"), ENDER = tag("ender_chest"), CRAFTING = tag("crafting"),
            FURNACE = tag("furnace"), SMOKER = tag("smoker"), BLAST = tag("blast_furnace"), ANVIL = tag("anvil"),
            CHARGING = tag("charging"), STONECUTTER = tag("stonecutter"), SMITHING = tag("smithing"), GRINDSTONE = tag("grindstone"),
            LOOM = tag("loom"), CARTOGRAPHY = tag("cartography"), ENCHANTING = tag("enchanting"), BREWING = tag("brewing"),
            JUKEBOX = tag("jukebox"), BED = tag("bed"), BEACON = tag("beacon"), LIGHT = tag("light"), BEACON_BASE = tag("beacon_base");

    /** What a fitted stack does. */
    public static Kind kindOf(ItemStack stack) {
        if (stack.isEmpty()) return Kind.NONE;
        if (stack.is(STORAGE)) return Kind.STORAGE;
        if (stack.is(ENDER)) return Kind.ENDER_CHEST;
        if (stack.is(CRAFTING)) return Kind.CRAFTING;
        if (stack.is(SMOKER)) return Kind.SMOKER;
        if (stack.is(BLAST)) return Kind.BLAST_FURNACE;
        if (stack.is(FURNACE)) return Kind.FURNACE;
        if (stack.is(ANVIL)) return Kind.ANVIL;
        if (stack.is(CHARGING)) return Kind.CHARGING;
        if (stack.is(STONECUTTER)) return Kind.STONECUTTER;
        if (stack.is(SMITHING)) return Kind.SMITHING;
        if (stack.is(GRINDSTONE)) return Kind.GRINDSTONE;
        if (stack.is(LOOM)) return Kind.LOOM;
        if (stack.is(CARTOGRAPHY)) return Kind.CARTOGRAPHY;
        if (stack.is(ENCHANTING)) return Kind.ENCHANTING;
        if (stack.is(BREWING)) return Kind.BREWING;
        if (stack.is(JUKEBOX)) return Kind.JUKEBOX;
        if (stack.is(BED)) return Kind.BED;
        if (stack.is(BEACON)) return Kind.BEACON;
        if (stack.is(LIGHT)) return Kind.LIGHT;
        if (stack.is(BEACON_BASE)) return Kind.BEACON_BASE;
        return Kind.NONE;
    }

    /** Whether a stack may be fitted in a fitting slot. */
    public static boolean accepts(int slot, ItemStack stack) {
        Kind kind = kindOf(stack);
        if (slot < WALL_BAYS) {
            return switch (kind) {
                case NONE, BED, BEACON, LIGHT, BEACON_BASE -> false;
                default -> true;
            };
        }
        if (slot == BUNK_LEFT || slot == BUNK_RIGHT) return kind == Kind.BED;
        if (slot == LOCKER_LEFT || slot == LOCKER_RIGHT) return kind == Kind.STORAGE;
        if (slot == ROOF) return kind == Kind.BEACON;
        if (slot == LAMP) return kind == Kind.LIGHT;
        return slot >= RACK_START && slot < RACK_START + RACK_SLOTS && kind == Kind.BEACON_BASE;
    }

    /** How many of a stack one fitting slot holds: one block, but the mineral rack takes full stacks. */
    public static int maxStack(int slot) {
        return slot >= RACK_START ? 64 : 1;
    }

    private final SupportVehicleEntity vehicle;
    /** The fitted blocks. */
    public final Fittings fittings;
    /** Contents of storage modules (wall bays and lockers) and furnace state, by fitting slot. */
    private final ModuleContainer[] storage = new ModuleContainer[FITTINGS];
    private final Furnace[] furnaces = new Furnace[WALL_BAYS];
    private final Brewer[] brewers = new Brewer[WALL_BAYS];
    private final SupportServices.State services = new SupportServices.State();

    public SupportModules(SupportVehicleEntity vehicle) {
        this.vehicle = vehicle;
        this.fittings = new Fittings();
        for (int i = 0; i < WALL_BAYS; i++) {
            this.furnaces[i] = new Furnace();
            this.brewers[i] = new Brewer();
        }
    }

    public ItemStack fitted(int slot) {
        return this.fittings.getItem(slot);
    }

    public Kind kind(int slot) {
        return kindOf(this.fitted(slot));
    }

    /** The storage inventory of a slot holding a storage module (created on first use). */
    public ModuleContainer storage(int slot) {
        if (this.storage[slot] == null) this.storage[slot] = new ModuleContainer(STORAGE_SLOTS);
        return this.storage[slot];
    }

    /** True while the module in this slot still holds things (it cannot be taken out until emptied). */
    public boolean holdsItems(int slot) {
        Kind kind = this.kind(slot);
        if (this.locked(slot)) return true;
        if (kind == Kind.STORAGE && this.storage[slot] != null && !this.storage[slot].isEmpty()) return true;
        if (slot < WALL_BAYS && kind == Kind.JUKEBOX && !this.services.discs[slot].isEmpty()) return true;
        if (slot < WALL_BAYS && kind == Kind.BREWING && !this.brewers[slot].items.isEmpty()) return true;
        return slot < WALL_BAYS && (kind == Kind.FURNACE || kind == Kind.SMOKER || kind == Kind.BLAST_FURNACE) && !this.furnaces[slot].items.isEmpty();
    }

    /** Light of the room: the fitted lamp is its only light source; with none fitted the room is dark. */
    public int roomLight() {
        return lampLight(this.fitted(LAMP));
    }

    /** The light a lamp gives (its block's light level), 0 for none. */
    public static int lampLight(ItemStack lamp) {
        return lamp.getItem() instanceof BlockItem block ? block.getBlock().defaultBlockState().getLightEmission() : 0;
    }

    /** A lamp is fitted: the awning and mast floodlights light the camp around the vehicle too. */
    public boolean campLit() {
        return !this.fitted(LAMP).isEmpty();
    }

    /**
     * Beacon pyramid level from the power rack: 9, 34, 83 and 164 blocks for levels 1 to 4, as a beacon's pyramid.
     * Raw ore, ingots and gems count too, nine to a block.
     */
    public int beaconLevel() {
        return levelFor(this.beaconBlocks());
    }

    /** The power rack's contents in whole blocks. */
    public int beaconBlocks() {
        int nines = 0;
        for (int i = 0; i < RACK_SLOTS; i++) {
            ItemStack stack = this.fitted(RACK_START + i);
            if (kindOf(stack) != Kind.BEACON_BASE) continue;
            boolean block = stack.getItem() instanceof BlockItem;
            nines += stack.getCount() * (block ? 9 : 1);
        }
        return nines / 9;
    }

    /** Blocks needed for each beacon level, 1 to 4 (a full pyramid's). */
    public static final int[] LEVEL_BLOCKS = {9, 34, 83, 164};

    public static int levelFor(int blocks) {
        int level = 0;
        while (level < LEVEL_BLOCKS.length && blocks >= LEVEL_BLOCKS[level]) level++;
        return level;
    }

    /** Every server tick, moving or not: furnaces cook. */
    public void tick(ServerLevel level) {
        for (int slot = 0; slot < WALL_BAYS; slot++) {
            Kind kind = this.kind(slot);
            RecipeType<? extends AbstractCookingRecipe> type = switch (kind) {
                case FURNACE -> RecipeType.SMELTING;
                case SMOKER -> RecipeType.SMOKING;
                case BLAST_FURNACE -> RecipeType.BLASTING;
                default -> null;
            };
            if (type != null) this.furnaces[slot].tick(level, type, kind == Kind.FURNACE ? 1 : 2);
            if (kind == Kind.BREWING && this.brewers[slot].tick(level)) {
                // A finished brew chimes, as on a brewing stand.
                net.minecraft.world.phys.Vec3 at = this.soundAt(slot);
                level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.BREWING_STAND_BREW, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    /** Whether this player may use the modules: aboard, or close to the vehicle. */
    public boolean canUse(Player player) {
        return !this.vehicle.isRemoved() && !this.vehicle.isWrecked()
                && (player.getVehicle() == this.vehicle || player.distanceToSqr(this.vehicle) < (REACH + 4) * (REACH + 4));
    }

    /** Open the module in a fitting slot for a player. Returns false if nothing usable is fitted there. */
    public boolean open(ServerPlayer player, int slot, @Nullable BlockPos at) {
        if (slot < 0 || slot >= FITTINGS || !this.canUse(player)) return false;
        Kind kind = this.kind(slot);
        BlockPos pos = at != null ? at : this.vehicle.blockPosition();
        ContainerLevelAccess access = ContainerLevelAccess.create(player.level(), pos);
        Component title = this.fitted(slot).getHoverName();
        SupportModules modules = this;
        switch (kind) {
            // Storage opens and closes with its block's own sounds (a chest's creak, a barrel's, an ender chest's),
            // played where it is fitted.
            case STORAGE -> player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv,
                    new SoundedContainer(this.storage(slot), this.fitted(slot), this.soundAt(slot))), title));
            case ENDER_CHEST -> player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv,
                    new SoundedContainer(p.getEnderChestInventory(), this.fitted(slot), this.soundAt(slot))),
                    Component.translatable("container.enderchest")));
            case CRAFTING -> player.openMenu(new SimpleMenuProvider((id, inv, p) -> new CraftingMenu(id, inv, access) {
                @Override public boolean stillValid(Player who) { return modules.canUse(who); }
            }, title));
            case FURNACE, SMOKER, BLAST_FURNACE -> {
                Furnace furnace = this.furnaces[slot];
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> switch (kind) {
                    case SMOKER -> new net.minecraft.world.inventory.SmokerMenu(id, inv, furnace.items, furnace.data);
                    case BLAST_FURNACE -> new net.minecraft.world.inventory.BlastFurnaceMenu(id, inv, furnace.items, furnace.data);
                    default -> new net.minecraft.world.inventory.FurnaceMenu(id, inv, furnace.items, furnace.data);
                }, title));
            }
            case BREWING -> {
                Brewer brewer = this.brewers[slot];
                player.openMenu(new SimpleMenuProvider((id, inv, p) -> new net.minecraft.world.inventory.BrewingStandMenu(id, inv, brewer.items, brewer.data),
                        Component.translatable("container.brewing")));
            }
            default -> {
                AbstractContainerMenu menu = SupportWorkstations.open(this, kind, access, player);
                if (menu == null && !SupportWorkstations.use(this.vehicle, this, slot, kind, player)) return false;
            }
        }
        return true;
    }

    public SupportServices.State services() {
        return this.services;
    }

    /** Choose the roof beacon's primary effect (vanilla's encoding), as its screen does. */
    public void setBeaconEffect(int primary) {
        this.services.primary = primary;
    }

    /** Choose the roof beacon's secondary effect (vanilla's encoding; level 4 only). */
    public void setBeaconSecondary(int secondary) {
        this.services.secondary = secondary;
    }

    /** Nothing is locked: bunk beds are placed and taken away live while deployed. */
    public boolean locked(int slot) {
        return false;
    }

    public SupportVehicleEntity vehicle() {
        return this.vehicle;
    }

    // ---- Saving.

    public void save(ValueOutput out) {
        ContainerHelper.saveAllItems(out.child("Fittings"), this.fittings.getItems());
        this.services.save(out.child("Services"));
        for (int slot = 0; slot < FITTINGS; slot++) {
            if (this.storage[slot] != null && !this.storage[slot].isEmpty()) {
                ContainerHelper.saveAllItems(out.child("Storage" + slot), this.storage[slot].getItems());
            }
        }
        for (int slot = 0; slot < WALL_BAYS; slot++) {
            Furnace furnace = this.furnaces[slot];
            if (furnace.items.isEmpty() && furnace.litTime == 0) continue;
            ValueOutput child = out.child("Furnace" + slot);
            ContainerHelper.saveAllItems(child.child("Items"), furnace.items.getItems());
            child.putInt("Lit", furnace.litTime);
            child.putInt("LitDuration", furnace.litDuration);
            child.putInt("Cook", furnace.cookingProgress);
            child.putInt("CookTotal", furnace.cookingTotal);
        }
        for (int slot = 0; slot < WALL_BAYS; slot++) {
            Brewer brewer = this.brewers[slot];
            if (brewer.items.isEmpty() && brewer.fuel == 0) continue;
            ValueOutput child = out.child("Brewer" + slot);
            ContainerHelper.saveAllItems(child.child("Items"), brewer.items.getItems());
            child.putInt("Time", brewer.brewTime);
            child.putInt("Fuel", brewer.fuel);
        }
    }

    public void load(ValueInput in) {
        in.child("Fittings").ifPresent(child -> ContainerHelper.loadAllItems(child, this.fittings.getItems()));
        // Saves from before the single lamp had a second lamp in slot 12, now the rack's first slot.
        ItemStack stray = this.fittings.getItem(RACK_START);
        if (kindOf(stray) == Kind.LIGHT) {
            // With the lamp slot taken it stays in the rack, where the beacon terminal lets the player take it out.
            if (this.fittings.getItem(LAMP).isEmpty()) {
                this.fittings.getItems().set(LAMP, stray);
                this.fittings.getItems().set(RACK_START, ItemStack.EMPTY);
            }
        }
        in.child("Services").ifPresent(this.services::load);
        for (int slot = 0; slot < FITTINGS; slot++) {
            final int s = slot;
            in.child("Storage" + slot).ifPresent(child -> ContainerHelper.loadAllItems(child, this.storage(s).getItems()));
        }
        for (int slot = 0; slot < WALL_BAYS; slot++) {
            Furnace furnace = this.furnaces[slot];
            Optional<ValueInput> child = in.child("Furnace" + slot);
            child.ifPresent(c -> {
                c.child("Items").ifPresent(items -> ContainerHelper.loadAllItems(items, furnace.items.getItems()));
                furnace.litTime = c.getIntOr("Lit", 0);
                furnace.litDuration = c.getIntOr("LitDuration", 0);
                furnace.cookingProgress = c.getIntOr("Cook", 0);
                furnace.cookingTotal = c.getIntOr("CookTotal", 0);
            });
            Brewer brewer = this.brewers[slot];
            in.child("Brewer" + slot).ifPresent(c -> {
                c.child("Items").ifPresent(items -> ContainerHelper.loadAllItems(items, brewer.items.getItems()));
                brewer.brewTime = c.getIntOr("Time", 0);
                brewer.fuel = c.getIntOr("Fuel", 0);
            });
        }
        this.fittings.setChanged();
    }

    /** Everything held by the fittings, for dropping when the vehicle is wrecked. */
    public NonNullList<ItemStack> allItems() {
        NonNullList<ItemStack> out = NonNullList.create();
        for (int slot = 0; slot < FITTINGS; slot++) {
            out.add(this.fittings.getItem(slot));
            if (this.storage[slot] != null) out.addAll(this.storage[slot].getItems());
        }
        for (Furnace furnace : this.furnaces) out.addAll(furnace.items.getItems());
        for (Brewer brewer : this.brewers) out.addAll(brewer.items.getItems());
        for (var disc : this.services.discs) out.addAll(disc.getItems());
        out.addAll(this.services.payment.getItems());
        return out;
    }

    /** The fitting slots. Changes are synced to clients (for drawing) through the vehicle. */
    public final class Fittings extends SimpleContainer {
        Fittings() {
            super(FITTINGS);
        }

        @Override public boolean stillValid(Player player) {
            return SupportModules.this.canUse(player);
        }

        @Override public boolean canPlaceItem(int slot, ItemStack stack) {
            return accepts(slot, stack);
        }

        @Override public int getMaxStackSize() {
            return 64;
        }

        @Override public void setChanged() {
            super.setChanged();
            SupportModules.this.vehicle.onFittingsChanged();
        }
    }

    /** A module's own inventory, usable only near the vehicle. */
    /**
     * Where a fitting is, in the world, for its sounds: a bay or bunk where its cell is (out with the slide-outs while
     * deployed), the lockers under the body, the roof mount and the lamp hook.
     */
    public net.minecraft.world.phys.Vec3 soundAt(int slot) {
        double slide = this.vehicle.isPacked() ? 0 : ttv.migami.jeg.vehicle.util.SupportRig.slides(this.vehicle.deployProgress()) * 16;
        double floor = ttv.migami.jeg.vehicle.util.SupportRig.FLOOR_Y, front = ttv.migami.jeg.vehicle.util.SupportRig.INTERIOR_FRONT;
        if (slot < WALL_BAYS) {
            double side = slot < 3 ? 1 : -1;
            return ttv.migami.jeg.vehicle.util.SupportRig.world(this.vehicle, side * (8 + slide), floor + 8, front + (2 + slot % 3 + .5) * 16);
        }
        return switch (slot) {
            case BUNK_LEFT, BUNK_RIGHT -> ttv.migami.jeg.vehicle.util.SupportRig.world(this.vehicle, (slot == BUNK_LEFT ? 1 : -1) * (8 + slide), floor + 4, front + 16);
            case LOCKER_LEFT, LOCKER_RIGHT -> ttv.migami.jeg.vehicle.util.SupportRig.world(this.vehicle, slot == LOCKER_LEFT ? 20 : -20, 8, 5);
            case ROOF -> ttv.migami.jeg.vehicle.util.SupportRig.world(this.vehicle, 0, ttv.migami.jeg.vehicle.util.SupportRig.ROOF_TOP + 8, 24);
            default -> ttv.migami.jeg.vehicle.util.SupportRig.world(this.vehicle, 11, ttv.migami.jeg.vehicle.util.SupportRig.CEILING_Y - 6, 16);
        };
    }

    /** Fitting or taking out a module sounds as placing or breaking its block, where it goes. */
    public void fittingSound(int slot, ItemStack stack, boolean placed) {
        if (!(this.vehicle.level() instanceof ServerLevel level) || !(stack.getItem() instanceof BlockItem item)) return;
        var type = item.getBlock().defaultBlockState().getSoundType();
        net.minecraft.world.phys.Vec3 at = this.soundAt(slot);
        level.playSound(null, at.x, at.y, at.z, placed ? type.getPlaceSound() : type.getBreakSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                (type.getVolume() + 1.0F) / 2.0F, type.getPitch() * 0.8F);
    }

    /**
     * A container that sounds as the block it stands for when a player opens and closes it (a chest menu calls these).
     * Everything else goes to the real container.
     */
    static final class SoundedContainer implements net.minecraft.world.Container {
        private final net.minecraft.world.Container inner;
        private final net.minecraft.sounds.SoundEvent open, close;
        private final net.minecraft.world.phys.Vec3 at;

        SoundedContainer(net.minecraft.world.Container inner, ItemStack block, net.minecraft.world.phys.Vec3 at) {
            this.inner = inner;
            this.at = at;
            var item = block.getItem();
            if (item == Items.BARREL) {
                this.open = net.minecraft.sounds.SoundEvents.BARREL_OPEN;
                this.close = net.minecraft.sounds.SoundEvents.BARREL_CLOSE;
            } else if (item == Items.ENDER_CHEST) {
                this.open = net.minecraft.sounds.SoundEvents.ENDER_CHEST_OPEN;
                this.close = net.minecraft.sounds.SoundEvents.ENDER_CHEST_CLOSE;
            } else if (item instanceof BlockItem b && b.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
                this.open = net.minecraft.sounds.SoundEvents.SHULKER_BOX_OPEN;
                this.close = net.minecraft.sounds.SoundEvents.SHULKER_BOX_CLOSE;
            } else {
                this.open = net.minecraft.sounds.SoundEvents.CHEST_OPEN;
                this.close = net.minecraft.sounds.SoundEvents.CHEST_CLOSE;
            }
        }

        private void play(Player player, net.minecraft.sounds.SoundEvent sound) {
            if (player.level() instanceof ServerLevel level) {
                level.playSound(null, this.at.x, this.at.y, this.at.z, sound, net.minecraft.sounds.SoundSource.BLOCKS, 0.5F,
                        level.getRandom().nextFloat() * 0.1F + 0.9F);
            }
        }

        @Override public void startOpen(net.minecraft.world.entity.ContainerUser user) {
            this.inner.startOpen(user);
            if (user instanceof Player player && !player.isSpectator()) this.play(player, this.open);
        }

        @Override public void stopOpen(net.minecraft.world.entity.ContainerUser user) {
            this.inner.stopOpen(user);
            if (user instanceof Player player && !player.isSpectator()) this.play(player, this.close);
        }

        @Override public int getContainerSize() { return this.inner.getContainerSize(); }
        @Override public boolean isEmpty() { return this.inner.isEmpty(); }
        @Override public ItemStack getItem(int slot) { return this.inner.getItem(slot); }
        @Override public ItemStack removeItem(int slot, int count) { return this.inner.removeItem(slot, count); }
        @Override public ItemStack removeItemNoUpdate(int slot) { return this.inner.removeItemNoUpdate(slot); }
        @Override public void setItem(int slot, ItemStack stack) { this.inner.setItem(slot, stack); }
        @Override public int getMaxStackSize() { return this.inner.getMaxStackSize(); }
        @Override public void setChanged() { this.inner.setChanged(); }
        @Override public boolean stillValid(Player player) { return this.inner.stillValid(player); }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return this.inner.canPlaceItem(slot, stack); }
        @Override public void clearContent() { this.inner.clearContent(); }
    }

    public final class ModuleContainer extends SimpleContainer {
        ModuleContainer(int size) {
            super(size);
        }

        @Override public boolean stillValid(Player player) {
            return SupportModules.this.canUse(player);
        }
    }

    /** A furnace, smoker or blast furnace fitted in a bay, cooking as the vanilla one does (no experience). */
    private final class Furnace {
        final ModuleContainer items = new ModuleContainer(3);
        int litTime, litDuration, cookingProgress, cookingTotal;
        final ContainerData data = new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> Furnace.this.litTime;
                    case 1 -> Furnace.this.litDuration;
                    case 2 -> Furnace.this.cookingProgress;
                    case 3 -> Furnace.this.cookingTotal;
                    default -> 0;
                };
            }

            @Override public void set(int index, int value) {
                switch (index) {
                    case 0 -> Furnace.this.litTime = value;
                    case 1 -> Furnace.this.litDuration = value;
                    case 2 -> Furnace.this.cookingProgress = value;
                    case 3 -> Furnace.this.cookingTotal = value;
                    default -> { }
                }
            }

            @Override public int getCount() {
                return 4;
            }
        };

        @SuppressWarnings("unchecked")
        void tick(ServerLevel level, RecipeType<? extends AbstractCookingRecipe> type, int speed) {
            boolean wasLit = this.litTime > 0;
            if (this.litTime > 0) this.litTime--;
            ItemStack input = this.items.getItem(0), fuel = this.items.getItem(1), output = this.items.getItem(2);
            Optional<? extends RecipeHolder<? extends AbstractCookingRecipe>> recipe = input.isEmpty() ? Optional.empty()
                    : level.recipeAccess().getRecipeFor((RecipeType<AbstractCookingRecipe>) type, new SingleRecipeInput(input), level);
            ItemStack result = recipe.map(r -> r.value().assemble(new SingleRecipeInput(input))).orElse(ItemStack.EMPTY);
            boolean canCook = !result.isEmpty() && (output.isEmpty()
                    || ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize());
            if (this.litTime <= 0 && canCook && level.fuelValues().isFuel(fuel)) {
                this.litDuration = this.litTime = level.fuelValues().burnDuration(fuel) / speed;
                if (this.litTime > 0) {
                    Item remainder = fuel.getItem() == Items.LAVA_BUCKET ? Items.BUCKET : null;
                    fuel.shrink(1);
                    if (fuel.isEmpty() && remainder != null) this.items.setItem(1, new ItemStack(remainder));
                }
            }
            if (this.litTime > 0 && canCook) {
                this.cookingTotal = recipe.get().value().cookingTime();
                if (++this.cookingProgress >= this.cookingTotal) {
                    this.cookingProgress = 0;
                    if (output.isEmpty()) this.items.setItem(2, result.copy());
                    else output.grow(result.getCount());
                    input.shrink(1);
                }
            } else if (this.cookingProgress > 0) {
                this.cookingProgress = Math.max(0, this.cookingProgress - 2);
            }
            if (wasLit != this.litTime > 0) SupportModules.this.vehicle.onFittingsChanged();
        }
    }

    /**
     * A brewing stand fitted in a bay, brewing as the vanilla one does: three bottles, an ingredient and blaze powder
     * (20 brews each), 400 ticks a brew.
     */
    private final class Brewer {
        final ModuleContainer items = new ModuleContainer(5);
        int brewTime, fuel;
        net.minecraft.world.item.Item ingredient = Items.AIR;
        final ContainerData data = new ContainerData() {
            @Override public int get(int index) {
                return index == 0 ? Brewer.this.brewTime : index == 1 ? Brewer.this.fuel : 0;
            }

            @Override public void set(int index, int value) {
                if (index == 0) Brewer.this.brewTime = value;
                else if (index == 1) Brewer.this.fuel = value;
            }

            @Override public int getCount() {
                return 2;
            }
        };

        /** Returns true when a brew finished this tick. */
        boolean tick(ServerLevel level) {
            var brewing = level.potionBrewing();
            ItemStack fuelStack = this.items.getItem(4);
            if (this.fuel <= 0 && fuelStack.is(Items.BLAZE_POWDER)) {
                this.fuel = 20;
                fuelStack.shrink(1);
            }
            ItemStack ingredient = this.items.getItem(3);
            boolean canBrew = false;
            if (!ingredient.isEmpty() && brewing.isIngredient(ingredient)) {
                for (int i = 0; i < 3; i++) {
                    ItemStack bottle = this.items.getItem(i);
                    if (!bottle.isEmpty() && brewing.hasMix(bottle, ingredient)) {
                        canBrew = true;
                        break;
                    }
                }
            }
            if (this.brewTime > 0) {
                this.brewTime--;
                if (this.brewTime == 0 && canBrew) {
                    for (int i = 0; i < 3; i++) {
                        this.items.setItem(i, brewing.mix(ingredient, this.items.getItem(i)));
                    }
                    var template = ingredient.getItem().getCraftingRemainder();
                    ingredient.shrink(1);
                    if (ingredient.isEmpty() && template != null) this.items.setItem(3, template.create());
                    return true;
                } else if (!canBrew || !ingredient.is(this.ingredient)) {
                    this.brewTime = 0;
                }
            } else if (canBrew && this.fuel > 0) {
                this.fuel--;
                this.brewTime = 400;
                this.ingredient = ingredient.getItem();
            }
            return false;
        }
    }

    /** Whether the furnace in a bay is burning (it glows). */
    public boolean furnaceLit(int slot) {
        return slot >= 0 && slot < WALL_BAYS && this.furnaces[slot].litTime > 0;
    }
}
