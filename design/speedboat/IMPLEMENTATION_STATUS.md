# Speedboat implementation status (2026-11-17)

Brief: [BRIEF.md](BRIEF.md).

## Built (refit pass 1)

- **Model:** SBW's `speedboat` refit at scale 1.0 by [refit.py](refit.py): every bone named (34, and the LOD's own
  `bone19`), 16 coplanar pairs fixed, the fully transparent helper faces dropped. The M2 moved from the cabin roof to a
  pintle on a pedestal at the bow (`pintle_post`, coloured from a palette corner added to SBW's texture); the old roof
  mount is a roof hatch. The hitbox is written from the model: hull, cabin, the engine under the stern deck, the bow
  gun turning with it.
- **Entity:** `SpeedboatEntity` (the bow gunner rides the gun). The gun is the gunner's (seat 1) and falls back to the
  driver (the turret's `"fallback_seat"`), a 12.7 mm (`vehicle_speedboat_hmg`, as the PLZ-05's roof gun), traversing
  128° either side of the bow.
- **Hull ("boat" in the data, `BoatInfo`; fleet plan Phase 0.5 for the speedboat):** the speedboat's constants left
  `VehicleEntity`; planing with hysteresis, the keel, beaching (the hull lifts itself onto a shore up to 1.25 above the
  water, scrapes to a stop, backs off under power), the draft it floats at, a deeper "afloat" probe for the engine. The
  fixed 40 km/h cruise is gone.
- **Client:** the bow rises on the plane, the screw spins and the rudder turns from the hull's motion on every client;
  spray off the bow and a wake behind the transom on the plane.
- **Sound:** `GroundVehicleAudio` with a speedboat profile (idle, working and distant loops derived from SBW's engine by
  `prepare_audio.py`, which now takes the vehicles to redo).
- **Lights:** red, green, masthead and stern lights, the cabin-roof searchlight.
- **Pack runs:** Streams Reflowing added to `packMods` with the server's settings (its streams carry no current there).

## Played (mcpfabric, pack client with Photon, a lake built beside the ArmourTest sandbox)

- By day from the shore: afloat at its waterline (after the draft; it first floated with its red bottom showing).
- At the helm, alone: fired the bow gun (the driver has it). The helm view looks out through the windscreen past the
  gun's post.
- Flat out in third person behind and in front: 16.7 m/s (60 km/h) on the HUD, the bow up, spray off both sides (after
  the fix below), a turn at speed carved in.
- Ran it at the sand bank at speed: it rode up and stopped on the sand, no damage (after the fix below).
- Changed seat to the gunner: sits behind the gun on the foredeck, swings round with it abeam; first person over the
  receiver (after the fix below); fired, the flash at the muzzle.
- Night: masthead, sidelights and the searchlight's beam.
- Found and fixed:
  - It never got past 30 km/h: the planing hull rose out of the buoyancy probe and the driver's client dropped it to
    its crawl ashore. "Afloat" is now a deeper probe than buoyancy.
  - It floated with its red bottom out of the water: `"draft"`.
  - Run at the bank it stopped dead: the bow gun's hitbox overhangs the stem and met the bank before the bow probe
    looked. The probe starts at the collision box's front; the hull lifts itself onto the shore (vanilla doesn't step a
    floating entity); beaching takes no impact damage (it took 3).
  - No spray was seen: it spawned inside the hull (placed from the square entity box). It is placed from the hull's
    boxes now.
  - The gunner's first-person view was filled by the gun's receiver: a fixed camera over the receiver, turning with the
    gun.

## Tested (`SpeedboatClientGameTest`, `-PwithPack`, passes)

| Check | Result |
| --- | --- |
| Driver alone, then a gunner boards, then leaves | the driver has the gun; the gunner takes it (turret seat 1); it goes back |
| Full throttle from rest | on the plane after 28 ticks; 0.84 blocks a tick (60 km/h) steady; bow rise 3.7° seen on the client |
| Flat out at a sand bank one block high | rides up onto the sand and rests; astern, backs off and floats again |

## Finalisation (2026-11-17, design/READINESS.md)

- **Sinks when destroyed** (`VehicleEntity.sinksAsWreck`, all boats): it burns on the water losing way, its fuel goes
  up after 1.5 s (the fireball; what was aboard thrown out to float), then it floods, settling by the stern and
  listing; at 2.5 s it goes under with a splash, sinks stern first (bow up 20°) at half a block a second with air
  bubbling up and the bubble-column sound, settles on the bottom and breaks up there 8 s later in a last rush of air.
  No fire under water (bubbles instead). Beached, it burns out as a land wreck does. Before, it vanished.
- **Real start and stop:** a lobster boat's marine diesel starting (catching at 0.6 s, the loops winding up then) and
  shutting down (bone666138, CC0; `prepare_audio.py`).
- **Water along the hull** (`GroundVehicleAudio` "hull", `vehicle/water/hull`): the rush and slap of water on a
  planing hull by speed through the water, from a motorboat at speed on a lake (jazbavac, CC0, MKH 416). The swimming
  BMP-2 and LAV-150 get it too, against their swim speed.
- **Feel** (`VehicleFeel` for boats): banks into a turn (inward), pitches and heaves on the chop more the faster it
  runs, rocks gently at rest; the crew's camera rides it (not through the gun sight).

| Test (`-PwithPack`) | Result |
| --- | --- |
| `BoatWreckClientGameTest` (new) | passes: burns afloat, blows up, 16 diamonds thrown out; under at +55 ticks, on the bottom (7 deep) at +245, bow up 20°; broke up at +565 |
| `SpeedboatClientGameTest` | passes, with the start heard on taking the helm and the hull water at 0.32 on the plane (66 km/h, bow rise 3.7°) |

Played (mcpfabric, pack client, ArmourTest lake, 2026-11-18): from the south end on the plane up the lake (16.7 m/s on
the instrument, spray along the hull, about 22 blocks a second over the open water), beached on the sand without
damage (hull 200 throughout), crawled ashore while held, backed off the beach under power. At midnight the navigation
lights show (green to starboard, red to port, the white masthead lamp). The helm's view through the windscreen and the
chase view are clear. Found adrift in a corner of the lake facing the bank, it beached at once (the lake has no
current; it had been pushed there).

## Not covered yet

- Currents: not tested (the server's streams carry none; vanilla flowing water pushes the hull, no longer cancelled).
- Collision with block-built ships (Shippy Ships, Sails and Airships, Create Aeronautics); hitboxes shot at.
- Four players aboard; landing them dry; the 1 km river run.
- **Listen:** `speedboat/engine_start`, `engine_stop`, `water/hull` (also on the swimming BMP-2 and LAV-150), the
  sinking (vanilla splash and bubble sounds); the running loops are still SBW's engine.
- The bank and chop seen in game.
- The dedicated server, multiplayer.

## Helm console (2026-11-19, the user's review)

A live panel on the sloped dash in front of the wheel, like the helicopters' cockpits (`SpeedboatCockpitLayer`,
textures from `make_console.py`): a chart plotter whose compass ring turns heading-up round the boat's own mark and
course line, an engine page with bars for revs, fuel (energy), trim (bow rise) and temperature, a tachometer
(0-6000 rpm) and a speedometer in knots with live needles, a compass bowl, and six lamps (engine, navigation lights,
on the plane, low fuel, hull damage, searchlight). The screens flicker on with the engine and dim a little at night.
Drawn in the hull's own frame: at first it sank into the dash as the bow rose on the plane (found in play). Played at
rest, on the plane at 16.7 m/s and at midnight. Not looked at from the gunner's seat or under each paint colour.
