"""Refit of Superb Warfare's BMP-2 to the vehicle guide (fleet plan section 2). The imported model's generator: rerun it
after any change.

python -I design/superbwarfare-port/fetch_sbw.py <sbw_dir> bmp_2
python -I design/bmp2/refit.py <sbw_dir> [--dry]

Writes the GeckoLib geometry, two LODs and texture into the mod, design/bmp2/rig.json, the OBB rows and the track
block into data/jeg/vehicles/bmp2.json, and review renders. --dry writes only the renders and rig.json.

Scale 0.73: SBW's BMP-2 is about 1.37x the real vehicle (6.7 m long, 3.15 m wide); the fleet plan builds it 1:1.
Tracks: the links' path is computed from the running gear (refit_lib's track helpers) and checked against SBW's
hand-typed path (Bmp2Model.getBoneMoveY/Z).
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

ID = "bmp2"
SBW_ID = "bmp_2"
SCALE = 0.73
ELEVATION_MIN, ELEVATION_MAX = -7.5, 74.0  # SBW's (view pitch -74..7.5 in the data, which the enemy AI uses)
LINKS = 50

NAMED = {
    "root": "root", "base": "hull", "turret": "turret", "barrel": "barrel",
    "maingun": "cannon_mount", "2a42": "cannon", "barrel2": "cannon_barrel", "breaker": "muzzle_brake",
    "mg": "coax", "guge": "coax_barrel", "flare": "cannon_flash", "flare2": "coax_flash",
    "hexadecagon": "turret_ring", "zpt": "turret_hatches", "lptg": "hatch_left", "rptg": "hatch_right", "jpdb": "turret_hatch_hinges",
    "hexadecagon2": "turret_roof", "bone37": "antennas", "ptd": "turret_sights", "pdd2": "commander_sight",
    "9m113": "atgm_launcher", "guge3": "atgm_tube", "light": "searchlight",
    "smk": "smoke_dischargers", "rsmk": "smoke_dischargers_right", "lsmk": "smoke_dischargers_left",
    "ct1": "hull_upper", "ct3": "hull_upper_right_frame", "ct4": "hull_upper_right", "pz": "turret_basket",
    "zs": "hull_fittings", "qpq": "hull_rear_box", "pgpq": "hull_rear_plate", "ctzs": "hull_handles",
    "light2": "headlight_left", "light3": "headlight_right",
    "pg": "hull_rear", "pgd": "rear_doors", "ld": "rear_door_left", "ld2": "rear_door_right",
    "db": "rear_door_left_step", "db2": "rear_door_right_step", "wy": "exhaust",
    "ctd": "hull_deck", "ctw": "engine_deck", "pgd2": "roof_hatch_left", "pgd3": "roof_hatch_right",
    "zsw": "trim_vane", "ctd2": "driver_hatch",
    "ct5": "hull_side_left_frame", "ct6": "hull_side_left", "ctl4": "fender_left", "ctl5": "fender_left_rear",
    "ct2": "hull_side_right_frame", "ct7": "hull_side_right", "ctl2": "fender_right", "ctl3": "fender_right_rear",
    "Wheel": "running_gear", "WheelL": "wheels_left", "WheelR": "wheels_right",
    "wheelL0": "sprocket_left", "wheelR0": "sprocket_right", "wheelL7": "idler_left", "wheelR7": "idler_right",
    **{f"wheelL{i}": f"road_wheel_left_{i}" for i in range(1, 7)},
    **{f"wheelR{i}": f"road_wheel_right_{i}" for i in range(1, 7)},
    "TrackL": "track_left", "TrackR": "track_right", "bone21": "track_left_belt", "bone2": "track_right_belt",
    **{f"trackMovL{i}": f"track_link_left_{i}" for i in range(LINKS)},
    **{f"trackMovR{i}": f"track_link_right_{i}" for i in range(LINKS)},
    **{f"trackRotL{i}": f"track_link_left_{i}_turn" for i in range(LINKS)},
    **{f"trackRotR{i}": f"track_link_right_{i}_turn" for i in range(LINKS)},
}


def gear(side):
    return [f"sprocket_{side}"] + [f"road_wheel_{side}_{i}" for i in range(1, 7)] + [f"idler_{side}"]


def rename_map(geo):
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


def sbw_path(t):
    """SBW's hand-typed path (Bmp2Model), unscaled px offsets (dy, dz), for comparison."""
    def seg(t, keys):
        for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
            if t <= t1:
                return v0 + (v1 - v0) * (t - t0) / (t1 - t0)
        return keys[-1][1]

    y = seg(t, [(0, 0), (37.6667, 0), (38.5833, -1.8), (40.3333, -4.1), (42.9167, -10.3), (44.25, -12.9), (52.4167, -23.96),
                (84.5833, -23.96), (93, -12.93), (95.25, -10.085), (97.5, -4.585), (98.8333, -1.165), (99.25, -0.25), (100, 0)])
    z = seg(t, [(0, 0), (37.6667, 111.6), (38.5833, 113.25), (40.3333, 116), (42.9167, 116), (44.25, 113.5), (52.4167, 96.25),
                (84.5833, 14.095), (93, -3.565), (95.25, -6.35), (97.5, -6.39), (98.8333, -3.03), (99.25, -1.95), (100, 0)])
    return y, z


