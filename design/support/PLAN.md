# FSV-6 field support vehicle: plan

Status: built 2026-11-15, phases S1–S7 (see [README](README.md) and [implementation status](IMPLEMENTATION_STATUS.md)).

**Decisions (user, 2026-11-15):**
- walk-in interior: yes
- beacons: both the Minecraft beacon and amber warning beacons
- modules: a fixed, tag-based list
- height: 3.4 blocks is acceptable
- name: FSV-6

**Where the build differs from this plan, and why:**
- **Seats:** the cab has two seats and the van has no jump seats. Packed, the slide-outs fill the van.
- **Floor:** one block up (not 1.25), so the real bunk beds and the walk-in shell sit on whole blocks.
- **Height:** about 3.1 blocks packed, under the plan's 3.4.
- **Room layout:**
  - The bays are in the slide-outs (rows 2–4) and the bunks are at the front of each slide-out, lengthwise.
  - The only way in is the rear ramp; there is no side door.
- **No handbrake key:** stopping is enough to deploy.
- **No lights key:** lights come on at night, as on the other vehicles.
- **Tow bar not drawn:** the towed vehicle follows the pintle, but the bar between them isn't modelled yet.
- **Not as vanilla:** an anvil here does not chip, and an enchanting table offers only the levels without bookshelves.
- **Beacon effects:** apply only with the vehicle deployed and the mast up.

It replaces the truck ([fleet plan](../FLEET_PLAN.md), section 1). Method:
[vehicle-development-guide.md](../../docs/vehicle-development-guide.md). Worked example: the BD-40 bulldozer.

## Purpose

- **What the player uses it for:** a base that drives. It carries the block functions a camp needs: storage,
  crafting, smelting, beds, light, a beacon, and charging and repair for other vehicles. They work on the move, and
  when it parks it unfolds into a room the crew can walk into.
- **Success looks like:** in five minutes a crew can:
  - load it at home and drive 2 km to a mining site, smelting ore in its furnace on the way
  - park and deploy it in about 8 seconds
  - walk in to craft, store the haul and recharge a BMP-2 parked beside it
  - under the floodlights and beacon, sleep through the night in its bunks and respawn at it after a death
  - pack it up and leave, with everything still inside

## Real-world reference

- **Class:** a 6×6 five-tonne tactical truck with an expansible van body, the kind used as a mobile command post,
  workshop or field clinic (FMTV M1087 expansible van class). On those, the van's side walls slide out on
  hydraulics to roughly double the floor area, the body stands on levelling jacks, and a generator powers the
  shelter. Command-post variants add an awning, a telescopic light mast and rear steps.
- **Proportions (1:1, a block is a metre):**
  - **Packed:** about 8.0 × 2.5 × 3.4 blocks (length × width × height). The cab is 2.2 long, and the van body is
    5.4 long with its floor 1.25 above the ground.
  - **Deployed:** 4.5 wide. Each side slides out 1 block, and the floor inside is 5 × 4.3 with 2 blocks of headroom.
  - **Awning:** 2 blocks beyond the left side.
  - **Steps:** 1.5 blocks behind the rear.
- **Operator position:** an enclosed cab with driver and co-driver; the occupants are visible through the glass.
  Four jump seats in the van can be used while driving. The driver needs to see the mirrors and the front corners.
- **Moving parts and what each does** (all functional, none cosmetic):
  - **Four stabiliser jacks:** level the body and take the load.
  - **Two slide-out sides:** carry the wall bays outward.
  - **Rear door:** folds down into steps.
  - **Side door:** the awning-side entry.
  - **Awning:** shade and a lit work area.
  - **Telescopic light mast:** floodlights.
  - **Roof hatch with beacon mount:** the beacon's beam.
  - **Fold-down bunks:** beds.
  - **Amber beacons on the cab roof:** a working light.
  - **Steering wheel, front-axle steer, wheels at their real rolling radius:** driving.
