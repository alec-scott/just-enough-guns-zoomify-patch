"""Textures for the CH-47F's cockpit (Ch47CockpitLayer; the user, 2026-11-21: "a detailed textured console"): the
CAAS glass cockpit's instrument panel with five displays under its glare shield, the centre console with the two
control display units, the radios and the flight control and cargo hook panels, and the overhead console's switch panels.

python -I design/chinook/make_cockpit.py

Writes to assets/jeg/textures/entity/vehicle/cockpit/. The faces' sizes are generate.py's COCKPIT boxes (model pixels);
every instrument's place on them is in LAYOUT (u across from the face's centre, the pilot's right positive; v down the
panel, or aft along the console, from its top or front edge); keep them in step with Ch47CockpitLayer.

- ch47_panel.png: the panel's face, matte grey-black: five display bezels with their soft keys, master caution and
  warning lamps at each end, a row of caution lamps, knobs and placards. Lit by the world; the displays are drawn over it.
- ch47_console.png: the console's top: two CDUs (their screens drawn live), the AFCS panel, two radio heads (live
  frequency windows), the cargo hook panel and the lights panel.
- ch47_overhead.png: the overhead console's underside: engine start and condition levers, fuel, electrical, APU and
  anti-ice panels of toggles and circuit breakers.
- ch47_pfd.png: the flight displays' overlay (both pilots'), over the live sky, ground and ladder.
- ch47_nav.png: the map page's ground, grid and range rings; the layer turns the compass rose.
- ch47_eng.png: the engine page: torque, gas producer and turbine temperature bars for both T55s, rotor speed, fuel
  in the two tanks and the transmission's oil; the layer fills them and writes the figures.
- ch47_cargo.png: the cargo page: the Chinook from above with its three hooks, the ramp and crew door, labels; the layer
  writes the hook's state, the rope, the load and the ramp and door.
"""
import importlib.util
import math
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
spec = importlib.util.spec_from_file_location("ah6_cockpit", ROOT / "design/ah6/make_cockpit.py")
A = importlib.util.module_from_spec(spec)
spec.loader.exec_module(A)        # the AH-6's drawing helpers (fonts, keys, bezels, grain) and palette
OUT = A.OUT

FACES = {"panel": (41.2, 9.0), "console": (9.2, 18.0), "overhead": (12.0, 15.0)}
MFD_U = [-16.4, -8.2, 0.0, 8.2, 16.4]           # display centres, left to right as seen from the seats
MFD_SIZE = (6.0, 5.0)
LAYOUT = {
    "panel.mfd": [(u - MFD_SIZE[0] / 2, 1.05, MFD_SIZE[0], MFD_SIZE[1]) for u in MFD_U],
    "panel.master": [(-20.1, 7.05, 1.3, 0.75), (-20.1, 7.95, 1.3, 0.75), (18.8, 7.05, 1.3, 0.75), (18.8, 7.95, 1.3, 0.75)],
    "panel.lamps": [(-6.3 + i * 1.6, 7.25, 1.3, 0.55) for i in range(8)],
    "console.cdu": [(-4.35, 0.7, 4.1, 2.2), (0.25, 0.7, 4.1, 2.2)],       # the two CDUs' screens
    "console.radio": [(-4.2, 10.45, 3.2, 0.7), (0.6, 10.45, 3.2, 0.7)],   # the radios' frequency windows
    "console.hook": (-3.9, 14.0, 2.4, 0.6),                               # the cargo hook panel's status window
}
TEXEL = 24
TAN = (176, 170, 150, 255)
RED_GUARD = (150, 30, 26, 255)


def face_image(name):
    w, h = FACES[name]
    return Image.new("RGBA", (round(w * TEXEL), round(h * TEXEL)), A.PANEL)


def to_tex(face, u, v):
    w, _ = FACES[face]
    return (u + w / 2) * TEXEL, v * TEXEL


def rect(face, box):
    u, v, w, h = box
    x0, y0 = to_tex(face, u, v)
    return [x0, y0, x0 + w * TEXEL, y0 + h * TEXEL]


