"""Refit of Superb Warfare's TOW launcher to the vehicle guide. The imported model's generator: rerun it after any
change.

python -I design/superbwarfare-port/fetch_sbw.py <sbw_dir> tow
python -I design/tow/refit.py <sbw_dir>

Writes the GeckoLib geometry, texture and the item icon into the mod, design/tow/rig.json, the OBB rows into
data/jeg/vehicles/tow.json, and review renders. Scale 1.0 (triage: about 1:1; the tube stands 1.2 blocks up, as on the
real M220 tripod).
"""
import json
import pathlib
import shutil
import sys

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "superbwarfare-port"))
import refit_lib as L  # noqa: E402
import render_geo as R  # noqa: E402

ID = "tow"
SBW_ID = "tow"
ELEVATION_MIN, ELEVATION_MAX = -40.0, 40.0  # SBW's

# +X is the gunner's left, -Z forward.
RENAME = {
    "tow": "root",
    "turret": "turret",            # the traversing unit
    "barrel": "barrel",            # elevation joint: the tube and its clamp
    "missile": "missile",          # the round in the tube, shown while one is loaded
    "bone7": "tube_middle",
    "bone8": "tube_collar",
    "bone9": "tube_front",
    "bone17": "tube_clamp",
    "guanmiao": "sight",           # the optical sight on the left of the tube, which the gunner looks through
    "bone11": "sight_body",
    "bone12": "sight_objective",
    "bone10": "sight_eyepiece",
    "bone3": "traverse_unit",
    "bone5": "elevation_plate",
    "bone6": "elevation_knob",
    "bone4": "traverse_head",
    "base": "tripod",
    "leg": "leg_right_front",
    "leg2": "leg_right_rear",
    "leg3": "leg_left",
    "bone": "mast",
    "bone13": "mast_collar",
    "bone14": "leg_hub_lower",
    "bone15": "leg_hub",
    "bone2": "mast_top",
}


def pose(elevation):
    return {"barrel": (-elevation, 0, 0)}


def to_data(point_px):
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

    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, texture)
    sub = lambda name: L.subtree(by, name)

    turret_pivot = by["turret"]["pivot"]
    trunnion = by["barrel"]["pivot"]
    tube_lo, tube_hi = L.extent(quads, sub("barrel"))
    front_lo, front_hi = L.extent(quads, sub("tube_front"))
    muzzle = [0.0, float((front_lo[1] + front_hi[1]) / 2), float(front_lo[2])]

    tripod_lo, tripod_hi = L.extent(quads, sub("tripod"))
    head_names = sub("turret") - sub("barrel")
    head_lo, head_hi = L.extent(quads, head_names)
    head_lo = head_lo.copy()
    head_lo[1] = max(head_lo[1], tripod_hi[1])
    parts = [("body", "tripod", tripod_lo, tripod_hi), ("body", "traversing unit and sight", head_lo, head_hi),
             ("turret", "tube", tube_lo, tube_hi)]
    obb = L.obb_rows(parts)
    for row in obb:
        if row["covers"] != "tripod":
            row["follows"] = "barrel" if row["covers"] == "tube" else "turret"

    all_lo, all_hi = L.extent(quads)
    rig = {
        "model": f"jeg:entity/vehicle/{ID}",
        "source": "Superb Warfare tow, CrawKatt/SuperbWarfareReFabricated@952d73c (used with the SBW team's permission)",
        "units_per_block": 16,
        "front": "-Z in the model, +Z in vehicle data",
        "scale_from_source": 1.0,
        "elevation_degrees": [ELEVATION_MIN, ELEVATION_MAX],
        "turret_origin": to_data(turret_pivot),
        "trunnion": to_data(trunnion),
        "barrel_offset_from_turret": [round(a - b, 4) for a, b in zip(to_data(trunnion), to_data(turret_pivot))],
        "muzzle_rest": to_data(muzzle),
        "tube_length_blocks": round(float(tube_hi[2] - tube_lo[2]) / 16, 3),
        "model_bounds": data_bounds(all_lo, all_hi),
        "hitbox": obb,
        "coplanar_face_pairs": {"in_source": found, "after_refit": len(L.coplanar_overlaps(geo))},
    }

    assets = L.ASSETS
    L.write_json(assets / f"geckolib/models/entity/vehicle/{ID}.geo.json", geo)
    shutil.copyfile(src / f"textures/entity/{SBW_ID}.png", assets / f"textures/entity/vehicle/{ID}.png")
    # The item icon: SBW's vehicle icon, brought to a 16 px item sprite.
    icon = Image.open(src / f"textures/vehicle_icon/{SBW_ID}_icon.png").convert("RGBA")
    if icon.size != (16, 16):
        box = icon.getbbox() or (0, 0, icon.width, icon.height)
        icon = icon.crop(box)
        side = max(icon.width, icon.height)
        square = Image.new("RGBA", (side, side))
        square.paste(icon, ((side - icon.width) // 2, (side - icon.height) // 2))
        icon = square.resize((16, 16), Image.Resampling.NEAREST)
    (assets / "textures/item").mkdir(parents=True, exist_ok=True)
    icon.save(assets / "textures/item/tow.png")
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

    for e in (0.0, ELEVATION_MAX):
        sheet, _ = R.contact_sheet(geo, texture, f"TOW refit, tube at {e:.0f} deg", "SBW tow, refit by design/tow/refit.py",
                                   "barrel", pose(e))
        sheet.save(HERE / f"review-elevation-{int(e)}.png", optimize=True)

    print(json.dumps({k: rig[k] for k in ("turret_origin", "trunnion", "barrel_offset_from_turret", "muzzle_rest",
                                          "tube_length_blocks", "model_bounds", "coplanar_face_pairs")}))
    for row in obb:
        print(row)


if __name__ == "__main__":
    main()
