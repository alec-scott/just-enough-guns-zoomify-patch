package ttv.migami.jeg.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.client.render.gun.GunMuzzleTracker;
import ttv.migami.jeg.item.GunItem;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.gun.MinigunSpin;
import ttv.migami.jeg.init.ModSounds;

/**
 * Barrel speed and sound for every minigun nearby. The barrels, the charge-up sound and the first shot all run off the one
 * spin value, so they stay in step: the charge-up sample is exactly as long as the spin-up.
 */
public final class MinigunClient {
    private static final float MAX_BARREL_DEGREES_PER_TICK = 54.0F;
    private static final float WHIR_VOLUME = 0.3F;
    private static final float FIRE_VOLUME = MinigunSpin.FIRE_VOLUME;
    private static final float WHIR_MIN_PITCH = 0.55F;
    // Below this the barrels are as good as stopped, so starting again plays the full charge-up.
    private static final float RESTART_FROM_STOPPED = 0.15F;
    private static final float VENT_VOLUME = 0.6F;
    /** The hiss dies away over this many ticks when the cooling ends before the sample does. */
    private static final int VENT_FADE_TICKS = 6;
    /** Puffs of gas a cooling gun lets off each tick. */
    private static final int VENT_PUFFS_PER_TICK = 3;
    /** The gas leaves the barrels up to this far (blocks) behind the muzzle. */
    private static final double VENT_BEHIND_MUZZLE = 0.3D;
    /** Puffs of vented gas spawned so far, for checks. */
    public static int ventPuffs;
    private static final Map<Integer, Barrels> BARRELS = new HashMap<>();

    private MinigunClient() {}

    private enum Audio { SILENT, SPOOL, WHIR, FIRE }

    private static final class Barrels {
        float spin;
        float spinO;
        float angle;
        float angleO;
        boolean wanted;
        boolean firing;
        boolean updatedThisTick;
        Audio audio = Audio.SILENT;
        MinigunSound loop;
        MinigunSound oneShot;
        /** The hiss of barrels being run cool (the reload key), while it lasts. */
        MinigunSound vent;
    }

    private static final class MinigunSound extends AbstractTickableSoundInstance {
        private final Entity entity;
        private float fadePerTick;

        MinigunSound(SoundEvent event, Entity entity, boolean looping, float volume) {
            super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.entity = entity;
            this.looping = looping;
            this.delay = 0;
            this.volume = volume;
            this.pitch = 1.0F;
            // A world-space source at the shooter's last tick position moves around the listener as they turn.
            this.relative = entity == Minecraft.getInstance().player;
            this.attenuation = this.relative ? Attenuation.NONE : Attenuation.LINEAR;
            this.follow();
        }

        void setPitch(float pitch) {
            this.pitch = pitch;
        }

        void halt() {
            this.stop();
        }

        /** Dies away over the given number of ticks, then stops. */
        void fadeOut(int ticks) {
            if (this.fadePerTick <= 0.0F) {
                this.fadePerTick = this.volume / ticks;
            }
        }

        @Override
        public void tick() {
            if (this.entity.isRemoved()) {
                this.stop();
                return;
            }
            if (this.fadePerTick > 0.0F) {
                this.volume -= this.fadePerTick;
                if (this.volume <= 0.0F) {
                    this.volume = 0.0F;
                    this.stop();
                    return;
                }
            }
            this.follow();
        }

        private void follow() {
            this.x = this.relative ? 0.0D : this.entity.getX();
            this.y = this.relative ? 0.0D : this.entity.getEyeY();
            this.z = this.relative ? 0.0D : this.entity.getZ();
        }
    }

    /**
     * Runs the local player's minigun for this tick. Returns true once the barrels are up to speed.
     *
     * @param wanted the trigger or aim is held
     * @param trigger the trigger is held and the gun can actually fire
     */
    public static boolean updateLocal(LocalPlayer player, boolean wanted, boolean trigger) {
        Barrels barrels = BARRELS.computeIfAbsent(player.getId(), id -> new Barrels());
        barrels.wanted = wanted;
        barrels.firing = trigger && barrels.spin >= 1.0F;
        barrels.updatedThisTick = true;
        if (wanted) {
            // Too much gun to run with while it is spinning.
            player.setSprinting(false);
        }
        return barrels.spin >= 1.0F;
    }

