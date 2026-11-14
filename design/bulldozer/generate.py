"""Author the BD-40's editable Blockbench project and GeckoLib assets.

Run from any directory: python design/bulldozer/generate.py
Requires Pillow. Writes the near/LOD geometry, animation library, texture atlas, glow map,
paint mask, rig manifest, Blockbench project and offline preview.

Revision 03 is a mid-size crawler dozer modelled on real machines: long engine hood behind a
cast radiator guard, open ROPS canopy (the operator stays visible and the view is clear all
round), semi-U blade on C-frame push arms outside the tracks with lift and tilt hydraulics,
idler / bottom rollers / carrier rollers / toothed sprocket running gear and a three-shank
ripper. Source units are 16 per block, the machine faces -Z and +X is the operator's left.
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

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
ASSETS = ROOT / "src/main/resources/assets/jeg"

# Materials are 32 px tiles; faces sample the tile's top-left corner, so detail is spread evenly.
MATERIALS = {
    "yellow": (214, 160, 36, 255), "yellow_light": (228, 178, 60, 255),
    "yellow_dark": (170, 122, 30, 255), "frame": (38, 40, 41, 255),
    "track": (44, 43, 41, 255), "steel": (104, 110, 110, 255),
    "blade": (146, 147, 140, 255), "chrome": (196, 202, 202, 255),
    "lamp": (240, 236, 214, 255), "amber": (255, 150, 20, 255),
    "tail": (196, 38, 26, 255), "grille": (28, 31, 33, 255),
    "console": (22, 36, 34, 255), "seat": (30, 31, 32, 255),
    "label": (214, 160, 36, 255), "edge": (122, 121, 112, 255),
    "seal": (18, 20, 21, 255), "hazard": (214, 160, 36, 255),
    "mesh": (52, 55, 56, 255), "rubber": (22, 22, 22, 255),
    "rust": (98, 66, 44, 255), "gauge": (16, 18, 18, 255),
    "plate": (74, 76, 76, 255), "soil": (102, 78, 52, 255),
    "mirror": (156, 183, 194, 255),
}
PAINT = ("yellow", "yellow_light", "yellow_dark")
TILE = 32
ATLAS = (256, 128)
BONES: list[dict] = []
BY_NAME: dict[str, dict] = {}

# Running gear. The tread path is a stadium of two straights and two arcs around the idler and sprocket.
TRACK_X = 17.0
TRACK_RADIUS = 6.5
TRACK_STRAIGHT = 46.0
TRACK_LENGTH = 2 * TRACK_STRAIGHT + 2 * math.pi * TRACK_RADIUS
LINKS = 30
# name, z, y, visible radius, rolling radius (the tread pitch line for the end wheels)
WHEELS = [("idler", -23.0, 8.0, 5.0, TRACK_RADIUS)]
WHEELS += [("roller", z, 5.3, 2.3, 2.3) for z in (-15.0, -7.5, 0.0, 7.5, 15.0)]
WHEELS += [("carrier", z, 11.6, 1.45, 1.45) for z in (-9.0, 9.0)]
WHEELS += [("sprocket", 23.0, 8.0, 4.6, TRACK_RADIUS)]

# Blade linkage. Push arms pivot on the track-frame trunnions; the blade (blade_level) keeps its pitch.
LIFT_PIVOT = [0.0, 8.0, -2.0]
BLADE_PIVOT = [0.0, 9.0, -39.0]
CUT_EDGE = [0.0, 0.0, -46.6]
LIFT_MIN, LIFT_MAX = -4.0, 30.0
# Lift cylinders: barrel trunnion on the radiator-guard bracket, rod clevis on the blade back.
LIFT_BASE = [12.5, 25.0, -19.0]
LIFT_END = [12.5, 16.0, -39.5]
LIFT_BARREL = 15.0
# Tilt struts: barrel on the push arm, rod on the blade's upper corner.
TILT_BASE = [21.2, 10.0, -24.0]
TILT_END = [21.2, 21.0, -39.8]
TILT_BARREL = 13.0

# Ripper: links pivot on the mount and carry the beam; lowering swings them down by up to RIP_MAX degrees.
RIP_UPPER_PIVOT = [0.0, 14.6, 31.6]
RIP_LOWER_PIVOT = [0.0, 6.6, 31.6]
RIP_LINK = 9.0
RIP_MAX = 45.0
RIP_BASE = [4.0, 17.0, 29.0]
RIP_END = [4.0, 13.0, 40.0]
RIP_BARREL = 7.0

SEAT_POINT = [0.0, 16.0, 7.5]  # rider position: the player's hips sit 12 px above it, on the cushion
EYE = [0.0, SEAT_POINT[1] + 25.92, SEAT_POINT[2]]
MIRROR_CENTER = [-17.2, 39.5, .6]
MIRROR_REAR_TARGET = [-22.0, 38.0, 64.0]  # rearward, outside the canopy/tank silhouette
EXHAUST_TIP = [8.4, 42.6, -17.0]


def mirror_rotation():
    # A rear-view mirror's normal bisects its directions to the eye and the rear scene.
    # Pointing it straight at the eye reflects the operator, and can hide the glass behind the ROPS post.
    def direction(target):
        delta = [target[i] - MIRROR_CENTER[i] for i in range(3)]
        length = math.hypot(*delta)
        return [v / length for v in delta]
    normal = [a + b for a, b in zip(direction(EYE), direction(MIRROR_REAR_TARGET))]
    return [-math.degrees(math.atan2(normal[1], math.hypot(normal[0], normal[2]))),
            math.degrees(math.atan2(normal[0], normal[2])), 0]


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "jeg/bulldozer/" + name))


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def bone(name, parent="chassis", pivot=(0, 0, 0), rotation=(0, 0, 0)):
    entry = {"name": name, "pivot": [float(v) for v in pivot], "rotation": [float(v) for v in rotation], "cubes": []}
    if parent:
        entry["parent"] = parent
    BONES.append(entry)
    BY_NAME[name] = entry
    return name


def cube(parent, name, origin, size, material="yellow", rotation=None, pivot=None):
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
    if material in ("label", "console", "gauge", "hazard", "mirror"):
        for face in entry["uv"].values():
            face["uv_size"] = [30, 14 if material == "label" else 30]
    BY_NAME[parent]["cubes"].append(entry)


def box(parent, name, a, b, material="yellow", rotation=None, pivot=None):
    origin = [min(a[i], b[i]) for i in range(3)]
    size = [abs(b[i] - a[i]) for i in range(3)]
    cube(parent, name, origin, size, material, rotation, pivot)


def side_x(side, x0, x1):
    """An x range authored for the left (+X) side, mirrored to the right for side -1."""
    return (x0, x1) if side > 0 else (-x1, -x0)


def sbox(parent, name, side, x0, x1, y0, y1, z0, z1, material="yellow", rotation=None, pivot=None):
    a, b = side_x(side, x0, x1)
    if pivot is not None:
        pivot = [pivot[0] * side, pivot[1], pivot[2]]
    if rotation is not None and side < 0:
        rotation = [rotation[0], -rotation[1], -rotation[2]]
    box(parent, name, [a, y0, z0], [b, y1, z1], material, rotation, pivot)


def cylinder_x(parent, name, center, length, radius, material, layers=8):
    """Round silhouette along X built from narrow, engine-compatible cuboid slices."""
    x, y, z = center
    step = 2 * radius / layers
    for i in range(layers):
        offset = -radius + (i + .5) * step
        half = math.sqrt(max(.001, radius * radius - offset * offset))
        cube(parent, name, [x - length / 2, y - radius + i * step, z - half], [length, step + .02, 2 * half], material)


def cylinder_y(parent, name, x, z, y0, y1, radius, material, layers=5):
    """Vertical round bar from Z-depth slices; inside a rotated bone it becomes any hydraulic barrel."""
    step = 2 * radius / layers
    for i in range(layers):
        offset = -radius + (i + .5) * step
        half = math.sqrt(max(.001, radius * radius - offset * offset))
        cube(parent, name, [x - radius + i * step, y0, z - half], [step + .02, y1 - y0, 2 * half], material)


def track_point(distance):
    s = distance % TRACK_LENGTH
    arc = math.pi * TRACK_RADIUS
    if s < 46:
        return [8 - TRACK_RADIUS, -23 + s, 0.0]
    if s < 46 + arc:
        angle = (s - 46) / TRACK_RADIUS
        return [8 - TRACK_RADIUS * math.cos(angle), 23 + TRACK_RADIUS * math.sin(angle), -math.degrees(angle)]
    if s < 92 + arc:
        return [8 + TRACK_RADIUS, 23 - (s - 46 - arc), -180.0]
    angle = math.pi + (s - 92 - arc) / TRACK_RADIUS
    return [8 - TRACK_RADIUS * math.cos(angle), -23 + TRACK_RADIUS * math.sin(angle), -math.degrees(angle)]


def rotate_x(point, pivot, angle):
    a = math.radians(angle)
    y, z = point[1] - pivot[1], point[2] - pivot[2]
    return [point[0], pivot[1] + y * math.cos(a) - z * math.sin(a), pivot[2] + y * math.sin(a) + z * math.cos(a)]


def blade_point(point, lift):
    """A point fixed to the blade: carried by the arms, while the blade itself keeps its pitch."""
    pivot = rotate_x(BLADE_PIVOT, LIFT_PIVOT, lift)
    return [point[i] - BLADE_PIVOT[i] + pivot[i] for i in range(3)]


def lift_pose(lift):
    end = blade_point(LIFT_END, lift)
    dy, dz = end[1] - LIFT_BASE[1], end[2] - LIFT_BASE[2]
    return math.degrees(math.atan2(dz, dy)), math.hypot(dy, dz), end


def tilt_pose(lift):
    """In the push arm's frame the blade corner turns back by the lift angle around the blade pivot."""
    end = rotate_x(TILT_END, BLADE_PIVOT, -lift)
    dy, dz = end[1] - TILT_BASE[1], end[2] - TILT_BASE[2]
    return math.degrees(math.atan2(dz, dy)), math.hypot(dy, dz), end


