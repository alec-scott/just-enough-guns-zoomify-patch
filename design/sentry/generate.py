"""Author the perimeter sentry's GeckoLib assets, rig manifest, hitboxes and review images (fleet plan section 6).

python -I design/sentry/generate.py

A remote weapon station on a tripod (Kongsberg Protector / SGR-A1 class, see BRIEF.md), about 1.6 x 2.1 x 1.6 blocks:
a tripod and hub, a pan-tilt head (`turret` yaw, `barrel` pitch) with a 7.62 mm machine gun, its ammunition can and a
belt that empties as the hold runs dry (`belt_1`..`belt_4`), a day/thermal camera with an IR lamp beside the gun, and a
status lamp (`lamp_green` idle, `lamp_amber` tracking, `lamp_red` firing or out of ammunition) on the camera.
Model pixels, 16 a block, the gun faces -Z and +X is the sentry's left. Requires Pillow and numpy (render_geo).
"""
from __future__ import annotations

import json
import math
import pathlib
import random
import sys

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE.parent / "superbwarfare-port"))
import refit_lib as L  # noqa: E402
import render_geo as R  # noqa: E402

ID = "sentry"
ASSETS = ROOT / "src/main/resources/assets/jeg"
VEHICLE_JSON = ROOT / f"src/main/resources/data/jeg/vehicles/{ID}.json"

MATERIALS = {
    "olive": (86, 92, 58, 255), "olive_dark": (64, 69, 44, 255), "steel": (84, 86, 84, 255),
    "gunmetal": (52, 54, 52, 255), "dark": (30, 31, 30, 255), "rubber": (36, 36, 34, 255),
    "glass": (110, 150, 168, 160), "ir_lens": (92, 22, 24, 255), "brass": (156, 128, 62, 255),
    "lamp_green": (70, 235, 100, 255), "lamp_amber": (255, 176, 46, 255), "lamp_red": (242, 46, 36, 255),
    "label": (70, 78, 50, 255), "copper": (176, 106, 60, 255),
}
# Every face its own texels (refit_lib.bake_unique), weathered by the detail pass (the fleet's model and texture review,
# 2026-11-19: the sentry was one flat 16-px swatch a material). TEXELS a model pixel.
TEXELS = 8
TILE = 16  # the old tile atlas, still laid out before the bake replaces every face's UV
ATLAS = (128, 32)
UV_SIZE = [128, 32]
# Kept as baked through the weathering: glass, lenses, lamps.
KEPT = ("glass", "ir_lens", "lamp_green", "lamp_amber", "lamp_red")
DETAIL = dict(upscale=1, mud_height=5.0, mud=0.5, wear=0.9, streaks=0.6, paint_lum=0.8, paint_sat=0.5)

# Dimensions (model px).
HUB_Y = 18.0          # the tripod's hub
PAN_Y = 21.0          # the head pans about this
TRUNNION_Y = 29.0     # the gun elevates about this
LEG_REACH = 12.0      # feet from the centre
LEG_BEARINGS = (180.0, 60.0, 300.0)  # one leg forward under the gun, two behind
MUZZLE_Z = -22.0
CAMERA_EYE = (-3.75, 33.2, -1.0)
ELEVATION = (-30.0, 60.0)

BONES: list[dict] = []
BY_NAME: dict[str, dict] = {}


def bone(name, parent="root", pivot=(0, 0, 0), rotation=None):
    entry = {"name": name, "pivot": [float(v) for v in pivot], "cubes": []}
    if parent:
        entry["parent"] = parent
    if rotation and any(rotation):
        entry["rotation"] = [float(v) for v in rotation]
    BONES.append(entry)
    BY_NAME[name] = entry
    return name


