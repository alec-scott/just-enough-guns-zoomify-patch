"""Author the FSV-6 field support vehicle's GeckoLib assets, rig manifest, hitboxes and review images.

Run from any directory: python design/support/generate.py
Requires Pillow. Writes the near/LOD geometry, texture atlas, glow map, paint mask, rig manifest, the vehicle
data's hitboxes (packed and deployed), a Blockbench project and review renders of the packed and deployed poses.

The FSV-6 is a 6x6 tactical truck with an expansible van body, modelled 1:1 (a block is a metre). Its van
floor stands exactly one block above the ground so that, deployed and snapped to the block grid, the walk-in
shell, the real beds placed in its bunks and the module bays all sit on whole blocks. Source units are 16 per
block, the vehicle faces -Z and +X is the driver's left.

Deployed interior (model pixels): x -32..32 (four cells), z -24..56 (five cells), floor y 16, ceiling y 48.
Columns: c0 x -32..-16 (right slide-out), c1, c2 (the fixed body, the aisle), c3 x 16..32 (left slide-out).
Rows: z0 -24..-8 ... z4 40..56. Each slide-out carries a bunk over rows z0-z1 and module bays in rows z2-z4.
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
VEHICLE_JSON = ROOT / "src/main/resources/data/jeg/vehicles/truck.json"

MATERIALS = {
    "olive": (86, 96, 62, 255), "olive_light": (104, 114, 76, 255), "olive_dark": (66, 74, 48, 255),
    "frame": (34, 36, 34, 255), "steel": (112, 116, 112, 255), "rubber": (24, 24, 24, 255),
    "tread": (30, 30, 29, 255), "glass": (150, 178, 186, 70), "lamp": (240, 236, 214, 255),
    "amber": (255, 150, 20, 255), "tail": (196, 38, 26, 255), "chrome": (196, 202, 202, 255),
    "grille": (26, 29, 28, 255), "floor": (150, 146, 138, 255), "interior": (204, 192, 170, 255),
    "seat": (40, 42, 38, 255), "console": (22, 36, 34, 255), "gauge": (16, 18, 18, 255),
    "label": (86, 96, 62, 255), "hazard": (214, 160, 36, 255), "rust": (98, 66, 44, 255),
    "mirror": (156, 183, 194, 255), "led": (255, 208, 146, 255), "canvas": (150, 138, 104, 255),
    "rack": (70, 74, 72, 255), "seal": (18, 20, 19, 255), "mud": (92, 76, 56, 255),
    "blackout": (60, 120, 200, 255),
}
PAINT = ("olive", "olive_light", "olive_dark", "label")
GLOWING = ("lamp", "amber", "tail", "console", "gauge", "led", "blackout")
TILE = 32  # the material tiles, painted per face by the bake
ATLAS = [256, 128]
# The fleet's model and texture review (2026-11-19, the user: "was the FSV texture detailing and model review also
# considered?"): every face its own texels (refit_lib.bake_unique), each laid with its material's tile as before (the
# diamond plate, hazard stripes, gauges and the FSV-6 plate kept), then the detail pass: panel seams, worn edges, bolt
# heads, grime streaks, dust on the roof, mud on the wheels and the lower body. Lamps, glass, mirrors and screens are
# kept as laid. 3 texels a model pixel keeps the atlas near 2048 square.
TEXELS = 3
STRETCHED = ("label", "console", "gauge", "hazard", "mirror")
KEPT = ("glass", "mirror", "lamp", "amber", "tail", "console", "gauge", "led", "blackout")
DETAIL = dict(upscale=1, mud_height=14.0, mud=0.55, wear=0.9, streaks=0.8)
BONES: list[dict] = []
BY_NAME: dict[str, dict] = {}

# ---- Rig constants (mirrored by SupportRig.java). Model pixels, 16 per block.
FLOOR_Y = 16.0                  # van floor: one block above the ground
CEILING_Y = 48.0                # interior ceiling: two blocks above the floor
ROOF_TOP = 50.0
INTERIOR_X = 32.0               # deployed interior half-width
SLIDE_TRAVEL = 16.0             # each slide-out moves one block outward
BODY_HALF = 20.0                # packed body half-width (2.5 blocks)
BODY_FRONT, BODY_REAR = -28.0, 60.0
INTERIOR_FRONT, INTERIOR_REAR = -24.0, 56.0
CELL = 16.0
WHEEL_RADIUS = 7.6              # tyre tops just under the floor plate (15.4) in cut wheel arches; 8.0 cut
                                # through the sill, cab floor and slide floors (user report, 2026-11-15)
WHEEL_HALF_WIDTH = 3.0          # (kept for the rig: the old tyre; see TYRE below)
WHEEL_X = 16.0                  # (kept for the rig: jack and ground probes use it)
# Tyre centre x and half-width per axle: wide super-single steer tyres in front, chunky tandem tyres behind.
TYRE = {"front": (17.5, 4.85), "middle": (17.0, 4.5), "rear": (17.0, 4.5)}   # front clears the arch flare (22.4)
# Wheel arches: the sill, rub strips and cab floor are cut over the tyres (inner edge 12.5), outboard of this x.
ARCH_X = 12.2
ARCH_CLEAR = 0.3
AXLES = [("front", -44.0), ("middle", 20.0), ("rear", 38.0)]
DOOR_HINGE = [0.0, FLOOR_Y, BODY_REAR]
DOOR_HEIGHT = CEILING_Y - FLOOR_Y
DOOR_THICKNESS = -0.3            # the outer skin (door window) is what meets the ground


def _door_open():
    """The hinge angle at which the folded door's outer top edge touches the ground (about 120 degrees)."""
    lo, hi = 90.0, 180.0
    for _ in range(60):
        mid = (lo + hi) / 2
        a = math.radians(mid)
        y = FLOOR_Y + DOOR_HEIGHT * math.cos(a) + DOOR_THICKNESS * math.sin(a)
        lo, hi = (mid, hi) if y > 0 else (lo, mid)
    return round((lo + hi) / 2, 3)


DOOR_OPEN = _door_open()
JACKS = [("front_left", 17.0, -25.5), ("front_right", -17.0, -25.5), ("rear_left", 17.0, 58.0), ("rear_right", -17.0, 58.0)]
JACK_FOOT_Y = 3.0
WINDOWS_Z = (-14.0, 12.0, 38.0)
WINDOW_HALF = 5.0
WINDOW_Y0, WINDOW_Y1 = 30.0, 41.0
# The room's one lamp hangs in the middle of the ceiling (the user's choice, 2026-11-15; it was over the aisle's left
# edge), drawn small so it hangs high (SupportVehicleRenderer.LAMP_HOOK draws the fitted lamp here).
LAMP_HOOK = (0.0, 16.0)
JACK_REACH = 24.0               # foot travel below its stowed height
MAST_BASE = [14.0, ROOF_TOP, -20.0]
MAST_LENGTH = 14.0              # each tube's length
MAST_STAGE = 12.0               # each of the three sections extends this far
BEACON_MOUNT = [0.0, ROOF_TOP, 24.0]
BEACON_RAISE = 8.0
AWNING_ROLLER = [BODY_HALF + 2.0, 45.5]   # x, y of the roller axis on the packed left wall
AWNING_DEPTH = 32.0
SEAT_DRIVER = [10.0, 16.0, -46.0]
SEAT_CODRIVER = [-10.0, 16.0, -46.0]
EYE = [SEAT_DRIVER[0], SEAT_DRIVER[1] + 25.92, SEAT_DRIVER[2]]
# Driver's binnacle (centre x, face bottom y, top y) and co-driver's status screen (centre x, bottom y, top y); model px.
# Mirrored by SupportCockpitLayer.
BINNACLE = (10.0, 31.0, 36.0)
CODRIVER_SCREEN = (-10.0, 24.0, 29.0)
MIRROR_CENTER = [26.0, 36.0, -60.0]
EXHAUST_TIP = [-18.0, 54.0, -29.0]
GENERATOR_EXHAUST = [-21.0, 8.0, -6.0]


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "jeg/support/" + name))


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
    if material in ("label", "console", "gauge", "hazard", "mirror"):
        for face in entry["uv"].values():
            face["uv_size"] = [30, 14 if material == "label" else 30]
    BY_NAME[parent]["cubes"].append(entry)


def box(parent, name, a, b, material="olive", rotation=None, pivot=None):
    origin = [min(a[i], b[i]) for i in range(3)]
    size = [abs(b[i] - a[i]) for i in range(3)]
    cube(parent, name, origin, size, material, rotation, pivot)


def side_x(side, x0, x1):
    """An x range authored for the left (+X) side, mirrored to the right for side -1."""
    return (x0, x1) if side > 0 else (-x1, -x0)


def arch_gaps():
    """z ranges cut out of the sill, rub strips and cab floor over the tyres: the front axle, and the tandem as one."""
    r = WHEEL_RADIUS + ARCH_CLEAR
    return [(AXLES[0][1] - r, AXLES[0][1] + r), (AXLES[1][1] - r, AXLES[2][1] + r)]


def around(z0, z1, gaps):
    """The parts of z0..z1 outside every gap."""
    parts, start = [], z0
    for g0, g1 in sorted(gaps):
        if g1 <= start or g0 >= z1:
            continue
        if g0 > start:
            parts.append((start, g0))
        start = max(start, g1)
    if start < z1:
        parts.append((start, z1))
    return parts


def sbox(parent, name, side, x0, x1, y0, y1, z0, z1, material="olive", rotation=None, pivot=None):
    a, b = side_x(side, x0, x1)
    if pivot is not None:
        pivot = [pivot[0] * side, pivot[1], pivot[2]]
    if rotation is not None and side < 0:
        rotation = [rotation[0], -rotation[1], -rotation[2]]
    box(parent, name, [a, y0, z0], [b, y1, z1], material, rotation, pivot)


