package ttv.migami.jeg.vehicle.client.render;

import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import ttv.migami.jeg.vehicle.entity.BulldozerEntity;
import ttv.migami.jeg.vehicle.terrain.BulldozerTerrainWork;
import ttv.migami.jeg.vehicle.util.BulldozerRig;

/** Drives every articulation from the machine's state: linkage, hydraulics, running gear, chassis and controls. */
public final class BulldozerRenderer extends AbstractVehicleGeoRenderer<BulldozerEntity> {
    /** Opt-in game-test probe, captured from actual posed cutting-edge and rod vertices. */
    public static boolean captureRig;
    public static Vec3 renderedCut;
    public static Vec3 renderedRodEnd;
    private static final int LINKS = 30;
    private static final String[][] SHOE_NAMES = new String[2][LINKS];
    private static final String[][] WHEEL_NAMES = new String[2][BulldozerRig.WHEEL_RADII.length];
    private static final String[] SIDES = {"left", "right"};
    static {
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < LINKS; i++) SHOE_NAMES[side][i] = "shoe_" + SIDES[side] + "_" + (i < 10 ? "0" : "") + i;
            for (int i = 0; i < BulldozerRig.WHEEL_RADII.length; i++) WHEEL_NAMES[side][i] = "wheel_" + SIDES[side] + "_" + i;
        }
    }

    public BulldozerRenderer(EntityRendererProvider.Context context) { super(context, new NamedVehicleGeoModel("bulldozer")); }

    @Override public void preRenderPass(RenderPassInfo<VehicleRenderState> pass, SubmitNodeCollector collector) {
        super.preRenderPass(pass, collector);
        if (!(pass.renderState().vehicle instanceof BulldozerEntity dozer)) return;
        float partial = Mth.clamp(pass.renderState().ageInTicks - dozer.tickCount, 0, 1);
        float time = pass.renderState().ageInTicks;
        float lift = dozer.bladeLift(partial), radians = lift * Mth.DEG_TO_RAD;
        double[] liftNow = BulldozerRig.liftCylinder(lift), liftRest = BulldozerRig.liftCylinder(0);
        double[] tiltNow = BulldozerRig.tiltStrut(lift), tiltRest = BulldozerRig.tiltStrut(0);
        float ripper = dozer.ripper(partial);
        Vec3 ripperOffset = BulldozerRig.ripperOffset(ripper);
        double[] ripNow = BulldozerRig.ripperCylinder(ripper), ripRest = BulldozerRig.ripperCylinder(0);
        float rpm = dozer.engineRpm(partial);
        boolean running = dozer.engineRunning();
        // A diesel's shake: small, fast and irregular, stronger with engine speed and blade load.
        float shake = running ? (.035F + rpm * .05F + dozer.load() * .05F) : 0;
        float jitterY = shake * (Mth.sin(time * 9.7F) + .6F * Mth.sin(time * 23.3F + 1.3F));
        float jitterPitch = shake * .35F * Mth.sin(time * 13.1F + .4F) * Mth.DEG_TO_RAD;
        float lean = dozer.lean(partial) * Mth.DEG_TO_RAD;
        boolean working = dozer.workStatus() == BulldozerTerrainWork.WORKING || dozer.workStatus() == BulldozerTerrainWork.BUSY
                || dozer.workStatus() == BulldozerTerrainWork.SPOIL;
        float chatter = working ? (.25F + dozer.load() * .5F) : 0;
        float liftRate = dozer.bladeLift(partial) - dozer.bladeLift(Math.max(0, partial - .5F));
        double speed = dozer.leftTravel(partial) + dozer.rightTravel(partial) - dozer.leftTravel(0) - dozer.rightTravel(0);
        double turn = dozer.leftTravel(partial) - dozer.rightTravel(partial) - dozer.leftTravel(0) + dozer.rightTravel(0);
        pass.addBoneUpdater((info, bones) -> {
            bones.ifPresent("chassis", b -> { b.setRotX(lean + jitterPitch); b.setTranslateY(jitterY); });
            bones.ifPresent("blade_lift", b -> b.setRotX(radians));
            bones.ifPresent("blade_level", b -> b.setRotX(-radians));
            bones.ifPresent("blade_tilt", b -> {
                b.setRotZ(chatter * .004F * Mth.sin(time * 31F));
                b.setTranslateY(chatter * .12F * Mth.sin(time * 19F + .7F));
            });
            bones.ifPresent("exhaust_flap", b -> b.setRotX(dozer.exhaustFlap(partial) * Mth.DEG_TO_RAD));
            bones.ifPresent("exhaust", b -> b.setRotZ(shake * .9F * Mth.sin(time * 17F) * Mth.DEG_TO_RAD));
            // The operator's joysticks follow what the machine is doing.
            bones.ifPresent("lever_travel", b -> {
                b.setRotX((float) Mth.clamp(-speed * 1.6, -.35, .35));
                b.setRotZ((float) Mth.clamp(turn * 1.6, -.3, .3));
            });
            bones.ifPresent("lever_blade", b -> b.setRotX(Mth.clamp(-liftRate * .35F, -.35F, .35F)));
            // Ripper parallelogram: both links swing about their mount pins and the beam follows, shanks upright.
            bones.ifPresent("ripper_upper_link", b -> b.setRotX(ripper * Mth.DEG_TO_RAD));
            bones.ifPresent("ripper_lower_link", b -> b.setRotX(ripper * Mth.DEG_TO_RAD));
            bones.ifPresent("ripper_beam", b -> { b.setTranslateY((float) ripperOffset.y); b.setTranslateZ((float) ripperOffset.z); });
            for (int s = 0; s < 2; s++) {
                String side = SIDES[s];
                bones.ifPresent("lift_barrel_" + side, b -> b.setRotX((float) (liftNow[0] - liftRest[0])));
                bones.ifPresent("lift_rod_" + side, b -> b.setScaleY((float) ((liftNow[1] - BulldozerRig.LIFT_BARREL) / (liftRest[1] - BulldozerRig.LIFT_BARREL))));
                bones.ifPresent("tilt_barrel_" + side, b -> b.setRotX((float) (tiltNow[0] - tiltRest[0])));
                bones.ifPresent("tilt_rod_" + side, b -> b.setScaleY((float) ((tiltNow[1] - BulldozerRig.TILT_BARREL) / (tiltRest[1] - BulldozerRig.TILT_BARREL))));
                bones.ifPresent("ripper_barrel_" + side, b -> b.setRotX((float) (ripNow[0] - ripRest[0])));
                bones.ifPresent("ripper_rod_" + side, b -> b.setScaleY((float) ((ripNow[1] - BulldozerRig.RIPPER_BARREL) / (ripRest[1] - BulldozerRig.RIPPER_BARREL))));
                double travel = (s == 0 ? dozer.leftTravel(partial) : dozer.rightTravel(partial)) * 16;
                for (int i = 0; i < LINKS; i++) {
                    double start = i * BulldozerRig.TRACK_LENGTH / LINKS;
                    double[] from = BulldozerRig.tread(start), to = BulldozerRig.tread(start + travel);
                    bones.ifPresent(SHOE_NAMES[s][i], b -> {
                        b.setRotX((float) (to[2] - from[2])); b.setTranslateY((float) (to[0] - from[0])); b.setTranslateZ((float) (to[1] - from[1]));
                    });
                }
                for (int i = 0; i < BulldozerRig.WHEEL_RADII.length; i++) {
                    float angle = (float) (-travel / BulldozerRig.WHEEL_RADII[i]);
                    bones.ifPresent(WHEEL_NAMES[s][i], b -> b.setRotX(angle));
                }
            }
        });
        if (!captureRig) return;
        pass.model().getBone("lift_rod_left").ifPresent(bone -> pass.addPerBoneRender(bone, (info, drawn, out) -> {
            if (shadowPass()) return;
            var pose = info.poseStack().last().pose();
            var p = pose.transformPosition((float) (-BulldozerRig.LIFT_BASE.x / 16) - drawn.pivotX() / 16,
                    (float) ((BulldozerRig.LIFT_BASE.y + liftRest[1]) / 16) - drawn.pivotY() / 16,
                    (float) (BulldozerRig.LIFT_BASE.z / 16) - drawn.pivotZ() / 16, new Vector3f());
            renderedRodEnd = Minecraft.getInstance().gameRenderer.getMainCamera().position().add(p.x, p.y, p.z);
        }));
        pass.model().getBone("finish_blade_tilt").ifPresent(bone -> pass.addPerBoneRender(bone, (info, drawn, out) -> {
            if (shadowPass()) return;
            var pose = info.poseStack().last().pose();
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
            var forward = BulldozerRig.forward(dozer.getYRot());
            double low = Double.POSITIVE_INFINITY, front = Double.NEGATIVE_INFINITY;
            Vec3 sum = Vec3.ZERO;
            int count = 0;
            for (var cube : ((com.geckolib.cache.model.cuboid.CuboidGeoBone) drawn).cubes) for (var quad : cube.quads()) {
                if (quad == null) continue;
                for (var vertex : quad.vertices()) {
                    // Per-bone callbacks run at the pivot; cuboid vertices still contain the absolute model coordinates.
                    var p = pose.transformPosition(vertex.posX() - drawn.pivotX() / 16,
                            vertex.posY() - drawn.pivotY() / 16, vertex.posZ() - drawn.pivotZ() / 16, new Vector3f());
                    var world = camera.add(p.x, p.y, p.z);
                    double depth = world.dot(forward);
                    if (world.y < low - .001) { low = world.y; front = depth; sum = world; count = 1; }
                    else if (Math.abs(world.y - low) < .001) {
                        if (depth > front + .001) { front = depth; sum = world; count = 1; }
                        else if (Math.abs(depth - front) < .001) { sum = sum.add(world); count++; }
                    }
                }
            }
            if (count > 0) renderedCut = sum.scale(1D / count);
        }));
    }

    private static boolean shadowPass() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            return (boolean) api.getMethod("isRenderingShadowPass").invoke(api.getMethod("getInstance").invoke(null));
        } catch (ReflectiveOperationException absent) {
            return false; // Iris is optional.
        }
    }
}
