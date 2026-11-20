"""Textures for the AH-6's instrument panel (Ah6CockpitLayer), a glass cockpit in the style of the Little Bird's: two
colour displays either side of a centre stack, a moving-map display over two standby gauges, and a weapons display on
the console between the seats.

python -I design/ah6/make_cockpit.py

Writes to assets/jeg/textures/entity/vehicle/cockpit/. The model's dash faces (in GeckoLib render pixels, from
design/ah6's measurements of ah6.geo.json) and every instrument's place on them are in LAYOUT, in model pixels (u across
from the face's centre, v down from its top edge); keep them in step with Ah6CockpitLayer.

- ah6_centre_panel.png, ah6_side_panel.png, ah6_console_panel.png: the faces themselves, matte black-grey with bezels,
  push-buttons, gauge rims, lamp sockets and switches, lit by the world.
- ah6_pfd.png: the left display's overlay, drawn over the live attitude: speed and altitude tape frames, the bank scale,
  the fixed aircraft symbol, labels. Transparent where the horizon shows.
- ah6_eng.png: the right display's engine page: bar frames and labels (NR, NP, TQ, TOT, FUEL); the layer fills the bars
  and writes the digits above them.
- ah6_nav.png: the centre display's navigation page: dark ground, range rings and a grid; the layer turns the compass
  rose with the heading and writes the heading.
- ah6_armt.png: the weapons page on the console: the helicopter seen from above with its gun and rocket pods, labels; the
  layer writes the counts and boxes the selected weapon.
- ah6_asi.png, ah6_alt.png: the standby airspeed (0..160 knots, -135..+135 degrees) and altimeter (a needle a thousand
  feet a turn) dials, backlit.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/jeg/textures/entity/vehicle/cockpit"
INTER = ROOT / "design/hud/Inter-SemiBold.ttf"

# The faces: (width, height) in model pixels. LAYOUT entries are u, v, width, height (rectangles, u of the left edge)
# or centre u, v, radius (dials).
FACES = {"centre": (5.46, 10.227), "side": (5.362, 3.9), "console": (5.46, 7.8)}
LAYOUT = {
    "centre.mfd": (-2.38, 0.42, 4.76, 4.76),
    "centre.asi": (-1.33, 7.0, 1.18),
    "centre.alt": (1.33, 7.0, 1.18),
    "centre.lamps": [(-2.5 + i * 1.02, 8.62, 0.86, 0.42) for i in range(5)],
    "side.screen": (-2.43, 0.22, 4.86, 3.2),
    "console.screen": (-2.33, 0.3, 4.66, 3.8),
    "console.radio": (-2.1, 4.55, 3.0, 0.8),
}
TEXEL = 24  # texels a model pixel on the panel faces

PANEL = (34, 36, 38, 255)
PANEL_EDGE = (22, 23, 25, 255)
BEZEL = (18, 19, 21, 255)
KEY = (58, 61, 64, 255)
KEY_TOP = (84, 88, 92, 255)
LABEL = (200, 202, 196, 255)
GLASS = (8, 10, 12, 255)
WHITE = (238, 240, 236, 255)
GREEN = (90, 255, 140, 255)
CYAN = (90, 220, 255, 255)
MAGENTA = (255, 90, 230, 255)
AMBER = (255, 190, 60, 255)
DIM = (120, 128, 132, 255)


def font(size):
    try:
        return ImageFont.truetype(str(INTER), size)
    except OSError:
        return ImageFont.load_default(size=size)


def face_image(name):
    w, h = FACES[name]
    return Image.new("RGBA", (round(w * TEXEL), round(h * TEXEL)), PANEL)


def to_tex(face, u, v):
    """Model pixels (u from the face's centre, v from its top) to texels."""
    w, _ = FACES[face]
    return (u + w / 2) * TEXEL, v * TEXEL


def rect(face, box):
    u, v, w, h = box
    x0, y0 = to_tex(face, u, v)
    return [x0, y0, x0 + w * TEXEL, y0 + h * TEXEL]


def grain(img, amount=6, seed=3):
    """A little paint grain and edge wear, so the faces aren't flat colour."""
    import random
    rnd = random.Random(seed)
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            n = rnd.randint(-amount, amount)
            px[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)), a)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, img.width - 1, img.height - 1], outline=PANEL_EDGE, width=2)


