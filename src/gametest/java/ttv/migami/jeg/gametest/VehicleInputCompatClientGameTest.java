package ttv.migami.jeg.gametest;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.vehicle.network.VehicleInputPayload;

/**
 * Singleplayer passes packets as objects, so the game tests never exercise the wire format. Encode and decode the
 * vehicle input packet by hand: the current packet must round-trip, and a pre-yftm.6 packet (no barrel-roll byte)
 * must still decode instead of disconnecting the player.
 */
public class VehicleInputCompatClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("jeg-gametest");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            String report = world.getServer().computeOnServer(server -> {
                VehicleInputPayload current = new VehicleInputPayload(42, true, false, true, false, false, true, false, true,
                        false, false, false, false, 2, true, false, 0.5F, -0.25F, 0.75F, 1);
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
                VehicleInputPayload.STREAM_CODEC.encode(buf, current);
                VehicleInputPayload roundTrip = VehicleInputPayload.STREAM_CODEC.decode(buf);

                // yftm.5 wire format: everything up to cruise speed, no barrel-roll byte.
                RegistryFriendlyByteBuf old = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
                old.writeVarInt(42);
                for (int i = 0; i < 12; i++) {
                    old.writeBoolean(i % 2 == 0);
                }
                old.writeVarInt(2);
                old.writeBoolean(true);
                old.writeBoolean(false);
                old.writeFloat(0.5F);
                old.writeFloat(-0.25F);
                old.writeFloat(0.75F);
                VehicleInputPayload fromOld = VehicleInputPayload.STREAM_CODEC.decode(old);
                return "roundTripEqual=" + current.equals(roundTrip) + " oldClient barrelRoll=" + fromOld.barrelRoll()
                        + " cruise=" + fromOld.cruiseSpeed() + " leftover=" + old.readableBytes();
            });
            LOGGER.info("[vehicle-input-compat] {}", report);
            if (!report.equals("roundTripEqual=true oldClient barrelRoll=0 cruise=0.75 leftover=0")) {
                throw new AssertionError("Vehicle input wire format: " + report);
            }
        }
    }
}
