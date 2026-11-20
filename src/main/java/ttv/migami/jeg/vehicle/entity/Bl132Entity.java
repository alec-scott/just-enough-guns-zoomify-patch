package ttv.migami.jeg.vehicle.entity;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.projectile.ShellEntity;
import ttv.migami.jeg.vehicle.util.ShellBallistics;

/**
 * The BL-132 130 mm/58 four-gun mount (Superb Warfare's, see design/bl132/BRIEF.md): the heavy base gun beside the
 * Mk42. Two tiers of twin guns fire in turn, four rounds in under a second, then the crew reloads all four (4.5 s).
 * It loads the same 5-inch shells as the Mk42, with SBW's heavier numbers for its 130 mm gun.
 */
public final class Bl132Entity extends ShellGunEntity {
    public static final String ID = "bl132";
    /** SBW's numbers: 15 blocks a tick, gravity 0.06. */
    public static final ShellBallistics BALLISTICS = new ShellBallistics(15.0D, 0.06D, 400);
    /** SBW's AP: 470 on what it strikes, 125 within 3.7 blocks. */
    public static final ShellEntity.Warhead AP = new ShellEntity.Warhead(3.7F, 125.0F, 470.0F, 3.7F, 0.0D, 0);
    /** SBW's HE: 250 direct, 160 within 11; the Mk42's proximity fuze. */
    public static final ShellEntity.Warhead HE = new ShellEntity.Warhead(11.0F, 160.0F, 250.0F, 6.5F, 4.0D, 10);
    /** SBW's cluster: 360 direct, 220 within 7; 18 bomblets of 120 direct and 61 within 3.5 (6x and 5x over 18). */
    public static final ShellEntity.Warhead BOMBLET = new ShellEntity.Warhead(3.5F, 220.0F * 5.0F / 18.0F, 360.0F * 6.0F / 18.0F, 1.5F, 0.0D, 0);
    public static final ShellEntity.Warhead CM = new ShellEntity.Warhead(7.0F, 220.0F, 360.0F, 4.0F, 0.0D, 0,
            new ShellEntity.Dispense(18, 8.0D, 0.4D, 0.6D, 3, 7, BOMBLET, new ShellBallistics(15.0D, 0.06D, 100)));
    /**
     * The incendiary, in grapeshot's place as the Mk42's (2026-11-19), larger for the 130 mm: it bursts 8 blocks over
     * where it would come down, 50 within 4.5, and 36 fragments of 14 rain over 6 blocks round the spot.
     */
    public static final ShellEntity.Warhead FIRE_FRAGMENT = ShellEntity.Warhead.fireFragment(14.0F, 8);
    public static final ShellEntity.Warhead INCENDIARY = ShellEntity.Warhead.incendiaryShell(4.5F, 50.0F, 170.0F,
            new ShellEntity.Incendiary(8.0D, 16.0D, 2, 36, 6.0D, FIRE_FRAGMENT, Mk42Entity.FRAGMENT_BALLISTICS));
    public static final Identifier HE_WEAPON = Reference.id("vehicle_bl132_he");
    public static final Identifier AP_WEAPON = Reference.id("vehicle_bl132_ap");
    public static final Identifier CM_WEAPON = Reference.id("vehicle_bl132_cm");
    public static final Identifier GS_WEAPON = Reference.id("vehicle_bl132_gs");
    private static final List<Shell> SHELLS = List.of(
            new Shell(HE_WEAPON, HE, "hud.jeg.shell.he_proximity"),
            new Shell(AP_WEAPON, AP, "hud.jeg.shell.ap"),
            new Shell(CM_WEAPON, CM, "hud.jeg.shell.cluster"),
            new Shell(GS_WEAPON, INCENDIARY, "hud.jeg.shell.incendiary"));
    /** Muzzles in SBW's firing order, lower pair then upper, alternating sides (design/bl132/rig.json). */
    public static final List<Vec3> MUZZLES = List.of(
            new Vec3(1.2469D, 2.0812D, 7.1437D), new Vec3(-1.2469D, 2.0812D, 7.1437D),
            new Vec3(1.2469D, 3.1875D, 7.1437D), new Vec3(-1.2469D, 3.1875D, 7.1437D));
    private static final List<String> SLIDES = List.of("gun_2_slide", "gun_4_slide", "gun_1_slide", "gun_3_slide");

    public Bl132Entity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level, ID, 4);
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
        return MUZZLES;
    }

    @Override
    public List<String> recoilBones() {
        return SLIDES;
    }

    @Override
    public double spread() {
        return 0.05D;
    }

    @Override
    public String reportSound() {
        return "vehicle.bl132";
    }

    /** A heavier gun than the Mk42's 2.8. */
    @Override
    public float reportLoudness() {
        return 3.0F;
    }

    /** SBW's fire clip: each slide goes 11 px back in a tick and runs out over about 10. */
    @Override
    public float recoilPixels() {
        return 11.0F;
    }

    @Override
    public int recoilTicks() {
        return 10;
    }

    @Override
    public float minElevation() {
        return -5.0F;
    }

    @Override
    public float maxElevation() {
        return 85.0F;
    }

    @Override
    public String hudTitle() {
        return "hud.jeg.bl132.title";
    }
}
