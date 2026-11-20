"""Refit of Superb Warfare's YX-100 main battle tank to the vehicle guide. The imported model's generator: rerun it after
any change.

python -I design/superbwarfare-port/fetch_sbw.py <sbw_dir> yx_100 --extra sounds/vehicle/yx_100/yx_100_engine.ogg \
    sounds/vehicle/yx_100/yx_100_far.ogg sounds/vehicle/yx_100/yx_100_fire_1p.ogg sounds/vehicle/yx_100/yx_100_fire_3p.ogg \
    sounds/vehicle/yx_100/yx_100_reload.ogg sounds/vehicle/yx_100/yx_100_veryfar.ogg
python -I design/yx100/refit.py <sbw_dir> [--dry]

Writes the GeckoLib geometry, its LOD, texture, clip and sounds into the mod, design/yx100/rig.json, the OBB rows and the
track path into data/jeg/vehicles/yx100.json, and review renders. --dry writes only the renders and rig.json.

The YX-100 is SBW's own design, not a real tank: it is kept as the fleet's one deliberate science-fiction vehicle (user,
2026-11-17; it replaced our Leopard 2A7). Scale 0.74 makes it 3.7 blocks wide over the skirts, a real MBT's width
(SBW's is 5.0).

Fixes over SBW's model:
- Faces whose texture is fully transparent (the muzzle glow and flash helpers, the sight marker far ahead of the gun)
  are dropped: SBW scales them up only while firing, and nothing here plays those clips.
- The right sprocket's pivot sits 4.3 px forward of its geometry (the left one's is centred), so it would wobble as it
  turned: it is moved onto its centre.
- The tube and its shroud are grouped under one `gun_tube` bone, which recoils.

Tracks: SBW stacks the 41 links of a side at one point and moves them along a hand-typed path in code. Here the path is
computed from the running gear (the belt around the idler, the seven road wheels and the sprocket: the convex hull of
their circles, each grown by the link's half thickness) and written to the vehicle data for the shared track driver.
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

ID = "yx100"
SBW_ID = "yx_100"
SCALE = 0.74
ELEVATION_MIN, ELEVATION_MAX = -10.0, 30.0  # SBW's TurretPitchRange
RWS_ELEVATION = (-10.0, 60.0)  # SBW's PassengerWeaponStationPitchRange
LINKS = 41  # per side
ROAD_WHEELS = 7

# SBW's road wheels, front to back (wheelL8 sits between 2 and 3).
ROAD = ["0", "1", "2", "8", "3", "4", "5"]

NAMED = {
    "root": "root", "Wheel": "running_gear", "WheelL": "wheels_left", "WheelR": "wheels_right",
    **{f"wheel{s}{n}": f"road_wheel_{side}_{i + 1}" for s, side in (("L", "left"), ("R", "right")) for i, n in enumerate(ROAD)},
    "wheelL6": "idler_left", "wheelR6": "idler_right", "wheelL7": "sprocket_left", "wheelR7": "sprocket_right",
    "TrackL": "track_left", "TrackR": "track_right", "bone2": "track_left_belt", "bone3": "track_right_belt",
    **{f"trackMovL{i}": f"track_link_left_{i}" for i in range(LINKS)},
    **{f"trackMovR{i}": f"track_link_right_{i}" for i in range(LINKS)},
    **{f"trackRotL{i}": f"track_link_left_{i}_turn" for i in range(LINKS)},
    **{f"trackRotR{i}": f"track_link_right_{i}_turn" for i in range(LINKS)},
    # Hull. SBW's "base" (its recoil-shake bone) carries the hull and the turret.
    "base": "hull", "bone6": "hull_lower", "HullMain": "hull_main", "engine": "engine_deck",
    "part": "hull_front_left", "part2": "hull_front_right", "driver_window": "driver_hatch",
    "HullsPartsR": "hull_fittings", "HullDecoR": "hull_deco", "wires": "hull_wires", "QQQDS2": "hull_wires_left",
    "skirt_all": "side_skirts", "metal_part": "side_skirt_plates", "deco": "side_skirt_deco",
    "rubber_skirt": "rubber_skirts", "headlights_main": "headlights", "locator_1": "locator_1", "locator_2": "locator_2",
    # Turret.
    "turret": "turret", "turret_body": "turret_body", "QQQT": "turret_armour", "pattern_tower": "turret_marking",
    "QQTQXy": "turret_front_right", "QQTQXz": "turret_front_left",
    "QQQBF": "smoke_dischargers", "QQBFy": "smoke_dischargers_right", "QQBFy2": "smoke_dischargers_left",
    "QBF1": "smoke_discharger_right_1", "QBF2": "smoke_discharger_right_2", "QBF3": "smoke_discharger_right_3",
    "QBF4": "smoke_discharger_left_1", "QBF5": "smoke_discharger_left_2", "QBF6": "smoke_discharger_left_3",
    "QQTHY": "turret_rear", "QTHY2": "turret_rear_box", "QQTHZ": "turret_bustle", "QTHY": "turret_bustle_part",
    "DQQTHS": "turret_roof_fittings", "DQTHSXT": "sensor_right", "DQTs": "sensor_right_head",
    "DQTHSXT2": "sensor_left", "DQTs2": "sensor_left_head", "DQTHS": "antenna_base", "atenna": "antenna",
    "QQTRy": "turret_rail_right", "QQTRz": "turret_rail_left", "QQTH": "turret_rear_plate",
    "multi_role_VLS_L": "drone_cells_left", "multi_roleVLSR": "drone_cells_right",
    "multi_role_VLS_L2": "drone_cells_rear_left", "multi_role_VLS_R2": "drone_cells_rear_right",
    "las_aps_L": "laser_aps_left", "las_aps_R": "laser_aps_right", "reactor": "reactor", "octagon": "reactor_core",
    "QQJX": "turret_fittings",
    # Gun.
    "barrel": "barrel", "barrelroot": "gun_cradle", "bb_main": "gun", "bone4": "mantlet", "flare2": "coax_flash",
    "AQGDP4": "gun_tube_core", "AQGDP3": "gun_tube_end", "top_cover": "gun_shroud", "pattern_RG": "gun_marking",
    "glow": "cannon_glow",
    # The commander's remote weapon station.
    "passengerWeaponStation": "rws_base", "passengerWeaponStationYaw": "rws_mount", "pattern_hmg": "rws_marking",
    "passengerWeaponStationPitch": "rws", "action": "rws_action", "flare": "rws_flash",
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


def gear(side):
    return [f"idler_{side}"] + [f"road_wheel_{side}_{i}" for i in range(1, ROAD_WHEELS + 1)] + [f"sprocket_{side}"]


# ---------------------------------------------------------------- fixes on SBW's geometry (before renaming)

def centre_pivot(geo, bone, texture):
    """Move a wheel's pivot (y, z) onto the centre of its drawn geometry, keeping the drawn cubes where they are."""
    geo = copy.deepcopy(geo)
    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, texture)
    lo, hi = L.extent(quads, L.subtree(by, bone))
    b = by[bone]
    old = list(b["pivot"])
    b["pivot"] = [old[0], round(float(lo[1] + hi[1]) / 2, 5), round(float(lo[2] + hi[2]) / 2, 5)]
    for c in b.get("cubes", []):
        if "rotation" in c and "pivot" not in c:
            c["pivot"] = old
    return geo, old, b["pivot"]


