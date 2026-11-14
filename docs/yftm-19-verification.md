# yftm.19 verification - 2026-11-14

This patch combines aircraft exterior and cockpit lighting, A-10 speed brakes, alternating helicopter launch sides, smoother helicopter tracers, and minigun cooldown fixes. See `CHANGELOG.md` for player-facing behavior and `minigun-cooling-verification.md` for the cooldown investigation.

## Release checks

All five targeted client game tests passed together with Minecraft 26.1.2, Fabric API 0.155.3+26.1.2, loader 0.20.1, Complementary Reimagined r5.9.3, Fresh Animations, and the `-PwithPack` mod subset configured in `build.gradle`:

```powershell
./gradlew.bat runClientGameTest '-PgameTests=A10DiveBrake,AircraftNightLights,HeliWeaponSides,MinigunRapidCool,MinigunDualWield' '-PwithPack' '-Pminecraft_version=26.1.2' '-Pfabric_version=0.155.3+26.1.2' '-Ploader_version=0.20.1'
```

- `A10DiveBrake`: free dive 248 km/h, braked dive 115 km/h; brakes opened fully and closed after release. Inspected the wingtip screenshot showing the split ailerons.
- `AircraftNightLights`: three ground-light spots at midnight and zero by day. Cockpit boarding succeeded. Inspected A-10 and Mi-28 pilot views and the Mi-28 searchlight and exterior lights at night.
- `HeliWeaponSides`: alternating sides for AH-6 70 mm rockets, Mi-28 80 mm rockets, pilot 9M120, 9M336, gunner 9M120 and Kh-39. Captures include helicopter tracers by day and night.
- `MinigunRapidCool`: cooldown, firing exclusion and resumption, cancellation, putting the gun away, both hands cooling, unequal heat, barrel rotation through stopping, and Iris shader pipeline identity. Shader reload still works with empty hands and a separate shader key.
- `MinigunDualWield`: firing, ammunition, heat, movement speed, tracer origins and third-person muzzle placement.

Local log: `build/yftm-19-pack-tests.log`; screenshots: `build/run/clientGameTest/screenshots/`. The pack test run passed before the build number was raised to 19.

These checks use integrated singleplayer with the configured pack subset, not the dedicated server or every pack mod. The yftm MCP tools are unavailable in this session, so live server settings were not queried. The run still logs OpenGL `No active program` errors during firing; passing these targeted checks does not establish that all rendering issues are resolved.

## Build and installation

Build with Java 25 using `./gradlew.bat build compileGametestJava`. The release artifact is `build/libs/jegn-1.7.2+mc26.1.2.yftm.19.jar`.

Install the same build on the server and clients: the aircraft changes add a synchronized speed-brake field to vehicle entity state. No running Minecraft instance is restarted or modified by the build.
