"""Shared helpers for refitting Superb Warfare models to the vehicle guide (an imported model's "generator").

A vehicle's design/<vehicle>/refit.py loads the SBW source (fetched with fetch_sbw.py), and then:
- renames bones (geometry, LODs and animations alike);
- converts box UV to per-face UV, so geometry can be rescaled without moving any texel;
- rescales about the model origin, keeping the ground at y = 0;
- poses the articulated bones and fits the hitbox from the posed model;
- writes the GeckoLib geometry, LODs, texture and animations into the mod, plus rig.json and review renders.

Coordinates: "file" space is the Bedrock geometry's own (pixels, the model faces -Z after the refit). The vehicle
data's OBBs are in blocks with +Z forward, as the bulldozer's generator writes them.
"""
import copy
import json
import math
import pathlib
import sys

import numpy as np

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ASSETS = ROOT / "src/main/resources/assets/jeg"
sys.path.insert(0, str(HERE / "triage"))
import render_geo  # noqa: E402


def load_json(path):
    return json.loads(pathlib.Path(path).read_text(encoding="utf-8"))


def write_json(path, value):
    path = pathlib.Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def bones_of(geo):
    return geo["minecraft:geometry"][0]["bones"]


# ---------------------------------------------------------------- renames

def rename_bones(geo, mapping, identifier):
    """Rename bones and their parent links. Every source bone must be mapped, so nothing keeps an SBW name by
    accident; map a bone to itself to keep its name."""
    geo = copy.deepcopy(geo)
    desc = geo["minecraft:geometry"][0]["description"]
    desc["identifier"] = identifier
    for b in bones_of(geo):
        if b["name"] not in mapping:
            raise SystemExit(f"bone {b['name']!r} has no entry in the rename map")
        b["name"] = mapping[b["name"]]
        if "parent" in b:
            b["parent"] = mapping[b["parent"]]
    names = [b["name"] for b in bones_of(geo)]
    if len(names) != len(set(names)):
        raise SystemExit(f"rename map produces duplicate bones: {sorted(n for n in names if names.count(n) > 1)}")
    return geo


def rename_animations(anim, mapping, clip_names):
    """Rename bone channels and clips. clip_names maps source clip -> new name; clips not listed are dropped."""
    out = {"format_version": anim.get("format_version", "1.8.0"), "animations": {}}
    for clip, body in anim["animations"].items():
        if clip not in clip_names:
            continue
        body = copy.deepcopy(body)
        body["bones"] = {mapping.get(bone, bone): channels for bone, channels in body.get("bones", {}).items()}
        out["animations"][clip_names[clip]] = body
    return out


# ---------------------------------------------------------------- UV and scale

def to_face_uv(geo):
    """Box UV depends on cube size, so rescaling a box-UV cube would move its texture. Rewrite every box-UV cube as
    the equivalent per-face UV (the rectangles GeckoLib would use) before scaling."""
    geo = copy.deepcopy(geo)
    for b in bones_of(geo):
        for c in b.get("cubes", []):
            uv = c.get("uv", [0, 0])
            if isinstance(uv, dict):
                continue
            mirror = bool(c.get("mirror", b.get("mirror", False)))
            faces = {}
            for face, (u1, v1, u2, v2) in render_geo.box_uv(uv[0], uv[1], c["size"], mirror).items():
                if face in ("up", "down"):
                    faces[face] = {"uv": [u2, v2], "uv_size": [u1 - u2, v1 - v2]}
                else:
                    faces[face] = {"uv": [u1, v1], "uv_size": [u2 - u1, v2 - v1]}
            c["uv"] = faces
            c.pop("mirror", None)
    return geo


def scale(geo, k):
    """Scale about the origin. Run to_face_uv first. Ground (y = 0) stays on the ground."""
    geo = copy.deepcopy(geo)

    def mul(v):
        return [round(x * k, 5) for x in v]

    for b in bones_of(geo):
        if "pivot" in b:
            b["pivot"] = mul(b["pivot"])
        if "inflate" in b:
            b["inflate"] = round(b["inflate"] * k, 5)
        for c in b.get("cubes", []):
            if isinstance(c.get("uv"), list):
                raise SystemExit("scale() needs per-face UV: call to_face_uv() first")
            c["origin"] = mul(c["origin"])
            c["size"] = mul(c["size"])
            if "pivot" in c:
                c["pivot"] = mul(c["pivot"])
            if "inflate" in c:
                c["inflate"] = round(c["inflate"] * k, 5)
    return geo