def material_uv(material, size):
    index = list(MATERIALS).index(material)
    u, v = (index % 8) * TILE + 1, (index // 8) * TILE + 1
    x, y, z = size
    clip = lambda a: max(0.5, min(TILE - 2, a))
    return {face: {"uv": [u, v], "uv_size": [clip(a), clip(b)]}
            for face, a, b in [("north", x, y), ("south", x, y), ("east", z, y), ("west", z, y), ("up", x, z), ("down", x, z)]}


def box(parent, a, b, material, rotation=None, pivot=None):
    origin = [round(min(a[i], b[i]), 4) for i in range(3)]
    size = [round(max(0.02, abs(b[i] - a[i])), 4) for i in range(3)]
    cube = {"origin": origin, "size": size, "uv": material_uv(material, size), "_material": material}
    if rotation and any(rotation):
        cube["rotation"] = [float(v) for v in rotation]
        cube["pivot"] = [round(float(v), 4) for v in (pivot or [origin[i] + size[i] / 2 for i in range(3)])]
    BY_NAME[parent]["cubes"].append(cube)


def cylinder_y(parent, x, z, y0, y1, radius, material, layers=4):
    step = 2 * radius / layers
    for i in range(layers):
        offset = -radius + (i + .5) * step
        half = math.sqrt(max(.001, radius * radius - offset * offset))
        box(parent, (x - radius + i * step, y0, z - half), (x - radius + (i + 1) * step + .02, y1, z + half), material)


def rod_x(parent, p0, p1, thickness, material):
    """A bar between two points of the same x, turned about X."""
    dy, dz = p1[1] - p0[1], p1[2] - p0[2]
    mid = [(p0[i] + p1[i]) / 2 for i in range(3)]
    length = math.hypot(dy, dz)
    # A positive turn about X brings +Y toward -Z (as the legs' first tilt did).
    angle = math.degrees(math.atan2(-dz, dy))
    box(parent, (mid[0] - thickness / 2, mid[1] - length / 2, mid[2] - thickness / 2),
        (mid[0] + thickness / 2, mid[1] + length / 2, mid[2] + thickness / 2), material, rotation=(angle, 0, 0), pivot=mid)


def along(p0, p1, f):
    return tuple(p0[i] + (p1[i] - p0[i]) * f for i in range(3))


def cylinder_z(parent, x, y, z0, z1, radius, material, layers=3):
    step = 2 * radius / layers
    for i in range(layers):
        offset = -radius + (i + .5) * step
        half = math.sqrt(max(.001, radius * radius - offset * offset))
        box(parent, (x - half, y - radius + i * step, z0), (x + half, y - radius + (i + 1) * step + .02, z1), material)


def create_model():
    bone("root", None)
    # ---- The tripod: a hub and three splayed legs with feet.
    bone("tripod", "root")
    box("tripod", (-2.5, HUB_Y - 3, -2.5), (2.5, HUB_Y + 0.5, 2.5), "olive_dark")
    box("tripod", (-2.7, HUB_Y - 0.4, -2.7), (2.7, HUB_Y + 0.3, 2.7), "gunmetal")         # the hub's cap ring
    cylinder_y("tripod", 0, 0, HUB_Y + 0.5, PAN_Y, 2.2, "steel")
    cylinder_y("tripod", 0, 0, HUB_Y + 1.6, HUB_Y + 2.4, 2.6, "gunmetal")                # the column's clamp
    box("tripod", (2.2, HUB_Y + 1.7, -0.4), (3.6, HUB_Y + 2.3, 0.4), "dark")             # its lever
    # The battery and radio box on the back of the column, its whip antenna.
    box("tripod", (-2.2, HUB_Y - 3.6, 2.5), (2.2, HUB_Y + 0.8, 5.2), "olive_dark")
    box("tripod", (-1.9, HUB_Y - 3.3, 5.2), (1.9, HUB_Y + 0.5, 5.35), "label")
    box("tripod", (-2.3, HUB_Y + 0.8, 2.4), (2.3, HUB_Y + 1.2, 5.3), "gunmetal")           # its lid
    box("tripod", (1.1, HUB_Y + 1.2, 4.1), (1.6, HUB_Y + 2.0, 4.6), "dark")                # antenna base
    box("tripod", (1.25, HUB_Y + 2.0, 4.25), (1.45, HUB_Y + 12.0, 4.45), "dark")           # the whip
    for i, bearing in enumerate(LEG_BEARINGS, start=1):
        leg = bone(f"leg_{i}", "tripod", (0, HUB_Y - 1, 0), (0, bearing, 0))
        top, foot = (0.0, HUB_Y - 1.6, 2.2), (0.0, 1.2, LEG_REACH)
        knee = along(top, foot, 0.58)
        # Hinge lug at the hub, the upper leg (olive tube), the clamp and its knob, the sliding lower leg (steel).
        box(leg, (-1.3, HUB_Y - 3.0, 1.2), (1.3, HUB_Y - 0.6, 3.0), "olive_dark")
        box(leg, (-1.45, HUB_Y - 2.2, 1.6), (1.45, HUB_Y - 1.4, 2.4), "steel")             # hinge pin
        rod_x(leg, top, along(top, foot, 0.62), 1.6, "olive")
        rod_x(leg, along(top, foot, 0.5), foot, 1.15, "steel")
        rod_x(leg, along(top, foot, 0.55), along(top, foot, 0.63), 2.1, "gunmetal")
        box(leg, (0.9, knee[1] - 0.35, knee[2] - 0.35), (1.9, knee[1] + 0.35, knee[2] + 0.35), "dark")
        box(leg, (-1.6, 0.0, LEG_REACH - 1.6), (1.6, 0.7, LEG_REACH + 1.6), "rubber")      # the foot pad
        box(leg, (-0.8, 0.7, LEG_REACH - 0.8), (0.8, 1.6, LEG_REACH + 0.8), "gunmetal")    # its swivel
    # ---- The head: pans on the hub.
    bone("turret", "root", (0, PAN_Y, 0))
    cylinder_y("turret", 0, 0, PAN_Y, PAN_Y + 2, 4.0, "dark")
    for ring in (PAN_Y + 0.35, PAN_Y + 1.15):
        cylinder_y("turret", 0, 0, ring, ring + 0.4, 4.35, "gunmetal")                     # cooling rings
    box("turret", (-3.2, PAN_Y + 0.1, 3.2), (-0.8, PAN_Y + 2.6, 5.8), "olive_dark")        # the pan motor
    box("turret", (-2.9, PAN_Y + 2.6, 3.5), (-1.1, PAN_Y + 3.0, 5.5), "gunmetal")          # its cap
    for side in (-1, 1):
        box("turret", (side * 4.4 - 0.6, PAN_Y + 2, -2.5), (side * 4.4 + 0.6, TRUNNION_Y + 1.5, 2.5), "olive")
        box("turret", (side * 4.4 - 0.75, PAN_Y + 2, -2.6), (side * 4.4 + 0.75, PAN_Y + 3.0, 2.6), "olive_dark")  # foot
        for dz in (-1.6, 1.6):
            box("turret", (side * 5.0 - 0.2, PAN_Y + 2.2, dz - 0.3), (side * 5.0 + 0.2, PAN_Y + 2.8, dz + 0.3), "steel")
        box("turret", (side * 5.0 - 0.25, TRUNNION_Y - 1.2, -1.2), (side * 5.0 + 0.25, TRUNNION_Y + 1.2, 1.2), "steel")  # bearing cap
    box("turret", (-4.4, PAN_Y + 1.8, 2.0), (4.4, PAN_Y + 3.4, 4.0), "olive_dark")      # the yoke's back bar
    # ---- The gun and everything that elevates with it.
    bone("barrel", "turret", (0, TRUNNION_Y, 0))
    y = TRUNNION_Y
    box("barrel", (-1.5, y - 1.5, -6.0), (1.5, y + 2.0, 7.0), "gunmetal")                # receiver
    box("barrel", (-1.4, y + 2.0, -3.0), (1.4, y + 2.6, 4.0), "dark")                     # feed cover
    box("barrel", (-1.6, y - 2.0, 7.0), (1.6, y + 2.5, 8.5), "dark")                      # back plate
    box("barrel", (-1.2, y - 1.0, 8.5), (1.2, y + 0.2, 10.0), "rubber")                   # spade grips
    cylinder_z("barrel", 0.0, y, MUZZLE_Z + 2.0, -6.0, 0.65, "dark")                       # barrel
    cylinder_z("barrel", 0.0, y, MUZZLE_Z, MUZZLE_Z + 2.0, 0.95, "dark")                   # flash hider
    box("barrel", (-0.4, y - 1.4, -14.0), (0.4, y - 0.6, -6.0), "steel")                  # gas tube
    box("barrel", (-4.4, y - 0.8, -0.8), (4.4, y + 0.8, 0.8), "steel")                     # trunnion pin
    box("barrel", (-0.85, y - 1.6, -15.2), (0.85, y + 0.75, -14.0), "dark")                # gas block
    box("barrel", (-0.3, y + 0.75, -15.0), (0.3, y + 1.9, -14.4), "dark")                  # front sight
    box("barrel", (-1.0, y + 0.45, -13.0), (1.0, y + 1.0, -6.0), "gunmetal")               # heat shield
    for z in (-12.2, -10.6, -9.0, -7.4):
        box("barrel", (-0.7, y + 1.0, z), (0.7, y + 1.06, z + 0.7), "dark")                # its slots
    for z in (-11.2, -8.6):
        box("barrel", (-0.2, y + 1.0, z - 0.2), (0.2, y + 2.3, z + 0.2), "dark")           # carry handle posts
    box("barrel", (-0.35, y + 2.1, -11.6), (0.35, y + 2.6, -8.2), "rubber")                # carry handle
    box("barrel", (-1.45, y + 2.6, 3.4), (1.45, y + 2.75, 4.0), "steel")                   # feed cover latch
    box("barrel", (-0.35, y + 2.6, 4.6), (0.35, y + 3.7, 5.1), "dark")                     # rear sight leaf
    box("barrel", (-1.56, y - 0.9, -1.8), (-1.5, y + 0.7, 2.2), "dark")                    # ejection port
    box("barrel", (-2.4, y + 0.1, -4.6), (-1.5, y + 0.7, -3.8), "steel")                   # charging handle
    for side in (-1, 1):
        box("barrel", (side * 0.55 - 0.18, y - 1.25, -16.5), (side * 0.55 + 0.18, y - 0.9, -7.0), "dark")  # bipod, folded
    box("barrel", (-0.15, y + 0.9, MUZZLE_Z + 0.3), (0.15, y + 1.0, MUZZLE_Z + 1.7), "rubber")      # flash hider slots
    for side in (-1, 1):
        box("barrel", (side * 0.95 - 0.05, y - 0.15, MUZZLE_Z + 0.3), (side * 0.95 + 0.05, y + 0.15, MUZZLE_Z + 1.7), "rubber")
    # Ammunition can on the left, its belt into the feed tray.
    box("barrel", (1.6, y - 4.0, -3.0), (5.0, y + 1.5, 3.0), "olive")
    box("barrel", (1.55, y + 0.9, -3.05), (5.05, y + 1.6, 3.05), "olive_dark")             # the lid's rim
    box("barrel", (5.0, y + 0.3, -0.7), (5.3, y + 1.2, 0.7), "steel")                      # its latch
    box("barrel", (2.4, y + 1.6, -0.3), (4.2, y + 1.95, 0.3), "dark")                      # its handle
    box("barrel", (4.95, y - 2.6, -2.0), (5.06, y + 0.4, 2.0), "label")
    for i in range(1, 5):
        seg = bone(f"belt_{i}", "barrel", (0, y, 0))
        x0 = 4.6 - i * 0.78
        box(seg, (x0, y + 1.6, -1.0), (x0 + 0.62, y + 2.2, 0.9), "brass")                  # the case
        box(seg, (x0 + 0.1, y + 1.7, -1.6), (x0 + 0.52, y + 2.1, -1.0), "copper")          # the bullet
        box(seg, (x0 - 0.04, y + 1.5, -0.5), (x0 + 0.66, y + 1.62, 0.6), "gunmetal")       # the link
    # The day/thermal camera on the right, the IR lamp under it, the status lamp on top.
    box("barrel", (-5.6, y + 1.0, -4.0), (-1.9, y + 4.0, 3.0), "dark")
    box("barrel", (-5.2, y + 1.4, -4.3), (-2.3, y + 3.6, -4.0), "glass")
    box("barrel", (-5.75, y + 3.95, -5.6), (-1.75, y + 4.25, -3.9), "gunmetal")            # sunshade, top
    for x in (-5.75, -2.05):
        box("barrel", (x, y + 1.1, -5.4), (x + 0.3, y + 4.0, -3.9), "gunmetal")            # and sides
    box("barrel", (-5.4, y + 1.2, -4.12), (-2.1, y + 1.4, -3.95), "steel")                 # lens ring
    box("barrel", (-4.3, y + 2.0, 3.0), (-3.2, y + 3.0, 3.8), "steel")                     # its connector
    box("barrel", (-5.2, y - 1.6, -3.5), (-2.3, y + 0.6, 1.0), "gunmetal")
    box("barrel", (-4.9, y - 1.3, -3.8), (-2.6, y + 0.3, -3.5), "ir_lens")
    # One lamp, big enough to read from the wall walk (1.6 px; three 0.8 px dots could not be made out at 3 blocks,
    # 2026-11-18): its three colours are bones in the same place, one shown at a time (and drawn glowing,
    # make_glow).
    for colour in ("lamp_green", "lamp_amber", "lamp_red"):
        lamp = bone(colour, "barrel", (0, y, 0))
        box(lamp, (-4.55, y + 4.0, 0.6), (-2.95, y + 5.4, 2.2), colour)


def paint_label(base, faces):
    """Stencils on the ammunition can's label and the battery box's: two lines of pale lettering."""
    px = base.load()
    for item in faces:
        if item["material"] != "label" or item["face"] not in ("east", "south"):
            continue
        x0, y0, x1, y1 = item["rect"]
        for row in (0.35, 0.62):
            yy = y0 + int((y1 - y0) * row)
            for xx in range(x0 + 2, x1 - 2):
                if (xx * 7 + yy) % 5 != 0:
                    px[xx, yy] = (214, 208, 176, 255)


def paint_tile(name, rgba, rng):
    tile = Image.new("RGBA", (TILE, TILE))
    px = tile.load()
    for yy in range(TILE):
        for xx in range(TILE):
            n = 0 if name.startswith("lamp") or name == "glass" else rng.choice((-4, -2, 0, 0, 2, 4))
            c = [max(0, min(255, v + n)) for v in rgba[:3]]
            px[xx, yy] = (*c, rgba[3])
    if name in ("olive", "olive_dark"):
        for _ in range(14):
            xx, yy = rng.randrange(TILE), rng.randrange(TILE)
            px[xx, yy] = (*(max(0, v - 14) for v in rgba[:3]), 255)
    if name == "label":
        for xx in range(2, 13):
            px[xx, 6] = (220, 214, 180, 255)
            px[xx, 9] = (220, 214, 180, 255)
    return tile


def make_texture():
    atlas = Image.new("RGBA", ATLAS, (0, 0, 0, 0))
    rng = random.Random(762)
    for i, (name, rgba) in enumerate(MATERIALS.items()):
        atlas.paste(paint_tile(name, rgba, rng), (i % 8 * TILE, i // 8 * TILE))
    return atlas


def make_glow():
    """The status lamps alone, for the emissive glow layer (VehicleGlowLayer draws <id>_glow.png at full brightness, so
    the lit lamp shows at night and blooms under a shader pack; the lamps that are off are hidden bones)."""
    atlas = Image.new("RGBA", ATLAS, (0, 0, 0, 0))
    rng = random.Random(762)
    for i, (name, rgba) in enumerate(MATERIALS.items()):
        if name.startswith("lamp"):
            atlas.paste(paint_tile(name, rgba, rng), (i % 8 * TILE, i // 8 * TILE))
    return atlas


def geometry():
    bones = []
    for b in BONES:
        out = {k: v for k, v in b.items() if k != "cubes"}
        out["cubes"] = [{k: v for k, v in c.items() if not k.startswith("_")} for c in b["cubes"]]
        if not out["cubes"]:
            del out["cubes"]
        bones.append(out)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.jeg.{ID}", "texture_width": UV_SIZE[0], "texture_height": UV_SIZE[1],
                        "visible_bounds_width": 4, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0]},
        "bones": bones}]}


