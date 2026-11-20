"""Audit and tighten the fleet's hitboxes against their models (the user, 2026-11-19: "check the hitboxes of all the
vehicles and ensure they are as tight as possible against their vehicle model").

python -I design/hitbox/audit_hitboxes.py            report only (design/hitbox/AUDIT.md)
python -I design/hitbox/audit_hitboxes.py --write    also rebuild the loose boxes in the vehicles' data

Each vehicle's hitbox is a list of boxes ("obb" in data/jeg/vehicles/<id>.json), axis-aligned in the vehicle's frame
(blocks, +Z forward), some following the turret or gun. They take bullets, carry players standing on the vehicle, push
entities and meet blocks. The model's visible faces (GeckoLib geometry at rest, faces whose texture is fully
transparent dropped) are sampled every half pixel. For every box:

- **fill**: the share of its volume within 0.12 blocks of the model's surface (a solid-ish box is mostly full, a box
  with air over a sloped deck is not);
- with --write, a loose box (under 60% fill, over 0.3 cubic blocks) is rebuilt: the model inside it, and any uncovered
  model within 0.75 blocks of it, is voxelized and filled solid, cut into the fewest boxes that hug it (greedy), and
  merged back to at most four (eight for a big box), each merge the one that takes in least air. The new boxes keep the old box's part and
  what it follows. A tight box is only shrunk to the model inside it, plus a small margin: shrinking only removes air.

And for the vehicle, **uncovered**: model surface no box holds (shots there pass through), by part. Thin parts that
would only fatten the boxes are left out on purpose: antennas, rotor blades and propellers (spinning discs), muzzle
flashes, glow helpers, and parts shown only in animations (the mortar's round). The FSV-6 (its slide-outs swap boxes
in code) and the BD-40 (its blade's boxes pinned by its own tests) are reported but not rewritten.
"""
import json
import math
import pathlib
import re
import sys

import numpy as np
from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "design/superbwarfare-port/triage"))
import render_geo  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/jeg"
DATA = ROOT / "src/main/resources/data/jeg/vehicles"
FLEET = ["bmp2", "lav150", "plz05", "yx100", "truck", "speedboat", "hpj11", "sentry", "tom6", "mortar", "mk42", "bl132",
         "tow", "type63", "howitzer", "a10", "ah6", "mi28", "bulldozer"]
SKIP = re.compile(r"antenna|aerial|whip|rotor|blade(?!_)|propeller|prop_|flash|glow|lens_flare", re.I)
HIDDEN = {"mortar": re.compile(r"^shell", re.I)}
# Not rewritten: the FSV-6 (slide-outs swap boxes in code), the BD-40 (blade boxes pinned by its tests) and the mortar
# (its tube box covers the tube over its whole 45-85 degree sweep, design/mortar/refit.py; the audit sees only rest).
# The AH-6 and Mi-28 too: re-cut, the Mi-28's tail-boom pieces sat on a post under the boom as it appeared and the
# block push-out launched it (HeliTakeoffSafety, 2026-11-19); their gain was small and none in coverage.
KEEP_AS_IS = {"truck", "bulldozer", "mortar", "ah6", "mi28"}
# Helicopters: what no box holds is mostly the main rotor (its blades named "wing" and "bone" in these models), which
# the hitbox leaves out on purpose; their boxes are only re-cut, never grown to take in uncovered parts.
NO_JOIN = {"ah6", "mi28"}
STEP = 1.0 / 32.0              # surface samples every half pixel
MARGIN = 0.04                  # blocks of slack round a box
NEAR = 0.12                    # a box's interior point counts as filled within this of the surface
LOOSE_FILL = 0.6
MAX_PIECES = 4
MAX_PIECES_BIG = 8
JOIN_REACH = 0.75


