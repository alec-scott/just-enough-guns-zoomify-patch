package ttv.migami.jeg.vehicle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * A flat surface in a cockpit to draw instruments on: a point in the model's render space (pixels) and the directions
 * across it (the pilot's right), down its face and out towards the pilot. Points on it are given as (u across, v down,
 * w out), in pixels. Quads are drawn from both sides.
 */
final class CockpitPanel {
    static final int FULL_BRIGHT = 0x00F000F0;

    final Vector3f origin;
    final Vector3f across;
    final Vector3f down;
    final Vector3f out;

    CockpitPanel(Vector3f origin, Vector3f across, Vector3f down, Vector3f out) {
        this.origin = origin;
        this.across = new Vector3f(across).normalize();
        this.down = new Vector3f(down).normalize();
        this.out = new Vector3f(out).normalize();
    }

    /** A point on the panel, in blocks in the model's frame. */
    Vector3f p(float u, float v, float w) {
        return new Vector3f(this.origin)
                .add(this.across.x * u, this.across.y * u, this.across.z * u)
                .add(this.down.x * v, this.down.y * v, this.down.z * v)
                .add(this.out.x * w, this.out.y * w, this.out.z * w)
                .div(16.0F);
    }

    /** A rectangle on the panel, {@code w} pixels proud of it, its texture's whole face, full bright. */
    void face(PoseStack.Pose pose, VertexConsumer buffer, float u, float v, float width, float height, float w, int colour) {
        quad(pose, buffer, this.p(u, v, w), this.p(u + width, v, w), this.p(u + width, v + height, w), this.p(u, v + height, w),
                0.0F, 0.0F, 1.0F, 1.0F, colour, FULL_BRIGHT, this.out);
    }

    /**
     * A thick line on the panel from (u0, v0) to (u1, v1), clipped to the window (cu0, cv0)-(cu1, cv1) (Liang and
     * Barsky's clipping), full bright.
     */
    void line(PoseStack.Pose pose, VertexConsumer buffer, float u0, float v0, float u1, float v1, float width, float w,
              int colour, float cu0, float cv0, float cu1, float cv1) {
        float du = u1 - u0;
        float dv = v1 - v0;
        float t0 = 0.0F;
        float t1 = 1.0F;
        float[] p = {-du, du, -dv, dv};
        float[] q = {u0 - cu0, cu1 - u0, v0 - cv0, cv1 - v0};
        for (int i = 0; i < 4; i++) {
            if (p[i] == 0.0F) {
                if (q[i] < 0.0F) {
                    return;
                }
                continue;
            }
            float t = q[i] / p[i];
            if (p[i] < 0.0F) {
                t0 = Math.max(t0, t);
            } else {
                t1 = Math.min(t1, t);
            }
        }
        if (t0 >= t1) {
            return;
        }
        float a0 = u0 + du * t0;
        float b0 = v0 + dv * t0;
        float a1 = u0 + du * t1;
        float b1 = v0 + dv * t1;
        float length = Mth.sqrt((a1 - a0) * (a1 - a0) + (b1 - b0) * (b1 - b0));
        if (length < 1.0E-4F) {
            return;
        }
        float su = -(b1 - b0) / length * width * 0.5F;
        float sv = (a1 - a0) / length * width * 0.5F;
        quad(pose, buffer, this.p(a0 - su, b0 - sv, w), this.p(a1 - su, b1 - sv, w), this.p(a1 + su, b1 + sv, w),
                this.p(a0 + su, b0 + sv, w), 0.0F, 0.0F, 1.0F, 1.0F, colour, FULL_BRIGHT, this.out);
    }