def cylinder_x(parent, name, center, length, radius, material, layers=8):
    """Round silhouette along X built from cuboid slices (a wheel seen from the side)."""
    x, y, z = center
    step = 2 * radius / layers
    for i in range(layers):
        offset = -radius + (i + .5) * step
        half = math.sqrt(max(.001, radius * radius - offset * offset))
        cube(parent, name, [x - length / 2, y - radius + i * step, z - half], [length, step + .02, 2 * half], material)


def cylinder_y(parent, name, x, z, y0, y1, radius, material, layers=5):
    """Vertical round bar from Z-depth slices."""
    step = 2 * radius / layers
    for i in range(layers):
        offset = -radius + (i + .5) * step
        half = math.sqrt(max(.001, radius * radius - offset * offset))
        cube(parent, name, [x - radius + i * step, y0, z - half], [step + .02, y1 - y0, 2 * half], material)


def cylinder_z(parent, name, x, y, z0, z1, radius, material, layers=5):
    """Horizontal round bar along Z (the awning roller, a steering column)."""
    step = 2 * radius / layers
    for i in range(layers):
        offset = -radius + (i + .5) * step
        half = math.sqrt(max(.001, radius * radius - offset * offset))
        cube(parent, name, [x - half, y - radius + i * step, z0], [2 * half, step + .02, z1 - z0], material)