def ripper_offset(angle):
    """How far the beam moves when the links swing down by this angle (y, z in source px)."""
    a = math.radians(angle)
    return [0.0, -RIP_LINK * math.sin(a), -(RIP_LINK - RIP_LINK * math.cos(a))]


def ripper_pose(angle):
    o = ripper_offset(angle)
    end = [RIP_END[0], RIP_END[1] + o[1], RIP_END[2] + o[2]]
    dy, dz = end[1] - RIP_BASE[1], end[2] - RIP_BASE[2]
    return math.degrees(math.atan2(dz, dy)), math.hypot(dy, dz), end


def hydraulic(prefix, side, parent, base, pose, barrel_length, barrel_radius, rod_radius, barrel_material="yellow"):
    angle, length, _ = pose(0)
    x = base[0] * side
    barrel = bone(f"{prefix}_barrel_{'left' if side > 0 else 'right'}", parent, [x, base[1], base[2]], [angle, 0, 0])
    cylinder_y(barrel, "hydraulic barrel", x, base[2], base[1] - 1.2, base[1] + barrel_length, barrel_radius, barrel_material, 5)
    cylinder_y(barrel, "barrel trunnion", x, base[2], base[1] - 1.6, base[1] + 1.2, barrel_radius + .45, "steel", 4)
    cylinder_y(barrel, "gland", x, base[2], base[1] + barrel_length - 1.2, base[1] + barrel_length, barrel_radius + .3, "frame", 4)
    box(barrel, "hose port", [x - .5, base[1] + 2, base[2] + barrel_radius - .2], [x + .5, base[1] + 3.2, base[2] + barrel_radius + .7], "rubber")
    rod = bone(f"{prefix}_rod_{'left' if side > 0 else 'right'}", barrel, [x, base[1] + barrel_length, base[2]])
    cylinder_y(rod, "polished piston rod", x, base[2], base[1] + barrel_length, base[1] + length, rod_radius, "chrome", 4)
    cylinder_y(rod, "rod eye", x, base[2], base[1] + length - 1.1, base[1] + length + .6, rod_radius + .5, "steel", 4)


