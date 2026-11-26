# 4x4 pickup and technicals: status

Pass 1, 2026-11-26. Brief: [BRIEF.md](BRIEF.md). Worktree branch `feature/sbw-candidates`, not released.

## Built

| Area | What | Where |
| --- | --- | --- |
| Model | SBW's four pickups refit at 0.76 (a Land Cruiser 70-class single cab, 5.2 blocks long); every bone named; 40–80 z-fighting pairs resolved per model; rebaked per face and weathered without rivets (a civilian body); two LODs; the TOW raised onto a pedestal so it clears the cab | `refit.py`, `rig.json`, `review-*.png` |
| Scope | Memes out: the dashboard figurine, the door and tailgate names, the 114514 plates (now "4071 KD"), the badge (a plain oval) | `refit.py strip`, `repaint_decals` |
| Data | Four entities: `pickup` (4 bed seats), `pickup_hmg` (standing 12.7 mm gunner), `pickup_rocket` (driver lays the 107 mm pod from the cab), `pickup_tow` (standing TOW gunner); hitbox from the model; wheels roll and steer | `data/jeg/vehicles/pickup*.json` |
| Weapons | The speedboat's M2; the Type-63's rockets through the new shared `RocketPodEntity` (the Type-63 moved onto it, its test still passes); the TOW's missile and wire (`PickupTowEntity`) | |
| Handling | About 95 km/h flat out; 8.6-block circle from a crawl | `max_forward_speed` 2.2, acceleration 0.026, steering 0.42 |
| Lights | Headlamps with their beam, tail and brake lamps, reversing lamps | `AircraftLights.PICKUP` |
| Doors | The cab doors swing out on their front hinges while someone boards or leaves, with a pickup door's latch and slam | renderer, `GroundVehicleAudio.cabDoors` |
| Sound | Real recordings (CC0): an old diesel's start, idle and run-down, a 4WD at revs, a real 4x4 cab interior, the door; shared by all four | `prepare_audio.py`, `sounds/vehicle/pickup/CREDITS.md` |
| HUD | Name, hull, energy, speed; the weapon list now shows a land vehicle's single gun; the rocket pod's fire-control panel (elevation, bearing, range) | |
| Pack | Carry On blacklist; recipes at the Vehicle Assembling Table; English and Chinese names | |
| Sandbox | A row behind the armour in `ArmourSandboxSetupClientGameTest` (not rebuilt yet) | |

## Tested

- **Played in the pack client (mcpfabric, Photon, day and night):** drove the 12.7 mm technical to 100 km/h; cab first
  person (dash, windscreen, mirror) and the driver seated under the roof in third person; the gunner's view through
  the M2's grips, a burst from the hold; the rocket technical loaded 12 HE, laid from the cab (30 degrees: 1,727 m), a
  ripple at 10 degrees over the cab; the TOW gunner's sight over the cab, two missiles, the second wrecked a BMP-2 at
  80 blocks; headlamp beam and tail lamps at night; the door swinging out as a villager boarded.
- **`PickupClientGameTest`** with `-PwithPack`: each of the four turns on 8.6 blocks from a crawl (limit 9); the pod stops
  at 10 degrees and a 12-rocket ripple over the cab leaves the hull at 150/150; a level TOW shot ahead clears the cab
  and flies 212 blocks. **`Type63ClientGameTest`** passes after the pod moved to `RocketPodEntity`.
- Not the dedicated server, not multiplayer.

## Not covered yet

- **Listen:** the engine start, idle, working loop, cab loop and stop, and the door latch and slam (all new).
- The chase camera sits in the rocket pod's backblast during a ripple (the smoke fills that view; the cab view stays
  clear).
- A mob in a cab seat (a villager) sits higher than a player and its head shows through the roof.
- The 12.7 mm's flash size against the guide's "small in the gunner's view"; the bed riders' poses on the plain pickup;
  the hull action camera for a standing gunner; damage smoke and the wreck; the player's full checklist in every view.
- The ArmourTest sandbox rebuild with the pickups.
