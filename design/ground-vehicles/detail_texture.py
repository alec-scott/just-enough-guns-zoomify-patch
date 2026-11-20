"""A detail and weathering pass over a refit vehicle's texture (the user's review, R7: "more texture and detail").

python -I design/ground-vehicles/detail_texture.py <vehicle> [--review-only]

SBW's textures paint most panels a flat colour at one or two texels a model pixel, which reads as plastic next to the
pack's shaded world. This pass works from the model: every texel is mapped back onto the face it textures (its place on
the hull in model pixels, the face's size and which way it faces), and is weathered by where it sits on the vehicle:

- the texture is first enlarged (nearest, so the original paint is kept texel for texel) to give the detail room;
- panel seams: a dark line round every face, and worn, lighter paint just inside it on the edges;
- a few bolt heads along the borders of big panels, lit from above;
- mottling in the paint (two scales of noise in model space, so it runs across faces as weathering would);
- grime streaks running down the vertical faces from their top edges;
- dust settled on the top faces; mud and dust thrown up on the lower hull, running gear and wheels.

The untouched texture is kept in design/<vehicle>/texture_base.png (taken from the mod the first time), so the pass can
be re-run and tuned without stacking. Glass, lamps, flashes and sights are left alone. A before/after render is written
to design/<vehicle>/review-texture.png.
"""
import math
import pathlib
import re
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "design/superbwarfare-port/triage"))
import render_geo  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/jeg"