def create_model():
    bone("root", None)
    bone("undercarriage", "root")
    bone("chassis", "root", [0, 8, 0])

    # ---- Undercarriage: track frames, running gear and articulated shoes.
    for side in (1, -1):
        name = "left" if side > 0 else "right"
        x = TRACK_X * side
        track = bone("track_" + name, "undercarriage", [x, 8, 0])
        sbox(track, "track roller frame", side, 13.6, 20.4, 6.6, 10.2, -19, 19, "frame")
        sbox(track, "frame top plate", side, 13.2, 20.8, 10.2, 10.8, -20, 20, "frame")
        sbox(track, "recoil spring guard", side, 20.4, 21.1, 6.9, 10.6, -19, -5, "yellow")
        sbox(track, "rear track guard", side, 20.4, 21.1, 6.9, 10.6, 5, 19, "yellow")
        sbox(track, "rock guard", side, 20.5, 21.0, 3.4, 6.9, -17, 17, "frame")
        sbox(track, "idler yoke", side, 14.6, 19.4, 5.6, 10.4, -21.5, -17, "frame")
        sbox(track, "sprocket hub housing", side, 12.6, 15.6, 4.2, 11.8, 18.5, 27.5, "frame")
        sbox(track, "push-arm trunnion", side, 21.0, 22.4, 5.8, 10.2, -4.2, .2, "steel")
        sbox(track, "access step", side, 21.0, 24.4, 10.6, 11.4, 6, 12, "plate")
        sbox(track, "step hanger", side, 21.0, 21.6, 8.0, 10.6, 6.5, 11.5, "frame")
        sbox(track, "packed soil", side, 14.0, 20.0, 10.8, 11.2, -16, -9, "soil")
        sbox(track, "packed soil", side, 14.5, 19.0, 10.8, 11.1, 4, 12, "soil")
        for i, (kind, z, y, radius, _) in enumerate(WHEELS):
            wheel = bone(f"wheel_{name}_{i}", track, [x, y, z])
            if kind in ("idler", "sprocket"):
                cylinder_x(wheel, kind + " rim", [x, y, z], 6.0, radius, "track", 10)
                cylinder_x(wheel, kind + " web", [x + side * 3.05, y, z], .5, radius * .72, "steel", 8)
                cylinder_x(wheel, kind + " hub", [x + side * 3.4, y, z], .5, 1.5, "frame", 6)
                for j in range(5 if kind == "idler" else 8):
                    a = j * math.tau / (5 if kind == "idler" else 8)
                    r = 2.4 if kind == "idler" else 2.2
                    box(wheel, "lightening hole" if kind == "idler" else "sprocket bolt",
                        [x + side * 3.32 - .2, y + math.sin(a) * r - .55, z + math.cos(a) * r - .55],
                        [x + side * 3.32 + .2, y + math.sin(a) * r + .55, z + math.cos(a) * r + .55],
                        "seal" if kind == "idler" else "chrome")
                if kind == "sprocket":
                    for j in range(11):
                        a = j * math.tau / 11
                        box(wheel, "sprocket tooth", [x - 2.2, y + math.sin(a) * 5.35 - .8, z + math.cos(a) * 5.35 - .8],
                            [x + 2.2, y + math.sin(a) * 5.35 + .8, z + math.cos(a) * 5.35 + .8], "edge", [math.degrees(-a), 0, 0],
                            [x, y + math.sin(a) * 5.35, z + math.cos(a) * 5.35])
            else:
                width = 6.4 if kind == "roller" else 3.6
                cylinder_x(wheel, kind + " tread", [x, y, z], width, radius, "track", 6 if kind == "roller" else 4)
                cylinder_x(wheel, kind + " flange", [x + side * (width / 2 - .3), y, z], .6, radius + .35, "steel", 6 if kind == "roller" else 4)
                box(wheel, "roller end bolt", [x + side * (width / 2) - .2, y + radius * .45 - .35, z - .35],
                    [x + side * (width / 2) + .2, y + radius * .45 + .35, z + .35], "chrome")
        for i in range(LINKS):
            y, z, angle = track_point(i * TRACK_LENGTH / LINKS)
            link = bone(f"shoe_{name}_{i:02}", track, [x, y, z], [angle, 0, 0])
            cube(link, "track shoe", [x - 4.5, y - 1.5, z - 1.95], [9, 2.4, 3.9], "track")
            cube(link, "grouser bar", [x - 4.6, y - 1.9, z - .6], [9.2, .9, 1.2], "edge")
            cube(link, "link rail", [x - 2.3, y + .9, z - 1.9], [4.6, .9, 3.8], "steel")
            cube(link, "link pin", [x - 2.7, y + .6, z + 1.25], [5.4, .7, .7], "seal")

    # ---- Hull frame, fenders and platform.
    box("chassis", "main frame", [-12, 4, -30], [12, 10, 30], "frame")
    box("chassis", "belly guard", [-10, 3.2, -28], [10, 4, 10], "steel")
    box("chassis", "frame rail", [-12.4, 9.6, -31], [12.4, 10.6, 30.5], "frame")
    for side in (1, -1):
        sbox("chassis", "fender", side, 11.5, 22.6, 16.6, 17.6, -24, 30)
        sbox("chassis", "fender nose", side, 11.5, 22.6, 16.6, 17.6, -30, -24, "yellow", [-24, 0, 0], [0, 17.6, -24])
        sbox("chassis", "fender edge", side, 22.0, 22.8, 15.4, 17.8, -24, 30, "yellow_dark")
        sbox("chassis", "fender tread plate", side, 12.2, 21.9, 17.6, 17.75, -4, 29, "plate")
        sbox("chassis", "fender support", side, 11.0, 12.6, 10.6, 16.6, -22, 28, "frame")
        sbox("chassis", "lift bracket", side, 10.4, 14.4, 22.4, 27.6, -21.6, -16.4, "yellow_dark")
        sbox("chassis", "lift trunnion pin", side, 14.4, 15.2, 24.2, 25.8, -19.8, -18.2, "chrome")
        sbox("chassis", "lift line", side, 10.5, 11.3, 18.0, 23.0, -20.0, -18.6, "rubber")

    bone("engine", "chassis", [0, 18, -18])
    box("engine", "engine hood", [-10.5, 10, -31], [10.5, 23.4, -4])
    box("engine", "hood crown", [-9.4, 23.4, -31], [9.4, 24.6, -4], "yellow_light")
    for side in (1, -1):
        sbox("engine", "hood shoulder", side, 8.8, 11.2, 22.4, 23.6, -31, -4, "yellow", [0, 0, -38], [10.0, 23.0, -17.5])
        for k in range(4):
            sbox("engine", "side louvre", side, 10.5, 10.85, 13.4 + k * 2.2, 14.6 + k * 2.2, -28, -11, "grille")
        sbox("engine", "door seam", side, 10.5, 10.62, 11, 22.6, -11.2, -10.9, "seal")
        sbox("engine", "door seam", side, 10.5, 10.62, 11, 22.6, -19.7, -19.4, "seal")
        sbox("engine", "door latch", side, 10.5, 10.9, 20.4, 21.2, -12.6, -11.6, "chrome")
        sbox("engine", "model badge", side, 10.5, 10.7, 20.6, 22.6, -27.5, -21.5, "label")
        sbox("engine", "hood grab rail", side, 7.2, 7.9, 25.4, 26.0, -27, -8, "steel")
        for z in (-26.5, -8.5):
            sbox("engine", "rail post", side, 7.25, 7.85, 24.6, 25.6, z - .3, z + .3, "steel")
    # Cast radiator guard with grille, guard bars, recessed lamps and lift-cylinder lugs.
    box("engine", "radiator guard", [-11.6, 9, -34.4], [11.6, 24.4, -31])
    box("engine", "guard top cap", [-11.8, 24.4, -34.6], [11.8, 25.2, -30.6], "yellow_dark")
    box("engine", "radiator core", [-9, 11.4, -34.7], [9, 23.0, -34.4], "grille")
    for y in (12.4, 15.4, 18.4, 21.2):
        box("engine", "guard bar", [-9.4, y, -35.4], [9.4, y + 1.1, -34.4], "frame")
    for x in (-3.6, 3.6):
        box("engine", "guard upright", [x - .5, 11, -35.5], [x + .5, 23.6, -34.4], "frame")
    box("engine", "tow eye", [-1.6, 6.6, -35.5], [1.6, 9.6, -32], "steel")
    box("engine", "horn bracket", [-11.4, 25.2, -33.2], [-10.4, 26.4, -32.2], "frame")
    for i, x in enumerate((-11.0, -9.6)):
        cylinder_x("engine", "horn trumpet", [x, 26.6, -33.4], 1.2, .55 + .1 * i, "frame", 4)
        cylinder_x("engine", "horn bell", [x, 26.6, -34.3], .5, .95 + .1 * i, "steel", 4)
    box("engine", "front counter plate", [-11, 6, -33], [11, 9, -30], "yellow_dark")
    for side in (1, -1):
        sbox("engine", "lamp housing", side, 5.6, 9.6, 25.2, 27.6, -35.2, -33.6, "frame")
        sbox("engine", "front work lamp", side, 6.0, 9.2, 25.6, 27.2, -35.35, -35.2, "lamp")
    # Air precleaner and exhaust with a rain flap.
    cylinder_y("engine", "precleaner stem", -7.6, -18, 24.6, 28.4, 1.3, "frame", 4)
    cylinder_y("engine", "precleaner bowl", -7.6, -18, 28.4, 31.4, 2.2, "frame", 6)
    cylinder_y("engine", "precleaner band", -7.6, -18, 29.6, 30.1, 2.35, "seal", 6)
    cylinder_y("engine", "precleaner cap", -7.6, -18, 31.4, 32.2, 1.4, "steel", 4)
    box("engine", "muffler", [6.3, 24.6, -21], [10.5, 27.4, -13], "frame")
    bone("exhaust", "engine", [8.4, 27.4, -17])
    cylinder_y("exhaust", "exhaust stack", 8.4, -17, 27.4, 42.4, 1.2, "rust", 5)
    cylinder_y("exhaust", "stack clamp", 8.4, -17, 31.0, 31.7, 1.45, "steel", 5)
    bone("exhaust_flap", "exhaust", [8.4, 42.4, -15.6])
    box("exhaust_flap", "rain flap", [7.0, 42.4, -18.6], [9.8, 42.75, -15.6], "frame")
    box("exhaust_flap", "flap hinge", [7.3, 42.2, -15.9], [9.5, 42.9, -15.4], "steel")

    # ---- Open operator station under a ROPS canopy.
    bone("cab", "chassis", [0, 18, 6])
    box("cab", "platform deck", [-11.5, 17.6, -4.4], [11.5, 19, 16.6], "plate")
    box("cab", "dash pod", [-9, 19, -4.6], [9, 23.4, -1.6], "frame")
    box("cab", "dash panel", [-8, 23.4, -4.4], [8, 24.6, -1.0], "frame", [-24, 0, 0], [0, 23.4, -1.0])
    box("cab", "instrument cluster", [-5.4, 23.5, -3.9], [5.4, 24.75, -1.7], "console", [-24, 0, 0], [0, 23.4, -1.0])
    for x in (-6.8, 6.8):
        box("cab", "gauge", [x - 1, 23.6, -4.0], [x + 1, 24.8, -2.4], "gauge", [-24, 0, 0], [0, 23.4, -1.0])
    box("cab", "decelerator pedal", [-2.4, 19, -1.2], [2.4, 19.7, 2.4], "rubber", [16, 0, 0], [0, 19, 2.4])
    box("cab", "seat pedestal", [-3.6, 19, 4.4], [3.6, 22.0, 11.4], "frame")
    box("cab", "suspension bellows", [-3.0, 22.0, 5.0], [3.0, 23.0, 10.8], "rubber")
    box("cab", "seat cushion", [-4.6, 23.0, 2.6], [4.6, 26.0, 11.0], "seat")
    box("cab", "cushion piping", [-4.7, 25.6, 2.5], [4.7, 26.1, 3.0], "rubber")
    box("cab", "seat back", [-4.6, 26.0, 9.6], [4.6, 36.6, 12.0], "seat", [8, 0, 0], [0, 26, 10.8])
    box("cab", "seat back shell", [-4.2, 26.6, 12.0], [4.2, 36.0, 12.8], "frame", [8, 0, 0], [0, 26, 10.8])
    box("cab", "headrest", [-3.2, 37.0, 9.8], [3.2, 39.6, 12.0], "seat", [8, 0, 0], [0, 26, 10.8])
    for side in (1, -1):
        sbox("cab", "armrest console", side, 5.4, 9.2, 19.0, 26.2, 3.6, 12.4, "frame")
        sbox("cab", "armrest pad", side, 5.7, 8.9, 26.2, 27.4, 6.0, 12.2, "seat")
        sbox("cab", "control plate", side, 5.6, 9.0, 26.2, 26.6, 3.4, 6.0, "steel")
        # ROPS: thick rear posts, slim front posts, canopy and grab handles.
        sbox("cab", "rear ROPS post", side, 9.6, 12.2, 17.6, 46.0, 13.9, 16.6)
        sbox("cab", "ROPS gusset", side, 9.6, 12.2, 17.6, 21.0, 11.4, 13.9, "yellow_dark")
        sbox("cab", "front ROPS post", side, 10.6, 12.1, 19.0, 46.0, -3.9, -2.4)
        sbox("cab", "grab handle", side, 12.2, 12.9, 24.0, 35.0, 14.6, 15.2, "steel")
        sbox("cab", "grab handle stand-off", side, 12.2, 12.9, 24.0, 24.7, 14.6, 16.0, "steel")
        sbox("cab", "grab handle stand-off", side, 12.2, 12.9, 34.3, 35.0, 14.6, 16.0, "steel")
        sbox("cab", "roof work lamp housing", side, 7.0, 10.6, 44.2, 45.6, -6.2, -4.4, "frame")
        sbox("cab", "roof work lamp", side, 7.3, 10.3, 44.4, 45.4, -6.35, -6.2, "lamp")
        sbox("cab", "rear work lamp housing", side, 7.0, 10.6, 44.2, 45.6, 18.4, 20.2, "frame")
        sbox("cab", "rear work lamp", side, 7.3, 10.3, 44.4, 45.4, 20.2, 20.35, "lamp")
    box("cab", "rear cross member", [-9.6, 29, 14.2], [9.6, 31, 16.4], "yellow_dark")
    box("cab", "rear guard screen", [-9.6, 31, 15.0], [9.6, 34.6, 15.4], "mesh")
    box("cab", "canopy", [-13, 46, -5.6], [13, 47.6, 18.6])
    box("cab", "canopy drip lip", [-13.3, 45.5, -5.9], [13.3, 46.0, 18.9], "yellow_dark")
    box("cab", "canopy liner", [-12.0, 45.3, -4.6], [12.0, 46.0, 17.6], "frame")
    box("cab", "canopy rib", [-12.5, 46.9, 5.2], [12.5, 47.6, 6.6], "yellow_dark")
    # Short, braced mount on the right front post. Keep the head behind/outboard of the post so the seated
    # operator sees the glass, rather than a silver edge or a stalk through the mirror face.
    box("cab", "mirror post clamp", [-12.35, 39.4, -4.15], [-10.35, 40.8, -2.15], "frame")
    box("cab", "mirror clamp bolt", [-12.55, 39.8, -3.55], [-12.35, 40.4, -2.95], "steel")
    box("cab", "mirror outrigger", [-17.55, 39.8, -3.8], [-12.1, 40.45, -3.15], "frame")
    box("cab", "mirror elbow", [-17.55, 39.8, -3.8], [-16.9, 40.45, -.1], "frame")
    mx, my, mz = MIRROR_CENTER
    bone("mirror", "cab", MIRROR_CENTER, mirror_rotation())
    box("mirror", "mirror swivel", [mx - .55, my - .55, mz - .95], [mx + .55, my + .55, mz - .45], "steel")
    # Stepped corners make a softly chamfered, tall industrial housing with a recessed rubber bezel.
    for name, half_width, y0, y1, z0, z1, material in (
            ("mirror shell", 2.1, -2.65, 2.65, -.6, .05, "frame"),
            ("mirror shell top", 1.7, 2.65, 3.0, -.6, .05, "frame"),
            ("mirror shell bottom", 1.7, -3.0, -2.65, -.6, .05, "frame"),
            ("mirror bezel", 1.9, -2.5, 2.5, .05, .12, "rubber"),
            ("mirror bezel top", 1.6, 2.5, 2.75, .05, .12, "rubber"),
            ("mirror bezel bottom", 1.6, -2.75, -2.5, .05, .12, "rubber"),
            ("mirror glass", 1.7, -2.3, 2.3, .12, .18, "mirror"),
            ("mirror glass top", 1.4, 2.3, 2.55, .12, .18, "mirror"),
            ("mirror glass bottom", 1.4, -2.55, -2.3, .12, .18, "mirror")):
        box("mirror", name, [mx - half_width, my + y0, mz + z0], [mx + half_width, my + y1, mz + z1], material)
        if material == "mirror":
            # One continuous finish across the stepped glass outline, including the narrow top/bottom pieces.
            index = list(MATERIALS).index(material)
            uv = {"uv": [index % 8 * TILE + 1 + (1.7 - half_width) / 3.4 * 30,
                         index // 8 * TILE + 1 + (2.55 - y1) / 5.1 * 30],
                  "uv_size": [2 * half_width / 3.4 * 30, (y1 - y0) / 5.1 * 30]}
            BY_NAME["mirror"]["cubes"][-1]["uv"]["south"] = uv
    # Fuel tank, toolbox, hydraulic tank and the rear hazard panel.
    box("cab", "fuel tank", [-11.5, 17.6, 16.6], [11.5, 28.6, 28.6])
    box("cab", "tank top", [-11.0, 28.6, 17.0], [11.0, 29.2, 28.2], "yellow_light")
    cylinder_y("cab", "fuel cap", -7.0, 24.0, 29.2, 30.2, 1.1, "steel", 4)
    box("cab", "rear hazard panel", [-6.5, 19.2, 28.6], [6.5, 23.0, 28.9], "hazard")
    for side in (1, -1):
        sbox("cab", "tail lamp", side, 8.0, 10.6, 24.4, 26.6, 28.6, 28.95, "tail")
        sbox("cab", "reflector", side, 8.0, 10.6, 22.4, 23.4, 28.6, 28.9, "amber")
    box("cab", "hydraulic tank", [13.0, 17.6, 19.0], [20.6, 24.2, 28.0])
    box("cab", "sight gauge", [20.6, 19.4, 24.0], [20.75, 23.0, 25.2], "chrome")
    box("cab", "toolbox", [-20.6, 17.6, 19.0], [-13.0, 21.8, 28.0], "frame")
    box("cab", "toolbox lid", [-20.8, 21.8, 18.8], [-12.8, 22.4, 28.2], "steel")
    box("cab", "battery box", [-20.6, 17.6, 4.0], [-14.0, 21.0, 12.0], "frame")
    bone("lever_travel", "cab", [7.2, 26.6, 4.7])
    box("lever_travel", "travel joystick stem", [6.8, 26.6, 4.3], [7.6, 30.4, 5.1], "steel")
    box("lever_travel", "travel joystick grip", [6.4, 30.4, 3.9], [8.0, 32.6, 5.5], "rubber")
    box("lever_travel", "direction rocker", [6.7, 32.6, 4.3], [7.7, 33.0, 5.1], "amber")
    bone("lever_blade", "cab", [-7.2, 26.6, 4.7])
    box("lever_blade", "blade joystick stem", [-7.6, 26.6, 4.3], [-6.8, 30.4, 5.1], "steel")
    box("lever_blade", "blade joystick grip", [-8.0, 30.4, 3.9], [-6.4, 32.6, 5.5], "rubber")
    box("lever_blade", "blade joystick cap", [-7.8, 32.6, 4.1], [-6.6, 33.0, 5.3], "yellow_light")
    bone("beacon", "cab", [7.8, 47.6, 14.8])
    box("beacon", "beacon base", [6.0, 47.6, 13.0], [9.6, 48.2, 16.6], "frame")
    box("beacon", "amber beacon lens", [6.4, 48.2, 13.4], [9.2, 50.4, 16.2], "amber")
    box("beacon", "beacon cap", [6.7, 50.4, 13.7], [8.9, 50.8, 15.9], "frame")

    # ---- Three-shank ripper on a parallelogram linkage: the links swing down and the beam drops the shanks
    # into the ground behind the machine while keeping them upright.
    bone("ripper", "chassis", [0, 10, 32])
    bone("ripper_upper_link", "ripper", RIP_UPPER_PIVOT)
    bone("ripper_lower_link", "ripper", RIP_LOWER_PIVOT)
    bone("ripper_beam", "ripper", [0, 10.6, RIP_UPPER_PIVOT[2] + RIP_LINK])
    for side in (1, -1):
        sbox("ripper", "ripper mount", side, 6.0, 10.0, 4.0, 16.4, 28.6, 33.0, "frame")
        sbox("ripper", "mount pin", side, 5.6, 10.4, 14.1, 15.1, 31.1, 32.1, "chrome")
        sbox("ripper", "mount pin", side, 5.6, 10.4, 6.1, 7.1, 31.1, 32.1, "chrome")
        sbox("ripper_upper_link", "upper link", side, 7.0, 9.0, 13.6, 15.6, 31.0, 41.0)
        sbox("ripper_lower_link", "lower link", side, 7.0, 9.0, 5.6, 7.6, 31.0, 41.0)
        sbox("ripper_beam", "link pin", side, 6.4, 9.6, 14.0, 15.2, 40.0, 41.2, "chrome")
        sbox("ripper_beam", "cylinder lug", side, 3.0, 5.0, 12.0, 14.0, 39.0, 41.0, "steel")
    box("ripper_beam", "ripper beam", [-14, 6.4, 39.4], [14, 15.0, 43.6])
    box("ripper_beam", "beam hazard stripe", [-13, 8.0, 43.6], [13, 13.4, 43.9], "hazard")
    for x in (-10.0, 0.0, 10.0):
        box("ripper_beam", "shank", [x - 1.3, 1.4, 40.0], [x + 1.3, 15.4, 43.0], "frame")
        box("ripper_beam", "shank pin", [x - 1.6, 12.4, 40.8], [x + 1.6, 13.4, 41.8], "chrome")
        box("ripper_beam", "shank protector", [x - 1.1, 1.6, 39.6], [x + 1.1, 7.0, 40.2], "edge")
        box("ripper_beam", "ripper tip", [x - 1.0, .6, 37.6], [x + 1.0, 2.6, 41.0], "edge", [-18, 0, 0], [x, 1.6, 40.0])

    # ---- Blade: C-frame push arms (blade_lift), the blade (blade_level/blade_tilt) and semi-U wings.
    bone("blade_lift", "undercarriage", LIFT_PIVOT)
    for side in (1, -1):
        sbox("blade_lift", "push arm", side, 22.2, 25.2, 5.6, 9.8, -40, -1.2)
        sbox("blade_lift", "push arm flange", side, 22.0, 25.4, 9.8, 10.4, -38, -3)
        sbox("blade_lift", "push arm gusset", side, 21.2, 22.2, 6.4, 11.0, -27.5, -20.5, "yellow_dark")
        cylinder_x("blade_lift", "trunnion cap", [23.7 * side, 8.0, -2.0], 3.6, 2.2, "steel", 6)
        sbox("blade_lift", "arm pin", side, 25.2, 25.8, 7.2, 8.8, -2.8, -1.2, "chrome")
    bone("blade_level", "blade_lift", BLADE_PIVOT)
    bone("blade_tilt", "blade_level", BLADE_PIVOT)
    for x0, x1, step in [(-24, 24, 0), (24, 28, 1), (28, 31.2, 2), (-28, -24, 1), (-31.2, -28, 2)]:
        forward = step * 1.5
        slices = 9
        height = 23.6 / slices
        for i in range(slices):
            yc = 2.4 + (i + .5) * height
            slope = -.044 * (yc - 13)
            zc = -43.2 - .022 * (yc - 13) ** 2 + .7 - forward
            cube("blade_tilt", "rolled moldboard", [x0, yc - height / 2 - .06, zc - .7], [x1 - x0, height + .12, 1.4], "blade",
                 [math.degrees(math.atan(slope)), 0, 0], [0, yc, zc])
        lift = 0 if step == 0 else .3
        for k in range(int((x1 - x0) // 8) if step == 0 else 1):
            a = x0 + k * 8 if step == 0 else x0
            b = a + 8 if step == 0 else x1
            box("blade_tilt", "cutting edge" if step == 0 else "end bit", [a + .08, lift, -46.6 - forward], [b - .08, 2.6, -45.0 - forward], "edge")
            if step == 0:
                box("blade_tilt", "edge bolt", [a + 3.3, 1.0, -46.78], [a + 4.7, 1.9, -46.6], "chrome")
        box("blade_tilt", "top lip", [x0, 25.6, -46.2 - forward], [x1, 27.0, -42.4 - forward], "yellow_dark")
        box("blade_tilt", "blade back", [x0, 2.4, -42.4 - forward], [x1, 24.8, -40.6 - forward], "yellow_dark")
    box("blade_tilt", "upper box beam", [-24, 22.4, -41.6], [24, 25.6, -38.8])
    box("blade_tilt", "lower box beam", [-24, 3.0, -41.0], [24, 6.2, -38.8])
    for x in (-23.4, -16, -6, 6, 16, 23.4):
        box("blade_tilt", "blade rib", [x - .8, 2.8, -40.8], [x + .8, 24.2, -38.6])
    for side in (1, -1):
        sbox("blade_tilt", "lift clevis", side, 11.0, 14.0, 14.4, 17.6, -40.2, -37.4, "steel")
        sbox("blade_tilt", "tilt bracket", side, 20.0, 22.4, 19.4, 22.6, -40.6, -38.4, "steel")
        sbox("blade_tilt", "arm ball socket", side, 22.0, 25.4, 5.4, 9.8, -41.6, -38.4, "steel")
        sbox("blade_tilt", "wing side plate", side, 30.9, 31.6, 2.6, 25.6, -47.6, -42.4, "yellow_dark")
    box("blade_tilt", "worn soil line", [-23.6, 2.6, -46.25], [23.6, 3.1, -46.0], "soil")

    for side in (1, -1):
        hydraulic("lift", side, "chassis", LIFT_BASE, lift_pose, LIFT_BARREL, 1.35, .62)
        hydraulic("tilt", side, "blade_lift", TILT_BASE, tilt_pose, TILT_BARREL, 1.05, .5)
        hydraulic("ripper", side, "ripper", RIP_BASE, ripper_pose, RIP_BARREL, .9, .45)

    BY_NAME["blade_tilt"]["locators"] = {"cut_left": [-24, 0, CUT_EDGE[2]], "cut_right": [24, 0, CUT_EDGE[2]],
                                          "blade_center": [0, 13, -43]}
    BY_NAME["cab"]["locators"] = {"driver_eye": EYE, "seat": SEAT_POINT}
    BY_NAME["exhaust"]["locators"] = {"smoke": EXHAUST_TIP}


def paint_tile(name, rgba, rng):
    tile = Image.new("RGBA", (TILE, TILE))
    px = tile.load()
    for y in range(TILE):
        for x in range(TILE):
            n = rng.choice((-3, -2, -1, 0, 0, 0, 1, 2, 3))
            c = [max(0, min(255, v + n)) for v in rgba[:3]]
            px[x, y] = (*c, rgba[3])

    def shade(x, y, amount, alpha=None):
        c = px[x % TILE, y % TILE]
        px[x % TILE, y % TILE] = (*(max(0, min(255, v + amount)) for v in c[:3]), c[3] if alpha is None else alpha)

    if name in PAINT or name == "label":
        # Weathered machine paint: chips through to primer, scuffs and settled dust.
        for _ in range(30):
            x, y = rng.randrange(TILE), rng.randrange(TILE)
            shade(x, y, rng.choice((-26, -18, 12)))
        for _ in range(6):
            x, y = rng.randrange(TILE), rng.randrange(TILE)
            for k in range(rng.randrange(2, 6)):
                shade(x + k, y + (k // 3), -14)
        for _ in range(8):
            x, y = rng.randrange(TILE), rng.randrange(TILE)
            px[x, y] = (70, 64, 58, 255)
    if name in ("frame", "steel", "track", "plate"):
        for _ in range(40):
            x, y = rng.randrange(TILE), rng.randrange(TILE)
            shade(x, y, rng.choice((-8, 6, 10)))
        for _ in range(10):
            x, y = rng.randrange(TILE), rng.randrange(TILE)
            px[x, y] = (92, 64, 40, 255) if name != "steel" else (130, 92, 60, 255)
    if name == "track":
        for y in range(TILE):
            for x in range(TILE):
                if y % 8 in (0, 1):
                    shade(x, y, 22)
                if rng.random() < .05:
                    px[x, y] = (88, 70, 48, 255)
    if name == "plate":
        for y in range(TILE):
            for x in range(TILE):
                if (x + 2 * y) % 6 == 0 and (x - y) % 4 < 2:
                    shade(x, y, 26)
    if name == "blade":
        # Polished by soil: vertical wear streaks, darker scale at the top, soil packed at the edge.
        for x in range(TILE):
            streak = rng.choice((0, 0, 6, 10, -6))
            for y in range(TILE):
                shade(x, y, streak - (10 if y < 4 else 0))
        for _ in range(60):
            x, y = rng.randrange(TILE), rng.randrange(TILE)
            px[x, y] = (110, 92, 70, 255) if rng.random() < .5 else (172, 172, 164, 255)
    if name == "edge":
        for x in range(TILE):
            for y in range(TILE):
                if y < 3:
                    shade(x, y, 28)
                if rng.random() < .08:
                    px[x, y] = (112, 90, 64, 255)
    if name == "chrome":
        for x in range(TILE):
            for y in range(TILE):
                shade(x, y, int(18 * math.cos(x * math.pi / 8)) + rng.choice((-2, 0, 2)))
    if name == "mirror":
        # Quiet blue-silver glass with a soft horizon and diagonal sheen; distinct from scratched piston chrome.
        # This is an authored finish, not a live rear-camera render.
        for y in range(TILE):
            t = y / (TILE - 1)
            top, bottom = ((143, 177, 194), (207, 218, 219)) if t < .52 else ((156, 173, 177), (123, 143, 150))
            blend = t / .52 if t < .52 else (t - .52) / .48
            for x in range(TILE):
                gleam = 12 if 8 <= x + y // 2 <= 10 else 0
                px[x, y] = (*[min(255, round(a + (b - a) * blend) + gleam) for a, b in zip(top, bottom)], 255)
    if name == "grille":
        for y in range(TILE):
            for x in range(TILE):
                if y % 3 == 0:
                    px[x, y] = (66, 70, 72, 255)
                elif x % 4 == 0:
                    px[x, y] = (48, 52, 54, 255)
    if name == "mesh":
        for y in range(TILE):
            for x in range(TILE):
                if x % 3 != 0 and y % 3 != 0:
                    px[x, y] = (0, 0, 0, 0)
    if name == "hazard":
        for y in range(TILE):
            for x in range(TILE):
                px[x, y] = (24, 24, 24, 255) if (x + y) % 10 < 5 else (226, 172, 32, 255)
    if name == "seat":
        for y in range(TILE):
            for x in range(TILE):
                if y % 7 == 0 or x % 9 == 0:
                    shade(x, y, 10)
    if name == "rust":
        for y in range(TILE):
            for x in range(TILE):
                shade(x, y, rng.choice((-14, -6, 0, 8, 14)))
                if y < 4:
                    px[x, y] = (30, 26, 24, 255)
    if name == "soil":
        for y in range(TILE):
            for x in range(TILE):
                shade(x, y, rng.choice((-18, -8, 0, 10, 16)))
    if name in ("lamp", "amber", "tail"):
        for y in range(TILE):
            for x in range(TILE):
                shade(x, y, 18 if (x // 4 + y // 4) % 2 == 0 else 0)
    draw = ImageDraw.Draw(tile)
    if name == "label":
        draw.rectangle((0, 0, 29, 13), fill=(26, 28, 29, 255))
        draw.text((2, 1), "BD-40", fill=(232, 182, 54, 255))
    if name == "console":
        draw.rectangle((1, 1, 28, 28), fill=(14, 22, 22, 255), outline=(60, 74, 72, 255))
        draw.rectangle((4, 4, 25, 15), fill=(26, 70, 54, 255))
        for j in range(3):
            draw.line((6, 7 + 3 * j, 12 + 4 * j, 7 + 3 * j), fill=(140, 222, 150, 255))
        for j, colour in enumerate(((228, 180, 40), (90, 200, 110), (220, 70, 50))):
            draw.rectangle((5 + 7 * j, 19, 9 + 7 * j, 23), fill=(*colour, 255))
    if name == "gauge":
        draw.ellipse((2, 2, 27, 27), fill=(20, 22, 22, 255), outline=(170, 174, 170, 255))
        draw.arc((6, 6, 23, 23), 150, 390, fill=(220, 220, 214, 255))
        draw.line((15, 15, 22, 9), fill=(245, 120, 40, 255), width=2)
    return tile


def subtree(name):
    names = [name]
    for b in BONES:
        if b.get("parent") in names and b["name"] not in names:
            names.append(b["name"])
    changed = True
    while changed:
        changed = False
        for b in BONES:
            if b.get("parent") in names and b["name"] not in names:
                names.append(b["name"]); changed = True
    return names


def extent(cubes):
    lo = [min(c["origin"][i] for c in cubes) for i in range(3)]
    hi = [max(c["origin"][i] + c["size"][i] for c in cubes) for i in range(3)]
    return lo, hi


def pick(bones, names=None, where=None):
    out = []
    for b in bones:
        for c in BY_NAME[b]["cubes"]:
            if names and not any(c["_name"].startswith(n) for n in names):
                continue
            if where and not where(c):
                continue
            out.append(c)
    return out


def hitbox_parts():
    """Collision and hit boxes fitted to the model at rest (blade at grade, ripper raised): one per real mass, in
    source pixels. Long or moving parts get their own boxes so no box spans empty space."""
    left = lambda c: c["origin"][0] + c["size"][0] / 2 > 0
    right = lambda c: not left(c)
    track_l = pick(subtree("track_left")) + pick(["chassis", "finish_chassis"], ["fender"], left)
    track_r = pick(subtree("track_right")) + pick(["chassis", "finish_chassis"], ["fender"], right)
    parts = [
        ("wheel_left", "left track and fender", extent(track_l)),
        ("wheel_right", "right track and fender", extent(track_r)),
        ("body", "main frame", extent(pick(["chassis", "finish_chassis"], ["main frame", "belly guard", "frame rail"]))),
        ("main_engine", "engine hood and radiator guard", extent(pick(["engine", "finish_engine"],
            ["engine hood", "hood", "radiator", "guard", "front counter", "lamp housing", "side louvre"]))),
        ("body", "operator station and canopy", extent(pick(["cab", "finish_cab"],
            ["platform", "dash", "seat", "armrest", "rear ROPS", "front ROPS", "canopy", "rear cross", "rear guard", "headrest"]))),
        ("body", "fuel and hydraulic tanks, toolbox", extent(pick(["cab", "finish_cab"], ["fuel tank", "tank top", "hydraulic tank", "toolbox"]))),
        ("body", "ripper", extent(pick([b for b in subtree("ripper") if "barrel" not in b and "rod" not in b], where=lambda c: "rotation" not in c))),
        ("body", "blade", extent(pick(["blade_tilt", "finish_blade_tilt"], where=lambda c: "rotation" not in c or c["origin"][1] > 2))),
        ("body", "left push arm", extent(pick(["blade_lift", "finish_blade_lift"], ["push arm", "trunnion", "arm pin"], left))),
        ("body", "right push arm", extent(pick(["blade_lift", "finish_blade_lift"], ["push arm", "trunnion", "arm pin"], right))),
    ]
    # Grousers dip below the ground plane; the boxes stand on it, or the machine would ride up on them.
    return [(part, label, ([lo[0], max(0.0, lo[1]), lo[2]], hi)) for part, label, (lo, hi) in parts]


def model_bounds():
    cubes = [c for b in BONES for c in b["cubes"]]
    return extent(cubes)


def write_vehicle_obb(parts):
    """Replace the obb list in the vehicle data with the fitted boxes (data space: blocks, +Z forward)."""
    path = ROOT / "src/main/resources/data/jeg/vehicles/bulldozer.json"
    text = path.read_text(encoding="utf-8")
    start = text.index('  "obb": [')
    end = text.index("  ],", start) + len("  ],")
    rows = []
    for part, label, (lo, hi) in parts:
        cx, cy, cz = [(lo[i] + hi[i]) / 32 for i in range(3)]
        hw, hh, hd = [(hi[i] - lo[i]) / 32 for i in range(3)]
        rows.append('    {"part": "%s", "x": %.4f, "y": %.4f, "z": %.4f, "half_width": %.4f, "half_height": %.4f, "half_depth": %.4f}'
                    % (part, cx, cy, -cz, hw, hh, hd))
    text = text[:start] + '  "obb": [\n' + ",\n".join(rows) + "\n  ]," + text[end:]
    path.write_text(text, encoding="utf-8")


def make_texture():
    atlas = Image.new("RGBA", ATLAS, (0, 0, 0, 0))
    rng = random.Random(4041)
    for i, (name, rgba) in enumerate(MATERIALS.items()):
        atlas.paste(paint_tile(name, rgba, rng), (i % 8 * TILE, i // 8 * TILE))
    return atlas


def separate_finishes():
    """Paint takes only the yellow panels: every mixed bone hands its glass, steel, rubber and lamps to a
    finish_ child bone, so the per-pixel paint mask never covers a yellow tile."""
    for b in list(BONES):
        paint = [c for c in b["cubes"] if c["_material"] in PAINT]
        other = [c for c in b["cubes"] if c["_material"] not in PAINT]
        if paint and other:
            fixed = bone("finish_" + b["name"], b["name"], b["pivot"])
            BY_NAME[fixed]["cubes"] = other
            b["cubes"] = paint


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
    """Keep articulation and silhouette while thinning the running gear and fine fittings at distance."""
    result = copy.deepcopy(geometry)
    bones = result["minecraft:geometry"][0]["bones"]
    kept = []
    for b in bones:
        name = b["name"]
        if name.startswith("shoe_"):
            if level == 2 or int(name.rsplit("_", 1)[1]) % 2:
                continue
            b["cubes"] = [c for c in b["cubes"] if c["size"][0] > 8]
            for c in b["cubes"]:
                c["origin"][2] -= 1.9
                c["size"][2] += 3.8
        elif name.startswith(("wheel_", "finish_wheel_")):
            b["cubes"] = [c for c in b["cubes"] if c["size"][0] >= 3][: (3 if level == 1 else 1)]
        else:
            limit = 1.0 if level == 1 else 2.6
            b["cubes"] = [c for c in b["cubes"] if max(c["size"]) >= limit]
        if level == 2 and name in ("track_left", "track_right", "finish_track_left", "finish_track_right") and name.startswith("finish"):
            x = b["pivot"][0]
            uv = copy.deepcopy(BY_NAME["shoe_left_00"]["cubes"][0]["uv"])
            for distance in range(0, 133, 11):
                y, z, angle = track_point(distance)
                b["cubes"].append({"origin": [x - 4.5, y - 1.5, z - 5.8], "size": [9, 3, 11.6],
                                   "rotation": [-angle, 0, 0], "pivot": [x, y, z], "uv": uv})
        kept.append(b)
    result["minecraft:geometry"][0]["bones"] = kept
    return result


def native_geometry(geometry):
    # Authoring/preview uses ordinary right-handed source rotations. Bedrock's
    # rotation convention and GeckoLib's X reflection require this conversion.
    result = copy.deepcopy(geometry)
    for b in result["minecraft:geometry"][0]["bones"]:
        for part in [b, *b.get("cubes", [])]:
            if "rotation" in part:
                x, y, z = part["rotation"]
                part["rotation"] = [-x, y, -z]
    return result


def make_animations():
    """Preview clips for Blockbench. In game, every articulation is driven from the machine's state."""
    animations = {"idle": {"loop": True, "animation_length": 1, "bones": {}},
                  "animation.bulldozer.exhaust_flap": {"loop": True, "animation_length": .3, "bones": {
                      "exhaust_flap": {"rotation": {"0.0": [-18, 0, 0], "0.1": [-34, 0, 0], "0.2": [-22, 0, 0], "0.3": [-18, 0, 0]}}}}}
    names = ["blade_lift", "blade_level"] + [f"{p}_{k}_{s}" for p in ("lift", "tilt") for k in ("barrel", "rod") for s in ("left", "right")]
    lift_channels = {name: {} for name in names}
    lift0, length0, _ = lift_pose(0)
    tilt0, tilt_length0, _ = tilt_pose(0)
    for i in range(41):
        t = i / 8
        angle = LIFT_MIN + (LIFT_MAX - LIFT_MIN) * (1 - math.cos(t / 5 * 2 * math.pi)) / 2
        la, ll, _ = lift_pose(angle)
        ta, tl, _ = tilt_pose(angle)
        lift_channels["blade_lift"].setdefault("rotation", {})[str(t)] = [angle, 0, 0]
        lift_channels["blade_level"].setdefault("rotation", {})[str(t)] = [-angle, 0, 0]
        for side in ("left", "right"):
            lift_channels["lift_barrel_" + side].setdefault("rotation", {})[str(t)] = [la - lift0, 0, 0]
            lift_channels["lift_rod_" + side].setdefault("scale", {})[str(t)] = [1, (ll - LIFT_BARREL) / (length0 - LIFT_BARREL), 1]
            lift_channels["tilt_barrel_" + side].setdefault("rotation", {})[str(t)] = [ta - tilt0, 0, 0]
            lift_channels["tilt_rod_" + side].setdefault("scale", {})[str(t)] = [1, (tl - TILT_BARREL) / (tilt_length0 - TILT_BARREL), 1]
    animations["animation.bulldozer.blade_lift_preview"] = {"loop": True, "animation_length": 5, "bones": lift_channels}
    for side in ("left", "right"):
        channels = {}
        for i in range(LINKS):
            start = i * TRACK_LENGTH / LINKS
            y, z, angle = track_point(start)
            frames = {"position": {}, "rotation": {}}
            for j in range(65):
                distance = start + j / 64 * TRACK_LENGTH
                ny, nz, na = track_point(distance)
                na -= 360 * math.floor(distance / TRACK_LENGTH)
                frames["position"][str(j / 16)] = [0, ny - y, nz - z]
                frames["rotation"][str(j / 16)] = [na - angle, 0, 0]
            channels[f"shoe_{side}_{i:02}"] = frames
        for i, (_, _, _, _, rolling) in enumerate(WHEELS):
            channels[f"wheel_{side}_{i}"] = {"rotation": {str(j / 16): [-math.degrees(j / 64 * TRACK_LENGTH / rolling), 0, 0] for j in range(65)}}
        animations[f"animation.bulldozer.track_{side}_preview"] = {"loop": True, "animation_length": 4, "bones": channels}
    return {"format_version": "1.8.0", "animations": animations}


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
        for name, position in b.get("locators", {}).items():
            identity = uid(b["name"] + "/locator/" + name)
            elements.append({"name": name, "uuid": identity, "type": "locator", "position": position,
                             "rotation": [0, 0, 0], "export": True})
            children[b["name"]].append(identity)

    def outliner(name):
        return {"uuid": uid(name), "isOpen": False, "children": children[name] + [outliner(b["name"]) for b in BONES if b.get("parent") == name]}
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
        clips.append({"uuid": uid(name), "name": name, "loop": "loop", "length": animation["animation_length"], "animators": animators})
    return {"meta": {"format_version": "5.0", "model_format": "bedrock", "box_uv": False},
            "name": "bulldozer", "model_identifier": "jeg.bulldozer", "visible_box": [8, 6, 1.5],
            "resolution": {"width": ATLAS[0], "height": ATLAS[1]}, "elements": elements, "groups": groups,
            "outliner": [outliner("root")], "animations": clips,
            "textures": [{"uuid": uid("texture"), "name": "bulldozer.png", "id": "0", "mode": "bitmap", "internal": True,
                          "source": atlas_url, "width": ATLAS[0], "height": ATLAS[1], "uv_width": ATLAS[0], "uv_height": ATLAS[1]}]}


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
    for angle in [LIFT_MIN + k * .5 for k in range(int((LIFT_MAX - LIFT_MIN) * 2) + 1)]:
        for pose, barrel, base in ((lift_pose, LIFT_BARREL, LIFT_BASE), (tilt_pose, TILT_BARREL, TILT_BASE)) + (
                ((ripper_pose, RIP_BARREL, RIP_BASE),) if 0 <= angle <= RIP_MAX else ()):
            direction, length, target = pose(angle)
            assert 1.0 < length - barrel <= barrel, ("Rod outside its stroke", pose.__name__, angle, length - barrel)
            a = math.radians(direction)
            endpoint = [base[0], base[1] + math.cos(a) * length, base[2] + math.sin(a) * length]
            assert math.dist(endpoint, target) < 1e-8, "Hydraulic endpoint detached"
    # The work volume and the drawn cutting edge must agree: the edge's lowest, foremost vertices are CUT_EDGE.
    edge = [c for c in BY_NAME["blade_tilt"]["cubes"] + BY_NAME.get("finish_blade_tilt", {"cubes": []})["cubes"]]
    lowest = min(c["origin"][1] for c in edge if "rotation" not in c)
    assert abs(lowest - CUT_EDGE[1]) < 1e-6
    front = min(c["origin"][2] for c in edge if "rotation" not in c and abs(c["origin"][1] - lowest) < 1e-6)
    assert abs(front - CUT_EDGE[2]) < 1e-6, front
    for c in edge:
        if "rotation" in c:
            # Rotated moldboard slices must stay above and behind the cutting edge.
            assert c["origin"][1] - 1.5 > CUT_EDGE[1] and c["origin"][2] > CUT_EDGE[2] - 4.0
    for s in (0, 3.5, 46, 92, TRACK_LENGTH - .001):
        a, b = track_point(s), track_point(s + TRACK_LENGTH)
        assert math.dist(a[:2], b[:2]) < 1e-8, "Track path is not closed"
    for kind, z, y, radius, rolling in WHEELS:
        if kind == "roller":
            assert abs((y - radius) - 3.0) < 1e-6, "Bottom rollers must ride on the lower tread"
        if kind == "carrier":
            assert abs((y + radius) - 13.05) < .1, "Carrier rollers must carry the upper tread"
    assert all(set(a["bones"]).issubset(names) for a in animations["animations"].values())
    all_ids = [e["uuid"] for e in project["elements"]] + [g["uuid"] for g in project["groups"]]
    assert len(set(all_ids)) == len(all_ids)
    count = sum(len(b["cubes"]) for b in BONES)
    print(f"Validated {len(BONES)} bones, {count} cuboids, {len(animations['animations'])} clips; UVs, hierarchy, "
          "hydraulic strokes, cutting edge and closed track paths passed.")
    return count


def main():
    create_model()
    separate_finishes()
    atlas = make_texture()
    texture_path = ASSETS / "textures/entity/vehicle/bulldozer.png"
    texture_path.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(texture_path)
    glow = Image.new("RGBA", ATLAS)
    for name in ("lamp", "amber", "tail", "console", "gauge"):
        index = list(MATERIALS).index(name)
        x, y = index % 8 * TILE, index // 8 * TILE
        glow.paste(atlas.crop((x, y, x + TILE, y + TILE)), (x, y))
    glow.save(texture_path.with_name("bulldozer_glow.png"))
    url = "data:image/png;base64," + base64.b64encode(texture_path.read_bytes()).decode()
    geometry = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.jeg.bulldozer", "texture_width": ATLAS[0], "texture_height": ATLAS[1],
                        "visible_bounds_width": 8, "visible_bounds_height": 6, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": [{k: [{ck: cv for ck, cv in c.items() if not ck.startswith("_")} for c in v] if k == "cubes" else v
                   for k, v in b.items()} for b in BONES]}]}
    animations = make_animations()
    project = make_bbmodel(url, animations)
    count = validate(animations, project)
    geometry = native_geometry(geometry)
    write_json(ASSETS / "geckolib/models/entity/vehicle/bulldozer.geo.json", geometry)
    for level in (1, 2):
        write_json(ASSETS / f"geckolib/models/entity/vehicle/lod/bulldozer_lod{level}.geo.json", make_lod(geometry, level))
    write_json(ASSETS / "vehicle_paint/bulldozer.json", {"unpainted_parts": unpainted_parts()})
    hitbox = hitbox_parts()
    write_vehicle_obb(hitbox)
    native_animations = copy.deepcopy(animations)
    for clip in native_animations["animations"].values():
        for channels in clip["bones"].values():
            for time, rotation in channels.get("rotation", {}).items():
                x, y, z = rotation
                channels["rotation"][time] = [-x, y, -z]
    write_json(ASSETS / "geckolib/animations/entity/vehicle/bulldozer.animation.json", native_animations)
    write_json(HERE / "bulldozer.bbmodel", project)
    manifest = {"model": "jeg:entity/vehicle/bulldozer", "units_per_block": 16, "front": "-Z in the source model",
                "lift_pivot": LIFT_PIVOT, "blade_pivot": BLADE_PIVOT, "cut_edge": CUT_EDGE,
                "lift_min_degrees": LIFT_MIN, "lift_max_degrees": LIFT_MAX,
                "lift_base": LIFT_BASE, "lift_end": LIFT_END, "lift_barrel": LIFT_BARREL,
                "tilt_base": TILT_BASE, "tilt_end": TILT_END, "tilt_barrel": TILT_BARREL,
                "track_radius": TRACK_RADIUS, "track_straight": TRACK_STRAIGHT, "track_length": TRACK_LENGTH,
                "links_per_track": LINKS, "wheel_rolling_radii": [w[4] for w in WHEELS],
                "blade_width_blocks": 4, "seat_point": SEAT_POINT, "driver_eye": EYE, "exhaust_tip": EXHAUST_TIP,
                "mirror_center": MIRROR_CENTER, "mirror_rear_target": MIRROR_REAR_TARGET,
                "ripper_upper_pivot": RIP_UPPER_PIVOT, "ripper_lower_pivot": RIP_LOWER_PIVOT, "ripper_link": RIP_LINK,
                "ripper_max_degrees": RIP_MAX, "ripper_base": RIP_BASE, "ripper_end": RIP_END, "ripper_barrel": RIP_BARREL,
                "model_bounds": [list(v) for v in model_bounds()],
                "hitbox": [{"part": p, "covers": label, "min": lo, "max": hi} for p, label, (lo, hi) in hitbox],
                "revision": "05 - seated mirror, continuous centred operator audio",
                "bones": len(BONES), "cuboids": count}
    write_json(HERE / "rig.json", manifest)
    template = (HERE / "preview.template.html").read_text(encoding="utf-8")
    model = {"bones": BONES, "materials": MATERIALS, "atlas": url, "rig": manifest, "audio": {},
             "assembly": json.loads((HERE / "assembly.recipe.json").read_text(encoding="utf-8"))}
    (HERE / "preview.html").write_text(template.replace("/*__MODEL__*/null", json.dumps(model, separators=(",", ":"))), encoding="utf-8")


if __name__ == "__main__":
    main()
