package ttv.migami.jeg.vehicle.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModSounds;
import ttv.migami.jeg.vehicle.client.audio.AircraftSoundRange;
import ttv.migami.jeg.vehicle.client.audio.DynamicRelativeSound;
import ttv.migami.jeg.vehicle.data.subdata.VehicleType;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * How a ground vehicle's weight is felt and heard (the user's rule of 2026-11-17: a tank feels heavy, and every movement
 * has a fitting sound and is felt by the crew). Client side, per vehicle:
 * <ul>
 *   <li>The hull rides on its suspension: it squats as it pulls away, dives as it brakes and rolls out of a turn, on a
 *   spring that rocks slowly and settles late in a heavy hull and quickly in a light one ({@link VehicleEntity#inertia}).
 *   Drawn by the renderer ({@link #pitch}, {@link #roll}, {@link #lift}).</li>
 *   <li>The crew in first person feels it: the hull's rock (except through a gun sight, stabilised as a modern turret's
 *   is), a rumble that grows with speed (track links or tyre lugs, lower in a heavy hull) and a jolt landing from a
 *   drop ({@link #cameraLift}, {@link #cameraPitch}, {@link #cameraRoll}).</li>
 *   <li>Every movement sounds: the turret's electric drive (armour), servos (the HPJ-11 and sentry) or the handwheels
 *   (towed guns) while the turret or gun moves; the brakes on a hard stop; a heavy knock landing; a splash driving into
 *   water. Real recordings, design/ground-vehicles/prepare_movement.py.</li>
 * </ul>
 */
public final class VehicleFeel {
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final double RANGE = 160.0D;
    /** For game tests: the movement sounds started lately, as "entityId:name". */
    private static final java.util.ArrayDeque<String> EVENTS = new java.util.ArrayDeque<>();

    private VehicleFeel() {}

    private static final class State {
        final VehicleEntity vehicle;
        Vec3 lastPos;
        double lastAlong, lastSpeed, lastVy, phase, phaseO;
        boolean lastGround, lastWater, wasBraking;
        float pitch, pitchO, pitchV, roll, rollO, rollV, lift, liftO, liftV;
        float lastTurretYaw, lastTurretPitch;
        int brakeCooldown, bumpCooldown;
        Sound drive, elevation, radar;

        State(VehicleEntity vehicle) {
            this.vehicle = vehicle;
            this.lastPos = vehicle.position();
            this.lastGround = vehicle.onGround();
            this.lastWater = vehicle.isInWater();
            this.lastTurretYaw = vehicle.turretYaw();
            this.lastTurretPitch = vehicle.turretPitch();
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            clear(mc);
            return;
        }
        var seen = new java.util.HashSet<Integer>();
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof VehicleEntity vehicle) || vehicle.isWrecked()) continue;
            VehicleType type = vehicle.vehicleData().defaults().vehicleType();
            if (type != VehicleType.LAND && type != VehicleType.ARTILLERY && type != VehicleType.BOAT) continue;
            if (camera.distanceToSqr(vehicle.position()) > RANGE * RANGE) continue;
            State s = STATES.computeIfAbsent(vehicle.getId(), id -> new State(vehicle));
            if (s.vehicle != vehicle) {
                s.drive = stop(mc, s.drive);
                s.elevation = stop(mc, s.elevation);
                s.radar = stop(mc, s.radar);
                STATES.put(vehicle.getId(), s = new State(vehicle));
            }
            seen.add(vehicle.getId());
            tickVehicle(mc, s, type);
        }
        var it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (!seen.contains(e.getKey())) {
                stop(mc, e.getValue().drive);
                stop(mc, e.getValue().elevation);
                stop(mc, e.getValue().radar);
                it.remove();
            }
        }
    }

    public static void clear(Minecraft mc) {
        STATES.values().forEach(s -> {
            stop(mc, s.drive);
            stop(mc, s.elevation);
            stop(mc, s.radar);
        });
        STATES.clear();
    }

    private static void tickVehicle(Minecraft mc, State s, VehicleType type) {
        VehicleEntity v = s.vehicle;
        float inertia = (float) v.inertia();
        Vec3 pos = v.position();
        Vec3 delta = pos.subtract(s.lastPos);
        s.lastPos = pos;
        Vec3 forward = Vec3.directionFromRotation(0.0F, v.getYRot());
        double along = delta.x * forward.x + delta.z * forward.z;
        double speed = Math.hypot(delta.x, delta.z);
        double accel = along - s.lastAlong;
        s.lastAlong = along;
        float yawRate = Mth.wrapDegrees(v.getYRot() - v.yRotO);
        boolean ground = v.onGround();
        boolean water = v.isInWater();

        if (type == VehicleType.LAND) {
            // The suspension: squat pulling away (nose up), dive braking (nose down), roll out of a turn. Heavy hulls
            // rock slower (a softer spring for their mass) and settle later.
            // A heavy hull pulls away and stops more gently, but rocks as much on its springs: the pitch is scaled up by
            // its mass.
            float targetPitch = Mth.clamp((float) (-accel * 28.0D / inertia), -3.5F, 3.5F);
            float targetRoll = Mth.clamp((float) (-yawRate * speed * 6.0D), -2.5F, 2.5F);
            if (!ground && !v.amphibiousAfloat()) {
                targetPitch = 0.0F;
                targetRoll = 0.0F;
            }
            float stiffness = 0.10F + 0.18F * inertia;
            float damping = 0.16F + 0.10F * inertia;
            s.pitchO = s.pitch;
            s.pitchV += (targetPitch - s.pitch) * stiffness - s.pitchV * damping;
            s.pitch += s.pitchV;
            s.rollO = s.roll;
            s.rollV += (targetRoll - s.roll) * stiffness - s.rollV * damping;
            s.roll += s.rollV;
            // Landing from a drop: the hull bottoms out and springs back; a heavy hull knocks harder and rocks less.
            s.liftO = s.lift;
            if (!s.lastGround && ground && s.lastVy < -0.18D && s.bumpCooldown <= 0) {
                float hit = (float) Math.min(0.3D, -s.lastVy * 0.6D);
                s.liftV -= hit;
                s.pitchV += (float) (s.lastAlong >= 0.0D ? 1.0D : -1.0D) * hit * 6.0F * inertia;
                oneShot(mc, v, "bump", Mth.clamp((float) (-s.lastVy * 2.2D), 0.4F, 1.4F), 1.05F - 0.25F * (1.0F - inertia));
                s.bumpCooldown = 8;
            }
            s.liftV += -s.lift * (0.22F + 0.1F * inertia) - s.liftV * 0.28F;
            s.lift += s.liftV;
            // The track links or tyre lugs under the hull: a rumble cycle per wheel spacing travelled.
            s.phaseO = s.phase;
            // Capped so the camera's wave stays smooth at 20 ticks a second: flat out the LAV-150 stepped it 3 radians a
            // tick (its chatter 8), past what a tick can show, and the driver's view hopped at random (the user's
            // report, 2026-11-19). Faster than this the rumble is felt as its size, not its rate.
            s.phase += Math.min(MAX_RUMBLE_STEP, Math.abs(along) * Math.PI * 2.0D / (v.vehicleData().tracks() != null ? 1.3D : 2.1D));
            // Brakes on a hard stop from speed; a splash driving into water.
            if (s.brakeCooldown > 0) s.brakeCooldown--;
            // The brakes, when the driver brakes from speed (synced, so every client hears them).
            boolean braking = v.driverBraking();
            if (braking && !s.wasBraking && s.lastSpeed > 0.15D && s.brakeCooldown <= 0) {
                oneShot(mc, v, v.vehicleData().tracks() != null ? "brake_heavy" : "brake", 0.9F, 1.0F);
                s.brakeCooldown = 40;
            }
            if (!s.lastWater && water && speed > 0.05D) {
                oneShot(mc, v, "splash", Mth.clamp((float) speed * 2.5F, 0.6F, 1.6F), 0.85F + 0.15F * inertia);
            }
        }
        if (type == VehicleType.BOAT) {
            // A light planing hull on the chop: it banks into a turn (inward, unlike a car's body roll), pitches and
            // heaves over the waves more the faster it runs, and rocks gently even stopped. Out of water (beached), still.
            boolean afloat = water || v.level().getFluidState(net.minecraft.core.BlockPos.containing(pos.x, pos.y - 0.6D, pos.z))
                    .is(net.minecraft.tags.FluidTags.WATER);
            double top = Math.max(0.1D, v.vehicleData().defaults().engine().maxForwardSpeed());
            float pace = (float) Mth.clamp(speed / top, 0.0D, 1.0D);
            float chop = afloat ? 0.25F + 0.75F * pace : 0.0F;
            s.phaseO = s.phase;
            s.phase += 0.1D + pace * 0.38D;
            float targetPitch = afloat ? (float) Math.sin(s.phase) * 1.3F * chop + Mth.clamp((float) (-accel * 18.0D), -2.0F, 2.0F) : 0.0F;
            float targetRoll = afloat ? Mth.clamp((float) (yawRate * speed * 5.0D), -7.0F, 7.0F) + (float) Math.sin(s.phase * 0.63D) * 1.1F * chop : 0.0F;
            s.pitchO = s.pitch;
            s.pitchV += (targetPitch - s.pitch) * 0.14F - s.pitchV * 0.18F;
            s.pitch += s.pitchV;
            s.rollO = s.roll;
            s.rollV += (targetRoll - s.roll) * 0.12F - s.rollV * 0.18F;
            s.roll += s.rollV;
            s.liftO = s.lift;
            s.lift = afloat ? (float) Math.sin(s.phase * 1.3D) * 0.05F * chop : 0.0F;
        }
        if (s.bumpCooldown > 0) s.bumpCooldown--;
        s.lastSpeed = speed;
        if (type == VehicleType.LAND) s.wasBraking = v.driverBraking();
        s.lastVy = delta.y;
        s.lastGround = ground;
        s.lastWater = water;

        // The HPJ-11's search radar: its drive motor hums while the antenna sweeps (manned or on automatic).
        if (v instanceof ttv.migami.jeg.vehicle.entity.Hpj11Entity ciws) {
            boolean sweeping = ciws.automatic() || ciws.getControllingPassenger() != null;
            s.radar = loop(mc, s.radar, v, "servo", sweeping ? 0.22F : 0.0F, 0.72F);
        }
        // The turret and gun drives, while they move.
        if (v.vehicleData().defaults().turret().enabled()) {
            float dYaw = Math.abs(Mth.wrapDegrees(v.turretYaw() - s.lastTurretYaw));
            float dPitch = Math.abs(v.turretPitch() - s.lastTurretPitch);
            s.lastTurretYaw = v.turretYaw();
            s.lastTurretPitch = v.turretPitch();
            String path = v.vehicleDataId().getPath();
            // Powered mounts whine as heavy electric drives (the Mk42's and BL-132's mounts and the HPJ-11's were a
            // handwheel and a thin servo: the user's review, 2026-11-18, wanted a heavy mechanical whine); only the
            // sentry keeps its small servo, and hand-laid guns their handwheels. The heavier the mount, the deeper.
            boolean servo = path.equals("sentry");
            boolean powered = path.equals("hpj11") || path.equals("mk42") || path.equals("bl132");
            boolean manual = type == VehicleType.ARTILLERY && !servo && !powered;
            String driveSound = servo ? "servo" : manual ? "handwheel" : "traverse";
            // inertia() falls with mass (0.4 for the 63 t Mk42, 1.15 for the lightest): heavy mounts drone lower.
            float lightness = (float) ((Mth.clamp(v.inertia(), 0.4D, 1.15D) - 0.4D) / 0.75D);
            float deep = servo ? 1.0F : Mth.lerp(lightness, 0.78F, 1.05F);
            float driveRate = manual ? Math.max(dYaw, dPitch) : dYaw;
            float driveLevel = driveRate > 0.04F ? Mth.clamp((manual ? 0.5F : 0.45F) + driveRate / 4.0F, 0.0F, 1.0F) : 0.0F;
            s.drive = loop(mc, s.drive, v, driveSound, driveLevel, deep * (0.9F + Mth.clamp(driveRate / 8.0F, 0.0F, 0.25F)));
            if (!manual) {
                float elevLevel = dPitch > 0.03F ? Mth.clamp(0.35F + dPitch / 3.0F, 0.0F, 0.9F) : 0.0F;
                s.elevation = loop(mc, s.elevation, v, servo ? "servo" : "elevation", elevLevel, servo ? 1.15F : deep);
            }
        }
    }

    /** Radians a tick the rumble's wave may advance (about 1.4 cycles a second), and the chatter's rate on it. */
    private static final double MAX_RUMBLE_STEP = 0.45D;
    private static final double CHATTER_RATIO = 2.3D;

    // ---- What the renderer and camera read.

    /** The hull's suspension pitch, degrees, positive nose down. */
    public static float pitch(VehicleEntity vehicle, float partialTick) {
        State s = STATES.get(vehicle.getId());
        return s == null || s.vehicle != vehicle ? 0.0F : Mth.lerp(partialTick, s.pitchO, s.pitch);
    }

    /** The hull's suspension roll, degrees, positive right side down. */
    public static float roll(VehicleEntity vehicle, float partialTick) {
        State s = STATES.get(vehicle.getId());
        return s == null || s.vehicle != vehicle ? 0.0F : Mth.lerp(partialTick, s.rollO, s.roll);
    }

    /** The hull's drop on its suspension after a landing, blocks (negative down). */
    public static float lift(VehicleEntity vehicle, float partialTick) {
        State s = STATES.get(vehicle.getId());
        return s == null || s.vehicle != vehicle ? 0.0F : Mth.lerp(partialTick, s.liftO, s.lift);
    }

    /** The crew's camera: the landing drop plus the running rumble, blocks. */
    public static float cameraLift(VehicleEntity vehicle, float partialTick) {
        State s = STATES.get(vehicle.getId());
        if (s == null || s.vehicle != vehicle) return 0.0F;
        double top = Math.max(0.1D, vehicle.vehicleData().defaults().engine().maxForwardSpeed() * 0.6D);
        float pace = (float) Mth.clamp(s.lastSpeed / top, 0.0D, 1.0D);
        double phase = Mth.lerp(partialTick, s.phaseO, s.phase);
        // A heavier hull: a deeper, slower heave with less chatter on top.
        float inertia = (float) vehicle.inertia();
        float heave = (float) Math.sin(phase) * 0.016F * pace;
        float chatter = (float) Math.sin(phase * CHATTER_RATIO) * 0.007F * pace * inertia;
        return Mth.lerp(partialTick, s.liftO, s.lift) * 0.7F + heave + chatter;
    }

    /** The crew's camera pitch from the hull's rock, degrees (none through a stabilised sight). */
    public static float cameraPitch(VehicleEntity vehicle, float partialTick) {
        return pitch(vehicle, partialTick) * 0.4F;
    }

    public static float cameraRoll(VehicleEntity vehicle, float partialTick) {
        return roll(vehicle, partialTick) * 0.4F;
    }

    // ---- Sounds.

    /** For game tests: the movement sounds started since the last call, as "entityId:name". */
    public static java.util.List<String> drainEvents() {
        var list = java.util.List.copyOf(EVENTS);
        EVENTS.clear();
        return list;
    }

    private static void event(VehicleEntity vehicle, String name) {
        EVENTS.addLast(vehicle.getId() + ":" + name);
        while (EVENTS.size() > 64) EVENTS.removeFirst();
    }

    private static void oneShot(Minecraft mc, VehicleEntity vehicle, String name, float loudness, float pitch) {
        event(vehicle, name);
        mc.getSoundManager().play(new Sound(vehicle, name, false, loudness, pitch));
    }

    private static Sound loop(Minecraft mc, Sound current, VehicleEntity vehicle, String name, float level, float pitch) {
        if (current != null && (current.isStopped() || current.removed)) current = null;
        if (current == null) {
            if (level <= 0.0F) return null;
            current = new Sound(vehicle, name, true, 1.0F, pitch);
            event(vehicle, name);
            mc.getSoundManager().play(current);
        }
        current.target = level;
        current.targetPitch = pitch;
        return current;
    }

    private static Sound stop(Minecraft mc, Sound sound) {
        if (sound != null) {
            sound.removed = true;
            mc.getSoundManager().stop(sound);
        }
        return null;
    }

    /**
     * One movement sound, following the vehicle: on the camera for its crew in first person, elsewhere at the vehicle on
     * the shared distance curve. A loop fades to its target level and stops itself once silent.
     */
    private static final class Sound extends AbstractTickableSoundInstance implements DynamicRelativeSound {
        final VehicleEntity vehicle;
        final boolean looped;
        final float loudness;
        float target, targetPitch, level;
        boolean removed;
        int age;

        Sound(VehicleEntity vehicle, String name, boolean looped, float loudness, float pitch) {
            super(ModSounds.ALL.get(Reference.id("vehicle.movement." + name)).get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.vehicle = vehicle;
            this.looped = looped;
            this.looping = looped;
            this.loudness = loudness;
            this.attenuation = Attenuation.NONE;
            this.delay = 0;
            this.pitch = pitch;
            this.targetPitch = pitch;
            this.target = looped ? 0.0F : 1.0F;
            this.level = looped ? 0.0F : 1.0F;
            this.volume = 0.001F;
            this.follow();
            this.volume = Math.max(0.001F, this.heard());
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        private float heard() {
            Minecraft mc = Minecraft.getInstance();
            boolean aboard = mc.player != null && mc.player.getRootVehicle() == this.vehicle;
            if (aboard) return this.level * 0.55F * this.loudness;
            double distance = mc.gameRenderer.getMainCamera().position().distanceTo(this.vehicle.position());
            return this.level * AircraftSoundRange.volume(distance, 0.55F * this.loudness);
        }

        private void follow() {
            Minecraft mc = Minecraft.getInstance();
            boolean onCamera = mc.player != null && mc.player.getRootVehicle() == this.vehicle && mc.options.getCameraType().isFirstPerson();
            this.relative = onCamera;
            Vec3 at = onCamera ? Vec3.ZERO : this.vehicle.position().add(0.0D, 1.2D, 0.0D);
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
        }

        @Override
        public void tick() {
            if (this.vehicle.isRemoved() || this.removed) {
                this.stop();
                return;
            }
            if (this.looped) {
                this.level = Mth.lerp(this.target > this.level ? 0.5F : 0.2F, this.level, this.target);
                this.pitch = Mth.lerp(0.3F, this.pitch, this.targetPitch);
                if (this.target <= 0.0F && this.level < 0.01F && ++this.age > 10) {
                    this.removed = true;
                    this.stop();
                    return;
                }
                if (this.target > 0.0F) this.age = 0;
            }
            this.follow();
            this.volume = Math.max(0.001F, this.heard());
        }
    }
}
