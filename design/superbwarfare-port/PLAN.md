# Superb Warfare port: vehicles, artillery, explosives and animations

Status: drafted 2026-11-15. Not started. Scope, animation picks and the Create Big Cannons overlap are still open
(see the end).

Source: [CrawKatt/SuperbWarfareReFabricated @ 0.8.8-1.21-Fabric](https://github.com/CrawKatt/SuperbWarfareReFabricated/tree/0.8.8-1.21-Fabric),
a Fabric 1.21 port of [Mercurows/SuperbWarfare](https://github.com/Mercurows/SuperbWarfare) (NeoForge 1.21.1).

## Licensing

- **Code** is GPL-3.0, the same as jegn. Ported files keep their notice, and a `NOTICE` entry credits the SBW team.
- **Assets** (models, textures, animations, sounds, icons) are All Rights Reserved upstream. The SBW team gave the
  user permission to use them (reported 2026-11-15). Before the first release that ships new SBW assets:
  - Keep a copy of the permission (who granted it, the date, and what it covers) in `design/superbwarfare-port/PERMISSION.md`.
  - Confirm it covers redistribution: this repo is public, and every `yftm_build` is a public GitHub release that
    reaches the server through the pack manager.
  - Rewrite the README credit line to match. It currently says SBW assets are CC BY-NC-SA 3.0, which conflicts with
    the ARR notice upstream.
  - Use the attribution wording the SBW team asked for, if they asked for any.

## Scope

Already in jegn (see [vehicle_sw_alignment_mapping.md](../../docs/vehicle_sw_alignment_mapping.md)): A-10, AH-6,
BMP-2, HPJ-11, laser tower, LAV-150, Mi-28, speedboat, TOM-6, truck, waveforce tower, C4, Claymore, TM-62,
Javelin, Igla, AGM-65, Kh-39, FPV drone.

| Group | Proposed in | Proposed out |
| --- | --- | --- |
| Artillery | Mortar, Type-63 107 mm MLRS, PLZ-05 self-propelled howitzer, TOW launcher; Mk42, Mle1934 and BL-132 as fixed ground guns (SBW already places them on land as `ArtilleryEntity`; there are no SBW ships). Mk42 and BL-132 elevate to 85°, so they double as base air defence; the Mle1934 tops out at 30° and is coastal or ground-only | Mounting guns on Create-built ships (entities don't ride Create contraptions) |
| Armour | YX-100 main battle tank | Prism Tank (a Command & Conquer unit), Annihilator (sci-fi) |
| Explosives | Mk-82 bomb, BLU-43, PTKM-1R, lunge mine, M67 and RGO grenades, M18 smoke, RPG rockets (standard, TBG), mortar shells, cannon shells, small and medium rockets, wire-guided missile, 9M336, flare decoy | Swarm drone, melon bomb (TOM-6 already has one) |
| Other | | Wheelchair, "Beast Senpai" (memes) |
| Animations | User to choose from: mortar, Mk42, Mle1934, BL-132, PLZ-05, YX-100, RPG, drone, lunge mine, PTKM-1R | |

Check against the pack before building: Create Big Cannons already does artillery, and the fleet plan turns the
waveforce tower into a towed howitzer. Use the `yftm` tools to see what CBC offers on the server, and drop or
reshape anything that duplicates it.

## How it comes across

SBW (NeoForge 1.21.1) → CrawKatt port (Fabric 1.21) → jegn (Fabric 26.1, GeckoLib). Translate SBW's behaviour into
jegn's existing systems instead of copying classes wholesale:

- **Vehicles** become `data/jeg/vehicles/<id>.json` plus `vehicle_assembly` recipes on `VehicleEntity` and its
  weapon stations. Add Java only for behaviour that data can't express.
- **Assets** follow the naming rules in the alignment mapping (strip underscores, `superbwarfare:` → `jeg:`, paths
  under `geo/entity/vehicle/`, `textures/entity/vehicle/`, `animations/entity/vehicle/`, LODs under `lod/`). Add a
  row per new vehicle to that mapping doc.
- **Animations:** SBW's `VehicleModel` already drives bones from synced state by name, which is what the guide asks
  for. jegn's `AbstractVehicleGeoRenderer` handles `turret` and `barrel` under the same names, so guns aim on
  arrival. It lacks the rest of SBW's driver: `base` recoil shake, `trackMov`/`trackRot` link paths, `wheelL`/`wheelR`
  and `passengerWeaponStationYaw`/`Pitch`. Port that once (step 2 below); it also serves the fleet plan's BMP-2
  refit. Replace SBW's hand-typed track paths (`Yx100Model.getBoneRotX`, about 30 lerp lines) with a path computed
  from the sprocket and idler positions in `rig.json`. Keep SBW `fire` clips only as one-shots started by a synced
  shot event; drop the `idle` clips.
- **Models:** keep SBW geometry where triage says so. Each kept model gets a refit script, so changes to it stay
  reproducible (the guide's "generator, not hand edits" rule).
- **Sound:** the guide asks for CC0 field recordings with `CREDITS.md`. SBW sound can now be used, but the far,
  distant, start-up and interior layers still have to meet the guide. Watch the stream pool (static loops only).

New shared systems, each built once:

1. **Indirect-fire solver:** range table and solver, a rangefinder or impact-indicator HUD, and shells on the
   long-range projectile path. Used by the mortar, the MLRS and both howitzers.
2. **Salvo sequencing** for the Type-63.
3. **Wire guidance** for the TOW (gunner steers until impact; the wire breaks if the gunner dismounts or LOS is lost).
4. **Tracked chassis with turret and hull**, plus armour zones. Shared with the BMP-2 refit in the fleet plan.
5. **Mine triggers:** pressure (BLU-43), tilt rod and top attack (PTKM-1R). TM-62's existing trigger is the base.

## Approach: triage first, then port each one finished

Don't bulk-port raw assets and assess them afterwards:
- The 11 vehicles already ported that way are why the fleet plan exists.
- Raw port work (registration, data, renderers) would be redone during normalisation.
- Anything registered ships in the next `yftm_build`.

1. **Triage, offline** ([triage/TRIAGE.md](triage/TRIAGE.md)). Render each SBW model from the front, side, top and
   three-quarter view with its own texture. Measure it against the real machine (barrel length from calibre ×
   calibres for guns, hull size for vehicles) and record bones, cubes, naming, texture size, LODs and clips. The user
   picks one verdict per model:
   - **keep:** port the geometry as is;
   - **refit:** keep the geometry; rescale, rename and fix it by script;
   - **remodel:** new generated model, SBW behaviour only.
2. **Shared tools, built once:**
   - **`design/<vehicle>/refit.py`:** loads the SBW geometry, applies scale, bone renames and z-fight fixes, and
     writes the GeckoLib geometry, LODs, `rig.json` and model-generated OBBs. Shared helpers go in
     `design/superbwarfare-port/refit_lib.py`. The fleet plan's BMP-2 and LAV-150 refits use the same tool.
   - **SBW's bone driver**, ported into `AbstractVehicleGeoRenderer`, with track paths taken from `rig.json`.
3. **Port one at a time, each finished:** brief, refit, data, focused tests with `-PwithPack`, sandbox world,
   player's checklist.
4. **Guide updates in the same change:**
   - an "imported model" path, where the refit script counts as the generator;
   - a rule allowing one-shot clips started by a synced event.

## Build order

1. **Explosives:** Mk-82 on the A-10 pylons, BLU-43, PTKM-1R, M18 smoke, then grenades and rockets where JEG lacks
   an equivalent. Small, self-contained, and they exercise the asset pipeline first.
2. **Mortar:** builds the indirect-fire solver and HUD.
3. **Type-63 MLRS**, then the towed howitzer (waveforce repurpose).
4. **TOW launcher.**
5. **YX-100 and PLZ-05**, after the BMP-2 tracked-chassis refit.
6. **Naval or coastal guns**, only if kept in scope.

Every vehicle needs:

- a `design/<vehicle>/BRIEF.md` from the template;
- the Carry On blacklist tag;
- far-flight for every round it fires;
- cross-vehicle fixes written back into the guide.

## Testing

- One focused game test per item, for example `Mk82Drop`, `Blu43Trigger`, `MortarIndirectFire`, `Type63Salvo`,
  `TowWireGuide`, `Yx100Turret`.
- Every weapon and round is fired at full range (the yftm.13 lesson).
- Every run uses `-PwithPack`. Check first that the user's Minecraft client is closed, and ask if it's running.
- Check that each test class actually logged (the bulldozer server test ends the JVM).
- The release report says what the pack run covered, and that it isn't the dedicated server.

## Sequencing with the fleet plan

The fleet plan is waiting on the user's go-ahead. Steps 3 and 5 overlap with it (waveforce howitzer, BMP-2
chassis), so schedule both plans together rather than running them separately.

## Open questions

1. Is the scope table right, including the exclusions?
2. Which animations count as "select"?
3. Mortar and howitzers vs. Create Big Cannons: build, reshape or drop?
4. Does the SBW permission cover public redistribution (GitHub releases, the server pack), and what attribution do
   they want?
