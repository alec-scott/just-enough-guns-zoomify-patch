"""Author the bush plane's GeckoLib assets, rig manifest, hitboxes and review images (fleet plan section 8: the TOM-6
remodelled as a STOL bush plane; it keeps the id jeg:tom6 so planes in saved worlds carry over).

python -I design/bushplane/generate.py

A PA-18 Super Cub-class two-seat STOL bush plane at 1:1 (a block is a metre): 6.9 m long, 10.7 m span, 2 m high on its
wheels. A strut-braced high wing with flaps and ailerons, a cowled flat-four with a two-blade propeller (`propeller`
spins), big tundra tyres and a tailwheel (taildragger), a tailplane with elevators and a fin with a rudder, and a
tandem cabin. Model pixels, 16 a block, the nose faces -Z and +X is the pilot's right
(as the A-10's: its red light is at -X). Requires Pillow and numpy.
"""
from __future__ import annotations

import json
import math
import pathlib
import random
import sys

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE.parent / "superbwarfare-port"))
import refit_lib as L  # noqa: E402
import render_geo as R  # noqa: E402

ID = "tom6"
ASSETS = ROOT / "src/main/resources/assets/jeg"
VEHICLE_JSON = ROOT / f"src/main/resources/data/jeg/vehicles/{ID}.json"

MATERIALS = {
    "yellow": (226, 186, 46, 255), "yellow_dark": (196, 158, 34, 255), "black": (32, 32, 32, 255),
    "white": (226, 226, 220, 255), "steel": (120, 122, 124, 255), "rubber": (30, 30, 30, 255),
    "glass": (120, 160, 178, 140), "interior": (70, 70, 66, 255), "prop": (44, 44, 44, 255),
    "prop_tip": (232, 196, 40, 255), "lamp_red": (230, 40, 34, 255), "lamp_green": (40, 220, 80, 255),
    "lamp_white": (240, 240, 230, 255), "hub": (180, 182, 184, 255), "seat": (52, 48, 42, 255),
}
TILE = 16  # the old tile atlas, laid out before the bake gives every face its own texels
ATLAS = (128, 32)
# The fleet's model and texture review (2026-11-19): every face its own texels (refit_lib.bake_unique), the fabric's rib
# tapes, the registration and the lightning stripe painted on, then the detail pass (seams, wear, oil and dust).
TEXELS = 4
UV_SIZE = [128, 32]
KEPT = ("glass", "lamp_red", "lamp_green", "lamp_white")
DETAIL = dict(upscale=1, mud_height=7.0, mud=0.45, wear=0.7, streaks=0.5, paint_lum=0.95, paint_sat=0.9, bolts=False)
REGISTRATION = "N618"

# ---- Dimensions (model px; 16 a metre).
NOSE_Z, TAIL_Z = -50.0, 60.0          # the cowling's front to the tail cone
FUSE_BOTTOM, FUSE_TOP = 14.0, 30.0    # the cabin section
COWL_TOP = 25.0                       # below the pilot's eye
HALF_W = 6.0                          # half the fuselage's width
WING_Y = 30.0                         # the wing sits on the cabin roof
WING_LE, WING_TE = -28.0, -2.0        # chord 1.6 m
HALF_SPAN = 85.5                      # 10.7 m span
FLAP_X = (8.0, 44.0)                  # inboard trailing edge
AILERON_X = (46.0, 82.0)
TRAILING = 6.0                        # flap and aileron chord
WHEEL_R = 6.5                         # a tundra tyre about 0.8 m across
WHEEL_X = 15.0                        # half the track, 1.9 m
WHEEL_Z = -24.0
TAILWHEEL_R = 2.0
# A taildragger: with the fuselage level (in flight) the tailwheel hangs this far above the main wheels' contact, so
# on the ground, both on it, the airframe sits nose up at atan(15 / 81) = 10.5 deg (a Super Cub's is about 11);
# AbstractVehicleGeoRenderer.TOM6_STANCE pitches it so while it is slow on the ground.
TAILWHEEL_LIFT = 15.0
TAILWHEEL_Z = 57.0
PROP_AXIS = (0.0, 21.0, -51.0)
PROP_RADIUS = 16.0                    # a 2 m propeller
SEATS = {"pilot": (0.0, 15.0, -16.0), "passenger": (0.0, 15.0, -6.0)}
PILOT_EYE = (0.0, 27.0, -16.0)

