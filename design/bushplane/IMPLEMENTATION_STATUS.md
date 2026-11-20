# Bush plane implementation status (2026-11-17)

Brief: [BRIEF.md](BRIEF.md). Fleet plan section 8: the TOM-6 remodelled as a STOL bush plane, keeping `jeg:tom6`.

## Built (pass 1)

- **Model:** a new Super Cub-class model at 1:1 by [generate.py](generate.py), not an SBW refit:
  - 7.5 × 10.7 × 2.4 blocks, 13 bones, 81 cuboids;
  - open cabin windows (posts only), an instrument panel with three dials, the cowl below the pilot's eye;
  - hitbox rows written from the model.
- **Flight:** `JetFlight` with the new `"aircraft"` data block (`AircraftInfo`):
  - a propeller's thrust lapse (0.45);
  - fixed gear;
  - flaps below 1.5 × the clean stall (stall and rotate at 0.78 of clean, extra drag);
  - its own strike points;
  - its own wing camera for the reversed view.
- **Data:** unarmed (`"weapons": []` now means none), pilot and passenger, the TOM-6's 27-slot hold; health 120,
  energy 300,000; entity box 3.0 × 2.4; assembly enabled.
- **Render:** the propeller turns with the engine (the A-10's fan turn) and blurs into a disc at power
  (`VehicleFanBlurLayer`); the flaps droop while out; the HUD shows FLAPS OUT / FLAPS IN in place of the gear.
- **Lights:** red (left) and green (right) wingtip lights, white tail light, red beacon, a landing light in the left
  wing, lit while taxiing.
- **Sound:** the SBW TOM-6 engine recording made into engine, cockpit, distant, start-up and flyby
  ([prepare_audio.py](prepare_audio.py)). A CC0 flat-four is still to find.

## Played (mcpfabric, pack client with Photon, AircraftTest sandbox)

| What | How it went |
| --- | --- |
| Walk-round by day | Reads as a Cub: tundra tyres, tailwheel, strut-braced wing; to scale beside the Mi-28. |
| Boarding, pilot's view | **Found:** a closed yellow box with a slot of sky; the "door frames" covered the windows and the cowl top stood above the eye. **Fixed:** posts, cowl lowered, panel added; it now sees out ahead and to both sides. |
| Pilot's HUD | **Found:** an assault rifle and a pistol listed; an empty `"weapons"` list fell back to the defaults. **Fixed** for every unarmed vehicle. |
| Take-off, W and a pull | Rotates at flap speed, lifts off in about 32 blocks, climbs at about 11° (0.37 b/t at 150 km/h). |
| Hands-off, W held | **Found:** a wreck at 103 km/h. Cause: the sandbox truck parked across the strip at z 120, not a fault. Stays on its wheels hands-off (test). |
| Climb, level, turn | Climb holds; a mouse turn banks and comes round 13°, and levels the wings when the mouse stops. |
| Third person behind and reversed | **Found:** the reversed view floated past the left wingtip (the A-10's wing camera). **Fixed:** `wing_camera` per aircraft; now on its own left wing. |
| Night circuit | **Found:** the navigation lights were swapped (green on the left), as was the landing light. **Fixed**, and a guide rule added. Approach at 52 km/h with the flaps out, touchdown at 58 km/h, stopped on the brakes in 26.6 blocks, no damage. |
| Passenger seat | Sees ahead between the front posts, over the panel and cowl. |
| Harness | `/jegstick` added to fly from the bridge; it first left the real cursor behind, and the snap-back pitched the nose down mid-climb. Fixed. |

## Tested (`BushPlaneClientGameTest`, `-PwithPack`)

| Check | Result |
| --- | --- |
| Ground roll from the brakes, full throttle, 12° climb-out (10–40 blocks) | 31.8 blocks; unstick at 64 km/h; no damage |
| Over 15 blocks (50 ft), wanted under 180 (a Super Cub's 150 m) | 143.9 blocks |
| The player at the controls, W held, mouse left alone | stays on its wheels to 156 km/h, no damage |
| Top speed level, full throttle (2.2–2.6) | 2.52 b/t (182 km/h) |

## Finalisation (2026-11-18, design/READINESS.md)

- **Tail down on the ground**, as a taildragger sits: the model's tailwheel now hangs 15 px above the main wheels'
  contact with the fuselage level (as in flight), and the renderer pitches the airframe 10.5 degrees nose up about its
  main wheels while it is slow on the ground (`BushPlaneStance`), easing level as the tail comes up on the take-off roll
  (by 45% of the stall speed). Drawn only: the flight model and its strike points are unchanged.
- **The control surfaces move**, from the attitude rates every client sees: the ailerons with the roll rate (the
  right one up rolling right), the elevator with the pitch rate (up as the nose comes up), the rudder with the yaw rate
  (into the turn); smoothed per tick, 20 degrees at most. The rudder turns about Y, whose sign GeckoLib's mirrored X
  frame reverses (guide).
- **Real recordings** (`prepare_audio.py`, CC0): the cabin in cruise from a 1964 Cessna 172, the engine started from a
  Piper Cub's cabin, a Diamond DA40's flat-four circling a lake for the distant layer and taking off past the recorder
  for the flyby. The close exterior loop and its start stay the SBW TOM-6 recording (no steady close exterior take of a
  light aircraft is free to use).
- `"mass"` 0.8 t (tow load only).

| Test / play | Result |
| --- | --- |
| `BushPlaneClientGameTest` (`-PwithPack`), now with the stance | passes: 10.5 deg nose up at rest; ground roll 36.2 blocks (including 7.8 crept during the run-up), unstick at 64 km/h, over 15 blocks after 149.1, top speed 182 km/h, no damage |
| Played (mcpfabric, pack client) | sits nose up on its tailwheel at rest (from the side); the tail comes up level by 7 m/s on the roll; pulled off, climbed at 26 degrees; a mouse turn to the right banks it right |

Found, not changed: on the brakes at full throttle it creeps (7.8 blocks over the 12 s run-up, rolling at 0.2 b/t at
the end); a Cub's heel brakes hold a run-up at part power, not full. The take-off test's ground roll includes the creep.

## Not done / not covered

- No separate landing-distance or stall game test yet (the plan names `BushPlaneStol` and `BushPlaneFlight`); the
  landing was played, not pinned.
- No hilltop-clearing sandbox; the 3 km mountain flight with a passenger is not played.
- Sound not listened to (builders can't hear the game): please listen to the engine in and out of the cockpit, and the flyby.
- **Listen:** `tom6/engine_cockpit` (Cessna cabin; check for voices), `engine_start_cockpit` (Cub), `engine_distant`
  and `flyby` (DA40); the SBW `engine` and `engine_start`.
- The control surfaces seen close up in flight (too small to judge from the chase camera in a screenshot).
