package ttv.migami.jeg.gametest;

import java.lang.reflect.Field;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.minecraft.client.CameraType;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.FabricClientBootstrap;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.client.MinigunClient;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.client.audio.HelicopterEngineAudio;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/** Uses a dedicated server and real packets, including idle hands, turning audio, touchdown and the MI-28 gun. */
public final class MultiplayerGunAudioClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestDedicatedServerContext server = context.worldBuilder().createServer();
             TestServerConnection connection = server.connect()) {
            connection.getClientLevel().waitForChunksRender();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 -60 0 0 25");
            server.runCommand("item replace entity @p weapon.mainhand with jeg:minigun");
            context.waitTicks(100);
            context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
            for (int scale : new int[]{1, 2, 3, 4}) {
                context.runOnClient(mc -> {
                    mc.options.guiScale().set(scale);
                    mc.resizeGui();
                });
                context.waitTicks(3);
                context.takeScreenshot("multiplayer_minigun_idle_scale_" + scale);
            }
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(35);
            SoundInstance gunSound = context.computeOnClient(mc -> minigunLoop(mc.player.getId()));
            check(gunSound != null && gunSound.isRelative() && gunSound.getAttenuation() == SoundInstance.Attenuation.NONE
                    && gunSound.getVolume() <= 0.8F, "The local minigun must play centered at a reduced volume");
            this.turnWithSound(context, gunSound);
            context.getInput().releaseKey(options -> options.keyAttack);
            context.waitTicks(50);
            this.remoteGun(context, server);

            this.helicopter(context, server, "ah6", 0);
            this.helicopter(context, server, "mi28", 200);
            LoggerFactory.getLogger("jeg-gametest").info("[multiplayer-audio] Dedicated-server gun, cockpit, landing and rotary gun checks passed");
        } finally {
            context.getInput().releaseKey(options -> options.keyAttack);
            context.getInput().releaseKey(options -> options.keyJump);
            context.getInput().releaseKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
            context.restoreDefaultGameOptions();
        }
    }

    private void helicopter(ClientGameTestContext context, TestDedicatedServerContext server, String id, int x) {
        server.runCommand("ride @p dismount");
        server.runCommand("tp @p " + x + " -60 0 0 0");
        server.runCommand("execute as @p at @s run summon jeg:" + id + " ~ ~ ~5");
        context.waitTicks(5);
        server.runCommand("ride @p mount @e[type=jeg:" + id + ",sort=nearest,limit=1]");
        context.waitFor(mc -> mc.player.getVehicle() instanceof VehicleEntity);
        context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        context.getInput().holdKey(options -> options.keyJump);
        int startup = context.computeOnClient(mc -> vehicle(mc).helicopterStartupTicks());
        context.waitTicks(startup + 35);
        context.getInput().releaseKey(options -> options.keyJump);
        SoundInstance engine = context.computeOnClient(mc -> engineLoop(vehicle(mc).getId()));
        check(engine != null && engine.isRelative() && engine.getAttenuation() == SoundInstance.Attenuation.NONE,
                "The cockpit engine must play centered");
        this.turnWithSound(context, engine);

        if (id.equals("mi28")) {
            server.runOnServer(mc -> {
                var player = mc.getPlayerList().getPlayers().getFirst();
                VehicleEntity heli = (VehicleEntity) player.getVehicle();
                heli.vehicleInventory().setItem(0, new ItemStack(BuiltInRegistries.ITEM.getValue(Reference.id("rifle_ammo")), 64));
                heli.changeSeat(player);
            });
            context.waitTicks(10);
            // The empty gun must still auto-reload before its spin-up gate can permit a shot.
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(15);
            check(context.computeOnClient(mc -> vehicle(mc).selectedVehicleWeaponReloading(mc.player)
                    && !vehicle(mc).isWeaponFiring()), "An empty MI-28 gun must auto-reload on the trigger");
            context.waitTicks(130);
            context.getInput().releaseKey(options -> options.keyAttack);
            context.waitTicks(VehicleEntity.ROTARY_GUN_STOP_TICKS + 8);
            int ammo = server.computeOnServer(mc -> ((VehicleEntity) mc.getPlayerList().getPlayers().getFirst().getVehicle()).selectedVehicleWeaponAmmo(mc.getPlayerList().getPlayers().getFirst()));
            check(ammo > 0, "The gun must be loaded before testing spin-up");
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(VehicleEntity.ROTARY_GUN_SPOOL_TICKS / 2);
            check(context.computeOnClient(mc -> vehicle(mc).rotaryGunSpin() > 0.1F && vehicle(mc).rotaryGunSpin() < 0.9F
                    && !vehicle(mc).isWeaponFiring()), "The MI-28 gun must spin up before firing");
            int during = server.computeOnServer(mc -> ((VehicleEntity) mc.getPlayerList().getPlayers().getFirst().getVehicle()).selectedVehicleWeaponAmmo(mc.getPlayerList().getPlayers().getFirst()));
            check(during == ammo, "Spin-up must not consume ammunition");
            context.takeScreenshot("multiplayer_mi28_gun_spooling");
            context.waitTicks(VehicleEntity.ROTARY_GUN_SPOOL_TICKS);
            check(context.computeOnClient(mc -> vehicle(mc).isWeaponFiring()
                    && FabricClientBootstrap.isVehicleFireAudioActive(vehicle(mc).getId())), "The MI-28 must fire with active audio after spin-up");
            context.getInput().releaseKey(options -> options.keyAttack);
            context.waitTicks(VehicleEntity.ROTARY_GUN_STOP_TICKS + 8);
            check(context.computeOnClient(mc -> vehicle(mc).rotaryGunSpin() == 0.0F
                    && !FabricClientBootstrap.isVehicleFireAudioActive(vehicle(mc).getId())), "The MI-28 gun must stop after release");
            server.runOnServer(mc -> ((VehicleEntity) mc.getPlayerList().getPlayers().getFirst().getVehicle()).changeSeat(mc.getPlayerList().getPlayers().getFirst()));
            context.waitTicks(10);
        }

        context.getInput().holdKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
        context.waitFor(mc -> vehicle(mc).onGround(), 600);
        context.getInput().releaseKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
        context.waitTicks(3);
        float initial = context.computeOnClient(mc -> vehicle(mc).propellerSpeed());
        check(initial > 0.025F, "Touchdown must leave the rotor spinning");
        context.waitTicks(60);
        float later = context.computeOnClient(mc -> vehicle(mc).propellerSpeed());
        check(later > 0.005F && later < initial, "The landed rotor must slow gradually");
        check(context.computeOnClient(mc -> HelicopterEngineAudio.phase(vehicle(mc).getId()).equals("COASTING")),
                "The engine loop must continue throughout rotor coast-down");
        check(engine.getVolume() < 0.65F && engine.getPitch() < 1.0F, "Coast-down must lower engine volume and pitch");
        context.takeScreenshot("multiplayer_" + id + "_rotor_coasting");
        context.waitTicks(context.computeOnClient(mc -> vehicle(mc).helicopterShutdownTicks()));
        check(context.computeOnClient(mc -> vehicle(mc).propellerSpeed() == 0.0F
                && HelicopterEngineAudio.phase(vehicle(mc).getId()).equals("SILENT")), "The landed rotor and audio must eventually stop");
    }

    private void turnWithSound(ClientGameTestContext context, SoundInstance sound) {
        for (int yaw : new int[]{90, 180, 270, 0}) {
            context.runOnClient(mc -> mc.player.setYRot(yaw));
            context.waitTicks(3);
            check(context.computeOnClient(mc -> mc.getSoundManager().isActive(sound)), "Turning must not interrupt the sound channel");
            check(sound.getX() == 0.0D && sound.getY() == 0.0D && sound.getZ() == 0.0D, "The cabin/local gun source must remain centered");
        }
    }

    private void remoteGun(ClientGameTestContext context, TestDedicatedServerContext server) {
        server.runCommand("execute as @p at @s run summon minecraft:armor_stand ~3 ~ ~3 {NoGravity:1b,Tags:[\"audio_test\"]}");
        context.waitFor(mc -> !mc.level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,
                mc.player.getBoundingBox().inflate(10.0D)).isEmpty());
        int id = context.computeOnClient(mc -> mc.level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,
                mc.player.getBoundingBox().inflate(10.0D)).getFirst().getId());
        context.runOnClient(mc -> MinigunClient.onRemoteState(id, ttv.migami.jeg.gun.MinigunSpin.STATE_FIRING));
        context.waitTicks(5);
        SoundInstance remote = context.computeOnClient(mc -> minigunLoop(id));
        check(remote != null && !remote.isRelative() && remote.getAttenuation() == SoundInstance.Attenuation.LINEAR,
                "Other entities' miniguns must remain positional");
        check(context.computeOnClient(mc -> mc.getSoundManager().isActive(remote)), "Nearby miniguns must remain audible");
        context.runOnClient(mc -> MinigunClient.onRemoteState(id, ttv.migami.jeg.gun.MinigunSpin.STATE_IDLE));
        context.waitTicks(50);
        server.runCommand("kill @e[tag=audio_test]");
    }

    private static VehicleEntity vehicle(net.minecraft.client.Minecraft mc) {
        return (VehicleEntity) mc.player.getVehicle();
    }

    private static SoundInstance minigunLoop(int id) {
        try {
            Field field = MinigunClient.class.getDeclaredField("BARRELS");
            field.setAccessible(true);
            Object barrels = ((Map<?, ?>) field.get(null)).get(id);
            Field loop = barrels.getClass().getDeclaredField("loop");
            loop.setAccessible(true);
            return (SoundInstance) loop.get(barrels);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static SoundInstance engineLoop(int id) {
        try {
            Field field = HelicopterEngineAudio.class.getDeclaredField("SOUNDS");
            field.setAccessible(true);
            return (SoundInstance) ((Map<?, ?>) field.get(null)).get(id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
