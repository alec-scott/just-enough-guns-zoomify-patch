# PLZ-05 self-propelled howitzer brief

Status: **being built, 2026-11-17.** Model refit: [refit.py](refit.py), [rig.json](rig.json).

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Peers:** the [towed 105 mm howitzer](../howitzer/BRIEF.md) (fire missions, the observer, charges), the
  [Mk42](../mk42/BRIEF.md) (its 5-inch shells, the slew-limited turret), the BMP-2 (tracked drive)
- **Source:** Superb Warfare's `plz_05` (model, texture, sounds), used with the SBW team's permission; see
  [the port plan](../superbwarfare-port/PLAN.md).

## Decisions (user, 2026-11-17)

- **A self-propelled howitzer whose fire control lays the gun.** With a fire mission set, the gunner engages the fire
  control and the turret slews and elevates itself onto the solution; the gunner picks the charge and arc and fires.
  The gun also fires direct, laid by the gunner's view, for close defence.
- **It fires the Mk42's 5-inch shells** (HE with proximity fuze, AP, cluster, grapeshot), as SBW's PLZ-05 does.
  No new rounds.
- **About 6 km with the top charge**, three charges.
- **Roof: a 12.7 mm machine gun and a rider.** One roof seat mans the gun on the commander's hatch; the other is a
  passenger who can use a hand weapon.

## Purpose

- **What the player uses it for:** indirect fire that moves. It drives to a firing point, takes an observer's mark,
  fires a few rounds and drives off before anything answers (shoot and scoot). Against the towed 105 mm it reaches
  half as far again, lays itself in seconds instead of by hand, and carries its crew under armour.
- **Success:** an observer marks an enemy camp 4 km away with a spyglass; the PLZ-05, driven up a minute before, takes
  the mark, the gunner engages the fire control, and the third round lands on the camp. The crew drives off before
  the smoke clears.
- **Not the MBT** (later in the port): the PLZ-05 is thin-skinned against tank guns, slow to traverse and fires only
  from a halt. Direct fire is a last resort.

## Real-world reference

- **Class:** PLZ-05 155 mm/52 tracked self-propelled howitzer (China, 2005).
- **Proportions:** hull about 7.3 m × 3.4 m, 3.3 m to the turret roof, about 11 m long with the gun forward.
  SBW's model is about 1.3× real: **scale 0.77**.
- **Gun:** elevation −3° to +68° (SBW: −3° to 65°), full traverse. Autoloader, 8 to 10 rounds a minute.
- **Mobility:** about 56 km/h on roads, a 12-cylinder diesel, crew of five.
- **Roof:** a 12.7 mm QJC-88 machine gun on a ring at the commander's hatch.
- **Moving parts:** `turret` (traverse), `barrel` (elevation), `tube` (recoils about 0.9 m), the track links on
  each side (they run over the road wheels), `sprocket_*`, `idler_*`, `road_wheel_*`, `return_roller_*` (turn with
  the tracks), `hmg_mount` (yaw) and `hmg` (pitch) on the roof (added by the refit).

## Controls

| Seat | Key | Action |
| --- | --- | --- |
| Driver (0) | W / S, A / D | drive; the tracks pivot it on the spot |
| Gunner (1, in the turret) | Mouse | lay the gun by the view (the turret slews after it: 2° and 1.5° a tick) |
| | N | engage or release the fire control: it lays the gun on the mission and holds it there |
| | V | next charge and arc: 1 low, 1 high, 2 low, 2 high, 3 low, 3 high |
| | X / Z | next / previous shell (HE, AP, cluster, grapeshot) |
| | Left button | fire (only at a halt, and loaded) |
| | R | reload from the hold (the autoloader also loads itself) |
| Roof gunner (2) | Mouse, left button, R | the 12.7 mm gun |
| Rider (3) | | a hand weapon |
| Anyone | change seat (B) | move to the next free seat (one player can drive, then take the gun) |
| Observer | spyglass + attack | mark a target for every fire-mission gun within 6.5 km |
| Gunner | `/justEnoughGuns firemission <x> <z>` | set the target by coordinates |

## Behaviour

- **Charges** (gravity 0.06, SBW's): charge 1 at 10 blocks a tick (about 1,700 blocks at 45°), charge 2 at 14
  (about 3,300), charge 3 at 19 (about 6,000). `FireSolution` gives the low and high arcs for the selected charge, the
  same solver as the howitzer's.
- **Fire control:** engaged, the turret's slew target is the solution (bearing and the chosen arc) instead of the
  gunner's view; it shows "LAYING" until it is within 0.2° on both, then "ON". Moving the hull re-solves from where the
  trunnions now are. Without a mission, or out of reach of the charge, it won't engage and says why.
- **Fires only at a halt:** above 0.05 blocks a tick the gun won't fire (the panel says "HALT").
- **Shells:** the Mk42's, with their warheads and the shared shell path (`ShellEntity` on `FarFlight`), one in the
  breech, 6 s to load.
- **Observer marks** reach every fire-mission gun (the howitzer and the PLZ-05) within reach of the observer.
- **12.7 mm:** aimed by the roof gunner's view, about 600 rounds a minute, long-range rounds.

## Vehicle data

- **Seats:** driver front left, the engine beside the driver on the right (enclosed, hidden), gunner in the turret (enclosed, hidden, camera at the sight on the
  turret), roof gunner at the commander's hatch (open, turns with the turret), rider at the other hatch.
- **Engine:** tracked; heavier than the BMP-2 (slower to accelerate, top speed about 0.6 blocks a tick).
- **Armour:** proof against rifle rounds and splinters; the 30 mm and anti-tank weapons go through.

## Hitbox

From `refit.py`: hull and side skirts (`body`), tracks (`wheel_left`/`wheel_right`), the engine at the front left
(`main_engine`), the turret (`turret`, `follows: turret`), the gun (`turret`, `follows: barrel`).

## Pack interplay

| Mod | How it cooperates |
| --- | --- |
| Carry On | `jeg:plz05` on the entity blacklist |
| Claim mods | block damage through the shared warhead rules |
| Create Big Cannons | different role (a crewed vehicle that moves, against block-built fixed guns) |

## Sound

SBW's: engine loop, the report (near, far and very far), the crew's report and the loader. **To be checked by ear.**
