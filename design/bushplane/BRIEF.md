# Bush plane brief (fleet plan section 8; replaces the TOM-6, keeps the id `jeg:tom6`)

## Task

Cheap long-range travel and scouting before players have elytra. It takes off and lands on rough ground in a short
run, carries one passenger and a little cargo (a small hold), and gives a slow, high view for mapping.

**Success in five minutes:** take off from a field in under 40 blocks, fly 3 km over mountains with a passenger, land
on a hilltop clearing, and take off again.

## Real reference

A PA-18 Super Cub-class two-seat STOL bush plane: about 7 m long, 10.7 m span, 2.4 m high on tundra tyres, tandem
seats, a strut-braced high wing with flaps, a cowled flat-four turning a two-blade propeller, a tailwheel. Cruise
about 160 km/h, top about 180 km/h, stall about 60 km/h with flaps.

## Model (1:1; [generate.py](generate.py), a new model, not an SBW refit)

- 7.5 blocks long, 10.7 span, 2.4 high; 13 bones, 71 cuboids, one 128 x 32 palette atlas.
- Bones the renderer moves: `propeller` (spins with the engine's spool) and `flap_left`/`flap_right` (droop 30°
  while they are out). The ailerons, elevator and rudder are bones of their own but stay still, as the A-10's do.
- Lights (AircraftLights `TOM6`): red and green navigation lights on the wingtips (red on the pilot's left), a white
  tail light, a red beacon, and a landing light in the left wing that is also lit while taxiing.

## Behaviour

- Unarmed: the hand LMG and rocket launcher are gone. Pilot (seat 0) and a passenger behind (seat 1), a 27-slot hold (the TOM-6's, so saved planes keep their cargo).
- Flight is `JetFlight` with the new `"aircraft"` data block (`AircraftInfo`):
  - `thrust_lapse` 0.45: a propeller loses much of its thrust by top speed (the A-10's turbofans 0.12);
  - `fixed_gear`: the wheels stay down, so no retraction, no gear sound and no extra gear drag;
  - flaps: below 1.5 × the clean stall speed the flaps run out (about a second), and the stall and rotate speeds
    drop to 0.78 of clean, at extra drag. So it leaves the ground and lands slow, and cleans up as it speeds up;
  - its own strike points (wingtips, nose, tail) for terrain contact.
- Engine: acceleration 0.02, top speed 2.5 blocks a tick (180 km/h), clean stall 0.95 (68 km/h; 53 km/h with
  flaps), dive limit 3.2. Health 120, energy 300,000.
- It does not say "pull up": the A-10's spoken warning is that jet's own (`VehicleSoundHelper.jetOwnSound`).

## Sound

From the SBW TOM-6's engine recording (permission: `design/superbwarfare-port/PERMISSION.md`), made by
[prepare_audio.py](prepare_audio.py) into the engine loop, cockpit and distant versions, a start-up and a flyby. The
rest (wind, runway roll, touchdown, the stall horn) falls back to the A-10's generic recordings. A CC0 flat-four
recording is still to find.

## Tests

- `BushPlaneClientGameTest`: the take-off run from the brakes at full throttle (10-40 blocks, undamaged) and the top
  speed level at full throttle (2.2-2.6 blocks a tick).
- Played via mcpfabric in the pack: take-off, circuit, landing, both seats, first and third person, by night.
