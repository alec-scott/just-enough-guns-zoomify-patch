# YX-100 main battle tank brief

Status: **being built, 2026-11-17.** Model refit: [refit.py](refit.py), [rig.json](rig.json).

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Peers:** the [PLZ-05](../plz05/BRIEF.md) (shell gun on a tracked hull, roof gun, the track driver, the refit it is
  modelled on), the [BMP-2](../bmp2/BRIEF.md) (driver who also fires)
- **Source:** Superb Warfare's `yx_100` (model, texture, sounds), used with the SBW team's permission; see
  [the port plan](../superbwarfare-port/PLAN.md).

## Decisions (user, 2026-11-17)

- **SBW's YX-100, not a real tank.** A Leopard 2A7 was generated first in its place (triage's recommendation); the
  user reverted it to the YX-100 and asked for it to be ported. It is the fleet's one deliberate science-fiction
  vehicle: the white and blue panels, cyan strips, drone cells, the two small pods on the turret's flanks (SBW's
  bones call them `las_aps`; SBW gives them no weapon) and the reactor stay as SBW drew them, all cosmetic. Its size is a real tank's.
- **It fires the Mk42's 5-inch shells** (HE with proximity fuze, AP, cluster, grapeshot; grapeshot became the incendiary on 2026-11-19), as SBW's does.

Kept from the Leopard (taken by default from SBW's seats, for review): the driver drives and fires the main gun and
coax (one player can fight it alone); the commander works the remote weapon station (a 12.7 mm) from inside; two
riders sit on the rear deck. SBW's swarm drones (its third seat) are left out: the port plan rules them out.

## Purpose

- **What the player uses it for:** direct fire against armour and hard points. From day 80 the enemy brings BMP-2s and
  LAV-150s; this outranges and outlasts both. The BMP-2 carries a squad and the PLZ-05 fires from far off; the tank
  closes and kills.
- **Success:** drive across rough ground, stop an enemy BMP-2 with one AP round while moving, and clear infantry with
  the coax and the weapon station.

## Size

SBW's model is 5.0 blocks wide over the skirts and 11.5 long with the gun forward. **Scale 0.74** makes it 3.7 wide
(a Leopard 2's 3.77 m), 7.0 long over the hull, 8.5 with the gun forward, 2.4 to the turret roof.

## Moving parts

`turret` (traverse), `barrel` (elevation), `gun_tube` (the tube and its shroud, recoils 6.8 px), the 41 links a side
(`track_link_<side>_<i>`, `_turn`), `idler_*` (front), `road_wheel_*_1..7`, `sprocket_*` (rear), `rws_mount` (yaw) and
`rws` (pitch). No return rollers: the top run rides the idler and sprocket.

## Controls

| Seat | Key | Action |
| --- | --- | --- |
| Driver (0) | W / S, A / D | drive; the tracks pivot it on the spot |
| | Mouse | lay the gun (the turret slews after the view, 2° and 1.5° a tick) |
| | Left button | fire (on the move too) |
| | X / Z | next / previous: HE, AP, cluster, grapeshot, coax |
| | R | reload from the hold |
| Commander (1) | Mouse, left button, R | the remote weapon station |
| Riders (2, 3) | | hand weapons, on the rear deck |
| Anyone | change seat (B) | the next free seat |

## Behaviour

- **Main gun:** the Mk42's shells and warheads on the shared shell path, flat and fast (40 blocks a tick, SBW's), one
  in the breech, 3.5 s to load (SBW's 70 ticks). Elevation up to +30° (SBW's).
- **Depression by bearing** (measured by the refit): SBW allows −10° all round, but its gun sits low over the hull.
  It depresses to −3.5° ahead, −2° 15–40° off the bow (the front deck's fittings), about −7° abeam, and stays at +2°
  to +3.5° over the engine deck (145–215°).
- **Weapon station:** a 12.7 mm aimed by the commander's view, −10° to +60° (SBW's), long-range rounds.
- **Hitbox:** hull, each track, the engine deck at the rear, the turret house (with the drone cells), and the gun
  outside it, the last two turning with the turret.

## Pack interplay

| Mod | How it cooperates |
| --- | --- |
| Carry On | `jeg:yx100` on the entity blacklist |
| Claim mods | block damage through the shared warhead rules |

## Sound

SBW's YX-100 recordings (cannon near, far, very far and crew; loader; engine, with loops derived from it).
**To be checked by ear.**
