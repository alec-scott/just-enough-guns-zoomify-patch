package ttv.migami.jeg.vehicle.client.overlay;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.client.VehicleClientState;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * A helicopter crew's helmet display in first person, in one green symbology around the reticle (the user asked for an
 * aesthetically pleasing first-person HUD, 2026-11-17; the stacked vehicle panel sat over the windscreen and mixed
 * five colours): a heading tape across the top, airspeed boxed left of the reticle, height above the ground and the
 * climb rate boxed right of it, and the lock prompt under it. The vehicle card (hull, power, flares) stands in the
 * bottom left corner and the crew list in the top left, clear of the instrument panel (2026-11-19). Everything has a
 * dark edge so it reads against sky and ground alike.
 */
final class HelicopterHmd {
    private static final int GREEN = 0xFF8CFF9A;
    private static final int DIM = 0xFF5FB86A;
    private static final int AMBER = 0xFFFFC94A;
    private static final int RED = 0xFFFF5A4A;
    private static final int SHADOW = 0x90000000;
    /** The heading tape: half its width in pixels, and the degrees either side of the centre it shows. */
    private static final int TAPE_HALF = 90;
    private static final int TAPE_SPAN = 60;
    /** The airspeed and altitude boxes sit this far either side of the reticle's centre. */
    private static final int BOX_OFFSET = 74;
    private static final int BOX_WIDTH = 40;

    private HelicopterHmd() {}

    static void render(GuiGraphicsExtractor g, Minecraft mc, VehicleEntity heli) {
        if (mc.player == null) {
            return;
        }
        Font font = mc.font;
        // Drawn at the vehicle HUD's 80% scale, in its font.
        float scale = HudKit.scale(mc);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        int width = Mth.floor(g.guiWidth() / scale);
        int height = Mth.floor(g.guiHeight() / scale);
        int cx = width / 2;
        int cy = height / 2;
        headingTape(g, font, cx, Mth.wrapDegrees(mc.gameRenderer.getMainCamera().yRot()));

        // Airspeed, boxed left of the reticle.
        String speed = String.format(Locale.ROOT, "%.1f", VehicleHudOverlay.speedMetresPerSecond(heli));
        valueBox(g, font, cx - BOX_OFFSET - BOX_WIDTH, cy - 6, speed, "SPD M/S", GREEN);

        // Height above the ground and the climb rate, boxed right of it.
        double altitude = heli.onGround() ? 0.0D : heli.exactAltitudeAboveGround();
        double climb = heli.getDeltaMovement().y * 20.0D;
        valueBox(g, font, cx + BOX_OFFSET, cy - 6, String.valueOf(Math.round(altitude)), "ALT M", GREEN);
        String vs = String.format(Locale.ROOT, "%s%.1f", climb >= 0.05D ? "▲" : climb <= -0.05D ? "▼" : " ", Math.abs(climb));
        // A fast drop near the ground is the thing to watch: amber.
        int vsColour = climb < -7.0D && altitude < 30.0D ? AMBER : DIM;
        text(g, font, vs, cx + BOX_OFFSET + BOX_WIDTH - w(font, vs), cy + 18, vsColour);

        // The weapon, its ammo and the flares, under the reticle (the warnings sit just under its centre).
        int y = cy + 32;
        // The weapon and its rounds are in the weapon list, the reload and the flares on the status card: here only the
        // missile lock, which is watched through the sight (each fact once, 2026-11-19).
        if (heli.hasVehicleWeapons() && heli.canPassengerUseSelectedVehicleWeapon(mc.player)) {
            if (heli.isSelectedVehicleWeaponLockOn(mc.player)) {
                boolean seeking = VehicleClientState.isRidingVehicle() && VehicleClientState.vehicleId() == heli.getId() && VehicleClientState.seekDown();
                String lock = seeking
                        ? Component.translatable(heli.hasMissileLock() ? "hud.jeg.vehicle.locked" : "hud.jeg.vehicle.locking").getString()
                        : Component.translatable("hud.jeg.vehicle.seek_prompt", KeyBindings.VEHICLE_SEEK.getTranslatedKeyMessage()).getString();
                centred(g, font, lock.toUpperCase(Locale.ROOT), cx, y, seeking && heli.hasMissileLock() ? RED : seeking ? AMBER : DIM);
                y += 11;
            }
        }

        // Hull and power are on the status card (VehicleHudOverlay), as in every other vehicle.
        g.pose().popMatrix();
    }

