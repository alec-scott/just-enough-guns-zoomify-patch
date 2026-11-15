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
10. **It passes the player's checklist below**, checked as a player in the sandbox, before it is handed over.

### The player's checklist: what the first review catches

The FSV-6's first hand-over passed every automated test, and the user's first ten minutes in it still found fifteen
problems; the second hand-over found ten more (the rows marked *second review*), the third eight more (*third
review*), and the fourth four (*fourth review*). Each was cheap to fix and should have been caught before the hand-over. All of them are things a player
notices at once, and none were covered by a test that called code directly. Go through this list in the sandbox, as a
player, with real keys and clicks, before reporting a vehicle done. Write the answers into the brief.

| Area | Check | What went wrong on the FSV-6 |
| --- | --- | --- |
| Interaction | Every block or fitting that looks usable opens with a **real right-click aimed by the player's view**, from where a player stands | The furnace, chest, workbench and beds inside could not be clicked: the vehicle's own entity box took every click |
| Interaction | Everything the player can fit or store can be added **and removed from where they use it**, in every state (packed, unfolding, deployed, moving) | Fitting needed a trip outside; the second bed could not be added and the first could not be removed while deployed |
| Interaction | Every input slot accepts **what a player would naturally try** | The beacon rack refused raw iron, gold and diamonds; players carry ingots, not blocks |
| Interaction | A vehicle with several functions has **one place inside that shows them all**, a console in the vehicle's own style | Fittings were only reachable from separate side menus |
| Interaction | Each module's own screen (furnace, chest, beacon) opens **only from that module, where it is, one at a time**. The seats' screens show just the hold and the fit-out | *Second review:* the hold and the console each had a button per module, which made the cab a remote control for the room |
| Interaction | Every action has a **prompt where the player is**: looking at it on foot, or in the driver's panel, with the real key names | *Third review:* towing worked only by sneak-clicking the low rear bumper, with nothing to say so, and the tester could not tow at all; an empty outside locker did nothing when clicked |
| Interaction | Our own screens are **marked `@IPNIgnore`** (Inventory Profiles Next, in the pack) unless sorting them is harmless | *Fourth review:* IPN's sort buttons on the support console would scramble fittings |
| Sound | Fitted blocks **keep their vanilla sounds**: chests and barrels open and close, furnaces crackle, a brew chimes, fitting and removing sound as placing and breaking the block | *Fourth review:* opening a chest bay was silent |
| Interaction | **No input is hidden.** Every item a function consumes goes in a slot the player can see and reach from the function's own screen | *Second review:* the beacon kept its own payment inventory that no screen showed, so raw ore could not be added |
| Model | Nothing **flickers while walking round it** at any distance | The entity was culled whenever its small remaining box left the screen (section 6) |
| Model | Every fitted block is drawn, **including the ones drawn by a block entity renderer** (chests, ender chests, shulker boxes) | *Third review:* chests were invisible in the bays: their block model is empty (section 6) |
| Model | **Real blocks placed in a moving room never show through its walls**: walls stand proud of the block cells they close | *Third review:* the bunk's bed (a full block to the cell edge) showed through the slide-out's end wall, which stopped on the same plane |
| Model | **Tyres are as wide and as tall as the real vehicle's**, front ones included; compare the side and three-quarter views with photos | *Third review:* 6 px tyres looked skinny on a 7-block truck. *Fourth review:* still too small; now 16 px tall (the most under a floor one block up), 10 px wide in front |
| Model | **Size the wheels before fixing the floor height.** The tyres' top limits how big they can be under a walk-in floor | The FSV-6's floor is one block up for its room, which caps its tyres at one block; a real 6×6's are bigger and its floor higher |
| Model | A closed cab has **live instruments** (gauges, warning lamps, a status screen), as the helicopters do | *Third review:* the cab had a flat painted panel behind the wheel |
| Model | Windows are **real openings** with glass, and can be seen through **from both sides** | Painted windows read as fake from the cab and from outside |
| Model | Light sources are **3D blocks or 3D models**, like vanilla's, not flat icons | The interior lamp was a flat item sprite |
| Model | A moving vehicle **turns as tightly as the real one**: measure the radius in a test and set it in the brief | The truck needed about 20 blocks to turn round; the target was about 10 |
| Lights | A road vehicle has **tail, brake and reversing lights**, and they follow the controls | No brake lights |
| Lights | Lights on a moving part **switch on only when the part reaches its working position**, sit exactly on its lamp and go off **as it starts to leave** | The mast's lamp was lit from the start of the climb until the mast had fully stowed, and floated beside the head |
| Lights | Interiors have a **warm light source you can see**, with block light to match, not just a bright room | The first room was lit by nothing visible |
| Lights | The interior's light comes **only from what the player fitted**: no built-in fill light, no glow without a lamp | *Second review:* four default lanterns and a built-in level 12 lit the room whatever was fitted; the user wanted one lamp slot as its only light |
| Camera | The F5 camera sits **far enough back to see the whole vehicle** and **does not stutter in a hard turn** | *Second review:* 9 blocks was too close for a 7-block truck; full lock made the view jump 20 times a second (section 6) |
| Sound | Every recording is **of the real thing**: a real truck start, a real truck horn. A synthesized or generic substitute is noticed at once | The synthesized start and car-like horn were both rejected |
| Sound | **No voices** in any vehicle sound. Scan every source for speech before shipping it | Recordings with a voice in them played on start and deploy |
| Sound | Loops are **long (15–20 s) and vary** (slow random pitch and volume drift), so they don't sound repetitive | A 4-second idle loop was obviously repetitive within a minute |
| Sound | A loop holds **only the steady sound**: no start, horn or door from the same take inside it | *Second review:* the driving loop was cut across a second engine start in the take, heard every 15 s |
| Sound | A vehicle feature that copies a vanilla block **sounds like that block**: a beacon activates, hums, deactivates and clicks when an effect is chosen | *Third review:* the roof beacon was silent |
| Sound | **Steady machinery is quieter than the engine** and sits back in the mix | The generator was 40% too loud, then another 40% (×0.36 in all) |

