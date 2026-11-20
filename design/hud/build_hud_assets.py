"""Builds the vehicle HUD's font and its icon and shape atlases.

    PYTHONPATH=<dir with fonttools> python design/hud/build_hud_assets.py

The HUD is drawn so one HUD unit covers a whole number n of screen pixels (HudKit.density: 80% of the GUI scale,
rounded). Minecraft samples font and GUI textures with NEAREST filtering, so anything antialiased looks smooth only
when one texel lands on one screen pixel. Everything here is therefore built once per density n = 1..6 and HudKit
picks the set that matches the screen:

Font: Inter SemiBold (SIL OFL 1.1, design/hud/Inter-OFL.txt), subset to Latin-1 and the HUD's symbols, written to
assets/jeg/font/hud.ttf, with one font definition per density (font/hud_<n>.json: a ttf provider at size 7, so the
caps are 5 HUD units tall, oversampled n times). Characters it lacks fall back to the vanilla font.

Atlases (textures/gui/hud/kit_<n>.png, 128n x 24n, white on transparent, tinted when drawn):
- rows 0-1: icons in 8n cells, each drawn in its 7n x 7n top-left box. Glyphs from Google's Material Icons Round
  (Apache 2.0, design/hud/MaterialIcons-LICENSE.txt); the ammunition icon is drawn here.
- row 2 (y 16n): filled circles, diameter 6n at x 0, 4n at x 8n, 3n at x 16n, 2n at x 24n: the panel and keycap
  corners, the status lamp and the bars' round ends.
Shapes are rendered at 8x and box-filtered down, so their edges are antialiased at the target size.
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "jeg")
HERE = os.path.dirname(os.path.abspath(__file__))
INTER = os.path.join(HERE, "Inter-SemiBold.ttf")
ICON_FONT = os.path.join(HERE, "MaterialIconsRound-Regular.otf")
DENSITIES = range(1, 7)
SS = 8  # supersampling for shapes and icons

# Font size in HUD units (Inter's cap height is 0.7275 em: caps 5.1 units) and the shift that puts the cap tops on y.
FONT_SIZE = 7.0
FONT_SHIFT = [0.0, -2.0]
SYMBOLS = "°·×▲▼→←↑↓∞–—…•"

# Order is HudKit's ICON_* constants. A name is a Material Icons Round glyph; None is drawn by draw_custom.
ICONS = [
    ("hull", "shield"),
    ("energy", "bolt"),
    ("speed", "speed"),
    ("driver", "person"),
    ("seat", "event_seat"),
    ("blade", "unfold_more"),
    ("load", "landslide"),
    ("hold", "inventory_2"),
    ("grade", "straighten"),
    ("ripper", "add_road"),
    ("lane", "straight"),
    ("warn", "warning"),
    ("ammo", None),
    ("decoy", "flare"),
    ("alt", "height"),
    ("throttle", "tune"),
    ("gear", "flight_land"),
    ("clock", "schedule"),
    ("target", "gps_fixed"),
    ("bearing", "explore"),
    ("elevation", "trending_up"),
    ("tow", "rv_hookup"),
    ("power", "power_settings_new"),
    ("check", "check"),
    ("reload", "autorenew"),
    ("compass", "navigation"),
]


def build_font():
    from fontTools import subset
    from fontTools.ttLib import TTFont

    font = TTFont(INTER)
    options = subset.Options()
    options.layout_features = ["kern", "liga", "calt", "tnum"]
    options.name_IDs = ["*"]
    options.notdef_outline = True
    sub = subset.Subsetter(options)
    unicodes = list(range(0x20, 0x7F)) + list(range(0xA0, 0x100)) + [ord(c) for c in SYMBOLS]
    sub.populate(unicodes=unicodes)
    sub.subset(font)
    os.makedirs(os.path.join(ASSETS, "font"), exist_ok=True)
    font.save(os.path.join(ASSETS, "font", "hud.ttf"))
    for n in DENSITIES:
        definition = {"providers": [
            {"type": "ttf", "file": "jeg:hud.ttf", "size": FONT_SIZE, "oversample": float(n), "shift": FONT_SHIFT, "skip": ""},
            {"type": "reference", "id": "minecraft:default"},
        ]}
        with open(os.path.join(ASSETS, "font", f"hud_{n}.json"), "w", encoding="utf-8", newline="\n") as f:
            json.dump(definition, f, indent=2, ensure_ascii=False)
            f.write("\n")
    # The pixel font this replaces.
    for old in (os.path.join(ASSETS, "font", "hud.json"), os.path.join(ASSETS, "textures", "font", "hud.png")):
        if os.path.exists(old):
            os.remove(old)
    print("font: hud.ttf,", ", ".join(f"hud_{n}.json" for n in DENSITIES))


def codepoints():
    table = {}
    with open(os.path.join(HERE, "MaterialIconsRound-Regular.codepoints"), encoding="utf-8") as f:
        for line in f:
            name, code = line.split()
            table[name] = int(code, 16)
    return table


def fit(ink, box):
    """Centre an ink image (L) in a box x box image."""
    out = Image.new("L", (box, box), 0)
    out.paste(ink, ((box - ink.width) // 2, (box - ink.height) // 2))
    return out


def draw_glyph(font_path, code, box):
    """A Material icon in a box x box supersampled mask: the 24-unit design grid fills the box."""
    font = ImageFont.truetype(font_path, box)
    ch = chr(code)
    im = Image.new("L", (box * 2, box * 2), 0)
    ImageDraw.Draw(im).text((box // 2, box // 2), ch, font=font, fill=255)
    # Sized by the em (every icon at the same scale, so strokes match), centred by its ink.
    ink = im.crop(im.getbbox() or (0, 0, 1, 1))
    return fit(ink, box)


def draw_custom(name, box):
    im = Image.new("L", (box, box), 0)
    d = ImageDraw.Draw(im)
    u = box / 24.0
    if name == "ammo":
        # Three cartridges standing side by side: a round-nosed bullet on a case with a rim.
        for cx in (6.5, 12.0, 17.5):
            w = 4.2
            d.rounded_rectangle([(cx - w / 2) * u, 9.5 * u, (cx + w / 2) * u, 20.5 * u], radius=0.8 * u, fill=255)
            d.ellipse([(cx - w / 2) * u, 3.0 * u, (cx + w / 2) * u, 11.5 * u], fill=255)
            d.rectangle([(cx - w / 2) * u, 7.5 * u, (cx + w / 2) * u, 10.0 * u], fill=255)
            d.line([(cx - w / 2) * u, 10.4 * u, (cx + w / 2) * u, 10.4 * u], fill=0, width=max(1, round(0.9 * u)))
    return im


def circle(diameter_px):
    big = Image.new("L", (diameter_px * SS, diameter_px * SS), 0)
    ImageDraw.Draw(big).ellipse([0, 0, diameter_px * SS - 1, diameter_px * SS - 1], fill=255)
    return big.resize((diameter_px, diameter_px), Image.Resampling.BOX)


def build_atlases():
    table = codepoints()
    missing = [glyph for _, glyph in ICONS if glyph and glyph not in table]
    if missing:
        sys.exit(f"not in Material Icons Round: {missing}")
    out_dir = os.path.join(ASSETS, "textures", "gui", "hud")
    os.makedirs(out_dir, exist_ok=True)
    for n in DENSITIES:
        sheet = Image.new("L", (128 * n, 24 * n), 0)
        cell, box = 8 * n, 7 * n
        for i, (name, glyph) in enumerate(ICONS):
            big = box * SS
            mask = draw_glyph(ICON_FONT, table[glyph], big) if glyph else draw_custom(name, big)
            small = mask.resize((box, box), Image.Resampling.BOX)
            sheet.paste(small, ((i % 16) * cell, (i // 16) * cell))
        for slot, diameter in enumerate((6, 4, 3, 2)):
            sheet.paste(circle(diameter * n), (slot * 8 * n, 16 * n))
        rgba = Image.new("RGBA", sheet.size, (255, 255, 255, 0))
        rgba.putalpha(sheet)
        rgba.save(os.path.join(out_dir, f"kit_{n}.png"), optimize=True)
    old = os.path.join(out_dir, "icons.png")
    if os.path.exists(old):
        os.remove(old)
    print("atlases:", ", ".join(f"kit_{n}.png" for n in DENSITIES))
    print("icons:", ", ".join(f"{i}={name}" for i, (name, _) in enumerate(ICONS)))


def preview(path):
    """The density-3 atlas enlarged 4x over the panel colour, for review."""
    sheet = Image.open(os.path.join(ASSETS, "textures", "gui", "hud", "kit_3.png"))
    bg = Image.new("RGBA", sheet.size, (15, 19, 23, 255))
    bg.alpha_composite(sheet)
    bg.resize((sheet.width * 4, sheet.height * 4), Image.Resampling.NEAREST).save(path)


if __name__ == "__main__":
    build_font()
    build_atlases()
    if len(sys.argv) > 1:
        preview(sys.argv[1])
