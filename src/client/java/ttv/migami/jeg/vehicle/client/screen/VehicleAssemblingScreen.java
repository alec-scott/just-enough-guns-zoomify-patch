package ttv.migami.jeg.vehicle.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ttv.migami.jeg.network.ClientNetworkHandler;
import ttv.migami.jeg.vehicle.client.overlay.HudKit;
import ttv.migami.jeg.vehicle.data.VehicleDataManager;
import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.menu.VehicleAssemblingMenu;
import ttv.migami.jeg.vehicle.recipe.VehicleAssemblyRecipe;
import ttv.migami.jeg.vehicle.recipe.VehicleAssemblyRecipeManager;

/**
 * The vehicle assembling table, in the vehicle screens' clean style (HudKit: rounded cards, Inter, one amber accent).
 * Tabs filter by kind; the list on the left gives each vehicle its short name (a list of "BL-132 130 mm Gun Mount" was
 * cut off on every button, the user's review of 2026-11-20) with a green or red dot for whether everything it needs is
 * carried. The card on the right turns the picked vehicle's real model in 3D (drag to turn it), its full name under it,
 * then every item it takes with how many are carried, and Assemble.
 * <p>
 * Inventory Profiles Next (in the yftm pack) puts its sort buttons in the title row's right end, left clear, and one
 * button at the right edge level with the top of the inventory slots, which lands in the vehicle card right of the first
 * item, where nothing is drawn. Its {@code IPNGuiHint(hide)} did not move or hide it (tried on the screen and the menu,
 * 2026-11-20), and {@code IPNPlayerSideOnly} stacked its buttons over the card beside the name.
 */
public final class VehicleAssemblingScreen extends AbstractContainerScreen<VehicleAssemblingMenu> {
    private static final int RECIPES_PER_PAGE = 6;
    private static final Component ALL_FILTER = Component.translatable("gui.jeg.vehicle_assembling.filter.all");
    private static final Component PREVIOUS_PAGE = Component.literal("‹");
    private static final Component NEXT_PAGE = Component.literal("›");
    private static final Component ASSEMBLE = Component.translatable("gui.jeg.vehicle_assembling.assemble");
    private static final Component MISSING = Component.translatable("gui.jeg.vehicle_assembling.missing");
    private static final Component NONE = Component.translatable("gui.jeg.vehicle_assembling.none");
    private static final Component DRAG = Component.translatable("gui.jeg.vehicle_assembling.drag");

    private static final int CARD = 0xF2151A1F, CARD_EDGE = 0xFF2B323A, WELL_RIM = 0xFF262D35, WELL = 0xFF101418;
    private static final int STAGE = 0xFF0C0F12, STAGE_FLOOR = 0xFF1B2228;
    private static final int CHIP_IDLE = 0x14FFFFFF, CHIP_HOVER = 0x26FFFFFF, CHIP_ON = 0x33F2B53A;
    private static final int ACCENT_HOVER = 0xFFFFC85A, ON_ACCENT = 0xFF231704;

    /** 320 by 238: no taller than the 240 the automatic GUI scale guarantees, so the hotbar is never cut off. */
    private static final int WIDTH = 320;
    private static final int HEIGHT = 238;
    private static final int TABS_Y = 18;
    private static final int TAB_HEIGHT = 13;
    /** The list card and, below it, the player's inventory card (its slots are placed by the menu). */
    private static final int LEFT_X = 4, LEFT_W = 172;
    private static final int LIST_TOP = 35, LIST_BOTTOM = 152;
    private static final int ROW_X = 8, ROW_Y = 39, ROW_W = 164, ROW_H = 14, ROW_STEP = 16;
    private static final int PAGE_Y = ROW_Y + RECIPES_PER_PAGE * ROW_STEP + 1;
    private static final int INV_TOP = 154;
    private static final int SLOTS_Y = 158, HOTBAR_Y = 216;
    /** The vehicle card: the turning model, its name, what it takes and Assemble. */
    private static final int CARD_X = 180, CARD_W = 136, CARD_TOP = 35, CARD_BOTTOM = 236;
    private static final int STAGE_X = CARD_X + 4, STAGE_Y = CARD_TOP + 5, STAGE_W = CARD_W - 8, STAGE_H = 76;
    private static final int ASSEMBLE_Y = CARD_BOTTOM - 19, ASSEMBLE_H = 15, ASSEMBLE_W = CARD_W - 8;
    /** Degrees the model turns a second on its own, and per GUI pixel dragged. */
    private static final float SPIN_RATE = 24.0F, DRAG_RATE = 1.4F;
    /** The view's angle above the vehicle. */
    private static final float TILT = 18.0F;

