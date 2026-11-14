package ttv.migami.jeg.gametest;

import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.block.entity.VehicleContainerBlockEntity;
import ttv.migami.jeg.vehicle.client.audio.BulldozerAudio;
import ttv.migami.jeg.vehicle.client.render.AircraftLights;
import ttv.migami.jeg.vehicle.client.render.BulldozerRenderer;
import ttv.migami.jeg.vehicle.client.screen.VehicleAssemblingScreen;
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;
import ttv.migami.jeg.vehicle.terrain.BulldozerTerrainWork;
import ttv.migami.jeg.vehicle.util.BulldozerRig;

/**
 * The BD-40 end to end on a dedicated server, driven through the real keys: assembly, the visible operator and
 * all-round view, truck-style driving speed, the blade linkage against its work volume, clearing a mixed bank and
 * a tall bank, hardness limits, a tree felled whole (by FallingTree in the pack, by the dozer otherwise),
 * protection, a full hold, the raised blade, audio, lights and persistence.
 */
public class BulldozerClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static volatile boolean deny;
    private static boolean registered;
    private String id;

    @Override public void runTest(ClientGameTestContext context) {
        if (!registered) { PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, entity) -> !deny); registered = true; }
        BulldozerRenderer.captureRig = true;
        try (var server = context.worldBuilder().createServer(); var world = server.connect()) {
            try {
                world.getClientLevel().waitForChunksRender();
                server.runCommand("gamerule show_advancement_messages false");
                server.runCommand("gamerule advance_time false");
                server.runCommand("gamerule spawn_radius 0");
                server.runCommand("gamemode survival @a");
                server.runCommand("effect give @a resistance infinite 255 true");
                server.runCommand("time set noon");
                server.runCommand("forceload add -16 -16 16 64");
                server.runCommand("tp @a 0 -60 0 0 0");
                server.runCommand("clear @a");
                this.assembleAndMount(context, server);

                // ---- Start-up, the operator seen in the seat, and an all-round view.
                context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
                context.waitTicks(30);
                context.takeScreenshot("bulldozer_hud_boarding");
                context.waitTicks(BulldozerEntity.START_TICKS);
                check(server.computeOnServer(s -> dozer(s).engineRunning()), "engine did not start");
                check(context.computeOnClient(mc -> BulldozerAudio.loopCount(mc.player.getVehicle().getId())) == 8, "engine sound layers missing");
                check(context.computeOnClient(mc -> !((BulldozerEntity) mc.player.getVehicle()).shouldHidePassenger(mc.player)), "operator is hidden");
                float relative = context.computeOnClient(mc -> {
                    mc.player.setYRot(mc.player.getVehicle().getYRot() + 170);
                    return 0F;
                });
                context.waitTicks(3);
                relative = context.computeOnClient(mc -> Mth.wrapDegrees(mc.player.getYRot() - mc.player.getVehicle().getYRot()));
                check(Math.abs(relative) > 160, "operator cannot look behind the machine: " + relative);
                context.runOnClient(mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.options.hideGui = true; });
                context.waitTicks(4);
                LOG.info("[bulldozer] first person: player yaw {} vehicle yaw {} camera yaw {}", context.computeOnClient(mc -> mc.player.getYRot()),
                        context.computeOnClient(mc -> mc.player.getVehicle().getYRot()), context.computeOnClient(mc -> mc.gameRenderer.getMainCamera().yRot()));
                context.takeScreenshot("bulldozer_look_behind");
                context.runOnClient(mc -> { mc.player.setYRot(mc.player.getVehicle().getYRot()); mc.player.setXRot(18); });
                context.waitTicks(4);
                context.takeScreenshot("bulldozer_operator_view");
                context.runOnClient(mc -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setXRot(10); mc.player.setYRot(mc.player.getVehicle().getYRot() + 35); });
                context.waitTicks(4);
                context.takeScreenshot("bulldozer_front_operator");
                context.runOnClient(mc -> { mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); mc.player.setYRot(mc.player.getVehicle().getYRot() + 140); mc.player.setXRot(12); });
                context.waitTicks(4);
                context.takeScreenshot("bulldozer_side_operator");
                context.runOnClient(mc -> { mc.player.setYRot(mc.player.getVehicle().getYRot()); mc.player.setXRot(15); mc.options.hideGui = false; });
                context.waitTicks(3);
                context.takeScreenshot("bulldozer_hud");

                // ---- Blade linkage against the work volume, lift limits and the grade lock.
                checkRig(context);
                context.getInput().holdKey(options -> options.keyJump); context.waitTicks(40); context.getInput().releaseKey(options -> options.keyJump);
                context.waitTicks(4);
                check(lift(server) >= BulldozerRig.LIFT_MAX - .01F, "Space did not raise the blade fully: " + lift(server));
                checkRig(context);
                context.takeScreenshot("bulldozer_blade_raised");
                double eyeBefore = context.computeOnClient(mc -> mc.gameRenderer.getMainCamera().position().y - mc.player.getVehicle().getY());
                context.getInput().holdKey(options -> options.keyShift); context.waitTicks(20);
                // Shift lowers the blade; it must not crouch the operator or sink the camera into the seat.
                check(context.computeOnClient(mc -> mc.player.getPose() != net.minecraft.world.entity.Pose.CROUCHING && !mc.player.isCrouching()),
                        "Shift crouched the operator in the seat");
                check(server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().getPose() != net.minecraft.world.entity.Pose.CROUCHING),
                        "Shift crouched the operator on the server");
                double eyeDuring = context.computeOnClient(mc -> mc.gameRenderer.getMainCamera().position().y - mc.player.getVehicle().getY());
                check(Math.abs(eyeDuring - eyeBefore) < .02, "Shift moved the camera: " + eyeBefore + " -> " + eyeDuring);
                context.waitTicks(20); context.getInput().releaseKey(options -> options.keyShift);
                context.waitTicks(4);
                check(lift(server) == 0, "grade lock did not stop the blade at grade: " + lift(server));
                check(server.computeOnServer(s -> dozer(s).getVehicle() == null && s.getPlayerList().getPlayers().getFirst().getVehicle() == dozer(s)), "Shift dismounted the operator");
                context.getInput().pressKey(KeyBindings.VEHICLE_DEPLOY_DECOY); context.waitTicks(4);
                check(!server.computeOnServer(s -> dozer(s).gradeAssist()), "G did not release the grade lock");
                context.getInput().holdKey(options -> options.keyShift); context.waitTicks(10); context.getInput().releaseKey(options -> options.keyShift);
                check(lift(server) == BulldozerRig.LIFT_MIN, "blade did not lower below grade with the lock off");
                checkRig(context);
                context.getInput().pressKey(KeyBindings.VEHICLE_DEPLOY_DECOY); context.waitTicks(4);
                check(server.computeOnServer(s -> dozer(s).gradeAssist()) && lift(server) == 0, "grade lock did not restore grade height");

                // ---- Driving: truck-style controls at a working machine's speed, under a tree canopy 3 blocks up.
                server.runCommand("fill -3 -57 6 3 -57 12 oak_leaves[persistent=true]");
                double top = this.drive(context, server, 130);
                check(server.computeOnServer(s -> dozer(s).getZ() > 13), "the canopy stopped the machine under leaves 3 blocks up: "
                        + server.computeOnServer(s -> dozer(s).position() + " top speed so far"));
                check(server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(0, -57, 9)).is(Blocks.OAK_LEAVES)), "the machine hit the canopy");
                LOG.info("[bulldozer] free-travel speed {} blocks/tick ({} km/h)", top, Math.round(top * 72));
                check(top > .16 && top < .3, "travel speed out of range: " + top);

                // ---- A mixed bank: soil, stone and deepslate, then a six-high bank and a separate overhang above it.
                this.reset(context, server);
                server.runOnServer(s -> {
                    var level = s.overworld();
                    for (int x = -2; x < 2; x++) {
                        for (int z = 6; z <= 8; z++) for (int y = -60; y <= -58; y++) level.setBlock(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState(), 3);
                        level.setBlock(new BlockPos(x, -60, 9), Blocks.STONE.defaultBlockState(), 3);
                        level.setBlock(new BlockPos(x, -59, 9), Blocks.STONE.defaultBlockState(), 3);
                        level.setBlock(new BlockPos(x, -60, 10), Blocks.DEEPSLATE.defaultBlockState(), 3);
                        for (int y = -60; y <= -55; y++) level.setBlock(new BlockPos(x, y, 13), Blocks.DIRT.defaultBlockState(), 3);
                        level.setBlock(new BlockPos(x, -60, 16), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
                        level.setBlock(new BlockPos(x, -54, 16), Blocks.OAK_PLANKS.defaultBlockState(), 3);
                    }
                });
                context.getInput().holdKey(options -> options.keyUp);
                boolean slowed = false;
                for (int i = 0; i < 260; i++) {
                    context.waitTicks(1);
                    int status = server.computeOnServer(s -> dozer(s).workStatus());
                    if (status == BulldozerTerrainWork.BUSY) slowed = true;
                    if (i == 62) context.takeScreenshot("bulldozer_clearing_bank");
                }
                context.getInput().releaseKey(options -> options.keyUp);
                check(server.computeOnServer(s -> {
                    var level = s.overworld();
                    for (int x = -2; x < 2; x++) for (int z = 6; z <= 16; z++) for (int y = -60; y <= -55; y++)
                        if (!level.getBlockState(new BlockPos(x, y, z)).isAir()) return false;
                    return true;
                }), "blade left material in the cut");
                check(server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(0, -54, 16)).is(Blocks.OAK_PLANKS)), "a separate overhang was cleared");
                check(server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(0, -61, 9)).is(Blocks.GRASS_BLOCK)), "grade lock let the blade dig the ground");
                check(server.computeOnServer(s -> Math.abs(dozer(s).getY() + 60) < .01 && dozer(s).getZ() > 14), "dozer climbed the bank or stalled: "
                        + server.computeOnServer(s -> dozer(s).position()));
                check(slowed, "a full blade never held the machine back");
                check(server.computeOnServer(s -> dozer(s).vehicleInventory().countItem(Items.DIRT) >= 30
                        && dozer(s).vehicleInventory().countItem(Items.COBBLESTONE) == 8
                        && dozer(s).vehicleInventory().countItem(Items.COBBLED_DEEPSLATE) == 4), "spoil did not reach the hold: "
                        + server.computeOnServer(s -> dozer(s).vehicleInventory().countItem(Items.DIRT) + " dirt, "
                        + dozer(s).vehicleInventory().countItem(Items.COBBLESTONE) + " cobble, " + dozer(s).vehicleInventory().countItem(Items.COBBLED_DEEPSLATE) + " deepslate"));

                // ---- Hardness: iron (5) is cleared; obsidian (50) and a chest stop the blade.
                this.reset(context, server);
                this.wall(server, 6, Blocks.IRON_BLOCK.defaultBlockState());
                this.drive(context, server, 70);
                check(this.wallCount(server, 6) == 0, "iron (hardness 5) was not cleared");
                check(server.computeOnServer(s -> dozer(s).vehicleInventory().countItem(Items.IRON_BLOCK)) == 8, "iron blocks were not kept");
                this.reset(context, server);
                this.wall(server, 6, Blocks.OBSIDIAN.defaultBlockState());
                server.runOnServer(s -> s.overworld().setBlock(new BlockPos(0, -58, 6), Blocks.CHEST.defaultBlockState(), 3));
                context.getInput().holdKey(options -> options.keyUp);
                context.waitTicks(70);
                check(server.computeOnServer(s -> dozer(s).getZ() < 6 - 2.85 && dozer(s).workStatus() == BulldozerTerrainWork.SOLID),
                        "blade passed into obsidian: " + server.computeOnServer(s -> dozer(s).getZ() + " status " + dozer(s).workStatus()));
                context.getInput().releaseKey(options -> options.keyUp);
                check(this.wallCount(server, 6) == 8, "obsidian was cut");
                check(server.computeOnServer(s -> s.overworld().getBlockState(new BlockPos(0, -58, 6)).is(Blocks.CHEST)), "a chest was cut");

                // ---- A tree is felled whole, as an axe would, and its wood goes into the hold.
                this.reset(context, server);
                server.runOnServer(s -> dozer(s).vehicleInventory().clearContent());
                server.runCommand("place feature minecraft:oak 0 -60 9");
                context.waitTicks(2);
                int logs = this.logs(server);
                check(logs >= 4, "test tree did not grow: " + logs + " logs");
                this.drive(context, server, 40);
                context.takeScreenshot("bulldozer_tree_felled");
                this.drive(context, server, 50);
                check(this.logs(server) == 0, "tree was not felled whole: " + this.logs(server) + " logs remain"
                        + (FabricLoader.getInstance().isModLoaded("fallingtree") ? " (FallingTree)" : " (dozer)"));
                check(server.computeOnServer(s -> dozer(s).vehicleInventory().countItem(Items.OAK_LOG)) >= logs - 1,
                        "felled wood did not reach the hold: " + server.computeOnServer(s -> dozer(s).vehicleInventory().countItem(Items.OAK_LOG)) + " of " + logs);
                LOG.info("[bulldozer] tree of {} logs felled by {}", logs, FabricLoader.getInstance().isModLoaded("fallingtree") ? "FallingTree" : "the dozer");

                // ---- Protected ground, the raised blade, and a full hold pushing spoil aside.
                this.reset(context, server);
                this.wall(server, 6, Blocks.DIRT.defaultBlockState());
                deny = true;
                context.getInput().holdKey(options -> options.keyUp);
                context.waitTicks(50);
                check(server.computeOnServer(s -> dozer(s).workStatus()) == BulldozerTerrainWork.PROTECTED, "protected status missing");
                context.getInput().releaseKey(options -> options.keyUp);
                deny = false;
                check(this.wallCount(server, 6) == 8, "protection was bypassed");
                this.reset(context, server);
                server.runOnServer(s -> { for (int x = -2; x < 2; x++) s.overworld().setBlock(new BlockPos(x, -60, 6), Blocks.DIRT.defaultBlockState(), 3); });
                context.getInput().holdKey(options -> options.keyJump); context.waitTicks(30); context.getInput().releaseKey(options -> options.keyJump);
                this.drive(context, server, 60);
                check(server.computeOnServer(s -> { int n = 0; for (int x = -2; x < 2; x++) if (!s.overworld().getBlockState(new BlockPos(x, -60, 6)).isAir()) n++; return n; }) == 4,
                        "the raised blade cut terrain");
                context.getInput().holdKey(options -> options.keyShift); context.waitTicks(30); context.getInput().releaseKey(options -> options.keyShift);
                this.reset(context, server);
                server.runOnServer(s -> { var d = dozer(s); for (int i = 0; i < d.vehicleContainerSlots(); i++) d.vehicleInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64)); });
                this.wall(server, 6, Blocks.DIRT.defaultBlockState());
                this.wall(server, 9, Blocks.IRON_BLOCK.defaultBlockState());
                this.drive(context, server, 90);
                check(this.wallCount(server, 6) == 0 && this.wallCount(server, 9) == 0, "a full hold stopped the work");
                check(server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(-8, -64, -4, 8, -50, 30),
                        e -> e.getItem().is(Items.DIRT)).isEmpty()), "spoil was dropped as items");
                // A riding player collects items around the machine, as vanilla riders do.
                check(server.computeOnServer(s -> s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(-8, -64, -4, 8, -50, 40),
                        e -> e.getItem().is(Items.IRON_BLOCK)).stream().mapToInt(e -> e.getItem().getCount()).sum()
                        + s.getPlayerList().getPlayers().getFirst().getInventory().countItem(Items.IRON_BLOCK)) == 8, "valuable drops were lost with a full hold");
                server.runOnServer(s -> { dozer(s).vehicleInventory().clearContent(); s.getPlayerList().getPlayers().getFirst().getInventory().clearContent(); });

                // ---- Cutting one level down: grade lock off, blade below grade, and the cut holds that level.
                this.reset(context, server);
                context.getInput().pressKey(KeyBindings.VEHICLE_DEPLOY_DECOY); context.waitTicks(4);
                context.getInput().holdKey(options -> options.keyShift); context.waitTicks(10); context.getInput().releaseKey(options -> options.keyShift);
                check(server.computeOnServer(s -> dozer(s).cutFloor()) == -61, "blade below grade did not lock its cut one level down: "
                        + server.computeOnServer(s -> dozer(s).cutFloor()));
                this.drive(context, server, 120);
                check(server.computeOnServer(s -> {
                    var level = s.overworld();
                    for (int x = -2; x < 2; x++) for (int z = 4; z <= 12; z++) {
                        if (!level.getBlockState(new BlockPos(x, -61, z)).isAir()) return false;
                        if (level.getBlockState(new BlockPos(x, -62, z)).isAir()) return false;
                    }
                    return Math.abs(dozer(s).getY() + 61) < .01;
                }), "the cut was not exactly one level deep: dozer at " + server.computeOnServer(s -> dozer(s).position()));
                context.takeScreenshot("bulldozer_cut_one_level");
                context.getInput().pressKey(KeyBindings.VEHICLE_DEPLOY_DECOY); context.waitTicks(4);
                check(server.computeOnServer(s -> dozer(s).gradeAssist() && dozer(s).cutFloor() == BulldozerEntity.NO_FLOOR), "grade lock did not end the cut");
                server.runCommand("fill -8 -62 -6 8 -62 40 dirt");
                server.runOnServer(s -> { dozer(s).vehicleInventory().clearContent(); s.getPlayerList().getPlayers().getFirst().getInventory().clearContent(); });

                // ---- The ripper lays a surface: stone bricks from the paving slot replace the ground and fill a hole.
                this.reset(context, server);
                server.runOnServer(s -> {
                    var d = dozer(s);
                    // A short stack then a full one: the ripper has to roll on through the bay.
                    d.vehicleInventory().setItem(BulldozerTerrainWork.pavingStart(d), new ItemStack(Items.STONE_BRICKS, 10));
                    d.vehicleInventory().setItem(BulldozerTerrainWork.pavingStart(d) + 1, new ItemStack(Items.STONE_BRICKS, 64));
                    s.overworld().setBlock(new BlockPos(0, -61, 6), Blocks.AIR.defaultBlockState(), 3);
                    s.overworld().setBlock(new BlockPos(-1, -61, 6), Blocks.AIR.defaultBlockState(), 3);
                });
                context.waitTicks(6);
                context.getInput().pressKey(KeyBindings.RELOAD);
                context.waitTicks(22);
                check(server.computeOnServer(s -> dozer(s).ripperDown() && dozer(s).ripperDeployed()), "R did not lower the ripper");
                context.takeScreenshot("bulldozer_ripper_down");
                this.drive(context, server, 110);
                check(server.computeOnServer(s -> {
                    for (int x = -2; x < 2; x++) for (int z = 0; z <= 8; z++)
                        if (!s.overworld().getBlockState(new BlockPos(x, -61, z)).is(Blocks.STONE_BRICKS)) return false;
                    return true;
                }), "the ripper did not lay a 4-wide surface: " + server.computeOnServer(s -> {
                    StringBuilder row = new StringBuilder();
                    for (int z = -2; z <= 10; z++) row.append(s.overworld().getBlockState(new BlockPos(0, -61, z)).getBlock().getName().getString().charAt(0));
                    return row.toString();
                }));
                check(server.computeOnServer(s -> dozer(s).vehicleInventory().countItem(Items.DIRT) > 20), "replaced ground was not kept in the hold");
                check(server.computeOnServer(s -> dozer(s).vehicleInventory().getItem(BulldozerTerrainWork.pavingStart(dozer(s))).isEmpty()
                        && BulldozerTerrainWork.pavingCount(dozer(s)) < 44), "the ripper did not draw on through the bay: "
                        + server.computeOnServer(s -> BulldozerTerrainWork.pavingCount(dozer(s))));
                context.takeScreenshot("bulldozer_paved");
                context.getInput().pressKey(KeyBindings.RELOAD);
                context.waitTicks(22);
                check(server.computeOnServer(s -> !dozer(s).ripperDown() && dozer(s).ripper(1) < .01F), "R did not raise the ripper");
                // ---- The hold marks the Ripper slot, which takes full blocks only.
                context.getInput().pressKey(options -> options.keyInventory);
                context.waitTicks(10);
                check(context.computeOnClient(mc -> mc.screen instanceof ttv.migami.jeg.vehicle.client.screen.VehicleScreen), "the hold did not open");
                check(context.computeOnClient(mc -> {
                    var menu = ((ttv.migami.jeg.vehicle.client.screen.VehicleScreen) mc.screen).getMenu();
                    var dozer = (BulldozerEntity) mc.player.getVehicle();
                    for (int i = 0; i < menu.toolCount(); i++) {
                        if (!(menu.slots.get(menu.toolStart() + i) instanceof ttv.migami.jeg.vehicle.menu.VehicleMenu.ToolSlot)) return false;
                    }
                    var slot = menu.slots.get(menu.toolStart());
                    return menu.toolStart() == BulldozerTerrainWork.pavingStart(dozer) && menu.toolCount() == BulldozerTerrainWork.PAVING_SLOTS
                            && !(menu.slots.get(menu.toolStart() - 1) instanceof ttv.migami.jeg.vehicle.menu.VehicleMenu.ToolSlot)
                            && slot.mayPlace(new ItemStack(Items.GRAVEL)) && !slot.mayPlace(new ItemStack(Items.STICK))
                            && !slot.mayPlace(new ItemStack(Items.OAK_SLAB)) && !slot.mayPlace(new ItemStack(Items.CHEST));
                }), "the hold's Ripper bay is not marked or does not take only full blocks");
                context.takeScreenshot("bulldozer_hold_ripper_bay");
                context.getInput().pressKey(options -> options.keyInventory);
                context.waitTicks(6);
                server.runOnServer(s -> dozer(s).vehicleInventory().clearContent());

                // ---- The horn sounds while the left mouse button is held.
                context.getInput().holdKey(options -> options.keyAttack);
                context.waitTicks(10);
                check(server.computeOnServer(s -> dozer(s).hornOn()), "the horn did not sound");
                check(context.computeOnClient(mc -> BulldozerAudio.loopVolume(mc.player.getVehicle().getId(), "horn")) > .1F, "the horn was not heard");
                context.getInput().releaseKey(options -> options.keyAttack);
                context.waitTicks(10);
                check(!server.computeOnServer(s -> dozer(s).hornOn()), "the horn kept sounding");

                // ---- Pivot turn with track sound, parking, lights and audio.
                this.reset(context, server);
                context.getInput().holdKey(options -> options.keyLeft); context.waitTicks(30);
                check(context.computeOnClient(mc -> BulldozerAudio.loopVolume(mc.player.getVehicle().getId(), "tracks")) > .01F, "pivoting tracks were silent");
                context.getInput().releaseKey(options -> options.keyLeft);
                check(server.computeOnServer(s -> Math.abs(Mth.wrapDegrees(dozer(s).getYRot()))) > 20, "stationary pivot did not turn");
                context.waitTicks(30);
                Vec3 parked = server.computeOnServer(s -> dozer(s).position());
                context.waitTicks(30);
                check(server.computeOnServer(s -> dozer(s).position().distanceTo(parked)) < .05, "parked dozer drifted: "
                        + server.computeOnServer(s -> dozer(s).position().subtract(parked)));
                server.runCommand("time set midnight"); context.waitTicks(20);
                check(context.computeOnClient(mc -> AircraftLights.spots().containsKey(mc.player.getVehicle().getId())), "work lights did not light the ground");
                context.runOnClient(mc -> { mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT); mc.player.setYRot(mc.player.getVehicle().getYRot() + 25); });
                context.waitTicks(5);
                context.takeScreenshot("bulldozer_night");
                context.runOnClient(mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setYRot(mc.player.getVehicle().getYRot()); mc.player.setXRot(20); });
                context.waitTicks(5);
                context.takeScreenshot("bulldozer_night_operator");
                context.runOnClient(mc -> mc.player.setYRot(mc.player.getVehicle().getYRot() + 120));
                context.waitTicks(3);
                context.takeScreenshot("bulldozer_night_operator_side");
                check(context.computeOnClient(mc -> BulldozerAudio.cab((BulldozerEntity) mc.player.getVehicle())), "operator audio not centred in first person");
                check(context.computeOnClient(mc -> BulldozerAudio.loopCount(mc.player.getVehicle().getId())) == 8, "loops were not restarted for the cab view");
                server.runCommand("time set noon"); context.waitTicks(20);
                check(context.computeOnClient(mc -> !AircraftLights.spots().containsKey(mc.player.getVehicle().getId())), "work lights stayed on by day");

                // ---- Persistence and shutdown.
                server.runOnServer(s -> {
                    var d = dozer(s);
                    var saved = d.saveVehicleContainerState();
                    var restored = ModEntities.BULLDOZER.get().create(s.overworld(), EntitySpawnReason.COMMAND);
                    restored.loadVehicleContainerState(saved);
                    check(restored.bladeLift() == d.bladeLift() && restored.gradeAssist() == d.gradeAssist(), "saved blade state changed");
                    s.getPlayerList().getPlayers().getFirst().stopRiding();
                });
                context.waitTicks(120);
                check(server.computeOnServer(s -> !dozer(s).powered()), "engine kept running after the operator left");
                check(context.computeOnClient(mc -> { for (var e : mc.level.entitiesForRendering()) if (e instanceof BulldozerEntity d) return BulldozerAudio.loopCount(d.getId()) == 0; return false; }),
                        "engine audio did not stop after shutdown");
                LOG.info("[bulldozer] COMPLETE: assembly, visible operator and all-round view, driving, linkage, banks, hardness, tree felling, protection, full hold, raised blade, audio, lights and persistence passed");
            } catch (RuntimeException | Error failure) {
                // Dedicated-server shutdown may exit the JVM before Fabric reports an assertion.
                LOG.error("[bulldozer] FAILED", failure);
                context.takeScreenshot("bulldozer_failure");
                throw failure;
            }
        } finally {
            context.runOnClient(mc -> mc.options.hideGui = false);
            deny = false;
            BulldozerRenderer.captureRig = false;
        }
    }

    private void assembleAndMount(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("give @a iron_ingot 28"); server.runCommand("give @a copper_ingot 8"); server.runCommand("give @a redstone 6");
        server.runCommand("setblock 2 -60 0 jeg:vehicle_assembling_table");
        server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().openMenu((MenuProvider) s.overworld().getBlockEntity(new BlockPos(2, -60, 0))));
        context.waitTicks(15);
        check(context.computeOnClient(mc -> mc.screen instanceof VehicleAssemblingScreen), "assembly screen missing");
        context.runOnClient(mc -> {
            for (var child : mc.screen.children()) if (child instanceof Button button && button.getMessage().getString().contains("BD-40")) { button.onPress(null); return; }
            throw new AssertionError("BD-40 missing from selection list");
        });
        context.waitTicks(5);
        check(context.computeOnClient(mc -> ((VehicleAssemblingScreen) mc.screen).canAssemble()), "recipe not available");
        context.runOnClient(mc -> {
            for (var child : mc.screen.children()) if (child instanceof Button button && button.getMessage().getString().equals("Assemble")) { button.onPress(null); return; }
            throw new AssertionError("Assemble button missing");
        });
        context.waitTicks(10);
        server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().closeContainer());
        context.runOnClient(mc -> mc.setScreen(null));
        server.runCommand("setblock 2 -60 0 air");
        this.id = server.computeOnServer(s -> {
            var player = s.getPlayerList().getPlayers().getFirst();
            ItemStack container = ItemStack.EMPTY;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i).is(ModItems.VEHICLE_CONTAINER.get())) container = player.getInventory().getItem(i).copy();
            check(!container.isEmpty(), "assembly did not deliver a container");
            player.getInventory().clearContent();
            player.setItemInHand(InteractionHand.MAIN_HAND, container);
            player.teleportTo(4, -60, -4);
            container.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(new Vec3(.5, -60, .5), Direction.UP, new BlockPos(0, -61, 0), false)));
            var placed = s.overworld().getBlockEntity(new BlockPos(0, -60, 0));
            check(placed instanceof VehicleContainerBlockEntity && ((VehicleContainerBlockEntity) placed).deploy(player), "assembled container did not deploy");
            var dozer = s.overworld().getEntitiesOfClass(BulldozerEntity.class, new AABB(-5, -64, -5, 5, -50, 5)).getFirst();
            dozer.snapTo(0, -60, 0, 0, 0);
            dozer.addEnergy(dozer.maxVehicleEnergy());
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.startRiding(dozer, true, true);
            return dozer.getUUID().toString();
        });
    }

    /** Clears the test strip and parks the machine at the origin facing +Z with the blade at grade. */
    private void reset(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("fill -8 -60 -6 8 -45 30 air");
        server.runCommand("fill -8 -61 -6 8 -61 30 grass_block");
        server.runCommand("kill @e[type=item]");
        server.runOnServer(s -> { var d = dozer(s); d.snapTo(0, -60, 0, 0, 0); d.setDeltaMovement(Vec3.ZERO); });
        context.waitTicks(6);
    }

    /** Holds W for the given ticks and returns the highest speed reached, blocks per tick. */
    private double drive(ClientGameTestContext context, TestServerContext server, int ticks) {
        context.getInput().holdKey(options -> options.keyUp);
        double top = 0;
        Vec3 last = server.computeOnServer(s -> dozer(s).position());
        for (int i = 0; i < ticks; i += 5) {
            context.waitTicks(5);
            Vec3 now = server.computeOnServer(s -> dozer(s).position());
            top = Math.max(top, now.subtract(last).horizontalDistance() / 5);
            last = now;
        }
        context.getInput().releaseKey(options -> options.keyUp);
        context.waitTicks(10);
        return top;
    }

    private void wall(TestServerContext server, int z, BlockState state) {
        server.runOnServer(s -> { for (int x = -2; x < 2; x++) for (int y = -60; y < -58; y++) s.overworld().setBlock(new BlockPos(x, y, z), state, 3); });
    }

    private int wallCount(TestServerContext server, int z) {
        return server.computeOnServer(s -> { int n = 0; for (int x = -2; x < 2; x++) for (int y = -60; y < -58; y++) if (!s.overworld().getBlockState(new BlockPos(x, y, z)).isAir()) n++; return n; });
    }

    private int logs(TestServerContext server) {
        return server.computeOnServer(s -> {
            int n = 0;
            for (BlockPos pos : BlockPos.betweenClosed(-4, -60, 4, 4, -48, 14)) if (s.overworld().getBlockState(pos).is(BlockTags.LOGS)) n++;
            return n;
        });
    }

    private BulldozerEntity dozer(MinecraftServer server) { return (BulldozerEntity) server.overworld().getEntity(UUID.fromString(this.id)); }
    private float lift(TestServerContext server) { return server.computeOnServer(s -> dozer(s).bladeLift()); }

    private void checkRig(ClientGameTestContext context) {
        context.waitTicks(2);
        double error = context.computeOnClient(mc -> {
            var d = (BulldozerEntity) mc.player.getVehicle();
            var shown = BulldozerRenderer.renderedCut;
            return shown == null ? 1000D : shown.distanceTo(BulldozerRig.cuttingCenter(d, d.bladeLift(1)));
        });
        check(error < .06, "rendered cutting edge disagrees with the work volume: " + error + context.computeOnClient(mc -> {
            var d = (BulldozerEntity) mc.player.getVehicle();
            return " rendered=" + BulldozerRenderer.renderedCut + " expected=" + BulldozerRig.cuttingCenter(d, d.bladeLift(1)) + " lift=" + d.bladeLift();
        }));
        double rodError = context.computeOnClient(mc -> {
            var d = (BulldozerEntity) mc.player.getVehicle();
            var p = BulldozerRig.liftCylinderEnd(d.bladeLift(1));
            return BulldozerRenderer.renderedRodEnd == null ? 1000D : BulldozerRenderer.renderedRodEnd.distanceTo(BulldozerRig.world(d, p.x, p.y, p.z));
        });
        check(rodError < .08, "lift cylinder rod detached from its clevis: " + rodError + context.computeOnClient(mc -> " shown=" + BulldozerRenderer.renderedRodEnd));
    }

    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
