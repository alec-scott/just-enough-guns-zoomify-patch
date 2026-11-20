# YX-100 implementation status (2026-11-17)

Brief: [BRIEF.md](BRIEF.md). Replaces the Leopard 2A7 built earlier the same day (its generator, model and brief are
in git history: commit 28b421c, `design/leopard2/`); the entity, data, weapons and sounds moved from `leopard2` to
`yx100`. The Leopard was never released, so no world holds one outside the dev saves.

## Built

- **Model:** SBW's `yx_100` refit at scale 0.74 by [refit.py](refit.py): all 325 bones named (links
  `track_link_<side>_<i>` and `_turn`, idler, road wheels, sprocket, gun, weapon station, smoke dischargers, the
  science-fiction fittings), 34 coplanar pairs fixed, 30 fully transparent helper faces dropped (SBW's muzzle glow and
  flashes, which only its fire clips scale up), the right sprocket's pivot moved onto its geometry (SBW's sat 4.3 px
  forward, so it would wobble), the tube and shroud grouped under `gun_tube` to recoil. Its LOD refit the same way.
  SBW's 1024² texture kept as is.
- **Tracks:** the path from the running gear (idler, seven road wheels, sprocket; no return rollers), 41 links a side,
  written to the data's `"tracks"`; SBW's link rest is 0.31 px off it and the lowest link 0.23 px above the ground.
- **Depression by bearing:** the refit samples the tube and shroud against the hull's faces at every 5° of traverse
  and writes the lowest clear elevation to `rig.json`; `Yx100Rig.lowestElevation` takes the stricter neighbour and
  `Yx100Entity.clampTurretPitch` holds the gun there. −3.5° ahead, −2° 15–40° off the bow, about −7° abeam, +2° to
  +3.5° over the engine deck. SBW's −10° all round would put the gun through the front deck and the engine deck.
- **Entity:** `Yx100Entity` (the Leopard's, renamed): the Mk42's four shells at 40 blocks a tick, fired on the move;
  coax; the commander's 12.7 mm weapon station; seats, muzzles, turret origin and hitbox from the rig.
- **Lights:** at SBW's amber markers on the front corners and the lamps at the rear plate's corners.
- **Data:** assembly recipe (the Leopard's), weapon stats, SBW's YX-100 sounds (the files the Leopard already used,
  moved), language, Carry On blacklist, alignment-mapping row, the ArmourTest sandbox.

## Played (mcpfabric, pack client with Photon, ArmourTest sandbox)

- By day from the front quarter, the side and above, beside the PLZ-05 and BMP-2: opaque and lit, links round the
  running gear, turret and gun in line with the hull, a little smaller than the PLZ-05.
- Boarded: the gunner's view looks along the gun from its right over the front deck; looking 5° down the gun stopped
  at −3.5° and the panel showed it. Drove at 10 m/s in third person. Fired HE twice (`/jegkey key.attack`): the round
  left, smoke rose ahead, the breech showed a 3.5 s reload.
- Changed seat to the commander: the station's gun follows the view from just behind it. Changed seat to the riders:
  they sit on the rear deck behind the turret.
- Not seen by hand: the muzzle flash close up, the weapon station firing (it needed a reload), lights by night (the
  seat cycle missed the driver; covered by the test below).

## Tested (`-PwithPack`, all pass)

| Test | Result |
| --- | --- |
| `Yx100ClientGameTest`: gunner looks 30° down ahead, abeam, astern | gun at −3.5°, −6.5° (turret −90°), +3.5° (turret 180°) |
| AP fired at 0.39 blocks a tick at a BMP-2 120 blocks ahead | BMP-2 320 → destroyed |
| Weapon station fired from the commander's seat | the round 1.46 blocks from the station's trunnion (its barrel) |
| `VehicleGunReachClientGameTest` | passes with the station's new muzzle |
| `GroundVehicleSoundClientGameTest` | passes with the renamed `vehicle.yx100.*` sounds |
| `ArmourNightLightsClientGameTest` | crewed YX-100 lit at night (beam 1.00), dark by day and uncrewed |
| `ArmourSandboxSetupClientGameTest` | ArmourTest rebuilt with the YX-100 and 10 stacks of ammunition |

## Finalisation (2026-11-17, design/READINESS.md)

- **Road speed** about 62 km/h played flat out (a modern MBT's; `max_forward_speed` 1.6), was 32.
- **Weight (`"mass"` 55 t):** the heaviest: 63 ticks to 80% of top (the LAV-150 28) and 44 to stop (21); rocks
  slowly on its suspension (`VehicleWeightClientGameTest`). Its turret's electric drive is heard as it slews.
- **Sound:** a real diesel start and shutdown (CC0, roboroo, lowered to 0.88 for a heavier engine); muffled in the cab.
- **Destroyed:** cooks off and burns as a charred hulk.
- **Played (pack, ArmourTest):** one 5" HE round destroyed the target BMP-2 at 100 blocks; the fire-control panel
  showed shell, elevation, bearing and range; one aiming mark.
- **Texture:** SBW's 1024² texture is 4 MB of video memory (5.3 MB with mipmaps), fine for one vehicle type.

## Not covered yet

- **Listen:** `engine_start`, `engine_stop` (and `_cab`), `movement/traverse`, `movement/elevation`,
  `movement/brake_heavy`, `movement/bump`; and listening (SBW's recordings and the derived engine loops, unchecked by ear).
- HE, cluster and grapeshot at a target; the coax; hitboxes shot at; the enemy AI (it isn't an enemy vehicle).
- The 1024² texture's memory cost in the pack (16× the others').
- The dedicated server, multiplayer.
