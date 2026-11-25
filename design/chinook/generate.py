"""Author the CH-47F Chinook's GeckoLib assets, rig manifest, hitboxes and review images (design/chinook/PLAN.md, P1).

python -I design/chinook/generate.py

A CH-47F tandem-rotor heavy-lift helicopter at 1:1 (a block is a metre): fuselage 15.5 m, 30 m with the rotors turning,
two three-blade rotors 18.3 m across, intermeshed and turning opposite ways (the aft one 60 degrees out of phase).
Model pixels, 16 a metre; the nose faces -Z and +X is the pilot's right (the A-10's convention: red light on -X).

Moving parts, each a bone on its real hinge (driven from synced state in the renderer, never canned animation):
  rotor_front / rotor_rear       spin about Y; blades rotor_<f|r>_blade_<i> droop at rest
  ramp                           hinged at the cabin floor's aft edge: closed (up), level, down
  ramp_door                      the upper ramp door, slides up into the aft pylon
  wheel_<fl|fr|rl|rr>            the gear's wheels
  hook                           the centre cargo hook
The walk-in cabin is hollow: its floor's top is 1 m up, 3 x 3 m inside (grown from the real 2.3 x 2.0: see grow), 9.2 m
long (rig.json "cabin").
Requires Pillow and numpy.
"""
from __future__ import annotations

import json
import math
import pathlib
import sys

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE.parent / "superbwarfare-port"))
sys.path.insert(0, str(HERE.parent / "superbwarfare-port" / "triage"))
import refit_lib as L  # noqa: E402
import render_geo as R  # noqa: E402

ID = "ch47"
ASSETS = ROOT / "src/main/resources/assets/jeg"
VEHICLE_JSON = ROOT / f"src/main/resources/data/jeg/vehicles/{ID}.json"

# US Army aircraft green, low-visibility black markings; the cabin's quilted lining, red troop-seat webbing.
MATERIALS = {
    "green": (74, 80, 56, 255), "green_dark": (60, 66, 46, 255), "green_label": (74, 80, 56, 255),
    "metal": (108, 110, 104, 255), "black": (30, 31, 29, 255), "rubber": (28, 28, 28, 255),
    "glass": (112, 140, 146, 120), "quilt": (104, 108, 88, 255), "floor": (84, 86, 82, 255),
    "tread": (58, 60, 58, 255), "webbing": (150, 40, 32, 255), "frame": (64, 66, 62, 255),
    "exhaust": (42, 40, 38, 255), "blade": (46, 48, 42, 255), "blade_tip": (214, 190, 64, 255),
    "hub": (88, 90, 86, 255), "screen": (34, 36, 34, 255), "panel": (40, 44, 40, 255),
    "lamp_red": (150, 36, 32, 255), "lamp_green": (40, 128, 70, 255), "lamp_white": (196, 198, 190, 255),
    "lamp_form": (84, 92, 74, 255), "lamp_dome": (206, 202, 186, 255), "cable": (34, 35, 33, 255),
}
TEXELS = 2
UV_SIZE = [256, 256]
# Kept as baked after the weathering pass: the lamps and glass, and the interior's detailed materials (interior_paint).
KEPT = ("glass", "lamp_red", "lamp_green", "lamp_white", "lamp_form", "lamp_dome", "blade_tip",
        "quilt", "floor", "tread", "webbing", "cable", "screen")
DETAIL = dict(upscale=1, mud_height=10.0, mud=0.35, wear=0.65, streaks=0.6, paint_lum=0.95, paint_sat=0.9, bolts=True)
SERIAL = "11-09618"

# ---- Dimensions (model px; 16 a metre). z: nose -134, tail +114; the rotor shafts 11.94 m apart.
NOSE_Z, TAIL_Z = -134.0, 114.0
FWD_HUB = (0.0, 69.0, -95.5)
AFT_HUB = (0.0, 90.0, 95.5)
ROTOR_R = 146.4               # 9.15 m
BLADE_CHORD = 13.0            # 0.81 m
BLADE_DROOP = 3.0             # degrees down at rest
FLOOR_Y = 16.0                # the cabin floor's top, 1 m up
BELLY_Y = 9.6
IN_HALF = 18.4                # the cabin 2.3 m wide inside
OUT_HALF = 21.4
CABIN_TOP = 48.0              # 2 m inside
ROOF_Y = 52.0
CABIN_FRONT = -81.0           # the cockpit bulkhead
CABIN_REAR = 66.0             # the ramp's hinge: 9.2 m of cabin
RAMP_LEN = 32.0               # 2 m
RAMP_CLOSED = 62.0            # degrees up, closed
SPONSON_HALF = 30.2           # 3.8 m over the sponsons
SPONSON_TOP = 25.0
GEAR_R = 7.0
GEAR_FRONT_Z, GEAR_REAR_Z = -62.0, 48.0
GEAR_FRONT_X, GEAR_REAR_X = 25.6, 27.0
HOOK = (0.0, BELLY_Y - 1.0, -10.0)
NACELLE = (27.0, 56.0)        # x, y of the T55s' axes
NACELLE_R = 7.5
NACELLE_Z = (62.0, 108.0)
SEATS = {"pilot": (8.0, FLOOR_Y, -108.0), "copilot": (-8.0, FLOOR_Y, -108.0)}
PILOT_EYE = (8.0, FLOOR_Y + 19.0, -108.0)
PORTHOLES = [-48.0 + 14.0 * i for i in range(8)]      # along both sides, behind the door gunners
DOOR_Z = (-79.0, -66.0)       # the crew door (right): 0.8 m wide, slides aft on rails along the outside
DOOR_TOP = 46.0
DOOR_SLIDE = 15.0             # how far its upper half slides open
DOOR_STEP_TOP = FLOOR_Y + 11.0  # the split: the lower half folds down into steps
GUN_WINDOW_Z = (-79.0, -68.0)  # the left door gunner's window (the forward escape hatch)
GUN_WINDOW_Y = (29.0, 42.0)
GUN_TRUNNION_Y = 36.5          # the door guns' elevation axis, on the pintles' tops (y 34)
CELLS_Z = 9                                         # 9 cabin cells, 1 m long each, from the bulkhead aft

