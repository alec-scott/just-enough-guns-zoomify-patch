package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Looks the A-10 over on the ground, then flies a full sortie with the pilot's own controls: take-off run, climb, both
 * turns, a loop, an aileron roll, an idle-power stall and its recovery, a dive, an approach and landing on the wheel
 * brakes, a GAU-8 burst and both missiles from the runway, and finally a high-speed crash. Logs speed, attitude and height every tick, saves screenshots along the way,
 * and fails on a take-off run outside 25-130 blocks, a loop that does not come round, no stall at idle, a landing
 * that costs over a tenth of the jet's health, or a crash that leaves the jet intact.
 */
public class A10FlightClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private final StringBuilder log = new StringBuilder();
    private final StringBuilder report = new StringBuilder("a10 report\n");
    private final StringBuilder failures = new StringBuilder();
    /** Every A-10 sound the player hears while flying it, with the view it was heard from ("|fp" or "|tp"). */
    private final java.util.List<String> cockpitSounds = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private final net.minecraft.client.sounds.SoundEventListener cockpitListener = (sound, events, range) -> {
        String path = sound.getIdentifier().getPath();
        if (path.contains("a10")) {
            boolean firstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
            this.cockpitSounds.add(path + (firstPerson ? "|fp" : "|tp"));
        }
    };
    /** The iron golems on the gun range, by UUID. */
    private final java.util.List<java.util.UUID> targets = new java.util.ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 0");
            context.waitTicks(5);
            server.runCommand("execute as @p at @s run summon jeg:a10 ~ ~ ~20 {Rotation:[0f,0f]}");
            context.waitTicks(10);
            this.walkAround(context, server);

            context.runOnClient(mc -> mc.getSoundManager().addListener(this.cockpitListener));
            server.runCommand("ride @p mount @e[type=jeg:a10,limit=1,sort=nearest]");
            context.waitFor(mc -> vehicle(mc) != null);
            context.waitTicks(10);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(5);
            context.takeScreenshot("a10_cockpit");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(10);
            context.takeScreenshot("a10_chase_parked");

            this.fly(context);
            this.weapons(context, server);
            context.runOnClient(mc -> mc.getSoundManager().removeListener(this.cockpitListener));
            this.cockpitAudio();
            this.shotDown(context, server);
            this.crash(context, server);
        } finally {
            LOGGER.info("[a10]\n{}", this.log);
            LOGGER.info("{}", this.report);
        }
        if (this.failures.length() > 0) {
            throw new AssertionError(this.failures.toString() + this.report);
        }
    }

    /**
     * The pilot hears the A-10 from inside in the cockpit view (the muffled cockpit versions of its engines, tyres, gear,
     * touchdown and gun) and as it sounds outside from the chase camera; never the wrong one for the view, and no flyby.
     * The gear and touchdown come from the pilot's own client (the server leaves the pilot out), so they must be heard.
     */
    private void cockpitAudio() {
        java.util.Set<String> heard;
        synchronized (this.cockpitSounds) {
            heard = new java.util.TreeSet<>(this.cockpitSounds);
        }
        this.report.append("pilot sounds: ").append(heard).append(System.lineSeparator());
        java.util.List<String> outside = java.util.List.of("engine_start", "engine", "runway_roll", "gear", "touchdown", "fire", "gun_stop");
        for (String name : outside) {
            if (heard.contains("vehicle.a10." + name + "|fp")) {
                this.fail("in the cockpit view the pilot heard the outside sound vehicle.a10." + name);
            }
            if (heard.contains("vehicle.a10." + name + "_cockpit|tp")) {
                this.fail("from the chase camera the pilot heard the muffled vehicle.a10." + name + "_cockpit");
            }
        }
        if (heard.stream().anyMatch(sound -> sound.contains("flyby"))) {
            this.fail("the pilot heard a flyby of their own jet");
        }
        if (!heard.contains("vehicle.a10.engine_cockpit|fp") || !heard.contains("vehicle.a10.engine|tp")) {
            this.fail("the engines were not heard muffled in the cockpit view and plain from the chase camera");
        }
        for (String name : java.util.List.of("gear", "touchdown", "fire")) {
            if (heard.stream().noneMatch(sound -> sound.startsWith("vehicle.a10." + name))) {
                this.fail("the pilot never heard vehicle.a10." + name + " in either view");
            }
        }
    }

    private void walkAround(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("execute as @p at @e[type=jeg:a10,limit=1] run tp @s ~-14 ~4 ~22 facing entity @e[type=jeg:a10,limit=1]");
        context.waitTicks(20);
        context.takeScreenshot("a10_parked_front");
        server.runCommand("execute as @p at @e[type=jeg:a10,limit=1] run tp @s ~-22 ~3 ~ facing entity @e[type=jeg:a10,limit=1]");
        context.waitTicks(10);
        context.takeScreenshot("a10_parked_side");
        server.runCommand("execute as @p at @e[type=jeg:a10,limit=1] run tp @s ~6 ~5 ~-20 facing entity @e[type=jeg:a10,limit=1]");
        context.waitTicks(10);
        context.takeScreenshot("a10_parked_rear");
        server.runCommand("execute as @p at @e[type=jeg:a10,limit=1] run tp @s ~ ~22 ~2 facing entity @e[type=jeg:a10,limit=1]");
        context.waitTicks(10);
        context.takeScreenshot("a10_parked_top");
    }

    private void fly(ClientGameTestContext context) {
        Vec3 start = context.computeOnClient(mc -> vehicle(mc).position());
        float health = context.computeOnClient(mc -> vehicle(mc).vehicleHealth());

        // Take-off: full throttle, then lift the nose to about 8 degrees at rotate speed and let it fly off.
        context.getInput().holdKey(options -> options.keyUp);
        context.waitTicks(55);
        context.getInput().releaseKey(options -> options.keyUp);
        Vec3 liftOff = null;
        for (int tick = 1; tick <= 700 && liftOff == null; tick++) {
            State state = this.state(context);
            double dy = state.speed > 0.76D && state.pitch > -8.0F ? -14.0D : 0.0D;
            context.getInput().moveCursor(0.0D, dy);
            context.waitTick();
            this.sample(context, "takeoff", tick);
            if (tick == 200) {
                context.takeScreenshot("a10_takeoff_roll");
            }
            State after = this.state(context);
            if (!after.grounded && after.altitude > 0.6D) {
                liftOff = after.position;
            }
        }
        if (liftOff == null) {
            this.fail("never left the ground");
            return;
        }
        double run = Math.sqrt(Math.pow(liftOff.x - start.x, 2) + Math.pow(liftOff.z - start.z, 2));
        this.report.append(String.format("take-off run %.0f blocks%n", run));
        if (run < 25.0D || run > 130.0D) {
            this.fail(String.format("take-off run %.0f blocks, expected 25-130", run));
        }
        this.record(context, "climb-out", 40, 0.0D, 0.0D);
        context.takeScreenshot("a10_climb_out");
        // Climb to about 45 blocks on a held climb, then level off.
        for (int tick = 1; tick <= 500 && this.state(context).altitude < 45.0D; tick++) {
            this.holdPitch(context, "climb", tick, -9.0F);
        }
        for (int tick = 1; tick <= 60; tick++) {
            this.holdPitch(context, "level", tick, -2.0F);
        }
        this.record(context, "hands-off", 60, 0.0D, 0.0D);
        float levelAltitude = (float) this.state(context).altitude;
        this.report.append(String.format("hands-off level flight: alt %.1f after 60 ticks, speed %.2f%n", levelAltitude, this.state(context).speed));

        // Right turn in the chase view, left turn from the cockpit.
        float headingBefore = this.state(context).yaw;
        float maxBank = 0.0F;
        for (int tick = 1; tick <= 70; tick++) {
            context.getInput().moveCursor(30.0D, 0.0D);
            context.waitTick();
            State state = this.sample(context, "turn-right", tick);
            maxBank = Math.max(maxBank, state.roll);
            if (tick == 45) {
                context.takeScreenshot("a10_turn_right_chase");
            }
        }
        this.record(context, "turn-exit", 50, 0.0D, 0.0D);
        float turned = Mth.wrapDegrees(this.state(context).yaw - headingBefore);
        this.report.append(String.format("right turn: %.0f degrees, max bank %.0f, roll after exit %.1f%n", turned, maxBank, this.state(context).roll));
        if (turned < 30.0F) {
            this.fail(String.format("right turn only turned %.0f degrees", turned));
        }
        if (Math.abs(this.state(context).roll) > 8.0F) {
            this.fail("wings did not level after the turn");
        }
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        for (int tick = 1; tick <= 60; tick++) {
            context.getInput().moveCursor(-30.0D, 0.0D);
            context.waitTick();
            this.sample(context, "turn-left-fp", tick);
            if (tick == 40) {
                context.takeScreenshot("a10_turn_left_cockpit");
            }
        }
        this.record(context, "turn-left-exit", 50, 0.0D, 0.0D);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));

        // Loop: build speed, then hold the stick back until the jet comes round to level again.
        context.getInput().holdKey(options -> options.keyUp);
        for (int tick = 1; tick <= 320 && this.state(context).speed < 1.57D; tick++) {
            this.holdPitch(context, "loop-entry", tick, 4.0F);
        }
        context.getInput().releaseKey(options -> options.keyUp);
        boolean inverted = false;
        boolean around = false;
        float loopStartYaw = this.state(context).yaw;
        for (int tick = 1; tick <= 260 && !around; tick++) {
            context.getInput().moveCursor(0.0D, -60.0D);
            context.waitTick();
            State state = this.sample(context, "loop", tick);
            if (Math.abs(state.roll) > 150.0F && state.pitch > -45.0F && state.pitch < 45.0F) {
                if (!inverted) {
                    context.takeScreenshot("a10_loop_top");
                }
                inverted = true;
            }
            if (inverted && Math.abs(state.roll) < 30.0F && state.pitch < 0.0F && Math.abs(Mth.wrapDegrees(state.yaw - loopStartYaw)) < 30.0F) {
                around = true;
            }
        }
        this.report.append(String.format("loop: inverted at top %s, came round %s%n", inverted, around));
        if (!inverted || !around) {
            this.fail("the loop did not go over the top and come round");
        }
        this.record(context, "loop-exit", 40, 0.0D, 0.0D);

        // Aileron roll: hold D for a full turn of the wings.
        float rollStartAlt = (float) this.state(context).altitude;
        float rolled = 0.0F;
        float previousRoll = this.state(context).roll;
        context.getInput().holdKey(options -> options.keyRight);
        for (int tick = 1; tick <= 120 && rolled < 340.0F; tick++) {
            context.waitTick();
            State state = this.sample(context, "roll", tick);
            rolled += Mth.wrapDegrees(state.roll - previousRoll);
            previousRoll = state.roll;
            if (tick == 30) {
                context.takeScreenshot("a10_roll");
            }
        }
        context.getInput().releaseKey(options -> options.keyRight);
        this.record(context, "roll-exit", 50, 0.0D, 0.0D);
        this.report.append(String.format("roll: %.0f degrees, height change %.1f, roll after exit %.1f%n", rolled,
                this.state(context).altitude - rollStartAlt, this.state(context).roll));
        if (rolled < 300.0F) {
            this.fail(String.format("aileron roll only turned %.0f degrees", rolled));
        }

        // Climb for room, then idle the engines and hold the nose up until it stalls.
        context.getInput().holdKey(options -> options.keyUp);
        for (int tick = 1; tick <= 400 && this.state(context).altitude < 90.0D; tick++) {
            this.holdPitch(context, "stall-climb", tick, -10.0F);
        }
        context.getInput().releaseKey(options -> options.keyUp);
        context.getInput().holdKey(options -> options.keyDown);
        context.getInput().holdKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
        boolean stallWarned = false;
        boolean stalled = false;
        for (int tick = 1; tick <= 700 && !stalled; tick++) {
            State state = this.holdPitch(context, "stall", tick, -12.0F);
            stallWarned |= state.stallWarning;
            // Too slow to fly: warned, sinking and at the angle-of-attack limit. Held there the nose drops by itself.
            if (state.stallWarning && state.vy < -0.08D && state.alpha > 13.5F) {
                stalled = true;
                context.takeScreenshot("a10_stall");
            }
        }
        context.getInput().releaseKey(options -> options.keyDown);
        context.getInput().releaseKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
        // Recovery: nose down, full power, and pull out once flying again.
        context.getInput().holdKey(options -> options.keyUp);
        double lowest = Double.MAX_VALUE;
        boolean recovered = false;
        for (int tick = 1; tick <= 400 && !recovered; tick++) {
            State state = this.state(context);
            State after = state.speed < 0.98D ? this.holdPitch(context, "recover", tick, 25.0F) : this.holdPitch(context, "pull-out", tick, -3.0F);
            lowest = Math.min(lowest, after.altitude);
            recovered = after.speed > 0.98D && after.vy > -0.05D;
        }
        context.getInput().releaseKey(options -> options.keyUp);
        this.report.append(String.format("stall: warned %s, stalled %s, recovered %s, lowest %.0f blocks%n", stallWarned, stalled, recovered, lowest));
        if (!stallWarned || !stalled || !recovered) {
            this.fail("idle stall: warned " + stallWarned + ", stalled " + stalled + ", recovered " + recovered);
        }

        // Dive and pull out.
        for (int tick = 1; tick <= 60; tick++) {
            State state = this.holdPitch(context, "dive", tick, 35.0F);
            if (tick == 40) {
                context.takeScreenshot("a10_dive");
            }
            if (state.altitude < 45.0D) {
                break;
            }
        }
        for (int tick = 1; tick <= 120 && this.state(context).vy < -0.05D; tick++) {
            this.holdPitch(context, "dive-pull-out", tick, -6.0F);
        }

        // Approach: 30% throttle, about four degrees down, flare low, wheel brakes after touchdown.
        this.setThrottle(context, 0.3F);
        float beforeLanding = context.computeOnClient(mc -> vehicle(mc).vehicleHealth());
        boolean touched = false;
        double touchdownSink = 0.0D;
        double touchdownSpeed = 0.0D;
        double previousVy = 0.0D;
        for (int tick = 1; tick <= 3000; tick++) {
            State state = this.state(context);
            if (!touched && !state.grounded) {
                // Down at about 0.13 blocks a tick while high, 0.06 on short final, then a gentle flare.
                double wantedSink = state.altitude > 25.0D ? 0.13D : 0.06D;
                float targetPitch = state.altitude > 4.0D
                        ? (-state.vy > wantedSink + 0.02D ? -4.0F : -state.vy < wantedSink - 0.02D ? 4.0F : (float) state.pitch)
                        : -4.5F;
                this.holdPitch(context, "approach", tick, targetPitch);
                if (tick % 200 == 0) {
                    context.takeScreenshot("a10_approach_" + tick);
                }
                State after = this.state(context);
                if (!after.grounded) {
                    previousVy = after.vy;
                }
            } else {
                if (!touched) {
                    touched = true;
                    touchdownSink = -previousVy;
                    touchdownSpeed = state.speed;
                    context.takeScreenshot("a10_touchdown");
                    context.getInput().holdKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
                    context.getInput().holdKey(options -> options.keyDown);
                }
                context.waitTick();
                State rolling = this.sample(context, "rollout", tick);
                if (rolling.speed < 0.02D) {
                    break;
                }
            }
        }
        context.getInput().releaseKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
        context.getInput().releaseKey(options -> options.keyDown);
        context.takeScreenshot("a10_landed");
        float afterLanding = context.computeOnClient(mc -> vehicle(mc) == null ? 0.0F : vehicle(mc).vehicleHealth());
        this.report.append(String.format("landing: touched %s, sink %.3f, speed %.2f, health %.1f -> %.1f (start %.1f)%n",
                touched, touchdownSink, touchdownSpeed, beforeLanding, afterLanding, health));
        if (!touched || beforeLanding - afterLanding > 0.1F * beforeLanding) {
            this.fail("landing: touched " + touched + ", health " + beforeLanding + " -> " + afterLanding);
        }
    }

    /**
     * Parked on the runway after landing: a GAU-8 burst at a wall ahead, then two of each missile, which must leave from
     * alternate wings (and the pylons must empty one by one).
     */
    private void weapons(ClientGameTestContext context, TestServerContext server) {
        if (context.computeOnClient(mc -> vehicle(mc) == null)) {
            this.fail("no jet to test the weapons on");
            return;
        }
        server.runOnServer(minecraftServer -> {
            var player = minecraftServer.getPlayerList().getPlayers().getFirst();
            if (player.getVehicle() instanceof VehicleEntity jet) {
                jet.addAmmoForAi(ttv.migami.jeg.Reference.id("rifle_ammo"), 600);
                jet.addAmmoForAi(ttv.migami.jeg.Reference.id("medium_anti_ground_missile"), 2);
                jet.addAmmoForAi(ttv.migami.jeg.Reference.id("large_anti_ground_missile"), 2);
                jet.reloadAiVehicleWeapons();
            }
        });
        // A range 60 blocks down the nose: a two-block platform with five iron golems on it, at the gun's height, and a
        // wall behind.
        String[] range = server.computeOnServer(minecraftServer -> {
            var player = minecraftServer.getPlayerList().getPlayers().getFirst();
            VehicleEntity jet = (VehicleEntity) player.getVehicle();
            Vec3 ahead = Vec3.directionFromRotation(0.0F, jet.getYRot());
            Vec3 side = new Vec3(ahead.z, 0.0D, -ahead.x);
            Vec3 centre = jet.position().add(ahead.scale(60.0D));
            int y = (int) Math.floor(jet.getY());
            String[] commands = new String[2];
            Vec3 wall = centre.add(ahead.scale(2.5D));
            commands[0] = String.format("fill %d %d %d %d %d %d minecraft:stone", (int) Math.floor(centre.x - 9), y, (int) Math.floor(centre.z - 9),
                    (int) Math.floor(centre.x + 9), y + 1, (int) Math.floor(centre.z + 9));
            commands[1] = String.format("fill %d %d %d %d %d %d minecraft:stone", (int) Math.floor(wall.x - 12), y, (int) Math.floor(wall.z - 1),
                    (int) Math.floor(wall.x + 12), y + 12, (int) Math.floor(wall.z + 1));
            return commands;
        });
        // The gun fires down the nose (1.7 blocks up when parked), so the targets stand on the ground; only the wall is built.
        server.runCommand(range[1]);
        this.targets.addAll(server.computeOnServer(minecraftServer -> {
            var player = minecraftServer.getPlayerList().getPlayers().getFirst();
            VehicleEntity jet = (VehicleEntity) player.getVehicle();
            Vec3 ahead = Vec3.directionFromRotation(0.0F, jet.getYRot());
            Vec3 side = new Vec3(ahead.z, 0.0D, -ahead.x);
            Vec3 centre = jet.position().add(ahead.scale(60.0D));
            java.util.List<java.util.UUID> spawned = new java.util.ArrayList<>();
            for (int i = 0; i < 5; i++) {
                // A group two blocks apart, like mobs bunched together.
                Vec3 at = centre.add(side.scale((i - 2) * 2.0D));
                var golem = net.minecraft.world.entity.EntityType.IRON_GOLEM.create(minecraftServer.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                golem.setNoAi(true);
                golem.snapTo(at.x, Math.floor(jet.getY()), at.z, jet.getYRot() + 180.0F, 0.0F);
                minecraftServer.overworld().addFreshEntity(golem);
                spawned.add(golem.getUUID());
            }
            return spawned;
        }));
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.waitTicks(20);
        context.takeScreenshot("a10_loaded");
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        context.waitTicks(5);
        context.takeScreenshot("a10_range_cockpit");
        // The zoomed targeting pod: the mouse looks down at the ground and the jet's nose stays where it is.
        context.getInput().holdMouse(1);
        context.waitTicks(3);
        float noseBefore = context.computeOnClient(mc -> vehicle(mc).getXRot());
        for (int tick = 0; tick < 15; tick++) {
            context.getInput().moveCursor(0.0D, 40.0D);
            context.waitTick();
        }
        context.waitTicks(3);
        float podPitch = context.computeOnClient(mc -> mc.gameRenderer.getMainCamera().xRot());
        float noseAfter = context.computeOnClient(mc -> vehicle(mc).getXRot());
        context.takeScreenshot("a10_pod_view_down");
        context.getInput().releaseMouse(1);
        context.waitTicks(5);
        this.report.append(String.format("pod view: looked %.0f degrees down, nose %.1f -> %.1f%n", podPitch, noseBefore, noseAfter));
        if (podPitch < 20.0F || Math.abs(noseAfter - noseBefore) > 1.0F) {
            this.fail(String.format("zoomed pod view: looked %.0f down, nose moved %.1f -> %.1f", podPitch, noseBefore, noseAfter));
        }
        float targetsBefore = this.targetHealth(server);
        float barrelsBefore = context.computeOnClient(mc -> vehicle(mc).jetGunAngle(1.0F));
        int gunBefore = context.computeOnClient(mc -> vehicle(mc).vehicleWeaponSlotAmmo(0));
        context.getInput().holdKey(options -> options.keyAttack);
        for (int tick = 1; tick <= 40; tick++) {
            context.waitTick();
            if (tick == 25) {
                context.takeScreenshot("a10_gun");
                float barrels = context.computeOnClient(mc -> vehicle(mc).jetGunAngle(1.0F));
                context.waitTick();
                float barrelsNext = context.computeOnClient(mc -> vehicle(mc).jetGunAngle(1.0F));
                this.report.append(String.format("gun barrels mid-burst: %.0f -> %.0f degrees (from %.0f before firing)%n", barrels, barrelsNext, barrelsBefore));
                if (barrels == barrelsBefore) {
                    this.fail("the GAU-8's barrels did not turn while it fired");
                }
            }
        }
        context.getInput().releaseKey(options -> options.keyAttack);
        context.waitTicks(20);
        context.takeScreenshot("a10_gun_impacts");
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        int gunAfter = context.computeOnClient(mc -> vehicle(mc).vehicleWeaponSlotAmmo(0));
        float targetsAfter = this.targetHealth(server);
        this.report.append(String.format("gun: %d -> %d rounds over a 40 tick burst; five iron golems %.0f -> %.0f health%n",
                gunBefore, gunAfter, targetsBefore, targetsAfter));
        // The golem on the crosshair goes down, and the rounds' bursts reach the ones beside it.
        int alive = this.targetsAlive(server);
        int hurt = this.targetsHurt(server);
        this.report.append(String.format("gun: %d of 5 golems left standing, %d hurt or killed%n", alive, hurt));
        if (alive > 4 || hurt < 3) {
            this.fail(String.format("a two-second GAU-8 burst into a group: %.0f health off five iron golems, %d left standing, %d hurt",
                    targetsBefore - targetsAfter, alive, hurt));
        }
        if (gunBefore - gunAfter < 30) {
            this.fail("the GAU-8 fired " + (gunBefore - gunAfter) + " rounds in two seconds");
        }
        for (int slot = 1; slot <= 2; slot++) {
            context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitTicks(5);
            StringBuilder sides = new StringBuilder();
            for (int shot = 0; shot < 2; shot++) {
                context.getInput().holdKeyFor(options -> options.keyAttack, 2);
                context.waitTicks(2);
                double side = server.computeOnServer(minecraftServer -> {
                    var player = minecraftServer.getPlayerList().getPlayers().getFirst();
                    if (!(player.getVehicle() instanceof VehicleEntity jet)) {
                        return 0.0D;
                    }
                    var missiles = player.level().getEntitiesOfClass(ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity.class,
                            jet.getBoundingBox().inflate(30.0D), missile -> missile.tickCount < 6);
                    if (missiles.isEmpty()) {
                        return 0.0D;
                    }
                    Vec3 offset = missiles.getFirst().position().subtract(jet.position());
                    Vec3 left = Vec3.directionFromRotation(0.0F, jet.getYRot() - 90.0F);
                    return offset.dot(left);
                });
                sides.append(String.format(" %.1f", side));
                context.takeScreenshot("a10_missile" + slot + "_shot" + (shot + 1));
                context.waitTicks(45);
            }
            this.report.append("missile slot ").append(slot + 1).append(" left-of-centre offsets:").append(sides).append(System.lineSeparator());
            String[] values = sides.toString().trim().split(" ");
            double first = Double.parseDouble(values[0]);
            double second = Double.parseDouble(values[1]);
            if (first == 0.0D || second == 0.0D || Math.signum(first) == Math.signum(second)) {
                this.fail("missile slot " + (slot + 1) + " did not alternate wings:" + sides);
            }
        }
    }

    private int targetsHurt(TestServerContext server) {
        return server.computeOnServer(minecraftServer -> (int) this.targets.stream()
                .filter(id -> !(minecraftServer.overworld().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity golem)
                        || !golem.isAlive() || golem.getHealth() < golem.getMaxHealth())
                .count());
    }

    private int targetsAlive(TestServerContext server) {
        return server.computeOnServer(minecraftServer -> (int) this.targets.stream()
                .filter(id -> minecraftServer.overworld().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity golem && golem.isAlive())
                .count());
    }

    private float targetHealth(TestServerContext server) {
        return server.computeOnServer(minecraftServer -> {
            float total = 0.0F;
            for (java.util.UUID id : this.targets) {
                if (minecraftServer.overworld().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity golem && golem.isAlive()) {
                    total += golem.getHealth();
                }
            }
            return total;
        });
    }

    /**
     * A second jet, taken up and shot to pieces: smoking at half health, burning with its control surfaces gone below a
     * third, then destroyed in the air. Watched from outside as it goes down, burning and rolling, and blows up on the
     * ground.
     */
    private void shotDown(ClientGameTestContext context, TestServerContext server) {
        // Engine close-ups first: run the landed jet up to full power on its brakes, then get out and look into the exhausts while the
        // fans are still turning. Two frames a few ticks apart: the fans turn, the rims around them stay put.
        if (context.computeOnClient(mc -> vehicle(mc) != null)) {
            context.getInput().holdKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
            this.setThrottle(context, 1.0F);
            context.waitTicks(60);
            context.getInput().releaseKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
            server.runCommand("ride @p dismount");
            server.runCommand("execute as @p at @e[type=jeg:a10,limit=1,sort=nearest] run tp @s ^-2.5 ^2.2 ^-9.5 facing ^ ^3.4 ^-3.5");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(2);
            context.takeScreenshot("a10_engines_a");
            context.waitTicks(3);
            context.takeScreenshot("a10_engines_b");
        }
        server.runCommand("ride @p dismount");
        context.waitTicks(10);
        String victim = server.computeOnServer(minecraftServer -> {
            var player = minecraftServer.getPlayerList().getPlayers().getFirst();
            VehicleEntity jet = ttv.migami.jeg.init.ModEntities.A10.get().create(minecraftServer.overworld(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            // Close by, where the chunks are loaded; boarded straight away so it does not fall unflown.
            jet.snapTo(player.getX(), player.getY() + 60.0D, player.getZ() - 40.0D, 0.0F, 0.0F);
            jet.setDeltaMovement(0.0D, 0.0D, 1.5D);
            minecraftServer.overworld().addFreshEntity(jet);
            return jet.getUUID().toString();
        });
        String mounted = server.computeOnServer(minecraftServer -> {
            var player = minecraftServer.getPlayerList().getPlayers().getFirst();
            var entity = minecraftServer.overworld().getEntity(java.util.UUID.fromString(victim));
            if (!(entity instanceof VehicleEntity jet)) {
                return "victim jet missing";
            }
            boolean ok = player.startRiding(jet, true, true);
            return String.format("mounted %s (player riding %s, jet wreck %d, passengers %d, y %.1f)", ok,
                    player.getVehicle(), jet.wreckState(), jet.getPassengers().size(), jet.getY());
        });
        this.report.append("victim: ").append(mounted).append(System.lineSeparator());
        if (!mounted.startsWith("mounted true")) {
            this.fail("could not board the jet to be shot down: " + mounted);
            return;
        }
        context.waitFor(mc -> vehicle(mc) != null);
        context.getInput().holdKey(options -> options.keyUp);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        for (int tick = 1; tick <= 30; tick++) {
            this.holdPitch(context, "victim", tick, -2.0F);
        }
        server.runCommand("damage " + victim + " 80 minecraft:generic");
        for (int tick = 1; tick <= 25; tick++) {
            this.holdPitch(context, "victim-hit", tick, -2.0F);
        }
        context.takeScreenshot("a10_damaged_light");
        server.runCommand("damage " + victim + " 50 minecraft:generic");
        for (int tick = 1; tick <= 25; tick++) {
            this.holdPitch(context, "victim-hit", tick, -2.0F);
        }
        context.takeScreenshot("a10_damaged_heavy");
        server.runCommand("damage " + victim + " 30 minecraft:generic");
        for (int tick = 1; tick <= 25; tick++) {
            this.holdPitch(context, "victim-hit", tick, -2.0F);
        }
        context.takeScreenshot("a10_damaged_burning");
        context.getInput().releaseKey(options -> options.keyUp);
        server.runCommand("damage " + victim + " 1000 minecraft:generic");
        context.waitFor(mc -> vehicle(mc) == null);
        // Watch it go down from off its wing.
        int going = 0;
        boolean blewUp = false;
        for (int tick = 1; tick <= 240 && !blewUp; tick++) {
            if (tick % 5 == 1) {
                server.runCommand("execute as @p at " + victim + " run tp @s ^40 ^10 ^-25 facing entity " + victim);
            }
            context.waitTick();
            int state = server.computeOnServer(minecraftServer ->
                    minecraftServer.overworld().getEntity(java.util.UUID.fromString(victim)) instanceof VehicleEntity jet && !jet.isRemoved()
                            ? jet.wreckState() : -1);
            if (state == VehicleEntity.WRECK_GOING_DOWN) {
                going++;
                if (going == 15 || going == 40) {
                    context.takeScreenshot("a10_going_down_" + going);
                }
            } else if (state == VehicleEntity.WRECK_BURNING || state == -1) {
                blewUp = true;
                context.waitTicks(3);
                context.takeScreenshot("a10_wreck_impact");
            }
        }
        context.waitTicks(40);
        context.takeScreenshot("a10_wreck_burning");
        this.report.append(String.format("shot down: went down for %d ticks, blew up on the ground %s%n", going, blewUp));
        if (going < 5 || !blewUp) {
            this.fail("a jet destroyed in the air did not go down and blow up (going down " + going + " ticks, blew up " + blewUp + ")");
        }
    }

    /** A third jet, flown flat out into the ground. */
    private void crash(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("ride @p dismount");
        context.waitTicks(10);
        server.runCommand("execute as @p at @s run summon jeg:a10 ~ ~70 ~60 {Rotation:[180f,0f],Motion:[0.0d,0.0d,-3.0d],Tags:[\"crash\"]}");
        context.waitTicks(1);
        server.runCommand("ride @p mount @e[type=jeg:a10,tag=crash,limit=1]");
        context.waitFor(mc -> vehicle(mc) != null);
        context.getInput().holdKey(options -> options.keyUp);
        boolean wrecked = false;
        for (int tick = 1; tick <= 300; tick++) {
            boolean aboard = context.computeOnClient(mc -> vehicle(mc) != null);
            if (!aboard) {
                wrecked = true;
                break;
            }
            this.holdPitch(context, "crash", tick, 40.0F);
        }
        context.getInput().releaseKey(options -> options.keyUp);
        context.waitTicks(10);
        context.takeScreenshot("a10_crash");
        boolean destroyed = server.computeOnServer(minecraftServer -> minecraftServer.overworld().getEntitiesOfClass(VehicleEntity.class,
                new net.minecraft.world.phys.AABB(-2000, -100, -2000, 2000, 400, 2000), v -> v.entityTags().contains("crash") && !v.isWrecked()).isEmpty());
        this.report.append(String.format("crash: thrown out %s, jet wrecked %s%n", wrecked, destroyed));
        if (!destroyed) {
            this.fail("flying into the ground at full speed left the jet intact");
        }
    }

    private void setThrottle(ClientGameTestContext context, float target) {
        for (int tick = 0; tick < 100; tick++) {
            float throttle = context.computeOnClient(mc -> vehicle(mc).jetThrottle());
            if (Math.abs(throttle - target) < 0.03F) {
                break;
            }
            if (throttle < target) {
                context.getInput().holdKey(options -> options.keyUp);
            } else {
                context.getInput().holdKey(options -> options.keyDown);
            }
            context.waitTick();
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().releaseKey(options -> options.keyDown);
        }
    }

    /** The test pilot's hand on the mouse: steer the nose towards a pitch (positive is nose down). */
    private State holdPitch(ClientGameTestContext context, String phase, int tick, float targetPitch) {
        State state = this.state(context);
        double error = state.pitch - targetPitch;
        double dy = Mth.clamp(-error * 1.2D, -28.0D, 28.0D);
        if (Math.abs(error) < 0.6D) {
            dy = 0.0D;
        }
        // Keep the wings level while doing it (the hold only acts upright).
        double dx = Mth.clamp(state.roll * 0.15D, -6.0D, 6.0D);
        if (Math.abs(state.roll) > 120.0F) {
            dx = 0.0D;
        }
        context.getInput().moveCursor(-dx, dy);
        context.waitTick();
        return this.sample(context, phase, tick);
    }

    private void record(ClientGameTestContext context, String phase, int ticks, double dx, double dy) {
        for (int tick = 1; tick <= ticks; tick++) {
            if (dx != 0.0D || dy != 0.0D) {
                context.getInput().moveCursor(dx, dy);
            }
            context.waitTick();
            this.sample(context, phase, tick);
        }
    }

    private void fail(String message) {
        this.failures.append(message).append('\n');
    }

    private record State(Vec3 position, double speed, double vy, float pitch, float yaw, float roll, double altitude,
                         boolean grounded, float throttle, float alpha, boolean stallWarning, float health, float gear) {
    }

    private State state(ClientGameTestContext context) {
        return context.computeOnClient(mc -> {
            VehicleEntity jet = vehicle(mc);
            if (jet == null) {
                return new State(Vec3.ZERO, 0, 0, 0, 0, 0, 0, true, 0, 0, false, 0, 0);
            }
            Vec3 velocity = jet.getDeltaMovement();
            return new State(jet.position(), velocity.length(), velocity.y, jet.getXRot(), jet.getYRot(), jet.roll(),
                    jet.onGround() ? 0.0D : jet.altitudeAboveGround(), jet.onGround(), jet.jetThrottle(), jet.jetAngleOfAttack(),
                    jet.jetStallWarning(), jet.vehicleHealth(), jet.jetGear());
        });
    }

    private State sample(ClientGameTestContext context, String phase, int tick) {
        State state = this.state(context);
        this.log.append(String.format("%s t%d speed=%.3f vy=%.3f pitch=%.1f roll=%.1f yaw=%.1f alt=%.1f ground=%s thr=%.2f aoa=%.1f stall=%s gear=%.2f hp=%.1f%n",
                phase, tick, state.speed, state.vy, state.pitch, state.roll, state.yaw, state.altitude, state.grounded,
                state.throttle, state.alpha, state.stallWarning, state.gear, state.health));
        return state;
    }

    private static VehicleEntity vehicle(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof VehicleEntity vehicle ? vehicle : null;
    }
}
