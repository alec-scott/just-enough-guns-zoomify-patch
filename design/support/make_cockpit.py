"""Textures for the FSV-6's cab instruments (SupportCockpitLayer).

python design/support/make_cockpit.py

Writes to assets/jeg/textures/entity/vehicle/cockpit/:
- fsv6_tacho.png, fsv6_speedo.png: backlit dials. A reading f (0..1) sits at -135 + 270 f degrees clockwise from
  straight up, as CockpitPanel.needle draws the needle.
- fsv6_lamps.png: the eight warning-lamp symbols in a row, white on clear (tinted by the lamp's colour when lit).
- fsv6_status.png: the co-driver's green status screen with its labels and empty bar frames; the layer fills the bars.
- fsv6_binnacle.png: the binnacle's face (dark, with the dial bezels), lit by the world.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/jeg/textures/entity/vehicle/cockpit"
WHITE = (240, 236, 220, 255)
DIM = (150, 146, 132, 255)
RED = (230, 60, 40, 255)
PHOSPHOR = (124, 255, 154, 255)
PHOSPHOR_DIM = (62, 140, 85, 255)


INTER = ROOT / "design/hud/Inter-SemiBold.ttf"


def font(size):
    """The HUD's face (Inter), as the vehicle HUD uses: smooth, not a pixel font (the user's rule, 2026-11-17)."""
    try:
        return ImageFont.truetype(str(INTER), size)
    except OSError:
        return ImageFont.load_default(size=size)


def angle(f):
    return math.radians(-135 + 270 * f)


def at(cx, cy, r, f):
    a = angle(f)
    return cx + r * math.sin(a), cy - r * math.cos(a)


def dial(name, maximum, major, minor, labels, unit, red_from=None):
    """A dial of `maximum` units: major ticks every `major` with labels / `labels`, minor ticks every `minor`."""
    s = 256
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = s / 2
    d.ellipse([2, 2, s - 3, s - 3], fill=(14, 16, 16, 255), outline=(70, 74, 70, 255), width=4)
    if red_from is not None:
        f0 = red_from / maximum
        for k in range(40):
            f = f0 + (1 - f0) * k / 39
            x0, y0 = at(c, c, 104, f)
            x1, y1 = at(c, c, 116, f)
            d.line([x0, y0, x1, y1], fill=RED, width=5)
    v = 0
    while v <= maximum + 1e-6:
        f = v / maximum
        is_major = abs(v / major - round(v / major)) < 1e-6
        x0, y0 = at(c, c, 98 if is_major else 106, f)
        x1, y1 = at(c, c, 118, f)
        d.line([x0, y0, x1, y1], fill=WHITE, width=5 if is_major else 2)
        if is_major:
            text = str(int(round(v / labels)))
            tx, ty = at(c, c, 76, f)
            fnt = font(30)
            w = d.textlength(text, font=fnt)
            d.text((tx - w / 2, ty - 17), text, font=fnt, fill=WHITE)
        v += minor
    fnt = font(16)
    w = d.textlength(unit, font=fnt)
    d.text((c - w / 2, c + 20), unit, font=fnt, fill=DIM)
    d.ellipse([c - 12, c - 12, c + 12, c + 12], fill=(60, 62, 60, 255))
    img.save(OUT / f"{name}.png")


def lamps():
    """Eight symbols, 32 px each: brake, reverse, battery, glow plug, beacons, tow, jacks, engine."""
    img = Image.new("RGBA", (256, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    fnt = font(18)
    for i in range(8):
        x = i * 32
        d.rounded_rectangle([x + 1, 1, x + 30, 30], radius=5, fill=(255, 255, 255, 70))
        cx, cy = x + 16, 16
        if i == 0:      # brake: (!) in a circle with side arcs
            d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], outline=WHITE, width=3)
            d.arc([cx - 14, cy - 12, cx - 4, cy + 12], 110, 250, fill=WHITE, width=2)
            d.arc([cx + 4, cy - 12, cx + 14, cy + 12], -70, 70, fill=WHITE, width=2)
            d.text((cx - 3, cy - 10), "!", font=fnt, fill=WHITE)
        elif i == 1:    # reverse: R
            d.text((cx - 6, cy - 11), "R", font=font(20), fill=WHITE)
        elif i == 2:    # battery
            d.rectangle([cx - 11, cy - 6, cx + 11, cy + 8], outline=WHITE, width=3)
            d.rectangle([cx - 8, cy - 9, cx - 4, cy - 6], fill=WHITE)
            d.rectangle([cx + 4, cy - 9, cx + 8, cy - 6], fill=WHITE)
            d.text((cx - 9, cy - 7), "- +", font=font(12), fill=WHITE)
        elif i == 3:    # glow plug: coil
            for k in range(4):
                d.arc([cx - 11 + k * 5, cy - 6, cx - 3 + k * 5, cy + 6], 180, 360, fill=WHITE, width=2)
            d.line([cx - 11, cy + 6, cx + 11, cy + 6], fill=WHITE, width=2)
        elif i == 4:    # beacon: dome with rays
            d.pieslice([cx - 8, cy - 4, cx + 8, cy + 12], 180, 360, fill=WHITE)
            d.rectangle([cx - 9, cy + 4, cx + 9, cy + 7], fill=WHITE)
            for a in (-60, -30, 0, 30, 60):
                r = math.radians(a)
                d.line([cx + 10 * math.sin(r), cy - 2 - 10 * math.cos(r), cx + 14 * math.sin(r), cy - 2 - 14 * math.cos(r)], fill=WHITE, width=2)
        elif i == 5:    # tow: hook
            d.arc([cx - 8, cy - 2, cx + 6, cy + 12], 0, 200, fill=WHITE, width=3)
            d.line([cx + 6, cy + 5, cx + 6, cy - 11], fill=WHITE, width=3)
            d.ellipse([cx + 2, cy - 14, cx + 10, cy - 8], outline=WHITE, width=2)
        elif i == 6:    # jacks: arrow down to a bar
            d.line([cx, cy - 12, cx, cy + 6], fill=WHITE, width=3)
            d.polygon([(cx - 6, cy + 1), (cx + 6, cy + 1), (cx, cy + 8)], fill=WHITE)
            d.line([cx - 11, cy + 11, cx + 11, cy + 11], fill=WHITE, width=3)
        elif i == 7:    # engine: block outline
            d.polygon([(cx - 11, cy - 3), (cx - 6, cy - 3), (cx - 6, cy - 7), (cx + 6, cy - 7), (cx + 6, cy - 3),
                       (cx + 11, cy - 3), (cx + 11, cy + 9), (cx - 11, cy + 9)], outline=WHITE, width=2)
    img.save(OUT / "fsv6_lamps.png")


# Status screen layout (pixels of a 240x100 image); SupportCockpitLayer fills the bars at the same places.
STATUS_W, STATUS_H = 240, 100
STATUS_BARS = {"BATT": 30, "HOLD": 48, "BODY": 66}
STATUS_BAR_X = (62, 196)
STATUS_BEACON_Y = 84


# The screens are drawn at S texels a layout unit; the layer's live fills use the layout's units.
S = 5
TITLE = (205, 255, 222, 255)
LABEL = (128, 198, 158, 255)
TRACK = (9, 27, 20, 255)
EDGE = (60, 124, 90, 255)
FAINT = (40, 84, 62, 255)


class Screen:
    """A screen in layout units (w x h), drawn at S texels a unit: a dark glass ground with a faint grid, a rounded rim,
    a title bar with an icon and an ONLINE lamp."""

    def __init__(self, w, h, title, icon):
        self.w, self.h = w, h
        self.img = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 255))
        self.d = ImageDraw.Draw(self.img)
        for y in range(h * S):
            k = y / (h * S)
            self.d.line([0, y, w * S, y], fill=(int(9 - 4 * k), int(24 - 10 * k), int(18 - 7 * k), 255))
        for g in range(8, w, 8):
            self.d.line([g * S, 26 * S, g * S, h * S], fill=(12, 31, 23, 255), width=1)
        for g in range(32, h, 8):
            self.d.line([0, g * S, w * S, g * S], fill=(12, 31, 23, 255), width=1)
        self.d.rounded_rectangle([S, S, w * S - S - 1, h * S - S - 1], radius=4 * S, outline=(34, 74, 54, 255), width=2)
        icon(self.d, 15 * S, 12 * S, S)
        self.text(24, 12, title, 12.5, TITLE, "lm")
        self.d.ellipse([(w - 47) * S, 10 * S, (w - 43) * S, 14 * S], fill=(80, 230, 130, 255))
        self.text(w - 10, 12, "ONLINE", 7.5, LABEL, "rm")
        self.d.line([8 * S, 24 * S, (w - 8) * S, 24 * S], fill=EDGE, width=2)

    def text(self, x, y, s, size, fill, anchor="la"):
        self.d.text((x * S, y * S), s, font=font(int(size * S)), fill=fill, anchor=anchor)

    def track(self, x0, y0, x1, y1):
        """A rounded track round the live fill (x0, y0)-(x1, y1), a unit of margin."""
        self.d.rounded_rectangle([(x0 - 2) * S, (y0 - 1) * S, (x1 + 2) * S, (y1 + 1) * S], radius=2 * S, fill=TRACK,
                                 outline=EDGE, width=2)

    def save(self, name):
        self.img.save(OUT / name)


def icon_bolt(d, cx, cy, s):
    d.polygon([(cx + 1 * s, cy - 6 * s), (cx - 4 * s, cy + 1 * s), (cx - 0.5 * s, cy + 1 * s), (cx - 1.5 * s, cy + 6 * s),
               (cx + 4 * s, cy - 1 * s), (cx + 0.5 * s, cy - 1 * s)], fill=TITLE)


def icon_beacon(d, cx, cy, s, colour=TITLE):
    d.pieslice([cx - 4 * s, cy - 3 * s, cx + 4 * s, cy + 5 * s], 180, 360, fill=colour)
    d.rounded_rectangle([cx - 5 * s, cy + 1 * s, cx + 5 * s, cy + 3 * s], radius=s, fill=colour)
    import math as _m
    for a in (-60, -30, 0, 30, 60):
        r = _m.radians(a)
        d.line([cx + 6 * s * _m.sin(r), cy - 6 * s * _m.cos(r), cx + 8 * s * _m.sin(r), cy - 8 * s * _m.cos(r)], fill=colour, width=s)


def icon_truck(d, cx, cy, s):
    d.rounded_rectangle([cx - 6 * s, cy - 4 * s, cx + 2 * s, cy + 3 * s], radius=s, fill=TITLE)
    d.rounded_rectangle([cx + 2.5 * s, cy - 1 * s, cx + 6 * s, cy + 3 * s], radius=s, fill=TITLE)
    for x in (-4, 3.5):
        d.ellipse([cx + (x - 1.6) * s, cy + 2.4 * s, cx + (x + 1.6) * s, cy + 5.6 * s], fill=(8, 22, 16, 255), outline=TITLE, width=s)


def status():
    sc = Screen(STATUS_W, STATUS_H, "FSV-6 STATUS", icon_truck)
    for name, y in STATUS_BARS.items():
        sc.text(10, y + 5, name, 8.5, LABEL, "lm")
        sc.track(STATUS_BAR_X[0], y, STATUS_BAR_X[1], y + 10)
    y = STATUS_BEACON_Y
    sc.text(10, y + 5, "BEACON", 8.5, LABEL, "lm")
    for k in range(4):
        x = STATUS_BAR_X[0] + k * 22
        sc.d.rounded_rectangle([x * S, y * S, (x + 16) * S, (y + 10) * S], radius=2 * S, fill=TRACK, outline=EDGE, width=2)
    sc.text(200, y + 5, "TOW", 8.5, LABEL, "rm")
    sc.d.rounded_rectangle([206 * S, y * S, 222 * S, (y + 10) * S], radius=2 * S, fill=TRACK, outline=EDGE, width=2)
    sc.save("fsv6_status.png")


def binnacle():
    img = Image.new("RGBA", (264, 88), (24, 26, 26, 255))
    d = ImageDraw.Draw(img)
    for cx in (54, 210):
        d.ellipse([cx - 44, 0, cx + 44, 88], fill=(40, 42, 40, 255))
        d.ellipse([cx - 41, 3, cx + 41, 85], fill=(12, 13, 13, 255))
    d.rounded_rectangle([102, 8, 162, 80], radius=4, fill=(16, 17, 17, 255), outline=(48, 50, 48, 255))
    img.save(OUT / "fsv6_binnacle.png")


# The room's wall panels (232 x 120 screens), filled live by SupportCockpitLayer at the same places.
WALL_W, WALL_H = 232, 120
WALL_ROWS = {"PWR": 34, "BATT": 54, "HOLD": 74, "LIGHT": 94}
WALL_BAR_X = (78, 220)


def wall_console():
    sc = Screen(WALL_W, WALL_H, "FSV-6 SUPPORT", icon_bolt)
    names = {"PWR": "POWER", "BATT": "BATTERY", "HOLD": "HOLD", "LIGHT": "LIGHT"}
    for name, y in WALL_ROWS.items():
        sc.text(10, y + 5, names[name], 8.5, LABEL, "lm")
        if name == "PWR":
            for k, label in enumerate(("GEN", "DSL", "BAT")):
                x = WALL_BAR_X[0] + k * 48
                sc.d.rounded_rectangle([x * S, y * S, (x + 12) * S, (y + 10) * S], radius=2 * S, fill=TRACK, outline=EDGE, width=2)
                sc.text(x + 16, y + 5, label, 8, LABEL, "lm")
        else:
            sc.track(WALL_BAR_X[0], y, WALL_BAR_X[1], y + 10)
    sc.save("fsv6_wall_console.png")


def wall_beacon():
    sc = Screen(WALL_W, WALL_H, "ROOF BEACON", icon_beacon)
    # The effect's tile: the chosen effect's colour fills it live; a faint beacon outline shows while none is.
    sc.d.rounded_rectangle([10 * S, 32 * S, 62 * S, 84 * S], radius=4 * S, fill=TRACK, outline=EDGE, width=2)
    icon_beacon(sc.d, 36 * S, 60 * S, int(S * 1.8), FAINT)
    sc.text(80, 37, "LEVEL", 8.5, LABEL, "lm")
    for k in range(4):
        x = 80 + k * 36
        sc.d.rounded_rectangle([x * S, 48 * S, (x + 28) * S, 62 * S], radius=2 * S, fill=TRACK, outline=EDGE, width=2)
        sc.text(x + 14, 55, "I" * (k + 1) if k < 3 else "IV", 6.5, FAINT, "mm")
    sc.text(80, 77, "BEAM", 8.5, LABEL, "lm")
    sc.track(130, 72, 220, 82)
    sc.text(10, 101, "RACK", 8.5, LABEL, "lm")
    sc.track(60, 96, 220, 106)
    sc.save("fsv6_wall_beacon.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    dial("fsv6_tacho", 3000, 500, 100, 100, "RPM x100", red_from=2600)
    dial("fsv6_speedo", 100, 20, 5, 1, "km/h")
    lamps()
    status()
    binnacle()
    wall_console()
    wall_beacon()
    print("wrote", OUT)