# ---- Grown for walking about in (the user, 2026-11-21: "3 blocks wide and 1 block higher", the cabin): the model is
# authored above at the real CH-47's size and every point is moved on output (grow): each half WIDEN out from the centre
# line, so only the flat floor, roof and belly lengthen and the curves keep their shapes (the cabin 2.3 -> 3 m wide
# inside), and everything above the windscreen's top (RAISE_FROM) up RAISE (2 -> 3 m high inside). Ahead of the cabin
# the nose is stretched instead between the pilot's eye line (NOSE_RAMP_FROM) and the windscreen's top, so the windscreen
# grows taller and the cockpit's roof rises in one curve, blended into the plain raise over NOSE_BLEND (a step there
# stood a box on the cockpit). The rotors only rise. mesh.py moves its points the same way; rig.json, the hitboxes and
# ChinookEntity's constants are in the grown frame.
WIDEN = 5.6
RAISE = 16.0
RAISE_FROM = 47.0
NOSE_RAMP_FROM = 34.0        # just above the windscreen's sill (33.6): the pilot's eye (35) still looks out over it
NOSE_BLEND = (-113.2, -112.0)  # z: the nose's stretch (ahead) to the plain raise (aft, from the flat sides)
KEEP_WIDTH = 12.0    # a box across the centre line narrower than this is one centred item (a lamp, the hook): kept


def grow_x(x):
    return x + WIDEN if x > 1e-9 else x - WIDEN if x < -1e-9 else 0.0


def raise_by(y, z):
    """How far an authored point at height y, station z rises."""
    step = RAISE if y > RAISE_FROM else 0.0
    ramp = RAISE * min(max((y - NOSE_RAMP_FROM) / (RAISE_FROM - NOSE_RAMP_FROM), 0.0), 1.0)
    t = min(max((z - NOSE_BLEND[0]) / (NOSE_BLEND[1] - NOSE_BLEND[0]), 0.0), 1.0)
    t = t * t * (3.0 - 2.0 * t)
    return ramp + (step - ramp) * t


def grow_y(y, z=0.0):
    return y + raise_by(y, z)


def grow(p, rotor=False):
    """An authored point in the grown model (a rotor's: only raised)."""
    return (p[0] if rotor else grow_x(p[0]), grow_y(p[1], p[2]), p[2])


def grow_box(lo, hi, rotor=False):
    """An authored box's corners in the grown model: stretched where it spans the lines, moved whole otherwise; a small
    one across the centre line keeps its width."""
    lo, hi = list(lo), list(hi)
    if not rotor:
        if lo[0] < 1e-9 and hi[0] > -1e-9 and hi[0] - lo[0] < KEEP_WIDTH:
            c = (lo[0] + hi[0]) / 2
            shift = grow_x(c) - c if abs(c) > 0.5 else 0.0
            lo[0], hi[0] = lo[0] + shift, hi[0] + shift
        else:
            lo[0], hi[0] = grow_x(lo[0]), grow_x(hi[0])
    zc = (lo[2] + hi[2]) / 2                 # one station for the whole box, so it stays a box
    lo[1], hi[1] = grow_y(lo[1], zc), grow_y(hi[1], zc)
    return lo, hi


# The cockpit's furniture in the grown model (model pixels, +X the right): (low corner, high corner). Ch47CockpitLayer
# draws on their faces (the panel's aft face, the console's top, the overhead console's underside); make_cockpit.py
# lays out the textures to these sizes. The pilot (right seat) sits at x 13.6, the eye at y 35, z -108.
COCKPIT = {
    "panel": ((-20.6, 26.0, -119.0), (20.6, 35.0, -115.0)),       # 41.2 x 9 face at z -115, its top at the sill
    "glare": ((-21.0, 35.0, -120.0), (21.0, 35.6, -114.0)),       # the glare shield over it
    "console": ((-4.6, 16.0, -115.0), (4.6, 26.0, -97.0)),        # 9.2 x 18 top at y 26
    "overhead": ((-6.0, 61.0, -112.0), (6.0, 64.0, -97.0)),       # 12 x 15 underside at y 61
}

BONES: list[dict] = []
BY_NAME: dict[str, dict] = {}


def is_rotor(name):
    return name.startswith("rotor")


def bone(name, parent="root", pivot=(0, 0, 0), rotation=None):
    pivot = grow(pivot, is_rotor(name))
    entry = {"name": name, "pivot": [float(v) for v in pivot], "cubes": []}
    if parent:
        entry["parent"] = parent
    if rotation:
        entry["rotation"] = [float(v) for v in rotation]
    BONES.append(entry)
    BY_NAME[name] = entry
    return name


def box(parent, a, b, material, rotation=None, pivot=None, raw=False):
    """A box, authored at the real size and grown; raw, already in the grown model (the cockpit's furniture, placed
    where the growing would stretch it: COCKPIT)."""
    rotor = is_rotor(parent)
    if not raw:
        a, b = grow_box([min(a[i], b[i]) for i in range(3)], [max(a[i], b[i]) for i in range(3)], rotor)
    if pivot is not None:
        pivot = grow(pivot, rotor)
    origin = [round(min(a[i], b[i]), 4) for i in range(3)]
    size = [round(max(0.01, abs(b[i] - a[i])), 4) for i in range(3)]
    cube = {"origin": origin, "size": size, "uv": {}, "_material": material}
    if rotation and any(rotation):
        cube["rotation"] = [round(float(v), 4) for v in rotation]
        cube["pivot"] = [round(float(v), 4) for v in (pivot or [origin[i] + size[i] / 2 for i in range(3)])]
    BY_NAME[parent]["cubes"].append(cube)


def mirrored(parent, x0, x1, y0, y1, z0, z1, material, **kw):
    """The same box on both sides of the centreline (x0, x1 on the +X side)."""
    box(parent, (x0, y0, z0), (x1, y1, z1), material, **kw)
    box(parent, (-x1, y0, z0), (-x0, y1, z1), material, **kw)


