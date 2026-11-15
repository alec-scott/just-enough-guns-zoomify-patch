package ttv.migami.jeg.vehicle.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * The look shared by the FSV-6's in-vehicle screens (support console, beacon terminal): olive steel equipment with
 * rivets, a stencilled name plate and a hazard-striped foot; recessed slot wells; green phosphor CRTs with scanlines,
 * a vignette, rounded corners, glowing text and a slow scan band; LEDs, segmented bar gauges and push-buttons. Drawn
 * with fills and text only, so it needs no textures to keep in step.
 */
final class ConsoleStyle {
    static final int STEEL = 0xFF3D4532, STEEL_LIGHT = 0xFF5E6A45, STEEL_DARK = 0xFF22271B, STEEL_DEEP = 0xFF171B12;
    static final int RACK = 0xFF1B2018, STENCIL = 0xFFDCD3AC, STENCIL_DIM = 0xFF8C8670, AMBER = 0xFFE8A93A;
    static final int CRT = 0xFF061209, CRT_LINE = 0xFF0A1C11, PHOSPHOR = 0xFF86FFA2, PHOSPHOR_DIM = 0xFF3E8C55;
    static final int PHOSPHOR_GLOW = 0x3386FFA2;
    static final int LED_GREEN = 0xFF6BFF6B, LED_AMBER = 0xFFFFB43A, LED_RED = 0xFFFF5040, LED_OFF = 0xFF262D27;
    static final int HAZARD = 0xFFD9A62B;

    private ConsoleStyle() {
    }

