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
   functional or documented as cosmetic. If a player will ask "what does this do?", the answer must exist. One
   exception, by the user's choice: SBW's YX-100 keeps its science-fiction look (`design/yx100/BRIEF.md`); its size is
   still a real tank's.
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

### The fleet review: rules every vehicle must meet

The user played all nineteen vehicles together (2026-11-18) and the same faults came up on vehicle after vehicle. Each
is a rule now: check every new vehicle against it before hand-over, as a player, in the pack with shaders. The
tracker is `design/READINESS.md` ("The user's fleet review"); "how" names the shared fix to reuse.

| Area | Rule | Found on | How |
| --- | --- | --- | --- |
| Sound | **Standing still sounds standing still.** At rest only a real idle plays: no track, tyre or labouring engine layer, and the cab hears a muffled idle, not a muffled drive | BMP-2, LAV-150 (the idle was the driving take slowed down) | `engine_idle` and `engine_cab_idle` cut from the real start take's idle (`design/ground-vehicles/prepare_idles.py`); `GroundVehicleAudio` crossfades the cab from idle to load |
| Feel | **Turrets and mounts have weight.** Every powered turret or mount has a real `traverse_rate`/`elevation_rate` (degrees a tick) and spins up and brakes; nothing follows the mouse 1:1. The view leads; the gun follows and its bore marker shows where it points | BMP-2, LAV-150, HPJ-11, sentry, speedboat gun | `"turret"` rates in the data; `VehicleEntity.slewSpeed` (full rate in `TURRET_SPIN_UP_TICKS`, braking to stop on the aim). Typical: tank and IFV 1.5-2 (30-40 deg/s), hand-swung pintle 3, CIWS 4 |
| Feel | **The view and the mount never drive each other.** A mount that takes its heading from the rider's view must not also turn the rider with it | mortar (spun on its own after any mouse move) | `VehicleEntity.yawFollowsRider`; `clampSeatView` skips the turn-with-vehicle for it. A handwheel-laid mount (mortar) overrides it to false: keys traverse, the mouse looks |
| Feel | **A turning circle a player can use**: radius from a crawl at most about 9 blocks for wheeled vehicles and IFVs, 14 for heavy armour, measured as `ArmourTurningClientGameTest` does. `steering_speed` is scaled by mass; check it after changing `"mass"` | BMP-2, LAV-150 (were 29 and 19), PLZ-05, YX-100 (50) | `engine.steering_speed` |
| Camera | **A camera on a moving part moves smoothly**: a server-laid turret is drawn on clients between its synced angles at the last two ticks (packets arrive mid-tick), and the crew camera's rumble never steps more than 0.45 rad a tick | LAV-150, BMP-2 first person | `VehicleEntity.drawsTurretFromSnapshots`, `VehicleFeel.MAX_RUMBLE_STEP`; `cameraJump` in `ArmourTurningClientGameTest` |
| Feel | **Speed for the game, not the spec sheet.** Real top speeds felt too fast for the armour; set road speed and acceleration by feel and write the number in the brief | BMP-2 (now 52 km/h), LAV-150 (72) | `max_forward_speed`, `acceleration`; mass scales the response (G13) |
| Weapons | **Muzzle flash to the calibre, small in the gunner's view.** An autocannon's flash is one short shrinking flare; a coax beside the sight uses the smallest flash | BMP-2, LAV-150 | `spawnVehicleMuzzleFlare` (20/30 mm), `spawnVehicleGunMuzzleFlash` (land vehicles: small) |
| Weapons | **Rounds never strike their own vehicle.** Submunitions and pellets from a shell that breaks up at the muzzle keep its arming time | YX-100 (its grapeshot destroyed it) | `ShellEntity.dispense`: children inherit the carrier's `tickCount` |
| Weapons | **Tracers and flashes leave the muzzle in every view, zoomed included**: lay a gun on the server at a rate (R2) so the drawn gun and the rounds agree; a zoomed sight sits **above and behind the gun, never on its muzzle** (from the muzzle, rounds and flash came out of the crosshair), so they rise into the crosshair from the bottom of the view | speedboat, Mi-28 gunner | the speedboat's rates; the Mi-28's `zoom_x/y/z` 0.26 above the bore and 1.6 behind the muzzle (`Mi28GunnerAimClientGameTest` pins it); a turreted helicopter gun's tracer starts on the gun as drawn, not where the server fired it a few ticks earlier (`BulletTrailRenderer.drawnVehicleMuzzleOffset`) |
| Weapons | **Reloads at a playable pace, and heard as heavy work** (a spent case dropped, the next round slid home): under about 4 s for a single round, 8 s for a rocket rack | TOW, PLZ-05, Type-63; BL-132, Mk42 | stats in `VehicleWeaponStats` (x 0.64); `design/mk42/prepare_reload_audio.py` |
| Camera | **No seat looks out from outside the hull.** A troop seat with no real view of its own has no first person | BMP-2, LAV-150 | `"no_first_person_seats"` in the data; F5 cycles the chase and hull cameras there |
| Camera | **The pilot sees the panel in first person.** The pinned view looks down at a seated pilot's eye line, with the eye about 0.7 m from the panel; the helmet display's speed and altitude boxes follow the aircraft's line ahead, and the status card moves off the displays | CH-47 (eye 2.5 m up and 0.44 m from the panel: only windscreen showed) | `VehicleEntity.cockpitViewDown` (12 on the CH-47), the seat's `zoom_camera`, `HelicopterHmd`, `VehicleHudOverlay` |
| Camera | **The pilot's eye stays in the seat when the hull banks.** In a helicopter only the view's own roll stays level through sway; the eye is placed with the hull's whole pose | CH-47 (eye 2.5 m over the roll pivot: swaying put the camera through the posts and out of the airframe) | `VehicleEntity.seatEyeOffsetWithPose` |
| Camera | **The last F5 view is an action camera on the vehicle** (helicopters: tail boom; A-10: wing; land vehicles and boats: rear corner of the hull's side, looking forward along it) | BMP-2, LAV-150 | `VehicleEntity.tailCameraPosition`/`hasHullCamera` |
| Model | **Textured like a real machine, not flat colour**: panel lines, weathering, decals, colour variation | BMP-2, LAV-150, HPJ-11 | `design/ground-vehicles/detail_texture.py <id>`: enlarges the texture and weathers each texel by where it sits on the model (seams, worn edges, bolts, mottling, streaks, dust, mud); the untouched texture kept as `design/<id>/texture_base.png`, a before/after in `review-texture.png`. Add a vehicle to its `VEHICLES` table |
| Model | **The occupant never clips the vehicle**: head clear of walls and roofs, feet on the floor, and a seated pilot stays in the seat as the airframe banks or slips | FSV-6, AH-6 | walls and floors stop short of the block cells real blocks sit in (`design/support/generate.py`); `VehiclePassengerRenderHandler.tiltWithAircraft` |
| Model | **Cab controls and the first-person view are steady**: a gear stick or lever moves only when its control does; no camera jitter | BD-40 gear stick, FSV-6 first person | drive levers from whole-tick motion, eased per frame (never from in-tick progress); crew cameras interpolate from `xOld` as the renderer does (`interpolatedVehiclePosition`) |
| Lights | **Lamps read as light under shaders** (Iris in the pack): a lit lamp is an emissive lens plus a soft glow, never a bare white cube; glows are not see-through from behind | Mi-28 tail, AH-6, A-10 tail and exhaust, bush plane | glows on `entityTranslucentEmissive` under shaders with the premultiplied `light_glow_soft.png` (a texture white to its rim is emitted as a disc), dimmer, no drawn searchlight shaft under shaders, small lens glare, and no glare at all for a lamp that is not a searchlight; flares are light sources too; each steady lamp a LambDynamicLights source (`AircraftLights.addLampSpots`); a glow inside a part (a jet's nozzle) fades out as the view turns off its axis, since the pack's bloom shines it through the walls (`VehicleExhaustGlowLayer`) |
| Lights | **Beams are steady and soft**: a headlight beam doesn't flicker as the vehicle moves and fades smoothly, not as a hard drawn shape | BD-40, FSV-6 | no drawn shaft on ground vehicles; the beam's spot smoothed over ticks |
| Lights | **A lamp's lit glow matches its unlit lens in size** (reversing lamps looked tiny next to their glow) | road vehicles | road lamps keep their size and only brighten |
| Damage | **Explosions are the realistic blast** of the A-10 and helicopters (fireball, real smoke column, debris), never the classic JEG burst | all | `RealisticBlast.explode` / `show` |
| Damage | **Smoke looks like smoke** (soft, volumetric, drifting) and never hides the target from its own gunner | LAV-150, Type-63 | rockets blow their smoke out behind the launcher (`ShellGunEntity.fireShell`); smoke screens (dischargers, grenades, smoke shells) are one billowing lit cloud per canister (`SmokeScreenPayload` -> `MissileBlastRenderer`'s smoke), never vanilla campfire smoke. **All other vehicle smoke and flame** (damage by hull left, burning wrecks, jet trails, gun gas, exhausts, flare trails) goes through `SmokeFx.emit` (kinds GREY, DARK, BLACK, FIRE, WHITE); a shell gun's muzzle blast and barrel wisps are drawn from its `GunReportPayload` (`MissileBlastRenderer.gunFired`). No `ParticleTypes.*SMOKE*` or campfire smoke on a vehicle (`SmokeFxClientGameTest`) |
| HUD | **Cards hold their size**: a card never shrinks back after a reading grows it, numbers have a digit of headroom, numbers sit against the right edge (`HudPanel`); F1 hides the vehicle HUD | all | `HudPanel.width`, `numeric` |
| HUD | **Minimal and single**: show only what this seat needs now, each fact once; readouts in fixed-width cards (the m/s card grew with its digits); nothing flashes; every mark on screen is explained by its shape (the yellow bore marker was not) | all | `VehicleHudOverlay`: one status card headed by the vehicle's name (the user wants the name kept, 2026-11-19) with `HudPanel.reading` rows (a speed under bars reads in the bars' column, `readingAfterBars`: set against the card's edge it sat apart, 2026-11-26), the weapon list for weapons (a land vehicle's single gun too: the 12.7 mm technical's gunner saw no rounds left, 2026-11-26), the crew list only when it matters, the bore marker a ring labelled GUN; switches drawn in the HUD style, never vanilla buttons |
| Interaction | **The helm has live instruments**, in the vehicle's own style, as the helicopters' cockpits do: no blank dash | speedboat | `SpeedboatCockpitLayer` (textures `design/speedboat/make_console.py`); draw in the dash's own bone frame (`inDash`), or it sinks into the dash when the hull pitches |
| Interaction | **A right-click on any visible part boards it, every time**, whatever is in hand | all | the gun mod's own right-click (aim) never swallows a click on a vehicle (`FabricClientBootstrap` UseEntityCallback) |
| Defences | **Automatic mode is a visible switch** (the hold's screen, the HUD's key chip), heard when thrown, engages hostile mobs only (a monster's mount included: a chicken jockey) and needs no ammunition; a mounted gun on a crewed vehicle gets one too, for when nobody is at it | HPJ-11, sentry, speedboat | `VehicleMenu.BUTTON_TOGGLE_AUTO` and the screen's switch; `AutoDefenceEntity.unmannedFireIsFree`; `Hpj11Entity.targetPriority` takes hostile mobs after air threats |
| Sound | **Weight and grit**: guns heavy and loud with a mechanical edge, horns deep, starts real; traverse a heavy electric whine, deeper the heavier the mount | sentry, howitzer, LAV-150 start, BD-40 horn, HPJ-11, BL-132, Mk42, Type-63 | the guns' body raised and soft-clipped (`design/sentry`, `design/howitzer` prepare_audio); `VehicleFeel` powered mounts on the traverse drive, pitched by mass |
| Sound | **Work machines warn**: a reversing beeper on construction and support vehicles | BD-40 | `design/bulldozer/prepare_review_audio.py` |


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
- **Parts on a turret follow it.** A box with `"follows": "turret"` turns with the turret's yaw about the turret
  origin, and `"follows": "barrel"` also elevates about the barrel pivot (`OBBInfo.Follows`). Write them at rest
  (turret yaw 0, barrel level). Hits and hit parts are tested in each box's own rest frame (`toBoxRestFrame`); the
  block and entity envelopes take the hull-aligned box round each as posed (`posedHullBoxes`), and the entity box is
  refitted when the turret moves, because bullets find a vehicle by its entity box. A barrel box doesn't push
  entities. The Mk42 is the first user (`Mk42ClientGameTest` shoots the gun and gunhouse at 0, 90 and 180 degrees).
- **Part damage to `turret` jams the gun** (`isTurretDamaged` stops firing after about 10 points). On a vehicle that is
  mostly turret (a gun mount), make only the gun the `turret` part and the house `body`, or the first hit jams it.
- **Long thin parts.** Where parts like a tail boom or stub wings would make the rotated envelope much bigger than
  the hull, keep the entity box at the hull size, as helicopters do (`usesHullBlockCollisionBox`). Then size
  `ModEntities` `sized(width, height)` to the hull.
- **Long vehicles are missed by vanilla's entity search.** Vanilla finds an entity by the 16-block section its origin
  stands in, looking only 2 blocks round the searched box. A part more than 2 blocks from the origin, in the next
  section, is never found: the Chinook's cockpit (7 m ahead of its origin) couldn't be clicked to board, and a round
  at its nose from the side passed through (2026-11-25). Searches that must see whole vehicles add them from
  `LargeVehicles` (every vehicle whose parts reach past 2 blocks registers itself each tick): `BulletEntity`'s
  entity hits and the player's view (`LargeVehiclePick`). Explosions still use vanilla's search. Test a click and a
  shot at the far ends of a long vehicle, with its origin near a section's edge.
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
- **The vehicle creator.** Every assembled vehicle needs a `data/jeg/vehicle_assembly/<id>.json` recipe, a short
  name for the list (`vehicle.jeg.<id>.short` in `en_us.json`, e.g. "BL-132"; without one the full name is cut off on
  its button) and a place in `VehicleAssemblingScreen.ORDER`, which runs each tab from light to heavy. The screen
  (restyled 2026-11-20) turns the vehicle's real model in 3D, sized from its `obb` parts, with the full name under it,
  so the parts must cover the whole model or it is drawn too big for its stage (`VehicleAssemblingScreenClientGameTest`
  shoots every vehicle's stage). Its tabs are categories, not vehicle types: Ground, Air (helicopters and planes), Sea,
  Artillery, and Defences (an `AutoDefenceEntity`). The old silhouette icons (`textures/vehicle_icon/`) are no longer
  drawn. Carried weapons (the mortar, the TOW) are crafted
  items instead and pack into their own item on a sneak-click with an empty hand; everything else packs into its
  container with the crowbar (`VehiclePackUp` covers both for the whole fleet).
- **An unarmed vehicle: `"weapons": []`.** An empty list means no weapons (since 2026-11-17); only a missing list
  falls back to the vehicle type's default assault rifle and pistol (`VehicleDataManager.parseWeapons`). Before that
  the empty list also fell back, and the bush plane's pilot found a rifle and a pistol on the HUD. The bulldozer,
  FSV-6 and mortar also override `hasVehicleWeapons()` to return false, from before the fix. A seat whose crew works
  the vehicle with R or the mouse needs `"ban_hand": true`: hand items are banned only by that or by a weapon usable
  from the seat, and the fallback rifle had been doing it for the mortar, so after the fix its R reloaded the gunner's
  hand gun instead of loading a round. A weapon that isn't a vehicle gun (the mortar's load-and-drop drill) is handled
  in its own class.

## 5. Model

Author the model with a generator script (`design/<vehicle>/generate.py`, copied from the bulldozer's), not by
hand-editing JSON. The script keeps the geometry parametric, validated and reproducible. It writes:

- the GeckoLib geometry, two LOD models, the texture and glow atlas, and the paint mask
- `rig.json`, the Blockbench project and an offline preview

**Curved skins (mesh).** GeckoLib draws only boxes. A vehicle that must look curved (the CH-47 is the worked example,
`design/chinook/mesh.py`) adds a quad mesh drawn on its GeckoLib bones by `VehicleMesh`, so animation still works:
- Write each part relative to its bone's pivot. GeckoLib hands the per-bone render the pose at the pivot.
- Keep opaque, glass and lining parts apart in the file: opaque goes in the cutout pass, glass in the translucent one,
  and linings are drawn only near the camera. Any opaque texel drawn in the translucent pass looks grainy under shaders.
- **Cut window outlines into the geometry, never into the texture's alpha.** A cutout edge steps at the texel size,
  and from a seat a hand's breadth away even 6 mm texels show as stairs. Give the window a signed distance
  (`Surface.clip`): the quads it crosses are split along it, the pane goes to the glass pass and the frame stays opaque,
  and `clip_inner` adds the frame's depth up to the lining. Blend a seal into the colour, not the alpha.
- Split only the cells the outline crosses (sample them), not every cell near it: the distance test alone quadrupled
  the CH-47's quads.
- **To make a model roomier, grow it on output; don't re-author it.** The CH-47's cabin went from 2.3 x 2 m to 3 x 3 m
  inside (`generate.grow`):
  - Each half moves out from the centre line, which only lengthens the flat roof, floor and belly.
  - Everything above a line moves up. Ahead of the cabin the nose is stretched smoothly instead, because a step there
    stood a box on the cockpit.
  - Every box, bone pivot, mesh point, seat, muzzle and hitbox goes through the same mapping. Rotors only rise.
  - Small centred items (lamps, the hook) keep their size.
  - Keep each surface's authored grid (`Surface.D`) and draw window outlines and painted placings in it. Add ring
    points on the raise's line, so the new wall shades as one with the wall below.
  - **Nothing may be authored exactly on the raise's line.** A point on it doesn't rise. The CH-47's aft pylon
    underside sat at 47, the line, so it stayed a metre under the grown ceiling and hung across the ramp opening as a
    dark slab (the user, 2026-11-21). Author the parts that meet the cabin's ceiling at the ceiling (`CABIN_TOP`), and
    look out of the cabin's openings in a cut-away render after any grow.
- **Build mirrored sides from named faces, not from a pair ordered by sign.** `cabin_wall` set `x_in, x_out =
  (-OUT_HALF, -IN_HALF)` for the left side, so its "inner" face was the outer skin. The lining and ribs were built
  through the skin, and the left side showed the cabin's quilting from outside. Check both sides from outside.

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

**Tracks are driven from a path, not from keyframes** (the PLZ-05, first; the BMP-2 and the MBT next). SBW stacks a
side's links at one point and moves them along a hand-typed keyframe path in code. Instead the refit computes the
links' centre line as the convex hull of the running gear's circles (sprocket, road wheels, idler, return rollers, each
grown by half a link's thickness), checks it against SBW's path (`design/plz05/refit.py`: within 1.5 px), and writes it
to the vehicle data as `"tracks"` (path, links per side, rest point and arc length, half the track width, and the wheel
bones with their radii). The shared driver (`AbstractVehicleGeoRenderer.updateTracks`) moves `track_link_<side>_<i>`
along it and turns `track_link_<side>_<i>_turn` to its direction; the client runs each side by the hull's movement and
turn (`VehicleEntity.trackTravel`), so nothing is synced. Measure from where the hull was at the end of the last tick, not
across `super.tick()`: a vehicle another player drives is moved by interpolation before that, and its tracks stood
still (only the driver's own client saw them run; `Plz05ClientGameTest` watches, not drives). Name the bones that way in the refit and the vehicle gets
moving tracks with no code. Check the link turn in a review render first: the right sign looked wrong by sign
reasoning; with it, links lie flat on the runs and their guide horns point inward round the ends.

**Road wheels roll and steer from the data as well** (the LAV-150, first). The refit measures each wheel's rolling
radius and the wheelbase from the drawn geometry and writes `"wheels"`: `{"roll": [{"bones": [...], "radius": px}],
"steer": [front wheel bones], "wheelbase": blocks}`. The shared driver (`AbstractVehicleGeoRenderer.updateWheels`) turns
each rolling bone by the distance driven over its radius (`VehicleEntity.wheelTravel`) and each steered bone about its
own Y axis by `VehicleEntity.wheelSteer`: the angle a real wheelbase gives for how fast the hull turns at its speed
(tan steer = wheelbase × turn per tick / distance per tick, at most 35°, eased), so every client agrees without the
driver's input being synced, and backing through a turn steers the other way. A wheel that both steers and rolls can be
one bone (GeckoLib rolls about X before it steers about Y). `Lav150WheelsClientGameTest` pins the roll per block and the
steer's sign and size.

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

**Opaque models, glass apart.** Vehicles draw opaque (`RenderTypes.entityCutout`) so shader packs light and shadow
them like mobs; drawn translucent (as from the port until 2026-11-15) Photon gave every vehicle a flat, washed-out
look (`VehicleLookCompareClientGameTest` shows both). Glass is any partly transparent pixel: `VehicleGlassTextures`
splits it out of the texture (painted ones too) and `VehicleGlassLayer` draws only it, translucent. So give glass an
alpha between 1 and 254, and every other pixel 0 or 255.

**Lit vertex by vertex.** Minecraft lights an entity with one value read at its origin, so a vehicle nosed into a
hangar was lit wholly as daylight, the half under the roof as bright as the cab in the sun (plain under Photon, whose
interiors are far darker than vanilla's; 2026-11-26). `VehicleVertexLight` gives each vertex of the model and its glass
the sky light of the block it is in (a vertex inside a solid block takes the brightest open block round it); block
light stays the renderer's own unless the vertex's block is brighter, so the FSV-6's room lamp and LambDynamicLights
still light the whole vehicle. A layer that draws the model again with `passInfo.packedLight()` should wrap its buffer
in `VehicleVertexLight.wrap` the same way.

**Materials and paint:**
- Real colours, a little weathering, and a separate material per finish: paint, bare steel, rubber, glass, lamps.
- The paint mask works per texture pixel. Use `separate_finishes` and the derived `unpainted_parts` so no
  unpainted bone samples a paint tile.

**Damage states.** Plan them as the helicopters do: smoke, sparks and fire as health falls; a wreck that behaves
physically; parts that stop working when hit.

### Texture quality (the review of 2026-11-19)

**Interiors people walk in need patterns, not flat colours** (the CH-47, 2026-11-21: "more detailed textures inside"). Paint them per material through `bake_unique`'s `fill`, at a higher density (`boost`). Lay each pattern out in model coordinates, so it carries on across the pieces a wall is built from (`design/chinook/interior_paint.py`: quilted blankets, floor plates and tie-downs, rollers, riveted frames, cables). Keep those materials out of the weathering pass (`KEPT`), and give a pattern its own material rather than reusing one the guns share (the cable runs were `black`, as the M240s are).

- **No flat swatches.** A face painted one colour at one texel a model pixel reads as plastic next to the pack's
  shaded world. Refits run `design/ground-vehicles/detail_texture.py <id>` (4x texture, panel seams, worn edges, bolt
  heads, grime streaks, dust on top faces, mud low down; salt and thin rust runs for ships' guns; non-skid on decks).
- **Our own generators bake unique UVs.** A generator that textured every cube from one tile per material
  (`material_uv`) gives every face its own texels with `refit_lib.bake_unique(BONES, MATERIALS, texels)` before
  writing the geometry, paints its markings on the baked faces (registration, stencils, rib tapes), runs
  `detail_texture.detail_image(geo, base, cfg)` and puts glass and lamps back with `refit_lib.paste_faces`. The glow
  layer comes from the same faces (`refit_lib.glow_of`). The sentry, bush plane, howitzer and FSV-6 do; a generator
  whose tiles carry patterns (the FSV-6's diamond plate, hazard stripes, dials) passes `fill=` to lay each face with
  its tile, stretched or repeated as the tile atlas showed it.
- **Repaint an unrealistic livery with rules, not by hand.** `detail_texture.py`'s `repaint` rules map a hue band (and
  optionally bones) to a real paint, shaded as the old paint was: SBW's teal Mk42 became haze grey, its pleasure-boat
  speedboat a patrol boat. A per-vehicle `skip` regex keeps glass and lamps; `protect` keeps glowing strips; fabric
  skins take `bolts=False`.
- **Imported models with shared, stretched UVs** (the A-10: its halves on the same texels, 4 x 2 texel patches over
  20 px panels) go through `refit_lib.rebake_imported(geo, texture, texels)`: every face gets a rectangle sized by the
  face, its old paint resampled into it and its UV moved with the same signs, so it looks as it did and can then be
  weathered and marked per side. Keep the original geometry and texture beside the script (`a10_base.geo.json`) so it
  re-runs from them.
- **Thin cubes painted with a cut-out picture** (the A-10's fan disc: a 20 px square, 1 px thick, painted with a round
  fan whose corners are clear) keep only their two big faces. Their edge faces stretch the whole picture over 1 px; given
  texels of their own by the rebake they turn solid, and the A-10's square rim and corners stood out of the nacelle,
  turning with the fan (2026-11-19). After a rebake, look at a render of every part that spins or sits inside another.
- **Local stains** (`stains=[...]` in a detail config): a colour at a strength, limited by bones, facing, a centre and
  radius, a height, broken into patches (`noise`) or run in streaks (`streak`, down or along a direction). The BD-40's
  soil, rust, soot and oil; the A-10's gun gas, exhaust and fluid streaks; the Compass Ghost's darker grey in soft
  patches over the upper surfaces.
- **Lettering on a small plate:** `bake_unique(..., boost={"label": 4})` gives a material's faces four times the
  texel density; draw the text with Inter at the plate's texel size. Check which side faces run back to front in a
  render of both sides before mirroring anything (neither does on the BD-40's engine).
- **Edit Blockbench files by the line.** A json rewrite of an imported model reformats every line (the A-10's file went
  from tabs to a 79,000-line diff); `design/a10/articulate.py` changes only the line it needs.
- **Regenerating a model rewrites its hitbox.** Generators write their own coarse `obb` rows: run
  `design/hitbox/audit_hitboxes.py --write <id>` after every regeneration, or the tight boxes are lost.

## 6. Behaviour

- **Server first.** World changes happen on the server only. Respect the world border, spawn protection and
  adventure mode, and call `PlayerBlockBreakEvents.BEFORE`/`AFTER` for every block, so claim mods apply.
- **A vehicle container sets every vehicle down on the ground where the box stood.** It spawned every vehicle half its
  height up; a jet fell with no pilot, its flight model turned the nose into the fall, and it landed nose first, and
  the artillery (the sentry first, 2026-11-20) has no drive to fall with, so it hung in the air
  (`VehicleContainerBlockEntity.deploy`). Only when something low beside the box is in the way at ground level does it
  start above and settle. Artillery now has weight too (`tickArtilleryGravity`: straight down, never pushed sideways
  or up), so a gun whose ground is dug out falls. `VehicleBoxLandingClientGameTest` unboxes every vehicle with an
  assembly recipe and checks it rests on the ground, level, where the box was: add each new vehicle to its list.
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
- **Powered mounts slew.** `"traverse_rate"`/`"elevation_rate"` (degrees per tick) in the turret data turn the
  turret toward the gunner's view on the server at those rates (`tickTurretSlew`); the view stays free and the HUD
  draws a bore marker where the gun points. Don't pin such a gunner's camera to the barrel: the view would sit beside
  the house, looking at its wall, while the gun turned. Use `"transform": "turret"` on the seat's camera (it turns with
  the turret, not with the gun's elevation), placed where nothing of the vehicle fills the view: the BL-132's camera
  behind its sight tower had the tower filling half the screen; it now sits on top of it.
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
  dependency that `-PwithPack` does not load. The Chinook does the second with the shared cabin
  (`vehicle/cabin`, `CabinVehicle`). Lessons from its first play-through (2026-11-20, `Ch47RampDoorClientGameTest`):
  - **Movement aboard collides with the cabin only, not the world.** Count feet as aboard (`cabinAboard`) only where
    the cabin has something under them. A ramp's aboard zone ran 0.3 m past its lip. People stepping on there had no
    floor, fell, and were let go inside the ground. When someone is taken aboard, set them on the tread they stand in
    (`CabinClient.standingHeight`): collision ignores a box the body already overlaps.
  - **A click handler that cancels `Minecraft.startUseItem` must set `rightClickDelay`** (vanilla sets it first thing).
    Otherwise the held-key repeat calls it again the same tick and every tick after. One click on the ramp panel
    cycled the ramp twice, and placing a furnace opened it at once.
  - **A door's aboard zone must hold a person through the whole jump that boards them.** The CH-47's crew door counted
    feet aboard only up to 0.3 m over the floor, so a jump in from the ground (taken aboard on the way up) left the
    zone at its top. The player was let go in mid-air outside the hull and fell back (2026-11-21). Count it to the
    top of a jump (`ChinookEntity.cabinAboard`). Give a folded-out step a shell box so the player can stand on it.
  - **A helicopter that holds its hover for its crew keeps its engines on every client.** The pilot's client cleared
    its own engine state as they pressed dismount and again on the dismount sync. It heard the shutdown play while the
    server held the hover (the CH-47, 2026-11-21). Any path that clears a vehicle's control state must ask
    `holdsHoverWhenPilotLeaves` first (`VehicleEntity.releasePilotControls`). Test it tick by tick after getting up,
    not only at the end.
  - **An open doorway has nothing to hit.** A click through it went on to the vehicle's own "right-click boards it"
    and sat the player in the pilot's seat. Route it to the door (`CabinVehicle.cabinControlOnRay`).
  - **People standing in the cabin are crew aboard** (2026-11-21: "it should still hover even if the pilot gets out of
    his seat"). Count them for the helicopter's autopilot (`hasCrewOffSeats`), and sync it, since the client runs the
    same check. Keep the engines on when the pilot gets up in flight (`holdsHoverWhenPilotLeaves`). Bridge the
    couple of ticks before the cabin hears where they stand. Re-send the engine state while the hover holds: the
    client wrote its own copy while it was predicting, and only changes are sent. `Ch47CrewHoverClientGameTest`.

  Lessons from the two-player play-test (2026-11-25: one player flying, one walking about inside, two pack clients
  over LAN, section 9):
  - **Play damage with a survival player.** The hull's ramming (`tickRammingDamage`) hit everyone inside its boxes, so
    a Chinook lifting off killed the person standing in its cabin ("crushed by a vehicle"). Every earlier trial used
    creative players, who take no damage, so it never showed. People aboard are carried: the hull's ramming and its
    push-out pass them by (`VehicleEntity.carriesAboard`, from `CabinNetwork`). `Ch47GroundIdleCrewClientGameTest`.
  - **Everything vanilla reads from a player's world movement is wrong aboard.** Their world movement is the vehicle's
    flight. Seen from the other client, a person standing still on the ramp at 23 m/s ran flat out; their footsteps
    were silent on both clients (vanilla's step sounds come from `Entity.move`, skipped aboard); and the engine they
    stood beside was Doppler-shifted as if it flew away from them. Limbs swing to the walk in cabin coordinates
    (`LivingEntityCabinWalkMixin`, `CabinClient.remoteWalked`); each client plays everyone's steps aboard from their
    cabin-coordinate walk (`CabinClient.playStep`: the floor's sound, or the placed block's); people aboard hear the
    interior engine mix with no Doppler, as the seated crew do (`HelicopterEngineAudio.isAboard`). The local player's
    own move is placed on the pose they were last placed on, so view bobbing reads their walk too.
  - **From inside, the view is never on the vehicle itself.** The eye stood in the vehicle's entity box, so every
    right-click in the cabin went to "right-click boards it" and sat the player in the first free seat (the pilot's,
    or the co-pilot's while the pilot flew), and an empty-handed punch hit the hull. The pick aboard is filtered
    (`LocalPlayerCabinPickMixin`, `CabinClient.filterPick`): not the vehicle, and nothing in the world behind the
    walls. A seat is taken by pointing at it (`CabinVehicle.cabinSeatOnRay`), and every control and seat has a prompt
    in the action bar (`cabinControlPrompt`), as the FSV-6's lockers have; outside, the hull says what a click does
    (`Ch47Hints`).
- **Fitted blocks are drawn two ways; test both.** A block with a real model is drawn with `submitMovingBlock`. A
  chest, trapped chest, ender chest or shulker box also reports `RenderShape.MODEL` in 26.1, but its block model is
  empty (its look comes from the block entity's renderer), so that draws nothing. Draw those through
  `BlockModelResolver.update(state, blockState, BlockDisplayContext.create())` and `BlockModelRenderState.submit`, as
  an enderman draws its held block (`SupportVehicleRenderer.drawSpecialBlock`). Fit both kinds in the visual test
  (`SupportLookClientGameTest`): the FSV-6's chests were invisible in every bay until then.
- **Blocks placed in a moving cabin keep their light, look and sound (2026-11-26).** A block in a cabin grid is drawn by
  the vehicle, so nothing of the world's gives it any of these. `CabinBlockEffects` (client) gives them back for every
  placeable block, as in the world:
  - Light: each block's own level (`getLightEmission`) lights its cell and a level less a cell away, and washes the
    walls in its colour (`VehicleLightsLayer`, the 16 brightest). Blocks that glow as drawn (magma) are full bright.
    Before, only a lit furnace lit anything; lanterns, torches and glowstone aboard were dark at night.
  - Look and sound: vanilla's `animateTick` runs for each block as often as for a placed one that far from the player,
    told it stands at its cell's spot in the world. `ClientLevelCabinEffectsMixin` moves the particles and sounds it
    makes onto the drawn block. The particles are then carried with the cabin each tick. Given only the aircraft's
    speed, a torch's smoke slowed in the world's air and streamed aft at 30 m/s.
  - Block entities: chests, ender chests and shulker boxes open their lids, and an enchanting table's book opens and
    turns to whoever is near, in the cabin's frame.
  - Placement follows the face clicked (`CabinBlocks.placedState`): wall torches, hanging lanterns, grindstones on the
    floor, end rods and amethyst out of the face. Candles light with flint and steel and go out by hand.
  - Check with the dev tools: `/jegcabin put <i j k> <block>` fills cells, and `/jegsounds <s>` logs every sound with
    its cabin cell.
- **Draw people standing in a cabin tilted with it, about their eye (2026-11-26).** The cabin places the eye and puts an
  upright body under it (`CabinMath.feetWorld`), which suits the camera, but the body drawn that way hangs out of a
  banked cabin: in a CH-47 banked 60 degrees, someone standing aboard hung 1.4 blocks to the low side, through the wall
  and the blocks stowed there. `VehiclePassengerRenderHandler.tiltWithAircraft` tilts them by the hull's attitude about
  the eye, as it tilts seated riders about the hips. Test with `/jegfly steep <speed> <radius>` (up to 60 degrees) and a
  second client watching.
- **Walls proud of the cells.** Where a real block (a bed) fills a cell up to the room's edge, the wall that closes
  that edge must stand a little outside it (0.4 px), not on the same plane, or the block shows through from outside.
- **Real blocks only where needed.** Place a real block only where a feature needs one (a bed for sleep and spawn).
  - Block players from breaking it (`PlayerBlockBreakEvents.BEFORE` on registered positions).
  - Use up the fitted item if the block was destroyed some other way, so nothing doubles.
  - Lock that fitting while the block exists.

### Towed guns

- **Draw a towed vehicle from the tower's smooth position.** The server moves it once a tick and the client eases
  between packets, while the truck its driver moves is smooth: drawn from its network position, the gun trailed a
  block behind and jumped, and the tow bar stretched and snapped (2026-11-19). Its renderer and the bar take the pose
  from `SupportVehicleEntity.clientTowPose` (the server's own tow geometry, `towPose`, applied to the truck's
  interpolated position each frame); `TowSmooth` measures the bar's length between frames.

Lessons from the 105 mm howitzer (`design/howitzer/`) and the Type-63.
- **Towing is the FSV-6's hitch; a gun says how it is towed.** `VehicleEntity.towEye()` gives the lunette in vehicle
  data coordinates, and the hitch puts that point on the pintle with the gun trailing away from the truck, so it goes
  trail first. Without a tow eye a vehicle is pulled nose first (the LAV-150). `canBeTowed()` refuses the hitch (a
  gun only packed and unmanned), and the hitch lets go if it turns false.
- **Deploy before firing, pack up before towing.** `canFireWeapons()` gates the shared gun path (ammunition is not
  used), and `turretTravelLock()` drives a slew-limited turret back to yaw 0 and level whatever its gunner looks at.
  The tow key (N) is general: `hasTowKeyAction()` and `towKey(player)` let a gun's crew deploy and pack up with it.
- **Swing trails and fold spades from synced progress**, with the open pose's hitboxes in `obb_deployed`. A diagonal
  trail needs several boxes along its length (the generator's `trail_segments`), or one box takes in the ground
  between the trails.
- **A long-recoil gun at high elevation strikes the ground with its breech.** Shorten the recoil with elevation
  (`HowitzerRig.recoilLength`, as real variable recoil does), and check the breech against the ground and the trails
  over the whole elevation range in the generator's validation.

### Boats

Lessons from the speedboat (`design/speedboat/`); the hull's behaviour is its data's `"boat"` block (`BoatInfo`), not
code keyed to its id.
- **Float it at its waterline:** `"draft"` (blocks the keel sits under the surface). Without it buoyancy settles the keel
  a little above the water and the antifouling shows; with it the hull rises until its waterline meets the surface.
- **Keep "afloat" separate from buoyancy.** A hull on the plane rides up out of the shallow buoyancy probe; when that
  also decided whether the engine pushed, the speedboat fell back to its crawl ashore every few ticks and never got
  past 30 km/h. Water anywhere within a block under the keel counts as afloat.
- **Planing is drag, not a speed cap:** high `"displacement_friction"` below `"plane_speed"` (set the thrust so the
  displacement speed settles just above it), low `"planed_friction"` above, with hysteresis so a bob doesn't drop it
  off the plane. A fixed cruise speed (the old speedboat's) also cancels currents; the keel (`"keel_grip"`) only removes
  sideways slip.
- **Beaching:** measure the shore from the waterline, and lift the hull yourself: vanilla only steps an entity that
  stands on ground, which a floating hull never does. Probe from the front of the box the hull collides with: a bow gun
  overhanging the stem is in it, and it met the bank before a probe started at the entity box's edge. Ashore, scrape it
  to a stop and let it crawl both ways (to back off, or reach the water from the workbench); no impact damage while
  beaching.
- **Place effects from the hull's own boxes** (its `"body"` OBBs). The entity box is square: spray put at its edge
  spawned inside the hull and nobody saw it.
- **The driver keeps the gun when alone:** the turret's `"fallback_seat"` (`VehicleEntity.turretSeat()`,
  `weaponUsableBySeat`). Weapons on the turret's seat follow it to the fallback while that seat is empty. Only a player
  or tagged enemy crew mans the gunner's seat: a mob carried there (yftm.22's passengers) leaves the gun with the driver.
  The BMP-2 (gunner, index 7, fallback 0) and the speedboat use it; the seat panel names the turret's seat "Gunner".
- **Passengers at firing ports face out of them:** seat yaw limits are relative to the hull, so a left-side port seat
  gets `min_yaw` -140, `max_yaw` -40, a right-side one 40 to 140 (seat `x` > 0 is the left). Their view comes from the
  seat's `zoom_camera` whenever it has `use_fixed_camera_pos` (always, not only zoomed; `usesFixedPassengerCamera`),
  so put that camera at the port **just outside the hull side**; the seat itself stays inside. SBW's cameras sat inside
  the side armour, and the squad saw only the hull's faces. (The server reports a rider at the hull's centre in x and
  z; check positions with mcpfabric's `player.getState`, which is the client's.)

### Weight and feel (the user's rule, 2026-11-17)

"A tank should feel heavy to move compared to a lighter vehicle. Every movement of a vehicle should have some sound
effect and tactile feedback." For every ground vehicle:
- **Give it a `"mass"`** (tonnes, the real machine's). `VehicleEntity.inertia()` = sqrt(12 / mass), clamped 0.4–1.15,
  scales the throttle's wind-up, the brakes, the steering's response and ground friction, with the thrust rebalanced so
  the top speed stays where the data puts it. Measured (`VehicleWeightClientGameTest`): the 10 t LAV-150 reaches 80% of
  top in 28 ticks and stops in 21; the 55 t YX-100 takes 63 and 44.
- **Top speed is where friction levels off:** 0.15 × `max_forward_speed` × f / (1 − f), about 0.6 × the max for f
  0.8, and in practice 7–18% under that. Set `max_forward_speed` from the brief's road speed and measure it.
- **The hull rides its suspension** (`VehicleFeel`, drawn in `vehicleAxis`): squat pulling away, dive braking, roll out
  of turns, a drop on landing; a heavy hull rocks slower and settles later. The crew's camera takes 40% of the rock, a
  speed rumble (deeper in a heavy hull) and the landing jolt; not through a zoomed gun sight (stabilised).
- **Every movement has a real sound** (`design/ground-vehicles/prepare_movement.py`, `VehicleFeel`): the turret's
  electric drive and the gun's elevation (armour), servos (HPJ-11, sentry), handwheels (towed guns), the brakes when the
  driver brakes (`driverBraking()`, synced; speed changes seen by a client are smoothed and can't tell braking from
  coasting), a heavy knock landing from a drop, a splash driving into water; with the engine's start, stop, idle, load
  and cab loops, the track clank or the tyres on gravel.
- **Set the acceleration with the mass.** The FSV-6 kept an acceleration of 0.01 (the armour's is about 0.03): with its
  15 t it wound up slower than the 55 t tank. `VehicleWeightClientGameTest` now drives the LAV-150, FSV-6 and YX-100 and
  asks that they come up to speed in order of weight; add a vehicle there when you tune one.
- **Boats** (`VehicleFeel`, `GroundVehicleAudio` "hull"): a planing hull banks into a turn (inward, against a car's body
  roll), pitches and heaves on the chop more with speed and rocks at rest; the water along the hull is heard by speed
  through the water (a swimming land hull's against its swim speed). A planing hull rides out of `isInWater()`: count
  water just under the keel too.
- **Towing shows its hitch:** the FSV-6 draws its tow bar (`SupportTowBarLayer`), an A-frame to a towed hull's nose or a
  drawbar to a gun's tow eye, built camera-relative from both entities' interpolated positions.

### Suspension moves the hull, not the running gear

`VehicleFeel` rolls a land vehicle's hull out of a turn and dives it on braking. Applied to the whole model it pushed the
outer track into the dirt in a hard turn (the BMP-2 and PLZ-05, 2026-11-19). A model whose running gear (tracks, road
wheels, wheels) are bones of their own beside the hull is listed in `AbstractVehicleGeoRenderer.SPRUNG` with its sprung
bones: the suspension turns those about the hull's pivot and the running gear stays on the ground. Keep the hull and the
running gear as sibling bones under the root in a new model, and add it to `SPRUNG`.

### A gun on automatic is its own

A crewed gun with a fallback seat (the driver keeps the gun while the gunner's seat is empty) must not fall back while it
fights on automatic: the driver could lay and fire a gun that was meant to be working by itself (the speedboat,
2026-11-19). `AutoDefenceEntity` turns `turretFallsBack` off while automatic. Anything that asks "does this seat have the
gun" must decide whether it means *now* (`turretSeat()`) or *can it ever* (the turret's seat or its fallback): a boat
takes its heading from a driver without a gun (`yawFollowsRider`), and asking "now" swung the speedboat to its driver's
view whenever the gun was on automatic or a gunner had it.

### Projectiles the player must see

A round too small or quick to see still has to read on screen. A canister's balls flew with a no-op renderer and the
user saw grapeshot as doing nothing (2026-11-19). A round faster than about 10 blocks a tick usually strikes within a
tick, so drawing the entity itself shows nothing (its client copy sits at the muzzle, then is removed): trace its path
client side as it appears and let that hang a moment, fading (grapeshot's streaks did, until it was replaced); throw
something where it strikes (sparks on armour, dust on the ground), and let it mark the world where it is real
(`VehicleGunBlockDamage`). Vanilla draws an entity as small as a ball only within 16 blocks.

### Incendiary and airburst shells

The GS shells became incendiary (2026-11-19: "explode over a target leaving flames below it"). `ShellEntity.Incendiary`:
once armed, a height fuze follows the path a few ticks ahead to where it comes down and bursts `burstHeight` above that,
back along the path (at most `maxLead` short on a shallow path); struck first, it bursts where it strikes. Its burning
fragments are shells too, each given the velocity that lands it on its own spot in the circle round where the shell
would have come down, so they rain onto the target whatever the angle it came in at, and are drawn client side as flame
and smoke (`trailFire`, always-visible particles: the entity renderer is a no-op). Fire is set only where the shell may
break blocks: the `tnt_explodes` rule (the HE shells' explosions follow it), spawn protection and claims through
`PlayerBlockBreakEvents.BEFORE` (`ShellEntity.setAlight`); what the fragments strike burns either way. The fuze's dud
range covers the fire's circle (`Warhead.reach`). Test the burst height and the fire with `IncendiaryShell`, watched
from beside the target with the player standing near it (the client draws nothing past its view distance).

### Glows inside a part, under shaders

Draw a glow the model holds (a jet's nozzles) the way every vehicle light is drawn under a shader pack:
`entityTranslucentEmissive` with the premultiplied `light_glow_soft.png`, its brightness as opacity, not additive
`RenderTypes.eyes`. The user saw the A-10's additive exhaust glow drop out as terrain loaded in, leaving black nozzles
(2026-11-19). Keep additive `eyes` for the vanilla look without a pack.

### Spinning parts of an imported model

Before spinning a bone, check in a render what it is: the A-10's `qianwaiyuan` ("front outer rim") was spun and
blurred as its fan from yftm.17 on, but it is the intake's eight-panel lip, so it turned out of line with the
cowling and its blurred copies stood out round the intake (2026-11-19). The fan was the disc its blades are painted on
(`qianzhou`), on the engine's axis. A part with eight-fold symmetry shows nothing turning but its texture; only spin
what really turns, and blur only that.

### Speeds shown

Show how fast a vehicle actually moves (its position against last tick's, eased), not its velocity field: a jet's
velocity and its motion part company (the A-10's readout looked wrong to the user, 2026-11-19). The cards' speed for
ground vehicles was already measured this way.

### Smoke and rooms

Smoke puffs (SmokeFx, the blast renderer) have no collision. An exhaust under a deployed room sends its smoke up
through the floor: vent it outside the deployed footprint, and let the renderer drop puffs inside the room
(`SupportVehicleEntity.insideClientRoom`, the FSV-6's). A vehicle with a walk-in interior does both.

### A load the vehicle carries

Show what a working vehicle is carrying (the BD-40's earth on its blade): a bone of its own (not inside a bone a test
captures, such as `finish_blade_tilt`), scaled from where it rests, driven client-side from synced state and eased frame
to frame. Drive it from what the player sees happening (the dozer is cutting) rather than a figure that measures
something else (the dozer's synced load is the hold's credit and stays near 0.15 while cutting a bank).

A load hanging under a helicopter (the Chinook's sling) pulls on the airframe through `externalAcceleration`. The hover
hold carries that pull (the user, 2026-11-21: hands off, a loaded Chinook hovers as an empty one does). Take the hold's
error on the speed *before* the pull, and add the pull back as lift. With only the error term it sank 2 m/s; with the
error on the pulled speed it settled climbing at the pull's own rate.

Draw a rope with weight, not as a straight tube (the user, 2026-11-21: "the rope should have gravity attached to it so
it naturally slings around"). `SlingRope` is a client-side Verlet chain under real gravity with air drag. Its links only
pull, so the rope can go slack. Pin it at both ends to the positions the server's physics hold. Let a free end hang with
a hook's weight. Tick it at 20 Hz in the vehicle's client tick, and draw it eased between ticks with its pinned ends
placed exactly. A rope hanging still is straight, and it bows only in motion: check that by flying past a camera
(`Ch47SlingRopeClientGameTest`), not by its sag at rest.

A helicopter coming down onto another vehicle rests on its roof (`restOnVehiclesBelow`, in `moveHelicopter` so the
pilot's predicting client does it too). Vehicle-against-vehicle resolution only stops sideways closing speed, so a
Chinook descended straight through an LAV-150 and stuck there.

### Handing the controls over (2026-11-25)

A client predicts a vehicle's movement only while its player drives it. Two handovers glitched on the CH-47:
- **Taking the controls.** The client's flight model kept what it held when that player last flew: back in the pilot's
  seat from the co-pilot's, the nose snapped 10 degrees down and the aircraft lurched off at its old speed. Seed the
  model from the vehicle as it is when prediction starts (`VehicleEntity.seedHelicopterPrediction`).
- **Leaving them in flight.** Forty forced state syncs after the pilot got up each set the position and the previous
  position together, so the aircraft (and the camera of anyone in its cabin) stood still between ticks and jumped on
  each: a 20 Hz judder for two seconds. A helicopter that flies on for its crew hands over with one sync that eases;
  the former pilot's client flies it on along its velocity until the server's states arrive; synced states are eased
  toward where the aircraft will be after the easing (position plus velocity times its three ticks), not where it was.
  Measure with a frame trace (`/jegfly trace N` logs the drawn pose each frame) rather than by eye.

### A pilot's head (2026-11-26)

A helicopter pilot's mouse is the stick, so the pilot looks about while free look (C) is held (`PilotHeadLook`). The
head turns within the cockpit, up to the seat's yaw and pitch limits, and turns with the airframe. Before, free look held
a world heading, so in a turn the cockpit swung round a fixed view, and letting go snapped the view back to the nose. Now
it eases back over about a fifth of a second. This applies to every helicopter's driver seat with a pinned view
(sensitivity 0, fixed camera). Jets keep their own free look: their view rolls with the hull.

### Light from lamps on long faces

A vehicle's lamps light it by drawing it again, washed, with the light worked out at each face's corners. A long
face (a cable run, a ceiling panel) then carries one end's colour all along it (neon green lines from a green jump
light) or, with every corner out of a lamp's reach, stays black round it. `VehicleLightsLayer` lights such faces near a
lamp cell by cell (0.4 m), laying the cells on the two triangles the face is drawn as: a bilinear patch on a face that
isn't flat sank behind it in dark jagged streaks.

### A transport helicopter on the ground (2026-11-25)

The light helicopters cut their engines whenever they stand on the ground with the collective down. A transport can't:
the CH-47 cut its engines the moment its twenty-second start finished unless the pilot held the collective through it,
and again at every touchdown, so troops walking off the ramp cost the pilot another start. A helicopter that returns
`idlesOnGround()` keeps flight rpm at flat pitch on the ground until the pilot holds descend there for two seconds
(counted only after a second on the ground, so the hold that landed it doesn't count), or leaves the seat. Its HUD says
which: starting with a progress bar, ground idle with the shut-down key, shutting down with a bar. A heavy airframe also
climbs and descends slower (`helicopterVerticalRateScale`, 0.6 on the CH-47: 6 m/s up, 6.6 down). The light helicopters'
11 m/s descent, held, set off the sink-rate warning (the missile beeper) in a plain descent.

### Destruction and wrecks

Nothing vanishes when destroyed (2026-11-17, `VehicleEntity.goesDownAsWreck`). Aircraft go down burning; land
vehicles and guns cook off where they stand (30 ticks of smoke and sparks), go up in the vehicle's `destroy` blast, throw
out what was aboard, and burn as a charred hulk for 10 s before breaking apart, upright and without spinning
(`GroundWreckClientGameTest`). Boats sink (`sinksAsWreck`, `BoatWreckClientGameTest`): they burn on the water losing
way, the fuel goes up, then they flood by the stern, go under bow up with air bubbling, settle on the bottom and break up
there; no fire under water. Test a sinking in a glass-walled pool deeper than the lake, from the rim looking down (under
a shader pack the glass hides it from the side). A rotary gun heard as a loop should carry as far as the aircraft's guns: the HPJ-11 mixes a near and a far loop on
`AircraftSoundRange` (`CiwsAudio`); the generic vehicle fire loop fades out by 56 blocks and cuts off with no tail.
Automatic guns go through `fireUnmanned`, a path of their own: anything manned fire does there (the report, the
flash, the trail) must be done in it too. The sentry on automatic fired in silence until 2026-11-18. Code that runs on
a live vehicle must
check `isWrecked()`: a wreck still exists, can be shot and seen, but has no controls.

### Doors and hatches on SBW models

SBW's door bones pivot mid-door, not on their hinges. Don't move pivots by hand in the generated model (a refit run
overwrites it); turn the bone in the renderer about the real hinge with `swingAboutHinge` (rotate about the pivot, then
translate by (I - R)(H - P), in GeckoLib's X-mirrored frame). The BMP-2's rear doors open while someone boards or leaves
(`VehicleDoors`, from the count of those the doors let in). Check the swing in a screenshot from behind at mid-travel.

The same applies to hinged parts of our own models. A part modelled reaching aft (model +Z), such as the Chinook's
ramp, lifts its far edge with a **negative** X turn in GeckoLib. The generator's review renders use the opposite sense,
so the Chinook's "closed" ramp looked right in `review-ramp-closed.png` and hung down in game. Its crew door's lower
half, on a hinge along the sill on the right (+X authored), folds outward with a **negative** Z turn: positive laid it
across the cabin floor. Check every pose in game.

**Doors that move are seen and heard moving** (the user, 2026-11-21). The CH-47 is the worked example:
- **Ease every motion.** Ramp the speed up and slow onto the stop on the server, as the Chinook's ramp does
  (`rampSpeed`), and ease the drawn fraction with smoothstep in the renderer (`Ch47Renderer.ease`). A constant rate
  starts and stops like a switch.
- **Model real mechanisms.** The crew door is split like the real one: its upper half slides aft, then its lower half
  folds down into the boarding steps. The two motions overlap in time.
- **Give each motion a real recording** (`design/chinook/prepare_audio.py doors()`). Use a loop while a powered part
  moves, played by the client from the synced angle (`Ch47CabinAudio`); track the last angle yourself, since the
  entity's own "previous" value is overwritten by the time the client's tick sees it. Add a lock clunk at each stop, a
  slide at the start, and a latch or step thud when the motion completes. Don't use vanilla door or piston sounds.

### Technicals: weapons in a pickup's bed (2026-11-26)

The 4x4 pickup and its three technicals (`design/pickup/`) are the worked example of a light civilian chassis carrying a
weapon:
- **Clear the cab at the lowest forward elevation.** A weapon low in the bed fires into its own roof when laid ahead.
  SBW's TOW stood its tripod on the bed floor, its tube 0.26 blocks under the roof: the refit puts it on a pedestal half
  a block up (`refit.py raise_tow`). The rocket pod lays from 10 degrees up, where a ripple over the cab clears it.
  `PickupClientGameTest` fires both straight ahead and checks the truck takes no damage.
- **A driver who drives keeps the cab's view.** A seat whose `zoom_camera` has `use_fixed_camera_pos` always looks
  through that camera in first person. The rocket technical's driver lays the pod from the cab, by the fire-control
  panel, so their seat has no fixed camera; the standing gunners at the 12.7 mm and the TOW have one at their sight.
- **A real-size cab is lower than a seated Minecraft body.** The pickup's cab is 1.2 blocks inside, and a seated
  player is about 1.4 tall from the hips up. Put the seat where the eye sits at a real seated height (about 1.35 above
  the ground in a pickup: seat `y` -0.25 with the floor at 0.53). The legs then sit below the floor, hidden by the doors,
  and the head stays under the roof from every side.
- **A mob in a cab seat sits where a player would** (2026-11-26, every vehicle). Riders other than players sit by their
  own attachment point (so a mob's feet don't sink through an open seat), which put a villager 0.6 higher than a player
  in the pickup's cab, its head through the roof. In an enclosed seat that is drawn (`enclosed`, not `hide_passenger`)
  a mob now sits no higher than a player (`VehicleEntity.seatsMobInCab`; `PickupCabClientGameTest` holds the head under
  the roof). A villager has no seated pose: on a vanilla client its legs and robe are left out in a cab seat
  (`VillagerModelSeatedMixin`); under the pack, Fresh Animations seats it itself and its shins can show below the sill.
- **Seats set at a floor: `"floor_seats"`.** The lift that stands a cow on the AH-6's bench (at a player's hips) floated
  the pickup's bed riders 0.6 above the bed, where a seat's height is the floor a player's feet rest on. List such seats
  in `"floor_seats"` (the pickup's bed, the technicals' gunners) and a mob rides them as a player does.
- **Doors answer to the seats behind them.** `VehicleDoors.open` takes the count that swings them: the BMP-2's whole
  squad, the pickup's cab alone (`VehicleEntity.cabOccupants`). Counting every passenger swung the pickup's cab doors open
  for a gunner climbing into the bed.
- **A backblast thins out around the camera.** A rocket's backblast puffs fade within 4 blocks of their edge
  (`MissileBlastRenderer` `nearFade`): the rocket technical's chase camera sits where the backblast drifts, and a ripple
  turned that whole view grey. Onlookers still see the full cloud; muzzle smoke and wrecks' smoke are unchanged.
- **One engine's recordings for a family.** The four pickups share `sounds/vehicle/pickup/`
  (`GroundVehicleAudio.soundFamily`), and the cab doors play a real door's latch and slam as they swing.
- **Take the memes out in the refit.** SBW's pickup carried a meme figurine on its dashboard, its meme name on the
  doors and tailgate and a meme number plate. `refit.py strip` drops the figurine's bones and the decal faces by the
  texture region they show, and repaints the badge and plates before the rebake. `rebake_imported(..., boost=)` gives a
  bone's faces more texels so a plate's lettering stays legible.

### Retiring a vehicle

Keep the entity type registered for one release as a `RetiredVehicleEntity`: on its first server tick a saved one
drops a chest item holding its hold (overflow loose) and removes itself. Remove its assembly recipe and icon entry at
once, and the type in the release after (the waveforce tower, retired 2026-11-16; the laser tower and the light combat
vehicle, 2026-11-17).

### Base defences (automatic fire)

The HPJ-11 and the sentry share `AutoDefenceEntity`: manual or automatic with N, a target picked by priority within
reach and in sight, led at the round's speed, laid through the turret's own slew (`turretAutoLay`), fired through
`VehicleEntity.fireUnmanned` once on. The server can switch automatic fire off and caps it per chunk (`Config`).
- **Decide "hostile" when it happens, not when you look.** A missile's launcher may be gone by the time a defence sees
  it (a crewman 150 blocks from any player despawned and the rocket was never engaged): `VehicleMissileEntity` records
  at launch whether the enemy fired it.
- **Never fire at anything friendly:** players, what they fired or drive, villagers, pets and passive animals; a
  sentry checks its line of fire before every shot, an air defence never lays below its floor (5°).
- **Give a new gun its armour piercing** (`BallisticProtection.explicitArmorPiercing`): an unlisted gun pierces almost
  nothing, and the HPJ-11's rounds burst on an AH-6 for no damage.
- **A remote gun's flash** is the medium one (`_hmg`, `_rws`, `_ciws`, `vehicle_sentry_*`): the cannon flare or the full
  machine-gun flash filled the operator's camera.

### Fixed-wing aircraft other than the A-10

`JetFlight` was written for the A-10; another aircraft sets what differs in its data's `"aircraft"` block
(`AircraftInfo`; the bush plane, `design/bushplane/`), not in code keyed to its id:
- `"thrust_lapse"`: the share of thrust lost by top speed. A turbofan's 0.12 is the default; a propeller loses far
  more (the bush plane 0.45), so it pulls hard off the brakes and tops out gently.
- `"fixed_gear"`: the wheels stay down, with no retraction, gear sound or extra gear drag (the top speed in the data
  already includes them).
- Flaps (`"flap_stall_factor"`, `"flap_speed_factor"`): below that multiple of the clean stall speed they run out and
  scale the stall and rotate speeds, at extra drag. That gives a short take-off and slow landing without lowering the
  stall speed of the clean wing. The renderer droops the flap bones from the speed every client sees.
- `"wing_camera"`: where the reversed third-person view's action camera sits on top of the left wing. The A-10's
  spot (7.6 out, 3.75 up) floated past the bush plane's wingtip, looking into the wing.
- `"strike_points"`: the wingtips, nose and tail checked against the terrain, in data coordinates. The A-10's
  points on a smaller airframe hit the ground in mid-air, or miss a wingtip that is in a tree.
- Spoken warnings are each jet's own recordings: `VehicleSoundHelper.jetOwnSound` keeps the A-10's "pull up" out of
  other cockpits, while generic sounds (wind, runway roll, the stall horn) fall back to the A-10's.

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

- **A helicopter's crew-served guns are heard as what they are.** Every helicopter gun plays the minigun loop unless
  the vehicle names a report for it (`ownGunReport`). The CH-47's M240 door guns use `VehicleGunReports.DOOR_GUN` (a
  real 7.62 mm Negev's report, shot by shot), and with that they also fire the long-range rounds the ground guns do.
- **Each gun can have its own ammunition** (`ammoContainer(weapon)`; the hold by default). Two guns sharing a hold
  share one supply, and a reload takes up to a whole magazine: the Chinook's first door gun to reload took all 128
  rounds, leaving the other empty. Its door guns each have a 9-slot can, opened by that gunner's hold key
  (`openHoldFor`).
- **A rifle-calibre gun with the gunner's eye just behind it** (a seat gun, `seatGunMuzzle`) gets the small muzzle
  flash; the medium one still filled the Chinook door gunner's view.
- **A key the vehicle reuses for something of its own** (the Chinook's ramp on X): count presses with
  `takeDriverSwitchPresses`, not by reading `landInput()` once a tick. A press whose down and up packets reach the
  server in the same tick never shows there.
- **Projectiles.** Fire through the existing long-range paths, where projectiles tick wherever they are, load
  terrain ahead, are never saved with a chunk and end at their range. Send tracers to players up to 1024 blocks
  away.
- **Guns that fire in an arc** (mortar, Mk42, later the artillery) fire a `ShellEntity`: it falls by a
  `ShellBallistics`, keeps flying through unloaded terrain, bursts with a `ShellEntity.Warhead` (blast falloff, direct
  hit, block power, optional proximity fuze), and the gunner's HUD predicts it with the same ballistics (`ShellAim`).
  A vehicle gun opts in with `firesShell`/`fireShell`; the shared gun path still handles ammo, drum, reload, cooldown
  and flash. The report is `GunReportPayload`/`GunReportAudio` (`<sound>.fire`, `.fire_distant`, and
  `.fire_interior` for the crew).
- **Gun mounts are `ShellGunEntity`s** (Mk42, BL-132): a subclass lists its shells (one vehicle weapon each, with a
  warhead), muzzles in firing order and recoiling bones; the base fires the next barrel, recoils it from the synced
  shot count, sends the report, and gets the fire-control panel, predictor and bone driver for free. Shell warheads
  can break up (`ShellEntity.Dispense`: cluster bomblets near the target) or burst over it (`ShellEntity.Incendiary`),
  and every shell is a dud within its reach (blast radius or fire circle, +1) of the gun that fired it, so firing at the
  gun's own feet can't hurt it.
  Rocket launchers use it too (the Type-63): `shellsAreRockets` gives the shells a motor glow and short trail, and
  `nextBarrel`/`barrelFiring` set the tube order when the tubes empty in turn and reload together. The base plays
  `animation.<id>.rest`, so write that clip (empty if need be) even when the source model has none; without it
  GeckoLib logs an error every frame.
- **Fire missions** (the howitzer): `FireSolution.solve` flies the shell tick by tick with the gun's own
  `ShellBallistics` from the muzzle's arm about the trunnions, and bisects for the low and high arcs, so the panel's
  numbers are what the shell will do. A gun with several charges returns the selected one from `ballistics()`, and
  the predictor and the flight follow it. Targets come from a player looking through a spyglass (attack marks the
  block, `ObserverMarker`, caught at the start of the client tick because vanilla swallows attack clicks while an item
  is in use) or from `/justEnoughGuns firemission <x> <z>`. Extra panel rows come from `ShellGunEntity.panelRows`.
- **Fire missions are shared** (`FireMissions`): a gun that takes them implements `FireMissions.Gun` (target, reach
  from the observer, crew ping), and an observer's mark reaches every such gun within its own reach (the howitzer
  4.5 km, the PLZ-05 6.5 km). The command and the spyglass go through it.
- **A fire control can lay the gun** (the PLZ-05): `turretAutoLay()` returns the turret yaw and pitch to slew to
  instead of the gunner's view; the slew rates still apply, and the HUD reports LAYING, then ON within 0.05°. Solve
  from the turret ring's centre: the trunnions lie on the line from it along the bore, so the bearing from the centre
  is the bearing to lay, and the solver needs no iteration.
- **Per-weapon rules:** `canFireWeapon(weapon)` gates one weapon (the PLZ-05's gun only at a halt, its roof gun on the
  move); `firesLongRangeRounds(weapon)` puts a ground vehicle's gun on the aircraft guns' far path;
  `seatGunMuzzle(weapon, direction)` places a pintle gun's muzzle from its own pivot along its gunner's view.
- **Weapon ids pick the muzzle flash.** An id without `machine_gun` got the cannon flare (fire and smoke blocks wide):
  the PLZ-05's roof gun first threw a fireball into its gunner's face. Pintle guns and weapon stations (`*_hmg`, `*_rws`: name new ones so) get the helicopters'
  medium flash.
- **The gun's panel goes to whoever lays the gun**, not the driver: `ShellGunEntity.laysGun(passenger)` (the PLZ-05's
  gunner is seat 1). Look seats up with `seatOf(passenger)`, never the passenger list's order: after a seat change the
  two differ (the bore marker compared them).
- **Limit elevation by bearing where the hull is in the way** (`clampTurretPitch`). Check the gun against the hull's
  real top (each cube, sloped plates by their slope) over the whole elevation and traverse range in the generator or
  refit, and write the lowest clear elevation per bearing to the rig (`design/yx100/refit.py`: the tube and shroud
  sampled every px against the hull's faces sampled every half px, 5° steps; the entity takes the stricter neighbour).
  Leave out the mantlet, which sits inside the turret house. Sample faces densely: corners alone, or a fixed count per
  face, leave holes over big plates that read as clear bearings. A refit inherits the source's clashes: SBW's YX-100
  allows −10° all round, but its gun meets the front deck at −3.5° and the engine deck below +3.5°.
- **Lift a dismount point 0.1 off the ground.** Hulls sit a few hundredths into the ground, so a dismount point at the
  hull's own height overlaps the block below and is refused; the player then lands on the roof (the BMP-2's rear-door
  exit, first try).
- **Hatch seats ride the turret** (`seatRidesTurret(seat)`): their data position is in hull coordinates with the turret
  straight ahead, and turns with it. Put an open hatch's eye clear above what is in front of it: the PLZ-05's roof
  gunner first sat 0.07 blocks above the receiver, which filled the view.
- **A gun's crew can own the tow key** (`takesTowKeyFrom`): the PLZ-05's gunner engages the fire control with N.
- **Ground vehicles' guns are heard and reach as the aircraft guns do** (fleet plan Phase 0.3): a gun listed in
  `VehicleGunReports` (the 20 mm, 30 mm, coax and every `*_hmg`/`*_rws`) sends a `GunReportPayload` per shot with its
  report set (`vehicle.gun.<gun>.fire`, `.fire_distant`, `.fire_interior`; `design/vehicle-guns/prepare_audio.py`) and a
  loudness, instead of a looping or one-shot hand-gun sound with linear fall-off, and its rounds go on the long-range
  path (`setLongRangeRound`). A new vehicle gun goes in that table with a report set; where SBW has no far take, the
  script derives one (low-passed, ground-echo tail). `VehicleGunReachClientGameTest` fires each one at range.
- **`setUseConsistentSettings(false)` gives a normal world, not the flat one.** A test needs it to keep its own render
  and simulation distance, but then y −60 is underground: the gun-reach test's vehicles first fired into stone. Build a
  platform high up (y 300), force-loading the area first: a normal world's spawn may be far from it.
- **Follow a far round by holding the entity, not by id.** Out past the chunks the server keeps entities in,
  `getEntity(id)` misses a round that is still flying; the gun-reach test first saw every round "gone" after one tick.
- **Smoke rounds** are `ShellEntity.Warhead.smokeShell`: no blast, a smoke screen where they land
  (`SmokeGrenadeEntity.smokeScreen`, sent to 512 blocks so an observer sees it).
- **Keys read as held (`isDown`) miss a game test's one-tick `pressKey`**: hold them a few ticks, as a player's press
  lasts. Keys read with `consumeClick` (the tow key) take a press.
- **Fill a test's crate within each item's stack size.** A stack over `stacksTo` is clamped, so the extra rounds
  vanish; the Type-63's test lost its range shot this way. Put the extra in the next slot.
- **Missiles steered by the gunner's eye** (the TOW) implement `VehicleMissileEntity.LineOfSightLauncher`: the
  missile is steered onto the sight line every tick, and the wire breaks when the gunner leaves, the launcher is lost,
  the sight loses the missile behind a block, or the wire runs out. Give such missiles a low-smoke trail
  (`VehicleMissileEntity.isLowSmoke`): the standard trail lies along the gunner's line of sight and hid the missile
  for seconds in the first play test.
- **A gunner's camera at a sight sits inside the sight's model.** Hide that bone in the gunner's own first-person
  view (the TOW's `sight`, in the bone driver), rather than moving the camera off the sight line.
- **Carried crew weapons** deploy with `DeployableWeaponItem` (the mortar, the TOW) and pack up on a sneak-click with
  an empty hand; return anything loaded or held in them.
- **Reload times in `VehicleWeaponStats` are scaled by 0.64 for every vehicle** (`vehicleReloadDuration`, 0.7 again
  on aircraft). Divide the time you want by 0.64 (the BL-132's 90 ticks is a stat of 141), and measure it in a test:
  the Mk42's "6 s" reload ran at 3.8 s until its test timed it.
- **Distant impacts** must appear for distant players (`DistantTerrainRefresh`).
- **Feel.** Give each weapon recoil, tracers, impact debris and reload sounds, and heat or cooling if real.
- **Aim.** The aim marker sits on the real bore, and the bore is tested against the model.
- **Test every weapon at range,** not only the one changed. yftm.13 fixed missiles but not helicopter rockets.

## 7. Sound

**Armoured vehicles share one engine audio (fleet plan Phase 0.1, 0.2).** A vehicle listed in `GroundVehicleSounds`
(loudness idle and flat out, tracked, enclosed cab) is heard through `GroundVehicleAudio`: idle and working loops
crossfaded by engine speed (from how fast the hull moves, so every client agrees), the muffled cab loop for an
enclosed crew in first person, the track clank by the tracks' speed, the distant loop, sound delay and Doppler; the
server sends far tracks; the old once-a-second engine sound stops. The engine winds up when a driver takes the controls
and down when they leave. The layers come from one recording by `design/ground-vehicles/prepare_audio.py` (16 s varying
loops; idle slowed, cab and distant low-passed). Lessons:
- **Sync whether the engine runs.** Inferred on the client from the crew, it fell silent 128 blocks off, inside entity
  range: clients aren't sent a mob crew that far (`groundEngineRunning`).
- **Write Ogg in blocks.** libsndfile's Vorbis encoder overflows the stack on Windows given a long buffer in one call.
- **Real starts and stops** (2026-11-17, `design/ground-vehicles/prepare_starts.py`): `engine_start`, `engine_stop`
  and their muffled `_cab` versions, cut from CC0 diesel takes. The loops wait for the engine to **catch** in the
  recording (`GroundVehicleSounds.startCatchTicks`) before they wind up, so the crank is never covered by a running
  engine. A start plays only when this client **saw** the engine start: a player arriving at a vehicle already running
  (back into entity range) hears it running, never a second start (`GroundVehicleAudio.LAST_RUNNING`).
- **No real cab recordings exist under CC0** for armour (searched 2026-11-17; the only "tank interior" take is a rattling
  car). The cab loops stay filtered exterior takes; say so in each status file rather than calling it missing work.

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

### Don't bury a report

A gun's report must keep its crack. The howitzer's was rebuilt "heavier" with its 25-180 Hz lifted 9 dB, a synthetic
sub thump and hard soft-clipping over a take that was already dull, and the user heard it as muffled (2026-11-19):
89% of its energy under 200 Hz, 1.6% above 2 kHz, a 6 dB crest. Measure a report before shipping it: a close shot of a
gun has several percent of its energy above 2 kHz and a crest over 10 dB. Choose a take that has the crack, lift the
low end a few dB at most, and only round the peaks (a gentle tanh); weight comes from the take, not from processing.
Vorbis overshoots a clipped crack: keep the peak at 0.7.

### Each tank its own engine (2026-11-19)

The user: "find a separate audio recording for each tank, it should have its own identity". Three vehicles had come
to share the BMP-2's engine; each now has a real recording of its own class of engine (CC0, fetched, licence-checked
and cached by `prepare_starts.source`): the BMP-2 an APC's diesel, the PLZ-05 a heavy tank diesel, the YX-100 a gas
turbine. One take should give a vehicle its start, idle and working loop where it can (they then sound like one
engine). A take with no stop can still give one: `prepare_starts.py` plays a turbine's spool-up backwards
(`"<take>:reverse"`) or runs an idle down (`"<take>:rundown"`). A short idle stretch is chained into a varying
16 s loop (`prepare_idles.py`) so it doesn't beat. Set `GroundVehicleSounds.startCatchTicks` to the new start's catch
(the YX-100's turbine: 80 ticks). Tracks already clank on their own layer, so prefer a drive take without track noise
when there is one; the PLZ-05's ride has some.

### Helicopter sounds: start, flight, slap and shutdown (2026-11-21)

The CH-47's sounds (`design/chinook/prepare_audio.py`) are the worked example. `HelicopterEngineAudio` plays, for
`vehicle.<id>.*`:
- **`startup`**, set as the data's `engine_start_sound`. Make it last `helicopterStartupTicks`; it crossfades into the
  loop over its last 1.5 s.
- **`engine`**, the near loop, pitched and faded by rotor speed. Its pitch falling as the rotor coasts *is* the rotor's
  wind-down, so don't record one into `shutdown`.
- **`engine_distant`**, which takes over with distance.
- **`slap`** (optional), a loop of the blades slapping. It swells while the helicopter comes down with the engines
  working (blade-vortex slap, the sound of a helicopter coming in to land) and in hard banked turns at speed, and dies
  on the ground. Cut it from the closest pass of a flyby.
- **`shutdown`** (optional), played once when the engines cut (on touchdown). Use the turbines' whine only: a high-passed
  spool-up played backwards works.

**No free close recording of a CH-47 exists** (Freesound, searched 2026-11-21: distant flyovers with wind, birds or a
dog). The user accepted "a heavy helicopter". The CH-47 is a UH-1 recorded within 20 m (OroborosNZ 157722, CC BY 4.0:
credit it in `HELICOPTER_CREDITS.md`), slowed to 0.93 with the low end raised. The flight loop lays two stretches of
the take over each other, so two rotors beat against each other. Read a take's rotor beat from its envelope (blade
passes a second: a UH-1 about 10.5, a Sea King about 17) to tell the aircraft and its distance before using it. Use the
preview Ogg (`-hq.ogg`, better than the MP3), and drop an original WAV or FLAC into the cache when you have one.
`Ch47SoundClientGameTest` checks every layer plays on an active channel.

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
- **Only white lamps light the world.** World light has no colour: vanilla's is warm and a shader pack tints all block
  light one colour, so the AH-6's red and green position lights lit the ground yellow-orange under Photon
  (2026-11-26). `AircraftLights.addLampSpots` passes only near-white lamps (`whiteLamp`) to LambDynamicLights; a
  coloured lamp keeps its glow and its drawn spill on the airframe.
- **Photon's own faults, not ours.** On the pack's Photon 1.3b, everything a 26.1 shader pack draws in its translucent
  pass vanished wherever Voxy LOD terrain was behind it: the A-10's nozzle glow, the cockpit screens (`RenderTypes.eyes`),
  held items' enchantment glint. Fixed in the user's Photon fork (`Marco-Cigna/photon`, branch `yftm`, a backport of
  upstream #609), with a lit enchantment glint. Test vehicle glows against distant ground (from altitude) as well as
  against the sky. The pack takes the fork from its GitHub releases (`photon-yftm.zip`, a fixed name so Iris keeps
  players' options); a Photon fault goes there, not into a workaround here.
- **Screens and other `eyes` quads under Photon on 26.1** go through its translucent pass, which lights them as a
  surface (sun with shadows, sky, block light) and adds their emission on top; they aren't purely emissive as they
  were before 26.1. In practice the emission wins: the A-10's displays held steady from dawn to dusk under the canopy
  frame's shadow (2026-11-26). Still check screens at noon and at night, from the seat, and keep their colours deep.
- **What neither JEG nor Photon can do (don't chase these as bugs).**
  - *Coloured light from a moving thing.* LambDynamicLights only raises the world's block-light level, which has no
    colour, and Photon tints it like a torch. A red or green lamp can glow and tint its own airframe (drawn spill), but
    the ground round it can only be lit warm white, so coloured lamps don't light the world at all (above). Photon's
    Colored Lights only covers placed blocks and held items; real coloured lamps would need a ray-traced lighting mod
    such as Photonics, which isn't in the pack.
  - *Light inside the model.* A walk-in room or cabin is part of the model, not blocks, so the world's light there is
    the outdoor light at that spot. Interior lamps are drawn and given to the blocks aboard by the vehicle itself
    (`CabinBlockEffects.light`, the FSV-6's room lamp); keep doing that.
- **Material maps (LabPBR), not used yet.** Photon reads `_n`/`_s` maps for normals, metal reflections, roughness and
  emission, but only with its Normal Mapping and Specular Mapping options on, which are off by default and off in the
  user's settings. For the pack as set up they would change nothing, and every vehicle texture (and every repaint)
  would need its maps kept in step. Try one only if the pack turns Specular Mapping on for everyone: pilot it on one
  vehicle (the A-10), compare look and `/jegfps` against the current build, then decide for the fleet. Lamps and
  screens stay drawn either way, so the look without a shader pack is unchanged.
- **Frame rate in the pack client.** `/jegfps <seconds>` (`DevKeysClient`) prints the average and slowest second and
  the median and 99th-percentile frame time. Set `inactivityFpsLimit:"minimized"`, `maxFps:260` and
  `enableVsync:false` in `build/run/packClient/options.txt` first: mcpfabric's input doesn't count as a player's, so
  the game drops to 30 fps as if AFK.
- **Moving a bone about Y or Z: mind GeckoLib's mirrored X.** The model's +X (the pilot's right) is -X in the
  frame bones turn in, so a rotation about X keeps its sign but one about Y or Z is reversed: the bush plane's rudder
  turns into a right turn with a *negative* `setRotY`. Drive control surfaces from the attitude rates every client sees
  (roll right is positive, pitch positive is nose down), smoothed per tick (`AbstractVehicleGeoRenderer.tom6Surfaces`).
- **Taildraggers sit tail down.** Model the tailwheel as it hangs in level flight (above the main wheels' contact) and
  pitch the drawn airframe nose up about the main wheels while slow on the ground (`BushPlaneStance`); the flight model
  stays level.
- **Navigation lights: red on the left (port), green on the right.** In model pixels with the nose at -Z, the pilot's
  left is **-X** (the A-10's red is at -142.7). The bush plane's generator had it backwards and flew with them swapped
  until a night screenshot from behind showed green on the left. Check it from behind in third person, where the
  viewer's left is the pilot's.
- **Glows need geometry behind them, and must stay visible.** yftm.21 modelled solid nozzle plugs on the A-10
  (`design/a10/nozzle.py`) and tucked a smaller glow into them, flush with a black face; the user found the orange glow
  gone and the rear looking worse, and both were put back as in yftm.20 (2026-11-17), with a shimmer. Before
  re-modelling a part a glow sits in, compare day and night screenshots of the old and new look from the reported
  angle, and keep the glow at its old size and brightness unless asked.
- **Give every helicopter a roll pivot at its middle** (`render_pivot_y`, or `rotateOffsetHeight`). The shared strafe
  sway kicks the hull against a strafe before it banks. About a pivot on the ground, the CH-47's cockpit and rotors
  slid the wrong way first: "it goes right a little bit before going left" (the user, 2026-11-21). The Mi-28 turns about
  1.8 m, and the CH-47 now about its cabin's middle, at the pilot's eye. Every pose (drawing, cabin, seats, lights,
  hitboxes) takes the same height. A big airframe can take less of the kick (`strafeSwayScale`).
  `Ch47FlightFeelClientGameTest` measures the view's wrong-way slide against the Mi-28's.
- **A cabin's lamps run on the battery.** `Kind.CABIN`, `JUMP_RED` and `JUMP_GREEN` (`AircraftLights.onBattery`)
  work with the engines off, so people standing in the CH-47 have light. Cabin lamps light only at night. Keep them
  out of LambDynamicLights: the whole model takes one light level, so a cabin lamp lit the outside of the hull too.
  Lamps in the texture are dark lenses, and the light set lights them. Formation strips are a night light
  (`Strip.nightOnly`): by day the CH-47's showed as neon-green bars on the hull.
- **Cockpit light is a night light.** `Kind.COCKPIT` spill is drawn additive over the whole model; by day it painted the
  AH-6's windscreen pillar lime, and under Photon it bloomed the Mi-28's frame orange. On aircraft it follows
  `AircraftLights.nightSwitch` and is cut to `SHADER_COCKPIT_SPILL` under a shader pack (`VehicleLightsLayer`); the
  FSV-6's room lamp (a closed room) keeps its level. Check every cockpit from the seat at noon and at midnight.
- **HUD.** Build the vehicle's state panel with `HudPanel` (`vehicle/client/overlay`), never with hand-placed text:
  it is drawn in the information layer, top left, beside every other vehicle's panel (user, 2026-11-17: the HUD must
  be clean, modern and 20% smaller, not cluttered). The look is `HudKit`:
  - Dark panels with soft rounded corners, a clean sans (Inter SemiBold, `font/hud.ttf`: all text in it goes through
    `HudKit.text`/`HudKit.width`), grey names, near-white readings, one amber accent. Use green, amber and red only for
    state that needs attention; icons are smooth Material Icons Round glyphs (`HudKit.ICON_*`), bars are thin pills and
    key hints rounded keycaps. Not pixel art (user, 2026-11-18: "clean and modern, not blocky or pixelated").
  - Minecraft samples fonts and GUI textures NEAREST, so antialiased text and shapes look smooth only one texel to one
    screen pixel. The layer is drawn at `HudKit.scale`, 80% of the GUI scale snapped to a whole number n of screen
    pixels per HUD unit, and `design/hud/build_hud_assets.py` builds the font (`font/hud_<n>.json`, oversample n) and
    the atlas of icons and round shapes (`textures/gui/hud/kit_<n>.png`) for n = 1..6; `HudKit` draws with the set
    for the current n. A screen drawn at the GUI scale calls `HudKit.useDensity(guiScale)` first (`VehicleScreen`).
    Draw rounded shapes with `roundRect`/`pill`, not `fill`, and new icons by adding a glyph name to the builder's
    `ICONS`. Sights, reticles and markers stay at the GUI's own scale.
  - Rows: `header` (status lamp and state), `bar`, `slider`, `value`, `note`, and `hints` (keycaps). They share one
    name column, and the panel sizes itself from the measured text.
  - Show key hints as keycaps (`HudKit.keyName` reads the binding and shortens SHIFT, CTRL, LMB) for a few seconds
    after boarding, then fade them.
  - Warnings are a breathing red chip under the crosshair, not flashing text.
  - For a scale-3 game test, use a window large enough (the dozer test uses 1280×900) and assert the actual
    `getWindow().getGuiScale()`. Selecting 3 in the default 854×480 window still renders at 2.
- **Check for overlapping crosshairs** (user, 2026-11-17: seen on several ported vehicles). From every seat, in first and
  third person, zoomed and not, with each weapon selected, screenshot the centre of the screen and count what is drawn
  there: exactly one aiming mark. The sources that stack up are the vanilla crosshair (cancelled by `GuiMixin` while
  the vehicle HUD is up), the gun crosshair a passenger's hand weapon draws (`CrosshairHandler`), the vehicle reticle
  (`VehicleHudOverlay.renderReticle`), a slew-limited turret's bore marker (`renderBoreMarker`), a shell gun's impact
  marker, and anything drawn into the vehicle's own model or cockpit layer (a sight bone in front of the camera). A
  second mark that means something else (where the gun points while it catches up with the view) gets its own shape,
  never another copy of the reticle, and is hidden when it lies on the reticle: the bore marker drew the same icon as
  the third-person reticle and the two sat on top of each other on every slew-limited turret (the Mk42, BL-132,
  Type-63, howitzer, PLZ-05, YX-100, HPJ-11 and sentry); it is four corner brackets now, gone within 7 GUI pixels of the
  centre. Check while the turret slews and once it has settled.
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

### Screens drawn on a vehicle's walls

Wall and cab screens are textures the layer fills live. Draw them at five texels a layout unit (or more) in the HUD's
Inter, never the default bitmap font or Arial at 1:1: the FSV-6's 232 x 120 CRT-style screens with scanlines read as
pixelated (the user, 2026-11-19). Dark glass, a faint grid, rounded tracks round each live bar, a title bar with an icon.
Keep every live element's place in the layout's units so the layer's code doesn't move. Never draw an "off" lamp in an
emissive render type: under a shader pack a dark emissive colour shows as a pale square; draw only what is lit.

### Aircraft instrument panels

Every crewed aircraft gets a live panel on its model's own dash (the A-10, the Mi-28, and the AH-6 since 2026-11-19:
"give the AH-6 the detailed instrument panel similar to the Mi-28"). A dash that is only painted on the model texture
(the AH-6's static attitude picture and flat green screens) reads as a toy from the seat.
- **Find the dash faces in the layer's own space.** `render_geo.load_quads` gives every model face in GeckoLib render
  pixels, the space `passInfo.getModelRenderMatrixPose()` draws in (forward is -Z, the pilot's right +X): list the
  faces in front of the seat, take each face's corners, and build a `CockpitPanel` from its top edge's centre, across,
  down and out. Render the view from the eye (`design/ah6/render_seat_view.py`) and compare it with a game screenshot
  from the seat before drawing anything.
- **Cover the face, then draw.** A lit face texture over the model face (`entityCutout`, the world's light), the
  screens on it (`eyes`), and the live marks last, each in its own `collector.order`. `eyes` blends translucent in
  this version: an opaque colour covers what is under it, so a colour attitude display draws its sky, then its
  ground (`CockpitPanel.ground`), then its lines and the overlay, each in its own `collector.order`: the quads of one
  translucent buffer are sorted by distance, and in one buffer the AH-6's big sky quad was drawn over half its ground.
  Use deep colours on emissive screens: a shader pack's bloom washes a light blue out to white.
- **Live figures** are seven-segment digits (`CockpitPanel.text`, with N, E, S and W for a compass rose), drawn from
  synced state only: attitude (`poseAngles`), speed and climb (`getDeltaMovement`), height (`altitudeAboveGround`),
  rotor (`propellerSpeed` over the speed it runs at in flight), energy, hull, ammo per slot, the selected weapon and
  the flares' cooldown.
- **Keep the HUD off the panel.** In a helicopter's first person the top middle is the helmet display's heading tape,
  the middle its speed and height boxes and the bottom middle the panel: the status card takes the bottom left corner
  and the crew list moves to the top left (stacked above the crew list, the card covered the speed box).

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

**Play it first: mcpfabric is required, and preferred over new scripted tests** (the user's standing rule,
2026-11-15). Develop and iterate by playing the vehicle in the pack client; add a scripted game test only to pin a
number that must not regress, for what can't be done by hand, or to guard a bug found. `design/playtest/mcp.py`
calls the bridge (`python -I design/playtest/mcp.py session.join '{"worldId":"sp:SupportTest"}'`, `vision.screenshot
'{}' out.png`). Gotchas learned on the mortar and FSV-6:
- Commands that need a player (`/gamemode`, `/ride`, `/jegkey`) go through `chat.send`, not `command.run`.
- Board a vehicle with `/ride @s mount @e[type=jeg:<id>,limit=1,sort=nearest]`; place the camera relative to a
  vehicle with `execute as @e[...] at @s run tp @p ^left ^up ^forward facing ...`.
- Close the pack client before any `./gradlew` compile or game test, and before rebuilding: both use the GPU, and a
  compile replaces classes under the running client.
- The client's render distance is agreed with the server at join (C2ME): set it before joining, not after.
- To fill a vehicle's hold, open it with `/jegkey key.inventory 1` and move stacks with `container.click`
  `{"slot":N,"button":0,"mode":"quick_move"}` (a `shift` flag is ignored). Slots past the hold are the player's.
- `/stop` does nothing in singleplayer, and stopping the Gradle task leaves the game running: end the `java.exe` whose
  command line has `packClient` before compiling or running tests.
- (Howitzer) The client pauses when its window loses focus, which it does while the user plays their own client: set
  `pauseOnLostFocus:false` in `build/run/packClient/options.txt` before launching.
- `control.look` keeps holding its view until another look: a later `tp ... facing` is overridden by it.
- Hold the use key with `/jegkey key.use <ticks>` (a spyglass, a held item); `control.setInput {"use":true}` doesn't
  start using an item. `screen.close` shuts any screen, where a second `key.inventory` press may not.
- **A stand-in mob driver needs the crew tag** (since yftm.22, only players and enemy crew drive or man guns): add
  `EnemyVehicleController.ENEMY_VEHICLE_CREW_TAG` before `startRiding`, or the vehicle stays dead and the test measures
  silence. The tag alone does not start the enemy AI (that needs `ENEMY_VEHICLE_TAG` on the vehicle). Fifteen tests
  were fixed for this on 2026-11-17.
- Don't `/tp` a vehicle with you aboard: the server calls it "moved wrongly" and the vehicle was lost. Summon a new one,
  or teleport yourself and board again.
- `/jegkey` presses sent while you aren't aboard are queued and spent when you board: three change-seat presses landed
  at once. Check the crew list (`[n] name`) after changing seat.
- Shaders reflect the hull in calm water: a "boat above you" in a screenshot is often its reflection. Check heights
  with `/data get entity @s Pos` and `execute positioned ... over world_surface` before building water.
- An aircraft flies from the mouse, which mcpfabric can't move: `/jegstick <dx> <dy> <ticks>` moves the cursor that
  many pixels a tick (negative dy pulls the nose up; `DevKeysClient`). It moves the real cursor too: shifting only
  the game's copy snapped back on the next real event, a flick that put the bush plane's nose down mid-climb.
- A sandbox's parked vehicles are obstacles: the AircraftTest world has a truck across x 0 at z 120, and the first
  bush plane take-off along x 0 ended in it. Take off along a clear lane (x -60).
- Ending the client loses what the world hasn't saved yet (`save-all` isn't available here): a vehicle summoned a
  few minutes before was gone after the restart. Summon again, or save and quit to the title first.

`-PwithMcp` puts mcpfabric in the pack client, so an agent can drive it:
screenshots, movement keys, right-clicks on what the crosshair is on, screens; our own bindings with
`/jegkey <name> [ticks]` (`DevKeysClient`). Drive where a player would, not where a test starts: the BD-40 turn bug
only showed after a pass had left it in its own trench. Take bursts of screenshots (2–3 a second) and compare them;
a label that flickers shows only across frames. Each problem found becomes a focused test.
```
./gradlew runPackClient -PwithPack -PwithMcp -Pminecraft_version=26.1.2 -Pfabric_version=0.155.3+26.1.2 -Ploader_version=0.20.1
```
Before launching, check no other session is building in this checkout: a compile replaces `build/classes` under a
running client (`NoClassDefFoundError` for a class that exists).

**Two players.** Anything one player does that another sees (people aboard a moving vehicle, seats, blocks placed in a
cabin, a slung load, sounds heard from outside) is played with two pack clients over LAN (the Chinook's two-player
pass, 2026-11-25, found five faults one client never showed). Start the first client as above with
`--args="--username Pilot"`, join the world, then `/jegoffline` (dev clients have no real session, so a second one is
refused by an online world) and `/publish true creative 25570`. `python -I design/playtest/second_client.py Walker
25570` starts a second client from the first one's command line in `build/run/packClient2`, with its own mcpfabric
bridge on port 25600 and the LAN world in its server list (`session.worlds` names it `mp:...`); drive it with
`MCP_RUN=build/run/packClient2 python -I design/playtest/mcp.py ...`. Both share the first's classes: no build while they
run. Put the player who must not die in survival: creative players take no damage, which hid a fatal one. Kill each
client by its own PID (the java.exe whose command line holds `jegn-...` and `packClient` or `packClient2`).

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