def scale_animations(anim, k):
    """Position keyframes are in pixels, so they scale with the model; rotations and scales do not."""
    anim = copy.deepcopy(anim)
    for body in anim["animations"].values():
        for channels in body.get("bones", {}).values():
            pos = channels.get("position")
            if isinstance(pos, list):
                channels["position"] = [x * k if isinstance(x, (int, float)) else x for x in pos]
            elif isinstance(pos, dict):
                for t, v in pos.items():
                    if isinstance(v, list):
                        pos[t] = [x * k if isinstance(x, (int, float)) else x for x in v]
                    elif isinstance(v, dict):
                        for key in ("pre", "post", "vector"):
                            if isinstance(v.get(key), list):
                                v[key] = [x * k if isinstance(x, (int, float)) else x for x in v[key]]
    return anim


# ---------------------------------------------------------------- measuring

def posed_quads(geo, pose=None, texture=None):
    """World-space quads in file space (pixels). texture: a PIL image; when given, fully transparent faces
    (helper points) are dropped."""
    quads, tex_size, bones, _ = render_geo.load_quads(geo, pose)
    if texture is not None:
        quads = render_geo.visible(quads, texture, tex_size)
    flip = np.array([-1.0, 1.0, 1.0])  # render_geo works in GeckoLib space, where X is negated
    return [(name, face, pts * flip, uv) for name, face, pts, uv in quads], bones


def subtree(bones, root):
    names = {root}
    grown = True
    while grown:
        grown = False
        for n, b in bones.items():
            if n not in names and b.get("parent") in names:
                names.add(n)
                grown = True
    return names


def extent(quads, names=None):
    pts = np.concatenate([q[2] for q in quads if names is None or q[0] in names])
    return pts.min(0), pts.max(0)


def bone_pivot(geo, name, pose=None):
    """A bone's pivot in file space after posing its ancestors."""
    quads_bones = {b["name"]: b for b in bones_of(geo)}
    point = np.array(quads_bones[name].get("pivot", [0, 0, 0]), float)
    return transform_point(geo, quads_bones[name].get("parent"), point, pose)


def transform_point(geo, bone, point, pose=None):
    """Carry a file-space point fixed to `bone` through the posed bone chain."""
    by = {b["name"]: b for b in bones_of(geo)}
    pose = pose or {}
    p = np.array([-point[0], point[1], point[2]], float)  # into GeckoLib space
    while bone in by:
        b = by[bone]
        pv = b.get("pivot", [0, 0, 0])
        r = [a + d for a, d in zip(b.get("rotation", [0, 0, 0]), pose.get(bone, (0, 0, 0)))]
        m = render_geo.affine((-pv[0], pv[1], pv[2]), (-r[0], -r[1], r[2]))
        p = (m @ np.append(p, 1.0))[:3]
        bone = b.get("parent")
    return [round(float(-p[0]), 4), round(float(p[1]), 4), round(float(p[2]), 4)]


def obb_rows(parts):
    """parts: [(part, label, lo, hi)] in file pixels -> vehicle-data OBB dicts (blocks, +Z forward), boxes
    standing on the ground."""
    rows = []
    for part, label, lo, hi in parts:
        lo = [lo[0], max(0.0, lo[1]), lo[2]]
        rows.append({"part": part, "covers": label,
                     "x": round((lo[0] + hi[0]) / 32, 4), "y": round((lo[1] + hi[1]) / 32, 4),
                     "z": round(-(lo[2] + hi[2]) / 32, 4),
                     "half_width": round((hi[0] - lo[0]) / 32, 4), "half_height": round((hi[1] - lo[1]) / 32, 4),
                     "half_depth": round((hi[2] - lo[2]) / 32, 4)})
    return rows


def coplanar_overlaps(geo):
    """Pairs of unrotated cubes in one bone whose faces share a plane and overlap: they z-fight (guide § Model).
    Returns [(bone, i, j, axis)]."""
    found = []
    for b in bones_of(geo):
        cubes = [(i, c) for i, c in enumerate(b.get("cubes", [])) if "rotation" not in c]
        boxes = [(i, np.array(c["origin"], float) - c.get("inflate", 0),
                  np.array(c["origin"], float) + np.array(c["size"], float) + c.get("inflate", 0)) for i, c in cubes]
        for a in range(len(boxes)):
            ia, la, ha = boxes[a]
            for bb in range(a + 1, len(boxes)):
                ib, lb, hb = boxes[bb]
                for axis in range(3):
                    others = [x for x in range(3) if x != axis]
                    shared = any(abs(pa - pb) < 1e-4 for pa in (la[axis], ha[axis]) for pb in (lb[axis], hb[axis]))
                    overlap = all(min(ha[o], hb[o]) - max(la[o], lb[o]) > 1e-4 for o in others)
                    if shared and overlap:
                        found.append((b["name"], ia, ib, axis))
                        break
    return found


