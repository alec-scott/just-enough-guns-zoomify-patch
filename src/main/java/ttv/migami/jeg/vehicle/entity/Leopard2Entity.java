package ttv.migami.jeg.vehicle.entity;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.data.subdata.SeatInfo;
import ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.Leopard2Rig;
import ttv.migami.jeg.vehicle.util.ShellBallistics;

/**
 * The Leopard 2A7 main battle tank (our own model, design/leopard2/BRIEF.md), replacing Superb Warfare's YX-100 and
 * keeping its behaviour: the driver drives and lays the stabilised 120 mm (the Mk42's 5-inch shells, flat and fast,
 * fired on the move) and the coax; the commander works a 12.7 mm remote weapon station on the roof; two riders sit on
 * the rear deck. The gun can't depress over the rear deck.
 */
public final class Leopard2Entity extends ShellGunEntity {
    public static final String ID = "leopard2";
    public static final int DRIVER_SEAT = 0, COMMANDER_SEAT = 1;
    /** SBW's YX-100 cannon: 40 blocks a tick; the 5-inch shells' gravity. */
    public static final ShellBallistics BALLISTICS = new ShellBallistics(40.0D, 0.06D, 150);
    public static final Identifier HE_WEAPON = Reference.id("vehicle_leopard2_he");
    public static final Identifier AP_WEAPON = Reference.id("vehicle_leopard2_ap");
    public static final Identifier CM_WEAPON = Reference.id("vehicle_leopard2_cm");
    public static final Identifier GS_WEAPON = Reference.id("vehicle_leopard2_gs");
    public static final Identifier RWS_WEAPON = Reference.id("vehicle_leopard2_rws");
    private static final List<Shell> SHELLS = List.of(
            new Shell(HE_WEAPON, Mk42Entity.HE, "hud.jeg.shell.he_proximity"),
            new Shell(AP_WEAPON, Mk42Entity.AP, "hud.jeg.shell.ap"),
            new Shell(CM_WEAPON, Mk42Entity.CM, "hud.jeg.shell.cluster"),
            new Shell(GS_WEAPON, Mk42Entity.GRAPESHOT, "hud.jeg.shell.grapeshot"));

    public Leopard2Entity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level, ID, 1);
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
        return List.of(Leopard2Rig.MUZZLE_REST);
    }

    @Override
    public List<String> recoilBones() {
        return List.of("gun_tube");
    }

    /** A stabilised tank gun: tight. */
    @Override
    public double spread() {
        return 0.02D;
    }

    @Override
    public String reportSound() {
        return "vehicle.leopard2";
    }

    @Override
    public float reportLoudness() {
        return 3.0F;
    }

    @Override
    public float recoilPixels() {
        return Leopard2Rig.RECOIL_PIXELS;
    }

    @Override
    public int recoilTicks() {
        return 8;
    }

    @Override
    public float minElevation() {
        return Leopard2Rig.MIN_ELEVATION;
    }

    @Override
    public float maxElevation() {
        return Leopard2Rig.MAX_ELEVATION;
    }

    @Override
    public String hudTitle() {
        return "hud.jeg.leopard2.title";
    }

    /** Over the rear deck the gun stays at or above level. */
    @Override
    protected float clampTurretPitch(float turretYaw, float pitch) {
        if (Math.abs(Mth.wrapDegrees(turretYaw)) >= 180.0F - Leopard2Rig.REAR_ARC) {
            return Math.min(pitch, -Leopard2Rig.REAR_MIN_ELEVATION);
        }
        return pitch;
    }

    // ---- The commander's remote weapon station.

    /** The commander sits under the weapon station, which turns with the turret. */
    @Override
    protected boolean seatRidesTurret(SeatInfo seat) {
        return seat.index() == COMMANDER_SEAT;
    }

    @Nullable
    public Entity passengerIn(int seat) {
        for (Entity passenger : this.getPassengers()) {
            if (this.seatOf(passenger) == seat) {
                return passenger;
            }
        }
        return null;
    }

    /** A point given in hull coordinates with the turret straight ahead, in the world as the turret is turned now. */
    public Vec3 onTurret(Vec3 point, float partialTick) {
        var turret = this.vehicleData().defaults().turret();
        Vec3 origin = new Vec3(turret.originX(), turret.originY(), turret.originZ());
        float hullYaw = Mth.rotLerp(partialTick, this.yRotO, this.getYRot());
        float gunYaw = hullYaw - this.turretYaw(partialTick);
        return this.getPosition(partialTick).add(origin.yRot(-hullYaw * Mth.DEG_TO_RAD))
                .add(point.subtract(origin).yRot(-gunYaw * Mth.DEG_TO_RAD));
    }

    @Override
    @Nullable
    protected Vec3 seatGunMuzzle(VehicleWeaponInfo weapon, Vec3 direction) {
        if (!RWS_WEAPON.equals(weapon.weaponId())) {
            return null;
        }
        return this.onTurret(Leopard2Rig.RWS_TRUNNION, 1.0F).add(direction.normalize().scale(Leopard2Rig.RWS_ARM));
    }
}
