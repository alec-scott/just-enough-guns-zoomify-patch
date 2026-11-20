"""The A-10's moving parts, set up on the imported model (the user, 2026-11-19: the A-10 animation upgrade).

python -I design/a10/articulate.py

The model (SBW-style, pinyin bone names) already has its control surfaces as their own bones, hinged where they should
be; nothing moved them. AbstractVehicleGeoRenderer.updateA10 drives them. In model pixels -X is the pilot's left (the red
light is there, AircraftLights), so the pinyin names read backwards for the sides:

- decelerons, split upper and lower, hinged on the wing's trailing edge: right wing `wingLR` (upper), `wingLR2` (lower),
  under `zuoyi`; left wing `wingRR`, `wingRR2`, under `youyi`. They deflect together as ailerons and split apart as the
  speed brakes;
- elevators: `wingLB` (right), `wingRB` (left), on `hengweiyi`;
- rudders: `weiyiL` (right fin), `weiyiR` (left fin), on `tailWing`;
- the canopy: `bone61` (its frame: the bow at the back and the two rails forward; the model has no glass), under the nose
  section `bone2`. Its pivot was in the middle of the frame; this script moves it to the hinge at the foot of the bow, so
  the canopy lifts at the front as the A-10's does. A bone with no rotation of its own: moving its pivot moves nothing.

Idempotent: run it again after re-importing the model.
"""
import json
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
GEO = ROOT / "src/main/resources/assets/jeg/geckolib/models/entity/vehicle/a10.geo.json"
# The foot of the canopy's bow (its two uprights stand from y 50.87, at z -72.74 .. -71.12), on the centre line.
CANOPY_HINGE = [0.0, 51.0, -71.9]


def main():
    text = GEO.read_text(encoding="utf-8")
    bones = {b["name"]: b for b in json.loads(text)["minecraft:geometry"][0]["bones"]}
    for name in ("wingLR", "wingLR2", "wingRR", "wingRR2", "wingLB", "wingRB", "weiyiL", "weiyiR", "bone61"):
        assert name in bones, name
    canopy = bones["bone61"]
    assert not any(canopy.get("rotation", [0, 0, 0])), "the canopy bone must not be rotated itself"
    if [float(v) for v in canopy["pivot"]] != CANOPY_HINGE:
        # Only the pivot line changes: the file keeps Blockbench's tab layout (a full rewrite reformats every line).
        old = '"pivot": [' + ", ".join(f"{v:g}" for v in canopy["pivot"]) + "]"
        start = text.index('"name": "bone61"')
        at = text.index(old, start)
        assert at - start < 120, "the canopy's pivot line was not found next to its name"
        text = text[:at] + '"pivot": [' + ", ".join(f"{v:g}" for v in CANOPY_HINGE) + "]" + text[at + len(old):]
        GEO.write_text(text, encoding="utf-8", newline="")
    print("canopy hinge", CANOPY_HINGE)


if __name__ == "__main__":
    main()
