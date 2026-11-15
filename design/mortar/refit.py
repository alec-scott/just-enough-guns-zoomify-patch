"""Refit of Superb Warfare's mortar to the vehicle guide. The imported model's generator: rerun it after any change.

python -I design/superbwarfare-port/fetch_sbw.py <sbw_dir> mortar
python -I design/mortar/refit.py <sbw_dir>

Writes the GeckoLib geometry, LOD, texture and clips into the mod, design/mortar/rig.json, and review renders. The
vehicle is not registered by this script.
"""
import pathlib
import shutil
import sys

import numpy as np
from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "superbwarfare-port"))
import refit_lib as L  # noqa: E402
import render_geo as R  # noqa: E402

ID = "mortar"
# 81 mm class (M252 / L16): tube 1.27 m. SBW's tube is 1.56 m, so 0.81 brings it to real size.
SCALE = 1.27 / 1.559
ELEVATION_MIN, ELEVATION_MAX = 45.0, 85.0  # real 81 mm mortars; SBW allowed 20-89

RENAME = {
    "mortar": "root",
    "paoguan": "barrel",        # elevation joint, driven like every jegn turret barrel
    "bone2": "tube_mount",      # turns the upright-authored tube to lie along -Z
    "shell": "shell",           # round shown being dropped in during the fire clip
    "paotong": "tube",
    "monitor": "fire_control",  # fire-control screen, shown when fitted
    "jiaojia": "bipod_pivot",   # bipod hinge on the tube collar; angle solved so the feet stay on the ground
    "bone": "bipod",
    "dizuo": "baseplate",
    "flare": "muzzle_flash",    # LOD only in the source
}
CLIPS = {"animation.mortar.idle": "animation.mortar.rest", "animation.mortar.fire": "animation.mortar.fire"}


def pose(elevation, bipod):
    # GeckoLib hands models the negated head pitch, so a raised tube is a negative file X rotation.
    return {"barrel": (-elevation, 0, 0), "bipod_pivot": (bipod, 0, 0)}


def bipod_for(geo, texture, elevation):
    """Bipod hinge angle that puts the lowest foot on the ground (y = 0), by bisection."""
    def foot_y(angle):
        quads, _ = L.posed_quads(geo, pose(elevation, angle), texture)
        return L.extent(quads, {"bipod"})[0][1]
    samples = np.linspace(-60.0, 220.0, 113)
    heights = [foot_y(a) for a in samples]
    # The feet cross the ground twice as the bipod swings; take the crossing nearest SBW's own estimate.
    estimate = 2.2 * elevation - 20
    crossings = [(a, b) for a, b, ya, yb in zip(samples, samples[1:], heights, heights[1:]) if (ya > 0) != (yb > 0)]
    if not crossings:
        # Near vertical the legs are too short to reach the ground (a real mortar runs out its elevating screw):
        # take the lowest reach and report the gap.
        best = min(range(len(samples)), key=lambda i: heights[i])
        a, b = samples[max(0, best - 1)], samples[min(len(samples) - 1, best + 1)]
        for _ in range(40):
            m1, m2 = a + (b - a) / 3, b - (b - a) / 3
            if foot_y(m1) < foot_y(m2):
                b = m2
            else:
                a = m1
        angle = (a + b) / 2
        return round(angle, 3), round(float(foot_y(angle)) / 16, 4)
    lo, hi = min(crossings, key=lambda ab: abs((ab[0] + ab[1]) / 2 - estimate))
    for _ in range(40):
        mid = (lo + hi) / 2
        if (foot_y(lo) > 0) == (foot_y(mid) > 0):
            lo = mid
        else:
            hi = mid
    return round((lo + hi) / 2, 3), 0.0