# Per vehicle: the enlargement, the height (model px above the ground) below which mud collects, its strength, and
# the paint's wear. Ground vehicles sit with their wheels on y = 0.
VEHICLES = {
    "bmp2": dict(upscale=2, mud_height=16.0, mud=0.62, wear=1.0, design="bmp2"),
    "lav150": dict(upscale=4, mud_height=20.0, mud=0.62, wear=1.0, design="lav150"),
    # A ship's gun on a pad: no mud; salt-grey grime instead, the streaks stronger.
    "hpj11": dict(upscale=4, mud_height=0.0, mud=0.0, wear=0.8, streaks=1.4, design="hpj11"),
    # The fleet's model and texture review (2026-11-19): the vehicles still painted flat at one texel a model pixel.
    # Naval mounts: SBW painted the Mk42 a toy teal and the BL-132 a near-black blue. The Mk42 goes to haze grey (the US
    # Navy's 5-H on the Mk 42); the BL-132 to a navy blue-grey, darker than the Mk42 so the two read apart (the
    # user's review, 2026-11-19: "a darker navy colour to give it more personality"); under the pack's shaders in sun it
    # reads as a light steel blue, which the user chose over a deeper navy (2026-11-19); both salt-stained and
    # rust-streaked, which shows the more on the dark paint.
    "mk42": dict(upscale=4, mud_height=0.0, mud=0.0, wear=0.9, streaks=0.6, salt=0.45, rust=0.45, design="mk42",
                 skip="window|lens|lamp|light|glow|flash|screen|glass",
                 repaint=[dict(hue=(150, 220), sat=(0.06, 1.0), lum=(0.18, 0.80), to=(138, 144, 148), ref=0.54),
                          dict(hue=(150, 220), sat=(0.06, 1.0), lum=(0.0, 0.18), to=(52, 55, 57), ref=0.15)]),
    "bl132": dict(upscale=4, mud_height=0.0, mud=0.0, wear=0.9, streaks=0.6, salt=0.45, rust=0.4, design="bl132",
                  skip="lens|lamp|light|glow|flash|screen|glass",  # the director's dome (sight_tower_window) is painted
                  repaint=[dict(hue=(190, 225), sat=(0.08, 1.0), lum=(0.15, 0.6), to=(56, 70, 90), ref=0.30),
                           dict(hue=(190, 225), sat=(0.08, 1.0), lum=(0.0, 0.15), to=(30, 37, 48), ref=0.10)]),
    # A patrol boat, not a pleasure boat: SBW's white hull, teal stripes, red seats and light-cyan deck become haze grey
    # topsides, a dark boot stripe, black upholstery and a dark non-skid deck; the antifouling below the waterline stays
    # red-brown, duller.
    "speedboat": dict(upscale=2, mud_height=0.0, mud=0.0, wear=0.8, streaks=0.7, salt=0.6, rust=0.2, deck_grain=1.0,
                      design="speedboat", skip="glass|window|windscreen|lens|lamp|light|glow|flash|beacon|water_mask",
                      repaint=[dict(bones="seat|bench", hue=(340, 20), sat=(0.2, 1.0), to=(40, 42, 44), ref=0.38),
                               dict(bones="hull_lower", hue=(340, 20), sat=(0.2, 1.0), to=(104, 50, 42), ref=0.38),
                               dict(hue=(340, 20), sat=(0.25, 1.0), lum=(0.2, 0.6), to=(62, 66, 68), ref=0.38),
                               dict(hue=(175, 215), sat=(0.18, 1.0), lum=(0.3, 0.75), to=(58, 62, 64), ref=0.56),
                               dict(hue=(0, 360), sat=(0.0, 0.1), lum=(0.6, 0.9), to=(128, 134, 138), ref=0.75)]),
    # SBW's science-fiction tank, kept in its own white and blue (the user's decision, 2026-11-17), now as a vehicle
    # in service: seams, worn edges, grime and mud. The cyan strips and drone cells stay as drawn.
    "yx100": dict(upscale=2, mud_height=22.0, mud=0.6, wear=1.0, paint_lum=0.97, paint_sat=0.55, design="yx100",
                  protect=[dict(hue=(170, 200), sat=(0.45, 1.0))]),
    "plz05": dict(upscale=4, mud_height=18.0, mud=0.6, wear=0.9, paint_lum=0.92, design="plz05"),
    "type63": dict(upscale=4, mud_height=10.0, mud=0.55, wear=1.0, design="type63"),
    "tow": dict(upscale=4, mud_height=6.0, mud=0.4, wear=0.8, design="tow"),
    # Aircraft: no mud.
    # SBW painted the Mi-28 a toy teal; the Russian Mi-28N flies in a dark grey-green.
    "mi28": dict(upscale=4, mud_height=0.0, mud=0.0, wear=0.7, streaks=0.8, design="mi28",
                 skip="glass|window|lens|lamp|light|glow|flash|screen",
                 repaint=[dict(hue=(185, 215), sat=(0.15, 1.0), lum=(0.2, 0.75), to=(96, 104, 92), ref=0.49)]),
    "ah6": dict(upscale=2, mud_height=0.0, mud=0.0, wear=0.7, streaks=0.8, design="ah6"),
}
# Faces that keep their paint exactly: glass and lenses, lamps, muzzle flashes, sights.
SKIP = re.compile(r"glass|window|lens|lamp|light|glow|flash|sight|periscope|screen", re.I)
# Running gear: dirt only (no seams, bolts or worn edges on rubber and track).
RUNNING_GEAR = re.compile(r"wheel|track|sprocket|idler|tyre|tire|shoe", re.I)

MUD = np.array([0.33, 0.28, 0.21])
DUST = np.array([0.60, 0.56, 0.46])
SALT = np.array([0.86, 0.87, 0.85])
RUST = np.array([0.46, 0.26, 0.13])


def hls(rgb):
    """Hue (degrees), lightness and saturation (HLS) of N x 3 colours in 0..1."""
    mx, mn = rgb.max(1), rgb.min(1)
    light = (mx + mn) / 2
    d = mx - mn
    sat = np.where(d < 1e-9, 0.0, d / np.maximum(1e-9, 1 - np.abs(2 * light - 1)))
    r, g, b = rgb[:, 0], rgb[:, 1], rgb[:, 2]
    safe = np.maximum(d, 1e-9)
    hue = np.where(mx == r, ((g - b) / safe) % 6, np.where(mx == g, (b - r) / safe + 2, (r - g) / safe + 4)) * 60.0
    return np.where(d < 1e-9, 0.0, hue), light, sat


