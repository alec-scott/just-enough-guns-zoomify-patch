package ttv.migami.jeg.vehicle.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Lights a vehicle's model vertex by vertex. Minecraft draws an entity with one light value, read at its origin, so a
 * vehicle half in a hangar was lit wholly as daylight, the half under the roof as bright as the cab in the sun, or wholly
 * dark with only its origin under cover. Shader packs make it plain: Photon's interiors are far darker than vanilla's
 * (played 2026-11-26, the FSV-6 nosed into a stone hangar).
 *
 * <p>Each vertex takes the sky light of the block it is in; a vertex inside a solid block (a wheel sunk into the ground, a
 * hull through a wall) takes the brightest of the open blocks round it. Block light keeps the vehicle's own (the
 * renderer's: the FSV-6 room lamp, LambDynamicLights at the origin) unless the vertex's block is brighter, so lamps
 * still light the whole vehicle as before.
 *
 * <p>GeckoLib transforms each vertex by the pose before handing it on, and an entity's pose is relative to the camera, so
 * a vertex's world position is the camera's position plus the vertex's.
 */
final class VehicleVertexLight implements VertexConsumer {
    private final VertexConsumer delegate;
    private final Level level;
    private final double cameraX;
    private final double cameraY;
    private final double cameraZ;
    private final int vehicleBlock;
    private final int vehicleSky;
    /** Packed light per block position, for this draw: neighbouring vertices share blocks. */
    private final Long2IntOpenHashMap cache = new Long2IntOpenHashMap();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
    private float x;
    private float y;
    private float z;

    private VehicleVertexLight(VertexConsumer delegate, Level level, Vec3 camera, int vehicleLight) {
        this.delegate = delegate;
        this.level = level;
        this.cameraX = camera.x;
        this.cameraY = camera.y;
        this.cameraZ = camera.z;
        this.vehicleBlock = LightCoordsUtil.block(vehicleLight);
        this.vehicleSky = LightCoordsUtil.sky(vehicleLight);
        this.cache.defaultReturnValue(-1);
    }

    /**
     * {@code buffer} lit vertex by vertex, or {@code buffer} itself where per-vertex light doesn't apply: no world (a
     * vehicle drawn on a screen), or a full-bright draw (the assembling screen, glows).
     */
    static VertexConsumer wrap(VertexConsumer buffer, Vec3 camera, int vehicleLight) {
        Level level = Minecraft.getInstance().level;
        if (level == null || camera == null || vehicleLight == LightCoordsUtil.FULL_BRIGHT) {
            return buffer;
        }
        return new VehicleVertexLight(buffer, level, camera, vehicleLight);
    }

    private int lightAt(float vx, float vy, float vz) {
        this.cursor.set(Mth.floor(this.cameraX + vx), Mth.floor(this.cameraY + vy), Mth.floor(this.cameraZ + vz));
        long key = this.cursor.asLong();
        int packed = this.cache.get(key);
        if (packed != -1) {
            return packed;
        }
        int sky;
        int block;
        if (this.level.getBlockState(this.cursor).isSolidRender()) {
            // Inside a solid block, whose own light is nothing: the brightest open block round it.
            sky = -1;
            block = 0;
            for (Direction direction : Direction.values()) {
                this.neighbour.setWithOffset(this.cursor, direction);
                if (this.level.getBlockState(this.neighbour).isSolidRender()) {
                    continue;
                }
                sky = Math.max(sky, this.level.getBrightness(LightLayer.SKY, this.neighbour));
                block = Math.max(block, this.level.getBrightness(LightLayer.BLOCK, this.neighbour));
            }
            if (sky < 0) {
                // Buried on every side: keep the vehicle's own.
                sky = this.vehicleSky;
            }
        } else {
            sky = this.level.getBrightness(LightLayer.SKY, this.cursor);
            block = this.level.getBrightness(LightLayer.BLOCK, this.cursor);
        }
        packed = LightCoordsUtil.pack(Math.max(block, this.vehicleBlock), sky);
        this.cache.put(key, packed);
        return packed;
    }

    @Override
    public void addVertex(float x, float y, float z, int colour, float u, float v, int overlay, int light,
                          float normalX, float normalY, float normalZ) {
        this.delegate.addVertex(x, y, z, colour, u, v, overlay, this.lightAt(x, y, z), normalX, normalY, normalZ);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        this.delegate.setColor(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer setColor(int colour) {
        this.delegate.setColor(colour);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        this.delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        this.delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        int light = this.lightAt(this.x, this.y, this.z);
        this.delegate.setUv2(light & 0xFFFF, light >>> 16);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        this.delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        this.delegate.setLineWidth(width);
        return this;
    }
}
