# Changelog

## 1.7.2+mc26.1.2.yftm.9 - 2026-11-08

### Fixed
- Switching helicopter or vehicle weapons no longer waits on the other weapon: each weapon reloads on its own (carrying on in the background while another is fired) and has its own fire delay. An empty weapon starts reloading as soon as someone aboard can fire it and there is ammunition.
- Hand-held gun tracers leave the gun's barrel on screen at any field of view, while sprinting, aiming or zoomed (the first-person gun is drawn at a fixed 70 degrees). A second, client-only tracer that started at the player's eyes is gone.
- Passengers firing their own guns from a flying helicopter: tracers leave the gun instead of a point behind the helicopter, and move on with it.
- Missile and rocket explosions close to a helicopter no longer cut out: smoke stays solid around the camera instead of thinning away or popping, fire and smoke no longer cut hard edges out of each other, and puffs fade into the ground instead of ending in straight lines. The white shock dome no longer sweeps over nearby helicopters as a flat sheet.
- Pilots and gunners holding fire no longer mine blocks or swing, eat, block or place with whatever is in their hand.
- Taking the controls of a helicopter that is airborne or still has its rotor turning restarts the engine at once instead of making a cold start.

### Changed
- Player gun spread is much tighter: 40% of the previous cone in every state (65% for shotguns, so pellets still spread). The crosshair matches. Mob gunners are unchanged.
- Helicopter weapons reload another 30% faster (0.448x the hand-held reload time).
- Explosion smoke is shaded across the cloud (a lit and a shadowed side) and stays upright as the view banks, so the column reads as solid. Debris leaves thin grey trails instead of thick dark arcs.
- Explosions are heard and shake the view when their shockwave reaches you, so a far blast is seen before it is heard, with a brief screen flash for close blasts in view.
- MI-28 gun tracers are back to their yftm.8 look.
- The AH-6's two miniguns fire alternately from each side, with a smaller muzzle flash.
- Dyed helicopters keep the original finish on rotors, glass, guns, rocket pods and landing gear.
- The MI-28 pilot's missile is renamed 9M-120F Thermobaric Missile.

## 1.7.2+mc26.1.2.yftm.8 - 2026-11-07

### Fixed
- Anchored the minigun's first-person hands to its handles, fixing the raised arm beside the gun.
- Kept the ammo HUD inside the current GUI viewport and measured the spacing between ammo and reserve text.
- Reduced minigun firing and spin volume, and centered the shooter's own audio so turning does not move it around the listener.
- Centered helicopter engine and gun audio for occupants, with quieter cockpit engine volume and positional playback for observers.
- Crossfaded helicopter startup into the flight loop over 1.5 seconds and smoothed RPM gain changes. Taking off and accelerating keep the same flight sound channel.
- Centered MI-28 barrel rotation on the bore in both model detail levels and aligned bullet/flash origins with the model's muzzle tip.
- Attached the MI-28 gunner camera to the barrel, with a stable view over the gun and an unobstructed zoomed view directly along the bore.
- Gun tracers and third-person muzzle flashes start at the gun's drawn muzzle instead of the shooter's eyes, for the shooter in first and third person, for everyone watching, and for passengers firing out of a helicopter. Tracers meet the bullet's true path where it lands, so aim is unchanged.
- Tracers run all the way to whatever stops them, including mobs and players, instead of vanishing a tick early.
- Parked helicopters no longer clank, bounce or lose health from resting on the ground, and no longer sound "Airspeed too low" when boarded.
- Banking vehicle cameras report their forward and left directions correctly (they were reversed while rolled).

### Added
- Right-click a vehicle with a dye to paint it. The body paint takes the dye's colour and keeps its shading, while markings, lights, seats and tyres stay as they are. A water bucket washes it off. Paint is saved and survives packing the vehicle into a crate.
- The MI-28 pilot's 9M-120 is now an incendiary missile: same warhead and the same anti-ground missile ammo as the gunner's, but it sets the ground and anyone caught in the blast alight.
- New missile and helicopter-rocket visuals: a modelled missile with nose, seeker band and swept fins, a white-hot motor glow and flame, and a thick smoke trail that billows and lingers.
- New missile explosions: a white flash, a billowing fireball that cools to soot, a shock bubble and a dust ring racing out across the ground, debris and embers, and a rising smoke column. They are visible up to 512 blocks away.
- In a helicopter, the reversed third-person view (F5 twice) is a wide-angle action camera on the tail boom, looking up the boom at the cabin and banking with the airframe.