- **Real top speed and handling:** about 90 km/h on road, heavy and soft-sprung. It coasts when the throttle is
  released and stops on the brake, as the truck does now.
- **Start-up and shut-down:** the diesel cranks and catches. When it deploys, the engine stops and a generator
  takes over the electrical load (an audible change from diesel to generator hum).

## Fitting system (the core of the request)

Players fit blocks into **bays**. Each fitted block is a **module** that does what that block does. Modules are
data on the vehicle, not blocks in the world. That is what lets them keep working while it drives, survive the
crowbar packing the vehicle into an item, and stay safe from the world.

### Bays

| Bay | Count | Where | Takes |
| --- | --- | --- | --- |
| Wall bays | 6 (3 per side) | on the slide-out walls | any 1-block module |
| Bunks | 2 | across the front of the van, stacked | beds |
| Lockers | 2 | under the body, opened from outside | storage modules only |
| Roof mount | 1 | roof hatch | beacon |
| Mineral rack | 1 | below the roof mount | iron, gold, emerald, diamond and netherite blocks (the beacon's pyramid) |
| Light fittings | fixed | interior, mast, cab roof | lanterns, sea lanterns, froglights, glowstone, redstone lamps |

There are two ways to fit and use modules:

- **Fitting:** a Fit-out tab in the vehicle's screen shows the bay layout. Drop a block item into a bay to fit it,
  and take it out to remove it. Removing a storage module that still holds items is refused, as breaking a full
  shulker would not be.
- **Using:**
  - **Driving:** any occupant opens the support panel (the vehicle screen), which has one tab per module.
  - **Deployed:** players walk in and right-click the module itself.

### Modules

| Module (block) | What it does | Moving | Deployed |
| --- | --- | --- | --- |
| Chest, barrel | +27 slots each | yes | yes |
| Ender chest | the player's own ender inventory | yes | yes |
| Crafting table | 3×3 crafting | yes | yes |
| Furnace, smoker, blast furnace | smelting with fuel, as vanilla; keeps cooking while driving and while nobody looks | yes | yes |
| Anvil | vanilla anvil (it chips with use, as vanilla), and repairs vehicles parked within 8 blocks using iron ingots from the hold | items only | yes |
| Vehicle charging station (JEG) | recharges vehicles within 8 blocks from the support vehicle's battery, and rearms them with ammo from the hold (the old truck's resupply job) | no | yes |
| Bed | a bunk; sleep and respawn (see Beds) | no | yes |
| Beacon | vanilla effects, range and payment. Its level comes from the mineral rack (9, 34, 83 or 164 blocks for levels 1–4) | no (stowed) | yes, with the mast up |
| Lights | interior light while moving (client), real block light while deployed | yes | yes |
| Phase S6: stonecutter, smithing table, grindstone, loom, cartography table, enchanting table (the bookshelf count comes from bookshelves in a storage module), brewing stand, jukebox | as vanilla | yes | yes |

- **Allowed blocks are data:** `data/jeg/support_modules/*.json` maps a block to a module type and its bays, so the
  pack can add more. Anything not listed is refused: modded machines, shulker boxes as modules, spawners, TNT.
- **Menus:** each module's screen is the vanilla menu, with `stillValid` bound to the vehicle and distance rather
  than to a block. That is how portable crafting tables are usually done.
- **Rendering:** each module is drawn with its own block model in its bay, scaled to fit, so a crafting table looks
  like one. A lit furnace glows, a bed is a bed, and the beacon has its beam.

### Beds

The bunk is the one module that becomes a real block. On deploy, a vanilla bed of the fitted colour is placed in the
bunk position; on pack-up it is removed.

- **Why a real block:** sleeping, skipping the night, the phantom timer, the "monsters nearby" rule and Undead
  Nights' `hordeNightsDisableSleeping = true` then all work exactly as they do for any bed, with no sleep mixins.
- **Respawn:** sleeping in a bunk sets the player's spawn as a bed does. JEG records that bunk against the vehicle.
  If the bed is gone because the vehicle packed up or moved, the player respawns beside the vehicle, wherever it now
  is. If the vehicle is destroyed, they fall back as for a missing bed.

