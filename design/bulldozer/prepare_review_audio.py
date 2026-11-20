"""The BD-40's reversing beeper and a heavier horn (the user's review, 2026-11-18: "should have a reverse truck beep
noise"; "make the horn heavier and use a better, more weighted sound").

python -I design/bulldozer/prepare_review_audio.py   (after prepare_audio.py, which it overrides for these two)

- reverse: a construction truck's real back-up beeper (Bon_Vivant_Pictures 449081, CC0; 1,420 Hz), four beeps
  (1.75-4.25 s) looped; it replaces the synthesized alarm, which nobody heard.
- horn / horn_distant: a truck's air horn recorded close (goose278 174819, CC0), its steady blast (3.15-3.60 s)
  stretched into a loop by overlapping copies, lowered to 0.82 for a big machine's weight and laid over the old
  industrial horn chord for body. The distant one is low-passed with ground echo.
Voices: none in the cut stretches (measured). Please listen.
"""
from __future__ import annotations

import importlib.util
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = ROOT / "src/main/resources/assets/jeg/sounds/vehicle/bulldozer"

spec = importlib.util.spec_from_file_location("starts", ROOT / "design/ground-vehicles/prepare_starts.py")
S = importlib.util.module_from_spec(spec)
spec.loader.exec_module(S)
spec = importlib.util.spec_from_file_location("loops", ROOT / "design/ground-vehicles/prepare_audio.py")
P = importlib.util.module_from_spec(spec)
spec.loader.exec_module(P)
S.SOURCES.update({
    "backup": ("Bon_Vivant_Pictures", "449081", "Back Up BEEP Only"),
    "airhorn": ("goose278", "174819", "air horn close and loud.WAV"),
})


def seam(data, seconds):
    n = round(seconds * S.RATE)
    t = np.linspace(0, np.pi / 2, n)
    body, tail = data[:-n].copy(), data[-n:]
    body[:n] = body[:n] * np.sin(t) + tail * np.cos(t)
    return body


def stretch(core, seconds, overlap=0.12):
    """A steady tone made longer by overlapping copies of it with equal-power crossfades."""
    n = round(overlap * S.RATE)
    out = core.copy()
    t = np.linspace(0, np.pi / 2, n)
    while len(out) < seconds * S.RATE:
        head = out[:-n]
        mix = out[-n:] * np.cos(t) + core[:n] * np.sin(t)
        out = np.concatenate([head, mix, core[n:]])
    return out


def main():
    beeper = S.highpass(S.cut(S.source("backup"), 1.75, 2.5), 300.0)
    reverse = S.level(seam(beeper, 0.05), 0.16, 0.85)
    blast = S.highpass(S.cut(S.source("airhorn"), 3.15, 0.45), 40.0)
    horn = stretch(S.repitch(blast, 0.82), 3.0)
    old = P.read(OUT / "horn.ogg")
    old = np.resize(old, len(horn))
    horn = S.level(horn, 0.2) * 0.75 + S.level(old, 0.2) * 0.35
    horn = S.level(seam(horn, 0.15), 0.22, 0.9)
    distant = S.level(P.echo(S.lowpass(horn, 700.0)), 0.12, 0.8)
    S.write(OUT / "reverse.ogg", reverse)
    S.write(OUT / "horn.ogg", horn)
    S.write(OUT / "horn_distant.ogg", distant)
    print(f"reverse {len(reverse) / S.RATE:.2f} s, horn {len(horn) / S.RATE:.2f} s")


if __name__ == "__main__":
    main()
