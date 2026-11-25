package ttv.migami.jeg.gametest;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ttv.migami.jeg.Reference;
import ttv.migami.jeg.vehicle.cabin.CabinClient;
import ttv.migami.jeg.vehicle.cabin.CabinMath;
import ttv.migami.jeg.vehicle.entity.ChinookEntity;

/**
 * Dev-only (gametest source set, never in the jar): {@code /jegfly <hover|circuit|land|off> [speed] [radius]} flies the
 * nearest Chinook along a scripted path from the server, as a pilot's reports would, so one test player can stand in
 * its cabin while it flies; and a probe of what that player sees ({@code /jegfly report}, {@code /jegfly reset}):
 * the camera's movement against the drawn cabin each frame and over a second while standing still, the walking speed in
 * cabin coordinates, and the Chinook's measured speed.
 */
final class ChinookDevTools {
    private static final Logger LOG = LoggerFactory.getLogger("jeg-chinook-dev");
    private static final double G = 9.81D;

    private static ChinookEntity flying;
    private static String mode = "off";
    private static double heading, speed, vSpeed, bank, pitch, target = 30.0D, radius = 150.0D, climbTo = Double.NaN, groundY;

    private static final Stat JITTER = new Stat("camera move against the drawn cabin per frame, standing still (blocks)");
    private static final Stat DRIFT = new Stat("camera drift against the drawn cabin over 1 s, standing still (blocks)");
    private static final Stat WALK = new Stat("walking speed in cabin coordinates, keys held (m/s)");
    private static final Stat FLIGHT = new Stat("the Chinook's measured speed (m/s)");
    private static Vec3 lastCamera, cameraSecondAgo, walkSecondAgo, lastPosition;
    private static long second, walkSecond;

    private ChinookDevTools() {
    }

