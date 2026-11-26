package ttv.migami.jeg.vehicle.entity;

import java.util.List;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;

/**
 * The Type-63 107 mm towed rocket launcher (Superb Warfare's, see design/type63/BRIEF.md): twelve tubes on a
 * two-wheeled carriage (the pod itself, its rockets and ripple are RocketPodEntity's). A crouched gunner lays it by eye within the carriage's 15 degrees either side (the mount slews
 * at its handwheels' pace; A/D turns the whole carriage), and a held trigger ripples the loaded tubes off in order.
 * The crew reloads all twelve from the crate. Rockets fly as shells (ShellEntity) with a short-burning motor.
 */
public final class Type63Entity extends RocketPodEntity {
    public static final String ID = "type63";
    /** The tubes' muzzles in SBW's firing order, top row first (design/type63/rig.json). */
    public static final List<Vec3> MUZZLES = List.of(
            new Vec3(-0.2851D, 1.0535D, 0.6021D), new Vec3(-0.095D, 1.0535D, 0.6021D), new Vec3(0.0952D, 1.0535D, 0.6021D), new Vec3(0.2853D, 1.0535D, 0.6021D),
            new Vec3(-0.2853D, 0.8634D, 0.6021D), new Vec3(-0.0952D, 0.8634D, 0.6021D), new Vec3(0.0952D, 0.8634D, 0.6021D), new Vec3(0.2853D, 0.8634D, 0.6021D),
            new Vec3(-0.2853D, 0.6732D, 0.6021D), new Vec3(-0.0952D, 0.6732D, 0.6021D), new Vec3(0.095D, 0.6732D, 0.6021D), new Vec3(0.2851D, 0.6732D, 0.6021D));
    /** A/D turns the carriage: the crew drags the trail round, slowly (degrees per tick). */
    public static final float CARRIAGE_TURN_RATE = 1.0F;

    public Type63Entity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level, ID);
    }

    @Override
    public List<Vec3> muzzlesRest() {
        return MUZZLES;
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
