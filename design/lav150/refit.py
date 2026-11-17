"""Refit of Superb Warfare's LAV-150 (Cadillac Gage V-150 Commando) to the vehicle guide (fleet plan section 3). The
imported model's generator: rerun it after any change.

python -I design/superbwarfare-port/fetch_sbw.py <sbw_dir> lav_150
python -I design/lav150/refit.py <sbw_dir> [--dry]

Writes the GeckoLib geometry, two LODs and texture into the mod, design/lav150/rig.json, the OBB rows and the wheel block
into data/jeg/vehicles/lav150.json, and review renders. --dry writes only the renders and rig.json.

Scale 0.85, by length: SBW's hull is 6.73 blocks against the real 5.69 m. SBW built it fat as well (3.0 blocks wide
against 2.26 m), so at true length it stays about 0.3 blocks too wide; its shapes are kept rather than squeezed.
Wheels: the front pair steer and every wheel rolls, driven by the shared wheel driver from the data's "wheels" block.
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

ID = "lav150"
SBW_ID = "lav_150"
SCALE = 0.85
ELEVATION_MIN, ELEVATION_MAX = -15.0, 32.5  # the view-pitch limits the enemy AI uses, unchanged

NAMED = {
    "root": "root", "Wheel": "running_gear", "base": "hull", "main": "hull_main", "turret": "turret", "barrel": "barrel",
    "wheelL0Turn": "wheel_front_left", "wheelR0Turn": "wheel_front_right",
    "wheelL1": "wheel_rear_left", "wheelR1": "wheel_rear_right",
    "bone5": "fender_front_left", "bone6": "fender_front_right", "bone": "side_left", "bone2": "side_right",
    "bone12": "side_right_far", "bone3": "fender_rear_left", "bone4": "fender_rear_right",
    "bone7": "bow_left", "bone8": "bow_right", "bone9": "turret_ring", "bone11": "turret_hatch_left",
    "bone10": "turret_hatch_right", "barrel2": "cannon_barrel", "base2": "mantlet", "octagon": "mantlet_sight",
    "flare": "cannon_flash", "flare2": "coax_flash",
}
WHEELS = ["wheel_front_left", "wheel_front_right", "wheel_rear_left", "wheel_rear_right"]


def to_data(point_px):
    return [round(float(point_px[0]) / 16, 4), round(float(point_px[1]) / 16, 4), round(-float(point_px[2]) / 16, 4) + 0.0]


def data_bounds(lo_px, hi_px):
    a, b = to_data(lo_px), to_data(hi_px)
    return [[min(x, y) for x, y in zip(a, b)], [max(x, y) for x, y in zip(a, b)]]


def refit(raw, identifier):
    face = L.to_face_uv(raw)
    names = {b["name"] for b in L.bones_of(face)}
    missing = names - set(NAMED)
    if missing:
        raise SystemExit(f"bones without a name: {sorted(missing)}")
    return L.scale(L.rename_bones(face, {k: v for k, v in NAMED.items() if k in names}, identifier), SCALE)


def pose(elevation):
    return {"barrel": (-elevation, 0, 0)}


def json_rows(obb):
    rows = []
    for row in obb:
        keys = ["part", "x", "y", "z", "half_width", "half_height", "half_depth"] + (["follows"] if "follows" in row else [])
        rows.append("    {" + ", ".join(f'"{k}": {json.dumps(row[k] if isinstance(row[k], str) else float(row[k]))}' for k in keys) + "}")
    return rows


def front_centre(quads, names):
    lo, hi = L.extent(quads, names)
    return [(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, lo[2]]


def main():
    src = pathlib.Path(sys.argv[1])
    dry = "--dry" in sys.argv
    texture = Image.open(src / f"textures/entity/{SBW_ID}.png")
    texture.load()
    raw = L.load_json(src / f"geo/{SBW_ID}.geo.json")
    if not L.render_identical(raw, L.to_face_uv(raw), texture):
        raise SystemExit("per-face UV rewrite changed the rendering")
    found = len(L.coplanar_overlaps(L.to_face_uv(raw)))
    geo = L.resolve_zfighting(refit(raw, f"geometry.jeg.{ID}"))
    lods = {level: L.resolve_zfighting(refit(L.load_json(src / f"geo/vehicle_lod/{SBW_ID}_lod{level}.geo.json"), f"geometry.jeg.{ID}_lod{level}"))
            for level in (1, 2)}
    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, texture)
    sub = lambda name: L.subtree(by, name)

    # ---- Wheels: centre and rolling radius of each, from its drawn geometry.
    wheels = {}
    for name in WHEELS:
        lo, hi = L.extent(quads, sub(name))
        wheels[name] = {"centre": to_data([(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2]),
                        "radius_px": round(float(hi[1] - lo[1]) / 2, 3)}
    radius = round(float(np.mean([w["radius_px"] for w in wheels.values()])), 3)
    wheelbase = round(abs(wheels["wheel_front_left"]["centre"][2] - wheels["wheel_rear_left"]["centre"][2]), 4)
    wheel_block = {"roll": [{"bones": WHEELS, "radius": radius}], "steer": ["wheel_front_left", "wheel_front_right"],
                   "wheelbase": wheelbase}

    # ---- Guns.
    turret_pivot = np.array(by["turret"]["pivot"], float)
    trunnion = np.array(L.bone_pivot(geo, "barrel"), float)
    cannon_muzzle = front_centre(quads, sub("cannon_barrel"))
    # The coax's flash is transparent until it fires (no faces to measure): its bone's pivot sits at the muzzle.
    coax_muzzle = L.bone_pivot(geo, "coax_flash")

    # ---- Hitbox: the hull, each side's wheels, the engine at the rear (the V-150's), the turret and the gun.
    hull_names = sub("hull") - sub("turret")
    hull_lo, hull_hi = L.extent(quads, hull_names)
    hull_lo = hull_lo.copy()
    wl_lo, wl_hi = L.extent(quads, sub("wheel_front_left") | sub("wheel_rear_left"))
    wr_lo, wr_hi = L.extent(quads, sub("wheel_front_right") | sub("wheel_rear_right"))
    hull_lo[1] = max(hull_lo[1], 0.6 * float(wl_hi[1]))  # the belly sits up between the wheels
    engine_lo = np.array([hull_lo[0] * .7, hull_lo[1], hull_hi[2] - (hull_hi[2] - hull_lo[2]) * .3])
    engine_hi = np.array([hull_hi[0] * .7, hull_hi[1] - 2, hull_hi[2]])
    house_names = sub("turret") - sub("barrel")
    house_lo, house_hi = L.extent(quads, house_names)
    gun_lo, gun_hi = L.extent(quads, sub("cannon_barrel"))
    gun_hi = gun_hi.copy()
    gun_hi[2] = min(gun_hi[2], house_lo[2])
    parts = [("body", "hull", hull_lo, hull_hi), ("wheel_left", "left wheels", wl_lo, wl_hi),
             ("wheel_right", "right wheels", wr_lo, wr_hi), ("main_engine", "engine compartment", engine_lo, engine_hi),
             ("turret", "turret", house_lo, house_hi), ("turret", "cannon, outside the turret", gun_lo, gun_hi)]
    obb = L.obb_rows(parts)
    for row in obb:
        if row["covers"] == "turret":
            row["follows"] = "turret"
        elif row["covers"].startswith("cannon"):
            row["follows"] = "barrel"

    all_lo, all_hi = L.extent(quads)
    rig = {
        "model": f"jeg:entity/vehicle/{ID}",
        "source": "Superb Warfare lav_150, CrawKatt/SuperbWarfareReFabricated@952d73c (used with the SBW team's permission)",
        "units_per_block": 16, "front": "-Z in the model, +Z in vehicle data", "scale_from_source": SCALE,
        "elevation_degrees": [ELEVATION_MIN, ELEVATION_MAX],
        "turret_origin": to_data(turret_pivot), "trunnion": to_data(trunnion),
        "barrel_offset_from_turret": [round(a - b, 4) for a, b in zip(to_data(trunnion), to_data(turret_pivot))],
        "cannon_muzzle_rest": to_data(cannon_muzzle), "coax_muzzle_rest": to_data(coax_muzzle),
        "wheels": wheels, "wheel_block": wheel_block,
        "model_bounds": data_bounds(all_lo, all_hi), "hull_bounds": data_bounds(hull_lo, hull_hi),
        "turret_bounds": data_bounds(house_lo, house_hi), "hitbox": obb,
        "coplanar_face_pairs": {"in_source": found, "after_refit": len(L.coplanar_overlaps(geo))},
    }
    L.write_json(HERE / "rig.json", rig)
    for e in (0.0, 30.0):
        sheet, _ = R.contact_sheet(geo, texture, f"LAV-150 refit (scale {SCALE}), gun at {e:.0f} deg",
                                   "SBW lav_150, refit by design/lav150/refit.py", "barrel", pose(e))
        sheet.save(HERE / f"review-elevation-{int(e)}.png", optimize=True)
    print(json.dumps({k: rig[k] for k in ("turret_origin", "trunnion", "barrel_offset_from_turret", "cannon_muzzle_rest",
                                          "coax_muzzle_rest", "wheel_block", "model_bounds", "hull_bounds", "coplanar_face_pairs")}))
    for row in obb:
        print({k: (float(v) if not isinstance(v, str) else v) for k, v in row.items()})
    if dry:
        return

    assets = L.ASSETS
    L.write_json(assets / f"geckolib/models/entity/vehicle/{ID}.geo.json", geo)
    for level, lod in lods.items():
        L.write_json(assets / f"geckolib/models/entity/vehicle/lod/{ID}_lod{level}.geo.json", lod)
    shutil.copyfile(src / f"textures/entity/{SBW_ID}.png", assets / f"textures/entity/vehicle/{ID}.png")
    # SBW's idle clip only hid the muzzle flashes by their old names; the renderer now shows them while the gun fires,
    # so the clip the entity plays (VehicleEntity's idle map) is left empty.
    L.write_json(assets / f"geckolib/animations/entity/vehicle/{ID}.animation.json",
                 {"format_version": "1.8.0", "animations": {"animation.lav_150.idle": {"loop": True, "bones": {}}}})
    data_path = L.ROOT / f"src/main/resources/data/jeg/vehicles/{ID}.json"
    data = data_path.read_text(encoding="utf-8")
    start = data.index('  "obb": [')
    end = data.index("],", start) + 2
    data = data[:start] + '  "obb": [\n' + ",\n".join(json_rows(obb)) + "\n  ]," + data[end:]
    wheel_text = '  "wheels": ' + json.dumps(wheel_block) + ",\n"
    if '  "wheels": ' in data:
        start = data.index('  "wheels": ')
        end = data.index("\n", start) + 1
        data = data[:start] + wheel_text + data[end:]
    else:
        at = data.index('  "weapons": [')
        data = data[:at] + wheel_text + data[at:]
    data_path.write_text(data, encoding="utf-8")


if __name__ == "__main__":
    main()