### Beacons and lights

"Beacons" is read two ways, and both are included (open question 2):

- **The Minecraft beacon:** a module, as above.
- **Amber rotating beacons on the cab roof:** a light fitting, switched with the lights.

**Light:**
- **Moving:** visual lamps through `AircraftLights`, plus LambDynamicLights entity light on the client (it is in
  the pack).
- **Deployed:** real light blocks inside the van, under the awning and from the mast floodlights to about 8 blocks.
  That stops mobs spawning in the camp. They are temporary light blocks of the kind JEG already uses for muzzle
  flashes (`DynamicLightBlock`), removed on pack-up.

## Deployment

The sequence is driven by one synced `deployProgress` value. The renderer poses the bones from it; there are no
canned animations. It can be reversed mid-way.

| Stage | Time | What happens | Sound |
| --- | --- | --- | --- |
| Checks | — | Stopped, handbrake on, slope ≤ 10°, heading within 8° of a cardinal, and the deployed footprint clear (sides, awning, steps). The HUD names what is in the way, for example "Left side blocked: stone at 120 64 −33" | — |
| 1. Jacks | 0–1.5 s | Four jacks extend and level the body. The vehicle eases onto the block grid and the cardinal heading (at most 0.5 block and 8°, hidden in the lift). Wheels unload | hydraulic pump, jack thunk |
| 2. Slide-outs | 1.5–4.5 s | Both sides slide out 1 block, carrying the wall bays. Entities in the way are pushed clear | slide-out motor |
| 3. Doors and awning | 4.5–6 s | The rear door folds into steps, the side door opens and the awning unrolls | door latches, canvas |
| 4. Mast and interior | 6–8 s | The light mast telescopes up, the beacon rises through the hatch, the bunks fold down and the lights come on. The engine stops and the generator starts | mast motor, generator start |
| Deployed | — | The walk-in shell and the bunk beds are placed, light blocks are set, and driving is locked | generator loop |

**Pack-up** reverses the sequence. It is refused while anyone is inside ("2 players inside") or a storage module's
screen is open.

**Key:** hold R for 1 second to deploy or pack up. R is free on unarmed vehicles, and the hold stops it happening
by accident. The deploy state shows in the HUD panel.

### The walk-in shell

When deployed, JEG places a `jeg:vehicle_shell` block in each cell of the floor, walls, roof and steps, so players
can walk inside. Entity collision can't make a hollow, rotated room, which is why the vehicle snaps to the grid at
stage 1.

The shell blocks are:
- invisible (the vehicle model draws the room)
- unbreakable by players, mobs (Zombie Break & Build) and explosions; blast damage passes to the vehicle
- never dropped, and never movable by pistons, Create contraptions or Carry On (`PushReaction.BLOCK`, tagged
  `c:relocation_not_supported`)

Each shell cell knows its vehicle and, at a bay, its module. Right-clicking a cell opens that module, which solves
picking from inside the vehicle's own box.

**Safety:**
- A saved registry (`SavedData`) lists every deployed vehicle with its cells.
- A shell block whose vehicle no longer exists removes itself when it loads or ticks: after the vehicle was killed,
  `/kill`ed, crowbarred or lost in a crash.
- The vehicle refuses to deploy into a claim it can't build in. It fires Fabric's block place events, so protection
  mods apply.

The shell, light blocks and bunks are the only world changes. Everything else stays data on the vehicle.

## Controls (same family as the truck and LAV-150)

| Key | Action |
| --- | --- |
| W / S | Throttle forward / reverse; it coasts when released |
| A / D | Steer |
| Left Ctrl | Brake |
| Space | Handbrake (needed to deploy) |
| R (hold 1 s) | Deploy / pack up |
| Lights key (as the aircraft) | Headlights, amber beacons; deployed: mast floodlights |
| Left mouse button | Horn |
| E | Support panel: hold, Fit-out, and one tab per module |
| Left Alt | Dismount |

