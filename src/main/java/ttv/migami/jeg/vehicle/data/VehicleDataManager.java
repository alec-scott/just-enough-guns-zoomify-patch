package ttv.migami.jeg.vehicle.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import ttv.migami.jeg.JustEnoughGuns;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.data.subdata.CameraPos;
import ttv.migami.jeg.vehicle.data.subdata.CollisionLevel;
import ttv.migami.jeg.vehicle.data.subdata.DamageModifierInfo;
import ttv.migami.jeg.vehicle.data.subdata.DestroyInfo;
import ttv.migami.jeg.vehicle.data.subdata.DismountInfo;
import ttv.migami.jeg.vehicle.data.subdata.EngineInfo;
import ttv.migami.jeg.vehicle.data.subdata.EngineType;
import ttv.migami.jeg.vehicle.data.subdata.OBBInfo;
import ttv.migami.jeg.vehicle.data.subdata.SeatInfo;
import ttv.migami.jeg.vehicle.data.subdata.SeekInfo;
import ttv.migami.jeg.vehicle.data.subdata.TrackInfo;
import ttv.migami.jeg.vehicle.data.subdata.WheelInfo;
import ttv.migami.jeg.vehicle.data.subdata.TurretInfo;
import ttv.migami.jeg.vehicle.data.subdata.VehicleContainerType;
import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo;

public final class VehicleDataManager {
    private static final Gson GSON = new Gson();
    private static volatile Map<Identifier, VehicleData> data = defaultsOnly();
    private static volatile Map<String, VehicleData> dataByString = indexByString(data);
    private static volatile VehicleData defaultFallback = data.get(DefaultVehicleData.TEST_WHEEL.id());
    private static volatile int version = 1;
    private static volatile Map<Identifier, String> syncedJson = Map.of();

    private VehicleDataManager() {}

    public static Loader reloadListener() {
        return new Loader();
    }

    public static int version() {
        return version;
    }

    public static VehicleData get(Identifier id) {
        VehicleData found = data.get(id);
        return found != null ? found : defaultFallback;
    }

    /** Fast lookup by serialized id string (avoids parsing an {@link Identifier} on every tick/frame). */
    public static VehicleData getByString(String id) {
        VehicleData found = dataByString.get(id);
        if (found != null) {
            return found;
        }
        return get(Identifier.parse(id));
    }

    private static void publish(Map<Identifier, VehicleData> loaded) {
        Map<Identifier, VehicleData> immutable = Map.copyOf(loaded);
        data = immutable;
        dataByString = indexByString(immutable);
        defaultFallback = immutable.get(DefaultVehicleData.TEST_WHEEL.id());
        version++;
    }

    private static Map<String, VehicleData> indexByString(Map<Identifier, VehicleData> map) {
        Map<String, VehicleData> byString = new HashMap<>(map.size() * 2);
        for (Map.Entry<Identifier, VehicleData> entry : map.entrySet()) {
            byString.put(entry.getKey().toString(), entry.getValue());
        }
        return Map.copyOf(byString);
    }

    public static VehicleData testVehicle() {
        return get(DefaultVehicleData.TEST_WHEEL.id());
    }

    public static Map<Identifier, VehicleData> all() {
        return Map.copyOf(data);
    }

    public static Map<Identifier, String> syncedJson() {
        return Map.copyOf(syncedJson);
    }

    public static void applySyncedJson(Map<Identifier, String> objects) {
        Map<Identifier, VehicleData> loaded = new HashMap<>(defaultsOnly());
        for (Map.Entry<Identifier, String> entry : objects.entrySet()) {
            try {
                JsonObject object = JsonParser.parseString(entry.getValue()).getAsJsonObject();
                VehicleData vehicleData = parse(entry.getKey(), object);
                loaded.put(vehicleData.id(), vehicleData);
            } catch (RuntimeException exception) {
                JustEnoughGuns.LOGGER.error("Failed to apply synced vehicle data {}: {}", entry.getKey(), exception.getMessage());
            }
        }
        publish(loaded);
    }

