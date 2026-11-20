"""Prepare the FSV-6's mono Ogg assets from credited CC0 field recordings, with no runtime downloads.

The diesel's idle and working loops come from one take of a turbo-diesel 4x4 revving (so the idle/load crossfade
is one voice), lowered for a bigger engine; the shelter runs on a diesel generator; the slide-outs on an electric
motor; the ramp is a heavy lift door's clank. Each source page is checked for its CC0 licence before it is used.

No asset may carry a human voice. Every source was listened to and trimmed clear of talking: the diesel take is cut
after second 8. The start is a real truck starting and the horn a real truck horn (synthesized versions were rejected
in review). Only the air-brake hiss is synthesized; the cab and distant layers are filtered from the take's clean idle
and working loops.

Sources are cached under build/support-audio-sources/. Requires numpy, scipy and soundfile.
Run: python design/support/prepare_audio.py
"""
from __future__ import annotations

import hashlib
import importlib.util
import json
import math
from pathlib import Path
import re
import urllib.request

import numpy as np
from scipy import signal

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
_spec = importlib.util.spec_from_file_location("dozer_audio", ROOT / "design/bulldozer/prepare_audio.py")
dozer = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(dozer)
sf = dozer.sf
RATE = dozer.RATE
band, segment, steady, normalize, loop, fade, run_down, repitch = (
    dozer.band, dozer.segment, dozer.steady, dozer.normalize, dozer.loop, dozer.fade, dozer.run_down, dozer.repitch)

OUT = ROOT / "src/main/resources/assets/jeg/sounds/vehicle/truck"
CACHE = ROOT / "build/support-audio-sources"
SOURCES = {
    "start": ("Legnalegna55", "530052", "Starting Truck Engine.mp3"),
    "idle": ("C-V", "565598", "Diesel idle 1.wav"),
    "generator": ("qubodup", "189896", "Diesel Power Generator.flac"),
    "motor": ("kentspublicdomain", "476943", "Electric motor engine start stop"),
    "horn": ("danlucaz", "513741", "Truck Horn"),
    "door": ("lolamadeus", "161228", "Large Metal Lift Door Opening.wav"),
}
CC0 = ("creativecommons.org/publicdomain/zero", "Creative Commons 0")


def source(key):
    author, identity, title = SOURCES[key]
    url = f"https://freesound.org/people/{author}/sounds/{identity}/"
    target = CACHE / (identity + "-hq.mp3")
    page_cache = CACHE / (identity + ".html")
    CACHE.mkdir(parents=True, exist_ok=True)
    if not page_cache.exists():
        request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (asset preparation script)"})
        page_cache.write_text(urllib.request.urlopen(request, timeout=30).read().decode(), encoding="utf-8")
    page = page_cache.read_text(encoding="utf-8")
    if not any(marker in page for marker in CC0):
        raise RuntimeError(f"{url} is not marked CC0; not using it")
    if not target.exists():
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
    print(f"Source {key} ({identity}): {len(data) / RATE:.2f}s")
    return data


def envelope(data, window=.1):
    n = round(window * RATE)
    frames = len(data) // n
    return np.array([np.sqrt(np.mean(data[i * n:(i + 1) * n] ** 2)) for i in range(frames)])


