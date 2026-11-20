package ttv.migami.jeg.vehicle.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.gun.GunStats;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.network.GunReportPayload;
import ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.projectile.ShellEntity;
import ttv.migami.jeg.vehicle.util.ShellBallistics;

/**
 * A fixed, enclosed gun mount that fires shells (the Mk42, the BL-132): the shared vehicle-gun path handles the
 * gunner's aim, the hold, the drum and its reload; this fires each shot as a {@link ShellEntity} from the next of its
 * barrels along the bore, slides that barrel back in recoil, and sends the report. A subclass gives its numbers: the
 * shells it can load (one vehicle weapon each), the ballistics, its muzzles and recoiling bones.
 */
public abstract class ShellGunEntity extends VehicleEntity {
    /** One loadable shell: the vehicle weapon that fires it, its warhead and the HUD's name for it. */
    public record Shell(Identifier weapon, ShellEntity.Warhead warhead, String hudName) {}

    /** An extra row on the gunner's fire-control panel: a label, its value and the value's colour. */
    public record PanelRow(net.minecraft.network.chat.Component label, net.minecraft.network.chat.Component value, int colour) {}

    private static final EntityDataAccessor<Integer> DATA_SHOTS = SynchedEntityData.defineId(ShellGunEntity.class, EntityDataSerializers.INT);

    /** Client: the shot count last seen, and when each barrel last fired (its recoil runs from there). */
    private int seenShots;
    private final long[] recoilStart;

    protected ShellGunEntity(EntityType<? extends VehicleEntity> type, Level level, String id, int barrels) {
        super(type, level);
        this.recoilStart = new long[barrels];
        java.util.Arrays.fill(this.recoilStart, Long.MIN_VALUE);
        this.setVehicleData(Reference.id(id));
    }

    public abstract ShellBallistics ballistics();

    /** The shells, in the order of the vehicle data's weapons. */
    public abstract List<Shell> shells();

    /** Each barrel's muzzle with the gun at rest, hull coordinates (from the refit's rig.json), in firing order. */
    public abstract List<Vec3> muzzlesRest();

    /** Each barrel's recoiling bone, in the same order as the muzzles. */
    public abstract List<String> recoilBones();

    /** Vanilla projectile inaccuracy for a shot (SBW's spread). */
    public abstract double spread();

    /** The report's sound prefix (GunReportPayload) and loudness on the aircraft curve. */
    public abstract String reportSound();

    public abstract float reportLoudness();

    /** How far a barrel slides back on firing (model pixels) and how long it takes to run out (ticks). */
    public abstract float recoilPixels();

    public abstract int recoilTicks();

    /** Elevation limits in degrees, for the bone driver and the HUD's scale. */
    public abstract float minElevation();

    public abstract float maxElevation();

    /** The HUD panel's title key. */
    public abstract String hudTitle();

    /** Whether this passenger lays and fires the gun (sees its fire-control panel): the driver's seat on a gun mount. */
    public boolean laysGun(@Nullable Entity passenger) {
        return passenger != null && this.getControllingPassenger() == passenger;
    }

