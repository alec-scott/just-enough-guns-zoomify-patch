# 4x4 pickup and technicals: status

Pass 1, 2026-11-26 (released in yftm.26); pass 2 the same day, below. Brief: [BRIEF.md](BRIEF.md); handover: [README.md](README.md).

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

## Pass 2: production fixes (2026-11-26, shipped after yftm.26)

| Fault found | Fix | Where |
| --- | --- | --- |
| The rocket technical's chase camera turned grey through a ripple: the backblast drifted back over the camera | A rocket's backblast puffs thin out within 4 blocks of their edge; onlookers still see the full cloud. What is left in that view is the rockets' own trail ahead, gone in about 4 s | `MissileBlastRenderer` `nearFade` |
| A villager in the cab sat 0.6 up by its feet, its head through the roof | A mob in an enclosed seat that is drawn sits no higher than a player (the rule applies to every such seat, the Mi-28's and CH-47's cockpits too; played only in the pickup); a villager's legs and robe are left out there on a vanilla client | `VehicleEntity.seatsMobInCab`, `VillagerModelSeatedMixin` |
| Mobs in the bed floated 0.6 above it (the lift that stands a cow on the AH-6's bench) | `"floor_seats"`: the pickup's four bed seats and the technicals' gunners; a mob rides them as a player does | `pickup*.json`, `VehicleData.floorSeats` |
| The cab doors swung open for a gunner climbing into the bed | The doors and their sounds count only the cab's riders | `VehicleEntity.cabOccupants`, `VehicleDoors`, `GroundVehicleAudio.cabDoors` |
| No handover notes | [README.md](README.md): seats, keys, behaviour, regenerate commands | |
| The ArmourTest sandbox had no pickups | Rebuilt: the four in a row behind the armour (z -14.5), their holds loaded (2, 6 and 2 stacks of ammunition) | `ArmourSandboxSetupClientGameTest` |

**Played in the pack client (mcpfabric, Photon):** the 12.7 mm gunner's three views (first person through the M2's
grips with a small flash; the chase camera, where the gun points away and the gunner's body hides it; the hull's action
camera beside the gun); a 9-rocket ripple from the cab driver's chase camera before and after the backblast fix; a
villager, zombies and a gunner aboard the 12.7 mm and the plain pickup (doors shut when the bed gunner boards; the bed
riders seated on the bed's sides); the plain pickup at 40/150 (grey smoke column), destroyed (blast and dust), and its
burning charred wreck.

| Test (`-PwithPack`) | Result |
| --- | --- |
| `PickupCabClientGameTest` (new) | passes: the villager in seat 1, head top 1.70 above the hull under the 1.80 roof (it was 2.30), eyes 1.37 above the 1.10 sill, the client seating it at the same 1.70; a zombie gunner in seat 2 leaves the cab count at 1, feet 0.62 on the bed floor |
| `PickupClientGameTest` | passes: all four turn on 8.6 blocks; the pod stops at 10 degrees, a 12-rocket ripple leaves 150/150; the TOW reaches 212 blocks, 150/150 |
| `VehicleMobPassengerClientGameTest` (shared seat code) | passes: the AH-6's cow still sits 0.60 above the pilot's feet |
| `ArmourSandboxSetupClientGameTest` | rebuilt `build/run/packClient/saves/ArmourTest` with the pickups |

## Not covered yet

- **Listen:** the engine start, idle, working loop, cab loop and stop, and the door latch and slam (all new).
- **Under Fresh Animations** a villager in the cab sits in its pose, shins down: they show below the door sill. The head
  stays under the roof; lifting it to hide the shins would put the head through the roof again (a cab lower than a
  seated Minecraft body, guide section 6). The vanilla-client leg hiding is not checked in game (the pack's EMF draws
  villagers itself).
- The chase camera puts the 12.7 mm behind its own gunner's back (the gun follows the view away from the camera).
- Not the dedicated server, not multiplayer.
