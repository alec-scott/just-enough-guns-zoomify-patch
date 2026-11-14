package ttv.migami.jeg.vehicle.client.render;

import com.geckolib.cache.model.GeoBone;
import com.geckolib.renderer.base.GeoRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.geckolib.util.RenderUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ttv.migami.jeg.vehicle.client.resource.DefaultVehicleResource;
import ttv.migami.jeg.vehicle.entity.base.VehicleEntity;

/**
 * Draws {@link AircraftLights}: a glow at each light, the light it casts on the airframe round it, formation strips,
 * rotor-tip lights, and a searchlight's beam, lens and the pool where it lands.
 * <p>
 * The airframe is lit by drawing the model again over itself, its own texture tinted by the lights near each vertex and
 * faded out beyond their reach, so a lit panel keeps its paint and rivets rather than turning a flat colour. Glows face
 * the camera; a beam is a strip along its axis turned to face the camera, soft at the edges, fading with distance.
 * <p>
 * From the pilot's seat the lights stay out of the way: no beam across the view, the glows smaller and dimmer, the
 * strobes faint, and the cockpit lighting kept low.
 */
final class VehicleLightsLayer<T extends VehicleEntity> extends GeoRenderLayer<T, Void, VehicleRenderState> {
    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("jeg", "textures/effect/light_glow.png");
    private static final Identifier BEAM = Identifier.fromNamespaceAndPath("jeg", "textures/effect/light_beam.png");
    private static final Identifier POOL = Identifier.fromNamespaceAndPath("jeg", "textures/effect/light_pool.png");
    /** The airframe is lit only within this many blocks of the camera; beyond, the glows alone show. */
    private static final float SPILL_DISTANCE = 128.0F;
    /**
     * Light on a surface can at most bring it up to its own texture's colour, and much of the airframe and cockpit is
     * dark grey: these scale the lights' spill so that close to a lamp the paint shows in its colour.
     */
    private static final float SPILL_GAIN = 1.8F;
    private static final float COCKPIT_GAIN = 3.0F;

    VehicleLightsLayer(GeoRenderer<T, Void, VehicleRenderState> renderer) {
        super(renderer);
    }