## Vehicle data

- **Seats:**
  - 0: driver, cab left, enclosed, visible, yaw ±100°
  - 1: co-driver, cab right, enclosed, visible
  - 2–5: van jump seats, enclosed and visible through the open doors when deployed
- **Engine:** start from the truck's `wheel` block (`acceleration` 0.012, `max_forward_speed` 0.98 ≈ 58 km/h).
  Raise the top speed toward about 80 km/h and slow the acceleration for the weight. Driving is locked when
  deployed.
- **Energy:** the existing 5,000,000 battery. The diesel charges it while driving and the generator while deployed
  (it burns fuel from a fuel slot, coal or charcoal, the same as a furnace). It powers the lights, beacon and
  charging station.
- **Hold:** keeps the truck's 102 slots ("huge") as the cargo hold, so existing trucks lose nothing. Storage modules
  add to it.
- **Armed:** no. No `turret`, so seats can look around.
- **Id:** stays `jeg:truck` with the new display name "FSV-6 Field Support Vehicle", so the trucks already on the
  server load as packed FSV-6s with their holds and paint. A migration test proves it.

## Hitbox

Generated from the model by the generator, in two sets chosen by deploy state (shared code: `"obb_deployed"`).
Packed:

| OBB part | Covers |
| --- | --- |
| `body` | cab |
| `body` | van body |
| `body` | chassis rails and fuel tank |
| `main_engine` | engine under the cab |
| `wheel_left` / `wheel_right` | each axle's wheels |
| `module` | each wall-bay row (hits there damage fitted modules first) |

- **Deployed:** the slide-outs, awning posts and mast become their own boxes. The shell blocks handle walking
  inside, and entity push is off for the van body while deployed.
- **Entity `sized`:** 2.5 × 3.4 packed. Deployed, the entity box stays the hull's, as helicopters do
  (`usesHullBlockCollisionBox`).
- **Must fit:** a 3-wide gap packed. At 3.4 blocks tall it won't pass under 3-block tree canopies, like the real
  truck (open question 5).

## Behaviour summary

- **Server first:** module ticking (furnaces, beacon pulses, charging) runs on the server, budgeted per tick.
- **Damage:**
  - Hits on a `module` part can knock a module out (a smashed furnace stops cooking until repaired with the
    anvil).
  - The wreck drops every module and its contents as a broken chest would, and removes the shell, beds and lights.
- **Persistence:** modules, their contents, furnace progress, beacon effects and deploy state save with the entity,
  survive chunk unloads and travel inside the crowbar's vehicle container item.

## Sound

All recordings CC0, with `CREDITS.md`. Loops are static, on the shared ground-vehicle audio of fleet plan
Phase 0.1.

| Layer | `_distant` / interior | Loudness | Driven by | Loop / one-shot |
| --- | --- | --- | --- | --- |
| diesel idle and load (one take) | both | 1.0–1.8 | RPM | loop |
| tyres and road | — | 0.6 | speed | loop |
| hydraulic pump, jacks | — | 0.8 | deploy stage 1 | loop and one-shots |
| slide-out motor | — | 0.7 | stage 2 | loop |
| doors, awning, mast | — | 0.6 | stages 3–4 | one-shots |
| generator | distant | 0.9 | deployed | loop |
| horn | distant | 2.0 | LMB | one-shot |
| reversing alarm | — | 0.8 | reverse | loop |

## Pack interplay

