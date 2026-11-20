package ttv.migami.jeg.gametest;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Not a check: builds the ArmourTest play-test world with every vehicle ported or refit since yftm.21, fully equipped. A
 * flat creative world (cheats on, mobs off, keep inventory) at noon, the day running, and in a row along z 0 facing
 * south: the Mk42, BL-132, Type-63, 105 mm howitzer, PLZ-05, YX-100, BMP-2 and LAV-150, the FSV-6 (to tow the
 * howitzer and the Type-63), and the mortar and TOW set up, and the HPJ-11 and a sentry at the end of the row. East of
 * it a lake (3 deep, its surface level with the ground) with a sand beach one block high at its far end, and the
 * speedboat on it. West of the row, on a runway south: the bush plane, A-10, AH-6 and Mi-28; north of the lake the BD-40.
 * The whole fleet, for the user's test of every vehicle together (2026-11-18). Every vehicle is charged and its hold
 * carries full stacks of
 * each ammunition its weapons take. The player carries a spyglass (the observer), mortar rounds, a spare mortar and
 * TOW, and rifle ammunition. Downrange: parked BMP-2s as targets at 100, 200 and 400 blocks.
 * <p>
 * For the eight-point review (2026-11-19): a range of tall grass with squads of armour stands 22 blocks in front of the
 * Mk42, BL-132, PLZ-05 and YX-100 (built for grapeshot; the incendiary that replaced it sets the grass alight), and a
 * coarse dirt field
 * in front of the BMP-2, LAV-150 and FSV-6 for hard turns (the tracks stay on the dirt).
 * Build: {@code ./gradlew runClientGameTest -PgameTests=ArmourSandboxSetup -PwithPack ...}; saved into
 * {@code build/run/packClient/saves/ArmourTest}. Play: {@code ./gradlew runPackClient -PwithPack ... --args="--quickPlaySingleplayer ArmourTest"}.
 */
