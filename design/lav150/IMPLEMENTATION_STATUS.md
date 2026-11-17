# LAV-150 implementation status (2026-11-17), refit pass 1

Brief: [BRIEF.md](BRIEF.md).

## Built

- **Model:** SBW's `lav_150` (the model the mod already shipped) refit at scale 0.85 by [refit.py](refit.py): every
  bone named, all 35 coplanar pairs fixed (`refit_lib.resolve_zfighting` now accounts for a cube's inflate, which this
  model needed; the PLZ-05's and BMP-2's outputs regenerate unchanged), both LODs renamed. 5.7 × 2.57 × 3.43 blocks
  with the antennas (real: 5.69 × 2.26 × 2.5 m; SBW built it wide).
- **Wheels:** the new shared wheel driver (guide section 5) from the data's `"wheels"` block, measured by the refit
  (radius 10.2 px, wheelbase 2.68 blocks). All four roll; the front pair steer by the hull's turn rate for its speed.
  The wheel bones pivot on their axles (within 0.07 px), so they roll without wobbling. SBW's canned idle clip is
  replaced by an empty one.
- **Hitbox:** hull, each side's wheels, the engine at the rear, the turret and the gun outside it turning with it.
- **Data:** driver seat and sight, turret origin and barrel, both muzzles, cameras rescaled; the four passengers sit
  in the hull's two benches (they all sat at one point); `up_step` 1.25; third-person camera at 10 blocks; dismount
  on the left side; entity box 2.6 × 2.6.
- **Muzzle flashes** show only while their gun fires, as on the BMP-2.
- **Lights** (fleet plan 0.8): the caged lamp boxes on the glacis with the beam at night, tail lamps at the rear corners
  that brighten braking; on while the engine runs.
- **Kept for the enemy AI:** the id, the turret seat (0) and its pitch limits.

## Played (mcpfabric, pack client with Photon, ArmourTest world)

- Boarded from the side; drove and turned in third person; from ahead in a right turn, the left front wheel shows its
  face toward the front, as a wheel steered right does.
- Fired the 20 mm (8 rounds, the flash on the barrel, hits at the crosshair, no flash after) and the coax (tracers,
  its own smaller flash).
- Dismounted on a crater's rim: the player stood free beside the hull, in the crater.
- At midnight: both glacis lamps lit with the beam on the ground ahead; the red tail lamps at the rear corners when
  braking.

## Tested (`-PwithPack`)

| Test | Result |
| --- | --- |
| `Lav150WheelsClientGameTest` (new) | passes: 160.0 px rolled over 10 blocks (160 expected); turning right 25.6°, backing through the same turn −25.1°, straight again 0.2° |
| `ArmourNightLightsClientGameTest` (LAV-150 added) | passes: lit with its beam at night, beam off by day; the earlier lens-glare fix shows no glare |
| `EnemyVehicleRaidClientGameTest` | passes: the LAV-150 on the new model closes from 70 to 63, lays on the player (0° off) and fires 12 rounds |

## Not covered yet

- Top speed and braking at 88 km/h (`Lav150Speed`), the roof hatch seat (`Lav150Hatch`), armour that stops rifle
  rounds and not 20 mm (`Lav150Hitbox`), run-flat tyres, swimming, suspension, real engine, tyre and interior audio.
- The enemy AI still halts near 60 blocks at times (see the BMP-2's status); this run's LAV-150 stopped at 63.
- Night view from outside at a distance, the dedicated server, multiplayer.
