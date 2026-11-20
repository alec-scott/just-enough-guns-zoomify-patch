"""Movement sounds shared by the ground vehicles and guns (VehicleMovementAudio; the user's rule of 2026-11-17: every
movement of a vehicle makes a real, fitting sound).

python -I design/ground-vehicles/prepare_movement.py

Writes sounds/vehicle/movement/:
- traverse: a turret's electric traverse drive under load (an electric actuator straining under load, mpuffenbarger),
  a 12.5 s loop; the armoured vehicles' turrets.
- elevation: the lighter actuator (mpuffenbarger), a 5 s loop; the guns elevating.
- servo: the same actuator raised to 1.35, a quick servo; the HPJ-11 and the sentry slewing.
- handwheel: a heavy geared spindle turning (kalhan), a 20 s loop; the towed guns laid by hand.
- brake: a truck's air brakes releasing (Filmscore, the hiss at 3.8 s); brake_heavy, the same lowered to 0.75 for the
  tracked hulls.
- bump: a heavy metal thud on the ground (7of9Designs); a hull landing hard or bottoming out.
- splash: a big splash into water (Bird_man); a hull driving into a river.

Every source page is checked for its CC0 licence. Measured (we can't listen): no speech-like modulation above 0.2 in any
cut, the actuators steady to within 1 dB, the thud's centroid 116 Hz. Please listen.

Sources are cached under build/ground-vehicle-sources/. Requires numpy, scipy and soundfile.
"""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path

import numpy as np
from scipy import signal

HERE = Path(__file__).resolve().parent
_spec = importlib.util.spec_from_file_location("starts", HERE / "prepare_starts.py")
starts = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(starts)
RATE, sf = starts.RATE, starts.sf
OUT = starts.SOUNDS / "movement"

starts.SOURCES.update({
    "actuator_load": ("mpuffenbarger", "683806", "SFX Electric ACtuator Under Load 1.mp3"),
    "actuator": ("mpuffenbarger", "683807", "SFX Electric Actuator 2.mp3"),
    "spindle": ("kalhan", "613993", "Spinning Metal Spindle (Heavy Rattle)"),
    "brakes": ("Filmscore", "823069", "Hydraulic Truck Brakes"),
    "thud": ("7of9Designs", "640204", "Heavy Metal Thud on Ground"),
    "splash": ("Bird_man", "316744", "Big Splash.wav"),
})


def band(data, low, high):
    return signal.sosfilt(signal.butter(3, [low, high], fs=RATE, btype="bandpass", output="sos"), data)


def loop(data, seam=0.5):
    """Closes the seam: the tail crossfaded (equal power) into the head."""
    n = round(seam * RATE)
    t = np.linspace(0, np.pi / 2, n)
    body, tail = data[:-n].copy(), data[-n:]
    body[:n] = body[:n] * np.sin(t) + tail * np.cos(t)
    return body


def main():
    take = {key: starts.source(key) for key in ("actuator_load", "actuator", "spindle", "brakes", "thud", "splash")}
    cut, level, fade, repitch = starts.cut, starts.level, starts.fade, starts.repitch
    clips = {
        "traverse": (level(loop(band(cut(take["actuator_load"], 1.5, 12.5), 60, 9000)), 0.10), True),
        "elevation": (level(loop(band(cut(take["actuator"], 1.0, 5.0), 80, 9000)), 0.08), True),
        "servo": (level(loop(band(repitch(cut(take["actuator"], 1.0, 5.0), 1.35), 120, 10000)), 0.07), True),
        "handwheel": (level(loop(band(cut(take["spindle"], 5.0, 20.0), 60, 9000)), 0.09), True),
        "brake": (level(fade(band(cut(take["brakes"], 3.8, 1.6), 200, 12000), 0.01, 0.5), 0.10), False),
        "brake_heavy": (level(fade(band(repitch(cut(take["brakes"], 3.8, 1.6), 0.75), 150, 9000), 0.01, 0.6), 0.10), False),
        "bump": (level(fade(band(take["thud"], 30, 6000), 0.002, 0.15), 0.16, 0.95), False),
        "splash": (level(fade(band(take["splash"], 60, 12000), 0.005, 0.3), 0.14, 0.95), False),
    }
    OUT.mkdir(exist_ok=True)
    report = {}
    for name, (data, looped) in clips.items():
        starts.write(OUT / f"{name}.ogg", data)
        decoded, _ = sf.read(OUT / f"{name}.ogg")
        report[name] = {"s": round(len(data) / RATE, 2), "loop": looped,
                        "seam_jump": round(float(abs(decoded[-1] - decoded[0])), 4) if looped else None}
    lines = ["# Sound credits: movement", ""]
    for name, key in (("traverse", "actuator_load"), ("elevation, servo", "actuator"), ("handwheel", "spindle"),
                      ("brake, brake_heavy", "brakes"), ("bump", "thud"), ("splash", "splash")):
        author, identity, title = starts.SOURCES[key]
        lines.append(f"- `{name}`: \"{title}\" by {author}, https://freesound.org/people/{author}/sounds/{identity}/ "
                     "(CC0 1.0), cut, filtered and looped by design/ground-vehicles/prepare_movement.py")
    (OUT / "CREDITS.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    (HERE / "movement-validation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=1))


if __name__ == "__main__":
    main()
