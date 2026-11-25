"""The CH-47's sounds (the user, 2026-11-21: "realistic Chinook sounds, startup, flying and landing, clean and high
quality"; "it doesn't need to be a real Chinook, just a heavy helicopter").

python -I design/chinook/prepare_audio.py

No clean close recording of a Chinook is free to use: Freesound's are distant flyovers with wind, birds and a dog
(searched 2026-11-21). So the sounds come from the cleanest close recordings of turbine helicopters there, made heavier:
slowed to 0.93 (a bigger engine's lower whine, a slower, deeper rotor beat), the low end raised, hiss and wind
filtered off, and the rotor doubled from two stretches of the take, so two rotors beat against each other as a tandem's do.

- startup: a UH-1's cold start, 20 s (OroborosNZ 157722, CC BY 4.0, a hand-held stereo mic within 20 m): the igniter's
  snaps, the turbine spooling up, its second sweep, the rotor coming up to speed. A second engine lights 3.5 s behind
  the first (the same spool, its whine only). Cut at 0.5-15.5 s and 22-27.5 s, joined over 2 s.
- flight: the same UH-1 running at flight RPM close by (122-146 s and 148-172 s laid over each other), a 24 s loop.
- slap: its closest pass (242-253 s), where the blades slap hardest, levelled and looped: the layer HelicopterEngineAudio
  raises while the helicopter comes down (blade-vortex slap, the sound of a helicopter landing) and in hard turns.
- engine_distant: a helicopter approaching from far off (Fission9 693866, CC0, 22-60 s), levelled, a loop; its
  distant blade slaps rounded off by a soft limiter so they don't hold the whole loop down.
- shutdown: the turbines spooling down when the engines are cut on landing: the UH-1's spool-up, its whine only (after the igniter's
  snaps), played backwards (the rotor's own wind-down is the flight loop slowing, HelicopterEngineAudio).

Voices: the cut stretches carry none (syllabic modulation of the 300-3400 Hz band below 0.15; the take's 30-42 s, which
reads higher, is left out). Please listen.

Each source page is checked for its licence (CC0 or CC BY 4.0) before use. The sources are Freesound's public high-quality
previews (Ogg Vorbis), cached under build/ch47-sound-sources/; an original file dropped there as <id>.wav or <id>.flac
(downloaded with a Freesound login) is used instead.
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
sys.path.insert(0, str(ROOT / "build/audio-tools-local"))
import soundfile as sf  # noqa: E402

RATE = 44100
OUT = ROOT / "src/main/resources/assets/jeg/sounds/vehicle/ch47"
CACHE = ROOT / "build/ch47-sound-sources"
LICENCES = {
    "cc0": "creativecommons.org/publicdomain/zero/1.0",
    "cc-by-4.0": "creativecommons.org/licenses/by/4.0",
}
SOURCES = {
    "huey": ("OroborosNZ", "157722", "UH-1 \"Huey\" Iroquois Helicopter", "cc-by-4.0"),
    "approach": ("Fission9", "693866", "Helicopter Landing and Idle", "cc0"),
    # The cabin's moving parts (doors()).
    "ramp_run": ("granconill", "156360", "piranha_rampe.mp3", "cc0"),
    "ramp_end": ("SoundsofTheMachine", "784981", "Motors_Hydraulic_Handycap_Ramp", "cc0"),
    "door_roll": ("Bowesy", "522127", "Train/Container - Sliding Door", "cc0"),
    "door_shut": ("Sadiquecat", "819037", "Ford 115 T350 - Side door sliding shut", "cc0"),
    "latch": ("suspensiondigital", "389705", "Box with Metal Latches Hit 2.wav", "cc0"),
    "window": ("kyles", "407326", "window metal heavy slide open close creak", "cc0"),
}
HEAVY = 0.93


def source(key):
    author, identity, title, licence = SOURCES[key]
    url = f"https://freesound.org/people/{author}/sounds/{identity}/"
    CACHE.mkdir(parents=True, exist_ok=True)
    page_cache = CACHE / (identity + ".html")
    if not page_cache.exists():
        request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (asset preparation script)"})
        page_cache.write_text(urllib.request.urlopen(request, timeout=30).read().decode(), encoding="utf-8")
    page = page_cache.read_text(encoding="utf-8")
    if LICENCES[licence] not in page:
        raise RuntimeError(f"{url} is not marked {licence}; not using it")
    originals = [CACHE / f"{identity}.{ext}" for ext in ("wav", "flac", "aiff")]
    target = next((p for p in originals if p.exists()), CACHE / (identity + "-hq.ogg"))
    if not target.exists():
        links = re.findall(r'https://cdn\.freesound\.org/previews/[^\s"<>]+-hq\.mp3', page)
        if not links:
            raise RuntimeError("No public preview found for " + url)
        request = urllib.request.Request(links[0].replace("-hq.mp3", "-hq.ogg"),
                                         headers={"User-Agent": "Mozilla/5.0 (asset preparation script)"})
        target.write_bytes(urllib.request.urlopen(request, timeout=120).read())
    data, rate = sf.read(target, always_2d=True)
    data = np.mean(data, axis=1)
    if rate != RATE:
        g = math.gcd(rate, RATE)
        data = signal.resample_poly(data, RATE // g, rate // g)
    return data - np.mean(data)


def cut(data, start, end):
    return data[round(start * RATE):round(end * RATE)].copy()


def sos(kind, freq, order=4):
    return signal.butter(order, freq, fs=RATE, btype=kind, output="sos")


def heavy(data, speed=HEAVY):
    """Bigger: played slower (lower and slower), the low end up 4 dB under 160 Hz, wind under 28 Hz and hiss over 11 kHz
    off."""
    n = int(len(data) / speed)
    data = np.interp(np.arange(n) * speed, np.arange(len(data)), data)
    data = signal.sosfiltfilt(sos("highpass", 28.0, 2), data)
    data = signal.sosfiltfilt(sos("lowpass", 11000.0, 4), data)
    low = signal.sosfiltfilt(sos("lowpass", 160.0, 2), data)
    return data + low * (10 ** (4 / 20) - 1)


def splice(a, b, seconds):
    """a then b, joined by an equal-power crossfade of `seconds`."""
    n = round(seconds * RATE)
    t = np.linspace(0, np.pi / 2, n)
    return np.concatenate([a[:-n], a[-n:] * np.cos(t) + b[:n] * np.sin(t), b[n:]])


def loop(data, seam=1.0):
    """A seamless loop: the tail crossfaded (equal power) into the head."""
    n = round(seam * RATE)
    t = np.linspace(0, np.pi / 2, n)
    body, tail = data[:-n].copy(), data[-n:]
    body[:n] = body[:n] * np.sin(t) + tail * np.cos(t)
    return body


def even(data, window=2.0):
    """Slow level changes (a pass coming and going) flattened, the beat inside each window kept."""
    n = round(window * RATE)
    power = signal.sosfiltfilt(sos("lowpass", 1.0 / window, 2), data ** 2)
    gain = 1.0 / np.sqrt(np.maximum(power, 1e-12))
    gain /= np.median(gain)
    return data * np.clip(gain, 0.25, 4.0) if n else data


def fade(data, attack, release):
    data = data.copy()
    a, r = round(attack * RATE), round(release * RATE)
    if a:
        data[:a] *= np.sin(np.linspace(0, np.pi / 2, a)) ** 2
    if r:
        data[-r:] *= np.cos(np.linspace(0, np.pi / 2, r)) ** 2
    return data


def level(data, rms, peak=0.89):
    data = data * (rms / max(1e-9, np.sqrt(np.mean(data ** 2))))
    top = np.max(np.abs(data))
    return data * (peak / top) if top > peak else data


def soft_limit(data, rms, knee=0.45):
    """Levelled to `rms`, then peaks over the knee rounded off (tanh) rather than the whole take turned down for them."""
    data = data * (rms / max(1e-9, np.sqrt(np.mean(data ** 2))))
    over = np.abs(data) > knee
    out = data.copy()
    out[over] = np.sign(data[over]) * (knee + (0.9 - knee) * np.tanh((np.abs(data[over]) - knee) / (0.9 - knee)))
    return out


def write(name, data):
    path = OUT / f"{name}.ogg"
    # In blocks: libsndfile's Vorbis encoder crashes the interpreter on one large write.
    with sf.SoundFile(path, "w", RATE, 1, format="OGG", subtype="VORBIS") as out:
        for i in range(0, len(data), 4096):
            out.write(data[i:i + 4096])
    decoded, rate = sf.read(path)
    assert rate == RATE and decoded.ndim == 1 and np.isfinite(decoded).all()
    assert np.max(np.abs(decoded)) < 0.98, (path, "clips")
    return decoded


def rotor_rate(data):
    env = np.abs(signal.sosfilt(sos("bandpass", [60, 2000]), data))
    env = signal.sosfilt(sos("lowpass", 60.0, 2), env)[::220]
    f, p = signal.welch(env - env.mean(), fs=RATE / 220, nperseg=min(len(env), 800))
    band = (f >= 5) & (f <= 40)
    return float(f[band][np.argmax(p[band])])


def syllabic(data):
    """The share of the speech band's modulation at syllable rate (3-7 Hz): high where someone talks."""
    env = np.abs(signal.sosfilt(sos("bandpass", [300, 3400]), data))
    env = signal.sosfilt(sos("lowpass", 30.0, 2), env)[::220]
    f, p = signal.welch(env - env.mean(), fs=RATE / 220, nperseg=min(len(env), 256))
    return float(np.sum(p[(f >= 3) & (f <= 7)]) / max(np.sum(p[(f >= 0.5) & (f <= 40)]), 1e-20))


