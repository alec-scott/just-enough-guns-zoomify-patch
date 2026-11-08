package ttv.migami.jeg.client.render;

import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * Plain player models, baked here rather than taken from the player renderer, for drawing the arms that hold a gun in
 * first person. Entity Model Features swaps the renderer's model for a resource pack's own (Fresh Animations re-shapes and
 * animates the arms, first person included), and those arms ignore the pose set on them, so they flew off the gun. The
 * gun poses are made for the vanilla arm, so the arms are drawn from that.
 */
public final class VanillaArmModels {
    private static PlayerModel wide;
    private static PlayerModel slim;

    private VanillaArmModels() {}

    public static PlayerModel forPlayer(AbstractClientPlayer player) {
        boolean slimArms = player.getSkin().model() == PlayerModelType.SLIM;
        if (slimArms) {
            if (slim == null) {
                slim = bake(true);
            }
            return slim;
        }
        if (wide == null) {
            wide = bake(false);
        }
        return wide;
    }

    private static PlayerModel bake(boolean slimArms) {
        return new PlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slimArms), 64, 64).bakeRoot(), slimArms);
    }
}
