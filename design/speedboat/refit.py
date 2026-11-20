"""Refit of Superb Warfare's speedboat (a SURC-class riverine assault boat) to the vehicle guide (fleet plan section 4).
The imported model's generator: rerun it after any change.

python -I design/superbwarfare-port/fetch_sbw.py <sbw_dir> speedboat
python -I design/speedboat/refit.py <sbw_dir> [--dry]

Writes the GeckoLib geometry, its LOD and texture into the mod, design/speedboat/rig.json, the OBB rows into
data/jeg/vehicles/speedboat.json, and review renders. --dry writes only the renders and rig.json.

Scale 1.0: SBW's hull is 7.4 blocks long, a SURC's 7.6 m (it is 3.3 wide against 2.7 m; its shapes are kept).

The machine gun moves from the cabin roof (where its gunner would have stood on the roof) to a pintle on a pedestal at
the bow, as on a real riverine boat, so a gunner sitting on the foredeck behind it fires over it and the driver drives
from the cabin (fleet plan decision 2: the driver keeps the gun when nobody is at it). The pedestal post is coloured
from a palette corner added to SBW's texture where no face samples it.
"""
import collections
import copy
import json
import math
import pathlib
import shutil
import sys

import numpy as np
from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "superbwarfare-port"))
import refit_lib as L  # noqa: E402
import render_geo as R  # noqa: E402

ID = "speedboat"
SBW_ID = "speedboat"
SCALE = 1.0
ELEVATION_MIN, ELEVATION_MAX = -25.0, 50.0  # SBW's seat pitch limits
# Where the pintle stands (file px; the bow is -Z): far enough forward that the gunner behind the gun sits on the
# foredeck ahead of the windscreen, and high enough that a seated gunner's eye is just over the gun.
PINTLE_Z = -44.0
GUN_BORE_Y = 44.5

NAMED = {
    "root": "root", "waterMask": "water_mask", "rudder": "rudder",
    "turret": "turret", "bone15": "pintle_cradle", "barrel": "barrel", "flare": "mg_flash", "gun2": "mg",
    "qiangguan": "mg_barrel_group", "action": "mg_action", "bone50": "mg_action_front", "bone51": "mg_action_rear",
    "bone52": "mg_barrel_jacket", "bone53": "mg_barrel_part", "bone19": "mg_lod_body",
    "propeller": "propeller", "bone5": "propeller_blade_1", "bone6": "propeller_blade_2", "bone7": "propeller_blade_3",
    "bone8": "propeller_blade_4",
    "body": "hull", "glass": "windscreen", "bone14": "roof_plate", "bone10": "mast", "bone11": "beacon",
    "sit": "seats", "bone9": "passenger_bench", "up": "superstructure", "di": "hull_lower",
    "bone12": "rail_left", "bone13": "rail_right", "bone": "bow_left", "bone2": "bow_right",
    "bone3": "stern_left", "bone4": "stern_right",
}

PALETTE_ORIGIN = None  # found at run time: a fully transparent 16 x 4 strip no face samples
SWATCHES = {"gunmetal": (58, 60, 58), "dark": (34, 35, 34), "grey": (120, 124, 126), "steel": (96, 98, 94)}
FACES = ("north", "south", "east", "west", "up", "down")


def to_data(point_px):
    return [round(float(point_px[0]) / 16, 4), round(float(point_px[1]) / 16, 4), round(-float(point_px[2]) / 16, 4) + 0.0]


def data_bounds(lo_px, hi_px):
    a, b = to_data(lo_px), to_data(hi_px)
    return [[min(x, y) for x, y in zip(a, b)], [max(x, y) for x, y in zip(a, b)]]


def sampled_rects(geo):
    rects = []
    for b in L.bones_of(geo):
        for c in b.get("cubes", []):
            for f in c["uv"].values():
                (u, v), (w, h) = f["uv"], f["uv_size"]
                rects.append((min(u, u + w), min(v, v + h), max(u, u + w), max(v, v + h)))
    return rects


