"""Refit of Superb Warfare's PLZ-05 155 mm self-propelled howitzer to the vehicle guide. The imported model's generator:
rerun it after any change.

python -I design/superbwarfare-port/fetch_sbw.py <sbw_dir> plz_05 --extra sounds/vehicle/plz_05/plz_05_engine.ogg \
    sounds/vehicle/plz_05/plz_05_far.ogg sounds/vehicle/plz_05/plz_05_fire_1p.ogg sounds/vehicle/plz_05/plz_05_fire_3p.ogg \
    sounds/vehicle/plz_05/plz_05_reload.ogg sounds/vehicle/plz_05/plz_05_veryfar.ogg
python -I design/plz05/refit.py <sbw_dir> [--dry]

Writes the GeckoLib geometry, its LOD, texture, clips and sounds into the mod, design/plz05/rig.json, the OBB rows and
the track path into data/jeg/vehicles/plz05.json, and review renders. --dry writes only the renders and rig.json.

Scale 0.77: SBW's PLZ-05 is about 1.3x the real vehicle (3.4 m wide).

Tracks: SBW stacks all 50 links of a side at one point and moves them along a hand-typed keyframe path in code
(Plz05Model.getBoneMoveY/Z/RotX). Here the path is computed from the running gear instead: the belt around the
sprocket, road wheels, idler and return rollers (the convex hull of their circles, each grown by the link's half
thickness), written to the vehicle data for the shared track driver. It is checked against SBW's path.

The roof machine gun (a 12.7 mm on a pintle at the right hatch) isn't in SBW's model: the refit adds it, with its own
texture strip below SBW's texture.
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

ID = "plz05"
SBW_ID = "plz_05"
SCALE = 0.77
ELEVATION_MIN, ELEVATION_MAX = -3.0, 68.0
LINKS = 50  # per side

NAMED = {
    "root": "root", "Wheel": "running_gear", "base": "base", "turret": "turret", "barrelBase": "cradle",
    "barrel": "barrel", "tube": "tube", "body": "hull",
    "WheelL": "wheels_left", "WheelR": "wheels_right",
    "wheelL0": "sprocket_left", "wheelR0": "sprocket_right", "wheelL7": "idler_left", "wheelR7": "idler_right",
    **{f"wheelL{i}": f"road_wheel_left_{i}" for i in range(1, 7)},
    **{f"wheelR{i}": f"road_wheel_right_{i}" for i in range(1, 7)},
    "smallwhlL": "return_rollers_left", "smallwhlR": "return_rollers_right",
    **{f"smallwhlL{i}": f"return_roller_left_{i}" for i in range(1, 4)},
    **{f"smallwhlR{i}": f"return_roller_right_{i}" for i in range(1, 4)},
    "TrackL": "track_left", "TrackR": "track_right", "bone21": "track_left_belt", "bone2": "track_right_belt",
    **{f"trackMovL{i}": f"track_link_left_{i}" for i in range(LINKS)},
    **{f"trackMovR{i}": f"track_link_right_{i}" for i in range(LINKS)},
    **{f"trackRotL{i}": f"track_link_left_{i}_turn" for i in range(LINKS)},
    **{f"trackRotR{i}": f"track_link_right_{i}_turn" for i in range(LINKS)},
    "xroll1": "trunnion", "xroll2": "trunnion_left", "xroll3": "trunnion_right", "bone12": "muzzle_brake",
    "bone19": "barrel_fittings",
    "yroll": "turret_ring", "trtbody": "turret_body", "trtitems": "turret_fittings",
    "tifront": "turret_front", "tifrontdoor": "turret_front_door", "smoke": "smoke_dischargers",
    "tifchuang": "turret_front_window", "tileft": "turret_left", "menchuang": "turret_left_doors",
    "tidoorL1": "turret_left_door_1", "tidoorL2": "turret_left_door_2", "tidoorL3": "turret_left_door_3",
    "tiright": "turret_right", "menchuang2": "turret_right_doors", "tidoorR1": "turret_right_door_1",
    "tidoorR2": "turret_right_door_2", "tiback": "turret_rear", "huojia": "turret_rack", "tiback1": "turret_rear_box",
    "tiback2": "turret_rear_lockers", "tibackdoor": "turret_rear_door", "titop": "turret_roof",
    "titop1": "sight", "titop2": "turret_roof_periscope_right", "titop3": "turret_roof_vents_right",
    "titop4": "hatch_right", "titop5": "hatch_left", "titop6": "turret_roof_vents_left", "titop7": "turret_roof_rear",
    "titoplight": "turret_roof_light", "titop8": "antenna",
    "main": "hull_main", "bottom": "suspension", **{f"hang{i}": f"suspension_arm_{i}" for i in range(1, 7)},
    "frontrL": "hull_front_left", "frontrR": "hull_front_right", "sidearmorL": "side_skirt_left",
    "sidearmorR": "side_skirt_right", "items": "hull_fittings", "engine": "engine_deck", "ctd2": "driver_hatch",
    "mifront1": "hull_front_fittings", "mifront2": "glacis", "flightL": "headlight_left", "flightR": "headlight_right",
    "mdoorL": "hull_door_left", "mdoorB": "hull_rear", "miback1": "hull_rear_fittings", "blightL": "taillight_left",
    "blightR": "taillight_right", "backup": "hull_rear_plate", "miback": "hull_rear_box",
}

# Running gear, per side: the bones whose circles the track wraps (roll radius measured from the geometry).
def gear(side):
    return ([f"sprocket_{side}"] + [f"road_wheel_{side}_{i}" for i in range(1, 7)] + [f"idler_{side}"]
            + [f"return_roller_{side}_{i}" for i in range(1, 4)])


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


# ---------------------------------------------------------------- tracks

def circle_of(geo, quads, by, bone):
    """A wheel's centre (z, y in file px) and its radius in the YZ plane, from its drawn geometry."""
    names = L.subtree(by, bone)
    pivot = np.array(L.bone_pivot(geo, bone), float)
    pts = np.concatenate([q[2] for q in quads if q[0] in names])
    r = float(np.max(np.hypot(pts[:, 1] - pivot[1], pts[:, 2] - pivot[2])))
    return float(pivot[2]), float(pivot[1]), r


