package ttv.migami.jeg.vehicle.entity;

import java.util.List;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The rocket technical (design/pickup/BRIEF.md): a 4x4 pickup with a Type 63 pod on a pedestal in its bed, as fielded
 * across the world's irregular forces. The driver lays it from the cab and ripples it off; it fires the Type-63's 107 mm
 * rockets (RocketPodEntity). It elevates from 10 degrees, so a ripple laid over the cab clears it.
 */
public final class PickupRocketEntity extends RocketPodEntity {
    public static final String ID = "pickup_rocket";
    /** The tubes' muzzles at rest, hull coordinates, top row first (design/pickup/rig.json). */
    public static final List<Vec3> MUZZLES = List.of(
            new Vec3(-0.2778D, 2.0833D, -0.5493D), new Vec3(-0.0925D, 2.0833D, -0.5493D), new Vec3(0.0927D, 2.0833D, -0.5493D), new Vec3(0.278D, 2.0833D, -0.5493D),
            new Vec3(-0.278D, 1.8981D, -0.5493D), new Vec3(-0.0927D, 1.8981D, -0.5493D), new Vec3(0.0927D, 1.8981D, -0.5493D), new Vec3(0.278D, 1.8981D, -0.5493D),
            new Vec3(-0.278D, 1.7128D, -0.5493D), new Vec3(-0.0927D, 1.7128D, -0.5493D), new Vec3(0.0925D, 1.7128D, -0.5493D), new Vec3(0.2778D, 1.7128D, -0.5493D));

    public PickupRocketEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level, ID);
    }

    @Override
    public List<Vec3> muzzlesRest() {
        return MUZZLES;
    }

    @Override
    public float minElevation() {
        return 10.0F;
    }

    @Override
    public float maxElevation() {
        return 60.0F;
    }

    @Override
    public String hudTitle() {
        return "hud.jeg.pickup_rocket.title";
    }
}
