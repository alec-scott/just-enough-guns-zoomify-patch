"""The perimeter sentry's sounds (design/sentry/BRIEF.md "Sound"; fleet readiness gate G6: real recordings).

python -I design/sentry/prepare_audio.py

Writes:
- sounds/vehicle/gun/sentry/fire.ogg, fire_distant.ogg, fire_interior.ogg: the sentry gun's report set, played by
  GunReportAudio on the shared distance curve (VehicleGunReports "vehicle.gun.sentry"). From a real IWI Negev NG-7
  (a 7.62x51 machine gun, the sentry's round) in full auto, recorded close outdoors (areniporgen, CC0): the burst's
  last round with its natural ring-out, so each round has a clean report. Its cracks are 65 ms apart and each falls
  20 dB within 30 ms; an M60 recording (FranklinRook1984, 538302) was rejected because its rounds smear into one wash.
  The near take as recorded, the distant one low-passed to what carries with the ground echo
  (vehicle-guns/prepare_audio.py's `distant`), the operator's (watching through the camera) thinner and quieter.
  Heavier again (the user's review, 2026-11-19: "harder, think a Warthog's gun or Battlefield"): the Negev's crack now
  rides on the M2 heavy machine gun's report (SBW's m_2_hb, used with the SBW team's permission) for its weight, with a
  short low thump under the first 120 ms, the bolt's clack (the dry fire, lowered) 18 ms after the crack, and a harder
  saturation; the distant and operator takes follow.
- sounds/vehicle/sentry/auto_on.ogg, auto_off.ogg: the cue when the mount is switched to automatic and back: a relay
  clunk (the dry fire, lowered further), the mount's servo stirring (vehicle/movement/servo, a real drive), and the
  station's two-tone confirmation chirp, rising for on and falling for off.
- sounds/vehicle/sentry/dry_fire.ogg: the bolt closing on an empty chamber, from a real pistol dry fire
  (brianhanson2nd, CC0) lowered to 0.8 for a heavier action; played when the sentry is out of ammunition with a target.

Every source page is checked for its CC0 licence (prepare_starts.source). Sources are cached under
build/ground-vehicle-sources/. Requires numpy, scipy and soundfile.
"""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path

import numpy as np
from scipy import signal

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


starts = _load("starts", ROOT / "design/ground-vehicles/prepare_starts.py")
guns = _load("guns", ROOT / "design/vehicle-guns/prepare_audio.py")
RATE, sf = starts.RATE, starts.sf
SOUNDS = starts.SOUNDS
GUN_OUT = SOUNDS / "gun" / "sentry"
OUT = SOUNDS / "sentry"

starts.SOURCES.update({
    "negev": ("areniporgen", "725798", "IWI Negev NG-7"),
    "dry": ("brianhanson2nd", "171211", "Dry Fire"),
})

GUN_CREDITS = """# Sentry gun report

From "IWI Negev NG-7" by areniporgen, https://freesound.org/people/areniporgen/sounds/725798/ (CC0 1.0), a 7.62x51
machine gun in full auto recorded close outdoors: the burst's last round with its ring-out
(`design/sentry/prepare_audio.py`).

| File | Processing |
| --- | --- |
| `fire.ogg` | band 40 Hz to 16 kHz, its 60-250 Hz body raised 7 dB; 2026-11-19: laid over SBW's M2 HB report (`gun/m_2_hb/m_2_hb_fire_3p.ogg`, by permission, `design/superbwarfare-port/PERMISSION.md`) with a low thump under it and the bolt's clack, soft-clipped harder, levelled louder |
| `fire_distant.ogg` | low-passed (900 Hz), softened attack, ground-echo tail |
| `fire_interior.ogg` | band 120 Hz to 5 kHz, quieter (the operator at the camera) |
"""

SENTRY_CREDITS = """# Sentry

- `dry_fire.ogg`: "Dry Fire" by brianhanson2nd, https://freesound.org/people/brianhanson2nd/sounds/171211/ (CC0 1.0),
  the click cut out, lowered to 0.8 and band-passed (`design/sentry/prepare_audio.py`).
- `auto_on.ogg`, `auto_off.ogg`: the same click lowered to 0.55 (a relay), `vehicle/movement/servo.ogg` (see its
  credits) briefly, and a two-tone electronic chirp made here (an equipment beep), rising or falling.
"""

HMG = SOUNDS / "gun" / "hmg" / "fire.ogg"
SERVO = SOUNDS / "movement" / "servo.ogg"


def onset(data, share=0.3):
    return int(np.argmax(np.abs(data) > share * np.max(np.abs(data))))


def at(base, layer, offset):
    """layer added into base from sample offset (base grown as needed)."""
    end = offset + len(layer)
    if end > len(base):
        base = np.concatenate([base, np.zeros(end - len(base))])
    base[offset:offset + len(layer)] += layer
    return base


def mono(path):
    data, rate = sf.read(path)
    if data.ndim > 1:
        data = data.mean(axis=1)
    if rate != RATE:
        data = signal.resample(data, int(len(data) * RATE / rate))
    return data


