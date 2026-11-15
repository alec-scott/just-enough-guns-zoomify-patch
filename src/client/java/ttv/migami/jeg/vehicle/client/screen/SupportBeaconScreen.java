package ttv.migami.jeg.vehicle.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import ttv.migami.jeg.vehicle.menu.SupportBeaconMenu;
import ttv.migami.jeg.vehicle.support.SupportModules;

import static ttv.migami.jeg.vehicle.client.screen.ConsoleStyle.*;

/**
 * The FSV-6's beacon terminal, opened from its panel on the room's front wall (beside the support console, behind the
 * cab). Drawn as the console is ({@link ConsoleStyle}):
 * <ul>
 *     <li>a green phosphor display: the beacon's state (not fitted, mast down, standby, lit), its level and how many
 *     blocks the rack holds against the next level, then the effect keys by tier (I to III primaries, IV the
 *     secondary: regeneration or the primary made stronger), lit amber when chosen and dimmed until the level allows;</li>
 *     <li>the power rack's nine wells under it, then the player's inventory.</li>
 * </ul>
 * Inventory Profiles Next leaves it alone (its sort buttons would scramble the power rack).
 */
@org.anti_ad.mc.ipn.api.IPNIgnore
public final class SupportBeaconScreen extends AbstractContainerScreen<SupportBeaconMenu> {
    private static final int KEY = 20, WELL_EDGE = 0xFF4E5A38;

    /** An effect key: its area, the menu button it presses, its effect and the level it needs. */
    private record EffectKey(int x, int y, int button, Holder<MobEffect> effect, int needs, boolean selected, boolean strongerPrimary) {
        boolean over(double mx, double my) {
            return mx >= this.x && mx < this.x + KEY && my >= this.y && my < this.y + KEY;
        }
    }

    private final List<EffectKey> keys = new ArrayList<>();

    public SupportBeaconScreen(SupportBeaconMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, SupportBeaconMenu.WIDTH, SupportBeaconMenu.HEIGHT);
        this.titleLabelY = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = this.leftPos, y = this.topPos;
        long millis = net.minecraft.util.Util.getMillis();
        chassis(g, this.font, x, y, this.imageWidth, this.imageHeight, Component.translatable("gui.jeg.support_beacon.title"), null);
        plate(g, x, y, this.imageWidth, this.imageHeight);

        // ---- The display.
        // 206 px tall (232 with the case), so it fits a 240 px GUI (scale 2 at 480 px high).
        int dx = x + 4, dy = y + 4, dw = this.imageWidth - 8, dh = 81;
        crt(g, dx, dy, dw, dh, millis);

        int level = this.menu.level(), blocks = this.menu.rackBlocks();
        boolean fitted = this.menu.flag(SupportBeaconMenu.FLAG_FITTED), mastUp = this.menu.flag(SupportBeaconMenu.FLAG_MAST_UP);
        boolean lit = this.menu.flag(SupportBeaconMenu.FLAG_LIT);
        String state = !fitted ? "not_fitted" : lit ? "lit" : !mastUp ? "mast_down" : level == 0 ? "no_power" : "standby";
        // The top line's right end stays empty: Inventory Profiles Next (in the pack) draws its buttons there.
        led(g, dx + 5, dy + 5, lit ? LED_GREEN : LED_AMBER, lit || (fitted && level > 0));
        glow(g, this.font, Component.translatable("gui.jeg.support_beacon.state." + state), dx + 14, dy + 4,
                lit ? PHOSPHOR : fitted && level > 0 ? LED_AMBER : PHOSPHOR_DIM);
        g.fill(dx + 4, dy + 14, dx + dw - 4, dy + 15, PHOSPHOR_DIM);

