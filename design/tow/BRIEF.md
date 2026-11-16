# TOW launcher brief

Status: **built and tested, not yet handed over, 2026-11-16** (built while the user was away; defaults at the end for
review). Model refit: [refit.py](refit.py), [rig.json](rig.json).

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Peers:** the [mortar](../mortar/BRIEF.md) (carried, set up from the hand) and the BMP-2's ATGM
- **Source:** Superb Warfare's `tow` (model, texture, numbers), used with the SBW team's permission.

## Purpose

- **What the player uses it for:** kill armour from a prepared position. From day 80 enemy BMP-2s and LAV-150s
  arrive; a squad sets a TOW up behind cover and kills them at up to a kilometre, steering the missile by eye.
- **Success:** set it up from the item, load from its crate, and hit a moving BMP-2 at 400 blocks.

## Real-world reference

- **Class:** BGM-71 TOW on the M220 tripod launcher. Tube 1.69 blocks, sight on its left, about 1.2 blocks up:
  **scale 1.0** (triage: about 1:1).
- **Guidance:** SACLOS by wire: the gunner keeps the crosshair on the target and the launcher steers the missile onto
  the line of sight. The flight motor burns about 1.6 s with little smoke; a lamp in the tail lets the sight track it.
- **Moving parts:** `turret` (traverse, ±90° of the tripod), `barrel` (the tube, -40° to 40°), `missile` (the round,
  shown while one is loaded), `sight` (hidden in the gunner's own first-person view, which looks through it).

## Controls (the vehicle-gun family)

| Key | Action |
| --- | --- |
| Right-click the ground with the item | set it up facing where you look |
| Right-click it | sit behind it at the sight |
| Mouse | lay the tube (it follows the view), and steer the missile in flight |
| Left button | fire |
| R | load the next missile from the crate (7.5 s) |
| Inventory key, seated | open the crate (9 slots) to put missiles in |
| Sneak + right-click, empty hand, on foot | pack up: the launcher and every missile come back |

## Behaviour

- **Wire guidance** (`VehicleMissileEntity.LineOfSightLauncher`, shared): every tick the missile is steered back onto
  the sight line a little ahead of itself. The wire breaks for good, and the missile flies on straight, when the
  gunner leaves the sight, the launcher is lost, the sight loses the missile behind a block, or it has run out
  1,000 blocks of wire.
- **Missile:** `vehicle_tow_missile` on the shared missile path (FarFlight, far tracks, Voxy refresh), ~4 blocks a
  tick, 400 ticks. SBW's 600 to a vehicle it strikes and 60 to the living within 6 blocks; blocks as the BMP-2's
  missile.
- **Low smoke:** a thin trail only while the motor burns (32 ticks), near and far (`MissileTrackPayload.lowSmoke`),
  so the gunner can see the missile down the line.

## Hitbox

From `refit.py`: tripod (hull), traversing unit and sight (`follows: turret`), tube (`follows: barrel`, part
`turret`).

## Sound

The BMP-2's missile launch (as SBW uses for the TOW), and the rocket launcher's reload sounds. **To be replaced or
checked by ear:** a real TOW launch, and the loading of a tube.

## Decisions taken by default (2026-11-16), for review

1. **Carried and set up from the hand**, like the mortar (SBW assembles it as a vehicle). Crafted at a crafting
   table.
2. **Missiles from a 9-slot crate on the tripod** (the shared vehicle-gun path), not from the gunner's pockets as the
   mortar does. Packing up returns them.
3. **Traverse ±90°** of where it was set up (the gunner sits behind it); set it up again to face elsewhere.
4. **Wire length 1,000 blocks** (the real one is 3.75 km; SBW's missile flies about 1,600 blocks in all).
5. **SBW's damage** (600 kills a BMP-2 in one hit).
