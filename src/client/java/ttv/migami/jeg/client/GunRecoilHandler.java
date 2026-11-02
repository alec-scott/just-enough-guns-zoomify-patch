package ttv.migami.jeg.client;

import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import ttv.migami.jeg.client.handler.AimingHandler;
import ttv.migami.jeg.gun.GunStats;
import ttv.migami.jeg.gun.RecoilProfiles;
import ttv.migami.jeg.item.attachment.AttachmentModifiers;

/**
 * Keeps 1.20.1-style camera recoil and first-person gun model recoil in sync.
 */
public final class GunRecoilHandler {
    private static final Random RANDOM = new Random();
    private static final long SPRINT_POSE_SUPPRESSION_NANOS = 250_000_000L;
    private static final double GUN_RECOIL_BASE_OMEGA = 0.6D;
    private static final double GUN_RECOIL_DAMPING_RATIO = 0.7D;
    private static final double GUN_RECOIL_IMPULSE = 2.1D;
    private static final int GUN_RECOIL_SUBSTEPS = 8;

    private static float recoil;
    private static float previous;
    /** Spring-driven gun kick (0 = rest, ~1 = full kick); integrated in sub-steps so shots blend instead of snapping. */
    private static double gunRecoilNormal;
    private static double previousGunRecoilNormal;
    private static double gunRecoilVelocity;
    private static double gunRecoilOmega = GUN_RECOIL_BASE_OMEGA;
    private static float gunRecoilSway;
    private static float previousGunRecoilSway;
    private static double gunRecoilAngle;
    private static float gunRecoilKick;
    private static float gunRecoilAdsReduction = 0.2F;
    private static float gunRecoilRandom;
    private static float cameraRecoil;
    private static float progressCameraRecoil;
    private static int cameraRecoilDirection = 1;
    private static long suppressSprintPoseUntilNanos;

    private GunRecoilHandler() {}

    public static void tick() {
        previous = recoil;
        tickCameraRecoil();
        tickGunRecoil();
        recoil = (float) Math.max(0.0D, gunRecoilNormal);
    }

    public static void onShot(GunStats stats) {
        onShot(stats, AttachmentModifiers.NONE);
    }

    public static void onShot(GunStats stats, AttachmentModifiers modifiers) {
        RecoilProfiles.Parameters parameters = RecoilProfiles.parameters(stats.id());
        float adsReduction = getAdsRecoilReduction(parameters);
        float recoilAngle = parameters.angle() * modifiers.recoilMultiplier();
        float recoilKick = parameters.kick() * modifiers.kickMultiplier();
        cameraRecoil = recoilAngle * adsReduction;
        progressCameraRecoil = 0.0F;
        cameraRecoilDirection = RANDOM.nextBoolean() ? 1 : -1;
        if (recoilAngle > 0.0F || recoilKick > 0.0F) {
            // Slower guns settle more slowly; rapid fire stacks with diminishing returns instead of snapping to full kick.
            gunRecoilOmega = GUN_RECOIL_BASE_OMEGA * Mth.clamp(6.0D / Math.max(4, stats.fireDelay()), 0.4D, 1.0D);
            addGunRecoilImpulse(1.0D);
        }
        gunRecoilAngle = recoilAngle;
        gunRecoilKick = recoilKick;
        gunRecoilAdsReduction = parameters.adsReduction();
        gunRecoilRandom = RANDOM.nextFloat();
        suppressSprintPoseUntilNanos = System.nanoTime() + SPRINT_POSE_SUPPRESSION_NANOS;
    }

    public static void addShot(float amount) {
        cameraRecoil = Math.max(0.0F, amount);
        progressCameraRecoil = 0.0F;
        cameraRecoilDirection = RANDOM.nextBoolean() ? 1 : -1;
    }

    public static void addDryFire(float amount) {
        cameraRecoil = Math.max(cameraRecoil, amount * 0.35F);
        progressCameraRecoil = 0.0F;
        cameraRecoilDirection = RANDOM.nextBoolean() ? 1 : -1;
        addGunRecoilImpulse(0.25D);
        gunRecoilAngle = Math.max(gunRecoilAngle, amount);
        gunRecoilKick = 0.0F;
        gunRecoilRandom = RANDOM.nextFloat();
    }

    private static void tickCameraRecoil() {
        if (cameraRecoil <= 0.0F) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            cameraRecoil = 0.0F;
            progressCameraRecoil = 0.0F;
            return;
        }

