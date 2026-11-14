# Developing a new vehicle

How to add a vehicle to JEG so it is realistic, consistent with the other vehicles, and works in the yftm pack.

**Reference vehicles:**
- **The A-10 and the helicopters (AH-6, Mi-28) are the standard to match.** Section 1 lists what to copy from them.
- **The BD-40 bulldozer (`design/bulldozer/`)** is the worked example of the process: brief, generator, rig, tests,
  sandbox, fitted hitbox and aircraft-style sound.

Start each vehicle by copying [vehicle-brief-template.md](vehicle-brief-template.md) to
`design/<vehicle>/BRIEF.md` and filling it in. The brief is the contract; this guide is how to meet it.

**Keep this guide current.** Whenever a fix on one vehicle would apply to others (a trap in the shared code, a better
pattern, a test technique), add it here in the same change. The sections below collect those lessons.

## What "done" means

A vehicle is done when all of these are true and shown by a game test run with `-PwithPack`:

1. **It looks like the real machine.** It has real-world proportions and parts, and every visible part is either
   functional or documented as cosmetic. If a player will ask "what does this do?", the answer must exist.
2. **Its hitbox fits it.** The collision and hit boxes follow the model's silhouette: nothing visible is
   unhittable, and nothing empty is hittable or collides.
3. **It drives like the other vehicles.** It uses the same keys, sensitivity and physics family as its peers (land
   vehicles feel like the truck, helicopters like the AH-6). It does not get its own movement code unless the
   brief says why.
4. **The operator is right.** They are visible when the real seat is visible, they sit on the seat, and they can
   look around as far as a real occupant could.
5. **Its purpose works end to end in the pack.** Check the pack's mods for any that touch the vehicle's job
   (FallingTree for clearing, claim mods for block breaking) and make the vehicle cooperate with them.
6. **It is heard the way the aircraft are heard:**
   - in every camera view, near and far
   - fading on the shared curve out to 1024 blocks, beyond entity range through server tracks
   - delayed by the speed of sound and with Doppler
   - its weapons carrying across the map
7. **Its weapons, if any, reach their full range.** Rounds and missiles keep flying through unloaded terrain, and
   distant players see the tracers and impacts.
8. **It looks right at night from every seat**, not just from outside.
9. **There is a sandbox world** the user can open and play the vehicle in immediately.

## 1. Learn from the A-10 and the helicopters

Read these before designing anything; reuse them rather than writing parallel systems.

| What they do well | Where | Take away |
| --- | --- | --- |
| One distance curve for every aircraft sound, out to 1024 blocks. Louder sources carry further; there is no Minecraft linear cut-off | `AircraftSoundRange.volume(distance, loudness)` | Every engine, gun and work sound uses it, with a loudness per source (A-10 engines 2.5 flat out, helicopter guns 2.5) |
| The full recording crossfades into a muffled `_distant` recording with ground echo between 40 and 240 blocks | `nearShare`/`distantShare`; `vehicle.*.engine_distant`, `vehicle.a10.fire_distant` | Record or derive a `_distant` version of each engine and gun |
| Heard beyond entity range (192 blocks on yftm): the server sends a track every 4 ticks to players 96–1024 blocks away, and the client plays the distant engine and gun from it | `VehicleEntity.sendFarAircraftSound`, `AircraftTrackPayload`, `FarAircraftAudio` | Extend the tracks to the new vehicle; the hand-over at entity range must not jump in volume |
| Sound arrives late, about a tick per 17 blocks, from where the vehicle was | `FarAircraftAudio`, `playDelayed` in the audio classes | Delay one-shots and far sound by distance |
| Doppler on approach and departure | `VehicleDoppler` | Pass every outside loop through it |
| A muffled cockpit version in first person, the real exterior sound outside | `JetAudio` (`jetCockpitSound`), `HelicopterEngineAudio` | Give enclosed cabs their own interior recordings; an open seat uses the exterior sound, centred |
| A gun heard like the aircraft carrying it: relative from inside, positional with the aircraft curve outside | `VehicleFireSoundInstance` (`HELICOPTER_GUN_LOUDNESS`) | Never give a weapon Minecraft's linear attenuation |
| Rounds and missiles keep flying wherever they are, load terrain ahead, are never saved with a chunk, and send tracers out to 1024 blocks | "FarFlight" missiles and rockets; long-range gun rounds (yftm.13, yftm.18) | Every projectile the vehicle fires uses the same path |
| Distant blasts appear on Voxy terrain soon after | `DistantTerrainRefresh` (yftm.15) | Weapons that change terrain far off must show it |
| Collision fitted part by part: the A-10 has fuselage, wings, tail and engines; the Mi-28 has 11 parts; named parts carry armour and take part damage | `a10.json`, `mi28.json` `obb` + `armor.parts`; `estimateHitPart` | See section 3 |
| Start-up as a sequence: latched ignition, rotor spin-up, no early lift, hot restart | `HelicopterStartupClientGameTest` | Engines start and stop as real ones do |
| Damage you can see: smoke, sparks, fire, a wreck that falls, burns and breaks apart | `HeliWreckClientGameTest` | Plan the vehicle's damaged and wrecked states |
| Handling held to a real envelope | `A10TaxiClientGameTest`, `A10Flight`, `HeliClimbDescent` | Test handling limits, not just "it moves" |
| A test for each claim about sound and range | `AircraftFarSound`, `A10GroundObserver`, `AircraftLongRange`, `RocketLongRange`, `MissileRealTerrain` | Copy their pattern (section 8) |