def toggle(d, cx, cy, label=None, guard=False, size=8, up=True):
    if guard:
        d.rectangle([cx - 9, cy - 11, cx + 9, cy + 11], fill=RED_GUARD, outline=(90, 16, 14, 255))
    d.rectangle([cx - 6, cy - 7, cx + 6, cy + 7], fill=A.BEZEL)
    d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=(160, 162, 160, 255))
    d.line([cx, cy, cx, cy - 9 if up else cy + 9], fill=(205, 207, 205, 255), width=3)
    if label:
        f = A.font(size)
        tw = d.textlength(label, font=f)
        d.text((cx - tw / 2, cy + 10), label, font=f, fill=A.LABEL)


def knob(d, cx, cy, r=9, label=None):
    d.ellipse([cx - r - 2, cy - r - 2, cx + r + 2, cy + r + 2], fill=(18, 19, 20, 255))
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(62, 64, 66, 255))
    d.line([cx, cy, cx + r * 0.7, cy - r * 0.7], fill=A.WHITE, width=2)
    if label:
        f = A.font(8)
        tw = d.textlength(label, font=f)
        d.text((cx - tw / 2, cy + r + 3), label, font=f, fill=A.LABEL)


def breakers(d, x0, y0, cols, rows, pitch=14):
    for i in range(cols):
        for j in range(rows):
            cx, cy = x0 + i * pitch, y0 + j * pitch
            d.ellipse([cx - 5, cy - 5, cx + 5, cy + 5], fill=(14, 15, 16, 255))
            d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=(40, 42, 44, 255))


def placard(d, box, title, size=9):
    x0, y0, x1, y1 = box
    d.rounded_rectangle(box, radius=4, outline=(60, 63, 66, 255), width=2)
    f = A.font(size)
    tw = d.textlength(title, font=f)
    d.rectangle([(x0 + x1 - tw) / 2 - 4, y0 - 6, (x0 + x1 + tw) / 2 + 4, y0 + 6], fill=A.PANEL)
    d.text(((x0 + x1 - tw) / 2, y0 - 6), title, font=f, fill=A.LABEL)


def screws(d, img, inset=6):
    for x, y in ((inset, inset), (img.width - inset, inset), (inset, img.height - inset), (img.width - inset, img.height - inset)):
        d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(84, 86, 88, 255))
        d.line([x - 2, y, x + 2, y], fill=(40, 42, 44, 255))


# ------------------------------------------------------------------ the faces

def panel():
    img = face_image("panel")
    A.grain(img, seed=21)
    d = ImageDraw.Draw(img)
    names = ("PFD", "MAP", "EICAS", "CARGO", "PFD")
    for i, box in enumerate(LAYOUT["panel.mfd"]):
        x0, y0, x1, y1 = rect("panel", box)
        d.rounded_rectangle([x0 - 16, y0 - 16, x1 + 16, y1 + 16], radius=8, fill=A.BEZEL)
        d.rectangle([x0, y0, x1, y1], fill=A.GLASS)
        # Six soft keys along the top and bottom, five down each side, a brightness rocker.
        for row_y in (y0 - 12, y1 + 4):
            span = (x1 - x0) / 6
            for k in range(6):
                A.key(d, [x0 + k * span + 4, row_y, x0 + (k + 1) * span - 4, row_y + 8])
        for col_x in (x0 - 13, x1 + 4):
            span = (y1 - y0) / 5
            for k in range(5):
                A.key(d, [col_x, y0 + k * span + 5, col_x + 9, y0 + (k + 1) * span - 5])
        f = A.font(10)
        tw = d.textlength(f"MFD {i + 1}", font=f)
        d.text(((x0 + x1 - tw) / 2, y1 + 15), f"MFD {i + 1}", font=f, fill=(120, 124, 128, 255))
    # Master caution (amber) and warning (red) at each end, lit by the layer; the caution lamps under the centre.
    for box in LAYOUT["panel.master"] + LAYOUT["panel.lamps"]:
        x0, y0, x1, y1 = rect("panel", box)
        d.rounded_rectangle([x0 - 3, y0 - 3, x1 + 3, y1 + 3], radius=3, fill=A.BEZEL)
        d.rectangle([x0, y0, x1, y1], fill=(26, 27, 28, 255))
    f = A.font(8)
    for i, label in enumerate(("ENG 1", "ENG 2", "XMSN", "HYD", "FUEL", "HOOK", "RAMP", "DOOR")):
        x0, y0, x1, y1 = rect("panel", LAYOUT["panel.lamps"][i])
        tw = d.textlength(label, font=f)
        d.text(((x0 + x1 - tw) / 2, y1 + 4), label, font=f, fill=A.LABEL)
    for u in (-12.3, 12.3):
        for k, label in enumerate(("BRT", "HSI", "BARO")):
            cx, cy = to_tex("panel", u - 1.4 + k * 1.4, 7.6)
            knob(d, cx, cy, r=8, label=label)
    for u, label in ((-18.4, "CAUTION"), (19.5, "CAUTION")):
        cx, cy = to_tex("panel", u, 8.85)
        tw = d.textlength(label, font=f)
        d.text((cx - tw / 2, cy - 4), label, font=f, fill=A.LABEL)
    screws(d, img)
    img.save(OUT / "ch47_panel.png")


