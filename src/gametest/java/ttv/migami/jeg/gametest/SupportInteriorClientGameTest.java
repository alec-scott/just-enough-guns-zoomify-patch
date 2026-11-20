package ttv.migami.jeg.gametest;

import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.block.VehicleShellBlock;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.support.SupportModules;
import ttv.migami.jeg.vehicle.support.SupportShell;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * The deployed FSV-6 used from inside with real clicks (the use key, aimed by the player's own view):
 * <ul>
 *     <li>the crafting table, furnace and chest bays open their screens; the front wall's right panel opens the support
 *     console, whose only key opens the hold (modules are not opened from it), and its left panel the beacon terminal;</li>
 *     <li>a bed fitted while deployed appears in its bunk and one taken out disappears (no item dropped); clicking the
 *     bunk's bed at night puts the player to sleep;</li>
 *     <li>the beacon terminal: raw iron clicked from the player's inventory into its power rack sets level 1, and
 *     clicking Speed on its display chooses the effect (Strength, a level 3 effect, cannot be chosen);</li>
 *     <li>the room is dark with no lamp fitted and lit by the one fitted lamp;</li>
 *     <li>the culling box covers the whole unfolded vehicle (it no longer vanishes as the cab leaves the view);</li>
 *     <li>packed: the turning circle is a real truck's, and pressing S while rolling forward lights the brake lights.</li>
 * </ul>
 * Screenshots: the room by its lamp at night, the beacon terminal, the windows from outside and inside, and the brake lights.
 */
public class SupportInteriorClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private String id;

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
                fit.setItem(0, new ItemStack(Items.CRAFTING_TABLE));
                fit.setItem(1, new ItemStack(Items.FURNACE));
                fit.setItem(2, new ItemStack(Items.CHEST));
                fit.setItem(SupportModules.BUNK_LEFT, new ItemStack(Items.RED_BED));
                fit.setItem(SupportModules.ROOF, new ItemStack(Items.BEACON));
                fit.setChanged();
            });
            context.waitTicks(5);

            // ---- Packed: the turning circle, and the brake lights.
            server.runOnServer(s -> player(s).startRiding(truck(s), true, true));
            context.waitTicks(SupportVehicleEntity.START_TICKS + 5);
            context.getInput().holdKey(options -> options.keyUp);
            // A turning circle is a low-speed figure: steer from a crawl. Since the FSV-6 pulls away 2.8 times harder
            // (2026-11-18), 20 ticks of throttle had it at 52 km/h before the turn, where no truck turns at full lock.
            context.waitTicks(8);
            context.getInput().holdKey(options -> options.keyLeft);
            context.waitTicks(10);
            double[] a = server.computeOnServer(s -> new double[]{truck(s).getX(), truck(s).getZ(), truck(s).getYRot()});
            context.waitTicks(40);
            double[] b = server.computeOnServer(s -> new double[]{truck(s).getX(), truck(s).getZ(), truck(s).getYRot()});
            context.getInput().releaseKey(options -> options.keyLeft);
            double travelled = Math.hypot(b[0] - a[0], b[1] - a[1]);
            double turned = Math.abs(Mth.wrapDegrees(b[2] - a[2])) * Mth.DEG_TO_RAD;
            double radius = turned > 1.0E-3 ? travelled / turned : 1.0E9;
            LOG.info("[support-interior] turning: {} blocks in 2 s, turned {} degrees, radius {} blocks",
                    String.format("%.1f", travelled), String.format("%.1f", turned * Mth.RAD_TO_DEG), String.format("%.1f", radius));
            if (radius > 15) failures.append(String.format("turning circle too wide: radius %.1f blocks%n", radius));
            context.getInput().releaseKey(options -> options.keyUp);
            context.getInput().holdKey(options -> options.keyDown);
            context.waitTicks(3);
            boolean braking = server.computeOnServer(s -> truck(s).braking());
            server.runCommand("time set midnight");
            context.runOnClient(mc -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setYRot(truck(mc) + 180); mc.player.setXRot(15); });
            context.waitTicks(4);
            context.takeScreenshot("support_brake_lights");
            context.getInput().releaseKey(options -> options.keyDown);
            context.getInput().releaseKey(options -> options.keyUp);
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            LOG.info("[support-interior] braking while rolling and pressing S: {}", braking);
            if (!braking) failures.append("pressing S while rolling forward did not light the brake lights\n");
            context.waitTicks(40);
            server.runCommand("time set noon");

            // ---- Park it squarely (stopped, heading south) and deploy.
            server.runOnServer(s -> player(s).stopRiding());
            context.waitTicks(5);
            server.runOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                truck.setDeltaMovement(Vec3.ZERO);
                truck.snapTo(truck.getX(), G, truck.getZ(), 0, 0);
                // The player got off beside the truck at its old heading; squared up, that spot can be where a slide-out
                // goes (a deploy refused IN_THE_WAY, rightly). Stand clear behind it, as a player would.
                player(s).teleportTo(truck.getX(), G, truck.getZ() - 9.0D);
            });
            context.waitTicks(10);
            String deploy = server.computeOnServer(s -> { truck(s).setDeltaMovement(Vec3.ZERO); return truck(s).requestDeploy(player(s)).name(); });
            context.waitTicks(SupportRig.DEPLOY_TICKS + 20);
            LOG.info("[support-interior] deploy {}: deployed {}", deploy, server.computeOnServer(s -> truck(s).isDeployed()));
            if (!server.computeOnServer(s -> truck(s).isDeployed())) failures.append("did not deploy: ").append(deploy).append('\n');

            // The culling box covers the room, the awning and the mast top, not just the cab.
            String bounds = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                Vec3 room = SupportRig.world(truck, 0, 32, 40), mast = SupportRig.world(truck, 14, 100, -20), awning = SupportRig.world(truck, 60, 40, 16);
                var box = truck.partBounds();
                return "room " + box.contains(room) + " mast " + box.contains(mast) + " awning " + box.contains(awning);
            });
            LOG.info("[support-interior] culling box covers {}", bounds);
            if (bounds.contains("false")) failures.append("culling box too small: ").append(bounds).append('\n');

            // ---- Real clicks inside: stand in the aisle and use each bay, then the support panel.
            Object[][] clicks = {
                    {"crafting bay", 3, 2, "Crafting"}, {"furnace bay", 3, 3, "Furnace"}, {"chest bay", 3, 4, "Chest"}};
            for (Object[] click : clicks) {
                String name = (String) click[0];
                int column = (Integer) click[1], row = (Integer) click[2];
                String opened = this.clickCell(context, server, column, row, 0, .5, (String) click[3]);
                LOG.info("[support-interior] click {} -> {}", name, opened);
                if (!opened.contains((String) click[3])) failures.append("clicking the ").append(name).append(" opened ").append(opened).append('\n');
            }
            // The room before a lamp is fitted: dark (the lamp is its only light).
            int unlit = server.computeOnServer(s -> roomLight(truck(s)));
            LOG.info("[support-interior] room light with no lamp: {}", unlit);
            if (unlit != 0) failures.append("room lit to ").append(unlit).append(" with no lamp fitted\n");
            server.runOnServer(s -> {
                var fit = truck(s).modules().fittings;
                fit.setItem(SupportModules.LAMP, new ItemStack(Items.LANTERN));
                fit.setChanged();
            });
            context.waitTicks(5);
            int lampLit = server.computeOnServer(s -> roomLight(truck(s)));
            LOG.info("[support-interior] room light with a lantern: {}", lampLit);
            // Light falls off from under the lamp (SupportShell.lampCell): measured, not just the cells' setting.
            int[] measured = server.computeOnServer(s -> new int[] {
                    blockLight(truck(s), new SupportRig.ShellCell(1, 2, 1, SupportRig.Cell.ROOM, 0)),
                    blockLight(truck(s), new SupportRig.ShellCell(1, 0, 1, SupportRig.Cell.ROOM, 0)),
                    blockLight(truck(s), new SupportRig.ShellCell(2, 4, 1, SupportRig.Cell.ROOM, 0))});
            LOG.info("[support-interior] measured light: under the lamp {}, front of aisle {}, back of aisle {}", measured[0], measured[1], measured[2]);
            // A lantern (15) is capped to a soft level under the lamp (SupportShell.ROOM_LAMP_MAX; 14 was blown out under Photon).
            if (lampLit != SupportShell.ROOM_LAMP_MAX) failures.append("the cell under the lamp gives ").append(lampLit)
                    .append(" for a lantern, not ").append(SupportShell.ROOM_LAMP_MAX).append('\n');
            if (measured[1] >= measured[0] || measured[2] >= measured[0] || measured[1] < 8 || measured[2] < 8) {
                failures.append("the room's light must fall off from the lamp but stay lit: ").append(java.util.Arrays.toString(measured)).append('\n');
            }

            String panel = this.clickPanel(context, server, CONSOLE_X, true);
            LOG.info("[support-interior] click support panel -> {}", panel);
            if (!panel.contains("SupportFit")) failures.append("clicking the support panel opened ").append(panel).append('\n');

            // ---- Fit-out while deployed: a blue bed into the right bunk appears; the red bed out of the left one goes.
            String beds = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                var fit = truck.modules().fittings;
                fit.setItem(SupportModules.BUNK_RIGHT, new ItemStack(Items.BLUE_BED));
                fit.setItem(SupportModules.BUNK_LEFT, ItemStack.EMPTY);
                fit.setChanged();
                return "changed";
            });
            context.waitTicks(25);
            String bunks = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                BlockPos leftHead = cell(truck, 3, 0), rightHead = cell(truck, 0, 0);
                long dropped = s.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, truck.getBoundingBox().inflate(10)).size();
                return "left " + s.overworld().getBlockState(leftHead).getBlock().getName().getString()
                        + ", right " + s.overworld().getBlockState(rightHead).getBlock().getName().getString() + ", items dropped " + dropped;
            });
            LOG.info("[support-interior] bunks after refitting while deployed ({}): {}", beds, bunks);
            if (!bunks.contains("right Blue Bed") || bunks.contains("left Red Bed") || !bunks.endsWith("dropped 0")) {
                failures.append("bunks did not follow the fittings while deployed: ").append(bunks).append('\n');
            }
            // Sleeping in the new bunk at night, by clicking it.
            server.runCommand("time set midnight");
            String slept = this.clickCell(context, server, 0, 0, 0, .4, "sleep");
            // (Closing the bed screen afterwards is "leave bed", so read the state captured right after the click.)
            boolean sleeping = slept.contains("sleeping true");
            LOG.info("[support-interior] clicked the blue bunk at night: {}, sleeping {}", slept, sleeping);
            if (!sleeping) failures.append("clicking the bunk's bed at night did not put the player to sleep\n");
            server.runOnServer(s -> player(s).stopSleeping());
            context.waitTicks(10);

            // ---- The room by night, lit by its lantern.
            server.runOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                Vec3 door = SupportRig.world(truck, 0, SupportRig.FLOOR_Y, 54);
                player(s).teleportTo(s.overworld(), door.x, door.y, door.z, Set.of(), truck.getYRot(), 10, true);
            });
            context.runOnClient(mc -> mc.options.hideGui = true);
            context.waitTicks(8);
            context.runOnClient(mc -> { mc.player.setYRot(truck(mc)); mc.player.setXRot(10); });
            context.waitTicks(4);
            context.takeScreenshot("support_room_lamp_night");
            // Looking out of a window from inside, then in through it from outside (by day).
            server.runCommand("time set noon");
            server.runOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                Vec3 in = SupportRig.world(truck, 4, SupportRig.FLOOR_Y, -14);
                player(s).teleportTo(s.overworld(), in.x, in.y, in.z, Set.of(), truck.getYRot() - 90, 0, true);
            });
            context.waitTicks(6);
            context.runOnClient(mc -> { mc.player.setYRot(truck(mc) - 90); mc.player.setXRot(0); });
            context.waitTicks(4);
            context.takeScreenshot("support_window_from_inside");
            server.runOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                Vec3 out = SupportRig.world(truck, 110, 14, 12);
                player(s).teleportTo(s.overworld(), out.x, out.y, out.z, Set.of(), truck.getYRot() + 90, 5, true);
            });
            context.waitTicks(6);
            context.runOnClient(mc -> { mc.player.setYRot(truck(mc) + 90); mc.player.setXRot(5); });
            context.waitTicks(4);
            context.takeScreenshot("support_window_from_outside");
            context.runOnClient(mc -> mc.options.hideGui = false);

            // ---- The beacon terminal, all by real clicks: open it from its panel, click 3 x 64 raw iron (21 blocks, level
            // 1) from the inventory into the power rack, then choose Speed on its display.
            server.runOnServer(s -> {
                var inventory = player(s).getInventory();
                for (int i = 0; i < 3; i++) inventory.setItem(9 + i, new ItemStack(Items.RAW_IRON, 64));
            });
            String terminal = this.clickPanel(context, server, BEACON_X, false);
            LOG.info("[support-interior] click beacon panel -> {}", terminal);
            if (!terminal.contains("SupportBeacon")) {
                failures.append("clicking the beacon panel opened ").append(terminal).append('\n');
            } else {
                context.waitTicks(4);
                for (int i = 0; i < 3; i++) {
                    // Menu slots: the rack's nine, then the player's main inventory (inventory slot 9 is menu slot 9).
                    this.clickBeaconSlot(context, SupportModules.RACK_SLOTS + i);
                    this.clickBeaconSlot(context, i);
                }
                context.waitTicks(4);
                int level = context.computeOnClient(mc -> mc.screen instanceof ttv.migami.jeg.vehicle.client.screen.SupportBeaconScreen screen ? screen.getMenu().level() : -1);
                // Strength needs level 3: its key does nothing at level 1.
                this.clickEffect(context, net.minecraft.world.effect.MobEffects.STRENGTH);
                String afterStrength = server.computeOnServer(s -> String.valueOf(truck(s).modules().services().primary()));
                this.clickEffect(context, net.minecraft.world.effect.MobEffects.SPEED);
                context.waitTicks(6);
                context.takeScreenshot("support_beacon_terminal");
                String beacon = server.computeOnServer(s -> {
                    var modules = truck(s).modules();
                    var primary = net.minecraft.world.inventory.BeaconMenu.decodeEffect(modules.services().primary());
                    return "level " + modules.beaconLevel() + " blocks " + modules.beaconBlocks() + " primary "
                            + (primary == null ? "none" : primary.getRegisteredName()) + " lit " + truck(s).beaconLit();
                });
                context.runOnClient(mc -> { if (mc.screen != null) mc.screen.onClose(); });
                server.runOnServer(s -> player(s).closeContainer());
                context.waitTicks(3);
                LOG.info("[support-interior] beacon terminal: client level {}, primary after Strength {}, then {}", level, afterStrength, beacon);
                if (level != 1 || !beacon.startsWith("level 1 blocks 21 ") || !beacon.contains("minecraft:speed") || !"0".equals(afterStrength)) {
                    failures.append("beacon terminal: client level ").append(level).append(", after Strength ").append(afterStrength)
                            .append(", then ").append(beacon).append('\n');
                }
            }

            // ---- The console: its Hold key opens the hold; the module icons do not open anything.
            String console = this.clickConsole(context, server, -2);
            LOG.info("[support-interior] console Hold key -> {}", console);
            if (!console.startsWith("VehicleMenu")) failures.append("console Hold key opened ").append(console).append('\n');
            String bay = this.clickConsole(context, server, 0);
            LOG.info("[support-interior] console module icon bay 0 -> {}", bay);
            if (!bay.startsWith("none: no console key")) failures.append("console bay 0 icon is a key: ").append(bay).append('\n');
        }
        if (failures.length() > 0) {
            LOG.error("[support-interior] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[support-interior] COMPLETE");
    }

    /** Stand in the aisle beside a cell, look at it and press the use key; returns what opened (or the sleep state). */
    private String clickCell(ClientGameTestContext context, TestServerContext server, int column, int row, int level, double faceOffset, String expect) {
        Vec3[] eyeAndTarget = server.computeOnServer(s -> {
            SupportVehicleEntity truck = truck(s);
            Vec3 target = Vec3.atCenterOf(cell(truck, column, row));
            Vec3 stand = SupportRig.world(truck, column >= 2 ? 2 : -2, SupportRig.FLOOR_Y, SupportRig.INTERIOR_FRONT + (row + .5) * SupportRig.CELL + (row == 0 ? 8 : 0));
            player(s).teleportTo(s.overworld(), stand.x, stand.y, stand.z, Set.of(), 0, 0, true);
            return new Vec3[]{stand.add(0, 1.62, 0), target.add(0, faceOffset - .5, 0)};
        });
        Vec3 look = eyeAndTarget[1].subtract(eyeAndTarget[0]);
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG);
        context.waitTicks(4);
        context.runOnClient(mc -> { mc.player.setYRot(yaw); mc.player.setXRot(pitch); });
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(6);
        String opened = server.computeOnServer(s -> player(s).containerMenu.getClass().getSimpleName() + " "
                + player(s).containerMenu.getClass().getSuperclass().getSimpleName() + " sleeping " + player(s).isSleeping());
        context.runOnClient(mc -> { if (mc.screen != null) mc.screen.onClose(); });
        server.runOnServer(s -> player(s).closeContainer());
        context.waitTicks(3);
        return opened;
    }

    /** Model x of the front wall's two panels: the support console (right of the aisle) and the beacon terminal (left). */
    private static final double CONSOLE_X = -8, BEACON_X = 8;

    private String clickPanel(ClientGameTestContext context, TestServerContext server, double panelX, boolean close) {
        Vec3[] eyeAndTarget = server.computeOnServer(s -> {
            SupportVehicleEntity truck = truck(s);
            Vec3 stand = SupportRig.world(truck, 0, SupportRig.FLOOR_Y, SupportRig.INTERIOR_FRONT + 40);
            Vec3 target = SupportRig.world(truck, panelX, SupportRig.FLOOR_Y + 22, SupportRig.INTERIOR_FRONT + .5);
            player(s).teleportTo(s.overworld(), stand.x, stand.y, stand.z, Set.of(), 0, 0, true);
            return new Vec3[]{stand.add(0, 1.62, 0), target};
        });
        Vec3 look = eyeAndTarget[1].subtract(eyeAndTarget[0]);
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) (-Mth.atan2(look.y, look.horizontalDistance()) * Mth.RAD_TO_DEG);
        context.waitTicks(4);
        context.runOnClient(mc -> { mc.player.setYRot(yaw); mc.player.setXRot(pitch); });
        context.waitTicks(3);
        context.getInput().pressKey(options -> options.keyUse);
        context.waitTicks(8);
        if (close) context.takeScreenshot("support_panel_fitout");
        String opened = server.computeOnServer(s -> player(s).containerMenu.getClass().getSimpleName());
        if (!close) return opened;
        context.runOnClient(mc -> { if (mc.screen != null) mc.screen.onClose(); });
        server.runOnServer(s -> player(s).closeContainer());
        context.waitTicks(3);
        return opened;
    }

    /** Open the console from its panel, screenshot it, and click the key (or module icon) for this slot with the mouse. */
    private String clickConsole(ClientGameTestContext context, TestServerContext server, int slot) {
        String panel = this.clickPanel(context, server, CONSOLE_X, false);
        if (!panel.contains("SupportFit")) return "none: panel opened " + panel;
        context.waitTicks(4);
        if (slot == -2) context.takeScreenshot("support_console");
        double[] at = context.computeOnClient(mc -> {
            if (!(mc.screen instanceof ttv.migami.jeg.vehicle.client.screen.SupportFitScreen screen)) return null;
            double[] centre = screen.keyCentre(slot);
            if (centre == null) return null;
            double scale = mc.getWindow().getGuiScale();
            return new double[]{centre[0] * scale, centre[1] * scale};
        });
        if (at == null) {
            context.runOnClient(mc -> { if (mc.screen != null) mc.screen.onClose(); });
            return "none: no console key for slot " + slot;
        }
        context.getInput().setCursorPos(at[0], at[1]);
        context.waitTick();
        context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(6);
        String opened = server.computeOnServer(s -> player(s).containerMenu.getClass().getSimpleName() + " "
                + player(s).containerMenu.getClass().getSuperclass().getSimpleName());
        context.runOnClient(mc -> { if (mc.screen != null) mc.screen.onClose(); });
        server.runOnServer(s -> player(s).closeContainer());
        context.waitTicks(3);
        return opened;
    }

    /** Left-click a slot of the open beacon terminal with the mouse. */
    private void clickBeaconSlot(ClientGameTestContext context, int menuSlot) {
        double[] at = context.computeOnClient(mc -> {
            if (!(mc.screen instanceof ttv.migami.jeg.vehicle.client.screen.SupportBeaconScreen screen)) return null;
            double[] centre = screen.slotCentre(menuSlot);
            double scale = mc.getWindow().getGuiScale();
            return new double[]{centre[0] * scale, centre[1] * scale};
        });
        if (at == null) return;
        context.getInput().setCursorPos(at[0], at[1]);
        context.waitTick();
        context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(3);
    }

    /** Left-click an effect's primary key on the open beacon terminal's display. */
    private void clickEffect(ClientGameTestContext context, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect) {
        double[] at = context.computeOnClient(mc -> {
            if (!(mc.screen instanceof ttv.migami.jeg.vehicle.client.screen.SupportBeaconScreen screen)) return null;
            double[] centre = screen.keyCentre(effect, false);
            if (centre == null) return null;
            double scale = mc.getWindow().getGuiScale();
            return new double[]{centre[0] * scale, centre[1] * scale};
        });
        if (at == null) return;
        context.getInput().setCursorPos(at[0], at[1]);
        context.waitTick();
        context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitTicks(4);
    }

    /** Measured block light in a room cell. */
    private static int blockLight(SupportVehicleEntity truck, SupportRig.ShellCell cell) {
        BlockPos pos = SupportShell.cellPos(truck.position(), truck.getYRot(), cell);
        return truck.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
    }

    /** Block light of a room cell (the aisle at the middle row, one block up). */
    private static int roomLight(SupportVehicleEntity truck) {
        BlockPos pos = SupportShell.cellPos(truck.position(), truck.getYRot(), new SupportRig.ShellCell(1, 2, 1, SupportRig.Cell.ROOM, 0));
        var state = truck.level().getBlockState(pos);
        return state.getBlock() instanceof VehicleShellBlock ? state.getValue(VehicleShellBlock.LIGHT) : -1;
    }

    private static BlockPos cell(SupportVehicleEntity truck, int column, int row) {
        return SupportShell.cellPos(truck.position(), truck.getYRot(), new SupportRig.ShellCell(column, row, 0, SupportRig.Cell.ROOM, 0));
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private SupportVehicleEntity truck(MinecraftServer server) {
        return (SupportVehicleEntity) server.overworld().getEntity(UUID.fromString(this.id));
    }

    private float truck(net.minecraft.client.Minecraft mc) {
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof SupportVehicleEntity t) return t.getYRot();
        return 0;
    }
}
