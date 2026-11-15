package ttv.migami.jeg.vehicle.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.menu.SupportFitMenu;
import ttv.migami.jeg.vehicle.network.SupportModulePayload;
import ttv.migami.jeg.vehicle.support.SupportModules;

import static ttv.migami.jeg.vehicle.client.screen.ConsoleStyle.*;

/**
 * The FSV-6's support console, opened from its panel on the room's front wall (right of the aisle) and from the hold.
 * Drawn as equipment ({@link ConsoleStyle}):
 * <ul>
 *     <li>left, the fit-out plan: the vehicle from above, cab at the top, with every fitting slot where it is: the bunks
 *     at the front and three bays down each side, the lamp hook mid-aisle (glowing when a lamp is fitted), the roof
 *     beacon mount over the rear, and the outside lockers beyond the walls. Empty slots show a faded picture of what
 *     they take and say so on hover;</li>
 *     <li>right, a green phosphor status CRT: power, battery, body, room light, beacon, bunks and tow, then the fitted
 *     bay modules (a burning furnace shows a lit lamp);</li>
 *     <li>a push-button to the hold.</li>
 * </ul>
 * The pack's Inventory Profiles Next is told to leave it alone: its sort buttons would move fittings and break the console.
 * Modules are not opened from here: each opens by clicking it in the room, and the beacon at its own terminal.
 */
@org.anti_ad.mc.ipn.api.IPNIgnore
public final class SupportFitScreen extends AbstractContainerScreen<SupportFitMenu> {
    private static final int PLAN_WIDTH = 176, DISPLAY_X = 184, DISPLAY_WIDTH = 140, WIDTH = 332;
    private static final int ROOM_X0 = 38, ROOM_X1 = 138, ROOM_Y0 = 33, ROOM_Y1 = 122;

    /** A console key: its area, what it opens (-2 the hold) and its label. */
    private record Key(int x, int y, int w, int h, int slot, Component label) {
        boolean over(double mx, double my) {
            return mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
        }
    }

    private final List<Key> keys = new ArrayList<>();

