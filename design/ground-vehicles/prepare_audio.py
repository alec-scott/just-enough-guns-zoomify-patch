"""Prepare the armoured vehicles' engine layers for GroundVehicleAudio (fleet plan Phase 0.1, guide section 7).

python -I design/ground-vehicles/prepare_audio.py [vehicle ...]

Each vehicle has one engine recording at sounds/vehicle/<id>/engine.ogg: Superb Warfare's for the LAV-150 and the
speedboat; for the BMP-2, PLZ-05 and YX-100 a real recording of their own, cut into it first (ENGINE_FROM). From it:
- engine_load: the recording itself, made into a 16 s loop that varies (copies chained at slightly different speed and
  level, joined by equal-power crossfades, the seam closed the same way), so it doesn't repeat within a minute;
- engine_idle: (superseded for the BMP-2, YX-100, PLZ-05 and LAV-150 by prepare_idles.py, a real idle; run it after this) the same loop slowed to 0.84 (the engine turning slower) and its highs taken off, so idle and load are
  one voice for the crossfade;
- engine_cab: the load loop muffled (low-passed at 1.3 kHz) for an enclosed crew in first person;
- engine_distant: the load loop low-passed to the rumble that carries (450 Hz) with a ground-echo tail.
The track clank shared by the tracked vehicles (sounds/vehicle/tracks/clank.ogg) is the BD-40's CC0 tracks recording made
into a 12 s varying loop. Requires numpy, scipy and soundfile (the workspace's local soundfile is used if needed).
"""
from __future__ import annotations

from pathlib import Path
import random
import sys

import numpy as np
from scipy import signal

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
try:
    import soundfile as sf
except ImportError:
    sys.path.insert(0, str(ROOT / "build/audio-tools-local"))
    import soundfile as sf

SOUNDS = ROOT / "src/main/resources/assets/jeg/sounds/vehicle"
VEHICLES = ("bmp2", "lav150", "plz05", "yx100", "speedboat")
# Each tank its own working engine (the user's review, 2026-11-19: "find a separate audio recording for each tank, it
# should have its own identity"), from a real recording rather than SBW's (the PLZ-05's carried a voice, the YX-100's
# was a sci-fi hover drive, and for a while both borrowed the BMP-2's): (freesound source in prepare_starts.SOURCES,
# from, length, speed, low boost). The YX-100's turbine works at a higher pitch than it idles, its exhaust roar raised.
ENGINE_FROM = {
    "bmp2": ("apc_hum", 0.5, 29.0, 1.0, 0.0),
    "plz05": ("tank_ride", 43.0, 34.0, 1.0, 0.0),
    "yx100": ("m1a1", 12.0, 10.4, 1.12, 0.5),
}
RATE = 44100
FADE = .3


_STARTS = []


def starts():
    """prepare_starts.py, for its freesound sources (fetched, checked CC0 and cached there)."""
    if not _STARTS:
        import importlib.util
        spec = importlib.util.spec_from_file_location("starts", HERE / "prepare_starts.py")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        _STARTS.append(module)
    return _STARTS[0]


def read(path):
    data, rate = sf.read(path, always_2d=True)
    data = np.mean(data, axis=1)
    if rate != RATE:
        data = signal.resample_poly(data, RATE, rate)
    return data


def resample(data, factor):
    """Play at `factor` times the speed (pitch and tempo together, as an engine turning faster)."""
    n = max(1, int(round(len(data) / factor)))
    return np.interp(np.linspace(0, len(data) - 1, n), np.arange(len(data)), data)


def lowpass(data, cutoff, order=4):
    return signal.sosfilt(signal.butter(order, cutoff, fs=RATE, btype="lowpass", output="sos"), data)


def crossfade(a, b, seconds=FADE):
    n = min(int(seconds * RATE), len(a), len(b))
    t = np.linspace(0, np.pi / 2, n)
    return np.concatenate([a[:-n], a[-n:] * np.cos(t) + b[:n] * np.sin(t), b[n:]])


