package ttv.migami.jeg.vehicle.entity;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.projectile.ShellEntity;
import ttv.migami.jeg.vehicle.util.ShellBallistics;

/**
 * The Mk42 5"/54 gun mount (Superb Warfare's, see design/mk42/BRIEF.md): a fixed, enclosed gun that defends a base
 * against the ground and the air. The gunner looks and the mount slews after the view (40 deg/s train, 25 deg/s
 * elevation); it fires HE (proximity fuzed), AP, cluster or incendiary from a 20-round loader drum, 40 a minute.
 */
public final class Mk42Entity extends ShellGunEntity {
    public static final String ID = "mk42";
    /** Superb Warfare's Mk42 shell: 15 blocks a tick, gravity 0.06 (about 3,750 blocks at 45 degrees, no drag). */
    public static final ShellBallistics BALLISTICS = new ShellBallistics(15.0D, 0.06D, 400);
    /** SBW's AP shell: 450 on what it strikes, 120 within 3 blocks. */
    public static final ShellEntity.Warhead AP = new ShellEntity.Warhead(3.0F, 120.0F, 450.0F, 3.0F, 0.0D, 0);
    /** SBW's HE shell (250 direct, 120 within 10), with a proximity fuze: 4 blocks, armed after 10 ticks (150 blocks). */
    public static final ShellEntity.Warhead HE = new ShellEntity.Warhead(10.0F, 120.0F, 250.0F, 6.0F, 4.0D, 10);
    /**
     * SBW's cluster shell: 300 direct, 180 within 6 if it strikes whole; 3 ticks out, once a block or target lies
     * within 7 ticks of flight ahead, it breaks into 18 bomblets thrown on at 0.4-0.6 of its speed (spread 8), each
     * 100 on what it strikes and 50 within 3 blocks (SBW's GunGrenade numbers: 6x damage and 5x blast over the count).
     */
    public static final ShellEntity.Warhead BOMBLET = new ShellEntity.Warhead(3.0F, 50.0F, 100.0F, 1.5F, 0.0D, 0);
    public static final ShellEntity.Warhead CM = new ShellEntity.Warhead(6.0F, 180.0F, 300.0F, 4.0F, 0.0D, 0,
            new ShellEntity.Dispense(18, 8.0D, 0.4D, 0.6D, 3, 7, BOMBLET, new ShellBallistics(15.0D, 0.06D, 100)));
    /**
     * The incendiary shell, in grapeshot's place (the user, 2026-11-19: "change GS shells to incendiary shells which
     * explode over a target leaving flames below it"; SBW's grapeshot was a canister of balls from the muzzle). Armed
     * after 2 ticks (30 blocks), it bursts 7 blocks over where it would come down (at most 16 short of it on a shallow
     * path): 40 within 4 blocks of the burst, and 28 burning fragments rain over 5 blocks round the spot, each 12 on what
     * it strikes, which burns for 8 seconds, and setting the ground alight where it lands.
     */
    public static final ShellEntity.Warhead FIRE_FRAGMENT = ShellEntity.Warhead.fireFragment(12.0F, 8);
    /** The fragments fall under a little less gravity than a shell, so they are seen raining down. */
    public static final ShellBallistics FRAGMENT_BALLISTICS = new ShellBallistics(1.0D, 0.04D, 80);
    public static final ShellEntity.Warhead INCENDIARY = ShellEntity.Warhead.incendiaryShell(4.0F, 40.0F, 150.0F,
            new ShellEntity.Incendiary(7.0D, 16.0D, 2, 28, 5.0D, FIRE_FRAGMENT, FRAGMENT_BALLISTICS));
    public static final Identifier AP_WEAPON = Reference.id("vehicle_mk42_ap");
    public static final Identifier HE_WEAPON = Reference.id("vehicle_mk42_he");
    public static final Identifier CM_WEAPON = Reference.id("vehicle_mk42_cm");
    public static final Identifier GS_WEAPON = Reference.id("vehicle_mk42_gs");
    private static final List<Shell> SHELLS = List.of(
            new Shell(HE_WEAPON, HE, "hud.jeg.shell.he_proximity"),
            new Shell(AP_WEAPON, AP, "hud.jeg.shell.ap"),
            new Shell(CM_WEAPON, CM, "hud.jeg.shell.cluster"),
            new Shell(GS_WEAPON, INCENDIARY, "hud.jeg.shell.incendiary"));
    /** The muzzle with the gun at rest, hull coordinates (design/mk42/rig.json muzzle_rest; the weapons' muzzle). */
    public static final Vec3 MUZZLE_REST = new Vec3(0.0D, 2.1601D, 6.9446D);
    /** The report's loudness on the aircraft distance curve: a 5-inch gun, above the mortar's 2.5. */
    public static final float REPORT_LOUDNESS = 2.8F;

    public Mk42Entity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level, ID, 1);
    }

    @Nullable
    public static ShellEntity.Warhead warheadFor(@Nullable Identifier weapon) {
        for (Shell shell : SHELLS) {
            if (shell.weapon().equals(weapon)) {
                return shell.warhead();
            }
        }
        return null;
    }

    @Override
    public ShellBallistics ballistics() {
        return BALLISTICS;
    }

    @Override
    public List<Shell> shells() {
        return SHELLS;
    }

    @Override
    public List<Vec3> muzzlesRest() {
        return List.of(MUZZLE_REST);
    }

    @Override
    public List<String> recoilBones() {
        return List.of("gun_slide");
    }

    /** SBW's spread, in vanilla projectile inaccuracy units. */
    @Override
    public double spread() {
        return 0.05D;
    }

    @Override
    public String reportSound() {
        return "vehicle.mk42";
    }

    @Override
    public float reportLoudness() {
        return REPORT_LOUDNESS;
    }

    /** SBW's fire clip: the gun slides 11 px into the house at once and runs out over about 6 ticks. */
    @Override
    public float recoilPixels() {
        return 11.0F;
    }

    @Override
    public int recoilTicks() {
        return 6;
    }

    @Override
    public float minElevation() {
        return -15.0F;
    }

    @Override
    public float maxElevation() {
        return 85.0F;
    }

    @Override
    public String hudTitle() {
        return "hud.jeg.mk42.title";
    }
}
