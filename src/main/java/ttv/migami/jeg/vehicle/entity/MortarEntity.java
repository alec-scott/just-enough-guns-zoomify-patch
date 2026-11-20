package ttv.migami.jeg.vehicle.entity;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.init.ModSounds;
import ttv.migami.jeg.network.GunReportPayload;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleInput;
import ttv.migami.jeg.vehicle.projectile.MortarRoundEntity;
import ttv.migami.jeg.vehicle.util.MortarRig;

/**
 * The 81 mm mortar (Superb Warfare's, refit to real size; see design/mortar/BRIEF.md). Carried as an item and set up
 * from the hand; one crouched gunner lays it with W/S (elevation) and A/D (traverse) and loads with R or the left
 * mouse button. A round drops down the tube and fires a second later; the next can go in four seconds after the last
 * was loaded (15 a minute, the real sustained rate). A second player can load from outside with a round in hand.
 */
public final class MortarEntity extends VehicleEntity implements ttv.migami.jeg.item.DeployableWeaponItem.SetsUp {
    public static final String ID = "mortar";
    /** Ticks from a round going down the tube to it leaving (the fire clip's flash is at 1.0 s). */
    public static final int DRILL_TICKS = 20;
    /** Ticks from firing until the next round can be loaded. */
    public static final int RELOAD_TICKS = 60;
    /** Ticks to set the baseplate and lay the bipod after deploying. */
    public static final int SETUP_TICKS = 40;
    /** Elevation and traverse rates, degrees per tick (5 and 10 degrees a second). */
    public static final float ELEVATION_RATE = 0.25F;
    public static final float TRAVERSE_RATE = 0.5F;
    /** How long the fire clip (drop, slide, flash) runs, in ticks. */
    public static final int FIRE_CLIP_TICKS = 30;
    /** A round won't be loaded with a block this close in front of the muzzle: it would burst on the crew's cover. */
    public static final double CLEARANCE = 3.0D;
    /** The report's loudness on the aircraft distance curve, as loud as a helicopter's gun. */
    public static final float REPORT_LOUDNESS = 2.5F;

