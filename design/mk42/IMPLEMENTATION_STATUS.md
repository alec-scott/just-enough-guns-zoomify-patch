# Mk42 implementation status (2026-11-16)

What exists, how it was checked, and what was not. Brief: [BRIEF.md](BRIEF.md).

## Built

- **Model:** SBW's `mk_42`, refit by [refit.py](refit.py) at scale 1.0: every bone renamed, per-face UV (checked
  pixel-identical), 8 coplanar pairs fixed, both LODs renamed, `rig.json`, and the hitbox written straight into
  `data/jeg/vehicles/mk42.json`.
- **Entity:** `Mk42Entity`; static engine, one enclosed gunner seat with the camera riding the gun ahead of the gun
  port; HE, AP, cluster and grapeshot on the shared vehicle-gun path (hold ammo, 20-round drum, 30 ticks a shot, 6 s
  drum reload). Cluster and grapeshot use the shell's new dispense stage (`ShellEntity.Dispense`).
- **Slew limit (shared):** `traverse_rate`/`elevation_rate` in the turret data. The view is free, the server turns
  the gun toward it, and the HUD draws a bore marker (`renderBoreMarker`) for any slew-limited turret.
- **Shared systems built with it:**
  - `ShellEntity` / `ShellBallistics` / `ShellAim`: the mortar's round and predictor generalised; the mortar now uses
    them (its test still passes).
  - `GunReportPayload` / `GunReportAudio`: the mortar's report generalised, with an interior layer for the crew.
  - `OBBInfo.Follows` (fleet plan Phase 0.4): boxes that follow the turret and barrel; entity box follows the aim.
  - `VehicleEntity.firesShell`/`fireShell`, `barrelPoint`, `barrelDirection`.
- **HUD:** fire-control panel (shell, elevation, bearing, predicted range and time of flight, impact marker on loaded
  terrain); vehicle info moved to the top middle for this gun.
- **Recoil:** `gun_slide` slides 11 px back and runs out over 6 ticks, from the synced shot count.
- **Resources:** SBW sounds (3p, far, 1p, loader), shell icons, lang, recipes (assembling table; shells at a crafting
  table), Carry On blacklist (both folders), alignment-mapping row.

## Played (mcpfabric, pack client with Photon)

- Summoned, viewed from front and three-quarter by day: renders opaque and lit under shaders.
- Boarded, opened the hold with the inventory key, moved shells in, reloaded with R, fired HE with the left button:
  drum counts down, muzzle flash and smoke at the muzzle, an HE burst 42 blocks out where the panel said.
- Panel numbers checked against theory at 15° (1,881 blocks, 6.5 s; theory 1,875 blocks, 129 ticks).
- Found and fixed: the vehicle info box ran into the fire-control panel in F5 and sat over the barrel in first
  person; the F5 camera was low behind the gunhouse.

Second pass (after the slew limit):
- The gunner's camera first rode the barrel; looking 90° away while the gun turned showed the gunhouse wall. It now
  sits on the roof (`"transform": "turret"` camera, new and shared): the view is clear while the gun catches up, and
  the barrel and bore marker line up under the crosshair once it has.
- F5 at the new distance shows the whole mount; the midnight view under Photon reads fine.
- HE fired at full depression burst 12 blocks out and hit its own gun for 88. Fixed: a shell is a dud within its
  blast radius (+1) of the gun that fired it (`ShellEntity.armedAt`), and the panel warns "Too close: fuze stays safe".
- The weapon list cut off "Cluster"/"Grapeshot": the list says CM/GS, the panel the full name.

## Tested (`Mk42ClientGameTest`, `-PwithPack`, passes)

| Check | Result |
| --- | --- |
| Gun hit where it points at turret yaw 0, 90, 180; its old place empty; gunhouse rear empty after 90° | pass |
| Entity box holds the gun at every yaw | pass |
| Gun follows the view to 45°; R loads 16 HE from the hold | pass |
| 45° HE shot through unloaded terrain | 3,733 blocks in 351 ticks; ballistics 3,735; HUD 3,751 |
| Gunner hears the interior report | pass |
| HE at 2 / 6 / 11 blocks | 113 / 76 / 0 |
| AP direct / 2 / 4 blocks | 560 / 64 / 0 |
| Proximity: HE passing 2.5 blocks from a phantom bursts; at 6 flies on; AP never | pass |
| Slew: elevation after 20 ticks, train after 20 ticks | 23.75° (25°/s), 38° (40°/s); both reach the view |
| Cluster shell near the ground | 18 bomblets; a target among them took 147 |
| Grapeshot | 36 pellets at the muzzle; a target 30 blocks out took 7 pellets (191) |
| HE bursting 9 blocks from its own gun | dud: hull 350 -> 350 |
| Cluster over a 3x3 grid of targets 3 blocks apart | 18 bomblets; 556 in all |

`MortarClientGameTest` was rerun after the shared refactor and passes (765.9 blocks at 45°, blast 94/48/0).

## Not covered yet

- **Listening:** none of the SBW sounds have been heard by a person. Please check `fire`, `fire_distant`,
  `fire_interior` and `load` for quality and for voices.
- Far observer at 1,100 blocks (the report rides `GunReportPayload` to 1,024 blocks like the mortar's).
- Firing at a real enemy AH-6 or BMP-2 (the fuze and damage are tested on mobs).
- The dedicated server's own mods, multiplayer, and claim mods with the shell's block damage.
- No sandbox world yet.