    /** A convex polygon on the panel (points as {u, v}), filled, full bright. */
    void fill(PoseStack.Pose pose, VertexConsumer buffer, List<float[]> polygon, float w, int colour) {
        for (int i = 1; i + 1 < polygon.size(); i += 2) {
            float[] a = polygon.get(0);
            float[] b = polygon.get(i);
            float[] c = polygon.get(i + 1);
            float[] d = i + 2 < polygon.size() ? polygon.get(i + 2) : c;
            quad(pose, buffer, this.p(a[0], a[1], w), this.p(b[0], b[1], w), this.p(c[0], c[1], w), this.p(d[0], d[1], w),
                    0.0F, 0.0F, 1.0F, 1.0F, colour, FULL_BRIGHT, this.out);
        }
    }

    /**
     * An attitude display in the window (u0, v0)-(u1, v1) centred on (cu, cv): the ground side shaded, the horizon and a
     * pitch ladder every 5 degrees ({@code scale} pixels a degree), turned by the bank (its right end rises as the
     * aircraft banks right) and slid down by the nose's rise. {@code gap} leaves the middle of each line open, as a HUD's
     * are around the flight path marker; the horizon is {@code lineWidth} thick, the ladder two thirds of that.
     */
    void attitude(PoseStack.Pose pose, VertexConsumer buffer, float cu, float cv, float u0, float v0, float u1, float v1,
                  float pitchUp, float bank, float scale, float w, int rgb, float level, float groundAlpha, float gap,
                  float lineWidth) {
        float roll = bank * Mth.DEG_TO_RAD;
        float au = Mth.cos(roll);
        float av = -Mth.sin(roll);
        float nu = Mth.sin(roll);
        float nv = Mth.cos(roll);
        float hu = cu + nu * pitchUp * scale;
        float hv = cv + nv * pitchUp * scale;
        if (groundAlpha > 0.0F) {
            this.fill(pose, buffer, ground(hu, hv, nu, nv, u0, v0, u1, v1), w - 0.02F, argb(rgb, groundAlpha * level));
        }
        float span = (u1 - u0) + (v1 - v0);
        this.split(pose, buffer, hu, hv, au, av, span, gap, lineWidth, w, argb(rgb, level), u0, v0, u1, v1);
        for (int step = -4; step <= 4; step++) {
            if (step == 0) {
                continue;
            }
            float half = (step % 2 == 0 ? 0.55F : 0.3F) * scale / 0.075F;
            float lu = cu + nu * (pitchUp - step * 5.0F) * scale;
            float lv = cv + nv * (pitchUp - step * 5.0F) * scale;
            this.split(pose, buffer, lu, lv, au, av, half, Math.min(gap, half * 0.6F), lineWidth * 0.67F, w,
                    argb(rgb, 0.75F * level), u0, v0, u1, v1);
        }
    }

    /**
     * The ground side of the horizon in the window (u0, v0)-(u1, v1), filled in {@code colour}: the horizon placed as
     * {@link #attitude} places it, for a colour display (sky drawn first, then this over it).
     */
    void ground(PoseStack.Pose pose, VertexConsumer buffer, float cu, float cv, float u0, float v0, float u1, float v1,
                float pitchUp, float bank, float scale, float w, int colour) {
        float roll = bank * Mth.DEG_TO_RAD;
        float nu = Mth.sin(roll);
        float nv = Mth.cos(roll);
        this.fill(pose, buffer, ground(cu + nu * pitchUp * scale, cv + nv * pitchUp * scale, nu, nv, u0, v0, u1, v1), w, colour);
    }

