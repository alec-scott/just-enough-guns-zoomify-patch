# BD-40 implementation status

Revision 05, incorporated into build 19 at the user's request on 15 November 2026. The existing build 19 tag and
release JAR are updated together; `yftm_build` remains 19. See [release verification](../../docs/yftm-19-verification.md).

## Verified on 14 November 2026

`BulldozerClientGameTest` passed on a dedicated test server with `-PwithPack` (Minecraft 26.1.2, loader 0.20.1):
the pack's render, animation, chunk and sound mods, Iris with the Complementary shader pack, and FallingTree 25 at the
server's own config. It was run twice more: once with FallingTree excluded (`-PpackExclude=fallingtree`), to cover
the dozer's own feller, and once as a final confirmation. All runs log `[bulldozer] COMPLETE`.

The test drives through the real keys and checks:

- **Assembly and start-up:** assembly at the vehicle table and deployment from the container; the diesel start and
  the seven sound layers.
- **Operator:** not hidden; can look 170° behind with the first-person camera following (camera yaw 170°).
- **Blade:** the rendered cutting edge and lift-cylinder rod match the server's work geometry at grade, fully
  raised and dug in. Lift limits (−4° to 30°), the grade lock, and Shift lowering the blade without dismounting.
- **Driving:** top speed on flat ground 0.169 blocks/tick (12 km/h). A stationary pivot turns with track sound.
  Released controls hold the machine still.
- **Clearing a bank:** a mixed bank of soil, stone and deepslate, then a six-high bank, is cleared completely
  without the dozer climbing it or digging the ground. Material holds the machine back while it is cut. A separate
  overhang is left. The hold receives 8 cobblestone, 4 cobbled deepslate and the dirt.
- **Hardness and blocking:** iron blocks (hardness 5) are cleared and kept. Obsidian and a chest stop the blade
  before it enters them.
- **Trees:** an oak is felled whole, with no logs left: by FallingTree in the pack, by the dozer without it. The
  wood ends up in the hold.
- **Protection and travel:** a denied `PlayerBlockBreakEvents.BEFORE` leaves terrain intact and shows the
  protected status. A raised blade does no work.
- **Full hold:** with every slot full, clearing continues; dirt is discarded and iron blocks are dropped, not lost.
- **Lights and persistence:**
  - Work lights light the ground at night and switch off by day.
  - First-person audio is centred; the current revision preserves the loops when the view changes.
  - Blade state survives save and load.
  - The engine shuts down after the operator leaves, and its audio stops.

Follow-up on 14 November 2026, after a hand test:

- **Silence in third person:** the dozer's 7 loops were streamed, and Minecraft has about 8 streaming channels, so
  loops restarted on a camera switch got no channel. They are now static. `BulldozerAudioClientGameTest` reads each
  loop's OpenAL source and confirms all of them play in third person, first person and after switching back.
- **Night view from the seat:** the floating searchlight lens and lamp glare were removed. The light set is marked
  `openSeat`, and the lens moved to the guard lamps. Checked with first-person night screenshots.
- **Digging one block down:** lowering the blade below grade locks the cut one block under the machine and holds
  it. Tested: a flat trench exactly one level deep, with the layer beneath intact.

Round three (14 November 2026), bringing the dozer up to the vehicle guide:

- **Fitted hitbox.**
  - Ten collision boxes are generated from the model: each track with its fender, the frame, the engine, the
    canopy, the tanks, the ripper, the blade and each push arm. The blade is now solid.
  - Shots use them (`preciseHitbox`), and entity pushing uses them too.
  - `BulldozerHitboxClientGameTest` checks two things. The boxes match `rig.json` and stay within the model. A real
    assault rifle hits the blade, both tracks, the engine and the canopy, while rounds through the empty space above
    the hood and beside the canopy miss.
  - The main test drives under leaves 3 blocks up untouched.
- **Sound travel.** The sound now follows the aircraft rules:
  - the shared distance curve and the distant crossfade
  - server tracks beyond entity range, with the horn as the far signal
  - speed-of-sound delay and Doppler

  `BulldozerFarSoundClientGameTest` (pack) measured 0.68 at 30 blocks, 0.22 at 180, then 0.14 from tracks at 260
  with no jump, 0.031 at 800, and silence at 1100. The horn was 0.24 at 600 blocks.
- **Ripper.** R lowers it, and it lays blocks from the hold's last row (the nine-slot Ripper bay) over the same
  columns the blade clears: five off the grid, four exactly on it. Replaced ground goes into ordinary cargo;
  holes are filled. The original feature test covered a filled hole and nine paved rows; the focused lane test
  checks eight fully reached rows in both grid alignments.
