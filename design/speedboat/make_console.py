"""Textures for the speedboat's helm console (SpeedboatCockpitLayer), a boat's take on the helicopters' panels.

python -I design/speedboat/make_console.py

Writes to assets/jeg/textures/entity/vehicle/cockpit/:
- speedboat_console.png: the console's face (dark marine panel, bezels round the screens and dials), lit by the world.
  300 x 156 for the 15 x 7.8 px face: 20 texels a model pixel. LAYOUT gives each instrument's place in model pixels,
  as SpeedboatCockpitLayer draws them.
- speedboat_tacho.png, speedboat_speedo.png: backlit dials (0..6000 rpm, 0..50 knots). A reading f (0..1) sits at
  -135 + 270 f degrees clockwise from straight up, as CockpitPanel.needle draws the needle.
- speedboat_chart.png: the chart plotter: sea, a shoreline, depth contours and a grid. The layer draws the heading-up
  compass ring, the course line and the boat's own mark over it.
- speedboat_engine.png: the engine page: labels and empty bar frames (RPM, FUEL, TRIM, TEMP); the layer fills them at
  ENGINE_BARS.
"""
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/assets/jeg/textures/entity/vehicle/cockpit"
INTER = ROOT / "design/hud/Inter-SemiBold.ttf"
WHITE = (240, 238, 228, 255)
DIM = (150, 156, 160, 255)
RED = (230, 60, 40, 255)
CYAN = (120, 220, 255, 255)
CYAN_DIM = (60, 120, 150, 255)

# Model pixels on the console face (u across from its centre, v down from its top edge); keep in step with
# SpeedboatCockpitLayer.
FACE_W, FACE_H, TEX = 15.0, 7.8, 20
LAYOUT = {
    "chart": (-7.0, 0.45, 4.3, 4.3),     # u, v, width, height
    "engine": (2.7, 0.45, 4.3, 4.3),
    "tacho": (-1.3, 2.0, 1.05),          # centre u, v, radius
    "speedo": (1.3, 2.0, 1.05),
}
LAMP_ROW_V, LAMP_SIZE, LAMP_U = 5.45, 0.7, [-6.6 + i * 1.05 for i in range(6)]
SWITCH_U = [1.4 + i * 1.05 for i in range(6)]

ENGINE_W = 172
ENGINE_BARS = {"RPM": 46, "FUEL": 76, "TRIM": 106, "TEMP": 136}
ENGINE_BAR_X = (58, 160)


def font(size):
    try:
        return ImageFont.truetype(str(INTER), size)
    except OSError:
        return ImageFont.load_default(size=size)


def at(cx, cy, r, f):
    a = math.radians(-135 + 270 * f)
    return cx + r * math.sin(a), cy - r * math.cos(a)


def dial(name, maximum, major, minor, labels, unit, red_from=None):
    s = 256
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    c = s / 2
    d.ellipse([2, 2, s - 3, s - 3], fill=(10, 14, 20, 255), outline=(170, 176, 180, 255), width=5)
    if red_from is not None:
        f0 = red_from / maximum
        for k in range(40):
            f = f0 + (1 - f0) * k / 39
            d.line([*at(c, c, 104, f), *at(c, c, 116, f)], fill=RED, width=5)
    v = 0
    while v <= maximum + 1e-6:
        f = v / maximum
        is_major = abs(v / major - round(v / major)) < 1e-6
        d.line([*at(c, c, 98 if is_major else 106, f), *at(c, c, 118, f)], fill=WHITE, width=5 if is_major else 2)
        if is_major:
            text = str(int(round(v / labels)))
            tx, ty = at(c, c, 74, f)
            fnt = font(30)
            w = d.textlength(text, font=fnt)
            d.text((tx - w / 2, ty - 19), text, font=fnt, fill=WHITE)
        v += minor
    fnt = font(20)
    w = d.textlength(unit, font=fnt)
    d.text((c - w / 2, c + 22), unit, font=fnt, fill=DIM)
    d.ellipse([c - 12, c - 12, c + 12, c + 12], fill=(60, 64, 68, 255))
    img.save(OUT / f"{name}.png")


