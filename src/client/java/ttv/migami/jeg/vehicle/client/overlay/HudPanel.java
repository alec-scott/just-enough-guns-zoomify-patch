package ttv.migami.jeg.vehicle.client.overlay;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * One HUD panel, built row by row and then measured and drawn: a header (a status lamp, what the vehicle is doing, a
 * short name on the right), then rows of icon, name and reading (a bar, a slider or a value) in shared columns, notes
 * across the panel, and keycap hints. Every operator panel and the vehicle card use it, so they line up and match.
 */
public final class HudPanel {
    private static final int PAD_X = 5, PAD_Y = 5, ICON_SPACE = 11, LABEL_GAP = 7, BAR_W = 44, VALUE_GAP = 5;
    private static final int HINT_LINE = 11, MIN_WIDTH = 92;

    private final Font font;
    private final List<Row> rows = new ArrayList<>();
    private int minWidth = MIN_WIDTH;

    private sealed interface Row permits Header, Gauge, Value, Note, Hints, Divider {}

    private record Header(int lamp, Component text, int colour, Component right, float progress) implements Row {}

    /** A bar ({@code slider} false) or a slider with an optional shaded band and tick, then a value. */
    private record Gauge(int icon, Component label, float ratio, int colour, Component value, int valueColour,
                         boolean slider, float band, float tick) implements Row {}

    private record Value(int icon, Component label, Component value, int colour, boolean right, boolean afterBars) implements Row {
        Value(int icon, Component label, Component value, int colour, boolean right) {
            this(icon, label, value, colour, right, false);
        }
    }

    private record Note(int icon, Component text, int colour) implements Row {}

    private record Hints(List<Hint> hints, float alpha) implements Row {}

    private record Divider() implements Row {}

    /** One key hint: the keys (shown as keycaps) and what they do. */
    public record Hint(List<Component> keys, Component action) {
        public static Hint of(Component action, Component... keys) {
            return new Hint(List.of(keys), action);
        }
    }

    public HudPanel(Font font) {
        this.font = font;
    }

    public HudPanel minWidth(int width) {
        this.minWidth = width;
        return this;
    }

    /** The first line: a lamp in {@code lamp}, the state, and an optional name at the right. */
    public HudPanel header(int lamp, Component text, Component right) {
        return this.header(lamp, text, HudKit.TEXT, right, Float.NaN);
    }

    /** A header with a thin progress line under it (deploying, packing up). */
    public HudPanel header(int lamp, Component text, int colour, Component right, float progress) {
        this.rows.add(new Header(lamp, text, colour, right, progress));
        this.rows.add(new Divider());
        return this;
    }

    public HudPanel bar(int icon, Component label, float ratio, int colour, Component value, int valueColour) {
        this.rows.add(new Gauge(icon, label, ratio, colour, value, valueColour, false, Float.NaN, Float.NaN));
        return this;
    }

    public HudPanel slider(int icon, Component label, float position, float band, float tick, int colour, Component value, int valueColour) {
        this.rows.add(new Gauge(icon, label, position, colour, value, valueColour, true, band, tick));
        return this;
    }

    public HudPanel value(int icon, Component label, Component value, int colour) {
        this.rows.add(new Value(icon, label, value, colour, false));
        return this;
    }

    /** A reading set against the card's right edge, so it never shifts as its digits change (a speed). */
    public HudPanel reading(int icon, Component label, Component value, int colour) {
        this.rows.add(new Value(icon, label, value, colour, true));
        return this;
    }

    /** A reading that starts where the bars' readings do (a speed under hull and energy bars), so they line up. */
    public HudPanel readingAfterBars(int icon, Component label, Component value, int colour) {
        this.rows.add(new Value(icon, label, value, colour, false, true));
        return this;
    }

    public HudPanel note(int icon, Component text, int colour) {
        this.rows.add(new Note(icon, text, colour));
        return this;
    }

    public HudPanel hints(float alpha, Hint... hints) {
        if (alpha > 0.05F && hints.length > 0) this.rows.add(new Hints(List.of(hints), alpha));
        return this;
    }

    public HudPanel divider() {
        this.rows.add(new Divider());
        return this;
    }

    public boolean isEmpty() {
        return this.rows.isEmpty();
    }

    private int labelWidth() {
        int w = 0;
        for (Row row : this.rows) {
            if (row instanceof Gauge gauge) w = Math.max(w, HudKit.width(this.font, gauge.label()));
            else if (row instanceof Value value) w = Math.max(w, HudKit.width(this.font, value.label()));
        }
        return w;
    }

    private int column() {
        return PAD_X + ICON_SPACE + this.labelWidth() + LABEL_GAP;
    }

    private int hintsWidth(Hints row) {
        int w = 0;
        for (Hint hint : row.hints()) {
            if (w > 0) w += 8;
            for (Component key : hint.keys()) w += HudKit.keyWidth(this.font, key) + 1;
            w += 2 + HudKit.width(this.font, hint.action());
        }
        return w;
    }

    /**
     * The widest each card has needed, by its rows' labels: a card never shrinks back, so it doesn't wobble as a reading
     * changes (the user's review, 2026-11-19: "text shouldn't shrink or expand based on the numbers").
     */
    private static final java.util.Map<String, Integer> HELD_WIDTH = new java.util.HashMap<>();

    /** A reading's width with a digit to spare (the HUD font's digits are all one width), so 99 to 100 doesn't grow it. */
    private int valueWidth(Component value) {
        String text = value.getString();
        int w = HudKit.width(this.font, value);
        return text.chars().anyMatch(Character::isDigit) ? w + HudKit.width(this.font, Component.literal("0")) : w;
    }

