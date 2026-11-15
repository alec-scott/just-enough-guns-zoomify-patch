package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.support.SupportModules;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * How the FSV-6 looks and sounds, from the places a player looks from. Mostly screenshots to look at:
 * <ul>
 *     <li>packed: the side (the wheels, the outside lockers), and the cab's instruments from the driver's seat by day,
 *     on the move and at night, and from the co-driver's seat;</li>
 *     <li>deployed: the bays fitted with a chest, trapped chest and ender chest (drawn from their item models) beside a
 *     barrel, crafting table and furnace (block models), and the slide-out's front end from outside (the bunk's bed
 *     must not show through it).</li>
 * </ul>
 * Checked: the roof beacon sounds as a beacon does when it lights (deploying, mast up) and when it goes out (packing up).
 */
public class SupportLookClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private String id;
    private final List<String> beaconSounds = Collections.synchronizedList(new ArrayList<>());
    private final net.minecraft.client.sounds.SoundEventListener listener = (sound, events, range) -> {
        String path = sound.getIdentifier().getPath();
        if (path.startsWith("block.beacon")) this.beaconSounds.add(path);
    };

    @Override public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("time set noon");
            server.runCommand("gamemode creative @a");
            server.runOnServer(s -> {
                SupportVehicleEntity truck = ModEntities.TRUCK.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                truck.snapTo(0, G, 0.5, 0, 0);
                truck.addEnergy(truck.maxVehicleEnergy());
                s.overworld().addFreshEntity(truck);
                this.id = truck.getUUID().toString();
                var fit = truck.modules().fittings;
                fit.setItem(0, new ItemStack(Items.CHEST));
                fit.setItem(1, new ItemStack(Items.TRAPPED_CHEST));
                fit.setItem(2, new ItemStack(Items.ENDER_CHEST));
                fit.setItem(3, new ItemStack(Items.BARREL));
                fit.setItem(4, new ItemStack(Items.CRAFTING_TABLE));
                fit.setItem(5, new ItemStack(Items.FURNACE));
                fit.setItem(SupportModules.BUNK_LEFT, new ItemStack(Items.RED_BED));
                fit.setItem(SupportModules.BUNK_RIGHT, new ItemStack(Items.BLUE_BED));
                fit.setItem(SupportModules.LOCKER_LEFT, new ItemStack(Items.BARREL));
                fit.setItem(SupportModules.LAMP, new ItemStack(Items.LANTERN));
                fit.setItem(SupportModules.ROOF, new ItemStack(Items.BEACON));
                for (int i = 0; i < SupportModules.RACK_SLOTS; i++) fit.setItem(SupportModules.RACK_START + i, new ItemStack(Items.IRON_INGOT, 64));
                truck.modules().setBeaconEffect(BeaconMenu.encodeEffect(MobEffects.SPEED));
                fit.setChanged();
            });
            context.runOnClient(mc -> mc.getSoundManager().addListener(this.listener));
            context.waitTicks(10);

            // ---- Packed, from outside: the left side (wheels, the left locker).
            this.view(context, server, 96, 10, 0, 0, 16, 0);
            context.takeScreenshot("support_look_side");
            this.view(context, server, 64, 14, -120, 0, 22, -24);
            context.takeScreenshot("support_look_front_quarter");

            // ---- The cab's instruments: driver by day, driving, at night; then the co-driver.
            server.runOnServer(s -> player(s).startRiding(truck(s), true, true));
            context.waitTicks(SupportVehicleEntity.START_TICKS + 15);
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
                mc.player.setYRot(truck(mc));
                mc.player.setXRot(28);
            });
            context.waitTicks(10);
            context.takeScreenshot("support_dash_driver");
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(50);
            context.runOnClient(mc -> { mc.player.setYRot(truck(mc)); mc.player.setXRot(28); });
            context.waitTicks(2);
            context.takeScreenshot("support_dash_driving");
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().holdKey(options -> options.keyDown);
            context.waitTicks(30);
            context.getInput().releaseKey(options -> options.keyDown);
            context.waitTicks(40);
            server.runCommand("time set midnight");
            context.waitTicks(10);
            context.runOnClient(mc -> { mc.player.setYRot(truck(mc)); mc.player.setXRot(28); });
            context.waitTicks(4);
            context.takeScreenshot("support_dash_night");
            server.runCommand("time set noon");
            context.getInput().pressKey(options -> ttv.migami.jeg.client.KeyBindings.VEHICLE_CHANGE_SEAT);
            context.waitTicks(10);
            context.runOnClient(mc -> { mc.player.setYRot(truck(mc)); mc.player.setXRot(48); });
            context.waitTicks(6);
            context.takeScreenshot("support_dash_codriver");
            context.runOnClient(mc -> mc.options.hideGui = false);

            // ---- Deploy, with the player well clear; the beacon lights once the mast is up.
            server.runOnServer(s -> {
                player(s).stopRiding();
                player(s).teleportTo(s.overworld(), 14, G, -12, Set.of(), 0, 0, true);
            });
            context.waitTicks(10);
            this.beaconSounds.clear();
            String deploy = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                truck.setDeltaMovement(Vec3.ZERO);
                return truck.requestDeploy(player(s)).name();
            });
            context.waitTicks(SupportRig.DEPLOY_TICKS + 30);
            boolean deployed = server.computeOnServer(s -> truck(s).isDeployed());
            boolean lit = server.computeOnServer(s -> truck(s).beaconLit());
            List<String> lighting = new ArrayList<>(this.beaconSounds);
            LOG.info("[support-look] deploy {}: deployed {}, beacon lit {}, sounds {}", deploy, deployed, lit, lighting);
            if (!deployed) failures.append("did not deploy: ").append(deploy).append('\n');
            if (!lighting.contains("block.beacon.activate")) failures.append("no beacon activate sound when it lit: ").append(lighting).append('\n');

            context.runOnClient(mc -> mc.options.hideGui = true);
            // The bays from the aisle by the door: left (chests) and right (barrel, crafting table, furnace).
            for (float turn : new float[]{-35, 35}) {
                server.runOnServer(s -> {
                    SupportVehicleEntity truck = truck(s);
                    Vec3 at = SupportRig.world(truck, 0, SupportRig.FLOOR_Y, 52);
                    player(s).teleportTo(s.overworld(), at.x, at.y, at.z, Set.of(), truck.getYRot() + turn, 20, true);
                });
                context.waitTicks(8);
                context.runOnClient(mc -> { mc.player.setYRot(truck(mc) + turn); mc.player.setXRot(20); });
                context.waitTicks(6);
                context.takeScreenshot(turn < 0 ? "support_look_bays_left" : "support_look_bays_right");
            }
            // The partition's live panels from the aisle: the support console and the beacon terminal (lit).
            this.view(context, server, 0, SupportRig.FLOOR_Y, 6, 0, SupportRig.FLOOR_Y + 21, -23);
            context.takeScreenshot("support_look_wall_panels");
            // The left slide-out's front end from outside, ahead of it (the bunk's bed is just behind that wall).
            this.view(context, server, 64, 20, -72, 26, 26, -24);
            context.takeScreenshot("support_look_slide_front");
            context.runOnClient(mc -> mc.options.hideGui = false);

            // ---- Pack up: the beacon goes out.
            server.runOnServer(s -> player(s).teleportTo(s.overworld(), 14, G, -12, Set.of(), 0, 0, true));
            context.waitTicks(5);
            this.beaconSounds.clear();
            String pack = server.computeOnServer(s -> truck(s).requestPackUp().name());
            context.waitTicks(SupportRig.DEPLOY_TICKS + 20);
            List<String> going = new ArrayList<>(this.beaconSounds);
            LOG.info("[support-look] pack up {}: packed {}, sounds {}", pack, server.computeOnServer(s -> truck(s).isPacked()), going);
            if (!going.contains("block.beacon.deactivate")) failures.append("no beacon deactivate sound when it went out: ").append(going).append('\n');
            context.runOnClient(mc -> mc.getSoundManager().removeListener(this.listener));
        }
        if (failures.length() > 0) {
            LOG.error("[support-look] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[support-look] COMPLETE");
    }

    /** Stand at a point in the vehicle's frame (model px: x left, y up, z rear; feet there) and look at another. */
    private void view(ClientGameTestContext context, TestServerContext server, double x, double y, double z, double tx, double ty, double tz) {
        Vec3[] eyeAndTarget = server.computeOnServer(s -> {
            SupportVehicleEntity truck = truck(s);
            Vec3 stand = SupportRig.world(truck, x, y, z);
            player(s).teleportTo(s.overworld(), stand.x, stand.y, stand.z, Set.of(), 0, 0, true);
            return new Vec3[]{stand.add(0, 1.62, 0), SupportRig.world(truck, tx, ty, tz)};
        });
        Vec3 look = eyeAndTarget[1].subtract(eyeAndTarget[0]);
        float yaw = (float) (net.minecraft.util.Mth.atan2(look.z, look.x) * net.minecraft.util.Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-net.minecraft.util.Mth.atan2(look.y, look.horizontalDistance()) * net.minecraft.util.Mth.RAD_TO_DEG);
        context.waitTicks(6);
        context.runOnClient(mc -> { mc.options.hideGui = true; mc.player.setYRot(yaw); mc.player.setXRot(pitch); });
        context.waitTicks(6);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private SupportVehicleEntity truck(MinecraftServer server) {
        return (SupportVehicleEntity) server.overworld().getEntity(UUID.fromString(this.id));
    }

    private static float truck(net.minecraft.client.Minecraft mc) {
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof SupportVehicleEntity t) return t.getYRot();
        return 0;
    }
}