- **Horn.** A dual-tone electric horn while the left mouse button is held. Tested on and off.
- **Regression tests for the shared code:** `HitMarker` and `AircraftFarSound` pass.

These regression tests also passed with the pack, because the change touches shared vehicle input and movement
code: `VehicleInputCompat`, `VehicleSneakDismount`, `VehiclePaint`, `VehicleAssemblingScreen`.

Revision 05 (14 November 2026), following the moving first-person audio and mirror report:

- **Moving first-person sound:** loops and one-shots now use listener coordinates at zero in first person.
  `DynamicRelativeSound` and `VehicleSoundEngineMixin` update the existing channel's relative flag together with
  its position on the sound thread. There is no tick-late world source drifting behind the camera, and changing
  views keeps the same buffers, source IDs and operator mix.
- **Audio verification:** `BulldozerAudioClientGameTest` passed with the pack, Sound Physics, Iris and Complementary
  shaders. The dozer drove over nine blocks while looking left/right at 90 and 150 degrees; all eight channels
  remained playing, relative and at zero during 48 per-tick checks. Third person restored world positioning with
  identical source IDs. The final run logged `[dozer-audio] COMPLETE` with no OpenAL errors.
  The test now queries OpenAL on its owning thread and no longer reads EFX's write-only direct-filter property.
- **Exterior regression:** a separate pack run of `BulldozerFarSoundClientGameTest` passed. The engine remained
  audible at 800 blocks (0.031), fell silent at 1100, and the horn reached 600 blocks (0.244), with a smooth handover
  from entity sounds to distant server tracks. Both focused runs used Minecraft 26.1.2 and loader 0.20.1.
- **Mirror:** a taller chamfered housing, rubber bezel, continuous blue-silver glass finish and compact clamped
  mount. The head sits toward the operator, with both post and arm behind the glass. Its normal bisects the
  directions to the seat and a rearward scene; the glass uses an authored texture rather than a live camera.
  `verify_preview.py` checks the angle and the sightline from every glass corner. Pack-client screenshots confirm
  the complete face is visible: [seated glance](in-game-mirror.png), [forward view](in-game-mirror-forward.png).
- **Generated assets:** regenerated the near model, both LODs, atlas, paint metadata, rig, Blockbench project,
  selection icon and offline preview. Linkage, UV, hierarchy and closed-track checks pass.

Claude handoff completed on 15 November 2026 (ripper lane, HUD and hold screen):

- **Lane width:** retained the five-column off-grid / four-column on-grid footprint. The focused test checks
  rows 0–7, which the machine fully reaches; it does not mistake an unreached row for a missing lane.
- **HUD:** the two-column panel uses measured text widths and whole screen pixels per font pixel. The test now
  runs in a 1280×900 window and asserts actual GUI scale 3, then checks scale 2 as well. The HUD receives and shows
  the total across multiple bay stacks (280 Dirt in the fixture), with the early control hints fading away.
- **Bay and player inventory:** all nine last-row slots are tool slots accepting full blocks and rejecting slabs
  and non-block items. The one-pixel amber outline leaves the row above's stack counts clear. A dedicated heading
  strip below the Ripper bay tab fixes the remaining clipped "Inventory" label; player slots, hotbar and screen
  height move together. The test also verifies that an ordinary vehicle menu retains its original layout.
- **Verification:** `BulldozerRipperLaneClientGameTest` passed with `-PwithPack`, Minecraft 26.1.2, Fabric API
  0.155.3 and loader 0.20.1, with Iris / Complementary and the pack's UI mods. Both lane patterns passed for all
  eight checked rows, the menu/count assertions passed, and the final run logged `[dozer-ripper-lane] COMPLETE`.
  Compile checks and the build passed. This was a local singleplayer pack run, not the live dedicated server.
- **Saved review images:** [HUD at scale 3](in-game-ripper-hud.png), [bay at scale 3](in-game-ripper-bay.png),
  [bay at scale 2](in-game-ripper-bay-gui2.png), [five-column lane](in-game-ripper-lane-off-grid.png),
  [four-column lane](in-game-ripper-lane-on-grid.png).

## Not covered

- **Dedicated server mods:** the pack run uses the build's pack-mod subset on a local test server, not the yftm
  dedicated server. Claim mods there were not exercised beyond Fabric's break events.
- **Listening and play:**
  - This revision still needs a listening pass. Automated checks verify channel playback and positioning;
    levels come from measured loudness.
  - Multiple dozers, several players and long sessions have not been stress-tested.
- **Trees:** only small oaks were tested. Trees over FallingTree's `maxSize` (100 logs) fall to the dozer's own
  feller.
- **Painting:** the paint mask is generated (yellow panels only), but dyeing the dozer was not exercised in game.
