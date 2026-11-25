"""The CH-47's interior surfaces painted in detail (the user, 2026-11-21: "add more detailed textures inside the heli"):
generate.py's box faces through refit_lib.bake_unique's `fill`, at BOOST times the base texel density.

Every pattern is laid out in the model's own coordinates (model pixels, a = the face's first axis, b = its second,
refit_lib.FACE_SIZE), so a pattern carries on across the pieces a wall is built from (the lining broken round the
windows, the floor's strips).

- quilt: the insulation blankets on the cabin's walls and ceiling: padded panels 6 x 4 px (37 x 25 cm), stitched
  seams with their thread, press-stud fasteners at the corners, a canvas weave, a few stains;
- floor: the cargo floor's aluminium plates, their screw rows, tie-down rings every 1.25 m, non-skid grit and scuffs;
- tread: the treadway's rollers between two rails;
- frame: painted ribs with rivet rows down both edges and lightening holes;
- cable: the cable and hydraulic runs, a bundle of cables held by clamps;
- webbing: red nylon seat webbing, woven, stitched at the edges;
- screen: the hook well's grate;
- metal: brushed;
- panel: screwed panels with an inset edge.
"""
from __future__ import annotations

import numpy as np

BOOST = {"quilt": 4, "floor": 4, "tread": 4, "frame": 3, "webbing": 4, "cable": 3, "screen": 4, "metal": 2, "panel": 2}


def _hash(ix, iy, seed):
    with np.errstate(over="ignore"):
        h = (np.asarray(ix, np.int64).astype(np.uint64) * np.uint64(73856093)) ^ (
            np.asarray(iy, np.int64).astype(np.uint64) * np.uint64(19349663)) ^ np.uint64(seed * 83492791 + 1)
        h = (h ^ (h >> np.uint64(13))) * np.uint64(1274126177)
        return ((h ^ (h >> np.uint64(16))) & np.uint64(0xFFFF)).astype(np.float64) / 65535.0


def _noise(a, b, scale, seed=0):
    """Smooth value noise, 0..1, a cell every `scale` model pixels."""
    x, y = a / scale, b / scale
    ix, iy = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64)
    fx, fy = x - ix, y - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    n00, n10 = _hash(ix, iy, seed), _hash(ix + 1, iy, seed)
    n01, n11 = _hash(ix, iy + 1, seed), _hash(ix + 1, iy + 1, seed)
    return (n00 * (1 - fx) + n10 * fx) * (1 - fy) + (n01 * (1 - fx) + n11 * fx) * fy


def _fbm(a, b, scale, seed=0):
    return 0.55 * _noise(a, b, scale, seed) + 0.3 * _noise(a, b, scale / 2, seed + 7) + 0.15 * _noise(a, b, scale / 4, seed + 13)


def _clamp01(x):
    return np.clip(x, 0.0, 1.0)


def _line(d, width):
    """1 on a line of `width` model pixels (d its distance), soft over a tenth of a pixel."""
    return _clamp01((width / 2 + 0.06 - np.abs(d)) / 0.12)


def _edge_dist(x, pitch):
    f = np.mod(x, pitch)
    return np.minimum(f, pitch - f)


def quilt(a, b, base):
    pa, pb = 6.0, 4.0
    da, db = _edge_dist(a, pa), _edge_dist(b, pb)
    pad = np.minimum(da, db)
    # Each pad a little different: the blankets sag and fade unevenly.
    tone = 0.94 + 0.12 * _hash(np.floor(a / pa), np.floor(b / pb), 3)
    pillow = 0.78 + 0.22 * np.sqrt(_clamp01(pad / 1.3))
    lum = tone * pillow * (0.97 + 0.06 * _fbm(a, b, 2.0, 5))
    weave = 0.035 * (np.sign(np.sin(a * 40.0)) * np.sign(np.sin(b * 40.0)))
    col = base[None, None, :] * (lum + weave)[..., None]
    seam = np.maximum(_line(da, 0.16), _line(db, 0.16))
    col = col * (1 - 0.45 * seam)[..., None]
    # The thread: short pale stitches either side of each seam.
    along_a = (np.mod(b, 0.45) < 0.2) & (np.abs(da - 0.22) < 0.05)
    along_b = (np.mod(a, 0.45) < 0.2) & (np.abs(db - 0.22) < 0.05)
    col[along_a | along_b] = col[along_a | along_b] * 0.6 + np.array([190.0, 186.0, 160.0]) * 0.4
    # Press-stud fasteners where four pads meet.
    stud = np.hypot(da, db)
    col[stud < 0.32] = np.array([46.0, 48.0, 44.0])
    col[stud < 0.16] = np.array([150.0, 152.0, 146.0])
    # A few old stains.
    stain = _clamp01((_fbm(a, b, 9.0, 11) - 0.66) * 6.0)
    return col * (1 - 0.18 * stain)[..., None]


