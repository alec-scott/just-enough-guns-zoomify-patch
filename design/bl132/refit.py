"""Refit of Superb Warfare's BL-132 130 mm/58 four-gun mount to the vehicle guide. The imported model's generator:
rerun it after any change.

python -I design/superbwarfare-port/fetch_sbw.py <sbw_dir> bl_132 --extra \
    sounds/bl_132/bl_132_fire_1p_01.ogg sounds/bl_132/bl_132_fire_1p_02.ogg sounds/bl_132/bl_132_fire_1p_03.ogg \
    sounds/bl_132/bl_132_fire_3p_01.ogg sounds/bl_132/bl_132_fire_3p_02.ogg sounds/bl_132/bl_132_fire_3p_03.ogg \
    sounds/bl_132/bl_132_reload_01.ogg sounds/bl_132/bl_132_reload_02.ogg sounds/bl_132/bl_132_reload_03.ogg
python -I design/bl132/refit.py <sbw_dir>

Writes the GeckoLib geometry, texture, clips and sounds into the mod, design/bl132/rig.json, the OBB rows into
data/jeg/vehicles/bl132.json, and review renders. Scale 1.0: each tube measures 7.54 blocks from the trunnions to the
muzzle, the real 130 mm/58 bore (triage's ~0.82 measured the breech behind the trunnions too). The Mk42 is held to
the same yardstick.
"""
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

ID = "bl132"
SBW_ID = "bl_132"
ELEVATION_MIN, ELEVATION_MAX = -5.0, 85.0  # SBW's

# Guns numbered as the gunner sees them: 1 upper left, 2 lower left, 3 upper right, 4 lower right (+X is left).
RENAME = {
    "root": "root",
    "dipan": "base", "dizuo1": "base_ring",
    "turret": "turret",
    "barrel": "barrel",                 # elevation joint (trunnions), driven like every jegn barrel
    "xroll": "cradle",
    "tube1": "gun_1", "bone5": "gun_1_jacket", "bone4": "gun_1_barrel", "action1": "gun_1_slide", "bone7": "gun_1_muzzle",
    "tube2": "gun_2", "bone8": "gun_2_jacket", "bone9": "gun_2_barrel", "action3": "gun_2_slide", "bone11": "gun_2_muzzle",
    "tube3": "gun_3", "bone12": "gun_3_jacket", "bone13": "gun_3_barrel", "action2": "gun_3_slide", "bone15": "gun_3_muzzle",
    "tube4": "gun_4", "bone16": "gun_4_jacket", "bone17": "gun_4_barrel", "action4": "gun_4_slide", "bone19": "gun_4_muzzle",
    "front": "house_front", "back": "house_back", "top": "roof", "tleft": "roof_left", "tright": "roof_right",
    "bottom": "house_floor", "left": "house_left", "lside": "house_left_cheek", "right": "house_right",
    "lside2": "house_right_cheek",
    "qianguanmiao": "sight_tower",       # the director's sight tower between the guns
    "bone1": "sight_tower_base", "bone2": "sight_tower_top", "bone3": "sight_tower_middle",
    "qiuchuang3": "sight_tower_window",
    "paobian": "gun_ports", "ldiban": "gun_port_floor_left", "rdiban": "gun_port_floor_right",
    "others": "fittings", "f": "fittings_front",
    "lview": "sight_left", "lview2": "sight_left_hood", "rview": "sight_right", "rview2": "sight_right_hood",
    "fpingtai": "front_platform",
    "tizi": "ladders",
    **{f"tz{i}": f"ladder_rung_{i}" for i in range(1, 18)},
}
CLIPS = {"animation.bl_132.idle": "animation.bl132.rest"}

# Firing order (SBW's ShootPos order: lower pair, then upper pair, alternating sides) and each gun's recoiling slide.
GUNS = [("gun_2", "gun_2_slide"), ("gun_4", "gun_4_slide"), ("gun_1", "gun_1_slide"), ("gun_3", "gun_3_slide")]
HOUSE = ["house_front", "house_back", "roof", "house_floor", "house_left", "house_right", "gun_ports", "fittings_front",
         "sight_left", "sight_right", "front_platform"]


def pose(elevation):
    # GeckoLib hands models the negated head pitch: a raised gun is a negative file X rotation (as the mortar).
    return {"barrel": (-elevation, 0, 0)}


def to_data(point_px):
    """File pixels (front -Z) to vehicle-data blocks (+Z forward)."""
    return [round(float(point_px[0]) / 16, 4), round(float(point_px[1]) / 16, 4), round(-float(point_px[2]) / 16, 4) + 0.0]


def data_bounds(lo_px, hi_px):
    a, b = to_data(lo_px), to_data(hi_px)
    return [[min(x, y) for x, y in zip(a, b)], [max(x, y) for x, y in zip(a, b)]]


