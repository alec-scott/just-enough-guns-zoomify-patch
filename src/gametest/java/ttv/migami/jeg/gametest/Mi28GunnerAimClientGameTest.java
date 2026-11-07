package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.List;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

/**
 * Puts the player in the Mi-28 gunner seat and fires the chin gun while looking off to the side, first with the hull
 * level and then pitched nose-down like a helicopter in forward flight. The bullets should leave from the barrel tip
 * (in front of the turret, not under the nose) and fly along the gunner's view in both cases.
 */
public class Mi28GunnerAimClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final float LOOK_YAW = 35.0F;
    private static final float LOOK_PITCH = 12.0F;

    private record Shot(Vec3 position, Vec3 velocity, Vec3 modelMuzzle) {
    }

    private record Geometry(Vec3 turret, Vec3 barrel, Vec3 spin, Vec3 muzzle) {}
    private Geometry geometry;
    private VehicleEntity firingVehicle;

    @Override
    public void runTest(ClientGameTestContext context) {
        this.geometry = modelGeometry("mi28");
        assertCenteredSpin(this.geometry);
        assertCenteredSpin(modelGeometry("lod/mi28_lod1"));
        List<Shot> shots = new ArrayList<>();
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof BulletEntity || entity instanceof VehicleMissileEntity) {
                synchronized (shots) {
                    shots.add(new Shot(entity.position(), entity.getDeltaMovement(), modelMuzzle(this.firingVehicle, this.geometry)));
                }
            }
        });
        StringBuilder report = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("fill -2 -51 4 2 -51 8 minecraft:stone");
            server.runCommand("tp @a 0 -50 0 0 0");
            server.runCommand("execute as @p at @s run summon jeg:mi28 ~ ~ ~6 {Rotation:[0f,0f]}");
            context.waitTicks(5);
            server.runOnServer(minecraftServer -> {
                for (Entity entity : minecraftServer.overworld().getAllEntities()) {
                    if (entity instanceof VehicleEntity vehicle) {
                        vehicle.vehicleInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(Reference.id("rifle_ammo")), 64));
                        vehicle.vehicleInventory().setItem(1, new ItemStack(BuiltInRegistries.ITEM.getValue(Reference.id("medium_anti_ground_missile")), 16));
                    }
                }
            });
            server.runCommand("ride @p mount @e[type=jeg:mi28,limit=1,sort=nearest]");
            context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
            context.getInput().pressKey(KeyBindings.VEHICLE_CHANGE_SEAT);
            context.waitTicks(10);
            int seat = context.computeOnClient(mc -> vehicle(mc).getSeatIndex(mc.player));
            report.append("seat=").append(seat).append('\n');
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            // The chin gun starts with an empty belt; reload it from the hold (5 s).
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(130);

            this.fire(context, server, shots, report, "level", 0.0F);
            this.fire(context, server, shots, report, "nose-down-25", 25.0F);
            this.fire(context, server, shots, report, "nose-up-20", -20.0F);

            // Pylon weapons are bolted to the wings: with the gunner (and chin turret) looking 35 degrees off the nose,
            // every pylon muzzle must stay where the data puts it in hull space; only the chin gun swings round.
            this.pose(context, server, 0.0F);
            context.waitTicks(2);
            String muzzles = server.computeOnServer(minecraftServer -> {
                StringBuilder out = new StringBuilder();
                for (Entity entity : minecraftServer.overworld().getAllEntities()) {
                    if (entity instanceof VehicleEntity vehicle) {
                        double yaw = Math.toRadians(vehicle.getYRot());
                        var weapons = vehicle.vehicleData().defaults().weapons();
                        for (int slot = 0; slot < weapons.size(); slot++) {
                            Vec3 offset = vehicle.aiWeaponMuzzlePosition(slot).subtract(vehicle.position());
                            double localX = offset.x * Math.cos(yaw) + offset.z * Math.sin(yaw);
                            double localZ = -offset.x * Math.sin(yaw) + offset.z * Math.cos(yaw);
                            var weapon = weapons.get(slot);
                            out.append(String.format("   %s data=(%.2f, %.2f, %.2f) muzzle=(%.2f, %.2f, %.2f)%n",
                                    weapon.weaponId().getPath(), weapon.muzzleX(), weapon.muzzleY(), weapon.muzzleZ(),
                                    localX, offset.y, localZ));
                        }
                    }
                }
                return out.toString();
            });
            report.append("muzzles with the gunner looking 35 deg aside:\n").append(muzzles);
        }
        LOGGER.info("[mi28-gunner]\n{}", report);
    }

    private void fire(ClientGameTestContext context, TestServerContext server, List<Shot> shots, StringBuilder report,
                      String label, float hullPitch) {
        synchronized (shots) {
            shots.clear();
        }
        float[] hull = new float[2];
        context.getInput().holdKey(options -> options.keyAttack);
        for (int tick = 0; tick < VehicleEntity.ROTARY_GUN_SPOOL_TICKS + 12; tick++) {
            this.pose(context, server, hullPitch);
            if (tick == 10 || tick == 20 || tick == 35) {
                context.takeScreenshot("mi28_gunner_" + label + "_t" + tick);
            }
            context.waitTick();
        }
        context.getInput().releaseKey(options -> options.keyAttack);
        this.pose(context, server, hullPitch);
        Vec3 look = context.computeOnClient(mc -> mc.player.getViewVector(1.0F));
        Vec3 vehiclePos = context.computeOnClient(mc -> vehicle(mc).position());
        float vehicleYaw = context.computeOnClient(mc -> vehicle(mc).getYRot());
        context.waitTicks(5);
        List<Shot> fired;
        synchronized (shots) {
            fired = new ArrayList<>(shots);
        }
        if (fired.isEmpty()) {
            throw new AssertionError("No MI-28 rounds fired during " + label);
        }
        report.append(String.format("%s: hullPitch=%.1f look=(%.3f,%.3f,%.3f) shots=%d%n",
                label, hullPitch, look.x, look.y, look.z, fired.size()));
        double yawRadians = Math.toRadians(vehicleYaw);
        for (Shot shot : fired) {
            double muzzleError = shot.position().distanceTo(shot.modelMuzzle());
            if (muzzleError > 0.002D) {
                throw new AssertionError("MI-28 round missed the model's barrel tip by " + muzzleError
                        + " blocks: spawn=" + shot.position() + ", model=" + shot.modelMuzzle());
            }
            Vec3 offset = shot.position().subtract(vehiclePos);
            // Hull-local position (yaw only): x right-left, z forward.
            double localX = offset.x * Math.cos(yawRadians) + offset.z * Math.sin(yawRadians);
            double localZ = -offset.x * Math.sin(yawRadians) + offset.z * Math.cos(yawRadians);
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, shot.velocity().normalize().dot(look)))));
            report.append(String.format("   spawn local=(%.2f, %.2f, %.2f) offAim=%.1fdeg muzzleError=%.6f%n", localX, offset.y, localZ, angle, muzzleError));
        }
        this.checkCamera(context, false);
        context.getInput().holdKey(options -> options.keyUse);
        for (int tick = 0; tick < 5; tick++) {
            this.pose(context, server, hullPitch);
            context.waitTick();
        }
        this.checkCamera(context, true);
        context.takeScreenshot("mi28_boresight_" + label);
        context.getInput().releaseKey(options -> options.keyUse);
        context.waitTicks(5);
    }

    /** Holds the gunner's view and the hull pitch in place on both sides each tick. */
    private void pose(ClientGameTestContext context, TestServerContext server, float hullPitch) {
        server.runOnServer(minecraftServer -> {
            for (Entity entity : minecraftServer.overworld().getAllEntities()) {
                if (entity instanceof VehicleEntity vehicle) {
                    this.firingVehicle = vehicle;
                    vehicle.setXRot(hullPitch);
                    vehicle.xRotO = hullPitch;
                    for (Entity passenger : vehicle.getPassengers()) {
                        passenger.setYRot(vehicle.getYRot() + LOOK_YAW);
                        passenger.setXRot(LOOK_PITCH);
                    }
                }
            }
        });
        context.runOnClient(mc -> {
            VehicleEntity vehicle = vehicle(mc);
            vehicle.setXRot(hullPitch);
            vehicle.xRotO = hullPitch;
            mc.player.setYRot(vehicle.getYRot() + LOOK_YAW);
            mc.player.yRotO = mc.player.getYRot();
            mc.player.setXRot(LOOK_PITCH);
            mc.player.xRotO = LOOK_PITCH;
        });
    }

    private static VehicleEntity vehicle(Minecraft mc) {
        return mc.player != null && mc.player.getVehicle() instanceof VehicleEntity vehicle ? vehicle : null;
    }

    private void checkCamera(ClientGameTestContext context, boolean zoom) {
        context.runOnClient(mc -> {
            VehicleEntity heli = vehicle(mc);
            Vec3 tip = modelMuzzle(heli, this.geometry);
            Geometry behind = new Geometry(this.geometry.turret(), this.geometry.barrel(), this.geometry.spin(),
                    this.geometry.muzzle().add(0.0D, 0.0D, -1.0D));
            Vec3 bore = tip.subtract(modelMuzzle(heli, behind)).normalize();
            Vec3 camera = heli.cameraPositionFor(mc.player, 1.0F);
            Vec3 rotation = heli.cameraRotationFor(mc.player, 1.0F);
            Vec3 look = Vec3.directionFromRotation((float) rotation.y, (float) rotation.x);
            if (look.dot(bore) < 0.9999D) {
                throw new AssertionError("The MI-28 camera must point along the model's barrel");
            }
            if (zoom) {
                Vec3 offset = camera.subtract(tip);
                if (offset.cross(bore).length() > 0.002D || offset.dot(bore) < 0.01D || offset.dot(bore) > 0.1D) {
                    throw new AssertionError("The zoom camera must sit on the bore, clear of the muzzle: " + offset);
                }
            } else {
                Vec3 right = new Vec3(-bore.z, 0.0D, bore.x).normalize();
                if (Math.abs(tip.subtract(camera).dot(right)) > 0.05D || camera.distanceTo(tip) > 2.0D) {
                    throw new AssertionError("The gunner camera must follow the barrel instead of orbiting away from it");
                }
            }
        });
    }

    private static Geometry modelGeometry(String model) {
        String path = "/assets/jeg/geckolib/models/entity/vehicle/" + model + ".geo.json";
        try (var input = Mi28GunnerAimClientGameTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new AssertionError("Missing model " + path);
            }
            JsonArray bones = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            JsonObject turret = bone(bones, "turret");
            JsonObject barrel = bone(bones, "barrel");
            JsonObject spin = bone(bones, "barrel2");
            JsonObject shaft = bone(bones, "bone72");
            JsonArray axis = shaft.getAsJsonArray("cubes").get(0).getAsJsonObject().getAsJsonArray("pivot");
            JsonObject tip = model.startsWith("lod/") ? shaft : bone(bones, "breaker");
            double tipZ = Double.POSITIVE_INFINITY;
            for (var element : tip.getAsJsonArray("cubes")) {
                tipZ = Math.min(tipZ, element.getAsJsonObject().getAsJsonArray("origin").get(2).getAsDouble());
            }
            Vec3 muzzle = new Vec3(axis.get(0).getAsDouble() / 16.0D, axis.get(1).getAsDouble() / 16.0D, -tipZ / 16.0D);
            return new Geometry(modelPoint(turret.getAsJsonArray("pivot")), modelPoint(barrel.getAsJsonArray("pivot")),
                    modelPoint(spin.getAsJsonArray("pivot")), muzzle);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static JsonObject bone(JsonArray bones, String name) {
        for (var element : bones) {
            JsonObject bone = element.getAsJsonObject();
            if (name.equals(bone.get("name").getAsString())) {
                return bone;
            }
        }
        throw new AssertionError("Missing model bone " + name);
    }

    private static Vec3 modelPoint(JsonArray point) {
        return new Vec3(point.get(0).getAsDouble() / 16.0D, point.get(1).getAsDouble() / 16.0D, -point.get(2).getAsDouble() / 16.0D);
    }

    private static void assertCenteredSpin(Geometry model) {
        double radius = Math.hypot(model.spin().x - model.muzzle().x, model.spin().y - model.muzzle().y);
        if (radius > 0.0001D) {
            throw new AssertionError("The spinning barrel orbits its bore by " + radius + " blocks");
        }
    }

    /** Derive the tip from the rendered model pivots, independently of the vehicle's configured muzzle. */
    private static Vec3 modelMuzzle(VehicleEntity vehicle, Geometry model) {
        if (vehicle == null) {
            throw new AssertionError("No MI-28 was selected for muzzle validation");
        }
        Vec3 origin = vehicle.position();
        double pivotY = vehicle.rotateOffsetHeight();
        Vec3 barrelOffset = model.barrel().subtract(model.turret());
        Vec3 tipOffset = model.muzzle().subtract(model.barrel());
        Matrix4d transform = new Matrix4d().translate(origin.x, origin.y + pivotY, origin.z)
                .rotateY(Math.toRadians(-vehicle.getYRot())).rotateX(Math.toRadians(vehicle.getXRot()))
                .rotateZ(Math.toRadians(vehicle.roll())).translate(0.0D, -pivotY, 0.0D)
                .translate(model.turret().x, model.turret().y, model.turret().z)
                .rotateY(Math.toRadians(vehicle.turretYaw())).translate(barrelOffset.x, barrelOffset.y, barrelOffset.z)
                .rotateX(Math.toRadians(vehicle.turretPitch())).translate(tipOffset.x, tipOffset.y, tipOffset.z);
        Vector3d tip = transform.transformPosition(new Vector3d());
        return new Vec3(tip.x, tip.y, tip.z);
    }
}
