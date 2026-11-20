# 105 mm howitzer brief

Status: **built, played and tested, not yet released, 2026-11-16** ([IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)). Replaces the waveforce tower ([fleet plan](../FLEET_PLAN.md), section 7). Model:
[generate.py](generate.py) (our own generated model), [rig.json](rig.json).

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Peers:** the [Type-63](../type63/BRIEF.md) (towed, laid by a crouched gunner, A/D shifts the carriage), the
  [mortar](../mortar/BRIEF.md) and [Mk42](../mk42/BRIEF.md) (the shell path and the range panel), the
  [FSV-6](../support/PLAN.md) (tows it)

## Decisions (user, 2026-11-16)

- **New id `jeg:howitzer`; the waveforce tower is retired.** It stays registered for one release; a saved tower turns
  into a dropped chest of its hold, then the type is removed.
- **Fire mission: observer and manual lay.** A player looking through a spyglass marks a target for the howitzers
  around; coordinates can also be typed. The gunner's panel gives the bearing and elevation to lay; the gunner lays
  by hand.
- **Range about 4 km, with charges:** three propellant charges give short, medium and long range, each with a low and
  a high arc.
- **Towed by the trail, and deployed to fire:** a tow eye on the trail end hitches to the FSV-6's pintle (the same
  hook lets the Type-63 be towed trail first). It fires only deployed (trails open, spades down), and is towed only
  packed.

## Purpose

- **What the player uses it for:** indirect fire. It breaks up a horde or a raid before it reaches the base, and hits
  targets the A-10 can't be spared for. Create Big Cannons covers block-built direct fire; this is crewed indirect
  fire at long range.
- **Success:** the FSV-6 tows it to a firing point; the crew deploys it; an observer with a spyglass marks a target;
  the gunner lays on the panel's numbers and the third round lands on the target at 2 km.

## Real-world reference

- **Class:** M101A1 105 mm light towed howitzer (the fleet plan named the M119; the M101 is chosen because it has
  split trails and travels barrel first on its own wheels, which is what the plan's model list describes; the M119
  has a box trail and travels with its barrel swung over the trail).
- **Proportions (1:1):** 5.94 m long in travel, 2.21 m wide, 1.73 m high; 2.36 m tube (L/22); tyres 9.00-20, about
  1.0 m across.
- **Laying:** elevation −5° to +66°, traverse 23° either side on the carriage; beyond that the crew shifts the
  trails. Muzzle velocity 472 m/s; 11.3 km range (gameplay: about 4 km, see Behaviour).
- **Rate:** 10 rounds a minute at most, 3 sustained.
- **Moving parts:** `turret` (top carriage, traverse), `barrel` (cradle and tube, elevation), `tube` (recoils about
  1 m along the cradle and runs out), `trail_left`/`trail_right` (open about 26° each way when deployed),
  `spade_left`/`spade_right` (fold down into the ground), `wheel_left`/`wheel_right` (roll when towed), `breech`
  (cosmetic block), `sight` (the panoramic telescope, on the left), `shield`.

## Controls (the vehicle-gun family, as the Type-63)

| Key | Action |
| --- | --- |
| Right-click it | crouch at the sight as gunner |
| N (tow key), seated | deploy (open the trails, drop the spades, 5 s) or pack up for towing |
| Mouse | lay the gun at the handwheels' pace (0.5° a tick), 23° either side of the carriage |
| A / D, deployed | shift the trails (0.5° a tick) to lay further off |
| X / Z | next / previous: HE or smoke |
| V | next propellant charge (1, 2, 3) |
| Left button | fire (only deployed and loaded) |
| R | load a round from the crate (the gun also loads itself while crewed) |
| Inventory key, seated | open the crate |
| Spyglass, attack while looking through it | mark the target (observer, any player) |
| `/justEnoughGuns firemission <x> <z>` | set the target by coordinates for the gun you crew |

## Behaviour

- **Charges** (`HowitzerEntity.CHARGES`, gravity 0.05 like the Type-63's rockets): charge 1 at 7 blocks a tick
  (about 1,000 blocks at 45°), charge 2 at 10 (about 2,000), charge 3 at 14.15 (about 4,000). Any target inside a
  charge's reach can be hit on a low arc (below 45°) or a high arc (above).
- **Fire mission:** the target is synced to the gun. The panel shows the target's grid, its range and bearing, and for
  the selected charge the elevation to lay for the low and high arcs (solved with the same tick-by-tick ballistics as
  the flight), with the gun's present lay beside them and an "on" mark within 0.2°. Out of a charge's reach, it says
  so and names the charge that reaches.
- **Observer:** looking through a spyglass and pressing attack marks the block looked at (up to 512 blocks away) for
  every howitzer within 4,500 blocks of the observer. The observer sees a marker and the range from the nearest gun;
  the crew hears a ping.
- **Deploy:** 100 ticks (trails open over the first 60, spades go down over the rest). Packing up runs it backwards.
  It can't fire until deployed, can't be laid while packed (the gun sits at its travel lock), and can't be towed
  unless packed and unmanned.
- **Rounds:** HE (300 direct, 200 within 12 blocks; blocks as the mortar's round, power 8, through the shared warhead
  rules) and smoke (a smoke screen where it lands, as the smoke grenade's, three times as wide). One round in the
  breech; loading takes 4 s.
- **Far flight:** shells use `ShellEntity` on `FarFlight`, so they fly their full range through unloaded terrain, and
  distant impacts show (`DistantTerrainRefresh`).

## Hitbox

Generated by `generate.py`, packed and deployed: carriage and axle (`body`), wheels (`wheel_left`/`wheel_right`),
shield and top carriage (`body`, `follows: turret`), cradle and tube (`turret`, `follows: barrel`), trails (`body`;
the deployed set opens with them). The gun is the `turret` part, so a hit there jams it, as on the Mk42.

## Pack interplay

| Mod | How it cooperates |
| --- | --- |
| Carry On | `jeg:howitzer` on the entity blacklist |
| Claim mods | block damage goes through the shared warhead rules (vanilla explosions), as for every shell |
| Create Big Cannons | different role (crewed indirect fire versus block-built guns) |

## Sound

Real recordings (`prepare_audio.py`): a field gun's report up close and for the crew, a US Army piece's rounds rolling
off the ground from afar; the Mk42's loading. **To be checked by ear.**

## Not built yet

- A sandbox world.
- A drawn tow bar (the FSV-6 doesn't draw one for any vehicle yet).
