package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.resources.sounds.SoundInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.vehicle.client.audio.HelicopterEngineAudio;
import ttv.migami.jeg.vehicle.entity.ChinookEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The CH-47's own sounds (2026-11-21): its 20 s cold start, the flight loop it crossfades into, the blades' slap swelling
 * while it comes down to land, the engines running on at ground idle after touchdown (2026-11-25), and the turbines
 * spooling down when the pilot holds descend on the ground to shut it down. Each must play on an active channel (a
 * missing or unreadable file never gets one).
 */
public class Ch47SoundClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @p 0.5 -60 0.5 0 0");
            server.runCommand("summon jeg:ch47 0.5 -60 12.5");
            context.waitTicks(20);
            server.runCommand("ride @p mount @e[type=jeg:ch47,limit=1]");
            context.waitFor(mc -> mc.player.getVehicle() instanceof ChinookEntity);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(20);
            StringBuilder failures = new StringBuilder();

            // ---- Cold start: the CH-47's own startup, 20 s, then its flight loop.
            int duration = context.computeOnClient(mc -> ((VehicleEntity) mc.player.getVehicle()).helicopterStartupTicks());
            if (duration != 400) {
                failures.append("the CH-47's start should last 400 ticks, it lasts ").append(duration).append('\n');
            }
            context.getInput().holdKey(options -> options.keyJump);
            context.waitTicks(40);
            String startup = this.playing(context, "engine");
            if (!startup.startsWith("jeg:vehicle.ch47.startup active")) {
                failures.append("the cold start should play vehicle.ch47.startup: ").append(startup).append('\n');
            }
            context.waitTicks(duration + 20);
            String flight = this.playing(context, "engine");
            if (!flight.startsWith("jeg:vehicle.ch47.engine active")) {
                failures.append("after the start the flight loop vehicle.ch47.engine should play: ").append(flight).append('\n');
            }
            // Climb clear, then come down with way on, as onto a landing zone.
            context.waitTicks(100);
            context.getInput().releaseKey(options -> options.keyJump);
            double height = context.computeOnClient(mc -> mc.player.getVehicle().getY()) + 60.0D;
            context.getInput().holdKey(options -> options.keyUp);
            context.getInput().holdKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
            float loudest = 0.0F;
            String slap = "none";
            for (int i = 0; i < 30; i++) {
                context.waitTicks(4);
                String now = this.playing(context, "slap");
                float volume = context.computeOnClient(mc -> {
                    SoundInstance sound = HelicopterEngineAudio.slap(mc.player.getVehicle().getId());
                    return sound == null || !mc.getSoundManager().isActive(sound) ? 0.0F : sound.getVolume();
                });
                if (volume > loudest) {
                    loudest = volume;
                    slap = now;
                }
            }
            context.takeScreenshot("ch47_sound_descent");
            context.getInput().releaseKey(options -> options.keyUp);
            if (!slap.startsWith("jeg:vehicle.ch47.slap active") || loudest < 0.08F) {
                failures.append("coming down, the blades' slap should swell (loudest ").append(loudest).append("): ").append(slap).append('\n');
            }
            // ---- Touchdown: the engines run on at ground idle (people walk off with the rotors turning)...
            context.waitFor(mc -> mc.player.getVehicle() != null && mc.player.getVehicle().onGround(), 1200);
            context.getInput().releaseKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
            Object shutdownBefore = context.computeOnClient(mc -> HelicopterEngineAudio.lastShutdown());
            context.waitTicks(40);
            String idling = this.playing(context, "engine");
            if (!idling.startsWith("jeg:vehicle.ch47.engine active") || context.computeOnClient(mc -> HelicopterEngineAudio.lastShutdown()) != shutdownBefore) {
                failures.append("after touchdown the engines should run on at ground idle: ").append(idling).append('\n');
            }
            // ...until the pilot holds descend on the ground: then the turbines spool down.
            context.getInput().holdKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
            context.waitTicks(VehicleEntity.GROUND_SHUTDOWN_HOLD_TICKS + 20);
            context.getInput().releaseKey(KeyBindings.VEHICLE_BRAKE_DESCEND);
            context.waitTicks(10);
            String shutdown = context.computeOnClient(mc -> describe(mc, HelicopterEngineAudio.lastShutdown()));
            if (!shutdown.startsWith("jeg:vehicle.ch47.shutdown active")) {
                failures.append("holding descend on the ground should play the shutdown: ").append(shutdown).append('\n');
            }
            context.waitTicks(60);
            String coasting = this.playing(context, "slap");
            if (!coasting.equals("none")) {
                failures.append("on the ground the slap should die away: ").append(coasting).append('\n');
            }
            LOG.info("[ch47-sound] climbed from {} startup='{}' flight='{}' slap='{}' (loudest {}) shutdown='{}' after landing slap='{}'",
                    height - 60.0D, startup, flight, slap, loudest, shutdown, coasting);

            // ---- A door gun: an M240's machine-gun report shot by shot, not the minigun loop.
            int seat = -1;
            for (int press = 0; press < 4 && seat != ChinookEntity.LEFT_GUNNER_SEAT && seat != ChinookEntity.RIGHT_GUNNER_SEAT; press++) {
                context.getInput().pressKey(KeyBindings.VEHICLE_CHANGE_SEAT);
                context.waitTicks(10);
                seat = context.computeOnClient(mc -> ((VehicleEntity) mc.player.getVehicle()).getSeatIndex(mc.player));
            }
            if (seat != ChinookEntity.LEFT_GUNNER_SEAT && seat != ChinookEntity.RIGHT_GUNNER_SEAT) {
                throw new AssertionError("couldn't reach a door gun (seat " + seat + ")\n" + failures);
            }
            final int gunSeat = seat;
            server.runOnServer(s -> {
                ChinookEntity chinook = (ChinookEntity) s.getPlayerList().getPlayers().get(0).getVehicle();
                chinook.doorGunAmmo(gunSeat).setItem(0, new net.minecraft.world.item.ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("jeg:rifle_ammo")), 64));
            });
            context.getInput().pressKey(KeyBindings.RELOAD);
            context.waitTicks(80);
            int reportsBefore = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.GunReportAudio.played());
            context.getInput().holdKey(options -> options.keyAttack);
            context.waitTicks(30);
            String loop = context.computeOnClient(mc -> {
                VehicleEntity vehicle = (VehicleEntity) mc.player.getVehicle();
                var sound = vehicle.activeVehicleFireSound(mc.player);
                return sound == null ? "none" : sound.location().toString();
            });
            context.getInput().releaseKey(options -> options.keyAttack);
            int reports = context.computeOnClient(mc -> ttv.migami.jeg.vehicle.client.audio.GunReportAudio.played()) - reportsBefore;
            LOG.info("[ch47-sound] door gun seat {}: {} reports in 1.5 s, firing loop '{}'", gunSeat, reports, loop);
            if (reports < 5 || !loop.equals("none")) {
                failures.append("the door gun should be heard as a machine gun's reports (").append(reports)
                        .append(" reports), with no firing loop (").append(loop).append(")\n");
            }
            if (!failures.isEmpty()) {
                throw new AssertionError("CH-47 sounds:\n" + failures);
            }
        }
    }

    private String playing(ClientGameTestContext context, String layer) {
        return context.computeOnClient(mc -> {
            int id = mc.player.getVehicle().getId();
            return describe(mc, layer.equals("slap") ? HelicopterEngineAudio.slap(id) : HelicopterEngineAudio.engine(id));
        });
    }

    private static String describe(net.minecraft.client.Minecraft mc, SoundInstance sound) {
        if (sound == null) {
            return "none";
        }
        return sound.getIdentifier() + (mc.getSoundManager().isActive(sound) ? " active" : " inactive")
                + String.format(java.util.Locale.ROOT, " vol=%.3f pitch=%.2f", sound.getVolume(), sound.getPitch());
    }
}
