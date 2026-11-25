package ttv.migami.jeg.vehicle.cabin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4d;

/**
 * The client side of walk-in cabins.
 * <ul>
 *     <li>The local player aboard moves in the cabin's coordinates (Entity.move, EntityMoveCabinMixin) against its
 *     walls, the ramp and the blocks aboard, with the cabin's "down" as gravity, and is placed each tick, after the
 *     vehicle has moved, from the vehicle's last and present poses: the same two its model is drawn between, so the
 *     view never moves against the drawn cabin. Its view turns with the vehicle's heading.</li>
 *     <li>Other people aboard are drawn where the server says they stand in the cabin, on the cabin as drawn here.</li>
 *     <li>Clicks aboard go to the cabin first (MinecraftCabinUseMixin): place a block (drawn at once, taken back if
 *     the server refuses it), open a chest, take a block.</li>
 *     <li>A teleport takes the player off (ClientPacketListenerCabinMixin).</li>
 * </ul>
 */
public final class CabinClient {
    /** Per vehicle: the server's grid and this player's changes not yet confirmed, merged for drawing and walking. */
    private static final class View {
        Map<BlockPos, BlockState> server = Map.of();
        final Map<BlockPos, BlockState> adds = new HashMap<>();
        final Set<BlockPos> removals = new HashSet<>();
        final Map<BlockPos, Long> since = new HashMap<>();
        volatile Map<BlockPos, BlockState> merged = Map.of();

        void remerge() {
            Map<BlockPos, BlockState> grid = new HashMap<>(this.server);
            grid.putAll(this.adds);
            for (BlockPos cell : this.removals) {
                grid.remove(cell);
            }
            this.merged = grid;
        }
    }

    private static final Map<Integer, View> VIEWS = new ConcurrentHashMap<>();
    /** Remote players aboard: entity id to (vehicle id, cabin coordinates), as the server last sent them. */
    private static final Map<Integer, Object[]> OCCUPANTS = new ConcurrentHashMap<>();

    private static int aboardId = -1;
    private static Vec3 local = Vec3.ZERO;
    private static Vec3 localAtTickStart = Vec3.ZERO;
    private static boolean onGround;
    private static boolean sendDetach;
    /** The cabin pose the local player was last placed on (endTick): its moves are placed on it too, so what vanilla
     * reads from the player's own world movement (view bobbing, limb swing) is the walk, not the Chinook's flight. */
    private static Matrix4d placedPose;
    /** Footsteps: distance walked on the cabin floor (vanilla's moveDist) and where the next step falls. */
    private static float stepDistance, nextStep = 1.0F;
    /** Remote people aboard: where each stood at the last client tick, how far they walked over it, their steps. */
    private static final Map<Integer, Vec3> REMOTE_LAST = new HashMap<>();
    private static final Map<Integer, Float> REMOTE_WALKED = new HashMap<>();
    private static final Map<Integer, float[]> REMOTE_STEPS = new HashMap<>();
    /** The action-bar prompt shown for what the view is on aboard, and how long until it is shown again. */
    private static int promptCooldown;
    private static int lastPrompt = -1;

    private CabinClient() {
    }

