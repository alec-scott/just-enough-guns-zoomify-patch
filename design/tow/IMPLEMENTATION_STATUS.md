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

## Not covered yet

- Listening (the launch and reload sounds are stand-ins).
- A moving target at 400 blocks; losing the missile behind a block (the rule is in, not tested); the 1,000-block wire end.
- Night view.
- The dedicated server, multiplayer, claim mods.
- No sandbox world yet.