def resolve_zfighting(geo, push=0.04):
    """Push the smaller cube of each coplanar pair out by `push` px on the shared face, as
    design/support/generate.py does, until no pair is left. Per-face UV is unaffected."""
    geo = copy.deepcopy(geo)
    # A cube sharing faces with many others (the LAV-150's hull) takes a few rounds: each push can open a new pair.
    for _ in range(40):
        pairs = coplanar_overlaps(geo)
        if not pairs:
            return geo
        by = {b["name"]: b for b in bones_of(geo)}
        for bone, i, j, axis in pairs:
            cubes = by[bone]["cubes"]
            a, b = cubes[i], cubes[j]
            vol = lambda c: c["size"][0] * c["size"][1] * c["size"][2]
            small, big = (a, b) if vol(a) <= vol(b) else (b, a)
            # Planes as drawn, inflate included (SBW's LAV-150 has cubes inflated by -0.05): the pairs were found that way.
            s_inf, b_inf = small.get("inflate", 0), big.get("inflate", 0)
            s_lo, s_hi = small["origin"][axis] - s_inf, small["origin"][axis] + small["size"][axis] + s_inf
            planes = (big["origin"][axis] - b_inf, big["origin"][axis] + big["size"][axis] + b_inf)
            if any(abs(s_lo - p) < 1e-4 for p in planes):
                small["origin"][axis] = round(small["origin"][axis] - push, 5)
                small["size"][axis] = round(small["size"][axis] + push, 5)
            if any(abs(s_hi - p) < 1e-4 for p in planes):
                small["size"][axis] = round(small["size"][axis] + push, 5)
    raise SystemExit(f"z-fighting not resolved: {coplanar_overlaps(geo)}")


# ---------------------------------------------------------------- tracks (the shared track driver's path)
# The links' centre line is the convex hull of the running gear's circles, each grown by half a link's thickness, in
# file pixels (z, y), counter-clockwise seen from the left: rearward along the bottom, forward along the top, the way
# the links run relative to the hull as the vehicle drives forward (guide section 5, "Tracks are driven from a path").

def circle_of(geo, quads, by, bone):
    """A wheel's centre (z, y in file px) and its radius in the YZ plane, from its drawn geometry."""
    names = subtree(by, bone)
    pivot = np.array(bone_pivot(geo, bone), float)
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


def links_posed(geo, side, path, rest, travel_px, links):
    """A copy of the geometry with one side's links moved along the path (for review renders), and the pose."""
    geo = copy.deepcopy(geo)
    by = {b["name"]: b for b in bones_of(geo)}
    total = path_length(path)
    s_rest, _ = nearest_s(path, (rest[2], rest[1]))
    pose = {}
    for i in range(links):
        s = s_rest + travel_px + i * total / links
        (z, y), tangent = path_at(path, s)
        dy, dz = y - rest[1], z - rest[2]
        for name in subtree(by, f"track_link_{side}_{i}"):
            b = by[name]
            b["pivot"] = [b["pivot"][0], b["pivot"][1] + dy, b["pivot"][2] + dz]
            for c in b.get("cubes", []):
                c["origin"] = [c["origin"][0], c["origin"][1] + dy, c["origin"][2] + dz]
                if "pivot" in c:
                    c["pivot"] = [c["pivot"][0], c["pivot"][1] + dy, c["pivot"][2] + dz]
        pose[f"track_link_{side}_{i}_turn"] = (link_turn(tangent), 0, 0)
    return geo, pose


# ---------------------------------------------------------------- checks