def main():
    src = pathlib.Path(sys.argv[1])
    texture = Image.open(src / "textures/entity/mortar.png")
    texture.load()

    geo = L.load_json(src / "geo/mortar.geo.json")
    face = L.to_face_uv(geo)
    if not L.render_identical(geo, face, texture):
        raise SystemExit("per-face UV rewrite changed the rendering")
    found = len(L.coplanar_overlaps(face))
    geo = L.resolve_zfighting(L.scale(L.rename_bones(face, RENAME, "geometry.jeg.mortar"), SCALE))
    lod = L.scale(L.rename_bones(L.to_face_uv(L.load_json(src / "geo/vehicle_lod/mortar_lod1.geo.json")), RENAME,
                                 "geometry.jeg.mortar_lod1"), SCALE)
    clips = L.scale_animations(L.rename_animations(L.load_json(src / "animations/mortar.animation.json"), RENAME, CLIPS),
                               SCALE)

    # Bipod table: the hinge angle per elevation, so the renderer keeps the feet planted.
    table, gaps = {}, {}
    for e in range(int(ELEVATION_MIN), int(ELEVATION_MAX) + 1, 5):
        table[str(e)], gap = bipod_for(geo, texture, e)
        if gap:
            gaps[str(e)] = gap

    # Hitbox: the baseplate, and the tube with bipod swept across the whole elevation range.
    sweep = []
    for e, angle in table.items():
        quads, _ = L.posed_quads(geo, pose(float(e), angle), texture)
        sweep.append(L.extent(quads, L.subtree({b["name"]: b for b in L.bones_of(geo)}, "barrel") - {"shell"}))
        rest_quads = quads
    tube_lo = np.min([s[0] for s in sweep], 0)
    tube_hi = np.max([s[1] for s in sweep], 0)
    base_lo, base_hi = L.extent(rest_quads, {"baseplate"})
    obb = L.obb_rows([("body", "baseplate", base_lo, base_hi), ("turret", "tube and bipod, 45-85 deg", tube_lo, tube_hi)])

    # Points the game needs, in the tube's own frame (rest pose) and at the extremes.
    by = {b["name"]: b for b in L.bones_of(geo)}
    tube_q, _ = L.posed_quads(geo, None, texture)
    tube_pts = np.concatenate([q[2] for q in tube_q if q[0] == "tube"])
    pivot = np.array(by["barrel"]["pivot"], float)
    far = tube_pts[np.argmax(np.linalg.norm(tube_pts - pivot, axis=1))]
    muzzle_rest = [round(float(v), 3) for v in (0.0, far[1], tube_pts[:, 2].min())]
    # muzzle_rest is in the rest model frame; only the barrel joint (and root) move it.
    muzzle = {str(int(e)): L.transform_point(geo, "barrel", muzzle_rest, pose(e, 0)) for e in (ELEVATION_MIN, ELEVATION_MAX)}

    all_q, _ = L.posed_quads(geo, pose(ELEVATION_MIN, table[str(int(ELEVATION_MIN))]), texture)
    lo, hi = L.extent(all_q)
    overlaps = L.coplanar_overlaps(geo)
    rig = {
        "model": "jeg:entity/vehicle/mortar",
        "source": "Superb Warfare mortar, CrawKatt/SuperbWarfareReFabricated@952d73c (used with the SBW team's permission)",
        "units_per_block": 16,
        "front": "-Z (fires towards -Z at yaw 0)",
        "scale_from_source": round(SCALE, 5),
        "tube_length_blocks": round(float(np.ptp(tube_pts @ np.array([0, 0, 1.0]))) / 16, 3),
        "elevation_degrees": [ELEVATION_MIN, ELEVATION_MAX],
        "elevation_pivot": [round(float(v), 4) for v in pivot],
        "elevation_sign": "barrel bone file X rotation = -elevation (GeckoLib setRotX(+elevation))",
        "muzzle_rest": muzzle_rest,
        "muzzle_at_elevation": muzzle,
        "bipod_pivot_rest": by["bipod_pivot"]["pivot"],
        "bipod_angle_by_elevation": table,
        "bipod_note": "file X rotation of bipod_pivot; interpolate linearly; renderer sets setRotX(-angle)",
        "bipod_foot_gap_blocks": gaps,
        "shell_drop_pixels": round(20 * SCALE, 3),
        "model_bounds_at_min_elevation": [[round(float(v), 3) for v in lo], [round(float(v), 3) for v in hi]],
        "hitbox": obb,
        "coplanar_face_pairs": {"in_source": found, "after_refit": len(overlaps)},
    }

    assets = L.ASSETS
    L.write_json(assets / f"geckolib/models/entity/vehicle/{ID}.geo.json", geo)
    L.write_json(assets / f"geckolib/models/entity/vehicle/lod/{ID}_lod1.geo.json", lod)
    L.write_json(assets / f"geckolib/animations/entity/vehicle/{ID}.animation.json", clips)
    shutil.copyfile(src / "textures/entity/mortar.png", assets / f"textures/entity/vehicle/{ID}.png")
    L.write_json(HERE / "rig.json", rig)

    for e in (ELEVATION_MIN, ELEVATION_MAX):
        sheet, _ = R.contact_sheet(geo, texture, f"Mortar refit at {e:.0f} deg (scale {SCALE:.3f})",
                                   "SBW mortar, refit by design/mortar/refit.py", "tube", pose(e, table[str(int(e))]))
        sheet.save(HERE / f"review-elevation-{int(e)}.png", optimize=True)

    print(f"tube {rig['tube_length_blocks']} blocks; bipod {table}; hitbox {obb}; coplanar pairs {len(overlaps)}")


if __name__ == "__main__":
    main()
