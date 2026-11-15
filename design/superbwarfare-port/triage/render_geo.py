"""Offline textured renders and measurements of Bedrock/GeckoLib models, for triaging Superb Warfare assets.

python -I design/superbwarfare-port/triage/render_geo.py <geo.json> <texture.png> <out.png> [--title T] [--real K=V ...]
python -I design/superbwarfare-port/triage/render_geo.py --sheet <out.png> <geo.json> <texture.png> [<geo> <tex> ...]

Geometry is converted the way GeckoLib bakes it (and Blockbench imports it): X of origins and pivots is negated, X and
Y rotations are negated, and rotations apply X, then Y, then Z about the pivot. In that space the face names are the
Blockbench ones (north = -Z, east = +X). Views are orthographic, so affine texture mapping is exact.

The script prints one JSON line of measurements (extents in blocks, barrel length, facing) for TRIAGE.md.
"""
import argparse
import json
import math
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont


# ---------------------------------------------------------------- geometry

def rot_matrix(rx, ry, rz):
    """Rz . Ry . Rx (X applied first), angles in degrees, internal space."""
    x, y, z = map(math.radians, (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return mz @ my @ mx


def affine(pivot, rot):
    """4x4: translate(pivot) . R . translate(-pivot)."""
    m = np.eye(4)
    r = rot_matrix(*rot)
    p = np.asarray(pivot, float)
    m[:3, :3] = r
    m[:3, 3] = p - r @ p
    return m


# Corner order per face: top-left, top-right, bottom-right, bottom-left as seen from outside the cube
# (side faces upright; up seen from above with north at the top; down seen from below with north at the top).
def face_corners(lo, hi):
    x0, y0, z0 = lo
    x1, y1, z1 = hi
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }


NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0),
           "down": (0, -1, 0)}


def box_uv(u, v, size, mirror):
    """Blockbench box-UV rectangles [u1, v1, u2, v2] per face (internal face names)."""
    sx, sy, sz = (math.floor(s + 1e-6) for s in size)
    rects = {
        "east": (u, v + sz, sz, sy),
        "west": (u + sz + sx, v + sz, sz, sy),
        "up": (u + sz + sx, v + sz, -sx, -sz),
        "down": (u + sz + 2 * sx, v, -sx, sz),
        "south": (u + 2 * sz + sx, v + sz, sx, sy),
        "north": (u + sz, v + sz, sx, sy),
    }
    out = {}
    for face, (fu, fv, w, h) in rects.items():
        if mirror:
            fu, w = fu + w, -w
        out[face] = (fu, fv, fu + w, fv + h)
    if mirror:
        out["east"], out["west"] = out["west"], out["east"]
    return out


def face_uv(faces):
    out = {}
    for face, f in faces.items():
        if not f or "uv" not in f:
            continue
        u, v = f["uv"]
        w, h = f.get("uv_size", (0, 0))
        if face in ("up", "down"):  # Bedrock stores these flipped relative to Blockbench
            out[face] = (u + w, v + h, u, v)
        else:
            out[face] = (u, v, u + w, v + h)
    return out


