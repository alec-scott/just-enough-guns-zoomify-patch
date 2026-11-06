# yftm.7 verification

The update continues the first-person gun handling, minigun effects, and helicopter flight work from yftm.6. It also scopes arm visibility to each rendered bone, separates the Power damage comparison from Punch/Flame effects, adds minigun assertions, and introduces collective-key helicopter ignition with recorded startup/flight audio and Doppler pitch.

## Reproduce

With JDK 25 selected through `JAVA_HOME`, run:

```powershell
.\gradlew.bat runClientGameTest build --offline --console=plain
```

Omit `--offline` if dependencies or Minecraft assets have not been cached. The fourteen registered client tests create their own worlds. Screenshots are written to `build/run/clientGameTest/screenshots/`.

## Coverage

- Assertions: cold helicopter entry and W do not start the engine, eight/twelve-second rotor wind-up stays grounded, server/client takeoff agrees, startup audio switches to flight audio, an empty battery prevents ignition, Doppler raises approach pitch and lowers receding pitch while preserving co-moving/transverse pitch, Power V damage, Punch knockback, Flame ignition, Infinity ammo preservation, Infinity/Mending compatibility, minigun spin-up/firing/spin-down, legacy vehicle input decoding, and station drops.
- Recorded scenarios: AH-6 and MI-28 acceleration, coasting, braking, strafing, reversing, climbing, barrel rolls, camera motion, pilot-seat changes, gun/rocket/missile aiming, foliage damage, weapon-weight movement, and hit feedback.
- Visual checks: first-person weapon grips, sprint carry, gun swaps, daytime/nighttime minigun effects, and missile flight.
- Audio assertions check both routing and actual active sound-engine channels while firing, then confirm the loops stop after trigger release. MI-28 guns use the minigun loop; AH-6 guns use the quieter A-10-derived loop. Helicopter engine assets decode as mono Ogg files for positional playback.
- Impact assertions fire each rocket/missile into a block, measure close splash damage, and check a target beyond its previous blast radius.

The client tests use an integrated server. Separate dedicated-server startup and real multiplayer latency/audio are outside this verification.

## Missile costs

Medium ground and air missiles use a redstone torch for guidance. The large ground missile uses a redstone block and still requires two TNT and two missile engines. Output counts stay at one missile; the small rocket recipe still produces four. None of the three changed recipes or their rocket/engine ingredients requires Ender Pearls or Blaze Rods.

Recording sources, licenses, and edit ranges are packaged in `assets/jeg/sounds/vehicle/HELICOPTER_CREDITS.md`.

## Audio edits

- AH-6: 8-second ignition; 46-second flight loop cut from 1:12–2:00 with a 2-second equal-power overlap. Runtime RPM and Doppler pitch keep it responsive to motion without restarting a short engine sample.
- MI-28: 12-second ignition; 9.25-second seamless flight loop made from the requested 1:26–1:36 section with a 0.75-second overlap.
- AH-6 gun: the existing A-10 recording, crossfaded into continuous fire at 80% amplitude. MI-28 retains minigun fire and its firing-stop tail.

## Rocket balance

| Projectile | Direct damage | Blast damage | Blast radius |
| --- | --- | --- | --- |
| 70 mm rocket | 110 | 60, falling off with distance | 7 blocks |
| 80 mm rocket | 125 | 75, falling off with distance | 9 blocks |
| 9M120 ground missile, either seat | — | 240 vehicle / 90 living | 10 blocks |
| KH-39 large ground missile | — | 600 vehicle / 110 living | 24 blocks |

The MI-28 gun's flash uses the existing texture at 20% size and 50% opacity for one tick.

## Results — 2026-11-06

- The inherited twelve-test client suite passed after the hand-rendering and enchantment-test fixes.
- Focused startup/flight checks passed for both helicopters, including empty-battery behavior, server/client takeoff agreement, startup-to-flight audio, and Doppler direction.
- The final blast/playback run passed all five projectile impact scenarios. Close splash damage measured 41.61 for 70 mm and 56.85 for 80 mm against test husks; probes at 6.8, 8.6, 9.6, and 23 blocks all took damage beyond the previous blast radii.
- AH-6 first-person/chase and MI-28 gunner checks each reported `soundChannelActive=true` while firing and `stoppedAfterRelease=true` afterward. This checks nonzero-volume sound-engine playback, not just sound-event selection.
- Inspected the reduced MI-28 flash in a first-person firing screenshot. Validated all five new Ogg assets, including the 46-second AH-6 flight bed and measured AH-6/A-10 gun RMS ratio of 0.801.
- `build` passed. Verified jar version, packaged audio bytes, the smaller-flash particle asset, runtime audio classes, and missile recipes. The full fourteen-test manifest is restored for future runs.

The final artifact is `build/libs/jegn-1.7.2+mc26.1.2.yftm.7.jar`.