def hull_ccw(points):
    """Andrew's monotone chain; points [(z, y)] -> hull counter-clockwise in (z right, y up)."""
    pts = sorted(set(points))

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])

    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


def track_path(circles, half_thickness, step_degrees=3.0):
    """The links' centre line: the convex hull of the wheels' circles grown by the link's half thickness, as a closed
    polygon [(z, y)] counter-clockwise in (z right, y up). With the front at -Z that runs rearward along the bottom and
    forward along the top: the way the links move relative to the hull when the vehicle drives forward."""
    pts = []
    n = int(round(360 / step_degrees))
    for z, y, r in circles:
        rr = r + half_thickness
        for k in range(n):
            a = 2 * math.pi * k / n
            pts.append((round(z + rr * math.cos(a), 4), round(y + rr * math.sin(a), 4)))
    return hull_ccw(pts)


def path_length(path):
    return sum(math.dist(path[i], path[(i + 1) % len(path)]) for i in range(len(path)))


def path_at(path, s):
    """Point and unit tangent at arc length s (wrapped)."""
    total = path_length(path)
    s %= total
    for i in range(len(path)):
        a, b = path[i], path[(i + 1) % len(path)]
        d = math.dist(a, b)
        if s <= d or i == len(path) - 1:
            t = 0.0 if d == 0 else s / d
            return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t), ((b[0] - a[0]) / d, (b[1] - a[1]) / d)
        s -= d
    raise AssertionError


def nearest_s(path, point):
    best = (1e9, 0.0)
    acc = 0.0
    for i in range(len(path)):
        a, b = np.array(path[i]), np.array(path[(i + 1) % len(path)])
        ab = b - a
        t = float(np.clip(np.dot(np.array(point) - a, ab) / max(np.dot(ab, ab), 1e-12), 0, 1))
        d = float(np.linalg.norm(a + ab * t - np.array(point)))
        if d < best[0]:
            best = (d, acc + t * float(np.linalg.norm(ab)))
        acc += float(np.linalg.norm(ab))
    return best[1], best[0]


def link_turn(tangent):
    """X rotation (degrees, file sense) that carries a link lying on the top run, moving forward (-Z), onto a path whose
    tangent is (dz, dy)."""
    dz, dy = tangent
    return math.degrees(math.atan2(dy, -dz))