def rounded(parent, x0, x1, y0, y1, z0, z1, material, c):
    """A box along Z with its four long edges cut back by c: a rounder section from three boxes."""
    box(parent, (x0 + c, y0, z0), (x1 - c, y1, z1), material)
    box(parent, (x0, y0 + c, z0), (x0 + c, y1 - c, z1), material)
    box(parent, (x1 - c, y0 + c, z0), (x1, y1 - c, z1), material)


def cylinder_z(parent, cx, cy, r, z0, z1, material, layers=6):
    """A round section along Z from slices."""
    step = 2 * r / layers
    for i in range(layers):
        off = -r + (i + .5) * step
        half = math.sqrt(max(.001, r * r - off * off))
        box(parent, (cx - half, cy - r + i * step, z0), (cx + half, cy - r + (i + 1) * step + .02, z1), material)


def m240(parent, side, x, z):
    """An M240H on its pintle at (x, 34, z): gun_<side>_mount turns about the post, gun_<side> elevates about its
    trunnion (GUN_TRUNNION_Y). Drawn pointing forward (-Z), the rest the renderer turns from; the belt feeds from the
    gun's left (-X as authored, its left once mirrored)."""
    mount = bone(f"gun_{side}_mount", parent, (x, 34.0, z))
    box(mount, (x - .9, 34.0, z - .9), (x + .9, 35.0, z + .9), "metal")                 # the pintle's swivel
    box(mount, (x - 1.3, 35.0, z - .7), (x - .9, 37.2, z + .7), "metal")                # the cradle's cheeks
    box(mount, (x + .9, 35.0, z - .7), (x + 1.3, 37.2, z + .7), "metal")
    gun = bone(f"gun_{side}", mount, (x, GUN_TRUNNION_Y, z))
    y = GUN_TRUNNION_Y
    box(gun, (x - .9, y - 1.0, z - 3.0), (x + .9, y + 1.4, z + 4.0), "black")          # receiver
    box(gun, (x - 1.0, y + 1.4, z - 2.0), (x + 1.0, y + 1.9, z + 2.6), "black")         # feed cover
    box(gun, (x - .25, y + 1.9, z - 5.0), (x + .25, y + 2.5, z - 3.2), "metal")        # carrying handle
    box(gun, (x - .25, y + 1.4, z - 5.0), (x + .25, y + 1.9, z - 4.6), "metal")
    cylinder_z(gun, x, y + .45, .45, z - 13.0, z - 3.0, "black", layers=3)              # barrel
    box(gun, (x - .3, y - .6, z - 10.0), (x + .3, y - .1, z - 3.0), "black")            # gas tube
    box(gun, (x - .6, y - .15, z - 14.6), (x + .6, y + 1.05, z - 13.0), "black")        # flash hider
    box(gun, (x - .15, y + .9, z - 12.6), (x + .15, y + 1.6, z - 12.1), "black")        # front sight
    box(gun, (x - 1.7, y - 1.0, z + 4.0), (x + 1.7, y - .2, z + 4.8), "black")          # spade grips' bar
    for gx in (-1.7, 1.1):
        box(gun, (x + gx, y - 2.6, z + 4.0), (x + gx + .6, y - .2, z + 4.8), "black")
    box(gun, (x - .2, y - 1.6, z + 4.1), (x + .2, y - 1.0, z + 4.6), "metal")          # butterfly trigger
    box(gun, (x - 4.2, y - 3.6, z - 2.0), (x - 1.2, y + .4, z + 2.2), "green_dark")    # 200-round ammo can
    box(gun, (x - 4.0, y + .4, z - 1.6), (x - 1.4, y + .7, z + 1.8), "metal")
    box(gun, (x - 1.4, y, z - .6), (x - .9, y + 1.0, z + .8), "black")                 # the belt into the feed tray


def wheel(parent, x, y, z, r, width, layers=6):
    step = 2 * r / layers
    for i in range(layers):
        off = -r + (i + .5) * step
        half = math.sqrt(max(.001, r * r - off * off))
        box(parent, (x - width / 2, y - r + i * step, z - half), (x + width / 2, y - r + (i + 1) * step + .02, z + half), "rubber")
    box(parent, (x - width / 2 - .1, y - r * .4, z - r * .4), (x + width / 2 + .1, y + r * .4, z + r * .4), "hub")


def wall_openings(side):
    """The holes in a cabin side: (z0, z1, y0, y1, glass?). Portholes on both; the crew door on the right (its sliding
    door is a bone of its own), the left door gunner's window on the left (its sliding pane is a bone too)."""
    holes = [(pz - 4.0, pz + 4.0, 33.0, 41.0, True) for pz in PORTHOLES]
    if side > 0:
        holes.append((DOOR_Z[0], DOOR_Z[1], FLOOR_Y, DOOR_TOP, False))
    else:
        holes.append((GUN_WINDOW_Z[0], GUN_WINDOW_Z[1], GUN_WINDOW_Y[0], GUN_WINDOW_Y[1], False))
    return holes


def spans_without(y0, y1, cuts):
    """[y0, y1] with the given (a, b) ranges taken out."""
    spans, at = [], y0
    for a, b in sorted(cuts):
        if a > at:
            spans.append((at, min(a, y1)))
        at = max(at, b)
    if at < y1:
        spans.append((at, y1))
    return [(a, b) for a, b in spans if b - a > 0.05]