def load_quads(geo_path):
    data = json.load(open(geo_path, encoding="utf-8"))
    geo = data["minecraft:geometry"][0]
    desc = geo["description"]
    tw, th = desc.get("texture_width", 64), desc.get("texture_height", 64)
    bones = {b["name"]: b for b in geo.get("bones", [])}

    world = {}

    def bone_matrix(name):
        if name in world:
            return world[name]
        b = bones[name]
        p = b.get("pivot", [0, 0, 0])
        r = b.get("rotation", [0, 0, 0])
        m = affine((-p[0], p[1], p[2]), (-r[0], -r[1], r[2]))
        parent = b.get("parent")
        if parent in bones:
            m = bone_matrix(parent) @ m
        world[name] = m
        return m

    def ancestors(name):
        while name in bones:
            yield name
            name = bones[name].get("parent")

    quads = []  # (bone, face, 4x3 corners, 4x2 uv in pixels)
    for name, b in bones.items():
        bm = bone_matrix(name)
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            inf = c.get("inflate", b.get("inflate", 0)) or 0
            lo = np.array([-(o[0] + s[0]), o[1], o[2]], float) - inf
            hi = lo + np.array(s, float) + 2 * inf
            cm = np.eye(4)
            if "rotation" in c:
                cp = c.get("pivot", [0, 0, 0])
                cr = c["rotation"]
                cm = affine((-cp[0], cp[1], cp[2]), (-cr[0], -cr[1], cr[2]))
            m = bm @ cm
            uv = c.get("uv", [0, 0])
            mirror = bool(c.get("mirror", b.get("mirror", False)))
            rects = face_uv(uv) if isinstance(uv, dict) else box_uv(uv[0], uv[1], s, mirror)
            for face, corners in face_corners(lo, hi).items():
                if face not in rects:
                    continue
                u1, v1, u2, v2 = rects[face]
                if u1 == u2 or v1 == v2:
                    continue
                pts = np.c_[np.array(corners, float), np.ones(4)] @ m.T
                quads.append((name, face, pts[:, :3], np.array([(u1, v1), (u2, v1), (u2, v2), (u1, v2)], float)))
    return quads, (tw, th), bones, None


# ---------------------------------------------------------------- measurements

def measure(quads, bones, barrel_bone="barrel"):
    pts = np.concatenate([q[2] for q in quads]) / 16.0
    lo, hi = pts.min(0), pts.max(0)

    def subtree(root):
        names = {root}
        grown = True
        while grown:
            grown = False
            for n, b in bones.items():
                if n not in names and b.get("parent") in names:
                    names.add(n)
                    grown = True
        return names

    out = {"extent_blocks": [round(float(v), 2) for v in hi - lo],
           "min": [round(float(v), 2) for v in lo], "max": [round(float(v), 2) for v in hi]}
    if barrel_bone in bones:
        names = subtree(barrel_bone)
        bp = np.concatenate([q[2] for q in quads if q[0] in names]) / 16.0
        out["barrel_z"] = [round(float(bp[:, 2].min()), 2), round(float(bp[:, 2].max()), 2)]
        axis = np.linalg.svd(bp - bp.mean(0), full_matrices=False)[2][0]  # the barrel's long axis
        along = bp @ axis
        out["barrel_length"] = round(float(along.max() - along.min()), 2)
        centre = (bp[:, 2].min() + bp[:, 2].max()) / 2
        out["faces"] = "-Z" if centre < (lo[2] + hi[2]) / 2 else "+Z"
    else:
        out["faces"] = "-Z"
    return out


# ---------------------------------------------------------------- rendering

LIGHT = np.array([0.35, 0.85, -0.4])
LIGHT /= np.linalg.norm(LIGHT)


def view_basis(yaw, pitch):
    """Camera looking at the model: yaw 0 looks along +Z (camera on the -Z side), positive pitch looks down."""
    y, p = math.radians(yaw), math.radians(pitch)
    fwd = np.array([math.sin(y) * math.cos(p), -math.sin(p), math.cos(y) * math.cos(p)])
    up_hint = np.array([0, 1, 0]) if abs(pitch) < 89 else np.array([-math.sin(y), 0, -math.cos(y)])
    right = np.cross(fwd, up_hint)
    right /= np.linalg.norm(right)
    up = np.cross(right, fwd)
    return right, up, fwd