def to_data(p):
    return [round(float(p[0]) / 16, 4), round(float(p[1]) / 16, 4), round(-float(p[2]) / 16, 4) + 0.0]


def pose(elevation=0.0, yaw=0.0):
    return {"turret": (0, yaw, 0), "barrel": (-elevation, 0, 0)}


def json_rows(obb):
    rows = []
    for row in obb:
        keys = ["part", "x", "y", "z", "half_width", "half_height", "half_depth"] + (["follows"] if "follows" in row else [])
        rows.append("    {" + ", ".join(f'"{k}": {json.dumps(row[k] if isinstance(row[k], str) else float(row[k]))}' for k in keys) + "}")
    return rows


def bake():
    """Unique UVs, the base paint, the weathering pass, glass and lamps put back; and the glow layer to match."""
    sys.path.insert(0, str(ROOT / "design/ground-vehicles"))
    import detail_texture as D
    uv, faces, base = L.bake_unique(BONES, MATERIALS, TEXELS)
    UV_SIZE[:] = [uv[0], uv[1]]
    paint_label(base, faces)
    geo = L.resolve_zfighting(geometry())
    texture, _, _ = D.detail_image(geo, base, DETAIL)
    L.paste_faces(texture, base, faces, KEPT)
    lit = {name: MATERIALS[name] for name in KEPT if name.startswith("lamp")}
    glow = L.glow_of(faces, base.size, lit, lit)
    return geo, texture, glow