def strip_transparent(geo, texture):
    """Drop faces whose texture rectangle is fully transparent, then cubes left with no face."""
    geo = copy.deepcopy(geo)
    desc = geo["minecraft:geometry"][0]["description"]
    alpha = np.asarray(texture.convert("RGBA"))[..., 3]
    kx, ky = texture.size[0] / desc["texture_width"], texture.size[1] / desc["texture_height"]
    dropped = 0
    for b in bones_of(geo):
        kept = []
        for c in b.get("cubes", []):
            faces = {}
            for face, f in c["uv"].items():
                (u, v), (w, h) = f["uv"], f["uv_size"]
                x0, x1 = sorted((u * kx, (u + w) * kx))
                y0, y1 = sorted((v * ky, (v + h) * ky))
                x0, y0 = max(0, int(math.floor(x0))), max(0, int(math.floor(y0)))
                x1, y1 = min(alpha.shape[1], max(int(math.ceil(x1)), x0 + 1)), min(alpha.shape[0], max(int(math.ceil(y1)), y0 + 1))
                if (alpha[y0:y1, x0:x1] > 127).any():
                    faces[face] = f
                else:
                    dropped += 1
            if faces:
                c["uv"] = faces
                kept.append(c)
        if "cubes" in b:
            b["cubes"] = kept
            if not kept:
                del b["cubes"]
    return geo, dropped


def renders_alike(geo_a, geo_b, texture, views=((35, 25), (0, 0), (90, 0))):
    """True when two geometries draw the same pixels, as the game draws them (fully transparent faces show nothing)."""
    for yaw, pitch in views:
        out = []
        for geo in (geo_a, geo_b):
            q, ts, _, _ = render_geo.load_quads(geo)
            out.append(np.asarray(render_geo.render(render_geo.visible(q, texture, ts), texture, ts, yaw, pitch)))
        if not np.array_equal(*out):
            return False
    return True



def render_identical(geo_a, geo_b, texture, views=((35, 25), (0, 0), (90, 0))):
    """True when two geometries draw the same pixels (used to prove a UV rewrite is lossless)."""
    for yaw, pitch in views:
        qa, ts, _, _ = render_geo.load_quads(geo_a)
        qb, _, _, _ = render_geo.load_quads(geo_b)
        a = np.asarray(render_geo.render(qa, texture, ts, yaw, pitch))
        b = np.asarray(render_geo.render(qb, texture, ts, yaw, pitch))
        if not np.array_equal(a, b):
            return False
    return True


def angle_of(v):
    return math.degrees(math.atan2(v[1], math.hypot(v[0], v[2])))