def render(quads, tex, tex_size, yaw, pitch, size=(560, 420), margin=0.08, player=False):
    """player: leave room right of the model for a 0.6 x 1.8 block player outline (side-on views only)."""
    right, up, fwd = view_basis(yaw, pitch)
    allp = np.concatenate([q[2] for q in quads])
    sx, sy = allp @ right, allp @ up
    model_right, ground = sx.max(), sy.min()
    if player:
        sx = np.append(sx, model_right + 16 * 1.4)
        sy = np.append(sy, ground + 16 * 1.8)
    w, h = size
    span = max((sx.max() - sx.min()) / (w * (1 - 2 * margin)), (sy.max() - sy.min()) / (h * (1 - 2 * margin)))
    scale = 1.0 / span
    cx, cy = (sx.max() + sx.min()) / 2, (sy.max() + sy.min()) / 2

    def project(p):
        return np.stack([(p @ right - cx) * scale + w / 2, h / 2 - (p @ up - cy) * scale, p @ fwd], -1)

    tw, th = tex_size
    texa = np.asarray(tex.convert("RGBA"), float) / 255.0
    ttw, tth = tex.size
    color = np.zeros((h, w, 3))
    color[:] = (0.93, 0.94, 0.95)
    depth = np.full((h, w), np.inf)

    for name, face, pts, uv in quads:
        # normal after transform, from the quad itself
        e1, e2 = pts[1] - pts[0], pts[3] - pts[0]
        nn = np.cross(e2, e1)
        ln = np.linalg.norm(nn)
        if ln < 1e-9:
            continue
        nn /= ln
        if nn @ fwd >= 0:  # back face
            continue
        shade = 0.55 + 0.45 * max(0.0, float(nn @ LIGHT))
        sp = project(pts)
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = sp[list(tri)]
            ua, ub, uc = uv[list(tri)]
            x0, x1 = int(max(0, math.floor(min(a[0], b[0], c[0])))), int(min(w - 1, math.ceil(max(a[0], b[0], c[0]))))
            y0, y1 = int(max(0, math.floor(min(a[1], b[1], c[1])))), int(min(h - 1, math.ceil(max(a[1], b[1], c[1]))))
            if x1 < x0 or y1 < y0:
                continue
            den = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(den) < 1e-12:
                continue
            xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            l1 = ((b[1] - c[1]) * (xs - c[0]) + (c[0] - b[0]) * (ys - c[1])) / den
            l2 = ((c[1] - a[1]) * (xs - c[0]) + (a[0] - c[0]) * (ys - c[1])) / den
            l3 = 1 - l1 - l2
            inside = (l1 >= -1e-6) & (l2 >= -1e-6) & (l3 >= -1e-6)
            if not inside.any():
                continue
            z = l1 * a[2] + l2 * b[2] + l3 * c[2]
            sub = depth[y0:y1 + 1, x0:x1 + 1]
            vis = inside & (z < sub - 1e-4)
            if not vis.any():
                continue
            u = (l1 * ua[0] + l2 * ub[0] + l3 * uc[0]) / tw * ttw
            v = (l1 * ua[1] + l2 * ub[1] + l3 * uc[1]) / th * tth
            ui = np.clip(np.floor(u).astype(int), 0, ttw - 1)
            vi = np.clip(np.floor(v).astype(int), 0, tth - 1)
            texel = texa[vi, ui]
            vis &= texel[..., 3] > 0.5
            if not vis.any():
                continue
            sub[vis] = z[vis]
            csub = color[y0:y1 + 1, x0:x1 + 1]
            csub[vis] = texel[..., :3][vis] * shade
    img = Image.fromarray((np.clip(color, 0, 1) * 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    px = 16 * scale  # pixels per block
    if player:
        gx = (model_right + 16 * 0.8 - cx) * scale + w / 2
        gy = h / 2 - (ground - cy) * scale
        d.rectangle([gx, gy - 1.8 * px, gx + 0.6 * px, gy], outline=(200, 40, 40), width=2)
        d.text((gx - 4, gy + 4), "player", fill=(200, 40, 40), font=font(12))
    d.line([12, h - 14, 12 + px, h - 14], fill=(30, 30, 30), width=3)
    d.text((14, h - 30), "1 block", fill=(30, 30, 30), font=font(12))
    return img


def font(size):
    for name in ("arial.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def visible(quads, tex, tex_size):
    """Drop quads whose texture rectangle is fully transparent (helper points such as sight or laser markers)."""
    alpha = np.asarray(tex.convert("RGBA"))[..., 3]
    tw, th = tex_size
    kx, ky = tex.size[0] / tw, tex.size[1] / th
    out = []
    for q in quads:
        us, vs = q[3][:, 0], q[3][:, 1]
        x0, x1 = int(math.floor(us.min() * kx)), int(math.ceil(us.max() * kx))
        y0, y1 = int(math.floor(vs.min() * ky)), int(math.ceil(vs.max() * ky))
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(alpha.shape[1], max(x1, x0 + 1)), min(alpha.shape[0], max(y1, y0 + 1))
        if (alpha[y0:y1, x0:x1] > 127).any():
            out.append(q)
    return out


def contact_sheet(geo, tex_path, title, notes, barrel_bone="barrel"):
    quads, tex_size, bones, _ = load_quads(geo)
    tex = Image.open(tex_path)
    quads = visible(quads, tex, tex_size)
    m = measure(quads, bones, barrel_bone)
    flip = 180 if m["faces"] == "+Z" else 0  # yaw so the "front" view looks at the muzzle end
    views = [("front", 0 + flip, 0), ("side", 90 + flip, 0), ("top", 0 + flip, 90),
             ("three-quarter", 35 + flip, 25)]
    tiles = []
    for label, yaw, pitch in views:
        img = render(quads, tex, tex_size, yaw, pitch, player=(pitch == 0))
        ImageDraw.Draw(img).text((10, 8), label, fill=(20, 20, 20), font=font(16))
        tiles.append(img)
    tw_, th_ = tiles[0].size
    head = 70
    sheet = Image.new("RGB", (tw_ * 2 + 6, th_ * 2 + 6 + head), (255, 255, 255))
    d = ImageDraw.Draw(sheet)
    d.text((10, 8), title, fill=(0, 0, 0), font=font(22))
    d.text((10, 40), notes, fill=(60, 60, 60), font=font(14))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % 2) * (tw_ + 6), head + (i // 2) * (th_ + 6)))
    return sheet, m


def small_sheet(items, cols=5):
    tiles = []
    for geo, tex_path in items:
        quads, tex_size, bones, _ = load_quads(geo)
        tex = Image.open(tex_path)
        shown = visible(quads, tex, tex_size)
        if not shown:  # the texture does not fit this model: show the geometry with it anyway, and say so
            print(f"warning: {geo}: texture leaves every face transparent", file=sys.stderr)
            shown = quads
            tex = Image.new("RGBA", tex.size, (150, 150, 150, 255))
        quads = shown
        img = render(quads, tex, tex_size, 35, 25, size=(260, 220))
        m = measure(quads, bones)
        stem = geo.replace("\\", "/").split("/")[-1].replace(".geo.json", "")
        e = m["extent_blocks"]
        ImageDraw.Draw(img).text((6, 4), f"{stem}  {e[0]}x{e[1]}x{e[2]} m", fill=(20, 20, 20), font=font(13))
        tiles.append(img)
    rows = math.ceil(len(tiles) / cols)
    sheet = Image.new("RGB", (cols * 264, rows * 224), (255, 255, 255))
    for i, t in enumerate(tiles):
        sheet.paste(t, ((i % cols) * 264, (i // cols) * 224))
    return sheet


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sheet", help="small multi-model sheet: out.png followed by geo/texture pairs")
    ap.add_argument("--title", default="")
    ap.add_argument("--notes", default="")
    ap.add_argument("--barrel-bone", default="barrel", help="bone whose subtree is measured as the barrel or tube")
    ap.add_argument("paths", nargs="+")
    a = ap.parse_args()
    if a.sheet:
        pairs = list(zip(a.paths[0::2], a.paths[1::2]))
        small_sheet(pairs).save(a.sheet, optimize=True)
        return
    geo, tex, out = a.paths
    sheet, m = contact_sheet(geo, tex, a.title, a.notes, a.barrel_bone)
    sheet.save(out, optimize=True)
    print(json.dumps(m))


if __name__ == "__main__":
    sys.exit(main())