    private List<VehicleAssemblyRecipe> allRecipes = List.of();
    private List<VehicleAssemblyRecipe> recipes = List.of();
    private int page;
    @Nullable
    private Category filter;
    @Nullable
    private Identifier selectedId;
    @Nullable
    private Button assembleButton;
    /** Client-only stand-ins drawn in the preview, one per vehicle shown, never added to the world. */
    private final Map<Identifier, VehicleEntity> previews = new HashMap<>();
    private float spin = 210.0F;
    private long lastFrame;
    private long dragPauseUntil;
    private boolean dragging;

    public VehicleAssemblingScreen(VehicleAssemblingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, WIDTH, HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        this.allRecipes = this.sorted(VehicleAssemblyRecipeManager.recipes());
        this.recipes = this.filteredRecipes();
        this.page = Math.min(this.page, this.maxPage());
        if (this.selected() == null) {
            this.selectedId = this.recipes.isEmpty() ? null : this.recipes.get(this.page * RECIPES_PER_PAGE).id();
        }
        this.addFilterButtons();
        this.addRecipeButtons();
        this.addPageButtons();
        this.assembleButton = this.addRenderableWidget(new Flat(this.leftPos + CARD_X + 4, this.topPos + ASSEMBLE_Y, ASSEMBLE_W, ASSEMBLE_H,
                ASSEMBLE, Style.PRIMARY, button -> {
                    VehicleAssemblyRecipe recipe = this.selected();
                    if (recipe != null && this.hasCost(recipe)) {
                        ClientNetworkHandler.sendAssembleVehicle(recipe.id());
                    }
                }));
        this.updateAssembleButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // The inventory changes under the screen (assembling, picking things up), so this is kept current.
        this.updateAssembleButton();
    }

    @Override
    public void removed() {
        super.removed();
        this.previews.clear();
    }

    private void updateAssembleButton() {
        if (this.assembleButton != null) {
            VehicleAssemblyRecipe recipe = this.selected();
            this.assembleButton.active = recipe != null && this.hasCost(recipe);
            this.assembleButton.visible = recipe != null;
        }
    }

    /** One tab per category the table has something in; the one shown is lit. */
    private void addFilterButtons() {
        List<Category> shown = new ArrayList<>();
        for (Category category : Category.values()) {
            if (this.allRecipes.stream().anyMatch(recipe -> this.categoryOf(recipe) == category)) {
                shown.add(category);
            }
        }
        int gap = 3;
        int width = (WIDTH - 8 - 8 - shown.size() * gap) / (shown.size() + 1);
        int x = this.leftPos + 8;
        int y = this.topPos + TABS_Y;
        Flat all = this.addRenderableWidget(new Flat(x, y, width, TAB_HEIGHT, ALL_FILTER, Style.TAB, button -> this.setFilter(null)));
        all.on = this.filter == null;
        for (Category category : shown) {
            x += width + gap;
            Flat button = this.addRenderableWidget(new Flat(x, y, width, TAB_HEIGHT, category.label(), Style.TAB, clicked -> this.setFilter(category)));
            button.on = this.filter == category;
        }
    }

