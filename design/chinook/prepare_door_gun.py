"""The CH-47 door guns' report set, voiced as an MG34 (the user, 2026-11-25: "make the sound of the side guns of a mg34
machine gun").

python -I design/chinook/prepare_door_gun.py
(after: python -I design/superbwarfare-port/fetch_sbw.py build/sbw-sources --extra sounds/gun/m_60/m_60_fire_3p.ogg
 sounds/gun/m_60/m_60_fire_1p.ogg sounds/gun/m_60/m_60_far.ogg sounds/gun/m_60/m_60_veryfar.ogg)

No recording of an MG34 firing could be found under a licence that can be checked: Freesound has none (its two MG42
takes are built from a commercial game's effects and from a YouTube clip), and the public-domain 1943 War Department
film "Enemy Weapons - German Infantry Small Arms" (Internet Archive EnemyWeapons, Commons "Mg 34.ogv") has narration and
handling sounds but no live fire. So the report is built from a real belt-fed general-purpose machine gun of the same
class, the M60 (Superb Warfare's recordings, used with the SBW team's permission, design/superbwarfare-port/PERMISSION.md),
voiced toward the MG34's heavier 7.92x57 Mauser round:
- fire.ogg (near): the M60's outside report pitched down 8% (a bigger case, a lower boom), with the first-person
  take's low thump (below 220 Hz) laid under it and the bolt's recoil clack 24 ms after the shot (the 1p take's
  second transient, band-passed), a short open-air ring-out, soft-clipped.
- fire_distant.ogg: the M60's far take pitched down the same, low-passed to what carries and rolled off the ground.
- fire_interior.ogg (the gunner in the door): the first-person take pitched down, its crack kept and the body full.
The guns keep their rate of fire: rounds can only leave on whole ticks, and an MG34's ~850 rounds a minute (1.4 ticks)
would come out as an uneven 1-2-1-2 gallop.

Writes src/main/resources/assets/jeg/sounds/vehicle/gun/door_gun/ and its CREDITS.md.
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from scipy import signal

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(ROOT / "build/audio-tools-local"))
import soundfile as sf  # noqa: E402

RATE = 44100
SBW = ROOT / "build/sbw-sources/sounds/gun/m_60"
OUT = ROOT / "src/main/resources/assets/jeg/sounds/vehicle/gun/door_gun"
PITCH = 0.92


def read(name):
    data, rate = sf.read(SBW / name, always_2d=True)
    data = np.mean(data, axis=1)
    assert rate == RATE, (name, rate)
    return data - np.mean(data)


def repitch(data, factor):
    n = int(len(data) / factor)
    return np.interp(np.arange(n) * factor, np.arange(len(data)), data)


def band(data, low=None, high=None, order=4):
    if low and high:
        sos = signal.butter(order, [low, high], "bandpass", fs=RATE, output="sos")
    elif low:
        sos = signal.butter(order, low, "highpass", fs=RATE, output="sos")
    else:
        sos = signal.butter(order, high, "lowpass", fs=RATE, output="sos")
    return signal.sosfilt(sos, data)


def pad(data, n):
    return np.concatenate([data, np.zeros(max(0, n - len(data)))])


def fade_out(data, seconds):
    n = min(len(data), int(seconds * RATE))
    data = data.copy()
    data[-n:] *= np.linspace(1.0, 0.0, n) ** 1.5
    return data


def soft_clip(data, drive):
    return np.tanh(data * drive) / np.tanh(drive)


def level(data, peak):
    return data * (peak / max(1e-9, np.max(np.abs(data))))


def ring(data, seconds, gain):
    """A short open-air ring-out: a few soft, darkening echoes."""
    out = pad(data, len(data) + int(max(seconds, 0.3) * RATE))
    dark = band(data, high=2500.0)
    for delay, g in ((0.045, gain), (0.09, gain * 0.6), (0.16, gain * 0.35), (0.27, gain * 0.18)):
        start = int(delay * RATE)
        out[start:start + len(dark)] += dark[:len(out) - start] * g
    return out


def write(path, data):
    with sf.SoundFile(path, "w", RATE, 1, format="OGG", subtype="VORBIS") as f:
        for i in range(0, len(data), 4096):
            f.write(data[i:i + 4096])
    decoded, rate = sf.read(path)
    assert rate == RATE and decoded.ndim == 1 and np.isfinite(decoded).all()
    assert np.max(np.abs(decoded)) < 0.98, (path, "clips")
    return decoded


def main():
    outside = repitch(read("m_60_fire_3p.ogg"), PITCH)
    inside = repitch(read("m_60_fire_1p.ogg"), PITCH)
    far = repitch(read("m_60_far.ogg"), PITCH)

    # Near: the outside report, its boom from the first-person take, the bolt's clack after it.
    n = max(len(outside), len(inside)) + int(0.4 * RATE)
    near = pad(outside, n) * 1.0
    near += pad(band(inside, high=220.0), n) * 0.55
    clack = band(inside, 1800.0, 6000.0)
    shift = int(0.024 * RATE)
    near[shift:shift + len(clack)] += clack[:n - shift] * 0.35
    near = ring(near, 0.35, 0.22)
    near = fade_out(near[: int(0.85 * RATE)], 0.35)
    near = level(soft_clip(level(near, 1.0), 1.8), 0.84)

    # Distant: the far take, darker, with its ground echo.
    distant = band(far, 35.0, 1400.0)
    distant = fade_out(distant[: int(1.9 * RATE)], 0.9)
    distant = level(distant, 0.75)

    # The gunner in the door: the first-person take, crack and body, a little room under it.
    crew = band(inside, 45.0, 12000.0)
    crew = ring(crew, 0.2, 0.12)
    crew = fade_out(crew[: int(0.75 * RATE)], 0.3)
    crew = level(soft_clip(level(crew, 1.0), 1.5), 0.8)

    OUT.mkdir(parents=True, exist_ok=True)
    for name, data in (("fire", near), ("fire_distant", distant), ("fire_interior", crew)):
        decoded = write(OUT / f"{name}.ogg", data)
        f, p = signal.welch(decoded, RATE, nperseg=4096)
        print(f"{name}: {len(decoded) / RATE:.2f} s, peak {20 * np.log10(np.max(np.abs(decoded))):.1f} dB, "
              f"centroid {(f * p).sum() / p.sum():.0f} Hz, below 200 Hz {p[f < 200].sum() / p.sum():.2f}")
    (OUT / "CREDITS.md").write_text("""# CH-47 door gun report (voiced as an MG34)

Built by `design/chinook/prepare_door_gun.py` from the M60 recordings of Superb Warfare (`gun/m_60/m_60_fire_3p.ogg`,
`m_60_fire_1p.ogg`, `m_60_far.ogg`), used with the SBW development team's permission
(`design/superbwarfare-port/PERMISSION.md`). No MG34 recording under a checkable licence was found; the M60 is a real
belt-fed machine gun of the same class, voiced toward the MG34's heavier 7.92x57 round.

| File | Processing |
| --- | --- |
| `fire.ogg` | the outside report pitched to 0.92, the first-person take's low thump (below 220 Hz) under it, the bolt's clack (1.8-6 kHz) 24 ms after, a short open-air ring-out, soft-clipped |
| `fire_distant.ogg` | the far take pitched to 0.92, band 35 Hz to 1.4 kHz, faded over 0.9 s |
| `fire_interior.ogg` | the first-person take pitched to 0.92, band 45 Hz to 12 kHz, a short ring, soft-clipped (the gunner in the door) |
""", encoding="utf-8")


if __name__ == "__main__":
    main()
