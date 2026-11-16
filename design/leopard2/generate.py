"""Author the Leopard 2A7's GeckoLib assets, rig manifest, hitboxes, track path and review images.

Run from any directory: python -I design/leopard2/generate.py
Requires Pillow and numpy. Writes the near/LOD geometry, texture atlas, paint mask, rig manifest, the vehicle data's
hitboxes and track block, a Blockbench project, review renders and the assembling table's icon.

A Leopard 2A7 (see BRIEF.md), modelled 1:1 (a block is a metre): a hull on seven road wheels a side with the idler in
front and the drive sprocket behind, side skirts, a turret with the 2A5-and-later wedge armour on its cheeks, a 120 mm
L/55 on a cradle that elevates and a tube that recoils, and a remote weapon station on the roof that turns and
elevates on its own. The track links are named for the shared track driver (guide section 5): the path is the convex
hull of the running gear, computed here. Source units are 16 per block, the tank faces -Z and +X is its left.
Helpers follow design/howitzer/generate.py.
"""
from __future__ import annotations

import base64
import copy
import json
import math
from pathlib import Path
import random
import sys
import uuid

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "design/superbwarfare-port"))
import refit_lib as L  # noqa: E402  (the shared track-path helpers)

ASSETS = ROOT / "src/main/resources/assets/jeg"
VEHICLE_JSON = ROOT / "src/main/resources/data/jeg/vehicles/leopard2.json"
ID = "leopard2"

MATERIALS = {
    "green": (72, 78, 58, 255),          # RAL 6031 bronze green
    "green_light": (88, 94, 70, 255),
    "green_dark": (56, 61, 45, 255),
    "steel": (70, 72, 70, 255),
    "bore": (18, 18, 18, 255),
    "rubber": (32, 32, 31, 255),
    "tread": (44, 43, 40, 255),
    "glass": (96, 136, 150, 150),
    "lens": (40, 70, 86, 255),
    "black": (26, 26, 25, 255),
    "dust": (104, 94, 76, 255),
    "brass": (150, 124, 60, 255),
}
PAINT = ("green", "green_light", "green_dark")
TILE = 32
ATLAS = (256, 64)

BONES: list[dict] = []
BY_NAME: dict[str, dict] = {}

# ---- Dimensions (model pixels, 16 to the metre). The Leopard 2A7: hull 7.7 m, 3.77 m over the skirts, roof 2.5 m.
TRACK_INNER, TRACK_OUTER = 19.6, 29.6         # the track's width, either side of the centre line
LINK_THICK = 1.4
ROAD_R, ROAD_Y = 5.6, 5.6 + LINK_THICK        # 700 mm road wheels on the track's inner face
ROAD_Z = [-42.0 + i * 14.4 for i in range(7)]
IDLER = (-56.0, 11.0, 5.0)                    # (z, y, radius): front
SPROCKET = (57.0, 11.5, 5.4)                  # rear, driven
ROLLERS = [(-31.0, 14.6, 1.8), (-9.0, 14.6, 1.8), (13.0, 14.6, 1.8), (35.0, 14.6, 1.8)]
LINKS = 60
BELLY_Y, SPONSON_Y, DECK_Y = 8.6, 19.2, 26.0
HULL_FRONT, HULL_REAR = -62.0, 62.0
TURRET_PIVOT = (0.0, DECK_Y, -4.0)
ROOF_Y = 40.0
TRUNNION = (0.0, 33.0, -28.0)
MUZZLE_Z = -122.0
TUBE_R = 1.6
ELEVATION = (-9.0, 20.0)
# Over the rear deck the gun can't depress: within REAR_ARC degrees of straight back it stays at or above REAR_MIN.
REAR_ARC, REAR_MIN = 70.0, 0.0
RECOIL_PX = 6.0
RWS_PIVOT = (12.0, ROOF_Y, 12.0)
RWS_TRUNNION = (12.0, ROOF_Y + 4.5, 12.0)
RWS_MUZZLE_Z = 12.0 - 17.0


def uid(name):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "jeg/leopard2/" + name))


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


def cube(parent, name, origin, size, material="green", rotation=None, pivot=None):
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
    BY_NAME[parent]["cubes"].append(entry)


def box(parent, name, a, b, material="green", rotation=None, pivot=None):
    origin = [min(a[i], b[i]) for i in range(3)]
    size = [abs(b[i] - a[i]) for i in range(3)]
    cube(parent, name, origin, size, material, rotation, pivot)


def plate(parent, name, x0, x1, p0, p1, thickness, material="green"):
    """A flat plate across X whose top face runs from p0 to p1 in the side view ((z, y) points): a sloped armour plate
    or a belt segment. Built along +Z from p0 and turned about X (right-handed) onto p1."""
    dz, dy = p1[0] - p0[0], p1[1] - p0[1]
    length = math.hypot(dz, dy)
    angle = math.degrees(math.atan2(-dy, dz))
    cube(parent, name, [x0, p0[1] - thickness, p0[0]], [x1 - x0, thickness, length], material,
         rotation=[angle, 0, 0], pivot=[x0, p0[1], p0[0]])


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
    """Horizontal round bar along Z (the gun tube, smoke dischargers)."""
    step = 2 * radius / layers
    for i in range(layers):
        offset = -radius + (i + .5) * step
        half = math.sqrt(max(.001, radius * radius - offset * offset))
        cube(parent, name, [x - half, y - radius + i * step, z0], [2 * half, step + .02, z1 - z0], material)


