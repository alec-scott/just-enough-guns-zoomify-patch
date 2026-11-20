"""Prepare the bush plane's sounds for JetAudio: real light-aircraft recordings (CC0) where they exist, and Superb Warfare's
TOM-6 engine (sounds/vehicle/tom6/engine_source.ogg; used with the SBW team's permission) for the close exterior loop.

Real recordings (2026-11-18; each source page checked for CC0 by prepare_starts.source):
- engine_cockpit: a 1964 Cessna 172 in cruise, recorded in the cabin (JLWSound, 987-1003 s of the flight), looped;
- engine_start_cockpit: a Piper Cub started from its cabin (craigsmith): the starter cranking, the catch at 10.75 s and
  the first rev, 8.6-15.0 s;
- engine_distant: a Diamond DA40 (a Lycoming flat-four) circling over a lake, recorded from the shore (Lars Erik
  Ertzgaard Ringen, MKH 416), 102-118 s, low-passed and looped;
- flyby: the same DA40 taking off past the recorder, the pass at 75-87 s.
Measured, as we cannot listen: the cabin cruise was taken where the recording is steadiest; please listen for voices.

From the SBW recording, as before:

python -I design/bushplane/prepare_audio.py

- engine: the recording made into a 16 s varying loop (the flat-four at cruise);
- engine_cockpit: that loop muffled through the cabin (low-passed at 1.4 kHz);
- engine_distant: the low rumble that carries (450 Hz) with a ground-echo tail;
- engine_start / engine_start_cockpit: the engine swept up from cranking to idle over four seconds;
- flyby: a pass, the loop swept from 1.12 to 0.88 of its speed (Doppler) as it swells and fades.
Everything else (cockpit wind, runway roll, touchdown, the stall horn) falls back to the A-10's generic recordings;
the A-10's spoken "pull up" warning is not played for it (VehicleSoundHelper.jetOwnSound).
"""
from __future__ import annotations

from pathlib import Path
import sys

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "design/ground-vehicles"))
import prepare_audio as P  # noqa: E402

SOUNDS = ROOT / "src/main/resources/assets/jeg/sounds/vehicle/tom6"


def sweep(data, start, end, seconds):
    """Play `data` (looped as needed) with its speed swept from `start` to `end` over `seconds`."""
    n = int(seconds * P.RATE)
    speed = np.linspace(start, end, n)
    position = np.cumsum(speed)
    return np.interp(position % (len(data) - 1), np.arange(len(data)), data)


def _starts():
    import importlib.util
    spec = importlib.util.spec_from_file_location("starts", ROOT / "design/ground-vehicles/prepare_starts.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.SOURCES.update({
        "cessna_cabin": ("JLWSound", "322321", "Small plane (Cessna) engine start, idle, and takeoff."),
        "cub_cabin": ("craigsmith", "437758", "G03-10-Light Plane Piper Cub Interior.wav"),
        "da40": ("LarsErikErtzgaardRingen", "586106", "Diamond Aircrafts DA40 fly by Notodden"),
        "airport_idle": ("wniebelski", "695971", "plane idling on small airport.wav"),
    })
    return module


def crossfade_loop(data, seam):
    """Closes the seam: the tail crossfaded (equal power) into the head."""
    n = round(seam * P.RATE)
    t = np.linspace(0, np.pi / 2, n)
    body, tail = data[:-n].copy(), data[-n:]
    body[:n] = body[:n] * np.sin(t) + tail * np.cos(t)
    return body