def repitch(data, factor):
    n = int(len(data) / factor)
    return np.interp(np.arange(n) * factor, np.arange(len(data)), data)


def doors():
    """The cabin's moving parts (the user, 2026-11-21: "the doors opening should have sounds"), real CC0 recordings:
    - ramp_hydraulic: a Piranha APC's rear ramp running (granconill 156360), 0.5-10.5 s, a loop while the ramp (and
      its upper door) move (Ch47CabinAudio);
    - ramp_stop: a powered ramp's motor stopping on its end stop with a heavy clunk (SoundsofTheMachine 784981,
      10.0-11.8 s), when the ramp reaches closed, level or the ground;
    - door_open: a heavy sliding door rolled open on its track to its stop (Bowesy 522127, 0-2.8 s);
    - door_close: a van's sliding side door rolled shut and latched (Sadiquecat 819037, 0.3-2.4 s);
    - door_step: a metal latch struck, lowered (suspensiondigital 389705): the crew door's lower half landing as a step;
    - gun_window: one stroke of a heavy metal-framed window sliding (kyles 407326, 1.45-2.75 s)."""
    report = {}
    takes = {key: source(key) for key in ("ramp_run", "ramp_end", "door_roll", "door_shut", "latch", "window")}
    made = {
        "ramp_hydraulic": level(loop(signal.sosfiltfilt(sos("highpass", 45.0, 2), cut(takes["ramp_run"], 0.5, 10.5)), 1.0), 0.10),
        "ramp_stop": level(fade(signal.sosfiltfilt(sos("highpass", 35.0, 2), cut(takes["ramp_end"], 10.0, 11.8)), 0.08, 0.3), 0.10),
        "door_open": level(fade(signal.sosfiltfilt(sos("highpass", 40.0, 2), cut(takes["door_roll"], 0.0, 2.8)), 0.02, 0.4), 0.10),
        "door_close": level(fade(signal.sosfiltfilt(sos("highpass", 40.0, 2), cut(takes["door_shut"], 0.3, 2.4)), 0.05, 0.4), 0.10),
        "door_step": level(fade(signal.sosfiltfilt(sos("lowpass", 6000.0, 2), repitch(takes["latch"], 0.8)), 0.0, 0.1), 0.09),
        "gun_window": level(fade(signal.sosfiltfilt(sos("highpass", 60.0, 2), cut(takes["window"], 1.45, 2.75)), 0.02, 0.25), 0.08),
    }
    for name, data in made.items():
        decoded = write(name, data)
        report[name] = {"seconds": round(len(decoded) / RATE, 2), "rms": round(float(np.sqrt(np.mean(decoded ** 2))), 3),
                        "peak": round(float(np.max(np.abs(decoded))), 3)}
    return report


