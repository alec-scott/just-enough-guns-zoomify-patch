"""Refit of Superb Warfare's "Sodayo" pickup and its three technicals to the vehicle guide. The imported models'
generator: rerun it after any change.

python -I design/superbwarfare-port/fetch_sbw.py <sbw_dir> --pin 2026-11 sodayo_pick_up sodayo_pick_up_hmg \
    sodayo_pick_up_rocket sodayo_pick_up_tow
python -I design/pickup/refit.py <sbw_dir> [--dry]

Writes, for each of pickup, pickup_hmg, pickup_rocket and pickup_tow: the GeckoLib geometry, two LODs, the texture
(rebaked per face and weathered) into the mod, the OBB rows and wheel block into data/jeg/vehicles/<id>.json, and
design/pickup/rig.json plus review renders. --dry writes only rig.json and the renders.

Scale 0.76, by length: SBW's truck is 6.84 blocks long against the 5.22 m of the single-cab 4x4 pickup it draws (the
Land Cruiser 70 class technicals are built on); at 0.76 the wheelbase is 3.13 m (3.18 real), the tyres 0.77 m and the
body 1.81 m wide.

Taken out, as the port's scope rules out SBW's memes (design/superbwarfare-port/PLAN.md): the "Beast Senpai" figurine on
the dashboard, the "Sodayo!" door decals and the "SODAYO" tailgate lettering; the grille badge is repainted as a plain
one and the 114514 plates as plain number plates. The dog-tag locator and the muzzle flash planes go too (jegn draws
its own flashes, guide section 6).
"""
import collections
import json
import pathlib
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE.parent / "superbwarfare-port"))
sys.path.insert(0, str(HERE.parent / "ground-vehicles"))
import refit_lib as L  # noqa: E402
import render_geo as R  # noqa: E402
import detail_texture as D  # noqa: E402

SCALE = 0.76
VARIANTS = {
    "pickup": "sodayo_pick_up",
    "pickup_hmg": "sodayo_pick_up_hmg",
    "pickup_rocket": "sodayo_pick_up_rocket",
    "pickup_tow": "sodayo_pick_up_tow",
}
ELEVATION = {"pickup_hmg": (-30.0, 60.0), "pickup_rocket": (10.0, 60.0), "pickup_tow": (-20.0, 20.0)}
TUBES = 12

