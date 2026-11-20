# SBW triage: what to keep, refit or remodel

Step 1 of [the port plan](../PLAN.md). Each SBW model is rendered offline with its own texture and measured against
the real machine. Nothing here is in the mod yet.

**Decided 2026-11-15:**
- The proposed verdicts are accepted, **for vehicles only**: standalone explosives are out of scope (the
  explosives section below is kept for reference).
- YX-100: **refit**, kept as a deliberate science-fiction exception (user, 2026-11-17, `design/yx100/`). It was first
  remodelled as a Leopard 2A7, which the user reverted the same day.
- Mle1934: **drop**.

- **keep:** port the geometry as is (plus `rig.json` and a generated hitbox).
- **refit:** keep the geometry; a `refit.py` rescales, renames bones and fixes it.
- **remodel:** new generated model; only SBW's behaviour and data come across.
- **drop:** leave it out.

## How these were made

[render_geo.py](render_geo.py) bakes the geometry the way GeckoLib does and draws front, side, top and
three-quarter orthographic views. The red box is a standing player (0.6 × 1.8 blocks), and the bar is one block.
Faces with a fully transparent texture (helper points such as the YX-100's sight marker 20 blocks ahead of the
barrel) are left out of the renders and the measurements. Measurements are in [measurements.jsonl](measurements.jsonl).

Assets: SBW Fabric `0.8.8-1.21-Fabric`, `src/main/resources/assets/superbwarfare/{geo,textures/entity}/<id>…`,
downloaded to a scratch folder (not committed). To rerun:

```
python -I design/superbwarfare-port/triage/render_geo.py <geo.json> <texture.png> <out.png> --title "…" [--barrel-bone paotong]
```

**Barrel length** is the barrel bone's extent along its own axis, so it includes the breech inside the gun house. A
1:1 model reads slightly over the bore length (calibre × calibres). Real-world figures marked ~ are approximate and
must be confirmed in each vehicle's brief.

## Vehicles and guns

| Model | Sheet | Measured (blocks) | Real reference | Scale | Texture | Bones / cubes, generic names | Driven bones | LODs | Clips | Proposed |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Mortar | [mortar.png](mortar.png) | tube 1.56 (incl. shell and fire-control screen); lies flat at rest, raised by code | 81 mm tube ~1.27 m | ~1:1 | 64² | 9 / 104; names in pinyin (`paotong`, `jiaojia`, `dizuo`) | none (pitch is posed by code) | 1 | idle, fire | **keep** |
| TOW | [tow.png](tow.png) | 1.09 × 1.39 × 1.69, tube 1.70 | BGM-71 missile 1.17 m; tube ~ to confirm | ~1:1 | 64² | 25 / 133; 16 generic | turret, barrel, base | none | none | **refit** (names, rig) |
| Mk42 5"/54 | [mk_42.png](mk_42.png) | 4.14 × 4.36 × 9.71, barrel 7.42 | bore 6.86 m | 1.08 | 128² | 52 / 347; 1 generic | turret, barrel | 2 | idle, fire | **keep** |
| Mle1934 138.6 mm/50 (twin) | [mle_1934.png](mle_1934.png) | 5.65 × 3.06 × 11.55, barrel 6.39 | bore 6.93 m | 0.92 | 128², mostly flat grey | 58 / 236; 2 generic | turret, barrel | 1 | idle, fire L/R | **refit + retexture**, or **drop** (only elevates to 30°, overlaps the Mk42) |
| BL-132 130 mm/58 (twin, two tiers) | [bl_132.png](bl_132.png) | 7.54 × 4.81 × 11.78, barrel 9.20 | bore 7.54 m | ~1.2 | 256² | 69 / 348; 15 generic | turret, barrel | none | idle, fire ×4 | **refit** (scale ~0.82, LODs) |
| Type-63 107 mm MLRS (towed, 12 tubes) | [type_63.png](type_63.png) | 2.08 × 1.47 × 3.30 incl. trail | ~1.6 wide × 1.1 high | ~1.3 | 256² | 158 / 675; **100 generic** | turret, barrel, wheels | 2 | none | **refit** (scale ~0.78, names) |
| YX-100 MBT | [yx_100.png](yx_100.png) | 5.09 × 6.16 (antenna) × 11.49, barrel 7.50 | no real tank: a fictional futuristic design. Real MBTs ~3.6 wide | ~1.4 | **1024²** (16× a 256² texture) | 325 / 735; 22 generic | tracks (189), coax, weapon station | 1 | idle, fire, coax, weapon station | **decide**: see below |
| PLZ-05 155 mm SPH | [plz_05.png](plz_05.png) | 4.45 × 7.15 (antenna) × 14.42, barrel 11.15 | 155 mm/54 bore 8.37 m | ~1.3 | 256² | 376 / 1079; 48 generic | tracks (252), turret, barrel | 1 | idle, shoot | **refit** (scale ~0.77) |