## 2. Research before building

- **Real machine.** Pick a real class (for example "mid-size crawler dozer like a D6") and note its proportions,
  layout, top speed, controls, the operator's sightlines, and which parts move and why. Use those numbers; adjust
  for gameplay only where the brief says so.
- **The pack.** Run `overview` and `list_mods` on the `yftm` MCP server, and read the configs of any mod the
  vehicle's job touches (`search` in `mc/config`). Record in the brief each mod that interacts and how. Add those
  mods to `packMods` in `build.gradle`, and copy the server's config into
  `src/gametest/resources/pack-config/`, so tests run against what players have. Note the server's view distance
  (192 blocks), which sets where far-sound tracks take over.
- **This codebase.** Read the vehicle JSON of the nearest existing vehicle and the `VehicleEntity` paths it uses.
  Reuse shared systems before writing new ones: steered land movement, seats, cameras, HUD, `AircraftLights`,
  `AircraftSoundRange`, far tracks, energy, cargo, paint. If a hook is missing, add a small protected one to the
  base class, as the bulldozer did with `tickSpecialLandMovement`.

## 3. Hitbox: fit it to the vehicle

The `obb` boxes in the vehicle JSON are the vehicle's body for everything:
- **Blocks:** they stop it against terrain.
- **Entities:** they push mobs and ram things.
- **Weapons:** `estimateHitPart` picks the part a shot struck, and that part's armour.
- **Entity box:** for land vehicles, the entity's own box (used for picking and coarse hits) is the rotated
  envelope of all of them.

So a box that is too big makes the vehicle easier to hit than it looks, snags on trees and walls it visibly
clears, and grows the entity box at every angle that isn't square on.

**How the code actually uses the boxes** (found on the bulldozer):

- **Blocks.** A land vehicle's block collision is one rotated box enclosing all the parts (`blockCollisionBounds`),
  not the parts themselves. The envelope is what snags, so no single part may stick out: one wide blade makes the
  whole machine that wide against blocks.
- **Bullets.** Bullets reach vehicles by two paths in `BulletEntity`: `findClosestEntityHit` and vanilla
  `ProjectileUtil.getHitResultOnMoveVector`. Both test the entity's whole box, grown by 0.3 blocks, unless the
  vehicle returns `preciseHitbox() == true`. Then both test the fitted parts (`VehicleEntity.clipHitbox`). Turn it
  on once the parts are fitted.
- **Entities.** Entity pushing uses the parts only for vehicles listed in `usesCustomObbEntityCollision`.
- **Damage.** The part a shot damages is the part containing the projectile (`estimateHitPart`).

**Rules:**
- **Hug the model.** Build the boxes from the model's real masses: hull, cab, wings, tail boom, tool. Each box should
  sit inside the visible part, or within a pixel or two of it; no box sticks out into empty space. Several smaller
  boxes beat one big one. The A-10 uses separate boxes for the fuselage, each wing, the tail and each engine.
- **Generate the boxes from the model; don't type them.** The bulldozer's `generate.py` computes each box from the
  cubes of named parts and writes them into the vehicle JSON and `rig.json`, so the hitbox can never drift from the
  model. Clamp box bottoms to ground level, because track grousers below zero would lift the vehicle.
- **Match the work lane.** A tool that cuts a lane (a 4-block blade) must be no wider than the lane, or the machine
  cannot follow its own cut.
