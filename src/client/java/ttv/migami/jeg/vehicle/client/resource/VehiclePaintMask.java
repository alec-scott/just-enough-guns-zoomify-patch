package ttv.migami.jeg.vehicle.client.resource;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import ttv.migami.jeg.Reference;

/**
 * Which pixels of a vehicle texture belong to parts that keep their own finish when the vehicle is dyed: rotors, glass,
 * guns, rocket pods, landing gear. A vehicle lists those parts (model bones, each with everything attached to it) in
 * {@code assets/<ns>/vehicle_paint/<vehicle>.json} as {@code "unpainted_parts"}; every texture pixel any of their faces
 * samples is masked off. Where a part is modelled inside a bone that is otherwise paintwork (a rocket pod hung from
 * a stub wing), {@code "unpainted_cubes"} masks just that bone's own cubes lying wholly below a height:
 * {@code {"bone": "bone19", "max_top": 40.9}} (model pixels). Without that file the whole texture may take paint.
 */
public final class VehiclePaintMask {
    private static final Logger LOGGER = LogManager.getLogger(Reference.MOD_ID + ".VehiclePaint");

    private VehiclePaintMask() {}

    /**
     * Pixels (row-major, {@code width * height}) that must not take paint, or null when the vehicle masks nothing.
     *
     * @param texture the texture being painted, {@code textures/<path>.png}; its model is {@code geckolib/models/<path>.geo.json}
     */
    @Nullable
    public static boolean[] unpainted(ResourceManager resources, Identifier texture, String vehicle, int width, int height) {
        Identifier config = Identifier.fromNamespaceAndPath(texture.getNamespace(), "vehicle_paint/" + vehicle + ".json");
        String path = texture.getPath();
        if (!path.startsWith("textures/") || !path.endsWith(".png")) {
            return null;
        }
        Identifier model = Identifier.fromNamespaceAndPath(texture.getNamespace(),
                "geckolib/models/" + path.substring("textures/".length(), path.length() - ".png".length()) + ".geo.json");
        try {
            JsonObject configJson = read(resources, config);
            JsonObject modelJson = read(resources, model);
            if (configJson == null || modelJson == null) {
                return null;
            }
            List<String> parts = new ArrayList<>();
            if (configJson.has("unpainted_parts")) {
                for (JsonElement part : configJson.getAsJsonArray("unpainted_parts")) {
                    parts.add(part.getAsString());
                }
            }
            Map<String, Double> below = new HashMap<>();
            if (configJson.has("unpainted_cubes")) {
                for (JsonElement entry : configJson.getAsJsonArray("unpainted_cubes")) {
                    JsonObject rule = entry.getAsJsonObject();
                    below.put(rule.get("bone").getAsString(), rule.get("max_top").getAsDouble());
                }
            }
            return mask(modelJson, parts, below, width, height);
        } catch (Exception e) {
            LOGGER.warn("Could not read the paint mask for {} from {} and {}", vehicle, config, model, e);
            return null;
        }
    }