def group_tube(geo):
    """The tube's core and its shroud under one recoiling bone at the trunnion."""
    geo = copy.deepcopy(geo)
    bones = L.bones_of(geo)
    by = {b["name"]: b for b in bones}
    pivot = by["barrel"]["pivot"]
    bones.append({"name": "gun_tube", "parent": "gun_cradle", "pivot": list(pivot)})
    for name in ("gun_tube_core", "gun_shroud"):
        by[name]["parent"] = "gun_tube"
    return geo


def refit(raw, texture, identifier, mapping):
    face = L.to_face_uv(raw)
    stripped, dropped = L.strip_transparent(face, texture)
    fixed, old, new = centre_pivot(stripped, "wheelR7", texture)
    geo = group_tube(L.rename_bones(fixed, mapping, identifier))
    return L.scale(geo, SCALE), dropped, (old, new)


def pose(elevation, turret=0.0, rws_yaw=0.0, rws_pitch=0.0):
    return {"turret": (0, turret, 0), "barrel": (-elevation, 0, 0), "rws_mount": (0, rws_yaw, 0), "rws": (-rws_pitch, 0, 0)}


# ---------------------------------------------------------------- the gun over the hull

def sample_quads(quads, names, spacing=1.0):
    """Points over each quad at most `spacing` px apart (its corners alone miss a long tube crossing a deck edge)."""
    pts = []
    for name, _, q, _ in quads:
        if name not in names:
            continue
        na = int(math.ceil(np.linalg.norm(q[1] - q[0]) / spacing)) + 1
        nb = int(math.ceil(np.linalg.norm(q[3] - q[0]) / spacing)) + 1
        a = np.linspace(0.0, 1.0, na)[:, None, None]
        b = np.linspace(0.0, 1.0, nb)[None, :, None]
        p = q[0] + (q[1] - q[0]) * a
        r = q[3] + (q[2] - q[3]) * a
        pts.append((p + (r - p) * b).reshape(-1, 3))
    return np.concatenate(pts)


