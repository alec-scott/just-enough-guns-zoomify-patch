"""The 5-inch incendiary shell's icon (the user, 2026-11-19: the GS shells became incendiary shells).

python -I design/mk42/incendiary_icon.py

The icon keeps the grapeshot shell's (shell_5in_gs.png, from Superb Warfare's port; kept as shell_5in_gs_base.png the
first time) shape: its blue-violet nose, band and lettering turn fire orange, and the letters "GS" become "IN",
drawn in the same 5-pixel style on the black tab.
"""
import colorsys
import pathlib
import shutil

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ICON = ROOT / "src/main/resources/assets/jeg/textures/item/shell_5in_gs.png"
BASE = HERE / "shell_5in_gs_base.png"
# The tab the letters sit on: rows 1-5, columns 1-9 (black, letters in the shell's colour).
LETTERS = {
    # I: three wide, bars top and bottom.
    (1, 1), (2, 1), (3, 1), (2, 2), (2, 3), (2, 4), (1, 5), (2, 5), (3, 5),
    # N: four wide.
    (5, 1), (8, 1), (5, 2), (6, 2), (8, 2), (5, 3), (7, 3), (8, 3), (5, 4), (8, 4), (5, 5), (8, 5),
}
LETTER = (238, 112, 34, 255)
HUE = 22 / 360  # fire orange


def main():
    if not BASE.exists():
        shutil.copyfile(ICON, BASE)
    icon = Image.open(BASE).convert("RGBA")
    out = icon.copy()
    for y in range(icon.height):
        for x in range(icon.width):
            r, g, b, a = icon.getpixel((x, y))
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > 0.15 and 0.55 < h < 0.8:
                # The violet parts: orange of the same lightness, a little brighter and more saturated.
                nr, ng, nb = colorsys.hsv_to_rgb(HUE, min(1.0, s * 1.9 + 0.2), min(1.0, v * 1.25))
                out.putpixel((x, y), (round(nr * 255), round(ng * 255), round(nb * 255), a))
    for y in range(1, 6):
        for x in range(1, 10):
            out.putpixel((x, y), LETTER if (x, y) in LETTERS else (0, 0, 0, 255))
    out.save(ICON)
    print("icon", ICON.relative_to(ROOT))


if __name__ == "__main__":
    main()