### Changed
- Helicopter guns read as heavy rotary guns: thicker, longer, hotter tracers with a bright head, a stronger muzzle flare, and a dust-and-spark puff where each round lands, visible at any distance. Through the MI-28 gunner's zoomed sight, rounds can now be watched all the way to impact.
- Medium missiles break stone and brick far more easily, and large missiles flatten buildings: medium warheads meet 45% of a block's blast resistance, large ones 20%. Obsidian-class blocks are unaffected.
- Missiles fire again sooner: medium anti-ground every 2 seconds (was 5), anti-air 2.5 seconds (was 6), large 3 seconds (was 6), BMP-2 2.5 seconds (was 5).
- Vehicle weapons reload 20% faster again (0.64× their base reload time).
- Gunfire light is brighter and matches the muzzle flare: light level 12 for pistols up to 15 for shotguns, machine guns and launchers (was 8), placed at the muzzle.
- The AH-6 and MI-28 hold two more rows of cargo (45 slots).
- Helicopter rotors coast down after touchdown, for up to twelve seconds on the AH-6 and eighteen seconds on the MI-28. Rotor animation, engine pitch and volume follow the same declining speed.
- The MI-28 gun takes 1.5 seconds to spin up from rest before firing. Barrel rotation and mechanical audio accelerate together; releasing the trigger lets the barrels coast down.

## 1.7.2+mc26.1.2.yftm.7 - 2026-11-06

### Changed
- Helicopters stay off when boarded. Pressing Space starts an eight-second AH-6 or twelve-second MI-28 rotor wind-up; lift remains disabled until operating RPM.
- Replaced AH-6 and MI-28 startup/flight audio with the supplied Freesound recordings 410604 and 81454, using mono positional loops and relative-motion Doppler pitch.
- Used the selected 1:26–1:36 section for MI-28 flight audio and a 46-second AH-6 flight bed with a two-second equal-power crossfade to soften repetition.
- Set the MI-28 gun to the minigun firing loop and the AH-6 gun to a seamless A-10-derived firing loop at 80% amplitude.
- Reduced the MI-28 gun flash to 20% size, 50% opacity, and a one-tick lifetime.
- Increased 70 mm rocket direct/blast damage to 110/60 with a 7-block blast radius, and 80 mm rocket damage to 125/75 with a 9-block radius.
- Increased medium ground missiles to 240 vehicle/90 living damage over 10 blocks, and large ground missiles to 600 vehicle/110 living damage over 24 blocks.
- Replaced medium missile Eyes of Ender with redstone torches and the large missile's Eye of Ender with a redstone block, retaining the higher explosive/engine costs of larger missiles.
- Smoothed first-person gun sprint carry, movement sway, and weapon raising/lowering, with handling and movement speed based on weapon weight.
- Retuned helicopter coasting, braking, strafing, lift, and yaw inertia, with steadier cameras during stops and a flight ceiling above the world's build limit.
- Added hover assistance while crew remain aboard with the pilot seat empty.
- Added minigun barrel spin-up and spin-down, synchronized firing audio, muzzle flares, and brighter tracers.
- Improved helicopter weapon crosshair alignment, missile models, acceleration, exhaust trails, and explosion feedback.
- Added configurable helicopter gun damage to foliage and glass, with block-break permission checks.

