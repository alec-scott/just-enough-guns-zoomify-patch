package ttv.migami.jeg.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import ttv.migami.jeg.client.handler.AimingHandler;
import ttv.migami.jeg.gun.GunWeight;
import ttv.migami.jeg.item.GunItem;
import ttv.migami.jeg.item.attachment.AttachmentType;
import ttv.migami.jeg.item.attachment.GunAttachments;

/**
 * How the gun in first person moves with the player: into a low carry for sprinting, side to side with each stride, and
 * lagging a little behind the view. Everything is stepped once per tick and interpolated per frame, and scaled by the gun's
 * {@link GunWeight}, so nothing moves in jumps and a minigun feels nothing like a pistol.
 */
public final class GunViewModelMotion {
    private static final float SPRINT_IN_RATE = 0.10F;
    private static final float SPRINT_OUT_RATE = 0.14F;
    private static final float SPRINT_OUT_TO_FIRE_RATE = 0.30F;
    private static final float GAIT_RESPONSE = 0.12F;
    // Horizontal speed in blocks per tick that counts as a full stride (a rifleman's sprint).
    private static final float FULL_GAIT_SPEED = 0.28F;
    private static final float FALL_RESPONSE = 0.25F;
    private static final float FALL_TILT_DEGREES = 8.0F;
    private static final float LAG_AMOUNT = 0.22F;
    private static final float LAG_RESPONSE = 0.28F;
    private static final float MAX_LAG_DEGREES = 3.5F;

    private static float sprint;
    private static float sprintO;
    private static float gait;
    private static float gaitO;
    private static float stride;
    private static float strideO;
    private static float fall;
    private static float fallO;
    private static float lagYaw;
    private static float lagYawO;
    private static float lagPitch;
    private static float lagPitchO;
    private static float lastYaw;
    private static float lastPitch;

    private GunViewModelMotion() {}

    public static void tick(Minecraft minecraft) {
        sprintO = sprint;
        gaitO = gait;
        strideO = stride;
        fallO = fall;
        lagYawO = lagYaw;
        lagPitchO = lagPitch;

        LocalPlayer player = minecraft.player;
        ItemStack held = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
        if (player == null || !(held.getItem() instanceof GunItem gun)) {
            sprint = Math.max(0.0F, sprint - SPRINT_OUT_RATE);
            gait *= 1.0F - GAIT_RESPONSE;
            fall *= 1.0F - FALL_RESPONSE;
            lagYaw *= 1.0F - LAG_RESPONSE;
            lagPitch *= 1.0F - LAG_RESPONSE;
            if (player != null) {
                lastYaw = player.getYRot();
                lastPitch = player.getXRot();
            }
            return;
        }

        float handling = GunWeight.of(gun.getStats().id()).handling();
        float steadiness = Mth.sqrt(handling);

        boolean firing = minecraft.options.keyAttack.isDown() || GunRecoilHandler.isSuppressingSprintPose();
        boolean carry = player.isSprinting() && !AimingHandler.get().isAiming() && !firing && !hasBayonet(held);
        if (carry) {
            sprint = Math.min(1.0F, sprint + SPRINT_IN_RATE / steadiness);
        } else {
            sprint = Math.max(0.0F, sprint - (firing ? SPRINT_OUT_TO_FIRE_RATE : SPRINT_OUT_RATE) / steadiness);
        }

        float strength = player.onGround() && !player.isPassenger()
                ? Mth.clamp((float) player.getDeltaMovement().horizontalDistance() / FULL_GAIT_SPEED, 0.0F, 1.0F)
                : 0.0F;
        gait += (strength - gait) * GAIT_RESPONSE;
        // Same pace as vanilla's walk distance, which the view bob follows.
        stride += (float) Math.sqrt(Mth.lengthSquared(player.getX() - player.xo, player.getZ() - player.zo)) * 0.6F;
        if (stride > 1000.0F) {
            stride -= 1000.0F;
            strideO -= 1000.0F;
        }

        float falling = player.isPassenger() ? 0.0F : (float) Mth.clamp(player.yo - player.getY(), -1.0D, 1.0D);
        fall += (falling - fall) * (FALL_RESPONSE / steadiness);

        float yawDelta = Mth.wrapDegrees(player.getYRot() - lastYaw);
        float pitchDelta = player.getXRot() - lastPitch;
        lastYaw = player.getYRot();
        lastPitch = player.getXRot();
        float reach = MAX_LAG_DEGREES * steadiness;
        lagYaw += (Mth.clamp(yawDelta * LAG_AMOUNT * handling, -reach, reach) - lagYaw) * (LAG_RESPONSE / steadiness);
        lagPitch += (Mth.clamp(pitchDelta * LAG_AMOUNT * handling, -reach, reach) - lagPitch) * (LAG_RESPONSE / steadiness);
    }

    /** Stride sway, fall and landing tilt and turning lag. Steadied while aiming down the sights. */
    public static void applyCarry(PoseStack poseStack, LocalPlayer player, GunWeight weight, float partialTick, float ads) {
        float handling = weight.handling();
        float steady = 1.0F - ads * 0.85F;
        float amount = Mth.lerp(partialTick, gaitO, gait) * steady * Mth.sqrt(handling);
        if (amount > 1.0E-3F && Minecraft.getInstance().options.bobView().get()) {
            // Tied to the distance walked so the gun swings at the pace of the footsteps, one way per step.
            float phase = Mth.lerp(partialTick, strideO, stride) * (float) Math.PI;
            float swing = Mth.sin(phase);
            float lift = Mth.cos(phase * 2.0F);
            poseStack.translate(swing * 0.018F * amount, lift * 0.010F * amount, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(swing * 1.6F * amount));
            poseStack.mulPose(Axis.ZP.rotationDegrees(swing * 1.4F * amount));
            poseStack.mulPose(Axis.XP.rotationDegrees(lift * 0.5F * amount));
        }

        float shownFall = Mth.lerp(partialTick, fallO, fall) * (1.0F - ads * 0.5F) * (1.0F - Mth.abs(player.getXRot()) / 90.0F);
        poseStack.mulPose(Axis.XP.rotationDegrees(shownFall * FALL_TILT_DEGREES));

        float lagSteady = 1.0F - ads * 0.7F;
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTick, lagYawO, lagYaw) * lagSteady));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, lagPitchO, lagPitch) * lagSteady));
    }

    /** The low carry for sprinting, eased in and out. */
    public static void applySprintCarry(PoseStack poseStack, int direction, float partialTick, float ads) {
        float blend = Mth.lerp(partialTick, sprintO, sprint);
        float eased = blend * blend * (3.0F - 2.0F * blend) * (1.0F - ads);
        if (eased <= 1.0E-3F) {
            return;
        }
        poseStack.translate(-0.18F * direction * eased, -0.08F * eased, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(30.0F * direction * eased));
        poseStack.mulPose(Axis.XP.rotationDegrees(-18.0F * eased));
    }

    private static boolean hasBayonet(ItemStack stack) {
        return GunAttachments.stack(stack, AttachmentType.BARREL)
                .filter(attachment -> attachment.is(ItemTags.SWORDS))
                .isPresent();
    }
}