def harder(negev_shot, click):
    """The Negev's crack on the M2's weight, a low thump under it and the bolt's clack, saturated."""
    m2 = mono(HMG)
    m2 = m2[max(0, onset(m2) - int(0.002 * RATE)):]
    crack = band(negev_shot, 40, 16000)
    crack = crack[max(0, onset(crack) - int(0.002 * RATE)):]
    m2 = m2 / max(1e-9, np.max(np.abs(m2)))
    crack = crack / max(1e-9, np.max(np.abs(crack)))
    mix = at(np.zeros(1), m2 * 0.85, 0)
    mix = at(mix, crack * 0.7, 0)
    # The thump: a fast-decaying low sine, as the blast's pressure on a close microphone.
    n = int(0.12 * RATE)
    tt = np.arange(n) / RATE
    thump = np.sin(2 * np.pi * (62 - 120 * tt) * tt) * np.exp(-tt / 0.03)
    mix = at(mix, thump * 0.75, 0)
    clack = band(starts.repitch(click, 0.75), 300, 9000)
    clack = clack / max(1e-9, np.max(np.abs(clack)))
    mix = at(mix, clack * 0.18, int(0.018 * RATE))
    mix = weighty(mix)
    mix = np.tanh(mix * 1.8) / np.tanh(1.8)
    return starts.fade(mix[:int(0.75 * RATE)], 0.0005, 0.3)


def chirp(rising):
    """Two short tones a fifth apart, rising (on) or falling (off): the station's confirmation."""
    tones = (880.0, 1320.0) if rising else (1320.0, 880.0)
    out = []
    for f in tones:
        n = int(0.075 * RATE)
        tt = np.arange(n) / RATE
        tone = (np.sin(2 * np.pi * f * tt) + 0.25 * np.sin(2 * np.pi * 2 * f * tt)) * np.minimum(1.0, tt / 0.004)
        out.append(starts.fade(tone, 0.003, 0.02))
        out.append(np.zeros(int(0.035 * RATE)))
    return np.concatenate(out)


def auto_cue(click, rising):
    relay = band(starts.repitch(click, 0.55), 120, 8000)
    relay = relay / max(1e-9, np.max(np.abs(relay)))
    servo = mono(SERVO)
    servo = starts.fade(servo[:int(0.35 * RATE)], 0.03, 0.15)
    servo = servo / max(1e-9, np.max(np.abs(servo)))
    if not rising:
        servo = starts.repitch(servo, 0.85)
    cue = at(np.zeros(1), relay * 0.9, 0)
    cue = at(cue, servo * 0.45, int(0.04 * RATE))
    cue = at(cue, chirp(rising) * 0.35, int(0.12 * RATE))
    return starts.level(starts.fade(cue, 0.001, 0.05), 0.08, 0.8)


def band(data, low, high):
    return signal.sosfilt(signal.butter(3, [low, high], fs=RATE, btype="bandpass", output="sos"), data)


def last_shot(burst):
    """The burst's last round, from its crack's onset (found on the high-passed envelope: the last rise of 12 dB
    within 20 ms) with 0.7 s of ring-out."""
    high = signal.sosfilt(signal.butter(4, 3000, fs=RATE, btype="highpass", output="sos"), burst)
    w = int(0.001 * RATE)
    env = 20 * np.log10(np.array([np.sqrt(np.mean(high[i:i + w] ** 2)) for i in range(0, len(high) - w, w)]) + 1e-9)
    onsets = [i for i in range(20, len(env)) if env[i] - env[i - 20:i].min() > 12 and env[i] > env.max() - 25]
    onset = onsets[-1]
    while onset > 0 and env[onset] - env[onset - 1] > 0.5:
        onset -= 1
    start = max(0, onset * w - int(0.002 * RATE))
    shot = burst[start:start + int(0.7 * RATE)].copy()
    return starts.fade(shot, 0.001, 0.3), start / RATE


def weighty(data):
    body = signal.sosfilt(signal.butter(2, [60, 250], fs=RATE, btype="bandpass", output="sos"), data)
    mixed = data + body * 1.25
    mixed = mixed / max(1e-9, np.max(np.abs(mixed)))
    return np.tanh(mixed * 2.6) / np.tanh(2.6)


def main():
    negev = starts.source("negev")
    dry = starts.source("dry")
    shot, at = last_shot(negev)
    # Weight and grit (the user's review, 2026-11-18: "too plastic, should feel solid and heavy, louder, more grit"):
    # the shot's own body (60-250 Hz) brought up 7 dB under it, and the whole driven through a soft clipper, as a
    # heavy gun's report saturates a close microphone; louder than before.
    heavy = harder(shot, starts.cut(dry, max(0.0, int(np.argmax(np.abs(dry))) / RATE - 0.01), 0.25))
    near = starts.level(heavy, 0.25, 0.82)
    interior = starts.level(band(heavy, 70, 6000), 0.14, 0.75)
    far = guns.distant(heavy, RATE) * 0.9
    click_at = int(np.argmax(np.abs(dry)))
    click = starts.cut(dry, max(0.0, click_at / RATE - 0.01), 0.25)
    click = starts.level(starts.fade(band(starts.repitch(click, 0.8), 150, 12000), 0.001, 0.12), 0.06, 0.6)

    GUN_OUT.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    report = {}
    for path, data in ((GUN_OUT / "fire.ogg", near), (GUN_OUT / "fire_distant.ogg", far),
                       (GUN_OUT / "fire_interior.ogg", interior), (OUT / "dry_fire.ogg", click),
                       (OUT / "auto_on.ogg", auto_cue(starts.cut(dry, max(0.0, click_at / RATE - 0.01), 0.25), True)),
                       (OUT / "auto_off.ogg", auto_cue(starts.cut(dry, max(0.0, click_at / RATE - 0.01), 0.25), False))):
        starts.write(path, data)
        decoded, _ = sf.read(path)
        report[path.relative_to(SOUNDS).as_posix()] = {"s": round(len(decoded) / RATE, 2),
                                                       "peak": round(float(np.max(np.abs(decoded))), 3)}
    report["negev_last_shot_at_s"] = round(at, 3)
    (HERE / "audio-validation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    (GUN_OUT / "CREDITS.md").write_text(GUN_CREDITS, encoding="utf-8")
    (OUT / "CREDITS.md").write_text(SENTRY_CREDITS, encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