    public SupportFitScreen(SupportFitMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, SupportFitMenu.HEIGHT);
        this.inventoryLabelY = -10000;
        this.titleLabelY = -10000;
    }

    private SupportVehicleEntity truck() {
        if (this.minecraft == null || this.minecraft.level == null) return null;
        return this.minecraft.level.getEntity(this.menu.vehicleId()) instanceof SupportVehicleEntity truck ? truck : null;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos, y = this.topPos;
        long millis = Util.getMillis();
        SupportVehicleEntity truck = this.truck();
        chassis(g, this.font, x, y, this.imageWidth, this.imageHeight, Component.translatable("gui.jeg.support_console.title"), "SER 0417");

        // ---- Left: the fit-out plan.
        plate(g, x, y, PLAN_WIDTH, SupportFitMenu.PLAYER_Y - 4);
        this.plan(g, x, y, truck, millis);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) well(g, x + 8 + column * 18, y + SupportFitMenu.PLAYER_Y + row * 18, 0xFF2E342C);
        }
        for (int column = 0; column < 9; column++) well(g, x + 8 + column * 18, y + SupportFitMenu.HOTBAR_Y, 0xFF3A4230);

        // ---- Right: the status CRT.
        int dx = x + DISPLAY_X, dy = y + 4, dw = DISPLAY_WIDTH, dh = 132;
        crt(g, dx, dy, dw, dh, millis);
        // (The CRT's top right stays clear: Inventory Profiles Next draws its buttons there in the pack.)
        glow(g, this.font, "> " + Component.translatable("gui.jeg.support_console.status").getString() + ((millis / 500) % 2 == 0 ? "_" : ""), dx + 5, dy + 4, PHOSPHOR);
        g.fill(dx + 4, dy + 14, dx + dw - 4, dy + 15, PHOSPHOR_DIM);
        this.keys.clear();
        ItemStack iconTip = ItemStack.EMPTY;
        int row = dy + 19;
        if (truck == null) {
            glow(g, this.font, Component.translatable("gui.jeg.support_console.no_link"), dx + 5, row, PHOSPHOR_DIM);
        } else {
            boolean generator = truck.generatorRunning(), diesel = truck.engineRunning();
            this.line(g, dx, row, "gui.jeg.support_console.power",
                    Component.translatable(generator ? "hud.jeg.support.generator" : diesel ? "hud.jeg.support.engine" : "hud.jeg.support.off"),
                    generator || diesel ? LED_GREEN : LED_OFF, generator || diesel);
            row += 12;
            float charge = truck.maxVehicleEnergy() <= 0 ? 0 : truck.vehicleEnergy() / (float) truck.maxVehicleEnergy();
            g.text(this.font, Component.translatable("gui.jeg.support_console.battery"), dx + 5, row, PHOSPHOR_DIM, false);
            segments(g, dx + 52, row + 1, dw - 86, 6, charge, 12, charge < .15F ? LED_AMBER : PHOSPHOR);
            glow(g, this.font, Math.round(charge * 100) + "%", dx + dw - 30, row, PHOSPHOR);
            row += 12;
            float progress = truck.deployProgress();
            Component body = truck.isDeployed() ? Component.translatable("hud.jeg.support.deployed")
                    : truck.isPacked() ? Component.translatable("hud.jeg.support.packed")
                    : Component.translatable(truck.deployTarget() ? "hud.jeg.support.deploying" : "hud.jeg.support.packing", Math.round(progress * 100));
            this.line(g, dx, row, "gui.jeg.support_console.body", body, truck.isDeployed() ? LED_GREEN : LED_AMBER, !truck.isPacked());
            row += 12;
            int light = SupportModules.lampLight(truck.fittedForDisplay(SupportModules.LAMP));
            this.line(g, dx, row, "gui.jeg.support_console.light", light > 0 ? Component.literal(String.valueOf(light))
                    : Component.translatable("gui.jeg.support_console.no_lamp"), LED_AMBER, light > 0 && generator);
            row += 12;
            int level = truck.beaconLevelForDisplay();
            boolean fitted = !truck.fittedForDisplay(SupportModules.ROOF).isEmpty();
            g.text(this.font, Component.translatable("gui.jeg.support_console.beacon"), dx + 5, row, PHOSPHOR_DIM, false);
            if (!fitted) {
                glow(g, this.font, Component.translatable("gui.jeg.support_console.beacon_none"), dx + 52, row, PHOSPHOR_DIM);
            } else {
                for (int k = 0; k < 4; k++) g.fill(dx + 52 + k * 7, row + 1, dx + 57 + k * 7, row + 7, level > k ? PHOSPHOR : 0xFF0F2A18);
                var effect = truck.beaconEffectForDisplay() > 0 ? BeaconMenu.decodeEffect(truck.beaconEffectForDisplay()) : null;
                if (effect != null) glow(g, this.font, effect.value().getDisplayName(), dx + 82, row, PHOSPHOR);
            }
            led(g, dx + dw - 10, row + 1, truck.beaconLit() ? LED_GREEN : LED_AMBER, truck.beaconLit() || (fitted && level > 0));
            row += 12;
            g.text(this.font, Component.translatable("gui.jeg.support_console.bunks"), dx + 5, row, PHOSPHOR_DIM, false);
            for (int side = 0; side < 2; side++) {
                ItemStack bed = truck.fittedForDisplay(side == 0 ? SupportModules.BUNK_LEFT : SupportModules.BUNK_RIGHT);
                int sx = dx + 52 + side * 14;
                g.fill(sx, row, sx + 10, row + 8, PHOSPHOR_DIM);
                g.fill(sx + 1, row + 1, sx + 9, row + 7, bed.isEmpty() ? CRT : bedColour(bed));
            }
            row += 12;
            var towed = truck.towedEntityId() >= 0 ? this.minecraft.level.getEntity(truck.towedEntityId()) : null;
            this.line(g, dx, row, "hud.jeg.support.towing", towed == null ? Component.literal("-") : towed.getType().getDescription(), LED_AMBER, towed != null);
            row += 14;
            // ---- The fitted bay modules (each opens by clicking it in the room).
            glow(g, this.font, Component.translatable("gui.jeg.support_console.modules"), dx + 5, row, PHOSPHOR);
            row += 10;
            // Cells 19 px on a 20 px step: the row ends at x 308, clear of the sort button Inventory Profiles Next
            // (in the yftm pack) puts at the screen's right edge level with the inventory label.
            for (int bay = 0; bay < SupportModules.WALL_BAYS; bay++) {
                int ix = dx + 5 + bay * 20, iy = row;
                ItemStack module = truck.fittedForDisplay(bay);
                g.fill(ix, iy, ix + 19, iy + 19, PHOSPHOR_DIM);
                g.fill(ix + 1, iy + 1, ix + 18, iy + 18, CRT);
                if (!module.isEmpty()) {
                    g.item(module, ix + 1, iy + 1);
                    if (mouseX >= ix && mouseX < ix + 19 && mouseY >= iy && mouseY < iy + 19) iconTip = module;
                    if (truck.furnaceLitForDisplay(bay)) led(g, ix + 13, iy + 1, LED_AMBER, true);
                }
            }
        }
        // ---- Console keys and notes under the CRT.
        int kx = x + DISPLAY_X, ky = y + 144, kw = DISPLAY_WIDTH, kh = 18;
        boolean hover = mouseX >= kx && mouseX < kx + kw && mouseY >= ky && mouseY < ky + kh;
        Component holdLabel = Component.translatable("gui.jeg.support_fit.open_hold");
        button(g, this.font, kx, ky, kw, kh, holdLabel, hover, LED_GREEN);
        this.keys.add(new Key(kx, ky, kw, kh, -2, holdLabel));
        g.text(this.font, Component.translatable("gui.jeg.support_console.beacon_hint"), kx, y + 170, STENCIL_DIM, false);
        g.text(this.font, Component.translatable("gui.jeg.support_console.beacon_hint2"), kx, y + 180, STENCIL_DIM, false);
        g.text(this.font, Component.translatable("gui.jeg.support_console.footer"), kx, y + this.imageHeight - 12, STENCIL_DIM, false);
        super.extractContents(g, mouseX, mouseY, partialTick);
        // Tooltips: what an empty fitting well takes; a module icon's name.
        for (int slot = 0; slot < SupportModules.FIT_SLOTS; slot++) {
            int[] at = SupportFitMenu.POSITIONS[slot];
            if (this.menu.slots.get(slot).hasItem()) continue;
            if (mouseX >= x + at[0] && mouseX < x + at[0] + 16 && mouseY >= y + at[1] && mouseY < y + at[1] + 16) {
                g.setComponentTooltipForNextFrame(this.font, List.of(Component.translatable("gui.jeg.support_fit.takes." + takes(slot))), mouseX, mouseY);
            }
        }
        if (!iconTip.isEmpty()) {
            g.setComponentTooltipForNextFrame(this.font, List.of(iconTip.getHoverName(), Component.translatable("gui.jeg.support_console.open_hint")), mouseX, mouseY);
        }
    }

    /** The vehicle from above, cab at the top, with the fitting slots' wells where the fittings are. */
    private void plan(GuiGraphicsExtractor g, int x, int y, SupportVehicleEntity truck, long millis) {
        int line = 0xFF6F7A55, floor = 0xFF242A1F, cab = 0xFF2C3325;
        glow(g, this.font, Component.translatable("gui.jeg.support_fit.title"), x + 6, y + 4, AMBER);
        // Wheels: steer axle under the cab, the tandem under the rear.
        int wheel = 0xFF0D0F0C;
        for (int[] w : new int[][]{{ROOM_X0 - 6, 12, 6, 12}, {ROOM_X1, 12, 6, 12}, {ROOM_X0 - 6, 84, 6, 11}, {ROOM_X1, 84, 6, 11},
                {ROOM_X0 - 6, 98, 6, 11}, {ROOM_X1, 98, 6, 11}}) {
            g.fill(x + w[0], y + w[1], x + w[0] + w[2], y + w[1] + w[3], wheel);
            for (int k = 1; k < w[3]; k += 3) g.fill(x + w[0], y + w[1] + k, x + w[0] + w[2], y + w[1] + k + 1, 0xFF262924);
        }
        // Cab: rounded nose, windscreen, two seats, the wheel.
        g.fill(x + ROOM_X0 + 4, y + 4, x + ROOM_X1 - 4, y + ROOM_Y0 - 3, line);
        g.fill(x + ROOM_X0 + 5, y + 5, x + ROOM_X1 - 5, y + ROOM_Y0 - 4, cab);
        g.fill(x + ROOM_X0 + 10, y + 6, x + ROOM_X1 - 10, y + 9, 0xFF5D7C86);
        for (int sx : new int[]{ROOM_X0 + 18, ROOM_X1 - 32}) {
            g.fill(x + sx, y + 14, x + sx + 14, y + 25, 0xFF1A1E16);
            g.fill(x + sx + 1, y + 15, x + sx + 13, y + 24, 0xFF353C2C);
        }
        g.fill(x + ROOM_X1 - 29, y + 11, x + ROOM_X1 - 21, y + 13, 0xFF0D0F0C);
        // Body: walls, floor, the partition behind the cab with its two panels (beacon terminal left, console right).
        g.fill(x + ROOM_X0, y + ROOM_Y0, x + ROOM_X1, y + ROOM_Y1, line);
        g.fill(x + ROOM_X0 + 2, y + ROOM_Y0 + 2, x + ROOM_X1 - 2, y + ROOM_Y1 - 2, floor);
        for (int fy = y + ROOM_Y0 + 4; fy < y + ROOM_Y1 - 3; fy += 4) g.fill(x + 64, fy, x + 112, fy + 1, 0xFF20251B);
        g.fill(x + 70, y + ROOM_Y0 + 2, x + 82, y + ROOM_Y0 + 4, AMBER);
        g.fill(x + 94, y + ROOM_Y0 + 2, x + 106, y + ROOM_Y0 + 4, PHOSPHOR_DIM);
        // Slide-out seams and the rear ramp.
        g.fill(x + 64, y + ROOM_Y0 + 2, x + 65, y + ROOM_Y1 - 2, 0xFF384030);
        g.fill(x + 111, y + ROOM_Y0 + 2, x + 112, y + ROOM_Y1 - 2, 0xFF384030);
        for (int k = 0; k < 3; k++) g.fill(x + 70, y + ROOM_Y1 - 2 + k * 2, x + 106, y + ROOM_Y1 - 1 + k * 2, 0xFF4A533A);
        // The lamp's light, round its hook, when one is fitted.
        boolean lamp = truck != null && !truck.fittedForDisplay(SupportModules.LAMP).isEmpty();
        int[] lampAt = SupportFitMenu.POSITIONS[SupportModules.LAMP];
        if (lamp) {
            for (int r = 18; r > 8; r -= 3) {
                g.fill(x + lampAt[0] + 8 - r, y + lampAt[1] + 8 - r, x + lampAt[0] + 8 + r, y + lampAt[1] + 8 + r, 0x0CFFC27A);
            }
        }
        // The roof hatch round the beacon mount (dashed), the lockers' outlines under the body.
        int[] roof = SupportFitMenu.POSITIONS[SupportModules.ROOF];
        for (int k = 0; k < 24; k += 4) {
            g.fill(x + roof[0] - 4 + k, y + roof[1] - 4, x + roof[0] - 2 + k, y + roof[1] - 3, STENCIL_DIM);
            g.fill(x + roof[0] - 4 + k, y + roof[1] + 19, x + roof[0] - 2 + k, y + roof[1] + 20, STENCIL_DIM);
        }
        for (int slot : new int[]{SupportModules.LOCKER_LEFT, SupportModules.LOCKER_RIGHT}) {
            int[] at = SupportFitMenu.POSITIONS[slot];
            int wall = slot == SupportModules.LOCKER_LEFT ? ROOM_X0 : ROOM_X1;
            int from = Math.min(at[0] + 18, wall), to = Math.max(at[0] - 2, wall);
            for (int k = from; k < to; k += 3) g.fill(x + k, y + at[1] + 8, x + k + 2, y + at[1] + 9, STENCIL_DIM);
        }
        // Wells, each with a faded picture of what it takes while empty.
        ItemStack[] bayGhosts = {new ItemStack(Items.CHEST), new ItemStack(Items.CRAFTING_TABLE), new ItemStack(Items.FURNACE)};
        for (int slot = 0; slot < SupportModules.FIT_SLOTS; slot++) {
            int[] at = SupportFitMenu.POSITIONS[slot];
            boolean empty = !this.menu.slots.get(slot).hasItem();
            well(g, x + at[0], y + at[1], empty ? 0xFF4E5A38 : 0xFF7E8C52);
            if (!empty) continue;
            ItemStack ghost = slot < SupportModules.WALL_BAYS ? bayGhosts[(int) ((millis / 1500 + slot) % 3)]
                    : slot == SupportModules.BUNK_LEFT || slot == SupportModules.BUNK_RIGHT ? new ItemStack(Items.RED_BED)
                    : slot == SupportModules.LOCKER_LEFT || slot == SupportModules.LOCKER_RIGHT ? new ItemStack(Items.BARREL)
                    : slot == SupportModules.ROOF ? new ItemStack(Items.BEACON) : new ItemStack(Items.LANTERN);
            ghost(g, ghost, x + at[0], y + at[1]);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.menu.vehicleId() >= 0) {
            for (Key key : this.keys) {
                if (key.over(event.x(), event.y())) {
                    if (this.minecraft != null) this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new SupportModulePayload(this.menu.vehicleId(), key.slot()));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Where the console key that opens this slot is drawn (GUI coordinates), for tests to click it. */
    public double[] keyCentre(int slot) {
        for (Key key : this.keys) {
            if (key.slot() == slot) return new double[]{key.x() + key.w() / 2.0, key.y() + key.h() / 2.0};
        }
        return null;
    }

    private void line(GuiGraphicsExtractor g, int dx, int row, String name, Component value, int ledColour, boolean lit) {
        g.text(this.font, Component.translatable(name), dx + 5, row, PHOSPHOR_DIM, false);
        glow(g, this.font, value, dx + 52, row, PHOSPHOR);
        led(g, dx + DISPLAY_WIDTH - 10, row + 1, ledColour, lit);
    }

    private static int bedColour(ItemStack bed) {
        if (bed.getItem() instanceof BlockItem item && item.getBlock() instanceof net.minecraft.world.level.block.BedBlock block) {
            return 0xFF000000 | block.getColor().getTextureDiffuseColor();
        }
        return PHOSPHOR;
    }

    private static String takes(int slot) {
        if (slot < SupportModules.WALL_BAYS) return "bay";
        if (slot == SupportModules.BUNK_LEFT || slot == SupportModules.BUNK_RIGHT) return "bunk";
        if (slot == SupportModules.LOCKER_LEFT || slot == SupportModules.LOCKER_RIGHT) return "locker";
        if (slot == SupportModules.ROOF) return "roof";
        return "lamp";
    }
}