def surface_points(vehicle):
    geo_path = ASSETS / f"geckolib/models/entity/vehicle/{vehicle}.geo.json"
    tex = Image.open(ASSETS / f"textures/entity/vehicle/{vehicle}.png")
    quads, tex_size, _, _ = render_geo.load_quads(str(geo_path))
    quads = render_geo.visible(quads, tex, tex_size)
    pts, names = [], []
    for name, face, corners, uv in quads:
        if SKIP.search(name) or (vehicle in HIDDEN and HIDDEN[vehicle].search(name)):
            continue
        c = corners.copy()
        c[:, 0] *= -1.0                       # GeckoLib space -> file space
        c = c / 16.0
        c[:, 2] *= -1.0                       # file -Z forward -> data +Z forward
        a, b, d = c[0], c[1], c[3]
        u, v = b - a, d - a
        nu = min(400, max(1, int(math.ceil(np.linalg.norm(u) / STEP))))
        nv = min(400, max(1, int(math.ceil(np.linalg.norm(v) / STEP))))
        s = (np.arange(nu + 1) / nu)[:, None, None]
        t = (np.arange(nv + 1) / nv)[None, :, None]
        grid = (a[None, None] + s * u[None, None] + t * v[None, None]).reshape(-1, 3)
        pts.append(grid)
        names.extend([name] * len(grid))
    return np.concatenate(pts), np.array(names)


def box_bounds(row):
    c = np.array([row["x"], row["y"], row["z"]], float)
    h = np.array([row["half_width"], row["half_height"], row["half_depth"]], float)
    return c - h, c + h


def inside(points, lo, hi, tol=0.0):
    return np.all((points >= lo - tol) & (points <= hi + tol), axis=1)


def fill(points, lo, hi):
    from scipy.spatial import cKDTree
    sub = points[inside(points, lo, hi, NEAR)]
    if len(sub) == 0:
        return 0.0
    tree = cKDTree(sub)
    n = [max(2, int((hi[i] - lo[i]) / 0.1)) for i in range(3)]
    axes = [np.linspace(lo[i], hi[i], n[i]) for i in range(3)]
    grid = np.stack(np.meshgrid(*axes, indexing="ij"), -1).reshape(-1, 3)
    d, _ = tree.query(grid, distance_upper_bound=NEAR)
    return float(np.mean(np.isfinite(d)))


def solid_voxels(points, lo, voxel, shape):
    """Voxels the surface points occupy, with every voxel they enclose filled in."""
    from scipy import ndimage
    grid = np.zeros(shape, bool)
    idx = np.clip(np.floor((points - lo) / voxel).astype(int), 0, np.array(shape) - 1)
    grid[idx[:, 0], idx[:, 1], idx[:, 2]] = True
    grid |= ndimage.binary_closing(grid, iterations=1)
    return ndimage.binary_fill_holes(grid)


def greedy_boxes(solid):
    """Cover the solid voxels with boxes: grow each along x, then z, then y while all solid and unclaimed."""
    taken = np.zeros_like(solid)
    boxes = []
    nx, ny, nz = solid.shape
    for x, y, z in zip(*np.nonzero(solid)):
        if taken[x, y, z]:
            continue
        x1 = x
        while x1 + 1 < nx and solid[x1 + 1, y, z] and not taken[x1 + 1, y, z]:
            x1 += 1
        z1 = z
        while z1 + 1 < nz and np.all(solid[x:x1 + 1, y, z1 + 1]) and not np.any(taken[x:x1 + 1, y, z1 + 1]):
            z1 += 1
        y1 = y
        while y1 + 1 < ny and np.all(solid[x:x1 + 1, y1 + 1, z:z1 + 1]) and not np.any(taken[x:x1 + 1, y1 + 1, z:z1 + 1]):
            y1 += 1
        taken[x:x1 + 1, y:y1 + 1, z:z1 + 1] = True
        boxes.append([x, y, z, x1 + 1, y1 + 1, z1 + 1])
    return boxes