    private void addRecipeButtons() {
        int start = this.page * RECIPES_PER_PAGE;
        int end = Math.min(this.recipes.size(), start + RECIPES_PER_PAGE);
        for (int index = start; index < end; index++) {
            VehicleAssemblyRecipe recipe = this.recipes.get(index);
            int row = index - start;
            Flat button = new Flat(this.leftPos + ROW_X, this.topPos + ROW_Y + row * ROW_STEP, ROW_W, ROW_H,
                    shortName(recipe.resultVehicle()), Style.ROW, clicked -> {
                        this.selectedId = recipe.id();
                        this.updateAssembleButton();
                    });
            button.recipe = recipe;
            this.addRenderableWidget(button);
        }
    }

    private void addPageButtons() {
        if (this.maxPage() <= 0) {
            return;
        }
        Flat previous = this.addRenderableWidget(new Flat(this.leftPos + ROW_X, this.topPos + PAGE_Y, 18, 12, PREVIOUS_PAGE, Style.TAB, button -> {
            this.page = Math.max(0, this.page - 1);
            this.refreshWidgets();
        }));
        previous.active = this.page > 0;
        Flat next = this.addRenderableWidget(new Flat(this.leftPos + ROW_X + ROW_W - 18, this.topPos + PAGE_Y, 18, 12, NEXT_PAGE, Style.TAB, button -> {
            this.page = Math.min(this.maxPage(), this.page + 1);
            this.refreshWidgets();
        }));
        next.active = this.page < this.maxPage();
    }

    private void refreshWidgets() {
        this.clearWidgets();
        this.init();
    }

    private void setFilter(@Nullable Category category) {
        if (this.filter == category) {
            return;
        }
        this.filter = category;
        this.page = 0;
        this.selectedId = null;
        this.refreshWidgets();
    }

    private List<VehicleAssemblyRecipe> filteredRecipes() {
        if (this.filter == null) {
            return this.allRecipes;
        }
        return this.allRecipes.stream().filter(recipe -> this.categoryOf(recipe) == this.filter).toList();
    }

    private static VehicleType typeOf(VehicleAssemblyRecipe recipe) {
        return VehicleDataManager.get(recipe.resultVehicle()).defaults().vehicleType();
    }

    /**
     * Where a vehicle is listed. The list was alphabetical by id under tabs named after the data's vehicle types ("Heli"
     * and "Air" apart, "Art"), which the user found confusing (2026-11-20). Helicopters and planes are now both Air; the
     * guns that fire on their own (AutoDefenceEntity: the sentry, the HPJ-11) are Defences, the crewed ones Artillery.
     */
    private Category categoryOf(VehicleAssemblyRecipe recipe) {
        return switch (typeOf(recipe)) {
            case LAND -> Category.GROUND;
            case HELICOPTER, AIRCRAFT -> Category.AIR;
            case BOAT -> Category.SEA;
            case ARTILLERY -> this.preview(recipe.resultVehicle()) instanceof ttv.migami.jeg.vehicle.entity.AutoDefenceEntity
                    ? Category.DEFENCES : Category.ARTILLERY;
        };
    }

    /** Grouped by category, each group from light to heavy (ORDER); anything not in ORDER after, by name. */
    private List<VehicleAssemblyRecipe> sorted(List<VehicleAssemblyRecipe> recipes) {
        List<VehicleAssemblyRecipe> list = new ArrayList<>(recipes);
        list.sort(java.util.Comparator.<VehicleAssemblyRecipe>comparingInt(recipe -> this.categoryOf(recipe).ordinal())
                .thenComparingInt(recipe -> {
                    int at = ORDER.indexOf(recipe.resultVehicle().getPath());
                    return at < 0 ? Integer.MAX_VALUE : at;
                })
                .thenComparing(recipe -> shortName(recipe.resultVehicle()).getString()));
        return list;
    }