    @Nullable
    private static JsonObject read(ResourceManager resources, Identifier id) throws Exception {
        var resource = resources.getResource(id);
        if (resource.isEmpty()) {
            return null;
        }
        try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /** Masks every pixel the named bones (and the bones attached to them) sample. Exposed for checks. */
    public static boolean[] mask(JsonObject model, List<String> parts, int width, int height) {
        return mask(model, parts, Map.of(), width, height);
    }

    /**
     * As {@link #mask(JsonObject, List, int, int)}, and also the cubes of each bone in {@code below} whose top lies at or
     * under the given height.
     */
    public static boolean[] mask(JsonObject model, List<String> parts, Map<String, Double> below, int width, int height) {
        JsonObject geometry = model.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        JsonObject description = geometry.getAsJsonObject("description");
        double scaleX = width / description.get("texture_width").getAsDouble();
        double scaleY = height / description.get("texture_height").getAsDouble();

        Map<String, JsonObject> bones = new HashMap<>();
        Map<String, List<String>> children = new HashMap<>();
        for (JsonElement element : geometry.getAsJsonArray("bones")) {
            JsonObject bone = element.getAsJsonObject();
            String name = bone.get("name").getAsString();
            bones.put(name, bone);
            if (bone.has("parent")) {
                children.computeIfAbsent(bone.get("parent").getAsString(), key -> new ArrayList<>()).add(name);
            }
        }

        Set<String> masked = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(parts);
        while (!queue.isEmpty()) {
            String name = queue.poll();
            if (masked.add(name)) {
                queue.addAll(children.getOrDefault(name, List.of()));
            }
        }

        boolean[] mask = new boolean[width * height];
        Set<String> touched = new HashSet<>(masked);
        touched.addAll(below.keySet());
        for (String name : touched) {
            JsonObject bone = bones.get(name);
            if (bone == null) {
                LOGGER.warn("Paint mask names a part the model does not have: {}", name);
                continue;
            }
            JsonArray cubes = bone.getAsJsonArray("cubes");
            if (cubes == null) {
                continue;
            }
            boolean whole = masked.contains(name);
            Double maxTop = below.get(name);
            for (JsonElement cubeElement : cubes) {
                JsonObject cube = cubeElement.getAsJsonObject();
                if (!whole) {
                    double top = cube.getAsJsonArray("origin").get(1).getAsDouble() + cube.getAsJsonArray("size").get(1).getAsDouble();
                    if (maxTop == null || top > maxTop) {
                        continue;
                    }
                }
                JsonElement uv = cube.get("uv");
                if (uv == null) {
                    continue;
                }
                if (uv.isJsonObject()) {
                    // Per-face UVs: each face samples its own rectangle (a negative size flips it).
                    for (var face : uv.getAsJsonObject().entrySet()) {
                        JsonObject faceUv = face.getValue().getAsJsonObject();
                        JsonArray at = faceUv.getAsJsonArray("uv");
                        JsonArray size = faceUv.getAsJsonArray("uv_size");
                        double u = at.get(0).getAsDouble();
                        double v = at.get(1).getAsDouble();
                        double w = size == null ? 0.0D : size.get(0).getAsDouble();
                        double h = size == null ? 0.0D : size.get(1).getAsDouble();
                        fill(mask, width, height, u, v, u + w, v + h, scaleX, scaleY);
                    }
                } else if (uv.isJsonArray()) {
                    // Box UV: the cube's six faces unfold into a (2d + 2w) by (d + h) block.
                    JsonArray size = cube.getAsJsonArray("size");
                    double w = Math.floor(size.get(0).getAsDouble());
                    double h = Math.floor(size.get(1).getAsDouble());
                    double d = Math.floor(size.get(2).getAsDouble());
                    double u = uv.getAsJsonArray().get(0).getAsDouble();
                    double v = uv.getAsJsonArray().get(1).getAsDouble();
                    fill(mask, width, height, u, v, u + 2.0D * (d + w), v + d + h, scaleX, scaleY);
                }
            }
        }
        return mask;
    }

    private static void fill(boolean[] mask, int width, int height, double u0, double v0, double u1, double v1, double scaleX, double scaleY) {
        int x0 = (int) Math.floor(Math.min(u0, u1) * scaleX);
        int y0 = (int) Math.floor(Math.min(v0, v1) * scaleY);
        // A zero-width face still samples one texel column or row.
        int x1 = Math.max(x0 + 1, (int) Math.ceil(Math.max(u0, u1) * scaleX));
        int y1 = Math.max(y0 + 1, (int) Math.ceil(Math.max(v0, v1) * scaleY));
        for (int y = Math.max(0, y0); y < Math.min(height, y1); y++) {
            for (int x = Math.max(0, x0); x < Math.min(width, x1); x++) {
                mask[y * width + x] = true;
            }
        }
    }
}