    /** The heading tape: ticks every 10 degrees, numbers every 30 (N, E, S, W at the quarters), a caret at the centre. */
    private static void headingTape(GuiGraphicsExtractor g, Font font, int cx, float yaw) {
        int top = 8;
        float heading = Mth.positiveModulo(yaw + 180.0F, 360.0F);
        int first = Mth.ceil((heading - TAPE_SPAN) / 10.0F) * 10;
        for (int mark = first; mark <= heading + TAPE_SPAN; mark += 10) {
            float offset = (mark - heading) / TAPE_SPAN;
            int x = cx + Math.round(offset * TAPE_HALF);
            int label = Math.floorMod(mark, 360);
            boolean major = label % 30 == 0;
            // Fade out towards the tape's ends.
            int alpha = Math.round(255.0F * Mth.clamp(1.4F - Math.abs(offset) * 1.2F, 0.0F, 1.0F));
            if (alpha < 16) {
                continue;
            }
            int colour = (alpha << 24) | (GREEN & 0xFFFFFF);
            line(g, x, top + 10, x + 1, top + (major ? 16 : 13), colour);
            if (major) {
                String name = switch (label) {
                    case 0 -> "N";
                    case 90 -> "E";
                    case 180 -> "S";
                    case 270 -> "W";
                    default -> String.format(Locale.ROOT, "%02d", label / 10);
                };
                text(g, font, name, x - w(font, name) / 2 + 1, top, colour);
            }
        }
        // The caret and the heading read out under it.
        line(g, cx - 2, top + 18, cx + 3, top + 19, GREEN);
        line(g, cx - 1, top + 19, cx + 2, top + 20, GREEN);
        line(g, cx, top + 20, cx + 1, top + 21, GREEN);
        String readout = String.format(Locale.ROOT, "%03d", Math.round(heading) % 360);
        int bx = cx - w(font, readout) / 2 - 3;
        g.fill(bx + 1, top + 24, bx + w(font, readout) + 5, top + 34, 0x40000000);
        frame(g, bx, top + 23, w(font, readout) + 6, 12, GREEN);
        text(g, font, readout, bx + 3, top + 25, GREEN);
    }

    /** A value in a thin box with its label under it, the box {@link #BOX_WIDTH} wide. */
    private static void valueBox(GuiGraphicsExtractor g, Font font, int x, int y, String value, String label, int colour) {
        g.fill(x + 1, y + 1, x + BOX_WIDTH - 1, y + 12, 0x40000000);
        frame(g, x, y, BOX_WIDTH, 13, colour);
        text(g, font, value, x + BOX_WIDTH - 3 - w(font, value), y + 3, colour);
        text(g, font, label, x + (BOX_WIDTH - w(font, label)) / 2, y + 15, DIM);
    }

    private static void centred(GuiGraphicsExtractor g, Font font, String text, int cx, int y, int colour) {
        text(g, font, text, cx - w(font, text) / 2, y, colour);
    }

    private static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int colour) {
        g.text(font, HudKit.hud(text), x, y, colour, true);
    }

    private static int w(Font font, String text) {
        return HudKit.width(font, Component.literal(text));
    }

    /** A filled run of pixels with a one-pixel dark edge around it (as faint as the run). */
    private static void line(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int colour) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, shadow(colour));
        g.fill(x0, y0, x1, y1, colour);
    }

    private static int shadow(int colour) {
        return ((colour >>> 24) * (SHADOW >>> 24) / 255) << 24;
    }

    /** A one-pixel box outline with a dark edge inside and out, the edges drawn first so the corners stay clean. */
    private static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h, int colour) {
        int edge = shadow(colour);
        g.fill(x - 1, y - 1, x + w + 1, y + 2, edge);
        g.fill(x - 1, y + h - 2, x + w + 1, y + h + 1, edge);
        g.fill(x - 1, y + 2, x + 2, y + h - 2, edge);
        g.fill(x + w - 2, y + 2, x + w + 1, y + h - 2, edge);
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }
}
