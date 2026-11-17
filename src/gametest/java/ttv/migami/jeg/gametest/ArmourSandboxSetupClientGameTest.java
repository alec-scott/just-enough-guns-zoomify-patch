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
 * south: the Mk42, BL-132, Type-63, 105 mm howitzer, PLZ-05, Leopard 2A7, BMP-2 and LAV-150, the FSV-6 (to tow the
 * howitzer and the Type-63), and the mortar and TOW set up. Every vehicle is charged and its hold carries full stacks of
 * each ammunition its weapons take. The player carries a spyglass (the observer), mortar rounds, a spare mortar and
 * TOW, and rifle ammunition. Downrange: parked BMP-2s as targets at 100, 200 and 400 blocks.
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
                    "gamerule spawn_radius 0", "difficulty peaceful", "forceload add -80 -16 80 420",
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
                {ModEntities.HOWITZER.get(), -36}, {ModEntities.PLZ05.get(), -24}, {ModEntities.LEOPARD2.get(), -10},
                {ModEntities.BMP2.get(), 4}, {ModEntities.LAV150.get(), 16}, {ModEntities.TRUCK.get(), 30},
                {ModEntities.MORTAR.get(), 42}, {ModEntities.TOW.get(), 48}};
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
