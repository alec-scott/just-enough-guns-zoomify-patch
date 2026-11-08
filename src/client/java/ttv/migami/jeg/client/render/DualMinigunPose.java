package ttv.migami.jeg.client.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * A minigun in each hand, in third person: each arm takes vanilla's crossbow-holding angles for its own side (the angle
 * a single gun is held at), instead of the crossbow hold's second arm reaching across onto the other gun, which swung the
 * off-hand gun out sideways. Matching vanilla's numbers exactly matters: animation packs such as Fresh Animations
 * recognise a crossbow hold by these angles and only then leave the arm to it, otherwise they pull the arms down.
 */
public final class DualMinigunPose {
    private DualMinigunPose() {}

    /** As AnimationUtils.animateCrossbowHold poses the holding arm, for either side. */
    public static void poseArm(ModelPart head, ModelPart arm, boolean left) {
        arm.yRot = (left ? 0.3F : -0.3F) + head.yRot;
        arm.xRot = -Mth.HALF_PI + head.xRot + 0.1F;
    }
}
