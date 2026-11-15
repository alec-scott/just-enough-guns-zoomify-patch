# FSV-6 sound assets

Prepared 2026-11-15 from public HQ previews of real recordings on Freesound. `design/support/prepare_audio.py` checks
that every source page carries the CC0 1.0 (public domain) licence before using it; author links are kept as a
courtesy. These are adapted sound-design assets.

| Asset | Recording | Author / source | Adaptation |
| --- | --- | --- | --- |
| engine_start | Starting Truck Engine.mp3 | [Legnalegna55, 530052](https://freesound.org/people/Legnalegna55/sounds/530052/) | A real truck's starter cranking and the engine catching (the take's first 3 s) |
| engine_load | the same take (8.6-15.6 s and 18.0-24.2 s) | as above | The same truck running hard, two stretches joined into a 12.6 s loop |
| engine_idle, engine_stop | Diesel idle 1.wav | [C-V, 565598](https://freesound.org/people/C-V/sounds/565598/) | A dually diesel truck idling (the same tone as the truck above), a 20 s loop so it does not repeat; the stop is the idle slowed to a halt |
| engine_cab | the idle above | — | The diesel heard through a closed cab: low-passed, with a little panel resonance |
| engine_distant | the working loop above | — | The rumble that carries, with a faint ground echo |
| generator, generator_distant | Diesel Power Generator.flac | [qubodup, 189896](https://freesound.org/people/qubodup/sounds/189896/) | The shelter's generator; loop, and a low-passed distant version |
| slide_motor | Electric motor engine start stop | [kentspublicdomain, 476943](https://freesound.org/people/kentspublicdomain/sounds/476943/) | The slide-outs' drive motor; loop |
| horn, horn_distant | Truck Horn | [danlucaz, 513741](https://freesound.org/people/danlucaz/sounds/513741/) | A real truck horn's steady tone, looped while held; and a band-limited distant version |
| air_brake | synthesized | — | The parking brake's valve click and air release as it starts to deploy |
| ramp_clank | Large Metal Lift Door Opening.wav | [lolamadeus, 161228](https://freesound.org/people/lolamadeus/sounds/161228/) | The ramp landing and closing |

In game the running engine loops drift slowly in pitch and level, so they never sound like the same loop twice.

No asset carries a human voice. The earlier truck-interior, bus air-brake and distant-truck recordings had people
talking in them and are no longer used.

The jacks use the BD-40's hydraulic pump and valve recordings and the reversing alarm its synthesized beeper (see
`../bulldozer/CREDITS.md`).

License: [CC0 1.0 Universal](https://creativecommons.org/publicdomain/zero/1.0/).

Levels, loop seams and source offsets are in `design/support/audio-validation.json`.
