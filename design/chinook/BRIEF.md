# CH-47F Chinook brief

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Standard to match:** the A-10 and the helicopters (AH-6, Mi-28)
- **Worked example of the process:** the BD-40 bulldozer; the FSV-6 for the room and its working blocks
- **Plan, decisions and phases:** [PLAN.md](PLAN.md)

Status 2026-11-20: brief drafted with the plan. Figures marked *(P0)* are public spec values still to be checked
against drawings. Figures marked *(P1)* come from `rig.json` once the generator exists.

## Purpose

- **What the player uses it for:** moving a squad, their gear and a vehicle across the map. It's a flying room people
  walk about in, fight from and build in, with a sling under it for a vehicle or gun.
- **Success looks like:** in five minutes a crew of three can:
  - walk up the ramp, set down a chest and a crafting table in the cabin, and take off
  - hook an LAV-150 from the hover and fly it 1 km with the load swinging and trailing
  - while one of them stands on the lowered ramp and fires a rifle at targets on the ground
  - set the LAV-150 down, land beside it, drop the ramp and drive it away

## Real-world reference

- **Class / model:** Boeing CH-47F Chinook, a tandem-rotor heavy-lift helicopter (US Army).
- **Proportions *(P0)*:**

  | Measurement | Value |
  | --- | --- |
  | Fuselage length | 15.5 m |
  | Length with rotors turning | 30.1 m |
  | Rotor diameter | 18.3 m each, 3 blades each |
  | Rotor shaft spacing | about 11.9 m (the discs overlap by about 6.4 m) |
  | Height to the aft rotor head | 5.7 m |
  | Fuselage width over the sponsons | 3.8 m |
  | Cabin | 9.2 m long × 2.3 m wide × 1.98 m high |
  | Cabin floor | about 0.8 m above ground |
  | Gear | four fixed gears; the rear pair swivel, and the rear right one steers |

- **Operator position:**
  - Pilot right, copilot left, side by side in a glazed nose with chin windows.
  - Must see: the ground below through the chin windows on approach, and the sling load. On the real aircraft the crew
    chief at the hook-well hatch talks the pilot onto it; here the pilot gets a hook view.
- **Moving parts:**

  | Part | What it does |
  | --- | --- |
  | Two rotors | Turn in opposite directions, intermeshed, 60° apart. Droop at rest, cone in flight. Discs tilt with the controls (differential lateral cyclic for yaw, differential collective for pitch) |
  | Rear ramp and upper ramp door | Hydraulic: closed, level or down to the ground |
  | Gear oleos | Compress under weight |
  | Centre hook and hook-well hatch | Carry and show the sling |
  | Door-gun pintles | Train the M240s |
  | Crew door | Slides aft along the fuselage to open the right door gun's position (user, 2026-11-20) |
  | Wipers | Cosmetic |

- **Real top speed and handling *(P0)*:** 315 km/h top, 291 km/h cruise. The game value is set against the Mi-28 and
  measured. It holds attitude and hovers when the controls are released, as the AH-6 does.
- **Start-up:** APU first (a high whine), then engine 1 and engine 2 (T55 turboshafts) light in turn, then the rotors
  engage and run up to 225 rpm. Shut-down reverses it, with the rotors coasting down.
- **Weights *(P0)*:**

  | | Mass |
  | --- | --- |
  | Empty | 11.1 t |
  | Max take-off | 22.7 t |
  | Centre hook limit (real) | 11.8 t; in game no limit (user, 2026-11-20) |
  | Forward and aft hooks | 7.7 t each (not used) |

- **Crew and capacity:** 3 crew plus door gunners, 33 to 55 troops.

## What to reuse from the A-10 / helicopters

Taken as they are:
- the rotor flight family (`tickHelicopterMovement`)
- `AircraftSoundRange`, far tracks (`AircraftTrackPayload`), Doppler and delayed sound
- the cockpit-sound split (`HelicopterEngineAudio`)
- `AircraftLights` (lamp spots, glows under shaders)
- `CockpitPanel` for the live glass cockpit
- the helmet HUD (`HelicopterHmd`)
- the wreck path (goes down burning) and flares
- `VehicleFeel` for the gear squat and crew rumble

Extended:
- rotors become a data list, for two rotors
- `externalAcceleration()` for the sling's pull
- the tail-camera F5 view gets a hook view