def cabin_wall(parent, side):
    """One cabin side between the bulkhead and the ramp hinge, built round real openings (glass in the portholes)."""
    # The inner face first on both sides: on the left it was -OUT_HALF, which built the lining and its ribs at (and
    # through) the outer skin, so the quilting showed on the outside (the user, 2026-11-21).
    x_in, x_out = (IN_HALF, OUT_HALF) if side > 0 else (-IN_HALF, -OUT_HALF)
    lo, hi = min(x_in, x_out), max(x_in, x_out)
    holes = wall_openings(side)
    cuts_z = sorted({CABIN_FRONT, CABIN_REAR, *[h[0] for h in holes], *[h[1] for h in holes]})
    lin = (x_in - .35, x_in) if side > 0 else (x_in, x_in + .35)
    for za, zb in zip(cuts_z, cuts_z[1:]):
        here = [h for h in holes if h[0] <= za + 1e-6 and zb <= h[1] + 1e-6]
        # The wall's skin and glass are mesh (mesh.py build_sides).
        # The quilted lining inside, broken round the openings.
        for ya, yb in spans_without(FLOOR_Y + 8.0, CABIN_TOP - .5, [(h[2] - .5, h[3] + .5) for h in here]):
            box(parent, (min(lin), ya, za + .2), (max(lin), yb, zb - .2), "quilt")
    # Frames (ribs) every metre, standing proud of the lining, broken where they cross an opening.
    for k in range(CELLS_Z + 1):
        rz = CABIN_FRONT + k * (CABIN_REAR - CABIN_FRONT) / CELLS_Z
        rib = (x_in - 1.2, x_in) if side > 0 else (x_in, x_in + 1.2)
        crossed = [(h[2], h[3]) for h in holes if h[0] - .6 < rz < h[1] + .6]
        for ya, yb in spans_without(FLOOR_Y, CABIN_TOP, crossed):
            box(parent, (min(rib), ya, rz - .6), (max(rib), yb, rz + .6), "frame")


