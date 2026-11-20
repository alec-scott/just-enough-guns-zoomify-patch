# HPJ-11 implementation status (2026-11-17)

Brief: [BRIEF.md](BRIEF.md).

## Built (refit pass 1)

- **Model:** SBW's `hpj_11` refit at scale 1.0 by [refit.py](refit.py): every bone named (71; the barrel cluster, both
  radars, the shroud's parts), 39 coplanar pairs fixed, the transparent helper faces dropped, the hitbox written from
  the model (base, mount, each radar turning with the mount, the gun elevating outside it).
- **Entity:** `Hpj11Entity` on the new shared `AutoDefenceEntity` (manual or automatic with N; target search, lead,
  lay through the turret's own slew, fire when on, the server's switch and per-chunk cap). The gun is a rotary
  `vehicle_hpj11_ciws` (belt-fed from the hold, spools up, a round a tick, long-range, armour piercing 7.5) with
  proximity-fuzed rounds (`BulletEntity.setProximityFuze`).
- **Shared, new:** `AirDefenceTargets` (what an air defence may engage: enemy-launched missiles, enemy aircraft,
  phantoms; never players' things); `VehicleMissileEntity.enemyLaunched()` (decided at launch), `owner()`,
  `shootDown()`; `VehicleEntity.fireUnmanned`, `configureVehicleRound`, `isRotaryWeapon`, `rotaryGunDemanded` (any
  vehicle's rotary gun spools up, not only the Mi-28's); the `BulletEntity` constructor owned by any entity; server
  config `autoDefenceEnabled`, `autoDefencePerChunk`.
- **Render:** the barrels spin with the gun, the search radar turns while manned or on automatic, the tracking radar
  follows the synced target.
- **Data:** the CIWS replaces the hand minigun and rocket launcher; health 300, energy 500,000, a 27-slot hold;
  assembly enabled (the existing recipe, about the BMP-2's cost); language.

## Played (mcpfabric, pack client with Photon, ArmourTest sandbox)

- By day: the mount, its gun and both radars stand right beside the sentry and the lake.
- At the controls, first person: held the trigger, the barrels spun up and it fired; third person from behind, the
  tracers and flash at the gun.
- N switched it to automatic ("Automatic: engaging enemy missiles, aircraft and phantoms"). Got out; at midnight
  (normal difficulty) summoned two phantoms 40 blocks out: it laid on them and both came down.
- Found and fixed: firing threw the cannon flare, a fireball that filled the operator's whole view; `_ciws` guns get the
  medium flash now.

## Tested (`Hpj11ClientGameTest`, `-PwithPack`, passes)

| Check | Result |
| --- | --- |
| An enemy crewman's 80 mm rocket fired at it from 150 blocks | shot down 121 blocks out, after 5 rounds |
| An enemy AH-6 on a tower 90 blocks east | destroyed (72 → 0) |
| A player's AH-6 on a tower 60 blocks north; the player beside it | untouched |

Found by the test: an enemy rocket was never engaged, because its launcher (a crewman 150 blocks from any player)
despawned and the rocket couldn't be told apart from a player's; missiles now record at launch whether the enemy fired
them. And the rounds burst on the AH-6 but did nothing: an unlisted gun pierces almost no armour; the CIWS has its own
armour piercing (7.5).

## Finalisation (2026-11-18)

- **The search radar's drive is heard** while the antenna sweeps (manned or on automatic): the servo recording
  (`VehicleFeel` "servo", quieter and lower than the turret's slew). `Hpj11ClientGameTest` checks it starts on
  automatic (passes with `-PwithPack`).
- **The gun from real recordings, heard far off** (`CiwsAudio`, `prepare_audio.py`): a US Navy Mk 15 Phalanx's burst
  for the firing loop up close and its ring-out when it stops; a C-RAM (the land Phalanx) recorded at a distance for
  the far loop and its echo; a real M134's electric drive for the barrels spooling up and running down. Near and far
  are mixed on the aircraft distance curve, so the gun carries to 1,024 blocks; before, SBW's loop faded out by 56
  blocks and cut off dead.
- **Log spam fixed:** "Unable to find animation: 'idle'" every frame for each HPJ-11 in view (see the sentry).
- **Destroyed:** cooks off, goes up, throws out its hold, burns and breaks apart (`GroundWreckClientGameTest`).
- `"mass"` 9 t (the FSV-6 feels it as tow load).

| Test (`-PwithPack`) | Result |
| --- | --- |
| `Hpj11ClientGameTest` | passes: rocket shot down 123 blocks out after 5 rounds, the gun heard as spin_up, fire, fire_distant, spin_down, fire_stop; enemy AH-6 72 -> 0, the player's untouched; the radar drive heard on automatic |
| `GroundWreckClientGameTest` (HPJ-11 added) | passes: cooks off then burns, did not move or spin, 16 diamonds thrown out, broke apart |

Played (mcpfabric, pack client): loaded its hold, held the trigger from the seat: the barrels spun up and it fired
(51 of 64 shells left mid-burst), the log clean of sound errors.

## Not covered yet

- The Mi-28's guided missiles (Kh-39, 9M120) shot down; several at once; an enemy Mi-28 in flight.
- The per-chunk cap and the server switch played; a claim mod.
- **Listen:** `vehicle/hpj11/fire` (Phalanx), `fire_stop`, `fire_distant` and `fire_stop_distant` (C-RAM),
  `spin_up` and `spin_down` (M134), the radar drive hum; and the far sound from 300+ blocks.
- The dedicated server, multiplayer.

## Texture pass (2026-11-19, the user's review R7)

`design/ground-vehicles/detail_texture.py` re-weathers the texture from the model: it is enlarged (the original paint
kept texel for texel) and each texel is shaded by where it sits on the hull: panel seams and worn edges, bolt heads
round big panels, mottled paint, grime run down from top edges, dust on top faces, mud low down. The untouched texture
is `texture_base.png` here (re-run the script to tune; it never stacks), the before/after `review-texture.png`. Seen in
the pack (Photon) by day from close; not yet looked at under every paint colour.
