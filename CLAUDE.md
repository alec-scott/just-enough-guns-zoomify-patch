# Working in this repo

## Testing changes (efficiency)

- Verify changes with the Fabric client game tests in `src/gametest/`, but run **only the tests that cover what you
  changed**, never the full suite unless the change could affect other parts of the mod (shared systems, core
  networking, build changes). The full suite takes 10+ minutes and stops at the first failure.
- Pick tests by class name (the `ClientGameTest` suffix is optional):
  `./gradlew runClientGameTest -PgameTests=HelicopterStartup,VehiclePaint`
- If no test covers the change, add one (a class in `src/gametest/java/ttv/migami/jeg/gametest/` plus its entry in
  `src/gametest/resources/fabric.mod.json`) and run just that.
- **Test inside the modpack before every release.** The mod ships in the yftm pack, so a bare dev-client pass is not
  enough: before bumping `yftm_build`, run the tests covering the change again with the pack's mods and shaders
  (Minecraft 26.1.2, loader 0.20.1):
  `./gradlew runClientGameTest -PgameTests=<tests> -PwithPack -Pminecraft_version=26.1.2 -Pfabric_version=0.155.3+26.1.2 -Ploader_version=0.20.1`
  - This shares the user's GPU: check that their own Minecraft client is not running first, and ask if it is.
  - Check what else the pack does in the area being changed (other mods' mixins, configs, keys, sounds, view and
    simulation distance) with the `yftm` MCP tools, and write tests at the server's settings or harsher.
  - `-PwithPack` is a singleplayer world with the subset of pack mods listed in `build.gradle`; it is not the dedicated
    server and lacks server mods such as Sable. If a change touches something a missing mod affects, add that mod to
    `packMods` first. Say in the release report what the pack run did and did not cover.
  - yftm.13 shipped a "missiles fly their full range" fix tested only on the bare client; helicopter rockets, a
    different projectile, were never fired at range and still vanished on the server. Test every weapon or path a fix
    claims to cover, not just the one that was changed.
- Screenshots land in `build/run/clientGameTest/screenshots/`.
- Never relaunch or close the user's own `runClient` instance unless they ask.

## Build

- `java` is not on PATH: set `JAVA_HOME` to the Temurin JDK 25 at
  `C:\Users\User\Desktop\Projects\forge-to-fabric-portlab\.local\jdk\jdk-25.0.4.1+1` before running `./gradlew`.
- Compile check without launching the game: `./gradlew compileJava compileClientJava compileGametestJava`.
