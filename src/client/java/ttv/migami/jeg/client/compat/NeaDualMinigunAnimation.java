package ttv.migami.jeg.client.compat;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.api.NotEnoughAnimationsApi;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import ttv.migami.jeg.client.render.DualMinigunPose;
import ttv.migami.jeg.gun.MinigunSpin;

/**
 * Not Enough Animations (in the yftm pack) poses the arms itself after the vanilla model, which left two miniguns hanging
 * at the hips. The dual-minigun pose is registered with it as one of its own arm animations, above its held-item and
 * bow poses (3100-3200) and below swapping items, riding, swimming, sleeping and dying (3500+).
 */
final class NeaDualMinigunAnimation extends BasicAnimation {
    private static final int PRIORITY = 3300;
    private static final BodyPart[] ARMS = {BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM};

    /** Only called by NeaCompat once Not Enough Animations is known to be installed and started. */
    static void register() {
        NotEnoughAnimationsApi.registerAnimation(new NeaDualMinigunAnimation());
        NotEnoughAnimationsApi.refreshEnabledAnimations();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isValid(AbstractClientPlayer player, PlayerData data) {
        return MinigunSpin.isDualWielding(player);
    }

    @Override
    public BodyPart[] getBodyParts(AbstractClientPlayer player, PlayerData data) {
        return ARMS;
    }

    @Override
    public int getPriority(AbstractClientPlayer player, PlayerData data) {
        return PRIORITY;
    }

    @Override
    public void apply(AbstractClientPlayer player, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
        boolean left = part == BodyPart.LEFT_ARM;
        DualMinigunPose.poseArm(model.head, left ? model.leftArm : model.rightArm, left);
    }
}
