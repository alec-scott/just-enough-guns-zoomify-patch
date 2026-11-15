# FSV-6 implementation status

2026-11-15. All runs below used `-PwithPack` (Minecraft 26.1.2, loader 0.20.1, the pack's client mods and shaders
subset in `build.gradle`) in a singleplayer world.

## Fourth review (2026-11-15)

| Feedback | Change | Checked by |
| --- | --- | --- |
| Wheels substantially bigger | Tyres 16 px tall (were 13), 10 px wide in front and 9 px on the tandem; cab sills cut round the front wheels, fenders over the tandem. One block is the most under a floor one block up: bigger needs the body raised | side and three-quarter screenshots, `SupportHitbox` |
| Remove the sorting buttons | The support console and beacon terminal (screens and menus) are `@IPNIgnore`: Inventory Profiles Next draws no sort buttons on them | console screenshot in the pack |
| Equipment keeps its sounds | Chests, barrels, ender chests and shulker boxes open and close with their sounds; burning furnaces, smokers and blast furnaces crackle; a finished brew chimes; fitting and taking out a module sounds as placing and breaking it | compiled; not asserted by a test |
| Console overhaul like the dashboard | The partition's two panels are live: the console's screen (power, battery, hold, light) and three dials; the beacon terminal's screen (effect icon, level pips, beam, rack) and four level lamps. The screens and lamps sit on plain dark faces (a painted dial showed through the live display) | `SupportLook`: wall panels screenshot |

## Third review (2026-11-15)

| Feedback | Cause and change | Checked by |
| --- | --- | --- |
| Chest texture disappeared | In 26.1 a chest's block model is empty (the block entity draws it): bays drew nothing. Chests, trapped and ender chests and shulker boxes are now drawn through `BlockModelResolver`, as an enderman's held block | `SupportLook`: chest, trapped chest and ender chest in the bays (screenshot), also on the bare client |
| Bed shows through the wall | The bunk's real bed reached the room's edge, on the slide-out end wall's plane. The end walls now stand 0.4 px proud | `SupportLook`: the slide-out's front end from outside (screenshot) |
| Beacon noise on/off | Activate and deactivate when it lights or goes out, the ambient hum every 4 s, the select click on choosing an effect | `SupportLook`: activate heard on deploying, deactivate on packing up |
| Outside lockers | They were there (under the body, ahead of the middle wheels) but olive like the rest, and an empty one did nothing. Yellow grab rail; looking at one says what is in it or how to fit it; clicking an empty one says so | side screenshot; prompt code unasserted |
| Console UI | Rebuilt as equipment (`ConsoleStyle`): riveted steel case, a fit-out floor plan with each slot where the fitting is and ghost pictures in empty wells, a CRT with glow, scan band and segmented gauges. The beacon terminal shares it | `SupportInterior` (all clicks), screenshots |
| Driver / co-driver dashboard | Not built before. A binnacle over the wheel with live tachometer, speedometer and eight warning lamps; a green status screen for the co-driver. The vehicle HUD moves to the bottom right in the FSV-6's cab | `SupportLook`: driver by day, driving, at night; co-driver (screenshots) |
| Wheels too skinny | 9 px steer tyres, 8 px on the tandem (were 6); arches, guards and flaps widened; the cab step moved ahead of the front wheel | side and three-quarter screenshots; `SupportHitbox` |
| Can't tow; want an indicator | Towing needed a sneak-click on the low bumper and nothing said so. In the pack, Carry On also took the sneak-click and lifted the vehicle. Now: driver key N with a panel prompt and dash lamp; on foot, an action-bar prompt and sneak + use anywhere on the back; every JEG vehicle on Carry On's entity blacklist | `SupportTow`, rewritten with real keys and clicks: N hitches, follows, N releases; on foot sneak + use hitches and releases |

