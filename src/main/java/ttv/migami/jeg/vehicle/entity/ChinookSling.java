package ttv.migami.jeg.vehicle.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ttv.migami.jeg.vehicle.cabin.CabinMath;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * The Chinook's centre cargo hook and its four-leg sling (design/chinook/PLAN.md, P7; the user: the pilot hooks up
 * alone, no weight limit). The pilot's tow key (N): lower the sling; over a vehicle's roof, hook it; with nothing in
 * reach, raise it again; carrying, press twice to release (a guarded switch, as the real hook's).
 * <p>
 * Physics, on the server, four substeps a tick: the rope's end (the load's lift centre, or the empty hook) is a point
 * mass on an inelastic rope from the belly hook, under real gravity (9.8 m/s², not Minecraft's), so the swing's period
 * is a true 2π√(L/g), about six seconds on the 8 m sling; bluff-body drag trails it back in forward flight; light
 * damping lets a swing die over a few periods. The load's own movement is off while slung
 * ({@link VehicleEntity#isSlung()}); it is moved with {@code move()}, so the ground stops it.
 * <p>
 * The rope's pull acts back on the Chinook ({@link #pull()}), computed on the server and the pilot's client alike from
 * the synced rope state, so prediction agrees: a load sags it and a swing rocks it, the heavier the more, and still
 * the heaviest climbs at full collective. Clients draw the load where the rope puts it from the Chinook's smooth pose
 * ({@link #clientLoadPosition}), as the FSV-6's tow does.
 */
public final class ChinookSling {
    public static final int STOWED = 0, HANGING = 1, CARRYING = 2;
    /** The pilot's sling key as they bound it, resolved on their client (the messages said "N"; the pack rebinds keys). */
    private static final Component TOW_KEY = Component.keybind("key.jeg.vehicle_tow");
    /** The rope's top: the bottom of the hook under the belly (rig.json "hook"), data coordinates. */
    public static final Vec3 HOOK = new Vec3(0.0D, 0.29D, 0.625D);
    /** The pendant from the hook to the apex ring; how far the ring rides above a load's lift points. */
    public static final double PENDANT = 6.0D, LEG_RISE = 2.2D;
    /** Real gravity in blocks a tick a tick. */
    public static final double G = 9.81D / 400.0D;
    private static final int SUBSTEPS = 4;
    /** The winch: 2.4 m/s. */
    private static final double WINCH_RATE = 0.12D;
    /** The share of the swing's speed (against the hook) lost a tick: a swing halves in about four periods. */
    private static final double DAMPING = 0.0015D;
    /** How far the empty hook reaches to a load's lift centre. */
    /** How far the hanging hook takes a load from: across, below the hook and above it (see candidate). */
    private static final double REACH_ACROSS = 4.5D, REACH_BELOW = 3.5D, REACH_ABOVE = 7.0D;
    /** The longest the rope pays out to a load hooked from high up (the winch then takes it in to its carrying length). */
    private static final double MAX_ROPE = 15.0D;
    /** The most the rope pulls the Chinook down, blocks a tick a tick: under the weighty flight's spare lift (0.0175). */
    private static final double MAX_PULL = 0.015D;
    private static final int RELEASE_WINDOW = 40;

    private final ChinookEntity chinook;
    // Server state.
    @Nullable private UUID loadId;
    @Nullable private Vec3 end, hookPrev;
    private Vec3 velocity = Vec3.ZERO;
    private boolean raising;
    private long releaseArmedUntil = Long.MIN_VALUE;
    // Client state: the rope's end against the hook last tick and this tick (eased between in the frame).
    private Vector3f offsetO = new Vector3f(), offsetNow = new Vector3f();
    /** Clients: the drawn ropes, with weight (SlingRope): the pendant, and the four legs. */
    private final SlingRope pendantRope = new SlingRope(14);
    private final java.util.List<SlingRope> legRopes = new java.util.ArrayList<>();
    private int ropeState = -1;
    /** The empty sling's legs: 1.5 m each, a steel hook's weight at each end. */
    public static final double LEG_LENGTH = 1.5D;
    private static final double HOOK_WEIGHT = 14.0D;
    /** The empty hook and pendant swing as a heavy steel hook does, not a light weight on a cord (SlingRope.WEIGHT). */
    private static final double EMPTY_HOOK_GRAVITY = 2.6D;
    private static final Map<Integer, ChinookEntity> CLIENT_SLINGS = new HashMap<>();

    ChinookSling(ChinookEntity chinook) {
        this.chinook = chinook;
    }

    private SynchedEntityData data() {
        return this.chinook.getEntityData();
    }

    public int state() {
        return this.data().get(ChinookEntity.SLING_STATE);
    }

    /** The rope's length now, hook to end (it winches in and out). */
    public float length() {
        return this.data().get(ChinookEntity.SLING_LENGTH);
    }

    /** The slung load's entity id, or -1. */
    public int loadEntityId() {
        return this.data().get(ChinookEntity.SLING_LOAD);
    }

    @Nullable
    public VehicleEntity loadEntity() {
        int id = this.loadEntityId();
        return id >= 0 && this.chinook.level().getEntity(id) instanceof VehicleEntity load ? load : null;
    }

    /** The rope's end (the hook, or the load's lift centre), server side; null while stowed from load. */
    @Nullable
    public Vec3 endWorld() {
        return this.end;
    }

    /** A vehicle's lift centre, where the hook takes it. */
    public static Vec3 liftCentreOf(VehicleEntity load) {
        return liftCentre(load);
    }

    /** Where the rope hangs from, in the world, as the Chinook is drawn at this point between ticks. */
    public Vec3 hookWorld(float partialTick) {
        return CabinMath.toWorld(this.chinook.localToWorld(partialTick), HOOK);
    }

    // ---- The pilot's key ----

    void key(ServerPlayer pilot) {
        ServerLevel level = (ServerLevel) this.chinook.level();
        switch (this.state()) {
            case STOWED -> {
                this.setState(HANGING);
                this.raising = false;
                this.end = this.hookWorld(1.0F);
                this.velocity = this.chinook.getDeltaMovement();
                this.sound(level, SoundEvents.CHAIN_PLACE, 0.8F);
                pilot.sendOverlayMessage(Component.translatable("message.jeg.ch47.sling_lowered", TOW_KEY));
            }
            case HANGING -> {
                VehicleEntity load = this.raising ? null : this.candidate(level);
                if (load != null) {
                    this.hook(load);
                    this.sound(level, SoundEvents.CHAIN_HIT, 1.2F);
                    pilot.sendOverlayMessage(Component.translatable("message.jeg.ch47.sling_hooked", load.getType().getDescription()));
                } else {
                    this.raising = !this.raising;
                    pilot.sendOverlayMessage(Component.translatable(this.raising ? "message.jeg.ch47.sling_raised" : "message.jeg.ch47.sling_lowered", TOW_KEY));
                }
            }
            default -> {
                long now = level.getGameTime();
                if (now <= this.releaseArmedUntil) {
                    this.release(level);
                    this.releaseArmedUntil = Long.MIN_VALUE;
                    pilot.sendOverlayMessage(Component.translatable("message.jeg.ch47.sling_released"));
                } else {
                    this.releaseArmedUntil = now + RELEASE_WINDOW;
                    pilot.sendOverlayMessage(Component.translatable("message.jeg.ch47.sling_release_armed", TOW_KEY));
                }
            }
        }
    }

    /**
     * The vehicle the hook would take now: empty of people, one a sling may take, its lift centre within
     * {@link #REACH_ACROSS} of the hanging hook across and from {@link #REACH_BELOW} under it to {@link #REACH_ABOVE}
     * over it (the user, 2026-11-25: "the sling hook should be more forgiving"; it had to be within 3 m of the roof's
     * middle). Nearest across first: a hook hanging over a roof takes that vehicle.
     */
    @Nullable
    private VehicleEntity candidate(ServerLevel level) {
        if (this.end == null) {
            return null;
        }
        VehicleEntity best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 hook = this.hookWorld(1.0F);
        for (VehicleEntity other : level.getEntitiesOfClass(VehicleEntity.class, new net.minecraft.world.phys.AABB(this.end, this.end)
                        .inflate(REACH_ACROSS + 6.0D, REACH_ABOVE + 6.0D, REACH_ACROSS + 6.0D),
                v -> v != this.chinook && v.getPassengers().isEmpty() && v.canBeSlung() && !v.isWrecked() && !v.isSlung())) {
            Vec3 lift = liftCentre(other);
            double across = Math.hypot(lift.x - this.end.x, lift.z - this.end.z);
            double above = this.end.y - lift.y;
            if (across > REACH_ACROSS || above > REACH_ABOVE || above < -REACH_BELOW || lift.distanceTo(hook) > MAX_ROPE) {
                continue;
            }
            double score = across * across + 0.1D * Math.max(0.0D, above) * Math.max(0.0D, above);
            if (score < bestScore) {
                best = other;
                bestScore = score;
            }
        }
        return best;
    }

    private static Vec3 liftCentre(VehicleEntity load) {
        return load.position().add(0.0D, load.slingLiftHeight(), 0.0D);
    }

    private void hook(VehicleEntity load) {
        this.loadId = load.getUUID();
        this.data().set(ChinookEntity.SLING_LOAD, load.getId());
        this.data().set(ChinookEntity.SLING_CANDIDATE, -1);
        // Hooked from higher up than the carrying length: the rope pays out to the load and the winch takes it in,
        // lifting the load smoothly rather than snatching it up to the hook.
        double reach = liftCentre(load).distanceTo(this.hookWorld(1.0F));
        this.data().set(ChinookEntity.SLING_LENGTH, (float) Math.max(this.length(), Math.min(reach, MAX_ROPE)));
        this.end = liftCentre(load);
        this.velocity = load.getDeltaMovement();
        this.raising = false;
        this.setState(CARRYING);
    }

    /** The load goes its own way with the rope's speed; the empty hook hangs on. */
    void release(ServerLevel level) {
        VehicleEntity load = this.loadId == null ? null : level.getEntity(this.loadId) instanceof VehicleEntity v ? v : null;
        if (load != null) {
            load.setDeltaMovement(this.velocity);
            load.hurtMarked = true;
            this.sound(level, SoundEvents.CHAIN_BREAK, 0.9F);
        }
        this.loadId = null;
        this.data().set(ChinookEntity.SLING_LOAD, -1);
        if (this.state() == CARRYING) {
            this.setState(HANGING);
            Vec3 hook = this.hookWorld(1.0F);
            if (this.end != null) {
                // The empty hook is at the apex ring, a pendant's length down the rope.
                Vec3 along = this.end.subtract(hook);
                double length = along.length();
                this.end = length < 1.0E-3D ? hook : hook.add(along.scale(Math.min(PENDANT, length) / length));
            }
        }
    }

    private void setState(int state) {
        this.data().set(ChinookEntity.SLING_STATE, state);
    }

    private void sound(ServerLevel level, SoundEvent sound, float pitch) {
        Vec3 at = this.hookWorld(1.0F);
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, 1.0F, pitch);
    }

    // ---- Physics (server) ----

    void tick(ServerLevel level) {
        this.trackParting();
        Vec3 hook = this.hookWorld(1.0F);
        Vec3 hookFrom = this.hookPrev == null ? hook : this.hookPrev;
        this.hookPrev = hook;
        int state = this.state();
        if (state == STOWED) {
            this.end = hook;
            this.velocity = hook.subtract(hookFrom);
            this.data().set(ChinookEntity.SLING_LENGTH, 0.0F);
            this.syncEnd(hook);
            return;
        }
        VehicleEntity load = null;
        if (state == CARRYING) {
            load = this.loadId == null ? null : level.getEntity(this.loadId) instanceof VehicleEntity v ? v : null;
            if (load == null && this.loadId != null && this.chinook.tickCount < 40) {
                // Just loaded from a save: the load's chunk may come in a moment later.
                this.syncEnd(hook);
                return;
            }
            // Gone, boarded, or held fast far past the rope's length (snagged, or the Chinook teleported away): it lets
            // go, or the rope stretched across the map to it (2026-11-25).
            if (load == null || load.isRemoved() || !load.getPassengers().isEmpty() || this.chinook.isWrecked()
                    || (this.end != null && liftCentre(load).distanceTo(this.end) > 8.0D)
                    || liftCentre(load).distanceTo(hook) > Math.max(this.length(), PENDANT + LEG_RISE) + 6.0D) {
                this.release(level);
                return;
            }
            if (load.getId() != this.loadEntityId()) {
                this.data().set(ChinookEntity.SLING_LOAD, load.getId());
            }
            if (this.end == null) {
                this.end = liftCentre(load);
            }
        }
        if (this.end == null) {
            this.end = hook;
        }
        // What the hook would take now, for the pilot's HUD: looked for a few times a second while it hangs.
        if (state == HANGING && this.chinook.tickCount % 5 == 0) {
            VehicleEntity reachable = this.raising ? null : this.candidate(level);
            this.data().set(ChinookEntity.SLING_CANDIDATE, reachable == null ? -1 : reachable.getId());
        } else if (state != HANGING && this.data().get(ChinookEntity.SLING_CANDIDATE) != -1) {
            this.data().set(ChinookEntity.SLING_CANDIDATE, -1);
        }
        // The winch.
        double target = state == CARRYING ? PENDANT + LEG_RISE : this.raising ? 0.0D : PENDANT;
        double length = this.length();
        length = length < target ? Math.min(target, length + WINCH_RATE) : Math.max(target, length - WINCH_RATE);
        if (state == CARRYING && length < this.end.distanceTo(hook) && length < target) {
            // Hooked up from close by: the rope takes up its slack as the Chinook climbs.
            length = Math.min(target, Math.max(length, this.end.distanceTo(hook)));
        }
        this.data().set(ChinookEntity.SLING_LENGTH, (float) length);
        if (state == HANGING && this.raising && length <= 0.05D) {
            this.setState(STOWED);
            this.raising = false;
            this.sound(level, SoundEvents.CHAIN_PLACE, 1.3F);
            this.syncEnd(hook);
            return;
        }

        double mass = load == null ? 0.05D : massOf(load);
        // Bluff-body drag on the load's frontal area over its mass (an empty hook barely trails).
        double drag = load == null ? 0.004D : 0.0012D * Math.sqrt(10.0D / Math.max(1.0D, mass));
        Vec3 hookVelocity = hook.subtract(hookFrom);
        double dt = 1.0D / SUBSTEPS;
        Vec3 end = this.end;
        Vec3 velocity = this.velocity;
        for (int step = 1; step <= SUBSTEPS; step++) {
            Vec3 h = hookFrom.lerp(hook, step / (double) SUBSTEPS);
            velocity = velocity.add(0.0D, -G * (load == null ? EMPTY_HOOK_GRAVITY : 1.0D) * dt, 0.0D);
            double speed = velocity.length();
            velocity = velocity.scale(Math.max(0.0D, 1.0D - drag * speed * dt));
            end = end.add(velocity.scale(dt));
            Vec3 d = end.subtract(h);
            double distance = d.length();
            if (distance > length && distance > 1.0E-6D) {
                Vec3 n = d.scale(1.0D / distance);
                end = h.add(n.scale(length));
                double radial = velocity.subtract(hookVelocity).dot(n);
                if (radial > 0.0D) {
                    velocity = velocity.subtract(n.scale(radial));
                }
            }
            Vec3 relative = velocity.subtract(hookVelocity);
            velocity = hookVelocity.add(relative.scale(1.0D - DAMPING * dt));
        }

        if (load != null) {
            // The load goes where the rope's end is, unless the ground (or anything solid) stops it.
            double lift = load.slingLiftHeight();
            Vec3 want = end.subtract(0.0D, lift, 0.0D);
            load.move(MoverType.SELF, want.subtract(load.position()));
            Vec3 got = load.position().add(0.0D, lift, 0.0D);
            Vec3 stopped = got.subtract(end);
            if (stopped.lengthSqr() > 1.0E-6D) {
                end = got;
                double vx = Math.abs(stopped.x) > 1.0E-3D ? 0.0D : velocity.x;
                double vz = Math.abs(stopped.z) > 1.0E-3D ? 0.0D : velocity.z;
                double vy = Math.abs(stopped.y) > 1.0E-3D ? 0.0D : velocity.y;
                if (stopped.y > 1.0E-3D) {
                    // Resting on the ground: it drags.
                    vx *= 0.7D;
                    vz *= 0.7D;
                }
                velocity = new Vec3(vx, vy, vz);
            }
            // The four legs hold it level; the swivel lets it turn slowly after the Chinook's heading.
            float yaw = load.getYRot() + Mth.wrapDegrees(this.chinook.getYRot() - load.getYRot()) * 0.03F;
            load.setYRot(yaw);
            load.setDeltaMovement(velocity);
            load.markSlung();
            load.resetFallDistance();
        } else if (level.getBlockState(BlockPos.containing(end)).isSolid()) {
            // The empty hook rests on whatever it is lowered onto.
            end = new Vec3(end.x, Math.floor(end.y) + 1.0D, end.z);
            velocity = new Vec3(velocity.x * 0.6D, Math.max(0.0D, velocity.y), velocity.z * 0.6D);
        }
        this.end = end;
        this.velocity = velocity;
        this.syncEnd(hook);
    }

    private void syncEnd(Vec3 hook) {
        Vec3 offset = this.end == null ? Vec3.ZERO : this.end.subtract(hook);
        this.data().set(ChinookEntity.SLING_OFFSET, new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
        this.data().set(ChinookEntity.SLING_VELOCITY, new Vector3f((float) this.velocity.x, (float) this.velocity.y, (float) this.velocity.z));
    }

    static double massOf(VehicleEntity vehicle) {
        double mass = vehicle.vehicleData().mass();
        return Double.isNaN(mass) || mass <= 0.0D ? 5.0D : mass;
    }

    private double loadRatio(VehicleEntity load) {
        return massOf(load) / massOf(this.chinook);
    }

    // ---- Keeping clear of the load (server and clients) ----

    /** The load just let go, kept clear of until the two are apart (or five seconds), or -1. */
    private int partingId = -1;
    private int partingTicks;

    private void trackParting() {
        int carried = this.state() == CARRYING ? this.loadEntityId() : -1;
        if (carried >= 0) {
            this.partingId = carried;
            this.partingTicks = 0;
            return;
        }
        if (this.partingId >= 0) {
            net.minecraft.world.entity.Entity parted = this.chinook.level().getEntity(this.partingId);
            if (parted == null || ++this.partingTicks > 100 || !parted.getBoundingBox().intersects(this.chinook.getBoundingBox().inflate(0.5D))) {
                this.partingId = -1;
            }
        }
    }

    /**
     * Whether the Chinook and this entity pass through each other: its load on the sling, and the load just let go
     * until they are apart. A helicopter set down on its own load rested on its roof while the rope moved the load about
     * under it, and both jolted back and forth until it slid off (the user, 2026-11-25: "if the vehicle it carries
     * glitches out the heli also glitches out ... when I disconnected it, it also glitched the Chinook out").
     */
    public boolean keepsClearOf(net.minecraft.world.entity.Entity other) {
        if (other == null) {
            return false;
        }
        int id = other.getId();
        return (this.state() == CARRYING && id == this.loadEntityId()) || id == this.partingId;
    }

    /** How high the Chinook's belly stays over its load's top when it comes down onto it (the hook's clearance). */
    public static final double LOAD_CLEARANCE = 1.2D;

    /**
     * Coming down with the load set down under it, the Chinook's descent eases off and stops with its belly
     * {@link #LOAD_CLEARANCE} over the load's top, instead of landing on its roof.
     */
    public Vec3 cushion(Vec3 velocity) {
        if (velocity.y >= 0.0D || this.state() != CARRYING) {
            return velocity;
        }
        VehicleEntity load = this.loadEntity();
        if (load == null) {
            return velocity;
        }
        net.minecraft.world.phys.AABB mine = this.chinook.getBoundingBox(), theirs = load.getBoundingBox();
        if (theirs.maxX <= mine.minX || theirs.minX >= mine.maxX || theirs.maxZ <= mine.minZ || theirs.minZ >= mine.maxZ
                || theirs.maxY > mine.maxY) {
            return velocity;
        }
        double gap = mine.minY - theirs.maxY - LOAD_CLEARANCE;
        double floor = -Math.max(0.0D, gap) * 0.25D;
        return velocity.y < floor ? new Vec3(velocity.x, floor, velocity.z) : velocity;
    }

    // ---- The rope's pull on the Chinook (server and the pilot's client, from the synced rope) ----

    /**
     * What the taut rope does to the Chinook this tick, blocks a tick a tick: the tension (the load's weight, plus what
     * its swing adds) along the rope, toward the load, scaled to stay under the rotor's spare lift however heavy.
     */
    public Vec3 pull() {
        if (this.state() != CARRYING) {
            return Vec3.ZERO;
        }
        VehicleEntity load = this.loadEntity();
        if (load == null) {
            return Vec3.ZERO;
        }
        org.joml.Vector3fc o = this.data().get(ChinookEntity.SLING_OFFSET);
        Vec3 offset = new Vec3(o.x(), o.y(), o.z());
        double distance = offset.length();
        // The rope takes the weight over its last 35 cm, as a stretching rope does: a load set down (or lifted off)
        // under a hovering Chinook no longer pulls it by its whole weight one tick and not at all the next, which
        // bounced the aircraft at 10 Hz (2026-11-25).
        double taut = Mth.clamp((distance - (this.length() - ROPE_GIVE)) / ROPE_GIVE, 0.0D, 1.0D);
        if (distance < 1.0E-3D || taut <= 0.0D) {
            return Vec3.ZERO;
        }
        Vec3 n = offset.scale(1.0D / distance);
        org.joml.Vector3fc v = this.data().get(ChinookEntity.SLING_VELOCITY);
        Vec3 relative = new Vec3(v.x(), v.y(), v.z()).subtract(this.chinook.getDeltaMovement());
        double radial = relative.dot(n);
        double tangential = Math.max(0.0D, relative.lengthSqr() - radial * radial);
        double tension = Mth.clamp((-G * n.y + tangential / distance) / G, 0.0D, 3.0D);
        double weight = MAX_PULL * (1.0D - Math.exp(-this.loadRatio(load)));
        return n.scale(weight * tension * taut);
    }

    /** The rope's stretch over which it takes up the load's weight. */
    private static final double ROPE_GIVE = 0.35D;

    /** A load makes the Chinook answer its controls more slowly (weighty flight's weight), the heavier the slower. */
    public double weightScale() {
        VehicleEntity load = this.state() == CARRYING ? this.loadEntity() : null;
        return load == null ? 1.0D : 1.0D + 0.6D * Math.min(this.loadRatio(load), 2.5D);
    }

    // ---- Clients ----

    void clientTick() {
        this.trackParting();
        this.offsetO = this.offsetNow;
        this.offsetNow = new Vector3f(this.data().get(ChinookEntity.SLING_OFFSET));
        CLIENT_SLINGS.values().removeIf(c -> c == this.chinook || c.isRemoved());
        int load = this.state() == CARRYING ? this.loadEntityId() : -1;
        if (load >= 0) {
            CLIENT_SLINGS.put(load, this.chinook);
        }
        this.tickRopes();
    }

    /**
     * The ropes as clients draw them: pinned at the hook and at the rope's end the server swings (the apex ring), with
     * a little slack so they sag; carrying, the legs pinned at the load's lift points; empty, the legs hanging free from
     * the ring with their hooks, swinging and trailing.
     */
    private void tickRopes() {
        int state = this.state();
        if (state != this.ropeState || state == STOWED || this.length() < 0.05F) {
            this.pendantRope.reset();
            this.legRopes.forEach(SlingRope::reset);
            this.ropeState = state;
            if (state == STOWED || this.length() < 0.05F) {
                return;
            }
        }
        Vec3 hookFrom = this.hookWorld(0.0F), hookTo = this.hookWorld(1.0F);
        Vec3 endFrom = hookFrom.add(this.offsetO.x, this.offsetO.y, this.offsetO.z);
        Vec3 endTo = hookTo.add(this.offsetNow.x, this.offsetNow.y, this.offsetNow.z);
        VehicleEntity load = state == CARRYING ? this.loadEntity() : null;
        if (load != null) {
            Vec3 ringFrom = ring(hookFrom, endFrom), ringTo = ring(hookTo, endTo);
            this.pendantRope.tick(hookFrom, hookTo, ringFrom, ringTo, hookTo.distanceTo(ringTo) * 1.015D, 1.0D);
            java.util.List<Vec3> from = liftPointsWorld(load, 0.0F), to = liftPointsWorld(load, 1.0F);
            this.legs(to.size());
            for (int i = 0; i < to.size(); i++) {
                this.legRopes.get(i).tick(ringFrom, ringTo, from.get(i), to.get(i), ringTo.distanceTo(to.get(i)) * 1.01D, 1.0D);
            }
        } else {
            this.pendantRope.tick(hookFrom, hookTo, endFrom, endTo, Math.max(0.05D, hookTo.distanceTo(endTo)) * 1.012D, 1.0D);
            this.legs(4);
            float yaw = this.chinook.getYRot() * Mth.DEG_TO_RAD;
            for (int i = 0; i < 4; i++) {
                double angle = yaw + Math.PI / 4.0D + i * Math.PI / 2.0D;
                Vec3 spread = new Vec3(Math.cos(angle) * 0.06D, -0.1D, Math.sin(angle) * 0.06D);
                this.legRopes.get(i).tick(endFrom.add(spread), endTo.add(spread), null, null, LEG_LENGTH, HOOK_WEIGHT);
            }
        }
    }

    private void legs(int count) {
        while (this.legRopes.size() < count) {
            this.legRopes.add(new SlingRope(7));
        }
    }

    /** The apex ring: a pendant's length down the rope from the hook toward its end. */
    public static Vec3 ring(Vec3 hook, Vec3 end) {
        Vec3 down = end.subtract(hook);
        double distance = down.length();
        Vec3 n = distance < 1.0E-3D ? new Vec3(0.0D, -1.0D, 0.0D) : down.scale(1.0D / distance);
        return hook.add(n.scale(Math.min(PENDANT, distance)));
    }

    /** On a client: a slung load's lift points in the world as it is drawn at this point between ticks. */
    public static java.util.List<Vec3> liftPointsWorld(VehicleEntity load, float partialTick) {
        Vec3 origin = clientLoadPosition(load, partialTick);
        if (origin == null) {
            origin = load.getPosition(partialTick);
        }
        float yaw = Mth.rotLerp(partialTick, load.yRotO, load.getYRot()) * Mth.DEG_TO_RAD;
        double cos = Math.cos(yaw), sin = Math.sin(yaw);
        java.util.List<Vec3> points = new java.util.ArrayList<>();
        for (Vec3 point : load.slingLiftPoints()) {
            points.add(origin.add(point.x * cos - point.z * sin, point.y, point.x * sin + point.z * cos));
        }
        return points;
    }

    /** On a client: the pendant as drawn (with weight). */
    public SlingRope pendantRope() {
        return this.pendantRope;
    }

    /** On a client: the legs as drawn, one a lift point (four, hanging free, while the hook is empty). */
    public java.util.List<SlingRope> legRopes() {
        return this.legRopes;
    }

    /** On a client: the rope's end at this point between ticks, eased between the last two ticks' synced positions. */
    public Vec3 clientEnd(float partialTick) {
        Vector3f o = new Vector3f(this.offsetO).lerp(this.offsetNow, partialTick);
        return this.hookWorld(partialTick).add(o.x, o.y, o.z);
    }

    /** On a client: the Chinook carrying {@code load}, or null. */
    @Nullable
    public static ChinookEntity clientCarrier(VehicleEntity load) {
        ChinookEntity chinook = CLIENT_SLINGS.get(load.getId());
        if (chinook == null) {
            return null;
        }
        if (chinook.isRemoved() || chinook.level() != load.level() || chinook.sling().loadEntityId() != load.getId()) {
            CLIENT_SLINGS.remove(load.getId());
            return null;
        }
        return chinook;
    }

    /**
     * On a client: where a slung load is drawn this frame, from the Chinook's smooth pose and the rope's eased end, not
     * where the server last sent it (that lagged a step behind the drawn Chinook and jumped). Null when no rope holds it.
     */
    @Nullable
    public static Vec3 clientLoadPosition(VehicleEntity load, float partialTick) {
        ChinookEntity chinook = clientCarrier(load);
        if (chinook == null) {
            return null;
        }
        return chinook.sling().clientEnd(partialTick).subtract(0.0D, load.slingLiftHeight(), 0.0D);
    }

    // ---- Saving ----

    void save(ValueOutput out) {
        out.putInt("State", this.state());
        out.putFloat("Length", this.length());
        out.putBoolean("Raising", this.raising);
        if (this.loadId != null) {
            out.store("Load", UUIDUtil.CODEC, this.loadId);
        }
    }

    void load(ValueInput in) {
        this.setState(in.getIntOr("State", STOWED));
        this.data().set(ChinookEntity.SLING_LENGTH, in.getFloatOr("Length", 0.0F));
        this.raising = in.getBooleanOr("Raising", false);
        this.loadId = in.read("Load", UUIDUtil.CODEC).orElse(null);
        if (this.state() == CARRYING && this.loadId == null) {
            this.setState(HANGING);
        }
        this.end = null;
        this.velocity = Vec3.ZERO;
    }
}
