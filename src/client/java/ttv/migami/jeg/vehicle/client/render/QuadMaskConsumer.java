package ttv.migami.jeg.vehicle.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import ttv.migami.jeg.vehicle.client.resource.TextureCoverage;

/**
 * Passes on only the quads whose texture area has something to draw ({@link TextureCoverage}): the glass and glow
 * layers redraw the whole model with a texture that is empty nearly everywhere. GeckoLib hands each quad over as four
 * full vertices ({@code GeoQuad.render}), so the four are held, their UVs bounded, and all four passed on or dropped.
 * Anything else written (the builder calls) goes straight through, after any held vertices.
 */
final class QuadMaskConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final TextureCoverage coverage;
    private final float[] held = new float[4 * 9];
    private final int[] heldInts = new int[4 * 3];
    private int count;

    private QuadMaskConsumer(VertexConsumer delegate, TextureCoverage coverage) {
        this.delegate = delegate;
        this.coverage = coverage;
    }

    static VertexConsumer wrap(VertexConsumer delegate, TextureCoverage coverage) {
        return VehicleDrawCulling.quadMask ? new QuadMaskConsumer(delegate, coverage) : delegate;
    }

    @Override
    public void addVertex(float x, float y, float z, int colour, float u, float v, int overlay, int light,
                          float normalX, float normalY, float normalZ) {
        int f = this.count * 9;
        this.held[f] = x;
        this.held[f + 1] = y;
        this.held[f + 2] = z;
        this.held[f + 3] = u;
        this.held[f + 4] = v;
        this.held[f + 5] = normalX;
        this.held[f + 6] = normalY;
        this.held[f + 7] = normalZ;
        int i = this.count * 3;
        this.heldInts[i] = colour;
        this.heldInts[i + 1] = overlay;
        this.heldInts[i + 2] = light;
        if (++this.count < 4) {
            return;
        }
        this.count = 0;
        VehicleDrawCulling.quadsSeen++;
        float minU = Math.min(Math.min(this.held[3], this.held[12]), Math.min(this.held[21], this.held[30]));
        float maxU = Math.max(Math.max(this.held[3], this.held[12]), Math.max(this.held[21], this.held[30]));
        float minV = Math.min(Math.min(this.held[4], this.held[13]), Math.min(this.held[22], this.held[31]));
        float maxV = Math.max(Math.max(this.held[4], this.held[13]), Math.max(this.held[22], this.held[31]));
        if (this.coverage.any(minU, minV, maxU, maxV)) {
            this.flush(4);
        } else {
            VehicleDrawCulling.quadsMasked++;
        }
    }

    /** Hands on the first {@code n} held vertices as they came. */
    private void flush(int n) {
        for (int k = 0; k < n; k++) {
            int f = k * 9;
            int i = k * 3;
            this.delegate.addVertex(this.held[f], this.held[f + 1], this.held[f + 2], this.heldInts[i], this.held[f + 3],
                    this.held[f + 4], this.heldInts[i + 1], this.heldInts[i + 2], this.held[f + 5], this.held[f + 6],
                    this.held[f + 7]);
        }
    }

    /** Something other than a GeckoLib quad: never hold back what came before it. */
    private void release() {
        if (this.count > 0) {
            int n = this.count;
            this.count = 0;
            this.flush(n);
        }
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        this.release();
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
        this.delegate.setUv2(u, v);
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
