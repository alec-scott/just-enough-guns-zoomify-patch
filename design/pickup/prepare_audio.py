"""The 4x4 pickup's engine (GroundVehicleAudio; guide section 7), shared by the pickup and its three technicals.

python -I design/pickup/prepare_audio.py

All cut from CC0 recordings on Freesound, each page checked for its CC0 licence before use (prepare_starts.source),
cached under build/ground-vehicle-sources/:

- engine_start, engine_idle, engine_cab_idle, engine_stop: Mullumbimby's cold start of an old diesel car (622829): the
  starter, the catch 1.4 s into the cut, then its idle; the stop is that idle running down. engine_cab_idle is the idle
  through a closed cab (low-passed as the armour's).
- engine_load: Alex_hears_things' 4WD engine held at revs (379914, its steady last 18 s), a 16 s loop that varies.
- engine_cab: nicotep's interior recording of a Daihatsu Taft 4x4 on the move (632734): a real cab, the first in the
  fleet (no armoured vehicle's interior exists under CC0).
- engine_distant: the working loop low-passed to the rumble that carries, with a ground echo.
- door_open, door_close: kyles' pickup door (450225): its latch and swing, its slam. Played as the cab doors swing
  (GroundVehicleAudio, while someone boards or leaves).

Voices: each cut stretch is measured for syllabic modulation in the speech band (printed; under 0.2 passes). Please
listen.
"""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
GV = ROOT / "design/ground-vehicles"


def module(name):
    spec = importlib.util.spec_from_file_location(name, GV / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


A = module("prepare_audio")
S = module("prepare_starts")
S.SOURCES.update({
    "old_diesel": ("Mullumbimby", "622829", "Cold start of old diesel car"),
    "4wd": ("Alex_hears_things", "379914", "4WD Engine & Rev.wav"),
    "taft_cab": ("nicotep", "632734", "INT 4X4 DAHIATSU Taft, Colombia.wav"),
    "door": ("kyles", "450225", "truck pickup door open close real nice slam.flac"),
})
OUT = ROOT / "src/main/resources/assets/jeg/sounds/vehicle/pickup"
RATE = S.RATE
CATCH = 1.4  # seconds into engine_start (GroundVehicleSounds.startCatchTicks 28)


def syllabic(x):
    from scipy import signal
    b, a = signal.butter(4, [300 / (RATE / 2), 3400 / (RATE / 2)], "band")
    env = np.abs(signal.hilbert(signal.lfilter(b, a, x)))
    env = signal.resample_poly(env, 1, RATE // 100)
    f, p = signal.welch(env - env.mean(), fs=100, nperseg=min(256, len(env)))
    return float(p[(f >= 3) & (f <= 7)].sum() / max(p[(f > 0.5) & (f < 20)].sum(), 1e-12))


def main():
    old = S.source("old_diesel")
    four = S.source("4wd")
    cab = S.source("taft_cab")
    door = S.source("door")
    cuts = {
        "start": S.cut(old, 2.6, 6.0),
        "idle": S.cut(old, 9.0, 18.0),
        "load": S.cut(four, 48.5, 18.0),
        "cab": S.cut(cab, 168.0, 24.0),
    }
    report = {name: round(syllabic(x), 3) for name, x in cuts.items()}
    print("syllabic", report)
    idle_loop = A.varying_loop(S.highpass(cuts["idle"]), 16.0, 11)
    load_loop = A.varying_loop(S.highpass(cuts["load"]), 16.0, 12)
    cab_loop = A.varying_loop(S.highpass(cuts["cab"]), 18.0, 13)
    files = {
        "engine_start": S.fade(S.level(S.highpass(cuts["start"]), 0.16), 0.02, 0.6),
        "engine_start_cab": S.fade(S.level(S.lowpass(S.highpass(cuts["start"]), S.CAB_CUTOFF), 0.12), 0.02, 0.6),
        "engine_stop": S.level(S.rundown(S.cut(idle_loop, 4.0, 2.6)), 0.14),
        "engine_stop_cab": S.level(S.lowpass(S.rundown(S.cut(idle_loop, 4.0, 2.6)), S.CAB_CUTOFF), 0.1),
        "engine_idle": idle_loop,
        "engine_cab_idle": S.lowpass(idle_loop, S.CAB_CUTOFF),
        "engine_load": load_loop,
        "engine_cab": cab_loop,
        "engine_distant": A.echo(A.lowpass(load_loop, 450.0)),
        # The take's latch and swing open (3.9 s) and its slam (11.9 s).
        "door_open": S.fade(S.level(S.highpass(S.cut(door, 3.9, 1.3), 60.0), 0.08), 0.005, 0.25),
        "door_close": S.fade(S.level(S.highpass(S.cut(door, 11.9, 1.1), 60.0), 0.1), 0.005, 0.3),
    }
    OUT.mkdir(parents=True, exist_ok=True)
    for name, data in files.items():
        if name.startswith(("engine_start", "engine_stop", "door")):
            S.write(OUT / f"{name}.ogg", data)
        else:
            A.write(OUT / f"{name}.ogg", data)
    (HERE / "audio-validation.json").write_text(json.dumps(
        {"syllabic": report, "catch_seconds": CATCH,
         "seconds": {k: round(len(v) / RATE, 2) for k, v in files.items()}}, indent=2) + "\n", encoding="utf-8")
    (OUT / "CREDITS.md").write_text("""# 4x4 pickup sound assets

Prepared 2026-11-26 by `design/pickup/prepare_audio.py` from public HQ previews of real recordings on Freesound; the
script checks each source page carries the CC0 1.0 licence before using it. Author links are kept as a courtesy.

| Asset | Recording | Author / source | Adaptation |
| --- | --- | --- | --- |
| engine_start, engine_start_cab | Cold start of old diesel car | [Mullumbimby, 622829](https://freesound.org/people/Mullumbimby/sounds/622829/) | The starter and the catch (2.6-8.6 s); the cab version muffled |
| engine_idle, engine_cab_idle, engine_stop, engine_stop_cab | the same take (9-27 s) | as above | Its idle as a 16 s loop that varies; muffled for the cab; the stop is the idle running down |
| engine_load, engine_distant | 4WD Engine & Rev.wav | [Alex_hears_things, 379914](https://freesound.org/people/Alex_hears_things/sounds/379914/) | The engine held at revs (48.5-66.5 s) as a 16 s loop; the distant rumble low-passed with a ground echo |
| door_open, door_close | truck pickup door open close real nice slam.flac | [kyles, 450225](https://freesound.org/people/kyles/sounds/450225/) | A pickup's own door: the latch and swing open (3.9 s), the slam (11.9 s) |
| engine_cab | INT 4X4 DAHIATSU Taft, Colombia.wav | [nicotep, 632734](https://freesound.org/people/nicotep/sounds/632734/) | A real 4x4's cab on the move (168-192 s, the stretch least like speech) as an 18 s loop |

No asset carries a human voice (syllabic modulation in `design/pickup/audio-validation.json`). Tyres are the shared
gravel recording (`../tyres/CREDITS.md`).

License: [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/).
""", encoding="utf-8")
    print({k: round(len(v) / RATE, 2) for k, v in files.items()})


if __name__ == "__main__":
    main()