### Fixed
- Kept gun arms on their animated grip poses during weapon changes and removed per-shot equip dips.
- Scoped arm-pose checks to the bone and render pass being drawn so unused fallback bones cannot hide the holding hand.
- Made vehicle assembly tables drop once and remove all remaining parts when broken.
- Accepted older vehicle input packets that omit the barrel-roll field.
- Sent vehicle aim updates before firing input so a newly fired round uses the matching crosshair point.
- Made helicopter client prediction check fuel before spinning the rotor.
- Enabled helicopter gun loop activation and gave firing loops an audible initial volume; added checks for actual sound-channel playback and stopping after trigger release.
- Separated Power damage checks from Punch and Flame effects and rejected damage samples with no bullet hits.

## 1.7.2 - 2026-06-24

### Changed
- Set the module version to `1.7.2`.

### Fixed
- Reworked ADS alignment for reflex, monocle, and holographic sights so their open-sight reticles use the Forge 1.20.1-derived scope camera position and each gun's scope mount transform.
- Kept telescopic sight and spyglass ADS corrections on their existing per-rifle path.
- Matched gun draw operation locks to each weapon's authored draw animation length, unlocking shoot, reload, inspect, melee, attachments, and ADS after 85% of the draw animation instead of a fixed delay.
- Preserved held fire and held action-key input through the draw unlock point while preventing released keys from firing late.

### Verification
- Passed `.\gradlew build`.

## 1.7.1 - 2026-06-17

### Added
- Restored the `jeg:repair_kit` recipe from the disabled recipe file while keeping the existing item ID.
- Added a repair kit tooltip explaining anvil repair support for guns and bulletproof armor.
- Added first-person held-gun left/right movement camera sway, separate from recoil handling.
- Added Y-key gun inspect animations for held animated guns, including tooltip guidance.

### Changed
- Increased Terror Phantom and drivable vehicle entity tracking range to 14 chunks so they can stay synced and visible out to roughly 224 blocks.
- Set the module version to `1.7.1`.
- Renamed the visible repair kit item to `Gun Repair Kit` / `枪械维修包` without changing the `jeg:repair_kit` registry ID.
- Changed gun anvil repair material and item `repairable(...)` properties from iron ingots to `jeg:repair_kit`.
- Made `jeg:repair_kit` the bulletproof armor anvil repair material and `repairable(...)` material.
- Converted targeted player-visible hardcoded strings to translation keys in vehicle assembly UI, attachment UI, gun overheating tooltip/message, Terror Raid bossbar, and command feedback.
- Reduced rocket and missile block damage and splash reach when directly hitting vehicles, while keeping direct vehicle hit damage intact.
- Increased aircraft no-input descent acceleration so powerless aircraft descend at roughly three times the previous terminal speed.
- Updated vehicle missile profiles, increasing ground-attack missile turn limits and retuning the 9M336 air-target missile turn limit.
- Increased helicopter maximum descent speed to `1.05D/tick` and made unmanned, unpowered, and critically damaged helicopters descend smoothly toward that speed.
- Slowed unmanned helicopter forced descent so falling from the old safe descent speed to max descent takes about 20 seconds.
- Changed unmanned, unpowered, and low-health helicopter forced descent back to a linear curve with 70% of the previous fast transition as the base step, and made low-health forced descent start at 20 health.