    public static void init() {
        CabinNetwork.clientAboard = () -> aboardId;
        ClientPlayNetworking.registerGlobalReceiver(CabinNetwork.GridPayload.TYPE, (payload, context) -> context.client().execute(() -> receiveGrid(payload)));
        ClientPlayNetworking.registerGlobalReceiver(CabinNetwork.OccupantsPayload.TYPE, (payload, context) -> context.client().execute(() -> receiveOccupants(payload)));
        ClientTickEvents.START_CLIENT_TICK.register(CabinClient::startTick);
        ClientTickEvents.END_CLIENT_TICK.register(CabinClient::endTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            VIEWS.clear();
            OCCUPANTS.clear();
            REMOTE_LAST.clear();
            REMOTE_WALKED.clear();
            REMOTE_STEPS.clear();
            aboardId = -1;
            placedPose = null;
        });
    }

    /** What is drawn and walked against aboard a vehicle here: the server's blocks with this player's changes. */
    public static Map<BlockPos, BlockState> grid(int vehicleId) {
        View view = VIEWS.get(vehicleId);
        return view == null ? Map.of() : view.merged;
    }

    public static boolean isAboard() {
        return aboardId >= 0;
    }

    public static int aboardVehicle() {
        return aboardId;
    }

    /** How many other people stand in this vehicle's cabin, as the server last said (the pilot's HUD). */
    /** The cabin vehicle a player stands in on this client (the local player or someone else aboard), or -1. */
    public static int cabinOf(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player) {
            return aboardId;
        }
        Object[] entry = OCCUPANTS.get(entity.getId());
        return entry == null ? -1 : (int) entry[0];
    }

    public static int othersAboard(int vehicleId) {
        int count = 0;
        for (Object[] entry : OCCUPANTS.values()) {
            if ((int) entry[0] == vehicleId) {
                count++;
            }
        }
        return count;
    }

    /**
     * For limb swing: how far this entity walked over the last tick in its cabin's coordinates, or NaN when it isn't a
     * remote person aboard. Their world movement is the vehicle's flight, which ran their legs flat out while they stood
     * still on the ramp (two-player play-test, 2026-11-25).
     */
    public static float remoteWalked(Entity entity) {
        Float walked = REMOTE_WALKED.get(entity.getId());
        return walked == null || !OCCUPANTS.containsKey(entity.getId()) ? Float.NaN : walked;
    }

    private static void receiveGrid(CabinNetwork.GridPayload payload) {
        View view = VIEWS.computeIfAbsent(payload.id(), id -> new View());
        Map<BlockPos, BlockState> grid = new HashMap<>();
        for (int i = 0; i < payload.cells().size(); i++) {
            grid.put(payload.cells().get(i), Block.stateById(payload.states().get(i)));
        }
        view.server = grid;
        // Changes the server has now confirmed no longer need holding.
        view.adds.keySet().removeIf(cell -> {
            if (grid.containsKey(cell)) {
                view.since.remove(cell);
                return true;
            }
            return false;
        });
        view.removals.removeIf(cell -> {
            if (!grid.containsKey(cell)) {
                view.since.remove(cell);
                return true;
            }
            return false;
        });
        view.remerge();
    }

    private static void receiveOccupants(CabinNetwork.OccupantsPayload payload) {
        OCCUPANTS.values().removeIf(entry -> (int) entry[0] == payload.id());
        for (int i = 0; i < payload.players().length; i++) {
            OCCUPANTS.put(payload.players()[i], new Object[]{payload.id(),
                    new Vec3(payload.locals()[i * 3], payload.locals()[i * 3 + 1], payload.locals()[i * 3 + 2])});
        }
    }

    private static CabinVehicle cabin(Minecraft mc, int id) {
        if (mc.level == null) {
            return null;
        }
        Entity entity = mc.level.getEntity(id);
        return entity instanceof CabinVehicle cabin && !entity.isRemoved() ? cabin : null;
    }

    private static void startTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        long now = mc.level.getGameTime();
        for (View view : VIEWS.values()) {
            // A change the server never confirmed within two seconds is taken back.
            boolean changed = false;
            for (Iterator<Map.Entry<BlockPos, Long>> it = view.since.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<BlockPos, Long> entry = it.next();
                if (now - entry.getValue() > 40L) {
                    view.adds.remove(entry.getKey());
                    view.removals.remove(entry.getKey());
                    it.remove();
                    changed = true;
                }
            }
            if (changed) {
                view.remerge();
            }
        }
        localAtTickStart = local;
        if (aboardId >= 0) {
            if (cabin(mc, aboardId) == null || player.isPassenger() || player.isSpectator() || player.isDeadOrDying()) {
                detach(player, null);
            }
            return;
        }
        if (player.isPassenger() || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof CabinVehicle cabin && entity.distanceToSqr(player) < 40.0D * 40.0D) {
                Vec3 feet = CabinMath.feetLocal(cabin.localToWorld(1.0F), player.position());
                if (cabin.cabinAboard(feet)) {
                    aboardId = entity.getId();
                    double floor = cabin.cabinLayout().inside().minY;
                    double y = feet.y < floor && feet.y > floor - 0.7D ? floor : feet.y;
                    // Stepping on from the ground (a lowered ramp's lip): stand on what is underfoot. Cabin collision
                    // ignores a box the body already overlaps, and the world's ground isn't part of it, so feet left
                    // inside the lip's step sank through it into the ground (play-test 2026-11-20).
                    y = Math.max(y, standingHeight(cabin.cabinShell(), feet));
                    local = new Vec3(feet.x, y, feet.z);
                    localAtTickStart = local;
                    onGround = false;
                    placedPose = null;
                    stepDistance = 0.0F;
                    nextStep = 1.0F;
                    break;
                }
            }
        }
    }

    /**
     * The top of the highest shell box under the feet that they stand in or a step below (a ramp's tread), or
     * negative infinity.
     */
    private static double standingHeight(List<net.minecraft.world.phys.AABB> shell, Vec3 feet) {
        double best = Double.NEGATIVE_INFINITY;
        for (net.minecraft.world.phys.AABB box : shell) {
            if (feet.x > box.minX && feet.x < box.maxX && feet.z > box.minZ && feet.z < box.maxZ
                    && box.maxY > feet.y - 1.0E-4D && box.maxY <= feet.y + 0.6D && box.minY <= feet.y + 1.0E-4D) {
                best = Math.max(best, box.maxY);
            }
        }
        return best;
    }

    /** Entity.move for the local player aboard: the move happens in the cabin's coordinates. */
    public static boolean move(LocalPlayer player, MoverType type, Vec3 movement) {
        if (aboardId < 0) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        CabinVehicle cabin = cabin(mc, aboardId);
        if (cabin == null) {
            return false;
        }
        // On the pose the player was last placed on, whether or not the vehicle has moved yet this tick: the view's yaw
        // was turned to it, and vanilla reads the player's world movement (view bobbing, limb swing) as their walk.
        Matrix4d now = placedPose != null ? placedPose : cabin.localToWorld(1.0F);
        double heading = CabinMath.heading(now);
        List<VoxelShape> shapes = CabinMath.shapes(cabin.cabinShell(), grid(aboardId), cabin.cabinLayout());
        Vec3 wanted = CabinMath.worldToLocalYaw(movement, heading);
        Vec3 moved = CabinMath.collideStepping(CabinMath.body(local), wanted, shapes, onGround, player.maxUpStep());
        local = local.add(moved);
        boolean below = wanted.y < 0.0D && moved.y > wanted.y + 1.0E-7D;
        boolean above = wanted.y > 0.0D && moved.y < wanted.y - 1.0E-7D;
        boolean sideX = Math.abs(moved.x - wanted.x) > 1.0E-7D;
        boolean sideZ = Math.abs(moved.z - wanted.z) > 1.0E-7D;
        onGround = below;
        player.setOnGround(below);
        player.horizontalCollision = sideX || sideZ;
        player.verticalCollision = below || above;
        player.verticalCollisionBelow = below;
        Vec3 velocity = player.getDeltaMovement();
        Vec3 horizontal = sideX || sideZ ? CabinMath.localToWorldYaw(new Vec3(moved.x, 0.0D, moved.z), heading) : new Vec3(velocity.x, 0.0D, velocity.z);
        player.setDeltaMovement(horizontal.x, below || above ? 0.0D : velocity.y, horizontal.z);
        if (below) {
            player.resetFallDistance();
            // Footsteps on the cabin floor (vanilla's step sounds come from Entity.move, which is skipped aboard).
            stepDistance += (float) Math.hypot(moved.x, moved.z) * 0.6F;
            if (stepDistance > nextStep) {
                nextStep = (int) stepDistance + 1.0F;
                if (!player.isSilent() && !player.isSteppingCarefully()) {
                    Vec3 feet = CabinMath.feetWorld(now, local);
                    playStep(Minecraft.getInstance(), cabin, local, feet, 1.0F);
                }
            }
        }
        // Placed now for this tick's logic; endTick places it again once the vehicle has moved.
        Vec3 world = CabinMath.feetWorld(now, local);
        player.setPos(world.x, world.y, world.z);
        if (!cabin.cabinAboard(local) || local.y < cabin.cabinLayout().inside().minY - 3.0D) {
            detach(player, cabin);
        }
        return true;
    }

    private static void detach(Player player, CabinVehicle cabin) {
        if (cabin != null) {
            Vec3 carry = CabinMath.feetWorld(cabin.localToWorld(1.0F), local).subtract(CabinMath.feetWorld(cabin.localToWorld(0.0F), local));
            player.setDeltaMovement(player.getDeltaMovement().add(carry));
        }
        aboardId = -1;
        sendDetach = true;
    }

    /** The server teleported the player: off the cabin. */
    public static void teleported() {
        if (aboardId >= 0) {
            aboardId = -1;
            sendDetach = true;
        }
    }

    private static void endTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.getConnection() == null) {
            return;
        }
        if (aboardId >= 0) {
            CabinVehicle cabin = cabin(mc, aboardId);
            if (cabin != null) {
                // From where the player stood on the vehicle's last pose to where they stand on this one: the two poses
                // the vehicle is drawn between this tick, so the view moves with the cabin as drawn.
                Matrix4d before = cabin.localToWorld(0.0F);
                Matrix4d now = cabin.localToWorld(1.0F);
                Vec3 old = CabinMath.feetWorld(before, localAtTickStart);
                Vec3 world = CabinMath.feetWorld(now, local);
                player.setPos(world.x, world.y, world.z);
                player.xo = old.x;
                player.yo = old.y;
                player.zo = old.z;
                player.xOld = old.x;
                player.yOld = old.y;
                player.zOld = old.z;
                placedPose = new Matrix4d(now);
                double turn = CabinMath.heading(now) - CabinMath.heading(before);
                float degrees = (float) -Math.toDegrees(Math.atan2(Math.sin(turn), Math.cos(turn)));
                player.setYRot(player.getYRot() + degrees);
                player.setYHeadRot(player.getYHeadRot() + degrees);
                ClientPlayNetworking.send(new CabinNetwork.MovePayload(aboardId, local.x, local.y, local.z, onGround));
            }
        } else if (sendDetach) {
            sendDetach = false;
            placedPose = null;
            ClientPlayNetworking.send(new CabinNetwork.MovePayload(-1, 0.0D, 0.0D, 0.0D, false));
        }
        // Other people aboard: on the cabin as drawn here, not where world-space smoothing would put them.
        for (Iterator<Map.Entry<Integer, Object[]>> it = OCCUPANTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Object[]> entry = it.next();
            Entity other = mc.level.getEntity(entry.getKey());
            CabinVehicle cabin = cabin(mc, (int) entry.getValue()[0]);
            if (other == null || cabin == null || other == player) {
                if (other == null || cabin == null) {
                    it.remove();
                }
                continue;
            }
            Vec3 at = (Vec3) entry.getValue()[1];
            Vec3 old = CabinMath.feetWorld(cabin.localToWorld(0.0F), at);
            Vec3 world = CabinMath.feetWorld(cabin.localToWorld(1.0F), at);
            other.setPos(world.x, world.y, world.z);
            other.xo = old.x;
            other.yo = old.y;
            other.zo = old.z;
            // Their walk in the cabin's coordinates: limb swing (LivingEntityCabinWalkMixin) and footsteps, heard here.
            Vec3 last = REMOTE_LAST.put(other.getId(), at);
            float walked = last == null ? 0.0F : (float) Math.hypot(at.x - last.x, at.z - last.z);
            REMOTE_WALKED.put(other.getId(), walked);
            // On their feet: level, or walking the lowered ramp (30 degrees: under 0.08 a tick at a walk), not jumping.
            boolean standing = last != null && Math.abs(at.y - last.y) < 0.12D;
            float[] steps = REMOTE_STEPS.computeIfAbsent(other.getId(), id -> new float[]{0.0F, 1.0F});
            if (standing && walked > 0.0F && walked < 1.0F) {
                steps[0] += walked * 0.6F;
                if (steps[0] > steps[1]) {
                    steps[1] = (int) steps[0] + 1.0F;
                    if (!other.isSilent() && !other.isShiftKeyDown()) {
                        playStep(mc, cabin, at, world, 1.0F);
                    }
                }
            }
        }
        REMOTE_LAST.keySet().removeIf(id -> !OCCUPANTS.containsKey(id));
        REMOTE_WALKED.keySet().removeIf(id -> !OCCUPANTS.containsKey(id));
        REMOTE_STEPS.keySet().removeIf(id -> !OCCUPANTS.containsKey(id));
        prompt(mc);
    }

    /**
     * A footstep aboard, at the feet: the placed block underfoot's own sound, or the cabin's aluminium floor and ramp
     * (a metal step, a little deeper than a block of iron's).
     */
    private static void playStep(Minecraft mc, CabinVehicle cabin, Vec3 feetLocal, Vec3 feetWorld, float volume) {
        BlockPos below = cabin.cabinLayout().cellAt(feetLocal.subtract(0.0D, 0.2D, 0.0D));
        BlockState state = cabin.cabinLayout().isCell(below) ? grid(cabin.cabinId()).get(below) : null;
        net.minecraft.world.level.block.SoundType type = state != null ? state.getSoundType() : net.minecraft.world.level.block.SoundType.METAL;
        float pitch = state != null ? type.getPitch() : 0.82F + mc.level.getRandom().nextFloat() * 0.08F;
        mc.level.playLocalSound(feetWorld.x, feetWorld.y, feetWorld.z, type.getStepSound(), net.minecraft.sounds.SoundSource.PLAYERS,
                type.getVolume() * 0.15F * volume, pitch, false);
    }

    /**
     * Aboard, the action bar says what a right-click on what the view is on would do: the ramp panel, the crew door, a
     * seat (the FSV-6's lockers do the same, SupportHints).
     */
    private static void prompt(Minecraft mc) {
        if (promptCooldown > 0) {
            promptCooldown--;
        }
        CabinVehicle cabin = aboardId >= 0 ? cabin(mc, aboardId) : null;
        if (cabin == null || mc.player == null || mc.screen != null || mc.options.hideGui) {
            lastPrompt = -1;
            return;
        }
        int control = target(mc, cabin);
        if (control < 0) {
            lastPrompt = -1;
            return;
        }
        if (control == lastPrompt && promptCooldown > 0) {
            return;
        }
        net.minecraft.network.chat.Component what = cabin.cabinControlPrompt(control);
        if (what != null) {
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("hud.jeg.cabin.prompt",
                    mc.options.keyUse.getTranslatedKeyMessage(), what), false);
        }
        lastPrompt = control;
        promptCooldown = 20;
    }

    /** The control or seat a right-click would work now, or -1 (the same choice {@link #handleUse} makes). */
    private static int target(Minecraft mc, CabinVehicle cabin) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Matrix4d pose = cabin.localToWorld(partialTick);
        Vec3 eye = mc.gameRenderer.getMainCamera().position();
        Vec3 from = CabinMath.toLocal(pose, eye);
        Vec3 to = CabinMath.toLocal(pose, eye.add(mc.player.getViewVector(partialTick).scale(mc.player.blockInteractionRange())));
        CabinMath.Hit hit = CabinMath.clip(from, to, cabin.cabinShell(), grid(aboardId), cabin.cabinLayout(), true);
        double length = to.distanceTo(from);
        double hitDistance = hit == null ? Double.MAX_VALUE : hit.point().distanceTo(from);
        // The control the ray meets (a door through its open doorway, or one on the structure it strikes), and how far.
        int control = -1;
        double controlDistance = Double.MAX_VALUE;
        double[] opening = cabin.cabinControlOnRay(from, to);
        if (opening != null && hitDistance > opening[1] * length) {
            control = (int) opening[0];
            controlDistance = opening[1] * length;
        } else if (hit != null && hit.cell() == null && cabin.cabinControlAt(hit.point()) >= 0) {
            control = cabin.cabinControlAt(hit.point());
            controlDistance = hitDistance;
        }
        if (!(mc.player.getMainHandItem().getItem() instanceof ttv.migami.jeg.item.GunItem) && !mc.player.isShiftKeyDown()) {
            double[] seat = cabin.cabinSeatOnRay(from, to);
            if (seat != null) {
                double seatDistance = seat[1] * length;
                boolean cockpit = seat[0] < CabinVehicle.SEAT_CONTROL + 2;
                // A seat or its gun takes the click when it is what the view meets first (the right door gun stands
                // stowed in the crew doorway: pointing at it takes it; the doorway round it works the door). A cockpit
                // seat is reached through the companionway's bulkhead.
                if ((cockpit || seatDistance < hitDistance) && seatDistance < controlDistance) {
                    return (int) seat[0];
                }
            }
        }
        return control;
    }

    /**
     * What the local player's view is on while aboard (LocalPlayerCabinPickMixin): never the cabin vehicle itself (its
     * entity box held the eye, so every click inside went to the vehicle, and the first free seat), nor anything in
     * the world behind the cabin's walls; through the open ramp, the world as usual.
     */
    public static net.minecraft.world.phys.HitResult filterPick(net.minecraft.world.phys.HitResult result, float partialTick, Entity camera) {
        Minecraft mc = Minecraft.getInstance();
        CabinVehicle cabin = aboardId >= 0 ? cabin(mc, aboardId) : null;
        if (cabin == null || mc.player == null || camera == null) {
            return result;
        }
        Vec3 eye = camera.getEyePosition(partialTick);
        Vec3 look = camera.getViewVector(partialTick);
        double range = Math.max(mc.player.blockInteractionRange(), mc.player.entityInteractionRange());
        net.minecraft.world.phys.HitResult base = result;
        if (result instanceof net.minecraft.world.phys.EntityHitResult entityHit && entityHit.getEntity() == cabin) {
            base = camera.pick(mc.player.blockInteractionRange(), partialTick, false);
        }
        double reach = base.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? range : base.getLocation().distanceTo(eye);
        Matrix4d pose = cabin.localToWorld(partialTick);
        Vec3 from = CabinMath.toLocal(pose, eye);
        Vec3 to = CabinMath.toLocal(pose, eye.add(look.scale(reach)));
        CabinMath.Hit hit = CabinMath.clip(from, to, cabin.cabinShell(), grid(aboardId), cabin.cabinLayout(), false);
        if (hit != null) {
            Vec3 at = CabinMath.toWorld(pose, hit.point());
            return net.minecraft.world.phys.BlockHitResult.miss(at, net.minecraft.core.Direction.getApproximateNearest(look.reverse()), BlockPos.containing(at));
        }
        return base;
    }

    // ---- Clicks aboard ----

    private static CabinMath.Hit aim(Minecraft mc, CabinVehicle cabin) {
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Matrix4d pose = cabin.localToWorld(partialTick);
        Vec3 eye = mc.gameRenderer.getMainCamera().position();
        Vec3 look = mc.player.getViewVector(partialTick);
        Vec3 from = CabinMath.toLocal(pose, eye);
        Vec3 to = CabinMath.toLocal(pose, eye.add(look.scale(mc.player.blockInteractionRange())));
        return CabinMath.clip(from, to, cabin.cabinShell(), grid(aboardId), cabin.cabinLayout(), true);
    }

    /** A right-click aboard: open a chest, or place the held block (drawn at once), as on the ground. */
    public static boolean handleUse() {
        Minecraft mc = Minecraft.getInstance();
        CabinVehicle cabin = aboardId >= 0 ? cabin(mc, aboardId) : null;
        if (cabin == null || mc.player == null || mc.level == null) {
            return false;
        }
        CabinMath.Hit hit = aim(mc, cabin);
        // A control (the ramp panel, the crew door, through its open doorway too) or a free seat the view is on: the
        // seat pointed at is the one taken (two-player play-test 2026-11-25: any click in the cabin seated the player in
        // the first free seat, the pilot's).
        int control = target(mc, cabin);
        if (control >= 0) {
            ClientPlayNetworking.send(new CabinNetwork.UsePayload(aboardId, 3, new BlockPos(control, 0, 0), Direction.UP.get3DDataValue()));
            mc.player.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        if (hit == null) {
            return false;
        }
        Map<BlockPos, BlockState> grid = grid(aboardId);
        BlockState target = hit.cell() == null ? null : grid.get(hit.cell());
        if (target != null && (CabinBlocks.usable(target) || CabinBlocks.lightable(target, mc.player.getMainHandItem()))
                && !mc.player.isShiftKeyDown()) {
            ClientPlayNetworking.send(new CabinNetwork.UsePayload(aboardId, 1, hit.cell(), hit.face().get3DDataValue()));
            mc.player.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        if (!(mc.player.getMainHandItem().getItem() instanceof BlockItem item)) {
            return false;
        }
        BlockPos cell = cabin.cabinLayout().placeCell(hit);
        if (cabin.cabinLayout().isCell(cell) && !grid.containsKey(cell)
                && !CabinMath.body(local).intersects(cabin.cabinLayout().cellBox(cell))) {
            Vec3 centre = cabin.cabinLayout().cellBox(cell).getCenter();
            double dx = local.x - centre.x;
            double dz = local.z - centre.z;
            BlockState state = CabinBlocks.placedState(item, hit.face(),
                    CabinMath.drawnFacing(Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH)));
            // Drawn at once, unless the server will refuse it (it says why).
            if (CabinBlocks.refusal(state) == null) {
                View view = VIEWS.computeIfAbsent(aboardId, id -> new View());
                view.adds.put(cell, state);
                view.removals.remove(cell);
                view.since.put(cell, mc.level.getGameTime());
                view.remerge();
            }
            ClientPlayNetworking.send(new CabinNetwork.UsePayload(aboardId, 0, cell, hit.face().get3DDataValue()));
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
        return true;
    }

    /** A left-click aboard on a placed block takes it, at once (never with a gun in hand: that fires). */
    public static boolean handleAttack() {
        Minecraft mc = Minecraft.getInstance();
        CabinVehicle cabin = aboardId >= 0 ? cabin(mc, aboardId) : null;
        if (cabin == null || mc.player == null || mc.level == null || mc.player.getMainHandItem().getItem() instanceof ttv.migami.jeg.item.GunItem) {
            return false;
        }
        CabinMath.Hit hit = aim(mc, cabin);
        if (hit == null || hit.cell() == null) {
            return false;
        }
        View view = VIEWS.computeIfAbsent(aboardId, id -> new View());
        view.removals.add(hit.cell());
        view.adds.remove(hit.cell());
        view.since.put(hit.cell(), mc.level.getGameTime());
        view.remerge();
        ClientPlayNetworking.send(new CabinNetwork.UsePayload(aboardId, 2, hit.cell(), hit.face().get3DDataValue()));
        mc.player.swing(InteractionHand.MAIN_HAND);
        return true;
    }
}
