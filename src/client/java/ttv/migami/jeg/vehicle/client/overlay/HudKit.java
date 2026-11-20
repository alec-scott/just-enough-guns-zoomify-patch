package ttv.migami.jeg.vehicle.client.overlay;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import ttv.migami.jeg.Reference;

/**
 * The vehicle HUD's look, shared by every panel: dark panels with soft rounded corners; a clean sans (Inter SemiBold,
 * {@code font/hud.ttf}) in near-white for readings and grey for names; one amber accent; green, amber and red only for
 * state; thin pill bars; smooth icons tinted per use; keys shown as rounded keycaps. The info layer is drawn at 80% of
 * the GUI scale.
 *
 * <p>Minecraft samples fonts and GUI textures NEAREST, so antialiased text and shapes only look smooth when one texel
 * lands on one screen pixel. Each HUD unit therefore covers a whole number of screen pixels (the density), and the
 * font ({@code font/hud_<n>.json}, oversampled n times) and the atlas of icons and round shapes
 * ({@code textures/gui/hud/kit_<n>.png}) are built for each density by design/hud/build_hud_assets.py; HudKit draws
 * with the set that matches.
 */
public final class HudKit {
    private static final int MAX_DENSITY = 6;
    private static final FontDescription[] FONTS = new FontDescription[MAX_DENSITY + 1];
    private static final Identifier[] KITS = new Identifier[MAX_DENSITY + 1];
    static {
        for (int n = 1; n <= MAX_DENSITY; n++) {
            FONTS[n] = new FontDescription.Resource(Reference.id("hud_" + n));
            KITS[n] = Reference.id("textures/gui/hud/kit_" + n + ".png");
        }
    }
    /** Screen pixels per HUD unit of what is being drawn now (set by {@link #scale} and {@link #useDensity}). */
    private static int density = 2;

    // Icon atlas cells (design/hud/build_hud_assets.py ICONS).
    public static final int ICON_HULL = 0, ICON_ENERGY = 1, ICON_SPEED = 2, ICON_DRIVER = 3, ICON_SEAT = 4, ICON_BLADE = 5,
            ICON_LOAD = 6, ICON_HOLD = 7, ICON_GRADE = 8, ICON_RIPPER = 9, ICON_LANE = 10, ICON_WARN = 11, ICON_AMMO = 12,
            ICON_DECOY = 13, ICON_ALT = 14, ICON_THROTTLE = 15, ICON_GEAR = 16, ICON_CLOCK = 17, ICON_TARGET = 18,
            ICON_BEARING = 19, ICON_ELEVATION = 20, ICON_TOW = 21, ICON_POWER = 22, ICON_CHECK = 23, ICON_RELOAD = 24,
            ICON_COMPASS = 25;
    public static final int NO_ICON = -1;

    /** Panel fill: dark slate at about 78%, enough to read over snow and sky without hiding the world behind it. */
    public static final int BG = 0xC80F1317;
    public static final int DIVIDER = 0x1CFFFFFF, TRACK = 0x2EFFFFFF, CHIP = 0x26FFFFFF;
    public static final int TEXT = 0xFFEEF1F4, MUTED = 0xFF8F99A3, FAINT = 0xFF5C656E;
    public static final int ACCENT = 0xFFF2B53A, OK = 0xFF63D68B, WARN = 0xFFF2B53A, BAD = 0xFFFF6159, INFO = 0xFF6CC6F0;
    /** Row pitch of a line of text (5-pixel caps and 4 of leading), and the cap height. */
    public static final int LINE = 9, CAP = 5;
    /** Gap between the info layer and the screen edges, HUD units. */
    public static final int MARGIN = 6;

    private HudKit() {}

    /**
     * HUD units per GUI unit for the info layer: 80% of the GUI scale, rounded to whole screen pixels per HUD unit.
     * Also selects that density's font and atlas for what is drawn next.
     */
    public static float scale(Minecraft mc) {
        int gui = Math.max(1, mc.getWindow().getGuiScale());
        int n = Math.max(1, Math.round(gui * 0.8F));
        useDensity(n);
        return n / (float) gui;
    }