def in_hue(hue, band):
    lo, hi = band
    return (hue >= lo) & (hue <= hi) if lo <= hi else (hue >= lo) | (hue <= hi)


def matches(rule, hue, light, sat):
    m = np.ones(hue.shape, bool)
    if "hue" in rule:
        m &= in_hue(hue, rule["hue"])
    if "sat" in rule:
        m &= (sat >= rule["sat"][0]) & (sat <= rule["sat"][1])
    if "lum" in rule:
        m &= (light >= rule["lum"][0]) & (light <= rule["lum"][1])
    return m


def apply_stain(stain, name, normal, P, rgb):
    """A local stain laid over the weathered paint (the BD-40's soot, rust and soil; the A-10's gun gas and exhaust):
    {colour} at {strength}, limited to bones matching {bones}, to faces turned towards {facing} (dot over {dot}), and
    fading out from {centre} over {radius} (model px, the same space as the quads); {noise} breaks it into patches of
    that size, {streak} runs it down vertical faces in streaks (or aft, along {streak} as a direction)."""
    if "bones" in stain and not re.search(stain["bones"], name, re.I):
        return rgb
    if "facing" in stain and float(np.dot(normal, np.array(stain["facing"], float))) < stain.get("dot", 0.5):
        return rgb
    w = np.full(len(P), float(stain["strength"]))
    if "centre" in stain:
        d = np.linalg.norm(P - np.array(stain["centre"], float)[None], axis=1)
        w *= np.clip(1.0 - d / stain["radius"], 0.0, 1.0) ** stain.get("falloff", 1.0)
    if "below" in stain:
        w *= P[:, 1] < stain["below"]
    if "noise" in stain:
        w *= np.clip(value_noise(P, 1.0 / stain["noise"], stain.get("seed", 21)) * 1.7 - 0.45, 0.0, 1.0)
    if stain.get("streak"):
        along = np.array(stain["streak"] if isinstance(stain["streak"], (list, tuple)) else (0.0, -1.0, 0.0), float)
        across = np.cross(normal, along)
        n = np.linalg.norm(across)
        column = P @ (across / n) if n > 1e-6 else P[:, 0]
        runs = value_noise(np.c_[column, np.zeros_like(column), np.full_like(column, 5.0)], 1 / 0.6, stain.get("seed", 21) + 1)
        w *= np.clip((runs - 0.4) * 2.5, 0.0, 1.0)
    if not w.any():
        return rgb
    colour = np.array(stain["colour"], float) / 255.0
    return rgb * (1 - w[:, None]) + colour[None] * w[:, None]


def repaint(rgb, name, rules):
    """New paint over old: each rule's texels take its colour, shaded as the old paint was (lightness against the
    rule's reference lightness), so the drawn shading, seams and highlights survive the change of colour."""
    hue, light, sat = hls(rgb)
    done = np.zeros(len(rgb), bool)
    for rule in rules:
        if "bones" in rule and not re.search(rule["bones"], name, re.I):
            continue
        m = matches(rule, hue, light, sat) & ~done
        if not m.any():
            continue
        shade = np.clip(light[m] / rule["ref"], 0.35, 1.6)
        rgb[m] = np.clip(np.array(rule["to"], float)[None] / 255.0 * shade[:, None], 0, 1)
        done |= m
    return rgb