    private static Map<Identifier, VehicleData> defaultsOnly() {
        return Map.of(
                DefaultVehicleData.TEST_WHEEL.id(), new VehicleData(DefaultVehicleData.TEST_WHEEL),
                DefaultVehicleData.LIGHT_COMBAT.id(), new VehicleData(DefaultVehicleData.LIGHT_COMBAT),
                DefaultVehicleData.TEST_HELICOPTER.id(), new VehicleData(DefaultVehicleData.TEST_HELICOPTER),
                DefaultVehicleData.TEST_BOAT.id(), new VehicleData(DefaultVehicleData.TEST_BOAT),
                DefaultVehicleData.TEST_ARTILLERY.id(), new VehicleData(DefaultVehicleData.TEST_ARTILLERY),
                DefaultVehicleData.TEST_AIRCRAFT.id(), new VehicleData(DefaultVehicleData.TEST_AIRCRAFT)
        );
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> implements IdentifiableResourceReloadListener {
        private static final FileToIdConverter LISTER = FileToIdConverter.json("vehicles");

        @Override
        public Identifier getFabricId() {
            return Reference.id("vehicle_data");
        }

        @Override
        protected Map<Identifier, JsonElement> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<Identifier, JsonElement> objects = new HashMap<>();
            LISTER.listMatchingResources(manager).forEach((fileId, resource) -> {
                Identifier id = LISTER.fileToId(fileId);
                try (var reader = resource.openAsReader()) {
                    objects.put(id, GsonHelper.fromJson(GSON, reader, JsonElement.class));
                } catch (IOException | RuntimeException exception) {
                    JustEnoughGuns.LOGGER.error("Failed to read vehicle data {}: {}", id, exception.getMessage());
                }
            });
            return objects;
        }

        @Override
        protected void apply(Map<Identifier, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
            Map<Identifier, VehicleData> loaded = new HashMap<>(defaultsOnly());
            Map<Identifier, String> raw = new HashMap<>();
            for (Map.Entry<Identifier, JsonElement> entry : objects.entrySet()) {
                try {
                    JsonObject object = entry.getValue().getAsJsonObject();
                    raw.put(entry.getKey(), GSON.toJson(object));
                    VehicleData vehicleData = parse(entry.getKey(), object);
                    loaded.put(vehicleData.id(), vehicleData);
                } catch (RuntimeException exception) {
                    JustEnoughGuns.LOGGER.error("Failed to load vehicle data {}: {}", entry.getKey(), exception.getMessage());
                }
            }
            publish(loaded);
            syncedJson = Map.copyOf(raw);
            JustEnoughGuns.LOGGER.info("Loaded {} JEG vehicle data entries", data.size());
        }
    }

    private static VehicleData parse(Identifier id, JsonObject object) {
        DefaultVehicleData fallback = fallbackFor(id);
        String entityType = getString(object, "entity_type", fallback.entityType());
        VehicleType vehicleType = getEnum(object, "vehicle_type", VehicleType.class, fallback.vehicleType());
        float maxHealth = getFloat(object, "max_health", fallback.maxHealth());
        float autoRepairPerTick = getFloat(object, "auto_repair_per_tick", fallback.autoRepairPerTick());
        int autoRepairCooldownTicks = getInt(object, "auto_repair_cooldown_ticks", fallback.autoRepairCooldownTicks());
        int maxEnergy = getInt(object, "max_energy", fallback.maxEnergy());
        float upStep = getFloat(object, "up_step", fallback.upStep());
        EngineInfo engine = parseEngine(getObject(object, "engine"), fallback.engine());
        List<SeatInfo> seats = parseSeats(object, fallback.seats());
        boolean allowFreeCam = getBoolean(object, "allow_free_cam", fallback.allowFreeCam());
        VehicleContainerType containerType = getEnum(object, "container_type", VehicleContainerType.class, fallback.containerType());
        CameraPos camera = parseCamera(getObject(object, "third_person_camera"), fallback.thirdPersonCamera());
        DismountInfo dismount = parseDismount(getObject(object, "dismount"), fallback.dismount());
        CollisionLevel collisionLevel = getEnum(object, "collision_level", CollisionLevel.class, fallback.collisionLevel());
        OBBInfo obb = parseObb(object, "obb", fallback.obb());
        OBBInfo deployedObb = parseObb(object, "obb_deployed", null);
        TrackInfo tracks = parseTracks(getObject(object, "tracks"));
        WheelInfo wheels = parseWheels(getObject(object, "wheels"));
        ttv.migami.jeg.vehicle.data.subdata.BoatInfo boat = parseBoat(getObject(object, "boat"));
        VehicleArmorProfile armor = parseArmor(getObject(object, "armor"), fallback.armor());
        DamageModifierInfo damageModifier = parseDamageModifier(getObject(object, "damage_modifier"), fallback.damageModifier());
        List<VehicleWeaponInfo> weapons = parseWeapons(object, fallback.weapons());
        TurretInfo turret = parseTurret(getObject(object, "turret"), fallback.turret());
        boolean hasDecoy = getBoolean(object, "has_decoy", fallback.hasDecoy());
        SeekInfo seek = parseSeek(getObject(object, "seek"), fallback.seek());
        DestroyInfo destroy = parseDestroy(getObject(object, "destroy"), fallback.destroy());

        return new VehicleData(new DefaultVehicleData(
                id,
                entityType,
                vehicleType,
                maxHealth,
                autoRepairPerTick,
                autoRepairCooldownTicks,
                maxEnergy,
                upStep,
                engine,
                seats,
                allowFreeCam,
                containerType,
                camera,
                dismount,
                collisionLevel,
                obb,
                armor,
                damageModifier,
                weapons,
                turret,
                hasDecoy,
                seek,
                destroy
        ), deployedObb, tracks, wheels, boat).withAircraft(parseAircraft(getObject(object, "aircraft")))
                .withAmphibious(parseAmphibious(getObject(object, "amphibious")))
                .withTurretRiders(parseIntList(object, "turret_riders"))
                .withNoFirstPersonSeats(parseIntList(object, "no_first_person_seats"))
                .withStandingSeats(parseIntList(object, "standing_seats"))
                .withGripSeats(parseIntList(object, "grip_seats"))
                .withMass(getDouble(object, "mass", Double.NaN));
    }