### Fixed
- Fixed Flamethrower reload timing so gameplay reload completion matches the authored 10.25 second first-person reload animation.
- Fixed Grenade Launcher reload animation so it uses the authored single reload clip instead of missing segmented reload loop/stop clips.
- Fixed repair kit behavior so it no longer repairs vehicles on right-click; repair-tool vehicle repair remains intact.
- Fixed helicopter and aircraft impact damage so controlled slow landings no longer explode from accumulated height alone, while uncontrolled high falls still deal crash damage.
- Restored helicopter rotor block-contact damage while airborne.
- Kept inspect playback from being interrupted by melee, draw, and normal sprint poses; shooting, reloading, and bayonet sprinting can interrupt it and replay starts from the beginning.
- Changed helicopter vertical crash damage to scale only with descent speed, keeping `0.35D/tick` landings safe and capping full-speed vertical impact damage near 70% of max health.
- Kept helicopter rotor motion and engine sound active while helicopters are descending in unmanned, unpowered, or low-health states.
- Let helicopter rotor speed wind down gradually after players leave, matching normal landed shutdown behavior.
- Restored fatal high-speed helicopter impact damage for block and vehicle collisions while leaving non-vehicle entity collisions unchanged.
- Played the missile warning alert for helicopter passengers during unsafe descent.
- Played the helicopter warning alert during forced descent even while the current descent speed is still within the safe-speed range.
- Prevented helicopter lift from holding forced-descent states above their target descent speed.
- Made helicopter forced descent accumulate from the tick-start vertical speed so air drag cannot cap the fall around a low terminal speed.
- Prevented active vehicle engines from continuing to move at very low energy without draining the remaining energy.
- Made missile and helicopter danger warnings flash red, increased helicopter warning volume, and added helicopter HUD warnings for low speed, power loss, and critical damage.
- Moved vehicle warning prompts to a flashing crosshair HUD message instead of the old passenger actionbar position.
- Stopped recovered helicopters from continuing low-health self-destruct decay after being repaired above the critical threshold.
- Prevented no-energy helicopters from spinning rotors or playing engine sound from pilot input alone.
- Prevented stopped helicopters from briefly twitching their rotors when the pilot exits.
- Smoothed fixed-wing no-pilot, no-energy, and critical-damage forced descent from the current vertical speed instead of snapping to maximum descent.
- Lowered the helicopter low-health self-destruct threshold to 20 health.
- Kept stopped helicopter rotors at zero when the pilot exits instead of spinning up briefly before winding down.
- Kept helicopter low-speed warnings from switching into the missile incoming warning while forced descent continues.

### Localization
- Expanded `zh_cn.json` to cover every key present in this branch's `en_us.json`.

### Verification
- Passed `.\gradlew compileJava compileClientJava clientClasses`.
- Passed `.\gradlew compileJava compileClientJava` after the vehicle direct-hit explosion tuning.
- Passed `.\gradlew compileJava compileClientJava` after the air vehicle impact/descent tuning.
- Passed `.\gradlew compileJava compileClientJava` after the gun animation fixes.
- Confirmed `repair_kit.json` parses and outputs `jeg:repair_kit`.
- Confirmed `zh_cn.json` has no missing keys relative to `en_us.json`.
- Full `build` and in-game gameplay checks were not run.
- Passed `.\gradlew compileJava compileClientJava`.
- Passed `.\gradlew compileJava compileClientJava` after the helicopter descent, crash damage, and feedback tuning.
- Passed `.\gradlew compileJava compileClientJava` after the helicopter rotor, descent, fatal impact, and warning updates.
- Passed `.\gradlew compileJava compileClientJava` after the exponential helicopter forced-descent curve update.
- Passed `.\gradlew compileJava compileClientJava` after increasing the helicopter forced-descent base step to 120%.
- Passed `.\gradlew compileJava compileClientJava` after restoring linear forced descent and widening forced-descent warnings.
- Passed `.\gradlew compileJava compileClientJava` after preventing forced-descent lift from limiting descent speed.
- Passed `.\gradlew compileJava compileClientJava` after making forced descent accumulate before air drag.
- Passed `.\gradlew compileJava compileClientJava` after the low-energy engine and helicopter warning updates.
- Passed `.\gradlew compileJava compileClientJava` after crosshair warnings, repair recovery, no-energy rotor gating, rotor-exit stability, and fixed-wing forced-descent smoothing.
- Passed `.\gradlew compileJava compileClientJava` after the stopped-rotor exit, low-speed warning, and 20-health self-destruct threshold fixes.

## 1.7.0-hotfix150620261622 - 2026-06-15

### Fixed
- Fixed recipe loading so joining players receive the full active `jeg` recipe set.
- Fixed recipe distribution on player login so loaded recipes are consistently assigned instead of dropping entries on Fabric 26.1.

## 1.7.0-hotfix140620261037 - 2026-06-14

### Fixed
- Fixed a dedicated Fabric 26.1 server crash when reload action-bar feedback tried to inspect client-only player methods and loaded `net.minecraft.client.resources.sounds.SoundInstance`.
- Kept reload gameplay, animation state, and client rendering behavior unchanged; only the server-safe action-bar feedback path was changed.

