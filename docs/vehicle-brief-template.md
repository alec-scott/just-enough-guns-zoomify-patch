# <Vehicle name> brief

Copy to `design/<vehicle>/BRIEF.md` and fill in every section before building.

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Standard to match:** the A-10 and helicopters
- **Worked example of the process:** the BD-40 bulldozer

## Purpose

- **What the player uses it for:** <one sentence: "clearing land", "moving troops"...>
- **Success looks like:** <what a player can do in five minutes with it>

## Real-world reference

- **Class / model it is based on:** <for example "mid-size crawler dozer, Cat D6 class">
- **Proportions:** <length × width × height in blocks; the main masses and where they sit>
- **Operator position and what they see:** <open or enclosed seat; must-see items such as tool edges or
  mirrors>
- **Moving parts and what each one does:** <part: function, or "cosmetic">
- **Real top speed / handling:** <value and gameplay value; coast or hold when controls are released>
- **Start-up and shut-down:** <how the real engine starts and stops>

## What to reuse from the A-10 / helicopters

<Which systems this vehicle uses as they are, and which it extends: AircraftSoundRange, far tracks, Doppler, cockpit
sound, long-range projectiles, lights, wreck behaviour...>

## Controls (same family as <peer vehicle>)

| Key | Action |
| --- | --- |
| W / S | |
| A / D | |
| Left Ctrl | |
| Space / Shift | |
| <other> | |

Changes from the peer vehicle, and why: <...>

## Vehicle data

- **Seats:** <index, position, `hide_passenger`, `enclosed`, pitch/yaw limits, why>
- **Engine:** <type, acceleration, speeds, friction, steering; the peer's values for comparison>
- **Armed:** <weapons / turret seat / none>

## Hitbox

| OBB part | Model part it covers | Centre (x, y, z) | Size (w × h × d) | Armour |
| --- | --- | --- | --- | --- |
| body | hull | | | |
| | | | | |

- **Model bounds (from rig.json):** <x, y, z ranges>
- **Moving or long parts and how they are handled:** <own part / code / hull-sized entity box>
- **Entity `sized(w, h)`:** <values and why>
- **Must fit past / under:** <for example "a 3-wide gap, tree canopies at 3 blocks">

## Behaviour

- **The job, step by step:** <what happens per tick on the server>
- **Limits and protection:** <what it refuses to do; tags; protection events; per-tick budgets>
- **States shown on the HUD:** <...>
- **Damage and wreck states:** <smoke/fire thresholds, part damage, what the wreck does>

## Weapons (if armed)

| Weapon | Projectile path | Range | Tracers / impacts far off | Recoil / heat / reload |
| --- | --- | --- | --- | --- |
| | | | | |

## Pack interplay

| Mod | Its config on the server | How the vehicle cooperates | Fallback without it | Test |
| --- | --- | --- | --- | --- |
| | | | | |

## Sound

| Layer | Recording (CC0 link) | `_distant` / interior version | Loudness (AircraftSoundRange) | Driven by | Loop / one-shot |
| --- | --- | --- | --- | --- | --- |
| engine idle | | | | RPM | loop (static) |
| engine load | | | | RPM | loop (static) |
| weapon | | | 2.5 | firing | loop (static) |
| | | | | | |

- **Real recordings, no voices, loop lengths:** <source of each recognisable sound; speech scan done; loops 15–20 s
  with drift>
- **Heard to:** <blocks; the far track extended to this vehicle: yes / no>
- **First-person sound:** <exterior centred (open seat) / interior recording (closed cab)>

## Lights

- **Lamps (model px):** <...>
- **Beacon / navigation lights:** <...>
- **Searchlight lens on:** <real lamp>
- **`openSeat`:** <yes / no>
- **Instruments:** <...>
- **Lights on moving parts:** <on only in the working position; lens position in the posed model>

## Interior, interaction and screens

- **What the player can use inside:** <each block or fitting; where they stand to click it; what it opens>
- **What can be fitted or removed, in which states:** <packed / unfolding / deployed / moving; from where>
- **What each input slot accepts:** <include the raw forms a player carries: ingots, not blocks>
- **Console / main screen:** <status readings (synced only), keys, module icons; style to match the vehicle>
- **Windows:** <real openings with glass, seen through from both sides / none>
- **Interior light:** <visible 3D source, block light level>

## Road-vehicle basics (if it drives on wheels)

- **Turning radius:** <real value in blocks; `steering_speed`; measured value>
- **Tail / brake / reversing lights:** <positions in model px>
- **Horn:** <real recording of this class of vehicle>

## Player checklist (before hand-over)

Copy the table from the guide's "The player's checklist" and answer each row for this vehicle, as a player in the
sandbox, with real keys and clicks.

## Tests

- **Focused test class:** `<Vehicle>ClientGameTest`. Checks: <list>
- **Hitbox test:** <OBB within bounds; shots per part hit, shots beside miss; fits past / under>
- **Sound tests:** <OpenAL per view; far sound to 1100 blocks; ground observer (Doppler, delay)>
- **Range tests for each weapon:** <...>
- **Screenshots:** first and third person, day and night, the job in progress, hitboxes, damage and wreck.
- **Pack runs:** `-PwithPack`, plus `-PpackExclude=<mod>` for each interacting mod.
- **Regression tests for shared code touched:** <...>
- **Sandbox world:** `<Name>` (course: <...>)

## Open questions for the user

- <anything ambiguous in the request, with the interpretation taken>