New and shared:
- `CabinSpace` (walking aboard, the cabin grid)
- `SlingLoad`

## Controls (same family as the AH-6)

| Key | Action |
| --- | --- |
| W / S, A / D, Space / Shift | as the AH-6 (cyclic, yaw, collective) |
| N (tow key) | sling: tap to lower or raise; tap over a load to hook up; hold 1 s to release |
| X (weapon switch; the pilot has no weapons) | ramp: closed → level → down |
| Flares | as the helicopters |
| F5 | chase, action camera, **hook view** while the sling is out |
| On foot aboard: right-click | sit in a seat, the ramp panel, placed blocks |

Changes from the AH-6, and why: N and X carry the sling and the ramp. Check both against the pack's bindings in P0.

## Vehicle data

- **Seats:**

  | # | Seat | Notes |
  | --- | --- | --- |
  | 0 | Pilot (right) | driver; `ban_hand` |
  | 1 | Copilot (left) | |
  | 2 | Left door gunner | M240H, forward escape-hatch window |
  | 3 | Right door gunner | M240H, crew door |

  No troop benches (the user, 2026-11-21): the cabin is left empty for the players' own blocks; each door gunner has a
  jump seat by the gun.

  All seats are `enclosed` for damage, but `hide_passenger` false: you see the crew. Look limits are as the real seats
  allow. Door gunners face out of their windows, with yaw limits relative to the hull, as the speedboat's port seats
  have.
- **Engine:** `"type": "rotor"`, `"mass"` about 15 (a typical operating weight), and the max forward speed against the
  Mi-28's 0.55, measured. Yaw, pitch and lift rates are slower than the AH-6's. All values are written here once
  measured in P2.
- **Armed:** two M240H side door guns as turret weapons on their seats (the right one in the sliding crew door, usable with it open; the left one in the escape-hatch window), flares (`has_decoy`). No ramp gun, no rockets.

## Hitbox *(P1)*

| OBB part | Model part it covers | Armour |
| --- | --- | --- |
| nose | cockpit and nose | |
| wall_left, wall_right, roof, belly | cabin shell; the room inside stays empty | |
| sponson_left, sponson_right | fuel sponsons | fuel |
| pylon_fwd, pylon_aft | transmissions | `main_engine` |
| nacelle_left, nacelle_right | T55 engines | `sub_engine` |
| ramp | ramp; follows its hinge (a new `Follows` kind or `obb` per pose) | |

- **Model bounds:** from `rig.json`.
- **Long parts:**
  - The rotors are handled in code (ground strike for both discs from `"rotors"`).
  - The entity box is hull-sized (`usesHullBlockCollisionBox`).
  - `partBounds()` covers both discs.
  - The room's own boxes are kept out of `envelopeBoxes` and `entityPushBoxes` (the FSV-6's lesson).
- **Must fit past / under:** it lands on a 20 × 34 pad clear of trees. The forward rotor's droop height above the
  ground is measured.

## Behaviour

- **The job, step by step:** see PLAN.md: cabin frame, ramp, sling.
- **Limits and protection:**
  - pack-up refused with anyone aboard or a load on the hook
  - ticking machines refused in the cabin grid
  - the sling takes only empty vehicles
  - no weight limit (user): a heavy load slows the climb and swings harder
- **States shown on the HUD:**
  - the status card (name, hull, energy, speed, radar height)
  - the sling card (state, pendant length, load and mass, swing angle)
  - a ramp chip
  - the start-up stage
- **Damage and wreck:**
  - smoke, then fire, by hull left
  - a transmission hit loses one rotor's lift
  - it goes down burning
  - the sling releases
  - on impact, everyone aboard is thrown out and every cabin block and container's contents drop

## Weapons

| Weapon | Projectile path | Range | Tracers / impacts far off | Recoil / heat / reload |
| --- | --- | --- | --- | --- |
| M240H ×2 (side doors) | long-range gun rounds (as the helicopter guns) | full | yes, to 1024 | pintle 3°/tick, medium flash |
| Hand guns from the cabin | `BulletEntity`, plus the cabin's velocity; stopped by the cabin walls | the gun's | as the gun | as the gun |
| Flares | decoys | — | — | as the helicopters |

## Pack interplay (checked in P0; the `yftm` MCP was down on 2026-11-20)