def merge_boxes(boxes, solid, limit):
    """Merge boxes pairwise, each time the pair whose joint bounding box takes in least air, to at most `limit`."""
    boxes = [list(b) for b in boxes]

    def air(b):
        return int(np.prod([b[3] - b[0], b[4] - b[1], b[5] - b[2]])) - int(solid[b[0]:b[3], b[1]:b[4], b[2]:b[5]].sum())

    airs = [air(b) for b in boxes]
    while len(boxes) > limit:
        best = None
        for i in range(len(boxes)):
            for j in range(i + 1, len(boxes)):
                a, b = boxes[i], boxes[j]
                m = [min(a[0], b[0]), min(a[1], b[1]), min(a[2], b[2]), max(a[3], b[3]), max(a[4], b[4]), max(a[5], b[5])]
                am = air(m)
                cost = am - airs[i] - airs[j]
                if best is None or cost < best[0]:
                    best = (cost, i, j, m, am)
        _, i, j, m, am = best
        boxes = [b for k, b in enumerate(boxes) if k not in (i, j)] + [m]
        airs = [a for k, a in enumerate(airs) if k not in (i, j)] + [am]
    return boxes


def rebuild(points, lo, hi):
    """Boxes (lo, hi) hugging the points, at most MAX_PIECES."""
    size = hi - lo
    voxel = float(np.clip(min(size) / 6.0, 0.0625, 0.25))
    glo = lo - voxel
    shape = tuple(int(math.ceil(s / voxel)) + 3 for s in size)
    solid = solid_voxels(points, glo, voxel, shape)
    greedy = greedy_boxes(solid)
    # A first coarse merge keeps the pairwise search small.
    while len(greedy) > 60:
        greedy = merge_boxes(greedy, solid, max(60, len(greedy) // 2))
    # A big box may keep more pieces (a wing and its pylons need their own), up to MAX_PIECES_BIG.
    limit = int(np.clip(round(float(np.prod(size)) / 3.0), MAX_PIECES, MAX_PIECES_BIG))
    pieces = merge_boxes(greedy, solid, limit)
    out = []
    for b in pieces:
        blo = glo + np.array(b[:3]) * voxel
        bhi = glo + np.array(b[3:]) * voxel
        held = inside(points, blo, bhi, 1e-6)
        if held.any():
            blo = np.maximum(blo, points[held].min(0) - MARGIN)
            bhi = np.minimum(bhi, points[held].max(0) + MARGIN)
            out.append((blo, bhi))
    return out


def audit(vehicle, write):
    path = DATA / f"{vehicle}.json"
    data = json.loads(path.read_text(encoding="utf-8"))
    rows = data.get("obb") or []
    points, names = surface_points(vehicle)
    bounds = [box_bounds(row) for row in rows]
    covered = np.zeros(len(points), bool)
    for lo, hi in bounds:
        covered |= inside(points, lo, hi, 1e-4)
    rewrite = write and vehicle not in KEEP_AS_IS
    # Uncovered model next to a box joins it (the nearest, within JOIN_REACH).
    extra = [np.zeros(len(points), bool) for _ in rows]
    loose = np.nonzero(~covered)[0]
    if rewrite and vehicle not in NO_JOIN and len(loose) and rows:
        dist = np.stack([np.linalg.norm(np.maximum(0, np.maximum(lo - points[loose], points[loose] - hi)), axis=1)
                         for lo, hi in bounds], 1)
        nearest = dist.argmin(1)
        close = dist.min(1) <= JOIN_REACH
        for k, i in zip(loose[close], nearest[close]):
            extra[i][k] = True
    # Nothing below where the old boxes stood: a box under the ground line lifts the vehicle onto it (the guide's
    # "clamp box bottoms to ground level"; the speedboat's hull runs 0.57 below its waterline).
    floor = min(lo[1] for lo, hi in bounds) if bounds else 0.0
    report, new_rows = [], []
    after_cover = np.zeros(len(points), bool)
    for row, (lo, hi), more in zip(rows, bounds, extra):
        held = inside(points, lo, hi, 1e-4)
        mine = held | more
        before = float(np.prod(hi - lo))
        f0 = fill(points, lo, hi)
        if rewrite and mine.any() and ((f0 < LOOSE_FILL and before > 0.3) or more.any()):
            pieces = rebuild(points[mine], np.minimum(lo, points[mine].min(0)), np.maximum(hi, points[mine].max(0)))
        elif held.any():
            pieces = [(np.maximum(lo, points[held].min(0) - MARGIN), np.minimum(hi, points[held].max(0) + MARGIN))]
        else:
            pieces = [(lo, hi)]
        pieces = [(np.array([tlo[0], max(tlo[1], floor), tlo[2]]), thi) for tlo, thi in pieces if thi[1] > floor + 0.05]
        after, fills = 0.0, []
        for tlo, thi in pieces:
            after += float(np.prod(thi - tlo))
            fills.append(fill(points, tlo, thi))
            after_cover |= inside(points, tlo, thi, 1e-4)
            new = dict(row)
            centre, half = (tlo + thi) / 2, (thi - tlo) / 2
            new.update({"x": round(float(centre[0]), 4), "y": round(float(centre[1]), 4), "z": round(float(centre[2]), 4),
                        "half_width": round(float(half[0]), 4), "half_height": round(float(half[1]), 4),
                        "half_depth": round(float(half[2]), 4)})
            new_rows.append(new)
        label = row.get("part", "?") + (f" ({len(pieces)} boxes)" if len(pieces) > 1 else "")
        report.append((label, row.get("follows", ""), before, after, f0, float(np.mean(fills)), int(held.sum())))
    uncovered = ~(after_cover if rewrite else covered)
    by_part = {}
    for n in names[uncovered]:
        by_part[n] = by_part.get(n, 0) + 1
    if rewrite and rows:
        text = path.read_text(encoding="utf-8")
        start = text.index('"obb": [')
        end = text.index("]", start) + 1
        lines = ["    {" + ", ".join(f'"{k}": {json.dumps(v)}' for k, v in row.items()) + "}" for row in new_rows]
        text = text[:start] + '"obb": [\n' + ",\n".join(lines) + "\n  ]" + text[end:]
        json.loads(text)
        path.write_text(text, encoding="utf-8")
    return report, by_part, len(points), int(uncovered.sum()), int((~covered).sum())


def main():
    write = "--write" in sys.argv
    only = [a for a in sys.argv[1:] if not a.startswith("--")]
    out = ["# Hitbox audit", "", "Generated by `design/hitbox/audit_hitboxes.py`" + (" --write (loose boxes rebuilt)" if write else "")
           + ". Volumes in cubic blocks; fill = share of the box within 0.12 blocks of the model's surface.", ""]
    for vehicle in only or FLEET:
        report, by_part, total, missed, missed_before = audit(vehicle, write)
        before = sum(r[2] for r in report)
        after = sum(r[3] for r in report)
        out += [f"## {vehicle}", "",
                f"volume {before:.2f} -> {after:.2f} ({100 * (1 - after / max(before, 1e-9)):.0f}% less); model surface "
                f"uncovered {100 * missed_before / max(total, 1):.1f}% -> {100 * missed / max(total, 1):.1f}%", "",
                "| box | follows | volume | after | fill | after | surface held |", "| --- | --- | --- | --- | --- | --- | --- |"]
        for part, follows, v0, v1, f0, f1, held in report:
            out.append(f"| {part} | {follows} | {v0:.2f} | {v1:.2f} | {f0:.0%} | {f1:.0%} | {held} |")
        if by_part:
            worst = sorted(by_part.items(), key=lambda kv: -kv[1])[:8]
            out += ["", "uncovered, by part: " + ", ".join(f"{k} {100 * v / total:.1f}%" for k, v in worst)]
        out.append("")
        print(vehicle, f"{before:.2f} -> {after:.2f}", f"uncovered {100 * missed_before / max(total, 1):.1f}% -> {100 * missed / max(total, 1):.1f}%")
    (HERE / "AUDIT.md").write_text("\n".join(out) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
