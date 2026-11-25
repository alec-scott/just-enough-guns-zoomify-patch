package ttv.migami.jeg.vehicle.cabin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AbstractChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

/**
 * The maths of a walk-in cabin that moves (design/chinook/PLAN.md, D1; proven in the 2026-11-20 trials).
 * <p>
 * Cabin coordinates are the vehicle's data coordinates: blocks, +Z forward, +X the left, +Y up from the ground under the
 * hull at rest. A cabin's pose is the vehicle's {@code localToWorld} matrix. People aboard walk in cabin coordinates with
 * the cabin's "down" as gravity; their eye sits where the cabin puts it and their view stays level with the world.
 */
public final class CabinMath {
    public static final double EYE = 1.62D;
    public static final double HALF_WIDTH = 0.3D;
    public static final double HEIGHT = 1.8D;

    private CabinMath() {
    }

    public static Vec3 toWorld(Matrix4dc localToWorld, Vec3 local) {
        Vector3d v = localToWorld.transformPosition(new Vector3d(local.x, local.y, local.z));
        return new Vec3(v.x, v.y, v.z);
    }

    public static Vec3 toLocal(Matrix4dc localToWorld, Vec3 world) {
        Vector3d v = new Matrix4d(localToWorld).invert().transformPosition(new Vector3d(world.x, world.y, world.z));
        return new Vec3(v.x, v.y, v.z);
    }

    /** Feet in the world for feet at {@code local}: the eye where the cabin puts it, the body upright below it. */
    public static Vec3 feetWorld(Matrix4dc localToWorld, Vec3 local) {
        return toWorld(localToWorld, local.add(0.0D, EYE, 0.0D)).subtract(0.0D, EYE, 0.0D);
    }

    public static Vec3 feetLocal(Matrix4dc localToWorld, Vec3 feetWorld) {
        return toLocal(localToWorld, feetWorld.add(0.0D, EYE, 0.0D)).subtract(0.0D, EYE, 0.0D);
    }

    /** The cabin's heading: world = rotateY(heading) * local, so local +Z points along (sin, 0, cos). */
    public static double heading(Matrix4dc localToWorld) {
        Vector3d forward = localToWorld.transformDirection(new Vector3d(0.0D, 0.0D, 1.0D));
        return Math.atan2(forward.x, forward.z);
    }

    public static Vec3 worldToLocalYaw(Vec3 d, double heading) {
        double c = Math.cos(heading);
        double s = Math.sin(heading);
        return new Vec3(d.x * c - d.z * s, d.y, d.x * s + d.z * c);
    }

    public static Vec3 localToWorldYaw(Vec3 d, double heading) {
        double c = Math.cos(heading);
        double s = Math.sin(heading);
        return new Vec3(d.x * c + d.z * s, d.y, -d.x * s + d.z * c);
    }

    public static AABB body(Vec3 feetLocal) {
        return new AABB(feetLocal.x - HALF_WIDTH, feetLocal.y, feetLocal.z - HALF_WIDTH, feetLocal.x + HALF_WIDTH, feetLocal.y + HEIGHT,
                feetLocal.z + HALF_WIDTH);
    }

    /**
     * A vehicle's model is drawn in render space, X and Z the negatives of data coordinates (the model's X mirrored, its
     * Z the data's backwards): half a turn about Y. A block facing a data direction carries the opposite one.
     */
    public static Direction drawnFacing(Direction dataDirection) {
        return dataDirection.getAxis().isHorizontal() ? dataDirection.getOpposite() : dataDirection;
    }

    public static boolean isChest(BlockState state) {
        return state.getBlock() instanceof AbstractChestBlock<?> || state.getBlock() instanceof ShulkerBoxBlock;
    }

    // ---- Collision ----

    public static List<VoxelShape> shapes(List<AABB> shell, Map<BlockPos, BlockState> grid, CabinLayout layout) {
        List<VoxelShape> shapes = new ArrayList<>(shell.size() + grid.size());
        for (AABB box : shell) {
            shapes.add(Shapes.create(box));
        }
        for (Map.Entry<BlockPos, BlockState> cell : grid.entrySet()) {
            VoxelShape shape = cell.getValue().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            if (!shape.isEmpty()) {
                Vec3 corner = layout.cellCorner(cell.getKey());
                shapes.add(shape.move(corner.x, corner.y, corner.z));
            }
        }
        return shapes;
    }

