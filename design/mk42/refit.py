"""Refit of Superb Warfare's Mk42 5"/54 gun mount to the vehicle guide. The imported model's generator: rerun it after
any change.

python -I design/superbwarfare-port/fetch_sbw.py <sbw_dir> mk_42 --extra sounds/mk_42/mk_42_fire_1p_1.ogg \
    sounds/mk_42/mk_42_fire_3p.ogg sounds/mk_42/mk_42_far.ogg sounds/cannon/cannon_reload_01.ogg \
    sounds/cannon/cannon_reload_02.ogg sounds/cannon/cannon_reload_03.ogg \
    textures/item/shells/ap_5_inches.png textures/item/shells/he_5_inches.png \n    textures/item/shells/cm_5_inches.png textures/item/shells/gs_5_inches.png
python -I design/mk42/refit.py <sbw_dir>

Writes the GeckoLib geometry, two LODs, texture, clips, item icons and sounds into the mod, design/mk42/rig.json, the
OBB rows for data/jeg/vehicles/mk42.json, and review renders. The model is kept at SBW's size: its 7.4-block gun
(breech included) is 1.08x the real 6.86 m bore, which is what a 1:1 gun with its breech inside the house measures.
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

ID = "mk42"
SBW_ID = "mk_42"
ELEVATION_MIN, ELEVATION_MAX = -15.0, 85.0  # the real Mk42's limits, and SBW's

# Pinyin and generic names to what each part is. +X is the gunner's left, -Z is forward.
RENAME = {
    "root": "root",
    "dipan": "base",                    # fixed foundation the mount turns on
    "dizuo1": "base_ring_1",
    "dizuo3": "base_ring_2",
    "dizuo5": "base_ring_3",
    "turret": "turret",                 # traverse joint, driven like every jegn turret
    "barrel": "barrel",                 # elevation joint (trunnions), driven like every jegn barrel
    "paoguan": "gun_slide",             # the gun: recoils 11 px into the house on firing
    "waibi2": "barrel_jacket",
    "waibi10": "barrel_chase",
    "move": "cradle",
    "zhupao": "recoil_housing",
    "xiagua": "cradle_lower",
    "pitch": "gun_port_shield",         # the shield that closes the gun port as the gun elevates
    "paoshen1": "house_floor",
    "1yuanjiao1": "house_floor_corner_left",
    "1yuanjiao2": "house_floor_corner_right",
    "paoshen2": "house_lower",
    "2yuanjiao1": "house_lower_corner_left",
    "2yuanjiao2": "house_lower_corner_right",
    "houbuhumian": "house_rear_panel",
    "paoshen4": "house_upper",
    "4yuanjiao3": "house_corner_front_left",
    "4yuanjiao4": "house_corner_rear_left",
    "4yuanjiao5": "house_corner_rear_right",
    "4yuanjiao2": "house_corner_front_right",
    "pitchbian": "gun_port",
    "pitchbian1": "gun_port_frame",
    "beiban": "rear_plate",
    "beibanyuanhu1": "rear_plate_arc",
    "top": "roof",
    "bone2": "roof_fitting",
    "houdingyuan": "roof_rear",
    "houyuanjiao1": "roof_rear_round",
    "qiuchuang3": "mount_captain_hood",  # centre hood at the rear of the roof
    "huokong1": "sight_hood_left",       # the pointer's and trainer's sight hoods
    "shangzhu1": "sight_left_upper_post",
    "qiuchuang1": "sight_left_window",
    "qiuke1": "sight_left_dome",
    "qianchuang1": "sight_left_front_window",
    "fujian1": "sight_left_bracket",
    "xiazhu1": "sight_left_lower_post",
    "huokong2": "sight_hood_right",
    "shangzhu2": "sight_right_upper_post",
    "qiuchuang2": "sight_right_window",
    "qiuke2": "sight_right_dome",
    "qianchuang2": "sight_right_front_window",
    "fujian2": "sight_right_bracket",
    "xiazhu2": "sight_right_lower_post",
    "hulan": "handrail_left",
    "hulan2": "handrail_right",
    "fujiadangkuai": "front_apron",
    "flare": "muzzle_flash",             # LOD only in the source
}
# SBW's fire clip recoils the gun and flashes the muzzle; jegn drives the recoil from the synced shot instead
# (AbstractVehicleGeoRenderer) and draws the flash as particles, so only the resting clip is kept.
CLIPS = {"animation.mk_42.idle": "animation.mk42.rest"}

HOUSE = {"house_floor", "house_lower", "house_upper", "roof", "rear_plate", "front_apron"}
HOODS = {"sight_hood_left": "sight hood, left", "sight_hood_right": "sight hood, right",
         "mount_captain_hood": "mount captain's hood"}


def pose(elevation):
    # GeckoLib hands models the negated head pitch: a raised gun is a negative file X rotation (as the mortar).
    return {"barrel": (-elevation, 0, 0)}


# The LODs were authored separately: the left sight hood is numbered 3 (4 for its window), and the roof trim has
# numbered families. Fixed names first, then the families by prefix.
LOD_RENAME = {
    "huokong3": "sight_hood_left", "shangzhu3": "sight_left_upper_post", "qiuchuang4": "sight_left_window",
    "qiuke3": "sight_left_dome", "qianchuang3": "sight_left_front_window", "fujian3": "sight_left_bracket",
    "xiazhu3": "sight_left_lower_post", "neibi": "barrel_liner", "qiandingyuan": "roof_front",
    "houyuanjiao2": "roof_rear_round_2", "beibanyuanhu2": "rear_plate_arc_2",
}
LOD_FAMILIES = [("dingzeyuan", "roof_edge_"), ("neiyuanhu", "house_inner_arc_"), ("qianyuanjiao", "roof_front_corner_"),
                ("qianyuanhu", "roof_front_arc_"), ("houdingyuanhu", "roof_rear_arc_")]


def lod_name(name):
    if name in LOD_RENAME:
        return LOD_RENAME[name]
    if name in RENAME:
        return RENAME[name]
    for prefix, new in LOD_FAMILIES:
        if name.startswith(prefix) and name[len(prefix):].isdigit():
            return new + name[len(prefix):]
    raise SystemExit(f"LOD bone {name!r} has no entry in the rename map")


def rename_lod(src, level):
    raw = L.to_face_uv(L.load_json(src / f"geo/vehicle_lod/{SBW_ID}_lod{level}.geo.json"))
    mapping = {b["name"]: lod_name(b["name"]) for b in L.bones_of(raw)}
    return L.rename_bones(raw, mapping, f"geometry.jeg.{ID}_lod{level}")


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
    lods = {level: rename_lod(src, level) for level in (1, 2)}
    clips = L.rename_animations(L.load_json(src / f"animations/{SBW_ID}.animation.json"),
                                {**RENAME, "paoguan": "gun_slide", "flare": "muzzle_flash", "bone": "root",
                                 "camera": "camera"}, CLIPS)

    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, texture)
    sub = lambda name: L.subtree(by, name)

    # Joints, in file pixels and in vehicle-data blocks.
    turret_pivot = np.array(by["turret"]["pivot"], float)
    trunnion = np.array(by["barrel"]["pivot"], float)
    chase_lo, chase_hi = L.extent(quads, sub("barrel_chase"))
    muzzle_px = [0.0, float((chase_lo[1] + chase_hi[1]) / 2), float(chase_lo[2])]
    bore = float(trunnion[2] - chase_lo[2]) / 16

    # Hitbox. The base is fixed; the gunhouse parts turn with the turret; the gun turns with the turret and elevates
    # with the barrel (VehicleEntity carries the test point into each box's rest frame, OBBInfo.Box.follows).
    house_names = set().union(*(sub(n) for n in HOUSE)) | {
        n for n in by if n.startswith(("house_corner", "house_floor_corner", "house_lower_corner", "roof_rear"))}
    house_lo, house_hi = L.extent(quads, house_names)
    base_lo, base_hi = L.extent(quads, sub("base"))
    gun_names = sub("gun_slide") | sub("cradle")
    gun_lo, gun_hi = L.extent(quads, gun_names)
    # Only the part outside the house: inside it the house box already answers.
    gun_lo = gun_lo.copy()
    gun_hi = gun_hi.copy()
    gun_hi[2] = min(gun_hi[2], house_lo[2])
    gun_lo[0], gun_hi[0] = L.extent(quads, sub("gun_slide"))[0][0], L.extent(quads, sub("gun_slide"))[1][0]
    gun_lo[1], gun_hi[1] = L.extent(quads, sub("gun_slide"))[0][1], L.extent(quads, sub("gun_slide"))[1][1]
    # Part damage to "turret" jams the gun (VehicleEntity.isTurretDamaged), so only the gun is the turret part; the
    # house and hoods are the body.
    parts = [("body", "base ring", base_lo, base_hi), ("body", "gunhouse", house_lo, house_hi)]
    for bone, label in HOODS.items():
        lo, hi = L.extent(quads, sub(bone))
        lo = lo.copy()
        lo[1] = max(lo[1], house_hi[1])  # only what stands above the roof
        parts.append(("body", label, lo, hi))
    parts.append(("turret", "gun, outside the house", gun_lo, gun_hi))
    obb = L.obb_rows(parts)
    for row in obb:
        if row["covers"] == "base ring":
            continue
        row["follows"] = "barrel" if row["covers"] == "gun, outside the house" else "turret"

    # Every box must sit inside the model's parts it covers, at rest.
    all_lo, all_hi = L.extent(quads)

    # How the gun stands at the limits, for the review and the test.
    muzzle_at = {}
    for e in (ELEVATION_MIN, 0.0, 45.0, ELEVATION_MAX):
        muzzle_at[str(int(e))] = to_data(L.transform_point(geo, "barrel", muzzle_px, pose(e)))

    overlaps = L.coplanar_overlaps(geo)
    rig = {
        "model": f"jeg:entity/vehicle/{ID}",
        "source": "Superb Warfare mk_42, CrawKatt/SuperbWarfareReFabricated@952d73c (used with the SBW team's permission)",
        "units_per_block": 16,
        "front": "-Z in the model, +Z in vehicle data",
        "scale_from_source": 1.0,
        "elevation_degrees": [ELEVATION_MIN, ELEVATION_MAX],
        "elevation_sign": "barrel bone file X rotation = -elevation (GeckoLib setRotX(+elevation))",
        "turret_origin": to_data(turret_pivot),
        "trunnion": to_data(trunnion),
        "barrel_offset_from_turret": [round(a - b, 4) for a, b in zip(to_data(trunnion), to_data(turret_pivot))],
        "muzzle_rest": to_data(muzzle_px),
        "bore_from_trunnion_blocks": round(bore, 3),
        "muzzle_at_elevation": muzzle_at,
        "recoil_pixels": 11.0,
        "model_bounds": data_bounds(all_lo, all_hi),
        "gunhouse_bounds": data_bounds(house_lo, house_hi),
        "hitbox": obb,
        "coplanar_face_pairs": {"in_source": found, "after_refit": len(overlaps)},
    }

    assets = L.ASSETS
    L.write_json(assets / f"geckolib/models/entity/vehicle/{ID}.geo.json", geo)
    for level, lod in lods.items():
        L.write_json(assets / f"geckolib/models/entity/vehicle/lod/{ID}_lod{level}.geo.json", lod)
    L.write_json(assets / f"geckolib/animations/entity/vehicle/{ID}.animation.json", clips)
    shutil.copyfile(src / f"textures/entity/{SBW_ID}.png", assets / f"textures/entity/vehicle/{ID}.png")
    copies = [("textures/item/shells/ap_5_inches.png", "textures/item/shell_5in_ap.png"),
              ("textures/item/shells/he_5_inches.png", "textures/item/shell_5in_he.png"),
              ("textures/item/shells/cm_5_inches.png", "textures/item/shell_5in_cm.png"),
              ("textures/item/shells/gs_5_inches.png", "textures/item/shell_5in_gs.png"),
              ("sounds/mk_42/mk_42_fire_3p.ogg", "sounds/vehicle/mk42/fire.ogg"),
              ("sounds/mk_42/mk_42_far.ogg", "sounds/vehicle/mk42/fire_distant.ogg"),
              ("sounds/mk_42/mk_42_fire_1p_1.ogg", "sounds/vehicle/mk42/fire_interior.ogg")]
    copies += [(f"sounds/cannon/cannon_reload_0{i}.ogg", f"sounds/vehicle/mk42/load_{i}.ogg") for i in (1, 2, 3)]
    for source, target in copies:
        (assets / target).parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src / source, assets / target)
    (assets / "sounds/vehicle/mk42/CREDITS.md").write_text(
        "# Mk42 sounds\n\n"
        "By the Superb Warfare development team, used with their permission\n"
        "(`design/superbwarfare-port/PERMISSION.md`). Copied unchanged by `design/mk42/refit.py`:\n\n"
        "| File | SBW source |\n| --- | --- |\n"
        "| `fire.ogg` | `mk_42/mk_42_fire_3p` |\n"
        "| `fire_distant.ogg` | `mk_42/mk_42_far` |\n"
        "| `fire_interior.ogg` | `mk_42/mk_42_fire_1p_1` |\n"
        "| `load_1.ogg` .. `load_3.ogg` | `cannon/cannon_reload_01` .. `03` |\n", encoding="utf-8")
    L.write_json(HERE / "rig.json", rig)
    # The hitbox goes straight into the vehicle data, so it can't drift from the model.
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
        sheet, _ = R.contact_sheet(geo, texture, f"Mk42 refit, gun at {e:.0f} deg", "SBW mk_42, refit by design/mk42/refit.py",
                                   "barrel", pose(e))
        sheet.save(HERE / f"review-elevation-{int(e)}.png", optimize=True)

    print(json.dumps({k: rig[k] for k in ("turret_origin", "trunnion", "barrel_offset_from_turret", "muzzle_rest",
                                          "bore_from_trunnion_blocks", "muzzle_at_elevation", "model_bounds",
                                          "coplanar_face_pairs")}, indent=1))
    for row in obb:
        print(row)


if __name__ == "__main__":
    main()