def main():
    src = pathlib.Path(sys.argv[1])
    texture = Image.open(src / f"textures/entity/{SBW_ID}.png")
    texture.load()

    raw = L.load_json(src / f"geo/{SBW_ID}.geo.json")
    face = L.to_face_uv(raw)
    if not L.render_identical(raw, face, texture):
        raise SystemExit("per-face UV rewrite changed the rendering")
    found = len(L.coplanar_overlaps(face))
    geo = L.resolve_zfighting(L.rename_bones(face, RENAME, f"geometry.jeg.{ID}"))
    clips = L.rename_animations(L.load_json(src / f"animations/{SBW_ID}.animation.json"), RENAME, CLIPS)

    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, texture)
    sub = lambda name: L.subtree(by, name)

    turret_pivot = np.array(by["turret"]["pivot"], float)
    trunnion = np.array(by["barrel"]["pivot"], float)
    muzzles = []
    for gun, slide in GUNS:
        lo, hi = L.extent(quads, sub(gun))
        muzzles.append({"gun": gun, "slide": slide,
                        "muzzle_rest": to_data([(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, lo[2]]),
                        "bore_from_trunnion_blocks": round(float(trunnion[2] - lo[2]) / 16, 3)})

    # Hitbox: the base is fixed; the house and sight tower turn with the turret; the four guns, outside the house,
    # turn with it and elevate with the barrel.
    house_names = set().union(*(sub(n) for n in HOUSE))
    house_lo, house_hi = L.extent(quads, house_names)
    base_lo, base_hi = L.extent(quads, sub("base"))
    tower_lo, tower_hi = L.extent(quads, sub("sight_tower"))
    tower_lo = tower_lo.copy()
    tower_lo[1] = max(tower_lo[1], house_hi[1])
    guns_lo, guns_hi = L.extent(quads, set().union(*(sub(g) for g, _ in GUNS)))
    guns_hi = guns_hi.copy()
    guns_hi[2] = min(guns_hi[2], house_lo[2])
    parts = [("body", "base ring", base_lo, base_hi), ("body", "gunhouse", house_lo, house_hi)]
    if tower_hi[1] > tower_lo[1]:
        parts.append(("body", "sight tower", tower_lo, tower_hi))
    parts.append(("turret", "guns, outside the house", guns_lo, guns_hi))
    obb = L.obb_rows(parts)
    for row in obb:
        if row["covers"] != "base ring":
            row["follows"] = "barrel" if row["covers"].startswith("guns") else "turret"

    all_lo, all_hi = L.extent(quads)
    overlaps = L.coplanar_overlaps(geo)
    rig = {
        "model": f"jeg:entity/vehicle/{ID}",
        "source": "Superb Warfare bl_132, CrawKatt/SuperbWarfareReFabricated@952d73c (used with the SBW team's permission)",
        "units_per_block": 16,
        "front": "-Z in the model, +Z in vehicle data",
        "scale_from_source": 1.0,
        "elevation_degrees": [ELEVATION_MIN, ELEVATION_MAX],
        "elevation_sign": "barrel bone file X rotation = -elevation (GeckoLib setRotX(+elevation))",
        "turret_origin": to_data(turret_pivot),
        "trunnion": to_data(trunnion),
        "barrel_offset_from_turret": [round(a - b, 4) for a, b in zip(to_data(trunnion), to_data(turret_pivot))],
        "guns_in_firing_order": muzzles,
        "recoil_pixels": 11.0,
        "model_bounds": data_bounds(all_lo, all_hi),
        "gunhouse_bounds": data_bounds(house_lo, house_hi),
        "hitbox": obb,
        "hitbox_note": "the ladders on the house sides (0.15 blocks proud) are left out of the boxes",
        "coplanar_face_pairs": {"in_source": found, "after_refit": len(overlaps)},
    }

    assets = L.ASSETS
    L.write_json(assets / f"geckolib/models/entity/vehicle/{ID}.geo.json", geo)
    L.write_json(assets / f"geckolib/animations/entity/vehicle/{ID}.animation.json", clips)
    shutil.copyfile(src / f"textures/entity/{SBW_ID}.png", assets / f"textures/entity/vehicle/{ID}.png")
    copies = []
    for i in (1, 2, 3):
        copies += [(f"sounds/bl_132/bl_132_fire_3p_0{i}.ogg", f"sounds/vehicle/bl132/fire_{i}.ogg"),
                   (f"sounds/bl_132/bl_132_fire_1p_0{i}.ogg", f"sounds/vehicle/bl132/fire_interior_{i}.ogg"),
                   (f"sounds/bl_132/bl_132_reload_0{i}.ogg", f"sounds/vehicle/bl132/load_{i}.ogg")]
    for source, target in copies:
        (assets / target).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src / source, assets / target)
    (assets / "sounds/vehicle/bl132/CREDITS.md").write_text(
        "# BL-132 sounds\n\n"
        "By the Superb Warfare development team, used with their permission\n"
        "(`design/superbwarfare-port/PERMISSION.md`). Copied unchanged by `design/bl132/refit.py`:\n\n"
        "| File | SBW source |\n| --- | --- |\n"
        "| `fire_1.ogg` .. `fire_3.ogg` | `bl_132/bl_132_fire_3p_01` .. `03` |\n"
        "| `fire_interior_1.ogg` .. `fire_interior_3.ogg` | `bl_132/bl_132_fire_1p_01` .. `03` |\n"
        "| `load_1.ogg` .. `load_3.ogg` | `bl_132/bl_132_reload_01` .. `03` |\n\n"
        "The distant report is the Mk42's (`vehicle/mk42/fire_distant`), as in SBW.\n", encoding="utf-8")
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

    for e in (0.0, 45.0, ELEVATION_MAX):
        sheet, _ = R.contact_sheet(geo, texture, f"BL-132 refit, guns at {e:.0f} deg", "SBW bl_132, refit by design/bl132/refit.py",
                                   "barrel", pose(e))
        sheet.save(HERE / f"review-elevation-{int(e)}.png", optimize=True)

    print(json.dumps({k: rig[k] for k in ("turret_origin", "trunnion", "barrel_offset_from_turret", "guns_in_firing_order",
                                          "model_bounds", "gunhouse_bounds", "coplanar_face_pairs")}))
    for row in obb:
        print(row)


if __name__ == "__main__":
    main()