def create_model():
    bone("root", None)
    bone("airframe", "root")
    bone("cabin", "root")
    a, c = "airframe", "cabin"

    # The nose, the cockpit's skin and glazing are mesh (mesh.py); its floor, instruments and seats are boxes.
    box(a, (-20.0, FLOOR_Y - 1.0, -116.0), (20.0, FLOOR_Y, -81.0), "floor")
    # The CH-47F's cockpit (its faces are drawn by Ch47CockpitLayer, from make_cockpit.py's textures; COCKPIT gives their
    # places in the grown model): the instrument panel with five displays under a glare shield, the centre console
    # between the seats, the overhead console, two seats.
    box(a, *COCKPIT["panel"], "panel", raw=True)
    box(a, *COCKPIT["glare"], "black", raw=True)
    box(a, *COCKPIT["console"], "panel", raw=True)
    box(a, *COCKPIT["overhead"], "panel", raw=True)
    for sx in (-1, 1):
        x = sx * 8.0
        box(a, (x - 3.5, FLOOR_Y, -106.0), (x + 3.5, FLOOR_Y + 8.0, -100.0), "frame")
        box(a, (x - 3.5, FLOOR_Y + 8.0, -101.5), (x + 3.5, FLOOR_Y + 20.0, -100.0), "frame")
    # The bulkhead between cockpit and cabin, with its companionway.
    box(a, (-20.5, FLOOR_Y, CABIN_FRONT - 1.0), (-6.0, CABIN_TOP, CABIN_FRONT), "frame")
    box(a, (6.0, FLOOR_Y, CABIN_FRONT - 1.0), (20.5, CABIN_TOP, CABIN_FRONT), "frame")
    box(a, (-6.0, 44.0, CABIN_FRONT - 1.0), (6.0, CABIN_TOP, CABIN_FRONT), "frame")

    # The cabin's skin, the roof tunnel and the sponsons are mesh (mesh.py); inside, the lining and frames are boxes.
    cabin_wall(a, 1)
    cabin_wall(a, -1)
    # The crew door (right, forward): rails above and below the opening, running aft for it to slide along.
    # A split door, as the real one: the upper half slides aft on these rails, the lower half folds out and down on a
    # hinge at the sill into the boarding steps (the treads on its inner face).
    for y in (DOOR_STEP_TOP - .8, DOOR_TOP + .3):
        box(a, (OUT_HALF, y, DOOR_Z[0] - .5), (OUT_HALF + 1.0, y + .5, DOOR_Z[1] + DOOR_SLIDE + .5), "metal")
    box(a, (OUT_HALF, FLOOR_Y - 3.5, DOOR_Z[0]), (OUT_HALF + 2.2, FLOOR_Y - 2.6, DOOR_Z[1]), "metal")  # the boarding step
    door = bone("crew_door", a, (OUT_HALF + .9, DOOR_STEP_TOP, DOOR_Z[0]))
    dx0, dx1 = OUT_HALF + .3, OUT_HALF + .9
    step = bone("crew_door_step", a, (dx1, FLOOR_Y - .3, (DOOR_Z[0] + DOOR_Z[1]) / 2))
    for ty in (FLOOR_Y + 2.5, FLOOR_Y + 6.5):
        box(step, (dx0 - .35, ty, DOOR_Z[0] + 1.0), (dx0, ty + 1.4, DOOR_Z[1] - 1.0), "tread")
    box(step, (dx0 - .3, FLOOR_Y + 9.6, DOOR_Z[0] + 5.0), (dx0, FLOOR_Y + 10.4, DOOR_Z[1] - 5.0), "metal")  # its grab
    w_lo, w_hi = 33.0, 41.0
    # The door's panel and window are mesh (mesh.py); its handle is a box.
    box(door, (dx1, 28.0, DOOR_Z[0] + 1.0), (dx1 + .35, 29.0, DOOR_Z[0] + 3.5), "metal")     # its handle
    # The left door gunner's window (the forward escape hatch): a glass pane that slides aft on its own rails.
    for y in (GUN_WINDOW_Y[0] - .6, GUN_WINDOW_Y[1] + .1):
        box(a, (-OUT_HALF - .8, y, GUN_WINDOW_Z[0] - .5), (-OUT_HALF, y + .5, GUN_WINDOW_Z[1] + 11.5), "metal")
    pane = bone("gun_window", a, (-OUT_HALF - .6, GUN_WINDOW_Y[0], GUN_WINDOW_Z[0]))
    box(pane, (-OUT_HALF - .7, GUN_WINDOW_Y[0], GUN_WINDOW_Z[0]), (-OUT_HALF - .2, GUN_WINDOW_Y[1], GUN_WINDOW_Z[1]), "glass")
    box(pane, (-OUT_HALF - .75, GUN_WINDOW_Y[0], GUN_WINDOW_Z[0]), (-OUT_HALF - .15, GUN_WINDOW_Y[1], GUN_WINDOW_Z[0] + 1.0), "frame")
    box(pane, (-OUT_HALF - .75, GUN_WINDOW_Y[0], GUN_WINDOW_Z[1] - 1.0), (-OUT_HALF - .15, GUN_WINDOW_Y[1], GUN_WINDOW_Z[1]), "frame")
    # The two door guns' pintle posts, inside the door and the window (the M240s come with the gunners' seats).
    for sx, z in ((1, (DOOR_Z[0] + DOOR_Z[1]) / 2), (-1, (GUN_WINDOW_Z[0] + GUN_WINDOW_Z[1]) / 2)):
        x = sx * (IN_HALF - 1.5)
        box(a, (x - .6, FLOOR_Y, z - .6), (x + .6, 33.0, z + .6), "metal")
        box(a, (x - 1.4, 33.0, z - 1.4), (x + 1.4, 34.0, z + 1.4), "metal")
        m240(a, "r" if sx > 0 else "l", x, z)
        # The gunner's jump seat, inboard of the gun (seats 2 and 3, data x 0.775): the only seats in the cabin.
        gx = sx * 12.4
        box(a, (gx - 3.0, FLOOR_Y + 6.4, z - 3.0), (gx + 3.0, FLOOR_Y + 7.0, z + 3.0), "webbing")
        box(a, (gx - .5, FLOOR_Y, z - .5), (gx + .5, FLOOR_Y + 6.4, z + .5), "frame")
        box(a, (gx - 2.0, FLOOR_Y, z - 2.0), (gx + 2.0, FLOOR_Y + .4, z + 2.0), "frame")
    # The pylons, the rear fuselage, the engines and the markings are mesh (mesh.py).
    # ---- Rotor heads and masts.
    for name, (hx, hy, hz), mast_from in (("front", FWD_HUB, 64.5), ("rear", AFT_HUB, 86.5)):
        # The mast, the hub and the blade grips are mesh (mesh.py build_rotor_heads); the bones stay.
        rotor = bone(f"rotor_{name}", "root", (hx, hy, hz))
        phase = 0.0 if name == "front" else 60.0
        for i in range(3):
            angle = phase + 120.0 * i
            blade = bone(f"rotor_{name[0]}_blade_{i}", rotor, (hx, hy, hz), rotation=(0, angle, 0))
            # Each blade along +X from the hub in its own bone, drooping at rest (the renderer cones it in flight).
            root_x = hx + 4.0
            # The blade itself is mesh (mesh.py): an aerofoil, twisted, drooping, with its tip band.

    # ---- Landing gear: dual-wheel forward legs, single-wheel aft legs, oleos up into the sponsons.
    for sx, tag in ((1, "r"), (-1, "l")):
        fx = sx * GEAR_FRONT_X
        bone(f"wheel_f{tag}", a, (fx, GEAR_R, GEAR_FRONT_Z))      # tyres and legs are mesh (mesh.py)
        rx = sx * GEAR_REAR_X
        bone(f"wheel_r{tag}", a, (rx, GEAR_R, GEAR_REAR_Z))

    # ---- The ramp, hinged at the cabin floor's aft edge, drawn level here (the renderer raises it to closed).
    ramp = bone("ramp", "root", (0.0, FLOOR_Y, CABIN_REAR))
    box(ramp, (-IN_HALF + 1.0, FLOOR_Y, CABIN_REAR + .5), (IN_HALF - 1.0, FLOOR_Y + .25, CABIN_REAR + RAMP_LEN - .5), "floor")
    for x in (-8.0, 8.0):
        box(ramp, (x - 1.5, FLOOR_Y + .25, CABIN_REAR + .5), (x + 1.5, FLOOR_Y + .6, CABIN_REAR + RAMP_LEN - .5), "tread")
    box(ramp, (-IN_HALF, FLOOR_Y - 3.0, CABIN_REAR + RAMP_LEN), (IN_HALF, FLOOR_Y + 1.0, CABIN_REAR + RAMP_LEN + 1.5), "metal")  # the lip
    # The upper ramp door, closing the opening above the raised ramp; slides up into the pylon.
    door = bone("ramp_door", "root", (0.0, CABIN_TOP, CABIN_REAR + 16.0))
    # Its panel is mesh (mesh.py).

    # ---- The centre cargo hook under the belly, in its well (a grate in the cabin floor above).
    hook = bone("hook", a, HOOK)
    hx, hy, hz = HOOK
    box(hook, (hx - 1.5, hy - 2.5, hz - 1.0), (hx + 1.5, hy + .5, hz + 1.0), "metal")
    box(hook, (hx - .6, hy - 4.0, hz - .6), (hx + .6, hy - 2.5, hz + .6), "black")

    # ---- Cabin interior: treadway floor, roof lines, dome and jump lights, the hook-well grate. No troop benches: the
    # cabin is left empty for the players' own blocks (the user, 2026-11-21); only the door guns' seats stay.
    box(c, (-IN_HALF + .5, FLOOR_Y, CABIN_FRONT + .5), (IN_HALF - .5, FLOOR_Y + .2, CABIN_REAR), "floor")
    for x in (-8.0, 8.0):
        box(c, (x - 2.0, FLOOR_Y + .2, CABIN_FRONT + .5), (x + 2.0, FLOOR_Y + .55, CABIN_REAR), "tread")
    box(c, (-4.0, FLOOR_Y + .2, -14.0), (4.0, FLOOR_Y + .35, -6.0), "screen")              # the hook-well grate
    box(c, (-IN_HALF + .5, CABIN_TOP - .4, CABIN_FRONT + .5), (IN_HALF - .5, CABIN_TOP, CABIN_REAR - .5), "quilt")  # ceiling
    # The fittings under the ceiling are placed against the grown ceiling (raw): authored at the real size they straddle
    # the raise's line, and were stretched down from the roof a metre (the user, 2026-11-21: "what's the stuff hanging
    # from the ceiling?"), the jump lights left a block under it.
    top = grow_y(CABIN_TOP)
    for x in (-10.0, -6.0, 6.0, 10.0):                                                       # cable and hydraulic runs
        gx = grow_x(x)
        box(c, (gx - .5, top - 1.4, CABIN_FRONT + 2.0), (gx + .5, top - .4, CABIN_REAR - 2.0), "cable", raw=True)
    for k in (1, 4, 7):                                                                      # dome lights
        z = CABIN_FRONT + (k + .5) * (CABIN_REAR - CABIN_FRONT) / CELLS_Z
        box(c, (-1.5, top - 1.2, z - 1.5), (1.5, top - .4, z + 1.5), "lamp_dome", raw=True)
    for x, mat in ((-4.0, "lamp_red"), (4.0, "lamp_green")):                                # jump lights over the ramp
        gx = grow_x(x)
        box(c, (gx - 1.2, top - 3.0, CABIN_REAR - 1.0), (gx + 1.2, top - 1.0, CABIN_REAR - .4), mat, raw=True)
    box(c, (IN_HALF - 2.0, 28.0, CABIN_REAR - 6.0), (IN_HALF - .4, 36.0, CABIN_REAR - 2.0), "panel")  # ramp control panel

    # ---- Lights: anti-collision (aft pylon top, belly), nav (red left, green right, white tail), formation strips,
    # two landing lights under the nose.
    box(a, (-1.5, 87.0, 98.0), (1.5, 89.0, 101.0), "lamp_red")
    box(a, (-1.5, BELLY_Y - 1.2, 20.0), (1.5, BELLY_Y, 23.0), "lamp_red")
    box(a, (-SPONSON_HALF - .3, SPONSON_TOP - 3.5, -55.0), (-SPONSON_HALF, SPONSON_TOP - 1.5, -53.0), "lamp_red")
    box(a, (SPONSON_HALF, SPONSON_TOP - 3.5, -55.0), (SPONSON_HALF + .3, SPONSON_TOP - 1.5, -53.0), "lamp_green")
    box(a, (-1.0, 72.0, TAIL_Z + 1.4), (1.0, 74.0, TAIL_Z + 1.7), "lamp_white")
    for sx in (-1, 1):
        lo, hi = sorted((sx * OUT_HALF, sx * (OUT_HALF + .12)))
        box(a, (lo, 44.5, -60.0), (hi, 45.5, -20.0), "lamp_form")                            # fuselage strip
        lo, hi = sorted((sx * 16.3, sx * 16.42))
        box(a, (lo, 66.0, 86.0), (hi, 67.0, 96.0), "lamp_form")                              # aft pylon strip
        lo, hi = sorted((sx * 12.0, sx * 12.12))
        box(a, (lo, 54.0, -104.0), (hi, 55.0, -88.0), "lamp_form")                           # forward pylon strip
        box(a, (sx * 6.0 - 1.6, BELLY_Y - .8, -104.0), (sx * 6.0 + 1.6, BELLY_Y, -100.0), "lamp_white")  # landing lights
    # Pitot tubes on the nose, an antenna on the roof tunnel.
    for sx in (-1, 1):
        box(a, (sx * 7.0 - .3, 27.0, NOSE_Z - 2.0), (sx * 7.0 + .3, 27.6, NOSE_Z + 6.0), "metal")
    box(a, (-.3, ROOF_Y + 6.0, 10.0), (.3, ROOF_Y + 14.0, 10.6), "black")