def sbw_path(t):
    """SBW's hand-typed path (Plz05Model), unscaled px offsets (dy, dz) and X rotation, for comparison."""
    def lerp(a, b, f):
        return a + (b - a) * f

    def seg(t, keys):
        for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
            if t <= t1:
                return lerp(v0, v1, (t - t0) / (t1 - t0))
        return keys[-1][1]

    y = seg(t, [(0, 0), (44, -0.6), (46, -4.13), (48, -9.565), (49.5833, -12.32), (55, -19.71), (86.75, -19.67),
                (95.3333, -11.005), (96.6667, -8.35), (98.3333, -3.285), (100, 0)])
    z = seg(t, [(0, 0), (44, 133.75), (46, 137.405), (48, 137.47), (49.5833, 134.72), (55, 119.36), (86.75, 23.32),
                (95.3333, -0.695), (96.6667, -3.205), (98.3333, -3.28), (100, 0)])
    return y, z


def links_posed(geo, side, path, rest, travel_px):
    """A copy of the geometry with one side's links moved along the path (for review renders), and the pose."""
    geo = copy.deepcopy(geo)
    by = {b["name"]: b for b in L.bones_of(geo)}
    total = path_length(path)
    s_rest, _ = nearest_s(path, (rest[2], rest[1]))
    pose = {}
    for i in range(LINKS):
        s = s_rest + travel_px + i * total / LINKS
        (z, y), tangent = path_at(path, s)
        dy, dz = y - rest[1], z - rest[2]
        for name in L.subtree(by, f"track_link_{side}_{i}"):
            b = by[name]
            b["pivot"] = [b["pivot"][0], b["pivot"][1] + dy, b["pivot"][2] + dz]
            for c in b.get("cubes", []):
                c["origin"] = [c["origin"][0], c["origin"][1] + dy, c["origin"][2] + dz]
                if "pivot" in c:
                    c["pivot"] = [c["pivot"][0], c["pivot"][1] + dy, c["pivot"][2] + dz]
        pose[f"track_link_{side}_{i}_turn"] = (link_turn(tangent), 0, 0)
    return geo, pose


# ---------------------------------------------------------------- the roof machine gun

PALETTE_ORIGIN = (240, 0)  # four 4x4 swatches in a fully transparent corner of SBW's texture
SWATCHES = {"gunmetal": (52, 54, 52), "dark": (30, 31, 30), "olive": (78, 84, 52), "steel": (96, 98, 94)}
FACES = ("north", "south", "east", "west", "up", "down")


def swatch_uv(name):
    i = list(SWATCHES).index(name)
    u, v = PALETTE_ORIGIN[0] + 4 * i + 1, PALETTE_ORIGIN[1] + 1
    return {face: {"uv": [u, v], "uv_size": [2, 2]} for face in FACES}


def box(lo, size, finish):
    return {"origin": [round(float(x), 4) for x in lo], "size": [round(float(x), 4) for x in size], "uv": swatch_uv(finish)}


def hmg_frame(quads, by):
    """The pintle stands on the right-hand hatch ring (the commander's), on the turret roof."""
    lo, hi = L.extent(quads, L.subtree(by, "hatch_right"))
    return float(lo[0] + hi[0]) / 2, float(hi[1]), float(lo[2] + hi[2]) / 2


