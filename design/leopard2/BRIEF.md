# Leopard 2A7 main battle tank brief

Status: **being built, 2026-11-17.** Our own generated model: [generate.py](generate.py), [rig.json](rig.json).
Replaces Superb Warfare's YX-100 (a science-fiction tank; [triage](../superbwarfare-port/triage/TRIAGE.md)) and keeps its
behaviour.

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Peers:** the [PLZ-05](../plz05/BRIEF.md) (shell gun on a tracked hull, roof gun, the track driver), the
  [BMP-2](../bmp2/BRIEF.md) (driver who also fires), the [howitzer](../howitzer/BRIEF.md) (generator layout)

## Decisions (user, 2026-11-17)

- **A Leopard 2A7**, the real tank nearest the YX-100's silhouette (wedge turret, flat hull, long gun).
- **It fires the Mk42's 5-inch shells** (HE with proximity fuze, AP, cluster, grapeshot), as the YX-100 did.

Taken by default from the YX-100 (for review): the driver drives and fires the main gun and coax (one player can fight
it alone, as on the BMP-2); the commander works a remote weapon station (a 12.7 mm) from inside; two riders sit on the
rear deck. The YX-100's swarm drones are left out (science fiction).

## Purpose

- **What the player uses it for:** direct fire against armour and hard points. From day 80 the enemy brings BMP-2s and
  LAV-150s; this outranges and outlasts both. The BMP-2 carries a squad and the PLZ-05 fires from far off; the tank
  closes and kills.
- **Success:** drive across rough ground at 60 km/h, stop an enemy BMP-2 at 1 km with one AP round while moving, and
  clear infantry with the coax and the weapon station.

## Real-world reference

- **Class:** Leopard 2A7 (Germany): 120 mm L/55 smoothbore, coaxial MG, FLW 200 remote weapon station, 4 crew.
- **Proportions (1:1):** hull 7.7 m, 11 m with the gun forward, 3.77 m wide over the skirts, about 2.6 m to the turret
  roof. Seven road wheels a side, the idler at the front, the drive sprocket at the rear, four return rollers behind
  the skirts.
- **Gun:** −9° to +20°; turret and gun stabilised, so it fires on the move.
- **Mobility:** about 68 km/h on roads; the engine and transmission at the rear.
- **Moving parts:** `turret`, `barrel`, `gun_tube` (recoils), the track links (the shared driver), `sprocket_*`,
  `idler_*`, `road_wheel_*`, `return_roller_*`, `rws_mount` (yaw) and `rws` (pitch).

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
  in the breech, 3.5 s to load (SBW's 70 ticks).
- **Weapon station:** a 12.7 mm aimed by the commander's view, long-range rounds.
- **Hitbox:** hull, each track, the engine at the rear, the turret, and the gun outside it, the last two turning with
  the turret.

## Sound

SBW's YX-100 recordings (cannon near, far, very far and crew; loader; engine). **To be checked by ear.**
