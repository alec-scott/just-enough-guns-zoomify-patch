package ttv.migami.jeg.vehicle.client.audio;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.registries.DeferredHolder;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModSounds;

/**
 * How aircraft sound with distance, shared by the sounds of aircraft the client has (JetAudio, HelicopterEngineAudio)
 * and of those only known from the server's tracks (FarAircraftAudio), so the two match where one hands over to the
 * other.
 * <p>
 * An aircraft is heard a long way off, out to {@link #MAX_RANGE}: rather than Minecraft's straight-line fade to silence
 * at a set distance, it fades as sound does, quickly near it and ever more slowly further out, and a louder engine (or
 * gun) carries further. With distance it also sounds further away: the full recording gives way, between
 * {@link #NEAR_LAYER_UNTIL} and {@link #DISTANT_LAYER_FROM} blocks, to a muffled one with its echo off the ground (the
 * {@code _distant} sounds), as air and terrain take the high frequencies out first. Sounds that use this are played with
 * no attenuation of Minecraft's own, the volume set here every tick. (Sound Physics Remastered, in the yftm pack, adds
 * its own air absorption and occlusion on top, as it does to every sound.)
 */
public final class AircraftSoundRange {
    /** Blocks: beyond here an aircraft is not heard at all. The last quarter fades to nothing. */
    public static final double MAX_RANGE = 1024.0D;
    /** Blocks per unit of loudness at which a sound has fallen to about a third of its full volume. */
    private static final double REFERENCE_PER_LOUDNESS = 96.0D;
    private static final double NEAR_LAYER_UNTIL = 40.0D;
    private static final double DISTANT_LAYER_FROM = 240.0D;

    private AircraftSoundRange() {}

    /**
     * Volume (0 to 1) of a sound of this loudness (1 is an ordinary sound at full volume; the A-10's engines flat out are
     * 2.5) heard from this far off.
     */
    public static float volume(double distance, float loudness) {
        if (loudness <= 0.0F || distance >= MAX_RANGE) {
            return 0.0F;
        }
        double reference = REFERENCE_PER_LOUDNESS * Math.max(1.0F, loudness);
        double falloff = Math.pow(reference / (reference + Math.max(0.0D, distance)), 1.5D);
        double edge = Mth.clamp((MAX_RANGE - distance) / (MAX_RANGE * 0.25D), 0.0D, 1.0D);
        return (float) (Math.min(1.0F, loudness) * falloff * edge);
    }

    /** The full recording's share at this distance (equal-power crossfade with {@link #distantShare}). */
    public static float nearShare(double distance) {
        return Mth.cos(crossfade(distance) * Mth.HALF_PI);
    }

    /** The muffled distant recording's share at this distance. */
    public static float distantShare(double distance) {
        return Mth.sin(crossfade(distance) * Mth.HALF_PI);
    }

    private static float crossfade(double distance) {
        return (float) Mth.clamp((distance - NEAR_LAYER_UNTIL) / (DISTANT_LAYER_FROM - NEAR_LAYER_UNTIL), 0.0D, 1.0D);
    }

    /** {@code vehicle.<id>.<name>_distant}, falling back to {@code vehicle.<id>.<name>}; null if neither exists. */
    public static SoundEvent distantSound(String vehicleId, String name) {
        DeferredHolder<SoundEvent, SoundEvent> sound = ModSounds.ALL.get(Reference.id("vehicle." + vehicleId + "." + name + "_distant"));
        if (sound == null) {
            sound = ModSounds.ALL.get(Reference.id("vehicle." + vehicleId + "." + name));
        }
        return sound == null ? null : sound.get();
    }

    /** For game tests: whether this sound is one of the distant ones. */
    public static boolean isDistant(SoundInstance sound) {
        return sound.getIdentifier().getPath().endsWith("_distant");
    }
}