Final run, all `-PwithPack`: `SupportLook`, `SupportTow`, `SupportInterior`, `SupportModules`, `SupportDeploy`,
`SupportHitbox`, `VehicleInputCompat` pass, and `SupportSandboxSetup` rebuilt the `SupportTest` world. Not covered: a
shader pack switched off (that pack run crashes on start), the dedicated server and multiplayer.

Also: the white square on the beacon terminal panel was removed, and the GeckoLib duplicate-locator errors fixed
(unique locator names).

## Second review (2026-11-15)

| Feedback | Change | Checked by |
| --- | --- | --- |
| A car start inside the driving loop | Loop re-cut clear of the take's second start | audio-validation.json (listen to confirm) |
| F5 camera too close | `third_person_camera.z` 9 → 11.5 blocks | screenshot |
| F5 camera stutters in hard turns | The rider's view turns with the vehicle's drawn yaw (shared `VehicleInputHandler.clampSeatView`, all land vehicles) | `VehicleInputCompat`; not measured frame by frame |
| One lamp slot, the only light inside | One lamp slot; no built-in light or default lanterns; one hook mid-room | `SupportInterior`: room light 0 with no lamp, 15 with a lantern; night screenshot |
| Beacon's separate, unreachable inventory | The beacon terminal (`SupportBeaconScreen`): 9-slot power rack (raw ore, ingots, gems, blocks) and effect keys; no vanilla payment slot. An effect above the rack's level gives nothing | `SupportInterior`: 3 × 64 raw iron clicked into the rack → level 1; Strength refused, Speed chosen and lit, all by mouse |
| Beacon UI between cab and room | Its own panel on the partition wall, left of the aisle; the console is on the right | `SupportInterior`: each panel clicked with the use key |
| Generator another −40% | ×0.6 again (×0.36 of the original) | audio levels (listen to confirm) |
| Seat screens: hold + fit-out only | The hold screen has one labelled Fit-out button; module screens open only by clicking the module in the room. The console's Hold key is its only key | `SupportInterior`: Hold key opens the hold, module icons open nothing; hold screenshot |
| Towing | Unchanged: sneak-click the rear pintle to hitch the nearest empty vehicle within 7 blocks; sneak-click again to drop it | `SupportTow` (first round) |
| Walking about in the back while driving | Investigated, not built: see the guide, section 6 | — |

Runs, all with `-PwithPack`: `SupportDeploy`, `SupportModules`, `SupportInterior`, `VehicleInputCompat` and
`SupportSandboxSetup` pass. Then `SupportInterior` and `SupportModules` again after the screen layout fixes, and
`SupportInterior` with the sandbox rebuild last.

## Verified by game tests (first round)