| Mod | How the Chinook cooperates | Test |
| --- | --- | --- |
| Carry On | `jeg:ch47` in both blacklists | sneak-click doesn't lift it |
| Sable / Create Aeronautics (server?) | mixin overlap with walking aboard | P0 spike against its jar |
| Iris + Photon | lights, cabin and glass under shaders | night screenshots from every seat |
| LambDynamicLights | lamps placed in the cabin, and the searchlight | night cabin |
| Entity Culling | `partBounds()` covers the rotors | walk round it |
| Not Enough Animations / Fresh Animations / EMF | limbs aboard animate from cabin motion | play-through |
| Inventory Profiles Next | `@IPNIgnore` on our screens; cabin chests sortable | — |
| Anti-cheat or movement mods (if any) | exempt people aboard | P3 flight |
| Parachute mod (if any) | jumping off the ramp | play-through |

## Sound

| Layer | Recording | `_distant` / interior | Loudness | Driven by | Loop / one-shot |
| --- | --- | --- | --- | --- | --- |
| rotor blade slap | real CH-47 (CC0 to find) | both | 3.0 | rotor rpm, load | loop (static) |
| turbines | real T55 / CH-47 | interior | 2.5 | engine rpm | loop |
| cabin transmission whine | real CH-47 interior | interior only | — | rotor rpm | loop |
| APU | real APU start and run | — | 1.5 | start-up | one-shot and loop |
| ramp hydraulics | real hydraulic pump | — | 1.0 | ramp moving | loop and end clunks |
| winch, hook latch, release | real | — | 1.0 | sling | one-shots |
| rope strain | real | — | 0.6 | tension | loop |
| M240 | the speedboat's or a real M240 | far | 2.5 | firing | loop (static) |
| rotor wash wind | real | — | 1.5 near only | height, rpm | loop |

- No voices; loops of 15–20 s with drift.
- Heard to 1024 blocks, with far tracks.
- First-person sound: the cabin and cockpit hear the interior mix; standing on the open ramp crossfades to the exterior.

## Lights

- **Anti-collision:** red strobes on the aft pylon and the belly.
- **Nav lights:** red left, green right, white tail.
- **Formation lights:** green strips along the fuselage and the pylons.
- **Landing and search lights:** two under the nose, steerable; the searchlight lens on the real lamp.
- **Cabin:** dome lights white, red or off from the panel. Red and green jump lights over the ramp. Lamps placed in the
  grid.
- **Instruments:** NVG-green at night.
- **`openSeat`:** no (cockpit enclosed). The door gunners count as open.
- **Lights on moving parts:** the ramp's lights are lit only in its working position.

## Interior, interaction and screens

- **What the player can use inside:** the gunners' seats (sit), the ramp panel (ramp, jump lights, dome lights), placed blocks.
  Each is clicked from where you stand, with a prompt.
- **What can be fitted or removed:** any block in the cabin grid, in every state (landed, flying, ramp open), from
  inside. A gunner's seat can't be taken while a block stands in its cell.
- **Input slots:** the working blocks' own menus (the FSV-6's set).
- **Console:** the cockpit MFDs show the sling and ramp state. No remote control of the cabin's blocks.
- **Windows:** real portholes with glass, and the cockpit glazing, seen through from both sides.
- **Interior light:** dome lights you can see, plus whatever lamps are placed. No built-in fill light.

## Player checklist (before hand-over)

The guide's table, answered as a player in the `ChinookTest` sandbox in P9.

## Tests

| Test | Phase |
| --- | --- |
| `Ch47RotorPhase` | P2 |
| `Ch47CabinWalk` | P3 |
| `Ch47Ramp` | P4 |
| `Ch47RampGun` | P5 |
| `Ch47CabinBlocks` | P6 |
| `Ch47Sling` | P7 |
| hitbox (per part, beside it misses) | P2 |
| far sound to 1100 blocks and ground observer | P8 |
| night lights from every seat | P8 |
| wreck with people aboard and a load on | P8 |

- **Pack runs:** `-PwithPack` for each, plus the mods found in P0.
- **Sandbox world:** `ChinookTest`: an airfield pad, an LAV-150, a howitzer, a speedboat on a pond, an overweight
  BMP-2, a firing range behind the pad, and chests of blocks.

## Open questions for the user

See PLAN.md, "Open questions". Each says the interpretation taken.
