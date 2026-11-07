package ttv.migami.jeg.client.render.gun;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.PerBoneRender;
import com.geckolib.renderer.base.RenderPassInfo;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

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

    private static final Map<Integer, Sample> THIRD_PERSON = new ConcurrentHashMap<>();
    private static volatile Sample firstPerson;

    private GunMuzzleTracker() {}

    /** Hooks the barrel bones of this render pass so the muzzle is measured as the gun is drawn. */
    public static void track(RenderPassInfo<GeoRenderState> passInfo, int ownerId, boolean firstPersonView) {
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
        Probe probe = new Probe(ownerId, firstPersonView);
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

    /** The muzzle in world space, or null when it was not drawn in the last few frames. */
    public static Vec3 muzzle(int entityId, boolean firstPersonView) {
        Sample sample = firstPersonView ? firstPerson : THIRD_PERSON.get(entityId);
        if (sample == null || sample.ownerId != entityId || System.nanoTime() - sample.timeNanos > MAX_AGE_NANOS) {
            return null;
        }
        return sample.world;
    }

    public static void clear() {
        THIRD_PERSON.clear();
        firstPerson = null;
    }

    private record Sample(int ownerId, Vec3 world, long timeNanos) {}

    /** Collects the barrel's front-most vertices across one render pass; each bone of the barrel reports into it. */
    private static final class Probe implements PerBoneRender<GeoRenderState> {
        private final int ownerId;
        private final boolean firstPersonView;
        private Matrix4f modelToLocal;
        private float frontZ = Float.POSITIVE_INFINITY;
        private final Vector3f frontSum = new Vector3f();
        private int frontCount;

        private Probe(int ownerId, boolean firstPersonView) {
            this.ownerId = ownerId;
            this.firstPersonView = firstPersonView;
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
            // Both the world and the first-person hand are drawn camera-relative and world-aligned: the hand pass
            // starts from the inverse of the view rotation it is then drawn with.
            Vector3f centre = new Vector3f(this.frontSum).div(this.frontCount);
            Vec3 world = camera.position().add(centre.x(), centre.y(), centre.z());
            Sample sample = new Sample(this.ownerId, world, System.nanoTime());
            if (this.firstPersonView) {
                firstPerson = sample;
            } else {
                THIRD_PERSON.put(this.ownerId, sample);
            }
        }
    }
}
