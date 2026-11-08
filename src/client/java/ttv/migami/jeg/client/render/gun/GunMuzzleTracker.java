package ttv.migami.jeg.client.render.gun;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import ttv.migami.jeg.mixin.CameraAccessor;

/**
 * Where each shooter's gun muzzle was last drawn, read from the rendered model itself. Every frame the barrel bones'
 * actual render matrices (animation, recoil, the arm's pose and the camera included) place the front face of the
 * barrel in the world, so tracers and muzzle flashes can leave the gun rather than the shooter's eyes.
 */
public final class GunMuzzleTracker {
    /** The gun's first bone found here, and everything under it, makes up the barrel. */
    private static final List<String> MUZZLE_ROOT_BONES = List.of("front", "barrel", "gun_body", "bow_body");
    /** Arms and attachment slots are not part of the gun's own barrel. */
    private static final Set<String> SKIPPED_BONES = Set.of(
            "left_arm", "right_arm", "fake_left_arm", "fake_right_arm", "left_grip", "right_grip",
            "attachment_bone", "railing", "silencer", "makeshift_stock", "light_stock", "tactical_stock",
            "weighted_stock", "light_grip", "vertical_grip", "angled_grip", "extended_mag", "missile");
    /** Vertices within this distance (blocks, along the barrel) of the front-most one belong to the muzzle face. */
    private static final float FRONT_FACE_DEPTH = 0.75F / 16.0F;
    /** A muzzle position older than this has not been drawn recently enough to trust. */
    private static final long MAX_AGE_NANOS = 250_000_000L;

    /** Keyed by shooter and arm: with a minigun in each hand, each gun has its own muzzle. */
    private static final Map<Long, Sample> THIRD_PERSON = new ConcurrentHashMap<>();
    private static volatile Sample firstPersonRight;
    private static volatile Sample firstPersonLeft;

    private GunMuzzleTracker() {}

    private static long key(int entityId, HumanoidArm arm) {
        return ((long) entityId << 1) | (arm == HumanoidArm.LEFT ? 1L : 0L);
    }

    /** Hooks the barrel bones of this render pass so the muzzle of the gun in {@code arm} is measured as it is drawn. */
    public static void track(RenderPassInfo<GeoRenderState> passInfo, int ownerId, boolean firstPersonView, HumanoidArm arm) {
        if (IrisShadowPass.active()) {
            // Iris draws entities a second time from the sun for shadows; that pass is not where the gun shows on screen.
            return;
        }
        Optional<GeoBone> root = Optional.empty();
        for (String name : MUZZLE_ROOT_BONES) {
            root = passInfo.model().getBone(name);
            if (root.isPresent()) {
                break;
            }
        }
        if (root.isEmpty()) {
            return;
        }
        Probe probe = new Probe(ownerId, firstPersonView, arm);
        register(passInfo, root.get(), probe);
    }

    private static void register(RenderPassInfo<GeoRenderState> passInfo, GeoBone bone, Probe probe) {
        if (SKIPPED_BONES.contains(bone.name())) {
            return;
        }
        if (bone instanceof CuboidGeoBone cuboid && cuboid.cubes.length > 0) {
            passInfo.addPerBoneRender(bone, probe);
        }
        for (GeoBone child : bone.children()) {
            register(passInfo, child, probe);
        }
    }

    /** The muzzle of the entity's main-hand gun in world space, or null when it was not drawn in the last few frames. */
    public static Vec3 muzzle(int entityId, boolean firstPersonView) {
        Minecraft minecraft = Minecraft.getInstance();
        HumanoidArm arm = minecraft.level != null && minecraft.level.getEntity(entityId) instanceof LivingEntity living
                ? living.getMainArm() : HumanoidArm.RIGHT;
        return muzzle(entityId, firstPersonView, arm);
    }

    /** The muzzle of the gun in {@code arm} in world space, or null when it was not drawn in the last few frames. */
    public static Vec3 muzzle(int entityId, boolean firstPersonView, HumanoidArm arm) {
        Sample sample = firstPersonView
                ? (arm == HumanoidArm.LEFT ? firstPersonLeft : firstPersonRight)
                : THIRD_PERSON.get(key(entityId, arm));
        if (sample == null || sample.ownerId != entityId || System.nanoTime() - sample.timeNanos > MAX_AGE_NANOS) {
            return null;
        }
        return sample.world;
    }

    public static void clear() {
        THIRD_PERSON.clear();
        firstPersonRight = null;
        firstPersonLeft = null;
    }

    /**
     * The first-person hand is drawn with its own fixed field of view (70 degrees), the world with the player's, widened
     * by sprinting or a high FOV setting and narrowed by aiming down sights or zooming. A point on the drawn gun is moved
     * to the place in the world that shows on the same pixel at the same depth, so a tracer drawn into the world leaves
     * the barrel where the barrel appears on screen.
     */
    static Vec3 handToWorldView(Camera camera, Vec3 offset) {
        float hudFov = ((CameraAccessor) camera).jeg$getHudFov();
        float worldFov = camera.getFov();
        if (hudFov <= 1.0F || worldFov <= 1.0F || Math.abs(hudFov - worldFov) < 0.01F) {
            return offset;
        }
        double scale = Math.tan(Math.toRadians(worldFov) * 0.5D) / Math.tan(Math.toRadians(hudFov) * 0.5D);
        Vector3fc forward = camera.forwardVector();
        Vec3 look = new Vec3(forward.x(), forward.y(), forward.z());
        double depth = offset.dot(look);
        Vec3 lateral = offset.subtract(look.scale(depth));
        return look.scale(depth).add(lateral.scale(scale));
    }

