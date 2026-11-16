package ttv.migami.jeg.item;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * A crew weapon carried as an item (the mortar, the TOW): right-click the ground to set it up facing where you look.
 * The weapon itself packs up again on a sneak-click with an empty hand.
 */
public class DeployableWeaponItem extends Item {
    /** A deployed weapon that needs time to set up (the mortar's baseplate and bipod). */
    public interface SetsUp {
        void startSetup();
    }

    private final Supplier<? extends EntityType<? extends VehicleEntity>> type;

    public DeployableWeaponItem(Properties properties, Supplier<? extends EntityType<? extends VehicleEntity>> type) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos clicked = context.getClickedPos();
        Vec3 at;
        if (context.getClickedFace() == Direction.UP && !level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()) {
            at = context.getClickLocation();
        } else {
            BlockPos above = clicked.relative(context.getClickedFace());
            at = Vec3.atBottomCenterOf(above);
        }
        EntityType<? extends VehicleEntity> entityType = this.type.get();
        if (!level.noCollision(null, entityType.getSpawnAABB(at.x, at.y, at.z))) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || player != null && !level.mayInteract(player, clicked)) {
            return InteractionResult.FAIL;
        }
        VehicleEntity weapon = entityType.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
        if (weapon == null) {
            return InteractionResult.FAIL;
        }
        weapon.setPos(at);
        float yaw = player == null ? 0.0F : player.getYRot();
        weapon.setYRot(yaw);
        weapon.yRotO = yaw;
        if (weapon instanceof SetsUp setsUp) {
            setsUp.startSetup();
        }
        serverLevel.addFreshEntity(weapon);
        serverLevel.gameEvent(player, GameEvent.ENTITY_PLACE, at);
        serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, 0.9F);
        serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.25F, 1.6F);
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS_SERVER;
    }
}