def find_palette(geo, texture, width=16, height=4):
    """A fully transparent corner no face samples, in the geometry's UV units (SBW's speedboat texture is drawn at 4x
    its declared 128 x 128)."""
    desc = geo["minecraft:geometry"][0]["description"]
    k = texture.size[0] / desc["texture_width"]
    alpha = np.asarray(texture.convert("RGBA"))[..., 3]
    rects = sampled_rects(geo)
    for y0 in range(desc["texture_height"] - height, -1, -height):
        for x0 in range(desc["texture_width"] - width, -1, -4):
            if (alpha[int(y0 * k):int((y0 + height) * k), int(x0 * k):int((x0 + width) * k)] > 0).any():
                continue
            if any(u1 < x0 + width and u2 > x0 and v1 < y0 + height and v2 > y0 for u1, v1, u2, v2 in rects):
                continue
            return x0, y0
    raise SystemExit("no free 16 x 4 corner in the texture for the palette")


def swatch_uv(name):
    i = list(SWATCHES).index(name)
    u, v = PALETTE_ORIGIN[0] + 4 * i + 1, PALETTE_ORIGIN[1] + 1
    return {face: {"uv": [u, v], "uv_size": [2, 2]} for face in FACES}


def box(lo, size, finish):
    return {"origin": [round(float(x), 4) for x in lo], "size": [round(float(x), 4) for x in size], "uv": swatch_uv(finish)}


def palette_texture(texture, k):
    out = texture.convert("RGBA").copy()
    x0, y0 = PALETTE_ORIGIN
    for i, colour in enumerate(SWATCHES.values()):
        for x in range(int(4 * k)):
            for y in range(int(4 * k)):
                out.putpixel((int((x0 + 4 * i) * k) + x, int(y0 * k) + y), colour + (255,))
    return out


def move_subtree(geo, root, delta):
    """Translate a bone and everything under it (pivots, cube origins and cube pivots) by delta (file px)."""
    geo = copy.deepcopy(geo)
    by = {b["name"]: b for b in L.bones_of(geo)}
    for name in L.subtree(by, root):
        b = by[name]
        if "pivot" in b:
            b["pivot"] = [round(p + d, 5) for p, d in zip(b["pivot"], delta)]
        for c in b.get("cubes", []):
            c["origin"] = [round(p + d, 5) for p, d in zip(c["origin"], delta)]
            if "pivot" in c:
                c["pivot"] = [round(p + d, 5) for p, d in zip(c["pivot"], delta)]
    return geo


def deck_top(quads, names, x, z):
    """The highest drawn point of the named bones over (x, z), file px."""
    best = None
    for name, _, pts, _ in quads:
        if name not in names:
            continue
        lo, hi = pts.min(0), pts.max(0)
        if lo[0] <= x <= hi[0] and lo[2] <= z <= hi[2]:
            best = hi[1] if best is None else max(best, hi[1])
    return best


def bow_gun(geo, texture):
    """Move the gun to the bow pintle and stand it on a pedestal post from the foredeck."""
    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, texture)
    turret_pivot = by["turret"]["pivot"]
    bore_y = L.bone_pivot(geo, "barrel")[1]
    delta = [0.0, GUN_BORE_Y - bore_y, PINTLE_Z - turret_pivot[2]]
    geo = move_subtree(geo, "turret", delta)
    hull = L.subtree(by, "hull") - L.subtree(by, "windscreen") - L.subtree(by, "roof_plate")
    deck = deck_top(quads, hull, 0.0, PINTLE_Z)
    by = {b["name"]: b for b in L.bones_of(geo)}
    q2, _ = L.posed_quads(geo, None, texture)
    cradle_lo, _ = L.extent(q2, L.subtree(by, "pintle_cradle"))
    post_top = float(cradle_lo[1])
    post = {"name": "pintle_post", "parent": "hull", "pivot": [0.0, deck, PINTLE_Z], "cubes": [
        box((-3.0, deck, PINTLE_Z - 3.0), (6.0, 1.0, 6.0), "steel"),                      # deck plate
        box((-1.4, deck + 1.0, PINTLE_Z - 1.4), (2.8, post_top - deck - 1.0, 2.8), "gunmetal"),  # post
        box((-2.2, post_top - 1.2, PINTLE_Z - 2.2), (4.4, 1.2, 4.4), "dark"),              # bearing collar
    ]}
    L.bones_of(geo).append(post)
    return geo, delta, deck


def refit(raw, texture, identifier):
    face = L.to_face_uv(raw)
    names = {b["name"] for b in L.bones_of(face)}
    missing = names - set(NAMED)
    if missing:
        raise SystemExit(f"bones without a name: {sorted(missing)}")
    stripped, dropped = L.strip_transparent(face, texture)
    geo = L.scale(L.rename_bones(stripped, {k: v for k, v in NAMED.items() if k in names}, identifier), SCALE)
    geo, delta, deck = bow_gun(geo, texture)
    return L.resolve_zfighting(geo), dropped, delta, deck


