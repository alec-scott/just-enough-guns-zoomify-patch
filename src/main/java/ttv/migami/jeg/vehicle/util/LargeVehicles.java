package ttv.migami.jeg.vehicle.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Vehicles whose parts reach more than two blocks from their origin. Vanilla finds an entity by the 16-block section its
 * origin is in, looking only two blocks round the searched box: a click or a round at a Chinook's nose (8 m ahead of its
 * origin) found nothing once the nose stood in the next section (the two-player play-test, 2026-11-25: the cockpit
 * couldn't be clicked to board). Searches that must see whole vehicles (rounds, the player's view) add these.
 */
public final class LargeVehicles {
    /** Vanilla's margin round a searched box (EntitySectionStorage). */
    private static final double SECTION_MARGIN = 2.0D;
    private static final Set<VehicleEntity> SERVER = ConcurrentHashMap.newKeySet();
    private static final Set<VehicleEntity> CLIENT = ConcurrentHashMap.newKeySet();

    private LargeVehicles() {
    }

    /** Each tick, from the vehicle: kept while it is large and loaded. */
    public static void track(VehicleEntity vehicle) {
        AABB parts = vehicle.partBounds();
        double reach = Math.max(Math.max(vehicle.getX() - parts.minX, parts.maxX - vehicle.getX()),
                Math.max(Math.max(vehicle.getZ() - parts.minZ, parts.maxZ - vehicle.getZ()), parts.maxY - vehicle.getY()));
        Set<VehicleEntity> set = vehicle.level().isClientSide() ? CLIENT : SERVER;
        if (reach > SECTION_MARGIN) {
            set.add(vehicle);
        } else {
            set.remove(vehicle);
        }
    }

    /** The large vehicles in this level whose parts meet the box (whether or not vanilla's search found them). */
    public static List<VehicleEntity> meeting(Level level, AABB box) {
        Set<VehicleEntity> set = level.isClientSide() ? CLIENT : SERVER;
        List<VehicleEntity> found = new ArrayList<>();
        for (VehicleEntity vehicle : set) {
            if (vehicle.isRemoved()) {
                set.remove(vehicle);
                continue;
            }
            if (vehicle.level() == level && vehicle.partBounds().intersects(box)) {
                found.add(vehicle);
            }
        }
        return found;
    }
}
