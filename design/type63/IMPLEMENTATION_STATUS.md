# Type-63 implementation status (2026-11-16)

Brief: [BRIEF.md](BRIEF.md).

## Built

- **Model:** SBW's `type_63` refit at scale 0.78 (all 158 bones named: 12 tubes, 12 rockets, handwheels, wheels,
  trails; 40 z-fighting pairs fixed), two LODs, rocket icons from SBW, hitbox written into
  `data/jeg/vehicles/type63.json`. SBW has no clips, so the refit writes an empty `animation.type63.rest`.
- **Entity:** `Type63Entity` on `ShellGunEntity`: three rockets (HE, AP, cluster), the tube order, rockets hidden as
  they fire, A/D carriage turn, 15° mount traverse.
- **Shared:** `ShellGunEntity.nextBarrel`/`barrelFiring` hooks for weapons whose tubes empty in order, and
  `ShellEntity.markRocket` (motor glow and short trail).

## Played (mcpfabric, pack client with Photon)

- Looked at it under shaders, boarded, loaded HE through the crate, reloaded with R, rippled a salvo at 30°: the
  panel read 1,726 m, and the rockets left with short motor trails.

## Tested (`Type63ClientGameTest`, `-PwithPack`, passes)

| Check | Result |
| --- | --- |
| R loads all twelve from the crate | 12 on server and client |
| Mount asked 40° off the carriage | stops at 15° |
| A for 20 ticks | carriage turns 20°, server follows |
| Held trigger | 12 rockets, each from its own tube, 14 ticks apart (154 ticks); drawn rockets go down with them |
| Reload of the crate's last rocket | loads 1 |
| 45° shot | 1,991 blocks in 283 ticks; HUD predicted 1,995 |

Fixed on the way: GeckoLib logged a missing `animation.type63.rest` every frame; the test's crate stack of 13 was
clamped to 12, so the range shot had nothing to load.

## Not covered yet

- Listening (a full ripple heard at range).
- AP and cluster rounds fired at range; a ripple's spread on the ground.
- Night view; the third-person view while firing.
- Towing behind the FSV-6 (the tow-eye hook is tested on the howitzer, `HowitzerClientGameTest`, not on the Type-63).
- The dedicated server, multiplayer, claim mods. No sandbox world yet.
