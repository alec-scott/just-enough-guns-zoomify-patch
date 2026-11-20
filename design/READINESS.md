# Fleet readiness: from first pass to shippable

Started 2026-11-17 at the user's request: take every vehicle from its current pass to a finished product that can ship
in the yftm pack, then mark it **Ready for human testing**. The user tests every vehicle together at the end.

A vehicle is ready when every gate below is met, or a gap is written down as a deliberate limit, and the evidence is
in its `design/<vehicle>/IMPLEMENTATION_STATUS.md`. The gates restate the vehicle guide's "What done means" and the
player's checklist ([../docs/vehicle-development-guide.md](../docs/vehicle-development-guide.md)).

## Gates

| # | Gate | Met when |
| --- | --- | --- |
| G1 | Model and look | Real proportions; no z-fighting or holes from any side; texture in the JEG style; LOD; icon; paint |
| G2 | Animation | Every moving part moves: tracks or wheels, turret and gun, recoil, hatches, rotors or props, control surfaces |
| G3 | Hitbox | Follows the silhouette; turret parts follow the turret; shots beside it miss |
| G4 | Crew and handling | Seats right; every seat's view; F5 camera sees the whole vehicle without stutter; handles like its peers |
| G5 | Weapons | Every weapon fires from its muzzle and reaches its full range; one aiming mark per view |
| G6 | Sound | Real recordings (no voices, no synth for recognisable sounds); idle/load/cab/distant layers; start and stop; far tracks; static loops |
| G7 | Lights | Head, tail, brake and reversing lamps (road vehicles), nav lights (aircraft), instruments; night from every seat under shaders |
| G8 | HUD and prompts | Compact panel; key names from bindings; prompts where the player is |
| G9 | Damage | Smoke and fire by hull left; a wreck that cooks off and burns (boats sink) |
| G10 | Pack | A `-PwithPack` run of its tests; Carry On blacklist; IPN on its screens; pack mods touching its job |
| G11 | Sandbox and docs | In a sandbox world fully equipped; README and IMPLEMENTATION_STATUS current |
| G12 | Played | The player's checklist played through mcpfabric in the pack, every view, firing and driving |
| G13 | Weight and feel (user, 2026-11-17) | Mass-scaled response (`"mass"`, `inertia()`); the hull squats, dives, rolls and lands on its suspension; the crew feels it (rumble, rock, jolts); **every movement has a real, fitting sound** (engine, tracks or tyres, turret drive, gun elevation, brakes, landing knock, water entry) |