## 1.7.0 - 2026-06-12

### Added
- Added Forge-style gun melee handling on the V key, including flashlight toggles, baseline melee sweeps, sword bayonet damage, and first-person `melee`/`bayonet` animations for animated guns.
- Added extended and drum magazine items for rifle, SMG, and shotgun magazine-fed reloads, using the same capacities as the old extended-mag and drum-mag attachment modifiers.
- Added loaded-magazine item tracking for magazine-fed guns so the current base, extended, or drum magazine type controls capacity, returned magazines, and gun-model magazine visibility.
- Added yellow tooltip hints for gun attachment-screen access and attachment items, including the magazine-feed incompatibility warning on the drum-mag attachment.

### Fixed
- Fixed magazine-fed servers so extended-mag and drum-mag attachments can no longer be installed on guns or used from old gun data to bypass magazine-fed reload behavior.
- Fixed magazine-fed reload swaps so full guns can replace the current magazine with a different valid magazine type, and reload completion validates the exact magazine selected when the reload began instead of re-scanning inventory.
- Fixed magazine-fed reload visuals so animated gun models show the old magazine type during the first half of reload and the new magazine type during the second half.
- Fixed interrupted reloads so switching away cancels reload progress and switching back replays the gun draw animation visibly instead of continuing or hiding a stale reload pose.
- Fixed first-person gun draw animations so they only trigger when switching from another hotbar item into the gun, not after inventory screen refreshes or reload state cleanup.
- Fixed reload animations ending early when an authored GeckoLib reload clip is longer than the gun's gameplay reload timer.
- Fixed first-person sprint animations so sprinting immediately after switching to a gun can interrupt lingering draw playback before firing.
- Fixed sword bayonet melee so V-key hits use target-center arc checks and the proper barrel attachment transform.
- Fixed bayonet first-person animation priority so switching to a bayonet-equipped gun plays the draw animation and sprinting with a bayonet uses the bayonet charge animation instead of the normal run pose.
- Restored Forge 1.20.1 explosive-muzzle entity-hit ignition while reducing explosive-muzzle armor piercing by 25% for balance.
- Fixed draw, reload, shoot, sprint, idle, and melee animation priority so first-person GeckoLib controllers recover cleanly after reload cancellation, hotbar switches, and local gunfire.
- Fixed grenade launcher grenades so launcher-fired grenades explode promptly instead of using an excessive fuse.
- Fixed telescope-sight ADS mouse sensitivity and FOV behavior so scoped aiming matches the 1.21.1 feel more closely.
- Fixed the attachment screen gun preview size and vertical placement on the 26.1 item preview pipeline.
- Fixed the attachment screen mod label for built-in JEGN guns.
- Fixed the service rifle rear grip so it renders correctly when no stock is installed.
- Removed temporary animation and overheat debug logging added during the reload/draw diagnosis pass.

### Documentation
- Documented magazine-fed extended/drum magazine item parity and capacity-attachment gating.
- Updated the NeoForge 1.21.1 attachment-port sync notes with the final draw/reload replay behavior and triggerable draw controller handoff.

## 1.6.2 - 2026-05-27

### Changed
- Rebalanced damaged sky ship armada loot so pistol and rifle ammo are more common while iron and lapis are less dominant.
- Tuned vehicle fall impact damage to scale more noticeably with drop height.

### Fixed
- Fixed high vehicle falls sometimes landing without impact damage.
- Fixed vehicle impact sounds playing inconsistently and stopped vehicle sounds from consuming streaming sound handles.
- Fixed vehicle cannon muzzle flares and gun muzzle flashes rendering too faint on the 26.1 particle pipeline.
- Fixed vehicle machine gun and coaxial machine gun fire sounds so sustained fire plays reliably.
- Fixed Waterpipe Shotgun reload animation falling into missing segmented reload clips.

## 1.6.1 - 2026-05-25

### Theme
- Enemy vehicle spawning and recovery.

