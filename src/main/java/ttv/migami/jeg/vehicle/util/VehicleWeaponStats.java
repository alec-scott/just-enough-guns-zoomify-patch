package ttv.migami.jeg.vehicle.util;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.gun.GunDefinitions;
import ttv.migami.jeg.gun.GunStats;

public final class VehicleWeaponStats {
    private static final Identifier SMALL_SHELL = Reference.id("small_shell");
    private static final Identifier AUTOCANNON_SHELL = Reference.id("autocannon_shell");
    private static final Identifier RIFLE_AMMO = Reference.id("rifle_ammo");
    private static final Identifier ROCKET = Reference.id("rocket");
    private static final Identifier SMALL_ROCKET = Reference.id("small_rocket");
    private static final Identifier MEDIUM_ANTI_AIR_MISSILE = Reference.id("medium_anti_air_missile");
    private static final Identifier MEDIUM_ANTI_GROUND_MISSILE = Reference.id("medium_anti_ground_missile");
    private static final Identifier LARGE_ANTI_GROUND_MISSILE = Reference.id("large_anti_ground_missile");
    private static final Identifier ROCKET_FIRE = Reference.id("item.rocket_launcher.fire");
    /** A real BGM-71 launch (design/tow/prepare_audio.py). */
    private static final Identifier TOW_LAUNCH = Reference.id("vehicle.tow.launch");
    private static final Identifier ROCKET_RELOAD_START = Reference.id("item.rocket_launcher.lid_open");
    /** The GAU-8's reload: the ammunition drum opened, then refilled, the feed seated and the bolt home. */
    private static final Identifier A10_GUN_RELOAD_START = Reference.id("vehicle.a10.gun_reload_start");
    private static final Identifier A10_GUN_RELOAD_END = Reference.id("vehicle.a10.gun_reload_end");
    private static final Identifier ROCKET_RELOAD_LOAD = Reference.id("item.rocket_launcher.rocket_in");
    private static final Identifier ROCKET_RELOAD_END = Reference.id("item.rocket_launcher.lid_close");
    private static final Identifier LAV150_CANNON_FIRE = Reference.id("vehicle.lav150.cannon_fire");
    private static final Identifier BMP2_CANNON_FIRE = Reference.id("vehicle.bmp2.cannon_fire");
    private static final Identifier BMP2_MISSILE_FIRE = Reference.id("vehicle.bmp2.missile_fire");
    private static final Identifier MACHINE_GUN_FIRE = Reference.id("item.light_machine_gun.fire");
    private static final Identifier A10_GUN_FIRE = Reference.id("vehicle.a10.fire");
    private static final Identifier MK42_LOAD = Reference.id("vehicle.mk42.load");
    private static final Identifier HOWITZER_LOAD = Reference.id("vehicle.howitzer.load");
    private static final Identifier BL132_LOAD = Reference.id("vehicle.bl132.load");
    private static final Identifier TYPE63_LOAD = Reference.id("vehicle.type63.load");
    private static final Identifier PLZ05_LOAD = Reference.id("vehicle.plz05.load");
    private static final Identifier YX100_LOAD = Reference.id("vehicle.yx100.load");

    private static final Map<Identifier, GunStats> STATS = createStats();

    /** A copy of a gun's stats under another id, with its own direct damage and tracer colour (a second belt). */
    private static GunStats withId(GunStats s, Identifier id, float damage, int trailColor) {
        return new GunStats(id, s.ammoItem(), s.reloadType(), s.magazineSize(), s.reloadTime(), s.additionalReloadTime(),
                s.fireDelay(), damage, s.projectileSpeed(), s.projectileLife(), s.gravity(), s.flameTrail(), s.spread(),
                s.projectileAmount(), s.fireSound(), s.silencedFireSound(), s.enchantedFireSound(), s.reloadStartSound(),
                s.reloadLoadSound(), s.reloadEndSound(), s.ejectorPullSound(), s.ejectorReleaseSound(), s.projectileSize(),
                trailColor, s.trailLengthMultiplier());
    }