DROPPED = {"senpai", "body_dogTag_9x9", "flare"}
NAMED = {
    "root": "root", "body": "body", "icon": "badges",
    # The cab (车头).
    "chetou": "cab", "qianchuang": "windscreen", "houchuang": "rear_window", "left2": "cab_side_left",
    "leftdoor": "door_left", "right2": "cab_side_right", "rightdoor": "door_right", "top": "cab_top", "gai": "bonnet",
    "front2": "front_end", "bottom": "cab_floor", "mid": "engine_bay", "midleft": "fender_front_left",
    "midright": "fender_front_right", "inner": "cab_interior", "move_control": "steering_wheel", "zuowei": "seats",
    # The bed (斗) and chassis (底盘).
    "dou": "bed", "dipan": "chassis", "left": "bed_side_left", "bone": "wheel_arch_rear_left", "right": "bed_side_right",
    "bone2": "wheel_arch_rear_right", "back": "tailgate", "front": "bed_front", "chest": "bed_crate",
    "qianlunzu": "axle_front", "houlunzu": "axle_rear",
    "Wheel": "running_gear", "wheelL0Turn": "wheel_front_left", "wheelR0Turn": "wheel_front_right",
    "wheelL1": "wheel_rear_left", "wheelR1": "wheel_rear_right",
    # The 12.7 mm on its post (机枪, 柱, 枪管).
    "jiqiang": "hmg_mount", "zhu": "hmg_post", "turret": "turret", "barrel": "barrel", "m2hb": "hmg", "mg": "hmg_body",
    "qiangguan": "hmg_barrel", "action": "hmg_action",
    # The Type 63 pod (火箭炮), named as the towed Type-63's (design/type63/refit.py).
    "hjp": "rocket_mount", "side": "launcher_side", "wakong": "launcher_cutout", "front3": "launcher_front",
    "back2": "launcher_back", "j1": "launcher_front_bracket_1", "j2": "launcher_front_bracket_2",
    "j3": "launcher_front_bracket_3", "rollx": "elevation_axle", "paojia": "cradle", "chilunxiang": "gearbox",
    "shoulunx": "handwheel_elevation", "shoulunpanx": "handwheel_elevation_rim", "rolly": "traverse_ring",
    "tube": "tube_1", **{f"tube{i}": f"tube_{i}" for i in range(2, 13)},
    **{f"shell{i}": f"rocket_{i + 1}" for i in range(TUBES)},
    # The TOW on its tripod (瞄 = sight).
    "tow": "tow_mount", "missile": "missile", "move_guanmiao": "sight", "tow_base": "tripod",
    "leg": "tripod_leg_1", "leg2": "tripod_leg_2", "leg3": "tripod_leg_3",
}
WHEELS = ["wheel_front_left", "wheel_front_right", "wheel_rear_left", "wheel_rear_right"]
# Faces of the badges bone taken out, by the texture region they show (SBW's 512 px sheet).
DECALS_OUT = [(108, 132), (0, 125)]  # the doors' "Sodayo!", the tailgate's "SODAYO"
BADGE = (60, 125, 101, 166)
PLATE = (0, 167, 256, 256)
FONT = ROOT / "design/hud/Inter-SemiBold.ttf"
DETAIL = dict(upscale=2, mud_height=12.0, mud=0.5, wear=0.5, paint_lum=0.97, paint_sat=0.4, bolts=False,
              skip="glass|window|windscreen|lens|lamp|light|glow|flash|sight|badges")


def rename_map(geo):
    bones = L.bones_of(geo)
    mapping = {b["name"]: NAMED[b["name"]] for b in bones if b["name"] in NAMED}
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


def strip(raw):
    """Drop the meme figurine, the locator and flash planes, and the lettering decals."""
    geo = json.loads(json.dumps(raw))
    bones = L.bones_of(geo)
    by = {b["name"]: b for b in bones}
    gone = set()
    for name in DROPPED:
        if name in by:
            gone |= L.subtree(by, name)
    kept = [b for b in bones if b["name"] not in gone]
    for b in kept:
        if b["name"] != "icon":
            continue
        cubes = []
        for c in b.get("cubes", []):
            faces = c.get("uv") or {}
            if any(isinstance(f, dict) and tuple(f.get("uv", ())[:2]) in DECALS_OUT for f in faces.values()):
                continue
            cubes.append(c)
        b["cubes"] = cubes
    geo["minecraft:geometry"][0]["bones"] = kept
    return geo


