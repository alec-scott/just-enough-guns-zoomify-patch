package ttv.migami.jeg.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModEffects;
import ttv.migami.jeg.init.ModEntities;
import ttv.migami.jeg.init.ModItems;
import ttv.migami.jeg.init.ModSounds;

public final class SmokeGrenadeEntity extends TimedThrowableItemProjectile {
    private static final float CLOUD_RADIUS = 3.5F;
    private static final int CLOUD_DURATION = 200;
    private static final double[] CLOUD_OFFSETS = {-0.5D, 0.5D, 1.5D, 2.5D};

    public SmokeGrenadeEntity(net.minecraft.world.entity.EntityType<? extends SmokeGrenadeEntity> type, Level level) {
        super(type, level);
        this.setItem(ModItems.AMMO.get(Reference.id("smoke_grenade")).get().getDefaultInstance());
    }

    public SmokeGrenadeEntity(Level level, LivingEntity owner, int fuseTicks) {
        this(ModEntities.SMOKE_GRENADE.get(), level);
        this.setOwner(owner);
        this.setFuse(fuseTicks);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.AMMO.get(Reference.id("smoke_grenade")).get();
    }

    @Override
    protected void spawnFlightParticles() {
        this.level().addParticle(
                ParticleTypes.CAMPFIRE_COSY_SMOKE,
                this.getX(),
                this.getY() + 0.2D,
                this.getZ(),
                (this.random.nextDouble() - 0.5D) * 0.08D,
                0.08D,
                (this.random.nextDouble() - 0.5D) * 0.08D
        );
    }

    @Override
    protected void explode() {
        ServerLevel serverLevel = (ServerLevel) this.level();
        SoundEvent sound = resolveSound("entity.smoke_grenade.explosion", SoundEvents.FIRE_EXTINGUISH);
        serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.BLOCKS, 2.0F, 1.0F);
        smokeScreen(serverLevel, this.position(), 1);
    }

    /**
     * A smoke screen at {@code at}: the grenade's cloud, {@code width} times as wide (a 105 mm smoke shell lays one three
     * times as wide). Drawn by each client as one billowing cloud of real-looking smoke (SmokeScreenPayload), no longer
     * vanilla's campfire puffs (the user's review, R17); the clouds under it carry the smoke's effect. Puts out fires.
     */
    public static void smokeScreen(ServerLevel serverLevel, net.minecraft.world.phys.Vec3 at, int width) {
        double x = at.x, y = at.y, z = at.z;
        // A shell's screen is watched from far off (the observer's spyglass): sent out to 512 blocks.
        boolean far = width > 1;
        ttv.migami.jeg.network.SmokeScreenPayload.send(serverLevel, at, CLOUD_RADIUS * width, CLOUD_DURATION + 60 * width,
                far ? 512.0D : 192.0D);
        for (double offset : CLOUD_OFFSETS) {
            AreaEffectCloud cloud = new AreaEffectCloud(serverLevel, x, y + offset, z);
            cloud.setRadius(CLOUD_RADIUS * width);
            cloud.setDuration(CLOUD_DURATION);
            cloud.setWaitTime(0);
            cloud.setRadiusPerTick(0.0F);
            // The cloud's own particles would be the effect's coloured swirls: a little ash drifting in the smoke instead.
            cloud.setCustomParticle(ParticleTypes.WHITE_ASH);
            cloud.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ModEffects.SMOKED.get()), 40, 0, false, false, true));
            serverLevel.addFreshEntity(cloud);
        }
        extinguishNearbyFire(serverLevel, BlockPos.containing(at), 4 * width);
    }

    private static void extinguishNearbyFire(ServerLevel level, BlockPos center, int radius) {
        boolean extinguished = false;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -2, -radius), center.offset(radius, 2, radius))) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BaseFireBlock) {
                level.removeBlock(pos, false);
                extinguished = true;
                continue;
            }
            if (CampfireBlock.isLitCampfire(state)) {
                CampfireBlock.dowse(null, level, pos, state);
                level.setBlock(pos, state.setValue(CampfireBlock.LIT, Boolean.FALSE), 11);
                extinguished = true;
                continue;
            }
            if (AbstractCandleBlock.isLit(state)) {
                AbstractCandleBlock.extinguish(null, state, level, pos);
                extinguished = true;
            }
        }

        if (extinguished) {
            level.playSound(null, center, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8F, 1.0F);
        }
    }

    private static SoundEvent resolveSound(String path, SoundEvent fallback) {
        var holder = ModSounds.ALL.get(Reference.id(path));
        return holder != null ? holder.get() : fallback;
    }
}
