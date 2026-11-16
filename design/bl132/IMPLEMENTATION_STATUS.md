# BL-132 implementation status (2026-11-16)

Brief: [BRIEF.md](BRIEF.md).

## Built

- **Model:** SBW's `bl_132` refit by [refit.py](refit.py) at scale 1.0: 69 bones renamed (guns numbered 1-4 as the
  gunner sees them), per-face UV checked pixel-identical, 57 coplanar pairs fixed, `rig.json`, hitbox written into
  `data/jeg/vehicles/bl132.json`. SBW ships no LODs for it.
- **Entity:** `Bl132Entity` on the shared `ShellGunEntity` (built for it out of the Mk42: shells per weapon, muzzles
  fired in turn, per-barrel recoil, report). The Mk42 now uses the same base.
- **HUD, aim, bone driver:** the Mk42's, made generic (`renderShellGunPanel`, `ShellGunAim`, the default case of the
  bone driver for any `ShellGunEntity`).
- **Resources:** sounds (with CREDITS.md), lang, assembling-table recipe, Carry On blacklist, alignment-mapping row.

## Played (mcpfabric, pack client with Photon)

- Summoned and viewed by day: renders opaque and lit under shaders, all four guns.
- Boarded, moved HE into the 27-slot hold, reloaded, held fire: four rounds in under a second, then the reload
  started by itself and refilled from the hold.
- Found and fixed: the weapon list's "130 mm HE" ran into the counts (now "130 HE"); the gunner's camera behind the
  sight tower had the tower filling half the view (now on top of the tower, guns framing the bottom of the view).

## Tested (`Bl132ClientGameTest`, `-PwithPack`, passes)

| Check | Result |
| --- | --- |
| Guns hit where they point at turret yaw 0 and 90; old place empty; entity box holds them | pass |
| Held trigger: four shells from four different muzzles, 4 ticks apart | ticks 269/273/277/281; lower left, lower right, upper left, upper right |
| Each barrel recoils as it fires, in turn | 10 px, running out over 10 ticks, one barrel at a time |
| Reload after the guns run dry | 87 ticks (SBW's 90) |
| 45° HE shell through unloaded terrain | 3,747 blocks; ballistics 3,747; HUD 3,752 |

Warhead behaviour (damage, proximity, cluster, grapeshot, danger close) is shared with the Mk42 and tested there.

## Not covered yet

- **Listening** to the SBW recordings.
- The night view and F5 from the side.
- Firing at real enemy vehicles and aircraft.
- The dedicated server, multiplayer, claim mods with the shells' block damage.
- No sandbox world yet.
