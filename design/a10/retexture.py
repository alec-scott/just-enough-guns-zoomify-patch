"""The A-10's texture upgrade (the user, 2026-11-19: "do that": the A-10 texture and animation upgrade).

python -I design/a10/retexture.py

The imported model textured its faces from a small 512 x 512 atlas: most faces stretched a 4 x 2 texel patch over a
20 px panel, and the left and right halves shared their texels (88% of the atlas), so nothing could be weathered per
panel and no marking could read on both sides. This gives every face its own texels (refit_lib.rebake_imported, 2 a
model pixel, its old paint resampled, its UV moved with the same signs so it looks as it did), then:

- its own light paint (white in the game; a Compass Ghost two-tone was tried and taken off, 2026-11-19);
- the detail pass, light: panel seams, rivet rows, worn leading edges, faint grime streaks;
- stains where an A-10 gets them: the GAU-8's gun gas over the nose round the muzzle and streaking back, the engines'
  exhaust on their nacelles' rims and blown back over the tailplane and the fins' inner faces, fluid streaks running
  aft under the wings;
- the tail code "DM" (Davis-Monthan) and the serial "AF 80-204" on the fins' outer faces.

The fan disc in each intake (qianzhou's 20 px square, painted with a round fan whose corners are clear) has no edge
faces in a10_base.geo.json (removed 2026-11-19): they had stretched the whole fan picture over a 1 px rim, clear at
the corners; given their own texels they turned solid, so the disc's square rim and corners stood out of the
nacelle, turning with the fan (the user's review: "fix the engine animation and model").

The original geometry and texture are kept in design/a10/a10_base.geo.json and texture_base.png (taken the first time),
so it can be re-run and tuned. Run design/a10/articulate.py first after re-importing the model (the canopy's hinge).
"""
import json
import pathlib
import shutil
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "design/superbwarfare-port"))
sys.path.insert(0, str(ROOT / "design/superbwarfare-port/triage"))
sys.path.insert(0, str(ROOT / "design/ground-vehicles"))
import refit_lib as L  # noqa: E402
import detail_texture as D  # noqa: E402
import render_geo as R  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/jeg"
GEO = ASSETS / "geckolib/models/entity/vehicle/a10.geo.json"
TEXTURE = ASSETS / "textures/entity/vehicle/a10.png"
TEXELS = 2
INTER = ROOT / "design/hud/Inter-SemiBold.ttf"
# Which fin faces' texture runs back to front (from the review render), so the markings read on both sides.
MIRRORED = {"east": False, "west": False}

# Positions in the model's render space (render_geo.load_quads: -Z forward, X mirrored from the file).
# Its own light paint, which reads white in the game (the user, 2026-11-19: "give the A10 back its white colour"): the
# darker Compass Ghost grey is gone, the wear and grime are light, the stains where an A-10 gets them kept.
DETAIL = dict(upscale=1, mud_height=0.0, mud=0.0, wear=0.45, streaks=0.2, paint_lum=0.95, paint_sat=0.45, stains=[
    # The GAU-8's gun gas: dense round the muzzle, streaking back along the nose.
    dict(centre=(-2.0, 27.0, -122.0), radius=26.0, falloff=1.3, colour=(46, 46, 44), strength=0.7, streak=(0, 0, 1), seed=42),
    dict(centre=(-2.0, 27.0, -123.0), radius=9.0, colour=(40, 40, 38), strength=0.8, seed=43),
    # The engines: soot round the nacelles' exhaust rims, and their blast blown back over the tailplane and fins.
    dict(centre=(27.0, 54.0, 68.0), radius=12.0, colour=(40, 38, 36), strength=0.6, seed=44),
    dict(centre=(-27.0, 54.0, 68.0), radius=12.0, colour=(40, 38, 36), strength=0.6, seed=45),
    dict(centre=(27.0, 50.0, 100.0), radius=26.0, falloff=1.2, colour=(66, 62, 56), strength=0.3, streak=(0, 0, 1), seed=46),
    dict(centre=(-27.0, 50.0, 100.0), radius=26.0, falloff=1.2, colour=(66, 62, 56), strength=0.3, streak=(0, 0, 1), seed=47),
    # Hydraulic fluid and fuel streaking aft under the wings.
    dict(bones="zuoyi|youyi|zhong", facing=(0, -1, 0), dot=0.5, colour=(80, 74, 62), strength=0.18, streak=(0, 0, 1), seed=48),
])