def varying_loop(data, seconds, seed):
    """Chain copies at 0.97-1.03 speed and 0.92-1.05 level until `seconds` long, then close the seam."""
    rng = random.Random(seed)
    body = data[int(.05 * RATE):len(data) - int(.05 * RATE)]
    out = body.copy()
    while len(out) < (seconds + FADE) * RATE:
        piece = resample(body, rng.uniform(.97, 1.03)) * rng.uniform(.92, 1.05)
        start = rng.randrange(0, max(1, len(piece) // 3))
        out = crossfade(out, piece[start:])
    n = int(FADE * RATE)
    loop = out[:int(seconds * RATE) + n]
    head, tail = loop[:n], loop[-n:]
    t = np.linspace(0, np.pi / 2, n)
    loop = loop[:-n].copy()
    loop[:n] = tail * np.cos(t) + head * np.sin(t)
    return loop


def normalise(data, peak=.75):
    top = np.max(np.abs(data)) or 1.0
    return data / top * peak


def echo(data):
    out = np.zeros(len(data))
    out += data
    for delay, gain in ((.09, .4), (.21, .25), (.37, .14)):
        d = int(delay * RATE)
        out[d:] += data[:-d] * gain
    return out


def write(path, data):
    """Written in blocks: libsndfile's Vorbis encoder overflows the stack on Windows given a long buffer at once."""
    path.parent.mkdir(parents=True, exist_ok=True)
    data = np.ascontiguousarray(normalise(data), dtype=np.float32)
    with sf.SoundFile(path, "w", samplerate=RATE, channels=1, format="OGG", subtype="VORBIS") as out:
        for start in range(0, len(data), 4096):
            out.write(data[start:start + 4096])


def main():
    # python -I prepare_audio.py [vehicle ...]: only those (the encoder isn't byte-repeatable, so rewriting the others
    # churns files that haven't changed); with none, every vehicle and the track clank.
    only = set(sys.argv[1:])
    report = []
    for i, vehicle in enumerate(VEHICLES):
        if only and vehicle not in only:
            continue
        if vehicle in ENGINE_FROM:
            key, start, length, speed, boost = ENGINE_FROM[vehicle]
            take = starts().source(key)
            source = starts().highpass(take[round(start * RATE):round((start + length) * RATE)])
            source = resample(source, speed)
            if boost:
                source = source + boost * lowpass(source, 220)
            write(SOUNDS / vehicle / "engine.ogg", source)
        else:
            source = read(SOUNDS / vehicle / "engine.ogg")
        load = varying_loop(source, 16.0, 1000 + i)
        idle = lowpass(resample(varying_loop(source, 16.0 * .84, 2000 + i), .84), 2600)
        cab = lowpass(load, 1300) * .8
        distant = echo(lowpass(load, 450))
        for name, data in (("engine_load", load), ("engine_idle", idle), ("engine_cab", cab), ("engine_distant", distant)):
            write(SOUNDS / vehicle / f"{name}.ogg", data)
            report.append(f"{vehicle}/{name}.ogg: {len(data) / RATE:.1f} s")
    if only:
        print(chr(10).join(report))
        return
    clank = varying_loop(read(SOUNDS / "bulldozer" / "tracks.ogg"), 12.0, 3000)
    write(SOUNDS / "tracks" / "clank.ogg", clank)
    report.append(f"tracks/clank.ogg: {len(clank) / RATE:.1f} s")
    (SOUNDS / "tracks" / "CREDITS.md").write_text(
        "# Track clank\n\n`clank.ogg`: the BD-40's CC0 tracks recording (see `../bulldozer/CREDITS.md`), made into a 12 s "
        "varying loop by `design/ground-vehicles/prepare_audio.py`.\n", encoding="utf-8")
    print("\n".join(report))


if __name__ == "__main__":
    main()