    private static final EntityDataAccessor<Float> DATA_ELEVATION = SynchedEntityData.defineId(MortarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_DRILL = SynchedEntityData.defineId(MortarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COOLDOWN = SynchedEntityData.defineId(MortarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SETUP = SynchedEntityData.defineId(MortarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SINCE_LOAD = SynchedEntityData.defineId(MortarEntity.class, EntityDataSerializers.INT);

    private float elevationO = 60.0F;
    private int loaderId = -1;
    /** Set when the gunner presses R or the left mouse button; served on the next tick. */
    @Nullable
    private ServerPlayer loadRequest;
    private boolean fireHeld;
    /** For game tests: rounds fired over the entity's life. */
    private int roundsFired;

    public MortarEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.setVehicleData(Reference.id(ID));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ELEVATION, 60.0F);
        builder.define(DATA_DRILL, 0);
        builder.define(DATA_COOLDOWN, 0);
        builder.define(DATA_SETUP, 0);
        builder.define(DATA_SINCE_LOAD, 1000);
    }

    /**
     * The mortar's round is its own drill, not a vehicle gun. An empty "weapons" list in the data falls back to the test
     * vehicle's guns (VehicleDataManager), so they are switched off here, as on the bulldozer.
     */
    @Override
    public boolean hasVehicleWeapons() {
        return false;
    }

    public float elevation() {
        return this.entityData.get(DATA_ELEVATION);
    }

    public float elevation(float partialTick) {
        return Mth.lerp(partialTick, this.elevationO, this.elevation());
    }

    public void setElevation(float elevation) {
        this.entityData.set(DATA_ELEVATION, MortarRig.clampElevation(elevation));
    }

    /** Ticks until a round can be loaded: setting up, a round on the way, or the reload after the last one. */
    public int readyInTicks() {
        return Math.max(this.entityData.get(DATA_SETUP), Math.max(this.entityData.get(DATA_DRILL), this.entityData.get(DATA_COOLDOWN)));
    }

    public boolean settingUp() {
        return this.entityData.get(DATA_SETUP) > 0;
    }

    public boolean roundInTube() {
        return this.entityData.get(DATA_DRILL) > 0;
    }

    public int ticksSinceLoad() {
        return this.entityData.get(DATA_SINCE_LOAD);
    }

    public int roundsFired() {
        return this.roundsFired;
    }

    public void startSetup() {
        this.entityData.set(DATA_SETUP, SETUP_TICKS);
    }

    @Override
    public void tick() {
        this.elevationO = this.elevation();
        super.tick();
        this.tickTraverse();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        this.countDown(DATA_SETUP);
        this.countDown(DATA_COOLDOWN);
        int since = this.entityData.get(DATA_SINCE_LOAD);
        if (since < 1000) {
            this.entityData.set(DATA_SINCE_LOAD, since + 1);
        }
        Entity gunner = this.getControllingPassenger();
        VehicleInput input = this.landInput();
        if (gunner instanceof Player && input != null && !this.isWrecked()) {
            // A hit on the tube jams the elevating gear; the traverse is on the baseplate and still turns.
            if (input.forwardAxis() != 0 && !this.isTurretDamaged()) {
                this.setElevation(this.elevation() + input.forwardAxis() * ELEVATION_RATE);
            }
        }
        if (this.loadRequest != null) {
            ServerPlayer player = this.loadRequest;
            this.loadRequest = null;
            if (player.getVehicle() == this) {
                this.tryLoad(player, null);
            }
        }
        int drill = this.entityData.get(DATA_DRILL);
        if (drill > 0) {
            this.entityData.set(DATA_DRILL, drill - 1);
            if (drill == 1) {
                this.fire(level);
            }
        }
    }

    /**
     * A/D traverse. A player gunner's client is authoritative for the vehicle's position and yaw (vanilla sends them up
     * in its vehicle-move packet), so the mortar turns there, and the server takes the new bearing from that packet;
     * turning it on the server would be overwritten by the client's yaw every tick (found by MortarClientGameTest).
     */
    private void tickTraverse() {
        Entity gunner = this.getControllingPassenger();
        VehicleInput input = this.landInput();
        if (!(gunner instanceof Player) || input == null || input.strafeAxis() == 0 || this.isWrecked()) {
            return;
        }
        boolean authoritative = this.level().isClientSide() ? this.isLocalInstanceAuthoritative() : !(gunner instanceof ServerPlayer);
        if (authoritative) {
            this.setYRot(Mth.wrapDegrees(this.getYRot() - input.strafeAxis() * TRAVERSE_RATE));
        }
    }

    /**
     * The baseplate turns only on its traverse handwheel (A/D, {@link #TRAVERSE_RATE}); the mouse looks around. Taking
     * the heading from the view as well made it turn at mouse speed, and with the view turning with the mount it spun
     * on its own (the user's review, 2026-11-18).
     */
    @Override
    public boolean yawFollowsRider(Entity rider) {
        return false;
    }

    private void countDown(EntityDataAccessor<Integer> accessor) {
        int value = this.entityData.get(accessor);
        if (value > 0) {
            this.entityData.set(accessor, value - 1);
        }
    }

    @Override
    public void processInput(ServerPlayer player, VehicleInput input) {
        super.processInput(player, input);
        if (player.getVehicle() != this || player != this.getControllingPassenger()) {
            return;
        }
        boolean fire = input.fire();
        if (input.reload() || fire && !this.fireHeld) {
            this.loadRequest = player;
        }
        this.fireHeld = fire;
    }

    /**
     * Puts a round down the tube: from {@code hand} when given (a second player loading from outside), else from the
     * player's inventory. Creative players need no round. Returns whether one went in.
     */
    public boolean tryLoad(Player player, @Nullable ItemStack hand) {
        if (this.isWrecked() || this.readyInTicks() > 0) {
            return false;
        }
        if (this.tubeBlocked()) {
            player.sendOverlayMessage(Component.translatable("message.jeg.mortar.blocked"));
            return false;
        }
        boolean creative = player.getAbilities().instabuild;
        ItemStack round = hand != null ? hand : findRound(player);
        if (round.isEmpty() && !creative) {
            player.sendOverlayMessage(Component.translatable("message.jeg.mortar.no_rounds"));
            return false;
        }
        if (!creative) {
            round.shrink(1);
        }
        this.loaderId = player.getId();
        this.entityData.set(DATA_DRILL, DRILL_TICKS);
        this.entityData.set(DATA_SINCE_LOAD, 0);
        this.playSound(Reference.id("vehicle.mortar.load"), 1.0F);
        return true;
    }

    /** Whether a block stands within {@link #CLEARANCE} blocks of the muzzle along the line of fire. */
    public boolean tubeBlocked() {
        float elevation = this.elevation();
        Vec3 muzzle = MortarRig.muzzleWorld(this.position(), this.getYRot(), elevation);
        Vec3 end = muzzle.add(MortarRig.launchVelocity(this.getYRot(), elevation).normalize().scale(CLEARANCE));
        return this.level().clip(new net.minecraft.world.level.ClipContext(muzzle, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() != net.minecraft.world.phys.HitResult.Type.MISS;
    }

    private static ItemStack findRound(Player player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.MORTAR_ROUND.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private void fire(ServerLevel level) {
        float elevation = this.elevation();
        float yaw = this.getYRot();
        Vec3 muzzle = MortarRig.muzzleWorld(this.position(), yaw, elevation);
        Vec3 direction = MortarRig.launchVelocity(yaw, elevation).normalize();
        // Vanilla's projectile spread (Projectile#getMovementToShoot), with Superb Warfare's inaccuracy.
        double spread = 0.0172275D * MortarRig.SPREAD;
        direction = direction.add(this.random.triangle(0.0D, spread), this.random.triangle(0.0D, spread), this.random.triangle(0.0D, spread));
        Entity loader = this.loaderId < 0 ? null : level.getEntity(this.loaderId);
        level.addFreshEntity(new MortarRoundEntity(level, loader, this, muzzle, direction.normalize().scale(MortarRig.MUZZLE_VELOCITY)));
        this.entityData.set(DATA_COOLDOWN, RELOAD_TICKS);
        this.roundsFired++;
        Vec3 out = Vec3.directionFromRotation(-elevation, yaw);
        level.sendParticles(ParticleTypes.EXPLOSION, muzzle.x, muzzle.y, muzzle.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, muzzle.x + out.x * 0.3D, muzzle.y + out.y * 0.3D, muzzle.z + out.z * 0.3D,
                6, 0.15D, 0.15D, 0.15D, 0.02D);
        GunReportPayload.send(level, this, muzzle, "vehicle.mortar", REPORT_LOUDNESS);
    }

    private void playSound(net.minecraft.resources.Identifier id, float volume) {
        var holder = ModSounds.ALL.get(id);
        if (holder != null) {
            SoundEvent sound = holder.get();
            this.level().playSound(null, this.getX(), this.getY() + 0.8D, this.getZ(), sound, SoundSource.PLAYERS, volume, 1.0F);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitLocation) {
        if (this.isWrecked() || player.isPassenger()) {
            return super.interact(player, hand, hitLocation);
        }
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.isEmpty() && hand == InteractionHand.MAIN_HAND) {
            if (!this.level().isClientSide()) {
                this.packUp(player);
            }
            return this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        if (stack.is(ModItems.MORTAR_ROUND.get())) {
            if (!this.level().isClientSide()) {
                this.tryLoad(player, stack);
            }
            return this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        return super.interact(player, hand, hitLocation);
    }

    /** Takes the mortar down: the item goes back to the player, with any round still in the tube. */
    public void packUp(Player player) {
        if (!this.getPassengers().isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.jeg.vehicle.occupied"));
            return;
        }
        give(player, new ItemStack(ModItems.MORTAR.get()));
        if (this.roundInTube()) {
            give(player, new ItemStack(ModItems.MORTAR_ROUND.get()));
        }
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_IRON.value(),
                SoundSource.PLAYERS, 1.0F, 0.8F);
        this.discard();
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>("Vehicle", 0, state -> {
            if (this.ticksSinceLoad() < FIRE_CLIP_TICKS) {
                state.setAndContinue(RawAnimation.begin().thenPlay("animation.mortar.fire"));
            } else {
                state.setAndContinue(RawAnimation.begin().thenLoop("animation.mortar.rest"));
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putFloat("MortarElevation", this.elevation());
        output.putInt("MortarSetup", this.entityData.get(DATA_SETUP));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setElevation(input.getFloatOr("MortarElevation", 60.0F));
        this.elevationO = this.elevation();
        this.entityData.set(DATA_SETUP, input.getIntOr("MortarSetup", 0));
    }
}