# ---- Livery: ARMY on the forward cabin sides, the serial on the aft pylon, both black, low visibility.
GLYPHS = {
    "A": [".XXX.", "X...X", "XXXXX", "X...X", "X...X"], "R": ["XXXX.", "X...X", "XXXX.", "X..X.", "X...X"],
    "M": ["X...X", "XX.XX", "X.X.X", "X...X", "X...X"], "Y": ["X...X", ".X.X.", "..X..", "..X..", "..X.."],
    "0": [".XXX.", "X..XX", "X.X.X", "XX..X", ".XXX."], "1": ["..X..", ".XX..", "..X..", "..X..", ".XXX."],
    "6": [".XXX.", "X....", "XXXX.", "X...X", ".XXX."], "8": [".XXX.", "X...X", ".XXX.", "X...X", ".XXX."],
    "9": [".XXX.", "X...X", ".XXXX", "....X", ".XXX."], "-": [".....", ".....", "XXXX.", ".....", "....."],
}


def paint_text(px, rect, size, text, colour, height_px, flip):
    x0, y0, x1, y1 = rect
    w, h = size
    k = (x1 - x0) / w
    cell = max(1, int(round(height_px / 5.0 * k)))
    span = len(text) * 6 * cell
    left = x0 + max(0, ((x1 - x0) - span) // 2)
    top = y0 + max(0, ((y1 - y0) - 5 * cell) // 2)
    for i, ch in enumerate(text):
        rows = GLYPHS.get(ch)
        if not rows:
            continue
        for r, row in enumerate(rows):
            for cc, on in enumerate(row):
                if on != "X":
                    continue
                for dy in range(cell):
                    for dx in range(cell):
                        gx = left + (i * 6 + cc) * cell + dx
                        if flip:
                            gx = left + span - 1 - ((i * 6 + cc) * cell + dx)
                        gy = top + r * cell + dy
                        if x0 <= gx < x1 and y0 <= gy < y1:
                            px[gx, gy] = colour


def paint_livery(base, faces):
    px = base.load()
    for item in faces:
        if item["material"] != "green_label" or item["face"] not in ("east", "west"):
            continue
        w, h = item["size"]
        text = "ARMY" if w > 25 else SERIAL
        paint_text(px, item["rect"], item["size"], text, (24, 25, 22, 255), 6.0 if text == "ARMY" else 4.0, item["face"] == "west")


def geometry():
    bones = []
    for b in BONES:
        out = {k: v for k, v in b.items() if k != "cubes"}
        out["cubes"] = [{k: v for k, v in cu.items() if not k.startswith("_")} for cu in b["cubes"]]
        if not out["cubes"]:
            del out["cubes"]
        bones.append(out)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.jeg.{ID}", "texture_width": UV_SIZE[0], "texture_height": UV_SIZE[1],
                        "visible_bounds_width": 32, "visible_bounds_height": 8, "visible_bounds_offset": [0, 3, 0]},
        "bones": bones}]}


