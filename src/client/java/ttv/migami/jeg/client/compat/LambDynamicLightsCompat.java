package ttv.migami.jeg.client.compat;

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import ttv.migami.jeg.vehicle.client.render.AircraftLights;

/**
 * LambDynamicLights (in the yftm pack): where a helicopter's searchlight or the A-10's taxi light lands, the ground is
 * lit for real, so blocks, mobs and players in the beam light up rather than only having a glow drawn over them.
 * Loaded only by LambDynamicLights' own entrypoint, so the mod is not needed without it.
 */
public final class LambDynamicLightsCompat implements DynamicLightsInitializer {
    private final Map<Integer, SpotLight> lights = new HashMap<>();

    @Override
    public void onInitializeDynamicLights(DynamicLightsContext context) {
        DynamicLightBehaviorManager manager = context.dynamicLightBehaviorManager();
        ClientTickEvents.END_CLIENT_TICK.register(client -> this.sync(manager));
    }

    /** One light per lit spot: added when a beam lands, moved with it, removed when it goes out. */
    private void sync(DynamicLightBehaviorManager manager) {
        Map<Integer, AircraftLights.Spot> spots = AircraftLights.spots();
        Iterator<Map.Entry<Integer, SpotLight>> it = this.lights.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, SpotLight> entry = it.next();
            if (!spots.containsKey(entry.getKey())) {
                entry.getValue().removed = true;
                manager.remove(entry.getValue());
                it.remove();
            }
        }
        for (Map.Entry<Integer, AircraftLights.Spot> entry : spots.entrySet()) {
            AircraftLights.Spot spot = entry.getValue();
            SpotLight light = this.lights.get(entry.getKey());
            if (light == null) {
                light = new SpotLight(spot);
                this.lights.put(entry.getKey(), light);
                manager.add(light);
            } else {
                light.state = spot;
            }
        }
    }

    /**
     * A light source floating just off the surface a beam lands on. LambDynamicLights reads it off its own thread, so the
     * current spot is swapped in whole; it is told the light has moved only once it has moved half a block or changed
     * strength, so a hovering helicopter does not keep the terrain under it relighting.
     */
    private static final class SpotLight implements DynamicLightBehavior {
        volatile AircraftLights.Spot state;
        volatile boolean removed;
        private AircraftLights.Spot reported;

        SpotLight(AircraftLights.Spot spot) {
            this.state = spot;
        }

        @Override
        public double lightAtPos(BlockPos pos, double falloffRatio) {
            AircraftLights.Spot spot = this.state;
            double dx = pos.getX() + 0.5D - spot.pos().x;
            double dy = pos.getY() + 0.5D - spot.pos().y;
            double dz = pos.getZ() + 0.5D - spot.pos().z;
            return spot.luminance() - Math.sqrt(dx * dx + dy * dy + dz * dz) * falloffRatio;
        }

        @Override
        public BoundingBox getBoundingBox() {
            AircraftLights.Spot spot = this.state;
            int reach = Mth.ceil(spot.luminance());
            int x = Mth.floor(spot.pos().x);
            int y = Mth.floor(spot.pos().y);
            int z = Mth.floor(spot.pos().z);
            return new BoundingBox(x - reach, y - reach, z - reach, x + reach, y + reach, z + reach);
        }

        @Override
        public boolean hasChanged() {
            AircraftLights.Spot spot = this.state;
            AircraftLights.Spot last = this.reported;
            if (last != null && last.pos().distanceToSqr(spot.pos()) < 0.25D
                    && Math.abs(last.luminance() - spot.luminance()) < 0.75F) {
                return false;
            }
            this.reported = spot;
            return true;
        }

        @Override
        public boolean isRemoved() {
            return this.removed;
        }
    }
}
