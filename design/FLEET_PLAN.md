# Fleet plan: bringing every vehicle up to the brief

Status: approved 2026-11-15, with the open questions answered (see the end). The truck is replaced by the field
support vehicle, planned in [support/PLAN.md](support/PLAN.md). Go-ahead given 2026-11-16, scheduled with the SBW
port. Done so far: **0.1 and 0.2** for the BMP-2, LAV-150, PLZ-05 and YX-100 (`GroundVehicleAudio`, far tracks; no real
start, stop or cab recordings yet), **0.3** (every ground vehicle gun on the long-range path and heard through reports, 2026-11-17); **0.4**
(turret-following hitbox parts, first used by the Mk42); **0.8** for the BMP-2, PLZ-05, YX-100 and LAV-150
(headlamps with the beam at night, brake and reversing lamps; convoy and blackout lamps, work lights and cab glow still
to do); **0.9** (`EnemyVehicleRaidClientGameTest`,
2026-11-17); **section 2, BMP-2 refit pass 1** (1:1 model, track driver, data; [bmp2/IMPLEMENTATION_STATUS.md](bmp2/IMPLEMENTATION_STATUS.md)); **section 3, LAV-150 refit pass 1** (1:1
model, the new shared wheel driver with steering, data, lights; [lav150/IMPLEMENTATION_STATUS.md](lav150/IMPLEMENTATION_STATUS.md),
2026-11-17); **section 4, speedboat refit pass 1** (bow gun the gunner takes from the driver, planing, beaching, the
hull's data in `"boat"`; [speedboat/IMPLEMENTATION_STATUS.md](speedboat/IMPLEMENTATION_STATUS.md), 2026-11-17); **section 5, HPJ-11 refit pass 1**
(the 30 mm CIWS with proximity-fuzed rounds, manual or automatic; [hpj11/IMPLEMENTATION_STATUS.md](hpj11/IMPLEMENTATION_STATUS.md));
**section 6, the sentry pass 1** (a new tripod remote weapon station; the laser tower retired;
[sentry/IMPLEMENTATION_STATUS.md](sentry/IMPLEMENTATION_STATUS.md)); **section 8, the bush plane pass 1** (a new 1:1
Super Cub-class model on the TOM-6's id, unarmed, flaps and a propeller's thrust in the new `"aircraft"` data block;
[bushplane/IMPLEMENTATION_STATUS.md](bushplane/IMPLEMENTATION_STATUS.md)); **section 9**, the light combat vehicle retired (all
2026-11-17; the HPJ-11 and the sentry share `AutoDefenceEntity`). **Section 7, the howitzer**, built
out of order with the SBW port's towed guns (2026-11-16, [howitzer/BRIEF.md](howitzer/BRIEF.md)): user decisions were a
new `jeg:howitzer` with the waveforce tower retired, a spyglass observer with manual laying, three charges to about
4 km, and deploy before firing.

The A-10, AH-6, Mi-28 and BD-40 bulldozer meet the standard in
[vehicle-development-guide.md](../docs/vehicle-development-guide.md). This plan covers every other vehicle in
`data/jeg/vehicles/`. For each one it sets a task, a real-world reference and a model decision, lists the gaps
against the brief, and orders the work. Each vehicle then gets its own `design/<vehicle>/BRIEF.md` from
[the template](../docs/vehicle-brief-template.md) before any code changes; the sections below are the starting point
for those briefs.

## Where the fleet stands

| Vehicle | Assemblable on the server | Task today | Verdict | Model |
| --- | --- | --- | --- | --- |
| Truck | yes | none: unarmed, 102-slot hold, two seats | **Replace:** FSV-6 field support vehicle, a mobile base that deploys ([plan](support/PLAN.md)) | **Remodel** (expansible van body; generator) |
| BMP-2 | yes; also spawns as an enemy from day 80 | armoured transport with 30 mm and ATGM | **Keep the role, sharpen it:** assault and anti-armour | Refit and rescale |
| LAV-150 | yes; also spawns as an enemy from day 80 | wheeled APC with 20 mm | **Keep the role, sharpen it:** fast scout and convoy escort | Refit |
| Speedboat | yes | boat with an MG the driver fires | **Sharpen:** fast squad transport and river patrol | Refit |
| HPJ-11 | no | static mount firing a hand minigun and a rocket launcher | **Repurpose:** base air and missile defence (CIWS) | Refit |
| Laser tower | no | sci-fi turret firing echo shards | **Repurpose:** perimeter sentry against hordes | **Remodel** |
| Waveforce tower | no | sci-fi cannon firing sculk catalysts | **Repurpose:** towed light howitzer (or retire; open question 1) | **Remodel** |
| TOM-6 | no | tiny plane with a melon bomb, hand LMG and rocket launcher | **Repurpose:** STOL bush plane for travel and scouting | **Remodel** |
| Light combat vehicle | no | pre-port placeholder with hand guns | **Retire** | n/a |
| `test_*` (5) | no | fixtures for tests | Leave as fixtures; out of scope | n/a |

Why these tasks: the server is a co-op PvE world (10 players) with zombie hordes at night (Undead Nights, Zombie
Break & Build), JEG gunner raids that bring enemy LAV-150s, BMP-2s, AH-6s and Mi-28s from day 80
(`enemyVehicleStartDay = 80` in `jeg-server.toml`), and Create's trains, block-built ships and Big Cannons. The
fleet already has close air support (A-10), attack and transport helicopters, and land clearing (bulldozer). What is
missing is keeping vehicles supplied in the field, defending a base from the air and from hordes, fast water
transport, and cheap long-range travel. Each repurposed vehicle fills one of those gaps instead of duplicating a
vehicle or a pack mod.

## What every remaining vehicle lacks today

Found by reading the vehicle JSON, `VehicleEntity` and the client audio code. These are shared gaps, so Phase 0
fixes them once.

1. **Engine sound is far below the aircraft standard.** Land vehicles and boats play their single engine recording
   from the server as a one-shot every 20 ticks (`VehicleEntity.tickEngineSound`). That means Minecraft's linear
   attenuation, silence at idle, no start-up, no `_distant` version, no Doppler, no cockpit sound and no far
   tracks. None of them has a `CREDITS.md`; the recordings came with the Superb Warfare port.
2. **Their guns stop at entity range.** Only helicopter and jet rounds get the long-range path
   (`bullet.setHelicopterGunRound()` in `VehicleEntity`). The BMP-2's 30 mm, the LAV-150's 20 mm, the coax guns and
   the speedboat's MG don't, so the yftm.13 lesson applies to them. The BMP-2's ATGM already uses `FarFlight`.
   Gun sounds fall back to hand-gun sounds with linear attenuation for the 20 mm and coax.
3. **Turret hitboxes sit on the ground.** OBBs are fixed in hull coordinates. The BMP-2's `turret` box spans
   y 0.13–0.81 and the LAV-150's spans y 0–0.73, inside the hull floor, so the real turret on the roof is never the
   `turret` part and its armour entry is unused. No box follows turret yaw.
4. **Hitboxes aren't fitted.** None of these vehicles returns `preciseHitbox()` (only the bulldozer does), so
   bullets test the whole entity box plus 0.3. The HPJ-11's two boxes cover about a quarter of the model (barrels
   and radars are unhittable); the Waveforce tower's barrel is uncovered; the TOM-6's box is larger than the model;
   the speedboat's `main_engine` box is the cabin.
