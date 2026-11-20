"""A seat's view of a GeckoLib model, for placing cockpit instruments: a perspective render from a point, texture
mapped, depth tested, in GeckoLib render pixels (the space a GeoRenderLayer draws in: forward -Z, the right +X).

python -I design/ah6/render_seat_view.py <geo.json> <texture.png> <out.png> <eye x> <eye y> <eye z> [pitch down]

The AH-6 pilot's eye is about (-7.2, 40, -16): compare with a game screenshot from the seat.
"""
import json, sys, math
sys.path.insert(0, "design/superbwarfare-port/triage")
import numpy as np
from PIL import Image, ImageDraw
import render_geo as R

def view(geo, tex_path, eye, yaw_deg=0.0, pitch_deg=0.0, fov=70.0, size=(854, 480), out=None, labels=False):
    quads, (tw, th), bones, _ = R.load_quads(geo)
    tex = Image.open(tex_path).convert("RGBA")
    kx, ky = tex.size[0] / tw, tex.size[1] / th
    W, H = size
    f = (H / 2) / math.tan(math.radians(fov / 2))
    eye = np.array(eye, float)
    # camera looks along -Z (render forward), yaw turns left, pitch down positive
    cy, sy = math.cos(math.radians(yaw_deg)), math.sin(math.radians(yaw_deg))
    cp, sp = math.cos(math.radians(pitch_deg)), math.sin(math.radians(pitch_deg))
    fwd = np.array([-sy * cp, -sp, -cy * cp])
    right = np.array([cy, 0.0, -sy])
    up = np.cross(right, fwd)
    img = Image.new("RGBA", size, (150, 190, 230, 255))
    canvas = np.array(img)
    zbuf = np.full((H, W), np.inf)
    texa = np.asarray(tex)
    for name, face, pts, uv in quads:
        rel = pts - eye
        z = rel @ fwd
        if (z < 0.5).any():
            continue
        x = rel @ right
        y = rel @ up
        sx = W / 2 + f * x / z
        sy_ = H / 2 - f * y / z
        # rasterize quad as two triangles with uv interpolation (affine in screen space is fine at this scale; use perspective-correct)
        P = np.stack([sx, sy_], 1)
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = (P[i] for i in tri)
            za, zb, zc = (z[i] for i in tri)
            ua, ub, uc = (uv[i] for i in tri)
            x0, x1 = int(max(0, math.floor(min(a[0], b[0], c[0])))), int(min(W - 1, math.ceil(max(a[0], b[0], c[0]))))
            y0, y1 = int(max(0, math.floor(min(a[1], b[1], c[1])))), int(min(H - 1, math.ceil(max(a[1], b[1], c[1]))))
            if x0 > x1 or y0 > y1:
                continue
            area = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
            if abs(area) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            w0 = ((b[0] - gx) * (c[1] - gy) - (b[1] - gy) * (c[0] - gx)) / area
            w1 = ((c[0] - gx) * (a[1] - gy) - (c[1] - gy) * (a[0] - gx)) / area
            w2 = 1 - w0 - w1
            inside = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
            if not inside.any():
                continue
            iz = w0 / za + w1 / zb + w2 / zc
            depth = 1 / iz
            u = (w0 * ua[0] / za + w1 * ub[0] / zb + w2 * uc[0] / zc) * depth
            v = (w0 * ua[1] / za + w1 * ub[1] / zb + w2 * uc[1] / zc) * depth
            tu = np.clip((u * kx).astype(int), 0, tex.size[0] - 1)
            tv = np.clip((v * ky).astype(int), 0, tex.size[1] - 1)
            col = texa[tv, tu]
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            ok = inside & (depth < sub) & (col[..., 3] > 127)
            sub[ok] = depth[ok]
            canvas[y0:y1 + 1, x0:x1 + 1][ok] = col[ok]
    img = Image.fromarray(canvas)
    if out:
        img.save(out)
    return img

if __name__ == "__main__":
    geo = sys.argv[1]; tex = sys.argv[2]; out = sys.argv[3]
    ex, ey, ez = map(float, sys.argv[4:7])
    pitch = float(sys.argv[7]) if len(sys.argv) > 7 else 0.0
    view(geo, tex, (ex, ey, ez), 0.0, pitch, out=out)
