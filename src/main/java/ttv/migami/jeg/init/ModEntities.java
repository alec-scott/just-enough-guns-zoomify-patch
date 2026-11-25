package ttv.migami.jeg.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.entity.BulletEntity;
import ttv.migami.jeg.entity.GrenadeEntity;
import ttv.migami.jeg.entity.MolotovCocktailEntity;
import ttv.migami.jeg.entity.SmokeGrenadeEntity;
import ttv.migami.jeg.entity.StunGrenadeEntity;
import ttv.migami.jeg.entity.WaterBombEntity;
import ttv.migami.jeg.entity.monster.Ghoul;
import ttv.migami.jeg.entity.monster.phantom.TerrorPhantom;
import ttv.migami.jeg.entity.monster.phantom.TerrorPhantomGuardian;
import ttv.migami.jeg.entity.monster.phantom.PhantomGunner;
import ttv.migami.jeg.entity.monster.phantom.PhantomGunnerMinion;
import ttv.migami.jeg.faction.raid.RaidEntity;
import ttv.migami.jeg.vehicle.entity.ConfiguredVehicleEntity;
import ttv.migami.jeg.vehicle.entity.TestAircraftEntity;
import ttv.migami.jeg.vehicle.entity.TestArtilleryEntity;
import ttv.migami.jeg.vehicle.entity.TestBoatEntity;
import ttv.migami.jeg.vehicle.entity.TestHelicopterEntity;
import ttv.migami.jeg.vehicle.entity.TestWheelVehicleEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleDecoyEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> REGISTER = DeferredRegister.create(Registries.ENTITY_TYPE, Reference.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<Ghoul>> GHOUL = REGISTER.register(
            "ghoul",
            () -> {
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Reference.id("ghoul"));
                return EntityType.Builder.of(Ghoul::new, MobCategory.MONSTER)
                        .sized(0.6F, 1.95F)
                        .build(key);
            }
    );

    public static final DeferredHolder<EntityType<?>, EntityType<BulletEntity>> BULLET = REGISTER.register(
            "bullet",
            () -> {
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Reference.id("bullet"));
                return EntityType.Builder.<BulletEntity>of(BulletEntity::new, MobCategory.MISC)
                        .sized(0.25F, 0.25F)
                        .clientTrackingRange(4)
                        .updateInterval(1)
                        .build(key);
            }
    );

    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeEntity>> GRENADE = REGISTER.register(
            "grenade",
            () -> {
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Reference.id("grenade"));
                return EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC)
                        .sized(0.25F, 0.25F)
                        .clientTrackingRange(6)
                        .updateInterval(2)
                        .build(key);
            }
    );

    public static final DeferredHolder<EntityType<?>, EntityType<StunGrenadeEntity>> STUN_GRENADE = REGISTER.register(
            "stun_grenade",
            () -> {
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Reference.id("stun_grenade"));
                return EntityType.Builder.<StunGrenadeEntity>of(StunGrenadeEntity::new, MobCategory.MISC)
                        .sized(0.25F, 0.25F)
                        .clientTrackingRange(6)
                        .updateInterval(2)
                        .build(key);
            }
    );

    public static final DeferredHolder<EntityType<?>, EntityType<SmokeGrenadeEntity>> SMOKE_GRENADE = REGISTER.register(
            "smoke_grenade",
            () -> {
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Reference.id("smoke_grenade"));
                return EntityType.Builder.<SmokeGrenadeEntity>of(SmokeGrenadeEntity::new, MobCategory.MISC)
                        .sized(0.25F, 0.25F)
                        .clientTrackingRange(6)
                        .updateInterval(2)
                        .build(key);
            }
    );

    public static final DeferredHolder<EntityType<?>, EntityType<MolotovCocktailEntity>> MOLOTOV_COCKTAIL = REGISTER.register(
            "molotov_cocktail",
            () -> {
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Reference.id("molotov_cocktail"));
                return EntityType.Builder.<MolotovCocktailEntity>of(MolotovCocktailEntity::new, MobCategory.MISC)
                        .sized(0.25F, 0.25F)
                        .clientTrackingRange(6)
                        .updateInterval(2)
                        .build(key);
            }
    );

    public static final DeferredHolder<EntityType<?>, EntityType<WaterBombEntity>> WATER_BOMB = REGISTER.register(
            "water_bomb",
            () -> {
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Reference.id("water_bomb"));
                return EntityType.Builder.<WaterBombEntity>of(WaterBombEntity::new, MobCategory.MISC)
                        .sized(0.25F, 0.25F)
                        .clientTrackingRange(6)
                        .updateInterval(2)
                        .build(key);
            }
    );

    public static final DeferredHolder<EntityType<?>, EntityType<PhantomGunner>> PHANTOM_GUNNER = REGISTER.register(
            "phantom_gunner",
            () -> {
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Reference.id("phantom_gunner"));
                return EntityType.Builder.of(PhantomGunner::new, MobCategory.MONSTER)
                        .sized(4.0F, 1.0F)
                        .clientTrackingRange(8)
                        .updateInterval(2)
                        .build(key);
            }
    );

    public static final DeferredHolder<EntityType<?>, EntityType<PhantomGunnerMinion>> PHANTOM_GUNNER_MINION = REGISTER.register(
            "phantom_gunner_minion",
            () -> {
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Reference.id("phantom_gunner_minion"));
                return EntityType.Builder.of(PhantomGunnerMinion::new, MobCategory.MONSTER)
                        .sized(4.0F, 1.0F)
                        .clientTrackingRange(8)
                        .updateInterval(2)
                        .build(key);
            }
    );

    public static final DeferredHolder<EntityType<?>, EntityType<TerrorPhantom>> TERROR_PHANTOM = REGISTER.register(
            "terror_phantom",
            () -> {
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Reference.id("terror_phantom"));
                return EntityType.Builder.of(TerrorPhantom::new, MobCategory.MONSTER)
                        .sized(40.19531F, 1.5F)
                        .clientTrackingRange(14)
                        .updateInterval(2)
                        .fireImmune()
                        .build(key);
            }
    );

    public static final DeferredHolder<EntityType<?>, EntityType<TerrorPhantomGuardian>> TERROR_PHANTOM_GUARDIAN = REGISTER.register(
            "terror_phantom_guardian",
            () -> {
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Reference.id("terror_phantom_guardian"));
                return EntityType.Builder.of(TerrorPhantomGuardian::new, MobCategory.MONSTER)
                        .sized(45.21973F, 1.8F)
                        .clientTrackingRange(14)
                        .updateInterval(2)
                        .fireImmune()
                        .build(key);
            }
    );

    public static final DeferredHolder<EntityType<?>, EntityType<RaidEntity>> RAID_ENTITY = REGISTER.register(
            "raid_entity",
            () -> {
                ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, Reference.id("raid_entity"));
                return EntityType.Builder.<RaidEntity>of(RaidEntity::new, MobCategory.MISC)
                        .sized(3.0F, 3.0F)
                        .clientTrackingRange(8)
                        .updateInterval(3)
                        .fireImmune()
                        .build(key);
            }
    );

    public static final DeferredHolder<EntityType<?>, EntityType<TestWheelVehicleEntity>> TEST_WHEEL_VEHICLE = REGISTER.register(
            "test_wheel_vehicle",
            () -> EntityType.Builder.<TestWheelVehicleEntity>of(TestWheelVehicleEntity::new, MobCategory.MISC)
                    .sized(1.4F, 1.1F)
                    .clientTrackingRange(14)
                    .updateInterval(1)
                    .build(entityKey("test_wheel_vehicle"))
    );

    /**
     * Retired 2026-11-17 (fleet plan section 9: a placeholder from before the port, its job done by the LAV-150): kept
     * registered for one release so a saved one turns into a chest of its hold (RetiredVehicleEntity). Remove the type
     * in the release after.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.RetiredVehicleEntity>> LIGHT_COMBAT_VEHICLE = REGISTER.register(
            "light_combat_vehicle",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.RetiredVehicleEntity>of(
                            (type, level) -> new ttv.migami.jeg.vehicle.entity.RetiredVehicleEntity(type, level, Reference.id("light_combat_vehicle")), MobCategory.MISC)
                    .sized(1.8F, 1.35F)
                    .clientTrackingRange(14)
                    .updateInterval(1)
                    .build(entityKey("light_combat_vehicle"))
    );

    public static final DeferredHolder<EntityType<?>, EntityType<TestHelicopterEntity>> TEST_HELICOPTER = REGISTER.register(
            "test_helicopter",
            () -> EntityType.Builder.<TestHelicopterEntity>of(TestHelicopterEntity::new, MobCategory.MISC)
                    .sized(2.0F, 1.25F)
                    .clientTrackingRange(14)
                    .updateInterval(1)
                    .build(entityKey("test_helicopter"))
    );

    public static final DeferredHolder<EntityType<?>, EntityType<TestBoatEntity>> TEST_BOAT = REGISTER.register(
            "test_boat",
            () -> EntityType.Builder.<TestBoatEntity>of(TestBoatEntity::new, MobCategory.MISC)
                    .sized(1.7F, 0.75F)
                    .clientTrackingRange(14)
                    .updateInterval(1)
                    .build(entityKey("test_boat"))
    );

    public static final DeferredHolder<EntityType<?>, EntityType<TestArtilleryEntity>> TEST_ARTILLERY = REGISTER.register(
            "test_artillery",
            () -> EntityType.Builder.<TestArtilleryEntity>of(TestArtilleryEntity::new, MobCategory.MISC)
                    .sized(1.3F, 1.45F)
                    .clientTrackingRange(14)
                    .updateInterval(1)
                    .build(entityKey("test_artillery"))
    );

    public static final DeferredHolder<EntityType<?>, EntityType<TestAircraftEntity>> TEST_AIRCRAFT = REGISTER.register(
            "test_aircraft",
            () -> EntityType.Builder.<TestAircraftEntity>of(TestAircraftEntity::new, MobCategory.MISC)
                    .sized(2.4F, 0.9F)
                    .clientTrackingRange(14)
                    .updateInterval(1)
                    .build(entityKey("test_aircraft"))
    );

    /** The FSV-6 field support vehicle. It keeps the old truck's id so trucks already in worlds load as FSV-6s. */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.SupportVehicleEntity>> TRUCK = REGISTER.register("truck",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.SupportVehicleEntity>of(ttv.migami.jeg.vehicle.entity.SupportVehicleEntity::new, MobCategory.MISC)
                    .sized(2.6F, 3.2F).clientTrackingRange(14).updateInterval(1).build(entityKey("truck")));
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.BulldozerEntity>> BULLDOZER = REGISTER.register("bulldozer",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.BulldozerEntity>of(ttv.migami.jeg.vehicle.entity.BulldozerEntity::new, MobCategory.MISC)
                    .sized(2.9F, 3.2F).clientTrackingRange(14).updateInterval(1).build(entityKey("bulldozer")));
    public static final DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> LAV150 = configuredVehicle("lav150", 2.6F, 2.6F);
    /** The speedboat (design/speedboat/rig.json): planing hull, bow gun with its gunner. */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.SpeedboatEntity>> SPEEDBOAT = REGISTER.register("speedboat",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.SpeedboatEntity>of(ttv.migami.jeg.vehicle.entity.SpeedboatEntity::new, MobCategory.MISC)
                    .sized(3.0F, 2.0F).clientTrackingRange(16).updateInterval(1).build(entityKey("speedboat")));
    public static final DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> AH6 = configuredVehicle("ah6", 2.4F, 2.9F);
    public static final DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> A10 = configuredVehicle("a10", 3.2F, 3.4F);
    public static final DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> BMP2 = configuredVehicle("bmp2", 3.4F, 2.4F);
    public static final DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> MI28 = configuredVehicle("mi28", 3.0F, 4.5F);
    /** The CH-47F Chinook (design/chinook/): its block box is its fitted parts' envelope, its cabin walk-in. */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.ChinookEntity>> CH47 = REGISTER.register("ch47",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.ChinookEntity>of(ttv.migami.jeg.vehicle.entity.ChinookEntity::new, MobCategory.MISC)
                    .sized(4.5F, 4.5F).clientTrackingRange(16).updateInterval(1).build(entityKey("ch47")));
    public static final DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> TOM6 = configuredVehicle("tom6", 3.0F, 2.4F);
    /**
     * Retired 2026-11-17 (replaced by the sentry, fleet plan section 6): kept registered for one release so a saved tower
     * turns into a chest of its hold (RetiredVehicleEntity). Remove the type in the release after.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.RetiredVehicleEntity>> LASER_TOWER = REGISTER.register(
            "laser_tower",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.RetiredVehicleEntity>of(
                            (type, level) -> new ttv.migami.jeg.vehicle.entity.RetiredVehicleEntity(type, level, Reference.id("laser_tower")), MobCategory.MISC)
                    .sized(1.0F, 1.9F).clientTrackingRange(14).updateInterval(1).build(entityKey("laser_tower")));
    /** The perimeter sentry (design/sentry/rig.json): a remote weapon station on a tripod, manual or automatic. */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.SentryEntity>> SENTRY = REGISTER.register("sentry",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.SentryEntity>of(ttv.migami.jeg.vehicle.entity.SentryEntity::new, MobCategory.MISC)
                    .sized(1.4F, 2.1F).clientTrackingRange(14).updateInterval(1).build(entityKey("sentry")));
    /** The HPJ-11 close-in weapon system (design/hpj11/rig.json): base air and missile defence, manual or automatic. */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.Hpj11Entity>> HPJ11 = REGISTER.register("hpj11",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.Hpj11Entity>of(ttv.migami.jeg.vehicle.entity.Hpj11Entity::new, MobCategory.MISC)
                    .sized(3.2F, 2.6F).clientTrackingRange(16).updateInterval(1).build(entityKey("hpj11")));
    /** The 81 mm mortar: the baseplate plus the tube at 45 degrees (design/mortar/rig.json). */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.MortarEntity>> MORTAR = REGISTER.register("mortar",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.MortarEntity>of(ttv.migami.jeg.vehicle.entity.MortarEntity::new, MobCategory.MISC)
                    .sized(0.8F, 1.4F).clientTrackingRange(14).updateInterval(1).build(entityKey("mortar")));
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.projectile.MortarRoundEntity>> MORTAR_ROUND = REGISTER.register("mortar_round",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.projectile.MortarRoundEntity>of(ttv.migami.jeg.vehicle.projectile.MortarRoundEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(16).updateInterval(1).build(entityKey("mortar_round")));
    /** The Mk42 5"/54 gun mount: the gunhouse (design/mk42/rig.json); its entity box follows the turret. */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.Mk42Entity>> MK42 = REGISTER.register("mk42",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.Mk42Entity>of(ttv.migami.jeg.vehicle.entity.Mk42Entity::new, MobCategory.MISC)
                    .sized(4.2F, 3.6F).clientTrackingRange(14).updateInterval(1).build(entityKey("mk42")));
    /** The BL-132 130 mm/58 four-gun mount: the gunhouse (design/bl132/rig.json); its entity box follows the turret. */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.Bl132Entity>> BL132 = REGISTER.register("bl132",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.Bl132Entity>of(ttv.migami.jeg.vehicle.entity.Bl132Entity::new, MobCategory.MISC)
                    .sized(7.2F, 4.5F).clientTrackingRange(14).updateInterval(1).build(entityKey("bl132")));
    /** The Type-63 107 mm towed rocket launcher (design/type63/rig.json). */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.Type63Entity>> TYPE63 = REGISTER.register("type63",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.Type63Entity>of(ttv.migami.jeg.vehicle.entity.Type63Entity::new, MobCategory.MISC)
                    .sized(1.6F, 1.2F).clientTrackingRange(14).updateInterval(1).build(entityKey("type63")));
    /** Superb Warfare's YX-100 main battle tank (design/yx100/rig.json). */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.Yx100Entity>> YX100 = REGISTER.register("yx100",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.Yx100Entity>of(ttv.migami.jeg.vehicle.entity.Yx100Entity::new, MobCategory.MISC)
                    .sized(3.7F, 2.5F).clientTrackingRange(16).updateInterval(1).build(entityKey("yx100")));
    /** The PLZ-05 155 mm self-propelled howitzer (design/plz05/rig.json). */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.Plz05Entity>> PLZ05 = REGISTER.register("plz05",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.Plz05Entity>of(ttv.migami.jeg.vehicle.entity.Plz05Entity::new, MobCategory.MISC)
                    .sized(3.4F, 3.0F).clientTrackingRange(16).updateInterval(1).build(entityKey("plz05")));
    /** The 105 mm towed howitzer (design/howitzer/rig.json): its entity box is refitted round the trails as they open. */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.HowitzerEntity>> HOWITZER = REGISTER.register("howitzer",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.HowitzerEntity>of(ttv.migami.jeg.vehicle.entity.HowitzerEntity::new, MobCategory.MISC)
                    .sized(2.2F, 1.7F).clientTrackingRange(14).updateInterval(1).build(entityKey("howitzer")));
    /** The TOW launcher on its tripod (design/tow/rig.json). */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.TowEntity>> TOW = REGISTER.register("tow",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.TowEntity>of(ttv.migami.jeg.vehicle.entity.TowEntity::new, MobCategory.MISC)
                    .sized(1.1F, 1.4F).clientTrackingRange(14).updateInterval(1).build(entityKey("tow")));
    /** A gun's shell in flight (the Mk42's); its ballistics and warhead come from the gun that fired it. */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.projectile.ShellEntity>> CANNON_SHELL = REGISTER.register("cannon_shell",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.projectile.ShellEntity>of(ttv.migami.jeg.vehicle.projectile.ShellEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(16).updateInterval(1).build(entityKey("cannon_shell")));
    /**
     * Retired 2026-11-16 (replaced by the 105 mm howitzer): kept registered for one release so a saved tower turns into a
     * chest of its hold (RetiredVehicleEntity). Remove the type in the release after.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ttv.migami.jeg.vehicle.entity.RetiredVehicleEntity>> WAVEFORCE_TOWER = REGISTER.register(
            "waveforce_tower",
            () -> EntityType.Builder.<ttv.migami.jeg.vehicle.entity.RetiredVehicleEntity>of(
                            (type, level) -> new ttv.migami.jeg.vehicle.entity.RetiredVehicleEntity(type, level, Reference.id("waveforce_tower")), MobCategory.MISC)
                    .sized(1.5F, 2.1F).clientTrackingRange(14).updateInterval(1).build(entityKey("waveforce_tower")));

    public static final DeferredHolder<EntityType<?>, EntityType<VehicleDecoyEntity>> VEHICLE_DECOY = REGISTER.register(
            "vehicle_decoy",
            () -> EntityType.Builder.<VehicleDecoyEntity>of(VehicleDecoyEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F)
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build(entityKey("vehicle_decoy"))
    );

    public static final DeferredHolder<EntityType<?>, EntityType<VehicleMissileEntity>> VEHICLE_MISSILE = REGISTER.register(
            "vehicle_missile",
            () -> EntityType.Builder.<VehicleMissileEntity>of(VehicleMissileEntity::new, MobCategory.MISC)
                    .sized(0.45F, 0.45F)
                    .clientTrackingRange(32)
                    .updateInterval(1)
                    .build(entityKey("vehicle_missile"))
    );

    private static DeferredHolder<EntityType<?>, EntityType<ConfiguredVehicleEntity>> configuredVehicle(String path, float width, float height) {
        return REGISTER.register(
                path,
                () -> EntityType.Builder.<ConfiguredVehicleEntity>of((type, level) -> new ConfiguredVehicleEntity(type, level, Reference.id(path)), MobCategory.MISC)
                        .sized(width, height)
                        .clientTrackingRange(14)
                        .updateInterval(1)
                        .build(entityKey(path))
        );
    }

    private static ResourceKey<EntityType<?>> entityKey(String path) {
        return ResourceKey.create(Registries.ENTITY_TYPE, Reference.id(path));
    }
}
