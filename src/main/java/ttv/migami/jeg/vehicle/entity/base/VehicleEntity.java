package ttv.migami.jeg.vehicle.entity.base;

import ttv.migami.jeg.Config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4d;
import org.joml.Vector4d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;
import ttv.migami.jeg.network.NetworkHandler;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.faction.GunnerFriendlyFireEvents;
import ttv.migami.jeg.gun.BallisticProtection;
import ttv.migami.jeg.gun.GunStats;
import ttv.migami.jeg.init.ModDamageTypes;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.init.ModParticleTypes;
import ttv.migami.jeg.init.ModSounds;
import ttv.migami.jeg.item.RepairToolItem;
import ttv.migami.jeg.particle.CannonMuzzleFlareOption;
import ttv.migami.jeg.vehicle.block.entity.VehicleContainerBlockEntity;
import ttv.migami.jeg.vehicle.ai.EnemyVehicleController;
import ttv.migami.jeg.vehicle.data.DefaultVehicleData;
import ttv.migami.jeg.vehicle.data.VehiclePartArmorProfile;
import ttv.migami.jeg.vehicle.data.VehicleData;
import ttv.migami.jeg.vehicle.data.VehicleDataManager;
import ttv.migami.jeg.vehicle.data.subdata.CameraPos;
import ttv.migami.jeg.vehicle.data.subdata.CollisionLevel;
import ttv.migami.jeg.vehicle.data.subdata.DismountInfo;
import ttv.migami.jeg.vehicle.data.subdata.EngineInfo;
import ttv.migami.jeg.vehicle.data.subdata.EngineType;
import ttv.migami.jeg.vehicle.data.subdata.OBBInfo;
import ttv.migami.jeg.vehicle.data.subdata.SeatInfo;
import ttv.migami.jeg.vehicle.data.subdata.SeekInfo;
import ttv.migami.jeg.vehicle.data.subdata.VehicleContainerType;
import ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo;
import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import ttv.migami.jeg.vehicle.menu.VehicleMenu;
import ttv.migami.jeg.vehicle.projectile.VehicleDecoyEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;
import ttv.migami.jeg.vehicle.util.VehicleSoundHelper;
import ttv.migami.jeg.vehicle.util.VehicleMissileProfile;
import ttv.migami.jeg.vehicle.util.VehicleWeaponStats;

public class VehicleEntity extends Entity implements MenuProvider, ExtendedMenuProvider<Integer>, GeoEntity {
    private static final double CLIENT_RESYNC_DISTANCE_SQR = 4.0D;
    private static final double CLIENT_RESYNC_HORIZONTAL_MOTION_DELTA_SQR = 0.16D;
    private static final float CLIENT_RESYNC_YAW_DELTA = 12.0F;
    private static final float CLIENT_RESYNC_PITCH_DELTA = 12.0F;
    /** Ticks of travel the predicting client may sit ahead of or behind the server along its flight path. */
    private static final double CLIENT_RESYNC_ALONG_TRACK_TICKS = 5.0D;
    private static final int MOVING_DRIVER_STATE_SYNC_INTERVAL = 2;
    private static final int UNMANNED_AIRBORNE_STATE_SYNC_INTERVAL = 1;
    private static final double VEHICLE_IDLE_FALL_SPEED_THRESHOLD = 1.0E-4D;
    private static final double VEHICLE_IDLE_SYNC_MOTION_THRESHOLD_SQR = 1.0E-4D;
    private static final double VEHICLE_IDLE_SYNC_Y_DELTA = 1.0E-3D;
    private static final float OTHER_VEHICLE_COLLISION_LENGTH_SCALE = 1.2F;
    /** Vehicle weapons reload in 0.64x the time of the same gun carried by hand. */
    private static final float VEHICLE_RELOAD_TIME_SCALE = 0.64F;
    /** Helicopter weapons reload another 30% faster on top of that (0.448x the hand-held time). */
    private static final float HELICOPTER_RELOAD_TIME_SCALE = 0.70F;
    private static final float SPEEDBOAT_COLLISION_LENGTH_SCALE = 1.2F;
    private static final double SPEEDBOAT_COLLISION_FORWARD_SHIFT = 0.15D;
    private static final double BOAT_WATER_PROBE_BELOW = 0.2D;
    private static final double BOAT_WATER_PROBE_ABOVE = 0.35D;
    private static final int BOAT_WATERBORNE_MEMORY_TICKS = 8;
    private static final double SPEEDBOAT_BOW_BLOCK_PROBE_DISTANCE = 0.35D;
    private static final double SPEEDBOAT_BOW_BLOCK_PROBE_HALF_WIDTH = 0.95D;
    private static final double SPEEDBOAT_BOW_BLOCK_PROBE_LOW_Y = 0.35D;
    private static final double SPEEDBOAT_BOW_BLOCK_PROBE_HIGH_Y = 0.9D;
    private static final double SPEEDBOAT_WATER_CRUISE_SPEED = 40.0D / 72.0D;
    private static final int DISMOUNT_LERP_SUPPRESSION_TICKS = 20;
    private static final int DISMOUNT_FOLLOWUP_SYNC_TICKS = 40;
    private static final double OBB_ENTITY_COLLISION_EPSILON = 1.0E-4D;
    private static final Set<String> RADAR_WARNING_VEHICLES = Set.of("lav150", "bmp2", "ah6", "mi28", "speedboat");

    private static final EntityDataAccessor<String> DATA_VEHICLE_ID = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ENERGY = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RIFLE_AMMO = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SELECTED_WEAPON = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_SELECTED_WEAPONS_BY_SEAT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_WEAPON_AMMO_BY_SLOT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DATA_WEAPON_RESERVE_AMMO_BY_SLOT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_SELECTED_WEAPON_AMMO = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SELECTED_WEAPON_RESERVE_AMMO = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SELECTED_WEAPON_RELOADING = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_SELECTED_WEAPON_RELOAD_TICKS = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RELOADING_WEAPON = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    /** Reload ticks left on every weapon slot, comma separated: each weapon reloads on its own. */
    private static final EntityDataAccessor<String> DATA_WEAPON_RELOAD_TICKS_BY_SLOT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_FLARE_AMMO = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DECOY_COOLDOWN = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_MISSILE_LOCKED = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_MISSILE_LOCK_TARGET = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_WARNING_MESSAGE = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_WARNING_UNTIL_TICK = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_LEFT_WHEEL_DAMAGED = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RIGHT_WHEEL_DAMAGED = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ENGINE_DAMAGED = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SUB_ENGINE_DAMAGED = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_TURRET_DAMAGED = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_TURRET_YAW = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_TURRET_PITCH = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PROPELLER_ROT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_PROPELLER_SPEED = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_HELICOPTER_STARTUP = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_ROLL = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_WEAPON_FIRING = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_ROTARY_GUN_SPIN = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_ROTARY_GUN_REQUESTED = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ACTIVE_FIRE_WEAPON = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    /** The dye the vehicle is painted with (DyeColor id), or -1 for its factory finish. */
    private static final EntityDataAccessor<Integer> DATA_PAINT = SynchedEntityData.defineId(VehicleEntity.class, EntityDataSerializers.INT);
    private static final Identifier RIFLE_AMMO = Reference.id("rifle_ammo");
    private static final Identifier FLARE_AMMO = Reference.id("flare");
    private static final int FLARE_DECOY_COOLDOWN_TICKS = 400;
    private static final int LAND_DECOY_COOLDOWN_TICKS = 500;
    private static final int FLARE_BURST_LAST_DELAY_TICKS = 48;
    private static final int FLARE_BURST_INTERVAL_TICKS = 6;
    private static final int REDSTONE_ENERGY_VALUE = 20;
    private static final int ENERGY_RECHARGE_INTERVAL = 20;
    private static final int LOW_HEALTH_DECAY_INTERVAL = 40;
    private static final int VEHICLE_WARNING_DISPLAY_TICKS = 60;
    private static final int VEHICLE_WARNING_SOUND_INTERVAL_TICKS = 10;
    private static final int RAM_DAMAGE_COOLDOWN_TICKS = 10;
    private static final String TAG_VEHICLE_ID = "VehicleDataId";
    private static final String TAG_HEALTH = "Health";
    private static final String TAG_ENERGY = "Energy";
    private static final String TAG_ITEMS = "Items";
    private static final String TAG_REPAIR_COOLDOWN = "RepairCooldown";
    private static final String TAG_SELECTED_WEAPON = "SelectedWeapon";
    private static final String TAG_SELECTED_WEAPONS = "SelectedWeapons";
    private static final String TAG_LOADED_WEAPON_AMMO = "LoadedWeaponAmmo";
    private static final String TAG_LOADED_WEAPON_SLOT = "WeaponSlot";
    private static final String TAG_LOADED_WEAPON_COUNT = "LoadedAmmo";
    private static final String TAG_DECOY_COOLDOWN = "DecoyCooldown";
    private static final String TAG_SEAT_ASSIGNMENTS = "SeatAssignments";
    private static final String TAG_SEAT_PASSENGER = "Passenger";
    private static final String TAG_SEAT_INDEX = "Seat";
    private static final String TAG_LEFT_WHEEL_HEALTH = "LeftWheelHealth";
    private static final String TAG_RIGHT_WHEEL_HEALTH = "RightWheelHealth";
    private static final String TAG_ENGINE_HEALTH = "EngineHealth";
    private static final String TAG_SUB_ENGINE_HEALTH = "SubEngineHealth";
    private static final String TAG_TURRET_HEALTH = "TurretHealth";
    private static final String TAG_PAINT = "Paint";
    private static final double TAIL_CAMERA_SIDE_OFFSET = 0.35D;
    private static final double TAIL_CAMERA_HEIGHT = 0.45D;
    private static final double TAIL_CAMERA_INSET = 0.5D;
    private static final float TAIL_CAMERA_DOWN_TILT = 7.0F;
    private static final String TAG_PERSISTENT_DATA = "PersistentData";
    private static final String GECKO_CONTROLLER = "Vehicle";
    private static final Map<String, String> VEHICLE_IDLE_ANIMATIONS = Map.of(
            "lav150", "animation.lav_150.idle",
            "speedboat", "animation.speedboat.idle",
            "laser_tower", "animation.lt.idle",
            "waveforce_tower", "animation.waveforce_tower.idle"
    );
    private static final float PART_MAX_HEALTH = 10.0F;
    private static final float REPAIR_KIT_HULL_REPAIR = 12.0F;
    private static final float REPAIR_KIT_PART_REPAIR = 5.0F;
    private static final float LOW_HEALTH_DECAY_THRESHOLD = 20.0F;
    private static final float LOW_HEALTH_DECAY_DAMAGE = 0.25F;
    private static final double RAM_DAMAGE_MIN_SPEED = 0.18D;
    private static final double VEHICLE_COLLISION_MIN_RELATIVE_SPEED = 0.25D;
    private static final int VEHICLE_IMPACT_DAMAGE_COOLDOWN_TICKS = 10;
    private static final double VEHICLE_HORIZONTAL_IMPACT_DAMAGE_THRESHOLD = 0.25D;
    private static final double VEHICLE_VERTICAL_IMPACT_DAMAGE_THRESHOLD = 0.45D;
    private static final double VEHICLE_HORIZONTAL_IMPACT_DAMAGE_LIGHT_END = 0.45D;
    private static final double VEHICLE_HORIZONTAL_IMPACT_LIGHT_DAMAGE_SCALE = 8.0D;
    private static final double VEHICLE_HORIZONTAL_IMPACT_HEAVY_DAMAGE_SCALE = 120.0D;
    private static final double VEHICLE_IMPACT_DAMAGE_MIN_MULTIPLIER = 0.25D;
    private static final double VEHICLE_IMPACT_MULTIPLIER_DROP_REFERENCE = 7.5D;
    private static final double VEHICLE_IMPACT_MULTIPLIER_HORIZONTAL_REFERENCE = 0.38D;
    private static final double VEHICLE_IMPACT_MULTIPLIER_VERTICAL_REFERENCE = 0.55D;
    private static final int LAND_VEHICLE_FALL_DAMAGE_AIRBORNE_TICKS = 14;
    private static final double LAND_VEHICLE_FALL_DAMAGE_MIN_VERTICAL_SPEED = 0.18D;
    private static final double VEHICLE_FALL_IMPACT_SAFE_DROP = 6.0D;
    private static final double VEHICLE_FALL_IMPACT_LIGHT_DROP_END = 10.0D;
    private static final double VEHICLE_FALL_IMPACT_LIGHT_DAMAGE_SCALE = 0.8D;
    private static final double VEHICLE_FALL_IMPACT_HEAVY_LINEAR_SCALE = 1.25D;
    private static final double VEHICLE_FALL_IMPACT_HEAVY_CURVE_SCALE = 0.5D;
    private static final double VEHICLE_FALL_IMPACT_SPEED_DAMAGE_SCALE = 7.5D;
    private static final double AIR_VEHICLE_CONTROLLED_FULL_DROP_SPEED = 1.2D;
    private static final double HELICOPTER_FATAL_IMPACT_SPEED = 1.2D;
    private static final double AIR_VEHICLE_UNCONTROLLED_IMPACT_DAMAGE_THRESHOLD = LAND_VEHICLE_FALL_DAMAGE_MIN_VERTICAL_SPEED;
    private static final double AIR_VEHICLE_UNCONTROLLED_FULL_DROP_SPEED = 0.35D;
    private static final double HELICOPTER_SAFE_DESCENT_SPEED = 0.35D;
    private static final double HELICOPTER_MAX_DESCENT_SPEED = 1.05D;
    private static final double HELICOPTER_FORCED_DESCENT_ACCELERATION = 0.035D * 0.70D;
    private static final double HELICOPTER_VERTICAL_IMPACT_MAX_DAMAGE_RATIO = 0.70D;
    private static final float HELICOPTER_DESCENT_ROTOR_SPEED = 0.035F;
    /** Rotor speed (hover is 0.085) above which a landed helicopter is still hot and restarts without a cold start. */
    private static final float HELICOPTER_HOT_RESTART_ROTOR_SPEED = 0.02F;
    /** Height above ground (blocks) past which a helicopter counts as airborne for a hot restart, not merely bouncing on its skids. */
    private static final double HELICOPTER_HOT_RESTART_ALTITUDE = 1.0D;
    private static final double HELICOPTER_DANGEROUS_DESCENT_WARNING_MIN_ALTITUDE = 8.0D;
    // Below this horizontal speed a fast descent is dangerous; with forward airspeed it is a normal descent.
    private static final double HELICOPTER_LOW_AIRSPEED_THRESHOLD = 0.2D;
    private static final double HELICOPTER_FORCED_DESCENT_WARNING_MIN_ALTITUDE = 3.0D;
    private static final double HELICOPTER_FORCED_DESCENT_WARNING_SINK_SPEED = 0.08D;
    // Tracked vehicles: minimum turn rate (degrees per tick per unit of steering), about 40 degrees a second on the spot.
    private static final double TRACK_PIVOT_TURN_RATE = 6.0D;
    private static final double TRACK_PIVOT_POWER_THRESHOLD = 0.05D;
    // Player pilots fly on demand: holding ascend/descend sets a vertical speed, releasing holds altitude.
    private static final double HELICOPTER_CLIMB_SPEED = 0.3D;
    private static final double HELICOPTER_COMMANDED_DESCENT_SPEED = 0.3D;
    private static final double HELICOPTER_VERTICAL_RESPONSE = 0.2D;
    // Landing assist: below this height a commanded descent slows down so the skids touch down gently.
    private static final double HELICOPTER_LANDING_ASSIST_ALTITUDE = 8.0D;
    private static final double HELICOPTER_TOUCHDOWN_SPEED = 0.05D;
    private static final double HELICOPTER_HOVER_POWER = 0.085D;
    private static final double HELICOPTER_CLIMB_POWER = 0.105D;
    private static final double HELICOPTER_DESCENT_POWER = 0.06D;
    private static final double HELICOPTER_POWER_RESPONSE = 0.004D;
    private static final double HELICOPTER_PLAYER_TURN_RESPONSE = 1.75D;
    // Player flight: WASD set a target velocity relative to the heading; no input settles back to a hover.
    private static final double HELICOPTER_STRAFE_SPEED_RATIO = 0.6D;
    private static final double HELICOPTER_PLAYER_SPEED_MULTIPLIER = 2.0D;
    private static final double HELICOPTER_ACCELERATION_RESPONSE = 0.06D;
    private static final double HELICOPTER_BRAKING_RESPONSE = 0.1D;
    /** How far the pilot can pitch the nose up to aim. */
    private static final float HELICOPTER_MAX_AIM_UP = 25.0F;
    /** How far the pilot can pitch the nose down to aim: deep enough to fire on targets close beneath the helicopter. */
    private static final float HELICOPTER_MAX_AIM_DOWN = 45.0F;
    /** The hull and cockpit camera never pitch further nose down than this (full aim plus forward tilt fits under it). */
    private static final float HELICOPTER_MAX_NOSE_DOWN = 60.0F;
    private static final float HELICOPTER_MAX_FORWARD_TILT = 15.0F;
    private static final float HELICOPTER_MAX_BANK = 30.0F;
    private static final float HELICOPTER_TURN_BANK = 1.5F;
    private static final float HELICOPTER_ATTITUDE_RESPONSE = 0.2F;
    // Weighted sway: a soft, lightly damped spring (~1.8 s period) that sideways acceleration kicks the opposite way.
    private static final float HELICOPTER_ROLL_SPRING = 0.03F;
    private static final float HELICOPTER_ROLL_DAMPING = 0.1F;
    private static final float HELICOPTER_SWAY_INERTIA = 0.7F;
    private static final float HELICOPTER_STRAFE_BANK = 0.6F;
    private static final float HELICOPTER_MAX_SWAY = 35.0F;
    private static final float HELICOPTER_DANGEROUS_DESCENT_WARNING_VOLUME = 14.0F;
    // Weighty flight (Config: vehicle.helicopter.weightyFlight). The stick tilts the hull, the tilted rotor is what pushes
    // the helicopter along, and drag sets the top speed, so speed builds and bleeds off with momentum. Each helicopter's
    // engine.player_weight slows the tilt, speed and climb response (1 = light scout).
    private static final float WEIGHTY_MAX_PITCH = 20.0F;
    // Letting go flares the nose up against the remaining speed; S while still moving forward is a hard flare.
    private static final float WEIGHTY_FLARE_PITCH = 16.0F;
    private static final float WEIGHTY_HARD_FLARE_PITCH = 28.0F;
    private static final float WEIGHTY_LEVEL_ROLL = 7.0F;
    // Lightly damped, so the hull rocks past level after a flare or a hard start before it settles.
    private static final double WEIGHTY_TILT_SPRING = 0.07D;
    private static final double WEIGHTY_TILT_DAMPING = 0.26D;
    // Ticks for a light helicopter to reach ~63% of a new speed once fully tilted.
    private static final double WEIGHTY_SPEED_TIME_TICKS = 40.0D;
    // How much of the tilt's push actually works against the current motion. The hull leans back as far as it looks right,
    // but a few tonnes at speed do not stop for a lean: hands off it mostly coasts, and even hard on the brake takes seconds.
    private static final double WEIGHTY_COAST_BRAKE_SHARE = 0.12D;
    private static final double WEIGHTY_HARD_BRAKE_SHARE = 0.45D;
    private static final double WEIGHTY_STRAFE_BRAKE_SHARE = 0.55D;
    // Near a standstill with nothing commanded the rotor wash is what settles it, so it comes to a real hover at the end.
    private static final double WEIGHTY_HOVER_SETTLE_SPEED = 0.15D;
    private static final double WEIGHTY_HOVER_SETTLE = 0.96D;
    // The nose takes a moment to start swinging and keeps going for a moment after the mouse stops.
    private static final float WEIGHTY_YAW_RESPONSE = 0.3F;
    // Max share of the gap between the travel direction and the nose closed per tick at speed (scaled by turnGrip).
    private static final double WEIGHTY_MAX_TURN_GRIP = 0.06D;
    // Climb/descent: a speed target chased through a lagging rotor, so climbs carry on, stops dip and the hull bobs.
    private static final double WEIGHTY_VERTICAL_GAIN = 0.10D;
    private static final double WEIGHTY_LIFT_RESPONSE = 0.12D;
    // Sink in blocks per tick at full tilt and tiltSink = 1.
    private static final double WEIGHTY_MAX_TILT_SINK = 0.08D;
    // Climb in blocks per tick a full-speed flare trades the forward momentum for (per unit of nose-up tilt).
    private static final double WEIGHTY_FLARE_BALLOON = 0.15D;
    // Gravity the rotor holds up, in blocks per tick squared. The rotor can give up to 60% more than hover, is only
    // trimmed for tilts up to 35 degrees, and is unloaded to a quarter while the helicopter is upside down.
    private static final double WEIGHTY_GRAVITY = 0.035D;
    private static final double WEIGHTY_MAX_EXTRA_LIFT = 0.5D;
    private static final double WEIGHTY_TRIM_LIMIT_COS = Math.cos(Math.toRadians(35.0D));
    private static final double WEIGHTY_INVERTED_THRUST = 0.25D;
    // Pilot-driven rolls (double-tap and hold A/D): roll acceleration and top roll rate in degrees per tick, divided by
    // weight. Past an ordinary bank the hull is the pilot's: let go and the spin bleeds off while the rotor slowly rights
    // it the short way round.
    private static final float WEIGHTY_ROLL_ACCELERATION = 2.5F;
    private static final float WEIGHTY_MAX_ROLL_RATE = 18.0F;
    private static final float WEIGHTY_FREE_ROLL_ANGLE = 45.0F;
    private static final float WEIGHTY_ROLL_SPIN_DAMPING = 0.22F;
    private static final float WEIGHTY_ROLL_RIGHTING = 0.006F;
    private static final float WEIGHTY_AIM_RESPONSE = 0.35F;
    // What the flare shows. The thrust tilt can pull far back to brake, but the hull only leans back gently towards this
    // many degrees and takes its time getting there, so stopping does not throw it (and everything riding it) backwards.
    private static final float WEIGHTY_HULL_MAX_NOSE_UP = 10.0F;
    private static final float WEIGHTY_HULL_TILT_RESPONSE = 0.14F;
    // The camera and the pilot's aim take this share of the hull's tilt, eased in slowly on top of the mouse aim.
    private static final float WEIGHTY_CAMERA_TILT_SHARE = 0.2F;
    private static final float WEIGHTY_CAMERA_TILT_RESPONSE = 0.08F;
    private static final float HELICOPTER_CAMERA_FOLLOW_RESPONSE = 0.2F;
    private static final float WEIGHTY_VISUAL_ROLL_SPRING = 0.06F;
    private static final float WEIGHTY_VISUAL_ROLL_DAMPING = 0.34F;
    private static final float WEIGHTY_SWAY_INERTIA = 0.22F;
    // A strafe shows more lean than the thrust that drives it, so the hull visibly hauls itself over; speed is unaffected.
    private static final float WEIGHTY_STRAFE_BANK_GAIN = 2.2F;
    // Stays under the sway limit, where the camera would start turning with the hull.
    private static final float WEIGHTY_MAX_BANK = 34.0F;
    private static final double LAND_VEHICLE_BODY_IMPACT_MIN_SPEED = 0.22D;
    private static final double LAND_VEHICLE_BODY_IMPACT_MAX_MOVED_RATIO = 0.75D;
    private static final double LAND_VEHICLE_BODY_IMPACT_PROBE_DISTANCE = 0.65D;
    private static final double AI_HELICOPTER_SPAWN_ENGINE_POWER = 0.095D;
    private static final double AI_HELICOPTER_SPAWN_UPWARD_SPEED = 0.16D;
    private static final float AI_HELICOPTER_SPAWN_ROTOR_SPEED = 0.09F;
    private static final double VEHICLE_ENTITY_COLLISION_SEARCH_EXPANSION = 0.45D;
    private static final double VEHICLE_ENTITY_COLLISION_DAMPING = 0.45D;
    private static final double VEHICLE_EXPLOSION_KNOCKBACK_REFERENCE_MASS = 16.0D;
    private static final double VEHICLE_EXPLOSION_KNOCKBACK_MIN_SCALE = 0.04D;
    private static final double VEHICLE_EXPLOSION_KNOCKBACK_MAX_SCALE = 0.22D;
    private static final float VEHICLE_COLLISION_SELF_DAMAGE_MULTIPLIER = 0.65F;
    /** The vehicle's own bumps and hard landings only reach open-seat passengers once they cost this share of its health. */
    private static final float SELF_STRIKE_PASSENGER_LEAK_MIN_RATIO = 0.25F;
    // The ceiling sits this far above the top of the world, so nothing that can be built or generated is out of reach.
    private static final double HELICOPTER_CEILING_ABOVE_WORLD = 64.0D;
    private static final double HELICOPTER_ALTITUDE_SOFT_ZONE = 12.0D;
    private static final int AIM_POINT_MAX_AGE_TICKS = 5;
    private static final double AIM_POINT_MIN_DISTANCE_SQR = 16.0D;
    private static final double AIM_POINT_MAX_OFF_VIEW_COS = Math.cos(Math.toRadians(20.0D));
    private static final float HELICOPTER_GUN_DAMAGE_MULTIPLIER = 1.8F;
    private static final double HELICOPTER_GUN_DISPERSION_DEGREES = 0.4D;
    // Enough to see the ground coming for the landing assist and descent warnings; further away it no longer matters.
    private static final double HELICOPTER_GROUND_PROBE_DISTANCE = 64.0D;
    private static final double AIRCRAFT_IDLE_DESCENT_ACCELERATION = 0.03D;
    private static final double AIRCRAFT_FORCED_DESCENT_ACCELERATION = HELICOPTER_FORCED_DESCENT_ACCELERATION;
    private static final double AIRCRAFT_MAX_DESCENT_SPEED = HELICOPTER_MAX_DESCENT_SPEED;
    private static final float HELICOPTER_ROTOR_GROUND_DAMAGE = 1.0F;
    private static final float HELICOPTER_ROTOR_DAMAGE_MIN_SPEED = 0.02F;
    private static final double HELICOPTER_ROTOR_CONTACT_SAMPLE_RADIUS = 0.55D;
    /**
     * The blades are thin: a sample only reaches this far above and below the rotor plane. The full sample radius used to
     * count a wall top half a block under the blades as a strike, chipping helicopters lifting off beside buildings.
     */
    private static final double HELICOPTER_ROTOR_CONTACT_SAMPLE_HALF_HEIGHT = 0.2D;
    private static final double DEFAULT_SEEK_RANGE = 64.0D;
    private static final double DEFAULT_SEEK_MIN_DOT = 0.985D;
    private static final double GRAVITY = 0.08D;

    private final SimpleContainer inventory = new SimpleContainer(VehicleMenu.MAX_VEHICLE_SLOT_COUNT);
    private final AnimatableInstanceCache geckoCache = GeckoLibUtil.createInstanceCache(this);
    private final CompoundTag persistentData = new CompoundTag();
    private final Map<UUID, Integer> seatAssignments = new HashMap<>();
    private final Map<UUID, Integer> recentDismountSeatAssignments = new HashMap<>();
    private final Set<UUID> preservedSeatAssignments = new HashSet<>();
    private final Map<Integer, Integer> loadedAmmoByWeaponSlot = new HashMap<>();
    private final Map<Integer, Integer> selectedWeaponSlotBySeat = new HashMap<>();
    private final Map<Integer, AiWeaponControl> aiWeaponControlBySeat = new HashMap<>();
    private final Map<Integer, Integer> aiFireCooldownByWeaponSlot = new HashMap<>();
    private VehicleInput input = VehicleInput.EMPTY;
    private int repairCooldown;
    /** Fire delay left on each weapon slot for the crew: switching weapons never waits out the last one's delay. */
    private final Map<Integer, Integer> fireCooldownByWeaponSlot = new HashMap<>();
    /** Reload ticks left on each weapon slot. A weapon keeps reloading while another one is selected and fired. */
    private final Map<Integer, Integer> reloadTicksByWeaponSlot = new HashMap<>();
    private int decoyCooldown;
    private int energyRechargeTick;
    private int ramDamageCooldown;
    private int unsupportedVehicleTicks;
    private int lastVehicleImpactSoundTick = Integer.MIN_VALUE;
    private double airborneStartY = Double.NaN;
    private double airborneMaxY = Double.NaN;
    private int weaponControllerId = -1;
    private boolean weaponFireInput;
    private float rotaryGunAngle;
    private float rotaryGunAngleO;

    public static final int ROTARY_GUN_SPOOL_TICKS = 30;
    public static final int ROTARY_GUN_STOP_TICKS = 45;
    private int seekControllerId = -1;
    private boolean seekInput;
    private int pendingFlareBurstTicks = -1;
    /** Shots fired by twin (mirrored) guns, to alternate barrels; and whether the shot being fired uses the mirror one. */
    private int twinGunShots;
    private boolean mirroredShot;
    private double wheelSteering;
    private double enginePower;
    private double lastTickSpeed;
    private double lastTickHorizontalSpeed;
    private double lastTickVerticalSpeed;
    private String lastVehicleWarningSoundKey = "";
    private int lastVehicleWarningSoundTick = Integer.MIN_VALUE;
    private int boatWaterborneTicks;
    private float turretYawO;
    private float turretPitchO;
    private float propellerRotO;
    private float rollO;
    // Roll predicted locally for the pilot; the synced DATA_ROLL lags behind it and would jitter the chase camera.
    private float predictedRoll;
    private boolean hasPredictedRoll;
    private int holdTick;
    private int holdPowerTick;
    private boolean engineStart;
    private boolean engineStartOver;
    private Vec3 preExplosionKnockbackVelocity;
    private int preExplosionKnockbackTick = -1;
    private float destroyRot;
    private float pilotAimPitch;
    private int dismountLerpSuppressionTicks;
    @Nullable
    private SyncedStateInterpolation syncedStateInterpolation;
    private int lastSyncedStateTick = -1000;
    private UUID recentDismountSyncPlayerId;
    private int recentDismountSyncTicks;
    private float leftWheelHealth = PART_MAX_HEALTH;
    private float rightWheelHealth = PART_MAX_HEALTH;
    private float engineHealth = PART_MAX_HEALTH;
    private float subEngineHealth = PART_MAX_HEALTH;
    private float rollVelocity;
    private double swayStrafeSpeed;
    private float heliTiltPitch;
    private float heliTiltPitchVelocity;
    private float heliTiltRoll;
    private float heliTiltRollVelocity;
    private double heliLift;
    // Hull tilt as shown (the thrust tilt with the flare softened), the mouse aim as shown, and the camera's share of the tilt.
    private final Map<UUID, AimPoint> passengerAimPoints = new HashMap<>();
    private float heliYawRate;
    private float heliShownTilt;
    private float heliShownAim;
    private float heliCameraTilt;
    private boolean heliCameraFromTilt;
    private float heliCameraPitch;
    private float heliCameraPitchO;
    // True while the pilot is rolling the hull by hand or it is still past an ordinary bank.
    private boolean manualRolling;
    private float turretHealth = PART_MAX_HEALTH;

    private record AimPoint(Vec3 point, int tick) {}

    private record BlockCollisionBounds(double centerX, double minY, double maxY, double centerZ, double halfWidth, double halfLength) {}

