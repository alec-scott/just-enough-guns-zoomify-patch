"""Refit of Superb Warfare's HPJ-11 close-in weapon system (an eleven-barrel 30 mm Gatling, the real H/PJ-11 / Type 1130)
to the vehicle guide (fleet plan section 5). The imported model's generator: rerun it after any change.

python -I design/superbwarfare-port/fetch_sbw.py <sbw_dir> hpj_11
python -I design/hpj11/refit.py <sbw_dir> [--dry]

Writes the GeckoLib geometry and texture into the mod, design/hpj11/rig.json, the OBB rows into
data/jeg/vehicles/hpj11.json, and review renders. --dry writes only the renders and rig.json.

Scale 1.0: SBW's mount is 3.6 blocks wide and 3.7 high with its radars, about a Type 1130's size. Every bone is named:
the barrel cluster (`barrel_cluster`) spins about the bore, the search radar (`search_radar`, the flat panel on the
left) turns all the time, the tracking radar (`tracking_radar`, the round head on the right) follows the target.
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

ID = "hpj11"
SBW_ID = "hpj_11"
SCALE = 1.0
ELEVATION_MIN, ELEVATION_MAX = -25.0, 85.0  # the Type 1130's

NAMED = {
    "root": "root", "dizuo": "base", "dizuoroll": "base_ring", "turret": "turret", "guding": "mount",
    "main": "mount_front",
    # File +X is the mount's left.
    "rightmain": "side_left", "yuanke": "side_left_drum", "houpingtai": "side_left_rear_deck",
    "radar": "search_radar_mount", "rdr": "search_radar", "radarbase1": "search_radar_base",
    "radarbase2": "search_radar_base_upper",
    "leftmain": "side_right", "yuanke2": "side_right_drum", "houpingtai2": "side_right_rear_deck",
    "radarbase3": "tracking_radar_base", "radarbase4": "tracking_radar_base_middle", "radarbase5": "tracking_radar_base_upper",
    "radar2": "tracking_radar_mount", "rdr2": "tracking_radar",
    "zhou": "trunnions", "zhou1": "trunnion_left", "zhou2": "trunnion_right",
    "barrel": "barrel", "flare": "cannon_flash", "paoguanwaike": "barrel_shroud", "paoshen": "gun_body",
    "zhou11": "barrel_trunnion_left", "zhou22": "barrel_trunnion_right",
    "paoguanroll": "barrel_cluster", "tube": "barrels",
}


def rename_map(geo):
    """Named bones by meaning; the generic ones after their parent, numbered."""
    bones = L.bones_of(geo)
    mapping = {b["name"]: NAMED[b["name"]] for b in bones if b["name"] in NAMED}
    counters = collections.Counter()
    for b in bones:
        name = b["name"]
        if name in mapping:
            continue
        parent = mapping.get(b.get("parent"), b.get("parent"))
        counters[parent] += 1
        mapping[name] = f"{parent}_part_{counters[parent]}"
    return mapping


def to_data(point_px):
    return [round(float(point_px[0]) / 16, 4), round(float(point_px[1]) / 16, 4), round(-float(point_px[2]) / 16, 4) + 0.0]


def data_bounds(lo_px, hi_px):
    a, b = to_data(lo_px), to_data(hi_px)
    return [[min(x, y) for x, y in zip(a, b)], [max(x, y) for x, y in zip(a, b)]]


def pose(elevation, yaw=0.0, radar=0.0):
    return {"turret": (0, yaw, 0), "barrel": (-elevation, 0, 0), "search_radar": (0, radar, 0)}


def json_rows(obb):
    rows = []
    for row in obb:
        keys = ["part", "x", "y", "z", "half_width", "half_height", "half_depth"] + (["follows"] if "follows" in row else [])
        rows.append("    {" + ", ".join(f'"{k}": {json.dumps(row[k] if isinstance(row[k], str) else float(row[k]))}' for k in keys) + "}")
    return rows


def main():
    src = pathlib.Path(sys.argv[1])
    dry = "--dry" in sys.argv
    texture = Image.open(src / f"textures/entity/{SBW_ID}.png")
    texture.load()
    raw = L.load_json(src / f"geo/{SBW_ID}.geo.json")
    face = L.to_face_uv(raw)
    if not L.render_identical(raw, face, texture):
        raise SystemExit("per-face UV rewrite changed the rendering")
    stripped, dropped = L.strip_transparent(face, texture)
    if not L.renders_alike(face, stripped, texture):
        raise SystemExit("dropping the transparent faces changed the rendering")
    found = len(L.coplanar_overlaps(face))
    mapping = rename_map(face)
    geo = L.resolve_zfighting(L.scale(L.rename_bones(stripped, mapping, f"geometry.jeg.{ID}"), SCALE))
    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, texture)
    sub = lambda name: L.subtree(by, name)

    turret_pivot = np.array(by["turret"]["pivot"], float)
    trunnion = np.array(L.bone_pivot(geo, "barrel"), float)
    spin_axis = np.array(L.bone_pivot(geo, "barrel_cluster"), float)
    gun_lo, gun_hi = L.extent(quads, sub("barrel"))
    muzzle = [float(spin_axis[0]), float(spin_axis[1]), float(gun_lo[2])]
    tracker = np.array(L.bone_pivot(geo, "tracking_radar"), float)

    # Hitbox: the base, the mount and its two side housings with the radars (turning with the turret), and the gun
    # (elevating with the barrel) outside the mount.
    base_lo, base_hi = L.extent(quads, sub("base"))
    house_names = sub("turret") - sub("barrel")
    house_lo, house_hi = L.extent(quads, house_names - sub("search_radar_mount") - sub("tracking_radar_mount")
                                  - sub("search_radar_base") - sub("search_radar_base_upper") - sub("tracking_radar_base")
                                  - sub("tracking_radar_base_middle") - sub("tracking_radar_base_upper"))
    search_lo, search_hi = L.extent(quads, sub("search_radar_mount") | sub("search_radar_base") | sub("search_radar_base_upper"))
    track_lo, track_hi = L.extent(quads, sub("tracking_radar_mount") | sub("tracking_radar_base") | sub("tracking_radar_base_middle")
                                  | sub("tracking_radar_base_upper"))
    barrel_lo, barrel_hi = L.extent(quads, sub("barrel"))
    barrel_hi = barrel_hi.copy()
    barrel_hi[2] = min(barrel_hi[2], house_lo[2])
    parts = [("body", "base", base_lo, base_hi), ("turret", "mount", house_lo, house_hi),
             ("turret", "search radar", search_lo, search_hi), ("turret", "tracking radar", track_lo, track_hi),
             ("turret", "gun, outside the mount", barrel_lo, barrel_hi)]
    obb = L.obb_rows(parts)
    for row in obb:
        row["follows"] = "barrel" if row["covers"].startswith("gun") else ("turret" if row["part"] == "turret" else None)
        if row["follows"] is None:
            del row["follows"]

    all_lo, all_hi = L.extent(quads)
    rig = {
        "model": f"jeg:entity/vehicle/{ID}",
        "source": "Superb Warfare hpj_11, CrawKatt/SuperbWarfareReFabricated@952d73c (used with the SBW team's permission)",
        "units_per_block": 16,
        "front": "-Z in the model, +Z in vehicle data",
        "scale_from_source": SCALE,
        "elevation_degrees": [ELEVATION_MIN, ELEVATION_MAX],
        "turret_origin": to_data(turret_pivot),
        "trunnion": to_data(trunnion),
        "barrel_offset_from_turret": [round(a - b, 4) for a, b in zip(to_data(trunnion), to_data(turret_pivot))],
        "barrel_spin_axis": to_data(spin_axis),
        "muzzle_rest": to_data(muzzle),
        "tracking_radar_pivot": to_data(tracker),
        "model_bounds": data_bounds(all_lo, all_hi),
        "hitbox": obb,
        "transparent_faces_dropped": dropped,
        "coplanar_face_pairs": {"in_source": found, "after_refit": len(L.coplanar_overlaps(geo))},
    }
    L.write_json(HERE / "rig.json", rig)
    for e in (0.0, 45.0):
        sheet, _ = R.contact_sheet(geo, texture, f"HPJ-11 refit, gun at {e:.0f} deg", "SBW hpj_11, refit by design/hpj11/refit.py",
                                   "barrel", pose(e))
        sheet.save(HERE / f"review-elevation-{int(e)}.png", optimize=True)
    print(json.dumps({k: v for k, v in rig.items() if k != "hitbox"}))
    for row in obb:
        print(row)
    if dry:
        return

    assets = L.ASSETS
    L.write_json(assets / f"geckolib/models/entity/vehicle/{ID}.geo.json", geo)
    texture.save(assets / f"textures/entity/vehicle/{ID}.png")
    L.write_json(assets / f"geckolib/animations/entity/vehicle/{ID}.animation.json",
                 {"format_version": "1.8.0", "animations": {f"animation.{ID}.rest": {"loop": True, "bones": {}}}})
    data_path = L.ROOT / f"src/main/resources/data/jeg/vehicles/{ID}.json"
    data = data_path.read_text(encoding="utf-8")
    start = data.index('  "obb": [')
    end = data.index("],", start) + 2
    data_path.write_text(data[:start] + '  "obb": [\n' + ",\n".join(json_rows(obb)) + "\n  ]," + data[end:], encoding="utf-8")


if __name__ == "__main__":
    main()
