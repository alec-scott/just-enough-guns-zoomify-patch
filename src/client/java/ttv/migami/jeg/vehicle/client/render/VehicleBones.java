package ttv.migami.jeg.vehicle.client.render;

import com.geckolib.cache.model.BakedGeoModel;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.cache.model.GeoQuad;
import com.geckolib.cache.model.GeoVertex;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.util.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.OverlayTexture;
import ttv.migami.jeg.vehicle.client.resource.TextureCoverage;

/**
 * Draws a vehicle's whole model again into another buffer, for the layers that redraw it with another texture or
 * consumer (glass, glow, lamp spill). In GeckoLib 5.5 {@code GeoBone.render} draws only that bone's own cubes; its
 * children come from {@code renderChildren}. Vehicle models keep their cubes in child bones under an empty root, so
 * calling {@code render} on the top-level bones alone drew nothing: from the 26.1 port until 2026-11-26 the glass
 * and glow layers were empty (canopies untinted, lamps unlit).
 */
final class VehicleBones {
    /** Per sparse texture (glass, glow) and model: the cubes with anything of it on them. Render thread only. */
    private static final Map<TextureCoverage, Map<BakedGeoModel, Plan>> PLANS = new WeakHashMap<>();

    private VehicleBones() {}

    /**
     * The cubes of a model that have something of a sparse texture on at least one face, and the bones on the way to
     * them. A cube with none would only feed {@link QuadMaskConsumer} quads it drops, after GeckoLib had moved each of
     * its vertices: with the FSV-6 in view that was 10% of the render thread for each of the glass and glow layers
     * (2026-11-26). Null while the mask is off ({@link VehicleDrawCulling#quadMask}), so everything is drawn.
     */
    @Nullable
    static Plan plan(BakedGeoModel model, TextureCoverage coverage) {
        if (!VehicleDrawCulling.quadMask) {
            return null;
        }
        return PLANS.computeIfAbsent(coverage, c -> new IdentityHashMap<>()).computeIfAbsent(model, m -> {
            Plan plan = new Plan(Collections.newSetFromMap(new IdentityHashMap<>()), Collections.newSetFromMap(new IdentityHashMap<>()));
            for (GeoBone bone : m.topLevelBones()) {
                mark(bone, coverage, plan);
            }
            return plan;
        });
    }

    /** Which cubes to draw, and which bones lead to one. */
    record Plan(Set<GeoCube> cubes, Set<GeoBone> bones) {}

    private static boolean mark(GeoBone bone, TextureCoverage coverage, Plan plan) {
        boolean any = false;
        if (bone instanceof CuboidGeoBone cuboid) {
            for (GeoCube cube : cuboid.cubes) {
                if (covered(cube, coverage)) {
                    plan.cubes.add(cube);
                    any = true;
                }
            }
        } else {
            // A bone of some other kind: drawn whole, as GeckoLib draws it.
            any = true;
        }
        for (GeoBone child : bone.children()) {
            any |= mark(child, coverage, plan);
        }
        if (any) {
            plan.bones.add(bone);
        }
        return any;
    }

    /** Whether any face of the cube passes {@link QuadMaskConsumer}'s test (its UVs are fixed, so this never changes). */
    private static boolean covered(GeoCube cube, TextureCoverage coverage) {
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) {
                continue;
            }
            GeoVertex[] vertices = quad.vertices();
            if (vertices.length != 4) {
                return true;
            }
            float minU = Math.min(Math.min(vertices[0].texU(), vertices[1].texU()), Math.min(vertices[2].texU(), vertices[3].texU()));
            float maxU = Math.max(Math.max(vertices[0].texU(), vertices[1].texU()), Math.max(vertices[2].texU(), vertices[3].texU()));
            float minV = Math.min(Math.min(vertices[0].texV(), vertices[1].texV()), Math.min(vertices[2].texV(), vertices[3].texV()));
            float maxV = Math.max(Math.max(vertices[0].texV(), vertices[1].texV()), Math.max(vertices[2].texV(), vertices[3].texV()));
            if (coverage.any(minU, minV, maxU, maxV)) {
                return true;
            }
        }
        return false;
    }

    /** As GeckoLib draws a bone: into its frame, its own cubes, then its children (skipping hidden ones). */
    static void drawAll(RenderPassInfo<?> passInfo, PoseStack pose, VertexConsumer out, int light) {
        drawAll(passInfo, pose, out, light, null);
    }

    /**
     * As {@link #drawAll(RenderPassInfo, PoseStack, VertexConsumer, int)}, but only the cubes in {@code plan} (all of
     * them when it is null). Mirrors GeckoLib 5.5's {@code CuboidGeoBone.render} and {@code GeoBone.renderChildren}:
     * each cube in its own push and pop, each child put in its frame and its position listeners told.
     */
    static void drawAll(RenderPassInfo<?> passInfo, PoseStack pose, VertexConsumer out, int light, @Nullable Plan plan) {
        for (GeoBone bone : passInfo.model().topLevelBones()) {
            if (plan != null && !plan.bones.contains(bone)) {
                continue;
            }
            pose.pushPose();
            RenderUtil.prepMatrixForBone(pose, bone);
            if (plan == null) {
                bone.render(passInfo, pose, out, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                bone.renderChildren(passInfo, pose, out, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            } else {
                drawBone(passInfo, bone, pose, out, light, plan);
                drawChildren(passInfo, bone, pose, out, light, plan);
            }
            pose.popPose();
        }
    }

    private static void drawBone(RenderPassInfo<?> passInfo, GeoBone bone, PoseStack pose, VertexConsumer out, int light, Plan plan) {
        if (!(bone instanceof CuboidGeoBone cuboid)) {
            bone.render(passInfo, pose, out, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            return;
        }
        if (bone.frameSnapshot != null && bone.frameSnapshot.isHidden()) {
            return;
        }
        for (GeoCube cube : cuboid.cubes) {
            if (plan.cubes.contains(cube)) {
                pose.pushPose();
                cube.render(pose, out, light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                pose.popPose();
            }
        }
    }

    private static void drawChildren(RenderPassInfo<?> passInfo, GeoBone bone, PoseStack pose, VertexConsumer out, int light, Plan plan) {
        if (bone.frameSnapshot != null && bone.frameSnapshot.areChildrenHidden()) {
            return;
        }
        for (GeoBone child : bone.children()) {
            if (!plan.bones.contains(child)) {
                continue;
            }
            pose.pushPose();
            RenderUtil.prepMatrixForBoneAndUpdateListeners(pose, child, passInfo);
            drawBone(passInfo, child, pose, out, light, plan);
            drawChildren(passInfo, child, pose, out, light, plan);
            pose.popPose();
        }
    }
}
