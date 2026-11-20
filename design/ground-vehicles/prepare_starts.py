"""Real engine starts and stops for the armoured vehicles (GroundVehicleAudio; guide section 7, "Still missing").

python -I design/ground-vehicles/prepare_starts.py

Each vehicle gets engine_start and engine_stop (heard outside and from an open seat) and engine_start_cab and
engine_stop_cab (the same, muffled as its engine_cab loop is, for an enclosed crew in first person). All are cut from
CC0 field recordings of real diesels; each source page is checked for its CC0 licence before use. The loops wind up
when the start "catches" (CATCH, seconds into the clip), which GroundVehicleSounds.startCatchTicks mirrors.

- BMP-2: roboroo's heavy diesel startup and shutdown (one take): starter, catch at 3.0 s; the shutdown at 20 s.
- YX-100 (since 2026-11-19): an M1A1 Abrams' gas turbine (adr1911 542582): starter, light-off and spool-up, catch at
  4.0 s; its stop is that spool played backwards.
- PLZ-05: DAVESTALKER's diesel start (starter whine, catch at 1.0 s); since 2026-11-19 its stop is its own idle (the
  stationary stretch of monosfera's tank ride, 572294) running down.
- LAV-150: a truck diesel started near its hood (kyles 629939, catch at 1.0 s, lowered to 0.92; since 2026-11-18) and
  a Ford LTL 9000's shutdown (YellowJacketStudios), the LAV's own class of engine (a truck V8 diesel).

Voices: none of the cut stretches carries speech (measured: syllabic modulation in the 300-3400 Hz band below 0.2 for
every source; the LTL shutdown take's transient at 68.5 s, a door or click, lies outside the cut). Please listen.

No real recording of an armoured vehicle's interior is available under CC0 (searched 2026-11-17: "inside tank",
"tank interior", "M113", "APC", "armored vehicle", "truck cab interior"; the one "tank interior" take, craigsmith
438015, is a rattling car on a rough road). The cab loops stay filtered exterior takes; this is a known limit.

Sources are cached under build/ground-vehicle-sources/. Requires numpy, scipy and soundfile.
"""
from __future__ import annotations

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
SOUNDS = ROOT / "src/main/resources/assets/jeg/sounds/vehicle"
CACHE = ROOT / "build/ground-vehicle-sources"
CC0 = ("creativecommons.org/publicdomain/zero", "Creative Commons 0")
SOURCES = {
    "roboroo": ("roboroo", "436793", "Diesel engine Startup and Stutdown"),
    "davestalker": ("DAVESTALKER", "443319", "Diesel Start-Idle-Revs MZ000004.wav"),
    "ltl_start": ("YellowJacketStudios", "435656", "FORD LTL 9000_engine start and idle.wav"),
    "ltl_stop": ("YellowJacketStudios", "435667", "FORD LTL 9000_back up and engine shutdown.wav"),
    # The LAV-150's start since the user's review (2026-11-18: "should be better"): a truck diesel started close to its
    # hood, punchier than the LTL's cab-side take; lowered to 0.92 for an armoured car's heavier engine.
    "kyles_truck": ("kyles", "629939", "truck engine start and idle ext front from outside near hood.wav"),
    # The wheeled vehicles' tyre layer (GroundVehicleAudio "tyres", by speed): gravel under a truck's tyres, the ground
    # most of a world is.
    "gravel": ("OBXJohn", "251662", "Tires on Gravel Road 2"),
    # Each tank its own engine (the user's review, 2026-11-19: "find a separate audio recording for each tank, it
    # should have its own identity"). The YX-100: an M1A1 Abrams' gas turbine, started; the PLZ-05: a heavy tank's
    # diesel recorded on a ride over rough ground; the BMP-2: an armoured personnel carrier's diesel.
    "m1a1": ("adr1911", "542582", "Start-up Sound - U.S. Army M1A1 Tank - Gas Turbine Engine.mp3"),
    "tank_ride": ("monosfera", "572294", "tank_engine.wav"),
    "apc_hum": ("qubodup", "182793", "Military Vehicle Humming.flac"),
}
# vehicle: (start source, start from, start length, catch (s, in the source), stop source, stop from, stop length, pitch)
PLAN = {
    "bmp2": ("roboroo", 0.0, 6.5, 3.0, "roboroo", 19.3, 3.7, 1.0, 1.0),
    # The turbine: the starter, light-off and spool-up (catch 4 s in, the whine well on its way); its stop is the
    # same spool played backwards, the whine falling away.
    "yx100": ("m1a1", 2.0, 8.5, 4.0, "m1a1:reverse", 2.4, 7.0, 1.0, 1.0),
    # Its own run-down: its idle (the ride's stationary stretch) slowing to a stop, the last beats fading.
    "plz05": ("davestalker", 0.0, 4.0, 1.0, "tank_ride:rundown", 37.2, 3.2, 1.0, 1.0),
    "lav150": ("kyles_truck", 0.0, 5.0, 1.0, "ltl_stop", 81.5, 7.5, 0.92, 1.0),
}
CAB_CUTOFF = 1300.0  # as engine_cab (design/ground-vehicles/prepare_audio.py)


