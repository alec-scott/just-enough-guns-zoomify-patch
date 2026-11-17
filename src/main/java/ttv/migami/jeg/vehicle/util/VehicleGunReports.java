package ttv.migami.jeg.vehicle.util;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The ground vehicles' guns heard as the aircraft guns are (fleet plan Phase 0.3, guide section 7): each shot sends a
 * {@link ttv.migami.jeg.network.GunReportPayload} with the gun's report set ({@code <sound>.fire}, {@code .fire_distant},
 * {@code .fire_interior}; design/vehicle-guns/prepare_audio.py) and a loudness on the shared distance curve, instead of
 * a hand-gun sound with Minecraft's linear fall-off. Their rounds also fly the long-range path. Helicopter and jet guns
 * keep their own looping sound.
 */
public final class VehicleGunReports {
    /** A gun's report set and how loud it is before distance (the aircraft guns are 2.5). */
    public record Report(String sound, float loudness) {}

    private static final Report CANNON_20MM = new Report("vehicle.gun.20mm", 2.3F);
    private static final Report CANNON_30MM = new Report("vehicle.gun.30mm", 2.5F);
    private static final Report HEAVY_MG = new Report("vehicle.gun.hmg", 2.0F);
    private static final Report COAX = new Report("vehicle.gun.coax", 1.6F);

    private VehicleGunReports() {}

    /** The report for a vehicle gun, or null for weapons heard another way (missiles, shells, hand guns). */
    @Nullable
    public static Report forWeapon(@Nullable Identifier weapon) {
        if (weapon == null) {
            return null;
        }
        String path = weapon.getPath();
        return switch (path) {
            case "vehicle_20mm_cannon" -> CANNON_20MM;
            case "vehicle_30mm_cannon" -> CANNON_30MM;
            case "vehicle_coax_machine_gun" -> COAX;
            default -> path.startsWith("vehicle_") && (path.endsWith("_hmg") || path.endsWith("_rws")) ? HEAVY_MG : null;
        };
    }
}
