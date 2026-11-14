package ttv.migami.jeg.vehicle.util;

import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.registries.DeferredHolder;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.gun.GunStats;
import ttv.migami.jeg.init.ModSounds;
import ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

public final class VehicleSoundHelper {
    private static final Identifier LOCKING_WARNING = Reference.id("vehicle.locking_warning");
    private static final Identifier LOCKED_WARNING = Reference.id("vehicle.locked_warning");
    private static final Identifier MISSILE_WARNING = Reference.id("vehicle.missile_warning");
    private static final Identifier MINIGUN_FIRE_LOOP = Reference.id("item.minigun.fire_loop");
    private static final Identifier AH6_FIRE_LOOP = Reference.id("vehicle.ah6.cannon_fire");
    private static final Map<String, Identifier> FIRE_SOUNDS = Map.ofEntries(
            Map.entry("a10", Reference.id("vehicle.a10.fire")),
            Map.entry("ah6", Reference.id("vehicle.ah6.cannon_fire")),
            Map.entry("bmp2", Reference.id("vehicle.bmp2.cannon_fire")),
            Map.entry("hpj11", Reference.id("vehicle.hpj11.fire")),
            Map.entry("laser_tower", Reference.id("vehicle.laser_tower.fire")),
            Map.entry("lav150", Reference.id("vehicle.lav150.cannon_fire")),
            Map.entry("mi28", Reference.id("vehicle.mi28.cannon_fire")),
            Map.entry("waveforce_tower", Reference.id("vehicle.waveforce_tower.fire"))
    );
    private static final Map<String, Identifier> GUIDED_FIRE_SOUNDS = Map.of(
            "bmp2", Reference.id("vehicle.bmp2.missile_fire")
    );
    private static final Map<String, Identifier> ENGINE_SOUNDS = Map.ofEntries(
            Map.entry("a10", Reference.id("vehicle.a10.engine")),
            Map.entry("ah6", Reference.id("vehicle.ah6.engine")),
            Map.entry("bmp2", Reference.id("vehicle.bmp2.engine")),
            Map.entry("lav150", Reference.id("vehicle.lav150.engine")),
            Map.entry("mi28", Reference.id("vehicle.mi28.engine")),
            Map.entry("speedboat", Reference.id("vehicle.speedboat.engine")),
            Map.entry("tom6", Reference.id("vehicle.tom6.engine")),
            Map.entry("truck", Reference.id("vehicle.truck.engine"))
    );

    private VehicleSoundHelper() {}

    public static SoundEvent lockWarning() {
        return lockedWarning();
    }

    public static SoundEvent lockingWarning() {
        DeferredHolder<SoundEvent, SoundEvent> sound = ModSounds.ALL.get(LOCKING_WARNING);
        return sound != null ? sound.get() : SoundEvents.NOTE_BLOCK_PLING.value();
    }

    public static SoundEvent lockedWarning() {
        DeferredHolder<SoundEvent, SoundEvent> sound = ModSounds.ALL.get(LOCKED_WARNING);
        return sound != null ? sound.get() : SoundEvents.NOTE_BLOCK_PLING.value();
    }

    public static SoundEvent missileWarning() {
        DeferredHolder<SoundEvent, SoundEvent> sound = ModSounds.ALL.get(MISSILE_WARNING);
        return sound != null ? sound.get() : lockedWarning();
    }

    /** A jet's gun (the A-10's GAU-8), as opposed to its missiles: fired as a looping burst like a helicopter's. */
    public static boolean isJetGun(VehicleEntity vehicle, VehicleWeaponInfo weapon) {
        String weaponPath = weapon.weaponId().getPath();
        return vehicle.isJet() && !weapon.guided() && !weaponPath.contains("rocket") && !weaponPath.contains("missile");
    }

    /** A jet's own sound: {@code vehicle.<id>.<name>}, falling back to the A-10's; null if neither exists. */
    public static SoundEvent jetSound(VehicleEntity vehicle, String name) {
        DeferredHolder<SoundEvent, SoundEvent> sound = ModSounds.ALL.get(Reference.id("vehicle." + vehicle.vehicleDataId().getPath() + "." + name));
        if (sound == null) {
            sound = ModSounds.ALL.get(Reference.id("vehicle.a10." + name));
        }
        return sound == null ? null : sound.get();
    }

    /**
     * The same sound as the pilot hears it from inside: the "_cockpit" version (muffled through the airframe) where the
     * jet has one, else the sound itself.
     */
    public static SoundEvent jetCockpitSound(VehicleEntity vehicle, String name) {
        SoundEvent cockpit = jetSound(vehicle, name + "_cockpit");
        return cockpit != null ? cockpit : jetSound(vehicle, name);
    }

    public static SoundEvent jetGearSound(VehicleEntity vehicle) {
        return jetSound(vehicle, "gear");
    }

    public static SoundEvent jetTouchdownSound(VehicleEntity vehicle) {
        return jetSound(vehicle, "touchdown");
    }

