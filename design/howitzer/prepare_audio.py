"""The 105 mm howitzer's report (design/howitzer/BRIEF.md "Sound"; fleet readiness gate G6: real recordings), replacing
the Mk42's report pitched down that stood in for it.

python -I design/howitzer/prepare_audio.py

Writes sounds/vehicle/howitzer/:
- fire.ogg, fire_2.ogg, fire_3.ogg: three real shots, heard near the gun (since 2026-11-19, the user: "the howitzer sounds
  muffled, is this the real sound?"). It was a real field gun ("69th Field Artillery Gun", InMotionAudio) but a dull
  take (0.6% of its energy above 2 kHz), and the bass lift, sub thump and hard clipping laid on it buried what crack it
  had (89% under 200 Hz). Now: two shots of a field gun and a howitzer firing an Excalibur round, both from US Army
  footage (qubodup, public domain), each with its crack and body as recorded (about 9% above 2 kHz): a 25 Hz high-pass,
  the low end lifted 3 dB, a gentle limiter for the peaks; the Excalibur shot, short, rolls on into the army battery's
  low tail.
- fire_interior.ogg, _2, _3: what the crew at the open gun hears: the same shots, the low end lifted a little more for
  the blast in the chest, no highs taken off.
- fire_distant_1..3.ogg: a US Army artillery piece firing three rounds in the field, from Army footage ("Artillery
  Gunfire", qubodup, CC0): each round with its natural roll off the ground, low-passed to what carries.

The loading sound stays the Mk42's (SBW, a breech and rammer). Every source page is checked for its CC0 licence
(prepare_starts.source).
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
OUT = starts.SOUNDS / "howitzer"

starts.SOURCES.update({
    "field_gun": ("InMotionAudio", "702245", "GUNArtl_69thFieldArtilleryGun01_InMotionAudio_FREESampleSunday.wav"),
    "army_arty": ("qubodup", "239137", "Artillery Gunfire"),
    "army_cannon": ("qubodup", "187767", "Cannon Shot"),
    "excalibur": ("qubodup", "175430", "Excalibur Howitzer Shot"),
})

CREDITS = """# 105 mm howitzer sounds

Prepared by `design/howitzer/prepare_audio.py`.

- `fire.ogg`, `fire_2.ogg`, `fire_interior.ogg`, `fire_interior_2.ogg` (since 2026-11-19): "Cannon Shot" by qubodup,
  https://freesound.org/people/qubodup/sounds/187767/ (CC0 1.0; from US Army footage), its two shots as recorded, the
  low end lifted (more for the crew), gently limited.
- `fire_3.ogg`, `fire_interior_3.ogg`: "Excalibur Howitzer Shot" by qubodup,
  https://freesound.org/people/qubodup/sounds/175430/ (CC0 1.0; from US Army footage), rolling on into the tail of
  "Artillery Gunfire" (below), low-passed.
- Was (until 2026-11-19): InMotionAudio's "69th Field Artillery Gun" (702245) with its bass lifted, a sub thump and
  hard clipping: it sounded muffled.
- `fire_distant_1..3.ogg`: "Artillery Gunfire" by qubodup, https://freesound.org/people/qubodup/sounds/239137/ (CC0 1.0;
  from US Army footage). Its three rounds, each with its roll-off, low-passed (1 kHz).
- Loading: the Mk42's (`../mk42/`).
"""


def band(data, low, high):
    return signal.sosfilt(signal.butter(3, [low, high], fs=RATE, btype="bandpass", output="sos"), data)


def lowpass(data, cut):
    return signal.sosfilt(signal.butter(4, cut, fs=RATE, btype="lowpass", output="sos"), data)


def natural(shot, low_lift):
    """A shot kept as recorded: 25 Hz high-pass, the low end (25-120 Hz) lifted by {low_lift}, a gentle limiter that
    rounds only the peaks (the crack stays), levelled to the peak."""
    x = starts.highpass(shot, 25.0)
    x = x + band(x, 25, 120) * low_lift
    x = x / max(1e-9, np.max(np.abs(x)))
    x = np.tanh(x * 1.6) / np.tanh(1.6)
    return x * 0.7  # Vorbis overshoots a sharp crack (the sources clip): 0.9 and 0.8 decoded past 0.98


def main():
    army = starts.source("army_arty")
    cannon, excalibur = starts.source("army_cannon"), starts.source("excalibur")
    cut, fade, level = starts.cut, starts.fade, starts.level
    # The Excalibur shot is short (0.8 s): it rolls on into the army battery's tail, low-passed, as a shot does across
    # open ground.
    tail = lowpass(band(cut(army, 0.3, 2.6), 25, 12000), 700)
    long_shot = np.zeros(int(0.35 * RATE) + len(tail))
    long_shot[:len(excalibur)] += excalibur / max(1e-9, np.max(np.abs(excalibur)))
    long_shot[int(0.35 * RATE):] += tail / max(1e-9, np.max(np.abs(tail))) * 0.35
    shots = [cut(cannon, 0.08, 2.45), cut(cannon, 6.33, 2.65), long_shot]
    clips = {}
    for i, shot in enumerate(shots, 1):
        suffix = "" if i == 1 else f"_{i}"
        clips["fire" + suffix] = fade(natural(shot, 0.4), 0.002, 0.8)
        clips["fire_interior" + suffix] = fade(natural(shot, 0.8), 0.002, 0.8)
    for i, (start, length) in enumerate(((0.0, 2.95), (3.0, 2.05), (5.05, 1.85)), 1):
        clips[f"fire_distant_{i}"] = level(fade(lowpass(band(cut(army, start, length), 25, 12000), 1000), 0.02, 0.6), 0.14, 0.85)
    OUT.mkdir(parents=True, exist_ok=True)
    report = {}
    for name, data in clips.items():
        path = OUT / f"{name}.ogg"
        starts.write(path, data)
        decoded, _ = sf.read(path)
        report[name] = {"s": round(len(decoded) / RATE, 2), "peak": round(float(np.max(np.abs(decoded))), 3)}
    (OUT / "CREDITS.md").write_text(CREDITS, encoding="utf-8")
    (HERE / "audio-validation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
