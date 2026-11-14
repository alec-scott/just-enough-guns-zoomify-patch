package ttv.migami.jeg.vehicle.client.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.client.GunCameraSwayHandler;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;
import ttv.migami.jeg.vehicle.util.VehicleSoundHelper;

/**
 * A jet's sounds on each client, built like the helicopters': the engines' start-up plays as they light, and the engine
 * loop (the A-10's own TF34s) fades in under it and from then on follows the engine spool, its pitch and volume rising
 * as the throttle goes up and falling as it comes back, with Doppler for anyone it passes. In the cockpit the rush of air
 * grows with airspeed; the tyres rumble while it rolls on the ground; anyone it races past low hears a real A-10 flyby;
 * and its pilot hears the stall warble and feels the GAU-8 shake the airframe.
 * <p>
 * From outside, the engines and the gun carry a long way and fade with distance as AircraftSoundRange has it, giving way
 * to muffled distant versions further off; past entity range FarAircraftAudio carries on with those.
 */
public final class JetAudio {
    private static final double AUDIBLE_RANGE_SQR = AircraftSoundRange.MAX_RANGE * AircraftSoundRange.MAX_RANGE;
    /** How loud the GAU-8 is heard from outside, before distance (AircraftSoundRange#volume): it carries further than the engines. */
    static final float GUN_LOUDNESS = 4.0F;
    private static final double FLYBY_RANGE = 64.0D;
    /** The share of its top speed a jet needs to be doing for a flyby. */
    private static final double FLYBY_MIN_SPEED_SHARE = 0.45D;
    private static final int FLYBY_COOLDOWN_TICKS = 80;
    /** Jets whose engines a listener has heard start (cleared once they have stopped again). */
    private static final java.util.Set<Integer> STARTED = new java.util.HashSet<>();
    private static final Map<Integer, Layer> ENGINES = new HashMap<>();
    private static final Map<Integer, Layer> COCKPITS = new HashMap<>();
    private static final Map<Integer, Layer> ROLLS = new HashMap<>();
    /** Outside the jet: its gun as heard from here, a moment after it fired. */
    private static final Map<Integer, Layer> GUNS = new HashMap<>();
    /** Outside the jet: the muffled distant versions of its engines and gun, taking over from the full ones with distance. */
    private static final Map<Integer, Layer> DISTANT_ENGINES = new HashMap<>();
    private static final Map<Integer, Layer> DISTANT_GUNS = new HashMap<>();
    /** Speed of sound, blocks per tick (343 m/s). */
    private static final double SOUND_SPEED = 343.0D / 20.0D;
    private static final int HISTORY_TICKS = 64;
    /** What each jet was doing each of the last few seconds, newest first: sound from it reaches a listener that late. */
    private static final Map<Integer, java.util.ArrayDeque<Snapshot>> HISTORY = new HashMap<>();
    private static Layer pullUpWarning;

    /** Where a jet was, how fast it went, and whether its gun was firing, one tick. */
    private record Snapshot(Vec3 position, Vec3 velocity, boolean firing, int ticksAgo) {}

    /**
     * The jet as heard from {@code listener} now: where it was when the sound arriving now left it. A jet 340 blocks off is
     * heard as it was a second ago, so its roar trails behind it and its gun is heard after its rounds land.
     */
    private static Snapshot heard(VehicleEntity jet, Vec3 listener) {
        java.util.ArrayDeque<Snapshot> history = HISTORY.get(jet.getId());
        if (history == null || history.isEmpty()) {
            return new Snapshot(jet.position(), jet.getDeltaMovement(), jet.isJetGunFiring(), 0);
        }
        Snapshot last = null;
        for (Snapshot snapshot : history) {
            last = snapshot;
            if (snapshot.ticksAgo() * SOUND_SPEED >= snapshot.position().distanceTo(listener)) {
                return snapshot;
            }
        }
        return last;
    }