### Added
- Added an admin login tip showing `/justEnoughGuns config vehicle enemySpawning enabled false` so server admins can disable enemy vehicle spawning.

### Fixed
- Fixed enemy BMP2 recovery after server restarts and improved unstuck behavior when enemy vehicles block each other.
- Fixed truck model collision coverage while keeping large AABB collision and ram damage disabled.

## 1.6.0 - 2026-05-23

### Added
- Added vehicle muzzle flare visuals and missile explosion effects.
- Added a server vehicle assembly toggle with action bar feedback when vehicle assembly is disabled.
- Added an action bar prompt when using a vehicle container without a crowbar.
- Synced Happy Ghast armored joy harness behavior, HUD support, and damage handling.

### Changed
- Reworked gunner growth and loadout progression with per-type progression data and configurable mob scaling commands.
- Tuned bolt rifle gunner progression and increased minigun armor-piercing strength.
- Refined the mob config command hierarchy for gunner spawn, growth, armor, and weapon tuning.
- Added configurable gunner terrain placement controls.

### Fixed
- Fixed vehicle entity collision opt-in and aligned vehicle collision bounds with OBB data.
- Fixed OBB vehicle entity collision, including speedboat collision behavior.
- Moved and tuned the MI-28 dismount position to keep players clear of missile pods.
- Fixed server-side class loading by keeping client-only vehicle effect code off dedicated servers.
- Fixed gunner spawn and progression scaling so natural spawns and spawn eggs receive the intended loadouts.
- Fixed minigun third-person render alignment on the 26.1 item model path.
- Fixed oversized gunner ammo drops.
- Fixed rocket gunner friendly-fire aggro.
- Fixed faction raid startup scheduling.
- Fixed gunner support block placement pacing.

### Removed
- Removed obsolete joyous armor plate item remnants and loot references.

## 1.6.0-pre - 2026-05-16

### Added
- Synced the configured vehicle system to Fabric 26.1, including data-driven vehicles, inventories, assembling recipes, charging station support, repair tools, missile and decoy entities, vehicle HUDs, controls, cameras, and dedicated renderers.
- Added LAV-150, BMP-2, speedboat, truck, AH-6, MI-28, A-10, TOM-6, HPJ-11, laser tower, and waveforce tower vehicle content and assets.
- Added SuperbWarfare-aligned vehicle weapons, missiles, sounds, icons, models, textures, recipes, assembly data, and alignment documentation.

### Changed
- Tuned land, boat, and aircraft vehicle handling, camera behavior, seat positions, passenger visibility, weapon stations, reverse speeds, collision boxes, armor profiles, and damage behavior.
- Tuned LAV-150 and BMP-2 weapon, mobility, turret, missile, and armor behavior.
- Increased vehicle charging station throughput and added repair-tool vehicle support.
- Increased speedboat, LAV-150, and BMP-2 smoke-screen height, thickness, and density.

### Fixed
- Fixed vehicle sync, dismount, ghosting, menu validation, hotbar selection, reload HUD, missile lock, target-warning, and blocked-wall handling issues.
- Fixed MI-28 co-pilot weapon state, MI-28 turret pitch limits, speedboat turret control, BMP-2 target road speed, vehicle ammo display names, and duplicate vehicle reload timer display.
- Synced helicopter zoom camera/passenger fixes, deployed vehicle ten-percent starting energy, and first-person repair-tool player arm rendering.
- Reduced vehicle debug logging noise.

### Documentation
- Added vehicle SuperbWarfare alignment mapping documentation.

## 1.5.1pre2 - 2026-05-09

### Changed
- Aligned muzzle flash and trail rendering with the current branch behavior.

## 1.5.1 - 2026-05-07

### Added
- Added custom bulletproof helmet and vest textures.
- Added ballistic armor interception for bulletproof helmets, bulletproof vests, and Bound Terror Phantom protection.
- Added gun tooltip armor-piercing display and bulletproof armor tooltip values for rating, undermatch multiplier, and overmatch multiplier.