| Test | What it checks | Result |
| --- | --- | --- |
| `SupportDeployClientGameTest` | Hold R from the driver's seat, parked 7° off south: it squares up to yaw 0, its room corner lands on whole blocks, the body stands one block above the ground and all 116 shell blocks are placed. The client's pose equals the server's. The generator is audible from outside and the diesel is silent. A player walks up the ramp into the room and stands on its floor. Refusals: 30° off a cardinal (HEADING), a stone where a slide-out goes (BLOCKED, with the cell), packing up with someone inside (PLAYERS_INSIDE). Packing up from the driver's seat removes every shell block and sets it back on its wheels. A deployed vehicle removed outright takes its shell with it. | pass |
| `SupportModulesClientGameTest` | A furnace smelts 2 iron ingots while driving 149 blocks. Fittings and contents survive the vehicle-container round trip (crowbar). Deployed: both bunks become real beds (4 cells), the room is lit to 15 by a fitted sea lantern, and 13 camp lamps are placed. Each bay opens its vanilla screen (crafting, furnace, chest, brewing, anvil; the charging station reports instead). A drained and damaged LAV-150 beside it is recharged, repaired to full and rearmed with 34 rounds from the hold. The roof beacon gives Speed. A survival player cannot break a bunk's bed. Sleeping there sets the spawn, and after packing up and moving the vehicle, a death respawns the player beside it. | pass |
| `SupportTowClientGameTest` | Sneak-clicking the pintle hitches the LAV-150 behind. Driving forward and round a turn, it follows 106 blocks, 2.7 blocks from the pintle. Sneak-clicking again releases it. | pass |
| `SupportHitboxClientGameTest` | The packed boxes match the generator's and lie within the model. Real rifle rounds at the cab, both van sides, a front wheel and the rear door hit; rounds at the air above the cab and beside the body miss. | pass |
| `SupportInteriorClientGameTest` | Played as a player with real keys, clicks and the mouse. At full lock the turning radius is 10.3 blocks. Pressing S while rolling sets the brake lights. Deployed, right-clicks aimed by the player's view open the crafting table, furnace and chest in the bays and the support panel. Beds are refitted while deployed (blue in, red out, 0 items dropped), and clicking the bunk at night sleeps. A rack of 192 iron ingots gives beacon level 1. On the support console, the Beacon key opens the beacon and the bay 0 module icon opens the crafting table, both clicked with the mouse. Screenshots: brake lights, room at night, windows from both sides, the console. | pass |
| `SupportSandboxSetupClientGameTest` | Builds and saves the `SupportTest` world (see below). | pass |
| Regression | `VehicleInputCompat`, `VehiclePaint`, `VehicleSneakDismount`, `VehicleAssemblingScreen`, `AircraftNightLights` and `BulldozerHitbox` all pass. These cover the shared code changed: input, the OBB accessors, data parsing, the HUD, the hold screen and lights. | pass |

Screenshots were looked at:
- deploying and deployed, outside, from above and from the side;
- the room from the doorway, beside the bays, and at night;
- the fitted room, the bays with modules, and the support console (status display and keys clear of the pack's
  Inventory Profiles Next sort button);
- the hold screen's module buttons;
- the beacon beam and lit camp at night;
- towing, and the sandbox.

## Play it

```powershell
./gradlew runPackClient -PwithPack -Pminecraft_version=26.1.2 -Pfabric_version=0.155.3+26.1.2 -Ploader_version=0.20.1 --args="--quickPlaySingleplayer SupportTest"
```

The `SupportTest` world is survival with cheats and no mobs, with the day running so the bunks can be tried at night.
- **The FSV-6:** at the spawn, fitted with a crafting table, furnace, chest, a red bunk bed and a lantern (its one lamp). Its hold
  has the rest to fit: a beacon, 192 iron blocks, a charging station, an anvil, beds, lamps, a smoker, a brewing
  stand, an ender chest, a jukebox and a disc, plus fuel, ore, iron and ammunition.
- **To service:** a drained LAV-150 and a damaged BMP-2.
- **To try deploying against:** a clear spot ahead, a hill at x +30 (too uneven) and stone pillars at x -30
  (blocked).

## Not covered

- **Listening:** nobody on the build side can hear the game. The audio checks are of loops, channels and volumes. The
  diesel is a lowered 4×4 take; whether it reads as a five-tonne truck needs your ears.
- **The dedicated server and multiplayer:**
  - Not covered at all: the dedicated server's own mods (Sable and the other server-side mods), and two players
    using one vehicle.
  - Partly covered: the shell's registry and the move-vehicle authority fix ran in the integrated server only.
- **Undead Nights' horde-night rule:** the bunks are vanilla beds, so it should refuse sleep on those nights as for
  any bed, but no horde night was run.
- **Shader packs:** the room and lights were checked without one active.
- **The tow bar:** it is not drawn.
- **Explosions on deployed beds:** the rule that uses up a bed destroyed while deployed is coded, but untested.
- **Chunk cleanup:** clearing orphaned shell blocks in chunks that were unloaded when the vehicle was destroyed is
  coded (random tick plus registry), but untested.

## Before release

- Bump `yftm_build` only after you've listened to it and played the sandbox.
- The server needs the pack-manager update, as for every jegn release.
