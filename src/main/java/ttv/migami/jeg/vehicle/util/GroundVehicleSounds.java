package ttv.migami.jeg.vehicle.util;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Ground vehicles heard the way the aircraft are (fleet plan Phase 0.1 and 0.2, guide section 7): their engines play on
 * the client through GroundVehicleAudio (idle, working, cab and distant loops from design/ground-vehicles/prepare_audio.py,
 * the track clank for tracked hulls) on the shared distance curve, and the server sends far tracks so they are heard
 * beyond entity range. A vehicle listed here no longer plays the old once-a-second engine sound.
 *
 * @param idleLoudness loudness before distance with the engine idling (AircraftSoundRange; the A-10 flat out is 2.5)
 * @param workLoudness loudness flat out
 * @param tracked      a tracked hull: the track clank plays with the tracks' speed
 * @param cab          the driver sits in an enclosed hull: in first person they hear the muffled cab loop
 */
public record GroundVehicleSounds(float idleLoudness, float workLoudness, boolean tracked, boolean cab) {
    private static final Map<String, GroundVehicleSounds> PROFILES = Map.of(
            "bmp2", new GroundVehicleSounds(1.0F, 1.7F, true, true),
            "lav150", new GroundVehicleSounds(0.8F, 1.4F, false, true),
            "plz05", new GroundVehicleSounds(1.1F, 1.8F, true, true),
            "leopard2", new GroundVehicleSounds(1.2F, 2.0F, true, true));

    /** The profile for a vehicle id (the data id's path), or null for a vehicle heard another way. */
    @Nullable
    public static GroundVehicleSounds forVehicle(String vehicleId) {
        return PROFILES.get(vehicleId);
    }

    /** Loudness at a throttle from 0 (idle) to 1 (flat out). */
    public float loudness(float throttle) {
        return this.idleLoudness + (this.workLoudness - this.idleLoudness) * Math.max(0.0F, Math.min(1.0F, throttle));
    }
}
