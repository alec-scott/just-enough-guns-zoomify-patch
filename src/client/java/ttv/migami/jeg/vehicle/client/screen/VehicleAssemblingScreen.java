package ttv.migami.jeg.vehicle.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.network.ClientNetworkHandler;
import ttv.migami.jeg.vehicle.data.VehicleDataManager;
import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import ttv.migami.jeg.vehicle.menu.VehicleAssemblingMenu;
import ttv.migami.jeg.vehicle.recipe.VehicleAssemblyRecipe;
import ttv.migami.jeg.vehicle.recipe.VehicleAssemblyRecipeManager;

/**
 * The vehicle assembling table. Vehicles are listed on the left, each marked green when everything it needs is in the
 * inventory and red when not. Picking one shows its silhouette on the right with every item it takes, one per line: the item, how
 * many are carried against how many are needed (green or red), and its name. Assemble builds the picked vehicle.
 * <p>
 * The top right and the right end of the inventory row are left clear: inventory mods in the yftm pack put their sort
 * buttons there.
 */
public final class VehicleAssemblingScreen extends AbstractContainerScreen<VehicleAssemblingMenu> {
    private static final int RECIPES_PER_PAGE = 6;
    private static final Component ALL_FILTER = Component.translatable("gui.jeg.vehicle_assembling.filter.all");
    private static final Component PREVIOUS_PAGE = Component.literal("<");
    private static final Component NEXT_PAGE = Component.literal(">");
    private static final Component ASSEMBLE = Component.translatable("gui.jeg.vehicle_assembling.assemble");
    private static final Component READY = Component.translatable("gui.jeg.vehicle_assembling.ready");
    private static final Component MISSING = Component.translatable("gui.jeg.vehicle_assembling.missing");
    private static final Component NONE = Component.translatable("gui.jeg.vehicle_assembling.none");

    private static final int TEXT = 0xFFE6E6E6;
    private static final int DIM = 0xFF9AA3AD;
    private static final int GOOD = 0xFF6BE36B;
    private static final int SHORT = 0xFFFF6B6B;

    private static final int FILTER_Y = 18;
    private static final int LIST_X = 8;
    private static final int LIST_Y = 40;
    private static final int LIST_WIDTH = 96;
    private static final int ROW_HEIGHT = 16;
    private static final int ROW_STEP = 18;
    private static final int PAGE_Y = LIST_Y + RECIPES_PER_PAGE * ROW_STEP + 2;
    private static final int PANEL_X = 108;
    private static final int PANEL_Y = 38;
    private static final int PANEL_RIGHT = 248;
    private static final int PANEL_BOTTOM = 162;
    /** The vehicle's silhouette, centred under its name. */
    private static final int ICON_Y = 64;
    private static final int ICON_WIDTH = 64;
    private static final int ICON_HEIGHT = 16;
    private static final int NEEDS_Y = 83;
    private static final int NEED_STEP = 15;
    private static final int ASSEMBLE_Y = 145;
    /** Clear of the inventory sort button at the right end of the inventory row. */
    private static final int ASSEMBLE_WIDTH = 118;
    private static final int SLOTS_Y = 166;
    private static final int HOTBAR_Y = 224;

    private List<VehicleAssemblyRecipe> allRecipes = List.of();
    private List<VehicleAssemblyRecipe> recipes = List.of();
    private int page;
    @Nullable
    private VehicleType filterType;
    @Nullable
    private Identifier selectedId;
    @Nullable
    private Button assembleButton;