def add_hmg(geo, frame):
    """A 12.7 mm machine gun on a pintle: hmg_mount turns (yaw) on the hatch ring, hmg elevates (pitch) on its
    trunnion. File px; the front is -Z, +X the vehicle's left. About 1.5 m long, as a QJC-88."""
    geo = copy.deepcopy(geo)
    cx, top, cz = frame
    ty = top + 6.0
    mount = {"name": "hmg_mount", "parent": "turret", "pivot": [cx, top, cz], "cubes": [
        box((cx - 2.6, top, cz - 2.6), (5.2, 0.8, 5.2), "steel"),          # race ring
        box((cx - 0.6, top + 0.8, cz - 0.6), (1.2, 4.4, 1.2), "gunmetal"),  # pintle post
        box((cx - 1.5, top + 5.2, cz - 1.0), (3.0, 0.8, 2.0), "dark"),      # cradle yoke
    ]}
    gun = {"name": "hmg", "parent": "hmg_mount", "pivot": [cx, ty, cz], "cubes": [
        box((cx - 1.1, ty - 1.0, cz - 3.0), (2.2, 2.4, 7.0), "gunmetal"),    # receiver
        box((cx - 0.7, ty - 0.5, cz - 6.0), (1.4, 1.4, 3.0), "dark"),        # barrel support
        box((cx - 0.4, ty - 0.2, cz - 18.0), (0.8, 0.8, 12.0), "dark"),      # barrel
        box((cx - 0.6, ty - 0.4, cz - 19.6), (1.2, 1.2, 1.6), "dark"),       # muzzle brake
        box((cx + 1.1, ty - 1.8, cz - 1.2), (1.8, 2.4, 3.2), "olive"),       # ammunition box, left side
        box((cx - 1.0, ty - 0.6, cz + 4.0), (2.0, 1.2, 0.8), "dark"),        # spade grips
        box((cx - 0.2, ty + 1.4, cz - 2.2), (0.4, 0.8, 0.6), "steel"),       # rear sight
    ]}
    L.bones_of(geo).extend([mount, gun])
    return geo


def check_palette_free(geo):
    """The swatches must sit where no SBW face samples the texture."""
    x0, y0 = PALETTE_ORIGIN
    for b in L.bones_of(geo):
        for c in b.get("cubes", []):
            for face in c["uv"].values():
                u, v = face["uv"]
                w, h = face["uv_size"]
                u1, u2 = sorted((u, u + w))
                v1, v2 = sorted((v, v + h))
                if u1 < x0 + 16 and u2 > x0 and v1 < y0 + 4 and v2 > y0:
                    raise SystemExit(f"bone {b['name']} samples the palette corner")


def palette_texture(texture):
    out = texture.convert("RGBA").copy()
    x0, y0 = PALETTE_ORIGIN
    for i, colour in enumerate(SWATCHES.values()):
        for x in range(4):
            for y in range(4):
                out.putpixel((x0 + 4 * i + x, y0 + y), colour + (255,))
    return out


def refit(raw, identifier, mapping):
    face = L.to_face_uv(raw)
    return L.scale(L.rename_bones(face, mapping, identifier), SCALE)


