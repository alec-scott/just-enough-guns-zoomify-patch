package ttv.migami.jeg.vehicle.support;

import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A furnace, smoker or blast furnace kept by a vehicle rather than by a block entity (the FSV-6's wall bays, the
 * Chinook's cabin), cooking as the vanilla one does (no experience). Its owner ticks it every server tick, moving or
 * not, and saves it.
 */
public final class VehicleFurnace {
    public final SimpleContainer items;
    public int litTime, litDuration, cookingProgress, cookingTotal;
    public final ContainerData data = new ContainerData() {
        @Override public int get(int index) {
            return switch (index) {
                case 0 -> VehicleFurnace.this.litTime;
                case 1 -> VehicleFurnace.this.litDuration;
                case 2 -> VehicleFurnace.this.cookingProgress;
                case 3 -> VehicleFurnace.this.cookingTotal;
                default -> 0;
            };
        }

        @Override public void set(int index, int value) {
            switch (index) {
                case 0 -> VehicleFurnace.this.litTime = value;
                case 1 -> VehicleFurnace.this.litDuration = value;
                case 2 -> VehicleFurnace.this.cookingProgress = value;
                case 3 -> VehicleFurnace.this.cookingTotal = value;
                default -> { }
            }
        }

        @Override public int getCount() {
            return 4;
        }
    };
    private final Runnable onLitChanged;

    /**
     * @param stillValid   who may keep its menu open (aboard, or within reach)
     * @param onLitChanged called as it lights or goes out (its owner shows the glow)
     */
    public VehicleFurnace(Predicate<Player> stillValid, Runnable onLitChanged) {
        this.items = new SimpleContainer(3) {
            @Override public boolean stillValid(Player player) {
                return stillValid.test(player);
            }
        };
        this.onLitChanged = onLitChanged;
    }

    public boolean lit() {
        return this.litTime > 0;
    }

    /** The recipes a furnace of this kind cooks, or null if the kind is no furnace. */
    @Nullable
    public static RecipeType<? extends AbstractCookingRecipe> recipeType(SupportModules.Kind kind) {
        return switch (kind) {
            case FURNACE -> RecipeType.SMELTING;
            case SMOKER -> RecipeType.SMOKING;
            case BLAST_FURNACE -> RecipeType.BLASTING;
            default -> null;
        };
    }

    /** One tick of this kind of furnace (a smoker and a blast furnace cook twice as fast on half the fuel). */
    public void tick(ServerLevel level, SupportModules.Kind kind) {
        RecipeType<? extends AbstractCookingRecipe> type = recipeType(kind);
        if (type != null) {
            this.tick(level, type, kind == SupportModules.Kind.FURNACE ? 1 : 2);
        }
    }

    @SuppressWarnings("unchecked")
    public void tick(ServerLevel level, RecipeType<? extends AbstractCookingRecipe> type, int speed) {
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
        if (wasLit != this.litTime > 0) this.onLitChanged.run();
    }

    /** Its screen: a smoker's, a blast furnace's or a furnace's. */
    public AbstractContainerMenu menu(int id, Inventory inventory, SupportModules.Kind kind) {
        return switch (kind) {
            case SMOKER -> new SmokerMenu(id, inventory, this.items, this.data);
            case BLAST_FURNACE -> new BlastFurnaceMenu(id, inventory, this.items, this.data);
            default -> new FurnaceMenu(id, inventory, this.items, this.data);
        };
    }

    /** Its burn and cook state (the items are saved by the owner with its other containers). */
    public void saveState(ValueOutput out) {
        out.putInt("Lit", this.litTime);
        out.putInt("LitDuration", this.litDuration);
        out.putInt("Cook", this.cookingProgress);
        out.putInt("CookTotal", this.cookingTotal);
    }

    public void loadState(ValueInput in) {
        this.litTime = in.getIntOr("Lit", 0);
        this.litDuration = in.getIntOr("LitDuration", 0);
        this.cookingProgress = in.getIntOr("Cook", 0);
        this.cookingTotal = in.getIntOr("CookTotal", 0);
    }

    /** Items and state together, as the FSV-6's bays save them. */
    public void save(ValueOutput out) {
        ContainerHelper.saveAllItems(out.child("Items"), this.items.getItems());
        this.saveState(out);
    }

    public void load(ValueInput in) {
        in.child("Items").ifPresent(items -> ContainerHelper.loadAllItems(items, this.items.getItems()));
        this.loadState(in);
    }
}