def source(key):
    author, identity, title = SOURCES[key]
    url = f"https://freesound.org/people/{author}/sounds/{identity}/"
    CACHE.mkdir(parents=True, exist_ok=True)
    page_cache, target = CACHE / (identity + ".html"), CACHE / (identity + "-hq.mp3")
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
        request = urllib.request.Request(links[0], headers={"User-Agent": "Mozilla/5.0 (asset preparation script)"})
        target.write_bytes(urllib.request.urlopen(request, timeout=60).read())
    data, rate = sf.read(target, always_2d=True)
    data = np.mean(data, axis=1)
    if rate != RATE:
        g = math.gcd(rate, RATE)
        data = signal.resample_poly(data, RATE // g, rate // g)
    return data - np.mean(data)


def cut(data, start, length):
    return data[round(start * RATE):round((start + length) * RATE)].copy()


def repitch(data, factor):
    if factor == 1.0:
        return data
    n = int(len(data) / factor)
    return np.interp(np.arange(n) * factor, np.arange(len(data)), data)


def rundown(data, low=0.3):
    """An engine stopping: the take played ever slower (speed falling from 1 to {low}, faster at the end) and fading."""
    n = len(data)
    speed = 1.0 - (1.0 - low) * (np.arange(n) / n) ** 1.6
    reached = np.cumsum(1.0 / speed)  # the output sample at which each source sample is reached
    source_at = np.interp(np.arange(int(reached[-1])), reached, np.arange(n))
    out = np.interp(source_at, np.arange(n), data)
    out *= np.linspace(1.0, 0.0, len(out)) ** 1.3
    return out


def fade(data, attack, release):
    data = data.copy()
    a, r = min(len(data) // 2, round(attack * RATE)), min(len(data) // 2, round(release * RATE))
    if a:
        data[:a] *= np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    if r:
        data[-r:] *= np.cos(np.linspace(0, np.pi / 2, r)) ** 2
    return data


def highpass(data, cutoff=28.0):
    return signal.sosfilt(signal.butter(2, cutoff, fs=RATE, btype="highpass", output="sos"), data)


def lowpass(data, cutoff):
    return signal.sosfilt(signal.butter(4, cutoff, fs=RATE, btype="lowpass", output="sos"), data)


def level(data, rms, peak=0.85):
    data = data * (rms / max(1e-9, np.sqrt(np.mean(data ** 2))))
    top = np.max(np.abs(data))
    return data * (peak / top) if top > peak else data


def write(path, data):
    with sf.SoundFile(path, "w", RATE, 1, format="OGG", subtype="VORBIS") as out:
        for i in range(0, len(data), 4096):
            out.write(data[i:i + 4096])
    decoded, rate = sf.read(path)
    assert rate == RATE and decoded.ndim == 1 and np.isfinite(decoded).all()
    assert np.max(np.abs(decoded)) < 0.98, (path, "clips")


def main():
    # python -I prepare_starts.py [vehicle ...]: only those (and not the tyre loop); with none, all.
    only = set(sys.argv[1:])
    takes = {key: source(key) for key in SOURCES}
    validation = HERE / "starts-validation.json"
    report = json.loads(validation.read_text(encoding="utf-8")) if only and validation.exists() else {}
    for vehicle, (s_key, s_from, s_len, catch, e_key, e_from, e_len, s_pitch, e_pitch) in PLAN.items():
        if only and vehicle not in only:
            continue
        start = repitch(highpass(cut(takes[s_key], s_from, s_len)), s_pitch)
        # The tail fades over 1.5 s, under the loops that wind up from the catch.
        start = level(fade(start, 0.03, 1.5), 0.12)
        e_source, _, how = e_key.partition(":")
        stop = repitch(highpass(cut(takes[e_source], e_from, e_len)), e_pitch)
        if how == "reverse":
            stop = stop[::-1].copy()
        elif how == "rundown":
            stop = rundown(stop)
        e_key = e_source
        # The head fades in under the loops winding down.
        stop = level(fade(stop, 0.4, 0.5), 0.1)
        out = SOUNDS / vehicle
        clips = {
            "engine_start": start,
            "engine_stop": stop,
            "engine_start_cab": level(lowpass(start, CAB_CUTOFF), 0.1),
            "engine_stop_cab": level(lowpass(stop, CAB_CUTOFF), 0.085),
        }
        for name, data in clips.items():
            write(out / f"{name}.ogg", data)
        report[vehicle] = {"catch_s": round(catch / s_pitch, 2), "start_s": round(len(start) / RATE, 2),
                           "stop_s": round(len(stop) / RATE, 2)}
        credits = out / "CREDITS.md"
        lines = credits.read_text(encoding="utf-8").splitlines() if credits.exists() else [f"# Sound credits: {vehicle}", ""]
        lines = [l for l in lines if "engine_start" not in l and "engine_stop" not in l]
        for name, key in (("engine_start, engine_start_cab", s_key), ("engine_stop, engine_stop_cab", e_key)):
            author, identity, title = SOURCES[key]
            lines.append(f"- `{name}`: \"{title}\" by {author.replace('%20', ' ')}, "
                         f"https://freesound.org/people/{author}/sounds/{identity}/ (CC0 1.0), cut and filtered by "
                         f"design/ground-vehicles/prepare_starts.py")
        credits.write_text("\n".join(lines) + "\n", encoding="utf-8")
    if only:
        validation.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(report, indent=1))
        return
    # The tyre loop: 19 s of the steady gravel take (1 s in, clear of the start), its seam closed by an equal-power
    # crossfade of the tail into the head; band-limited to the tyre noise (no engine hum below 70 Hz).
    gravel = signal.sosfilt(signal.butter(3, [70, 9000], fs=RATE, btype="bandpass", output="sos"), cut(takes["gravel"], 1.0, 19.6))
    n = round(0.6 * RATE)
    t = np.linspace(0, np.pi / 2, n)
    body, tail = gravel[:-n].copy(), gravel[-n:]
    body[:n] = body[:n] * np.sin(t) + tail * np.cos(t)
    tyres = level(body, 0.11)
    (SOUNDS / "tyres").mkdir(exist_ok=True)
    write(SOUNDS / "tyres" / "gravel.ogg", tyres)
    decoded, _ = sf.read(SOUNDS / "tyres" / "gravel.ogg")
    report["tyres"] = {"loop_s": round(len(tyres) / RATE, 2), "seam_jump": round(float(abs(decoded[-1] - decoded[0])), 4)}
    author, identity, title = SOURCES["gravel"]
    (SOUNDS / "tyres" / "CREDITS.md").write_text(
        "# Sound credits: tyres\n\n"
        f"- `gravel`: \"{title}\" by {author}, https://freesound.org/people/{author}/sounds/{identity}/ (CC0 1.0), a 19 s "
        "loop cut and band-limited by design/ground-vehicles/prepare_starts.py\n", encoding="utf-8")
    (HERE / "starts-validation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=1))


if __name__ == "__main__":
    main()