def key(d, box, label=None, size=9):
    x0, y0, x1, y1 = box
    d.rounded_rectangle(box, radius=2, fill=KEY, outline=BEZEL)
    d.line([x0 + 2, y0 + 1, x1 - 2, y0 + 1], fill=KEY_TOP)
    if label:
        f = font(size)
        tw = d.textlength(label, font=f)
        d.text(((x0 + x1 - tw) / 2, (y0 + y1) / 2 - size * 0.62), label, font=f, fill=LABEL)


def bezel(d, box, keys_top=0, keys_bottom=0, labels_top=(), labels_bottom=()):
    x0, y0, x1, y1 = box
    d.rounded_rectangle([x0 - 7, y0 - 7, x1 + 7, y1 + 7], radius=5, fill=BEZEL)
    d.rectangle([x0, y0, x1, y1], fill=GLASS)
    for row, count, labels in ((-1, keys_top, labels_top), (1, keys_bottom, labels_bottom)):
        if not count:
            continue
        span = (x1 - x0) / count
        for i in range(count):
            kx0 = x0 + i * span + 5
            kx1 = x0 + (i + 1) * span - 5
            ky0, ky1 = (y0 - 6, y0 - 1) if row < 0 else (y1 + 1, y1 + 6)
            key(d, [kx0, ky0, kx1, ky1])
            if labels and i < len(labels):
                f = font(8)
                tw = d.textlength(labels[i], font=f)
                ty = y0 + 2 if row < 0 else y1 - 11
                d.text(((kx0 + kx1 - tw) / 2, ty), labels[i], font=f, fill=CYAN)


def dial_rim(d, face, centre):
    cu, cv, r = centre
    cx, cy = to_tex(face, cu, cv)
    rr = (r + 0.12) * TEXEL
    d.ellipse([cx - rr - 3, cy - rr - 3, cx + rr + 3, cy + rr + 3], fill=BEZEL)
    d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(46, 48, 50, 255))
    r0 = r * TEXEL
    d.ellipse([cx - r0, cy - r0, cx + r0, cy + r0], fill=GLASS)
    # Four screws.
    for a in (45, 135, 225, 315):
        sx = cx + (rr + 1) * math.cos(math.radians(a))
        sy = cy + (rr + 1) * math.sin(math.radians(a))
        d.ellipse([sx - 2, sy - 2, sx + 2, sy + 2], fill=(90, 92, 94, 255))


def centre_panel():
    img = face_image("centre")
    grain(img)
    d = ImageDraw.Draw(img)
    bezel(d, rect("centre", LAYOUT["centre.mfd"]), keys_top=4, keys_bottom=4)
    for k in ("centre.asi", "centre.alt"):
        dial_rim(d, "centre", LAYOUT[k])
    for box in LAYOUT["centre.lamps"]:
        x0, y0, x1, y1 = rect("centre", box)
        d.rounded_rectangle([x0 - 2, y0 - 2, x1 + 2, y1 + 2], radius=2, fill=BEZEL)
        d.rectangle([x0, y0, x1, y1], fill=(26, 27, 28, 255))
    # Three guarded toggles along the bottom, and their labels.
    f = font(8)
    for i, label in enumerate(("BAT", "GEN", "AVN")):
        cx, cy = to_tex("centre", -2.1 + i * 1.6, 9.62)
        d.rectangle([cx - 7, cy - 5, cx + 7, cy + 5], fill=BEZEL)
        d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=(150, 152, 150, 255))
        d.line([cx, cy, cx, cy - 9], fill=(190, 192, 190, 255), width=3)
        tw = d.textlength(label, font=f)
        d.text((cx + 9, cy - 5), label, font=f, fill=LABEL)
    img.save(OUT / "ah6_centre_panel.png")


def side_panel():
    img = face_image("side")
    grain(img, seed=5)
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = rect("side", LAYOUT["side.screen"])
    d.rounded_rectangle([x0 - 5, y0 - 5, x1 + 5, y1 + 5], radius=4, fill=BEZEL)
    d.rectangle([x0, y0, x1, y1], fill=GLASS)
    # A row of push-buttons under the screen, and a brightness rocker.
    span = (x1 - x0 - 20) / 5
    for i in range(5):
        key(d, [x0 + i * span + 3, y1 + 6, x0 + (i + 1) * span - 3, img.height - 4])
    key(d, [x1 - 16, y1 + 6, x1, img.height - 4])
    img.save(OUT / "ah6_side_panel.png")


