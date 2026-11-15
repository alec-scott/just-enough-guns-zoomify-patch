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


def font(size):
    try:
        return ImageFont.truetype("arialbd.ttf", size)
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
    fnt = font(18)
    w = d.textlength(unit, font=fnt)
    d.text((c - w / 2, c + 18), unit, font=fnt, fill=DIM)
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


def status():
    img = Image.new("RGBA", (STATUS_W, STATUS_H), (7, 20, 13, 255))
    d = ImageDraw.Draw(img)
    for y in range(1, STATUS_H, 2):
        d.line([0, y, STATUS_W, y], fill=(11, 29, 19, 255))
    d.text((8, 4), "FSV-6  STATUS", font=font(15), fill=PHOSPHOR)
    d.line([6, 22, STATUS_W - 6, 22], fill=PHOSPHOR_DIM, width=1)
    fnt = font(13)
    for name, y in STATUS_BARS.items():
        d.text((8, y - 2), name, font=fnt, fill=PHOSPHOR_DIM)
        d.rectangle([STATUS_BAR_X[0] - 2, y - 1, STATUS_BAR_X[1] + 2, y + 11], outline=PHOSPHOR_DIM, width=1)
    d.text((8, STATUS_BEACON_Y - 2), "BCN", font=fnt, fill=PHOSPHOR_DIM)
    for k in range(4):
        x = STATUS_BAR_X[0] + k * 22
        d.rectangle([x, STATUS_BEACON_Y, x + 16, STATUS_BEACON_Y + 10], outline=PHOSPHOR_DIM, width=1)
    d.text((168, STATUS_BEACON_Y - 2), "TOW", font=fnt, fill=PHOSPHOR_DIM)
    d.rectangle([206, STATUS_BEACON_Y, 222, STATUS_BEACON_Y + 10], outline=PHOSPHOR_DIM, width=1)
    img.save(OUT / "fsv6_status.png")


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


def screen_base(title):
    img = Image.new("RGBA", (WALL_W, WALL_H), (7, 20, 13, 255))
    d = ImageDraw.Draw(img)
    for y in range(1, WALL_H, 2):
        d.line([0, y, WALL_W, y], fill=(11, 29, 19, 255))
    d.text((10, 4), title, font=font(16), fill=PHOSPHOR)
    d.line([8, 24, WALL_W - 8, 24], fill=PHOSPHOR_DIM, width=1)
    return img, d


def wall_console():
    img, d = screen_base("FSV-6 SUPPORT")
    fnt = font(13)
    for name, y in WALL_ROWS.items():
        d.text((10, y - 2), name, font=fnt, fill=PHOSPHOR_DIM)
        if name == "PWR":
            for k, label in enumerate(("GEN", "DSL", "BAT")):
                x = WALL_BAR_X[0] + k * 48
                d.rectangle([x, y, x + 12, y + 10], outline=PHOSPHOR_DIM, width=1)
                d.text((x + 16, y - 2), label, font=font(11), fill=PHOSPHOR_DIM)
        else:
            d.rectangle([WALL_BAR_X[0] - 2, y - 1, WALL_BAR_X[1] + 2, y + 11], outline=PHOSPHOR_DIM, width=1)
    img.save(OUT / "fsv6_wall_console.png")


def wall_beacon():
    img, d = screen_base("ROOF BEACON")
    fnt = font(13)
    d.rectangle([10, 32, 62, 84], outline=PHOSPHOR_DIM, width=1)          # the effect's icon
    d.text((80, 30), "LEVEL", font=fnt, fill=PHOSPHOR_DIM)
    for k in range(4):
        x = 80 + k * 36
        d.rectangle([x, 48, x + 28, 62], outline=PHOSPHOR_DIM, width=1)
    d.text((80, 70), "BEAM", font=fnt, fill=PHOSPHOR_DIM)
    d.rectangle([128, 71, 222, 83], outline=PHOSPHOR_DIM, width=1)
    d.text((10, 94), "RACK", font=fnt, fill=PHOSPHOR_DIM)
    d.rectangle([58, 95, 222, 107], outline=PHOSPHOR_DIM, width=1)
    img.save(OUT / "fsv6_wall_beacon.png")


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