    private static DefaultVehicleData fallbackFor(Identifier id) {
        if (id.equals(DefaultVehicleData.LIGHT_COMBAT.id())) {
            return DefaultVehicleData.LIGHT_COMBAT;
        }
        if (id.equals(DefaultVehicleData.TEST_HELICOPTER.id())) {
            return DefaultVehicleData.TEST_HELICOPTER;
        }
        if (id.equals(DefaultVehicleData.TEST_BOAT.id())) {
            return DefaultVehicleData.TEST_BOAT;
        }
        if (id.equals(DefaultVehicleData.TEST_ARTILLERY.id())) {
            return DefaultVehicleData.TEST_ARTILLERY;
        }
        if (id.equals(DefaultVehicleData.TEST_AIRCRAFT.id())) {
            return DefaultVehicleData.TEST_AIRCRAFT;
        }
        return DefaultVehicleData.TEST_WHEEL;
    }

    private static EngineInfo parseEngine(JsonObject object, EngineInfo fallback) {
        if (object == null) {
            return fallback;
        }
        return new EngineInfo(
                getEnum(object, "type", EngineType.class, fallback.type()),
                getDouble(object, "acceleration", fallback.acceleration()),
                getDouble(object, "max_forward_speed", fallback.maxForwardSpeed()),
                getDouble(object, "max_reverse_speed", fallback.maxReverseSpeed()),
                getDouble(object, "friction", fallback.friction()),
                getDouble(object, "steering_speed", fallback.steeringSpeed()),
                getInt(object, "energy_cost_rate", fallback.energyCostRate()),
                getDouble(object, "increment", fallback.increment()),
                getDouble(object, "decrement", fallback.decrement()),
                getDouble(object, "pitch_speed", fallback.pitchSpeed()),
                getDouble(object, "yaw_speed", fallback.yawSpeed()),
                getDouble(object, "roll_speed", fallback.rollSpeed()),
                getDouble(object, "lift_speed", fallback.liftSpeed()),
                getResourceLocation(object, "engine_start_sound", fallback.engineStartSound()),
                getFloat(object, "engine_sound_volume", fallback.engineSoundVolume()),
                getDouble(object, "player_max_speed", fallback.playerMaxSpeed()),
                getDouble(object, "player_acceleration", fallback.playerAcceleration()),
                getDouble(object, "player_weight", fallback.playerWeight()),
                getDouble(object, "stall_speed", fallback.stallSpeed()),
                getDouble(object, "rotate_speed", fallback.rotateSpeed()),
                getDouble(object, "max_dive_speed", fallback.maxDiveSpeed())
        );
    }