| Mod | Its config on the server | How the vehicle cooperates | Test |
| --- | --- | --- | --- |
| Undead Nights | `hordeNightsDisableSleeping = true` | bunks are real beds, so its rule applies unchanged | `SupportBunk` on a horde night |
| Zombie Break & Build | zombies break blocks | shell blocks are unbreakable; damage goes to the vehicle | `SupportShellSafety` |
| Carry On | JEG vehicles are not in `forbiddenEntities`; it can't lift past width 1.5 | tag JEG vehicle types `c:capturing_not_supported`, and shell blocks `c:relocation_not_supported` | `SupportShellSafety` |
| LambDynamicLights | client | entity light while moving | screenshot at night |
| Inventory Profiles Next | client sorting | sorting stays inside one module's inventory | manual check |
| Create (contraptions), pistons | — | the shell refuses to move | `SupportShellSafety` |
| Sound Physics Remastered | reverb and occlusion | the interior is open air when deployed; check it isn't muffled oddly | listen (ask the user) |
| Iris / Complementary, Voxy | client | night screenshots inside and out with shaders | pack run |

## Tests (each a focused class)

| Class | Checks |
| --- | --- |
| `SupportDeploy` | each refusal (moving, slope, heading, blocked side named), the stages in order, snap within limits, reverse mid-way, pack-up refused with a player inside |
| `SupportShellSafety` | cells placed and removed exactly; orphan cleanup after the vehicle is killed, crowbarred or unloaded; explosions; zombies; pistons; Carry On |
| `SupportModules` | fit and unfit each type; crafting a recipe; a furnace cooking while driving 500 blocks; storage surviving reload and the crowbar |
| `SupportBunk` | sleep skips the night; refused on a horde night with Undead Nights; respawn beside the vehicle after it moved 300 blocks |
| `SupportBeacon` | level from the mineral rack; effects only in range and only deployed; beam drawn |
| `SupportResupply` | charging and rearming a parked BMP-2; anvil repair from iron |
| `SupportLights` | light levels when deployed; no mob spawns in the lit camp |
| `SupportMigration` | an old truck's NBT with a full hold loads as an FSV-6 with everything |
| `SupportHitbox` | both sets within the model bounds; shots hit parts and miss beside them |
| shared | ground audio and far sound at this id; `VehiclePaint`, `VehicleSneakDismount`, `VehicleInputCompat` |
| sandbox | `SupportSandboxSetup`: a camp site with a slope, a blocked spot, a BMP-2 to recharge, and night |

Every run uses `-PwithPack`, plus `-PpackExclude=undeadnights` for the bunk tests.

## Phases

| Phase | Contents | Size |
| --- | --- | --- |
| S1 | Generator model at 1:1 with every moving part, `rig.json`, hitbox sets, deploy state machine and checks, HUD, deploy sounds, migration from the truck | L |
| S2 | The walk-in shell, its registry and cleanup, module picking through shell cells, light blocks | L |
| S3 | Module framework (Fit-out tab, data files, rendering of block models, panel tabs) with storage, ender chest, crafting table, furnaces and lights | L |
| S4 | Bunks as real beds, respawn at the vehicle, Undead Nights interplay | M |
| S5 | Beacon and mineral rack; charging station (resupply) and anvil (vehicle repair) | M |
| S6 | Further vanilla modules | M |
| S7 | Tow hitch for the howitzer and wrecks (from the fleet plan) | M |

S1 to S3 make it a working mobile base; each later phase stands on its own and can ship separately.

## Open questions for the user

1. **Walk-in interior.** It needs the vehicle to snap to the block grid and a cardinal heading when it deploys (at
   most half a block and 8°, eased during the jack lift). Recommended: yes. The alternative is exterior bays only:
   roll-up shutters you click from outside, with no snap, no shell blocks and less to go wrong, but no walking in.
2. **"Beacons":** the Minecraft beacon, amber warning beacons, or both? The plan does both.
3. **What works while moving:** recommended as in the module table (storage, crafting, furnaces, ender chest,
   lights). Beds, beacon and charging only work deployed.
4. **Fixed module list or any block?** Recommended: a data-driven list, so nothing unexpected (modded machines,
   block entities with complex state) is carried around.
5. **Height:** 3.4 blocks, as the real van, so it won't pass under 3-block tree canopies. Acceptable?
6. **Name:** "FSV-6 Field Support Vehicle", keeping the `jeg:truck` id. Fine?
