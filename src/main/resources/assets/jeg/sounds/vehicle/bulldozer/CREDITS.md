# BD-40 sound assets

Prepared 2026-11-14 from public HQ previews of real recordings on Freesound. Every source page identifies its
recording as CC0 1.0 (public domain); author links are kept as a courtesy. These are adapted sound-design assets.

| Asset | Recording | Author / source | Adaptation |
| --- | --- | --- | --- |
| engine_idle, engine_stop | Construction_Bulldozer_Catarpillar_Engine_Planierer.wav (23.7–35.1 s) | [Rincewind87, 165200](https://freesound.org/people/Rincewind87/sounds/165200/) | Close-miked Caterpillar crawler at idle; seamless loop. The shutdown is the same idle slowed continuously to a stop |
| engine_load | the same take (7.2–19.6 s) | as above | The same engine working hard; loop. Cut from one take so the idle/load crossfade stays one voice |
| engine_distant | Construction_Bulldozer_Diesel.wav | [Rincewind87, 165199](https://freesound.org/people/Rincewind87/sounds/165199/) | A working bulldozer, low-passed to the rumble that carries |
| blade | Bulldozer Shaking Rocks .wav | [johnnydekk, 615309](https://freesound.org/people/johnnydekk/sounds/615309/) | Soil and rock tumbling off a dozer; loop |
| blade_impact_1, blade_impact_2 | Bulldozer Encendida | [Mariayolanda511, 850572](https://freesound.org/people/Mariayolanda511/sounds/850572/) | Two blade clanks cut from a bulldozer working a site |
| tracks | tracks moving + construction equipment tracked.wav | [christerljung, 849062](https://freesound.org/people/christerljung/sounds/849062/), [cognito perceptu, 88422](https://freesound.org/people/cognito%20perceptu/sounds/88422/) | Track clank layered over tracked-machine rattle (warning tones notched out); loop |
| tree_fall | Big Tree Fall in Forest | [Kinoton, 494071](https://freesound.org/people/Kinoton/sounds/494071/) | A conifer creaking, cracking and crashing down |
| hydraulic, hydraulic_stop | hydraulics.wav | [nuckan, 212941](https://freesound.org/people/nuckan/sounds/212941/) | Pump loop and valve release |
| horn, horn_distant | Industrial Air Horn (0.6–1.9 s) | [mcpable, 131930](https://freesound.org/people/mcpable/sounds/131930/) | An oil rig's air horn: its steady tone lowered to about 280 Hz, with a second horn a minor third above and a quiet sub-octave, as a heavy machine's dual horn; looped. A muffled distant version |
| engine_start | engine_start.wav | [RichieMcMullen, 386800](https://freesound.org/people/RichieMcMullen/sounds/386800/) | Diesel crank and catch |

License: [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/).

`reverse.ogg` is an original synthesized back-up alarm. Everything else is a field recording.

Processing is reproducible with `design/bulldozer/prepare_audio.py` (mono, 44.1 kHz Ogg Vorbis). Source downloads
are cached in `build/bulldozer-audio-sources/`. Segment offsets, peaks, levels, loop-seam measurements and hashes are
in `design/bulldozer/audio-validation.json`.
