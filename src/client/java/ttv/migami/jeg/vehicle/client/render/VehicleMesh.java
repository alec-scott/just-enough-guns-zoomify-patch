package ttv.migami.jeg.vehicle.client.render;

import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ttv.migami.jeg.Reference;

/**
 * A vehicle's curved skin, drawn as smooth-shaded quads on its GeckoLib bones. GeckoLib draws only boxes (it reads a
 * model's {@code poly_mesh} but never draws it), so a vehicle that must look curved (the Chinook, the user's request of
 * 2026-11-21) keeps its boxes for the parts that are boxes and has the rest drawn from a mesh here.
 * <p>
 * The mesh file ({@code assets/<ns>/models/mesh/<id>.bin}, written by the vehicle's generator, e.g.
 * design/chinook/mesh.py) holds parts, each riding one bone: its vertices are in the model's space (blocks, the frame
 * GeckoLib draws the bone's boxes in), so the bone's own transform moves them exactly as it moves its boxes (rotors
 * turn, the ramp swings, the door slides). Little-endian: "JMSH", version 1, the part count, then per part the bone's
 * name (u16 length, UTF-8), a kind (0 opaque, 1 glass, 2 opaque but only seen from inside or close by: linings), the
 * quad count, and per quad four vertices of x, y, z, u, v,
 * nx, ny, nz (floats).
 */
public final class VehicleMesh {
    private static final Logger LOGGER = LogManager.getLogger(Reference.MOD_ID + ".VehicleMesh");
    private static final Map<Identifier, VehicleMesh> CACHE = new ConcurrentHashMap<>();
    private static final int FLOATS_PER_VERTEX = 8;
    private static final VehicleMesh EMPTY = new VehicleMesh(List.of());

    private record Part(String bone, boolean glass, boolean inside, float[] data) {}

    private final List<Part> parts;

    private VehicleMesh(List<Part> parts) {
        this.parts = parts;
    }

    /** The mesh at {@code id} (read once; an empty mesh if it is missing or broken, with a warning). */
    public static VehicleMesh get(Identifier id) {
        return CACHE.computeIfAbsent(id, VehicleMesh::read);
    }

    public boolean isEmpty() {
        return this.parts.isEmpty();
    }

    /**
     * Draws every part on its bone in this render pass, with {@code texture}: opaque parts in the cutout pass (lit and
     * shadowed by shader packs as the boxes are), glass in the translucent one. Linings are drawn only when {@code near}
     * (the camera close enough to see inside), which halves the cost of a vehicle seen from afar.
     */
    public void attach(RenderPassInfo<?> pass, Identifier texture, boolean near) {
        RenderType opaque = RenderTypes.entityCutout(texture);
        RenderType glass = RenderTypes.entityTranslucent(texture);
        int light = pass.packedLight();
        int colour = pass.renderColor();
        for (Part part : this.parts) {
            if (part.inside() && !near) {
                continue;
            }
            pass.model().getBone(part.bone()).ifPresent(bone -> addPart(pass, bone, part, part.glass() ? glass : opaque, light, colour));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addPart(RenderPassInfo pass, com.geckolib.cache.model.GeoBone bone, Part part, RenderType type, int light, int colour) {
        pass.addPerBoneRender(bone, (info, drawn, out) -> out.submitCustomGeometry(((RenderPassInfo<?>) info).poseStack(), type,
                (pose, buffer) -> emit(pose, buffer, part.data(), light, colour)));
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer buffer, float[] data, int light, int colour) {
        for (int i = 0; i < data.length; i += FLOATS_PER_VERTEX) {
            buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(colour)
                    .setUv(data[i + 3], data[i + 4])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
        }
    }

    private static VehicleMesh read(Identifier id) {
        var resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            LOGGER.warn("No vehicle mesh at {}", id);
            return EMPTY;
        }
        try (InputStream in = resource.get().open()) {
            ByteBuffer buffer = ByteBuffer.wrap(new DataInputStream(in).readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            byte[] magic = new byte[4];
            buffer.get(magic);
            if (!"JMSH".equals(new String(magic, StandardCharsets.US_ASCII)) || buffer.getInt() != 1) {
                LOGGER.warn("{} is not a version 1 vehicle mesh", id);
                return EMPTY;
            }
            int count = buffer.getInt();
            List<Part> parts = new ArrayList<>(count);
            for (int p = 0; p < count; p++) {
                byte[] name = new byte[Short.toUnsignedInt(buffer.getShort())];
                buffer.get(name);
                byte kind = buffer.get();
                int quads = buffer.getInt();
                float[] data = new float[quads * 4 * FLOATS_PER_VERTEX];
                buffer.asFloatBuffer().get(data);
                buffer.position(buffer.position() + data.length * Float.BYTES);
                parts.add(new Part(new String(name, StandardCharsets.UTF_8), kind == 1, kind == 2, data));
            }
            return new VehicleMesh(List.copyOf(parts));
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read the vehicle mesh {}", id, e);
            return EMPTY;
        }
    }
}