def console_panel():
    img = face_image("console")
    grain(img, seed=7)
    d = ImageDraw.Draw(img)
    bezel(d, rect("console", LAYOUT["console.screen"]), keys_bottom=4)
    # The radio's control head: its window (lit by the layer), two knobs.
    x0, y0, x1, y1 = rect("console", LAYOUT["console.radio"])
    d.rounded_rectangle([x0 - 5, y0 - 5, x1 + 5, y1 + 5], radius=3, fill=BEZEL)
    d.rectangle([x0, y0, x1, y1], fill=GLASS)
    for cu in (1.45, 2.15):
        cx, cy = to_tex("console", cu, 4.95)
        d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=(20, 21, 22, 255))
        d.ellipse([cx - 7, cy - 7, cx + 7, cy + 7], fill=(64, 66, 68, 255))
        d.line([cx, cy, cx, cy - 7], fill=WHITE, width=2)
    # Switches and their labels: master arm (red guard), the pods' select, lights.
    f = font(9)
    for i, (label, guard) in enumerate((("ARM", True), ("GUN", False), ("RKT", False), ("CM", False))):
        cx, cy = to_tex("console", -1.9 + i * 1.25, 6.35)
        if guard:
            d.rectangle([cx - 10, cy - 12, cx + 10, cy + 12], fill=(150, 30, 26, 255), outline=(90, 16, 14, 255))
        d.rectangle([cx - 6, cy - 8, cx + 6, cy + 8], fill=BEZEL)
        d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=(160, 162, 160, 255))
        d.line([cx, cy, cx, cy - 10], fill=(200, 202, 200, 255), width=3)
        lf = font(7) if len(label) > 4 else f
        tw = d.textlength(label, font=lf)
        d.text((cx - tw / 2, cy + 14), label, font=lf, fill=LABEL)
    img.save(OUT / "ah6_console_panel.png")


# ------------------------------------------------------------------ screens

