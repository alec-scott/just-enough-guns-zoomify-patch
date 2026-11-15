package ttv.migami.jeg.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.vehicle.entity.MortarEntity;

/**
 * The 81 mm mortar, carried: right-click the ground to set it up facing where you look. It takes two seconds to set
 * the baseplate and lay the bipod before it can fire; sneak-clicking it with an empty hand packs it up again.
 */
public class MortarItem extends Item {
    public MortarItem(Properties properties) {
        super(properties);
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
        var type = ModEntities.MORTAR.get();
        if (!level.noCollision(null, type.getSpawnAABB(at.x, at.y, at.z))) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || player != null && !level.mayInteract(player, clicked)) {
            return InteractionResult.FAIL;
        }
        MortarEntity mortar = type.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
        if (mortar == null) {
            return InteractionResult.FAIL;
        }
        mortar.setPos(at);
        float yaw = player == null ? 0.0F : player.getYRot();
        mortar.setYRot(yaw);
        mortar.yRotO = yaw;
        mortar.startSetup();
        serverLevel.addFreshEntity(mortar);
        serverLevel.gameEvent(player, GameEvent.ENTITY_PLACE, at);
        serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 1.0F, 0.9F);
        serverLevel.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.25F, 1.6F);
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS_SERVER;
    }
}
