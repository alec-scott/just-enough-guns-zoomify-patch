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
 * {@code /jegstick <dx> <dy> <ticks>} moves the cursor that many pixels each tick, as a hand on the mouse would: an
 * aircraft flies from the cursor's movement, which mcpfabric cannot make (negative dy pulls the nose up).
 * {@code /jegclick <x> <y> [shift]} clicks the open screen at GUI coordinates, and {@code /jegclick slot <index> [shift]}
 * on a menu slot's centre, as a real left click (mcpfabric's ui.click only takes named widgets; shift 1 holds Shift).
 * {@code /jegsounds <ticks>} logs every sound the client starts for that long (id, volume, pitch, tick, looping), in
 * the log as {@code [jegsounds]} lines and as a count per id in chat: we can't hear the game.
 * Lives in the gametest source set, so it reaches only dev and pack-client runs, never the shipped jar.
 */
public final class DevKeysClient implements ClientModInitializer {
    /** Bindings held down, with the client ticks left before they are released. */
    private static final Map<KeyMapping, Integer> HELD = new HashMap<>();
    /** The cursor movement /jegstick asked for: pixels a tick across and down, and the ticks left. */
    private static double stickX;
    private static double stickY;
    private static int stickTicks;

    @Override
    public void onInitializeClient() {
        ChinookDevTools.init();
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("jegkey")
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("list").executes(DevKeysClient::list))
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("key", StringArgumentType.word())
                                .executes(context -> press(context, 1))
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("ticks", IntegerArgumentType.integer(1, 1200))
                                        .executes(context -> press(context, IntegerArgumentType.getInteger(context, "ticks")))))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("jegstick")
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("dx", IntegerArgumentType.integer(-200, 200))
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("dy", IntegerArgumentType.integer(-200, 200))
                                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("ticks", IntegerArgumentType.integer(1, 1200))
                                                .executes(DevKeysClient::stick))))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("jegclick")
                        .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("slot")
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("index", IntegerArgumentType.integer(0, 512))
                                        .executes(context -> clickSlot(context, false))
                                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("shift", IntegerArgumentType.integer(0, 1))
                                                .executes(context -> clickSlot(context, IntegerArgumentType.getInteger(context, "shift") == 1)))))
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("x", IntegerArgumentType.integer(0, 10000))
                                .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("y", IntegerArgumentType.integer(0, 10000))
                                        .executes(context -> clickAt(context, false))
                                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("shift", IntegerArgumentType.integer(0, 1))
                                                .executes(context -> clickAt(context, IntegerArgumentType.getInteger(context, "shift") == 1)))))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("jegsounds")
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("ticks", IntegerArgumentType.integer(1, 2400))
                                .executes(DevKeysClient::sounds))));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("jegfps")
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, Integer>argument("seconds", IntegerArgumentType.integer(1, 120))
                                .executes(DevKeysClient::fps))));
        ClientTickEvents.START_CLIENT_TICK.register(DevKeysClient::moveCursor);
        ClientTickEvents.END_CLIENT_TICK.register(DevKeysClient::tick);
        ClientTickEvents.END_CLIENT_TICK.register(DevKeysClient::sampleFps);
    }

    // /jegfps <seconds>: the game's frame rate over that long, for comparing settings (shader options) by play-test,
    // since mcpfabric reports none and the F3 overlay can't be opened through a key binding. Averages the game's own
    // per-second counter and takes the last frame's time each tick for the slow frames.
    private static int fpsTicksLeft;
    private static final java.util.List<Integer> FPS_SAMPLES = new java.util.ArrayList<>();
    private static final java.util.List<Long> FRAME_NS_SAMPLES = new java.util.ArrayList<>();
    private static int lastFpsSecond = -1;

    private static int fps(CommandContext<FabricClientCommandSource> context) {
        fpsTicksLeft = IntegerArgumentType.getInteger(context, "seconds") * 20;
        FPS_SAMPLES.clear();
        FRAME_NS_SAMPLES.clear();
        lastFpsSecond = -1;
        context.getSource().sendFeedback(Component.literal("[jegfps] sampling " + fpsTicksLeft / 20 + " s"));
        return 1;
    }

    private static void sampleFps(Minecraft client) {
        if (fpsTicksLeft <= 0) {
            return;
        }
        fpsTicksLeft--;
        FRAME_NS_SAMPLES.add(client.getFrameTimeNs());
        int second = fpsTicksLeft / 20;
        if (second != lastFpsSecond) {
            lastFpsSecond = second;
            FPS_SAMPLES.add(client.getFps());
        }
        if (fpsTicksLeft == 0 && client.player != null) {
            double avg = FPS_SAMPLES.stream().mapToInt(Integer::intValue).average().orElse(0);
            int min = FPS_SAMPLES.stream().mapToInt(Integer::intValue).min().orElse(0);
            java.util.List<Long> sorted = new java.util.ArrayList<>(FRAME_NS_SAMPLES);
            sorted.sort(null);
            double p99ms = sorted.isEmpty() ? 0 : sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * 0.99))) / 1.0e6;
            double medianMs = sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2) / 1.0e6;
            String line = String.format(java.util.Locale.ROOT, "[jegfps] avg %.1f fps, min %d fps, frame median %.2f ms, p99 %.2f ms (%d s)",
                    avg, min, medianMs, p99ms, FPS_SAMPLES.size());
            client.gui.getChat().addClientSystemMessage(Component.literal(line));
            org.slf4j.LoggerFactory.getLogger("jegfps").info(line);
        }
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

    private static int clickAt(CommandContext<FabricClientCommandSource> context, boolean shift) {
        return click(context, IntegerArgumentType.getInteger(context, "x"), IntegerArgumentType.getInteger(context, "y"), shift);
    }

    private static int clickSlot(CommandContext<FabricClientCommandSource> context, boolean shift) {
        if (!(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
            context.getSource().sendError(Component.literal("No container screen open"));
            return 0;
        }
        int index = IntegerArgumentType.getInteger(context, "index");
        if (index >= screen.getMenu().slots.size()) {
            context.getSource().sendError(Component.literal("No slot " + index));
            return 0;
        }
        var slot = screen.getMenu().slots.get(index);
        try {
            var left = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("leftPos");
            var top = net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class.getDeclaredField("topPos");
            left.setAccessible(true);
            top.setAccessible(true);
            return click(context, left.getInt(screen) + slot.x + 8, top.getInt(screen) + slot.y + 8, shift);
        } catch (ReflectiveOperationException e) {
            context.getSource().sendError(Component.literal("jegclick: " + e));
            return 0;
        }
    }

    /** A left click at GUI coordinates on the open screen: press and release, with Shift held if asked. */
    private static int click(CommandContext<FabricClientCommandSource> context, int x, int y, boolean shift) {
        var screen = Minecraft.getInstance().screen;
        if (screen == null) {
            context.getSource().sendError(Component.literal("No screen open"));
            return 0;
        }
        var info = new net.minecraft.client.input.MouseButtonInfo(0, shift ? 1 : 0);
        var event = new net.minecraft.client.input.MouseButtonEvent(x, y, info);
        boolean handled = screen.mouseClicked(event, false);
        screen.mouseReleased(event);
        context.getSource().sendFeedback(Component.literal("Clicked " + x + ", " + y + (shift ? " with Shift" : "") + (handled ? "" : " (not handled)")));
        return 1;
    }

    private static final org.slf4j.Logger SOUND_LOG = org.slf4j.LoggerFactory.getLogger("jegsounds");
    private static final Map<String, Integer> HEARD = new java.util.TreeMap<>();
    private static int soundTicks;
    private static int soundTick;
    private static net.minecraft.client.sounds.SoundEventListener soundListener;

    private static int sounds(CommandContext<FabricClientCommandSource> context) {
        var manager = Minecraft.getInstance().getSoundManager();
        if (soundListener == null) {
            soundListener = (sound, events, range) -> {
                if (soundTicks <= 0) return;
                String id = sound.getIdentifier().toString();
                synchronized (HEARD) {
                    HEARD.merge(id, 1, Integer::sum);
                }
                SOUND_LOG.info("[jegsounds] t{} {} vol {} pitch {} loop {} rel {} at {} {} {}", soundTick, id,
                        String.format("%.2f", sound.getVolume()), String.format("%.2f", sound.getPitch()), sound.isLooping(),
                        sound.isRelative(), (int) sound.getX(), (int) sound.getY(), (int) sound.getZ());
            };
            manager.addListener(soundListener);
        }
        synchronized (HEARD) {
            HEARD.clear();
        }
        soundTick = 0;
        soundTicks = IntegerArgumentType.getInteger(context, "ticks");
        context.getSource().sendFeedback(Component.literal("Logging sounds for " + soundTicks + " ticks"));
        return 1;
    }

    private static void soundTick(Minecraft minecraft) {
        if (soundTicks <= 0) return;
        soundTick++;
        if (soundTick % 20 == 0) {
            logPlaying(minecraft);
        }
        if (--soundTicks == 0) {
            String summary;
            synchronized (HEARD) {
                summary = HEARD.entrySet().stream().map(e -> e.getKey().replace("jeg:", "") + " x" + e.getValue())
                        .collect(Collectors.joining(", "));
            }
            SOUND_LOG.info("[jegsounds] summary: {}", summary);
            minecraft.gui.getChat().addClientSystemMessage(Component.literal("Sounds: " + summary));
        }
    }

    /** Every playing JEG sound with its volume now (a loop's volume changes as it plays; the listener sees only its start). */
    private static void logPlaying(Minecraft minecraft) {
        try {
            Object engine = null;
            for (var field : minecraft.getSoundManager().getClass().getDeclaredFields()) {
                if (field.getType().getSimpleName().equals("SoundEngine")) {
                    field.setAccessible(true);
                    engine = field.get(minecraft.getSoundManager());
                }
            }
            if (engine == null) return;
            StringBuilder line = new StringBuilder();
            for (var field : engine.getClass().getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Map<?, ?> map = (Map<?, ?>) field.get(engine);
                if (map == null || map.isEmpty() || !(map.keySet().iterator().next() instanceof net.minecraft.client.resources.sounds.SoundInstance)) continue;
                for (Object key : new java.util.ArrayList<>(map.keySet())) {
                    var sound = (net.minecraft.client.resources.sounds.SoundInstance) key;
                    String id = sound.getIdentifier().toString();
                    if (!id.startsWith("jeg:")) continue;
                    line.append(id.substring(4)).append(' ').append(String.format("%.2f", sound.getVolume()))
                            .append('/').append(String.format("%.2f", sound.getPitch())).append("; ");
                }
                break;
            }
            SOUND_LOG.info("[jegsounds] t{} playing: {}", soundTick, line);
        } catch (ReflectiveOperationException | RuntimeException e) {
            SOUND_LOG.info("[jegsounds] playing: {}", e.toString());
        }
    }

    private static int stick(CommandContext<FabricClientCommandSource> context) {
        stickX = IntegerArgumentType.getInteger(context, "dx");
        stickY = IntegerArgumentType.getInteger(context, "dy");
        stickTicks = IntegerArgumentType.getInteger(context, "ticks");
        context.getSource().sendFeedback(Component.literal("Stick " + (int) stickX + ", " + (int) stickY + " a tick for " + stickTicks + " ticks"));
        return 1;
    }

    /**
     * Moves the cursor as a real mouse event would: the mouse handler's own onMove (what a GLFW cursor event calls), and
     * the real cursor put there too. Setting only the handler's position snapped back on the next real event, a flick
     * the size of every move so far (it put the bush plane's nose down 9 degrees mid-climb).
     */
    private static void moveCursor(Minecraft minecraft) {
        if (stickTicks <= 0) {
            return;
        }
        stickTicks--;
        try {
            var handler = minecraft.mouseHandler;
            long window = minecraft.getWindow().handle();
            double x = handler.xpos() + stickX;
            double y = handler.ypos() + stickY;
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(window, x, y);
            var onMove = handler.getClass().getDeclaredMethod("onMove", long.class, double.class, double.class);
            onMove.setAccessible(true);
            onMove.invoke(handler, window, x, y);
        } catch (ReflectiveOperationException e) {
            stickTicks = 0;
            minecraft.gui.getChat().addClientSystemMessage(Component.literal("jegstick: " + e));
        }
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
        soundTick(minecraft);
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