    /** The order within each category: light to heavy, utility last. */
    private static final List<String> ORDER = List.of(
            "pickup", "pickup_hmg", "pickup_rocket", "pickup_tow",
            "lav150", "bmp2", "yx100", "plz05", "truck", "bulldozer",
            "ah6", "mi28", "ch47", "tom6", "a10",
            "speedboat",
            "type63", "howitzer", "mk42", "bl132",
            "sentry", "hpj11");

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
        // Drawn at the GUI scale: the HUD font and shapes made for that many screen pixels per unit.
        HudKit.useDensity(this.minecraft.getWindow().getGuiScale());
        int x = this.leftPos;
        int y = this.topPos;
        card(guiGraphics, x + LEFT_X, y + LIST_TOP, LEFT_W, LIST_BOTTOM - LIST_TOP);
        card(guiGraphics, x + LEFT_X, y + INV_TOP, LEFT_W, HEIGHT - 2 - INV_TOP);
        card(guiGraphics, x + CARD_X, y + CARD_TOP, CARD_W, CARD_BOTTOM - CARD_TOP);
        this.drawSlotWells(guiGraphics);

        VehicleAssemblyRecipe recipe = this.selected();
        if (recipe != null) {
            this.drawStage(guiGraphics, recipe, mouseX, mouseY);
        }
        super.extractContents(guiGraphics, mouseX, mouseY, partialTick);

