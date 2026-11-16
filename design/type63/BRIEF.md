# Type-63 rocket launcher brief

Status: **built and tested, not yet handed over, 2026-11-16** (built while the user was away; defaults at the end for
review). Model refit: [refit.py](refit.py), [rig.json](rig.json).

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Peers:** the [Mk42](../mk42/BRIEF.md) and [BL-132](../bl132/BRIEF.md) (the same shell-gun path), the
  [mortar](../mortar/BRIEF.md) (indirect fire, the range panel)
- **Source:** Superb Warfare's `type_63` (model, texture, sounds, numbers), used with the SBW team's permission.

## Purpose

- **What the player uses it for:** area fire. A base lays it on a road or an enemy camp 1–2 km out and drops a
  12-rocket ripple across it; the cluster round covers a wider area, the AP round kills a vehicle it hits.
- **Success:** load twelve from the crate, read the range off the panel, and ripple them onto a point 1,500 blocks
  away.

## Real-world reference

- **Class:** Chinese Type 63 107 mm towed multiple rocket launcher: twelve tubes in three rows of four on a
  two-wheeled carriage with split trails. SBW's model is about 1.3× real, so **scale 0.78**.
- **Laying:** the tube pack traverses about 16° either side and elevates to about 57°, by handwheels; a full ripple
  takes about 7–9 s.
- **Moving parts:** `turret` (traverse), `barrel` (the tube pack), `rocket_1`–`rocket_12` (the rockets, hidden as
  each tube fires and shown again on reload), the handwheels.

## Controls (the vehicle-gun family)

| Key | Action |
| --- | --- |
| Right-click it | crouch at the handwheels as gunner |
| Mouse | lay the tube pack (it follows the view at the handwheels' pace, 0.5° a tick), 15° either side of the carriage |
| A / D | drag the carriage round (1° a tick) to lay further off |
| Left button, held | ripple: the loaded tubes fire in order, 14 ticks apart |
| 1 / 2 / 3 | HE, AP, cluster |
| R | load all twelve tubes from the crate (30 s) |
| Inventory key, seated | open the crate (9 slots) to put rockets in |
| FSV-6 driver's N, with it empty behind the truck | hitch it by the trail and tow it |

## Behaviour

- **Rockets fly as shells** (`ShellEntity`, flagged as rockets: a motor glow and a short trail as they leave):
  10 blocks a tick, gravity 0.05, about 2,000 blocks at 45°, on the far-flight path. The range panel
  (`ShellGunAim`) predicts the fall; SBW's spread (0.5) scatters a ripple around that point.
- **Warheads (SBW's):** HE 200 direct and 200 within 12 blocks, blocks as power 6; AP 500 on what it strikes, 100
  within 5; cluster breaks into 12 bomblets (150 direct, 125 within 3) when it is 7 ticks from a block or target.
- **Firing order:** top row first, left to right, each rocket from its own tube; the tubes are reloaded together.

## Hitbox

From `refit.py`: carriage and trails (hull), wheels, the cradle (`follows: turret`), the tube pack
(`follows: barrel`, part `turret`).

## Sound

SBW's four launch sounds and its tube-loading sound. **To be checked by ear**, especially a full ripple at range.

## Decisions taken by default (2026-11-16), for review

1. **One gunner seated at the handwheels**, on the shared vehicle-gun path. SBW's launcher has no seat: it is loaded
   tube by tube by hand and fired with a separate control.
2. **Built at the vehicle workbench** (24 iron, 8 copper, 6 redstone), and **towed by the FSV-6** trail first: its tow
   eye is at the trail's end (`Type63Entity.TOW_EYE`, the shared tow-eye hook added with the howitzer). A/D swings the
   carriage round where it stands.
3. **All twelve tubes reload at once from the crate**, 30 s, rather than SBW's one tube at a time by hand.
4. **SBW's range and damage** (about 2,000 blocks; the real rocket reaches 8 km).