def main():
    source = P.read(SOUNDS / "engine_source.ogg")
    loop = P.varying_loop(source, 16.0, 6060)
    start = sweep(loop, 0.25, 1.0, 4.0)
    fade = np.clip(np.linspace(0.0, 1.0, len(start)) * 2.0, 0.0, 1.0)
    start = start * fade
    flyby = sweep(loop, 1.12, 0.88, 5.0)
    envelope = np.concatenate([np.linspace(0.15, 1.0, len(flyby) // 2), np.linspace(1.0, 0.0, len(flyby) - len(flyby) // 2)])
    flyby = flyby * envelope
    outputs = {
        "engine": loop,
        "engine_cockpit": P.lowpass(loop, 1400) * 0.85,
        "engine_distant": P.echo(P.lowpass(loop, 450)),
        "engine_start": start,
        "engine_start_cockpit": P.lowpass(start, 1400) * 0.85,
        "flyby": flyby,
    }
    starts = _starts()
    cessna, cub, da40 = starts.source("cessna_cabin"), starts.source("cub_cabin"), starts.source("da40")
    outputs["engine_cockpit"] = starts.level(crossfade_loop(P.lowpass(starts.cut(cessna, 987.0, 16.3), 6000), 0.3), 0.12, 0.8)
    outputs["engine_start_cockpit"] = starts.level(starts.fade(starts.cut(cub, 8.6, 6.4), 0.05, 0.6), 0.10, 0.85)
    outputs["engine_distant"] = starts.level(crossfade_loop(P.lowpass(starts.cut(da40, 102.0, 16.4), 700), 0.4), 0.10, 0.7)
    outputs["flyby"] = starts.level(starts.fade(starts.cut(da40, 75.0, 12.0), 1.5, 3.0), 0.14, 0.9)
    # The low-power engine (JetAudio "engine_idle", the user's review 2026-11-18: take-off, cruise and landing should
    # sound different): a light aircraft idling on an airfield (wniebelski 695971), 20-38 s, and in the cabin the
    # Cessna's idle after its start (JLWSound 322321, 8-24 s). They crossfade into the power loops as the throttle opens.
    idle = starts.source("airport_idle")
    outputs["engine_idle"] = starts.level(crossfade_loop(starts.cut(idle, 20.0, 18.4), 0.4), 0.11, 0.8)
    outputs["engine_idle_cockpit"] = starts.level(crossfade_loop(P.lowpass(starts.cut(cessna, 8.0, 16.4), 6000), 0.4), 0.11, 0.8)
    for name, data in outputs.items():
        P.write(SOUNDS / f"{name}.ogg", data)
        print(f"tom6/{name}.ogg: {len(data) / P.RATE:.1f} s")
    (SOUNDS / "CREDITS.md").write_text(CREDITS, encoding="utf-8")


CREDITS = """# Bush plane sounds

Prepared by `design/bushplane/prepare_audio.py`.

- `engine.ogg`, `engine_start.ogg`: the Superb Warfare team's TOM-6 engine recording (`engine_source.ogg`, as shipped by
  the port), used with their permission (`design/superbwarfare-port/PERMISSION.md`).
- `engine_cockpit.ogg`: "Small plane (Cessna) engine start, idle, and takeoff." by JLWSound,
  https://freesound.org/people/JLWSound/sounds/322321/ (CC0 1.0): a 1964 Cessna 172's cabin in cruise, looped.
- `engine_start_cockpit.ogg`: "G03-10-Light Plane Piper Cub Interior.wav" by craigsmith,
  https://freesound.org/people/craigsmith/sounds/437758/ (CC0 1.0): the engine started from the cabin.
- `engine_idle.ogg`: "plane idling on small airport.wav" by wniebelski,
  https://freesound.org/people/wniebelski/sounds/695971/ (CC0 1.0): the idle, looped.
- `engine_idle_cockpit.ogg`: the Cessna recording above, its idle after the start (8-24 s), looped.
- `engine_distant.ogg`, `flyby.ogg`: "Diamond Aircrafts DA40 fly by Notodden" by LarsErikErtzgaardRingen,
  https://freesound.org/people/LarsErikErtzgaardRingen/sounds/586106/ (CC0 1.0): circling over the lake (low-passed,
  looped) and taking off past the recorder.
"""


if __name__ == "__main__":
    main()
