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
     * A smoke screen at {@code at}: the grenade's clouds and burst, {@code width} times as wide (a 105 mm smoke shell
     * lays one three times as wide). Puts out fires under it.
     */
    public static void smokeScreen(ServerLevel serverLevel, net.minecraft.world.phys.Vec3 at, int width) {
        double x = at.x, y = at.y, z = at.z;
        // A shell's screen is watched from far off (the observer's spyglass): sent out to 512 blocks, not 32.
        boolean far = width > 1;
        serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, far, far, x, y + 0.5D, z, 60 * width, 0.8D * width, 1.2D, 0.8D * width, 0.02D);
        serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, far, far, x, y + 0.5D, z, 20 * width, 0.6D * width, 0.8D, 0.6D * width, 0.01D);
        for (double offset : CLOUD_OFFSETS) {
            AreaEffectCloud cloud = new AreaEffectCloud(serverLevel, x, y + offset, z);
            cloud.setRadius(CLOUD_RADIUS * width);
            cloud.setDuration(CLOUD_DURATION);
            cloud.setWaitTime(0);
            cloud.setRadiusPerTick(0.0F);
            cloud.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(ModEffects.SMOKED.get()), 40, 0, false, false, true));
            serverLevel.addFreshEntity(cloud);
        }
        extinguishNearbyFire(serverLevel, BlockPos.containing(at), 4 * width);
        if (far) {
            // The clouds (and their particles) are only seen within entity range, about 160 blocks: a shell's screen
            // keeps sending smoke to 512 blocks for as long as the clouds last, so the observer sees it hold.
            SCREENS.add(new Screen(serverLevel, at, width, new int[] {CLOUD_DURATION}));
        }
    }

    private record Screen(ServerLevel level, net.minecraft.world.phys.Vec3 at, int width, int[] ticksLeft) {}

    private static final java.util.List<Screen> SCREENS = new java.util.ArrayList<>();

    /** Keeps the far-seen smoke of the shells' screens going (FabricEntrypoint, every server tick). */
    public static void tickScreens(net.minecraft.server.MinecraftServer server) {
        SCREENS.removeIf(screen -> {
            int left = --screen.ticksLeft()[0];
            if (left % 4 == 0) {
                // Big, long-lived grey puffs (ModParticleTypes.SMOKE_SCREEN): campfire smoke is a block across, a speck at 200 blocks.
                double spread = 2.6D * screen.width();
                screen.level().sendParticles(ttv.migami.jeg.init.ModParticleTypes.SMOKE_SCREEN.get(), true, true, screen.at().x,
                        screen.at().y + 1.5D, screen.at().z, 2 * screen.width(), spread, 0.9D, spread, 0.005D);
            }
            return left <= 0;
        });
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