def deck_heights(quads, names, cell=1.0):
    pts = sample_quads(quads, names, 0.5)
    keys = np.floor(pts[:, [0, 2]] / cell).astype(int)
    height = {}
    for (kx, kz), y in zip(map(tuple, keys), pts[:, 1]):
        if y > height.get((kx, kz), -1e9):
            height[(kx, kz)] = y
    return height


def gun_clear(geo, texture, hull_names, gun_root, turret, elevation, height, by, margin=0.5):
    quads, _ = L.posed_quads(geo, pose(elevation, turret), texture)
    pts = sample_quads(quads, L.subtree(by, gun_root))
    for p in pts:
        h = height.get((int(math.floor(p[0])), int(math.floor(p[2]))))
        if h is not None and p[1] < h + margin:
            return False
    return True


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
    if not L.renders_alike(face, L.strip_transparent(face, texture)[0], texture):
        raise SystemExit("dropping the transparent faces changed the rendering")
    found = len(L.coplanar_overlaps(face))
    mapping = rename_map(face)
    geo, dropped, sprocket_fix = refit(raw, texture, f"geometry.jeg.{ID}", mapping)
    geo = L.resolve_zfighting(geo)
    lod_raw = L.load_json(src / f"geo/vehicle_lod/{SBW_ID}_lod1.geo.json")
    lod, _, _ = refit(lod_raw, texture, f"geometry.jeg.{ID}_lod1", mapping)
    lod = L.resolve_zfighting(lod)
    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, texture)
    sub = lambda name: L.subtree(by, name)

    # ---- Tracks: the path from the running gear.
    circles = {side: [L.circle_of(geo, quads, by, bone) for bone in gear(side)] for side in ("left", "right")}
    for a, b in zip(circles["left"], circles["right"]):
        if max(abs(x - y) for x, y in zip(a, b)) > 1e-3:
            raise SystemExit(f"the two sides' running gear differ ({a} against {b}); the shared path assumes a mirror image")
    link_rest = np.array(L.bone_pivot(geo, "track_link_left_0"), float)
    link_lo, link_hi = L.extent(quads, sub("track_link_left_0"))
    plate = by["track_link_left_0_turn"]["cubes"][0]
    half_thickness = plate["size"][1] / 2
    path = L.track_path(circles["left"], half_thickness)
    total = L.path_length(path)
    s_rest, rest_off = L.nearest_s(path, (link_rest[2], link_rest[1]))
    lowest = min(y for _, y in path) - half_thickness
    if lowest < -0.05:
        raise SystemExit(f"the links' path runs {lowest:.2f} px under the ground")
    radius = lambda i: round(circles["left"][i][2], 3)
    wheel_groups = [
        {"bones": [f"idler_{s}" for s in ("left", "right")], "radius": radius(0)},
        {"bones": [f"road_wheel_{s}_{i}" for s in ("left", "right") for i in range(1, ROAD_WHEELS + 1)], "radius": radius(1)},
        {"bones": [f"sprocket_{s}" for s in ("left", "right")], "radius": radius(ROAD_WHEELS + 1)},
    ]
    tracks = {"links": LINKS, "rest": [round(float(link_rest[2]), 4), round(float(link_rest[1]), 4)],
              "rest_s": round(s_rest, 4), "half_width": round(float(link_lo[0] + link_hi[0]) / 32, 4),
              "path": [[round(z, 3), round(y, 3)] for z, y in path], "wheels": wheel_groups}

    # ---- The gun, the coax and the weapon station.
    turret_pivot = np.array(by["turret"]["pivot"], float)
    trunnion = np.array(L.bone_pivot(geo, "barrel"), float)
    tube_lo, tube_hi = L.extent(quads, sub("gun_tube"))
    core_lo, core_hi = L.extent(quads, sub("gun_tube_core"))
    muzzle = [(core_lo[0] + core_hi[0]) / 2, (core_lo[1] + core_hi[1]) / 2, tube_lo[2]]
    coax = L.bone_pivot(geo, "coax_flash")
    rws_pivot = L.bone_pivot(geo, "rws_mount")
    rws_trunnion = L.bone_pivot(geo, "rws")
    rws_flash = L.bone_pivot(geo, "rws_flash")
    rws_lo, rws_hi = L.extent(quads, sub("rws"))
    armour_lo, armour_hi = L.extent(quads, sub("turret_armour") - sub("turret_roof_fittings") - sub("drone_cells_left")
                                    - sub("drone_cells_right") - sub("laser_aps_left") - sub("laser_aps_right"))

    # ---- Where the hull stops the gun depressing: the lowest clear elevation at each bearing.
    hull_names = sub("hull") - sub("turret")
    # The tube and its shroud against the hull: the mantlet sits inside the turret house.
    height = deck_heights(quads, hull_names)
    limits = []
    for bearing in range(0, 360, 5):
        e = ELEVATION_MIN
        while e < ELEVATION_MAX and not gun_clear(geo, texture, hull_names, "gun_tube", bearing, e, height, by):
            e += 0.5
        limits.append((bearing, e))
    # Mirrored: the gun must clear at a bearing and its mirror image, whichever way the game counts yaw.
    clear_at = {b: e for b, e in limits}
    by_bearing = [max(clear_at[b], clear_at[(360 - b) % 360]) for b in range(0, 360, 5)]

    # ---- Hitbox: the hull, each track run, the engine deck; the turret house turns with the turret, and the gun
    # outside the house elevates with the barrel.
    body_names = hull_names - sub("engine_deck")
    hull_lo, hull_hi = L.extent(quads, body_names)
    engine_lo, engine_hi = L.extent(quads, sub("engine_deck"))
    track_parts = []
    for side in ("left", "right"):
        lo, hi = L.extent(quads, sub(f"track_{side}") | sub(f"wheels_{side}"))
        track_parts.append((f"wheel_{side}", f"{side} track and running gear", lo, hi))
    house_names = sub("turret") - sub("barrel") - sub("antenna_base") - sub("rws_base")
    house_lo, house_hi = L.extent(quads, house_names)
    gun_lo, gun_hi = L.extent(quads, sub("barrel"))
    gun_hi = gun_hi.copy()
    gun_hi[2] = min(gun_hi[2], house_lo[2])
    parts = [("body", "hull", hull_lo, hull_hi), *track_parts, ("main_engine", "engine deck", engine_lo, engine_hi),
             ("turret", "turret house", house_lo, house_hi), ("turret", "gun, outside the house", gun_lo, gun_hi)]
    obb = L.obb_rows(parts)
    for row in obb:
        if row["covers"] == "turret house":
            row["follows"] = "turret"
        elif row["covers"].startswith("gun"):
            row["follows"] = "barrel"

    all_lo, all_hi = L.extent(quads, set(by) - sub("antenna_base"))
    rig = {
        "model": f"jeg:entity/vehicle/{ID}",
        "source": "Superb Warfare yx_100, CrawKatt/SuperbWarfareReFabricated@952d73c (used with the SBW team's permission)",
        "units_per_block": 16,
        "front": "-Z in the model, +Z in vehicle data",
        "scale_from_source": SCALE,
        "elevation_degrees": [ELEVATION_MIN, ELEVATION_MAX],
        "lowest_elevation_by_bearing": {"step_degrees": 5, "from": "turret straight ahead, either side",
                                        "degrees": by_bearing},
        "turret_origin": to_data(turret_pivot),
        "trunnion": to_data(trunnion),
        "barrel_offset_from_turret": [round(a - b, 4) for a, b in zip(to_data(trunnion), to_data(turret_pivot))],
        "muzzle_rest": to_data(muzzle),
        "bore_from_trunnion_blocks": round(float(trunnion[2] - tube_lo[2]) / 16, 4),
        "recoil_pixels": round(9.25 * SCALE, 3),
        "coax_muzzle_rest": to_data(coax),
        "turret_roof_y": round(float(armour_hi[1]) / 16, 4),
        "rws_mount_pivot": to_data(rws_pivot),
        "rws_trunnion": to_data(rws_trunnion),
        "rws_muzzle_rest": to_data([rws_trunnion[0], rws_trunnion[1], min(rws_lo[2], rws_flash[2])]),
        "rws_elevation_degrees": list(RWS_ELEVATION),
        "tracks": {k: v for k, v in tracks.items() if k != "path"} | {
            "path_points": len(path), "path_length_px": round(total, 3), "link_pitch_px": round(total / LINKS, 3),
            "rest_off_path_px": round(rest_off, 3), "lowest_link_px": round(lowest, 3)},
        "model_bounds": data_bounds(all_lo, all_hi),
        "hull_bounds": data_bounds(hull_lo, hull_hi),
        "engine_deck_bounds": data_bounds(engine_lo, engine_hi),
        "turret_house_bounds": data_bounds(house_lo, house_hi),
        "hitbox": obb,
        "transparent_faces_dropped": dropped,
        "sprocket_right_pivot": {"sbw": sprocket_fix[0], "centred": sprocket_fix[1]},
        "coplanar_face_pairs": {"in_source": found, "after_refit": len(L.coplanar_overlaps(geo))},
    }
    L.write_json(HERE / "rig.json", rig)

    # ---- Review renders: links on the path, the gun at 0 and 30 degrees, the weapon station turned.
    posed, link_pose = L.links_posed(geo, "left", path, link_rest, 0.0, LINKS)
    posed, pose_r = L.links_posed(posed, "right", path, link_rest * np.array([-1, 1, 1]), 0.0, LINKS)
    link_pose.update(pose_r)
    for e in (0.0, 30.0):
        sheet, _ = R.contact_sheet(posed, texture, f"YX-100 refit (scale {SCALE}), gun at {e:.0f} deg",
                                   "SBW yx_100, refit by design/yx100/refit.py; links on the computed path", "barrel",
                                   pose(e) | link_pose)
        sheet.save(HERE / f"review-elevation-{int(e)}.png", optimize=True)
    q, ts, _, _ = R.load_quads(posed, pose(10.0, 0.0, 30.0, 15.0) | link_pose)
    R.render(R.visible(q, texture, ts), texture, ts, 35, 25, size=(1400, 1000)).save(HERE / "review-weapon-station.png", optimize=True)
    q, ts, _, _ = R.load_quads(posed, pose(by_bearing[36], 180.0) | link_pose)
    R.render(R.visible(q, texture, ts), texture, ts, 90, 0, size=(1400, 700)).save(HERE / "review-rear-deck.png", optimize=True)
    print(json.dumps({k: rig[k] for k in ("turret_origin", "trunnion", "muzzle_rest", "bore_from_trunnion_blocks",
                                          "coax_muzzle_rest", "turret_roof_y", "rws_mount_pivot", "rws_trunnion",
                                          "rws_muzzle_rest", "lowest_elevation_by_bearing", "model_bounds", "hull_bounds",
                                          "engine_deck_bounds", "coplanar_face_pairs", "transparent_faces_dropped",
                                          "sprocket_right_pivot")}))
    print(json.dumps(rig["tracks"]))
    for row in obb:
        print(row)
    if dry:
        return

    # ---- Into the mod.
    assets = L.ASSETS
    L.write_json(assets / f"geckolib/models/entity/vehicle/{ID}.geo.json", geo)
    L.write_json(assets / f"geckolib/models/entity/vehicle/lod/{ID}_lod1.geo.json", lod)
    texture.save(assets / f"textures/entity/vehicle/{ID}.png")
    shutil.copyfile(src / f"textures/vehicle_icon/{SBW_ID}_icon.png", assets / f"textures/vehicle_icon/{ID}_icon.png")
    # SBW's fire clips are driven from the synced shots (the tube's recoil); ShellGunEntity plays "<id>.rest".
    L.write_json(assets / f"geckolib/animations/entity/vehicle/{ID}.animation.json",
                 {"format_version": "1.8.0", "animations": {f"animation.{ID}.rest": {"loop": True, "bones": {}}}})
    copies = [("yx_100_fire_3p", "fire"), ("yx_100_fire_1p", "fire_interior"), ("yx_100_far", "fire_distant"),
              ("yx_100_veryfar", "fire_very_far"), ("yx_100_reload", "load"), ("yx_100_engine", "engine")]
    for source, target in copies:
        out = assets / f"sounds/vehicle/{ID}/{target}.ogg"
        out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src / f"sounds/vehicle/yx_100/{source}.ogg", out)
    (assets / f"sounds/vehicle/{ID}/CREDITS.md").write_text(
        "# YX-100 sounds\n\n"
        "By the Superb Warfare development team, used with their permission\n"
        "(`design/superbwarfare-port/PERMISSION.md`). Copied unchanged by `design/yx100/refit.py`:\n\n"
        "| File | SBW source |\n| --- | --- |\n"
        + "".join(f"| `{target}.ogg` | `vehicle/yx_100/{source}` |\n" for source, target in copies)
        + "\nThe engine loops (`engine_idle`, `engine_load`, `engine_cab`, `engine_distant`) are derived from\n"
          "`engine.ogg` by `design/ground-vehicles/prepare_audio.py`.\n", encoding="utf-8")

    # The OBB rows and the track block replace their placeholders in the hand-written vehicle data.
    data_path = L.ROOT / f"src/main/resources/data/jeg/vehicles/{ID}.json"
    data = data_path.read_text(encoding="utf-8")
    start = data.index('  "obb": [')
    end = data.index("],", start) + 2
    data = data[:start] + '  "obb": [\n' + ",\n".join(json_rows(obb)) + "\n  ]," + data[end:]
    start = data.index('  "tracks": ')
    end = data.index("\n", data.index('"wheels"', start)) + 1
    track_text = ('  "tracks": {"links": ' + str(LINKS) + ', "rest": ' + json.dumps(tracks["rest"]) + ', "rest_s": '
                  + json.dumps(tracks["rest_s"]) + ', "half_width": ' + json.dumps(tracks["half_width"])
                  + ',\n    "path": ' + json.dumps(tracks["path"], separators=(",", ":"))
                  + ',\n    "wheels": ' + json.dumps(wheel_groups) + "},\n")
    data_path.write_text(data[:start] + track_text + data[end:], encoding="utf-8")


if __name__ == "__main__":
    main()
