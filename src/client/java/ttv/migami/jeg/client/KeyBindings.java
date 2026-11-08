package ttv.migami.jeg.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.client.overlay.VehicleHudOverlay;

public final class KeyBindings {
    private KeyBindings() {}

    private static boolean initialised;
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Reference.id("jeg"));

    public static final KeyMapping RELOAD = new KeyMapping(
            "key.jeg.reload",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY
    );

    public static final KeyMapping ATTACHMENTS = new KeyMapping(
            "key.jeg.attachments",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY
    );

    public static final KeyMapping MELEE = new KeyMapping(
            "key.jeg.melee",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    public static final KeyMapping INSPECT = new KeyMapping(
            "key.jeg.inspect",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            CATEGORY
    );

    public static final KeyMapping VEHICLE_FREE_LOOK = new KeyMapping(
            "key.jeg.vehicle_free_look",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY
    );

    public static final KeyMapping VEHICLE_SWITCH_WEAPON = new KeyMapping(
            "key.jeg.vehicle_switch_weapon",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY
    );

    public static final KeyMapping VEHICLE_PREVIOUS_WEAPON = new KeyMapping(
            "key.jeg.vehicle_previous_weapon",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            CATEGORY
    );

    public static final KeyMapping VEHICLE_DEPLOY_DECOY = new KeyMapping(
            "key.jeg.vehicle_deploy_decoy",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY
    );

    public static final KeyMapping VEHICLE_SEEK = new KeyMapping(
            "key.jeg.vehicle_seek",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY
    );

    public static final KeyMapping VEHICLE_BRAKE_DESCEND = new KeyMapping(
            "key.jeg.vehicle_brake_descend",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            CATEGORY
    );

    public static final KeyMapping VEHICLE_AUTO_FORWARD = new KeyMapping(
            "key.jeg.vehicle_auto_forward",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY
    );

    public static final KeyMapping VEHICLE_CHANGE_SEAT = new KeyMapping(
            "key.jeg.vehicle_change_seat",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            CATEGORY
    );

    public static final KeyMapping VEHICLE_DISMOUNT = new KeyMapping(
            "key.jeg.vehicle_dismount",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            CATEGORY
    );

    public static final KeyMapping VEHICLE_PLAYER_INVENTORY = new KeyMapping(
            "key.jeg.vehicle_player_inventory",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            CATEGORY
    );

    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        KeyMappingHelper.registerKeyMapping(RELOAD);
        KeyMappingHelper.registerKeyMapping(ATTACHMENTS);
        KeyMappingHelper.registerKeyMapping(MELEE);
        KeyMappingHelper.registerKeyMapping(INSPECT);
        KeyMappingHelper.registerKeyMapping(VEHICLE_FREE_LOOK);
        KeyMappingHelper.registerKeyMapping(VEHICLE_SWITCH_WEAPON);
        KeyMappingHelper.registerKeyMapping(VEHICLE_PREVIOUS_WEAPON);
        KeyMappingHelper.registerKeyMapping(VEHICLE_DEPLOY_DECOY);
        KeyMappingHelper.registerKeyMapping(VEHICLE_SEEK);
        KeyMappingHelper.registerKeyMapping(VEHICLE_BRAKE_DESCEND);
        KeyMappingHelper.registerKeyMapping(VEHICLE_AUTO_FORWARD);
        KeyMappingHelper.registerKeyMapping(VEHICLE_CHANGE_SEAT);
        KeyMappingHelper.registerKeyMapping(VEHICLE_DISMOUNT);
        KeyMappingHelper.registerKeyMapping(VEHICLE_PLAYER_INVENTORY);
    }

    /** Names of Zoomify's zoom keys; Zoomify binds zoom to C by default, the same key as vehicle free look. */
    public static final String ZOOMIFY_ZOOM_KEY_PREFIX = "zoomify.key.zoom";

    /**
     * Whether vehicle free look is held. A passenger who keeps their own hands (not flying, not on a vehicle gun) zooms
     * with Zoomify instead when its zoom shares the free-look key, so the one key does not also swing their view away.
     */
    public static boolean vehicleFreeLookDown() {
        if (!VEHICLE_FREE_LOOK.isDown()) {
            return false;
        }
        if (VehicleHudOverlay.shouldReplaceHotbar()) {
            return true;
        }
        for (KeyMapping mapping : Minecraft.getInstance().options.keyMappings) {
            if (mapping.getName().startsWith(ZOOMIFY_ZOOM_KEY_PREFIX) && mapping.same(VEHICLE_FREE_LOOK)) {
                return false;
            }
        }
        return true;
    }
}