    private record Sample(int ownerId, Vec3 world, long timeNanos) {}

    /** Whether Iris is drawing its shadow pass, looked up reflectively so Iris stays optional. */
    private static final class IrisShadowPass {
        private static final MethodHandle IS_RENDERING_SHADOW_PASS = find();

        private static MethodHandle find() {
            if (!FabricLoader.getInstance().isModLoaded("iris")) {
                return null;
            }
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object instance = api.getMethod("getInstance").invoke(null);
                return MethodHandles.publicLookup()
                        .findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class))
                        .bindTo(instance);
            } catch (Throwable ignored) {
                return null;
            }
        }

        static boolean active() {
            if (IS_RENDERING_SHADOW_PASS == null) {
                return false;
            }
            try {
                return (boolean) IS_RENDERING_SHADOW_PASS.invokeExact();
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    /** Collects the barrel's front-most vertices across one render pass; each bone of the barrel reports into it. */
    private static final class Probe implements PerBoneRender<GeoRenderState> {
        private final int ownerId;
        private final boolean firstPersonView;
        private final HumanoidArm arm;
        private Matrix4f modelToLocal;
        private float frontZ = Float.POSITIVE_INFINITY;
        private final Vector3f frontSum = new Vector3f();
        private int frontCount;

        private Probe(int ownerId, boolean firstPersonView, HumanoidArm arm) {
            this.ownerId = ownerId;
            this.firstPersonView = firstPersonView;
            this.arm = arm;
        }

        @Override
        public void submitRenderTask(RenderPassInfo<GeoRenderState> passInfo, GeoBone bone, SubmitNodeCollector collector) {
            if (bone.frameSnapshot != null && bone.frameSnapshot.isHidden()) {
                return;
            }
            if (this.modelToLocal == null) {
                this.modelToLocal = new Matrix4f(passInfo.getModelRenderMatrixState()).invert();
            }
            Matrix4f pose = passInfo.poseStack().last().pose();
            Vector3f rendered = new Vector3f();
            Vector3f local = new Vector3f();
            for (GeoCube cube : ((CuboidGeoBone) bone).cubes) {
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) {
                        continue;
                    }
                    for (GeoVertex vertex : quad.vertices()) {
                        pose.transformPosition(vertex.posX(), vertex.posY(), vertex.posZ(), rendered);
                        this.modelToLocal.transformPosition(rendered, local);
                        this.add(local.z(), rendered);
                    }
                }
            }
            this.publish();
        }

        /** Keeps a running centre of the vertices on the barrel's front face, the front being the model's negative Z. */
        private void add(float z, Vector3f rendered) {
            if (z < this.frontZ - FRONT_FACE_DEPTH) {
                // A new front: anything kept so far is now too far behind it.
                this.frontZ = z;
                this.frontSum.set(rendered);
                this.frontCount = 1;
                return;
            }
            if (z <= this.frontZ + FRONT_FACE_DEPTH) {
                this.frontZ = Math.min(this.frontZ, z);
                this.frontSum.add(rendered);
                this.frontCount++;
            }
        }

        private void publish() {
            if (this.frontCount == 0) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            Camera camera = minecraft.gameRenderer.getMainCamera();
            // The world is drawn camera-relative and world-aligned. The first-person hand is not always: vanilla starts
            // it from the inverse of the view rotation it then puts on the model-view stack, but Iris (with a shader
            // pack) draws it straight in view space with only bobbing on the model-view stack. Taking the point through
            // the model-view matrix the hand is drawn with lands it in view space either way, and undoing the camera's
            // view rotation turns that back into a world-aligned offset.
            Vector3f centre = new Vector3f(this.frontSum).div(this.frontCount);
            if (this.firstPersonView) {
                Matrix4f viewToWorld = camera.getViewRotationMatrix(new Matrix4f()).invert();
                viewToWorld.mul(RenderSystem.getModelViewStack()).transformPosition(centre);
            }
            Vec3 offset = new Vec3(centre.x(), centre.y(), centre.z());
            if (this.firstPersonView) {
                offset = handToWorldView(camera, offset);
            }
            Vec3 world = camera.position().add(offset);
            Sample sample = new Sample(this.ownerId, world, System.nanoTime());
            if (this.firstPersonView && this.arm == HumanoidArm.LEFT) {
                firstPersonLeft = sample;
            } else if (this.firstPersonView) {
                firstPersonRight = sample;
            } else {
                THIRD_PERSON.put(key(this.ownerId, this.arm), sample);
            }
        }
    }
}