    /** For game tests: what a listener at the camera hears of this jet now. */
    public static String describe(VehicleEntity jet) {
        Entity listener = Minecraft.getInstance().getCameraEntity();
        Snapshot heard = listener == null ? null : heard(jet, listener.position());
        Layer engine = ENGINES.get(jet.getId());
        Layer gun = GUNS.get(jet.getId());
        Layer distantEngine = DISTANT_ENGINES.get(jet.getId());
        Layer distantGun = DISTANT_GUNS.get(jet.getId());
        return String.format(java.util.Locale.ROOT, "delay=%d engine=%s pitch=%.3f gun=%s gunPitch=%.3f engineVol=%.3f distantEngineVol=%.3f gunVol=%.3f distantGunVol=%.3f",
                heard == null ? -1 : heard.ticksAgo(), engine != null && !engine.isStopped(), engine == null ? 0.0F : engine.getPitch(),
                gun != null && !gun.isStopped(), gun == null ? 0.0F : gun.getPitch(), playingVolume(engine), playingVolume(distantEngine),
                playingVolume(gun), playingVolume(distantGun));
    }

    private static float playingVolume(Layer layer) {
        return layer == null || layer.isStopped() ? 0.0F : layer.getVolume();
    }
    private static final Map<Integer, Float> LAST_SPOOL = new HashMap<>();
    private static final Map<Integer, Double> LAST_DISTANCE = new HashMap<>();
    private static final Map<Integer, Integer> FLYBY_COOLDOWN = new HashMap<>();
    /** For the jet the player flies: its gear position, ticks off the ground and vertical speed, last tick. */
    private static final Map<Integer, Float> LAST_GEAR = new HashMap<>();
    private static final Map<Integer, Integer> AIRBORNE = new HashMap<>();
    private static final Map<Integer, Double> LAST_VERTICAL = new HashMap<>();
    private static Layer stallWarning;