### Changed
- Replaced bulletproof armor Projectile Protection behavior with ammo AP versus armor rating mitigation.
- Set helmet ballistic ratings to 80% of the same-tier vest rating.
- Restored balanced gun stats and preserved recent NeoForge ADS transform fixes.

### Documentation
- Documented the ballistic armor interception formula and durability pressure model.

## 1.4.2 - 2026-04-27

### Added
- Added configurable Phantom Gunner death explosions with `/justEnoughGuns config mob phantom deathExplosion`.

### Changed
- Synced NeoForge 26.1 gun HUD, dynamic crosshair, hit marker, spread, and overheat behavior with the NeoForge 1.21.1 1.4.2 implementation.
- Added configurable ammo HUD, timer HUD, crosshair, hit marker, and dynamic crosshair dot settings.
- Updated player gun spread to combine firing spread accumulation with movement spread degrees.
- Reduced light machine gun passive overheat cooling and lowered minigun heat gain.

### Fixed
- Hit markers now trigger only after successful living-entity bullet damage, including direct explosive hits, and no longer trigger for block hits or failed/friendly-fire hits.
- Overheat now renders with the timer texture while preserving water-cooling progress rendering.
- Fixed magazine-fed guns showing "No ammo" instead of "No compatible magazine" when no suitable loaded magazine is available for reload.

## 1.3.1 - 2026-02-20

### Added
- Added NeoForge 1.21.11 faction patrol/raid runtime modules (`FactionEventTicker`, patrol encounter tracking, home-raid trigger flow, raid controller entity) aligned with newer branch behavior.
- Added faction patrol boss bar UI and localized messages for patrol/omen/home-trigger events.
- Added `Faction Omen` status effect registration and icon resource (`textures/mob_effect/faction_omen.png`).
- Added patrol spawn diagnostics in command feedback/logs, including detailed spawn-failure categories for troubleshooting.
- Added configurable day-scaling gunner accuracy controls (`gunnerAccuracyStartDay`, `gunnerAccuracyDaysToMax`, `gunnerAccuracyMaxSpreadMultiplier`).
- Added configurable gunner shotgun spread control (`gunnerShotgunSpreadMultiplier`).

### Changed
- Crosshair is hidden while the player is holding any gun item (main hand/offhand).
- Faction raids are now bound to patrol omen faction context; patrol boss bar duplication was removed.
- Home-return raid trigger consumes faction omen and starts raid near player respawn/home position.
- Patrol spawn position sampling now prefers valid surface positions near target players.
- Path setup at spawn now uses tolerant fallback flow and records path-init failure for diagnostics.
- Same-faction gunners are treated as friendly targets and no longer attack each other.
- Gunner loadouts are locked: gunner mobs are prevented from picking up loot and can recover a gun when disarmed.
- Non-flamethrower guns now use `IGNORE_LEAVES` bullet collision behavior.
- Weapon handling pass updated recoil/movement spread behavior by weapon class.
- Weapon durability baseline was increased (about 2.5x), with additional durability/gravity tuning pass.

### Fixed
- Fixed raid ground spawns to avoid leaves and unsafe relocation outcomes.
- Fixed raid mob unreachable timeout behavior via forced repath/relocation.
- Fixed unreachable-cleanup edge case when no nearby players are present.
- Unified raid target validation and constrained terror raid target range.
- Removed monster auto-win outcome when no players are inside raid radius.
- Tightened empty-fire visual handling and removed empty-fire recoil animation side effects.
- Fixed machine-gun overheat bar visibility on NeoForge HUD.

### Balance
- Rebalanced terror phantom survivability and protection:
  - adjusted HP/protection values
  - set armor to `6`
  - increased projectile protection to `5`
- Reworked rocket damage model:
  - adjusted blast damage/falloff
  - increased direct-hit damage and ensured direct hits bypass terror projectile protection.
- Rebalanced recoil/spread across weapon classes and removed flamethrower recoil.
- Added/updated tiered low armor values for bulletproof helmets and vests.
- Gunner shot spread now scales with in-game day progression; shotgun pellet spread is tighter by default.