# ---- Tracks: the links' path (the shared driver's) from the running gear.

def gear_circles():
    circles = [(IDLER[0], IDLER[1], IDLER[2])]
    circles += [(z, ROAD_Y, ROAD_R) for z in ROAD_Z]
    circles += [(SPROCKET[0], SPROCKET[1], SPROCKET[2])]
    circles += list(ROLLERS)
    return circles


PATH = L.track_path(gear_circles(), LINK_THICK / 2)
PATH_LENGTH = L.path_length(PATH)


def top_run_y(z):
    """The top run's centre height over z (the highest path point above it)."""
    best = None
    for (z0, y0), (z1, y1) in zip(PATH, PATH[1:] + PATH[:1]):
        if min(z0, z1) <= z <= max(z0, z1) and z0 != z1:
            y = y0 + (y1 - y0) * (z - z0) / (z1 - z0)
            best = y if best is None else max(best, y)
    return best


LINK_REST = (0.0, top_run_y(0.0))             # (z, y): the links are modelled lying here on the top run
LINK_REST_S = L.nearest_s(PATH, LINK_REST)[0]


def create_model():
    bone("root", None)

    # ---- Hull: lower hull between the tracks, sponsons over them, glacis, deck, rear plate.
    bone("hull", "root")
    box("hull", "lower hull", [-TRACK_INNER + .6, BELLY_Y, -54.0], [TRACK_INNER - .6, SPONSON_Y, 58.0], "green_dark")
    box("hull", "upper hull", [-TRACK_OUTER - .2, SPONSON_Y, -48.0], [TRACK_OUTER + .2, DECK_Y, HULL_REAR], "green")
    # The upper glacis lies almost flat from the deck to the nose; the lower glacis falls steeply to the belly.
    plate("hull", "upper glacis", -TRACK_OUTER - .2, TRACK_OUTER + .2, (HULL_FRONT, 20.0), (-48.0, DECK_Y), 4.0)
    plate("hull", "lower glacis", -TRACK_INNER + .6, TRACK_INNER - .6, (-56.0, BELLY_Y + .2), (HULL_FRONT + .5, 20.0), 3.0, "green_dark")
    box("hull", "rear plate", [-TRACK_OUTER, 12.0, HULL_REAR - 2.0], [TRACK_OUTER, DECK_Y, HULL_REAR], "green_dark")
    box("hull", "deck", [-TRACK_OUTER + 1.0, DECK_Y, -46.0], [TRACK_OUTER - 1.0, DECK_Y + .3, HULL_REAR - 1.0], "green_light")
    # Engine deck: louvres over the rear third.
    for i in range(6):
        z0 = 22.0 + i * 6.2
        box("hull", "engine louvre", [-20.0, DECK_Y + .3, z0], [20.0, DECK_Y + .8, z0 + 4.2], "green_dark")
    box("hull", "exhaust grille left", [21.0, 18.0, HULL_REAR], [28.0, 24.0, HULL_REAR + .4], "black")
    box("hull", "exhaust grille right", [-28.0, 18.0, HULL_REAR], [-21.0, 24.0, HULL_REAR + .4], "black")
    for side in (1, -1):
        box("hull", "tow hook", [side * 14.0 - 1.0, 13.0, HULL_REAR], [side * 14.0 + 1.0, 15.0, HULL_REAR + 1.6], "steel")
        box("hull", "tow hook", [side * 14.0 - 1.0, 10.0, -57.5], [side * 14.0 + 1.0, 12.0, -55.0], "steel")
        # Headlights sit low on the glacis, under the gun's sweep at full depression.
        box("hull", "headlight", [side * 24.0 - 1.6, 21.0, -58.5], [side * 24.0 + 1.6, 23.6, -56.0], "steel")
        box("hull", "headlight lens", [side * 24.0 - 1.2, 21.4, -58.6], [side * 24.0 + 1.2, 23.2, -58.5], "glass")
        box("hull", "tail light", [side * 26.0 - 1.0, 22.0, HULL_REAR + .4], [side * 26.0 + 1.0, 23.4, HULL_REAR + .8], "lens")
        box("hull", "rear stowage", [side * 6.0 - 4.0, DECK_Y + .3, HULL_REAR - 6.0], [side * 6.0 + 4.0, DECK_Y + 2.6, HULL_REAR - 1.2], "green_dark")
    # The driver sits front right, his hatch beside the turret's front.
    bone("driver_hatch", "hull", (-12.0, DECK_Y + .3, -40.0))
    box("driver_hatch", "driver hatch", [-17.0, DECK_Y + .3, -44.0], [-7.0, DECK_Y + 1.2, -36.0], "green_light")
    for x in (-15.0, -12.0, -9.0):
        box("driver_hatch", "driver periscope", [x - 1.0, DECK_Y + .3, -45.2], [x + 1.0, DECK_Y + 1.6, -44.0], "lens")

    # ---- Side skirts: heavy armour at the front, lighter plates behind, covering the top run.
    for side, label in ((1, "left"), (-1, "right")):
        name = bone("skirt_" + label, "hull")
        x_in, x_out = side * (TRACK_OUTER + .3), side * (TRACK_OUTER + 1.3)
        box(name, "skirt", [min(x_in, x_out), 10.0, -40.0], [max(x_in, x_out), SPONSON_Y + 1.0, 58.0], "green")
        x_heavy = side * (TRACK_OUTER + 1.8)
        box(name, "heavy skirt", [min(x_in, x_heavy), 9.0, -58.0], [max(x_in, x_heavy), SPONSON_Y + 1.5, -40.0], "green")
        box(name, "skirt rubber flap", [min(x_in, x_out), 8.0, -40.0], [max(x_in, x_out), 10.0, 58.0], "rubber")
        for z in range(-34, 58, 12):
            box(name, "skirt bolt", [min(x_out, x_out + side * .3), 18.0, z - .5], [max(x_out, x_out + side * .3), 19.0, z + .5], "steel")

    # ---- Running gear: each wheel turns about its own centre; spokes and bolts show the turn.
    for side, label in ((1, "left"), (-1, "right")):
        cx = side * (TRACK_INNER + TRACK_OUTER) / 2
        for i, z in enumerate(ROAD_Z, start=1):
            name = bone(f"road_wheel_{label}_{i}", "hull", (cx, ROAD_Y, z))
            for dx in (-2.6, 2.6):
                cylinder_x(name, "tyre", (cx + dx, ROAD_Y, z), 3.4, ROAD_R, "rubber", layers=8)
                cylinder_x(name, "rim", (cx + dx + (.4 if dx * side > 0 else -.4), ROAD_Y, z), 3.0, ROAD_R - 1.4, "green_dark", layers=6)
            face = cx + side * 4.6
            for k in range(6):
                a = 2 * math.pi * k / 6
                y, zz = ROAD_Y + 2.6 * math.sin(a), z + 2.6 * math.cos(a)
                box(name, "wheel bolt", [min(face, face + side * .5), y - .45, zz - .45], [max(face, face + side * .5), y + .45, zz + .45], "steel")
            box(name, "hub", [min(face, face + side * .7), ROAD_Y - 1.2, z - 1.2], [max(face, face + side * .7), ROAD_Y + 1.2, z + 1.2], "steel")
        zi, yi, ri = IDLER
        name = bone("idler_" + label, "hull", (cx, yi, zi))
        for dx in (-2.6, 2.6):
            cylinder_x(name, "idler", (cx + dx, yi, zi), 3.0, ri, "steel", layers=8)
        for k in range(4):
            a = math.pi * k / 4
            box(name, "idler spoke", [cx - 4.2, yi - .5, zi - ri + .6], [cx + 4.2, yi + .5, zi + ri - .6], "green_dark",
                rotation=[math.degrees(a), 0, 0], pivot=[cx, yi, zi])
        zs, ys, rs = SPROCKET
        name = bone("sprocket_" + label, "hull", (cx, ys, zs))
        for dx in (-2.4, 2.4):
            cylinder_x(name, "sprocket ring", (cx + dx, ys, zs), 1.6, rs, "steel", layers=8)
        cylinder_x(name, "sprocket hub", (cx, ys, zs), 4.0, 2.4, "green_dark", layers=4)
        for k in range(10):
            a = 2 * math.pi * k / 10
            y, zz = ys + (rs + .3) * math.sin(a), zs + (rs + .3) * math.cos(a)
            for dx in (-2.4, 2.4):
                box(name, "sprocket tooth", [cx + dx - .8, y - .5, zz - .5], [cx + dx + .8, y + .5, zz + .5], "steel")
        for i, (zr, yr, rr) in enumerate(ROLLERS, start=1):
            name = bone(f"return_roller_{label}_{i}", "hull", (cx, yr, zr))
            cylinder_x(name, "return roller", (cx, yr, zr), 4.0, rr, "steel", layers=4)
            box(name, "roller cap", [cx - 2.4, yr - .5, zr - .5], [cx + 2.4, yr + .5, zr + .5], "green_dark")

        # ---- The track: a belt along the path, and the links (moved by the shared driver).
        belt = bone(f"track_{label}_belt", "hull")
        coarse = L.track_path(gear_circles(), LINK_THICK / 2 - .45, step_degrees=15.0)
        for (z0, y0), (z1, y1) in zip(coarse, coarse[1:] + coarse[:1]):
            # Going round counter-clockwise, a plate's thickness falls on its right: outward, inside the links' span and
            # clear of the wheels.
            plate(belt, "belt", cx - 4.4, cx + 4.4, (z0, y0), (z1, y1), .8, "tread")
        group = bone(f"track_{label}", "hull")
        zr, yr = LINK_REST
        for i in range(LINKS):
            link = bone(f"track_link_{label}_{i}", group, (cx, yr, zr))
            turn = bone(f"track_link_{label}_{i}_turn", link, (cx, yr, zr))
            box(turn, "link", [cx - 5.0, yr - LINK_THICK / 2, zr - 1.9], [cx + 5.0, yr + LINK_THICK / 2, zr + 1.9], "tread")
            box(turn, "link grouser", [cx - 5.0, yr + LINK_THICK / 2, zr - .5], [cx + 5.0, yr + LINK_THICK / 2 + .5, zr + .5], "steel")
            box(turn, "guide horn", [cx - .6, yr - LINK_THICK / 2 - 1.4, zr - .8], [cx + .6, yr - LINK_THICK / 2, zr + .8], "steel")

    # ---- Turret: traverses about the ring. Body, wedge armour on the cheeks, bustle, roof fittings.
    bone("turret", "root", TURRET_PIVOT)
    box("turret", "turret body", [-25.0, DECK_Y + .5, -26.0], [25.0, ROOF_Y, 30.0], "green")
    box("turret", "turret ring cover", [-20.0, DECK_Y, -20.0], [20.0, DECK_Y + .5, 16.0], "green_dark")
    for side in (1, -1):
        # The wedges sweep back from beside the mantlet to the cheek's outer edge, stepped across X.
        # Seen from the side their faces lean back as they rise: three tiers, each set further back than the one below.
        for k in range(10):
            xa, xb = 7.0 + 2.0 * k, 9.0 + 2.0 * k
            front = -46.0 + 2.0 * k
            top = ROOF_Y - 1.5 - .25 * k
            tiers = [(DECK_Y + 1.5, 31.5, 0.0), (31.5, 35.5, 3.0), (35.5, top, 6.5)]
            for y0, y1, back in tiers:
                box("turret", "wedge", [min(side * xa, side * xb), y0, front + back], [max(side * xa, side * xb), y1, -25.9], "green")
        box("turret", "wedge edge", [min(side * 7.0, side * 8.0), DECK_Y + 1.5, -46.4], [max(side * 7.0, side * 8.0), ROOF_Y - 1.5, -46.0], "green_dark")
        # Smoke dischargers on the turret's flanks, behind the wedges, four a side.
        for k in range(4):
            y = 34.5 + (k % 2) * 2.4
            z = 8.0 + (k // 2) * 3.0
            cylinder_z("turret", "smoke discharger", side * 26.6, y, z - 2.6, z, .9, "black", layers=3)
        box("turret", "discharger bracket", [min(side * 25.0, side * 27.8), 32.6, 5.0], [max(side * 25.0, side * 27.8), 33.2, 13.0], "steel")
        # Stowage baskets on the bustle's flanks.
        box("turret", "bustle basket", [min(side * 22.5, side * 26.0), 30.0, 30.0], [max(side * 22.5, side * 26.0), 38.0, 44.0], "green_dark")
    box("turret", "bustle", [-22.5, 28.0, 30.0], [22.5, ROOF_Y - .5, 44.0], "green")
    box("turret", "bustle rear basket", [-20.0, 30.0, 44.0], [20.0, 37.0, 47.0], "green_dark")
    box("turret", "roof", [-24.0, ROOF_Y, -24.0], [24.0, ROOF_Y + .3, 42.0], "green_light")
    # The gunner's main sight (EMES 15) on the roof's front right, its window facing forward.
    bone("sight", "turret", (-14.0, ROOF_Y, -22.0))
    box("sight", "gunner sight", [-19.0, ROOF_Y, -26.0], [-9.0, ROOF_Y + 4.0, -16.0], "green")
    box("sight", "gunner sight window", [-18.0, ROOF_Y + .8, -26.1], [-10.0, ROOF_Y + 3.2, -26.0], "lens")
    # The commander's periscope (PERI-R17) on the left, standing proud, and his hatch behind it.
    bone("commander_sight", "turret", (14.0, ROOF_Y, -10.0))
    cylinder_y("commander_sight", "periscope mast", 14.0, -10.0, ROOF_Y, ROOF_Y + 4.0, 2.2, "green_dark")
    box("commander_sight", "periscope head", [11.0, ROOF_Y + 4.0, -13.0], [17.0, ROOF_Y + 8.0, -7.0], "green")
    box("commander_sight", "periscope window", [11.6, ROOF_Y + 5.0, -13.1], [16.4, ROOF_Y + 7.2, -13.0], "lens")
    box("turret", "commander hatch", [8.0, ROOF_Y + .3, 2.0], [18.0, ROOF_Y + 1.2, 10.0], "green_light")
    box("turret", "loader hatch", [-18.0, ROOF_Y + .3, 2.0], [-8.0, ROOF_Y + 1.2, 10.0], "green_light")
    for x in (-22.0, -19.0):
        box("turret", "antenna base", [x - .8, ROOF_Y + .3, 38.0], [x + .8, ROOF_Y + 2.0, 39.6], "black")
        box("turret", "antenna", [x - .2, ROOF_Y + 2.0, 38.6], [x + .2, ROOF_Y + 30.0, 39.0], "black")
    box("turret", "wind sensor", [3.0, ROOF_Y + .3, 36.0], [4.0, ROOF_Y + 5.0, 37.0], "black")

    # ---- The gun: cradle and mantlet elevate about the trunnions; the tube recoils in the cradle.
    bone("barrel", "turret", TRUNNION)
    box("barrel", "mantlet", [-6.5, 27.6, -33.0], [6.5, 38.4, -25.0], "green")
    box("barrel", "mantlet face", [-5.5, 28.6, -33.4], [5.5, 37.4, -33.0], "green_dark")
    cylinder_z("barrel", "coax barrel", -4.0, 30.6, -36.0, -33.0, .5, "black", layers=3)
    bone("gun_tube", "barrel", TRUNNION)
    cylinder_z("gun_tube", "gun tube", 0.0, TRUNNION[1], MUZZLE_Z, -33.0, TUBE_R, "green", layers=6)
    cylinder_z("gun_tube", "thermal sleeve band", 0.0, TRUNNION[1], -56.0, -54.0, TUBE_R + .35, "green_dark", layers=6)
    cylinder_z("gun_tube", "fume extractor", 0.0, TRUNNION[1], -80.0, -68.0, TUBE_R + .8, "green", layers=6)
    cylinder_z("gun_tube", "thermal sleeve band", 0.0, TRUNNION[1], -100.0, -98.0, TUBE_R + .35, "green_dark", layers=6)
    cylinder_z("gun_tube", "muzzle", 0.0, TRUNNION[1], MUZZLE_Z, MUZZLE_Z + 3.0, TUBE_R + .25, "steel", layers=6)
    box("gun_tube", "bore", [-.9, TRUNNION[1] - .9, MUZZLE_Z - .05], [.9, TRUNNION[1] + .9, MUZZLE_Z], "bore")
    box("gun_tube", "muzzle reference sensor", [-.6, TRUNNION[1] + TUBE_R + .25, MUZZLE_Z + 3.0], [.6, TRUNNION[1] + TUBE_R + 1.3, MUZZLE_Z + 5.0], "black")

    # ---- The remote weapon station (FLW 200) on the roof: turns on its ring, elevates the 12.7 mm and its sensor.
    bone("rws_mount", "turret", RWS_PIVOT)
    cylinder_y("rws_mount", "rws ring", RWS_PIVOT[0], RWS_PIVOT[2], ROOF_Y + .3, ROOF_Y + 1.3, 3.0, "green_dark")
    box("rws_mount", "rws pedestal", [RWS_PIVOT[0] - 1.6, ROOF_Y + 1.3, RWS_PIVOT[2] - 1.6], [RWS_PIVOT[0] + 1.6, ROOF_Y + 4.0, RWS_PIVOT[2] + 1.6], "green")
    box("rws_mount", "rws cradle arm", [RWS_PIVOT[0] - 3.4, ROOF_Y + 3.2, RWS_PIVOT[2] - 1.4], [RWS_PIVOT[0] - 2.4, ROOF_Y + 6.0, RWS_PIVOT[2] + 1.4], "green")
    bone("rws", "rws_mount", RWS_TRUNNION)
    x, y, z = RWS_TRUNNION
    box("rws", "rws gun", [x - 1.1, y - 1.0, z - 4.0], [x + 1.1, y + 1.2, z + 4.0], "steel")
    cylinder_z("rws", "rws barrel", x, y, RWS_MUZZLE_Z, z - 4.0, .45, "black", layers=3)
    box("rws", "rws flash hider", [x - .6, y - .6, RWS_MUZZLE_Z - 1.0], [x + .6, y + .6, RWS_MUZZLE_Z], "black")
    box("rws", "rws ammo box", [x + 1.1, y - 2.0, z - 2.0], [x + 3.2, y + .8, z + 2.0], "green")
    box("rws", "rws sensor", [x - 4.6, y - .6, z - 3.0], [x - 2.4, y + 2.4, z + 1.0], "green")
    box("rws", "rws sensor window", [x - 4.3, y + .2, z - 3.1], [x - 2.7, y + 1.8, z - 3.0], "lens")


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
        # Field-worn matte paint: scuffs, a few chips to bare metal, dust settled low.
        for _ in range(30):
            shade(rng.randrange(TILE), rng.randrange(TILE), rng.choice((-14, -9, 8)))
        for _ in range(4):
            x, y = rng.randrange(TILE), rng.randrange(TILE)
            px[x, y] = (84, 82, 76, 255)
        for y in range(TILE - 8, TILE):
            for x in range(TILE):
                if rng.random() < .3:
                    shade(x, y, 7)
    if name in ("steel", "dust"):
        for _ in range(48):
            shade(rng.randrange(TILE), rng.randrange(TILE), rng.choice((-10, 6, 12)))
    if name == "tread":
        for y in range(TILE):
            for x in range(TILE):
                if (x // 3 + y // 4) % 2 == 0:
                    shade(x, y, 12)
    if name == "glass":
        for y in range(TILE):
            for x in range(TILE):
                if 6 <= x + y // 2 <= 8:
                    px[x, y] = (205, 222, 228, 110)
    if name == "lens":
        for y in range(TILE):
            for x in range(TILE):
                if 4 <= x + y // 2 <= 6:
                    shade(x, y, 40)
    return tile


# ---- Pose: the bone transforms for a state, shared by the renders, the hitboxes and the validation.

def pose(elevation=0.0, yaw=0.0, recoil=0.0, rws_yaw=0.0, rws_pitch=0.0, travel=0.0):
    """Rotation (deg, right-handed) and translation (px) per bone. travel runs the links along the path (px)."""
    state = {
        "turret": {"r": [0, yaw, 0]},
        "barrel": {"r": [elevation, 0, 0]},
        "gun_tube": {"t": [0, 0, recoil]},
        "rws_mount": {"r": [0, rws_yaw, 0]},
        "rws": {"r": [rws_pitch, 0, 0]},
    }
    for label in ("left", "right"):
        for i in range(LINKS):
            (z, y), tangent = L.path_at(PATH, LINK_REST_S + travel + i * PATH_LENGTH / LINKS)
            state[f"track_link_{label}_{i}"] = {"t": [0, y - LINK_REST[1], z - LINK_REST[0]]}
            # The driver's turn is in Bedrock's (file) sense; authoring here is right-handed, the negative of it.
            state[f"track_link_{label}_{i}_turn"] = {"r": [-L.link_turn(tangent), 0, 0]}
    return state


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


def extent_of(state, bones, names=None, exclude=()):
    points = []
    for _, c, pts in posed_cubes(state, set(bones)):
        if names and not any(c["_name"].startswith(n) for n in names):
            continue
        if any(c["_name"].startswith(n) for n in exclude):
            continue
        points.extend(pts)
    return extent_points(points)


# ---- Hitboxes at rest (turret straight ahead, gun level); boxes on the turret or the gun follow them.

def hitbox_parts():
    state = pose()
    hull_bones = [b for b in subtree("hull") if not b.startswith(("track_", "road_wheel_", "idler_", "sprocket_", "return_roller_", "skirt_"))]
    hull = extent_of(state, hull_bones, exclude=("tow hook", "exhaust", "tail light", "headlight"))
    parts = [("body", "hull", hull, None)]
    for side, label in ((1, "left"), (-1, "right")):
        gear = [b for b in subtree("hull") if b.endswith(label) or f"_{label}_" in b]
        parts.append((f"wheel_{label}", f"{label} track, running gear and skirt", extent_of(state, gear), None))
    # The engine and transmission fill the hull behind the turret.
    parts.append(("main_engine", "engine compartment", ([-TRACK_INNER, BELLY_Y, 20.0], [TRACK_INNER, DECK_Y, HULL_REAR - 2.0]), None))
    turret_bones = [b for b in subtree("turret") if b not in subtree("barrel") and b not in subtree("rws_mount")]
    house = extent_of(state, turret_bones, exclude=("antenna", "wind sensor", "periscope"))
    parts.append(("turret", "turret", house, "turret"))
    gun = extent_of(state, subtree("gun_tube"))
    gun = ([gun[0][0], gun[0][1], gun[0][2]], [gun[1][0], gun[1][1], min(gun[1][2], house[0][2])])
    parts.append(("turret", "gun, outside the turret", gun, "barrel"))
    return parts


def obb_rows(parts):
    rows = []
    for part, label, (lo, hi), follows in parts:
        lo = [lo[0], max(0.0, lo[1]), lo[2]]
        cx, cy, cz = [(lo[i] + hi[i]) / 32 for i in range(3)]
        hw, hh, hd = [(hi[i] - lo[i]) / 32 for i in range(3)]
        tail = f', "follows": "{follows}"' if follows else ""
        rows.append('    {"part": "%s", "x": %.4f, "y": %.4f, "z": %.4f, "half_width": %.4f, "half_height": %.4f, "half_depth": %.4f%s}'
                    % (part, cx, cy, -cz + 0.0, hw, hh, hd, tail))
    return rows


def write_vehicle_data(parts, tracks):
    """Replace the obb list and the tracks block in the vehicle data (data space: blocks, +Z forward)."""
    text = VEHICLE_JSON.read_text(encoding="utf-8")
    start = text.index('  "obb": [')
    end = text.index("  ],", start) + len("  ],")
    text = text[:start] + '  "obb": [\n' + ",\n".join(obb_rows(parts)) + "\n  ]," + text[end:]
    start = text.index('  "tracks": ')
    end = text.index("\n", text.index('"wheels"', start)) + 1
    text = text[:start] + tracks + text[end:]
    VEHICLE_JSON.write_text(text, encoding="utf-8")


def make_texture():
    atlas = Image.new("RGBA", ATLAS, (0, 0, 0, 0))
    rng = random.Random(2007)
    for i, (name, rgba) in enumerate(MATERIALS.items()):
        atlas.paste(paint_tile(name, rgba, rng), (i % 8 * TILE, i // 8 * TILE))
    return atlas


def separate_finishes():
    """Paint takes only the green panels: a mixed bone hands its steel, rubber and glass to a finish_ child bone. The
    track links keep their names (the driver finds them by name), so their bones are left whole."""
    for b in list(BONES):
        if b["name"].startswith("track_link_"):
            continue
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
        if b["name"].startswith("track_link_"):
            # Far off the links still run (the driver finds them by name): keep the plate, drop the small parts.
            b["cubes"] = b["cubes"][:1]
            continue
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
    """The rest clip ShellGunEntity plays. In game every articulation is driven from synced state."""
    return {"format_version": "1.8.0", "animations": {f"animation.{ID}.rest": {"loop": True, "bones": {}}}}


def make_bbmodel(atlas_url):
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
    return {"meta": {"format_version": "5.0", "model_format": "bedrock", "box_uv": False},
            "name": ID, "model_identifier": "jeg." + ID, "visible_box": [12, 5, 2],
            "resolution": {"width": ATLAS[0], "height": ATLAS[1]}, "elements": elements, "groups": groups,
            "outliner": [outliner("root")], "animations": [],
            "textures": [{"uuid": uid("texture"), "name": ID + ".png", "id": "0", "mode": "bitmap", "internal": True,
                          "source": atlas_url, "width": ATLAS[0], "height": ATLAS[1], "uv_width": ATLAS[0], "uv_height": ATLAS[1]}]}


def lowest_of(state, bones, ignore=()):
    return min(p[1] for _, c, pts in posed_cubes(state, set(bones)) if not c["_name"].startswith(ignore) for p in pts)


def validate(project):
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
    # The tracks stand on the ground: the bottom run's links touch it, at any travel.
    for travel in (0.0, 1.3, 2.7):
        low = lowest_of(pose(travel=travel), [n for n in names if n.startswith("track_link_")], ignore=("guide horn",))
        assert -.6 < low < .4, ("Track links must stand on the ground", travel, low)
    # Nothing but the tracks reaches the ground: belly, skirts and wheels ride clear of it.
    clear = lowest_of(pose(), [n for n in names if not n.startswith(("track_", "finish_track_"))])
    assert clear > .5, ("Something other than the tracks drags", clear)
    # The road wheels ride on the track: their tyres just above the links' inner face.
    wheel_low = lowest_of(pose(), [n for n in names if n.startswith(("road_wheel_", "finish_road_wheel_"))])
    assert abs(wheel_low - LINK_THICK) < .1, ("Road wheels must sit on the track", wheel_low)
    # At every elevation and traverse the tube clears the hull: its top from the hull's cubes (the glacis plates by their
    # slope), the skirts and fittings included.
    hull_boxes = [extent_points(pts) for name, c, pts in posed_cubes(pose(), set(subtree("hull")))
                  if "rotation" not in c and not name.startswith(("track_", "road_wheel_", "finish_road_wheel_", "idler_", "sprocket_", "return_roller_"))]

    def hull_top(x, z):
        top = -1.0
        if HULL_FRONT <= z <= -48.0 and abs(x) <= TRACK_OUTER + .2:
            top = 20.0 + (z - HULL_FRONT) * (DECK_Y - 20.0) / (-48.0 - HULL_FRONT)
        for lo, hi in hull_boxes:
            if lo[0] <= x <= hi[0] and lo[2] <= z <= hi[2]:
                top = max(top, hi[1])
        return top
    for elevation in range(int(ELEVATION[0]), int(ELEVATION[1]) + 1, 1):
        for yaw in range(0, 181, 10):
            if 180.0 - yaw <= REAR_ARC and elevation < REAR_MIN:
                continue
            state = pose(elevation, float(yaw))
            tube = [p for _, c, pts in posed_cubes(state, set(subtree("gun_tube"))) for p in pts]
            for p in tube:
                assert p[1] > hull_top(p[0], p[2]) + .3, ("Gun strikes the hull", elevation, yaw, p, hull_top(p[0], p[2]))
    all_ids = [e["uuid"] for e in project["elements"]] + [g["uuid"] for g in project["groups"]]
    assert len(set(all_ids)) == len(all_ids)
    count = sum(len(b["cubes"]) for b in BONES)
    print(f"Validated {len(BONES)} bones, {count} cuboids; UVs, hierarchy, ground contact, wheels on the track and gun "
          "clearance over the elevation and traverse range passed.")
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
    poses = {"rest": pose(), "laid": pose(12.0, 30.0, 0.0, 40.0, 10.0, 2.0), "depressed": pose(ELEVATION[0], 0.0)}
    for label, state in poses.items():
        for view, (yaw, pitch) in views.items():
            if label != "rest" and view not in ("side", "three_quarter"):
                continue
            image = Image.new("RGBA", (1100, 560), (24, 30, 34, 255))
            draw = ImageDraw.Draw(image)
            cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)

            def project(p):
                x = p[0] * cy - p[2] * sy
                z = p[0] * sy + p[2] * cy
                y = p[1] * cp + z * sp
                depth = z * cp - p[1] * sp
                return 550 - x * 4.0, 420 - y * 4.0, depth
            faces = []
            for _, c, pts in posed_cubes(state):
                rgba = MATERIALS[c["_material"]]
                for idx, shade in (((0, 1, 3, 2), .78), ((4, 6, 7, 5), .78), ((0, 4, 5, 1), .62), ((2, 3, 7, 6), 1.0),
                                   ((0, 2, 6, 4), .9), ((1, 5, 7, 3), .9)):
                    quad = [project(pts[i]) for i in idx]
                    faces.append((sum(q[2] for q in quad) / 4, [(q[0], q[1]) for q in quad],
                                  tuple(int(v * shade) for v in rgba[:3]) + (255 if rgba[3] > 200 else 150,)))
            faces.sort(key=lambda f: -f[0])
            draw.line((0, 420, 1100, 420), fill=(60, 70, 74, 255))
            for _, poly, colour in faces:
                draw.polygon(poly, fill=colour)
            draw.text((12, 10), f"Leopard 2A7  {label}  {view}", fill=(230, 230, 220, 255))
            image.save(f"{path_prefix}{label}-{view}.png")


def make_icon(path):
    """The assembling table's selection icon: a grey side view on a 256 x 64 transparent strip."""
    scale = 1.4
    width, height = 256, 64
    image = Image.new("RGBA", (width * 2, height * 2), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    faces = []
    for _, c, pts in posed_cubes(pose()):
        rgba = MATERIALS[c["_material"]]
        if rgba[3] < 200 or c["_name"] == "antenna":
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


def data_point(p):
    """Model pixels to vehicle data (blocks, +Z forward, +X left)."""
    return [round(p[0] / 16, 4), round(p[1] / 16, 4), round(-p[2] / 16, 4) + 0.0]


SOUNDS = [("yx_100_fire_3p", "fire"), ("yx_100_fire_1p", "fire_interior"), ("yx_100_far", "fire_distant"),
          ("yx_100_veryfar", "fire_very_far"), ("yx_100_reload", "load"), ("yx_100_engine", "engine")]


def copy_sounds(sbw_dir):
    """SBW's YX-100 recordings (used with the SBW team's permission), fetched with fetch_sbw.py --extra."""
    out = ASSETS / f"sounds/vehicle/{ID}"
    out.mkdir(parents=True, exist_ok=True)
    for source, target in SOUNDS:
        (out / f"{target}.ogg").write_bytes((Path(sbw_dir) / f"sounds/vehicle/yx_100/{source}.ogg").read_bytes())
    (out / "CREDITS.md").write_text(
        "# Leopard 2A7 sounds\n\nBy the Superb Warfare development team (their YX-100's), used with their permission\n"
        "(`design/superbwarfare-port/PERMISSION.md`). Copied unchanged by `design/leopard2/generate.py --sbw <dir>`:\n\n"
        "| File | SBW source |\n| --- | --- |\n" + "".join(f"| `{t}.ogg` | `vehicle/yx_100/{s}` |\n" for s, t in SOUNDS),
        encoding="utf-8")


def main():
    if "--sbw" in sys.argv:
        copy_sounds(sys.argv[sys.argv.index("--sbw") + 1])
    create_model()
    separate_finishes()
    print(f"Biased {resolve_zfighting()} coplanar faces against z-fighting.")
    atlas = make_texture()
    texture_path = ASSETS / f"textures/entity/vehicle/{ID}.png"
    atlas.save(texture_path)
    url = "data:image/png;base64," + base64.b64encode(texture_path.read_bytes()).decode()
    geometry = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.jeg.{ID}", "texture_width": ATLAS[0], "texture_height": ATLAS[1],
                        "visible_bounds_width": 14, "visible_bounds_height": 5, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": [{k: [{ck: cv for ck, cv in c.items() if not ck.startswith("_")} for c in v] if k == "cubes" else v
                   for k, v in b.items()} for b in BONES]}]}
    project = make_bbmodel(url)
    count = validate(project)
    geometry = native_geometry(geometry)
    write_json(ASSETS / f"geckolib/models/entity/vehicle/{ID}.geo.json", geometry)
    for level in (1, 2):
        write_json(ASSETS / f"geckolib/models/entity/vehicle/lod/{ID}_lod{level}.geo.json", make_lod(geometry, level))
    write_json(ASSETS / f"vehicle_paint/{ID}.json", {"unpainted_parts": unpainted_parts()})
    write_json(ASSETS / f"geckolib/animations/entity/vehicle/{ID}.animation.json", make_animations())
    write_json(HERE / f"{ID}.bbmodel", project)

    parts = hitbox_parts()
    cx = (TRACK_INNER + TRACK_OUTER) / 2
    wheels = [
        {"bones": ["sprocket_left", "sprocket_right"], "radius": SPROCKET[2]},
        {"bones": [f"road_wheel_{s}_{i}" for s in ("left", "right") for i in range(1, 8)], "radius": ROAD_R},
        {"bones": ["idler_left", "idler_right"], "radius": IDLER[2]},
        {"bones": [f"return_roller_{s}_{i}" for s in ("left", "right") for i in range(1, 5)], "radius": ROLLERS[0][2]},
    ]
    tracks = ('  "tracks": {"links": ' + str(LINKS) + ', "rest": ' + json.dumps([round(LINK_REST[0], 4), round(LINK_REST[1], 4)])
              + ', "rest_s": ' + json.dumps(round(LINK_REST_S, 4)) + ', "half_width": ' + json.dumps(round(cx / 16, 4))
              + ',\n    "path": ' + json.dumps([[round(z, 3), round(y, 3)] for z, y in PATH], separators=(",", ":"))
              + ',\n    "wheels": ' + json.dumps(wheels) + "},\n")
    write_vehicle_data(parts, tracks)

    rest = pose()
    bounds = extent_points([p for _, c, pts in posed_cubes(rest) if c["_name"] != "antenna" for p in pts])
    manifest = {"model": f"jeg:entity/vehicle/{ID}", "units_per_block": 16, "front": "-Z in the model, +Z in vehicle data",
                "reference": "Leopard 2A7, 1:1",
                "turret_origin": data_point(TURRET_PIVOT), "trunnion": data_point(TRUNNION),
                "barrel_offset_from_turret": data_point([TRUNNION[i] - TURRET_PIVOT[i] for i in range(3)]),
                "muzzle_rest": data_point([0.0, TRUNNION[1], MUZZLE_Z]),
                "coax_muzzle_rest": data_point([-4.0, 30.6, -36.0]),
                "rws_mount_pivot": data_point(RWS_PIVOT), "rws_trunnion": data_point(RWS_TRUNNION),
                "rws_muzzle_rest": data_point([RWS_TRUNNION[0], RWS_TRUNNION[1], RWS_MUZZLE_Z - 1.0]),
                "elevation_degrees": list(ELEVATION), "rear_arc_degrees": REAR_ARC, "rear_min_elevation": REAR_MIN, "recoil_px": RECOIL_PX,
                "tracks": {"links": LINKS, "path_points": len(PATH), "path_length_px": round(PATH_LENGTH, 3),
                           "link_pitch_px": round(PATH_LENGTH / LINKS, 3), "rest": LINK_REST, "rest_s": round(LINK_REST_S, 4),
                           "half_width_blocks": round(cx / 16, 4)},
                "model_bounds": [data_point(bounds[0]), data_point(bounds[1])],
                "hitbox": [{"part": p, "covers": l, "min": lo, "max": hi, "follows": f} for p, l, (lo, hi), f in parts],
                "bones": len(BONES), "cuboids": count}
    write_json(HERE / "rig.json", manifest)
    render_views(str(HERE / "review-"))
    make_icon(ASSETS / f"textures/vehicle_icon/{ID}_icon.png")
    print(json.dumps({k: manifest[k] for k in ("turret_origin", "trunnion", "barrel_offset_from_turret", "muzzle_rest",
                                               "coax_muzzle_rest", "rws_trunnion", "rws_muzzle_rest", "model_bounds", "tracks")}))


if __name__ == "__main__":
    main()