def pose(elevation, yaw=0.0):
    return {"turret": (0, yaw, 0), "barrel": (-elevation, 0, 0)}


def json_rows(obb):
    rows = []
    for row in obb:
        keys = ["part", "x", "y", "z", "half_width", "half_height", "half_depth"] + (["follows"] if "follows" in row else [])
        rows.append("    {" + ", ".join(f'"{k}": {json.dumps(row[k] if isinstance(row[k], str) else float(row[k]))}' for k in keys) + "}")
    return rows


def main():
    global PALETTE_ORIGIN
    src = pathlib.Path(sys.argv[1])
    dry = "--dry" in sys.argv
    texture = Image.open(src / f"textures/entity/{SBW_ID}.png")
    texture.load()
    raw = L.load_json(src / f"geo/{SBW_ID}.geo.json")
    face = L.to_face_uv(raw)
    if not L.render_identical(raw, face, texture):
        raise SystemExit("per-face UV rewrite changed the rendering")
    if not L.renders_alike(face, L.strip_transparent(face, texture)[0], texture):
        raise SystemExit("dropping the transparent faces changed the rendering")
    found = len(L.coplanar_overlaps(face))
    lod_raw = L.load_json(src / f"geo/vehicle_lod/{SBW_ID}_lod1.geo.json")
    PALETTE_ORIGIN = find_palette(L.to_face_uv(raw), texture)
    x0, y0 = PALETTE_ORIGIN
    if any(u1 < x0 + 16 and u2 > x0 and v1 < y0 + 4 and v2 > y0 for u1, v1, u2, v2 in sampled_rects(L.to_face_uv(lod_raw))):
        raise SystemExit("the LOD samples the palette corner")
    tex = palette_texture(texture, texture.size[0] / raw["minecraft:geometry"][0]["description"]["texture_width"])
    geo, dropped, delta, deck = refit(raw, tex, f"geometry.jeg.{ID}")
    lod, _, _, _ = refit(lod_raw, tex, f"geometry.jeg.{ID}_lod1")
    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, tex)
    sub = lambda name: L.subtree(by, name)

    # ---- The gun: trunnion, muzzle, and how far it may traverse before it points into the cabin.
    turret_pivot = np.array(by["turret"]["pivot"], float)
    trunnion = np.array(L.bone_pivot(geo, "barrel"), float)
    mg_lo, mg_hi = L.extent(quads, sub("barrel"))
    muzzle = [float(trunnion[0]), float(trunnion[1]), float(mg_lo[2])]
    grips_aft = float(mg_hi[2])
    # The cabin: the windscreen's footprint, from the deck up to the highest part of the superstructure over it (SBW's
    # "up" bone also carries the whole deck, so its extent alone would be the hull).
    glass_lo, glass_hi = L.extent(quads, sub("windscreen"))
    cabin_top = max(float(pts[:, 1].max()) for name, _, pts, _ in quads
                    if name in sub("superstructure") | sub("windscreen") | sub("roof_plate")
                    and pts[:, 0].min() >= glass_lo[0] - 1 and pts[:, 0].max() <= glass_hi[0] + 1
                    and pts[:, 2].min() >= glass_lo[2] - 1 and pts[:, 2].max() <= glass_hi[2] + 1)
    cabin_deck = deck_top(quads, sub("hull_lower"), 0.0, float(glass_lo[2] + glass_hi[2]) / 2)
    cabin_lo = np.array([glass_lo[0], cabin_deck, glass_lo[2]])
    cabin_hi = np.array([glass_hi[0], cabin_top, glass_hi[2]])
    # The cabin's front corners from the pintle: aft of them the bore line crosses the cabin.
    dz = float(cabin_lo[2] - turret_pivot[2])
    corner = math.degrees(math.atan2(float(cabin_hi[0]), dz))
    traverse = math.floor(min(150.0, 180.0 - corner) - 10.0)

    # ---- Seats: the gunner on the foredeck behind the gun; the driver at the helm.
    gunner_z = grips_aft + 4.0
    gunner_deck = deck_top(quads, sub("hull") - sub("windscreen") - sub("pintle_post"), 0.0, gunner_z)

    # ---- Hitbox: the hull below the gunwale, the cabin, the engine at the stern, the gun (turning with it).
    lower_lo, lower_hi = L.extent(quads, sub("hull_lower") | sub("bow_left") | sub("bow_right") | sub("stern_left")
                                  | sub("stern_right") | sub("rail_left") | sub("rail_right"))
    stern_lo, stern_hi = L.extent(quads, sub("stern_left") | sub("stern_right"))
    engine_lo = np.array([-14.0, max(0.0, float(lower_lo[1])), float(stern_lo[2])])
    engine_hi = np.array([14.0, 14.0, float(stern_hi[2])])
    gun_lo, gun_hi = L.extent(quads, sub("turret"))
    parts = [("body", "hull", lower_lo, lower_hi), ("body", "cabin", cabin_lo, cabin_hi),
             ("main_engine", "engine under the stern deck", engine_lo, engine_hi),
             ("turret", "bow gun", gun_lo, gun_hi)]
    obb = L.obb_rows(parts)
    for row in obb:
        if row["covers"] == "bow gun":
            row["follows"] = "turret"

    all_lo, all_hi = L.extent(quads)
    rig = {
        "model": f"jeg:entity/vehicle/{ID}",
        "source": "Superb Warfare speedboat, CrawKatt/SuperbWarfareReFabricated@952d73c (used with the SBW team's permission)",
        "units_per_block": 16,
        "front": "-Z in the model, +Z in vehicle data",
        "scale_from_source": SCALE,
        "gun_moved_px": delta,
        "foredeck_y": round(deck / 16, 4),
        "elevation_degrees": [ELEVATION_MIN, ELEVATION_MAX],
        "traverse_degrees_either_side": traverse,
        "turret_origin": to_data(turret_pivot),
        "trunnion": to_data(trunnion),
        "barrel_offset_from_turret": [round(a - b, 4) for a, b in zip(to_data(trunnion), to_data(turret_pivot))],
        "muzzle_rest": to_data(muzzle),
        "gunner_seat_deck": to_data([0.0, gunner_deck, gunner_z]),
        "propeller_axis": to_data(by["propeller"]["pivot"]),
        "rudder_pivot": to_data(by["rudder"]["pivot"]),
        "model_bounds": data_bounds(all_lo, all_hi),
        "hull_bounds": data_bounds(lower_lo, lower_hi),
        "cabin_bounds": data_bounds(cabin_lo, cabin_hi),
        "hitbox": obb,
        "transparent_faces_dropped": dropped,
        "palette_origin": list(PALETTE_ORIGIN),
        "coplanar_face_pairs": {"in_source": found, "after_refit": len(L.coplanar_overlaps(geo))},
    }
    L.write_json(HERE / "rig.json", rig)

    for e, yaw in ((0.0, 0.0), (20.0, 60.0)):
        sheet, _ = R.contact_sheet(geo, tex, f"Speedboat refit, bow gun at {e:.0f} deg, {yaw:.0f} deg off the bow",
                                   "SBW speedboat, refit by design/speedboat/refit.py; gun moved to a bow pintle", "barrel",
                                   pose(e, yaw))
        sheet.save(HERE / f"review-gun-{int(e)}-{int(yaw)}.png", optimize=True)
    q, ts, _, _ = R.load_quads(geo, pose(5.0, 30.0))
    R.render(R.visible(q, tex, ts), tex, ts, 30, 20, size=(1400, 1000)).save(HERE / "review-bow.png", optimize=True)
    print(json.dumps({k: v for k, v in rig.items() if k != "hitbox"}))
    for row in obb:
        print(row)
    if dry:
        return

    assets = L.ASSETS
    L.write_json(assets / f"geckolib/models/entity/vehicle/{ID}.geo.json", geo)
    L.write_json(assets / f"geckolib/models/entity/vehicle/lod/{ID}_lod1.geo.json", lod)
    tex.save(assets / f"textures/entity/vehicle/{ID}.png")
    L.write_json(assets / f"geckolib/animations/entity/vehicle/{ID}.animation.json",
                 {"format_version": "1.8.0", "animations": {f"animation.{ID}.idle": {"loop": True, "bones": {}}}})
    data_path = L.ROOT / f"src/main/resources/data/jeg/vehicles/{ID}.json"
    data = data_path.read_text(encoding="utf-8")
    start = data.index('  "obb": [')
    end = data.index("],", start) + 2
    data_path.write_text(data[:start] + '  "obb": [\n' + ",\n".join(json_rows(obb)) + "\n  ]," + data[end:], encoding="utf-8")


if __name__ == "__main__":
    main()
