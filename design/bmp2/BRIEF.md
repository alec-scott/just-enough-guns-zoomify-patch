# BMP-2 brief

Status: **refit pass 1 being built, 2026-11-17** ([refit.py](refit.py), [rig.json](rig.json)). From
[the fleet plan](../FLEET_PLAN.md), section 2; decisions there (2026-11-15) apply: built 1:1; the driver keeps the
main gun when alone and a passenger in the gunner's seat takes it over.

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Peers:** the [PLZ-05](../plz05/BRIEF.md) (the same track driver and refit tools), the LAV-150 (the other enemy
  raid vehicle)
- **Source:** Superb Warfare's `bmp_2` (already shipped unchanged since the port; now refit), used with the SBW
  team's permission.

## Purpose

- **What the player uses it for:** carry a squad into a fight and kill armour. It is the only player vehicle with a
  long-range anti-tank missile, and the enemy brings LAV-150s and BMP-2s from day 80.
- **Success:** drive a squad of six across rough ground and a river, destroy an enemy LAV-150 at 1 km with the ATGM
  and a BMP-2 at 400 m with the 30 mm, and dismount the squad behind cover through the rear doors.

## Real-world reference

- **Class:** BMP-2 tracked infantry fighting vehicle. 6.7 × 3.15 × 2.45 m, 65 km/h on road, amphibious at about
  7 km/h. 30 mm 2A42 with dual feed (AP and HE), coaxial PKT, 9M113 Konkurs ATGM. Crew of three plus seven dismounts.
- **Refit:** SBW's model is about 1.37× real: **scale 0.73** gives 7.0 × 3.43 × 2.66 blocks (the fenders and the
  turret's sights account for the extra width and height).
- **Moving parts:** `turret`, `barrel`, the track links of each side, `sprocket_*` (front), `road_wheel_*`, `idler_*`
  (rear), `rear_door_left`/`right`, the hatches, the ATGM launcher on the turret roof.

## Pass 1 (this pass)

- The refit at 1:1: names for every bone (links `track_link_<side>_<i>`), the track path for the shared driver, the
  LODs renamed, the hitbox written from the model (hull, tracks, the engine at the front right, the turret and the gun
  turning with it).
- Data rescaled: seats, turret origin and barrel, muzzles (the ATGM now leaves from its launcher on the roof),
  cameras; passengers 3 and 4 get their own cameras (they shared 1's and 2's); `up_step` 1.25; third-person camera
  at 11 blocks; dismounting at the rear doors; entity box 3.4 × 2.4.
- The muzzle flashes show only while their gun fires (the generic idle left them drawn all the time).
- The enemy AI keeps its numbers: the turret seat, its pitch limits (−74° to 7.5° view pitch) and the id.
- `EnemyVehicleRaidClientGameTest` (fleet plan 0.9): an enemy LAV-150 and BMP-2 close in, lay on and fire at a player.

## Later passes

- Swimming; dual-feed 30 mm (AP and HE); long-range rounds for the 30 mm and coax (fleet plan 0.3); the gunner's seat
  taking the gun from the driver; hatches and rear doors opening; ground-vehicle audio (0.1); lights (0.8); damage
  states (0.7).
