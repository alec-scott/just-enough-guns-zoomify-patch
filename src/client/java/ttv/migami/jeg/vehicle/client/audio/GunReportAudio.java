package ttv.migami.jeg.vehicle.client.audio;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.init.ModSounds;
import ttv.migami.jeg.network.GunReportPayload;

/**
 * A gun's report (the mortar, the Mk42), heard as the aircraft guns are (guide section 7): on the shared distance curve
 * out to 1024 blocks, the full recording crossfading into the muffled distant one, and arriving a tick per 17 blocks
 * after the shot. The gun's own crew hears its interior recording at once, centred, when it has one.
 */
public final class GunReportAudio {
    private static final double SOUND_SPEED = 17.15D;
    private static final List<Pending> PENDING = new ArrayList<>();
    /** For game tests: reports played, and the volume of the last near, distant and interior layers. */
    private static int played;
    private static float lastNear;
    private static float lastDistant;
    private static float lastInterior;

    private GunReportAudio() {}

    public static void report(GunReportPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.getCameraEntity() == null) {
            return;
        }
        ttv.migami.jeg.client.render.MissileBlastRenderer.gunFired(payload);
        if (minecraft.player != null && minecraft.player.getVehicle() != null
                && minecraft.player.getVehicle().getId() == payload.gunId()
                && ModSounds.ALL.containsKey(Reference.id(payload.sound() + ".fire_interior"))) {
            played++;
            lastNear = 0.0F;
            lastDistant = 0.0F;
            lastInterior = 1.0F;
            var sound = ModSounds.ALL.get(Reference.id(payload.sound() + ".fire_interior")).get();
            float pitch = 0.96F + minecraft.level.getRandom().nextFloat() * 0.08F;
            minecraft.getSoundManager().play(new SimpleSoundInstance(sound.location(), SoundSource.PLAYERS, lastInterior, pitch,
                    SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true));
            return;
        }
        double distance = minecraft.getCameraEntity().position().distanceTo(payload.muzzle());
        long due = minecraft.level.getGameTime() + Math.round(distance / SOUND_SPEED);
        PENDING.add(new Pending(payload, due));
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null) {
            PENDING.clear();
            return;
        }
        long now = minecraft.level.getGameTime();
        PENDING.removeIf(pending -> {
            if (pending.due > now) {
                return false;
            }
            play(minecraft, pending.report);
            return true;
        });
    }

    private static void play(Minecraft minecraft, GunReportPayload report) {
        if (minecraft.getCameraEntity() == null) {
            return;
        }
        Vec3 muzzle = report.muzzle();
        double distance = minecraft.getCameraEntity().position().distanceTo(muzzle);
        float volume = AircraftSoundRange.volume(distance, report.loudness());
        lastNear = volume * AircraftSoundRange.nearShare(distance);
        lastDistant = volume * AircraftSoundRange.distantShare(distance);
        lastInterior = 0.0F;
        played++;
        layer(minecraft, report.sound() + ".fire", lastNear, muzzle);
        layer(minecraft, report.sound() + ".fire_distant", lastDistant, muzzle);
    }

    private static void layer(Minecraft minecraft, String name, float volume, Vec3 at) {
        var holder = ModSounds.ALL.get(Reference.id(name));
        if (holder == null || volume < 0.005F) {
            return;
        }
        SoundEvent sound = holder.get();
        float pitch = 0.96F + minecraft.level.getRandom().nextFloat() * 0.08F;
        minecraft.getSoundManager().play(new SimpleSoundInstance(sound.location(), SoundSource.PLAYERS, volume, pitch,
                SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, at.x, at.y, at.z, false));
    }

    public static int played() {
        return played;
    }

    public static float lastNear() {
        return lastNear;
    }

    public static float lastDistant() {
        return lastDistant;
    }

    public static float lastInterior() {
        return lastInterior;
    }

    private record Pending(GunReportPayload report, long due) {}
}