        // Level and the rack against the next level.
        glow(g, this.font, Component.translatable("gui.jeg.support_beacon.level", level), dx + 5, dy + 18, PHOSPHOR);
        int next = level < SupportModules.LEVEL_BLOCKS.length ? SupportModules.LEVEL_BLOCKS[level] : -1;
        int previous = level == 0 ? 0 : SupportModules.LEVEL_BLOCKS[level - 1];
        Component count = next < 0 ? Component.translatable("gui.jeg.support_beacon.blocks_max", blocks)
                : Component.translatable("gui.jeg.support_beacon.blocks", blocks, next);
        g.text(this.font, count, dx + dw - 5 - this.font.width(count), dy + 18, PHOSPHOR_DIM, false);
        int bx = dx + 5, bw = dw - 10;
        float fill = next < 0 ? 1F : Math.max(0, Math.min(1, (blocks - previous) / (float) (next - previous)));
        segments(g, bx, dy + 28, bw, 4, fill, 20, next < 0 ? PHOSPHOR : LED_AMBER);

        // ---- Effect keys by tier.
        this.keys.clear();
        Holder<MobEffect> primary = BeaconMenu.decodeEffect(this.menu.primary());
        Holder<MobEffect> secondary = BeaconMenu.decodeEffect(this.menu.secondary());
        List<List<Holder<MobEffect>>> tiers = BeaconBlockEntity.BEACON_EFFECTS;
        String[] numerals = {"I", "II", "III", "IV"};
        int kx = dx + 5, ky = dy + 46;
        for (int tier = 0; tier < 4; tier++) {
            List<Holder<MobEffect>> effects = new ArrayList<>(tier < tiers.size() ? tiers.get(tier) : List.of());
            boolean secondaryTier = tier == 3;
            int width = (effects.size() + (secondaryTier ? 1 : 0)) * (KEY + 2) - 2;
            g.text(this.font, numerals[tier], kx + (width - this.font.width(numerals[tier])) / 2, dy + 36, level > tier ? PHOSPHOR : PHOSPHOR_DIM, false);
            for (Holder<MobEffect> effect : effects) {
                int button = (secondaryTier ? SupportBeaconMenu.BUTTON_SECONDARY : SupportBeaconMenu.BUTTON_PRIMARY) + BeaconMenu.encodeEffect(effect);
                boolean selected = secondaryTier ? effect.equals(secondary) : effect.equals(primary);
                this.keys.add(new EffectKey(kx, ky, button, effect, tier + 1, selected, false));
                kx += KEY + 2;
            }
            if (secondaryTier && primary != null) {
                // The primary made stronger (level II of it).
                this.keys.add(new EffectKey(kx, ky, SupportBeaconMenu.BUTTON_SECONDARY + BeaconMenu.encodeEffect(primary), primary, 4,
                        primary.equals(secondary), true));
            }
            kx += 5;
        }
        for (EffectKey key : this.keys) {
            boolean usable = level >= key.needs() && (!key.strongerPrimary() || primary != null);
            boolean hover = usable && key.over(mouseX, mouseY);
            g.fill(key.x(), key.y(), key.x() + KEY, key.y() + KEY, key.selected() ? AMBER : hover ? PHOSPHOR : PHOSPHOR_DIM);
            g.fill(key.x() + 1, key.y() + 1, key.x() + KEY - 1, key.y() + KEY - 1, key.selected() ? 0xFF2A2410 : CRT);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(key.effect()), key.x() + 1, key.y() + 1, 18, 18);
            if (key.strongerPrimary()) g.text(this.font, "II", key.x() + KEY - 1 - this.font.width("II"), key.y() + KEY - 9, STENCIL, true);
            if (!usable) g.fill(key.x() + 1, key.y() + 1, key.x() + KEY - 1, key.y() + KEY - 1, 0xC007140D);
        }
        // What the beacon gives now.
        Component giving = primary == null ? Component.translatable("gui.jeg.support_beacon.choose")
                : secondary == null || level < 4 ? primary.value().getDisplayName().copy()
                : primary.equals(secondary) ? Component.translatable("gui.jeg.support_beacon.stronger", primary.value().getDisplayName())
                : Component.translatable("gui.jeg.support_beacon.pair", primary.value().getDisplayName(), secondary.value().getDisplayName());
        glow(g, this.font, giving, dx + 5, dy + 70, primary == null ? PHOSPHOR_DIM : PHOSPHOR);

        // ---- The power rack and the player's inventory.
        for (int i = 0; i < SupportModules.RACK_SLOTS; i++) {
            int sx = x + SupportBeaconMenu.RACK_X + i * 18, sy = y + SupportBeaconMenu.RACK_Y;
            well(g, sx, sy, WELL_EDGE);
            if (!this.menu.slots.get(i).hasItem()) {
                ItemStack[] rackGhosts = {new ItemStack(Items.RAW_IRON), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_BLOCK)};
                ghost(g, rackGhosts[(int) ((millis / 1500 + i) % 3)], sx, sy);
            }
        }
        g.text(this.font, Component.translatable("gui.jeg.support_beacon.rack"), x + 8, y + SupportBeaconMenu.RACK_Y + 20, STENCIL, false);
        // (The right end of this line is Inventory Profiles Next's sort button: what the rack takes is in the wells' tooltip.)
        g.fill(x, y + SupportBeaconMenu.PLAYER_Y - 5, x + this.imageWidth, y + SupportBeaconMenu.PLAYER_Y - 4, STEEL_LIGHT);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) well(g, x + 8 + column * 18, y + SupportBeaconMenu.PLAYER_Y + row * 18, 0xFF2E342C);
        }
        for (int column = 0; column < 9; column++) well(g, x + 8 + column * 18, y + SupportBeaconMenu.HOTBAR_Y, 0xFF2E342C);
        super.extractContents(g, mouseX, mouseY, partialTick);

        for (EffectKey key : this.keys) {
            if (!key.over(mouseX, mouseY)) continue;
            Component name = key.strongerPrimary() ? Component.translatable("gui.jeg.support_beacon.stronger", key.effect().value().getDisplayName())
                    : key.effect().value().getDisplayName();
            g.setComponentTooltipForNextFrame(this.font, level >= key.needs() ? List.of(name)
                    : List.of(name, Component.translatable("gui.jeg.support_beacon.needs", key.needs())), mouseX, mouseY);
        }
        for (int i = 0; i < SupportModules.RACK_SLOTS; i++) {
            int sx = x + SupportBeaconMenu.RACK_X + i * 18, sy = y + SupportBeaconMenu.RACK_Y;
            if (!this.menu.slots.get(i).hasItem() && mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                g.setComponentTooltipForNextFrame(this.font, List.of(Component.translatable("gui.jeg.support_fit.takes.rack")), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && this.minecraft != null && this.minecraft.gameMode != null) {
            for (EffectKey key : this.keys) {
                if (key.over(event.x(), event.y()) && this.menu.level() >= key.needs()) {
                    // Choosing the chosen effect again clears it.
                    int button = key.selected() && !key.strongerPrimary() && key.needs() <= 3 ? SupportBeaconMenu.BUTTON_CLEAR : key.button();
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Where the key for an effect is drawn (GUI coordinates), for tests to click it; {@code secondary} for tier IV. */
    public double[] keyCentre(Holder<MobEffect> effect, boolean secondary) {
        for (EffectKey key : this.keys) {
            if (key.effect().equals(effect) && (key.button() >= SupportBeaconMenu.BUTTON_SECONDARY) == secondary) {
                return new double[]{key.x() + KEY / 2.0, key.y() + KEY / 2.0};
            }
        }
        return null;
    }

    /** Where a menu slot is drawn (GUI coordinates, its centre), for tests to click it. */
    public double[] slotCentre(int menuSlot) {
        var slot = this.menu.slots.get(menuSlot);
        return new double[]{this.leftPos + slot.x + 8, this.topPos + slot.y + 8};
    }
}
