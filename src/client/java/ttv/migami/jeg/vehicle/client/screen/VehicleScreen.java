package ttv.migami.jeg.vehicle.client.screen;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.client.VehicleInputHandler;
import ttv.migami.jeg.vehicle.client.overlay.HudKit;
import ttv.migami.jeg.vehicle.menu.VehicleMenu;

public final class VehicleScreen extends AbstractContainerScreen<VehicleMenu> {
    private static final int TEXTURE_X_OFFSET = 8;

    /** The icons drawn on the support vehicle's module buttons. */
    private record ModuleIcon(ItemStack stack, int x, int y) {}
    private final java.util.List<ModuleIcon> moduleIcons = new java.util.ArrayList<>();

    public VehicleScreen(VehicleMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, menu.vehicleColumns() > 13 ? 320 : 222, menu.screenHeight());
        this.titleLabelX = 15;
        this.titleLabelY = 5;
        this.inventoryLabelX = menu.playerInventoryXOffset() + this.titleLabelX;
        this.inventoryLabelY = menu.playerInventoryY() - (menu.toolCount() > 0 ? 18 : 12);
    }

    @Override
    protected void init() {
        super.init();
        VehicleInputHandler.clearPendingVehicleInventoryClicks();
        // Aboard a field support vehicle, the hold has one button: the Fit-out console. Each module's own screen (a
        // furnace, the beacon) opens only by clicking it in the room, one at a time.
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getVehicle() instanceof ttv.migami.jeg.vehicle.entity.SupportVehicleEntity truck) {
            // One button beside the hold's top right corner: labelled when the screen has room, else an icon (name on hover).
            int bx = this.leftPos + this.imageWidth + 2, by = this.topPos, size = 20;
            int id = truck.getId();
            Component label = Component.translatable("gui.jeg.support_fit.title");
            int labelled = this.font.width(label) + 10;
            boolean room = this.width - bx - 2 >= labelled;
            this.moduleIcons.clear();
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(room ? label : Component.empty(),
                    b -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ttv.migami.jeg.vehicle.network.SupportModulePayload(id, -1)))
                    .bounds(bx, by, room ? labelled : size, size).tooltip(net.minecraft.client.gui.components.Tooltip.create(label)).build());
            if (!room) this.moduleIcons.add(new ModuleIcon(new ItemStack(Items.SMITHING_TABLE), bx + 2, by + 2));
        }
        // A base defence's automatic mode, on a switch beside the hold: it was only on a key nobody found (review,
        // 2026-11-18). On automatic it engages hostile mobs by itself and needs no ammunition.
        // Drawn in the HUD's own style (a card, a status dot, the HUD font), not a vanilla pixel button.
    }

    /** The automatic switch's area on screen (x, y, w, h), or null when this isn't a base defence. */
    @org.jetbrains.annotations.Nullable
    private int[] autoSwitch() {
        if (!(this.menu.vehicle() instanceof ttv.migami.jeg.vehicle.entity.AutoDefenceEntity defence)) {
            return null;
        }
        int w = Math.max(HudKit.width(this.font, Component.translatable("gui.jeg.auto_defence.on")),
                HudKit.width(this.font, Component.translatable("gui.jeg.auto_defence.off"))) + 22;
        return new int[]{this.leftPos + this.imageWidth + 4, this.topPos, w, 18};
    }

    private void drawAutoSwitch(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int[] r = this.autoSwitch();
        if (r == null || !(this.menu.vehicle() instanceof ttv.migami.jeg.vehicle.entity.AutoDefenceEntity defence)) {
            return;
        }
        boolean on = defence.automatic();
        boolean hover = mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
        HudKit.roundRect(g, r[0], r[1], r[2], r[3], 3, hover ? HudKit.ACCENT : CARD_EDGE);
        HudKit.roundRect(g, r[0] + 1, r[1] + 1, r[2] - 2, r[3] - 2, 2, CARD);
        HudKit.dot(g, r[0] + 7, r[1] + 6, on ? HudKit.OK : HudKit.FAINT);
        HudKit.text(g, this.font, autoLabel(defence), r[0] + 14, r[1] + 6, on ? HudKit.TEXT : HudKit.MUTED);
        if (hover) {
            g.setComponentTooltipForNextFrame(this.font, List.of(Component.translatable(
                    "gui.jeg.auto_defence.tooltip." + defence.vehicleDataId().getPath())), mouseX, mouseY);
        }
    }

    private static Component autoLabel(ttv.migami.jeg.vehicle.entity.AutoDefenceEntity defence) {
        return Component.translatable(defence.automatic() ? "gui.jeg.auto_defence.on" : "gui.jeg.auto_defence.off");
    }

    @Override
    public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Drawn at the GUI scale: the HUD font and shapes made for that many screen pixels per unit.
        HudKit.useDensity(this.minecraft.getWindow().getGuiScale());
        int x = this.leftPos;
        int y = this.topPos;
        // Two flat cards, the hold and the player's inventory, sized from their slots, each slot a recessed well.
        int[][] cards = this.cards();
        for (int[] card : cards) {
            if (card != null) card(guiGraphics, x + card[0], y + card[1], card[2] - card[0], card[3] - card[1]);
        }
        int[] bay = this.toolBay();
        boolean selected = this.menu.toolBaySelected();
        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            boolean tool = bay != null && i >= this.menu.toolStart() && i < this.menu.toolStart() + this.menu.toolCount();
            int sx = x + slot.x, sy = y + slot.y;
            // The bay feeding the vehicle's tool (the bulldozer's ripper) has warm wells; selected (clicked, so
            // shift-clicked blocks go into it) they glow brighter.
            guiGraphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, tool ? selected ? 0xFF9A7330 : 0xFF5A4724 : WELL_RIM);
            guiGraphics.fill(sx, sy, sx + 16, sy + 16, tool ? selected ? 0xFF3A2C12 : 0xFF221B10 : WELL);
            guiGraphics.fill(sx, sy, sx + 16, sy + 1, 0x40000000);
        }
        if (bay != null) {
            // The bay's outline sits on the slot borders, so it never covers the counts of the row above or the tab below.
            int left = x + bay[0], top = y + bay[1], right = x + bay[2], bottom = y + bay[3];
            int edge = selected ? 0xFFFFD46A : HudKit.ACCENT;
            // Selected, the outline doubles outwards so the bay stands out at a glance.
            int w = selected ? 2 : 1;
            guiGraphics.fill(left - w + 1, top - w + 1, right + w - 1, top + 1, edge);
            guiGraphics.fill(left - w + 1, bottom - 1, right + w - 1, bottom + w - 1, edge);
            guiGraphics.fill(left - w + 1, top, left + 1, bottom, edge);
            guiGraphics.fill(right - 1, top, right + w - 1, bottom, edge);
        }
        super.extractContents(guiGraphics, mouseX, mouseY, partialTick);
        this.drawAutoSwitch(guiGraphics, mouseX, mouseY);
        for (ModuleIcon icon : this.moduleIcons) guiGraphics.item(icon.stack(), icon.x(), icon.y());
        this.extractSeatPanel(guiGraphics, mouseX, mouseY);
        if (bay != null) {
            boolean hoveringEmpty = false;
            for (int i = 0; i < this.menu.toolCount(); i++) {
                Slot slot = this.menu.slots.get(this.menu.toolStart() + i);
                int sx = x + slot.x, sy = y + slot.y;
                if (!slot.hasItem()) {
                    // Ghost blocks show what goes here.
                    guiGraphics.item(new ItemStack(Items.STONE_BRICKS), sx, sy);
                    guiGraphics.fill(sx, sy, sx + 16, sy + 16, selected ? 0x903A2C12 : 0xC8221B10);
                    hoveringEmpty |= mouseX >= sx - 1 && mouseX < sx + 17 && mouseY >= sy - 1 && mouseY < sy + 17;
                }
            }
            // Named on a tab hanging from the bay's bottom-right corner (the panel ends just past the bay, so a label beside
            // it hung off the edge; under it, it straddled the seam to the player inventory). The tab stops above the
            // player's slots.
            Component name = Component.translatable("container.jeg.ripper_slot");
            int[] tab = this.toolBayTab(bay);
            int nameX = tab[0] + 4, nameY = tab[1] + 3;
            guiGraphics.fill(tab[0], tab[1], tab[2], tab[3], selected ? 0xFFFFD46A : HudKit.ACCENT);
            guiGraphics.fill(tab[0] + 1, tab[1], tab[2] - 1, tab[3] - 1, selected ? 0xFFE8A82C : 0xFF2A2214);
            HudKit.text(guiGraphics, this.font, name, nameX, nameY, selected ? 0xFF231704 : HudKit.ACCENT);
            hoveringEmpty |= mouseX >= tab[0] && mouseX < tab[2] && mouseY >= tab[1] && mouseY < tab[3];
            if (hoveringEmpty) {
                guiGraphics.setComponentTooltipForNextFrame(this.font, List.of(
                        Component.translatable("container.jeg.ripper_slot.title").withStyle(ChatFormatting.GOLD),
                        Component.translatable("container.jeg.ripper_slot.line1", KeyBindings.RELOAD.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY),
                        Component.translatable("container.jeg.ripper_slot.line2").withStyle(ChatFormatting.GRAY),
                        Component.translatable(selected ? "container.jeg.ripper_slot.selected" : "container.jeg.ripper_slot.select")
                                .withStyle(selected ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY)), mouseX, mouseY);
            }
        }
    }

    private static final int CARD = 0xF2151A1F, CARD_EDGE = 0xFF2B323A, WELL_RIM = 0xFF262D35, WELL = 0xFF101418;

    /** A card: the fill inside a thin edge, both with smooth rounded corners (HudKit), and a soft amber line on top. */
    static void card(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        HudKit.roundRect(g, x, y, w, h, 3, CARD_EDGE);
        HudKit.roundRect(g, x + 1, y + 1, w - 2, h - 2, 2, CARD);
        g.fill(x + 4, y + 1, x + w - 4, y + 2, HudKit.fade(HudKit.ACCENT, 0.55F));
    }

    /** The hold's and the player inventory's cards (left, top, right, bottom, relative to the screen), from their slots. */
    private int[][] cards() {
        int[] hold = this.slotArea(false), pack = this.slotArea(true);
        // With a tool bay, the hold card reaches lower for the bay's name tab.
        int[] holdCard = hold == null ? null : new int[]{hold[0] - 6, hold[1] - 16, hold[2] + 6, hold[3] + (this.toolBay() != null ? 14 : 6)};
        int[] packCard = pack == null ? null : new int[]{pack[0] - 6, pack[1] - 16, pack[2] + 6, pack[3] + 6};
        if (holdCard != null && packCard != null && holdCard[3] > packCard[1] - 3) {
            // Tight layouts: the two cards meet with a small gap rather than overlapping.
            int mid = (holdCard[3] - 4 + pack[1] - 12) / 2;
            holdCard[3] = mid - 1;
            packCard[1] = mid + 2;
        }
        return new int[][]{holdCard, packCard};
    }

    /** The bounds of the hold's slots ({@code player} false) or the player's, relative to the screen, or null. */
    @org.jetbrains.annotations.Nullable
    private int[] slotArea(boolean player) {
        int[] area = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        boolean any = false;
        for (Slot slot : this.menu.slots) {
            if ((slot.container instanceof Inventory) != player) continue;
            any = true;
            area[0] = Math.min(area[0], slot.x - 1);
            area[1] = Math.min(area[1], slot.y - 1);
            area[2] = Math.max(area[2], slot.x + 17);
            area[3] = Math.max(area[3], slot.y + 17);
        }
        return any ? area : null;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int[][] cards = this.cards();
        if (cards[0] != null) HudKit.text(guiGraphics, this.font, this.title, cards[0][0] + 6, cards[0][1] + 6, HudKit.TEXT);
        if (cards[1] != null) HudKit.text(guiGraphics, this.font, this.playerInventoryTitle, cards[1][0] + 6, cards[1][1] + 6, HudKit.MUTED);
    }

    /** One seat in the side panel, with its eject button's box when a mob sits there (a player never gets one). */
    public record SeatRow(int seat, Component label, @org.jetbrains.annotations.Nullable Entity occupant, int x, int y, int ejectX, int ejectY) {
        public boolean ejectable() {
            return this.occupant != null && !(this.occupant instanceof Player);
        }

        boolean overEject(double mouseX, double mouseY) {
            return this.ejectable() && mouseX >= this.ejectX && mouseX < this.ejectX + EJECT_SIZE && mouseY >= this.ejectY && mouseY < this.ejectY + EJECT_SIZE;
        }
    }

    private static final int SEAT_ROW_HEIGHT = 12;
    private static final int EJECT_SIZE = 9;
    private static final int SEAT_PANEL_MAX_WIDTH = 150;

    @org.jetbrains.annotations.Nullable
    private VehicleEntity seatVehicle() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || this.menu.vehicleId() < 0) {
            return null;
        }
        return minecraft.level.getEntity(this.menu.vehicleId()) instanceof VehicleEntity vehicle ? vehicle : null;
    }

    /**
     * Who sits in each of the vehicle's seats, laid out in a panel left of the hold (or right of it when the left has no
     * room). Rebuilt every frame, so it follows mobs and players getting in and out while the screen is open.
     */
    public List<SeatRow> seatRows() {
        VehicleEntity vehicle = this.seatVehicle();
        if (vehicle == null) {
            return List.of();
        }
        int seatCount = vehicle.vehicleData().defaults().seats().size();
        VehicleType type = vehicle.vehicleData().defaults().vehicleType();
        boolean flies = type == VehicleType.HELICOPTER || type == VehicleType.AIRCRAFT;
        java.util.List<Component> labels = new java.util.ArrayList<>();
        java.util.List<Entity> occupants = new java.util.ArrayList<>();
        int textWidth = HudKit.width(this.font, Component.translatable("gui.jeg.vehicle_seats.title"));
        var turret = vehicle.vehicleData().defaults().turret();
        for (int seat = 0; seat < seatCount; seat++) {
            // A turret with its own seat (the BMP-2's, the speedboat's bow gun) names it; a driver who works the turret
            // stays "Driver".
            Component name = vehicle.isDriverSeat(seat)
                    ? Component.translatable(flies ? "gui.jeg.vehicle_seats.pilot" : "gui.jeg.vehicle_seats.driver")
                    : turret.enabled() && seat == turret.seatIndex() ? Component.translatable("gui.jeg.vehicle_seats.gunner")
                    : Component.translatable("gui.jeg.vehicle_seats.seat", seat + 1);
            Entity occupant = vehicle.passengerForSeat(seat);
            Component who = occupant != null ? occupant.getDisplayName() : Component.translatable("gui.jeg.vehicle_seats.empty");
            Component label = Component.empty().append(name).append(": ").append(who);
            labels.add(label);
            occupants.add(occupant);
            textWidth = Math.max(textWidth, HudKit.width(this.font, label));
        }
        int panelWidth = Math.min(SEAT_PANEL_MAX_WIDTH, textWidth + EJECT_SIZE + 12);
        int left = this.leftPos + TEXTURE_X_OFFSET - 4 - panelWidth;
        if (left < 2 && this.leftPos + this.imageWidth + 4 + panelWidth <= this.width - 2) {
            left = this.leftPos + this.imageWidth + 4;
        }
        left = Math.max(2, left);
        int top = this.topPos + 4;
        java.util.List<SeatRow> rows = new java.util.ArrayList<>();
        for (int seat = 0; seat < seatCount; seat++) {
            int y = top + 14 + seat * SEAT_ROW_HEIGHT;
            rows.add(new SeatRow(seat, labels.get(seat), occupants.get(seat), left + 4, y, left + panelWidth - EJECT_SIZE - 3, y - 1));
        }
        return rows;
    }

    private void extractSeatPanel(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        List<SeatRow> rows = this.seatRows();
        if (rows.isEmpty()) {
            return;
        }
        int left = rows.getFirst().x() - 4, right = rows.getFirst().ejectX() + EJECT_SIZE + 3;
        int top = rows.getFirst().y() - 14, bottom = rows.getLast().y() + SEAT_ROW_HEIGHT;
        card(guiGraphics, left, top, right - left, bottom - top);
        HudKit.text(guiGraphics, this.font, Component.translatable("gui.jeg.vehicle_seats.title"), left + 5, top + 5, HudKit.TEXT);
        int textRight = right - EJECT_SIZE - 6;
        for (SeatRow row : rows) {
            String text = row.label().getString();
            while (!text.isEmpty() && HudKit.width(this.font, Component.literal(text)) > textRight - row.x()) {
                text = text.substring(0, text.length() - 1);
            }
            HudKit.text(guiGraphics, this.font, Component.literal(text), row.x(), row.y() + 1, row.occupant() == null ? HudKit.FAINT : HudKit.TEXT);
            if (row.ejectable()) {
                boolean hover = row.overEject(mouseX, mouseY);
                HudKit.panel(guiGraphics, row.ejectX(), row.ejectY(), EJECT_SIZE, EJECT_SIZE, hover ? 0xFFE0483E : 0xFF8A2E2A);
                HudKit.text(guiGraphics, this.font, "×", row.ejectX() + 3, row.ejectY() + 2, 0xFFFFFFFF);
                if (hover) {
                    guiGraphics.setComponentTooltipForNextFrame(this.font,
                            List.of(Component.translatable("gui.jeg.vehicle_seats.eject", row.occupant().getDisplayName())), mouseX, mouseY);
                }
            }
        }
    }

    /** The label tab under the tool bay (left, top, right, bottom on screen), from the bay's own area. */
    private int[] toolBayTab(int[] bay) {
        int x = this.leftPos, y = this.topPos;
        Component name = Component.translatable("container.jeg.ripper_slot");
        // Hanging from the bay's bottom-right corner (the panel ends just past the bay, so a label beside it hung off the
        // edge; under it, it straddled the seam to the player inventory). The tab stops above the player's slots.
        int nameX = x + bay[2] - 4 - HudKit.width(this.font, name), nameY = y + bay[3] + 3;
        int bottom = Math.min(nameY + HudKit.CAP + 3, y + this.menu.playerInventoryY() - 2);
        return new int[]{nameX - 4, y + bay[3] - 1, x + bay[2], bottom};
    }

    /**
     * Clicks the screen handles itself: a seat's eject button (a mob put off the vehicle), then the tool bay (its tab, its
     * outline or one of its empty slots, with nothing in hand) to select it, so shift-clicked blocks from the player's
     * inventory go into it instead of the hold. A second click on the bay deselects it.
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int[] auto = this.autoSwitch();
        if (event.button() == 0 && auto != null && this.minecraft != null && this.minecraft.gameMode != null
                && event.x() >= auto[0] && event.x() < auto[0] + auto[2] && event.y() >= auto[1] && event.y() < auto[1] + auto[3]) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, VehicleMenu.BUTTON_TOGGLE_AUTO);
            return true;
        }
        if (event.button() == 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            for (SeatRow row : this.seatRows()) {
                if (row.overEject(event.x(), event.y())) {
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, row.seat());
                    return true;
                }
            }
        }
        int[] bay = this.toolBay();
        if (event.button() == 0 && bay != null && this.menu.getCarried().isEmpty() && this.minecraft != null
                && this.minecraft.gameMode != null && this.minecraft.player != null) {
            double mx = event.x(), my = event.y();
            int[] tab = this.toolBayTab(bay);
            boolean onTab = mx >= tab[0] && mx < tab[2] && my >= tab[1] && my < tab[3];
            boolean inBay = mx >= this.leftPos + bay[0] && mx < this.leftPos + bay[2] && my >= this.topPos + bay[1] && my < this.topPos + bay[3];
            Slot slot = this.hoveredSlot;
            boolean onEmptyBaySlot = inBay && (slot == null || slot.index >= this.menu.toolStart()
                    && slot.index < this.menu.toolStart() + this.menu.toolCount() && !slot.hasItem());
            if (onTab || onEmptyBaySlot) {
                this.menu.clickMenuButton(this.minecraft.player, VehicleMenu.BUTTON_TOGGLE_TOOL_BAY);
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, VehicleMenu.BUTTON_TOGGLE_TOOL_BAY);
                this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** The tool bay's slot area relative to the screen (left, top, right, bottom), or null. */
    @org.jetbrains.annotations.Nullable
    private int[] toolBay() {
        if (this.menu.toolCount() <= 0 || this.menu.toolStart() + this.menu.toolCount() > this.menu.slots.size()) return null;
        int[] area = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int i = 0; i < this.menu.toolCount(); i++) {
            Slot slot = this.menu.slots.get(this.menu.toolStart() + i);
            area[0] = Math.min(area[0], slot.x - 1);
            area[1] = Math.min(area[1], slot.y - 1);
            area[2] = Math.max(area[2], slot.x + 17);
            area[3] = Math.max(area[3], slot.y + 17);
        }
        return area;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.keyInventory.matches(event)) {
            if (VehicleInputHandler.shouldIgnoreVehicleInventoryKey()) {
                return true;
            }
            VehicleInputHandler.syncMouseToCurrentCursor();
            this.minecraft.player.closeContainer();
            this.minecraft.setScreen(null);
            VehicleInputHandler.clearPendingVehicleInventoryClicks();
            return true;
        }
        if (KeyBindings.VEHICLE_PLAYER_INVENTORY.matches(event)) {
            VehicleInputHandler.syncMouseToCurrentCursor();
            this.minecraft.player.closeContainer();
            this.minecraft.setScreen(new InventoryScreen(this.minecraft.player));
            VehicleInputHandler.clearPendingPlayerInventoryClicks();
            return true;
        }
        return super.keyPressed(event);
    }
}
