# 4x4 pickup and technicals brief

Status: **pass 1 being built, 2026-11-26** ([refit.py](refit.py), [rig.json](rig.json)). The first of the upstream
candidates in [the port plan](../superbwarfare-port/PLAN.md) ("Sodayo TenEven9" pickup technical, four versions).

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Peers:** the LAV-150 (wheeled, the shared wheel driver), the speedboat (a standing gunner on a pintle 12.7 mm), the
  Type-63 (the same 107 mm pod, now `RocketPodEntity`), the TOW (the same launcher and wire guidance)
- **Source:** Superb Warfare's `sodayo_pick_up`, `_hmg`, `_rocket`, `_tow` at the Fabric port's `1.21-Fabric`,
  commit `c5189d1` (`fetch_sbw.py --pin 2026-11`), used with the SBW team's permission.

## Purpose

- **What the player uses it for:** fast, cheap wheels. The unarmed pickup carries four in the bed; the technicals put
  a 12.7 mm, a 107 mm rocket pod or a TOW where a team can reach a fight before any armour, and leave as fast.
- **Success:** cross 2 km of road quicker than the LAV-150, put a 12.7 mm burst, a ripple of rockets or a TOW onto a
  target and drive off; lose it to a single 30 mm burst.

## Real-world reference

- **Class:** a single-cab 4x4 pickup of the Land Cruiser 70 / Hilux class, the "technical" of irregular forces:
  5.22 × 1.87 × 1.94 m, wheelbase 3.18 m, 0.8 m tyres, about 2.3 t empty.
- **Refit:** SBW's truck is 6.84 blocks long: **scale 0.76** gives 5.2 blocks, a 3.13 wheelbase, 0.77 tyres and a
  1.81-block body (2.3 over the mirrors).
- **Taken out:** SBW's memes (the port's scope): the "Beast Senpai" figurine on the dashboard, the "Sodayo!" door
  decals, the "SODAYO" tailgate lettering, the 114514 plates (now plain plates) and the badge (now a plain chrome
  oval). No maker's name or badge is used.
- **Moving parts:** the four `wheel_*` (front pair steer), `turret`/`barrel` of each weapon, `door_left`/`door_right`
  (not yet swung), the rocket pod's `rocket_1..12` (hidden as fired) and `handwheel_elevation`, the TOW's `missile`.
- **Weapons as fielded:** an M2-class 12.7 mm on a post, a Type 63 twelve-tube 107 mm pod on a pedestal, a TOW on its
  tripod. All face forward over the cab at rest.

## Vehicle data

- **Seats:** driver (left, as the steering wheel) and front passenger in the cab; the pickup has four bed seats
  along the sides; the 12.7 mm and TOW have a standing gunner (`standing_seats`, `grip_seats`, `turret_riders`); the
  rocket technical is laid and fired by the driver from the cab (`turret.seat` 0), as its crew fires it electrically.
- **Engine:** wheel, `max_forward_speed` 2.2 (about 95 km/h, the fastest land vehicle), acceleration 0.026, steering
  0.42 (8.3 to 8.6 blocks round from a crawl, `PickupClientGameTest`), `mass` 2.4–2.7. The LAV-150 is 1.8 / 0.024 / 0.29 at 9.9 t.
- **Armour:** none: rating 1.5, rifle rounds go through to the crew (`passenger_leak_multiplier` 0.45); 140–150 hull.

## Hitbox

From the refit (`rig.json`): cab and bonnet, bed, engine under the bonnet, each side's wheels, the weapon mount, and the
weapon following the turret. Entity box 2.3 × 2.0.

## Weapons

- 12.7 mm: the speedboat's M2 (`vehicle_speedboat_hmg`), rifle ammunition, 100-round box. Elevation −30° to 60°.
- 107 mm: the Type-63's rockets (HE, AP, cluster) from the same ammunition, rippled top row first. Elevation 10° to 60°
  (at 10° a ripple over the cab clears its roof).
- TOW: the TOW launcher's missile and wire (1,000 blocks), the gunner at the sight. Elevation −20° to 20°.

## Pass 1 (this pass)

The refit, data, entities, renderer, recipes, names, Carry On blacklist, pod shared with the Type-63.

## Still to do

Lights (head, tail, brake, reversing), real engine and horn recordings, door swing on boarding, the cab occupants'
fit (a seated Minecraft body is taller than the real cab), HUD names, sandbox placement, a focused game test, the
player's checklist in every view.
