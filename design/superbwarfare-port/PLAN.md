# Superb Warfare port: vehicles and artillery

Status: scope and triage decided 2026-11-15 ([triage/TRIAGE.md](triage/TRIAGE.md)). Next: step 2, shared tools.
Open: the Create Big Cannons overlap and the permission's coverage (see the end).

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

## Scope (decided 2026-11-15)

**Vehicles only.** Standalone explosives (mines, grenades, bombs, the Mk-82) are out. The rounds each vehicle fires
come across as part of that vehicle's weapons: their behaviour (fuzes, blast, guidance, far flight), drawn by jegn's
projectile renderers. The mortar shell is the one round model kept: it is loaded by hand and seen up close.

| Vehicle | Verdict | Model work |
| --- | --- | --- |
| Mortar | keep | rename pinyin bones; `rig.json` |
| TOW launcher | refit | name 16 generic bones; `rig.json` |
| Mk42 5"/54 (fixed gun, air and ground) | keep | `rig.json`; LODs exist |
| BL-132 130 mm/58 (fixed gun, air and ground) | refit | scale ~0.82; names; LODs |
| Type-63 107 mm MLRS (towed) | refit | scale ~0.78; name 100 generic bones |
| PLZ-05 155 mm SPH | refit | scale ~0.77; names |
| Main battle tank (from the YX-100) | **remodel** | new generated model of a real MBT; keeps the YX-100's behaviour (main gun, coax, weapon station) and SBW's track driver |

Out: Mle1934 (dropped at triage), all standalone explosives, Prism Tank (a Command & Conquer unit), Annihilator and
swarm drone (sci-fi), wheelchair and "Beast Senpai" (memes), mounting guns on Create-built ships.

**Animations** are those of the vehicles in scope: SBW's state-driven bones (turret, barrel, tracks, wheels, recoil
shake, weapon station) and the `fire` clips as one-shots. The new MBT gets its own.

Already in jegn (see [vehicle_sw_alignment_mapping.md](../../docs/vehicle_sw_alignment_mapping.md)): A-10, AH-6,
BMP-2, HPJ-11, laser tower, LAV-150, Mi-28, speedboat, TOM-6, truck, waveforce tower, C4, Claymore, TM-62,
Javelin, Igla, AGM-65, Kh-39, FPV drone.

Check against the pack before building: Create Big Cannons already does artillery, and the fleet plan turns the
waveforce tower into a towed howitzer. Use the `yftm` tools to see what CBC offers on the server, and reshape
anything that duplicates it.

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
  reproducible (the guide's "generator, not hand edits" rule). The MBT is generated from scratch, like the bulldozer.
- **Sound:** the guide asks for CC0 field recordings with `CREDITS.md`. SBW sound can now be used, but the far,
  distant, start-up and interior layers still have to meet the guide. Watch the stream pool (static loops only).

New shared systems, each built once:

1. **Indirect-fire solver:** range table and solver, a rangefinder or impact-indicator HUD, and shells on the
   long-range projectile path. Used by the mortar, the MLRS, the PLZ-05 and the waveforce howitzer.
2. **Salvo sequencing** for the Type-63.
3. **Wire guidance** for the TOW (gunner steers until impact; the wire breaks if the gunner dismounts or LOS is lost).
4. **Tracked chassis with turret and hull**, plus armour zones. Shared by the MBT, the PLZ-05 and the BMP-2 refit.

## Approach: triage first, then port each one finished

Don't bulk-port raw assets and assess them afterwards:
- The 11 vehicles already ported that way are why the fleet plan exists.
- Raw port work (registration, data, renderers) would be redone during normalisation.
- Anything registered ships in the next `yftm_build`.

1. **Triage, offline:** done ([triage/TRIAGE.md](triage/TRIAGE.md)).
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

1. **Mortar:** the simplest model and a clean rig. It builds the indirect-fire solver and HUD, and is the first use
   of `refit.py`.
2. **Mk42**, then **BL-132:** fixed guns on the same turret and barrel bones; base air defence.
3. **TOW launcher:** wire guidance.
4. **Type-63 MLRS:** salvo, wheels; then the towed howitzer (waveforce repurpose in the fleet plan).
5. **PLZ-05**, then the **MBT:** after the bone driver's track paths and the BMP-2 tracked-chassis refit.

Every vehicle needs:

- a `design/<vehicle>/BRIEF.md` from the template;
- the Carry On blacklist tag;
- far-flight for every round it fires;
- cross-vehicle fixes written back into the guide.

## Testing

- One focused game test per vehicle feature, for example `MortarIndirectFire`, `Mk42AirDefence`, `TowWireGuide`,
  `Type63Salvo`, `Plz05Tracks`.
- Every weapon and round is fired at full range (the yftm.13 lesson).
- Every run uses `-PwithPack`. Check first that the user's Minecraft client is closed, and ask if it's running.
- Check that each test class actually logged (the bulldozer server test ends the JVM).
- The release report says what the pack run covered, and that it isn't the dedicated server.

## Sequencing with the fleet plan

The fleet plan is waiting on the user's go-ahead. Build steps 4 and 5 overlap with it (waveforce howitzer, BMP-2
chassis), so schedule both plans together rather than running them separately.

## Open questions

1. Mortar, MLRS and PLZ-05 vs. Create Big Cannons: what does CBC already give players on the server, and does any
   of these need reshaping?
2. Does the SBW permission cover public redistribution (GitHub releases, the server pack), and what attribution do
   they want?
