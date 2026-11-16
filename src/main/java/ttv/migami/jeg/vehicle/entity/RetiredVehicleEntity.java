package ttv.migami.jeg.vehicle.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * A vehicle type kept registered for one release after it was retired (the waveforce tower, replaced by the 105 mm
 * howitzer), so saved worlds lose nothing: on its first tick on the server it leaves its hold behind as a chest item
 * holding what it carried (the overflow dropped loose), and is gone.
 */
public final class RetiredVehicleEntity extends VehicleEntity {
    public RetiredVehicleEntity(EntityType<? extends VehicleEntity> type, Level level, Identifier vehicleDataId) {
        super(type, level);
        this.setVehicleData(vehicleDataId);
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
            this.ejectPassengers();
            var hold = this.vehicleInventory();
            java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
            for (int slot = 0; slot < hold.getContainerSize(); slot++) {
                ItemStack stack = hold.removeItemNoUpdate(slot);
                if (!stack.isEmpty()) {
                    stacks.add(stack);
                }
            }
            BlockPos at = this.blockPosition();
            if (!stacks.isEmpty()) {
                // A chest holds 27 stacks; anything past that drops beside it.
                ItemStack chest = new ItemStack(Items.CHEST);
                chest.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stacks.subList(0, Math.min(27, stacks.size()))));
                Containers.dropItemStack(level, at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D, chest);
                for (ItemStack rest : stacks.subList(Math.min(27, stacks.size()), stacks.size())) {
                    Containers.dropItemStack(level, at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D, rest);
                }
            }
            this.discard();
            return;
        }
        super.tick();
    }
}