public class ArmourSandboxSetupClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");
    private static final String WORLD = "ArmourTest";
    private static final int Y = -60;

    @Override
    public void runTest(ClientGameTestContext context) {
        Path saved;
        try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(settings -> {
            settings.setName(WORLD);
            settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            settings.setAllowCommands(true);
        }).create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            for (String command : new String[]{
                    "defaultgamemode creative", "gamemode creative @a", "time set noon", "weather clear 1000000",
                    "gamerule advance_weather false", "gamerule spawn_mobs false", "gamerule keep_inventory true",
                    "gamerule spawn_radius 0", "difficulty peaceful", "forceload add -130 -40 100 420",
                    "setworldspawn 0 -60 -12", "tp @a 0 -60 -12 0 0"}) {
                server.runOnServer(s -> DozerSandboxSetupClientGameTest.run(s, command));
            }
            context.waitTicks(20);
            String placed = server.computeOnServer(ArmourSandboxSetupClientGameTest::build);
            LOGGER.warn("[armour-sandbox] {}", placed);
            context.waitTicks(40);
            server.runOnServer(s -> DozerSandboxSetupClientGameTest.run(s, "tp @a 0 -55 -16 0 15"));
            context.waitTicks(10);
            context.takeScreenshot("armour_sandbox");
            saved = server.computeOnServer(s -> s.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
        }
        Path target = Path.of("").toAbsolutePath().getParent().resolve("packClient").resolve("saves").resolve(WORLD);
        DozerSandboxSetupClientGameTest.copyWorld(saved, target);
        long entityBytes = DozerSandboxSetupClientGameTest.sizeOf(target.resolve("dimensions/minecraft/overworld/entities"));
        LOGGER.warn("[armour-sandbox] saved {} to {} (entity data {} bytes)", saved, target, entityBytes);
        if (entityBytes == 0) throw new AssertionError("The vehicles were not saved with the world");
    }

    private static String build(MinecraftServer server) {
        var level = server.overworld();
        Object[][] row = {
                {ModEntities.MK42.get(), -70}, {ModEntities.BL132.get(), -56}, {ModEntities.TYPE63.get(), -44},
                {ModEntities.HOWITZER.get(), -36}, {ModEntities.PLZ05.get(), -24}, {ModEntities.YX100.get(), -10},
                {ModEntities.BMP2.get(), 4}, {ModEntities.LAV150.get(), 16}, {ModEntities.TRUCK.get(), 30},
                {ModEntities.MORTAR.get(), 42}, {ModEntities.TOW.get(), 48}, {ModEntities.HPJ11.get(), 54}, {ModEntities.SENTRY.get(), 62}};
        StringBuilder report = new StringBuilder();
        for (Object[] entry : row) {
            @SuppressWarnings("unchecked")
            EntityType<? extends VehicleEntity> type = (EntityType<? extends VehicleEntity>) entry[0];
            VehicleEntity vehicle = type.create(level, EntitySpawnReason.COMMAND);
            vehicle.snapTo((int) entry[1] + 0.5D, Y, 0.5D, 0.0F, 0.0F);
            vehicle.addEnergy(vehicle.maxVehicleEnergy());
            int loaded = load(vehicle);
            level.addFreshEntity(vehicle);
            report.append(vehicle.vehicleDataId().getPath()).append(" (").append(loaded).append(" stacks) ");
        }
        // The lake and the speedboat.
        DozerSandboxSetupClientGameTest.run(server, "fill 68 -63 -10 96 -61 60 minecraft:water");
        DozerSandboxSetupClientGameTest.run(server, "fill 68 -63 61 96 -60 70 minecraft:sand");
        VehicleEntity boat = ModEntities.SPEEDBOAT.get().create(level, EntitySpawnReason.COMMAND);
        boat.snapTo(82.5D, Y - 0.2D, 0.5D, 0.0F, 0.0F);
        boat.addEnergy(boat.maxVehicleEnergy());
        report.append("speedboat (").append(load(boat)).append(" stacks) ");
        level.addFreshEntity(boat);
        // The earlier finished vehicles, for testing alongside (2026-11-17): the A-10 west of the row facing south down a
        // clear runway, the AH-6 and Mi-28 beside it, the BD-40 north of the lake; and the bush plane (2026-11-18) west of
        // the A-10, clear of its wing, on the same runway.
        Object[][] others = {
                {ModEntities.TOM6.get(), -128.5D, 0.5D}, {ModEntities.A10.get(), -112.5D, 0.5D}, {ModEntities.AH6.get(), -92.5D, 0.5D},
                {ModEntities.MI28.get(), -82.5D, 0.5D}, {ModEntities.BULLDOZER.get(), 76.5D, -26.5D}};
        for (Object[] entry : others) {
            @SuppressWarnings("unchecked")
            EntityType<? extends VehicleEntity> type = (EntityType<? extends VehicleEntity>) entry[0];
            VehicleEntity vehicle = type.create(level, EntitySpawnReason.COMMAND);
            vehicle.snapTo((double) entry[1], Y, (double) entry[2], 0.0F, 0.0F);
            vehicle.addEnergy(vehicle.maxVehicleEnergy());
            int loaded = load(vehicle);
            level.addFreshEntity(vehicle);
            report.append(vehicle.vehicleDataId().getPath()).append(" (").append(loaded).append(" stacks) ");
        }
        // The shell range: tall grass from the guns' muzzles out, a squad of armour stands in it 22 blocks off each gun.
        for (int gunX : new int[]{-70, -56, -24, -10}) {
            for (var pos : net.minecraft.core.BlockPos.betweenClosed(gunX - 5, Y, 9, gunX + 5, Y, 26)) {
                net.minecraft.world.level.block.DoublePlantBlock.placeAt(level,
                        net.minecraft.world.level.block.Blocks.TALL_GRASS.defaultBlockState(), pos.immutable(), 2);
            }
            for (int i = 0; i < 6; i++) {
                var stand = EntityType.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
                stand.snapTo(gunX - 1.5D + i * 0.7D, Y, 22.5D + (i % 2) * 0.8D, 180.0F, 0.0F);
                level.addFreshEntity(stand);
            }
        }
        // A dirt field for hard turns, in front of the BMP-2, LAV-150 and FSV-6.
        DozerSandboxSetupClientGameTest.run(server, "fill -2 -61 8 40 -61 80 minecraft:coarse_dirt");
        report.append("GS range at x -70 -56 -24 -10, dirt field x -2..40 z 8..80 ");
        // Targets downrange.
        for (int distance : new int[]{100, 200, 400}) {
            VehicleEntity bmp = ModEntities.BMP2.get().create(level, EntitySpawnReason.COMMAND);
            bmp.snapTo(-10.5D, Y, distance + 0.5D, 180.0F, 0.0F);
            level.addFreshEntity(bmp);
        }
        var player = server.getPlayerList().getPlayers().getFirst();
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.SPYGLASS));
        player.getInventory().add(new ItemStack(ModItems.MORTAR_ROUND.get(), 64));
        player.getInventory().add(new ItemStack(ModItems.MORTAR_ROUND.get(), 64));
        player.getInventory().add(new ItemStack(ModItems.TOW.get()));
        player.getInventory().add(new ItemStack(ModItems.AMMO.get(ttv.migami.jeg.Reference.id("rifle_ammo")).get(), 64));
        // For the BD-40's ripper (lane lock X, ripper R; the bay restocks from these).
        player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.STONE_BRICKS, 64));
        player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.STONE_BRICKS, 64));
        return report.toString();
    }

    /** Full stacks of each ammunition the vehicle's weapons take, two of each where the hold has room. */
    private static int load(VehicleEntity vehicle) {
        Set<Identifier> ammo = new LinkedHashSet<>();
        for (VehicleWeaponInfo weapon : vehicle.vehicleData().defaults().weapons()) {
            if (weapon.ammoId() != null) ammo.add(weapon.ammoId());
        }
        var hold = vehicle.vehicleInventory();
        int slot = 0, stacks = 0;
        for (Identifier id : ammo) {
            Item item = BuiltInRegistries.ITEM.getValue(id);
            if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
            for (int copy = 0; copy < 2 && slot < hold.getContainerSize(); copy++) {
                hold.setItem(slot++, new ItemStack(item, item.getDefaultMaxStackSize()));
                stacks++;
            }
        }
        return stacks;
    }
}
