"""The TOW's launch (design/tow; fleet readiness gate G6: real recordings), replacing the BMP-2's missile sound that
stood in for it.

python -I design/tow/prepare_audio.py

Writes sounds/vehicle/tow/launch.ogg: a real BGM-71 TOW launch (qubodup, from US military footage, CC0; 0.6 s, the
launch motor's crack and the flight motor lighting) over the roar and echo of an anti-tank missile leaving the tube on a
US Army range ("Launching Anti-Tank Missiles", qubodup, CC0): the TOW clip on top, the range recording's first launch
under it from 0.15 s, so the motor's roar rolls away over 3 s as it does outdoors.

The reload keeps the mod's own launcher handling sounds (lid open, round in, lid closed). Every source page is checked
for its CC0 licence (prepare_starts.source).
"""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path

import numpy as np
from scipy import signal

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
_spec = importlib.util.spec_from_file_location("starts", ROOT / "design/ground-vehicles/prepare_starts.py")
starts = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(starts)
RATE, sf = starts.RATE, starts.sf
OUT = starts.SOUNDS / "tow"

starts.SOURCES.update({
    "tow": ("qubodup", "67543", "bgm-71_tow_missile_launch_1_no_echo.flac"),
    "range": ("qubodup", "184274", "Launching Anti-Tank Missiles.flac"),
})

CREDITS = """# TOW sounds

Prepared by `design/tow/prepare_audio.py`.

- `launch.ogg`: "bgm-71_tow_missile_launch_1_no_echo.flac" by qubodup, https://freesound.org/people/qubodup/sounds/67543/
  (CC0 1.0; from US military footage), over "Launching Anti-Tank Missiles.flac" by qubodup,
  https://freesound.org/people/qubodup/sounds/184274/ (CC0 1.0; from US Government footage): the range recording's
  first launch, from 0.15 s, for the motor's roar and echo. Band-passed and levelled.
"""


def band(data, low, high):
    return signal.sosfilt(signal.butter(3, [low, high], fs=RATE, btype="bandpass", output="sos"), data)


def rms(data):
    return float(np.sqrt(np.mean(data ** 2)))


def main():
    tow, rng = starts.source("tow"), starts.source("range")
    cut, fade, level = starts.cut, starts.fade, starts.level
    crack = band(tow, 40, 16000)
    roar = fade(band(cut(rng, 0.0, 3.6), 40, 14000), 0.15, 1.6)
    # The TOW clip on top at the range launch's own level, the roar under it from where the clip's crack has passed.
    crack = crack * (rms(roar[: len(crack)]) / max(rms(crack), 1e-9))
    mix = roar * np.clip(np.arange(len(roar)) / (0.15 * RATE), 0.35, 1.0)
    mix[: len(crack)] += crack
    launch = level(fade(mix, 0.002, 1.6), 0.14, 0.9)
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / "launch.ogg"
    starts.write(path, launch)
    decoded, _ = sf.read(path)
    report = {"launch": {"s": round(len(decoded) / RATE, 2), "peak": round(float(np.max(np.abs(decoded))), 3)}}
    (OUT / "CREDITS.md").write_text(CREDITS, encoding="utf-8")
    (HERE / "audio-validation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
