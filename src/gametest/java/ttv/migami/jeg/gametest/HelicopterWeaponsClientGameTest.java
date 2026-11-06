package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.client.FabricClientBootstrap;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.init.ModSounds;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;
import ttv.migami.jeg.vehicle.util.VehicleSoundHelper;
import ttv.migami.jeg.vehicle.util.VehicleWeaponStats;

/**
 * Hovers in front of a wall, works out where the crosshair sits on it, and measures how far the rounds land from that, from
 * the chase camera and first person, for the gun and the rockets. Also leaves the pilot seat mid-air to check the helicopter
 * holds altitude, fires the big missile for the shot of it flying and going off, and shoots a block of leaves down.
 */
public class HelicopterWeaponsClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final int WALL_Z = 90;

    private record Shot(Vec3 position, Vec3 velocity) {}

    private final List<Shot> shots = new ArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof BulletEntity || entity instanceof VehicleMissileEntity) {
                synchronized (this.shots) {
                    this.shots.add(new Shot(entity.position(), entity.getDeltaMovement()));
                }
            }
        });
        StringBuilder report = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            // The wall is 90 blocks away. A low-distance test client otherwise aims into unloaded air at 320 blocks,
            // measuring parallax against a wall the aiming ray cannot see.
            context.runOnClient(mc -> {
                mc.options.renderDistance().set(8);
                mc.options.broadcastOptions();
            });
            server.runOnServer(minecraftServer -> minecraftServer.getPlayerList().setViewDistance(8));
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 0");
            server.runCommand("fill -60 -60 " + WALL_Z + " 60 40 " + WALL_Z + " minecraft:stone");
            context.waitFor(mc -> mc.level.getBlockState(new BlockPos(0, -40, WALL_Z)).is(Blocks.STONE));

            this.mount(context, server, "ah6");
            this.climb(context);
            this.prepare(server);
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(130);
            this.accuracy(context, report, "ah6 gun chase", CameraType.THIRD_PERSON_BACK);
            this.accuracy(context, report, "ah6 gun fp", CameraType.FIRST_PERSON);
            context.takeScreenshot("heli_ah6_gun_fp");

            context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitTicks(5);
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(120);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            this.accuracy(context, report, "ah6 rocket chase", CameraType.THIRD_PERSON_BACK);
            context.getInput().holdKey(options -> options.keyAttack);
            for (int tick = 1; tick <= 16; tick++) {
                context.waitTick();
                if (tick % 2 == 0) {
                    context.takeScreenshot("heli_ah6_rocket_t" + tick);
                }
            }
            context.getInput().releaseKey(options -> options.keyAttack);

            server.runCommand("ride @p dismount");
            server.runCommand("kill @e[type=jeg:ah6]");
            context.waitTicks(5);
            server.runCommand("tp @a 0 -60 0 0 0");
            context.waitTicks(10);

            this.mount(context, server, "mi28");
            this.climb(context);
            this.prepare(server);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));

            // Leave the pilot seat in forward flight: it should hold its height and glide to a hover.
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(40);
            context.getInput().releaseKey(options -> options.keyUp);
            double before = context.computeOnClient(mc -> vehicle(mc).getY());
            context.getInput().pressKey(KeyBindings.VEHICLE_CHANGE_SEAT);
            StringBuilder hover = new StringBuilder();
            for (int tick = 1; tick <= 200; tick++) {
                context.waitTick();
                if (tick % 25 == 0) {
                    final int at = tick;
                    String line = context.computeOnClient(mc -> String.format("t%d seat=%d y=%.2f speed=%.3f roll=%.1f%n",
                            at, vehicle(mc).getSeatIndex(mc.player), vehicle(mc).getY(),
                            vehicle(mc).getDeltaMovement().horizontalDistance(), vehicle(mc).roll()));
                    hover.append(line);
                }
            }
            double after = context.computeOnClient(mc -> vehicle(mc).getY());
            report.append(String.format("hover without a pilot: y %.2f -> %.2f%n%s", before, after, hover));

            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(130);
            this.accuracy(context, report, "mi28 gunner chin gun", CameraType.FIRST_PERSON);

            // The big missile, seen from the chase view as it flies out and hits the wall.
            context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitTicks(3);
            context.getInput().pressKey(KeyBindings.VEHICLE_SWITCH_WEAPON);
            context.waitTicks(3);
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(400);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(10);
            synchronized (this.shots) {
                this.shots.clear();
            }
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(2);
            context.getInput().releaseKey(options -> options.keyAttack);
            for (int tick = 1; tick <= 60; tick++) {
                context.waitTick();
                if (tick <= 16 ? tick % 2 == 0 : tick % 5 == 0) {
                    context.takeScreenshot("heli_mi28_missile_t" + tick);
                }
                if (tick % 6 == 0) {
                    String line = context.computeOnClient(mc -> {
                        StringBuilder out = new StringBuilder();
                        for (Entity entity : mc.level.entitiesForRendering()) {
                            if (entity instanceof VehicleMissileEntity missile) {
                                out.append(String.format("missile z=%.1f y=%.1f speed=%.2f weapon=%s%n", missile.getZ(), missile.getY(),
                                        missile.getDeltaMovement().length(), missile.missileWeaponId().getPath()));
                            }
                        }
                        return out.toString();
                    });
                    report.append(line);
                }
            }

            // Leaves and grass between the guns and the wall.
            server.runCommand("ride @p dismount");
            server.runCommand("kill @e[type=jeg:mi28]");
            context.waitTicks(5);
            server.runCommand("tp @a 0 -60 0 0 0");
            context.waitTicks(10);
            this.mount(context, server, "ah6");
            this.climb(context);
            this.prepare(server);
            double y = context.computeOnClient(mc -> vehicle(mc).getY());
            int low = (int) y - 6;
            int high = (int) y + 6;
            String leaves = "fill -10 " + low + " 40 10 " + high + " 41 minecraft:oak_leaves[persistent=true]";
            server.runCommand(leaves);
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(130);
            int leavesBefore = this.countLeaves(server, low, high);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(60);
            context.getInput().releaseKey(options -> options.keyAttack);
            context.waitTicks(5);
            context.takeScreenshot("heli_ah6_leaves");
            int leavesAfter = this.countLeaves(server, low, high);
            report.append(String.format("leaves shot through: %d -> %d%n", leavesBefore, leavesAfter));
        }
        LOGGER.info("[heli-weapons]\n{}", report);
    }

    private int countLeaves(TestServerContext server, int low, int high) {
        return server.computeOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            int count = 0;
            for (BlockPos position : BlockPos.betweenClosed(-10, low, 40, 10, high, 41)) {
                if (level.getBlockState(position).is(BlockTags.LEAVES)) {
                    count++;
                }
            }
            return count;
        });
    }

    private void mount(ClientGameTestContext context, TestServerContext server, String id) {
        server.runCommand("execute as @p at @s run summon jeg:" + id + " ~ ~ ~6 {Rotation:[0f,0f]}");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=jeg:" + id + ",limit=1,sort=nearest]");
        context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
    }

    private void climb(ClientGameTestContext context) {
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.getInput().holdKey(options -> options.keyJump);
        int startup = context.computeOnClient(mc -> vehicle(mc).helicopterStartupTicks());
        context.waitTicks(startup + 90);
        context.getInput().releaseKey(options -> options.keyJump);
        context.waitTicks(50);
    }

    private void prepare(TestServerContext server) {
        server.runOnServer(minecraftServer -> {
            for (Entity entity : minecraftServer.overworld().getAllEntities()) {
                if (entity instanceof VehicleEntity vehicle) {
                    Identifier expected = Reference.id("ah6".equals(vehicle.vehicleDataId().getPath())
                            ? "vehicle.ah6.cannon_fire" : "item.minigun.fire_loop");
                    for (var weapon : vehicle.vehicleData().defaults().weapons()) {
                        if (VehicleSoundHelper.isHelicopterGun(vehicle, weapon)
                                && VehicleSoundHelper.fireSound(vehicle, weapon, VehicleWeaponStats.get(weapon.weaponId())) != ModSounds.ALL.get(expected).get()) {
                            throw new AssertionError("Wrong gun sound for " + vehicle.vehicleDataId() + ": expected " + expected);
                        }
                    }
                    LOGGER.info("[heli-gun-audio] {} uses {}", vehicle.vehicleDataId(), expected);
                    String[] ammo = {"rifle_ammo", "small_rocket", "medium_anti_ground_missile", "large_anti_ground_missile"};
                    for (int slot = 0; slot < ammo.length; slot++) {
                        Identifier id = Reference.id(ammo[slot]);
                        vehicle.vehicleInventory().setItem(slot, new ItemStack(BuiltInRegistries.ITEM.getValue(id), 64));
                    }
                }
            }
        });
    }

    /** Fires for a moment and compares each round's line, where it crosses the wall, with the crosshair's point on the wall. */
    private void accuracy(ClientGameTestContext context, StringBuilder report, String label, CameraType camera) {
        context.runOnClient(mc -> mc.options.setCameraType(camera));
        context.waitFor(mc -> mc.gameRenderer.getMainCamera().isDetached() == !camera.isFirstPerson());
        context.waitTicks(10);
        Vec3 crosshair = context.computeOnClient(this::crosshairOnWall);
        synchronized (this.shots) {
            this.shots.clear();
        }
        context.getInput().holdKey(options -> options.keyAttack);
        context.waitTicks(16);
        boolean gun = label.contains("gun");
        boolean audioActive = context.computeOnClient(mc -> FabricClientBootstrap.isVehicleFireAudioActive(vehicle(mc).getId()));
        if (gun) {
            context.takeScreenshot("heli_" + label.replace(' ', '_') + "_firing");
        }
        context.getInput().releaseKey(options -> options.keyAttack);
        context.waitTicks(10);
        if (gun) {
            boolean stopped = context.computeOnClient(mc -> !FabricClientBootstrap.isVehicleFireAudioActive(vehicle(mc).getId()));
            report.append(label).append(" soundChannelActive=").append(audioActive).append(" stoppedAfterRelease=").append(stopped).append('\n');
            if (!audioActive || !stopped) {
                throw new AssertionError("Gun audio must have active playback while firing and stop on release: " + report);
            }
        }
        double total = 0.0D;
        double worst = 0.0D;
        int count = 0;
        double sumX = 0.0D;
        double sumY = 0.0D;
        synchronized (this.shots) {
            for (Shot shot : this.shots) {
                if (shot.velocity.z <= 0.01D) {
                    continue;
                }
                double t = (WALL_Z - shot.position.z) / shot.velocity.z;
                Vec3 at = shot.position.add(shot.velocity.scale(t));
                double miss = Math.hypot(at.x - crosshair.x, at.y - crosshair.y);
                total += miss;
                sumX += at.x - crosshair.x;
                sumY += at.y - crosshair.y;
                worst = Math.max(worst, miss);
                count++;
                if (count <= 3) {
                    report.append(String.format("   shot pos=(%.2f, %.2f, %.2f) vel=(%.3f, %.3f, %.3f) at wall=(%.2f, %.2f)%n",
                            shot.position.x, shot.position.y, shot.position.z, shot.velocity.x, shot.velocity.y, shot.velocity.z, at.x, at.y));
                }
            }
        }
        if (count == 0) {
            throw new AssertionError("No rounds fired during " + label + ": " + report);
        }
        report.append(String.format("%s: rounds=%d crosshair=(%.1f, %.1f) avgMiss=%.2f blocks worst=%.2f offset=(%.2f, %.2f)%n",
                label, count, crosshair.x, crosshair.y, count == 0 ? 0.0D : total / count, worst,
                count == 0 ? 0.0D : sumX / count, count == 0 ? 0.0D : sumY / count));
    }

    private Vec3 crosshairOnWall(Minecraft mc) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 look = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        Vec3 from = camera.position();
        double t = (WALL_Z - from.z) / Math.max(1.0E-4D, look.z);
        return from.add(look.scale(t));
    }

    private static VehicleEntity vehicle(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof VehicleEntity vehicle ? vehicle : null;
    }
}