def mirror_model():
    """The model is authored with +X as the aircraft's right; the game's convention is +X its left (the vehicle guide,
    section 5, checked with a top-down screenshot). Mirror everything in X so the crew door, the pilot's seat and the
    green nav light end up on the right in game."""
    for b in BONES:
        b["pivot"][0] = -b["pivot"][0]
        if "rotation" in b:
            r = b["rotation"]
            b["rotation"] = [r[0], -r[1], -r[2]]
        for cu in b["cubes"]:
            cu["origin"][0] = round(-(cu["origin"][0] + cu["size"][0]), 4)
            if "rotation" in cu:
                r = cu["rotation"]
                cu["rotation"] = [r[0], -r[1], -r[2]]
                cu["pivot"][0] = -cu["pivot"][0]


def mx(p):
    """An authored point (+X the right) in the game's frame (+X the left)."""
    return (-p[0], p[1], p[2])


def raw_data(p):
    """A point of the built (grown) model in vehicle data."""
    return [round(float(p[0]) / 16, 4), round(float(p[1]) / 16, 4), round(-float(p[2]) / 16, 4) + 0.0]


def to_data(p):
    """An authored point, grown, in vehicle data (blocks, +Z forward)."""
    p = grow(p)
    return [round(float(p[0]) / 16, 4), round(float(p[1]) / 16, 4), round(-float(p[2]) / 16, 4) + 0.0]


def json_rows(obb):
    rows = []
    for row in obb:
        keys = ["part", "x", "y", "z", "half_width", "half_height", "half_depth"]
        rows.append("    {" + ", ".join(f'"{k}": {json.dumps(row[k] if isinstance(row[k], str) else float(row[k]))}' for k in keys) + "}")
    return rows


def bake():
    sys.path.insert(0, str(ROOT / "design/ground-vehicles"))
    import detail_texture as D
    sys.path.insert(0, str(HERE))
    import interior_paint as IP
    uv, faces, base = L.bake_unique(BONES, MATERIALS, TEXELS, boost={"green_label": 4, **IP.BOOST},
                                    fill=IP.make_fill(MATERIALS, TEXELS, L.FACE_SIZE))
    UV_SIZE[:] = [uv[0], uv[1]]
    paint_livery(base, faces)
    geo = L.resolve_zfighting(geometry())
    texture, _, _ = D.detail_image(geo, base, DETAIL)
    L.paste_faces(texture, base, faces, KEPT + ("green_label",))
    return geo, texture, faces


