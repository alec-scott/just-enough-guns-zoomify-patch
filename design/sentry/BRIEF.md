# Perimeter sentry brief

Status: **pass 1 being built, 2026-11-17** ([generate.py](generate.py), [rig.json](rig.json)). From
[the fleet plan](../FLEET_PLAN.md), section 6: it replaces the laser tower (a science-fiction turret, retired); its
decisions (2026-11-15) apply: automatic fire on by default, capped per chunk.

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Peers:** the HPJ-11 (the other base defence, the same automatic logic: `AutoDefenceEntity`)

## Purpose

- **What the player uses it for:** hold a base's perimeter on horde nights while the players are busy elsewhere.
- **Success:** three sentries on a wall, fed rifle ammunition, hold off a horde night's first wave, and never shoot a
  player, villager or pet.

## Real-world reference

- **Class:** a remote weapon station on a tripod (Kongsberg Protector / Samsung SGR-A1 class): a 7.62 mm machine gun on
  a pan-tilt head, a day/thermal camera and an IR lamp, about 1.6 × 2.1 × 1.6 blocks.
- **Model:** our own, generated ([generate.py](generate.py)): a tripod and hub, the head (`turret` yaw, `barrel` pitch,
  −30° to +60°), the gun with its ammunition can and a belt (`belt_1`..`belt_4`) that empties as the hold runs down,
  the camera and IR lamp, and the status lamp (`lamp_green` watching, `lamp_amber` tracking, `lamp_red` firing or out
  of ammunition).

## Controls

| Seat | Key | Action |
| --- | --- | --- |
| Operator (0, remote) | Mouse | lay the gun (the head slews at 12° and 8° a tick); the view is the camera's |
| | Left button | fire |
| | N | manual or automatic |

## Behaviour

- **The gun** (`vehicle_sentry_machine_gun`): 7.62 mm, fed from the hold (rifle ammunition), about 600 rounds a minute,
  heard as the coax (a report on the shared distance curve), long-range rounds.
- **Automatic** (N; works manned or not): monsters within 48 blocks in sight; those going for a player or a villager
  first, then the nearest. It leads them and fires when within 3°.
- **Never fires** while anything that isn't a monster (a player, a villager, a pet, a passive animal, a vehicle) stands
  in its line of fire.
- **Server limits:** as the HPJ-11's (`autoDefenceEnabled`, `autoDefencePerChunk`, counted across every automatic
  defence in the chunk).
- **Cheap to build** so a base has several: 14 iron, 4 copper, 6 redstone, a glass pane.

## Hitbox

From the generator: the tripod, the head (turning), the gun, camera and ammunition (elevating).

## Pack interplay

| Mod | How it cooperates |
| --- | --- |
| Undead Nights, Zombie Break & Build | the hordes it holds off (what is hostile is vanilla's `Enemy`) |
| Carry On | `jeg:sentry` on the entity blacklist |

## Sound

Its own report: a real IWI Negev NG-7 (7.62x51, CC0), near, distant and operator takes on the shared distance curve
(`prepare_audio.py`); the servo while the head slews; a dry-fire click when it is empty with a target.