def font(size):
    try:
        return ImageFont.truetype(str(INTER), size)
    except OSError:
        return ImageFont.load_default(size=size)


def tail_codes(texture, faces):
    """"DM" and "AF 80-204" on each fin's outer face (the tail bone's large east and west faces), dark grey as the
    A-10's low-visibility markings."""
    draw = ImageDraw.Draw(texture)
    count = 0
    for item in faces:
        if item["bone"] != "tail" or item["face"] not in ("east", "west"):
            continue
        w, h = item["size"]
        if w < 10 or h < 30:  # the fin's main panel, 13 x 38 px
            continue
        x0, y0, x1, y1 = item["rect"]
        tile = Image.new("RGBA", (x1 - x0, y1 - y0), (0, 0, 0, 0))
        d = ImageDraw.Draw(tile)
        big = font(int(min((y1 - y0) * 0.22, (x1 - x0) * 0.62)))
        small = font(int(min((y1 - y0) * 0.06, (x1 - x0) * 0.15)))
        d.text(((x1 - x0) * 0.5, (y1 - y0) * 0.36), "DM", font=big, fill=(52, 54, 56, 255), anchor="mm")
        d.text(((x1 - x0) * 0.5, (y1 - y0) * 0.56), "AF 80-204", font=small, fill=(52, 54, 56, 255), anchor="mm")
        if MIRRORED[item["face"]]:
            tile = tile.transpose(Image.FLIP_LEFT_RIGHT)
        texture.alpha_composite(tile, (x0, y0))
        count += 1
    return count


def main():
    base_geo, base_tex = HERE / "a10_base.geo.json", HERE / "texture_base.png"
    if not base_geo.exists():
        shutil.copyfile(GEO, base_geo)
        shutil.copyfile(TEXTURE, base_tex)
    geo = json.loads(base_geo.read_text(encoding="utf-8"))
    # Keep the canopy's hinge (articulate.py) in step with the live model.
    live = {b["name"]: b for b in json.loads(GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]["bones"]}
    for b in geo["minecraft:geometry"][0]["bones"]:
        if b["name"] == "bone61":
            b["pivot"] = live["bone61"]["pivot"]
    data, baked, faces = L.rebake_imported(geo, Image.open(base_tex), TEXELS)
    result, _, _ = D.detail_image(data, baked, DETAIL)
    painted = tail_codes(result, faces)
    GEO.write_text(json.dumps(data, indent="\t") + "\n", encoding="utf-8", newline="")
    result.save(TEXTURE, optimize=True)
    # Before and after, from the front and rear quarters.
    q0, ts0, _, _ = R.load_quads(str(base_geo))
    q1, ts1, _, _ = R.load_quads(data)
    tiles = []
    for label, q, tex, ts in (("before", q0, Image.open(base_tex).convert("RGBA"), ts0), ("after", q1, result, ts1)):
        q = R.visible(q, tex, ts)
        for yaw, pitch in ((35, 22), (215, 22)):
            img = R.render(q, tex, ts, yaw, pitch, size=(900, 600))
            ImageDraw.Draw(img).text((10, 8), f"{label}  yaw {yaw}", fill=(20, 20, 20), font=R.font(16))
            tiles.append(img)
    sheet = Image.new("RGB", (1806, 1206), (255, 255, 255))
    for i, tile in enumerate(tiles):
        sheet.paste(tile, ((i % 2) * 906, (i // 2) * 606))
    sheet.save(HERE / "review-texture.png", optimize=True)
    print("texture", result.size, "uv", data["minecraft:geometry"][0]["description"]["texture_width"],
          data["minecraft:geometry"][0]["description"]["texture_height"], "faces", len(faces), "tail codes", painted)


if __name__ == "__main__":
    main()