def floor(a, b, base):
    # Plates 12 px each way, screwed along their edges.
    da, db = _edge_dist(a, 12.0), _edge_dist(b, 12.0)
    col = base[None, None, :] * (0.93 + 0.1 * _hash(np.floor(a / 12.0), np.floor(b / 12.0), 17))[..., None]
    col = col * (0.95 + 0.08 * _fbm(a, b, 1.2, 19))[..., None]                   # non-skid grit
    seam = np.maximum(_line(da, 0.14), _line(db, 0.14))
    col = col * (1 - 0.5 * seam)[..., None]
    screws = ((np.abs(da - 0.45) < 0.12) & (np.abs(np.mod(b, 1.5) - 0.75) < 0.12)) | \
             ((np.abs(db - 0.45) < 0.12) & (np.abs(np.mod(a, 1.5) - 0.75) < 0.12))
    col[screws] = np.array([128.0, 130.0, 124.0])
    # Tie-down rings every 1.25 m, each in its recessed cup.
    ra, rb = np.mod(a + 10.0, 20.0) - 10.0, np.mod(b + 10.0, 20.0) - 10.0
    r = np.hypot(ra, rb)
    col[r < 1.0] = col[r < 1.0] * 0.45
    ring = np.abs(r - 0.62) < 0.13
    col[ring] = np.array([150.0, 150.0, 140.0])
    # Scuffs where the loads go in and out.
    scuff = _clamp01((_fbm(a * 0.6, b * 0.15, 3.0, 23) - 0.58) * 5.0)
    return col * (1 - 0.2 * scuff)[..., None] + (0.2 * scuff)[..., None] * np.array([150.0, 150.0, 145.0])


def tread(a, b, base, along_b=True):
    along, across = (b, a) if along_b else (a, b)
    gap = _line(_edge_dist(along, 1.5), 0.25)                                      # between the rollers
    roll = 0.75 + 0.35 * np.cos(np.clip(np.mod(along, 1.5) / 1.5 - 0.5, -0.5, 0.5) * np.pi)
    col = np.broadcast_to(np.array([96.0, 98.0, 96.0]), along.shape + (3,)) * roll[..., None]
    col = col * (1 - 0.7 * gap)[..., None]
    rail = _line(_edge_dist(across, 4.0), 0.7)                                    # the strip's rails
    return col * (1 - rail)[..., None] + rail[..., None] * base[None, None, :] * 0.9


def frame(u, v, w, h, a, b, base):
    long_u = w >= h
    along, across, span = (u, v, h) if long_u else (v, u, w)
    col = base[None, None, :] * (0.95 + 0.08 * _fbm(a, b, 3.0, 29))[..., None]
    edge = np.minimum(across, span - across)
    riv = (np.abs(edge - 0.22) < 0.09) & (np.abs(np.mod(along, 0.7) - 0.35) < 0.09)
    col[riv] = np.array([118.0, 120.0, 114.0])
    if span > 1.0:
        c = np.hypot(np.mod(along, 4.0) - 2.0, across - span / 2)
        hole = c < min(0.4, span * 0.3)
        col[hole] = col[hole] * 0.35
    return col


