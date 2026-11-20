"""The HPJ-11's gun sounds (design/hpj11/BRIEF.md "Sound"; fleet readiness gate G6: real recordings), played by
CiwsAudio.

python -I design/hpj11/prepare_audio.py

Writes sounds/vehicle/hpj11/:
- fire.ogg: the firing loop up close, from a real US Navy Mk 15 Phalanx CIWS fast burst (qubodup, from US Government
  footage, CC0): the steady 1.2 s of the burst, its seam crossfaded.
- fire_stop.ogg: the same burst's end, the last rounds and its 4 s ring-out and echo, played when it stops firing.
- fire_distant.ogg, fire_stop_distant.ogg: the gun from far off, from a real C-RAM (the land Phalanx) firing, recorded
  at a distance with its echo (gurkenjaeger13, CC0): the steady 2.7 s low-passed and looped, and its echo tail.
- spin_up.ogg, spin_down.ogg: the barrels spooling up and running down, from a real M134 electrically driven rotary gun
  recorded 4 feet away (rob762x51, the Garwood M134G demo, CC0): the motor winding up before the first round, and
  running down after the last.

CiwsAudio mixes the near and distant layers on the aircraft distance curve (AircraftSoundRange), so the gun carries as
far as a helicopter's. Every source page is checked for its CC0 licence (prepare_starts.source). Measured, as we cannot
listen: no clipping in the cuts, loop seams within 1 dB.
"""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path

import numpy as np
from scipy import signal

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


starts = _load("starts", ROOT / "design/ground-vehicles/prepare_starts.py")
RATE, sf = starts.RATE, starts.sf
OUT = starts.SOUNDS / "hpj11"

starts.SOURCES.update({
    "phalanx": ("qubodup", "163119", "Navy MK 15 Phalanx CIWS Anti-Air Fast Burst"),
    "cram": ("gurkenjaeger13", "621817", "C-RAM - Firing (+ Echo).mp3"),
    "m134": ("rob762x51", "165042", "Minigun Burst Audio"),
})

CREDITS = """# HPJ-11 sounds

Prepared by `design/hpj11/prepare_audio.py` (cut, filtered, levelled; loops crossfaded at the seam).

- `fire.ogg`, `fire_stop.ogg`: "Navy MK 15 Phalanx CIWS Anti-Air Fast Burst" by qubodup,
  https://freesound.org/people/qubodup/sounds/163119/ (CC0 1.0; from US Government footage). The steady burst, looped;
  the burst's end with its ring-out.
- `fire_distant.ogg`, `fire_stop_distant.ogg`: "C-RAM - Firing (+ Echo).mp3" by gurkenjaeger13,
  https://freesound.org/people/gurkenjaeger13/sounds/621817/ (CC0 1.0). The steady fire low-passed and looped; its echo.
- `spin_up.ogg`, `spin_down.ogg`: "Minigun Burst Audio" (the Garwood M134G demo) by rob762x51,
  https://freesound.org/people/rob762x51/sounds/165042/ (CC0 1.0). The motor winding up before the first round and
  running down after the last.
"""


def band(data, low, high):
    return signal.sosfilt(signal.butter(3, [low, high], fs=RATE, btype="bandpass", output="sos"), data)


def lowpass(data, cut):
    return signal.sosfilt(signal.butter(4, cut, fs=RATE, btype="lowpass", output="sos"), data)


def loop(data, seam):
    """Closes the seam: the tail crossfaded (equal power) into the head."""
    n = round(seam * RATE)
    t = np.linspace(0, np.pi / 2, n)
    body, tail = data[:-n].copy(), data[-n:]
    body[:n] = body[:n] * np.sin(t) + tail * np.cos(t)
    return body


def seam_db(data):
    w = round(0.02 * RATE)
    head, tail = np.sqrt(np.mean(data[:w] ** 2)), np.sqrt(np.mean(data[-w:] ** 2))
    return round(float(20 * np.log10(max(head, 1e-9) / max(tail, 1e-9))), 2)


def main():
    phalanx, cram, m134 = (starts.source(k) for k in ("phalanx", "cram", "m134"))
    cut, fade, level = starts.cut, starts.fade, starts.level
    clips = {
        "fire": (level(loop(band(cut(phalanx, 0.25, 1.15), 50, 15000), 0.15), 0.20), True),
        "fire_stop": (level(fade(band(cut(phalanx, 1.5, 4.4), 50, 15000), 0.01, 1.5), 0.10), False),
        "fire_distant": (level(loop(lowpass(band(cut(cram, 0.6, 2.9), 40, 8000), 1400), 0.3), 0.16), True),
        "fire_stop_distant": (level(fade(lowpass(band(cut(cram, 3.4, 5.6), 40, 8000), 1400), 0.02, 2.0), 0.08), False),
        "spin_up": (level(fade(band(cut(m134, 0.05, 0.9), 60, 14000), 0.02, 0.12), 0.08), False),
        "spin_down": (level(fade(band(cut(m134, 31.3, 1.35), 60, 14000), 0.01, 0.6), 0.07), False),
    }
    OUT.mkdir(parents=True, exist_ok=True)
    report = {}
    for name, (data, looped) in clips.items():
        path = OUT / f"{name}.ogg"
        starts.write(path, data)
        decoded, _ = sf.read(path)
        report[name] = {"s": round(len(decoded) / RATE, 2), "peak": round(float(np.max(np.abs(decoded))), 3),
                        "loop_seam_db": seam_db(decoded) if looped else None}
    (OUT / "CREDITS.md").write_text(CREDITS, encoding="utf-8")
    (HERE / "audio-validation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