def pose(elevation, hmg_yaw=0.0, hmg_pitch=0.0):
    return {"barrel": (-elevation, 0, 0), "hmg_mount": (0, hmg_yaw, 0), "hmg": (-hmg_pitch, 0, 0)}


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
    if not L.render_identical(raw, L.to_face_uv(raw), texture):
        raise SystemExit("per-face UV rewrite changed the rendering")
    found = len(L.coplanar_overlaps(L.to_face_uv(raw)))
    mapping = rename_map(L.to_face_uv(raw))
    base = L.resolve_zfighting(refit(raw, f"geometry.jeg.{ID}", mapping))
    check_palette_free(base)
    by = {b["name"]: b for b in L.bones_of(base)}
    quads, _ = L.posed_quads(base, None, texture)
    frame = hmg_frame(quads, by)
    geo = L.resolve_zfighting(add_hmg(base, frame))
    tex = palette_texture(texture)
    lod_raw = L.load_json(src / f"geo/vehicle_lod/{SBW_ID}_lod1.geo.json")
    lod_names = {b["name"] for b in L.bones_of(lod_raw)}
    lod = L.resolve_zfighting(add_hmg(L.resolve_zfighting(refit(lod_raw, f"geometry.jeg.{ID}_lod1",
                                             {k: v for k, v in mapping.items() if k in lod_names})), frame))
    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, tex)
    sub = lambda name: L.subtree(by, name)

    # ---- Tracks: the path from the running gear, checked against SBW's.
    circles = {side: [circle_of(geo, quads, by, bone) for bone in gear(side)] for side in ("left", "right")}
    for a, b in zip(circles["left"], circles["right"]):
        if max(abs(x - y) for x, y in zip(a, b)) > 1e-3:
            raise SystemExit("the two sides' running gear differ; the shared path assumes a mirror image")
    link_rest = np.array(L.bone_pivot(geo, "track_link_left_0"), float)
    link_lo, link_hi = L.extent(quads, sub("track_link_left_0"))
    plate = [c for c in by["track_link_left_0_turn"]["cubes"] if "rotation" not in c][0]
    half_thickness = plate["size"][1] / 2
    path = track_path(circles["left"], half_thickness)
    total = path_length(path)
    s_rest, rest_off = nearest_s(path, (link_rest[2], link_rest[1]))
    worst = 0.0
    for k in range(200):
        dy, dz = sbw_path(k / 2)
        worst = max(worst, nearest_s(path, (link_rest[2] + dz * SCALE, link_rest[1] + dy * SCALE))[1])
    if worst > 2.0:
        raise SystemExit(f"computed track path strays {worst:.2f} px from SBW's")
    wheel_groups = [
        {"bones": [f"sprocket_{s}" for s in ("left", "right")], "radius": round(circles["left"][0][2], 3)},
        {"bones": [f"road_wheel_{s}_{i}" for s in ("left", "right") for i in range(1, 7)], "radius": round(circles["left"][1][2], 3)},
        {"bones": [f"idler_{s}" for s in ("left", "right")], "radius": round(circles["left"][7][2], 3)},
        {"bones": [f"return_roller_{s}_{i}" for s in ("left", "right") for i in range(1, 4)], "radius": round(circles["left"][8][2], 3)},
    ]
    tracks = {"links": LINKS, "rest": [round(float(link_rest[2]), 4), round(float(link_rest[1]), 4)],
              "rest_s": round(s_rest, 4), "half_width": round(float(link_lo[0] + link_hi[0]) / 32, 4),
              "path": [[round(z, 3), round(y, 3)] for z, y in path], "wheels": wheel_groups}

    # ---- Gun and roof gun geometry for the rig.
    turret_pivot = np.array(by["turret"]["pivot"], float)
    trunnion = np.array(L.bone_pivot(geo, "barrel"), float)
    tube_lo, tube_hi = L.extent(quads, sub("tube"))
    muzzle = [(tube_lo[0] + tube_hi[0]) / 2, (tube_lo[1] + tube_hi[1]) / 2, tube_lo[2]]
    hmg_lo, hmg_hi = L.extent(quads, sub("hmg"))
    hmg_trunnion = np.array(by["hmg"]["pivot"], float)
    hmg_muzzle = [hmg_trunnion[0], hmg_trunnion[1], hmg_lo[2]]
    sight_lo, sight_hi = L.extent(quads, sub("sight"))

    # ---- Hitbox: the hull, each track run, the engine deck; the turret house turns with the turret, and the gun
    # outside the house elevates with the barrel.
    hull_lo, hull_hi = L.extent(quads, sub("hull") - sub("engine_deck"))
    engine_lo, engine_hi = L.extent(quads, sub("engine_deck"))
    track_parts = []
    for side in ("left", "right"):
        lo, hi = L.extent(quads, sub(f"track_{side}") | sub(f"wheels_{side}") | sub(f"return_rollers_{side}"))
        track_parts.append((f"wheel_{side}", f"{side} track and running gear", lo, hi))
    house_names = sub("turret") - sub("barrel") - sub("antenna") - sub("hmg_mount")
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

    all_lo, all_hi = L.extent(quads, set(by) - sub("antenna"))
    rig = {
        "model": f"jeg:entity/vehicle/{ID}",
        "source": "Superb Warfare plz_05, CrawKatt/SuperbWarfareReFabricated@952d73c (used with the SBW team's permission)",
        "units_per_block": 16,
        "front": "-Z in the model, +Z in vehicle data",
        "scale_from_source": SCALE,
        "elevation_degrees": [ELEVATION_MIN, ELEVATION_MAX],
        "turret_origin": to_data(turret_pivot),
        "trunnion": to_data(trunnion),
        "barrel_offset_from_turret": [round(a - b, 4) for a, b in zip(to_data(trunnion), to_data(turret_pivot))],
        "muzzle_rest": to_data(muzzle),
        "bore_from_trunnion_blocks": round(float(trunnion[2] - tube_lo[2]) / 16, 4),
        "recoil_pixels": round(11.0 * SCALE, 3),
        "sight_top": to_data([(sight_lo[0] + sight_hi[0]) / 2, sight_hi[1], (sight_lo[2] + sight_hi[2]) / 2]),
        "hmg_mount_pivot": to_data(by["hmg_mount"]["pivot"]),
        "hmg_trunnion": to_data(hmg_trunnion),
        "hmg_muzzle_rest": to_data(hmg_muzzle),
        "tracks": {k: v for k, v in tracks.items() if k != "path"} | {
            "path_points": len(path), "path_length_px": round(total, 3), "link_pitch_px": round(total / LINKS, 3),
            "rest_off_path_px": round(rest_off, 3), "worst_distance_from_sbw_path_px": round(worst, 3)},
        "model_bounds": data_bounds(all_lo, all_hi),
        "hull_bounds": data_bounds(hull_lo, hull_hi),
        "turret_house_bounds": data_bounds(house_lo, house_hi),
        "hitbox": obb,
        "coplanar_face_pairs": {"in_source": found, "after_refit": len(L.coplanar_overlaps(geo))},
    }
    L.write_json(HERE / "rig.json", rig)

    # ---- Review renders: links on the path, the gun at 0 and 45 degrees, the roof gun turned.
    posed, link_pose = links_posed(geo, "left", path, link_rest, 0.0)
    posed, pose_r = links_posed(posed, "right", path, link_rest * np.array([-1, 1, 1]), 0.0)
    link_pose.update(pose_r)
    for e in (0.0, 45.0):
        sheet, _ = R.contact_sheet(posed, tex, f"PLZ-05 refit (scale {SCALE}), gun at {e:.0f} deg",
                                   "SBW plz_05, refit by design/plz05/refit.py; links on the computed path", "barrel",
                                   pose(e) | link_pose)
        sheet.save(HERE / f"review-elevation-{int(e)}.png", optimize=True)
    q, ts, _, _ = R.load_quads(posed, pose(10.0, 30.0, 15.0) | link_pose)
    R.render(R.visible(q, tex, ts), tex, ts, 35, 25, size=(1400, 1000)).save(HERE / "review-roof-gun.png", optimize=True)
    print(json.dumps({k: rig[k] for k in ("turret_origin", "trunnion", "muzzle_rest", "bore_from_trunnion_blocks",
                                          "sight_top", "hmg_mount_pivot", "hmg_trunnion", "hmg_muzzle_rest",
                                          "model_bounds", "coplanar_face_pairs")}))
    print(json.dumps(rig["tracks"]))
    for row in obb:
        print(row)
    if dry:
        return

    # ---- Into the mod.
    assets = L.ASSETS
    L.write_json(assets / f"geckolib/models/entity/vehicle/{ID}.geo.json", geo)
    L.write_json(assets / f"geckolib/models/entity/vehicle/lod/{ID}_lod1.geo.json", lod)
    tex.save(assets / f"textures/entity/vehicle/{ID}.png")
    shutil.copyfile(src / f"textures/vehicle_icon/{SBW_ID}_icon.png", assets / f"textures/vehicle_icon/{ID}_icon.png")
    # SBW's shoot clip is driven from the synced shots (the tube's recoil); ShellGunEntity plays "<id>.rest".
    L.write_json(assets / f"geckolib/animations/entity/vehicle/{ID}.animation.json",
                 {"format_version": "1.8.0", "animations": {f"animation.{ID}.rest": {"loop": True, "bones": {}}}})
    copies = [("plz_05_fire_3p", "fire"), ("plz_05_fire_1p", "fire_interior"), ("plz_05_far", "fire_distant"),
              ("plz_05_veryfar", "fire_very_far"), ("plz_05_reload", "load"), ("plz_05_engine", "engine")]
    for source, target in copies:
        out = assets / f"sounds/vehicle/{ID}/{target}.ogg"
        out.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src / f"sounds/vehicle/plz_05/{source}.ogg", out)
    (assets / f"sounds/vehicle/{ID}/CREDITS.md").write_text(
        "# PLZ-05 sounds\n\n"
        "By the Superb Warfare development team, used with their permission\n"
        "(`design/superbwarfare-port/PERMISSION.md`). Copied unchanged by `design/plz05/refit.py`:\n\n"
        "| File | SBW source |\n| --- | --- |\n"
        + "".join(f"| `{target}.ogg` | `vehicle/plz_05/{source}` |\n" for source, target in copies), encoding="utf-8")

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
