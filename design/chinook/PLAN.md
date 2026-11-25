# CH-47F Chinook: plan

Status (2026-11-25): P1–P7 built and played, through an eighth pass (below); P8 sound and lights
in part. Contract: [BRIEF.md](BRIEF.md). Method:
[vehicle-development-guide.md](../../docs/vehicle-development-guide.md). Peers: the AH-6 and Mi-28 (flight), the FSV-6
(walk-in room, working blocks, tow), the BD-40 (process).

**What the user asked for (2026-11-20):**
1. Walk about inside the Chinook normally, and fire your own gun out of the back.
2. A rear ramp that opens and closes; you can stand on it and board over it.
3. Lower four ropes, hook them to a vehicle and carry it. The load swings naturally as the Chinook flies.
4. Follow the vehicle guidelines, and make it as good-looking and cool as possible.
5. Place blocks inside as you would anywhere else, even in flight.

Points 1 and 5 are what the guide currently says can't be done ("Walking about inside while it moves is not
possible with a block shell", section 6). Most of this plan is about the shared system that makes them possible.

## Decisions taken (change them before phase 3 if they're wrong)

| # | Question | Taken | Why | Alternative |
| --- | --- | --- | --- | --- |
| D1 | How a moving room works | **Our own cabin frame (`CabinSpace`)**: people aboard move in the cabin's own coordinates, are networked relative to it, and collide with the cabin's boxes and placed blocks | No hard dependency. No pilot lag. Testable with `-PwithPack`. The FSV-6 (and any future vehicle) can reuse it to let people walk about while it drives. Both trials passed (below) | A Sable sub-level. The pack ships Sable 2.0.3 inside the Create Aeronautics port, on server and clients, and every block works natively there. But Sable runs physics on the server only: a Sable-driven aircraft lags the pilot by ping + ~100 ms, and a sub-level made to follow a JEG aircraft drew 2.5–6 blocks off it |
| D2 | The view of a person standing aboard | Turns with the Chinook's yaw (as a rider's does); pitch and roll stay level with the horizon, and the cabin is seen tilting around them | Aiming stays exact, so shots out of the ramp go where the crosshair is. A tilting camera in a banking helicopter is nauseating | Tilt the camera with the cabin |
| D3 | The four ropes (user, 2026-11-20) | **One centre hook with a four-leg sling**: a pendant from the hook to an apex ring, then four legs to the load's four lift points | This is how a real CH-47 lifts a vehicle, and the player sees four ropes go down | — |
| D4 | Hooking up (user, 2026-11-20) | **The pilot alone**, from a steady hover with the sling key: the legs reach down and latch onto the load's lift points. No ground crew needed | One player does the whole job | — |
| D5 | What it can lift (user, 2026-11-20) | **Any vehicle, no weight limit.** The load's `"mass"` still weighs on the Chinook: a heavy load slows the climb and acceleration and swings with more momentum, but nothing is refused | The user's choice | — |
| D6 | Blocks inside | Any block can be placed and broken in the cabin grid; it is drawn and collides. The FSV-6's working set works (chests, barrels, shulker boxes, ender chest, crafting table, furnace family, anvil, brewing stand, stonecutter, smithing, enchanting, lamps). Blocks with a ticking machine or redstone are refused with a message | Nothing is silently broken, nothing duplicates, and it reuses the FSV-6's "working blocks as data" | A virtual level that runs any block entity (Create's contraption approach): much larger |
| D7 | Armament (user, 2026-11-20) | **Two M240H side door guns only**: the right one in the crew door, the left one in the forward escape-hatch window. No ramp gun. The crew door **slides open and shut** (aft along the fuselage); its gun fires only with it open. Flares. No rockets | The user's choice | — |
| D9 | The cabin's furniture (user, 2026-11-21) | **Empty but for the two door guns and their jump seats**: no troop benches, no troop seats (seats 0–3 only) | The players fill it with their own blocks | — |
| D10 | Hovering with a load (user, 2026-11-21) | **Hands off, a loaded Chinook hovers** as an empty one does: the hover hold carries the sling's pull (`VehicleEntity.weightyHelicopterVelocity`, `heliExternalPull`) | The user's rule | A real collective (the load sank it 2 m/s) |
| D11 | Door-gun ammunition (user, 2026-11-21) | **Each M240 has its own 9-slot can**, opened by its gunner's hold key (`ammoContainer`, `openHoldFor`); the hold feeds neither | The user's rule | The shared hold (one gun's reload emptied it) |
| D8 | Name and size | "CH-47F Chinook", id `ch47`, at real 1:1 scale (about 30 blocks long with the rotors turning) | The guide asks for real proportions | — |

## Built so far, and what differs from the plan

| Phase | Built | Differs from the plan / still to do |
| --- | --- | --- |
| P4 ramp | Synced target and angle (closed 62°, level 0°, down −30°) at 1° a tick, eased on clients; the pilot's X and the panel on the aft right wall cycle it; boarding up the lowered ramp (`cabinAboard` follows its surface); its hitbox drops out below 45°; the upper ramp door slides up into the pylon. The crew door slides (synced), clicked from inside or outside; its hitbox drops out while open | The down angle is fixed, not solved against the terrain. No outside switch by the hinge yet, no jump lights, placeholder sounds (piston, iron door) |
| P5 guns | Two M240H on pintles (model bones `gun_<l\|r>_mount`/`gun_<l\|r>`), seats 2 and 3 aim them through the shared seat-gun path (`seatGunMuzzle`, as the PLZ-05's roof gun); right gun only with the crew door open (the door opens as the gunner sits), left gun through its window, which slides open while the gunner sits. Rounds and grenades fired from the cabin leave with the Chinook's motion (`CabinNetwork.carriedVelocity`) | No slew-rate limit on the pintles yet; the door guns' own rounds don't inherit the Chinook's motion (as the AH-6's don't) |
| P6 blocks | `CabinBlocks`: storage (27 slots), ender chest, furnace family (cooking, glowing, saved: the FSV-6's furnace moved into the shared `VehicleFurnace`), crafting table and the FSV-6's workstation menus; multi-cell blocks, redstone sources and other block entities refused with a message; benches fold when their cell is filled, and a seat whose cell holds a block can't be taken (`VehicleEntity.seatBlocked`) | The brewing stand is refused for now (the FSV-6's brewer isn't shared yet). Cabin lighting still P8 |
| P7 sling | `ChinookSling`: N lowers the sling; over a vehicle's roof N hooks it; N with nothing in reach raises it; carrying, **N twice within 2 s releases** (the tow key sends single presses, so this replaces "hold 1 s"). Real-g pendulum with 4 substeps, inelastic rope, drag, light damping; the pull acts back on the Chinook on server and the pilot's client alike (`VehicleEntity.externalAcceleration`), capped under the rotor's spare lift so nothing is too heavy; a load also makes it fly heavier (`flightWeightScale`). The load's own movement is off while slung (`isSlung`); clients draw it from the Chinook's smooth pose; ropes drawn by `Ch47SlingLayer` | No hook view, no landing damage for a load set down hard, the load isn't released if the pilot leaves. Numbers (drag, damping, pull) untuned until played |

### Play-through of P4–P7 (2026-11-20, pack client over mcpfabric, creative)

| Area | Worked | Found and fixed | Still open |
| --- | --- | --- | --- |
| Ramp | X cycles it a step at a time (pilot); the panel works; walking down it from inside to the ground | Drawn upside down: "closed" hung under the belly, "down" pointed up (GeckoLib's X turn is the negative of generate.py's; `Ch47Renderer`). One panel click moved it two steps (`MinecraftCabinUseMixin` skipped the right-click delay). Walking up it from the ground sank the player into the ground under the belly (the aboard zone ran past the lip; `cabinAboard`, `CabinClient.standingHeight`). Ramp and door positions weren't saved (now `RampTarget`, `RampAngle`, `DoorTarget`, `DoorOpen`) | Saving the ramp/door not yet seen across a reload in game. With the ramp level, people on the ground walk through it (its hitbox is off below 45°) |
| Crew door | Clicked open from outside, slides aft; walked in through it; opened from inside | A click through the open door from inside sat the player in the pilot's seat instead of shutting it (`cabinControlOnRay`) | The right gun at rest lies across the doorway |
| Door guns | Both aim with the view; the left window slides open for its gunner; both fire (left through the window, right through the door); a rifle fires out of the open ramp | | The two guns share the hold's ammo: one gun's reload took all 128 rounds into its magazine, leaving the other empty. The muzzle flash fills the gunner's view |
| Blocks | Furnace cooks (raw iron to ingots) and glows while lit; crafting table opens; a bed is refused with "needs more than one cell"; blocks and contents survive a relaunch; the seat under a block is skipped by change-seat | Placing a furnace opened it at once (the same right-click-delay bug) | A lit furnace is drawn almost white under shaders (cabin lighting, P8). The fold direction wasn't checked by eye; the block hides the bench |
| Sling | N lowers, N hooks ("Hooked: LAV-150"), lifts and carries it; the rope legs are drawn; the load trails slightly in forward flight; N twice releases ("Press N again..." then "Load released"); N raises the empty hook; the LAV landed undamaged | | **With a load and no input the Chinook sinks about 2 m/s until the load touches down** (empty, it holds its hover); the pilot has to pulse the collective. The N key is also mcpfabric's approve key in dev ("Nothing is waiting for approval"). A Chinook descending onto the LAV ended overlapping it, then couldn't climb |

`Ch47RampDoorClientGameTest` guards the panel's one-step click, walking up the ramp from the ground, and the door from inside.

### Second pass (2026-11-21, after the user's answers)

| Item | Done | Seen in game |
| --- | --- | --- |
| Hover with a load (D10) | The hover hold carries the sling's pull | LAV-150 slung, hands off: held within 0.1 m after settling (it had sunk 2 m/s; a first fix climbed 0.17 m/s) |
| Separate ammunition (D11) | A 9-slot can per M240 | Right gunner's hold key opens "Right M240: ammunition"; 64 rounds there fed only the right gun; the left showed 0/0 and wouldn't fire; magazine rounds kept over a reload |
| Empty cabin (D9) | Benches and troop seats removed (generate.py, ch47.json) | Empty cabin with the two guns and jump seats |
| Gun across the doorway | Unmanned guns stow pointing up and aft beside their posts | Clear of the crew door |
| Level ramp walked through | A deck box while level | Walking in from the ground stops at its aft edge |
| Landing on a vehicle | `restOnVehiclesBelow` | The Chinook came to rest on the LAV-150's roof and lifted off again (the LAV took 96 damage from a full-speed descent; the Chinook none) |
| Door-gun flash | The small flash for seat guns | Small, clear of the sights |
| X missed after taking the seat | Presses counted per packet (`takeDriverSwitchPresses`) | One press straight after mounting moved the ramp one step |
| Ramp/door saving | Saved and restored | Level ramp and open door both back after an autosave and relaunch |
| Lit furnace "too white" | Nothing to fix | A lit furnace in the world is drawn the same under the pack's shaders |
| Left gun window | Nothing to fix | Shut, glass |

`Ch47LoadedHoverClientGameTest` guards the loaded hover, the separate cans and X's one step per press.

### Curved skin (2026-11-21, the user: "hyperrealistic, with curves, not blocky")

The user chose the real curved mesh over refined boxes. The outer skin is now a smooth-shaded quad mesh
(`design/chinook/mesh.py`, drawn by `VehicleMesh` on the GeckoLib bones); the interior, instruments, guns and lights stay
boxes. See the vehicle guide, section 5, "Curved skins".

| Item | Done | Seen in game |
| --- | --- | --- |
| Skin | Fuselage, nose, pylons, sponsons, nacelles, blades, rotor heads, gear, ramp and door panels lofted from CH-47F dimensions, with a 2048 painted atlas (panel lines, rivets, streaks, soot, stencils) | Pack client with shaders, from 3 to 47 blocks, ramp down, loaded hover |
| Windscreen | Clipped into the geometry along its outline (rounded 6 cm corners), with the frame's depth between skin and lining; the panes in the glass pass, the frame opaque | Pilot's view: straight frames, round corners; the stepped edges and the grain on the posts are gone |
| Cost | 24,400 quads (16,900 before the clip); the cockpit and cabin linings (4,700) are drawn only within 32 blocks | Seen at 15 and 47 blocks; the 32-block switch itself not watched |

Left: the pack's shader draws a dithered shadow edge on the cockpit roof at noon (box models show the same).

### Third pass (2026-11-21, the user's review)

| Item | Done | Seen in game |
| --- | --- | --- |
| Hover when the pilot gets up | People standing in the cabin count as crew aboard for the autopilot (`hasCrewOffSeats`, synced), and the engines stay on when the pilot gets up in flight | Played: got up into the cabin at 11 m, held exactly for 16 s; abandoned, it came down. `Ch47CrewHoverClientGameTest` (server and client agree) |
| Glitch when swaying | The pilot's eye was placed without the hull's roll and drifted out of the cockpit; now it stays in the seat (all helicopters) | Played: posts stay in place, horizon level. `HelicopterWeapons`, `Mi28GunnerAim` pass |
| Door guns sound like a minigun | M240s heard shot by shot as a 7.62 mm machine gun (`ownGunReport`) | `Ch47Sound`: 29 reports in 1.5 s, no loop |
| Cabin 3 wide, 1 higher | Grown on output (`generate.grow`): cabin 3 x 3 m inside, nose stretched to a taller windscreen, rotors 1 m higher; seats, guns, hitboxes, cabin layout (3 x 3 cells) moved with it | Played: from three sides, inside and from the pilot's seat. `Ch47RampDoor` (panel re-aimed at the wider wall), `Ch47Sound`, `Ch47CrewHover` pass |
| Door sounds and animations | Eased ramp, upper ramp door and gun window; the crew door split (upper half slides, lower half folds into steps); real recordings: ramp hydraulics loop and lock, door slide open and shut, step, window | Played: the upper half slides, the step folds out over the sill (its fold sense was backwards at first: laid across the cabin floor). Sounds checked by `Ch47RampDoor` passing; not listened to |
| Detailed cockpit console | `Ch47CockpitLayer`: five live displays (both PFDs, map, engines, cargo), master caution and caution lamps, console CDUs, radios and hook window, overhead switch panels (`make_cockpit.py`) | Played: displays live under shaders, console and overhead legible |
| Cabin ceiling fittings hanging down | Cable runs, dome lights and jump lights placed against the grown ceiling (the growing had stretched them a metre down) | Played: flush with the roof |
| First person shows the console and navigation | The pilot's view looks 12 degrees down, the eye lowered to 2.3 m (2.5 m since 2026-11-25) and 0.7 m from the panel; the panel raised to the sill; the helmet display's boxes over the windscreen, the status card top right | Played: windscreen above, the engine page, map, flight display, caution lamps and console below, nothing over them |
| Detailed interior textures | `interior_paint.py` paints the box interior per material in model coordinates (patterns carry across pieces), 6-8 texels a pixel: quilted blankets, floor plates with tie-down rings, roller treadways, riveted frames with lightening holes, cable bundles, webbing, the hook grate; the mesh's cabin walls quilted and the cockpit lining panelled and riveted | Played: cabin forward, aft, wall, floor, cockpit |
| More texture detail | 4096 atlas at 7 texels a pixel; per-panel tone, inspection hatches with screws, rivet rows, seam shading, chipping (heaviest by the door and ramp), oil under the engines, mud low down, a subdued flag, fuel fillers | Played: seen close from outside; texture crops in review |

### Fourth pass (2026-11-21, the user's review)

| Item | Done | Seen in game |
| --- | --- | --- |
| Dark slab over the ramp opening | The aft pylon's underside was authored on the raise's line (47), so the grow left it a metre under the 3 m ceiling; now at the ceiling (`mesh.py` `AP_YB`) | Played, pack + Photon: looking aft from the ramp, the opening is clear to the ceiling |
| Quilting on the outside of the left side | `cabin_wall` built the left lining and ribs at the outer skin (`x_in` was `-OUT_HALF`) | Played: left side plain paint at noon, close and from 10 m |
| Bright green bars outside | Formation strips a dull panel by day, lit only at night (`Strip.nightOnly`); position, beacon, tail, landing and dome lamps dark lenses lit by `AircraftLights`; navigation lenses 12 cm, not 19 x 25 cm | Played: no neon by day on either side |
| Jump lights flash and beep as the ramp moves | Red jump light flashes 0.4 s on / 0.4 s off while the ramp travels, with a 2.4 kHz beep at each flash (`ramp_beep`, synthesised); green steady while it stands open; both dark shut; on the battery | `Ch47RampDoor`: 10 switches, 5 beeps, none after; played: red flashing and washing the aft cabin, then green |
| Jumping in at the crew door | The door's aboard zone runs to the top of a jump and over the folded-out step, which is a shell box at the sill | `Ch47RampDoor`: forward + jump from 3.2 m out lands on the cabin floor |
| Lighting inside and out | `AircraftLights.CH47`: red/green position lights, white tail, red beacons (aft pylon, belly), night formation strips, landing-light beam, cockpit glow; inside, three dome lights at night and the jump lights, both on the battery (`Kind.CABIN`, `JUMP_RED`, `JUMP_GREEN`) | Played at midnight: cabin walls lit warm, beacon flashing and tail light seen from behind; the side lights and formation strips not seen lit (the pilot's free look doesn't orbit the camera, and getting out on the ground cuts the engines). The cabin floor stays dim under Photon |

### Fifth pass (2026-11-21, the user's review)

| Item | Done | Seen in game |
| --- | --- | --- |
| The rope has weight | The drawn pendant and legs are ropes with weight (`SlingRope`: Verlet nodes under real gravity, air drag, pull-only links), pinned at the hook and at the end the server swings; the empty sling's legs hang free with their hooks | `Ch47SlingRope`, from a camera beside it: moving off, the pendant bows 0.5 m and the legs trail 1.4 m; stopping, they swing 0.7 m ahead. Screenshots: hanging straight, bowed, trailing in a curve |
| Flies like the Mi-28, slower | Handling rates set near the Mi-28's (yaw 0.58, pitch 0.66, roll 0.54, lift 0.6, power 0.62/0.95; they were 0.42-0.55), top speed 1.24 (it matched the Mi-28's 1.55) | `Ch47FlightFeel`: 24 m/s against the Mi-28's 30 (80%), both 143 ticks to 90% of their top speed |
| Strafing slid the wrong way first | The hull rolled about the ground, so the shared strafe sway (a kick against the strafe before the bank) carried the cockpit and rotors the wrong way; now it turns about the cabin's middle at 2.2 m (`rotateOffsetHeight`, as the Mi-28's `render_pivot_y`), with half the kick (`strafeSwayScale` 0.5) | `Ch47FlightFeel`, strafing left from a hover: the view's wrong-way slide 0.08 m before, 0 now (Mi-28 0.05); counter-bank 0.9 deg (Mi-28 2.9); 1.2 m left after a second (Mi-28 1.7). All six Ch47 tests pass with the new pivot |
| Spool-down heard as the pilot gets up in flight | The pilot's client wiped its own copy of the engine state when they pressed dismount and again on the dismount sync, so the shutdown played (8 ticks of "cold" engines until the server's resend); a helicopter that holds its hover now only takes the pilot's hands off (`VehicleEntity.releasePilotControls`) | `Ch47CrewHover`, now sampled every tick after getting up: startup progress held at 1, rotor and sound pitch unchanged, no shutdown (it played before the fix) |

### Sixth pass (2026-11-25): two players, one flying and one walking about inside

The user: "a hyperrealistic and easy to use Chinook; test with two players, one walking around inside and one flying,
and see if there is any aesthetic, gameplay or sound design that needs improvement". Played with two pack clients over
LAN (the pilot hosting, the walker a separate client in survival; `design/playtest/second_client.py`, guide section 9).

| Found | Fixed | Seen in game |
| --- | --- | --- |
| **The walker was killed as the Chinook took off** ("crushed by a vehicle"): the hull's ramming hit everyone inside its boxes. Every trial before used creative players, who take no damage | People in the cabin are carried: ramming and push-out pass them by (`VehicleEntity.carriesAboard`) | Survival walker through lift-off, a climb and forward flight at 23 m/s: 20/20 health. `Ch47GroundIdleCrew` |
| **The engines cut out as the 20 s start finished**, unless Space was held through it; and at every touchdown, so dropping troops cost another start (the shut-down sound played straight after the start-up) | Ground idle (`idlesOnGround`): flight rpm at flat pitch on the ground until descend is held there for 2 s (after 1 s down) or the pilot leaves | Cold start hands-off: running at 157 ticks on the ground, no shutdown; landed: still running; descend held: shut down at 40 ticks, server and client in step. `Ch47GroundIdleCrew` |
| The cockpit and nose couldn't be clicked: vanilla finds an entity only near the 16-block section its origin is in, and the nose is 7 m from it. A round at the nose from the side passed through the same way | `LargeVehicles`: rounds (`BulletEntity`) and the view (`LargeVehiclePick`) also test every long vehicle's whole box | Clicked to board at 5 and 7 m ahead of the origin, beside the cockpit |
| A right-click anywhere in the cabin sat the walker in the first free seat (the co-pilot's); a punch hit the hull | Aboard, the view is never on the Chinook, nor on the world behind its walls (`CabinClient.filterPick`); a seat is taken by pointing at it (`cabinSeatOnRay`) | Empty-handed click on a wall: nothing; pointing at the right door gun's seat: "Right Button: take the right door gun", seated, the crew door slid open; got up again inside |
| From the pilot's chase view, the walker standing still on the ramp ran flat out (limb swing read the flight) | Remote people aboard swing their limbs to their walk in the cabin (`LivingEntityCabinWalkMixin`) | Standing: legs together over four frames; walking: striding |
| Footsteps aboard were silent on both clients | Steps from the walk in cabin coordinates, on each client: the floor's metal step, or a placed block's | Eight steps heard by the walker and the same eight by the pilot, at the walker's feet |
| The walker heard the outside engine mix, Doppler-lowered (pitch 0.94) at speed; no wind at the open ramp | Aboard the cabin: the interior mix, no Doppler (`HelicopterEngineAudio.isAboard`); wind by airspeed and nearness to the open ramp, crew door or gun window (`Ch47CabinAudio.WindSound`, the A-10's canopy roar, deeper) | Engine 0.65 at pitch 1.00 aboard; wind 0.08-0.16 at the hover |
| The pilot had no sign of the start-up, the ramp, door or sling state, nor which keys work them; the sling messages said "N" whatever the binding | A two-line strip under the crew list (engines with a start/shut-down bar, people aboard; ramp, crew door, sling), key hints for a few seconds and the start or shut-down key on the ground; the sling messages name the bound key | Off, starting 77%, ground idle, shutting down, running, spooling down; ramp moving; sling lowered and carrying |
| Nothing said what a click on the outside does | On foot outside: "board, pilot's seat" or the crew door | Seen beside the cockpit |
| It climbed at 10 m/s and came down at 11 (the light helicopters' rates); a held descent set off the sink-rate warning (the missile beeper) | `helicopterVerticalRateScale` 0.6: 6 m/s up, 6.6 down | Climb about 5-6 m/s; landed without the warning |
| Parked, the helmet display read "▼1.2" (gravity in the hull's velocity on the ground) | 0 on the ground | Gunner's view parked |

Also played, working: boarding over the lowered ramp and the jump lights from outside; standing on the level ramp in
flight; a chest placed and opened by the walker and seen by the pilot; the walker restored aboard after a relog and
carried when the Chinook was teleported; a slung LAV-150 seen from beside the flight path on the non-pilot client
(pendant bowing, four legs, the load trailing).

Not covered: a dedicated server (the host was the pilot's integrated server), ping (both clients on one machine), the
wind and footsteps by ear, night.

### Seventh pass (2026-11-25): the user's own play

| Asked | Done | Seen in game |
| --- | --- | --- |
| Door guns with an MG34's sound | No MG34 recording with a licence that can be checked exists (Freesound's MG42 takes are made from a game's effects and a YouTube clip; the 1943 War Department film of the MG34 has no live fire). The report is built from Superb Warfare's M60 (by permission), pitched 8% lower with a heavier thump and the bolt's clack, near, far and gunner takes (`prepare_door_gun.py`, `vehicle.gun.door_gun`). Rate of fire unchanged: whole ticks can't give an MG34's even 850 rounds a minute | `Ch47Sound`: 29 door-gun reports in 1.5 s |
| The trigger to use the side guns closer to each gun | A door gun is taken by pointing at the gun (or its seat) within 2.1 m of the eye, the feet within 2.3 m of its pintle (it was 3.2 m from the seat); whatever the view meets first wins, so the doorway round the stowed right gun works the door | At 4 m: the door prompt; next to the gun: "take the right door gun" |
| Pilot's view higher, to see over the panel | Eye raised from 2.3 to 2.5 m | Outside visible over the glare shield, displays still in view |
| Glitch going from co-pilot to pilot (screen and chopper jumping) | A client taking over the flight seeds its flight model from the helicopter as it is (attitude, engines); it kept what it had when that player last flew it | Into the pilot's seat in a hover: the nose settles 1.4 degrees over 2 s; before, it snapped 10 degrees down and lurched forward |
| A more forgiving sling hook | The hanging hook takes a load within 4.5 m across, from 3.5 m under its roof (the hook lying beside it) to 7 m over it (was 3 m from the roof middle); hooked from high up the rope pays out and the winch takes it in; the HUD says "<load> in reach: N"; a load held fast far past the rope lets go | Hooked from 12 m up; HUD "LAV-150 in reach" |
| Getting up while moving glitched as it levelled | One forced handover sync instead of 40 (each snapped the aircraft and the cabin's camera); the handover eases toward where the aircraft will be; the client flies it on along its velocity until the server's states arrive; synced states eased toward the projected position | Frame trace at 21 m/s: was 9.7/32.8/22 m/s juddering for 2 s and the cabin view bouncing 0.8 m a frame; now 21.6, 23, 29, 27, 26, 24 ... 21.6, view steady |
| More gravity on the rope | Drawn rope 2.6 times real gravity, a third of the air drag, a heavier hook; the empty hook swings as heavily on the server | Hangs straight under the load in the climb |
| Start-up and flying sound louder, flying heavier | The files were 7-10 dB under the other helicopters': flight loop 7% lower, body +3.5 dB, levelled +7.4 dB (seamless, `heavier_engine.py`); start +6 dB, shut-down +7 dB; aboard it plays at full volume (was 0.65); outside loudness 3.0 (was 2.4) | Aboard: engine 1.00 |
| Light inside the cabin poor (black ceiling, neon green lines, flaring jump light) | Lamp light worked out on cells of long faces (corner-only light ran a long cable run's end colour along it and left big panels black), laid on the face's drawn triangles; dome lights wrap onto the ceiling round them and reach the floor; jump lights and formation strips no longer colour the cabin; small lenses instead of flares | Midnight: ceiling and walls lit warm, cable runs dark, small dome and jump lamps |

### Eighth pass (2026-11-25): rotors, set-down load, unattended hover and cabin blocks

| Asked | Done | Seen in game / tested |
| --- | --- | --- |
| Ascending, blades seem to rotate slower | Governed 225 rpm (`rotorTurnPerTick`, 67.5°/tick instead of 146–180° aliasing with 3 blades); blades cone up with collective (`Ch47Renderer.coneDegrees`/`cone`, drooping 3° at rest and coning up in hover/climb); engine pitch and gain lean into climb load (`HelicopterEngineAudio`, `aboardCap` 0.9 for headroom) | Played (eye-level side-on captures): parked blades droop downward, hovering blades cone symmetrically upward on both hubs; `Ch47Sound` (pitch 1.03, vol 1.006 in climb) |
| Carried vehicle glitching glitches the Chinook (and on disconnect) | Helicopter and its slung load pass through each other (`ChinookSling.keepsClearOf`, `partingId` grace after release, `VehicleEntity.keepsClear` in collision/ramming/`restOnVehiclesBelow`); descent cushions and stops 1.2 m over a set-down load (`ChinookSling.cushion`); rope takes weight smoothly over 35 cm (`ROPE_GIVE`) | `Ch47SetDownLoad` (`-PwithPack`): holding descend over set-down AH-6 held 1.20 m clearance with 0 reversals; release steady (0 reversals, max step 0.051 m); health 220/220 and 72/72 |
| Hovering Chinook fell on world reload / dropped when abandoned | `TAG_HELICOPTER_RUNNING` saved and loaded (legacy fallback guarded by `TAG_VEHICLE_ID` so fresh `/summon` starts cold); unattended hovering Chinook descends and lands itself unhurt (`autolandsUnattended`) | `Ch47CrewHover` (`-PwithPack`): saved/loaded in hover stays `progress=1.00`, `over true`; abandoned descends smoothly from y -45 to -60, `ground=true`, 220/220 HP, `progress=0.00`; `Ch47Sound` confirms cold start |
| No furnace or chest animations and cabin block lighting looks off | `CabinNetwork.LidPayload` + `CabinBlocks.lidded` swing chest/ender-chest lids and barrel `OPEN` state with vanilla sounds; `CabinBlockEffects` adds lid animation, lit furnace mouth flames/smoke/crackle and per-cell cabin light (hull + dome lamps + nearby lit furnaces); `Ch47Renderer` draws chests via `BlockEntityRenderer` and all cabin blocks with `CabinBlockEffects.light`; `VehicleLightsLayer` casts flickering warm light from lit furnaces | Played in `ChinookTest` under Photon shaders (noon & midnight): furnace faces into cabin, emits front-mouth smoke and flame particles while smelting, washes floor/walls/chest/barrel in warm light at midnight; chest lid swings open and shut cleanly; no black chests or daylight-lit furnace at midnight |

All eight CH-47 tests pass with `-PwithPack` (`Ch47RampDoor`, `Ch47LoadedHover`, `Ch47SetDownLoad`, `Ch47Sound`, `Ch47CrewHover`, `Ch47GroundIdleCrew`, `Ch47SlingRope`, `Ch47FlightFeel`). Not covered by this pass: dedicated server, multiplayer over ping, and listening by ear (engine climb note, furnace crackle, chest/barrel sounds).

### Every placeable block aboard: light, look and sound; the pilot's head (2026-11-26)

The user: "test all the items placeable inside the heli and ensure the lighting, animation and sounds are all preserved
for them", "you should be able to move your head around in the pilot seat in first person", "the passenger in the back
kind of bugs out when the chinook hard banks right or left with cargo in the back", "fix the UI of the speed" and "remove
the chinook startup time indicator and preserve it like the other helis". Played in `ChinookTest`
(pack client, Photon), with the cabin filled through `/jegcabin put` and by hand, and compared against the same blocks
placed on the ground beside it.

| Found | Fix | Checked |
|---|---|---|
| Blocks that give light gave none aboard. Lanterns, torches, glowstone, sea lanterns, shroomlight, froglights, the respawn anchor and the ender chest were drawn by the cabin's light: dark at night, and nothing round them lit. Only a lit furnace lit anything | Each block's own light level lights its cell and the cells round it, and washes the walls in its colour (`CabinBlockEffects.light`, `emitters`, `colour`; `VehicleLightsLayer`, the 16 brightest). Magma, which glows as drawn, is full bright | Midnight: lanterns, glowstone, shroomlight, sea lantern, magma and the respawn anchor glow as their twins on the ground do; the cells and walls beside them are lit |
| No block's own look but a furnace's: no torch or candle flames, no end rod sparks, no crying obsidian tears, no spore blossom spores, no ender chest particles, no candle or respawn anchor sounds | Vanilla `animateTick` runs for every block aboard as often as for a placed one that far away. `ClientLevelCabinEffectsMixin` puts what it makes on the drawn block, and the particles are carried with the cabin. The furnace's hand-made flames went | `/jegsounds`: candle ambience, respawn anchor hum and furnace crackle logged in their own cells. Torch, soul torch, wall torch, candle and furnace-mouth flames and smoke, crying obsidian tears, ender chest and spore blossom particles seen. At 30 m/s in a banked circuit, tears and spores stay by their blocks (they streamed aft before the carry) |
| Shulker box lid didn't open; the enchanting table had no book | Both drawn by their block entity's renderer like the chests; the shulker's lid opens on the lid message; the book opens and turns to the nearest player in the cabin's frame | Lid raised while open and settled shut after; the book open and facing the player |
| Every placed block took its default state: a torch on a wall stood in mid-air, a lantern under the ceiling stood on nothing, a grindstone lay on its side, candles couldn't be lit | `CabinBlocks.placedState` on the placing client and the server alike, from the face clicked: wall torches, hanging lanterns, attached faces, end rods and amethyst facing out; candles light with flint and steel or a fire charge and go out with an empty hand (`lightable`, `toggleLight`) | Wall torches leaning out from the side walls and the bulkhead, flames and smoke on them; the lantern hanging on its chain; a candle put out (`block.candle.extinguish`) and relit (`item.flintandsteel.use`), both at its cell |
| Someone standing in the cabin "bugs out" in a hard bank with cargo aboard: drawn upright under the eye the cabin places, they hung 1.4 blocks to the low side at 60 degrees, through the wall and the blocks stowed there | People standing in a cabin are drawn tilted with it about their eye (`VehiclePassengerRenderHandler.tiltWithAircraft`, `CabinClient.cabinOf`), as seated riders already were about their hips, so their feet stay on the floor | Two clients, both aboard, in a 60-degree banked circuit at 30 m/s (`/jegfly steep 30 40`): the walker stands along the cabin, parallel to the stacked blocks beside them, feet on the aisle. The walker's own camera: 0.0004 blocks of jitter |
| The speed in the status card sat apart, against the card's right edge, while hull and energy read after their bars | `HudPanel.readingAfterBars`: the speed starts in the bars' reading column | Pilot seat: 139/220, 94% and 0.0 m/s in one column |
| A start-up bar ("Engines Starting 77%"), engine states and start or shut-down keys in the Chinook strip; no other helicopter has them | Taken out: it starts as the others do, heard and seen in its rotors. The strip keeps the ramp, door, sling and who is aboard | Holding Space to start: no bar; the strip reads "Ramp Closed, Crew door Shut, Sling Stowed, 1 aboard" |
| Pilot's free look kept a world heading (the cockpit swung round it in a turn) and snapped to the nose on release | `PilotHeadLook`: the head turns against the airframe while C is held and eases back when let go (all helicopters' pinned driver seats) | Looked 60 degrees right, then flew: window frames stayed put in the view as heading and speed changed; on release, back to the nose |

Also checked, already working: barrel, chest, trapped chest, shulker box and ender chest open and close with their
sounds at their cells; furnace cooks, glows and crackles; a hand-placed furnace faces the placer with smoke at its mouth.
Refused as before: campfire, brewing stand, lectern. Not covered: redstone lamps and copper bulbs light only by
redstone (none aboard), so they stay as placed. The enchanting table doesn't count bookshelves aboard. Listening by ear,
a dedicated server, and a second player watching.


## Trials (2026-11-20)

Both ran in the pack client over mcpfabric. The code is in the worktree `../jegn-sabletrial`, branch
`trial/sable-cabin` (not committed): `/jegtrial` is the Sable trial, `/jegcabin` our own cabin.

| Check | Sable sub-level | Own cabin (D1) |
| --- | --- | --- |
| Standing still in a 30° banked turn, 30 and 70 m/s | drift p95 0.03 blocks/s; camera ≤ 0.005 blocks/frame | drift 0; camera 0 (by construction); 0 server corrections |
| Jumping in the banked turn | lands about 1 block toward the low side (world gravity) | lands on the spot (cabin "down") |
| Walking | normal | 4.3 m/s; walls stop the body at the right distance |
| Blocks placed in flight / chest opened in flight | yes / yes | yes (chest, furnace, lantern at 50–70 m/s) / yes |
| A rifle fired aboard | wall stops it, open rear lets it out | the same (25 rounds stopped by the wall) |
| Save, hard kill, relaunch mid-hover | cabin, player's spot and blocks back | the same; the player re-boards on the same spot |
| Survival, minutes airborne | — | no "flying" kick, no fall damage |
| Pilot lag if the cabin flies the aircraft | ping + ~1.5 ticks (Sable's 75 ms buffer) | none (pilot's client flies it, as the AH-6) |
| With Sable loaded too | — | unchanged; no Sable errors |

**Lessons for P3:**
- **Moving the player's chunk loading.** Skipping vanilla's move handling for people aboard also skips moving their
  chunk loading. The cabin then flew out of the loaded area and froze. The server must call
  `level.getChunkSource().move(player)` after placing them.
- **Measuring the cabin's speed.** The trial's report now measures the cabin's speed alongside each run, because the
  freeze above first showed up as perfect but meaningless numbers.
- **Interpolation.** People aboard are drawn from the cabin's previous and current poses (`xo` from the previous one),
  so the camera interpolates with the drawn cabin.
- **Not yet covered:** a second player watching someone walk aboard, limb animation and footsteps aboard, mobs aboard,
  and chest contents across a restart.

**Visual checks (second pass, simulated ping on the trial's own packets):**
- **Smoothness:** the cabin was drawn at 50 m/s from aboard and at 40 m/s from the ground. No-ping, 120 ms ± 30 and
  200 ms ± 60 all gave 0 hitch frames out of about 455–565 per run. The cabin never ran out of poses, with the
  interpolation delay at 1–2 ticks. The drawn speed was within about 3–4% of true on average (worst frame 22%, which
  is frame-timing noise).
- **Placing blocks:** a placed block is drawn the moment you click. At 120 ms ping the server confirmed it about 233 ms
  later, and none were taken back. As in vanilla, the game refuses a block that would go into the cell you stand in.
- **Glass:** draws correctly. It's vanilla clear glass, so a window only looks like an opening.
- **Lighting is wrong in both trial modes.** World light comes in through the walls: a glowstone floor under the cabin
  lit its inside at midnight. Lamps placed inside don't light the cabin.
  - With each block lit by its world cell, the light is steady but patchy.
  - With one light for the whole cabin, the light pumps by up to 25 levels as the cabin crosses lit ground.
  - The Chinook must light its cabin in cabin space: daylight by the time of day and whether it's under cover, plus
    its own lamps and the lamps placed in it. No world block light.
- **Teleports:** a server teleport didn't take the player out of the cabin; the carry pulled them straight back. Any
  teleport (commands, ender pearls, respawn) has to take the player off the cabin first.

## Architecture

### Shared systems (new package `ttv.migami.jeg.vehicle.cabin`)

These are written for the Chinook and documented in the guide as the way to build any walk-in vehicle.

**`CabinSpace`**: implemented by a vehicle that has a room.
- `cabinFrame(partialTick)`: the pose of the room (position plus yaw, pitch and roll) at the tick, or between ticks
  on a client. It's built from the same interpolated position the renderer uses (`interpolatedVehiclePosition`), so
  the room drawn and the room walked in are the same.
- `cabinColliders()`: the fixed interior boxes in cabin coordinates (floor, walls, ceiling, benches, the hook-well
  grate, the companionway to the cockpit), plus the ramp's boxes at its current angle, plus the placed blocks'
  shapes. All of these are axis-aligned in cabin coordinates, so vanilla's box collision works unchanged.
- `aboardVolume()`: where an entity counts as aboard (the room, plus the ramp while it's open).

**Aboard tracking (`Aboard`, attached to the entity)**: the vehicle id, the position in cabin coordinates, the yaw
relative to the cabin, and on-floor.
- **Attach** when the feet enter `aboardVolume()`: walking up the lowered ramp, stepping in at the crew door, or
  dropping in through the open ramp.
- **Detach** on leaving the volume. The entity keeps its world position, and its velocity becomes the cabin's plus
  its own (so you fall out of the back at the Chinook's speed). Also detach on death, dimension change, the vehicle
  being removed, and when the wreck breaks up.

**Movement aboard, the core of D1.**
- A mixin on `Entity.move` sees that the entity is aboard. It converts the move into cabin coordinates, collides it
  against `cabinColliders()` with vanilla's collision code, and converts the result back through the cabin frame of
  this tick.
- Gravity is the cabin's "down", so you stand on the floor whatever the Chinook's attitude.
- Each tick the cabin's own motion (translation and yaw) is applied to everyone aboard, so they are carried.

**Networking, relative to the cabin.** This is what Sable does for its sub-levels, and what the guide says jitters
without it.
- A player aboard sends `CabinMovePayload` (cabin coordinates, relative yaw, on-floor) in place of the world position
  of vanilla's move packet. The server places them at its own cabin frame times that position, so the passenger's
  client and the pilot's client can each run at their own lag without the passenger sliding about.
- `CabinOccupantsPayload` sends other clients the cabin coordinates of everyone aboard. They draw them at their own
  interpolated cabin frame, never from world-space interpolation.
- Server-side exemptions while aboard (`ServerGamePacketListenerImpl` mixin):
  - the "moved too quickly" and "moved wrongly" checks
  - the flying-kick counter (`aboveGroundTickCount`)
  - fall distance, which is reset while standing on the cabin floor

  Without these, players at 80 m/s are rubber-banded and kicked within four seconds of take-off.

**Things that read world motion and must read cabin motion instead** (found by thinking ahead; phase 3 tests each):
- **Walk animation and limb swing** (`LivingEntity` walk animation, and Not Enough Animations / Fresh Animations in the
  pack). Otherwise legs run flat out at the Chinook's speed.
- **Footstep sounds and distance walked.** Steps on the cabin floor sound like metal treadways, not the air below.
- **Sprinting, hunger and the "on ground" flag.** Jumping must work on the cabin floor.

**Picking and clicks.**
- A mixin on `GameRenderer.pick` ray-casts the cabin in cabin coordinates first: placed blocks, seats, panels, the
  ramp control and the walls.
- The vehicle's own entity box never takes a click from someone aboard. This is the FSV-6's lesson: every click
  inside picked the truck.
- Clicks go to the server as `CabinUsePayload` (cell, face, hit point, hand): place, use, start/abort/finish breaking.

**Cabin grid (`CabinGrid`)**, used by D6.
- Cells are whole blocks in cabin coordinates. Each holds a block state plus block-entity data for the working set.
  The grid is saved in the entity (and so survives the crowbar's container) and synced by `CabinGridPayload` on
  change and on start tracking.
- `CabinGridLevelReader`: a `LevelReader` over the grid, with light and biome delegated to the real level, so
  `getStateForPlacement` and `updateShape` give real placements (stairs face you, fences and panes connect, slabs
  sit top or bottom).
- **Working blocks** reuse the FSV-6's module code (`SupportWorkstations`, `SupportModules`): vanilla menus bound to
  the vehicle (`stillValid` overridden), `ContainerLevelAccess` given real coordinates, and the vehicle's own cooking
  tick.
- **Breaking** takes the player's normal dig time. Drops go to the player's inventory, or to the floor as carried
  items. Container contents spill onto the floor.
- **Drawing:**
  - `submitMovingBlock` for ordinary blocks; the block-entity path for chests and shulkers (`drawSpecialBlock`).
  - Light comes from lamps placed in the grid (a flood fill in cabin cells), plus the cabin's dome lights, plus the
    daylight through the windows.
  - Each lamp is also a LambDynamicLights source.
- **No benches** (D9): the cabin is left empty; a door gunner's seat can't be taken while a block stands in its cell.

### The Chinook itself

**`ChinookEntity extends VehicleEntity implements CabinSpace`**, with `Ch47Rig` mirroring `design/chinook/rig.json`.

**Flight.** The `"rotor"` engine family, so it flies like the AH-6 and Mi-28 but heavier (`"mass"`, slower
`yaw_speed`, `pitch_speed` and `lift_speed`).
- One small protected hook in `tickHelicopterMovement`: `externalAcceleration()`. The sling's rope tension acts on
  the Chinook through it, so a swinging load rocks the aircraft.

**Rotors from data.** A `"rotors"` list (`RotorInfo`: centre, radius, blades, direction, phase) replaces the
single-rotor assumptions:
- ground strike (`upperMainRotorContactInfo`)
- blur
- downwash

The two rotors turn in opposite directions with their blades phased 60° apart, so they intermesh as the real
synchronising shaft keeps them. A test pins the phase, because a bug there shows as blades passing through each other.

**Ramp (`ChinookRamp`).**
- A synced target and angle: closed, level (in flight, for standing on and firing your own gun out of the back), and down (resting
  on the ground; its angle is solved against the terrain under its lip).
- The upper ramp door retracts into the aft pylon as the ramp opens.
- Collision is a run of thin steps along the ramp at its current angle, so you walk up it. People standing on it
  are carried as it moves.
- Controls:
  - the pilot's spare key (X: the pilot has no weapons)
  - the ramp control panel inside at the aft bulkhead
  - an outside switch by the hinge when landed
- Sounds: a hydraulic pump whine and end-stop clunks.

**Sling (`SlingLoad`, server, saved)**, all on the centre hook.
- **States:** stowed → lowering → hanging → hooked → carrying → releasing.
- **Physics, sub-stepped 4× per tick:**
  - The load (its lift centre) is a point mass on the pendant. The rope is an inelastic constraint with a little
    damped stretch.
  - Real gravity (9.8 m/s², not Minecraft's 32), so the swing period is a true 2π√(L/g): about 6 s on a 9 m pendant.
    This is what makes it look heavy and natural.
  - Bluff-body drag scaled by the load's size and mass, so in forward flight it trails back and weathervanes.
  - The hook's swivel, so the load turns slowly in yaw.
  - Light damping, so a swing dies out over a few periods rather than at once.
- **Tension acts back on the Chinook** through `externalAcceleration()`.
- **The load's own movement code is off while slung** (`VehicleEntity.isSlung()`). It's moved by `move()` so terrain
  stops it, it takes landing damage above a threshold speed, and it must be empty, as a tow is.
- **Lift points** come from the load's data: a new `"sling": {"lift_points": [...]}`, and by default the top corners
  of its `body` box.
- **Drawn from the Chinook's smooth position** (`clientSlingPose`, as `clientTowPose` does for the FSV-6's tow).
  Otherwise the load lags a block and jumps.
- **Ropes (`SlingRopeLayer`):**
  - the pendant, the apex ring and four legs to the posed lift points: taut under load, sagging when slack
  - while hanging empty, the four leg hooks swing freely

### Key map

| Who | Key | Action |
| --- | --- | --- |
| Pilot | as the AH-6 | flight (cyclic, collective, yaw), start-up, flares |
| Pilot | N (the tow key, `hasTowKeyAction`) | tap: lower or raise the sling; tap over a load from a steady hover: hook up; **hold 1 s**: release (guarded, like the real hook switch) |
| Pilot | X (weapon switch; the pilot has no weapons) | ramp: closed → level → down → closed |
| Pilot | F5 | the helicopter views, plus a **hook view** looking straight down from the centre hook while the sling is out |
| Aboard, on foot | right-click | seats (sit), the ramp panel, placed blocks; place blocks as anywhere else |
| Door gunners | as the Mi-28's gunner | the M240s (the right one only with the crew door open) |

Check every default against the pack's bindings (read `options.txt` after a `-PwithPack` run), as the pack-keys note
says.

## Phases

Every phase ends with a play-through over mcpfabric with the pack (required by CLAUDE.md) and a focused test only where
a number must not regress. Each phase updates `IMPLEMENTATION_STATUS.md` and the brief.

### P0 Research and the cabin spike

**Research and setup:**
- Fill [BRIEF.md](BRIEF.md) from real CH-47F drawings and photos: cabin, ramp and gear dimensions, floor height, the
  rotor heads' positions and droop, light positions, and the cockpit layout.
- Pack check with the `yftm` MCP (it was down on 2026-11-20):
  - Is Sable or Create Aeronautics on the server and the clients, at what version?
  - Is there any anti-cheat or movement-check mod?
  - Is there a parachute mod (jumping out of the ramp)?
  - Which claim mods, and the server's entity tracking range?
  - Add what interacts to `packMods`.
- List the CC0 recordings needed (BRIEF "Sound"). The tandem-rotor blade slap is the signature, so it has to be a real
  CH-47 recording.

**Spike:** `CabinSpace` on `TestHelicopterEntity` with a plain box room. One player walks figure-eights while it flies
circuits at full speed, banking, in the pack.

**Exit (all measured):**
- The player's cabin position drifts less than 0.05 blocks a second with no input.
- No rubber-banding.
- No kick after 3 minutes aboard in flight.
- No fall damage on a fast descent.
- Legs animate at walking pace.
- Then record D1 as confirmed, or bring the Sable alternative back to the user with what was learned.

**Gotcha to settle in P0:** if the server has Sable, our `Entity.move` mixin and Sable's entity-tracking mixins touch
the same code. Test against its jar, outside `-PwithPack` if needed.

### P1 Model (`design/chinook/generate.py`)

Our own generator, as the sentry and bush plane have. It writes the geometry, two LODs, `rig.json`, the Blockbench
file, a preview, the icon and the paint mask, and textures through `bake_unique` and `detail_texture`.

**The airframe:**
- rounded-square fuselage, sponsons and gear (two front, two rear; the rear right wheel steers)
- forward pylon with its transmission fairing
- aft pylon with the two T55 nacelles and their exhausts
- chin windows, the crew door with its hoist fairing, and the left escape-hatch window

**Moving parts, each a bone on its real hinge:**
- the two three-blade rotors: droop at rest, coning in flight, and swashplate tilt that follows the controls
  (differential lateral cyclic when yawing, differential collective when pitching)
- the ramp and the upper ramp door, with their hydraulic actuators (cylinder and rod, checked across the stroke)
- the gear oleos, which compress under weight
- the hook-well hatch and the centre hook

**Cabin:**
- no troop benches (D9); a jump seat by each door gun
- treadway floor with rollers, and ribs with quilted insulation
- ceiling cable and hydraulic runs, and portholes with glass (seen through from both sides)
- the ramp panel, with red and green jump lights above the ramp
- dome lights (white and red) and a fire extinguisher
- the companionway to the cockpit

**Cockpit:**
- the CAAS glass cockpit: five MFDs drawn live (`CockpitPanel`, as the AH-6's)
- overhead and centre consoles
- NVG-green lighting at night

**Finish:**
- matt Army aircraft green, low-visibility black markings and a generic serial (no real unit insignia)
- NO STEP and rescue stencils
- exhaust soot on the aft pylon, oil streaks under the transmissions, dust and mud on the gear and belly

**Review:** renders from every side, and from the eye points (pilot, a person standing mid-cabin, on the level ramp,
at the hook-well grate) with `render_eye.py`.

### P2 Flight, crew and the shell of a vehicle

**Entity and data:**
- `ChinookEntity` and `data/jeg/vehicles/ch47.json`: mass, engine, seats, cameras, `obb` fitted part by part (nose,
  cabin walls, belly, sponsons, pylons, nacelles; the room hollow), and armour parts.
- Rotors from data, in place of the single-rotor code.
- Start-up: APU whine, then each engine catching in turn, then the rotors engaging (about 12 s), with a hot restart.

**Seats:**
- pilot (right), copilot (left)
- left and right door gunners (no ramp gunner)
- no troop seats (D9)

**HUD and cameras:**
- HUD: the shared status card plus a sling card (state, pendant length, load and mass, swing angle; heavy loads show their mass, nothing is refused).
- The helmet HUD in first person.
- F5: the chase, tail-boom-style, wing-style and hook views.

**Around the vehicle:**
- Far tracks, Doppler, the aircraft wreck path, and Carry On blacklists (both folders).
- An assembly recipe and the icon.
- `partBounds()` covering both rotor discs, so it isn't culled while visible.
- The sandbox world `ChinookTest`, built by `ChinookSandboxSetupClientGameTest`: an airfield, an LAV-150, a howitzer,
  a speedboat on a pond, an overweight BMP-2, a firing range behind the parking spot, and chests of blocks.

**Exit:**
- Flies in the AH-6 family, heavier.
- Measured top speed, climb and turn rate written in the brief.
- Rotors intermesh in a slow-motion capture (`Ch47RotorPhaseClientGameTest` pins the 60° phase).

### P3 Walking aboard (`CabinSpace` for real)

**Build:**
- Everything in "Shared systems" except the grid.
- Boarding: up the lowered ramp, and in at the crew door. A right-click on the outside sits you in a pilot seat near
  the nose; elsewhere it puts you inside at the crew door when landed (the guide's "a right-click boards it").
- Sitting in seats from inside, and getting up back into the cabin.
- Logout and login aboard, kept as cabin coordinates and restored when the Chinook loads.
- Death aboard.
- Dropped items carried and coming to rest on the floor. Free mobs aboard carried (pets follow you in).
- Remote players drawn relative to the cabin.

**Focused test `Ch47CabinWalkClientGameTest`:**
- The spike's measurements, now on the real cabin.
- Walk from the ramp to the cockpit during a banking turn at cruise.
- Never through a wall or the floor; never out of a closed cabin.

### P4 Ramp

**Build:**
- The three ramp states and the solved ground angle.
- Collision and carrying on the ramp.
- The pilot key, the inside panel and the outside switch, with prompts at each.
- The jump lights.
- Sounds.
- Lights that sit on the moving ramp come on only in its working position (guide rule).

**Focused test `Ch47RampClientGameTest`:**
- Walk up from the ground into the cabin.
- Stand on the level ramp in flight for 30 s without sliding off.
- The ramp closes on someone standing on it and lifts them.
- Walking off the end in flight drops you at the Chinook's speed.

### P5 Guns aboard

**Your own gun:**
- JEG guns fired by someone aboard start with the Chinook's velocity added.
- Their rounds are tested against the cabin walls in cabin coordinates: a wall stops them with a spark and no
  damage, so you can't shoot through the fuselage from inside. Through the open ramp, the door windows and the
  portholes' openings they fly free.
- The Chinook never takes damage from its own occupants' rounds (guide: rounds never strike their own vehicle).

**Mounted guns:** the two M240H side door guns (no ramp gun); the crew door slides open for the right one.
- muzzles on the drawn guns
- real slew rates (pintle, 3°/tick)
- medium flash
- a far report

**Focused test `Ch47RampGunClientGameTest`:**
- From the cabin, a rifle's rounds hit a target 100 blocks behind through the open ramp.
- The closed ramp stops them.
- The Chinook's health is unchanged.
- The door gun reaches its full range.

### P6 Blocks in the cabin

**Build:**
- `CabinGrid`, placement through `CabinGridLevelReader`, and breaking.
- The working set's menus, from the FSV-6 code.
- Drawing (both block paths), light, saving and syncing.
- (Benches dropped, D9.)
- Refusing ticking machines with a message.
- Nothing lost:
  - pack-up keeps the grid
  - the wreck drops every block and every container's contents
  - pack-up is refused with someone aboard

**Focused test `Ch47CabinBlocksClientGameTest`, in flight:**
- Place a chest, crafting table, lantern, stairs and a slab with real clicks aimed by the view.
- Open the chest and craft.
- Break the lantern; the cabin goes dark at night.
- Land, pack up, unpack: everything is still there.
- Use `SupportLookClientGameTest`'s pattern for the drawn blocks.

### P7 Sling

**Build:**
- `SlingLoad` and its physics.
- Lift points in the data of every liftable vehicle.
- The pilot's hookup.
- Rope drawing.
- The hook view.
- Overload behaviour, and release (guarded hold).
- Load release when the Chinook is wrecked.
- **(Dropped, user 2026-11-20: the pilot hooks up alone.)** The ground crew latching legs by hand. Right-click a dangling hook to take it, then right-click near a lift
  point.

**Focused test `Ch47SlingClientGameTest`:**
- Hook up an LAV-150, lift it, and fly 300 blocks.
- The swing period is within 10% of 2π√(L/g).
- The amplitude never grows; it halves within about 4 periods after a stop.
- It trails back in cruise.
- Release from 1 m: the load lands upright and undamaged.
- The BMP-2 hooks but can't be lifted.
- An occupied vehicle is refused.

### P8 Look and sound

**Sound** (the BRIEF's table):
- blade slap near and far, turbines, and the cabin's transmission whine
- APU, ramp hydraulics, winch, hook latch and release, rope strain, and the M240s
- through `AircraftSoundRange`, far tracks and Doppler

Done 2026-11-21 (the user: "realistic Chinook sounds, startup, flying and landing, clean, high quality"; then "it
doesn't need to be a real Chinook, just a heavy helicopter"): its own 20 s two-turbine cold start (`ChinookEntity.helicopterStartupTicks`
400, was 240), the flight loop, the distant rotor, the blades' slap swelling as it comes down to land, and the turbines
spooling down over the rotor's 20 s coast when the engines cut on touchdown. Built by `design/chinook/prepare_audio.py` from a close
UH-1 recording and a distant approach, made heavier (guide, section 7, "Helicopter sounds"); guarded by
`Ch47SoundClientGameTest`. Still to do from the list above: APU, the transmission whine, ramp hydraulics, winch, hook,
rope.

**Downwash:**
- a brownout dust cloud when hovering low over sand or dirt, and spray over water (`SmokeFx` and the speedboat's
  spray)
- loose items and players on the ground pushed away near the rotors

**Lights:**
- red anti-collision strobes, nav lights, and the green formation-light strips
- two steerable landing and search lights
- cabin dome lights (white, red, off) from the panel
- NVG-green instruments at night under shaders, following every rule in the fleet review's Lights rows

**Feel:** gear oleos squat on landing, a landing knock, and the crew camera rumble (`VehicleFeel`).

### P9 Readiness and hand-over

- Every gate G1–G13 in `design/READINESS.md`, and the guide's player checklist answered in the brief.
- `-PwithPack` runs of every Chinook test.
- The guide updated: "Walking about inside while it moves" now points to `CabinSpace`; new sections for slung loads
  and tandem rotors. Lessons go in the same change, as CLAUDE.md requires.
- Mark the Chinook "Ready for human testing" and list the new sounds for the user to listen to.

**Order:** P0 → P1 and P2 → P3 → P4 → P5 → P6 → P7 → P8 → P9. P3 comes first among the features because it's the
riskiest, and P5 and P6 depend on it. P7 (sling) doesn't depend on the cabin and can move ahead if the user wants it
sooner.

## Risks

| Risk | Where it bites | Plan |
| --- | --- | --- |
| Passenger jitter or drift aboard | P3 | Cabin-relative networking from the start, and the spike's measured exit criteria before anything else is built on it |
| Server movement checks or an anti-cheat mod rubber-band or kick people aboard | P0, P3 | Exemptions while aboard; check the server's mod list in P0 |
| Sable's mixins on the server conflict with ours | P0 | Test against its jar in P0; if it's on the server, tests must cover it |
| A 30-block entity culled or flickering | P2 | `partBounds()` covering the rotors; walk round it in the play-through |
| The sling oscillating unstably at 20 ticks/s | P7 | 4 substeps, implicit damping on the rope, and the test's no-growth check |
| A slung load or someone aboard ending up in an unloaded chunk | P3, P7 | The pilot loads the chunks around them; release the sling and detach anyone left behind if the pilot dismounts; test a 1 km flight |
| Items lost or duplicated in the grid | P6 | One source of truth (the grid), the wreck and pack-up paths tested, and ticking blocks refused |
| Performance with a full cabin and many players aboard | P3, P6 | At most 36 cells; per-tick cost measured in the play-through with 4 players (mcpfabric plus bots) |

## Open questions for the user

1. **D1:** our own cabin frame rather than a Sable sub-level. Confirm, or say if you'd rather depend on Sable/Aeronautics
   (and whether the server has it).
2. **D2:** the view stays level while the cabin tilts around you. Or should it tilt with the cabin?
3. *(answered 2026-11-20, see the decisions table)*
4. *(answered 2026-11-20, see the decisions table)*
5. *(answered 2026-11-20, see the decisions table)*
