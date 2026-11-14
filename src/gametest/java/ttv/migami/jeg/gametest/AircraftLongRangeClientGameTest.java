package ttv.migami.jeg.gametest;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.render.BulletTrailRenderer;
import ttv.migami.jeg.client.render.MissileTrailRenderer;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;
import ttv.migami.jeg.vehicle.util.VehicleWeaponStats;

/**
 * The A-10's GAU-8, the helicopters' machine gun and both Mavericks fired level over real terrain, out past the
 * simulation distance and everything the server has loaded (render distance 6, simulation 5). The gun rounds start 500
 * blocks from the player and fly past them. Fails unless: every round and missile keeps moving every tick to the end of
 * its range and is then gone (rather than hanging in the air where the loaded world ends); the player, 500 blocks from
 * the muzzle, is sent each round's tracer; and the client follows each missile the whole way.
 */
public class AircraftLongRangeClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int RENDER_DISTANCE = 6;
    private static final int SIMULATION_DISTANCE = 5;
    private static final double ALTITUDE = 260.0D;
    /** A jet's speed at the trigger (200 km/h), on top of its rounds' and missiles'. */
    private static final double JET_SPEED = 2.7D;
    private static final double GUN_RANGE = 1200.0D;

    @Override
    public void runTest(ClientGameTestContext context) {
        int[] previous = context.computeOnClient(mc -> new int[]{mc.options.renderDistance().get(), mc.options.simulationDistance().get()});
        context.runOnClient(mc -> {
            mc.options.renderDistance().set(RENDER_DISTANCE);
            mc.options.simulationDistance().set(SIMULATION_DISTANCE);
        });
        StringBuilder report = new StringBuilder("aircraft long range report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).create()) {
            TestServerContext server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("tp @a 0 " + (int) ALTITUDE + " 0 -90 0");
            context.waitTicks(100);
            world.getClientLevel().waitForChunksRender();

            String[][] guns = {{"vehicle_gau8_cannon", "jet"}, {"light_machine_gun", "helicopter"}};
            for (int shot = 0; shot < guns.length; shot++) {
                this.gunShot(context, server, guns[shot][0], guns[shot][1].equals("jet"), shot == 0 ? -6.0D : 6.0D, report, failures);
                context.waitTicks(20);
            }
            String[] missiles = {"vehicle_agm65_missile", "vehicle_agm65g_missile"};
            for (int shot = 0; shot < missiles.length; shot++) {
                this.missileShot(context, server, missiles[shot], shot == 0 ? -12.0D : 12.0D, report, failures);
                context.waitTicks(20);
            }
        } finally {
            context.runOnClient(mc -> {
                mc.options.renderDistance().set(previous[0]);
                mc.options.simulationDistance().set(previous[1]);
            });
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    /** One round, fired as the vehicle fires it, from 500 blocks behind the player along +X. */
    private void gunShot(ClientGameTestContext context, TestServerContext server, String weapon, boolean jet, double z,
                         StringBuilder report, StringBuilder failures) {
        var stats = VehicleWeaponStats.get(Reference.id(weapon));
        double startX = -500.0D;
        // The muzzle's chunks are loaded, as the pilot's own presence there would do; from there on the round is on its own.
        server.runCommand(String.format(Locale.ROOT, "forceload add %d %d %d %d", (int) startX - 16, (int) z - 16, (int) startX + 16, (int) z + 16));
        context.waitTicks(40);
        AtomicReference<BulletEntity> fired = new AtomicReference<>();
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            var player = s.getPlayerList().getPlayers().getFirst();
            Vec3 velocity = new Vec3(stats.projectileSpeed() + (jet ? JET_SPEED : 0.0D), 0.0D, 0.0D);
            BulletEntity round = new BulletEntity(level, player, stats, velocity, stats.damage(), false);
            round.setVehicleRound();
            round.setHelicopterGunRound();
            round.initialisePosition(new Vec3(startX, ALTITUDE, z));
            level.addFreshEntity(round);
            round.sendTrailToClients(level);
            fired.set(round);
        });
        int id = fired.get().getId();
        double speed = fired.get().getDeltaMovement().length();
        context.waitTick();
        boolean tracer = context.computeOnClient(mc -> BulletTrailRenderer.hasTrail(id));
        double[] flight = this.follow(context, server, fired::get, 400, () -> false);
        server.runCommand("forceload remove all");
        double flown = flight[0] - startX;
        report.append(String.format(Locale.ROOT, "%s: %.1f b/t, flew %.0f blocks (to x=%.0f), gone after %.0f ticks, stalled %.0f ticks, tracer sent 500 blocks off %s%n",
                weapon, speed, flown, flight[0], flight[1], flight[2], tracer));
        if (flight[1] < 0.0D) {
            failures.append(String.format(Locale.ROOT, "%s: still in the air 400 ticks on, at x=%.0f%n", weapon, flight[0]));
        }
        if (flown < GUN_RANGE * 0.9D || flown > GUN_RANGE * 1.1D) {
            failures.append(String.format(Locale.ROOT, "%s: flew %.0f blocks, expected about %.0f%n", weapon, flown, GUN_RANGE));
        }
        if (flight[2] > 2.0D) {
            failures.append(String.format(Locale.ROOT, "%s: hung in the air for %.0f ticks%n", weapon, flight[2]));
        }
        if (!tracer) {
            failures.append(weapon).append(": the player 500 blocks from the muzzle was not sent its tracer\n");
        }
    }

    /** A Maverick launched level from a jet beside the player, along +X with no target. */
    private void missileShot(ClientGameTestContext context, TestServerContext server, String weapon, double z,
                             StringBuilder report, StringBuilder failures) {
        AtomicReference<VehicleMissileEntity> fired = new AtomicReference<>();
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            Entity owner = EntityType.MARKER.create(level, EntitySpawnReason.COMMAND);
            owner.setPos(0.0D, ALTITUDE, z);
            level.addFreshEntity(owner);
            VehicleMissileEntity missile = new VehicleMissileEntity(level, owner, null, new Vec3(8.0D, ALTITUDE - 2.0D, z),
                    new Vec3(JET_SPEED, 0.0D, 0.0D), Reference.id(weapon));
            level.addFreshEntity(missile);
            fired.set(missile);
        });
        int id = fired.get().getId();
        int[] followed = {0, 0};
        double[] flight = this.follow(context, server, fired::get, 400, () -> {
            boolean seen = context.computeOnClient(mc -> mc.level.getEntity(id) != null || MissileTrailRenderer.farMissiles() > 0);
            followed[1]++;
            if (seen) {
                followed[0]++;
            }
            return seen;
        });
        double flown = flight[0] - 8.0D;
        report.append(String.format(Locale.ROOT, "%s: flew %.0f blocks, gone after %.0f ticks, stalled %.0f ticks, client followed %d of %d ticks%n",
                weapon, flown, flight[1], flight[2], followed[0], followed[1]));
        if (flight[1] < 0.0D) {
            failures.append(String.format(Locale.ROOT, "%s: still in the air 400 ticks on, at x=%.0f%n", weapon, flight[0]));
        }
        if (flown < 500.0D) {
            failures.append(String.format(Locale.ROOT, "%s: only flew %.0f blocks%n", weapon, flown));
        }
        if (flight[2] > 2.0D) {
            failures.append(String.format(Locale.ROOT, "%s: hung in the air for %.0f ticks%n", weapon, flight[2]));
        }
        if (followed[0] < followed[1] - 6) {
            failures.append(String.format(Locale.ROOT, "%s: the client lost sight of it for %d of %d ticks%n", weapon, followed[1] - followed[0], followed[1]));
        }
    }

    /**
     * Follows a projectile on the server until it is gone: its furthest X, the server ticks it lasted (-1 if it never
     * went) and how many ticks it was alive without moving.
     */
    private double[] follow(ClientGameTestContext context, TestServerContext server, java.util.function.Supplier<? extends Entity> projectile,
                            int maxTicks, java.util.function.BooleanSupplier eachTick) {
        double maxX = projectile.get().getX();
        double lastX = maxX;
        int stalled = 0;
        long start = server.computeOnServer(s -> (long) s.getTickCount());
        for (int tick = 1; tick <= maxTicks; tick++) {
            context.waitTick();
            double[] state = server.computeOnServer(s -> new double[]{projectile.get().getX(), projectile.get().isRemoved() ? 1.0D : 0.0D, s.getTickCount() - start});
            if (state[1] > 0.0D) {
                return new double[]{Math.max(maxX, state[0]), state[2], stalled};
            }
            eachTick.getAsBoolean();
            if (state[0] - lastX < 0.1D) {
                stalled++;
            }
            lastX = state[0];
            maxX = Math.max(maxX, state[0]);
        }
        return new double[]{maxX, -1.0D, stalled};
    }
}
