"""The Mk42's and BL-132's reloads, each its own (the user's reviews: 2026-11-18, "a heavy shell being dropped to the
ground and another slid in"; 2026-11-19, "reload sound for the 2 heavy naval guns should feel different").

python -I design/mk42/prepare_reload_audio.py

Both guns had the same spent case and the same ram layered over their reloads, so they sounded alike. Now (load_1..3
each; vehicle.mk42.load / vehicle.bl132.load pick one at random, at the start and the end of a reload):

- Mk42, an automatic mount refilling its loader drum: machinery. An electric drive running up and labouring under the
  load (mpuffenbarger 683806, an electric actuator under load, a little lowered), the SBW cannon reload's quick
  mechanical clicks at full strength over it, a crisp breech slam (EpicWizard 264889, a steel door struck, near its own
  pitch) and a short hydraulic hiss as the rammer returns (Filmscore 823069, hydraulic brakes). No case on the deck.
- BL-132, a crew loading twin 130 mm guns by hand: weight. Two big cases thrown down on the deck one after the other
  (7of9Designs 640204, a heavy metal thud, under GrayJoy 210102's brass case dropped on concrete played at a third of
  its pitch, so it booms and rings long), then two heavy rams (the steel door at 0.55 of its pitch, its brightness
  taken off), over the SBW BL-132 reload kept low for the mechanism. No motor.

It builds from the SBW reloads as committed in 9146413 (git show), so it can be re-run. Voices: none (object
recordings). Please listen.
"""
from __future__ import annotations

import importlib.util
import io
import subprocess
from pathlib import Path

import numpy as np
from scipy import signal

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
spec = importlib.util.spec_from_file_location("starts", ROOT / "design/ground-vehicles/prepare_starts.py")
S = importlib.util.module_from_spec(spec)
spec.loader.exec_module(S)
sf = S.sf
S.SOURCES.update({
    "brass": ("GrayJoy", "210102", "Brass bullet shell casing drop onto concrete, multiple takes"),
    "shed": ("EpicWizard", "264889", "SFX Metal Shed Door - Hits, Clangs, Rattle"),
    "actuator": ("mpuffenbarger", "683806", "SFX Electric ACtuator Under Load 1.mp3"),
    "thud": ("7of9Designs", "640204", "Heavy Metal Thud on Ground"),
    "hydraulic": ("Filmscore", "823069", "Hydraulic Truck Brakes"),
})
SOUNDS = ROOT / "src/main/resources/assets/jeg/sounds/vehicle"
SBW_COMMIT = "9146413"
# Mk42: where each take's drive, slam and hiss start in their recordings (s).
MK42_DRIVE = (3.7, 5.65, 9.95)
MK42_SLAM = (36.69, 52.29, 72.42)
MK42_HISS = (4.2, 10.06, 2.51)
# BL-132: the brass drop and the two rams of each take.
BL132_BRASS = (4.45, 7.94, 10.42)
BL132_RAMS = ((45.96, 57.57), (76.98, 88.02), (102.74, 107.78))


def band(data, low, high):
    return signal.sosfilt(signal.butter(3, [low, high], fs=S.RATE, btype="bandpass", output="sos"), data)


def sbw(gun, i):
    raw = subprocess.run(["git", "show", f"{SBW_COMMIT}:src/main/resources/assets/jeg/sounds/vehicle/{gun}/load_{i + 1}.ogg"],
                         cwd=ROOT, check=True, capture_output=True).stdout
    data, rate = sf.read(io.BytesIO(raw), always_2d=True)
    data = np.mean(data, axis=1)
    if rate != S.RATE:
        data = signal.resample_poly(data, S.RATE, rate)
    return data


def place(out, sound, at):
    start = int(at * S.RATE)
    end = min(len(out), start + len(sound))
    out[start:end] += sound[:end - start]


def mk42(i, actuator, shed, hydraulic):
    out = np.zeros(int(2.4 * S.RATE))
    drive = S.repitch(S.cut(actuator, MK42_DRIVE[i], 1.5), 0.82)
    place(out, S.level(S.fade(band(drive, 70, 5000), 0.08, 0.35), 0.07), 0.0)
    place(out, S.level(sbw("mk42", i), 0.11), 0.12)
    slam = S.repitch(S.cut(shed, MK42_SLAM[i], 0.6), 0.95)
    place(out, S.level(S.fade(band(slam, 150, 8000), 0.002, 0.3), 0.14), 1.62)
    hiss = S.cut(hydraulic, MK42_HISS[i], 0.45)
    place(out, S.level(S.fade(band(hiss, 1500, 9000), 0.01, 0.25), 0.03), 1.78)
    return out


