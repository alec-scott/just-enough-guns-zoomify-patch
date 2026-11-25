"""Louder, heavier CH-47 engine sounds (the user, 2026-11-25: "startup sound and the actual flying sound should be
louder; the flying sound should feel heavier like a heavy chopper").

python -I design/chinook/heavier_engine.py

Works on the files prepare_audio.py made (kept the first time under build/ch47-engine-originals/, so it can be run
again): they sat 7-10 dB under the AH-6's and Mi-28's engine loops (flight RMS -17 dB against -10 and -7) and the
start 15 dB under.
- flight.ogg: lowered 7% (a bigger, slower rotor; resampled in the frequency domain, which keeps the loop seamless), its
  body raised (+3.5 dB at 45-140 Hz, the blades' thump and the transmission's drone), the hiss above 6 kHz eased 3 dB,
  then levelled to RMS -9.5 dB with a soft limiter. Every filter runs circularly round the loop.
- startup.ogg, shutdown.ogg: levelled up 6-7 dB (to RMS -14.5 and -16 dB, under a smooth limiter), the start lows +3 dB.
"""
from __future__ import annotations

import shutil
import sys
from pathlib import Path

import numpy as np
from scipy import signal

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "build/audio-tools-local"))
import soundfile as sf  # noqa: E402

RATE = 44100
OUT = ROOT / "src/main/resources/assets/jeg/sounds/vehicle/ch47"
KEEP = ROOT / "build/ch47-engine-originals"


def original(name):
    KEEP.mkdir(parents=True, exist_ok=True)
    kept = KEEP / name
    if not kept.exists():
        shutil.copy2(OUT / name, kept)
    data, rate = sf.read(kept, always_2d=True)
    assert rate == RATE
    return np.mean(data, axis=1)


def circular(data, sos):
    """A filter run round a loop: on three copies, keeping the middle one, so the seam stays seamless."""
    n = len(data)
    return signal.sosfilt(sos, np.concatenate([data, data, data]))[n:2 * n]


def shelf_band(data, low, high, gain_db, loop):
    """Raise (or cut) one band by adding (or taking away) its band-passed copy."""
    sos = signal.butter(2, [low, high], "bandpass", fs=RATE, output="sos")
    part = circular(data, sos) if loop else signal.sosfilt(sos, data)
    return data + part * (10 ** (gain_db / 20.0) - 1.0)


def high_cut(data, cutoff, gain_db, loop):
    sos = signal.butter(2, cutoff, "highpass", fs=RATE, output="sos")
    part = circular(data, sos) if loop else signal.sosfilt(sos, data)
    return data + part * (10 ** (gain_db / 20.0) - 1.0)


def limit(data, rms_db, ceiling=0.89):
    """To this RMS, the peaks rounded off softly under the ceiling (a static curve: safe on a loop)."""
    data = data * (10 ** (rms_db / 20.0) / max(1e-9, np.sqrt(np.mean(data ** 2))))
    knee = ceiling * 0.6
    over = np.abs(data) > knee
    out = data.copy()
    span = ceiling - knee
    out[over] = np.sign(data[over]) * (knee + span * np.tanh((np.abs(data[over]) - knee) / span))
    return out


def level_smooth(data, rms_db, ceiling=0.8):
    """To this RMS, held under the ceiling by a smooth gain (10 ms look-ahead, 120 ms release): no squared-off peaks for
    the encoder to overshoot (the start's turbine whine, waveshaped, came out of Vorbis over full scale)."""
    data = data * (10 ** (rms_db / 20.0) / max(1e-9, np.sqrt(np.mean(data ** 2))))
    look = int(0.010 * RATE)
    peak = np.abs(data)
    held = np.maximum.reduce([np.concatenate([peak[k:], np.zeros(k)]) for k in range(0, look, 8)])
    want = np.minimum(1.0, ceiling / np.maximum(held, 1e-9))
    gain = np.empty_like(want)
    g = 1.0
    release = 1.0 - np.exp(-1.0 / (0.12 * RATE))
    for i, w in enumerate(want):
        g = w if w < g else g + (w - g) * release
        gain[i] = g
    return data * gain


def write(name, data):
    path = OUT / name
    with sf.SoundFile(path, "w", RATE, 1, format="OGG", subtype="VORBIS") as f:
        for i in range(0, len(data), 4096):
            f.write(data[i:i + 4096])
    decoded, rate = sf.read(path)
    assert rate == RATE and decoded.ndim == 1 and np.isfinite(decoded).all()
    assert np.max(np.abs(decoded)) < 0.99, (name, "clips")
    f_, p = signal.welch(decoded, RATE, nperseg=8192)
    print(f"{name}: {len(decoded) / RATE:.2f} s, RMS {20 * np.log10(np.sqrt(np.mean(decoded ** 2))):.1f} dB, "
          f"peak {20 * np.log10(np.max(np.abs(decoded))):.1f} dB, centroid {(f_ * p).sum() / p.sum():.0f} Hz, "
          f"below 150 Hz {p[f_ < 150].sum() / p.sum():.2f}")


def main():
    flight = original("flight.ogg")
    # 7% lower: resampled in the frequency domain, which treats the take as one turn of a loop.
    flight = signal.resample(flight, int(round(len(flight) / 0.93)))
    flight = shelf_band(flight, 45.0, 140.0, 3.5, loop=True)
    flight = high_cut(flight, 6000.0, -3.0, loop=True)
    write("flight.ogg", limit(flight, -9.5))

    startup = original("startup.ogg")
    startup = shelf_band(startup, 40.0, 160.0, 3.0, loop=False)
    write("startup.ogg", level_smooth(startup, -14.5))

    shutdown = original("shutdown.ogg")
    write("shutdown.ogg", level_smooth(shutdown, -16.0))


if __name__ == "__main__":
    main()
