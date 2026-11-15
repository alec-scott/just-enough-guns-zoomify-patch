package ttv.migami.jeg.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.client.KeyBindings;
import ttv.migami.jeg.mixin.GuiAccessor;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The vehicle HUD as a player first sees it, found driving the AH-6, A-10, FSV-6 and BD-40 in the pack client: each
 * vehicle is named as itself (it was "Vehicle Inventory"), the boarding line names the real dismount key (vanilla's said
 * Left Shift, which is a vehicle control and never ejects), and the info panel's place in first person and F5
 * (screenshots for review: it covered the vehicle in F5 and the aircraft's instruments from the seat).
 */
public class VehicleHudLayoutClientGameTest implements FabricClientGameTest {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-gametest");
    private static final String[][] VEHICLES = {
            {"ah6", "AH-6"}, {"a10", "A-10"}, {"truck", "FSV-6 Field Support Vehicle"}, {"bulldozer", "BD-40 Bulldozer"}};

    @Override public void runTest(ClientGameTestContext context) {
        StringBuilder failures = new StringBuilder();
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientLevel().waitForChunksRender();
            TestServerContext server = world.getServer();
            server.runCommand("gamerule show_advancement_messages false");
            server.runCommand("time set noon");
            int x = 0;
            for (String[] vehicle : VEHICLES) {
                String id = vehicle[0];
                server.runCommand("ride @p dismount");
                server.runCommand("tp @p " + x + " -60 0 0 0");
                server.runCommand("summon jeg:" + id + " " + x + " -60 6 {Energy:5000000}");
                context.waitTicks(5);
                server.runCommand("ride @p mount @e[type=jeg:" + id + ",sort=nearest,limit=1]");
                context.waitFor(mc -> mc.player.getVehicle() instanceof VehicleEntity);
                context.waitTicks(3);
                String name = context.computeOnClient(mc -> mc.player.getVehicle().getName().getString());
                String boarding = context.computeOnClient(mc -> {
                    Component line = ((GuiAccessor) mc.gui).jeg$getOverlayMessageString();
                    return line == null ? "" : line.getString();
                });
                String dismountKey = context.computeOnClient(mc -> KeyBindings.VEHICLE_DISMOUNT.getTranslatedKeyMessage().getString());
                LOG.info("[vehicle-hud] {}: name '{}', boarding line '{}'", id, name, boarding);
                if (!name.equals(vehicle[1])) failures.append(id).append(" is named '").append(name).append("'\n");
                if (!boarding.contains(dismountKey) || boarding.contains("Shift")) {
                    failures.append(id).append(" boarding line '").append(boarding).append("' does not name ").append(dismountKey).append('\n');
                }
                context.runOnClient(mc -> { mc.options.setCameraType(CameraType.FIRST_PERSON); mc.player.setYRot(mc.player.getVehicle().getYRot()); mc.player.setXRot(10); });
                context.waitTicks(5);
                context.takeScreenshot("hud_" + id + "_first_person");
                context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
                context.waitTicks(10);
                context.takeScreenshot("hud_" + id + "_chase");
                context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
                x += 40;
            }
            server.runCommand("ride @p dismount");
        }
        if (failures.length() > 0) {
            LOG.error("[vehicle-hud] FAILED: {}", failures);
            throw new AssertionError(failures.toString());
        }
        LOG.info("[vehicle-hud] COMPLETE");
    }
}
