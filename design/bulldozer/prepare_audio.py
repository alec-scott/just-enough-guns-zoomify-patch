"""Prepare the BD-40's mono Ogg assets from credited CC0 field recordings of real bulldozers, with no runtime downloads.

The engine is a close-miked Caterpillar crawler (idle and working loops cut from one take, so they share one
voice for the crossfade), the distant layer a working bulldozer, the blade a dozer shaking soil and rock, the
tracks a tracked machine's clank, and the tree a conifer creaking and crashing down.

Sources are cached under build/. Requires numpy, scipy and soundfile; this workspace's
existing local soundfile install is used when the normal package is unavailable.
"""
from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path
import re
import sys
import urllib.request

import numpy as np
from scipy import signal

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
try:
    import soundfile as sf
except ImportError:
    sys.path.insert(0, str(ROOT / "build/audio-tools-local"))
    import soundfile as sf

RATE = 44100
OUT = ROOT / "src/main/resources/assets/jeg/sounds/vehicle/bulldozer"
CACHE = ROOT / "build/bulldozer-audio-sources"
SOURCES = {
    "cat": ("Rincewind87", "165200", "Construction_Bulldozer_Catarpillar_Engine_Planierer.wav"),
    "dozer": ("Rincewind87", "165199", "Construction_Bulldozer_Diesel.wav"),
    "site": ("Mariayolanda511", "850572", "Bulldozer Encendida"),
    "rocks": ("johnnydekk", "615309", "Bulldozer Shaking Rocks .wav"),
    "clank": ("christerljung", "849062", "tracks moving"),
    "tree": ("Kinoton", "494071", "Big Tree Fall in Forest"),
    "work": ("cognito%20perceptu", "88422", "construction equipment tracked.wav"),
    "hydraulic": ("nuckan", "212941", "hydraulics.wav"),
    "start": ("RichieMcMullen", "386800", "engine_start.wav"),
    "horn": ("mcpable", "131930", "Industrial Air Horn"),
}