    /** The window's corners on the ground side of a horizon through (hu, hv) with downward normal (nu, nv). */
    private static List<float[]> ground(float hu, float hv, float nu, float nv, float u0, float v0, float u1, float v1) {
        List<float[]> ground = new java.util.ArrayList<>();
        float[][] corners = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i];
            float[] b = corners[(i + 1) % 4];
            float da = (a[0] - hu) * nu + (a[1] - hv) * nv;
            float db = (b[0] - hu) * nu + (b[1] - hv) * nv;
            if (da >= 0.0F) {
                ground.add(a);
            }
            if ((da >= 0.0F) != (db >= 0.0F)) {
                float t = da / (da - db);
                ground.add(new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t});
            }
        }
        return ground;
    }

    /** Seven-segment digits' segments (a top, then clockwise, g the middle), as {x0, y0, x1, y1} of a 1 x 1 cell. */
    private static final float[][] SEGMENTS = {{0, 0, 1, 0}, {1, 0, 1, 0.5F}, {1, 0.5F, 1, 1}, {0, 1, 1, 1},
            {0, 0.5F, 0, 1}, {0, 0, 0, 0.5F}, {0, 0.5F, 1, 0.5F}};
    /** Which segments each digit lights, bit 0 = a. */
    private static final int[] DIGITS = {0b0111111, 0b0000110, 0b1011011, 0b1001111, 0b1100110, 0b1101101, 0b1111101,
            0b0000111, 0b1111111, 0b1101111};
    /** Stroked letters (polylines in the 1 x 1 cell) for a compass rose. */
    private static final Map<Character, float[]> LETTERS = Map.of(
            'N', new float[]{0, 1, 0, 0, 1, 1, 1, 0},
            'E', new float[]{1, 0, 0, 0, 0, 1, 1, 1},
            'W', new float[]{0, 0, 0.25F, 1, 0.5F, 0.4F, 0.75F, 1, 1, 0});

    /** The width {@link #text} takes for {@code s} at {@code height}. */
    static float textWidth(String s, float height) {
        float width = 0.0F;
        for (int i = 0; i < s.length(); i++) {
            width += s.charAt(i) == '.' ? height * 0.3F : height * 0.78F;
        }
        return Math.max(0.0F, width - height * 0.23F);
    }

    /**
     * Digits (seven-segment), '-', '.', and the letters N, E, S and W, {@code height} pixels tall, its top left at (u, v),
     * clipped to the window (cu0, cv0)-(cu1, cv1).
     */
    void text(PoseStack.Pose pose, VertexConsumer buffer, String s, float u, float v, float height, float w, int colour,
              float cu0, float cv0, float cu1, float cv1) {
        float cell = height * 0.55F;
        float stroke = height * 0.13F;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                this.line(pose, buffer, u + height * 0.05F, v + height - stroke * 0.5F, u + height * 0.05F + stroke,
                        v + height - stroke * 0.5F, stroke, w, colour, cu0, cv0, cu1, cv1);
                u += height * 0.3F;
                continue;
            }
            int mask = c >= '0' && c <= '9' ? DIGITS[c - '0'] : c == 'S' ? DIGITS[5] : c == '-' ? 0b1000000 : 0;
            for (int k = 0; k < 7; k++) {
                if ((mask >> k & 1) != 0) {
                    float[] seg = SEGMENTS[k];
                    this.line(pose, buffer, u + seg[0] * cell, v + seg[1] * height, u + seg[2] * cell, v + seg[3] * height,
                            stroke, w, colour, cu0, cv0, cu1, cv1);
                }
            }
            float[] strokes = LETTERS.get(c);
            if (strokes != null) {
                for (int k = 0; k + 3 < strokes.length; k += 2) {
                    this.line(pose, buffer, u + strokes[k] * cell, v + strokes[k + 1] * height, u + strokes[k + 2] * cell,
                            v + strokes[k + 3] * height, stroke, w, colour, cu0, cv0, cu1, cv1);
                }
                if (c == 'E') {
                    this.line(pose, buffer, u, v + height * 0.5F, u + cell * 0.8F, v + height * 0.5F, stroke, w, colour,
                            cu0, cv0, cu1, cv1);
                }
            }
            u += height * 0.78F;
        }
    }

    /** A rectangle's outline, {@code width} thick. */
    void outline(PoseStack.Pose pose, VertexConsumer buffer, float u0, float v0, float u1, float v1, float width, float w,
                 int colour) {
        float h = width * 0.5F;
        this.face(pose, buffer, u0 - h, v0 - h, u1 - u0 + width, width, w, colour);
        this.face(pose, buffer, u0 - h, v1 - h, u1 - u0 + width, width, w, colour);
        this.face(pose, buffer, u0 - h, v0 + h, width, v1 - v0 - width, w, colour);
        this.face(pose, buffer, u1 - h, v0 + h, width, v1 - v0 - width, w, colour);
    }

    /** A line through (u, v) along (au, av), {@code half} each way, with a gap of {@code gap} each side of the middle. */
    private void split(PoseStack.Pose pose, VertexConsumer buffer, float u, float v, float au, float av, float half,
                       float gap, float width, float w, int colour, float u0, float v0, float u1, float v1) {
        if (gap <= 0.0F) {
            this.line(pose, buffer, u - au * half, v - av * half, u + au * half, v + av * half, width, w, colour, u0, v0, u1, v1);
            return;
        }
        this.line(pose, buffer, u - au * half, v - av * half, u - au * gap, v - av * gap, width, w, colour, u0, v0, u1, v1);
        this.line(pose, buffer, u + au * gap, v + av * gap, u + au * half, v + av * half, width, w, colour, u0, v0, u1, v1);
    }

    /** A needle from the centre (u, v) out to {@code length} at {@code angle} radians clockwise from straight up. */
    void needle(PoseStack.Pose pose, VertexConsumer buffer, float u, float v, float length, float angle, float width,
                float w, int colour) {
        this.line(pose, buffer, u, v, u + Mth.sin(angle) * length, v - Mth.cos(angle) * length, width, w, colour,
                -1000.0F, -1000.0F, 1000.0F, 1000.0F);
    }

    /** Corners in order across the top then back along the bottom; drawn from both sides. */
    static void quad(PoseStack.Pose pose, VertexConsumer buffer, Vector3f a, Vector3f b, Vector3f c, Vector3f d,
                     float u0, float v0, float u1, float v1, int colour, int light, Vector3f normal) {
        vertex(pose, buffer, a, u0, v0, colour, light, normal);
        vertex(pose, buffer, b, u1, v0, colour, light, normal);
        vertex(pose, buffer, c, u1, v1, colour, light, normal);
        vertex(pose, buffer, d, u0, v1, colour, light, normal);
        vertex(pose, buffer, d, u0, v1, colour, light, normal);
        vertex(pose, buffer, c, u1, v1, colour, light, normal);
        vertex(pose, buffer, b, u1, v0, colour, light, normal);
        vertex(pose, buffer, a, u0, v0, colour, light, normal);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vector3f p, float u, float v, int colour,
                               int light, Vector3f normal) {
        buffer.addVertex(pose, p.x, p.y, p.z)
                .setColor(colour)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normal.x, normal.y, normal.z);
    }

    /** Opaque white dimmed to {@code level}: a screen texture shown at that brightness. */
    static int grey(float level) {
        int g = Math.round(255.0F * Mth.clamp(level, 0.0F, 1.0F));
        return 0xFF000000 | (g << 16) | (g << 8) | g;
    }

    static int argb(int rgb, float alpha) {
        return (Math.round(255.0F * Mth.clamp(alpha, 0.0F, 1.0F)) << 24) | (rgb & 0xFFFFFF);
    }

    /** Screens flicker on: off, a flicker, then up to full over the start-up ({@code t} 0..1). */
    static float bootLevel(float t) {
        if (t >= 1.0F) {
            return 1.0F;
        }
        if (t < 0.15F) {
            return 0.0F;
        }
        if (t < 0.3F) {
            return ((int) (t * 40.0F) & 1) == 0 ? 0.6F : 0.1F;
        }
        return Mth.clamp((t - 0.3F) / 0.7F, 0.0F, 1.0F) * 0.8F + 0.2F;
    }
}
