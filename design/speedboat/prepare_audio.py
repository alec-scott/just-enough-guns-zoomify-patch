"""The speedboat's real start and stop, and the water on the hull (GroundVehicleAudio; READINESS gate G13).

python -I design/speedboat/prepare_audio.py

- engine_start, engine_stop: bone666138's lobster boat starting and shutting down (one take, CC0): a marine diesel,
  the SURC's own kind of engine. The starter turns and it catches at 0.6 s (GroundVehicleSounds.startCatchTicks 12);
  the shutdown runs down from 11.3 s.
- water/hull: the rush and slap of water along a planing hull, from jazbavac's motorboat at speed on a lake (CC0,
  Sennheiser MKH 416): 18 s of the steady run at 360 s, high-passed above the engine (its hum is the engine loops'
  job), a loop. Shared by every hull that makes way through water: the speedboat, and the swimming BMP-2 and LAV-150.

Voices: none heard in the cut stretches by the syllable-rate measure used for the starts, which reads boat engines
high (their firing rate sits in the syllable band), so this one is not conclusive. Please listen.

Uses design/ground-vehicles/prepare_starts.py's licence check, cache and helpers. Requires numpy, scipy and soundfile.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "ground-vehicles"))
import prepare_starts as ps  # noqa: E402
import numpy as np  # noqa: E402
from scipy import signal  # noqa: E402

ps.SOURCES["lobster"] = ("bone666138", "398416", "Lobster Boat Starting and Stopping")
ps.SOURCES["speeding"] = ("jazbavac", "198519", "boat speeding, water.wav")
RATE = ps.RATE
START = (0.0, 3.2)  # from, length (s); catches at 0.6 s
STOP = (10.6, 2.9)
HULL = (360.0, 18.6)


def loop(data, seam=0.6):
    """Closes the loop's seam with an equal-power crossfade of its tail into its head."""
    n = round(seam * RATE)
    t = np.linspace(0, np.pi / 2, n)
    body, tail = data[:-n].copy(), data[-n:]
    body[:n] = body[:n] * np.sin(t) + tail * np.cos(t)
    return body


def main():
    lobster, speeding = ps.source("lobster"), ps.source("speeding")
    start = ps.level(ps.fade(ps.highpass(ps.cut(lobster, *START)), 0.02, 1.2), 0.12)
    stop = ps.level(ps.fade(ps.highpass(ps.cut(lobster, *STOP)), 0.3, 0.4), 0.1)
    hull = signal.sosfilt(signal.butter(3, [600, 12000], fs=RATE, btype="bandpass", output="sos"), ps.cut(speeding, *HULL))
    hull = ps.level(loop(hull), 0.1)
    out = ps.SOUNDS
    ps.write(out / "speedboat" / "engine_start.ogg", start)
    ps.write(out / "speedboat" / "engine_stop.ogg", stop)
    (out / "water").mkdir(exist_ok=True)
    ps.write(out / "water" / "hull.ogg", hull)
    decoded, _ = ps.sf.read(out / "water" / "hull.ogg")
    report = {"start_s": round(len(start) / RATE, 2), "catch_s": 0.6, "stop_s": round(len(stop) / RATE, 2),
              "hull_loop_s": round(len(hull) / RATE, 2), "hull_seam_jump": round(float(abs(decoded[-1] - decoded[0])), 4)}
    a, i, t = ps.SOURCES["lobster"]
    (out / "speedboat" / "CREDITS.md").write_text(
        "# Sound credits: speedboat\n\n"
        "- `engine_idle`, `engine_load`, `engine_distant`, `engine_cab`, `engine`: Superb Warfare's speedboat engine "
        "(used with the SBW team's permission, design/superbwarfare-port/PERMISSION.md), derived by "
        "design/ground-vehicles/prepare_audio.py\n"
        f"- `engine_start`, `engine_stop`: \"{t}\" by {a}, https://freesound.org/people/{a}/sounds/{i}/ (CC0 1.0), cut "
        "and filtered by design/speedboat/prepare_audio.py\n", encoding="utf-8")
    a, i, t = ps.SOURCES["speeding"]
    (out / "water" / "CREDITS.md").write_text(
        "# Sound credits: water\n\n"
        f"- `hull`: \"{t}\" by {a}, https://freesound.org/people/{a}/sounds/{i}/ (CC0 1.0), an 18 s loop cut from the "
        "run at speed and band-limited above the engine by design/speedboat/prepare_audio.py\n", encoding="utf-8")
    (HERE / "audio-validation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=1))


if __name__ == "__main__":
    main()