### Notes

- **The realistic models share one style.** Mortar, TOW, Mk42, BL-132, Type-63 and PLZ-05: moderately detailed,
  muted, lightly weathered. They sit well beside the ported fleet, which came from the same team. PLZ-05 (digital
  camo, full running gear) and Mk42 are the best of the set.
- **Scale runs large.** SBW builds ground vehicles at about 1.2–1.4× real. This matches the BMP-2 (1.37×) found by
  the fleet plan. The guide wants 1:1, so most refits start with a rescale. The guns aimed by players (mortar, TOW,
  Mk42) are already about 1:1.
- **Mle1934 is the weakest model.** It is mostly flat untextured grey across an 11-block gun house. It only
  elevates to 30°, so it can't do the Mk42's air-defence job.
- **YX-100 breaks rule 1 of "done"** ("it looks like the real machine"). Its white and blue panels and cyan glow
  strips are science fiction, unlike the rest of the fleet, and its 1024² texture costs 16× the memory of the
  others'. Its running gear and SBW's track driver are still worth having. Options:
  1. **Remodel as a real MBT** (a generated model, e.g. a Type 99A or Leopard 2 class). Reuse the YX-100's
     behaviour, coax and weapon station, and the track driver.
  2. **Keep it as a deliberate sci-fi exception.** The guide would need to allow it.
  3. **Drop it.**

  Recommended: **1**.

## Explosives and projectiles

[explosives.png](explosives.png). One three-quarter view each, sizes in metres (blocks).

| Model | Measured | Real | Proposed |
| --- | --- | --- | --- |
| BLU-43 | 0.16 × 0.02 × 0.08 | ~1:1 | **keep** |
| PTKM-1R | 0.24 × 0.71 × 0.27 | ~1:1 | **keep** |
| Mortar shell | 0.13 × 0.59 | 81 mm round ~0.5–0.6 m | **keep** |
| Mk-82 | 4.55 long | 2.21 m | **refit** (scale 0.5) |
| Lunge mine | 2.38 × 1.44 × 2.69 incl. pole | pole ~1.5–2 m | **check in game** (parts of the pole render transparent offline) |
| Hand grenade (M67) | 0.16 | 0.064 | keep only if JEG lacks one; it's a blocky sphere at 2.5× |
| RGO grenade | renders as loose fragments offline | | **check in game** before deciding |
| RPG rockets (standard, TBG) | 2.24, 2.79 long | PG-7V ~0.95 m | **don't port the model**; oversized for visibility in flight |
| Cannon shell | 0.6 wide | 130–155 mm | **don't port the model** (flight visual only) |
| Small cannon shell | an orange cube | | **don't port** (placeholder) |
| Small rocket | 2.13 long | Hydra 70 ~1.06 m | **don't port the model** |
| Medium rocket, wire-guided missile | grey: their textures live under other names | | find the textures, then decide |
| M18 smoke grenade | no texture found under its name | | find the texture, then decide |

For projectiles in flight, jegn's own renderers and far-flight tracers matter more than the model. Port the
behaviour (fuzes, blast, guidance), and use SBW models only where a round is seen up close or at rest.

## Decisions (2026-11-15)

1. Proposed verdicts accepted; vehicles only, no standalone explosives.
2. YX-100: remodel as a real MBT; reverted the same day to option 2, a refit of SBW's model.
3. Mle1934: drop.
