# TOW implementation status (2026-11-16)

Brief: [BRIEF.md](BRIEF.md).

## Built

- **Model:** SBW's `tow` refit at scale 1.0 (all 25 bones named; no coplanar faces; no LODs or clips in the source),
  item icon from SBW's vehicle icon, hitbox written into `data/jeg/vehicles/tow.json`.
- **Entity:** `TowEntity` on the shared vehicle-gun path (aim, fire, crate, reload), plus wire guidance and pack-up.
- **Shared:**
  - `DeployableWeaponItem` (the mortar's item, generalised; `MortarItem` now extends it).
  - `VehicleEntity.onMissileLaunched` hook.
  - `VehicleMissileEntity.LineOfSightLauncher` and `steerLineOfSight` (SACLOS with wire-break rules).
  - Low-smoke missiles in `MissileTrailRenderer`, with a `lowSmoke` flag on the far track packet.

## Played (mcpfabric, pack client with Photon)

- Set up from the item with a real right-click, viewed under shaders, boarded with a real right-click, loaded via the
  crate, reloaded with R, fired.
- Found and fixed:
  - The camera sat inside the sight housing, which cluttered the view: the sight isn't drawn in the gunner's own
    first-person view.
  - The missile's standard thick trail lay along the line of sight and hid the missile for seconds: the TOW now
    lays a thin trail only while its motor burns, and the motor glow shows on the reticle.
  - The vehicle info box sat just under the reticle: moved to the top.

## Tested (`TowClientGameTest`, `-PwithPack`, passes)

| Check | Result |
| --- | --- |
| Set up with a real right-click on the ground | pass |
| R loads one missile from the crate | pass |
| A BMP-2 100 blocks down the sight line | destroyed by one missile (320 hull) |
| Turn 12° after launch | the missile is 0.002 blocks off the new sight line, 188 blocks out; wire intact |
| Gunner leaves the sight | wire breaks; the missile keeps its heading (dot 1.0) |
| Sneak-click pack-up | launcher and the missile left in the crate returned |

## Finalisation (2026-11-18, design/READINESS.md)

- **A real launch** (`prepare_audio.py`, `vehicle.tow.launch`), replacing the BMP-2's missile sound: a BGM-71 TOW
  launch from US military footage (qubodup, CC0) over the roar and echo of an anti-tank missile leaving the tube on a US
  Army range (qubodup, CC0). The reload keeps the mod's launcher handling sounds (lid open, round in, lid closed).
- **Weight:** `"mass"` 0.1 t (tow load only).

| Test (`-PwithPack`) | Result |
| --- | --- |
| `TowClientGameTest` | passes, now also listening: `vehicle.tow.launch` heard |

Played (mcpfabric, pack client, midnight): loaded two missiles through its hold, R to load, the sight's bracket reads
clearly in the dark; fired at a target 60 blocks off: hit, "Reloading 6s" in the panel.

## Not covered yet

- **Listen:** `vehicle/tow/launch`; the reload's handling sounds.
- A moving target at 400 blocks; losing the missile behind a block (the rule is in, not tested); the 1,000-block wire end.
- The dedicated server, multiplayer, claim mods.
- No sandbox world yet.