def create_model():
    bone("root", None)
    bone("chassis", "root")

    # ---- Ladder frame, axles, tanks, generator and underbody lockers.
    for side in (1, -1):
        sbox("chassis", "frame rail", side, 5, 9, 7, 12, -62, 62, "frame")
        sbox("chassis", "rail flange", side, 4.5, 9.5, 11.6, 12.4, -62, 62, "frame")
    for z in (-56, -30, -6, 12, 30, 46, 60):
        box("chassis", "cross member", [-5, 8, z - 1.2], [5, 11, z + 1.2], "frame")
    for name, z in AXLES:
        box("chassis", name + " axle beam", [-13, 5, z - 1.2], [13, 7.8, z + 1.2], "steel")
        box("chassis", name + " differential", [-3.2, 3.4, z - 3], [3.2, 8.6, z + 3], "steel")
        for side in (1, -1):
            sbox("chassis", "leaf spring", side, 7, 11, 7.8, 9.4, z - 9, z + 9, "frame")
            sbox("chassis", "spring hanger", side, 7.4, 10.6, 9.4, 12, z - 9.5, z - 7.5, "frame")
            sbox("chassis", "spring hanger", side, 7.4, 10.6, 9.4, 12, z + 7.5, z + 9.5, "frame")
            sbox("chassis", "shock absorber", side, 10.6, 11.8, 6, 12, z + 3, z + 4.2, "steel")
    box("chassis", "propeller shaft", [-.9, 5.8, -36], [.9, 7.6, 34], "steel")
    # Generator set on the right, ahead of the rear axles; it runs the shelter when deployed.
    bone("generator", "chassis", [-15, 8, -12])
    box("generator", "generator enclosure", [-21, 3.6, -22], [-9.6, 13.6, -2], "olive_dark")
    for k in range(5):
        box("generator", "generator louvre", [-21.25, 5 + k * 1.7, -20], [-21, 5.8 + k * 1.7, -12], "grille")
    box("generator", "generator access panel", [-21.25, 4.6, -10.6], [-21, 12.6, -3.6], "olive")
    box("generator", "panel latch", [-21.45, 8.2, -9.6], [-21.2, 9.2, -8.8], "chrome")
    cylinder_y("generator", "generator exhaust", GENERATOR_EXHAUST[0], GENERATOR_EXHAUST[2], 3.4, 8, .7, "rust", 4)
    box("generator", "fuel filler", [-21.3, 11.0, -21.0], [-20.8, 12.4, -19.6], "steel")
    # Diesel tank and the battery box on the left.
    cylinder_x("chassis", "fuel tank", [15, 8.2, -13.5], 10, 4.6, "olive_dark", 8)
    box("chassis", "fuel tank strap", [9.8, 3.4, -18.6], [20.2, 13.2, -17.8], "frame")
    box("chassis", "fuel tank strap", [9.8, 3.4, -9.2], [20.2, 13.2, -8.4], "frame")
    cylinder_y("chassis", "fuel cap", 18.2, -13.5, 12.6, 13.8, .9, "steel", 4)
    # Underbody lockers: storage opened from outside (left behind the tank, right behind the generator).
    for side, z0, z1 in ((1, -3.5, 12.0), (-1, -1.0, 12.0)):   # ahead of the middle axle's tyres (z 13.5)
        name = "left" if side > 0 else "right"
        sbox("chassis", "locker box", side, 10, 20, 3.5, 13.4, z0, z1, "olive_dark")
        bone("locker_door_" + name, "chassis", [20 * side, 3.5, (z0 + z1) / 2])
        sbox("locker_door_" + name, "locker door", side, 20, 20.5, 3.8, 13.0, z0 + .4, z1 - .4, "olive")
        sbox("locker_door_" + name, "locker handle", side, 20.5, 20.9, 10.6, 11.4, (z0 + z1) / 2 - 2, (z0 + z1) / 2 + 2, "chrome")
        sbox("locker_door_" + name, "locker stencil", side, 20.5, 20.6, 5, 9.4, z0 + 2, z0 + 8, "label")
        # A hazard-striped grab rail along the top of the door, so the lockers read as something to open.
        sbox("locker_door_" + name, "locker grab rail", side, 20.5, 21.1, 12.0, 12.8, z0 + 1, z1 - 1, "hazard")
    # Rear bumper, under-ride guard, tail and reversing lamps.
    box("chassis", "rear bumper", [-19, 5, 60], [19, 9, 62.5], "frame")
    box("chassis", "number plate", [-4, 5.4, 62.5], [4, 8.6, 62.7], "label")
    for side in (1, -1):
        sbox("chassis", "tail lamp housing", side, 8.6, 19.6, 4.6, 10.4, 62.4, 62.8, "frame")
        sbox("chassis", "tail lamp", side, 13, 17.6, 5.4, 9.6, 62.8, 63.2, "tail")
        sbox("chassis", "indicator", side, 17, 19, 6, 8.4, 62.5, 62.9, "amber")
        sbox("chassis", "reversing lamp", side, 9.2, 12.6, 5.4, 9.6, 62.8, 63.2, "lamp")
        sbox("chassis", "blackout marker", side, 13.5, 15, 8.6, 9.2, 62.5, 62.9, "blackout")
    for side in (1, -1):
        # Mud flaps behind the rear wheels and guards over the tandem.
        sbox("chassis", "mud flap", side, 12, 22, 1.5, 14, 47.6, 48.2, "rubber")
        sbox("chassis", "tandem fender", side, 20, 22.4, 15.6, 16.4, 11, 48, "olive_dark")
        sbox("chassis", "tandem fender lip", side, 21.8, 22.4, 13.8, 15.6, 11, 48, "olive_dark")

    # ---- Wheels: six singles on 1 m tyres; the front pair steer about their kingpins.
    for i, (name, z) in enumerate(AXLES):
        for side in (1, -1):
            label = f"{name}_{'left' if side > 0 else 'right'}"
            parent = "chassis"
            tyre_x, half = TYRE[name]
            x = tyre_x * side
            if name == "front":
                parent = bone("steer_" + ('left' if side > 0 else 'right'), "chassis", [x, WHEEL_RADIUS, z])
            wheel = bone("wheel_" + label, parent, [x, WHEEL_RADIUS, z])
            cylinder_x(wheel, "tyre", [x, WHEEL_RADIUS, z], 2 * half, WHEEL_RADIUS, "tread", 12)
            # Sidewall, rim and hub stacked outward on separate planes (no coplanar faces to flicker).
            cylinder_x(wheel, "sidewall", [x + side * (half + .1), WHEEL_RADIUS, z], .2, WHEEL_RADIUS - .8, "rubber", 8)
            cylinder_x(wheel, "rim", [x + side * (half + .33), WHEEL_RADIUS, z], .26, 4.6, "olive_dark", 8)
            cylinder_x(wheel, "hub", [x + side * (half + .6), WHEEL_RADIUS, z], .28, 1.8, "steel", 4)
            for k in range(6):
                a = k * math.tau / 6
                box(wheel, "wheel nut", [x + side * (half + .35) - .2, WHEEL_RADIUS + math.sin(a) * 3.0 - .35, z + math.cos(a) * 3.0 - .35],
                    [x + side * (half + .35) + .2, WHEEL_RADIUS + math.sin(a) * 3.0 + .35, z + math.cos(a) * 3.0 + .35], "chrome")
            # Tread blocks on the crown so the rotation reads.
            for k in range(16):
                a = k * math.tau / 16
                yc, zc = WHEEL_RADIUS + math.sin(a) * (WHEEL_RADIUS - .25), z + math.cos(a) * (WHEEL_RADIUS - .25)
                box(wheel, "tread lug", [x - half + .3, yc - .6, zc - 1.0], [x + half - .3, yc + .6, zc + 1.0],
                    "rubber", [math.degrees(-a), 0, 0], [x, yc, zc])

    # ---- Cab: forward-control steel cab with two seats, the dash and a turning wheel.
    bone("cab", "chassis", [0, 16, -46])
    # The cab floor, cut over the front tyres outboard; the front wheel arches (above the floor) roof the cut.
    box("cab", "cab floor", [-ARCH_X, 13.6, -63], [ARCH_X, 16, -30.5], "frame")
    for side in (1, -1):
        for z0, z1 in around(-63, -30.5, arch_gaps()):
            sbox("cab", "cab floor", side, ARCH_X, 19.5, 13.6, 16, z0, z1, "frame")
    box("cab", "cab back wall", [-19.5, 16, -32], [19.5, 48, -30.5])
    box("cab", "cab roof", [-20, 46, -64], [20, 49, -30.5])
    box("cab", "roof peak", [-18.5, 49, -62], [18.5, 49.6, -32], "olive_light")
    box("cab", "front panel", [-20, 14, -64], [20, 32, -62])
    box("cab", "grille", [-12, 17, -64.3], [12, 29, -64], "grille")
    for y in (18.6, 21.6, 24.6, 27.4):
        box("cab", "grille bar", [-12.2, y, -64.6], [12.2, y + .9, -64.2], "olive_dark")
    box("cab", "badge", [-4, 30, -64.3], [4, 31.6, -64], "label")
    box("cab", "windscreen frame", [-20, 32, -64], [20, 33, -62], "olive_dark")
    box("cab", "windscreen header", [-20, 45, -64], [20, 46, -62], "olive_dark")
    box("cab", "windscreen pillar", [-20, 33, -64], [-18, 45, -62], "olive_dark")
    box("cab", "windscreen pillar", [18, 33, -64], [20, 45, -62], "olive_dark")
    box("cab", "centre pillar", [-.6, 33, -64], [.6, 45, -62.4], "olive_dark")
    box("cab", "windscreen", [-18, 33, -63.4], [18, 45, -63], "glass")
    for x in (-9, 9):
        box("cab", "wiper", [x - 6, 33.4, -63.8], [x + 6, 33.9, -63.5], "frame", [0, 0, 16], [x, 33.6, -63.6])
    for side in (1, -1):
        sbox("cab", "cab side lower", side, 19.5, 20, 17.6, 32, -62, -30.5)
        sbox("cab", "cab sill", side, 19.5, 20, 14, 17.6, -62, -53)
        sbox("cab", "cab sill", side, 19.5, 20, 14, 17.6, -35, -30.5)
        sbox("cab", "cab side upper", side, 19.5, 20, 32, 46, -62, -56, "olive")
        sbox("cab", "cab side upper", side, 19.5, 20, 32, 46, -40, -30.5, "olive")
        sbox("cab", "door window", side, 19.6, 19.9, 33, 45, -56, -40, "glass")
        sbox("cab", "window frame", side, 19.5, 20, 45, 46, -56, -40, "olive_dark")
        sbox("cab", "door seam", side, 20, 20.1, 15, 45, -56.2, -55.8, "seal")
        sbox("cab", "door seam", side, 20, 20.1, 15, 45, -40.2, -39.8, "seal")
        sbox("cab", "door handle", side, 20, 20.4, 30, 30.8, -43, -41, "chrome")
        sbox("cab", "cab step", side, 16, 20.5, 8, 9, -61.5, -52, "rack")
        sbox("cab", "step hanger", side, 18, 19, 9, 14, -61, -60, "frame")
        sbox("cab", "step hanger", side, 18, 19, 9, 14, -53.5, -52.5, "frame")
        sbox("cab", "front wheel arch", side, 11.5, 23.2, 16.0, 17.6, -53, -35, "olive_dark")
        sbox("cab", "arch flare", side, 22.4, 23.2, 13.0, 16.0, -53, -35, "olive_dark")
        sbox("cab", "headlamp housing", side, 13, 19, 19, 25, -64.6, -62, "frame")
        sbox("cab", "headlamp", side, 13.6, 18.4, 19.6, 24.4, -64.8, -64.6, "lamp")
        sbox("cab", "indicator", side, 13.6, 18.4, 16.6, 18.4, -64.8, -64.2, "amber")
        sbox("cab", "blackout lamp", side, 9.6, 11.6, 25.8, 27.2, -64.6, -64.2, "blackout")
        sbox("cab", "marker lamp", side, 15, 18, 49, 50.2, -62, -61, "amber")
        # Mirrors on outriggers, aimed back along the body.
        sbox("cab", "mirror arm", side, 20, 25.4, 40, 40.8, -61, -60.2, "frame")
        sbox("cab", "mirror arm", side, 20, 25.4, 31, 31.8, -61, -60.2, "frame")
        sbox("cab", "mirror head", side, 24.6, 27.4, 31, 41, -61.4, -59.4, "frame")
        sbox("cab", "mirror glass", side, 24.9, 27.1, 31.6, 40.4, -59.4, -59.2, "mirror")
    box("cab", "front bumper", [-20, 8, -66.5], [20, 13.6, -62], "frame")
    box("cab", "tow hook", [-1.6, 9, -67.6], [1.6, 11.6, -66.5], "steel")
    for x in (-14, 14):
        box("cab", "bumper step", [x - 3, 12, -66.8], [x + 3, 13.6, -64], "rack")
    # Interior: seats, dash, gear lever. The wheel turns with the steering.
    for name, seat in (("driver", SEAT_DRIVER), ("codriver", SEAT_CODRIVER)):
        x, y, z = seat
        box("cab", name + " seat base", [x - 4, y, z - 3], [x + 4, y + 6, z + 4], "frame")
        box("cab", name + " seat cushion", [x - 4.5, y + 6, z - 4], [x + 4.5, y + 10, z + 5], "seat")
        box("cab", name + " seat back", [x - 4.5, y + 10, z + 4], [x + 4.5, y + 24, z + 6.5], "seat", [8, 0, 0], [x, y + 10, z + 5])
        box("cab", name + " headrest", [x - 3, y + 24.5, z + 5], [x + 3, y + 28, z + 7], "seat", [8, 0, 0], [x, y + 10, z + 5])
    box("cab", "dash", [-19, 16, -62], [19, 30, -57], "frame")
    box("cab", "dash top", [-19, 30, -62], [19, 31, -55], "seal")
    # The driver's instrument binnacle on the dash top, seen over the steering wheel: a hood over a flat face at
    # z -57.5 (SupportCockpitLayer draws the lit gauges and lamps on it). The co-driver's status screen sits in a bezel
    # on the dash face (z -57).
    bx, by0, by1 = BINNACLE
    box("cab", "binnacle", [bx - 7, by0, -61], [bx + 7, by1, -57.6], "frame")
    box("cab", "binnacle face", [bx - 6.6, by0 + .3, -57.6], [bx + 6.6, by1 - .3, -57.5], "gauge")
    box("cab", "binnacle hood", [bx - 7.4, by1, -61.5], [bx + 7.4, by1 + .7, -56.4], "seal")
    for s in (-1, 1):
        box("cab", "binnacle cheek", [bx + s * 7 - .4, by0, -61], [bx + s * 7 + .4, by1, -56.6], "seal")
    cx, cy0, cy1 = CODRIVER_SCREEN
    box("cab", "status bezel", [cx - 6.4, cy0 - .4, -57.2], [cx + 6.4, cy1 + .4, -56.95], "frame")
    box("cab", "status screen", [cx - 6, cy0, -56.95], [cx + 6, cy1, -56.9], "seal")
    box("cab", "gear lever", [-.5, 16, -50], [.5, 24, -49], "steel")
    box("cab", "gear knob", [-1, 24, -50.5], [1, 25.5, -48.5], "rubber")
    bone("steering_column", "cab", [SEAT_DRIVER[0], 28, -55], [-50, 0, 0])
    box("steering_column", "steering column", [SEAT_DRIVER[0] - .8, 22, -55.8], [SEAT_DRIVER[0] + .8, 28, -54.2], "frame")
    bone("steering_wheel", "steering_column", [SEAT_DRIVER[0], 28, -55])
    sx = SEAT_DRIVER[0]
    for k in range(12):
        a = k * math.tau / 12
        box("steering_wheel", "wheel rim", [sx + math.cos(a) * 4.2 - .65, 28 + math.sin(a) * 4.2 - .65, -55.5],
            [sx + math.cos(a) * 4.2 + .65, 28 + math.sin(a) * 4.2 + .65, -54.5], "rubber")
    box("steering_wheel", "wheel spoke", [sx - 4, 27.6, -55.3], [sx + 4, 28.4, -54.7], "frame")
    box("steering_wheel", "wheel spoke", [sx - .4, 24, -55.3], [sx + .4, 28, -54.7], "frame")
    box("steering_wheel", "wheel boss", [sx - 1.2, 26.8, -55.6], [sx + 1.2, 29.2, -54.4], "frame")
    # Exhaust stack behind the cab on the right.
    cylinder_y("cab", "exhaust stack", EXHAUST_TIP[0], EXHAUST_TIP[2], 30, 53.4, 1.1, "rust", 5)
    box("cab", "stack guard", [-19.6, 34, -30.4], [-16.4, 46, -29.8], "mesh" if "mesh" in MATERIALS else "grille")
    box("cab", "rain cap", [-19.4, 53.4, -30.2], [-16.6, 54, -27.8], "frame")
    # Amber beacons on the cab roof, each with a reflector that turns.
    for side in (1, -1):
        name = "left" if side > 0 else "right"
        x = 12 * side
        box("cab", "beacon base", [x - 2, 49.6, -42], [x + 2, 50.4, -38], "frame")
        box("cab", "beacon dome", [x - 1.6, 50.4, -41.6], [x + 1.6, 53.4, -38.4], "amber")
        bone("beacon_" + name, "cab", [x, 52, -40])
        box("beacon_" + name, "beacon reflector", [x - 1.2, 50.6, -40.2], [x + 1.2, 53.0, -39.8], "chrome")

    # ---- Van body: fixed floor, front wall, roof and rear posts. The side walls are the slide-outs' outer walls.
    bone("body", "chassis", [0, FLOOR_Y, 16])
    # The sill, cut over the tandem outboard (the floor plate and slide floors above close the cut).
    box("body", "body sill", [-ARCH_X, 13.6, BODY_FRONT], [ARCH_X, FLOOR_Y - .6, BODY_REAR], "frame")
    for side in (1, -1):
        for z0, z1 in around(BODY_FRONT, BODY_REAR, arch_gaps()):
            sbox("body", "body sill", side, ARCH_X, BODY_HALF, 13.6, FLOOR_Y - .6, z0, z1, "frame")
    box("body", "floor plate", [-16, FLOOR_Y - .6, INTERIOR_FRONT], [16, FLOOR_Y, INTERIOR_REAR], "floor")
    box("body", "front wall", [-BODY_HALF, FLOOR_Y, BODY_FRONT], [BODY_HALF, CEILING_Y, INTERIOR_FRONT])
    box("body", "front wall lining", [-16, FLOOR_Y, INTERIOR_FRONT], [16, CEILING_Y, INTERIOR_FRONT + .3], "interior")
    box("body", "roof", [-BODY_HALF, CEILING_Y, BODY_FRONT], [BODY_HALF, ROOF_TOP, BODY_REAR])
    box("body", "roof liner", [-16, CEILING_Y - .3, INTERIOR_FRONT], [16, CEILING_Y, INTERIOR_REAR], "interior")
    box("body", "roof rim", [-BODY_HALF - .3, ROOF_TOP - 1, BODY_FRONT - .3], [BODY_HALF + .3, ROOF_TOP + .4, BODY_FRONT + 1], "olive_dark")
    box("body", "roof rim", [-BODY_HALF - .3, ROOF_TOP - 1, BODY_REAR - 1], [BODY_HALF + .3, ROOF_TOP + .4, BODY_REAR + .3], "olive_dark")
    for side in (1, -1):
        sbox("body", "rear post", side, 16, BODY_HALF, FLOOR_Y, CEILING_Y, INTERIOR_REAR, BODY_REAR)
        sbox("body", "rear post lining", side, 16, 16.3, FLOOR_Y, CEILING_Y, INTERIOR_REAR, BODY_REAR, "interior")
        sbox("body", "front corner post", side, BODY_HALF - .2, BODY_HALF + .4, FLOOR_Y - 2, CEILING_Y + 1, BODY_FRONT - .2, BODY_FRONT + 3, "olive_dark")
        sbox("body", "rear corner post", side, BODY_HALF - .2, BODY_HALF + .4, FLOOR_Y - 2, CEILING_Y + 1, BODY_REAR - 3, BODY_REAR + .2, "olive_dark")
        for z0, z1 in around(BODY_FRONT, BODY_REAR, arch_gaps()):
            sbox("body", "sill rub strip", side, BODY_HALF, BODY_HALF + .3, 13.6, 14.6, z0, z1, "frame")
        sbox("body", "front marker", side, 17, 19, CEILING_Y + .2, CEILING_Y + 1.2, BODY_FRONT - .4, BODY_FRONT, "amber")
        sbox("body", "rear marker", side, 17, 19, CEILING_Y - 2, CEILING_Y - .8, BODY_REAR + .2, BODY_REAR + .5, "tail")
        sbox("body", "rear work lamp", side, 12, 15, CEILING_Y - 1.4, CEILING_Y + .2, BODY_REAR, BODY_REAR + .6, "lamp")
    box("body", "door header", [-16, CEILING_Y - .1, INTERIOR_REAR], [16, CEILING_Y, BODY_REAR], "olive_dark")
    box("body", "stencil", [-8, 38, BODY_FRONT - .2], [8, 42, BODY_FRONT], "label")
    box("body", "third brake light", [-4, CEILING_Y - 1.6, BODY_REAR + .2], [4, CEILING_Y - .2, BODY_REAR + .6], "tail")
    # The lamp hook: the room's one light hangs here once a lamp is fitted.
    hx, hz = LAMP_HOOK
    box("body", "lamp hook plate", [hx - 1.5, CEILING_Y - .6, hz - 1.5], [hx + 1.5, CEILING_Y - .3, hz + 1.5], "steel")
    box("body", "lamp hook", [hx - .3, CEILING_Y - 1.4, hz - .3], [hx + .3, CEILING_Y - .6, hz + .3], "frame")
    # The partition behind the cab carries two panels, one per wall cell (SupportShell: PANEL in column 1, BEACON_PANEL in
    # column 2): on the right of the aisle the support console (fit-out), on the left the beacon terminal.
    for cx, name in ((-8.0, "console"), (8.0, "beacon terminal")):
        box("body", f"{name} housing", [cx - 7, FLOOR_Y + 14, INTERIOR_FRONT + .3], [cx + 7, FLOOR_Y + 27, INTERIOR_FRONT + 1.4], "frame")
        # The screen is dark glass: SupportCockpitLayer draws the live display on it. Plain "seal", not "gauge": the
        # atlas paints a dial on "gauge" faces, and the live display (additive) let it show through.
        box("body", f"{name} screen", [cx - 5.8, FLOOR_Y + 19.5, INTERIOR_FRONT + 1.4], [cx + 5.8, FLOOR_Y + 25.5, INTERIOR_FRONT + 1.55], "seal")
        # (No name plate over the screen: the label tile stretched onto a 9 x 1 px plate came out as a blur, and the
        # screens' title bars name them, 2026-11-19.)
    for x in (-12.0, -8.0, -4.0):
        box("body", "console gauge", [x - 1.4, FLOOR_Y + 15.2, INTERIOR_FRONT + 1.4], [x + 1.4, FLOOR_Y + 18.4, INTERIOR_FRONT + 1.6], "seal")
    # The beacon terminal: a row of four level lamps under its screen and a cyan beam window.
    for k in range(4):
        x = 3.5 + k * 3
        box("body", "beacon level lamp", [x - 1, FLOOR_Y + 16, INTERIOR_FRONT + 1.4], [x + 1, FLOOR_Y + 17.6, INTERIOR_FRONT + 1.6], "seal")
    # Roof hatch with the beacon mount, and the mast's base tube.
    bx, by, bz = BEACON_MOUNT
    box("body", "hatch frame", [bx - 9, ROOF_TOP, bz - 9], [bx + 9, ROOF_TOP + .8, bz + 9], "olive_dark")
    bone("beacon_mount", "body", [bx, ROOF_TOP, bz])
    box("beacon_mount", "beacon platform", [bx - 8, ROOF_TOP - .4, bz - 8], [bx + 8, ROOF_TOP + .4, bz + 8], "rack")
    box("beacon_mount", "platform rail", [bx - 8, ROOF_TOP + .4, bz - 8], [bx + 8, ROOF_TOP + 1, bz - 7.4], "steel")
    box("beacon_mount", "platform rail", [bx - 8, ROOF_TOP + .4, bz + 7.4], [bx + 8, ROOF_TOP + 1, bz + 8], "steel")
    for side in (1, -1):
        box("beacon_mount", "lift post", [bx + side * 7 - .6, ROOF_TOP - 8, bz - .6], [bx + side * 7 + .6, ROOF_TOP - .4, bz + .6], "chrome")
    # Light mast: carried flat on the roof pointing aft, swung upright on its hinge, then telescoped.
    mx, my, mz = MAST_BASE
    box("body", "mast hinge bracket", [mx - 2.4, my, mz - 2.4], [mx + 2.4, my + 1.2, mz + 2.4], "frame")
    box("body", "mast rest", [mx - 2.0, my, mz + MAST_LENGTH - 3], [mx + 2.0, my + 2.4, mz + MAST_LENGTH - 1.6], "frame")
    hinge_y = my + 1.8
    mast = bone("mast", "body", [mx, hinge_y, mz])
    cylinder_y(mast, "mast base tube", mx, mz, hinge_y - .6, hinge_y + MAST_LENGTH, 1.7, "olive_dark", 6)
    box(mast, "hinge knuckle", [mx - 1.2, hinge_y - 1.2, mz - 1.2], [mx + 1.2, hinge_y + 1.2, mz + 1.2], "steel")
    previous = mast
    for k, radius in enumerate((1.4, 1.1, .8)):
        section = bone(f"mast_{k + 1}", previous, [mx, hinge_y, mz])
        cylinder_y(section, "mast section", mx, mz, hinge_y + .2, hinge_y + MAST_LENGTH - .4, radius, "steel", 5)
        box(section, "section collar", [mx - radius - .2, hinge_y + MAST_LENGTH - 1.2, mz - radius - .2],
            [mx + radius + .2, hinge_y + MAST_LENGTH - .4, mz + radius + .2], "frame")
        previous = section
    top = hinge_y + MAST_LENGTH - .4
    head = bone("mast_head", previous, [mx, top, mz])
    box(head, "light bar", [mx - 5, top, mz - 1], [mx + 5, top + 1.2, mz + 1], "frame")
    for dx in (-3, 3):
        box(head, "floodlight housing", [mx + dx - 1.8, top + 1.2, mz - 1.6], [mx + dx + 1.8, top + 3.8, mz + 1.6], "frame")
        box(head, "floodlight", [mx + dx - 1.4, top + 1.5, mz - 1.8], [mx + dx + 1.4, top + 3.5, mz - 1.6], "lamp")
        box(head, "floodlight", [mx + dx + 1.8, top + 1.5, mz - 1.4], [mx + dx + 2.0, top + 3.5, mz + 1.4], "lamp")
    BY_NAME[head]["locators"] = {"floodlights": [mx, top + 2.5, mz]}

    # ---- Rear door: hinged at the floor edge, it folds down into a ramp whose top edge meets the ground.
    hx, hy, hz = DOOR_HINGE
    bone("rear_door", "body", DOOR_HINGE)
    # The door round its window opening (x -6..6, 20..28 px up the door).
    for a, b, y0, y1 in ((-16, 16, 0, 20), (-16, 16, 28, DOOR_HEIGHT), (-16, -6, 20, 28), (6, 16, 20, 28)):
        box("rear_door", "door panel", [a, hy + y0, hz - 4], [b, hy + y1, hz])
        box("rear_door", "door lining", [max(a, -15.5), hy + max(y0, .5), hz - 4.3], [min(b, 15.5), hy + min(y1, DOOR_HEIGHT - .5), hz - 4], "floor")
    for k in (0, 1, 2, 4):
        box("rear_door", "ramp tread", [-14, hy + 4 + k * 5.6, hz - 4.6], [14, hy + 4.8 + k * 5.6, hz - 4.3], "rack")
    box("rear_door", "door window", [-6, hy + 20, hz - 2.1], [6, hy + 28, hz - 1.9], "glass")
    box("rear_door", "door handle", [8, hy + 14, hz], [12, hy + 15, hz + .6], "chrome")
    box("rear_door", "hinge", [-15, hy - .8, hz - .8], [15, hy + .8, hz + .8], "steel")
    for x in (-10, 10):
        box("rear_door", "door stencil", [x - 3, hy + 6, hz], [x + 3, hy + 9, hz + .1], "hazard")

    # ---- Slide-outs: each carries the floor, outer wall, roof and end walls of its column, the bays and a bunk.
    for side in (1, -1):
        name = "left" if side > 0 else "right"
        slide = bone("slide_" + name, "body", [0, FLOOR_Y, 16])
        # Underside at the floor plate's (15.4), above the tyres in every slide position (it was 14.2, through them).
        # The plate's top sits just under the block grid: the bunk's real bed stands on the cell below it, and a plate up
        # through the cell hid the bed's legs and flickered against them (the user's review, 2026-11-18).
        sbox(slide, "slide floor", side, 0, BODY_HALF, FLOOR_Y - .6, FLOOR_Y - .25, INTERIOR_FRONT, INTERIOR_REAR, "frame")
        sbox(slide, "slide floor plate", side, 0, 16, FLOOR_Y - .25, FLOOR_Y - .05, INTERIOR_FRONT, INTERIOR_REAR, "floor")
        # The outer wall and its lining, built round three window openings you can see through from either side.
        edges = [INTERIOR_FRONT]
        for zc in WINDOWS_Z:
            edges += [zc - WINDOW_HALF, zc + WINDOW_HALF]
        edges.append(INTERIOR_REAR)
        sbox(slide, "outer wall", side, 16, BODY_HALF, FLOOR_Y + .4, WINDOW_Y0, INTERIOR_FRONT, INTERIOR_REAR)
        sbox(slide, "outer wall", side, 16, BODY_HALF, WINDOW_Y1, CEILING_Y, INTERIOR_FRONT, INTERIOR_REAR)
        sbox(slide, "wall lining", side, 15.7, 16, FLOOR_Y + .6, WINDOW_Y0, INTERIOR_FRONT, INTERIOR_REAR, "interior")
        sbox(slide, "wall lining", side, 15.7, 16, WINDOW_Y1, CEILING_Y - 2, INTERIOR_FRONT, INTERIOR_REAR, "interior")
        for z0, z1 in zip(edges[0::2], edges[1::2]):
            sbox(slide, "outer wall", side, 16, BODY_HALF, WINDOW_Y0, WINDOW_Y1, z0, z1)
            sbox(slide, "wall lining", side, 15.7, 16, WINDOW_Y0, WINDOW_Y1, z0, z1, "interior")
        sbox(slide, "slide roof", side, 0, BODY_HALF, CEILING_Y - 2, CEILING_Y, INTERIOR_FRONT, INTERIOR_REAR, "olive_dark")
        sbox(slide, "slide ceiling", side, 0, 16, CEILING_Y - 2.3, CEILING_Y - 2, INTERIOR_FRONT, INTERIOR_REAR, "interior")
        # End walls stand just outside the cells they close: 1.5 px into the first cell, the bed's head ran into the
        # front one and flickered (the user's review, 2026-11-18; the guide's "walls stand proud of the block cells").
        for z0, z1 in ((INTERIOR_FRONT - 1.9, INTERIOR_FRONT - .05), (INTERIOR_REAR + .05, INTERIOR_REAR + 1.9)):
            sbox(slide, "end wall", side, 0, BODY_HALF, FLOOR_Y - .25, CEILING_Y - 2, z0, z1, "olive_dark")
        sbox(slide, "seal strip", side, BODY_HALF, BODY_HALF + .3, FLOOR_Y + .4, CEILING_Y, INTERIOR_FRONT - .3, INTERIOR_FRONT + .5, "seal")
        sbox(slide, "seal strip", side, BODY_HALF, BODY_HALF + .3, FLOOR_Y + .4, CEILING_Y, INTERIOR_REAR - .5, INTERIOR_REAR + .3, "seal")
        sbox(slide, "drip rail", side, BODY_HALF, BODY_HALF + .6, CEILING_Y - .6, CEILING_Y, INTERIOR_FRONT, INTERIOR_REAR, "olive_dark")
        for z in WINDOWS_Z:
            sbox(slide, "side window", side, 17.9, 18.1, WINDOW_Y0, WINDOW_Y1, z - WINDOW_HALF, z + WINDOW_HALF, "glass")
            sbox(slide, "window frame", side, BODY_HALF, BODY_HALF + .4, WINDOW_Y1, WINDOW_Y1 + .8, z - WINDOW_HALF - .4, z + WINDOW_HALF + .4, "olive_dark")
            sbox(slide, "window frame", side, BODY_HALF, BODY_HALF + .4, WINDOW_Y0 - .8, WINDOW_Y0, z - WINDOW_HALF - .4, z + WINDOW_HALF + .4, "olive_dark")
            sbox(slide, "window frame", side, BODY_HALF, BODY_HALF + .4, WINDOW_Y0, WINDOW_Y1, z - WINDOW_HALF - .4, z - WINDOW_HALF, "olive_dark")
            sbox(slide, "window frame", side, BODY_HALF, BODY_HALF + .4, WINDOW_Y0, WINDOW_Y1, z + WINDOW_HALF, z + WINDOW_HALF + .4, "olive_dark")
        sbox(slide, "slide stencil", side, BODY_HALF, BODY_HALF + .1, 20, 24, 40, 52, "label")
        # Bay racks on rows z2-z4: a shelf frame per bay that the fitted module stands in.
        for row in (2, 3, 4):
            z0 = INTERIOR_FRONT + row * CELL
            sbox(slide, "bay upright", side, 14.6, 15.7, FLOOR_Y + .6, CEILING_Y - 2.3, z0 + .2, z0 + 1.2, "rack")
            sbox(slide, "bay shelf", side, .6, 15.7, FLOOR_Y + 15.6, FLOOR_Y + 16.4, z0 + .4, z0 + 15.6, "rack")
            sbox(slide, "bay tie-down", side, 1, 15.6, FLOOR_Y + .6, FLOOR_Y + 1.2, z0 + 1, z0 + 2, "steel")
        sbox(slide, "bay upright", side, 14.6, 15.7, FLOOR_Y + .6, CEILING_Y - 2.3, INTERIOR_REAR - 2.6, INTERIOR_REAR - 1.6, "rack")
        # Bunk: a steel frame over rows z0-z1 that folds down from the outer wall (the real bed is placed on it).
        bunk = bone("bunk_" + name, slide, [15 * side, FLOOR_Y + .6, INTERIOR_FRONT + CELL])
        sbox(bunk, "bunk frame", side, .6, 15, FLOOR_Y + 1, FLOOR_Y + 3.2, INTERIOR_FRONT + 1.6, INTERIOR_FRONT + 2 * CELL - .4, "rack")
        sbox(bunk, "bunk webbing", side, 1.6, 14, FLOOR_Y + 3.2, FLOOR_Y + 3.6, INTERIOR_FRONT + 2.6, INTERIOR_FRONT + 2 * CELL - 1.4, "canvas")
        for z in (INTERIOR_FRONT + 2.4, INTERIOR_FRONT + 2 * CELL - 1.2):
            sbox(bunk, "bunk leg", side, 1, 2.4, FLOOR_Y + .6, FLOOR_Y + 1, z - .7, z + .7, "rack")
        # Locators in the slide-out's packed frame: the bay column is 0..16 px out (16..32 once deployed).
        # (Locator names are unique across the model: GeckoLib logs an error for each repeated one.)
        BY_NAME[slide]["locators"] = {f"bay_{name}_{row}": [8 * side, FLOOR_Y, INTERIOR_FRONT + (row + .5) * CELL] for row in (2, 3, 4)}
        BY_NAME[slide]["locators"][f"bunk_{name}"] = [8 * side, FLOOR_Y, INTERIOR_FRONT + CELL]

    # Awning on the left: a roller on the slide-out's wall top, canvas that unrolls outward and two legs.
    rx, ry = AWNING_ROLLER
    cylinder_z("slide_left", "awning roller", rx, ry, INTERIOR_FRONT + 2, INTERIOR_REAR - 2, 1.6, "olive_dark", 5)
    box("slide_left", "roller bracket", [BODY_HALF, ry - 2, INTERIOR_FRONT + 1], [rx + 1.6, ry + 2, INTERIOR_FRONT + 2], "frame")
    box("slide_left", "roller bracket", [BODY_HALF, ry - 2, INTERIOR_REAR - 2], [rx + 1.6, ry + 2, INTERIOR_REAR - 1], "frame")
    canvas = bone("awning", "slide_left", [rx, ry, 16], [0, 0, -7])
    box(canvas, "awning canvas", [rx, ry - .25, INTERIOR_FRONT + 2.4], [rx + AWNING_DEPTH, ry + .25, INTERIOR_REAR - 2.4], "canvas")
    box(canvas, "awning valance", [rx + AWNING_DEPTH - .4, ry - 3, INTERIOR_FRONT + 2.4], [rx + AWNING_DEPTH, ry - .25, INTERIOR_REAR - 2.4], "canvas")
    box(canvas, "awning bar", [rx + AWNING_DEPTH - 1, ry - 1, INTERIOR_FRONT + 2.2], [rx + AWNING_DEPTH, ry, INTERIOR_REAR - 2.2], "steel")
    box(canvas, "awning light strip", [rx + 4, ry - .6, 4], [rx + 6, ry - .25, 28], "led")
    for k, z in enumerate((INTERIOR_FRONT + 3, INTERIOR_REAR - 3)):
        leg = bone(f"awning_leg_{k}", canvas, [rx + AWNING_DEPTH - .5, ry - .5, z])
        box(leg, "awning leg", [rx + AWNING_DEPTH - 1, ry - .5 - 44, z - .5], [rx + AWNING_DEPTH, ry - .5, z + .5], "steel")
        box(leg, "leg foot", [rx + AWNING_DEPTH - 1.6, ry - .5 - 44.6, z - 1.1], [rx + AWNING_DEPTH + .6, ry - .5 - 44, z + 1.1], "rubber")

    # ---- Stabiliser jacks at the body's corners: a housing under the sill and a leg that extends to the ground.
    for name, x, z in JACKS:
        box("body", "jack housing", [x - 1.6, 4.4, z - 1.6], [x + 1.6, 13.6, z + 1.6], "olive_dark")
        box("body", "jack motor", [x - 1.2, 9.6, z + (1.6 if z < 0 else -3.2)], [x + 1.2, 12.6, z + (3.2 if z < 0 else -1.6)], "frame")
        leg = bone("jack_" + name, "body", [x, 13, z])
        box(leg, "jack leg", [x - 1, JACK_FOOT_Y + .8, z - 1], [x + 1, 12.8, z + 1], "chrome")
        box(leg, "jack foot", [x - 2.4, JACK_FOOT_Y, z - 2.4], [x + 2.4, JACK_FOOT_Y + .8, z + 2.4], "frame")
        box(leg, "foot tread", [x - 2.2, JACK_FOOT_Y - .2, z - 2.2], [x + 2.2, JACK_FOOT_Y, z + 2.2], "rubber")

    BY_NAME["cab"]["locators"] = {"driver_eye": EYE, "driver_seat": SEAT_DRIVER, "codriver_seat": SEAT_CODRIVER,
                                  "exhaust": EXHAUST_TIP}
    BY_NAME["generator"]["locators"] = {"generator_exhaust": GENERATOR_EXHAUST}


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

    if name in PAINT:
        # Field-worn matte paint: scuffs, chips to primer and settled dust towards the bottom.
        for _ in range(28):
            shade(rng.randrange(TILE), rng.randrange(TILE), rng.choice((-18, -12, 10)))
        for _ in range(6):
            x, y = rng.randrange(TILE), rng.randrange(TILE)
            for k in range(rng.randrange(2, 5)):
                shade(x + k, y, -12)
        for y in range(TILE - 6, TILE):
            for x in range(TILE):
                if rng.random() < .25:
                    shade(x, y, 8)
    if name in ("frame", "steel", "rack"):
        for _ in range(40):
            shade(rng.randrange(TILE), rng.randrange(TILE), rng.choice((-8, 6, 10)))
        for _ in range(8):
            px[rng.randrange(TILE), rng.randrange(TILE)] = (92, 64, 40, 255)
    if name == "tread":
        for y in range(TILE):
            for x in range(TILE):
                if (x // 3 + y // 5) % 2 == 0:
                    shade(x, y, 14)
    if name == "floor":
        for y in range(TILE):
            for x in range(TILE):
                if (x + 2 * y) % 6 == 0 and (x - y) % 4 < 2:
                    shade(x, y, 22)
    if name == "chrome":
        for x in range(TILE):
            for y in range(TILE):
                shade(x, y, int(18 * math.cos(x * math.pi / 8)))
    if name == "mirror":
        for y in range(TILE):
            t = y / (TILE - 1)
            for x in range(TILE):
                gleam = 12 if 8 <= x + y // 2 <= 10 else 0
                px[x, y] = (int(150 + 50 * (1 - t)) + gleam, int(176 + 30 * (1 - t)) + gleam, int(190 + 20 * (1 - t)) + gleam, 255)
    if name == "glass":
        for y in range(TILE):
            for x in range(TILE):
                if 6 <= x + y // 2 <= 8:
                    px[x, y] = (215, 230, 235, 110)
    if name == "grille":
        for y in range(TILE):
            for x in range(TILE):
                if y % 3 == 0:
                    px[x, y] = (60, 64, 62, 255)
    if name == "hazard":
        for y in range(TILE):
            for x in range(TILE):
                px[x, y] = (24, 24, 24, 255) if (x + y) % 10 < 5 else (226, 172, 32, 255)
    if name == "canvas":
        for y in range(TILE):
            for x in range(TILE):
                if (x + y) % 2 == 0:
                    shade(x, y, -6)
                if y % 8 == 0:
                    shade(x, y, -14)
    if name == "interior":
        for y in range(TILE):
            for x in range(TILE):
                if x % 16 == 0 or y % 16 == 0:
                    shade(x, y, -18)
    if name == "seat":
        for y in range(TILE):
            for x in range(TILE):
                if y % 7 == 0 or x % 9 == 0:
                    shade(x, y, 10)
    if name == "rust":
        for y in range(TILE):
            for x in range(TILE):
                shade(x, y, rng.choice((-14, -6, 0, 8, 14)))
    if name == "mud":
        for y in range(TILE):
            for x in range(TILE):
                shade(x, y, rng.choice((-18, -8, 0, 10)))
    if name in ("lamp", "amber", "tail", "blackout"):
        for y in range(TILE):
            for x in range(TILE):
                shade(x, y, 18 if (x // 4 + y // 4) % 2 == 0 else 0)
    if name == "led":
        # A warm frosted diffuser: brightest in the middle, falling off to an amber edge.
        for y in range(TILE):
            for x in range(TILE):
                d = ((x - 15.5) ** 2 + (y - 15.5) ** 2) ** .5 / 22
                px[x, y] = (255, max(0, int(232 - 70 * d)), max(0, int(190 - 120 * d)), 255)
    draw = ImageDraw.Draw(tile)
    if name == "label":
        draw.rectangle((0, 0, 29, 13), fill=(70, 78, 50, 255))
        draw.text((2, 1), "FSV-6", fill=(226, 226, 210, 255))
    if name == "console":
        draw.rectangle((1, 1, 28, 28), fill=(14, 22, 22, 255), outline=(60, 74, 72, 255))
        draw.rectangle((4, 4, 25, 15), fill=(26, 70, 54, 255))
        for j, colour in enumerate(((228, 180, 40), (90, 200, 110), (220, 70, 50))):
            draw.rectangle((5 + 7 * j, 19, 9 + 7 * j, 23), fill=(*colour, 255))
    if name == "gauge":
        draw.ellipse((2, 2, 27, 27), fill=(20, 22, 22, 255), outline=(170, 174, 170, 255))
        draw.arc((6, 6, 23, 23), 150, 390, fill=(220, 220, 214, 255))
        draw.line((15, 15, 22, 9), fill=(245, 120, 40, 255), width=2)
    return tile


# ---- Pose: the bone transforms for a deploy state, shared by the review renders and the validation.
STAGES = {"jacks": (0.0, 0.19), "slides": (0.19, 0.56), "doors": (0.56, 0.75), "mast_raise": (0.75, 0.86), "mast": (0.86, 1.0)}


def stage(progress, name):
    a, b = STAGES[name]
    t = min(1.0, max(0.0, (progress - a) / (b - a)))
    return t * t * (3 - 2 * t)


def pose(progress, lift=0.0, ground=0.0):
    """Translation (px), rotation (deg) and scale per bone for a deploy progress 0..1 (mirrors SupportRig)."""
    slides = stage(progress, "slides")
    doors = stage(progress, "doors")
    mast = stage(progress, "mast")
    raise_ = stage(progress, "mast_raise")
    jacks = stage(progress, "jacks")
    out = {}
    out["slide_left"] = {"t": [SLIDE_TRAVEL * slides, 0, 0]}
    out["slide_right"] = {"t": [-SLIDE_TRAVEL * slides, 0, 0]}
    out["rear_door"] = {"r": [DOOR_OPEN * doors, 0, 0]}
    out["awning"] = {"s": [max(.04, doors), 1, 1]}
    leg_swing = 90 * (1 - min(1, max(0, doors * 1.25 - .25)))
    for k in range(2):
        out[f"awning_leg_{k}"] = {"r": [0, 0, -leg_swing], "s": [1, (44 + lift * 16) / 44, 1]}
    out["mast"] = {"r": [90 * (1 - raise_), 0, 0]}
    for k in range(3):
        out[f"mast_{k + 1}"] = {"t": [0, MAST_STAGE * mast, 0]}
    out["beacon_mount"] = {"t": [0, BEACON_RAISE * mast, 0]}
    for side in ("left", "right"):
        out["bunk_" + side] = {"r": [0, 0, (-1 if side == "left" else 1) * 90 * (1 - mast)]}
    for name, *_ in JACKS:
        out["jack_" + name] = {"t": [0, -(JACK_FOOT_Y + lift * 16 - ground) * jacks, 0]}
    out["root"] = {"t": [0, lift * 16 * jacks, 0]}
    return out


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


def mat_scale(s):
    return [[s[0], 0, 0, 0], [0, s[1], 0, 0], [0, 0, s[2], 0], [0, 0, 0, 1]]


def about(pivot, rotation, scale=(1, 1, 1)):
    return mat_mul(mat_mul(mat_mul(mat_translate(pivot), mat_rotate(rotation)), mat_scale(scale)), mat_translate([-v for v in pivot]))


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
        m = mat_mul(mat_translate(extra.get("t", [0, 0, 0])), about(b["pivot"], rotation, extra.get("s", [1, 1, 1])))
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


def tyre_clashes(progress):
    """Body cubes a tyre's tread passes through in this pose (axle beams excepted: the wheels are on them)."""
    state = pose(progress)
    tyres, body = [], []
    for name, c, pts in posed_cubes(state):
        box_ = extent_points(pts)
        wheelish = "wheel" in name or "steer" in name
        if wheelish and c["_name"] == "tyre":
            tyres.append(box_)
        elif not wheelish and "axle" not in c["_name"] and "differential" not in c["_name"]:
            body.append((name, c["_name"], box_))
    found = set()
    for lo, hi in tyres:
        for name, label, (blo, bhi) in body:
            if all(min(hi[i], bhi[i]) - max(lo[i], blo[i]) > 0.01 for i in range(3)):
                found.add((name, label))
    return sorted(found)


def extent_points(points):
    return [min(p[i] for p in points) for i in range(3)], [max(p[i] for p in points) for i in range(3)]


# ---- Hitboxes: one per real mass in each pose, in source pixels.
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
    for bone_name, c, pts in posed_cubes(state, set(bones)):
        if names and not any(c["_name"].startswith(n) for n in names):
            continue
        if where and not where(c, pts):
            continue
        points.extend(pts)
    return extent_points(points)


def hitbox_parts(progress):
    state = pose(progress)
    left = lambda c, pts: sum(p[0] for p in pts) > 0
    right = lambda c, pts: not left(c, pts)
    wheel_bones = [b["name"] for b in BONES if b["name"].startswith(("wheel_", "finish_wheel_"))]
    parts = []
    for side in ("left", "right"):
        mine = [w for w in wheel_bones if w.endswith(side)]
        parts.append(("wheel_" + side, side + " front wheel", extent_of(state, [w for w in mine if "_front_" in w])))
        parts.append(("wheel_" + side, side + " tandem wheels", extent_of(state, [w for w in mine if "_front_" not in w])))
    parts += [
        ("main_engine", "cab and engine", extent_of(state, ["cab", "finish_cab"], ["cab", "front panel", "grille", "windscreen", "front bumper", "headlamp"])),
        ("body", "chassis, tanks, lockers and generator", extent_of(state, ["chassis", "finish_chassis", "generator", "finish_generator"],
                                                                     ["frame rail", "cross member", "fuel tank", "locker", "generator", "rear bumper", "axle", "differential"])),
        ("body", "van body", extent_of(state, ["body", "finish_body"], ["body sill", "front wall", "roof", "rear post", "floor plate"])),
    ]
    if progress >= 1.0:
        for side in ("left", "right"):
            parts.append(("module", side + " slide-out", extent_of(state, subtree("slide_" + side),
                                                                    ["slide floor", "outer wall", "slide roof", "end wall"])))
    else:
        parts.append(("module", "slide-outs, stowed", extent_of(state, subtree("slide_left") + subtree("slide_right"),
                                                                 ["slide floor", "outer wall", "slide roof"])))
    # Tread lugs and jack feet dip to the ground plane; boxes stand on it.
    return [(part, label, ([lo[0], max(0.0, lo[1]), lo[2]], hi)) for part, label, (lo, hi) in parts]


def obb_rows(parts):
    rows = []
    for part, label, (lo, hi) in parts:
        cx, cy, cz = [(lo[i] + hi[i]) / 32 for i in range(3)]
        hw, hh, hd = [(hi[i] - lo[i]) / 32 for i in range(3)]
        rows.append('    {"part": "%s", "x": %.4f, "y": %.4f, "z": %.4f, "half_width": %.4f, "half_height": %.4f, "half_depth": %.4f}'
                    % (part, cx, cy, -cz, hw, hh, hd))
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


def make_texture():
    atlas = Image.new("RGBA", ATLAS, (0, 0, 0, 0))
    rng = random.Random(6061)
    for i, (name, rgba) in enumerate(MATERIALS.items()):
        atlas.paste(paint_tile(name, rgba, rng), (i % 8 * TILE, i // 8 * TILE))
    return atlas


def separate_finishes():
    """Paint takes only the olive panels: every mixed bone hands its glass, steel, rubber and lamps to a finish_
    child bone, so the per-pixel paint mask never covers an olive tile."""
    for b in list(BONES):
        paint = [c for c in b["cubes"] if c["_material"] in PAINT]
        other = [c for c in b["cubes"] if c["_material"] not in PAINT]
        if paint and other:
            fixed = bone("finish_" + b["name"], b["name"], b["pivot"])
            BY_NAME[fixed]["cubes"] = other
            b["cubes"] = paint


def resolve_zfighting(bias=.04):
    """Coplanar overlapping faces of two different finishes flicker (z-fighting) as the camera moves: wheel rims on
    tyre sidewalls, end walls on outer walls, lamps on their housings. Within each bone (and its finish_ child, the same
    frame) push the smaller cube of each such pair out through the shared plane by a hair, as a decal is biased."""
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
    bones = result["minecraft:geometry"][0]["bones"]
    for b in bones:
        name = b["name"]
        if name.startswith(("wheel_", "finish_wheel_")):
            b["cubes"] = [c for c in b["cubes"] if c["size"][0] >= 2][: (10 if level == 1 else 4)]
        else:
            limit = 1.0 if level == 1 else 2.6
            b["cubes"] = [c for c in b["cubes"] if max(c["size"]) >= limit or ("_glow" in c)]
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
    """Preview clips for Blockbench. In game every articulation is driven from the vehicle's synced state."""
    clips = {"idle": {"loop": True, "animation_length": 1, "bones": {}}}
    channels = {}
    for i in range(41):
        t = i / 40 * 8
        progress = min(1.0, i / 32)
        for name, transform in pose(progress).items():
            if name == "root":
                continue
            entry = channels.setdefault(name, {})
            if "t" in transform:
                entry.setdefault("position", {})[str(t)] = transform["t"]
            if "r" in transform:
                entry.setdefault("rotation", {})[str(t)] = transform["r"]
            if "s" in transform:
                entry.setdefault("scale", {})[str(t)] = transform["s"]
    clips["animation.support.deploy_preview"] = {"loop": False, "animation_length": 8, "bones": channels}
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
        for name, position in b.get("locators", {}).items():
            identity = uid(b["name"] + "/locator/" + name)
            elements.append({"name": name, "uuid": identity, "type": "locator", "position": position, "rotation": [0, 0, 0], "export": True})
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
        clips.append({"uuid": uid(name), "name": name, "loop": "once", "length": animation["animation_length"], "animators": animators})
    return {"meta": {"format_version": "5.0", "model_format": "bedrock", "box_uv": False},
            "name": "support", "model_identifier": "jeg.truck", "visible_box": [10, 6, 1.5],
            "resolution": {"width": ATLAS[0], "height": ATLAS[1]}, "elements": elements, "groups": groups,
            "outliner": [outliner("root")], "animations": clips,
            "textures": [{"uuid": uid("texture"), "name": "truck.png", "id": "0", "mode": "bitmap", "internal": True,
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
    # The ramp's top edge reaches the ground exactly when the door is fully open.
    lowest = min(p[1] for _, _, pts in posed_cubes(pose(1.0), {"rear_door"}) for p in pts)
    assert abs(lowest) < .6, ("Ramp does not reach the ground", lowest)
    # Packed, nothing of a slide-out pokes out of the body; deployed, its floor is the interior's outer column.
    for progress, expect in ((0.0, BODY_HALF), (1.0, INTERIOR_X + 4)):
        lo, hi = extent_of(pose(progress), subtree("slide_left"), ["slide floor", "outer wall", "slide roof", "end wall"])
        assert abs(hi[0] - expect) < .1, ("slide-out extent", progress, hi[0])
    # The deployed interior is whole cells and the van floor is one block up.
    assert (INTERIOR_REAR - INTERIOR_FRONT) % CELL == 0 and (2 * INTERIOR_X) % CELL == 0 and FLOOR_Y == CELL
    # The driver's head clears the cab roof, and the eye is behind the windscreen.
    assert SEAT_DRIVER[1] + 29 <= 46, "Cab roof too low for the driver's head"
    # The tyres roll on the ground and touch nothing of the body, packed and deployed (not just "fit under the floor").
    assert WHEEL_RADIUS * 2 <= FLOOR_Y, "Tyres reach into the room"
    for progress in (0.0, 1.0):
        clashes = tyre_clashes(progress)
        assert not clashes, f"Tyres cut through the body at progress {progress}: {clashes[:6]}"
    assert AXLES[1][1] - WHEEL_RADIUS >= 12.0, "Middle tyres hit the lockers"
    assert AXLES[2][1] + WHEEL_RADIUS <= 47.6, "Rear tyres hit the mud flaps"
    assert AXLES[0][1] - WHEEL_RADIUS >= -53.0, "Front tyres hit the cab step"
    assert all(set(a["bones"]).issubset(names) for a in animations["animations"].values())
    all_ids = [e["uuid"] for e in project["elements"]] + [g["uuid"] for g in project["groups"]]
    assert len(set(all_ids)) == len(all_ids)
    count = sum(len(b["cubes"]) for b in BONES)
    print(f"Validated {len(BONES)} bones, {count} cuboids; UVs, hierarchy, ramp reach, slide-out travel, cell grid, "
          "head clearance and tyre clearance passed.")
    return count


def render_views(atlas, path_prefix):
    """Flat-shaded orthographic and three-quarter review renders of the packed and deployed poses."""
    colours = {name: rgba for name, rgba in MATERIALS.items()}
    views = {
        "side": (math.radians(90), 0.0),
        "front": (0.0, 0.0),
        "rear": (math.radians(180), 0.0),
        "three_quarter": (math.radians(140), math.radians(22)),
        "front_quarter": (math.radians(35), math.radians(18)),
        "top": (math.radians(90), math.radians(89)),
    }
    for label, progress in (("packed", 0.0), ("deployed", 1.0)):
        state = pose(progress)
        for view, (yaw, pitch) in views.items():
            image = Image.new("RGBA", (900, 520), (24, 30, 34, 255))
            draw = ImageDraw.Draw(image)
            cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)

            def project(p):
                # Yaw about Y, then tilt the camera down by the pitch; larger depth is nearer the camera.
                x = p[0] * cy - p[2] * sy
                z = p[0] * sy + p[2] * cy
                y = p[1] * cp + z * sp
                depth = z * cp - p[1] * sp
                return 450 - x * 5.2, 400 - y * 5.2, depth
            faces = []
            for _, c, pts in posed_cubes(state):
                rgba = colours[c["_material"]]
                for idx, shade in (((0, 1, 3, 2), .78), ((4, 6, 7, 5), .78), ((0, 4, 5, 1), .62), ((2, 3, 7, 6), 1.0),
                                   ((0, 2, 6, 4), .9), ((1, 5, 7, 3), .9)):
                    quad = [project(pts[i]) for i in idx]
                    faces.append((sum(q[2] for q in quad) / 4, [(q[0], q[1]) for q in quad],
                                  tuple(int(v * shade) for v in rgba[:3]) + (255 if rgba[3] > 200 else 150,)))
            faces.sort(key=lambda f: -f[0])  # farthest first
            draw.line((0, 420, 900, 420), fill=(60, 70, 74, 255))
            for _, poly, colour in faces:
                draw.polygon(poly, fill=colour)
            draw.text((12, 10), f"FSV-6  {label}  {view}", fill=(230, 230, 220, 255))
            image.save(f"{path_prefix}{label}-{view}.png")


def make_icon(path):
    """The assembling table's selection icon: a grey side view of the packed vehicle on a 256 x 64 transparent strip,
    like the other vehicles' icons."""
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
    box = image.getbbox()
    crop = image.crop(box)
    crop.thumbnail((width - 16, height - 8))
    icon = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    icon.paste(crop, ((width - crop.width) // 2, height - crop.height - 4), crop)
    icon.save(path)


def bake():
    """Every face its own texels laid with its material's tile (stretched for the plates and dials, repeated for the
    rest, as the tile atlas showed them), then the weathering pass; lamps, glass and screens put back as laid."""
    import sys
    sys.path.insert(0, str(ROOT / "design/superbwarfare-port"))
    sys.path.insert(0, str(ROOT / "design/superbwarfare-port/triage"))
    sys.path.insert(0, str(ROOT / "design/ground-vehicles"))
    import numpy as np
    import refit_lib as L
    rng = random.Random(6061)
    tiles = {name: paint_tile(name, rgba, rng) for name, rgba in MATERIALS.items()}
    repeated = {name: np.asarray(tile.crop((1, 1, 31, 31)).resize((30 * TEXELS, 30 * TEXELS), Image.NEAREST)).astype(int)
                for name, tile in tiles.items()}

    def fill(item, w, h):
        name = item["material"]
        if name in STRETCHED:
            source = tiles[name].crop((1, 1, 31, 15 if name == "label" else 31))
            return np.asarray(source.resize((w + 2, h + 2), Image.NEAREST)).astype(int)
        block = repeated[name]
        reps = ((h + 2) // block.shape[0] + 1, (w + 2) // block.shape[1] + 1, 1)
        return np.tile(block, reps)[:h + 2, :w + 2]

    uv, faces, base = L.bake_unique(BONES, MATERIALS, TEXELS, fill=fill)
    ATLAS[:] = [int(round(uv[0])), int(round(uv[1]))]
    return L, faces, base


def main():
    create_model()
    separate_finishes()
    print(f"Biased {resolve_zfighting()} coplanar faces against z-fighting.")
    L, faces, base = bake()
    texture_path = ASSETS / "textures/entity/vehicle/truck.png"
    geometry = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.jeg.truck", "texture_width": ATLAS[0], "texture_height": ATLAS[1],
                        "visible_bounds_width": 10, "visible_bounds_height": 7, "visible_bounds_offset": [0, 2, 0]},
        "bones": [{k: [{ck: cv for ck, cv in c.items() if not ck.startswith("_")} for c in v] if k == "cubes" else v
                   for k, v in b.items()} for b in BONES]}]}
    import detail_texture as D
    atlas, _, _ = D.detail_image(native_geometry(geometry), base, DETAIL)
    L.paste_faces(atlas, base, faces, KEPT)
    atlas.save(texture_path)
    # The glow layer: the lamps, screens and LEDs as laid; tail lamps glow dimly as tail lights (the brake lights,
    # AircraftLights BRAKE, brighten them when braking).
    glow = Image.new("RGBA", base.size, (0, 0, 0, 0))
    for item in faces:
        if item["material"] in GLOWING:
            x0, y0, x1, y1 = item["rect"]
            patch = base.crop((x0 - 1, y0 - 1, x1 + 1, y1 + 1))
            if item["material"] == "tail":
                patch = patch.point(lambda v: int(v * .45))
                patch.putalpha(255)
            glow.paste(patch, (x0 - 1, y0 - 1))
    glow.save(texture_path.with_name("truck_glow.png"))
    url = "data:image/png;base64," + base64.b64encode(texture_path.read_bytes()).decode()
    animations = make_animations()
    project = make_bbmodel(url, animations)
    count = validate(animations, project)
    geometry = native_geometry(geometry)
    write_json(ASSETS / "geckolib/models/entity/vehicle/truck.geo.json", geometry)
    for level in (1, 2):
        write_json(ASSETS / f"geckolib/models/entity/vehicle/lod/truck_lod{level}.geo.json", make_lod(geometry, level))
    write_json(ASSETS / "vehicle_paint/truck.json", {"unpainted_parts": unpainted_parts()})
    packed, deployed = hitbox_parts(0.0), hitbox_parts(1.0)
    write_vehicle_obb(packed, deployed)
    native_animations = copy.deepcopy(animations)
    for clip in native_animations["animations"].values():
        for channels in clip["bones"].values():
            for time, rotation in channels.get("rotation", {}).items():
                x, y, z = rotation
                channels["rotation"][time] = [-x, y, -z]
    write_json(ASSETS / "geckolib/animations/entity/vehicle/truck.animation.json", native_animations)
    write_json(HERE / "support.bbmodel", project)
    bounds = {label: [list(v) for v in extent_points([p for _, _, pts in posed_cubes(pose(pr)) for p in pts])]
              for label, pr in (("packed", 0.0), ("deployed", 1.0))}
    manifest = {"model": "jeg:entity/vehicle/truck", "units_per_block": 16, "front": "-Z in the source model",
                "floor_y": FLOOR_Y, "ceiling_y": CEILING_Y, "roof_top": ROOF_TOP, "interior_half_x": INTERIOR_X,
                "interior_front": INTERIOR_FRONT, "interior_rear": INTERIOR_REAR, "cell": CELL,
                "slide_travel": SLIDE_TRAVEL, "body_half": BODY_HALF, "body_front": BODY_FRONT, "body_rear": BODY_REAR,
                "wheel_radius": WHEEL_RADIUS, "wheel_x": WHEEL_X, "axles": AXLES,
                "door_hinge": DOOR_HINGE, "door_height": DOOR_HEIGHT, "door_open_degrees": DOOR_OPEN,
                "jacks": JACKS, "jack_foot_y": JACK_FOOT_Y, "jack_reach": JACK_REACH,
                "mast_base": MAST_BASE, "mast_length": MAST_LENGTH, "mast_stage": MAST_STAGE, "beacon_mount": BEACON_MOUNT, "beacon_raise": BEACON_RAISE,
                "awning_roller": AWNING_ROLLER, "awning_depth": AWNING_DEPTH, "lamp_hook": LAMP_HOOK,
                "windows_z": WINDOWS_Z, "window_y": [WINDOW_Y0, WINDOW_Y1],
                "seat_driver": SEAT_DRIVER, "seat_codriver": SEAT_CODRIVER, "driver_eye": EYE,
                "exhaust_tip": EXHAUST_TIP, "generator_exhaust": GENERATOR_EXHAUST, "stages": STAGES,
                "model_bounds": bounds,
                "hitbox": {label: [{"part": p, "covers": l, "min": lo, "max": hi} for p, l, (lo, hi) in parts]
                           for label, parts in (("packed", packed), ("deployed", deployed))},
                "revision": "01 - expansible van body, walk-in deploy", "bones": len(BONES), "cuboids": count}
    write_json(HERE / "rig.json", manifest)
    render_views(atlas, str(HERE / "review-"))
    make_icon(ASSETS / "textures/vehicle_icon/truck_icon.png")


if __name__ == "__main__":
    main()
