package ttv.migami.jeg.vehicle.entity;

import net.minecraft.world.phys.Vec3;

/**
 * A rope with weight, as clients draw it (the user, 2026-11-21: "the rope should have gravity attached to it so it
 * naturally slings around"): a chain of nodes moved by Verlet integration under real gravity (9.81 m/s², as the sling's
 * own pendulum, {@link ChinookSling}), with a little air drag, held together by length constraints that only pull (a rope
 * goes slack, it never pushes). Its top is pinned to the hook; its bottom is pinned to what holds it (the apex ring, a
 * load's lift point) or hangs free with a hook's weight on it. So it sags between its ends, bows and whips as the
 * Chinook moves, and its free legs swing and trail. Ticked at 20 Hz with substeps; drawn eased between the last two ticks.
 */
public final class SlingRope {
    /**
     * Gravity on the rope, blocks a tick a tick: real gravity times {@link #WEIGHT}. A sling rope and its hooks are
     * heavy against the air: at real gravity with the drag below it streamed and fluttered like a light cord (the user,
     * 2026-11-25: "there should be more gravity on the rope, it feels too light").
     */
    private static final double WEIGHT = 2.6D;
    private static final double G = 9.81D / 400.0D * WEIGHT;
    private static final int SUBSTEPS = 4;
    private static final int ITERATIONS = 12;
    /** The share of each node's speed kept a substep (air and the rope's own stiffness). */
    private static final double DAMPING = 0.985D;
    /** Quadratic air drag on the nodes, a tick: a rope streams back at speed, a heavy one less. */
    private static final double DRAG = 0.007D;

    private final int count;
    private final double[] x, y, z, px, py, pz, ox, oy, oz;
    private boolean started;

    public SlingRope(int nodes) {
        this.count = Math.max(2, nodes);
        this.x = new double[this.count];
        this.y = new double[this.count];
        this.z = new double[this.count];
        this.px = new double[this.count];
        this.py = new double[this.count];
        this.pz = new double[this.count];
        this.ox = new double[this.count];
        this.oy = new double[this.count];
        this.oz = new double[this.count];
    }

    public int size() {
        return this.count;
    }

    /** Starts it again from straight (the sling was stowed, or the rope jumped: a teleport, a chunk reload). */
    public void reset() {
        this.started = false;
    }

    /**
     * One tick. The top moves from {@code topFrom} to {@code topTo} over the tick; the bottom likewise when {@code bottomTo}
     * is given, or hangs free with {@code endWeight} times a node's mass on it. {@code length} is the rope's whole length.
     */
    public void tick(Vec3 topFrom, Vec3 topTo, Vec3 bottomFrom, Vec3 bottomTo, double length, double endWeight) {
        int last = this.count - 1;
        if (this.started && Math.abs(this.x[0] - topFrom.x) + Math.abs(this.y[0] - topFrom.y) + Math.abs(this.z[0] - topFrom.z) > 16.0D) {
            this.started = false;
        }
        if (!this.started) {
            Vec3 end = bottomTo != null ? bottomTo : topTo.add(0.0D, -length, 0.0D);
            for (int i = 0; i <= last; i++) {
                Vec3 p = topTo.lerp(end, i / (double) last);
                this.x[i] = this.px[i] = this.ox[i] = p.x;
                this.y[i] = this.py[i] = this.oy[i] = p.y;
                this.z[i] = this.pz[i] = this.oz[i] = p.z;
            }
            this.started = true;
            return;
        }
        System.arraycopy(this.x, 0, this.ox, 0, this.count);
        System.arraycopy(this.y, 0, this.oy, 0, this.count);
        System.arraycopy(this.z, 0, this.oz, 0, this.count);
        double rest = length / last;
        double h = 1.0D / SUBSTEPS;
        double gravity = G * h * h;
        double endInverse = 1.0D / Math.max(1.0D, endWeight);
        for (int step = 1; step <= SUBSTEPS; step++) {
            double t = step / (double) SUBSTEPS;
            Vec3 top = topFrom.lerp(topTo, t);
            Vec3 bottom = bottomTo == null ? null : (bottomFrom == null ? bottomTo : bottomFrom.lerp(bottomTo, t));
            for (int i = 1; i <= last; i++) {
                double vx = (this.x[i] - this.px[i]) * DAMPING;
                double vy = (this.y[i] - this.py[i]) * DAMPING;
                double vz = (this.z[i] - this.pz[i]) * DAMPING;
                double speed = Math.sqrt(vx * vx + vy * vy + vz * vz) / h;
                double drag = Math.max(0.0D, 1.0D - DRAG * speed * h);
                this.px[i] = this.x[i];
                this.py[i] = this.y[i];
                this.pz[i] = this.z[i];
                this.x[i] += vx * drag;
                this.y[i] += vy * drag - gravity;
                this.z[i] += vz * drag;
            }
            this.pin(0, top);
            if (bottom != null) {
                this.pin(last, bottom);
            }
            for (int k = 0; k < ITERATIONS; k++) {
                for (int i = 0; i < last; i++) {
                    double dx = this.x[i + 1] - this.x[i], dy = this.y[i + 1] - this.y[i], dz = this.z[i + 1] - this.z[i];
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d <= rest || d < 1.0E-9D) {
                        continue;
                    }
                    double wa = i == 0 ? 0.0D : 1.0D;
                    double wb = i + 1 == last ? (bottom != null ? 0.0D : endInverse) : 1.0D;
                    double sum = wa + wb;
                    if (sum <= 0.0D) {
                        continue;
                    }
                    double f = (d - rest) / d / sum;
                    this.x[i] += dx * f * wa;
                    this.y[i] += dy * f * wa;
                    this.z[i] += dz * f * wa;
                    this.x[i + 1] -= dx * f * wb;
                    this.y[i + 1] -= dy * f * wb;
                    this.z[i + 1] -= dz * f * wb;
                }
            }
            this.pin(0, top);
            if (bottom != null) {
                this.pin(last, bottom);
            }
        }
    }

    private void pin(int i, Vec3 at) {
        this.x[i] = at.x;
        this.y[i] = at.y;
        this.z[i] = at.z;
    }

    /** Node {@code i} at this point between the last two ticks. */
    public Vec3 node(int i, float partialTick) {
        return new Vec3(this.ox[i] + (this.x[i] - this.ox[i]) * partialTick,
                this.oy[i] + (this.y[i] - this.oy[i]) * partialTick,
                this.oz[i] + (this.z[i] - this.oz[i]) * partialTick);
    }

    public boolean started() {
        return this.started;
    }
}
