package ttv.migami.jeg.vehicle.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

/**
 * The TOW launcher (Superb Warfare's, see design/tow/BRIEF.md): a crew-carried tripod set up from the hand. The gunner
 * sits behind it and looks through the sight; the shared vehicle-gun path aims the tube with the view, fires the
 * missile from the hold and reloads it. The missile is wire guided: it flies down the gunner's sight line for as long
 * as the gunner stays at the sight, sees it, and the wire (1,000 blocks) lasts (VehicleMissileEntity.LineOfSightLauncher).
 * Sneak-click with an empty hand packs it up, with the missiles in its crate.
 */
public final class TowEntity extends VehicleEntity implements VehicleMissileEntity.LineOfSightLauncher {
    public static final String ID = "tow";
    public static final net.minecraft.resources.Identifier MISSILE_WEAPON = Reference.id("vehicle_tow_missile");
    /** The trunnion, hull coordinates at rest (design/tow/rig.json): the sight line runs from here along the tube. */
    public static final Vec3 SIGHT_REST = new Vec3(0.0D, 1.1748D, 0.0D);
    /** How far the wire pays out. The real TOW's runs 3.75 km; SBW's missile flies about 1,600 blocks in all. */
    public static final double WIRE_LENGTH = 1000.0D;

    public TowEntity(EntityType<? extends VehicleEntity> type, Level level) {
        super(type, level);
        this.setVehicleData(Reference.id(ID));
    }

    /** Its boxes are fitted to the model and follow the traverse and the tube (design/tow/rig.json). */
    @Override
    public boolean preciseHitbox() {
        return true;
    }

    @Override
    protected void onMissileLaunched(VehicleMissileEntity missile, LivingEntity shooter, VehicleWeaponInfo weapon) {
        if (MISSILE_WEAPON.equals(weapon.weaponId())) {
            missile.setLineOfSightGuided(this);
        }
    }

    @Override
    public Vec3 sightOrigin(float partialTick) {
        return this.barrelPoint(SIGHT_REST, partialTick);
    }

    @Override
    public Vec3 sightDirection(float partialTick) {
        return this.barrelDirection(partialTick);
    }

    @Override
    public boolean gunnerAtSight(Entity owner) {
        return owner.getVehicle() == this && !this.isWrecked();
    }

    @Override
    public double wireLength() {
        return WIRE_LENGTH;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitLocation) {
        if (!this.isWrecked() && !player.isPassenger() && player.isShiftKeyDown() && player.getItemInHand(hand).isEmpty()) {
            if (!this.level().isClientSide()) {
                this.packUp(player);
            }
            return this.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        }
        return super.interact(player, hand, hitLocation);
    }

    /** Takes the launcher down: the item goes back to the player, with every missile in its crate. */
    public void packUp(Player player) {
        if (!this.getPassengers().isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.jeg.vehicle.occupied"));
            return;
        }
        give(player, new ItemStack(ModItems.TOW.get()));
        // The missile in the tube, if one is loaded, and the crate.
        int loaded = this.vehicleWeaponSlotAmmo(0);
        if (loaded > 0) {
            give(player, new ItemStack(ModItems.AMMO.get(Reference.id("medium_anti_ground_missile")).get(), loaded));
        }
        var hold = this.vehicleInventory();
        for (int slot = 0; slot < hold.getContainerSize(); slot++) {
            ItemStack stack = hold.removeItemNoUpdate(slot);
            if (!stack.isEmpty()) {
                give(player, stack);
            }
        }
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
        this.discard();
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
