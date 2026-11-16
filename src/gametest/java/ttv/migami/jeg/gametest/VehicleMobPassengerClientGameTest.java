package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.client.screen.VehicleScreen;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.menu.VehicleMenu;

/**
 * Mobs in vehicles (e.g. placed by Carry On): in the AH-6 a mob never takes the pilot's seat, even one it sat in before,
 * and can't board when only the pilot's seat is free. A mob found in the pilot's seat is moved to a passenger seat. The
 * hold's seat panel puts a mob off with its X (with Slow Falling when airborne) and never ejects a player.
 */
public class VehicleMobPassengerClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final String HELI = "@e[type=jeg:ah6,limit=1,sort=nearest]";

    @Override
    public void runTest(ClientGameTestContext context) {
        StringBuilder report = new StringBuilder("vehicle mob passenger report\n");
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamemode survival @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 ~ 0 0 0");
            server.runCommand("execute as @p at @s run summon jeg:ah6 ~ ~ ~4 {Rotation:[0f,0f]}");
            for (int i = 1; i <= 4; i++) {
                server.runCommand("execute as @p at @s run summon cow ~2 ~ ~4 {NoAI:1b,Tags:[\"cow" + i + "\"],CustomName:\"Cow " + i + "\"}");
            }
            context.waitTicks(5);

            // Cow 1 "remembers" the pilot's seat, as a mob saved there before this fix would.
            server.runOnServer(s -> heli(s).rememberSeatAssignment(tagged(s, "cow1"), 0));
            for (int i = 1; i <= 4; i++) {
                server.runCommand("ride @e[tag=cow" + i + ",limit=1] mount " + HELI);
            }
            // /ride forces the mount past canAddPassenger, so a cow with no passenger seat left is put off by the next check.
            context.waitTicks(15);
            String seats = server.computeOnServer(VehicleMobPassengerClientGameTest::describeSeats);
            report.append("four cows boarding an empty AH-6: ").append(seats).append('\n');
            boolean pilotFree = server.computeOnServer(s -> heli(s).passengerForSeat(0) == null && heli(s).getControllingPassenger() == null);
            boolean cow4Out = server.computeOnServer(s -> tagged(s, "cow4").getVehicle() == null);
            if (!pilotFree) failures.append("a cow took the pilot's seat or controls the AH-6\n");
            if (!cow4Out) failures.append("a fourth cow boarded with only the pilot's seat free\n");

            server.runCommand("ride @p mount " + HELI);
            context.waitFor(mc -> mc.player != null && mc.player.getVehicle() instanceof VehicleEntity);
            context.waitTicks(5);
            boolean playerPilots = server.computeOnServer(s -> heli(s).getControllingPassenger() == player(s) && heli(s).getSeatIndex(player(s)) == 0);
            report.append("player boards: ").append(server.computeOnServer(VehicleMobPassengerClientGameTest::describeSeats)).append('\n');
            if (!playerPilots) failures.append("the player did not take the free pilot's seat\n");
            // Seats 0 and 1 are level in the AH-6: the cow sits by its own attachment point, 0.6 above a player's feet.
            double lift = server.computeOnServer(s -> heli(s).passengerForSeat(1).getY() - player(s).getY());
            report.append(String.format("cow in seat 2 sits %.2f above the pilot's feet%n", lift));
            if (Math.abs(lift - 0.6D) > 0.05D) failures.append(String.format("cow sits %.2f above the pilot's feet, expected 0.60%n", lift));

            // The hold, opened from the seat: one X per cow, none for the player.
            server.runOnServer(s -> player(s).openMenu(heli(s)));
            context.waitFor(mc -> mc.screen instanceof VehicleScreen);
            context.waitTicks(5);
            context.takeScreenshot("vehicle_mob_seats");
            String rows = context.computeOnClient(mc -> {
                StringBuilder out = new StringBuilder();
                for (VehicleScreen.SeatRow row : ((VehicleScreen) mc.screen).seatRows()) {
                    out.append(row.seat()).append(row.ejectable() ? "x " : "- ");
                }
                return out.toString().trim();
            });
            report.append("seat panel (x = eject button): ").append(rows).append('\n');
            if (!"0- 1x 2x 3x".equals(rows)) failures.append("seat panel should show X on seats 1-3 only, got ").append(rows).append('\n');

            // Click seat 1's X with the mouse.
            double[] at = context.computeOnClient(mc -> {
                for (VehicleScreen.SeatRow row : ((VehicleScreen) mc.screen).seatRows()) {
                    if (row.seat() == 1) {
                        double scale = mc.getWindow().getGuiScale();
                        return new double[]{(row.ejectX() + 4.5) * scale, (row.ejectY() + 4.5) * scale};
                    }
                }
                return null;
            });
            context.getInput().setCursorPos(at[0], at[1]);
            context.waitTick();
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(5);
            context.takeScreenshot("vehicle_mob_seat_ejected");
            String ejected = server.computeOnServer(s -> {
                Entity seat1 = heli(s).passengerForSeat(1);
                return seat1 == null ? "empty" : seat1.getName().getString();
            });
            report.append("after clicking seat 1's X: seat 1 ").append(ejected).append('\n');
            if (!"empty".equals(ejected)) failures.append("seat 1's X did not put the cow off (seat holds ").append(ejected).append(")\n");
            // The client must agree: the other cows stay in their seats on screen, so their X buttons still work.
            String rowsAfter = context.computeOnClient(mc -> {
                StringBuilder out = new StringBuilder();
                for (VehicleScreen.SeatRow row : ((VehicleScreen) mc.screen).seatRows()) {
                    out.append(row.seat()).append(row.ejectable() ? "x " : "- ");
                }
                return out.toString().trim();
            });
            report.append("seat panel after the eject: ").append(rowsAfter).append('\n');
            if (!"0- 1- 2x 3x".equals(rowsAfter)) failures.append("after the eject the screen shows ").append(rowsAfter).append(", expected 0- 1- 2x 3x\n");
            // And clicking seat 3's X (Cow 2, still in seat 3) puts that cow off.
            double[] at3 = context.computeOnClient(mc -> {
                for (VehicleScreen.SeatRow row : ((VehicleScreen) mc.screen).seatRows()) {
                    if (row.seat() == 2) {
                        double scale = mc.getWindow().getGuiScale();
                        return new double[]{(row.ejectX() + 4.5) * scale, (row.ejectY() + 4.5) * scale};
                    }
                }
                return null;
            });
            context.getInput().setCursorPos(at3[0], at3[1]);
            context.waitTick();
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            context.waitTicks(5);
            boolean cow2Off = server.computeOnServer(s -> tagged(s, "cow2").getVehicle() == null);
            report.append("clicking seat 3's X next puts Cow 2 off: ").append(cow2Off).append('\n');
            if (!cow2Off) failures.append("the second eject (seat 3) did nothing\n");
            context.takeScreenshot("vehicle_mob_seat_second_eject");
            server.runCommand("ride @e[tag=cow2,limit=1] mount " + HELI);
            context.waitTicks(15);

            // The pilot's seat button does nothing to a player, even sent directly.
            boolean playerKept = server.computeOnServer(s -> {
                ServerPlayer player = player(s);
                boolean clicked = player.containerMenu instanceof VehicleMenu menu && menu.clickMenuButton(player, 0);
                return !clicked && player.getVehicle() == heli(s);
            });
            report.append("eject button on the player's seat leaves the player aboard: ").append(playerKept).append('\n');
            if (!playerKept) failures.append("the eject button put a player off the vehicle\n");
            context.runOnClient(mc -> { if (mc.screen != null) mc.screen.onClose(); });
            context.waitTicks(3);

            // Put off while airborne: the cow gets Slow Falling.
            boolean slowFalling = server.computeOnServer(s -> {
                VehicleEntity heli = heli(s);
                Entity cow = tagged(s, "cow2");
                heli.setOnGround(false);
                boolean ejectedCow = heli.ejectMobPassenger(cow);
                return ejectedCow && cow instanceof LivingEntity living && living.hasEffect(MobEffects.SLOW_FALLING);
            });
            report.append("cow put off an airborne AH-6 gets Slow Falling: ").append(slowFalling).append('\n');
            if (!slowFalling) failures.append("a cow put off in the air did not get Slow Falling\n");

            // A mob found in the pilot's seat (an old save) is moved back to a passenger seat.
            server.runCommand("ride @p dismount");
            context.waitTicks(5);
            server.runOnServer(s -> heli(s).rememberSeatAssignment(tagged(s, "cow3"), 0));
            context.waitTicks(25);
            int cow3Seat = server.computeOnServer(s -> {
                Entity cow = tagged(s, "cow3");
                return cow.getVehicle() == heli(s) ? heli(s).getSeatIndex(cow) : -1;
            });
            report.append("cow moved into the pilot's seat ends up in seat ").append(cow3Seat).append('\n');
            if (cow3Seat <= 0) failures.append("a cow left in the pilot's seat was not moved to a passenger seat (seat ").append(cow3Seat).append(")\n");
        }
        LOGGER.info(report.toString());
        if (failures.length() > 0) {
            throw new AssertionError(failures.toString() + report);
        }
    }

    private static String describeSeats(MinecraftServer server) {
        VehicleEntity heli = heli(server);
        StringBuilder out = new StringBuilder();
        for (int seat = 0; seat < 4; seat++) {
            Entity passenger = heli.passengerForSeat(seat);
            out.append(seat).append('=').append(passenger == null ? "empty" : passenger.getName().getString()).append(seat < 3 ? ", " : "");
        }
        return out.toString();
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static VehicleEntity heli(MinecraftServer server) {
        for (Entity entity : server.overworld().getAllEntities()) {
            if (entity instanceof VehicleEntity vehicle) {
                return vehicle;
            }
        }
        throw new IllegalStateException("no AH-6");
    }

    private static Entity tagged(MinecraftServer server, String tag) {
        for (Entity entity : server.overworld().getAllEntities()) {
            if (entity.entityTags().contains(tag)) {
                return entity;
            }
        }
        throw new IllegalStateException("no entity tagged " + tag);
    }
}
