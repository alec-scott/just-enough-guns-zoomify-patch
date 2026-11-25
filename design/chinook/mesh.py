"""The CH-47F Chinook's curved skin (the user, 2026-11-21: realistic, with curves, not blocky): a smooth-shaded quad mesh
the mod draws on the GeckoLib bones generate.py writes (VehicleMesh), and its painted texture.

python -I design/chinook/mesh.py        (after generate.py, which writes the boxes the mesh rides on)

Writes:
  src/main/resources/assets/jeg/models/mesh/ch47.bin           the mesh (format in VehicleMesh.java)
  src/main/resources/assets/jeg/textures/entity/vehicle/ch47_skin.png
  src/main/resources/assets/jeg/textures/vehicle_icon/ch47_icon.png
  design/chinook/review-mesh-*.png                              renders of the mesh with the boxes

Authored in generate.py's frame: model pixels (16 a metre), +X the aircraft's right, nose toward -Z. VehicleMesh draws a
vertex at (x, y, z) / 16, which is where GeckoLib draws that point of generate.py's (mirrored) boxes.

What is a mesh and what stays a box: the outer skin (fuselage, cockpit glazing, pylons, roof tunnel, sponsons, engine
nacelles and their mounts, rotor blades, wheels and gear legs, the ramp's and crew door's outer panels) is mesh. The walk-in
cabin's interior (floor, lining, frames, lights), the cockpit's instruments and seats, the door guns, rotor hubs,
masts and the lights stay boxes.

The cockpit glazing is cut into the geometry along its outline (Surface.clip, nose_glass_sdf), not by texture alpha, so
its frames are straight lines with rounded corners from the pilot's seat.
"""
from __future__ import annotations

import json
import math
import pathlib
import struct
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import generate as G  # noqa: E402  (constants, and the refit/render helpers it puts on the path)
import interior_paint as IP  # noqa: E402  (the cabin's quilted blankets, as on the boxes)

ROOT = G.ROOT
ASSETS = G.ASSETS
MESH_OUT = ASSETS / "models/mesh/ch47.bin"
TEX_OUT = ASSETS / "textures/entity/vehicle/ch47_skin.png"
ATLAS = 4096           # about a centimetre a texel on the skin (the user, 2026-11-21: "the texture needs more detail")
TEXELS = 7.0            # texels a model pixel aimed for (112 a metre); pack() lowers it until the islands fit
PAD = 3

OPAQUE, GLASS, HOLE = 1, 2, 0
INSIDE = 3                      # opaque, seen only from inside or close by: not drawn from afar
INSIDE_MATERIALS = ("cockpit", "interior", "soot", "intake")

# ------------------------------------------------------------------------------------------------ shape helpers


def pchip(points):
    """A smooth monotone curve through (x, y) control points (no overshoot), clamped at the ends."""
    xs = np.array([p[0] for p in points], float)
    ys = np.array([p[1] for p in points], float)
    h = np.diff(xs)
    d = np.diff(ys) / h
    m = np.zeros_like(ys)
    m[0], m[-1] = d[0], d[-1]
    for k in range(1, len(xs) - 1):
        if d[k - 1] * d[k] <= 0:
            m[k] = 0.0
        else:
            w1, w2 = 2 * h[k] + h[k - 1], h[k] + 2 * h[k - 1]
            m[k] = (w1 + w2) / (w1 / d[k - 1] + w2 / d[k])

    def f(x):
        x = np.clip(np.asarray(x, float), xs[0], xs[-1])
        k = np.clip(np.searchsorted(xs, x) - 1, 0, len(xs) - 2)
        t = (x - xs[k]) / h[k]
        t2, t3 = t * t, t * t * t
        return ((2 * t3 - 3 * t2 + 1) * ys[k] + (t3 - 2 * t2 + t) * h[k] * m[k]
                + (-2 * t3 + 3 * t2) * ys[k + 1] + (t3 - t2) * h[k] * m[k + 1])
    return f


def arc(cx, cy, r, a0, a1, n, include_end=False):
    count = n + 1 if include_end else n
    return [(cx + r * math.cos(a0 + (a1 - a0) * k / n), cy + r * math.sin(a0 + (a1 - a0) * k / n)) for k in range(count)]


def fillet_ring(poly, radii, per_arc=6):
    """A closed outline through a convex polygon's corners, each rounded with its radius (0 keeps the corner)."""
    pts = []
    n = len(poly)
    for i in range(n):
        p0, p1, p2 = np.array(poly[i - 1], float), np.array(poly[i], float), np.array(poly[(i + 1) % n], float)
        r = radii[i]
        d1, d2 = p1 - p0, p2 - p1
        l1, l2 = np.linalg.norm(d1), np.linalg.norm(d2)
        if r <= 1e-6 or l1 < 1e-6 or l2 < 1e-6:
            pts.append(tuple(p1))
            continue
        d1, d2 = d1 / l1, d2 / l2
        cos_t = float(np.clip(np.dot(-d1, d2), -1, 1))
        theta = math.acos(cos_t)
        t = min(r / math.tan(theta / 2), l1 * 0.49, l2 * 0.49)
        r_eff = t * math.tan(theta / 2)
        a, b = p1 - d1 * t, p1 + d2 * t
        bis = (-d1 + d2)
        bis /= np.linalg.norm(bis)
        c = p1 + bis * (r_eff / math.sin(theta / 2))
        a0 = math.atan2(a[1] - c[1], a[0] - c[0])
        a1 = math.atan2(b[1] - c[1], b[0] - c[0])
        da = (a1 - a0 + math.pi) % (2 * math.pi) - math.pi
        for k in range(per_arc + 1):
            ang = a0 + da * k / per_arc
            pts.append((c[0] + r_eff * math.cos(ang), c[1] + r_eff * math.sin(ang)))
    return pts


# ------------------------------------------------------------------------------------------------ surfaces


def grow_points(P, rotor=False):
    """Authored points in the grown model (generate.grow): each half out from the centre line, all above the
    windscreen's top raised (the nose stretched instead); a rotor's points only raised."""
    P = np.array(P, float)
    if not rotor:
        x = P[..., 0]
        P[..., 0] = np.where(x > 1e-9, x + G.WIDEN, np.where(x < -1e-9, x - G.WIDEN, 0.0))
    y, z = P[..., 1], P[..., 2]
    step = np.where(y > G.RAISE_FROM, G.RAISE, 0.0)
    ramp = G.RAISE * np.clip((y - G.NOSE_RAMP_FROM) / (G.RAISE_FROM - G.NOSE_RAMP_FROM), 0.0, 1.0)
    t = np.clip((z - G.NOSE_BLEND[0]) / (G.NOSE_BLEND[1] - G.NOSE_BLEND[0]), 0.0, 1.0)
    t = t * t * (3.0 - 2.0 * t)
    P[..., 1] = y + ramp + (step - ramp) * t
    return P


