# PLZ-05 implementation status (2026-11-17)

Brief: [BRIEF.md](BRIEF.md).

## Built

- **Model:** SBW's `plz_05` refit at scale 0.77 by [refit.py](refit.py): all 376 bones named (links
  `track_link_<side>_<i>` and `_turn`, sprocket, road wheels, idler, return rollers, hatches, sight), 99 coplanar pairs
  fixed, its LOD renamed, the hitbox written into `data/jeg/vehicles/plz05.json`. The refit adds the roof 12.7 mm
  (`hmg_mount`, `hmg`) on the right-hand hatch, coloured from a palette corner added to SBW's texture where no face
  samples it.
- **Tracks (shared, new):** the refit computes the links' path from the running gear (within 1.45 px of SBW's
  hand-typed path) and writes it to the data as `"tracks"`; `TrackInfo` parses it; `VehicleEntity.trackTravel` runs
  each side from the hull's movement and turn on the client; `AbstractVehicleGeoRenderer.updateTracks` moves the links
  and turns the wheels. Any vehicle whose model names its links that way gets tracks with no code.
- **Entity:** `Plz05Entity` on `ShellGunEntity`: three charges with low and high arcs (V), the fire control (N, from
  the gunner), fires only at a halt, the Mk42's four shells, the roof gun on the long-range path, hatch seats that ride
  the turret.
- **Shared, new:** `FireMissions` (observer marks and typed missions reach every fire-mission gun; the howitzer moved
  onto it), `turretAutoLay`, `canFireWeapon`, `firesLongRangeRounds`, `seatGunMuzzle`, `seatRidesTurret`,
  `takesTowKeyFrom`, `seatOf`, `ShellGunEntity.laysGun`.
- **Data:** assembly recipe (56 iron, 4 iron blocks, 20 copper, 16 redstone, 2 green dye), weapon stats, sounds (SBW's),
  language, Carry On blacklist, alignment-mapping row.

## Played (mcpfabric, pack client with Photon)

- Looked at it by day from the side, rear and three-quarter: opaque and lit, links round the running gear, roof gun
  on its hatch. Drove it and compared a burst of frames: the top run's links move.
- Loaded HE and rifle ammunition through the hold; changed seat (B) to the gunner; set a mission 1.5 km off by command;
  engaged the fire control: the turret swung round about 135° onto it by itself. Fired two rounds: the second landed
  within about 10 blocks of the target.
- Changed seat to the roof gunner and fired the 12.7 mm; checked its aim against the view at three headings.
- Found and fixed:
  - The driver saw the gunner's fire-control panel (and the impact marker); the gunner, in seat 1, didn't. The
    panel now goes to whoever lays the gun (`laysGun`); the bore marker compared the passenger's list position with
    the seat, now `seatOf`.
  - The roof gun threw the cannon flare (a fireball and smoke blocks wide) into its gunner's face: its id lacked
    `machine_gun`. Pintle guns now get the helicopters' medium flash.
  - The roof gunner's eye sat 0.07 blocks above the receiver, which filled the view: the seat is 0.22 higher.

Second pass (after the fixes), pack client with Photon:
- The driver no longer sees the gun's panel; the gunner does, with the fire control ON. The driver's eye sat 0.2
  blocks under the barrel, which filled the top of the view: it is 0.2 lower, just over the glacis, looking past the
  travel lock.
- The panel's extra rows ran under the vehicle info box (their label column was sized by "Fire control"): the labels
  are six letters now and the charge row shorter.
- The roof gunner sees over the gun; the flash is the helicopters' medium one at the muzzle; tracers run to the
  crosshair.
- Night (Photon, midnight): a moonlit silhouette, no glow (it has no lights yet).

## Tested (`Plz05ClientGameTest`, `-PwithPack`, passes; `HowitzerClientGameTest` rerun after the fire-mission move, passes)

| Check | Result |
| --- | --- |
| Hull moved 10 blocks forward (watched, not driven: the client interpolates) | both tracks ran 160.0 px |
| Quarter turn right on the spot | left +35.1 px, right −35.1 px |
| Trigger held while the hull moves 0.2 a tick | not halted, no shell |
| `/justEnoughGuns firemission 0 4000`, N, gunner looking 90° off | laid at 20.8° (charge 3, low) |
| Three HE rounds at 4 km | 3.7, 6.8 and 6.1 blocks from the target (bound: 40) |

Found by the test: a watched (not driven) PLZ-05's tracks didn't move. Travel was measured across `super.tick()`, but a
watched entity is moved by interpolation earlier in the tick; it is now measured from the last tick's end.

## Since (2026-11-17)

- Heard the aircraft way (`GroundVehicleAudio`, far tracks; `GroundVehicleSoundClientGameTest`), and every gun on the
  long-range path with reports (`VehicleGunReachClientGameTest`). Real start, stop and cab recordings are still to find.

## Not covered yet

- Listening: the engine loops (derived from SBW's engine take), the report and the loader are unchecked by ear.
- An observer's spyglass mark at range (the howitzer's limit applies: 512 blocks); the high arcs; AP, cluster and
  grapeshot fired from it; the roof gun's rounds at range.
- Hitboxes shot at; the enemy AI; swimming (it doesn't); climbing at `up_step` 1.25.
- Night view, the dedicated server, multiplayer (a second player watching the tracks is covered only by the test's
  watched case). No sandbox world yet.
