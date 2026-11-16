# BMP-2 implementation status (2026-11-17), refit pass 1

Brief: [BRIEF.md](BRIEF.md).

## Built

- **Model:** SBW's `bmp_2` (the model the mod already shipped, byte for byte) refit at scale 0.73 by
  [refit.py](refit.py): every bone named, 46 coplanar pairs fixed, both LODs renamed. The antennas are left out of the
  measured bounds and hitbox. 7.0 × 3.43 × 2.66 blocks (real: 6.7 × 3.15 × 2.45 m).
- **Tracks:** the path from the running gear (within 1.47 px of SBW's hand-typed path) in the data's `"tracks"`; the
  shared driver moves the links and turns the sprockets, road wheels and idlers. The track helpers moved from the
  PLZ-05's refit into `refit_lib.py` (the PLZ-05's outputs regenerate unchanged).
- **Hitbox:** hull, each track run, the engine at the front right (SBW's engine box, scaled: no bone is the engine),
  the turret and the gun outside it turning with the turret.
- **Data:** seats, turret origin and barrel, muzzles (the ATGM now leaves from its launcher on the roof, ahead of the
  turret), cameras rescaled; passengers 3 and 4 have their own cameras; `up_step` 1.25; third-person camera at 11
  blocks; dismount at the rear doors (0.1 up: the hull sits 0.03 into the ground, so a spot at its own height is
  refused); entity box 3.4 × 2.4.
- **Muzzle flashes** show only while their gun fires (they were drawn all the time).
- **Markings painted out** (user, 2026-11-17): SBW's "Z" (bow and stern) and St. George ribbon with star (both sides)
  were decal cubes on transparent ground; the refit drops them (`drop_markings`), and the hull paint shows. The other
  vehicles' textures were checked: no such markings (the Mi-28 carries only a tail number).
- **Kept for the enemy AI:** the id, the turret seat (0) and its pitch limits.

## Played (mcpfabric, pack client with Photon)

- Beside a PLZ-05 by day: right size against it, opaque and lit, no flash drawn at rest.
- Boarded, loaded 30 mm through the hold, reloaded, fired in first and third person: one round every 10 ticks, the
  burst on the target.
- Dismounted: the player stands 4 blocks behind the hull, at the rear doors. Found and fixed on the way: the first
  try put the player on the roof (the dismount point sat in the ground).

## Tested (`-PwithPack`)

| Test | Result |
| --- | --- |
| `EnemyVehicleRaidClientGameTest` (new, fleet plan 0.9) | passes: LAV-150 and BMP-2 spawned crewed 70 blocks off, facing 90° away; both close in (to 58–60), lay the turret on the player (0° off) within their pitch limits, and fire |
| `TowClientGameTest` (shoots a BMP-2) | passes: one TOW destroys the refit BMP-2 (320 hull) at 100 blocks |

Found while writing the raid test:
- Past their target retention (LAV-150 88 blocks, BMP-2 92) the AI drops a remembered player and patrols, so a raid
  spawned further off doesn't find its target. The test spawns at 70.
- **Open:** the AI sometimes halts near 60 blocks, short of its engagement range (42 for the LAV-150, 52 for the
  BMP-2): one run sat at 60.x for 45 s. The test asks only for closing under 63; the cause isn't found yet.

## Not covered yet

- The passengers' outward view (SBW's firing ports face sideways; jegn seats have no orientation), the gunner's seat
  taking the gun from the driver, swimming, dual-feed 30 mm, long-range rounds for the 30 mm and coax, the rear doors
  and hatches opening, ground-vehicle audio, lights, damage states.
- The tracks seen moving on the BMP-2 itself (the shared driver is pinned by `Plz05ClientGameTest`).
- Night view, the dedicated server, multiplayer.
