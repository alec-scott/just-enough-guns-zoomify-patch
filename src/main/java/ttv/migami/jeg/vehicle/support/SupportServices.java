package ttv.migami.jeg.vehicle.support;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.data.subdata.VehicleWeaponInfo;
import ttv.migami.jeg.vehicle.entity.SupportVehicleEntity;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.SupportRig;

/**
 * The support vehicle's services to the camp, all while deployed:
 * <ul>
 *     <li><b>Charging station</b> (fitted in a bay): recharges vehicles within 8 blocks from the FSV-6's battery and
 *     rearms them, moving the ammunition their weapons use from the FSV-6's hold into theirs.</li>
 *     <li><b>Anvil</b>: repairs vehicles within 8 blocks, an iron ingot from the hold for each 10 points.</li>
 *     <li><b>Beacon</b> (roof mount): set at the beacon terminal in the room ({@code SupportBeaconMenu}). Its level is
 *     set by the power rack; it works with the light mast raised, and reaches 10 + 10 blocks a level as a beacon does.</li>
 *     <li><b>Jukebox</b>: use it holding a disc to play it, empty-handed to take the disc out again.</li>
 * </ul>
 */
public final class SupportServices {
    private static final double SERVICE_RANGE = 8;
    private static final int CHARGE_PER_SECOND = 4000;
    private static final int AMMO_PER_SECOND = 32;
    private static final float REPAIR_PER_INGOT = 10;

    private SupportServices() {}

    /** Beacon effects chosen in its screen (vanilla's encoding: registry id + 1, 0 for none) and the jukebox's disc. */
    public static final class State {
        int primary, secondary;
        final SimpleContainer payment = new SimpleContainer(1);

        /** The chosen primary effect (vanilla's encoding: registry id + 1, 0 for none). */
        public int primary() {
            return this.primary;
        }

        /** The chosen secondary effect (level 4), in the same encoding. */
        public int secondary() {
            return this.secondary;
        }
        final SimpleContainer[] discs = new SimpleContainer[SupportModules.WALL_BAYS];

        State() {
            for (int i = 0; i < this.discs.length; i++) this.discs[i] = new SimpleContainer(1);
        }

        void save(ValueOutput out) {
            out.putInt("BeaconPrimary", this.primary);
            out.putInt("BeaconSecondary", this.secondary);
            for (int i = 0; i < this.discs.length; i++) {
                if (!this.discs[i].isEmpty()) out.store("Disc" + i, ItemStack.CODEC, this.discs[i].getItem(0));
            }
        }

        void load(ValueInput in) {
            this.primary = in.getIntOr("BeaconPrimary", 0);
            this.secondary = in.getIntOr("BeaconSecondary", 0);
            for (int i = 0; i < this.discs.length; i++) {
                final int slot = i;
                in.read("Disc" + i, ItemStack.CODEC).ifPresent(stack -> this.discs[slot].setItem(0, stack));
            }
        }
    }

