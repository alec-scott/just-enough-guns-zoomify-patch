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
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

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

    /** How long (ticks) a vehicle found evenly lit is drawn with that one light before it is looked at vertex by vertex again. */
    static final int RECHECK_TICKS = 4;
    /**
     * Vehicles whose last vertex-by-vertex pass found one light everywhere (the usual case, out in the open): drawn with
     * that light, as GeckoLib draws any entity, until something could have changed it. Render thread only.
     */
    private static final java.util.Map<VehicleEntity, Even> EVEN = new java.util.WeakHashMap<>();

    /** The light every vertex had, and what it was found under: the vehicle's block, heading and own light. */
    private record Even(int packed, int untilTick, long blockPos, int heading, int vehicleLight) {
        boolean holds(VehicleEntity vehicle, int vehicleLight) {
            return vehicle.tickCount < this.untilTick && vehicleLight == this.vehicleLight
                    && vehicle.blockPosition().asLong() == this.blockPos && headingStep(vehicle) == this.heading;
        }
    }

    /** The heading in 10-degree steps: turning moves the model's vertices into other blocks. */
    private static int headingStep(VehicleEntity vehicle) {
        return Mth.floor(Mth.wrapDegrees(vehicle.getYRot()) / 10.0F);
    }

    /** One draw of a vehicle's model: the buffer to write to and the light to give GeckoLib. */
    static final class Draw {
        private final VertexConsumer consumer;
        private final int light;
        private final VehicleVertexLight lit;
        private final VehicleEntity vehicle;
        private final int vehicleLight;

        private Draw(VertexConsumer consumer, int light, VehicleVertexLight lit, VehicleEntity vehicle, int vehicleLight) {
            this.consumer = consumer;
            this.light = light;
            this.lit = lit;
            this.vehicle = vehicle;
            this.vehicleLight = vehicleLight;
        }

        VertexConsumer consumer() {
            return this.consumer;
        }

        int light() {
            return this.light;
        }

        /** After a layer's draw (the glass): it only covers part of the model, so it can't say the whole is even. */
        void end() {
        }

        /**
         * After the main pass, which draws the whole model: if every vertex had the same light, remember it for
         * {@link #RECHECK_TICKS} ticks; otherwise keep lighting vertex by vertex.
         */
        void endWholeModel() {
            if (this.lit == null || this.vehicle == null) {
                return;
            }
            if (this.lit.even()) {
                EVEN.put(this.vehicle, new Even(this.lit.first, this.vehicle.tickCount + RECHECK_TICKS,
                        this.vehicle.blockPosition().asLong(), headingStep(this.vehicle), this.vehicleLight));
            } else {
                EVEN.remove(this.vehicle);
            }
        }
    }

    /**
     * How to draw a vehicle's model: lit vertex by vertex, or with one light where that changes nothing. One light is
     * used for a full-bright draw (the assembling screen), with no world (a vehicle drawn on a screen), in Iris's shadow
     * pass (which keeps depth, not lighting), and while the vehicle's last full pass found the same light at every
     * vertex (re-checked every {@link #RECHECK_TICKS} ticks and whenever it moves to another block, turns or its own
     * light changes). Measured 2026-11-26 without shaders, where the CPU sets the pace: the per-vertex lookups were 18%
     * of the render thread with the ArmourTest fleet in view.
     */
    static Draw begin(VertexConsumer buffer, Vec3 camera, int vehicleLight, VehicleEntity vehicle) {
        Level level = Minecraft.getInstance().level;
        if (level == null || camera == null || vehicleLight == LightCoordsUtil.FULL_BRIGHT
                || VehicleDrawCulling.shadowPassLight && ttv.migami.jeg.client.compat.ShaderPacks.renderingShadowPass()) {
            return new Draw(buffer, vehicleLight, null, null, vehicleLight);
        }
        if (vehicle != null && VehicleDrawCulling.evenLight) {
            Even even = EVEN.get(vehicle);
            if (even != null && even.holds(vehicle, vehicleLight)) {
                VehicleDrawCulling.evenDraws++;
                return new Draw(buffer, even.packed(), null, null, vehicleLight);
            }
        }
        VehicleVertexLight lit = new VehicleVertexLight(buffer, level, camera, vehicleLight);
        return new Draw(lit, vehicleLight, lit, vehicle, vehicleLight);
    }

    /** The first light found this draw, and whether any other was. */
    private int first = -1;
    private boolean mixed;

    private boolean even() {
        return this.first != -1 && !this.mixed;
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
        if (this.first == -1) {
            this.first = packed;
        } else if (packed != this.first) {
            this.mixed = true;
        }
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