    private JetAudio() {}

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            clear(minecraft);
            return;
        }
        prune(minecraft, ENGINES);
        prune(minecraft, COCKPITS);
        prune(minecraft, ROLLS);
        prune(minecraft, GUNS);
        prune(minecraft, DISTANT_ENGINES);
        prune(minecraft, DISTANT_GUNS);
        FLYBY_COOLDOWN.replaceAll((id, ticks) -> ticks - 1);
        FLYBY_COOLDOWN.values().removeIf(ticks -> ticks <= 0);
        for (Entity entity : minecraft.level.entitiesForRendering()) {
            if (!(entity instanceof VehicleEntity jet) || !jet.isJet() || !inRange(minecraft, jet)) {
                continue;
            }
            java.util.ArrayDeque<Snapshot> history = HISTORY.computeIfAbsent(jet.getId(), id -> new java.util.ArrayDeque<>());
            java.util.ArrayDeque<Snapshot> aged = new java.util.ArrayDeque<>();
            aged.add(new Snapshot(jet.position(), jet.getDeltaMovement(), jet.isJetGunFiring(), 0));
            for (Snapshot snapshot : history) {
                if (snapshot.ticksAgo() + 1 < HISTORY_TICKS) {
                    aged.add(new Snapshot(snapshot.position(), snapshot.velocity(), snapshot.firing(), snapshot.ticksAgo() + 1));
                }
            }
            HISTORY.put(jet.getId(), aged);
            if (!aboard(jet)) {
                tickHeardGun(minecraft, jet);
            }
            float spool = jet.jetSpool();
            float lastSpool = LAST_SPOOL.getOrDefault(jet.getId(), spool);
            LAST_SPOOL.put(jet.getId(), spool);
            if (spool <= 5.0E-4F) {
                STARTED.remove(jet.getId());
            } else if (spool > lastSpool && lastSpool < 0.04F && STARTED.add(jet.getId())) {
                // Lighting up from cold: once, as the spool starts to climb.
                playAt(minecraft, jet, aboard(jet) ? VehicleSoundHelper.jetCockpitSound(jet, "engine_start") : VehicleSoundHelper.jetSound(jet, "engine_start"),
                        aboard(jet) ? 0.85F : 2.5F, 1.0F);
            }
            // Aboard, every layer is the cockpit version: the same sound muffled through the airframe.
            ensure(minecraft, ENGINES, jet, aboard(jet) ? VehicleSoundHelper.jetCockpitSound(jet, "engine") : VehicleSoundHelper.engineSound(jet),
                    JetAudio::engineRunning, Kind.ENGINE);
            if (!aboard(jet)) {
                ensure(minecraft, DISTANT_ENGINES, jet, AircraftSoundRange.distantSound(jet.vehicleDataId().getPath(), "engine"),
                        JetAudio::engineRunning, Kind.DISTANT_ENGINE);
            }
            if (aboard(jet)) {
                ensure(minecraft, COCKPITS, jet, VehicleSoundHelper.jetSound(jet, "cockpit_flight"), JetAudio::flying, Kind.COCKPIT);
            }
            ensure(minecraft, ROLLS, jet, aboard(jet) ? VehicleSoundHelper.jetCockpitSound(jet, "runway_roll") : VehicleSoundHelper.jetSound(jet, "runway_roll"),
                    JetAudio::rolling, Kind.ROLL);
            tickFlyby(minecraft, jet);
            if (jet.getControllingPassenger() == minecraft.player) {
                tickPilotEvents(minecraft, jet);
            }
        }
        VehicleEntity riding = minecraft.player.getVehicle() instanceof VehicleEntity jet && jet.isJet()
                && jet.getControllingPassenger() == minecraft.player ? jet : null;
        if (riding != null && riding.isJetGunFiring()) {
            // The GAU-8 shakes the whole airframe.
            GunCameraSwayHandler.addShake(0.13F);
        }
        boolean pullUp = riding != null && riding.jetPullUpWarning();
        if (pullUp && (pullUpWarning == null || pullUpWarning.isStopped() || pullUpWarning.jet != riding)) {
            SoundEvent sound = VehicleSoundHelper.jetSound(riding, "pull_up");
            if (sound != null) {
                pullUpWarning = new Layer(riding, sound, Kind.STALL, VehicleEntity::jetPullUpWarning);
                minecraft.getSoundManager().play(pullUpWarning);
            }
        } else if (!pullUp && pullUpWarning != null) {
            minecraft.getSoundManager().stop(pullUpWarning);
            pullUpWarning = null;
        }
        boolean warn = riding != null && riding.jetStallWarning() && !pullUp;
        if (warn && (stallWarning == null || stallWarning.isStopped() || stallWarning.jet != riding)) {
            SoundEvent sound = VehicleSoundHelper.jetSound(riding, "stall_warning");
            if (sound != null) {
                stallWarning = new Layer(riding, sound, Kind.STALL, VehicleEntity::jetStallWarning);
                minecraft.getSoundManager().play(stallWarning);
            }
        } else if (!warn && stallWarning != null) {
            minecraft.getSoundManager().stop(stallWarning);
            stallWarning = null;
        }
    }

    /**
     * The gun from outside: the BRRRT starts and stops as the gun did when that sound left it, so a distant listener sees
     * the rounds land and hears the gun after, and the roar rolls away from where the burst ended.
     */
    private static void tickHeardGun(Minecraft minecraft, VehicleEntity jet) {
        Entity listener = minecraft.getCameraEntity();
        if (listener == null) {
            return;
        }
        Snapshot heard = heard(jet, listener.position());
        Layer playing = GUNS.get(jet.getId());
        boolean sounding = playing != null && !playing.isStopped();
        if (heard.firing() && !sounding) {
            SoundEvent sound = VehicleSoundHelper.jetSound(jet, "fire");
            if (sound != null) {
                Layer gun = new Layer(jet, sound, Kind.GUN, ignored -> true);
                GUNS.put(jet.getId(), gun);
                minecraft.getSoundManager().play(gun);
            }
            SoundEvent distant = AircraftSoundRange.distantSound(jet.vehicleDataId().getPath(), "fire");
            if (distant != null) {
                Layer gun = new Layer(jet, distant, Kind.DISTANT_GUN, ignored -> true);
                Layer previous = DISTANT_GUNS.put(jet.getId(), gun);
                if (previous != null) {
                    minecraft.getSoundManager().stop(previous);
                }
                minecraft.getSoundManager().play(gun);
            }
        } else if (!heard.firing() && sounding) {
            minecraft.getSoundManager().stop(playing);
            GUNS.remove(jet.getId());
            Layer distant = DISTANT_GUNS.remove(jet.getId());
            if (distant != null) {
                minecraft.getSoundManager().stop(distant);
            }
            SoundEvent tail = VehicleSoundHelper.jetSound(jet, "gun_stop");
            Vec3 at = heard.position();
            double distance = at.distanceTo(listener.position());
            float volume = AircraftSoundRange.volume(distance, GUN_LOUDNESS) * AircraftSoundRange.nearShare(distance);
            if (tail != null && volume > 0.005F) {
                minecraft.getSoundManager().play(new SimpleSoundInstance(tail.location(), SoundSource.PLAYERS, volume, 1.0F, RandomSource.create(),
                        false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false));
            }
        }
    }

    /** Someone on the ground (or in another aircraft) the jet races past low hears a real A-10 go by. */
    private static void tickFlyby(Minecraft minecraft, VehicleEntity jet) {
        Entity listener = minecraft.getCameraEntity();
        if (listener == null || listener.getRootVehicle() == jet) {
            return;
        }
        double distance = listener.distanceTo(jet);
        Double last = LAST_DISTANCE.put(jet.getId(), distance);
        double speed = jet.getDeltaMovement().length();
        // Play it just before the closest approach, so the loudest part lands as it passes.
        Vec3 toListener = listener.position().subtract(jet.position());
        double closingTicks = speed > 0.1D ? toListener.dot(jet.getDeltaMovement()) / (speed * speed) : -1.0D;
        double share = speed / jet.jetTopSpeed();
        if (last == null || distance > FLYBY_RANGE || share < FLYBY_MIN_SPEED_SHARE || FLYBY_COOLDOWN.containsKey(jet.getId())
                || closingTicks < 0.0D || closingTicks > 20.0D) {
            return;
        }
        float volume = (float) Mth.clamp(3.0D * (1.0D - distance / (FLYBY_RANGE * 1.5D)) * (share / 0.9D), 0.6D, 4.0D);
        playAt(minecraft, jet, VehicleSoundHelper.jetSound(jet, "flyby"), volume, 0.95F + jet.getRandom().nextFloat() * 0.1F);
        FLYBY_COOLDOWN.put(jet.getId(), FLYBY_COOLDOWN_TICKS);
    }

    private static void playAt(Minecraft minecraft, VehicleEntity jet, SoundEvent sound, float volume, float pitch) {
        if (sound == null) {
            return;
        }
        minecraft.getSoundManager().play(aboard(jet)
                ? SimpleSoundInstance.forUI(sound, pitch, volume)
                : new SimpleSoundInstance(sound, SoundSource.AMBIENT, volume, pitch, RandomSource.create(), jet.getX(), jet.getY() + 2.5D, jet.getZ()));
    }

    public static void clear(Minecraft minecraft) {
        for (Map<Integer, Layer> layers : java.util.List.of(ENGINES, COCKPITS, ROLLS, GUNS, DISTANT_ENGINES, DISTANT_GUNS)) {
            layers.values().forEach(layer -> minecraft.getSoundManager().stop(layer));
            layers.clear();
        }
        LAST_SPOOL.clear();
        HISTORY.clear();
        if (pullUpWarning != null) {
            minecraft.getSoundManager().stop(pullUpWarning);
            pullUpWarning = null;
        }
        STARTED.clear();
        LAST_DISTANCE.clear();
        LAST_GEAR.clear();
        AIRBORNE.clear();
        LAST_VERTICAL.clear();
        FLYBY_COOLDOWN.clear();
        if (stallWarning != null) {
            minecraft.getSoundManager().stop(stallWarning);
            stallWarning = null;
        }
    }

    private static boolean engineRunning(VehicleEntity jet) {
        return jet.jetSpool() > 0.003F;
    }

    private static boolean flying(VehicleEntity jet) {
        return jet.getDeltaMovement().length() > 0.3D;
    }

    private static boolean rolling(VehicleEntity jet) {
        return jet.onGround() && jet.getDeltaMovement().horizontalDistance() > 0.04D;
    }

    private static boolean inRange(Minecraft minecraft, VehicleEntity jet) {
        return jet.isAlive() && !jet.isRemoved() && !jet.isWrecked() && minecraft.player.distanceToSqr(jet) <= AUDIBLE_RANGE_SQR;
    }

    private static boolean aboard(VehicleEntity jet) {
        return inCockpit(jet);
    }

    /**
     * Whether the player hears this jet from inside its cockpit: riding it and looking out of it (first person, the
     * targeting pod included). From a third-person camera it sounds as it does outside.
     */
    public static boolean inCockpit(VehicleEntity jet) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.player.getRootVehicle() == jet && minecraft.options.getCameraType().isFirstPerson();
    }

    /**
     * The gear and touchdown of the jet the player flies, which the server leaves to the pilot's client: the cockpit
     * versions from the cockpit, the outside ones from a chase camera.
     */
    private static void tickPilotEvents(Minecraft minecraft, VehicleEntity jet) {
        float gear = jet.jetGear();
        Float lastGear = LAST_GEAR.put(jet.getId(), gear);
        if (lastGear != null) {
            if (lastGear >= 0.999F && gear < lastGear) {
                playPilot(minecraft, jet, "gear", 1.6F, 1.0F);
            } else if (lastGear <= 0.001F && gear > lastGear) {
                playPilot(minecraft, jet, "gear", 1.6F, 0.9F);
            }
        }
        boolean grounded = jet.onGround();
        int airborne = AIRBORNE.getOrDefault(jet.getId(), 0);
        double sink = Math.max(0.0D, -LAST_VERTICAL.getOrDefault(jet.getId(), 0.0D));
        double scale = jet.jetTopSpeed() / 3.4D;
        if (grounded && airborne >= 3 && jet.getDeltaMovement().horizontalDistance() > 0.5D * scale) {
            float volume = (float) Mth.clamp(0.8D + jet.getDeltaMovement().horizontalDistance() / scale * 0.6D + sink / Math.max(0.4D, scale) * 2.0D, 0.8D, 3.0D);
            playPilot(minecraft, jet, "touchdown", volume, 0.9F + jet.getRandom().nextFloat() * 0.2F);
        }
        AIRBORNE.put(jet.getId(), grounded ? 0 : airborne + 1);
        LAST_VERTICAL.put(jet.getId(), jet.getDeltaMovement().y);
    }

    private static void playPilot(Minecraft minecraft, VehicleEntity jet, String name, float volume, float pitch) {
        SoundEvent sound = inCockpit(jet) ? VehicleSoundHelper.jetCockpitSound(jet, name) : VehicleSoundHelper.jetSound(jet, name);
        playAt(minecraft, jet, sound, inCockpit(jet) ? Math.min(1.0F, volume) : volume, pitch);
    }

    private static void prune(Minecraft minecraft, Map<Integer, Layer> layers) {
        layers.entrySet().removeIf(entry -> {
            Layer layer = entry.getValue();
            if (layer.isStopped() || layer.jet.level() != minecraft.level || !inRange(minecraft, layer.jet)
                    || layer.relativeMode() != aboard(layer.jet)) {
                minecraft.getSoundManager().stop(layer);
                return true;
            }
            return false;
        });
    }

    private static void ensure(Minecraft minecraft, Map<Integer, Layer> layers, VehicleEntity jet, SoundEvent sound,
                               Predicate<VehicleEntity> active, Kind kind) {
        if (sound == null || layers.containsKey(jet.getId()) || !active.test(jet)) {
            return;
        }
        Layer layer = new Layer(jet, sound, kind, active);
        layers.put(jet.getId(), layer);
        minecraft.getSoundManager().play(layer);
    }

    private enum Kind {
        ENGINE, COCKPIT, ROLL, STALL, GUN, DISTANT_ENGINE, DISTANT_GUN;

        boolean gun() {
            return this == GUN || this == DISTANT_GUN;
        }
    }

    private static final class Layer extends AbstractTickableSoundInstance {
        private final VehicleEntity jet;
        private final Kind kind;
        private final Predicate<VehicleEntity> active;
        private float gain = 0.001F;

        Layer(VehicleEntity jet, SoundEvent sound, Kind kind, Predicate<VehicleEntity> active) {
            super(sound, kind == Kind.STALL || kind.gun() ? SoundSource.PLAYERS : SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.jet = jet;
            this.kind = kind;
            this.active = active;
            this.looping = true;
            this.delay = 0;
            this.relative = kind == Kind.STALL || kind == Kind.COCKPIT || (kind == Kind.ENGINE || kind == Kind.ROLL) && aboard(jet);
            // The engines and gun outside set their own fade with distance (AircraftSoundRange); the tyres fade as usual.
            this.attenuation = this.relative || kind != Kind.ROLL ? Attenuation.NONE : Attenuation.LINEAR;
            // SoundEngine discards a sound that opens silent; start barely audible and fade in.
            this.volume = 0.001F;
            this.update();
        }

        boolean relativeMode() {
            return this.kind != Kind.STALL && this.relative;
        }

        @Override
        public void tick() {
            if (this.jet.isRemoved()) {
                this.stop();
                return;
            }
            boolean on = this.active.test(this.jet);
            this.update();
            if (!on && this.gain <= 0.01F) {
                this.stop();
            }
        }

        private void update() {
            // Outside the jet each sound comes from where the jet was when it set off, with that moment's Doppler.
            Entity listener = Minecraft.getInstance().getCameraEntity();
            Snapshot heard = !this.relative && listener != null ? heard(this.jet, listener.position()) : null;
            Vec3 source = heard == null ? this.jet.position() : heard.position();
            Vec3 sourceVelocity = heard == null ? this.jet.getDeltaMovement() : heard.velocity();
            this.x = this.relative ? 0.0D : source.x;
            this.y = this.relative ? 0.0D : source.y + 2.5D;
            this.z = this.relative ? 0.0D : source.z;
            float doppler = 1.0F;
            if (heard != null && listener.getRootVehicle() != this.jet) {
                doppler = VehicleDoppler.multiplier(source, sourceVelocity, listener.position(), listener.getRootVehicle().getDeltaMovement());
            }
            double speed = this.jet.getDeltaMovement().length();
            double distance = listener == null ? 0.0D : listener.position().distanceTo(new Vec3(this.x, this.y, this.z));
            float loudness = VehicleSoundHelper.aircraftEngineLoudness(this.jet);
            float target;
            float pitch;
            switch (this.kind) {
                case ENGINE -> {
                    // The recording is the TF34s at full power: idle plays it at 0.62 and full power at 1.0. While the
                    // engines wind up to idle the loop fades in under the start-up sound.
                    target = this.relative ? loudness * 0.42F
                            : AircraftSoundRange.volume(distance, loudness) * AircraftSoundRange.nearShare(distance);
                    pitch = VehicleSoundHelper.aircraftEnginePitch(this.jet);
                }
                case DISTANT_ENGINE -> {
                    target = AircraftSoundRange.volume(distance, loudness) * AircraftSoundRange.distantShare(distance);
                    pitch = VehicleSoundHelper.aircraftEnginePitch(this.jet);
                }
                case COCKPIT -> {
                    float airspeed = (float) Mth.clamp(speed / this.jet.jetTopSpeed(), 0.0D, 1.3D);
                    // The air roars louder over the canopy in a hard bank.
                    float bank = Mth.clamp((Math.abs(Mth.wrapDegrees(this.jet.roll())) - 30.0F) / 50.0F, 0.0F, 1.0F);
                    target = 0.15F + 0.75F * airspeed * airspeed + 0.35F * bank * airspeed;
                    pitch = 0.85F + 0.25F * airspeed;
                }
                case ROLL -> {
                    float rolling = (float) Mth.clamp(this.jet.getDeltaMovement().horizontalDistance() / (0.6D * this.jet.jetTopSpeed()), 0.0D, 1.0D);
                    target = this.jet.onGround() ? (0.3F + 1.6F * rolling) * (this.relative ? 0.75F : 1.0F) : 0.0F;
                    pitch = 0.7F + 0.6F * rolling;
                }
                case GUN -> {
                    target = AircraftSoundRange.volume(distance, GUN_LOUDNESS) * AircraftSoundRange.nearShare(distance);
                    pitch = 1.0F;
                }
                case DISTANT_GUN -> {
                    target = AircraftSoundRange.volume(distance, GUN_LOUDNESS) * AircraftSoundRange.distantShare(distance);
                    pitch = 1.0F;
                }
                default -> {
                    target = 0.7F;
                    pitch = 1.0F;
                }
            }
            if (!this.active.test(this.jet)) {
                target = 0.0F;
            }
            this.gain = this.kind.gun() ? target : Mth.lerp(this.kind == Kind.ROLL ? 0.3F : 0.12F, this.gain, target);
            this.volume = Math.max(0.001F, this.gain);
            this.pitch = Mth.clamp(pitch * (this.kind == Kind.STALL || this.kind == Kind.COCKPIT ? 1.0F : doppler), 0.5F, 2.0F);
        }
    }
}