    private record LocalBounds(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {}

    private record ObbCollisionCorrection(Vec3 movement, double depth) {}

    private record AiWeaponControl(int entityId, boolean fire, boolean seek) {}

    public VehicleEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public CompoundTag getPersistentData() {
        return this.persistentData;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(
                GECKO_CONTROLLER,
                0,
                state -> {
                    state.setAndContinue(RawAnimation.begin().thenLoop(this.idleAnimationName()));
                    return PlayState.CONTINUE;
                }
        ));
    }

    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        if (this.usesHullBlockCollisionBox()) {
            // Helicopter part boxes include the tail boom and stub wings, so their rotated envelope is several times
            // the airframe; keep the block/pick box at the hull-sized entity dimensions instead.
            return super.makeBoundingBox(position);
        }
        AABB envelope = this.obbEnvelopeBox(position);
        if (envelope != null) {
            return envelope;
        }
        double scale = this.collisionLengthScale();
        double offsetZ = this.collisionCenterOffsetZ();
        if (Math.abs(scale - 1.0D) < 1.0E-6D && Math.abs(offsetZ) < 1.0E-6D) {
            return super.makeBoundingBox(position);
        }
        var dimensions = super.getDimensions(this.getPose());
        double halfWidth = dimensions.width() * 0.5D;
        double halfLength = halfWidth * scale;
        double yaw = Math.toRadians(this.getYRot());
        double absSin = Math.abs(Math.sin(yaw));
        double absCos = Math.abs(Math.cos(yaw));
        double extentX = halfWidth * absCos + halfLength * absSin;
        double extentZ = halfWidth * absSin + halfLength * absCos;
        Vec3 offset = this.rotateLocalOffset(0.0D, 0.0D, offsetZ, 1.0F);
        double centerX = position.x + offset.x;
        double centerZ = position.z + offset.z;
        return new AABB(centerX - extentX, position.y, centerZ - extentZ, centerX + extentX, position.y + dimensions.height(), centerZ + extentZ);
    }

    @Nullable
    private AABB obbEnvelopeBox(Vec3 position) {
        BlockCollisionBounds bounds = this.blockCollisionBounds();
        if (bounds == null) {
            return null;
        }
        double yaw = Math.toRadians(this.getYRot());
        double absSin = Math.abs(Math.sin(yaw));
        double absCos = Math.abs(Math.cos(yaw));
        double extentX = bounds.halfWidth() * absCos + bounds.halfLength() * absSin;
        double extentZ = bounds.halfWidth() * absSin + bounds.halfLength() * absCos;
        Vec3 offset = this.rotateLocalOffset(bounds.centerX(), 0.0D, bounds.centerZ(), 1.0F);
        double centerX = position.x + offset.x;
        double centerZ = position.z + offset.z;
        return new AABB(centerX - extentX, position.y + bounds.minY(), centerZ - extentZ, centerX + extentX, position.y + bounds.maxY(), centerZ + extentZ);
    }

    /** Box covering every part, for entity-collision searches and render culling. */
    public AABB partBounds() {
        AABB envelope = this.obbEnvelopeBox(this.position());
        return envelope == null ? this.getBoundingBox() : this.getBoundingBox().minmax(envelope);
    }

    private boolean usesHullBlockCollisionBox() {
        return this.isAh6Vehicle() || this.isMi28Vehicle();
    }

    @Override
    public void refreshDimensions() {
        super.refreshDimensions();
        this.updateVehicleBoundingBox();
    }

    private void updateVehicleBoundingBox() {
        this.setBoundingBox(this.makeBoundingBox(this.position()));
    }

    private boolean setVehicleYawIfUnblocked(float yaw) {
        if (!this.usesObbBlockCollisionBounds()) {
            this.setYRot(yaw);
            this.updateVehicleBoundingBox();
            return true;
        }
        float previousYaw = this.getYRot();
        AABB previousBox = this.getBoundingBox();
        boolean wasBlocked = this.intersectsBlockCollision(previousBox);
        this.setYRot(yaw);
        this.updateVehicleBoundingBox();
        if (!wasBlocked && this.intersectsBlockCollision(this.getBoundingBox())) {
            this.setYRot(previousYaw);
            this.setBoundingBox(previousBox);
            return false;
        }
        return true;
    }

    public float turnAiVehicleYawToward(float desiredYaw, float maxStep) {
        float yawDiff = Mth.wrapDegrees(desiredYaw - this.getYRot());
        if (Math.abs(yawDiff) < 0.001F) {
            return 0.0F;
        }
        float step = Mth.clamp(yawDiff, -Math.abs(maxStep), Math.abs(maxStep));
        if (this.setVehicleYawIfUnblocked(this.getYRot() + step)) {
            return Mth.wrapDegrees(desiredYaw - this.getYRot());
        }
        return yawDiff;
    }

    private float collisionLengthScale() {
        if (this.isSpeedboatVehicle()) {
            return SPEEDBOAT_COLLISION_LENGTH_SCALE;
        }
        return OTHER_VEHICLE_COLLISION_LENGTH_SCALE;
    }

    @Nullable
    private BlockCollisionBounds blockCollisionBounds() {
        if (!this.usesObbBlockCollisionBounds()) {
            return null;
        }
        var boxes = this.vehicleData().defaults().obb().boxes();
        if (boxes.isEmpty()) {
            return null;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (OBBInfo.Box box : boxes) {
            minX = Math.min(minX, box.x() - box.halfWidth());
            maxX = Math.max(maxX, box.x() + box.halfWidth());
            minY = Math.min(minY, box.y() - box.halfHeight());
            maxY = Math.max(maxY, box.y() + box.halfHeight());
            minZ = Math.min(minZ, box.z() - box.halfDepth());
            maxZ = Math.max(maxZ, box.z() + box.halfDepth());
        }
        if (this.usesVehiclePoseTransform()) {
            // Aircraft hitboxes cover the fuselage but not the skids or landing gear, which reach down to the
            // entity origin; without this the vehicle settles until the fuselage box touches the ground.
            minY = Math.min(minY, 0.0D);
        }
        return new BlockCollisionBounds(
                (minX + maxX) * 0.5D,
                minY,
                maxY,
                (minZ + maxZ) * 0.5D,
                Math.max((maxX - minX) * 0.5D, this.getBbWidth() * 0.5D),
                Math.max((maxZ - minZ) * 0.5D, this.getBbWidth() * 0.5D)
        );
    }

    private boolean usesObbBlockCollisionBounds() {
        return !OBBInfo.DEFAULT.equals(this.vehicleData().defaults().obb());
    }

    private boolean usesCustomObbEntityCollision() {
        return switch (this.vehicleDataId().getPath()) {
            case "lav150", "bmp2", "speedboat", "ah6", "mi28", "truck" -> true;
            default -> false;
        };
    }

    private String idleAnimationName() {
        String path = this.vehicleDataId().getPath();
        return VEHICLE_IDLE_ANIMATIONS.getOrDefault(path, "idle");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geckoCache;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_VEHICLE_ID, DefaultVehicleData.TEST_WHEEL.id().toString());
        builder.define(DATA_HEALTH, DefaultVehicleData.TEST_WHEEL.maxHealth());
        builder.define(DATA_ENERGY, DefaultVehicleData.TEST_WHEEL.maxEnergy());
        builder.define(DATA_RIFLE_AMMO, 0);
        builder.define(DATA_SELECTED_WEAPON, 0);
        builder.define(DATA_SELECTED_WEAPONS_BY_SEAT, "");
        builder.define(DATA_WEAPON_AMMO_BY_SLOT, "");
        builder.define(DATA_WEAPON_RESERVE_AMMO_BY_SLOT, "");
        builder.define(DATA_SELECTED_WEAPON_AMMO, 0);
        builder.define(DATA_SELECTED_WEAPON_RESERVE_AMMO, 0);
        builder.define(DATA_SELECTED_WEAPON_RELOADING, false);
        builder.define(DATA_SELECTED_WEAPON_RELOAD_TICKS, 0);
        builder.define(DATA_RELOADING_WEAPON, -1);
        builder.define(DATA_WEAPON_RELOAD_TICKS_BY_SLOT, "");
        builder.define(DATA_FLARE_AMMO, 0);
        builder.define(DATA_DECOY_COOLDOWN, 0);
        builder.define(DATA_MISSILE_LOCKED, false);
        builder.define(DATA_MISSILE_LOCK_TARGET, -1);
        builder.define(DATA_WARNING_MESSAGE, "");
        builder.define(DATA_WARNING_UNTIL_TICK, 0);
        builder.define(DATA_LEFT_WHEEL_DAMAGED, false);
        builder.define(DATA_RIGHT_WHEEL_DAMAGED, false);
        builder.define(DATA_ENGINE_DAMAGED, false);
        builder.define(DATA_SUB_ENGINE_DAMAGED, false);
        builder.define(DATA_TURRET_DAMAGED, false);
        builder.define(DATA_TURRET_YAW, 0.0F);
        builder.define(DATA_TURRET_PITCH, 0.0F);
        builder.define(DATA_PROPELLER_ROT, 0.0F);
        builder.define(DATA_PROPELLER_SPEED, 0.0F);
        builder.define(DATA_HELICOPTER_STARTUP, 0);
        builder.define(DATA_ROLL, 0.0F);
        builder.define(DATA_WEAPON_FIRING, false);
        builder.define(DATA_ROTARY_GUN_SPIN, 0.0F);
        builder.define(DATA_ROTARY_GUN_REQUESTED, false);
        builder.define(DATA_ACTIVE_FIRE_WEAPON, -1);
        builder.define(DATA_PAINT, -1);
    }

    /** The dye this vehicle has been painted with, or null for its original colours. */
    @Nullable
    public net.minecraft.world.item.DyeColor paint() {
        int id = this.entityData.get(DATA_PAINT);
        return id < 0 ? null : net.minecraft.world.item.DyeColor.byId(id);
    }

    public void setPaint(@Nullable net.minecraft.world.item.DyeColor color) {
        this.entityData.set(DATA_PAINT, color == null ? -1 : color.getId());
    }

    public VehicleData vehicleData() {
        return VehicleDataManager.get(Identifier.parse(this.entityData.get(DATA_VEHICLE_ID)));
    }

    public Identifier vehicleDataId() {
        return this.vehicleData().id();
    }

    protected void setVehicleData(Identifier id) {
        VehicleData data = VehicleDataManager.get(id);
        this.entityData.set(DATA_VEHICLE_ID, data.id().toString());
        this.entityData.set(DATA_HEALTH, data.defaults().maxHealth());
        this.entityData.set(DATA_ENERGY, data.defaults().maxEnergy());
        this.refreshDimensions();
    }

    public float vehicleHealth() {
        return this.entityData.get(DATA_HEALTH);
    }

    public float maxVehicleHealth() {
        return this.vehicleData().defaults().maxHealth();
    }

    public boolean repairWithTool(float hullAmount, float partAmount) {
        if (this.level().isClientSide() || this.isRemoved() || hullAmount <= 0.0F) {
            return false;
        }
        boolean repaired = false;
        if (this.vehicleHealth() < this.maxVehicleHealth()) {
            this.entityData.set(DATA_HEALTH, Math.min(this.maxVehicleHealth(), this.vehicleHealth() + hullAmount));
            repaired = true;
        }
        if (partAmount > 0.0F) {
            repaired |= this.repairParts(partAmount, true);
        }
        if (repaired) {
            this.repairCooldown = 0;
            this.resetRecoveredLowHealthState();
            this.hurtMarked = true;
        }
        return repaired;
    }

    public boolean hurtWithRepairTool(DamageSource source, float amount) {
        return this.hurtVehicleIgnoringArmor(source, amount);
    }

    public int vehicleEnergy() {
        return this.entityData.get(DATA_ENERGY);
    }

    public int maxVehicleEnergy() {
        return this.vehicleData().defaults().maxEnergy();
    }

    public boolean consumeEnergy(int amount) {
        if (amount <= 0) {
            return true;
        }
        int current = this.vehicleEnergy();
        if (current < amount) {
            return false;
        }
        this.entityData.set(DATA_ENERGY, current - amount);
        return true;
    }

    public boolean addEnergy(int amount) {
        if (amount <= 0 || this.maxVehicleEnergy() <= 0) {
            return false;
        }
        int current = this.vehicleEnergy();
        if (current >= this.maxVehicleEnergy()) {
            return false;
        }
        this.entityData.set(DATA_ENERGY, Math.min(this.maxVehicleEnergy(), current + amount));
        return true;
    }

    public void setAiVehicleInput(VehicleInput input) {
        if (!this.level().isClientSide()) {
            this.input = input == null ? VehicleInput.EMPTY : input;
        }
    }

    public void setAiWeaponControl(LivingEntity controller, boolean fire, boolean seek) {
        if (this.level().isClientSide()) {
            return;
        }
        if (controller != null && controller.getVehicle() == this) {
            this.weaponControllerId = controller.getId();
            this.seekControllerId = controller.getId();
            this.weaponFireInput = fire;
            this.seekInput = seek;
        } else {
            this.weaponControllerId = -1;
            this.seekControllerId = -1;
            this.weaponFireInput = false;
            this.seekInput = false;
            this.aiWeaponControlBySeat.clear();
            this.entityData.set(DATA_WEAPON_FIRING, false);
        }
    }

    public void setAiWeaponControlForSeat(int seatIndex, LivingEntity controller, boolean fire, boolean seek) {
        if (this.level().isClientSide()) {
            return;
        }
        if (controller != null && controller.getVehicle() == this && this.getSeatIndex(controller) == seatIndex) {
            this.aiWeaponControlBySeat.put(seatIndex, new AiWeaponControl(controller.getId(), fire, seek));
        } else {
            this.aiWeaponControlBySeat.remove(seatIndex);
        }
    }

    public void setAiTurretAim(float yaw, float pitch) {
        if (this.level().isClientSide()) {
            return;
        }
        this.entityData.set(DATA_TURRET_YAW, Mth.wrapDegrees(yaw));
        this.entityData.set(DATA_TURRET_PITCH, pitch);
    }

    public boolean selectAiWeaponForSeat(int seatIndex, int slot) {
        if (this.level().isClientSide() || !this.hasVehicleWeapons()) {
            return false;
        }
        var weapons = this.vehicleData().defaults().weapons();
        if (seatIndex < 0 || slot < 0 || slot >= weapons.size() || !weapons.get(slot).usableBySeat(seatIndex)) {
            return false;
        }
        if (slot == this.selectedVehicleWeaponIndexForSeat(seatIndex)) {
            this.entityData.set(DATA_SELECTED_WEAPON, slot);
            this.syncSelectedWeaponAmmoState();
            return true;
        }
        this.entityData.set(DATA_SELECTED_WEAPON, slot);
        this.setSelectedWeaponSlotForSeat(seatIndex, slot);
        this.syncSelectedWeaponAmmoState();
        return true;
    }

    public void addAmmoForAi(Identifier ammoId, int count) {
        if (this.level().isClientSide() || count <= 0) {
            return;
        }
        Item ammo = this.resolveAmmoItem(ammoId);
        if (ammo == null) {
            return;
        }
        int remaining = count;
        while (remaining > 0) {
            int toInsert = Math.min(remaining, ammo.getDefaultMaxStackSize());
            ItemStack stack = new ItemStack(ammo, toInsert);
            ItemStack leftover = this.inventory.addItem(stack);
            remaining -= toInsert - leftover.getCount();
            if (!leftover.isEmpty()) {
                break;
            }
        }
        this.inventory.setChanged();
        this.syncSelectedWeaponAmmoState();
    }

    public void reloadAiVehicleWeapons() {
        if (this.level().isClientSide() || !this.hasVehicleWeapons()) {
            return;
        }
        var weapons = this.vehicleData().defaults().weapons();
        for (int slot = 0; slot < weapons.size(); slot++) {
            if (!this.usesSharedVehicleReloadSystem(slot)) {
                continue;
            }
            VehicleWeaponInfo weapon = weapons.get(slot);
            GunStats stats = VehicleWeaponStats.get(weapon.weaponId());
            int magazineSize = stats == null ? 0 : Math.max(0, stats.magazineSize());
            int loaded = this.weaponLoadedAmmo(slot);
            int reserve = this.countAmmo(weapon.ammoId());
            int transfer = Math.min(Math.max(0, magazineSize - loaded), reserve);
            if (transfer > 0 && this.consumeAmmo(weapon.ammoId(), transfer)) {
                this.setWeaponLoadedAmmo(slot, loaded + transfer);
            }
        }
        this.syncSelectedWeaponAmmoState();
    }

    public Vec3 aiWeaponMuzzlePosition(int weaponSlot) {
        var weapons = this.vehicleData().defaults().weapons();
        if (weaponSlot < 0 || weaponSlot >= weapons.size()) {
            return this.position().add(0.0D, 1.0D, 0.0D);
        }
        VehicleWeaponInfo weapon = weapons.get(weaponSlot);
        Vec3 articulatedMuzzle = this.articulatedWeaponMuzzlePosition(weapon);
        if (articulatedMuzzle != null) {
            return articulatedMuzzle;
        }
        return this.weaponMuzzlePosition(weapon, this.horizontalDirection(this.getYRot()), 1.25D, 0.95D);
    }
    public int vehicleRifleAmmo() {
        return this.entityData.get(DATA_RIFLE_AMMO);
    }

    public int selectedVehicleWeaponAmmo() {
        return this.hasVehicleWeapons() ? this.entityData.get(DATA_SELECTED_WEAPON_AMMO) : 0;
    }

    public int selectedVehicleWeaponAmmo(Entity passenger) {
        int slot = this.selectedVehicleWeaponIndex(passenger);
        return slot < 0 ? 0 : this.weaponAmmoFromSyncedData(slot);
    }

    public int selectedVehicleWeaponReserveAmmo() {
        return this.hasVehicleWeapons() ? this.entityData.get(DATA_SELECTED_WEAPON_RESERVE_AMMO) : 0;
    }

    public int selectedVehicleWeaponReserveAmmo(Entity passenger) {
        int slot = this.selectedVehicleWeaponIndex(passenger);
        return slot < 0 ? 0 : this.weaponReserveAmmoFromSyncedData(slot);
    }

    public boolean selectedVehicleWeaponReloading() {
        return this.hasVehicleWeapons() && this.entityData.get(DATA_SELECTED_WEAPON_RELOADING);
    }

    public boolean selectedVehicleWeaponReloading(Entity passenger) {
        return this.selectedVehicleWeaponReloadTicks(passenger) > 0;
    }

    public int selectedVehicleWeaponReloadTicks() {
        return this.hasVehicleWeapons() ? this.entityData.get(DATA_SELECTED_WEAPON_RELOAD_TICKS) : 0;
    }

    public int selectedVehicleWeaponReloadTicks(Entity passenger) {
        return this.vehicleWeaponSlotReloadTicks(this.selectedVehicleWeaponIndex(passenger));
    }

    /** Rounds loaded in this weapon slot, as last synced. */
    public int vehicleWeaponSlotAmmo(int slot) {
        return slot < 0 ? 0 : this.weaponAmmoFromSyncedData(slot);
    }

    /** Reload ticks left on this weapon slot, as last synced: 0 when it is not reloading. */
    public int vehicleWeaponSlotReloadTicks(int slot) {
        return slot < 0 ? 0 : this.valueFromSyncedIntList(this.entityData.get(DATA_WEAPON_RELOAD_TICKS_BY_SLOT), slot, 0);
    }

    public int vehicleFlareAmmo() {
        if (this.hasBuiltInDecoy()) {
            return this.decoyCooldown == 0 ? 1 : 0;
        }
        return this.entityData.get(DATA_FLARE_AMMO);
    }

    public int vehicleDecoyCooldown() {
        return this.entityData.get(DATA_DECOY_COOLDOWN);
    }

    @Nullable
    public Identifier selectedVehicleWeaponId() {
        VehicleWeaponInfo weapon = this.selectedWeapon();
        return weapon == null ? null : weapon.weaponId();
    }

    @Nullable
    public Identifier selectedVehicleWeaponId(Entity passenger) {
        VehicleWeaponInfo weapon = this.selectedWeapon(passenger);
        return weapon == null ? null : weapon.weaponId();
    }

    public int selectedVehicleWeaponIndex() {
        var weapons = this.vehicleData().defaults().weapons();
        if (!this.hasVehicleWeapons() || weapons.isEmpty()) {
            return -1;
        }
        return Mth.clamp(this.entityData.get(DATA_SELECTED_WEAPON), 0, weapons.size() - 1);
    }

    public int selectedVehicleWeaponIndex(Entity passenger) {
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        if (fallbackIndex < 0) {
            return this.selectedVehicleWeaponIndex();
        }
        return this.selectedVehicleWeaponIndexForSeat(this.seatIndexForPassenger(passenger, fallbackIndex));
    }

    public boolean isSelectedVehicleWeaponGuided() {
        VehicleWeaponInfo weapon = this.selectedWeapon();
        return weapon != null && weapon.guided();
    }

    public boolean isSelectedVehicleWeaponGuided(Entity passenger) {
        VehicleWeaponInfo weapon = this.selectedWeapon(passenger);
        return weapon != null && weapon.guided();
    }

    public boolean isSelectedVehicleWeaponLockOn() {
        VehicleWeaponInfo weapon = this.selectedWeapon();
        return weapon != null && weapon.guided() && VehicleMissileProfile.get(weapon.weaponId()).usesLockOn();
    }

    public boolean isSelectedVehicleWeaponLockOn(Entity passenger) {
        VehicleWeaponInfo weapon = this.selectedWeapon(passenger);
        return weapon != null && weapon.guided() && VehicleMissileProfile.get(weapon.weaponId()).usesLockOn();
    }

    public boolean canPassengerUseVehicleWeapon(Entity passenger, int weaponIndex) {
        var weapons = this.vehicleData().defaults().weapons();
        if (weaponIndex < 0 || weaponIndex >= weapons.size()) {
            return false;
        }
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        return fallbackIndex >= 0 && weapons.get(weaponIndex).usableBySeat(this.seatIndexForPassenger(passenger, fallbackIndex));
    }

    public boolean canPassengerUseSelectedVehicleWeapon(Entity passenger) {
        return this.canPassengerUseVehicleWeapon(passenger, this.selectedVehicleWeaponIndex(passenger));
    }

    public int vehicleWeaponIndexForDisplaySlot(Entity passenger, int displaySlot) {
        if (displaySlot < 0) {
            return -1;
        }
        int usableSlot = 0;
        var weapons = this.vehicleData().defaults().weapons();
        for (int index = 0; index < weapons.size(); index++) {
            if (this.canPassengerUseVehicleWeapon(passenger, index)) {
                if (usableSlot == displaySlot) {
                    return index;
                }
                usableSlot++;
            }
        }
        return -1;
    }

    public boolean hasMissileLock() {
        return this.entityData.get(DATA_MISSILE_LOCKED);
    }

    public String activeWarningMessageKey() {
        String key = this.entityData.get(DATA_WARNING_MESSAGE);
        if (key.isEmpty() || this.tickCount > this.entityData.get(DATA_WARNING_UNTIL_TICK)) {
            return "";
        }
        return key;
    }

    public boolean isLeftWheelDamaged() {
        return this.entityData.get(DATA_LEFT_WHEEL_DAMAGED);
    }

    public boolean isRightWheelDamaged() {
        return this.entityData.get(DATA_RIGHT_WHEEL_DAMAGED);
    }

    public boolean isEngineDamaged() {
        return this.entityData.get(DATA_ENGINE_DAMAGED);
    }

    public boolean isSubEngineDamaged() {
        return this.entityData.get(DATA_SUB_ENGINE_DAMAGED);
    }

    public boolean isTurretDamaged() {
        return this.entityData.get(DATA_TURRET_DAMAGED);
    }

    public float turretYaw() {
        return this.entityData.get(DATA_TURRET_YAW);
    }

    public float turretPitch() {
        return this.entityData.get(DATA_TURRET_PITCH);
    }

    public float turretYaw(float partialTick) {
        return Mth.rotLerp(partialTick, this.turretYawO, this.turretYaw());
    }

    public float turretPitch(float partialTick) {
        return Mth.lerp(partialTick, this.turretPitchO, this.turretPitch());
    }

    public float propellerRot() {
        return this.entityData.get(DATA_PROPELLER_ROT);
    }

    public float propellerRot(float partialTick) {
        return Mth.lerp(partialTick, this.propellerRotO, this.propellerRot());
    }

    public float propellerSpeed() {
        return this.entityData.get(DATA_PROPELLER_SPEED);
    }

    /** Cold start lasts eight seconds on the AH-6 and twelve on the heavier MI-28. */
    public int helicopterStartupTicks() {
        return "mi28".equals(this.vehicleDataId().getPath()) ? 240 : 160;
    }

    public float helicopterStartupProgress() {
        return Mth.clamp(this.entityData.get(DATA_HELICOPTER_STARTUP) / (float) this.helicopterStartupTicks(), 0.0F, 1.0F);
    }

    public void primeAiHelicopterSpawnHover() {
        if (this.vehicleData().defaults().vehicleType() != VehicleType.HELICOPTER) {
            return;
        }
        this.engineStart = true;
        this.engineStartOver = true;
        this.entityData.set(DATA_HELICOPTER_STARTUP, this.helicopterStartupTicks());
        this.enginePower = Math.max(this.enginePower, AI_HELICOPTER_SPAWN_ENGINE_POWER);
        this.entityData.set(DATA_PROPELLER_SPEED, Math.max(this.propellerSpeed(), AI_HELICOPTER_SPAWN_ROTOR_SPEED));
        Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(motion.x, Math.max(motion.y, AI_HELICOPTER_SPAWN_UPWARD_SPEED), motion.z);
        this.hurtMarked = true;
        this.needsSync = true;
    }

    public float roll() {
        if (this.hasPredictedRoll && this.level().isClientSide() && this.shouldRunClientPrediction()) {
            return this.predictedRoll;
        }
        return this.entityData.get(DATA_ROLL);
    }

    public float roll(float partialTick) {
        return Mth.rotLerp(partialTick, this.rollO, this.roll());
    }

    /** Helicopter sway belongs to the hull: cameras keep the horizon level and do not swing sideways with the bank. */
    public boolean keepsCameraLevel() {
        return this.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER;
    }

    public float cameraRoll(float partialTick) {
        float roll = this.roll(partialTick);
        if (!this.keepsCameraLevel()) {
            return roll;
        }
        // Ordinary sway leaves the horizon level; past it (a barrel roll) the view blends in and turns with the hull.
        float blend = Mth.clamp((Math.abs(Mth.wrapDegrees(roll)) - HELICOPTER_MAX_SWAY) / 55.0F, 0.0F, 1.0F);
        return Mth.wrapDegrees(roll) * blend * blend * (3.0F - 2.0F * blend);
    }

    public boolean isWeaponFiring() {
        return this.entityData.get(DATA_WEAPON_FIRING);
    }

    public float rotaryGunSpin() {
        return this.entityData.get(DATA_ROTARY_GUN_SPIN);
    }

    public boolean rotaryGunRequested() {
        return this.entityData.get(DATA_ROTARY_GUN_REQUESTED);
    }

    public float rotaryGunAngle(float partialTick) {
        return Mth.lerp(partialTick, this.rotaryGunAngleO, this.rotaryGunAngle);
    }

    public int helicopterShutdownTicks() {
        return "mi28".equals(this.vehicleDataId().getPath()) ? 360 : 240;
    }

    @Nullable
    public SoundEvent activeVehicleFireSound() {
        VehicleWeaponInfo weapon = this.activeFireWeapon();
        if (weapon == null) {
            return null;
        }
        GunStats stats = VehicleWeaponStats.get(weapon.weaponId());
        return stats == null ? null : VehicleSoundHelper.fireSound(this, weapon, stats);
    }

    @Nullable
    public SoundEvent activeVehicleFireSound(Entity passenger) {
        VehicleWeaponInfo weapon = this.selectedWeapon(passenger);
        Identifier weaponId = weapon == null ? null : weapon.weaponId();
        if (weapon == null || weaponId == null) {
            return null;
        }
        GunStats stats = VehicleWeaponStats.get(weaponId);
        return stats == null ? null : VehicleSoundHelper.fireSound(this, weapon, stats);
    }

    @Nullable
    private VehicleWeaponInfo activeFireWeapon() {
        var weapons = this.vehicleData().defaults().weapons();
        int activeSlot = this.entityData.get(DATA_ACTIVE_FIRE_WEAPON);
        if (activeSlot >= 0 && activeSlot < weapons.size()) {
            return weapons.get(activeSlot);
        }
        return null;
    }

    private void setRoll(float roll) {
        float wrapped = Mth.wrapDegrees(roll);
        this.entityData.set(DATA_ROLL, wrapped);
        if (this.level().isClientSide()) {
            this.predictedRoll = wrapped;
            this.hasPredictedRoll = true;
        }
    }

    public boolean hasBuiltInDecoy() {
        return this.vehicleData().defaults().hasDecoy();
    }

    @Override
    public float maxUpStep() {
        return this.vehicleData().defaults().upStep();
    }

    public boolean isFreeLookInputDown() {
        return this.vehicleData().defaults().allowFreeCam() && this.input.freeLook();
    }

    public SimpleContainer vehicleInventory() {
        return this.inventory;
    }

    public boolean hasVehicleWeapons() {
        return !this.isTruckVehicle() && !this.vehicleData().defaults().weapons().isEmpty();
    }

    public boolean isTruckVehicle() {
        return "truck".equals(this.vehicleDataId().getPath());
    }

    public boolean isSpeedboatVehicle() {
        return "speedboat".equals(this.vehicleDataId().getPath());
    }

    public boolean isAh6Vehicle() {
        return "ah6".equals(this.vehicleDataId().getPath());
    }

    public boolean isMi28Vehicle() {
        return "mi28".equals(this.vehicleDataId().getPath());
    }

    public double collisionCenterOffsetZ() {
        if (this.isSpeedboatVehicle()) {
            return SPEEDBOAT_COLLISION_FORWARD_SHIFT;
        }
        return 0.0D;
    }

    public boolean hasFocusedDriverSightHud() {
        if (!this.hasVehicleWeapons()) {
            return false;
        }
        var seats = this.vehicleData().defaults().seats();
        if (seats.isEmpty()) {
            return false;
        }
        SeatInfo seat = seats.getFirst();
        return seat.driver() && this.usesArticulatedSeatTransform(seat);
    }

    public boolean hasFocusedSightHud(Entity passenger) {
        if (!(passenger instanceof LivingEntity living) || passenger.getVehicle() != this || !this.hasVehicleWeapons()) {
            return false;
        }
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        if (fallbackIndex < 0) {
            return false;
        }
        int seatIndex = this.seatIndexForPassenger(passenger, fallbackIndex);
        SeatInfo seat = this.seatForPassenger(seatIndex);
        if (!this.hasWeaponUsableBySeat(seatIndex)) {
            return false;
        }
        return this.usesArticulatedSeatTransform(seat)
                || this.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER;
    }

    public boolean guidedWeaponsUseArticulatedTurret() {
        return this.vehicleData().defaults().turret().guidedUsesTurret();
    }

    public void processInput(ServerPlayer player, VehicleInput input) {
        if (player.getVehicle() != this) {
            return;
        }
        if (this.hasVehicleWeapons()) {
            if (input.switchWeapon() && this.selectWeaponFor(player, 1)) {
                this.weaponControllerId = player.getId();
            }
            if (input.previousWeapon() && this.selectWeaponFor(player, -1)) {
                this.weaponControllerId = player.getId();
            }
            if (input.weaponSlot() >= 0 && input.weaponSlot() < this.vehicleData().defaults().weapons().size() && this.selectWeaponSlot(player, input.weaponSlot())) {
                this.weaponControllerId = player.getId();
            }
        }
        this.selectFallbackWeaponFor(player, input);
        VehicleWeaponInfo selectedWeapon = this.selectedWeapon(player);
        boolean canUseSelectedWeapon = selectedWeapon != null && this.canUseSelectedWeapon(player, selectedWeapon);
        if (selectedWeapon != null && !this.isFreeLookInput(input) && canUseSelectedWeapon && this.controlsTurret(player)) {
            this.setTurretAimFromView(player, this.getYRot(), player.getYRot(), player.getXRot(), 1.0F);
        }
        if (input.reload() && canUseSelectedWeapon) {
            this.startWeaponReload(player);
        }
        if (input.deployDecoy()) {
            this.tryDeployDecoy(player);
        }
        // Only armed seats drive the vehicle trigger; a free-hand passenger firing their own gun must not take it over.
        boolean armedSeat = this.hasWeaponUsableBySeat(this.seatIndexForPassenger(player, this.getPassengers().indexOf(player)));
        if (armedSeat && input.seekTarget()) {
            this.seekControllerId = player.getId();
            this.seekInput = true;
        } else if (this.seekControllerId == player.getId()) {
            this.seekInput = false;
        }
        if (armedSeat && input.fire()) {
            this.weaponControllerId = player.getId();
            this.weaponFireInput = true;
        } else if (this.weaponControllerId == player.getId()) {
            this.weaponFireInput = false;
        }
        if (player == this.getControllingPassenger()) {
            this.input = input;
        }
    }

    public void processClientInput(Player player, VehicleInput input) {
        if (!this.level().isClientSide() || player.getVehicle() != this) {
            return;
        }
        if (this.hasVehicleWeapons()) {
            if (input.switchWeapon()) {
                this.selectWeaponFor(player, 1);
            }
            if (input.previousWeapon()) {
                this.selectWeaponFor(player, -1);
            }
            if (input.weaponSlot() >= 0 && input.weaponSlot() < this.vehicleData().defaults().weapons().size()) {
                this.selectWeaponSlot(player, input.weaponSlot());
            }
        }
        this.selectFallbackWeaponFor(player, input);
        VehicleWeaponInfo selectedWeapon = this.selectedWeapon(player);
        if (selectedWeapon != null && !this.isFreeLookInput(input) && this.canUseSelectedWeapon(player, selectedWeapon) && this.controlsTurret(player)) {
            this.setTurretAimFromView(player, this.getYRot(), player.getYRot(), player.getXRot(), 1.0F);
        }
        if (player == this.getControllingPassenger()) {
            this.input = input;
        }
    }

    public void refreshClientTurretAim(Entity passenger) {
        this.refreshClientTurretAim(passenger, 1.0F);
    }

    public void refreshClientTurretAim(Entity passenger, float partialTick) {
        if (!this.level().isClientSide()
                || !(passenger instanceof LivingEntity living)
                || passenger.getVehicle() != this
                || !this.hasVehicleWeapons()
                || clientFreeLookDown()) {
            return;
        }
        VehicleWeaponInfo selectedWeapon = this.selectedWeapon(living);
        if (selectedWeapon != null && this.canUseSelectedWeapon(living, selectedWeapon) && this.controlsTurret(living)) {
            // Aim from the frame-interpolated aircraft heading and view so the turret camera does not step once per tick.
            this.setTurretAimFromView(living, Mth.rotLerp(partialTick, this.yRotO, this.getYRot()),
                    living.getViewYRot(partialTick), living.getViewXRot(partialTick), partialTick);
            if (living == localClientPlayer()) {
                // The local gunner re-aims every frame; interpolating from the tick-start (server-synced) value made the
                // turret camera swing between the server and predicted aim every tick while the aircraft turned.
                this.turretYawO = this.turretYaw();
                this.turretPitchO = this.turretPitch();
            }
        }
    }

    private boolean isFreeLookInput(VehicleInput input) {
        return this.vehicleData().defaults().allowFreeCam() && input.freeLook();
    }

    /**
     * Points the turret along the gunner's view. On aircraft the turret rides the hull's pitch and roll, so the view is
     * first turned into hull space; otherwise a nose-down or banking helicopter swung the gun off the crosshair.
     */
    private void setTurretAimFromView(LivingEntity player, float vehicleYaw, float viewYaw, float viewPitch, float partialTick) {
        float turretYaw;
        float turretPitch;
        if (this.usesVehiclePoseTransform()) {
            Vec3 look = Vec3.directionFromRotation(viewPitch, viewYaw);
            Vector4d local = this.vehiclePoseRotation(partialTick).invert(new Matrix4d())
                    .transform(new Vector4d(look.x, look.y, look.z, 0.0D));
            turretYaw = (float) Math.toDegrees(Math.atan2(local.x, local.z));
            turretPitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(local.y, -1.0D, 1.0D)));
        } else {
            turretYaw = -Mth.wrapDegrees(viewYaw - vehicleYaw);
            turretPitch = viewPitch;
        }
        int fallbackIndex = this.getPassengers().indexOf(player);
        if (fallbackIndex >= 0) {
            SeatInfo seat = this.seatForPassenger(player, fallbackIndex);
            // Seat limits are relative to the hull, like the client's view clamp.
            turretYaw = -Mth.clamp(-turretYaw, seat.minYaw(), seat.maxYaw());
            turretPitch = Mth.clamp(turretPitch, seat.minPitch(), seat.maxPitch());
        }
        this.entityData.set(DATA_TURRET_YAW, Mth.wrapDegrees(turretYaw));
        this.entityData.set(DATA_TURRET_PITCH, turretPitch);
    }

    /** With a turret, only its seat aims it; other armed seats (the Mi-28 pilot's rockets) overwrote the gunner's aim. */
    private boolean controlsTurret(LivingEntity player) {
        var turret = this.vehicleData().defaults().turret();
        return !turret.enabled() || this.seatIndexForPassenger(player, this.getPassengers().indexOf(player)) == turret.seatIndex();
    }

    private boolean shouldAlignDriverView(int seatIndex) {
        VehicleType type = this.vehicleData().defaults().vehicleType();
        if ((type != VehicleType.HELICOPTER && type != VehicleType.AIRCRAFT)
                || seatIndex < 0
                || seatIndex >= this.vehicleData().defaults().seats().size()) {
            return false;
        }
        return this.vehicleData().defaults().seats().get(seatIndex).driver();
    }

    private void alignPassengerViewToVehicle(Entity passenger, int seatIndex) {
        if (!(passenger instanceof LivingEntity living) || !this.shouldAlignDriverView(seatIndex)) {
            return;
        }
        float yaw = this.getYRot();
        float pitch = this.cameraPitch(1.0F);
        living.setYRot(yaw);
        living.yRotO = yaw;
        living.setYHeadRot(yaw);
        living.yHeadRotO = yaw;
        living.yBodyRot = yaw;
        living.yBodyRotO = yaw;
        living.setXRot(pitch);
        living.xRotO = pitch;
        if (this.level().isClientSide() && living == localClientPlayer()) {
            syncClientMousePosition();
        }
    }

    public void changeSeat(ServerPlayer player) {
        if (player.getVehicle() != this) {
            return;
        }
        int seatCount = this.vehicleData().defaults().seats().size();
        if (seatCount < 2) {
            return;
        }
        int fallbackIndex = this.getPassengers().indexOf(player);
        int currentSeat = this.seatIndexForPassenger(player, fallbackIndex);
        for (int offset = 1; offset < seatCount; offset++) {
            int nextSeat = (currentSeat + offset) % seatCount;
            if (!this.isSeatOccupied(nextSeat, player)) {
                this.seatAssignments.put(player.getUUID(), nextSeat);
                this.input = VehicleInput.EMPTY;
                this.alignPassengerViewToVehicle(player, nextSeat);
                this.syncSeatAssignments();
                return;
            }
        }
    }

    @Override
    public void tick() {
        this.rotaryGunAngleO = this.rotaryGunAngle;
        this.rotaryGunAngle += this.rotaryGunSpin() * this.rotaryGunSpin() * 54.0F;
        if (this.rotaryGunAngle >= 360.0F) {
            this.rotaryGunAngle -= 360.0F;
            this.rotaryGunAngleO -= 360.0F;
        }
        this.turretYawO = this.turretYaw();
        this.turretPitchO = this.turretPitch();
        this.propellerRotO = this.propellerRot();
        if (this.level().isClientSide() && !this.shouldRunClientPrediction()) {
            this.hasPredictedRoll = false;
        }
        this.rollO = this.roll();
        SyncedStateInterpolation interpolation = this.syncedStateInterpolation();
        if (interpolation != null) {
            interpolation.interpolate();
        }
        this.updateLastTickMovementSpeed();
        super.tick();
        if (this.level().isClientSide() && this.dismountLerpSuppressionTicks > 0) {
            this.dismountLerpSuppressionTicks--;
        }
        this.applyPassengerYaw();
        if (!this.level().isClientSide()) {
            this.clearStaleDriverInput();
            this.tickServerMovement();
            this.tickHelicopterRotorGroundDamage();
            this.tickRammingDamage();
            this.tickVehicleEntityCollisionResolution();
            this.tickMissileLock();
            this.tickWeaponReload();
            this.tickServerWeapon();
            this.tickDecoyCooldown();
            this.tickPendingFlareDecoys();
            this.tickInventoryEnergyRecharge();
            this.tickAutoRepair();
            this.tickRecentDismountStateSync();
            this.tickDriverStateSync();
            this.entityData.set(DATA_RIFLE_AMMO, this.countRifleAmmo());
            this.syncSelectedWeaponAmmoState();
            this.entityData.set(DATA_FLARE_AMMO, this.countAmmo(FLARE_AMMO));
            this.entityData.set(DATA_DECOY_COOLDOWN, this.decoyCooldown);
        } else if (this.shouldRunClientPrediction()) {
            VehicleType type = this.vehicleData().defaults().vehicleType();
            if (type == VehicleType.BOAT) {
                this.tickClientPredictedBoatMovement();
            } else if (type == VehicleType.HELICOPTER) {
                this.tickClientPredictedHelicopterMovement();
            } else {
                this.tickClientPredictedLandMovement();
            }
        }
        if (this.keepsCameraLevel()) {
            this.tickHelicopterCameraPitch();
        }
        this.tickObbEntityCollisionSupport();
    }

    @Override
    public void move(MoverType type, Vec3 pos) {
        boolean wasVerticallySupported = this.onGround() || this.verticalCollisionBelow;
        int unsupportedTicksBeforeMove = this.unsupportedVehicleTicks;
        Vec3 before = this.position();
        Vec3 velocityBeforeMove = this.getDeltaMovement();
        super.move(type, pos);
        Vec3 actualMovement = this.position().subtract(before);
        actualMovement = this.stopAtVehicleEntityImpact(before, pos, actualMovement, velocityBeforeMove);
        this.applyVehicleImpactDamage(pos, actualMovement, wasVerticallySupported, unsupportedTicksBeforeMove);
        if (!this.onGround() && !this.verticalCollisionBelow && unsupportedTicksBeforeMove == 0 && Double.isNaN(this.airborneStartY)) {
            this.airborneStartY = before.y();
            this.airborneMaxY = Math.max(before.y(), this.getY());
        }
        this.updateVehicleSupportTicks();
    }

    private void resetAirborneImpactTracking() {
        this.unsupportedVehicleTicks = 0;
        this.airborneStartY = Double.NaN;
        this.airborneMaxY = Double.NaN;
    }

    private void updateLastTickMovementSpeed() {
        Vec3 movement = this.getDeltaMovement();
        this.lastTickHorizontalSpeed = new Vec3(movement.x, 0.0D, movement.z).length();
        this.lastTickVerticalSpeed = movement.y + 0.06D;
        this.lastTickSpeed = new Vec3(movement.x, this.lastTickVerticalSpeed, movement.z).length();
    }

    private void applyVehicleImpactDamage(Vec3 requestedMovement, Vec3 actualMovement, boolean wasVerticallySupported, int unsupportedTicksBeforeMove) {
        if (this.level().isClientSide()
                || this.vehicleData().defaults().collisionLevel() == CollisionLevel.NONE
                || this.ramDamageCooldown > 0) {
            return;
        }
        VehicleType vehicleType = this.vehicleData().defaults().vehicleType();
        if (vehicleType == VehicleType.HELICOPTER || vehicleType == VehicleType.AIRCRAFT) {
            this.applyAirVehicleImpactDamage(vehicleType, requestedMovement, wasVerticallySupported);
            return;
        }
        boolean landVehicle = vehicleType == VehicleType.LAND;
        boolean supportedAfterMove = this.onGround() || this.verticalCollisionBelow;
        int fallAirborneTicks = landVehicle ? LAND_VEHICLE_FALL_DAMAGE_AIRBORNE_TICKS : 8;
        double trackedFallDistance = this.currentAirborneDropDistance();
        boolean hadTrackedFall = unsupportedTicksBeforeMove >= fallAirborneTicks || trackedFallDistance > VEHICLE_FALL_IMPACT_SAFE_DROP;
        boolean downwardImpact = requestedMovement.y < -LAND_VEHICLE_FALL_DAMAGE_MIN_VERTICAL_SPEED
                || this.verticalCollision && requestedMovement.y < actualMovement.y - LAND_VEHICLE_FALL_DAMAGE_MIN_VERTICAL_SPEED;
        boolean landedAfterFall = hadTrackedFall
                && supportedAfterMove
                && downwardImpact;
        double fallDistance = landedAfterFall ? trackedFallDistance : 0.0D;
        Vec3 requestedHorizontal = new Vec3(requestedMovement.x(), 0.0D, requestedMovement.z());
        boolean landBodyImpact = landVehicle && this.isLandVehicleBodyImpact(requestedMovement, actualMovement);
        double horizontalImpactSpeed = this.horizontalCollision
                ? requestedHorizontal.subtract(actualMovement.x(), 0.0D, actualMovement.z()).horizontalDistance()
                : 0.0D;
        if (landBodyImpact) {
            horizontalImpactSpeed = Math.max(horizontalImpactSpeed, requestedHorizontal.length());
        }
        if (landVehicle && unsupportedTicksBeforeMove > 0 && !landBodyImpact) {
            horizontalImpactSpeed = 0.0D;
        }
        double verticalImpactSpeed = this.verticalCollision && !wasVerticallySupported ? Math.abs(requestedMovement.y() - actualMovement.y()) : 0.0D;
        if (landedAfterFall) {
            double fallDurationBonus = Math.max(0, unsupportedTicksBeforeMove - fallAirborneTicks) * 0.015D;
            verticalImpactSpeed = Math.max(verticalImpactSpeed, Math.abs(requestedMovement.y) + fallDurationBonus);
        }
        if (landVehicle && (unsupportedTicksBeforeMove < LAND_VEHICLE_FALL_DAMAGE_AIRBORNE_TICKS || fallDistance <= VEHICLE_FALL_IMPACT_SAFE_DROP) && !landedAfterFall) {
            verticalImpactSpeed = 0.0D;
        }
        float damage = this.vehicleImpactDamage(horizontalImpactSpeed, verticalImpactSpeed, fallDistance);
        if (damage <= 0.0F) {
            return;
        }
        DamageSource source = this.vehicleStrikeDamageSource();
        if (!this.hurtVehicleStrikeWithArmor(source, damage)) {
            return;
        }
        this.ramDamageCooldown = VEHICLE_IMPACT_DAMAGE_COOLDOWN_TICKS;
        Direction bounceDirection = this.impactBounceDirection(requestedMovement);
        if (this.verticalCollision) {
            this.bounceVertical(bounceDirection);
        }
        if (this.horizontalCollision) {
            this.bounceHorizontal(bounceDirection);
        }
        this.enginePower *= 0.8D;
        this.needsSync = true;
    }

    private void applyAirVehicleImpactDamage(VehicleType vehicleType, Vec3 requestedMovement, boolean wasVerticallySupported) {
        // Sitting on the ground, gravity keeps pressing the hull into it; that is resting, not landing. Counting it as an
        // impact bounced parked helicopters, chipped their health and clanked every few ticks.
        boolean landing = this.verticalCollision && !wasVerticallySupported;
        if (!landing && !this.horizontalCollision) {
            return;
        }
        float damageAmount = this.airVehicleImpactDamage(vehicleType, landing);
        if (damageAmount <= 0.0F) {
            return;
        }
        this.resetAirborneImpactTracking();
        this.ramDamageCooldown = VEHICLE_IMPACT_DAMAGE_COOLDOWN_TICKS;
        Direction bounceDirection = this.impactBounceDirection(requestedMovement);
        if (landing) {
            this.bounceVertical(bounceDirection);
        }
        if (this.horizontalCollision) {
            this.bounceHorizontal(bounceDirection);
            this.enginePower *= 0.8D;
        }
        this.needsSync = true;

        // Both hurt paths play the strike sound once health actually drops, so armor-absorbed bumps stay silent.
        DamageSource source = this.vehicleStrikeDamageSource();
        if (vehicleType == VehicleType.HELICOPTER && this.lastTickSpeed >= HELICOPTER_FATAL_IMPACT_SPEED) {
            this.hurtVehicleIgnoringArmor(source, Math.max(damageAmount, this.maxVehicleHealth() + 1.0F));
            return;
        }
        this.hurtVehicleStrikeWithArmor(source, damageAmount);
    }

    private float airVehicleImpactDamage(VehicleType vehicleType, boolean landing) {
        double horizontalImpactSpeed = this.horizontalCollision ? this.lastTickHorizontalSpeed : 0.0D;
        double verticalImpactSpeed = landing ? Math.abs(this.lastTickVerticalSpeed) : 0.0D;
        if (vehicleType == VehicleType.HELICOPTER) {
            if (landing) {
                // A hull that only rose a few centimetres (a skid nudged up by a block beside it, a lift-off wobble) cannot
                // come back down fast, whatever velocity it carried.
                verticalImpactSpeed = this.verticalCollisionBelow
                        ? Math.min(Math.max(0.0D, -this.lastTickVerticalSpeed), this.currentAirborneDropDistance())
                        : Math.max(0.0D, this.lastTickVerticalSpeed);
            }
            return (float) (this.vehicleImpactDamage(horizontalImpactSpeed, 0.0D, 0.0D)
                    + this.helicopterVerticalImpactDamage(verticalImpactSpeed));
        }
        double fallDistance = this.verticalCollision ? this.airVehicleEffectiveFallDistance(verticalImpactSpeed) : 0.0D;
        return this.vehicleImpactDamage(horizontalImpactSpeed, verticalImpactSpeed, fallDistance);
    }

    private double helicopterVerticalImpactDamage(double verticalImpactSpeed) {
        if (verticalImpactSpeed <= HELICOPTER_SAFE_DESCENT_SPEED) {
            return 0.0D;
        }
        double speedRange = HELICOPTER_MAX_DESCENT_SPEED - HELICOPTER_SAFE_DESCENT_SPEED;
        double t = speedRange <= 0.0D ? 1.0D : Mth.clamp((verticalImpactSpeed - HELICOPTER_SAFE_DESCENT_SPEED) / speedRange, 0.0D, 1.0D);
        return this.maxVehicleHealth() * HELICOPTER_VERTICAL_IMPACT_MAX_DAMAGE_RATIO * t * t;
    }

    private double airVehicleEffectiveFallDistance(double verticalImpactSpeed) {
        boolean uncontrolled = this.getControllingPassenger() == null;
        double damageThreshold = uncontrolled ? AIR_VEHICLE_UNCONTROLLED_IMPACT_DAMAGE_THRESHOLD : VEHICLE_VERTICAL_IMPACT_DAMAGE_THRESHOLD;
        if (verticalImpactSpeed <= damageThreshold) {
            return 0.0D;
        }
        double trackedFallDistance = this.currentAirborneDropDistance();
        if (trackedFallDistance <= VEHICLE_FALL_IMPACT_SAFE_DROP) {
            return trackedFallDistance;
        }
        double fullDropSpeed = uncontrolled ? AIR_VEHICLE_UNCONTROLLED_FULL_DROP_SPEED : AIR_VEHICLE_CONTROLLED_FULL_DROP_SPEED;
        double speedRange = fullDropSpeed - damageThreshold;
        double speedFactor = speedRange <= 0.0D ? 1.0D : Mth.clamp((verticalImpactSpeed - damageThreshold) / speedRange, 0.0D, 1.0D);
        return VEHICLE_FALL_IMPACT_SAFE_DROP + (trackedFallDistance - VEHICLE_FALL_IMPACT_SAFE_DROP) * speedFactor;
    }

    private Vec3 stopAtVehicleEntityImpact(Vec3 before, Vec3 requestedMovement, Vec3 actualMovement, Vec3 velocityBeforeMove) {
        if (this.level().isClientSide()
                || this.noPhysics
                || this.isRemoved()
                || this.vehicleData().defaults().collisionLevel() == CollisionLevel.NONE
                || requestedMovement.lengthSqr() <= 1.0E-8D) {
            return actualMovement;
        }
        AABB currentBox = this.getBoundingBox();
        AABB previousBox = currentBox.move(before.subtract(this.position()));
        AABB sweptBox = this.sweptVehicleEntityCollisionBox(previousBox, currentBox);
        for (VehicleEntity target : this.level().getEntitiesOfClass(VehicleEntity.class, sweptBox.inflate(VEHICLE_ENTITY_COLLISION_SEARCH_EXPANSION))) {
            if (target == this || target.isRemoved() || target.noPhysics
                    || target.vehicleData().defaults().collisionLevel() == CollisionLevel.NONE) {
                continue;
            }
            AABB targetBox = target.getBoundingBox();
            boolean alreadyBroadlyOverlapping = previousBox.intersects(targetBox);
            if ((alreadyBroadlyOverlapping && !this.isMovingTowardVehicleImpact(target, requestedMovement))
                    || (!sweptBox.intersects(targetBox) && !this.vehicleEntityCollisionIntersects(target))) {
                continue;
            }
            double relativeSpeed = this.vehicleRelativeCollisionSpeed(target, velocityBeforeMove, target.getDeltaMovement());
            this.setPos(before.x(), before.y(), before.z());
            this.setDeltaMovement(Vec3.ZERO);
            target.setDeltaMovement(Vec3.ZERO);
            this.needsSync = true;
            target.needsSync = true;
            this.hurtMarked = true;
            target.hurtMarked = true;
            this.damageVehicleEntityCollision(target, relativeSpeed);
            return Vec3.ZERO;
        }
        return actualMovement;
    }

    private AABB sweptVehicleEntityCollisionBox(AABB previousBox, AABB currentBox) {
        return new AABB(
                Math.min(previousBox.minX, currentBox.minX),
                Math.min(previousBox.minY, currentBox.minY),
                Math.min(previousBox.minZ, currentBox.minZ),
                Math.max(previousBox.maxX, currentBox.maxX),
                Math.max(previousBox.maxY, currentBox.maxY),
                Math.max(previousBox.maxZ, currentBox.maxZ));
    }

    private boolean isMovingTowardVehicleImpact(VehicleEntity target, Vec3 requestedMovement) {
        Vec3 toTarget = target.position().subtract(this.position());
        VehicleType type = this.vehicleData().defaults().vehicleType();
        VehicleType targetType = target.vehicleData().defaults().vehicleType();
        if (type == VehicleType.HELICOPTER || type == VehicleType.AIRCRAFT || targetType == VehicleType.HELICOPTER || targetType == VehicleType.AIRCRAFT) {
            return requestedMovement.dot(toTarget) > 0.0D;
        }
        return new Vec3(requestedMovement.x, 0.0D, requestedMovement.z).dot(new Vec3(toTarget.x, 0.0D, toTarget.z)) > 0.0D;
    }

    private void updateVehicleSupportTicks() {
        if (this.onGround() || this.verticalCollisionBelow) {
            this.resetAirborneImpactTracking();
        } else {
            if (Double.isNaN(this.airborneStartY)) {
                this.airborneStartY = this.getY();
                this.airborneMaxY = this.getY();
            } else {
                this.airborneMaxY = Math.max(this.airborneMaxY, this.getY());
            }
            this.unsupportedVehicleTicks = Math.min(this.unsupportedVehicleTicks + 1, 100);
        }
    }

    private double currentAirborneDropDistance() {
        double startY = Double.isNaN(this.airborneStartY) ? this.yo : this.airborneStartY;
        double maxY = Double.isNaN(this.airborneMaxY) ? startY : Math.max(this.airborneMaxY, startY);
        return Math.max(0.0D, maxY - this.getY());
    }

    private boolean isLandVehicleBodyImpact(Vec3 requestedMovement, Vec3 actualMovement) {
        Vec3 requestedHorizontal = new Vec3(requestedMovement.x(), 0.0D, requestedMovement.z());
        double requestedSpeed = requestedHorizontal.length();
        if (requestedSpeed < LAND_VEHICLE_BODY_IMPACT_MIN_SPEED) {
            return false;
        }
        double actualSpeed = new Vec3(actualMovement.x(), 0.0D, actualMovement.z()).length();
        if (actualSpeed / requestedSpeed > LAND_VEHICLE_BODY_IMPACT_MAX_MOVED_RATIO) {
            return false;
        }
        AABB box = this.getBoundingBox();
        double height = box.getYsize();
        double lowerOffset = Mth.clamp(height * 0.45D, 0.75D, 1.35D);
        double upperInset = Mth.clamp(height * 0.10D, 0.05D, 0.25D);
        double minY = Math.min(box.minY + lowerOffset, box.maxY - 0.15D);
        double maxY = Math.max(minY + 0.15D, box.maxY - upperInset);
        AABB bodyProbe = new AABB(box.minX, minY, box.minZ, box.maxX, Math.min(maxY, box.maxY), box.maxZ)
                .move(requestedHorizontal.normalize().scale(LAND_VEHICLE_BODY_IMPACT_PROBE_DISTANCE));
        return this.intersectsBlockCollision(bodyProbe);
    }

    private float vehicleImpactDamage(double horizontalImpactSpeed, double verticalImpactSpeed) {
        return this.vehicleImpactDamage(horizontalImpactSpeed, verticalImpactSpeed, 0.0D);
    }

    private float vehicleImpactDamage(double horizontalImpactSpeed, double verticalImpactSpeed, double fallDistance) {
        double horizontalDamage = this.horizontalImpactDamage(horizontalImpactSpeed);
        double verticalDamage = this.fallImpactDamage(fallDistance, verticalImpactSpeed);
        if (horizontalDamage <= 0.0D && verticalDamage <= 0.0D) {
            return 0.0F;
        }
        double damage = horizontalDamage + verticalDamage;
        return (float) (damage * this.vehicleCollisionWeight() * this.vehicleImpactDamageMultiplier(horizontalImpactSpeed, verticalImpactSpeed, fallDistance));
    }

    private double vehicleImpactDamageMultiplier(double horizontalImpactSpeed, double verticalImpactSpeed, double fallDistance) {
        double severity = Math.max(0.0D, fallDistance) / VEHICLE_IMPACT_MULTIPLIER_DROP_REFERENCE
                + Math.max(0.0D, horizontalImpactSpeed) / VEHICLE_IMPACT_MULTIPLIER_HORIZONTAL_REFERENCE
                + Math.max(0.0D, verticalImpactSpeed) / VEHICLE_IMPACT_MULTIPLIER_VERTICAL_REFERENCE;
        double curve = 1.0D - Math.exp(-severity);
        return VEHICLE_IMPACT_DAMAGE_MIN_MULTIPLIER + (1.0D - VEHICLE_IMPACT_DAMAGE_MIN_MULTIPLIER) * curve;
    }

    private double horizontalImpactDamage(double horizontalImpactSpeed) {
        if (horizontalImpactSpeed <= VEHICLE_HORIZONTAL_IMPACT_DAMAGE_THRESHOLD) {
            return 0.0D;
        }
        double lightImpact = Math.min(horizontalImpactSpeed, VEHICLE_HORIZONTAL_IMPACT_DAMAGE_LIGHT_END) - VEHICLE_HORIZONTAL_IMPACT_DAMAGE_THRESHOLD;
        double damage = lightImpact * VEHICLE_HORIZONTAL_IMPACT_LIGHT_DAMAGE_SCALE;
        if (horizontalImpactSpeed > VEHICLE_HORIZONTAL_IMPACT_DAMAGE_LIGHT_END) {
            double heavyImpact = horizontalImpactSpeed - VEHICLE_HORIZONTAL_IMPACT_DAMAGE_LIGHT_END;
            damage += heavyImpact * heavyImpact * VEHICLE_HORIZONTAL_IMPACT_HEAVY_DAMAGE_SCALE;
        }
        return damage;
    }

    private double fallImpactDamage(double fallDistance, double verticalImpactSpeed) {
        if (fallDistance <= VEHICLE_FALL_IMPACT_SAFE_DROP) {
            return 0.0D;
        }
        double lightDrop = Math.min(fallDistance, VEHICLE_FALL_IMPACT_LIGHT_DROP_END) - VEHICLE_FALL_IMPACT_SAFE_DROP;
        double damage = lightDrop * VEHICLE_FALL_IMPACT_LIGHT_DAMAGE_SCALE;
        if (fallDistance > VEHICLE_FALL_IMPACT_LIGHT_DROP_END) {
            double heavyDrop = fallDistance - VEHICLE_FALL_IMPACT_LIGHT_DROP_END;
            damage += heavyDrop * VEHICLE_FALL_IMPACT_HEAVY_LINEAR_SCALE
                    + Math.pow(heavyDrop, 1.25D) * VEHICLE_FALL_IMPACT_HEAVY_CURVE_SCALE;
        }
        double verticalImpact = Math.max(0.0D, verticalImpactSpeed - VEHICLE_VERTICAL_IMPACT_DAMAGE_THRESHOLD);
        damage += verticalImpact * verticalImpact * VEHICLE_FALL_IMPACT_SPEED_DAMAGE_SCALE;
        return damage;
    }

    private double vehicleCollisionWeight() {
        return switch (this.vehicleData().defaults().collisionLevel()) {
            case LIGHT -> 0.75D;
            case MEDIUM -> 1.0D;
            case HEAVY -> 1.25D;
            case NONE -> 0.0D;
        };
    }

    private Direction impactBounceDirection(Vec3 movement) {
        double x = Math.abs(movement.x());
        double y = Math.abs(movement.y());
        double z = Math.abs(movement.z());
        if (y >= x && y >= z) {
            return movement.y() < 0.0D ? Direction.UP : Direction.DOWN;
        }
        if (x >= z) {
            return movement.x() < 0.0D ? Direction.EAST : Direction.WEST;
        }
        return movement.z() < 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private DamageSource vehicleStrikeDamageSource() {
        Entity attacker = this.getControllingPassenger();
        return ModDamageTypes.causeVehicleStrikeDamage(this.level().registryAccess(), this, attacker == null ? this : attacker);
    }

    private void playVehicleStrikeSound() {
        this.playVehicleMetalHitSound(this.getSoundSource(), 1.0F);
    }

    private void playVehicleDamageSound(boolean penetrated) {
        this.playVehicleMetalHitSound(SoundSource.PLAYERS, penetrated ? 0.85F : 1.2F);
    }

    private void playVehicleMetalHitSound(SoundSource source, float pitch) {
        if (this.tickCount == this.lastVehicleImpactSoundTick) {
            return;
        }
        this.lastVehicleImpactSoundTick = this.tickCount;
        var holder = ModSounds.ALL.get(Reference.id("block.hit.metal"));
        SoundEvent sound = holder == null ? SoundEvents.ANVIL_LAND : holder.get();
        this.level().playSound(null, this, sound, source, 1.0F, pitch);
    }

    private void bounceHorizontal(Direction direction) {
        if (direction.getAxis() == Direction.Axis.X) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.8D, 0.99D, 0.99D));
        } else if (direction.getAxis() == Direction.Axis.Z) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.99D, 0.99D, 0.8D));
        }
    }

    private void bounceVertical(Direction direction) {
        if (direction.getAxis() == Direction.Axis.Y) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.9D, -0.8D, 0.9D));
        }
    }

    private void tickRecentDismountStateSync() {
        if (this.level().isClientSide() || this.recentDismountSyncTicks <= 0 || this.recentDismountSyncPlayerId == null) {
            return;
        }
        this.recentDismountSyncTicks--;
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            this.recentDismountSyncPlayerId = null;
            return;
        }
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(this.recentDismountSyncPlayerId);
        if (player == null || player.level() != this.level()) {
            this.recentDismountSyncPlayerId = null;
            this.recentDismountSyncTicks = 0;
            return;
        }
        NetworkHandler.broadcastForcedVehicleState(this);
        if (this.recentDismountSyncTicks <= 0) {
            this.recentDismountSyncPlayerId = null;
        }
    }

    private void tickDriverStateSync() {
        int syncInterval = this.authoritativeVehicleStateSyncInterval(true);
        if (syncInterval > 0 && this.tickCount % syncInterval == 0) {
            NetworkHandler.broadcastVehicleState(this);
        }
    }

    private boolean isEnemyAiVehicle() {
        return this.entityTags().contains(EnemyVehicleController.ENEMY_VEHICLE_TAG);
    }

    private int authoritativeVehicleStateSyncInterval(boolean serverSide) {
        VehicleType type = this.vehicleData().defaults().vehicleType();
        if (type != VehicleType.LAND && type != VehicleType.BOAT && type != VehicleType.HELICOPTER) {
            return 0;
        }
        Entity controllingPassenger = this.getControllingPassenger();
        if ((serverSide ? controllingPassenger instanceof ServerPlayer : controllingPassenger != null) || this.isEnemyAiVehicle()) {
            return MOVING_DRIVER_STATE_SYNC_INTERVAL;
        }
        if (controllingPassenger == null && this.shouldSyncUnmannedPredictedState()) {
            return UNMANNED_AIRBORNE_STATE_SYNC_INTERVAL;
        }
        return 0;
    }
    private boolean shouldSyncUnmannedPredictedState() {
        if (this.getControllingPassenger() != null) {
            return false;
        }
        Vec3 motion = this.getDeltaMovement();
        return !this.onGround()
                || Math.abs(motion.y) > VEHICLE_IDLE_FALL_SPEED_THRESHOLD
                || motion.horizontalDistanceSqr() > VEHICLE_IDLE_SYNC_MOTION_THRESHOLD_SQR
                || Math.abs(this.getY() - this.yo) > VEHICLE_IDLE_SYNC_Y_DELTA;
    }

    private boolean shouldRunClientPrediction() {
        return this.dismountLerpSuppressionTicks <= 0
                && this.isLocalInstanceAuthoritative()
                && clientVehicleStateMatches(this.getId());
    }

    private void clearStaleDriverInput() {
        if (this.getControllingPassenger() == null) {
            if (this.keepsCameraLevel() && this.hasPlayerPassenger() && !this.onGround()) {
                // The crew is still aboard with the pilot's seat empty: the engine stays on for the autopilot to hover.
                this.input = VehicleInput.EMPTY;
            } else if (!this.level().isClientSide() && this.getPassengers().isEmpty()) {
                this.clearControlState(false);
                this.recentDismountSyncPlayerId = null;
                this.recentDismountSyncTicks = 0;
            } else {
                this.clearDriverControlState(false);
            }
        }
    }

    private void clearControlState(boolean stopHorizontalMotion) {
        this.entityData.set(DATA_HELICOPTER_STARTUP, 0);
        this.input = VehicleInput.EMPTY;
        this.weaponFireInput = false;
        this.seekInput = false;
        this.weaponControllerId = -1;
        this.seekControllerId = -1;
        this.entityData.set(DATA_WEAPON_FIRING, false);
        this.entityData.set(DATA_ACTIVE_FIRE_WEAPON, -1);
        this.enginePower = 0.0D;
        this.wheelSteering = 0.0D;
        this.holdTick = 0;
        this.holdPowerTick = 0;
        this.engineStart = false;
        this.engineStartOver = false;
        this.destroyRot = 0.0F;
        this.cancelWeaponReload();
        if (stopHorizontalMotion) {
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(0.0D, motion.y, 0.0D);
            this.needsSync = true;
        }
    }

    private void clearDriverControlState(boolean stopHorizontalMotion) {
        this.entityData.set(DATA_HELICOPTER_STARTUP, 0);
        this.input = VehicleInput.EMPTY;
        this.enginePower = 0.0D;
        this.wheelSteering = 0.0D;
        this.holdTick = 0;
        this.holdPowerTick = 0;
        this.engineStart = false;
        this.engineStartOver = false;
        this.destroyRot = 0.0F;
        if (stopHorizontalMotion) {
            Vec3 motion = this.getDeltaMovement();
            this.setDeltaMovement(0.0D, motion.y, 0.0D);
            this.needsSync = true;
        }
    }

    public void clearClientControlState() {
        this.clearControlState(true);
    }

    public void applySeatAssignments(Map<UUID, Integer> assignments) {
        this.seatAssignments.clear();
        int seatCount = this.vehicleData().defaults().seats().size();
        for (Map.Entry<UUID, Integer> assignment : assignments.entrySet()) {
            int seatIndex = assignment.getValue();
            if (seatIndex >= 0 && seatIndex < seatCount) {
                this.seatAssignments.put(assignment.getKey(), seatIndex);
            }
        }
        Entity localPlayer = localClientPlayer();
        if (this.level().isClientSide() && localPlayer != null && localPlayer.getVehicle() == this) {
            int fallbackIndex = this.getPassengers().indexOf(localPlayer);
            int seatIndex = this.seatIndexForPassenger(localPlayer, fallbackIndex);
            this.alignPassengerViewToVehicle(localPlayer, seatIndex);
        }
    }

    public Map<UUID, Integer> seatAssignmentsSnapshot() {
        return new HashMap<>(this.seatAssignments);
    }

    public void forgetSeatAssignment(Entity passenger) {
        this.preservedSeatAssignments.remove(passenger.getUUID());
        this.seatAssignments.remove(passenger.getUUID());
        this.syncSeatAssignments();
    }

    public void preserveSeatAssignment(Entity passenger) {
        if (this.seatAssignments.containsKey(passenger.getUUID())) {
            this.preservedSeatAssignments.add(passenger.getUUID());
        }
    }

    public void rememberSeatAssignment(Entity passenger, int seatIndex) {
        int seatCount = this.vehicleData().defaults().seats().size();
        if (seatIndex < 0 || seatIndex >= seatCount || this.isSeatOccupied(seatIndex, passenger)) {
            return;
        }
        this.seatAssignments.put(passenger.getUUID(), seatIndex);
    }

    private void syncSeatAssignments() {
        if (!this.level().isClientSide()) {
            NetworkHandler.broadcastVehicleSeatAssignments(this);
        }
    }

    public void syncAuthoritativeState(double x, double y, double z, double motionX, double motionY, double motionZ, float yaw, float pitch, boolean forceApply) {
        if (!this.shouldApplyAuthoritativeState(x, y, z, motionX, motionY, motionZ, yaw, pitch, forceApply)) {
            return;
        }
        if (this.level().isClientSide() && !forceApply && !this.shouldRunClientPrediction()) {
            SyncedStateInterpolation interpolation = this.syncedStateInterpolation();
            if (interpolation != null) {
                this.lastSyncedStateTick = this.tickCount;
                interpolation.interpolateToSyncedState(new Vec3(x, y, z), yaw, pitch);
            } else {
                int lerpSteps = this.authoritativeVehicleStateSyncInterval(false);
                super.lerpPositionAndRotationStep(lerpSteps > 0 ? lerpSteps : MOVING_DRIVER_STATE_SYNC_INTERVAL, x, y, z, yaw, pitch);
            }
            super.lerpMotion(new Vec3(motionX, motionY, motionZ));
            return;
        }
        this.setPos(x, y, z);
        this.setDeltaMovement(motionX, motionY, motionZ);
        this.setYRot(yaw);
        this.setXRot(pitch);
        this.updateVehicleBoundingBox();
        this.yRotO = yaw;
        this.xRotO = pitch;
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.needsSync = true;
        if (forceApply && this.level().isClientSide()) {
            this.dismountLerpSuppressionTicks = DISMOUNT_LERP_SUPPRESSION_TICKS;
            if (clientVehicleId() == this.getId() && !this.isLocalInstanceAuthoritative()) {
                clearClientVehicleState();
                this.clearControlState(false);
            }
        }
    }

    @Override
    @Nullable
    public InterpolationHandler getInterpolation() {
        return this.syncedStateInterpolation();
    }

    /**
     * Remote-controlled land, boat and helicopter vehicles are smoothed from the mod's state packets. Vanilla move packets
     * snapped them each tick with rotation rounded to 1.4 degrees, fighting the precise state and making the seats bob.
     */
    @Nullable
    private SyncedStateInterpolation syncedStateInterpolation() {
        if (!this.level().isClientSide() || this.isLocalInstanceAuthoritative()) {
            return null;
        }
        VehicleType type = this.vehicleData().defaults().vehicleType();
        if (type != VehicleType.LAND && type != VehicleType.BOAT && type != VehicleType.HELICOPTER) {
            return null;
        }
        if (this.syncedStateInterpolation == null) {
            this.syncedStateInterpolation = new SyncedStateInterpolation(this);
        }
        return this.syncedStateInterpolation;
    }

    private static final class SyncedStateInterpolation extends InterpolationHandler {
        private static final int VANILLA_PACKET_SUPPRESSION_TICKS = 5;
        private final VehicleEntity vehicle;

        private SyncedStateInterpolation(VehicleEntity vehicle) {
            super(vehicle);
            this.vehicle = vehicle;
        }

        @Override
        public void interpolateTo(Vec3 position, float yRot, float xRot) {
            // Vanilla packets still move parked or pushed vehicles, but not while the mod's state sync is running.
            if (this.vehicle.tickCount - this.vehicle.lastSyncedStateTick <= VANILLA_PACKET_SUPPRESSION_TICKS) {
                return;
            }
            super.interpolateTo(position, yRot, xRot);
        }

        private void interpolateToSyncedState(Vec3 position, float yRot, float xRot) {
            super.interpolateTo(position, yRot, xRot);
        }
    }

    @Override
    protected void lerpPositionAndRotationStep(int steps, double x, double y, double z, double yRot, double xRot) {
        if (this.level().isClientSide()) {
            VehicleType type = this.vehicleData().defaults().vehicleType();
            if (type == VehicleType.LAND || type == VehicleType.BOAT || type == VehicleType.HELICOPTER) {
                return;
            }
        }
        super.lerpPositionAndRotationStep(steps, x, y, z, yRot, xRot);
    }

    @Override
    public void lerpMotion(Vec3 motion) {
        if (this.level().isClientSide()) {
            VehicleType type = this.vehicleData().defaults().vehicleType();
            if (type == VehicleType.LAND || type == VehicleType.BOAT || type == VehicleType.HELICOPTER) {
                return;
            }
        }
        super.lerpMotion(motion);
    }

    private boolean shouldApplyAuthoritativeState(double x, double y, double z, double motionX, double motionY, double motionZ, float yaw, float pitch, boolean forceApply) {
        if (forceApply || !this.level().isClientSide() || !this.shouldRunClientPrediction()) {
            return true;
        }
        Vec3 syncedPosition = new Vec3(x, y, z);
        Vec3 syncedMotion = new Vec3(motionX, motionY, motionZ);
        Vec3 horizontalMotionDelta = new Vec3(this.getDeltaMovement().x - motionX, 0.0D, this.getDeltaMovement().z - motionZ);
        if (this.isPositionDivergedFromServer(syncedPosition, syncedMotion)) {
            return true;
        }
        if (horizontalMotionDelta.lengthSqr() >= CLIENT_RESYNC_HORIZONTAL_MOTION_DELTA_SQR) {
            return true;
        }
        return Math.abs(Mth.wrapDegrees(this.getYRot() - yaw)) >= CLIENT_RESYNC_YAW_DELTA
                || Math.abs(Mth.wrapDegrees(this.getXRot() - pitch)) >= CLIENT_RESYNC_PITCH_DELTA;
    }

    /**
     * The server's state is a tick or a few old when it lands, so at speed the client is that many ticks of travel along
     * its path from it without anything being wrong. The AH-6 covers 2.3 blocks a tick flat out, past the plain 2-block
     * limit, and every tick of network jitter snapped the pilot back a full tick. Only being off to the side of the path,
     * or further along it than a few ticks of travel, counts as diverged.
     */
    private boolean isPositionDivergedFromServer(Vec3 syncedPosition, Vec3 syncedMotion) {
        Vec3 offset = this.position().subtract(syncedPosition);
        double speed = syncedMotion.length();
        if (speed < 1.0E-3D) {
            return offset.lengthSqr() >= CLIENT_RESYNC_DISTANCE_SQR;
        }
        Vec3 direction = syncedMotion.scale(1.0D / speed);
        double along = offset.dot(direction);
        Vec3 across = offset.subtract(direction.scale(along));
        double alongTolerance = Math.sqrt(CLIENT_RESYNC_DISTANCE_SQR) + speed * CLIENT_RESYNC_ALONG_TRACK_TICKS;
        return across.lengthSqr() >= CLIENT_RESYNC_DISTANCE_SQR || Math.abs(along) >= alongTolerance;
    }

    private void tickServerWeapon() {
        if (!this.hasVehicleWeapons()) {
            this.entityData.set(DATA_WEAPON_FIRING, false);
            this.entityData.set(DATA_ROTARY_GUN_REQUESTED, false);
            this.entityData.set(DATA_ROTARY_GUN_SPIN, 0.0F);
            this.entityData.set(DATA_ACTIVE_FIRE_WEAPON, -1);
            return;
        }
        this.fireCooldownByWeaponSlot.replaceAll((slot, cooldown) -> Math.max(0, cooldown - 1));
        this.fireCooldownByWeaponSlot.values().removeIf(cooldown -> cooldown <= 0);
        this.aiFireCooldownByWeaponSlot.replaceAll((slot, cooldown) -> Math.max(0, cooldown - 1));
        this.aiFireCooldownByWeaponSlot.entrySet().removeIf(entry -> entry.getValue() <= 0);

        boolean[] anyFiring = {false};
        int[] activeFireWeaponSlot = {-1};
        LivingEntity shooter = this.weaponController();
        VehicleWeaponInfo selectedWeapon = shooter == null ? null : this.selectedWeapon(shooter);
        this.tickRotaryGun(shooter, selectedWeapon);
        if (this.vehicleGunReady(selectedWeapon) && this.shouldLoopVehicleFireSound(shooter, selectedWeapon, this.weaponFireInput)) {
            anyFiring[0] = true;
            activeFireWeaponSlot[0] = this.selectedVehicleWeaponIndex(shooter);
        }
        if (this.tryFireVehicleWeapon(shooter, selectedWeapon, this.weaponFireInput, this.seekInput, -1)) {
            anyFiring[0] = true;
        }

        this.aiWeaponControlBySeat.entrySet().removeIf(entry -> {
            LivingEntity aiShooter = this.aiWeaponController(entry.getValue(), entry.getKey());
            if (aiShooter == null) {
                return true;
            }
            VehicleWeaponInfo aiWeapon = this.selectedWeapon(aiShooter);
            if (this.vehicleGunReady(aiWeapon) && this.shouldLoopVehicleFireSound(aiShooter, aiWeapon, entry.getValue().fire())) {
                anyFiring[0] = true;
                activeFireWeaponSlot[0] = this.selectedVehicleWeaponIndex(aiShooter);
            }
            if (this.tryFireVehicleWeapon(aiShooter, aiWeapon, entry.getValue().fire(), entry.getValue().seek(), entry.getKey())) {
                anyFiring[0] = true;
            }
            return false;
        });
        this.entityData.set(DATA_WEAPON_FIRING, anyFiring[0]);
        this.entityData.set(DATA_ACTIVE_FIRE_WEAPON, activeFireWeaponSlot[0]);
    }

    private boolean tryFireVehicleWeapon(@Nullable LivingEntity shooter, @Nullable VehicleWeaponInfo selectedWeapon, boolean fireInput, boolean seekInput, int aiSeatIndex) {
        int selectedSlot = shooter == null ? -1 : this.selectedVehicleWeaponIndex(shooter);
        // Only this weapon's own reload holds it back: another one reloading in the background does not.
        if (!fireInput || shooter == null || selectedWeapon == null || this.isWeaponSlotReloading(selectedSlot)) {
            return false;
        }
        if (this.weaponFireCooldown(selectedSlot, aiSeatIndex) > 0 || this.isTurretDamaged()) {
            return false;
        }
        VehicleWeaponInfo weapon = selectedWeapon;
        int shooterSeat = this.seatIndexForPassenger(shooter, this.getPassengers().indexOf(shooter));
        if (!weapon.usableBySeat(shooterSeat)) {
            return false;
        }
        GunStats stats = VehicleWeaponStats.get(weapon.weaponId());
        if (stats == null || !this.hasEnergy(weapon.energyCost())) {
            return false;
        }
        if (this.usesSharedVehicleReloadSystem(shooter)) {
            if (this.selectedWeaponLoadedAmmo(shooter) <= 0) {
                this.startWeaponReload(shooter);
                return false;
            }
        } else if (!this.hasAmmo(weapon.ammoId())) {
            return false;
        }
        if (!this.vehicleGunReady(weapon)) {
            return false;
        }
        // A pair of guns fires left, right, left: this shot comes from the mirrored barrel every other time, and aims
        // from it, so both streams still meet on the crosshair.
        this.mirroredShot = weapon.mirrored() && (this.twinGunShots++ & 1) == 1;
        Vec3 direction = this.weaponAimDirection(shooter);
        if (direction.lengthSqr() < 1.0E-4D || !this.consumeEnergy(weapon.energyCost())) {
            return false;
        }
        if (this.usesSharedVehicleReloadSystem(shooter)) {
            int slot = this.selectedVehicleWeaponIndex(shooter);
            this.setWeaponLoadedAmmo(slot, this.weaponLoadedAmmo(slot) - 1);
            this.syncSelectedWeaponAmmoState();
        } else if (!this.consumeAmmo(weapon.ammoId())) {
            return false;
        }
        boolean helicopterGun = VehicleSoundHelper.isHelicopterGun(this, weapon);
        // A helicopter's gun is heard through the firing loop alone (see VehicleFireSoundInstance).
        if (!helicopterGun && (this.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER || !this.shouldLoopVehicleFireSound(shooter, weapon, fireInput))) {
            this.playWeaponFireSound(weapon, stats);
        }
        if (weapon.guided()) {
            this.launchMissile(shooter, direction, stats, seekInput);
            this.setWeaponFireCooldown(selectedSlot, aiSeatIndex, stats.fireDelay());
            return true;
        }
        Vec3 muzzle = this.weaponMuzzlePosition(weapon, direction, 1.15D, 0.9D);
        this.mirroredShot = false;
        this.spawnVehicleMuzzleFlare(weapon, muzzle, direction);
        float damage = helicopterGun ? stats.damage() * HELICOPTER_GUN_DAMAGE_MULTIPLIER : stats.damage();
        Vec3 shot = helicopterGun ? this.withGunDispersion(direction) : direction;
        BulletEntity bullet = new BulletEntity(this.level(), shooter, stats, shot.scale(stats.projectileSpeed()), damage, false);
        bullet.setVehicleRound();
        if (helicopterGun) {
            bullet.setHelicopterGunRound();
        }
        bullet.initialisePosition(muzzle);
        this.level().addFreshEntity(bullet);
        if (this.level() instanceof ServerLevel serverLevel) {
            bullet.sendTrailToClients(serverLevel);
        }
        this.setWeaponFireCooldown(selectedSlot, aiSeatIndex, stats.fireDelay());
        return true;
    }

    /** A mounted gun on a vibrating airframe throws a tight cone around the crosshair rather than a single line. */
    private Vec3 withGunDispersion(Vec3 direction) {
        double spread = Math.toRadians(HELICOPTER_GUN_DISPERSION_DEGREES);
        return direction.add(
                (this.random.nextDouble() - 0.5D) * 2.0D * spread,
                (this.random.nextDouble() - 0.5D) * 2.0D * spread,
                (this.random.nextDouble() - 0.5D) * 2.0D * spread).normalize();
    }

    private int weaponFireCooldown(int selectedSlot, int aiSeatIndex) {
        if (aiSeatIndex < 0) {
            return this.fireCooldownByWeaponSlot.getOrDefault(selectedSlot, 0);
        }
        return this.aiFireCooldownByWeaponSlot.getOrDefault(selectedSlot, 0);
    }

    private void setWeaponFireCooldown(int selectedSlot, int aiSeatIndex, int cooldown) {
        int value = Math.max(1, cooldown);
        if (aiSeatIndex < 0) {
            this.fireCooldownByWeaponSlot.put(selectedSlot, value);
        } else {
            this.aiFireCooldownByWeaponSlot.put(selectedSlot, value);
        }
    }
    private void spawnVehicleMuzzleFlare(VehicleWeaponInfo weapon, Vec3 muzzle, Vec3 direction) {
        if (!(this.level() instanceof ServerLevel serverLevel) || direction.lengthSqr() < 1.0E-4D) {
            return;
        }
        String weaponPath = weapon.weaponId().getPath();
        if (weapon.guided() || weaponPath.contains("rocket") || weaponPath.contains("missile")) {
            this.spawnVehicleMuzzleFlareParticle(serverLevel, new CannonMuzzleFlareOption(1.0F, 0.86F, 0.55F, 6, 0.70F, 1, 0.10F), muzzle, direction, 3, 0.08D, 0.18D);
            this.spawnVehicleMuzzleFlareParticle(serverLevel, new CannonMuzzleFlareOption(0.55F, 0.45F, 0.4F, 18, 0.82F, 2, 0.025F), muzzle, direction, 1, 0.0D, 0.10D);
            return;
        }
        if (this.usesGunMuzzleFlash(weaponPath)) {
            this.spawnVehicleGunMuzzleFlash(serverLevel, muzzle, direction);
            return;
        }
        this.spawnVehicleMuzzleFlareParticle(serverLevel, new CannonMuzzleFlareOption(1.0F, 0.95F, 0.78F, 8, 0.70F, 1, 0.20F), muzzle, direction, 4, 0.10D, 0.30D);
        this.spawnVehicleMuzzleFlareParticle(serverLevel, new CannonMuzzleFlareOption(0.45F, 0.42F, 0.4F, 38, 0.88F, 2, 0.04F), muzzle, direction, 1, 0.0D, 0.14D);
    }

    private boolean usesGunMuzzleFlash(String weaponPath) {
        return weaponPath.contains("machine_gun")
                || weaponPath.contains("minigun")
                || weaponPath.equals("vehicle_coax_machine_gun")
                || weaponPath.equals("light_machine_gun");
    }

    private void spawnVehicleGunMuzzleFlash(ServerLevel level, Vec3 muzzle, Vec3 direction) {
        Vec3 forward = direction.normalize();
        // Helicopter guns fire fast and sit close to the pilot's view: a full-size flash (let alone cannon flares and
        // smoke, which are blocks wide) on every round washes out the screen. The MI-28's chin gun sits right on its
        // zoomed sight, so it gets the smallest flash; the AH-6's pylon miniguns a modest one.
        var flash = ModParticleTypes.GUN_MUZZLE_FLASH.get();
        if ("mi28".equals(this.vehicleDataId().getPath())) {
            flash = ModParticleTypes.SMALL_GUN_MUZZLE_FLASH.get();
        } else if (this.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER) {
            flash = ModParticleTypes.MEDIUM_GUN_MUZZLE_FLASH.get();
        }
        level.sendParticles(flash, muzzle.x, muzzle.y, muzzle.z, 0, forward.x, forward.y, forward.z, 0.12D);
    }

    private void spawnVehicleMuzzleFlareParticle(ServerLevel level, CannonMuzzleFlareOption option, Vec3 center, Vec3 direction, int count, double radius, double speed) {
        Vec3 forward = direction.normalize();
        Vec3 side = this.randomPerpendicular(forward).normalize();
        Vec3 up = forward.cross(side).normalize();
        int particleCount = Math.max(1, count);
        for (int i = 0; i < particleCount; i++) {
            double angle = 2.0D * Math.PI * i / particleCount;
            Vec3 offset = side.scale(Math.cos(angle) * radius).add(up.scale(Math.sin(angle) * radius));
            Vec3 pos = center.add(offset);
            Vec3 velocity = offset.lengthSqr() > 1.0E-6D ? offset.normalize().add(forward.scale(6.0D)) : forward.scale(6.0D);
            level.sendParticles(option, pos.x, pos.y, pos.z, 0, velocity.x, velocity.y, velocity.z, speed);
        }
    }

    private Vec3 randomPerpendicular(Vec3 direction) {
        Vec3 candidate = new Vec3(direction.y, -direction.x, 0.0D);
        if (candidate.lengthSqr() > 1.0E-4D) {
            return candidate;
        }
        return new Vec3(0.0D, direction.z, -direction.y);
    }

    private void playWeaponFireSound(VehicleWeaponInfo weapon, GunStats stats) {
        SoundEvent sound = VehicleSoundHelper.fireSound(this, weapon, stats);
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.PLAYERS, 7.5F, 1.0F);
    }

    private void playWeaponReloadSound(VehicleWeaponInfo weapon, @Nullable GunStats stats, boolean start) {
        if (stats == null) {
            return;
        }
        var sound = start ? stats.reloadStartSoundEvent() : stats.reloadEndSoundEvent().or(() -> stats.reloadLoadSoundEvent());
        sound.ifPresent(value -> this.level().playSound(null, this.getX(), this.getY(), this.getZ(), value, SoundSource.PLAYERS, 2.0F, 1.0F));
    }

    private boolean isRotaryGun(@Nullable VehicleWeaponInfo weapon) {
        return weapon != null && "mi28".equals(this.vehicleDataId().getPath())
                && VehicleSoundHelper.isHelicopterGun(this, weapon);
    }

    private boolean vehicleGunReady(@Nullable VehicleWeaponInfo weapon) {
        return !this.isRotaryGun(weapon) || this.rotaryGunSpin() >= 1.0F;
    }

    private void tickRotaryGun(@Nullable LivingEntity shooter, @Nullable VehicleWeaponInfo weapon) {
        boolean requested = this.isRotaryGun(weapon) && this.shouldLoopVehicleFireSound(shooter, weapon, this.weaponFireInput);
        for (var entry : this.aiWeaponControlBySeat.entrySet()) {
            LivingEntity controller = this.aiWeaponController(entry.getValue(), entry.getKey());
            VehicleWeaponInfo aiWeapon = controller == null ? null : this.selectedWeapon(controller);
            requested |= this.isRotaryGun(aiWeapon) && this.shouldLoopVehicleFireSound(controller, aiWeapon, entry.getValue().fire());
        }
        float spin = this.rotaryGunSpin();
        this.entityData.set(DATA_ROTARY_GUN_REQUESTED, requested);
        this.entityData.set(DATA_ROTARY_GUN_SPIN, requested
                ? Math.min(1.0F, spin + 1.0F / ROTARY_GUN_SPOOL_TICKS)
                : Math.max(0.0F, spin - 1.0F / ROTARY_GUN_STOP_TICKS));
    }

    private boolean shouldLoopVehicleFireSound(@Nullable LivingEntity shooter, @Nullable VehicleWeaponInfo weapon, boolean fireInput) {
        boolean helicopterGun = weapon != null && VehicleSoundHelper.isHelicopterGun(this, weapon);
        if (shooter == null
                || weapon == null
                || (this.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER && !helicopterGun)
                || (!helicopterGun && this.usesGunMuzzleFlash(weapon.weaponId().getPath()))
                || !fireInput
                || this.isWeaponSlotReloading(this.selectedVehicleWeaponIndex(shooter))
                || this.isTurretDamaged()
                || !this.canUseSelectedWeapon(shooter, weapon)
                || weapon.guided()) {
            return false;
        }
        GunStats stats = VehicleWeaponStats.get(weapon.weaponId());
        if (stats == null || (!helicopterGun && stats.fireDelay() > 4) || !this.hasEnergy(weapon.energyCost())) {
            return false;
        }
        if (this.usesSharedVehicleReloadSystem(shooter)) {
            return this.selectedWeaponLoadedAmmo(shooter) > 0;
        }
        return this.hasAmmo(weapon.ammoId());
    }

    private Vec3 weaponMuzzlePosition(VehicleWeaponInfo weapon, Vec3 direction, double fallbackForwardOffset, double fallbackHeight) {
        Vec3 articulatedMuzzle = this.articulatedWeaponMuzzlePosition(weapon);
        if (articulatedMuzzle != null) {
            return articulatedMuzzle;
        }
        if (weapon.hasMuzzle() && this.usesVehiclePoseTransform()) {
            // Aircraft pylons are bolted to the hull: place the muzzle with the hull's attitude, not the aim direction,
            // so a gunner looking sideways does not swing the rocket pods round the nose.
            return this.position().add(this.rotateLocalOffsetWithPose(this.muzzleX(weapon), weapon.muzzleY(), weapon.muzzleZ(), 1.0F));
        }
        if (weapon.hasMuzzle()) {
            Vec3 aim = direction.normalize();
            Vec3 side = new Vec3(aim.z, 0.0D, -aim.x);
            if (side.lengthSqr() < 1.0E-4D) {
                side = this.rotateLocalOffset(1.0D, 0.0D, 0.0D);
            } else {
                side = side.normalize();
            }
            return this.position()
                    .add(0.0D, weapon.muzzleY(), 0.0D)
                    .add(side.scale(this.muzzleX(weapon)))
                    .add(aim.scale(weapon.muzzleZ()));
        }
        return this.position().add(0.0D, fallbackHeight, 0.0D).add(direction.scale(fallbackForwardOffset));
    }

    /** The muzzle's sideways offset, on the far side for the mirrored barrel of a pair of guns. */
    private double muzzleX(VehicleWeaponInfo weapon) {
        return weapon.mirrored() && this.mirroredShot ? -weapon.muzzleX() : weapon.muzzleX();
    }

    @Nullable
    private Vec3 articulatedWeaponMuzzlePosition(VehicleWeaponInfo weapon) {
        if (!this.usesArticulatedTurretMuzzle(weapon)) {
            return null;
        }
        var turret = this.vehicleData().defaults().turret();
        return this.articulatedBarrelPosition(
                weapon.muzzleX() - turret.barrelX(),
                weapon.muzzleY() - turret.originY() - turret.barrelY(),
                weapon.muzzleZ() - turret.originZ() - turret.barrelZ(),
                1.0F
        );
    }

    private void tickMissileLock() {
        if (!this.hasVehicleWeapons()) {
            this.entityData.set(DATA_MISSILE_LOCKED, false);
            this.entityData.set(DATA_MISSILE_LOCK_TARGET, -1);
            return;
        }
        Entity target = null;
        LivingEntity shooter = this.seekInput ? this.seekController() : null;
        VehicleWeaponInfo weapon = shooter == null ? null : this.selectedWeapon(shooter);
        if (shooter != null && weapon != null && weapon.guided() && VehicleMissileProfile.get(weapon.weaponId()).usesLockOn()) {
            Vec3 direction = shooter.getViewVector(1.0F).normalize();
            if (direction.lengthSqr() >= 1.0E-4D) {
                target = this.findLookTarget(shooter, direction, this.seekRange(), this.seekMinDot(), VehicleMissileProfile.get(weapon.weaponId()));
            }
        }
        this.entityData.set(DATA_MISSILE_LOCKED, target != null);
        this.entityData.set(DATA_MISSILE_LOCK_TARGET, target == null ? -1 : target.getId());
        if (this.shouldWarnSeekTarget() && target != null && this.tickCount % VEHICLE_WARNING_SOUND_INTERVAL_TICKS == 0) {
            this.warnSeekTarget(target);
        }
    }

    private void warnSeekTarget(Entity target) {
        this.warnTarget(target, "message.jeg.vehicle.lock_warning", VehicleSoundHelper.lockedWarning(), 14.0F, 1.7F);
    }

    public static boolean hasRadarWarningSystem(Entity target) {
        return target instanceof VehicleEntity vehicle && RADAR_WARNING_VEHICLES.contains(vehicle.vehicleDataId().getPath());
    }

    public static void warnIncomingMissileTarget(Entity target) {
        warnTarget(target, "message.jeg.vehicle.missile_warning", VehicleSoundHelper.missileWarning(), 14.0F, 1.0F);
    }

    private static Component warningMessage(String key, int tickCount) {
        ChatFormatting color = (tickCount / 10) % 2 == 0 ? ChatFormatting.RED : ChatFormatting.DARK_RED;
        return Component.translatable(key).withStyle(style -> style.withColor(color).withBold(true));
    }

    private static void warnTarget(Entity target, String messageKey, SoundEvent sound, float volume, float pitch) {
        if (target instanceof ServerPlayer player) {
            warnPlayer(player, warningMessage(messageKey, target.tickCount), sound, volume, pitch);
            return;
        }
        if (!hasRadarWarningSystem(target)) {
            return;
        }
        if (target instanceof VehicleEntity vehicle) {
            vehicle.showVehicleWarning(messageKey);
            vehicle.playVehicleWarningSound(messageKey, sound, volume, pitch);
            return;
        }
        target.level().playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                sound,
                SoundSource.PLAYERS,
                volume,
                pitch
        );
    }

    private void showVehicleWarning(String messageKey) {
        this.entityData.set(DATA_WARNING_MESSAGE, messageKey);
        this.entityData.set(DATA_WARNING_UNTIL_TICK, this.tickCount + VEHICLE_WARNING_DISPLAY_TICKS);
    }

    private void playVehicleWarningSound(String messageKey, SoundEvent sound, float volume, float pitch) {
        if (messageKey.equals(this.lastVehicleWarningSoundKey)
                && this.tickCount - this.lastVehicleWarningSoundTick < VEHICLE_WARNING_SOUND_INTERVAL_TICKS) {
            return;
        }
        this.lastVehicleWarningSoundKey = messageKey;
        this.lastVehicleWarningSoundTick = this.tickCount;
        this.level().playSound(
                null,
                this.getX(),
                this.getY(),
                this.getZ(),
                sound,
                SoundSource.PLAYERS,
                volume,
                pitch
        );
    }

    private static void warnPlayer(ServerPlayer player, Component message, SoundEvent sound, float volume, float pitch) {
        player.sendSystemMessage(message, true);
        playWarningSound(player, sound, volume, pitch);
    }

    private static void playWarningSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                sound,
                SoundSource.PLAYERS,
                volume,
                pitch
        );
    }

    private void launchMissile(LivingEntity shooter, Vec3 direction, GunStats stats, boolean seekInput) {
        VehicleWeaponInfo weapon = this.selectedWeapon(shooter);
        if (weapon == null) {
            return;
        }
        Vec3 muzzle = this.weaponMuzzlePosition(weapon, direction, 1.25D, 0.95D);
        VehicleMissileProfile profile = VehicleMissileProfile.get(weapon.weaponId());
        Vec3 velocity = direction.scale(profile.maxSpeed() * VehicleMissileEntity.LAUNCH_SPEED_SHARE).add(this.getDeltaMovement());
        Entity target = seekInput && profile.usesLockOn() ? this.findLookTarget(shooter, direction, this.seekRange(), this.seekMinDot(), profile) : null;
        this.level().addFreshEntity(new VehicleMissileEntity(this.level(), shooter, target, muzzle, velocity, weapon.weaponId()));
        this.spawnVehicleMuzzleFlare(weapon, muzzle, direction);
    }

    /** Remembers what this crew member's crosshair is on. */
    public void setPassengerAimPoint(Entity passenger, Vec3 point) {
        this.passengerAimPoints.put(passenger.getUUID(), new AimPoint(point, this.tickCount));
    }

    @Nullable
    public Vec3 freshAimPoint(Entity passenger) {
        AimPoint aim = this.passengerAimPoints.get(passenger.getUUID());
        return aim != null && this.tickCount - aim.tick() <= AIM_POINT_MAX_AGE_TICKS ? aim.point() : null;
    }

    /**
     * Where a shot goes. The camera sits metres away from the guns (twelve back and six up in the chase view), so firing along
     * the view lands that far off the crosshair; rounds go from the muzzle to what the crosshair is on instead.
     */
    private Vec3 weaponAimDirection(LivingEntity shooter) {
        Vec3 nominal = this.nominalWeaponAimDirection(shooter);
        Vec3 aim = this.freshAimPoint(shooter);
        VehicleWeaponInfo weapon = this.selectedWeapon(shooter);
        if (aim == null || weapon == null) {
            return nominal;
        }
        Vec3 toAim = aim.subtract(this.weaponMuzzlePosition(weapon, nominal, 1.15D, 0.9D));
        if (toAim.lengthSqr() < AIM_POINT_MIN_DISTANCE_SQR) {
            return nominal;
        }
        Vec3 direction = toAim.normalize();
        return direction.dot(nominal) < AIM_POINT_MAX_OFF_VIEW_COS ? nominal : direction;
    }

    private Vec3 nominalWeaponAimDirection(LivingEntity shooter) {
        Vec3 articulatedAim = this.articulatedWeaponAimDirection(shooter);
        if (articulatedAim != null) {
            return articulatedAim;
        }
        Vec3 zoomAim = this.weaponZoomAimDirection(shooter);
        if (zoomAim != null) {
            return zoomAim;
        }
        return Vec3.directionFromRotation(this.weaponPitch(shooter), shooter.getYRot()).normalize();
    }

    @Nullable
    private Vec3 articulatedWeaponAimDirection(LivingEntity shooter) {
        if (!this.usesArticulatedTurretAim(shooter)) {
            return null;
        }
        return this.articulatedBarrelDirection(1.0F).normalize();
    }

    @Nullable
    private Vec3 weaponZoomAimDirection(LivingEntity shooter) {
        if (!this.level().isClientSide()
                || !clientVehicleStateMatches(this.getId())
                || !clientZoomDown()) {
            return null;
        }
        VehicleWeaponInfo weapon = this.selectedWeapon(shooter);
        if (weapon == null || !this.canUseSelectedWeapon(shooter, weapon)) {
            return null;
        }
        Vec3 muzzle = this.weaponMuzzlePosition(weapon, Vec3.directionFromRotation(this.weaponPitch(shooter), shooter.getYRot()), 1.25D, 0.95D);
        Vec3 camera = this.cameraPositionFor(shooter, 1.0F);
        Vec3 direction = muzzle.subtract(camera);
        return direction.lengthSqr() < 1.0E-4D ? null : direction.normalize();
    }

    private float weaponPitch(LivingEntity shooter) {
        SeatInfo seat = this.seatForPassenger(shooter, this.getPassengers().indexOf(shooter));
        return Mth.clamp(shooter.getXRot(), seat.minPitch(), seat.maxPitch());
    }

    @Nullable
    private LivingEntity weaponController() {
        Entity controller = this.weaponControllerId < 0 ? this.getControllingPassenger() : this.level().getEntity(this.weaponControllerId);
        if (controller instanceof LivingEntity living && living.getVehicle() == this) {
            return living;
        }
        this.weaponControllerId = -1;
        this.weaponFireInput = false;
        this.entityData.set(DATA_WEAPON_FIRING, false);
        this.entityData.set(DATA_ACTIVE_FIRE_WEAPON, -1);
        return null;
    }

    @Nullable
    private LivingEntity aiWeaponController(AiWeaponControl control, int seatIndex) {
        Entity controller = this.level().getEntity(control.entityId());
        if (controller instanceof LivingEntity living && living.getVehicle() == this && this.getSeatIndex(living) == seatIndex) {
            return living;
        }
        return null;
    }

    @Nullable
    private LivingEntity seekController() {
        Entity controller = this.seekControllerId < 0 ? null : this.level().getEntity(this.seekControllerId);
        VehicleWeaponInfo weapon = controller instanceof LivingEntity living ? this.selectedWeapon(living) : null;
        if (weapon != null && controller instanceof LivingEntity living && living.getVehicle() == this && this.canUseSelectedWeapon(living, weapon)) {
            return living;
        }
        this.seekControllerId = -1;
        this.seekInput = false;
        return null;
    }

    @Nullable
    private Entity findLookTarget(LivingEntity shooter, Vec3 direction, double range, double minDot, VehicleMissileProfile profile) {
        Vec3 eye = shooter.getEyePosition();
        Entity bestTarget = null;
        double bestScore = minDot;
        for (Entity candidate : this.level().getEntities(this, this.getBoundingBox().inflate(range), candidate -> candidate instanceof VehicleDecoyEntity || profile.canLock(candidate, shooter, this))) {
            if (!candidate.isAlive()) {
                continue;
            }
            Vec3 targetCenter = candidate instanceof LivingEntity living
                    ? living.getEyePosition()
                    : candidate.position().add(0.0D, candidate.getBbHeight() * 0.5D, 0.0D);
            Vec3 toTarget = targetCenter.subtract(eye);
            double distance = toTarget.length();
            if (distance <= 0.0D || distance > range) {
                continue;
            }
            double dot = toTarget.normalize().dot(direction);
            if (dot > bestScore && this.canSeeMissileTarget(eye, targetCenter, candidate)) {
                bestScore = dot;
                bestTarget = candidate;
            }
        }
        return bestTarget;
    }

    private boolean canSeeMissileTarget(Vec3 eye, Vec3 targetCenter, Entity candidate) {
        if (!(candidate instanceof VehicleDecoyEntity) && VehicleDecoyEntity.isSmokeBlockingTarget(candidate)) {
            return false;
        }
        HitResult hit = this.level().clip(new ClipContext(eye, targetCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS;
    }

    private double seekRange() {
        SeekInfo seek = this.vehicleData().defaults().seek();
        return seek.range() > 0.0D ? seek.range() : DEFAULT_SEEK_RANGE;
    }

    private double seekMinDot() {
        SeekInfo seek = this.vehicleData().defaults().seek();
        return seek.angle() > 0.0D ? Math.cos(Math.toRadians(seek.angle())) : DEFAULT_SEEK_MIN_DOT;
    }

    private boolean shouldWarnSeekTarget() {
        SeekInfo seek = this.vehicleData().defaults().seek();
        return seek.warnsTarget() || seek == SeekInfo.NONE;
    }

    private void tickDecoyCooldown() {
        if (this.decoyCooldown > 0) {
            this.decoyCooldown--;
        }
    }

    private void tickPendingFlareDecoys() {
        if (this.pendingFlareBurstTicks < 0) {
            return;
        }
        this.pendingFlareBurstTicks--;
        if (this.pendingFlareBurstTicks >= 0 && this.pendingFlareBurstTicks % FLARE_BURST_INTERVAL_TICKS == 0) {
            this.shootFlareDecoyPair(false);
        }
        if (this.pendingFlareBurstTicks <= 0) {
            this.pendingFlareBurstTicks = -1;
        }
    }

    private void tickRammingDamage() {
        if (this.ramDamageCooldown > 0) {
            this.ramDamageCooldown--;
            return;
        }
        CollisionLevel collisionLevel = this.vehicleData().defaults().collisionLevel();
        if (collisionLevel == CollisionLevel.NONE) {
            return;
        }
        VehicleType type = this.vehicleData().defaults().vehicleType();
        Vec3 movement = this.getDeltaMovement();
        double speed = type == VehicleType.HELICOPTER
                ? Math.max(movement.length(), Math.sqrt((this.getX() - this.xo) * (this.getX() - this.xo) + (this.getY() - this.yo) * (this.getY() - this.yo) + (this.getZ() - this.zo) * (this.getZ() - this.zo)))
                : movement.horizontalDistance();
        if (speed < RAM_DAMAGE_MIN_SPEED) {
            return;
        }
        float damage = this.ramDamageAmount(collisionLevel, speed);
        if (damage <= 0.0F) {
            return;
        }
        boolean damaged = false;
        for (LivingEntity target : this.level().getEntitiesOfClass(LivingEntity.class, this.partBounds().inflate(0.25D, 0.15D, 0.25D))) {
            if (!target.isAlive() || target.getVehicle() == this) {
                continue;
            }
            if (this.usesCustomObbEntityCollision() && this.obbCollisionCorrection(target.getBoundingBox()) == null) {
                continue;
            }
            DamageSource source = this.vehicleStrikeDamageSource();
            target.hurt(source, damage);
            GunnerFriendlyFireEvents.clearFriendlyVehicleStrikeTargetAfterDamage(target, source);
            damaged = true;
        }
        for (VehicleEntity target : this.level().getEntitiesOfClass(VehicleEntity.class, this.partBounds().inflate(0.35D, 0.2D, 0.35D))) {
            if (target == this || target.isRemoved()
                    || target.vehicleData().defaults().collisionLevel() == CollisionLevel.NONE) {
                continue;
            }
            if ((this.usesCustomObbEntityCollision() && this.obbCollisionCorrection(target.getBoundingBox()) == null)
                    || (target.usesCustomObbEntityCollision() && target.obbCollisionCorrection(this.getBoundingBox()) == null)) {
                continue;
            }
            double relativeSpeed = this.vehicleRelativeCollisionSpeed(target);
            if (relativeSpeed < VEHICLE_COLLISION_MIN_RELATIVE_SPEED) {
                continue;
            }
            if (this.damageVehicleEntityCollision(target, relativeSpeed)) {
                damaged = true;
            }
        }
        if (damaged) {
            this.ramDamageCooldown = RAM_DAMAGE_COOLDOWN_TICKS;
        }
    }

    private double vehicleRelativeCollisionSpeed(VehicleEntity other) {
        return this.vehicleRelativeCollisionSpeed(other, this.getDeltaMovement(), other.getDeltaMovement());
    }

    private double vehicleRelativeCollisionSpeed(VehicleEntity other, Vec3 selfMovement, Vec3 otherMovement) {
        Vec3 relative = selfMovement.subtract(otherMovement);
        VehicleType type = this.vehicleData().defaults().vehicleType();
        VehicleType otherType = other.vehicleData().defaults().vehicleType();
        if (type == VehicleType.HELICOPTER || type == VehicleType.AIRCRAFT || otherType == VehicleType.HELICOPTER || otherType == VehicleType.AIRCRAFT) {
            return relative.length();
        }
        return relative.horizontalDistance();
    }

    private boolean damageVehicleEntityCollision(VehicleEntity target, double relativeSpeed) {
        if (relativeSpeed < VEHICLE_COLLISION_MIN_RELATIVE_SPEED) {
            return false;
        }
        if (this.applyFatalHelicopterVehicleEntityImpact(target)) {
            return true;
        }
        float targetDamage = this.vehicleImpactDamage(relativeSpeed, 0.0D);
        float selfDamage = this.vehicleImpactDamage(relativeSpeed, 0.0D) * VEHICLE_COLLISION_SELF_DAMAGE_MULTIPLIER;
        boolean targetDamaged = targetDamage > 0.0F && target.hurtVehicleStrikeWithArmor(this.vehicleStrikeDamageSource(), targetDamage);
        boolean selfDamaged = selfDamage > 0.0F && this.hurtVehicleStrikeWithArmor(target.vehicleStrikeDamageSource(), selfDamage);
        if (targetDamaged || selfDamaged) {
            target.ramDamageCooldown = RAM_DAMAGE_COOLDOWN_TICKS;
            this.ramDamageCooldown = RAM_DAMAGE_COOLDOWN_TICKS;
            return true;
        }
        return targetDamage > 0.0F || selfDamage > 0.0F;
    }

    private boolean applyFatalHelicopterVehicleEntityImpact(VehicleEntity target) {
        boolean damaged = false;
        if (this.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER
                && this.lastTickSpeed >= HELICOPTER_FATAL_IMPACT_SPEED) {
            damaged |= this.hurtVehicleIgnoringArmor(target.vehicleStrikeDamageSource(), this.maxVehicleHealth() + 1.0F);
        }
        if (target.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER
                && target.lastTickSpeed >= HELICOPTER_FATAL_IMPACT_SPEED) {
            damaged |= target.hurtVehicleIgnoringArmor(this.vehicleStrikeDamageSource(), target.maxVehicleHealth() + 1.0F);
        }
        if (damaged) {
            target.ramDamageCooldown = RAM_DAMAGE_COOLDOWN_TICKS;
            this.ramDamageCooldown = RAM_DAMAGE_COOLDOWN_TICKS;
        }
        return damaged;
    }

    private void tickVehicleEntityCollisionResolution() {
        if (this.noPhysics
                || this.isRemoved()
                || this.vehicleData().defaults().collisionLevel() == CollisionLevel.NONE) {
            return;
        }
        for (VehicleEntity target : this.level().getEntitiesOfClass(VehicleEntity.class, this.partBounds().inflate(VEHICLE_ENTITY_COLLISION_SEARCH_EXPANSION))) {
            if (target == this || target.isRemoved() || target.noPhysics || this.getId() >= target.getId()
                    || target.vehicleData().defaults().collisionLevel() == CollisionLevel.NONE) {
                continue;
            }
            Vec3 correction = this.vehicleEntityCollisionCorrection(target);
            if (correction == null || correction.lengthSqr() <= 1.0E-8D) {
                continue;
            }
            this.applyVehicleEntityCollisionCorrection(target, correction);
        }
    }

    @Nullable
    private Vec3 vehicleEntityCollisionCorrection(VehicleEntity target) {
        Vec3 best = null;
        double bestLength = Double.POSITIVE_INFINITY;
        if (this.usesCustomObbEntityCollision()) {
            ObbCollisionCorrection correction = this.obbCollisionCorrection(target.getBoundingBox());
            if (correction != null) {
                best = correction.movement();
                bestLength = correction.depth();
            }
        }
        if (target.usesCustomObbEntityCollision()) {
            ObbCollisionCorrection correction = target.obbCollisionCorrection(this.getBoundingBox());
            if (correction != null && correction.depth() < bestLength) {
                best = correction.movement().scale(-1.0D);
                bestLength = correction.depth();
            }
        }
        if (best != null) {
            return best;
        }
        return this.aabbVehicleCollisionCorrection(target);
    }

    private boolean vehicleEntityCollisionIntersects(VehicleEntity target) {
        if (this.usesCustomObbEntityCollision() && this.obbCollisionCorrection(target.getBoundingBox()) != null) {
            return true;
        }
        if (target.usesCustomObbEntityCollision() && target.obbCollisionCorrection(this.getBoundingBox()) != null) {
            return true;
        }
        return this.getBoundingBox().intersects(target.getBoundingBox());
    }

    @Nullable
    private Vec3 aabbVehicleCollisionCorrection(VehicleEntity target) {
        AABB self = this.getBoundingBox();
        AABB other = target.getBoundingBox();
        double overlapX = Math.min(self.maxX, other.maxX) - Math.max(self.minX, other.minX);
        double overlapY = Math.min(self.maxY, other.maxY) - Math.max(self.minY, other.minY);
        double overlapZ = Math.min(self.maxZ, other.maxZ) - Math.max(self.minZ, other.minZ);
        if (overlapX <= 0.0D || overlapY <= 0.0D || overlapZ <= 0.0D) {
            return null;
        }
        double centerX = (other.minX + other.maxX) * 0.5D - (self.minX + self.maxX) * 0.5D;
        double centerZ = (other.minZ + other.maxZ) * 0.5D - (self.minZ + self.maxZ) * 0.5D;
        if (overlapX < overlapZ) {
            return new Vec3((centerX >= 0.0D ? 1.0D : -1.0D) * (overlapX + OBB_ENTITY_COLLISION_EPSILON), 0.0D, 0.0D);
        }
        return new Vec3(0.0D, 0.0D, (centerZ >= 0.0D ? 1.0D : -1.0D) * (overlapZ + OBB_ENTITY_COLLISION_EPSILON));
    }

    private void applyVehicleEntityCollisionCorrection(VehicleEntity target, Vec3 correction) {
        this.stopVehicleEntitySqueezeVelocity(target, correction);
    }

    private void stopVehicleEntitySqueezeVelocity(VehicleEntity target, Vec3 correction) {
        Vec3 horizontal = new Vec3(correction.x(), 0.0D, correction.z());
        if (horizontal.lengthSqr() <= 1.0E-8D) {
            return;
        }
        Vec3 normal = horizontal.normalize();
        double selfAlong = this.getDeltaMovement().dot(normal);
        double targetAlong = target.getDeltaMovement().dot(normal);
        double closingSpeed = selfAlong - targetAlong;
        if (closingSpeed <= 0.0D) {
            return;
        }
        if (selfAlong > 0.0D) {
            this.setDeltaMovement(this.getDeltaMovement().subtract(normal.scale(selfAlong)));
            this.hurtMarked = true;
        }
        if (targetAlong < 0.0D) {
            target.setDeltaMovement(target.getDeltaMovement().subtract(normal.scale(targetAlong)));
            target.hurtMarked = true;
        }
    }

    private double vehicleCollisionMass() {
        double levelWeight = switch (this.vehicleData().defaults().collisionLevel()) {
            case LIGHT -> 0.8D;
            case MEDIUM -> 1.0D;
            case HEAVY -> 1.35D;
            case NONE -> 0.6D;
        };
        return Math.max(1.0D, this.vehicleData().defaults().maxHealth() * levelWeight);
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        this.preExplosionKnockbackVelocity = this.getDeltaMovement();
        this.preExplosionKnockbackTick = this.tickCount;
        return false;
    }

    @Override
    public void onExplosionHit(Entity source) {
        Vec3 before = this.preExplosionKnockbackTick == this.tickCount ? this.preExplosionKnockbackVelocity : null;
        this.preExplosionKnockbackVelocity = null;
        this.preExplosionKnockbackTick = -1;
        if (before == null) {
            return;
        }
        Vec3 impulse = this.getDeltaMovement().subtract(before);
        double scale = Mth.clamp(VEHICLE_EXPLOSION_KNOCKBACK_REFERENCE_MASS / this.vehicleCollisionMass(),
                VEHICLE_EXPLOSION_KNOCKBACK_MIN_SCALE,
                VEHICLE_EXPLOSION_KNOCKBACK_MAX_SCALE);
        this.setDeltaMovement(before.add(impulse.scale(scale)));
        this.hurtMarked = true;
    }

    private void tickObbEntityCollisionSupport() {
        if (!this.usesCustomObbEntityCollision()
                || this.noPhysics
                || this.isRemoved()) {
            return;
        }
        Entity localPlayer = this.level().isClientSide() ? localClientPlayer() : null;
        for (Entity entity : this.level().getEntities(this, this.partBounds().inflate(0.35D), this::canSupportWithObbCollision)) {
            if (this.level().isClientSide() && entity != localPlayer) {
                continue;
            }
            ObbCollisionCorrection correction = this.obbCollisionCorrection(entity.getBoundingBox());
            if (correction == null) {
                continue;
            }
            entity.setPos(entity.position().add(correction.movement()));
            if (correction.movement().y > 0.0D) {
                entity.setDeltaMovement(entity.getDeltaMovement().x * 0.2D, Math.max(entity.getDeltaMovement().y, 0.0D), entity.getDeltaMovement().z * 0.2D);
                entity.setOnGround(true);
                entity.fallDistance = 0.0F;
            } else {
                entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.2D, 0.2D, 0.2D));
            }
            entity.needsSync = true;
        }
    }

    private boolean canSupportWithObbCollision(Entity entity) {
        return entity.isAlive()
                && entity.isPushable()
                && !entity.noPhysics
                && entity.getVehicle() == null
                && !this.hasPassenger(entity)
                && !(entity instanceof Player player && player.isSpectator());
    }

    @Nullable
    private ObbCollisionCorrection obbCollisionCorrection(AABB entityBox) {
        LocalBounds entityBounds = this.toLocalBounds(entityBox);
        ObbCollisionCorrection best = null;
        for (OBBInfo.Box box : this.vehicleData().defaults().obb().boxes()) {
            double boxMinX = box.x() - box.halfWidth();
            double boxMaxX = box.x() + box.halfWidth();
            double boxMinY = box.y() - box.halfHeight();
            double boxMaxY = box.y() + box.halfHeight();
            double boxMinZ = box.z() - box.halfDepth();
            double boxMaxZ = box.z() + box.halfDepth();
            double overlapX = Math.min(entityBounds.maxX(), boxMaxX) - Math.max(entityBounds.minX(), boxMinX);
            double overlapY = Math.min(entityBounds.maxY(), boxMaxY) - Math.max(entityBounds.minY(), boxMinY);
            double overlapZ = Math.min(entityBounds.maxZ(), boxMaxZ) - Math.max(entityBounds.minZ(), boxMinZ);
            if (overlapX <= 0.0D || overlapY <= 0.0D || overlapZ <= 0.0D) {
                continue;
            }
            double entityCenterX = (entityBounds.minX() + entityBounds.maxX()) * 0.5D;
            double entityCenterZ = (entityBounds.minZ() + entityBounds.maxZ()) * 0.5D;
            Vec3 localCorrection;
            double depth;
            if (entityBounds.minY() >= boxMaxY - 0.35D && overlapY <= Math.min(overlapX, overlapZ) + 0.2D) {
                depth = overlapY + OBB_ENTITY_COLLISION_EPSILON;
                localCorrection = new Vec3(0.0D, depth, 0.0D);
            } else if (overlapX < overlapZ) {
                double direction = entityCenterX >= box.x() ? 1.0D : -1.0D;
                depth = overlapX + OBB_ENTITY_COLLISION_EPSILON;
                localCorrection = new Vec3(direction * depth, 0.0D, 0.0D);
            } else {
                double direction = entityCenterZ >= box.z() ? 1.0D : -1.0D;
                depth = overlapZ + OBB_ENTITY_COLLISION_EPSILON;
                localCorrection = new Vec3(0.0D, 0.0D, direction * depth);
            }
            Vec3 worldCorrection = this.rotateLocalOffset(localCorrection.x, localCorrection.y, localCorrection.z);
            if (best == null || depth < best.depth()) {
                best = new ObbCollisionCorrection(worldCorrection, depth);
            }
        }
        return best;
    }

    private LocalBounds toLocalBounds(AABB box) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    Vec3 local = this.toLocalVehicleSpace(new Vec3(x, y, z));
                    minX = Math.min(minX, local.x);
                    maxX = Math.max(maxX, local.x);
                    minY = Math.min(minY, local.y);
                    maxY = Math.max(maxY, local.y);
                    minZ = Math.min(minZ, local.z);
                    maxZ = Math.max(maxZ, local.z);
                }
            }
        }
        return new LocalBounds(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private void tickHelicopterRotorGroundDamage() {
        if (this.vehicleData().defaults().vehicleType() != VehicleType.HELICOPTER
                || this.onGround()
                || this.propellerSpeed() < HELICOPTER_ROTOR_DAMAGE_MIN_SPEED) {
            return;
        }
        if (this.isUpperMainRotorTouchingGround()) {
            this.hurtVehicleIgnoringArmor(this.vehicleStrikeDamageSource(), HELICOPTER_ROTOR_GROUND_DAMAGE);
        }
    }

    private boolean isUpperMainRotorTouchingGround() {
        RotorContactInfo rotor = this.upperMainRotorContactInfo();
        if (rotor.radius() <= 0.0D) {
            return false;
        }
        if (this.rotorSampleIntersectsBlock(rotor.centerX(), rotor.centerY(), rotor.centerZ())) {
            return true;
        }
        for (int ring = 1; ring <= 4; ring++) {
            double radius = rotor.radius() * ring / 4.0D;
            int samples = ring * 12;
            for (int sample = 0; sample < samples; sample++) {
                double angle = Math.PI * 2.0D * sample / samples;
                double localX = rotor.centerX() + Math.cos(angle) * radius;
                double localZ = rotor.centerZ() + Math.sin(angle) * radius;
                if (this.rotorSampleIntersectsBlock(localX, rotor.centerY(), localZ)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean rotorSampleIntersectsBlock(double localX, double localY, double localZ) {
        Vec3 world = this.position().add(this.rotateLocalOffsetWithPose(localX, localY, localZ, 1.0F));
        AABB sampleBox = new AABB(
                world.x - HELICOPTER_ROTOR_CONTACT_SAMPLE_RADIUS,
                world.y - HELICOPTER_ROTOR_CONTACT_SAMPLE_HALF_HEIGHT,
                world.z - HELICOPTER_ROTOR_CONTACT_SAMPLE_RADIUS,
                world.x + HELICOPTER_ROTOR_CONTACT_SAMPLE_RADIUS,
                world.y + HELICOPTER_ROTOR_CONTACT_SAMPLE_HALF_HEIGHT,
                world.z + HELICOPTER_ROTOR_CONTACT_SAMPLE_RADIUS
        );
        return this.intersectsBlockCollision(sampleBox);
    }

    private boolean intersectsBlockCollision(AABB box) {
        VoxelShape boxShape = Shapes.create(box);
        int minX = Mth.floor(box.minX);
        int minY = Mth.floor(box.minY);
        int minZ = Mth.floor(box.minZ);
        int maxX = Mth.floor(box.maxX - 1.0E-7D);
        int maxY = Mth.floor(box.maxY - 1.0E-7D);
        int maxZ = Mth.floor(box.maxZ - 1.0E-7D);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = this.level().getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    VoxelShape collision = state.getCollisionShape(this.level(), pos);
                    if (collision.isEmpty()
                            || !collision.bounds().move(pos.getX(), pos.getY(), pos.getZ()).intersects(box)
                            || !Shapes.joinIsNotEmpty(collision.move(pos.getX(), pos.getY(), pos.getZ()), boxShape, BooleanOp.AND)) {
                        continue;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private RotorContactInfo upperMainRotorContactInfo() {
        return switch (this.vehicleDataId().getPath()) {
            case "ah6" -> new RotorContactInfo(0.0D, 56.64911D / 16.0D, 0.0D, 5.5D);
            case "mi28" -> new RotorContactInfo(0.0D, 61.215D / 16.0D, -3.8046D / 16.0D, 8.85D);
            default -> new RotorContactInfo(0.0D, 18.0D / 16.0D, -2.0D / 16.0D, 2.0D);
        };
    }

    private float ramDamageAmount(CollisionLevel collisionLevel, double speed) {
        float baseDamage = switch (collisionLevel) {
            case LIGHT -> 3.0F;
            case MEDIUM -> 5.0F;
            case HEAVY -> 8.0F;
            case NONE -> 0.0F;
        };
        if (this.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER) {
            return baseDamage * (float) Mth.clamp((speed - 0.12D) / 0.18D, 0.6D, 3.0D);
        }
        return baseDamage * (float) Mth.clamp(speed / 0.35D, 0.35D, 1.5D);
    }

    private void tryDeployDecoy(ServerPlayer player) {
        if (this.decoyCooldown > 0) {
            return;
        }
        if (!this.hasBuiltInDecoy() && !this.consumeAmmo(FLARE_AMMO)) {
            return;
        }
        if (this.shouldDeploySmokeDecoy()) {
            this.deploySmokeDecoys(player);
            this.decoyCooldown = LAND_DECOY_COOLDOWN_TICKS;
            return;
        }
        this.shootFlareDecoyPair(true);
        this.pendingFlareBurstTicks = FLARE_BURST_LAST_DELAY_TICKS;
        this.decoyCooldown = FLARE_DECOY_COOLDOWN_TICKS;
    }

    private boolean shouldDeploySmokeDecoy() {
        VehicleType type = this.vehicleData().defaults().vehicleType();
        return this.hasBuiltInDecoy() && type != VehicleType.HELICOPTER && type != VehicleType.AIRCRAFT;
    }

    private void deploySmokeDecoys(ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 position = this.position().add(0.0D, this.getBbHeight(), 0.0D);
        for (int index = 0; index < 8; index++) {
            double yaw = Math.toRadians(-78.75D + 22.5D * index);
            Vec3 direction = look.yRot((float) yaw).normalize();
            this.level().addFreshEntity(VehicleDecoyEntity.smoke(this.level(), this, position, direction, 4.0F, 8.0F));
        }
        this.level().playSound(null, this, SoundEvents.FIRE_EXTINGUISH, this.getSoundSource(), 1.0F, 1.0F);
    }

    private void shootFlareDecoyPair(boolean first) {
        Vec3 position = this.position().add(this.getDeltaMovement()).add(0.0D, 0.5D, 0.0D);
        this.shootFlareDecoy(position, this.rotateLocalOffsetWithPose(1.0D, -0.2D, 0.6D, 1.0F), first);
        this.shootFlareDecoy(position, this.rotateLocalOffsetWithPose(-1.0D, -0.2D, 0.6D, 1.0F), first);
    }

    private void shootFlareDecoy(Vec3 position, Vec3 direction, boolean first) {
        if (direction.lengthSqr() < 1.0E-4D) {
            return;
        }
        float velocity = (float) (this.getDeltaMovement().length() * 0.3D + 0.7D);
        this.level().addFreshEntity(VehicleDecoyEntity.flare(this.level(), this, position, direction.normalize(), velocity, 8.0F));
        this.level().playSound(null, this, first ? SoundEvents.FIREWORK_ROCKET_LAUNCH : SoundEvents.FIRECHARGE_USE, this.getSoundSource(), 2.0F, 1.0F);
    }

    private void tickInventoryEnergyRecharge() {
        if (this.maxVehicleEnergy() <= 0 || this.vehicleEnergy() >= this.maxVehicleEnergy()) {
            return;
        }
        this.energyRechargeTick++;
        if (this.energyRechargeTick < ENERGY_RECHARGE_INTERVAL) {
            return;
        }
        this.energyRechargeTick = 0;
        if (this.consumeInventoryItem(net.minecraft.world.item.Items.REDSTONE, 1)) {
            this.entityData.set(DATA_ENERGY, Math.min(this.maxVehicleEnergy(), this.vehicleEnergy() + REDSTONE_ENERGY_VALUE));
        }
    }

    private boolean consumeInventoryItem(Item item, int count) {
        int remaining = count;
        for (int slot = 0; slot < this.inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = this.inventory.getItem(slot);
            if (!stack.is(item)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            if (stack.isEmpty()) {
                this.inventory.setItem(slot, ItemStack.EMPTY);
            }
            remaining -= removed;
        }
        if (remaining == 0) {
            this.inventory.setChanged();
            return true;
        }
        return false;
    }

    private boolean selectWeaponSlot(Player player, int slot) {
        var weapons = this.vehicleData().defaults().weapons();
        if (slot < 0 || slot >= weapons.size()) {
            return false;
        }
        int seatIndex = this.seatIndexForPassenger(player, this.getPassengers().indexOf(player));
        if (!weapons.get(slot).usableBySeat(seatIndex)) {
            return false;
        }
        if (slot == this.selectedVehicleWeaponIndexForSeat(seatIndex)) {
            this.setSelectedWeaponSlotForSeat(seatIndex, slot);
            if (slot != this.selectedVehicleWeaponIndex()) {
                this.entityData.set(DATA_SELECTED_WEAPON, slot);
                if (this.level().isClientSide()) {
                    this.syncPerSeatSelectedWeaponState();
                } else {
                    this.syncSelectedWeaponAmmoState();
                }
            }
            return false;
        }
        this.entityData.set(DATA_SELECTED_WEAPON, slot);
        this.setSelectedWeaponSlotForSeat(seatIndex, slot);
        if (this.level().isClientSide()) {
            this.syncPerSeatSelectedWeaponState();
        } else {
            this.syncSelectedWeaponAmmoState();
        }
        return true;
    }

    private void selectFallbackWeaponFor(Player player, VehicleInput input) {
        if (!this.hasVehicleWeapons() || !this.shouldFallbackSelectWeapon(player, input)) {
            return;
        }
        int fallbackIndex = this.getPassengers().indexOf(player);
        if (fallbackIndex < 0) {
            return;
        }
        int selectedWeapon = this.selectedVehicleWeaponIndexForSeat(this.seatIndexForPassenger(player, fallbackIndex));
        if (selectedWeapon >= 0 && selectedWeapon != this.selectedVehicleWeaponIndex()) {
            this.selectWeaponSlot(player, selectedWeapon);
        }
    }

    private boolean shouldFallbackSelectWeapon(Player player, VehicleInput input) {
        int fallbackIndex = this.getPassengers().indexOf(player);
        if (fallbackIndex < 0 || !this.hasWeaponUsableBySeat(this.seatIndexForPassenger(player, fallbackIndex))) {
            return false;
        }
        return input.fire()
                || input.reload()
                || input.seekTarget();
    }

    private int countRifleAmmo() {
        return this.countAmmo(RIFLE_AMMO);
    }

    private int countAmmo(Identifier ammoId) {
        Item ammo = this.resolveAmmoItem(ammoId);
        if (ammo == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
            ItemStack stack = this.inventory.getItem(slot);
            if (stack.is(ammo)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private boolean hasAmmo(Identifier ammoId) {
        return this.countAmmo(ammoId) > 0;
    }

    private boolean usesSharedVehicleReloadSystem() {
        Identifier weaponId = this.selectedVehicleWeaponId();
        if (weaponId == null) {
            return false;
        }
        GunStats stats = VehicleWeaponStats.get(weaponId);
        return stats != null && stats.usesMagazine() && !stats.isInventoryFed();
    }

    private boolean usesSharedVehicleReloadSystem(Entity passenger) {
        Identifier weaponId = this.selectedVehicleWeaponId(passenger);
        if (weaponId == null) {
            return false;
        }
        GunStats stats = VehicleWeaponStats.get(weaponId);
        return stats != null && stats.usesMagazine() && !stats.isInventoryFed();
    }

    private boolean usesSharedVehicleReloadSystem(int slot) {
        var weapons = this.vehicleData().defaults().weapons();
        if (slot < 0 || slot >= weapons.size()) {
            return false;
        }
        GunStats stats = VehicleWeaponStats.get(weapons.get(slot).weaponId());
        return stats != null && stats.usesMagazine() && !stats.isInventoryFed();
    }

    private int weaponLoadedAmmo(int slot) {
        return Math.max(0, this.loadedAmmoByWeaponSlot.getOrDefault(slot, 0));
    }

    private void setWeaponLoadedAmmo(int slot, int ammo) {
        if (ammo <= 0) {
            this.loadedAmmoByWeaponSlot.remove(slot);
        } else {
            this.loadedAmmoByWeaponSlot.put(slot, ammo);
        }
    }

    private int selectedWeaponLoadedAmmo() {
        return this.weaponLoadedAmmo(this.selectedVehicleWeaponIndex());
    }

    private int selectedWeaponLoadedAmmo(Entity passenger) {
        return this.weaponLoadedAmmo(this.selectedVehicleWeaponIndex(passenger));
    }

    private int selectedWeaponReserveAmmo() {
        VehicleWeaponInfo weapon = this.selectedWeapon();
        return weapon == null ? 0 : this.countAmmo(weapon.ammoId());
    }

    private int selectedWeaponReserveAmmo(Entity passenger) {
        VehicleWeaponInfo weapon = this.selectedWeapon(passenger);
        return weapon == null ? 0 : this.countAmmo(weapon.ammoId());
    }

    private int selectedWeaponMagazineSize() {
        Identifier weaponId = this.selectedVehicleWeaponId();
        GunStats stats = weaponId == null ? null : VehicleWeaponStats.get(weaponId);
        return stats == null ? 0 : Math.max(0, stats.magazineSize());
    }

    private int selectedWeaponMagazineSize(Entity passenger) {
        Identifier weaponId = this.selectedVehicleWeaponId(passenger);
        GunStats stats = weaponId == null ? null : VehicleWeaponStats.get(weaponId);
        return stats == null ? 0 : Math.max(0, stats.magazineSize());
    }

    /** How long this weapon takes to reload on this vehicle: quicker than by hand, and quicker again on a helicopter. */
    private int vehicleReloadDuration(@Nullable GunStats stats) {
        if (stats == null) {
            return 0;
        }
        float scale = VEHICLE_RELOAD_TIME_SCALE;
        if (this.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER) {
            scale *= HELICOPTER_RELOAD_TIME_SCALE;
        }
        return Math.max(0, Math.round(stats.totalReloadTime() * scale));
    }

    private boolean isWeaponSlotReloading(int slot) {
        return this.weaponReloadTicks(slot) > 0;
    }

    private int weaponReloadTicks(int slot) {
        return slot < 0 ? 0 : this.reloadTicksByWeaponSlot.getOrDefault(slot, 0);
    }

    /** The lowest weapon slot that is reloading, or -1. */
    private int firstReloadingWeaponSlot() {
        int first = -1;
        for (var entry : this.reloadTicksByWeaponSlot.entrySet()) {
            if (entry.getValue() > 0 && (first < 0 || entry.getKey() < first)) {
                first = entry.getKey();
            }
        }
        return first;
    }

    /** Whether this weapon slot could start a reload now: it takes magazines, is not full or already reloading, and there is ammo aboard. */
    private boolean canReloadWeaponSlot(int slot) {
        var weapons = this.vehicleData().defaults().weapons();
        if (slot < 0 || slot >= weapons.size() || !this.usesSharedVehicleReloadSystem(slot) || this.isWeaponSlotReloading(slot)) {
            return false;
        }
        GunStats stats = VehicleWeaponStats.get(weapons.get(slot).weaponId());
        int magazineSize = stats == null ? 0 : Math.max(0, stats.magazineSize());
        return magazineSize > 0
                && this.weaponLoadedAmmo(slot) < magazineSize
                && this.countAmmo(weapons.get(slot).ammoId()) > 0;
    }

    private boolean canReloadSelectedWeapon() {
        return this.canReloadWeaponSlot(this.selectedVehicleWeaponIndex());
    }

    private boolean canReloadSelectedWeapon(Entity passenger) {
        return this.canReloadWeaponSlot(this.selectedVehicleWeaponIndex(passenger));
    }

    private void startWeaponReload(Entity passenger) {
        this.startWeaponSlotReload(this.selectedVehicleWeaponIndex(passenger));
    }

    private void startWeaponSlotReload(int slot) {
        if (!this.canReloadWeaponSlot(slot)) {
            return;
        }
        VehicleWeaponInfo weapon = this.vehicleData().defaults().weapons().get(slot);
        GunStats stats = VehicleWeaponStats.get(weapon.weaponId());
        int reloadDuration = this.vehicleReloadDuration(stats);
        if (reloadDuration <= 0) {
            this.finishWeaponReload(slot);
            return;
        }
        this.reloadTicksByWeaponSlot.put(slot, reloadDuration);
        this.syncSelectedWeaponAmmoState();
        this.playWeaponReloadSound(weapon, stats, true);
    }

    private void cancelWeaponReload() {
        if (this.reloadTicksByWeaponSlot.isEmpty()) {
            return;
        }
        this.reloadTicksByWeaponSlot.clear();
        this.syncSelectedWeaponAmmoState();
    }

    /**
     * Counts every weapon's reload down on its own, so a launcher emptied and switched away from is loaded by the time
     * it is selected again, and starts the reload of any empty weapon that someone aboard can fire.
     */
    private void tickWeaponReload() {
        if (!this.reloadTicksByWeaponSlot.isEmpty()) {
            java.util.List<Integer> finished = new java.util.ArrayList<>();
            for (var entry : this.reloadTicksByWeaponSlot.entrySet()) {
                int ticks = entry.getValue() - 1;
                entry.setValue(ticks);
                if (ticks <= 0) {
                    finished.add(entry.getKey());
                }
            }
            for (int slot : finished) {
                this.reloadTicksByWeaponSlot.remove(slot);
                this.finishWeaponReload(slot);
            }
            this.syncSelectedWeaponAmmoState();
        }
        var weapons = this.vehicleData().defaults().weapons();
        for (int slot = 0; slot < weapons.size(); slot++) {
            if (this.weaponLoadedAmmo(slot) <= 0 && this.canReloadWeaponSlot(slot) && this.hasCrewForWeapon(weapons.get(slot))) {
                this.startWeaponSlotReload(slot);
            }
        }
    }

    /** Whether anyone aboard sits in a seat that can fire this weapon. */
    private boolean hasCrewForWeapon(VehicleWeaponInfo weapon) {
        var passengers = this.getPassengers();
        for (int index = 0; index < passengers.size(); index++) {
            Entity passenger = passengers.get(index);
            if (passenger instanceof LivingEntity && weapon.usableBySeat(this.seatIndexForPassenger(passenger, index))) {
                return true;
            }
        }
        return false;
    }

    public boolean canReloadSelectedVehicleWeapon() {
        return this.canReloadSelectedWeapon();
    }

    public boolean canReloadSelectedVehicleWeapon(Entity passenger) {
        return this.canReloadSelectedWeapon(passenger);
    }

    public int selectedVehicleWeaponMagazineSize() {
        return this.selectedWeaponMagazineSize();
    }

    public int selectedVehicleWeaponMagazineSize(Entity passenger) {
        return this.selectedWeaponMagazineSize(passenger);
    }

    private void finishWeaponReload(int slot) {
        var weapons = this.vehicleData().defaults().weapons();
        if (slot < 0 || slot >= weapons.size()) {
            this.syncSelectedWeaponAmmoState();
            return;
        }
        VehicleWeaponInfo weapon = weapons.get(slot);
        GunStats stats = VehicleWeaponStats.get(weapon.weaponId());
        int magazineSize = stats == null ? 0 : Math.max(0, stats.magazineSize());
        int loaded = this.weaponLoadedAmmo(slot);
        int reserve = this.countAmmo(weapon.ammoId());
        int transfer = Math.min(Math.max(0, magazineSize - loaded), reserve);
        if (transfer > 0 && this.consumeAmmo(weapon.ammoId(), transfer)) {
            this.setWeaponLoadedAmmo(slot, loaded + transfer);
        }
        this.playWeaponReloadSound(weapon, stats, false);
        this.syncSelectedWeaponAmmoState();
    }

    private void syncSelectedWeaponAmmoState() {
        if (!this.hasVehicleWeapons()) {
            this.entityData.set(DATA_SELECTED_WEAPON_AMMO, 0);
            this.entityData.set(DATA_SELECTED_WEAPON_RESERVE_AMMO, 0);
            this.entityData.set(DATA_SELECTED_WEAPON_RELOADING, false);
            this.entityData.set(DATA_SELECTED_WEAPON_RELOAD_TICKS, 0);
            this.entityData.set(DATA_RELOADING_WEAPON, -1);
            this.entityData.set(DATA_WEAPON_RELOAD_TICKS_BY_SLOT, "");
            this.entityData.set(DATA_SELECTED_WEAPONS_BY_SEAT, "");
            this.entityData.set(DATA_WEAPON_AMMO_BY_SLOT, "");
            this.entityData.set(DATA_WEAPON_RESERVE_AMMO_BY_SLOT, "");
            return;
        }
        int selectedSlot = this.selectedVehicleWeaponIndex();
        VehicleWeaponInfo weapon = this.selectedWeapon();
        if (!this.usesSharedVehicleReloadSystem()) {
            this.entityData.set(DATA_SELECTED_WEAPON_AMMO, weapon == null ? 0 : this.countAmmo(weapon.ammoId()));
            this.entityData.set(DATA_SELECTED_WEAPON_RESERVE_AMMO, 0);
            this.entityData.set(DATA_SELECTED_WEAPON_RELOADING, false);
            this.entityData.set(DATA_SELECTED_WEAPON_RELOAD_TICKS, 0);
            this.entityData.set(DATA_RELOADING_WEAPON, -1);
            this.syncPerSeatSelectedWeaponState();
            this.syncWeaponAmmoMaps();
            return;
        }
        this.entityData.set(DATA_SELECTED_WEAPON_AMMO, this.weaponLoadedAmmo(selectedSlot));
        this.entityData.set(DATA_SELECTED_WEAPON_RESERVE_AMMO, this.selectedWeaponReserveAmmo());
        this.entityData.set(DATA_SELECTED_WEAPON_RELOADING, this.isWeaponSlotReloading(selectedSlot));
        this.entityData.set(DATA_SELECTED_WEAPON_RELOAD_TICKS, this.weaponReloadTicks(selectedSlot));
        this.entityData.set(DATA_RELOADING_WEAPON, this.firstReloadingWeaponSlot());
        this.syncPerSeatSelectedWeaponState();
        this.syncWeaponAmmoMaps();
    }

    private void syncPerSeatSelectedWeaponState() {
        int seatCount = this.vehicleData().defaults().seats().size();
        StringBuilder builder = new StringBuilder();
        for (int seatIndex = 0; seatIndex < seatCount; seatIndex++) {
            if (seatIndex > 0) {
                builder.append(',');
            }
            builder.append(this.selectedVehicleWeaponIndexForSeat(seatIndex));
        }
        this.entityData.set(DATA_SELECTED_WEAPONS_BY_SEAT, builder.toString());
    }

    private void syncWeaponAmmoMaps() {
        var weapons = this.vehicleData().defaults().weapons();
        StringBuilder loaded = new StringBuilder();
        StringBuilder reserve = new StringBuilder();
        StringBuilder reloading = new StringBuilder();
        for (int slot = 0; slot < weapons.size(); slot++) {
            if (slot > 0) {
                loaded.append(',');
                reserve.append(',');
                reloading.append(',');
            }
            VehicleWeaponInfo weapon = weapons.get(slot);
            loaded.append(this.usesSharedVehicleReloadSystem(slot) ? this.weaponLoadedAmmo(slot) : this.countAmmo(weapon.ammoId()));
            reserve.append(this.usesSharedVehicleReloadSystem(slot) ? this.countAmmo(weapon.ammoId()) : 0);
            reloading.append(this.weaponReloadTicks(slot));
        }
        this.entityData.set(DATA_WEAPON_AMMO_BY_SLOT, loaded.toString());
        this.entityData.set(DATA_WEAPON_RESERVE_AMMO_BY_SLOT, reserve.toString());
        this.entityData.set(DATA_WEAPON_RELOAD_TICKS_BY_SLOT, reloading.toString());
    }

    private int weaponAmmoFromSyncedData(int slot) {
        return this.valueFromSyncedIntList(this.entityData.get(DATA_WEAPON_AMMO_BY_SLOT), slot, 0);
    }

    private int weaponReserveAmmoFromSyncedData(int slot) {
        return this.valueFromSyncedIntList(this.entityData.get(DATA_WEAPON_RESERVE_AMMO_BY_SLOT), slot, 0);
    }

    private int selectedWeaponFromSyncedSeatData(int seatIndex) {
        return this.valueFromSyncedIntList(this.entityData.get(DATA_SELECTED_WEAPONS_BY_SEAT), seatIndex, -1);
    }

    private int valueFromSyncedIntList(String value, int index, int fallback) {
        if (index < 0 || value == null || value.isEmpty()) {
            return fallback;
        }
        int currentIndex = 0;
        int start = 0;
        for (int cursor = 0; cursor <= value.length(); cursor++) {
            if (cursor == value.length() || value.charAt(cursor) == ',') {
                if (currentIndex == index) {
                    try {
                        return Integer.parseInt(value.substring(start, cursor));
                    } catch (NumberFormatException ignored) {
                        return fallback;
                    }
                }
                currentIndex++;
                start = cursor + 1;
            }
        }
        return fallback;
    }

    private boolean hasEnergy(int amount) {
        return amount <= 0 || this.vehicleEnergy() >= amount;
    }

    private boolean consumeAmmo(Identifier ammoId) {
        return this.consumeAmmo(ammoId, 1);
    }

    private boolean consumeAmmo(Identifier ammoId, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (this.countAmmo(ammoId) < amount) {
            return false;
        }
        Item ammo = this.resolveAmmoItem(ammoId);
        if (ammo == null) {
            return false;
        }
        int remaining = amount;
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
            ItemStack stack = this.inventory.getItem(slot);
            if (!stack.is(ammo)) {
                continue;
            }
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            if (stack.isEmpty()) {
                this.inventory.setItem(slot, ItemStack.EMPTY);
            }
            remaining -= removed;
            if (remaining == 0) {
                this.inventory.setChanged();
                return true;
            }
        }
        return false;
    }

    @Nullable
    private Item resolveAmmoItem(Identifier ammoId) {
        var modAmmo = ModItems.AMMO.get(ammoId);
        if (modAmmo != null) {
            return modAmmo.get();
        }
        return BuiltInRegistries.ITEM.getOptional(ammoId).orElse(null);
    }

    @Nullable
    private VehicleWeaponInfo selectedWeapon() {
        var weapons = this.vehicleData().defaults().weapons();
        if (!this.hasVehicleWeapons() || weapons.isEmpty()) {
            return null;
        }
        int index = Mth.clamp(this.entityData.get(DATA_SELECTED_WEAPON), 0, weapons.size() - 1);
        return weapons.get(index);
    }

    @Nullable
    private VehicleWeaponInfo selectedWeapon(Entity passenger) {
        var weapons = this.vehicleData().defaults().weapons();
        int index = this.selectedVehicleWeaponIndex(passenger);
        if (!this.hasVehicleWeapons() || index < 0 || index >= weapons.size()) {
            return null;
        }
        return weapons.get(index);
    }

    private boolean selectWeaponFor(Player player, int direction) {
        var weapons = this.vehicleData().defaults().weapons();
        if (weapons.isEmpty()) {
            return false;
        }
        int seatIndex = this.seatIndexForPassenger(player, this.getPassengers().indexOf(player));
        int current = this.selectedVehicleWeaponIndexForSeat(seatIndex);
        if (current < 0) {
            current = 0;
        }
        int step = direction < 0 ? -1 : 1;
        for (int offset = 1; offset <= weapons.size(); offset++) {
            int next = Mth.positiveModulo(current + offset * step, weapons.size());
            if (weapons.get(next).usableBySeat(seatIndex)) {
                if (next == current) {
                    this.setSelectedWeaponSlotForSeat(seatIndex, next);
                    if (next != this.selectedVehicleWeaponIndex()) {
                        this.entityData.set(DATA_SELECTED_WEAPON, next);
                        if (this.level().isClientSide()) {
                            this.syncPerSeatSelectedWeaponState();
                        } else {
                            this.syncSelectedWeaponAmmoState();
                        }
                    }
                    return false;
                }
                this.entityData.set(DATA_SELECTED_WEAPON, next);
                this.setSelectedWeaponSlotForSeat(seatIndex, next);
                if (this.level().isClientSide()) {
                    this.syncPerSeatSelectedWeaponState();
                } else {
                    this.syncSelectedWeaponAmmoState();
                }
                return true;
            }
        }
        return false;
    }

    private void setSelectedWeaponSlotForSeat(int seatIndex, int slot) {
        var weapons = this.vehicleData().defaults().weapons();
        if (seatIndex < 0 || slot < 0 || slot >= weapons.size()) {
            return;
        }
        if (weapons.get(slot).usableBySeat(seatIndex)) {
            this.selectedWeaponSlotBySeat.put(seatIndex, slot);
        }
    }

    private int selectedVehicleWeaponIndexForSeat(int seatIndex) {
        if (seatIndex < 0) {
            return -1;
        }
        Integer selectedSlot = this.selectedWeaponSlotBySeat.get(seatIndex);
        var weapons = this.vehicleData().defaults().weapons();
        if (selectedSlot != null
                && selectedSlot >= 0
                && selectedSlot < weapons.size()
                && weapons.get(selectedSlot).usableBySeat(seatIndex)) {
            return selectedSlot;
        }
        int syncedSlot = this.selectedWeaponFromSyncedSeatData(seatIndex);
        if (syncedSlot >= 0 && syncedSlot < weapons.size() && weapons.get(syncedSlot).usableBySeat(seatIndex)) {
            return syncedSlot;
        }
        return this.firstUsableWeaponSlotForSeat(seatIndex);
    }

    private int firstUsableWeaponSlotForSeat(int seatIndex) {
        var weapons = this.vehicleData().defaults().weapons();
        for (int index = 0; index < weapons.size(); index++) {
            if (weapons.get(index).usableBySeat(seatIndex)) {
                return index;
            }
        }
        return -1;
    }

    private boolean canUseSelectedWeapon(LivingEntity passenger, VehicleWeaponInfo weapon) {
        return weapon.usableBySeat(this.seatIndexForPassenger(passenger, this.getPassengers().indexOf(passenger)));
    }

    private boolean hasWeaponUsableBySeat(int seatIndex) {
        for (VehicleWeaponInfo weapon : this.vehicleData().defaults().weapons()) {
            if (weapon.usableBySeat(seatIndex)) {
                return true;
            }
        }
        return false;
    }

    public boolean shouldHidePassenger(Entity passenger) {
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        return fallbackIndex >= 0 && this.seatForPassenger(passenger, fallbackIndex).hidePassenger();
    }

    public boolean shouldProtectPassenger(Entity passenger) {
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        return fallbackIndex >= 0 && (this.seatForPassenger(passenger, fallbackIndex).hidePassenger()
                || this.seatForPassenger(passenger, fallbackIndex).enclosed());
    }

    public boolean shouldBanPassengerHand(Entity passenger) {
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        if (fallbackIndex < 0) {
            return false;
        }
        int seatIndex = this.seatIndexForPassenger(passenger, fallbackIndex);
        return this.seatForPassenger(seatIndex).banHand() || this.hasWeaponUsableBySeat(seatIndex);
    }

    public Vec3 cameraPositionFor(Entity passenger, float partialTick) {
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        if (fallbackIndex < 0) {
            return passenger.getEyePosition(partialTick);
        }
        SeatInfo seat = this.seatForPassenger(passenger, fallbackIndex);
        Vec3 articulatedZoomPosition = this.articulatedZoomCameraPosition(seat, partialTick);
        if (articulatedZoomPosition != null) {
            return articulatedZoomPosition;
        }
        var zoomCamera = seat.zoomCamera();
        if (this.usesFixedPassengerCamera(passenger, zoomCamera)) {
            return this.fixedSeatCameraPosition(zoomCamera, partialTick);
        }
        if (this.usesFirstPersonSeatCamera(passenger)) {
            if (zoomCamera.useSimulatedThirdPerson()) {
                return this.simulatedThirdPersonCameraPosition(passenger, zoomCamera, partialTick);
            }
            if (clientZoomDown() && (zoomCamera.x() != 0.0D || zoomCamera.y() != 0.0D || zoomCamera.z() != 0.0D)) {
                Vec3 firstPersonOffset = this.firstPersonSeatCameraOffset(zoomCamera.x(), zoomCamera.y(), zoomCamera.z(), partialTick);
                return this.interpolatedVehiclePosition(partialTick).add(firstPersonOffset);
            }
        }
        Vec3 offset = this.usesVehiclePoseTransform()
                ? this.seatEyeOffsetWithPose(seat.x(), seat.y() + passenger.getEyeHeight(), seat.z(), partialTick)
                : this.seatOffset(seat, passenger.getEyeHeight(), partialTick);
        return this.interpolatedVehiclePosition(partialTick).add(offset);
    }

    @Nullable
    private Vec3 articulatedZoomCameraPosition(SeatInfo seat, float partialTick) {
        if (!this.usesArticulatedSeatTransform(seat)
                || !this.level().isClientSide()
                || !clientVehicleStateMatches(this.getId())) {
            return null;
        }
        var zoomCamera = seat.zoomCamera();
        boolean hasDedicatedZoomOffset = zoomCamera.zoomX() != 0.0D || zoomCamera.zoomY() != 0.0D || zoomCamera.zoomZ() != 0.0D;
        if (!clientZoomDown() || !hasDedicatedZoomOffset) {
            return null;
        }
        double x = zoomCamera.zoomX();
        double y = zoomCamera.zoomY();
        double z = zoomCamera.zoomZ();
        if (x == 0.0D && y == 0.0D && z == 0.0D) {
            return null;
        }
        return this.articulatedBarrelPosition(x, y, z, partialTick);
    }

    public Vec3 cameraRotationFor(Entity passenger, float partialTick) {
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        if (fallbackIndex >= 0) {
            SeatInfo seat = this.seatForPassenger(passenger, fallbackIndex);
            Vec3 helicopterZoomRotation = this.helicopterZoomCameraRotation(passenger, partialTick);
            if (helicopterZoomRotation != null) {
                return helicopterZoomRotation;
            }
            Vec3 articulatedRotation = this.articulatedCameraRotation(seat, partialTick);
            if (articulatedRotation != null) {
                return articulatedRotation;
            }
            Vec3 fixedSeatRotation = this.fixedSeatCameraRotation(passenger, seat, partialTick);
            if (fixedSeatRotation != null) {
                return fixedSeatRotation;
            }
            Vec3 zoomRotation = this.weaponCameraRotation(passenger, partialTick);
            if (zoomRotation != null) {
                return zoomRotation;
            }
            return new Vec3(passenger.getViewYRot(partialTick), this.seatBoundViewPitch(passenger, seat, partialTick), 0.0D);
        }
        return new Vec3(passenger.getViewYRot(partialTick), passenger.getViewXRot(partialTick), 0.0D);
    }

    private float seatBoundViewPitch(Entity passenger, SeatInfo seat, float partialTick) {
        return Mth.clamp(passenger.getViewXRot(partialTick), seat.minPitch(), seat.maxPitch());
    }

    @Nullable
    private Vec3 helicopterZoomCameraRotation(Entity passenger, float partialTick) {
        if (this.vehicleData().defaults().vehicleType() != VehicleType.HELICOPTER
                || !this.usesFirstPersonSeatCamera(passenger)
                || !clientZoomDown()
                || !this.usesAircraftCamera(passenger)) {
            return null;
        }
        return new Vec3(
                Mth.rotLerp(partialTick, this.yRotO, this.getYRot()),
                this.cameraPitch(partialTick),
                this.cameraRoll(partialTick)
        );
    }

    public boolean usesAircraftCamera(Entity passenger) {
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        if (fallbackIndex < 0) {
            return false;
        }
        SeatInfo seat = this.seatForPassenger(passenger, fallbackIndex);
        return seat.zoomCamera().useAircraftCamera();
    }

    public boolean usesFixedCameraPosition(Entity passenger) {
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        if (fallbackIndex < 0) {
            return false;
        }
        SeatInfo seat = this.seatForPassenger(passenger, fallbackIndex);
        return seat.zoomCamera().useFixedCameraPos();
    }

    public Vec3 aircraftCameraPositionFor(Entity passenger, float partialTick) {
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        if (fallbackIndex < 0) {
            return passenger.getEyePosition(partialTick);
        }
        var camera = this.seatForPassenger(passenger, fallbackIndex).zoomCamera();
        Vec3 offset = this.cameraOffsetWithPose(camera.aircraftX(), camera.aircraftY(), camera.aircraftZ(), partialTick);
        return this.interpolatedVehiclePosition(partialTick).add(offset);
    }

    public Vec3 detachedPoseCameraPositionFor(Entity passenger, float partialTick) {
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        if (fallbackIndex < 0) {
            return passenger.getEyePosition(partialTick);
        }
        CameraPos camera = this.vehicleData().defaults().thirdPersonCamera();
        SeatInfo seat = this.seatForPassenger(passenger, fallbackIndex);
        if (seat.zoomCamera().useAircraftCamera()) {
            camera = seat.zoomCamera();
            Vec3 offset = this.chaseCameraOffset(camera.aircraftX(), camera.aircraftY(), camera.aircraftZ(), partialTick);
            return this.interpolatedVehiclePosition(partialTick).add(offset);
        }
        Vec3 offset = this.cameraOffsetWithPose(camera.x(), camera.y(), camera.z(), partialTick);
        return this.interpolatedVehiclePosition(partialTick).add(offset);
    }

    /**
     * The helicopter's tail camera: an action camera bolted to the top of the tail boom near its end, looking back along
     * the boom at the cabin and rotor. It is rigid, so it pitches and banks with the airframe.
     */
    public Vec3 tailCameraPosition(float partialTick) {
        OBBInfo.Box tail = null;
        for (OBBInfo.Box box : this.vehicleData().defaults().obb().boxes()) {
            if (tail == null || box.z() - box.halfDepth() < tail.z() - tail.halfDepth()) {
                tail = box;
            }
        }
        double x = TAIL_CAMERA_SIDE_OFFSET;
        double y = tail == null ? 2.0D : tail.y() + tail.halfHeight() + TAIL_CAMERA_HEIGHT;
        double z = tail == null ? -4.0D : tail.z() - tail.halfDepth() + TAIL_CAMERA_INSET;
        return this.interpolatedVehiclePosition(partialTick).add(this.rotateLocalOffsetWithPose(x, y, z, partialTick));
    }

    /** Yaw, pitch and roll of the tail camera: the hull's own attitude, tipped down a little towards the cabin. */
    public Vec3 tailCameraRotation(float partialTick) {
        return new Vec3(
                Mth.rotLerp(partialTick, this.yRotO, this.getYRot()),
                Mth.lerp(partialTick, this.xRotO, this.getXRot()) + TAIL_CAMERA_DOWN_TILT,
                this.roll(partialTick)
        );
    }

    public Vec3 aircraftCameraRotationFor(float partialTick) {
        return new Vec3(
                Mth.rotLerp(partialTick, this.yRotO, this.getYRot()),
                this.cameraPitch(partialTick),
                this.cameraRoll(partialTick)
        );
    }

    @Nullable
    private Vec3 fixedSeatCameraRotation(Entity passenger, SeatInfo seat, float partialTick) {
        if (!this.usesFirstPersonSeatCamera(passenger)
                || clientFreeLookDown()
                || clientZoomDown()
                || !seat.zoomCamera().useFixedCameraPos()) {
            return null;
        }
        if (seat.sensitivityY() != 0.0F || seat.sensitivityZ() != 0.0F) {
            return null;
        }
        return new Vec3(
                Mth.rotLerp(partialTick, this.yRotO, this.getYRot()),
                this.cameraPitch(partialTick),
                0.0D
        );
    }

    @Nullable
    private Vec3 weaponCameraRotation(Entity passenger, float partialTick) {
        if (!(passenger instanceof LivingEntity shooter)
                || !this.level().isClientSide()
                || !clientVehicleStateMatches(this.getId())
                || !clientZoomDown()) {
            return null;
        }
        Vec3 direction = this.weaponZoomAimDirection(shooter);
        if (direction == null) {
            return null;
        }
        return new Vec3(this.yRotFromVector(direction), this.xRotFromVector(direction), this.cameraRollForAiming(partialTick));
    }

    @Nullable
    private Vec3 articulatedCameraRotation(SeatInfo seat, float partialTick) {
        if (!this.usesArticulatedSeatTransform(seat)) {
            return null;
        }
        Vec3 barrel = this.articulatedBarrelDirection(partialTick).normalize();
        return new Vec3(this.yRotFromVector(barrel), this.xRotFromVector(barrel), this.cameraRollForAiming(partialTick));
    }

    private double cameraRollForAiming(float partialTick) {
        return this.usesVehiclePoseTransform() ? this.cameraRoll(partialTick) : 0.0D;
    }

    private boolean usesFirstPersonSeatCamera(Entity passenger) {
        return this.level().isClientSide()
                && clientFirstPersonCamera()
                && this.hasPassenger(passenger);
    }

    private boolean usesFixedPassengerCamera(Entity passenger, CameraPos camera) {
        return this.level().isClientSide()
                && this.hasPassenger(passenger)
                && camera.useFixedCameraPos()
                && !camera.useSimulatedThirdPerson()
                && (camera.x() != 0.0D || camera.y() != 0.0D || camera.z() != 0.0D);
    }

    @Nullable
    private static Entity localClientPlayer() {
        Object minecraft = minecraftInstance();
        Object player = readField(minecraft, "player");
        return player instanceof Entity entity ? entity : null;
    }

    private static void syncClientMousePosition() {
        Object minecraft = minecraftInstance();
        Object mouseHandler = readField(minecraft, "mouseHandler");
        if (mouseHandler == null) {
            return;
        }
        try {
            double x = ((Number) mouseHandler.getClass().getMethod("xpos").invoke(mouseHandler)).doubleValue();
            double y = ((Number) mouseHandler.getClass().getMethod("ypos").invoke(mouseHandler)).doubleValue();
            Class<?> state = Class.forName("ttv.migami.jeg.vehicle.client.VehicleClientState");
            state.getMethod("syncMousePosition", double.class, double.class).invoke(null, x, y);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
        }
    }

    private static boolean clientVehicleStateMatches(int entityId) {
        return clientStateBoolean("isRidingVehicle") && clientVehicleId() == entityId;
    }

    private static int clientVehicleId() {
        try {
            Class<?> state = Class.forName("ttv.migami.jeg.vehicle.client.VehicleClientState");
            return ((Number) state.getMethod("vehicleId").invoke(null)).intValue();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return -1;
        }
    }

    private static boolean clientZoomDown() {
        return clientStateBoolean("zoomDown");
    }

    private static boolean clientFreeLookDown() {
        return clientStateBoolean("freeLookDown");
    }

    private static void clearClientVehicleState() {
        try {
            Class.forName("ttv.migami.jeg.vehicle.client.VehicleClientState").getMethod("clear").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
        }
    }

    private static boolean clientFirstPersonCamera() {
        Object minecraft = minecraftInstance();
        Object options = readField(minecraft, "options");
        if (options == null) {
            return false;
        }
        try {
            Object cameraType = options.getClass().getMethod("getCameraType").invoke(options);
            return Boolean.TRUE.equals(cameraType.getClass().getMethod("isFirstPerson").invoke(cameraType));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean clientStateBoolean(String methodName) {
        try {
            Class<?> state = Class.forName("ttv.migami.jeg.vehicle.client.VehicleClientState");
            return Boolean.TRUE.equals(state.getMethod(methodName).invoke(null));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    @Nullable
    private static Object minecraftInstance() {
        try {
            return Class.forName("net.minecraft.client.Minecraft").getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static Object readField(@Nullable Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            var field = target.getClass().getField(name);
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            try {
                var field = target.getClass().getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
    }

    private void tickAutoRepair() {
        if (this.tickLowHealthDecay()) {
            return;
        }
        if (this.repairCooldown > 0) {
            this.repairCooldown--;
            return;
        }
        float repair = this.vehicleData().defaults().autoRepairPerTick();
        if (repair <= 0.0F) {
            return;
        }
        boolean repaired = false;
        if (this.vehicleHealth() < this.maxVehicleHealth()) {
            this.entityData.set(DATA_HEALTH, Math.min(this.maxVehicleHealth(), this.vehicleHealth() + repair));
            repaired = true;
        }
        repaired |= this.autoRepairParts(repair);
        if (repaired) {
            this.resetRecoveredLowHealthState();
            this.hurtMarked = true;
        }
    }

    private boolean autoRepairParts(float repair) {
        return this.repairParts(repair, false);
    }

    private boolean repairParts(float repair, boolean includeSubEngine) {
        boolean repaired = false;
        if (this.leftWheelHealth < PART_MAX_HEALTH) {
            this.leftWheelHealth = Math.min(PART_MAX_HEALTH, this.leftWheelHealth + repair);
            repaired = true;
        }
        if (this.rightWheelHealth < PART_MAX_HEALTH) {
            this.rightWheelHealth = Math.min(PART_MAX_HEALTH, this.rightWheelHealth + repair);
            repaired = true;
        }
        if (this.engineHealth < PART_MAX_HEALTH) {
            this.engineHealth = Math.min(PART_MAX_HEALTH, this.engineHealth + repair);
            repaired = true;
        }
        if (includeSubEngine && this.subEngineHealth < PART_MAX_HEALTH) {
            this.subEngineHealth = Math.min(PART_MAX_HEALTH, this.subEngineHealth + repair);
            repaired = true;
        }
        if (this.turretHealth < PART_MAX_HEALTH) {
            this.turretHealth = Math.min(PART_MAX_HEALTH, this.turretHealth + repair);
            repaired = true;
        }
        if (repaired) {
            this.syncPartDamageFlags();
        }
        return repaired;
    }

    private boolean tickLowHealthDecay() {
        if (!this.isLowHealthDecayActive()) {
            this.resetRecoveredLowHealthState();
            return false;
        }
        if (this.tickCount % LOW_HEALTH_DECAY_INTERVAL == 0) {
            float newHealth = this.vehicleHealth() - LOW_HEALTH_DECAY_DAMAGE;
            this.entityData.set(DATA_HEALTH, Math.max(0.0F, newHealth));
            this.hurtMarked = true;
            if (newHealth <= 0.0F) {
                this.destroyVehicle();
            }
        }
        return true;
    }

    private float lowHealthDecayThreshold() {
        return Math.min(LOW_HEALTH_DECAY_THRESHOLD, this.maxVehicleHealth());
    }

    private boolean isLowHealthDecayActive() {
        float health = this.vehicleHealth();
        return health > 0.0F && health < this.lowHealthDecayThreshold();
    }

    private void resetRecoveredLowHealthState() {
        if (this.isLowHealthDecayActive()) {
            return;
        }
        this.destroyRot = 0.0F;
    }

    private void tickServerMovement() {
        EngineInfo engine = this.vehicleData().defaults().engine();
        if (this.vehicleData().defaults().vehicleType() == VehicleType.ARTILLERY) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (this.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER
                || this.vehicleData().defaults().vehicleType() == VehicleType.AIRCRAFT) {
            this.tickServerAirMovement(engine);
            this.tickEngineSound();
            return;
        }
        if (this.vehicleData().defaults().vehicleType() == VehicleType.BOAT) {
            this.tickBoatMovement(engine, true);
            this.tickEngineSound();
            return;
        }
        Vec3 velocity = this.getDeltaMovement();
        int forwardAxis = this.input.forwardAxis();
        int steeringAxis = this.steeringAxis();
        if (engine.steeringSpeed() > 0.0D) {
            this.tickServerSteeredLandMovement(engine, velocity, forwardAxis, steeringAxis);
            return;
        }
        boolean hasThrottle = this.hasEngineEnergy(engine, forwardAxis != 0 || steeringAxis != 0);
        this.tickBasicLandMovement(engine, velocity, forwardAxis, this.input.strafeAxis(), hasThrottle, true);
    }

    private void tickBasicLandMovement(EngineInfo engine, Vec3 velocity, int forwardAxis, int strafeAxis, boolean hasThrottle, boolean engineSound) {
        boolean grounded = this.onGround();
        double mobility = this.mobilityMultiplier();

        if (hasThrottle && (forwardAxis != 0 || strafeAxis != 0)) {
            Vec3 forward = this.landDriveForward();
            Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
            Vec3 desired = forward.scale(forwardAxis).add(right.scale(strafeAxis * 0.55D));
            if (desired.lengthSqr() > 1.0E-4D) {
                desired = desired.normalize().scale(engine.acceleration() * mobility);
                velocity = velocity.add(desired.x, 0.0D, desired.z);
            }
        }

        double maxSpeed = (forwardAxis < 0 ? engine.maxReverseSpeed() : engine.maxForwardSpeed()) * mobility;
        Vec3 horizontal = new Vec3(velocity.x, 0.0D, velocity.z);
        if (horizontal.length() > maxSpeed) {
            horizontal = horizontal.normalize().scale(maxSpeed);
            velocity = new Vec3(horizontal.x, velocity.y, horizontal.z);
        }

        double friction = this.input.brake() ? 0.55D : engine.friction();
        if (grounded) {
            velocity = new Vec3(velocity.x * friction, velocity.y, velocity.z * friction);
        }
        if (!this.isNoGravity()) {
            velocity = velocity.add(0.0D, -GRAVITY, 0.0D);
        }

        this.moveGroundVehicle(velocity);
        if (engineSound) {
            this.tickEngineSound();
        }
    }

    private void tickClientPredictedLandMovement() {
        if (this.vehicleData().defaults().vehicleType() != VehicleType.LAND) {
            return;
        }
        EngineInfo engine = this.vehicleData().defaults().engine();
        Vec3 velocity = this.getDeltaMovement();
        int forwardAxis = this.input.forwardAxis();
        int strafeAxis = this.input.strafeAxis();
        int steeringAxis = this.steeringAxis();
        if (engine.steeringSpeed() > 0.0D) {
            this.tickSteeredLandMovement(engine, velocity, forwardAxis, steeringAxis, false);
            return;
        }
        this.tickBasicLandMovement(engine, velocity, forwardAxis, strafeAxis, forwardAxis != 0 || strafeAxis != 0, false);
    }

    private void tickClientPredictedBoatMovement() {
        if (this.vehicleData().defaults().vehicleType() != VehicleType.BOAT) {
            return;
        }
        EngineInfo engine = this.vehicleData().defaults().engine();
        this.tickBoatMovement(engine, false);
    }

    private void tickClientPredictedHelicopterMovement() {
        if (this.vehicleData().defaults().vehicleType() != VehicleType.HELICOPTER) {
            return;
        }
        EngineInfo engine = this.vehicleData().defaults().engine();
        this.tickHelicopterMovement(engine, false);
    }

    private void tickBoatMovement(EngineInfo engine, boolean consumeEnergy) {
        Vec3 velocity = this.getDeltaMovement();
        int forwardAxis = this.input.forwardAxis();
        int steeringAxis = this.steeringAxis();
        boolean waterContact = this.hasBoatWaterContact();
        boolean inWater = this.isBoatWaterborne(waterContact);
        double mobility = this.mobilityMultiplier();

        if (forwardAxis > 0) {
            this.enginePower = Math.min(this.enginePower + engine.acceleration(), 1.0D);
        } else if (forwardAxis < 0) {
            this.enginePower = Math.max(this.enginePower - engine.acceleration(), -1.0D);
        } else {
            this.enginePower *= inWater ? 0.985D : 0.94D;
        }
        if (this.input.brake()) {
            this.enginePower *= 0.72D;
        }
        if (steeringAxis != 0) {
            this.enginePower *= 0.992D;
        }

        if (consumeEnergy) {
            int cost = this.scaledEngineEnergyCost(engine, Math.abs(this.enginePower) > 1.0E-4D, Math.abs(this.enginePower));
            if (cost > 0 && !this.consumeEngineEnergyCost(cost)) {
                this.enginePower *= 0.9D;
            }
        }

        double steeringSpeed = engine.steeringSpeed() > 0.0D ? engine.steeringSpeed() : 0.12D;
        if (steeringAxis != 0) {
            this.wheelSteering += steeringAxis * steeringSpeed;
        }
        this.wheelSteering *= inWater ? 0.84D : 0.72D;

        Vec3 horizontal = new Vec3(velocity.x, 0.0D, velocity.z);
        double horizontalSpeed = horizontal.length();
        if (Math.abs(this.wheelSteering) > 1.0E-4D) {
            double yawDelta = Math.max(8.0D, horizontalSpeed * 16.0D) * this.wheelSteering * (this.enginePower >= 0.0D ? 1.0D : -0.7D);
            float previousYaw = this.getYRot();
            this.yRotO = previousYaw;
            this.setYRot(previousYaw + (float) yawDelta);
            this.updateVehicleBoundingBox();
        }

        float yaw = this.getYRot();
        double yawRadians = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
        double targetSpeed = (this.enginePower >= 0.0D ? engine.maxForwardSpeed() : engine.maxReverseSpeed()) * mobility;
        double thrustScale = inWater ? 0.11D : 0.05D;
        velocity = velocity.add(forward.scale(thrustScale * targetSpeed * this.enginePower));

        horizontal = new Vec3(velocity.x, 0.0D, velocity.z);
        double maxSpeed = (this.enginePower < 0.0D ? engine.maxReverseSpeed() : engine.maxForwardSpeed()) * mobility * (inWater ? 1.0D : 0.45D);
        if (horizontal.length() > maxSpeed) {
            horizontal = horizontal.normalize().scale(maxSpeed);
            velocity = new Vec3(horizontal.x, velocity.y, horizontal.z);
        }
        if (inWater) {
            double vertical = waterContact ? Math.min(0.035D, velocity.y + 0.014D) : Math.max(-0.035D, velocity.y - 0.006D);
            velocity = new Vec3(velocity.x * engine.friction(), vertical, velocity.z * engine.friction());
        } else {
            velocity = velocity.add(0.0D, -GRAVITY, 0.0D).multiply(0.94D, 0.98D, 0.94D);
        }
        velocity = this.stabilizeSpeedboatWaterCruise(velocity, forward, forwardAxis, inWater, mobility, engine);
        velocity = this.limitSpeedboatBlockCollision(velocity);

        Vec3 before = this.position();
        this.setDeltaMovement(velocity);
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 moved = this.position().subtract(before);
        double nextY = inWater ? (waterContact ? Math.min(0.035D, moved.y + 0.004D) : Math.max(-0.035D, moved.y * 0.94D)) : moved.y * 0.98D;
        this.setDeltaMovement(moved.x * 0.985D, nextY, moved.z * 0.985D);
        if (moved.horizontalDistanceSqr() > 1.0E-7D || Math.abs(moved.y) > 1.0E-7D) {
            this.hurtMarked = true;
            this.needsSync = true;
        }
    }

    private boolean isBoatWaterborne(boolean waterContact) {
        if (waterContact) {
            this.boatWaterborneTicks = BOAT_WATERBORNE_MEMORY_TICKS;
            return true;
        }
        if (this.boatWaterborneTicks > 0) {
            this.boatWaterborneTicks--;
            return true;
        }
        return false;
    }

    private boolean hasBoatWaterContact() {
        if (this.isInWater() || this.isUnderWater()) {
            return true;
        }
        AABB box = this.getBoundingBox();
        int minX = Mth.floor(box.minX + 1.0E-4D);
        int maxX = Mth.floor(box.maxX - 1.0E-4D);
        int minY = Mth.floor(box.minY - BOAT_WATER_PROBE_BELOW);
        int maxY = Mth.floor(Math.min(box.minY + BOAT_WATER_PROBE_ABOVE, box.maxY));
        int minZ = Mth.floor(box.minZ + 1.0E-4D);
        int maxZ = Mth.floor(box.maxZ - 1.0E-4D);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (this.level().getFluidState(cursor.set(x, y, z)).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Vec3 stabilizeSpeedboatWaterCruise(Vec3 velocity, Vec3 forward, int forwardAxis, boolean inWater, double mobility, EngineInfo engine) {
        if (!this.isSpeedboatVehicle() || !inWater || forwardAxis <= 0 || this.input.brake() || this.enginePower <= 0.0D) {
            return velocity;
        }
        double maxForwardSpeed = engine.maxForwardSpeed() * mobility;
        double targetSpeed = Math.min(SPEEDBOAT_WATER_CRUISE_SPEED, maxForwardSpeed) * Mth.clamp(this.enginePower, 0.0D, 1.0D);
        if (targetSpeed <= 1.0E-5D) {
            return velocity;
        }
        Vec3 horizontal = forward.normalize().scale(targetSpeed);
        return new Vec3(horizontal.x, velocity.y, horizontal.z);
    }

    private Vec3 limitSpeedboatBlockCollision(Vec3 velocity) {
        if (!this.isSpeedboatVehicle()) {
            return velocity;
        }
        Vec3 horizontal = new Vec3(velocity.x, 0.0D, velocity.z);
        if (horizontal.lengthSqr() < 1.0E-7D || !this.isSpeedboatBowBlocked(horizontal)) {
            return velocity;
        }
        this.enginePower *= 0.35D;
        this.wheelSteering *= 0.5D;
        return new Vec3(0.0D, velocity.y, 0.0D);
    }

    private boolean isSpeedboatBowBlocked(Vec3 horizontal) {
        Vec3 direction = horizontal.normalize();
        Vec3 side = new Vec3(direction.z, 0.0D, -direction.x);
        AABB box = this.getBoundingBox();
        double distance = Math.max(this.getBbWidth() * 0.5D, this.getBbWidth() * this.collisionLengthScale() * 0.5D) + horizontal.length() + SPEEDBOAT_BOW_BLOCK_PROBE_DISTANCE;
        Vec3 bowCenter = this.position().add(direction.scale(distance));
        double[] sideOffsets = {-SPEEDBOAT_BOW_BLOCK_PROBE_HALF_WIDTH, 0.0D, SPEEDBOAT_BOW_BLOCK_PROBE_HALF_WIDTH};
        double[] yOffsets = {SPEEDBOAT_BOW_BLOCK_PROBE_LOW_Y, SPEEDBOAT_BOW_BLOCK_PROBE_HIGH_Y};
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (double yOffset : yOffsets) {
            for (double sideOffset : sideOffsets) {
                Vec3 sample = bowCenter.add(side.scale(sideOffset));
                cursor.set(sample.x, box.minY + yOffset, sample.z);
                BlockState state = this.level().getBlockState(cursor);
                if (!state.getCollisionShape(this.level(), cursor).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasEngineEnergy(EngineInfo engine, boolean active) {
        int cost = this.scaledEngineEnergyCost(engine, active, 1.0D);
        return cost <= 0 || this.vehicleEnergy() >= cost;
    }

    private boolean consumeEngineEnergy(EngineInfo engine, boolean active) {
        return this.consumeEngineEnergyCost(this.scaledEngineEnergyCost(engine, active, 1.0D));
    }

    private int scaledEngineEnergyCost(EngineInfo engine, boolean active, double scale) {
        if (!active || engine.energyCostRate() <= 0 || this.maxVehicleEnergy() <= 0) {
            return 0;
        }
        double scaledCost = engine.energyCostRate() * Math.max(0.0D, scale);
        return Math.max(1, (int) Math.ceil(scaledCost));
    }

    private boolean consumeEngineEnergyCost(int cost) {
        if (cost <= 0) {
            return true;
        }
        int current = this.vehicleEnergy();
        if (current <= 0) {
            return false;
        }
        if (current < cost) {
            this.consumeEnergy(current);
            return false;
        }
        return this.consumeEnergy(cost);
    }

    private void tickServerSteeredLandMovement(EngineInfo engine, Vec3 velocity, int forwardAxis, int steeringAxis) {
        this.tickSteeredLandMovement(engine, velocity, forwardAxis, steeringAxis, true);
    }

    private void tickSteeredLandMovement(EngineInfo engine, Vec3 velocity, int forwardAxis, int steeringAxis, boolean engineSound) {
        double mobility = this.mobilityMultiplier();
        Vec3 forward = this.landDriveForward();

        Vec3 horizontal = new Vec3(velocity.x, 0.0D, velocity.z);
        double horizontalSpeed = horizontal.length();
        if (forwardAxis > 0) {
            this.enginePower = Math.min(this.enginePower + (this.enginePower < 0.0D ? engine.acceleration() * 2.0D : engine.acceleration()), 1.0D);
        } else if (forwardAxis < 0) {
            this.enginePower = Math.max(this.enginePower - (this.enginePower > 0.0D ? engine.acceleration() * 2.0D : engine.acceleration()), -1.0D);
        } else {
            this.enginePower *= 0.97D;
        }
        if (this.input.brake()) {
            this.enginePower *= 0.6D;
        }
        if (steeringAxis != 0) {
            this.enginePower *= 0.98D;
        }

        if (engineSound) {
            int cost = this.scaledEngineEnergyCost(engine, Math.abs(this.enginePower) > 1.0E-4D, Math.abs(this.enginePower));
            if (cost > 0 && !this.consumeEngineEnergyCost(cost)) {
                this.enginePower *= 0.95D;
            }
        }

        if (steeringAxis != 0) {
            this.wheelSteering += steeringAxis * engine.steeringSpeed();
        }
        this.wheelSteering *= Math.max(0.78D - 0.25D * horizontalSpeed, 0.1D);

        if (Math.abs(this.wheelSteering) > 1.0E-4D) {
            float previousYaw = this.getYRot();
            double turnRate = Math.max(12.0D * horizontalSpeed, 0.0D);
            double turnDirection = this.enginePower > 0.0D ? 1.0D : -1.0D;
            if (engine.type() == EngineType.TRACK && turnRate < TRACK_PIVOT_TURN_RATE) {
                // Tracks counter-rotate, so tracked vehicles can pivot on the spot instead of needing speed to steer.
                turnRate = TRACK_PIVOT_TURN_RATE;
                if (Math.abs(this.enginePower) < TRACK_PIVOT_POWER_THRESHOLD) {
                    turnDirection = 1.0D;
                }
            }
            double yawDelta = turnRate * this.wheelSteering * turnDirection;
            if (this.setVehicleYawIfUnblocked(previousYaw + (float) yawDelta)) {
                this.yRotO = previousYaw;
            } else {
                this.enginePower *= 0.75D;
                this.wheelSteering *= 0.35D;
            }
        }

        double targetSpeed = (this.enginePower > 0.0D ? engine.maxForwardSpeed() : engine.maxReverseSpeed()) * mobility;
        velocity = velocity.add(forward.scale(0.15D * targetSpeed * this.enginePower));
        horizontal = new Vec3(velocity.x, 0.0D, velocity.z);
        horizontalSpeed = horizontal.length();

        double maxSpeed = (this.enginePower < 0.0D ? engine.maxReverseSpeed() : engine.maxForwardSpeed()) * mobility;
        if (horizontalSpeed > maxSpeed) {
            horizontal = horizontal.normalize().scale(maxSpeed);
            velocity = new Vec3(horizontal.x, velocity.y, horizontal.z);
        }

        double friction = this.input.brake() ? 0.55D : engine.friction();
        if (this.onGround()) {
            velocity = new Vec3(velocity.x * friction, velocity.y, velocity.z * friction);
        }
        if (!this.isNoGravity()) {
            velocity = velocity.add(0.0D, -GRAVITY, 0.0D);
        }

        this.moveGroundVehicle(velocity);
        if (engineSound) {
            this.tickEngineSound();
        }
    }

    private void moveGroundVehicle(Vec3 velocity) {
        Vec3 before = this.position();
        this.setDeltaMovement(velocity);
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 moved = this.position().subtract(before);
        if (moved.horizontalDistanceSqr() < 1.0E-7D && new Vec3(velocity.x, 0.0D, velocity.z).lengthSqr() > 1.0E-7D) {
            velocity = new Vec3(0.0D, velocity.y, 0.0D);
            this.enginePower *= 0.35D;
            this.wheelSteering *= 0.5D;
        }
        boolean unmannedLandVehicle = this.vehicleData().defaults().vehicleType() == VehicleType.LAND && this.getControllingPassenger() == null;
        double nextY = velocity.y;
        if (unmannedLandVehicle && !this.onGround() && !this.verticalCollisionBelow) {
            nextY = Math.min(nextY, moved.y - GRAVITY);
        }
        if (this.onGround() || this.verticalCollisionBelow) {
            nextY = moved.y > 0.0D ? Math.min(moved.y * 0.2D, 0.08D) : 0.0D;
            if (unmannedLandVehicle) {
                nextY = Math.min(0.0D, nextY);
            }
        } else if (this.verticalCollision && nextY > 0.0D) {
            nextY = 0.0D;
        }
        this.setDeltaMovement(moved.x * 0.98D, nextY * 0.98D, moved.z * 0.98D);
        if (moved.horizontalDistanceSqr() > 1.0E-7D || Math.abs(moved.y) > 1.0E-7D) {
            this.hurtMarked = true;
            this.needsSync = true;
        }
    }

    private Vec3 landDriveForward() {
        return this.horizontalDirection(this.getYRot());
    }

    private Vec3 helicopterLiftVector(double strength) {
        return this.rotateLocalDirectionWithPose(0.0D, strength, 0.0D, 1.0F);
    }

    private double helicopterAltitudeFromGround() {
        Vec3 start = this.position();
        Vec3 end = start.add(0.0D, -HELICOPTER_GROUND_PROBE_DISTANCE, 0.0D);
        HitResult hit = this.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (hit.getType() == HitResult.Type.MISS) {
            return HELICOPTER_GROUND_PROBE_DISTANCE;
        }
        return Math.max(0.0D, start.y - hit.getLocation().y);
    }

    public double rotateOffsetHeight() {
        return this.vehicleData().defaults().turret().renderPivotY();
    }

    private int steeringAxis() {
        return (this.input.right() ? 1 : 0) - (this.input.left() ? 1 : 0);
    }

    private boolean hasPlayerPassenger() {
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Player) {
                return true;
            }
        }
        return false;
    }

    private Vec3 horizontalDirection(float yaw) {
        double yawRadians = Math.toRadians(yaw);
        return new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
    }

    private void tickEngineSound() {
        // Helicopters use one positional client sound per engine, including startup and Doppler pitch.
        if (this.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER) {
            return;
        }
        if (this.tickCount % 20 != 0) {
            return;
        }
        SoundEvent sound = VehicleSoundHelper.engineSound(this);
        if (sound == null) {
            return;
        }
        VehicleType type = this.vehicleData().defaults().vehicleType();
        EngineInfo engine = this.vehicleData().defaults().engine();
        Vec3 movement = this.getDeltaMovement();
        boolean inputActive = this.input.forwardAxis() != 0 || this.input.strafeAxis() != 0 || this.input.verticalAxis() != 0;
        boolean active = inputActive && this.hasEngineEnergy(engine, true);
        if (!active) {
            active = switch (type) {
                case LAND, BOAT -> movement.x * movement.x + movement.z * movement.z > 0.0025D;
                // A steady hover has no movement, so keep the engine audible while the rotor is powered in the air.
                case HELICOPTER -> movement.lengthSqr() > 0.0025D || (!this.onGround() && (movement.y < -1.0E-4D || this.propellerSpeed() > HELICOPTER_DESCENT_ROTOR_SPEED));
                case AIRCRAFT -> movement.lengthSqr() > 0.0025D;
                case ARTILLERY -> false;
            };
        }
        if (!active) {
            return;
        }
        this.level().playSound(null, this, sound, SoundSource.AMBIENT, Math.max(0.1F, this.vehicleData().defaults().engine().engineSoundVolume()), 1.0F);
    }

    private void tickServerAirMovement(EngineInfo engine) {
        if (this.vehicleData().defaults().vehicleType() == VehicleType.HELICOPTER) {
            this.tickServerHelicopterMovement(engine);
            return;
        }
        Vec3 velocity = this.getDeltaMovement();
        int forwardAxis = this.input.forwardAxis();
        int strafeAxis = this.input.strafeAxis();
        int verticalAxis = this.input.verticalAxis();
        boolean inputActive = forwardAxis != 0 || strafeAxis != 0 || verticalAxis != 0;
        boolean energyEmpty = this.maxVehicleEnergy() > 0 && this.vehicleEnergy() <= 0;
        boolean forcedDescent = !this.onGround() && (this.getControllingPassenger() == null || energyEmpty || this.isLowHealthDecayActive());
        boolean hasThrottle = !forcedDescent && this.consumeEngineEnergy(engine, inputActive);
        if (forcedDescent) {
            forwardAxis = 0;
            strafeAxis = 0;
            verticalAxis = 0;
        }
        double mobility = this.mobilityMultiplier();
        double verticalSpeedBeforeForces = velocity.y;

        float yaw = this.getYRot();
        double yawRadians = Math.toRadians(yaw);
        Vec3 forward = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
        Vec3 right = new Vec3(forward.z, 0.0D, -forward.x);
        Vec3 desired = forward.scale(forwardAxis).add(right.scale(strafeAxis * 0.65D)).add(0.0D, verticalAxis * 0.8D, 0.0D);
        if (hasThrottle && desired.lengthSqr() > 1.0E-4D) {
            velocity = velocity.add(desired.normalize().scale(engine.acceleration() * mobility));
        }

        double maxSpeed = engine.maxForwardSpeed() * mobility;
        if (!forcedDescent && velocity.length() > maxSpeed) {
            velocity = velocity.normalize().scale(maxSpeed);
        }
        double drag = this.input.brake() ? 0.82D : 0.94D;
        velocity = velocity.multiply(drag, 0.90D, drag);
        if (verticalAxis == 0) {
            velocity = velocity.add(0.0D, -AIRCRAFT_IDLE_DESCENT_ACCELERATION, 0.0D);
        }
        if (forcedDescent) {
            double descentTarget = Math.max(verticalSpeedBeforeForces - AIRCRAFT_FORCED_DESCENT_ACCELERATION, -AIRCRAFT_MAX_DESCENT_SPEED);
            velocity = new Vec3(velocity.x, Math.min(velocity.y, descentTarget), velocity.z);
        }

        this.setDeltaMovement(velocity);
        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    private void tickServerHelicopterMovement(EngineInfo engine) {
        this.tickHelicopterMovement(engine, true);
    }

    private void tickHelicopterMovement(EngineInfo engine, boolean consumeEnergy) {
        // With someone aboard but the pilot's seat empty (the pilot moved to the guns) the autopilot holds height and heading,
        // and lets any speed bleed off into a hover. Abandoned, or with the power gone, it still comes down.
        boolean autopilot = this.getControllingPassenger() == null && this.hasPlayerPassenger() && !this.onGround()
                && this.engineStartOver && !this.isLowHealthDecayActive();
        if (autopilot) {
            this.input = VehicleInput.EMPTY;
        }
        Vec3 velocity = this.getDeltaMovement();
        if (this.onGround() && velocity.y < 0.0D) {
            // Resting on the ground the hull is not falling. Gravity used to pile up here to full descent speed while the
            // rotor spun up, so the first moment of lift-off "landed" again at that speed: over a third of the AH-6's health
            // gone, and a share of it leaked to the passengers.
            velocity = new Vec3(velocity.x, 0.0D, velocity.z);
        }
        Vec3 velocityAtTickStart = velocity;
        double verticalSpeedBeforeDrag = velocity.y;
        int forwardAxis = this.input.forwardAxis();
        int verticalAxis = this.input.verticalAxis();
        int steeringAxis = this.steeringAxis();
        boolean playerPilot = this.getControllingPassenger() instanceof Player;
        // Player pilots fly forward with W, so only the ascend key climbs; AI pilots keep forward-as-collective.
        boolean up = verticalAxis > 0 || (!playerPilot && forwardAxis > 0);
        boolean down = verticalAxis < 0;
        boolean back = forwardAxis < 0 || this.input.brake();
        double mobility = this.mobilityMultiplier();
        LivingEntity pilot = this.getControllingPassenger() instanceof LivingEntity living ? living : null;
        float currentRotorSpeed = this.propellerSpeed();
        double powerAdd = engine.increment();
        double powerReduce = engine.decrement();
        double pitchSpeed = engine.pitchSpeed();
        double yawSpeed = engine.yawSpeed();
        double rollSpeed = engine.rollSpeed();
        double liftSpeed = engine.liftSpeed();
        boolean engineEnergyActive = up || down || back || this.engineStart || this.engineStartOver || Math.abs(this.enginePower) > 1.0E-4D;
        double helicopterEnergyScale = Math.max(8.3333D * Math.abs(this.enginePower), engineEnergyActive ? 1.0E-4D : 0.0D);
        // Prediction checks the same fuel state as the server; only the server spends it.
        boolean hasEnergy = this.hasEngineEnergy(engine, engineEnergyActive);
        boolean hasPlayerPassenger = this.hasPlayerPassenger();
        double altitude = this.helicopterAltitudeFromGround();
        double ceiling = this.level().getMaxY() + HELICOPTER_CEILING_ABOVE_WORLD;
        double altitudeLimitFactor = Mth.clamp((ceiling - this.getY()) / HELICOPTER_ALTITUDE_SOFT_ZONE, 0.0D, 1.0D);
        boolean altitudeLimited = altitudeLimitFactor <= 0.0D;
        boolean lowHealthDescent = this.isLowHealthDecayActive();
        boolean forcedDescent = !this.onGround() && ((pilot == null && !autopilot) || !hasEnergy || lowHealthDescent);
        boolean rotorIdleLocked = this.onGround() && pilot == null && currentRotorSpeed <= 0.0005F;
        if (rotorIdleLocked) {
            up = false;
            down = false;
            back = false;
            this.enginePower = 0.0D;
            this.engineStart = false;
            this.engineStartOver = false;
            this.holdTick = 0;
            this.holdPowerTick = 0;
        }
        if (!hasEnergy) {
            up = false;
            down = false;
            back = false;
            if (this.onGround()) {
                this.enginePower = 0.0D;
                this.engineStart = false;
                this.engineStartOver = false;
            }
        }
        if (altitudeLimited) {
            up = false;
        }
        // Hot restart: a pilot taking the controls of a helicopter that is airborne (jumped out and climbed back in, or a
        // world loaded mid-flight), or still has its rotor turning on the ground, picks the engine straight back up rather
        // than sitting through a cold start while it falls. A stopped rotor on the ground still needs the full start, and a
        // cold start under way (its rotor winding up) is not cut short.
        if (pilot != null && !this.engineStartOver && hasEnergy && !lowHealthDescent
                && this.vehicleHealth() > 0.1F * this.maxVehicleHealth()
                && ((!this.onGround() && altitude > HELICOPTER_HOT_RESTART_ALTITUDE)
                        || (up && !this.engineStart && currentRotorSpeed >= HELICOPTER_HOT_RESTART_ROTOR_SPEED))) {
            this.engineStart = true;
            this.engineStartOver = true;
            this.entityData.set(DATA_HELICOPTER_STARTUP, this.helicopterStartupTicks());
            this.enginePower = Math.max(this.enginePower, HELICOPTER_HOVER_POWER);
        }
        boolean playerVerticalControl = (playerPilot || autopilot)
                && hasEnergy
                && !lowHealthDescent
                && !forcedDescent
                && this.engineStartOver
                && this.vehicleHealth() > 0.1F * this.maxVehicleHealth()
                && (!this.onGround() || up);
        double targetVerticalSpeed = up ? HELICOPTER_CLIMB_SPEED : down ? -HELICOPTER_COMMANDED_DESCENT_SPEED : 0.0D;
        if (down && !up && altitude < HELICOPTER_LANDING_ASSIST_ALTITUDE) {
            double landingFactor = Mth.clamp(altitude / HELICOPTER_LANDING_ASSIST_ALTITUDE, 0.0D, 1.0D);
            targetVerticalSpeed = -Mth.lerp(landingFactor, HELICOPTER_TOUCHDOWN_SPEED, HELICOPTER_COMMANDED_DESCENT_SPEED);
        }
        double turnResponse = playerPilot ? HELICOPTER_PLAYER_TURN_RESPONSE : 1.0D;
        if (!playerVerticalControl) {
            this.pilotAimPitch = 0.0F;
            this.settleWeightyHelicopterState();
        }
        if (playerPilot && !playerVerticalControl && this.onGround()) {
            // Settle any flight attitude left over from touchdown.
            this.setXRot(this.getXRot() * 0.8F);
            this.setRoll(this.roll() * 0.8F);
        }

        if (this.onGround()) {
            velocity = velocity.multiply(0.8D, 1.0D, 0.8D);
        } else {
            this.setRoll(this.roll() * (back ? 0.9F : 0.99F));
            float drag = (float) Mth.clamp(0.95F - 0.015F * velocity.length(), 0.5F, 0.99F);
            velocity = velocity.add(this.horizontalDirection(this.getYRot()).scale((this.getXRot() < 0.0F ? -0.035D : this.getXRot() > 0.0F ? 0.035D : 0.0D) * velocity.length()));
            velocity = velocity.multiply(drag, 0.95D, drag);
        }

        if (this.isInWater()) {
            velocity = velocity.multiply(0.6D, 0.6D, 0.6D);
        }

        if (!lowHealthDescent && this.vehicleHealth() > 0.1F * this.maxVehicleHealth()) {
            if (pilot == null && !autopilot) {
                this.holdTick = 0;
                this.holdPowerTick = 0;
                if (this.engineStartOver) {
                    this.enginePower *= 0.99D;
                }
                if (!hasPlayerPassenger) {
                    this.engineStart = false;
                    this.engineStartOver = false;
                }
                this.setRoll(this.roll() * 0.98F);
                this.xRotO = this.getXRot();
                this.setXRot(this.getXRot() * 0.98F);
                velocity = velocity.multiply(0.96D, hasPlayerPassenger ? 0.98D : 0.94D, 0.96D);
            } else {
                if (playerVerticalControl) {
                    // A/D strafe and the attitude follows movement, so the mouse only turns and aims the nose.
                    this.holdTick = 0;
                    if (!this.isFreeLookInputDown()) {
                        this.pilotAimPitch = Mth.clamp(this.pilotAimPitch + (float) (1.5D * pitchSpeed * turnResponse * HELICOPTER_HOVER_POWER) * this.input.mouseY(), -HELICOPTER_MAX_AIM_UP, HELICOPTER_MAX_AIM_DOWN);
                    }
                } else if (this.input.right()) {
                    this.holdTick++;
                    this.wheelSteering -= 2.0D * Math.min(this.holdTick, 7) * this.enginePower;
                } else if (this.input.left()) {
                    this.holdTick++;
                    this.wheelSteering += 2.0D * Math.min(this.holdTick, 7) * this.enginePower;
                } else {
                    this.holdTick = 0;
                }

                if (!this.isFreeLookInputDown() && !playerVerticalControl) {
                    float mousePitch = this.input.mouseY();
                    float mouseYaw = this.input.mouseX();
                    if (!playerPilot) {
                        this.xRotO = this.getXRot();
                    }
                    this.setXRot(Mth.clamp(this.getXRot() + (this.onGround() ? 0.0F : 1.5F) * (float) (pitchSpeed * turnResponse) * mousePitch * currentRotorSpeed, -50.0F, 40.0F));
                    this.setRoll((float) (this.roll() - rollSpeed * (this.wheelSteering + (this.onGround() ? 0.0D : 0.25D) * mouseYaw * currentRotorSpeed)));
                }

                float mouseYaw = this.input.mouseX();
                this.yRotO = this.getYRot();
                float yawStep = (float) (yawSpeed * Mth.clamp((this.onGround() ? 0.1D : 2.0D * turnResponse) * mouseYaw * currentRotorSpeed + (this.isSubEngineDamaged() ? 25.0D : 0.0D) * currentRotorSpeed, -10.0D * turnResponse, 10.0D * turnResponse));
                if (playerVerticalControl && Config.helicopterFlight().weightyFlight()) {
                    double yawWeight = weightyHelicopterWeight(engine, Config.helicopterFlight());
                    this.heliYawRate += (yawStep - this.heliYawRate) * (float) (WEIGHTY_YAW_RESPONSE / Math.sqrt(yawWeight));
                    yawStep = this.heliYawRate;
                } else {
                    this.heliYawRate = 0.0F;
                }
                this.setYRot(this.getYRot() + yawStep);
                this.updateVehicleBoundingBox();
            }

            if (!hasEnergy) {
                this.enginePower *= 0.995D;
                this.engineStart = false;
                this.engineStartOver = false;
            } else {
                if (!this.engineStart && up) {
                    this.engineStart = true;
                    this.entityData.set(DATA_HELICOPTER_STARTUP, 0);
                }
                if (playerVerticalControl) {
                    // Rotor power follows the commanded climb/hover/descent; vertical speed itself is set below.
                    double targetPower = up ? HELICOPTER_CLIMB_POWER : down ? HELICOPTER_DESCENT_POWER : HELICOPTER_HOVER_POWER;
                    this.enginePower += Mth.clamp(targetPower - this.enginePower, -HELICOPTER_POWER_RESPONSE, HELICOPTER_POWER_RESPONSE);
                    this.holdPowerTick = 0;
                } else if (up && this.engineStartOver) {
                    this.holdPowerTick++;
                    this.enginePower = Math.min(this.enginePower + 0.00045D * powerAdd * Math.min(this.holdPowerTick, 8), 0.105D);
                }
                if (this.engineStartOver && !playerVerticalControl) {
                    if (down) {
                        this.holdPowerTick++;
                        this.enginePower = Math.max(this.enginePower - 0.001D * powerReduce * Math.min(this.holdPowerTick, 5), this.onGround() ? 0.0D : 0.025D / liftSpeed);
                    } else if (back) {
                        this.holdPowerTick++;
                        this.enginePower = Math.max(this.enginePower - 0.001D * powerReduce * Math.min(this.holdPowerTick, 5), this.onGround() ? 0.0D : 0.04D / liftSpeed);
                    }
                }
                if (altitudeLimited && this.engineStartOver && !playerVerticalControl) {
                    this.enginePower = Math.max(this.enginePower - 0.0015D * powerReduce, 0.0D);
                }
                if (this.engineStart && !this.engineStartOver) {
                    int elapsed = Math.min(this.helicopterStartupTicks(), this.entityData.get(DATA_HELICOPTER_STARTUP) + 1);
                    this.entityData.set(DATA_HELICOPTER_STARTUP, elapsed);
                    double progress = this.helicopterStartupProgress();
                    this.enginePower = HELICOPTER_HOVER_POWER * progress * progress * (3.0D - 2.0D * progress);
                }
                if (!playerVerticalControl && !(up || down || back) && this.engineStartOver) {
                    if (velocity.y < 0.0D) {
                        this.enginePower = Math.min(this.enginePower + 0.00015D, 0.105D);
                    } else {
                        this.enginePower = Math.max(this.enginePower - (this.onGround() ? 0.00005D : 0.00045D), 0.0D);
                    }
                    this.holdPowerTick = 0;
                }
            }
        } else if (!this.onGround() && this.engineStartOver) {
            this.enginePower = Math.max(this.enginePower - 0.0003D, 0.01D);
            this.destroyRot += 0.08F;
            float diffX = 45.0F - this.getXRot();
            float diffZ = -20.0F - this.roll();
            this.xRotO = this.getXRot();
            this.setXRot(this.getXRot() + diffX * 0.05F * currentRotorSpeed);
            this.yRotO = this.getYRot();
            this.setYRot(this.getYRot() + this.destroyRot);
            this.setRoll(this.roll() + diffZ * 0.1F * currentRotorSpeed);
            velocity = velocity.add(0.0D, -this.destroyRot * 0.004D, 0.0D);
        }

        if (this.isEngineDamaged()) {
            this.enginePower *= 0.98D;
        }
        this.wheelSteering *= 0.9D;
        if (rotorIdleLocked) {
            this.enginePower = 0.0D;
            this.engineStart = false;
            this.engineStartOver = false;
            this.holdTick = 0;
            this.holdPowerTick = 0;
        }

        // Once landed, the unpowered rotor coasts independently of engine power. Braking must not snap it to a stop.
        boolean coasting = this.onGround() && !up && (this.engineStartOver || !this.engineStart);
        if (coasting) {
            this.enginePower = 0.0D;
            this.engineStart = false;
            this.engineStartOver = false;
        }
        float nextRotorSpeed = coasting
                ? Math.max(0.0F, currentRotorSpeed - (float) HELICOPTER_HOVER_POWER / this.helicopterShutdownTicks())
                : Mth.lerp(0.18F, currentRotorSpeed, (float) this.enginePower);
        // The rotor winds up on the ground; collective cannot lift the helicopter before operating RPM.
        float liftRotorSpeed = coasting || (this.engineStart && !this.engineStartOver) ? 0.0F : nextRotorSpeed;
        if (rotorIdleLocked) {
            nextRotorSpeed = 0.0F;
            liftRotorSpeed = 0.0F;
        }
        if (!rotorIdleLocked && !this.onGround() && (velocity.y < -1.0E-4D || forcedDescent)
                && (this.engineStartOver || currentRotorSpeed >= HELICOPTER_DESCENT_ROTOR_SPEED)) {
            nextRotorSpeed = Math.max(nextRotorSpeed, HELICOPTER_DESCENT_ROTOR_SPEED);
        }
        this.entityData.set(DATA_PROPELLER_SPEED, rotorIdleLocked ? 0.0F : nextRotorSpeed * 0.9995F);
        this.entityData.set(DATA_PROPELLER_ROT, rotorIdleLocked ? this.propellerRot() : this.propellerRot() + 30.0F * nextRotorSpeed);

        if (consumeEnergy && this.engineStart) {
            int cost = this.scaledEngineEnergyCost(engine, true, helicopterEnergyScale);
            if (!this.consumeEngineEnergyCost(cost)) {
                this.engineStart = false;
                this.engineStartOver = false;
                this.enginePower *= 0.995D;
            }
        }

        Config.HelicopterFlightSettings flight = Config.helicopterFlight();
        if (playerVerticalControl && flight.weightyFlight()) {
            float yawDelta = Mth.wrapDegrees(this.getYRot() - this.yRotO);
            velocity = this.weightyHelicopterVelocity(velocityAtTickStart, targetVerticalSpeed, engine, mobility, flight);
            this.updateWeightyHelicopterAttitude(velocity, engine, mobility, yawDelta, flight);
        } else if (playerVerticalControl) {
            // Steer the velocity directly instead of summing gravity and rotor lift, which never balance exactly and made the hover drift.
            velocity = this.playerHelicopterVelocity(velocityAtTickStart, targetVerticalSpeed, engine, mobility);
            this.updatePlayerHelicopterAttitude(velocity, engine, mobility, Mth.wrapDegrees(this.getYRot() - this.yRotO));
        } else {
            Vec3 lift = this.helicopterLiftVector(liftRotorSpeed * liftSpeed * mobility * altitudeLimitFactor);
            velocity = velocity.add(0.0D, -0.06D, 0.0D).add(lift);
        }
        if (forcedDescent) {
            double descentTarget = Math.max(verticalSpeedBeforeDrag - HELICOPTER_FORCED_DESCENT_ACCELERATION, -HELICOPTER_MAX_DESCENT_SPEED);
            velocity = new Vec3(velocity.x, Math.min(velocity.y, descentTarget), velocity.z);
        }
        if (altitudeLimited && velocity.y > 0.0D) {
            velocity = new Vec3(velocity.x, velocity.y * 0.25D, velocity.z);
        }

        if (this.engineStart && this.helicopterStartupProgress() >= 1.0F && nextRotorSpeed >= HELICOPTER_HOVER_POWER * 0.95D) {
            this.engineStartOver = true;
        }
        if (this.enginePower < 0.0004D && (!this.engineStart || this.engineStartOver)) {
            this.engineStart = false;
            this.engineStartOver = false;
        }
        if (!this.engineStart) {
            this.entityData.set(DATA_HELICOPTER_STARTUP, 0);
        }

        double maxHorizontalSpeed = playerVerticalControl ? this.playerHelicopterMaxSpeed(engine, mobility) : engine.maxForwardSpeed() * mobility;
        Vec3 horizontalVelocity = new Vec3(velocity.x, 0.0D, velocity.z);
        if (horizontalVelocity.length() > maxHorizontalSpeed) {
            horizontalVelocity = horizontalVelocity.normalize().scale(maxHorizontalSpeed);
            velocity = new Vec3(horizontalVelocity.x, velocity.y, horizontalVelocity.z);
        }

        this.moveHelicopter(new Vec3(velocity.x, Mth.clamp(velocity.y, -HELICOPTER_MAX_DESCENT_SPEED, 0.45D), velocity.z));
        this.warnHelicopterDangerousDescent(forcedDescent, altitude);
    }

    private double playerHelicopterMaxSpeed(EngineInfo engine, double mobility) {
        double maxSpeed = engine.playerMaxSpeed() > 0.0D ? engine.playerMaxSpeed() : engine.maxForwardSpeed() * HELICOPTER_PLAYER_SPEED_MULTIPLIER;
        return maxSpeed * mobility;
    }

    private Vec3 playerHelicopterVelocity(Vec3 current, double targetVerticalSpeed, EngineInfo engine, double mobility) {
        int forward = this.input.forwardAxis();
        int strafe = this.input.strafeAxis();
        double forwardSpeed = forward > 0 ? this.playerHelicopterMaxSpeed(engine, mobility) : engine.maxReverseSpeed() * mobility * HELICOPTER_PLAYER_SPEED_MULTIPLIER;
        if (forward > 0 && this.input.cruiseSpeed() > 0.0F) {
            // Cruise control holds the speed it was switched on at instead of flying flat out.
            forwardSpeed = Math.min(forwardSpeed, this.input.cruiseSpeed());
        }
        double strafeSpeed = this.playerHelicopterMaxSpeed(engine, mobility) * HELICOPTER_STRAFE_SPEED_RATIO;
        Vec3 forwardDirection = this.horizontalDirection(this.getYRot());
        Vec3 leftDirection = new Vec3(forwardDirection.z, 0.0D, -forwardDirection.x);
        Vec3 target = forwardDirection.scale(forward * forwardSpeed).add(leftDirection.scale(strafe * strafeSpeed));
        Vec3 horizontal = new Vec3(current.x, 0.0D, current.z);
        double response = (forward == 0 && strafe == 0 ? HELICOPTER_BRAKING_RESPONSE : HELICOPTER_ACCELERATION_RESPONSE) * Mth.clamp(engine.playerAcceleration(), 0.05D, 4.0D);
        horizontal = horizontal.add(target.subtract(horizontal).scale(response));
        double vertical = Mth.lerp(HELICOPTER_VERTICAL_RESPONSE, current.y, targetVerticalSpeed);
        return new Vec3(horizontal.x, vertical, horizontal.z);
    }

    private void updatePlayerHelicopterAttitude(Vec3 velocity, EngineInfo engine, double mobility, float yawDelta) {
        Vec3 forwardDirection = this.horizontalDirection(this.getYRot());
        double maxForward = Math.max(1.0E-4D, this.playerHelicopterMaxSpeed(engine, mobility));
        double maxStrafe = Math.max(1.0E-4D, maxForward * HELICOPTER_STRAFE_SPEED_RATIO);
        float forwardFraction = (float) Mth.clamp(velocity.dot(forwardDirection) / maxForward, -1.0D, 1.0D);
        // Sway follows the pilot's strafing, not the measured sideways velocity: while turning, the velocity lags the
        // heading and every mouse movement read as a sideways jolt that rocked the hull at random.
        double acceleration = Mth.clamp(engine.playerAcceleration(), 0.05D, 4.0D);
        boolean braking = this.input.forwardAxis() == 0 && this.input.strafeAxis() == 0;
        double previousStrafeSpeed = this.swayStrafeSpeed;
        this.swayStrafeSpeed += (this.input.strafeAxis() * maxStrafe - this.swayStrafeSpeed)
                * (braking ? HELICOPTER_BRAKING_RESPONSE : HELICOPTER_ACCELERATION_RESPONSE) * acceleration;
        float leftFraction = (float) Mth.clamp(this.swayStrafeSpeed / maxStrafe, -1.0D, 1.0D);
        double fullLateralAcceleration = maxStrafe * HELICOPTER_ACCELERATION_RESPONSE * acceleration;
        float leftAccelerationFraction = (float) Mth.clamp((this.swayStrafeSpeed - previousStrafeSpeed) / fullLateralAcceleration, -1.5D, 1.5D);
        // Nose down (positive pitch) into forward flight, bank (positive roll is right) into strafes and turns.
        float targetPitch = Mth.clamp(forwardFraction * HELICOPTER_MAX_FORWARD_TILT + this.pilotAimPitch, -50.0F, HELICOPTER_MAX_NOSE_DOWN);
        float targetRoll = Mth.clamp(-leftFraction * HELICOPTER_STRAFE_BANK * HELICOPTER_MAX_FORWARD_TILT
                + yawDelta * HELICOPTER_TURN_BANK, -HELICOPTER_MAX_BANK, HELICOPTER_MAX_BANK);
        this.setXRot(this.getXRot() + (targetPitch - this.getXRot()) * HELICOPTER_ATTITUDE_RESPONSE);
        // The hull hangs off the rotor like a weight: a sudden move right swings it left, it then swings through into a
        // gentle lean, and stopping throws it the other way before it rocks back upright.
        this.rollVelocity += (targetRoll - this.roll()) * HELICOPTER_ROLL_SPRING - this.rollVelocity * HELICOPTER_ROLL_DAMPING
                + leftAccelerationFraction * HELICOPTER_SWAY_INERTIA;
        this.setRoll(Mth.clamp(this.roll() + this.rollVelocity, -HELICOPTER_MAX_SWAY, HELICOPTER_MAX_SWAY));
    }

    private static double weightyHelicopterWeight(EngineInfo engine, Config.HelicopterFlightSettings flight) {
        return Mth.clamp(engine.playerWeight() * flight.weightMultiplier(), 0.5D, 3.0D);
    }

    /** Hull tilt in degrees whose sideways rotor push is {@code thrust} (the sine of the tilt). */
    private static float weightyTiltForThrust(double thrust) {
        return (float) Math.toDegrees(Math.asin(Mth.clamp(thrust, -1.0D, 1.0D)));
    }

    /** Nose-down tilt passes through; nose-up tilt follows it at first, then levels off towards the hull's limit. */
    private static float softenedNoseUp(float tilt) {
        return tilt >= 0.0F ? tilt : -WEIGHTY_HULL_MAX_NOSE_UP * (1.0F - (float) Math.exp(tilt / WEIGHTY_HULL_MAX_NOSE_UP));
    }

    /**
     * Pitch the helicopter's cameras and pilot look along. In weighty flight that is the mouse aim plus a small, slowly
     * eased share of the hull's tilt; otherwise (no pilot, arcade controls, other seats' clients) it eases after the hull.
     */
    private void tickHelicopterCameraPitch() {
        this.heliCameraPitchO = this.heliCameraPitch;
        if (this.heliCameraFromTilt) {
            this.heliCameraPitch = Mth.clamp(this.heliShownAim + this.heliCameraTilt, -50.0F, HELICOPTER_MAX_NOSE_DOWN);
            this.heliCameraFromTilt = false;
        } else {
            this.heliCameraPitch += (this.getXRot() - this.heliCameraPitch) * HELICOPTER_CAMERA_FOLLOW_RESPONSE;
        }
    }

    /** Camera pitch: steadied on helicopters, the hull's own pitch on everything else. */
    public float cameraPitch(float partialTick) {
        return this.keepsCameraLevel()
                ? Mth.lerp(partialTick, this.heliCameraPitchO, this.heliCameraPitch)
                : Mth.lerp(partialTick, this.xRotO, this.getXRot());
    }

    /** Out of player control (landed, power loss, AI) the thrust tilt and rotor lag ease back to neutral. */
    private void settleWeightyHelicopterState() {
        this.heliTiltPitch *= 0.8F;
        this.heliTiltRoll = Mth.wrapDegrees(this.heliTiltRoll) * 0.8F;
        this.heliTiltPitchVelocity = 0.0F;
        this.heliTiltRollVelocity = 0.0F;
        this.heliLift = 0.0D;
        this.manualRolling = false;
        this.heliYawRate = 0.0F;
        this.heliShownTilt *= 0.8F;
        this.heliShownAim *= 0.8F;
        this.heliCameraTilt *= 0.8F;
    }

    public boolean isBarrelRolling() {
        return this.manualRolling;
    }

    /**
     * Pilot-driven roll. While the pilot holds a double-tapped strafe key the hull spins up that way and keeps turning
     * for as long as it is held; let go and the spin carries on briefly. Past an ordinary bank nothing snaps it back:
     * the spin bleeds off and the rotor slowly rights the hull. Returns true while the roll axis is the pilot's.
     */
    private boolean tickWeightyManualRoll(double weight, Config.HelicopterFlightSettings flight) {
        int command = flight.barrelRolls() ? Integer.signum(this.input.barrelRoll()) : 0;
        this.heliTiltRoll = Mth.wrapDegrees(this.heliTiltRoll);
        boolean pastBank = Math.abs(this.heliTiltRoll) > WEIGHTY_FREE_ROLL_ANGLE;
        if (command == 0 && !pastBank) {
            this.manualRolling = false;
            return false;
        }
        if (!this.manualRolling) {
            // Picks up from the lean on show, which a strafe makes greater than the thrust tilt, so the hull does not jump.
            this.heliTiltRoll = Mth.wrapDegrees(this.roll());
        }
        this.manualRolling = true;
        float maxRate = (float) (WEIGHTY_MAX_ROLL_RATE / weight);
        if (command != 0) {
            // A positive command rolls right (positive roll).
            this.heliTiltRollVelocity = Mth.clamp(this.heliTiltRollVelocity + (float) (command * WEIGHTY_ROLL_ACCELERATION / weight),
                    -maxRate, maxRate);
        } else {
            this.heliTiltRollVelocity += (float) (-this.heliTiltRoll * WEIGHTY_ROLL_RIGHTING / weight)
                    - this.heliTiltRollVelocity * WEIGHTY_ROLL_SPIN_DAMPING;
        }
        this.heliTiltRoll = Mth.wrapDegrees(this.heliTiltRoll + this.heliTiltRollVelocity);
        return true;
    }

    private Vec3 weightyHelicopterVelocity(Vec3 current, double targetVerticalSpeed, EngineInfo engine, double mobility,
                                           Config.HelicopterFlightSettings flight) {
        double weight = weightyHelicopterWeight(engine, flight);
        int forward = this.input.forwardAxis();
        int strafe = this.input.strafeAxis();
        double maxSpeed = Math.max(1.0E-4D, this.playerHelicopterMaxSpeed(engine, mobility));
        double maxStrafe = maxSpeed * HELICOPTER_STRAFE_SPEED_RATIO;
        double sinMaxPitch = Math.sin(Math.toRadians(WEIGHTY_MAX_PITCH));
        Vec3 forwardDirection = this.horizontalDirection(this.getYRot());
        Vec3 leftDirection = new Vec3(forwardDirection.z, 0.0D, -forwardDirection.x);
        Vec3 horizontal = new Vec3(current.x, 0.0D, current.z);
        double forwardSpeed = horizontal.dot(forwardDirection);
        double leftSpeed = horizontal.dot(leftDirection);

        // 1. The stick asks for a tilt. Hands off, the nose flares up against the remaining speed and the hull levels.
        // Positive pitch is nose down; positive roll banks right.
        float targetPitch;
        if (forward > 0) {
            double fraction = this.input.cruiseSpeed() > 0.0F ? Math.min(1.0D, this.input.cruiseSpeed() / maxSpeed) : 1.0D;
            targetPitch = weightyTiltForThrust(fraction * sinMaxPitch);
        } else if (forward < 0 && forwardSpeed > 0.15D * maxSpeed) {
            // S while still moving forward: haul the nose up hard and trade the speed away.
            targetPitch = (float) (-WEIGHTY_HARD_FLARE_PITCH * Mth.clamp(1.5D * forwardSpeed / maxSpeed, 0.35D, 1.0D));
        } else if (forward < 0) {
            double reverseSpeed = engine.maxReverseSpeed() * mobility * HELICOPTER_PLAYER_SPEED_MULTIPLIER;
            targetPitch = -weightyTiltForThrust(Math.min(1.0D, reverseSpeed / maxSpeed) * sinMaxPitch);
        } else {
            targetPitch = (float) (-Mth.clamp(forwardSpeed / maxSpeed, -1.0D, 1.0D) * WEIGHTY_FLARE_PITCH);
        }
        float targetRoll = strafe != 0
                ? -strafe * weightyTiltForThrust(HELICOPTER_STRAFE_SPEED_RATIO * sinMaxPitch)
                : (float) (Mth.clamp(leftSpeed / maxStrafe, -1.0D, 1.0D) * WEIGHTY_LEVEL_ROLL);

        // 2. The hull swings to that tilt on a spring; heavier airframes tilt more slowly and rock for longer.
        // While the pilot rolls by hand (or the hull is still past an ordinary bank) the roll axis is theirs.
        double tiltSpring = WEIGHTY_TILT_SPRING / weight;
        double tiltDamping = WEIGHTY_TILT_DAMPING / Math.sqrt(weight);
        this.heliTiltPitchVelocity += (float) ((targetPitch - this.heliTiltPitch) * tiltSpring - this.heliTiltPitchVelocity * tiltDamping);
        this.heliTiltPitch += this.heliTiltPitchVelocity;
        boolean rolling = this.tickWeightyManualRoll(weight, flight);
        if (!rolling) {
            this.heliTiltRollVelocity += (float) ((targetRoll - this.heliTiltRoll) * tiltSpring - this.heliTiltRollVelocity * tiltDamping);
            this.heliTiltRoll += this.heliTiltRollVelocity;
        }

        // 3. Rotor thrust. The pilot assist trims it to hold altitude through ordinary tilts; past 35 degrees of bank, and
        // above all upside down, gravity wins and the helicopter drops.
        double pitchRadians = Math.toRadians(this.heliTiltPitch);
        double rollRadians = Math.toRadians(this.heliTiltRoll);
        double uprightness = Math.cos(pitchRadians) * Math.cos(rollRadians);
        double tiltAmount = 0.0D;
        double pitchThrust = Math.sin(pitchRadians) * Math.cos(rollRadians) / sinMaxPitch;
        double rollThrust = Math.sin(rollRadians) / sinMaxPitch;
        double verticalTarget = targetVerticalSpeed;
        if (!rolling) {
            tiltAmount = Math.min(pitchThrust * pitchThrust + rollThrust * rollThrust, 1.5D);
            verticalTarget -= WEIGHTY_MAX_TILT_SINK * flight.tiltSink() * Math.min(weight, 1.0D) * tiltAmount;
            // A flare at speed balloons upward as the forward momentum turns into height.
            verticalTarget += WEIGHTY_FLARE_BALLOON * Math.max(0.0D, -pitchThrust) * Mth.clamp(forwardSpeed / maxSpeed, 0.0D, 1.0D);
        }
        double desiredLift = (verticalTarget - current.y) * WEIGHTY_VERTICAL_GAIN;
        this.heliLift += (desiredLift - this.heliLift) * WEIGHTY_LIFT_RESPONSE / weight;
        this.heliLift = Mth.clamp(this.heliLift, -WEIGHTY_GRAVITY, WEIGHTY_MAX_EXTRA_LIFT * WEIGHTY_GRAVITY);
        double thrust = WEIGHTY_GRAVITY + this.heliLift;
        if (uprightness < 0.0D) {
            thrust *= WEIGHTY_INVERTED_THRUST;
        }
        double trim = 1.0D / Math.max(uprightness, WEIGHTY_TRIM_LIMIT_COS);
        double verticalAcceleration = thrust * uprightness * trim - WEIGHTY_GRAVITY;

        // 4. The tilted rotor pushes along; drag makes the full tilt settle at exactly the helicopter's top speed.
        // Mid-roll the push follows the real thrust, so the roll arcs out to the side and back.
        double drag = 1.0D / (WEIGHTY_SPEED_TIME_TICKS * weight);
        double fullThrust = maxSpeed * drag / (1.0D - drag);
        if (rolling) {
            double thrustShare = thrust / WEIGHTY_GRAVITY;
            pitchThrust = Math.sin(pitchRadians) * Math.cos(rollRadians) * thrustShare;
            rollThrust = Math.sin(rollRadians) * thrustShare;
        } else {
            // Tilt that opposes the motion only takes a share off it, so speed in any direction has to be worked off.
            if (pitchThrust * forwardSpeed < 0.0D) {
                pitchThrust *= forward < 0 ? WEIGHTY_HARD_BRAKE_SHARE : WEIGHTY_COAST_BRAKE_SHARE;
            }
            // Positive roll thrust pushes right, away from leftSpeed.
            if (rollThrust * leftSpeed > 0.0D) {
                rollThrust *= strafe != 0 ? WEIGHTY_STRAFE_BRAKE_SHARE : WEIGHTY_COAST_BRAKE_SHARE;
            }
        }
        horizontal = horizontal.add(forwardDirection.scale(pitchThrust * fullThrust)).add(leftDirection.scale(-rollThrust * fullThrust));

        // 5. Turning swings the nose first; the momentum follows it round over a moment, so fast turns drift.
        double along = horizontal.dot(forwardDirection);
        double speed = horizontal.length();
        if (strafe == 0 && !rolling && along > 0.0D && speed > 1.0E-4D) {
            double grip = WEIGHTY_MAX_TURN_GRIP * flight.turnGrip() * Mth.clamp(2.0D * along / maxSpeed, 0.0D, 1.0D) / weight;
            Vec3 direction = horizontal.scale(1.0D / speed);
            Vec3 turned = direction.add(forwardDirection.subtract(direction).scale(grip));
            if (turned.lengthSqr() > 1.0E-8D) {
                horizontal = turned.normalize().scale(speed);
            }
        }
        horizontal = horizontal.scale(1.0D - drag);
        if (forward == 0 && strafe == 0 && !rolling && horizontal.length() < WEIGHTY_HOVER_SETTLE_SPEED * maxSpeed) {
            horizontal = horizontal.scale(WEIGHTY_HOVER_SETTLE);
        }
        return new Vec3(horizontal.x, current.y + verticalAcceleration, horizontal.z);
    }

    private void updateWeightyHelicopterAttitude(Vec3 velocity, EngineInfo engine, double mobility, float yawDelta,
                                                 Config.HelicopterFlightSettings flight) {
        double weight = weightyHelicopterWeight(engine, flight);
        double maxSpeed = Math.max(1.0E-4D, this.playerHelicopterMaxSpeed(engine, mobility));
        double maxStrafe = maxSpeed * HELICOPTER_STRAFE_SPEED_RATIO;
        // The hull shows the thrust tilt; the pilot's mouse aim rides on top without pushing the helicopter.
        this.heliShownTilt += (softenedNoseUp(this.heliTiltPitch) - this.heliShownTilt) * WEIGHTY_HULL_TILT_RESPONSE;
        this.heliShownAim += (this.pilotAimPitch - this.heliShownAim) * WEIGHTY_AIM_RESPONSE;
        this.setXRot(Mth.clamp(this.heliShownTilt + this.heliShownAim, -50.0F, HELICOPTER_MAX_NOSE_DOWN));
        this.heliCameraTilt += (this.heliShownTilt * WEIGHTY_CAMERA_TILT_SHARE - this.heliCameraTilt) * WEIGHTY_CAMERA_TILT_RESPONSE;
        this.heliCameraFromTilt = true;

        // Same weighted sway as before, layered on the thrust bank: a sudden strafe swings the hull the other way first.
        double previousStrafeSpeed = this.swayStrafeSpeed;
        boolean braking = this.input.forwardAxis() == 0 && this.input.strafeAxis() == 0;
        double swayResponse = (braking ? HELICOPTER_BRAKING_RESPONSE : HELICOPTER_ACCELERATION_RESPONSE) / weight;
        this.swayStrafeSpeed += (this.input.strafeAxis() * maxStrafe - this.swayStrafeSpeed) * swayResponse;
        if (this.isBarrelRolling()) {
            // The pilot is rolling the hull by hand; no spring or sway on top of it.
            this.rollVelocity = 0.0F;
            this.setRoll(this.heliTiltRoll);
            return;
        }
        double fullLateralAcceleration = maxStrafe * HELICOPTER_ACCELERATION_RESPONSE / weight;
        float leftAccelerationFraction = (float) Mth.clamp((this.swayStrafeSpeed - previousStrafeSpeed) / fullLateralAcceleration, -1.5D, 1.5D);
        double speedFraction = Mth.clamp(velocity.horizontalDistance() / maxSpeed, 0.0D, 1.0D);
        float turnBank = (float) (yawDelta * HELICOPTER_TURN_BANK * (0.5D + 0.5D * speedFraction));
        // The extra lean goes with the strafe and fades as it is let go, so stopping does not whip the hull across.
        float strafing = (float) Mth.clamp(Math.abs(this.swayStrafeSpeed) / maxStrafe, 0.0D, 1.0D);
        float shownBank = this.heliTiltRoll * Mth.lerp(strafing, 1.0F, WEIGHTY_STRAFE_BANK_GAIN);
        float targetRoll = Mth.clamp(shownBank + turnBank, -WEIGHTY_MAX_BANK, WEIGHTY_MAX_BANK);
        this.rollVelocity += (float) ((targetRoll - this.roll()) * WEIGHTY_VISUAL_ROLL_SPRING / weight
                - this.rollVelocity * WEIGHTY_VISUAL_ROLL_DAMPING / Math.sqrt(weight)
                + leftAccelerationFraction * WEIGHTY_SWAY_INERTIA * weight);
        this.setRoll(Mth.clamp(this.roll() + this.rollVelocity, -HELICOPTER_MAX_SWAY, HELICOPTER_MAX_SWAY));
    }

    private void warnHelicopterDangerousDescent(boolean forcedDescent, double altitude) {
        // A parked helicopter with its engine off is not falling, even on a tick where it is briefly off the ground
        // (boarding it, settling on its skids): only warn of a forced descent when it is high up and actually dropping.
        if (forcedDescent && (altitude <= HELICOPTER_FORCED_DESCENT_WARNING_MIN_ALTITUDE
                || this.getDeltaMovement().y > -HELICOPTER_FORCED_DESCENT_WARNING_SINK_SPEED)) {
            return;
        }
        if (this.level().isClientSide()
                || (!forcedDescent && (altitude <= HELICOPTER_DANGEROUS_DESCENT_WARNING_MIN_ALTITUDE
                || this.getDeltaMovement().y >= -HELICOPTER_SAFE_DESCENT_SPEED
                || this.getDeltaMovement().horizontalDistance() >= HELICOPTER_LOW_AIRSPEED_THRESHOLD))) {
            return;
        }
        String warningKey = this.helicopterDangerWarningKey(forcedDescent);
        this.showVehicleWarning(warningKey);
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof ServerPlayer player) {
                this.playVehicleWarningSound(warningKey, VehicleSoundHelper.missileWarning(), HELICOPTER_DANGEROUS_DESCENT_WARNING_VOLUME, 1.0F);
            }
        }
    }

    private String helicopterDangerWarningKey(boolean forcedDescent) {
        if (forcedDescent) {
            return "message.jeg.vehicle.helicopter_low_speed_warning";
        }
        if (this.isLowHealthDecayActive()) {
            return "message.jeg.vehicle.helicopter_critical_damage_warning";
        }
        if (this.maxVehicleEnergy() > 0 && this.vehicleEnergy() <= 0) {
            return "message.jeg.vehicle.helicopter_power_loss_warning";
        }
        return "message.jeg.vehicle.helicopter_low_speed_warning";
    }

    private void moveHelicopter(Vec3 velocity) {
        Vec3 before = this.position();
        this.setDeltaMovement(velocity);
        this.move(MoverType.SELF, velocity);
        Vec3 moved = this.position().subtract(before);
        if (moved.lengthSqr() > 1.0E-7D || this.horizontalCollision || this.verticalCollision) {
            this.hurtMarked = true;
            this.needsSync = true;
        }
    }

    private void tickServerBoatMovement(EngineInfo engine) {
        this.tickBoatMovement(engine, true);
    }

    private double mobilityMultiplier() {
        double multiplier = this.isEngineDamaged() ? 0.35D : 1.0D;
        if (this.isLeftWheelDamaged()) {
            multiplier *= 0.75D;
        }
        if (this.isRightWheelDamaged()) {
            multiplier *= 0.75D;
        }
        return Math.max(0.2D, multiplier);
    }

    private void applyPassengerYaw() {
        Entity passenger = this.getControllingPassenger();
        VehicleType type = this.vehicleData().defaults().vehicleType();
        if (passenger == null || this.isFreeLookInputDown() || type == VehicleType.LAND || type == VehicleType.HELICOPTER || type == VehicleType.AIRCRAFT) {
            return;
        }
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        if (fallbackIndex >= 0 && this.usesArticulatedSeatTransform(this.seatForPassenger(passenger, fallbackIndex))) {
            return;
        }
        this.setYRot(passenger.getYRot());
        this.yRotO = this.getYRot();
    }

    @Override
    public void positionRider(@NotNull Entity passenger, @NotNull MoveFunction callback) {
        if (!this.hasPassenger(passenger)) {
            return;
        }
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        SeatInfo seat = this.seatForPassenger(passenger, fallbackIndex);
        Vec3 offset = this.seatOffset(seat, 0.0D, 1.0F);
        callback.accept(passenger, this.getX() + offset.x, this.getY() + offset.y, this.getZ() + offset.z);
        if (passenger instanceof LivingEntity living) {
            living.fallDistance = 0.0F;
        }
    }

    private Vec3 rotateSeatOffset(SeatInfo seat) {
        return this.seatOffset(seat, 0.0D, 1.0F);
    }

    private Vec3 seatOffset(SeatInfo seat, double eyeHeight, float partialTick) {
        if (this.usesVehiclePoseTransform()) {
            return this.rotateLocalOffsetWithPose(seat.x(), seat.y() + eyeHeight, seat.z(), partialTick);
        }
        return this.rotateLocalOffset(seat.x(), seat.y() + eyeHeight, seat.z(), partialTick);
    }

    public boolean usesVehiclePoseTransform() {
        VehicleType type = this.vehicleData().defaults().vehicleType();
        return type == VehicleType.HELICOPTER || type == VehicleType.AIRCRAFT;
    }

    public Vec3 vehiclePoseOffset(double localX, double localY, double localZ, float partialTick) {
        return this.rotateLocalOffsetWithPose(localX, localY, localZ, partialTick);
    }

    private Vec3 firstPersonSeatCameraOffset(double localX, double localY, double localZ, float partialTick) {
        if (this.usesVehiclePoseTransform()) {
            return this.seatEyeOffsetWithPose(localX, localY, localZ, partialTick);
        }
        return this.rotateLocalOffset(localX, localY, localZ, partialTick);
    }

    /** First-person eye inside the hull: turned by the camera's roll, so a barrel roll keeps the eye in the cockpit. */
    private Vec3 seatEyeOffsetWithPose(double localX, double localY, double localZ, float partialTick) {
        if (!this.keepsCameraLevel()) {
            return this.rotateLocalOffsetWithPose(localX, localY, localZ, partialTick);
        }
        Vec3 origin = this.interpolatedVehiclePosition(partialTick);
        Vector4d worldPosition = this.vehiclePoseTransformWithRoll(partialTick, this.cameraRoll(partialTick))
                .transform(new Vector4d(localX, localY, localZ, 1.0D));
        return new Vec3(worldPosition.x - origin.x, worldPosition.y - origin.y, worldPosition.z - origin.z);
    }

    /** Offset of a camera that orbits the vehicle: turned by the camera's pitch, without roll. */
    private Vec3 chaseCameraOffset(double localX, double localY, double localZ, float partialTick) {
        Vec3 origin = this.interpolatedVehiclePosition(partialTick);
        double pivotY = this.rotateOffsetHeight();
        Matrix4d transform = new Matrix4d();
        transform.translate(origin.x, origin.y + pivotY, origin.z);
        transform.rotate(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, this.yRotO, this.getYRot())));
        transform.rotate(Axis.XP.rotationDegrees(this.cameraPitch(partialTick)));
        transform.translate(0.0D, -pivotY, 0.0D);
        Vector4d worldPosition = transform.transform(new Vector4d(localX, localY, localZ, 1.0D));
        return new Vec3(worldPosition.x - origin.x, worldPosition.y - origin.y, worldPosition.z - origin.z);
    }

    private Vec3 cameraOffsetWithPose(double localX, double localY, double localZ, float partialTick) {
        return this.keepsCameraLevel()
                ? this.chaseCameraOffset(localX, localY, localZ, partialTick)
                : this.rotateLocalOffsetWithPose(localX, localY, localZ, partialTick);
    }

    private Vec3 simulatedThirdPersonCameraPosition(Entity passenger, CameraPos camera, float partialTick) {
        Vec3 view = passenger.getViewVector(partialTick);
        double x = Mth.lerp(partialTick, passenger.xo, passenger.getX()) - camera.simulatedThirdPersonDistance() * view.x;
        double y = Mth.lerp(partialTick, passenger.yo + passenger.getEyeHeight() + camera.simulatedThirdPersonHeight(), passenger.getEyeY() + camera.simulatedThirdPersonHeight())
                - camera.simulatedThirdPersonDistance() * view.y;
        double z = Mth.lerp(partialTick, passenger.zo, passenger.getZ()) - camera.simulatedThirdPersonDistance() * view.z;
        return new Vec3(x, y, z);
    }

    private Vec3 fixedSeatCameraPosition(CameraPos camera, float partialTick) {
        if ("barrel".equalsIgnoreCase(camera.transform())) {
            return this.articulatedBarrelPosition(camera.x(), camera.y(), camera.z(), partialTick);
        }
        Vec3 offset = this.usesVehiclePoseTransform()
                ? this.seatEyeOffsetWithPose(camera.x(), camera.y(), camera.z(), partialTick)
                : this.rotateLocalOffset(camera.x(), camera.y(), camera.z(), partialTick);
        return this.interpolatedVehiclePosition(partialTick).add(offset);
    }

    private boolean usesArticulatedSeatTransform(SeatInfo seat) {
        return this.vehicleData().defaults().turret().enabled() && seat.index() == this.vehicleData().defaults().turret().seatIndex();
    }

    private boolean usesArticulatedTurretAim(LivingEntity shooter) {
        VehicleWeaponInfo weapon = this.selectedWeapon(shooter);
        if (weapon == null || !this.vehicleData().defaults().turret().enabled()) {
            return false;
        }
        int turretSeat = this.vehicleData().defaults().turret().seatIndex();
        return this.canUseSelectedWeapon(shooter, weapon)
                && this.seatIndexForPassenger(shooter, this.getPassengers().indexOf(shooter)) == turretSeat
                && (!weapon.guided() || this.guidedWeaponsUseArticulatedTurret());
    }

    private boolean usesArticulatedTurretMuzzle(VehicleWeaponInfo weapon) {
        if (!this.vehicleData().defaults().turret().enabled() || !weapon.hasMuzzle()) {
            return false;
        }
        if (weapon.turretMounted() != null) {
            return weapon.turretMounted();
        }
        return !weapon.guided() || this.guidedWeaponsUseArticulatedTurret();
    }

    private Vec3 rotateLocalOffset(double localX, double localY, double localZ) {
        return this.rotateLocalOffset(localX, localY, localZ, 1.0F);
    }

    private Vec3 rotateLocalOffset(double localX, double localY, double localZ, float partialTick) {
        double yaw = Math.toRadians(Mth.rotLerp(partialTick, this.yRotO, this.getYRot()));
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double x = localX * cos - localZ * sin;
        double z = localX * sin + localZ * cos;
        return new Vec3(x, localY, z);
    }

    private Vec3 toLocalVehicleSpace(Vec3 world) {
        double dx = world.x - this.getX();
        double dz = world.z - this.getZ();
        double yaw = Math.toRadians(this.getYRot());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double x = dx * cos + dz * sin;
        double z = -dx * sin + dz * cos;
        return new Vec3(x, world.y - this.getY(), z);
    }

    private Vec3 rotateLocalOffsetWithPose(double localX, double localY, double localZ, float partialTick) {
        Vec3 origin = this.interpolatedVehiclePosition(partialTick);
        Vector4d worldPosition = this.vehiclePoseTransform(partialTick).transform(new Vector4d(localX, localY, localZ, 1.0D));
        return new Vec3(worldPosition.x - origin.x, worldPosition.y - origin.y, worldPosition.z - origin.z);
    }

    private Vec3 rotateLocalOffsetWithPoseNoRoll(double localX, double localY, double localZ, float partialTick) {
        Vec3 origin = this.interpolatedVehiclePosition(partialTick);
        Vector4d worldPosition = this.vehiclePoseTransformNoRoll(partialTick).transform(new Vector4d(localX, localY, localZ, 1.0D));
        return new Vec3(worldPosition.x - origin.x, worldPosition.y - origin.y, worldPosition.z - origin.z);
    }

    private Vec3 rotateLocalDirectionWithPose(double localX, double localY, double localZ, float partialTick) {
        Vector4d localOrigin = this.vehiclePoseRotation(partialTick).transform(new Vector4d(0.0D, 0.0D, 0.0D, 1.0D));
        Vector4d worldDirection = this.vehiclePoseRotation(partialTick).transform(new Vector4d(localX, localY, localZ, 1.0D));
        return new Vec3(worldDirection.x - localOrigin.x, worldDirection.y - localOrigin.y, worldDirection.z - localOrigin.z);
    }

    private Matrix4d vehiclePoseTransform(float partialTick) {
        Vec3 origin = this.interpolatedVehiclePosition(partialTick);
        double pivotY = this.rotateOffsetHeight();
        Matrix4d transform = new Matrix4d();
        transform.translate(origin.x, origin.y + pivotY, origin.z);
        transform.rotate(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, this.yRotO, this.getYRot())));
        transform.rotate(Axis.XP.rotationDegrees(Mth.lerp(partialTick, this.xRotO, this.getXRot())));
        transform.rotate(Axis.ZP.rotationDegrees(this.roll(partialTick)));
        transform.translate(0.0D, -pivotY, 0.0D);
        return transform;
    }

    private Matrix4d vehiclePoseTransformNoRoll(float partialTick) {
        return this.vehiclePoseTransformWithRoll(partialTick, 0.0F);
    }

    private Matrix4d vehiclePoseTransformWithRoll(float partialTick, float roll) {
        Vec3 origin = this.interpolatedVehiclePosition(partialTick);
        double pivotY = this.rotateOffsetHeight();
        Matrix4d transform = new Matrix4d();
        transform.translate(origin.x, origin.y + pivotY, origin.z);
        transform.rotate(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, this.yRotO, this.getYRot())));
        transform.rotate(Axis.XP.rotationDegrees(Mth.lerp(partialTick, this.xRotO, this.getXRot())));
        if (roll != 0.0F) {
            transform.rotate(Axis.ZP.rotationDegrees(roll));
        }
        transform.translate(0.0D, -pivotY, 0.0D);
        return transform;
    }

    private Matrix4d vehiclePoseRotation(float partialTick) {
        Matrix4d transform = new Matrix4d();
        transform.rotate(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, this.yRotO, this.getYRot())));
        transform.rotate(Axis.XP.rotationDegrees(Mth.lerp(partialTick, this.xRotO, this.getXRot())));
        transform.rotate(Axis.ZP.rotationDegrees(this.roll(partialTick)));
        return transform;
    }

    private Vec3 articulatedTurretOffset(double localX, double localY, double localZ, float partialTick) {
        var turret = this.vehicleData().defaults().turret();
        if (this.usesVehiclePoseTransform()) {
            Vec3 turretOrigin = this.rotateLocalOffsetWithPose(turret.originX(), turret.originY(), turret.originZ(), partialTick);
            Vec3 turretLocal = this.rotateLocalOffsetByYaw(localX, localY, localZ, -this.turretYaw(partialTick));
            return turretOrigin.add(this.rotateLocalDirectionWithPose(turretLocal.x, turretLocal.y, turretLocal.z, partialTick));
        }
        Vec3 turretOrigin = this.rotateLocalOffset(turret.originX(), turret.originY(), turret.originZ(), partialTick);
        Vec3 turretLocal = this.rotateLocalOffsetByYaw(localX, localY, localZ, this.articulatedWorldTurretYaw(partialTick));
        return turretOrigin.add(turretLocal);
    }

    private Vec3 articulatedBarrelPosition(double localX, double localY, double localZ, float partialTick) {
        var turret = this.vehicleData().defaults().turret();
        Vec3 barrelOrigin = this.articulatedTurretOffset(turret.barrelX(), turret.barrelY(), turret.barrelZ(), partialTick);
        Vec3 barrelLocal = this.usesVehiclePoseTransform()
                ? this.rotateLocalOffsetByYawAndPitch(localX, localY, localZ, -this.turretYaw(partialTick), this.articulatedTurretPitch(partialTick))
                : this.rotateLocalOffsetByYawAndPitch(localX, localY, localZ, this.articulatedWorldTurretYaw(partialTick), this.articulatedTurretPitch(partialTick));
        Vec3 barrelOffset = this.usesVehiclePoseTransform()
                ? this.rotateLocalDirectionWithPose(barrelLocal.x, barrelLocal.y, barrelLocal.z, partialTick)
                : barrelLocal;
        return this.interpolatedVehiclePosition(partialTick)
                .add(barrelOrigin)
                .add(barrelOffset);
    }

    private Vec3 articulatedBarrelDirection(float partialTick) {
        if (this.usesVehiclePoseTransform()) {
            Vec3 barrelLocal = this.rotateLocalOffsetByYawAndPitch(0.0D, 0.0D, 1.0D, -this.turretYaw(partialTick), this.articulatedTurretPitch(partialTick));
            return this.rotateLocalDirectionWithPose(barrelLocal.x, barrelLocal.y, barrelLocal.z, partialTick);
        }
        return this.rotateLocalOffsetByYawAndPitch(0.0D, 0.0D, 1.0D, this.articulatedWorldTurretYaw(partialTick), this.articulatedTurretPitch(partialTick));
    }

    private float articulatedWorldTurretYaw(float partialTick) {
        return Mth.rotLerp(partialTick, this.yRotO, this.getYRot()) - this.turretYaw(partialTick);
    }

    private float articulatedTurretPitch(float partialTick) {
        return this.turretPitch(partialTick);
    }

    private Vec3 rotateLocalOffsetByYawAndPitch(double localX, double localY, double localZ, float yaw, float pitch) {
        double pitchRadians = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRadians);
        double sinPitch = Math.sin(pitchRadians);
        double pitchedY = localY * cosPitch - localZ * sinPitch;
        double pitchedZ = localY * sinPitch + localZ * cosPitch;
        return this.rotateLocalOffsetByYaw(localX, pitchedY, pitchedZ, yaw);
    }

    private Vec3 rotateLocalOffsetByYaw(double localX, double localY, double localZ, float yaw) {
        double yawRadians = Math.toRadians(yaw);
        double cos = Math.cos(yawRadians);
        double sin = Math.sin(yawRadians);
        double x = localX * cos - localZ * sin;
        double z = localX * sin + localZ * cos;
        return new Vec3(x, localY, z);
    }

    private float yRotFromVector(Vec3 vec3) {
        return (float) -Math.toDegrees(Math.atan2(vec3.x, vec3.z));
    }

    private float xRotFromVector(Vec3 vec3) {
        return (float) -Math.toDegrees(Math.atan2(vec3.y, vec3.horizontalDistance()));
    }

    private Vec3 interpolatedVehiclePosition(float partialTick) {
        return new Vec3(
                Mth.lerp(partialTick, this.xo, this.getX()),
                Mth.lerp(partialTick, this.yo, this.getY()),
                Mth.lerp(partialTick, this.zo, this.getZ())
        );
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putString(TAG_VEHICLE_ID, this.entityData.get(DATA_VEHICLE_ID));
        output.putFloat(TAG_HEALTH, this.vehicleHealth());
        output.putInt(TAG_ENERGY, this.vehicleEnergy());
        output.putInt(TAG_REPAIR_COOLDOWN, this.repairCooldown);
        output.putInt(TAG_SELECTED_WEAPON, this.entityData.get(DATA_SELECTED_WEAPON));
        ValueOutput.ValueOutputList selectedWeapons = output.childrenList(TAG_SELECTED_WEAPONS);
        int seatCount = this.vehicleData().defaults().seats().size();
        for (int seatIndex = 0; seatIndex < seatCount; seatIndex++) {
            ValueOutput selectedWeaponTag = selectedWeapons.addChild();
            selectedWeaponTag.putInt(TAG_SEAT_INDEX, seatIndex);
            selectedWeaponTag.putInt(TAG_LOADED_WEAPON_SLOT, this.selectedVehicleWeaponIndexForSeat(seatIndex));
        }
        ValueOutput.ValueOutputList loadedAmmo = output.childrenList(TAG_LOADED_WEAPON_AMMO);
        for (Map.Entry<Integer, Integer> entry : this.loadedAmmoByWeaponSlot.entrySet()) {
            ValueOutput weaponTag = loadedAmmo.addChild();
            weaponTag.putInt(TAG_LOADED_WEAPON_SLOT, entry.getKey());
            weaponTag.putInt(TAG_LOADED_WEAPON_COUNT, entry.getValue());
        }
        output.putInt(TAG_DECOY_COOLDOWN, this.decoyCooldown);
        output.putFloat(TAG_LEFT_WHEEL_HEALTH, this.leftWheelHealth);
        output.putFloat(TAG_RIGHT_WHEEL_HEALTH, this.rightWheelHealth);
        output.putFloat(TAG_ENGINE_HEALTH, this.engineHealth);
        output.putFloat(TAG_SUB_ENGINE_HEALTH, this.subEngineHealth);
        output.putFloat(TAG_TURRET_HEALTH, this.turretHealth);
        net.minecraft.world.item.DyeColor paint = this.paint();
        if (paint != null) {
            output.putString(TAG_PAINT, paint.getName());
        }
        if (!this.persistentData.isEmpty()) {
            output.store(TAG_PERSISTENT_DATA, CompoundTag.CODEC, this.persistentData.copy());
        }
        ValueOutput.ValueOutputList seats = output.childrenList(TAG_SEAT_ASSIGNMENTS);
        for (Map.Entry<UUID, Integer> assignment : this.seatAssignments.entrySet()) {
            ValueOutput seat = seats.addChild();
            seat.putString(TAG_SEAT_PASSENGER, assignment.getKey().toString());
            seat.putInt(TAG_SEAT_INDEX, assignment.getValue());
        }
        ContainerHelper.saveAllItems(output.child(TAG_ITEMS), this.inventory.getItems());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        this.selectedWeaponSlotBySeat.clear();
        this.entityData.set(DATA_VEHICLE_ID, input.getStringOr(TAG_VEHICLE_ID, this.entityData.get(DATA_VEHICLE_ID)));
        float health = input.getFloatOr(TAG_HEALTH, this.maxVehicleHealth());
        this.entityData.set(DATA_HEALTH, Mth.clamp(health, 0.0F, this.maxVehicleHealth()));
        int energy = input.getIntOr(TAG_ENERGY, this.maxVehicleEnergy());
        this.entityData.set(DATA_ENERGY, Mth.clamp(energy, 0, this.maxVehicleEnergy()));
        this.repairCooldown = input.getIntOr(TAG_REPAIR_COOLDOWN, 0);
        this.entityData.set(DATA_SELECTED_WEAPON, this.readSelectedWeapon(input));
        this.readSelectedWeaponsBySeat(input);
        this.readLoadedWeaponAmmo(input);
        this.reloadTicksByWeaponSlot.clear();
        this.fireCooldownByWeaponSlot.clear();
        this.decoyCooldown = input.getIntOr(TAG_DECOY_COOLDOWN, 0);
        this.leftWheelHealth = input.getFloatOr(TAG_LEFT_WHEEL_HEALTH, PART_MAX_HEALTH);
        this.rightWheelHealth = input.getFloatOr(TAG_RIGHT_WHEEL_HEALTH, PART_MAX_HEALTH);
        this.engineHealth = input.getFloatOr(TAG_ENGINE_HEALTH, PART_MAX_HEALTH);
        this.subEngineHealth = input.getFloatOr(TAG_SUB_ENGINE_HEALTH, PART_MAX_HEALTH);
        this.turretHealth = input.getFloatOr(TAG_TURRET_HEALTH, PART_MAX_HEALTH);
        String paint = input.getStringOr(TAG_PAINT, "");
        this.setPaint(paint.isEmpty() ? null : net.minecraft.world.item.DyeColor.byName(paint, null));
        this.clearPersistentData();
        input.read(TAG_PERSISTENT_DATA, CompoundTag.CODEC).ifPresent(this.persistentData::merge);
        this.readSeatAssignments(input);
        this.syncPartDamageFlags();
        input.child(TAG_ITEMS).ifPresent(items -> ContainerHelper.loadAllItems(items, this.inventory.getItems()));
        this.syncSelectedWeaponAmmoState();
    }

    private void clearPersistentData() {
        for (String key : Set.copyOf(this.persistentData.keySet())) {
            this.persistentData.remove(key);
        }
    }

    private int readSelectedWeapon(ValueInput input) {
        int weaponCount = this.vehicleData().defaults().weapons().size();
        if (weaponCount <= 0) {
            return 0;
        }
        return Mth.clamp(input.getIntOr(TAG_SELECTED_WEAPON, 0), 0, weaponCount - 1);
    }

    private void readSelectedWeaponsBySeat(ValueInput input) {
        int seatCount = this.vehicleData().defaults().seats().size();
        int weaponCount = this.vehicleData().defaults().weapons().size();
        if (seatCount <= 0 || weaponCount <= 0) {
            return;
        }
        ValueInput.ValueInputList selectedWeapons = input.childrenListOrEmpty(TAG_SELECTED_WEAPONS);
        if (!selectedWeapons.isEmpty()) {
            for (ValueInput selectedWeapon : selectedWeapons) {
                int seatIndex = selectedWeapon.getIntOr(TAG_SEAT_INDEX, -1);
                int slot = selectedWeapon.getIntOr(TAG_LOADED_WEAPON_SLOT, -1);
                this.setSelectedWeaponSlotForSeat(seatIndex, slot);
            }
        }
        if (this.selectedWeaponSlotBySeat.isEmpty()) {
            this.setSelectedWeaponSlotForSeat(0, this.readSelectedWeapon(input));
        }
        for (int seatIndex = 0; seatIndex < seatCount; seatIndex++) {
            if (!this.selectedWeaponSlotBySeat.containsKey(seatIndex)) {
                int firstSlot = this.firstUsableWeaponSlotForSeat(seatIndex);
                if (firstSlot >= 0) {
                    this.setSelectedWeaponSlotForSeat(seatIndex, firstSlot);
                }
            }
        }
    }

    private void readLoadedWeaponAmmo(ValueInput input) {
        this.loadedAmmoByWeaponSlot.clear();
        ValueInput.ValueInputList loadedAmmo = input.childrenListOrEmpty(TAG_LOADED_WEAPON_AMMO);
        if (loadedAmmo.isEmpty()) {
            return;
        }
        int weaponCount = this.vehicleData().defaults().weapons().size();
        for (ValueInput weaponTag : loadedAmmo) {
            int slot = weaponTag.getIntOr(TAG_LOADED_WEAPON_SLOT, -1);
            int ammo = weaponTag.getIntOr(TAG_LOADED_WEAPON_COUNT, 0);
            if (slot >= 0 && slot < weaponCount && ammo > 0) {
                this.loadedAmmoByWeaponSlot.put(slot, ammo);
            }
        }
    }

    private void readSeatAssignments(ValueInput input) {
        this.seatAssignments.clear();
        ValueInput.ValueInputList seats = input.childrenListOrEmpty(TAG_SEAT_ASSIGNMENTS);
        if (seats.isEmpty()) {
            return;
        }
        int seatCount = this.vehicleData().defaults().seats().size();
        for (ValueInput seat : seats) {
            String passenger = seat.getStringOr(TAG_SEAT_PASSENGER, "");
            if (passenger.isBlank()) {
                continue;
            }
            int seatIndex = seat.getIntOr(TAG_SEAT_INDEX, -1);
            if (seatIndex >= 0 && seatIndex < seatCount) {
                try {
                    this.seatAssignments.put(UUID.fromString(passenger), seatIndex);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public CompoundTag saveVehicleContainerState() {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, this.level().registryAccess());
        this.addAdditionalSaveData(output);
        return output.buildResult();
    }

    public void loadVehicleContainerState(CompoundTag tag) {
        this.readAdditionalSaveData(TagValueInput.create(ProblemReporter.DISCARDING, this.level().registryAccess(), tag));
        this.refreshDimensions();
    }

    @Override
    public boolean hurtServer(ServerLevel serverLevel, DamageSource source, float amount) {
        if (this.isRemoved() || this.isInvulnerableToBase(source)) {
            return false;
        }
        if (this.isEnemyAiVehicle() && source.getEntity() instanceof LivingEntity attacker) {
            EnemyVehicleController.rememberTarget(this, attacker, 20 * 30);
        }
        OBBInfo.Part hitPart = this.estimateHitPart(source);
        ArmorHit armorHit = this.applyVehicleArmor(source, amount, hitPart);
        float finalDamage = this.vehicleData().defaults().damageModifier().apply(armorHit.finalDamage());
        float minimumDamage = source.is(ModDamageTypes.VEHICLE_STRIKE) ? 0.0F : 1.0F;
        if (finalDamage <= minimumDamage) {
            return false;
        }
        this.applyPartDamage(hitPart, finalDamage);
        this.applyPassengerLeakDamage(source, finalDamage, hitPart, armorHit.penetrated());
        this.repairCooldown = this.vehicleData().defaults().autoRepairCooldownTicks();
        float oldHealth = this.vehicleHealth();
        float newHealth = oldHealth - finalDamage;
        this.entityData.set(DATA_HEALTH, Math.max(0.0F, newHealth));
        if (newHealth < oldHealth) {
            if (source.is(ModDamageTypes.VEHICLE_STRIKE)) {
                this.playVehicleStrikeSound();
            } else {
                this.playVehicleDamageSound(armorHit.penetrated());
            }
        }
        this.hurtMarked = true;
        if (newHealth <= 0.0F) {
            this.destroyVehicle();
        }
        return true;
    }

    private boolean hurtVehicleIgnoringArmor(DamageSource source, float amount) {
        if (this.level().isClientSide() || this.isRemoved() || this.isInvulnerableToBase(source) || amount <= 0.0F) {
            return false;
        }
        this.repairCooldown = this.vehicleData().defaults().autoRepairCooldownTicks();
        float oldHealth = this.vehicleHealth();
        float newHealth = oldHealth - amount;
        this.entityData.set(DATA_HEALTH, Math.max(0.0F, newHealth));
        if (newHealth < oldHealth) {
            this.playVehicleStrikeSound();
        }
        this.hurtMarked = true;
        if (newHealth <= 0.0F) {
            this.destroyVehicle();
        }
        return true;
    }

    private boolean hurtVehicleStrikeWithArmor(DamageSource source, float amount) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        return this.hurtServer(serverLevel, source, amount);
    }

    private void destroyVehicle() {
        for (Entity passenger : java.util.List.copyOf(this.getPassengers())) {
            if (passenger.entityTags().contains(EnemyVehicleController.ENEMY_VEHICLE_CREW_TAG)) {
                passenger.discard();
            }
        }
        this.ejectPassengers();
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticleTypes.SMALL_EXPLOSION.get(), this.getX(), this.getY() + 0.6D, this.getZ(), 12, 0.8D, 0.5D, 0.8D, 0.08D);
            var destroy = this.vehicleData().defaults().destroy();
            if (destroy.explodes() && destroy.explosionPower() > 0.0F) {
                this.level().explode(this, this.getX(), this.getY() + 0.4D, this.getZ(), destroy.explosionPower(), ExplosionInteraction.MOB);
            }
        }
        this.discard();
    }

    public void destroyFromEnemyCrewLoss() {
        if (!this.level().isClientSide() && !this.isRemoved() && this.isEnemyAiVehicle()) {
            this.destroyVehicle();
        }
    }

    private ArmorHit applyVehicleArmor(DamageSource source, float amount, OBBInfo.Part hitPart) {
        if (!(source.getDirectEntity() instanceof BulletEntity bullet)) {
            if (source.is(ModDamageTypes.VEHICLE_STRIKE)) {
                VehiclePartArmorProfile armor = this.vehicleData().defaults().armor().forPart(hitPart);
                float armorMultiplier = Mth.clamp(1.05F - armor.rating() * 0.07F, 0.32F, 1.05F);
                return new ArmorHit(amount * armorMultiplier, true);
            }
            return new ArmorHit(amount, true);
        }
        VehiclePartArmorProfile armor = this.vehicleData().defaults().armor().forPart(hitPart);
        BallisticProtection.IntrinsicArmorProfile intrinsic = new BallisticProtection.IntrinsicArmorProfile(
                armor.rating(),
                armor.undermatchMultiplier(),
                armor.overmatchMultiplier()
        );
        BallisticProtection.BallisticResult result = BallisticProtection.applyToIntrinsicArmor(
                amount,
                bullet.getGunStats(),
                intrinsic,
                BallisticProtection.isRocketDirectHit(bullet.getGunStats())
        );
        return new ArmorHit(result.finalDamage(), !result.armorApplied() || result.overmatched());
    }

    private void applyPassengerLeakDamage(DamageSource source, float finalDamage, OBBInfo.Part hitPart, boolean penetrated) {
        VehiclePartArmorProfile armor = this.vehicleData().defaults().armor().forPart(hitPart);
        float leakMultiplier = armor.passengerLeakMultiplier();
        if (leakMultiplier <= 0.0F || this.getPassengers().isEmpty()) {
            return;
        }
        if (source.is(ModDamageTypes.VEHICLE_STRIKE) && source.getDirectEntity() == this
                && finalDamage < this.maxVehicleHealth() * SELF_STRIKE_PASSENGER_LEAK_MIN_RATIO) {
            return;
        }
        Entity sourceEntity = source.getEntity();
        Entity directEntity = source.getDirectEntity();
        for (int index = 0; index < this.getPassengers().size(); index++) {
            Entity passenger = this.getPassengers().get(index);
            if (!(passenger instanceof LivingEntity living) || passenger == sourceEntity || passenger == directEntity) {
                continue;
            }
            SeatInfo seat = this.seatForPassenger(passenger, index);
            if (seat.enclosed()) {
                continue;
            }
            living.hurt(source, finalDamage * leakMultiplier);
        }
    }

    private SeatInfo seatForPassenger(int index) {
        var seats = this.vehicleData().defaults().seats();
        if (index >= 0 && index < seats.size()) {
            return seats.get(index);
        }
        return SeatInfo.DRIVER;
    }

    private SeatInfo seatForPassenger(Entity passenger, int fallbackIndex) {
        return this.seatForPassenger(this.seatIndexForPassenger(passenger, fallbackIndex));
    }

    private int seatIndexForPassenger(Entity passenger, int fallbackIndex) {
        int seatCount = this.vehicleData().defaults().seats().size();
        int assignedSeat = this.seatAssignments.getOrDefault(passenger.getUUID(), fallbackIndex);
        if (assignedSeat >= 0 && assignedSeat < seatCount) {
            return assignedSeat;
        }
        return Mth.clamp(fallbackIndex, 0, Math.max(0, seatCount - 1));
    }

    public int getSeatIndex(Entity passenger) {
        return this.seatIndexForPassenger(passenger, this.getPassengers().indexOf(passenger));
    }

    @Nullable
    public Entity passengerForSeat(int seatIndex) {
        for (int index = 0; index < this.getPassengers().size(); index++) {
            Entity passenger = this.getPassengers().get(index);
            if (this.seatIndexForPassenger(passenger, index) == seatIndex) {
                return passenger;
            }
        }
        return null;
    }

    private boolean isSeatOccupied(int seatIndex, @Nullable Entity except) {
        for (int index = 0; index < this.getPassengers().size(); index++) {
            Entity passenger = this.getPassengers().get(index);
            if (passenger != except && this.seatIndexForPassenger(passenger, index) == seatIndex) {
                return true;
            }
        }
        return false;
    }

    private OBBInfo.Part estimateHitPart(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct == null) {
            return OBBInfo.Part.BODY;
        }
        Vec3 local = this.toLocalVehiclePosition(direct.position());
        for (OBBInfo.Box box : this.vehicleData().defaults().obb().boxes()) {
            if (Math.abs(local.x - box.x()) <= box.halfWidth()
                    && Math.abs(local.y - box.y()) <= box.halfHeight()
                    && Math.abs(local.z - box.z()) <= box.halfDepth()) {
                return box.part();
            }
        }
        if (local.y < 0.65D && Math.abs(local.x) > 0.35D) {
            return local.x < 0.0D ? OBBInfo.Part.WHEEL_LEFT : OBBInfo.Part.WHEEL_RIGHT;
        }
        if (local.y < 0.9D && local.z < -0.35D) {
            return OBBInfo.Part.MAIN_ENGINE;
        }
        return OBBInfo.Part.BODY;
    }

    private Vec3 toLocalVehiclePosition(Vec3 worldPosition) {
        double dx = worldPosition.x - this.getX();
        double dy = worldPosition.y - this.getY();
        double dz = worldPosition.z - this.getZ();
        double yaw = Math.toRadians(this.getYRot());
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        return new Vec3(dx * cos + dz * sin, dy, -dx * sin + dz * cos);
    }

    private void applyPartDamage(OBBInfo.Part hitPart, float finalDamage) {
        VehiclePartArmorProfile armor = this.vehicleData().defaults().armor().forPart(hitPart);
        float partDamage = finalDamage * armor.partDamageMultiplier();
        switch (hitPart) {
            case WHEEL_LEFT -> this.leftWheelHealth = Math.max(0.0F, this.leftWheelHealth - partDamage);
            case WHEEL_RIGHT -> this.rightWheelHealth = Math.max(0.0F, this.rightWheelHealth - partDamage);
            case MAIN_ENGINE -> this.engineHealth = Math.max(0.0F, this.engineHealth - partDamage);
            case SUB_ENGINE -> this.subEngineHealth = Math.max(0.0F, this.subEngineHealth - partDamage);
            case TURRET -> this.turretHealth = Math.max(0.0F, this.turretHealth - partDamage);
            default -> {
            }
        }
        this.syncPartDamageFlags();
    }

    private void syncPartDamageFlags() {
        this.entityData.set(DATA_LEFT_WHEEL_DAMAGED, this.leftWheelHealth <= 0.0F);
        this.entityData.set(DATA_RIGHT_WHEEL_DAMAGED, this.rightWheelHealth <= 0.0F);
        this.entityData.set(DATA_ENGINE_DAMAGED, this.engineHealth <= 0.0F);
        this.entityData.set(DATA_SUB_ENGINE_DAMAGED, this.subEngineHealth <= 0.0F);
        this.entityData.set(DATA_TURRET_DAMAGED, this.turretHealth <= 0.0F);
    }

    private boolean repairWithKit() {
        boolean repaired = false;
        if (this.vehicleHealth() < this.maxVehicleHealth()) {
            this.entityData.set(DATA_HEALTH, Math.min(this.maxVehicleHealth(), this.vehicleHealth() + REPAIR_KIT_HULL_REPAIR));
            repaired = true;
        }
        if (this.leftWheelHealth < PART_MAX_HEALTH) {
            this.leftWheelHealth = Math.min(PART_MAX_HEALTH, this.leftWheelHealth + REPAIR_KIT_PART_REPAIR);
            repaired = true;
        }
        if (this.rightWheelHealth < PART_MAX_HEALTH) {
            this.rightWheelHealth = Math.min(PART_MAX_HEALTH, this.rightWheelHealth + REPAIR_KIT_PART_REPAIR);
            repaired = true;
        }
        if (this.engineHealth < PART_MAX_HEALTH) {
            this.engineHealth = Math.min(PART_MAX_HEALTH, this.engineHealth + REPAIR_KIT_PART_REPAIR);
            repaired = true;
        }
        if (this.subEngineHealth < PART_MAX_HEALTH) {
            this.subEngineHealth = Math.min(PART_MAX_HEALTH, this.subEngineHealth + REPAIR_KIT_PART_REPAIR);
            repaired = true;
        }
        if (this.turretHealth < PART_MAX_HEALTH) {
            this.turretHealth = Math.min(PART_MAX_HEALTH, this.turretHealth + REPAIR_KIT_PART_REPAIR);
            repaired = true;
        }
        if (repaired) {
            this.repairCooldown = 0;
            this.syncPartDamageFlags();
            this.resetRecoveredLowHealthState();
            this.hurtMarked = true;
        }
        return repaired;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitLocation) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getItem() instanceof RepairToolItem repairTool) {
            if (this.level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (player instanceof ServerPlayer serverPlayer && this.level() instanceof ServerLevel serverLevel) {
                repairTool.useOnVehicle(serverLevel, serverPlayer, hand, stack, this);
                return InteractionResult.CONSUME;
            }
        }
        if (!this.level().isClientSide() && player.isShiftKeyDown() && stack.is(ModItems.CROWBAR.get()) && player instanceof ServerPlayer serverPlayer) {
            if (!this.getPassengers().isEmpty()) {
                serverPlayer.sendSystemMessage(Component.translatable("message.jeg.vehicle.occupied"));
                return InteractionResult.CONSUME;
            }
            ItemStack container = VehicleContainerBlockEntity.createItemFor(this);
            if (!serverPlayer.getInventory().add(container)) {
                serverPlayer.drop(container, false);
            }
            if (!serverPlayer.getAbilities().instabuild) {
                stack.hurtAndBreak(1, serverPlayer, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            }
            this.discard();
            return InteractionResult.CONSUME;
        }
        InteractionResult painted = this.tryPaint(player, hand, stack);
        if (painted != null) {
            return painted;
        }
        if (!this.level().isClientSide() && player.isShiftKeyDown() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(this);
            return InteractionResult.CONSUME;
        }
        if (!this.level().isClientSide() && !player.isPassenger()) {
            player.startRiding(this);
        }
        return this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Right-clicking with a dye paints the vehicle that colour, using up the dye; a water bucket washes the paint off.
     * Returns null when the held item does neither, so the click goes on to board or open the vehicle.
     */
    @Nullable
    private InteractionResult tryPaint(Player player, InteractionHand hand, ItemStack stack) {
        net.minecraft.world.item.DyeColor dye = stack.get(net.minecraft.core.component.DataComponents.DYE);
        if (dye != null && stack.getItem() instanceof net.minecraft.world.item.DyeItem) {
            if (dye == this.paint()) {
                // Already that colour: keep the dye, and do not climb in either.
                return this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
            }
            if (!this.level().isClientSide()) {
                this.setPaint(dye);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 0.6F, 0.8F);
                stack.consume(1, player);
            }
            return this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        if (this.paint() != null && stack.is(net.minecraft.world.item.Items.WATER_BUCKET)) {
            if (!this.level().isClientSide()) {
                this.setPaint(null);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8F, 1.0F);
            }
            return this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        return null;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.jeg.vehicle");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new VehicleMenu(containerId, playerInventory, this.inventory, this.vehicleContainerSlots(), this);
    }

    @Override
    public Integer getScreenOpeningData(ServerPlayer player) {
        return this.vehicleContainerSlots();
    }

    public int vehicleContainerSlots() {
        VehicleContainerType containerType = this.vehicleData().defaults().containerType();
        return Math.min(containerType.slots(), this.inventory.getContainerSize());
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        if (this.isEnemyAiVehicle() && !passenger.entityTags().contains(EnemyVehicleController.ENEMY_VEHICLE_CREW_TAG)) {
            return false;
        }
        return this.getPassengers().size() < this.vehicleData().defaults().seats().size();
    }

    @Override
    protected void addPassenger(@NotNull Entity passenger) {
        super.addPassenger(passenger);
        int seatCount = this.vehicleData().defaults().seats().size();
        int savedSeat = this.seatAssignments.getOrDefault(passenger.getUUID(), -1);
        if (savedSeat >= 0 && savedSeat < seatCount && !this.isSeatOccupied(savedSeat, passenger)) {
            this.alignPassengerViewToVehicle(passenger, savedSeat);
            this.syncSeatAssignments();
            return;
        }
        for (int seat = 0; seat < seatCount; seat++) {
            if (!this.isSeatOccupied(seat, passenger)) {
                this.seatAssignments.put(passenger.getUUID(), seat);
                this.alignPassengerViewToVehicle(passenger, seat);
                this.syncSeatAssignments();
                return;
            }
        }
        this.syncSeatAssignments();
    }

    @Override
    protected void removePassenger(@NotNull Entity passenger) {
        boolean controllingPassenger = passenger == this.getControllingPassenger();
        ServerPlayer syncPlayer = !this.level().isClientSide() && passenger instanceof ServerPlayer player ? player : null;
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        if (fallbackIndex >= 0 || this.seatAssignments.containsKey(passenger.getUUID())) {
            if (this.recentDismountSeatAssignments.size() > 16) {
                this.recentDismountSeatAssignments.clear();
            }
            this.recentDismountSeatAssignments.put(passenger.getUUID(), this.seatIndexForPassenger(passenger, fallbackIndex));
        }
        boolean preserveSeatAssignment = this.preservedSeatAssignments.remove(passenger.getUUID());
        if (controllingPassenger) {
            this.cancelWeaponReload();
            this.clearControlState(true);
        }
        if (passenger instanceof LivingEntity living) {
            living.fallDistance = 0.0F;
        }
        super.removePassenger(passenger);
        if (!preserveSeatAssignment) {
            this.seatAssignments.remove(passenger.getUUID());
        }
        this.syncSeatAssignments();
        if (controllingPassenger && syncPlayer != null) {
            this.recentDismountSyncPlayerId = syncPlayer.getUUID();
            this.recentDismountSyncTicks = DISMOUNT_FOLLOWUP_SYNC_TICKS;
            NetworkHandler.broadcastForcedVehicleState(this);
        }
        if (controllingPassenger && this.vehicleData().defaults().vehicleType() == VehicleType.LAND) {
            Vec3 passengerMotion = passenger.getDeltaMovement();
            passenger.setDeltaMovement(0.0D, passengerMotion.y, 0.0D);
        }
    }

    public double getPassengersRidingOffset() {
        return 0.45D;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        int fallbackIndex = this.getPassengers().indexOf(passenger);
        SeatInfo seat = this.dismountSeatForPassenger(passenger, fallbackIndex);
        boolean hasConfiguredDismount = seat != null && seat.dismount() != DismountInfo.DEFAULT;
        DismountInfo dismount = hasConfiguredDismount ? seat.dismount() : this.vehicleData().defaults().dismount();
        Vec3 offset = this.rotateLocalOffset(dismount.x(), dismount.y(), dismount.z());
        Vec3 candidate = this.position().add(offset);
        if (hasConfiguredDismount && this.usesDirectConfiguredDismount()) {
            return candidate;
        }
        Vec3 configured = this.findValidDismountPosition(passenger, candidate);
        if (configured != null) {
            return configured;
        }

        Vec3 side = this.findSideDismountPosition(passenger);
        if (side != null) {
            return side;
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Nullable
    private SeatInfo dismountSeatForPassenger(LivingEntity passenger, int fallbackIndex) {
        if (fallbackIndex >= 0 || this.seatAssignments.containsKey(passenger.getUUID())) {
            return this.seatForPassenger(passenger, fallbackIndex);
        }
        Integer recentSeat = this.recentDismountSeatAssignments.remove(passenger.getUUID());
        return recentSeat != null ? this.seatForPassenger(recentSeat) : null;
    }

    private boolean usesDirectConfiguredDismount() {
        return "mi28".equals(this.vehicleDataId().getPath());
    }

    @Nullable
    private Vec3 findSideDismountPosition(LivingEntity passenger) {
        double clearance = (this.getBbWidth() + passenger.getBbWidth()) * 0.5D + 0.2D;
        Vec3[] offsets = {
                this.rotateLocalOffset(clearance, 0.0D, 0.0D),
                this.rotateLocalOffset(-clearance, 0.0D, 0.0D)
        };
        for (Vec3 offset : offsets) {
            Vec3 base = this.position().add(offset);
            Vec3 candidate = this.findGroundedDismountPosition(passenger, base);
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    private Vec3 findGroundedDismountPosition(LivingEntity passenger, Vec3 base) {
        BlockPos exitPos = BlockPos.containing(base.x, this.getY(), base.z);
        BlockPos floorPos = exitPos.below();
        double exitHeight = this.level().getBlockFloorHeight(exitPos);
        if (DismountHelper.isBlockFloorValid(exitHeight)) {
            Vec3 candidate = this.findValidDismountPosition(passenger, new Vec3(base.x, exitPos.getY() + exitHeight, base.z));
            if (candidate != null) {
                return candidate;
            }
        }
        double floorHeight = this.level().getBlockFloorHeight(floorPos);
        if (DismountHelper.isBlockFloorValid(floorHeight)) {
            return this.findValidDismountPosition(passenger, new Vec3(base.x, floorPos.getY() + floorHeight, base.z));
        }
        return null;
    }

    @Nullable
    private Vec3 findValidDismountPosition(LivingEntity passenger, Vec3 candidate) {
        for (Pose pose : passenger.getDismountPoses()) {
            if (DismountHelper.canDismountTo(this.level(), candidate, passenger, pose)) {
                passenger.setPose(pose);
                return candidate;
            }
        }
        return null;
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        Entity passenger = null;
        boolean hasDriverSeat = false;
        for (int index = 0; index < this.getPassengers().size(); index++) {
            if (this.seatForPassenger(this.getPassengers().get(index), index).driver()) {
                passenger = this.getPassengers().get(index);
                break;
            }
        }
        for (SeatInfo seat : this.vehicleData().defaults().seats()) {
            hasDriverSeat |= seat.driver();
        }
        if (passenger == null && !hasDriverSeat) {
            passenger = this.getFirstPassenger();
        }
        return passenger instanceof LivingEntity living ? living : null;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // Helicopter hull boxes are smaller than the airframe, so size render distance from the full part bounds.
        double size = this.partBounds().getSize();
        if (Double.isNaN(size)) {
            size = 1.0D;
        }
        size *= 64.0D * getViewScale();
        return distance < size * size;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return !this.isRemoved()
                && this.vehicleData().defaults().collisionLevel() != CollisionLevel.NONE
                && !this.usesCustomObbEntityCollision();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void push(Vec3 movement) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    private record RotorContactInfo(double centerX, double centerY, double centerZ, double radius) {
    }

    private record ArmorHit(float finalDamage, boolean penetrated) {
    }

}
