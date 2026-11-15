package ttv.migami.jeg.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Dev-only: {@code /jegkey <name> [ticks]} presses a key binding as if the player pressed it, for driving the game from
 * outside (mcpfabric's control tools only hold the vanilla movement keys). {@code name} is a binding id such as
 * {@code key.jeg.vehicle_tow}, or just {@code vehicle_tow} for JEG's own. {@code /jegkey list} lists JEG's bindings.
 * Lives in the gametest source set, so it reaches only dev and pack-client runs, never the shipped jar.
 */
public final class DevKeysClient implements ClientModInitializer {
    /** Bindings held down, with the client ticks left before they are released. */
    private static final Map<KeyMapping, Integer> HELD = new HashMap<>();

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("jegkey")
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list").executes(DevKeysClient::list))
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("key", StringArgumentType.word())
                                .executes(context -> press(context, 1))
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("ticks", IntegerArgumentType.integer(1, 1200))
                                        .executes(context -> press(context, IntegerArgumentType.getInteger(context, "ticks")))))));
        ClientTickEvents.END_CLIENT_TICK.register(DevKeysClient::tick);
    }

    private static int press(CommandContext<FabricClientCommandSource> context, int ticks) {
        String name = StringArgumentType.getString(context, "key");
        KeyMapping mapping = KeyMapping.get(name.contains(".") ? name : "key.jeg." + name);
        if (mapping == null) {
            context.getSource().sendError(Component.literal("No key binding " + name + "; try /jegkey list"));
            return 0;
        }
        InputConstants.Key key = InputConstants.getKey(mapping.saveString());
        KeyMapping.set(key, true);
        KeyMapping.click(key);
        HELD.put(mapping, ticks);
        context.getSource().sendFeedback(Component.literal("Pressed " + mapping.getName() + " for " + ticks + " ticks"));
        return 1;
    }

    private static int list(CommandContext<FabricClientCommandSource> context) {
        String names = java.util.Arrays.stream(Minecraft.getInstance().options.keyMappings)
                .map(KeyMapping::getName)
                .filter(name -> name.startsWith("key.jeg."))
                .map(name -> name.substring("key.jeg.".length()))
                .sorted()
                .collect(Collectors.joining(", "));
        context.getSource().sendFeedback(Component.literal(names));
        return 1;
    }

    private static void tick(Minecraft minecraft) {
        HELD.entrySet().removeIf(entry -> {
            int left = entry.getValue() - 1;
            if (left > 0) {
                entry.setValue(left);
                return false;
            }
            KeyMapping.set(InputConstants.getKey(entry.getKey().saveString()), false);
            return true;
        });
    }
}
