"""Prepare the ground vehicles' gun report sets (fleet plan Phase 0.3): near, distant and crew recordings per gun, played
by GunReportAudio on the aircraft distance curve (guide section 7).

python -I design/superbwarfare-port/fetch_sbw.py <sbw_dir> --extra sounds/gun/m_2_hb/m_2_hb_far.ogg \
    sounds/gun/m_2_hb/m_2_hb_fire_1p.ogg sounds/gun/m_2_hb/m_2_hb_fire_3p.ogg sounds/gun/m_2_hb/m_2_hb_veryfar.ogg \
    sounds/vehicle/bmp_2/bmp_2_cannon_fire_1p.ogg sounds/vehicle/bmp_2/bmp_2_cannon_fire_3p.ogg \
    sounds/vehicle/common/coax_fire_1p.ogg sounds/vehicle/lav_150/lav_150_cannon_far.ogg \
    sounds/vehicle/lav_150/lav_150_cannon_fire_1p.ogg sounds/vehicle/lav_150/lav_150_cannon_fire_3p.ogg \
    sounds/vehicle/lav_150/lav_150_cannon_veryfar.ogg
python -I design/vehicle-guns/prepare_audio.py <sbw_dir>

Superb Warfare recorded near, crew (1p) and far takes for the 20 mm and the M2 12.7 mm: those are copied unchanged.
The 30 mm and the coaxial MG have no far take; theirs is derived from the near one: low-passed to what carries (about
900 Hz), the attack softened and a ground-echo tail added, as the distant layers of the bulldozer and the FSV-6 were.
Requires numpy, scipy and soundfile (the workspace's local soundfile install is used when the package is missing).
"""
from __future__ import annotations

from pathlib import Path
import sys

import numpy as np
from scipy import signal

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
try:
    import soundfile as sf
except ImportError:
    sys.path.insert(0, str(ROOT / "build/audio-tools-local"))
    import soundfile as sf

ASSETS = ROOT / "src/main/resources/assets/jeg/sounds"
OUT = ASSETS / "vehicle/gun"

# gun -> (near, crew, distant): ("sbw", path) copies an SBW take; ("mod", path) one already in the mod;
# ("derive", source) low-passes a near take into the distant layer.
GUNS = {
    "20mm": (("sbw", "vehicle/lav_150/lav_150_cannon_fire_3p.ogg"), ("sbw", "vehicle/lav_150/lav_150_cannon_fire_1p.ogg"),
             ("sbw", "vehicle/lav_150/lav_150_cannon_far.ogg")),
    "30mm": (("sbw", "vehicle/bmp_2/bmp_2_cannon_fire_3p.ogg"), ("sbw", "vehicle/bmp_2/bmp_2_cannon_fire_1p.ogg"),
             ("derive", ("sbw", "vehicle/bmp_2/bmp_2_cannon_fire_3p.ogg"))),
    "hmg": (("sbw", "gun/m_2_hb/m_2_hb_fire_3p.ogg"), ("sbw", "gun/m_2_hb/m_2_hb_fire_1p.ogg"),
            ("sbw", "gun/m_2_hb/m_2_hb_far.ogg")),
    "coax": (("mod", "item/light_machine_gun/fire.ogg"), ("sbw", "vehicle/common/coax_fire_1p.ogg"),
             ("derive", ("mod", "item/light_machine_gun/fire.ogg"))),
}


def read(sbw, kind, path):
    data, rate = sf.read(Path(sbw) / "sounds" / path if kind == "sbw" else ASSETS / path, always_2d=True)
    return np.mean(data, axis=1), rate


def distant(data, rate):
    """What a gun sounds like from far off: the highs gone, the crack softened, and the report rolling back off the
    ground for most of a second."""
    sos = signal.butter(4, 900.0, "lowpass", fs=rate, output="sos")
    low = signal.sosfilt(sos, data)
    attack = int(.02 * rate)
    low[:attack] *= np.linspace(0.0, 1.0, attack)
    tail = np.zeros(len(low) + int(.9 * rate))
    tail[:len(low)] += low
    for delay, gain in ((.11, .45), (.23, .3), (.41, .18), (.62, .1)):
        start = int(delay * rate)
        tail[start:start + len(low)] += low * gain
    tail *= np.linspace(1.0, 0.0, len(tail)) ** .5
    peak = np.max(np.abs(tail)) or 1.0
    return tail / peak * .7


def main():
    sbw = sys.argv[1]
    credits = []
    for gun, (near, crew, far) in GUNS.items():
        out = OUT / gun
        out.mkdir(parents=True, exist_ok=True)
        for layer, (kind, path) in (("fire", near), ("fire_interior", crew)):
            source = Path(sbw) / "sounds" / path if kind == "sbw" else ASSETS / path
            (out / f"{layer}.ogg").write_bytes(source.read_bytes())
            credits.append((gun, layer, kind, path, "unchanged"))
        kind, path = far
        if kind == "derive":
            data, rate = read(sbw, *path)
            # In blocks: libsndfile's Vorbis encoder overflows the stack on Windows given a long buffer at once.
            samples = np.ascontiguousarray(distant(data, rate), dtype=np.float32)
            with sf.SoundFile(out / "fire_distant.ogg", "w", samplerate=rate, channels=1, format="OGG", subtype="VORBIS") as f:
                for start in range(0, len(samples), 4096):
                    f.write(samples[start:start + 4096])
            credits.append((gun, "fire_distant", path[0], path[1], "low-passed (900 Hz), softened attack, ground-echo tail"))
        else:
            (out / "fire_distant.ogg").write_bytes((Path(sbw) / "sounds" / path).read_bytes())
            credits.append((gun, "fire_distant", kind, path, "unchanged"))
    rows = "".join(f"| `{g}/{layer}.ogg` | {'SBW `' + p + '`' if k == 'sbw' else 'JEG `' + p + '`'} | {how} |\n"
                   for g, layer, k, p, how in credits)
    (OUT / "CREDITS.md").write_text(
        "# Vehicle gun reports\n\nNear, distant and crew recordings for the ground vehicles' guns, played by "
        "`GunReportAudio`. SBW recordings are by the Superb Warfare development team, used with their permission "
        "(`design/superbwarfare-port/PERMISSION.md`). Prepared by `design/vehicle-guns/prepare_audio.py`.\n\n"
        "| File | Source | Processing |\n| --- | --- | --- |\n" + rows, encoding="utf-8")
    print("\n".join(f"{g}/{layer}: {how}" for g, layer, _, _, how in credits))


if __name__ == "__main__":
    main()