    private static CameraPos parseCamera(JsonObject object, CameraPos fallback) {
        if (object == null) {
            return fallback;
        }
        return new CameraPos(
                getDouble(object, "x", fallback.x()),
                getDouble(object, "y", fallback.y()),
                getDouble(object, "z", fallback.z()),
                getDouble(object, "zoom_x", fallback.zoomX()),
                getDouble(object, "zoom_y", fallback.zoomY()),
                getDouble(object, "zoom_z", fallback.zoomZ()),
                getBoolean(object, "use_fixed_camera_pos", fallback.useFixedCameraPos()),
                getBoolean(object, "use_aircraft_camera", fallback.useAircraftCamera()),
                getDouble(object, "aircraft_x", fallback.aircraftX()),
                getDouble(object, "aircraft_y", fallback.aircraftY()),
                getDouble(object, "aircraft_z", fallback.aircraftZ()),
                getBoolean(object, "use_simulated_third_person", fallback.useSimulatedThirdPerson()),
                getDouble(object, "simulated_third_person_distance", fallback.simulatedThirdPersonDistance()),
                getDouble(object, "simulated_third_person_height", fallback.simulatedThirdPersonHeight()),
                getString(object, "transform", fallback.transform())
        );
    }

    private static DismountInfo parseDismount(JsonObject object, DismountInfo fallback) {
        if (object == null) {
            return fallback;
        }
        return new DismountInfo(
                getDouble(object, "x", fallback.x()),
                getDouble(object, "y", fallback.y()),
                getDouble(object, "z", fallback.z())
        );
    }