    /** Draw next at {@code pixelsPerUnit} screen pixels per unit: a screen drawn at the GUI scale passes the scale. */
    public static void useDensity(int pixelsPerUnit) {
        density = Mth.clamp(pixelsPerUnit, 1, MAX_DENSITY);
    }

    /** The HUD font for the current density. */
    public static FontDescription font() {
        return FONTS[density];
    }

    public static MutableComponent hud(Component text) {
        FontDescription font = font();
        return text.copy().withStyle(style -> style.withFont(font));
    }

    public static MutableComponent hud(String text) {
        return hud(Component.literal(text));
    }

    public static int width(Font font, Component text) {
        return font.width(hud(text));
    }

    /** Text in the HUD font, its cap tops at {@code y}. */
    public static void text(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int colour) {
        g.text(font, hud(text), x, y, colour, false);
    }

    public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int colour) {
        text(g, font, Component.literal(text), x, y, colour);
    }

    public static void centred(GuiGraphicsExtractor g, Font font, Component text, int cx, int y, int colour) {
        text(g, font, text, cx - width(font, text) / 2, y, colour);
    }

    public static void rightAligned(GuiGraphicsExtractor g, Font font, Component text, int right, int y, int colour) {
        text(g, font, text, right - width(font, text), y, colour);
    }

    /** A panel: the fill with soft rounded corners (3 units). */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        panel(g, x, y, w, h, BG);
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill) {
        roundRect(g, x, y, w, h, 3, fill);
    }

    /** A filled rectangle with antialiased corners of radius {@code r} units (1, 2 or 3). */
    public static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int colour) {
        if (w < 2 * r || h < 2 * r) {
            g.fill(x, y, x + w, y + h, colour);
            return;
        }
        g.fill(x + r, y, x + w - r, y + h, colour);
        g.fill(x, y + r, x + r, y + h - r, colour);
        g.fill(x + w - r, y + r, x + w, y + h - r, colour);
        int n = density, cx = circleX(2 * r), cy = 16 * n, q = r * n;
        kit(g, x, y, r, r, cx, cy, q, q, colour);
        kit(g, x + w - r, y, r, r, cx + q, cy, q, q, colour);
        kit(g, x, y + h - r, r, r, cx, cy + q, q, q, colour);
        kit(g, x + w - r, y + h - r, r, r, cx + q, cy + q, q, q, colour);
    }

    /** A horizontal pill {@code h} units tall (2 or 4) with round ends. */
    public static void pill(GuiGraphicsExtractor g, int x, int y, int w, int h, int colour) {
        int r = h / 2;
        if (w < h) {
            g.fill(x, y, x + w, y + h, colour);
            return;
        }
        int n = density, cx = circleX(h), q = r * n;
        g.fill(x + r, y, x + w - r, y + h, colour);
        kit(g, x, y, r, h, cx, 16 * n, q, h * n, colour);
        kit(g, x + w - r, y, r, h, cx + q, 16 * n, q, h * n, colour);
    }

    /** The atlas column of the filled circle of {@code diameter} units (6, 4, 3 or 2). */
    private static int circleX(int diameter) {
        int slot = switch (diameter) {
            case 6 -> 0;
            case 4 -> 1;
            case 3 -> 2;
            default -> 3;
        };
        return slot * 8 * density;
    }

    /** A region of the current density's atlas, {@code w} x {@code h} units from {@code uw} x {@code vh} texels (1:1). */
    private static void kit(GuiGraphicsExtractor g, int x, int y, int w, int h, int u, int v, int uw, int vh, int colour) {
        int n = density;
        g.blit(RenderPipelines.GUI_TEXTURED, KITS[n], x, y, u, v, w, h, uw, vh, 128 * n, 24 * n, colour);
    }

    /** One 7x7 icon from the atlas, tinted, centred on a line of text whose caps start at {@code y}. */
    public static void icon(GuiGraphicsExtractor g, int icon, int x, int y, int colour) {
        if (icon < 0) return;
        int n = density;
        kit(g, x, y - 1, 7, 7, (icon % 16) * 8 * n, (icon / 16) * 8 * n, 7 * n, 7 * n, colour);
    }

    /** A status lamp: a round 3-unit dot beside a line of text whose caps start at {@code y}. */
    public static void dot(GuiGraphicsExtractor g, int x, int y, int colour) {
        int n = density;
        kit(g, x, y + 1, 3, 3, circleX(3), 16 * n, 3 * n, 3 * n, colour);
    }

    /** A thin pill bar on a text row (caps at {@code y}): the track, then the filled part. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, float ratio, int colour) {
        pill(g, x, y + 2, w, 2, TRACK);
        int filled = Math.round(w * Mth.clamp(ratio, 0.0F, 1.0F));
        if (filled > 0) pill(g, x, y + 2, Math.max(filled, 2), 2, colour);
    }

    /** A slider: a thin track, an optional shaded band from {@code bandFrom} to the end, a tick and a pill marker. */
    public static void slider(GuiGraphicsExtractor g, int x, int y, int w, float marker, float bandFrom, float tick, int colour) {
        if (!Float.isNaN(bandFrom)) {
            int from = x + Math.round(w * Mth.clamp(bandFrom, 0.0F, 1.0F));
            pill(g, from, y + 1, x + w - from, 4, 0x305E8AB4);
        }
        g.fill(x, y + 2, x + w, y + 3, TRACK);
        if (!Float.isNaN(tick)) {
            int t = x + Math.round(w * Mth.clamp(tick, 0.0F, 1.0F));
            g.fill(t, y + 1, t + 1, y + 4, MUTED);
        }
        int m = x + Math.round(w * Mth.clamp(marker, 0.0F, 1.0F));
        int n = density, cx = circleX(2);
        g.fill(m - 1, y, m + 1, y + 5, colour);
        kit(g, m - 1, y - 1, 2, 1, cx, 16 * n, 2 * n, n, colour);
        kit(g, m - 1, y + 5, 2, 1, cx, 17 * n, 2 * n, n, colour);
    }

    /** A keycap's width for {@code key}. */
    public static int keyWidth(Font font, Component key) {
        return Math.max(width(font, key) + 6, 9);
    }

    /** A keycap: a light rounded chip with the key's name centred in it. Returns its width. */
    public static int keycap(GuiGraphicsExtractor g, Font font, Component key, int x, int y, float alpha) {
        int w = keyWidth(font, key);
        roundRect(g, x, y - 2, w, 9, 2, fade(CHIP, alpha));
        centred(g, font, key, x + w / 2, y, fade(TEXT, alpha));
        return w;
    }

    /** A key's short name for a keycap: SHIFT, CTRL, LMB rather than "Left Shift", "Left Control", "Left Button". */
    public static Component keyName(KeyMapping mapping) {
        String key = mapping.saveString();
        String name = switch (key) {
            case "key.keyboard.left.shift", "key.keyboard.right.shift" -> "Shift";
            case "key.keyboard.left.control", "key.keyboard.right.control" -> "Ctrl";
            case "key.keyboard.left.alt", "key.keyboard.right.alt" -> "Alt";
            case "key.keyboard.space" -> "Space";
            case "key.mouse.left" -> "LMB";
            case "key.mouse.right" -> "RMB";
            case "key.mouse.middle" -> "MMB";
            default -> null;
        };
        return name != null ? Component.literal(name) : mapping.getTranslatedKeyMessage();
    }

    /** {@code argb} with its alpha multiplied by {@code alpha}. */
    public static int fade(int argb, float alpha) {
        if (alpha >= 1.0F) return argb;
        int a = Math.round((argb >>> 24) * Mth.clamp(alpha, 0.0F, 1.0F));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    /** Green above half, amber above a quarter, red below. */
    public static int health(float ratio) {
        return ratio > 0.5F ? OK : ratio > 0.25F ? WARN : BAD;
    }
}