# Political markings painted out (user, 2026-11-17): SBW's texture carries a "Z" (bow and stern) and a St. George ribbon
# with a star (both sides) as decals on transparent ground. Their cubes are dropped; the hull paint shows beneath.
# Regions in the geometry's UV units (128 across a 512 texture).
MARKINGS = [("ribbon and star", (0.0, 106.0, 128.0, 128.0)), ("Z", (92.5, 84.0, 100.5, 93.0))]


def drop_markings(geo):
    """Remove every cube that samples a marking decal; returns the geometry and how many went."""
    dropped = 0
    for b in L.bones_of(geo):
        kept = []
        for c in b.get("cubes", []):
            marked = False
            for face in c["uv"].values():
                u, v = face["uv"]
                w, h = face["uv_size"]
                u1, u2 = sorted((u, u + w))
                v1, v2 = sorted((v, v + h))
                marked |= any(x1 <= u1 and u2 <= x2 and y1 <= v1 and v2 <= y2 for _, (x1, y1, x2, y2) in MARKINGS)
            if marked:
                dropped += 1
            else:
                kept.append(c)
        if "cubes" in b:
            b["cubes"] = kept
    return geo, dropped


def refit(raw, identifier, mapping):
    face, dropped = drop_markings(L.to_face_uv(raw))
    print(f"{identifier}: {dropped} marking decal cubes dropped")
    return L.scale(L.rename_bones(face, mapping, identifier), SCALE)


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
    mapping = rename_map(L.to_face_uv(raw))
    geo = L.resolve_zfighting(refit(raw, f"geometry.jeg.{ID}", mapping))
    lods = {}
    for level in (1, 2):
        lod_raw = L.load_json(src / f"geo/vehicle_lod/{SBW_ID}_lod{level}.geo.json")
        names = {b["name"] for b in L.bones_of(lod_raw)}
        missing = names - set(mapping)
        if missing:
            raise SystemExit(f"LOD {level} has bones the main model lacks: {sorted(missing)[:8]}")
        lods[level] = L.resolve_zfighting(refit(lod_raw, f"geometry.jeg.{ID}_lod{level}", {k: v for k, v in mapping.items() if k in names}))
    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, texture)
    sub = lambda name: L.subtree(by, name)

    # ---- Tracks.
    circles = {side: [L.circle_of(geo, quads, by, bone) for bone in gear(side)] for side in ("left", "right")}
    for a, b in zip(circles["left"], circles["right"]):
        if max(abs(x - y) for x, y in zip(a, b)) > 1e-3:
            raise SystemExit("the two sides' running gear differ; the shared path assumes a mirror image")
    link_rest = np.array(L.bone_pivot(geo, "track_link_left_0"), float)
    link_lo, link_hi = L.extent(quads, sub("track_link_left_0"))
    plate = max(by["track_link_left_0_turn"]["cubes"], key=lambda c: c["size"][0] * c["size"][2])
    half_thickness = plate["size"][1] / 2
    path = L.track_path(circles["left"], half_thickness)
    total = L.path_length(path)
    s_rest, rest_off = L.nearest_s(path, (link_rest[2], link_rest[1]))
    worst = 0.0
    for k in range(200):
        dy, dz = sbw_path(k / 2)
        worst = max(worst, L.nearest_s(path, (link_rest[2] + dz * SCALE, link_rest[1] + dy * SCALE))[1])
    wheel_groups = [
        {"bones": ["sprocket_left", "sprocket_right"], "radius": round(circles["left"][0][2], 3)},
        {"bones": [f"road_wheel_{s}_{i}" for s in ("left", "right") for i in range(1, 7)], "radius": round(circles["left"][1][2], 3)},
        {"bones": ["idler_left", "idler_right"], "radius": round(circles["left"][7][2], 3)},
    ]
    tracks = {"links": LINKS, "rest": [round(float(link_rest[2]), 4), round(float(link_rest[1]), 4)],
              "rest_s": round(s_rest, 4), "half_width": round(float(link_lo[0] + link_hi[0]) / 32, 4),
              "path": [[round(z, 3), round(y, 3)] for z, y in path], "wheels": wheel_groups}
    print(json.dumps({"circles_left": [[round(v, 3) for v in c] for c in circles["left"]], "half_thickness": half_thickness,
                      "path_length_px": round(total, 2), "rest_off_path_px": round(rest_off, 3), "sbw_path_worst_px": round(worst, 3)}))
    if worst > 2.5:
        raise SystemExit(f"computed track path strays {worst:.2f} px from SBW's")

    # ---- Guns.
    turret_pivot = np.array(by["turret"]["pivot"], float)
    trunnion = np.array(L.bone_pivot(geo, "barrel"), float)
    cannon_muzzle = front_centre(quads, sub("cannon_barrel"))
    coax_muzzle = front_centre(quads, sub("coax_barrel"))
    atgm_muzzle = front_centre(quads, sub("atgm_tube"))

    # ---- Hitbox: hull, tracks, the engine (front right), the turret house and the gun outside it.
    turret_names = sub("turret")
    hull_names = sub("hull") - turret_names
    hull_lo, hull_hi = L.extent(quads, hull_names)
    # The engine sits under the deck at the front right; no bone is its own, so the box is SBW's engine OBB, scaled.
    sbw_engine_centre, sbw_engine_half = np.array([-1.09375, 2.0625, 1.75]), np.array([1.15625, 0.375, 1.125])
    engine_lo = np.array([1, 1, -1]) * (sbw_engine_centre - sbw_engine_half * np.array([1, 1, -1])) * SCALE * 16
    engine_hi = np.array([1, 1, -1]) * (sbw_engine_centre + sbw_engine_half * np.array([1, 1, -1])) * SCALE * 16
    track_parts = []
    for side in ("left", "right"):
        lo, hi = L.extent(quads, sub(f"track_{side}") | sub(f"wheels_{side}"))
        track_parts.append((f"wheel_{side}", f"{side} track and running gear", lo, hi))
    house_names = turret_names - sub("barrel") - sub("atgm_launcher") - sub("smoke_dischargers") - sub("searchlight") - sub("antennas")
    house_lo, house_hi = L.extent(quads, house_names)
    gun_lo, gun_hi = L.extent(quads, sub("cannon_barrel"))
    gun_hi = gun_hi.copy()
    gun_hi[2] = min(gun_hi[2], house_lo[2])
    parts = [("body", "hull", hull_lo, hull_hi), *track_parts, ("main_engine", "engine deck", engine_lo, engine_hi),
             ("turret", "turret", house_lo, house_hi), ("turret", "cannon, outside the turret", gun_lo, gun_hi)]
    obb = L.obb_rows(parts)
    for row in obb:
        if row["covers"] == "turret":
            row["follows"] = "turret"
        elif row["covers"].startswith("cannon"):
            row["follows"] = "barrel"

    all_lo, all_hi = L.extent(quads, set(by) - sub("antennas"))
    rig = {
        "model": f"jeg:entity/vehicle/{ID}",
        "source": "Superb Warfare bmp_2, CrawKatt/SuperbWarfareReFabricated@952d73c (used with the SBW team's permission)",
        "units_per_block": 16,
        "front": "-Z in the model, +Z in vehicle data",
        "scale_from_source": SCALE,
        "elevation_degrees": [ELEVATION_MIN, ELEVATION_MAX],
        "turret_origin": to_data(turret_pivot),
        "trunnion": to_data(trunnion),
        "barrel_offset_from_turret": [round(a - b, 4) for a, b in zip(to_data(trunnion), to_data(turret_pivot))],
        "cannon_muzzle_rest": to_data(cannon_muzzle),
        "coax_muzzle_rest": to_data(coax_muzzle),
        "atgm_muzzle_rest": to_data(atgm_muzzle),
        "tracks": {k: v for k, v in tracks.items() if k != "path"} | {
            "path_points": len(path), "path_length_px": round(total, 3), "link_pitch_px": round(total / LINKS, 3),
            "rest_off_path_px": round(rest_off, 3), "worst_distance_from_sbw_path_px": round(worst, 3)},
        "model_bounds": data_bounds(all_lo, all_hi),
        "hull_bounds": data_bounds(hull_lo, hull_hi),
        "turret_bounds": data_bounds(house_lo, house_hi),
        "rear_doors": data_bounds(*L.extent(quads, sub("rear_doors"))),
        "hitbox": obb,
        "coplanar_face_pairs": {"in_source": found, "after_refit": len(L.coplanar_overlaps(geo))},
    }
    L.write_json(HERE / "rig.json", rig)

    posed, link_pose = L.links_posed(geo, "left", path, link_rest, 0.0, LINKS)
    posed, pose_r = L.links_posed(posed, "right", path, link_rest * np.array([-1, 1, 1]), 0.0, LINKS)
    link_pose.update(pose_r)
    for e in (0.0, 30.0):
        sheet, _ = R.contact_sheet(posed, texture, f"BMP-2 refit (scale {SCALE}), gun at {e:.0f} deg",
                                   "SBW bmp_2, refit by design/bmp2/refit.py; links on the computed path", "barrel",
                                   pose(e) | link_pose)
        sheet.save(HERE / f"review-elevation-{int(e)}.png", optimize=True)
    print(json.dumps({k: rig[k] for k in ("turret_origin", "trunnion", "barrel_offset_from_turret", "cannon_muzzle_rest",
                                          "coax_muzzle_rest", "atgm_muzzle_rest", "model_bounds", "hull_bounds",
                                          "rear_doors", "coplanar_face_pairs")}))
    for row in obb:
        print({k: (float(v) if not isinstance(v, str) else v) for k, v in row.items()})
    if dry:
        return

    assets = L.ASSETS
    L.write_json(assets / f"geckolib/models/entity/vehicle/{ID}.geo.json", geo)
    for level, lod in lods.items():
        L.write_json(assets / f"geckolib/models/entity/vehicle/lod/{ID}_lod{level}.geo.json", lod)
    shutil.copyfile(src / f"textures/entity/{SBW_ID}.png", assets / f"textures/entity/vehicle/{ID}.png")

    data_path = L.ROOT / f"src/main/resources/data/jeg/vehicles/{ID}.json"
    data = data_path.read_text(encoding="utf-8")
    start = data.index('  "obb": [')
    end = data.index("],", start) + 2
    data = data[:start] + '  "obb": [\n' + ",\n".join(json_rows(obb)) + "\n  ]," + data[end:]
    track_text = ('  "tracks": {"links": ' + str(LINKS) + ', "rest": ' + json.dumps(tracks["rest"]) + ', "rest_s": '
                  + json.dumps(tracks["rest_s"]) + ', "half_width": ' + json.dumps(tracks["half_width"])
                  + ',\n    "path": ' + json.dumps(tracks["path"], separators=(",", ":"))
                  + ',\n    "wheels": ' + json.dumps(wheel_groups) + "},\n")
    if '  "tracks": ' in data:
        start = data.index('  "tracks": ')
        end = data.index("\n", data.index('"wheels"', start)) + 1
        data = data[:start] + track_text + data[end:]
    else:
        at = data.index('  "weapons": [')
        data = data[:at] + track_text + data[at:]
    data_path.write_text(data, encoding="utf-8")


if __name__ == "__main__":
    main()
