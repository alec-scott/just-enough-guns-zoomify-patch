"""Refit of Superb Warfare's Type-63 107 mm towed rocket launcher to the vehicle guide. The imported model's generator:
rerun it after any change.

python -I design/superbwarfare-port/fetch_sbw.py <sbw_dir> type_63 --extra sounds/type63/fire_1.ogg \
    sounds/type63/fire_2.ogg sounds/type63/fire_3.ogg sounds/type63/fire_4.ogg sounds/type63/rot1.ogg \
    sounds/type63/ty63_reload.ogg textures/item/medium_rocket_ap.png textures/item/medium_rocket_cm.png \
    textures/item/medium_rocket_he.png
python -I design/type63/refit.py <sbw_dir>

Writes the GeckoLib geometry, two LODs, texture, sounds and rocket icons into the mod, design/type63/rig.json, the OBB
rows into data/jeg/vehicles/type63.json, and review renders. Scale 0.78: SBW's launcher is about 1.3x the real Type 63
(2.6 m long, 1.1 m high).
"""
import collections
import json
import pathlib
import shutil
import sys

import numpy as np
from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "superbwarfare-port"))
import refit_lib as L  # noqa: E402
import render_geo as R  # noqa: E402

ID = "type63"
SBW_ID = "type_63"
SCALE = 0.78
ELEVATION_MIN, ELEVATION_MAX = -5.0, 60.0  # SBW's
TUBES = 12

NAMED = {
    "root": "root", "turret": "turret", "barrel": "barrel",
    "side": "launcher_side", "wakong": "launcher_cutout", "front": "launcher_front", "back": "launcher_back",
    "j1": "launcher_front_bracket_1", "j2": "launcher_front_bracket_2", "j3": "launcher_front_bracket_3",
    "rollx": "elevation_axle", "paojia": "carriage", "chilunxiang": "gearbox",
    "shoulunx": "handwheel_elevation", "shoulunpanx": "handwheel_elevation_rim",
    "shouluny": "handwheel_traverse", "shoulunpany": "handwheel_traverse_rim",
    "rolly": "traverse_ring", "dipan": "axle", "wheelL0": "wheel_left", "wheelR0": "wheel_right", "weijia": "trail",
    "tube": "tube_1", **{f"tube{i}": f"tube_{i}" for i in range(2, 13)},
    **{f"shell{i}": f"rocket_{i + 1}" for i in range(TUBES)},
}


def rename_map(geo):
    """Named bones by meaning; each rocket's body after its rocket; the generic ones after their parent, numbered."""
    bones = L.bones_of(geo)
    mapping = {}
    for b in bones:
        if b["name"] in NAMED:
            mapping[b["name"]] = NAMED[b["name"]]
    counters = collections.Counter()
    for b in bones:
        name = b["name"]
        if name in mapping:
            continue
        parent = mapping.get(b.get("parent"), b.get("parent"))
        if name.startswith("main"):
            mapping[name] = f"{parent}_body"
            continue
        counters[parent] += 1
        mapping[name] = f"{parent}_part_{counters[parent]}"
    return mapping


def pose(elevation):
    return {"barrel": (-elevation, 0, 0)}


def to_data(point_px):
    return [round(float(point_px[0]) / 16, 4), round(float(point_px[1]) / 16, 4), round(-float(point_px[2]) / 16, 4) + 0.0]


def data_bounds(lo_px, hi_px):
    a, b = to_data(lo_px), to_data(hi_px)
    return [[min(x, y) for x, y in zip(a, b)], [max(x, y) for x, y in zip(a, b)]]


def refit(raw, identifier):
    face = L.to_face_uv(raw)
    return L.scale(L.rename_bones(face, rename_map(face), identifier), SCALE)


