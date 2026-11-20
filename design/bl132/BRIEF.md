# BL-132 130 mm/58 four-gun mount brief

Status: **built and tested, not yet handed over, 2026-11-16.** Second of the SBW fixed guns, built on the Mk42's
systems while the user was away; the defaults taken are listed at the end for review. Model refit:
[refit.py](refit.py), [rig.json](rig.json). What was played and tested: [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md).

- **Method:** [vehicle-development-guide.md](../../docs/vehicle-development-guide.md)
- **Peer:** the [Mk42](../mk42/BRIEF.md): same shells, controls, fire control and shared code
- **Source:** Superb Warfare's `bl_132` (model, texture, numbers, sounds), used with the SBW team's permission.

## Purpose

- **What the player uses it for:** the heavy base gun. Where the Mk42 keeps up a steady 40 rounds a minute, the
  BL-132 puts four 130 mm rounds on a target in under a second, then reloads for 4.5 s: a salvo that kills an enemy
  BMP-2 or breaks up a horde in one go, and flak against aircraft.
- **Not CBC, not the CIWS:** as for the Mk42.

## Real-world reference

- **Class:** SBW's own design: two tiers of twin 130 mm/58 guns in one house. 130 mm/58 is the calibre of the
  Soviet SM-2-1 twin mount; a four-gun mount of it never existed. Kept by the triage (accepted by the user) as a
  fictional but plausible heavy mount in the same style as the rest of the SBW set.
- **Proportions:** house 7.25 wide × 8.4 long × 4.5 high blocks (sight tower to 4.8), guns 7.54 blocks from the
  trunnions to the muzzle, the real 130 mm/58 bore. **Scale 1.0** (triage's ~0.82 measured the breech behind the
  trunnions too; the Mk42 is held to the same trunnion-to-muzzle yardstick).
- **Moving parts:** `turret` (traverse, ±180°), `barrel` (all four guns elevate together, -5° to 85°), `gun_N_slide`
  (each gun's chase slides 11 px back as it fires and runs out over 10 ticks, from the synced shot count).
- **Rates:** SBW's: 300 rounds a minute, four in the drum, 90 ticks to reload; traverse 25°/s, elevation 40°/s.

## Controls

The Mk42's: look to aim (the mount slews after the view; a bore marker shows where the guns point), hold the left
button to fire the salvo, R reloads, weapon keys pick HE / AP / cluster / grapeshot. The camera sits on top of the
director's sight tower, between the guns.

## Vehicle data

One gunner (enclosed, hidden); static; `max_health` 400 (SBW); hold `small` (27 slots: the four gun drums draw from
it). Heavier armour than the Mk42 (rating 7).

## Hitbox

From `refit.py`: base ring (hull); gunhouse and sight tower (`follows: turret`, part `body`); the four guns outside
the house (`follows: barrel`, part `turret`, so only hits on the guns jam them). The ladders on the house sides,
0.15 blocks proud, are left out.

## Weapons (the Mk42's 5-inch shells, SBW's 130 mm numbers)

| Shell | Direct | Blast | Notes |
| --- | --- | --- | --- |
| HE | 250 | 160 within 11, blocks power 6.5 | proximity fuze 4 blocks, armed after 10 ticks |
| AP | 470 | 125 within 3.7 | |
| Cluster | 360 (whole) | 220 within 7 | 18 bomblets of 120 direct, 61 within 3.5 |
| Grapeshot (incendiary since 2026-11-19: bursts 8 up, 36 fragments of 14 over 6 blocks) | 1,100 / 36 per pellet | none | 36 pellets at the muzzle |

The four guns fire in SBW's order: lower left, lower right, upper left, upper right. Every shell is a dud within its
blast radius (+1) of the gun (shared with the Mk42).

## Sound

SBW's `bl_132_fire_3p` (3 takes) outside, `bl_132_fire_1p` (3 takes) for the gunner, the Mk42's distant report (as
SBW), `bl_132_reload` (3 takes) for the reload. Loudness 3.0 (above the Mk42's 2.8). **To be checked by ear.**

## Decisions taken by default (2026-11-16), for review

1. **Kept a fictional four-gun mount** as triage decided, rather than remodelling it as a real twin SM-2-1.
2. **Scale 1.0**, not triage's ~0.82 (reason above).
3. **Shells shared with the Mk42** (SBW does the same), so one ammunition supply serves both guns.
4. **SBW's numbers** for damage, rate and reload.