def repaint_decals(texture):
    """The grille badge as a plain chrome oval; the plates as plain plates with a made-up number."""
    tex = texture.copy()
    d = ImageDraw.Draw(tex)
    x0, y0, x1, y1 = BADGE
    d.rectangle((x0, y0, x1 - 1, y1 - 1), fill=(0, 0, 0, 0))
    d.ellipse((x0 + 2, y0 + 8, x1 - 3, y1 - 9), fill=(70, 72, 74, 255), outline=(196, 198, 200, 255), width=3)
    d.line((x0 + 9, (y0 + y1) // 2, x1 - 10, (y0 + y1) // 2), fill=(176, 178, 180, 255), width=3)
    x0, y0, x1, y1 = PLATE
    d.rectangle((x0, y0, x1 - 1, y1 - 1), fill=(226, 224, 214, 255))
    d.rectangle((x0 + 3, y0 + 3, x1 - 4, y1 - 4), outline=(34, 34, 36, 255), width=4)
    font = ImageFont.truetype(str(FONT), 58)
    text = "4071 KD"
    w = d.textlength(text, font=font)
    d.text(((x0 + x1 - w) / 2, y0 + 12), text, font=font, fill=(28, 28, 30, 255))
    return tex


def to_data(point_px):
    return [round(float(point_px[0]) / 16, 4), round(float(point_px[1]) / 16, 4), round(-float(point_px[2]) / 16, 4) + 0.0]


def data_bounds(lo_px, hi_px):
    a, b = to_data(lo_px), to_data(hi_px)
    return [[min(x, y) for x, y in zip(a, b)], [max(x, y) for x, y in zip(a, b)]]


TOW_RAISE = 8.0  # px: the TOW on a pedestal, half a block up
BED_FLOOR = 9.74  # px: the bed floor (the chassis top, 0.801 x 0.76 blocks in SBW's model)


def raise_tow(geo):
    """SBW stands the TOW's tripod on the bed floor, so its tube (1.54 blocks up) aims into the cab's roof (1.8): a
    level shot ahead hits the cab and the gunner's sight looks at the rear window. Technicals carry it on a pedestal:
    the launcher goes half a block up onto a steel stand, painted as the tripod, and clears the roof level."""
    geo = json.loads(json.dumps(geo))
    by = {b["name"]: b for b in L.bones_of(geo)}
    for name in L.subtree(by, "tow_mount"):
        b = by[name]
        if "pivot" in b:
            b["pivot"][1] = round(b["pivot"][1] + TOW_RAISE, 5)
        for c in b.get("cubes", []):
            c["origin"][1] = round(c["origin"][1] + TOW_RAISE, 5)
            if "pivot" in c:
                c["pivot"][1] = round(c["pivot"][1] + TOW_RAISE, 5)
    tripod = [c for n in sorted(L.subtree(by, "tripod")) for c in by[n].get("cubes", [])]
    leg = max(tripod, key=lambda c: c["size"][0] * c["size"][1] * c["size"][2])
    floor = BED_FLOOR
    px, _, pz = by["tow_mount"]["pivot"]
    faces = {face: dict(next(iter(leg["uv"].values()))) for face in ("north", "south", "east", "west", "up", "down")}
    by["tow_mount"].setdefault("cubes", []).extend([
        # the column and a base plate bolted to the bed
        {"origin": [round(px - 2.0, 5), round(floor, 5), round(pz - 2.0, 5)], "size": [4.0, TOW_RAISE, 4.0], "uv": faces},
        {"origin": [round(px - 4.5, 5), round(floor, 5), round(pz - 4.5, 5)], "size": [9.0, 0.6, 9.0],
         "uv": json.loads(json.dumps(faces))},
        # the table the tripod's feet stand on
        {"origin": [round(px - 5.0, 5), round(floor + TOW_RAISE - 0.6, 5), round(pz - 5.0, 5)], "size": [10.0, 0.6, 10.0],
         "uv": json.loads(json.dumps(faces))},
    ])
    return geo


def make_lod(geo, level, identifier):
    """Thin fine fittings at distance; keep every bone so the driver still finds the articulated ones."""
    out = json.loads(json.dumps(geo))
    out["minecraft:geometry"][0]["description"]["identifier"] = identifier
    limit = 0.8 if level == 1 else 2.0
    for b in L.bones_of(out):
        cubes = b.get("cubes", [])
        if b["name"].startswith("wheel_"):
            b["cubes"] = sorted(cubes, key=lambda c: -c["size"][0] * c["size"][1] * c["size"][2])[: (6 if level == 1 else 2)]
        else:
            b["cubes"] = [c for c in cubes if max(c["size"]) >= limit]
    return out


def json_rows(obb):
    rows = []
    for row in obb:
        keys = ["part", "x", "y", "z", "half_width", "half_height", "half_depth"] + (["follows"] if "follows" in row else [])
        rows.append("    {" + ", ".join(f'"{k}": {json.dumps(row[k] if isinstance(row[k], str) else float(row[k]))}' for k in keys) + "}")
    return rows


def front_centre(quads, names):
    lo, hi = L.extent(quads, names)
    return [(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, lo[2]]


def refit_variant(src, vid, sbw_id, dry):
    texture = Image.open(src / f"textures/entity/{sbw_id}.png").convert("RGBA")
    raw = L.load_json(src / f"geo/{sbw_id}.geo.json")
    face = L.to_face_uv(raw)
    if not L.render_identical(raw, face, texture):
        raise SystemExit(f"{vid}: per-face UV rewrite changed the rendering")
    found = len(L.coplanar_overlaps(face))
    stripped = strip(face)
    named = L.rename_bones(stripped, rename_map(stripped), f"geometry.jeg.{vid}")
    geo = L.scale(named, SCALE)
    if vid == "pickup_tow":
        geo = raise_tow(geo)
    geo = L.resolve_zfighting(geo)
    painted = repaint_decals(texture)
    geo, baked, _ = L.rebake_imported(geo, painted, texels=2, boost={"badges": 6})
    detailed, _, _ = D.detail_image(geo, baked, DETAIL)

    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, detailed)
    sub = lambda name: L.subtree(by, name) if name in by else set()

    wheels = {}
    for name in WHEELS:
        lo, hi = L.extent(quads, sub(name))
        wheels[name] = {"centre": to_data([(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2]),
                        "radius_px": round(float(hi[1] - lo[1]) / 2, 3)}
    radius = round(float(np.mean([w["radius_px"] for w in wheels.values()])), 3)
    wheelbase = round(abs(wheels["wheel_front_left"]["centre"][2] - wheels["wheel_rear_left"]["centre"][2]), 4)
    wheel_block = {"roll": [{"bones": WHEELS, "radius": radius}], "steer": ["wheel_front_left", "wheel_front_right"],
                   "wheelbase": wheelbase}

    # Hitbox: cab, bed, each side's wheels, the engine under the bonnet, and the weapon turning with its mount.
    weapon = {"pickup_hmg": "hmg_mount", "pickup_rocket": "rocket_mount", "pickup_tow": "tow_mount"}.get(vid)
    body_names = sub("body") - (sub(weapon) if weapon else set()) - sub("badges") - sub("bed_crate")
    cab_lo, cab_hi = L.extent(quads, sub("cab"))
    bed_lo, bed_hi = L.extent(quads, sub("bed"))
    eng_lo, eng_hi = L.extent(quads, sub("engine_bay") | sub("bonnet"))
    wl_lo, wl_hi = L.extent(quads, sub("wheel_front_left") | sub("wheel_rear_left"))
    wr_lo, wr_hi = L.extent(quads, sub("wheel_front_right") | sub("wheel_rear_right"))
    cab_lo = cab_lo.copy()
    cab_lo[1] = max(cab_lo[1], 0.55 * float(wl_hi[1]))
    bed_lo = bed_lo.copy()
    bed_lo[1] = max(bed_lo[1], 0.55 * float(wl_hi[1]))
    parts = [("body", "cab", cab_lo, cab_hi), ("body", "bed", bed_lo, bed_hi),
             ("main_engine", "engine under the bonnet", eng_lo, eng_hi),
             ("wheel_left", "left wheels", wl_lo, wl_hi), ("wheel_right", "right wheels", wr_lo, wr_hi)]
    rig = {"model": f"jeg:entity/vehicle/{vid}",
           "source": f"Superb Warfare {sbw_id}, CrawKatt/SuperbWarfareReFabricated@c5189d1 (used with the SBW team's permission)",
           "units_per_block": 16, "front": "-Z in the model, +Z in vehicle data", "scale_from_source": SCALE,
           "wheels": wheels, "wheel_block": wheel_block}
    if weapon:
        turret_pivot = np.array(by["turret"]["pivot"], float)
        trunnion = np.array(L.bone_pivot(geo, "barrel"), float)
        rig["elevation_degrees"] = list(ELEVATION[vid])
        rig["turret_origin"] = to_data(turret_pivot)
        rig["trunnion"] = to_data(trunnion)
        rig["barrel_offset_from_turret"] = [round(a - b, 4) for a, b in zip(to_data(trunnion), to_data(turret_pivot))]
        mount_lo, mount_hi = L.extent(quads, sub(weapon) - sub("turret"))
        head_lo, head_hi = L.extent(quads, sub("turret"))
        parts += [("body", "weapon mount", mount_lo, mount_hi), ("turret", "weapon", head_lo, head_hi)]
        if vid == "pickup_hmg":
            rig["muzzle_rest"] = to_data(front_centre(quads, sub("hmg_barrel")))
        elif vid == "pickup_tow":
            rig["muzzle_rest"] = to_data(front_centre(quads, sub("barrel")))
            rig["sight_rest"] = to_data(L.bone_pivot(geo, "sight"))
        else:
            muzzles = []
            for i in range(1, TUBES + 1):
                lo, hi = L.extent(quads, sub(f"rocket_{i}"))
                muzzles.append(to_data([(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, lo[2]]))
            rig["muzzles_in_firing_order"] = muzzles
            rig["handwheel_elevation_pivot"] = to_data(L.bone_pivot(geo, "handwheel_elevation"))
    obb = L.obb_rows(parts)
    for row in obb:
        if row["covers"] == "weapon":
            row["follows"] = "turret"
    all_lo, all_hi = L.extent(quads)
    rig.update({"model_bounds": data_bounds(all_lo, all_hi), "cab_bounds": data_bounds(cab_lo, cab_hi),
                "bed_bounds": data_bounds(bed_lo, bed_hi),
                "steering_wheel": to_data(L.bone_pivot(geo, "steering_wheel")),
                "door_left_hinge": to_data(L.bone_pivot(geo, "door_left")),
                "door_right_hinge": to_data(L.bone_pivot(geo, "door_right")),
                "hitbox": obb, "texture_size": list(detailed.size),
                "coplanar_face_pairs": {"in_source": found, "after_refit": len(L.coplanar_overlaps(geo))}})

    pose = {"barrel": (-20.0, 0, 0)} if weapon else None
    sheet, _ = R.contact_sheet(geo, detailed, f"{vid} refit (scale {SCALE})", f"SBW {sbw_id}, design/pickup/refit.py",
                               "barrel" if weapon else "body", pose)
    sheet.save(HERE / f"review-{vid}.png", optimize=True)
    if not dry:
        assets = L.ASSETS
        L.write_json(assets / f"geckolib/models/entity/vehicle/{vid}.geo.json", geo)
        for level in (1, 2):
            L.write_json(assets / f"geckolib/models/entity/vehicle/lod/{vid}_lod{level}.geo.json",
                         make_lod(geo, level, f"geometry.jeg.{vid}_lod{level}"))
        detailed.save(assets / f"textures/entity/vehicle/{vid}.png", optimize=True)
        L.write_json(assets / f"geckolib/animations/entity/vehicle/{vid}.animation.json",
                     {"format_version": "1.8.0", "animations": {f"animation.{vid}.{clip}": {"loop": True, "bones": {}} for clip in ("idle", "rest")}})
        data_path = ROOT / f"src/main/resources/data/jeg/vehicles/{vid}.json"
        if data_path.exists():
            data = data_path.read_text(encoding="utf-8")
            start = data.index('  "obb": [')
            end = data.index("],", start) + 2
            data = data[:start] + '  "obb": [\n' + ",\n".join(json_rows(obb)) + "\n  ]," + data[end:]
            start = data.index('  "wheels": ')
            end = data.index("\n", start) + 1
            data = data[:start] + '  "wheels": ' + json.dumps(wheel_block) + ",\n" + data[end:]
            data_path.write_text(data, encoding="utf-8")
    return rig


def main():
    src = pathlib.Path(sys.argv[1])
    dry = "--dry" in sys.argv
    only = [a for a in sys.argv[2:] if not a.startswith("--")]
    rigs = {}
    for vid, sbw_id in VARIANTS.items():
        if only and vid not in only:
            continue
        rigs[vid] = refit_variant(src, vid, sbw_id, dry)
        r = rigs[vid]
        print(vid, json.dumps({k: r[k] for k in r if k not in ("hitbox", "wheels", "source", "model")}))
    if not only:
        L.write_json(HERE / "rig.json", rigs)


if __name__ == "__main__":
    main()
