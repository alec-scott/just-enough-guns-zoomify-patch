"""The GAU-8 as its pilot hears it, louder (the user's review, 2026-11-19: "gun sound inside the cockpit of the A10
should be louder").

python -I design/a10/cockpit_gun.py

The cockpit gun (fire_cockpit.ogg, and its run-down gun_stop_cockpit.ogg) was mixed at half the outside gun's loudness
(RMS 0.42 against 0.83), and a sound played inside the cockpit can't go above full volume in the game, so the file
itself is raised: driven into a soft saturation (tanh) that lifts its body about 4.5 dB without clipping, its peak kept
at 0.8 so Vorbis doesn't overshoot. The loop stays seamless (each sample is changed on its own). The files as they were
are kept in design/a10/cockpit_gun_base/ the first time, and the script always works from those.
"""
import importlib.util
import shutil
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
spec = importlib.util.spec_from_file_location("starts", ROOT / "design/ground-vehicles/prepare_starts.py")
S = importlib.util.module_from_spec(spec)
spec.loader.exec_module(S)
SOUNDS = ROOT / "src/main/resources/assets/jeg/sounds/vehicle/a10"
BASE = HERE / "cockpit_gun_base"
DRIVE = 3.0


def main():
    BASE.mkdir(exist_ok=True)
    for name in ("fire_cockpit", "gun_stop_cockpit"):
        base = BASE / f"{name}.ogg"
        if not base.exists():
            shutil.copyfile(SOUNDS / f"{name}.ogg", base)
        data, rate = S.sf.read(base, always_2d=True)
        data = np.mean(data, axis=1)
        before = float(np.sqrt(np.mean(data ** 2)))
        x = data / max(1e-9, float(np.max(np.abs(data))))
        out = np.tanh(x * DRIVE) / np.tanh(DRIVE) * 0.8
        if rate != S.RATE:
            raise SystemExit(f"{name}: {rate} Hz, expected {S.RATE}")
        S.write(SOUNDS / f"{name}.ogg", out)
        print(name, f"rms {before:.3f} -> {float(np.sqrt(np.mean(out ** 2))):.3f}")


if __name__ == "__main__":
    main()