def value_noise(p, freq, seed):
    """Smooth value noise in 3D at points p (N x 3), 0..1."""
    q = p * freq
    i = np.floor(q)
    f = q - i
    f = f * f * (3.0 - 2.0 * f)

    def h(dx, dy, dz):
        n = (i[:, 0] + dx) * 127.1 + (i[:, 1] + dy) * 311.7 + (i[:, 2] + dz) * 74.7 + seed * 19.19
        return np.modf(np.abs(np.sin(n) * 43758.5453))[0]

    x00 = h(0, 0, 0) * (1 - f[:, 0]) + h(1, 0, 0) * f[:, 0]
    x10 = h(0, 1, 0) * (1 - f[:, 0]) + h(1, 1, 0) * f[:, 0]
    x01 = h(0, 0, 1) * (1 - f[:, 0]) + h(1, 0, 1) * f[:, 0]
    x11 = h(0, 1, 1) * (1 - f[:, 0]) + h(1, 1, 1) * f[:, 0]
    y0 = x00 * (1 - f[:, 1]) + x10 * f[:, 1]
    y1 = x01 * (1 - f[:, 1]) + x11 * f[:, 1]
    return y0 * (1 - f[:, 2]) + y1 * f[:, 2]


def detail(vehicle):
    cfg = VEHICLES[vehicle]
    design = ROOT / "design" / cfg["design"]
    tex_path = ASSETS / f"textures/entity/vehicle/{vehicle}.png"
    base_path = design / "texture_base.png"
    if not base_path.exists():
        shutil.copyfile(tex_path, base_path)
    base = Image.open(base_path).convert("RGBA")
    geo_path = ASSETS / f"geckolib/models/entity/vehicle/{vehicle}.geo.json"
    result, quads, size = detail_image(str(geo_path), base, cfg)
    return base, result, quads, size