5. **Scale is inconsistent.** The A-10 and the bulldozer are 1:1 (a block is a metre). The BMP-2's hull is about
   1.37× real size (9.1 × 4.4 blocks against 6.7 × 3.15 m). The LAV-150 is about 1.16× and the truck is 1.22× long
   but 1:1 wide.
6. **No lights, no damage states, no cockpit instruments.** `AircraftLights` has sets only for the bulldozer, A-10,
   AH-6 and Mi-28. There is no land or sea wreck, and no paint mask outside the AH-6, Mi-28 and bulldozer.
7. **The models come from the Superb Warfare port:** hand-authored, bones named `bone37` or in Chinese, no
   `rig.json`, and canned idle animations (`VEHICLE_IDLE_ANIMATIONS`) instead of synced state. Their licence should
   be checked before any release that changes them. The standard asks for our own generated models and CC0 sound
   anyway.
8. **No tests of their own.** The truck, LAV-150 and BMP-2 appear only as targets in the A-10 and aircraft sandboxes
   and in the assembling screen test. The other vehicles have none. Nothing tests the enemy vehicle AI, which
   depends on the LAV-150's and BMP-2's seats and turret limits (`EnemyVehicleController`).
9. **Vehicle-specific behaviour is hard-coded by id** in `VehicleEntity`: `usesCustomObbEntityCollision`,
   `RADAR_WARNING_VEHICLES`, `isTruckVehicle`, the speedboat constants and the idle animation map. Each new vehicle
   currently means editing a 7,600-line class.

