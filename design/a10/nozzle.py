"""RETIRED (2026-11-17): the user found these nozzles worse than the model's own and the glow in them gone, so
a10.geo.json and VehicleExhaustGlowLayer are back to their yftm.20 state. Do not run this again without asking.

The A-10's exhaust nozzles: the generator for the cubes in bones houzhou / houzhou2 of a10.geo.json.

python -I design/a10/nozzle.py            # rewrite the nozzle cubes in the mod's a10.geo.json (idempotent)
python -I design/a10/nozzle.py --review   # also render rear and side review images into design/a10/

Superb Warfare's A-10 (and so ours) ends each engine nacelle in a flat black disc: the bones meant for the rear rim,
inner rim and tail cone (houwaiyuan, houneiyuan, houzhou) are empty. From behind it read as a hole, with the exhaust
glow (VehicleExhaustGlowLayer) floating in it. This adds a TF34-style rear: the cowling tapering in two steps to the
nozzle lip, a dark mouth, and a stepped tail cone standing out of it.

File (Bedrock) coordinates, pixels; the jet faces -Z, so the nozzles point +Z. Each stage is the nacelle's own
construction: four flat boxes turned 0/45/90/135 degrees about the engine axis, which together make an octagon.
"""
import copy
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
GEO = ROOT / "src/main/resources/assets/jeg/geckolib/models/entity/vehicle/a10.geo.json"
TEXTURE = ROOT / "src/main/resources/assets/jeg/textures/entity/vehicle/a10.png"

AXIS_Y = 54.0
ENGINES = {"houzhou": -26.46, "houzhou2": 26.46}
NACELLE_END_Z = 67.63          # where the nacelle body (bone zhuti) stops
TURNS = (0.0, 45.0, 90.0, 135.0)

# Uniform texture patches on a10.png (texture units = pixels, 512 square), measured from the faces that use them.
GREY = ([41.0, 109.0], [4.0, 2.0])    # nacelle paint, (166, 176, 183)
BLACK = ([123.0, 122.0], [2.0, 2.0])  # the old flat engine back, (0, 0, 0)
DARK = ([87.0, 124.0], [2.0, 2.0])    # the fan hub's dark metal, (55, 58, 64)

# (name, z0, z1, width across, thickness, side patch, rear-face patch)
STAGES = [
    ("cowl", NACELLE_END_Z - 0.1, 72.0, 21.2, 8.8, GREY, GREY),
    ("lip", 72.0, 77.0, 18.6, 7.7, GREY, BLACK),
    ("cone1", 77.0, 78.4, 7.0, 7.0, DARK, DARK),
    ("cone2", 78.4, 79.6, 4.6, 4.6, DARK, DARK),
    ("cone3", 79.6, 80.4, 2.4, 2.4, DARK, DARK),
]
# Where the glow belongs now (VehicleExhaustGlowLayer): just behind the lip's black mouth.
MOUTH_Z = 77.05
MOUTH_RADIUS_BLOCKS = 0.55


def dumps(value, depth=0):
    """Blockbench's layout (tabs, number arrays on one line), so a rewrite only shows the nozzle cubes in a diff."""
    pad, inner = "\t" * depth, "\t" * (depth + 1)
    if isinstance(value, dict):
        if not value:
            return "{\n\n" + pad + "}"
        if set(value) <= {"uv", "uv_size", "material_instance"}:  # a plain face: one line (rotated ones are not)
            return "{" + ", ".join(f"{json.dumps(k)}: {dumps(v)}" for k, v in value.items()) + "}"
        items = [f"{inner}{json.dumps(k)}: {dumps(v, depth + 1)}" for k, v in value.items()]
        return "{\n" + ",\n".join(items) + "\n" + pad + "}"
    if isinstance(value, list):
        if all(not isinstance(v, (dict, list)) for v in value):
            return "[" + ", ".join(json.dumps(v) for v in value) + "]"
        return "[\n" + ",\n".join(inner + dumps(v, depth + 1) for v in value) + "\n" + pad + "]"
    return json.dumps(value)


def face(patch):
    uv, size = patch
    return {"uv": list(uv), "uv_size": list(size)}


def stage_cubes(axis_x, z0, z1, width, thickness, side, rear):
    cubes = []
    for turn in TURNS:
        if width == thickness and turn in (90.0, 135.0):
            continue  # a square stage needs only two turns
        cube = {
            "origin": [round(axis_x - width / 2, 4), round(AXIS_Y - thickness / 2, 4), round(z0, 4)],
            "size": [width, thickness, round(z1 - z0, 4)],
            "uv": {"north": face(side), "east": face(side), "south": face(rear), "west": face(side),
                   "up": face(side), "down": face(side)},
        }
        if turn:
            cube["pivot"] = [axis_x, AXIS_Y, round(z0, 4)]
            cube["rotation"] = [0.0, 0.0, turn]
        cubes.append(cube)
    return cubes


def build(geo):
    geo = copy.deepcopy(geo)
    bones = {b["name"]: b for b in geo["minecraft:geometry"][0]["bones"]}
    for bone, axis_x in ENGINES.items():
        cubes = []
        for _, z0, z1, width, thickness, side, rear in STAGES:
            cubes += stage_cubes(axis_x, z0, z1, width, thickness, side, rear)
        bones[bone]["cubes"] = cubes
    return geo


def review(geo):
    sys.path.insert(0, str(ROOT / "design/superbwarfare-port/triage"))
    import render_geo as R
    from PIL import Image
    texture = Image.open(TEXTURE)
    quads, size, _, _ = R.load_quads(geo)
    for name, yaw, pitch in (("rear", 180, 8), ("rear-quarter", 145, 15), ("side", 90, 0)):
        R.render(quads, texture, size, yaw, pitch, size=(900, 520)).save(HERE / f"review-nozzle-{name}.png")


def main():
    raw = GEO.read_text(encoding="utf-8").replace("\r\n", "\n")
    geo = json.loads(raw)
    if dumps(geo) != raw:
        raise SystemExit("a10.geo.json is not in the layout this script writes; refusing to reformat it")
    geo = build(geo)
    GEO.write_text(dumps(geo), encoding="utf-8", newline="")
    print(f"nozzles written: {sum(len(s) for s in [stage_cubes(0, *st[1:]) for st in STAGES])} cubes per engine")
    if "--review" in sys.argv:
        review(geo)


if __name__ == "__main__":
    main()
