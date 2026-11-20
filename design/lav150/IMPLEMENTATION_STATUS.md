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

## Finalisation (2026-11-17, design/READINESS.md)

- **Road speed:** 88.0 km/h measured flat out, the brief's (`max_forward_speed` 2.181: top speed is where friction
  levels off, set from a measured run), and it brakes to a stop in about 1 s.
- **Weight (`"mass"` 9.9 t):** the lightest armour, so the quickest to wind up (80% of top in 28 ticks) and to stop;
  squats about 1° pulling away and dives 2° braking, rolls out of turns; the crew feels the rumble and the rock.
- **The rifleman's hatch** (seat 6 on the HUD, index 5, `"turret_riders"`): stands waist-out of the turret's left
  hatch, rides the turret round, uses his own weapon, drawn for everyone; the hatch lid swings open on its outer hinge
  while he's in it.
- **Swims** at about 5 km/h (`"amphibious"`, waterline 1.45 up), the 20 mm free to fire afloat, and climbs out up a
  shelving bank.
- **Armour** rating 5.1 (hull and engine): stops rifle rounds (the best, the combat rifle, pierces 4.96) and not the
  20 mm (5.2); the tyres (4.8) can be shot out by rifles. **Run-flat:** a shot-out side slows it to 0.75 (both 0.56),
  never stopping it.
- **Sound:** a real truck diesel start and shutdown (CC0, Ford LTL 9000), the loops winding up as it catches; tyres on
  gravel by speed; the air brakes when braking; the turret's electric drive and the gun's elevation; a knock landing
  from a drop; a splash into water.
- **Destroyed:** cooks off and burns as a charred hulk.

| Test (`-PwithPack`) | Result |
| --- | --- |
| `Lav150SpeedSwimClientGameTest` (new) | passes: 88 km/h, stopped 21 ticks after braking; hatch rider in seat 5, 2.3 up; swims 4.8 km/h and climbs out |
| `VehicleWeightClientGameTest` (new) | passes: 80% of top in 28 ticks, stopped in 21, squat -0.98°, dive 1.96°, brakes and turret drive heard, knock landing |
| `Lav150WheelsClientGameTest` | passes |
| `EnemyVehicleRaidClientGameTest` | passes |

## Not covered yet

- **Listen:** `engine_start`, `engine_stop` (and `_cab`), `tyres/gravel`, `movement/brake`, `movement/traverse`,
  `movement/elevation`, `movement/bump`, `movement/splash`.
- The hatch lid's swing direction is coded from the model's geometry but not yet seen in a close screenshot.
- The enemy AI still halts near 60 blocks at times (see the BMP-2's status).
- The dedicated server, multiplayer.

## Texture pass (2026-11-19, the user's review R7)

`design/ground-vehicles/detail_texture.py` re-weathers the texture from the model: it is enlarged (the original paint
kept texel for texel) and each texel is shaded by where it sits on the hull: panel seams and worn edges, bolt heads
round big panels, mottled paint, grime run down from top edges, dust on top faces, mud low down. The untouched texture
is `texture_base.png` here (re-run the script to tune; it never stacks), the before/after `review-texture.png`. Seen in
the pack (Photon) by day from close; not yet looked at under every paint colour.