def source(key):
    author, identity, title = SOURCES[key]
    url = f"https://freesound.org/people/{author}/sounds/{identity}/"
    target = CACHE / (identity + "-hq.mp3")
    CACHE.mkdir(parents=True, exist_ok=True)
    if not target.exists():
        page = urllib.request.urlopen(url, timeout=30).read().decode()
        links = re.findall(r'https://cdn\.freesound\.org/previews/[^\s"<>]+-hq\.mp3', page)
        if not links:
            raise RuntimeError("No public HQ preview found for " + url)
        urllib.request.urlretrieve(links[0], target)
    data, rate = sf.read(target, always_2d=True)
    data = np.mean(data, axis=1)
    if rate != RATE:
        divisor = math.gcd(rate, RATE)
        data = signal.resample_poly(data, RATE // divisor, rate // divisor)
    data -= np.mean(data)
    print(f"Source {identity}: {len(data)/RATE:.2f}s, mono {RATE}Hz")
    return data


def band(data, low, high):
    return signal.sosfilt(signal.butter(3, [low, high], fs=RATE, btype="bandpass", output="sos"), data)


def periodic_band(data, low, high):
    # Warm the filter with a preceding cycle; zero initial state would click at the loop seam.
    filtered = band(np.tile(data, 3), low, high)
    return filtered[len(data):2*len(data)].copy()


def segment(data, start, duration):
    return data[round(start * RATE):round((start + duration) * RATE)].copy()


def steady(data, duration):
    """Choose a continuous segment without near-silent joins in a multi-loop source."""
    count = round(duration * RATE)
    candidates = []
    for start in range(0, max(1, len(data) - count), RATE // 4):
        chunk = data[start:start + count]
        if len(chunk) < count:
            continue
        rms = np.array([np.sqrt(np.mean(c * c)) for c in np.array_split(chunk, max(2, int(duration * 8)))])
        score = np.std(rms) / max(1e-8, np.mean(rms)) + .01 / max(.005, np.mean(rms))
        candidates.append((score, start))
    start = min(candidates)[1]
    return data[start:start + count].copy(), start / RATE


def normalize(data, rms=.12, peak=.72):
    data = data - np.mean(data)
    level = np.sqrt(np.mean(data * data))
    data *= rms / max(level, 1e-8)
    maximum = np.max(np.abs(data))
    if maximum > peak:
        data *= peak / maximum
    return data


def loop(data, seconds=.25):
    """Overlap tail/head while preserving the non-zero seam (no periodic fade to silence)."""
    n = min(round(seconds * RATE), len(data) // 5)
    t = np.linspace(0, 1, n, endpoint=False)
    blend = data[-n:] * np.cos(t * np.pi / 2) + data[:n] * np.sin(t * np.pi / 2)
    result = np.concatenate((blend, data[n:-n]))
    # Pick the quietest first derivative as the wrap point without changing the waveform.
    middle = len(result) // 2
    result = np.roll(result, -middle)
    # Bridge less than a millisecond at the wrap without fading the engine to silence.
    n = 32
    boundary = (result[0] + result[-1]) / 2
    taper = np.linspace(1, 0, n) ** 2
    result[:n] += (boundary - result[0]) * taper
    result[-n:] += (boundary - result[-1]) * taper[::-1]
    return result


def fade(data, attack=.015, release=.08):
    data = data.copy()
    a, r = min(len(data)//2, round(attack*RATE)), min(len(data)//2, round(release*RATE))
    if a:
        data[:a] *= np.linspace(0, 1, a)
    if r:
        data[-r:] *= np.linspace(1, 0, r)
    return data


def run_down(data, seconds=2.6, end_rate=.28):
    """Diesel shutdown: the recorded idle slowed continuously to a stop (pitch and firing rate fall together)."""
    n = round(seconds * RATE)
    t = np.linspace(0, 1, n)
    rate = 1 - (1 - end_rate) * t ** 1.4
    position = np.cumsum(rate)
    out = np.interp(position, np.arange(len(data)), data)
    envelope = (1 - t) ** 1.6
    return fade(out * envelope, .005, .25)


def repitch(data, factor):
    """Resample to change pitch (factor < 1 lowers it and lengthens the clip)."""
    n = int(len(data) / factor)
    return np.interp(np.arange(n) * factor, np.arange(len(data)), data)


def heavy_horn(tone):
    """A heavy machine's horn from an industrial air horn's steady tone (about 318 Hz): lowered to about 280 Hz,
    a second horn a minor third above for the dual-tone chord, a quiet sub-octave for weight, and the brightest top
    rolled off, so it reads as plant machinery rather than a car."""
    low = repitch(tone, .88)
    high = repitch(tone, .88 * 1.19)
    sub = repitch(tone, .44)
    n = min(len(low), len(high), len(sub))
    mixed = low[:n] + .62 * high[:n] + .45 * sub[:n]
    mixed = np.tanh(mixed / np.max(np.abs(mixed)) * 1.4)
    # Body: lift the fundamentals (140-420 Hz) and roll the top off, as a big horn heard through air and steel.
    body = signal.sosfilt(signal.butter(2, [140, 420], fs=RATE, btype="bandpass", output="sos"), mixed)
    mixed = mixed + 1.6 * body
    return signal.sosfilt(signal.butter(3, 3400, fs=RATE, btype="lowpass", output="sos"), mixed)


def tile(data, seconds):
    reps = int(math.ceil(seconds * RATE / len(data)))
    return np.tile(data, reps)[:round(seconds * RATE)]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    cat, dozer, site, rocks, clank, tree, work, hydraulic, start, horn = [source(k) for k in
        ("cat", "dozer", "site", "rocks", "clank", "tree", "work", "hydraulic", "start", "horn")]
    horn_loop = loop(heavy_horn(segment(band(horn, 60, 9000), .6, 1.3)), .12)
    idle_slice = segment(band(cat, 28, 7000), 23.7, 11.4)
    load_slice = segment(band(cat, 28, 7000), 7.2, 12.4)
    distant_slice, distant_at = steady(band(dozer, 35, 650), 10)
    clank_slice = segment(band(clank, 140, 9000), .05, 3.4)
    rattle_slice, rattle_at = steady(band(work, 220, 3200), 3.4)
    for frequency in (1000, 2000):
        b, a = signal.iirnotch(frequency, 14, RATE)
        rattle_slice = signal.lfilter(b, a, rattle_slice)
    tracks_mix = normalize(clank_slice, .1) + .45 * normalize(rattle_slice, .1)
    blade_slice = segment(band(rocks, 55, 9000), .2, 4.2)
    hydraulic_slice, hydraulic_at = steady(band(hydraulic, 100, 5000), 1.4)
    start_clip = fade(band(start, 45, 5000), .01, .15)
    stop_clip = fade(segment(band(hydraulic, 100, 3200), max(0, hydraulic_at - .05), .3), .004, .12)
    beep = np.zeros(RATE)
    n = round(.22 * RATE)
    beep[:n] = fade(np.sin(np.arange(n) * math.tau * 930 / RATE) + .25 * np.sin(np.arange(n) * math.tau * 1860 / RATE), .012, .035)
    assets = {
        "engine_idle": normalize(loop(idle_slice, .35), .12),
        "engine_load": normalize(loop(load_slice, .35), .14),
        "engine_distant": normalize(loop(distant_slice, .4), .09, .6),
        "tracks": normalize(loop(tracks_mix, .2), .09),
        "blade": normalize(loop(blade_slice, .3), .11),
        "hydraulic": normalize(loop(hydraulic_slice, .12), .07),
        "engine_start": normalize(start_clip, .12),
        "engine_stop": normalize(run_down(idle_slice[: round(4 * RATE)]), .1),
        "hydraulic_stop": normalize(stop_clip, .05, .35),
        "blade_impact_1": normalize(fade(segment(band(site, 110, 9000), 29.52, .55), .004, .2), .12, .9),
        "blade_impact_2": normalize(fade(segment(band(site, 110, 9000), 12.78, .45), .004, .18), .12, .9),
        "tree_fall": normalize(fade(segment(band(tree, 40, 12000), 0, 7.4), .02, .9), .13, .92),
        "reverse": beep * .09,
        "horn": normalize(horn_loop, .16, .8),
        "horn_distant": normalize(periodic_band(horn_loop, 120, 1200), .12, .7),
    }
    loops = {"engine_idle", "engine_load", "engine_distant", "tracks", "blade", "hydraulic", "horn", "horn_distant"}
    report = {}
    for name, data in assets.items():
        target = OUT / (name + ".ogg")
        # libsndfile's Vorbis encoder overflows the stack on long single writes on Windows; write in blocks.
        with sf.SoundFile(target, "w", RATE, 1, format="OGG", subtype="VORBIS") as out:
            for i in range(0, len(data), 4096):
                out.write(data[i:i + 4096])
        decoded, rate = sf.read(target)
        assert rate == RATE and decoded.ndim == 1 and np.isfinite(decoded).all()
        assert np.max(np.abs(decoded)) < .98, (name, "clips")
        if name in loops:
            assert abs(decoded[-1] - decoded[0]) < .03, (name, "Decoded loop seam jumps")
        report[name] = {"duration_seconds": round(len(decoded) / RATE, 3), "sample_rate": rate, "channels": 1,
                        "loop": name in loops, "peak": round(float(np.max(np.abs(decoded))), 4),
                        "rms": round(float(np.sqrt(np.mean(decoded * decoded))), 4),
                        "seam_step": round(float(abs(decoded[-1] - decoded[0])), 5),
                        "sha256": hashlib.sha256(target.read_bytes()).hexdigest()}
    report["source_segments_seconds"] = {"engine_idle": [23.7, 11.4], "engine_load": [7.2, 12.4], "distant": distant_at,
                                         "rattle": rattle_at, "hydraulic": hydraulic_at, "blade": [.2, 4.0],
                                         "blade_impact": [[29.52, .55], [12.78, .45]], "tree_fall": [0, 7.4],
                                         "horn": [.6, 1.3]}
    (HERE / "audio-validation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    for stale in ("engine_cab.ogg",):
        (OUT / stale).unlink(missing_ok=True)
    print(f"Validated {len(assets)} mono Ogg assets: finite samples, sample rates, decoded peaks and loop seams.")


if __name__ == "__main__":
    main()
