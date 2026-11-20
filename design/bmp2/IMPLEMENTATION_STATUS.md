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

## Since (2026-11-17)

- Heard the aircraft way (`GroundVehicleAudio`, far tracks; `GroundVehicleSoundClientGameTest`), and every gun on the
  long-range path with reports (`VehicleGunReachClientGameTest`). Real start, stop and cab recordings are still to find.

## Finalisation (2026-11-17, design/READINESS.md)

- **Gunner's seat** (seat 8 on the HUD, index 7, in the turret): takes the 30 mm, coax and ATGM from the driver
  (`"fallback_seat": 0`); alone, the driver keeps them. Only a player or enemy crew mans it: a mob carried there leaves
  the gun with the driver. The enemy AI still crews seat 0 and fires through the fallback (`EnemyVehicleRaid` passes).
  The seat panel in the hold names it "Gunner".
- **Passengers at the firing ports:** each squad seat's eye sits at its port (1.45 up, just outside the hull side), and
  the view turns only outward (left ports -140° to -40°, right ports 40° to 140°, the rear door all round), so they
  look out, and may fire their own weapons, through it. Before, the eye sat inside the side armour.
- **Rear doors** swing open on their outer hinges while someone boards or leaves (`VehicleDoors`,
  `swingAboutHinge`; SBW's pivots sit mid-door). Played: shut, mid-swing and open from directly behind.
- **Passengers inside are not drawn** for anyone (`EntityRendererHiddenPassengerMixin`): a mob passenger was drawn
  through the roof, and other players would have been.
- **Swims** (`"amphibious"`): floats with its waterline 1.25 blocks up, makes way at 7 km/h steered by the tracks, a
  bow wave and wake, no ATGM afloat (the 30 mm still fires), and hauls itself up a shelving bank.
- **Dual-feed 30 mm:** slot 1 is AP (as before: pierces 9.8, a small blast), slot 4 the new HE belt
  (`vehicle_30mm_cannon_he`: pierces 3.2, bursts 16 damage over 4.5 blocks, orange tracer), both from the hold's
  autocannon shells.
- **Muzzle smoke:** the autocannons (20 and 30 mm) now throw a short flash and a puff that clears between rounds; the
  big gun's 38-tick cloud had filled the gunner's sight.
- **Sound:** a real diesel start and shutdown (CC0, roboroo), the loops winding up when it catches; muffled versions
  in the cab. The cab loop itself is still a filtered exterior take (no CC0 interior recording exists).
- **Destroyed:** cooks off, goes up and burns as a charred hulk (`GroundWreck`), no longer vanishing.
- **Road speed** 59 km/h played flat out (brief 65; `max_forward_speed` 1.321), was 33.
- **Weight (`"mass"` 14 t):** winds up and stops more slowly than the LAV-150, faster than the tanks; squats pulling
  away, dives braking (seen played at 6 m/s), rolls out of turns; the crew feels the rumble and rock (not through the
  gunner's sight). Every movement sounds: the turret's electric drive and the gun's elevation while they move, the
  heavy brakes, a knock landing from a drop, a splash driving into water (`VehicleFeel`).
- **Passenger port cameras** moved outside the hull (their seats stay inside): played, seat 2 looks out of the left
  side at the LAV-150 parked beside it.

| Test (`-PwithPack`) | Result |
| --- | --- |
| `Bmp2SwimClientGameTest` (new) | passes: afloat 139 ticks at 6.7 km/h, waterline within 0.15 of the surface, ATGM refused afloat, 30 mm allowed, out on the far bank |
| `GroundVehicleSoundClientGameTest` | passes: start on boarding, loops silent while cranking, stop on leaving, no second start when a running vehicle returns into range |
| `GroundWreckClientGameTest` (new) | passes: cooks off, burns, throws out its cargo, stays upright in place, breaks apart |
| `EnemyVehicleRaidClientGameTest` | passes with the gunner's seat: closes to 58, lays on the player, fires |

## Not covered yet

- **Listen:** `engine_start`, `engine_stop` (and `_cab`), the 30 mm HE, `movement/traverse`, `movement/elevation`,
  `movement/brake_heavy`, `movement/bump`, `movement/splash`.
- The tracks seen moving on the BMP-2 itself (the shared driver is pinned by `Plz05ClientGameTest`).
- The dedicated server, multiplayer (a second player's view of the doors, hidden passengers and the bow wave).

## Texture pass (2026-11-19, the user's review R7)

`design/ground-vehicles/detail_texture.py` re-weathers the texture from the model: it is enlarged (the original paint
kept texel for texel) and each texel is shaded by where it sits on the hull: panel seams and worn edges, bolt heads
round big panels, mottled paint, grime run down from top edges, dust on top faces, mud low down. The untouched texture
is `texture_base.png` here (re-run the script to tune; it never stacks), the before/after `review-texture.png`. Seen in
the pack (Photon) by day from close; not yet looked at under every paint colour.