    /** A value that is a number (it starts with a digit or a sign): set against the right edge, as the speed is. */
    private static boolean numeric(Component value) {
        String text = value.getString().strip();
        return !text.isEmpty() && (Character.isDigit(text.charAt(0)) || (text.length() > 1 && (text.charAt(0) == '-' || text.charAt(0) == '+')
                && Character.isDigit(text.charAt(1))));
    }

    public int width() {
        int natural = this.naturalWidth();
        StringBuilder key = new StringBuilder().append(this.minWidth);
        for (Row row : this.rows) {
            switch (row) {
                case Gauge gauge -> key.append('|').append(gauge.label().getString());
                case Value value -> key.append('|').append(value.label().getString());
                default -> { }
            }
        }
        return HELD_WIDTH.merge(key.toString(), natural, Math::max);
    }

    private int naturalWidth() {
        int column = this.column();
        int w = this.minWidth;
        for (Row row : this.rows) {
            int need = switch (row) {
                case Header header -> PAD_X + 7 + HudKit.width(this.font, header.text())
                        + (header.right() == null ? 0 : 10 + HudKit.width(this.font, header.right())) + PAD_X;
                case Gauge gauge -> column + BAR_W + VALUE_GAP + this.valueWidth(gauge.value()) + PAD_X;
                case Value value -> column + (value.afterBars() ? BAR_W + VALUE_GAP : 0) + this.valueWidth(value.value()) + PAD_X;
                case Note note -> PAD_X + (note.icon() >= 0 ? ICON_SPACE : 0) + HudKit.width(this.font, note.text()) + PAD_X;
                case Hints hints -> PAD_X + this.hintsWidth(hints) + PAD_X;
                case Divider divider -> 0;
            };
            w = Math.max(w, need);
        }
        return w;
    }

    public int height() {
        int h = PAD_Y * 2 - (HudKit.LINE - HudKit.CAP);
        for (Row row : this.rows) h += rowHeight(row);
        // A keycap row reaches lower than a line of text.
        if (!this.rows.isEmpty() && this.rows.getLast() instanceof Hints) h += 3;
        return h;
    }

    private static int rowHeight(Row row) {
        return switch (row) {
            case Hints hints -> HINT_LINE;
            case Divider divider -> 4;
            default -> HudKit.LINE;
        };
    }

    public void draw(GuiGraphicsExtractor g, int x, int y) {
        int w = this.width();
        HudKit.panel(g, x, y, w, this.height());
        int column = x + this.column();
        int right = x + w - PAD_X;
        int cy = y + PAD_Y;
        for (int i = 0; i < this.rows.size(); i++) {
            Row row = this.rows.get(i);
            switch (row) {
                case Header header -> {
                    HudKit.dot(g, x + PAD_X, cy, header.lamp());
                    HudKit.text(g, this.font, header.text(), x + PAD_X + 7, cy, header.colour());
                    if (header.right() != null) HudKit.rightAligned(g, this.font, header.right(), right, cy, HudKit.MUTED);
                }
                case Gauge gauge -> {
                    HudKit.icon(g, gauge.icon(), x + PAD_X, cy, HudKit.MUTED);
                    HudKit.text(g, this.font, gauge.label(), x + PAD_X + ICON_SPACE, cy, HudKit.MUTED);
                    if (gauge.slider()) HudKit.slider(g, column, cy, BAR_W, gauge.ratio(), gauge.band(), gauge.tick(), gauge.colour());
                    else HudKit.bar(g, column, cy, BAR_W, gauge.ratio(), gauge.colour());
                    HudKit.text(g, this.font, gauge.value(), column + BAR_W + VALUE_GAP, cy, gauge.valueColour());
                }
                case Value value -> {
                    HudKit.icon(g, value.icon(), x + PAD_X, cy, HudKit.MUTED);
                    HudKit.text(g, this.font, value.label(), x + PAD_X + ICON_SPACE, cy, HudKit.MUTED);
                    if (value.afterBars()) HudKit.text(g, this.font, value.value(), column + BAR_W + VALUE_GAP, cy, value.colour());
                    else if (value.right() || numeric(value.value())) HudKit.rightAligned(g, this.font, value.value(), right, cy, value.colour());
                    else HudKit.text(g, this.font, value.value(), column, cy, value.colour());
                }
                case Note note -> {
                    int tx = x + PAD_X;
                    if (note.icon() >= 0) {
                        HudKit.icon(g, note.icon(), tx, cy, note.colour());
                        tx += ICON_SPACE;
                    }
                    HudKit.text(g, this.font, note.text(), tx, cy, note.colour());
                }
                case Hints hints -> {
                    int hx = x + PAD_X;
                    int ty = cy + 2;
                    for (Hint hint : hints.hints()) {
                        if (hx > x + PAD_X) hx += 8;
                        for (Component key : hint.keys()) hx += HudKit.keycap(g, this.font, key, hx, ty, hints.alpha()) + 1;
                        hx += 2;
                        HudKit.text(g, this.font, hint.action(), hx, ty, HudKit.fade(HudKit.MUTED, hints.alpha()));
                        hx += HudKit.width(this.font, hint.action());
                    }
                }
                case Divider divider -> {
                    float progress = i > 0 && this.rows.get(i - 1) instanceof Header header ? header.progress() : Float.NaN;
                    int ly = cy - 2;
                    g.fill(x + PAD_X, ly, right, ly + 1, HudKit.DIVIDER);
                    if (!Float.isNaN(progress)) g.fill(x + PAD_X, ly, x + PAD_X + Math.round((right - x - PAD_X) * progress), ly + 1, HudKit.ACCENT);
                }
            }
            cy += rowHeight(row);
        }
    }
}