def screen(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


def text(d, xy, s, size, fill, anchor="la"):
    d.text(xy, s, font=font(size), fill=fill, anchor=anchor)


def pfd():
    """Overlay for the attitude display (256 x 168, the screen 4.86 x 3.2 px): the tapes sit over the horizon."""
    w, h = 256, 168
    img = screen(w, h)
    d = ImageDraw.Draw(img)
    tape = (10, 12, 16, 200)
    # Speed tape on the left, altitude tape on the right, each with a pointer box (the layer writes the digits).
    d.rectangle([4, 26, 44, 142], fill=tape, outline=(200, 205, 210, 140))
    d.rectangle([212, 26, 252, 142], fill=tape, outline=(200, 205, 210, 140))
    for y in range(34, 142, 12):
        d.line([36, y, 44, y], fill=(220, 224, 226, 200), width=2)
        d.line([212, y, 220, y], fill=(220, 224, 226, 200), width=2)
    for x0, x1, tip in ((2, 46, 52), (210, 254, 204)):
        d.polygon([(x0, 74), (x1, 74), (tip, 84), (x1, 94), (x0, 94)] if tip > x1 else
                  [(x1, 74), (x0, 74), (tip, 84), (x0, 94), (x1, 94)], fill=(0, 0, 0, 235), outline=WHITE)
    text(d, (24, 6), "KTS", 13, CYAN, "ma")
    text(d, (232, 6), "FT", 13, CYAN, "ma")
    # The bank scale: ticks at 10, 20, 30, 45 and 60 degrees either side of the top, on an arc over the centre.
    cx, cy, r = 128, 98, 66
    for a in (-60, -45, -30, -20, -10, 10, 20, 30, 45, 60):
        long = abs(a) in (30, 60)
        x0 = cx + r * math.sin(math.radians(a))
        y0 = cy - r * math.cos(math.radians(a))
        x1 = cx + (r + (10 if long else 6)) * math.sin(math.radians(a))
        y1 = cy - (r + (10 if long else 6)) * math.cos(math.radians(a))
        d.line([x0, y0, x1, y1], fill=WHITE, width=2)
    d.polygon([(cx, cy - r), (cx - 5, cy - r - 9), (cx + 5, cy - r - 9)], fill=WHITE)
    # The aircraft symbol, fixed: wings and the centre dot, in yellow with a black edge.
    yellow = (255, 220, 40, 255)
    for side in (-1, 1):
        pts = [(cx + side * 22, 82), (cx + side * 52, 82), (cx + side * 52, 88), (cx + side * 28, 88), (cx + side * 28, 96),
               (cx + side * 22, 96)]
        d.polygon(pts, fill=yellow, outline=(0, 0, 0, 255))
    d.rectangle([cx - 4, 82, cx + 4, 88], fill=yellow, outline=(0, 0, 0, 255))
    # Heading box at the bottom (the layer writes the heading).
    d.rectangle([104, 146, 152, 166], fill=(0, 0, 0, 235), outline=WHITE)
    text(d, (100, 156), "HDG", 10, CYAN, "rm")
    img.save(OUT / "ah6_pfd.png")


ENG_BARS = [("NR", 26), ("NP", 74), ("TQ", 122), ("TOT", 170), ("FUEL", 218)]
ENG_BAR = (40, 136, 18)  # top, bottom, width, in texels of the 256 x 168 page


def eng():
    w, h = 256, 168
    img = screen(w, h)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], fill=(6, 8, 12, 255))
    top, bottom, bw = ENG_BAR
    for label, x in ENG_BARS:
        d.rectangle([x - bw // 2 - 2, top - 2, x + bw // 2 + 2, bottom + 2], outline=(200, 205, 210, 255), width=2)
        # Green band, amber, red line: where a reading belongs.
        span = bottom - top
        if label in ("NR", "NP"):
            d.rectangle([x + bw // 2 + 4, top + span * 0.06, x + bw // 2 + 7, top + span * 0.16], fill=(60, 230, 90, 255))
            d.line([x + bw // 2 + 3, top + span * 0.03, x + bw // 2 + 9, top + span * 0.03], fill=(240, 50, 40, 255), width=2)
        elif label in ("TQ", "TOT"):
            d.rectangle([x + bw // 2 + 4, top + span * 0.25, x + bw // 2 + 7, bottom], fill=(60, 230, 90, 255))
            d.rectangle([x + bw // 2 + 4, top + span * 0.1, x + bw // 2 + 7, top + span * 0.25], fill=(255, 190, 60, 255))
            d.line([x + bw // 2 + 3, top + span * 0.08, x + bw // 2 + 9, top + span * 0.08], fill=(240, 50, 40, 255), width=2)
        else:
            d.rectangle([x + bw // 2 + 4, bottom - span * 0.15, x + bw // 2 + 7, bottom], fill=(255, 190, 60, 255))
        text(d, (x, bottom + 8), label, 13, CYAN, "ma")
    text(d, (6, 4), "ENG", 11, (130, 140, 150, 255))
    img.save(OUT / "ah6_eng.png")


def nav():
    w = h = 256
    img = screen(w, h)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], fill=(6, 10, 14, 255))
    # A faint grid and terrain shading, range rings round the aircraft (lower middle, heading up).
    import random
    rnd = random.Random(11)
    terrain = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    td = ImageDraw.Draw(terrain)
    for _ in range(26):
        x, y, r = rnd.randint(0, w), rnd.randint(0, h), rnd.randint(14, 46)
        td.ellipse([x - r, y - r * 0.7, x + r, y + r * 0.7], fill=(40, 70, 44, 90))
    terrain = terrain.filter(ImageFilter.GaussianBlur(9))
    img.alpha_composite(terrain)
    d = ImageDraw.Draw(img)
    for g in range(0, w, 32):
        d.line([g, 0, g, h], fill=(40, 60, 70, 120))
        d.line([0, g, w, g], fill=(40, 60, 70, 120))
    cx, cy = 128, 150
    for r in (40, 80):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(80, 160, 190, 150), width=1)
    text(d, (cx + 42, cy - 8), "1", 10, (80, 160, 190, 255))
    text(d, (cx + 82, cy - 8), "2", 10, (80, 160, 190, 255))
    # The heading box at the top (the layer writes the heading), and page labels.
    d.rectangle([104, 4, 152, 26], fill=(0, 0, 0, 255), outline=WHITE, width=2)
    text(d, (6, 6), "NAV", 12, CYAN)
    text(d, (250, 6), "MAP", 12, (130, 140, 150, 255), "ra")
    text(d, (6, 236), "GS", 12, CYAN)
    text(d, (250, 236), "RNG 2", 12, (130, 140, 150, 255), "ra")
    img.save(OUT / "ah6_nav.png")


ARMT = {"gun": (16, 120, 104, 156), "rkt": (152, 120, 240, 156)}  # boxes on the 256 x 210 page, the layer's digits inside


def armt():
    w, h = 256, 210
    img = screen(w, h)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], fill=(6, 8, 12, 255))
    text(d, (6, 4), "ARMT", 12, CYAN)
    # The Little Bird from above: the egg-shaped cabin, the tail boom, the skids and the outrigger pods.
    g = (150, 220, 170, 255)
    cx = 128
    d.ellipse([cx - 22, 22, cx + 22, 80], outline=g, width=2)
    d.line([cx, 80, cx, 112], fill=g, width=3)
    d.line([cx - 10, 112, cx + 10, 112], fill=g, width=2)
    for side in (-1, 1):
        d.line([cx + side * 26, 30, cx + side * 26, 78], fill=g, width=2)
        d.line([cx + side * 22, 52, cx + side * 62, 52], fill=g, width=2)
        d.rounded_rectangle([cx + side * 62 - 8, 36, cx + side * 62 + 8, 70], radius=6, outline=g, width=2)
    text(d, (cx - 62, 74), "M134", 10, (130, 200, 150, 255), "ma")
    text(d, (cx + 62, 74), "LAU-61", 10, (130, 200, 150, 255), "ma")
    for name, (x0, y0, x1, y1) in ARMT.items():
        d.rectangle([x0, y0, x1, y1], outline=(130, 140, 150, 255), width=2)
        text(d, ((x0 + x1) / 2, y0 - 16), "GUN" if name == "gun" else "RKT", 12, CYAN, "ma")
    text(d, (6, 186), "CM", 12, CYAN)
    text(d, (250, 186), "MSTR ARM", 11, (130, 140, 150, 255), "ra")
    img.save(OUT / "ah6_armt.png")


def at(cx, cy, r, f, sweep=270.0):
    """A reading f (0..1) on a dial sweeping {sweep} degrees centred on the top: -135..+135 for 270."""
    a = math.radians(-sweep / 2 + sweep * f)
    return cx + r * math.sin(a), cy - r * math.cos(a)


def around(cx, cy, r, f):
    """A reading f (0..1) on a full-turn dial, 0 at the top, clockwise."""
    a = 2 * math.pi * f
    return cx + r * math.sin(a), cy - r * math.cos(a)


def asi():
    s = 128
    img = screen(s, s)
    d = ImageDraw.Draw(img)
    c = s / 2
    d.ellipse([1, 1, s - 2, s - 2], fill=(10, 12, 14, 255))
    # Arcs: white (slow), green (normal), red line at 152 knots (the Little Bird's never-exceed).
    for k in range(60):
        f = k / 59
        knots = f * 160
        colour = (60, 220, 90, 255) if 20 <= knots <= 130 else None
        if colour:
            d.line([*at(c, c, 56, f), *at(c, c, 60, f)], fill=colour, width=3)
    d.line([*at(c, c, 50, 152 / 160), *at(c, c, 61, 152 / 160)], fill=(240, 50, 40, 255), width=3)
    for knots in range(0, 161, 10):
        f = knots / 160
        major = knots % 20 == 0
        d.line([*at(c, c, 47 if major else 52, f), *at(c, c, 56, f)], fill=WHITE, width=2 if major else 1)
        if major and knots % 40 == 0:
            text(d, at(c, c, 36, f), str(knots // 10 if knots else 0), 13, WHITE, "mm")
    text(d, (c, c + 47), "KNOTS", 9, (190, 192, 190, 255), "mm")
    text(d, (c, c - 20), "x10", 8, (150, 152, 150, 255), "mm")
    img.save(OUT / "ah6_asi.png")


def alt():
    s = 128
    img = screen(s, s)
    d = ImageDraw.Draw(img)
    c = s / 2
    d.ellipse([1, 1, s - 2, s - 2], fill=(10, 12, 14, 255))
    for k in range(50):
        f = k / 50
        major = k % 5 == 0
        d.line([*around(c, c, 49 if major else 54, f), *around(c, c, 59, f)], fill=WHITE, width=2 if major else 1)
    for n in range(10):
        text(d, around(c, c, 38, n / 10), str(n), 14, WHITE, "mm")
    text(d, (c, c + 20), "ALT", 9, (190, 192, 190, 255), "mm")
    text(d, (c, c - 20), "100 FT", 8, (150, 152, 150, 255), "mm")
    img.save(OUT / "ah6_alt.png")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    centre_panel()
    side_panel()
    console_panel()
    pfd()
    eng()
    nav()
    armt()
    asi()
    alt()
    print("ok")


if __name__ == "__main__":
    main()
