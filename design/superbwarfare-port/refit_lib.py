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
    for _ in range(8):
        pairs = coplanar_overlaps(geo)
        if not pairs:
            return geo
        by = {b["name"]: b for b in bones_of(geo)}
        for bone, i, j, axis in pairs:
            cubes = by[bone]["cubes"]
            a, b = cubes[i], cubes[j]
            vol = lambda c: c["size"][0] * c["size"][1] * c["size"][2]
            small, big = (a, b) if vol(a) <= vol(b) else (b, a)
            s_lo, s_hi = small["origin"][axis], small["origin"][axis] + small["size"][axis]
            planes = (big["origin"][axis], big["origin"][axis] + big["size"][axis])
            if any(abs(s_lo - p) < 1e-4 for p in planes):
                small["origin"][axis] = round(small["origin"][axis] - push, 5)
                small["size"][axis] = round(small["size"][axis] + push, 5)
            if any(abs(s_hi - p) < 1e-4 for p in planes):
                small["size"][axis] = round(small["size"][axis] + push, 5)
    raise SystemExit(f"z-fighting not resolved: {coplanar_overlaps(geo)}")


# ---------------------------------------------------------------- checks

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