def console():
    w, h = int(FACE_W * TEX), int(FACE_H * TEX)
    img = Image.new("RGBA", (w, h), (30, 33, 37, 255))
    rng = random.Random(7)
    px = img.load()
    for y in range(h):
        for x in range(w):
            n = rng.randint(-3, 3)
            r, g, b, a = px[x, y]
            px[x, y] = (r + n, g + n, b + n, 255)
    d = ImageDraw.Draw(img)

    def box(u, v):
        return (u + FACE_W / 2) * TEX, v * TEX

    # A raised lip round the face.
    d.rectangle([0, 0, w - 1, h - 1], outline=(52, 56, 60, 255), width=3)
    for key in ("chart", "engine"):
        u, v, bw, bh = LAYOUT[key]
        x0, y0 = box(u, v)
        d.rounded_rectangle([x0 - 7, y0 - 7, x0 + bw * TEX + 7, y0 + bh * TEX + 7], radius=8, fill=(14, 15, 17, 255),
                            outline=(66, 70, 74, 255), width=2)
        # Soft keys under each screen.
        for k in range(4):
            kx = x0 + 8 + k * (bw * TEX - 16) / 3.0
            d.rounded_rectangle([kx - 6, y0 + bh * TEX + 10, kx + 6, y0 + bh * TEX + 15], radius=2, fill=(70, 74, 78, 255))
    for key in ("tacho", "speedo"):
        u, v, r = LAYOUT[key]
        cx, cy = box(u, v)
        rr = r * TEX
        d.ellipse([cx - rr - 6, cy - rr - 6, cx + rr + 6, cy + rr + 6], fill=(150, 156, 160, 255))
        d.ellipse([cx - rr - 2, cy - rr - 2, cx + rr + 2, cy + rr + 2], fill=(12, 13, 15, 255))
    # The lamp row's sockets and the switch row (left: warning lamps; right: toggles).
    for u in LAMP_U:
        x0, y0 = box(u, LAMP_ROW_V)
        d.rounded_rectangle([x0 - 2, y0 - 2, x0 + LAMP_SIZE * TEX + 2, y0 + LAMP_SIZE * TEX + 2], radius=4,
                            fill=(12, 13, 15, 255), outline=(80, 84, 88, 255), width=1)
    for u in SWITCH_U:
        x0, y0 = box(u, LAMP_ROW_V)
        d.rounded_rectangle([x0, y0, x0 + 12, y0 + LAMP_SIZE * TEX], radius=3, fill=(18, 19, 21, 255))
        d.rounded_rectangle([x0 + 3, y0 + 2, x0 + 9, y0 + 8], radius=2, fill=(180, 184, 188, 255))
    # The middle: a compass bowl between the dials and a key switch.
    cx, cy = box(0.0, 4.55)
    d.ellipse([cx - 14, cy - 14, cx + 14, cy + 14], fill=(150, 156, 160, 255))
    d.ellipse([cx - 11, cy - 11, cx + 11, cy + 11], fill=(16, 18, 22, 255))
    img.save(OUT / "speedboat_console.png")


def chart():
    s = 172
    img = Image.new("RGBA", (s, s), (10, 42, 78, 255))
    d = ImageDraw.Draw(img)
    # Deeper water darker, depth contours along a coast in the top left.
    for k, colour in enumerate([(22, 70, 112, 255), (34, 96, 140, 255), (52, 124, 164, 255)]):
        pts = [(0, 30 + 26 * (3 - k) + 6 * math.sin(x / 13.0 + k)) for x in range(0, 1)]
        poly = [(0, 0)]
        for x in range(0, s + 1, 6):
            poly.append((x, 18 + 22 * (3 - k) + 9 * math.sin(x / 19.0 + k * 0.7) - x * 0.35))
        poly.append((s, 0))
        d.polygon(poly, fill=colour)
    land = [(0, 0)]
    for x in range(0, s + 1, 4):
        land.append((x, 10 + 8 * math.sin(x / 15.0) + 4 * math.sin(x / 5.3) - x * 0.35))
    land.append((s, 0))
    d.polygon(land, fill=(196, 176, 112, 255))
    # An island and a marked channel.
    d.ellipse([118, 112, 150, 134], fill=(196, 176, 112, 255), outline=(60, 140, 180, 255), width=2)
    for i, (x, y) in enumerate([(40, 140), (62, 120), (84, 100), (104, 82)]):
        d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(230, 60, 40, 255) if i % 2 else (60, 200, 90, 255))
    # Grid.
    for g in range(0, s, 43):
        d.line([g, 0, g, s], fill=(255, 255, 255, 34), width=1)
        d.line([0, g, s, g], fill=(255, 255, 255, 34), width=1)
    fnt = font(12)
    d.rectangle([0, s - 18, s, s], fill=(4, 12, 22, 230))
    d.text((6, s - 16), "CHART  HDG UP", font=fnt, fill=CYAN)
    d.text((s - 46, s - 16), "0.5nm", font=fnt, fill=CYAN_DIM)
    img.save(OUT / "speedboat_chart.png")


def engine():
    s = ENGINE_W
    img = Image.new("RGBA", (s, s), (6, 14, 22, 255))
    d = ImageDraw.Draw(img)
    for y in range(1, s, 2):
        d.line([0, y, s, y], fill=(9, 20, 30, 255))
    d.text((8, 6), "ENGINE", font=font(15), fill=CYAN)
    d.line([6, 28, s - 6, 28], fill=CYAN_DIM, width=1)
    fnt = font(12)
    for name, y in ENGINE_BARS.items():
        d.text((8, y - 1), name, font=fnt, fill=CYAN_DIM)
        d.rectangle([ENGINE_BAR_X[0] - 2, y - 1, ENGINE_BAR_X[1] + 2, y + 13], outline=CYAN_DIM, width=1)
    # Trim reads from the middle (bow down to bow up).
    mid = (ENGINE_BAR_X[0] + ENGINE_BAR_X[1]) // 2
    d.line([mid, ENGINE_BARS["TRIM"] - 4, mid, ENGINE_BARS["TRIM"] + 16], fill=CYAN_DIM, width=1)
    img.save(OUT / "speedboat_engine.png")


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    dial("speedboat_tacho", 6000, 1000, 250, 1000, "RPM x1000", red_from=5200)
    dial("speedboat_speedo", 50, 10, 2, 1, "KNOTS")
    console()
    chart()
    engine()
    print("wrote", OUT)
