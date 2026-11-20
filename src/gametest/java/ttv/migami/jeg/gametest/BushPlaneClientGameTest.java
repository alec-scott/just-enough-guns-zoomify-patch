package ttv.migami.jeg.gametest;

import java.util.Locale;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;

/**
 * The bush plane (design/bushplane/BRIEF.md): a short take-off and its top speed. A husk pilot runs the engine up on
 * the brakes, lets go at full throttle with the stick held back, and the ground roll is measured until the wheels last
 * touch; then, flown in place high up (its position held, its velocity its own), it is held level at full throttle
 * until its speed settles. Fails on a ground roll over 40 blocks or under 10, not clearing 15 blocks (50 ft) within 180, or a top speed outside 2.2-2.6 blocks a
 * tick (158-187 km/h; the data's 2.5), or on any damage taken leaving the ground.
 */
public class BushPlaneClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("bush plane report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("tp @a 0 -60 -20 0 20");
            server.runCommand("forceload add -48 -16 48 460");
            context.waitTicks(10);

            // ---- Take-off.
            String planeId = this.spawn(server, -60.0D, Vec3.ZERO);
            // A taildragger sits nose up on its tailwheel at rest (drawn, BushPlaneStance), before the engine is started.
            context.waitTicks(20);
            float stanceAtRest = this.stance(context, planeId);
            // From cold the engine takes six seconds to reach idle: run it up on the brakes first, as a pilot would.
            this.fly(context, server, planeId, 240, false, true, Float.NaN, null);
            // run: the distance rolled until the wheels last touched, its tick and speed; the distance to 2 blocks up
            // (over a hedge) and to 15 (a 50 ft obstacle, as flight manuals give it); the highest it got. Climbing out the
            // pilot holds 12 degrees nose-up, a Cub's climb attitude.
            double[] run = {0.0D, 0.0D, 0.0D, Double.NaN, 0.0D, Double.NaN};
            StringBuilder trace = new StringBuilder();
            this.fly(context, server, planeId, 150, false, false, -12.0F, (plane, tick) -> {
                double height = plane.getY() + 60.0D;
                if (plane.onGround()) {
                    run[0] = plane.getZ() - 0.5D;
                    run[1] = tick;
                    run[2] = plane.getDeltaMovement().length();
                }
                if (Double.isNaN(run[3]) && height > 2.0D) {
                    run[3] = plane.getZ() - 0.5D;
                }
                if (Double.isNaN(run[5]) && height > 15.0D) {
                    run[5] = plane.getZ() - 0.5D;
                }
                run[4] = Math.max(run[4], height);
                if (tick % 5 == 0) {
                    trace.append(String.format(Locale.ROOT, "  t%d z %.1f h %.2f pitch %.1f speed %.3f ground %s hp %.0f%n", tick,
                            plane.getZ(), height, plane.getXRot(), plane.getDeltaMovement().length(), plane.onGround(), plane.vehicleHealth()));
                }
            });
            LOGGER.info("bush plane take-off trace\n{}", trace);
            float stanceFlying = this.stance(context, planeId);
            report.append(String.format(Locale.ROOT, "stance: %.1f deg nose up at rest, engine off; %.1f climbing away (NaN: out of the client's range)%n", stanceAtRest, stanceFlying));
            if (stanceAtRest < 10.0F || stanceAtRest > 11.0F) failures.append("at rest it does not sit tail down (" + stanceAtRest + " deg)\n");
            if (stanceFlying > 0.5F) failures.append("in the air it is still drawn tail down (" + stanceFlying + " deg)\n");
            String health = server.computeOnServer(s -> s.overworld().getEntity(UUID.fromString(planeId)) instanceof VehicleEntity plane
                    ? String.format(Locale.ROOT, "%.0f/%.0f", plane.vehicleHealth(), plane.maxVehicleHealth()) : "gone");
            report.append(String.format(Locale.ROOT, "ground roll %.1f blocks in %.0f ticks, unstick at %.2f b/t (%.0f km/h); 2 blocks up after %.1f, 15 (50 ft) after %.1f; climbed to %.1f; health %s%n",
                    run[0], run[1], run[2], run[2] * 72.0D, run[3], run[5], run[4], health));
            if (run[4] < 2.0D) {
                failures.append("it never climbed away in 150 ticks at full throttle\n");
            } else if (run[0] > 40.0D || run[0] < 10.0D) {
                failures.append(String.format(Locale.ROOT, "ground roll %.1f blocks (wanted 10-40; fleet plan section 8)%n", run[0]));
            }
            if (Double.isNaN(run[5]) || run[5] > 180.0D) {
                failures.append(String.format(Locale.ROOT, "over 15 blocks (50 ft) only after %.1f blocks (wanted under 180; a Super Cub's 150 m)%n", run[5]));
            }
            if (!health.matches("(\\d+)/\\1")) {
                failures.append("the take-off damaged it (" + health + ")\n");
            }
            this.remove(server, planeId);

            // ---- Hands off: the player at the controls, from cold, W held and the mouse left alone, as a player first
            // tries it, with the pilot's client predicting the flight as in play. It must stay on its wheels or fly off
            // by itself, never put its nose or a wing into the ground. (A play-test wreck at 103 km/h that looked like
            // this, 2026-11-17, was the AircraftTest sandbox's truck parked across the strip at z 120.)
            String handsOffId = server.computeOnServer(minecraftServer -> {
                var level = minecraftServer.overworld();
                VehicleEntity plane = ModEntities.TOM6.get().create(level, EntitySpawnReason.COMMAND);
                plane.snapTo(0.5D, -60.0D, 0.5D, 0.0F, 0.0F);
                level.addFreshEntity(plane);
                return plane.getUUID().toString();
            });
            server.runCommand("tp @p 2.5 -60 0.5");
            context.waitTicks(2);
            server.runCommand("ride @p mount @e[type=jeg:tom6,limit=1,sort=nearest]");
            context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
            StringBuilder handsOff = new StringBuilder();
            double[] worst = {0.0D, 0.0D};
            context.getInput().holdKey(options -> options.keyUp);
            for (int tick = 0; tick < 360; tick++) {
                int at = tick;
                server.runOnServer(minecraftServer -> {
                    if (minecraftServer.overworld().getEntity(UUID.fromString(handsOffId)) instanceof VehicleEntity plane) {
                        worst[0] = Math.max(worst[0], plane.getY() + 60.0D);
                        worst[1] = Math.max(worst[1], plane.getXRot());
                        if (at >= 150 || at % 10 == 0) {
                            handsOff.append(String.format(Locale.ROOT, "  t%d z %.2f h %.3f vy %.3f pitch %.2f speed %.3f ground %s hc %s hp %.0f wreck %s%n", at,
                                    plane.getZ(), plane.getY() + 60.0D, plane.getDeltaMovement().y, plane.getXRot(), plane.getDeltaMovement().length(),
                                    plane.onGround(), plane.horizontalCollision, plane.vehicleHealth(), plane.isWrecked()));
                        }
                    }
                });
                context.waitTick();
            }
            context.getInput().releaseKey(options -> options.keyUp);
            LOGGER.info("bush plane hands-off trace\n{}", handsOff);
            String handsOffEnd = server.computeOnServer(s -> s.overworld().getEntity(UUID.fromString(handsOffId)) instanceof VehicleEntity plane
                    ? String.format(Locale.ROOT, "%.0f/%.0f wreck %s", plane.vehicleHealth(), plane.maxVehicleHealth(), plane.isWrecked()) : "gone");
            report.append(String.format(Locale.ROOT, "hands off, W held: highest %.1f, most nose-down %.1f; end %s%n", worst[0], worst[1], handsOffEnd));
            if (!handsOffEnd.matches("(\\d+)/\\1 wreck false")) {
                failures.append("hands off with W held it crashed or was damaged (" + handsOffEnd + ")\n");
            }
            server.runCommand("ride @p dismount");
            context.waitTicks(2);
            this.remove(server, handsOffId);

            // ---- Top speed, flown in place 100 blocks up.
            String fastId = this.spawn(server, 40.0D, new Vec3(0.0D, 0.0D, 1.5D));
            double[] speed = {0.0D, 0.0D, 0.0D};
            StringBuilder cruise = new StringBuilder();
            this.fly(context, server, fastId, 1200, true, false, 0.0F, (plane, tick) -> {
                double now = plane.getDeltaMovement().length();
                if (tick >= 1100) {
                    speed[0] += now;
                    speed[1]++;
                }
                speed[2] = Math.max(speed[2], now);
                if (tick % 50 == 0) {
                    cruise.append(String.format(Locale.ROOT, "  t%d speed %.3f vy %.3f pitch %.1f%n", tick, now, plane.getDeltaMovement().y, plane.getXRot()));
                }
            });
            LOGGER.info("bush plane cruise trace\n{}", cruise);
            double top = speed[0] / Math.max(1.0D, speed[1]);
            report.append(String.format(Locale.ROOT, "top speed level at full throttle %.3f b/t (%.0f km/h), most %.3f%n", top, top * 72.0D, speed[2]));
            if (top < 2.2D || top > 2.6D) {
                failures.append(String.format(Locale.ROOT, "top speed %.3f b/t (wanted 2.2-2.6)%n", top));
            }
            this.remove(server, fastId);
        }
        LOGGER.info("{}", report);
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
        LOGGER.info("[bush-plane] COMPLETE");
    }

    private interface Probe {
        void sample(VehicleEntity plane, int tick);
    }

    private String spawn(TestServerContext server, double y, Vec3 velocity) {
        return server.computeOnServer(minecraftServer -> {
            var level = minecraftServer.overworld();
            VehicleEntity plane = ModEntities.TOM6.get().create(level, EntitySpawnReason.COMMAND);
            plane.snapTo(0.5D, y, 0.5D, 0.0F, 0.0F);
            plane.setDeltaMovement(velocity);
            level.addFreshEntity(plane);
            var pilot = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
            pilot.setNoAi(true);
            pilot.setSilent(true);
            pilot.snapTo(plane.getX(), plane.getY(), plane.getZ(), 0.0F, 0.0F);
            level.addFreshEntity(pilot);
            // Since yftm.22 only a player or tagged crew may drive or man a gun.
            pilot.addTag(ttv.migami.jeg.vehicle.ai.EnemyVehicleController.ENEMY_VEHICLE_CREW_TAG);
            pilot.startRiding(plane, true, true);
            return plane.getUUID().toString();
        });
    }

    /** The nose-up angle the client draws the plane at (its stance on the tailwheel). */
    private float stance(ClientGameTestContext context, String id) {
        return context.computeOnClient(mc -> {
            for (var entity : mc.level.entitiesForRendering()) {
                if (entity instanceof VehicleEntity v && v.getUUID().toString().equals(id)) {
                    return ttv.migami.jeg.vehicle.client.render.BushPlaneStance.of(v);
                }
            }
            return Float.NaN;
        });
    }

    private void remove(TestServerContext server, String id) {
        server.runOnServer(minecraftServer -> {
            var entity = minecraftServer.overworld().getEntity(UUID.fromString(id));
            if (entity != null) {
                entity.getPassengers().forEach(passenger -> passenger.discard());
                entity.discard();
            }
        });
    }

    /**
     * Full throttle for some ticks, on the wheel brakes or not, the stick holding the nose at a pitch (NaN: stick
     * centred); in place, the plane is put back where it started every tick (its velocity left alone).
     */
    private void fly(ClientGameTestContext context, TestServerContext server, String id, int ticks, boolean inPlace, boolean brake,
                     float pitch, Probe probe) {
        Vec3[] spot = {null};
        for (int tick = 0; tick < ticks; tick++) {
            int at = tick;
            server.runOnServer(minecraftServer -> {
                if (minecraftServer.overworld().getEntity(UUID.fromString(id)) instanceof VehicleEntity plane) {
                    float stick = Float.isNaN(pitch) ? 0.0F : Mth.clamp((pitch - plane.getXRot()) * 0.4F, -3.0F, 3.0F);
                    plane.setAiVehicleInput(new VehicleInput(true, false, false, false, brake, false, false, false, false, false,
                            false, false, -1, false, false, 0.0F, stick));
                    if (inPlace) {
                        if (spot[0] == null) {
                            spot[0] = plane.position();
                        }
                        plane.setPos(spot[0]);
                    }
                    if (probe != null) {
                        probe.sample(plane, at);
                    }
                }
            });
            context.waitTick();
        }
    }
}