Never on the build side: **listening** (the user's ears), the **dedicated server's own mods** and **multiplayer**.
Each vehicle's status lists the sounds to listen to.

## Shared work (done once for many vehicles)

| Item | Vehicles | State |
| --- | --- | --- |
| Ground wrecks: land vehicles and guns cook off and burn as a charred hulk instead of vanishing | all land and artillery | done 2026-11-17 (`VehicleEntity.goesDownAsWreck`, `GroundWreckClientGameTest`) |
| Boats sink as a wreck | speedboat | done 2026-11-17 (`sinksAsWreck`, `BoatWreckClientGameTest`) |
| Real start and stop recordings (`prepare_starts.py`), the loops waiting for the catch | BMP-2, LAV-150, PLZ-05, YX-100 | done 2026-11-17; cab recordings: none exist under CC0 (limit) |
| Tyre noise on gravel for wheeled hulls (`GroundVehicleAudio` "tyres") | LAV-150 | done 2026-11-17 |
| Stand-in mob drivers in tests need the crew tag since yftm.22 (15 tests were silently testing a dead vehicle) | tests | done 2026-11-17 |
| Hidden seats hide every occupant, not only the local player (`EntityRendererHiddenPassengerMixin`) | all enclosed vehicles | done 2026-11-17 |
| A turret's gunner seat is manned only by a player or crew, never a carried mob (`turretSeat`) | BMP-2, speedboat | done 2026-11-17 |
| Swimming land vehicles (`"amphibious"`) | BMP-2, LAV-150 | done 2026-11-17 |
| Road speeds as the briefs state (top speed is where friction levels off, 0.6 × `max_forward_speed`; data now set so it equals the brief's) | LAV-150 88, BMP-2 65, PLZ-05 56 km/h | done 2026-11-17 |
| Hatch seats that ride the turret from data (`"turret_riders"`) | LAV-150 | done 2026-11-17 |
| Autocannon muzzle smoke that clears between rounds | BMP-2, LAV-150 | done 2026-11-17 |
| A load on the FSV-6's hitch weighs on its drive (`drivenMass`/`driveInertia`; every fleet vehicle and gun now has a real `"mass"`, used on guns and aircraft only as tow load) | FSV-6 and all it tows | done 2026-11-18 (`SupportTowClientGameTest`: 0.69 towing the LAV-150, 0.89 alone) |
| Weight and feel (G13): `"mass"` and `inertia()` in the land physics; `VehicleFeel` (suspension pose, crew camera, movement sounds: turret drive, elevation, servos, handwheels, brakes, landing knock, splash; `prepare_movement.py`) | all land vehicles and guns | done 2026-11-17 (`VehicleWeightClientGameTest`) |

## Vehicles

| Vehicle | Id | Where it stands | Status |
| --- | --- | --- | --- |
| BMP-2 | `bmp2` | finalised 2026-11-17 ([status](bmp2/IMPLEMENTATION_STATUS.md)) | **Ready for human testing** |
| LAV-150 | `lav150` | finalised 2026-11-17 ([status](lav150/IMPLEMENTATION_STATUS.md)) | **Ready for human testing** |
| PLZ-05 | `plz05` | finalised 2026-11-17 ([status](plz05/IMPLEMENTATION_STATUS.md)); the AP/CM/GS shells and an observer past 512 blocks not played | **Ready for human testing** |
| YX-100 | `yx100` | finalised 2026-11-17 ([status](yx100/IMPLEMENTATION_STATUS.md)); the weapon station and the AP/CM/GS shells not played | **Ready for human testing** |
| FSV-6 | `truck` | finalised 2026-11-18 ([status](support/IMPLEMENTATION_STATUS.md)): mass and road speed, tyres, air brakes, tow bar drawn, tow load; played (pack-up, road, night) | **Ready for human testing** |
| Speedboat | `speedboat` | finalised 2026-11-18 ([status](speedboat/IMPLEMENTATION_STATUS.md)): sinks as a wreck, real start/stop, hull water, banking and chop; played (plane, beach, night lights) | **Ready for human testing** |
| HPJ-11 | `hpj11` | finalised 2026-11-18 ([status](hpj11/IMPLEMENTATION_STATUS.md)); real CIWS recordings near and far, spin-up and down, radar hum, wreck | **Ready for human testing** |
| Sentry | `sentry` | finalised 2026-11-18 ([status](sentry/IMPLEMENTATION_STATUS.md)); heard on automatic (was silent), a real Negev report, dry-fire click, glowing lamp; the wall, line-of-fire and wreck tests | **Ready for human testing** |
| Bush plane | `tom6` | finalised 2026-11-18 ([status](bushplane/IMPLEMENTATION_STATUS.md)): tail-down stance, moving control surfaces, real light-aircraft recordings; played | **Ready for human testing** |
| 81 mm mortar | `mortar` | released yftm.21; checked 2026-11-18 ([brief](mortar/BRIEF.md)): test with the pack, played at night | **Ready for human testing** |
| Mk42 | `mk42` | SBW port; finalised 2026-11-18 ([status](mk42/IMPLEMENTATION_STATUS.md)): test with the pack, played at night, wreck | **Ready for human testing** |
| BL-132 | `bl132` | SBW port; finalised 2026-11-18 ([status](bl132/IMPLEMENTATION_STATUS.md)): test with the pack, played at night | **Ready for human testing** |
| TOW | `tow` | finalised 2026-11-18 ([status](tow/IMPLEMENTATION_STATUS.md)); a real BGM-71 launch, played at night | **Ready for human testing** |
| Type-63 | `type63` | SBW port; finalised 2026-11-18 ([status](type63/IMPLEMENTATION_STATUS.md)): test with the pack, played at night | **Ready for human testing** |
| 105 mm howitzer | `howitzer` | finalised 2026-11-18 ([status](howitzer/IMPLEMENTATION_STATUS.md)); a real field gun's report near and far, played at night | **Ready for human testing** |
| A-10 | `a10` | reference; audited 2026-11-18: `A10Flight`, `A10Taxi`, `AircraftNightLights` pass with `-PwithPack` after the fleet-wide changes (HUD, masses, wrecks, sounds); nothing regressed | **Ready for human testing** |
| AH-6 | `ah6` | reference; audited 2026-11-18: `HelicopterStartup`, `HelicopterWeapons`, `HeliWreck` pass with `-PwithPack` after the fleet-wide changes (HUD, masses, wrecks, sounds); nothing regressed | **Ready for human testing** |
| Mi-28 | `mi28` | reference; audited 2026-11-18: `HelicopterStartup`, `HelicopterWeapons`, `Mi28GunnerAim`, `HeliWreck` pass with `-PwithPack` after the fleet-wide changes (HUD, masses, wrecks, sounds); nothing regressed | **Ready for human testing** |
| BD-40 | `bulldozer` | reference; lane lock, ripper restock, new HUD (2026-11-17/18); audited 2026-11-18: `BulldozerBank`, `BulldozerCutTurn`, `BulldozerAudio`, `BulldozerLaneLock`, `BulldozerRipperLane` pass with `-PwithPack` after the fleet-wide changes (HUD, masses, wrecks, sounds); nothing regressed | **Ready for human testing** |

## For the user's test

The whole fleet is in the `ArmourTest` world (rebuilt 2026-11-18 by `ArmourSandboxSetupClientGameTest`): the row along z 0
(guns, armour, FSV-6, mortar, TOW, HPJ-11, sentry), the speedboat on the lake east of it, the BD-40 north of the lake,
and west of the row on a runway south the bush plane, A-10, AH-6 and Mi-28. Every hold is full of its ammunition.

Things to try on each vehicle marked ready, with what to listen for, are in its status file under "Not covered yet".
Shared across the armour: drive off a bank into the ArmourTest lake (BMP-2, LAV-150 swim), brake hard from speed
(the hull dives, the brakes hiss), slew the turret (its drive whines), and destroy one (it cooks off and burns).

## The eight-point review (2026-11-19)

| # | Vehicle | Asked | Done |
| --- | --- | --- | --- |
| X1 | speedboat | on automatic the driver shouldn't control the gun | **fixed**: on automatic the driver no longer stands in for an empty gunner's seat (`VehicleEntity.turretFallsBack`, off while an `AutoDefenceEntity` is automatic): they can neither lay nor fire it, only switch it (N). A gunner still takes it by hand. `Speedboat`: on automatic the turret seat is the gunner's on both sides and the driver may not fire; back on manual the gun is the driver's again. Then ("speed boat bugs out if the sentry is on auto mode"): with the gun off the driver's seat the boat took its heading from the driver's view (`VehicleEntity.yawFollowsRider` asked who has the gun now, not whose seat can work it), so on automatic, or with a gunner aboard, it swung to wherever the driver looked; the driver now always steers with the keys. `Speedboat`: on automatic, the driver looking 60 degrees aside, the heading holds |
| X9 | sentry, speedboat | no minigun charge-up sound on the boat's gun and the tripod sentry | **fixed**: on automatic every auto defence asked for the rotary gun's spool-up, so the two machine guns played the minigun's wind-up; only a rotary gun (the HPJ-11's) spools up now (`AutoDefenceEntity.rotaryGunDemanded`). `Sentry`: no spin while it engages (pending the build, after the user's session) |
| X10 | A-10 | give it back its white colour | **done**: its own light paint again (white in the game): the Compass Ghost two-tone taken off, the wear and grime light; the DM tail codes, gun gas and exhaust staining kept, softer (`design/a10/retexture.py`) |
| X11 | A-10 | fix the engine animation and model; the orange in the nozzles sometimes clips out as terrain loads, leaving black | **fixed**, three faults. The model (the user's picture is the left intake): the fan disc is a 20 px square painted with a round fan, its corners clear; today's rebake gave its 1 px edge faces their own texels and they turned solid, so the square's rim and corners (14 px from the axis, the cowling 11-12) stood out of the nacelle, turning with the fan. The disc has only its front and back faces now (`design/a10/a10_base.geo.json`, `retexture.py` re-run). The animation: the intake's eight-panel lip (`qianwaiyuan`) was spun and blurred as if it were the fan, so it turned out of line with the cowling and its blurred copies stood out round the intake; now only the fan disc and spinner turn and blur (`AbstractVehicleGeoRenderer.updateA10`, `VehicleFanBlurLayer`). The glow: under a shader pack it is drawn emissive and blended with the soft glow every vehicle light uses there, not as additive "eyes" quads (`VehicleExhaustGlowLayer`). Played: parked and at full power from the user's angle, and a low pass over new chunks with the glow in every frame of a 5 s burst. `A10EngineCloseup`: close in front of each intake and behind each nozzle |
| X12 | A-10 | flaps come out from the bottom again | **fixed**: today's speed brakes opened the lower deceleron halves down out of the wing, against the user's rule of 2026-11-15. The lower halves stay shut; an aileron only rises, both halves together; the speed brake lifts the upper halves alone. `A10DiveBrake` |
| X13 | A-10 | the speed counter counts the speed wrong | **changed, needs the user's eye**: it showed the jet's velocity value; it now shows the distance actually flown a tick, eased (`VehicleHudOverlay.jetSpeed`). In level flight the old and new readings agree (`A10Flight`: 49.4 m/s shown, flown and velocity), so the fault the user saw wasn't reproduced. Played: 0 m/s at full throttle on the brakes, 50 m/s in a low pass. Units are m/s (about 180 km/h at that speed) |
| X14 | A-10 | the gun inside the cockpit should be louder | **done**: the cockpit gun was mixed at half the outside gun, and a sound played in the cockpit can't go above full volume, so the file is raised about 4.5 dB with soft saturation (`design/a10/cockpit_gun.py`, the old files kept beside it). Needs listening by ear |
| X15 | Mk42, BL-132, PLZ-05, YX-100 | change GS shells to incendiary shells which explode over a target leaving flames below it | **done**: the GS shells are incendiary (INC), grapeshot gone (its ids kept, so shells already made load as incendiary). Armed after 30 blocks, a height fuze bursts the shell 7 blocks over where it would come down (8 on the BL-132; at most 16 short of it on a flat shot), with a flash, a small blast (40 within 4) and 28 burning fragments (36 on the BL-132) raining over 5 blocks round the spot (6): 12 each on what they strike, which burns 8 s, and fire where they land. No fire with `tnt_explodes` off, in spawn protection or claims; targets still burn. Recipe: blaze powder, copper, gunpowder; new orange IN icon. Played: YX-100 fired at 45 m in the ArmourTest sandbox, the ground ahead burning. `IncendiaryShell`: steep shot bursts 7.0 up, 20+ fire blocks, 6 of 6 husks alight; with `tnt_explodes` off no fire, 6 of 6 alight; YX-100 with the real keys, 26+ fire blocks, 6 of 6, tank unhurt |
| X2 | BMP-2 | tracks clip into the dirt in a hard turn | **fixed**: the suspension's roll out of a turn and dive on braking (`VehicleFeel`) turned the whole model, tracks too, about its middle, so the outer track (and, braking, the front of both) sank into the ground. They now move the hull's bones alone (`AbstractVehicleGeoRenderer.SPRUNG`: the BMP-2, PLZ-05, YX-100 and LAV-150), the running gear staying on the ground. `TrackTurn` |
| X3 | all guns with GS | what does GS do, it looks like it does nothing | **superseded by X15** (GS is incendiary now). Was **answered and made visible**: GS is canister, 100 iron balls in a 7 degree cone from the muzzle (10 damage each, 11 on the BL-132), for men and light vehicles at short range; no blast, nothing to blocks or armour beyond what the balls hit. They flew unseen (15 to 40 blocks a tick, striking within a tick), leaving only small sparks; now each ball's path hangs as a pale streak fading over a third of a second (`CanisterStreaks`, traced client side to what it strikes), sparks off armour where it strikes, and the balls cut grass, flowers, crops, leaves and glass they cross (`VehicleGunBlockDamage`, as a helicopter's rounds, with claims and the server switch). HUD "Grapeshot (100)" (it said 36). `Yx100Grapeshot`: the streaks from the side (100 drawn), grass cut |
| X4 | PLZ-05 | tracks clip into the dirt in a hard turn | **fixed** with X2. `TrackTurn` |
| X5 | Mk42, BL-132 | their reloads should sound different | **done**: both had the same case drop and ram over them. The Mk42 is now an automatic loader: an electric drive labouring, the mechanism's clatter, a crisp breech slam and a hydraulic hiss; the BL-132 a crew loading by hand: two big cases booming onto the deck and two heavy rams, no motor (`design/mk42/prepare_reload_audio.py`, CC0 recordings). Needs listening by ear |
| X6 | bush plane | reduce the wind sound dramatically | **done**: it borrowed the A-10's canopy roar at full strength; a propeller aircraft now hears it at 15% and a little deeper (`JetAudio.PROP_AIRFLOW_SHARE`) |
| X7 | A-10 | remove the crosshair in first person | **done**: from the cockpit no gun marker over the nose (as the helicopters' first person); the chase camera keeps it. `A10Look`: the pilot's view with the HUD on |
| X8 | BL-132 | a darker navy colour to give it more personality | **done**: a navy blue-grey (was haze grey like the Mk42) that reads as a light steel blue under the pack's shaders, which the user chose over a deeper navy tried after it; the director's dome too, salt and rust showing on the dark paint (`detail_texture.py` bl132) |

## The A-10, BD-40 and FSV-6 room (2026-11-19)

| # | Vehicle | Asked | Done |
| --- | --- | --- | --- |
| W1 | FSV-6 | smoke coming up through the deployed floor | **fixed**: the generator's stack stood under the right slide-out, so deployed its smoke rose through the room. It now vents sideways, out from under the slide-out and blown clear of its wall (`SupportRig.GENERATOR_OUTLET`); and no smoke puff is drawn inside a deployed FSV-6's room (`SupportVehicleEntity.insideClientRoom`, checked by the blast renderer), whatever its source. `SupportLook`: deployed with the generator running, no smoke in the room |
| W2 | FSV-6 | the dashboard inside looks poor quality | **redrawn**: the wall console, the beacon terminal and the cab's status screen at five times the resolution in the HUD's Inter, on dark glass with a fine grid, rounded tracks and chips, a title bar with an icon and an ONLINE lamp (every live element in its old place); the blurry "FSV-6" plates over the wall screens (a 30 x 14 tile stretched onto 9 x 1 px) are gone; the beacon's level lamps are dark when off (an "off" colour drawn emissive showed as beige squares) |
| W3 | BD-40 | texture upgrade | **done**: every face its own texels at 4 a pixel, each laid with its material's tile; the detail pass with a working dozer's wear: soil packed on the blade's face and the track shoes, rust on the blade's back, soot round the stack, oil weeping down the engine's sides, mud on the running gear; the cutting edge, chrome rods, lamps and gauges kept clean; the "BD-40" badge lettered sharp (its plate at four times the density) and moved to a clear panel; hazard chevrons drawn at the bake's density |
| W4 | BD-40 | earth building up on the blade | **done**: a mound of earth against the moldboard (`blade_spoil`), grown from the cutting edge while the blade cuts, held while it pushes on or stands, spilling off in clods as the blade lifts and left behind when it backs off. `BulldozerLoad`: 0.71 while cutting a bank, 0 after lifting |
| W5 | A-10 | animation upgrade | **done**: the split decelerons deflect as ailerons and open apart as the speed brakes; elevators and rudders follow the airframe's rates; the canopy stands open while it is parked with nobody aboard and closes when a pilot climbs in. The model already had the surfaces as hinged bones (`design/a10/articulate.py` names them and moves the canopy's hinge). The gear already retracted. Seen in `A10DiveBrake` (brakes open, from behind and at the wingtip) and `A10Look` |
| W6 | A-10 | texture upgrade | **done**: every face its own texels (`refit_lib.rebake_imported`, 2 a pixel; its left and right halves had shared 88% of the atlas, its faces stretched 4 x 2 texel patches); the Compass Ghost greys, panel seams and rivets, worn edges, the GAU-8's gun gas on the nose, exhaust soot on the nacelles and blown back over the tail, fluid streaks under the wings; "DM" and "AF 80-204" on the fins (`design/a10/retexture.py`) |

## The model, texture and sound review (2026-11-19)

The user asked for a model and texture review of every vehicle ("it should be high quality and look realistic") and a
recording of its own for each tank. Every vehicle was rendered and measured (texels a model pixel, colours, how much
texture faces share); the lacking ones were reworked:

| # | Vehicle | Found | Done |
| --- | --- | --- | --- |
| U1 | Mk42 | flat toy teal, one texel a model pixel, 128 colours | **repainted** US Navy haze grey, 4x texture, panel seams, worn edges, bolt heads, salt and thin rust runs (`detail_texture.py` repaint rules) |
| U2 | BL-132 | near-black blue, flat | **repainted** naval blue-grey, weathered as the Mk42 |
| U3 | Speedboat | a pleasure boat's white, teal stripes, red seats | **repainted** as a patrol boat: haze grey topsides, dark boot stripe, black seats, dark non-skid deck, red-brown antifouling; salt and grime |
| U4 | Mi-28 | SBW's toy teal | **repainted** the Mi-28N's dark grey-green, weathered |
| U5 | YX-100 | clean white and blue plastic | **weathered** in its own sci-fi livery (kept, the user's decision of 2026-11-17): seams, worn edges, grime, mud on the running gear; cyan strips untouched. A camouflage repaint is one rule away if wanted |
| U6 | PLZ-05, Type 63, TOW, AH-6 | one texel a pixel (AH-6 flat) | **detail pass**: 4x (AH-6 2x) texture, seams, wear, grime, mud |
| U7 | Sentry | 43 boxes from one 16-px swatch a material | **remodelled**: telescoping tripod legs with clamps and hinge lugs, a battery box with stencils and a whip antenna, cooling rings and motor on the pan drum, bearing caps, an M240-class gun (cover, sights, carry handle, gas block, slotted heat shield, folded bipod, slotted flash hider), the can's lid and latch, a belt of rounds with bullets and links, the camera's sunshade; every face its own texels (`refit_lib.bake_unique`) and the detail pass. 125 boxes |
| U8 | Bush plane | 81 boxes, flat yellow swatches | **remodelled**: rounded fuselage, the nose bowl's two inlets, exhaust stack and air box, seats and stick, jury struts, tip bows, fuel caps, pitot, VHF whip, dorsal fillet, flying wires, bungee housings; fabric rib tapes, registration N618, the stripe run onto the cowl; weathered (no bolt heads on fabric). 127 boxes |
| U9 | 105 mm howitzer | flat olive swatches | **retextured**: every face its own texels, the "105" stencil, the detail pass (seams, bolts, wear, mud on wheels and spades) |
| U10 | BMP-2, PLZ-05, YX-100 sound | the three ran on the BMP-2's engine | **each its own real recording** (CC0): the BMP-2 an armoured personnel carrier's diesel hum (qubodup 182793); the PLZ-05 a heavy tank's diesel on a rough ride (monosfera 572294), its stop its own idle running down; the YX-100 an M1A1 Abrams' gas turbine (adr1911 542582): spool-up start, turbine idle, working whine, the spool played backwards as it stops. **Listen**: the voices check could not be made by ear |
| U11 | Hitboxes of the reworked models | regenerating put back the generators' coarse boxes | re-tightened by `audit_hitboxes.py --write` (sentry, bush plane, howitzer): no model surface uncovered on the sentry and bush plane, 0.8% on the howitzer |
| U12 | 105 mm howitzer report | "sounds muffled, is this the real sound?": a real field gun, but a dull take (0.6% of its energy above 2 kHz) buried further under a bass lift, a sub thump and hard clipping (89% under 200 Hz, crest 6 dB) | **replaced**: three real shots from US Army footage (qubodup 187767, a field gun's two shots; 175430, a howitzer firing an Excalibur round, into the battery's tail), kept as recorded with the low end lifted 3 dB and a gentle limiter: about 7% above 2 kHz, crest 12-13 dB; the crew's takes a little more low end. **Listen** |
| U13 | Sentry test | failed "it must fire" with all five zombies dead | the test counted ammunition, which automatic fire no longer uses; it counts the shots heard now |
| U14 | FSV-6 texture | left out of the first pass ("was the FSV texture detailing also considered?"): its tiles were patterned, so it looked less flat than it was, but every face of a material showed the same swatch | **retextured**: every face its own texels (3 a model pixel, 2048 x 1392), each laid with its material's tile (diamond plate, hazard stripes, gauges, the FSV-6 plate kept), then the detail pass (seams, worn edges, bolt heads, grime, dust on the roof, mud on the wheels and lower body); the glow layer from the same faces. Model unchanged |
| U15 | A vehicle towed by the FSV-6 jerks and lags | the server moves it once a tick and the client eased between its packets, while the truck the player drives moves smoothly: the gun trailed ~1 block too far back, the bar's length swung by a block and jumped up to 0.35 blocks a frame | **fixed**: the towed vehicle (and the tow bar's end) is drawn each frame where the tow puts it from the truck's smooth position, by the same geometry the server uses (`SupportVehicleEntity.towPose`). `TowSmooth`: over 540 samples, driving straight and turning both ways, the bar holds its 0.3 blocks to the thousandth (spread 0.000) |

Already good and left as they are: BMP-2, LAV-150 and HPJ-11 (detailed in the first review), A-10, BD-40, mortar. Seen in the pack with shaders: `FleetLook` (each reworked vehicle from the front and rear quarters, the sentry
at night).

## The user's third review (2026-11-19)

| # | Issue | Vehicles | State |
| --- | --- | --- | --- |
| T1 | Turning circle too wide; first-person view jitters on the move | BMP-2, LAV-150 (PLZ-05, YX-100 checked with them) | **fixed**: steering raised (BMP-2 0.10 -> 0.25, LAV-150 0.14 -> 0.29, PLZ-05 0.07 -> 0.17, YX-100 0.08 -> 0.19): radius from a crawl 7.5 / 7.4 / 8.2 / 8.1 blocks (was about 29 / 19 / 50 / 50). Jitter: the crew camera's rumble wave was stepped faster than a tick can show at speed (capped, `VehicleFeel`), and a server-laid turret, with the camera on its barrel, was drawn from packets arriving mid-tick (now drawn between tick snapshots). `ArmourTurning`, `Lav150Drive` pin both |
| T2 | BMP-2: weapons 1 and 4 the same | BMP-2 | **fixed**: the 2A42's two feeds are told apart: `30mm 2A42 AP` (a penetrator, no burst: sparks and spall) and `30mm 2A42 HE-I` (the burst), each its own icon, the HE moved next to the coax (slots: AP, coax, HE, ATGM) |
| T3 | A weird sound on boarding; an engine loop with a voice in it | YX-100, PLZ-05 | **fixed, listen**: the PLZ-05's and YX-100's drive loops were SBW takes (the PLZ-05's carried speech, the YX-100's is a sci-fi hover drive): both now run on the BMP-2's clean diesel, slower for their weight; the PLZ-05's idle is the clean take the others use |
| T4 | GS rounds don't do anything | YX-100, Mk42, BL-132 | **fixed**: the canister was 36 balls scattered at random in 5 degrees, which bunched on the bore; now 100 balls spread evenly over 7 degrees (the same weight of shot), a muzzle cone of dust and smoke, dust where the balls land. `Yx100Grapeshot`: 6 of a squad of 8 zombies at 16 blocks and the BMP-2 behind them destroyed; `Mk42`: 4.9 balls into one target at 30 blocks |
| T5 | Passengers sit outside the tank | YX-100 | **fixed**: seats 3 and 4 are the loader (turret) and hull crewman, inside and hidden, chase or hull camera (no first person from inside the hull) |
| T6 | Howitzer has no firing sound; it should be heavy and gritty | howitzer | **fixed, listen**: its crew heard only a muffled "interior" take meant for closed turrets; an open gun's crew now hears the full blast, rebuilt harder (body +, sub thump, its own crackle driven hard, soft-clipped) |
| T7 | Smoke is puffs; gun smoke should be real and animated; every vehicle needs damage and destruction visuals | all; howitzer, Mk42, BL-132 | **fixed**: one smoke system (`SmokeFx` -> the blasts' lit puff renderer) for damage (grey, dark, then fire under a black column), burning wrecks, jets' trails and gun gas, the BD-40's and FSV-6's exhausts, flare trails, wreck break-up; guns' muzzle blasts (forward jet, muzzle-brake lobes, smoke curling off the barrel for 3.5 s; rockets' backblast) drawn from the report each client gets. No vanilla smoke sprites left on the vehicles. `SmokeFx` |
| T8 | Tail and front (red, green) lights still bulbs; bush plane lighting | Mi-28, AH-6, A-10, bush plane | **fixed**: under a shader pack the glow is a premultiplied soft halo (the old one was white to its rim, which the pack takes as emitted light: a hard disc), dimmer; searchlight glare smaller and no drawn shaft under shaders (it bloomed into a white stick); the bush plane's wing landing light has no glare or shaft of its own. Seen in `AircraftNightLights`, `AircraftCockpitViews` |
| T9 | Flares need better light and look | AH-6, Mi-28, A-10 | **fixed**: each burning flare is a real light source (LambDynamicLights, flickering, by day and night), its glow the soft halo under shaders, its trail real white smoke with flame at its heart |
| T10 | Helicopter first person: reticle should go; the AH-6's first-person UI regressed | helicopters | **fixed / check**: no reticle over the view in first person (the helmet display only; zoomed, the sight as before); the standard status card shows in first person again (bottom middle, below the heading tape) and the helmet display keeps only its tape, speed and height boxes and the lock prompt (its status block, weapon, reload and flares lines were duplicates). Whether this is the regression meant: ask |
| T11 | A-10 top speed reduced? | A-10 | **checked**: its data is unchanged (2.778 b/t, 200 km/h); `A10Flight` measures it |
| T12 | HUD numbers make cards grow and shrink; m/s misaligned on some cards | all | **fixed**: cards keep the widest width they have needed (`HudPanel`), numbers have a digit to spare, numeric values sit against the right edge as the speed does; the jet card's speed and altitude in the same style |
| T13 | Vehicle names in the HUD | all | **fixed**: the status card is headed by the vehicle's name |
| T14 | Sentry ignores a zombie riding a chicken; speedboat gun auto mode; heavier sentry report; a sound on switching automatic | sentry, HPJ-11, speedboat | **fixed**: a monster's mount is part of the threat; the speedboat's bow gun has the automatic switch; the sentry's report laid over the M2's with a thump and the bolt's clack; a relay, servo and chirp when switched. `AutoDefenceTargets` |
| T15 | Mortar: where will it land | mortar, Mk42, BL-132 | **fixed**: a ring on the ground at the predicted impact and a beam of light standing on it, seen from the gun at any range (amber, dashed, when beyond loaded ground) |
| T16 | Hitboxes as tight as possible | fleet | **done**: `design/hitbox/audit_hitboxes.py` rebuilt the loose boxes into boxes that hug the model (report: `design/hitbox/AUDIT.md`); FSV-6, BD-40, mortar (tube sweep) and the helicopters kept as they were |
| T17 | Every vehicle in the vehicle creator, with a recipe | fleet | **done**: all 17 assembled vehicles have a `vehicle_assembly` recipe (the mortar and the TOW are crafted items); the creator now finds each vehicle's icon by its file, and the four that showed none (BD-40, Mk42, BL-132, Type 63) have one (SBW's for the naval guns, drawn from the model for the Type 63: `refit_lib.silhouette_icon`) |
| T18 | Can the turrets be picked up and moved | fleet | **yes, pinned**: sneak + right-click with the crowbar packs any vehicle nobody is in (the sentry, HPJ-11 and the guns included) into its container, which is placed and opened with the crowbar again, damage and load kept; the mortar and TOW pack into their own items with an empty hand. `VehiclePackUp` packs and redeploys all 19 |
| T19 | AH-6 instrument panel like the Mi-28's | AH-6 | **done, look**: `Ah6CockpitLayer` on the model's dash: colour attitude display (sky and ground, ladder, bank pointer, speed, radar height, heading), engine page (NR, NP, TQ, TOT, fuel bars and figures), the centre stack's navigation display (compass rose turning with the heading, drift line, ground speed), standby airspeed and altimeter with live needles, caution lamps, master caution and warning; the weapons page on the console (rounds, rockets, the selected weapon boxed, flares ready). In a helicopter's first person the status card now takes the bottom left corner (the crew list the top left) instead of covering the panel. Screenshots in `AircraftCockpitViews` |

## The user's fleet review (2026-11-18)

The user played the whole fleet in ArmourTest and listed what is wrong. The items that recur on several vehicles are
now rules in the guide (player's checklist, *fleet review* rows) and every vehicle is checked against them. State:
**open**, **fixed** (played or tested, how in the vehicle's status), **limit** (written down, why).

### Recurring (every vehicle checked)

| # | Issue | Vehicles named | State |
| --- | --- | --- | --- |
| R1 | Standing still sounds like moving (track or tyre layer at rest) | BMP-2, LAV-150 | **fixed**: real idles from each start take (`prepare_idles.py`), a muffled `engine_cab_idle` in the cab at rest; played (BMP-2 at rest: only the cab idle, 0.42; driving: the cab load, tracks) |
| R2 | Turret or mount slews at mouse speed: no rate, no spin-up or braking | BMP-2, LAV-150, HPJ-11, sentry, speedboat gun; mortar spins on its own | **fixed**: rates on all five (1.5 / 1.5 / 4 / 4.5 / 3 deg a tick), every powered mount spins up and brakes (`slewSpeed`); the mortar no longer takes its heading from the view (`yawFollowsRider`); played (BMP-2 lags and settles; mortar steady after a mouse sweep, A turns it 0.5 deg a tick) |
| R3 | Feels too fast | BMP-2, LAV-150 | **fixed**: BMP-2 about 52 km/h (was 65), LAV-150 about 72 (was 88), slower to pull away; `Lav150SpeedSwim` pins it |
| R4 | Muzzle flash too big | BMP-2, LAV-150 | **fixed**: one short shrinking flare for the 20/30 mm, the smallest flash for a coax |
| R5 | Passenger's first-person view is outside the hull | BMP-2, LAV-150 | **fixed**: `"no_first_person_seats"` on their troop seats; played (seat 2 drops to the chase view) |
| R6 | Last F5 view should be a mounted "GoPro" camera on the hull, as on the helicopters and A-10 | BMP-2, LAV-150 | **fixed** for every land vehicle and boat (`hasHullCamera`: the rear corner of the hull's left side, looking forward); played on the BMP-2 |
| R7 | Model needs more texture and detail | BMP-2, LAV-150, HPJ-11 | **fixed** (2026-11-19): `design/ground-vehicles/detail_texture.py` enlarges each texture (BMP-2 1024, LAV-150 2048, HPJ-11 512) and weathers it from the model: panel seams, worn edges, bolt heads, mottled paint, grime streaks, dust on top, mud low down (salt grime, no mud, on the HPJ-11). Seen in the pack by day. Look |
| R8 | Movement sounds light: traverse should be a heavy mechanical whine | HPJ-11, BL-132, Mk42, Type-63 | **fixed**: the HPJ-11, Mk42 and BL-132 mounts now play the heavy electric drive (not a handwheel or thin servo), pitched down with mass; handwheels louder (Type-63). Listen |
| R9 | Reloads: too slow (TOW, PLZ-05, Type-63); reload should sound heavy (shell dropped, next slid in) | TOW, PLZ-05, Type-63, BL-132, Mk42 | **fixed**: TOW 3.75 s (was 7.5), PLZ-05 3 s (6), Type-63 8 s (30); Mk42 and BL-132 reload = a 5-inch case clanging on the deck and the next round rammed (`design/mk42/prepare_reload_audio.py`, CC0). Listen |
| R10 | Lamps under shaders show as white bulbs, not light; glows see-through | Mi-28 tail, AH-6, A-10 tail and exhaust glow, bush plane | **fixed**: under shaders the glows are soft emissive halos, and every steady lamp lights what is round it through LambDynamicLights; seen at night under Photon (2026-11-19, `AircraftNightLights` -PwithPack: the Mi-28, AH-6 and A-10 from round about). The A-10's exhaust glow now fades out as the view turns off the nozzle's axis (the pack's bloom shone it through the nacelle side on); `A10EngineCloseup` shots from behind, the rear quarter, the side and the intakes |
| R11 | Headlight beams flicker while driving and look hand-drawn | BD-40, FSV-6 | **fixed**: no drawn shaft on ground vehicles (only aircraft searchlights), the lit spot smoothed over ticks |
| R12 | Reversing lamps too small next to their lit glow | all road vehicles | **fixed**: road lamps keep their glow size and only brighten |
| R13 | Tracers and muzzle flash not from the muzzle when zoomed | speedboat, Mi-28 gunner | **fixed**. Speedboat: its gun is server-laid (R2). Mi-28: the zoomed sight sat on the muzzle itself, so rounds and flash came out of the crosshair; it now sits 0.26 above the gun and 1.6 behind its muzzle, boresighted, and the rounds rise into the crosshair from the gun below; in flight the tracer starts on the gun as drawn (the server's muzzle was a few ticks ahead). Played (zoomed, firing at the ground); `Mi28GunnerAim` pins the sight |
| R14 | Occupant clips into the vehicle (head into a wall, feet into the floor); pilot leans out in a hard sideslip | FSV-6, AH-6 | **fixed**: FSV-6 slide-out end walls and floor plate moved clear of the bunk's cells (the bed flickered against them); aircraft riders drawn banked and pitched with the airframe. The AH-6 pilot in a bank not yet seen close |
| R15 | First-person jitter; cab controls twitch | FSV-6, BD-40 gear stick | **fixed**: crew cameras interpolate from the same old position the renderer draws the hull from; the BD-40's levers follow whole-tick motion, eased |
| R16 | Explosions: the classic JEG burst should go; use the realistic blast and smoke of the A-10 and helicopters | all | **fixed**: explosive bullets, grenades, autocannon HE and vehicles blowing up now use the missiles' realistic blast (`RealisticBlast`) |
| R17 | Smoke: real smoke, not puffs; not so thick it hides the target | LAV-150, Type-63 | **fixed**: Type-63 rockets puff at the tube and blow their exhaust behind the launcher; smoke screens (the armour's dischargers, smoke grenades, the howitzer's smoke shell) are now one billowing cloud of soft lit smoke per canister that rolls out low, climbs, drifts downwind, holds and thins away, thinner at its edges (`SmokeScreenPayload`, drawn with the blasts' smoke), no vanilla campfire smoke. Played (the LAV-150's salvo) |
| R18 | HUD: minimal, only what is needed, nothing twice; fixed-width readouts (m/s card grows with the digits); no flashing; the yellow bore marker unexplained | all | **fixed**: one status card (no name, no weapon line: the weapon list has them), speed a right-aligned row in it, crew list only with others aboard or just after boarding, gun panel without its repeated title and shell, the bore marker a dotted ring labelled GUN; the sentry's lamp steady and hidden from its operator |
| R19 | Right-clicking a vehicle sometimes doesn't board | all | **fixed**: with a gun in hand every right-click on an entity was refused (to aim); vehicles are exempt. Played (boarded the BMP-2 holding a rifle) |
| R20 | Sounds want more weight and grit: guns, horns, start-ups | sentry, howitzer, LAV-150 start, BD-40 horn | **fixed, listen**: sentry report with its body raised and saturated, louder; howitzer a deep blast with a sub thump and a 3 s rolling tail; LAV-150 a truck diesel started by the hood; BD-40 a real truck air horn |

### One vehicle

| Vehicle | Issue | State |
| --- | --- | --- |
| YX-100 | GS rounds blow up the tank (pellets struck its own barrel) | **fixed**: pellets keep the carrier's arming time; played (fired GS, hull 600/600) |
| YX-100 | A sound repeats after boarding | **probably fixed, listen**: nothing repeats in a logged 20 s; at rest the cab played the SBW sci-fi drive loop, now a real diesel idle |
| Mortar | Mount spins on its own after a mouse move (view and mount fed each other) | **fixed**, played |
| TOW | Third-person pose doesn't hold the launcher | **fixed**: `"grip_seats"`, both hands forward on the grips |
| HPJ-11, sentry | A visible auto-mode button; on auto, hostile mobs only, unlimited ammunition | **fixed**: an Automatic switch on the hold screen and a key line on the HUD; automatic fire draws no ammunition or power; the HPJ-11 also takes hostile mobs within 64 blocks (down to -15 deg, never through anything friendly). `SentryWall` pins it |
| Sentry | First-person screen flashes green and red | **fixed** (lamp steady, hidden from the operator) |
| Speedboat | Console like the helicopters'; gunner stands; hitting land glitches (should glide up) | gunner **stands** (`"standing_seats"`, played); **glides** up a beach from 0.1 b/t and slides on (played: climbed onto the sand and slid on); **console fixed**: a live helm panel (`SpeedboatCockpitLayer`): chart plotter with a heading-up compass ring, engine page (revs, fuel, trim, temperature bars), tachometer and knots dials with needles, compass, lamps; played at rest, on the plane and at night |
| BD-40 | A white ball between the headlamps (2026-11-19) | **fixed**: it was the beam's own lens glare, drawn mid-grille; a pair of headlamps draws none (as every other road vehicle). `BulldozerNightLights` pins it, with shots from ahead, above the right front, the side and at dusk |
| Speedboat | The gunner should stand holding the gun (2026-11-19) | **fixed**: he stood with his arms at his sides; the gunner's seat is now also a gripping station (`"grip_seats"`, as the TOW), both hands forward on the gun. Played (from behind and the quarter); `SpeedboatGunnerPose` pins the seat |
| Sentry, HPJ-11 | A zombie riding a chicken was never shot (2026-11-19) | **fixed**: the chicken counted as friendly in the line of fire; an animal the target rides is part of the threat now (never a player or a vehicle). `AutoDefenceTargets` |
| Sentry, HPJ-11, speedboat | A sound when automatic is switched (2026-11-19) | **fixed**: a relay clunk, the mount's servo and a two-tone chirp, rising for automatic and falling for manual (`vehicle.sentry.auto_on`/`auto_off`, `design/sentry/prepare_audio.py`). Heard in `AutoDefenceTargets`. Listen |
| Sentry | Heavier, harder report, like a Warthog's gun or Battlefield (2026-11-19) | **fixed, listen**: the Negev's crack laid over SBW's M2 report, a low thump under it, the bolt's clack, saturated harder |
| Speedboat | The bow gun wants an automatic mode (2026-11-19) | **fixed**: N or the hold screen's switch; with no player at the gun it engages hostile mobs within 48 blocks by itself, free of ammunition, never through anything friendly; a gunner always lays it by hand. `AutoDefenceTargets` (the driver alone aboard: a zombie on the bank killed) |
| All | The vehicle's name gone from the HUD (2026-11-19) | **fixed**: it heads the status card again, its dot the hull's state |
| BD-40 | Reversing beeper | **fixed**: a real construction truck's back-up beeper (CC0), audible from the seat. Listen |
| Mi-28 | Full throttle pitches the nose too far down | **fixed**: the drawn hull eases towards 11 deg nose down at full thrust (the thrust is unchanged); all helicopters |
| Bush plane | Propeller-plane sound: take-off, landing and cruise | **fixed, listen**: a real light-aircraft idle (taxi, landing approach) crossfading into the power loop as the throttle opens; light tyre roll instead of the A-10's. Played (idle 0.27 at low throttle, power 0.67 on the roll) |

**Review fixes tested** (all `-PwithPack`, 2026-11-18): `Mortar`, `Tow`, `Type63`, `Yx100`, `Speedboat`, `Hpj11`,
`SentryWall`, `GroundVehicleSound`, `SupportInterior`, `Lav150SpeedSwim`, `Mi28GunnerAim`, `BushPlane` pass. Two tests
were changed with the behaviour: `Hpj11` and `SentryWall` no longer count rounds spent on automatic (free now) but
check the hold stays full; `SupportInterior` measures the FSV-6's turning circle from a crawl (13.1 blocks), its
steering raised 0.18 -> 0.21 to make up for its mass. Played through mcpfabric: the mortar, BMP-2 (idle sound, turret,
troop seat, hull camera), YX-100 (GS fired, no self-damage), HPJ-11 switch and HUD, boarding with a rifle in hand, the
speedboat's standing gunner and beaching, the TOW gunner, the bush plane's idle and power layers, the AH-6 at night.
`ArmourTest` rebuilt with the whole fleet; the world as the user left it is backed up in the session scratchpad.

**The rest of the review (2026-11-19)**: R7 (texture pass), R10 (night under shaders, the A-10's exhaust), R13 (the
Mi-28 gunner's sight and tracers), R17 (the smoke screen) and the speedboat's console are done; see their rows. Tested
with `-PwithPack`: `Mi28GunnerAim` (its zoom check now pins the sight above the gun), `A10EngineCloseup`,
`AircraftNightLights`, `Speedboat`. Played through mcpfabric: the three retextured vehicles by day, the LAV-150's smoke
salvo, the speedboat's console (at rest, on the plane, at night), the Mi-28 gunner zoomed and firing. `ArmourTest`
rebuilt with the whole fleet for the user's test.
