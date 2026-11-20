package ttv.migami.jeg.gametest;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.entity.TowEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

/**
 * The TOW launcher (design/tow/BRIEF.md) with real keys and clicks: set up from the item with a right-click; R loads a
 * missile from its crate; the left button fires; the missile flies down the gunner's sight line and follows it when
 * the gunner turns; it kills a BMP-2 100 blocks off; the wire breaks when the gunner leaves the sight and the missile
 * flies on straight; sneak-click packs it up with its missiles.
 */
public class TowClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private int towId = -1;

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(mc -> mc.options.renderDistance().set(12));
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            // The real recordings (a BGM-71 launch, design/tow/prepare_audio.py) must be what is heard.
            java.util.List<String> heard = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            net.minecraft.client.sounds.SoundEventListener listener = (sound, events, range) -> {
                String path = sound.getIdentifier().getPath();
                if (path.contains("tow")) heard.add(path);
            };
            context.runOnClient(mc -> mc.getSoundManager().addListener(listener));
            context.waitTicks(100);
            TestServerContext server = world.getServer();
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("effect give @a minecraft:resistance infinite 255 true");
            server.runCommand("tp @a 0.5 -60 0.5 0 55");
            server.runCommand("give @a jeg:tow 1");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.player.getInventory().setSelectedSlot(0);
            });
            context.waitTicks(40);

            // 1. Set it up with a real right-click on the ground.
            context.getInput().pressKey(options -> options.keyUse);
            context.waitTicks(5);
            this.towId = server.computeOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity instanceof TowEntity tow) {
                        tow.vehicleInventory().setItem(0, new ItemStack(ModItems.AMMO.get(Reference.id("medium_anti_ground_missile")).get(), 4));
                        return tow.getId();
                    }
                }
                throw new AssertionError("right-clicking the ground with the TOW did not set one up");
            });
            Vec3 at = server.computeOnServer(s -> tow(s).position());
            LOGGER.info("[tow] set up at {}", at);

            // 2. Man it, load with R.
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().get(0);
                player.getInventory().setSelectedSlot(2);
                check(player.startRiding(tow(s), true, true), "the gunner must be able to man the TOW");
            });
            context.waitTicks(10);
            this.look(context, server, 0.0F, 0.0F);
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(90);
            int loaded = server.computeOnServer(s -> tow(s).vehicleWeaponSlotAmmo(0));
            check(loaded == 1, "R must load a missile from the crate, loaded " + loaded);
            context.takeScreenshot("tow_sight");

            // 3. A BMP-2 100 blocks down the sight line: one missile kills it.
            Vec3 bmpAt = at.add(0.0D, 0.0D, 100.0D);
            server.runCommand(String.format(Locale.ROOT, "summon jeg:bmp2 %.1f %.1f %.1f", bmpAt.x, bmpAt.y, bmpAt.z));
            context.waitTicks(10);
            float bmpBefore = server.computeOnServer(s -> bmp(s).vehicleHealth());
            this.fire(context);
            context.waitTicks(60);
            boolean bmpGone = server.computeOnServer(s -> {
                for (var entity : s.overworld().getAllEntities()) {
                    if (entity instanceof VehicleEntity v && v.vehicleDataId().getPath().equals("bmp2")) {
                        return v.isWrecked() || v.vehicleHealth() <= 0.0F;
                    }
                }
                return true;
            });
            LOGGER.info("[tow] BMP-2 {} -> destroyed {}", bmpBefore, bmpGone);
            check(bmpGone, "a TOW must destroy a BMP-2 it strikes (SBW's 600)");
            server.runCommand("kill @e[type=jeg:bmp2]");

            // 4. Steering: fire straight ahead, then turn 12 degrees right; the missile follows the new line.
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(90);
            this.fire(context);
            context.waitTicks(8);
            this.look(context, server, -12.0F, 0.0F);
            context.waitTicks(40);
            double[] steer = server.computeOnServer(s -> {
                VehicleMissileEntity missile = missile(s);
                check(missile != null, "the steered missile must still be in flight");
                TowEntity tow = tow(s);
                Vec3 origin = tow.sightOrigin(1.0F);
                Vec3 line = tow.sightDirection(1.0F);
                Vec3 off = missile.position().subtract(origin);
                double along = off.dot(line);
                return new double[] {off.subtract(line.scale(along)).length(), along, missile.wireIntact() ? 1 : 0};
            });
            LOGGER.info("[tow] after turning 12 deg: {} blocks off the new sight line, {} along it, wire {}", steer[0], steer[1], steer[2]);
            check(steer[2] == 1.0D, "the wire must hold while the gunner keeps the sight");
            check(steer[0] < 2.5D, "the missile must fly down the new sight line, " + steer[0] + " blocks off it");
            server.runCommand("kill @e[type=jeg:vehicle_missile]");

            // 5. Wire break: leave the sight and the missile flies on straight.
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(90);
            this.look(context, server, 0.0F, 0.0F);
            context.waitTicks(10);
            this.fire(context);
            context.waitTicks(10);
            server.runOnServer(s -> s.getPlayerList().getPlayers().get(0).stopRiding());
            context.waitTicks(4);
            Vec3[] before = server.computeOnServer(s -> {
                VehicleMissileEntity missile = missile(s);
                check(missile != null, "the missile must still fly after the gunner leaves");
                check(!missile.wireIntact(), "the wire must break when the gunner leaves the sight");
                return new Vec3[] {missile.position(), missile.getDeltaMovement()};
            });
            context.waitTicks(10);
            Vec3 after = server.computeOnServer(s -> missile(s) == null ? null : missile(s).position());
            if (after != null) {
                Vec3 heading = after.subtract(before[0]).normalize();
                double straight = heading.dot(before[1].normalize());
                LOGGER.info("[tow] unguided heading kept: {}", straight);
                check(straight > 0.99D, "with the wire broken the missile must fly on straight, " + straight);
            }
            server.runCommand("kill @e[type=jeg:vehicle_missile]");

            // 6. Pack up with a sneak-click: the launcher and the missiles left come back.
            int missilesBefore = server.computeOnServer(s -> count(s, ModItems.AMMO.get(Reference.id("medium_anti_ground_missile")).get()));
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().get(0);
                player.setShiftKeyDown(true);
                player.getInventory().setSelectedSlot(5);
                tow(s).interact(player, net.minecraft.world.InteractionHand.MAIN_HAND, tow(s).position());
                player.setShiftKeyDown(false);
            });
            context.waitTicks(5);
            boolean gone = server.computeOnServer(s -> s.overworld().getEntity(this.towId) == null);
            int launchers = server.computeOnServer(s -> count(s, ModItems.TOW.get()));
            int missilesAfter = server.computeOnServer(s -> count(s, ModItems.AMMO.get(Reference.id("medium_anti_ground_missile")).get()));
            LOGGER.info("[tow] pack-up: removed {} launchers {} missiles {} -> {}", gone, launchers, missilesBefore, missilesAfter);
            check(gone && launchers == 1, "packing up must remove the TOW and return the launcher");
            check(missilesAfter == missilesBefore + 1, "packing up must return the missile left in the crate");
            context.runOnClient(mc -> mc.getSoundManager().removeListener(listener));
            LOGGER.info("[tow] sounds heard: {}", heard.stream().distinct().toList());
            if (!heard.contains("vehicle.tow.launch")) throw new AssertionError("vehicle.tow.launch was not heard: " + java.util.List.copyOf(heard));
            LOGGER.info("[tow] COMPLETE");
        } catch (Throwable failure) {
            LOGGER.error("[tow] FAILED: {}", failure.getMessage());
            throw failure;
        }
    }

    private void look(ClientGameTestContext context, TestServerContext server, float yaw, float pitch) {
        context.runOnClient(mc -> {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
        });
        context.waitTicks(3);
    }

    private void fire(ClientGameTestContext context) {
        context.getInput().holdKey(options -> options.keyAttack);
        context.waitTicks(3);
        context.getInput().releaseKey(options -> options.keyAttack);
    }

    private TowEntity tow(MinecraftServer server) {
        if (server.overworld().getEntity(this.towId) instanceof TowEntity tow) {
            return tow;
        }
        throw new AssertionError("the TOW is gone");
    }

    private static VehicleEntity bmp(MinecraftServer server) {
        for (var entity : server.overworld().getAllEntities()) {
            if (entity instanceof VehicleEntity v && v.vehicleDataId().getPath().equals("bmp2")) {
                return v;
            }
        }
        throw new AssertionError("no BMP-2");
    }

    private static VehicleMissileEntity missile(MinecraftServer server) {
        for (var entity : server.overworld().getAllEntities()) {
            if (entity instanceof VehicleMissileEntity missile && missile.isAlive()) {
                return missile;
            }
        }
        return null;
    }

    private static int count(MinecraftServer server, net.minecraft.world.item.Item item) {
        var inventory = server.getPlayerList().getPlayers().get(0).getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                total += inventory.getItem(slot).getCount();
            }
        }
        return total;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