    private static List<SeatInfo> parseSeats(JsonObject object, List<SeatInfo> fallback) {
        JsonElement element = object.get("seats");
        if (element == null || !element.isJsonArray()) {
            return fallback;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) {
            return fallback;
        }
        java.util.ArrayList<SeatInfo> seats = new java.util.ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            JsonObject seat = array.get(index).getAsJsonObject();
            SeatInfo fallbackSeat = index < fallback.size() ? fallback.get(index) : SeatInfo.DRIVER;
            seats.add(new SeatInfo(
                    getInt(seat, "index", index),
                    getDouble(seat, "x", fallbackSeat.x()),
                    getDouble(seat, "y", fallbackSeat.y()),
                    getDouble(seat, "z", fallbackSeat.z()),
                    getBoolean(seat, "driver", index == 0),
                    getBoolean(seat, "enclosed", fallbackSeat.enclosed()),
                    getBoolean(seat, "hide_passenger", fallbackSeat.hidePassenger()),
                    getBoolean(seat, "ban_hand", fallbackSeat.banHand()),
                    (float) getDouble(seat, "min_pitch", fallbackSeat.minPitch()),
                    (float) getDouble(seat, "max_pitch", fallbackSeat.maxPitch()),
                    (float) getDouble(seat, "min_yaw", fallbackSeat.minYaw()),
                    (float) getDouble(seat, "max_yaw", fallbackSeat.maxYaw()),
                    (float) getDouble(seat, "sensitivity_x", fallbackSeat.sensitivityX()),
                    (float) getDouble(seat, "sensitivity_y", fallbackSeat.sensitivityY()),
                    (float) getDouble(seat, "sensitivity_z", fallbackSeat.sensitivityZ()),
                    parseCamera(getObject(seat, "zoom_camera"), fallbackSeat.zoomCamera()),
                    parseDismount(getObject(seat, "dismount"), fallbackSeat.dismount())
            ));
        }
        seats.sort(Comparator.comparingInt(SeatInfo::index));
        return List.copyOf(seats);
    }

    /** The road wheels for the shared wheel driver ("wheels"), or null for a vehicle without them. */
    private static WheelInfo parseWheels(JsonObject object) {
        if (object == null) {
            return null;
        }
        List<TrackInfo.Wheel> roll = new java.util.ArrayList<>();
        if (object.has("roll")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(object, "roll")) {
                JsonObject group = element.getAsJsonObject();
                List<String> bones = new java.util.ArrayList<>();
                GsonHelper.getAsJsonArray(group, "bones").forEach(bone -> bones.add(bone.getAsString()));
                roll.add(new TrackInfo.Wheel(List.copyOf(bones), getFloat(group, "radius", 1.0F)));
            }
        }
        List<String> steer = new java.util.ArrayList<>();
        if (object.has("steer")) {
            GsonHelper.getAsJsonArray(object, "steer").forEach(bone -> steer.add(bone.getAsString()));
        }
        return new WheelInfo(List.copyOf(roll), List.copyOf(steer), getFloat(object, "wheelbase", 2.5F));
    }

    /** A fixed-wing aircraft's own flight settings ("aircraft"; AircraftInfo), or the A-10's when absent. */
    private static ttv.migami.jeg.vehicle.data.subdata.AircraftInfo parseAircraft(JsonObject object) {
        var d = ttv.migami.jeg.vehicle.data.subdata.AircraftInfo.DEFAULT;
        if (object == null) {
            return d;
        }
        List<double[]> points = new java.util.ArrayList<>();
        if (object.has("strike_points")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(object, "strike_points")) {
                JsonArray p = element.getAsJsonArray();
                points.add(new double[]{p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble(), p.get(3).getAsDouble()});
            }
        }
        return new ttv.migami.jeg.vehicle.data.subdata.AircraftInfo(
                getDouble(object, "thrust_lapse", d.thrustLapse()),
                getBoolean(object, "fixed_gear", d.fixedGear()),
                getDouble(object, "flap_stall_factor", d.flapStallFactor()),
                getDouble(object, "flap_speed_factor", d.flapSpeedFactor()),
                List.copyOf(points),
                object.has("wing_camera") ? parseTriple(GsonHelper.getAsJsonArray(object, "wing_camera")) : d.wingCamera());
    }

    private static double[] parseTriple(JsonArray array) {
        return new double[]{array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble()};
    }

    private static java.util.List<Integer> parseIntList(JsonObject object, String key) {
        java.util.List<Integer> values = new java.util.ArrayList<>();
        if (object.has(key) && object.get(key).isJsonArray()) {
            object.getAsJsonArray(key).forEach(e -> values.add(e.getAsInt()));
        }
        return values;
    }

    /** A land vehicle's swimming ("amphibious"; AmphibiousInfo), or NONE when absent. */
    private static ttv.migami.jeg.vehicle.data.subdata.AmphibiousInfo parseAmphibious(JsonObject object) {
        if (object == null) {
            return ttv.migami.jeg.vehicle.data.subdata.AmphibiousInfo.NONE;
        }
        java.util.List<Integer> noFire = new java.util.ArrayList<>();
        if (object.has("no_fire_slots") && object.get("no_fire_slots").isJsonArray()) {
            object.getAsJsonArray("no_fire_slots").forEach(e -> noFire.add(e.getAsInt()));
        }
        return new ttv.migami.jeg.vehicle.data.subdata.AmphibiousInfo(getDouble(object, "waterline", 1.0D),
                getDouble(object, "max_speed", 0.1D), java.util.List.copyOf(noFire));
    }

    /** A boat's hull behaviour ("boat"; BoatInfo), or the default hull when absent. */
    private static ttv.migami.jeg.vehicle.data.subdata.BoatInfo parseBoat(JsonObject object) {
        var d = ttv.migami.jeg.vehicle.data.subdata.BoatInfo.DEFAULT;
        if (object == null) {
            return d;
        }
        return new ttv.migami.jeg.vehicle.data.subdata.BoatInfo(
                getFloat(object, "collision_length_scale", d.collisionLengthScale()),
                getDouble(object, "collision_forward_shift", d.collisionForwardShift()),
                getDouble(object, "bow_probe_distance", d.bowProbeDistance()),
                getDouble(object, "bow_probe_half_width", d.bowProbeHalfWidth()),
                getDouble(object, "bow_probe_low_y", d.bowProbeLowY()),
                getDouble(object, "bow_probe_high_y", d.bowProbeHighY()),
                getDouble(object, "thrust", d.thrust()),
                getDouble(object, "plane_speed", d.planeSpeed()),
                getDouble(object, "displacement_friction", d.displacementFriction()),
                getDouble(object, "planed_friction", d.planedFriction()),
                getFloat(object, "bow_rise_degrees", d.bowRiseDegrees()),
                getDouble(object, "keel_grip", d.keelGrip()),
                getDouble(object, "beach_speed", d.beachSpeed()),
                getDouble(object, "beach_step", d.beachStep()),
                getDouble(object, "draft", d.draft()));
    }

    /** The running gear for the shared track driver ("tracks"), or null for a vehicle without one. */
    private static TrackInfo parseTracks(JsonObject object) {
        if (object == null) {
            return null;
        }
        JsonArray rest = GsonHelper.getAsJsonArray(object, "rest");
        JsonArray path = GsonHelper.getAsJsonArray(object, "path");
        float[] pathZ = new float[path.size()];
        float[] pathY = new float[path.size()];
        for (int i = 0; i < path.size(); i++) {
            JsonArray point = path.get(i).getAsJsonArray();
            pathZ[i] = point.get(0).getAsFloat();
            pathY[i] = point.get(1).getAsFloat();
        }
        List<TrackInfo.Wheel> wheels = new java.util.ArrayList<>();
        if (object.has("wheels")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(object, "wheels")) {
                JsonObject wheel = element.getAsJsonObject();
                List<String> bones = new java.util.ArrayList<>();
                GsonHelper.getAsJsonArray(wheel, "bones").forEach(bone -> bones.add(bone.getAsString()));
                wheels.add(new TrackInfo.Wheel(List.copyOf(bones), getFloat(wheel, "radius", 1.0F)));
            }
        }
        return TrackInfo.of(getInt(object, "links", 0), rest.get(0).getAsFloat(), rest.get(1).getAsFloat(),
                getFloat(object, "rest_s", 0.0F), getFloat(object, "half_width", 1.0F), pathZ, pathY, List.copyOf(wheels));
    }

    private static OBBInfo parseObb(JsonObject object, String key, OBBInfo fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            return fallback;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) {
            return fallback;
        }
        java.util.ArrayList<OBBInfo.Box> boxes = new java.util.ArrayList<>();
        for (JsonElement boxElement : array) {
            JsonObject box = boxElement.getAsJsonObject();
            OBBInfo.Part part = getEnum(box, "part", OBBInfo.Part.class, OBBInfo.Part.BODY);
            boxes.add(new OBBInfo.Box(
                    part,
                    getDouble(box, "x", 0.0D),
                    getDouble(box, "y", 0.75D),
                    getDouble(box, "z", 0.0D),
                    getDouble(box, "half_width", 0.5D),
                    getDouble(box, "half_height", 0.5D),
                    getDouble(box, "half_depth", 0.5D),
                    getEnum(box, "follows", OBBInfo.Follows.class, OBBInfo.Follows.HULL)
            ));
        }
        return new OBBInfo(List.copyOf(boxes));
    }

    private static List<VehicleWeaponInfo> parseWeapons(JsonObject object, List<VehicleWeaponInfo> fallback) {
        JsonElement element = object.get("weapons");
        if (element == null || !element.isJsonArray()) {
            return fallback;
        }
        JsonArray array = element.getAsJsonArray();
        if (array.isEmpty()) {
            // "weapons": [] is an unarmed vehicle (the bush plane, the truck, the bulldozer), not "use the defaults":
            // that gave them the default hand guns.
            return List.of();
        }
        java.util.ArrayList<VehicleWeaponInfo> weapons = new java.util.ArrayList<>();
        for (JsonElement weaponElement : array) {
            JsonObject weapon = weaponElement.getAsJsonObject();
            JsonObject muzzle = getObject(weapon, "muzzle");
            weapons.add(new VehicleWeaponInfo(
                    Identifier.parse(getString(weapon, "weapon", "jeg:assault_rifle")),
                    Identifier.parse(getString(weapon, "ammo", "jeg:rifle_ammo")),
                    getInt(weapon, "energy_cost", 0),
                    getBoolean(weapon, "guided", false),
                    getInt(weapon, "seat", getInt(weapon, "seat_index", -1)),
                    muzzle == null ? Double.NaN : getDouble(muzzle, "x", Double.NaN),
                    muzzle == null ? Double.NaN : getDouble(muzzle, "y", Double.NaN),
                    muzzle == null ? Double.NaN : getDouble(muzzle, "z", Double.NaN),
                    weapon.has("turret_mounted") ? Boolean.valueOf(getBoolean(weapon, "turret_mounted", true)) : null,
                    muzzle != null && getBoolean(muzzle, "mirror", false)
            ));
        }
        return List.copyOf(weapons);
    }

    private static TurretInfo parseTurret(JsonObject object, TurretInfo fallback) {
        if (object == null) {
            return fallback;
        }
        return new TurretInfo(
                getInt(object, "seat", fallback.seatIndex()),
                getDouble(object, "render_pivot_y", fallback.renderPivotY()),
                getDouble(object, "origin_x", fallback.originX()),
                getDouble(object, "origin_y", fallback.originY()),
                getDouble(object, "origin_z", fallback.originZ()),
                getDouble(object, "barrel_x", fallback.barrelX()),
                getDouble(object, "barrel_y", fallback.barrelY()),
                getDouble(object, "barrel_z", fallback.barrelZ()),
                getBoolean(object, "guided_uses_turret", fallback.guidedUsesTurret()),
                (float) getDouble(object, "traverse_rate", fallback.traverseRate()),
                (float) getDouble(object, "elevation_rate", fallback.elevationRate()),
                getInt(object, "fallback_seat", fallback.fallbackSeat())
        );
    }

    private static DestroyInfo parseDestroy(JsonObject object, DestroyInfo fallback) {
        if (object == null) {
            return fallback;
        }
        return new DestroyInfo(
                getBoolean(object, "explodes", fallback.explodes()),
                getFloat(object, "explosion_power", fallback.explosionPower())
        );
    }

    private static SeekInfo parseSeek(JsonObject object, SeekInfo fallback) {
        if (object == null) {
            return fallback;
        }
        return new SeekInfo(
                getDouble(object, "range", fallback.range()),
                getDouble(object, "angle", fallback.angle()),
                getBoolean(object, "warns_target", fallback.warnsTarget())
        );
    }

    private static VehicleArmorProfile parseArmor(JsonObject object, VehicleArmorProfile fallback) {
        if (object == null) {
            return fallback;
        }
        VehiclePartArmorProfile fallbackPart = parseArmorPart(getObject(object, "fallback"), fallback.fallback());
        EnumMap<OBBInfo.Part, VehiclePartArmorProfile> parts = new EnumMap<>(OBBInfo.Part.class);
        JsonObject partsObject = getObject(object, "parts");
        if (partsObject != null) {
            for (Map.Entry<String, JsonElement> entry : partsObject.entrySet()) {
                OBBInfo.Part part = parseEnumValue(entry.getKey(), OBBInfo.Part.class);
                parts.put(part, parseArmorPart(entry.getValue().getAsJsonObject(), fallbackPart));
            }
        }
        return new VehicleArmorProfile(fallbackPart, parts);
    }

    private static VehiclePartArmorProfile parseArmorPart(JsonObject object, VehiclePartArmorProfile fallback) {
        if (object == null) {
            return fallback;
        }
        return new VehiclePartArmorProfile(
                getFloat(object, "rating", fallback.rating()),
                getFloat(object, "undermatch_multiplier", fallback.undermatchMultiplier()),
                getFloat(object, "overmatch_multiplier", fallback.overmatchMultiplier()),
                getFloat(object, "passenger_leak_multiplier", fallback.passengerLeakMultiplier()),
                getFloat(object, "part_damage_multiplier", fallback.partDamageMultiplier())
        );
    }

    private static DamageModifierInfo parseDamageModifier(JsonObject object, DamageModifierInfo fallback) {
        if (object == null) {
            return fallback;
        }
        return new DamageModifierInfo(getFloat(object, "global_multiplier", fallback.globalMultiplier()));
    }

    private static JsonObject getObject(JsonObject object, String name) {
        JsonElement element = object.get(name);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String getString(JsonObject object, String name, String fallback) {
        JsonElement element = object.get(name);
        return element != null ? element.getAsString() : fallback;
    }

    private static boolean getBoolean(JsonObject object, String name, boolean fallback) {
        JsonElement element = object.get(name);
        return element != null ? element.getAsBoolean() : fallback;
    }

    private static int getInt(JsonObject object, String name, int fallback) {
        JsonElement element = object.get(name);
        return element != null ? element.getAsInt() : fallback;
    }

    private static float getFloat(JsonObject object, String name, float fallback) {
        JsonElement element = object.get(name);
        return element != null ? element.getAsFloat() : fallback;
    }

    private static double getDouble(JsonObject object, String name, double fallback) {
        JsonElement element = object.get(name);
        return element != null ? element.getAsDouble() : fallback;
    }

    private static Identifier getResourceLocation(JsonObject object, String name, Identifier fallback) {
        JsonElement element = object.get(name);
        return element != null ? Identifier.parse(element.getAsString()) : fallback;
    }

    private static <T extends Enum<T>> T getEnum(JsonObject object, String name, Class<T> type, T fallback) {
        JsonElement element = object.get(name);
        return element != null ? parseEnumValue(element.getAsString(), type) : fallback;
    }

    private static <T extends Enum<T>> T parseEnumValue(String value, Class<T> type) {
        try {
            return Enum.valueOf(type, value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new JsonSyntaxException("Unknown " + type.getSimpleName() + " value: " + value, exception);
        }
    }
}