def silhouette_icon(geo, texture, path, pose=None, width=256, height=64):
    """The vehicle creator's selection icon, in the style of SBW's: a light grey side view, nose to the left as theirs,
    on a transparent 256 x 64 strip, faces lit by how much they face the viewer and up."""
    from PIL import Image, ImageDraw
    quads, _ = posed_quads(geo, pose, texture)
    scale = 4
    image = Image.new("RGBA", (width * scale, height * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    pts = np.concatenate([q[2] for q in quads])
    lo, hi = pts.min(0), pts.max(0)
    k = min((width - 16) * scale / max(1e-6, hi[2] - lo[2]), (height - 8) * scale / max(1e-6, hi[1] - lo[1]))
    faces = []
    for _, _, corners, _ in quads:
        n = np.cross(corners[1] - corners[0], corners[2] - corners[0])
        length = np.linalg.norm(n)
        if length < 1e-9:
            continue
        n = n / length
        tone = int(186 + 50 * max(0.0, abs(n[0])) * 0.6 + 50 * max(0.0, n[1]) * 0.8)
        poly = [(width * scale / 2 + (p[2] - (lo[2] + hi[2]) / 2) * k, height * scale - 4 * scale - (p[1] - lo[1]) * k)
                for p in corners]
        faces.append((corners[:, 0].mean(), poly, (min(255, tone),) * 3 + (255,)))
    faces.sort(key=lambda f: -f[0])  # far side (positive X, the vehicle's left in file space) first
    for _, poly, colour in faces:
        draw.polygon(poly, fill=colour)
    icon = image.resize((width, height), Image.Resampling.LANCZOS)
    path.parent.mkdir(parents=True, exist_ok=True)
    icon.save(path)


# ---------------------------------------------------------------- unique UVs for our own generators
# Our generators (sentry, bush plane, howitzer) first textured every cube from one 16-px tile per material, so every face
# of a material showed the same flat swatch and nothing could be weathered per panel (the fleet's model and texture
# review, 2026-11-19). bake_unique gives every face its own texels, at {texels} a model pixel, filled with its material,
# so the detail pass (design/ground-vehicles/detail_texture.detail_image) can seam, wear and dirty each panel.

FACE_SIZE = {"north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2)}


def bake_unique(bones, materials, texels=4, width=None, seed=7, fill=None, boost=None):
    """Rewrite each cube's UV in {bones} (generator bone dicts whose cubes carry "_material") to a unique rectangle per
    face. Returns (uv_size, faces, base): the atlas size in UV units (for the geometry's texture_width/height), one entry
    per face {bone, cube, face, material, size (px), rect (texels x0, y0, x1, y1)}, and the base texture (each face its
    material's colour, a faint grain), {texels} texels a UV unit. {boost} {material: k} gives that material's faces k
    times the texel density (their UV rectangle k times the face): a small name plate can carry legible lettering.
    {fill}(item, width, height), when given, paints a
    face instead: an (height + 2) x (width + 2) x 4 array, a texel of bleed all round (the FSV-6 lays its patterned
    material tiles this way)."""
    import random
    from PIL import Image
    rng = random.Random(seed)
    items = []
    for b in bones:
        for ci, cube in enumerate(b.get("cubes", [])):
            for face, (ia, ib) in FACE_SIZE.items():
                k = (boost or {}).get(cube["_material"], 1)
                w, h = cube["size"][ia] * k, cube["size"][ib] * k
                tw, th = max(1, math.ceil(w * texels - 1e-6)), max(1, math.ceil(h * texels - 1e-6))
                items.append({"bone": b["name"], "cube": ci, "face": face, "material": cube["_material"],
                              "size": (w, h), "tex": (tw, th), "cube_ref": cube})
    pad = 2
    area = sum((i["tex"][0] + pad) * (i["tex"][1] + pad) for i in items)
    if width is None:
        width = 64
        while width * width < area * 1.25:
            width *= 2
    width = max(width, max(i["tex"][0] + pad for i in items))
    # Shelf packing, tallest first.
    x = y = shelf = 0
    for item in sorted(items, key=lambda i: (-i["tex"][1], -i["tex"][0])):
        tw, th = item["tex"]
        if x + tw + pad > width:
            x, y, shelf = 0, y + shelf, 0
        item["rect"] = (x + 1, y + 1, x + 1 + tw, y + 1 + th)
        x += tw + pad
        shelf = max(shelf, th + pad)
    height = y + shelf
    height = ((height + texels * 16 - 1) // (texels * 16)) * texels * 16
    for item in items:
        x0, y0, _, _ = item["rect"]
        w, h = item["size"]
        uv = item["cube_ref"].setdefault("_faces", {})
        uv[item["face"]] = {"uv": [round(x0 / texels, 4), round(y0 / texels, 4)], "uv_size": [round(w, 4), round(h, 4)]}
    for b in bones:
        for cube in b.get("cubes", []):
            cube["uv"] = cube.pop("_faces")
    base = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    px = np.zeros((height, width, 4), np.uint8)
    for item in items:
        x0, y0, x1, y1 = item["rect"]
        if fill is not None:
            block = fill(item, x1 - x0, y1 - y0)
            if block is not None:
                px[y0 - 1:y1 + 1, x0 - 1:x1 + 1] = np.clip(block, 0, 255)
                continue
        rgba = materials[item["material"]]
        grain = np.array([rng.choice((-2, -1, 0, 0, 1, 2)) for _ in range((y1 - y0 + 2) * (x1 - x0 + 2))]).reshape(y1 - y0 + 2, x1 - x0 + 2)
        block = np.empty((y1 - y0 + 2, x1 - x0 + 2, 4), np.int32)
        block[..., :3] = np.array(rgba[:3])[None, None] + (grain[..., None] if rgba[3] == 255 else 0)
        block[..., 3] = rgba[3]
        # One texel of bleed round each rectangle, so mipmaps and filtering at the edge keep the face's colour.
        px[y0 - 1:y1 + 1, x0 - 1:x1 + 1] = np.clip(block, 0, 255)
    for item in items:
        item.pop("cube_ref")
    return (width / texels, height / texels), items, Image.fromarray(px, "RGBA").copy()


def paste_faces(target, source, faces, materials_kept):
    """Copy the faces of the given materials from {source} onto {target} (both at the same texel scale or {target}
    an exact multiple): glass, lamps and lenses keep their bake after the weathering pass."""
    from PIL import Image
    k = target.width // source.width
    for item in faces:
        if item["material"] not in materials_kept:
            continue
        x0, y0, x1, y1 = item["rect"]
        patch = source.crop((x0 - 1, y0 - 1, x1 + 1, y1 + 1))
        if k != 1:
            patch = patch.resize((patch.width * k, patch.height * k), Image.NEAREST)
        target.paste(patch, ((x0 - 1) * k, (y0 - 1) * k))
    return target


def glow_of(faces, size, materials_lit, colours, scale=1):
    """The emissive layer's texture: only the faces of the lit materials, in their colours."""
    from PIL import Image
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    for item in faces:
        if item["material"] in materials_lit:
            x0, y0, x1, y1 = item["rect"]
            img.paste(colours[item["material"]], ((x0 - 1) * scale, (y0 - 1) * scale, (x1 + 1) * scale, (y1 + 1) * scale))
    return img


def rebake_imported(geo, texture, texels=2, pad=2, boost=None):
    """An imported model (per-face UVs, faces sharing and stretching small texture patches: the A-10's 4 x 2 texel patch
    over a 20 px panel, its left and right halves on the same texels) given a texture of its own per face: each face gets
    a rectangle sized by the face itself at {texels} texels a model pixel, filled with its old paint resampled (nearest),
    and its UV moved there with the same signs, so every face looks as it did, its left and right apart and with room for
    the detail pass. {boost} ({bone: factor}) gives a bone's faces that many times the density: lettering on a small
    plate (the pickup's number plates). Returns (geometry, texture, faces): faces as bake_unique's, with bone, face and
    rect."""
    from PIL import Image
    data = copy.deepcopy(geo)
    desc = data["minecraft:geometry"][0]["description"]
    tw, th = desc["texture_width"], desc["texture_height"]
    old = texture.convert("RGBA")
    kx, ky = old.width / tw, old.height / th
    items = []
    for b in data["minecraft:geometry"][0]["bones"]:
        for cube in b.get("cubes", []):
            for face, f in (cube.get("uv") or {}).items():
                if not f or "uv" not in f:
                    continue
                ia, ib = FACE_SIZE[face]
                w, h = cube["size"][ia], cube["size"][ib]
                u, v = f["uv"]
                su, sv = f.get("uv_size", (0, 0))
                if su == 0 or sv == 0:
                    continue
                k = (boost or {}).get(b["name"], 1)
                items.append({"bone": b["name"], "face": face, "f": f, "size": (max(w, 0.05), max(h, 0.05)), "k": k,
                              "src": (min(u, u + su), min(v, v + sv), max(u, u + su), max(v, v + sv)),
                              "signs": (1 if su > 0 else -1, 1 if sv > 0 else -1),
                              "tex": (max(1, math.ceil(max(w, 0.05) * texels * k - 1e-6)), max(1, math.ceil(max(h, 0.05) * texels * k - 1e-6)))})
    area = sum((i["tex"][0] + pad) * (i["tex"][1] + pad) for i in items)
    width = 64
    while width * width < area * 1.25:
        width *= 2
    x = y = shelf = 0
    for item in sorted(items, key=lambda i: (-i["tex"][1], -i["tex"][0])):
        w_, h_ = item["tex"]
        if x + w_ + pad > width:
            x, y, shelf = 0, y + shelf, 0
        item["rect"] = (x + 1, y + 1, x + 1 + w_, y + 1 + h_)
        x += w_ + pad
        shelf = max(shelf, h_ + pad)
    height = ((y + shelf + texels * 16 - 1) // (texels * 16)) * texels * 16
    out = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    for item in items:
        x0, y0, x1, y1 = item["rect"]
        u0, v0, u1, v1 = item["src"]
        box = (int(math.floor(u0 * kx + 1e-6)), int(math.floor(v0 * ky + 1e-6)),
               max(int(math.floor(u0 * kx + 1e-6)) + 1, int(math.ceil(u1 * kx - 1e-6))),
               max(int(math.floor(v0 * ky + 1e-6)) + 1, int(math.ceil(v1 * ky - 1e-6))))
        patch = old.crop(box).resize((x1 - x0 + 2, y1 - y0 + 2), Image.NEAREST)
        out.paste(patch, (x0 - 1, y0 - 1))
        w, h = item["size"]
        sx, sy = item["signs"]
        f = item["f"]
        uw, uh = (x1 - x0) / texels, (y1 - y0) / texels
        if item["k"] == 1:
            uw, uh = w, h
        f["uv"] = [round(x0 / texels + (uw if sx < 0 else 0.0), 4), round(y0 / texels + (uh if sy < 0 else 0.0), 4)]
        f["uv_size"] = [round(sx * uw, 4), round(sy * uh, 4)]
        item["size"] = (w, h)
    desc["texture_width"], desc["texture_height"] = width / texels, height / texels
    for item in items:
        item.pop("f")
    return data, out, items
