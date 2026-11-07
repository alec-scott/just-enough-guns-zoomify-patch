# yftm.8 verification

This patch addresses the reported multiplayer minigun hand/HUD problems, loud and direction-dependent local audio, cockpit engine placement, helicopter landing coast-down, and MI-28 gun spin-up.

## Reproduce

With JDK 25 selected through `JAVA_HOME`, run:

```powershell
.\gradlew.bat runClientGameTest build --offline --console=plain
```

Omit `--offline` if dependencies and assets are not cached. Screenshots are written to `build/run/clientGameTest/screenshots/`.

## Coverage

- `MultiplayerGunAudioClientGameTest` creates a dedicated test server and connects a client over the multiplayer connection. It captures idle minigun hands and ammo HUD at GUI scales 1–4.
- The local minigun and occupied helicopter engine must use a centered source with no positional attenuation. The tests turn through four headings and verify the same sound channel stays active.
- A remote entity's minigun must keep positional attenuation and audible playback.
- AH-6 and MI-28 startup, takeoff and landing are exercised through normal controls. Rotors must remain spinning at touchdown, slow over the next three seconds, keep a quieter/lower-pitched engine loop during coast-down, and eventually stop with their audio.
- MI-28 trigger input must still auto-reload an empty gun. Once loaded, it must spend no ammo or fire during the first half of its 30-tick spin-up, then fire with an active sound channel. Release must stop firing and finish barrel coast-down.
- Existing minigun spin, first-person gun hand, helicopter startup and helicopter weapon tests cover aiming, firing, switching, startup/takeoff, weapon alignment and firing-loop release. The MI-28 weapon test now waits for the intentional spin-up.
- Startup checks require both startup and flight recordings to overlap on active channels, maintain their combined level during the crossfade, and retain the same flight channel through takeoff and acceleration.
- MI-28 muzzle checks derive the barrel tip from the model geometry and compare it with actual projectile spawn positions at level, nose-down and nose-up hull attitudes. Both model detail levels must place the spin pivot on the bore.
- Gunner camera checks require the normal view to follow the barrel and the zoomed camera to point along the bore from a clear position just ahead of the muzzle. Screenshots cover the three hull attitudes.

## Behavior

Minigun wrists are attached to explicit handle anchors under the animated gun body. Other weapons retain their authored arm animation bones. The HUD measures the scaled current-ammo text and reserve text and anchors the group to the active GUI viewport's right margin.

The minigun firing volume is 0.75 and mechanical spin volume is 0.3. Remote minigun samples retain explicit attenuation distances so reducing volume does not also remove their useful audible range. The helicopter's occupants hear centered engine and gun audio; observers hear positional sources. Cabin engine gain is capped at 0.65 before RPM scaling.

The flight recording fades in during the final 1.5 seconds of startup while the startup recording fades out with an equal-power curve. RPM gain is eased between updates. The flight loop continues through stationary/flight changes rather than opening a new channel when movement starts. Entering or leaving the cockpit uses a short crossfade between listener modes.

On touchdown with collective released, engine power shuts down and the rotor coasts independently of braking input. AH-6 coast-down takes up to 240 ticks and MI-28 up to 360 ticks from operating speed. Animation, engine pitch and engine gain all follow rotor speed. MI-28 gun rotation and mechanical audio follow its server-synchronized spin, with 30 ticks to full speed and 45 ticks to stop.

The MI-28 muzzle is `(0, 0.659625, 7.317575)` in hull space at neutral turret angles. Its spin axis uses the shaft geometry's Y centre at 10.554 model pixels, eliminating the former 1.759-pixel orbit. The normal gunner view follows the barrel from just above its axis so the spinning gun stays visible; zoom puts the camera on the bore, 0.05 blocks beyond the tip to keep the gun model out of the sight picture.

The automated sound checks establish routing, gain and active channel continuity. Perceived loudness on the user's audio setup and interactions with other installed client mods still require their in-game check.

## Results — 2026-11-07

- Minigun spin and first-person gun hand checks passed; inspected idle/firing screenshots with the new handle anchors and HUD layout.
- Dedicated multiplayer checks passed for local versus remote audio, turning without restarting channels, both helicopters' landing coast-down, and MI-28 automatic reload/spin-up/firing/release.
- Both helicopters passed the startup crossfade's active-channel and combined-level assertions, then retained their flight loop through takeoff and acceleration.
- MI-28 projectile spawn error against the model muzzle measured `0.000000` blocks at all three tested hull attitudes. Both barrel detail levels passed the centred-axis check.
- The normal turret-following camera and clear zoomed boresight passed checks at all three hull attitudes. Inspected the centred gun and unobstructed boresight screenshots.
- A separate server process reached the ready state in an isolated world and shut down cleanly.

The artifact is `build/libs/jegn-1.7.2+mc26.1.2.yftm.8.jar`. Install the same build on the server and clients because the vehicle's synchronized data includes new gun spin fields.

## Additional changes — 2026-11-07 (later)

Each was checked with a targeted client game test (the new tests are registered in `src/gametest/resources/fabric.mod.json`):

- `GunMuzzleOriginClientGameTest`: the muzzle is read from the drawn gun model; tracers start within 0.01–0.09 blocks of it in first person, third person and when watching another shooter.
- `VehiclePaintClientGameTest`: dye paints the AH-6 and MI-28 without boarding, uses one dye, is saved, and a water bucket washes it off.
- `HelicopterBoardingClientGameTest`: boarding and starting both helicopters plays no clank, raises no warning and costs no health.
- `VehicleGunVisualsClientGameTest`: through the MI-28 zoomed sight, 40 of 40 sampled tracers were drawn reaching their impact and the view stays clear; AH-6 rounds show as heavy tracers; a passenger's rifle tracer starts at the rifle (0.11–0.13 blocks).
- `MissileBlastClientGameTest`: on a 9 × 9 cobblestone/plank hut, a medium missile broke 36–37 blocks, a large one all 307, and the incendiary 47–52 blocks, leaving 71–86 fires. Blast frames inspected.
- `HelicopterTailCameraClientGameTest`: the reversed view sits on the tail boom (6.4 blocks behind the AH-6, 12.3 behind the MI-28), looks along the boom and banks with the hull.
- `MissileFlightClientGameTest`: missile and rocket model, motor glow and smoke trail inspected.

A full-suite run in one client crashed the game process natively (exit 0xCFFFFFFF) about 19 minutes in, during the AH-6 side-view part of `VehicleGunVisualsClientGameTest`; that test passes when run alone. Watch for client crashes in long sessions.
