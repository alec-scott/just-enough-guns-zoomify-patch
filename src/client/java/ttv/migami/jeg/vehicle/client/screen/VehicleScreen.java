package ttv.migami.jeg.vehicle.client.screen;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.client.VehicleInputHandler;
import ttv.migami.jeg.vehicle.menu.VehicleMenu;

public final class VehicleScreen extends AbstractContainerScreen<VehicleMenu> {
    private static final int TEXTURE_X_OFFSET = 8;
    private static final Identifier MINI = Reference.id("textures/gui/vehicle/inventory/mini.png");
    private static final Identifier SMALL = Reference.id("textures/gui/vehicle/inventory/small.png");
    private static final Identifier CABIN = Reference.id("textures/gui/vehicle/inventory/cabin.png");
    private static final Identifier MEDIUM = Reference.id("textures/gui/vehicle/inventory/medium.png");
    private static final Identifier LARGE = Reference.id("textures/gui/vehicle/inventory/large.png");
    private static final Identifier HUGE = Reference.id("textures/gui/vehicle/inventory/huge.png");
    private static final Identifier PLAYER_INVENTORY = Reference.id("textures/gui/vehicle/inventory/player_inventory.png");

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
    }

    @Override
    public void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos;
        int y = this.topPos;
        Identifier texture = this.vehicleInventoryTexture();
        if (texture != null) {
            int textureSize = this.menu.vehicleColumns() > 13 ? 328 : 256;
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + TEXTURE_X_OFFSET, y, 0, 0, this.imageWidth, this.imageHeight, textureSize, textureSize);
        } else {
            guiGraphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xDD20252B);
        }
        if (this.menu.toolCount() > 0) {
            // Extend the texture's plain top border into a heading strip, below the bay's label. The original
            // panel has only eight pixels above its slots, so "Inventory" straddled its border and got clipped.
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, PLAYER_INVENTORY,
                    x + TEXTURE_X_OFFSET + this.menu.playerInventoryXOffset(), y + this.menu.playerInventoryY() - 20,
                    0, 0, 175, 12, 175, 5, 256, 256);
        }
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, PLAYER_INVENTORY, x + TEXTURE_X_OFFSET + this.menu.playerInventoryXOffset(), y + this.menu.playerInventoryY() - 8, 0, 0, 175, 90, 256, 256);
        int[] bay = this.toolBay();
        if (bay != null) {
            // The bay feeding the vehicle's tool (the bulldozer's ripper): tinted wells in a 1-pixel amber outline drawn on
            // the slot borders, so it never covers the counts of the row above or the label below.
            for (int i = 0; i < this.menu.toolCount(); i++) {
                Slot slot = this.menu.slots.get(this.menu.toolStart() + i);
                guiGraphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF6E5A3A);
            }
            int left = x + bay[0], top = y + bay[1], right = x + bay[2], bottom = y + bay[3];
            guiGraphics.fill(left, top, right, top + 1, 0xFFE0A030);
            guiGraphics.fill(left, bottom - 1, right, bottom, 0xFFE0A030);
            guiGraphics.fill(left, top, left + 1, bottom, 0xFFE0A030);
            guiGraphics.fill(right - 1, top, right, bottom, 0xFFE0A030);
        }
        super.extractContents(guiGraphics, mouseX, mouseY, partialTick);
        if (bay != null) {
            boolean hoveringEmpty = false;
            for (int i = 0; i < this.menu.toolCount(); i++) {
                Slot slot = this.menu.slots.get(this.menu.toolStart() + i);
                int sx = x + slot.x, sy = y + slot.y;
                if (!slot.hasItem()) {
                    // Ghost blocks show what goes here.
                    guiGraphics.item(new ItemStack(Items.STONE_BRICKS), sx, sy);
                    guiGraphics.fill(sx, sy, sx + 16, sy + 16, 0xC055432A);
                    hoveringEmpty |= mouseX >= sx - 1 && mouseX < sx + 17 && mouseY >= sy - 1 && mouseY < sy + 17;
                }
            }
            // Named on a tab hanging from the bay's bottom-right corner (the panel ends just past the bay, so a label beside
            // it hung off the edge; under it, it straddled the seam to the player inventory). The tab stops above the
            // player's slots.
            Component name = Component.translatable("container.jeg.ripper_slot");
            int nameX = x + bay[2] - 4 - this.font.width(name), nameY = y + bay[3] + 2;
            int tabLeft = nameX - 4, tabBottom = Math.min(nameY + this.font.lineHeight + 1, y + this.menu.playerInventoryY() - 2);
            guiGraphics.fill(tabLeft, y + bay[3] - 1, x + bay[2], tabBottom, 0xFFE0A030);
            guiGraphics.fill(tabLeft + 1, y + bay[3] - 1, x + bay[2] - 1, tabBottom - 1, 0xFF4A3A22);
            guiGraphics.text(this.font, name, nameX, nameY, 0xFFF0C060, false);
            hoveringEmpty |= mouseX >= nameX && mouseX < x + bay[2] && mouseY >= nameY && mouseY < nameY + this.font.lineHeight;
            if (hoveringEmpty) {
                guiGraphics.setComponentTooltipForNextFrame(this.font, List.of(
                        Component.translatable("container.jeg.ripper_slot.title").withStyle(ChatFormatting.GOLD),
                        Component.translatable("container.jeg.ripper_slot.line1", KeyBindings.RELOAD.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY),
                        Component.translatable("container.jeg.ripper_slot.line2").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
            }
        }
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

    private Identifier vehicleInventoryTexture() {
        if (this.menu.vehicleRows() <= 0) {
            return null;
        }
        if (this.menu.vehicleColumns() == 17) {
            return HUGE;
        }
        if (this.menu.vehicleColumns() == 13) {
            return LARGE;
        }
        return switch (this.menu.vehicleRows()) {
            case 1 -> MINI;
            case 3 -> SMALL;
            case 5 -> CABIN;
            case 6 -> MEDIUM;
            default -> null;
        };
    }
}
