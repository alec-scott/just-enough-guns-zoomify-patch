package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;
import ttv.migami.jeg.vehicle.util.VehicleWeaponStats;

/** A large missile and a pair of pod rockets fly across the view: motor glow, flame and the smoke trails they leave. */
public class MissileFlightClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set noon");
            server.runCommand("tp @a 0 -59 0 0 -8");
            context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            context.runOnClient(mc -> mc.gui.getChat().clearMessages(true));
            context.waitTicks(40);
            server.runOnServer(s -> {
                ServerLevel level = s.overworld();
                Entity owner = EntityType.MARKER.create(level, EntitySpawnReason.COMMAND);
                owner.setPos(-40.0D, -40.0D, 30.0D);
                level.addFreshEntity(owner);
                level.addFreshEntity(new VehicleMissileEntity(level, owner, null, new Vec3(-40.0D, -50.0D, 26.0D),
                        new Vec3(0.6D, 0.04D, 0.0D), Reference.id("vehicle_kh39_missile")));
                var stats = VehicleWeaponStats.get(Reference.id("vehicle_80mm_rocket"));
                var player = s.getPlayerList().getPlayers().get(0);
                for (int i = 0; i < 2; i++) {
                    BulletEntity rocket = new BulletEntity(level, player, stats, new Vec3(1.6D, 0.02D, 0.0D));
                    rocket.initialisePosition(new Vec3(-45.0D - i * 4.0D, -54.0D + i, 18.0D));
                    level.addFreshEntity(rocket);
                }
            });
            int waited = 0;
            for (int shot : new int[] {8, 16, 26, 45}) {
                context.waitTicks(shot - waited);
                waited = shot;
                context.takeScreenshot("missile_flight_t" + shot);
            }
        }
    }
}
