# YX-100 sounds

By the Superb Warfare development team, used with their permission
(`design/superbwarfare-port/PERMISSION.md`). Copied unchanged by `design/yx100/refit.py`:

| File | SBW source |
| --- | --- |
| `fire.ogg` | `vehicle/yx_100/yx_100_fire_3p` |
| `fire_interior.ogg` | `vehicle/yx_100/yx_100_fire_1p` |
| `fire_distant.ogg` | `vehicle/yx_100/yx_100_far` |
| `fire_very_far.ogg` | `vehicle/yx_100/yx_100_veryfar` |
| `load.ogg` | `vehicle/yx_100/yx_100_reload` |

- `engine.ogg` and the loops derived from it (`engine_load`, `engine_cab`, `engine_distant`), and `engine_idle`, `engine_cab_idle`, since 2026-11-19: the M1A1 Abrams' gas turbine (see `engine_start`): its steady whine after the spool-up (12-22 s) idles as recorded and works raised to 1.12 with its exhaust roar lifted, by design/ground-vehicles/prepare_audio.py and prepare_idles.py. `engine_stop` is the spool-up played backwards
- `engine_start, engine_start_cab`: "Start-up Sound - U.S. Army M1A1 Tank - Gas Turbine Engine.mp3" by adr1911, https://freesound.org/people/adr1911/sounds/542582/ (CC0 1.0), cut and filtered by design/ground-vehicles/prepare_starts.py
- `engine_stop, engine_stop_cab`: "Start-up Sound - U.S. Army M1A1 Tank - Gas Turbine Engine.mp3" by adr1911, https://freesound.org/people/adr1911/sounds/542582/ (CC0 1.0), cut and filtered by design/ground-vehicles/prepare_starts.py