    /** Rows this gun adds to the fire-control panel under the range (the howitzer's charge and fire mission). */
    public List<PanelRow> panelRows(@Nullable Entity viewer, float partialTick) {
        return List.of();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SHOTS, 0);
    }

    /** Its boxes are fitted to the model and follow the turret. */
    @Override
    public boolean preciseHitbox() {
        return true;
    }

    @Nullable
    public Shell shellFor(@Nullable Identifier weapon) {
        for (Shell shell : this.shells()) {
            if (shell.weapon().equals(weapon)) {
                return shell;
            }
        }
        return null;
    }

    @Override
    protected boolean firesShell(VehicleWeaponInfo weapon) {
        return this.shellFor(weapon.weaponId()) != null;
    }

    @Override
    protected void fireShell(ServerLevel level, LivingEntity shooter, VehicleWeaponInfo weapon, GunStats stats, Vec3 dataMuzzle) {
        Vec3 muzzle = this.barrelPoint(this.muzzlesRest().get(this.barrelFiring(shooter)), 1.0F);
        Vec3 bore = this.barrelDirection();
        // Vanilla's projectile spread (Projectile#getMovementToShoot), with Superb Warfare's inaccuracy.
        double spread = 0.0172275D * this.spread();
        Vec3 direction = bore.add(this.random.triangle(0.0D, spread), this.random.triangle(0.0D, spread),
                this.random.triangle(0.0D, spread)).normalize();
        ShellEntity shell = new ShellEntity(ModEntities.CANNON_SHELL.get(), level, shooter, this, muzzle,
                direction.scale(this.ballistics().muzzleVelocity()), this.ballistics(), this.shellFor(weapon.weaponId()).warhead());
        if (this.shellsAreRockets()) {
            shell.markRocket();
        }
        level.addFreshEntity(shell);
        this.entityData.set(DATA_SHOTS, this.entityData.get(DATA_SHOTS) + 1);
        // The muzzle blast's smoke (a rocket's backblast behind the launcher, so it never hides the target from its crew,
        // the Type-63 review of 2026-11-18) is drawn by each client from this report as real smoke on the gun as drawn
        // (MissileBlastRenderer.gunFired), no longer the campfire puffs sent from here (2026-11-19).
        GunReportPayload.send(level, this, muzzle, this.reportSound(), this.reportLoudness());
    }

    /** The barrel this shot leaves from (server, as it is fired; the drum has already been drawn on). */
    protected int barrelFiring(LivingEntity shooter) {
        return this.nextBarrel();
    }

    /** Whether this gun's shells are rockets (a motor glow and a short trail as they leave, MissileTrailRenderer). */
    public boolean shellsAreRockets() {
        return false;
    }

    /** The barrel that fires next. */
    public int nextBarrel() {
        return Math.floorMod(this.shotsFired(), this.muzzlesRest().size());
    }

    /** The next barrel's muzzle this frame, in the world. */
    public Vec3 muzzle(float partialTick) {
        return this.barrelPoint(this.muzzlesRest().get(this.nextBarrel()), partialTick);
    }

    /** The gun's elevation above level, degrees (the turret pitch is the view's: up is negative). */
    public float elevation(float partialTick) {
        return -this.turretPitch(partialTick);
    }

    /** The compass bearing the gun is laid on, degrees from north. */
    public int bearing(float partialTick) {
        float worldYaw = Mth.rotLerp(partialTick, this.yRotO, this.getYRot()) - this.turretYaw(partialTick);
        return Math.floorMod(Math.round(worldYaw + 180.0F), 360);
    }

    /** The passenger's selected shell, or the first one when no shell weapon is selected. */
    public Shell selectedShell(Entity passenger) {
        Shell shell = this.shellFor(this.selectedVehicleWeaponId(passenger));
        return shell != null ? shell : this.shells().getFirst();
    }

    /** Shells fired over the gun's life (synced; the client's recoil follows it). */
    public int shotsFired() {
        return this.entityData.get(DATA_SHOTS);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            int shots = this.shotsFired();
            if (shots != this.seenShots) {
                // Not on the first sight of the gun: a count that was already up when it came into view isn't a shot.
                if (this.tickCount > 1) {
                    int barrels = this.recoilStart.length;
                    for (int shot = Math.max(this.seenShots, shots - barrels); shot < shots; shot++) {
                        this.recoilStart[Math.floorMod(shot, barrels)] = this.level().getGameTime();
                    }
                }
                this.seenShots = shots;
            }
        }
    }

    /** How far a barrel has slid back, in model pixels (client). */
    public float recoilPixels(int barrel, float partialTick) {
        long start = this.recoilStart[barrel];
        if (start == Long.MIN_VALUE) {
            return 0.0F;
        }
        float age = (this.level().getGameTime() - start) + partialTick;
        if (age < 0.0F || age > this.recoilTicks()) {
            return 0.0F;
        }
        return this.recoilPixels() * (1.0F - Mth.clamp(age / this.recoilTicks(), 0.0F, 1.0F));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        String rest = "animation." + this.vehicleDataId().getPath() + ".rest";
        controllers.add(new AnimationController<>("Vehicle", 0, state -> {
            state.setAndContinue(RawAnimation.begin().thenLoop(rest));
            return PlayState.CONTINUE;
        }));
    }
}