    static void init() {
        // Dev clients have no real session, so a second one can join a world opened to LAN only once it is offline:
        // /jegoffline, then /publish (design/playtest/second_client.py).
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(Commands.literal("jegoffline")
                .executes(c -> {
                    c.getSource().getServer().setUsesAuthentication(false);
                    return reply(c, "offline: dev clients can join once published");
                })));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(Commands.literal("jegfly")
                .then(Commands.literal("report").executes(ChinookDevTools::report))
                .then(Commands.literal("bbox").executes(ChinookDevTools::bbox))
                .then(Commands.literal("trace").then(Commands.argument("frames", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5000))
                        .executes(c -> {
                            traceFrames = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "frames");
                            return reply(c, "tracing " + traceFrames + " frames");
                        })))
                .then(Commands.literal("reset").executes(c -> {
                    for (Stat stat : new Stat[]{JITTER, DRIFT, WALK, FLIGHT}) {
                        stat.reset();
                    }
                    return reply(c, "stats reset");
                }))
                .then(Commands.argument("mode", StringArgumentType.word())
                        .executes(c -> fly(c, StringArgumentType.getString(c, "mode"), -1, -1))
                        .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0, 120))
                                .executes(c -> fly(c, StringArgumentType.getString(c, "mode"), DoubleArgumentType.getDouble(c, "speed"), -1))
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(20, 3000))
                                        .executes(c -> fly(c, StringArgumentType.getString(c, "mode"), DoubleArgumentType.getDouble(c, "speed"),
                                                DoubleArgumentType.getDouble(c, "radius"))))))));
        // Fill the nearest Chinook's cabin cells directly, to try every placeable block at once (the light, animation and
        // sound pass, 2026-11-26): /jegcabin put <i> <j> <k> <block>, /jegcabin clear. A refused block is reported.
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(Commands.literal("jegcabin")
                .then(Commands.literal("clear").executes(c -> {
                    ChinookEntity chinook = nearestChinook(c);
                    if (chinook == null) {
                        return reply(c, "no Chinook loaded");
                    }
                    for (net.minecraft.core.BlockPos cell : new ArrayList<>(chinook.cabinGrid().keySet())) {
                        chinook.cabinGrid().remove(cell);
                        ttv.migami.jeg.vehicle.cabin.CabinBlocks.removed(chinook, cell);
                    }
                    chinook.cabinGridChanged();
                    return reply(c, "cabin cleared");
                }))
                .then(Commands.literal("put")
                        .then(Commands.argument("cell", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .then(Commands.argument("block", net.minecraft.commands.arguments.blocks.BlockStateArgument.block(registry))
                                        .executes(c -> {
                                            ChinookEntity chinook = nearestChinook(c);
                                            if (chinook == null) {
                                                return reply(c, "no Chinook loaded");
                                            }
                                            net.minecraft.core.BlockPos cell = c.getArgument("cell",
                                                    net.minecraft.commands.arguments.coordinates.Coordinates.class).getBlockPos(c.getSource());
                                            net.minecraft.world.level.block.state.BlockState state = net.minecraft.commands.arguments.blocks.BlockStateArgument
                                                    .getBlock(c, "block").getState();
                                            if (!chinook.cabinLayout().isCell(cell)) {
                                                return reply(c, "not a cell: " + cell.toShortString());
                                            }
                                            Component refusal = ttv.migami.jeg.vehicle.cabin.CabinBlocks.refusal(state);
                                            if (refusal != null) {
                                                return reply(c, "refused: " + refusal.getString());
                                            }
                                            ttv.migami.jeg.vehicle.cabin.CabinBlocks.removed(chinook, cell);
                                            chinook.cabinGrid().put(cell, state);
                                            ttv.migami.jeg.vehicle.cabin.CabinBlocks.created(chinook, cell, state);
                                            chinook.cabinGridChanged();
                                            return reply(c, "put " + state + " at " + cell.toShortString());
                                        }))))));
        // Every sound the client plays, for a while, with where it is in the nearest Chinook's cabin (cell and cabin
        // coordinates): checks that blocks aboard sound at their block (/jegsounds <seconds>).
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(Commands.literal("jegsounds")
                .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 600)).executes(c -> {
                    soundLogUntil = System.currentTimeMillis() + com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "seconds") * 1000L;
                    return reply(c, "logging sounds");
                }))));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!soundListener) {
                soundListener = true;
                mc.getSoundManager().addListener((sound, events, range) -> {
                    if (System.currentTimeMillis() > soundLogUntil || mc.level == null || mc.player == null) {
                        return;
                    }
                    ChinookEntity nearest = null;
                    for (Entity entity : mc.level.entitiesForRendering()) {
                        if (entity instanceof ChinookEntity c && (nearest == null || c.distanceToSqr(mc.player) < nearest.distanceToSqr(mc.player))) {
                            nearest = c;
                        }
                    }
                    String where = "";
                    if (nearest != null) {
                        Vec3 local = CabinMath.toLocal(nearest.localToWorld(1.0F), new Vec3(sound.getX(), sound.getY(), sound.getZ()));
                        net.minecraft.core.BlockPos cell = ChinookEntity.LAYOUT.cellAt(local);
                        where = String.format(" local %.2f %.2f %.2f cell %s%s", local.x, local.y, local.z, cell.toShortString(),
                                ChinookEntity.LAYOUT.isCell(cell) ? "" : " (outside the grid)");
                    }
                    LOG.info("[cabin-sound] {} at {} {} {}{}", sound.getIdentifier(), String.format("%.2f", sound.getX()),
                            String.format("%.2f", sound.getY()), String.format("%.2f", sound.getZ()), where);
                });
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(ChinookDevTools::tick);
        HudElementRegistry.addLast(Reference.id("chinook_dev_probe"), ChinookDevTools::frame);
    }

    private static int fly(CommandContext<CommandSourceStack> context, String next, double newSpeed, double newRadius) {
        ChinookEntity nearest = null;
        for (Entity entity : context.getSource().getLevel().getAllEntities()) {
            if (entity instanceof ChinookEntity chinook && (nearest == null || chinook.distanceToSqr(context.getSource().getPosition())
                    < nearest.distanceToSqr(context.getSource().getPosition()))) {
                nearest = chinook;
            }
        }
        if (nearest == null) {
            return reply(context, "no Chinook loaded");
        }
        if (flying != nearest || "off".equals(mode)) {
            flying = nearest;
            heading = Math.toRadians(-nearest.getYRot());
            speed = vSpeed = bank = pitch = 0.0D;
            groundY = nearest.getY();
        }
        if (newSpeed >= 0) {
            target = newSpeed;
        }
        if (newRadius > 0) {
            radius = newRadius;
        }
        mode = next;
        if (("hover".equals(next) || "circuit".equals(next) || "steep".equals(next)) && (Double.isNaN(climbTo) || climbTo < nearest.getY())) {
            climbTo = nearest.getY() + 25.0D;
        }
        if ("hold".equals(next)) {
            mode = "hover";
            climbTo = nearest.getY();
        }
        return reply(context, "Chinook " + mode + " at " + target + " m/s, radius " + radius);
    }

    private static double approach(double value, double goal, double step) {
        return value < goal ? Math.min(goal, value + step) : Math.max(goal, value - step);
    }

    private static void tick(MinecraftServer server) {
        if (flying == null || flying.isRemoved() || "off".equals(mode)) {
            return;
        }
        double dt = 0.05D;
        Vec3 at = flying.position();
        double bankTarget = 0.0D, pitchTarget = 0.0D;
        double want = Double.isNaN(climbTo) ? 0.0D : Math.max(-4.0D, Math.min(4.0D, (climbTo - at.y) * 0.6D));
        switch (mode) {
            case "hover" -> speed = approach(speed, 0.0D, 4.0D * dt);
            case "circuit", "steep" -> {
                // "steep": a hard bank, up to 60 degrees (a passenger aboard glitched in hard banks, 2026-11-26).
                speed = approach(speed, target, 3.0D * dt);
                heading += speed / radius * dt;
                bankTarget = -Math.min(Math.atan(speed * speed / (G * radius)), Math.toRadians("steep".equals(mode) ? 60.0D : 30.0D));
                pitchTarget = Math.toRadians(5.0D) * speed / 80.0D;
            }
            case "land" -> {
                speed = approach(speed, 0.0D, 4.0D * dt);
                want = Math.max(-3.0D, groundY - at.y);
                if (at.y <= groundY + 0.05D && speed < 0.1D) {
                    mode = "off";
                }
            }
            default -> {
            }
        }
        vSpeed = approach(vSpeed, want, 3.0D * dt);
        bank += (bankTarget - bank) * Math.min(1.0D, dt / 0.8D);
        pitch += (pitchTarget - pitch) * Math.min(1.0D, dt / 1.0D);
        Vec3 velocity = new Vec3(Math.sin(heading) * speed, vSpeed, Math.cos(heading) * speed).scale(dt);
        Vec3 next = at.add(velocity);
        flying.setPos(next.x, next.y, next.z);
        flying.setDeltaMovement(velocity);
        flying.setYRot((float) -Math.toDegrees(heading));
        flying.setXRot((float) Math.toDegrees(pitch));
        flying.setRoll((float) Math.toDegrees(bank));
        flying.hurtMarked = true;
        if (lastPosition != null) {
            FLIGHT.add(lastPosition.distanceTo(next) * 20.0D);
        }
        lastPosition = next;
    }

    /** Frames left to trace (/jegfly trace N): each frame the nearest Chinook's drawn pose and the camera, to the log. */
    private static volatile int traceFrames;

    private static void trace(Minecraft mc, DeltaTracker delta) {
        if (traceFrames <= 0 || mc.player == null || mc.level == null) {
            return;
        }
        traceFrames--;
        ChinookEntity nearest = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ChinookEntity c && (nearest == null || c.distanceToSqr(mc.player) < nearest.distanceToSqr(mc.player))) {
                nearest = c;
            }
        }
        if (nearest == null) {
            return;
        }
        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        org.joml.Matrix4d pose = nearest.localToWorld(partialTick);
        org.joml.Vector3d at = pose.getTranslation(new org.joml.Vector3d());
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        Vec3 cameraLocal = CabinMath.toLocal(pose, camera);
        LOG.info("[chinook-trace] t {} p {} pos {} {} {} yaw {} pitch {} roll {} cam {} {} {} camLocal {} {} {} seat {} pilot {} aboard {}",
                mc.level.getGameTime(), String.format("%.3f", partialTick),
                String.format("%.4f", at.x), String.format("%.4f", at.y), String.format("%.4f", at.z),
                String.format("%.3f", nearest.getViewYRot(partialTick)), String.format("%.3f", nearest.getViewXRot(partialTick)),
                String.format("%.3f", nearest.roll()),
                String.format("%.4f", camera.x), String.format("%.4f", camera.y), String.format("%.4f", camera.z),
                String.format("%.4f", cameraLocal.x), String.format("%.4f", cameraLocal.y), String.format("%.4f", cameraLocal.z),
                nearest.seatOf(mc.player), nearest.getControllingPassenger() == mc.player, CabinClient.isAboard());
    }

    private static void frame(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        trace(mc, delta);
        if (!CabinClient.isAboard() || mc.player == null || mc.level == null
                || !(mc.level.getEntity(CabinClient.aboardVehicle()) instanceof ChinookEntity chinook)) {
            lastCamera = cameraSecondAgo = walkSecondAgo = null;
            return;
        }
        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        Vec3 camera = CabinMath.toLocal(chinook.localToWorld(partialTick), mc.gameRenderer.getMainCamera().position());
        boolean keys = mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()
                || mc.options.keyJump.isDown();
        long now = System.currentTimeMillis() / 1000L;
        if (!keys) {
            if (lastCamera != null) {
                JITTER.add(camera.distanceTo(lastCamera));
            }
            if (now != second) {
                if (cameraSecondAgo != null) {
                    DRIFT.add(camera.distanceTo(cameraSecondAgo));
                }
                cameraSecondAgo = camera;
                second = now;
            }
            walkSecondAgo = null;
        } else {
            cameraSecondAgo = null;
            if (now != walkSecond) {
                if (walkSecondAgo != null) {
                    WALK.add(new Vec3(camera.x - walkSecondAgo.x, 0.0D, camera.z - walkSecondAgo.z).length());
                }
                walkSecondAgo = camera;
                walkSecond = now;
            }
        }
        lastCamera = camera;
    }

    /** The nearest Chinook's entity box (what picks and clicks test) on the server and, in singleplayer, the client. */
    private static int bbox(CommandContext<CommandSourceStack> context) {
        ChinookEntity nearest = null;
        for (Entity entity : context.getSource().getLevel().getAllEntities()) {
            if (entity instanceof ChinookEntity chinook && (nearest == null || chinook.distanceToSqr(context.getSource().getPosition())
                    < nearest.distanceToSqr(context.getSource().getPosition()))) {
                nearest = chinook;
            }
        }
        if (nearest == null) {
            return reply(context, "no Chinook loaded");
        }
        String text = String.format("server pos %s yaw %.1f bbox %s parts %s | %s", nearest.position(), nearest.getYRot(), nearest.getBoundingBox(), nearest.partBounds(), nearest.helicopterDebugState());
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(nearest.getId()) instanceof ChinookEntity client) {
            text += String.format(" | client pos %s yaw %.1f | %s", client.position(), client.getYRot(), client.helicopterDebugState());
        }
        LOG.info("[chinook-dev] {}", text);
        return reply(context, text);
    }

    private static int report(CommandContext<CommandSourceStack> context) {
        String text = "chinook dev report\n" + FLIGHT + "\n" + JITTER + "\n" + DRIFT + "\n" + WALK + "\naboard " + CabinClient.isAboard();
        LOG.info("[chinook-dev] {}", text);
        return reply(context, text);
    }

    private static volatile long soundLogUntil;
    private static boolean soundListener;

    private static ChinookEntity nearestChinook(CommandContext<CommandSourceStack> context) {
        ChinookEntity nearest = null;
        Vec3 from = context.getSource().getPosition();
        for (ServerLevel level : context.getSource().getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ChinookEntity chinook && (nearest == null || chinook.distanceToSqr(from) < nearest.distanceToSqr(from))) {
                    nearest = chinook;
                }
            }
        }
        return nearest;
    }

    private static int reply(CommandContext<CommandSourceStack> context, String text) {
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static final class Stat {
        private final String name;
        private final List<Double> values = new ArrayList<>();

        Stat(String name) {
            this.name = name;
        }

        synchronized void add(double value) {
            if (this.values.size() < 100_000) {
                this.values.add(value);
            }
        }

        synchronized void reset() {
            this.values.clear();
        }

        @Override
        public synchronized String toString() {
            if (this.values.isEmpty()) {
                return this.name + ": no samples";
            }
            List<Double> sorted = new ArrayList<>(this.values);
            sorted.sort(Double::compare);
            double sum = 0.0D;
            for (double v : sorted) {
                sum += v;
            }
            return String.format("%s: n %d, mean %.4f, p95 %.4f, max %.4f", this.name, sorted.size(), sum / sorted.size(),
                    sorted.get((int) Math.min(sorted.size() - 1, Math.floor(sorted.size() * 0.95D))), sorted.get(sorted.size() - 1));
        }
    }
}