def detail_image(geo, base, cfg):
    """The weathering pass over {base}, the texture of {geo} (a .geo.json path or a loaded geometry): returns the
    detailed texture (enlarged by cfg["upscale"]), the model's visible quads and its UV size. Our own generators call
    this on the texture they bake (refit_lib.bake_unique), the refits through detail()."""
    k_up = cfg["upscale"]
    big = base.resize((base.width * k_up, base.height * k_up), Image.NEAREST)
    src = np.asarray(big, float) / 255.0
    out = src.copy()
    done = np.zeros(src.shape[:2], bool)

    quads, (tw, th), _, _ = render_geo.load_quads(geo)
    quads = render_geo.visible(quads, base, (tw, th))
    kx, ky = big.width / tw, big.height / th
    # Biggest faces first, so where faces share texels the panel they belong to sets them.
    quads.sort(key=lambda q: -np.linalg.norm(np.cross(q[2][1] - q[2][0], q[2][3] - q[2][0])))
    streak_gain = cfg.get("streaks", 1.0)

    skip = re.compile(cfg["skip"], re.I) if "skip" in cfg else SKIP
    for name, face, pts, uv in quads:
        if skip.search(name):
            continue
        gear = bool(RUNNING_GEAR.search(name))
        (u1, v1), (u2, _), (_, v2) = uv[0], uv[1], uv[2]
        x0, x1 = sorted((u1 * kx, u2 * kx))
        y0, y1 = sorted((v1 * ky, v2 * ky))
        xs = np.arange(int(math.floor(x0)), int(math.ceil(x1)))
        ys = np.arange(int(math.floor(y0)), int(math.ceil(y1)))
        xs = xs[(xs >= 0) & (xs < big.width)]
        ys = ys[(ys >= 0) & (ys < big.height)]
        if xs.size == 0 or ys.size == 0:
            continue
        gx, gy = np.meshgrid(xs, ys)
        gx, gy = gx.ravel(), gy.ravel()
        keep = ~done[gy, gx] & (src[gy, gx, 3] > 0.5)
        if not keep.any():
            continue
        gx, gy = gx[keep], gy[keep]
        s = ((gx + 0.5) / kx - u1) / (u2 - u1)
        t = ((gy + 0.5) / ky - v1) / (v2 - v1)
        inside = (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1)
        gx, gy, s, t = gx[inside], gy[inside], s[inside], t[inside]
        if gx.size == 0:
            continue
        a, b, c, d = pts
        P = (a[None] * ((1 - s) * (1 - t))[:, None] + b[None] * (s * (1 - t))[:, None]
             + c[None] * (s * t)[:, None] + d[None] * ((1 - s) * t)[:, None])
        e1, e2 = b - a, d - a
        width, height = np.linalg.norm(e1), np.linalg.norm(e2)
        normal = np.cross(e2, e1)
        ln = np.linalg.norm(normal)
        if ln < 1e-9 or width < 1e-6 or height < 1e-6:
            continue
        normal /= ln
        rgb = out[gy, gx, :3]
        if "repaint" in cfg and not gear:
            rgb = repaint(rgb.copy(), name, cfg["repaint"])
        lum = rgb @ np.array([0.3, 0.59, 0.11])
        sat = rgb.max(1) - rgb.min(1)
        # Leave bright or strongly coloured texels (markings, lamps, glass) as painted.
        paint = (lum < cfg.get("paint_lum", 0.72)) & (sat < cfg.get("paint_sat", 0.32))
        if "protect" in cfg:
            hue_, light_, sat_ = hls(rgb)
            for rule in cfg["protect"]:
                paint &= ~matches(rule, hue_, light_, sat_)
        factor = np.ones(gx.size)

        # Mottled paint: broad patches and a fine grain, continuous across faces.
        broad = value_noise(P, 1 / 9.0, 1) - 0.5
        fine = value_noise(P, 1 / 1.6, 2) - 0.5
        grain = value_noise(P, 1 / 0.45, 3) - 0.5
        factor *= 1 + 0.13 * broad + 0.07 * fine + 0.04 * grain

        border = np.minimum.reduce([s * width, (1 - s) * width, t * height, (1 - t) * height])
        if not gear and min(width, height) >= 2.0:
            wear_mask = value_noise(P, 1 / 1.1, 4)
            seam = border < 0.28
            factor[seam] *= 0.74
            worn = (border >= 0.28) & (border < 0.85) & (wear_mask > 0.5)
            factor[worn] *= 1 + 0.14 * cfg["wear"] * np.clip((wear_mask[worn] - 0.5) * 3.0, 0, 1)
            # Bolt heads every 2.4 px along the borders of large panels, 0.8 px in.
            if min(width, height) >= 6.0 and cfg.get("bolts", True):
                us, vs = s * width, t * height
                inset = 0.8
                near_edge_u = np.minimum(us, width - us)
                near_edge_v = np.minimum(vs, height - vs)
                du = np.abs(((us - inset) / 2.4) - np.round((us - inset) / 2.4)) * 2.4
                dv = np.abs(((vs - inset) / 2.4) - np.round((vs - inset) / 2.4)) * 2.4
                on_v_edge = np.abs(near_edge_u - inset) < 0.24
                on_u_edge = np.abs(near_edge_v - inset) < 0.24
                bolt = (on_v_edge & (dv < 0.24)) | (on_u_edge & (du < 0.24))
                factor[bolt] *= 1.22
                shadow = ((np.abs(near_edge_u - inset - 0.22) < 0.12) & (dv < 0.26)) | \
                         ((np.abs(near_edge_v - inset - 0.22) < 0.12) & (du < 0.26))
                factor[shadow & ~bolt] *= 0.8

        up = normal[1]
        y = P[:, 1]
        top = max(a[1], b[1], c[1], d[1])
        if abs(up) < 0.55:
            # Grime run down from the top edge in streaks; the column follows the face's horizontal direction.
            horiz = np.cross(normal, [0.0, 1.0, 0.0])
            hn = np.linalg.norm(horiz)
            column = P @ (horiz / hn) if hn > 1e-6 else P[:, 0]
            streak = value_noise(np.c_[column, np.zeros_like(column), np.full_like(column, 7.0)], 1 / 0.7, 5)
            streak = np.clip((streak - 0.45) * 2.2, 0, 1) * np.exp(-np.clip(top - y, 0, None) / 10.0)
            factor *= 1 - 0.16 * streak_gain * streak
        new = rgb * factor[:, None]
        if abs(up) < 0.55 and (cfg.get("rust", 0.0) > 0 or cfg.get("salt", 0.0) > 0):
            # At sea: thin rust runs from the face's top edge, and salt dried along its foot.
            horiz = np.cross(normal, [0.0, 1.0, 0.0])
            hn = np.linalg.norm(horiz)
            column = P @ (horiz / hn) if hn > 1e-6 else P[:, 0]
            runs = value_noise(np.c_[column, np.zeros_like(column), np.full_like(column, 3.0)], 1 / 0.55, 11)
            fall = np.exp(-np.clip(top - y, 0, None) / 6.0)
            rust = np.clip((runs - 0.82) * 5.0, 0, 1) * fall * cfg.get("rust", 0.0)
            rust *= 0.6 + 0.4 * value_noise(P, 1 / 0.6, 12)
            new = new * (1 - rust[:, None]) + RUST[None] * rust[:, None]
            bottom = min(a[1], b[1], c[1], d[1])
            salt = np.clip(1 - (y - bottom) / 3.0, 0, 1) * np.clip(value_noise(P, 1 / 1.2, 13) - 0.3, 0, 1)
            salt = salt * cfg.get("salt", 0.0) * 0.5
            new = new * (1 - salt[:, None]) + SALT[None] * salt[:, None]
        if up > 0.6 and cfg.get("deck_grain", 0.0) > 0:
            # Non-skid deck: a coarse grit.
            grit = value_noise(P, 1 / 0.6, 14) - 0.5
            new = new * (1 + 0.1 * cfg["deck_grain"] * grit)[:, None]

        if up > 0.6:
            dust = np.clip(value_noise(P, 1 / 3.0, 6) - 0.35, 0, 1) * 0.22
            new = new * (1 - dust[:, None]) + DUST[None] * dust[:, None]
        if cfg["mud_height"] > 0:
            low = np.clip((cfg["mud_height"] - y) / cfg["mud_height"], 0, 1) ** 1.2
            splatter = np.clip(value_noise(P, 1 / 1.4, 7) * 1.4 - 0.2, 0, 1)
            mud = cfg["mud"] * low * (0.35 + 0.65 * splatter) * (1.15 if gear else 1.0)
            mud = np.clip(mud, 0, 0.8)
            new = new * (1 - mud[:, None]) + MUD[None] * mud[:, None]

        for stain in cfg.get("stains", ()):
            new = apply_stain(stain, name, normal, P, new)
        rgb[paint] = np.clip(new[paint], 0, 1)
        out[gy, gx, :3] = rgb
        done[gy, gx] = True

    result = Image.fromarray((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")
    return result, quads, (tw, th)


def review(vehicle, base, result, tw_th):
    geo_path = ASSETS / f"geckolib/models/entity/vehicle/{vehicle}.geo.json"
    quads, tex_size, _, _ = render_geo.load_quads(str(geo_path))
    quads = render_geo.visible(quads, base, tex_size)
    tiles = []
    for label, tex in (("before", base), ("after", result)):
        for yaw, pitch in ((35, 25), (215, 20)):
            img = render_geo.render(quads, tex, tex_size, yaw, pitch, size=(720, 520))
            ImageDraw.Draw(img).text((10, 8), f"{label}  yaw {yaw}", fill=(20, 20, 20), font=render_geo.font(16))
            tiles.append(img)
    w, h = tiles[0].size
    sheet = Image.new("RGB", (w * 2 + 6, h * 2 + 6), (255, 255, 255))
    for i, tile in enumerate(tiles):
        sheet.paste(tile, ((i % 2) * (w + 6), (i // 2) * (h + 6)))
    return sheet


def main():
    vehicle = sys.argv[1]
    base, result, quads, tw_th = detail(vehicle)
    design = ROOT / "design" / VEHICLES[vehicle]["design"]
    review(vehicle, base, result, tw_th).save(design / "review-texture.png", optimize=True)
    if "--review-only" not in sys.argv:
        result.save(ASSETS / f"textures/entity/vehicle/{vehicle}.png", optimize=True)
    print(vehicle, base.size, "->", result.size)


if __name__ == "__main__":
    main()