def main():
    create_model()
    geo, texture, glow = bake()
    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, texture)
    sub = lambda name: L.subtree(by, name)
    lo, hi = L.extent(quads)
    feet_low = min(float(q[2][:, 1].min()) for q in quads if q[0].startswith("leg_"))
    if feet_low < -0.05 or feet_low > 0.05:
        raise SystemExit(f"the feet must stand on the ground and nothing below it, lowest point {feet_low:.2f} px")

    tripod_lo, tripod_hi = L.extent(quads, sub("tripod"))
    head_lo, head_hi = L.extent(quads, sub("turret") - sub("barrel"))
    gun_lo, gun_hi = L.extent(quads, sub("barrel"))
    parts = [("body", "tripod", tripod_lo, tripod_hi), ("turret", "head", head_lo, head_hi),
             ("turret", "gun, camera and ammunition", gun_lo, gun_hi)]
    obb = L.obb_rows(parts)
    obb[1]["follows"] = "turret"
    obb[2]["follows"] = "barrel"

    rig = {
        "model": f"jeg:entity/vehicle/{ID}", "units_per_block": 16, "front": "-Z in the model, +Z in vehicle data",
        "reference": "remote weapon station on a tripod (Kongsberg Protector / SGR-A1 class), 7.62 mm",
        "turret_origin": to_data((0, PAN_Y, 0)), "trunnion": to_data((0, TRUNNION_Y, 0)),
        "barrel_offset_from_turret": to_data((0, TRUNNION_Y - PAN_Y, 0)),
        "muzzle_rest": to_data((0, TRUNNION_Y, MUZZLE_Z)), "camera_eye": to_data(CAMERA_EYE),
        "elevation_degrees": list(ELEVATION), "model_bounds": [to_data(lo), to_data(hi)],
        "hitbox": obb, "bones": len(BONES), "cuboids": sum(len(b.get("cubes", [])) for b in L.bones_of(geo)),
    }
    L.write_json(HERE / "rig.json", rig)
    for e, yaw in ((0.0, 0.0), (35.0, 40.0)):
        sheet, _ = R.contact_sheet(geo, texture, f"Sentry, gun at {e:.0f} deg, head {yaw:.0f} deg",
                                   "design/sentry/generate.py", "barrel", pose(e, yaw))
        sheet.save(HERE / f"review-{int(e)}-{int(yaw)}.png", optimize=True)

    L.write_json(ASSETS / f"geckolib/models/entity/vehicle/{ID}.geo.json", geo)
    texture.save(ASSETS / f"textures/entity/vehicle/{ID}.png")
    glow.save(ASSETS / f"textures/entity/vehicle/{ID}_glow.png")
    L.write_json(ASSETS / f"geckolib/animations/entity/vehicle/{ID}.animation.json",
                 {"format_version": "1.8.0", "animations": {f"animation.{ID}.rest": {"loop": True, "bones": {}}}})
    # The assembling table's icon: the review render's three-quarter view, greyed onto a 256 x 64 strip.
    q, ts, _, _ = R.load_quads(geo, pose(10.0, 30.0))
    shot = R.render(R.visible(q, texture, ts), texture, ts, 35, 15, size=(400, 400)).convert("RGBA")
    background = shot.getpixel((2, 2))
    pixels = [(0, 0, 0, 0) if p[:3] == background[:3] else (*(min(235, 120 + int(.3 * p[0] + .59 * p[1] + .11 * p[2]) // 2),) * 3, 255)
              for p in shot.getdata()]
    shot.putdata(pixels)
    crop = shot.crop(shot.getbbox())
    crop.thumbnail((240, 56))
    icon = Image.new("RGBA", (256, 64), (0, 0, 0, 0))
    icon.paste(crop, ((256 - crop.width) // 2, 64 - crop.height - 4), crop)
    icon.save(ASSETS / f"textures/vehicle_icon/{ID}_icon.png")
    if VEHICLE_JSON.exists():
        data = VEHICLE_JSON.read_text(encoding="utf-8")
        start = data.index('  "obb": [')
        end = data.index("],", start) + 2
        VEHICLE_JSON.write_text(data[:start] + '  "obb": [\n' + ",\n".join(json_rows(obb)) + "\n  ]," + data[end:], encoding="utf-8")
    print(json.dumps({k: v for k, v in rig.items() if k != "hitbox"}))


if __name__ == "__main__":
    main()