    private static Map<Identifier, GunStats> createStats() {
        Map<Identifier, GunStats> stats = new LinkedHashMap<>();
        stats.put(
                Reference.id("vehicle_20mm_cannon"),
                new GunStats(
                        Reference.id("vehicle_20mm_cannon"),
                        SMALL_SHELL,
                        "jeg:magazine",
                        12,
                        50,
                        10,
                        4,
                        45.0F,
                        20.0F,
                        60,
                        true,
                        false,
                        0.5F,
                        1,
                        LAV150_CANNON_FIRE,
                        null,
                        LAV150_CANNON_FIRE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0.12F,
                        0x66FF00,
                        2.0F
                )
        );
        stats.put(
                Reference.id("vehicle_30mm_cannon"),
                new GunStats(
                        Reference.id("vehicle_30mm_cannon"),
                        AUTOCANNON_SHELL,
                        "jeg:magazine",
                        8,
                        60,
                        20,
                        15,
                        60.0F,
                        24.0F,
                        90,
                        true,
                        false,
                        0.35F,
                        1,
                        BMP2_CANNON_FIRE,
                        null,
                        BMP2_CANNON_FIRE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0.16F,
                        0xFFC700,
                        2.8F
                )
        );
        // The 2A42's second feed (dual feed, fleet plan section 2): HE, bursting among infantry; the first is AP.
        stats.put(Reference.id("vehicle_30mm_cannon_he"), withId(stats.get(Reference.id("vehicle_30mm_cannon")),
                Reference.id("vehicle_30mm_cannon_he"), 40.0F, 0xFF7A1A));
        stats.put(
                Reference.id("vehicle_coax_machine_gun"),
                new GunStats(
                        Reference.id("vehicle_coax_machine_gun"),
                        RIFLE_AMMO,
                        "jeg:magazine",
                        40,
                        30,
                        10,
                        2,
                        9.5F,
                        30.0F,
                        50,
                        false,
                        false,
                        0.5F,
                        1,
                        MACHINE_GUN_FIRE,
                        null,
                        MACHINE_GUN_FIRE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        GunStats.STANDARD_BULLET_PROJECTILE_SIZE,
                        0x66FF00,
                        1.5F
                )
        );
        // The A-10's GAU-8: fed from rifle ammo like the helicopter guns, but every 30 mm round bursts on impact. Spread is
        // the cone (degrees) the rounds scatter over, so a burst covers an area rather than one point.
        stats.put(
                Reference.id("vehicle_gau8_cannon"),
                new GunStats(
                        Reference.id("vehicle_gau8_cannon"),
                        RIFLE_AMMO,
                        "jeg:magazine",
                        300,
                        120,
                        0,
                        1,
                        16.0F,
                        24.0F,
                        60,
                        false,
                        false,
                        1.4F,
                        1,
                        A10_GUN_FIRE,
                        null,
                        A10_GUN_FIRE,
                        A10_GUN_RELOAD_START,
                        null,
                        A10_GUN_RELOAD_END,
                        null,
                        null,
                        0.16F,
                        0xFFC040,
                        2.8F
                )
        );
        stats.put(
                Reference.id("vehicle_bmp2_missile"),
                new GunStats(
                        Reference.id("vehicle_bmp2_missile"),
                        MEDIUM_ANTI_GROUND_MISSILE,
                        "jeg:magazine",
                        1,
                        120,
                        0,
                        50,
                        600.0F,
                        0.95F,
                        120,
                        false,
                        true,
                        0.0F,
                        1,
                        BMP2_MISSILE_FIRE,
                        null,
                        BMP2_MISSILE_FIRE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0.22F,
                        0xFF7744,
                        3.0F
                )
        );
        stats.put(
                Reference.id("rocket_launcher"),
                new GunStats(
                        Reference.id("rocket_launcher"),
                        ROCKET,
                        "jeg:magazine",
                        1,
                        80,
                        20,
                        18,
                        140.0F,
                        0.9F,
                        120,
                        false,
                        true,
                        0.0F,
                        1,
                        ROCKET_FIRE,
                        null,
                        ROCKET_FIRE,
                        ROCKET_RELOAD_START,
                        ROCKET_RELOAD_LOAD,
                        ROCKET_RELOAD_END,
                        null,
                        null,
                        0.22F,
                        0xFF7744,
                        3.0F
                )
        );
        stats.put(
                Reference.id("vehicle_70mm_rocket"),
                new GunStats(
                        Reference.id("vehicle_70mm_rocket"),
                        SMALL_ROCKET,
                        "jeg:magazine",
                        14,
                        100,
                        0,
                        3,
                        110.0F,
                        9.0F,
                        80,
                        true,
                        true,
                        0.25F,
                        1,
                        ROCKET_FIRE,
                        null,
                        ROCKET_FIRE,
                        ROCKET_RELOAD_START,
                        ROCKET_RELOAD_LOAD,
                        ROCKET_RELOAD_END,
                        null,
                        null,
                        0.16F,
                        0xFF7744,
                        2.0F
                )
        );
        stats.put(
                Reference.id("vehicle_80mm_rocket"),
                new GunStats(
                        Reference.id("vehicle_80mm_rocket"),
                        SMALL_ROCKET,
                        "jeg:magazine",
                        42,
                        200,
                        0,
                        3,
                        125.0F,
                        9.0F,
                        80,
                        true,
                        true,
                        0.25F,
                        1,
                        ROCKET_FIRE,
                        null,
                        ROCKET_FIRE,
                        ROCKET_RELOAD_START,
                        ROCKET_RELOAD_LOAD,
                        ROCKET_RELOAD_END,
                        null,
                        null,
                        0.16F,
                        0xFF7744,
                        2.0F
                )
        );
        stats.put(
                Reference.id("vehicle_9m120_driver_missile"),
                vehicleMissileStats(Reference.id("vehicle_9m120_driver_missile"), MEDIUM_ANTI_GROUND_MISSILE, 4, 240, 40, 650.0F)
        );
        stats.put(
                Reference.id("vehicle_9m120_passenger_missile"),
                vehicleMissileStats(Reference.id("vehicle_9m120_passenger_missile"), MEDIUM_ANTI_GROUND_MISSILE, 8, 240, 40, 650.0F)
        );
        stats.put(
                Reference.id("vehicle_kh39_missile"),
                vehicleMissileStats(Reference.id("vehicle_kh39_missile"), LARGE_ANTI_GROUND_MISSILE, 2, 260, 60, 1100.0F)
        );
        stats.put(
                Reference.id("vehicle_agm65_missile"),
                vehicleMissileStats(Reference.id("vehicle_agm65_missile"), MEDIUM_ANTI_GROUND_MISSILE, 2, 200, 16, 650.0F)
        );
        stats.put(
                Reference.id("vehicle_agm65g_missile"),
                vehicleMissileStats(Reference.id("vehicle_agm65g_missile"), LARGE_ANTI_GROUND_MISSILE, 2, 280, 30, 1100.0F)
        );
        stats.put(
                Reference.id("vehicle_9m336_missile"),
                vehicleMissileStats(Reference.id("vehicle_9m336_missile"), MEDIUM_ANTI_AIR_MISSILE, 4, 260, 50, 260.0F)
        );
        // The Mk42's 5"/54 (design/mk42/BRIEF.md): fires ShellEntity shells (Mk42Entity#fireShell), not bullets, so
        // damage, speed and life here only describe it; the warheads and ballistics are in Mk42Entity. A 20-round
        // loader drum, 30 ticks a shot (40 a minute), 6 s to refill the drum from the hold. Vehicle reloads run at 0.64 of
        // the stat (VehicleEntity.vehicleReloadDuration), so the stat is 120 / 0.64.
        for (String kind : new String[]{"he", "ap", "cm", "gs"}) {
            Identifier id = Reference.id("vehicle_mk42_" + kind);
            stats.put(id, new GunStats(
                    id,
                    Reference.id("shell_5in_" + kind),
                    "jeg:magazine",
                    20,
                    188,
                    0,
                    30,
                    switch (kind) {
                        case "ap" -> 450.0F;
                        case "cm" -> 300.0F;
                        case "gs" -> 376.0F; // incendiary: 28 fragments of 12 and the burst's 40
                        default -> 250.0F;
                    },
                    15.0F,
                    400,
                    true,
                    false,
                    0.05F,
                    1,
                    null,
                    null,
                    null,
                    MK42_LOAD,
                    null,
                    MK42_LOAD,
                    null,
                    null,
                    0.2F,
                    0xFFC040,
                    1.0F
            ));
        }
        // The BL-132's 130 mm/58 (design/bl132/BRIEF.md): four guns fired in turn, SBW's 300 rounds a minute (4 ticks),
        // then SBW's 90 ticks to reload all four from the hold (stat 90 / 0.64, see the Mk42's). It loads the Mk42's
        // 5-inch shells.
        for (String kind : new String[]{"he", "ap", "cm", "gs"}) {
            Identifier id = Reference.id("vehicle_bl132_" + kind);
            stats.put(id, new GunStats(
                    id,
                    Reference.id("shell_5in_" + kind),
                    "jeg:magazine",
                    4,
                    141,
                    0,
                    4,
                    switch (kind) {
                        case "ap" -> 470.0F;
                        case "cm" -> 360.0F;
                        case "gs" -> 554.0F; // incendiary: 36 fragments of 14 and the burst's 50
                        default -> 250.0F;
                    },
                    15.0F,
                    400,
                    true,
                    false,
                    0.05F,
                    1,
                    null,
                    null,
                    null,
                    BL132_LOAD,
                    null,
                    BL132_LOAD,
                    null,
                    null,
                    0.2F,
                    0xFFC040,
                    1.0F
            ));
        }
        // The 105 mm howitzer (design/howitzer/BRIEF.md): one round in the breech, fired as a ShellEntity (HowitzerEntity,
        // whose charge sets the muzzle velocity); the crew loads the next in 4 s (stat 125 / 0.64 = 80 ticks).
        for (String kind : new String[]{"he", "smoke"}) {
            Identifier id = Reference.id("vehicle_howitzer_" + kind);
            stats.put(id, new GunStats(
                    id,
                    Reference.id("shell_105mm_" + kind),
                    "jeg:magazine",
                    1,
                    125,
                    0,
                    20,
                    kind.equals("he") ? 300.0F : 60.0F,
                    10.0F,
                    600,
                    true,
                    false,
                    0.15F,
                    1,
                    null,
                    null,
                    null,
                    HOWITZER_LOAD,
                    null,
                    HOWITZER_LOAD,
                    null,
                    null,
                    0.2F,
                    0xFFC040,
                    1.0F
            ));
        }
        // The PLZ-05 (design/plz05/BRIEF.md): the Mk42's 5-inch shells, fired as ShellEntity shells (Plz05Entity, whose
        // charge sets the muzzle velocity). One in the breech; the autoloader loads the next in 3 s (stat 94 * 0.64; 6 s
        // until the user's review, 2026-11-18, found it slow).
        for (String kind : new String[]{"he", "ap", "cm", "gs"}) {
            Identifier id = Reference.id("vehicle_plz05_" + kind);
            stats.put(id, new GunStats(
                    id,
                    Reference.id("shell_5in_" + kind),
                    "jeg:magazine",
                    1,
                    94,
                    0,
                    20,
                    switch (kind) {
                        case "ap" -> 450.0F;
                        case "cm" -> 300.0F;
                        case "gs" -> 376.0F; // incendiary: 28 fragments of 12 and the burst's 40
                        default -> 250.0F;
                    },
                    19.0F,
                    700,
                    true,
                    false,
                    0.06F,
                    1,
                    null,
                    null,
                    null,
                    PLZ05_LOAD,
                    null,
                    PLZ05_LOAD,
                    null,
                    null,
                    0.2F,
                    0xFFC040,
                    1.0F
            ));
        }
        // The YX-100 (design/yx100/BRIEF.md): the Mk42's 5-inch shells fired flat and fast (Yx100Entity's
        // ballistics), one in the breech, loaded in 3.5 s (SBW's: 70 ticks, stat 109 * 0.64).
        for (String kind : new String[]{"he", "ap", "cm", "gs"}) {
            Identifier id = Reference.id("vehicle_yx100_" + kind);
            stats.put(id, new GunStats(id, Reference.id("shell_5in_" + kind), "jeg:magazine", 1, 109, 0, 20,
                    switch (kind) {
                        case "ap" -> 450.0F;
                        case "cm" -> 300.0F;
                        case "gs" -> 376.0F; // incendiary: 28 fragments of 12 and the burst's 40
                        default -> 250.0F;
                    },
                    40.0F, 150, true, false, 0.02F, 1, null, null, null, YX100_LOAD, null, YX100_LOAD, null, null,
                    0.2F, 0xFFC040, 1.0F));
        }
        // The YX-100's remote weapon station: a 12.7 mm, as the PLZ-05's roof gun.
        stats.put(Reference.id("vehicle_yx100_rws"), new GunStats(Reference.id("vehicle_yx100_rws"), RIFLE_AMMO, "jeg:magazine",
                100, 125, 0, 2, 14.0F, 30.0F, 60, false, false, 0.4F, 1, MACHINE_GUN_FIRE, null, MACHINE_GUN_FIRE,
                null, null, null, null, null, GunStats.STANDARD_BULLET_PROJECTILE_SIZE, 0xFFAA33, 1.5F));
        // The PLZ-05's roof 12.7 mm: about 600 rounds a minute from a 100-round box (8 s to change: stat 125 / 0.64), on
        // the long-range path (VehicleGunReports).
        stats.put(Reference.id("vehicle_plz05_hmg"), new GunStats(
                Reference.id("vehicle_plz05_hmg"),
                RIFLE_AMMO,
                "jeg:magazine",
                100,
                125,
                0,
                2,
                14.0F,
                30.0F,
                60,
                false,
                false,
                0.6F,
                1,
                MACHINE_GUN_FIRE,
                null,
                MACHINE_GUN_FIRE,
                null,
                null,
                null,
                null,
                null,
                GunStats.STANDARD_BULLET_PROJECTILE_SIZE,
                0xFFAA33,
                1.5F
        ));
        // The sentry's 7.62 mm (design/sentry/BRIEF.md): fed from the hold, about 600 rounds a minute, as the coax.
        stats.put(Reference.id("vehicle_sentry_machine_gun"), new GunStats(Reference.id("vehicle_sentry_machine_gun"), RIFLE_AMMO,
                "jeg:inventory_fed", 0, 0, 0, 2, 8.0F, 24.0F, 60, false, false, 0.5F, 1, MACHINE_GUN_FIRE, null, MACHINE_GUN_FIRE,
                null, null, null, null, null, GunStats.STANDARD_BULLET_PROJECTILE_SIZE, 0xFFAA33, 1.2F));
        // The HPJ-11's eleven-barrel 30 mm (design/hpj11/BRIEF.md): belt-fed from the hold, a round a tick once spun up (1,200
        // rounds a minute; SBW's), 30 blocks a tick, proximity-fuzed against missiles and aircraft (Hpj11Entity).
        stats.put(Reference.id("vehicle_hpj11_ciws"), new GunStats(Reference.id("vehicle_hpj11_ciws"), AUTOCANNON_SHELL, "jeg:inventory_fed",
                0, 0, 0, 1, 14.0F, 30.0F, 90, true, false, 0.35F, 1, null, null, null,
                null, null, null, null, null, 0.16F, 0xFFC700, 2.8F));
        // The speedboat's bow 12.7 mm (an M2 on a pintle, design/speedboat/BRIEF.md): as the PLZ-05's roof gun.
        stats.put(Reference.id("vehicle_speedboat_hmg"), new GunStats(Reference.id("vehicle_speedboat_hmg"), RIFLE_AMMO, "jeg:magazine",
                100, 125, 0, 2, 14.0F, 30.0F, 60, false, false, 0.6F, 1, MACHINE_GUN_FIRE, null, MACHINE_GUN_FIRE,
                null, null, null, null, null, GunStats.STANDARD_BULLET_PROJECTILE_SIZE, 0xFFAA33, 1.5F));
        // The Type-63's 107 mm rockets (design/type63/BRIEF.md): fired as ShellEntity rockets (Type63Entity), twelve in a
        // ripple 14 ticks apart (all twelve in about 8 s, the real rate), then 30 s for the crew to reload the tubes
        // from the crate in 8 s (stat 250 * 0.64; 30 s until the user's review, 2026-11-18: far too slow to play).
        for (String kind : new String[]{"he", "ap", "cm"}) {
            Identifier id = Reference.id("vehicle_type63_" + kind);
            stats.put(id, new GunStats(
                    id,
                    Reference.id("rocket_107mm_" + kind),
                    "jeg:magazine",
                    12,
                    250,
                    0,
                    14,
                    switch (kind) {
                        case "ap" -> 500.0F;
                        case "cm" -> 300.0F;
                        default -> 200.0F;
                    },
                    10.0F,
                    400,
                    true,
                    true,
                    0.5F,
                    1,
                    null,
                    null,
                    null,
                    TYPE63_LOAD,
                    null,
                    TYPE63_LOAD,
                    null,
                    null,
                    0.2F,
                    0xFFC040,
                    1.0F
            ));
        }
        // The TOW (design/tow/BRIEF.md): one missile in the tube, loaded from the crate in its hold in 3.75 s (half SBW's
        // 7.5 s, the user's review, 2026-11-18). The stat is 117 * 0.64 (vehicle reloads run at 0.64, see the Mk42's).
        stats.put(Reference.id("vehicle_tow_missile"), new GunStats(
                Reference.id("vehicle_tow_missile"),
                MEDIUM_ANTI_GROUND_MISSILE,
                "jeg:magazine",
                1,
                117,
                0,
                20,
                600.0F,
                4.0F,
                400,
                false,
                true,
                0.0F,
                1,
                TOW_LAUNCH,
                null,
                TOW_LAUNCH,
                ROCKET_RELOAD_START,
                ROCKET_RELOAD_LOAD,
                ROCKET_RELOAD_END,
                null,
                null,
                0.2F,
                0xFFFFFF,
                1.0F
        ));
        return Map.copyOf(stats);
    }

    private static GunStats vehicleMissileStats(Identifier id, Identifier ammo, int magazineSize, int reloadTime, int fireDelay, float damage) {
        return new GunStats(
                id,
                ammo,
                "jeg:magazine",
                magazineSize,
                reloadTime,
                0,
                fireDelay,
                damage,
                1.0F,
                160,
                false,
                true,
                0.0F,
                1,
                ROCKET_FIRE,
                null,
                ROCKET_FIRE,
                ROCKET_RELOAD_START,
                ROCKET_RELOAD_LOAD,
                ROCKET_RELOAD_END,
                null,
                null,
                0.22F,
                0xFF7744,
                3.0F
        );
    }

    private VehicleWeaponStats() {}

    public static GunStats get(Identifier weaponId) {
        GunStats stats = GunDefinitions.ALL.get(weaponId);
        return stats != null ? stats : STATS.get(weaponId);
    }
}