    /** A helicopter's machine gun, as opposed to its rockets and missiles. */
    public static boolean isHelicopterGun(VehicleEntity vehicle, VehicleWeaponInfo weapon) {
        String weaponPath = weapon.weaponId().getPath();
        return vehicle.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER
                && !weapon.guided() && !weaponPath.contains("rocket") && !weaponPath.contains("missile");
    }

    public static SoundEvent fireSound(VehicleEntity vehicle, VehicleWeaponInfo weapon, GunStats fallbackStats) {
        if (isHelicopterGun(vehicle, weapon)) {
            Identifier id = "ah6".equals(vehicle.vehicleDataId().getPath()) ? AH6_FIRE_LOOP : MINIGUN_FIRE_LOOP;
            DeferredHolder<SoundEvent, SoundEvent> loop = ModSounds.ALL.get(id);
            if (loop != null) {
                return loop.get();
            }
        }
        String weaponPath = weapon.weaponId().getPath();
        if ("vehicle_20mm_cannon".equals(weaponPath)
                || !weaponPath.startsWith("vehicle_")
                || "vehicle_coax_machine_gun".equals(weaponPath)) {
            return fallbackStats.fireSoundEvent().orElse(SoundEvents.CROSSBOW_SHOOT);
        }
        if (weaponPath.contains("rocket") || weaponPath.contains("missile")) {
            return fallbackStats.fireSoundEvent().orElse(SoundEvents.CROSSBOW_SHOOT);
        }
        String vehiclePath = vehicle.vehicleDataId().getPath();
        Identifier id = weapon.guided() ? GUIDED_FIRE_SOUNDS.get(vehiclePath) : FIRE_SOUNDS.get(vehiclePath);
        if (id == null) {
            id = FIRE_SOUNDS.get(vehiclePath);
        }
        DeferredHolder<SoundEvent, SoundEvent> sound = id == null ? null : ModSounds.ALL.get(id);
        if (sound != null) {
            return sound.get();
        }
        return fallbackStats.fireSoundEvent().orElse(SoundEvents.CROSSBOW_SHOOT);
    }

    public static SoundEvent engineSound(VehicleEntity vehicle) {
        DeferredHolder<SoundEvent, SoundEvent> sound = ModSounds.ALL.get(ENGINE_SOUNDS.get(vehicle.vehicleDataId().getPath()));
        return sound == null ? null : sound.get();
    }

    /** A helicopter rotor's speed at full operating RPM (see HelicopterEngineAudio). */
    public static final float HELICOPTER_OPERATING_ROTOR_SPEED = 0.085F;

    /** Whether this is a flying vehicle whose engine is heard from far off (a jet or a helicopter). */
    public static boolean isAircraft(VehicleEntity vehicle) {
        return vehicle.isJet() || vehicle.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER;
    }

    /**
     * How loud an aircraft's engine is heard from outside, before distance: the engine's sound volume scaled by how hard
     * it runs (a jet's spool, a helicopter's rotor speed). Louder engines carry further (see AircraftSoundRange).
     */
    public static float aircraftEngineLoudness(VehicleEntity vehicle) {
        float engineVolume = Math.max(0.1F, vehicle.vehicleData().defaults().engine().engineSoundVolume());
        if (vehicle.isJet()) {
            // The recording is the engines at full power; while they wind up to idle the loop fades in.
            float spool = net.minecraft.util.Mth.clamp(vehicle.jetSpool(), 0.0F, 1.0F);
            float lit = net.minecraft.util.Mth.clamp(spool / 0.08F, 0.0F, 1.0F);
            return engineVolume * lit * lit * (0.35F + 0.65F * spool);
        }
        return engineVolume * Math.min(1.0F, helicopterRpm(vehicle));
    }

    /** The engine loop's pitch before Doppler: idle low, full power at the recording's own pitch. */
    public static float aircraftEnginePitch(VehicleEntity vehicle) {
        if (vehicle.isJet()) {
            return 0.62F + 0.38F * net.minecraft.util.Mth.clamp(vehicle.jetSpool(), 0.0F, 1.0F);
        }
        return net.minecraft.util.Mth.clamp(0.35F + helicopterRpm(vehicle) * 0.65F, 0.35F, 1.12F);
    }

    private static float helicopterRpm(VehicleEntity vehicle) {
        return net.minecraft.util.Mth.clamp(vehicle.propellerSpeed() / HELICOPTER_OPERATING_ROTOR_SPEED, 0.0F, 1.25F);
    }

    public static SoundEvent engineStartSound(VehicleEntity vehicle) {
        Identifier configured = vehicle.vehicleData().defaults().engine().engineStartSound();
        DeferredHolder<SoundEvent, SoundEvent> sound = configured == null ? null : ModSounds.ALL.get(configured);
        if (sound != null) {
            return sound.get();
        }
        return engineSound(vehicle);
    }
}
