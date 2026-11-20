"""Author the 105 mm towed howitzer's GeckoLib assets, rig manifest, hitboxes and review images.

Run from any directory: python design/howitzer/generate.py
Requires Pillow. Writes the near/LOD geometry, texture atlas, paint mask, rig manifest, the vehicle data's hitboxes
(packed and deployed), a Blockbench project, review renders and the assembling table's icon.

An M101A1-class 105 mm light towed howitzer (see BRIEF.md), modelled 1:1 (a block is a metre): split trails that
open and close, spades that fold down into the ground, a shield and top carriage that traverse on the bottom
carriage, a cradle that elevates about the trunnions and a tube that recoils along it. Source units are 16 per
block, the gun faces -Z and +X is the gunner's left. Helpers follow design/support/generate.py.
"""
from __future__ import annotations

import base64
import copy
import json
import math
from pathlib import Path
import random
import uuid

from PIL import Image, ImageDraw
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ASSETS = ROOT / "src/main/resources/assets/jeg"
VEHICLE_JSON = ROOT / "src/main/resources/data/jeg/vehicles/howitzer.json"
ID = "howitzer"

MATERIALS = {
    "olive": (86, 92, 58, 255),
    "olive_light": (104, 110, 70, 255),
    "olive_dark": (66, 71, 46, 255),
    "steel": (74, 76, 74, 255),
    "bore": (20, 20, 20, 255),
    "rubber": (34, 34, 33, 255),
    "tread": (40, 40, 38, 255),
    "glass": (110, 150, 168, 150),
    "label": (70, 78, 50, 255),
    "rust": (104, 70, 44, 255),
    "mud": (84, 68, 50, 255),
    "brass": (150, 124, 60, 255),
}
PAINT = ("olive", "olive_light", "olive_dark", "label")
TILE = 32  # the old tile atlas, laid out before the bake gives every face its own texels
ATLAS = [256, 64]
# The fleet's model and texture review (2026-11-19): every face its own texels (refit_lib.bake_unique) and the detail
# pass over them (seams, worn edges, bolt heads, grime, mud on the wheels and spades), as the refits have.
TEXELS = 4
DETAIL = dict(upscale=1, mud_height=9.0, mud=0.55, wear=1.0, streaks=0.8)

BONES: list[dict] = []
BY_NAME: dict[str, dict] = {}

# ---- Dimensions (model pixels). The M101A1: 5.94 m long in travel, 2.21 m wide, 1.73 m high; tyres 9.00-20.
WHEEL_RADIUS = 8.0              # a 1.0 m tyre
WHEEL_X = 15.0                  # tyre centre
TYRE_WIDTH = 4.0
AXLE = (0.0, WHEEL_RADIUS, 0.0)
TURRET_PIVOT = (0.0, 12.0, 1.0)  # the top carriage traverses about this on the bottom carriage
TRUNNION = (0.0, 18.0, 2.0)      # the cradle elevates about this
TUBE_AXIS_Y = 18.5
MUZZLE_Z = -34.0
BREECH_REAR_Z = 10.0
SHIELD_Z = -9.0
TRAIL_HINGE_X = 5.0
TRAIL_HINGE = (TRAIL_HINGE_X, 9.0, 6.0)
TRAIL_LENGTH = 55.0             # hinge to end, so the gun is about 5.9 m long in travel
TRAIL_SLOPE = 6.2               # degrees down from the hinge to the end
TRAIL_HALF = 2.0                # half the trail's width
TRAIL_CLOSED = 3.0              # degrees each trail swings in to meet the other for towing
TRAIL_OPEN = 26.0               # degrees each trail swings out to fire
SPADE_FOLDED = 84.0             # degrees the spade folds up onto the trail for travel
ELEVATION = (-5.0, 66.0)
TRAVERSE = 23.0
RECOIL = (16.0, 6.0)            # tube travel along the cradle at 0 and at full elevation (variable recoil)
SEAT_GUNNER = [10.0, 0.0, 9.0]  # crouched behind the shield on the left, at the sight
SIGHT_EYE = [9.0, 22.0, 3.0]
STAGES = {"trails": (0.0, 0.6), "spades": (0.6, 1.0)}


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "jeg/howitzer/" + name))


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def bone(name, parent="root", pivot=(0, 0, 0), rotation=(0, 0, 0)):
    entry = {"name": name, "pivot": [float(v) for v in pivot], "rotation": [float(v) for v in rotation], "cubes": []}
    if parent:
        entry["parent"] = parent
    BONES.append(entry)
    BY_NAME[name] = entry
    return name


