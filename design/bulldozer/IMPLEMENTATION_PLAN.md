# BD-40 bulldozer implementation plan

**Implemented:** the vehicle now has runtime registration, controls, terrain work, cargo, audio and night lighting. This document records the original design and broader acceptance targets. Read [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for the exact implementation, verification and remaining manual/stress checks.

## Intended experience

Drive a weighty tracked machine into a dirt bank, lower the blade, hold the work button, and clear a broad strip of blocks while the engine loads up and the tracks crawl forward. Release the work button and destruction stops. Raise the blade and drive away. The hydraulics, cutting edge and collision volume must agree about where the machine is working.

The chosen machine is a **tracked bulldozer with a lifting front blade**, as confirmed by the user. The push arms swing up and down; the hydraulic rods extend and retract between their attachment points. The cab stays fixed to the chassis. Terrain is cleared progressively in groups of blocks at the blade, rather than deleting a Minecraft chunk at once.

The initial deliverable was the model, textures, rig and animation studies. Runtime integration is now implemented. Numerical handling and throughput values below are design targets; the status document gives the current values.

## Controls and feedback

| Control | Driver action |
| --- | --- |
| W / S | Forward / reverse; slow through zero before reversing direction |
| A / D | Differential steering; pivot on the spot when stationary |
| Space / Left Ctrl | Raise / lower blade; release to hold its height |
| Hold left mouse | Work the blade; only its contact area can break blocks |
| Shift | Brake; automatically hold when parked or the driver leaves |
| G | Toggle grade assist; on by default, with the selected grade shown |
| E / H | Existing vehicle cargo / player inventory controls |
| C / F5 | Existing free look / camera modes |
| Left Alt | Existing dismount binding |

These must be rebindable and active only for the bulldozer driver. For this vehicle, Left Ctrl operates the blade and **must not also apply the existing vehicle brake**; map braking to Shift in the bulldozer input context. Shift already does not eject JEG riders. Keep the behavior of every other vehicle unchanged. Disable tool inputs in menus and reset them when focus is lost. G is available because this civilian vehicle has no decoys. Do not add an R action; the pack's Iris shader shortcut also uses R.

Use a small construction HUD: blade height, grade assist, cargo fill, energy and work status. A faint footprint shows the next contact strip only while working or adjusting the blade. Protected blocks, a full hold or insufficient power show a short reason. Avoid weapon slots, target locks and combat crosshairs on this machine. First-person view looks over the hood at the blade; an elevated chase camera shows both blade corners and the tracks. Keep the camera stable through small surface bumps.

## Vehicle selection and assembly cost

The finished bulldozer must appear as **BD-40 Bulldozer** in the vehicle assembling table's **All** and **Land** lists, using `jeg:bulldozer` and the supplied `textures/vehicle_icon/bulldozer_icon.png`. Use the existing list, selection panel, ingredient counts and Assemble button; do not introduce a separate shop or currency.

| Material | Truck today | BD-40 target |
| --- | --- | --- |
| Iron ingots | 24 | **28** |
| Copper ingots | 8 | **8** |
| Redstone | 6 | **6** |

The bulldozer costs only four more iron than the truck. No diamonds, netherite, rare blueprints or military ammunition are needed. The exact ready-to-copy recipe is `design/bulldozer/assembly.recipe.json`; the name and sound subtitles are in `lang.fragment.json`.

Adding recipe JSON alone will not expose it: `VehicleAssemblyRecipeManager.ENABLED_ASSEMBLY_VEHICLES` explicitly whitelists supported vehicles. When gameplay is ready, register the entity and LAND vehicle data, add `Reference.id("bulldozer")` to that set, install the recipe under `data/jeg/vehicle_assembly/bulldozer.json`, and merge the language entries. The existing screen then discovers the recipe, resolves the icon and shows the cost. Verify pagination as well as the Land filter; the added entry increases the total vehicle count.

Keep these as prepared assets until the entity is functional, so the live selection menu never sells a missing or unfinished vehicle. The assembly and sound fragments are intentionally not activated by this model/plan revision. Selection, pricing and audio integration are release requirements, not optional later polish.

## Model and rig contract

Assets are already in the runtime resource paths listed in `README.md`. `DefaultVehicleResource` looks for the new `geckolib/models` and `geckolib/animations` paths, so legacy duplicate exports are unnecessary.

| Bone(s) | Driver of the pose |
| --- | --- |
| `root`, `chassis` | Entity position/yaw and smoothed terrain presentation |
| `track_left`, `track_right` | Independent track assemblies |
| `shoe_left_00..29`, `shoe_right_00..29` | Distance around the closed tread path |
| `wheel_left_0..4`, `wheel_right_0..4` | Signed track distance divided by wheel radius |
| `blade_lift` | Normalized authoritative lift, mapped to -4..24 degrees |
| `blade_level` | Opposite lift angle to hold the cutting face upright |
| `blade_yaw`, `blade_tilt` | Neutral in the first release; reserved for angle/tilt later |
| `lift_barrel_left/right` | Look along the base-to-clevis vector |
| `lift_rod_left/right` | Extend from the cylinder gland to the moving clevis |
| `cab`, `engine`, `exhaust`, `beacon`, `wiper` | Static detail, subtle engine presentation and optional secondary animation |

Locators: `cut_left`, `cut_right`, `blade_center`, `driver_eye`, `seat`, `smoke`. Bone coordinates and linkage constants are in `rig.json` and `generate.py`.

For lift angle theta, rotate the clevis `(13,12,-37)` about `(0,9,10)` around source X. Cylinder base is `(13,25,-1)`, mirrored for the other side. Set barrel angle to `atan2(deltaZ, deltaY)` and rod length to `distance(base, clevis) - 20`. The rod starts at the gland; scaling occurs about that point, not the cylinder's base. The preview verifies attachment and available stroke across the entire range. Use the same analytical solution in Java; do not hand-author independent piston motion.

The current renderer turns the source model's -Z nose toward entity forward. Centralize source-to-world transforms in `BulldozerRig`, including GeckoLib's source-X convention and the existing `180 - yaw` vehicle rotation. At zero body pitch/roll, a source point `(x,y,z)` maps to `entityPos + (x/16)*left + (y/16)*up - (z/16)*forward`, where entity yaw defines forward. Verify this against rendered blade corners at yaw 0, 90, 180 and 270 before allowing terrain destruction.

The five existing clips are idle, engine idle, blade-lift study and separate left/right track studies. The idle clip is named `idle` to match `VehicleEntity`'s fallback controller. The three clips ending in `_preview` demonstrate articulation. Runtime movement must use synchronized lift and actual signed travel, not play those clips by elapsed time: stationary tracks must stop immediately, and reversing must reverse them. Soft body roll is presentation, not an oscillating force applied to the collision solver.

## Driving and stability

Use `VehicleType.LAND` and `EngineType.TRACK`; no new broad vehicle category is needed. Give the dozer a low center of presentation, a broad track footprint, slow changes of speed and independently commanded left/right track rates.

| Parameter | Initial target |
| --- | --- |
| Forward / reverse travel | About 12 / 7 km/h |
| Working travel | About 4 km/h, reduced further by blade load |
| Acceleration | About 1.5 seconds to travel speed |
| Brake response | Stop from travel speed within about 0.5 seconds on flat ground |
| Stationary pivot | At most 20 degrees per second |
| Hydraulic motion | About 18 degrees per second, eased acceleration and hard end stops |
| Step height | At most one block in travel; work into a bank before stepping onto it |
| Presentation pitch / roll | Smooth, limited response; target caps around 12 / 8 degrees for ordinary terrain |
| Useful working slope | About 20 degrees; steeper slopes reduce traction and suspend cutting if unsupported |

The shared tracked movement currently accumulates `enginePower`, keeps coasting on release, uses a speed-dependent `wheelSteering`, and permits pivoting. Its `EngineInfo.acceleration` is throttle response, not a direct physical acceleration. Reuse the collision and movement infrastructure, but tune measured speed and stopping distance, not just JSON constants. Neutral input must settle to a hold. S brakes first and then reverses; holding the brake never creates reverse thrust.

Use stable kinematic ground movement with front/rear support samples on both track footprints, plus a center support check. Median/robust support estimates prevent one grass tuft or stair edge from tilting the whole model. Rate-limit visual terrain following; derive track phase from actual displacement and yaw. When airborne, suspend blade work, preserve normal gravity and keep the blade from acting as a suspension leg. On parking, seat loss, zero power or a stale input timeout, stop work immediately and hold the vehicle when supported.

Retain a fixed chassis/cab collision footprint. Keep blade contact as a separate swept volume derived from its actual pose; do not expand the entity's main AABB to the full raised-blade bounds. At a failed cut, soften the engine load and stop forward advance; exclude ordinary low-speed tool contact from the combat vehicle's wall-impact bounce and damage path. Do not disable fall damage or high-speed chassis collisions. Limit the steering sweep during work so a pivot cannot tunnel the blade sideways through a wall.

Avoid digging beneath either supporting track. Detect an unsupported front edge and stop cutting/advancing before driving over a newly created drop. Do not force-load chunks or push the machine into unloaded terrain.

## Block clearing

Use a dedicated server-side `BulldozerTerrainWork` controller. Clearing is deliberate: a driver must hold work, have sufficient power, and bring the low blade into contact while stationary or moving forward slowly. Travel, reversing, a raised blade, airborne movement and uncontrolled rolling do not destroy terrain. A stationary blade can finish the exposed blocks it already touches; it cannot reach farther into a bank without advancing.

Default work is approximately four blocks wide and one block deep in vertical cutting height. Permit up to two blocks in height only when their collision shapes actually intersect the working blade's swept face. Thin slabs, fences, waterlogged blocks and rotated poses need shape intersection, not a bounding-box-only test. Block selection is the union of the physical blade's old and proposed poses over the small next movement, clipped to its front contact layer. Query a tight, loaded-only region and take exposed blocks first; an unbreakable or denied block stops its contact lane so the dozer cannot remove blocks behind it.

**Grade assist:** capture an integer working floor from supporting ground when engaged. Never break a block below that floor. Raising the blade always works. Lowering beyond the floor clamps the work pose and shows the stop on the HUD. G deliberately disables the limit for a shallow cut, still within the mechanical lift range; track-support exclusion remains active. Re-enabling assist captures the intended current supporting floor, not the height of a raised transport blade. Persist grade state and target height.

Apply hardness-weighted work rather than instant mass deletion. Start with roughly 12-16 soil blocks per second and 1-2 ordinary stone blocks per second at full available load. This is a gameplay tuning choice; hard stone should feel much heavier than soil. Maintain cracking progress only for the active contact set. Releasing work clears the cracks and load feedback promptly. Prevent an arm-only upward sweep from becoming a vertical mining laser.

Use configurable `bulldozer_breakable` and `bulldozer_unbreakable` block tags. Soil, sand, gravel, clay, snow, leaves and ordinary stone are initial candidates; server owners control the set. Never break unbreakable-hardness blocks, bedrock, portals, moving pistons, fluids, blocks with inventories/block entities or denied protected blocks. Explicit denial wins over the breakable tag. Water entering a cut is ordinary Minecraft fluid behavior, never a fluid-deletion shortcut.

### Permissions, drops and cargo

Attribute each successful break to the controlling server player. Validate game mode, dimension, loaded chunk, interaction permission, world border and current protection hooks immediately before committing the break. `VehicleGunBlockDamage` already checks `level.mayInteract` and Fabric's `PlayerBlockBreakEvents.BEFORE`; use that as a starting point, not proof that every claims mod is covered. Validate the actual server's protection mod (including Sable if present) in a dedicated integration test before release.

Use one reusable break service with ordinary block loot and the appropriate server-owned virtual digging tool (shovel for soil, pickaxe of the configured tier for stone). Do not temporarily replace the driver's hand or grant Fortune/Silk Touch. Keep energy and drops tied to successful breaks. Check events once, revalidate the exact block state, remove it through one authoritative path, insert its loot into cargo once, and spawn only permitted overflow. Do not combine automatic vanilla drops with manual loot creation.

Default full-hold behavior is to pause clearing and explain why; an explicit server setting may allow leftover drops on the ground. For capacity checks involving loot, prepare one bounded loot result for that attempt, reserve capacity, and revalidate state before removal. Do not reroll loot every tick while waiting for space. Deduplicate target positions per tick and clear transient work on unload. Rejoining or reopening the cargo screen must never replay a completed break. Use the existing vehicle inventory save and menu synchronization for the hold.

## Server and network design

Keep simulation on the server. The client sends intent, never block positions, arbitrary lift values or harvested items. Reuse the existing drive input packet for movement. Add `BulldozerControlPayload(vehicleId, liftAxis, workHeld, brakeHeld, gradeToggleSequence)` for tool intent, with an enum/range-checked lift axis and monotonic edge sequence for the toggle. Do not reinterpret weapon fire/decoy fields as construction actions.

Only the current driver of that exact entity can control it. Process on the server thread, rate-limit inputs, clear holds after a short timeout, and reject NaNs or out-of-range values. Two people interacting must not race for the controls. Keep a single authoritative work queue per entity and a fair world-level work budget.

Synchronize lift, signed left/right travel phases, tool load, work state, grade state and desired hull presentation through a compact dozer-specific state. Use the existing position/velocity/yaw correction system for chassis movement. Send changes at a bounded rate, then interpolate remote presentation. The driver may predict motion and lift within the mechanical limits, but block removal waits for the server. Resuming visibility initializes previous/current angles equally, avoiding a snap from zero. Store lift, grade and cargo; do not persist held work, particles or queued block targets.

Bound terrain work independently of frame rate: initial limits of 96 candidate positions and four successful breaks per dozer tick, with a shared world cap of 16 successful breaks per tick, a small shared time budget and round-robin fairness. The hardness budget usually stays far below those caps. Batch surface dust and contact sound notifications; never send a packet or spawn debris for every cube in the model. Reuse normal block/chunk updates and verify distant crater/cut updates with Voxy Server Side. No chunk tickets are added for construction.

## Animation, visuals and sound

- Yellow paint with restrained wear, visibly polished rods, dark tread faces, a clean steel cutting edge and actual glass. Use the existing paint-mask system to keep tracks, glass, rods, lamps and the cutting face from being dyed. The atlas shares material tiles, so add material-aware masking or isolate paint UVs before enabling arbitrary repainting.
- Tracks travel from real displacement. During a pivot, the two tracks run opposite ways. Under a blocked blade, reduced advance and a restrained slip effect communicate traction; avoid tracks racing at transport speed while the hull is motionless.
- A hydraulic hiss while moving the blade, a soft end-stop clunk once per stop, a continuous diesel loop that loads up while cutting, slow track clatter and material-specific scrape/crunch sounds. One persistent loop per sound layer; no restarts on every packet. The cab view uses reduced exterior noise. Reverse warning is audible outside at a reasonable range.
- Sparse block-coloured dust and a few short-lived fragments at actual contacts. Blade steel stays readable; effects must not hide the cut. Emissive atlas is provided for lamps, tail lights, beacon and instruments. Gate these by power in a dozer-specific light layer; the existing generic glow layer would otherwise leave them always on.
- Add optional LambDynamicLights work-light pools using the existing integration approach, with low glare and bounded reach. The amber roof beacon flashes while powered/working. Keep first-person shake optional and small.
- Build LOD1/LOD2 before release: replace the individual shoes with simple bands at distance, retaining blade lift and cylinder alignment. Target around 100 and 40 cuboids. The revised 511-cuboid model is the near view. Author clips and rig names so LOD changes never reset lift or track phase.

### Grounded visual direction - revision 02

The supplied model now has a lower cab, raked windscreen/posts, a thin roof, smaller lamps and beacon, chamfered engine-hood shoulders, circular roller/flange profiles and a curved steel moldboard made from seven angled bands. Smoked glass and muted industrial ochre replace the strong blue glazing and saturated yellow. Low-contrast surface wear replaces the conspicuous speckling, and the wheel hubs use steel instead of large yellow squares. The cutting edge, linkages and ground contact remain in the same functional locations.

Maintain that approach as details are added: plausible panel thickness, mechanical mounting points, round running gear and restrained material contrast. Preserve the mod's model format and geometric style; avoid oversized roof slabs, bright plastic-looking surfaces, exaggerated lights and decorative moving parts that do not have a mechanical purpose. The cab eye locator has been lowered to `(0,38.5,11)` source units to match the new roof height. Recheck the camera in Minecraft during integration.

### Aircraft-style audio principles, scaled to a ground vehicle

Nine real-source/utility audio assets are now prepared under `sounds/vehicle/bulldozer/`, with credits in that folder. `prepare_audio.py` generates the mono Ogg files and validates decoded levels; `audio-contract.json` specifies the controller rules. `sounds.fragment.json` and `lang.fragment.json` are ready to merge when the vehicle controller is implemented. The offline preview can audition the sounds and transition between outside, cab and distant mixes without recreating its seven loop channels. This is asset/presentation work; in-game audio is not wired yet.

| Layer | What drives it | Maximum range target |
| --- | --- | --- |
| Diesel startup | One actual cold start; crossfade its last 0.8 seconds into the persistent engine | 128 blocks |
| Engine idle / load | Smoothed actual RPM and drivetrain/blade load; a blocked working blade can load the engine while hull speed is zero | 192 blocks |
| Cab engine | Centered listener-relative sound, filtered and quieter, with reduced exterior machinery | Occupant only |
| Distant engine | Low-passed recording, blended from the near engine over roughly 40-96 blocks | 192 blocks |
| Track clatter | Actual tread motion; both tracks during pivoting; silent when parked | 64 blocks |
| Hydraulic pump | Actual blade motion; silent when held against an end stop | 32 blocks |
| Valve release / soft stop | One motion-to-stop transition, not every held-key tick | 24 blocks |
| Reverse warning | Actual reverse movement, attenuated inside the cab | 64 blocks |

Mirror the useful principles of `JetAudio` and `HelicopterEngineAudio`: persistent mono positional sources outside, a centered cab mix inside, startup overlap, smoothed gain/pitch, bounded combined loudness, listener crossfades around 250 ms, a muted distant layer, short exterior-transient propagation delays and cleanup on unload/disconnect. Reuse or extract the shared lifecycle/mixing logic without pretending the dozer is an aircraft.

The dedicated controller owns the dozer's sound layers. Suppress the shared LAND `VehicleEntity.tickEngineSound` path for this vehicle while that controller is active, so a stock engine loop does not play underneath the new diesel. Preserve the truck and other land vehicles' existing sound paths. The prepared sound IDs use `vehicle.bulldozer.engine_idle` and related names; wire those deliberately instead of relying on a generic `.engine` fallback.

Engine speed and load must not snap with W/S input or restart a sample. Camera changes, stopped movement, blade movement and late observer arrival must never replay startup. At ground speeds Doppler is subtle; initially clamp the ratio to 0.97-1.03. Avoid jet flyby pitch swings, jet/rotor samples, oversized bass impacts and musical feedback. The reverse warning is a quiet electronic safety tone; other prepared layers come from actual diesel, tracked-machine and hydraulic recordings.

The initial controller uses ordinary tracked entities, with 192 blocks as a maximum while the entity is available. Lower server/client tracking distances may shorten that reach. Fade out when the entity is untracked and discard stale audio; do not leave a phantom engine at its last position. Do not copy the aircraft's 1024-block range, extend its far-aircraft network protocol, or force chunks to load for sound. If longer-range ground tracking is later needed, design and test it explicitly.

Add material-specific contact scraping/crunching from actual successful blade contacts, with sparse impact events and a bounded particle/sound budget. A denied or raised blade must not sound as though it is excavating. Startup and shutdown use the power state; dismounting without a cold start does not restart the diesel. The current shutdown design fades the existing engine; a recorded shutdown can be added if a suitable matching source is obtained.

Test with Sound Physics enabled: the sum of engine, load, hydraulics and tracks must remain below an intrusive wall of sound. Verify several nearby machines, first-person cab versus chase camera, observer turning, distance crossover, cold start, stop, reverse, held hydraulic end stops and disconnect. Treat final loudness and loop naturalness as a listening acceptance gate, in addition to numerical checks.

## Integration map

| Existing surface | Planned change |
| --- | --- |
| `init/ModEntities.java` | Register a dedicated `BulldozerEntity`; `ConfiguredVehicleEntity` is final, so do not subclass it |
| `vehicle/entity/base/VehicleEntity.java` | Add narrowly scoped land-controller hooks before movement and after actual movement; construction state/logic stays in separate classes |
| New `vehicle/entity/BulldozerEntity.java` | Compose `BulldozerController`, rig state, persistence and construction capabilities |
| New `vehicle/entity/base/BulldozerController.java` | Drive response, blade state, work eligibility, stop/parking behavior |
| New `vehicle/util/BulldozerRig.java` | Shared pose/locator mathematics and swept blade geometry, with no client imports |
| New `vehicle/terrain/BulldozerTerrainWork.java` | Candidate selection, exposure/grade/support checks, hardness, budgets and break service |
| `vehicle/network/`, `network/NetworkHandler.java`, `network/ClientNetworkHandler.java` | Typed tool intent, state registration, driver validation and timeout |
| `vehicle/client/VehicleInputHandler.java`, `client/KeyBindings.java` | Scoped controls and key-conflict handling |
| New `vehicle/client/render/BulldozerGeoModel.java`, `BulldozerRenderer.java` | Use `NamedVehicleGeoModel` and existing vehicle render infrastructure; apply the measured rig pose |
| `client/FabricClientBootstrap.java` | Register renderer and dozer presentation/audio tick |
| `vehicle/client/VehicleCameraHandler.java`, `overlay/VehicleHudOverlay.java` | Driver-eye locator, stable chase view and construction HUD |
| `data/jeg/vehicles/bulldozer.json`, `data/jeg/vehicle_assembly/bulldozer.json` | Track drive, one driver, cargo, OBB, energy and assembly recipe |
| `vehicle/recipe/VehicleAssemblyRecipeManager.java`, `lang/en_us.json`, `textures/vehicle_icon/bulldozer_icon.png` | Enable the selection entry, show its icon/name and enforce the 28 iron / 8 copper / 6 redstone cost |
| New `vehicle/client/audio/BulldozerAudio.java`, `sounds.json` | Persistent diesel/machinery layers, cab/distant mixes, real power/load events and complete sound cleanup |
| `Config.java`, block tags, language and sound assets | Server work limits, clear messages, break permissions, sound events and licensed recordings |

Preserve LAND behavior for the truck, BMP-2 and LAV-150. Existing movement helpers are private, so expose only the hooks actually needed; avoid copying the entire vehicle tick or turning construction into a giant special case inside `VehicleEntity`.

Initial content target: one enclosed driver seat, a medium cargo hold, ordinary vehicle charging and repair support, health around 200, light civilian armor, no weapons/decoys and no enemy-spawn conversion. Set the first-person camera from `driver_eye`; measure seat alignment in-game instead of assuming passenger feet equal the seat locator. Fit chassis/cab/track hitboxes to the exported model, and keep the tool volume separate. The assembly cost is fixed at 28 iron, 8 copper and 6 redstone for the initial implementation.

## Implementation order and gates

1. **Spawnable presentation prototype.** Register the entity, renderer, data and a test-only spawn route. Validate model orientation, camera, collision fit, texture/glow, daylight/night readability and the entire hydraulic range. Terrain destruction remains disabled until the collider matches the displayed blade at all four cardinal headings.
2. **Stable driving and controls.** Implement the bounded track controller, pivot, brake, support sampling, blade adjustment and parking. Test first/third person, slopes, slab/stair travel, tight walls, menus, focus loss and dismount. Repeat shared land-vehicle movement tests.
3. **Functional blade.** Add contact sweep, grade assist, hardness, protection-aware breaks, loot/cargo and energy. Deliver a reliable four-block clearing swath. Test all stop conditions and map the contact feedback to the exact work area.
4. **Multiplayer and persistence.** Verify dedicated server behavior, a remote observer, latency/corrections, driver handoff, unload/reload, reconnect and two dozers meeting at the same cut. Confirm cargo and terrain change exactly once.
5. **Presentation and performance pass.** Finish LOD, distance-driven tread animation, light gating, engine/hydraulic/surface audio, exhaust and dust. Profile several working machines with the pack's shaders, animation, chunk and protection mods at the actual server settings. Tune parameters from measured behavior.
6. **Release gate.** All tests below pass, relevant shared vehicle regressions pass, a person completes the manual driving/clearing course, and the pack/dedicated-server check passes. Then bump `yftm_build`, document limitations, build and release the same version for server and clients.

Blade angling/tilting, material pushing/placement, a rear ripper and automatic road grading are later additions. The first functional release includes lifting, stable travel/pivoting, progressive terrain clearing, grade assist, cargo, sound, multiplayer and crafting; those essentials are not postponed as cosmetic polish.

## Acceptance tests

Add focused Fabric client game tests plus an actual dedicated multiplayer check, following `CLAUDE.md`:

| Test | Pass condition |
| --- | --- |
| `BulldozerRig` | Rendered and computed cutting corners agree within 0.05 block across full lift, four headings and representative terrain pitch/roll; cylinder endpoints agree within 0.01 block |
| `BulldozerSelection` | Entry appears in All and Land, including pagination; correct icon/name/cost; missing ingredients disable assembly; success consumes exactly 28 iron, 8 copper and 6 redstone and produces one usable vehicle |
| `BulldozerDrive` | Measured speed/stop targets met; no drift after parking; stationary pivot bounded; no wall climbing or repeated bounce damage at cutting speed |
| `BulldozerBlade` | Four-wide soil strip clears only in contact; release and raised blade stop destruction; reverse and side pivot do not cut an unintended area; no breaking behind an unbreakable contact |
| `BulldozerGrade` | A flat lane stays flat, supporting track blocks remain intact, deliberate shallow cutting works with assist off, and a raised blade does not redefine the floor |
| `BulldozerDrops` | Correct tool-tier loot, cargo capacity pause, allowed overflow, no duplicate drops/energy charge after repeat input, reload or save/load |
| `BulldozerProtectedTerrain` | Spawn/world-border/claim denial, bedrock, fluids and block entities remain untouched; actual pack protection-mod behavior checked |
| `BulldozerMultiplayer` | Unauthorized passenger inputs rejected; remote lift/track motion smooth; held work stops after timeout/disconnect; no client-only terrain changes |
| `BulldozerPerformance` | Candidate/break budgets hold with several machines; unloaded chunks stay unloaded; Voxy updates the cut; particles, audio and frame time remain bounded |
| `BulldozerPresentation` | Tracks match travel and pivot, piston rods stay attached, lamps obey power, sound loops do not restart, cab remains readable with pack shaders |
| `BulldozerAudio` | No startup replay on visibility/camera changes; gain/pitch and near/far crossover smooth; tracks stop when parked; hydraulics stop at limits; reverse warning follows actual motion; cab mix centered; all channels clean up |

Manual course: a straight dirt bank, gravel/sand pile, mixed stone ridge, one-block stairs, slabs, a slope, a cliff edge, a wall beside the blade, water entering a cut, a protected build and a nearly full hold. Include stopping mid-cut, holding a blade end stop, reversing, parking on a slope and dismounting during work. The operator must be able to clear a level strip without fighting steering, bouncing or camera shake.

## Evidence already available

`generate.py` and `verify_preview.py` passed for revision 02: 87 bones, 511 cuboids, five clips, valid UVs/hierarchy, closed track paths, hydraulic stroke, both rendered rod endpoints at -4/0/12/24 degrees, upright blade compensation, lift controls, counter-rotating tracks and no preview JavaScript errors. The native selection icon is exported from the geometry. Nine Ogg assets decode in the browser; seven persistent preview channels survive listener and load changes without recreation and are stopped on mute. The images show the revised geometry at grade height and raised, plus a side view.

The later runtime implementation and game-test evidence are recorded separately in [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md). Broader manual and stress-test targets above remain useful release gates.
