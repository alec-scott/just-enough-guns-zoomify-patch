package ttv.migami.jeg.vehicle.cabin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractCandleBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.LidBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import ttv.migami.jeg.vehicle.entity.ChinookEntity;

/**
 * What the blocks placed in a cabin do on clients, as placed ones do in the world (the user, 2026-11-25: "no furnace or
 * chest animations and lighting looks off"; 2026-11-26: every placeable block's light, animation and sound):
 * <ul>
 * <li>Each block's own look: vanilla's {@code animateTick} runs for it as often as for a placed block that far from the
 * player, and the particles and sounds it makes are put in the cabin (a torch's flame, a candle's, an end rod's
 * sparks, crying obsidian's tears, a spore blossom's spores, a furnace's crackle). The particles then move with the
 * cabin, as in a closed room's still air: given the aircraft's speed once, a flame or a puff of smoke slowed in the
 * world's air and streamed aft through the cabin at 30 m/s. {@link #mapping()} is read by the ClientLevel mixin while it
 * runs.</li>
 * <li>Lids and books: chests and ender chests lift their lids, shulker boxes open, while someone has them open (the
 * server says when, {@link CabinNetwork.LidPayload}); an enchanting table's book opens and turns to whoever stands near.</li>
 * <li>Light: every block aboard that gives light (its own block-light level, as placed) lights itself and the cells round
 * it, falling off a level a block; {@link #emitters} also wash the cabin's walls ({@code VehicleLightsLayer}).</li>
 * </ul>
 */
public final class CabinBlockEffects {
    /** Each block aboard that needs a block entity to be drawn (lids, a book), by vehicle id, then cell. */
    private static final Map<Integer, Map<BlockPos, BlockEntity>> ENTITIES = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();

