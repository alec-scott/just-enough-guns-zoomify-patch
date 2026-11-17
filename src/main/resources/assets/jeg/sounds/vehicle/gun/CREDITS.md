# Vehicle gun reports

Near, distant and crew recordings for the ground vehicles' guns, played by `GunReportAudio`. SBW recordings are by the Superb Warfare development team, used with their permission (`design/superbwarfare-port/PERMISSION.md`). Prepared by `design/vehicle-guns/prepare_audio.py`.

| File | Source | Processing |
| --- | --- | --- |
| `20mm/fire.ogg` | SBW `vehicle/lav_150/lav_150_cannon_fire_3p.ogg` | unchanged |
| `20mm/fire_interior.ogg` | SBW `vehicle/lav_150/lav_150_cannon_fire_1p.ogg` | unchanged |
| `20mm/fire_distant.ogg` | SBW `vehicle/lav_150/lav_150_cannon_far.ogg` | unchanged |
| `30mm/fire.ogg` | SBW `vehicle/bmp_2/bmp_2_cannon_fire_3p.ogg` | unchanged |
| `30mm/fire_interior.ogg` | SBW `vehicle/bmp_2/bmp_2_cannon_fire_1p.ogg` | unchanged |
| `30mm/fire_distant.ogg` | SBW `vehicle/bmp_2/bmp_2_cannon_fire_3p.ogg` | low-passed (900 Hz), softened attack, ground-echo tail |
| `hmg/fire.ogg` | SBW `gun/m_2_hb/m_2_hb_fire_3p.ogg` | unchanged |
| `hmg/fire_interior.ogg` | SBW `gun/m_2_hb/m_2_hb_fire_1p.ogg` | unchanged |
| `hmg/fire_distant.ogg` | SBW `gun/m_2_hb/m_2_hb_far.ogg` | unchanged |
| `coax/fire.ogg` | JEG `item/light_machine_gun/fire.ogg` | unchanged |
| `coax/fire_interior.ogg` | SBW `vehicle/common/coax_fire_1p.ogg` | unchanged |
| `coax/fire_distant.ogg` | JEG `item/light_machine_gun/fire.ogg` | low-passed (900 Hz), softened attack, ground-echo tail |
