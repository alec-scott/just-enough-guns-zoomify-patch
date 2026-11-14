package ttv.migami.jeg.gametest;

import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;

/**
 * The A-10's level top speed and what a long GAU-8 burst does to it. An AI-flown jet, its engines wound up to full power
 * while it is kept flying level in place, is let go 100 blocks up and flown hands off at full throttle until it settles at its top speed,
 * then fires a ten-second burst, then stops. Fails unless: it tops out at about 200 km/h; the gun takes a quarter of
 * that off within two seconds; it never slows below flying speed or warns of a stall while firing; it picks up speed
 * again once the gun stops; and the rounds leave about two degrees below the nose, along the HUD marker. Last, the
 * player sits in a parked jet for screenshots of the gun marker from the chase camera and the cockpit.
 */
public class A10GunRecoilClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final double KMH = 72.0D;

    private record Sample(double speed, double y, boolean stallWarning, boolean firing, float spool, double z, float health) {}

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder log = new StringBuilder();
        StringBuilder failures = new StringBuilder();
        StringBuilder report = new StringBuilder("a10 gun recoil report\n");
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 -60 -20 0 0");
            context.waitTicks(20);
            String jetId = server.computeOnServer(minecraftServer -> {
                var level = minecraftServer.overworld();
                VehicleEntity jet = ModEntities.A10.get().create(level, EntitySpawnReason.COMMAND);
                jet.snapTo(0.5D, 40.0D, 0.5D, 0.0F, 0.0F);
                level.addFreshEntity(jet);
                var pilot = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
                pilot.setNoAi(true);
                pilot.setSilent(true);
                pilot.snapTo(jet.getX(), jet.getY(), jet.getZ(), 0.0F, 0.0F);
                level.addFreshEntity(pilot);
                pilot.startRiding(jet, true, true);
                jet.addAmmoForAi(Reference.id("rifle_ammo"), 640);
                jet.reloadAiVehicleWeapons();
                return jet.getUUID().toString();
            });
            double top = server.computeOnServer(minecraftServer -> jet(minecraftServer, jetId).jetTopSpeed());
            double stall = server.computeOnServer(minecraftServer -> jet(minecraftServer, jetId).vehicleData().defaults().engine().jetStallSpeed());

            // Wind the engines up to full power, flying level at speed but put back where it started every tick.
            for (int tick = 0; tick < 200; tick++) {
                server.runOnServer(minecraftServer -> {
                    VehicleEntity jet = jet(minecraftServer, jetId);
                    fly(jet, false);
                    jet.setDeltaMovement(0.0D, 0.0D, top * 0.97D);
                    jet.setPos(0.5D, 40.0D, 0.5D);
                });
                context.waitTick();
            }
            // From here on it is put back to the same spot after every tick, keeping its speed and attitude (the flight does
            // not depend on where it is), so it never leaves the loaded chunks; the player watches from behind.
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a -5.5 49 -33.5 0 12");
            context.runOnClient(mc -> mc.options.hideGui = true);

            // Hands off at full throttle until it settles.
            double cruiseTop = 0.0D;
            for (int tick = 1; tick <= 280; tick++) {
                Sample sample = this.step(context, server, jetId, false, log, "cruise", tick);
                if (tick > 220) {
                    cruiseTop = Math.max(cruiseTop, sample.speed);
                }
            }
            report.append(String.format(Locale.ROOT, "level top speed %.3f b/t (%.0f km/h), data top %.3f (%.0f km/h), stall %.3f (%.0f km/h)%n",
                    cruiseTop, cruiseTop * KMH, top, top * KMH, stall, stall * KMH));
            if (cruiseTop < 0.93D * top || cruiseTop > 1.04D * top || top * KMH < 195.0D || top * KMH > 205.0D) {
                failures.append(String.format(Locale.ROOT, "level top speed %.0f km/h, expected about 200%n", cruiseTop * KMH));
            }

            // A ten-second burst.
            double before = this.speed(server, jetId);
            double afterTwo = 0.0D;
            double atFirstRound = 0.0D;
            int firstRound = -1;
            double lowest = Double.MAX_VALUE;
            boolean warned = false;
            int firingTicks = 0;
            double depressionSum = 0.0D;
            double offMarkerSum = 0.0D;
            int rounds = 0;
            for (int tick = 1; tick <= 200; tick++) {
                Sample sample = this.step(context, server, jetId, true, log, "burst", tick);
                // The barrels spin up before the first round: two seconds from then.
                if (firstRound < 0 && sample.firing) {
                    firstRound = tick;
                    atFirstRound = sample.speed;
                }
                if (firstRound > 0 && tick == firstRound + 40) {
                    afterTwo = sample.speed;
                }
                if (tick == 30) {
                    context.takeScreenshot("a10_burst_chase");
                }
                lowest = Math.min(lowest, sample.speed);
                warned |= sample.stallWarning;
                if (sample.firing) {
                    firingTicks++;
                }
                // Fresh rounds: how far below the nose they leave (their own speed, without the jet's), and how far below the HUD
                // marker's line (the marker is 250 blocks out from the muzzle, measured here from the jet: a few tenths of a
                // degree apart).
                double[] shots = server.computeOnServer(minecraftServer -> {
                    VehicleEntity jet = jet(minecraftServer, jetId);
                    Vec3 marker = jet.jetGunAimPoint(1.0F, 250.0D).subtract(jet.position()).normalize();
                    double markerDown = Math.toDegrees(-Math.asin(marker.y));
                    double noseDown = jet.getXRot();
                    double depression = 0.0D;
                    double offMarker = 0.0D;
                    int count = 0;
                    for (BulletEntity bullet : minecraftServer.overworld().getEntitiesOfClass(BulletEntity.class,
                            jet.getBoundingBox().inflate(40.0D), bullet -> bullet.tickCount <= 1)) {
                        Vec3 direction = bullet.getDeltaMovement().subtract(jet.getDeltaMovement()).normalize();
                        depression += Math.toDegrees(-Math.asin(direction.y)) - noseDown;
                        offMarker += Math.toDegrees(-Math.asin(bullet.getDeltaMovement().normalize().y)) - markerDown;
                        count++;
                    }
                    return new double[]{depression, offMarker, count};
                });
                depressionSum += shots[0];
                offMarkerSum += shots[1];
                rounds += (int) shots[2];
            }
            double drop = afterTwo > 0.0D ? 1.0D - afterTwo / atFirstRound : 0.0D;
            report.append(String.format(Locale.ROOT, "burst: trigger at %.0f km/h, first round at t%d, %.0f km/h -> %.0f km/h 2 s later (%.0f%% slower), lowest %.0f km/h (%.2f x stall), stall warning %s, gun firing %d of 200 ticks%n",
                    before * KMH, firstRound, atFirstRound * KMH, afterTwo * KMH, drop * 100.0D, lowest * KMH, lowest / stall, warned, firingTicks));
            if (firingTicks < 150) {
                failures.append("the gun did not keep firing through the burst\n");
            }
            if (drop < 0.22D) {
                failures.append(String.format(Locale.ROOT, "a two-second burst only took %.0f%% off the speed%n", drop * 100.0D));
            }
            if (lowest < 1.25D * stall || warned) {
                failures.append(String.format(Locale.ROOT, "firing slowed the jet to %.2f x its stall speed (warning %s)%n", lowest / stall, warned));
            }
            double depression = rounds > 0 ? depressionSum / rounds : Double.NaN;
            double offMarker = rounds > 0 ? offMarkerSum / rounds : Double.NaN;
            report.append(String.format(Locale.ROOT, "rounds: %d sampled, on average %.2f degrees below the nose and %.2f below the marker%n",
                    rounds, depression, offMarker));
            if (rounds < 20 || !(depression > 1.0D && depression < 3.0D) || !(Math.abs(offMarker) < 0.8D)) {
                failures.append("the rounds did not leave about two degrees below the nose, along the marker\n");
            }

            // Guns off: it picks up speed again.
            for (int tick = 1; tick <= 100; tick++) {
                this.step(context, server, jetId, false, log, "after", tick);
            }
            double recovered = this.speed(server, jetId);
            report.append(String.format(Locale.ROOT, "five seconds after the burst: %.0f km/h%n", recovered * KMH));
            if (recovered < lowest * 1.1D) {
                failures.append("the jet did not pick up speed again after the burst\n");
            }
            server.runOnServer(minecraftServer -> {
                VehicleEntity jet = jet(minecraftServer, jetId);
                jet.ejectPassengers();
                jet.discard();
            });

            // The marker, parked, with the player at the controls.
            server.runCommand("gamemode creative @a");
            context.runOnClient(mc -> mc.options.hideGui = false);
            server.runCommand("tp @a 0 -60 0 0 0");
            context.waitTicks(10);
            server.runCommand("execute as @p at @s run summon jeg:a10 ~ ~ ~6 {Rotation:[0f,0f]}");
            context.waitTicks(10);
            server.runCommand("ride @p mount @e[type=jeg:a10,limit=1,sort=nearest]");
            context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(20);
            context.takeScreenshot("a10_gun_marker_chase");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.waitTicks(10);
            context.takeScreenshot("a10_gun_marker_cockpit");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        } finally {
            context.runOnClient(mc -> mc.options.hideGui = false);
            LOGGER.info("[a10-gun]\n{}", log);
            LOGGER.info("{}", report);
        }
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    private Sample step(ClientGameTestContext context, TestServerContext server, String jetId, boolean fire, StringBuilder log, String phase, int tick) {
        server.runOnServer(minecraftServer -> fly(jet(minecraftServer, jetId), fire));
        context.waitTick();
        Sample sample = server.computeOnServer(minecraftServer -> {
            VehicleEntity jet = jet(minecraftServer, jetId);
            Sample now = new Sample(jet.getDeltaMovement().length(), jet.getY(), jet.jetStallWarning(), jet.isJetGunFiring(), jet.jetSpool(),
                    jet.getZ(), jet.vehicleHealth());
            jet.setPos(0.5D, jet.getY(), 0.5D);
            return now;
        });
        log.append(String.format(Locale.ROOT, "%s t%d speed=%.3f (%.0f km/h) y=%.1f z=%.1f stall=%s firing=%s spool=%.2f hp=%.0f%n",
                phase, tick, sample.speed, sample.speed * KMH, sample.y, sample.z, sample.stallWarning, sample.firing, sample.spool, sample.health));
        return sample;
    }

    private double speed(TestServerContext server, String jetId) {
        return server.computeOnServer(minecraftServer -> jet(minecraftServer, jetId).getDeltaMovement().length());
    }

    /** Full throttle, hands off the stick, the gun on or off. */
    private static void fly(VehicleEntity jet, boolean fire) {
        jet.setAiVehicleInput(new VehicleInput(true, false, false, false, false, false, false, false, false, false,
                false, false, -1, false, false, 0.0F, 0.0F));
        if (jet.getControllingPassenger() != null) {
            jet.setAiWeaponControl(jet.getControllingPassenger(), fire, false);
        }
    }

    private static VehicleEntity jet(net.minecraft.server.MinecraftServer server, String id) {
        if (server.overworld().getEntity(UUID.fromString(id)) instanceof VehicleEntity jet) {
            return jet;
        }
        throw new AssertionError("the jet disappeared");
    }
}