- **Cover everything solid.** Every large visible part is inside some box: a blade, a boom, a gun pod. If a part
  moves (a lifting blade, a turret), either give it its own named part, or document why it is left out and handle
  its contacts in code. The bulldozer does the latter for its blade.
- **Name the parts that matter** (`main_engine`, `sub_engine`, `turret`, `wheel_left`/`wheel_right`), and give
  them `armor.parts` entries so hits there damage the right system.
- **Long thin parts.** Where parts like a tail boom or stub wings would make the rotated envelope much bigger than
  the hull, keep the entity box at the hull size, as helicopters do (`usesHullBlockCollisionBox`). Then size
  `ModEntities` `sized(width, height)` to the hull.
- **Clearances.** Keep the top under 3 blocks if it should pass under tree canopies. Keep the bottom at ground level
  so steps work with `up_step`.
- **Verify with pictures and a test, not by eye in JSON:**
  - Screenshot the vehicle with hitboxes shown (F3+B, or render the OBBs) from the front, the side and above, at
    yaw 0° and 45°. Check every box against the model.
  - Assert in the test that every OBB part lies within the model's bounds plus a small margin. Read the bounds from
    the generator's `rig.json`.
  - Shoot it with a real gun, the `HitMarker` way: rounds aimed at each part hit, and rounds aimed into the empty
    space round the hull (inside the old whole-vehicle box) miss. Shoot from more than 5 blocks away: closer, a
    creative player's left click is a melee hit on the whole box, and every shot "hits"
    (`BulldozerHitboxClientGameTest`).
  - Drive it past a wall and under a canopy that the model clears, and assert it isn't stopped.

## 4. Vehicle data (`data/jeg/vehicles/<id>.json`)

- **Engine.** Start from the peer vehicle's `engine` block and change only speeds and acceleration. The steered
  land model reaches about `0.83 × max_forward_speed` in blocks per tick; check the top speed in a test.
- **Seats.** Seat `y` is where the rider's feet would be, and the hips sit 12 px above it. For a sitting rider, put
  the cushion top at `seat_y×16 + 10` px. The eye is at `seat_y×16 + 26` px; make sure the roof clears the head
  (`seat_y×16 + 29`).
- **Look range.** Set `min_yaw`/`max_yaw` to what the real seat allows (±180 for an open seat). Never declare
  `"turret": {"seat": N}` on an unarmed vehicle: it pins that seat's first-person camera to the turret barrel, and
  the occupant cannot look around.
- **`hide_passenger`.** True only for enclosed or armoured crews. `enclosed` controls damage protection, not
  visibility; set it to what the real seat offers.
- **Other entries.** Give it no `weapons`, `seek` or decoys unless it is armed.

## 5. Model