def console():
    img = face_image("console")
    A.grain(img, seed=23)
    d = ImageDraw.Draw(img)
    # Two CDUs: their screens (drawn live), line select keys beside them, and keypads below.
    for box in LAYOUT["console.cdu"]:
        x0, y0, x1, y1 = rect("console", box)
        d.rounded_rectangle([x0 - 12, y0 - 6, x1 + 12, y0 + 5.7 * TEXEL], radius=6, fill=A.BEZEL)
        d.rectangle([x0, y0, x1, y1], fill=A.GLASS)
        for k in range(4):
            ky = y0 + 6 + k * (y1 - y0 - 12) / 4
            A.key(d, [x0 - 10, ky, x0 - 3, ky + 8])
            A.key(d, [x1 + 3, ky, x1 + 10, ky + 8])
        keys = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        kx0, ky0 = x0 + 2, y1 + 8
        pitch_x, pitch_y = (x1 - x0 - 4) / 6, 14
        for n, ch in enumerate(keys):
            i, j = n % 6, n // 6
            A.key(d, [kx0 + i * pitch_x + 1, ky0 + j * pitch_y, kx0 + (i + 1) * pitch_x - 1, ky0 + j * pitch_y + 11], ch, size=8)
    # The AFCS panel.
    x0, y0, x1, y1 = rect("console", (-4.3, 7.0, 8.6, 2.6))
    placard(d, [x0, y0, x1, y1], "AFCS")
    for i, label in enumerate(("SYS 1", "SYS 2", "BARO", "RAD", "HDG", "HVR")):
        cx, cy = x0 + 20 + i * (x1 - x0 - 40) / 5, y0 + 22
        A.key(d, [cx - 13, cy - 8, cx + 13, cy + 8], label, size=7)
    for i, label in enumerate(("CPLR", "TRIM", "HOLD")):
        toggle(d, x0 + 34 + i * 60, y0 + 44, label)
    # Two radio heads with their frequency windows (drawn live) and knobs.
    for box, title in zip(LAYOUT["console.radio"], ("VHF/UHF 1", "VHF/UHF 2")):
        x0, y0, x1, y1 = rect("console", box)
        placard(d, [x0 - 6, y0 - 10, x1 + 6, y1 + 48], title, size=8)
        d.rectangle([x0, y0, x1, y1], fill=A.GLASS)
        for k in range(2):
            knob(d, x0 + 18 + k * 40, y1 + 24, r=10)
    # The cargo hook panel: master, arm, the release (guarded) and its status window (drawn live).
    x0, y0, x1, y1 = rect("console", (-4.3, 13.4, 8.6, 2.2))
    placard(d, [x0, y0, x1, y1], "CARGO HOOK")
    hx0, hy0, hx1, hy1 = rect("console", LAYOUT["console.hook"])
    d.rectangle([hx0, hy0, hx1, hy1], fill=A.GLASS)
    toggle(d, x0 + 130, y0 + 18, "MSTR")
    toggle(d, x0 + 160, y0 + 18, "ARM")
    toggle(d, x0 + 190, y0 + 18, "REL", guard=True)
    # The lights panel.
    x0, y0, x1, y1 = rect("console", (-4.3, 16.1, 8.6, 1.6))
    placard(d, [x0, y0, x1, y1], "LIGHTS")
    for i, label in enumerate(("POS", "ANTI", "FORM", "SRCH", "CABIN")):
        knob(d, x0 + 22 + i * 40, y0 + 16, r=8, label=label)
    screws(d, img)
    img.save(OUT / "ch47_console.png")