    @Override
    public void submitRenderTask(RenderPassInfo<VehicleRenderState> passInfo, SubmitNodeCollector collector) {
        VehicleEntity vehicle = passInfo.renderState().vehicle;
        if (vehicle == null) {
            return;
        }
        AircraftLights.LightSet set = AircraftLights.forVehicle(vehicle);
        if (set == null || !AircraftLights.isPowered(vehicle)) {
            return;
        }
        float partialTick = Mth.clamp(passInfo.renderState().ageInTicks - vehicle.tickCount, 0.0F, 1.0F);
        boolean occupant = AircraftLights.isOccupantView(vehicle);
        boolean openSeat = occupant && set.openSeat();
        float seconds = (vehicle.level().getGameTime() + partialTick) / 20.0F;
        float seed = AircraftLights.seed(vehicle);
        CameraRenderState camera = passInfo.cameraState();
        Quaternionf facing = new Quaternionf(camera.orientation);
        Matrix4f model = new Matrix4f(passInfo.getModelRenderMatrixState());
        float cameraDistance = model.transformPosition(new Vector3f()).length();

        // Each light's glow and the light it casts, in the camera-relative frame the model is drawn in.
        List<Glow> glows = new ArrayList<>();
        List<float[]> spills = new ArrayList<>();
        for (AircraftLights.PointLight light : set.lights()) {
            Vector3f at = model.transformPosition(new Vector3f(light.x() / 16.0F, light.y() / 16.0F, light.z() / 16.0F));
            float r = ((light.colour() >> 16) & 0xFF) / 255.0F;
            float g = ((light.colour() >> 8) & 0xFF) / 255.0F;
            float b = (light.colour() & 0xFF) / 255.0F;
            if (light.kind() == AircraftLights.Kind.COCKPIT) {
                float level = COCKPIT_GAIN * (occupant ? 0.6F : 1.0F);
                // Instrument light falls mostly forward and down, but bounces round a small cockpit: wrap it.
                spills.add(new float[]{at.x, at.y, at.z, r, g, b, light.spill() * level, light.spillRadius() * 1.25F, 0.4F});
                continue;
            }
            float flash = AircraftLights.flash(light, seconds, seed);
            if (flash <= 0.002F) {
                continue;
            }
            if (openSeat && light.kind() != AircraftLights.Kind.BEACON) {
                // Lamps around an open seat face away from the operator: no glare, and no spill on the canopy beside them.
                continue;
            }
            float shown = flash;
            float size = light.size() * (0.55F + 0.45F * flash);
            if (occupant) {
                shown *= openSeat ? 0.3F : light.kind() == AircraftLights.Kind.STROBE ? 0.25F : 0.5F;
                size *= 0.7F;
            }
            glows.add(new Glow(at, size, r, g, b, shown, !occupant));
            if (!openSeat) {
                spills.add(new float[]{at.x, at.y, at.z, r, g, b, light.spill() * SPILL_GAIN * flash,
                        light.spillRadius() * 1.2F, 0.1F});
            }
        }

        // Formation strips: flat panels of soft light on the skin, and a little of it on the panels round them.
        List<Vector3f[]> strips = new ArrayList<>();
        List<float[]> stripColours = new ArrayList<>();
        for (AircraftLights.Strip strip : set.strips()) {
            float out = Math.signum(strip.x()) * 0.15F;
            float x = (strip.x() + out) / 16.0F;
            float y0 = (strip.y() - strip.height() * 0.5F) / 16.0F;
            float y1 = (strip.y() + strip.height() * 0.5F) / 16.0F;
            float z0 = strip.z0() / 16.0F;
            float z1 = strip.z1() / 16.0F;
            strips.add(new Vector3f[]{
                    model.transformPosition(new Vector3f(x, y0, z0)), model.transformPosition(new Vector3f(x, y0, z1)),
                    model.transformPosition(new Vector3f(x, y1, z1)), model.transformPosition(new Vector3f(x, y1, z0))});
            float r = ((strip.colour() >> 16) & 0xFF) / 255.0F;
            float g = ((strip.colour() >> 8) & 0xFF) / 255.0F;
            float b = (strip.colour() & 0xFF) / 255.0F;
            stripColours.add(new float[]{r, g, b});
            Vector3f centre = model.transformPosition(new Vector3f(x, strip.y() / 16.0F, (z0 + z1) * 0.5F));
            spills.add(new float[]{centre.x, centre.y, centre.z, r, g, b, 0.5F, 1.0F, 0.2F});
        }

        // The searchlight: from the lens along the beam to the first block it meets.
        AircraftLights.Searchlight search = set.searchlight();
        float searchLevel = search == null ? 0.0F : AircraftLights.searchlightLevel(vehicle, search, partialTick);
        Beam beam = null;
        if (searchLevel > 0.0F) {
            beam = beam(vehicle, search, searchLevel, model, camera);
            float r = ((search.colour() >> 16) & 0xFF) / 255.0F;
            float g = ((search.colour() >> 8) & 0xFF) / 255.0F;
            float b = (search.colour() & 0xFF) / 255.0F;
            if (!openSeat) {
                spills.add(new float[]{beam.lens.x, beam.lens.y, beam.lens.z, r, g, b, 0.55F * searchLevel, 1.1F, 0.0F});
            }
        }

        PoseStack modelPose = new PoseStack();
        modelPose.last().set(passInfo.getModelRenderMatrixPose());
        if (!spills.isEmpty() && cameraDistance < SPILL_DISTANCE) {
            float[][] lights = spills.toArray(new float[0][]);
            collector.order(1).submitCustomGeometry(modelPose,
                    RenderTypes.eyes(DefaultVehicleResource.texture(vehicle)),
                    (pose, buffer) -> {
                        PoseStack spillPose = new PoseStack();
                        spillPose.last().set(pose);
                        SpillConsumer lit = new SpillConsumer(buffer, lights);
                        passInfo.renderPosed(() -> {
                            // As GeckoLib draws a bone: into its frame, its own cubes, then its children (which skip
                            // whatever the battle damage hides).
                            for (GeoBone bone : passInfo.model().topLevelBones()) {
                                spillPose.pushPose();
                                RenderUtil.prepMatrixForBone(spillPose, bone);
                                bone.render(passInfo, spillPose, lit, 0x00F000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                                bone.renderChildren(passInfo, spillPose, lit, 0x00F000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
                                spillPose.popPose();
                            }
                        });
                    });
        }

        // Glows and the beam are built in the camera-relative frame directly; an identity pose leaves them there.
        PoseStack identity = new PoseStack();
        if (!strips.isEmpty()) {
            collector.order(2).submitCustomGeometry(identity, RenderTypes.eyes(POOL), (pose, buffer) -> {
                for (int i = 0; i < strips.size(); i++) {
                    Vector3f[] c = strips.get(i);
                    float[] rgb = stripColours.get(i);
                    int[] colour = colour(rgb[0], rgb[1], rgb[2], 0.95F);
                    quad(buffer, c[0], c[1], c[2], c[3], 0.0F, 1.0F, colour, colour);
                }
            });
        }
        AircraftLights.RotorTips tips = set.rotorTips();
        if (tips != null) {
            // Radians the rotor turns in a tick: the tip lights are drawn smeared over half of that, as the eye sees a
            // spinning light, which closes into a ring once the rotor is up to speed.
            float turn = Math.abs(vehicle.propellerRot(1.0F) - vehicle.propellerRot(0.0F));
            float tipAlpha = occupant ? 0.45F : 0.9F;
            collector.order(2).submitCustomGeometry(modelPose, RenderTypes.eyes(GLOW),
                    (pose, buffer) -> rotorTips(passInfo, pose, buffer, tips, turn, facing, tipAlpha));
        }
        if (!glows.isEmpty() || beam != null) {
            Beam lensBeam = beam;
            collector.order(2).submitCustomGeometry(identity, RenderTypes.eyes(GLOW), (pose, buffer) -> {
                for (Glow glow : glows) {
                    glow(buffer, facing, glow.at, glow.size, glow.r, glow.g, glow.b, glow.alpha, glow.pullForward);
                }
                if (lensBeam != null && !openSeat) {
                    glow(buffer, facing, lensBeam.lens, lensBeam.lensSize, lensBeam.r, lensBeam.g, lensBeam.b,
                            lensBeam.lensAlpha, !occupant);
                }
            });
        }
        if (beam != null) {
            Beam shaft = beam;
            if (!occupant && shaft.shaftAlpha > 0.003F) {
                collector.order(2).submitCustomGeometry(identity, RenderTypes.eyes(BEAM),
                        (pose, buffer) -> shaft(buffer, shaft));
            }
            if (shaft.hit != null) {
                collector.order(2).submitCustomGeometry(identity, RenderTypes.eyes(POOL),
                        (pose, buffer) -> pool(buffer, shaft));
            }
        }
    }

    private record Glow(Vector3f at, float size, float r, float g, float b, float alpha, boolean pullForward) {
    }

    /** Each blade's tip light, smeared along its arc by {@code turn} (radians a tick) about the hub. */
    private static void rotorTips(RenderPassInfo<VehicleRenderState> passInfo, PoseStack.Pose pose, VertexConsumer buffer,
                                  AircraftLights.RotorTips tips, float turn, Quaternionf facing, float alpha) {
        float r = ((tips.colour() >> 16) & 0xFF) / 255.0F;
        float g = ((tips.colour() >> 8) & 0xFF) / 255.0F;
        float b = (tips.colour() & 0xFF) / 255.0F;
        float half = Math.min(turn * 0.5F, Mth.PI / 5.0F + 0.05F);
        float step = 1.5F * Mth.DEG_TO_RAD;
        int points = Math.max(1, Mth.ceil(2.0F * half / step));
        float each = alpha * Mth.clamp(3.0F / points, 0.1F, 1.0F);
        Vector3f tipModel = new Vector3f(tips.tip()).div(16.0F);
        Vector3f hubModel = new Vector3f(tips.hub()).div(16.0F);
        passInfo.renderPosed(() -> {
            for (String name : tips.blades()) {
                GeoBone blade = passInfo.model().getBone(name).orElse(null);
                if (blade == null) {
                    continue;
                }
                PoseStack bonePose = new PoseStack();
                bonePose.last().set(pose);
                Deque<GeoBone> chain = new ArrayDeque<>();
                for (GeoBone bone = blade; bone != null; bone = bone.parent()) {
                    chain.push(bone);
                }
                for (GeoBone bone : chain) {
                    RenderUtil.prepMatrixForBone(bonePose, bone);
                }
                Matrix4f matrix = bonePose.last().pose();
                Vector3f tip = matrix.transformPosition(new Vector3f(tipModel));
                Vector3f hub = matrix.transformPosition(new Vector3f(hubModel));
                Vector3f axis = matrix.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
                Vector3f arm = new Vector3f(tip).sub(hub);
                for (int i = 0; i < points; i++) {
                    float angle = points == 1 ? 0.0F : -half + 2.0F * half * i / (points - 1);
                    Vector3f at = new Quaternionf().fromAxisAngleRad(axis, angle).transform(new Vector3f(arm)).add(hub);
                    glow(buffer, facing, at, 0.36F, r, g, b, each, false);
                }
            }
        });
    }

    /** A searchlight this frame, in the camera-relative frame. */
    private static final class Beam {
        Vector3f lens;
        Vector3f dir;
        float length;
        float halfAngle;
        float r;
        float g;
        float b;
        float lensSize;
        float lensAlpha;
        float shaftAlpha;
        BlockHitResult hit;
        Vector3f hitAt;
        float poolRadius;
        float poolAlpha;
    }

    private static Beam beam(VehicleEntity vehicle, AircraftLights.Searchlight light, float level, Matrix4f model,
                             CameraRenderState camera) {
        Beam beam = new Beam();
        beam.lens = model.transformPosition(light.modelPos());
        beam.dir = model.transformDirection(light.modelDir()).normalize();
        beam.halfAngle = light.halfAngle() * Mth.DEG_TO_RAD;
        beam.r = ((light.colour() >> 16) & 0xFF) / 255.0F;
        beam.g = ((light.colour() >> 8) & 0xFF) / 255.0F;
        beam.b = (light.colour() & 0xFF) / 255.0F;
        Vec3 from = camera.pos.add(beam.lens.x, beam.lens.y, beam.lens.z);
        beam.hit = AircraftLights.clip(vehicle.level(), vehicle, from, beam.dir, light.range());
        float distance = beam.hit == null ? light.range() : (float) beam.hit.getLocation().distanceTo(from);
        beam.length = Math.min(distance, 40.0F);
        // Glare: the lens flares when the beam points at the camera.
        Vector3f toCamera = new Vector3f(beam.lens).negate().normalize();
        float glare = (float) Math.pow(Math.max(0.0F, toCamera.dot(beam.dir)), 6.0D);
        beam.lensSize = 0.55F + 2.4F * glare;
        beam.lensAlpha = level * (0.85F + 0.15F * glare);
        // A faint shaft through the air, plainer in rain.
        float rain = vehicle.level().getRainLevel(1.0F);
        beam.shaftAlpha = level * 0.17F * (1.0F + 1.3F * rain);
        if (beam.hit != null) {
            Vec3 hit = beam.hit.getLocation().subtract(camera.pos);
            beam.hitAt = new Vector3f((float) hit.x, (float) hit.y, (float) hit.z);
            beam.poolRadius = Mth.clamp(0.4F + distance * Mth.sin(beam.halfAngle), 1.0F, 8.0F);
            beam.poolAlpha = level * 0.6F * Mth.clamp(1.25F - distance / light.range(), 0.0F, 1.0F);
        }
        return beam;
    }

    /**
     * A glow facing the camera. Pulled a little towards the camera so a light set on a curved skin is not cut in half by
     * it (not from the pilot's seat, where it would show through the airframe).
     */
    private static void glow(VertexConsumer buffer, Quaternionf facing, Vector3f at, float size, float r, float g,
                             float b, float alpha, boolean pullForward) {
        float distance = at.length();
        // Far off, a light keeps a minimum apparent size, so a distant aircraft still shows as a point of light.
        float half = Math.max(size * 0.7F, distance * 0.006F);
        Vector3f centre = new Vector3f(at);
        if (pullForward && distance > 0.5F) {
            centre.mul(1.0F - Math.min(0.3F, half * 0.6F) / distance);
        }
        Vector3f right = facing.transform(new Vector3f(half, 0.0F, 0.0F));
        Vector3f up = facing.transform(new Vector3f(0.0F, half, 0.0F));
        // A coloured halo round a core near white, as a bright lamp looks to the eye.
        billboard(buffer, centre, right, up, colour(r, g, b, alpha));
        float core = 0.38F;
        billboard(buffer, centre, new Vector3f(right).mul(core), new Vector3f(up).mul(core),
                colour(Mth.lerp(0.6F, r, 1.0F), Mth.lerp(0.6F, g, 1.0F), Mth.lerp(0.6F, b, 1.0F), alpha));
    }

    private static void billboard(VertexConsumer buffer, Vector3f centre, Vector3f right, Vector3f up, int[] colour) {
        quad(buffer,
                new Vector3f(centre).sub(right).sub(up),
                new Vector3f(centre).add(right).sub(up),
                new Vector3f(centre).add(right).add(up),
                new Vector3f(centre).sub(right).add(up),
                0.0F, 1.0F, colour, colour);
    }

    /** The beam: a strip along its axis turned to face the camera, widening with the cone, fading as it goes. */
    private static void shaft(VertexConsumer buffer, Beam beam) {
        int segments = 5;
        float tan = Mth.sin(beam.halfAngle) / Mth.cos(beam.halfAngle);
        for (int i = 0; i < segments; i++) {
            float s0 = beam.length * i / segments;
            float s1 = beam.length * (i + 1) / segments;
            Vector3f p0 = new Vector3f(beam.dir).mul(s0).add(beam.lens);
            Vector3f p1 = new Vector3f(beam.dir).mul(s1).add(beam.lens);
            Vector3f middle = new Vector3f(p0).add(p1).mul(0.5F);
            Vector3f side = new Vector3f(beam.dir).cross(middle);
            if (side.lengthSquared() < 1.0E-6F) {
                continue;
            }
            side.normalize();
            // Seen end on the strip has no width to show; let it go rather than flicker as a line.
            float endOn = Math.abs(new Vector3f(middle).normalize().dot(beam.dir));
            float view = 1.0F - endOn * endOn * endOn * endOn;
            float w0 = 0.12F + s0 * tan * 0.85F;
            float w1 = 0.12F + s1 * tan * 0.85F;
            int[] c0 = colour(beam.r, beam.g, beam.b, beam.shaftAlpha * view * fade(s0, beam.length));
            int[] c1 = colour(beam.r, beam.g, beam.b, beam.shaftAlpha * view * fade(s1, beam.length));
            quad(buffer,
                    new Vector3f(side).mul(-w0).add(p0),
                    new Vector3f(side).mul(w0).add(p0),
                    new Vector3f(side).mul(w1).add(p1),
                    new Vector3f(side).mul(-w1).add(p1),
                    0.0F, 1.0F, c0, c1);
        }
    }

    private static float fade(float s, float length) {
        float along = 1.0F - s / length;
        return along * along / (1.0F + s / 20.0F);
    }

    /** The pool of light on the surface the beam meets: stretched along the beam where it strikes at a slant. */
    private static void pool(VertexConsumer buffer, Beam beam) {
        Direction face = beam.hit.getDirection();
        Vector3f normal = new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ());
        Vector3f along = new Vector3f(beam.dir).sub(new Vector3f(normal).mul(beam.dir.dot(normal)));
        if (along.lengthSquared() < 1.0E-4F) {
            along = Math.abs(normal.y) > 0.5F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
        }
        along.normalize();
        Vector3f across = new Vector3f(normal).cross(along).normalize();
        // A low beam's pool runs long; past two and a half times as long as wide it only spreads thin.
        float slant = Math.max(Math.abs(beam.dir.dot(normal)), 0.4F);
        float lengthways = beam.poolRadius / slant;
        Vector3f centre = new Vector3f(normal).mul(0.04F).add(beam.hitAt);
        Vector3f a = new Vector3f(along).mul(lengthways);
        Vector3f c = new Vector3f(across).mul(beam.poolRadius);
        int[] colour = colour(beam.r, beam.g, beam.b, beam.poolAlpha);
        quad(buffer,
                new Vector3f(centre).sub(a).sub(c),
                new Vector3f(centre).add(a).sub(c),
                new Vector3f(centre).add(a).add(c),
                new Vector3f(centre).sub(a).add(c),
                0.0F, 1.0F, colour, colour);
    }

    private static int[] colour(float r, float g, float b, float alpha) {
        float a = Mth.clamp(alpha, 0.0F, 1.0F);
        return new int[]{Math.round(r * 255.0F), Math.round(g * 255.0F), Math.round(b * 255.0F), Math.round(a * 255.0F)};
    }

    /** A quad drawn from both sides: v0 at the first two corners, v1 at the last two. */
    private static void quad(VertexConsumer buffer, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, float v0,
                             float v1, int[] c0, int[] c1) {
        vertex(buffer, p0, 0.0F, v0, c0);
        vertex(buffer, p1, 1.0F, v0, c0);
        vertex(buffer, p2, 1.0F, v1, c1);
        vertex(buffer, p3, 0.0F, v1, c1);
        vertex(buffer, p3, 0.0F, v1, c1);
        vertex(buffer, p2, 1.0F, v1, c1);
        vertex(buffer, p1, 1.0F, v0, c0);
        vertex(buffer, p0, 0.0F, v0, c0);
    }

    private static void vertex(VertexConsumer buffer, Vector3f p, float u, float v, int[] colour) {
        buffer.addVertex(p.x, p.y, p.z)
                .setColor(colour[0], colour[1], colour[2], colour[3])
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    /**
     * Takes the model's quads as GeckoLib writes them and passes on only those a light reaches, each vertex coloured by
     * the lights round it: brighter nearer, and on the side that faces the light ({@code wrap} lets some round the
     * back, as light does in a small cockpit). Each light is {x, y, z, r, g, b, intensity, radius, wrap}.
     */
    private static final class SpillConsumer implements VertexConsumer {
        private final VertexConsumer out;
        private final float[][] lights;
        private final float[] position = new float[12];
        private final float[] uv = new float[8];
        private final int[] colour = new int[4];
        private final float[] normal = new float[3];
        private int overlay;
        private int count;

        SpillConsumer(VertexConsumer out, float[][] lights) {
            this.out = out;
            this.lights = lights;
        }

        @Override
        public void addVertex(float x, float y, float z, int argb, float u, float v, int overlay, int light,
                              float nx, float ny, float nz) {
            int i = this.count;
            this.position[i * 3] = x;
            this.position[i * 3 + 1] = y;
            this.position[i * 3 + 2] = z;
            this.uv[i * 2] = u;
            this.uv[i * 2 + 1] = v;
            if (i == 0) {
                float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
                float scale = length > 1.0E-6F ? 1.0F / length : 0.0F;
                this.normal[0] = nx * scale;
                this.normal[1] = ny * scale;
                this.normal[2] = nz * scale;
                this.overlay = overlay;
            }
            if (++this.count == 4) {
                this.count = 0;
                this.flush();
            }
        }

        private void flush() {
            boolean any = false;
            for (int i = 0; i < 4; i++) {
                this.colour[i] = this.light(this.position[i * 3], this.position[i * 3 + 1], this.position[i * 3 + 2]);
                any |= (this.colour[i] >>> 24) > 1;
            }
            if (!any) {
                return;
            }
            for (int i = 0; i < 4; i++) {
                this.out.addVertex(this.position[i * 3], this.position[i * 3 + 1], this.position[i * 3 + 2],
                        this.colour[i], this.uv[i * 2], this.uv[i * 2 + 1], this.overlay, 0x00F000F0,
                        this.normal[0], this.normal[1], this.normal[2]);
            }
        }

        private int light(float x, float y, float z) {
            float r = 0.0F;
            float g = 0.0F;
            float b = 0.0F;
            for (float[] light : this.lights) {
                float dx = light[0] - x;
                float dy = light[1] - y;
                float dz = light[2] - z;
                float d2 = dx * dx + dy * dy + dz * dz;
                float radius = light[7];
                if (d2 >= radius * radius) {
                    continue;
                }
                float d = Mth.sqrt(d2);
                float reach = 1.0F - d / radius;
                float facing = d > 1.0E-4F ? (this.normal[0] * dx + this.normal[1] * dy + this.normal[2] * dz) / d : 1.0F;
                float wrap = light[8];
                float lit = Math.max(0.0F, facing * (1.0F - wrap) + wrap);
                float w = light[6] * reach * reach * lit;
                r += light[3] * w;
                g += light[4] * w;
                b += light[5] * w;
            }
            float max = Math.max(r, Math.max(g, b));
            if (max <= 0.004F) {
                return 0;
            }
            float alpha = Math.min(1.0F, max);
            return (Math.round(alpha * 255.0F) << 24)
                    | (Math.round(r / max * 255.0F) << 16)
                    | (Math.round(g / max * 255.0F) << 8)
                    | Math.round(b / max * 255.0F);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }
}