    /** Vanilla's axis-by-axis collision (Y first, then the larger horizontal axis), against the given shapes only. */
    public static Vec3 collide(AABB box, Vec3 movement, List<VoxelShape> shapes) {
        double x = movement.x;
        double y = movement.y;
        double z = movement.z;
        if (y != 0.0D) {
            y = Shapes.collide(Direction.Axis.Y, box, shapes, y);
            if (y != 0.0D) {
                box = box.move(0.0D, y, 0.0D);
            }
        }
        boolean zFirst = Math.abs(x) < Math.abs(z);
        if (zFirst && z != 0.0D) {
            z = Shapes.collide(Direction.Axis.Z, box, shapes, z);
            if (z != 0.0D) {
                box = box.move(0.0D, 0.0D, z);
            }
        }
        if (x != 0.0D) {
            x = Shapes.collide(Direction.Axis.X, box, shapes, x);
            if (!zFirst && x != 0.0D) {
                box = box.move(x, 0.0D, 0.0D);
            }
        }
        if (!zFirst && z != 0.0D) {
            z = Shapes.collide(Direction.Axis.Z, box, shapes, z);
        }
        return new Vec3(x, y, z);
    }

    /** {@link #collide} with vanilla-style stepping up onto a ledge no higher than {@code step}. */
    public static Vec3 collideStepping(AABB box, Vec3 movement, List<VoxelShape> shapes, boolean onGround, double step) {
        Vec3 plain = collide(box, movement, shapes);
        boolean blocked = plain.x != movement.x || plain.z != movement.z;
        boolean grounded = onGround || plain.y != movement.y && movement.y < 0.0D;
        if (step <= 0.0D || !blocked || !grounded) {
            return plain;
        }
        Vec3 rise = collide(box, new Vec3(0.0D, step, 0.0D), shapes);
        Vec3 across = collide(box.move(0.0D, rise.y, 0.0D), new Vec3(movement.x, 0.0D, movement.z), shapes);
        Vec3 stepped = new Vec3(across.x, rise.y, across.z);
        Vec3 settle = collide(box.move(stepped), new Vec3(0.0D, -stepped.y + Math.min(0.0D, movement.y), 0.0D), shapes);
        stepped = stepped.add(settle);
        return stepped.horizontalDistanceSqr() > plain.horizontalDistanceSqr() + 1.0E-7D ? stepped : plain;
    }

    /** What a segment in cabin coordinates strikes first: the shell (cell null) or a placed block. */
    public record Hit(Vec3 point, Direction face, BlockPos cell, double distanceSqr) {
    }

    /** {@code wholeCells}: aim at a placed block's whole cell (clicks), or at its collision shape (rounds). */
    public static Hit clip(Vec3 from, Vec3 to, List<AABB> shell, Map<BlockPos, BlockState> grid, CabinLayout layout, boolean wholeCells) {
        Hit best = null;
        for (AABB box : shell) {
            best = nearer(best, clipBox(box, from, to, null));
        }
        for (Map.Entry<BlockPos, BlockState> cell : grid.entrySet()) {
            if (wholeCells) {
                best = nearer(best, clipBox(layout.cellBox(cell.getKey()), from, to, cell.getKey()));
            } else {
                Vec3 corner = layout.cellCorner(cell.getKey());
                VoxelShape shape = cell.getValue().getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                for (AABB box : shape.toAabbs()) {
                    best = nearer(best, clipBox(box.move(corner), from, to, cell.getKey()));
                }
            }
        }
        return best;
    }

    private static Hit clipBox(AABB box, Vec3 from, Vec3 to, BlockPos cell) {
        BlockHitResult result = AABB.clip(List.of(box), from, to, BlockPos.ZERO);
        if (result == null || result.getType() == HitResult.Type.MISS) {
            return null;
        }
        return new Hit(result.getLocation(), result.getDirection(), cell, result.getLocation().distanceToSqr(from));
    }

    private static Hit nearer(Hit a, Hit b) {
        if (a == null) {
            return b;
        }
        return b != null && b.distanceSqr() < a.distanceSqr() ? b : a;
    }
}