        if (this.maxPage() > 0) {
            HudKit.centred(guiGraphics, this.font, Component.literal((this.page + 1) + " / " + (this.maxPage() + 1)),
                    x + ROW_X + ROW_W / 2, y + PAGE_Y + 3, HudKit.MUTED);
        }
        if (recipe == null) {
            List<FormattedCharSequence> lines = this.font.split(HudKit.hud(NONE), CARD_W - 16);
            for (int i = 0; i < lines.size(); i++) {
                guiGraphics.text(this.font, lines.get(i), x + CARD_X + 8, y + CARD_TOP + 10 + i * 10, HudKit.MUTED, false);
            }
            return;
        }
        this.drawDetails(guiGraphics, recipe, mouseX, mouseY);
    }

    /** A card: the fill inside a thin edge, both with smooth rounded corners, and a soft amber line on top. */
    private static void card(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        HudKit.roundRect(g, x, y, w, h, 3, CARD_EDGE);
        HudKit.roundRect(g, x + 1, y + 1, w - 2, h - 2, 2, CARD);
        g.fill(x + 4, y + 1, x + w - 4, y + 2, HudKit.fade(HudKit.ACCENT, 0.55F));
    }

    /** The stage: a dark well, lighter towards the bottom, the picked vehicle's model turning in it and its kind. */
    private void drawStage(GuiGraphicsExtractor g, VehicleAssemblyRecipe recipe, int mouseX, int mouseY) {
        Identifier vehicleId = recipe.resultVehicle();
        int x0 = this.leftPos + STAGE_X, y0 = this.topPos + STAGE_Y, x1 = x0 + STAGE_W, y1 = y0 + STAGE_H;
        HudKit.roundRect(g, x0, y0, STAGE_W, STAGE_H, 3, STAGE);
        g.fillGradient(x0 + 3, y0 + STAGE_H / 3, x1 - 3, y1 - 3, STAGE, STAGE_FLOOR);
        HudKit.text(g, this.font, this.categoryOf(recipe).label(), x0 + 5, y0 + 5, HudKit.FAINT);

        long now = net.minecraft.util.Util.getMillis();
        float seconds = this.lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
        this.lastFrame = now;
        if (!this.dragging && now >= this.dragPauseUntil) {
            this.spin = (this.spin + SPIN_RATE * seconds) % 360.0F;
        }
        VehicleEntity vehicle = this.preview(vehicleId);
        if (vehicle != null) {
            this.drawVehicle(g, vehicle, x0 + 2, y0 + 2, x1 - 2, y1 - 2);
        }
        if (mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1 && !this.dragging) {
            HudKit.rightAligned(g, this.font, DRAG, x1 - 5, y1 - 9, HudKit.FAINT);
        }
    }

    /**
     * The vehicle's own model through the GUI's entity renderer, turned by {@link #spin} and seen from a little above,
     * sized so that its parts (wings, rotors' hubs, barrels) stay inside the stage at every angle.
     */
    private void drawVehicle(GuiGraphicsExtractor g, VehicleEntity vehicle, int x0, int y0, int x1, int y1) {
        Minecraft mc = Minecraft.getInstance();
        EntityRenderer<? super VehicleEntity, ?> renderer = mc.getEntityRenderDispatcher().getRenderer(vehicle);
        EntityRenderState state;
        try {
            state = renderer.createRenderState(vehicle, 1.0F);
        } catch (RuntimeException e) {
            return;
        }
        state.shadowPieces.clear();
        state.outlineColor = 0;
        state.nameTag = null;
        state.lightCoords = 0xF000F0;

        AABB parts = vehicle.partBounds().move(-vehicle.getX(), -vehicle.getY(), -vehicle.getZ());
        Vector3f centre = new Vector3f((float) (parts.minX + parts.maxX) / 2.0F, (float) (parts.minY + parts.maxY) / 2.0F,
                (float) (parts.minZ + parts.maxZ) / 2.0F);
        float reach = (float) Math.sqrt(parts.getXsize() * parts.getXsize() + parts.getZsize() * parts.getZsize()) / 2.0F;
        float height = (float) parts.getYsize();
        float tilt = TILT * Mth.DEG_TO_RAD;
        float across = 2.0F * reach;
        float tall = height * Mth.cos(tilt) + across * Mth.sin(tilt);
        float scale = Math.min((x1 - x0 - 4) / Math.max(across, 0.5F), (y1 - y0 - 4) / Math.max(tall, 0.5F));

        // Negative about X looks down on it (positive showed every vehicle's underside).
        Quaternionf view = new Quaternionf().rotateX(-tilt);
        Quaternionf rotation = new Quaternionf().rotateZ(Mth.PI).mul(view).rotateY(this.spin * Mth.DEG_TO_RAD);
        Vector3f translation = rotation.transform(new Vector3f(centre)).negate();
        g.entity(state, scale, translation, rotation, view, x0, y0, x1, y1);
    }

    /** The model for the vehicle a recipe makes: a client-only entity, never added to the world. */
    @Nullable
    private VehicleEntity preview(Identifier vehicleId) {
        if (this.previews.containsKey(vehicleId)) {
            return this.previews.get(vehicleId);
        }
        VehicleEntity vehicle = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            try {
                EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(VehicleDataManager.get(vehicleId).defaults().entityType()));
                if (type.create(mc.level, EntitySpawnReason.LOAD) instanceof VehicleEntity made) {
                    made.snapTo(0.0D, 0.0D, 0.0D, 0.0F, 0.0F);
                    made.yRotO = 0.0F;
                    made.xRotO = 0.0F;
                    vehicle = made;
                }
            } catch (RuntimeException ignored) {
                // Drawn without a model rather than not at all.
            }
        }
        this.previews.put(vehicleId, vehicle);
        return vehicle;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.selected() != null && this.onStage(event.x(), event.y())) {
            this.dragging = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging) {
            this.spin = (this.spin - (float) dragX * DRAG_RATE) % 360.0F;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging) {
            this.dragging = false;
            // Left where the player turned it for a moment before it carries on turning.
            this.dragPauseUntil = net.minecraft.util.Util.getMillis() + 2500L;
            return true;
        }
        return super.mouseReleased(event);
    }

    private boolean onStage(double mouseX, double mouseY) {
        int x0 = this.leftPos + STAGE_X, y0 = this.topPos + STAGE_Y;
        return mouseX >= x0 && mouseX < x0 + STAGE_W && mouseY >= y0 && mouseY < y0 + STAGE_H;
    }

    /** Under the model: the vehicle's full name, its kind and whether it can be built, then each item it takes. */
    private void drawDetails(GuiGraphicsExtractor guiGraphics, VehicleAssemblyRecipe recipe, int mouseX, int mouseY) {
        int left = this.leftPos + CARD_X + 6;
        int right = this.leftPos + CARD_X + CARD_W - 6;
        int centre = this.leftPos + CARD_X + CARD_W / 2;
        int top = this.topPos + STAGE_Y + STAGE_H + 4;
        List<FormattedCharSequence> name = this.font.split(HudKit.hud(vehicleName(recipe.resultVehicle())), right - left);
        if (name.size() > 2) {
            name = name.subList(0, 2);
        }
        for (FormattedCharSequence line : name) {
            guiGraphics.text(this.font, line, centre - this.font.width(line) / 2, top, HudKit.TEXT, false);
            top += 10;
        }
        guiGraphics.fill(left, top + 1, right, top + 2, HudKit.DIVIDER);
        top += 4;

        Player player = this.minecraft == null ? null : this.minecraft.player;
        int rows = recipe.ingredients().size();
        int step = rows <= 0 ? 16 : Mth.clamp((this.topPos + ASSEMBLE_Y - 2 - top) / rows, 13, 17);
        int rowY = top;
        for (VehicleAssemblyRecipe.Ingredient ingredient : recipe.ingredients()) {
            Item item = BuiltInRegistries.ITEM.getValue(ingredient.item());
            ItemStack stack = new ItemStack(item);
            int have = player == null ? 0 : countItem(player, item);
            boolean enough = have >= ingredient.count();
            guiGraphics.item(stack, left, rowY);
            int textY = rowY + 6;
            Component count = Component.literal(Math.min(have, 9999) + "/" + ingredient.count());
            HudKit.text(guiGraphics, this.font, count, left + 20, textY, enough ? HudKit.OK : HudKit.BAD);
            int nameX = left + 20 + HudKit.width(this.font, count) + 5;
            String itemName = item.getName(stack).getString();
            int room = right - nameX;
            if (HudKit.width(this.font, Component.literal(itemName)) > room) {
                while (!itemName.isEmpty() && HudKit.width(this.font, Component.literal(itemName + "…")) > room) {
                    itemName = itemName.substring(0, itemName.length() - 1);
                }
                itemName = itemName.stripTrailing() + "…";
            }
            HudKit.text(guiGraphics, this.font, itemName, nameX, textY, HudKit.TEXT);
            if (mouseX >= left && mouseX < right && mouseY >= rowY && mouseY < rowY + step) {
                guiGraphics.setTooltipForNextFrame(this.font, stack, mouseX, mouseY);
            }
            rowY += step;
        }
    }

    /** Recessed wells under the inventory slots, as in the vehicle's hold. */
    private void drawSlotWells(GuiGraphicsExtractor guiGraphics) {
        for (int row = 0; row < 4; row++) {
            int slotY = this.topPos + (row < 3 ? SLOTS_Y + row * 18 : HOTBAR_Y);
            for (int col = 0; col < 9; col++) {
                int slotX = this.leftPos + 8 + col * 18;
                guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, WELL_RIM);
                guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, WELL);
                guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 1, 0x40000000);
            }
        }
    }

    /** Only the title: the cards speak for themselves, and the sort buttons sit where the inventory label went. */
    @Override
    protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        HudKit.text(guiGraphics, this.font, this.title, 8, 7, HudKit.TEXT);
    }

    private static Component vehicleName(Identifier vehicleId) {
        return Component.translatable("entity." + vehicleId.getNamespace() + "." + vehicleId.getPath());
    }

    /** The list's name for a vehicle: its short name ("BL-132"), or its full name where none is given. */
    private static Component shortName(Identifier vehicleId) {
        String key = "vehicle." + vehicleId.getNamespace() + "." + vehicleId.getPath() + ".short";
        return I18n.exists(key) ? Component.translatable(key) : vehicleName(vehicleId);
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

    /** For checks: every vehicle the table offers, and showing one as if picked (its tab cleared, its page turned to). */
    public List<Identifier> offeredVehicles() {
        return this.allRecipes.stream().map(VehicleAssemblyRecipe::resultVehicle).toList();
    }

    /** For checks: how far the model is turned, in degrees. */
    public float modelTurn() {
        return this.spin;
    }

    /** For checks: the vehicles in the list under the tab shown. */
    public List<Identifier> listedVehicles() {
        return this.recipes.stream().map(VehicleAssemblyRecipe::resultVehicle).toList();
    }

    public void show(Identifier vehicleId) {
        this.filter = null;
        this.recipes = this.filteredRecipes();
        for (int i = 0; i < this.recipes.size(); i++) {
            if (this.recipes.get(i).resultVehicle().equals(vehicleId)) {
                this.page = i / RECIPES_PER_PAGE;
                this.selectedId = this.recipes.get(i).id();
            }
        }
        this.refreshWidgets();
    }

    private enum Style { TAB, ROW, PRIMARY }

    /**
     * A flat button in the HUD style: a rounded chip with its label in the HUD font. A tab lights amber when it is the
     * filter shown; a vehicle row carries a dot for whether it can be built and an amber edge when picked; Assemble is
     * solid amber when it can be pressed.
     */
    private final class Flat extends Button {
        private final Style style;
        private boolean on;
        @Nullable
        private VehicleAssemblyRecipe recipe;

        Flat(int x, int y, int width, int height, Component message, Style style, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
            this.style = style;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            HudKit.useDensity(VehicleAssemblingScreen.this.minecraft.getWindow().getGuiScale());
            int x = this.getX(), y = this.getY(), w = this.getWidth(), h = this.getHeight();
            // Hover only: a clicked button keeps focus, and a focused row lit like a hovered one looked stuck.
            boolean hover = this.active && this.isHovered();
            var font = VehicleAssemblingScreen.this.font;
            int textY = y + (h - HudKit.CAP) / 2;
            switch (this.style) {
                case TAB -> {
                    HudKit.roundRect(g, x, y, w, h, 2, this.on ? CHIP_ON : hover ? CHIP_HOVER : CHIP_IDLE);
                    int colour = this.on ? HudKit.ACCENT : !this.active ? HudKit.FAINT : hover ? HudKit.TEXT : HudKit.MUTED;
                    HudKit.centred(g, font, this.fitted(w - 6), x + w / 2, textY, colour);
                }
                case ROW -> {
                    boolean picked = this.recipe != null && this.recipe.id().equals(VehicleAssemblingScreen.this.selectedId);
                    HudKit.roundRect(g, x, y, w, h, 2, picked ? CHIP_ON : hover ? CHIP_HOVER : CHIP_IDLE);
                    if (picked) {
                        g.fill(x, y + 3, x + 2, y + h - 3, HudKit.ACCENT);
                    }
                    boolean ready = this.recipe != null && VehicleAssemblingScreen.this.hasCost(this.recipe);
                    HudKit.dot(g, x + 6, textY, ready ? HudKit.OK : HudKit.BAD);
                    HudKit.text(g, font, this.fitted(w - 18), x + 13, textY, picked ? HudKit.TEXT : hover ? HudKit.TEXT : HudKit.MUTED);
                }
                case PRIMARY -> {
                    int fill = !this.active ? CHIP_IDLE : hover ? ACCENT_HOVER : HudKit.ACCENT;
                    HudKit.roundRect(g, x, y, w, h, 3, fill);
                    // Pressable it says what it does; short of items it says why not.
                    HudKit.centred(g, font, this.active ? this.getMessage() : MISSING, x + w / 2, textY, this.active ? ON_ACCENT : HudKit.BAD);
                }
            }
        }

        /** The label, cut short with an ellipsis if it is wider than {@code room}. */
        private Component fitted(int room) {
            var font = VehicleAssemblingScreen.this.font;
            String text = this.getMessage().getString();
            if (HudKit.width(font, Component.literal(text)) <= room) {
                return this.getMessage();
            }
            while (!text.isEmpty() && HudKit.width(font, Component.literal(text + "…")) > room) {
                text = text.substring(0, text.length() - 1);
            }
            return Component.literal(text.stripTrailing() + "…");
        }
    }

    /** The tabs, in the order the list runs: what drives, flies or sails, then what is set down and fires. */
    private enum Category {
        GROUND, AIR, SEA, ARTILLERY, DEFENCES;

        Component label() {
            return Component.translatable("gui.jeg.vehicle_assembling.category." + this.name().toLowerCase(java.util.Locale.ROOT));
        }
    }
}