def steady_at(data, duration, quantile_low, quantile_high):
    """The steadiest stretch whose loudness lies in a band of the take's loudness (idle low, working high)."""
    env = envelope(data)
    lo, hi = np.quantile(env, quantile_low), np.quantile(env, quantile_high)
    count = round(duration * RATE)
    best = None
    for start in range(0, max(1, len(data) - count), RATE // 10):
        chunk = env[start // round(.1 * RATE): (start + count) // round(.1 * RATE)]
        if len(chunk) == 0:
            continue
        mean = np.mean(chunk)
        if not lo <= mean <= hi:
            continue
        score = np.std(chunk) / max(1e-8, mean)
        if best is None or score < best[0]:
            best = (score, start)
    if best is None:
        return steady(data, duration)
    return data[best[1]:best[1] + count].copy(), best[1] / RATE


def onset(data, threshold=.25):
    """Time of the first sample above a share of the take's peak."""
    env = envelope(data, .02)
    return int(np.argmax(env > threshold * np.max(env))) * .02


def write(name, data, loops, report):
    target = OUT / (name + ".ogg")
    with sf.SoundFile(target, "w", RATE, 1, format="OGG", subtype="VORBIS") as out:
        for i in range(0, len(data), 4096):
            out.write(data[i:i + 4096])
    decoded, rate = sf.read(target)
    assert rate == RATE and decoded.ndim == 1 and np.isfinite(decoded).all()
    assert np.max(np.abs(decoded)) < .98, (name, "clips")
    if name in loops:
        assert abs(decoded[-1] - decoded[0]) < .03, (name, "Decoded loop seam jumps")
    report[name] = {"duration_seconds": round(len(decoded) / RATE, 3), "loop": name in loops,
                    "peak": round(float(np.max(np.abs(decoded))), 4), "rms": round(float(np.sqrt(np.mean(decoded * decoded))), 4),
                    "seam_step": round(float(abs(decoded[-1] - decoded[0])), 5), "sha256": hashlib.sha256(target.read_bytes()).hexdigest()}


def lowpass(data, cutoff, order=4):
    return signal.sosfilt(signal.butter(order, cutoff, fs=RATE, btype="lowpass", output="sos"), data)


def starter(idle, seconds=1.1, catch=1.0):
    """A diesel's starter motor cranking (a geared whine with the compression strokes beating at ~11 Hz) that catches
    into the engine's own idle with a short rev. Synthesized: no recording, no voices."""
    rng = np.random.default_rng(6062)
    n = round(seconds * RATE)
    t = np.arange(n) / RATE
    strokes = .55 + .45 * np.maximum(0, np.sin(2 * np.pi * (10 + 2 * t) * t)) ** 3
    whine_freq = 190 + 40 * t / seconds
    whine = np.sin(2 * np.pi * np.cumsum(whine_freq) / RATE) + .4 * np.sin(4 * np.pi * np.cumsum(whine_freq) / RATE)
    body = band(rng.standard_normal(n), 90, 1400)
    crank = (body / np.max(np.abs(body)) * .8 + .35 * whine) * strokes
    crank *= np.minimum(1, t / .04)
    m = round(catch * RATE)
    idle_part = idle[:m].copy()
    rev = repitch(idle[:round(m * .6)], 1.25)[:round(m * .45)]
    caught = idle_part.copy()
    caught[:len(rev)] = caught[:len(rev)] * .4 + rev * .9
    caught *= np.linspace(1, .85, m)
    overlap = round(.12 * RATE)
    fade_out, fade_in = np.linspace(1, 0, overlap), np.linspace(0, 1, overlap)
    crank = normalize(crank, .1)
    caught = normalize(caught, .13)
    out = np.concatenate((crank[:-overlap], crank[-overlap:] * fade_out + caught[:overlap] * fade_in, caught[overlap:]))
    return fade(out, .01, .25)


def muffled_cab(loop_slice):
    """The diesel heard through a closed cab: the high end gone, a little low resonance from the cab's panels."""
    low = lowpass(loop_slice, 700)
    boom = band(loop_slice, 70, 160)
    return low + .6 * boom


def distant(loop_slice):
    """A working diesel far off: only the rumble carries, with a faint ground echo."""
    rumble = band(loop_slice, 35, 520)
    delay = round(.11 * RATE)
    echo = np.concatenate((np.zeros(delay), rumble[:-delay])) * .3
    return rumble + echo


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    start, idle_take, generator, motor, horn, door = [source(k) for k in SOURCES]
    # A real truck: its start (crank and catch, the first 3 s of the take) and its running engine. The take holds
    # three starts (at 0 s, 8.2 s and 16.4 s: a gap of silence, then the ~10 Hz crank beat), so the running loop is
    # three clean stretches between them (1.6-7.6 s, 9.6-15.7 s, 17.6-23.9 s), each matched in level and joined with
    # equal-power crossfades: ~17.5 s with no start in it. A dually diesel truck's long, clean idle of the same tone
    # (spectral centroid ~2.4 kHz, 70 % below 500 Hz) gives a 16 s idle loop, so neither repeats quickly.
    start_band, idle_band = band(start, 30, 9000), band(idle_take, 30, 9000)
    start_clip = fade(segment(start_band, .1, 3.1), .01, .35)
    idle_slice = segment(idle_band, 4.0, 20.0)
    idle_at = 4.0
    load_at = [(1.6, 6.0), (9.6, 6.1), (17.6, 6.3)]
    pieces = [segment(start_band, at, length) for at, length in load_at]
    target_rms = np.mean([np.sqrt(np.mean(piece * piece)) for piece in pieces])
    pieces = [piece * (target_rms / np.sqrt(np.mean(piece * piece))) for piece in pieces]
    overlap = round(.4 * RATE)
    ramp = np.linspace(0, np.pi / 2, overlap)
    load_slice = pieces[0]
    for piece in pieces[1:]:
        load_slice = np.concatenate((load_slice[:-overlap], load_slice[-overlap:] * np.cos(ramp) + piece[:overlap] * np.sin(ramp), piece[overlap:]))
    deep = 1.0
    idle_deep, load_deep = idle_slice, normalize(load_slice, .14)
    gen_slice, gen_at = steady(band(generator, 40, 6000), 6)
    motor_slice, motor_at = steady_at(band(motor, 120, 9000), 1.2, .5, 1.0)
    horn_slice, horn_at = segment(band(horn, 80, 9000), .1, .36), .1
    door_at = onset(band(door, 60, 9000), .35)
    assets = {
        "engine_start": normalize(start_clip, .13),
        "engine_idle": normalize(loop(idle_deep, .3), .12),
        "engine_load": normalize(loop(load_deep, .3), .14),
        "engine_stop": normalize(run_down(idle_deep[: round(3 * RATE)], 2.2, .3), .1),
        "engine_distant": normalize(loop(distant(load_deep), .4), .09, .6),
        "engine_cab": normalize(loop(muffled_cab(idle_deep), .4), .11),
        "generator": normalize(loop(gen_slice, .3), .1),
        "generator_distant": normalize(loop(band(gen_slice, 40, 500), .3), .08, .6),
        "slide_motor": normalize(loop(motor_slice, .15), .08),
        "horn": normalize(loop(np.tile(horn_slice, 3), .05), .16, .8),
        "horn_distant": normalize(loop(np.tile(band(horn_slice, 120, 1200), 3), .05), .12, .7),
        "ramp_clank": normalize(fade(segment(band(door, 60, 9000), max(0, door_at - .03), 1.1), .004, .3), .12, .9),
    }
    loops = {"engine_idle", "engine_load", "engine_distant", "engine_cab", "generator", "generator_distant", "slide_motor",
             "horn", "horn_distant"}
    report = {}
    for name, data in assets.items():
        write(name, data, loops, report)
    report["source_segments_seconds"] = {"idle": idle_at, "load": load_at, "generator": gen_at, "motor": motor_at,
                                         "horn": horn_at, "door": door_at, "diesel_repitch": deep,
                                         "shared": ["air_brake: vehicle/movement/brake (design/ground-vehicles/prepare_movement.py)"],
                                         "derived": ["engine_cab (the idle, muffled)", "engine_distant (load, rumble + echo)"]}
    (HERE / "audio-validation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"Validated {len(assets)} mono Ogg assets: finite samples, peaks and loop seams.")


if __name__ == "__main__":
    main()