def bl132(i, brass, thud, shed):
    out = np.zeros(int(2.3 * S.RATE))
    for k, at in enumerate((0.0, 0.34)):
        boom = S.repitch(thud, 0.9 + 0.12 * k)
        place(out, S.level(S.fade(band(boom, 30, 2500), 0.002, 0.2), 0.16), at)
        ring = S.repitch(S.cut(brass, BL132_BRASS[(i + k) % 3], 0.55), 0.33 + 0.03 * k)
        place(out, S.level(S.fade(band(ring, 60, 6000), 0.002, 0.6), 0.06), at + 0.01)
    place(out, S.level(sbw("bl132", i), 0.05), 0.4)
    for k, at in enumerate((0.85, 1.3)):
        ram = S.repitch(S.cut(shed, BL132_RAMS[i][k], 0.7), 0.55 + 0.04 * k)
        ram = S.lowpass(band(ram, 35, 6000), 2600)
        place(out, S.level(S.fade(ram, 0.002, 0.45), 0.17), at)
    return out


def finish(out):
    out = np.tanh(out / max(1e-9, np.max(np.abs(out))) * 1.6) / np.tanh(1.6)
    return S.level(S.fade(out, 0.001, 0.35), 0.15, 0.8)


def main():
    brass, shed = S.source("brass"), S.source("shed")
    actuator, thud, hydraulic = S.source("actuator"), S.source("thud"), S.source("hydraulic")
    for i in range(3):
        S.write(SOUNDS / "mk42" / f"load_{i + 1}.ogg", finish(mk42(i, actuator, shed, hydraulic)))
        S.write(SOUNDS / "bl132" / f"load_{i + 1}.ogg", finish(bl132(i, brass, thud, shed)))
    notes = {
        "mk42": ("\n`load_1.ogg` .. `load_3.ogg` (2026-11-19, `design/mk42/prepare_reload_audio.py`): the automatic loader: "
                 "an electric drive (\"SFX Electric ACtuator Under Load 1\" by mpuffenbarger, "
                 "https://freesound.org/people/mpuffenbarger/sounds/683806/, CC0 1.0, lowered), the SBW cannon reload above, "
                 "a breech slam (\"SFX Metal Shed Door - Hits, Clangs, Rattle\" by EpicWizard, "
                 "https://freesound.org/people/EpicWizard/sounds/264889/, CC0 1.0) and a hydraulic hiss (\"Hydraulic Truck "
                 "Brakes\" by Filmscore, https://freesound.org/people/Filmscore/sounds/823069/, CC0 1.0).\n"),
        "bl132": ("\n`load_1.ogg` .. `load_3.ogg` (2026-11-19, `design/mk42/prepare_reload_audio.py`): the crew loading by hand: "
                  "two big cases on the deck (\"Heavy Metal Thud on Ground\" by 7of9Designs, "
                  "https://freesound.org/people/7of9Designs/sounds/640204/, CC0 1.0, under \"Brass bullet shell casing drop "
                  "onto concrete\" by GrayJoy, https://freesound.org/people/GrayJoy/sounds/210102/, CC0 1.0, lowered) and two "
                  "heavy rams (\"SFX Metal Shed Door - Hits, Clangs, Rattle\" by EpicWizard, "
                  "https://freesound.org/people/EpicWizard/sounds/264889/, CC0 1.0, lowered), over the SBW BL-132 reload "
                  "above.\n"),
    }
    for gun, note in notes.items():
        credits = SOUNDS / gun / "CREDITS.md"
        text = credits.read_text(encoding="utf-8")
        cut = text.find("\n`load_1.ogg` .. `load_3.ogg` (2026-11-1")
        if cut >= 0:
            text = text[:cut].rstrip("\n") + "\n"
        credits.write_text(text + note, encoding="utf-8")
        print(gun, "done")


if __name__ == "__main__":
    main()