## Phase 0: shared foundations (do once, before any vehicle)

Each item lands with its regression test and a note in the guide, per CLAUDE.md.

| # | Work | Reuses | Test | Size |
| --- | --- | --- | --- | --- |
| 0.1 | **Ground vehicle audio.** Generalise `BulldozerAudio` into a `GroundVehicleAudio` that every land vehicle and boat uses: static loops, idle/load crossfade from an RPM model, start-up and shut-down, `AircraftSoundRange` loudness, `_distant` crossfade, Doppler, centred first-person sound for open seats, interior recordings for closed cabs, loops that survive view switches. Retire the server one-shot for those types. | `BulldozerAudio`, `DynamicRelativeSound`, `VehicleDoppler` | `GroundVehicleAudio` (OpenAL per view, as `BulldozerAudio` does) | L |
| 0.2 | **Far tracks for ground vehicles.** Move `sendsFarSoundTracks`/`farSoundLoudness`/`farSoundSignal` from per-class overrides into vehicle JSON (`"far_sound": {...}`) so configured vehicles get them. | `BulldozerEntity` overrides, `FarAircraftAudio` | `GroundVehicleFarSound` (parameterised copy of `BulldozerFarSound`) | M |
| 0.3 | **Long-range rounds for every vehicle gun.** Rename `setHelicopterGunRound` to `setLongRangeRound` and apply it to all vehicle guns. Give turret guns `VehicleFireSoundInstance` with a loudness instead of hand-gun sounds. | `FarFlight`, `VehicleFireSoundInstance` | Extend `AircraftLongRange` to the 30 mm, 20 mm, coax and boat MG | M |
| 0.4 | **Turret-relative hitbox parts.** Add `"follows": "turret"` to an OBB box so it rotates with turret yaw (and `"follows": "barrel"` for pitch). Move `preciseHitbox` into data (`"precise_hitbox": true`). | `clipHitbox`, `estimateHitPart` | `TurretHitbox`: shots at the turret hit `turret` at 0°, 90° and 180° yaw | M |
| 0.5 | **Data flags instead of id switches.** `custom_obb_entity_collision`, `radar_warning`, `idle_animation` and the speedboat tuning become vehicle JSON fields. Defaults reproduce today's behaviour exactly. | — | Existing `VehicleInputCompat`, `VehiclePaint`, helicopter and bulldozer tests | S |
| 0.6 | **Shared generator library.** Split `design/bulldozer/generate.py` into `design/_lib/vehiclegen/` (cubes, cylinders, `native_geometry`, LODs, atlas, paint mask, `rig.json`, bbmodel, preview, validation) so each remodel is a small script. Add `extract_rig.py`, which writes `rig.json` (bounds per bone, pivots) from an existing geo file, so refitted models get the same hitbox checks. | bulldozer generator | Regenerate the bulldozer and check it is byte-identical | M |
| 0.7 | **Land and sea damage states.** Smoke, sparks and fire as health falls; a burning wreck that settles (land) or sinks (boat); part damage that stops the system it hits (tracks or wheels: no drive; engine: no power; turret: no traverse). | `HeliWreck` | `GroundWreck` | M |
| 0.8 | **Light sets for ground vehicles.** Headlights, tail and brake lights, convoy and blackout lamps, a work light, and instrument glow for closed cabs. | `AircraftLights`, `CockpitPanel` | Extend `AircraftNightLights` | S |
| 0.9 | **Enemy AI regression test.** Spawn an enemy LAV-150 and BMP-2 at a target; assert they approach, aim within limits and fire. Every later change to those two runs it. | `EnemyVehicleController` | `EnemyVehicleRaid` | S |