        float recoilAmount = cameraRecoil * 0.15F;
        float startProgress = progressCameraRecoil / cameraRecoil;
        float endProgress = Math.min(1.0F, (progressCameraRecoil + recoilAmount) / cameraRecoil);
        float delta = endProgress - startProgress;
        float pitch = minecraft.player.getXRot();
        float yaw = minecraft.player.getYRot();

        if (startProgress < 0.2F) {
            pitch -= (delta / 0.2F) * cameraRecoil;
            yaw += cameraRecoilDirection * (delta / 0.2F) * cameraRecoil * 0.5F;
        } else {
            pitch += (delta / 0.8F) * cameraRecoil;
            yaw -= cameraRecoilDirection * (delta / 0.8F) * cameraRecoil * 0.5F;
        }

        minecraft.player.setXRot(Mth.clamp(pitch, -90.0F, 90.0F));
        minecraft.player.setYRot(yaw);
        progressCameraRecoil += recoilAmount;

        if (progressCameraRecoil >= cameraRecoil) {
            cameraRecoil = 0.0F;
            progressCameraRecoil = 0.0F;
        }
    }

    private static void addGunRecoilImpulse(double strength) {
        double stacked = Mth.clamp(gunRecoilNormal, 0.0D, 1.0D);
        gunRecoilVelocity += GUN_RECOIL_IMPULSE * gunRecoilOmega * strength * (1.0D - 0.5D * stacked);
    }

    private static void tickGunRecoil() {
        previousGunRecoilNormal = gunRecoilNormal;
        previousGunRecoilSway = gunRecoilSway;
        double stiffness = gunRecoilOmega * gunRecoilOmega;
        double damping = 2.0D * GUN_RECOIL_DAMPING_RATIO * gunRecoilOmega;
        double step = 1.0D / GUN_RECOIL_SUBSTEPS;
        for (int i = 0; i < GUN_RECOIL_SUBSTEPS; i++) {
            gunRecoilVelocity += (-stiffness * gunRecoilNormal - damping * gunRecoilVelocity) * step;
            gunRecoilNormal += gunRecoilVelocity * step;
        }
        // Ease the sideways twitch toward this shot's random direction rather than jumping to it.
        float swayTarget = gunRecoilRandom * 2.0F - 1.0F;
        gunRecoilSway += (swayTarget - gunRecoilSway) * 0.5F;
        if (Math.abs(gunRecoilNormal) < 1.0E-4D && Math.abs(gunRecoilVelocity) < 1.0E-4D) {
            gunRecoilNormal = 0.0D;
            gunRecoilVelocity = 0.0D;
        }
    }

    /** Trigger released: stop the camera kick as before, but let the gun model settle instead of snapping to rest. */
    public static void releaseTrigger() {
        cameraRecoil = 0.0F;
        progressCameraRecoil = 0.0F;
    }

    public static void stopImmediate() {
        recoil = 0.0F;
        previous = 0.0F;
        gunRecoilNormal = 0.0D;
        previousGunRecoilNormal = 0.0D;
        gunRecoilVelocity = 0.0D;
        gunRecoilSway = 0.0F;
        previousGunRecoilSway = 0.0F;
        gunRecoilAngle = 0.0D;
        gunRecoilKick = 0.0F;
        cameraRecoil = 0.0F;
        progressCameraRecoil = 0.0F;
    }

    public static float getRecoil(float partialTick) {
        return Mth.lerp(partialTick, previous, recoil);
    }

    public static boolean isSuppressingSprintPose() {
        return System.nanoTime() < suppressSprintPoseUntilNanos;
    }

    public static float current() {
        return recoil;
    }

    public static double getGunRecoilNormal(float partialTick) {
        return Mth.lerp(partialTick, previousGunRecoilNormal, gunRecoilNormal);
    }

    public static double getGunRecoilAngle() {
        return gunRecoilAngle;
    }

    public static float getGunRecoilKick() {
        return gunRecoilKick;
    }

    /** Smoothed sideways twitch in [-1, 1]. */
    public static float getGunRecoilSway(float partialTick) {
        return Mth.lerp(partialTick, previousGunRecoilSway, gunRecoilSway);
    }

    public static float getAdsRecoilReduction() {
        return 1.0F - gunRecoilAdsReduction * AimingHandler.get().getNormalisedAdsProgress();
    }

    private static float getAdsRecoilReduction(RecoilProfiles.Parameters parameters) {
        return 1.0F - parameters.adsReduction() * AimingHandler.get().getNormalisedAdsProgress();
    }
}
