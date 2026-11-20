package ttv.migami.jeg.vehicle.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;
import ttv.migami.jeg.vehicle.projectile.ShellEntity;
import ttv.migami.jeg.vehicle.util.ShellBallistics;

/**
 * The Type-63 107 mm towed rocket launcher (Superb Warfare's, see design/type63/BRIEF.md): twelve tubes on a
 * two-wheeled carriage. A crouched gunner lays it by eye within the carriage's 15 degrees either side (the mount slews
 * at its handwheels' pace; A/D turns the whole carriage), and a held trigger ripples the loaded tubes off in order.
 * The crew reloads all twelve from the crate. Rockets fly as shells (ShellEntity) with a short-burning motor.
 */
public final class Type63Entity extends ShellGunEntity {
    public static final String ID = "type63";
    public static final int TUBES = 12;
    /** SBW's rockets: 10 blocks a tick, gravity 0.05 (about 2,000 blocks at 45 degrees). */
    public static final ShellBallistics BALLISTICS = new ShellBallistics(10.0D, 0.05D, 400);
    /** SBW's AP rocket: 500 on what it strikes, 100 within 5. */
    public static final ShellEntity.Warhead AP = new ShellEntity.Warhead(5.0F, 100.0F, 500.0F, 4.0F, 0.0D, 0);
    /** SBW's HE rocket: 200 direct, 200 within 12; blocks as power 6 (a mortar round is 8, but twelve come at once). */
    public static final ShellEntity.Warhead HE = new ShellEntity.Warhead(12.0F, 200.0F, 200.0F, 6.0F, 0.0D, 0);
    /** SBW's cluster rocket: 12 bomblets of 150 direct, 125 within 3 (6x damage and 5x blast over the count). */
    public static final ShellEntity.Warhead BOMBLET = new ShellEntity.Warhead(3.0F, 300.0F * 5.0F / 12.0F, 300.0F * 6.0F / 12.0F, 1.5F, 0.0D, 0);
    public static final ShellEntity.Warhead CM = new ShellEntity.Warhead(6.0F, 300.0F, 300.0F, 4.0F, 0.0D, 0,
            new ShellEntity.Dispense(12, 8.0D, 0.4D, 0.6D, 3, 7, BOMBLET, new ShellBallistics(10.0D, 0.05D, 100)));
    public static final Identifier HE_WEAPON = Reference.id("vehicle_type63_he");
    public static final Identifier AP_WEAPON = Reference.id("vehicle_type63_ap");
    public static final Identifier CM_WEAPON = Reference.id("vehicle_type63_cm");
    private static final List<Shell> SHELLS = List.of(
            new Shell(HE_WEAPON, HE, "hud.jeg.shell.he"),
            new Shell(AP_WEAPON, AP, "hud.jeg.shell.ap"),
            new Shell(CM_WEAPON, CM, "hud.jeg.shell.cluster_12"));
    /** The tubes' muzzles in SBW's firing order, top row first (design/type63/rig.json). */
    public static final List<Vec3> MUZZLES = List.of(
            new Vec3(-0.2851D, 1.0535D, 0.6021D), new Vec3(-0.095D, 1.0535D, 0.6021D), new Vec3(0.0952D, 1.0535D, 0.6021D), new Vec3(0.2853D, 1.0535D, 0.6021D),
            new Vec3(-0.2853D, 0.8634D, 0.6021D), new Vec3(-0.0952D, 0.8634D, 0.6021D), new Vec3(0.0952D, 0.8634D, 0.6021D), new Vec3(0.2853D, 0.8634D, 0.6021D),
            new Vec3(-0.2853D, 0.6732D, 0.6021D), new Vec3(-0.0952D, 0.6732D, 0.6021D), new Vec3(0.095D, 0.6732D, 0.6021D), new Vec3(0.2851D, 0.6732D, 0.6021D));
    private static final List<String> ROCKETS;
    /** A/D turns the carriage: the crew drags the trail round, slowly (degrees per tick). */
    public static final float CARRIAGE_TURN_RATE = 1.0F;

    static {
        List<String> rockets = new ArrayList<>();
        for (int i = 1; i <= TUBES; i++) {
            rockets.add("rocket_" + i);
        }
        ROCKETS = List.copyOf(rockets);
    }

    public Type63Entity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level, ID, TUBES);
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

    /** Rockets don't recoil; the bones listed are the rockets themselves (hidden as they go, see rocketsLoaded). */
    @Override
    public List<String> recoilBones() {
        return ROCKETS;
    }

    /** SBW's rocket spread: a ripple scatters over the target area. */
    @Override
    public double spread() {
        return 0.5D;
    }

    @Override
    public String reportSound() {
        return "vehicle.type63";
    }

    @Override
    public float reportLoudness() {
        return 2.6F;
    }

    @Override
    public float recoilPixels() {
        return 0.0F;
    }

    @Override
    public int recoilTicks() {
        return 1;
    }

    @Override
    public float minElevation() {
        return -5.0F;
    }

    @Override
    public float maxElevation() {
        return 60.0F;
    }

    @Override
    public String hudTitle() {
        return "hud.jeg.type63.title";
    }

    /** Rockets still in the tubes for the gunner's (or else the first) shell type. */
    public int rocketsLoaded() {
        Entity gunner = this.getControllingPassenger();
        return Mth.clamp(gunner == null ? this.vehicleWeaponSlotAmmo(0) : this.selectedVehicleWeaponAmmo(gunner), 0, TUBES);
    }

    /** The tubes empty in order and are reloaded all together, so the next to fire is the first still loaded. */
    @Override
    public int nextBarrel() {
        return Mth.clamp(TUBES - this.rocketsLoaded(), 0, TUBES - 1);
    }

    /** On the server the tube's round has already been taken when the shot is made: the one fired is one further back. */
    @Override
    protected int barrelFiring(LivingEntity shooter) {
        return Mth.clamp(TUBES - 1 - this.selectedVehicleWeaponAmmo(shooter), 0, TUBES - 1);
    }

    @Override
    public boolean shellsAreRockets() {
        return true;
    }

    /** The towing eye at the end of the trail (the carriage's hitbox ends there, design/type63/rig.json): towed trail first. */
    public static final Vec3 TOW_EYE = new Vec3(0.0D, 0.25D, -1.95D);

    @Override
    public Vec3 towEye() {
        return TOW_EYE;
    }

    @Override
    public void tick() {
        super.tick();
        this.tickCarriage();
    }

    /**
     * A/D drags the carriage round. A player gunner's client is authoritative for the vehicle's yaw (vanilla sends it
     * up in its vehicle-move packet), so the carriage turns there and the server follows (as the mortar's traverse).
     */
    private void tickCarriage() {
        Entity gunner = this.getControllingPassenger();
        VehicleInput input = this.landInput();
        if (!(gunner instanceof Player) || input == null || input.strafeAxis() == 0 || this.isWrecked()) {
            return;
        }
        boolean authoritative = this.level().isClientSide() ? this.isLocalInstanceAuthoritative() : !(gunner instanceof ServerPlayer);
        if (authoritative) {
            this.setYRot(Mth.wrapDegrees(this.getYRot() - input.strafeAxis() * CARRIAGE_TURN_RATE));
        }
    }
}