**Ask the user to listen.** Builders can't hear the game. Say in the report which sounds are new and ask for a
listening check. Don't call a sound finished because its gain values look right.

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
- **Turning circle.** For wheeled land vehicles, `steering_speed` sets how fast the wheels turn in: each tick
  `wheelSteering += input × steering_speed`, and steering decays by `× max(0.78 − 0.25v, 0.1)`. The FSV-6 went from
  a radius of about 20 blocks to 10.3 when `steering_speed` was raised to 0.18. Put the real vehicle's radius in the
  brief, then measure it in a test: drive at full lock for 2 s and work out the radius from the distance travelled
  and the heading change (`SupportInteriorClientGameTest`).
- **Other entries.** Give it no `weapons`, `seek` or decoys unless it is armed.
- **An unarmed vehicle still needs code.** A missing or empty `"weapons"` list falls back to the test wheel vehicle's
  assault rifle and pistol (`VehicleDataManager.parseWeapons`), so the entity class overrides
  `hasVehicleWeapons()` to return false (the bulldozer, FSV-6 and mortar do). A weapon that isn't a vehicle gun
  (the mortar's load-and-drop drill) is handled the same way, in its own class.

## 5. Model

Author the model with a generator script (`design/<vehicle>/generate.py`, copied from the bulldozer's), not by
hand-editing JSON. The script keeps the geometry parametric, validated and reproducible. It writes:

- the GeckoLib geometry, two LOD models, the texture and glow atlas, and the paint mask
- `rig.json`, the Blockbench project and an offline preview

**Imported models (Superb Warfare).** A model kept from SBW is never hand-edited either: its
`design/<vehicle>/refit.py` is its generator. It builds on `design/superbwarfare-port/refit_lib.py` and reads the
source fetched by `fetch_sbw.py` from a pinned commit. The mortar (`design/mortar/refit.py`) is the worked example.
Lessons from it:
- **Convert box UV to per-face UV before rescaling.** Box UV is laid out from the cube's size, so scaling a box-UV
  cube moves its texture. `to_face_uv` is checked lossless by rendering both versions pixel for pixel.
- **Map every bone by name.** `rename_bones` refuses a bone missing from the map, so no `bone37` or pinyin name
  survives, and it renames the LODs and clips too.
- **GeckoLib hands a model the negated head pitch.** SBW's `setRotX(headPitch)` therefore raises a barrel with a
  negative X rotation in file degrees. Pose the refit that way, and check the pose with a render, not by sign
  reasoning.
- **Drop fully transparent cubes before measuring.** SBW hides helper points in the geometry (the YX-100 has one 20
  blocks ahead of its barrel), and they wreck bounds and hitboxes. `posed_quads(..., texture)` leaves them out.
- **Solve linked parts from the geometry.** SBW's bipod formula left the feet floating at low elevations. The refit
  solves the hinge angle per elevation so the feet touch the ground, and writes the table to `rig.json`.
- **Fit the hitbox over the whole sweep** of a moving part (`refit.py` unions the tube's boxes over 45–85°).
- `render_geo.py` (in `design/superbwarfare-port/triage/`) renders any Bedrock model with its texture for review.

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
- Before the game, render the model from the eye point in perspective: `design/support/render_eye.py` does it for any
  model point and pose. It shows at once whether the geometry or the game is wrong.

**Driving bones from code (GeckoLib snapshots).**
- Translations are in pixels in the authored sense: `setTranslateX(+16)` moves a bone 16 px to the vehicle's left
  (+X). This was checked on the FSV-6's slide-outs with a top-down screenshot. The A-10's fan code negates X inside a
  rotated sub-frame, so check each new use with a picture from above.
- X rotations are as authored. Y and Z rotations are the negative of the authored right-handed sense.
- To draw something in a moving part (a module in a bay), add a per-bone render on that bone. The pose is at the bone's
  pivot in render space (the model's X negated): translate by `(-x / 16 - pivotX / 16, y / 16 - pivotY / 16,
  z / 16 - pivotZ / 16)`. Coordinates are in the bone's own unmoved frame: the bone's animation moves them.
- Items and blocks draw with `ItemStackRenderState` and `ItemDisplayContext.NONE`: a block item is then a full-size
  block centred on the pose. Chests, beds and beacons render through their special models.

**Coplanar faces flicker.** Two cubes of different finishes sharing a face plane z-fight as the camera moves (rim
on tyre, lamp on housing, end wall on outer wall). `design/support/generate.py`'s `resolve_zfighting()` pushes the
smaller of each such pair out by 0.04 px within a bone; scan both poses after changes.

**Light on the model.** GeckoLib lights the whole model with one value, read at the entity's origin. When the origin is
under a floor or in a block (the FSV-6 stands its floor a block up), override `getBlockLightLevel` and
`getSkyLightLevel` in the renderer to read the light where the occupants are.

**Materials and paint:**
- Real colours, a little weathering, and a separate material per finish: paint, bare steel, rubber, glass, lamps.
- The paint mask works per texture pixel. Use `separate_finishes` and the derived `unpainted_parts` so no
  unpainted bone samples a paint tile.

**Damage states.** Plan them as the helicopters do: smoke, sparks and fire as health falls; a wreck that behaves
physically; parts that stop working when hit.

## 6. Behaviour

- **Server first.** World changes happen on the server only. Respect the world border, spawn protection and
  adventure mode, and call `PlayerBlockBreakEvents.BEFORE`/`AFTER` for every block, so claim mods apply.
- **A vehicle container sets a jet down on its wheels where the box stood.** It spawned every vehicle half its
  height up; a jet fell with no pilot, its flight model turned the nose into the fall, and it landed nose first
  (`VehicleContainerBlockEntity.deploy`).
- **Carry On (in the pack) picks up any entity with sneak + right-click and an empty hand.** Unblacklisted, a player
  could carry a whole vehicle off, and every sneak-click on a vehicle went to Carry On instead of the vehicle (the
  FSV-6's on-foot tow click lifted the truck). Every JEG vehicle entity is listed in
  `data/carryon/tags/entity_type/entity_blacklist.json` (and the legacy `entity_types` folder Carry On ships). Add new
  vehicle entity types there.
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
- **A rider's view turns with a land vehicle.** `VehicleInputHandler.clampSeatView` turns the rider's yaw by the
  vehicle's yaw as drawn this frame (`rotLerp` of `yRotO`/`getYRot()`), as a boat does, and clamps the seat's yaw
  limits against that same yaw. Clamping against the tick's yaw while the view kept its world heading made a hard turn
  hit the limit and step the F5 camera 20 times a second. Turret seats and vehicles posed by `usesVehiclePoseTransform`
  keep their world aim. Set `third_person_camera.z` to about 1.6× the vehicle's length (the FSV-6: 11.5 blocks).
- **Shift is free for controls.** Riding a JEG vehicle, Shift neither ejects (`wantsToStopRiding`) nor crouches
  (`getDesiredPose`, `PlayerVehicleSneakDismountMixin`); crouching sank the first-person camera into the seat.
  Keys that are unused on an unarmed vehicle (R, left mouse button) can carry its tools.
- **Limits that protect the world.** Give hardness caps, deny tags, block entities, and grade or floor locks clear
  rules, and show them in the HUD.

### Vehicles that move themselves, unfold, or carry working blocks

Lessons from the FSV-6 (`design/support/`).
- **The driver's client owns a ridden vehicle's position.** Vanilla's move-vehicle packet lets the driver's client
  report where the vehicle is, and the server takes it. To place a ridden vehicle from the server (squaring up, lifting
  onto jacks), override `isLocalClientAuthoritative()` to return false while you do. Otherwise the server's move is
  snapped back to the client's next tick.
- **A walk-in interior needs blocks.** Entity collision cannot make a hollow, rotated room. Snap the vehicle to the
  block grid and a cardinal heading when it parks, then place invisible, unbreakable shell blocks for floor, walls,
  ceiling and steps. `VehicleShellBlock` has kinds with their own collision shapes.
- **Keeping the shell safe:**
  - Keep a saved registry of every block a vehicle placed.
  - Let a shell block whose vehicle is gone clear itself (random tick plus the registry).
  - Remove the shell when the entity is destroyed (`remove` with `shouldDestroy()`).
  - Refuse to pack up with anyone inside.
- **The entity box is one axis-aligned envelope.** `envelopeBoxes` are merged into a single box round all of them,
  so keeping any long box makes the entity's box span the whole vehicle. On the FSV-6, the chassis box under the floor
  made the box cover the room, and every click inside hit the vehicle. Keep only the boxes that are really outside
  the room.
- **Culling.** When the entity box is cut down like that, override `partBounds()` to cover the whole unfolded vehicle,
  or it vanishes whenever the part that is left leaves the screen (seen as flicker walking round it).
- **Test clicks with the real use key, aimed by the player's view.** Calling the use method from the server passes
  even when every real click is taken by something else (`SupportInteriorClientGameTest`).
- **Room cells and clicks.** Give the room's cells an empty outline shape, so clicks pass through to the real blocks
  and bays. While the vehicle is unfolded, keep its own entity box out of the room (`envelopeBoxes`,
  `entityPushBoxes`), or the player is pushed out and every click inside picks the vehicle.
- **Shapes that change.** A vehicle with a second pose gives its fitted boxes for it as `obb_deployed` in the vehicle
  JSON, and returns them from `obbBoxes()` in that pose.
- **Working blocks as data.** Fitted blocks (crafting tables, furnaces, chests) are data on the vehicle, not blocks
  in the world:
  - They work while it moves, survive the crowbar's container (save them in `addAdditionalSaveData`), and mobs and
    pistons cannot reach them.
  - Open the vanilla menus bound to the vehicle: subclass them and override `stillValid`.
  - Give `ContainerLevelAccess.create(level, pos)` real coordinates, or crafting results never compute.
  - Cook and brew with your own tick: the vanilla block entities write lit states into the world at their position.
- **Walking about inside while it moves is not possible with a block shell.** The shell exists only while parked;
  vanilla has no collision against a moving, rotating entity interior, and each player's own client owns their
  movement, so a room built from entity boxes jitters by the network lag between the driver's and each passenger's
  client. The ways to do it, from cheapest: crew seats in the back that face a module and open it (one at a time);
  own moving collision plus carrying riders along each tick (large, and still laggy for passengers); or a Sable
  sub-level (Create Aeronautics, in the pack), which moves real blocks that players can walk on, at the cost of a hard
  dependency that `-PwithPack` does not load.
- **Fitted blocks are drawn two ways; test both.** A block with a real model is drawn with `submitMovingBlock`. A
  chest, trapped chest, ender chest or shulker box also reports `RenderShape.MODEL` in 26.1, but its block model is
  empty (its look comes from the block entity's renderer), so that draws nothing. Draw those through
  `BlockModelResolver.update(state, blockState, BlockDisplayContext.create())` and `BlockModelRenderState.submit`, as
  an enderman draws its held block (`SupportVehicleRenderer.drawSpecialBlock`). Fit both kinds in the visual test
  (`SupportLookClientGameTest`): the FSV-6's chests were invisible in every bay until then.
- **Walls proud of the cells.** Where a real block (a bed) fills a cell up to the room's edge, the wall that closes
  that edge must stand a little outside it (0.4 px), not on the same plane, or the block shows through from outside.
- **Real blocks only where needed.** Place a real block only where a feature needs one (a bed for sleep and spawn).
  - Block players from breaking it (`PlayerBlockBreakEvents.BEFORE` on registered positions).
  - Use up the fitted item if the block was destroyed some other way, so nothing doubles.
  - Lock that fitting while the block exists.

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
- **Use a real recording for every recognisable sound.** Players know what a truck start and a truck horn sound
  like. A synthesized start and a car horn were both rejected on the FSV-6. Synthesize only sounds nobody can compare
  against, such as a brake-air hiss under the mix.
- **No voices.** Field recordings often have someone talking in them. Listen to every source and cut the take
  clear of the talking; note in the script which seconds were cut and why (`design/support/prepare_audio.py`). The
  FSV-6 first shipped a start and a deploy sound with a voice in them.
- **Loops of 15–20 s, with slow drift.** A loop of a few seconds is heard as a loop within a minute. Cut long loops,
  and let pitch (about ±3%) and volume (about ±7%) wander slowly on top of the RPM model. Two or three sines with
  random phases per instance and periods of several seconds are enough (`SupportAudio`).
- **Machinery under the engine.** Generators, pumps and fans run for minutes, so mix them well below the engine.
  The FSV-6's generator was cut by 40% after review.

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
- **Drawn glows under shader packs.** Every light layer draws additive `RenderTypes.eyes` quads, which a shader pack
  treats as emissive and blooms: the FSV-6's headlight pool became a hard white blob. Where the pack lights something
  for real, leave the drawn version out under shaders (`ShaderPacks.inUse()`; spots light the ground through
  `LambDynamicLightsCompat`), as `VehicleLightsLayer` does for the pool. Check every light at night from outside with
  the user's shader pack: Photon by default (`-PpackShaders`), with their options from `pack-config/shaderpacks/`.
- **Glows need geometry behind them.** The A-10's exhaust glow was placed for a nozzle the model never had and read
  as a hole into the engine; model the part first (`design/a10/nozzle.py`), then put the glow inside it.
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
- **Brake and reversing lights** are light kinds (`AircraftLights.Kind.BRAKE`, `REVERSE`), driven by synced
  `braking()`/`reversing()` state in `flash(vehicle, …)`. Tail lamps glow dim when lit and bright when braking.
- **Lights on moving parts** belong to a separate light set used only when the part is fully in its working position
  (the FSV-6's `TRUCK_MAST_UP`). Place them at the lamp's lens in the posed model, not where the part sits when
  stowed. Screenshot them at mid-travel to show they are off.
- **Match the tool's footprint to the blade's.** A tool that lays or works the cut lane covers the same block columns
  the blade clears: sample the same band (`BulldozerRig.inBite` uses the edge less 0.05) at under a block apart. A
  4-block blade off the grid clears 5 columns.

### Prompts for actions in the world

Every action a player can take on the vehicle needs a prompt, shown where they are when they could take it, with the
real key names (`KeyMapping.getTranslatedKeyMessage()`):
- **On foot:** the action bar while looking at the part (`SupportHints`: the hitch at the back, each outside
  locker), refreshed every half second.
- **In the seat:** a row in the driver's panel, with a pulsing lamp when there is something to act on ("N: hitch
  LAV-150"), and a key for it (a new `KeyMapping`; check the pack's options for a free default, N here).
- **When nothing happens, say why:** an empty locker says what to fit and where.
  - A turn the vehicle refuses (`setVehicleYawIfUnblocked` silently undoes a turn into blocks) needs a status. The
    BD-40 sat in its own trench saying "Ready" while A and D did nothing; it now cuts the walls with the blade down
    (`BulldozerTerrainWork.clearTurn`, natural ground only, using `turnBlockedByBlocks`/`boundingBoxAtYaw`) and says
    "Turn blocked: lower the blade to cut" or "back out" otherwise.
  - Name the real reason. A raised blade against a dirt bank said "Too hard for the blade", which tells the player
    lowering it won't help; it now says "Blade raised: lower it to dig".
- **Hold a working status over the gaps between bites.** Work that happens every few ticks (a blade cutting a block a
  column at a time) reads as on/off/on; the dozer holds "Clearing" 12 ticks after its last bite while pushing. Sample
  the status every tick in a test and fail on any drop back to idle.
- **The vehicle info panel (name, hull, energy, weapon) must not cover the work.** Its old spot, lower middle, was
  over the BD-40's blade, over every vehicle in F5, and over the AH-6's and A-10's cockpit instruments. Now
  (`VehicleHudOverlay.vehicleInfoInCorner` / `vehicleInfoAtTop`): the FSV-6 and the BD-40 put it in the bottom-right
  corner in both views (their own panel is top left, where a top-middle title ran into the FSV-6's hint and refusal
  lines, and they have no weapon list); any other vehicle in F5 and aircraft in first person put it at the top middle
  (the bottom corners hold the seat and weapon lists); other ground vehicles in first person keep the lower middle, with the
  action bar lifted above it (`GuiMixin`). A new vehicle with a dashboard or a tool in the lower middle joins one of
  those. Energy reads as a percentage, not the raw store (5,000,000); speeds read in m/s everywhere (jets too).
- **The boarding line names the real dismount key.** Vanilla's "Press Left Shift to Dismount" is wrong here (Shift is
  descend or blade down); `VehicleInputHandler` replaces it with `VEHICLE_DISMOUNT`'s key on boarding.
- **Names:** a vehicle's name is its `entity.jeg.<id>` (`VehicleEntity.getTypeName`). Don't override
  `getDisplayName` for a menu title: Entity and MenuProvider share it, and every vehicle was "Vehicle Inventory" in
  kill messages, commands and hold titles.

### Cab instruments

- **Live faces are additive: draw them on plain dark faces.** `RenderTypes.eyes` adds its light to what is under it,
  so anything painted on the model face shows through. The FSV-6's wall screens were modelled with the "gauge"
  material, which the atlas paints as a dial, and every screen showed a stretched dial behind its display. Give a
  face that carries a live display a plain dark material ("seal").
- **Draw each set of instruments only for the players who can see it** (aboard for the cab, inside the room for its
  panels; `SupportCockpitLayer`): nobody else can read them, and it saves the work.

A closed cab gets live instruments, as the helicopters do (`SupportCockpitLayer`, after `Mi28CockpitLayer`):
- **Placement:** put the driver's gauges where they are seen over the steering wheel (a binnacle on the dash top),
  and check the sightline from the driver's eye in the generator (`render_eye.py`).
- **Drawing:** backlit dials with live needles (`CockpitPanel.needle`; textures from a script, `make_cockpit.py`,
  with the needle's angle convention), warning lamps from a symbol strip tinted when lit, and a status screen for the
  co-driver with bars filled from synced state.
- **Power:** the screens boot with power (`CockpitPanel.bootLevel`) and dim a little at night.

### Screens inside the vehicle (consoles)

A vehicle with more than one function needs a single screen that opens the rest. Make it part of the vehicle, not a
plain inventory. The FSV-6's support console (`SupportFitScreen`) is the example.
- **Draw it as equipment in the vehicle's style.** Use a steel case with rivets and a stencilled name plate,
  recessed wells for slots, a CRT status display and push-buttons. Draw everything with `fill` and text, with no
  texture to keep in step with the model. Share the drawing (`ConsoleStyle`) between a vehicle's screens.
- **Lay the fittings out as the vehicle.** The FSV-6's fit-out is a top-down plan: cab at the top, each slot where
  that fitting is in the vehicle, and empty slots showing a faded picture of what they take. Players find a slot by
  where it is, not by reading labels.
- **The status display reads the synced entity state.** Show only values that are synced to the client (power,
  battery, unfold state, light level, beacon level and effect, bunks, towing), each with a status lamp: green on,
  amber partial or working, dark off. A value the client cannot see should not appear on the display.
- **The console shows; the modules open.** The console shows each module (icons, a lit lamp on a burning furnace)
  and fits them, but a module's own screen opens only by clicking the module in the room. Its keys open only the
  vehicle's own screens (the hold) through one payload (`SupportModulePayload`). Play `UI_BUTTON_CLICK` on press.
- **A module with its own controls gets its own terminal in the room.** The FSV-6's beacon terminal
  (`SupportBeaconScreen`, its own `VehicleShellBlock.Kind.BEACON_PANEL` cell beside the console) holds the beacon's
  power rack and its effect keys. Don't reuse a vanilla screen that keeps state the vehicle can't see: the vanilla
  beacon screen's payment slot was an inventory no other screen showed.
- **One panel per clickable wall cell.** Two panels in one wall cell can't be told apart by a click: give each its own
  cell and draw each centred on its cell (`generate.py`), and aim tests at each cell's centre.
- **Empty slots say what they take** in a tooltip. Hints are short, and never run under a neighbouring label: the
  pack's font is wider than vanilla's.
- **Leave room for the pack's screen buttons.** Inventory Profiles Next puts a sort button at the screen's right
  edge, level with the player inventory label. A wide screen with its own content there gets the button drawn on top
  of it. Keep that spot empty, and screenshot the screen in a `-PwithPack` run to check.
- **Test it with the mouse.** Give the screen a lookup (`keyCentre(slot)`), move the cursor there in the test,
  `pressMouse`, and assert the menu that opened on the server (`SupportInteriorClientGameTest.clickConsole`).

## 9. Tests

**Play it before the tests are final.** `-PwithMcp` puts mcpfabric in the pack client, so an agent can drive it:
screenshots, movement keys, right-clicks on what the crosshair is on, screens; our own bindings with
`/jegkey <name> [ticks]` (`DevKeysClient`). Drive where a player would, not where a test starts: the BD-40 turn bug
only showed after a pass had left it in its own trench. Take bursts of screenshots (2–3 a second) and compare them;
a label that flickers shows only across frames. Each problem found becomes a focused test.
```
./gradlew runPackClient -PwithPack -PwithMcp -Pminecraft_version=26.1.2 -Pfabric_version=0.155.3+26.1.2 -Ploader_version=0.20.1
```
Before launching, check no other session is building in this checkout: a compile replaces `build/classes` under a
running client (`NoClassDefFoundError` for a class that exists).

**Bisecting the pack.** `-PpackExclude=shaders` currently crashes on start ("Global terrain uniforms have not been
updated", Iris and Voxy) and `-PpackExclude=etf` fails to load (a pack mod requires EMF). To tell whether a fault is
the pack's or ours, run the same test on the bare client (no `-PwithPack`) instead.

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