def cube(parent, name, origin, size, material="olive", rotation=None, pivot=None):
    entry = {"origin": [round(float(v), 4) for v in origin], "size": [round(float(v), 4) for v in size],
             "_material": material, "_name": name}
    if rotation and any(rotation):
        entry["rotation"] = [round(float(v), 4) for v in rotation]
        entry["pivot"] = [round(float(v), 4) for v in (pivot or [origin[i] + size[i] / 2 for i in range(3)])]
    index = list(MATERIALS).index(material)
    u, v = (index % 8) * TILE + 1, (index // 8) * TILE + 1
    x, y, z = size
    entry["uv"] = {face: {"uv": [u, v], "uv_size": [max(.5, min(30, a)), max(.5, min(30, b))]}
                   for face, a, b in [("north", x, y), ("south", x, y), ("east", z, y),
                                      ("west", z, y), ("up", x, z), ("down", x, z)]}
    if material == "label":
        for face in entry["uv"].values():
            face["uv_size"] = [30, 14]
    BY_NAME[parent]["cubes"].append(entry)


def box(parent, name, a, b, material="olive", rotation=None, pivot=None):
    origin = [min(a[i], b[i]) for i in range(3)]
    size = [abs(b[i] - a[i]) for i in range(3)]
    cube(parent, name, origin, size, material, rotation, pivot)


def cylinder_x(parent, name, center, length, radius, material, layers=8):
    """Round silhouette along X built from cuboid slices (a wheel seen from the side)."""
    x, y, z = center
    step = 2 * radius / layers
    for i in range(layers):
        offset = -radius + (i + .5) * step
        half = math.sqrt(max(.001, radius * radius - offset * offset))
        cube(parent, name, [x - length / 2, y - radius + i * step, z - half], [length, step + .02, 2 * half], material)


def cylinder_y(parent, name, x, z, y0, y1, radius, material, layers=4):
    step = 2 * radius / layers
    for i in range(layers):
        offset = -radius + (i + .5) * step
        half = math.sqrt(max(.001, radius * radius - offset * offset))
        cube(parent, name, [x - radius + i * step, y0, z - half], [step + .02, y1 - y0, 2 * half], material)


def cylinder_z(parent, name, x, y, z0, z1, radius, material, layers=5):
    """Horizontal round bar along Z (the tube, the recuperator)."""
    step = 2 * radius / layers
    for i in range(layers):
        offset = -radius + (i + .5) * step
        half = math.sqrt(max(.001, radius * radius - offset * offset))
        cube(parent, name, [x - half, y - radius + i * step, z0], [2 * half, step + .02, z1 - z0], material)


def create_model():
    bone("root", None)

    # ---- Bottom carriage: axle, saddle and the trail hinges. It stays put; everything else hangs off it.
    bone("carriage", "root")
    cylinder_x("carriage", "axle", AXLE, 2 * (WHEEL_X - TYRE_WIDTH / 2) + .2, 1.1, "steel", layers=4)
    box("carriage", "saddle", [-6.5, 7.0, -5.0], [6.5, 12.0, 7.0], "olive")
    box("carriage", "saddle top", [-5.5, 11.99, -4.0], [5.5, 12.0, 6.0], "olive_dark")
    for side in (1, -1):
        x0, x1 = sorted((side * 3.4, side * 7.0))
        box("carriage", "trail hinge lug", [x0, 7.5, 4.0], [x1, 11.0, 8.6], "steel")
        box("carriage", "spring housing", [side * 11.5 - 1.2, 6.6, -1.5], [side * 11.5 + 1.2, 9.4, 1.5], "olive_dark")

    # ---- Wheels, rolling about the axle. Five lug nuts on each hub show the turn.
    for side, label in ((1, "left"), (-1, "right")):
        name = bone("wheel_" + label, "carriage", AXLE)
        cx = side * WHEEL_X
        cylinder_x(name, "tyre", (cx, AXLE[1], 0.0), TYRE_WIDTH, WHEEL_RADIUS, "tread", layers=10)
        cylinder_x(name, "rim", (cx + side * .3, AXLE[1], 0.0), TYRE_WIDTH + .3, 4.4, "olive", layers=6)
        cylinder_x(name, "hub", (cx + side * .9, AXLE[1], 0.0), TYRE_WIDTH + .6, 1.8, "steel", layers=4)
        face = cx + side * (TYRE_WIDTH / 2 + 1.2)
        for k in range(5):
            a = 2 * math.pi * k / 5
            y, z = AXLE[1] + 2.9 * math.sin(a), 2.9 * math.cos(a)
            box(name, "lug nut", [face - .4 if side > 0 else face - .2, y - .45, z - .45],
                [face + .2 if side > 0 else face + .4, y + .45, z + .45], "steel")

    # ---- Top carriage and shield: traverse together on the saddle.
    bone("turret", "carriage", TURRET_PIVOT)
    for side in (1, -1):
        x0, x1 = sorted((side * 4.6, side * 6.2))
        box("turret", "cheek", [x0, 12.0, -5.0], [x1, 20.5, 6.5], "olive")
        cylinder_y("turret", "equilibrator", side * 7.3, 2.5, 12.0, 19.0, 1.0, "steel")
        box("turret", "equilibrator cap", [side * 7.3 - 1.2, 19.0, 1.3], [side * 7.3 + 1.2, 19.8, 3.7], "olive_dark")
    box("turret", "transom", [-4.6, 12.0, -3.0], [4.6, 13.6, 5.0], "olive_dark")
    # The shield: two wings and a skirt round an open slot the tube rises through.
    slot, slot_bottom, top, bottom = 3.6, 11.5, 27.0, 9.0
    for side in (1, -1):
        x0, x1 = sorted((side * slot, side * 17.0))
        box("turret", "shield wing", [x0, bottom, SHIELD_Z - .6], [x1, top, SHIELD_Z], "olive")
        box("turret", "shield brace", [side * 9.0 - .5, 12.0, SHIELD_Z], [side * 9.0 + .5, 13.0, -4.0], "olive_dark")
        box("turret", "shield rim", [x0, top - .6, SHIELD_Z - .9], [x1, top, SHIELD_Z - .6], "olive_dark")
    box("turret", "shield skirt", [-slot, bottom, SHIELD_Z - .6], [slot, slot_bottom, SHIELD_Z], "olive")
    box("turret", "stencil", [7.0, 21.0, SHIELD_Z - .65], [15.0, 24.5, SHIELD_Z - .6], "label")
    box("turret", "sight port", [7.6, 18.6, SHIELD_Z - .65], [9.8, 20.4, SHIELD_Z - .6], "bore")
    # The handwheels: traverse on the gunner's (left) side, elevation on the right.
    bone("handwheel_traverse", "turret", (6.9, 14.5, 5.0))
    box("handwheel_traverse", "spoke", [6.6, 12.4, 4.7], [7.2, 16.6, 5.3], "steel")
    box("handwheel_traverse", "spoke", [6.6, 14.2, 2.9], [7.2, 14.8, 7.1], "steel")
    cylinder_x("handwheel_traverse", "rim", (6.9, 14.5, 5.0), .6, 2.3, "olive_dark", layers=4)
    bone("handwheel_elevation", "turret", (-6.9, 16.0, 4.0))
    box("handwheel_elevation", "spoke", [-7.2, 13.9, 3.7], [-6.6, 18.1, 4.3], "steel")
    box("handwheel_elevation", "spoke", [-7.2, 15.7, 1.9], [-6.6, 16.3, 6.1], "steel")
    cylinder_x("handwheel_elevation", "rim", (-6.9, 16.0, 4.0), .6, 2.3, "olive_dark", layers=4)
    # The panoramic telescope on its mount, eyepiece to the rear, at the gunner's eye.
    bone("sight", "turret", (8.0, 18.0, 1.0))
    box("sight", "sight mount", [6.2, 17.0, -.5], [8.2, 19.0, 2.0], "olive_dark")
    box("sight", "sight body", [8.0, 19.0, -1.0], [10.0, 22.6, 1.4], "steel")
    box("sight", "sight head", [7.9, 22.6, -1.6], [10.1, 24.2, 1.2], "olive_dark")
    box("sight", "sight lens", [8.4, 22.9, -1.7], [9.6, 23.9, -1.6], "glass")
    box("sight", "eyepiece", [8.5, 21.0, 1.4], [9.5, 22.0, 2.6], "rubber")

    # ---- Cradle: elevates about the trunnions; carries the recuperator and the tube's sleeve.
    bone("barrel", "turret", TRUNNION)
    cylinder_x("barrel", "trunnion", TRUNNION, 9.6, .9, "steel", layers=4)
    cylinder_z("barrel", "sleeve", 0.0, TUBE_AXIS_Y, -15.0, 4.5, 2.6, "olive", layers=6)
    cylinder_z("barrel", "recuperator", 0.0, 15.2, -13.0, 6.0, 1.5, "olive_dark", layers=4)
    box("barrel", "cradle rail", [-1.6, 13.4, -13.5], [1.6, 14.2, 6.5], "steel")
    box("barrel", "sleeve band", [-2.8, 15.8, -14.6], [2.8, 21.2, -13.4], "olive_dark")

    # ---- Tube and breech: recoil along the cradle (variable recoil, see RECOIL).
    bone("tube", "barrel", (0.0, TUBE_AXIS_Y, 0.0))
    cylinder_z("tube", "tube", 0.0, TUBE_AXIS_Y, MUZZLE_Z + 1.8, -15.0, 1.6, "steel", layers=5)
    cylinder_z("tube", "muzzle swell", 0.0, TUBE_AXIS_Y, MUZZLE_Z, MUZZLE_Z + 1.8, 1.85, "steel", layers=5)
    box("tube", "bore", [-.9, TUBE_AXIS_Y - .9, MUZZLE_Z - .05], [.9, TUBE_AXIS_Y + .9, MUZZLE_Z], "bore")
    box("tube", "breech ring", [-3.0, TUBE_AXIS_Y - 3.0, 4.5], [3.0, TUBE_AXIS_Y + 3.0, BREECH_REAR_Z], "steel")
    box("tube", "breech block", [-1.6, TUBE_AXIS_Y - 1.6, BREECH_REAR_Z], [1.6, TUBE_AXIS_Y + 1.6, BREECH_REAR_Z + .6], "olive_dark")
    box("tube", "breech lever", [3.0, TUBE_AXIS_Y - .4, 7.0], [3.6, TUBE_AXIS_Y + .4, 9.6], "steel")
    box("tube", "breech handle", [3.0, TUBE_AXIS_Y + .4, 8.8], [3.6, TUBE_AXIS_Y + 2.4, 9.6], "brass")

    # ---- Trails: box beams from the hinges, sloping to the ground; each swings in to tow and out to fire.
    for side, label in ((1, "left"), (-1, "right")):
        hx = side * TRAIL_HINGE_X
        name = bone("trail_" + label, "carriage", (hx, TRAIL_HINGE[1], TRAIL_HINGE[2]), (TRAIL_SLOPE, 0, 0))
        y0, y1 = TRAIL_HINGE[1] - 2.0, TRAIL_HINGE[1] + 2.0
        z0, z1 = TRAIL_HINGE[2], TRAIL_HINGE[2] + TRAIL_LENGTH
        box(name, "trail beam", [hx - TRAIL_HALF, y0, z0 + 2.0], [hx + TRAIL_HALF, y1, z1], "olive")
        box(name, "trail cap", [hx - TRAIL_HALF + .4, y1, z0 + 2.0], [hx + TRAIL_HALF - .4, y1 + .3, z1 - 1.0], "olive_dark")
        box(name, "trail hinge", [hx - 1.2, y0 + .5, z0 - .6], [hx + 1.2, y1 - .5, z0 + 2.0], "steel")
        box(name, "handspike bracket", [hx + side * TRAIL_HALF - (.6 if side > 0 else 0), y0 + 1.0, z1 - 12.0],
            [hx + side * TRAIL_HALF + (0 if side > 0 else .6), y1 - 1.0, z1 - 9.0], "steel")
        box(name, "trail end", [hx - TRAIL_HALF - .3, y0 - .3, z1 - 3.0], [hx + TRAIL_HALF + .3, y1 + .3, z1], "olive_dark")
        # The spade hangs down from the trail end deployed, and folds up along the trail's top for travel.
        spade = bone("spade_" + label, name, (hx, y1, z1))
        box(spade, "spade plate", [hx - 3.5, y1 - 6.0, z1], [hx + 3.5, y1, z1 + .7], "steel")
        box(spade, "spade edge", [hx - 3.5, y1 - 6.8, z1 + .05], [hx + 3.5, y1 - 6.0, z1 + .65], "rust")
        box(spade, "spade rib", [hx - .5, y1 - 5.5, z1 + .7], [hx + .5, y1 - .5, z1 + 1.6], "steel")
        if side > 0:
            # The lunette (tow eye) on the left trail's end: with the trails closed it sits on the centre line.
            lx = hx - TRAIL_LENGTH * math.sin(math.radians(TRAIL_CLOSED))
            box(name, "lunette arm", [lx - .6, TRAIL_HINGE[1] - .6, z1], [lx + .6, TRAIL_HINGE[1] + .6, z1 + 2.5], "steel")
            box(name, "lunette ring", [lx - 1.8, TRAIL_HINGE[1] - .5, z1 + 2.5], [lx + 1.8, TRAIL_HINGE[1] + .5, z1 + 3.1], "steel")
            box(name, "lunette ring", [lx - 1.8, TRAIL_HINGE[1] - .5, z1 + 5.9], [lx + 1.8, TRAIL_HINGE[1] + .5, z1 + 6.5], "steel")
            for sx in (-1, 1):
                box(name, "lunette ring", [lx + sx * 1.8 - (.6 if sx > 0 else 0), TRAIL_HINGE[1] - .5, z1 + 3.1],
                    [lx + sx * 1.8 + (0 if sx > 0 else .6), TRAIL_HINGE[1] + .5, z1 + 5.9], "steel")


def paint_tile(name, rgba, rng):
    tile = Image.new("RGBA", (TILE, TILE))
    px = tile.load()
    for y in range(TILE):
        for x in range(TILE):
            n = rng.choice((-3, -2, -1, 0, 0, 0, 1, 2, 3))
            c = [max(0, min(255, v + n)) for v in rgba[:3]]
            px[x, y] = (*c, rgba[3])

    def shade(x, y, amount):
        c = px[x % TILE, y % TILE]
        px[x % TILE, y % TILE] = (*(max(0, min(255, v + amount)) for v in c[:3]), c[3])

    if name in PAINT:
        # Field-worn matte paint: scuffs, chips to bare metal and dust settled low.
        for _ in range(30):
            shade(rng.randrange(TILE), rng.randrange(TILE), rng.choice((-18, -12, 10)))
        for _ in range(5):
            x, y = rng.randrange(TILE), rng.randrange(TILE)
            px[x, y] = (88, 86, 80, 255)
        for y in range(TILE - 6, TILE):
            for x in range(TILE):
                if rng.random() < .25:
                    shade(x, y, 8)
    if name in ("steel", "rust"):
        for _ in range(48):
            shade(rng.randrange(TILE), rng.randrange(TILE), rng.choice((-10, 6, 12)))
    if name == "tread":
        for y in range(TILE):
            for x in range(TILE):
                if (x // 3 + y // 4) % 2 == 0:
                    shade(x, y, 14)
    if name == "mud":
        for y in range(TILE):
            for x in range(TILE):
                shade(x, y, rng.choice((-18, -8, 0, 10)))
    if name == "glass":
        for y in range(TILE):
            for x in range(TILE):
                if 6 <= x + y // 2 <= 8:
                    px[x, y] = (215, 230, 235, 110)
    if name == "brass":
        for x in range(TILE):
            for y in range(TILE):
                shade(x, y, int(16 * math.cos(x * math.pi / 8)))
    if name == "label":
        draw = ImageDraw.Draw(tile)
        draw.rectangle((0, 0, 29, 13), fill=(70, 78, 50, 255))
        draw.text((6, 1), "105", fill=(222, 222, 204, 255))
    return tile


# ---- Pose: the bone transforms for a state, shared by the review renders, the hitboxes and the validation.
def stage(progress, name):
    a, b = STAGES[name]
    t = min(1.0, max(0.0, (progress - a) / (b - a)))
    return t * t * (3 - 2 * t)


def trail_angle(progress):
    """Each trail's swing out from straight (degrees, the left trail's right-handed Y sense): closed to tow, open to fire."""
    return -TRAIL_CLOSED + (TRAIL_OPEN + TRAIL_CLOSED) * stage(progress, "trails")


def recoil_length(elevation):
    t = (elevation - ELEVATION[0]) / (ELEVATION[1] - ELEVATION[0])
    return RECOIL[0] + (RECOIL[1] - RECOIL[0]) * max(0.0, min(1.0, t))


def pose(progress, elevation=0.0, yaw=0.0, recoil=0.0):
    """Rotation (deg) and translation (px) per bone for a deploy progress 0..1 (mirrors HowitzerRig)."""
    swing = trail_angle(progress)
    fold = SPADE_FOLDED * (1.0 - stage(progress, "spades"))
    return {
        "turret": {"r": [0, yaw, 0]},
        "barrel": {"r": [elevation, 0, 0]},
        "tube": {"t": [0, 0, recoil]},
        "trail_left": {"r": [0, swing, 0]},
        "trail_right": {"r": [0, -swing, 0]},
        "spade_left": {"r": [fold, 0, 0]},
        "spade_right": {"r": [fold, 0, 0]},
    }


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def mat_translate(p):
    return [[1, 0, 0, p[0]], [0, 1, 0, p[1]], [0, 0, 1, p[2]], [0, 0, 0, 1]]


def mat_rotate(r):
    x, y, z = (math.radians(v) for v in r)
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = [[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]
    ry = [[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]]
    rz = [[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    return mat_mul(mat_mul(rz, ry), rx)


def about(pivot, rotation):
    return mat_mul(mat_mul(mat_translate(pivot), mat_rotate(rotation)), mat_translate([-v for v in pivot]))


def apply(m, p):
    return [m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3)]


def bone_matrices(state):
    cache = {}

    def get(name):
        if name in cache:
            return cache[name]
        b = BY_NAME[name]
        extra = state.get(name, {})
        rotation = [b["rotation"][i] + extra.get("r", [0, 0, 0])[i] for i in range(3)]
        m = mat_mul(mat_translate(extra.get("t", [0, 0, 0])), about(b["pivot"], rotation))
        cache[name] = mat_mul(get(b["parent"]), m) if b.get("parent") else m
        return cache[name]
    for b in BONES:
        get(b["name"])
    return cache


def cube_corners(c):
    x, y, z = c["origin"]
    sx, sy, sz = c["size"]
    pts = [[x + dx * sx, y + dy * sy, z + dz * sz] for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
    if "rotation" in c:
        m = about(c["pivot"], c["rotation"])
        pts = [apply(m, p) for p in pts]
    return pts


def posed_cubes(state, bones=None):
    matrices = bone_matrices(state)
    for b in BONES:
        if bones and b["name"] not in bones:
            continue
        for c in b["cubes"]:
            yield b["name"], c, [apply(matrices[b["name"]], p) for p in cube_corners(c)]


def posed_point(state, bone_name, point):
    return apply(bone_matrices(state)[bone_name], point)


def extent_points(points):
    return [min(p[i] for p in points) for i in range(3)], [max(p[i] for p in points) for i in range(3)]


def subtree(name):
    names = [name]
    changed = True
    while changed:
        changed = False
        for b in BONES:
            if b.get("parent") in names and b["name"] not in names:
                names.append(b["name"])
                changed = True
    return names


def extent_of(state, bones, names=None, where=None):
    points = []
    for _, c, pts in posed_cubes(state, set(bones)):
        if names and not any(c["_name"].startswith(n) for n in names):
            continue
        if where and not where(c, pts):
            continue
        points.extend(pts)
    return extent_points(points)


# ---- Hitboxes, in source pixels at rest (turret yaw 0, gun level): boxes on the turret or the cradle follow them.
TRAIL_SEGMENTS = 3


def trail_segments(state, label):
    """A swung-out trail is diagonal: one box per third of its length, so no box takes in much empty ground."""
    hinge_z = TRAIL_HINGE[2]
    bones = subtree("trail_" + label)
    parts = []
    for k in range(TRAIL_SEGMENTS):
        z0 = hinge_z + TRAIL_LENGTH * k / TRAIL_SEGMENTS - 1e-3
        z1 = hinge_z + TRAIL_LENGTH * (k + 1) / TRAIL_SEGMENTS - 1e-3
        last = k == TRAIL_SEGMENTS - 1

        def mine(c, pts, z0=z0, z1=z1, last=last):
            if c["_name"].startswith("trail beam"):
                return z0 <= c["origin"][2] < z1
            return last and c["_name"].startswith(("trail end", "spade plate", "lunette"))
        parts.append(("body", f"{label} trail, part {k + 1}", extent_of(state, bones, where=mine), None))
    return parts


def split_beams():
    """Cut each trail beam into thirds, so the hitbox segments can be taken per third."""
    for label in ("left", "right"):
        b = BY_NAME["trail_" + label]
        out = []
        for c in b["cubes"]:
            if c["_name"] != "trail beam":
                out.append(c)
                continue
            z0, length = c["origin"][2], c["size"][2]
            hinge_z = TRAIL_HINGE[2]
            cuts = [hinge_z + TRAIL_LENGTH * k / TRAIL_SEGMENTS for k in range(1, TRAIL_SEGMENTS)]
            edges = [z0] + [z for z in cuts if z0 < z < z0 + length] + [z0 + length]
            for a, e in zip(edges, edges[1:]):
                piece = copy.deepcopy(c)
                piece["origin"][2] = round(a, 4)
                piece["size"][2] = round(e - a, 4)
                out.append(piece)
        b["cubes"] = out


def hitbox_parts(progress):
    state = pose(progress)
    parts = []
    for label in ("left", "right"):
        parts.append(("wheel_" + label, label + " wheel", extent_of(state, subtree("wheel_" + label)), None))
    parts.append(("body", "bottom carriage and axle", extent_of(state, ["carriage", "finish_carriage"]), None))
    parts.append(("body", "shield", extent_of(state, ["turret", "finish_turret"], ["shield"]), "turret"))
    parts.append(("body", "top carriage", extent_of(state, ["turret", "finish_turret", "handwheel_traverse", "finish_handwheel_traverse", "handwheel_elevation", "finish_handwheel_elevation", "sight", "finish_sight"],
                                                    ["cheek", "equilibrator", "transom", "spoke", "rim", "sight"]), "turret"))
    parts.append(("turret", "cradle and tube", extent_of(state, subtree("barrel")), "barrel"))
    if progress >= 1.0:
        for label in ("left", "right"):
            parts += trail_segments(state, label)
    else:
        parts.append(("body", "trails, closed", extent_of(state, subtree("trail_left") + subtree("trail_right")), None))
    # Spades dig below the ground; boxes stand on it.
    return [(part, label, ([lo[0], max(0.0, lo[1]), lo[2]], hi), follows) for part, label, (lo, hi), follows in parts]


def obb_rows(parts):
    rows = []
    for part, label, (lo, hi), follows in parts:
        cx, cy, cz = [(lo[i] + hi[i]) / 32 for i in range(3)]
        hw, hh, hd = [(hi[i] - lo[i]) / 32 for i in range(3)]
        tail = f', "follows": "{follows}"' if follows else ""
        rows.append('    {"part": "%s", "x": %.4f, "y": %.4f, "z": %.4f, "half_width": %.4f, "half_height": %.4f, "half_depth": %.4f%s}'
                    % (part, cx, cy, -cz, hw, hh, hd, tail))
    return rows


def write_vehicle_obb(packed, deployed):
    """Replace the obb lists in the vehicle data with the fitted boxes (data space: blocks, +Z forward)."""
    text = VEHICLE_JSON.read_text(encoding="utf-8")
    for key, parts in (("obb", packed), ("obb_deployed", deployed)):
        marker = f'  "{key}": ['
        start = text.index(marker)
        end = text.index("  ],", start) + len("  ],")
        text = text[:start] + marker + "\n" + ",\n".join(obb_rows(parts)) + "\n  ]," + text[end:]
    VEHICLE_JSON.write_text(text, encoding="utf-8")


def paint_numbers(base, faces):
    """The "105" stencilled on the shield's label plate, both faces, in the cream the Army stencils in."""
    draw = ImageDraw.Draw(base)
    for item in faces:
        if item["material"] != "label" or item["face"] not in ("north", "south"):
            continue
        x0, y0, x1, y1 = item["rect"]
        if x1 - x0 < 12 or y1 - y0 < 6:
            continue
        from PIL import ImageFont
        size = max(8, int((y1 - y0) * 0.62))
        try:
            font = ImageFont.truetype(str(ROOT / "design/hud/Inter-SemiBold.ttf"), size)
        except OSError:
            font = ImageFont.load_default(size=size)
        draw.text(((x0 + x1) / 2, (y0 + y1) / 2), "105", font=font, fill=(222, 220, 200, 255), anchor="mm")


def bake():
    """Every face its own texels, the stencil, then the weathering pass; glass kept as baked."""
    sys.path.insert(0, str(ROOT / "design/superbwarfare-port"))
    sys.path.insert(0, str(ROOT / "design/superbwarfare-port/triage"))
    sys.path.insert(0, str(ROOT / "design/ground-vehicles"))
    import refit_lib as L
    uv, faces, base = L.bake_unique(BONES, MATERIALS, TEXELS)
    ATLAS[:] = [int(round(uv[0])), int(round(uv[1]))]
    paint_numbers(base, faces)
    return L, faces, base


def make_texture():
    atlas = Image.new("RGBA", ATLAS, (0, 0, 0, 0))
    rng = random.Random(1050)
    for i, (name, rgba) in enumerate(MATERIALS.items()):
        atlas.paste(paint_tile(name, rgba, rng), (i % 8 * TILE, i // 8 * TILE))
    return atlas


def separate_finishes():
    """Paint takes only the olive panels: a mixed bone hands its steel, rubber and glass to a finish_ child bone."""
    for b in list(BONES):
        paint = [c for c in b["cubes"] if c["_material"] in PAINT]
        other = [c for c in b["cubes"] if c["_material"] not in PAINT]
        if paint and other:
            fixed = bone("finish_" + b["name"], b["name"], b["pivot"])
            BY_NAME[fixed]["cubes"] = other
            b["cubes"] = paint


def resolve_zfighting(bias=.04):
    """Push the smaller of each coplanar overlapping pair of different finishes out by a hair (see the FSV-6's)."""
    groups = {}
    for b in BONES:
        groups.setdefault(b["name"].removeprefix("finish_"), []).extend(b["cubes"])
    nudged = 0
    for cubes in groups.values():
        flat = [c for c in cubes if "rotation" not in c]
        for i in range(len(flat)):
            for j in range(i + 1, len(flat)):
                a, b = flat[i], flat[j]
                if a["_material"] == b["_material"]:
                    continue
                for axis in range(3):
                    others = [k for k in range(3) if k != axis]
                    overlap = all(min(a["origin"][k] + a["size"][k], b["origin"][k] + b["size"][k]) - max(a["origin"][k], b["origin"][k]) > .05
                                  for k in others)
                    if not overlap:
                        continue
                    area = lambda c: c["size"][others[0]] * c["size"][others[1]]
                    small, big = (a, b) if area(a) <= area(b) else (b, a)
                    for small_max in (False, True):
                        plane = small["origin"][axis] + (small["size"][axis] if small_max else 0)
                        for big_max in (False, True):
                            other = big["origin"][axis] + (big["size"][axis] if big_max else 0)
                            if abs(plane - other) < 1e-3 and small_max == big_max:
                                if small_max:
                                    small["size"][axis] = round(small["size"][axis] + bias, 4)
                                else:
                                    small["origin"][axis] = round(small["origin"][axis] - bias, 4)
                                    small["size"][axis] = round(small["size"][axis] + bias, 4)
                                nudged += 1
    return nudged


def unpainted_parts():
    children = {}
    for b in BONES:
        children.setdefault(b.get("parent"), []).append(b["name"])

    def painted(name):
        return any(c["_material"] in PAINT for c in BY_NAME[name]["cubes"]) or any(painted(c) for c in children.get(name, []))

    result = []

    def walk(name):
        if not painted(name):
            result.append(name)
            return
        for child in children.get(name, []):
            walk(child)
    walk("root")
    return result


def make_lod(geometry, level):
    result = copy.deepcopy(geometry)
    for b in result["minecraft:geometry"][0]["bones"]:
        if b["name"].startswith(("wheel_", "finish_wheel_")):
            b["cubes"] = [c for c in b["cubes"] if c["size"][0] >= 2][: (10 if level == 1 else 4)]
        else:
            limit = .7 if level == 1 else 2.0
            b["cubes"] = [c for c in b["cubes"] if max(c["size"]) >= limit * 2 and min(c["size"]) >= limit * .4]
    return result


def native_geometry(geometry):
    # Authoring uses ordinary right-handed rotations; Bedrock's convention and GeckoLib's X reflection need this.
    result = copy.deepcopy(geometry)
    for b in result["minecraft:geometry"][0]["bones"]:
        for part in [b, *b.get("cubes", [])]:
            if "rotation" in part:
                x, y, z = part["rotation"]
                part["rotation"] = [-x, y, -z]
    return result


def make_animations():
    """The rest clip ShellGunEntity plays, and a preview of deploying for Blockbench. In game every articulation is
    driven from the gun's synced state."""
    clips = {f"animation.{ID}.rest": {"loop": True, "bones": {}}}
    channels = {}
    for i in range(41):
        t = i / 40 * 5
        for name, transform in pose(min(1.0, i / 32)).items():
            entry = channels.setdefault(name, {})
            if "r" in transform:
                entry.setdefault("rotation", {})[str(t)] = transform["r"]
    clips[f"animation.{ID}.deploy_preview"] = {"loop": False, "animation_length": 5, "bones": channels}
    return {"format_version": "1.8.0", "animations": clips}


def make_bbmodel(atlas_url, animations):
    elements, groups = [], []
    children = {b["name"]: [] for b in BONES}
    for b in BONES:
        groups.append({"name": b["name"], "uuid": uid(b["name"]), "origin": b["pivot"], "rotation": b["rotation"], "export": True})
        for i, c in enumerate(b["cubes"]):
            identity = uid(b["name"] + "/" + str(i))
            elements.append({"name": c["_name"], "uuid": identity, "type": "cube", "box_uv": False,
                             "from": c["origin"], "to": [c["origin"][k] + c["size"][k] for k in range(3)],
                             "origin": c.get("pivot", b["pivot"]), "rotation": c.get("rotation", [0, 0, 0]),
                             "faces": {f: {"uv": [*v["uv"], v["uv"][0] + v["uv_size"][0], v["uv"][1] + v["uv_size"][1]], "texture": 0}
                                       for f, v in c["uv"].items()}})
            children[b["name"]].append(identity)

    def outliner(name):
        return {"name": name, "uuid": uid(name), "isOpen": False,
                "children": children[name] + [outliner(b["name"]) for b in BONES if b.get("parent") == name]}
    clips = []
    for name, animation in animations["animations"].items():
        animators = {}
        for b, channels in animation["bones"].items():
            frames = []
            for channel, values in channels.items():
                for time, vector in values.items():
                    frames.append({"channel": channel, "time": float(time), "interpolation": "linear",
                                   "data_points": [dict(zip(("x", "y", "z"), map(str, vector)))]})
            animators[uid(b)] = {"name": b, "type": "bone", "keyframes": frames}
        clips.append({"uuid": uid(name), "name": name, "loop": "once", "length": animation.get("animation_length", 0), "animators": animators})
    return {"meta": {"format_version": "5.0", "model_format": "bedrock", "box_uv": False},
            "name": ID, "model_identifier": "jeg." + ID, "visible_box": [8, 4, 1.5],
            "resolution": {"width": ATLAS[0], "height": ATLAS[1]}, "elements": elements, "groups": groups,
            "outliner": [outliner("root")], "animations": clips,
            "textures": [{"uuid": uid("texture"), "name": ID + ".png", "id": "0", "mode": "bitmap", "internal": True,
                          "source": atlas_url, "width": ATLAS[0], "height": ATLAS[1], "uv_width": ATLAS[0], "uv_height": ATLAS[1]}]}


def lowest_of(state, bones, ignore=()):
    return min(p[1] for _, c, pts in posed_cubes(state, set(bones)) if not c["_name"].startswith(ignore) for p in pts)


def validate(animations, project):
    names = {b["name"] for b in BONES}
    assert len(names) == len(BONES), "Duplicate bone names"
    for b in BONES:
        seen = {b["name"]}
        p = b.get("parent")
        while p:
            assert p in names and p not in seen, "Broken bone hierarchy"
            seen.add(p)
            p = BY_NAME[p].get("parent")
        for c in b["cubes"]:
            assert all(math.isfinite(n) for n in c["origin"] + c["size"])
            assert all(n > 0 for n in c["size"]), (b["name"], c["_name"], c["size"])
            for face in c["uv"].values():
                assert all(0 <= face["uv"][k] < face["uv"][k] + face["uv_size"][k] <= ATLAS[k] for k in range(2))
    # The tyres stand on the ground.
    for label in ("left", "right"):
        assert abs(lowest_of(pose(0.0), subtree("wheel_" + label))) < .05, "Tyres must stand on the ground"
    # Packed, nothing but the tyres touches the ground: the trail ends and folded spades ride clear.
    clear = lowest_of(pose(0.0), [n for n in names if not n.startswith(("wheel_", "finish_wheel_"))])
    assert clear > .3, ("Packed, something drags on the ground", clear)
    # Deployed, the spades dig in: their edges a little below the ground.
    for label in ("left", "right"):
        dig = lowest_of(pose(1.0), subtree("spade_" + label))
        assert -3.0 < dig < -.2, ("Spades must bite into the ground", label, dig)
    # Closed for towing the two trail beams meet without overlapping.
    left = extent_of(pose(0.0), subtree("trail_left"), ["trail end"])
    right = extent_of(pose(0.0), subtree("trail_right"), ["trail end"])
    assert right[1][0] <= left[0][0] + .35, ("Closed trails overlap", right[1][0], left[0][0])
    # At every elevation, the breech, run fully back, clears the ground and the opened trails.
    for elevation in range(int(ELEVATION[0]), int(ELEVATION[1]) + 1, 5):
        state = pose(1.0, elevation, 0.0, recoil_length(elevation))
        low = lowest_of(state, subtree("tube"))
        assert low > 1.0, ("Breech strikes the ground", elevation, low)
        tube = extent_of(state, subtree("tube"))
        for label in ("left", "right"):
            for _, c, pts in posed_cubes(state, set(subtree("trail_" + label))):
                lo, hi = extent_points(pts)
                hits = all(min(tube[1][i], hi[i]) - max(tube[0][i], lo[i]) > .2 for i in range(3))
                if hits and c["_name"].startswith("trail beam"):
                    # Coarse box test; confirm with the breech ring's own corners against the beam.
                    ring = [p for _, rc, rpts in posed_cubes(state, set(subtree("tube"))) if rc["_name"] == "breech ring" for p in rpts]
                    rlo, rhi = extent_points(ring)
                    assert not all(min(rhi[i], hi[i]) - max(rlo[i], lo[i]) > .2 for i in range(3)), \
                        ("Breech strikes a trail", elevation, label)
    # At full depression the tube clears the shield's skirt.
    state = pose(1.0, ELEVATION[0])
    crossing = []
    for _, c, pts in posed_cubes(state, set(subtree("barrel"))):
        lo, hi = extent_points(pts)
        if lo[2] <= SHIELD_Z and hi[2] >= SHIELD_Z - .6 and lo[0] < 3.6 and hi[0] > -3.6:
            crossing.append(lo[1])
    muzzle_low = min(crossing)
    assert muzzle_low > 11.5, ("Tube hits the shield skirt at full depression", muzzle_low)
    assert all(set(a["bones"]).issubset(names) for a in animations["animations"].values())
    all_ids = [e["uuid"] for e in project["elements"]] + [g["uuid"] for g in project["groups"]]
    assert len(set(all_ids)) == len(all_ids)
    count = sum(len(b["cubes"]) for b in BONES)
    print(f"Validated {len(BONES)} bones, {count} cuboids; UVs, hierarchy, ground contact, spades, trails, "
          "breech clearance over the elevation range and the shield slot passed.")
    return count


def render_views(path_prefix):
    """Flat-shaded orthographic and three-quarter review renders."""
    views = {
        "side": (math.radians(90), 0.0),
        "front": (0.0, 0.0),
        "three_quarter": (math.radians(140), math.radians(22)),
        "front_quarter": (math.radians(35), math.radians(18)),
        "top": (math.radians(90), math.radians(89)),
    }
    poses = {"packed": pose(0.0), "deployed": pose(1.0, 20.0),
             "deployed-66-recoil": pose(1.0, 66.0, 0.0, recoil_length(66.0)),
             "deployed-0-recoil": pose(1.0, 0.0, 15.0, recoil_length(0.0))}
    for label, state in poses.items():
        for view, (yaw, pitch) in views.items():
            if label.endswith("recoil") and view not in ("side", "three_quarter"):
                continue
            image = Image.new("RGBA", (900, 520), (24, 30, 34, 255))
            draw = ImageDraw.Draw(image)
            cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)

            def project(p):
                x = p[0] * cy - p[2] * sy
                z = p[0] * sy + p[2] * cy
                y = p[1] * cp + z * sp
                depth = z * cp - p[1] * sp
                return 450 - x * 6.0 - (60 if view == "side" else 0), 380 - y * 6.0, depth
            faces = []
            for _, c, pts in posed_cubes(state):
                rgba = MATERIALS[c["_material"]]
                for idx, shade in (((0, 1, 3, 2), .78), ((4, 6, 7, 5), .78), ((0, 4, 5, 1), .62), ((2, 3, 7, 6), 1.0),
                                   ((0, 2, 6, 4), .9), ((1, 5, 7, 3), .9)):
                    quad = [project(pts[i]) for i in idx]
                    faces.append((sum(q[2] for q in quad) / 4, [(q[0], q[1]) for q in quad],
                                  tuple(int(v * shade) for v in rgba[:3]) + (255 if rgba[3] > 200 else 150,)))
            faces.sort(key=lambda f: -f[0])
            draw.line((0, 380, 900, 380), fill=(60, 70, 74, 255))
            for _, poly, colour in faces:
                draw.polygon(poly, fill=colour)
            draw.text((12, 10), f"105 mm howitzer  {label}  {view}", fill=(230, 230, 220, 255))
            image.save(f"{path_prefix}{label}-{view}.png")


def make_icon(path):
    """The assembling table's selection icon: a grey side view of the packed gun on a 256 x 64 transparent strip."""
    scale = 3.6
    width, height = 256, 64
    image = Image.new("RGBA", (width * 2, height * 2), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    faces = []
    for _, c, pts in posed_cubes(pose(0.0)):
        rgba = MATERIALS[c["_material"]]
        if rgba[3] < 200:
            continue
        grey = int(.299 * rgba[0] + .587 * rgba[1] + .114 * rgba[2])
        tone = min(235, 120 + grey // 2)
        for idx in ((0, 1, 3, 2), (4, 6, 7, 5)):
            quad = [(width + p[2] * scale, height * 2 - 8 - p[1] * scale, p[0]) for p in (pts[i] for i in idx)]
            faces.append((sum(q[2] for q in quad) / 4, [(q[0], q[1]) for q in quad], (tone, tone, tone, 255)))
    faces.sort(key=lambda f: f[0])
    for _, poly, colour in faces:
        draw.polygon(poly, fill=colour)
    crop = image.crop(image.getbbox())
    crop.thumbnail((width - 16, height - 8))
    icon = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    icon.paste(crop, ((width - crop.width) // 2, height - crop.height - 4), crop)
    path.parent.mkdir(parents=True, exist_ok=True)
    icon.save(path)


def make_round_icon(path, body, band, letters):
    """A 16 x 16 inventory icon of a 105 mm round, lying corner to corner: brass case lower left, painted projectile
    with its band, nose to the upper right, and the round's letters in the free corner (as the 5-inch shells' icons)."""
    from PIL import ImageFont  # noqa: F401  (the default bitmap font is used)
    icon = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = icon.load()
    outline = (24, 24, 22, 255)
    for i in range(16):
        for j in range(16):
            # Along the round (u, 0 at the case's base to 1 at the nose) and across it (v, in pixels).
            u = (i + (15 - j)) / 30.0
            v = (i - (15 - j)) / 2.0
            half = 1.9 if u < .78 else 1.9 * max(0.0, (1.0 - u) / .22)
            if abs(v) > half + .6 or u < .04 or u > 1.0:
                continue
            if abs(v) > half - .3:
                px[i, j] = outline
            elif u < .42:
                shade = 20 if v < 0 else -10
                px[i, j] = tuple(max(0, min(255, c + shade)) for c in (176, 146, 70)) + (255,)
            elif .56 < u < .64:
                px[i, j] = band
            else:
                shade = 16 if v < 0 else -8
                px[i, j] = tuple(max(0, min(255, c + shade)) for c in body[:3]) + (255,)
    draw = ImageDraw.Draw(icon)
    for k, ch in enumerate(letters):
        x0 = 1 + k * 4
        draw.rectangle((x0 - 1, 0, x0 + 3, 6), fill=(24, 24, 22, 255))
    glyphs = {"H": ["1.1", "1.1", "111", "1.1", "1.1"], "E": ["111", "1..", "11.", "1..", "111"],
              "S": ["111", "1..", "111", "..1", "111"], "M": ["1.1", "111", "111", "1.1", "1.1"]}
    for k, ch in enumerate(letters):
        for r, line in enumerate(glyphs[ch]):
            for c, on in enumerate(line):
                if on == "1":
                    px[1 + k * 4 + c, 1 + r] = band if sum(band[:3]) > 300 else (226, 226, 214, 255)
    path.parent.mkdir(parents=True, exist_ok=True)
    icon.save(path)


def data_point(p):
    """Model pixels to vehicle data (blocks, +Z forward, +X left)."""
    return [round(p[0] / 16, 4), round(p[1] / 16, 4), round(-p[2] / 16, 4)]


def main():
    create_model()
    split_beams()
    separate_finishes()
    print(f"Biased {resolve_zfighting()} coplanar faces against z-fighting.")
    L, faces, base = bake()
    texture_path = ASSETS / f"textures/entity/vehicle/{ID}.png"
    geometry = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.jeg.{ID}", "texture_width": ATLAS[0], "texture_height": ATLAS[1],
                        "visible_bounds_width": 9, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1, 0]},
        "bones": [{k: [{ck: cv for ck, cv in c.items() if not ck.startswith("_")} for c in v] if k == "cubes" else v
                   for k, v in b.items()} for b in BONES]}]}
    import detail_texture as D
    atlas, _, _ = D.detail_image(native_geometry(geometry), base, DETAIL)
    L.paste_faces(atlas, base, faces, ("glass",))
    atlas.save(texture_path)
    url = "data:image/png;base64," + base64.b64encode(texture_path.read_bytes()).decode()
    animations = make_animations()
    project = make_bbmodel(url, animations)
    count = validate(animations, project)
    geometry = native_geometry(geometry)
    write_json(ASSETS / f"geckolib/models/entity/vehicle/{ID}.geo.json", geometry)
    for level in (1, 2):
        write_json(ASSETS / f"geckolib/models/entity/vehicle/lod/{ID}_lod{level}.geo.json", make_lod(geometry, level))
    write_json(ASSETS / f"vehicle_paint/{ID}.json", {"unpainted_parts": unpainted_parts()})
    packed, deployed = hitbox_parts(0.0), hitbox_parts(1.0)
    write_vehicle_obb(packed, deployed)
    native_animations = copy.deepcopy(animations)
    for clip in native_animations["animations"].values():
        for channels in clip["bones"].values():
            for time, rotation in channels.get("rotation", {}).items():
                x, y, z = rotation
                channels["rotation"][time] = [-x, y, -z]
    write_json(ASSETS / f"geckolib/animations/entity/vehicle/{ID}.animation.json", native_animations)
    write_json(HERE / f"{ID}.bbmodel", project)
    packed_state = pose(0.0)
    lx = TRAIL_HINGE_X - TRAIL_LENGTH * math.sin(math.radians(TRAIL_CLOSED))
    tow_eye = posed_point(packed_state, "trail_left", [lx, TRAIL_HINGE[1], TRAIL_HINGE[2] + TRAIL_LENGTH + 4.5])
    bounds = {label: [list(v) for v in extent_points([p for _, _, pts in posed_cubes(pose(pr)) for p in pts])]
              for label, pr in (("packed", 0.0), ("deployed", 1.0))}
    manifest = {"model": f"jeg:entity/vehicle/{ID}", "units_per_block": 16, "front": "-Z in the model, +Z in vehicle data",
                "reference": "M101A1 105 mm light towed howitzer, 1:1",
                "turret_origin": data_point(TURRET_PIVOT), "trunnion": data_point(TRUNNION),
                "barrel_offset_from_turret": data_point([TRUNNION[i] - TURRET_PIVOT[i] for i in range(3)]),
                "muzzle_rest": data_point([0.0, TUBE_AXIS_Y, MUZZLE_Z]),
                "tow_eye": data_point(tow_eye), "seat_gunner": data_point(SEAT_GUNNER), "sight_eye": data_point(SIGHT_EYE),
                "elevation_degrees": list(ELEVATION), "traverse_degrees": TRAVERSE, "recoil_px": list(RECOIL),
                "wheel_radius": WHEEL_RADIUS / 16, "trail_closed_degrees": TRAIL_CLOSED, "trail_open_degrees": TRAIL_OPEN,
                "trail_slope_degrees": TRAIL_SLOPE, "spade_folded_degrees": SPADE_FOLDED, "stages": STAGES,
                "model_bounds": bounds,
                "hitbox": {label: [{"part": p, "covers": l, "min": lo, "max": hi, "follows": f} for p, l, (lo, hi), f in parts]
                           for label, parts in (("packed", packed), ("deployed", deployed))},
                "bones": len(BONES), "cuboids": count}
    write_json(HERE / "rig.json", manifest)
    render_views(str(HERE / "review-"))
    make_icon(ASSETS / f"textures/vehicle_icon/{ID}_icon.png")
    make_round_icon(ASSETS / "textures/item/shell_105mm_he.png", (78, 86, 52), (226, 196, 52, 255), "HE")
    make_round_icon(ASSETS / "textures/item/shell_105mm_smoke.png", (150, 156, 140), (34, 34, 30, 255), "SM")
    print(json.dumps({k: manifest[k] for k in ("turret_origin", "trunnion", "barrel_offset_from_turret", "muzzle_rest", "tow_eye")}))


if __name__ == "__main__":
    main()