def main():
    src = pathlib.Path(sys.argv[1])
    texture = Image.open(src / f"textures/entity/{SBW_ID}.png")
    texture.load()

    raw = L.load_json(src / f"geo/{SBW_ID}.geo.json")
    if not L.render_identical(raw, L.to_face_uv(raw), texture):
        raise SystemExit("per-face UV rewrite changed the rendering")
    found = len(L.coplanar_overlaps(L.to_face_uv(raw)))
    geo = L.resolve_zfighting(refit(raw, f"geometry.jeg.{ID}"))
    lods = {level: refit(L.load_json(src / f"geo/vehicle_lod/{SBW_ID}_lod{level}.geo.json"), f"geometry.jeg.{ID}_lod{level}")
            for level in (1, 2)}

    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, texture)
    sub = lambda name: L.subtree(by, name)

    # Each rocket leaves its tube at the pack's front face, on its own axis; SBW fires and empties them in rocket order.
    launcher_lo, launcher_hi = L.extent(quads, sub("barrel"))
    muzzles = []
    for i in range(1, TUBES + 1):
        lo, hi = L.extent(quads, sub(f"rocket_{i}"))
        muzzles.append(to_data([(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, launcher_lo[2]]))
    turret_lo, turret_hi = L.extent(quads, sub("turret") - sub("barrel"))
    turret_hi = turret_hi.copy()
    turret_hi[1] = min(turret_hi[1], launcher_lo[1])
    hull_names = sub("axle") | sub("trail") | sub("traverse_ring")
    hull_lo, hull_hi = L.extent(quads, hull_names)
    wl_lo, wl_hi = L.extent(quads, sub("wheel_left"))
    wr_lo, wr_hi = L.extent(quads, sub("wheel_right"))
    parts = [("body", "axle, trail and traverse ring", hull_lo, hull_hi),
             ("wheel_left", "left wheel", wl_lo, wl_hi), ("wheel_right", "right wheel", wr_lo, wr_hi),
             ("body", "carriage and handwheels", turret_lo, turret_hi),
             ("turret", "tube pack", launcher_lo, launcher_hi)]
    obb = L.obb_rows(parts)
    for row in obb:
        if row["covers"] == "carriage and handwheels":
            row["follows"] = "turret"
        elif row["covers"] == "tube pack":
            row["follows"] = "barrel"

    all_lo, all_hi = L.extent(quads)
    rig = {
        "model": f"jeg:entity/vehicle/{ID}",
        "source": "Superb Warfare type_63, CrawKatt/SuperbWarfareReFabricated@952d73c (used with the SBW team's permission)",
        "units_per_block": 16,
        "front": "-Z in the model, +Z in vehicle data",
        "scale_from_source": SCALE,
        "elevation_degrees": [ELEVATION_MIN, ELEVATION_MAX],
        "turret_origin": to_data(by["turret"]["pivot"]),
        "trunnion": to_data(by["barrel"]["pivot"]),
        "barrel_offset_from_turret": [round(a - b, 4) for a, b in zip(to_data(by["barrel"]["pivot"]), to_data(by["turret"]["pivot"]))],
        "muzzles_in_firing_order": muzzles,
        "handwheel_elevation_pivot": to_data(by["handwheel_elevation"]["pivot"]),
        "handwheel_traverse_pivot": to_data(by["handwheel_traverse"]["pivot"]),
        "wheel_left_pivot": to_data(by["wheel_left"]["pivot"]),
        "model_bounds": data_bounds(all_lo, all_hi),
        "hitbox": obb,
        "coplanar_face_pairs": {"in_source": found, "after_refit": len(L.coplanar_overlaps(geo))},
    }

    assets = L.ASSETS
    L.write_json(assets / f"geckolib/models/entity/vehicle/{ID}.geo.json", geo)
    for level, lod in lods.items():
        L.write_json(assets / f"geckolib/models/entity/vehicle/lod/{ID}_lod{level}.geo.json", lod)
    shutil.copyfile(src / f"textures/entity/{SBW_ID}.png", assets / f"textures/entity/vehicle/{ID}.png")
    # SBW ships no clips for the Type-63; ShellGunEntity still plays "<id>.rest", so write an empty one.
    L.write_json(assets / f"geckolib/animations/entity/vehicle/{ID}.animation.json",
                 {"format_version": "1.8.0", "animations": {f"animation.{ID}.rest": {"loop": True, "bones": {}}}})
    copies = [(f"sounds/type63/fire_{i}.ogg", f"sounds/vehicle/type63/fire_{i}.ogg") for i in (1, 2, 3, 4)]
    copies += [("sounds/type63/ty63_reload.ogg", "sounds/vehicle/type63/load.ogg"),
               ("sounds/type63/rot1.ogg", "sounds/vehicle/type63/handwheel.ogg")]
    copies += [(f"textures/item/medium_rocket_{k}.png", f"textures/item/rocket_107mm_{k}.png") for k in ("he", "ap", "cm")]
    for source, target in copies:
        (assets / target).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src / source, assets / target)
    (assets / "sounds/vehicle/type63/CREDITS.md").write_text(
        "# Type-63 sounds\n\n"
        "By the Superb Warfare development team, used with their permission\n"
        "(`design/superbwarfare-port/PERMISSION.md`). Copied unchanged by `design/type63/refit.py`:\n\n"
        "| File | SBW source |\n| --- | --- |\n"
        "| `fire_1.ogg` .. `fire_4.ogg` | `type63/fire_1` .. `4` |\n"
        "| `load.ogg` | `type63/ty63_reload` |\n"
        "| `handwheel.ogg` | `type63/rot1` |\n\n"
        "The distant report is the Mk42's (`vehicle/mk42/fire_distant`).\n", encoding="utf-8")
    # SBW has no icon for the Type-63: the vehicle creator's is drawn from the model.
    L.silhouette_icon(geo, texture, assets / f"textures/vehicle_icon/{ID}_icon.png")
    L.write_json(HERE / "rig.json", rig)

    data_path = L.ROOT / f"src/main/resources/data/jeg/vehicles/{ID}.json"
    text = data_path.read_text(encoding="utf-8")
    start = text.index('  "obb": [')
    end = text.index("],", start) + 2
    rows = []
    for row in obb:
        keys = ["part", "x", "y", "z", "half_width", "half_height", "half_depth"] + (["follows"] if "follows" in row else [])
        rows.append("    {" + ", ".join(f'"{k}": {json.dumps(row[k] if isinstance(row[k], str) else float(row[k]))}' for k in keys) + "}")
    newline = "\n"
    data_path.write_text(text[:start] + '  "obb": [' + newline + ("," + newline).join(rows) + newline + "  ],"
                         + text[end:], encoding="utf-8")

    for e in (0.0, 45.0):
        sheet, _ = R.contact_sheet(geo, texture, f"Type-63 refit, tubes at {e:.0f} deg (scale {SCALE})",
                                   "SBW type_63, refit by design/type63/refit.py", "barrel", pose(e))
        sheet.save(HERE / f"review-elevation-{int(e)}.png", optimize=True)

    print(json.dumps({k: rig[k] for k in ("turret_origin", "trunnion", "barrel_offset_from_turret", "muzzles_in_firing_order",
                                          "handwheel_elevation_pivot", "handwheel_traverse_pivot", "model_bounds",
                                          "coplanar_face_pairs")}))
    for row in obb:
        print(row)


if __name__ == "__main__":
    main()