class Surface:
    """A patch: a grid of points P (rows x cols x 3), smooth normals, and which quads to draw (hole, opaque, glass)."""

    def __init__(self, name, bone, P, kinds=None, material="paint", normals=None, outward=None, flip=False, closed=False,
                 design=None):
        """P authored at the real size, built grown (generate.grow; a rotor's parts and the masts only rise); or, with
        `design` (the authored grid), P already grown."""
        P = np.asarray(P, float)
        if closed:
            P = np.concatenate([P, P[:, :1]], axis=1)
        if design is None:
            design = P
            P = grow_points(P, rotor=bone.startswith("rotor") or name.endswith(" mast"))
        # The authored grid: windows' outlines and painted placings are drawn in it, whatever the growing did.
        self.D = np.asarray(design, float)
        self.name, self.bone, self.material, self.P = name, bone, material, P
        R, C = P.shape[:2]
        self.kinds = np.full((R - 1, C - 1), OPAQUE, int) if kinds is None else np.asarray(kinds, int)
        if self.kinds.shape != (R - 1, C - 1):
            raise ValueError(f"{name}: kinds {self.kinds.shape} for a {R}x{C} grid")
        self.N = self._normals(closed) if normals is None else np.broadcast_to(np.asarray(normals, float), P.shape).copy()
        if outward is not None:
            ref = outward(design)
            s = np.sign(np.sum(self.N * ref, axis=-1, keepdims=True))
            s[s == 0] = 1
            # One orientation for the whole patch (a few flat spots must not flip on their own).
            self.N *= 1 if np.sum(np.sum(self.N * ref, -1)) >= 0 else -1
        if flip:
            self.N = -self.N
        # Arc-length parameters for the texture: columns along u, rows along v.
        du = np.linalg.norm(np.diff(P, axis=1), axis=-1)            # R x C-1
        dv = np.linalg.norm(np.diff(P, axis=0), axis=-1)            # R-1 x C
        cu = np.concatenate([[0.0], np.cumsum(du.mean(axis=0))])
        cv = np.concatenate([[0.0], np.cumsum(dv.mean(axis=1))])
        self.lu, self.lv = max(cu[-1], 0.25), max(cv[-1], 0.25)
        self.cu, self.cv = cu / self.lu, cv / self.lv
        self.rect = None   # (x0, y0, w, h) in the atlas
        # A window's exact outline (points -> bool), when it doesn't follow the grid: the texture's alpha draws the
        # outline and every quad touching it is drawn in the glass pass, so the edge is smooth, not stepped.
        self.window = None
        self.density = None   # texels a model pixel, when finer than the atlas's (the nose's glazing)
        self.window_alpha = 0.22  # the glass's opacity in the window (0: cut clean, the glass drawn by another surface)
        # A window cut into the geometry (points -> signed distance in model pixels, > 0 on the glass): the quads marked
        # GLASS are clipped along that outline, the frame drawn opaque and the pane in the glass pass (or dropped when
        # window_alpha is 0), so the edge is a true line at any distance. clip_inner, a grid like P a skin's thickness
        # in, adds the frame's depth (the reveal) between this surface and the lining.
        self.clip = None
        self.clip_inner = None

    def _vert(self, i, j, fr, fc, P=None, uv=None):
        p = bilerp(self.P if P is None else P, i, j, fr, fc)
        n = bilerp(self.N, i, j, fr, fc)
        n = n / max(np.linalg.norm(n), 1e-9)
        if uv is None:
            x0, y0, w, h = self.rect
            u = (x0 + (self.cu[j] + (self.cu[j + 1] - self.cu[j]) * fc) * w) / ATLAS
            v = (y0 + (self.cv[i] + (self.cv[i + 1] - self.cv[i]) * fr) * h) / ATLAS
        else:
            u, v = uv
        return (p[0] / 16, p[1] / 16, p[2] / 16, u, v, n[0], n[1], n[2])

    def _clip_quad(self, i, j, out):
        """Quad (i, j) split exactly along the clip outline: a quadtree down to CLIP_DEPTH where the outline passes,
        each leaf cut along the line where the distance crosses zero (bilinear patches are ruled, so the pieces meet
        their neighbours and the unclipped quads without cracks)."""
        sdf = self.clip
        cache = {}

        def S(c):
            if c not in cache:
                cache[c] = float(sdf(bilerp(self.D, i, j, *c)[None])[0])
            return cache[c]

        def emit(poly, kind):
            if len(poly) < 3:
                return
            if kind == GLASS:
                if self.window_alpha <= 0:
                    return
                verts = [self._vert(i, j, fr, fc, uv=GLASS_SWATCH.uv()) for fr, fc in poly]
            else:
                verts = [self._vert(i, j, fr, fc) for fr, fc in poly]
            m = len(verts)
            for t in range(1, m - 1, 2):
                out[kind].append([verts[0], verts[t], verts[t + 1], verts[t + 2] if t + 2 < m else verts[t + 1]])

        def clip(cs, ss, sign):
            res, cuts = [], []
            for k in range(len(cs)):
                a, b = cs[k], cs[(k + 1) % len(cs)]
                sa, sb = sign * ss[k], sign * ss[(k + 1) % len(cs)]
                if sa >= 0:
                    res.append(a)
                if (sa >= 0) != (sb >= 0):
                    t = sa / (sa - sb)
                    p = (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
                    res.append(p)
                    cuts.append(p)
            dedup = [p for k, p in enumerate(res) if k == 0 or abs(p[0] - res[k - 1][0]) + abs(p[1] - res[k - 1][1]) > 1e-9]
            return dedup, cuts

        def reveal(a, b, glass_at):
            oa, ob = bilerp(self.P, i, j, *a), bilerp(self.P, i, j, *b)
            ia, ib = bilerp(self.clip_inner, i, j, *a), bilerp(self.clip_inner, i, j, *b)
            n = np.cross(ob - oa, bilerp(self.N, i, j, 0.5 * (a[0] + b[0]), 0.5 * (a[1] + b[1])))
            n = n / max(np.linalg.norm(n), 1e-9)
            mid = 0.5 * (oa + ob)
            if np.dot(n, bilerp(self.P, i, j, *glass_at) - mid) < 0:
                n = -n                                    # the reveal faces into the opening
            ua, ub = self._vert(i, j, *a)[3:5], self._vert(i, j, *b)[3:5]
            quad = [(p[0] / 16, p[1] / 16, p[2] / 16, uv[0], uv[1], n[0], n[1], n[2])
                    for p, uv in ((oa, ua), (ob, ub), (ib, ub), (ia, ua))]
            out[OPAQUE].append(quad)

        def rec(fr0, fr1, fc0, fc1, depth):
            cs = [(fr0, fc0), (fr0, fc1), (fr1, fc1), (fr1, fc0)]
            ss = [S(c) for c in cs]
            # The outline's finest feature (a post) is a pixel wide and a quad at most a few, so a 7 x 7 sample of the
            # cell finds any crossing; a cell the outline doesn't cross is drawn whole.
            f = np.linspace(0.0, 1.0, 7)
            fr, fc = np.meshgrid(fr0 + (fr1 - fr0) * f, fc0 + (fc1 - fc0) * f, indexing="ij")
            sample = sdf(bilerp(self.D[..., None, None, :], i, j, fr[..., None], fc[..., None]))
            if (sample > 0).all() or (sample < 0).all():
                emit(cs, GLASS if ss[0] > 0 else OPAQUE)
                return
            if depth < CLIP_DEPTH:
                fm, cm = 0.5 * (fr0 + fr1), 0.5 * (fc0 + fc1)
                for a0, a1, b0, b1 in ((fr0, fm, fc0, cm), (fr0, fm, cm, fc1), (fm, fr1, cm, fc1), (fm, fr1, fc0, cm)):
                    rec(a0, a1, b0, b1, depth + 1)
                return
            glass, cuts = clip(cs, ss, 1)
            frame, _ = clip(cs, ss, -1)
            emit(glass, GLASS)
            emit(frame, OPAQUE)
            if self.clip_inner is not None and len(cuts) == 2 and len(glass) >= 3:
                reveal(cuts[0], cuts[1], (sum(q[0] for q in glass) / len(glass), sum(q[1] for q in glass) / len(glass)))

        rec(0.0, 1.0, 0.0, 1.0, 0)

    def _normals(self, closed):
        P = self.P
        if closed:
            core = P[:, :-1]
            du = np.roll(core, -1, axis=1) - np.roll(core, 1, axis=1)
            du = np.concatenate([du, du[:, :1]], axis=1)
        else:
            du = np.zeros_like(P)
            du[:, 1:-1] = P[:, 2:] - P[:, :-2]
            du[:, 0] = P[:, 1] - P[:, 0]
            du[:, -1] = P[:, -1] - P[:, -2]
        dv = np.zeros_like(P)
        if P.shape[0] > 2:
            dv[1:-1] = P[2:] - P[:-2]
        dv[0] = P[1] - P[0]
        dv[-1] = P[-1] - P[-2]
        n = np.cross(du, dv)
        length = np.linalg.norm(n, axis=-1, keepdims=True)
        bad = length[..., 0] < 1e-9
        n = n / np.maximum(length, 1e-9)
        if bad.any():
            # Degenerate points (a nose tip): the mean of the row's normals.
            for i, j in zip(*np.nonzero(bad)):
                row = n[i][~bad[i]]
                n[i, j] = row.mean(0) / max(np.linalg.norm(row.mean(0)), 1e-9) if len(row) else (0, 0, -1)
        return n

    def texel_grid(self):
        """Each texel's point and normal on the surface, and its kind (hole, opaque, glass)."""
        x0, y0, w, h = self.rect
        u = (np.arange(w) + 0.5) / w
        v = (np.arange(h) + 0.5) / h
        cols = np.interp(u, self.cu, np.arange(len(self.cu)))
        rows = np.interp(v, self.cv, np.arange(len(self.cv)))
        ci = np.clip(np.floor(cols).astype(int), 0, len(self.cu) - 2)
        ri = np.clip(np.floor(rows).astype(int), 0, len(self.cv) - 2)
        fc = (cols - ci)[None, :, None]
        fr = (rows - ri)[:, None, None]
        R, C = np.meshgrid(ri, ci, indexing="ij")

        def bil(A):
            return ((1 - fr) * ((1 - fc) * A[R, C] + fc * A[R, C + 1]) + fr * ((1 - fc) * A[R + 1, C] + fc * A[R + 1, C + 1]))
        P = bil(self.P)
        N = bil(self.N)
        N /= np.maximum(np.linalg.norm(N, axis=-1, keepdims=True), 1e-9)
        kinds = self.kinds[R, C]
        self.texel_design = bil(self.D)
        if self.window is not None:
            kinds = np.where(kinds == HOLE, HOLE, np.where(self.window(self.texel_design), GLASS, OPAQUE))
        return P, N, kinds

    def quads(self):
        """Vertex rows (x, y, z, u, v, nx, ny, nz) of each quad drawn, split into opaque and glass."""
        x0, y0, w, h = self.rect
        out = {OPAQUE: [], GLASS: []}
        R, C = self.kinds.shape
        for i in range(R):
            for j in range(C):
                k = self.kinds[i, j]
                if k == HOLE:
                    continue
                if k == GLASS and self.clip is not None:
                    self._clip_quad(i, j, out)
                    continue
                verts = []
                for (a, b) in ((i, j), (i, j + 1), (i + 1, j + 1), (i + 1, j)):
                    p, n = self.P[a, b], self.N[a, b]
                    u = (x0 + self.cu[b] * w) / ATLAS
                    v = (y0 + self.cv[a] * h) / ATLAS
                    verts.append((p[0] / 16, p[1] / 16, p[2] / 16, u, v, n[0], n[1], n[2]))
                out[k].append(verts)
        return out


SURFACES: list[Surface] = []
CLIP_DEPTH = 3          # a clipped quad's finest cells: 1/8 of it (one to two centimetres at the nose)


def bilerp(A, i, j, fr, fc):
    return (1 - fr) * ((1 - fc) * A[i, j] + fc * A[i, j + 1]) + fr * ((1 - fc) * A[i + 1, j] + fc * A[i + 1, j + 1])


class Swatch(Surface):
    """A small flat island drawing nothing itself: one uniform colour other surfaces' pieces point at (the panes)."""

    def __init__(self, name, material):
        P = np.array([[[0, 0, 0], [4, 0, 0]], [[0, 4, 0], [4, 4, 0]]], float)
        super().__init__(name, "airframe", P, np.full((1, 1), HOLE), material=material, normals=(0, 0, 1))

    def uv(self):
        x0, y0, w, h = self.rect
        return ((x0 + w * 0.5) / ATLAS, (y0 + h * 0.5) / ATLAS)


def add(surface):
    SURFACES.append(surface)
    return surface


def cap(name, bone, ring, centre, material="paint", outward=None):
    """A fan closing a ring (an end of a loft), as a two-row grid whose first row is the centre."""
    ring = np.asarray(ring, float)
    P = np.stack([np.repeat(np.asarray(centre, float)[None], len(ring), 0), ring])
    return add(Surface(name, bone, P, material=material, outward=outward))


# ------------------------------------------------------------------------------------------------ the fuselage

STRIP_Z = (-112.0, G.CABIN_REAR)             # the flat sides between the cockpit's front and the ramp's hinge
STRIP_Y = (G.BELLY_Y + 4.0, 46.0)             # their bottom and top (where the corner arcs meet them)
COCKPIT_WINDOWS = [(-111.0, -99.0), (-97.0, -84.0)]
COCKPIT_WINDOW_Y = (31.5, 46.0)


def openings(side):
    """The holes and windows in one flat side: (z0, z1, y0, y1, kind). side +1 is the right (crew door)."""
    out = [(pz - 4.0, pz + 4.0, 33.0, 41.0, GLASS) for pz in G.PORTHOLES]
    out += [(z0, z1, COCKPIT_WINDOW_Y[0], COCKPIT_WINDOW_Y[1], GLASS) for z0, z1 in COCKPIT_WINDOWS]
    if side > 0:
        out.append((G.DOOR_Z[0], G.DOOR_Z[1], G.FLOOR_Y, G.DOOR_TOP, HOLE))
    else:
        out.append((G.GUN_WINDOW_Z[0], G.GUN_WINDOW_Z[1], G.GUN_WINDOW_Y[0], G.GUN_WINDOW_Y[1], HOLE))
    return out


def strip_y_lines():
    ys = {STRIP_Y[0], STRIP_Y[1]}
    for side in (1, -1):
        for o in openings(side):
            for y in (o[2], o[3]):
                if STRIP_Y[0] < y < STRIP_Y[1]:
                    ys.add(round(y, 4))
    ys.add(26.0)
    ys.add(32.5)
    return sorted(ys)


SIDE_Y = strip_y_lines()
SIDE_FRACS = list(np.linspace(0.0, 1.0, 7))      # the loft's own side samples (the nose); the flat sides are separate

FUS_HW = pchip([(-134, 1.0), (-132.5, 7.5), (-130, 12.0), (-126, 16.0), (-121, 19.2), (-116, 20.9), (-112, 21.4), (90, 21.4)])
FUS_YB = pchip([(-134, 20.0), (-132.5, 15.5), (-129, 12.2), (-124, 10.4), (-118, 9.7), (-114, 9.6), (66, 9.6), (72, 16.0),
                (78, 28.0), (84, 41.0), (90, 50.0)])
FUS_YT = pchip([(-134, 22.0), (-132.5, 26.5), (-129, 29.8), (-125, 31.6), (-122, 32.6), (-120.5, 35.5), (-117, 42.6),
                (-114, 47.6), (-112, 48.8), (-110, 49.0), (-86, 49.0), (-80, 52.0), (90, 52.0)])
NOSE_RT = pchip([(-134, 0.6), (-132, 4.0), (-128, 7.5), (-123, 10.0), (-119, 9.0), (-115, 5.0), (-112, 2.8)])
NOSE_RB = pchip([(-134, 0.6), (-132, 3.0), (-128, 4.0), (-112, 4.0)])


def fuselage_section(z):
    hw, yb, yt = float(FUS_HW(z)), float(FUS_YB(z)), float(FUS_YT(z))
    if z >= STRIP_Z[0]:
        rb, rt = 4.0, yt - STRIP_Y[1]
    else:
        rb, rt = float(NOSE_RB(z)), float(NOSE_RT(z))
    if z > G.CABIN_REAR:
        rb = min(4.0 + (z - G.CABIN_REAR) * 0.4, (yt - yb) * 0.45)
    height = yt - yb
    rt = max(0.3, min(rt, hw * 0.98, height * 0.6))
    rb = max(0.3, min(rb, hw * 0.98, height - rt - 0.05))
    return hw, yb, yt, rb, rt


N_BF, N_BA, N_TA, N_TF = 5, 7, 10, 6


def fuselage_ring(z):
    """The section at z, all the way round from the bottom's centre (right side up, over the top, left side down),
    with a label per point: bf/ba/s/ta/tf (bottom flat, bottom arc, side, top arc, top flat)."""
    hw, yb, yt, rb, rt = fuselage_section(z)
    half, labels = [], []
    for k in range(N_BF):
        half.append(((hw - rb) * k / N_BF, yb))
        labels.append("bf")
    for p in arc(hw - rb, yb + rb, rb, -math.pi / 2, 0.0, N_BA):
        half.append(p)
        labels.append("ba")
    y0, y1 = yb + rb, yt - rt
    for f in SIDE_FRACS:
        half.append((hw, y0 + (y1 - y0) * f))
        labels.append("s")
    # The top arc, with two more points either side of the raise's line (generate.grow): the quad between them becomes
    # the wall the raise adds, upright as the side below it, so it shades as one wall with it (without them the
    # arc's normals leaned over it and it showed as a separate band). Where the line misses the arc they sit at its
    # end, adding nothing.
    angles = [math.pi / 2 * k / N_TA for k in range(1, N_TA)]
    cy = yt - rt
    for y_line in (G.RAISE_FROM - 0.02, G.RAISE_FROM + 0.02):
        u = (y_line - cy) / max(rt, 1e-6)
        angles.append(math.asin(min(max(u, 0.0005), 0.9995)))
    for a in sorted(angles):
        half.append((hw - rt + rt * math.cos(a), cy + rt * math.sin(a)))
        labels.append("ta")
    for k in range(1, N_TF):
        half.append(((hw - rt) * (1 - k / N_TF), yt))
        labels.append("tf")
    top = [(0.0, yt)]
    right = half
    left = [(-x, y) for x, y in reversed(half[1:])]
    pts = right + top + left
    labs = labels + ["tf"] + list(reversed(labels[1:]))
    return [(x, y, z) for x, y in pts], labs


def fuselage_zs():
    zs = set(np.round(np.arange(-134.0, -111.5, 1.0), 4))
    zs.update([-133.6, -133.2, -112.0, -110.0, -88.0, -86.0, -84.0, -82.0, -80.0, -78.0])
    for side in (1, -1):
        for o in openings(side):
            zs.update([o[0], o[1]])
    zs.update(np.round(np.arange(-80.0, G.CABIN_REAR, 8.0), 4))
    zs.update(np.round(np.arange(G.CABIN_REAR, 86.01, 1.5), 4))
    zs.update([G.CABIN_REAR, -81.0, -121.5, -113.0, -131.0, -122.0])
    return sorted(z for z in zs if -134.0 <= z <= 86.0)


def round_min(a, b, r):
    """min(a, b) with the corner where both are near zero rounded to radius r (a fillet in the window's corner)."""
    h = np.clip(0.5 + 0.5 * (b - a) / r, 0, 1)
    return b * (1 - h) + a * h - r * h * (1 - h)


def nose_glass_sdf(P):
    """The cockpit's front glazing as a signed distance in model pixels (> 0 on the glass): two windscreens and two
    corner panes between posts, with rounded corners, and the chin windows. In the authored frame (Surface.D): the
    growing stretches the panes up with the nose."""
    x, y, z = np.abs(P[..., 0]), P[..., 1], P[..., 2]
    sill = 33.6 + 0.35 * np.clip(x - 8.0, 0, None)                 # the sill rises a little toward the corners
    r = 0.9                                                        # the panes' corner radius (6 cm)
    screen = round_min(z + 121.6, -113.4 - z, r)
    screen = round_min(screen, y - sill, r)
    screen = round_min(screen, x - 0.7, r)                         # the centre post
    screen = round_min(screen, np.maximum(10.6 - x, x - 11.6), r)  # the posts between the front and corner panes
    screen = round_min(screen, np.maximum(-114.4 - z, 46.2 - y), r)
    e = np.sqrt(((z + 126.5) / 3.2) ** 2 + ((y - 16.3) / 3.6) ** 2)
    chin = np.minimum(np.minimum((1.0 - e) * 3.4, x - 4.0), 12.5 - x)
    return np.maximum(screen, chin)


def nose_glass(P):
    return nose_glass_sdf(P) > 0


def build_fuselage():
    zs = fuselage_zs()
    rings, labels = [], None
    for z in zs:
        ring, labels = fuselage_ring(z)
        rings.append(ring)
    P = np.array(rings)                     # R x C x 3 (closed: C points, the seam added by Surface)
    R, C = P.shape[:2]
    Pc = np.concatenate([P, P[:, :1]], axis=1)
    labs = labels + [labels[0]]
    kinds = np.full((R - 1, C), OPAQUE, int)
    for i in range(R - 1):
        zc = 0.5 * (zs[i] + zs[i + 1])
        for j in range(C):
            c = 0.25 * (Pc[i, j] + Pc[i, j + 1] + Pc[i + 1, j] + Pc[i + 1, j + 1])
            xc, yc = c[0], c[1]
            side = labs[j] == "s" and labs[j + 1] == "s"
            if side and STRIP_Z[0] <= zc <= STRIP_Z[1]:
                kinds[i, j] = HOLE                       # the flat sides are their own panels (build_sides)
            elif zc > G.CABIN_REAR and abs(xc) < G.IN_HALF + 0.3 and yc < G.CABIN_TOP + 1.0:
                kinds[i, j] = HOLE                       # the ramp's opening
            else:
                # Any quad the windscreen's or chin windows' outline comes near is clipped along it (Surface.clip).
                f = np.linspace(0.0, 1.0, 6)
                fr, fc = np.meshgrid(f, f, indexing="ij")
                samples = bilerp(Pc[..., None, None, :], i, j, fr[..., None], fc[..., None])
                if (nose_glass_sdf(samples) > -0.6).any():
                    kinds[i, j] = GLASS
    fus = Surface("fuselage", "airframe", P, kinds, outward=lambda P: P - np.stack(
        [np.zeros(P.shape[:2]), np.full(P.shape[:2], 30.0), P[..., 2]], -1), closed=True)
    # Two texture islands sharing the row at the cockpit's front (and one set of normals, so no shading seam): the
    # nose, with its glazing, at a finer texture, and the rest.
    k = zs.index(STRIP_Z[0])
    nose = add(Surface("nose", "airframe", fus.P[:k + 1], kinds[:k], normals=fus.N[:k + 1], design=fus.D[:k + 1]))
    nose.clip = nose_glass_sdf
    nose.density = 10.0
    add(Surface("fuselage", "airframe", fus.P[k:], kinds[k:], normals=fus.N[k:], design=fus.D[k:]))
    # The nose tip's cap.
    tip = P[0]
    cap("nose cap", "airframe", np.concatenate([tip, tip[:1]]), (0.0, tip[:, 1].mean(), zs[0] - 0.4),
        outward=lambda P: np.broadcast_to(np.array([0, 0, -1.0]), P.shape))
    # Inside: the cockpit's lining (so the crew see walls, not the paint's back), and the aft walls beside the ramp.
    inner_kinds = np.full_like(kinds, HOLE)
    for i in range(R - 1):
        zc = 0.5 * (zs[i] + zs[i + 1])
        for j in range(C):
            c = 0.25 * (Pc[i, j] + Pc[i, j + 1] + Pc[i + 1, j] + Pc[i + 1, j + 1])
            if kinds[i, j] == HOLE:
                continue
            if zc <= -81.0 and c[1] > G.FLOOR_Y - 0.5:
                # Where the glazing's outline crosses it, the lining is cut by the same outline (glass pass, clear).
                inner_kinds[i, j] = kinds[i, j]
            elif zc > G.CABIN_REAR and kinds[i, j] == OPAQUE:
                inner_kinds[i, j] = OPAQUE
    inner = Pc - 0.8 * fus.N
    # The cockpit's part of the lining at the nose's fine texture (its window posts are seen from a hand's breadth away).
    lining = add(Surface("cockpit lining", "airframe", inner[:k + 1], inner_kinds[:k], material="cockpit", normals=-fus.N[:k + 1]))
    lining.clip = nose_glass_sdf
    lining.window_alpha = 0.0
    lining.density = 10.0
    nose.clip_inner = lining.P
    add(Surface("fuselage inside", "airframe", inner[k:], inner_kinds[k:], material="cockpit", normals=-fus.N[k:]))
    return zs


def grid_kinds(zs, ys, holes, inside=False):
    kinds = np.full((len(ys) - 1, len(zs) - 1), OPAQUE, int)
    for i in range(len(ys) - 1):
        yc = 0.5 * (ys[i] + ys[i + 1])
        for j in range(len(zs) - 1):
            zc = 0.5 * (zs[j] + zs[j + 1])
            for z0, z1, y0, y1, kind in holes:
                if z0 < zc < z1 and y0 < yc < y1:
                    kinds[i, j] = HOLE if inside else kind
    return kinds


def plane_x(x, zs, ys):
    Z, Y = np.meshgrid(zs, ys)
    return np.stack([np.full_like(Z, x), Y, Z], -1)


def build_sides(fuselage_z):
    """The two flat sides, inside and out, with the windows, the crew door and the gun window cut exactly."""
    for side in (1, -1):
        holes = openings(side)
        zs_out = sorted({STRIP_Z[0], STRIP_Z[1], -81.0, *[h[0] for h in holes], *[h[1] for h in holes]})
        name = "right" if side > 0 else "left"
        add(Surface(f"{name} side", "airframe", plane_x(side * G.OUT_HALF, zs_out, SIDE_Y), grid_kinds(zs_out, SIDE_Y, holes),
                    normals=(side, 0, 0)))
        # Inside: the cockpit's walls (just inside the skin), and the cabin's (behind its lining and frames).
        zs_ck = [z for z in zs_out if z <= -81.0]
        add(Surface(f"{name} cockpit wall", "airframe", plane_x(side * (G.OUT_HALF - 0.8), zs_ck, SIDE_Y),
                    grid_kinds(zs_ck, SIDE_Y, holes, inside=True), material="cockpit", normals=(-side, 0, 0)))
        zs_cb = [z for z in zs_out if z >= -81.0]
        ys_cb = sorted({G.FLOOR_Y, G.CABIN_TOP, *[y for y in SIDE_Y if G.FLOOR_Y < y < G.CABIN_TOP]})
        add(Surface(f"{name} cabin wall", "airframe", plane_x(side * (G.IN_HALF + 0.05), zs_cb, ys_cb),
                    grid_kinds(zs_cb, ys_cb, holes, inside=True), material="interior", normals=(-side, 0, 0)))
        # The openings' reveals, from the skin to the inner wall.
        for z0, z1, y0, y1, kind in holes:
            x_in = side * ((G.OUT_HALF - 0.8) if z1 <= -81.0 else (G.IN_HALF + 0.05))
            x_out = side * G.OUT_HALF
            xs = [x_out, x_in]
            for y, ny in ((y0, 1.0), (y1, -1.0)):
                P = np.array([[(x, y, z0) for x in xs], [(x, y, z1) for x in xs]])
                add(Surface(f"{name} reveal", "airframe", P, normals=(0, ny, 0)))
            for z, nz in ((z0, 1.0), (z1, -1.0)):
                P = np.array([[(x, y0, z) for x in xs], [(x, y1, z) for x in xs]])
                add(Surface(f"{name} reveal", "airframe", P, normals=(0, 0, nz)))


# ------------------------------------------------------------------------------------------------ pylons, tunnel, sponsons


def loft(name, bone, zs, section, material="paint", closed=True, centre=None, caps=(False, False)):
    """A surface through sections section(z) -> list of (x, y) points (same count each), along z."""
    rings = [[(x, y, z) for x, y in section(z)] for z in zs]
    P = np.array(rings, float)
    if centre is None:
        def centre(P):
            c = P.mean(axis=1, keepdims=True)
            return P - c
    s = add(Surface(name, bone, P, material=material, outward=centre, closed=closed))
    for end, flag in ((0, caps[0]), (-1, caps[1])):
        if flag:
            ring = P[end]
            ring = np.concatenate([ring, ring[:1]]) if closed else ring
            mid = ring[:-1].mean(0) if closed else ring.mean(0)
            direction = np.array([0, 0, -1.0 if end == 0 else 1.0])
            cap(f"{name} cap", bone, ring, mid, material=material, outward=lambda P, d=direction: np.broadcast_to(d, P.shape))
    return s


FP_YT = pchip([(-115, 47.5), (-112, 52.0), (-108, 58.5), (-103, 63.2), (-96, 65.0), (-89, 64.5), (-83, 62.0), (-77, 58.6),
               (-74, 58.2)])
FP_HW = pchip([(-115, 8.0), (-111, 11.0), (-106, 12.0), (-86, 12.0), (-80, 9.5), (-74, 6.6)])


def forward_pylon_section(z):
    hw, yt, yb = float(FP_HW(z)), float(FP_YT(z)), 46.0
    rt = min(hw * 0.55, (yt - yb) * 0.7)
    poly = [(hw, yb), (hw, yt), (-hw, yt), (-hw, yb)]
    pts = fillet_ring(poly, [0.0, rt, rt, 0.0], per_arc=7)
    return pts


# Its underside is the cabin's ceiling line (CABIN_TOP), raised with the cabin: at 47, on the raise's line and not
# above it, it stayed a metre under the grown ceiling and hung across the ramp opening (the user, 2026-11-21).
AP_YB = pchip([(52, 48.0), (82, 48.0), (88, 49.0), (100, 54.0), (110, 59.0), (115.5, 62.0)])
AP_YT = pchip([(52, 50.5), (57, 54.0), (62, 61.0), (67, 70.0), (72, 78.5), (77, 84.5), (83, 87.2), (104, 87.6), (109, 86.0),
               (113, 82.0), (115.5, 77.0)])
AP_HWB = pchip([(52, 21.4), (88, 21.4), (98, 19.5), (108, 15.0), (115.5, 4.0)])
AP_HWT = pchip([(52, 19.0), (60, 15.0), (68, 12.0), (76, 10.8), (104, 10.4), (110, 8.0), (115.5, 2.0)])


def aft_pylon_section(z):
    yb, yt, hwb, hwt = float(AP_YB(z)), float(AP_YT(z)), float(AP_HWB(z)), float(AP_HWT(z))
    rb = min(4.0, hwb * 0.6)
    rt = min(5.0, hwt * 0.8, (yt - yb) * 0.35)
    poly = [(hwb, yb), (hwt, yt), (-hwt, yt), (-hwb, yb)]
    return fillet_ring(poly, [rb, rt, rt, rb], per_arc=7)


def sponson_section_fn(side):
    s_of = pchip([(-66, 0.04), (-64, 0.35), (-61, 0.72), (-57, 0.93), (-53, 1.0), (49, 1.0), (54, 0.9), (58, 0.62),
                  (61, 0.28), (62.5, 0.04)])

    def section(z):
        s = float(s_of(z))
        x_in, x_out = G.OUT_HALF - 2.0, G.OUT_HALF + (G.SPONSON_HALF - G.OUT_HALF) * s
        cy = 17.6
        half = 7.4 * (0.35 + 0.65 * s)
        lo, hi = cy - half, cy + half * 0.97
        poly = [(x_in, lo), (x_out - 2.0 * s, lo), (x_out, lo + 3.2 * s + 0.2), (x_out, hi - 2.6 * s - 0.2), (x_out - 2.2 * s, hi),
                (x_in, hi)]
        r = [0.0, 3.0 * s + 0.2, 3.0 * s + 0.2, 2.5 * s + 0.2, 2.5 * s + 0.2, 0.0]
        pts = fillet_ring(poly, r, per_arc=4)
        return [(side * x, y) for x, y in pts] if side > 0 else [(-x, y) for x, y in reversed(pts)]
    return section


def build_airframe_extras():
    zs = list(np.round(np.arange(-115.0, -73.9, 1.5), 3)) + [-74.0]
    loft("forward pylon", "airframe", sorted(set(zs)), forward_pylon_section, closed=False)
    tunnel_zs = [-76.0, -50.0, -20.0, 10.0, 40.0, 58.0]

    def tunnel(z):
        poly = [(6.5, 50.5), (6.5, 58.0), (-6.5, 58.0), (-6.5, 50.5)]
        return fillet_ring(poly, [0.0, 3.6, 3.6, 0.0], per_arc=6)
    loft("roof tunnel", "airframe", tunnel_zs, tunnel, closed=False)
    ap_zs = sorted(set(list(np.round(np.arange(52.0, 115.5, 1.5), 3)) + [115.5]))
    loft("aft pylon", "airframe", ap_zs, aft_pylon_section, closed=True, caps=(False, True))
    sp_zs = sorted(set(list(np.round(np.arange(-66.0, -52.0, 1.0), 3)) + list(np.arange(-52.0, 48.0, 10.0))
                       + list(np.round(np.arange(48.0, 62.6, 1.0), 3)) + [62.5]))
    for side in (1, -1):
        loft(f"{'right' if side > 0 else 'left'} sponson", "airframe", sp_zs, sponson_section_fn(side), closed=True,
             caps=(True, True))


# ------------------------------------------------------------------------------------------------ engines


def revolve_z(name, bone, cx, cy, profile, n=28, material="paint", inward=False):
    """A surface of revolution about the axis through (cx, cy) along z: profile = [(z, r), ...]."""
    angles = np.linspace(0, 2 * np.pi, n, endpoint=False)
    P = np.array([[(cx + r * math.cos(a), cy + r * math.sin(a), z) for a in angles] for z, r in profile], float)
    axis = lambda P: P - np.stack([np.full(P.shape[:2], cx), np.full(P.shape[:2], cy), P[..., 2]], -1)
    s = add(Surface(name, bone, P, material=material, outward=axis, closed=True))
    if inward:
        s.N = -s.N
    return s


def build_engines():
    for side in (1, -1):
        nx, ny = side * G.NACELLE[0], G.NACELLE[1]
        r = G.NACELLE_R
        z0, z1 = G.NACELLE_Z
        name = "right" if side > 0 else "left"
        body = [(z0 - 0.6, r - 1.6), (z0 - 0.3, r - 0.6), (z0 + 0.4, r - 0.1), (z0 + 2.0, r), (z0 + 14, r), (z0 + 30, r - 0.1),
                (z1 - 6, r - 0.5), (z1 - 2, r - 1.1), (z1, r - 1.6)]
        revolve_z(f"{name} nacelle", "airframe", nx, ny, body)
        # The intake's throat, the screen across it, and the exhaust pipe with its dark inside.
        revolve_z(f"{name} intake", "airframe", nx, ny, [(z0 - 0.6, r - 1.6), (z0 + 2.5, r - 2.2)], material="intake", inward=True)
        ring = np.array([(nx + (r - 2.2) * math.cos(a), ny + (r - 2.2) * math.sin(a), z0 + 2.5)
                         for a in np.linspace(0, 2 * np.pi, 29)])
        cap(f"{name} intake screen", "airframe", ring, (nx, ny, z0 + 2.5), material="screen",
            outward=lambda P: np.broadcast_to(np.array([0, 0, -1.0]), P.shape))
        revolve_z(f"{name} exhaust", "airframe", nx, ny, [(z1 - 0.5, r - 1.9), (z1 + 3.0, r - 2.0), (z1 + 6.0, r - 2.4)],
                  material="exhaust")
        revolve_z(f"{name} exhaust inside", "airframe", nx, ny, [(z1 + 1.0, r - 2.8), (z1 + 6.0, r - 2.6)], material="soot",
                  inward=True)
        ring = np.array([(nx + (r - 2.8) * math.cos(a), ny + (r - 2.8) * math.sin(a), z1 + 1.0) for a in np.linspace(0, 2 * np.pi, 29)])
        cap(f"{name} exhaust end", "airframe", ring, (nx, ny, z1 + 1.0), material="soot",
            outward=lambda P: np.broadcast_to(np.array([0, 0, 1.0]), P.shape))
        # The mount: a faired strut from the pylon's side into the nacelle.
        x_in, x_out = side * 11.0, side * (abs(nx) - r + 2.0)
        lo_x, hi_x = sorted((x_in, x_out))
        strut_s = pchip([(z0 + 6, 0.2), (z0 + 10, 1.0), (z1 - 12, 1.0), (z1 - 6, 0.2)])

        def strut(z, lo_x=lo_x, hi_x=hi_x):
            s = float(strut_s(z))
            yc, hh = ny - 0.5, 3.4 * s + 0.3
            poly = [(hi_x, yc - hh), (hi_x, yc + hh), (lo_x, yc + hh), (lo_x, yc - hh)]
            return fillet_ring(poly, [hh * 0.9] * 4, per_arc=4)
        loft(f"{name} engine mount", "airframe", list(np.linspace(z0 + 6, z1 - 6, 14)), strut, closed=True, caps=(True, True))


# ------------------------------------------------------------------------------------------------ rotor blades


def naca(t, n=14):
    """A symmetric NACA aerofoil of thickness t (chord 1): points round it from the trailing edge over the top."""
    xs = (1 - np.cos(np.linspace(0, np.pi, n // 2 + 1))) / 2          # 0 (leading) .. 1 (trailing), clustered at both
    yt = 5 * t * (0.2969 * np.sqrt(xs) - 0.1260 * xs - 0.3516 * xs ** 2 + 0.2843 * xs ** 3 - 0.1036 * xs ** 4)
    upper = list(zip(xs[::-1], yt[::-1]))              # trailing -> leading over the top
    lower = list(zip(xs[1:-1], -yt[1:-1]))             # leading -> trailing underneath
    return upper + lower


def build_blades():
    foil = naca(0.12)
    spans = [14.0, 15.5, 20.0, 30.0, 45.0, 60.0, 75.0, 90.0, 105.0, 120.0, 132.0, G.ROTOR_R - 8.0, G.ROTOR_R - 3.0,
             G.ROTOR_R - 0.8]
    droop = math.radians(G.BLADE_DROOP)
    chord = G.BLADE_CHORD
    for name, (hx, hy, hz) in (("f", G.FWD_HUB), ("r", G.AFT_HUB)):
        for i in range(3):
            bone = f"rotor_{name}_blade_{i}"
            rings = []
            for r in spans:
                twist = math.radians(8.0 - 12.0 * (r - 14.0) / (G.ROTOR_R - 14.0))
                c = chord * (0.86 if r < 16.0 else 1.0)
                ring = []
                for fx, fy in foil:
                    zc = (fx - 0.25) * c                     # the quarter chord on the pitch axis; leading edge toward -z
                    yc = fy * c
                    z = zc * math.cos(twist) - yc * math.sin(twist)
                    y = zc * math.sin(twist) + yc * math.cos(twist)
                    # Droop at rest: the blade bends down from the hub, as the boxes' -3 degrees.
                    ring.append((hx + r * math.cos(droop), hy - r * math.sin(droop) + y, hz + z))
                rings.append(ring)
            P = np.array(rings)
            axis = lambda P, hy=hy, hz=hz: np.stack([np.zeros(P.shape[:2]), P[..., 1] - (hy - (P[..., 0] - hx) * math.sin(droop)),
                                                     P[..., 2] - hz], -1)
            add(Surface(f"blade {name}{i}", bone, P, material="blade", outward=axis, closed=True))
            tip = P[-1]
            cap(f"blade tip {name}{i}", bone, np.concatenate([tip, tip[:1]]), tip.mean(0) + np.array([0.6, 0, 0]),
                material="blade", outward=lambda P: np.broadcast_to(np.array([1.0, 0, 0]), P.shape))


def revolve_y(name, bone, cx, cz, profile, n=24, material="metal"):
    """A surface of revolution about the vertical axis through (cx, cz): profile = [(y, r), ...]."""
    angles = np.linspace(0, 2 * np.pi, n, endpoint=False)
    P = np.array([[(cx + r * math.cos(a), y, cz + r * math.sin(a)) for a in angles] for y, r in profile], float)
    axis = lambda P: np.stack([P[..., 0] - cx, np.zeros(P.shape[:2]), P[..., 2] - cz], -1)
    return add(Surface(name, bone, P, material=material, outward=axis, closed=True))


def build_rotor_heads():
    """Masts, the hubs with their swashplates and caps, and each blade's grip (a tapered cuff with its pitch horn)."""
    for name, (hx, hy, hz), mast_from in (("front", G.FWD_HUB, 63.5), ("rear", G.AFT_HUB, 85.5)):
        revolve_y(f"{name} mast", "airframe", hx, hz, [(mast_from, 1.9), (hy - 4.6, 1.9), (hy - 4.3, 3.6), (hy - 3.5, 3.6),
                                                        (hy - 3.2, 1.7), (hy - 2.4, 1.7)])
        bone = f"rotor_{name}"
        revolve_y(f"{name} hub", bone, hx, hz, [(hy - 2.6, 1.8), (hy - 2.3, 4.3), (hy - 1.6, 4.8), (hy + 1.0, 4.8), (hy + 1.6, 4.2),
                                                 (hy + 1.9, 2.6), (hy + 3.0, 2.2), (hy + 3.6, 1.2), (hy + 3.8, 0.05)], material="hub")
        tag = name[0]
        droop = math.radians(G.BLADE_DROOP)
        for i in range(3):
            b = f"rotor_{tag}_blade_{i}"
            tube(f"{name} grip {i}", b, (hx + 4.0, hy, hz), (hx + 14.5 * math.cos(droop), hy - 14.5 * math.sin(droop), hz), 1.35,
                 n=14, material="hub")
            tube(f"{name} cuff {i}", b, (hx + 11.0, hy - 11.0 * math.sin(droop), hz),
                 (hx + 15.0 * math.cos(droop), hy - 15.0 * math.sin(droop), hz), 1.7, n=14, material="hub")
            tube(f"{name} pitch horn {i}", b, (hx + 6.0, hy - 0.3, hz - 1.2), (hx + 6.0, hy - 0.3, hz - 3.6), 0.45, n=8, material="metal")
            tube(f"{name} pitch link {i}", b, (hx + 6.0, hy - 0.6, hz - 3.4), (hx + 6.0, hy - 4.2, hz - 3.4), 0.3, n=8, material="metal")


# ------------------------------------------------------------------------------------------------ wheels and gear


def revolve_x(name, bone, cx, cy, cz, profile, n=24, material="rubber", inward=False):
    """A surface of revolution about the axle through (cx, cy, cz) along x: profile = [(dx, r), ...]."""
    angles = np.linspace(0, 2 * np.pi, n, endpoint=False)
    P = np.array([[(cx + dx, cy + r * math.cos(a), cz + r * math.sin(a)) for a in angles] for dx, r in profile], float)
    axis = lambda P: np.stack([np.zeros(P.shape[:2]), P[..., 1] - cy, P[..., 2] - cz], -1)
    s = add(Surface(name, bone, P, material=material, outward=axis, closed=True))
    if inward:
        s.N = -s.N
    return s


def tyre(name, bone, cx, cy, cz, width, r_out):
    w = width / 2
    r_in = r_out * 0.6
    profile = [(-w + 0.2, r_in), (-w, r_in + 0.6), (-w, r_out - 1.4), (-w + 0.35, r_out - 0.5), (-w + 1.0, r_out - 0.1),
               (0.0, r_out), (w - 1.0, r_out - 0.1), (w - 0.35, r_out - 0.5), (w, r_out - 1.4), (w, r_in + 0.6), (w - 0.2, r_in)]
    revolve_x(name, bone, cx, cy, cz, profile, material="rubber")
    for dx, sgn in ((-w + 0.4, -1.0), (w - 0.4, 1.0)):
        ring = np.array([(cx + dx, cy + r_in * math.cos(a), cz + r_in * math.sin(a)) for a in np.linspace(0, 2 * np.pi, 25)])
        cap(f"{name} hub", bone, ring, (cx + dx + 0.3 * sgn, cy, cz), material="hub",
            outward=lambda P, s=sgn: np.broadcast_to(np.array([s, 0, 0]), P.shape))


def tube(name, bone, a, b, radius, n=12, material="metal"):
    a, b = np.asarray(a, float), np.asarray(b, float)
    axis = b - a
    length = np.linalg.norm(axis)
    axis /= length
    helper = np.array([0, 0, 1.0]) if abs(axis[2]) < 0.9 else np.array([1.0, 0, 0])
    u = np.cross(axis, helper)
    u /= np.linalg.norm(u)
    v = np.cross(axis, u)
    angles = np.linspace(0, 2 * np.pi, n, endpoint=False)
    rings = [[p + radius * (math.cos(t) * u + math.sin(t) * v) for t in angles] for p in (a, b)]
    s = add(Surface(name, bone, np.array(rings), material=material,
                    outward=lambda P: P - (a + np.einsum("ijk,k->ij", P - a, axis)[..., None] * axis), closed=True))
    return s


def build_gear():
    for sx, tag in ((1, "r"), (-1, "l")):
        fx, rz = sx * G.GEAR_FRONT_X, G.GEAR_FRONT_Z
        for dx in (-2.4, 2.4):
            tyre(f"front tyre {tag}{dx:+.0f}", f"wheel_f{tag}", fx + dx, G.GEAR_R, rz, 3.6, G.GEAR_R)
        tube(f"front axle {tag}", "airframe", (fx - 4.4, G.GEAR_R, rz), (fx + 4.4, G.GEAR_R, rz), 0.7)
        tube(f"front oleo {tag}", "airframe", (fx, G.GEAR_R, rz), (fx, G.GEAR_R + 3.2, rz), 1.05, material="chrome")
        tube(f"front leg {tag}", "airframe", (fx, G.GEAR_R + 3.0, rz), (fx * 0.97, G.BELLY_Y + 4.0, rz + 0.5), 1.35)
        tube(f"front brace {tag}", "airframe", (fx, G.GEAR_R + 1.5, rz), (fx * 0.9, G.BELLY_Y + 3.0, rz - 5.0), 0.55)
        rx, rrz = sx * G.GEAR_REAR_X, G.GEAR_REAR_Z
        tyre(f"rear tyre {tag}", f"wheel_r{tag}", rx, G.GEAR_R, rrz, 4.4, G.GEAR_R)
        tube(f"rear axle {tag}", "airframe", (rx - 3.0, G.GEAR_R, rrz), (rx + 3.0, G.GEAR_R, rrz), 0.7)
        tube(f"rear fork {tag}", "airframe", (rx + sx * 2.9, G.GEAR_R, rrz), (rx + sx * 2.9, G.GEAR_R + 4.5, rrz - 1.0), 0.7)
        tube(f"rear oleo {tag}", "airframe", (rx + sx * 2.9, G.GEAR_R + 4.0, rrz - 1.0), (rx * 0.97, G.BELLY_Y + 4.5, rrz - 1.5), 1.2,
             material="chrome")
        tube(f"rear leg {tag}", "airframe", (rx * 0.97, G.BELLY_Y + 1.0, rrz - 1.5), (rx * 0.95, G.BELLY_Y + 5.5, rrz - 1.8), 1.5)


# ------------------------------------------------------------------------------------------------ the moving panels


def slab(name, bone, lo, hi, material="paint", faces=("down", "up", "east", "west", "north", "south"), holes=None):
    """A box as six flat panels (each its own texture island), optionally with rectangular holes on its x faces."""
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    spec = {
        "down": ([[(x0, y0, z0), (x1, y0, z0)], [(x0, y0, z1), (x1, y0, z1)]], (0, -1, 0)),
        "up": ([[(x0, y1, z0), (x1, y1, z0)], [(x0, y1, z1), (x1, y1, z1)]], (0, 1, 0)),
        "north": ([[(x0, y0, z0), (x1, y0, z0)], [(x0, y1, z0), (x1, y1, z0)]], (0, 0, -1)),
        "south": ([[(x0, y0, z1), (x1, y0, z1)], [(x0, y1, z1), (x1, y1, z1)]], (0, 0, 1)),
    }
    for face in faces:
        if face in ("east", "west"):
            x, nx = (x1, 1.0) if face == "east" else (x0, -1.0)
            zs = sorted({z0, z1, *[h[0] for h in (holes or [])], *[h[1] for h in (holes or [])]})
            ys = sorted({y0, y1, *[h[2] for h in (holes or [])], *[h[3] for h in (holes or [])]})
            kinds = grid_kinds(zs, ys, holes or [], inside=(face == "west" and holes is not None and nx < 0 and False))
            add(Surface(f"{name} {face}", bone, plane_x(x, zs, ys), kinds, material=material, normals=(nx, 0, 0)))
        else:
            P, n = spec[face]
            add(Surface(f"{name} {face}", bone, np.array(P, float), material=material, normals=n))


def build_panels():
    # The crew door, split as the real one: the upper half (its window glazed, one pane on its outer face) slides aft
    # on rails outside the skin; the lower half folds out and down on a hinge at the sill into the boarding steps.
    dx0, dx1 = G.OUT_HALF + 0.3, G.OUT_HALF + 0.9
    z0, z1 = G.DOOR_Z
    split = G.DOOR_STEP_TOP
    win = (z0 + 2.5, z1 - 2.5, 33.0, 41.0)
    slab("crew door", "crew_door", (dx0, split, z0), (dx1, G.DOOR_TOP, z1), faces=("down", "up", "north", "south"))
    zs = sorted({z0, z1, win[0], win[1]})
    ys = sorted({split, G.DOOR_TOP, win[2], win[3]})
    add(Surface("crew door outside", "crew_door", plane_x(dx1, zs, ys), grid_kinds(zs, ys, [(*win, GLASS)]), normals=(1, 0, 0)))
    add(Surface("crew door inside", "crew_door", plane_x(dx0, zs, ys), grid_kinds(zs, ys, [(*win, GLASS)], inside=True),
                material="interior", normals=(-1, 0, 0)))
    slab("crew door step", "crew_door_step", (dx0, G.FLOOR_Y - 0.3, z0), (dx1, split - 0.05, z1),
         faces=("down", "up", "north", "south", "east"))
    zs, ys = [z0, z1], [G.FLOOR_Y - 0.3, split - 0.05]
    add(Surface("crew door step inside", "crew_door_step", plane_x(dx0, zs, ys), material="interior", normals=(-1, 0, 0)))
    # The ramp's skin: its underside is the aircraft's tail when it is shut.
    slab("ramp", "ramp", (-G.IN_HALF, G.FLOOR_Y - 3.0, G.CABIN_REAR), (G.IN_HALF, G.FLOOR_Y - 0.02, G.CABIN_REAR + G.RAMP_LEN),
         faces=("down", "east", "west", "south"))
    for sx in (-1, 1):
        lo, hi = sorted((sx * G.IN_HALF, sx * (G.IN_HALF - 1.0)))
        slab("ramp wall", "ramp", (lo, G.FLOOR_Y, G.CABIN_REAR), (hi, G.FLOOR_Y + 5.0, G.CABIN_REAR + G.RAMP_LEN),
             faces=("up", "east", "west", "south"))
    slab("ramp door", "ramp_door", (-G.IN_HALF, 40.0, G.CABIN_REAR + 15.0), (G.IN_HALF, G.CABIN_TOP, G.CABIN_REAR + 17.0))


# ------------------------------------------------------------------------------------------------ the texture


def pack():
    order = sorted(SURFACES, key=lambda s: -s.lv)
    density = TEXELS
    while True:
        x = y = row_h = 0
        ok = True
        for s in order:
            d = max(density, s.density * density / TEXELS) if s.density else density
            w = max(2, int(math.ceil(s.lu * d)))
            h = max(2, int(math.ceil(s.lv * d)))
            if w + 2 * PAD > ATLAS:
                ok = False
                break
            if x + w + 2 * PAD > ATLAS:
                x, y = 0, y + row_h
                row_h = 0
            s.rect = (x + PAD, y + PAD, w, h)
            x += w + 2 * PAD
            row_h = max(row_h, h + 2 * PAD)
        if ok and y + row_h <= ATLAS:
            return density
        density *= 0.92


def hash3(ix, iy, iz, seed):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 2654435761)
    h = (h ^ (h >> 13)) * 1274126177
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def value_noise(P, scale, seed=0):
    q = P / scale
    i = np.floor(q).astype(np.int64)
    f = q - i
    f = f * f * (3 - 2 * f)
    out = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[..., 0] if dx else 1 - f[..., 0]) * (f[..., 1] if dy else 1 - f[..., 1]) * (f[..., 2] if dz else 1 - f[..., 2])
                out = out + w * hash3(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz, seed)
    return out


def fbm(P, scale, octaves=4, seed=0):
    total, amp, norm = 0.0, 1.0, 0.0
    for o in range(octaves):
        total = total + amp * value_noise(P, scale / (2 ** o), seed + o * 17)
        norm += amp
        amp *= 0.5
    return total / norm


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)


BASE = {
    "paint": (0.290, 0.314, 0.226), "cockpit": (0.205, 0.214, 0.198), "interior": (0.36, 0.38, 0.33),
    "metal": (0.42, 0.43, 0.41), "chrome": (0.66, 0.67, 0.66), "rubber": (0.085, 0.085, 0.082), "hub": (0.33, 0.34, 0.31),
    "blade": (0.205, 0.215, 0.195), "intake": (0.30, 0.31, 0.29), "screen": (0.12, 0.125, 0.12), "exhaust": (0.20, 0.19, 0.18),
    "soot": (0.04, 0.04, 0.04),
}

FRAMES_Z = [-112.0, -81.0, -66.0] + [float(z) for z in np.arange(-56.0, 66.0, 16.0)] + [66.0, 80.0, 94.0, 104.0]
SEAMS_Y = [STRIP_Y[0], 22.0, 26.0, 46.0]


def line_mask(d, width):
    """1 on a line (|d| < width/2), fading over a texel."""
    return 1.0 - smooth(width * 0.5, width * 0.5 + 0.25, np.abs(d))


def nearest(values, x):
    values = np.asarray(values, float)
    idx = np.clip(np.searchsorted(values, x), 1, len(values) - 1)
    return np.minimum(np.abs(x - values[idx - 1]), np.abs(x - values[idx]))


def text_mask(text, height_px, font_path="C:/Windows/Fonts/arialbd.ttf"):
    """A white-on-black image of the text, height_px tall."""
    try:
        font = ImageFont.truetype(font_path, int(height_px * 1.35))
    except OSError:
        font = ImageFont.load_default()
    left, top, right, bottom = font.getbbox(text)
    img = Image.new("L", (right - left + 4, bottom - top + 4), 0)
    ImageDraw.Draw(img).text((2 - left, 2 - top), text, fill=255, font=font)
    return np.asarray(img, float) / 255.0


def flag(P, N, side, z0=79.0, z1=91.0, y0=73.0, y1=79.4):
    """A subdued US flag on the aft pylon's side (0..1 of black): stripes, the canton toward the nose, its stars."""
    on = (P[..., 0] * side > 0) & (N[..., 0] * side > 0.55)
    zr = (P[..., 2] - z0) / (z1 - z0)          # 0 at the nose end: the canton leads on both sides
    yr = (y1 - P[..., 1]) / (y1 - y0)
    inside = on & (zr >= 0) & (zr < 1) & (yr >= 0) & (yr < 1)
    stripe = (np.floor(yr * 13).astype(int) % 2 == 0)
    canton = (zr < 0.4) & (yr < 7 / 13)
    sz, sy = zr / 0.4 * 6, yr / (7 / 13) * 5
    star = (np.hypot(np.mod(sz, 1.0) - 0.5, np.mod(sy, 1.0) - 0.5) < 0.22) & canton
    m = np.where(canton, np.where(star, 0.35, 0.95), np.where(stripe, 0.9, 0.45))
    edge = line_mask(np.minimum(np.minimum(zr, 1 - zr) * (z1 - z0), np.minimum(yr, 1 - yr) * (y1 - y0)), 0.2)
    return np.where(inside, np.maximum(m, edge), 0.0)


def stencil(P, N, side, text, z0, z1, y0, y1):
    """Where a side-on text panel (z0..z1, y0..y1) falls on these texels: 0..1. Text reads correctly from outside:
    on the right side it runs toward the nose, on the left toward the tail."""
    mask = text_mask(text, 64)
    h, w = mask.shape
    on = (P[..., 0] * side > 0) & (N[..., 0] * side > 0.55)
    zr = (P[..., 2] - z0) / (z1 - z0)
    yr = (y1 - P[..., 1]) / (y1 - y0)
    if side > 0:
        zr = 1 - zr
    inside = on & (zr >= 0) & (zr < 1) & (yr >= 0) & (yr < 1)
    out = np.zeros(P.shape[:2])
    if inside.any():
        out[inside] = mask[np.clip((yr[inside] * h).astype(int), 0, h - 1), np.clip((zr[inside] * w).astype(int), 0, w - 1)]
    return out


PANEL_BANDS_Y = [STRIP_Y[0], 22.0, 26.0, 46.0, 58.0, 66.0, 74.0, 82.0]
# The side bays that take an inspection hatch, and those kept clear (windows, doors and markings), authored frame.
HATCH_BANDS = [(STRIP_Y[0] + 0.8, 21.4), (26.6, 32.6), (41.6, 45.4)]


def hashed(*ints):
    """0..1, the same for the same integers (a panel's tone, a hatch's place)."""
    with np.errstate(over="ignore"):              # the products wrap, as a hash's should
        h = np.zeros(np.broadcast(*ints).shape, np.uint64)
        for k, i in enumerate(ints):
            h = h ^ (np.asarray(i, np.int64).astype(np.uint64) * np.uint64((73856093, 19349663, 83492791, 2654435761, 97531)[k % 5]))
        h = (h ^ (h >> np.uint64(13))) * np.uint64(1274126177)
        out = ((h ^ (h >> np.uint64(16))) & np.uint64(0xFFFF)).astype(np.float64) / 65535.0
    return out if out.ndim else float(out)


def rivet_row(along, across, pitch=1.2, radius=0.17):
    """1 on the rivets of a row: `across` the distance from the row's line, `along` the distance along it."""
    a = np.mod(along, pitch) - pitch / 2
    return 1.0 - smooth(radius * 0.55, radius, np.sqrt(a * a + across * across))


def rect_edge(D, z0, z1, y0, y1):
    """Distance to a rectangle's outline in the (z, y) side plane, inside it; 9 outside."""
    z, y = D[..., 2], D[..., 1]
    inside = (z > z0) & (z < z1) & (y > y0) & (y < y1)
    d = np.minimum(np.minimum(z - z0, z1 - z), np.minimum(y - y0, y1 - y))
    return np.where(inside, d, 9.0)


def hatches(side):
    """Small inspection hatches in the side bays between the frames: (z0, z1, y0, y1), placed by hash, clear of the
    openings and the markings."""
    out = []
    keep_clear = [(o[0] - 1.5, o[1] + 1.5, o[2] - 1.5, o[3] + 1.5) for o in openings(side)]
    keep_clear += [(-49.0, -9.0, 24.0, 32.0), (-41.0, -29.0, 16.0, 19.0), (-21.0, -12.0, 13.0, 22.0)]
    frames = sorted(FRAMES_Z)
    for i, (za, zb) in enumerate(zip(frames, frames[1:])):
        if zb - za < 7.0 or za < -112.0 or zb > G.CABIN_REAR:
            continue
        for j, (ya, yb) in enumerate(HATCH_BANDS):
            if hashed(i, j, side + 2) > 0.5:
                continue
            w = 2.5 + (zb - za - 4.5) * 0.6 * hashed(i, j, side + 2, 7)
            h = min(yb - ya - 0.6, 2.0 + 3.5 * hashed(i, j, side + 2, 11))
            z0 = za + 1.2 + (zb - za - 2.4 - w) * hashed(i, j, side + 2, 13)
            y0 = ya + 0.3 + (yb - ya - 0.6 - h) * hashed(i, j, side + 2, 17)
            box = (z0, z0 + w, y0, y0 + h)
            if any(box[0] < c[1] and box[1] > c[0] and box[2] < c[3] and box[3] > c[2] for c in keep_clear):
                continue
            out.append(box)
    return out


def paint_surface(s):
    P, N, kind = s.texel_grid()
    # Where things were placed (seams, hatches, markings, soot, walkways) is read in the authored frame; the noise and
    # the roof's centre seam in the built one, so the grown parts carry them unstretched.
    D = s.texel_design
    if s.material == "glass":
        # The clipped panes: dark, slightly blue-green glass (one colour: the pieces all point at its middle).
        col = np.broadcast_to(np.array([0.085, 0.115, 0.125]), P.shape).copy()
        return col, np.full(P.shape[:2], 0.26)
    base = np.array(BASE.get(s.material, BASE["paint"]))
    col = np.broadcast_to(base, P.shape).copy()
    alpha = np.ones(P.shape[:2])
    big = fbm(P, 40.0, 4, seed=3)
    small = fbm(P, 3.0, 3, seed=11)
    up = np.clip(N[..., 1], -1, 1)
    if s.material in ("paint",):
        # Faded where the sun beats down, grimier low down and underneath, a mottle of touch-ups.
        lum = 1.0 + 0.06 * (big - 0.5) + 0.04 * (small - 0.5)
        lum += 0.06 * np.clip(up, 0, 1) - 0.12 * np.clip(-up, 0, 1)
        lum -= 0.10 * (1 - smooth(8.0, 22.0, D[..., 1])) * (np.abs(D[..., 0]) > 10)
        col = col * lum[..., None]
        fade = 0.05 * np.clip(up, 0, 1)[..., None]
        col = col * (1 - fade) + fade * np.array([0.42, 0.43, 0.40])
        # Panel seams: frames round the fuselage, longerons along it, and the pylons' and sponsons' own panels.
        dz = nearest(FRAMES_Z, D[..., 2])
        seams = line_mask(dz, 0.32)
        on_side = np.abs(N[..., 0]) > 0.75
        seams = np.maximum(seams, line_mask(nearest(SEAMS_Y, D[..., 1]), 0.3) * on_side)
        seams = np.maximum(seams, line_mask(np.abs(P[..., 0]) - 0.0, 0.25) * (up > 0.85) * (np.abs(D[..., 2]) < 120))
        # Longitudinal seams over the roof and belly, and smaller access panels between the frames.
        flat = np.abs(N[..., 1]) > 0.8
        seams = np.maximum(seams, line_mask(nearest([-13.0, -6.5, 6.5, 13.0], D[..., 0]), 0.28) * flat)
        hatch = np.zeros(P.shape[:2])
        for z0, z1, y0, y1 in ((-60.0, -52.0, 34.0, 40.0), (-8.0, 2.0, 15.0, 21.0), (30.0, 40.0, 34.0, 40.0), (70.0, 80.0, 58.0, 64.0),
                               (90.0, 100.0, 76.0, 82.0), (-108.0, -100.0, 50.0, 55.0)):
            inside_z = (D[..., 2] > z0) & (D[..., 2] < z1)
            inside_y = (D[..., 1] > y0) & (D[..., 1] < y1)
            edge = np.minimum(np.minimum(np.abs(D[..., 2] - z0), np.abs(D[..., 2] - z1)) * inside_y,
                              np.where(inside_z, np.minimum(np.abs(D[..., 1] - y0), np.abs(D[..., 1] - y1)), 9.0))
            hatch = np.maximum(hatch, line_mask(np.where(inside_y | inside_z, edge, 9.0), 0.26) * on_side * (inside_z | inside_y)
                               * (D[..., 2] > z0 - 0.2) * (D[..., 2] < z1 + 0.2) * (D[..., 1] > y0 - 0.2) * (D[..., 1] < y1 + 0.2))
        seams = np.maximum(seams, hatch)
        if "pylon" in s.name:
            seams = np.maximum(seams, line_mask(nearest([58.0, 66.0, 74.0, 82.0], D[..., 1]), 0.3) * on_side)
        # Each panel its own tone: repainted and weathered panels never quite match.
        fz = np.searchsorted(np.array(FRAMES_Z), D[..., 2])
        band = np.searchsorted(np.array(PANEL_BANDS_Y), D[..., 1])
        tone = hashed(fz, band, (D[..., 0] > 0).astype(np.int64), (np.abs(N[..., 1]) > 0.8).astype(np.int64))
        col *= (1 + 0.06 * (tone - 0.5))[..., None]
        col = col * (1 - 0.05 * tone[..., None]) + (0.05 * tone)[..., None] * np.array([0.31, 0.29, 0.21])
        # Inspection hatches in the side bays, their screws at the corners.
        screws = np.zeros(P.shape[:2])
        for side in (1, -1):
            facing = (D[..., 0] * side > 0) & on_side
            if not facing.any():
                continue
            for z0, z1, y0, y1 in hatches(side):
                e = rect_edge(D, z0, z1, y0, y1)
                seams = np.maximum(seams, line_mask(e, 0.2) * facing)
                for cz, cy in ((z0 + 0.45, y0 + 0.45), (z1 - 0.45, y0 + 0.45), (z0 + 0.45, y1 - 0.45), (z1 - 0.45, y1 - 0.45)):
                    d = np.hypot(D[..., 2] - cz, D[..., 1] - cy)
                    screws = np.maximum(screws, (1.0 - smooth(0.1, 0.2, d)) * facing)
        # Soft shadow either side of every seam (the panels' edges stand a little proud), then the seam itself.
        near = np.minimum(dz, np.where(on_side, nearest(SEAMS_Y, D[..., 1]), 9.0))
        col *= (1 - 0.07 * (1 - smooth(0.15, 1.1, near)))[..., None]
        col *= (1 - 0.32 * seams)[..., None]
        col *= (1 - 0.35 * screws)[..., None]
        # Rivet rows: both sides of each frame, and along the side seams.
        riv = rivet_row(D[..., 1] + D[..., 0], dz - 0.55)
        riv = np.maximum(riv, rivet_row(D[..., 2], nearest(SEAMS_Y, D[..., 1]) - 0.5) * on_side)
        col *= (1 + 0.16 * riv)[..., None]
        # Chipped paint, bare aluminium showing: near seams and edges, worst round the crew door and the ramp.
        traffic = smooth(4.0, 0.0, np.abs(D[..., 2] - (G.DOOR_Z[0] + G.DOOR_Z[1]) / 2) - 6.0) * (D[..., 0] > 0)
        traffic = np.maximum(traffic, smooth(G.CABIN_REAR - 6.0, G.CABIN_REAR, D[..., 2]) * (D[..., 1] < 30.0))
        chip = fbm(P, 0.7, 3, seed=51)
        worn = (chip > 0.75 - 0.05 * traffic) & ((near < 0.9) | (traffic > 0.3))
        col[worn] = col[worn] * 0.5 + np.array([0.43, 0.44, 0.42]) * 0.5
        # Rain and grime streaking down from the seams and rivets, and a fine paint grain.
        streak = fbm(P * np.array([1.0, 0.06, 1.0]), 1.6, 3, seed=29)
        col *= (1 - 0.07 * smooth(0.55, 0.85, streak) * on_side)[..., None]
        col *= (1 + 0.025 * (fbm(P, 0.45, 2, seed=61) - 0.5))[..., None]
        # Oil and hydraulic fluid seeping from under the engines and the transmissions, running aft and down.
        oil = smooth(58.0, 70.0, D[..., 2]) * (1 - smooth(104.0, 114.0, D[..., 2])) * smooth(44.0, 50.0, D[..., 1])
        oil *= 1 - smooth(54.0, 62.0, D[..., 1])
        oil *= smooth(0.6, 0.85, fbm(P * np.array([1.0, 0.12, 1.0]), 1.4, 3, seed=73))
        col = col * (1 - 0.3 * oil)[..., None] + (0.3 * oil)[..., None] * np.array([0.09, 0.075, 0.05])
        # Dust and mud thrown up low on the fuselage and the sponsons.
        mud = (1 - smooth(9.0, 17.0, D[..., 1])) * smooth(0.35, 0.65, fbm(P, 5.0, 3, seed=83))
        col = col * (1 - 0.35 * mud)[..., None] + (0.35 * mud)[..., None] * np.array([0.33, 0.29, 0.21])
        # Soot from the T55s on the aft pylon's sides behind and above the exhausts, and oil under the engines.
        soot = smooth(96.0, 112.0, D[..., 2]) * smooth(40.0, 52.0, D[..., 1]) * (1 - smooth(70.0, 86.0, D[..., 1]))
        soot *= (0.6 + 0.4 * fbm(P, 4.0, 3, seed=41))
        col = col * (1 - 0.55 * soot)[..., None] + (0.55 * soot)[..., None] * np.array([0.06, 0.06, 0.055])
        # Black anti-glare panel ahead of the windscreen; walkways on the roof in non-skid black.
        glare = (D[..., 2] < -120.0) & (up > 0.45) & (D[..., 1] > 26.0)
        walk = (up > 0.9) & (D[..., 1] > 51.5) & (np.abs(D[..., 0]) > 8.5) & (np.abs(D[..., 0]) < 13.0) & (D[..., 2] > -70) & (D[..., 2] < 50)
        col[glare] = col[glare] * 0.25 + np.array([0.05, 0.05, 0.05]) * 0.75
        col[walk] = col[walk] * 0.62 + np.array([0.09, 0.095, 0.085]) * 0.38
        # Markings: U.S. ARMY on the forward cabin sides, the serial on the aft pylon, both low-visibility black.
        for side in (1, -1):
            m = stencil(D, N, side, "U.S. ARMY", -48.0, -10.0, 24.8, 31.2)
            m = np.maximum(m, stencil(D, N, side, G.SERIAL, 86.0, 104.0, 66.0, 71.0))
            m = np.maximum(m, stencil(D, N, side, "NO STEP", -40.0, -30.0, 16.6, 18.4))
            m = np.maximum(m, stencil(D, N, side, "FUEL", -20.0, -14.0, 19.0, 20.6))
            # The filler cap under it, a ring.
            ring = np.abs(np.hypot(D[..., 2] + 17.0, D[..., 1] - 16.0) - 1.1)
            m = np.maximum(m, line_mask(ring, 0.22) * (D[..., 0] * side > 0) * (N[..., 0] * side > 0.55))
            m = np.maximum(m, flag(D, N, side))
            col = col * (1 - 0.85 * m)[..., None] + (0.85 * m)[..., None] * np.array([0.045, 0.045, 0.04])
    elif s.material == "blade":
        r = np.hypot(P[..., 0] - np.where(P[..., 2] < 0, G.FWD_HUB[0], G.AFT_HUB[0]), 0)
        hub_z = np.where(P[..., 2] < 0, G.FWD_HUB[2], G.AFT_HUB[2])
        span = np.abs(P[..., 0] - 0.0)
        lum = 1.0 + 0.08 * (big - 0.5) + 0.04 * (small - 0.5)
        col = col * lum[..., None]
        # The leading edge's erosion strip (bare metal) and the tip's yellow band.
        lead = (P[..., 2] - hub_z) < -2.6
        col[lead] = col[lead] * 0.3 + np.array([0.46, 0.47, 0.46]) * 0.7
        tip = span > G.ROTOR_R - 8.0
        col[tip] = np.array([0.80, 0.68, 0.16]) * (0.94 + 0.08 * small[tip])[..., None]
    elif s.material in ("rubber",):
        col = col * (1 + 0.15 * (small - 0.5))[..., None]
    elif s.material in ("cockpit", "interior"):
        col = col * (1 + 0.08 * (big - 0.5) + 0.05 * (small - 0.5))[..., None]
        # Aft of the cockpit bulkhead the walls carry the cabin's quilted insulation blankets (interior_paint.quilt, the
        # same as the boxed lining); the cockpit's lining is painted panels on their frames, riveted, with a stiffener
        # along the windows' sills (the user, 2026-11-21: "more detailed textures inside").
        cabin = D[..., 2] > -81.0
        if cabin.any():
            quilted = IP.quilt(D[..., 2], D[..., 1], np.array(G.MATERIALS["quilt"][:3], float)) / 255.0
            col = np.where(cabin[..., None], quilted, col)
        cockpit = ~cabin
        if cockpit.any():
            dz = nearest([-112.0, -104.0, -96.0, -88.0, -81.0], D[..., 2])
            dy = nearest([24.0, 31.0, 40.0], D[..., 1])
            seams = np.maximum(line_mask(dz, 0.3), line_mask(dy, 0.24))
            riv = (np.abs(dz - 0.5) < 0.12) & (np.abs(np.mod(D[..., 1] + D[..., 0], 1.1) - 0.55) < 0.12)
            panel_tone = 1 + 0.05 * (hashed(np.searchsorted(np.array([-112.0, -104.0, -96.0, -88.0]), D[..., 2]),
                                            np.searchsorted(np.array([24.0, 31.0, 40.0]), D[..., 1]), 5) - 0.5)
            shaded = col * panel_tone[..., None] * (1 - 0.35 * seams)[..., None] * (1 + 0.18 * riv)[..., None]
            col = np.where(cockpit[..., None], shaded, col)
    elif s.material in ("screen",):
        grid = ((np.mod(D[..., 0] * 3.0, 1.0) < 0.35) | (np.mod(D[..., 1] * 3.0, 1.0) < 0.35))
        col = np.where(grid[..., None], np.array([0.30, 0.31, 0.30]), col)
    elif s.material in ("exhaust", "soot", "metal", "chrome", "hub", "intake"):
        col = col * (1 + 0.12 * (big - 0.5) + 0.08 * (small - 0.5))[..., None]
    # Glass: a blue-grey tint, a little lighter toward the top as it catches the sky.
    if s.window is not None:
        coverage = window_coverage(s)
        g = np.array([0.075, 0.10, 0.11]) * (0.85 + 0.5 * smooth(28.0, 48.0, D[..., 1]))[..., None]
        col = col * (1 - coverage[..., None]) + g * coverage[..., None]
        alpha = 1.0 - (1.0 - s.window_alpha) * coverage
        return np.clip(col, 0, 1), alpha
    if s.clip is not None:
        # The frame round a clipped window: a dark rubber seal a few millimetres wide, blended (not cut) so it stays
        # smooth; the reveal between skin and lining takes these colours too.
        f = smooth(-0.45, -0.12, s.clip(D))
        col = col * (1 - 0.85 * f)[..., None] + (0.85 * f)[..., None] * np.array([0.05, 0.05, 0.048])
        return np.clip(col, 0, 1), alpha
    glass = kind == GLASS
    if glass.any():
        # Dark, slightly blue-green, a little lighter toward the top where it catches the sky.
        g = np.array([0.075, 0.10, 0.11]) * (0.85 + 0.5 * smooth(28.0, 48.0, D[..., 1]))[..., None]
        col[glass] = g[glass]
        alpha[glass] = 0.34
    return np.clip(col, 0, 1), alpha


def window_coverage(s, n=4):
    """How much of each texel the surface's window covers (0..1), from n x n samples."""
    x0, y0, w, h = s.rect
    u = (np.arange(w * n) + 0.5) / (w * n)
    v = (np.arange(h * n) + 0.5) / (h * n)
    cols = np.interp(u, s.cu, np.arange(len(s.cu)))
    rows = np.interp(v, s.cv, np.arange(len(s.cv)))
    ci = np.clip(np.floor(cols).astype(int), 0, len(s.cu) - 2)
    ri = np.clip(np.floor(rows).astype(int), 0, len(s.cv) - 2)
    fc = (cols - ci)[None, :, None]
    fr = (rows - ri)[:, None, None]
    R, C = np.meshgrid(ri, ci, indexing="ij")
    A = s.P
    P = ((1 - fr) * ((1 - fc) * A[R, C] + fc * A[R, C + 1]) + fr * ((1 - fc) * A[R + 1, C] + fc * A[R + 1, C + 1]))
    mask = s.window(P).astype(float)
    return mask.reshape(h, n, w, n).mean(axis=(1, 3))


def paint():
    atlas = np.zeros((ATLAS, ATLAS, 4))
    for s in SURFACES:
        col, a = paint_surface(s)
        x0, y0, w, h = s.rect
        block = np.concatenate([col, a[..., None]], -1)
        # Bleed the island into its padding so filtering at the edge never picks the gap.
        padded = np.pad(block, ((PAD, PAD), (PAD, PAD), (0, 0)), mode="edge")
        atlas[y0 - PAD:y0 + h + PAD, x0 - PAD:x0 + w + PAD] = padded
    return Image.fromarray((np.clip(atlas, 0, 1) * 255).astype(np.uint8), "RGBA")


# ------------------------------------------------------------------------------------------------ output


def bone_pivots():
    """Each bone's pivot in the authored frame (the geo file is mirrored in X)."""
    geo = json.loads((ASSETS / "geckolib/models/entity/vehicle/ch47.geo.json").read_text(encoding="utf-8"))
    return {b["name"]: np.array([-b["pivot"][0], b["pivot"][1], b["pivot"][2]], float) / 16
            for b in geo["minecraft:geometry"][0]["bones"]}


def write_mesh(path):
    # GeckoLib hands a per-bone render the pose at the bone's pivot (RenderUtil.transformToBone ends with
    # translateToPivotPoint), so each part is written relative to its bone's pivot.
    pivots = bone_pivots()
    parts = {}
    for s in SURFACES:
        q = s.quads()
        pivot = pivots.get(s.bone, np.zeros(3))
        for kind in (OPAQUE, GLASS):
            for quad in q[kind]:
                key = INSIDE if (kind == OPAQUE and s.material in INSIDE_MATERIALS) else kind
                parts.setdefault((s.bone, key), []).append(
                    [(v[0] - pivot[0], v[1] - pivot[1], v[2] - pivot[2], *v[3:]) for v in quad])
    out = bytearray(b"JMSH")
    out += struct.pack("<ii", 1, len(parts))
    total = 0
    for (bone, kind), quads in sorted(parts.items()):
        name = bone.encode("utf-8")
        code = 1 if kind == GLASS else 2 if kind == INSIDE else 0
        out += struct.pack("<H", len(name)) + name + struct.pack("<bi", code, len(quads))
        flat = np.array(quads, np.float32).reshape(-1)
        out += flat.tobytes()
        total += len(quads)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(bytes(out))
    return total, {f"{b}{' (glass)' if k == GLASS else ' (inside)' if k == INSIDE else ''}": len(q) for (b, k), q in sorted(parts.items())}


# ------------------------------------------------------------------------------------------------ reviews


LIGHT = np.array([0.45, 0.8, -0.4])
LIGHT /= np.linalg.norm(LIGHT)


def review_quads(geo, pose, texture):
    """The boxes (from the geo, posed) and the mesh, as (corners, normals, colour-per-corner-source) for the renderer."""
    import render_geo as RG
    quads, tex_size, bones, _ = RG.load_quads(geo, pose)
    tex = np.asarray(texture.convert("RGBA"), float) / 255.0
    th, tw = tex.shape[:2]
    out = []
    for name, face, pts, uv in quads:
        u = np.clip((uv[:, 0].mean() / tex_size[0] * tw).astype(int), 0, tw - 1)
        v = np.clip((uv[:, 1].mean() / tex_size[1] * th).astype(int), 0, th - 1)
        c = tex[v, u]
        if c[3] < 0.05:
            continue
        n = np.cross(pts[1] - pts[0], pts[3] - pts[0])
        ln = np.linalg.norm(n)
        if ln < 1e-9:
            continue
        out.append((pts, np.repeat((n / ln)[None], 4, 0), np.repeat(c[None, :3], 4, 0), c[3]))
    return out, bones


def bone_matrices(geo, pose):
    import render_geo as RG
    bones = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
    world = {}

    def m(name):
        if name in world:
            return world[name]
        b = bones[name]
        p = b.get("pivot", [0, 0, 0])
        r = [a + d for a, d in zip(b.get("rotation", [0, 0, 0]), (pose or {}).get(name, (0, 0, 0)))]
        mat = RG.affine((-p[0], p[1], p[2]), (-r[0], -r[1], r[2]))
        if b.get("parent") in bones:
            mat = m(b["parent"]) @ mat
        world[name] = mat
        return mat
    return {name: m(name) for name in bones}


def mesh_review_quads(geo, pose, atlas):
    mats = bone_matrices(geo, pose)
    tex = np.asarray(atlas, float) / 255.0
    out = []
    for s in SURFACES:
        mat = mats.get(s.bone, np.eye(4))
        rot = mat[:3, :3]
        # The same pieces the game draws (clipped windows included), coloured from the atlas at each corner and the
        # middle, so markings and seams show in the review.
        for k, quads in s.quads().items():
            for quad in quads:
                q = np.array(quad, float)
                pts = (np.c_[q[:, :3] * 16, np.ones(4)] @ mat.T)[:, :3]
                nrm = q[:, 5:8] @ rot.T
                uv = q[:, 3:5] * ATLAS
                def sample(u, v):
                    return tex[int(np.clip(v, 0, ATLAS - 1)), int(np.clip(u, 0, ATLAS - 1)), :3]
                mid = sample(*uv.mean(0))
                cols = np.array([sample(u, v) for u, v in uv]) * 0.5 + mid * 0.5
                out.append((pts, nrm, cols, 0.45 if k == GLASS else 1.0))
    return out


def render(quads, yaw, pitch, size=(900, 600), margin=0.06, distance=None):
    """A z-buffered render with per-pixel normals and colours (smooth shading), orthographic."""
    import render_geo as RG
    right, up, fwd = RG.view_basis(yaw, pitch)
    allp = np.concatenate([q[0] for q in quads])
    sx, sy = allp @ right, allp @ up
    w, h = size
    span = max((sx.max() - sx.min()) / (w * (1 - 2 * margin)), (sy.max() - sy.min()) / (h * (1 - 2 * margin)))
    scale = 1.0 / span
    cx, cy = (sx.max() + sx.min()) / 2, (sy.max() + sy.min()) / 2
    img = np.zeros((h, w, 3))
    sky = np.linspace(0.80, 0.93, h)[:, None]
    img[:] = np.stack([sky * 0.92, sky * 0.95, sky], -1)
    depth = np.full((h, w), np.inf)
    ordered = [q for q in quads if q[3] >= 0.99] + [q for q in quads if q[3] < 0.99]
    for pts, nrm, cols, a in ordered:
        sp = np.stack([(pts @ right - cx) * scale + w / 2, h / 2 - (pts @ up - cy) * scale, pts @ fwd], -1)
        for tri in ((0, 1, 2), (0, 2, 3)):
            A, B, Cc = sp[list(tri)]
            na, nb, nc = nrm[list(tri)]
            ca, cb, cc = cols[list(tri)]
            x0i, x1i = int(max(0, math.floor(min(A[0], B[0], Cc[0])))), int(min(w - 1, math.ceil(max(A[0], B[0], Cc[0]))))
            y0i, y1i = int(max(0, math.floor(min(A[1], B[1], Cc[1])))), int(min(h - 1, math.ceil(max(A[1], B[1], Cc[1]))))
            if x1i < x0i or y1i < y0i:
                continue
            den = (B[1] - Cc[1]) * (A[0] - Cc[0]) + (Cc[0] - B[0]) * (A[1] - Cc[1])
            if abs(den) < 1e-12:
                continue
            xs, ys = np.meshgrid(np.arange(x0i, x1i + 1) + 0.5, np.arange(y0i, y1i + 1) + 0.5)
            l1 = ((B[1] - Cc[1]) * (xs - Cc[0]) + (Cc[0] - B[0]) * (ys - Cc[1])) / den
            l2 = ((Cc[1] - A[1]) * (xs - Cc[0]) + (A[0] - Cc[0]) * (ys - Cc[1])) / den
            l3 = 1 - l1 - l2
            inside = (l1 >= -1e-6) & (l2 >= -1e-6) & (l3 >= -1e-6)
            if not inside.any():
                continue
            z = l1 * A[2] + l2 * B[2] + l3 * Cc[2]
            sub = depth[y0i:y1i + 1, x0i:x1i + 1]
            vis = inside & (z < sub - 1e-4)
            if not vis.any():
                continue
            n = l1[..., None] * na + l2[..., None] * nb + l3[..., None] * nc
            n /= np.maximum(np.linalg.norm(n, axis=-1, keepdims=True), 1e-9)
            n = np.where((n @ fwd)[..., None] > 0, -n, n)            # two-sided
            c = l1[..., None] * ca + l2[..., None] * cb + l3[..., None] * cc
            diff = np.clip(n @ LIGHT, 0, 1)
            spec = np.clip(n @ (LIGHT - fwd) / np.linalg.norm(LIGHT - fwd), 0, 1) ** 24 * 0.18
            shade = 0.42 + 0.62 * diff
            px = c * shade[..., None] + spec[..., None]
            csub = img[y0i:y1i + 1, x0i:x1i + 1]
            if a < 0.99:
                csub[vis] = csub[vis] * (1 - a) + px[vis] * a
            else:
                sub[vis] = z[vis]
                csub[vis] = px[vis]
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


def icon(quads, path, width=256, height=64):
    """The vehicle creator's icon, as generate.py's: a light grey side view, nose left, on a transparent strip."""
    scale = 4
    image = Image.new("RGBA", (width * scale, height * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    pts = np.concatenate([q[0] for q in quads])
    lo, hi = pts.min(0), pts.max(0)
    k = min((width - 16) * scale / max(1e-6, hi[2] - lo[2]), (height - 8) * scale / max(1e-6, hi[1] - lo[1]))
    faces = []
    for corners, nrm, _, _ in quads:
        n = nrm.mean(0)
        n /= max(np.linalg.norm(n), 1e-9)
        tone = int(186 + 50 * abs(n[0]) * 0.6 + 50 * max(0.0, n[1]) * 0.8)
        poly = [(width * scale / 2 + (p[2] - (lo[2] + hi[2]) / 2) * k, height * scale - 4 * scale - (p[1] - lo[1]) * k) for p in corners]
        faces.append((-corners[:, 0].mean(), poly, (min(255, tone),) * 3 + (255,)))
    faces.sort(key=lambda f: -f[0])
    for _, poly, colour in faces:
        draw.polygon(poly, fill=colour)
    image.resize((width, height), Image.Resampling.LANCZOS).save(path)


GLASS_SWATCH = Swatch("glass swatch", "glass")


def build():
    add(GLASS_SWATCH)
    zs = build_fuselage()
    build_sides(zs)
    build_airframe_extras()
    build_engines()
    build_blades()
    build_rotor_heads()
    build_gear()
    build_panels()


def main():
    build()
    density = pack()
    atlas = paint()
    TEX_OUT.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(TEX_OUT, optimize=True)
    total, parts = write_mesh(MESH_OUT)
    geo = json.loads((ASSETS / "geckolib/models/entity/vehicle/ch47.geo.json").read_text(encoding="utf-8"))
    texture = Image.open(ASSETS / "textures/entity/vehicle/ch47.png")
    for label, pose in (("closed", {"ramp": (G.RAMP_CLOSED, 0, 0)}), ("down", {"ramp": (-28.0, 0, 0)})):
        quads = review_quads(geo, pose, texture)[0] + mesh_review_quads(geo, pose, atlas)
        views = [render(quads, yaw, pitch, size=(820, 520)) for yaw, pitch in ((-125, 18), (55, 14), (-90, 0), (180, 8))]
        sheet = Image.new("RGB", (1640, 1040), (255, 255, 255))
        for n, v in enumerate(views):
            sheet.paste(v, ((n % 2) * 820, (n // 2) * 520))
        sheet.save(HERE / f"review-mesh-{label}.png", optimize=True)
        if label == "closed":
            icon(quads, ASSETS / "textures/vehicle_icon/ch47_icon.png")
    lo = min(float(np.min(s.P[..., 1])) for s in SURFACES if s.material == "rubber")
    if abs(lo) > 0.15:
        raise SystemExit(f"the tyres must stand on the ground: lowest point {lo:.2f} px")
    print(json.dumps({"quads": total, "parts": parts, "surfaces": len(SURFACES), "texels_per_px": round(density, 2),
                      "texture": ATLAS, "mesh_bytes": MESH_OUT.stat().st_size}, indent=1))


if __name__ == "__main__":
    main()
