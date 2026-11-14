# Changelog

## 1.7.2+mc26.1.2.yftm.18 - 2026-11-14

### Added
- Jets and helicopters are heard from far off, up to 1024 blocks (before, the sound cut out at about 190 blocks, where the server stops sending the vehicle). The sound fades smoothly with distance, quickly close by and slowly further out, and past about 40 blocks gives way to a muffled, echoing distant version of the engine (and of the A-10's gun); from 240 blocks that is all that is heard. Past 190 blocks the server sends far players where each aircraft is every 4 ticks, so the distant sound carries on without a jump. Far sounds arrive late, at the speed of sound, with Doppler. At full power an A-10 is still faintly heard 950 blocks off. Helicopter guns now carry a long way too (before, within about 16 blocks).
- Gun rounds from the A-10 and the helicopters keep flying outside the loaded area, like the helicopter rockets, out to 1200 blocks, and players up to 1024 blocks away see their tracers.

### Changed
- A-10 top speed is 200 km/h (was 120), with the rest scaled to match: it stalls below about 76 km/h, takes off at about 94 km/h and dives at up to about 250 km/h. Cruise control steps are scaled too.
- The A-10's gun points 2 degrees below the nose, like the real GAU-8, and its aiming marker sits lower to match.
- Firing the GAU-8 slows the A-10 much harder (a burst takes it from 200 to about 120 km/h in two seconds), but the recoil eases off before the stall, so it never falls below about 110 km/h or stalls from firing.
- The A-10 is tougher: health 240 (was 180), armour 8 (was 7), and its engines are better protected.
- The A-10 answers the controls a little faster: about 18% quicker in pitch and yaw and 10% quicker in roll.

### Fixed
- The back of the A-10's engines no longer looks see-through. The rear cowlings were being spun and blurred like the fans; now only the front fans spin.

## 1.7.2+mc26.1.2.yftm.17 - 2026-11-14

### Added
- The A-10 flies as a fixed-wing jet. W and S set a throttle that stays where it is left (shown on the HUD), and the engines spool up and down with it. Lift grows with airspeed: a full-throttle take-off run is about 120 blocks; the nose wheel only lifts at take-off speed, about 56 km/h. Low and slow after take-off (and on the approach) the nose will not come up further than the jet can hold, from 8 degrees just above the stall to 25 with speed, so hauling back off the runway climbs away instead of stalling. Gravity always acts, so climbing costs speed and diving gains it. Too slow, and the stall warning sounds, the nose drops and the jet sinks until it has the speed to fly again. Top speed is 120 km/h level, up to about 150 in a dive; it stalls below about 45 km/h.
- On the ground: the nose wheel swings over gradually and the tyres' grip limits the turn, so the faster the jet rolls the wider it turns. Crawling, it turns in a circle about 20 blocks across; taxiing at 18 km/h it comes full circle in about 20 seconds; at 70 km/h a 90-degree turn takes about 10 seconds. With the throttle closed the brakes hold the jet still; open it at all and it taxis on idle thrust.
- Cruise control (J, the helicopters' cruise key): switched on, an autothrottle holds the airspeed the A-10 had, through climbs, dives and turns; W and S then raise or lower the speed it holds, shown on the HUD. J again switches it off.
- Flying it: a heavy airframe that takes a moment to answer. The mouse moves the nose. Moving it sideways eases the jet into a banked turn and rolls level again after. A and D roll by hand; hold them for continuous rolls. Hands off, it holds its climb, descent or level flight. Loops and inverted flight work. Left Ctrl is the airbrake in flight and the wheel brake on the ground. A and D, or the mouse, steer the nose wheel when taxiing. Free look still works.
- The gear comes up on its own once the jet is clear of the ground and goes back down for the approach. The wheels roll on the ground, and each touchdown puffs tyre smoke.
- Engines: each fan spins up with the throttle and airspeed, a blurred disc at full power. The exhausts glow from inside, from a dull ember at idle on the ground to a hot orange core flat out; in the air they stay a clear orange however far the throttle is pulled back. The glow is seen from behind and fades side on.
- Views: first person sits in the cockpit looking out of the canopy. The chase camera keeps the horizon level while the jet banks beneath it (until the bank passes about 75 degrees, as in a loop or roll). Zoom switches to a stabilised targeting-pod view, like the helicopters' gunner camera: the mouse looks around, down to the ground below, without moving the nose. The third F5 view is an action camera stuck on the left wing near its tip, looking forward and in across the wing at the engines and cockpit, banking with the jet. The aiming marker follows where the gun actually points, so it swings round with the jet in a turn.
- Landing within a normal sink rate, wings level and nose up, costs nothing. A hard landing, a wingtip or nose touching first, or a belly landing costs health, and slamming in breaks the airframe. Wings, tail, nose and engines striking terrain or trees damage the jet, and at speed wreck it. Abandoned in the air, out of fuel or badly damaged, the jet goes down out of control.
- The GAU-8's seven barrels spin up while it fires and run down after, and its gas streams off the nose in a grey cloud. Each GAU-8 round kicks up the ground it hits: chunks of the block (dirt, sand, stone), a plume of dust, dust drifting down and a haze over strafed ground, with sparks, hot fragments, a small flash and smoke. Hard turns pull vapour trails off the wingtips, and a steep bank at speed streams vapour over the wings.
- Battle damage on the A-10: a grey plume from an engine below 60% health. Below 35%, thick smoke, sparks and a fuel leak from the wing; below 30%, ailerons and an elevator shot away; below 15%, the engines on fire under a black trail.
- Destroyed in the air, the A-10 loses its outer right wing and a fin. It goes down burning, rolling towards the missing wing, and blows up where it hits, throwing wreckage. A fast crash into the ground blows up on the spot. The hulk burns on its belly with the gear gone.
- HUD: airspeed, height above ground, throttle, gear and brake state, plus flashing STALL and PULL UP warnings for the pilot. The pull-up warning also sounds an alarm in the cockpit.
- Sounds, from real recordings: the A-10's own TF34 turbofans start up when a pilot boards, winding up to idle, then rise and fall in pitch and volume with the throttle, with Doppler as the jet passes. People on the ground hear a real A-10 flyby as it races past them low. They hear the gun late, as at a real gun run: the sound travels at the speed of sound (about 17 blocks a tick), so a burst fired 150 blocks away arrives some 9 ticks after the tracers. In the cockpit the rush of air grows with airspeed. Tyres rumble on the runway during the take-off run and rollout, and squeal as they touch down. The gear can be heard going up after take-off and down for landing. A stall warble plays in the cockpit, and the GAU-8 shakes the airframe (gently) as it fires. In first person (the cockpit and the targeting pod) the pilot hears everything from inside: the engines, tyres, gear, touchdown and gun come through muffled by the airframe, and the gun louder and heavier, with the buzz of its 65 rounds a second felt through the seat. From the chase and wing cameras it all sounds as it does outside. Credits and licences: `assets/jeg/sounds/vehicle/a10/CREDITS.md` (the engine recording is CC BY-NC).

### Changed
- Countermeasure flares (A-10 and helicopters) burn like the real thing: a blinding white core in a warm halo with a glare streak, flickering, flaring up as they ignite and dying away as they burn out, seen from up to 320 blocks. Each leaves a thick white smoke trail hanging in the sky and sheds falling white sparks. The flare's own light was never drawn before (only its particles).
- A-10 weapons: the GAU-8 Avenger (30 mm, fed from rifle ammo) fires 20 rounds a second over a 1.4-degree cone (about three blocks across at 60 blocks). Every round bursts on impact, and it punches through the BMP-2's and LAV-150's armour. It fires with its own continuous roar, which echoes away when the trigger is released, and reloading its 300-round drum (6 seconds) is heard: the drum door opening, then the feed seating and the bolt slamming home. Its tracers are heavy, smooth glowing streaks. Firing it slows the jet hard (its recoil matches the engines' full thrust) and wraps the nose in gun smoke. Weapons reload as fast as a helicopter's. Its second and third weapons are the AGM-65D Maverick (medium anti-ground missile) and AGM-65G Maverick (large anti-ground missile). Both fire from alternate wings. Zoomed into the targeting pod, the pod is their sight (a small square; the gun marker is hidden): a Maverick goes to the vehicle or creature under the pod's crosshair, or to the spot on the ground it is looking at, wherever that is from the nose, and follows the crosshair as long as the pilot keeps looking through the pod. Otherwise they lock on with the seek key. Each Maverick's hit sets off secondary explosions around its crater over the next second or two, as what it hit cooks off: three for the AGM-65D, five bigger ones for the AGM-65G (one of them burning fuel), each with its own flash, smoke, boom, crater and damage. The gun always fires down the nose, at its marker, whatever the pod looks at. Each pylon shows its missile only while it is loaded. The weapon list shows each with its own icon (GAU-8, AGM-65D, AGM-65G).
- The A-10 fits its model: the cockpit seat and camera, a chase camera 24 blocks back, hitboxes for the fuselage, wings, engines and tail, and the dismount beside the cockpit. Health is 180, with heavy armour around the cockpit. Its storage has five rows (was three). It can be built at the vehicle assembling table, a little dearer than the MI-28: 58 iron ingots, 28 copper ingots, 20 redstone and 18 feathers. The unused bomb and rocket parts are no longer drawn.
- Vehicle state packets now carry roll, so other players see a jet loop and roll smoothly.

## 1.7.2+mc26.1.2.yftm.16 - 2026-11-11

### Changed
- Sneaking (Shift) no longer throws you out of a JEG vehicle. Vanilla ejects a rider as soon as they sneak, which made Shift an eject button in a helicopter. Use the Vehicle Dismount key (Left Alt by default) to get out; other mods' and vanilla vehicles are unchanged.
- Helicopters climb faster (0.5 blocks/tick, was 0.4) and descend faster (0.55, was 0.45). The landing assist now starts at 15 blocks (was 12) so touchdowns stay gentle.

## 1.7.2+mc26.1.2.yftm.15 - 2026-11-10

### Added
- Minigun rapid cool-down: press the reload key (R) with a hot minigun and the barrels spin without firing, hissing and venting white gas, until the gun is cold. From fully overheated that takes about 3.5 seconds; left alone in the hand it takes over a minute. The gun cannot fire meanwhile; press the key again to stop part way. Putting the gun away stops it, and with a minigun in each hand both cool on one press. Coolant in the off hand still takes the key first. Other players see the gas and hear the hiss.
- New sound: the vent hiss, mixed from two CC0 Freesound recordings (365071 by sniperous, 234782 by wubitog); see `assets/jeg/sounds/item/minigun/VENT_CREDITS.md`.

### Fixed
- The crater of a missile, helicopter rocket or cannon shell in terrain beyond the view distance (drawn by Voxy from Voxy Server Side) shows about 4 seconds after the blast, under the smoke, instead of up to 14. Voxy Server Side only told players about changed terrain on its 10-second timer; for 12 seconds after a blast the change now goes out as soon as it is noted, at most once a second. The server config is untouched. This reaches into Voxy Server Side 0.19.0 internals; if a later version differs it logs one warning and the crater arrives on the usual timer.

## 1.7.2+mc26.1.2.yftm.14 - 2026-11-10

### Fixed
- Helicopter rockets (70 mm and 80 mm) no longer vanish a moment after launch or hang in mid-air. The server only sent a rocket to players within 64 blocks, which it covers in a third of a second, and it stopped moving at the edge of the simulation distance; yftm.13 fixed this for guided missiles only. Rockets now fly and are followed like missiles: they tick wherever they are, load the terrain ahead of them, and players out to 1024 blocks see their motor and smoke trail.

### Changed
- A rocket that hits nothing is gone after 720 blocks (80 ticks of flight) rather than after the bullet lifetime setting (60 seconds).

## 1.7.2+mc26.1.2.yftm.13 - 2026-11-10

### Fixed
- Ammo counts in the vehicle weapon list no longer run over the weapon's icon and out of their box: the boxes widen to fit the longest count ("128/512", a reload countdown).
- Long-range missiles no longer hang in mid-air at the edge of the loaded world until a player comes near. A missile only moved once the chunks ahead had finished loading, and at speed it got there first; once stopped it could not ask for them again. Missiles now tick every tick wherever they are, load the terrain 80 and 160 blocks ahead of them, and are never saved with a chunk.

### Added
- The Mi-28's chin gun heats up as it fires: over a few seconds of sustained fire its barrels redden, hottest at the muzzle, and thin wisps of smoke curl off the muzzle once it stops. It cools again in about ten seconds. Kept faint in the gunner's own view.

### Changed
- The Mi-28 gunner can swing the view and the chin gun much further: 160 degrees to either side (was 110), 70 degrees down (was 40) and 30 degrees up (was 10).
- A missile's blast damage is a round zone that eases off towards its edge (three quarters at half the radius, none at the rim), measured to the nearest part of the target. It used to be a cube around the impact with full damage out to its corners.
- Missiles punch through a wall up to two blocks thick with open space behind it and burst up to three blocks inside, so a hit on a house wrecks it from within: a 9M-120 levels a small village house and takes out more than half of a medium one. Solid ground, thick rock and blast-resistant blocks stop a missile where it hits.
- Bigger blasts: 70 mm rockets 4.0 explosion power (was 3.2), 80 mm 5.0 (was 4.0), 9M113 4.0 (was 3.0), 9M-120 and 9M-120F 7.5 (was 4.5), 9M336 4.0 (was 3.2), KH-39 14 (was 11). Craters, the drawn blast, the boom and vanilla blast damage to anything nearby grow to match; the drawn blast and boom are no longer capped for the biggest warheads (only the view shake is).
- More blast-resistant materials: any block with a blast resistance of 10 or more now keeps it in full against warheads (it used to take 1200, obsidian-class). That adds trial spawners and vaults (50), ender chests (600) and tough blocks from other mods. Stone, deepslate, bricks, purpur, metal blocks and end stone (9 and below) are still blown apart.

## 1.7.2+mc26.1.2.yftm.12 - 2026-11-09

### Fixed
- Missiles stay visible past your render distance with Voxy and shaders. Voxy pushes the render-distance fog out to a billion blocks, which switched off drawing far missiles and blasts inside view, and shader packs then faded them out at the chunk render distance. Far missiles and blasts are now drawn inside the chunk render distance as well as the fog.
- A missile's motor shows as a bright orange-white point at any range, in daylight too: the old glow was added light, which washed out against a pale sky, and the missile's own smoke covered it when seen from behind.
- The MI-28 pilot's ammo line names the 9M-120F Thermobaric Missile, not the "Medium Anti Ground Missile" it loads (the gunner's plain 9M-120 loads the same ammo). Vehicle ammo lines now always show the weapon's name.

### Changed
- Missile positions and blasts reach players up to 1024 blocks away (was 512), so the longest shots (9M336 ~600 blocks, KH-39 ~540) are seen to impact. Wreck explosions and rocket blasts too.
- The vehicle assembling table is laid out afresh: vehicles listed on the left with a green or red mark for whether you can build them now; picking one shows its silhouette and every item it needs as icon, carried/needed (green or red) and name. Assemble builds the picked vehicle and is greyed out until everything is there (clicking a vehicle no longer builds it straight away). Nothing overlaps, inventory slots are drawn, and the pack's sort buttons keep their corners.

## 1.7.2+mc26.1.2.yftm.11 - 2026-11-08

### Added
- Dual-wield miniguns: with a minigun in each hand the trigger spins up and fires both. Each gun has its own heat and fires from its own muzzle (tracers and flashes); both share inventory ammo. Two are slower to carry than one. A minigun alone in the off hand does not fire.
- Helicopters show battle damage: engine smoke below 60% hull, thick smoke, sparks and a shot-out canopy below 35%, fire and a lost tail rotor below 15%, and the hull darkens as it goes.
- Destroyed helicopters and aircraft no longer vanish: they go down spinning and burning, blow up where they hit (or cook off first if already on the ground), and burn as a charred wreck for 10 seconds before breaking apart. A wreck cannot be boarded, repaired or damaged.
- Everything inside a destroyed vehicle is thrown out after the blast instead of being lost.
- The helicopter weapon list shows every weapon's own ammo (loaded/spare, or rounds aboard), with the ammo item beside it; reloading shows a countdown, empty shows red.

### Fixed
- Long-range missile shots: a missile past the server's simulation distance used to freeze in mid-air and never go off. Missiles now keep their flight path loaded (like an ender pearl) and explode at range.
- Missiles beyond entity range (192 blocks on yftm) stay visible: players out to 512 blocks see the motor glow and smoke trail, and far missiles and blasts are drawn inside the render-distance fog.

### Changed
- Helicopters climb faster (0.4 blocks/tick, was 0.3) and descend faster (0.45, was 0.3); the landing assist starts at 12 blocks so touchdowns stay gentle, and holding descend no longer sets off the low-airspeed warning.
- Missiles and rockets (including the rocket pods and the hand-held rocket launcher) blow dense blocks apart: stone, deepslate, purpur and end stone brick crumble like soft blocks, so End cities and stone walls take real damage. Blast-proof blocks (obsidian and tougher) are unchanged.
- Vehicle charging stations reach 8 blocks around, 3 below and 6 above (was 2 around), charging the nearest vehicle that needs it.

### Compatibility
- The dual-minigun pose works with Fresh Animations (EMF) and Not Enough Animations.
- Clients and the server must update together: the tracer network message changed.
- Game test option -PwithPack runs tests inside the yftm pack's mods (with -PpackExclude to leave some out).

## 1.7.2+mc26.1.2.yftm.9 - 2026-11-08

### Fixed
- Switching helicopter or vehicle weapons no longer waits on the other weapon: each weapon reloads on its own (carrying on in the background while another is fired) and has its own fire delay. An empty weapon starts reloading as soon as someone aboard can fire it and there is ammunition.
- Hand-held gun tracers leave the gun's barrel on screen at any field of view, while sprinting, aiming or zoomed (the first-person gun is drawn at a fixed 70 degrees). A second, client-only tracer that started at the player's eyes is gone.
- Passengers firing their own guns from a flying helicopter: tracers leave the gun instead of a point behind the helicopter, and move on with it.
- Missile and rocket explosions close to a helicopter no longer cut out: smoke stays solid around the camera instead of thinning away or popping, fire and smoke no longer cut hard edges out of each other, and puffs fade into the ground instead of ending in straight lines. The white shock dome no longer sweeps over nearby helicopters as a flat sheet.
- Pilots and gunners holding fire no longer mine blocks or swing, eat, block or place with whatever is in their hand.
- Taking the controls of a helicopter that is airborne or still has its rotor turning restarts the engine at once instead of making a cold start.

### Changed
- Player gun spread is much tighter: 40% of the previous cone in every state (65% for shotguns, so pellets still spread). The crosshair matches. Mob gunners are unchanged.
- Helicopter weapons reload another 30% faster (0.448x the hand-held reload time).
- Explosion smoke is shaded across the cloud (a lit and a shadowed side) and stays upright as the view banks, so the column reads as solid. Debris leaves thin grey trails instead of thick dark arcs.
- Explosions are heard and shake the view when their shockwave reaches you, so a far blast is seen before it is heard, with a brief screen flash for close blasts in view.
- MI-28 gun tracers are back to their yftm.8 look.
- The AH-6's two miniguns fire alternately from each side, with a smaller muzzle flash.
- Dyed helicopters keep the original finish on rotors, glass, guns, rocket pods and landing gear.
- The MI-28 pilot's missile is renamed 9M-120F Thermobaric Missile.

## 1.7.2+mc26.1.2.yftm.8 - 2026-11-07

### Fixed
- Anchored the minigun's first-person hands to its handles, fixing the raised arm beside the gun.
- Kept the ammo HUD inside the current GUI viewport and measured the spacing between ammo and reserve text.
- Reduced minigun firing and spin volume, and centered the shooter's own audio so turning does not move it around the listener.
- Centered helicopter engine and gun audio for occupants, with quieter cockpit engine volume and positional playback for observers.
- Crossfaded helicopter startup into the flight loop over 1.5 seconds and smoothed RPM gain changes. Taking off and accelerating keep the same flight sound channel.
- Centered MI-28 barrel rotation on the bore in both model detail levels and aligned bullet/flash origins with the model's muzzle tip.
- Attached the MI-28 gunner camera to the barrel, with a stable view over the gun and an unobstructed zoomed view directly along the bore.
- Gun tracers and third-person muzzle flashes start at the gun's drawn muzzle instead of the shooter's eyes, for the shooter in first and third person, for everyone watching, and for passengers firing out of a helicopter. Tracers meet the bullet's true path where it lands, so aim is unchanged.
- Tracers run all the way to whatever stops them, including mobs and players, instead of vanishing a tick early.
- Parked helicopters no longer clank, bounce or lose health from resting on the ground, and no longer sound "Airspeed too low" when boarded.
- Banking vehicle cameras report their forward and left directions correctly (they were reversed while rolled).

### Added
- Right-click a vehicle with a dye to paint it. The body paint takes the dye's colour and keeps its shading, while markings, lights, seats and tyres stay as they are. A water bucket washes it off. Paint is saved and survives packing the vehicle into a crate.
- The MI-28 pilot's 9M-120 is now an incendiary missile: same warhead and the same anti-ground missile ammo as the gunner's, but it sets the ground and anyone caught in the blast alight.
- New missile and helicopter-rocket visuals: a modelled missile with nose, seeker band and swept fins, a white-hot motor glow and flame, and a thick smoke trail that billows and lingers.
- New missile explosions: a white flash, a billowing fireball that cools to soot, a shock bubble and a dust ring racing out across the ground, debris and embers, and a rising smoke column. They are visible up to 512 blocks away.
- In a helicopter, the reversed third-person view (F5 twice) is a wide-angle action camera on the tail boom, looking up the boom at the cabin and banking with the airframe.

### Changed
- Helicopter guns read as heavy rotary guns: thicker, longer, hotter tracers with a bright head, a stronger muzzle flare, and a dust-and-spark puff where each round lands, visible at any distance. Through the MI-28 gunner's zoomed sight, rounds can now be watched all the way to impact.
- Medium missiles break stone and brick far more easily, and large missiles flatten buildings: medium warheads meet 45% of a block's blast resistance, large ones 20%. Obsidian-class blocks are unaffected.
- Missiles fire again sooner: medium anti-ground every 2 seconds (was 5), anti-air 2.5 seconds (was 6), large 3 seconds (was 6), BMP-2 2.5 seconds (was 5).
- Vehicle weapons reload 20% faster again (0.64× their base reload time).
- Gunfire light is brighter and matches the muzzle flare: light level 12 for pistols up to 15 for shotguns, machine guns and launchers (was 8), placed at the muzzle.
- The AH-6 and MI-28 hold two more rows of cargo (45 slots).
- Helicopter rotors coast down after touchdown, for up to twelve seconds on the AH-6 and eighteen seconds on the MI-28. Rotor animation, engine pitch and volume follow the same declining speed.
- The MI-28 gun takes 1.5 seconds to spin up from rest before firing. Barrel rotation and mechanical audio accelerate together; releasing the trigger lets the barrels coast down.

## 1.7.2+mc26.1.2.yftm.7 - 2026-11-06

### Changed
- Helicopters stay off when boarded. Pressing Space starts an eight-second AH-6 or twelve-second MI-28 rotor wind-up; lift remains disabled until operating RPM.
- Replaced AH-6 and MI-28 startup/flight audio with the supplied Freesound recordings 410604 and 81454, using mono positional loops and relative-motion Doppler pitch.
- Used the selected 1:26–1:36 section for MI-28 flight audio and a 46-second AH-6 flight bed with a two-second equal-power crossfade to soften repetition.
- Set the MI-28 gun to the minigun firing loop and the AH-6 gun to a seamless A-10-derived firing loop at 80% amplitude.
- Reduced the MI-28 gun flash to 20% size, 50% opacity, and a one-tick lifetime.
- Increased 70 mm rocket direct/blast damage to 110/60 with a 7-block blast radius, and 80 mm rocket damage to 125/75 with a 9-block radius.
- Increased medium ground missiles to 240 vehicle/90 living damage over 10 blocks, and large ground missiles to 600 vehicle/110 living damage over 24 blocks.
- Replaced medium missile Eyes of Ender with redstone torches and the large missile's Eye of Ender with a redstone block, retaining the higher explosive/engine costs of larger missiles.
- Smoothed first-person gun sprint carry, movement sway, and weapon raising/lowering, with handling and movement speed based on weapon weight.
- Retuned helicopter coasting, braking, strafing, lift, and yaw inertia, with steadier cameras during stops and a flight ceiling above the world's build limit.
- Added hover assistance while crew remain aboard with the pilot seat empty.
- Added minigun barrel spin-up and spin-down, synchronized firing audio, muzzle flares, and brighter tracers.
- Improved helicopter weapon crosshair alignment, missile models, acceleration, exhaust trails, and explosion feedback.
- Added configurable helicopter gun damage to foliage and glass, with block-break permission checks.

### Fixed
- Kept gun arms on their animated grip poses during weapon changes and removed per-shot equip dips.
- Scoped arm-pose checks to the bone and render pass being drawn so unused fallback bones cannot hide the holding hand.
- Made vehicle assembly tables drop once and remove all remaining parts when broken.
- Accepted older vehicle input packets that omit the barrel-roll field.
- Sent vehicle aim updates before firing input so a newly fired round uses the matching crosshair point.
- Made helicopter client prediction check fuel before spinning the rotor.
- Enabled helicopter gun loop activation and gave firing loops an audible initial volume; added checks for actual sound-channel playback and stopping after trigger release.
- Separated Power damage checks from Punch and Flame effects and rejected damage samples with no bullet hits.

## 1.7.2 - 2026-06-24

### Changed
- Set the module version to `1.7.2`.

### Fixed
- Reworked ADS alignment for reflex, monocle, and holographic sights so their open-sight reticles use the Forge 1.20.1-derived scope camera position and each gun's scope mount transform.
- Kept telescopic sight and spyglass ADS corrections on their existing per-rifle path.
- Matched gun draw operation locks to each weapon's authored draw animation length, unlocking shoot, reload, inspect, melee, attachments, and ADS after 85% of the draw animation instead of a fixed delay.
- Preserved held fire and held action-key input through the draw unlock point while preventing released keys from firing late.

### Verification
- Passed `.\gradlew build`.

## 1.7.1 - 2026-06-17

### Added
- Restored the `jeg:repair_kit` recipe from the disabled recipe file while keeping the existing item ID.
- Added a repair kit tooltip explaining anvil repair support for guns and bulletproof armor.
- Added first-person held-gun left/right movement camera sway, separate from recoil handling.
- Added Y-key gun inspect animations for held animated guns, including tooltip guidance.

### Changed
- Increased Terror Phantom and drivable vehicle entity tracking range to 14 chunks so they can stay synced and visible out to roughly 224 blocks.
- Set the module version to `1.7.1`.
- Renamed the visible repair kit item to `Gun Repair Kit` / `枪械维修包` without changing the `jeg:repair_kit` registry ID.
- Changed gun anvil repair material and item `repairable(...)` properties from iron ingots to `jeg:repair_kit`.
- Made `jeg:repair_kit` the bulletproof armor anvil repair material and `repairable(...)` material.
- Converted targeted player-visible hardcoded strings to translation keys in vehicle assembly UI, attachment UI, gun overheating tooltip/message, Terror Raid bossbar, and command feedback.
- Reduced rocket and missile block damage and splash reach when directly hitting vehicles, while keeping direct vehicle hit damage intact.
- Increased aircraft no-input descent acceleration so powerless aircraft descend at roughly three times the previous terminal speed.
- Updated vehicle missile profiles, increasing ground-attack missile turn limits and retuning the 9M336 air-target missile turn limit.
- Increased helicopter maximum descent speed to `1.05D/tick` and made unmanned, unpowered, and critically damaged helicopters descend smoothly toward that speed.
- Slowed unmanned helicopter forced descent so falling from the old safe descent speed to max descent takes about 20 seconds.
- Changed unmanned, unpowered, and low-health helicopter forced descent back to a linear curve with 70% of the previous fast transition as the base step, and made low-health forced descent start at 20 health.

### Fixed
- Fixed Flamethrower reload timing so gameplay reload completion matches the authored 10.25 second first-person reload animation.
- Fixed Grenade Launcher reload animation so it uses the authored single reload clip instead of missing segmented reload loop/stop clips.
- Fixed repair kit behavior so it no longer repairs vehicles on right-click; repair-tool vehicle repair remains intact.
- Fixed helicopter and aircraft impact damage so controlled slow landings no longer explode from accumulated height alone, while uncontrolled high falls still deal crash damage.
- Restored helicopter rotor block-contact damage while airborne.
- Kept inspect playback from being interrupted by melee, draw, and normal sprint poses; shooting, reloading, and bayonet sprinting can interrupt it and replay starts from the beginning.
- Changed helicopter vertical crash damage to scale only with descent speed, keeping `0.35D/tick` landings safe and capping full-speed vertical impact damage near 70% of max health.
- Kept helicopter rotor motion and engine sound active while helicopters are descending in unmanned, unpowered, or low-health states.
- Let helicopter rotor speed wind down gradually after players leave, matching normal landed shutdown behavior.
- Restored fatal high-speed helicopter impact damage for block and vehicle collisions while leaving non-vehicle entity collisions unchanged.
- Played the missile warning alert for helicopter passengers during unsafe descent.
- Played the helicopter warning alert during forced descent even while the current descent speed is still within the safe-speed range.
- Prevented helicopter lift from holding forced-descent states above their target descent speed.
- Made helicopter forced descent accumulate from the tick-start vertical speed so air drag cannot cap the fall around a low terminal speed.
- Prevented active vehicle engines from continuing to move at very low energy without draining the remaining energy.
- Made missile and helicopter danger warnings flash red, increased helicopter warning volume, and added helicopter HUD warnings for low speed, power loss, and critical damage.
- Moved vehicle warning prompts to a flashing crosshair HUD message instead of the old passenger actionbar position.
- Stopped recovered helicopters from continuing low-health self-destruct decay after being repaired above the critical threshold.
- Prevented no-energy helicopters from spinning rotors or playing engine sound from pilot input alone.
- Prevented stopped helicopters from briefly twitching their rotors when the pilot exits.
- Smoothed fixed-wing no-pilot, no-energy, and critical-damage forced descent from the current vertical speed instead of snapping to maximum descent.
- Lowered the helicopter low-health self-destruct threshold to 20 health.
- Kept stopped helicopter rotors at zero when the pilot exits instead of spinning up briefly before winding down.
- Kept helicopter low-speed warnings from switching into the missile incoming warning while forced descent continues.

### Localization
- Expanded `zh_cn.json` to cover every key present in this branch's `en_us.json`.

### Verification
- Passed `.\gradlew compileJava compileClientJava clientClasses`.
- Passed `.\gradlew compileJava compileClientJava` after the vehicle direct-hit explosion tuning.
- Passed `.\gradlew compileJava compileClientJava` after the air vehicle impact/descent tuning.
- Passed `.\gradlew compileJava compileClientJava` after the gun animation fixes.
- Confirmed `repair_kit.json` parses and outputs `jeg:repair_kit`.
- Confirmed `zh_cn.json` has no missing keys relative to `en_us.json`.
- Full `build` and in-game gameplay checks were not run.
- Passed `.\gradlew compileJava compileClientJava`.
- Passed `.\gradlew compileJava compileClientJava` after the helicopter descent, crash damage, and feedback tuning.
- Passed `.\gradlew compileJava compileClientJava` after the helicopter rotor, descent, fatal impact, and warning updates.
- Passed `.\gradlew compileJava compileClientJava` after the exponential helicopter forced-descent curve update.
- Passed `.\gradlew compileJava compileClientJava` after increasing the helicopter forced-descent base step to 120%.
- Passed `.\gradlew compileJava compileClientJava` after restoring linear forced descent and widening forced-descent warnings.
- Passed `.\gradlew compileJava compileClientJava` after preventing forced-descent lift from limiting descent speed.
- Passed `.\gradlew compileJava compileClientJava` after making forced descent accumulate before air drag.
- Passed `.\gradlew compileJava compileClientJava` after the low-energy engine and helicopter warning updates.
- Passed `.\gradlew compileJava compileClientJava` after crosshair warnings, repair recovery, no-energy rotor gating, rotor-exit stability, and fixed-wing forced-descent smoothing.
- Passed `.\gradlew compileJava compileClientJava` after the stopped-rotor exit, low-speed warning, and 20-health self-destruct threshold fixes.

## 1.7.0-hotfix150620261622 - 2026-06-15

### Fixed
- Fixed recipe loading so joining players receive the full active `jeg` recipe set.
- Fixed recipe distribution on player login so loaded recipes are consistently assigned instead of dropping entries on Fabric 26.1.

## 1.7.0-hotfix140620261037 - 2026-06-14

### Fixed
- Fixed a dedicated Fabric 26.1 server crash when reload action-bar feedback tried to inspect client-only player methods and loaded `net.minecraft.client.resources.sounds.SoundInstance`.
- Kept reload gameplay, animation state, and client rendering behavior unchanged; only the server-safe action-bar feedback path was changed.

## 1.7.0 - 2026-06-12

### Added
- Added Forge-style gun melee handling on the V key, including flashlight toggles, baseline melee sweeps, sword bayonet damage, and first-person `melee`/`bayonet` animations for animated guns.
- Added extended and drum magazine items for rifle, SMG, and shotgun magazine-fed reloads, using the same capacities as the old extended-mag and drum-mag attachment modifiers.
- Added loaded-magazine item tracking for magazine-fed guns so the current base, extended, or drum magazine type controls capacity, returned magazines, and gun-model magazine visibility.
- Added yellow tooltip hints for gun attachment-screen access and attachment items, including the magazine-feed incompatibility warning on the drum-mag attachment.

### Fixed
- Fixed magazine-fed servers so extended-mag and drum-mag attachments can no longer be installed on guns or used from old gun data to bypass magazine-fed reload behavior.
- Fixed magazine-fed reload swaps so full guns can replace the current magazine with a different valid magazine type, and reload completion validates the exact magazine selected when the reload began instead of re-scanning inventory.
- Fixed magazine-fed reload visuals so animated gun models show the old magazine type during the first half of reload and the new magazine type during the second half.
- Fixed interrupted reloads so switching away cancels reload progress and switching back replays the gun draw animation visibly instead of continuing or hiding a stale reload pose.
- Fixed first-person gun draw animations so they only trigger when switching from another hotbar item into the gun, not after inventory screen refreshes or reload state cleanup.
- Fixed reload animations ending early when an authored GeckoLib reload clip is longer than the gun's gameplay reload timer.
- Fixed first-person sprint animations so sprinting immediately after switching to a gun can interrupt lingering draw playback before firing.
- Fixed sword bayonet melee so V-key hits use target-center arc checks and the proper barrel attachment transform.
- Fixed bayonet first-person animation priority so switching to a bayonet-equipped gun plays the draw animation and sprinting with a bayonet uses the bayonet charge animation instead of the normal run pose.
- Restored Forge 1.20.1 explosive-muzzle entity-hit ignition while reducing explosive-muzzle armor piercing by 25% for balance.
- Fixed draw, reload, shoot, sprint, idle, and melee animation priority so first-person GeckoLib controllers recover cleanly after reload cancellation, hotbar switches, and local gunfire.
- Fixed grenade launcher grenades so launcher-fired grenades explode promptly instead of using an excessive fuse.
- Fixed telescope-sight ADS mouse sensitivity and FOV behavior so scoped aiming matches the 1.21.1 feel more closely.
- Fixed the attachment screen gun preview size and vertical placement on the 26.1 item preview pipeline.
- Fixed the attachment screen mod label for built-in JEGN guns.
- Fixed the service rifle rear grip so it renders correctly when no stock is installed.
- Removed temporary animation and overheat debug logging added during the reload/draw diagnosis pass.

### Documentation
- Documented magazine-fed extended/drum magazine item parity and capacity-attachment gating.
- Updated the NeoForge 1.21.1 attachment-port sync notes with the final draw/reload replay behavior and triggerable draw controller handoff.

## 1.6.2 - 2026-05-27

### Changed
- Rebalanced damaged sky ship armada loot so pistol and rifle ammo are more common while iron and lapis are less dominant.
- Tuned vehicle fall impact damage to scale more noticeably with drop height.

### Fixed
- Fixed high vehicle falls sometimes landing without impact damage.
- Fixed vehicle impact sounds playing inconsistently and stopped vehicle sounds from consuming streaming sound handles.
- Fixed vehicle cannon muzzle flares and gun muzzle flashes rendering too faint on the 26.1 particle pipeline.
- Fixed vehicle machine gun and coaxial machine gun fire sounds so sustained fire plays reliably.
- Fixed Waterpipe Shotgun reload animation falling into missing segmented reload clips.

## 1.6.1 - 2026-05-25

### Theme
- Enemy vehicle spawning and recovery.

### Added
- Added an admin login tip showing `/justEnoughGuns config vehicle enemySpawning enabled false` so server admins can disable enemy vehicle spawning.

### Fixed
- Fixed enemy BMP2 recovery after server restarts and improved unstuck behavior when enemy vehicles block each other.
- Fixed truck model collision coverage while keeping large AABB collision and ram damage disabled.

## 1.6.0 - 2026-05-23

### Added
- Added vehicle muzzle flare visuals and missile explosion effects.
- Added a server vehicle assembly toggle with action bar feedback when vehicle assembly is disabled.
- Added an action bar prompt when using a vehicle container without a crowbar.
- Synced Happy Ghast armored joy harness behavior, HUD support, and damage handling.

### Changed
- Reworked gunner growth and loadout progression with per-type progression data and configurable mob scaling commands.
- Tuned bolt rifle gunner progression and increased minigun armor-piercing strength.
- Refined the mob config command hierarchy for gunner spawn, growth, armor, and weapon tuning.
- Added configurable gunner terrain placement controls.

### Fixed
- Fixed vehicle entity collision opt-in and aligned vehicle collision bounds with OBB data.
- Fixed OBB vehicle entity collision, including speedboat collision behavior.
- Moved and tuned the MI-28 dismount position to keep players clear of missile pods.
- Fixed server-side class loading by keeping client-only vehicle effect code off dedicated servers.
- Fixed gunner spawn and progression scaling so natural spawns and spawn eggs receive the intended loadouts.
- Fixed minigun third-person render alignment on the 26.1 item model path.
- Fixed oversized gunner ammo drops.
- Fixed rocket gunner friendly-fire aggro.
- Fixed faction raid startup scheduling.
- Fixed gunner support block placement pacing.

### Removed
- Removed obsolete joyous armor plate item remnants and loot references.

## 1.6.0-pre - 2026-05-16

### Added
- Synced the configured vehicle system to Fabric 26.1, including data-driven vehicles, inventories, assembling recipes, charging station support, repair tools, missile and decoy entities, vehicle HUDs, controls, cameras, and dedicated renderers.
- Added LAV-150, BMP-2, speedboat, truck, AH-6, MI-28, A-10, TOM-6, HPJ-11, laser tower, and waveforce tower vehicle content and assets.
- Added SuperbWarfare-aligned vehicle weapons, missiles, sounds, icons, models, textures, recipes, assembly data, and alignment documentation.

### Changed
- Tuned land, boat, and aircraft vehicle handling, camera behavior, seat positions, passenger visibility, weapon stations, reverse speeds, collision boxes, armor profiles, and damage behavior.
- Tuned LAV-150 and BMP-2 weapon, mobility, turret, missile, and armor behavior.
- Increased vehicle charging station throughput and added repair-tool vehicle support.
- Increased speedboat, LAV-150, and BMP-2 smoke-screen height, thickness, and density.

### Fixed
- Fixed vehicle sync, dismount, ghosting, menu validation, hotbar selection, reload HUD, missile lock, target-warning, and blocked-wall handling issues.
- Fixed MI-28 co-pilot weapon state, MI-28 turret pitch limits, speedboat turret control, BMP-2 target road speed, vehicle ammo display names, and duplicate vehicle reload timer display.
- Synced helicopter zoom camera/passenger fixes, deployed vehicle ten-percent starting energy, and first-person repair-tool player arm rendering.
- Reduced vehicle debug logging noise.

### Documentation
- Added vehicle SuperbWarfare alignment mapping documentation.

## 1.5.1pre2 - 2026-05-09

### Changed
- Aligned muzzle flash and trail rendering with the current branch behavior.

## 1.5.1 - 2026-05-07

### Added
- Added custom bulletproof helmet and vest textures.
- Added ballistic armor interception for bulletproof helmets, bulletproof vests, and Bound Terror Phantom protection.
- Added gun tooltip armor-piercing display and bulletproof armor tooltip values for rating, undermatch multiplier, and overmatch multiplier.

### Changed
- Replaced bulletproof armor Projectile Protection behavior with ammo AP versus armor rating mitigation.
- Set helmet ballistic ratings to 80% of the same-tier vest rating.
- Restored balanced gun stats and preserved recent NeoForge ADS transform fixes.

### Documentation
- Documented the ballistic armor interception formula and durability pressure model.

## 1.4.2 - 2026-04-27

### Added
- Added configurable Phantom Gunner death explosions with `/justEnoughGuns config mob phantom deathExplosion`.

### Changed
- Synced NeoForge 26.1 gun HUD, dynamic crosshair, hit marker, spread, and overheat behavior with the NeoForge 1.21.1 1.4.2 implementation.
- Added configurable ammo HUD, timer HUD, crosshair, hit marker, and dynamic crosshair dot settings.
- Updated player gun spread to combine firing spread accumulation with movement spread degrees.
- Reduced light machine gun passive overheat cooling and lowered minigun heat gain.

### Fixed
- Hit markers now trigger only after successful living-entity bullet damage, including direct explosive hits, and no longer trigger for block hits or failed/friendly-fire hits.
- Overheat now renders with the timer texture while preserving water-cooling progress rendering.
- Fixed magazine-fed guns showing "No ammo" instead of "No compatible magazine" when no suitable loaded magazine is available for reload.

## 1.3.1 - 2026-02-20

### Added
- Added NeoForge 1.21.11 faction patrol/raid runtime modules (`FactionEventTicker`, patrol encounter tracking, home-raid trigger flow, raid controller entity) aligned with newer branch behavior.
- Added faction patrol boss bar UI and localized messages for patrol/omen/home-trigger events.
- Added `Faction Omen` status effect registration and icon resource (`textures/mob_effect/faction_omen.png`).
- Added patrol spawn diagnostics in command feedback/logs, including detailed spawn-failure categories for troubleshooting.
- Added configurable day-scaling gunner accuracy controls (`gunnerAccuracyStartDay`, `gunnerAccuracyDaysToMax`, `gunnerAccuracyMaxSpreadMultiplier`).
- Added configurable gunner shotgun spread control (`gunnerShotgunSpreadMultiplier`).

### Changed
- Crosshair is hidden while the player is holding any gun item (main hand/offhand).
- Faction raids are now bound to patrol omen faction context; patrol boss bar duplication was removed.
- Home-return raid trigger consumes faction omen and starts raid near player respawn/home position.
- Patrol spawn position sampling now prefers valid surface positions near target players.
- Path setup at spawn now uses tolerant fallback flow and records path-init failure for diagnostics.
- Same-faction gunners are treated as friendly targets and no longer attack each other.
- Gunner loadouts are locked: gunner mobs are prevented from picking up loot and can recover a gun when disarmed.
- Non-flamethrower guns now use `IGNORE_LEAVES` bullet collision behavior.
- Weapon handling pass updated recoil/movement spread behavior by weapon class.
- Weapon durability baseline was increased (about 2.5x), with additional durability/gravity tuning pass.

### Fixed
- Fixed raid ground spawns to avoid leaves and unsafe relocation outcomes.
- Fixed raid mob unreachable timeout behavior via forced repath/relocation.
- Fixed unreachable-cleanup edge case when no nearby players are present.
- Unified raid target validation and constrained terror raid target range.
- Removed monster auto-win outcome when no players are inside raid radius.
- Tightened empty-fire visual handling and removed empty-fire recoil animation side effects.
- Fixed machine-gun overheat bar visibility on NeoForge HUD.

### Balance
- Rebalanced terror phantom survivability and protection:
  - adjusted HP/protection values
  - set armor to `6`
  - increased projectile protection to `5`
- Reworked rocket damage model:
  - adjusted blast damage/falloff
  - increased direct-hit damage and ensured direct hits bypass terror projectile protection.
- Rebalanced recoil/spread across weapon classes and removed flamethrower recoil.
- Added/updated tiered low armor values for bulletproof helmets and vests.
- Gunner shot spread now scales with in-game day progression; shotgun pellet spread is tighter by default.
