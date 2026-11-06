package ttv.migami.jeg.vehicle.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import ttv.migami.jeg.client.render.entity.MissileModel;
import ttv.migami.jeg.vehicle.projectile.VehicleMissileEntity;

public final class VehicleMissileRenderer extends EntityRenderer<VehicleMissileEntity, VehicleMissileRenderer.State> {
    public VehicleMissileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        MissileModel.submit(poseStack, collector, state.motion, state.big ? 2.4F : 1.5F, state.big ? 0.46F : 0.28F, state.lightCoords, state.flicker);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(VehicleMissileEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.motion = entity.getDeltaMovement();
        state.big = entity.missileWeaponId().getPath().contains("kh39");
        state.flicker = (entity.tickCount * 0.137F) % 1.0F;
    }

    public static final class State extends EntityRenderState {
        Vec3 motion = Vec3.ZERO;
        boolean big;
        float flicker;
    }
}
