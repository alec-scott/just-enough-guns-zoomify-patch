package ttv.migami.jeg.vehicle.cabin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A cabin's room in its vehicle's data coordinates: the space people walk in, and the grid of whole-block cells that
 * blocks can be placed in. Cell (i, j, k) fills {@code origin + (i, j, k)} to {@code origin + (i + 1, j + 1, k + 1)}.
 *
 * @param inside    the room people walk in
 * @param origin    the corner of cell (0, 0, 0)
 * @param cellsX    cells across
 * @param cellsY    cells up
 * @param cellsZ    cells along
 */
public record CabinLayout(AABB inside, Vec3 origin, int cellsX, int cellsY, int cellsZ) {
    public boolean isCell(BlockPos cell) {
        return cell.getX() >= 0 && cell.getX() < this.cellsX && cell.getY() >= 0 && cell.getY() < this.cellsY && cell.getZ() >= 0
                && cell.getZ() < this.cellsZ;
    }

    public Vec3 cellCorner(BlockPos cell) {
        return this.origin.add(cell.getX(), cell.getY(), cell.getZ());
    }

    public AABB cellBox(BlockPos cell) {
        Vec3 corner = this.cellCorner(cell);
        return new AABB(corner.x, corner.y, corner.z, corner.x + 1.0D, corner.y + 1.0D, corner.z + 1.0D);
    }

    /** The cell holding a point in cabin coordinates. */
    public BlockPos cellAt(Vec3 local) {
        return BlockPos.containing(local.x - this.origin.x, local.y - this.origin.y, local.z - this.origin.z);
    }

    /** The cell a block placed against a hit goes into: half a block out from the face struck. */
    public BlockPos placeCell(CabinMath.Hit hit) {
        return this.cellAt(hit.point().add(hit.face().getStepX() * 0.5D, hit.face().getStepY() * 0.5D, hit.face().getStepZ() * 0.5D));
    }
}
