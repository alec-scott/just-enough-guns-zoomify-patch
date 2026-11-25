package ttv.migami.jeg.vehicle.cabin;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;

/**
 * A vehicle people walk about in while it moves (the Chinook first). Everything is in the vehicle's data coordinates;
 * {@link #localToWorld} is the same pose its model is drawn at, so the room walked in and the room drawn never part.
 */
public interface CabinVehicle {
    int cabinId();

    CabinLayout cabinLayout();

    /** The structure people collide with now (floor, walls, roof, the ramp at its present angle), not the placed blocks. */
    List<AABB> cabinShell();

    /** Where feet count as aboard now (the room, and the ramp while it is open). */
    boolean cabinAboard(Vec3 feetLocal);

    /** The blocks placed aboard: cell to state (on a client, as the server last sent them). */
    Map<BlockPos, BlockState> cabinGrid();

    /** What the containers placed aboard hold (storage, and the furnaces' slots), by cell (server). */
    Map<BlockPos, SimpleContainer> cabinChests();

    /** The furnaces placed aboard, by cell (server; their slots are in {@link #cabinChests}). */
    Map<BlockPos, ttv.migami.jeg.vehicle.support.VehicleFurnace> cabinFurnaces();

    /** Whether a block may not go in this cell now (someone sits there). Server. */
    default boolean cabinCellBlocked(BlockPos cell) {
        return false;
    }

    /** Data coordinates to the world, at this point between ticks (0 last tick's pose, 1 this tick's). */
    Matrix4d localToWorld(float partialTick);

    /** Called on the server when the grid changes, to send it to the players who see the vehicle. */
    void cabinGridChanged();

    /** A control at this point of the cabin's structure (a ramp panel, a door), or -1. */
    default int cabinControlAt(Vec3 local) {
        return -1;
    }

    /**
     * A control the ray crosses where there is no structure to hit (an open doorway, whose door is worked by clicking
     * the opening), as {control, fraction of the way from {@code from} to {@code to}}, or null. Cabin coordinates.
     */
    @org.jetbrains.annotations.Nullable
    default double[] cabinControlOnRay(Vec3 from, Vec3 to) {
        return null;
    }

    /** A player aboard worked a control (server). */
    default void cabinControl(net.minecraft.server.level.ServerPlayer player, int control) {
    }

    /** Controls from this id on are the vehicle's seats ({@code SEAT_CONTROL + seat}): clicked from the cabin to sit. */
    int SEAT_CONTROL = 100;

    /**
     * A free seat the ray points at from inside, within reach, as {control, fraction of the way from {@code from} to
     * {@code to}}, or null. Cabin coordinates. A cockpit seat may be reached through the companionway's bulkhead.
     */
    @org.jetbrains.annotations.Nullable
    default double[] cabinSeatOnRay(Vec3 from, Vec3 to) {
        return null;
    }

    /** What a right-click on this control does, for the prompt in the action bar (client), or null. */
    @org.jetbrains.annotations.Nullable
    default net.minecraft.network.chat.Component cabinControlPrompt(int control) {
        return null;
    }
}
