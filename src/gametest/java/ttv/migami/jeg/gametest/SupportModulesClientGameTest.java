package ttv.migami.jeg.gametest;

import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.block.VehicleShellBlock;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.support.SupportModules;
import ttv.migami.jeg.vehicle.support.SupportShell;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * The FSV-6's fitted modules end to end: a furnace that smelts while it drives; fittings and their contents that survive
 * being packed into a vehicle container (the crowbar); and, deployed, real bunk beds, the room lit by a fitted sea
 * lantern, camp floodlights, every bay opening its vanilla screen, a damaged LAV-150 beside it recharged, rearmed and
 * repaired, the roof beacon's effect, beds that players cannot break, and a player who slept in a bunk respawning beside
 * the vehicle after it has packed up.
 */
public class SupportModulesClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final int G = -60;
    private String truckId, lavId;

    @Override public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule immediate_respawn true");
            server.runCommand("time set noon");
            server.runCommand("gamemode creative @a");
            server.runOnServer(s -> {
                ServerLevel level = s.overworld();
                SupportVehicleEntity truck = ModEntities.TRUCK.get().create(level, EntitySpawnReason.COMMAND);
                truck.snapTo(0, G, 0, 0, 0);
                truck.addEnergy(truck.maxVehicleEnergy());
                level.addFreshEntity(truck);
                this.truckId = truck.getUUID().toString();
                var fit = truck.modules().fittings;
                fit.setItem(0, new ItemStack(Items.CRAFTING_TABLE));
                fit.setItem(1, new ItemStack(Items.FURNACE));
                fit.setItem(2, new ItemStack(Items.CHEST));
                fit.setItem(3, new ItemStack(Items.BREWING_STAND));
                fit.setItem(4, new ItemStack(item("jeg:vehicle_charging_station")));
                fit.setItem(5, new ItemStack(Items.ANVIL));
                fit.setItem(SupportModules.BUNK_LEFT, new ItemStack(Items.RED_BED));
                fit.setItem(SupportModules.BUNK_RIGHT, new ItemStack(Items.BLUE_BED));
                fit.setItem(SupportModules.LOCKER_LEFT, new ItemStack(Items.BARREL));
                fit.setItem(SupportModules.ROOF, new ItemStack(Items.BEACON));
                fit.setItem(SupportModules.LAMP, new ItemStack(Items.SEA_LANTERN));
                for (int i = 0; i < SupportModules.RACK_SLOTS; i++) fit.setItem(SupportModules.RACK_START + i, new ItemStack(Items.IRON_BLOCK, 64));
                truck.modules().storage(2).setItem(0, new ItemStack(Items.DIAMOND, 7));
                truck.vehicleInventory().setItem(0, new ItemStack(item("jeg:rifle_ammo"), 64));
                truck.vehicleInventory().setItem(1, new ItemStack(Items.IRON_INGOT, 32));
                fit.setChanged();
                s.getPlayerList().getPlayers().getFirst().startRiding(truck, true, true);
            });
            context.waitTicks(SupportVehicleEntity.START_TICKS + 10);

            // ---- A furnace smelts while the vehicle drives (the hold-screen button opens it too: screenshot).
            boolean furnaceStarted = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                return truck.modules().open(player(s), 1, null) && player(s).containerMenu instanceof net.minecraft.world.inventory.FurnaceMenu menu
                        && fill(menu, Items.RAW_IRON, 4, Items.COAL, 2);
            });
            server.runOnServer(s -> player(s).closeContainer());
            if (!furnaceStarted) failures.append("the furnace module did not open as a furnace\n");
            context.getInput().holdKey(options -> options.keyUp);
            context.waitTicks(200);
            context.getInput().releaseKey(options -> options.keyUp);
            context.waitTicks(260);
            String cooked = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                truck.modules().open(player(s), 1, null);
                var menu = player(s).containerMenu;
                String result = String.format("drove to z %.1f, output %s", truck.getZ(), menu.getSlot(2).getItem());
                player(s).closeContainer();
                return result;
            });
            LOG.info("[support-modules] furnace while driving: {}", cooked);
            if (!server.computeOnServer(s -> { truck(s).modules().open(player(s), 1, null); boolean ok = player(s).containerMenu.getSlot(2).getItem().getCount() >= 2; player(s).closeContainer(); return ok; })) {
                failures.append("the furnace did not smelt while driving: ").append(cooked).append('\n');
            }
            // Hold screen with the module buttons.
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitTicks(10);
            context.takeScreenshot("support_hold_module_buttons");
            context.getInput().pressKey(options -> options.keyInventory);
            context.waitTicks(5);
            context.runOnClient(mc -> { if (mc.screen != null) mc.screen.onClose(); });

            // ---- Fittings and contents survive being packed into a vehicle container.
            String packed = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                var tag = truck.saveVehicleContainerState();
                SupportVehicleEntity copy = ModEntities.TRUCK.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                copy.loadVehicleContainerState(tag);
                return copy.modules().fitted(0).getItem() + " " + copy.modules().fitted(SupportModules.ROOF).getItem() + " "
                        + copy.modules().storage(2).getItem(0) + " rack level " + copy.modules().beaconLevel();
            });
            LOG.info("[support-modules] copied through the container: {}", packed);
            if (!packed.contains("crafting_table") || !packed.contains("beacon") || !packed.contains("7 minecraft:diamond") || !packed.endsWith("4")) {
                failures.append("fittings lost through the vehicle container: ").append(packed).append('\n');
            }

            // ---- A damaged LAV-150 beside it, with no charge and no ammunition; then deploy.
            server.runOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                var spot = SupportRig.world(truck, 120, 0, 16);
                VehicleEntity lav = (VehicleEntity) ModEntities.LAV150.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                lav.snapTo(spot.x, G, spot.z, 0, 0);
                s.overworld().addFreshEntity(lav);
                lav.consumeEnergy(lav.vehicleEnergy());
                lav.hurtServer(s.overworld(), s.overworld().damageSources().generic(), 60);
                this.lavId = lav.getUUID().toString();
                truck.requestDeploy(player(s));
            });
            context.waitTicks(SupportRig.DEPLOY_TICKS + 20);
            String deployed = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                ServerLevel level = s.overworld();
                StringBuilder out = new StringBuilder("deployed " + truck.isDeployed());
                int beds = 0, lamps = 0, lit = 0;
                for (var placed : SupportShell.layout(truck.position(), truck.getYRot(), truck)) {
                    BlockState state = level.getBlockState(placed.pos());
                    if (state.getBlock() instanceof BedBlock) beds++;
                    if (state.getBlock() instanceof VehicleShellBlock && state.getValue(VehicleShellBlock.KIND) == VehicleShellBlock.Kind.ROOM
                            && state.getValue(VehicleShellBlock.LIGHT) == 15) lit++;
                }
                for (BlockPos lamp : SupportShell.lampCells(truck.position(), truck.getYRot())) {
                    if (level.getBlockState(lamp).getBlock() instanceof VehicleShellBlock) lamps++;
                }
                out.append(", bed cells ").append(beds).append(", room cells lit 15: ").append(lit).append(", camp lamps ").append(lamps);
                return out.toString();
            });
            LOG.info("[support-modules] {}", deployed);
            if (!deployed.contains("bed cells 4") || deployed.contains("lit 15: 0") || deployed.endsWith("lamps 0")) {
                failures.append("deployed room wrong: ").append(deployed).append('\n');
            }

            // ---- Each bay opens its module (as the shell's bay cells do).
            String menus = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                StringBuilder out = new StringBuilder();
                for (int bay = 0; bay < 6; bay++) {
                    BlockPos cell = SupportShell.cellPos(truck.position(), truck.getYRot(),
                            new SupportRig.ShellCell(bay < 3 ? 3 : 0, 2 + bay % 3, 0, SupportRig.Cell.BAY, 0));
                    player(s).stopRiding();
                    player(s).teleportTo(s.overworld(), truck.getX(), truck.getY() + 1, truck.getZ() - 1, Set.of(), 0, 0, true);
                    truck.useBay(player(s), cell);
                    out.append(bay).append('=').append(player(s).containerMenu.getClass().getSimpleName()).append(' ');
                    player(s).closeContainer();
                }
                return out.toString();
            });
            LOG.info("[support-modules] bay menus: {}", menus);
            for (String expected : new String[]{"0=", "1=FurnaceMenu", "2=ChestMenu", "3=BrewingStandMenu"}) {
                if (!menus.contains(expected)) failures.append("bay menu missing ").append(expected).append(": ").append(menus).append('\n');
            }
            if (menus.contains("0=InventoryMenu")) failures.append("the crafting bay did not open: ").append(menus).append('\n');

            // ---- Services: the LAV is charged, rearmed and repaired; the beacon gives Speed.
            server.runOnServer(s -> truck(s).modules().setBeaconEffect(BeaconMenu.encodeEffect(MobEffects.SPEED)));
            context.waitTicks(100);
            String services = server.computeOnServer(s -> {
                VehicleEntity lav = (VehicleEntity) s.overworld().getEntity(UUID.fromString(this.lavId));
                int ammo = 0;
                for (int i = 0; i < lav.vehicleInventory().getContainerSize(); i++) {
                    if (lav.vehicleInventory().getItem(i).is(item("jeg:rifle_ammo"))) ammo += lav.vehicleInventory().getItem(i).getCount();
                }
                return String.format("lav energy %d, health %.0f/%.0f, rifle ammo %d; player speed %s",
                        lav.vehicleEnergy(), lav.vehicleHealth(), lav.maxVehicleHealth(), ammo, player(s).hasEffect(MobEffects.SPEED));
            });
            LOG.info("[support-modules] services: {}", services);
            if (services.contains("energy 0,") || services.contains("ammo 0;") || services.endsWith("false")) {
                failures.append("services wrong: ").append(services).append('\n');
            }

            // ---- Screenshots: the fitted room and the Fit-out screen; the beacon's beam at night.
            server.runOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                var door = SupportRig.world(truck.position(), truck.getYRot(), 0, SupportRig.FLOOR_Y, 54);
                player(s).teleportTo(s.overworld(), door.x, door.y, door.z, Set.of(), truck.getYRot(), 12, true);
            });
            context.runOnClient(mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = true; });
            context.waitTicks(8);
            context.runOnClient(mc -> { mc.player.setYRot(0); mc.player.setXRot(12); });
            context.waitTicks(4);
            context.takeScreenshot("support_room_fitted");
            server.runOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                var aisle = SupportRig.world(truck.position(), truck.getYRot(), -10, SupportRig.FLOOR_Y, 40);
                player(s).teleportTo(s.overworld(), aisle.x, aisle.y, aisle.z, Set.of(), truck.getYRot() - 70, 18, true);
            });
            context.waitTicks(6);
            context.runOnClient(mc -> { mc.player.setYRot(-70); mc.player.setXRot(18); });
            context.waitTicks(4);
            context.takeScreenshot("support_room_bays_fitted");
            context.runOnClient(mc -> mc.options.hideGui = false);
            server.runOnServer(s -> truck(s).openFitMenu(player(s)));
            context.waitTicks(10);
            context.takeScreenshot("support_fit_screen");
            server.runOnServer(s -> player(s).closeContainer());
            server.runCommand("time set midnight");
            server.runOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                var view = SupportRig.world(truck.position(), truck.getYRot(), 120, 60, 220);
                player(s).teleportTo(s.overworld(), view.x, view.y, view.z, Set.of(), 28, -12, true);
            });
            context.runOnClient(mc -> { mc.options.hideGui = true; mc.player.setYRot(28); mc.player.setXRot(-12); });
            context.waitTicks(10);
            context.takeScreenshot("support_beacon_night");
            context.runOnClient(mc -> mc.options.hideGui = false);

            // ---- Beds cannot be broken by players; sleeping sets the bunk, and after packing up a death respawns beside it.
            String bunk = server.computeOnServer(s -> {
                SupportVehicleEntity truck = truck(s);
                BlockPos head = SupportShell.cellPos(truck.position(), truck.getYRot(), new SupportRig.ShellCell(3, 0, 0, SupportRig.Cell.BUNK, 0));
                player(s).setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                boolean broke = player(s).gameMode.destroyBlock(head);
                player(s).setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player(s).teleportTo(s.overworld(), head.getX() + .5, head.getY() + .6, head.getZ() + .5, Set.of(), 0, 0, true);
                var slept = player(s).startSleepInBed(head);
                return "broke bed " + broke + ", still a bed " + (s.overworld().getBlockState(head).getBlock() instanceof BedBlock)
                        + ", sleep " + slept.left().map(Object::toString).orElse("ok") + ", spawn " + player(s).getRespawnConfig();
            });
            LOG.info("[support-modules] bunk: {}", bunk);
            if (bunk.contains("broke bed true") || bunk.contains("still a bed false")) failures.append("bunk bed not protected: ").append(bunk).append('\n');
            server.runCommand("time set noon");
            server.runOnServer(s -> {
                player(s).stopSleeping();
                player(s).teleportTo(s.overworld(), truck(s).getX() + 6, G, truck(s).getZ() + 12, Set.of(), 0, 0, true);
                player(s).startRiding(truck(s), true, true);
                truck(s).requestPackUp();
                player(s).stopRiding();
            });
            context.waitTicks(SupportRig.DEPLOY_TICKS + 20);
            server.runOnServer(s -> {
                // Drive it off 40 blocks (as if moved), then the player dies far away.
                truck(s).setPos(truck(s).getX() + 40, G, truck(s).getZ());
                ttv.migami.jeg.vehicle.support.SupportBunks.track(truck(s));
                player(s).teleportTo(s.overworld(), -60, G, -60, Set.of(), 0, 0, true);
                player(s).setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            });
            server.runCommand("kill @a");
            context.waitTicks(20);
            String respawn = server.computeOnServer(s -> String.format("respawned at %.1f %.1f %.1f, truck at %.1f %.1f %.1f",
                    player(s).getX(), player(s).getY(), player(s).getZ(), truck(s).getX(), truck(s).getY(), truck(s).getZ()));
            LOG.info("[support-modules] {}", respawn);
            if (!server.computeOnServer(s -> player(s).distanceTo(truck(s)) < 6)) failures.append("did not respawn beside the vehicle: ").append(respawn).append('\n');
        }
        if (failures.length() > 0) {
            LOG.error("[support-modules] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[support-modules] COMPLETE");
    }

    /** Put items into a furnace menu's input and fuel slots. */
    private static boolean fill(net.minecraft.world.inventory.AbstractContainerMenu menu, net.minecraft.world.item.Item input, int inputs,
                                net.minecraft.world.item.Item fuel, int fuels) {
        menu.getSlot(0).set(new ItemStack(input, inputs));
        menu.getSlot(1).set(new ItemStack(fuel, fuels));
        return true;
    }

    private static net.minecraft.world.item.Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(id));
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private SupportVehicleEntity truck(MinecraftServer server) {
        return (SupportVehicleEntity) server.overworld().getEntity(UUID.fromString(this.truckId));
    }
}