## Vehicles

Order of work: truck, BMP-2 and LAV-150, speedboat, HPJ-11, sentry, bush plane, howitzer. The truck goes first:
the user asked for it, and the howitzer and fleet resupply depend on it. The BMP-2 and LAV-150 come next because
players drive them and the server spawns them as enemies.

### 1. Truck → FSV-6 field support vehicle

**Superseded by [support/PLAN.md](support/PLAN.md).** The user asked for a support vehicle that deploys and can be
fitted with beacons, storage, crafting tables, lights, beds and so on: a mobile base. The resupply, repair and tow
jobs below carry over into that plan as modules (charging station, anvil) and its towing phase. This section stays
for the record.

- **Task:** keep the fleet in the fight away from base. It carries ammunition, energy and repair stock to where the
  vehicles are, and tows what can't drive itself.
- **Success in five minutes:** load at base, drive 1 km to a BMP-2 and an A-10 at a forward strip, drop the
  tailgate, and rearm, recharge and repair both without either going home. Then hitch a wrecked LAV-150 and tow it
  back.
- **Real reference:** a 6×6 five-tonne tactical cargo truck (FMTV M1083 or Ural-4320 class). About 7.4 × 2.5 × 2.9
  m, 90 km/h on road. A turbo-diesel that cranks and catches, and coasts when the throttle is released, unlike the
  bulldozer.
- **Model: remodel with the generator.** The role needs a new rear body, and the current model is 1.22× long. Build
  the cab and chassis at 1:1:
  - Enclosed cab with dash, a steering wheel that turns with steering, and mirrors aimed with the bulldozer's
    eye-point method.
  - Three axles with front-axle steer bones, wheels at their real rolling radius (about 0.54 block), and a spare
    wheel.
  - **Logistics body:** a drop-side bed with hoops and canvas (the canvas rolls up as a state), and a fold-down
    tailgate on its own bone. The visible load fills with the hold: ammo crates, jerry cans and battery packs appear
    in rack bones as stock rises.
  - Rear tow pintle and light bar; headlights, blackout lamps, tail and brake lights.
- **Behaviour:**
  - **Resupply** while parked with the tailgate down. Vehicles within about 8 blocks of the tailgate draw from the
    truck each tick at a capped rate:
    - ammo for their weapons from the hold
    - energy from the truck's battery (reuse `VehicleChargingStationBlockEntity`'s charge logic)
    - repair from iron ingots in the hold, until they are at full health
  - Both HUDs show what is flowing and what is running short. Players can still open the hold by hand.
  - **Tow:** a hitch point on the pintle. A towed vehicle follows on a rigid bar, its own brakes off. The truck
    tows the howitzer (section 7) and disabled vehicles. Towing is new code: one protected hook in the base class,
    as the bulldozer's `tickSpecialLandMovement` was.
  - Mark the resupply stock as a tool bay (`toolSlotStart`/`toolSlotCount`) so pickups never displace it.
  - Remove `isTruckVehicle()`; it exists only to hide the pistol the Superb Warfare truck had.
- **Sound:** a CC0 medium truck diesel (idle and load from one take), an interior cab recording, air brakes, tailgate
  clank, a reversing alarm and the distant version. Loudness well under the bulldozer at idle, similar under load.
- **Tests:** `TruckResupply` (rates, range, tailgate states, tool bay); `TruckTow` (hitch, follow on a slope,
  release); `TruckHitbox`; the shared audio and far-sound tests at the truck's id; a sandbox with a forward strip, a
  damaged BMP-2 and A-10, and a wreck to tow.