BONES: list[dict] = []
BY_NAME: dict[str, dict] = {}


def bone(name, parent="root", pivot=(0, 0, 0)):
    entry = {"name": name, "pivot": [float(v) for v in pivot], "cubes": []}
    if parent:
        entry["parent"] = parent
    BONES.append(entry)
    BY_NAME[name] = entry
    return name


def material_uv(material, size):
    index = list(MATERIALS).index(material)
    u, v = (index % 8) * TILE + 1, (index // 8) * TILE + 1
    x, y, z = size
    clip = lambda a: max(0.5, min(TILE - 2, a))
    return {face: {"uv": [u, v], "uv_size": [clip(a), clip(b)]}
            for face, a, b in [("north", x, y), ("south", x, y), ("east", z, y), ("west", z, y), ("up", x, z), ("down", x, z)]}


def box(parent, a, b, material, rotation=None, pivot=None):
    origin = [round(min(a[i], b[i]), 4) for i in range(3)]
    size = [round(max(0.01, abs(b[i] - a[i])), 4) for i in range(3)]
    cube = {"origin": origin, "size": size, "uv": material_uv(material, size), "_material": material}
    if rotation and any(rotation):
        cube["rotation"] = [round(float(v), 4) for v in rotation]
        cube["pivot"] = [round(float(v), 4) for v in (pivot or [origin[i] + size[i] / 2 for i in range(3)])]
    BY_NAME[parent]["cubes"].append(cube)


def rod(parent, p0, p1, thickness, material):
    """A straight bar between two points in a plane of constant z (rotated about Z) or of constant x (about X)."""
    dx, dy, dz = (p1[i] - p0[i] for i in range(3))
    mid = [(p0[i] + p1[i]) / 2 for i in range(3)]
    if abs(dz) < 1e-6:
        length = math.hypot(dx, dy)
        angle = math.degrees(math.atan2(dx, dy))
        box(parent, (mid[0] - thickness / 2, mid[1] - length / 2, mid[2] - thickness / 2),
            (mid[0] + thickness / 2, mid[1] + length / 2, mid[2] + thickness / 2), material, rotation=(0, 0, angle), pivot=mid)
    else:
        length = math.hypot(dz, dy)
        angle = math.degrees(math.atan2(dz, dy))
        box(parent, (mid[0] - thickness / 2, mid[1] - length / 2, mid[2] - thickness / 2),
            (mid[0] + thickness / 2, mid[1] + length / 2, mid[2] + thickness / 2), material, rotation=(angle, 0, 0), pivot=mid)


def wheel(parent, x, y, z, radius, width, material="rubber", layers=7):
    """A tyre seen from the side: slices along Y, turned about X."""
    step = 2 * radius / layers
    for i in range(layers):
        offset = -radius + (i + .5) * step
        half = math.sqrt(max(.001, radius * radius - offset * offset))
        box(parent, (x - width / 2, y - radius + i * step, z - half), (x + width / 2, y - radius + (i + 1) * step + .02, z + half), material)
    box(parent, (x - width / 2 - 0.1, y - radius * 0.35, z - radius * 0.35), (x + width / 2 + 0.1, y + radius * 0.35, z + radius * 0.35), "hub")


def chamfered(parent, x0, x1, y0, y1, z0, z1, material, c):
    """A box running along Z with its four long edges cut back by {c}: a rounder section from three boxes."""
    box(parent, (x0 + c, y0, z0), (x1 - c, y1, z1), material)
    box(parent, (x0, y0 + c, z0), (x0 + c, y1 - c, z1), material)
    box(parent, (x1 - c, y0 + c, z0), (x1, y1 - c, z1), material)


def create_model():
    bone("root", None)
    bone("airframe", "root")
    a = "airframe"
    # ---- Fuselage: cowling, cabin, tail cone tapering to the tail post.
    # The cowl's top stays under the pilot's eye (27.5 px): it first came up to 28 and filled the view ahead.
    chamfered(a, -5.0, 5.0, 15.0, 24.0, NOSE_Z, NOSE_Z + 6, "yellow", 1.6)                # the nose bowl
    for side in (-1, 1):
        x0, x1 = sorted((side * 1.7, side * 4.2))
        box(a, (x0, 18.3, NOSE_Z - 0.06), (x1, 21.9, NOSE_Z + 0.5), "black")               # its two air inlets
    chamfered(a, -HALF_W, HALF_W, FUSE_BOTTOM, COWL_TOP, NOSE_Z + 6, -30.0, "yellow", 1.6)  # cowl
    box(a, (-5.2, COWL_TOP - 2.0, NOSE_Z + 2), (-3.0, COWL_TOP + 0.4, NOSE_Z + 8), "black")  # exhaust/air box
    box(a, (-0.6, COWL_TOP - 0.1, NOSE_Z + 9), (0.6, COWL_TOP + 0.35, NOSE_Z + 12), "steel")  # oil filler door
    box(a, (2.6, FUSE_BOTTOM - 1.6, NOSE_Z + 8), (4.4, FUSE_BOTTOM + 0.2, -33.0), "steel")    # the exhaust stack
    box(a, (2.8, FUSE_BOTTOM - 1.4, -33.0), (4.2, FUSE_BOTTOM - 0.2, -32.2), "black")        # its outlet
    box(a, (-3.6, FUSE_BOTTOM - 1.4, NOSE_Z + 6), (-0.6, FUSE_BOTTOM + 0.2, NOSE_Z + 13), "black")  # carburettor air box
    chamfered(a, -HALF_W, HALF_W, FUSE_BOTTOM, 22.0, -30.0, 4.0, "yellow", 1.0)            # cabin lower
    box(a, (HALF_W - 0.02, 16.0, -20.0), (HALF_W + 0.25, 16.6, -14.5), "steel")            # the step
    # Window frames: posts at the windscreen, the door split and the back, so the pilot sees out to both sides (full
    # panels here first walled the cabin in).
    for side in (-1, 1):
        x0, x1 = sorted((side * HALF_W, side * (HALF_W - 0.6)))
        for z0, z1 in ((-30.0, -28.0), (-12.5, -11.5), (2.0, 4.0)):
            box(a, (x0, 22.0, z0), (x1, FUSE_TOP, z1), "yellow")
    # Instrument panel under the windscreen, with a few dials.
    box(a, (-HALF_W + 0.8, 19.0, -28.0), (HALF_W - 0.8, 23.0, -27.0), "black")
    for x in (-3.0, 0.0, 3.0):
        box(a, (x - 0.8, 20.6, -27.1), (x + 0.8, 22.2, -26.9), "white")
    box(a, (-HALF_W + 0.3, 22.4, -27.0), (HALF_W - 0.3, 29.6, 2.0), "glass")                # side windows (one slab)
    box(a, (-HALF_W + 0.5, 21.6, -31.5), (HALF_W - 0.5, 29.5, -29.5), "glass", rotation=(-35, 0, 0))  # windscreen
    box(a, (-HALF_W + 0.8, FUSE_BOTTOM + 0.5, -26.0), (HALF_W - 0.8, 22.0, 2.0), "interior")
    # Tandem seats and the stick, seen through the windows.
    for z in (-13.4, -3.4):
        box(a, (-2.6, 22.0, z), (2.6, 26.2, z + 1.0), "seat")
    box(a, (-0.25, 22.0, -21.0), (0.25, 24.8, -20.5), "black")
    box(a, (-HALF_W - 0.05, 18.0, -30.0), (HALF_W + 0.05, 19.4, 40.0), "black")            # the side stripe
    box(a, (-HALF_W - 0.05, 18.4, NOSE_Z + 6.0), (HALF_W + 0.05, 19.0, -30.0), "black")      # running on along the cowl
    for i, (z0, z1, w, b, t) in enumerate([(4.0, 18.0, 5.4, 15.0, 28.0), (18.0, 32.0, 4.4, 16.5, 27.0),
                                           (32.0, 46.0, 3.2, 18.0, 26.0), (46.0, TAIL_Z, 2.0, 19.5, 25.0)]):
        chamfered(a, -w, w, b, t, z0, z1, "yellow", min(1.0, w * 0.25))
    # The VHF whip behind the wing.
    box(a, (-0.5, 28.0, 9.0), (0.5, 28.4, 11.0), "black")
    box(a, (-0.12, 28.4, 9.85), (0.12, 34.5, 10.1), "black")
    # ---- Wing on the cabin roof, its trailing edge broken out into flaps and ailerons.
    wing = bone("wing", a)
    for side in (-1, 1):
        x_root, x_tip = 0.0, side * HALF_SPAN
        box(wing, (min(x_root, x_tip), WING_Y, WING_LE), (max(x_root, x_tip), WING_Y + 2.6, WING_TE - TRAILING), "yellow")
        # the fixed trailing edge between flap and aileron and at the tip
        for xa, xb in ((0.0, FLAP_X[0]), (FLAP_X[1], AILERON_X[0]), (AILERON_X[1], HALF_SPAN)):
            box(wing, (min(side * xa, side * xb), WING_Y + 0.3, WING_TE - TRAILING), (max(side * xa, side * xb), WING_Y + 2.2, WING_TE), "yellow")
        box(wing, (side * HALF_SPAN - (0.9 if side > 0 else -0.9) - 0.6, WING_Y + 0.6, WING_LE + 3.0),
            (side * HALF_SPAN - (0.9 if side > 0 else -0.9) + 0.6, WING_Y + 1.8, WING_LE + 6.0), "lamp_red" if side < 0 else "lamp_green")
        name = "left" if side < 0 else "right"
        flap = bone(f"flap_{name}", wing, (0.0, WING_Y + 1.2, WING_TE - TRAILING))
        box(flap, (min(side * FLAP_X[0], side * FLAP_X[1]), WING_Y + 0.4, WING_TE - TRAILING),
            (max(side * FLAP_X[0], side * FLAP_X[1]), WING_Y + 2.0, WING_TE), "yellow_dark")
        aileron = bone(f"aileron_{name}", wing, (0.0, WING_Y + 1.2, WING_TE - TRAILING))
        box(aileron, (min(side * AILERON_X[0], side * AILERON_X[1]), WING_Y + 0.4, WING_TE - TRAILING),
            (max(side * AILERON_X[0], side * AILERON_X[1]), WING_Y + 2.0, WING_TE), "yellow_dark")
        # the lift struts, two a side, from the lower longeron to the wing, each with its jury strut
        for z in (WING_LE + 6.0, WING_TE - 9.0):
            rod(a, (side * (HALF_W - 0.5), FUSE_BOTTOM + 2.0, z), (side * 40.0, WING_Y, z), 1.0, "steel")
            rod(a, (side * 24.0, FUSE_BOTTOM + 2.0 + (WING_Y - FUSE_BOTTOM - 2.0) * (24.0 - HALF_W + 0.5) / (40.0 - HALF_W + 0.5), z),
                (side * 24.0, WING_Y, z), 0.5, "steel")
        # the rounded tip bow, and the fuel cap on the wing root
        x0, x1 = sorted((side * HALF_SPAN, side * (HALF_SPAN + 1.2)))
        box(wing, (x0, WING_Y + 0.4, WING_LE + 1.5), (x1, WING_Y + 2.2, WING_TE - TRAILING - 1.0), "yellow")
        x0, x1 = sorted((side * (HALF_W + 2.0), side * (HALF_W + 3.6)))
        box(wing, (x0, WING_Y + 2.6, WING_LE + 6.0), (x1, WING_Y + 3.0, WING_LE + 7.6), "steel")
    box(a, (-40.0 + 2.0, WING_Y + 2.4, WING_LE + 0.5), (-40.0 + 6.0, WING_Y + 2.8, WING_LE + 1.5), "lamp_white")  # landing light, left wing
    box(a, (-56.4, WING_Y - 1.4, WING_LE + 4.0), (-56.0, WING_Y, WING_LE + 4.4), "steel")         # the pitot's mast
    box(a, (-56.4, WING_Y - 1.4, WING_LE - 2.0), (-56.0, WING_Y - 1.0, WING_LE + 4.4), "steel")   # and its tube
    # ---- Undercarriage: sprung legs to big tundra tyres; a tailwheel.
    for side in (-1, 1):
        name = "left" if side < 0 else "right"
        rod(a, (side * (HALF_W - 0.5), FUSE_BOTTOM, WHEEL_Z), (side * WHEEL_X, WHEEL_R, WHEEL_Z), 1.2, "steel")
        rod(a, (side * (HALF_W - 0.5), FUSE_BOTTOM, WHEEL_Z + 6.0), (side * WHEEL_X, WHEEL_R, WHEEL_Z + 6.0), 1.0, "steel")
        box(a, (min(side * WHEEL_X, side * (WHEEL_X - 1.0)), WHEEL_R - 0.5, WHEEL_Z), (max(side * WHEEL_X, side * (WHEEL_X - 1.0)), WHEEL_R + 0.5, WHEEL_Z + 6.0), "steel")
        x0, x1 = sorted((side * (HALF_W - 0.3), side * (HALF_W + 1.4)))
        box(a, (x0, FUSE_BOTTOM - 1.2, WHEEL_Z - 1.0), (x1, FUSE_BOTTOM + 0.6, WHEEL_Z + 7.0), "black")  # bungee housing
        w = bone(f"wheel_{name}", a, (side * WHEEL_X, WHEEL_R, WHEEL_Z))
        wheel(w, side * WHEEL_X, WHEEL_R, WHEEL_Z, WHEEL_R, 5.0)
    tail_y = TAILWHEEL_R + TAILWHEEL_LIFT
    rod(a, (0.0, 20.5, TAILWHEEL_Z - 3.0), (0.0, tail_y, TAILWHEEL_Z), 0.8, "steel")
    tw = bone("tailwheel", a, (0.0, tail_y, TAILWHEEL_Z))
    wheel(tw, 0.0, tail_y, TAILWHEEL_Z, TAILWHEEL_R, 1.4, layers=4)
    # ---- Tail: tailplane and elevators, fin and rudder.
    box(a, (-25.0, 22.0, 48.0), (25.0, 23.2, 56.0), "yellow")
    elevator = bone("elevator", a, (0.0, 22.6, 56.0))
    box(elevator, (-25.0, 22.1, 56.0), (25.0, 23.1, 61.5), "yellow_dark")
    box(a, (-0.6, 25.0, 48.0), (0.6, 37.0, 58.0), "yellow")
    for z0, y1 in ((40.0, 26.6), (43.0, 28.6), (45.5, 31.0)):
        box(a, (-0.5, 24.5, z0), (0.5, y1, 48.0), "yellow")                                  # dorsal fillet
    for side in (-1, 1):
        rod(a, (0.0, 33.0, 52.0), (side * 20.0, 23.2, 52.0), 0.25, "steel")                  # flying wires
        rod(a, (side * 18.0, 22.0, 52.0), (side * 2.0, 20.0, 52.0), 0.25, "steel")
    rudder = bone("rudder", a, (0.0, 30.0, 58.0))
    box(rudder, (-0.5, 21.0, 58.0), (0.5, 38.0, 64.0), "yellow_dark")
    box(rudder, (-0.7, 37.2, 58.5), (0.7, 38.4, 60.0), "lamp_red")                          # beacon
    box(rudder, (-0.5, 24.0, 63.6), (0.5, 25.4, 64.3), "lamp_white")                        # tail light
    # ---- Engine: spinner and two-blade propeller.
    prop = bone("propeller", a, PROP_AXIS)
    px, py, pz = PROP_AXIS
    box(prop, (px - 2.6, py - 2.6, pz - 3.5), (px + 2.6, py + 2.6, pz + 1.0), "white")
    box(prop, (px - 1.6, py - 1.6, pz - 4.6), (px + 1.6, py + 1.6, pz - 3.5), "white")
    for sign in (-1, 1):
        y0, y1 = (py, py + sign * PROP_RADIUS)
        box(prop, (px - 1.4, min(y0, y1), pz - 0.6), (px + 1.4, max(y0, y1), pz + 0.4), "prop", rotation=(0, sign * 12, 0), pivot=(px, py, pz))
        tip0 = py + sign * (PROP_RADIUS - 2.0)
        box(prop, (px - 1.45, min(tip0, y1), pz - 0.65), (px + 1.45, max(tip0, y1), pz + 0.45), "prop_tip", rotation=(0, sign * 12, 0), pivot=(px, py, pz))


# Which side faces' texture runs backwards along the fuselage (so the registration reads front to back on both sides).
MIRRORED = {"east": False, "west": False}
GLYPHS = {
    "N": ["X...X", "XX..X", "X.X.X", "X..XX", "X...X"], "S": [".XXXX", "X....", ".XXX.", "....X", "XXXX."],
    "C": [".XXXX", "X....", "X....", "X....", ".XXXX"], "1": ["..X..", ".XX..", "..X..", "..X..", ".XXX."],
    "6": [".XXX.", "X....", "XXXX.", "X...X", ".XXX."], "8": [".XXX.", "X...X", ".XXX.", "X...X", ".XXX."],
}


def paint_livery(base, faces):
    """Paint on the baked texture: rib tapes along the fabric wing and tail, the registration on the fuselage's sides,
    the black stripe run onto the cowl."""
    px = base.load()
    tape = (238, 204, 74, 255)
    for item in faces:
        x0, y0, x1, y1 = item["rect"]
        w, h = item["size"]
        if item["bone"] == "wing" and item["material"] == "yellow" and item["face"] in ("up", "down") and w > 20:
            # Chordwise rib tapes every 7 px of span, each a texel wide with its row of stitches.
            k = (x1 - x0) / w
            for rib in range(1, int(w / 7.0) + 1):
                xx = x0 + int(rib * 7.0 * k)
                for yy in range(y0, y1):
                    px[xx, yy] = tape
                    if yy % 3 == 0 and xx + 1 < x1:
                        px[xx + 1, yy] = (204, 168, 40, 255)
        if item["bone"] == "airframe" and item["material"] == "yellow" and item["face"] in ("east", "west") \
                and 13.5 < w < 14.5 and h > 9.5:
            # The second tail section's sides (14 x 10.5 px): the registration, black, 5 px high.
            k = (x1 - x0) / w
            cell = max(1, int(round(0.5 * k)))
            text = REGISTRATION if item["face"] == "west" else REGISTRATION
            span = len(text) * 6 * cell
            left = x0 + max(0, ((x1 - x0) - span) // 2)
            top = y0 + int(2.0 * k)
            for i, ch in enumerate(text):
                rows = GLYPHS.get(ch)
                if not rows:
                    continue
                for r, row in enumerate(rows):
                    for c, on in enumerate(row):
                        if on == "X":
                            for dy in range(cell):
                                for dx in range(cell):
                                    gx = left + (i * 6 + c) * cell + dx
                                    if MIRRORED[item["face"]]:
                                        gx = left + span - 1 - ((i * 6 + c) * cell + dx)
                                    gy = top + r * cell + dy
                                    if x0 <= gx < x1 and y0 <= gy < y1:
                                        px[gx, gy] = (26, 26, 26, 255)


def paint_tile(name, rgba, rng):
    tile = Image.new("RGBA", (TILE, TILE))
    px = tile.load()
    for yy in range(TILE):
        for xx in range(TILE):
            n = 0 if name.startswith("lamp") or name in ("glass", "prop_tip") else rng.choice((-3, -1, 0, 0, 1, 3))
            c = [max(0, min(255, v + n)) for v in rgba[:3]]
            px[xx, yy] = (*c, rgba[3])
    if name == "glass":
        for yy in range(TILE):
            for xx in range(TILE):
                if 5 <= xx + yy // 2 <= 7:
                    px[xx, yy] = (210, 228, 236, 110)
    return tile


def make_texture():
    atlas = Image.new("RGBA", ATLAS, (0, 0, 0, 0))
    rng = random.Random(18)
    for i, (name, rgba) in enumerate(MATERIALS.items()):
        atlas.paste(paint_tile(name, rgba, rng), (i % 8 * TILE, i // 8 * TILE))
    return atlas


def geometry():
    bones = []
    for b in BONES:
        out = {k: v for k, v in b.items() if k != "cubes"}
        out["cubes"] = [{k: v for k, v in c.items() if not k.startswith("_")} for c in b["cubes"]]
        if not out["cubes"]:
            del out["cubes"]
        bones.append(out)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.jeg.{ID}", "texture_width": UV_SIZE[0], "texture_height": UV_SIZE[1],
                        "visible_bounds_width": 13, "visible_bounds_height": 4, "visible_bounds_offset": [0, 1, 0]},
        "bones": bones}]}