def ramp_beep():
    """ramp_beep: the ramp warning's beep, one per flash of the red jump light while the ramp travels (the user,
    2026-11-21: "make the heli lights flash when the ramp goes down with a beep sound"). An electronic warning tone, made
    as one is: a 2.4 kHz piezo sounder (its square-ish drive gives odd harmonics), 0.16 s with a 4 ms rise and a short
    ring-down, through a small housing (band-passed 900 Hz-6 kHz). Synthesised, no recording."""
    n = round(0.20 * RATE)
    t = np.arange(n) / RATE
    f = 2400.0
    tone = np.sin(2 * np.pi * f * t) + 0.28 * np.sin(2 * np.pi * 3 * f * t) + 0.12 * np.sin(2 * np.pi * 5 * f * t)
    env = np.clip(t / 0.004, 0, 1) * np.where(t < 0.16, 1.0, np.exp(-(t - 0.16) / 0.012))
    beep = signal.sosfiltfilt(sos("bandpass", (900.0, 6000.0), 2), tone * env)
    decoded = write("ramp_beep", level(fade(beep, 0.0, 0.01), 0.07))
    return {"seconds": round(len(decoded) / RATE, 2), "rms": round(float(np.sqrt(np.mean(decoded ** 2))), 3),
            "peak": round(float(np.max(np.abs(decoded))), 3)}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    huey, approach = source("huey"), source("approach")

    # Startup: ignition and the first engine's spool (0.5-15.5 s), joined to the second sweep and the rotor coming up
    # (22-27.5 s); the second engine's whine 3.5 s behind.
    first = splice(cut(huey, 0.5, 15.5), cut(huey, 22.0, 27.5), 2.0)
    whine = signal.sosfiltfilt(sos("highpass", 900.0), first)
    delay = round(3.5 * RATE)
    second = np.concatenate([np.zeros(delay), whine[:-delay]]) * 10 ** (-5 / 20)
    startup = heavy(first + second)
    # Ends on the flight loop's head, so the engine sound's 1.5 s crossfade into it meets the same sound.
    startup = level(fade(startup, 0.05, 0.0), 0.085)

    a, b = heavy(cut(huey, 122.0, 146.0)), heavy(cut(huey, 148.0, 172.0))
    flight = level(loop(0.72 * a + 0.72 * b, 1.2), 0.14)

    slap = heavy(cut(huey, 242.0, 253.5))
    slap = signal.sosfiltfilt(sos("highpass", 70.0, 2), even(slap))
    slap = level(loop(slap, 0.8), 0.13)

    distant = heavy(cut(approach, 22.0, 60.0))
    distant = signal.sosfiltfilt(sos("lowpass", 2600.0, 2), even(distant, 3.0))
    distant = soft_limit(loop(distant, 1.5), 0.14)

    spool = signal.sosfiltfilt(sos("highpass", 700.0), heavy(cut(huey, 5.0, 24.0)))[::-1]
    shutdown = level(fade(spool, 0.3, 2.5), 0.06)

    report = {}
    for name, data, source_cut in (("startup", startup, cut(huey, 0.5, 27.5)), ("flight", flight, cut(huey, 122.0, 172.0)),
                                   ("slap", slap, cut(huey, 242.0, 253.5)), ("engine_distant", distant, cut(approach, 22.0, 60.0)),
                                   ("shutdown", shutdown, cut(huey, 5.0, 24.0))):
        decoded = write(name, data)
        report[name] = {"seconds": round(len(decoded) / RATE, 2), "rms": round(float(np.sqrt(np.mean(decoded ** 2))), 3),
                        "peak": round(float(np.max(np.abs(decoded))), 3), "rotor_hz": round(rotor_rate(decoded), 1),
                        "source_syllabic": round(syllabic(source_cut), 3)}
        if name in ("flight", "slap", "engine_distant"):
            report[name]["seam_jump"] = round(float(abs(decoded[-1] - decoded[0])), 4)
    report["doors"] = doors()
    report["ramp_beep"] = ramp_beep()
    (HERE / "audio-validation.json").write_text(json.dumps(report, indent=1) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=1))


if __name__ == "__main__":
    main()
