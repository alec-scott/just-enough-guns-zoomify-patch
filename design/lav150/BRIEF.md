# LAV-150 brief

Status: **refit pass 1 being built, 2026-11-17** ([refit.py](refit.py), [rig.json](rig.json)). From
[the fleet plan](../FLEET_PLAN.md), section 3; decisions there (2026-11-15) apply: built 1:1; the driver keeps the
main gun when alone.

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Peers:** the [BMP-2](../bmp2/BRIEF.md) (the other enemy raid vehicle, refit the same way), the FSV-6 (it escorts
  it; the FSV-6 tows a wrecked LAV-150 home)
- **Source:** Superb Warfare's `lav_150` (shipped unchanged since the port; now refit), used with the SBW team's
  permission.

## Purpose

- **What the player uses it for:** get somewhere fast and hold it: escort the truck, answer a raid before the BMP-2
  can, suppress infantry and hordes. Wheels and speed set it apart from the BMP-2; it is not an armour killer.
- **Success:** reach a raid 2 km away on roads at about 90 km/h, clear a horde with the 20 mm and coax, drop off
  three dismounts, and outrun an enemy BMP-2.

## Real-world reference

- **Class:** Cadillac Gage V-150 Commando 4×4 armoured car. 5.69 × 2.26 × 2.5 m, 88 km/h, amphibious, rifle-proof
  armour. 20 mm cannon and coaxial MG in a one-man turret, engine at the rear, roof hatches.
- **Refit:** SBW's hull is 6.73 blocks long: **scale 0.85** gives 5.7 blocks. SBW built it wide as well, so at true
  length it stays 2.57 blocks wide (0.3 over); its shapes are kept rather than squeezed.
- **Moving parts:** `turret`, `barrel`, the four `wheel_*` (the front pair steer), `turret_hatch_left`/`right`.

## Pass 1 (this pass)

- The refit at 1:1: every bone named, z-fighting resolved (35 pairs), LODs renamed, the hitbox written from the model
  (hull, each side's wheels, the engine at the rear, the turret and the gun turning with it).
- Wheels: the shared wheel driver (new; guide section 5) rolls all four and steers the front pair from the data's
  `"wheels"` block. SBW's canned idle clip is replaced by an empty one.
- Data rescaled: driver seat and sight, turret origin and barrel, muzzles, cameras; the four passengers get real
  places in the hull (they all sat at one point); `up_step` 1.25; dismount at the side door; entity box 2.6 × 2.6.
- The muzzle flashes show only while their gun fires, as on the BMP-2.
- Lights (fleet plan 0.8): the glacis lamps with their beam at night, tail lamps that brighten braking.
- The enemy AI keeps its numbers: the id, the turret seat (0) and its pitch limits (−15° to 32.5°).

## Later passes

- Top speed to 88 km/h with braking and steering at speed (`Lav150Speed`); a roof hatch seat for a passenger's rifle
  (`Lav150Hatch`); armour that stops rifle rounds and not 20 mm (`Lav150Hitbox`); run-flat tyres; swimming;
  suspension; a real V8 diesel, tyre roar and interior recordings.