    /** Use a module with no screen of its own. */
    public static boolean use(SupportVehicleEntity vehicle, ServerPlayer player, int slot, SupportModules.Kind kind) {
        SupportModules modules = vehicle.modules();
        State state = modules.services();
        switch (kind) {
            case BEACON -> {
                vehicle.openBeaconMenu(player);
                return true;
            }
            case JUKEBOX -> {
                if (!vehicle.isDeployed()) {
                    player.sendOverlayMessage(Component.translatable("message.jeg.support.deploy_first"));
                    return true;
                }
                BlockPos at = bayPos(vehicle, slot);
                SimpleContainer disc = state.discs[slot];
                ItemStack held = player.getMainHandItem();
                if (!disc.isEmpty()) {
                    player.level().levelEvent(1011, at, 0);
                    if (!player.getInventory().add(disc.getItem(0))) player.drop(disc.getItem(0), false);
                    disc.clearContent();
                } else if (JukeboxSong.fromStack(held).isPresent()) {
                    Holder<JukeboxSong> song = JukeboxSong.fromStack(held).get();
                    disc.setItem(0, held.split(1));
                    int id = player.level().registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG).getId(song.value());
                    player.level().levelEvent(null, 1010, at, id);
                } else {
                    player.sendOverlayMessage(Component.translatable("message.jeg.support.jukebox_empty"));
                }
                return true;
            }
            case CHARGING, ANVIL -> {
                int count = targets(vehicle).size();
                player.sendOverlayMessage(Component.translatable(kind == SupportModules.Kind.CHARGING
                        ? "message.jeg.support.charging" : "message.jeg.support.repairing", count));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static BlockPos bayPos(SupportVehicleEntity vehicle, int bay) {
        int column = bay < 3 ? 3 : 0, row = 2 + bay % 3;
        return SupportShell.cellPos(vehicle.position(), vehicle.getYRot(), new SupportRig.ShellCell(column, row, 0, SupportRig.Cell.BAY, 0));
    }

    private static java.util.List<VehicleEntity> targets(SupportVehicleEntity vehicle) {
        return vehicle.level().getEntitiesOfClass(VehicleEntity.class, vehicle.getBoundingBox().inflate(SERVICE_RANGE),
                other -> other != vehicle && !other.isWrecked() && !other.isRemoved());
    }

    /** Once a second while deployed: charge, rearm and repair the vehicles round the camp; every 4 s the beacon. */
    public static void tick(SupportVehicleEntity vehicle, ServerLevel level) {
        if (!vehicle.isDeployed() || vehicle.isWrecked()) return;
        SupportModules modules = vehicle.modules();
        boolean charging = false, repairing = false;
        for (int bay = 0; bay < SupportModules.WALL_BAYS; bay++) {
            charging |= modules.kind(bay) == SupportModules.Kind.CHARGING;
            repairing |= modules.kind(bay) == SupportModules.Kind.ANVIL;
        }
        if (vehicle.tickCount % 20 == 0 && (charging || repairing)) {
            for (VehicleEntity other : targets(vehicle)) {
                if (charging) {
                    int want = Math.min(CHARGE_PER_SECOND, other.maxVehicleEnergy() - other.vehicleEnergy());
                    int give = Math.min(want, vehicle.vehicleEnergy());
                    if (give > 0 && vehicle.consumeEnergy(give)) other.addEnergy(give);
                    rearm(vehicle, other);
                }
                if (repairing && other.vehicleHealth() < other.maxVehicleHealth() && takeIngot(vehicle)) {
                    other.repairWithTool(REPAIR_PER_INGOT, REPAIR_PER_INGOT * .5F);
                }
            }
        }
        if (vehicle.tickCount % 80 == 0 && !modules.fitted(SupportModules.ROOF).isEmpty()) {
            applyBeacon(vehicle, level, modules);
        }
    }

    /** Move the ammunition each of the other vehicle's weapons uses from this hold into its hold. */
    private static void rearm(SupportVehicleEntity vehicle, VehicleEntity other) {
        SimpleContainer from = vehicle.vehicleInventory(), to = other.vehicleInventory();
        int budget = AMMO_PER_SECOND;
        for (VehicleWeaponInfo weapon : other.vehicleData().defaults().weapons()) {
            var ammo = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(weapon.ammoId()).orElse(null);
            if (ammo == null || ammo == Items.AIR) continue;
            for (int i = 0; i < from.getContainerSize() && budget > 0; i++) {
                ItemStack stack = from.getItem(i);
                if (!stack.is(ammo)) continue;
                ItemStack moving = stack.copyWithCount(Math.min(budget, stack.getCount()));
                ItemStack left = to.addItem(moving);
                int moved = moving.getCount() - left.getCount();
                if (moved <= 0) break;
                stack.shrink(moved);
                budget -= moved;
            }
        }
        if (budget < AMMO_PER_SECOND) from.setChanged();
    }

    private static boolean takeIngot(SupportVehicleEntity vehicle) {
        SimpleContainer hold = vehicle.vehicleInventory();
        for (int i = 0; i < hold.getContainerSize(); i++) {
            if (hold.getItem(i).is(Items.IRON_INGOT)) {
                hold.getItem(i).shrink(1);
                hold.setChanged();
                return true;
            }
        }
        return false;
    }

    /** As a beacon does: the primary effect (amplified at level 4 if chosen twice) and regeneration as the secondary. */
    private static void applyBeacon(SupportVehicleEntity vehicle, ServerLevel level, SupportModules modules) {
        int levels = modules.beaconLevel();
        State state = modules.services();
        if (levels <= 0 || state.primary <= 0 || SupportRig.mast(vehicle.deployProgress()) < 1) return;
        Holder<MobEffect> primary = BeaconMenu.decodeEffect(state.primary);
        Holder<MobEffect> secondary = state.secondary > 0 ? BeaconMenu.decodeEffect(state.secondary) : null;
        // Taking blocks off the rack can drop the level below the chosen effect's tier: then it gives nothing, as a beacon.
        if (primary == null || ttv.migami.jeg.vehicle.menu.SupportBeaconMenu.tierOf(primary) >= levels) return;
        double range = levels * 10 + 10;
        int duration = (9 + levels * 2) * 20;
        int amplifier = levels >= 4 && primary.equals(secondary) ? 1 : 0;
        Vec3 at = SupportRig.world(vehicle, 0, SupportRig.ROOF_TOP, 24);
        AABB box = new AABB(at, at).inflate(range).expandTowards(0, level.getHeight(), 0);
        for (Player player : level.getEntitiesOfClass(Player.class, box)) {
            player.addEffect(new MobEffectInstance(primary, duration, amplifier, true, true));
            if (levels >= 4 && secondary != null && !primary.equals(secondary)) {
                player.addEffect(new MobEffectInstance(secondary, duration, 0, true, true));
            }
        }
    }

    /** The beacon is lit (for its beam): fitted, deployed, a level and an effect chosen. Server side. */
    public static boolean beaconLit(SupportVehicleEntity vehicle) {
        SupportModules modules = vehicle.modules();
        Holder<MobEffect> primary = modules.services().primary > 0 ? BeaconMenu.decodeEffect(modules.services().primary) : null;
        return vehicle.isDeployed() && !modules.fitted(SupportModules.ROOF).isEmpty() && primary != null
                && ttv.migami.jeg.vehicle.menu.SupportBeaconMenu.tierOf(primary) < modules.beaconLevel();
    }
}
