# Speedboat brief

Status: **refit pass 1 being built, 2026-11-17** ([refit.py](refit.py), [rig.json](rig.json)). From
[the fleet plan](../FLEET_PLAN.md), section 4; its decisions (2026-11-15) apply: the driver keeps the gun when alone, a
passenger in the gunner's seat takes it over.

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Peers:** the LAV-150 (fast, lightly armed, carries a squad), the FSV-6 (it resupplies it)
- **Source:** Superb Warfare's `speedboat` (shipped unchanged since the port; now refit), used with the SBW team's
  permission.

## Purpose

- **What the player uses it for:** move a squad across water faster than anything else and put it ashore. Create's
  block-built ships are big and slow; this is small and fast.
- **Success:** carry four players 1 km down a river and along a coast, beach the boat, and land them dry, with the bow
  gunner covering the landing.

## Real-world reference

- **Class:** a riverine assault boat, SURC class: 7.6 × 2.7 m, about 60 km/h, a planing hull, an M2 on a pintle at the
  bow, the helm in an open cabin behind a windscreen.
- **Refit:** SBW's hull is 7.4 blocks long: **scale 1.0**. It is 3.3 wide against 2.7; its shapes are kept.
- **Moving parts:** `turret` (the pintle's yaw) and `barrel` (the gun's elevation), `propeller` (spins), `rudder`
  (turns), `root` (the bow rises on the plane).

## Decisions taken in pass 1

- **The gun moves to the bow.** SBW's sits on the cabin roof, where its gunner would stand on the roof. The refit puts it
  on a pintle on a pedestal at the bow, with the gunner's seat on the foredeck behind it, ahead of the windscreen; the
  gunner swings round with the gun. It traverses 128° either side of the bow (aft of that the bore line crosses the
  cabin) and elevates −25° to +50°.
- **A 12.7 mm, not the coax.** The model is an M2, so it fires a heavy MG (`vehicle_speedboat_hmg`, as the PLZ-05's roof
  gun: long-range rounds, the medium flash) instead of the 7.62 mm coax it used to.
- **Seats:** the driver at the helm (0), the bow gunner (1), four on the stern benches (2 to 5).

## Controls

| Seat | Key | Action |
| --- | --- | --- |
| Driver (0) | W / S, A / D | throttle ahead and astern, rudder |
| | Mouse, left button, R | the bow gun, while nobody is at it |
| Gunner (1) | Mouse, left button, R | the bow gun |
| Passengers (2 to 5) | | hand weapons |
| Anyone | change seat (B) | the next free seat |

## Behaviour

- **Planing** ("boat" in the data): below 0.42 blocks a tick (30 km/h) the hull ploughs through the water (high drag);
  above it, it rides on top, drag drops, and it reaches about 60 km/h. The bow rises as it climbs onto the plane (up to
  5°) and settles to half that flat out; every client sees it from the hull's speed.
- **The keel:** sideways slip is mostly lost each tick, so it carves turns instead of skidding.
- **Beaching:** run at a shore no higher than 1 block above the water at 0.3 blocks a tick or more, the hull rides up
  onto it and grounds instead of stopping dead. Ashore it only crawls (a fifth of its speed), enough to back off a beach
  under power or to reach the water from the workbench.
- **Currents:** the hull keeps whatever the water pushes it with (vanilla flowing water; the server's Streams
  Reflowing gives its streams no current, `vanillaWaterFlow=false`). The old fixed 40 km/h cruise, which overwrote that
  push, is gone.
- **Spray and wake** on the plane: spray off both sides of the bow, a churned wake behind the stern.
- **Lights:** red to port, green to starboard, white masthead and stern lights, a searchlight on the cabin roof.

## Hitbox

From `refit.py`: the hull to the gunwale, the cabin, the engine under the stern deck (`main_engine`), the bow gun
(turning with it).

## Pack interplay

| Mod | How it cooperates |
| --- | --- |
| Streams Reflowing | its water and spreading; no stream current on the server; now in `packMods` with the server's settings |
| Carry On | `jeg:speedboat` on the entity blacklist |
| Shippy Ships, Sails and Airships | block-built ships; the speedboat stops at their hulls as at any block (not tested yet) |

## Sound

SBW's engine recording, made into idle, working and distant loops (`GroundVehicleAudio`, heard far off). The plan's CC0
outboard or inboard diesel and hull slap are still to find. **To be checked by ear.**