- **Pack interplay:** Carry On (check it can't pick up the truck or a hitched vehicle), Inventory Profiles Next
  (sorting must leave the tool bay alone), Chunk Loaders (a parked supply point in an unloaded chunk).

### 2. BMP-2 → assault and anti-armour

- **Task:** carry a squad into a fight and kill armour. It is the only player vehicle with a long-range anti-tank
  missile, and the enemy brings LAV-150s and BMP-2s from day 80.
- **Success in five minutes:** drive a squad of six across rough ground and a river, destroy an enemy LAV-150 at
  1 km with the ATGM and a BMP-2 at 400 m with the 30 mm, and dismount the squad behind cover.
- **Real reference:** BMP-2 tracked infantry fighting vehicle. 6.7 × 3.15 × 2.45 m, 65 km/h on road, amphibious
  at about 7 km/h. 30 mm 2A42 autocannon with dual feed (AP and HE), coaxial PKT, 9M113 Konkurs ATGM. Crew of three
  plus seven dismounts.
- **Model: refit and rescale.** The Superb Warfare geometry is detailed (907 cubes) and the right machine, but it is
  1.37× real size. Then:
  - Scale it to 1:1, which also lets it through 3-wide gaps the entity box now blocks.
  - Rename bones, run `extract_rig.py`, and drive road wheels, sprocket, idler and track links from synced speed.
    Hatches open for dismounting.
  - Make the smoke dischargers (bones `smk`, `lsmk`, `rsmk`) the decoy launchers.
  - Remodel only if the scale-down breaks the hatch or turret detail.
- **Behaviour:**
  - **Swims:** slow, steered by the tracks, bow wave, no firing the ATGM afloat.
  - **Dual-feed 30 mm:** switch between AP (better against armour) and HE (bursts against infantry and hordes).
  - **Dismount points** at the rear doors, not at the side.
  - **Seats placed where the real ones are.** Today passengers 3 and 4 share seat 1's and 2's zoom camera.
- **Data:** `up_step` 2.25 is a two-block climb, unreal for a tracked vehicle. Bring it to about 1.25 and test
  that it still climbs the terrain the AI drives. Keep the turret pitch limits the AI uses.
- **Hitbox:** hull, glacis, turret and barrel parts (`follows: turret`), tracks as `wheel_left`/`wheel_right`, and
  the engine at the front right where the real one sits.
- **Sound:** a CC0 tracked-vehicle diesel (UTD-20 class V6), track clatter by speed, an interior recording, the
  2A42 near and `_distant`, and the ATGM launch.
- **Tests:** `Bmp2Swim`, `Bmp2Weapons` (AP/HE, ATGM at range), `Bmp2Hitbox`, `EnemyVehicleRaid`, shared audio and far
  sound; a sandbox with a river crossing and enemy armour at range.

### 3. LAV-150 → fast scout and convoy escort

- **Task:** get somewhere fast and hold it. It escorts the truck, answers a raid before the BMP-2 can get there, and
  suppresses infantry and hordes. Wheels and speed separate it from the BMP-2; it is not an armour killer.
- **Success in five minutes:** reach a raid 2 km away on roads at about 90 km/h, clear a horde with the 20 mm and
  coax, drop off three dismounts, and outrun an enemy BMP-2.
- **Real reference:** Cadillac Gage V-150 Commando 4×4 armoured car. 5.7 × 2.26 × 2.5 m, 88 km/h, amphibious,
  rifle-proof armour. 20 mm cannon and coaxial MG in a one-man turret, roof hatches.
- **Model: refit.** It is close to scale (about 1.16×); scale it to 1:1. Rename bones, run `extract_rig.py`, steer
  the front wheels, and replace the canned `animation.lav_150.idle` with synced suspension. Add roof hatches that
  open when a passenger stands up.
- **Behaviour:**
  - **Passenger seats:** all four passengers sit at the same point (0, 1, 0) with a simulated third-person camera.
    Give them real positions, and a roof hatch seat where the passenger can fire a personal weapon (the
    `PassengerRifle` path).
  - **Armour:** stops rifle rounds, not 20 mm and up (it is lighter than the BMP-2, and the data should say so
    clearly).
  - **Run-flat tyres:** a shot-out wheel slows it rather than stopping it.
- **Sound:** a CC0 V8 diesel, tyre roar by speed, an interior recording, the 20 mm near and `_distant`.
- **Tests:** `Lav150Speed` (top speed, braking, steering at speed), `Lav150Hatch`, `Lav150Hitbox`,
  `EnemyVehicleRaid`, shared audio and far sound.

### 4. Speedboat → fast squad transport and river patrol

- **Task:** move a squad across water faster than anything else, and put it ashore. Create's block-built ships are
  big and slow, and this is small and fast.
- **Success in five minutes:** carry four players 1 km down a river and along a coast, beach the boat, and land them
  dry, with the bow gunner covering the landing.
- **Real reference:** a riverine assault boat or RHIB (SURC or CB90 class, scaled to the current hull). About 60
  km/h, planing hull, pintle MG at the bow.
- **Model: refit.** Keep the hull. Move the MG to a pintle at the bow, operated from passenger seat 1, so the driver
  drives (open question 2). Add a bow-rise pose with speed, spray and wake, and a propeller that spins from synced
  RPM. Relabel the `main_engine` box to the real engine at the stern.
- **Behaviour:**
  - **Planing:** the bow rises and drag drops above a speed.
  - **Beaching:** running onto a sand or gravel shore at speed grounds it, and it can back off under power.
  - **Currents:** it rides Streams Reflowing's currents instead of fighting them.
  - **No special cases in code:** the speedboat constants in `VehicleEntity` move into data (Phase 0.5).
- **Sound:** CC0 twin outboard or inboard diesel, hull slap by speed, the distant version heard far across water.
- **Pack interplay:** Streams Reflowing (currents), Shippy Ships, Sails and Airships, and Create Aeronautics
  (collision with block ships), Sound Physics Remastered (open water).
- **Tests:** `SpeedboatPlane`, `SpeedboatBeach`, `SpeedboatCurrent` (with and without Streams Reflowing),
  `SpeedboatHitbox`, shared audio and far sound.

### 5. HPJ-11 → base air and missile defence

- **Task:** protect a base from the air. From day 80 the enemy brings AH-6s and Mi-28s, and the Mi-28 fires
  missiles. Nothing on the ground can answer them today.
- **Success in five minutes:** place it at a base, load it with 30 mm, switch it to automatic, and watch it shoot
  down an enemy AH-6 and two incoming missiles while ignoring friendly aircraft.
- **Real reference:** an eleven-barrel 30 mm close-in weapon system (the real H/PJ-11). The model is already that
  machine, with search and tracking radars.
- **Model: refit.** Keep the geometry. Barrels spin from synced fire rate with blur; the search radar turns
  continuously and the tracking radar follows the target. Fit the hitbox to the mount, the barrel cluster and both
  radars (only a quarter is covered today).
- **Behaviour:**
  - **Proper weapon:** replace the hand `jeg:minigun` (pistol ammo) and `jeg:rocket_launcher` with a
    `vehicle_30mm_ciws`. It needs a spin-up, a high rate of fire, heat, long-range rounds and proximity bursts that
    can kill missiles and rockets in flight.
  - **Two modes:**
    - **Manual:** the operator aims, using the Mi-28 gunner's aim code.
    - **Automatic:** it tracks hostile aircraft, missiles and phantoms within range, using energy and ammo from its
      hold.
  - **Who it never fires at:** players, their vehicles, tamed or passive mobs, and anything inside a protected
    claim. It fires only above a set elevation, so it can't rake the base.
  - Enable it in `ENABLED_ASSEMBLY_VEHICLES` with a recipe that costs about what the BMP-2 does.
- **Sound:** a CC0 rotary-cannon burst (a GAU-8-class recording is a reasonable stand-in), spin-up and spin-down,
  radar servo, the distant version. Gun loudness 2.5, as the aircraft guns have.
- **Tests:** `CiwsIntercept` (missile and rocket killed in flight), `CiwsAuto` (targets an enemy helicopter, ignores
  a friendly one and a player), `CiwsHitbox`, `CiwsLongRange`.

### 6. Laser tower → perimeter sentry

- **Task:** hold a base perimeter on horde nights while the players are busy elsewhere.
- **Success in five minutes:** place three sentries on a wall, feed them rifle ammo, and they hold off a horde
  night's first wave. They never shoot a player, villager or pet.
- **Real reference:** a remote weapon station on a mast or tripod (Samsung SGR-A1 or Kongsberg Protector class),
  with a 7.62 mm MG, a day/thermal camera and an IR lamp. The current model is a sci-fi laser, so the brief rules it
  out.
- **Model: remodel**, about 1 × 2.2 × 1.5 blocks:
  - a pedestal or tripod base
  - a pan-tilt head on its own yaw and pitch bones
  - an MG with an ammo can and a belt that empties visibly
  - a camera box and IR lamp
  - a status lamp: green for idle, amber for tracking, red for firing or out of ammo
- **Behaviour:**
  - **Targets:** in automatic mode it engages hostile mobs within about 48 blocks with a clear line of sight. It
    gives priority to mobs breaking blocks (Zombie Break & Build) and to the closest threat.
  - **Supplies:** it takes rifle ammo and energy from its hold. The logistics truck can resupply it.
  - **Manual mode:** a player can sit in it.
  - **Ownership:** it obeys claims and never engages players or their entities.
  - **Cost:** cheap to assemble, so players build several.
- **Sound:** a CC0 7.62 MG at gun loudness, servo whine, and an out-of-ammo click.
- **Pack interplay:** Undead Nights, Zombie Break & Build, Zombie Improvements, Mob Spawn Control, Alex's Mobs and
  Easy Villagers (what counts as hostile), Entity Culling (it must still see culled mobs on the server).
- **Tests:** `SentryHorde` (holds a wave at the server's horde settings or harsher), `SentryFriendly`,
  `SentryAmmo`, `SentryHitbox`.

### 7. Waveforce tower → towed light howitzer

- **Task:** indirect fire. It breaks up a horde or a raid before it reaches the base, and hits targets the A-10 is
  needed elsewhere for. Create Big Cannons covers block-built direct fire; this is crewed indirect fire at long
  range.
- **Success in five minutes:** the truck tows it to a firing point. The crew deploys it, takes a target's
  coordinates from a forward observer, and lands the third round on it at 2 km.
- **Real reference:** an M119-class 105 mm light towed howitzer (about 6 × 1.8 × 1.6 m in travel, 11 km range in
  reality), chosen over a 155 mm M777 because its size suits the truck and a block world. The current model is a
  sci-fi cannon.
- **Model: remodel:**
  - split trail legs that open, a firing platform and spades
  - a barrel on an elevating cradle with a recoil sleeve that slides back on firing
  - a shield, wheels, a sight
  - a tow eye that hitches to the truck's pintle
- **Behaviour:**
  - **Deploy before firing:** opening the trails and lowering the platform takes time. Undeploy it to tow.
  - **Fire mission:** enter target coordinates, or take them from a player holding a spyglass (the forward
    observer). The HUD gives azimuth, elevation and time of flight. The shell follows a real ballistic arc on
    `FarFlight`, and impacts show through `DistantTerrainRefresh`.
  - **Ammunition:** HE and smoke shells from the truck.
  - **Protection:** block damage respects claims and the existing warhead rules, and a server config caps it.
- **Sound:** a CC0 105 mm report heard across the map (the loudest source in the mod), shell whistle on arrival,
  breech and spade sounds.
- **Tests:** `HowitzerDeploy`, `HowitzerFireMission` (lands within a dispersion radius at 1 and 2 km over real
  terrain), `HowitzerTow`, `HowitzerFarSound`.

### 8. TOM-6 → STOL bush plane

- **Task:** cheap long-range travel and scouting before players have elytra. It takes off and lands on rough ground
  in a short run, carries one passenger and a little cargo, and gives a slow, high view for mapping (Xaero's).
- **Success in five minutes:** take off from a field in under 40 blocks, fly 3 km over mountains with a passenger,
  land on a hilltop clearing, and take off again.
- **Real reference:** a PA-18 Super Cub-class STOL bush plane (6.9 m long, 10.7 m span, 2 m high, about 180 km/h,
  tundra tyres, tandem seats). The current model is a 2.7-block toy with a watermelon, and the brief rules it out.
- **Model: remodel at 1:1:**
  - high wing with struts and flaps on bones
  - a two-blade propeller with blur
  - big tundra tyres and a tailwheel
  - a tandem cockpit with a few glowing instruments (the A-10 cockpit pattern, simplified)
  - navigation lights and a landing light
- **Behaviour:** unarmed. Remove the hand LMG and rocket launcher. Flight reuses `JetFlight` with a propeller
  thrust curve, flaps for short take-off, and a stall that is gentle compared with the A-10's.
- **Sound:** a CC0 flat-four aero engine, propeller wash, the distant version, and a cockpit version.
- **Tests:** `BushPlaneStol` (take-off and landing distance), `BushPlaneFlight` (envelope, stall), hitbox, shared
  aircraft far sound; a sandbox with a short strip and a mountain clearing.

### 9. Light combat vehicle → retire

It is a placeholder from before the Superb Warfare port: generic geometry, hand guns, not assemblable, and its job
is done better by the LAV-150. Remove its recipe, item and creative entry. Keep the entity type registered for one
release, and on load turn any saved one into a dropped chest of its hold, so no world loses items. Then remove the
type.

## Release grouping (suggested)

| Build | Contents |
| --- | --- |
| yftm.20 | Phase 0.3, 0.5, 0.9 (long-range rounds, data flags, AI test). Small, low risk, and fixes a real gap now |
| yftm.21 | Phase 0.1, 0.2, 0.6 (ground audio, far tracks, generator library) |
| yftm.22 | Support vehicle, in the phases of [support/PLAN.md](support/PLAN.md), which may span several builds (with 0.4, 0.7, 0.8 as it needs them); retire the light combat vehicle |
| yftm.23 | BMP-2 and LAV-150 |
| yftm.24 | Speedboat |
| yftm.25 | HPJ-11 and the sentry (base defence together) |
| yftm.26 | Bush plane |
| yftm.27 | Howitzer |

Each build follows CLAUDE.md: focused tests for what changed, a `-PwithPack` run before bumping `yftm_build`, and a
release report that says what the pack run did and did not cover (the dedicated server's own mods, listening,
multiplayer).

## Decisions (2026-11-15)

1. Waveforce tower becomes the towed howitzer.
2. The driver keeps the main gun when alone; a passenger in the gunner seat takes it over.
3. Sentry and CIWS automatic fire: on by default, with a config cap per chunk.
4. The BMP-2, LAV-150 and support vehicle are built at 1:1.
5. Superb Warfare assets: the SBW team gave permission to use their assets (user, 2026-11-15). Keep the written
   record and the redistribution check described in [superbwarfare-port/PLAN.md](superbwarfare-port/PLAN.md).

## Open questions for the user (as asked)

1. **Waveforce tower: howitzer or retire?** Recommended: the towed howitzer, because it gives the truck a second job
   and the fleet indirect fire. Retiring it is the cheaper option if Create Big Cannons already covers what players
   want.
2. **Who fires the main gun on the BMP-2, LAV-150 and speedboat?** Today the driver drives and shoots. Recommended:
   the driver keeps the gun when alone, and a passenger in the gunner seat takes it over, as on the Mi-28. Solo play
   stays possible on a 10-player server.
3. **Sentry and CIWS automatic modes.** Should automatic fire be allowed at all on the server, and should it need a
   server config switch? Recommended: on by default, with a config cap on sentries per chunk.
4. **Scale.** Bring the BMP-2, LAV-150 and truck to 1:1 like the A-10 and bulldozer? Recommended: yes. It makes the
   BMP-2 smaller and easier to fit through gaps, and slightly harder to hit.
5. **Superb Warfare assets.** Confirm the licence of the ported models, textures and sounds. Kept models are refits
   of that geometry, and the plan replaces all ported sound with CC0 recordings.