    public VehicleAssemblingScreen(VehicleAssemblingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 256, 248);
    }

    @Override
    protected void init() {
        super.init();
        this.allRecipes = VehicleAssemblyRecipeManager.recipes();
        this.recipes = this.filteredRecipes();
        this.page = Math.min(this.page, this.maxPage());
        if (this.selected() == null) {
            this.selectedId = this.recipes.isEmpty() ? null : this.recipes.get(this.page * RECIPES_PER_PAGE).id();
        }
        this.addFilterButtons();
        this.addRecipeButtons();
        this.addPageButtons();
        this.assembleButton = this.addRenderableWidget(Button.builder(ASSEMBLE, button -> {
                    VehicleAssemblyRecipe recipe = this.selected();
                    if (recipe != null && this.hasCost(recipe)) {
                        ClientNetworkHandler.sendAssembleVehicle(recipe.id());
                    }
                })
                .bounds(this.leftPos + PANEL_X + 4, this.topPos + ASSEMBLE_Y, ASSEMBLE_WIDTH, 16)
                .build());
        this.updateAssembleButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // The inventory changes under the screen (assembling, picking things up), so this is kept current.
        this.updateAssembleButton();
    }

    private void updateAssembleButton() {
        if (this.assembleButton != null) {
            VehicleAssemblyRecipe recipe = this.selected();
            this.assembleButton.active = recipe != null && this.hasCost(recipe);
            this.assembleButton.visible = recipe != null;
        }
    }

    /** One tab per kind of vehicle the table can build; the one shown is greyed out. */
    private void addFilterButtons() {
        List<FilterButton> shown = new ArrayList<>();
        for (FilterButton filter : FilterButton.values()) {
            if (this.allRecipes.stream().anyMatch(recipe -> typeOf(recipe) == filter.type)) {
                shown.add(filter);
            }
        }
        int width = Math.min(46, (this.imageWidth - 16 - shown.size() * 2) / (shown.size() + 1));
        int x = this.leftPos + 8;
        int y = this.topPos + FILTER_Y;
        Button all = this.addRenderableWidget(Button.builder(ALL_FILTER, button -> this.setFilter(null)).bounds(x, y, width, 16).build());
        all.active = this.filterType != null;
        for (FilterButton filter : shown) {
            x += width + 2;
            Button button = this.addRenderableWidget(Button.builder(filter.label, clicked -> this.setFilter(filter.type))
                    .bounds(x, y, width, 16)
                    .build());
            button.active = this.filterType != filter.type;
        }
    }

    private void addRecipeButtons() {
        int start = this.page * RECIPES_PER_PAGE;
        int end = Math.min(this.recipes.size(), start + RECIPES_PER_PAGE);
        for (int index = start; index < end; index++) {
            VehicleAssemblyRecipe recipe = this.recipes.get(index);
            int row = index - start;
            this.addRenderableWidget(Button.builder(vehicleName(recipe.resultVehicle()), clicked -> {
                        this.selectedId = recipe.id();
                        this.updateAssembleButton();
                    })
                    .bounds(this.leftPos + LIST_X, this.topPos + LIST_Y + row * ROW_STEP, LIST_WIDTH, ROW_HEIGHT)
                    .build());
        }
    }

    private void addPageButtons() {
        if (this.maxPage() <= 0) {
            return;
        }
        Button previous = this.addRenderableWidget(Button.builder(PREVIOUS_PAGE, button -> {
                    this.page = Math.max(0, this.page - 1);
                    this.refreshWidgets();
                })
                .bounds(this.leftPos + LIST_X, this.topPos + PAGE_Y, 16, 14)
                .build());
        previous.active = this.page > 0;
        Button next = this.addRenderableWidget(Button.builder(NEXT_PAGE, button -> {
                    this.page = Math.min(this.maxPage(), this.page + 1);
                    this.refreshWidgets();
                })
                .bounds(this.leftPos + LIST_X + LIST_WIDTH - 16, this.topPos + PAGE_Y, 16, 14)
                .build());
        next.active = this.page < this.maxPage();
    }

    private void refreshWidgets() {
        this.clearWidgets();
        this.init();
    }

    private void setFilter(@Nullable VehicleType type) {
        if (this.filterType == type) {
            return;
        }
        this.filterType = type;
        this.page = 0;
        this.selectedId = null;
        this.refreshWidgets();
    }

    private List<VehicleAssemblyRecipe> filteredRecipes() {
        if (this.filterType == null) {
            return this.allRecipes;
        }
        return this.allRecipes.stream().filter(recipe -> typeOf(recipe) == this.filterType).toList();
    }

    private static VehicleType typeOf(VehicleAssemblyRecipe recipe) {
        return VehicleDataManager.get(recipe.resultVehicle()).defaults().vehicleType();
    }

    private int maxPage() {
        return Math.max(0, (this.recipes.size() - 1) / RECIPES_PER_PAGE);
    }

    /** The picked vehicle, if it is still in the list shown. */
    @Nullable
    private VehicleAssemblyRecipe selected() {
        if (this.selectedId == null) {
            return null;
        }
        for (VehicleAssemblyRecipe recipe : this.recipes) {
            if (recipe.id().equals(this.selectedId)) {
                return recipe;
            }
        }
        return null;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xEE1B2026);
        guiGraphics.outline(x, y, this.imageWidth, this.imageHeight, 0xFF3A434D);
        guiGraphics.fill(x + PANEL_X, y + PANEL_Y, x + PANEL_RIGHT, y + PANEL_BOTTOM, 0xCC0F1216);
        guiGraphics.outline(x + PANEL_X, y + PANEL_Y, PANEL_RIGHT - PANEL_X, PANEL_BOTTOM - PANEL_Y, 0xFF3A434D);
        this.drawSlotBackgrounds(guiGraphics);

        VehicleAssemblyRecipe recipe = this.selected();
        super.extractContents(guiGraphics, mouseX, mouseY, partialTick);

        this.drawListMarkers(guiGraphics);
        if (this.maxPage() > 0) {
            guiGraphics.centeredText(this.font, Component.literal((this.page + 1) + " / " + (this.maxPage() + 1)),
                    x + LIST_X + LIST_WIDTH / 2, y + PAGE_Y + 3, DIM);
        }
        if (recipe == null) {
            guiGraphics.textWithWordWrap(this.font, NONE, x + PANEL_X + 6, y + PANEL_Y + 6, PANEL_RIGHT - PANEL_X - 12, DIM);
            return;
        }
        this.drawDetails(guiGraphics, recipe, mouseX, mouseY);
    }

    /** A small green or red mark at the end of each vehicle's button, and an outline round the picked one. */
    private void drawListMarkers(GuiGraphicsExtractor guiGraphics) {
        int start = this.page * RECIPES_PER_PAGE;
        int end = Math.min(this.recipes.size(), start + RECIPES_PER_PAGE);
        for (int index = start; index < end; index++) {
            VehicleAssemblyRecipe recipe = this.recipes.get(index);
            int left = this.leftPos + LIST_X;
            int top = this.topPos + LIST_Y + (index - start) * ROW_STEP;
            int mark = this.hasCost(recipe) ? GOOD : SHORT;
            guiGraphics.fill(left + 3, top + 5, left + 7, top + 11, mark);
            if (recipe.id().equals(this.selectedId)) {
                guiGraphics.outline(left - 1, top - 1, LIST_WIDTH + 2, ROW_HEIGHT + 2, 0xFFFFFFFF);
            }
        }
    }

    /** The picked vehicle's name, whether it can be built, and each item it takes. */
    private void drawDetails(GuiGraphicsExtractor guiGraphics, VehicleAssemblyRecipe recipe, int mouseX, int mouseY) {
        int x = this.leftPos + PANEL_X + 5;
        int top = this.topPos + PANEL_Y + 5;
        int nameWidth = PANEL_RIGHT - PANEL_X - 10;
        guiGraphics.textWithWordWrap(this.font, vehicleName(recipe.resultVehicle()), x, top, nameWidth, TEXT);
        boolean ready = this.hasCost(recipe);
        int nameLines = this.font.split(vehicleName(recipe.resultVehicle()), nameWidth).size();
        guiGraphics.text(this.font, ready ? READY : MISSING, x, top + nameLines * 9 + 3, ready ? GOOD : SHORT);

        Identifier icon = vehicleIcon(recipe.resultVehicle());
        if (icon != null) {
            int iconX = this.leftPos + PANEL_X + (PANEL_RIGHT - PANEL_X - ICON_WIDTH) / 2;
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, icon, iconX, this.topPos + ICON_Y, 0, 0, ICON_WIDTH, ICON_HEIGHT, 256, 64, 256, 64);
        }

        Player player = this.minecraft == null ? null : this.minecraft.player;
        int rowY = this.topPos + NEEDS_Y;
        for (VehicleAssemblyRecipe.Ingredient ingredient : recipe.ingredients()) {
            Item item = BuiltInRegistries.ITEM.getValue(ingredient.item());
            ItemStack stack = new ItemStack(item);
            int have = player == null ? 0 : countItem(player, item);
            guiGraphics.item(stack, x, rowY);
            String count = have + "/" + ingredient.count();
            int textX = x + 19;
            int textY = rowY + 4;
            guiGraphics.text(this.font, count, textX, textY, have >= ingredient.count() ? GOOD : SHORT);
            int nameX = textX + this.font.width(count) + 4;
            int room = this.leftPos + PANEL_RIGHT - 4 - nameX;
            String name = item.getName(stack).getString();
            if (this.font.width(name) > room) {
                name = this.font.plainSubstrByWidth(name, room - this.font.width("…")) + "…";
            }
            guiGraphics.text(this.font, name, nameX, textY, TEXT);
            if (mouseX >= x && mouseX < this.leftPos + PANEL_RIGHT - 4 && mouseY >= rowY && mouseY < rowY + NEED_STEP) {
                guiGraphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
            }
            rowY += NEED_STEP;
        }
    }

    /** Dark squares under the inventory slots, which this plain panel otherwise does not show. */
    private void drawSlotBackgrounds(GuiGraphicsExtractor guiGraphics) {
        for (int row = 0; row < 4; row++) {
            int slotY = this.topPos + (row < 3 ? SLOTS_Y + row * 18 : HOTBAR_Y);
            for (int col = 0; col < 9; col++) {
                int slotX = this.leftPos + 8 + col * 18;
                guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF2E353D);
                guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF14181C);
            }
        }
    }

    /** Only the title: the panel speaks for the inventory, and the sort buttons sit where its label went. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        guiGraphics.text(this.font, this.title, 8, 6, TEXT, false);
    }

    /** The vehicle's white silhouette (64 by 16), as on its container item. */
    @Nullable
    private static Identifier vehicleIcon(Identifier vehicleId) {
        return switch (vehicleId.getPath()) {
            case "a10", "ah6", "bmp2", "hpj11", "laser_tower", "lav150", "mi28", "speedboat", "tom6", "truck", "waveforce_tower" ->
                    Reference.id("textures/vehicle_icon/" + vehicleId.getPath() + "_icon.png");
            default -> null;
        };
    }

    private static Component vehicleName(Identifier vehicleId) {
        return Component.translatable("entity." + vehicleId.getNamespace() + "." + vehicleId.getPath());
    }

    private boolean hasCost(VehicleAssemblyRecipe recipe) {
        Player player = this.minecraft == null ? null : this.minecraft.player;
        if (player == null) {
            return false;
        }
        for (VehicleAssemblyRecipe.Ingredient ingredient : recipe.ingredients()) {
            Item item = BuiltInRegistries.ITEM.getValue(ingredient.item());
            if (countItem(player, item) < ingredient.count()) {
                return false;
            }
        }
        return true;
    }

    private static int countItem(Player player, Item item) {
        int count = 0;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** For checks: which vehicle is picked and whether Assemble can be pressed. */
    @Nullable
    public Identifier selectedVehicle() {
        VehicleAssemblyRecipe recipe = this.selected();
        return recipe == null ? null : recipe.resultVehicle();
    }

    public boolean canAssemble() {
        return this.assembleButton != null && this.assembleButton.active;
    }

    private enum FilterButton {
        LAND(VehicleType.LAND, Component.translatable("gui.jeg.vehicle_assembling.filter.land")),
        HELICOPTER(VehicleType.HELICOPTER, Component.translatable("gui.jeg.vehicle_assembling.filter.helicopter")),
        AIRCRAFT(VehicleType.AIRCRAFT, Component.translatable("gui.jeg.vehicle_assembling.filter.aircraft")),
        BOAT(VehicleType.BOAT, Component.translatable("gui.jeg.vehicle_assembling.filter.boat")),
        ARTILLERY(VehicleType.ARTILLERY, Component.translatable("gui.jeg.vehicle_assembling.filter.artillery"));

        private final VehicleType type;
        private final Component label;

        FilterButton(VehicleType type, Component label) {
            this.type = type;
            this.label = label;
        }
    }
}
