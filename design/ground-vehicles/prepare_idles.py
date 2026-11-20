"""Real idle loops for the armour (the user's review, 2026-11-18: "when stationary the vehicle sounds like it's moving").

python -I design/ground-vehicles/prepare_idles.py

The idle loops were the SBW driving recording slowed to 0.84, so a hull standing still still clanked and laboured as
if on the move, and the cab loop (that driving take muffled) played at rest too. Each armoured vehicle's engine_idle is
now the steady idle of the same real diesel whose start it already plays (prepare_starts.py), after the catch and
before any rev or shutdown; engine_cab_idle is that idle muffled as the cab loops are, which GroundVehicleAudio plays in
the cab at rest, crossfading into engine_cab as the hull works. Loops are crossfaded at the seam and levelled to the
old idle loop's RMS so the mix keeps its balance.

- BMP-2: roboroo's heavy diesel (436793), idling 5.0-19.0 s.
- YX-100: the M1A1's turbine (adr1911 542582), its steady whine 12.0-22.0 s after the spool-up.
- PLZ-05: monosfera's tank (572294), the stationary stretch of the ride, 37.2-41.8 s.
- LAV-150: the Ford LTL 9000's diesel (YellowJacketStudios 435656), idling 8.0-26.0 s.

Voices: none (the stretches are inside the takes prepare_starts.py measured). Please listen.
"""
from __future__ import annotations

import importlib.util
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOUNDS = ROOT / "src/main/resources/assets/jeg/sounds/vehicle"

spec = importlib.util.spec_from_file_location("starts", HERE / "prepare_starts.py")
S = importlib.util.module_from_spec(spec)
spec.loader.exec_module(S)
spec = importlib.util.spec_from_file_location("loops", HERE / "prepare_audio.py")
P = importlib.util.module_from_spec(spec)
spec.loader.exec_module(P)

# vehicle: (source, from, length, pitch)
PLAN = {
    "bmp2": ("roboroo", 5.0, 14.0, 1.0),
    # Each its own engine since 2026-11-19: the YX-100 idles on the turbine it starts (the steady whine after the
    # spool-up), the PLZ-05 on the heavy diesel it drives on (the ride's stationary stretch).
    "yx100": ("m1a1", 12.0, 10.0, 1.0),
    "plz05": ("tank_ride", 37.2, 4.6, 1.0),
    "lav150": ("ltl_start", 8.0, 18.0, 1.0),
}
SEAM = 0.6


def loop(data, seam=SEAM):
    """The tail crossfaded (equal power) into the head, so the loop has no seam."""
    n = round(seam * S.RATE)
    t = np.linspace(0, np.pi / 2, n)
    body, tail = data[:-n].copy(), data[-n:]
    body[:n] = body[:n] * np.sin(t) + tail * np.cos(t)
    return body


def rms(data):
    return float(np.sqrt(np.mean(data ** 2)))


def main():
    # python -I prepare_idles.py [vehicle ...]: only those; with none, all.
    import sys
    only = set(sys.argv[1:])
    for vehicle, (key, start, length, pitch) in PLAN.items():
        if only and vehicle not in only:
            continue
        folder = SOUNDS / vehicle
        old_idle = P.read(folder / "engine_idle.ogg")
        old_cab = P.read(folder / "engine_cab.ogg")
        idle = S.highpass(S.repitch(S.cut(S.source(key), start, length), pitch))
        # A short stretch (the PLZ-05's six seconds) is chained into a 16 s loop that varies, as the working loops
        # are, so it doesn't beat every few seconds.
        idle = P.varying_loop(idle, 16.0, 4000 + len(vehicle)) if length < 10.0 else loop(idle)
        idle = S.level(idle, rms(old_idle))
        # The cab hears it through the hull as it hears the working engine (engine_cab: 1.3 kHz, 0.8), at the
        # level the cab loop has against the load loop.
        cab = S.highpass(S.repitch(S.cut(S.source(key), start, length), pitch))
        cab = S.level(P.varying_loop(cab, 16.0, 4000 + len(vehicle)) if length < 10.0 else loop(cab), 1.0)
        cab = S.level(S.lowpass(cab, S.CAB_CUTOFF), rms(old_cab) * 0.8)
        S.write(folder / "engine_idle.ogg", idle)
        S.write(folder / "engine_cab_idle.ogg", cab)
        print(f"{vehicle}: engine_idle {len(idle) / S.RATE:.1f} s, engine_cab_idle {len(cab) / S.RATE:.1f} s")


if __name__ == "__main__":
    main()