def cables(u, v, w, h, a, b, base):
    long_u = w >= h
    along, across, span = (u, v, h) if long_u else (v, u, w)
    k = np.floor(across / max(span, 1e-6) * 4).astype(int) % 4
    tints = np.array([[34.0, 35.0, 33.0], [60.0, 52.0, 30.0], [32.0, 40.0, 54.0], [44.0, 44.0, 42.0]])
    col = tints[k]
    gap = _line(_edge_dist(across, span / 4), 0.05)
    col = col * (1 - 0.6 * gap)[..., None] * (0.9 + 0.15 * np.cos(np.mod(across, span / 4) / (span / 4) * np.pi - np.pi / 2))[..., None]
    clamp = np.abs(np.mod(along, 6.0) - 3.0) < 0.18
    col[clamp] = np.array([140.0, 142.0, 136.0])
    return col


def webbing(a, b, base):
    weave = 0.9 + 0.1 * np.sign(np.sin(a * 25.0) * np.sin(b * 25.0))
    col = base[None, None, :] * weave[..., None] * (0.95 + 0.08 * _fbm(a, b, 1.0, 31))[..., None]
    stitch = (np.abs(_edge_dist(a, 3.0) - 0.2) < 0.05) & (np.mod(b, 0.4) < 0.2)
    col[stitch] = np.array([200.0, 190.0, 170.0])
    return col


def grate(a, b, base):
    hole = (_edge_dist(a, 0.5) > 0.08) & (_edge_dist(b, 0.5) > 0.08)
    col = np.broadcast_to(np.array([96.0, 98.0, 94.0]), a.shape + (3,)).copy()
    col[hole] = np.array([18.0, 18.0, 17.0])
    return col


def brushed(a, b, base, along_a=True):
    s = _noise(a * (0.2 if along_a else 6.0), b * (6.0 if along_a else 0.2), 0.6, 37)
    return base[None, None, :] * (0.9 + 0.18 * s)[..., None]


def panel(u, v, w, h, a, b, base):
    col = base[None, None, :] * (0.95 + 0.07 * _fbm(a, b, 2.0, 41))[..., None]
    edge = np.minimum(np.minimum(u, w - u), np.minimum(v, h - v))
    col = col * (1 - 0.35 * _line(edge - 0.3, 0.1))[..., None]
    corner = (np.minimum(u, w - u) < 0.6) & (np.minimum(v, h - v) < 0.6) & \
             (np.hypot(np.minimum(u, w - u) - 0.45, np.minimum(v, h - v) - 0.45) < 0.12)
    col[corner] = np.array([120.0, 122.0, 118.0])
    return col


def make_fill(materials, texels, face_axes):
    """The `fill` for refit_lib.bake_unique: the detailed interior materials painted, anything else left to the baker."""

    def fill(item, w, h):
        mat = item["material"]
        if mat not in BOOST:
            return None
        cube = item["cube_ref"]
        ia, ib = face_axes[item["face"]]
        t = texels * BOOST[mat]
        jj, ii = np.meshgrid(np.arange(w + 2), np.arange(h + 2))
        u = (jj - 0.5) / t
        v = (ii - 0.5) / t
        a = cube["origin"][ia] + u
        b = cube["origin"][ib] + cube["size"][ib] - v
        fw, fh = cube["size"][ia], cube["size"][ib]
        base = np.array(materials[mat][:3], float)
        if mat == "quilt":
            rgb = quilt(a, b, base)
        elif mat == "floor":
            rgb = floor(a, b, base) if item["face"] in ("up", "down") else brushed(a, b, base)
        elif mat == "tread":
            rgb = tread(a, b, base, along_b=item["face"] in ("up", "down")) if item["face"] in ("up", "down") else base[None, None, :] * np.ones(a.shape + (1,))
        elif mat == "frame":
            rgb = frame(u, v, fw, fh, a, b, base)
        elif mat == "cable":
            rgb = cables(u, v, fw, fh, a, b, base)
        elif mat == "webbing":
            rgb = webbing(a, b, base)
        elif mat == "screen":
            rgb = grate(a, b, base)
        elif mat == "metal":
            rgb = brushed(a, b, base, along_a=fw >= fh)
        else:
            rgb = panel(u, v, fw, fh, a, b, base)
        out = np.empty((h + 2, w + 2, 4), np.float64)
        out[..., :3] = rgb
        out[..., 3] = 255.0
        return out

    return fill