def overhead():
    img = face_image("overhead")
    A.grain(img, seed=25)
    d = ImageDraw.Draw(img)
    W, H = img.size
    panels = [((8, 10, W - 8, 74), "ENGINE START"), ((8, 92, W // 2 - 4, 196), "FUEL"), ((W // 2 + 4, 92, W - 8, 196), "ELECTRICAL"),
              ((8, 214, W // 2 - 4, 300), "APU"), ((W // 2 + 4, 214, W - 8, 300), "ANTI-ICE"), ((8, 318, W - 8, H - 10), "CIRCUIT BREAKERS")]
    for box, title in panels:
        placard(d, list(box), title)
    # Engine start: the two condition levers' detents and the start switches.
    for i, label in enumerate(("ENG 1", "ENG 2")):
        cx = 60 + i * 170
        d.rounded_rectangle([cx - 24, 26, cx + 24, 64], radius=4, fill=A.BEZEL)
        d.rectangle([cx - 4, 30, cx + 4, 60], fill=(10, 10, 10, 255))
        d.rounded_rectangle([cx - 12, 36, cx + 12, 48], radius=3, fill=(210, 40, 30, 255))
        toggle(d, cx + 46, 42, "START", guard=True)
        f = A.font(9)
        d.text((cx - 18, 16), label, font=f, fill=A.LABEL)
    # Fuel: boost pumps and crossfeed; electrical: generators, batteries, buses.
    for i, label in enumerate(("L FWD", "L AFT", "R FWD", "R AFT")):
        toggle(d, 30 + (i % 2) * 64, 120 + (i // 2) * 40, label, size=7)
    toggle(d, 30 + 128 - 20, 140, "XFEED", guard=True, size=7)
    for i, label in enumerate(("GEN 1", "GEN 2", "APU GEN", "BATT", "BUS TIE", "EXT")):
        toggle(d, W // 2 + 26 + (i % 3) * 44, 120 + (i // 3) * 40, label, size=7)
    for i, label in enumerate(("MASTER", "START", "BLEED")):
        toggle(d, 30 + i * 44, 248, label, size=7)
    for i, label in enumerate(("ENG 1", "ENG 2", "PITOT", "WSHLD")):
        toggle(d, W // 2 + 22 + i * 34, 248, label, size=6)
    breakers(d, 22, 336, (W - 44) // 14 + 1, 2)
    screws(d, img)
    img.save(OUT / "ch47_overhead.png")


# ------------------------------------------------------------------ the displays (256 x 213: the 6 x 5 px screens)

PAGE = (256, 213)


def pfd():
    w, h = PAGE
    img = A.screen(w, h)
    d = ImageDraw.Draw(img)
    tape = (10, 12, 16, 200)
    d.rectangle([4, 34, 46, 176], fill=tape, outline=(200, 205, 210, 140))
    d.rectangle([210, 34, 252, 176], fill=tape, outline=(200, 205, 210, 140))
    for y in range(42, 176, 12):
        d.line([38, y, 46, y], fill=(220, 224, 226, 200), width=2)
        d.line([210, y, 218, y], fill=(220, 224, 226, 200), width=2)
    for x0, x1, tip in ((2, 48, 54), (208, 254, 202)):
        d.polygon([(x0, 95), (x1, 95), (tip, 105), (x1, 115), (x0, 115)] if tip > x1 else
                  [(x1, 95), (x0, 95), (tip, 105), (x0, 115), (x1, 115)], fill=(0, 0, 0, 235), outline=A.WHITE)
    A.text(d, (25, 10), "KTS", 13, A.CYAN, "ma")
    A.text(d, (231, 10), "FT", 13, A.CYAN, "ma")
    cx, cy, r = 128, 120, 74
    for a in (-60, -45, -30, -20, -10, 10, 20, 30, 45, 60):
        long = abs(a) in (30, 60)
        d.line([cx + r * math.sin(math.radians(a)), cy - r * math.cos(math.radians(a)),
                cx + (r + (10 if long else 6)) * math.sin(math.radians(a)), cy - (r + (10 if long else 6)) * math.cos(math.radians(a))],
               fill=A.WHITE, width=2)
    d.polygon([(cx, cy - r), (cx - 5, cy - r - 9), (cx + 5, cy - r - 9)], fill=A.WHITE)
    yellow = (255, 220, 40, 255)
    for side in (-1, 1):
        d.polygon([(cx + side * 22, 102), (cx + side * 52, 102), (cx + side * 52, 108), (cx + side * 28, 108),
                   (cx + side * 28, 116), (cx + side * 22, 116)], fill=yellow, outline=(0, 0, 0, 255))
    d.rectangle([cx - 4, 102, cx + 4, 108], fill=yellow, outline=(0, 0, 0, 255))
    d.rectangle([104, 186, 152, 208], fill=(0, 0, 0, 235), outline=A.WHITE)
    A.text(d, (100, 197), "HDG", 10, A.CYAN, "rm")
    A.text(d, (156, 197), "RALT", 10, A.CYAN, "lm")
    img.save(OUT / "ch47_pfd.png")


def nav():
    w, h = PAGE
    img = A.screen(w, h)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], fill=(6, 10, 14, 255))
    import random
    from PIL import ImageFilter
    rnd = random.Random(13)
    terrain = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    td = ImageDraw.Draw(terrain)
    for _ in range(30):
        x, y, r = rnd.randint(0, w), rnd.randint(0, h), rnd.randint(14, 46)
        td.ellipse([x - r, y - r * 0.7, x + r, y + r * 0.7], fill=(40, 70, 44, 90))
    img.alpha_composite(terrain.filter(ImageFilter.GaussianBlur(9)))
    d = ImageDraw.Draw(img)
    for g in range(0, w, 32):
        d.line([g, 0, g, h], fill=(40, 60, 70, 110))
        d.line([0, g, w, g], fill=(40, 60, 70, 110))
    cx, cy = 128, 140
    for r in (36, 72):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(80, 160, 190, 150), width=1)
    A.text(d, (cx + 38, cy - 8), "2", 10, (80, 160, 190, 255))
    A.text(d, (cx + 74, cy - 8), "4", 10, (80, 160, 190, 255))
    d.rectangle([104, 4, 152, 26], fill=(0, 0, 0, 255), outline=A.WHITE, width=2)
    A.text(d, (6, 6), "MAP", 12, A.CYAN)
    A.text(d, (250, 6), "TRK UP", 11, (130, 140, 150, 255), "ra")
    A.text(d, (6, 194), "GS", 12, A.CYAN)
    A.text(d, (250, 194), "RNG 4", 12, (130, 140, 150, 255), "ra")
    img.save(OUT / "ch47_nav.png")


# The engine page's bars: (label, x, group) on the 256 x 213 page; tops and bottoms in ENG_BAR.
ENG_BARS = [("TQ", 22), ("TQ", 46), ("NG", 82), ("NG", 106), ("TGT", 142), ("TGT", 166), ("NR", 202), ("FUEL", 230)]
ENG_BAR = (44, 160, 16)


def eng():
    w, h = PAGE
    img = A.screen(w, h)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], fill=(6, 8, 12, 255))
    top, bottom, bw = ENG_BAR
    for k, (label, x) in enumerate(ENG_BARS):
        d.rectangle([x - bw // 2 - 2, top - 2, x + bw // 2 + 2, bottom + 2], outline=(200, 205, 210, 255), width=2)
        span = bottom - top
        if label in ("TQ", "TGT"):
            d.line([x - bw // 2 - 4, top + span * 0.1, x + bw // 2 + 4, top + span * 0.1], fill=(240, 50, 40, 255), width=2)
            d.line([x - bw // 2 - 4, top + span * 0.22, x + bw // 2 + 4, top + span * 0.22], fill=(255, 190, 60, 255), width=1)
        elif label == "NR":
            d.rectangle([x + bw // 2 + 3, top + span * 0.06, x + bw // 2 + 6, top + span * 0.14], fill=(60, 230, 90, 255))
        elif label == "FUEL":
            d.rectangle([x + bw // 2 + 3, bottom - span * 0.15, x + bw // 2 + 6, bottom], fill=(255, 190, 60, 255))
        if k % 2 == 0 and label in ("TQ", "NG", "TGT"):
            A.text(d, (x + 12, bottom + 8), label, 12, A.CYAN, "ma")
            A.text(d, (x, bottom + 26), "1", 10, (150, 160, 170, 255), "ma")
            A.text(d, (x + 24, bottom + 26), "2", 10, (150, 160, 170, 255), "ma")
        elif label in ("NR", "FUEL"):
            A.text(d, (x, bottom + 8), label, 12, A.CYAN, "ma")
    A.text(d, (6, 4), "EICAS", 11, (130, 140, 150, 255))
    A.text(d, (250, 4), "XMSN OIL", 10, (130, 140, 150, 255), "ra")
    img.save(OUT / "ch47_eng.png")


def cargo():
    w, h = PAGE
    img = A.screen(w, h)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], fill=(6, 8, 12, 255))
    A.text(d, (6, 4), "CARGO", 12, A.CYAN)
    g = (150, 220, 170, 255)
    # The Chinook from above, nose up: the fuselage, both rotor discs, the sponsons, the ramp and crew door.
    cx, top, bottom = 70, 26, 196
    d.rounded_rectangle([cx - 16, top + 6, cx + 16, bottom - 22], radius=12, outline=g, width=2)
    d.ellipse([cx - 44, top - 6, cx + 44, top + 82], outline=(70, 110, 90, 255), width=1)
    d.ellipse([cx - 44, bottom - 110, cx + 44, bottom - 22], outline=(70, 110, 90, 255), width=1)
    for side in (-1, 1):
        d.rectangle([cx + side * 16, top + 60, cx + side * 24, bottom - 50] if side > 0 else
                    [cx - 24, top + 60, cx - 16, bottom - 50], outline=g, width=1)
    d.rectangle([cx - 12, bottom - 22, cx + 12, bottom - 6], outline=g, width=2)       # the ramp
    A.text(d, (cx, bottom + 2), "RAMP", 9, (130, 200, 150, 255), "ma")
    d.rectangle([cx + 16, top + 30, cx + 20, top + 44], fill=g)                       # the crew door (right)
    for hy in (top + 76, top + 96, top + 116):                                          # the three hooks
        d.ellipse([cx - 4, hy - 4, cx + 4, hy + 4], outline=g, width=2)
    # The readings' places: the layer writes them.
    for y, label in ((30, "HOOK"), (66, "ROPE"), (102, "LOAD"), (138, "RAMP"), (174, "DOOR")):
        A.text(d, (132, y), label, 12, A.CYAN)
        d.line([132, y + 30, 250, y + 30], fill=(40, 48, 56, 255), width=1)
    img.save(OUT / "ch47_cargo.png")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    panel()
    console()
    overhead()
    pfd()
    nav()
    eng()
    cargo()
    print("ok")


if __name__ == "__main__":
    main()