def to_data(p):
    return [round(float(p[0]) / 16, 4), round(float(p[1]) / 16, 4), round(-float(p[2]) / 16, 4) + 0.0]


def json_rows(obb):
    rows = []
    for row in obb:
        keys = ["part", "x", "y", "z", "half_width", "half_height", "half_depth"]
        rows.append("    {" + ", ".join(f'"{k}": {json.dumps(row[k] if isinstance(row[k], str) else float(row[k]))}' for k in keys) + "}")
    return rows


def bake():
    """Unique UVs, the base paint and livery, the weathering pass, glass and lamps put back."""
    sys.path.insert(0, str(ROOT / "design/ground-vehicles"))
    import detail_texture as D
    uv, faces, base = L.bake_unique(BONES, MATERIALS, TEXELS)
    UV_SIZE[:] = [uv[0], uv[1]]
    paint_livery(base, faces)
    geo = L.resolve_zfighting(geometry())
    texture, _, _ = D.detail_image(geo, base, DETAIL)
    L.paste_faces(texture, base, faces, KEPT)
    return geo, texture


def main():
    create_model()
    geo, texture = bake()
    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, texture)
    sub = lambda name: L.subtree(by, name)
    lo, hi = L.extent(quads)
    low = float(lo[1])
    if abs(low) > 0.1:
        raise SystemExit(f"the wheels must stand on the ground, lowest point {low:.2f} px")
    prop_lo, prop_hi = L.extent(quads, sub("propeller"))
    if prop_lo[1] < 1.0:
        raise SystemExit(f"the propeller strikes the ground at rest: {prop_lo[1]:.2f} px")

    def ext(names):
        return L.extent(quads, names)
    airframe_only = sub("airframe") - sub("wing") - sub("propeller") - sub("wheel_left") - sub("wheel_right") - sub("tailwheel") - sub("elevator") - sub("rudder")
    fuse = [q for q in quads if q[0] in airframe_only and float(q[2][:, 0].max()) <= HALF_W + 1.0 and float(q[2][:, 0].min()) >= -HALF_W - 1.0]
    fuse_lo = (min(float(q[2][:, i].min()) for q in fuse) for i in range(3))
    fuse_lo = list(fuse_lo)
    fuse_hi = [max(float(q[2][:, i].max()) for q in fuse) for i in range(3)]
    cowl_lo, cowl_hi = [-HALF_W, FUSE_BOTTOM, NOSE_Z - 5.0], [HALF_W, COWL_TOP, -30.0]
    wing_lo, wing_hi = ext(sub("wing"))
    tail_lo, tail_hi = ext(sub("elevator") | sub("rudder"))
    tail_lo = [min(tail_lo[0], -25.0), min(tail_lo[1], 21.0), 46.0]
    gear_lo, gear_hi = ext(sub("wheel_left") | sub("wheel_right"))
    parts = [("body", "fuselage", fuse_lo, fuse_hi), ("main_engine", "engine and propeller", cowl_lo, cowl_hi),
             ("body", "wing", wing_lo, wing_hi), ("body", "tail", tail_lo, tail_hi), ("body", "main gear", gear_lo, gear_hi)]
    obb = L.obb_rows(parts)

    rig = {
        "model": f"jeg:entity/vehicle/{ID}", "units_per_block": 16, "front": "-Z in the model, +Z in vehicle data",
        "reference": "PA-18 Super Cub-class two-seat STOL bush plane, 1:1",
        "length_m": round((hi[2] - lo[2]) / 16, 2), "span_m": round((hi[0] - lo[0]) / 16, 2), "height_m": round((hi[1] - lo[1]) / 16, 2),
        "propeller_axis": to_data(PROP_AXIS), "propeller_radius": PROP_RADIUS / 16,
        "seats": {k: to_data(v) for k, v in SEATS.items()}, "pilot_eye": to_data(PILOT_EYE),
        "wingtips": [to_data((HALF_SPAN, WING_Y + 1.2, (WING_LE + WING_TE) / 2)), to_data((-HALF_SPAN, WING_Y + 1.2, (WING_LE + WING_TE) / 2))],
        "nav_lights": {"left_red": [-(HALF_SPAN - 1.5), WING_Y + 1.2, WING_LE + 4.5], "right_green": [HALF_SPAN - 1.5, WING_Y + 1.2, WING_LE + 4.5],
                       "tail_white": [0.0, 24.7, 64.0], "beacon": [0.0, 37.8, 59.2], "landing_light": [-36.0, WING_Y + 2.6, WING_LE + 1.0]},
        "model_bounds": [to_data(lo), to_data(hi)], "hitbox": obb, "bones": len(BONES),
        "cuboids": sum(len(b.get("cubes", [])) for b in L.bones_of(geo)),
    }
    L.write_json(HERE / "rig.json", rig)
    for label, pose in (("rest", {}), ("flaps", {"flap_left": (-30, 0, 0), "flap_right": (-30, 0, 0), "propeller": (0, 0, 40)})):
        sheet, _ = R.contact_sheet(geo, texture, f"Bush plane ({label})", "design/bushplane/generate.py", "propeller", pose)
        sheet.save(HERE / f"review-{label}.png", optimize=True)

    L.write_json(ASSETS / f"geckolib/models/entity/vehicle/{ID}.geo.json", geo)
    texture.save(ASSETS / f"textures/entity/vehicle/{ID}.png")
    L.write_json(ASSETS / f"geckolib/animations/entity/vehicle/{ID}.animation.json",
                 {"format_version": "1.8.0", "animations": {f"animation.{ID}.idle": {"loop": True, "bones": {}}}})
    q, ts, _, _ = R.load_quads(geo, {})
    shot = R.render(R.visible(q, texture, ts), texture, ts, 90, 0, size=(600, 300)).convert("RGBA")
    background = shot.getpixel((2, 2))
    shot.putdata([(0, 0, 0, 0) if p[:3] == background[:3] else (*(min(235, 120 + int(.3 * p[0] + .59 * p[1] + .11 * p[2]) // 2),) * 3, 255)
                  for p in shot.get_flattened_data()])
    crop = shot.crop(shot.getbbox())
    crop.thumbnail((240, 56))
    icon = Image.new("RGBA", (256, 64), (0, 0, 0, 0))
    icon.paste(crop, ((256 - crop.width) // 2, 64 - crop.height - 4), crop)
    icon.save(ASSETS / f"textures/vehicle_icon/{ID}_icon.png")
    if VEHICLE_JSON.exists():
        data = VEHICLE_JSON.read_text(encoding="utf-8")
        start = data.index('  "obb": [')
        end = data.index("],", start) + 2
        VEHICLE_JSON.write_text(data[:start] + '  "obb": [\n' + ",\n".join(json_rows(obb)) + "\n  ]," + data[end:], encoding="utf-8")
    print(json.dumps({k: v for k, v in rig.items() if k != "hitbox"}))


if __name__ == "__main__":
    main()