Author the model with a generator script (`design/<vehicle>/generate.py`, copied from the bulldozer's), not by
hand-editing JSON. The script keeps the geometry parametric, validated and reproducible. It writes:

- the GeckoLib geometry, two LOD models, the texture and glow atlas, and the paint mask
- `rig.json`, the Blockbench project and an offline preview

**Conventions:**
- 16 units per block; the vehicle faces −Z; +X is the operator's left.
- Author rotations in ordinary right-handed form; `native_geometry` converts them for GeckoLib.
- Avoid Y rotations on cubes. Step geometry instead (the bulldozer's semi-U blade wings), because the Y conversion
  is untested.

**Write the model's bounds and part extents into `rig.json`**, so the hitbox test can check the OBBs against them.

**Moving parts:**
- Give each moving part its own bone, pivoted at its real hinge.
- Drive every articulation from synced state in the renderer; never from canned animations.
- A hydraulic cylinder is a barrel bone, rotated toward its target, plus a rod bone scaled along Y. Validate that
  both ends stay attached and the rod stays within its stroke across the whole range.
- Anything that rotates (wheels, sprockets, fans, rotors) needs visible asymmetry or blur, or the rotation is
  invisible (the A-10's fans blur at full power).
- Roll wheels at their real rolling radius.

**Gameplay geometry:**
- Keep the constants that gameplay depends on (pivots, cutting edges, muzzles, seat, exhaust) in one Java
  `<Vehicle>Rig` class that mirrors `rig.json`.
- Test that the drawn part sits where the server thinks it is (`BulldozerRenderer.captureRig`; the A-10's gun bore
  and aim marker).

**Sightlines:**
- Check from the eye point what the operator needs to see before building.
- Aim the parts the operator uses from the eye point: compute a mirror normal as the bisector of its directions
  to the seat and the rear scene, and keep the glass's sightline clear of the canopy posts. Give glass its own
  finish instead of piston chrome. Check every glass corner's sightline, then take a first-person screenshot:
  a clear ray to the centre can still leave the edges behind the post. Put the arm behind the housing/glass.
- Keep tall fittings off the centre of the forward view.
- Keep the headrest and screens below and behind the eye.
- Confirm with first-person screenshots.

**Materials and paint:**
- Real colours, a little weathering, and a separate material per finish: paint, bare steel, rubber, glass, lamps.
- The paint mask works per texture pixel. Use `separate_finishes` and the derived `unpainted_parts` so no
  unpainted bone samples a paint tile.

**Damage states.** Plan them as the helicopters do: smoke, sparks and fire as health falls; a wreck that behaves
physically; parts that stop working when hit.

## 6. Behaviour

- **Server first.** World changes happen on the server only. Respect the world border, spawn protection and
  adventure mode, and call `PlayerBlockBreakEvents.BEFORE`/`AFTER` for every block, so claim mods apply.
- **Present the vehicle's work to other mods as the matching player action.** For example, the bulldozer holds an
  axe while breaking logs so FallingTree fells the tree. Restore the player's real hand afterwards, and keep a safe
  fallback for when the mod is absent.
- **Budget the work per tick** (per vehicle and per level), and let unfinished work slow the vehicle smoothly:
  ease a speed limit down as the load rises and back as it clears. Don't zero the speed while work is pending; that
  reads as stop-start. Stop dead only against something the tool cannot work.
- **Work that spans ticks must give the same result as work done in one.** Remember what the vehicle changed
  recently (the bulldozer keeps its cut positions for 5 s) so its own fresh holes are not mistaken for the world's.
- **Never leave anything that would block the vehicle following its own work.** The bulldozer always clears its
  full height in front of it, whatever the bank rules say above that.
- **World Y can be negative** (down to -64). Never use -1 or `< 0` as "not found" for a height: use
  `Integer.MIN_VALUE`. Test on the superflat world at Y -60, which catches it.
- **Client prediction.** Anything that changes movement (load, blocking) must be synced and computed the same way
  on both sides.
- **Real controls and start-up.** A hydrostatic machine stops when the lever is released; a truck coasts. Engines
  crank and catch: no instant power, and no power before the start-up sound says so.
- **Shift is free for controls.** Riding a JEG vehicle, Shift neither ejects (`wantsToStopRiding`) nor crouches
  (`getDesiredPose`, `PlayerVehicleSneakDismountMixin`); crouching sank the first-person camera into the seat.
  Keys that are unused on an unarmed vehicle (R, left mouse button) can carry its tools.
- **Limits that protect the world.** Give hardness caps, deny tags, block entities, and grade or floor locks clear
  rules, and show them in the HUD.

### Hold and tool bays

- **Mark slots that feed a tool.** If part of the hold feeds a tool (the bulldozer's ripper bay is the last row),
  return it from `toolSlotStart()`/`toolSlotCount()`. The menu then makes those slots `VehicleMenu.ToolSlot`, which
  takes only what the tool can use. `VehicleScreen` frames them in amber, labels them, shows ghost items when empty
  and explains them on hover.
- **Size a bay for real use.** One stack is not enough for a tool that consumes material; the bulldozer's bay is a
  row of 9.
- **Keep automatic inserts out of tool bays.** Spoil and loot pickup skip them, so the operator's material is
  never displaced.

### Weapons

- **Projectiles.** Fire through the existing long-range paths, where projectiles tick wherever they are, load
  terrain ahead, are never saved with a chunk and end at their range. Send tracers to players up to 1024 blocks
  away.
- **Distant impacts** must appear for distant players (`DistantTerrainRefresh`).
- **Feel.** Give each weapon recoil, tracers, impact debris and reload sounds, and heat or cooling if real.
- **Aim.** The aim marker sits on the real bore, and the bore is tested against the model.
- **Test every weapon at range,** not only the one changed. yftm.13 fixed missiles but not helicopter rockets.

## 7. Sound

**Sources.**
- Use field recordings of the real machine type, licensed CC0, credited in `sounds/vehicle/<id>/CREDITS.md`.
- Cut idle and working loops from one take where possible, so the crossfade stays one voice.
- For each engine and each gun, prepare a muffled `_distant` version with ground echo, as the aircraft have.
- For enclosed cabs, prepare an interior recording; it is the first-person sound.

**Pipeline.**
- Use a script (`prepare_audio.py`): band-limit, cut, crossfade loop seams, and measure peaks, levels and seams
  into `audio-validation.json`.
- Write Vorbis in blocks, because libsndfile overflows the stack on long single writes on Windows.

**How it travels.** Make it match the aircraft:
- **Distance.** All outside sound uses `AircraftSoundRange.volume(distance, loudness)`, with no attenuation of
  Minecraft's own (`Attenuation.NONE`). Pick each source's loudness against the existing ones: helicopter engines,
  A-10 engines 2.5 flat out, guns 2.5. A loud diesel is well under a jet.
- **Near to distant.** The full recording crossfades into the `_distant` one with `nearShare`/`distantShare`.
- **Beyond entity range.** Override `sendsFarSoundTracks`, `farSoundLoudness`, `farSoundPitch` and `farSoundSignal`
  in the vehicle; aircraft do this by default. The server's `AircraftTrackPayload` and the client's
  `FarAircraftAudio` then keep the vehicle audible out to 1024 blocks. They play its `engine_distant` sound, and its
  `fire_distant` (or `horn_distant`) while the signal is on. There must be no jump in volume at the hand-over
  (`BulldozerFarSoundClientGameTest`).
- **Arrival and pitch.** Delay sound by distance (about a tick per 17 blocks) and apply `VehicleDoppler` outside.
- **Weapons.** A gun uses the vehicle's curve with its own loudness (as `VehicleFireSoundInstance` does), never
  `Attenuation.LINEAR`. Far tracks carry the firing state so the distant gun is heard too.
- **Pick a source of the right class and measure it.** The bulldozer's first horn came from a car horn and read as
  one even when pitched down. An industrial air horn with a lifted fundamental did not.
  - Check the spectral centroid and the share of energy below 500 Hz against what the sound should be: the
    centroid went from about 1,400 Hz to 650 Hz, and the low share from 15% to 58%.

**Layers and views.**
- Engine idle and load are crossfaded by an RPM model, with pitch following RPM.
- Mechanical layers scale with their cause (tracks with speed, tools with load), with one-shots for start, stop,
  impacts and work events.
- First person in an open seat: the exterior sound, centred. In a closed cab: the interior recording. Outside:
  positional.
- **Never restart loops on a view change.** It is audible as the sound resetting. Centred first-person sound must
  use listener-relative coordinates at `(0, 0, 0)`. A world source placed on the camera once per tick drifts behind
  the moving listener between frames and pans rapidly when the driver looks sideways.
  - Vanilla only sets the channel's relative flag at playback start. Opt into `DynamicRelativeSound` to have
    `VehicleSoundEngineMixin` update it alongside the position in the same sound-thread task. Change the instance's
    relative flag and coordinates together each tick, as `BulldozerAudio.Layer.follow` does.
  - Use world coordinates outside. Keep the same channels and the same mix while aboard in both views.

**Loops must be static (`"stream": false`).** Minecraft has about 8 streaming channels, shared with music. Seven
streamed loops starved the pool, and restarted loops got no channel: silence. This is what made the bulldozer
silent after a camera switch.

**Measure what is actually heard.**
- Check that the same loop instances survive a view switch (`BulldozerAudioClientGameTest`).
- Read each loop's OpenAL source (playing, gain, positioning mode) in each view, as `BulldozerAudioClientGameTest` does.
- Query OpenAL through `ChannelHandle.execute` on the sound thread. Test while driving and looking both ways:
  centred channels must remain relative at zero, and channel IDs must survive both head turns and view switches.
- Record what a player hears at range, as `AircraftFarSound` and `A10GroundObserver` do.
- Our own volume variables prove nothing about audibility. Nobody on the build side can hear the game, so ask the
  user to listen and say so in the report.

## 8. Lights and HUD

- **Lights** go in `AircraftLights` at the real lamp positions in model pixels. Put the searchlight lens on a real
  lamp, never floating in mid-air.
- **Open seats.** If the operator sits in the open among the lamps, set `openSeat` on the light set, so the occupant
  view skips lamp backs, the lens glare and spill near the eye. Check the night view from every seat.
- **Cockpit instruments** glow as the A-10's and Mi-28's do (`A10CockpitLayer`, `Mi28CockpitLayer`), dimmed at
  night.
- **HUD.** Add a compact panel for the vehicle's state, drawn small (the bulldozer's is three-quarter size).
  - Show key hints only for a few seconds after boarding, then fade them.
  - Read key names from the bindings.
  - Show tool lines only while the tool is in use.
  - Snap the scale to whole screen pixels per font pixel: `round(guiScale * 0.75) / guiScale`, not a plain 0.75,
    which blurs the text at GUI scale 3.
    For a scale-3 game test, use a window large enough (the dozer test uses 1280×900) and assert the actual
    `getWindow().getGuiScale()`. Selecting 3 in the default 854×480 window still renders at 2.
  - Lay it out in two columns, names on the left and readings in one shared column, and size the panel from the
    measured text. The pack's font is wider than vanilla, so text placed at fixed offsets runs into its neighbour.
- **Tool slots in a hold** (the bulldozer's Ripper bay): mark them with a 1-pixel outline on the slot borders and a
  label inside the panel (the bay's tab), never a frame that spills over the counts of the next row or a label past
  the panel's edge.
  Reserve separate space for the player inventory's heading below the tool tab. If the player slots move down,
  move the hotbar and grow the screen height too. Check counted stacks and both headings at GUI scales 2 and 3.
- **Match the tool's footprint to the blade's.** A tool that lays or works the cut lane covers the same block columns
  the blade clears: sample the same band (`BulldozerRig.inBite` uses the edge less 0.05) at under a block apart. A
  4-block blade off the grid clears 5 columns.

## 9. Tests

Follow `CLAUDE.md`, which sets the test commands, JDK and pack versions. For a vehicle that means:

- **Test the feature you change, not everything.** Each feature gets a small focused test class: a
  singleplayer world, the feature's cases side by side, a few seconds of driving (`BulldozerBankClientGameTest`).
  Run that, plus the regression tests of any shared code you touched. Run the full vehicle test only when a change
  could affect the whole vehicle.
- **Probe before guessing.** When a focused test fails and the code reads right, call the internal method from the
  test (by reflection) and log what it returns, or add temporary prints; then remove them. The negative-Y sentinel
  bug was found this way in two runs.
- **One full test class** (`<Vehicle>ClientGameTest`) that drives through the real keys and covers:
  - assembly, start-up, the operator (visible, view range, camera yaw)
  - handling limits, the vehicle's job in each mode, limits and protection
  - pack-mod interplay, lights day and night, and persistence
- **A hitbox test:**
  - OBB parts lie within the model's bounds
  - shots at each part hit that part, and shots beside the silhouette miss
  - it fits past walls and under canopies the model clears
  - hitbox screenshots
- **Sound tests:**
  - OpenAL state in each camera view and across switches
  - a far-sound test: the vehicle held at distances out to 1100 blocks; still heard at 800, silent past 1024, only
    ever quieter with distance, no jump at the entity-range hand-over
  - a ground-observer test: Doppler, and delay by distance for engines and guns
- **Range tests for every weapon,** over real terrain, past the loaded world (`AircraftLongRange`,
  `RocketLongRange`, `MissileRealTerrain`). Each projectile keeps moving to the end of its range, distant players
  get the tracers, and the client follows it.
- **Screenshots** in first and third person, from the front and the side, by day and by night, of the job in
  progress, and of damage and wreck states. **Look at them**: most visual bugs are found only by looking.
- **Log `[<vehicle>] COMPLETE` or `FAILED`.** A dedicated-server test's shutdown can exit the JVM, hiding the
  assertion and ending the run after the first such class. Run each dedicated-server class on its own.
- **With and without each interacting mod.** Run with `-PwithPack`, then again with `-PpackExclude=<mod>` for
  each mod the vehicle interacts with.
- **Regression tests** for any shared code you touched: input (`VehicleInputCompat`), Shift
  (`VehicleSneakDismount`), paint (`VehiclePaint`), assembly (`VehicleAssemblingScreen`), lights
  (`AircraftNightLights`), far sound (`AircraftFarSound`), range (`AircraftLongRange`).
- **Before running shader tests,** check whether the user's own Minecraft client is running, and ask.

## 10. Sandbox world and handover

- Add `<Vehicle>SandboxSetupClientGameTest`, modelled on `DozerSandboxSetupClientGameTest` and
  `A10SandboxSetupClientGameTest`.
- Build a course that exercises every feature and limit, with targets at range for weapons, in the game mode the
  pack's mods need (FallingTree skips creative). Save it to `build/run/packClient/saves/<Name>` and open it with
  `runPackClient`.
- Write `design/<vehicle>/README.md` (controls, behaviour, model, sound, regenerate commands) and
  `IMPLEMENTATION_STATUS.md` (what was verified, how, and what was not).
- In the report to the user, say what each run covered and what it did not: listening, the dedicated server's own
  mods, and multiplayer.
