package ttv.migami.jeg.vehicle.client.audio;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModSounds;
import ttv.migami.jeg.vehicle.entity.Hpj11Entity;

/**
 * The HPJ-11's gun as it is heard (design/hpj11/prepare_audio.py; real CIWS recordings): the barrels spooling up, the
 * firing loop up close (a Phalanx) and from far off (a C-RAM at a distance), mixed on the aircraft distance curve so
 * the gun carries as far as a helicopter's, the burst's ring-out when it stops, and the barrels running down. It
 * replaces the generic vehicle fire loop for the HPJ-11 (FabricClientBootstrap skips it), which faded out by 56 blocks
 * and simply cut off.
 */
public final class CiwsAudio {
    /** How loud the gun is before distance (helicopter guns are 2.5): a 30 mm rotary at thousands of rounds a minute. */
    private static final float LOUDNESS = 3.0F;
    /** Ticks the loop holds through a gap in the synced firing flag before the gun counts as stopped. */
    private static final int HOLD_TICKS = 3;
    private static final Map<Integer, State> STATES = new HashMap<>();
    /** For game tests: the sounds started lately, as "entityId:name". */
    private static final ArrayDeque<String> EVENTS = new ArrayDeque<>();

    private CiwsAudio() {}

    private static final class State {
        Loop near, far;
        int sinceFiring = 99;
        float lastSpin;
        boolean spinningDown;
    }

    public static void tick(Minecraft mc) {
        Entity camera = mc.getCameraEntity();
        if (mc.level == null || camera == null) {
            STATES.values().forEach(CiwsAudio::stopLoops);
            STATES.clear();
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Hpj11Entity ciws)) continue;
            double distance = camera.position().distanceTo(ciws.position());
            if (distance >= AircraftSoundRange.MAX_RANGE) continue;
            seen.add(ciws.getId());
            State s = STATES.computeIfAbsent(ciws.getId(), id -> new State());
            float volume = AircraftSoundRange.volume(distance, LOUDNESS);
            float near = volume * AircraftSoundRange.nearShare(distance);
            float far = volume * AircraftSoundRange.distantShare(distance);

            s.sinceFiring = ciws.isWeaponFiring() && !ciws.isWrecked() ? 0 : s.sinceFiring + 1;
            if (s.sinceFiring <= HOLD_TICKS) {
                s.near = loop(mc, s.near, ciws, "fire", near);
                s.far = loop(mc, s.far, ciws, "fire_distant", far);
            } else if (s.near != null || s.far != null) {
                stopLoops(s);
                oneShot(mc, ciws, "fire_stop", near);
                oneShot(mc, ciws, "fire_stop_distant", far);
            }

            // The barrels: a motor winding up from rest, and running down from full speed (heard close by only).
            float spin = ciws.rotaryGunSpin();
            if (spin > 0.05F && s.lastSpin <= 0.05F) {
                oneShot(mc, ciws, "spin_up", near * 0.9F);
                s.spinningDown = false;
            } else if (spin > s.lastSpin) {
                s.spinningDown = false;
            } else if (spin < s.lastSpin && !s.spinningDown && s.lastSpin > 0.5F) {
                oneShot(mc, ciws, "spin_down", near * 0.9F);
                s.spinningDown = true;
            }
            s.lastSpin = spin;
        }
        STATES.entrySet().removeIf(e -> {
            if (seen.contains(e.getKey())) return false;
            stopLoops(e.getValue());
            return true;
        });
    }

    /** For game tests: the sounds started since the last call, as "entityId:name". */
    public static List<String> drainEvents() {
        var list = List.copyOf(EVENTS);
        EVENTS.clear();
        return list;
    }

    private static void event(Entity vehicle, String name) {
        EVENTS.addLast(vehicle.getId() + ":" + name);
        while (EVENTS.size() > 64) EVENTS.removeFirst();
    }

    private static SoundEvent sound(String name) {
        var holder = ModSounds.ALL.get(Reference.id("vehicle.hpj11." + name));
        return holder == null ? null : holder.get();
    }

    private static Loop loop(Minecraft mc, Loop current, Hpj11Entity ciws, String name, float volume) {
        if (current != null && !current.isStopped()) {
            current.level = volume;
            return current;
        }
        SoundEvent sound = sound(name);
        if (sound == null) return null;
        Loop loop = new Loop(ciws, sound, volume);
        event(ciws, name);
        mc.getSoundManager().play(loop);
        return loop;
    }

    private static void oneShot(Minecraft mc, Hpj11Entity ciws, String name, float volume) {
        SoundEvent sound = sound(name);
        if (sound == null || volume < 0.005F) return;
        event(ciws, name);
        Vec3 at = ciws.position().add(0.0D, 1.5D, 0.0D);
        mc.getSoundManager().play(new SimpleSoundInstance(sound.location(), SoundSource.PLAYERS, volume, 1.0F,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false));
    }

    private static void stopLoops(State s) {
        if (s.near != null) s.near.end();
        if (s.far != null) s.far.end();
        s.near = null;
        s.far = null;
    }

    /** A loop at the mount, its level set from outside each tick (distance is applied by the caller). */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final Hpj11Entity ciws;
        float level;

        Loop(Hpj11Entity ciws, SoundEvent sound, float level) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.ciws = ciws;
            this.level = level;
            this.looping = true;
            this.delay = 0;
            this.attenuation = Attenuation.NONE;
            this.volume = Math.max(0.001F, level);
            this.x = ciws.getX();
            this.y = ciws.getY() + 1.5D;
            this.z = ciws.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        void end() {
            this.stop();
        }

        @Override
        public void tick() {
            if (this.ciws.isRemoved()) {
                this.stop();
                return;
            }
            this.volume = Math.max(0.001F, this.level);
            this.x = this.ciws.getX();
            this.y = this.ciws.getY() + 1.5D;
            this.z = this.ciws.getZ();
        }
    }
}
