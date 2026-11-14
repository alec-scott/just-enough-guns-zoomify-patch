package ttv.migami.jeg.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;

/**
 * The helicopters' rockets and missiles leave from alternate sides, as the A-10's do. An AH-6 and an Mi-28 hover, held in
 * place, with husks at the controls (and in the Mi-28's gunner seat); each rocket and missile weapon is fired in turn,
 * and every new round is put down to the side of the helicopter it appeared on. Fails unless each weapon's rounds come
 * left and right in turn. Screenshots: both guns' tracers at night and by day, and the Mi-28 with one Kh-39 gone.
 */
public class HeliWeaponSidesClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final Vec3 AH6 = new Vec3(-20.5D, -48.0D, 0.5D);
    private static final Vec3 MI28 = new Vec3(20.5D, -46.0D, 0.5D);
    private static final String[][] AMMO = {
            {"rifle_ammo", "64"}, {"rifle_ammo", "64"}, {"rifle_ammo", "64"}, {"small_rocket", "64"}, {"small_rocket", "64"},
            {"medium_anti_ground_missile", "64"}, {"medium_anti_air_missile", "64"}, {"large_anti_ground_missile", "64"}
    };

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 -52 -14 0 0");
            context.runOnClient(mc -> {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                mc.options.hideGui = true;
            });
            context.waitTicks(10);
            String[] ids = server.computeOnServer(minecraftServer -> new String[]{
                    spawn(minecraftServer, ModEntities.AH6.get(), AH6, 1), spawn(minecraftServer, ModEntities.MI28.get(), MI28, 2)});
            this.hold(context, server, ids, 40, -1, -1);

            StringBuilder failures = new StringBuilder();
            StringBuilder report = new StringBuilder();
            // seat, weapon slot: AH-6 rockets; Mi-28 rockets, pilot's 9M120 and 9M336, gunner's 9M120 and Kh-39.
            Object[][] runs = {
                    {0, 0, 1, "ah6 70mm rockets", 6},
                    {1, 0, 0, "mi28 80mm rockets", 6},
                    {1, 0, 1, "mi28 pilot 9M120", 4},
                    {1, 0, 2, "mi28 9M336", 4},
                    {1, 1, 4, "mi28 gunner 9M120", 4},
                    {1, 1, 5, "mi28 Kh-39", 2}
            };
            for (Object[] run : runs) {
                int which = (Integer) run[0];
                int seat = (Integer) run[1];
                int slot = (Integer) run[2];
                String label = (String) run[3];
                int wanted = (Integer) run[4];
                List<Integer> sides = this.fire(context, server, ids, which, seat, slot, wanted);
                report.append(label).append(": ").append(sides).append(System.lineSeparator());
                boolean alternates = sides.size() >= Math.min(wanted, 2);
                for (int i = 1; i < sides.size(); i++) {
                    alternates &= !sides.get(i).equals(sides.get(i - 1));
                }
                if (!alternates || !sides.contains(1) || !sides.contains(-1)) {
                    failures.append(label).append(" did not alternate sides: ").append(sides).append(System.lineSeparator());
                }
                if (label.equals("mi28 Kh-39")) {
                    // Both fired: no missile left under either pylon. Then reload for the shot of one left.
                    server.runOnServer(minecraftServer -> stock(heli(minecraftServer, ids[1])));
                    this.hold(context, server, ids, 5, -1, -1);
                    this.fire(context, server, ids, 1, 1, 5, 1);
                    this.look(server, MI28, "^-6 ^1 ^9", "^ ^-0.5 ^");
                    this.hold(context, server, ids, 4, -1, -1);
                    context.takeScreenshot("heli_sides_mi28_one_kh39_left");
                }
            }

            // Tracers: the AH-6's guns, then the Mi-28's chin gun, by day and at night.
            for (String time : new String[]{"noon", "midnight"}) {
                server.runCommand("time set " + time);
                this.look(server, AH6, "^7 ^1 ^-6", "^ ^-2 ^14");
                this.select(server, ids, 0, 0, 0);
                this.hold(context, server, ids, 14, 0, 0);
                context.takeScreenshot("heli_sides_ah6_tracers_" + time);
                this.hold(context, server, ids, 2, -1, -1);
                this.look(server, MI28, "^-8 ^1 ^-5", "^ ^-3 ^14");
                this.select(server, ids, 1, 1, 3);
                this.hold(context, server, ids, 14, 1, 1);
                context.takeScreenshot("heli_sides_mi28_tracers_" + time);
                this.hold(context, server, ids, 2, -1, -1);
            }
            LOGGER.warn("heli weapon sides:{}{}", System.lineSeparator(), report);
            if (!failures.isEmpty()) {
                throw new AssertionError(failures.toString());
            }
        } finally {
            context.runOnClient(mc -> mc.options.hideGui = false);
        }
    }

    private static String spawn(MinecraftServer server, EntityType<? extends VehicleEntity> type, Vec3 at, int crew) {
        var level = server.overworld();
        VehicleEntity vehicle = type.create(level, EntitySpawnReason.COMMAND);
        vehicle.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(vehicle);
        for (int i = 0; i < crew; i++) {
            Mob husk = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
            husk.setNoAi(true);
            husk.setSilent(true);
            husk.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
            level.addFreshEntity(husk);
            husk.startRiding(vehicle, true, true);
        }
        stock(vehicle);
        return vehicle.getUUID().toString();
    }

    private static void stock(VehicleEntity vehicle) {
        var hold = vehicle.vehicleInventory();
        hold.clearContent();
        for (int i = 0; i < AMMO.length && i < hold.getContainerSize(); i++) {
            hold.setItem(i, new ItemStack(BuiltInRegistries.ITEM.getValue(Reference.id(AMMO[i][0])), Integer.parseInt(AMMO[i][1])));
        }
        vehicle.addEnergy(vehicle.maxVehicleEnergy());
        vehicle.reloadAiVehicleWeapons();
    }

    private static VehicleEntity heli(MinecraftServer server, String id) {
        return (VehicleEntity) server.overworld().getEntity(UUID.fromString(id));
    }

    private static LivingEntity crew(VehicleEntity vehicle, int seat) {
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof LivingEntity living && vehicle.getSeatIndex(living) == seat) {
                return living;
            }
        }
        return null;
    }

    private void select(TestServerContext server, String[] ids, int which, int seat, int slot) {
        server.runOnServer(minecraftServer -> heli(minecraftServer, ids[which]).selectAiWeaponForSeat(seat, slot));
    }

    /**
     * Fires one weapon until {@code wanted} rounds have left or 300 ticks pass; for each new round, the side it appeared on
     * (1 the helicopter's left, -1 its right).
     */
    private List<Integer> fire(ClientGameTestContext context, TestServerContext server, String[] ids, int which, int seat,
                               int slot, int wanted) {
        this.select(server, ids, which, seat, slot);
        this.hold(context, server, ids, 3, -1, -1);
        List<Integer> sides = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        server.runOnServer(minecraftServer -> {
            VehicleEntity vehicle = heli(minecraftServer, ids[which]);
            for (Entity entity : minecraftServer.overworld().getEntities((Entity) null, vehicle.getBoundingBox().inflate(40.0D), e -> true)) {
                seen.add(entity.getId());
            }
        });
        for (int tick = 0; tick < 300 && sides.size() < wanted; tick++) {
            this.hold(context, server, ids, 1, which, seat);
            List<Integer> found = server.computeOnServer(minecraftServer -> {
                VehicleEntity vehicle = heli(minecraftServer, ids[which]);
                List<Integer> out = new ArrayList<>();
                AABB around = vehicle.getBoundingBox().inflate(40.0D);
                for (Entity entity : minecraftServer.overworld().getEntities((Entity) null, around, e -> true)) {
                    if (seen.add(entity.getId()) && !(entity instanceof LivingEntity) && !(entity instanceof VehicleEntity)) {
                        // Facing south (+Z), the helicopter's left is +X.
                        double side = entity.getX() - vehicle.getX();
                        if (Math.abs(side) > 0.4D) {
                            out.add(side > 0.0D ? 1 : -1);
                        }
                    }
                }
                return out;
            });
            sides.addAll(found);
        }
        this.hold(context, server, ids, 2, -1, -1);
        return sides;
    }

    private void look(TestServerContext server, Vec3 spot, String from, String at) {
        server.runCommand("execute positioned " + spot.x + " " + spot.y + " " + spot.z + " rotated 0 0 run tp @p " + from
                + " facing " + at);
    }

    /**
     * Holds both helicopters hovering in place, collective up so the rotors stay at speed; the one numbered
     * {@code firing} has its crew in {@code seat} on the trigger.
     */
    private void hold(ClientGameTestContext context, TestServerContext server, String[] ids, int ticks, int firing, int seat) {
        Vec3[] spots = {AH6, MI28};
        for (int tick = 0; tick < ticks; tick++) {
            server.runOnServer(minecraftServer -> {
                for (int i = 0; i < ids.length; i++) {
                    VehicleEntity vehicle = heli(minecraftServer, ids[i]);
                    if (vehicle == null) {
                        continue;
                    }
                    vehicle.setAiVehicleInput(new VehicleInput(false, false, false, false, false, true, false, false,
                            false, false, false, false, -1, false, false, 0.0F, 0.0F));
                    LivingEntity pilot = crew(vehicle, 0);
                    LivingEntity gunner = crew(vehicle, 1);
                    boolean on = i == firing;
                    vehicle.setAiWeaponControl(pilot, on && seat == 0, false);
                    if (gunner != null) {
                        vehicle.setAiWeaponControlForSeat(1, gunner, on && seat == 1, false);
                    }
                    vehicle.setDeltaMovement(Vec3.ZERO);
                    vehicle.setPos(spots[i]);
                    vehicle.setYRot(0.0F);
                    vehicle.setXRot(0.0F);
                }
            });
            context.waitTick();
        }
    }
}