    public static void onRemoteState(int entityId, byte state) {
        Barrels barrels = BARRELS.computeIfAbsent(entityId, id -> new Barrels());
        barrels.wanted = state != MinigunSpin.STATE_IDLE;
        barrels.firing = state == MinigunSpin.STATE_FIRING;
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            for (Barrels barrels : BARRELS.values()) {
                haltAll(minecraft, barrels);
            }
            BARRELS.clear();
            return;
        }

        int localId = minecraft.player.getId();
        Iterator<Map.Entry<Integer, Barrels>> iterator = BARRELS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Barrels> entry = iterator.next();
            Barrels barrels = entry.getValue();
            Entity entity = minecraft.level.getEntity(entry.getKey());
            if (entity == null || entity.isRemoved()) {
                haltAll(minecraft, barrels);
                if (barrels.vent != null) {
                    barrels.vent.halt();
                }
                iterator.remove();
                continue;
            }
            if (entry.getKey() == localId && !barrels.updatedThisTick) {
                // Put the gun away: stop asking for it to spin.
                barrels.wanted = false;
                barrels.firing = false;
            }
            barrels.updatedThisTick = false;

            barrels.spinO = barrels.spin;
            barrels.spin = barrels.wanted
                    ? Math.min(1.0F, barrels.spin + 1.0F / MinigunSpin.SPOOL_TICKS)
                    : Math.max(0.0F, barrels.spin - 1.0F / MinigunSpin.SPIN_DOWN_TICKS);
            barrels.angleO = barrels.angle;
            barrels.angle += barrels.spin * barrels.spin * MAX_BARREL_DEGREES_PER_TICK;
            if (barrels.angle > 360.0F) {
                barrels.angle -= 360.0F;
                barrels.angleO -= 360.0F;
            }
            tickAudio(minecraft, entity, barrels);
            tickVent(minecraft, entity, barrels);
            if (!barrels.wanted && barrels.spin <= 0.0F && barrels.audio == Audio.SILENT
                    && (barrels.oneShot == null || barrels.oneShot.isStopped() || !minecraft.getSoundManager().isActive(barrels.oneShot))) {
                iterator.remove();
            }
        }
    }

    private static void tickAudio(Minecraft minecraft, Entity entity, Barrels barrels) {
        Audio target;
        if (barrels.firing) {
            target = Audio.FIRE;
        } else if (!barrels.wanted) {
            target = Audio.SILENT;
        } else if (barrels.audio == Audio.SPOOL && barrels.spin < 1.0F) {
            target = Audio.SPOOL;
        } else if (barrels.audio == Audio.SILENT && barrels.spinO <= RESTART_FROM_STOPPED) {
            target = barrels.spin < 1.0F ? Audio.SPOOL : Audio.WHIR;
        } else {
            target = Audio.WHIR;
        }

        if (target != barrels.audio) {
            Audio previous = barrels.audio;
            haltAll(minecraft, barrels);
            switch (target) {
                case SPOOL -> barrels.oneShot = play(minecraft, "item.minigun.spool_up", entity, false, WHIR_VOLUME);
                case WHIR -> barrels.loop = play(minecraft, "item.minigun.spin_loop", entity, true, WHIR_VOLUME);
                case FIRE -> barrels.loop = play(minecraft, "item.minigun.fire_loop", entity, true, FIRE_VOLUME);
                case SILENT -> {
                    if (previous == Audio.FIRE) {
                        barrels.oneShot = play(minecraft, "item.minigun.fire_stop", entity, false, FIRE_VOLUME);
                    } else if (barrels.spin > RESTART_FROM_STOPPED) {
                        barrels.oneShot = play(minecraft, "item.minigun.spin_down", entity, false, WHIR_VOLUME);
                    }
                }
            }
            barrels.audio = target;
        }

        if (barrels.audio == Audio.WHIR && barrels.loop != null) {
            barrels.loop.setPitch(Mth.lerp(barrels.spin * barrels.spin, WHIR_MIN_PITCH, 1.0F));
        }
    }

    /**
     * A minigun being run cool on the reload key hisses and lets gas off its barrels. Read off the held gun, which
     * every client is sent, so it shows on other players as well.
     */
    private static void tickVent(Minecraft minecraft, Entity entity, Barrels barrels) {
        boolean main = false;
        boolean off = false;
        if (entity instanceof LivingEntity living) {
            main = GunItem.isRapidCooling(living.getMainHandItem());
            off = MinigunSpin.isDualWielding(living) && GunItem.isRapidCooling(living.getOffhandItem());
        }
        if (!main && !off) {
            if (barrels.vent != null) {
                barrels.vent.fadeOut(VENT_FADE_TICKS);
                barrels.vent = null;
            }
            return;
        }
        if (barrels.vent == null) {
            barrels.vent = play(minecraft, "item.minigun.vent", entity, false, VENT_VOLUME);
        }
        LivingEntity living = (LivingEntity) entity;
        boolean firstPerson = entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
        if (main) {
            puff(minecraft, living, living.getMainArm(), firstPerson);
        }
        if (off) {
            puff(minecraft, living, living.getMainArm().getOpposite(), firstPerson);
        }
    }

    /** White gas out of both sides of the barrels of the gun in that arm, where it was last drawn, curling upwards. */
    private static void puff(Minecraft minecraft, LivingEntity living, HumanoidArm arm, boolean firstPerson) {
        Vec3 muzzle = GunMuzzleTracker.muzzle(living.getId(), firstPerson, arm);
        if (muzzle == null) {
            // Not on screen.
            return;
        }
        Vec3 look = living.getViewVector(1.0F);
        Vec3 side = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        side = side.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
        Vec3 moving = living.getDeltaMovement();
        var random = living.getRandom();
        for (int i = 0; i < VENT_PUFFS_PER_TICK; i++) {
            double out = (random.nextBoolean() ? 1.0D : -1.0D) * (0.03D + random.nextDouble() * 0.04D);
            Vec3 from = muzzle.subtract(look.scale(VENT_BEHIND_MUZZLE * random.nextDouble()));
            minecraft.level.addParticle(ParticleTypes.WHITE_SMOKE, from.x, from.y, from.z,
                    moving.x + side.x * out + look.x * 0.02D,
                    0.02D + random.nextDouble() * 0.03D,
                    moving.z + side.z * out + look.z * 0.02D);
            ventPuffs++;
        }
    }

    /** Whether the hiss of barrels being run cool is playing for this entity. */
    public static boolean venting(int entityId) {
        Barrels barrels = BARRELS.get(entityId);
        return barrels != null && barrels.vent != null && !barrels.vent.isStopped()
                && Minecraft.getInstance().getSoundManager().isActive(barrels.vent);
    }

    private static MinigunSound play(Minecraft minecraft, String key, Entity entity, boolean looping, float volume) {
        Identifier id = Reference.id(key);
        var holder = ModSounds.ALL.get(id);
        if (holder == null) {
            return null;
        }
        MinigunSound sound = new MinigunSound(holder.get(), entity, looping, volume);
        minecraft.getSoundManager().play(sound);
        return sound;
    }

    private static void haltAll(Minecraft minecraft, Barrels barrels) {
        if (barrels.loop != null) {
            barrels.loop.halt();
            minecraft.getSoundManager().stop(barrels.loop);
            barrels.loop = null;
        }
        if (barrels.oneShot != null) {
            barrels.oneShot.halt();
            minecraft.getSoundManager().stop(barrels.oneShot);
            barrels.oneShot = null;
        }
    }

    /** Barrel rotation in degrees for the player holding the gun. */
    public static float barrelAngle(int entityId, float partialTick) {
        Barrels barrels = BARRELS.get(entityId);
        return barrels == null ? 0.0F : Mth.lerp(partialTick, barrels.angleO, barrels.angle);
    }

    public static float spin(int entityId) {
        Barrels barrels = BARRELS.get(entityId);
        return barrels == null ? 0.0F : barrels.spin;
    }

    public static String audioState(int entityId) {
        Barrels barrels = BARRELS.get(entityId);
        return barrels == null ? "NONE" : barrels.audio.name();
    }
}