    private CabinBlockEffects() {
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(CabinNetwork.LidPayload.TYPE, (payload, context) -> context.client().execute(() -> {
            BlockState state = CabinClient.grid(payload.id()).get(payload.cell());
            BlockEntity lid = state == null ? null : blockEntity(payload.id(), payload.cell(), state);
            if (lid != null) {
                lid.triggerEvent(1, payload.open() ? 1 : 0);
            }
        }));
        ClientTickEvents.END_CLIENT_TICK.register(CabinBlockEffects::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ENTITIES.clear();
            CARRIED.clear();
            POSES.clear();
        });
    }

    /** Whether a block aboard is drawn by a block entity's renderer (a chest's lid, a shulker box's, a book). */
    private static boolean animated(BlockState state) {
        return CabinMath.isChest(state) || state.getBlock() instanceof EnchantingTableBlock;
    }

    /** Whether the block's own model is drawn as well as its block entity (the enchanting table: the renderer adds the book). */
    public static boolean drawsModelToo(BlockState state) {
        return state.getBlock() instanceof EnchantingTableBlock;
    }

    /** The block entity that draws this block aboard (a lid, a book), or null for a block drawn by its model alone. */
    @Nullable
    public static BlockEntity blockEntity(int vehicleId, BlockPos cell, BlockState state) {
        if (!animated(state) || !(state.getBlock() instanceof EntityBlock block)) {
            return null;
        }
        Map<BlockPos, BlockEntity> entities = ENTITIES.computeIfAbsent(vehicleId, id -> new HashMap<>());
        BlockEntity entity = entities.get(cell);
        if (entity != null && entity.getBlockState().getBlock() == state.getBlock()) {
            if (entity.getBlockState() != state) {
                entity.setBlockState(state);
            }
            return entity;
        }
        entity = block.newBlockEntity(BlockPos.ZERO, state);
        if (!(entity instanceof LidBlockEntity || entity instanceof ShulkerBoxBlockEntity || entity instanceof EnchantingTableBlockEntity)) {
            return null;
        }
        entities.put(cell, entity);
        return entity;
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        carry(mc);
        ENTITIES.entrySet().removeIf(entry -> {
            if (!(mc.level.getEntity(entry.getKey()) instanceof ChinookEntity chinook)) {
                return true;
            }
            Map<BlockPos, BlockState> grid = CabinClient.grid(entry.getKey());
            entry.getValue().entrySet().removeIf(cell -> {
                BlockState state = grid.get(cell.getKey());
                if (state == null || state.getBlock() != cell.getValue().getBlockState().getBlock()) {
                    return true;
                }
                BlockEntity entity = cell.getValue();
                if (entity instanceof ChestBlockEntity chest) {
                    ChestBlockEntity.lidAnimateTick(mc.level, BlockPos.ZERO, state, chest);
                } else if (entity instanceof EnderChestBlockEntity ender) {
                    EnderChestBlockEntity.lidAnimateTick(mc.level, BlockPos.ZERO, state, ender);
                } else if (entity instanceof ShulkerBoxBlockEntity shulker) {
                    // Its lid's progress; given a spot far below the world, the neighbour updates and the push on
                    // entities in its way that come with it in the world touch nothing.
                    ShulkerBoxBlockEntity.tick(mc.level, new BlockPos(0, mc.level.getMinY() - 64, 0), state, shulker);
                } else if (entity instanceof EnchantingTableBlockEntity table) {
                    book(table, chinook, cell.getKey(), mc.player);
                }
                return false;
            });
            return entry.getValue().isEmpty();
        });
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ChinookEntity chinook && chinook.distanceToSqr(mc.player) < 48.0D * 48.0D) {
                animate(mc, chinook);
            }
        }
    }

    // ---- The blocks' own look: particles and sounds ----

    /** The cabin a block's animateTick is running for, while it runs (read by the ClientLevel mixin), or null. */
    @Nullable
    private static Mapping mapping;

    /**
     * Where a block aboard is while its animateTick runs: it is told it stands at {@code pos} (its cell's spot in the
     * world, so what it reads round it is what is round the aircraft there), and everything it puts at that block is put
     * at the drawn block in the cabin.
     */
    private record Mapping(int vehicleId, Matrix4d pose, BlockPos pos, Vec3 corner) {
        /** A point given relative to {@link #pos}, in the world: the block is drawn turned half round (render space). */
        Vec3 point(double x, double y, double z) {
            double px = x - this.pos.getX();
            double py = y - this.pos.getY();
            double pz = z - this.pos.getZ();
            return CabinMath.toWorld(this.pose, new Vec3(this.corner.x + 1.0D - px, this.corner.y + py, this.corner.z + 1.0D - pz));
        }

        /** A velocity (blocks a tick) given in the block's frame, in the world's axes (the cabin carries the rest). */
        Vec3 velocity(double x, double y, double z) {
            Vector3d v = this.pose.transformDirection(new Vector3d(-x, y, -z));
            return new Vec3(v.x, v.y, v.z);
        }
    }

    /** Whether a block aboard is being animated now: the ClientLevel mixin moves what it adds into the cabin. */
    public static boolean mapping() {
        return mapping != null;
    }

    /**
     * A particle a block aboard adds, put at the drawn block and carried with the cabin from then on. As the world's are,
     * it is left out with particles set to minimal, a third of them with decreased, and beyond 32 blocks.
     */
    public static void particle(ClientLevel level, ParticleOptions particle, boolean force, boolean decreased, boolean alwaysVisible,
                                double x, double y, double z, double vx, double vy, double vz) {
        Mapping m = mapping;
        Minecraft mc = Minecraft.getInstance();
        Vec3 at = m.point(x, y, z);
        if (!force && !alwaysVisible) {
            net.minecraft.server.level.ParticleStatus status = mc.options.particles().get();
            if (status == net.minecraft.server.level.ParticleStatus.MINIMAL
                    || (status == net.minecraft.server.level.ParticleStatus.DECREASED || decreased) && RANDOM.nextInt(3) == 0
                    || mc.gameRenderer.getMainCamera().position().distanceToSqr(at) > 32.0D * 32.0D) {
                return;
            }
        }
        Vec3 v = m.velocity(vx, vy, vz);
        Particle made = mc.particleEngine.createParticle(particle, at.x, at.y, at.z, v.x, v.y, v.z);
        if (made != null) {
            CARRIED.add(new Carried(made, m.vehicleId()));
        }
    }

    /** The particles blocks aboard have made, and the cabin each moves with. */
    private record Carried(Particle particle, int vehicleId) {
    }

    private static final List<Carried> CARRIED = new ArrayList<>();
    /** Each cabin's pose last tick, to carry its particles by how it has moved since. */
    private static final Map<Integer, Matrix4d> POSES = new HashMap<>();

    /**
     * Each tick, after the particles have moved: every particle a block aboard made moves as its cabin did since last
     * tick (where it was drawn last frame stays, so it is drawn sliding with the cabin between ticks).
     */
    private static void carry(Minecraft mc) {
        Map<Integer, Matrix4d> now = new HashMap<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ChinookEntity chinook && chinook.distanceToSqr(mc.player) < 64.0D * 64.0D) {
                now.put(chinook.getId(), chinook.localToWorld(1.0F));
            }
        }
        CARRIED.removeIf(carried -> {
            Particle particle = carried.particle();
            Matrix4d before = POSES.get(carried.vehicleId());
            Matrix4d after = now.get(carried.vehicleId());
            if (!particle.isAlive() || after == null) {
                return true;
            }
            if (before != null) {
                net.minecraft.world.phys.AABB box = particle.getBoundingBox();
                Vector3d at = new Vector3d((box.minX + box.maxX) * 0.5D, box.minY, (box.minZ + box.maxZ) * 0.5D);
                new Matrix4d(after).mul(new Matrix4d(before).invert()).transformPosition(at);
                particle.setPos(at.x, at.y, at.z);
            }
            return false;
        });
        POSES.clear();
        POSES.putAll(now);
    }

    /** A sound a block aboard plays, at the drawn block. */
    public static void sound(ClientLevel level, double x, double y, double z, SoundEvent sound, SoundSource source, float volume, float pitch,
                             boolean distanceDelay) {
        Mapping m = mapping;
        mapping = null;
        try {
            Vec3 at = m.point(x, y, z);
            level.playLocalSound(at.x, at.y, at.z, sound, source, volume, pitch, distanceDelay);
        } finally {
            mapping = m;
        }
    }

    /** A sound a block aboard plays for a player, at the drawn block. */
    public static void seededSound(ClientLevel level, @Nullable Entity except, double x, double y, double z, Holder<SoundEvent> sound,
                                   SoundSource source, float volume, float pitch, long seed) {
        Mapping m = mapping;
        mapping = null;
        try {
            Vec3 at = m.point(x, y, z);
            level.playSeededSound(except, at.x, at.y, at.z, sound, source, volume, pitch, seed);
        } finally {
            mapping = m;
        }
    }

    /**
     * Each block aboard runs its animateTick as often as a placed block as far from the player does: vanilla picks 667
     * spots a tick round the player within 16 blocks and 667 within 32, each axis's offset the difference of two
     * random numbers, so a block beside the player is picked about once in five ticks and one 16 blocks off far less.
     */
    private static void animate(Minecraft mc, ChinookEntity chinook) {
        Map<BlockPos, BlockState> grid = CabinClient.grid(chinook.getId());
        if (grid.isEmpty()) {
            return;
        }
        Matrix4d pose = chinook.localToWorld(1.0F);
        BlockPos player = mc.player.blockPosition();
        for (Map.Entry<BlockPos, BlockState> entry : List.copyOf(grid.entrySet())) {
            Vec3 corner = ChinookEntity.LAYOUT.cellCorner(entry.getKey());
            Vec3 centre = CabinMath.toWorld(pose, corner.add(0.5D, 0.5D, 0.5D));
            BlockPos pos = BlockPos.containing(centre);
            int dx = Math.abs(pos.getX() - player.getX());
            int dy = Math.abs(pos.getY() - player.getY());
            int dz = Math.abs(pos.getZ() - player.getZ());
            float chance = 667.0F * (picked(dx, 16) * picked(dy, 16) * picked(dz, 16) + picked(dx, 32) * picked(dy, 32) * picked(dz, 32));
            if (RANDOM.nextFloat() >= chance) {
                continue;
            }
            mapping = new Mapping(chinook.getId(), pose, pos, corner);
            try {
                entry.getValue().getBlock().animateTick(entry.getValue(), mc.level, pos, RANDOM);
            } catch (RuntimeException e) {
                // A block whose look reads more of the world than the aircraft has round it: it goes without.
            } finally {
                mapping = null;
            }
        }
    }

    /** How likely vanilla's pick lands at this offset on one axis: nextInt(r) - nextInt(r) == d. */
    private static float picked(int d, int r) {
        return d >= r ? 0.0F : (r - d) / (float) (r * r);
    }

    /**
     * An enchanting table's book, as vanilla's bookAnimationTick has it but in the cabin's frame: it opens when someone
     * is within 3 blocks and turns to them, else it closes and idles round. Turned in the world's frame, the book drawn in
     * the turning cabin would face wherever the aircraft's heading put it.
     */
    private static void book(EnchantingTableBlockEntity table, ChinookEntity chinook, BlockPos cell, Player player) {
        table.oOpen = table.open;
        table.oRot = table.rot;
        Vec3 corner = ChinookEntity.LAYOUT.cellCorner(cell);
        Vec3 local = CabinMath.toLocal(chinook.localToWorld(1.0F), player.position());
        // In the block's drawn frame (the data frame turned half round), from the table's middle.
        double dx = -(local.x - (corner.x + 0.5D));
        double dz = -(local.z - (corner.z + 0.5D));
        double dy = local.y - (corner.y + 0.5D);
        if (dx * dx + dy * dy + dz * dz < 9.0D) {
            table.tRot = (float) Mth.atan2(dz, dx);
            table.open += 0.1F;
            if (table.open < 0.5F || RANDOM.nextInt(40) == 0) {
                float before = table.flipT;
                do {
                    table.flipT += RANDOM.nextInt(4) - RANDOM.nextInt(4);
                } while (before == table.flipT);
            }
        } else {
            table.tRot += 0.02F;
            table.open -= 0.1F;
        }
        table.rot = Mth.wrapDegrees(table.rot * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        table.tRot = Mth.wrapDegrees(table.tRot * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        float turn = Mth.wrapDegrees((table.tRot - table.rot) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        table.rot += turn * 0.4F;
        table.open = Mth.clamp(table.open, 0.0F, 1.0F);
        table.time++;
        table.oFlip = table.flip;
        float flip = Mth.clamp((table.flipT - table.flip) * 0.4F, -0.2F, 0.2F);
        table.flipA += (flip - table.flipA) * 0.9F;
        table.flip += table.flipA;
    }

    // ---- Light ----

    /** The block light a block gives as placed: a lit furnace 13, a torch 14, glowstone 15, a lit candle 3 a candle. */
    public static int emission(BlockState state) {
        return state.getLightEmission();
    }

    /**
     * The light a block aboard is drawn with: the cabin's own (the aircraft's light, which the walls round it have), and
     * as block light the dome lamps' at night and the light of every block aboard that gives it, a level less a block
     * away (a block that gives light is lit by its own). A block that glows as it is drawn (magma) is drawn full bright,
     * as placed. The furnace was drawn by the world's light at the cabin (full daylight at midnight under a shader pack)
     * and the chests by the aircraft's alone, so they stood black beside the lamp-lit walls; lamps placed aboard lit
     * nothing (2026-11-26).
     *
     * @param lamps the dome lamps' positions and reaches in the model's frame (blocks), and how far on they are
     */
    public static int light(int hullLight, Map<BlockPos, BlockState> grid, BlockPos cell, Vec3 drawnCentre, float[][] lamps, float lampLevel) {
        BlockState self = grid.get(cell);
        if (self != null && self.emissiveRendering(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            return LightCoordsUtil.FULL_BRIGHT;
        }
        int block = LightCoordsUtil.block(hullLight);
        if (lampLevel > 0.0F) {
            for (float[] lamp : lamps) {
                double d = drawnCentre.distanceTo(new Vec3(lamp[0], lamp[1], lamp[2]));
                float reach = 1.0F - (float) (d / lamp[3]);
                if (reach > 0.0F) {
                    block = Math.max(block, Math.round(LAMP_LIGHT * lampLevel * reach));
                }
            }
        }
        for (Map.Entry<BlockPos, BlockState> other : grid.entrySet()) {
            int emission = emission(other.getValue());
            if (emission > block) {
                block = Math.max(block, emission - other.getKey().distManhattan(cell));
            }
        }
        return LightCoordsUtil.pack(Mth.clamp(block, 0, 15), LightCoordsUtil.sky(hullLight));
    }

    /** A dome lamp's light at its lens, as block light. */
    private static final int LAMP_LIGHT = 13;

    /** The blocks aboard that give light, for the light they cast on the cabin round them (cells). */
    public static List<BlockPos> emitters(Map<BlockPos, BlockState> grid) {
        List<BlockPos> cells = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : grid.entrySet()) {
            if (emission(entry.getValue()) > 0) {
                cells.add(entry.getKey());
            }
        }
        return cells;
    }

    /** Whether a block's light is a flame's, which flickers (torches, lanterns, candles, furnaces, a jack o'lantern). */
    public static boolean flame(BlockState state) {
        return state.getBlock() instanceof BaseTorchBlock || state.getBlock() instanceof LanternBlock
                || state.getBlock() instanceof AbstractCandleBlock || state.getBlock() instanceof AbstractFurnaceBlock
                || state.getBlock() instanceof CarvedPumpkinBlock;
    }

    /** Whether a furnace (its light comes from its mouth, not its middle). */
    public static boolean furnace(BlockState state) {
        return state.getBlock() instanceof AbstractFurnaceBlock && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING);
    }

    /**
     * The colour a block's light washes the cabin with: soul fire blue, the sea's lamps and end rods cool white, crying
     * obsidian, amethyst and the respawn anchor violet, froglights their own, fire and lamps warm.
     */
    public static float[] colour(BlockState state) {
        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (id.contains("soul")) {
            return new float[]{0.35F, 0.8F, 1.0F};
        }
        if (id.contains("sea_lantern") || id.contains("end_rod") || id.contains("sea_pickle")) {
            return new float[]{0.8F, 0.92F, 1.0F};
        }
        if (id.contains("crying_obsidian") || id.contains("amethyst") || id.contains("respawn_anchor") || id.contains("ender_chest")
                || id.contains("enchanting_table")) {
            return new float[]{0.62F, 0.35F, 1.0F};
        }
        if (id.equals("verdant_froglight")) {
            return new float[]{0.6F, 1.0F, 0.55F};
        }
        if (id.equals("pearlescent_froglight")) {
            return new float[]{1.0F, 0.7F, 0.95F};
        }
        if (id.contains("copper")) {
            return new float[]{1.0F, 0.72F, 0.5F};
        }
        if (id.contains("glowstone") || id.contains("shroomlight") || id.contains("lamp") || id.contains("froglight")) {
            return new float[]{1.0F, 0.78F, 0.45F};
        }
        return new float[]{1.0F, 0.56F, 0.22F};
    }
}