    /** The equipment case: bevelled steel round (x, y, w, h), rivets, a name plate on top and a hazard-striped foot. */
    static void chassis(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component title, String serial) {
        int x0 = x - 6, y0 = y - 18, x1 = x + w + 6, y1 = y + h + 8;
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF0E110B);
        g.fill(x0, y0, x1, y1, STEEL);
        // Brushed texture: faint horizontal streaks.
        for (int yy = y0 + 2; yy < y1 - 2; yy += 3) g.fill(x0 + 2, yy, x1 - 2, yy + 1, 0x0CFFFFFF);
        g.fill(x0, y0, x1, y0 + 1, STEEL_LIGHT);
        g.fill(x0, y0, x0 + 1, y1, STEEL_LIGHT);
        g.fill(x0, y1 - 1, x1, y1, STEEL_DEEP);
        g.fill(x1 - 1, y0, x1, y1, STEEL_DEEP);
        // Hazard stripes along the foot.
        int fy = y1 - 6;
        g.fill(x0 + 3, fy, x1 - 3, y1 - 2, 0xFF141712);
        for (int sx = x0 + 3; sx < x1 - 3; sx += 8) {
            for (int k = 0; k < 4; k++) {
                int px = sx + k;
                if (px + 4 <= x1 - 3) g.fill(px, fy + k, px + 4, fy + k + 1, HAZARD);
            }
        }
        // Rivets along the edges.
        for (int rx = x0 + 6; rx < x1 - 4; rx += 24) {
            rivet(g, rx, y0 + 3);
        }
        for (int ry = y0 + 14; ry < y1 - 10; ry += 24) {
            rivet(g, x0 + 2, ry);
            rivet(g, x1 - 5, ry);
        }
        // Name plate.
        int pw = font.width(title) + 16;
        g.fill(x + 4, y0 + 3, x + 4 + pw, y0 + 15, 0xFF0E110B);
        g.fill(x + 5, y0 + 4, x + 3 + pw, y0 + 14, STEEL_DARK);
        g.text(font, title, x + 12, y0 + 5, STENCIL, false);
        if (serial != null) {
            int sw = font.width(serial);
            g.text(font, serial, x + w - sw - 2, y0 + 5, STENCIL_DIM, false);
        }
    }

    static void rivet(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 3, y + 3, STEEL_DEEP);
        g.fill(x, y, x + 2, y + 2, STEEL_LIGHT);
        g.fill(x + 1, y + 1, x + 2, y + 2, 0xFF7A8858);
    }

    /** An engraved, slightly sunken plate (a panel inside the case). */
    static void plate(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, RACK);
        g.fill(x, y, x + w, y + 1, STEEL_DEEP);
        g.fill(x, y, x + 1, y + h, STEEL_DEEP);
        g.fill(x, y + h - 1, x + w, y + h, STEEL_LIGHT);
        g.fill(x + w - 1, y, x + w, y + h, STEEL_LIGHT);
    }

    /** A recessed slot well (the slot's 16 px inside), its rim in {@code edge}. */
    static void well(GuiGraphicsExtractor g, int x, int y, int edge) {
        g.fill(x - 2, y - 2, x + 18, y + 18, 0xFF0B0D09);
        g.fill(x - 1, y - 1, x + 17, y + 17, edge);
        g.fill(x, y, x + 16, y + 16, 0xFF0C0F0B);
        g.fill(x, y, x + 16, y + 2, 0xFF050605);
        g.fill(x, y, x + 1, y + 16, 0xFF050605);
    }

    /** A faded picture of what a slot takes, in an empty well. */
    static void ghost(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        g.item(stack, x, y);
        g.fill(x, y, x + 16, y + 16, 0xB00C0F0B);
    }

    /** A CRT screen: scanlines, a slow bright scan band, a vignette and rounded corners in the case's colour. */
    static void crt(GuiGraphicsExtractor g, int x, int y, int w, int h, long millis) {
        g.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xFF0B0D09);
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, STEEL_DARK);
        g.fill(x, y, x + w, y + h, CRT);
        for (int line = y + 1; line < y + h; line += 2) g.fill(x, line, x + w, line + 1, CRT_LINE);
        int band = y + (int) ((millis / 30) % (h + 24)) - 12;
        for (int k = 0; k < 12; k++) {
            int by = band + k;
            if (by >= y && by < y + h) g.fill(x, by, x + w, by + 1, ((int) (10 * Mth.sin(k / 11.0F * Mth.PI)) << 24) | 0x86FFA2);
        }
        // Vignette.
        for (int k = 0; k < 4; k++) {
            int a = (0x30 - k * 0x0B) << 24;
            g.fill(x + k, y + k, x + w - k, y + k + 1, a);
            g.fill(x + k, y + h - k - 1, x + w - k, y + h - k, a);
            g.fill(x + k, y + k + 1, x + k + 1, y + h - k - 1, a);
            g.fill(x + w - k - 1, y + k + 1, x + w - k, y + h - k - 1, a);
        }
        // Rounded corners.
        for (int[] c : new int[][]{{x, y, 1, 1}, {x + w - 1, y, -1, 1}, {x, y + h - 1, 1, -1}, {x + w - 1, y + h - 1, -1, -1}}) {
            pixel(g, c[0], c[1]);
            pixel(g, c[0] + c[2], c[1]);
            pixel(g, c[0], c[1] + c[3]);
        }
    }

    private static void pixel(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 1, y + 1, STEEL_DARK);
    }

    /** Phosphor text with a soft glow round it. */
    static void glow(GuiGraphicsExtractor g, Font font, Component text, int x, int y, int colour) {
        int glow = (0x30 << 24) | (colour & 0xFFFFFF);
        g.text(font, text, x + 1, y, glow, false);
        g.text(font, text, x - 1, y, glow, false);
        g.text(font, text, x, y + 1, glow, false);
        g.text(font, text, x, y - 1, glow, false);
        g.text(font, text, x, y, colour, false);
    }

    static void glow(GuiGraphicsExtractor g, Font font, String text, int x, int y, int colour) {
        glow(g, font, Component.literal(text), x, y, colour);
    }

    /** A 5 px LED with a highlight; lit ones get a halo. */
    static void led(GuiGraphicsExtractor g, int x, int y, int colour, boolean lit) {
        if (lit) g.fill(x - 1, y - 1, x + 6, y + 6, (0x40 << 24) | (colour & 0xFFFFFF));
        g.fill(x, y, x + 5, y + 5, lit ? colour : LED_OFF);
        g.fill(x + 1, y + 1, x + 2, y + 2, lit ? 0xCCFFFFFF : 0x40FFFFFF);
    }

    /** A segmented bar gauge: {@code segments} cells, the filled ones lit. */
    static void segments(GuiGraphicsExtractor g, int x, int y, int w, int h, float fill, int segments, int colour) {
        int lit = Math.round(Mth.clamp(fill, 0.0F, 1.0F) * segments);
        float step = w / (float) segments;
        for (int i = 0; i < segments; i++) {
            int sx = x + Math.round(i * step), ex = x + Math.round((i + 1) * step) - 1;
            g.fill(sx, y, ex, y + h, i < lit ? colour : 0xFF0F2A18);
        }
    }

    /** A chunky push-button with an LED, its label stencilled; pressed while hovered. */
    static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component label, boolean hover, int led) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF0B0D09);
        g.fill(x, y, x + w, y + h, hover ? 0xFF4F5B38 : 0xFF444F31);
        g.fill(x, y, x + w, y + 1, hover ? 0xFF353D27 : STEEL_LIGHT);
        g.fill(x, y + h - 2, x + w, y + h, 0xFF1A1E14);
        led(g, x + 5, y + (h - 5) / 2, led, true);
        g.text(font, label, x + 14 + (w - 14 - font.width(label)) / 2, y + (h - 8) / 2 + (hover ? 1 : 0), hover ? AMBER : STENCIL, false);
    }
}