def main():
    create_model()
    mirror_model()
    geo, texture, faces = bake()
    by = {b["name"]: b for b in L.bones_of(geo)}
    quads, _ = L.posed_quads(geo, None, texture)
    sub = lambda name: L.subtree(by, name)
    lo, hi = L.extent(quads)
    # The tyres are mesh now: mesh.py checks they stand on the ground.
    rotors = sub("rotor_front") | sub("rotor_rear")
    body_lo, body_hi = L.extent(quads, set(by) - rotors)
    for name in ("rotor_front", "rotor_rear"):
        if not any(q[0] in sub(name) for q in quads):
            continue                      # all mesh now: mesh.py checks the blades' droop
        r_lo, _ = L.extent(quads, sub(name))
        if float(r_lo[1]) < 40.0:
            raise SystemExit(f"{name} droops to {float(r_lo[1]):.1f} px: it would strike the cabin or a person")

    def ext(names):
        return L.extent(quads, names)
    cabin_lo, cabin_hi = [-OUT_HALF, BELLY_Y, CABIN_FRONT], [OUT_HALF, ROOF_Y, CABIN_REAR + 20.0]
    nose_lo, nose_hi = [-19.0, 10.0, NOSE_Z], [19.0, 49.0, CABIN_FRONT]
    fwd_pylon = ([-12.0, 49.0, -116.0], [12.0, 66.0, -80.0])
    aft_pylon = ([-OUT_HALF, CABIN_TOP, 60.0], [OUT_HALF, 89.0, TAIL_Z])
    sponsons = ([-SPONSON_HALF, BELLY_Y, -78.0], [SPONSON_HALF, SPONSON_TOP, 60.0])
    engines = ([-NACELLE[0] - NACELLE_R, NACELLE[1] - NACELLE_R, NACELLE_Z[0]], [NACELLE[0] + NACELLE_R, NACELLE[1] + NACELLE_R, NACELLE_Z[1] + 6.0])
    # The cabin is walked in: its boxes are the shell (belly, roof, two walls, the closed ramp), never the room, so its
    # people aren't pushed out and clicks inside aren't taken by the aircraft (the FSV-6's lesson, guide section 6).
    rear_z = CABIN_REAR + 20.0
    parts = [("body", "cockpit", nose_lo, nose_hi),
             ("body", "belly", [-OUT_HALF, BELLY_Y, CABIN_FRONT], [OUT_HALF, FLOOR_Y, CABIN_REAR]),
             ("body", "roof", [-OUT_HALF, CABIN_TOP, CABIN_FRONT], [OUT_HALF, ROOF_Y + 6.0, CABIN_REAR]),
             # The right wall (-X in the file and in data) in pieces about the crew door, whose own box drops out while
             # it stands open (ChinookEntity.obbBoxes).
             ("body", "wall", [-OUT_HALF, FLOOR_Y, CABIN_FRONT], [-IN_HALF, CABIN_TOP, DOOR_Z[0]]),
             ("body", "crew door", [-OUT_HALF, FLOOR_Y, DOOR_Z[0]], [-IN_HALF, DOOR_TOP, DOOR_Z[1]]),
             ("body", "wall", [-OUT_HALF, DOOR_TOP, DOOR_Z[0]], [-IN_HALF, CABIN_TOP, DOOR_Z[1]]),
             ("body", "wall", [-OUT_HALF, FLOOR_Y, DOOR_Z[1]], [-IN_HALF, CABIN_TOP, rear_z]),
             ("body", "wall", [IN_HALF, FLOOR_Y, CABIN_FRONT], [OUT_HALF, CABIN_TOP, rear_z]),
             ("body", "sponson", [-SPONSON_HALF, BELLY_Y, -64.0], [-OUT_HALF, SPONSON_TOP, 60.0]),
             ("body", "sponson", [OUT_HALF, BELLY_Y, -64.0], [SPONSON_HALF, SPONSON_TOP, 60.0]),
             ("body", "ramp", [-IN_HALF, FLOOR_Y, CABIN_REAR], [IN_HALF, CABIN_TOP, CABIN_REAR + 16.0]),
             ("main_engine", "forward pylon", *fwd_pylon), ("main_engine", "aft pylon", *aft_pylon)]
    for sx in (-1, 1):
        parts.append(("sub_engine", "engine", [min(sx * (NACELLE[0] - NACELLE_R), sx * (NACELLE[0] + NACELLE_R)), NACELLE[1] - NACELLE_R, NACELLE_Z[0]],
                      [max(sx * (NACELLE[0] - NACELLE_R), sx * (NACELLE[0] + NACELLE_R)), NACELLE[1] + NACELLE_R, NACELLE_Z[1] + 6.0]))
    parts = [(kind, name, *grow_box(lo_, hi_)) for kind, name, lo_, hi_ in parts]
    obb = L.obb_rows(parts)

    cell_len = (CABIN_REAR - CABIN_FRONT) / CELLS_Z
    rig = {
        "model": f"jeg:entity/vehicle/{ID}", "units_per_block": 16, "front": "-Z in the model, +Z in vehicle data",
        "reference": "Boeing CH-47F Chinook, 1:1, its cabin grown to 3 x 3 m inside (grow)",
        "length_m": round((hi[2] - lo[2]) / 16, 2), "fuselage_length_m": round((TAIL_Z - NOSE_Z) / 16, 2),
        "width_m": round((body_hi[0] - body_lo[0]) / 16, 2), "height_m": round((hi[1] - lo[1]) / 16, 2),
        "rotors": [{"name": "front", "hub": to_data(FWD_HUB), "radius": ROTOR_R / 16, "blades": 3, "direction": 1, "phase": 0.0},
                   {"name": "rear", "hub": to_data(AFT_HUB), "radius": ROTOR_R / 16, "blades": 3, "direction": -1, "phase": 60.0}],
        "cabin": {"floor_y": FLOOR_Y / 16, "inside_half_width": grow_x(IN_HALF) / 16, "outside_half_width": grow_x(OUT_HALF) / 16,
                  "inside_height": (grow_y(CABIN_TOP) - FLOOR_Y) / 16,
                  "front_z": to_data((0, 0, CABIN_FRONT))[2], "rear_z": to_data((0, 0, CABIN_REAR))[2], "cells_along": CELLS_Z,
                  "cell_length": cell_len / 16},
        "ramp": {"hinge": to_data((0.0, FLOOR_Y, CABIN_REAR)), "length": RAMP_LEN / 16, "closed_deg": RAMP_CLOSED},
        "crew_door": {"z": [to_data((0, 0, DOOR_Z[1]))[2], to_data((0, 0, DOOR_Z[0]))[2]], "top": DOOR_TOP / 16, "slide": DOOR_SLIDE / 16,
                      "side": "right (-X in the model and data)"},
        "gun_window": {"z": [to_data((0, 0, GUN_WINDOW_Z[1]))[2], to_data((0, 0, GUN_WINDOW_Z[0]))[2]],
                       "y": [GUN_WINDOW_Y[0] / 16, GUN_WINDOW_Y[1] / 16], "side": "left (+X in the model and data)"},
        "door_guns": {"right": to_data(mx((IN_HALF - 1.5, GUN_TRUNNION_Y, (DOOR_Z[0] + DOOR_Z[1]) / 2))),
                      "left": to_data(mx((-(IN_HALF - 1.5), GUN_TRUNNION_Y, (GUN_WINDOW_Z[0] + GUN_WINDOW_Z[1]) / 2))),
                      "muzzle_from_trunnion": 14.6 / 16},
        "hook": to_data(HOOK), "seats": {k: to_data(mx(v)) for k, v in SEATS.items()}, "pilot_eye": to_data(mx(PILOT_EYE)),
        "lights": {"anti_collision_top": to_data((0.0, 88.0, 99.5)), "anti_collision_belly": to_data((0.0, BELLY_Y - .6, 21.5)),
                   "nav_left_red": to_data(mx((-SPONSON_HALF, SPONSON_TOP - 2.5, -54.0))),
                   "nav_right_green": to_data(mx((SPONSON_HALF, SPONSON_TOP - 2.5, -54.0))),
                   "tail_white": to_data((0.0, 81.0, TAIL_Z)), "landing": [to_data((6.0, 10.0, -102.0)), to_data((-6.0, 10.0, -102.0))]},
        "model_bounds": [raw_data(lo), raw_data(hi)], "hitbox": obb, "bones": len(BONES),
        "cuboids": sum(len(b.get("cubes", [])) for b in L.bones_of(geo)), "texture": list(texture.size),
    }
    L.write_json(HERE / "rig.json", rig)
    L.write_json(ASSETS / f"geckolib/models/entity/vehicle/{ID}.geo.json", geo)
    texture.save(ASSETS / f"textures/entity/vehicle/{ID}.png", optimize=True)
    L.write_json(ASSETS / f"geckolib/animations/entity/vehicle/{ID}.animation.json",
                 {"format_version": "1.8.0", "animations": {f"animation.{ID}.idle": {"loop": True, "bones": {}}}})
    tex_path = ASSETS / f"textures/entity/vehicle/{ID}.png"
    # Reviews and the icon come from mesh.py, which draws the boxes and the mesh together.
    if VEHICLE_JSON.exists():
        data = VEHICLE_JSON.read_text(encoding="utf-8")
        start = data.index('  "obb": [')
        end = data.index("],", start) + 2
        VEHICLE_JSON.write_text(data[:start] + '  "obb": [\n' + ",\n".join(json_rows(obb)) + "\n  ]," + data[end:], encoding="utf-8")
    print(json.dumps({k: v for k, v in rig.items() if k not in ("hitbox",)}, indent=1))


if __name__ == "__main__":
    main()
